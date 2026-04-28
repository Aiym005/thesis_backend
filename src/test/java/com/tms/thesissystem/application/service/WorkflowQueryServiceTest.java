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
import com.tms.thesissystem.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowQueryServiceTest {

    private final WorkflowRepository repository = mock(WorkflowRepository.class);
    private final WorkflowQueryService service = new WorkflowQueryService(repository);

    @Test
    void aggregatesDashboardDataAndPendingCounts() {
        LocalDateTime now = LocalDateTime.now();
        User user = new User(100001L, UserRole.STUDENT, "22b1num0027", "Ану", "Бат-Эрдэнэ", "anu.bat-erdene@tms.mn", "Software Engineering", "SE");
        Topic pendingTopic = new Topic(1L, "Topic A", "Desc", "SE", user.id(), user.fullName(), UserRole.STUDENT,
                user.id(), user.fullName(), null, null, TopicStatus.PENDING_TEACHER_APPROVAL, now, now, List.of());
        Topic approvedTopic = new Topic(2L, "Topic B", "Desc", "SE", user.id(), user.fullName(), UserRole.STUDENT,
                user.id(), user.fullName(), 200001L, "Teacher One", TopicStatus.APPROVED, now, now, List.of());
        Plan pendingPlan = new Plan(10L, 2L, "Topic B", user.id(), user.fullName(), PlanStatus.PENDING_TEACHER_APPROVAL, List.of(), List.of(), now, now);
        Review review = new Review(20L, 10L, 1, 200001L, "Teacher One", 95, "Good", now);
        Notification notification = new Notification(30L, user.id(), "Title", "Body", now);
        AuditEntry auditEntry = new AuditEntry(40L, "TOPIC", 2L, "APPROVED", "Teacher One", "Approved", now);

        when(repository.findAllUsers()).thenReturn(List.of(user));
        when(repository.findAllTopics()).thenReturn(List.of(pendingTopic, approvedTopic));
        when(repository.findAllPlans()).thenReturn(List.of(pendingPlan));
        when(repository.findAllReviews()).thenReturn(List.of(review));
        when(repository.findAllNotifications()).thenReturn(List.of(notification));
        when(repository.findAllAuditEntries()).thenReturn(List.of(auditEntry));

        WorkflowQueryService.DashboardSnapshot snapshot = service.getDashboard();

        assertThat(snapshot.users()).containsExactly(user);
        assertThat(snapshot.topics()).containsExactly(pendingTopic, approvedTopic);
        assertThat(snapshot.plans()).containsExactly(pendingPlan);
        assertThat(snapshot.reviews()).containsExactly(review);
        assertThat(snapshot.notifications()).containsExactly(notification);
        assertThat(snapshot.auditTrail()).containsExactly(auditEntry);
        assertThat(snapshot.summary().pendingTopics()).isEqualTo(1);
        assertThat(snapshot.summary().pendingPlans()).isEqualTo(1);
        assertThat(snapshot.summary().totalReviews()).isEqualTo(1);
    }
}
