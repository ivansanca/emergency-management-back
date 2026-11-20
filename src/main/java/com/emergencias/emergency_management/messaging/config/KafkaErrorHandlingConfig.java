package com.emergencias.emergency_management.messaging.config;

import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Error handling of the Kafka listeners.
 * <p>
 * Invalid events are sent straight to the dead letter topic, because retrying
 * them would fail again. Any other failure is treated as temporary (for
 * instance Neo4j being down) and is retried before the event is given up.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    static final long RETRY_INTERVAL_MS = 1000L;
    static final long MAX_RETRIES = 2L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate) {

        return errorHandler(new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                KafkaErrorHandlingConfig::deadLetterDestination));
    }

    static DefaultErrorHandler errorHandler(ConsumerRecordRecoverer recoverer) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        errorHandler.addNotRetryableExceptions(
                InvalidIncidentEventException.class,
                JacksonException.class);

        return errorHandler;
    }

    /**
     * Partition -1 lets Kafka choose the partition, so the dead letter topic
     * does not need as many partitions as the source topic.
     */
    static TopicPartition deadLetterDestination(
            ConsumerRecord<?, ?> record,
            Exception exception) {

        return new TopicPartition(KafkaTopics.EMERGENCY_EVENTS_DLT, -1);
    }
}
