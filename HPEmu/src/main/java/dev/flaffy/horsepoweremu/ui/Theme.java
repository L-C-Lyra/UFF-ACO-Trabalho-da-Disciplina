package dev.flaffy.horsepoweremu.ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

//todo: adicionar tema customizavel?
public final class Theme {

    private Theme() {}

    public static final Color BG          = new Color(0xF5F6F8);
    public static final Color PANEL       = new Color(0xFFFFFF);
    public static final Color PANEL_ALT   = new Color(0xF0F1F4);
    public static final Color BORDER      = new Color(0xD8DAE0);
    public static final Color TEXT        = new Color(0x1A1B23);
    public static final Color TEXT_SEC    = new Color(0x6B7280);
    public static final Color TEXT_MONO   = new Color(0x2D3A4A);

    public static final Color ACCENT      = new Color(0x3B5BDB);
    public static final Color GREEN       = new Color(0x2F9E44);
    public static final Color RED         = new Color(0xE03131);
    public static final Color ORANGE      = new Color(0xE67700);
    public static final Color PURPLE      = new Color(0x7048E8);
    public static final Color CYAN        = new Color(0x1971C2);

    public static final Color FLAG_N      = new Color(0xE03131);
    public static final Color FLAG_V      = new Color(0xD6510B);
    public static final Color FLAG_B      = new Color(0x7048E8);
    public static final Color FLAG_I      = new Color(0x1971C2);
    public static final Color FLAG_Z      = new Color(0x2F9E44);
    public static final Color FLAG_C      = new Color(0xE67700);
    public static final Color FLAG_OFF    = new Color(0xE8E9EC);
    public static final Color FLAG_TEXT   = Color.WHITE;

    public static final Color HIGHLIGHT_FRESH = new Color(0xFFF3BF);
    public static final Color HIGHLIGHT_ROM   = new Color(0xEAF0FF);
    public static final int   HIGHLIGHT_MS    = 1500;

    public static final Font FONT_MONO    = new Font("Monospaced", Font.PLAIN,  12);
    public static final Font FONT_MONO_SM = new Font("Monospaced", Font.PLAIN,  11);
    public static final Font FONT_MONO_BD = new Font("Monospaced", Font.BOLD,   12);
    public static final Font FONT_UI      = new Font("SansSerif",  Font.PLAIN,  12);
    public static final Font FONT_UI_BD   = new Font("SansSerif",  Font.BOLD,   12);
    public static final Font FONT_UI_SM   = new Font("SansSerif",  Font.PLAIN,  11);

    public static Border panelBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER),
            title, TitledBorder.LEFT, TitledBorder.TOP,
            FONT_UI_BD, TEXT_SEC);
    }

    public static Border lineBorder() {
        return BorderFactory.createLineBorder(BORDER);
    }

    public static Border emptyBorder(int t, int l, int b, int r) {
        return BorderFactory.createEmptyBorder(t, l, b, r);
    }

    public static void applyGlobally() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception ignored) {}

        UIManager.put("Panel.background",           PANEL);
        UIManager.put("Label.foreground",           TEXT);
        UIManager.put("Label.background",           PANEL);
        UIManager.put("Button.background",          new Color(0xE8E9EC));
        UIManager.put("Button.foreground",          TEXT);
        UIManager.put("Button.border",              BorderFactory.createLineBorder(BORDER));
        UIManager.put("Button.focus",               new Color(0,0,0,0));
        UIManager.put("TextArea.background",        PANEL);
        UIManager.put("TextArea.foreground",        TEXT_MONO);
        UIManager.put("ScrollPane.background",      PANEL);
        UIManager.put("Viewport.background",        PANEL);
        UIManager.put("ScrollBar.background",       BG);
        UIManager.put("ScrollBar.thumb",            BORDER);
        UIManager.put("ScrollBar.track",            BG);
        UIManager.put("ComboBox.background",        PANEL);
        UIManager.put("ComboBox.foreground",        TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("Spinner.background",         PANEL);
        UIManager.put("TextField.background",       PANEL);
        UIManager.put("TextField.foreground",       TEXT);
        UIManager.put("CheckBox.background",        PANEL);
        UIManager.put("CheckBox.foreground",        TEXT);
        UIManager.put("ToolBar.background",         BG);
        UIManager.put("SplitPane.background",       BG);
        UIManager.put("SplitPaneDivider.background",BG);
        UIManager.put("TitledBorder.titleColor",    TEXT_SEC);
        UIManager.put("OptionPane.background",      PANEL);
        UIManager.put("OptionPane.messageForeground",TEXT);
    }

    public static Color interpolate(Color from, Color to, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)(from.getRed()   + (to.getRed()   - from.getRed())   * t);
        int g = (int)(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int b = (int)(from.getBlue()  + (to.getBlue()  - from.getBlue())  * t);
        return new Color(r, g, b);
    }
}