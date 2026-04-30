package com.tms.thesissystem.microservices.workflow;

import com.tms.thesissystem.application.port.WorkflowEventPublisher;
import com.tms.thesissystem.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects which {@link WorkflowEventPublisher} is wired in based on
 * {@code app.messaging.rabbit.enabled}.
 *
 * <ul>
 *     <li>{@code true}  — publish via the shared RabbitMQ fanout exchange (production wiring).</li>
 *     <li>{@code false} or unset — publish in-process via Spring's {@link ApplicationEventPublisher}
 *         (default, used by tests and single-process boot).</li>
 * </ul>
 */
@Configuration
public class WorkflowPublisherConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.messaging.rabbit.enabled", havingValue = "true")
    WorkflowEventPublisher rabbitWorkflowEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.rabbit.exchange:" + RabbitMqConfig.WORKFLOW_EXCHANGE + "}") String exchangeName) {
        return event -> rabbitTemplate.convertAndSend(exchangeName, "", event);
    }

    @Bean
    @ConditionalOnProperty(name = "app.messaging.rabbit.enabled", havingValue = "false", matchIfMissing = true)
    WorkflowEventPublisher localWorkflowEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return applicationEventPublisher::publishEvent;
    }
}
