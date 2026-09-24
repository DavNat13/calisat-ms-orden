package com.califorge.msorden.dto;

import com.califorge.msorden.model.EstadoOrden;
import com.califorge.msorden.model.Orden;
import com.califorge.msorden.model.OrdenItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrdenResponse(
        @Schema(description = "Identificador de la orden.", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID id,

        @Schema(description = "sub (claim) del usuario autenticado propietario de la orden.")
        String usuarioSub,

        @Schema(description = "Estado actual en la maquina de estados.", example = "PENDIENTE")
        EstadoOrden estado,

        @Schema(description = "Suma de los subtotales de los items.", example = "45.50")
        BigDecimal subtotal,

        @Schema(description = "Total a pagar (en esta fase, igual al subtotal).", example = "45.50")
        BigDecimal total,

        @Schema(description = "Lineas de la orden (snapshot de precios).")
        List<OrdenItemResponse> items,

        @Schema(description = "Calle de envio (snapshot).", example = "Av. Siempre Viva 742")
        String direccionCalle,

        @Schema(description = "Ciudad de envio (snapshot).", example = "Madrid")
        String direccionCiudad,

        @Schema(description = "Pais de envio (snapshot).", example = "Espana")
        String direccionPais,

        @Schema(description = "Codigo postal de envio (snapshot).", example = "28001")
        String direccionCodigoPostal,

        @Schema(description = "Fecha de creacion de la orden.")
        LocalDateTime fechaCreacion) {

    public static OrdenResponse desde(Orden orden, List<OrdenItem> items) {
        List<OrdenItemResponse> lineas = items == null
                ? List.of()
                : items.stream().map(OrdenItemResponse::desde).toList();
        return new OrdenResponse(
                orden.getId(),
                orden.getUsuarioSub(),
                orden.getEstado(),
                orden.getSubtotal(),
                orden.getTotal(),
                lineas,
                orden.getDireccionCalle(),
                orden.getDireccionCiudad(),
                orden.getDireccionPais(),
                orden.getDireccionCodigoPostal(),
                orden.getFechaCreacion());
    }
}
