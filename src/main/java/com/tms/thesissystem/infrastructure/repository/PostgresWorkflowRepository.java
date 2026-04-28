package com.tms.thesissystem.infrastructure.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.ApprovalRecord;
import com.tms.thesissystem.domain.ApprovalStage;
import com.tms.thesissystem.domain.AuditEntry;
import com.tms.thesissystem.domain.Notification;
import com.tms.thesissystem.domain.Plan;
import com.tms.thesissystem.domain.PlanStatus;
import com.tms.thesissystem.domain.Review;
import com.tms.thesissystem.domain.Topic;
import com.tms.thesissystem.domain.TopicStatus;
import com.tms.thesissystem.domain.User;
import com.tms.thesissystem.domain.UserRole;
import com.tms.thesissystem.domain.WeeklyTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true", matchIfMissing = true)
public class PostgresWorkflowRepository implements WorkflowRepository {
    private static final long STUDENT_OFFSET = 100_000L;
    private static final long TEACHER_OFFSET = 200_000L;
    private static final long DEPARTMENT_OFFSET = 300_000L;
    private static final String SOFTWARE_ENGINEERING = "Software Engineering";
    private final String url;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, Review> reviews = new LinkedHashMap<>();
    private final Map<Long, AuditEntry> audits = new LinkedHashMap<>();
    private final AtomicLong reviewSequence = new AtomicLong(3100);
    private final AtomicLong notificationSequence = new AtomicLong(4100);
    private final AtomicLong auditSequence = new AtomicLong(5100);

