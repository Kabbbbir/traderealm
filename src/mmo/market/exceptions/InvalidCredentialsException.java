package mmo.market.exceptions;

/** Thrown when login fails: unknown username or wrong password. */
public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException() {
        super("Wrong username or password.");
    }
}

