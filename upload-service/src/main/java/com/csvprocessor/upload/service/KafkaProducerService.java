package com.csvprocessor.upload.service;

import com.csvprocessor.upload.dto.JobEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Kafka Producer Service for the Upload Service.
 * 
 * This service provides Kafka message production capabilities for publishing
 * JobEvent messages to trigger worker processing.
 * 
 * Features:
 * - Reactive wrapper around KafkaTemplate
 * - Job ID as message key for partition affinity
 * - Asynchronous send with Mono composition
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class KafkaProducerService {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    /** Kafka template for sending messages */
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    /** Target Kafka topic for job events */
    private final String topic;

    /**
     * Constructs the KafkaProducerService.
     * 
     * @param kafkaTemplate The Kafka template for sending messages
     * @param topic The target topic for job events
     */
    public KafkaProducerService(KafkaTemplate<String, JobEvent> kafkaTemplate,
                                @Value("${kafka.topics.jobs:csv-jobs}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Sends a JobEvent to the Kafka topic.
     * 
     * @param event The job event to publish
     * @return Mono<Void> completing when the message is sent
     */
    public Mono<Void> sendEvent(JobEvent event) {
        logger.debug("Publishing JobEvent to Kafka - jobId={}, topic={}", 
                event.getJobId(), topic);
        return Mono.fromFuture(kafkaTemplate.send(topic, event.getJobId(), event))
                .doOnSuccess(result -> 
                        logger.info("JobEvent published - jobId={}, partition={}, offset={}",
                                event.getJobId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()))
                .doOnError(error -> 
                        logger.error("JobEvent publish failed - jobId={}, error={}",
                                event.getJobId(), error.getMessage()))
                .then();
    }
}
