package com.emergencias.emergency_management.messaging.consumer;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Logs every event that ends up in a dead letter topic, automatic or manual,
 * together with the topic it came from and the reason of the rejection. It
 * has its own consumer group so that it does not take part in the
 * rebalances of the main consumers.
 */
@Component
@Slf4j
public class DeadLetterConsumer {

    @KafkaListener(
            topics = {KafkaTopics.EMERGENCY_EVENTS_DLT, KafkaTopics.EMERGENCY_EVENTS_MANUAL_DLT},
            groupId = "emergency-management-dlt")
    public void consume(ConsumerRecord<String, String> record) {
        log.warn("Evento rechazado desde {}. Motivo: {} ({}). Mensaje: {}",
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN),
                record.value());
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null
                ? "desconocido"
                : new String(header.value(), StandardCharsets.UTF_8);
    }
}
