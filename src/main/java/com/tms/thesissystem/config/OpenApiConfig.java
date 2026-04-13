package com.tms.thesissystem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI thesisSystemOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Дипломын ажлын системийн API")
                        .description("Нэвтрэлт, сэдэв, төлөвлөгөө, баталгаажуулалтын endpoint-ууд")
                        .version("v1")
                        .contact(new Contact()
                                .name("TMS Дипломын ажлын систем")
                                .email("support@tms.mn")))
                .servers(List.of(new Server().url("http://localhost:8080").description("Локал сервер")));
    }
}
