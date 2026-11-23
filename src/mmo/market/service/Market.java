package mmo.market.service;

import mmo.market.exceptions.AuctionException;
import mmo.market.exceptions.InsufficientFundsException;
import mmo.market.exceptions.OrderException;
import mmo.market.exceptions.TradeException;
import mmo.market.model.Account;
import mmo.market.model.Auction;
import mmo.market.model.Item;
import mmo.market.model.Order;
import mmo.market.model.Rarity;
import mmo.market.model.TradeOffer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * The auction house and the trade desk.
 *
 * Auctions: the item is escrowed when listed; every bid is escrowed from the
 * bidder's purse and the previous bidder is refunded, so gold can never be
 * spent twice. Expired auctions settle: item to the winner, gold to the seller.
 *
 * Trades: two-way offers. Both players must accept before anything moves;
 * everything is re-checked at settlement so a stale offer can never pay out.
 *
 * Orders: a buy/sell order book. Buy orders escrow their gold and sell orders
 * escrow their item until they fill or are cancelled. A new order fills
 * against the best resting order on the other side, at the resting price.
 */
public class Market {

    private static final int MAX_LOG = 50;

    private final AccountManager accounts;
    private final PriceCatalog catalog;
    private final Path saveFile;
    private final List<Auction> auctions = new ArrayList<>();
    private final List<TradeOffer> trades = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final Deque<String> events = new ArrayDeque<>();
    private long nextAuctionId = 1;
    private long nextTradeId = 1;
    private long nextOrderId = 1;

    public Market(AccountManager accounts, PriceCatalog catalog, Path saveFile) {
        this.accounts = accounts;
        this.catalog = catalog;
        this.saveFile = saveFile;
        load();
    }

    // ---------- auctions ----------

    /** Lists an item from the seller's inventory. The item is escrowed in the auction. */
    public synchronized Auction createAuction(Account seller, Item item, int basePrice, int durationSeconds)
            throws AuctionException {
        if (!seller.getInventory().getItems().contains(item)) {
            throw new AuctionException("That item is not in " + seller.getUsername() + "'s inventory.");
        }
        if (basePrice < 1) {
            throw new AuctionException("Base price must be at least 1 gold.");
        }
        if (durationSeconds < 5) {
            throw new AuctionException("Duration must be at least 5 seconds.");
        }
        seller.getInventory().remove(item);
        Auction auction = new Auction(nextAuctionId++, item, seller.getUsername(), basePrice,
                System.currentTimeMillis() + durationSeconds * 1000L);
        auctions.add(auction);
        log(seller.getUsername() + " listed " + item.getName() + " (base " + basePrice + "g, "
                + durationSeconds + "s)");
        accounts.saveAll();
        save();
        return auction;
    }

    /**
     * Places a bid. The gold leaves the bidder's purse immediately (escrow),
     * and whoever they outbid gets their escrowed gold refunded.
     */
    public synchronized void placeBid(Account bidder, long auctionId, int amount)
            throws AuctionException, InsufficientFundsException {
        Auction auction = findAuction(auctionId);
        if (auction == null) {
            throw new AuctionException("No such auction.");
        }
        auction.checkBid(bidder.getUsername(), amount);   // auction rules first
        bidder.withdraw(amount);                          // escrow: throws if the purse is too small
        String previousBidder = auction.getCurrentBidder();
        if (previousBidder != null) {
            Account previous = accounts.find(previousBidder);
            if (previous != null) {
                previous.deposit(auction.getCurrentBid());
                log(previousBidder + " was refunded " + auction.getCurrentBid() + "g");
            }
        }
        auction.applyBid(bidder.getUsername(), amount);
        log(bidder.getUsername() + " bid " + amount + "g on " + auction.getItem().getName());
        accounts.saveAll();
        save();
    }

    /** Settles every expired auction. Called by the BotEngine timer. Returns how many settled. */
    public synchronized int settleExpired() {
        int settled = 0;
        Iterator<Auction> it = auctions.iterator();
        while (it.hasNext()) {
            Auction auction = it.next();
            if (!auction.isExpired()) {
                continue;
            }
            Account seller = accounts.find(auction.getSeller());
            if (auction.getCurrentBidder() != null) {
                Account winner = accounts.find(auction.getCurrentBidder());
                if (winner != null && seller != null) {
                    winner.getInventory().add(auction.getItem());   // item to the winner
                    seller.deposit(auction.getCurrentBid());        // escrowed gold to the seller
                    catalog.recordSale(auction.getItem().getName(), auction.getCurrentBid());
                    log(auction.getItem().getName() + " sold to " + winner.getUsername()
                            + " for " + auction.getCurrentBid() + "g");
                }
            } else if (seller != null) {
                seller.getInventory().add(auction.getItem());       // no bids: item goes back
                log(auction.getItem().getName() + " got no bids and returned to " + seller.getUsername());
            }
            it.remove();
            settled++;
        }
        if (settled > 0) {
            accounts.saveAll();
            save();
        }
        return settled;
    }

