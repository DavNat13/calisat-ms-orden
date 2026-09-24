package com.califorge.msorden.client;

/**
 * Evento hacia calisat-ms-notificaciones (POST /api/v1/notificaciones/eventos);
 * espejo minimo del NotificacionEventoRequest con cadenas en lugar de enums
 * (canal/tipo se serializan como texto en el MS receptor).
 */
public record NotificacionEventoDto(
        String canal,
        String tipo,
        String asunto,
        String cuerpoTexto,
        String cuerpoHtml,
        String destinatarioSub,
        String destinatarioEmail,
        String destinatarioNombre,
        String plantillaCodigo,
        String payloadJson,
        String origenMs,
        String correlacionId) {
}
