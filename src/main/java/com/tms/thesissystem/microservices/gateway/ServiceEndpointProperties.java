package com.tms.thesissystem.microservices.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.services")
public class ServiceEndpointProperties {
    private Endpoint user = new Endpoint();
    private Endpoint workflow = new Endpoint();
    private Endpoint notification = new Endpoint();
    private Endpoint audit = new Endpoint();

    @Getter
    @Setter
    public static class Endpoint {
        private String baseUrl;
    }
}
