package com.califorge.msorden.dto;

/**
 * Mensaje de orden publicado en RabbitMQ (calisat.exchange) hacia
 * calisat-ms-notificaciones. Debe coincidir con la estructura JSON que
 * consume el listener del receptor.
 *
 * @param ordenId identificador de la orden
 * @param usuarioSub sub Azure del dueño de la orden (destinatario)
 * @param evento tipo de evento: ORDEN_CONFIRMADA u ORDEN_CANCELADA
 * @param estado estado de la orden en el momento de publicar
 * @param total total de la orden (texto, por BigDecimal)
 */
public record OrdenMensaje(
        String ordenId,
        String usuarioSub,
        String evento,
        String estado,
        String total) {
}
