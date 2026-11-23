package mmo.market.ui;

import mmo.market.MarketSession;
import mmo.market.exceptions.AuctionException;
import mmo.market.exceptions.InsufficientFundsException;
import mmo.market.exceptions.OrderException;
import mmo.market.exceptions.TradeException;
import mmo.market.model.Account;
import mmo.market.model.Admin;
import mmo.market.model.Auction;
import mmo.market.model.BotPlayer;
import mmo.market.model.Item;
import mmo.market.model.MarketEvent;
import mmo.market.model.Order;
import mmo.market.model.Player;
import mmo.market.model.Rarity;
import mmo.market.model.TradeOffer;
import mmo.market.service.AccountManager;
import mmo.market.service.EventEngine;
import mmo.market.service.Market;
import mmo.market.service.PriceCatalog;
import mmo.market.service.WorldSetup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The main window after login: tabs for inventory, the auction house,
 * trades and the order book, a banner line for world events, plus an event
 * log at the bottom. Every window is a view on the shared MarketSession, so
 * two accounts logged in side by side see and act on the same market. The
 * session's BotEngine ticks every 2 seconds and calls refresh(), which
 * rewrites only the cells that actually changed so the selected row and the
 * scroll position survive every tick.
 *
 * The dark/gold styling comes from Theme, so it matches the login screen.
 */
public class MarketWindow extends JFrame {

    private static final Color YOUR_LISTING = new Color(0x2E, 0x28, 0x16);   // gold tint
    private static final Color YOUR_BID     = new Color(0x1E, 0x32, 0x26);   // green tint

    private final MarketSession session;
    private final AccountManager accounts;
    private final PriceCatalog catalog;
    private final Market market;
    private final Account account;
    private final EventEngine events;
    // one stable reference so the engine can deregister exactly this listener
    private final Runnable tickListener = this::refresh;

    private final JLabel goldLabel = new JLabel();
    private final JLabel eventLabel = new JLabel(" ");
    private final DefaultTableModel inventoryModel = readOnlyModel("Item", "Rarity", "Last price (g)");
    private final DefaultTableModel auctionModel = readOnlyModel("ID", "Item", "Rarity", "Seller", "Base", "Top bid", "Bidder", "Time left");
    private final DefaultTableModel tradeModel = readOnlyModel("ID", "From", "To", "Gives", "Wants", "Status");
    private final DefaultTableModel buyModel = readOnlyModel("Item", "Price (g)", "Buyer");
    private final DefaultTableModel adminModel = readOnlyModel("Name", "Role", "Gold", "Inventory");
    private final DefaultTableModel sellModel = readOnlyModel("Item", "Price (g)", "Seller");
    private final JTable inventoryTable = new JTable(inventoryModel);
    private final JTable auctionTable = new JTable(auctionModel);
    private final JTable tradeTable = new JTable(tradeModel);
    private final JTable buyTable = new JTable(buyModel);
    private final JTable sellTable = new JTable(sellModel);
    private final JTable adminTable = new JTable(adminModel);
    // admin-only controls, built in buildAdminTab() when an ADMIN logs in
    private JComboBox<String> adminItemBox;
    private JComboBox<String> adminEventBox;
    private JTextField adminPriceField;
    private JComboBox<String> adminSpawnItemBox;
    private JComboBox<String> adminSpawnAccountBox;
    private JLabel adminFeesLabel;
    private final JTextArea logArea = new JTextArea(6, 60);
    private final List<TradeOffer> myTrades = new ArrayList<>();
    private final List<Order> shownBuys = new ArrayList<>();
    private final List<Order> shownSells = new ArrayList<>();

    // the identity of the rows currently shown in each table, so refresh()
    // can tell "same rows, new values" apart from "rows appeared/disappeared"
    private final List<Object> inventoryKeys = new ArrayList<>();
    private final List<Object> auctionKeys = new ArrayList<>();
    private final List<Object> tradeKeys = new ArrayList<>();
    private final List<Object> buyKeys = new ArrayList<>();
    private final List<Object> sellKeys = new ArrayList<>();

