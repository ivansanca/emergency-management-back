package com.emergencias.emergency_management.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.neo4j.Neo4jContainer;

/**
 * Containers used by the integration tests. They are Spring beans, so they
 * live as long as the cached context, and {@code @ServiceConnection} points
 * the application at them instead of the addresses in the properties.
 * <p>
 * The images are the same versions used for development.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestContainers {

    static final String NEO4J_IMAGE = "neo4j:2026.09";
    static final String KAFKA_IMAGE = "apache/kafka:4.3.1";

    @Bean
    @ServiceConnection
    Neo4jContainer neo4jContainer() {
        return new Neo4jContainer(NEO4J_IMAGE);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }
}
