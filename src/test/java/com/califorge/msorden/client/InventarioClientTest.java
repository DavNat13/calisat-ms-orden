package com.califorge.msorden.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioClientTest {

    @Mock
    private RestTemplate restTemplate;

    private InventarioClient cliente() {
        return new InventarioClient(restTemplate, "http://localhost:8083");
    }

    @Test
    void reservar_posteaElCuerpoAlEndpointDeReserva() {
        cliente().reservar("SKU-1", 3, "orden-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(
                eq("http://localhost:8083/api/v1/stock/{sku}/reservar"),
                captor.capture(),
                eq(Object.class),
                eq("SKU-1"));
        assertEquals(3, captor.getValue().get("cantidad"));
        assertEquals("orden-1", captor.getValue().get("refOrden"));
    }

    @Test
    void liberar_y_confirmar_usanSusEndpointsRespectivos() {
        cliente().liberar("SKU-2", 1, "orden-2");
        cliente().confirmar("SKU-2", 1, "orden-2");

        verify(restTemplate).postForObject(
                eq("http://localhost:8083/api/v1/stock/{sku}/liberar"), any(), eq(Object.class), eq("SKU-2"));
        verify(restTemplate).postForObject(
                eq("http://localhost:8083/api/v1/stock/{sku}/confirmar"), any(), eq(Object.class), eq("SKU-2"));
    }

    @Test
    void movimientos_noLanzanExcepcionSiInventarioEstaCaido() {
        when(restTemplate.postForObject(anyString(), any(), eq(Object.class), anyString()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertDoesNotThrow(() -> {
            cliente().reservar("SKU-1", 2, "orden-1");
            cliente().liberar("SKU-1", 2, "orden-1");
            cliente().confirmar("SKU-1", 2, "orden-1");
        });
    }
}
