package com.tms.thesissystem.microservices.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackageClasses = ApiGatewayApplication.class)
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ApiGatewayApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "application-api-gateway"));
        app.run(args);
    }
}
