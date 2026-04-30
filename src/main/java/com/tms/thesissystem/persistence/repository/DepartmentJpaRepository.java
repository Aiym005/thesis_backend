package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, Long> {
    Optional<DepartmentEntity> findFirstByOrderByIdAsc();
    Optional<DepartmentEntity> findByName(String name);
    Optional<DepartmentEntity> findTopByOrderByIdDesc();
}
