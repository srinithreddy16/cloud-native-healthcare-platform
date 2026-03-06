package org.srinith.apigateway.config;

import org.srinith.apigateway.filter.JwtValidationGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final JwtValidationGatewayFilterFactory jwtValidationGatewayFilterFactory;

    public GatewayConfig(JwtValidationGatewayFilterFactory jwtValidationGatewayFilterFactory) {
        this.jwtValidationGatewayFilterFactory = jwtValidationGatewayFilterFactory;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth service: /auth/** -> http://auth-service:4005
                .route("auth-service-route", r -> r
                        .path("/auth/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .removeRequestHeader("Content-Length"))
                        .uri("http://auth-service:4005"))
                // Patient service REST API (protected by JWT)
                .route("patient-service-route", r -> r
                        .path("/api/patients/**")
                        .filters(f -> f
                                .filter(jwtValidationGatewayFilterFactory.apply(new Object()))
                                .stripPrefix(1))
                        .uri("http://patient-service:4000"))
                // Patient service OpenAPI docs (public)
                .route("api-docs-patient-route", r -> r
                        .path("/api-docs/patients")
                        .filters(f -> f.rewritePath("/api-docs/patients", "/v3/api-docs"))
                        .uri("http://patient-service:4000"))
                .build();
    }
}

