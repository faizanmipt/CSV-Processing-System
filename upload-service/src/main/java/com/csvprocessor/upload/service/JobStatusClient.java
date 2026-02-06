package com.csvprocessor.upload.service;

import com.csvprocessor.upload.dto.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Job Status Client for the Upload Service.
 * 
 * This service provides HTTP-based communication with the centralized Job Status Store
 * for updating and retrieving job processing statuses.
 * 
 * Features:
 * - Non-blocking WebClient for reactive HTTP communication
 * - Input validation for job ID and status parameters
 * - Comprehensive logging for observability
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class JobStatusClient {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(JobStatusClient.class);

    /** WebClient instance for HTTP communication */
    private final WebClient webClient;

    /**
     * Constructs the JobStatusClient with the specified base URL.
     * 
     * @param webClientBuilder The WebClient builder
     * @param jobStatusBaseUrl The base URL of the job status service
     */
    public JobStatusClient(WebClient.Builder webClientBuilder,
                          @Value("${job.status.url:http://common-jobstore-service:8084}") String jobStatusBaseUrl) {
        logger.info("Initializing JobStatusClient - baseUrl={}", jobStatusBaseUrl);
        this.webClient = webClientBuilder.baseUrl(jobStatusBaseUrl).build();
    }

    /**
     * Updates the status of a job in the centralized store.
     * 
     * @param jobId The unique job identifier
     * @param status The new status to set
     * @return Mono<Void> completing when the update is acknowledged
     */
    public Mono<Void> updateStatus(String jobId, JobStatus status) {
        if (jobId == null || jobId.isBlank()) {
            return Mono.error(new IllegalArgumentException("Job ID cannot be null or empty"));
        }
        if (status == null) {
            return Mono.error(new IllegalArgumentException("Status cannot be null"));
        }

        logger.debug("Updating job status - jobId={}, status={}", jobId, status);

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
     * Retrieves the current status of a job.
     * 
     * @param jobId The unique job identifier
     * @return Mono containing the current JobStatus
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
