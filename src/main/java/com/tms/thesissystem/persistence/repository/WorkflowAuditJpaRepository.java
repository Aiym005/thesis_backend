package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.WorkflowAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowAuditJpaRepository extends JpaRepository<WorkflowAuditEntity, Long> {
    Optional<WorkflowAuditEntity> findTopByOrderByIdDesc();
}
