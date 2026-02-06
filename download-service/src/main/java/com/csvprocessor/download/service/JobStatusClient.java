package com.csvprocessor.download.service;

import com.csvprocessor.download.dto.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Job Status Client for the Download Service.
 * 
 * This service provides HTTP-based communication with the centralized Job Status Store
 * for retrieving job processing statuses.
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
     * Constructs the JobStatusClient.
     * 
     * @param webClientBuilder The WebClient builder
     * @param jobStatusBaseUrl Base URL of the job status service
     */
    public JobStatusClient(WebClient.Builder webClientBuilder,
                          @Value("${job.status.url:http://common-jobstore-service:8084}") String jobStatusBaseUrl) {
        logger.info("Initializing JobStatusClient - baseUrl={}", jobStatusBaseUrl);
        this.webClient = webClientBuilder.baseUrl(jobStatusBaseUrl).build();
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
