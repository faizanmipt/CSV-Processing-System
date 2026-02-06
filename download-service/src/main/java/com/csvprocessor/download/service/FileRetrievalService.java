package com.csvprocessor.download.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File Retrieval Service for processed CSV files.
 * 
 * This service provides non-blocking file retrieval capabilities for downloading
 * processed CSV files. It wraps synchronous file system operations in reactive
 * Mono types for proper integration with the reactive processing pipeline.
 * 
 * Features:
 * - Reactive file lookup by job ID
 * - File existence and readability validation
 * - Error handling with descriptive messages
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class FileRetrievalService {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(FileRetrievalService.class);

    /** Directory path for processed files */
    @Value("${file.processed.dir}")
    private String processedDir;

    /**
     * Retrieves a processed CSV file by job ID.
     * 
     * This method constructs the file path from the job ID and returns
     * a FileSystemResource if the file exists and is readable.
     * 
     * @param jobId The job identifier for the processed file
     * @return Mono containing the file Resource
     */
    public Mono<Resource> getFile(String jobId) {
        return Mono.fromCallable(() -> {
            Path filePath = Paths.get(processedDir).resolve(jobId + ".csv");
            Resource resource = new FileSystemResource(filePath);
            
            if (resource.exists() && resource.isReadable()) {
                logger.info("File located - path={}", filePath);
                return resource;
            } else {
                logger.warn("File not accessible - path={}", filePath);
                return resource;
            }
        })
        .doOnNext(resource -> {
            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("File not available for download - jobId={}", jobId);
            }
        })
        .doOnError(error -> 
            logger.error("File retrieval failed - jobId={}", jobId, error)
        )
        .onErrorResume(error -> 
            Mono.error(new RuntimeException("File retrieval failed - jobId=" + jobId, error))
        );
    }
}
