package com.tms.thesissystem.application.service;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.application.port.WorkflowEventPublisher;
import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.ApprovalRecord;
import com.tms.thesissystem.domain.ApprovalStage;
import com.tms.thesissystem.domain.Plan;
import com.tms.thesissystem.domain.PlanStatus;
import com.tms.thesissystem.domain.Review;
import com.tms.thesissystem.domain.Topic;
import com.tms.thesissystem.domain.TopicStatus;
import com.tms.thesissystem.domain.User;
import com.tms.thesissystem.domain.UserRole;
import com.tms.thesissystem.domain.WeeklyTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowCommandServiceTest {

    private static final Long STUDENT_ID = 100001L;
    private static final Long TEACHER_ID = 200001L;
    private static final Long DEPARTMENT_ID = 300001L;

    private final WorkflowRepository repository = mock(WorkflowRepository.class);
    private final WorkflowEventPublisher eventPublisher = mock(WorkflowEventPublisher.class);
    private final WorkflowCommandService service = new WorkflowCommandService(repository, eventPublisher);

    private final User student = new User(STUDENT_ID, UserRole.STUDENT, "22b1num0027", "Ану", "Бат-Эрдэнэ", "anu.bat-erdene@tms.mn", "99000001", "Software Engineering", "SE");
    private final User teacher = new User(TEACHER_ID, UserRole.TEACHER, "tch001", "Энх", "Сүрэн", "enkh.suren@tms.mn", "99000002", "Software Engineering", "SE");
    private final User department = new User(DEPARTMENT_ID, UserRole.DEPARTMENT, "sisi-admin", "Dept", "Admin", "dept@example.com", null, "Software Engineering", "B.SE");

    @BeforeEach
    void setUp() {
        when(repository.findUserById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(repository.findUserById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(repository.findUserById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(repository.saveTopic(any(Topic.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.savePlan(any(Plan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveReview(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void proposeTopicPersistsTopicAndPublishesTeacherNotification() {
        when(repository.nextTopicId()).thenReturn(1L);
        when(repository.findUsersByRole(UserRole.TEACHER)).thenReturn(List.of(teacher));

        Topic topic = service.proposeTopic(1L, "AI Thesis", "Desc", "SE");

        assertThat(topic.getId()).isEqualTo(1L);
        assertThat(topic.getStatus()).isEqualTo(TopicStatus.PENDING_TEACHER_APPROVAL);
        verify(repository).saveTopic(topic);
        WorkflowEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo("TOPIC_PROPOSED");
        assertThat(event.recipientIds()).containsExactly(STUDENT_ID);
    }

    @Test
    void departmentApprovalAssignsAdvisorAndSupersedesPreviousTopics() {
        Topic pendingTopic = new Topic(10L, "Chosen Topic", "Desc", "SE", STUDENT_ID, student.fullName(), UserRole.STUDENT,
                STUDENT_ID, student.fullName(), null, null, TopicStatus.PENDING_DEPARTMENT_APPROVAL, now(), now(),
                new ArrayList<>(List.of(new ApprovalRecord(ApprovalStage.TEACHER, TEACHER_ID, teacher.fullName(), true, "ok", now()))));
        Topic previousTopic = new Topic(11L, "Old Topic", "Desc", "SE", STUDENT_ID, student.fullName(), UserRole.STUDENT,
                STUDENT_ID, student.fullName(), TEACHER_ID, teacher.fullName(), TopicStatus.APPROVED, now(), now(), new ArrayList<>());

        when(repository.findTopicById(10L)).thenReturn(Optional.of(pendingTopic));
        when(repository.findAllTopics()).thenReturn(List.of(pendingTopic, previousTopic));

        Topic result = service.departmentDecisionOnTopic(10L, 1L, true, 1L, "approved");

        assertThat(result.getStatus()).isEqualTo(TopicStatus.APPROVED);
        assertThat(result.getAdvisorTeacherId()).isEqualTo(TEACHER_ID);
        assertThat(previousTopic.getStatus()).isEqualTo(TopicStatus.SUPERSEDED);
        WorkflowEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo("TOPIC_FINALIZED");
        assertThat(event.recipientIds()).containsExactly(DEPARTMENT_ID, STUDENT_ID, TEACHER_ID);
    }

    @Test
    void savePlanUsesLatestApprovedTopicWhenRequestedTopicMissing() {
        Topic approvedTopic = new Topic(22L, "Approved Topic", "Desc", "SE", STUDENT_ID, student.fullName(), UserRole.STUDENT,
                STUDENT_ID, student.fullName(), TEACHER_ID, teacher.fullName(), TopicStatus.APPROVED, now().minusDays(1), now(), new ArrayList<>());
        List<WeeklyTask> tasks = weeklyTasks(15);

        when(repository.findTopicById(999L)).thenReturn(Optional.empty());
        when(repository.findAllTopics()).thenReturn(List.of(approvedTopic));
        when(repository.findPlanByStudentId(STUDENT_ID)).thenReturn(Optional.empty());
        when(repository.nextPlanId()).thenReturn(50L);

        Plan plan = service.savePlan(1L, null, tasks);

        assertThat(plan.getId()).isEqualTo(50L);
        assertThat(plan.getTopicId()).isEqualTo(22L);
        assertThat(plan.getTasks()).hasSize(15);
        WorkflowEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo("PLAN_SAVED");
        assertThat(event.recipientIds()).containsExactly(STUDENT_ID, TEACHER_ID);
    }

    @Test
    void updateRejectedTopicResetsStatusAndClearsPreviousApprovals() {
        Topic rejectedTopic = new Topic(12L, "Rejected Topic", "Desc", "SE", STUDENT_ID, student.fullName(), UserRole.STUDENT,
                STUDENT_ID, student.fullName(), null, null, TopicStatus.REJECTED, now(), now(),
                new ArrayList<>(List.of(new ApprovalRecord(ApprovalStage.TEACHER, TEACHER_ID, teacher.fullName(), false, "revise", now()))));

        when(repository.findTopicById(12L)).thenReturn(Optional.of(rejectedTopic));
        when(repository.findAllTopics()).thenReturn(List.of(rejectedTopic));
        when(repository.findUsersByRole(UserRole.TEACHER)).thenReturn(List.of(teacher));

        Topic updated = service.updateStudentTopic(12L, 1L, "Retried Topic", "Updated", "SE");

        assertThat(updated.getStatus()).isEqualTo(TopicStatus.PENDING_TEACHER_APPROVAL);
        assertThat(updated.getApprovals()).isEmpty();
    }

    @Test
    void submitRejectedPlanClearsPreviousApprovalsBeforeResubmission() {
        Plan rejectedPlan = new Plan(31L, 41L, "Approved Topic", STUDENT_ID, student.fullName(), PlanStatus.REJECTED,
                weeklyTasks(15),
                List.of(new ApprovalRecord(ApprovalStage.TEACHER, TEACHER_ID, teacher.fullName(), false, "fix it", now())),
                now(), now());
        Topic topic = new Topic(41L, "Approved Topic", "Desc", "SE", STUDENT_ID, student.fullName(), UserRole.STUDENT,
                STUDENT_ID, student.fullName(), TEACHER_ID, teacher.fullName(), TopicStatus.APPROVED, now(), now(), new ArrayList<>());

        when(repository.findPlanById(31L)).thenReturn(Optional.of(rejectedPlan));
        when(repository.findTopicById(41L)).thenReturn(Optional.of(topic));
        when(repository.findUsersByRole(UserRole.STUDENT)).thenReturn(List.of(student));
        when(repository.findUsersByRole(UserRole.TEACHER)).thenReturn(List.of(teacher));

        Plan submitted = service.submitPlan(31L, 1L);

        assertThat(submitted.getStatus()).isEqualTo(PlanStatus.PENDING_TEACHER_APPROVAL);
        assertThat(submitted.getApprovals()).isEmpty();
    }

    @Test
    void submitPlanRequiresAdvisorAssignment() {
        Plan plan = new Plan(31L, 41L, "Approved Topic", STUDENT_ID, student.fullName(), PlanStatus.DRAFT, weeklyTasks(15), List.of(), now(), now());
        Topic topic = new Topic(41L, "Approved Topic", "Desc", "SE", STUDENT_ID, student.fullName(), UserRole.STUDENT,
                STUDENT_ID, student.fullName(), null, null, TopicStatus.APPROVED, now(), now(), new ArrayList<>());

        when(repository.findPlanById(31L)).thenReturn(Optional.of(plan));
        when(repository.findTopicById(41L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.submitPlan(31L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("удирдагч багш");
    }

    @Test
    void teacherDecisionOnPlanRejectsNonAdvisor() {
        Plan plan = new Plan(31L, 41L, "Approved Topic", STUDENT_ID, student.fullName(), PlanStatus.PENDING_TEACHER_APPROVAL, weeklyTasks(15), List.of(), now(), now());
        Topic topic = new Topic(41L, "Approved Topic", "Desc", "SE", STUDENT_ID, student.fullName(), UserRole.STUDENT,
                STUDENT_ID, student.fullName(), 200099L, "Another Teacher", TopicStatus.APPROVED, now(), now(), new ArrayList<>());

        when(repository.findPlanById(31L)).thenReturn(Optional.of(plan));
        when(repository.findTopicById(41L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.teacherDecisionOnPlan(31L, 1L, true, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("зөвхөн удирдагч багш");
    }

    @Test
    void submitReviewStoresReviewAndPublishesToStudent() {
        Plan plan = new Plan(31L, 41L, "Approved Topic", STUDENT_ID, student.fullName(), PlanStatus.APPROVED, weeklyTasks(15), List.of(), now(), now());

        when(repository.findPlanById(31L)).thenReturn(Optional.of(plan));
        when(repository.nextReviewId()).thenReturn(77L);

        Review review = service.submitReview(31L, 1L, 2, 88, "progress");

        assertThat(review.id()).isEqualTo(77L);
        verify(repository).saveReview(review);
        WorkflowEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo("REVIEW_SUBMITTED");
        assertThat(event.recipientIds()).containsExactly(STUDENT_ID);
    }

    private WorkflowEvent capturedEvent() {
        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(eventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private static List<WeeklyTask> weeklyTasks(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(week -> new WeeklyTask(week, "Week " + week, "Deliverable " + week, "Focus " + week))
                .toList();
    }

    private static LocalDateTime now() {
        return LocalDateTime.of(2026, 4, 11, 12, 0);
    }
}
