package com.csvprocessor.jobstore.controller;

import com.csvprocessor.jobstore.dto.JobStatus;
import com.csvprocessor.jobstore.store.JobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Job Status REST Controller.
 * 
 * This controller provides REST endpoints for job status management.
 * It exposes the JobStore functionality through HTTP endpoints for
 * consumption by other services in the CSV processing system.
 * 
 * Endpoints:
 * - PUT /status/{id} - Update job status
 * - GET /status/{id} - Get current job status
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@RestController
@RequestMapping("/status")
public class JobStatusController {

    /** Logger instance for this controller */
    private static final Logger logger = LoggerFactory.getLogger(JobStatusController.class);

    /** Job status storage service */
    private final JobStore jobStore;

    /**
     * Constructs the JobStatusController.
     * 
     * @param jobStore The job status storage service
     */
    public JobStatusController(JobStore jobStore) {
        this.jobStore = jobStore;
    }

    /**
     * Updates the status of a job.
     * 
     * @param jobId The unique job identifier
     * @param status The new status to set
     * @return 200 OK on success, 400 Bad Request on invalid input
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStatus(@PathVariable("id") String jobId, @RequestBody JobStatus status) {
        if (jobId == null || jobId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Job ID is required\"}");
        }
        
        logger.info("Updating job status - jobId={}, status={}", jobId, status);
        jobStore.updateStatus(jobId, status);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves the current status of a job.
     * 
     * @param jobId The unique job identifier
     * @return 200 OK with status body, 400 Bad Request for invalid ID, 404 Not Found for unknown job
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getStatus(@PathVariable("id") String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Job ID is required\"}");
        }
        
        JobStatus status = jobStore.getStatus(jobId);
        if (status == null) {
            logger.warn("Job not found - jobId={}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Job not found\"}");
        }
        
        return ResponseEntity.ok(status);
    }
}
