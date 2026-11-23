package mmo.market.service;

import mmo.market.model.Item;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The catalog of tradeable items with their past prices.
 * Kabir sets these prices by editing data/items.txt (one per line:
 * Name|RARITY|price). After every sale the market updates the past price,
 * so the bots always bid against the latest history.
 */
public class PriceCatalog {

    private final Path file;
    private final List<Item> items = new ArrayList<>();

    public PriceCatalog(Path file) {
        this.file = file;
        load();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Item findByName(String name) {
        for (Item item : items) {
            if (item.getName().equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }

    public void add(Item item) {
        items.add(item);
        save();
    }

    /** Moves the past prices of the given items at once (a world event hit them). */
    public void adjustPrices(List<Item> items, double multiplier) {
        for (Item item : items) {
            item.setLastSoldPrice(Math.max(1, (int) Math.round(item.getLastSoldPrice() * multiplier)));
        }
        save();
    }

    /** Admin tool: set one item's catalog price directly. Returns false on bad input. */
    public boolean setPrice(String itemName, int price) {
        Item item = findByName(itemName);
        if (item == null || price < 1) {
            return false;
        }
        item.setLastSoldPrice(price);
        save();
        return true;
    }

    /** Called after every sale: the past price moves and the bots adapt. */
    public void recordSale(String itemName, int price) {
        Item item = findByName(itemName);
        if (item != null) {
            item.setLastSoldPrice(price);
            save();
        }
    }

    public Item randomItem(Random random) {
        return items.get(random.nextInt(items.size()));
    }

    private void load() {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    items.add(new Item(parts[0],
                            mmo.market.model.Rarity.valueOf(parts[1]),
                            Integer.parseInt(parts[2])));
                }
            }
        } catch (NoSuchFileException e) {
            // first run: WorldSetup seeds the defaults
        } catch (IOException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                for (Item item : items) {
                    writer.write(item.getName() + "|" + item.getRarity() + "|" + item.getLastSoldPrice());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save item catalog to " + file, e);
        }
    }
}

