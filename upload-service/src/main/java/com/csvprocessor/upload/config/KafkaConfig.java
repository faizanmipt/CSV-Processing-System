package com.csvprocessor.upload.config;

import com.csvprocessor.upload.dto.JobEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for the Upload Service Producer.
 * 
 * This configuration provides Spring beans for Kafka message production operations.
 * The producer is configured for reliability with idempotence, proper acknowledgment,
 * and retry mechanisms to ensure message delivery.
 * 
 * Reliability Features:
 * - Idempotent producer for exactly-once semantics
 * - All acknowledgments (acks=all) for guaranteed delivery
 * - Configurable retry attempts with exponential backoff
 * - LZ4 compression for efficient message transmission
 * 
 * Performance Optimizations:
 * - Batching enabled with configurable batch size
 * - Linger time optimization for throughput
 * - Buffer memory for handling production bursts
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Configuration
public class KafkaConfig {

    /** Logger instance for this configuration */
    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    /** Kafka cluster bootstrap servers for initial connection */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** Number of retry attempts for transient failures */
    @Value("${kafka.producer.retries:5}")
    private int retries;

    /** Base delay in milliseconds between retry attempts */
    @Value("${kafka.producer.retry.delay_ms:100}")
    private long retryDelayMs;

    /** Maximum delay in milliseconds between retry attempts */
    @Value("${kafka.producer.retry.max_delay_ms:10000}")
    private long maxRetryDelayMs;

    /**
     * Creates the Kafka ProducerFactory with reliability and performance configurations.
     * 
     * Configuration properties include:
     * - Bootstrap servers for cluster connection
     * - Serializers for key and value encoding
     * - Acknowledgment level for message durability
     * - Idempotence for exactly-once semantics
     * - Retry settings for transient error handling
     * - Batching and compression for performance
     * 
     * @return ProducerFactory configured for JobEvent production
     */
    @Bean
    public ProducerFactory<String, JobEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Reliability settings for guaranteed message delivery
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // Retry backoff configuration
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, retryDelayMs);
        
        // Batch settings for throughput optimization
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        // Compression for network efficiency
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        
        logger.info("Kafka producer configured - bootstrapServers={}, retries={}, acks=all, idempotence=true",
                bootstrapServers, retries);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates the KafkaTemplate for sending JobEvent messages.
     * 
     * The KafkaTemplate provides a high-level abstraction for sending messages
     * and includes built-in support for template operations and metrics.
     * 
     * @return KafkaTemplate configured for JobEvent production
     */
    @Bean
    public KafkaTemplate<String, JobEvent> kafkaTemplate() {
        KafkaTemplate<String, JobEvent> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true); // Enable Micrometer tracing
        return template;
    }
}
