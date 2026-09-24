package com.califorge.msorden.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

/**
 * Cliente HTTP de calisat-ms-notificaciones (fase B): publica eventos
 * (ORDEN_CONFIRMADA, ORDEN_CANCELADA) con cabecera Idempotency-Key para
 * que la ingesta del receptor sea idempotente. Sin service discovery:
 * base URL = CALISAT_NOTIFICACIONES_URL (default http://localhost:8087).
 *
 * <p>Resiliencia (patron del diseno): try/catch que NUNCA rompe la
 * transaccion principal; si notificaciones esta caido, la orden sigue
 * devolviendo su respuesta normal.</p>
 */
@Component
public class NotificacionesClient {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NotificacionesClient(RestTemplate restTemplate,
                                @Value("${CALISAT_NOTIFICACIONES_URL:http://localhost:8087}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Publica un evento en POST /api/v1/notificaciones/eventos.
     * Best-effort: cualquier fallo se registra y el flujo continua.
     *
     * @param idempotencyKey clave de idempotencia (cabecera Idempotency-Key)
     * @param evento payload del evento
     */
    public void publicar(String idempotencyKey, NotificacionEventoDto evento) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                headers.set("Idempotency-Key", idempotencyKey);
            }
            restTemplate.exchange(
                    baseUrl + "/api/v1/notificaciones/eventos",
                    HttpMethod.POST,
                    new HttpEntity<>(evento, headers),
                    String.class);
        } catch (RestClientException ex) {
            log.warn("No se pudo publicar el evento '{}' en notificaciones ({}): el flujo principal continua",
                    evento.correlacionId(), ex.getMessage());
        }
    }
}
