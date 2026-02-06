package com.csvprocessor.api.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application Entry Point.
 * 
 * This Spring Cloud Gateway application serves as the single entry point for all
 * client requests in the CSV processing system. It handles request routing,
 * load balancing, and provides a unified API surface for external clients.
 * 
 * Routing Configuration:
 * - /api/upload/** -> upload-service (file upload endpoints)
 * - /api/download/** -> download-service (file download endpoints)
 * - /status/** -> common-jobstore-service (job status endpoints)
 * 
 * Features:
 * - Request routing to backend microservices
 * - Load balancing across service instances
 * - Centralized entry point for monitoring
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
