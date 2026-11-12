package com.emergencias.emergency_management.config;

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
                IllegalArgumentException.class,
                JacksonException.class);

        return errorHandler;
    }

    static TopicPartition deadLetterDestination(
            ConsumerRecord<?, ?> record,
            Exception exception) {

        return new TopicPartition(KafkaTopics.EMERGENCY_EVENTS_DLT, -1);
    }
}
