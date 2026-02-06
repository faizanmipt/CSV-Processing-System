package com.csvprocessor.upload.service;

import com.csvprocessor.upload.dto.JobEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fully Reactive Kafka Producer with Backpressure Support.
 * 
 * This service provides:
 * 1. Reactive API with Mono/Flux
 * 2. Backpressure handling with bounded buffer
 * 3. Automatic retry on failure
 * 4. Monitoring metrics
 * 
 * Note: Uses hybrid approach - KafkaTemplate.send() is blocking,
 * wrapped in Mono.fromFuture() for reactive composition.
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class ReactiveKafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaProducerService.class);

    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    @Value("${kafka.topics.jobs:csv-jobs}")
    private String topic;

    @Value("${producer.buffer.max-size:10000}")
    private int maxBufferSize;

    /** Backpressure sink with BUFFER strategy */
    private Sinks.Many<JobEvent> sink;
    
    /** Metrics counters */
    private final AtomicLong sentCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong bufferedCount = new AtomicLong(0);

    /**
     * Constructs the ReactiveKafkaProducerService.
     * 
     * @param kafkaTemplate The Kafka template for sending messages
     */
    public ReactiveKafkaProducerService(KafkaTemplate<String, JobEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Initialize the reactive pipeline after @Value injection.
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing reactive Kafka producer pipeline with buffer size: {}", maxBufferSize);
        
        // Create sink after @Value fields are injected
        this.sink = Sinks.many().multicast()
            .onBackpressureBuffer(maxBufferSize);
        
        // Start the pipeline
        startPipeline();
    }

    /**
     * Start consuming from the sink and sending to Kafka.
     */
    private void startPipeline() {
        sink.asFlux()
            .publishOn(Schedulers.boundedElastic())
            .flatMap(this::sendEvent)
            .doOnNext(result -> {
                sentCount.incrementAndGet();
                bufferedCount.decrementAndGet();
            })
            .doOnError(error -> {
                failedCount.incrementAndGet();
                bufferedCount.decrementAndGet();
                logger.error("Send pipeline error", error);
            })
            .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                .filter(throwable -> throwable instanceof Exception)
            )
            .subscribe();
    }

    /**
     * Emit event with backpressure.
     * 
     * If downstream is slow, events will be buffered up to maxBufferSize.
     * 
     * @param event JobEvent to send
     * @return Mono that completes when event is buffered
     */
    public Mono<Void> emitWithBackpressure(JobEvent event) {
        bufferedCount.incrementAndGet();
        
        Sinks.EmitResult result = sink.tryEmitNext(event);
        
        if (result.isSuccess()) {
            logger.debug("Buffered event: {}", event.getJobId());
            return Mono.empty();
        } else {
            bufferedCount.decrementAndGet();
            logger.warn("Failed to buffer event: {} - {}", event.getJobId(), result);
            return Mono.error(new RuntimeException("Failed to buffer event: " + result));
        }
    }

    /**
     * Send a JobEvent to Kafka reactively.
     * 
     * @param event The JobEvent to send
     * @return Mono containing the send result
     */
    private Mono<SendResult<String, JobEvent>> sendEvent(JobEvent event) {
        String jobId = event.getJobId();
        
        logger.info("Sending JobEvent to Kafka: jobId={}", jobId);
        
        return Mono.fromFuture(
                kafkaTemplate.send(topic, jobId, event)
            )
            .doOnSuccess(result -> {
                logger.info("Successfully sent jobId={} to partition={}, offset={}", 
                    jobId, 
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            })
            .doOnError(error -> {
                logger.error("Failed to send jobId={}: {}", jobId, error.getMessage());
            });
    }

    /**
     * Send with timeout.
     * 
     * @param event The JobEvent to send
     * @param timeout The timeout duration
     * @return Mono containing the send result
     */
    public Mono<SendResult<String, JobEvent>> sendEventWithTimeout(JobEvent event, Duration timeout) {
        return sendEvent(event)
            .timeout(timeout)
            .doOnError(error -> {
                logger.error("Timeout sending jobId={}: {}", event.getJobId(), error.getMessage());
            });
    }

    /**
     * Get producer metrics.
     * 
     * @return ProducerMetrics containing current metrics
     */
    public ProducerMetrics getMetrics() {
        return new ProducerMetrics(
            sentCount.get(),
            failedCount.get(),
            bufferedCount.get(),
            sink.currentSubscriberCount()
        );
    }

    /**
     * Check producer health.
     * 
     * @return true if buffer is not full
     */
    public boolean isHealthy() {
        return bufferedCount.get() < maxBufferSize;
    }

    /**
     * Graceful shutdown.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down reactive Kafka producer");
        sink.tryEmitComplete();
    }

    /**
     * Producer metrics record.
     * 
     * @param sentCount Number of successfully sent events
     * @param failedCount Number of failed send attempts
     * @param bufferedCount Current number of events in buffer
     * @param activeSubscribers Number of active subscribers
     */
    public record ProducerMetrics(
        long sentCount,
        long failedCount,
        long bufferedCount,
        int activeSubscribers
    ) {}
}
