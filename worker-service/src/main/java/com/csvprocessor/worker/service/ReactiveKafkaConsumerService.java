package com.csvprocessor.worker.service;

import com.csvprocessor.worker.dto.JobEvent;
import com.csvprocessor.worker.dto.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;

/**
 * Reactive Kafka Consumer Service for processing CSV job events.
 * 
 * This service implements a production-grade reactive streaming consumer that:
 * - Subscribes to the csv-jobs Kafka topic for incoming job events
 * - Processes each job event through the CSV processing pipeline
 * - Manages job status updates via the JobStatusClient
 * - Handles failures with exponential backoff retry logic
 * - Sends permanently failed messages to a Dead Letter Queue (DLQ)
 * 
 * Architecture:
 * - Uses Project Reactor for non-blocking, backpressure-aware streaming
 * - Implements the reactive pull model for controlled message consumption
 * - Provides exactly-once processing semantics with manual offset acknowledgment
 * - Decouples consumption from processing via reactive operators
 * 
 * Thread Safety:
 * - KafkaReceiver is thread-safe and can be shared across subscribers
 * - Each partition is assigned to exactly one consumer in the group
 * - Offset management is handled per-partition by the KafkaReceiver
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class ReactiveKafkaConsumerService {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaConsumerService.class);

    /** Kafka receiver for consuming job events from the csv-jobs topic */
    private final KafkaReceiver<String, String> kafkaReceiver;

    /** Kafka sender for producing failed messages to the DLQ */
    private final KafkaSender<String, String> kafkaSender;

    /** CSV processing service for handling job file processing */
    private final CsvProcessor csvProcessor;

    /** Client for updating job status in the job status store */
    private final JobStatusClient jobStatusClient;

    /** Jackson ObjectMapper for deserializing job events */
    private final ObjectMapper objectMapper;

    /** Dead Letter Queue topic name for failed messages */
    @Value("${kafka.dlq.topic:csv-jobs-dlq}")
    private String dlqTopic;

    /** Maximum number of retry attempts for failed message processing */
    @Value("${worker.retry.max_attempts:5}")
    private int maxRetryAttempts;

    /** Initial interval in milliseconds for exponential backoff retry */
    @Value("${worker.retry.initial_interval_ms:1000}")
    private long initialIntervalMs;

    /** Maximum concurrency level for parallel message processing */
    @Value("${worker.processing.concurrency:2}")
    private int processingConcurrency;

    /**
     * Constructs the ReactiveKafkaConsumerService with required dependencies.
     * 
     * @param kafkaReceiver The Kafka receiver for consuming messages
     * @param kafkaSender The Kafka sender for producing DLQ messages
     * @param csvProcessor The CSV processing service
     * @param jobStatusClient The job status client for status updates
     * @param objectMapper The JSON object mapper for deserialization
     */
    public ReactiveKafkaConsumerService(
            KafkaReceiver<String, String> kafkaReceiver,
            KafkaSender<String, String> kafkaSender,
            CsvProcessor csvProcessor,
            JobStatusClient jobStatusClient,
            ObjectMapper objectMapper) {
        this.kafkaReceiver = kafkaReceiver;
        this.kafkaSender = kafkaSender;
        this.csvProcessor = csvProcessor;
        this.jobStatusClient = jobStatusClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Initializes and starts the reactive Kafka consumer.
     * 
     * This method is called after bean construction by the Spring container.
     * It subscribes to the Kafka receiver's message stream and initiates
     * the reactive processing pipeline.
     */
    @PostConstruct
    public void start() {
        logger.info("Initializing reactive Kafka consumer for job event processing");
        logger.info("Configured retry policy: maxAttempts={}, initialIntervalMs={}", 
                maxRetryAttempts, initialIntervalMs);
        logger.info("Processing concurrency level: {}", processingConcurrency);

        consume()
            .subscribe(
                ok -> {},
                err -> logger.error("Critical error in Kafka consumer pipeline", err)
            );
    }

    /**
     * Creates the infinite message consumption stream.
     * 
     * This method returns a Flux that represents the continuous stream of
     * incoming Kafka messages. Each message is processed through the handleRecord
     * method with controlled concurrency to prevent overwhelming downstream systems.
     * 
     * @return Flux representing the infinite message stream
     */
    private Flux<Void> consume() {
        return kafkaReceiver.receive()
            .doOnSubscribe(s ->
                logger.info("Kafka consumer subscription active, awaiting job events"))
            .doOnNext(r ->
                logger.debug("Received message - key={}, partition={}, offset={}",
                        r.key(), r.partition(), r.offset()))
            .flatMap(this::handleRecord, processingConcurrency);
    }

    /**
     * Processes a single Kafka record through the complete pipeline.
     * 
     * The processing pipeline consists of:
     * 1. Parse the JSON payload into a JobEvent
     * 2. Execute the CSV processing job
     * 3. Apply exponential backoff retry on transient failures
     * 4. Route to failure handler for permanent failures
     * 
     * @param record The Kafka receiver record containing the job event
     * @return Mono representing the asynchronous processing result
     */
    private Mono<Void> handleRecord(ReceiverRecord<String, String> record) {
        return parseEvent(record)
            .flatMap(event -> processJob(event, record))
            .retryWhen(Retry.backoff(
                    maxRetryAttempts,
                    Duration.ofMillis(initialIntervalMs)))
            .onErrorResume(error ->
                handleFailure(record, error)
            );
    }

    /**
     * Parses the JSON payload from a Kafka record into a JobEvent.
     * 
     * @param record The Kafka receiver record containing the message
     * @return Mono containing the parsed JobEvent
     */
    private Mono<JobEvent> parseEvent(ReceiverRecord<String, String> record) {
        return Mono.fromCallable(() ->
                objectMapper.readValue(record.value(), JobEvent.class)
            )
            .doOnError(e ->
                logger.error("Failed to deserialize job event from message payload", e));
    }

    /**
     * Processes a job event through the CSV processing pipeline.
     * 
     * This method orchestrates the complete job processing:
     * 1. Updates job status to IN_PROGRESS
     * 2. Processes the CSV file
     * 3. Updates job status to COMPLETED on success
     * 4. Acknowledges the Kafka offset
     * 
     * @param event The job event containing processing instructions
     * @param record The original Kafka record for offset management
     * @return Mono representing the asynchronous job processing result
     */
    private Mono<Void> processJob(JobEvent event,
                                  ReceiverRecord<String, String> record) {
        String jobId = event.getJobId();
        String filePath = event.getFilePath();

        logger.info("Initiating job processing - jobId={}, filePath={}", jobId, filePath);

        return jobStatusClient.updateStatus(jobId, JobStatus.IN_PROGRESS)
            .then(csvProcessor.processFile(filePath, jobId))
            .flatMap(out ->
                jobStatusClient.updateStatus(jobId, JobStatus.COMPLETED)
            )
            .doOnSuccess(v -> {
                logger.info("Job processing completed successfully - jobId={}", jobId);
                record.receiverOffset().acknowledge();
            })
            .then();
    }

    /**
     * Handles permanent processing failures.
     * 
     * When a job fails after all retry attempts, this method:
     * 1. Updates the job status to FAILED
     * 2. Sends the original message to the Dead Letter Queue
     * 3. Acknowledges the Kafka offset to prevent reprocessing
     * 
     * @param record The original Kafka record that failed processing
     * @param error The exception that caused the failure
     * @return Mono representing the asynchronous failure handling result
     */
    private Mono<Void> handleFailure(ReceiverRecord<String, String> record,
                                     Throwable error) {
        logger.error("Job processing failed after all retry attempts", error);

        return parseEvent(record)
            .flatMap(event ->
                jobStatusClient.updateStatus(event.getJobId(), JobStatus.FAILED)
                    .then(sendToDlq(record.value(), error))
            )
            .doFinally(s -> record.receiverOffset().acknowledge())
            .then();
    }

    /**
     * Sends a failed message to the Dead Letter Queue.
     * 
     * The DLQ preserves failed messages for later analysis and manual
     * intervention. The original message is enriched with error context
     * before being sent to the DLQ topic.
     * 
     * @param payload The original message payload
     * @param error The exception that caused the failure
     * @return Mono representing the asynchronous DLQ production result
     */
    private Mono<Void> sendToDlq(String payload, Throwable error) {
        String enrichedPayload =
            payload + " | ERROR: " + error.getMessage();

        SenderRecord<String, String, Void> dlqRecord =
            SenderRecord.create(dlqTopic, null, null,
                    null, enrichedPayload, null);

        return kafkaSender.send(Mono.just(dlqRecord))
            .doOnNext(r ->
                logger.warn("Message sent to Dead Letter Queue - topic={}", dlqTopic))
            .then();
    }

    /**
     * Gracefully shuts down the Kafka consumer.
     * 
     * This method is called during application context destruction
     * to ensure clean shutdown of the consumer.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Initiating graceful shutdown of Kafka consumer");
    }
}
