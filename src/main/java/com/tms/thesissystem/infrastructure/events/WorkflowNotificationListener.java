package com.tms.thesissystem.infrastructure.events;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.model.Notification;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowNotificationListener {
    private final WorkflowRepository repository;

    public WorkflowNotificationListener(WorkflowRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void onWorkflowEvent(WorkflowEvent event) {
        for (Long recipientId : event.recipientIds()) {
            repository.saveNotification(new Notification(repository.nextNotificationId(), recipientId,
                    event.notificationTitle(), event.notificationMessage(), event.occurredAt()));
        }
    }
}
