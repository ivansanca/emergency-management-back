package com.emergencias.emergency_management.messaging.config;

import com.emergencias.emergency_management.exception.EventRejectedException;
import com.emergencias.emergency_management.exception.InfrastructureFailures;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.BackOffHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultBackOffHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Error handling of the Kafka listeners. Failures are handled in three ways:
 * <ul>
 *   <li>Invalid events, and events that Kafka rejects for good when they are
 *   published (too large, for instance), go straight to the dead letter
 *   topic, because retrying them would fail again.</li>
 *   <li>Infrastructure failures (Neo4j or Kafka not reachable, topic missing,
 *   not authorised) are retried with an increasing delay and without limit.
 *   An emergency must not be discarded because a dependency is briefly down:
 *   the partition waits and carries on, in order, once it is back.</li>
 *   <li>Any other failure, such as a bug, is retried a couple of times and
 *   then sent to the dead letter topic, so that it cannot block the
 *   partition forever.</li>
 * </ul>
 * Spring Kafka chooses how to retry a record on its first failure and only
 * chooses again when the type of the exception changes. That is why a
 * temporary publishing failure and a rejected event are different classes.
 * <p>
 * Each failed attempt is logged as a warning with its root cause; without it
 * Spring Kafka only logs "Record in retry and not yet recovered".
 */
@Configuration
@Slf4j
public class KafkaErrorHandlingConfig {

    static final long RETRY_INTERVAL_MS = 1000L;
    static final long MAX_RETRIES = 2L;
    static final long MAX_RETRY_INTERVAL_MS = 30_000L;
    static final double RETRY_MULTIPLIER = 2.0;

    private static final int MAX_CAUSE_MESSAGE = 200;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate) {

        return errorHandler(new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                KafkaErrorHandlingConfig::deadLetterDestination));
    }

    static DefaultErrorHandler errorHandler(ConsumerRecordRecoverer recoverer) {
        return errorHandler(recoverer, new DefaultBackOffHandler());
    }

    /**
     * @param backOffHandler waits between attempts; tests pass one that does
     *                       not wait
     */
    static DefaultErrorHandler errorHandler(
            ConsumerRecordRecoverer recoverer, BackOffHandler backOffHandler) {

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES),
                backOffHandler);

        errorHandler.setBackOffFunction(
                (record, exception) -> backOffFor(exception));
        errorHandler.addNotRetryableExceptions(
                InvalidIncidentEventException.class,
                EventRejectedException.class,
                JacksonException.class);
        errorHandler.setRetryListeners(new LoggingRetryListener());

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

    /** Type and message of the deepest cause, which is the original error. */
    static String describe(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = failure;
        while (root.getCause() != null && visited.add(root)) {
            root = root.getCause();
        }

        String type = root.getClass().getSimpleName();
        if (root.getMessage() == null) {
            return type;
        }
        String message = root.getMessage().replaceAll("\\s+", " ");
        return type + ": " + message.substring(0, Math.min(message.length(), MAX_CAUSE_MESSAGE));
    }

    /** Logs every failed attempt with its cause. */
    static class LoggingRetryListener implements RetryListener {

        @Override
        public void failedDelivery(
                ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {

            log.warn("Evento {}: falló el intento {} ({})",
                    record.key(), deliveryAttempt, describe(exception));
        }
    }
}
