package mmo.market.model;

/** A human player who logs in through the account page. */
public class Player extends Account {

    /** New account created at registration. */
    public Player(String username, String passwordHash) {
        super(username, passwordHash, STARTING_GOLD);
    }

    /** Existing account loaded back from the save file. */
    public Player(String username, String passwordHash, int gold) {
        super(username, passwordHash, gold);
    }

    @Override
    public String getRole() {
        return "PLAYER";
    }
}

