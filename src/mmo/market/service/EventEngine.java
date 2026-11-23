package mmo.market.service;

import mmo.market.model.Item;
import mmo.market.model.MarketEvent;

import java.util.List;
import java.util.Random;

/**
 * Fires random world events that move the economy (see MarketEvent). Runs on
 * the same 2-second tick as the BotEngine: it counts down to the next event,
 * moves the affected past prices when it fires, and announces it in the
 * market log. The market window shows the active event as a banner.
 */
public class EventEngine {

    private static final int MIN_TICKS_BETWEEN_EVENTS = 15;   // ~30 seconds
    private static final int MAX_TICKS_BETWEEN_EVENTS = 30;   // ~60 seconds
    private static final int BANNER_TICKS = 15;               // banner stays ~30 seconds

    private static final MarketEvent[] ALL = MarketEvent.values();

    private final Market market;
    private final PriceCatalog catalog;
    private final Random random = new Random();

    private MarketEvent active;
    private int bannerTicksLeft;
    private int ticksUntilNext = 8 + random.nextInt(5);   // first event ~20 seconds in

    public EventEngine(Market market, PriceCatalog catalog) {
        this.market = market;
        this.catalog = catalog;
    }

    /** Called once per bot tick. Fires a new event when the countdown runs out. */
    public void tick() {
        if (bannerTicksLeft > 0 && --bannerTicksLeft == 0) {
            active = null;   // the banner fades; the price change stays, like real news
        }
        if (--ticksUntilNext <= 0) {
            fire();
            ticksUntilNext = MIN_TICKS_BETWEEN_EVENTS
                    + random.nextInt(MAX_TICKS_BETWEEN_EVENTS - MIN_TICKS_BETWEEN_EVENTS + 1);
        }
    }

    /** Picks a random event, moves the affected past prices and announces it. */
    private void fire() {
        trigger(ALL[random.nextInt(ALL.length)]);
    }

    /** Admin tool: fire a chosen event right away. The random timer keeps running. */
    public void trigger(MarketEvent event) {
        List<Item> hit = event.affectedItems(catalog.getItems(), random);
        catalog.adjustPrices(hit, event.getMultiplier());
        active = event;
        bannerTicksLeft = BANNER_TICKS;
        market.announce(event.describe(hit));
    }

    /** Text for the banner label in the market window, or blank when nothing runs. */
    public String getBannerText() {
        return active == null ? " " : active.getTitle().toUpperCase() + "!  " + active.getStory();
    }
}

