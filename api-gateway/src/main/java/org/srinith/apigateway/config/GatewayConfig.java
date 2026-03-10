package org.srinith.apigateway.config;

import org.srinith.apigateway.filter.JwtValidationGatewayFilterFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final JwtValidationGatewayFilterFactory jwtValidationGatewayFilterFactory;
    private final String authServiceUrl;
    private final String patientServiceUrl;

    public GatewayConfig(
            JwtValidationGatewayFilterFactory jwtValidationGatewayFilterFactory,
            @Value("${auth.service.url:http://host.docker.internal:4005}") String authServiceUrl,
            @Value("${patient.service.url:http://host.docker.internal:4000}") String patientServiceUrl) {
        this.jwtValidationGatewayFilterFactory = jwtValidationGatewayFilterFactory;
        this.authServiceUrl = authServiceUrl;
        this.patientServiceUrl = patientServiceUrl;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth service: /auth/** -> ${auth.service.url}
                .route("auth-service-route", r -> r
                        .path("/auth/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .removeRequestHeader("Content-Length"))
                        .uri(authServiceUrl))
                // Patient service REST API (protected by JWT)
                .route("patient-service-route", r -> r
                        .path("/api/patients/**")
                        .filters(f -> f
                                .filter(jwtValidationGatewayFilterFactory.apply(new Object()))
                                .stripPrefix(1))
                        .uri(patientServiceUrl))
                // Patient service OpenAPI docs (public)
                .route("api-docs-patient-route", r -> r
                        .path("/api-docs/patients")
                        .filters(f -> f.rewritePath("/api-docs/patients", "/v3/api-docs"))
                        .uri(patientServiceUrl))
                .build();
    }
}

