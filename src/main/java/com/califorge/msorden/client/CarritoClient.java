package com.califorge.msorden.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cliente HTTP de calisat-ms-carrito (fase B). Sin service discovery:
 * base URL = CALISAT_CARRITO_URL (default http://localhost:8084).
 *
 * <p>Resiliencia: toda operacion degrada (Optional vacio o log) si el
 * carrito esta caido; jamas interrumpe el flujo principal de la orden.</p>
 */
@Component
public class CarritoClient {

    private static final Logger log = LoggerFactory.getLogger(CarritoClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CarritoClient(RestTemplate restTemplate,
                         @Value("${CALISAT_CARRITO_URL:http://localhost:8084}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Lee el carrito de un usuario (uso interno: la orden lo consulta
     * antes/tras el checkout segun el diseno).
     */
    public Optional<CarritoDto> buscarPorUsuario(String usuarioSub, String estado) {
        try {
            CarritoDto carrito = restTemplate.getForObject(
                    baseUrl + "/api/v1/carrito?usuarioSub={usuarioSub}&estado={estado}",
                    CarritoDto.class, usuarioSub, estado);
            return Optional.ofNullable(carrito);
        } catch (RestClientException ex) {
            log.warn("Carrito no disponible para el usuario '{}' ({}): se omite la lectura",
                    usuarioSub, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Vacia el carrito tras confirmar el checkout (operacion idempotente).
     * Si el carrito esta caido solo se registra el fallo: la orden ya existe.
     */
    public void vaciar(String usuarioSub) {
        try {
            restTemplate.delete(baseUrl + "/api/v1/carrito?usuarioSub={usuarioSub}", usuarioSub);
        } catch (RestClientException ex) {
            log.warn("No se pudo vaciar el carrito del usuario '{}' ({}): el checkout continua",
                    usuarioSub, ex.getMessage());
        }
    }
}
