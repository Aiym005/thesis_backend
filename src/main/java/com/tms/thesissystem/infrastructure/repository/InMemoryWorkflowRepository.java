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
public class InMemoryWorkflowRepository implements WorkflowRepository {
    private static final long STUDENT_OFFSET = 100_000L;
    private static final long TEACHER_OFFSET = 200_000L;
    private static final long DEPARTMENT_OFFSET = 300_000L;
    private static final String SOFTWARE_ENGINEERING = "Software Engineering";
    private static final int MIN_STUDENT_COUNT = 100;
    private static final int MIN_TEACHER_COUNT = 20;

    private final String url;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, Review> reviews = new LinkedHashMap<>();
    private final Map<Long, AuditEntry> audits = new LinkedHashMap<>();
    private final AtomicLong reviewSequence = new AtomicLong(3100);
    private final AtomicLong notificationSequence = new AtomicLong(4100);
    private final AtomicLong auditSequence = new AtomicLong(5100);

    private record SeedStudent(String firstName, String lastName, String email, String program, String sisiId) {
        String fullName() { return firstName + " " + lastName; }
    }

    private record SeedTeacher(String firstName, String lastName, String email, String loginId) {
        String fullName() { return firstName + " " + lastName; }
    }

    public InMemoryWorkflowRepository(@Value("${app.database.url}") String url,
                                      @Value("${app.database.username}") String username,
                                      @Value("${app.database.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        ensureCoreTables();
        ensureProjectionTables();
        ensureIdentityColumns();
        seedIfEmpty();
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

    private void seedIfEmpty() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from student")) {
            resultSet.next();
            connection.setAutoCommit(false);
            try {
                List<SeedStudent> students = studentSeeds();
                List<SeedTeacher> teachers = teacherSeeds();
                if (resultSet.getInt(1) == 0) {
                    long softwareEngineeringDepartmentId = insertDepartment(connection, SOFTWARE_ENGINEERING);

                    long studentAId = insertStudent(connection, softwareEngineeringDepartmentId, students.get(0));
                    long studentBId = insertStudent(connection, softwareEngineeringDepartmentId, students.get(1));
                    for (int index = 2; index < students.size(); index++) {
                        insertStudent(connection, softwareEngineeringDepartmentId, students.get(index));
                    }

                    long teacherAId = insertTeacher(connection, softwareEngineeringDepartmentId, teachers.get(0));
                    long teacherBId = insertTeacher(connection, softwareEngineeringDepartmentId, teachers.get(1));
                    long teacherCId = insertTeacher(connection, softwareEngineeringDepartmentId, teachers.get(2));
                    for (int index = 3; index < teachers.size(); index++) {
                        insertTeacher(connection, softwareEngineeringDepartmentId, teachers.get(index));
                    }

                    ensureCatalogTopic(connection, teacherAId, "B.SE", "AI-based Thesis Workflow Automation",
                            "Дипломын сэдэв дэвшүүлэлт, баталгаажуулалт, явцын хяналтыг автоматжуулах.");
                    ensureCatalogTopic(connection, teacherBId, "B.SE", "Event-driven Student Research Tracker",
                            "Судалгааны milestone, reminder, review процессыг event bus ашиглан удирдах.");
                    ensureCatalogTopic(connection, teacherCId, "B.DS", "Data-driven Research Planning",
                            "Өгөгдөлд суурилсан судалгааны төлөвлөлт ба үнэлгээний систем.");

                    long approvedTopicId = insertTopic(connection, studentBId, UserRole.STUDENT, "B.SE", TopicStatus.APPROVED,
                            payload("Layered Architecture for Graduation Management",
                                    "Тэнхим, багш, оюутны approval flow-тай систем боловсруулах.",
                                    studentBId, students.get(1).fullName(), teacherAId, teachers.get(0).fullName(), List.of()));
                    insertTopicRequest(connection, approvedTopicId, studentBId, UserRole.STUDENT, true, "Seed request");

                    long pendingTeacherTopicId = insertTopic(connection, studentAId, UserRole.STUDENT, "B.SE", TopicStatus.PENDING_TEACHER_APPROVAL,
                            payload("Distributed Thesis Workflow",
                                    "Оюутны санал болгосон дипломын workflow automation сэдэв.",
                                    studentAId, students.get(0).fullName(), null, null, List.of()));
                    insertTopicRequest(connection, pendingTeacherTopicId, studentAId, UserRole.STUDENT, true, "Seed pending teacher request");

                    long planId = insertPlan(connection, approvedTopicId, studentBId, PlanStatus.APPROVED);
                    for (int week = 1; week <= 15; week++) {
                        insertPlanWeek(connection, planId, week, "7 хоног " + week + " - milestone", json(Map.of(
                                "deliverable", "Deliverable " + week,
                                "focus", "Судалгаа, загварчлал, хэрэгжилт, тест"
                        )));
                    }
                    insertPlanResponse(connection, planId, teacherAId, "TEACHER", "APPROVED", "Seed teacher approval");
                    insertPlanResponse(connection, planId, softwareEngineeringDepartmentId, "DEPARTMENT", "APPROVED", "Seed department approval");

                    reviews.put(3001L, new Review(3001L, planId, 4, encodeUserId(UserRole.TEACHER, teacherAId), teachers.get(0).fullName(), 92,
                            "Судалгааны хэсэг сайн, implementation-ийн architecture section-ийг гүнзгийрүүл.", LocalDateTime.now().minusDays(1)));
                    insertWorkflowNotification(connection, 4001L, encodeUserId(UserRole.STUDENT, studentBId), "Төлөвлөгөө батлагдсан",
                            "15 долоо хоногийн төлөвлөгөөг тэнхим баталгаажууллаа.", LocalDateTime.now().minusHours(6));
                    audits.put(5001L, new AuditEntry(5001L, "PLAN", planId, "PLAN_APPROVED", "SE Department",
                            "Тэнхим төлөвлөгөөг эцэслэн баталж review phase-ийг нээлээ.", LocalDateTime.now().minusHours(6)));
                }
                ensureUsersForExistingData(connection);
                ensureCatalogTopicsForExistingData(connection);

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

    private void ensureUsersForExistingData(Connection connection) throws Exception {
        long softwareEngineeringDepartmentId = findOrCreateDepartment(connection, SOFTWARE_ENGINEERING);
        normalizeDepartmentAssignments(connection, softwareEngineeringDepartmentId);
        ensureMinimumStudents(connection, softwareEngineeringDepartmentId);
        ensureMinimumTeachers(connection, softwareEngineeringDepartmentId);
    }

    private void normalizeDepartmentAssignments(Connection connection, long departmentId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("update student set dep_id = ? where dep_id <> ? or dep_id is null")) {
            ps.setLong(1, departmentId);
            ps.setLong(2, departmentId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("update teacher set dep_id = ? where dep_id <> ? or dep_id is null")) {
            ps.setLong(1, departmentId);
            ps.setLong(2, departmentId);
            ps.executeUpdate();
        }
    }

    private void ensureMinimumStudents(Connection connection, long departmentId) throws Exception {
        List<Long> existingIds = existingStudentIds(connection);
        List<SeedStudent> seeds = studentSeeds();
        for (int index = 0; index < seeds.size(); index++) {
            SeedStudent seed = seeds.get(index);
            if (index < existingIds.size()) {
                updateStudent(connection, existingIds.get(index), departmentId, seed);
            } else {
                insertStudent(connection, departmentId, seed);
            }
        }
    }

    private void ensureMinimumTeachers(Connection connection, long departmentId) throws Exception {
        List<Long> existingIds = existingTeacherIds(connection);
        List<SeedTeacher> seeds = teacherSeeds();
        for (int index = 0; index < seeds.size(); index++) {
            SeedTeacher seed = seeds.get(index);
            if (index < existingIds.size()) {
                updateTeacher(connection, existingIds.get(index), departmentId, seed);
            } else {
                insertTeacher(connection, departmentId, seed);
            }
        }
    }

    private long findOrCreateDepartment(Connection connection, String name) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select id from department where name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return insertDepartment(connection, name);
    }

    private void ensureCatalogTopicsForExistingData(Connection connection) throws Exception {
        List<SeedTeacher> teachers = teacherSeeds();
        Long teacherAId = findTeacherIdByEmail(connection, teachers.get(0).email());
        Long teacherBId = findTeacherIdByEmail(connection, teachers.get(1).email());
        Long teacherCId = findTeacherIdByEmail(connection, teachers.get(2).email());

        if (countAvailableTopics(connection) == 0) {
            if (teacherAId != null) {
                insertTopic(connection, teacherAId, UserRole.TEACHER, "B.SE", TopicStatus.AVAILABLE,
                        payload("Open Catalog Topic A", "Оюутнууд сонгож болох нээлттэй сэдэв A.", null, null, null, null, List.of()));
            }
            if (teacherBId != null) {
                insertTopic(connection, teacherBId, UserRole.TEACHER, "B.SE", TopicStatus.AVAILABLE,
                        payload("Open Catalog Topic B", "Оюутнууд сонгож болох нээлттэй сэдэв B.", null, null, null, null, List.of()));
            }
            if (teacherCId != null) {
                insertTopic(connection, teacherCId, UserRole.TEACHER, "B.DS", TopicStatus.AVAILABLE,
                        payload("Open Catalog Topic C", "Оюутнууд сонгож болох нээлттэй сэдэв C.", null, null, null, null, List.of()));
            }
        }

        if (teacherAId != null) {
            ensureCatalogTopic(connection, teacherAId, "B.SE", "AI-based Thesis Workflow Automation",
                    "Дипломын сэдэв дэвшүүлэлт, баталгаажуулалт, явцын хяналтыг автоматжуулах.");
        }
        if (teacherBId != null) {
            ensureCatalogTopic(connection, teacherBId, "B.SE", "Event-driven Student Research Tracker",
                    "Судалгааны milestone, reminder, review процессыг event bus ашиглан удирдах.");
        }
        if (teacherCId != null) {
            ensureCatalogTopic(connection, teacherCId, "B.DS", "Data-driven Research Planning",
                    "Өгөгдөлд суурилсан судалгааны төлөвлөлт ба үнэлгээний систем.");
        }
    }

    private int countAvailableTopics(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select count(*) from topic where status = ?")) {
            ps.setString(1, TopicStatus.AVAILABLE.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void ensureCatalogTopic(Connection connection, Long teacherId, String program, String title, String description) throws Exception {
        if (topicExists(connection, title)) {
            return;
        }
        insertTopic(connection, teacherId, UserRole.TEACHER, program, TopicStatus.AVAILABLE,
                payload(title, description, null, null, null, null, List.of()));
    }

    private boolean topicExists(Connection connection, String title) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select count(*) from topic where fields::text ilike ?")) {
            ps.setString(1, "%\"title\":\"" + title + "\"%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private Long findTeacherIdByEmail(Connection connection, String email) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select id from teacher where mail = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return null;
            }
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

    private long insertStudent(Connection connection, long depId, SeedStudent student) throws Exception {
        return insertStudent(connection, depId, student.firstName(), student.lastName(), student.email(), student.program(), student.sisiId());
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

    private long insertTeacher(Connection connection, long depId, SeedTeacher teacher) throws Exception {
        return insertTeacher(connection, depId, teacher.firstName(), teacher.lastName(), teacher.email());
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

    private List<Long> existingStudentIds(Connection connection) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("select id from student order by id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private List<Long> existingTeacherIds(Connection connection) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("select id from teacher order by id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private void updateStudent(Connection connection, long id, long depId, SeedStudent student) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                update student
                set dep_id = ?, firstname = ?, lastname = ?, mail = ?, program = ?, sisi_id = ?, is_choosed = ?, proposed_number = ?
                where id = ?
                """)) {
            ps.setLong(1, depId);
            ps.setString(2, student.firstName());
            ps.setString(3, student.lastName());
            ps.setString(4, student.email());
            ps.setString(5, student.program());
            ps.setString(6, student.sisiId());
            ps.setBoolean(7, false);
            ps.setInt(8, 0);
            ps.setLong(9, id);
            ps.executeUpdate();
        }
    }

    private void updateTeacher(Connection connection, long id, long depId, SeedTeacher teacher) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                update teacher
                set dep_id = ?, firstname = ?, lastname = ?, mail = ?, num_of_choosed_stud = ?
                where id = ?
                """)) {
            ps.setLong(1, depId);
            ps.setString(2, teacher.firstName());
            ps.setString(3, teacher.lastName());
            ps.setString(4, teacher.email());
            ps.setInt(5, 0);
            ps.setLong(6, id);
            ps.executeUpdate();
        }
    }

    private List<SeedStudent> studentSeeds() {
        return List.of(
                new SeedStudent("Ану", "Бат-Эрдэнэ", "anu.bat-erdene@tms.mn", "B.SE", "22b1num0027"),
                new SeedStudent("Тэмүүлэн", "Дорж", "temuulen.dorj@tms.mn", "B.SE", "22b1num0028"),
                new SeedStudent("Номин", "Эрдэнэ", "nomin.erdene@tms.mn", "B.SE", "22b1num0029"),
                new SeedStudent("Марал", "Мөнхбат", "maral.munkhbat@tms.mn", "B.SE", "22b1num0030"),
                new SeedStudent("Энхжин", "Ганзориг", "enkhjin.ganzorig@tms.mn", "B.SE", "22b1num0031"),
                new SeedStudent("Билгүүн", "Түвшинжаргал", "bilguun.tuvshinjargal@tms.mn", "B.SE", "22b1num0032"),
                new SeedStudent("Сувд", "Лхагвасүрэн", "suvd.lkhagvasuren@tms.mn", "B.SE", "22b1num0033"),
                new SeedStudent("Одгэрэл", "Батсүх", "odgerel.batsukh@tms.mn", "B.SE", "22b1num0034"),
                new SeedStudent("Мөнхжин", "Пүрэвдорж", "munkhjin.purevdorj@tms.mn", "B.SE", "22b1num0035"),
                new SeedStudent("Уянга", "Сандаг", "uyanga.sandag@tms.mn", "B.SE", "22b1num0036"),
                new SeedStudent("Төгөлдөр", "Алтанхуяг", "tuguldur.altankhuyag@tms.mn", "B.SE", "22b1num0037"),
                new SeedStudent("Хүслэн", "Очирбат", "khuslen.ochirbat@tms.mn", "B.SE", "22b1num0038"),
                new SeedStudent("Ариунболд", "Нямдорж", "ariunbold.nyamdorj@tms.mn", "B.SE", "22b1num0039"),
                new SeedStudent("Намуун", "Жаргалсайхан", "namuun.jargalsaikhan@tms.mn", "B.SE", "22b1num0040"),
                new SeedStudent("Бат-Оргил", "Эрдэнэбат", "bat-orgil.erdenebat@tms.mn", "B.SE", "22b1num0041"),
                new SeedStudent("Дөлгөөн", "Мягмарсүрэн", "dulguun.myagmarsuren@tms.mn", "B.SE", "22b1num0042"),
                new SeedStudent("Жавхлан", "Бямбасүрэн", "javkhlan.byambasuren@tms.mn", "B.SE", "22b1num0043"),
                new SeedStudent("Пүрэвсүрэн", "Ганбат", "purevsuren.ganbat@tms.mn", "B.SE", "22b1num0044"),
                new SeedStudent("Саруул", "Даваажав", "saruul.davaajav@tms.mn", "B.SE", "22b1num0045"),
                new SeedStudent("Анхбаяр", "Болдбаатар", "ankhbayar.boldbaatar@tms.mn", "B.SE", "22b1num0046"),
                new SeedStudent("Мишээл", "Төмөрбаатар", "misheel.tumurbaatar@tms.mn", "B.SE", "22b1num0047"),
                new SeedStudent("Амин-Эрдэнэ", "Цогтбаяр", "amin-erdene.tsogtbayar@tms.mn", "B.SE", "22b1num0048"),
                new SeedStudent("Ивээл", "Энхтүвшин", "iveel.enkhtuvshin@tms.mn", "B.SE", "22b1num0049"),
                new SeedStudent("Сэцэн", "Баттулга", "setsen.battulga@tms.mn", "B.SE", "22b1num0050"),
                new SeedStudent("Тэнүүн", "Адьяа", "tenuun.adiyaa@tms.mn", "B.SE", "22b1num0051"),
                new SeedStudent("Гэгээ", "Чинзориг", "gegee.chinzorig@tms.mn", "B.SE", "22b1num0052"),
                new SeedStudent("Хонгор", "Амарбаясгалан", "khongor.amarbayasgalan@tms.mn", "B.SE", "22b1num0053"),
                new SeedStudent("Мөнгөнцэцэг", "Самдан", "munguntsetseg.samdan@tms.mn", "B.SE", "22b1num0054"),
                new SeedStudent("Эрдэнэсувд", "Рэнцэн", "erdenesuvd.rentsen@tms.mn", "B.SE", "22b1num0055"),
                new SeedStudent("Тэлмүүн", "Цэрэндаш", "telmuun.tserendash@tms.mn", "B.SE", "22b1num0056"),
                new SeedStudent("Ариунзул", "Гомбосүрэн", "ariunzul.gombosuren@tms.mn", "B.SE", "22b1num0057"),
                new SeedStudent("Хулан", "Лувсан", "khulan.luvsan@tms.mn", "B.SE", "22b1num0058"),
                new SeedStudent("Мөнхтөр", "Сүхбаатар", "munkhtur.sukhbaatar@tms.mn", "B.SE", "22b1num0059"),
                new SeedStudent("Содном", "Чулуунбат", "sodnom.chuluunbat@tms.mn", "B.SE", "22b1num0060"),
                new SeedStudent("Ирмүүн", "Цэндсүрэн", "irmuun.tsendsuren@tms.mn", "B.SE", "22b1num0061"),
                new SeedStudent("Наранзул", "Очирхуяг", "naranzul.ochirkhuyag@tms.mn", "B.SE", "22b1num0062"),
                new SeedStudent("Тэмүүжин", "Галбадрах", "temuujin.galbadrah@tms.mn", "B.SE", "22b1num0063"),
                new SeedStudent("Сэлэнгэ", "Гэрэлтуяа", "selenge.gereltuya@tms.mn", "B.SE", "22b1num0064"),
                new SeedStudent("Даваахүү", "Цогзолмаа", "davaakhuu.tsogzolmaa@tms.mn", "B.SE", "22b1num0065"),
                new SeedStudent("Оюунчимэг", "Төрбат", "oyuunchimeg.turbat@tms.mn", "B.SE", "22b1num0066"),
                new SeedStudent("Баянмөнх", "Эрдэнэчимэг", "bayanmunkh.erdenechimeg@tms.mn", "B.SE", "22b1num0067"),
                new SeedStudent("Цэлмэг", "Сайнбаяр", "tselmeg.sainbayar@tms.mn", "B.SE", "22b1num0068"),
                new SeedStudent("Ган-Эрдэнэ", "Тогтох", "gan-erdene.togtokh@tms.mn", "B.SE", "22b1num0069"),
                new SeedStudent("Энэрэл", "Мөнгөншагай", "enerel.mungunshagai@tms.mn", "B.SE", "22b1num0070"),
                new SeedStudent("Чингүүн", "Отгонбаяр", "chinguun.otgonbayar@tms.mn", "B.SE", "22b1num0071"),
                new SeedStudent("Алтанзаяа", "Цэрэндолгор", "altanzayaa.tserendolgor@tms.mn", "B.SE", "22b1num0072"),
                new SeedStudent("Отгончимэг", "Хишигт", "otgonchimeg.khishigt@tms.mn", "B.SE", "22b1num0073"),
                new SeedStudent("Золбоо", "Түвдэндорж", "zolboo.tuvdendorj@tms.mn", "B.SE", "22b1num0074"),
                new SeedStudent("Мөнх-Оргил", "Зоригт", "munkh-orgil.zorigt@tms.mn", "B.SE", "22b1num0075"),
                new SeedStudent("Хулангоо", "Чойжилсүрэн", "khulangoo.choijilsuren@tms.mn", "B.SE", "22b1num0076"),
                new SeedStudent("Ундрах", "Энх-Амгалан", "undrakh.enkh-amgalan@tms.mn", "B.SE", "22b1num0077"),
                new SeedStudent("Батчимэг", "Жамъян", "batchimeg.jamyan@tms.mn", "B.SE", "22b1num0078"),
                new SeedStudent("Түвшинтөгс", "Наранбаатар", "tuvshintugs.naranbaatar@tms.mn", "B.SE", "22b1num0079"),
                new SeedStudent("Содгэрэл", "Дашдондог", "sodgerel.dashdondog@tms.mn", "B.SE", "22b1num0080"),
                new SeedStudent("Баясгалан", "Хүрэлбаатар", "bayasgalan.khurelbaatar@tms.mn", "B.SE", "22b1num0081"),
                new SeedStudent("Эгшиглэн", "Гончиг", "egshiglen.gonchig@tms.mn", "B.SE", "22b1num0082"),
                new SeedStudent("Сүндэр", "Эрдэнэпүрэв", "sunder.erdenepurev@tms.mn", "B.SE", "22b1num0083"),
                new SeedStudent("Төгс-Эрдэнэ", "Насанбуян", "tugs-erdene.nasanbuyan@tms.mn", "B.SE", "22b1num0084"),
                new SeedStudent("Оюунгэрэл", "Түмэнжаргал", "oyungerel.tumenjargal@tms.mn", "B.SE", "22b1num0085"),
                new SeedStudent("Золзаяа", "Сэргэлэн", "zolzayaa.sergelen@tms.mn", "B.SE", "22b1num0086"),
                new SeedStudent("Эрдэнэболд", "Базар", "erdenebold.bazar@tms.mn", "B.SE", "22b1num0087"),
                new SeedStudent("Мөнхдөл", "Гэрэлчулуун", "munkhdul.gerelchuluun@tms.mn", "B.SE", "22b1num0088"),
                new SeedStudent("Гэрэлмаа", "Дамбадаржаа", "gerelmaa.dambadarjaa@tms.mn", "B.SE", "22b1num0089"),
                new SeedStudent("Бадамцэцэг", "Лхасүрэн", "badamtsetseg.lkhasuren@tms.mn", "B.SE", "22b1num0090"),
                new SeedStudent("Отгонтөгс", "Мөнхсайхан", "otgontugs.munkhsaikhan@tms.mn", "B.SE", "22b1num0091"),
                new SeedStudent("Сувд-Эрдэнэ", "Борхүү", "suvd-erdene.borkhuu@tms.mn", "B.SE", "22b1num0092"),
                new SeedStudent("Тэнгэр", "Шинэбаяр", "tenger.shinebayar@tms.mn", "B.SE", "22b1num0093"),
                new SeedStudent("Гүнжид", "Бумцэнд", "gunjid.bumtsend@tms.mn", "B.SE", "22b1num0094"),
                new SeedStudent("Жаргалмаа", "Сэржмядаг", "jargalmaa.serjmyadag@tms.mn", "B.SE", "22b1num0095"),
                new SeedStudent("Чинзориг", "Хатанбаатар", "chinzorig.khatanbaatar@tms.mn", "B.SE", "22b1num0096"),
                new SeedStudent("Түвшинжаргал", "Эрдэнэ-Очир", "tuvshinjargal.erdene-ochir@tms.mn", "B.SE", "22b1num0097"),
                new SeedStudent("Энхсаран", "Норов", "enkhsaran.norov@tms.mn", "B.SE", "22b1num0098"),
                new SeedStudent("Батнасан", "Мэндсайхан", "batnasan.mendsaikhan@tms.mn", "B.SE", "22b1num0099"),
                new SeedStudent("Солонго", "Цогт", "solongo.tsogt@tms.mn", "B.SE", "22b1num0100"),
                new SeedStudent("Урангоо", "Даваасамбуу", "urangoo.davaasambuu@tms.mn", "B.SE", "22b1num0101"),
                new SeedStudent("Эрдэнэзул", "Амгаланбаатар", "erdenezul.amgalanbaatar@tms.mn", "B.SE", "22b1num0102"),
                new SeedStudent("Батсүрэн", "Нармандах", "batsuren.narmandakh@tms.mn", "B.SE", "22b1num0103"),
                new SeedStudent("Гантулга", "Төгөлдөр", "gantulga.tuguldur@tms.mn", "B.SE", "22b1num0104"),
                new SeedStudent("Сарнай", "Отгонсүх", "sarnai.otgonsukh@tms.mn", "B.SE", "22b1num0105"),
                new SeedStudent("Энхриймаа", "Гомбожав", "enkhriimaa.gombojav@tms.mn", "B.SE", "22b1num0106"),
                new SeedStudent("Мөнхцацрал", "Батдэлгэр", "munkhtsatsral.batdelger@tms.mn", "B.SE", "22b1num0107"),
                new SeedStudent("Өлзийжаргал", "Ганпүрэв", "ulziijargal.ganpurev@tms.mn", "B.SE", "22b1num0108"),
                new SeedStudent("Чимгээ", "Төрмөнх", "chimgee.turmunkh@tms.mn", "B.SE", "22b1num0109"),
                new SeedStudent("Нарансолонго", "Лхагважав", "naransolongo.lkhagvajav@tms.mn", "B.SE", "22b1num0110"),
                new SeedStudent("Дэлгэрзаяа", "Баяржаргал", "delgerzayaa.bayarjargal@tms.mn", "B.SE", "22b1num0111"),
                new SeedStudent("Сүхбат", "Мөнхбаатар", "sukhbat.munkhbaatar@tms.mn", "B.SE", "22b1num0112"),
                new SeedStudent("Баярцэцэг", "Чинбат", "bayartsetseg.chinbat@tms.mn", "B.SE", "22b1num0113"),
                new SeedStudent("Оргилуун", "Отгонжаргал", "orgiluun.otgonjargal@tms.mn", "B.SE", "22b1num0114"),
                new SeedStudent("Анужин", "Цэрэнпил", "anujin.tserenpil@tms.mn", "B.SE", "22b1num0115"),
                new SeedStudent("Тодгэрэл", "Жигжид", "todgerel.jigjid@tms.mn", "B.SE", "22b1num0116"),
                new SeedStudent("Цэлмүүн", "Баясгалант", "tselmuun.bayasgalant@tms.mn", "B.SE", "22b1num0117"),
                new SeedStudent("Сарантуяа", "Гүррагчаа", "sarantuyaa.gurragchaa@tms.mn", "B.SE", "22b1num0118"),
                new SeedStudent("Амарбаяр", "Нямбуу", "amarbayar.nyambuu@tms.mn", "B.SE", "22b1num0119"),
                new SeedStudent("Нандин-Эрдэнэ", "Туяацэцэг", "nandin-erdene.tuyaatsetseg@tms.mn", "B.SE", "22b1num0120"),
                new SeedStudent("Бүтэнбаяр", "Цэнд-Аюуш", "butenbayar.tsend-ayuush@tms.mn", "B.SE", "22b1num0121"),
                new SeedStudent("Энхмэнд", "Батжаргал", "enkhmend.batjargal@tms.mn", "B.SE", "22b1num0122"),
                new SeedStudent("Гэрэлсайхан", "Лодой", "gerelsaikhan.lodoi@tms.mn", "B.SE", "22b1num0123"),
                new SeedStudent("Мишээлт", "Өсөхбаяр", "misheelt.usukhbayar@tms.mn", "B.SE", "22b1num0124"),
                new SeedStudent("Оюундэлгэр", "Мөнгөнцог", "oyuundelger.munguntsog@tms.mn", "B.SE", "22b1num0125"),
                new SeedStudent("Тэмүүн", "Баатарсүрэн", "temuun.baatarsuren@tms.mn", "B.SE", "22b1num0126")
        );
    }

    private List<SeedTeacher> teacherSeeds() {
        return List.of(
                new SeedTeacher("Энх", "Сүрэн", "enkh.suren@tms.mn", "tch001"),
                new SeedTeacher("Болор", "Наран", "bolor.naran@tms.mn", "tch002"),
                new SeedTeacher("Саруул", "Мөнх", "saruul.munkh@tms.mn", "tch003"),
                new SeedTeacher("Ганболд", "Батбаяр", "ganbold.batbayar@tms.mn", "tch004"),
                new SeedTeacher("Отгонжаргал", "Сүх", "otgonjargal.sukh@tms.mn", "tch005"),
                new SeedTeacher("Мөнх-Эрдэнэ", "Түвшин", "munkh-erdene.tuvshin@tms.mn", "tch006"),
                new SeedTeacher("Нарантуяа", "Болд", "narantuya.bold@tms.mn", "tch007"),
                new SeedTeacher("Батцэцэг", "Эрдэнэбат", "battsetseg.erdenebat@tms.mn", "tch008"),
                new SeedTeacher("Төгсжаргал", "Гантулга", "tugsjargal.gantulga@tms.mn", "tch009"),
                new SeedTeacher("Уянга", "Даваа", "uyanga.davaa@tms.mn", "tch010"),
                new SeedTeacher("Чимгээ", "Батсайхан", "chimgee.batsaikhan@tms.mn", "tch011"),
                new SeedTeacher("Халиун", "Мөнхтөр", "khaliun.munkhtur@tms.mn", "tch012"),
                new SeedTeacher("Баярмаа", "Жаргал", "bayarmaa.jargal@tms.mn", "tch013"),
                new SeedTeacher("Даваадорж", "Энхболд", "davaadorj.enkhbold@tms.mn", "tch014"),
                new SeedTeacher("Отгонбат", "Лхагва", "otgonbat.lkhagva@tms.mn", "tch015"),
                new SeedTeacher("Пүрэв", "Очир", "purev.ochir@tms.mn", "tch016"),
                new SeedTeacher("Солонго", "Баттөр", "solongo.battur@tms.mn", "tch017"),
                new SeedTeacher("Жавзмаа", "Гэрэл", "javzmaa.gerel@tms.mn", "tch018"),
                new SeedTeacher("Эрдэнэсайхан", "Төмөр", "erdenesaikhan.tumur@tms.mn", "tch019"),
                new SeedTeacher("Нямсүрэн", "Алтанхуяг", "nyamsuren.altankhuyag@tms.mn", "tch020")
        );
    }
}
