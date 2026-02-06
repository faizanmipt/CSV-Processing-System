package com.csvprocessor.upload.dto;

/**
 * Job Status Enum for Upload Service.
 * 
 * This enum defines all possible states a job can be in during the
 * CSV processing pipeline.
 * 
 * Status Lifecycle:
 * RECEIVED -> STORED -> QUEUED -> IN_PROGRESS -> COMPLETED/FAILED
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
public enum JobStatus {
    
    /** Job has been received but file not yet stored */
    RECEIVED,
    
    /** File has been stored successfully */
    STORED,
    
    /** Job has been queued for processing */
    QUEUED,
    
    /** Job is currently being processed */
    IN_PROGRESS,
    
    /** Job completed successfully */
    COMPLETED,
    
    /** Job processing failed */
    FAILED
}
