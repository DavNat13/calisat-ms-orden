package com.califorge.msorden.exception;

/** Inventario rechazo la reserva (409): no hay stock suficiente para algun item. */
public class StockInsuficienteException extends RuntimeException {

    private final String sku;

    public StockInsuficienteException(String sku) {
        super("Stock insuficiente para el producto '" + sku + "'");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
