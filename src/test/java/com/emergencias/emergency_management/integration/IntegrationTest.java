package com.emergencias.emergency_management.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that runs the whole application against real Neo4j and Kafka
 * started in throwaway containers.
 * <p>
 * Every test class with this annotation shares the same Spring context, and
 * therefore the same containers. The tests are skipped, not failed, when
 * Docker is not available; in GitHub Actions Docker is always available.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestContainers.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface IntegrationTest {
}
