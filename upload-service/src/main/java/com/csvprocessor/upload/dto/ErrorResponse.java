package com.csvprocessor.upload.dto;

/**
 * Data Transfer Object for error responses.
 * 
 * This DTO encapsulates error information returned to clients when
 * an upload or processing operation fails.
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
public class ErrorResponse {
    
    /** Error message describing the failure */
    private String error;

    /**
     * Constructs an ErrorResponse with the specified error message.
     * 
     * @param error The error message
     */
    public ErrorResponse(String error) {
        this.error = error;
    }

    /**
     * Returns the error message.
     * 
     * @return The error message
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message.
     * 
     * @param error The error message to set
     */
    public void setError(String error) {
        this.error = error;
    }
}
