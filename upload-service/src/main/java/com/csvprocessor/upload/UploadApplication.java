package com.csvprocessor.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Upload Service Application Entry Point.
 * 
 * This Spring Boot application serves as the file upload service in the CSV processing
 * system. It is responsible for:
 * - Receiving CSV file uploads from clients
 * - Validating uploaded files (content type, size, format)
 * - Storing uploaded files in the raw data directory
 * - Creating job entries in the centralized job status store
 * - Publishing job events to Kafka for worker processing
 * 
 * Processing Pipeline:
 * 1. Receive file upload via REST API
 * 2. Validate file content type and format
 * 3. Generate unique job ID
 * 4. Store file in raw directory
 * 5. Update job status to STORED
 * 6. Publish JobEvent to Kafka topic
 * 7. Update job status to QUEUED
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@SpringBootApplication
public class UploadApplication {
    public static void main(String[] args) {
        SpringApplication.run(UploadApplication.class, args);
    }
}
