package mmo.market.exceptions;

/** Thrown when an auction action is not allowed (bid too low, auction over, ...). */
public class AuctionException extends Exception {
    public AuctionException(String message) {
        super(message);
    }
}

