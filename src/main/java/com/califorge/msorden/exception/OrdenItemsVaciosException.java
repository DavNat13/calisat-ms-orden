package com.califorge.msorden.exception;

/**
 * Lanzada cuando una orden se intenta crear sin lineas de items.
 * Se traduce a HTTP 400 Bad Request.
 */
public class OrdenItemsVaciosException extends RuntimeException {

    public OrdenItemsVaciosException() {
        super("La orden debe incluir al menos un item");
    }
}
