package com.tms.thesissystem.microservices.audit;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {
    private final AuditEventStore store;

    public AuditEventConsumer(AuditEventStore store) {
        this.store = store;
    }

    @RabbitListener(queues = RabbitMqConfig.AUDIT_QUEUE)
    public void consume(WorkflowEvent event) {
        store.append(
                event.entityType(),
                event.entityId(),
                event.action(),
                event.actorName(),
                event.detail(),
                event.occurredAt()
        );
    }
}
