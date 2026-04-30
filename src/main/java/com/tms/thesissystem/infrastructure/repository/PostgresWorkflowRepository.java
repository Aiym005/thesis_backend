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
import com.tms.thesissystem.persistence.entity.DepartmentEntity;
import com.tms.thesissystem.persistence.entity.PlanEntity;
import com.tms.thesissystem.persistence.entity.PlanResponseEntity;
import com.tms.thesissystem.persistence.entity.PlanWeekEntity;
import com.tms.thesissystem.persistence.entity.StudentEntity;
import com.tms.thesissystem.persistence.entity.TeacherEntity;
import com.tms.thesissystem.persistence.entity.TopicEntity;
import com.tms.thesissystem.persistence.entity.TopicRequestEntity;
import com.tms.thesissystem.persistence.entity.WorkflowAuditEntity;
import com.tms.thesissystem.persistence.entity.WorkflowNotificationEntity;
import com.tms.thesissystem.persistence.entity.WorkflowReviewEntity;
import com.tms.thesissystem.persistence.repository.DepartmentJpaRepository;
import com.tms.thesissystem.persistence.repository.PlanJpaRepository;
import com.tms.thesissystem.persistence.repository.PlanResponseJpaRepository;
import com.tms.thesissystem.persistence.repository.PlanWeekJpaRepository;
import com.tms.thesissystem.persistence.repository.StudentJpaRepository;
import com.tms.thesissystem.persistence.repository.TeacherJpaRepository;
import com.tms.thesissystem.persistence.repository.TopicJpaRepository;
import com.tms.thesissystem.persistence.repository.TopicRequestJpaRepository;
import com.tms.thesissystem.persistence.repository.WorkflowAuditJpaRepository;
import com.tms.thesissystem.persistence.repository.WorkflowNotificationJpaRepository;
import com.tms.thesissystem.persistence.repository.WorkflowReviewJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true", matchIfMissing = true)
public class PostgresWorkflowRepository implements WorkflowRepository {
    private static final long STUDENT_OFFSET = 100_000L;
    private static final long TEACHER_OFFSET = 200_000L;
    private static final long DEPARTMENT_OFFSET = 300_000L;
    private static final String SOFTWARE_ENGINEERING = "Software Engineering";

    private final DepartmentJpaRepository departmentRepository;
    private final StudentJpaRepository studentRepository;
    private final TeacherJpaRepository teacherRepository;
    private final TopicJpaRepository topicRepository;
    private final TopicRequestJpaRepository topicRequestRepository;
    private final PlanJpaRepository planRepository;
    private final PlanWeekJpaRepository planWeekRepository;
    private final PlanResponseJpaRepository planResponseRepository;
    private final WorkflowReviewJpaRepository reviewRepository;
    private final WorkflowNotificationJpaRepository notificationRepository;
    private final WorkflowAuditJpaRepository auditRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicLong topicSequence = new AtomicLong();
    private final AtomicLong planSequence = new AtomicLong();
    private final AtomicLong reviewSequence = new AtomicLong(3100);
    private final AtomicLong notificationSequence = new AtomicLong(4100);
    private final AtomicLong auditSequence = new AtomicLong(5100);

    @PostConstruct
    void initialize() {
        ensureIdentityColumns();
        syncSequences();
        reconcileApprovedTopicsPerStudent();
    }

    private void ensureIdentityColumns() {
        departmentRepository.findAll().forEach(department -> {
            if (department.getAdmin() == null || department.getAdmin().isBlank()) {
                department.setAdmin("sisi-admin");
                departmentRepository.save(department);
            }
        });
    }

