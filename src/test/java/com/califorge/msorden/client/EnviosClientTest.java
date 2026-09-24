package com.califorge.msorden.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnviosClientTest {

    @Mock
    private RestTemplate restTemplate;

    private EnviosClient cliente() {
        return new EnviosClient(restTemplate, "http://localhost:8086");
    }

    @Test
    void crear_devuelveElEnvioCreado() {
        UUID ordenId = UUID.randomUUID();
        UUID envioId = UUID.randomUUID();
        EnvioSolicitudDto solicitud = new EnvioSolicitudDto(
                ordenId, "sub-1", "Calle 1", "Madrid", "Espana", "28001", null);
        when(restTemplate.postForObject(
                "http://localhost:8086/api/v1/envios", solicitud, EnvioCreadoDto.class))
                .thenReturn(new EnvioCreadoDto(envioId, "CAL-ABC123", "CREADO"));

        Optional<EnvioCreadoDto> resultado = cliente().crear(solicitud);

        assertTrue(resultado.isPresent());
        assertEquals(envioId, resultado.get().id());
        assertEquals("CAL-ABC123", resultado.get().numeroGuia());
        verify(restTemplate).postForObject(
                eq("http://localhost:8086/api/v1/envios"), eq(solicitud), eq(EnvioCreadoDto.class));
    }

    @Test
    void crear_degradaAVacioSiEnviosEstaCaido() {
        EnvioSolicitudDto solicitud = new EnvioSolicitudDto(
                UUID.randomUUID(), "sub-1", null, null, null, null, null);
        when(restTemplate.postForObject(
                "http://localhost:8086/api/v1/envios", solicitud, EnvioCreadoDto.class))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertTrue(cliente().crear(solicitud).isEmpty());
    }
}
