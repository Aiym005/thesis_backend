package com.tms.thesissystem.application.service;

import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.AuditEntry;
import com.tms.thesissystem.domain.Notification;
import com.tms.thesissystem.domain.Plan;
import com.tms.thesissystem.domain.PlanStatus;
import com.tms.thesissystem.domain.Review;
import com.tms.thesissystem.domain.Topic;
import com.tms.thesissystem.domain.TopicStatus;
import com.tms.thesissystem.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkflowQueryService {
    private final WorkflowRepository repository;

    public DashboardSnapshot getDashboard() {
        List<Topic> topics = repository.findAllTopics();
        List<Plan> plans = repository.findAllPlans();
        List<Review> reviews = repository.findAllReviews();
        return new DashboardSnapshot(
                repository.findAllUsers(),
                topics,
                plans,
                reviews,
                repository.findAllNotifications(),
                repository.findAllAuditEntries(),
                new Summary(
                        topics.stream().filter(topic -> topic.getStatus().name().startsWith("PENDING")).count(),
                        plans.stream().filter(plan -> plan.getStatus().name().startsWith("PENDING")).count(),
                        reviews.size()
                )
        );
    }

    public StudentWorkspace getStudentWorkspace(Long studentId) {
        if (studentId == null) {
            return new StudentWorkspace(null, null);
        }

        List<Topic> topics = repository.findAllTopics();
        Topic studentTopic = selectPreferredTopic(topics, studentId).orElse(null);
        Plan studentPlan = selectPreferredPlan(repository.findAllPlans(), studentId, studentTopic == null ? null : studentTopic.getId()).orElse(null);
        return new StudentWorkspace(studentTopic, studentPlan);
    }

    private Optional<Topic> selectPreferredTopic(List<Topic> topics, Long studentId) {
        Comparator<Topic> byNewestFirst = Comparator
                .comparing(Topic::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Topic::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        return topics.stream()
                .filter(topic -> studentId.equals(topic.getOwnerStudentId()) || studentId.equals(topic.getProposerId()))
                .min((left, right) -> {
                    int leftPriority = topicPriority(left.getStatus());
                    int rightPriority = topicPriority(right.getStatus());
                    if (leftPriority != rightPriority) {
                        return Integer.compare(leftPriority, rightPriority);
                    }
                    return byNewestFirst.compare(left, right);
                });
    }

    private int topicPriority(TopicStatus status) {
        if (status == TopicStatus.PENDING_TEACHER_APPROVAL || status == TopicStatus.PENDING_DEPARTMENT_APPROVAL) {
            return 0;
        }
        if (status == TopicStatus.APPROVED) {
            return 1;
        }
        if (status == TopicStatus.REJECTED) {
            return 2;
        }
        return 3;
    }

    private Optional<Plan> selectPreferredPlan(List<Plan> plans, Long studentId, Long topicId) {
        Comparator<Plan> byNewest = Comparator
                .comparing(Plan::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Plan::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        List<Plan> studentPlans = plans.stream()
                .filter(plan -> studentId.equals(plan.getStudentId()))
                .toList();
        List<Plan> topicPlans = topicId == null ? studentPlans : studentPlans.stream().filter(plan -> topicId.equals(plan.getTopicId())).toList();
        List<Plan> preferredPool = topicPlans.isEmpty() ? studentPlans : topicPlans;

        Optional<Plan> nonRejected = preferredPool.stream()
                .filter(plan -> plan.getStatus() != PlanStatus.REJECTED)
                .max(byNewest);
        if (nonRejected.isPresent()) {
            return nonRejected;
        }

        Optional<Plan> rejected = preferredPool.stream()
                .filter(plan -> plan.getStatus() == PlanStatus.REJECTED)
                .max(byNewest);
        if (rejected.isPresent()) {
            return rejected;
        }

        return studentPlans.stream().max(byNewest);
    }

    public record DashboardSnapshot(
            List<User> users,
            List<Topic> topics,
            List<Plan> plans,
            List<Review> reviews,
            List<Notification> notifications,
            List<AuditEntry> auditTrail,
            Summary summary
    ) { }

    public record StudentWorkspace(Topic topic, Plan plan) { }

    public record Summary(long pendingTopics, long pendingPlans, long totalReviews) { }
}
