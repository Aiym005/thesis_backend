package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.PlanResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanResponseJpaRepository extends JpaRepository<PlanResponseEntity, Long> {
    List<PlanResponseEntity> findByPlanIdOrderByIdAsc(Long planId);
    Optional<PlanResponseEntity> findFirstByPlanIdAndApproverType(Long planId, String approverType);
}
