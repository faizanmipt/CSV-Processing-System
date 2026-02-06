package com.csvprocessor.api.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API Gateway Route Configuration.
 * 
 * This configuration defines the routing rules for directing incoming requests
 * to the appropriate backend microservices. Each route specifies a path pattern
 * and the target service URI.
 * 
 * Route Definitions:
 * - Upload Service: Handles CSV file uploads
 * - Status Service: Routes directly to common-jobstore-service for job status
 * - Download Service: Handles processed file downloads
 * - Health Endpoints: System health monitoring
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Configuration
public class GatewayConfig {

    /**
     * Configures the gateway routes for the application.
     * 
     * @param builder The RouteLocatorBuilder for constructing routes
     * @return RouteLocator containing all configured routes
     */
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Upload Service - handles CSV file uploads
                .route("upload-service", r -> r.path("/API/upload/**")
                        .uri("http://upload-service:8081"))

                // Status Service - routes directly to common-jobstore-service
                .route("status-service", r -> r.path("/status/**")
                        .uri("http://common-jobstore-service:8084"))

                // Download Service - streams processed CSV files
                .route("download-service", r -> r.path("/API/download/**")
                        .uri("http://download-service:8083"))

                // Health check endpoints
                .route("actuator-health", r -> r.path("/actuator/health/**")
                        .uri("lb://api-gateway/actuator/health"))

                .build();
    }
}
