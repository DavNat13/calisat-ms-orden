package com.califorge.msorden.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionesClientTest {

    @Mock
    private RestTemplate restTemplate;

    private NotificacionesClient cliente() {
        return new NotificacionesClient(restTemplate, "http://localhost:8087");
    }

    private NotificacionEventoDto evento() {
        return new NotificacionEventoDto(
                "EMAIL", "SISTEMA", "Orden confirmada", "Tu orden fue confirmada.", null,
                "sub-1", null, null, null, "{\"ordenId\":\"o-1\"}",
                "calisat-ms-orden", "orden-o-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicar_enviaElEventoConCabeceraIdempotencyKey() {
        cliente().publicar("orden-1-confirmada", evento());

        ArgumentCaptor<HttpEntity<NotificacionEventoDto>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8087/api/v1/notificaciones/eventos"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(String.class));

        HttpEntity<NotificacionEventoDto> entity = captor.getValue();
        assertEquals("orden-1-confirmada", entity.getHeaders().getFirst("Idempotency-Key"));
        assertEquals("Orden confirmada", entity.getBody().asunto());
        assertEquals("calisat-ms-orden", entity.getBody().origenMs());
    }

    @Test
    void publicar_noLanzaExcepcionSiNotificacionesEstaCaido() {
        when(restTemplate.exchange(
                eq("http://localhost:8087/api/v1/notificaciones/eventos"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertDoesNotThrow(() -> cliente().publicar("orden-1-confirmada", evento()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicar_omiteLaCabeceraCuandoNoHayClave() {
        cliente().publicar(null, evento());

        ArgumentCaptor<HttpEntity<NotificacionEventoDto>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8087/api/v1/notificaciones/eventos"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(String.class));
        assertEquals(null, captor.getValue().getHeaders().getFirst("Idempotency-Key"));
    }
}
