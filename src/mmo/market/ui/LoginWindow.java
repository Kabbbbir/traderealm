package mmo.market.ui;

import mmo.market.MarketSession;
import mmo.market.exceptions.AuthException;
import mmo.market.model.Account;
import mmo.market.service.AccountManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * The account window: login and registration.
 * A CardLayout switches between the two "pages" inside one frame.
 * A successful login opens a MarketWindow on the shared session and closes
 * this one. Extra login windows (from "Log in another account") join the
 * same session, so every account trades on the same market.
 */
public class LoginWindow extends JFrame {

    private static final Color BG    = new Color(0x16, 0x1A, 0x24);
    private static final Color FIELD = new Color(0x25, 0x2B, 0x3B);
    private static final Color GOLD  = new Color(0xD4, 0xAF, 0x37);
    private static final Color TEXT  = new Color(0xEC, 0xEC, 0xF1);

    private final MarketSession session;
    private final AccountManager accountManager;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private final JTextField loginUser = new JTextField(16);
    private final JPasswordField loginPass = new JPasswordField(16);
    private final JTextField regUser = new JTextField(16);
    private final JPasswordField regPass = new JPasswordField(16);
    private final JPasswordField regPass2 = new JPasswordField(16);

    public LoginWindow(MarketSession session) {
        super("TradeRealm - MMORPG Trading Simulator");
        this.session = session;
        this.accountManager = session.getAccounts();

        loginUser.setName("loginUser");
        loginPass.setName("loginPass");
        regUser.setName("regUser");
        regPass.setName("regPass");
        regPass2.setName("regPass2");

        cardPanel.add(buildLoginCard(), "login");
        cardPanel.add(buildRegisterCard(), "register");
        cards.show(cardPanel, "login");

        setContentPane(cardPanel);
        setSize(460, 560);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    // ---------- login page ----------

    private JPanel buildLoginCard() {
        JPanel card = darkPanel();
        card.add(title("TradeRealm"));
        card.add(subtitle("MMORPG Trading Simulator"));
        card.add(Box.createVerticalStrut(30));
        card.add(label("Username"));
        card.add(field(loginUser));
        card.add(Box.createVerticalStrut(12));
        card.add(label("Password"));
        card.add(field(loginPass));
        card.add(Box.createVerticalStrut(26));

        JButton login = goldButton("Log in");
        login.setName("loginButton");
        login.addActionListener(e -> onLogin());
        loginPass.addActionListener(e -> onLogin());   // Enter key logs in
        card.add(login);

        card.add(Box.createVerticalStrut(16));
        JButton toRegister = linkButton("New here? Create an account");
        toRegister.setName("toRegisterLink");
        toRegister.addActionListener(e -> cards.show(cardPanel, "register"));
        card.add(toRegister);
        return card;
    }

    private void onLogin() {
        try {
            Account account = accountManager.login(
                    loginUser.getText(), new String(loginPass.getPassword()));
            dispose();
            session.openMarketWindow(account);
        } catch (AuthException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Login failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- register page ----------

    private JPanel buildRegisterCard() {
        JPanel card = darkPanel();
        card.add(title("Create account"));
        card.add(subtitle("Pick a name, get " + Account.STARTING_GOLD + " starting gold"));
        card.add(Box.createVerticalStrut(24));
        card.add(label("Username"));
        card.add(field(regUser));
        card.add(Box.createVerticalStrut(10));
        card.add(label("Password"));
        card.add(field(regPass));
        card.add(Box.createVerticalStrut(10));
        card.add(label("Repeat password"));
        card.add(field(regPass2));
        card.add(Box.createVerticalStrut(24));

        JButton register = goldButton("Create account");
        register.setName("registerButton");
        register.addActionListener(e -> onRegister());
        regPass2.addActionListener(e -> onRegister());
        card.add(register);

        card.add(Box.createVerticalStrut(16));
        JButton toLogin = linkButton("Back to login");
        toLogin.setName("toLoginLink");
        toLogin.addActionListener(e -> cards.show(cardPanel, "login"));
        card.add(toLogin);
        return card;
    }

    private void onRegister() {
        String p1 = new String(regPass.getPassword());
        String p2 = new String(regPass2.getPassword());
        if (!p1.equals(p2)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.",
                    "Registration failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Account account = accountManager.register(regUser.getText(), p1);
            JOptionPane.showMessageDialog(this,
                    "Account created for " + account.getUsername() + ". You can log in now.",
                    "Welcome to TradeRealm", JOptionPane.INFORMATION_MESSAGE);
            loginUser.setText(account.getUsername());   // prefill for convenience
            regPass.setText("");
            regPass2.setText("");
            cards.show(cardPanel, "login");
        } catch (AuthException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Registration failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- small styling helpers ----------

    private JPanel darkPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(48, 48, 48, 48));
        return panel;
    }

    private JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.DIALOG, Font.BOLD, 28));
        l.setForeground(GOLD);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JLabel subtitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        l.setForeground(new Color(0x9A, 0xA0, 0xB0));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        l.setForeground(TEXT);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JComponent field(JTextField f) {
        f.setMaximumSize(new Dimension(250, 34));
        f.setBackground(FIELD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setHorizontalAlignment(JTextField.CENTER);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3A, 0x42, 0x56)),
                new EmptyBorder(4, 8, 4, 8)));
        f.setAlignmentX(Component.CENTER_ALIGNMENT);
        return f;
    }

    private JButton goldButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        b.setBackground(GOLD);
        b.setForeground(new Color(0x1A, 0x14, 0x05));
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setMaximumSize(new Dimension(250, 38));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        return b;
    }

    private JButton linkButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        b.setForeground(GOLD);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        return b;
    }
}

