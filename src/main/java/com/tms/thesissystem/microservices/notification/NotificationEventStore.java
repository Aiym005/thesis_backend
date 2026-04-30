package com.tms.thesissystem.microservices.notification;

import com.tms.thesissystem.persistence.entity.WorkflowNotificationEntity;
import com.tms.thesissystem.persistence.repository.WorkflowNotificationJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class NotificationEventStore {
    private final WorkflowNotificationJpaRepository repository;
    private final AtomicLong sequence = new AtomicLong(1);

    @PostConstruct
    void initialize() {
        syncSequence();
    }

    public synchronized void append(Long userId, String title, String message, LocalDateTime createdAt) {
        WorkflowNotificationEntity entity = new WorkflowNotificationEntity();
        entity.setId(sequence.incrementAndGet());
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setCreatedAt(createdAt);
        repository.save(entity);
    }

    public synchronized List<NotificationView> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))).stream()
                .map(entity -> new NotificationView(
                        entity.getId(),
                        entity.getUserId(),
                        entity.getTitle(),
                        entity.getMessage(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    private void syncSequence() {
        sequence.set(repository.findTopByOrderByIdDesc().map(WorkflowNotificationEntity::getId).orElse(1L));
    }

    public record NotificationView(Long id, Long userId, String title, String message, LocalDateTime createdAt) {
    }
}
