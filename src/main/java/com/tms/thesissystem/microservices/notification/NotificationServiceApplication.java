package com.tms.thesissystem.microservices.notification;

import com.tms.thesissystem.config.RabbitMqConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackageClasses = {
        NotificationServiceApplication.class,
        RabbitMqConfig.class
})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(NotificationServiceApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "application-notification-service"));
        app.run(args);
    }
}
