package com.tms.thesissystem.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "topic_request")
@Getter
@Setter
public class TopicRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "is_selected")
    private boolean selected;
    @Column(name = "req_note")
    private String requestNote;
    @Column(name = "req_text")
    private String requestText;
    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;
    @Column(name = "requested_by_type", nullable = false)
    private String requestedByType;
    @Column(name = "selected_at")
    private LocalDate selectedAt;
    @Column(name = "topic_id", nullable = false)
    private Long topicId;
}
