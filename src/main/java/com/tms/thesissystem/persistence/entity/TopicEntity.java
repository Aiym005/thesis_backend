package com.tms.thesissystem.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDate;

@Entity
@Table(name = "topic")
@Getter
@Setter
public class TopicEntity {
    @Id
    private Long id;
    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;
    @Column(name = "created_by_id", nullable = false)
    private Long createdById;
    @Column(name = "created_by_type", nullable = false)
    private String createdByType;
    @Column(columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String fields;
    @Column(name = "form_id")
    private Long formId;
    private String program;
    @Column(nullable = false)
    private String status;
}
