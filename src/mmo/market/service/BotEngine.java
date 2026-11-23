package mmo.market.service;

import mmo.market.exceptions.AuctionException;
import mmo.market.model.Auction;
import mmo.market.model.BotPlayer;
import mmo.market.model.Item;
import mmo.market.model.Order;
import mmo.market.model.TradeOffer;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drives the 10 bot players and the world events. Every 2 seconds it ticks
 * the EventEngine, settles expired auctions, lets bots list fresh items so
 * the auction house never runs dry, lets one bot per auction consider a bid,
 * and answers any trade offers waiting on a bot. One engine is shared by all
 * windows on the same market; each window registers a tick listener. Runs on a
 * Swing Timer, so
 * everything happens on the GUI thread and no extra thread-safety is needed.
 */
public class BotEngine {

    private static final int TICK_MS = 2000;

    private final Market market;
    private final AccountManager accounts;
    private final List<BotPlayer> bots;
    private final EventEngine events;
    private final Random random = new Random();
    private final List<Runnable> tickListeners = new ArrayList<>();
    private final Timer timer;

    public BotEngine(Market market, AccountManager accounts, List<BotPlayer> bots,
                     EventEngine events) {
        this.market = market;
        this.accounts = accounts;
        this.bots = bots;
        this.events = events;
        this.timer = new Timer(TICK_MS, e -> {
            tick();
            // every open window refreshes its tables after each tick
            for (Runnable listener : new ArrayList<>(tickListeners)) {
                listener.run();
            }
        });
    }

    /** Windows register to be told after every tick so they can refresh. */
    public void addTickListener(Runnable listener) {
        tickListeners.add(listener);
    }

