package com.tms.thesissystem.infrastructure.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.model.ApprovalRecord;
import com.tms.thesissystem.domain.model.ApprovalStage;
import com.tms.thesissystem.domain.model.AuditEntry;
import com.tms.thesissystem.domain.model.Notification;
import com.tms.thesissystem.domain.model.Plan;
import com.tms.thesissystem.domain.model.PlanStatus;
import com.tms.thesissystem.domain.model.Review;
import com.tms.thesissystem.domain.model.Topic;
import com.tms.thesissystem.domain.model.TopicStatus;
import com.tms.thesissystem.domain.model.User;
import com.tms.thesissystem.domain.model.UserRole;
import com.tms.thesissystem.domain.model.WeeklyTask;
import org.springframework.beans.factory.annotation.Value;
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
public class InMemoryWorkflowRepository implements WorkflowRepository {
    private static final long STUDENT_OFFSET = 100_000L;
    private static final long TEACHER_OFFSET = 200_000L;
    private static final long DEPARTMENT_OFFSET = 300_000L;

    private final String url;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, Review> reviews = new LinkedHashMap<>();
    private final Map<Long, Notification> notifications = new LinkedHashMap<>();
    private final Map<Long, AuditEntry> audits = new LinkedHashMap<>();
    private final AtomicLong reviewSequence = new AtomicLong(3100);
    private final AtomicLong notificationSequence = new AtomicLong(4100);
    private final AtomicLong auditSequence = new AtomicLong(5100);

    public InMemoryWorkflowRepository(@Value("${app.database.url}") String url,
                                      @Value("${app.database.username}") String username,
                                      @Value("${app.database.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        seedIfEmpty();
    }

    private void seedIfEmpty() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from student")) {
            resultSet.next();
            if (resultSet.getInt(1) > 0) {
                return;
            }
            connection.setAutoCommit(false);
            try {
                long departmentId = insertDepartment(connection, "Software Engineering");
                long studentAId = insertStudent(connection, departmentId, "Anu", "Bat", "anu@tms.mn", "B.SE", "22B1NUM0027");
                long studentBId = insertStudent(connection, departmentId, "Temuulen", "Dorj", "temuulen@tms.mn", "B.SE", "22B1NUM0028");
                long teacherAId = insertTeacher(connection, departmentId, "Enkh", "Suren", "enkh@tms.mn");
                long teacherBId = insertTeacher(connection, departmentId, "Bolor", "Naran", "bolor@tms.mn");
                long teacherCId = insertTeacher(connection, departmentId, "Saruul", "Munkh", "saruul@tms.mn");

                long availableA = insertTopic(connection, teacherAId, UserRole.TEACHER, "B.SE", TopicStatus.AVAILABLE,
                        payload("AI-based Thesis Workflow Automation",
                                "Дипломын сэдэв дэвшүүлэлт, баталгаажуулалт, явцын хяналтыг автоматжуулах.",
                                null, null, null, null, List.of()));
                long availableB = insertTopic(connection, teacherBId, UserRole.TEACHER, "B.SE", TopicStatus.AVAILABLE,
                        payload("Event-driven Student Research Tracker",
                                "Судалгааны milestone, reminder, review процессыг event bus ашиглан удирдах.",
                                null, null, null, null, List.of()));

                long approvedTopicId = insertTopic(connection, studentBId, UserRole.STUDENT, "B.SE", TopicStatus.APPROVED,
                        payload("Layered Architecture for Graduation Management",
                                "Тэнхим, багш, оюутны approval flow-тай систем боловсруулах.",
                                studentBId, "Temuulen Dorj", teacherAId, "Enkh Suren", List.of()));
                insertTopicRequest(connection, approvedTopicId, studentBId, UserRole.STUDENT, true, "Seed request");

                long planId = insertPlan(connection, approvedTopicId, studentBId, PlanStatus.APPROVED);
                for (int week = 1; week <= 15; week++) {
                    insertPlanWeek(connection, planId, week, "7 хоног " + week + " - milestone", json(Map.of(
                            "deliverable", "Deliverable " + week,
                            "focus", "Судалгаа, загварчлал, хэрэгжилт, тест"
                    )));
                }
                insertPlanResponse(connection, planId, teacherAId, "TEACHER", "APPROVED", "Seed teacher approval");
                insertPlanResponse(connection, planId, departmentId, "DEPARTMENT", "APPROVED", "Seed department approval");

                reviews.put(3001L, new Review(3001L, planId, 4, encodeUserId(UserRole.TEACHER, teacherAId), "Enkh Suren", 92,
                        "Судалгааны хэсэг сайн, implementation-ийн architecture section-ийг гүнзгийрүүл.", LocalDateTime.now().minusDays(1)));
                notifications.put(4001L, new Notification(4001L, encodeUserId(UserRole.STUDENT, studentBId), "Төлөвлөгөө батлагдсан",
                        "15 долоо хоногийн төлөвлөгөөг тэнхим баталгаажууллаа.", LocalDateTime.now().minusHours(6)));
                audits.put(5001L, new AuditEntry(5001L, "PLAN", planId, "PLAN_APPROVED", "SE Department",
                        "Тэнхим төлөвлөгөөг эцэслэн баталж review phase-ийг нээлээ.", LocalDateTime.now().minusHours(6)));

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Database seed failed", exception);
        }
    }

    @Override
    public synchronized List<User> findAllUsers() {
        List<User> users = new ArrayList<>();
        try (Connection connection = getConnection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select s.id, s.firstname, s.lastname, s.mail, s.program, d.name from student s left join department d on d.id = s.dep_id order by s.id")) {
                while (rs.next()) {
                    users.add(new User(encodeUserId(UserRole.STUDENT, rs.getLong(1)), UserRole.STUDENT, rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(6), rs.getString(5)));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select t.id, t.firstname, t.lastname, t.mail, d.name from teacher t left join department d on d.id = t.dep_id order by t.id")) {
                while (rs.next()) {
                    users.add(new User(encodeUserId(UserRole.TEACHER, rs.getLong(1)), UserRole.TEACHER, rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), null));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select id, name from department order by id")) {
                while (rs.next()) {
                    users.add(new User(encodeUserId(UserRole.DEPARTMENT, rs.getLong(1)), UserRole.DEPARTMENT, rs.getString(2), "Department", rs.getString(2).toLowerCase().replace(" ", "") + "@tms.mn", rs.getString(2), null));
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
        return notifications.values().stream().sorted(Comparator.comparing(Notification::createdAt).reversed()).toList();
    }

    @Override
    public synchronized Notification saveNotification(Notification notification) {
        notifications.put(notification.id(), notification);
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

    private Topic mapTopic(Connection connection, ResultSet rs) throws Exception {
        Map<String, Object> fields = parseJsonMap(rs.getString("fields"));
        Long topicId = rs.getLong("id");
        Long ownerStudentId = longValue(fields.get("ownerStudentId"));
        String ownerStudentName = stringValue(fields.get("ownerStudentName"));
        List<ApprovalRecord> approvals = mapApprovals(fields.get("approvals"));
        if (ownerStudentId == null) {
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
                TopicStatus.valueOf(rs.getString("status")),
                localDateTime(rs.getDate("created_at")),
                localDateTime(rs.getDate("created_at")),
                approvals
        );
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
        try (PreparedStatement ps = connection.prepareStatement("insert into department(name, programs) values (?, ?::json)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, "[\"B.SE\"]");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
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
        try (PreparedStatement ps = connection.prepareStatement("insert into plan_week(plan_id, result, task, week_number) values (?, ?, ?, ?)")) {
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
}