    public PostgresWorkflowRepository(@Value("${app.database.url}") String url,
                                      @Value("${app.database.username}") String username,
                                      @Value("${app.database.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        ensureCoreTables();
        ensureProjectionTables();
        ensureIdentityColumns();
        syncNotificationSequence();
        reconcileApprovedTopicsPerStudent();
    }

    private void ensureCoreTables() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists department (
                        id bigserial primary key,
                        name text not null,
                        programs jsonb,
                        admin text
                    )
                    """);
            statement.execute("""
                    create table if not exists student (
                        id bigserial primary key,
                        dep_id bigint references department(id),
                        firstname text not null,
                        lastname text not null,
                        mail text,
                        program text,
                        sisi_id text,
                        is_choosed boolean default false,
                        proposed_number integer default 0
                    )
                    """);
            statement.execute("""
                    create table if not exists teacher (
                        id bigserial primary key,
                        dep_id bigint references department(id),
                        firstname text not null,
                        lastname text not null,
                        mail text,
                        num_of_choosed_stud integer default 0
                    )
                    """);
            statement.execute("""
                    create table if not exists topic (
                        id bigserial primary key,
                        created_at date not null,
                        created_by_id bigint not null,
                        created_by_type text not null,
                        fields jsonb not null,
                        form_id bigint,
                        program text,
                        status text not null
                    )
                    """);
            statement.execute("""
                    create table if not exists topic_request (
                        id bigserial primary key,
                        is_selected boolean default false,
                        req_note text,
                        req_text text,
                        requested_by_id bigint not null,
                        requested_by_type text not null,
                        selected_at date,
                        topic_id bigint not null references topic(id)
                    )
                    """);
            statement.execute("""
                    create table if not exists plan (
                        id bigserial primary key,
                        created_at date not null,
                        status text not null,
                        student_id bigint not null,
                        topic_id bigint not null references topic(id)
                    )
                    """);
            statement.execute("""
                    create table if not exists plan_week (
                        id bigserial primary key,
                        plan_id bigint not null references plan(id) on delete cascade,
                        result jsonb,
                        task text not null,
                        week_number integer not null
                    )
                    """);
            statement.execute("""
                    create table if not exists plan_response (
                        id bigserial primary key,
                        approver_id bigint not null,
                        approver_type text not null,
                        note text,
                        plan_id bigint not null references plan(id) on delete cascade,
                        res text not null,
                        res_date date
                    )
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize core tables", exception);
        }
    }

    private void ensureProjectionTables() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists workflow_notification (
                        id bigint primary key,
                        user_id bigint not null,
                        title text not null,
                        message text not null,
                        created_at timestamp not null
                    )
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize projection tables", exception);
        }
    }

    private void ensureIdentityColumns() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("alter table department add column if not exists admin text");
            statement.execute("update department set admin = 'sisi-admin' where admin is null or trim(admin) = ''");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize identity columns", exception);
        }
    }

    private void syncNotificationSequence() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select coalesce(max(id), 4100) from workflow_notification")) {
            if (resultSet.next()) {
                notificationSequence.set(resultSet.getLong(1));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sync notification sequence", exception);
        }
    }

    @Override
    public synchronized List<User> findAllUsers() {
        List<User> users = new ArrayList<>();
        try (Connection connection = getConnection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select s.id, s.firstname, s.lastname, s.mail, s.program, s.sisi_id, d.name from student s left join department d on d.id = s.dep_id order by s.id")) {
                while (rs.next()) {
                    users.add(new User(
                            encodeUserId(UserRole.STUDENT, rs.getLong(1)),
                            UserRole.STUDENT,
                            rs.getString(6),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(7),
                            rs.getString(5)
                    ));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select t.id, t.firstname, t.lastname, t.mail, d.name from teacher t left join department d on d.id = t.dep_id order by t.id")) {
                while (rs.next()) {
                    long rawId = rs.getLong(1);
                    users.add(new User(
                            encodeUserId(UserRole.TEACHER, rawId),
                            UserRole.TEACHER,
                            teacherLoginId(rawId),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            "B.SE"
                    ));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select id, name, admin from department order by id limit 1")) {
                while (rs.next()) {
                    users.add(new User(
                            encodeUserId(UserRole.DEPARTMENT, rs.getLong(1)),
                            UserRole.DEPARTMENT,
                            rs.getString(3),
                            rs.getString(2),
                            "Department",
                            "sisi.admin@tms.mn",
                            rs.getString(2),
                            "B.SE"
                    ));
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load users", exception);
        }
        return users;
    }

    @Override
    public synchronized List<User> findUsersByRole(UserRole role) {
        return findAllUsers().stream().filter(user -> user.role() == role).toList();
    }

    @Override
    public synchronized Optional<User> findUserById(Long id) {
        return findAllUsers().stream().filter(user -> user.id().equals(id)).findFirst();
    }

    @Override
    public synchronized User createUserAccount(String username, UserRole role) {
        try (Connection connection = getConnection()) {
            long departmentId = ensureDefaultDepartment(connection);
            connection.setAutoCommit(false);
            try {
                User createdUser = switch (role) {
                    case STUDENT -> {
                        long rawId = insertStudent(connection, departmentId, username, "User", username + "@tms.mn", "B.SE", username);
                        yield new User(encodeUserId(UserRole.STUDENT, rawId), UserRole.STUDENT, username, username, "User", username + "@tms.mn", SOFTWARE_ENGINEERING, "B.SE");
                    }
                    case TEACHER -> {
                        long rawId = insertTeacher(connection, departmentId, username, "Teacher", username + "@tms.mn");
                        yield new User(encodeUserId(UserRole.TEACHER, rawId), UserRole.TEACHER, teacherLoginId(rawId), username, "Teacher", username + "@tms.mn", SOFTWARE_ENGINEERING, "B.SE");
                    }
                    case DEPARTMENT -> {
                        long rawId = insertDepartment(connection, username);
                        try (PreparedStatement updateAdmin = connection.prepareStatement("update department set admin = ? where id = ?")) {
                            updateAdmin.setString(1, username);
                            updateAdmin.setLong(2, rawId);
                            updateAdmin.executeUpdate();
                        }
                        yield new User(encodeUserId(UserRole.DEPARTMENT, rawId), UserRole.DEPARTMENT, username, username, "Department", username + "@tms.mn", username, "B.SE");
                    }
                };
                connection.commit();
                return createdUser;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create workflow user account", exception);
        }
    }

    @Override
    public Long nextTopicId() {
        return 0L;
    }

    @Override
    public synchronized List<Topic> findAllTopics() {
        List<Topic> topics = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from topic order by id desc")) {
            while (rs.next()) {
                topics.add(mapTopic(connection, rs));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load topics", exception);
        }
        return topics.stream().sorted(Comparator.comparing(Topic::updatedAt).reversed()).toList();
    }

    @Override
    public synchronized Optional<Topic> findTopicById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("select * from topic where id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapTopic(connection, rs));
            }
            return Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load topic", exception);
        }
    }

    @Override
    public synchronized Topic saveTopic(Topic topic) {
        try (Connection connection = getConnection()) {
            Long effectiveTopicId = topic.id();
            boolean exists;
            try (PreparedStatement existsPs = connection.prepareStatement("select count(*) from topic where id = ?")) {
                existsPs.setLong(1, topic.id());
                try (ResultSet rs = existsPs.executeQuery()) {
                    rs.next();
                    exists = rs.getInt(1) > 0;
                }
            }

            if (exists) {
                try (PreparedStatement ps = connection.prepareStatement("""
                        update topic
                        set created_by_id = ?, created_by_type = ?, program = ?, status = ?, created_at = ?, fields = ?::json
                        where id = ?
                        """)) {
                    ps.setLong(1, decodeUserId(topic.proposerRole(), topic.proposerId()));
                    ps.setString(2, topic.proposerRole().name());
                    ps.setString(3, topic.program());
                    ps.setString(4, topic.status().name());
                    ps.setDate(5, Date.valueOf(topic.createdAt().toLocalDate()));
                    ps.setString(6, json(payload(topic.title(), topic.description(), decodeNullableUserId(UserRole.STUDENT, topic.ownerStudentId()), topic.ownerStudentName(),
                            decodeNullableUserId(UserRole.TEACHER, topic.advisorTeacherId()), topic.advisorTeacherName(), topic.approvals())));
                    ps.setLong(7, topic.id());
                    ps.executeUpdate();
                }
            } else {
                long id = insertTopic(connection, decodeUserId(topic.proposerRole(), topic.proposerId()), topic.proposerRole(), topic.program(), topic.status(),
                        payload(topic.title(), topic.description(), decodeNullableUserId(UserRole.STUDENT, topic.ownerStudentId()), topic.ownerStudentName(),
                                decodeNullableUserId(UserRole.TEACHER, topic.advisorTeacherId()), topic.advisorTeacherName(), topic.approvals()));
                effectiveTopicId = id;
            }

            if (topic.ownerStudentId() != null) {
                upsertTopicRequest(connection, effectiveTopicId, decodeUserId(UserRole.STUDENT, topic.ownerStudentId()), UserRole.STUDENT, topic.status() != TopicStatus.REJECTED, "Topic workflow request");
            }

            return findTopicById(effectiveTopicId)
                    .orElseGet(() -> findAllTopics().stream()
                            .filter(item -> item.title().equals(topic.title()) && item.proposerId().equals(topic.proposerId()))
                            .findFirst()
                            .orElse(topic));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save topic", exception);
        }
    }

    @Override
    public Long nextPlanId() {
        return 0L;
    }

    @Override
    public synchronized List<Plan> findAllPlans() {
        List<Plan> plans = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select p.*, t.fields from plan p join topic t on t.id = p.topic_id order by p.id desc")) {
            while (rs.next()) {
                plans.add(mapPlan(connection, rs));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load plans", exception);
        }
        return plans.stream().sorted(Comparator.comparing(Plan::updatedAt).reversed()).toList();
    }

    @Override
    public synchronized Optional<Plan> findPlanById(Long id) {
        return findAllPlans().stream().filter(plan -> plan.id().equals(id)).findFirst();
    }

    @Override
    public synchronized Optional<Plan> findPlanByStudentId(Long studentId) {
        return findAllPlans().stream().filter(plan -> plan.studentId().equals(studentId)).findFirst();
    }

    @Override
    public synchronized Plan savePlan(Plan plan) {
        try (Connection connection = getConnection()) {
            boolean exists;
            try (PreparedStatement existsPs = connection.prepareStatement("select count(*) from plan where id = ?")) {
                existsPs.setLong(1, plan.id());
                try (ResultSet rs = existsPs.executeQuery()) {
                    rs.next();
                    exists = rs.getInt(1) > 0;
                }
            }

            if (exists) {
                try (PreparedStatement ps = connection.prepareStatement("update plan set topic_id = ?, student_id = ?, status = ?, created_at = ? where id = ?")) {
                    ps.setLong(1, plan.topicId());
                    ps.setLong(2, decodeUserId(UserRole.STUDENT, plan.studentId()));
                    ps.setString(3, plan.status().name());
                    ps.setDate(4, Date.valueOf(plan.createdAt().toLocalDate()));
                    ps.setLong(5, plan.id());
                    ps.executeUpdate();
                }
                try (PreparedStatement deleteWeeks = connection.prepareStatement("delete from plan_week where plan_id = ?")) {
                    deleteWeeks.setLong(1, plan.id());
                    deleteWeeks.executeUpdate();
                }
            } else {
                insertPlan(connection, plan.topicId(), decodeUserId(UserRole.STUDENT, plan.studentId()), plan.status());
            }

            Long effectivePlanId = exists ? plan.id() : findPlanByStudentId(plan.studentId()).map(Plan::id).orElseThrow();
            for (WeeklyTask task : plan.tasks()) {
                insertPlanWeek(connection, effectivePlanId, task.week(), task.title(), json(Map.of(
                        "deliverable", task.deliverable(),
                        "focus", task.focus()
                )));
            }
            return findPlanById(effectivePlanId).orElse(plan);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save plan", exception);
        }
    }

    @Override
    public Long nextReviewId() {
        return reviewSequence.incrementAndGet();
    }

    @Override
    public synchronized List<Review> findAllReviews() {
        return reviews.values().stream().sorted(Comparator.comparing(Review::createdAt).reversed()).toList();
    }

    @Override
    public synchronized Review saveReview(Review review) {
        reviews.put(review.id(), review);
        return review;
    }

    @Override
    public Long nextNotificationId() {
        return notificationSequence.incrementAndGet();
    }

    @Override
    public synchronized List<Notification> findAllNotifications() {
        List<Notification> result = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select id, user_id, title, message, created_at from workflow_notification order by created_at desc, id desc")) {
            while (rs.next()) {
                result.add(new Notification(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load notifications", exception);
        }
        return result;
    }

    @Override
    public synchronized Notification saveNotification(Notification notification) {
        try (Connection connection = getConnection()) {
            insertWorkflowNotification(connection, notification.id(), notification.userId(), notification.title(), notification.message(), notification.createdAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save notification", exception);
        }
        return notification;
    }

    @Override
    public Long nextAuditId() {
        return auditSequence.incrementAndGet();
    }

    @Override
    public synchronized List<AuditEntry> findAllAuditEntries() {
        return audits.values().stream().sorted(Comparator.comparing(AuditEntry::createdAt).reversed()).toList();
    }

    @Override
    public synchronized AuditEntry saveAuditEntry(AuditEntry auditEntry) {
        audits.put(auditEntry.id(), auditEntry);
        syncPlanResponse(auditEntry);
        return auditEntry;
    }

    private void syncPlanResponse(AuditEntry auditEntry) {
        if (!"PLAN".equals(auditEntry.entityType())) return;
        if (!auditEntry.action().contains("APPROVED") && !auditEntry.action().contains("REJECTED")) return;
        ApprovalStage stage = auditEntry.action().contains("DEPARTMENT") || "PLAN_APPROVED".equals(auditEntry.action())
                ? ApprovalStage.DEPARTMENT
                : ApprovalStage.TEACHER;
        String result = auditEntry.action().contains("REJECTED") ? "REJECTED" : "APPROVED";
        User actor = findAllUsers().stream().filter(user -> user.fullName().equals(auditEntry.actorName())).findFirst().orElse(null);
        if (actor == null) return;
        try (Connection connection = getConnection()) {
            insertPlanResponse(connection, auditEntry.entityId(), decodeUserId(actor.role(), actor.id()), stage.name(), result, auditEntry.detail());
        } catch (Exception ignored) {
        }
    }

    private void insertWorkflowNotification(Connection connection, Long id, Long userId, String title, String message, LocalDateTime createdAt) throws Exception {
        try (PreparedStatement check = connection.prepareStatement("select count(*) from workflow_notification where id = ?")) {
            check.setLong(1, id);
            try (ResultSet rs = check.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            update workflow_notification
                            set user_id = ?, title = ?, message = ?, created_at = ?
                            where id = ?
                            """)) {
                        update.setLong(1, userId);
                        update.setString(2, title);
                        update.setString(3, message);
                        update.setTimestamp(4, java.sql.Timestamp.valueOf(createdAt));
                        update.setLong(5, id);
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }

        try (PreparedStatement ps = connection.prepareStatement("""
                insert into workflow_notification(id, user_id, title, message, created_at)
                values (?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setTimestamp(5, java.sql.Timestamp.valueOf(createdAt));
            ps.executeUpdate();
        }
    }

    private Topic mapTopic(Connection connection, ResultSet rs) throws Exception {
        Map<String, Object> fields = parseJsonMap(rs.getString("fields"));
        Long topicId = rs.getLong("id");
        Long ownerStudentId = longValue(fields.get("ownerStudentId"));
        String ownerStudentName = stringValue(fields.get("ownerStudentName"));
        List<ApprovalRecord> approvals = mapApprovals(fields.get("approvals"));
        TopicStatus status = TopicStatus.valueOf(rs.getString("status"));
        if (ownerStudentId == null
                && (status == TopicStatus.PENDING_TEACHER_APPROVAL || status == TopicStatus.PENDING_DEPARTMENT_APPROVAL)) {
            try (PreparedStatement ps = connection.prepareStatement("select requested_by_id from topic_request where topic_id = ? order by id desc limit 1")) {
                ps.setLong(1, topicId);
                try (ResultSet requestRs = ps.executeQuery()) {
                    if (requestRs.next()) {
                        ownerStudentId = requestRs.getLong(1);
                        ownerStudentName = findUserById(encodeUserId(UserRole.STUDENT, ownerStudentId)).map(User::fullName).orElse(null);
                    }
                }
            }
        }
        return new Topic(
                topicId,
                stringValue(fields.get("title")),
                stringValue(fields.get("description")),
                rs.getString("program"),
                encodeUserId(UserRole.valueOf(rs.getString("created_by_type")), rs.getLong("created_by_id")),
                findUserById(encodeUserId(UserRole.valueOf(rs.getString("created_by_type")), rs.getLong("created_by_id"))).map(User::fullName).orElse("Unknown"),
                UserRole.valueOf(rs.getString("created_by_type")),
                ownerStudentId == null ? null : encodeUserId(UserRole.STUDENT, ownerStudentId),
                ownerStudentName,
                longValue(fields.get("advisorTeacherId")) == null ? null : encodeUserId(UserRole.TEACHER, longValue(fields.get("advisorTeacherId"))),
                stringValue(fields.get("advisorTeacherName")),
                status,
                localDateTime(rs.getDate("created_at")),
                localDateTime(rs.getDate("created_at")),
                approvals
        );
    }

    private void reconcileApprovedTopicsPerStudent() {
        Map<Long, List<Topic>> approvedTopicsByStudent = new LinkedHashMap<>();
        for (Topic topic : findAllTopics()) {
            if (topic.status() != TopicStatus.APPROVED || topic.ownerStudentId() == null) {
                continue;
            }
            approvedTopicsByStudent
                    .computeIfAbsent(topic.ownerStudentId(), ignored -> new ArrayList<>())
                    .add(topic);
        }

        approvedTopicsByStudent.values().forEach(topics -> {
            if (topics.size() < 2) {
                return;
            }
            topics.sort(Comparator.comparing(Topic::id).reversed());
            topics.stream().skip(1).forEach(topic -> {
                topic.supersede(LocalDateTime.now());
                saveTopic(topic);
            });
        });
    }

    private Plan mapPlan(Connection connection, ResultSet rs) throws Exception {
        Long planId = rs.getLong("id");
        Long topicId = rs.getLong("topic_id");
        Long studentId = rs.getLong("student_id");
        Map<String, Object> topicFields = parseJsonMap(rs.getString("fields"));
        List<WeeklyTask> tasks = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("select * from plan_week where plan_id = ? order by week_number")) {
            ps.setLong(1, planId);
            try (ResultSet weekRs = ps.executeQuery()) {
                while (weekRs.next()) {
                    Map<String, Object> result = parseJsonMap(weekRs.getString("result"));
                    tasks.add(new WeeklyTask(
                            weekRs.getInt("week_number"),
                            weekRs.getString("task"),
                            stringValue(result.get("deliverable")),
                            stringValue(result.get("focus"))
                    ));
                }
            }
        }
        List<ApprovalRecord> approvals = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("select * from plan_response where plan_id = ? order by id")) {
            ps.setLong(1, planId);
            try (ResultSet responseRs = ps.executeQuery()) {
                while (responseRs.next()) {
                    Long actorId = responseRs.getLong("approver_id");
                    String actorType = responseRs.getString("approver_type");
                    String res = responseRs.getString("res");
                    approvals.add(new ApprovalRecord(
                            ApprovalStage.valueOf(actorType),
                            encodeUserId(ApprovalStage.valueOf(actorType) == ApprovalStage.TEACHER ? UserRole.TEACHER : UserRole.DEPARTMENT, actorId),
                            findUserById(encodeUserId(ApprovalStage.valueOf(actorType) == ApprovalStage.TEACHER ? UserRole.TEACHER : UserRole.DEPARTMENT, actorId)).map(User::fullName).orElse("Unknown"),
                            "APPROVED".equalsIgnoreCase(res),
                            responseRs.getString("note"),
                            localDateTime(responseRs.getDate("res_date"))
                    ));
                }
            }
        }
        return new Plan(
                planId,
                topicId,
                stringValue(topicFields.get("title")),
                encodeUserId(UserRole.STUDENT, studentId),
                findUserById(encodeUserId(UserRole.STUDENT, studentId)).map(User::fullName).orElse("Unknown"),
                PlanStatus.valueOf(rs.getString("status")),
                tasks,
                approvals,
                localDateTime(rs.getDate("created_at")),
                localDateTime(rs.getDate("created_at"))
        );
    }

    private long insertDepartment(Connection connection, String name) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("insert into department(name, programs, admin) values (?, ?::json, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, "[\"B.SE\"]");
            ps.setString(3, "sisi-admin");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long ensureDefaultDepartment(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select id from department order by id limit 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return insertDepartment(connection, SOFTWARE_ENGINEERING);
    }

    private long insertStudent(Connection connection, long depId, String firstName, String lastName, String mail, String program, String sisiId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into student(dep_id, firstname, lastname, mail, program, sisi_id, is_choosed, proposed_number)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, depId);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            ps.setString(4, mail);
            ps.setString(5, program);
            ps.setString(6, sisiId);
            ps.setBoolean(7, false);
            ps.setInt(8, 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertTeacher(Connection connection, long depId, String firstName, String lastName, String mail) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into teacher(dep_id, firstname, lastname, mail, num_of_choosed_stud)
                values (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, depId);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            ps.setString(4, mail);
            ps.setInt(5, 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertTopic(Connection connection, Long createdById, UserRole createdByType, String program, TopicStatus status, Map<String, Object> payload) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into topic(created_at, created_by_id, created_by_type, fields, form_id, program, status)
                values (?, ?, ?, ?::json, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(LocalDate.now()));
            ps.setLong(2, createdById);
            ps.setString(3, createdByType.name());
            ps.setString(4, json(payload));
            ps.setNull(5, Types.BIGINT);
            ps.setString(6, program);
            ps.setString(7, status.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertTopicRequest(Connection connection, Long topicId, Long requestedById, UserRole requestedByType, boolean selected, String note) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into topic_request(is_selected, req_note, req_text, requested_by_id, requested_by_type, selected_at, topic_id)
                values (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setBoolean(1, selected);
            ps.setString(2, note);
            ps.setString(3, note);
            ps.setLong(4, requestedById);
            ps.setString(5, requestedByType.name());
            ps.setDate(6, Date.valueOf(LocalDate.now()));
            ps.setLong(7, topicId);
            ps.executeUpdate();
        }
    }

    private void upsertTopicRequest(Connection connection, Long topicId, Long requestedById, UserRole requestedByType, boolean selected, String note) throws Exception {
        try (PreparedStatement check = connection.prepareStatement("select id from topic_request where topic_id = ? order by id desc limit 1")) {
            check.setLong(1, topicId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            update topic_request
                            set is_selected = ?, req_note = ?, req_text = ?, requested_by_id = ?, requested_by_type = ?, selected_at = ?
                            where id = ?
                            """)) {
                        update.setBoolean(1, selected);
                        update.setString(2, note);
                        update.setString(3, note);
                        update.setLong(4, requestedById);
                        update.setString(5, requestedByType.name());
                        update.setDate(6, Date.valueOf(LocalDate.now()));
                        update.setLong(7, rs.getLong(1));
                        update.executeUpdate();
                    }
                } else {
                    insertTopicRequest(connection, topicId, requestedById, requestedByType, selected, note);
                }
            }
        }
    }

    private long insertPlan(Connection connection, Long topicId, Long studentId, PlanStatus status) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into plan(created_at, status, student_id, topic_id)
                values (?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(LocalDate.now()));
            ps.setString(2, status.name());
            ps.setLong(3, studentId);
            ps.setLong(4, topicId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertPlanWeek(Connection connection, Long planId, int week, String task, String result) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("insert into plan_week(plan_id, result, task, week_number) values (?, ?::jsonb, ?, ?)")) {
            ps.setLong(1, planId);
            ps.setString(2, result);
            ps.setString(3, task);
            ps.setInt(4, week);
            ps.executeUpdate();
        }
    }

    private void insertPlanResponse(Connection connection, Long planId, Long approverId, String approverType, String res, String note) throws Exception {
        try (PreparedStatement check = connection.prepareStatement("select count(*) from plan_response where plan_id = ? and approver_type = ?")) {
            check.setLong(1, planId);
            check.setString(2, approverType);
            try (ResultSet rs = check.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            update plan_response set approver_id = ?, note = ?, res = ?, res_date = ? where plan_id = ? and approver_type = ?
                            """)) {
                        update.setLong(1, approverId);
                        update.setString(2, note);
                        update.setString(3, res);
                        update.setDate(4, Date.valueOf(LocalDate.now()));
                        update.setLong(5, planId);
                        update.setString(6, approverType);
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into plan_response(approver_id, approver_type, note, plan_id, res, res_date)
                values (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, approverId);
            ps.setString(2, approverType);
            ps.setString(3, note);
            ps.setLong(4, planId);
            ps.setString(5, res);
            ps.setDate(6, Date.valueOf(LocalDate.now()));
            ps.executeUpdate();
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(url, username, password);
    }

    private Map<String, Object> payload(String title, String description, Long ownerStudentId, String ownerStudentName,
                                        Long advisorTeacherId, String advisorTeacherName, List<ApprovalRecord> approvals) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("description", description);
        payload.put("ownerStudentId", ownerStudentId);
        payload.put("ownerStudentName", ownerStudentName);
        payload.put("advisorTeacherId", advisorTeacherId);
        payload.put("advisorTeacherName", advisorTeacherName);
        payload.put("approvals", serializeApprovals(approvals));
        return payload;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> parseJsonMap(String value) throws Exception {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        return objectMapper.readValue(value, new TypeReference<>() {});
    }

    private List<ApprovalRecord> mapApprovals(Object approvalsObject) {
        if (!(approvalsObject instanceof List<?> items)) return List.of();
        List<ApprovalRecord> approvals = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                approvals.add(new ApprovalRecord(
                        ApprovalStage.valueOf(stringValue(map.get("stage"))),
                        longValue(map.get("actorId")),
                        stringValue(map.get("actorName")),
                        Boolean.TRUE.equals(map.get("approved")),
                        stringValue(map.get("note")),
                        map.get("decidedAt") == null ? LocalDateTime.now() : LocalDateTime.parse(String.valueOf(map.get("decidedAt")))
                ));
            }
        }
        return approvals;
    }

    private List<Map<String, Object>> serializeApprovals(List<ApprovalRecord> approvals) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ApprovalRecord approval : approvals) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("stage", approval.stage().name());
            map.put("actorId", approval.actorId());
            map.put("actorName", approval.actorName());
            map.put("approved", approval.approved());
            map.put("note", approval.note());
            map.put("decidedAt", approval.decidedAt().toString());
            items.add(map);
        }
        return items;
    }

    private LocalDateTime localDateTime(Date date) {
        return date == null ? LocalDateTime.now() : date.toLocalDate().atStartOfDay();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        String string = String.valueOf(value);
        return string.isBlank() || "null".equalsIgnoreCase(string) ? null : Long.parseLong(string);
    }

    private boolean idEquals(long a, Long b) {
        return b != null && a == b;
    }

    private long encodeUserId(UserRole role, long rawId) {
        return switch (role) {
            case STUDENT -> STUDENT_OFFSET + rawId;
            case TEACHER -> TEACHER_OFFSET + rawId;
            case DEPARTMENT -> DEPARTMENT_OFFSET + rawId;
        };
    }

    private long decodeUserId(UserRole role, long encodedId) {
        return switch (role) {
            case STUDENT -> encodedId - STUDENT_OFFSET;
            case TEACHER -> encodedId - TEACHER_OFFSET;
            case DEPARTMENT -> encodedId - DEPARTMENT_OFFSET;
        };
    }

    private Long decodeNullableUserId(UserRole role, Long encodedId) {
        return encodedId == null ? null : decodeUserId(role, encodedId);
    }

    private String teacherLoginId(long rawId) {
        return "tch" + String.format("%03d", rawId);
    }

}
