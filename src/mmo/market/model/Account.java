package mmo.market.model;

import mmo.market.exceptions.InsufficientFundsException;
import mmo.market.service.Passwords;

/**
 * Base class for every account in the simulator, human or bot.
 * It is abstract: you cannot make a plain "Account", only a
 * concrete subclass such as Player (and later BotPlayer).
 *
 * Holds the login data plus the purse (gold) and the inventory,
 * because both bots and humans need those for auctions and trades.
 */
public abstract class Account implements Storable, Authenticatable {

    /** Every new account starts with this much gold. */
    public static final int STARTING_GOLD = 1000;

    private final String username;
    private final String passwordHash;   // we never store the raw password
    private int gold;
    private final Inventory inventory = new Inventory();

    protected Account(String username, String passwordHash, int gold) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.gold = gold;
    }

    /** Each subclass says what it is: "PLAYER", "BOT", ... (polymorphism). */
    public abstract String getRole();

    /** Extra data a subclass wants saved, e.g. a bot's aggression. Empty by default. */
    protected String extraData() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean checkPassword(String rawPassword) {
        return passwordHash.equals(Passwords.sha256(rawPassword));
    }

    public int getGold() {
        return gold;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void deposit(int amount) {
        gold += amount;
    }

    /** Bids and trade payments go through this so a purse can never go negative. */
    public void withdraw(int amount) throws InsufficientFundsException {
        if (amount > gold) {
            throw new InsufficientFundsException(
                    username + " has only " + gold + " gold, cannot pay " + amount + ".");
        }
        gold -= amount;
    }

    /**
     * File format: ROLE|username|passwordHash|gold|extra|item;item;...
     * where each item is name,rarity,lastPrice. Item names never contain
     * the separators , ; | (they come from the price catalog).
     */
    @Override
    public String toRecord() {
        StringBuilder sb = new StringBuilder();
        sb.append(getRole()).append('|').append(username).append('|')
          .append(passwordHash).append('|').append(gold).append('|')
          .append(extraData()).append('|');
        boolean first = true;
        for (Item item : inventory.getItems()) {
            if (!first) {
                sb.append(';');
            }
            sb.append(item.getName()).append(',').append(item.getRarity()).append(',')
              .append(item.getLastSoldPrice());
            first = false;
        }
        return sb.toString();
    }
}

