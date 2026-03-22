package com.tms.thesissystem.api;

import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
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

@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowQueryService queryService;
    private final WorkflowCommandService commandService;

    public WorkflowController(WorkflowQueryService queryService, WorkflowCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/dashboard")
    public WorkflowQueryService.DashboardSnapshot dashboard() { return queryService.getDashboard(); }

    @PostMapping("/topics/proposals")
    public WorkflowQueryService.DashboardSnapshot proposeTopic(@RequestBody TopicProposalRequest request) {
        commandService.proposeTopic(request.studentId(), request.title(), request.description(), request.program());
        return queryService.getDashboard();
    }

    @PostMapping("/topics/catalog")
    public WorkflowQueryService.DashboardSnapshot createTeacherTopic(@RequestBody TeacherTopicRequest request) {
        commandService.createTeacherTopic(request.teacherId(), request.title(), request.description(), request.program());
        return queryService.getDashboard();
    }

    @PostMapping("/topics/claim")
    public WorkflowQueryService.DashboardSnapshot claimTopic(@RequestBody TopicClaimRequest request) {
        commandService.claimTopic(request.topicId(), request.studentId());
        return queryService.getDashboard();
    }

    @PostMapping("/topics/teacher-decision")
    public WorkflowQueryService.DashboardSnapshot teacherTopicDecision(@RequestBody DecisionRequest request) {
        commandService.teacherDecisionOnTopic(request.entityId(), request.actorId(), request.approved(), request.note());
        return queryService.getDashboard();
    }

    @PostMapping("/topics/department-decision")
    public WorkflowQueryService.DashboardSnapshot departmentTopicDecision(@RequestBody DepartmentTopicDecisionRequest request) {
        commandService.departmentDecisionOnTopic(request.topicId(), request.departmentId(), request.approved(), request.advisorTeacherId(), request.note());
        return queryService.getDashboard();
    }

    @PostMapping("/plans")
    public WorkflowQueryService.DashboardSnapshot savePlan(@RequestBody PlanSaveRequest request) {
        List<WeeklyTask> tasks = request.tasks().stream().map(task -> new WeeklyTask(task.week(), task.title(), task.deliverable(), task.focus())).toList();
        commandService.savePlan(request.studentId(), request.topicId(), tasks);
        return queryService.getDashboard();
    }

    @PostMapping("/plans/submit")
    public WorkflowQueryService.DashboardSnapshot submitPlan(@RequestBody PlanSubmitRequest request) {
        commandService.submitPlan(request.planId(), request.studentId());
        return queryService.getDashboard();
    }

    @PostMapping("/plans/teacher-decision")
    public WorkflowQueryService.DashboardSnapshot teacherPlanDecision(@RequestBody DecisionRequest request) {
        commandService.teacherDecisionOnPlan(request.entityId(), request.actorId(), request.approved(), request.note());
        return queryService.getDashboard();
    }

    @PostMapping("/plans/department-decision")
    public WorkflowQueryService.DashboardSnapshot departmentPlanDecision(@RequestBody DecisionRequest request) {
        commandService.departmentDecisionOnPlan(request.entityId(), request.actorId(), request.approved(), request.note());
        return queryService.getDashboard();
    }

    @PostMapping("/reviews")
    public WorkflowQueryService.DashboardSnapshot review(@RequestBody ReviewRequest request) {
        commandService.submitReview(request.planId(), request.reviewerId(), request.week(), request.score(), request.comment());
        return queryService.getDashboard();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public void handleDomainError(RuntimeException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    public record TopicProposalRequest(Long studentId, String title, String description, String program) { }
    public record TeacherTopicRequest(Long teacherId, String title, String description, String program) { }
    public record TopicClaimRequest(Long topicId, Long studentId) { }
    public record DecisionRequest(Long entityId, Long actorId, boolean approved, String note) { }
    public record DepartmentTopicDecisionRequest(Long topicId, Long departmentId, boolean approved, Long advisorTeacherId, String note) { }
    public record PlanSaveRequest(Long studentId, Long topicId, List<WeeklyTaskRequest> tasks) { }
    public record WeeklyTaskRequest(int week, String title, String deliverable, String focus) { }
    public record PlanSubmitRequest(Long planId, Long studentId) { }
    public record ReviewRequest(Long planId, Long reviewerId, int week, int score, String comment) { }
}
