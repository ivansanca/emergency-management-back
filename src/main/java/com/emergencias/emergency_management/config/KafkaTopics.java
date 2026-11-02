package com.emergencias.emergency_management.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    public static final String EMERGENCY_EVENTS = "emergency-events";
}
