package mmo.market.model;

/** Anything that can be logged in with a username and password. */
public interface Authenticatable {

    String getUsername();

    /** True when the raw (typed) password matches the stored hash. */
    boolean checkPassword(String rawPassword);
}

