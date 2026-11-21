package com.emergencias.emergency_management;

import com.emergencias.emergency_management.integration.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the application context starts against real Neo4j and Kafka.
 */
@IntegrationTest
class EmergencyManagementApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBean(EmergencyManagementApplication.class)).isNotNull();
    }
}
