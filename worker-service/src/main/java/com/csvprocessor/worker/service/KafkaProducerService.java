package com.csvprocessor.worker.service;

import com.csvprocessor.worker.dto.JobEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer Service for Dead Letter Queue Operations.
 * 
 * This service provides functionality for sending failed job events to a
 * Dead Letter Queue (DLQ) for later analysis and reprocessing. It wraps
 * the synchronous Spring KafkaTemplate in a reactive Mono for non-blocking
 * integration with the reactive processing pipeline.
 * 
 * Features:
 * - Reactive wrapper around KafkaTemplate for Mono-based composition
 * - Configurable timeout for DLQ operations
 * - Graceful error handling that prevents pipeline failures
 * - Detailed logging for DLQ operations traceability
 * 
 * Error Handling Strategy:
 * - DLQ failures do not propagate to the main processing pipeline
 * - Failed DLQ sends are logged but do not block job completion
 * - This ensures processing continues even if DLQ is unavailable
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class KafkaProducerService {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    /** Kafka template for sending messages to Kafka topics */
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    /** Dead Letter Queue topic name for failed job events */
    @Value("${kafka.dlq.topic:csv-jobs-dlq}")
    private String dlqTopic;

    /** Timeout in seconds for DLQ send operations */
    @Value("${kafka.dlq.timeout.seconds:10}")
    private long dlqTimeoutSeconds;

    /**
     * Constructs the KafkaProducerService with the specified KafkaTemplate.
     * 
     * @param kafkaTemplate The Kafka template for sending messages
     */
    public KafkaProducerService(KafkaTemplate<String, JobEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a failed JobEvent to the Dead Letter Queue.
     * 
     * This method publishes a job event that has failed processing to the
     * configured DLQ topic. The job ID is used as the message key for
     * partition affinity. The operation has a configurable timeout.
     * 
     * Error Handling:
     * - If the DLQ send times out or fails, the error is logged
     * - The Mono returns empty to prevent breaking the main pipeline
     * - The original job failure is still considered complete
     * 
     * @param event The failed job event to send to DLQ
     * @return Mono<Void> that completes when the DLQ send finishes (or is skipped on error)
     */
    public Mono<Void> sendToDlq(JobEvent event) {
        logger.warn("Sending job to Dead Letter Queue - jobId={}, topic={}", 
                event.getJobId(), dlqTopic);

        CompletableFuture<SendResult<String, JobEvent>> future =
                kafkaTemplate.send(dlqTopic, event.getJobId(), event);

        return Mono.fromFuture(future)
                .timeout(Duration.ofSeconds(dlqTimeoutSeconds))
                .doOnSuccess(result ->
                        logger.info("DLQ publish successful - jobId={}, partition={}, offset={}",
                                event.getJobId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                        )
                )
                .doOnError(error ->
                        logger.error("DLQ publish failed - jobId={}, error={}",
                                event.getJobId(),
                                error.getMessage(),
                                error
                        )
                )
                // Graceful degradation: DLQ failure does not affect main pipeline
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
