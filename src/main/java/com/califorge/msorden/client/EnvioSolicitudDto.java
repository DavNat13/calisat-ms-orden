package com.califorge.msorden.client;

import java.util.UUID;

/**
 * Solicitud de creacion de envio en calisat-ms-envios
 * (POST /api/v1/envios); espejo minimo del EnvioRequest del MS de envios.
 */
public record EnvioSolicitudDto(
        UUID ordenId,
        String usuarioSub,
        String direccionCalle,
        String direccionCiudad,
        String direccionPais,
        String direccionCodigoPostal,
        String transportista) {
}
