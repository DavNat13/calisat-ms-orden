package com.califorge.msorden.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrdenItemRequest(
        @Schema(description = "SKU del producto.", example = "ANILLAS-001", maxLength = 64)
        @NotBlank(message = "sku es obligatorio")
        @Size(max = 64, message = "sku no puede superar 64 caracteres")
        String sku,

        @Schema(description = "Nombre del producto (snapshot).", example = "Anillas de madera Pro", maxLength = 200)
        @Size(max = 200, message = "nombreProducto no puede superar 200 caracteres")
        String nombreProducto,

        @Schema(description = "Cantidad solicitada (minimo 1).", example = "2")
        @NotNull(message = "cantidad es obligatoria")
        @Min(value = 1, message = "cantidad debe ser al menos 1")
        Integer cantidad,

        @Schema(description = "Precio unitario al momento de la compra (snapshot, mayor a 0).", example = "19.99")
        @NotNull(message = "precioUnitario es obligatorio")
        @Positive(message = "precioUnitario debe ser mayor a 0")
        BigDecimal precioUnitario) {
}
