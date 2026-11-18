package com.califorge.msorden.dto;

import java.time.Instant;

/**
 * Evento de interaccion publicado en el topico {@code interacciones-user} (key = userId).
 * Contrato compartido con calisat-ms-analytics (alias de tipo "interaccion").
 */
public record InteraccionEvent(
        String eventId,
        String userId,
        String itemId,
        String action,
        String category,
        String productType,
        Instant timestamp) {
}
