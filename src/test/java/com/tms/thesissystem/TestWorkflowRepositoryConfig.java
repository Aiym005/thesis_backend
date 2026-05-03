package com.tms.thesissystem;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@TestConfiguration
public class TestWorkflowRepositoryConfig {
    @Bean
    @Primary
    WorkflowRepository workflowRepository() {
        return new TestWorkflowRepository();
    }

    static class TestWorkflowRepository implements WorkflowRepository {
        private static final String DEPARTMENT_NAME = "Software Engineering";
        private static final String PROGRAM = "B.SE";

        private final Map<Long, User> users = new LinkedHashMap<>();
        private final Map<Long, Topic> topics = new LinkedHashMap<>();
        private final Map<Long, Plan> plans = new LinkedHashMap<>();
        private final Map<Long, Review> reviews = new LinkedHashMap<>();
        private final Map<Long, Notification> notifications = new LinkedHashMap<>();
        private final Map<Long, AuditEntry> audits = new LinkedHashMap<>();

        private final AtomicLong studentSequence = new AtomicLong(100010L);
        private final AtomicLong teacherSequence = new AtomicLong(200010L);
        private final AtomicLong departmentSequence = new AtomicLong(300010L);
        private final AtomicLong topicSequence = new AtomicLong(10L);
        private final AtomicLong planSequence = new AtomicLong(20L);
        private final AtomicLong reviewSequence = new AtomicLong(30L);
        private final AtomicLong notificationSequence = new AtomicLong(40L);
        private final AtomicLong auditSequence = new AtomicLong(50L);

        TestWorkflowRepository() {
            initializeFixture();
        }

        private void initializeFixture() {
            LocalDateTime now = LocalDateTime.now();

            User student = new User(100001L, UserRole.STUDENT, "22b1num0027", "Ану", "Бат-Эрдэнэ", "anu.bat-erdene@tms.mn", DEPARTMENT_NAME, PROGRAM);
            User teacherA = new User(200001L, UserRole.TEACHER, "tch001", "Энх", "Сүрэн", "enkh.suren@tms.mn", DEPARTMENT_NAME, PROGRAM);
            User teacherB = new User(200002L, UserRole.TEACHER, "tch002", "Болор", "Наран", "bolor.naran@tms.mn", DEPARTMENT_NAME, PROGRAM);
            User department = new User(300001L, UserRole.DEPARTMENT, "se-dept", "Програм", "админ", "sisi.admin@tms.mn", DEPARTMENT_NAME, PROGRAM);

            users.put(student.id(), student);
            users.put(teacherA.id(), teacherA);
            users.put(teacherB.id(), teacherB);
            users.put(department.id(), department);

            Topic approvedCatalogTopicA = new Topic(
                    1L,
                    "AI-based Thesis Workflow Automation",
                    "Catalog topic for workflow automation.",
                    PROGRAM,
                    teacherA.id(),
                    teacherA.fullName(),
                    UserRole.TEACHER,
                    null,
                    null,
                    teacherA.id(),
                    teacherA.fullName(),
                    TopicStatus.APPROVED,
                    now.minusDays(6),
                    now.minusDays(5),
                    List.of(new ApprovalRecord(ApprovalStage.DEPARTMENT, department.id(), department.fullName(), true, "Catalog approved", now.minusDays(5)))
            );

            Topic approvedCatalogTopicB = new Topic(
                    2L,
                    "Event-driven Student Research Tracker",
                    "Catalog topic for event-driven milestone tracking.",
                    PROGRAM,
                    teacherB.id(),
                    teacherB.fullName(),
                    UserRole.TEACHER,
                    null,
                    null,
                    teacherB.id(),
                    teacherB.fullName(),
                    TopicStatus.APPROVED,
                    now.minusDays(4),
                    now.minusDays(3),
                    List.of(new ApprovalRecord(ApprovalStage.DEPARTMENT, department.id(), department.fullName(), true, "Catalog approved", now.minusDays(3)))
            );

            Topic approvedStudentTopic = new Topic(
                    3L,
                    "Layered Architecture for Graduation Management",
                    "Student topic with completed approvals.",
                    PROGRAM,
                    student.id(),
                    student.fullName(),
                    UserRole.STUDENT,
                    student.id(),
                    student.fullName(),
                    teacherA.id(),
                    teacherA.fullName(),
                    TopicStatus.APPROVED,
                    now.minusDays(10),
                    now.minusDays(8),
                    List.of(
                            new ApprovalRecord(ApprovalStage.TEACHER, teacherA.id(), teacherA.fullName(), true, "Teacher approved", now.minusDays(9)),
                            new ApprovalRecord(ApprovalStage.DEPARTMENT, department.id(), department.fullName(), true, "Department approved", now.minusDays(8))
                    )
            );

            topics.put(approvedCatalogTopicA.getId(), approvedCatalogTopicA);
            topics.put(approvedCatalogTopicB.getId(), approvedCatalogTopicB);
            topics.put(approvedStudentTopic.getId(), approvedStudentTopic);

            List<WeeklyTask> fixtureTasks = java.util.stream.IntStream.rangeClosed(1, 15)
                    .mapToObj(week -> new WeeklyTask(week, "Week " + week, "Deliverable " + week, "Focus " + week))
                    .toList();

            Plan approvedPlan = new Plan(
                    1L,
                    approvedStudentTopic.getId(),
                    approvedStudentTopic.getTitle(),
                    student.id(),
                    student.fullName(),
                    PlanStatus.APPROVED,
                    fixtureTasks,
                    List.of(
                            new ApprovalRecord(ApprovalStage.TEACHER, teacherA.id(), teacherA.fullName(), true, "Teacher approved", now.minusDays(7)),
                            new ApprovalRecord(ApprovalStage.DEPARTMENT, department.id(), department.fullName(), true, "Department approved", now.minusDays(6))
                    ),
                    now.minusDays(8),
                    now.minusDays(6)
            );

            plans.put(approvedPlan.getId(), approvedPlan);
            reviews.put(1L, new Review(1L, approvedPlan.getId(), 4, teacherA.id(), teacherA.fullName(), 92, "Судалгааны хэсэг сайн.", now.minusDays(1)));
            notifications.put(1L, new Notification(1L, student.id(), "Төлөвлөгөө батлагдсан", "15 долоо хоногийн төлөвлөгөөг тэнхим баталгаажууллаа.", now.minusHours(6)));
            audits.put(1L, new AuditEntry(1L, "PLAN", approvedPlan.getId(), "PLAN_APPROVED", department.fullName(), "Тэнхим төлөвлөгөөг эцэслэн баталлаа.", now.minusHours(6)));
        }

