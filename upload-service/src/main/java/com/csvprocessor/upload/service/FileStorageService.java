package com.csvprocessor.upload.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * File Storage Service for handling file persistence operations.
 * 
 * This service provides reactive file storage capabilities for uploaded CSV files.
 * It implements non-blocking file writes using Spring's reactive DataBuffer utilities
 * with proper backpressure handling.
 * 
 * Features:
 * - Reactive file streaming with backpressure support
 * - Asynchronous directory creation
 * - Efficient memory usage through streaming
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class FileStorageService {

    /** Directory path for storing uploaded files */
    @Value("${file.upload.dir}")
    private String uploadDir;

    /**
     * Saves an uploaded file to the storage directory.
     * 
     * This method performs:
     * 1. Directory creation (if not exists) on boundedElastic scheduler
     * 2. Streaming file content with backpressure using DataBufferUtils
     * 
     * @param filePart The uploaded file content from the HTTP request
     * @param jobId The job identifier used for generating the filename
     * @return Mono containing the absolute path of the saved file
     */
    public Mono<String> saveFile(FilePart filePart, String jobId) {
        Path filePath = Paths.get(uploadDir).resolve(jobId + ".csv");

        // Step 1: Ensure parent directory exists (offloaded to separate thread)
        Mono<Path> prepareDirectory = Mono.fromCallable(() -> {
                Files.createDirectories(filePath.getParent());
                return filePath;
            })
            .subscribeOn(Schedulers.boundedElastic());

        // Step 2: Stream file content with backpressure support
        Flux<DataBuffer> fileContent = filePart.content();

        return prepareDirectory
            .flatMap(path ->
                DataBufferUtils.write(
                        fileContent,
                        path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                )
                .then()
            )
            .thenReturn(filePath.toString());
    }
}
