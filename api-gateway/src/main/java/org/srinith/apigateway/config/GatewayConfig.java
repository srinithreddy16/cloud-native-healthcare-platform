package org.srinith.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Patient service REST API
                .route("patient-service-route", r -> r
                        .path("/api/patients/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("http://patient-service:4000"))
                // Patient service OpenAPI docs
                .route("api-docs-patient-route", r -> r
                        .path("/api-docs/patients")
                        .filters(f -> f.rewritePath("/api-docs/patients", "/v3/api-docs"))
                        .uri("http://patient-service:4000"))
                .build();
    }
}

