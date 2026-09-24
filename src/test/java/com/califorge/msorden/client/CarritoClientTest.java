package com.califorge.msorden.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CarritoClient cliente() {
        return new CarritoClient(restTemplate, "http://localhost:8084");
    }

    @Test
    void buscarPorUsuario_devuelveElCarritoCuandoResponde() {
        UUID id = UUID.randomUUID();
        CarritoDto carrito = new CarritoDto(id, "sub-1", "ABIERTO", List.of(), LocalDateTime.now());
        when(restTemplate.getForObject(
                "http://localhost:8084/api/v1/carrito?usuarioSub={usuarioSub}&estado={estado}",
                CarritoDto.class, "sub-1", "ABIERTO"))
                .thenReturn(carrito);

        Optional<CarritoDto> resultado = cliente().buscarPorUsuario("sub-1", "ABIERTO");

        assertTrue(resultado.isPresent());
        assertEquals(id, resultado.get().id());
        assertEquals("ABIERTO", resultado.get().estado());
    }

    @Test
    void buscarPorUsuario_degradaAVacioSiElCarritoEstaCaido() {
        when(restTemplate.getForObject(
                "http://localhost:8084/api/v1/carrito?usuarioSub={usuarioSub}&estado={estado}",
                CarritoDto.class, "sub-1", "ABIERTO"))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertTrue(cliente().buscarPorUsuario("sub-1", "ABIERTO").isEmpty());
    }

    @Test
    void vaciar_noLanzaExcepcionSiElCarritoEstaCaido() {
        doThrow(new ResourceAccessException("Connection refused"))
                .when(restTemplate)
                .delete("http://localhost:8084/api/v1/carrito?usuarioSub={usuarioSub}", "sub-1");

        assertDoesNotThrow(() -> cliente().vaciar("sub-1"));
        verify(restTemplate).delete(
                "http://localhost:8084/api/v1/carrito?usuarioSub={usuarioSub}", "sub-1");
    }
}
