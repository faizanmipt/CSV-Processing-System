package com.csvprocessor.worker.service;

import com.csvprocessor.worker.dto.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Job Status Client Service for communicating with the centralized Job Status Store.
 * 
 * This service provides non-blocking HTTP communication with the common-jobstore-service
 * for updating and retrieving job processing statuses. It is used by the worker service
 * to report job state changes during CSV processing operations.
 * 
 * Features:
 * - Fully reactive WebClient-based HTTP communication
 * - Input validation for job ID and status parameters
 * - Comprehensive logging for observability
 * - Graceful error handling with logged diagnostics
 * 
 * Communication Pattern:
 * - PUT /status/{id} - Update job status
 * - GET /status/{id} - Retrieve current job status
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class JobStatusClient {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(JobStatusClient.class);

    /** WebClient instance for non-blocking HTTP communication */
    private final WebClient webClient;

    /**
     * Constructs the JobStatusClient with the specified base URL.
     * 
     * @param webClientBuilder The WebClient builder for creating WebClient instances
     * @param jobStatusBaseUrl The base URL of the job status service
     */
    public JobStatusClient(WebClient.Builder webClientBuilder,
                          @Value("${job.status.url:http://common-jobstore-service:8084}") String jobStatusBaseUrl) {
        logger.info("Initializing JobStatusClient - baseUrl={}", jobStatusBaseUrl);
        this.webClient = webClientBuilder.baseUrl(jobStatusBaseUrl).build();
    }

    /**
     * Updates the status of a job in the centralized job status store.
     * 
     * This method sends an HTTP PUT request to update the job status.
     * It performs input validation before making the remote call.
     * 
     * @param jobId The unique identifier of the job
     * @param status The new status to set for the job
     * @return Mono<Void> that completes when the status update is acknowledged
     * @throws IllegalArgumentException if jobId is null/empty or status is null
     */
    public Mono<Void> updateStatus(String jobId, JobStatus status) {
        if (jobId == null || jobId.isBlank()) {
            return Mono.error(new IllegalArgumentException("Job ID cannot be null or empty"));
        }
        if (status == null) {
            return Mono.error(new IllegalArgumentException("Status cannot be null"));
        }

        logger.debug("Initiating status update - jobId={}, status={}", jobId, status);

        return webClient.put()
                .uri("/status/{id}", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(status)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> logger.info("Status update completed - jobId={}, status={}", jobId, status))
                .doOnError(error -> logger.error("Status update failed - jobId={}, error={}", 
                        jobId, error.getMessage()));
    }

    /**
     * Retrieves the current status of a job from the centralized job status store.
     * 
     * This method sends an HTTP GET request to retrieve the current job status.
     * 
     * @param jobId The unique identifier of the job
     * @return Mono containing the current JobStatus
     * @throws IllegalArgumentException if jobId is null or empty
     */
    public Mono<JobStatus> getStatus(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Mono.error(new IllegalArgumentException("Job ID cannot be null or empty"));
        }

        logger.debug("Fetching job status - jobId={}", jobId);

        return webClient.get()
                .uri("/status/{id}", jobId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JobStatus.class)
                .doOnSuccess(s -> logger.debug("Status retrieved - jobId={}, status={}", jobId, s))
                .doOnError(error -> logger.error("Status retrieval failed - jobId={}, error={}", 
                        jobId, error.getMessage()));
    }
}
