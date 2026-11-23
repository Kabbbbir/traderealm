package mmo.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The items one account is holding. Filled by drops/purchases in step 2. */
public class Inventory {

    private final List<Item> items = new ArrayList<>();

    public void add(Item item) {
        items.add(item);
    }

    public boolean remove(Item item) {
        return items.remove(item);
    }

    /** Read-only view so nobody can change the list from outside. */
    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }
}

