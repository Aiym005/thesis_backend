package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.WorkflowReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowReviewJpaRepository extends JpaRepository<WorkflowReviewEntity, Long> {
    Optional<WorkflowReviewEntity> findTopByOrderByIdDesc();
}
