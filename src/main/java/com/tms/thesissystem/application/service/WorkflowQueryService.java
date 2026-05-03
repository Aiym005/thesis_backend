package com.tms.thesissystem.application.service;

import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.AuditEntry;
import com.tms.thesissystem.domain.Notification;
import com.tms.thesissystem.domain.Plan;
import com.tms.thesissystem.domain.Review;
import com.tms.thesissystem.domain.Topic;
import com.tms.thesissystem.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public record DashboardSnapshot(
            List<User> users,
            List<Topic> topics,
            List<Plan> plans,
            List<Review> reviews,
            List<Notification> notifications,
            List<AuditEntry> auditTrail,
            Summary summary
    ) { }

    public record Summary(long pendingTopics, long pendingPlans, long totalReviews) { }
}
