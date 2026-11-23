package mmo.market.service;

import mmo.market.exceptions.AuthException;
import mmo.market.exceptions.InvalidCredentialsException;
import mmo.market.exceptions.UsernameTakenException;
import mmo.market.model.Account;
import mmo.market.model.Admin;
import mmo.market.model.BotPlayer;
import mmo.market.model.Item;
import mmo.market.model.Player;
import mmo.market.model.Rarity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles registration and login, and saves every account (human and bot)
 * to a text file so they survive a restart, inventories included.
 */
public class AccountManager {

    /** Keyed by lowercase username so "Arthas" and "arthas" are the same account. */
    private final Map<String, Account> accounts = new HashMap<>();
    private final Path saveFile;

    public AccountManager(Path saveFile) {
        this.saveFile = saveFile;
        load();
    }

    /** Creates a new Player with the starting purse and saves it to disk. */
    public Account register(String username, String rawPassword) throws AuthException {
        username = username == null ? "" : username.trim();
        validateUsername(username);
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new AuthException("Password must be at least 4 characters.");
        }
        if (accounts.containsKey(username.toLowerCase())) {
            throw new UsernameTakenException(username);
        }
        Player player = new Player(username, Passwords.sha256(rawPassword));
        accounts.put(username.toLowerCase(), player);
        saveAll();
        return player;
    }

    /** Creates the admin account. Used once by the world setup; registration stays for players. */
    public Admin registerAdmin(String username, String rawPassword) throws AuthException {
        username = username == null ? "" : username.trim();
        validateUsername(username);
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new AuthException("Password must be at least 4 characters.");
        }
        if (accounts.containsKey(username.toLowerCase())) {
            throw new UsernameTakenException(username);
        }
        Admin admin = new Admin(username, Passwords.sha256(rawPassword));
        accounts.put(username.toLowerCase(), admin);
        saveAll();
        return admin;
    }

    /** Used by the world setup to add the 10 bots. */
    public void addBot(BotPlayer bot) {
        accounts.put(bot.getUsername().toLowerCase(), bot);
        saveAll();
    }

    /** Returns the account when username + password match, otherwise throws. */
    public Account login(String username, String rawPassword) throws InvalidCredentialsException {
        Account account = accounts.get(username == null ? "" : username.trim().toLowerCase());
        if (account == null || !account.checkPassword(rawPassword)) {
            throw new InvalidCredentialsException();
        }
        return account;
    }

    /** Finds any account by username, or null. */
    public Account find(String username) {
        return username == null ? null : accounts.get(username.trim().toLowerCase());
    }

    /** The bot players (role "BOT"), used by the BotEngine. */
    public List<BotPlayer> getBots() {
        List<BotPlayer> bots = new ArrayList<>();
        for (Account account : accounts.values()) {
            if (account instanceof BotPlayer) {
                bots.add((BotPlayer) account);
            }
        }
        return bots;
    }

    /** Every account, used for the trade-partner picker. */
    public List<Account> getAll() {
        return new ArrayList<>(accounts.values());
    }

    public int count() {
        return accounts.size();
    }

    private void validateUsername(String username) throws AuthException {
        if (!username.matches("[A-Za-z0-9_]{3,16}")) {
            throw new AuthException("Username must be 3-16 letters, digits or underscores.");
        }
    }

    /**
     * Reads the save file back into memory. Missing file = fresh start.
     * Old step-1 records without the extra/inventory fields still load fine.
     */
    private void load() {
        try (BufferedReader reader = Files.newBufferedReader(saveFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 4) {
                    continue;
                }
                String extra = parts.length > 4 ? parts[4] : "";
                String itemsField = parts.length > 5 ? parts[5] : "";
                Account account;
                if ("PLAYER".equals(parts[0])) {
                    account = new Player(parts[1], parts[2], Integer.parseInt(parts[3]));
                } else if ("ADMIN".equals(parts[0])) {
                    account = new Admin(parts[1], parts[2], Integer.parseInt(parts[3]));
                } else if ("BOT".equals(parts[0])) {
                    double aggression = extra.isEmpty() ? 1.0 : Double.parseDouble(extra);
                    account = new BotPlayer(parts[1], parts[2], Integer.parseInt(parts[3]), aggression);
                } else {
                    continue;  // unknown role, skip the line
                }
                loadItems(account, itemsField);
                accounts.put(account.getUsername().toLowerCase(), account);
            }
        } catch (NoSuchFileException e) {
            // first run: no save file yet, nothing to load
        } catch (IOException e) {
            System.err.println("Could not read " + saveFile + ": " + e.getMessage());
        }
    }

    private void loadItems(Account account, String itemsField) {
        if (itemsField.isEmpty()) {
            return;
        }
        for (String itemText : itemsField.split(";")) {
            String[] f = itemText.split(",");
            if (f.length == 3) {
                account.getInventory().add(new Item(f[0], Rarity.valueOf(f[1]), Integer.parseInt(f[2])));
            }
        }
    }

    /** Writes every account to the save file, one record per line. */
    public synchronized void saveAll() {
        try {
            if (saveFile.getParent() != null) {
                Files.createDirectories(saveFile.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(saveFile)) {
                for (Account account : accounts.values()) {
                    writer.write(account.toRecord());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save accounts to " + saveFile, e);
        }
    }
}

