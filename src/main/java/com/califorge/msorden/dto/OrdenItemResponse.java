package com.califorge.msorden.dto;

import com.califorge.msorden.model.OrdenItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record OrdenItemResponse(
        @Schema(description = "Identificador de la linea de la orden.", example = "3f1d0f6e-8f1e-4f2a-9c3d-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "SKU del producto.", example = "ANILLAS-001")
        String sku,

        @Schema(description = "Nombre del producto (snapshot).", example = "Anillas de madera Pro")
        String nombreProducto,

        @Schema(description = "Cantidad.", example = "2")
        int cantidad,

        @Schema(description = "Precio unitario snapshot.", example = "19.99")
        BigDecimal precioUnitario,

        @Schema(description = "Subtotal de la linea (cantidad x precioUnitario).", example = "39.98")
        BigDecimal subtotal) {

    public static OrdenItemResponse desde(OrdenItem item) {
        return new OrdenItemResponse(
                item.getId(),
                item.getSku(),
                item.getNombreProducto(),
                item.getCantidad(),
                item.getPrecioUnitario(),
                item.getSubtotal());
    }
}
