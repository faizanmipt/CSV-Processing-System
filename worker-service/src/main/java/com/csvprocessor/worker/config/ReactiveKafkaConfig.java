package com.csvprocessor.worker.config;

import com.csvprocessor.worker.dto.JobEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Reactive Kafka Configuration for the Worker Service.
 * 
 * This configuration provides Spring beans for reactive Kafka message consumption
 * and production operations. It utilizes Project Reactor Kafka for non-blocking,
 * backpressure-aware streaming capabilities.
 * 
 * Architecture:
 * - KafkaReceiver: Consumes job events from the csv-jobs topic
 * - KafkaSender: Produces failed messages to the Dead Letter Queue (DLQ)
 * 
 * Spring Boot auto-configuration creates the necessary infrastructure beans
 * when the appropriate dependencies are present on the classpath.
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Configuration
public class ReactiveKafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaConfig.class);

    /**
     * Kafka bootstrap servers configuration value.
     * Injected from application properties: spring.kafka.bootstrap-servers
     */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Consumer group identifier for the worker service.
     * All worker instances sharing the same group ID will collaboratively
     * consume messages from the subscribed topic partitions.
     */
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Topic name for consuming job events.
     * Defaults to "csv-jobs" if not explicitly configured.
     */
    @Value("${kafka.topic:csv-jobs}")
    private String topic;

    /**
     * Creates the Kafka receiver options for consuming messages.
     * 
     * Configuration includes:
     * - Consumer group assignment for load balancing
     * - String deserializers for both key and value
     * - Manual offset management (auto-commit disabled)
     * - Earliest offset reset policy for at-least-once semantics
     * - Partition assignment and revocation listeners for lifecycle tracking
     * 
     * @return ReceiverOptions configured for the csv-jobs topic subscription
     */
    @Bean
    public ReceiverOptions<String, String> reactiveReceiverOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(props)
                .subscription(Collections.singletonList(topic))
                .addAssignListener(partitions -> 
                    logger.info("Partition assignment received: {}", partitions))
                .addRevokeListener(partitions -> 
                    logger.info("Partition revocation received: {}", partitions));

        logger.info("Kafka receiver initialized - Topic: {}, Group ID: {}", 
                topic, groupId);
        
        return options;
    }

    /**
     * Creates the KafkaReceiver bean for reactive message consumption.
     * 
     * The KafkaReceiver provides a reactive stream of incoming messages
     * from the configured topic. Messages are consumed with manual offset
     * acknowledgment, ensuring exactly-once processing semantics when
     * combined with proper offset management.
     * 
     * @param options The receiver options created by reactiveReceiverOptions()
     * @return KafkaReceiver instance configured for the csv-jobs topic
     */
    @Bean
    public KafkaReceiver<String, String> kafkaReceiver(ReceiverOptions<String, String> options) {
        return KafkaReceiver.create(options);
    }

    /**
     * Creates the Kafka sender options for producing messages.
     * 
     * Configuration includes:
     * - Bootstrap servers for initial cluster connection
     * - String serializers for key and value encoding
     * - Acknowledgment level set to "all" for durability
     * - Retry configuration for transient failures
     * - Idempotence enabled for exactly-once semantics
     * 
     * @return SenderOptions configured for reliable message production
     */
    @Bean
    public SenderOptions<String, String> reactiveSenderOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return SenderOptions.create(props);
    }

    /**
     * Creates the KafkaSender bean for reactive message production.
     * 
     * The KafkaSender is used primarily for sending failed messages
     * to the Dead Letter Queue (DLQ). This ensures that messages
     * which cannot be processed after retry attempts are preserved
     * for later analysis and manual intervention.
     * 
     * @param options The sender options created by reactiveSenderOptions()
     * @return KafkaSender instance configured for DLQ message production
     */
    @Bean
    public KafkaSender<String, String> kafkaSender(SenderOptions<String, String> options) {
        return KafkaSender.create(options);
    }
}
