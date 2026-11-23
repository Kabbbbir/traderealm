package mmo.market.model;

/**
 * The administrator account: a special Player with extra powers in the
 * market window (set catalog prices, trigger world events, inspect every
 * account). Inherits login, purse and inventory from Player; only the role
 * changes, which is what polymorphic getRole() is for.
 */
public class Admin extends Player {

    /** New admin created by WorldSetup seeding. */
    public Admin(String username, String passwordHash) {
        super(username, passwordHash);
    }

    /** Existing admin loaded back from the save file. */
    public Admin(String username, String passwordHash, int gold) {
        super(username, passwordHash, gold);
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }
}

