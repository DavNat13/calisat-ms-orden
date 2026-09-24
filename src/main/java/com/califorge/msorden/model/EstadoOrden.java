package com.califorge.msorden.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.califorge.msorden.exception.EstadoInvalidoException;

/**
 * Estados posibles de una orden. Es una maquina de estados: no toda
 * combinacion de origen/destino es valida (ver OrdenService.TRANSICIONES_PERMITIDAS).
 */
public enum EstadoOrden {

    PENDIENTE,
    PAGADA,
    EN_PREPARACION,
    ENVIADA,
    ENTREGADA,
    CANCELADA,
    FALLO_PAGO;

    /** Estados desde los cuales la orden ya no admite mas transiciones. */
    public static Set<EstadoOrden> estadosFinales() {
        return EnumSet.of(ENTREGADA, CANCELADA, FALLO_PAGO);
    }

    /**
     * Convierte un texto (p.ej. JSON) al enum, insensible a mayusculas.
     *
     * @param valor texto con el nombre del estado, p.ej. "EN_PREPARACION"
     * @return el estado correspondiente
     * @throws EstadoInvalidoException si el texto no corresponde a ningun estado
     */
    public static EstadoOrden desdeTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new EstadoInvalidoException(String.valueOf(valor));
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new EstadoInvalidoException(valor);
        }
    }
}
