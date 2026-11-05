package com.example.timetablescheduler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration for SatSched Timetable Solver REST API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI timetableOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SatSched Timetable Scheduler API")
                        .description("Full-School Constraint-Based Timetable Solver REST API using Google OR-Tools CP-SAT")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SatSched Development Team"))
                        .license(new License()
                                .name("Apache 2.0")));
    }
}

