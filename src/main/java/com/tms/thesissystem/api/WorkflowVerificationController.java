package com.tms.thesissystem.api;

import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.domain.model.Plan;
import com.tms.thesissystem.domain.model.PlanStatus;
import com.tms.thesissystem.domain.model.Topic;
import com.tms.thesissystem.domain.model.TopicStatus;
import com.tms.thesissystem.domain.model.User;
import com.tms.thesissystem.domain.model.WeeklyTask;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/verification")
public class WorkflowVerificationController {
    private final WorkflowQueryService queryService;
    private final WorkflowCommandService commandService;

    public WorkflowVerificationController(WorkflowQueryService queryService, WorkflowCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/state")
    public WorkflowStateResponse state() {
        return WorkflowStateResponse.from(queryService.getDashboard());
    }

    @GetMapping("/users")
    public List<User> users() {
        return queryService.getDashboard().users();
    }

    @GetMapping("/topics")
    public TopicStateResponse topics() {
        WorkflowQueryService.DashboardSnapshot snapshot = queryService.getDashboard();
        return new TopicStateResponse(
                snapshot.topics(),
                filterTopics(snapshot.topics(), TopicStatus.AVAILABLE),
                filterTopics(snapshot.topics(), TopicStatus.PENDING_TEACHER_APPROVAL),
                filterTopics(snapshot.topics(), TopicStatus.PENDING_DEPARTMENT_APPROVAL),
                filterTopics(snapshot.topics(), TopicStatus.APPROVED),
                filterTopics(snapshot.topics(), TopicStatus.REJECTED)
        );
    }

    @GetMapping("/plans")
    public PlanStateResponse plans() {
        WorkflowQueryService.DashboardSnapshot snapshot = queryService.getDashboard();
        return new PlanStateResponse(
                snapshot.plans(),
                filterPlans(snapshot.plans(), PlanStatus.DRAFT),
                filterPlans(snapshot.plans(), PlanStatus.PENDING_TEACHER_APPROVAL),
                filterPlans(snapshot.plans(), PlanStatus.PENDING_DEPARTMENT_APPROVAL),
                filterPlans(snapshot.plans(), PlanStatus.APPROVED),
                filterPlans(snapshot.plans(), PlanStatus.REJECTED)
        );
    }

    @PostMapping("/topics/student-proposals")
    public TopicActionResponse studentProposesTopic(@RequestBody StudentTopicProposalRequest request) {
        Topic topic = commandService.proposeTopic(request.studentId(), request.title(), request.description(), request.program());
        return new TopicActionResponse(topic, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/topics/teacher-proposals")
    public TopicActionResponse teacherProposesTopic(@RequestBody TeacherTopicProposalRequest request) {
        Topic topic = commandService.createTeacherTopic(request.teacherId(), request.title(), request.description(), request.program());
        return new TopicActionResponse(topic, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/topics/selections")
    public TopicActionResponse studentSelectsApprovedTopic(@RequestBody TopicSelectionRequest request) {
        Topic topic = commandService.claimTopic(request.topicId(), request.studentId());
        return new TopicActionResponse(topic, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/topics/teacher-approvals")
    public TopicActionResponse teacherApprovesStudentTopic(@RequestBody TopicTeacherApprovalRequest request) {
        Long resolvedTopicId = resolvePendingTeacherTopicId(request);
        Topic topic = commandService.teacherDecisionOnTopic(resolvedTopicId, request.resolvedTeacherId(), request.approved(), request.note());
        return new TopicActionResponse(topic, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/topics/department-approvals")
    public TopicActionResponse departmentApprovesTopic(@RequestBody TopicDepartmentApprovalRequest request) {
        Topic topic = commandService.departmentDecisionOnTopic(
                request.topicId(),
                request.departmentId(),
                request.approved(),
                request.advisorTeacherId(),
                request.note()
        );
        return new TopicActionResponse(topic, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/plans")
    public PlanActionResponse studentCreatesPlan(@RequestBody StudentPlanRequest request) {
        List<WeeklyTask> tasks = request.tasks().stream()
                .map(task -> new WeeklyTask(task.week(), task.title(), task.deliverable(), task.focus()))
                .toList();
        Plan plan = commandService.savePlan(request.studentId(), request.topicId(), tasks);
        return new PlanActionResponse(plan, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/plans/submit")
    public PlanActionResponse studentSubmitsPlan(@RequestBody PlanSubmitRequest request) {
        Plan plan = commandService.submitPlan(request.planId(), request.studentId());
        return new PlanActionResponse(plan, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/plans/teacher-approvals")
    public PlanActionResponse teacherApprovesPlan(@RequestBody PlanApprovalRequest request) {
        Plan plan = commandService.teacherDecisionOnPlan(request.planId(), request.actorId(), request.approved(), request.note());
        return new PlanActionResponse(plan, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @PostMapping("/plans/department-approvals")
    public PlanActionResponse departmentApprovesPlan(@RequestBody PlanApprovalRequest request) {
        Plan plan = commandService.departmentDecisionOnPlan(request.planId(), request.actorId(), request.approved(), request.note());
        return new PlanActionResponse(plan, WorkflowStateResponse.from(queryService.getDashboard()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public void handleDomainError(RuntimeException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    private List<Topic> filterTopics(List<Topic> topics, TopicStatus status) {
        return topics.stream().filter(topic -> topic.status() == status).toList();
    }

    private List<Plan> filterPlans(List<Plan> plans, PlanStatus status) {
        return plans.stream().filter(plan -> plan.status() == status).toList();
    }

    private Long resolvePendingTeacherTopicId(TopicTeacherApprovalRequest request) {
        if (request.resolvedTopicId() != null) {
            return request.resolvedTopicId();
        }
        if (request.topicTitle() == null || request.topicTitle().isBlank()) {
            throw new IllegalArgumentException("Сэдэвийн id эсвэл нэр дамжуулаагүй байна.");
        }
        Optional<Topic> matchedTopic = queryService.getDashboard().topics().stream()
                .filter(topic -> topic.status() == TopicStatus.PENDING_TEACHER_APPROVAL)
                .filter(topic -> request.topicTitle().equalsIgnoreCase(topic.title()))
                .findFirst();
        return matchedTopic.map(Topic::id)
                .orElseThrow(() -> new IllegalArgumentException("Сэдэв олдсонгүй. Нэр: " + request.topicTitle()));
    }

    public record StudentTopicProposalRequest(Long studentId, String title, String description, String program) { }
    public record TeacherTopicProposalRequest(Long teacherId, String title, String description, String program) { }
    public record TopicSelectionRequest(Long topicId, Long studentId) { }
    public record TopicTeacherApprovalRequest(Long topicId, Long entityId, Long teacherId, Long actorId, String topicTitle, boolean approved, String note) {
        public Long resolvedTopicId() {
            return topicId != null ? topicId : entityId;
        }

        public Long resolvedTeacherId() {
            return teacherId != null ? teacherId : actorId;
        }
    }
    public record TopicDepartmentApprovalRequest(Long topicId, Long departmentId, boolean approved, Long advisorTeacherId, String note) { }
    public record StudentPlanRequest(Long studentId, Long topicId, List<WeeklyTaskRequest> tasks) { }
    public record WeeklyTaskRequest(int week, String title, String deliverable, String focus) { }
    public record PlanSubmitRequest(Long planId, Long studentId) { }
    public record PlanApprovalRequest(Long planId, Long actorId, boolean approved, String note) { }

    public record TopicActionResponse(Topic topic, WorkflowStateResponse state) { }
    public record PlanActionResponse(Plan plan, WorkflowStateResponse state) { }

    public record TopicStateResponse(
            List<Topic> allTopics,
            List<Topic> availableTopics,
            List<Topic> pendingTeacherApprovalTopics,
            List<Topic> pendingDepartmentApprovalTopics,
            List<Topic> approvedStudentTopics,
            List<Topic> rejectedTopics
    ) { }

    public record PlanStateResponse(
            List<Plan> allPlans,
            List<Plan> draftPlans,
            List<Plan> pendingTeacherApprovalPlans,
            List<Plan> pendingDepartmentApprovalPlans,
            List<Plan> approvedPlans,
            List<Plan> rejectedPlans
    ) { }

    public record WorkflowStateResponse(
            List<User> users,
            TopicStateResponse topics,
            PlanStateResponse plans
    ) {
        public static WorkflowStateResponse from(WorkflowQueryService.DashboardSnapshot snapshot) {
            List<Topic> topics = snapshot.topics();
            List<Plan> plans = snapshot.plans();
            return new WorkflowStateResponse(
                    snapshot.users(),
                    new TopicStateResponse(
                            topics,
                            topics.stream().filter(topic -> topic.status() == TopicStatus.AVAILABLE).toList(),
                            topics.stream().filter(topic -> topic.status() == TopicStatus.PENDING_TEACHER_APPROVAL).toList(),
                            topics.stream().filter(topic -> topic.status() == TopicStatus.PENDING_DEPARTMENT_APPROVAL).toList(),
                            topics.stream().filter(topic -> topic.status() == TopicStatus.APPROVED).toList(),
                            topics.stream().filter(topic -> topic.status() == TopicStatus.REJECTED).toList()
                    ),
                    new PlanStateResponse(
                            plans,
                            plans.stream().filter(plan -> plan.status() == PlanStatus.DRAFT).toList(),
                            plans.stream().filter(plan -> plan.status() == PlanStatus.PENDING_TEACHER_APPROVAL).toList(),
                            plans.stream().filter(plan -> plan.status() == PlanStatus.PENDING_DEPARTMENT_APPROVAL).toList(),
                            plans.stream().filter(plan -> plan.status() == PlanStatus.APPROVED).toList(),
                            plans.stream().filter(plan -> plan.status() == PlanStatus.REJECTED).toList()
                    )
            );
        }
    }
}
