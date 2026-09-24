package com.califorge.msorden.client;

import java.util.UUID;

/**
 * Respuesta minima de creacion de envio (EnvioResponse de calisat-ms-envios);
 * solo los campos que consume la orden.
 */
public record EnvioCreadoDto(
        UUID id,
        String numeroGuia,
        String estado) {
}
