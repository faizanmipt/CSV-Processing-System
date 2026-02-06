package com.csvprocessor.upload.controller;

import com.csvprocessor.upload.dto.JobEvent;
import com.csvprocessor.upload.dto.JobStatus;
import com.csvprocessor.upload.dto.ErrorResponse;
import com.csvprocessor.upload.dto.UploadResponse;
import com.csvprocessor.upload.service.FileStorageService;
import com.csvprocessor.upload.service.JobStatusClient;
import com.csvprocessor.upload.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * File Upload Controller for handling CSV file uploads.
 * 
 * This REST controller provides endpoints for uploading CSV files to the processing system.
 * It orchestrates the complete upload pipeline including validation, storage, status updates,
 * and Kafka event production.
 * 
 * Upload Pipeline:
 * 1. Receive multipart file upload request
 * 2. Validate file content type
 * 3. Generate unique job identifier
 * 4. Update job status to RECEIVED
 * 5. Store file in raw data directory
 * 6. Update job status to STORED
 * 7. Publish JobEvent to Kafka for worker processing
 * 8. Update job status to QUEUED
 * 9. Return job ID to client
 * 
 * Error Handling:
 * - Invalid content types are rejected with BAD_REQUEST
 * - Storage failures trigger FAILED status update
 * - Kafka production errors are logged and propagated
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@RestController
@RequestMapping("/API/upload")
public class FileUploadController {

    /** Logger instance for this controller */
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    /** Set of allowed content types for CSV uploads */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel"
    );

    /** Service for file storage operations */
    private final FileStorageService fileStorageService;

    /** Client for job status updates */
    private final JobStatusClient jobStatusClient;

    /** Service for Kafka message production */
    private final KafkaProducerService kafkaProducerService;

    /** Directory path for raw file storage */
    private final String rawFileDir;

    /**
     * Constructs the FileUploadController with required dependencies.
     * 
     * @param fileStorageService Service for file storage operations
     * @param jobStatusClient Client for job status updates
     * @param kafkaProducerService Service for Kafka message production
     * @param rawFileDir Directory path for raw file storage
     */
    public FileUploadController(FileStorageService fileStorageService,
                                JobStatusClient jobStatusClient,
                                KafkaProducerService kafkaProducerService,
                                @Value("${file.raw.dir:/data/raw}") String rawFileDir) {
        this.fileStorageService = fileStorageService;
        this.jobStatusClient = jobStatusClient;
        this.kafkaProducerService = kafkaProducerService;
        this.rawFileDir = rawFileDir;
    }

    /**
     * Handles CSV file upload requests.
     * 
     * This endpoint accepts multipart file uploads containing CSV files,
     * processes them through the upload pipeline, and returns a job ID
     * for tracking the processing status.
     * 
     * @param filePart The uploaded file content
     * @return ResponseEntity containing UploadResponse with job ID or ErrorResponse
     */
    @PostMapping(
            value = "",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ResponseEntity<?>> uploadCsv(
            @RequestPart("file") FilePart filePart
    ) {
        String jobId = UUID.randomUUID().toString();
        logger.info("Processing file upload - jobId={}, filename={}", jobId, filePart.filename());

        // Validate content type before processing
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "unknown";

        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            logger.warn("Upload rejected - jobId={}, invalidContentType={}", jobId, contentType);
            return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid content type: " + contentType + ". Only CSV files are accepted.")));
        }

        return Mono.just(jobId)
                // Step 1: Set status to RECEIVED
                .flatMap(id -> {
                    logger.debug("Updating status to RECEIVED - jobId={}", id);
                    return jobStatusClient.updateStatus(id, JobStatus.RECEIVED)
                            .thenReturn(id);
                })
                // Step 2: Save file to storage
                .flatMap(id -> {
                    logger.debug("Storing file - jobId={}", id);
                    return fileStorageService.saveFile(filePart, id)
                            .doOnNext(path -> logger.info("File stored - jobId={}, path={}", id, path))
                            .map(path -> id);
                })
                // Step 3: Set status to STORED
                .flatMap(id -> {
                    logger.debug("Updating status to STORED - jobId={}", id);
                    return jobStatusClient.updateStatus(id, JobStatus.STORED)
                            .thenReturn(id);
                })
                // Step 4: Publish JobEvent to Kafka
                .flatMap(id -> {
                    logger.debug("Publishing JobEvent to Kafka - jobId={}", id);
                    JobEvent event = new JobEvent();
                    event.setJobId(id);
                    event.setFilePath(rawFileDir + "/" + id + ".csv");
                    return kafkaProducerService.sendEvent(event)
                            .thenReturn(id);
                })
                // Step 5: Set status to QUEUED
                .flatMap(id -> {
                    logger.debug("Updating status to QUEUED - jobId={}", id);
                    return jobStatusClient.updateStatus(id, JobStatus.QUEUED)
                            .thenReturn(id);
                })
                // Return success response
                .<ResponseEntity<?>>thenReturn(ResponseEntity
                        .status(HttpStatus.OK)
                        .body(new UploadResponse(jobId)))
                // Handle errors and update status to FAILED
                .onErrorResume(ex -> {
                    logger.error("Upload pipeline failed - jobId={}", jobId, ex);
                    return jobStatusClient.updateStatus(jobId, JobStatus.FAILED)
                            .onErrorResume(e -> {
                                logger.error("Failed to update FAILED status - jobId={}", jobId, e);
                                return Mono.empty();
                            })
                            .thenReturn(ResponseEntity
                                    .status(HttpStatus.BAD_REQUEST)
                                    .body(new ErrorResponse(ex.getMessage())));
                });
    }
}
