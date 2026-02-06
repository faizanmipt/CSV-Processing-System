package com.csvprocessor.upload.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Global Exception Handler for Upload Service.
 * 
 * This controller advice provides centralized exception handling for the upload
 * service, converting exceptions into appropriate HTTP error responses.
 * 
 * Exception Mappings:
 * - MaxUploadSizeExceededException -> 413 Payload Too Large
 * - IllegalArgumentException -> 400 Bad Request
 * - General Exception -> 500 Internal Server Error
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** Logger instance for this handler */
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles file upload size limit exceeded exceptions.
     * 
     * @param e The exception thrown when file size exceeds limit
     * @return ResponseEntity with 413 status and error message
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public ResponseEntity<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logger.warn("File upload size limit exceeded: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"File size exceeds the maximum allowed limit (10MB)\"}");
    }

    /**
     * Handles illegal argument exceptions.
     * 
     * @param e The exception thrown for invalid arguments
     * @return ResponseEntity with 400 status and error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
    }

    /**
     * Handles uncaught exceptions.
     * 
     * @param e The unexpected exception
     * @return ResponseEntity with 500 status and generic error message
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<String> handleException(Exception e) {
        logger.error("Unhandled exception in upload service", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"An unexpected error occurred\"}");
    }
}
