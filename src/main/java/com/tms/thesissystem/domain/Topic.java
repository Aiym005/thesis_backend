package com.tms.thesissystem.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Getter
public class Topic {
    private final Long id;
    private String title;
    private String description;
    private String program;
    private final Long proposerId;
    private final String proposerName;
    private final UserRole proposerRole;
    private Long ownerStudentId;
    private String ownerStudentName;
    private Long advisorTeacherId;
    private String advisorTeacherName;
    private TopicStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<ApprovalRecord> approvals;

    public static Topic teacherCatalogTopic(Long id, String title, String description, String program, User teacher, LocalDateTime now) {
        return new Topic(id, title, description, program, teacher.id(), teacher.fullName(), teacher.role(),
                null, null, teacher.id(), teacher.fullName(), TopicStatus.PENDING_DEPARTMENT_APPROVAL, now, now, List.of());
    }

    public static Topic studentProposal(Long id, String title, String description, String program, User student, LocalDateTime now) {
        return new Topic(id, title, description, program, student.id(), student.fullName(), student.role(),
                student.id(), student.fullName(), null, null, TopicStatus.PENDING_TEACHER_APPROVAL, now, now, List.of());
    }

    public static Topic departmentCatalogTopic(Long id, String title, String description, String program, User department, LocalDateTime now) {
        return new Topic(id, title, description, program, department.id(), department.fullName(), department.role(),
                null, null, null, null, TopicStatus.AVAILABLE, now, now, List.of());
    }

    public void claim(User student, LocalDateTime now) {
        if (status != TopicStatus.AVAILABLE) {
            throw new IllegalStateException("Сонгох боломжтой сэдэв биш байна.");
        }
        ownerStudentId = student.id();
        ownerStudentName = student.fullName();
        status = TopicStatus.PENDING_TEACHER_APPROVAL;
        updatedAt = now;
    }

    public void teacherDecision(User teacher, boolean approved, String note, LocalDateTime now) {
        if (status != TopicStatus.PENDING_TEACHER_APPROVAL) {
            throw new IllegalStateException("Багшийн баталгаажуулалт хүлээж байгаа сэдэв биш байна.");
        }
        approvals.add(new ApprovalRecord(ApprovalStage.TEACHER, teacher.id(), teacher.fullName(), approved, note, now));
        if (approved) {
            status = TopicStatus.PENDING_DEPARTMENT_APPROVAL;
        } else if (proposerRole == UserRole.TEACHER && ownerStudentId != null) {
            releaseToCatalog();
        } else {
            status = TopicStatus.REJECTED;
        }
        updatedAt = now;
    }

    public void departmentDecision(User departmentUser, boolean approved, Long advisorTeacherId, String advisorTeacherName, String note, LocalDateTime now) {
        if (status != TopicStatus.PENDING_DEPARTMENT_APPROVAL) {
            throw new IllegalStateException("Тэнхимийн баталгаажуулалт хүлээж байгаа сэдэв биш байна.");
        }
        approvals.add(new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentUser.id(), departmentUser.fullName(), approved, note, now));
        if (approved) {
            if (ownerStudentId == null) {
                this.status = TopicStatus.AVAILABLE;
                updatedAt = now;
                return;
            }
            if (advisorTeacherId == null || advisorTeacherName == null || advisorTeacherName.isBlank()) {
                throw new IllegalArgumentException("Удирдагч багшийг заавал томилно.");
            }
            this.advisorTeacherId = advisorTeacherId;
            this.advisorTeacherName = advisorTeacherName;
            this.status = TopicStatus.APPROVED;
        } else {
            if (proposerRole == UserRole.TEACHER && ownerStudentId != null) {
                releaseToCatalog();
            } else {
                this.status = TopicStatus.REJECTED;
            }
        }
        updatedAt = now;
    }

    public void supersede(LocalDateTime now) {
        ownerStudentId = null;
        ownerStudentName = null;
        advisorTeacherId = null;
        advisorTeacherName = null;
        status = TopicStatus.SUPERSEDED;
        updatedAt = now;
    }

    private void releaseToCatalog() {
        ownerStudentId = null;
        ownerStudentName = null;
        status = TopicStatus.AVAILABLE;
    }

    public void revise(String title, String description, String program, LocalDateTime now) {
        this.title = title;
        this.description = description;
        this.program = program;
        if (status == TopicStatus.REJECTED) {
            approvals.clear();
            advisorTeacherId = null;
            advisorTeacherName = null;
            if (ownerStudentId != null) {
                status = TopicStatus.PENDING_TEACHER_APPROVAL;
            } else if (proposerRole == UserRole.TEACHER) {
                status = TopicStatus.PENDING_DEPARTMENT_APPROVAL;
            } else if (proposerRole == UserRole.DEPARTMENT) {
                status = TopicStatus.AVAILABLE;
            }
        }
        this.updatedAt = now;
    }

    public void delete(LocalDateTime now) {
        ownerStudentId = null;
        ownerStudentName = null;
        advisorTeacherId = null;
        advisorTeacherName = null;
        status = TopicStatus.DELETED;
        updatedAt = now;
    }
}
