package com.califorge.msorden.config;

import com.califorge.msorden.dto.InteraccionEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Productor Kafka de eventos de interaccion. Tiempos de espera acotados: si el broker no esta
 * disponible la publicacion falla rapido y NUNCA bloquea el flujo de negocio (best-effort).
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public KafkaTemplate<String, Object> interaccionKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        Map<String, Object> p = new HashMap<>();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        p.put(JsonSerializer.TYPE_MAPPINGS, "interaccion:" + InteraccionEvent.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "1");
        p.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
        p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(p));
    }
}
