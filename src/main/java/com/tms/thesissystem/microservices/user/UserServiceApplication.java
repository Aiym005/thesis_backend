package com.tms.thesissystem.microservices.user;

import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.AuthService;
import com.tms.thesissystem.application.service.DatabaseStatusService;
import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.infrastructure.repository.PostgresWorkflowRepository;
import com.tms.thesissystem.service.user.api.UserServiceController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackageClasses = {
        UserServiceController.class,
        AuthService.class,
        WorkflowQueryService.class,
        PostgresWorkflowRepository.class
}, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
                WorkflowCommandService.class,
                DatabaseStatusService.class
        }
))
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(UserServiceApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "application-user-service"));
        app.run(args);
    }

    @Bean
    ApiResponseMapper apiResponseMapper() {
        return new ApiResponseMapper();
    }
}
