package mmo.market.model;

/**
 * An item that can sit in a player's inventory and later be
 * auctioned or traded. The lastSoldPrice is the "past price"
 * the bots look at when they decide their bids.
 *
 * Two Items are equal when their names are equal, so the same
 * item found in an inventory, an auction and the price catalog
 * is treated as one and the same thing.
 */
public class Item {

    private final String name;
    private final Rarity rarity;
    private int lastSoldPrice;

    public Item(String name, Rarity rarity, int lastSoldPrice) {
        this.name = name;
        this.rarity = rarity;
        this.lastSoldPrice = lastSoldPrice;
    }

    public String getName()        { return name; }
    public Rarity getRarity()      { return rarity; }
    public int getLastSoldPrice()  { return lastSoldPrice; }

    /** Updated whenever the item is sold, so price history stays current. */
    public void setLastSoldPrice(int price) { this.lastSoldPrice = price; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Item && ((Item) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + rarity + ")";
    }
}

