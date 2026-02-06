package com.csvprocessor.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Worker Service Application Entry Point.
 * 
 * This Spring Boot application serves as the worker service in the CSV processing
 * system. It is responsible for:
 * - Consuming job events from Kafka (csv-jobs topic)
 * - Processing CSV files (email detection and flagging)
 * - Updating job status via the centralized job status store
 * - Handling failures with retry logic and DLQ routing
 * 
 * Application Architecture:
 * - Reactive Kafka Consumer: Project Reactor Kafka for streaming consumption
 * - CSV Processor: Streaming file processor with batch I/O optimization
 * - Job Status Client: HTTP-based communication with job status store
 * - Dead Letter Queue: Kafka-based failure handling mechanism
 * 
 * Dependencies:
 * - Spring Boot WebFlux for reactive web support
 * - Spring Kafka for Kafka integration
 * - Reactor Kafka for reactive Kafka operations
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@SpringBootApplication
public class WorkerApplication {

    /**
     * Main entry point for the Worker Service application.
     * 
     * @param args Command-line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
