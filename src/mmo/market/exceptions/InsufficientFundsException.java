package mmo.market.exceptions;

/** Thrown when a bid or payment is bigger than the purse. */
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

