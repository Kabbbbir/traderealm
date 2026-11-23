package mmo.market.exceptions;

/** Thrown when an order cannot be placed or cancelled (bad price, unknown item, not yours). */
public class OrderException extends Exception {
    public OrderException(String message) {
        super(message);
    }
}

