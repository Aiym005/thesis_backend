package com.tms.thesissystem.service.plan.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.domain.WeeklyTask;
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
@RequestMapping("/api/plans")
public class PlanServiceController {
    private final WorkflowQueryService queryService;
    private final WorkflowCommandService commandService;
    private final ApiResponseMapper apiResponseMapper;

    public PlanServiceController(WorkflowQueryService queryService, WorkflowCommandService commandService, ApiResponseMapper apiResponseMapper) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.apiResponseMapper = apiResponseMapper;
    }

    @GetMapping
    public ApiDtos.PlanStateResponse plans() {
        return apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard()).plans();
    }

    @PostMapping
    public ApiDtos.PlanActionResponse savePlan(@RequestBody PlanSaveRequest request) {
        List<WeeklyTask> tasks = request.tasks().stream()
                .map(task -> new WeeklyTask(task.week(), task.title(), task.deliverable(), task.focus()))
                .toList();
        return new ApiDtos.PlanActionResponse(
                apiResponseMapper.toPlanDto(commandService.savePlan(request.studentId(), request.topicId(), tasks)),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/submit")
    public ApiDtos.PlanActionResponse submitPlan(@RequestBody PlanSubmitRequest request) {
        return new ApiDtos.PlanActionResponse(
                apiResponseMapper.toPlanDto(commandService.submitPlan(request.planId(), request.studentId())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/approvals/teacher")
    public ApiDtos.PlanActionResponse teacherDecision(@RequestBody PlanDecisionRequest request) {
        return new ApiDtos.PlanActionResponse(
                apiResponseMapper.toPlanDto(commandService.teacherDecisionOnPlan(
                        request.planId(), request.actorId(), request.approved(), request.note())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/approvals/department")
    public ApiDtos.PlanActionResponse departmentDecision(@RequestBody PlanDecisionRequest request) {
        return new ApiDtos.PlanActionResponse(
                apiResponseMapper.toPlanDto(commandService.departmentDecisionOnPlan(
                        request.planId(), request.actorId(), request.approved(), request.note())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiDtos.ApiErrorResponse handleDomainError(RuntimeException exception) {
        return new ApiDtos.ApiErrorResponse(exception.getMessage());
    }

    public record PlanSaveRequest(Long studentId, Long topicId, List<WeeklyTaskRequest> tasks) {
    }

    public record WeeklyTaskRequest(int week, String title, String deliverable, String focus) {
    }

    public record PlanSubmitRequest(Long planId, Long studentId) {
    }

    public record PlanDecisionRequest(Long planId, Long actorId, boolean approved, String note) {
    }
}
