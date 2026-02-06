package com.csvprocessor.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job Event DTO for Upload Service.
 * 
 * This DTO represents a job processing event published to Kafka when a new 
 * CSV file needs processing. It contains the essential information needed 
 * by worker services to locate and process the uploaded file.
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobEvent {
    
    /** Unique identifier for the job */
    private String jobId;
    
    /** Absolute file path to the raw CSV file */
    private String filePath;
}
