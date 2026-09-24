package com.califorge.msorden.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrdenCreateRequest(
        @Schema(description = "Lineas de la orden. Los precios vienen como snapshot del cliente.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "items no puede estar vacio")
        @Valid
        List<OrdenItemRequest> items,

        @Schema(description = "Calle de envio (snapshot, opcional).", example = "Av. Siempre Viva 742", maxLength = 200)
        @Size(max = 200, message = "direccionCalle no puede superar 200 caracteres")
        String direccionCalle,

        @Schema(description = "Ciudad de envio (snapshot, opcional).", example = "Madrid", maxLength = 100)
        @Size(max = 100, message = "direccionCiudad no puede superar 100 caracteres")
        String direccionCiudad,

        @Schema(description = "Pais de envio (snapshot, opcional).", example = "Espana", maxLength = 100)
        @Size(max = 100, message = "direccionPais no puede superar 100 caracteres")
        String direccionPais,

        @Schema(description = "Codigo postal de envio (snapshot, opcional).", example = "28001", maxLength = 20)
        @Size(max = 20, message = "direccionCodigoPostal no puede superar 20 caracteres")
        String direccionCodigoPostal,

        @Schema(description = "Clave de idempotencia opcional en el body. Si tambien llega el header Idempotency-Key, este tiene prioridad.", example = "orden-2026-001", maxLength = 100)
        @Size(max = 100, message = "idempotencyKey no puede superar 100 caracteres")
        String idempotencyKey) {
}
