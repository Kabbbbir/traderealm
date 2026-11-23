package mmo.market.service;

import mmo.market.exceptions.AuctionException;
import mmo.market.model.BotPlayer;
import mmo.market.model.Item;
import mmo.market.model.Player;
import mmo.market.model.Rarity;

import java.util.List;
import java.util.Random;

/**
 * Seeds the world on first run: the price catalog (the past prices Kabir
 * sets), the 10 bot players with gold and items, and a few opening auctions.
 */
public final class WorldSetup {

    /** The seeded admin login, documented for the demo (username / password). */
    public static final String ADMIN_NAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";

    private WorldSetup() { }

    public static void ensureSeeded(AccountManager accounts, PriceCatalog catalog, Market market) {
        seedCatalog(catalog);
        seedAdmin(accounts);
        seedBots(accounts, catalog);
        seedAuctions(accounts, market);
    }

    /** The default past prices. Kabir can change them by editing data/items.txt. */
    private static void seedCatalog(PriceCatalog catalog) {
        if (!catalog.isEmpty()) {
            return;
        }
        catalog.add(new Item("Healing Potion", Rarity.COMMON, 25));
        catalog.add(new Item("Mana Potion", Rarity.COMMON, 30));
        catalog.add(new Item("Iron Ore", Rarity.COMMON, 15));
        catalog.add(new Item("Goblin Dagger", Rarity.UNCOMMON, 90));
        catalog.add(new Item("Swift Boots", Rarity.UNCOMMON, 120));
        catalog.add(new Item("Mystic Orb", Rarity.RARE, 260));
        catalog.add(new Item("Runed Shield", Rarity.RARE, 300));
        catalog.add(new Item("Enchanted Ring", Rarity.RARE, 340));
        catalog.add(new Item("Stormblade", Rarity.EPIC, 450));
        catalog.add(new Item("Phoenix Feather", Rarity.EPIC, 500));
        catalog.add(new Item("Dragonscale Armor", Rarity.LEGENDARY, 900));
        catalog.add(new Item("Warg Mount Whistle", Rarity.LEGENDARY, 1200));
    }

    /** The admin account for the demo: logs in like any user, gets the Admin tab. */
    private static void seedAdmin(AccountManager accounts) {
        if (accounts.find(ADMIN_NAME) != null) {
            return;
        }
        try {
            accounts.registerAdmin(ADMIN_NAME, ADMIN_PASSWORD);
        } catch (mmo.market.exceptions.AuthException ignored) {
            // already there from a previous run
        }
    }

    private static void seedBots(AccountManager accounts, PriceCatalog catalog) {
        Random random = new Random();
        for (int i = 1; i <= 10; i++) {
            String name = "TraderBot" + i;
            if (accounts.find(name) != null) {
                continue;   // already exists from an earlier run
            }
            double aggression = 0.9 + random.nextDouble() * 0.5;   // 0.9 to 1.4
            BotPlayer bot = new BotPlayer(name, Passwords.sha256("bot" + i + "-" + random.nextInt()),
                    800 + random.nextInt(700), aggression);
            int itemCount = 2 + random.nextInt(2);
            for (int j = 0; j < itemCount; j++) {
                bot.getInventory().add(copy(catalog.randomItem(random)));
            }
            accounts.addBot(bot);
        }
    }

    private static void seedAuctions(AccountManager accounts, Market market) {
        if (!market.getAuctions().isEmpty()) {
            return;   // there are already live auctions
        }
        Random random = new Random();
        int listed = 0;
        for (BotPlayer bot : accounts.getBots()) {
            if (listed >= 5) {
                break;
            }
            List<Item> items = bot.getInventory().getItems();
            if (items.isEmpty()) {
                continue;
            }
            Item item = items.get(0);
            int base = Math.max(1, (int) (item.getLastSoldPrice() * 0.7));
            try {
                market.createAuction(bot, item, base, 300 + random.nextInt(600));   // 5 to 15 min
                listed++;
            } catch (AuctionException ignored) {
                // bot had nothing listable; try the next bot
            }
        }
    }

    /** A brand-new human gets 3 random items so the inventory tab is not empty. */
    public static void starterPackIfNew(Player player, PriceCatalog catalog, AccountManager accounts) {
        if (!player.getInventory().getItems().isEmpty() || catalog.isEmpty()) {
            return;
        }
        Random random = new Random();
        for (int i = 0; i < 3; i++) {
            player.getInventory().add(copy(catalog.randomItem(random)));
        }
        accounts.saveAll();
    }

    private static Item copy(Item item) {
        return new Item(item.getName(), item.getRarity(), item.getLastSoldPrice());
    }
}

