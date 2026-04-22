package com.tms.thesissystem.microservices.workflow;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.DatabaseStatusService;
import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.domain.model.WeeklyTask;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkflowCompatibilityController {
    private final WorkflowQueryService queryService;
    private final WorkflowCommandService commandService;
    private final ApiResponseMapper apiResponseMapper;
    private final DatabaseStatusService databaseStatusService;

    public WorkflowCompatibilityController(WorkflowQueryService queryService,
                                           WorkflowCommandService commandService,
                                           ApiResponseMapper apiResponseMapper,
                                           DatabaseStatusService databaseStatusService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.apiResponseMapper = apiResponseMapper;
        this.databaseStatusService = databaseStatusService;
    }

    @GetMapping("/dashboard")
    public ApiDtos.DashboardResponse dashboard() {
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @GetMapping("/verification/state")
    public ApiDtos.DashboardResponse verificationState() {
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @GetMapping("/verification/users")
    public List<ApiDtos.UserDto> verificationUsers() {
        return queryService.getDashboard().users().stream()
                .map(apiResponseMapper::toUserDto)
                .toList();
    }

    @GetMapping("/system/database")
    public DatabaseStatusService.DatabaseStatus databaseStatus() {
        return databaseStatusService.check();
    }

    @PostMapping("/verification/topics/student-proposals")
    public ApiDtos.DashboardResponse studentProposesTopic(@RequestBody StudentTopicProposalRequest request) {
        commandService.proposeTopic(request.studentId(), request.title(), request.description(), request.program());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/teacher-proposals")
    public ApiDtos.DashboardResponse teacherProposesTopic(@RequestBody TeacherTopicProposalRequest request) {
        commandService.createTeacherTopic(request.teacherId(), request.title(), request.description(), request.program());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/department-proposals")
    public ApiDtos.DashboardResponse departmentCreatesApprovedTopic(@RequestBody DepartmentTopicProposalRequest request) {
        commandService.createDepartmentTopic(request.departmentId(), request.title(), request.description(), request.program());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/selections")
    public ApiDtos.DashboardResponse studentSelectsTopic(@RequestBody TopicSelectionRequest request) {
        commandService.claimTopic(request.topicId(), request.studentId());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/student-updates")
    public ApiDtos.DashboardResponse studentUpdatesTopic(@RequestBody StudentTopicUpdateRequest request) {
        commandService.updateStudentTopic(request.topicId(), request.studentId(), request.title(), request.description(), request.program());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/teacher-updates")
    public ApiDtos.DashboardResponse teacherUpdatesTopic(@RequestBody TeacherTopicUpdateRequest request) {
        commandService.updateTeacherTopic(request.topicId(), request.teacherId(), request.title(), request.description(), request.program());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/department-updates")
    public ApiDtos.DashboardResponse departmentUpdatesTopic(@RequestBody DepartmentTopicUpdateRequest request) {
        commandService.updateDepartmentTopic(request.topicId(), request.departmentId(), request.title(), request.description(), request.program());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/deletions")
    public ApiDtos.DashboardResponse deleteTopic(@RequestBody TopicDeleteRequest request) {
        commandService.deleteTopic(request.topicId(), request.actorId(), request.actorRole());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/teacher-approvals")
    public ApiDtos.DashboardResponse teacherApprovesTopic(@RequestBody TopicTeacherApprovalRequest request) {
        commandService.teacherDecisionOnTopic(request.resolvedTopicId(), request.resolvedTeacherId(), request.approved(), request.note());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/topics/department-approvals")
    public ApiDtos.DashboardResponse departmentApprovesTopic(@RequestBody TopicDepartmentApprovalRequest request) {
        commandService.departmentDecisionOnTopic(request.topicId(), request.departmentId(), request.approved(), request.advisorTeacherId(), request.note());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/plans")
    public ApiDtos.DashboardResponse studentCreatesPlan(@RequestBody StudentPlanRequest request) {
        List<WeeklyTask> tasks = request.tasks().stream()
                .map(task -> new WeeklyTask(task.week(), task.title(), task.deliverable(), task.focus()))
                .toList();
        commandService.savePlan(request.studentId(), request.topicId(), tasks);
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/plans/submit")
    public ApiDtos.DashboardResponse studentSubmitsPlan(@RequestBody PlanSubmitRequest request) {
        commandService.submitPlan(request.planId(), request.studentId());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/plans/teacher-approvals")
    public ApiDtos.DashboardResponse teacherApprovesPlan(@RequestBody PlanApprovalRequest request) {
        commandService.teacherDecisionOnPlan(request.planId(), request.resolvedActorId(), request.approved(), request.note());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @PostMapping("/verification/plans/department-approvals")
    public ApiDtos.DashboardResponse departmentApprovesPlan(@RequestBody PlanApprovalRequest request) {
        commandService.departmentDecisionOnPlan(request.planId(), request.resolvedActorId(), request.approved(), request.note());
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleDomainError(RuntimeException exception) {
        return new ApiErrorResponse(exception.getMessage());
    }

    public record StudentTopicProposalRequest(Long studentId, String title, String description, String program) {
    }

    public record TeacherTopicProposalRequest(Long teacherId, String title, String description, String program) {
    }

    public record DepartmentTopicProposalRequest(Long departmentId, String title, String description, String program) {
    }

    public record TopicSelectionRequest(Long topicId, Long studentId) {
    }

    public record StudentTopicUpdateRequest(Long topicId, Long studentId, String title, String description, String program) {
    }

    public record TeacherTopicUpdateRequest(Long topicId, Long teacherId, String title, String description, String program) {
    }

    public record DepartmentTopicUpdateRequest(Long topicId, Long departmentId, String title, String description, String program) {
    }

    public record TopicDeleteRequest(Long topicId, Long actorId, com.tms.thesissystem.domain.model.UserRole actorRole) {
    }

    public record TopicTeacherApprovalRequest(Long topicId, Long entityId, Long teacherId, Long actorId, boolean approved, String note) {
        public Long resolvedTopicId() {
            return topicId != null ? topicId : entityId;
        }

        public Long resolvedTeacherId() {
            return teacherId != null ? teacherId : actorId;
        }
    }

    public record TopicDepartmentApprovalRequest(Long topicId, Long departmentId, boolean approved, Long advisorTeacherId, String note) {
    }

    public record StudentPlanRequest(Long studentId, Long topicId, List<WeeklyTaskRequest> tasks) {
    }

    public record WeeklyTaskRequest(int week, String title, String deliverable, String focus) {
    }

    public record PlanSubmitRequest(Long planId, Long studentId) {
    }

    public record PlanApprovalRequest(Long planId, Long actorId, Long teacherId, Long departmentId, boolean approved, String note) {
        public Long resolvedActorId() {
            if (actorId != null) {
                return actorId;
            }
            if (teacherId != null) {
                return teacherId;
            }
            return departmentId;
        }
    }

    public record ApiErrorResponse(String message) {
    }
}
