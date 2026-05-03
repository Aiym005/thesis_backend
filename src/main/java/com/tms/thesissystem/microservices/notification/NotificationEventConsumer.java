package com.tms.thesissystem.microservices.notification;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {
    private final NotificationEventStore store;

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void consume(WorkflowEvent event) {
        if (event.recipientIds() == null || event.recipientIds().isEmpty()) {
            log.debug("Skipping notification event without recipients: {}", event.action());
            return;
        }
        for (Long recipientId : event.recipientIds()) {
            if (recipientId == null) {
                continue;
            }
            store.append(recipientId, event.notificationTitle(), event.notificationMessage(), event.occurredAt());
        }
    }
}
