package com.tms.thesissystem.microservices.workflow;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.application.port.WorkflowEventPublisher;
import com.tms.thesissystem.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowPublisherConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
    WorkflowEventPublisher rabbitWorkflowEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.rabbit.exchange:" + RabbitMqConfig.WORKFLOW_EXCHANGE + "}") String exchangeName) {
        return event -> rabbitTemplate.convertAndSend(exchangeName, "", event);
    }

    @Bean
    @ConditionalOnProperty(name = "app.messaging.rabbit.enabled", havingValue = "false")
    WorkflowEventPublisher localWorkflowEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return applicationEventPublisher::publishEvent;
    }
}
