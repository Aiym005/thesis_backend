package com.tms.thesissystem.service.topic.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.domain.TopicStatus;
import com.tms.thesissystem.domain.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicServiceController {
    private final WorkflowQueryService queryService;
    private final WorkflowCommandService commandService;
    private final ApiResponseMapper apiResponseMapper;

    public TopicServiceController(WorkflowQueryService queryService, WorkflowCommandService commandService, ApiResponseMapper apiResponseMapper) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.apiResponseMapper = apiResponseMapper;
    }

    @GetMapping
    public ApiDtos.TopicStateResponse topics() {
        return apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard()).topics();
    }

    @PostMapping("/proposals/student")
    public ApiDtos.TopicActionResponse proposeStudentTopic(@RequestBody StudentTopicProposalRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.proposeTopic(
                        request.studentId(), request.title(), request.description(), request.program())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/proposals/teacher")
    public ApiDtos.TopicActionResponse proposeTeacherTopic(@RequestBody TeacherTopicProposalRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.createTeacherTopic(
                        request.teacherId(), request.title(), request.description(), request.program())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/department-catalog")
    public ApiDtos.TopicActionResponse createDepartmentTopic(@RequestBody DepartmentTopicProposalRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.createDepartmentTopic(
                        request.departmentId(), request.title(), request.description(), request.program())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/student-update")
    public ApiDtos.TopicActionResponse updateStudentTopic(@RequestBody StudentTopicUpdateRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.updateStudentTopic(
                        request.topicId(), request.studentId(), request.title(), request.description(), request.program())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/teacher-update")
    public ApiDtos.TopicActionResponse updateTeacherTopic(@RequestBody TeacherTopicUpdateRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.updateTeacherTopic(
                        request.topicId(), request.teacherId(), request.title(), request.description(), request.program())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/department-update")
    public ApiDtos.TopicActionResponse updateDepartmentTopic(@RequestBody DepartmentTopicUpdateRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.updateDepartmentTopic(
                        request.topicId(), request.departmentId(), request.title(), request.description(), request.program())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/delete")
    public ApiDtos.TopicActionResponse deleteTopic(@RequestBody TopicDeleteRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.deleteTopic(
                        request.topicId(), request.actorId(), request.actorRole())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/claim")
    public ApiDtos.TopicActionResponse claimTopic(@RequestBody TopicClaimRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.claimTopic(request.topicId(), request.studentId())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/approvals/teacher")
    public ApiDtos.TopicActionResponse teacherDecision(@RequestBody TopicTeacherApprovalRequest request) {
        Long topicId = request.topicId();
        if (topicId == null) {
            topicId = queryService.getDashboard().topics().stream()
                    .filter(topic -> topic.status() == TopicStatus.PENDING_TEACHER_APPROVAL)
                    .filter(topic -> request.topicTitle() != null && request.topicTitle().equalsIgnoreCase(topic.title()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Сэдэв олдсонгүй."))
                    .id();
        }
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.teacherDecisionOnTopic(
                        topicId, request.teacherId(), request.approved(), request.note())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @PostMapping("/approvals/department")
    public ApiDtos.TopicActionResponse departmentDecision(@RequestBody TopicDepartmentApprovalRequest request) {
        return new ApiDtos.TopicActionResponse(
                apiResponseMapper.toTopicDto(commandService.departmentDecisionOnTopic(
                        request.topicId(), request.departmentId(), request.approved(), request.advisorTeacherId(), request.note())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiDtos.ApiErrorResponse handleDomainError(RuntimeException exception) {
        return new ApiDtos.ApiErrorResponse(exception.getMessage());
    }

    public record StudentTopicProposalRequest(Long studentId, String title, String description, String program) {
    }

    public record TeacherTopicProposalRequest(Long teacherId, String title, String description, String program) {
    }

    public record DepartmentTopicProposalRequest(Long departmentId, String title, String description, String program) {
    }

    public record StudentTopicUpdateRequest(Long topicId, Long studentId, String title, String description, String program) {
    }

    public record TeacherTopicUpdateRequest(Long topicId, Long teacherId, String title, String description, String program) {
    }

    public record DepartmentTopicUpdateRequest(Long topicId, Long departmentId, String title, String description, String program) {
    }

    public record TopicDeleteRequest(Long topicId, Long actorId, UserRole actorRole) {
    }

    public record TopicClaimRequest(Long topicId, Long studentId) {
    }

    public record TopicTeacherApprovalRequest(Long topicId, Long teacherId, String topicTitle, boolean approved, String note) {
    }

    public record TopicDepartmentApprovalRequest(Long topicId, Long departmentId, boolean approved, Long advisorTeacherId, String note) {
    }
}
