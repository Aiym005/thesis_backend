package com.tms.thesissystem.microservices.audit;

import com.tms.thesissystem.config.RabbitMqConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackageClasses = {
        AuditServiceApplication.class,
        RabbitMqConfig.class
})
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AuditServiceApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "application-audit-service"));
        app.run(args);
    }
}
