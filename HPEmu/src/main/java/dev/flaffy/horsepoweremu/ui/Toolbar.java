package dev.flaffy.horsepoweremu.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class Toolbar extends JPanel {

    public static final int SPEED_MIN = 5;
    public static final int SPEED_MAX = 400;

    public final JButton  btnLoad, btnReset, btnRunPause, btnStep;
    public final JSpinner spnSpeed;

    public Toolbar(Runnable onLoad, Runnable onReset,
                   Runnable onRunPause, Runnable onStep,
                   Consumer<Integer> onSpeed) {
        setBackground(Theme.PANEL_ALT);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 5));

        btnLoad     = btn("Load ROM",  new Color(0x3B5BDB), onLoad);
        btnReset    = btn("Reset",     new Color(0xC92A2A), onReset);
        btnRunPause = btn("▶  Run",    new Color(0x2F9E44), onRunPause);
        btnStep     = btn("Step",      new Color(0x6741D9), onStep);

        add(btnLoad);
        add(sep());
        add(btnReset);
        add(btnRunPause);
        add(btnStep);
        add(sep());

        JLabel spd = new JLabel("Speed:");
        spd.setFont(Theme.FONT_UI_SM);
        spd.setForeground(Theme.TEXT);
        add(spd);

        spnSpeed = new JSpinner(new SpinnerNumberModel(100, SPEED_MIN, SPEED_MAX, 5));
        spnSpeed.setFont(Theme.FONT_UI_SM);
        ((JSpinner.DefaultEditor) spnSpeed.getEditor()).getTextField().setColumns(4);
        spnSpeed.setMaximumSize(spnSpeed.getPreferredSize());
        spnSpeed.addChangeListener(e -> onSpeed.accept((Integer) spnSpeed.getValue()));
        add(spnSpeed);

        JLabel pct = new JLabel("%");
        pct.setFont(Theme.FONT_UI_SM);
        pct.setForeground(Theme.TEXT);
        add(pct);

        add(sep());

        JLabel clk = new JLabel("Clock: 3.58~ MHz");
        clk.setFont(Theme.FONT_MONO_SM);
        clk.setForeground(Theme.TEXT_SEC);
        add(clk);
    }

    private JButton btn(String label, Color accent, Runnable action) {
        JButton b = new JButton(label);
        b.setFont(Theme.FONT_UI_SM);
        b.setForeground(Color.WHITE);
        b.setBackground(accent);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(Theme.emptyBorder(4, 10, 4, 10));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            final Color base = accent;
            public void mouseEntered(java.awt.event.MouseEvent e) { b.setBackground(base.brighter()); }
            public void mouseExited(java.awt.event.MouseEvent e) { b.setBackground(base); }
        });
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    private Component sep() {
        JSeparator s = new JSeparator(JSeparator.VERTICAL);
        s.setPreferredSize(new Dimension(1, 20));
        s.setForeground(Theme.BORDER);
        return s;
    }

    public void setRunning(boolean running) {
        btnRunPause.setText(running ? "⏸  Pause" : "▶  Run");
        btnStep.setEnabled(!running);
    }
}