    // ---------- trades ----------

    /** Creates a two-way offer. The proposer's accept is implicit in sending it. */
    public synchronized TradeOffer proposeTrade(Account from, String toUser,
            Item fromItem, int fromGold, Item toItem, int toGold) throws TradeException {
        Account to = accounts.find(toUser);
        if (to == null) {
            throw new TradeException("No such player: " + toUser);
        }
        if (to == from) {
            throw new TradeException("You cannot trade with yourself.");
        }
        if (fromGold < 0 || toGold < 0) {
            throw new TradeException("Gold cannot be negative.");
        }
        if (fromItem == null && fromGold == 0) {
            throw new TradeException("Offer something: an item or gold.");
        }
        if (toItem == null && toGold == 0) {
            throw new TradeException("Request something: an item or gold.");
        }
        if (fromItem != null && !from.getInventory().getItems().contains(fromItem)) {
            throw new TradeException("That item is not in your inventory.");
        }
        if (toItem != null && !to.getInventory().getItems().contains(toItem)) {
            throw new TradeException(toUser + " no longer has that item.");
        }
        if (fromGold > from.getGold()) {
            throw new TradeException("You don't have that much gold.");
        }
        TradeOffer trade = new TradeOffer(nextTradeId++, from.getUsername(), to.getUsername(),
                fromItem, fromGold, toItem, toGold);
        trades.add(trade);
        log(from.getUsername() + " offered " + TradeOffer.describe(fromItem, fromGold)
                + " to " + to.getUsername() + " for " + TradeOffer.describe(toItem, toGold));
        save();
        return trade;
    }

    /** One side accepts. When both sides have accepted, the trade settles right away. */
    public synchronized void acceptTrade(TradeOffer trade, Account by)
            throws TradeException, InsufficientFundsException {
        if (trade.getStatus() != TradeOffer.Status.PENDING) {
            throw new TradeException("That trade is no longer open.");
        }
        trade.accept(by.getUsername());
        if (trade.bothAccepted()) {
            settleTrade(trade);
        } else {
            log(by.getUsername() + " accepted the trade, waiting for the other side");
        }
        accounts.saveAll();
        save();
    }

    public synchronized void declineTrade(TradeOffer trade, Account by) {
        trade.setStatus(TradeOffer.Status.DECLINED);
        log(by.getUsername() + " declined the trade");
        save();
    }

    /** Moves gold and items only after BOTH accepts, and re-checks everything first. */
    private void settleTrade(TradeOffer trade) throws TradeException, InsufficientFundsException {
        Account from = accounts.find(trade.getFromUser());
        Account to = accounts.find(trade.getToUser());
        if (from == null || to == null) {
            trade.setStatus(TradeOffer.Status.CANCELLED);
            throw new TradeException("A player in this trade no longer exists.");
        }
        if (trade.getFromItem() != null && !from.getInventory().getItems().contains(trade.getFromItem())) {
            trade.setStatus(TradeOffer.Status.CANCELLED);
            throw new TradeException(from.getUsername() + " no longer has the offered item.");
        }
        if (trade.getToItem() != null && !to.getInventory().getItems().contains(trade.getToItem())) {
            trade.setStatus(TradeOffer.Status.CANCELLED);
            throw new TradeException(to.getUsername() + " no longer has the requested item.");
        }
        from.withdraw(trade.getFromGold());
        to.deposit(trade.getFromGold());
        to.withdraw(trade.getToGold());
        from.deposit(trade.getToGold());
        if (trade.getFromItem() != null) {
            from.getInventory().remove(trade.getFromItem());
            to.getInventory().add(trade.getFromItem());
        }
        if (trade.getToItem() != null) {
            to.getInventory().remove(trade.getToItem());
            from.getInventory().add(trade.getToItem());
        }
        trade.setStatus(TradeOffer.Status.COMPLETED);
        log("Trade completed: " + from.getUsername() + " gave " + TradeOffer.describe(trade.getFromItem(), trade.getFromGold())
                + ", " + to.getUsername() + " gave " + TradeOffer.describe(trade.getToItem(), trade.getToGold()));
    }

