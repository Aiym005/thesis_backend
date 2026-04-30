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
@Table(name = "plan_response")
@Getter
@Setter
public class PlanResponseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "approver_id", nullable = false)
    private Long approverId;
    @Column(name = "approver_type", nullable = false)
    private String approverType;
    private String note;
    @Column(name = "plan_id", nullable = false)
    private Long planId;
    @Column(name = "res", nullable = false)
    private String response;
    @Column(name = "res_date")
    private LocalDate responseDate;
}
