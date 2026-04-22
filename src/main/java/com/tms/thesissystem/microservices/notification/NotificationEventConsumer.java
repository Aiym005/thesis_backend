package com.tms.thesissystem.microservices.notification;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {
    private final NotificationEventStore store;

    public NotificationEventConsumer(NotificationEventStore store) {
        this.store = store;
    }

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void consume(WorkflowEvent event) {
        for (Long recipientId : event.recipientIds()) {
            store.append(recipientId, event.notificationTitle(), event.notificationMessage(), event.occurredAt());
        }
    }
}
