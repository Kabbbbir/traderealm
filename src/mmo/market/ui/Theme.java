package mmo.market.ui;

import mmo.market.model.Rarity;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * The shared look of the app: one class owns the colours and the small
 * styling helpers, so every window uses the same dark/gold palette as the
 * login screen and changing the theme means editing one file.
 */
public final class Theme {

    public static final Color BG    = new Color(0x16, 0x1A, 0x24);   // window background
    public static final Color PANEL = new Color(0x1F, 0x24, 0x33);   // raised panels, table headers
    public static final Color FIELD = new Color(0x25, 0x2B, 0x3B);   // tables and input fields
    public static final Color GOLD  = new Color(0xD4, 0xAF, 0x37);   // headings, primary buttons
    public static final Color TEXT  = new Color(0xEC, 0xEC, 0xF1);
    public static final Color MUTED = new Color(0x9A, 0xA0, 0xB0);
    public static final Color LINE  = new Color(0x3A, 0x42, 0x56);   // borders and grid lines
    public static final Color SELECTION = new Color(0x36, 0x41, 0x5C);
    public static final Color GREEN = new Color(0x4C, 0xC3, 0x4C);
    public static final Color RED   = new Color(0xFF, 0x6B, 0x6B);
    public static final Color EVENT = new Color(0xFF, 0xB3, 0x47);   // active-event banner

    private Theme() { }

    /** The classic MMO colour per rarity tier (grey, green, blue, purple, orange). */
    public static Color rarityColor(Rarity rarity) {
        switch (rarity) {
            case UNCOMMON:  return new Color(0x4C, 0xC3, 0x4C);
            case RARE:      return new Color(0x4A, 0x9E, 0xFF);
            case EPIC:      return new Color(0xC4, 0x6B, 0xFF);
            case LEGENDARY: return new Color(0xFF, 0x8C, 0x2E);
            default:        return new Color(0xB8, 0xB8, 0xB8);   // COMMON
        }
    }

    /** A gold primary button, same style as the login screen. */
    public static JButton goldButton(String text) {
        JButton b = new JButton(text);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setBackground(GOLD);
        b.setForeground(new Color(0x1A, 0x14, 0x05));
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return b;
    }

    /** Dark table look: colours, row height and a styled header. */
    public static void styleTable(JTable table) {
        table.setBackground(FIELD);
        table.setForeground(TEXT);
        table.setGridColor(LINE);
        table.setShowVerticalLines(false);
        table.setRowHeight(24);
        table.setSelectionBackground(SELECTION);
        table.setSelectionForeground(TEXT);
        table.setFont(table.getFont().deriveFont(13f));
        table.setFillsViewportHeight(true);

        DefaultTableCellRenderer header = new DefaultTableCellRenderer();
        header.setBackground(PANEL);
        header.setForeground(GOLD);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setHeaderRenderer(header);
        }
    }

    /** A scroll pane that fits the dark theme. */
    public static JScrollPane darkScroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.getViewport().setBackground(FIELD);
        sp.setBorder(BorderFactory.createLineBorder(LINE));
        return sp;
    }

    /** A plain label in the theme's text colour. */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        return l;
    }

    /** Dark styling for text fields and combo boxes. */
    public static void styleInput(JComponent c) {
        c.setBackground(FIELD);
        c.setForeground(TEXT);
        if (c instanceof JTextField) {
            ((JTextField) c).setCaretColor(TEXT);
        }
    }
}

