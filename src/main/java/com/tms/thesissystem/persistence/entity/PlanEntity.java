package com.tms.thesissystem.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "plan")
@Getter
@Setter
public class PlanEntity {
    @Id
    private Long id;
    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;
    @Column(nullable = false)
    private String status;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "topic_id", nullable = false)
    private Long topicId;
}
