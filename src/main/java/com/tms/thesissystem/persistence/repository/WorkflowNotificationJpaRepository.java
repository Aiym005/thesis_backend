package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.WorkflowNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowNotificationJpaRepository extends JpaRepository<WorkflowNotificationEntity, Long> {
    Optional<WorkflowNotificationEntity> findTopByOrderByIdDesc();
}
