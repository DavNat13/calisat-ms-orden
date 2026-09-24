package com.califorge.msorden.exception;

import com.califorge.msorden.model.EstadoOrden;

/**
 * Lanzada cuando la transicion solicitada no esta permitida por la
 * maquina de estados de las ordenes. Se traduce a HTTP 409 Conflict.
 */
public class TransicionNoPermitidaException extends RuntimeException {

    public TransicionNoPermitidaException(EstadoOrden desde, EstadoOrden hasta) {
        super("Transicion no permitida: " + desde + " -> " + hasta);
    }
}
