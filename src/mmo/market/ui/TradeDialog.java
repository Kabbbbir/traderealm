package mmo.market.ui;

import mmo.market.exceptions.TradeException;
import mmo.market.model.Account;
import mmo.market.model.Item;
import mmo.market.service.AccountManager;
import mmo.market.service.Market;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for creating a two-way trade offer: pick a partner, what you give
 * (item and/or gold) and what you want back (item and/or gold).
 */
public class TradeDialog extends JDialog {

    private static final String NONE = "(no item)";

    private final JComboBox<String> partnerBox = new JComboBox<>();
    private final JComboBox<String> myItemBox = new JComboBox<>();
    private final JComboBox<String> theirItemBox = new JComboBox<>();
    private final JTextField myGoldField = new JTextField("0", 6);
    private final JTextField theirGoldField = new JTextField("0", 6);

    private final List<Item> myItems = new ArrayList<>();
    private final List<Item> theirItems = new ArrayList<>();

    public TradeDialog(Window owner, Account me, AccountManager accounts, Market market) {
        super(owner, "New trade offer", ModalityType.APPLICATION_MODAL);

        for (Account other : accounts.getAll()) {
            if (!other.getUsername().equalsIgnoreCase(me.getUsername())) {
                partnerBox.addItem(other.getUsername());
            }
        }
        for (Item item : me.getInventory().getItems()) {
            myItems.add(item);
            myItemBox.addItem(item.getName() + " (" + item.getRarity() + ")");
        }
        myItemBox.addItem(NONE);
        partnerBox.addActionListener(e -> refillTheirItems(accounts));
        if (partnerBox.getItemCount() > 0) {
            refillTheirItems(accounts);
        }

        Theme.styleInput(partnerBox);
        Theme.styleInput(myItemBox);
        Theme.styleInput(theirItemBox);
        Theme.styleInput(myGoldField);
        Theme.styleInput(theirGoldField);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBackground(Theme.BG);
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        form.add(Theme.label("Trade with:"));
        form.add(partnerBox);
        form.add(Theme.label("You give item:"));
        form.add(myItemBox);
        form.add(Theme.label("You give gold:"));
        form.add(myGoldField);
        form.add(Theme.label("You want item:"));
        form.add(theirItemBox);
        form.add(Theme.label("You want gold:"));
        form.add(theirGoldField);

        JButton send = Theme.goldButton("Send offer");
        send.addActionListener(e -> onSend(me, accounts, market));
        JButton cancel = new JButton("Cancel");
        cancel.setBackground(Theme.PANEL);
        cancel.setForeground(Theme.TEXT);
        cancel.setOpaque(true);
        cancel.setBorderPainted(false);
        cancel.setFocusPainted(false);
        cancel.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel();
        buttons.setBackground(Theme.BG);
        buttons.add(send);
        buttons.add(cancel);

        getContentPane().setBackground(Theme.BG);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private void refillTheirItems(AccountManager accounts) {
        theirItems.clear();
        theirItemBox.removeAllItems();
        Account partner = accounts.find((String) partnerBox.getSelectedItem());
        if (partner != null) {
            for (Item item : partner.getInventory().getItems()) {
                theirItems.add(item);
                theirItemBox.addItem(item.getName() + " (" + item.getRarity() + ")");
            }
        }
        theirItemBox.addItem(NONE);
    }

    private void onSend(Account me, AccountManager accounts, Market market) {
        try {
            int myGold = parseGold(myGoldField.getText());
            int theirGold = parseGold(theirGoldField.getText());
            Item myItem = myItemBox.getSelectedIndex() < myItems.size()
                    ? myItems.get(myItemBox.getSelectedIndex()) : null;
            Item theirItem = theirItemBox.getSelectedIndex() < theirItems.size()
                    ? theirItems.get(theirItemBox.getSelectedIndex()) : null;
            market.proposeTrade(me, (String) partnerBox.getSelectedItem(),
                    myItem, myGold, theirItem, theirGold);
            dispose();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Gold must be a whole number.",
                    "Invalid trade", JOptionPane.ERROR_MESSAGE);
        } catch (TradeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Invalid trade", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int parseGold(String text) {
        return Integer.parseInt(text.trim());
    }
}

