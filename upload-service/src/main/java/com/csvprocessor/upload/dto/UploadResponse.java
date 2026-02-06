package com.csvprocessor.upload.dto;

/**
 * Data Transfer Object for successful upload responses.
 * 
 * This DTO is returned to clients upon successful file upload. It contains
 * the job ID that can be used to track processing status and download
 * the processed file.
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
public class UploadResponse {
    
    /** Unique job identifier for tracking the upload */
    private String id;

    /**
     * Constructs an UploadResponse with the specified job ID.
     * 
     * @param id The unique job identifier
     */
    public UploadResponse(String id) {
        this.id = id;
    }

    /**
     * Returns the job identifier.
     * 
     * @return The job ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the job identifier.
     * 
     * @param id The job ID to set
     */
    public void setId(String id) {
        this.id = id;
    }
}
