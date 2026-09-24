package com.califorge.msorden.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente HTTP de calisat-ms-inventario (fase B): reserva, libera y confirma
 * stock. Sin service discovery: base URL = CALISAT_INVENTARIO_URL
 * (default http://localhost:8083).
 *
 * <p>Resiliencia: cada operacion es best-effort (try/catch + log). Si el
 * inventario esta caido, el flujo principal de la orden NO se interrumpe
 * (saga con compensacion manual; Resilience4j es fase posterior).</p>
 */
@Component
public class InventarioClient {

    private static final Logger log = LoggerFactory.getLogger(InventarioClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventarioClient(RestTemplate restTemplate,
                            @Value("${CALISAT_INVENTARIO_URL:http://localhost:8083}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void reservar(String sku, int cantidad, String refOrden) {
        movimiento("reservar", sku, cantidad, refOrden);
    }

    public void liberar(String sku, int cantidad, String refOrden) {
        movimiento("liberar", sku, cantidad, refOrden);
    }

    public void confirmar(String sku, int cantidad, String refOrden) {
        movimiento("confirmar", sku, cantidad, refOrden);
    }

    private void movimiento(String accion, String sku, int cantidad, String refOrden) {
        if (cantidad <= 0) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("cantidad", cantidad);
            body.put("refOrden", refOrden);
            restTemplate.postForObject(
                    baseUrl + "/api/v1/stock/{sku}/" + accion, body, Object.class, sku);
        } catch (RestClientException ex) {
            log.warn("Inventario '{}': SKU '{}' ref '{}' no disponible ({}): el flujo principal continua",
                    accion, sku, refOrden, ex.getMessage());
        }
    }
}
