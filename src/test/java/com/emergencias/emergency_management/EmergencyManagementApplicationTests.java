package com.emergencias.emergency_management;

import com.emergencias.emergency_management.integration.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Checks that the application context starts against real Neo4j and Kafka.
 */
@IntegrationTest
class EmergencyManagementApplicationTests {

    @Test
    void contextLoads() {
    }
}
