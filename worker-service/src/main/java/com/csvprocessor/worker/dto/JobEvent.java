package com.csvprocessor.worker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job Event DTO for Worker Service.
 * 
 * This DTO represents a job processing event consumed from Kafka.
 * It contains the essential information needed to process the uploaded file.
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
