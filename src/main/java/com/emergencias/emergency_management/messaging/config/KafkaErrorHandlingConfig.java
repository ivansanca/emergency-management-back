package com.emergencias.emergency_management.messaging.config;

import com.emergencias.emergency_management.exception.InfrastructureFailures;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Error handling of the Kafka listeners. Failures are handled in three ways:
 * <ul>
 *   <li>Invalid events go straight to the dead letter topic, because
 *   retrying them would fail again.</li>
 *   <li>Infrastructure failures (Neo4j or Kafka not reachable) are retried
 *   with an increasing delay and without limit. An emergency must not be
 *   discarded because a dependency is briefly down: the partition waits and
 *   carries on, in order, once it is back.</li>
 *   <li>Any other failure, such as a bug, is retried a couple of times and
 *   then sent to the dead letter topic, so that it cannot block the
 *   partition forever.</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    static final long RETRY_INTERVAL_MS = 1000L;
    static final long MAX_RETRIES = 2L;
    static final long MAX_RETRY_INTERVAL_MS = 30_000L;
    static final double RETRY_MULTIPLIER = 2.0;

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

        errorHandler.setBackOffFunction(
                (record, exception) -> backOffFor(exception));
        errorHandler.addNotRetryableExceptions(
                InvalidIncidentEventException.class,
                JacksonException.class);

        return errorHandler;
    }

    /**
     * Back-off for infrastructure failures, or {@code null} to use the
     * default one.
     */
    static BackOff backOffFor(Exception exception) {
        if (!InfrastructureFailures.isInfrastructureFailure(exception)) {
            return null;
        }

        ExponentialBackOff backOff =
                new ExponentialBackOff(RETRY_INTERVAL_MS, RETRY_MULTIPLIER);
        backOff.setMaxInterval(MAX_RETRY_INTERVAL_MS);
        return backOff;
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
