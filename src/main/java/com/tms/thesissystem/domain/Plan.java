package com.tms.thesissystem.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Plan {
    public static final int REQUIRED_WEEKS = 15;

    private final Long id;
    private final Long topicId;
    private final String topicTitle;
    private final Long studentId;
    private final String studentName;
    private PlanStatus status;
    private final List<WeeklyTask> tasks;
    private final List<ApprovalRecord> approvals;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Plan(Long id, Long topicId, String topicTitle, Long studentId, String studentName, PlanStatus status,
                List<WeeklyTask> tasks, List<ApprovalRecord> approvals, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.topicId = topicId;
        this.topicTitle = topicTitle;
        this.studentId = studentId;
        this.studentName = studentName;
        this.status = status;
        this.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        this.approvals = approvals == null ? new ArrayList<>() : new ArrayList<>(approvals);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void updateTasks(List<WeeklyTask> updatedTasks, LocalDateTime now) {
        if (status == PlanStatus.PENDING_TEACHER_APPROVAL || status == PlanStatus.PENDING_DEPARTMENT_APPROVAL) {
            throw new IllegalStateException("Баталгаажуулалт явагдаж буй төлөвлөгөөг засах боломжгүй.");
        }
        tasks.clear();
        tasks.addAll(updatedTasks);
        if (status == PlanStatus.REJECTED) {
            approvals.clear();
        }
        status = PlanStatus.DRAFT;
        updatedAt = now;
    }

    public void submit(LocalDateTime now) {
        if (tasks.size() == REQUIRED_WEEKS - 1) {
            tasks.add(new WeeklyTask(
                    REQUIRED_WEEKS,
                    "7 хоног " + REQUIRED_WEEKS + " - milestone",
                    "Deliverable " + REQUIRED_WEEKS,
                    "Тайлан дүгнэлт, эцсийн сайжруулалт"
            ));
        }
        if (tasks.size() != REQUIRED_WEEKS) {
            throw new IllegalStateException("Төлөвлөгөө 15 долоо хоногийн даалгавартай байх ёстой.");
        }
        if (status == PlanStatus.REJECTED) {
            approvals.clear();
        }
        status = PlanStatus.PENDING_TEACHER_APPROVAL;
        updatedAt = now;
    }

    public void teacherDecision(User teacher, boolean approved, String note, LocalDateTime now) {
        if (status != PlanStatus.PENDING_TEACHER_APPROVAL) {
            throw new IllegalStateException("Багшийн баталгаажуулалт хүлээж байгаа төлөвлөгөө биш байна.");
        }
        approvals.add(new ApprovalRecord(ApprovalStage.TEACHER, teacher.id(), teacher.fullName(), approved, note, now));
        status = approved ? PlanStatus.PENDING_DEPARTMENT_APPROVAL : PlanStatus.REJECTED;
        updatedAt = now;
    }

    public void departmentDecision(User departmentUser, boolean approved, String note, LocalDateTime now) {
        if (status != PlanStatus.PENDING_DEPARTMENT_APPROVAL) {
            throw new IllegalStateException("Тэнхимийн баталгаажуулалт хүлээж байгаа төлөвлөгөө биш байна.");
        }
        approvals.add(new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentUser.id(), departmentUser.fullName(), approved, note, now));
        status = approved ? PlanStatus.APPROVED : PlanStatus.REJECTED;
        updatedAt = now;
    }

    public Long id() { return id; }
    public Long topicId() { return topicId; }
    public String topicTitle() { return topicTitle; }
    public Long studentId() { return studentId; }
    public String studentName() { return studentName; }
    public PlanStatus status() { return status; }
    public List<WeeklyTask> tasks() { return List.copyOf(tasks); }
    public List<ApprovalRecord> approvals() { return List.copyOf(approvals); }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
