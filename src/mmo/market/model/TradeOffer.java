package mmo.market.model;

import mmo.market.exceptions.TradeException;

/**
 * A two-way trade: the proposer offers something and requests something back.
 * BOTH players must accept before any item or gold moves. Making the offer
 * counts as the proposer's accept; the partner accepts in the Trades tab.
 */
public class TradeOffer implements Storable {

    public enum Status { PENDING, COMPLETED, DECLINED, CANCELLED }

    private final long id;
    private final String fromUser;
    private final String toUser;
    private final Item fromItem;    // what the proposer gives (null = nothing)
    private final int fromGold;     // gold the proposer adds
    private final Item toItem;      // what the partner gives (null = nothing)
    private final int toGold;       // gold the partner adds
    private boolean fromAccepted = true;   // sending the offer = accepting your own side
    private boolean toAccepted;
    private Status status = Status.PENDING;

    public TradeOffer(long id, String fromUser, String toUser,
                      Item fromItem, int fromGold, Item toItem, int toGold) {
        this.id = id;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.fromItem = fromItem;
        this.fromGold = fromGold;
        this.toItem = toItem;
        this.toGold = toGold;
    }

    /** Marks one side as accepted. The Market settles once bothAccepted() is true. */
    public void accept(String username) throws TradeException {
        if (username.equalsIgnoreCase(fromUser)) {
            fromAccepted = true;
        } else if (username.equalsIgnoreCase(toUser)) {
            toAccepted = true;
        } else {
            throw new TradeException(username + " is not part of this trade.");
        }
    }

    public boolean bothAccepted() {
        return fromAccepted && toAccepted;
    }

    public boolean involves(String username) {
        return fromUser.equalsIgnoreCase(username) || toUser.equalsIgnoreCase(username);
    }

    /** Human-readable side of a deal, e.g. "Stormblade + 50g". */
    public static String describe(Item item, int gold) {
        if (item == null && gold == 0) return "nothing";
        if (item == null) return gold + "g";
        if (gold == 0) return item.getName();
        return item.getName() + " + " + gold + "g";
    }

    public long getId()          { return id; }
    public String getFromUser()  { return fromUser; }
    public String getToUser()    { return toUser; }
    public Item getFromItem()    { return fromItem; }
    public int getFromGold()     { return fromGold; }
    public Item getToItem()      { return toItem; }
    public int getToGold()       { return toGold; }
    public Status getStatus()    { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toRecord() {
        return "TRADE|" + id + "|" + fromUser + "|" + toUser + "|"
                + (fromItem == null ? "-" : fromItem.getName()) + "|" + fromGold + "|"
                + (toItem == null ? "-" : toItem.getName()) + "|" + toGold + "|"
                + fromAccepted + "|" + toAccepted + "|" + status;
    }
}

