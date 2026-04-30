package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.TopicEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicJpaRepository extends JpaRepository<TopicEntity, Long> {
    Optional<TopicEntity> findTopByOrderByIdDesc();
}
