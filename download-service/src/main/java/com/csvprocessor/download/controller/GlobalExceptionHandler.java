package com.csvprocessor.download.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Global Exception Handler for Download Service.
 * 
 * This controller advice provides centralized exception handling for the
 * download service, converting exceptions into appropriate HTTP responses.
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles uncaught exceptions in the download service.
     * 
     * @param e The exception that was thrown
     * @return ResponseEntity with 500 status and error message
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<String> handleException(Exception e) {
        logger.error("Unhandled exception in download service", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"An unexpected error occurred\"}");
    }
}
