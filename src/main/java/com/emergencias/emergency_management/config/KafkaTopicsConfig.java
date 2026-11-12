package com.emergencias.emergency_management.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic emergencyEventsTopic() {
        return topic(KafkaTopics.EMERGENCY_EVENTS);
    }

    @Bean
    public NewTopic emergencyEventsDeadLetterTopic() {
        return topic(KafkaTopics.EMERGENCY_EVENTS_DLT);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
