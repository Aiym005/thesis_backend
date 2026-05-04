package com.tms.thesissystem.api;

import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.domain.ApprovalRecord;
import com.tms.thesissystem.domain.AuditEntry;
import com.tms.thesissystem.domain.Notification;
import com.tms.thesissystem.domain.Plan;
import com.tms.thesissystem.domain.PlanStatus;
import com.tms.thesissystem.domain.Review;
import com.tms.thesissystem.domain.Topic;
import com.tms.thesissystem.domain.TopicStatus;
import com.tms.thesissystem.domain.User;
import com.tms.thesissystem.domain.WeeklyTask;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApiResponseMapper {
    public ApiDtos.DashboardResponse toDashboardResponse(WorkflowQueryService.DashboardSnapshot snapshot) {
        return new ApiDtos.DashboardResponse(
                snapshot.users().stream().map(this::toUserDto).toList(),
                snapshot.topics().stream()
                        .filter(topic -> topic.getStatus() != TopicStatus.DELETED)
                        .map(this::toTopicDto)
                        .toList(),
                snapshot.plans().stream().map(this::toPlanDto).toList(),
                snapshot.reviews().stream().map(this::toReviewDto).toList(),
                snapshot.notifications().stream().map(this::toNotificationDto).toList(),
                snapshot.auditTrail().stream().map(this::toAuditEntryDto).toList(),
                new ApiDtos.SummaryDto(
                        snapshot.summary().pendingTopics(),
                        snapshot.summary().pendingPlans(),
                        snapshot.summary().totalReviews()
                )
        );
    }

    public ApiDtos.WorkflowStateResponse toWorkflowStateResponse(WorkflowQueryService.DashboardSnapshot snapshot) {
        List<ApiDtos.TopicDto> topics = snapshot.topics().stream()
                .filter(topic -> topic.getStatus() != TopicStatus.DELETED)
                .map(this::toTopicDto)
                .toList();
        List<ApiDtos.PlanDto> plans = snapshot.plans().stream().map(this::toPlanDto).toList();
        return new ApiDtos.WorkflowStateResponse(
                snapshot.users().stream().map(this::toUserDto).toList(),
                new ApiDtos.TopicStateResponse(
                        topics,
                        topics.stream()
                                .filter(topic -> !"DELETED".equals(topic.status()))
                                .filter(topic -> !"SUPERSEDED".equals(topic.status()))
                                .filter(topic -> !"REJECTED".equals(topic.status()))
                                .toList(),
                        filterTopics(topics, TopicStatus.AVAILABLE),
                        filterTopics(topics, TopicStatus.PENDING_TEACHER_APPROVAL),
                        filterTopics(topics, TopicStatus.PENDING_DEPARTMENT_APPROVAL),
                        filterTopics(topics, TopicStatus.APPROVED),
                        filterTopics(topics, TopicStatus.REJECTED)
                ),
                new ApiDtos.PlanStateResponse(
                        plans,
                        filterPlans(plans, PlanStatus.DRAFT),
                        filterPlans(plans, PlanStatus.PENDING_TEACHER_APPROVAL),
                        filterPlans(plans, PlanStatus.PENDING_DEPARTMENT_APPROVAL),
                        filterPlans(plans, PlanStatus.APPROVED),
                        filterPlans(plans, PlanStatus.REJECTED)
                )
        );
    }

    public ApiDtos.StudentWorkspaceResponse toStudentWorkspaceResponse(WorkflowQueryService.StudentWorkspace workspace) {
        return new ApiDtos.StudentWorkspaceResponse(
                workspace.topic() == null ? null : toTopicDto(workspace.topic()),
                workspace.plan() == null ? null : toPlanDto(workspace.plan())
        );
    }

    public ApiDtos.UserDto toUserDto(User user) {
        return new ApiDtos.UserDto(user.id(), user.role().name(), user.loginId(), user.firstName(), user.lastName(), user.email(), user.phoneNumber(), user.departmentName(), user.program());
    }

    public ApiDtos.TopicDto toTopicDto(Topic topic) {
        return new ApiDtos.TopicDto(
                topic.getId(),
                topic.getTitle(),
                topic.getDescription(),
                topic.getProgram(),
                topic.getProposerId(),
                topic.getProposerName(),
                topic.getProposerRole().name(),
                topic.getOwnerStudentId(),
                topic.getOwnerStudentName(),
                topic.getAdvisorTeacherId(),
                topic.getAdvisorTeacherName(),
                topic.getStatus().name(),
                topic.getCreatedAt(),
                topic.getUpdatedAt(),
                topic.getApprovals().stream().map(this::toApprovalRecordDto).toList()
        );
    }

    public ApiDtos.PlanDto toPlanDto(Plan plan) {
        return new ApiDtos.PlanDto(
                plan.getId(),
                plan.getTopicId(),
                plan.getTopicTitle(),
                plan.getStudentId(),
                plan.getStudentName(),
                plan.getStatus().name(),
                plan.getTasks().stream().map(this::toWeeklyTaskDto).toList(),
                plan.getApprovals().stream().map(this::toApprovalRecordDto).toList(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    public ApiDtos.ReviewDto toReviewDto(Review review) {
        return new ApiDtos.ReviewDto(review.id(), review.planId(), review.week(), review.reviewerId(), review.reviewerName(), review.score(), review.comment(), review.createdAt());
    }

    public ApiDtos.NotificationDto toNotificationDto(Notification notification) {
        return new ApiDtos.NotificationDto(notification.id(), notification.userId(), notification.title(), notification.message(), notification.createdAt());
    }

    public ApiDtos.AuditEntryDto toAuditEntryDto(AuditEntry auditEntry) {
        return new ApiDtos.AuditEntryDto(auditEntry.id(), auditEntry.entityType(), auditEntry.entityId(), auditEntry.action(), auditEntry.actorName(), auditEntry.detail(), auditEntry.createdAt());
    }

    private ApiDtos.WeeklyTaskDto toWeeklyTaskDto(WeeklyTask task) {
        return new ApiDtos.WeeklyTaskDto(task.week(), task.title(), task.deliverable(), task.focus());
    }

    private ApiDtos.ApprovalRecordDto toApprovalRecordDto(ApprovalRecord record) {
        return new ApiDtos.ApprovalRecordDto(record.stage().name(), record.actorId(), record.actorName(), record.approved(), record.note(), record.decidedAt());
    }

    private List<ApiDtos.TopicDto> filterTopics(List<ApiDtos.TopicDto> topics, TopicStatus status) {
        return topics.stream().filter(topic -> status.name().equals(topic.status())).toList();
    }

    private List<ApiDtos.PlanDto> filterPlans(List<ApiDtos.PlanDto> plans, PlanStatus status) {
        return plans.stream().filter(plan -> status.name().equals(plan.status())).toList();
    }
}
