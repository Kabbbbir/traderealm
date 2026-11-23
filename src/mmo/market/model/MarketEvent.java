package mmo.market.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A random world event that moves the economy. Each event says which items it
 * hits and how their past prices move; the bots decide their bids from past
 * prices, so their behaviour adapts immediately.
 *
 * This is an enum with fields and behaviour: every constant carries its own
 * data (title, story, price multiplier, target rarities) and the shared logic
 * lives once in the methods below.
 */
public enum MarketEvent {

    DRAGON_ATTACK("Dragon attack",
            "Adventurers gear up: rare and better items are in demand",
            1.40, EnumSet.of(Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY)),
    MERCHANT_CARAVAN("Merchant caravan",
            "A caravan floods the market with cheap goods",
            0.70, EnumSet.of(Rarity.COMMON, Rarity.UNCOMMON)),
    GOLD_RUSH("Gold rush",
            "Fresh gold flows into the realm: everything gets pricier",
            1.25, EnumSet.allOf(Rarity.class)),
    MARKET_CRASH("Market crash",
            "Panic selling: everything gets cheaper",
            0.75, EnumSet.allOf(Rarity.class)),
    SUPPLY_SHORTAGE("Supply shortage",
            "One item suddenly becomes hard to find",
            2.00, null);   // null targets = hits one random item instead

    private final String title;
    private final String story;
    private final double multiplier;
    private final Set<Rarity> targets;   // null = one random item

    MarketEvent(String title, String story, double multiplier, Set<Rarity> targets) {
        this.title = title;
        this.story = story;
        this.multiplier = multiplier;
        this.targets = targets;
    }

    /** The catalog items this event hits (one random item for a shortage). */
    public List<Item> affectedItems(List<Item> catalog, Random random) {
        List<Item> hit = new ArrayList<>();
        if (targets == null) {
            hit.add(catalog.get(random.nextInt(catalog.size())));
            return hit;
        }
        for (Item item : catalog) {
            if (targets.contains(item.getRarity())) {
                hit.add(item);
            }
        }
        return hit;
    }

    /** One line for the market log, naming up to three of the affected items. */
    public String describe(List<Item> hit) {
        StringBuilder sb = new StringBuilder("EVENT: " + title + " - " + story + " (");
        for (int i = 0; i < hit.size() && i < 3; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(hit.get(i).getName());
        }
        if (hit.size() > 3) {
            sb.append(", ...");
        }
        int pct = (int) Math.round((multiplier - 1) * 100);
        sb.append(pct >= 0 ? " +" : " ").append(pct).append("%)");
        return sb.toString();
    }

    public String getTitle()      { return title; }
    public String getStory()      { return story; }
    public double getMultiplier() { return multiplier; }
}

