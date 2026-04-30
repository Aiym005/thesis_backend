package com.tms.thesissystem.microservices.workflow;

import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.DatabaseStatusService;
import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.config.PersistenceConfiguration;
import com.tms.thesissystem.config.RabbitMqConfig;
import com.tms.thesissystem.infrastructure.repository.PostgresWorkflowRepository;
import com.tms.thesissystem.service.plan.api.PlanServiceController;
import com.tms.thesissystem.service.review.api.ReviewServiceController;
import com.tms.thesissystem.service.topic.api.TopicServiceController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(
        basePackageClasses = {
                TopicServiceController.class,
                PlanServiceController.class,
                ReviewServiceController.class,
                WorkflowCommandService.class,
                WorkflowQueryService.class,
                PersistenceConfiguration.class,
                PostgresWorkflowRepository.class,
                RabbitMqConfig.class,
                WorkflowPublisherConfiguration.class,
                ApiResponseMapper.class,
                DatabaseStatusService.class,
                WorkflowCompatibilityController.class
        }
)
public class WorkflowServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WorkflowServiceApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "application-workflow-service"));
        app.run(args);
    }
}
