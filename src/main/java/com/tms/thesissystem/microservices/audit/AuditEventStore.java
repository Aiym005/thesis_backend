package com.tms.thesissystem.microservices.audit;

import com.tms.thesissystem.persistence.entity.WorkflowAuditEntity;
import com.tms.thesissystem.persistence.repository.WorkflowAuditJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Transactional
@RequiredArgsConstructor
public class AuditEventStore {
    private final WorkflowAuditJpaRepository repository;
    private final AtomicLong sequence = new AtomicLong(1);

    @PostConstruct
    void initialize() {
        sequence.set(repository.findTopByOrderByIdDesc().map(WorkflowAuditEntity::getId).orElse(1L));
    }

    public void append(String entityType, Long entityId, String action, String actorName, String detail, LocalDateTime createdAt) {
        WorkflowAuditEntity entity = new WorkflowAuditEntity();
        entity.setId(sequence.incrementAndGet());
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setAction(action);
        entity.setActorName(actorName);
        entity.setDetail(detail);
        entity.setCreatedAt(createdAt);
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AuditView> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))).stream()
                .map(entity -> new AuditView(
                        entity.getId(),
                        entity.getEntityType(),
                        entity.getEntityId(),
                        entity.getAction(),
                        entity.getActorName(),
                        entity.getDetail(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    public record AuditView(Long id, String entityType, Long entityId, String action, String actorName, String detail, LocalDateTime createdAt) {
    }
}
