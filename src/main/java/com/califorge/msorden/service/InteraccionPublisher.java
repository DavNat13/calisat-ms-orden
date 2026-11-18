package com.califorge.msorden.service;

import com.califorge.msorden.dto.InteraccionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Publica interacciones (view / cart / purchase) en Kafka con key = userId. Nunca lanza excepcion. */
@Component
public class InteraccionPublisher {

    public static final String VIEW = "view";
    public static final String CART = "cart";
    public static final String PURCHASE = "purchase";

    private static final Logger log = LoggerFactory.getLogger(InteraccionPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public InteraccionPublisher(KafkaTemplate<String, Object> interaccionKafkaTemplate,
                                @Value("${calisat.kafka.topic-interacciones:interacciones-user}") String topic) {
        this.kafka = interaccionKafkaTemplate;
        this.topic = topic;
    }

    /** Usuario del JWT actual, o "anonimo" en rutas publicas. */
    public static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? jwt.getName() : "anonimo";
    }

    public void publicar(String userId, String itemId, String action, String category) {
        if (userId == null || itemId == null) {
            return;
        }
        try {
            InteraccionEvent evento = new InteraccionEvent(UUID.randomUUID().toString(), userId, itemId,
                    action, category, null, Instant.now());
            kafka.send(topic, userId, evento).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.warn("No se pudo publicar interaccion '{}' en Kafka (flujo continuado): {}", action, ex.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar interaccion '{}' en Kafka (flujo continuado): {}", action, e.getMessage());
        }
    }
}
