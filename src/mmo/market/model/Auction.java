package mmo.market.model;

import mmo.market.exceptions.AuctionException;

/**
 * One auction: an item, a base price and a time limit.
 * While it runs, the item is escrowed (taken out of the seller's inventory)
 * and every bid is escrowed too (taken out of the bidder's purse by the Market).
 */
public class Auction implements Storable {

    private final long id;
    private final Item item;
    private final String seller;
    private final int basePrice;
    private int currentBid;          // 0 while nobody has bid yet
    private String currentBidder;    // null while nobody has bid yet
    private final long endsAtMillis;

    /** New auction created in the UI or by a bot. */
    public Auction(long id, Item item, String seller, int basePrice, long endsAtMillis) {
        this(id, item, seller, basePrice, 0, null, endsAtMillis);
    }

    /** Full constructor, used when loading back from the save file. */
    public Auction(long id, Item item, String seller, int basePrice,
                   int currentBid, String currentBidder, long endsAtMillis) {
        this.id = id;
        this.item = item;
        this.seller = seller;
        this.basePrice = basePrice;
        this.currentBid = currentBid;
        this.currentBidder = currentBidder;
        this.endsAtMillis = endsAtMillis;
    }

    /** Checks the auction rules for a bid. The Market escrows the gold after this passes. */
    public void checkBid(String bidder, int amount) throws AuctionException {
        if (isExpired()) {
            throw new AuctionException("This auction is over.");
        }
        if (bidder.equalsIgnoreCase(seller)) {
            throw new AuctionException("You cannot bid on your own auction.");
        }
        int minimum = currentBid == 0 ? basePrice : currentBid + 1;
        if (amount < minimum) {
            throw new AuctionException("Bid must be at least " + minimum + " gold.");
        }
    }

    /** Called by the Market only, after the bid gold is safely escrowed. */
    public void applyBid(String bidder, int amount) {
        this.currentBid = amount;
        this.currentBidder = bidder;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endsAtMillis;
    }

    public long getSecondsLeft() {
        long left = (endsAtMillis - System.currentTimeMillis()) / 1000;
        return Math.max(0, left);
    }

    public long getId()           { return id; }
    public Item getItem()         { return item; }
    public String getSeller()     { return seller; }
    public int getBasePrice()     { return basePrice; }
    public int getCurrentBid()    { return currentBid; }
    public String getCurrentBidder() { return currentBidder; }
    public long getEndsAtMillis() { return endsAtMillis; }

    @Override
    public String toRecord() {
        return "AUCTION|" + id + "|" + item.getName() + "|" + item.getRarity() + "|"
                + item.getLastSoldPrice() + "|" + seller + "|" + basePrice + "|"
                + currentBid + "|" + (currentBidder == null ? "-" : currentBidder) + "|" + endsAtMillis;
    }
}

