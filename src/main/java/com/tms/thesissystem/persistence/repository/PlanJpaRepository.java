package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanJpaRepository extends JpaRepository<PlanEntity, Long> {
    Optional<PlanEntity> findFirstByStudentIdOrderByIdDesc(Long studentId);
    Optional<PlanEntity> findTopByOrderByIdDesc();
}
