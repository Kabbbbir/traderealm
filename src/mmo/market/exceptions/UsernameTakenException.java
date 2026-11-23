package mmo.market.exceptions;

/** Thrown when someone registers a username that already exists. */
public class UsernameTakenException extends AuthException {
    public UsernameTakenException(String username) {
        super("The username '" + username + "' is already taken.");
    }
}