    // ---------- orders (the buy/sell order book) ----------

    /**
     * Places a buy order: the offered gold is escrowed immediately. If a sell
     * order at or below the offer exists, the trade fills right away at the
     * resting order's price and the buyer is refunded the difference.
     * Returns the resting order, or null when it filled immediately.
     */
    public synchronized Order placeBuyOrder(Account buyer, String itemName, int price)
            throws OrderException, InsufficientFundsException {
        if (catalog.findByName(itemName) == null) {
            throw new OrderException("Unknown item: " + itemName);
        }
        if (price < 1) {
            throw new OrderException("Price must be at least 1 gold.");
        }
        Order match = getBestSell(itemName);
        if (match != null && (match.getPrice() > price
                || match.getOwner().equalsIgnoreCase(buyer.getUsername()))) {
            match = null;   // too expensive, or your own listing
        }
        buyer.withdraw(price);   // escrow the full offer
        Order resting = null;
        if (match == null) {
            resting = new Order(nextOrderId++, Order.Side.BUY, itemName, price, buyer.getUsername());
            orders.add(resting);
            log(buyer.getUsername() + " bids " + price + "g for " + itemName + " (buy order)");
        } else {
            Account seller = accounts.find(match.getOwner());
            orders.remove(match);
            buyer.getInventory().add(catalogItem(match.getItemName()));
            if (seller != null) {
                seller.deposit(match.getPrice());
            }
            if (price > match.getPrice()) {
                buyer.deposit(price - match.getPrice());   // refund what was not needed
            }
            catalog.recordSale(match.getItemName(), match.getPrice());
            log("Order filled: " + buyer.getUsername() + " bought " + match.getItemName()
                    + " from " + match.getOwner() + " for " + match.getPrice() + "g");
        }
        accounts.saveAll();
        save();
        return resting;
    }

    /**
     * Places a sell order: the item is escrowed (leaves the inventory) right
     * away, and comes back if the order is cancelled. If a buy order at or
     * above the ask exists, the trade fills right away at the resting price.
     * Returns the resting order, or null when it filled immediately.
     */
    public synchronized Order placeSellOrder(Account seller, Item item, int price)
            throws OrderException {
        if (!seller.getInventory().getItems().contains(item)) {
            throw new OrderException("That item is not in " + seller.getUsername() + "'s inventory.");
        }
        if (price < 1) {
            throw new OrderException("Price must be at least 1 gold.");
        }
        Order match = getBestBuy(item.getName());
        if (match != null && (match.getPrice() < price
                || match.getOwner().equalsIgnoreCase(seller.getUsername()))) {
            match = null;
        }
        seller.getInventory().remove(item);   // escrow the goods
        Order resting = null;
        if (match == null) {
            resting = new Order(nextOrderId++, Order.Side.SELL, item.getName(), price, seller.getUsername());
            orders.add(resting);
            log(seller.getUsername() + " asks " + price + "g for " + item.getName() + " (sell order)");
        } else {
            Account buyer = accounts.find(match.getOwner());
            orders.remove(match);
            seller.deposit(match.getPrice());   // the resting buy sets the price
            if (buyer != null) {
                buyer.getInventory().add(item);
            }
            catalog.recordSale(item.getName(), match.getPrice());
            log("Order filled: " + seller.getUsername() + " sold " + item.getName()
                    + " to " + match.getOwner() + " for " + match.getPrice() + "g");
        }
        accounts.saveAll();
        save();
        return resting;
    }

    /** Cancels your own open order. The escrowed gold or item comes back. */
    public synchronized void cancelOrder(long orderId, Account by) throws OrderException {
        Order order = findOrder(orderId);
        if (order == null || !order.getOwner().equalsIgnoreCase(by.getUsername())) {
            throw new OrderException("That is not your open order.");
        }
        orders.remove(order);
        if (order.getSide() == Order.Side.BUY) {
            by.deposit(order.getPrice());                         // escrowed gold back
        } else {
            by.getInventory().add(catalogItem(order.getItemName()));   // escrowed item back
        }
        log(by.getUsername() + " cancelled the "
                + order.getSide().toString().toLowerCase() + " order for " + order.getItemName());
        accounts.saveAll();
        save();
    }

    /** A fresh inventory copy of a catalog item (for fills and cancellations). */
    private Item catalogItem(String itemName) {
        Item listed = catalog.findByName(itemName);
        return new Item(listed.getName(), listed.getRarity(), listed.getLastSoldPrice());
    }

    // ---------- lookups, log, persistence ----------

