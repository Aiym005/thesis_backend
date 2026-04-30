package com.tms.thesissystem.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_audit")
@Getter
@Setter
public class WorkflowAuditEntity {
    @Id
    private Long id;
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(nullable = false)
    private String action;
    @Column(name = "actor_name", nullable = false)
    private String actorName;
    @Column(nullable = false)
    private String detail;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
