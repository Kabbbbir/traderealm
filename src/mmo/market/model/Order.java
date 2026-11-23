package mmo.market.model;

/**
 * One open order on the order book, for one unit of an item.
 *
 * A BUY order escrows its price in gold when it is placed: the gold leaves
 * the buyer's purse and comes back if the order is cancelled, or partly comes
 * back if it fills at a cheaper price. A SELL order escrows the item itself
 * the same way. The Market matches a new order against the best resting
 * order on the other side; the resting (older) order sets the trade price.
 */
public class Order implements Storable {

    public enum Side { BUY, SELL }

    private final long id;
    private final Side side;
    private final String itemName;
    private final int price;       // limit price per unit, in gold
    private final String owner;

    public Order(long id, Side side, String itemName, int price, String owner) {
        this.id = id;
        this.side = side;
        this.itemName = itemName;
        this.price = price;
        this.owner = owner;
    }

    public long getId()       { return id; }
    public Side getSide()     { return side; }
    public String getItemName() { return itemName; }
    public int getPrice()     { return price; }
    public String getOwner()  { return owner; }

    @Override
    public String toRecord() {
        return "ORDER|" + id + "|" + side + "|" + itemName + "|" + price + "|" + owner;
    }
}