    public MarketWindow(MarketSession session, Account account) {
        super("TradeRealm - Marketplace - " + account.getUsername());
        this.session = session;
        this.accounts = session.getAccounts();
        this.catalog = session.getCatalog();
        this.market = session.getMarket();
        this.account = account;

        // a brand-new human gets a few starter items so the inventory is not empty
        if (account instanceof Player && !(account instanceof BotPlayer)) {
            WorldSetup.starterPackIfNew((Player) account, catalog, accounts);
        }

        this.events = session.getEvents();

        buildUi();
        refresh();
        session.addTickListener(tickListener);   // the shared engine refreshes every window

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                session.removeTickListener(tickListener);
            }
        });
        setSize(840, 620);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        // top bar, row 1: who is logged in, their purse, logout
        JLabel who = Theme.label("Logged in as " + account.getUsername() + "  (" + account.getRole() + ")");
        who.setFont(who.getFont().deriveFont(Font.BOLD));
        goldLabel.setFont(goldLabel.getFont().deriveFont(Font.BOLD));
        goldLabel.setForeground(Theme.GOLD);
        JButton logout = Theme.goldButton("Log out");
        logout.setName("logoutButton");
        logout.addActionListener(e -> {
            dispose();
            session.openLoginWindow();
        });
        JButton addUser = Theme.goldButton("Log in another account");
        addUser.setName("addUserButton");
        addUser.setToolTipText("Opens a second login on this same market, for a two-account demo on one laptop");
        addUser.addActionListener(e -> session.openLoginWindow());
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row1.setOpaque(false);
        row1.add(who);
        row1.add(goldLabel);
        row1.add(logout);
        row1.add(addUser);

        // top bar, row 2: the world-event banner (blank while no event runs)
        eventLabel.setName("eventBanner");
        eventLabel.setFont(eventLabel.getFont().deriveFont(Font.BOLD, 12f));
        eventLabel.setForeground(Theme.EVENT);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row2.setOpaque(false);
        row2.add(eventLabel);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Theme.BG);
        topBar.setBorder(new EmptyBorder(10, 4, 8, 4));
        topBar.add(row1, BorderLayout.NORTH);
        topBar.add(row2, BorderLayout.SOUTH);

        // the default light "selected tab" background would hide our light text,
        // so the selected tab gets the theme's dark highlight colour instead
        UIManager.put("TabbedPane.selected", Theme.SELECTION);
        UIManager.put("TabbedPane.selectHighlight", Theme.LINE);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("tabs");
        tabs.setBackground(Theme.PANEL);
        tabs.setForeground(Theme.TEXT);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 12f));
        tabs.addTab("My Inventory", buildInventoryTab());
        tabs.addTab("Auction House", buildAuctionTab());
        tabs.addTab("Trades", buildTradesTab());
        tabs.addTab("Orders", buildOrdersTab());
        if (account instanceof Admin) {
            tabs.addTab("Admin", buildAdminTab());   // only admins get the dashboard
        }

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(0x10, 0x12, 0x1A));
        logArea.setForeground(new Color(0xC8, 0xD0, 0xE0));
        logArea.setCaretColor(Theme.TEXT);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.BG);
        TitledBorder logBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.LINE), "Market events");
        logBorder.setTitleColor(Theme.MUTED);
        bottom.setBorder(logBorder);
        bottom.add(Theme.darkScroll(logArea), BorderLayout.CENTER);

        getContentPane().setBackground(Theme.BG);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(topBar, BorderLayout.NORTH);
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
    }

    /** One dark content panel for a tab, holding the table and its buttons. */
    private JPanel buildTab(JTable table, JButton... buttons) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(Theme.darkScroll(table), BorderLayout.CENTER);
        JPanel buttonRow = new JPanel();
        buttonRow.setBackground(Theme.BG);
        for (JButton b : buttons) {
            buttonRow.add(b);
        }
        panel.add(buttonRow, BorderLayout.SOUTH);
        return panel;
    }

    // ---------- inventory tab ----------

    private JPanel buildInventoryTab() {
        Theme.styleTable(inventoryTable);
        inventoryTable.setDefaultRenderer(Object.class, new DarkCellRenderer());
        JButton sell = Theme.goldButton("Auction selected item");
        sell.setName("sellButton");
        sell.addActionListener(e -> onSell());
        return buildTab(inventoryTable, sell);
    }

    private void onSell() {
        int row = inventoryTable.getSelectedRow();
        if (row < 0) {
            info("Select an item first.");
            return;
        }
        Item item = account.getInventory().getItems().get(row);
        try {
            int base = askNumber("Base price (gold) for " + item.getName() + ":",
                    String.valueOf(Math.max(1, (int) (currentPrice(item) * 0.7))));
            if (base < 0) return;   // cancelled
            int minutes = askNumber("Duration (minutes):", "5");
            if (minutes < 0) return;
            market.createAuction(account, item, base, minutes * 60);
            refresh();
        } catch (AuctionException ex) {
            info(ex.getMessage());
        }
    }

    /** The catalog's current past price for this item (world events move it). */
    private int currentPrice(Item item) {
        Item listed = catalog.findByName(item.getName());
        return listed != null ? listed.getLastSoldPrice() : item.getLastSoldPrice();
    }

    // ---------- auction tab ----------

    private JPanel buildAuctionTab() {
        Theme.styleTable(auctionTable);
        auctionTable.setDefaultRenderer(Object.class, new AuctionCellRenderer());
        // ID and the numeric columns stay narrow, the item name gets the room
        auctionTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        auctionTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        auctionTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        JButton bid = Theme.goldButton("Bid on selected");
        bid.setName("bidButton");
        bid.addActionListener(e -> onBid());
        return buildTab(auctionTable, bid);
    }

    private void onBid() {
        int row = auctionTable.getSelectedRow();
        List<Auction> live = market.getAuctions();
        if (row < 0 || row >= live.size()) {
            info("Select an auction first.");
            return;
        }
        Auction auction = live.get(row);
        int minimum = auction.getCurrentBid() == 0 ? auction.getBasePrice() : auction.getCurrentBid() + 1;
        try {
            int amount = askNumber("Your bid on " + auction.getItem().getName()
                    + " (min " + minimum + "g):", String.valueOf(minimum));
            if (amount < 0) return;
            market.placeBid(account, auction.getId(), amount);
            refresh();
        } catch (AuctionException | InsufficientFundsException ex) {
            info(ex.getMessage());
        }
    }

    // ---------- trades tab ----------

    private JPanel buildTradesTab() {
        Theme.styleTable(tradeTable);
        tradeTable.setDefaultRenderer(Object.class, new TradeCellRenderer());
        JButton newTrade = Theme.goldButton("New trade offer");
        newTrade.setName("newTradeButton");
        newTrade.addActionListener(e -> {
            new TradeDialog(this, account, accounts, market).setVisible(true);
            refresh();
        });
        JButton accept = Theme.goldButton("Accept selected");
        accept.setName("acceptTradeButton");
        accept.addActionListener(e -> onAcceptTrade());
        JButton decline = Theme.goldButton("Decline selected");
        decline.setName("declineTradeButton");
        decline.addActionListener(e -> onDeclineTrade());
        return buildTab(tradeTable, newTrade, accept, decline);
    }

    private void onAcceptTrade() {
        TradeOffer trade = selectedTrade();
        if (trade == null) return;
        try {
            market.acceptTrade(trade, account);
            refresh();
        } catch (TradeException | InsufficientFundsException ex) {
            info(ex.getMessage());
        }
    }

    private void onDeclineTrade() {
        TradeOffer trade = selectedTrade();
        if (trade == null) return;
        market.declineTrade(trade, account);
        refresh();
    }

    private TradeOffer selectedTrade() {
        int row = tradeTable.getSelectedRow();
        if (row < 0 || row >= myTrades.size()) {
            info("Select a trade first.");
            return null;
        }
        return myTrades.get(row);
    }

    // ---------- orders tab ----------

    private JPanel buildOrdersTab() {
        Theme.styleTable(buyTable);
        Theme.styleTable(sellTable);
        buyTable.setDefaultRenderer(Object.class, new OrderCellRenderer());
        sellTable.setDefaultRenderer(Object.class, new OrderCellRenderer());
        // selecting in one book clears the other, so cancel has one clear target
        buyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && buyTable.getSelectedRow() >= 0) {
                sellTable.clearSelection();
            }
        });
        sellTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && sellTable.getSelectedRow() >= 0) {
                buyTable.clearSelection();
            }
        });

        JSplitPane books = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true,
                titled("Buy orders - what people bid", buyTable),
                titled("Sell orders - what people ask", sellTable));
        books.setResizeWeight(0.5);
        books.setBorder(null);

        JButton buy = Theme.goldButton("Place buy order");
        buy.setName("buyOrderButton");
        buy.addActionListener(e -> onBuyOrder());
        JButton sell = Theme.goldButton("Place sell order");
        sell.setName("sellOrderButton");
        sell.addActionListener(e -> onSellOrder());
        JButton fill = Theme.goldButton("Fill selected order");
        fill.setName("fillOrderButton");
        fill.setToolTipText("Sell into a selected buy order, or buy from a selected sell order, at its price");
        fill.addActionListener(e -> onFillOrder());
        JButton cancel = Theme.goldButton("Cancel my selected order");
        cancel.setName("cancelOrderButton");
        cancel.addActionListener(e -> onCancelOrder());

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(books, BorderLayout.CENTER);
        JPanel buttonRow = new JPanel();
        buttonRow.setBackground(Theme.BG);
        buttonRow.add(buy);
        buttonRow.add(sell);
        buttonRow.add(fill);
        buttonRow.add(cancel);
        panel.add(buttonRow, BorderLayout.SOUTH);
        return panel;
    }

    /** A titled dark panel wrapping one table. */
    private JPanel titled(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.LINE), title);
        border.setTitleColor(Theme.MUTED);
        panel.setBorder(border);
        panel.add(Theme.darkScroll(table), BorderLayout.CENTER);
        return panel;
    }

    private void onBuyOrder() {
        List<Item> items = catalog.getItems();
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = items.get(i).getName() + " (" + items.get(i).getRarity() + ")";
        }
        Object picked = JOptionPane.showInputDialog(this, "Buy which item?", "Buy order",
                JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (picked == null) return;   // cancelled
        String itemName = names[0];
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(picked)) {
                itemName = items.get(i).getName();
            }
        }
        try {
            int price = askNumber("Max price (gold) for " + itemName + ":",
                    String.valueOf(catalog.findByName(itemName).getLastSoldPrice()));
            if (price < 0) return;
            market.placeBuyOrder(account, itemName, price);
            refresh();
        } catch (OrderException | InsufficientFundsException ex) {
            info(ex.getMessage());
        }
    }

    private void onSellOrder() {
        List<Item> mine = account.getInventory().getItems();
        if (mine.isEmpty()) {
            info("Your inventory is empty.");
            return;
        }
        String[] labels = new String[mine.size()];
        for (int i = 0; i < mine.size(); i++) {
            labels[i] = (i + 1) + ". " + mine.get(i).getName() + " (" + mine.get(i).getRarity() + ")";
        }
        Object picked = JOptionPane.showInputDialog(this, "Sell which item?", "Sell order",
                JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (picked == null) return;
        int index = Integer.parseInt(picked.toString().substring(0, picked.toString().indexOf('.'))) - 1;
        Item item = mine.get(index);
        try {
            int price = askNumber("Ask price (gold) for " + item.getName() + ":",
                    String.valueOf(currentPrice(item)));
            if (price < 0) return;
            market.placeSellOrder(account, item, price);
            refresh();
        } catch (OrderException ex) {
            info(ex.getMessage());
        }
    }

    /**
     * Fills the selected order in one click: a selected buy order means "sell
     * that item into the bid", a selected sell order means "buy at that ask".
     * Goes through the same matching code as placing an order at that price,
     * so escrow settles exactly the same way.
     */
    private void onFillOrder() {
        int buyRow = buyTable.getSelectedRow();
        int sellRow = sellTable.getSelectedRow();
        if (buyRow < 0 && sellRow < 0) {
            info("Select an order to fill first.");
            return;
        }
        Order order = buyRow >= 0 ? shownBuys.get(buyRow) : shownSells.get(sellRow);
        if (order.getOwner().equalsIgnoreCase(account.getUsername())) {
            info("That is your own order - cancel it instead.");
            return;
        }
        if (market.findOrder(order.getId()) == null) {
            info("That order was just filled by someone else.");
            refresh();
            return;
        }
        try {
            if (order.getSide() == Order.Side.BUY) {
                // sell into the bid: hand over the item, receive the bid's gold
                Item mine = null;
                for (Item item : account.getInventory().getItems()) {
                    if (item.getName().equalsIgnoreCase(order.getItemName())) {
                        mine = item;
                        break;
                    }
                }
                if (mine == null) {
                    info("You do not have " + order.getItemName() + " to sell.");
                    return;
                }
                market.placeSellOrder(account, mine, order.getPrice());
            } else {
                // buy from the ask: pay its price, receive the item
                market.placeBuyOrder(account, order.getItemName(), order.getPrice());
            }
            refresh();
        } catch (OrderException | InsufficientFundsException ex) {
            info(ex.getMessage());
        }
    }

    private void onCancelOrder() {
        int buyRow = buyTable.getSelectedRow();
        int sellRow = sellTable.getSelectedRow();
        if (buyRow < 0 && sellRow < 0) {
            info("Select one of your orders first.");
            return;
        }
        Order order = buyRow >= 0 ? shownBuys.get(buyRow) : shownSells.get(sellRow);
        try {
            market.cancelOrder(order.getId(), account);
            refresh();
        } catch (OrderException ex) {
            info(ex.getMessage());
        }
    }

    // ---------- admin tab (ADMIN role only) ----------

    private JPanel buildAdminTab() {
        Theme.styleTable(adminTable);
        adminTable.setName("adminTable");

        // every account with its purse and belongings, refreshed on every tick
        JPanel tablePanel = titled("All accounts", adminTable);

        List<Item> items = catalog.getItems();
        String[] itemNames = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            itemNames[i] = items.get(i).getName() + " (" + items.get(i).getRarity() + ")";
        }
        adminItemBox = new JComboBox<>(itemNames);
        adminItemBox.setName("adminItemBox");
        adminPriceField = new JTextField(7);
        adminPriceField.setName("adminPriceField");
        JButton setPrice = Theme.goldButton("Set price");
        setPrice.setName("adminSetPriceButton");
        setPrice.addActionListener(e -> onAdminSetPrice());
        JPanel priceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        priceRow.setOpaque(false);
        priceRow.add(Theme.label("Catalog price:"));
        priceRow.add(adminItemBox);
        priceRow.add(adminPriceField);
        priceRow.add(setPrice);

        MarketEvent[] values = MarketEvent.values();
        String[] eventNames = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            eventNames[i] = values[i].getTitle();
        }
        adminEventBox = new JComboBox<>(eventNames);
        adminEventBox.setName("adminEventBox");
        JButton trigger = Theme.goldButton("Trigger event");
        trigger.setName("adminTriggerButton");
        trigger.addActionListener(e -> onAdminTrigger());
        JPanel eventRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        eventRow.setOpaque(false);
        eventRow.add(Theme.label("World event:"));
        eventRow.add(adminEventBox);
        eventRow.add(trigger);

        adminSpawnItemBox = new JComboBox<>(itemNames);
        adminSpawnItemBox.setName("adminSpawnItemBox");
        adminSpawnAccountBox = new JComboBox<>();
        adminSpawnAccountBox.setName("adminSpawnAccountBox");
        JButton spawn = Theme.goldButton("Spawn item");
        spawn.setName("adminSpawnButton");
        spawn.setToolTipText("Mints the item straight into that account's inventory");
        spawn.addActionListener(e -> onAdminSpawn());
        JPanel spawnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        spawnRow.setOpaque(false);
        spawnRow.add(Theme.label("Spawn item:"));
        spawnRow.add(adminSpawnItemBox);
        spawnRow.add(Theme.label("for"));
        spawnRow.add(adminSpawnAccountBox);
        spawnRow.add(spawn);

        adminFeesLabel = Theme.label(" ");
        adminFeesLabel.setName("adminFeesLabel");
        JPanel feesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        feesRow.setOpaque(false);
        feesRow.add(adminFeesLabel);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(Theme.BG);
        controls.setBorder(new EmptyBorder(8, 8, 8, 8));
        controls.add(priceRow);
        controls.add(Box.createVerticalStrut(8));
        controls.add(eventRow);
        controls.add(Box.createVerticalStrut(8));
        controls.add(spawnRow);
        controls.add(Box.createVerticalStrut(8));
        controls.add(feesRow);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    /** Admin: overwrite one item's catalog price. Bots and events read it from there. */
    private void onAdminSetPrice() {
        int index = adminItemBox.getSelectedIndex();
        if (index < 0) return;
        String itemName = catalog.getItems().get(index).getName();
        int price;
        try {
            price = Integer.parseInt(adminPriceField.getText().trim());
        } catch (NumberFormatException ex) {
            info("Enter a whole number of gold.");
            return;
        }
        if (!catalog.setPrice(itemName, price)) {
            info("Price must be at least 1 gold.");
            return;
        }
        market.announce("The admin set the price of " + itemName + " to " + price + "g");
        refresh();
    }

    /** Admin: mint an item straight into any account's inventory (new supply). */
    private void onAdminSpawn() {
        int index = adminSpawnItemBox.getSelectedIndex();
        String who = (String) adminSpawnAccountBox.getSelectedItem();
        if (index < 0 || who == null) return;
        Account target = accounts.find(who);
        if (target == null) return;
        Item proto = catalog.getItems().get(index);
        target.getInventory().add(new Item(proto.getName(), proto.getRarity(), proto.getLastSoldPrice()));
        accounts.saveAll();
        market.announce("The admin spawned " + proto.getName() + " for " + target.getUsername());
        refresh();
    }

    /** Admin: fire the chosen world event right now, for demos and testing. */
    private void onAdminTrigger() {
        MarketEvent event = MarketEvent.values()[adminEventBox.getSelectedIndex()];
        events.trigger(event);
        refresh();
    }

    /** Rebuilds the admin accounts table, keeping the selected row by username. */
    private void refreshAdminTab() {
        String selected = null;
        int row = adminTable.getSelectedRow();
        if (row >= 0 && row < adminModel.getRowCount()) {
            selected = String.valueOf(adminModel.getValueAt(row, 0));
        }
        List<Account> all = accounts.getAll();
        all.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
        adminModel.setRowCount(0);
        for (Account acc : all) {
            StringBuilder belongings = new StringBuilder();
            for (Item item : acc.getInventory().getItems()) {
                if (belongings.length() > 0) belongings.append(", ");
                belongings.append(item.getName());
            }
            adminModel.addRow(new Object[]{acc.getUsername(), acc.getRole(), acc.getGold(),
                    belongings.length() == 0 ? "-" : belongings.toString()});
        }
        if (selected != null) {
            for (int i = 0; i < adminModel.getRowCount(); i++) {
                if (selected.equals(adminModel.getValueAt(i, 0))) {
                    adminTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        // keep the spawn-target dropdown in step with the account list
        Object currentTarget = adminSpawnAccountBox.getSelectedItem();
        if (adminSpawnAccountBox.getItemCount() != all.size()) {
            adminSpawnAccountBox.removeAllItems();
            for (Account acc : all) {
                adminSpawnAccountBox.addItem(acc.getUsername());
            }
            if (currentTarget != null) {
                adminSpawnAccountBox.setSelectedItem(currentTarget);
            }
        }
        adminFeesLabel.setText("Market fees burned so far: " + market.getFeesBurned()
                + " g (gold sink, " + (int) (Market.MARKET_FEE_RATE * 100) + "% of every sale)");
    }

    // ---------- refresh + helpers ----------

    /**
     * Brings the window up to date with the market. Called after each bot tick
     * and after every own action. Only the cells whose values actually changed
     * are rewritten, so the selected row and the scroll position stay put
     * instead of being reset every 2 seconds.
     */
    public void refresh() {
        if (account instanceof Admin && adminItemBox != null) {
            refreshAdminTab();
        }
        String goldText = "Gold: " + account.getGold();
        if (!goldText.equals(goldLabel.getText())) {
            goldLabel.setText(goldText);
        }

        String banner = events.getBannerText();
        if (!banner.equals(eventLabel.getText())) {
            eventLabel.setText(banner);
        }

        int slot = 0;
        List<Object> newInventoryKeys = new ArrayList<>();
        List<Object[]> inventoryRows = new ArrayList<>();
        for (Item item : account.getInventory().getItems()) {
            // name + slot identify a row, even when you own two of the same item
            newInventoryKeys.add(item.getName() + "#" + slot++);
            inventoryRows.add(new Object[]{item.getName(), item.getRarity(), currentPrice(item)});
        }
        syncRows(inventoryModel, inventoryTable, inventoryKeys, newInventoryKeys, inventoryRows);

        List<Object> newAuctionKeys = new ArrayList<>();
        List<Object[]> auctionRows = new ArrayList<>();
        for (Auction a : market.getAuctions()) {
            newAuctionKeys.add(a.getId());
            auctionRows.add(new Object[]{a.getId(), a.getItem().getName(), a.getItem().getRarity(),
                    a.getSeller(),
                    a.getBasePrice(),
                    a.getCurrentBid() == 0 ? "-" : a.getCurrentBid(),
                    a.getCurrentBidder() == null ? "-" : a.getCurrentBidder(),
                    formatTime(a.getSecondsLeft())});
        }
        syncRows(auctionModel, auctionTable, auctionKeys, newAuctionKeys, auctionRows);

        List<Object> newTradeKeys = new ArrayList<>();
        List<Object[]> tradeRows = new ArrayList<>();
        List<TradeOffer> newMyTrades = new ArrayList<>();
        for (TradeOffer t : market.getTrades()) {
            if (t.involves(account.getUsername())) {
                newTradeKeys.add(t.getId());
                newMyTrades.add(t);
                tradeRows.add(new Object[]{t.getId(), t.getFromUser(), t.getToUser(),
                        TradeOffer.describe(t.getFromItem(), t.getFromGold()),
                        TradeOffer.describe(t.getToItem(), t.getToGold()),
                        t.getStatus()});
            }
        }
        syncRows(tradeModel, tradeTable, tradeKeys, newTradeKeys, tradeRows);
        myTrades.clear();
        myTrades.addAll(newMyTrades);

        // order book: bids sorted high to low, asks low to high (ties by age)
        List<Order> buys = new ArrayList<>();
        List<Order> sells = new ArrayList<>();
        for (Order o : market.getOrders()) {
            (o.getSide() == Order.Side.BUY ? buys : sells).add(o);
        }
        buys.sort((a, b) -> a.getPrice() != b.getPrice()
                ? Integer.compare(b.getPrice(), a.getPrice())
                : Long.compare(a.getId(), b.getId()));
        sells.sort((a, b) -> a.getPrice() != b.getPrice()
                ? Integer.compare(a.getPrice(), b.getPrice())
                : Long.compare(a.getId(), b.getId()));

        List<Object> newBuyKeys = new ArrayList<>();
        List<Object[]> buyRows = new ArrayList<>();
        List<Order> newShownBuys = new ArrayList<>();
        for (Order o : buys) {
            newBuyKeys.add(o.getId());
            newShownBuys.add(o);
            buyRows.add(new Object[]{o.getItemName(), o.getPrice(), o.getOwner()});
        }
        syncRows(buyModel, buyTable, buyKeys, newBuyKeys, buyRows);
        shownBuys.clear();
        shownBuys.addAll(newShownBuys);

        List<Object> newSellKeys = new ArrayList<>();
        List<Object[]> sellRows = new ArrayList<>();
        List<Order> newShownSells = new ArrayList<>();
        for (Order o : sells) {
            newSellKeys.add(o.getId());
            newShownSells.add(o);
            sellRows.add(new Object[]{o.getItemName(), o.getPrice(), o.getOwner()});
        }
        syncRows(sellModel, sellTable, sellKeys, newSellKeys, sellRows);
        shownSells.clear();
        shownSells.addAll(newShownSells);

        // event log: only rewrite when something new happened, then keep the
        // caret on the newest line so the latest event stays visible
        StringBuilder sb = new StringBuilder();
        for (String event : market.getRecentEvents(12)) {
            sb.append(event).append('\n');
        }
        String logText = sb.toString();
        if (!logText.equals(logArea.getText())) {
            logArea.setText(logText);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    /**
     * Writes fresh rows into a table without disturbing the person using it.
     *
     * If the same rows are still shown (same keys, same order), only the cells
     * whose values changed are updated - a cell update keeps the selection and
     * the scroll position. If rows really appeared or disappeared, the table
     * is rebuilt once and the previously selected row is selected again, found
     * by its key so it is still the same entry even if it moved.
     */
    private void syncRows(DefaultTableModel model, JTable table, List<Object> currentKeys,
                          List<Object> newKeys, List<Object[]> newRows) {
        Object selectedKey = null;
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < currentKeys.size()) {
            selectedKey = currentKeys.get(selectedRow);
        }

        if (currentKeys.equals(newKeys)) {
            // same rows as before: update changed cells in place, selection survives
            for (int row = 0; row < newRows.size(); row++) {
                for (int col = 0; col < newRows.get(row).length; col++) {
                    Object fresh = newRows.get(row)[col];
                    Object shown = model.getValueAt(row, col);
                    if (fresh == null ? shown != null : !fresh.equals(shown)) {
                        model.setValueAt(fresh, row, col);
                    }
                }
            }
            return;
        }

        // rows appeared or disappeared: rebuild, then reselect the same entry
        model.setRowCount(0);
        for (Object[] row : newRows) {
            model.addRow(row);
        }
        currentKeys.clear();
        currentKeys.addAll(newKeys);
        if (selectedKey != null) {
            int newRow = newKeys.indexOf(selectedKey);
            if (newRow >= 0) {
                table.setRowSelectionInterval(newRow, newRow);
                table.scrollRectToVisible(table.getCellRect(newRow, 0, true));
            }
        }
    }

    // ---------- cell renderers (the colours of the tables) ----------

    /**
     * Base look for every table cell: dark background, light text, and rarity
     * names in the classic MMO colours. The two subclasses below add their own
     * touches on top - a small example of inheritance inside the UI code.
     */
    private static class DarkCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                c.setBackground(Theme.FIELD);
                c.setForeground(Theme.TEXT);
            }
            if (value instanceof Rarity) {
                c.setForeground(Theme.rarityColor((Rarity) value));
            }
            return c;
        }
    }

    /**
     * Auction rows: your own listings get a gold tint, auctions you lead get a
     * green tint, and a countdown under a minute turns red.
     */
    private class AuctionCellRenderer extends DarkCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String me = account.getUsername();
            String seller = String.valueOf(table.getModel().getValueAt(row, 3));
            String bidder = String.valueOf(table.getModel().getValueAt(row, 6));
            if (!isSelected) {
                if (me.equalsIgnoreCase(seller)) {
                    c.setBackground(YOUR_LISTING);
                } else if (me.equalsIgnoreCase(bidder)) {
                    c.setBackground(YOUR_BID);
                }
            }
            if (column == 7 && String.valueOf(value).startsWith("0:")) {
                c.setForeground(Theme.RED);
            }
            return c;
        }
    }

    /** Order rows: your own orders are tinted, item names carry their rarity colour. */
    private class OrderCellRenderer extends DarkCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                String trader = String.valueOf(table.getModel().getValueAt(row, 2));
                if (account.getUsername().equalsIgnoreCase(trader)) {
                    c.setBackground(YOUR_BID);
                }
            }
            if (column == 0) {
                Item listed = catalog.findByName(String.valueOf(value));
                if (listed != null) {
                    c.setForeground(Theme.rarityColor(listed.getRarity()));
                }
            }
            return c;
        }
    }

    /** Trade rows: the status column is green when done, red when off, gold while pending. */
    private class TradeCellRenderer extends DarkCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof TradeOffer.Status) {
                switch ((TradeOffer.Status) value) {
                    case COMPLETED: c.setForeground(Theme.GREEN); break;
                    case DECLINED:
                    case CANCELLED: c.setForeground(Theme.RED); break;
                    default:        c.setForeground(Theme.GOLD);
                }
            }
            return c;
        }
    }

    // ---------- small dialogs ----------

    private static String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private int askNumber(String message, String preset) {
        String answer = JOptionPane.showInputDialog(this, message, preset);
        if (answer == null) {
            return -1;   // cancelled
        }
        try {
            return Integer.parseInt(answer.trim());
        } catch (NumberFormatException ex) {
            info("Please enter a whole number.");
            return -1;
        }
    }

    private void info(String message) {
        JOptionPane.showMessageDialog(this, message, "TradeRealm", JOptionPane.INFORMATION_MESSAGE);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}

