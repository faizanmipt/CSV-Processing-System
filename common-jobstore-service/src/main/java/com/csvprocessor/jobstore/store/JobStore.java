package com.csvprocessor.jobstore.store;

import com.csvprocessor.jobstore.dto.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Job Status Store.
 * 
 * This service provides thread-safe storage for job statuses using a concurrent
 * hash map. It serves as the central repository for job processing state
 * across the distributed CSV processing system.
 * 
 * Features:
 * - Thread-safe status updates using ConcurrentHashMap
 * - O(1) lookup performance for status queries
 * - Simple in-memory storage (single instance)
 * 
 * Note: For production deployments, consider integrating with a
 * persistent data store like Redis or a relational database.
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class JobStore {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(JobStore.class);

    /** Thread-safe storage for job status mappings */
    private final ConcurrentHashMap<String, JobStatus> jobStatusMap = new ConcurrentHashMap<>();

    /**
     * Updates the status of a job.
     * 
     * @param jobId The unique job identifier
     * @param status The new status to set
     */
    public void updateStatus(String jobId, JobStatus status) {
        logger.debug("Updating job status - jobId={}, status={}", jobId, status);
        jobStatusMap.put(jobId, status);
    }

    /**
     * Retrieves the current status of a job.
     * 
     * @param jobId The unique job identifier
     * @return The current JobStatus or null if not found
     */
    public JobStatus getStatus(String jobId) {
        return jobStatusMap.get(jobId);
    }
}
