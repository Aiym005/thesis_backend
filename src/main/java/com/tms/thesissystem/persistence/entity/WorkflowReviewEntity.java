package com.tms.thesissystem.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_review")
@Getter
@Setter
public class WorkflowReviewEntity {
    @Id
    private Long id;
    @Column(name = "plan_id", nullable = false)
    private Long planId;
    @Column(name = "week_number", nullable = false)
    private int weekNumber;
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;
    @Column(name = "reviewer_name", nullable = false)
    private String reviewerName;
    @Column(nullable = false)
    private int score;
    private String comment;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
