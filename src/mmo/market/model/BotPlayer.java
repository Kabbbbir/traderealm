package mmo.market.model;

/**
 * The 10 auto-bidders. A BotPlayer IS a Player (inheritance), but it is
 * driven by the program (the BotEngine) instead of the login screen.
 *
 * Each bot has an aggression factor: the most it will pay for an item is
 * the item's past price times its aggression. Because the 10 bots have
 * different aggression values, they keep outbidding each other until the
 * price passes all but one of their limits.
 */
public class BotPlayer extends Player {

    private final double aggression;

    public BotPlayer(String username, String passwordHash, int gold, double aggression) {
        super(username, passwordHash, gold);
        this.aggression = aggression;
    }

    /** The most this bot will pay for the item. */
    public int maxBidFor(Item item) {
        return (int) (item.getLastSoldPrice() * aggression);
    }

    public double getAggression() {
        return aggression;
    }

    @Override
    public String getRole() {
        return "BOT";
    }

    @Override
    protected String extraData() {
        return String.valueOf(aggression);
    }
}

