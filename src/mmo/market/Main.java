package mmo.market;

import mmo.market.service.AccountManager;
import mmo.market.service.Market;
import mmo.market.service.PriceCatalog;
import mmo.market.service.WorldSetup;

import javax.swing.SwingUtilities;
import java.nio.file.Paths;

/**
 * Entry point. All data lives in the data/ folder next to where you run it:
 * accounts.txt (accounts + inventories), items.txt (past prices Kabir sets),
 * market.txt (live auctions, orders and pending trades).
 *
 * One launch creates one MarketSession: a single shared market that every
 * window (one per logged-in account) looks at.
 */
public class Main {

    public static void main(String[] args) {
        AccountManager accounts = new AccountManager(Paths.get("data", "accounts.txt"));
        PriceCatalog catalog = new PriceCatalog(Paths.get("data", "items.txt"));
        Market market = new Market(accounts, catalog, Paths.get("data", "market.txt"));
        WorldSetup.ensureSeeded(accounts, catalog, market);
        MarketSession session = new MarketSession(accounts, catalog, market);
        SwingUtilities.invokeLater(session::openLoginWindow);
    }
}

