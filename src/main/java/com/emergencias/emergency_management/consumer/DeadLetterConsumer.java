package com.emergencias.emergency_management.consumer;

import com.emergencias.emergency_management.config.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class DeadLetterConsumer {

    @KafkaListener(topics = KafkaTopics.EMERGENCY_EVENTS_DLT)
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
