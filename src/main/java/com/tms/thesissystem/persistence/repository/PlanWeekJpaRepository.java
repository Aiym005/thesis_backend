package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.PlanWeekEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanWeekJpaRepository extends JpaRepository<PlanWeekEntity, Long> {
    List<PlanWeekEntity> findByPlanIdOrderByWeekNumberAsc(Long planId);
    void deleteByPlanId(Long planId);
}
