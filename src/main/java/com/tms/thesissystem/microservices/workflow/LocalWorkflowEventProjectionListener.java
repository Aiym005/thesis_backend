package com.tms.thesissystem.microservices.workflow;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.AuditEntry;
import com.tms.thesissystem.domain.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.rabbit.enabled", havingValue = "false", matchIfMissing = true)
public class LocalWorkflowEventProjectionListener {
    private final WorkflowRepository repository;

    public LocalWorkflowEventProjectionListener(WorkflowRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void handle(WorkflowEvent event) {
        repository.saveAuditEntry(new AuditEntry(
                repository.nextAuditId(),
                event.entityType(),
                event.entityId(),
                event.action(),
                event.actorName(),
                event.detail(),
                event.occurredAt()
        ));

        if (event.recipientIds() == null || event.recipientIds().isEmpty()) {
            return;
        }

        for (Long recipientId : event.recipientIds()) {
            if (recipientId == null) {
                continue;
            }
            repository.saveNotification(new Notification(
                    repository.nextNotificationId(),
                    recipientId,
                    event.notificationTitle(),
                    event.notificationMessage(),
                    event.occurredAt()
            ));
        }
    }
}