    private void syncSequences() {
        topicSequence.set(topicRepository.findTopByOrderByIdDesc().map(TopicEntity::getId).orElse(0L));
        planSequence.set(planRepository.findTopByOrderByIdDesc().map(PlanEntity::getId).orElse(0L));
        reviewSequence.set(reviewRepository.findTopByOrderByIdDesc().map(WorkflowReviewEntity::getId).orElse(3100L));
        notificationSequence.set(notificationRepository.findTopByOrderByIdDesc().map(WorkflowNotificationEntity::getId).orElse(4100L));
        auditSequence.set(auditRepository.findTopByOrderByIdDesc().map(WorkflowAuditEntity::getId).orElse(5100L));
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<User> findAllUsers() {
        Map<Long, DepartmentEntity> departments = departmentRepository.findAll().stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        List<User> users = new ArrayList<>();
        for (StudentEntity student : studentRepository.findAll(Sort.by("id"))) {
            DepartmentEntity department = departments.get(student.getDepId());
            users.add(new User(
                    encodeUserId(UserRole.STUDENT, student.getId()),
                    UserRole.STUDENT,
                    student.getSisiId(),
                    student.getFirstName(),
                    student.getLastName(),
                    student.getMail(),
                    department == null ? null : department.getName(),
                    student.getProgram()
            ));
        }
        for (TeacherEntity teacher : teacherRepository.findAll(Sort.by("id"))) {
            DepartmentEntity department = departments.get(teacher.getDepId());
            users.add(new User(
                    encodeUserId(UserRole.TEACHER, teacher.getId()),
                    UserRole.TEACHER,
                    teacherLoginId(teacher.getId()),
                    teacher.getFirstName(),
                    teacher.getLastName(),
                    teacher.getMail(),
                    department == null ? null : department.getName(),
                    "B.SE"
            ));
        }
        for (DepartmentEntity department : departmentRepository.findAll(Sort.by("id"))) {
            users.add(new User(
                    encodeUserId(UserRole.DEPARTMENT, department.getId()),
                    UserRole.DEPARTMENT,
                    department.getAdmin(),
                    department.getName(),
                    "Department",
                    department.getAdmin() + "@tms.mn",
                    department.getName(),
                    "B.SE"
            ));
        }
        return users;
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<User> findUsersByRole(UserRole role) {
        return findAllUsers().stream().filter(user -> user.role() == role).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized Optional<User> findUserById(Long id) {
        return findAllUsers().stream().filter(user -> user.id().equals(id)).findFirst();
    }

    @Override
    public synchronized User createUserAccount(String username, UserRole role) {
        long departmentId = ensureDefaultDepartment();
        return switch (role) {
            case STUDENT -> {
                StudentEntity student = new StudentEntity();
                student.setDepId(departmentId);
                student.setFirstName(username);
                student.setLastName("User");
                student.setMail(username + "@tms.mn");
                student.setProgram("B.SE");
                student.setSisiId(username);
                student.setChoosed(false);
                student.setProposedNumber(0);
                StudentEntity saved = studentRepository.save(student);
                yield new User(encodeUserId(UserRole.STUDENT, saved.getId()), UserRole.STUDENT, username, username, "User", username + "@tms.mn", SOFTWARE_ENGINEERING, "B.SE");
            }
            case TEACHER -> {
                TeacherEntity teacher = new TeacherEntity();
                teacher.setDepId(departmentId);
                teacher.setFirstName(username);
                teacher.setLastName("Teacher");
                teacher.setMail(username + "@tms.mn");
                teacher.setNumberOfChoosedStudents(0);
                TeacherEntity saved = teacherRepository.save(teacher);
                yield new User(encodeUserId(UserRole.TEACHER, saved.getId()), UserRole.TEACHER, teacherLoginId(saved.getId()), username, "Teacher", username + "@tms.mn", SOFTWARE_ENGINEERING, "B.SE");
            }
            case DEPARTMENT -> {
                DepartmentEntity department = new DepartmentEntity();
                department.setName(username);
                department.setPrograms("[\"B.SE\"]");
                department.setAdmin(username);
                DepartmentEntity saved = departmentRepository.save(department);
                yield new User(encodeUserId(UserRole.DEPARTMENT, saved.getId()), UserRole.DEPARTMENT, username, username, "Department", username + "@tms.mn", username, "B.SE");
            }
        };
    }

    @Override
    public Long nextTopicId() {
        return topicSequence.incrementAndGet();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<Topic> findAllTopics() {
        Map<Long, User> userIndex = indexUsers(findAllUsers());
        return topicRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(entity -> mapTopic(entity, userIndex))
                .sorted(Comparator.comparing(Topic::updatedAt).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized Optional<Topic> findTopicById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Map<Long, User> userIndex = indexUsers(findAllUsers());
        return topicRepository.findById(id).map(entity -> mapTopic(entity, userIndex));
    }

    @Override
    public synchronized Topic saveTopic(Topic topic) {
        TopicEntity entity = topicRepository.findById(topic.id()).orElseGet(TopicEntity::new);
        entity.setId(topic.id());
        entity.setCreatedAt(topic.createdAt().toLocalDate());
        entity.setCreatedById(decodeUserId(topic.proposerRole(), topic.proposerId()));
        entity.setCreatedByType(topic.proposerRole().name());
        entity.setFields(json(payload(
                topic.title(),
                topic.description(),
                decodeNullableUserId(UserRole.STUDENT, topic.ownerStudentId()),
                topic.ownerStudentName(),
                decodeNullableUserId(UserRole.TEACHER, topic.advisorTeacherId()),
                topic.advisorTeacherName(),
                topic.approvals()
        )));
        entity.setFormId(null);
        entity.setProgram(topic.program());
        entity.setStatus(topic.status().name());
        topicRepository.save(entity);

        if (topic.ownerStudentId() != null) {
            upsertTopicRequest(topic.id(), decodeUserId(UserRole.STUDENT, topic.ownerStudentId()), UserRole.STUDENT, topic.status() != TopicStatus.REJECTED, "Topic workflow request");
        }
        return findTopicById(topic.id()).orElse(topic);
    }

    @Override
    public Long nextPlanId() {
        return planSequence.incrementAndGet();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<Plan> findAllPlans() {
        Map<Long, User> userIndex = indexUsers(findAllUsers());
        return planRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(entity -> mapPlan(entity, userIndex))
                .sorted(Comparator.comparing(Plan::updatedAt).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized Optional<Plan> findPlanById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Map<Long, User> userIndex = indexUsers(findAllUsers());
        return planRepository.findById(id).map(entity -> mapPlan(entity, userIndex));
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized Optional<Plan> findPlanByStudentId(Long studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        long rawStudentId = decodeUserId(UserRole.STUDENT, studentId);
        Map<Long, User> userIndex = indexUsers(findAllUsers());
        return planRepository.findFirstByStudentIdOrderByIdDesc(rawStudentId)
                .map(entity -> mapPlan(entity, userIndex));
    }

    @Override
    public synchronized Plan savePlan(Plan plan) {
        PlanEntity entity = planRepository.findById(plan.id()).orElseGet(PlanEntity::new);
        entity.setId(plan.id());
        entity.setTopicId(plan.topicId());
        entity.setStudentId(decodeUserId(UserRole.STUDENT, plan.studentId()));
        entity.setStatus(plan.status().name());
        entity.setCreatedAt(plan.createdAt().toLocalDate());
        planRepository.save(entity);

        planWeekRepository.deleteByPlanId(plan.id());
        for (WeeklyTask task : plan.tasks()) {
            PlanWeekEntity weekEntity = new PlanWeekEntity();
            weekEntity.setPlanId(plan.id());
            weekEntity.setWeekNumber(task.week());
            weekEntity.setTask(task.title());
            weekEntity.setResult(json(Map.of(
                    "deliverable", task.deliverable(),
                    "focus", task.focus()
            )));
            planWeekRepository.save(weekEntity);
        }
        return findPlanById(plan.id()).orElse(plan);
    }

    @Override
    public Long nextReviewId() {
        return reviewSequence.incrementAndGet();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<Review> findAllReviews() {
        return reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))).stream()
                .map(entity -> new Review(
                        entity.getId(),
                        entity.getPlanId(),
                        entity.getWeekNumber(),
                        entity.getReviewerId(),
                        entity.getReviewerName(),
                        entity.getScore(),
                        entity.getComment(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public synchronized Review saveReview(Review review) {
        WorkflowReviewEntity entity = new WorkflowReviewEntity();
        entity.setId(review.id());
        entity.setPlanId(review.planId());
        entity.setWeekNumber(review.week());
        entity.setReviewerId(review.reviewerId());
        entity.setReviewerName(review.reviewerName());
        entity.setScore(review.score());
        entity.setComment(review.comment());
        entity.setCreatedAt(review.createdAt());
        reviewRepository.save(entity);
        return review;
    }

    @Override
    public Long nextNotificationId() {
        return notificationSequence.incrementAndGet();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<Notification> findAllNotifications() {
        return notificationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))).stream()
                .map(entity -> new Notification(entity.getId(), entity.getUserId(), entity.getTitle(), entity.getMessage(), entity.getCreatedAt()))
                .toList();
    }

    @Override
    public synchronized Notification saveNotification(Notification notification) {
        WorkflowNotificationEntity entity = new WorkflowNotificationEntity();
        entity.setId(notification.id());
        entity.setUserId(notification.userId());
        entity.setTitle(notification.title());
        entity.setMessage(notification.message());
        entity.setCreatedAt(notification.createdAt());
        notificationRepository.save(entity);
        return notification;
    }

    @Override
    public Long nextAuditId() {
        return auditSequence.incrementAndGet();
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<AuditEntry> findAllAuditEntries() {
        return auditRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))).stream()
                .map(entity -> new AuditEntry(
                        entity.getId(),
                        entity.getEntityType(),
                        entity.getEntityId(),
                        entity.getAction(),
                        entity.getActorName(),
                        entity.getDetail(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public synchronized AuditEntry saveAuditEntry(AuditEntry auditEntry) {
        WorkflowAuditEntity entity = new WorkflowAuditEntity();
        entity.setId(auditEntry.id());
        entity.setEntityType(auditEntry.entityType());
        entity.setEntityId(auditEntry.entityId());
        entity.setAction(auditEntry.action());
        entity.setActorName(auditEntry.actorName());
        entity.setDetail(auditEntry.detail());
        entity.setCreatedAt(auditEntry.createdAt());
        auditRepository.save(entity);
        syncPlanResponse(auditEntry);
        return auditEntry;
    }

    private void syncPlanResponse(AuditEntry auditEntry) {
        if (!"PLAN".equals(auditEntry.entityType())) {
            return;
        }
        if (!auditEntry.action().contains("APPROVED") && !auditEntry.action().contains("REJECTED")) {
            return;
        }
        ApprovalStage stage = auditEntry.action().contains("DEPARTMENT") || "PLAN_APPROVED".equals(auditEntry.action())
                ? ApprovalStage.DEPARTMENT
                : ApprovalStage.TEACHER;
        String result = auditEntry.action().contains("REJECTED") ? "REJECTED" : "APPROVED";
        User actor = findAllUsers().stream().filter(user -> user.fullName().equals(auditEntry.actorName())).findFirst().orElse(null);
        if (actor == null) {
            return;
        }
        upsertPlanResponse(auditEntry.entityId(), decodeUserId(actor.role(), actor.id()), stage.name(), result, auditEntry.detail());
    }

    private Topic mapTopic(TopicEntity entity, Map<Long, User> userIndex) {
        Map<String, Object> fields = parseJsonMap(entity.getFields());
        Long ownerStudentId = longValue(fields.get("ownerStudentId"));
        String ownerStudentName = stringValue(fields.get("ownerStudentName"));
        TopicStatus status = TopicStatus.valueOf(entity.getStatus());
        if (ownerStudentId == null
                && (status == TopicStatus.PENDING_TEACHER_APPROVAL || status == TopicStatus.PENDING_DEPARTMENT_APPROVAL)) {
            ownerStudentId = topicRequestRepository.findFirstByTopicIdOrderByIdDesc(entity.getId())
                    .map(TopicRequestEntity::getRequestedById)
                    .orElse(null);
            ownerStudentName = ownerStudentId == null
                    ? null
                    : Optional.ofNullable(userIndex.get(encodeUserId(UserRole.STUDENT, ownerStudentId)))
                            .map(User::fullName)
                            .orElse(null);
        }
        Long advisorTeacherId = longValue(fields.get("advisorTeacherId"));
        UserRole proposerRole = UserRole.valueOf(entity.getCreatedByType());
        long encodedProposerId = encodeUserId(proposerRole, entity.getCreatedById());
        String proposerName = Optional.ofNullable(userIndex.get(encodedProposerId))
                .map(User::fullName)
                .orElse("Unknown");
        return new Topic(
                entity.getId(),
                stringValue(fields.get("title")),
                stringValue(fields.get("description")),
                entity.getProgram(),
                encodedProposerId,
                proposerName,
                proposerRole,
                ownerStudentId == null ? null : encodeUserId(UserRole.STUDENT, ownerStudentId),
                ownerStudentName,
                advisorTeacherId == null ? null : encodeUserId(UserRole.TEACHER, advisorTeacherId),
                stringValue(fields.get("advisorTeacherName")),
                status,
                localDateTime(entity.getCreatedAt()),
                localDateTime(entity.getCreatedAt()),
                mapApprovals(fields.get("approvals"))
        );
    }

    private Plan mapPlan(PlanEntity entity, Map<Long, User> userIndex) {
        Map<String, Object> topicFields = topicRepository.findById(entity.getTopicId())
                .map(TopicEntity::getFields)
                .map(this::parseJsonMap)
                .orElseGet(LinkedHashMap::new);
        List<WeeklyTask> tasks = planWeekRepository.findByPlanIdOrderByWeekNumberAsc(entity.getId()).stream()
                .map(week -> {
                    Map<String, Object> result = parseJsonMap(week.getResult());
                    return new WeeklyTask(week.getWeekNumber(), week.getTask(),
                            stringValue(result.get("deliverable")), stringValue(result.get("focus")));
                })
                .toList();
        List<ApprovalRecord> approvals = planResponseRepository.findByPlanIdOrderByIdAsc(entity.getId()).stream()
                .map(response -> {
                    ApprovalStage stage = ApprovalStage.valueOf(response.getApproverType());
                    UserRole role = stage == ApprovalStage.TEACHER ? UserRole.TEACHER : UserRole.DEPARTMENT;
                    long encodedActorId = encodeUserId(role, response.getApproverId());
                    String actorName = Optional.ofNullable(userIndex.get(encodedActorId))
                            .map(User::fullName)
                            .orElse("Unknown");
                    return new ApprovalRecord(
                            stage,
                            encodedActorId,
                            actorName,
                            "APPROVED".equalsIgnoreCase(response.getResponse()),
                            response.getNote(),
                            localDateTime(response.getResponseDate())
                    );
                })
                .toList();
        long encodedStudentId = encodeUserId(UserRole.STUDENT, entity.getStudentId());
        String studentName = Optional.ofNullable(userIndex.get(encodedStudentId))
                .map(User::fullName)
                .orElse("Unknown");
        return new Plan(
                entity.getId(),
                entity.getTopicId(),
                stringValue(topicFields.get("title")),
                encodedStudentId,
                studentName,
                PlanStatus.valueOf(entity.getStatus()),
                tasks,
                approvals,
                localDateTime(entity.getCreatedAt()),
                localDateTime(entity.getCreatedAt())
        );
    }

    private Map<Long, User> indexUsers(List<User> users) {
        Map<Long, User> index = new LinkedHashMap<>();
        for (User user : users) {
            index.put(user.id(), user);
        }
        return index;
    }

    private void upsertTopicRequest(Long topicId, Long requestedById, UserRole requestedByType, boolean selected, String note) {
        TopicRequestEntity entity = topicRequestRepository.findFirstByTopicIdOrderByIdDesc(topicId).orElseGet(TopicRequestEntity::new);
        entity.setTopicId(topicId);
        entity.setSelected(selected);
        entity.setRequestNote(note);
        entity.setRequestText(note);
        entity.setRequestedById(requestedById);
        entity.setRequestedByType(requestedByType.name());
        entity.setSelectedAt(LocalDate.now());
        topicRequestRepository.save(entity);
    }

    private void upsertPlanResponse(Long planId, Long approverId, String approverType, String result, String note) {
        PlanResponseEntity entity = planResponseRepository.findFirstByPlanIdAndApproverType(planId, approverType).orElseGet(PlanResponseEntity::new);
        entity.setPlanId(planId);
        entity.setApproverId(approverId);
        entity.setApproverType(approverType);
        entity.setResponse(result);
        entity.setNote(note);
        entity.setResponseDate(LocalDate.now());
        planResponseRepository.save(entity);
    }

    private long ensureDefaultDepartment() {
        return departmentRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    DepartmentEntity entity = new DepartmentEntity();
                    entity.setName(SOFTWARE_ENGINEERING);
                    entity.setPrograms("[\"B.SE\"]");
                    entity.setAdmin("sisi-admin");
                    return departmentRepository.save(entity);
                })
                .getId();
    }

    private void reconcileApprovedTopicsPerStudent() {
        Map<Long, List<Topic>> approvedTopicsByStudent = new LinkedHashMap<>();
        for (Topic topic : findAllTopics()) {
            if (topic.status() != TopicStatus.APPROVED || topic.ownerStudentId() == null) {
                continue;
            }
            approvedTopicsByStudent.computeIfAbsent(topic.ownerStudentId(), ignored -> new ArrayList<>()).add(topic);
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

    private List<ApprovalRecord> mapApprovals(Object approvalsObject) {
        if (!(approvalsObject instanceof List<?> items)) {
            return List.of();
        }
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize payload", exception);
        }
    }

    private Map<String, Object> parseJsonMap(String value) {
        try {
            if (value == null || value.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse payload", exception);
        }
    }

    private LocalDateTime localDateTime(LocalDate date) {
        return date == null ? LocalDateTime.now() : date.atStartOfDay();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String string = String.valueOf(value);
        return string.isBlank() || "null".equalsIgnoreCase(string) ? null : Long.parseLong(string);
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
