package com.califorge.msorden.exception;

/**
 * Lanzada cuando el texto enviado como estado no corresponde a ningun
 * valor del enum EstadoOrden. Se traduce a HTTP 400 Bad Request.
 */
public class EstadoInvalidoException extends RuntimeException {

    public EstadoInvalidoException(String valor) {
        super("Estado invalido: " + valor);
    }
}
