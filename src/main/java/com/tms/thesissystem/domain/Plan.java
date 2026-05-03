package com.tms.thesissystem.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Getter
public class Plan {
    public static final int REQUIRED_WEEKS = 15;

    private final Long id;
    private final Long topicId;
    private final String topicTitle;
    private final Long studentId;
    private final String studentName;
    private PlanStatus status;
    private List<WeeklyTask> tasks;
    private List<ApprovalRecord> approvals;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateTasks(List<WeeklyTask> updatedTasks, LocalDateTime now) {
        if (status == PlanStatus.PENDING_TEACHER_APPROVAL || status == PlanStatus.PENDING_DEPARTMENT_APPROVAL) {
            throw new IllegalStateException("Баталгаажуулалт явагдаж буй төлөвлөгөөг засах боломжгүй.");
        }
        tasks = updatedTasks == null ? new ArrayList<>() : new ArrayList<>(updatedTasks);
        if (status == PlanStatus.REJECTED) {
            approvals = new ArrayList<>();
        }
        status = PlanStatus.DRAFT;
        updatedAt = now;
    }

    public void submit(LocalDateTime now) {
        List<WeeklyTask> normalizedTasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        if (normalizedTasks.size() == REQUIRED_WEEKS - 1) {
            normalizedTasks.add(new WeeklyTask(
                    REQUIRED_WEEKS,
                    "7 хоног " + REQUIRED_WEEKS + " - milestone",
                    "Deliverable " + REQUIRED_WEEKS,
                    "Тайлан дүгнэлт, эцсийн сайжруулалт"
            ));
        }
        if (normalizedTasks.size() != REQUIRED_WEEKS) {
            throw new IllegalStateException("Төлөвлөгөө 15 долоо хоногийн даалгавартай байх ёстой.");
        }
        if (status == PlanStatus.REJECTED) {
            approvals = new ArrayList<>();
        }
        tasks = normalizedTasks;
        status = PlanStatus.PENDING_TEACHER_APPROVAL;
        updatedAt = now;
    }

    public void teacherDecision(User teacher, boolean approved, String note, LocalDateTime now) {
        if (status != PlanStatus.PENDING_TEACHER_APPROVAL) {
            throw new IllegalStateException("Багшийн баталгаажуулалт хүлээж байгаа төлөвлөгөө биш байна.");
        }
        List<ApprovalRecord> updatedApprovals = approvals == null ? new ArrayList<>() : new ArrayList<>(approvals);
        updatedApprovals.add(new ApprovalRecord(ApprovalStage.TEACHER, teacher.id(), teacher.fullName(), approved, note, now));
        approvals = updatedApprovals;
        status = approved ? PlanStatus.PENDING_DEPARTMENT_APPROVAL : PlanStatus.REJECTED;
        updatedAt = now;
    }

    public void departmentDecision(User departmentUser, boolean approved, String note, LocalDateTime now) {
        if (status != PlanStatus.PENDING_DEPARTMENT_APPROVAL) {
            throw new IllegalStateException("Тэнхимийн баталгаажуулалт хүлээж байгаа төлөвлөгөө биш байна.");
        }
        List<ApprovalRecord> updatedApprovals = approvals == null ? new ArrayList<>() : new ArrayList<>(approvals);
        updatedApprovals.add(new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentUser.id(), departmentUser.fullName(), approved, note, now));
        approvals = updatedApprovals;
        status = approved ? PlanStatus.APPROVED : PlanStatus.REJECTED;
        updatedAt = now;
    }
}