    public synchronized Auction findAuction(long id) {
        for (Auction auction : auctions) {
            if (auction.getId() == id) {
                return auction;
            }
        }
        return null;
    }

    public synchronized List<Auction> getAuctions() {
        return new ArrayList<>(auctions);
    }

    public synchronized List<TradeOffer> getTrades() {
        return new ArrayList<>(trades);
    }

    public synchronized Order findOrder(long id) {
        for (Order order : orders) {
            if (order.getId() == id) {
                return order;
            }
        }
        return null;
    }

    public synchronized List<Order> getOrders() {
        return new ArrayList<>(orders);
    }

    /** The cheapest open sell order for an item, or null. Ties go to the older order. */
    public synchronized Order getBestSell(String itemName) {
        Order best = null;
        for (Order order : orders) {
            if (order.getSide() == Order.Side.SELL && order.getItemName().equalsIgnoreCase(itemName)
                    && (best == null || order.getPrice() < best.getPrice()
                        || (order.getPrice() == best.getPrice() && order.getId() < best.getId()))) {
                best = order;
            }
        }
        return best;
    }

    /** The highest open buy order for an item, or null. Ties go to the older order. */
    public synchronized Order getBestBuy(String itemName) {
        Order best = null;
        for (Order order : orders) {
            if (order.getSide() == Order.Side.BUY && order.getItemName().equalsIgnoreCase(itemName)
                    && (best == null || order.getPrice() > best.getPrice()
                        || (order.getPrice() == best.getPrice() && order.getId() < best.getId()))) {
                best = order;
            }
        }
        return best;
    }

    /** The item catalog (names, rarities, past prices), for choosing what to order. */
    public List<Item> getCatalogItems() {
        return catalog.getItems();
    }

    public synchronized List<String> getRecentEvents(int n) {
        List<String> list = new ArrayList<>(events);
        return list.subList(Math.max(0, list.size() - n), list.size());
    }

    /** Adds a line to the market events log (used by world events). */
    public synchronized void announce(String event) {
        log(event);
    }

    private void log(String event) {
        events.addLast(event);
        while (events.size() > MAX_LOG) {
            events.removeFirst();
        }
    }

    private void save() {
        try {
            if (saveFile.getParent() != null) {
                Files.createDirectories(saveFile.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(saveFile)) {
                for (Auction auction : auctions) {
                    writer.write(auction.toRecord());
                    writer.newLine();
                }
                for (TradeOffer trade : trades) {
                    if (trade.getStatus() == TradeOffer.Status.PENDING) {
                        writer.write(trade.toRecord());
                        writer.newLine();
                    }
                }
                for (Order order : orders) {
                    writer.write(order.toRecord());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save market to " + saveFile, e);
        }
    }

    private void load() {
        try (BufferedReader reader = Files.newBufferedReader(saveFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p[0].equals("AUCTION") && p.length == 10) {
                    Item item = new Item(p[2], Rarity.valueOf(p[3]), Integer.parseInt(p[4]));
                    Auction auction = new Auction(Long.parseLong(p[1]), item, p[5],
                            Integer.parseInt(p[6]), Integer.parseInt(p[7]),
                            p[8].equals("-") ? null : p[8], Long.parseLong(p[9]));
                    auctions.add(auction);
                    nextAuctionId = Math.max(nextAuctionId, auction.getId() + 1);
                } else if (p[0].equals("TRADE") && p.length == 11) {
                    Item fromItem = p[4].equals("-") ? null : catalog.findByName(p[4]);
                    Item toItem = p[6].equals("-") ? null : catalog.findByName(p[6]);
                    TradeOffer trade = new TradeOffer(Long.parseLong(p[1]), p[2], p[3],
                            fromItem, Integer.parseInt(p[5]), toItem, Integer.parseInt(p[7]));
                    trade.setStatus(TradeOffer.Status.valueOf(p[10]));
                    if (trade.getStatus() == TradeOffer.Status.PENDING) {
                        trades.add(trade);
                        nextTradeId = Math.max(nextTradeId, trade.getId() + 1);
                    }
                } else if (p[0].equals("ORDER") && p.length == 6) {
                    Order order = new Order(Long.parseLong(p[1]), Order.Side.valueOf(p[2]),
                            p[3], Integer.parseInt(p[4]), p[5]);
                    orders.add(order);
                    nextOrderId = Math.max(nextOrderId, order.getId() + 1);
                }
            }
        } catch (NoSuchFileException e) {
            // first run: nothing to load
        } catch (IOException e) {
            System.err.println("Could not read " + saveFile + ": " + e.getMessage());
        }
    }
}