    public void removeTickListener(Runnable listener) {
        tickListeners.remove(listener);
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    /** One round of bot activity. Package-visible so tests can call it directly. */
    public void tick() {
        events.tick();
        market.settleExpired();
        maybeList();
        for (Auction auction : market.getAuctions()) {
            maybeBid(auction);
        }
        maybeOrder();
        answerTrades();
    }

    /**
     * Bots work the order book too: they take a fair resting order when one
     * exists (a sell at or below 105% of the past price, or a buy at or above
     * 90%), and otherwise rest their own limit order a little off the past
     * price. Keeps the book busy without ever breaking the purse rule.
     */
    private void maybeOrder() {
        if (random.nextInt(100) >= 65) {
            return;   // only some ticks produce an order
        }
        BotPlayer bot = bots.get(random.nextInt(bots.size()));
        List<Item> catalogItems = market.getCatalogItems();
        List<Order> open = market.getOrders();
        Item item;
        if (!open.isEmpty() && random.nextBoolean()) {
            // look at something already on the book, so orders actually meet
            item = findCatalogItem(catalogItems,
                    open.get(random.nextInt(open.size())).getItemName());
        } else {
            item = catalogItems.get(random.nextInt(catalogItems.size()));
        }
        int past = Math.max(1, item.getLastSoldPrice());
        Item owned = findOwned(bot, item.getName());

        Order cheapestSell = market.getBestSell(item.getName());
        if (cheapestSell != null && !cheapestSell.getOwner().equalsIgnoreCase(bot.getUsername())
                && cheapestSell.getPrice() <= (int) (past * 1.10)
                && cheapestSell.getPrice() <= bot.getGold()) {
            try {
                market.placeBuyOrder(bot, item.getName(), cheapestSell.getPrice());
            } catch (Exception ignored) {
                // someone else took it first; next tick tries again
            }
            return;
        }
        Order highestBuy = market.getBestBuy(item.getName());
        if (highestBuy != null && !highestBuy.getOwner().equalsIgnoreCase(bot.getUsername())
                && highestBuy.getPrice() >= (int) (past * 0.85) && owned != null) {
            try {
                market.placeSellOrder(bot, owned, highestBuy.getPrice());
            } catch (Exception ignored) {
                // the buy order was gone; next tick tries again
            }
            return;
        }

        // nothing worth taking: rest a limit order (one per bot per item)
        if (open.size() >= 14) {
            return;   // the book is full enough
        }
        for (Order order : open) {
            if (order.getOwner().equalsIgnoreCase(bot.getUsername())
                    && order.getItemName().equalsIgnoreCase(item.getName())) {
                return;
            }
        }
        try {
            if (random.nextBoolean() && bot.getGold() > past) {
                int price = Math.max(1, (int) (past * (0.80 + random.nextDouble() * 0.15)));
                market.placeBuyOrder(bot, item.getName(), price);
            } else if (owned != null) {
                int price = Math.max(1, (int) (past * (1.05 + random.nextDouble() * 0.15)));
                market.placeSellOrder(bot, owned, price);
            }
        } catch (Exception ignored) {
            // not enough gold or a stale item; next tick tries again
        }
    }

    /** The catalog entry with this name. */
    private Item findCatalogItem(List<Item> catalogItems, String itemName) {
        for (Item item : catalogItems) {
            if (item.getName().equalsIgnoreCase(itemName)) {
                return item;
            }
        }
        return catalogItems.get(0);   // cannot happen: orders only exist for catalog items
    }

    /** The bot's own copy of an item by name, or null. */
    private Item findOwned(BotPlayer bot, String itemName) {
        for (Item item : bot.getInventory().getItems()) {
            if (item.getName().equalsIgnoreCase(itemName)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Bots put items up for auction when the auction house is running low.
     * Without this, once the seeded auctions have all ended the auction house
     * stays empty forever, because bots otherwise only bid on existing listings.
     */
    private void maybeList() {
        if (market.getAuctions().size() >= 5) {
            return;   // enough on offer already
        }
        List<BotPlayer> sellers = new ArrayList<>();
        for (BotPlayer bot : bots) {
            if (!bot.getInventory().getItems().isEmpty()) {
                sellers.add(bot);
            }
        }
        if (sellers.isEmpty()) {
            return;
        }
        BotPlayer bot = sellers.get(random.nextInt(sellers.size()));
        List<Item> items = bot.getInventory().getItems();
        Item item = items.get(random.nextInt(items.size()));
        int base = Math.max(1, (int) (item.getLastSoldPrice() * 0.7));   // same rule as the seed auctions
        try {
            market.createAuction(bot, item, base, 300 + random.nextInt(600));   // 5 to 15 minutes
        } catch (AuctionException ignored) {
            // item was already listed by someone else; tries again next tick
        }
    }

    /** One random eligible bot considers bidding on this auction. */
    private void maybeBid(Auction auction) {
        List<BotPlayer> candidates = new ArrayList<>();
        for (BotPlayer bot : bots) {
            if (bot.getUsername().equalsIgnoreCase(auction.getSeller())) {
                continue;   // no bidding on your own auction
            }
            if (bot.getUsername().equalsIgnoreCase(
                    auction.getCurrentBidder() == null ? "" : auction.getCurrentBidder())) {
                continue;   // already leading
            }
            candidates.add(bot);
        }
        if (candidates.isEmpty()) {
            return;
        }
        BotPlayer bot = candidates.get(random.nextInt(candidates.size()));
        int max = bot.maxBidFor(auction.getItem());                 // past price x aggression
        int current = auction.getCurrentBid() == 0 ? auction.getBasePrice() - 1 : auction.getCurrentBid();
        if (current >= max) {
            return;                                                 // too rich for this bot
        }
        int bid = Math.min(max, current + 1 + random.nextInt(15));  // small increments
        if (bid > bot.getGold()) {
            return;                                                 // never over the purse
        }
        try {
            market.placeBid(bot, auction.getId(), bid);
        } catch (Exception ignored) {
            // another bot may have acted first; it retries next tick
        }
    }

    /** Bots answer trade offers addressed to them. */
    private void answerTrades() {
        for (TradeOffer trade : market.getTrades()) {
            if (trade.getStatus() != TradeOffer.Status.PENDING) {
                continue;
            }
            BotPlayer bot = findBot(trade.getToUser());
            if (bot == null) {
                continue;
            }
            if (botLikes(trade)) {
                try {
                    market.acceptTrade(trade, bot);
                } catch (Exception ignored) {
                    // trade changed in the meantime; re-evaluated next tick
                }
            } else {
                market.declineTrade(trade, bot);
            }
        }
    }

    /** A bot accepts when what it receives is worth at least 90% of what it gives. */
    private boolean botLikes(TradeOffer trade) {
        int receives = trade.getFromGold() + valueOf(trade.getFromItem());
        int gives = trade.getToGold() + valueOf(trade.getToItem());
        return receives >= gives * 0.9;
    }

    private int valueOf(Item item) {
        return item == null ? 0 : item.getLastSoldPrice();
    }

    private BotPlayer findBot(String username) {
        for (BotPlayer bot : bots) {
            if (bot.getUsername().equalsIgnoreCase(username)) {
                return bot;
            }
        }
        return null;
    }
}

