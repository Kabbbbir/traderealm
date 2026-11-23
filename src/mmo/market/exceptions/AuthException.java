package mmo.market.exceptions;

/** Base class for every account/register/login problem. */
public class AuthException extends Exception {
    public AuthException(String message) {
        super(message);
    }
}

