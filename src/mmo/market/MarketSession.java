package mmo.market;

import mmo.market.model.Account;
import mmo.market.service.AccountManager;
import mmo.market.service.BotEngine;
import mmo.market.service.EventEngine;
import mmo.market.service.Market;
import mmo.market.service.PriceCatalog;
import mmo.market.ui.LoginWindow;
import mmo.market.ui.MarketWindow;

import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * One running TradeRealm world: one market, one price catalog, one bot
 * engine and one event engine, shared by every window this app opens.
 *
 * Opening the app twice would start two separate worlds that fight over the
 * same save files, so instead the session can open extra windows on the SAME
 * world: several accounts log in side by side and see the same auctions,
 * orders, trades and events. The app exits when the last window closes.
 */
public class MarketSession {

    private final AccountManager accounts;
    private final PriceCatalog catalog;
    private final Market market;
    private final EventEngine events;
    private final BotEngine botEngine;
    private int openWindows;

    public MarketSession(AccountManager accounts, PriceCatalog catalog, Market market) {
        this.accounts = accounts;
        this.catalog = catalog;
        this.market = market;
        this.events = new EventEngine(market, catalog);
        this.botEngine = new BotEngine(market, accounts, accounts.getBots(), events);
        this.botEngine.start();   // the world is alive even at the login screen
    }

    public AccountManager getAccounts() { return accounts; }
    public PriceCatalog getCatalog()    { return catalog; }
    public Market getMarket()           { return market; }
    public EventEngine getEvents()      { return events; }
    public BotEngine getBotEngine()     { return botEngine; }

    /** Windows register here so every open window refreshes on every bot tick. */
    public void addTickListener(Runnable listener)    { botEngine.addTickListener(listener); }
    public void removeTickListener(Runnable listener) { botEngine.removeTickListener(listener); }

    /** Opens the login/register window. Used at startup and by "Log in another account". */
    public void openLoginWindow() {
        track(new LoginWindow(this));
    }

    /** Opens a market window for a logged-in account on this session's market. */
    public void openMarketWindow(Account account) {
        track(new MarketWindow(this, account));
    }

    /** Shows a window, cascades it a little so stacked windows stay visible,
     *  and counts it so the app can exit when the last one closes. */
    private void track(JFrame window) {
        openWindows++;
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                openWindows--;
                if (openWindows == 0) {
                    System.exit(0);
                }
            }
        });
        // cascade each extra window so it does not hide the previous one
        window.setLocation(24 + 28 * (openWindows - 1), 16 + 28 * (openWindows - 1));
        window.setVisible(true);
    }
}

