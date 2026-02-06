package com.csvprocessor.jobstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Job Status Store Service Application Entry Point.
 * 
 * This Spring Boot application serves as the centralized job status store
 * for the CSV processing system. It maintains the processing state of all
 * jobs across the distributed system.
 * 
 * Responsibilities:
 * - Storing job status updates from upload, worker, and download services
 * - Providing job status lookup for downstream services
 * - Acting as the single source of truth for job processing state
 * 
 * Job Status Lifecycle:
 * RECEIVED -> STORED -> QUEUED -> IN_PROGRESS -> COMPLETED/FAILED
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@SpringBootApplication
public class JobStatusApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobStatusApplication.class, args);
    }
}
