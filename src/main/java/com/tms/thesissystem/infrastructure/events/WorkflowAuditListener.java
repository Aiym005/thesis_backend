package com.tms.thesissystem.infrastructure.events;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.model.AuditEntry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAuditListener {
    private final WorkflowRepository repository;

    public WorkflowAuditListener(WorkflowRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void onWorkflowEvent(WorkflowEvent event) {
        repository.saveAuditEntry(new AuditEntry(repository.nextAuditId(), event.entityType(), event.entityId(),
                event.action(), event.actorName(), event.detail(), event.occurredAt()));
    }
}
