package com.tms.thesissystem.microservices.audit;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {
    private final AuditEventStore store;

    @RabbitListener(queues = RabbitMqConfig.AUDIT_QUEUE)
    public void consume(WorkflowEvent event) {
        log.debug("Recording audit entry for action {} on {} #{}", event.action(), event.entityType(), event.entityId());
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
