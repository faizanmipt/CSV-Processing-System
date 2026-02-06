package com.csvprocessor.download.dto;

/**
 * Data Transfer Object for error responses in the download service.
 * 
 * @author CSV Processing System
 * @version 1.0.0
 * @since 2024
 */
public class ErrorResponse {
    private String error;

    public ErrorResponse(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
