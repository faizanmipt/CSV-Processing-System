package com.csvprocessor.download.controller;

import com.csvprocessor.download.dto.JobStatus;
import com.csvprocessor.download.dto.ErrorResponse;
import com.csvprocessor.download.service.FileRetrievalService;
import com.csvprocessor.download.service.JobStatusClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Download Controller for processed CSV file retrieval.
 * 
 * This REST controller provides endpoints for downloading processed CSV files.
 * It validates job completion status before allowing file downloads and
 * streams files to clients using reactive programming patterns.
 * 
 * Download Flow:
 * 1. Receive download request with job ID
 * 2. Query job status from centralized store
 * 3. Handle status-specific responses:
 *    - IN_PROGRESS: Return LOCKED (job still processing)
 *    - COMPLETED: Stream file to client
 *    - FAILED: Return BAD_REQUEST (re-upload suggested)
 *    - Unknown: Return BAD_REQUEST with status value
 * 
 * Error Handling:
 * - Job not found in store: Return 404 NOT_FOUND
 * - Invalid job ID format: Return 400 BAD_REQUEST
 * - Status service unavailable: Return 502 BAD_GATEWAY
 * - File not found on disk: Return 500 INTERNAL_SERVER_ERROR
 * - Unexpected errors: Return 500 INTERNAL_SERVER_ERROR
 * 
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@RestController
@RequestMapping("/API")
public class DownloadController {

    /** Logger instance for this controller */
    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    /** Service for file retrieval operations */
    private final FileRetrievalService fileRetrievalService;

    /** Client for job status queries */
    private final JobStatusClient jobStatusClient;

    /**
     * Constructs the DownloadController.
     * 
     * @param fileRetrievalService Service for file retrieval
     * @param jobStatusClient Client for job status queries
     */
    public DownloadController(FileRetrievalService fileRetrievalService, JobStatusClient jobStatusClient) {
        this.fileRetrievalService = fileRetrievalService;
        this.jobStatusClient = jobStatusClient;
    }

    /**
     * Handles download requests for processed CSV files.
     * 
     * @param jobId The job identifier for the processed file
     * @return ResponseEntity containing the file or error message
     */
    @GetMapping("/download/{id}")
    public Mono<ResponseEntity<Object>> download(@PathVariable("id") String jobId) {
        logger.info("Download request received - jobId={}", jobId);

        return jobStatusClient.getStatus(jobId)
                .flatMap(status -> {
                    switch (status) {
                        case IN_PROGRESS:
                            logger.warn("Download rejected - job still processing - jobId={}", jobId);
                            return Mono.just(ResponseEntity.status(HttpStatus.LOCKED)
                                    .body((Object) new ErrorResponse("Job is still processing. Please wait until completion.")));
                        
                        case COMPLETED:
                            logger.info("Retrieving processed file - jobId={}", jobId);
                            return fileRetrievalService.getFile(jobId)
                                    .flatMap(resource -> {
                                        if (resource.exists() && resource.isReadable()) {
                                            logger.info("Streaming file to client - jobId={}", jobId);
                                            return Mono.just(ResponseEntity.ok()
                                                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                                                            "attachment; filename=\"" + jobId + ".csv\"")
                                                    .contentType(MediaType.parseMediaType("text/csv"))
                                                    .body((Object) resource));
                                        } else {
                                            logger.error("Processed file not found - jobId={}", jobId);
                                            return Mono.just(ResponseEntity.internalServerError()
                                                    .body((Object) new ErrorResponse("Processed file not found. Please contact support.")));
                                        }
                                    });
                        
                        case FAILED:
                            logger.warn("Download rejected - job failed - jobId={}", jobId);
                            return Mono.just(ResponseEntity.badRequest()
                                    .body((Object) new ErrorResponse("Job processing failed. Please upload the file again.")));
                        
                        default:
                            logger.warn("Unknown job status - jobId={}, status={}", jobId, status);
                            return Mono.just(ResponseEntity.badRequest()
                                    .body((Object) new ErrorResponse("Unknown job status: " + status)));
                    }
                })
                // Handle job not found (null status from store)
                .switchIfEmpty(Mono.fromCallable(() -> {
                    logger.warn("Job not found in status store - jobId={}", jobId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body((Object) new ErrorResponse("Invalid job ID."));
                }))
                // Handle HTTP error responses from status service
                .onErrorResume(WebClientResponseException.BadRequest.class, ex -> {
                    logger.warn("Bad request for job ID - jobId={}, error={}", jobId, ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body((Object) new ErrorResponse("Invalid job ID format.")));
                })
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    logger.warn("Job status service returned not found - jobId={}", jobId);
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body((Object) new ErrorResponse("Invalid job ID.")));
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Job status service error - jobId={}, statusCode={}", 
                            jobId, ex.getStatusCode());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body((Object) new ErrorResponse("Status service temporarily unavailable. Please try again later.")));
                })
                .onErrorResume(ex -> {
                    logger.error("Download processing failed - jobId={}", jobId, ex);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body((Object) new ErrorResponse("An unexpected error occurred during download.")));
                });
    }
}
