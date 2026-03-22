package com.tms.thesissystem;

import com.tms.thesissystem.api.ApiDtos;
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
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestWorkflowRepositoryConfig.class)
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
        ApiDtos.DashboardResponse snapshot = workflowController.dashboard();
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
        ApiDtos.DashboardResponse snapshot = workflowController.proposeTopic(
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
        ApiDtos.DashboardResponse state = workflowVerificationController.state();
        assertNotNull(state);
        assertFalse(state.users().isEmpty());
        assertFalse(state.topics().isEmpty());
        assertFalse(state.plans().isEmpty());
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

        ApiDtos.DashboardResponse afterClaim = workflowController.claimTopic(
                new WorkflowController.TopicClaimRequest(availableTopicId, studentId)
        );
        assertNotNull(afterClaim);

        ApiDtos.DashboardResponse afterTeacherTopicApproval = workflowController.teacherTopicDecision(
                new WorkflowController.DecisionRequest(availableTopicId, teacherId, true, "OK")
        );
        assertNotNull(afterTeacherTopicApproval);

        ApiDtos.DashboardResponse afterDepartmentTopicApproval = workflowController.departmentTopicDecision(
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
        ApiDtos.DashboardResponse afterPlanSave = workflowController.savePlan(planSaveRequest);
        assertNotNull(afterPlanSave);

        Long savedPlanId = afterPlanSave.plans().stream()
                .filter(plan -> plan.studentId().equals(studentId))
                .findFirst()
                .orElseThrow()
                .id();

        ApiDtos.DashboardResponse afterPlanSubmit = workflowController.submitPlan(
                new WorkflowController.PlanSubmitRequest(savedPlanId, studentId)
        );
        assertNotNull(afterPlanSubmit);

        ApiDtos.DashboardResponse afterTeacherPlanApproval = workflowController.teacherPlanDecision(
                new WorkflowController.DecisionRequest(savedPlanId, teacherId, true, "Plan OK")
        );
        assertNotNull(afterTeacherPlanApproval);

        ApiDtos.DashboardResponse afterDepartmentPlanApproval = workflowController.departmentPlanDecision(
                new WorkflowController.DecisionRequest(savedPlanId, departmentId, true, "Final OK")
        );
        assertNotNull(afterDepartmentPlanApproval);

        assertFalse(afterDepartmentPlanApproval.plans().stream()
                .filter(plan -> plan.id().equals(savedPlanId))
                .findFirst()
                .orElseThrow()
                .status()
                .isBlank());
    }

    @Test
    void verificationControllerRunsFullTeacherStudentDepartmentWorkflow() {
        ApiDtos.DashboardResponse initial = workflowVerificationController.state();
        Long studentId = initial.users().stream().filter(user -> "STUDENT".equals(user.role())).findFirst().orElseThrow().id();
        Long teacherId = initial.users().stream().filter(user -> "TEACHER".equals(user.role())).findFirst().orElseThrow().id();
        Long advisorTeacherId = initial.users().stream().filter(user -> "TEACHER".equals(user.role())).skip(1).findFirst().orElseThrow().id();
        Long departmentId = initial.users().stream().filter(user -> "DEPARTMENT".equals(user.role())).findFirst().orElseThrow().id();

        ApiDtos.DashboardResponse teacherTopic = workflowVerificationController.teacherProposesTopic(
                new WorkflowVerificationController.TeacherTopicProposalRequest(
                        teacherId,
                        "Department Approved Catalog Topic",
                        "Teacher proposes a topic that department adds to approved catalog.",
                        "B.SE"
                )
        );
        Long teacherTopicId = teacherTopic.topics().stream()
                .filter(topic -> "Department Approved Catalog Topic".equals(topic.title()))
                .findFirst()
                .orElseThrow()
                .id();
        assertEquals(TopicStatus.PENDING_DEPARTMENT_APPROVAL.name(), teacherTopic.topics().stream()
                .filter(topic -> topic.id().equals(teacherTopicId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse departmentCatalogApproval = workflowVerificationController.departmentApprovesTopic(
                new WorkflowVerificationController.TopicDepartmentApprovalRequest(
                        teacherTopicId,
                        departmentId,
                        true,
                        null,
                        "Catalog approved"
                )
        );
        assertEquals(TopicStatus.AVAILABLE.name(), departmentCatalogApproval.topics().stream()
                .filter(topic -> topic.id().equals(teacherTopicId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse studentSelection = workflowVerificationController.studentSelectsApprovedTopic(
                new WorkflowVerificationController.TopicSelectionRequest(teacherTopicId, studentId)
        );
        assertEquals(TopicStatus.PENDING_TEACHER_APPROVAL.name(), studentSelection.topics().stream()
                .filter(topic -> topic.id().equals(teacherTopicId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse teacherApproval = workflowVerificationController.teacherApprovesStudentTopic(
                new WorkflowVerificationController.TopicTeacherApprovalRequest(
                        teacherTopicId,
                        null,
                        teacherId,
                        null,
                        "Department Approved Catalog Topic",
                        true,
                        "Teacher approved student selection"
                )
        );
        assertEquals(TopicStatus.PENDING_DEPARTMENT_APPROVAL.name(), teacherApproval.topics().stream()
                .filter(topic -> topic.id().equals(teacherTopicId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse departmentFinalApproval = workflowVerificationController.departmentApprovesTopic(
                new WorkflowVerificationController.TopicDepartmentApprovalRequest(
                        teacherTopicId,
                        departmentId,
                        true,
                        advisorTeacherId,
                        "Department assigned advisor"
                )
        );
        assertEquals(TopicStatus.APPROVED.name(), departmentFinalApproval.topics().stream()
                .filter(topic -> topic.id().equals(teacherTopicId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse savedPlan = workflowVerificationController.studentCreatesPlan(
                new WorkflowVerificationController.StudentPlanRequest(
                        studentId,
                        teacherTopicId,
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
        Long savedPlanId = savedPlan.plans().stream()
                .filter(plan -> plan.studentId().equals(studentId) && plan.topicId().equals(teacherTopicId))
                .findFirst()
                .orElseThrow()
                .id();
        assertEquals(PlanStatus.DRAFT.name(), savedPlan.plans().stream()
                .filter(plan -> plan.id().equals(savedPlanId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse submittedPlan = workflowVerificationController.studentSubmitsPlan(
                new WorkflowVerificationController.PlanSubmitRequest(savedPlanId, studentId)
        );
        assertEquals(PlanStatus.PENDING_TEACHER_APPROVAL.name(), submittedPlan.plans().stream()
                .filter(plan -> plan.id().equals(savedPlanId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse teacherPlanApproval = workflowVerificationController.teacherApprovesPlan(
                new WorkflowVerificationController.PlanApprovalRequest(savedPlanId, teacherId, true, "Teacher plan approval")
        );
        assertEquals(PlanStatus.PENDING_DEPARTMENT_APPROVAL.name(), teacherPlanApproval.plans().stream()
                .filter(plan -> plan.id().equals(savedPlanId))
                .findFirst()
                .orElseThrow()
                .status());

        ApiDtos.DashboardResponse departmentPlanApproval = workflowVerificationController.departmentApprovesPlan(
                new WorkflowVerificationController.PlanApprovalRequest(savedPlanId, departmentId, true, "Department plan approval")
        );
        assertEquals(PlanStatus.APPROVED.name(), departmentPlanApproval.plans().stream()
                .filter(plan -> plan.id().equals(savedPlanId))
                .findFirst()
                .orElseThrow()
                .status());
    }
}
