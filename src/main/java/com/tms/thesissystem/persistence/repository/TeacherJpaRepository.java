package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherJpaRepository extends JpaRepository<TeacherEntity, Long> {
}
