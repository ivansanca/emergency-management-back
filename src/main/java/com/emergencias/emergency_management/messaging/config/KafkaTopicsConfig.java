package com.emergencias.emergency_management.messaging.config;

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

    @Bean
    public NewTopic incidentCreatedTopic() {
        return topic(KafkaTopics.INCIDENT_CREATED);
    }

    @Bean
    public NewTopic incidentRecommendationTopic() {
        return topic(KafkaTopics.INCIDENT_RECOMMENDATION);
    }

    @Bean
    public NewTopic resourceAssignedTopic() {
        return topic(KafkaTopics.RESOURCE_ASSIGNED);
    }

    @Bean
    public NewTopic incidentClosedTopic() {
        return topic(KafkaTopics.INCIDENT_CLOSED);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
