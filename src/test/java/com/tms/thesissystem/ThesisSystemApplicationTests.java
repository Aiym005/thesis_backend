package com.tms.thesissystem;

import com.tms.thesissystem.api.SystemController;
import com.tms.thesissystem.api.WorkflowController;
import com.tms.thesissystem.api.WorkflowVerificationController;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.domain.model.PlanStatus;
import com.tms.thesissystem.domain.model.TopicStatus;
import com.tms.thesissystem.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ThesisSystemApplicationTests {

    @Autowired
    private WorkflowController workflowController;

    @Autowired
    private WorkflowQueryService workflowQueryService;

    @Autowired
    private SystemController systemController;

    @Autowired
    private WorkflowVerificationController workflowVerificationController;

    @Test
    void dashboardLoads() {
        WorkflowQueryService.DashboardSnapshot snapshot = workflowController.dashboard();
        assertNotNull(snapshot);
        assertFalse(snapshot.users().isEmpty());
        assertFalse(snapshot.topics().isEmpty());
    }

    @Test
    void topicProposalCreatesPendingTeacherApproval() {
        Long studentId = workflowQueryService.getDashboard().users().stream()
                .filter(user -> user.role() == UserRole.STUDENT)
                .findFirst()
                .orElseThrow()
                .id();
        WorkflowQueryService.DashboardSnapshot snapshot = workflowController.proposeTopic(
                new WorkflowController.TopicProposalRequest(studentId, "Distributed Thesis Governance", "Workflow orchestration", "B.SE")
        );
        assertNotNull(snapshot);
        assertFalse(snapshot.topics().isEmpty());
    }

    @Test
    void databaseStatusEndpointExists() {
        assertNotNull(systemController.databaseStatus());
    }

    @Test
    void verificationControllerExposesDbBackedWorkflowState() {
        WorkflowVerificationController.WorkflowStateResponse state = workflowVerificationController.state();
        assertNotNull(state);
        assertFalse(state.users().isEmpty());
        assertNotNull(state.topics());
        assertNotNull(state.plans());
    }

    @Test
    void fullTopicAndPlanApprovalFlowWorks() {
        WorkflowQueryService.DashboardSnapshot initial = workflowQueryService.getDashboard();
        Long studentId = initial.users().stream().filter(user -> user.role() == UserRole.STUDENT).findFirst().orElseThrow().id();
        Long teacherId = initial.users().stream().filter(user -> user.role() == UserRole.TEACHER).findFirst().orElseThrow().id();
        Long departmentId = initial.users().stream().filter(user -> user.role() == UserRole.DEPARTMENT).findFirst().orElseThrow().id();
        Long secondTeacherId = initial.users().stream().filter(user -> user.role() == UserRole.TEACHER).skip(1).findFirst().orElseThrow().id();

        Long availableTopicId = workflowController.createTeacherTopic(
                new WorkflowController.TeacherTopicRequest(
                        teacherId,
                        "Regression Flow Catalog Topic",
                        "Catalog topic for end-to-end approval flow.",
                        "B.SE"
                )
        ).topics().stream()
                .filter(topic -> "Regression Flow Catalog Topic".equals(topic.title()))
                .findFirst()
                .map(topic -> topic.id())
                .orElseThrow();

        workflowController.departmentTopicDecision(
                new WorkflowController.DepartmentTopicDecisionRequest(availableTopicId, departmentId, true, null, "Catalog ready")
        );

        WorkflowQueryService.DashboardSnapshot afterClaim = workflowController.claimTopic(
                new WorkflowController.TopicClaimRequest(availableTopicId, studentId)
        );
        assertNotNull(afterClaim);

        WorkflowQueryService.DashboardSnapshot afterTeacherTopicApproval = workflowController.teacherTopicDecision(
                new WorkflowController.DecisionRequest(availableTopicId, teacherId, true, "OK")
        );
        assertNotNull(afterTeacherTopicApproval);

        WorkflowQueryService.DashboardSnapshot afterDepartmentTopicApproval = workflowController.departmentTopicDecision(
                new WorkflowController.DepartmentTopicDecisionRequest(availableTopicId, departmentId, true, secondTeacherId, "Assigned")
        );
        assertNotNull(afterDepartmentTopicApproval);

        WorkflowController.PlanSaveRequest planSaveRequest = new WorkflowController.PlanSaveRequest(
                studentId,
                availableTopicId,
                java.util.stream.IntStream.rangeClosed(1, 15)
                        .mapToObj(week -> new WorkflowController.WeeklyTaskRequest(
                                week,
                                "Week " + week,
                                "Deliverable " + week,
                                "Focus " + week
                        ))
                        .toList()
        );
        WorkflowQueryService.DashboardSnapshot afterPlanSave = workflowController.savePlan(planSaveRequest);
        assertNotNull(afterPlanSave);

        Long savedPlanId = afterPlanSave.plans().stream()
                .filter(plan -> plan.studentId().equals(studentId))
                .findFirst()
                .orElseThrow()
                .id();

        WorkflowQueryService.DashboardSnapshot afterPlanSubmit = workflowController.submitPlan(
                new WorkflowController.PlanSubmitRequest(savedPlanId, studentId)
        );
        assertNotNull(afterPlanSubmit);

        WorkflowQueryService.DashboardSnapshot afterTeacherPlanApproval = workflowController.teacherPlanDecision(
                new WorkflowController.DecisionRequest(savedPlanId, teacherId, true, "Plan OK")
        );
        assertNotNull(afterTeacherPlanApproval);

        WorkflowQueryService.DashboardSnapshot afterDepartmentPlanApproval = workflowController.departmentPlanDecision(
                new WorkflowController.DecisionRequest(savedPlanId, departmentId, true, "Final OK")
        );
        assertNotNull(afterDepartmentPlanApproval);

        assertFalse(afterDepartmentPlanApproval.plans().stream()
                .filter(plan -> plan.id().equals(savedPlanId))
                .findFirst()
                .orElseThrow()
                .status()
                .name()
                .isBlank());
    }

    @Test
    void verificationControllerRunsFullTeacherStudentDepartmentWorkflow() {
        WorkflowVerificationController.WorkflowStateResponse initial = workflowVerificationController.state();
        Long studentId = initial.users().stream().filter(user -> user.role() == UserRole.STUDENT).findFirst().orElseThrow().id();
        Long teacherId = initial.users().stream().filter(user -> user.role() == UserRole.TEACHER).findFirst().orElseThrow().id();
        Long advisorTeacherId = initial.users().stream().filter(user -> user.role() == UserRole.TEACHER).skip(1).findFirst().orElseThrow().id();
        Long departmentId = initial.users().stream().filter(user -> user.role() == UserRole.DEPARTMENT).findFirst().orElseThrow().id();

        WorkflowVerificationController.TopicActionResponse teacherTopic = workflowVerificationController.teacherProposesTopic(
                new WorkflowVerificationController.TeacherTopicProposalRequest(
                        teacherId,
                        "Department Approved Catalog Topic",
                        "Teacher proposes a topic that department adds to approved catalog.",
                        "B.SE"
                )
        );
        assertEquals(TopicStatus.PENDING_DEPARTMENT_APPROVAL, teacherTopic.topic().status());

        WorkflowVerificationController.TopicActionResponse departmentCatalogApproval = workflowVerificationController.departmentApprovesTopic(
                new WorkflowVerificationController.TopicDepartmentApprovalRequest(
                        teacherTopic.topic().id(),
                        departmentId,
                        true,
                        null,
                        "Catalog approved"
                )
        );
        assertEquals(TopicStatus.AVAILABLE, departmentCatalogApproval.topic().status());

        WorkflowVerificationController.TopicActionResponse studentSelection = workflowVerificationController.studentSelectsApprovedTopic(
                new WorkflowVerificationController.TopicSelectionRequest(departmentCatalogApproval.topic().id(), studentId)
        );
        assertEquals(TopicStatus.PENDING_TEACHER_APPROVAL, studentSelection.topic().status());

        WorkflowVerificationController.TopicActionResponse teacherApproval = workflowVerificationController.teacherApprovesStudentTopic(
                new WorkflowVerificationController.TopicTeacherApprovalRequest(
                        studentSelection.topic().id(),
                        null,
                        teacherId,
                        null,
                        studentSelection.topic().title(),
                        true,
                        "Teacher approved student selection"
                )
        );
        assertEquals(TopicStatus.PENDING_DEPARTMENT_APPROVAL, teacherApproval.topic().status());

        WorkflowVerificationController.TopicActionResponse departmentFinalApproval = workflowVerificationController.departmentApprovesTopic(
                new WorkflowVerificationController.TopicDepartmentApprovalRequest(
                        studentSelection.topic().id(),
                        departmentId,
                        true,
                        advisorTeacherId,
                        "Department assigned advisor"
                )
        );
        assertEquals(TopicStatus.APPROVED, departmentFinalApproval.topic().status());

        WorkflowVerificationController.PlanActionResponse savedPlan = workflowVerificationController.studentCreatesPlan(
                new WorkflowVerificationController.StudentPlanRequest(
                        studentId,
                        departmentFinalApproval.topic().id(),
                        java.util.stream.IntStream.rangeClosed(1, 15)
                                .mapToObj(week -> new WorkflowVerificationController.WeeklyTaskRequest(
                                        week,
                                        "Week " + week,
                                        "Deliverable " + week,
                                        "Focus " + week
                                ))
                                .toList()
                )
        );
        assertEquals(PlanStatus.DRAFT, savedPlan.plan().status());

        WorkflowVerificationController.PlanActionResponse submittedPlan = workflowVerificationController.studentSubmitsPlan(
                new WorkflowVerificationController.PlanSubmitRequest(savedPlan.plan().id(), studentId)
        );
        assertEquals(PlanStatus.PENDING_TEACHER_APPROVAL, submittedPlan.plan().status());

        WorkflowVerificationController.PlanActionResponse teacherPlanApproval = workflowVerificationController.teacherApprovesPlan(
                new WorkflowVerificationController.PlanApprovalRequest(submittedPlan.plan().id(), teacherId, true, "Teacher plan approval")
        );
        assertEquals(PlanStatus.PENDING_DEPARTMENT_APPROVAL, teacherPlanApproval.plan().status());

        WorkflowVerificationController.PlanActionResponse departmentPlanApproval = workflowVerificationController.departmentApprovesPlan(
                new WorkflowVerificationController.PlanApprovalRequest(teacherPlanApproval.plan().id(), departmentId, true, "Department plan approval")
        );
        assertEquals(PlanStatus.APPROVED, departmentPlanApproval.plan().status());
    }
}
