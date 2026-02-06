package com.csvprocessor.download;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Download Service Application Entry Point.
 * 
 * This Spring Boot application serves as the file download service in the CSV processing
 * system. It is responsible for:
 * - Providing REST endpoints for downloading processed CSV files
 * - Validating job completion status before allowing downloads
 * - Streaming processed files to clients
 * 
 * Download Workflow:
 * 1. Receive download request with job ID
 * 2. Query job status from centralized store
 * 3. If IN_PROGRESS: Return LOCKED status
 * 4. If COMPLETED: Stream file to client
 * 5. If FAILED: Return error response
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@SpringBootApplication
public class DownloadApplication {
    public static void main(String[] args) {
        SpringApplication.run(DownloadApplication.class, args);
    }
}
