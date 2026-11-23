package mmo.market.exceptions;

/** Thrown when a trade offer is invalid or can no longer go through. */
public class TradeException extends Exception {
    public TradeException(String message) {
        super(message);
    }
}

