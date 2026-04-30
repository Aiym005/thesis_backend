package com.tms.thesissystem;

import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.AuthService;
import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import com.tms.thesissystem.application.service.security.UserServiceSecurityConfig;
import com.tms.thesissystem.microservices.workflow.WorkflowCompatibilityController;
import com.tms.thesissystem.service.user.api.UserServiceController;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        excludeName = {
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
        },
        scanBasePackageClasses = {
        UserServiceController.class,
        WorkflowCompatibilityController.class,
        AuthService.class,
        WorkflowCommandService.class,
        WorkflowQueryService.class,
        UserServiceSecurityConfig.class,
        ApiResponseMapper.class
})
public class TestApplication {
}
