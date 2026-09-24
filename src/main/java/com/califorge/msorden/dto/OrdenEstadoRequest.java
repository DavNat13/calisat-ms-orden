package com.califorge.msorden.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrdenEstadoRequest(
        @Schema(description = "Estado destino deseado, p.ej. PAGADA, EN_PREPARACION, ENVIADA, ENTREGADA o CANCELADA.", example = "PAGADA")
        @NotBlank(message = "estado es obligatorio")
        @Size(max = 30, message = "estado no puede superar 30 caracteres")
        String estado) {
}