        @Override
        public List<User> findAllUsers() {
            return users.values().stream().sorted(Comparator.comparing(User::id)).toList();
        }

        @Override
        public List<User> findUsersByRole(UserRole role) {
            return users.values().stream()
                    .filter(user -> user.role() == role)
                    .sorted(Comparator.comparing(User::id))
                    .toList();
        }

        @Override
        public Optional<User> findUserById(Long id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public User createUserAccount(String username, UserRole role, String firstName, String lastName, String phoneNumber) {
            String resolvedFirstName = (firstName == null || firstName.isBlank()) ? username : firstName;
            String resolvedLastName = (lastName == null || lastName.isBlank()) ? "User" : lastName;
            String resolvedPhoneNumber = (phoneNumber == null || phoneNumber.isBlank()) ? username : phoneNumber;
            User createdUser = switch (role) {
                case STUDENT -> new User(studentSequence.getAndIncrement(), role, username, resolvedFirstName, resolvedLastName, username + "@tms.mn", DEPARTMENT_NAME, PROGRAM);
                case TEACHER -> new User(teacherSequence.getAndIncrement(), role, username, resolvedFirstName, resolvedLastName, resolvedPhoneNumber, DEPARTMENT_NAME, PROGRAM);
                case DEPARTMENT -> new User(departmentSequence.getAndIncrement(), role, username, resolvedFirstName, resolvedLastName, username + "@tms.mn", DEPARTMENT_NAME, PROGRAM);
            };
            users.put(createdUser.id(), createdUser);
            return createdUser;
        }

        @Override
        public Long nextTopicId() {
            return topicSequence.getAndIncrement();
        }

        @Override
        public List<Topic> findAllTopics() {
            return topics.values().stream().sorted(Comparator.comparing(Topic::getUpdatedAt).reversed()).toList();
        }

        @Override
        public Optional<Topic> findTopicById(Long id) {
            return Optional.ofNullable(topics.get(id));
        }

        @Override
        public Topic saveTopic(Topic topic) {
            topics.put(topic.getId(), topic);
            return topic;
        }

        @Override
        public Long nextPlanId() {
            return planSequence.getAndIncrement();
        }

        @Override
        public List<Plan> findAllPlans() {
            return plans.values().stream().sorted(Comparator.comparing(Plan::getUpdatedAt).reversed()).toList();
        }

        @Override
        public Optional<Plan> findPlanById(Long id) {
            return Optional.ofNullable(plans.get(id));
        }

        @Override
        public Optional<Plan> findPlanByStudentId(Long studentId) {
            return plans.values().stream().filter(plan -> plan.getStudentId().equals(studentId)).findFirst();
        }

        @Override
        public Plan savePlan(Plan plan) {
            plans.put(plan.getId(), plan);
            return plan;
        }

        @Override
        public Long nextReviewId() {
            return reviewSequence.getAndIncrement();
        }

        @Override
        public List<Review> findAllReviews() {
            return new ArrayList<>(reviews.values());
        }

        @Override
        public Review saveReview(Review review) {
            reviews.put(review.id(), review);
            return review;
        }

        @Override
        public Long nextNotificationId() {
            return notificationSequence.getAndIncrement();
        }

        @Override
        public List<Notification> findAllNotifications() {
            return new ArrayList<>(notifications.values());
        }

        @Override
        public Notification saveNotification(Notification notification) {
            notifications.put(notification.id(), notification);
            return notification;
        }

        @Override
        public Long nextAuditId() {
            return auditSequence.getAndIncrement();
        }

        @Override
        public List<AuditEntry> findAllAuditEntries() {
            return new ArrayList<>(audits.values());
        }

        @Override
        public AuditEntry saveAuditEntry(AuditEntry auditEntry) {
            audits.put(auditEntry.id(), auditEntry);
            return auditEntry;
        }
    }
}
