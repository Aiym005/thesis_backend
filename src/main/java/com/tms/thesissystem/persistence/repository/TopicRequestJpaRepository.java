package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.TopicRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicRequestJpaRepository extends JpaRepository<TopicRequestEntity, Long> {
    Optional<TopicRequestEntity> findFirstByTopicIdOrderByIdDesc(Long topicId);
}
