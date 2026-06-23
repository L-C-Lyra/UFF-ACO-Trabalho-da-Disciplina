package dev.flaffy.horsepoweremu.ui;

import dev.flaffy.horsepoweremu.emu.CPU;
import dev.flaffy.horsepoweremu.emu.Memory;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;

public class MemoryViewPanel extends JPanel {

    private static final int COLS      = 8;
    private static final String[][] REGIONS = {
        {"Vectors    $0000–$00FF", "0000", "00FF"},
        {"ROM cart   $0100–$7FFF", "0100", "7FFF"},
        {"RAM        $8000–$FEFF", "8000", "FEFF"},
        {"Stack top  $FEFF",       "FE00", "FEFF"},
        {"MMIO       $FF00–$FFFF", "FF00", "FFFF"},
    };

    private final Memory memory;
    private final CPU    cpu;

    private final JComboBox<String>  regionBox;
    private final JTable             table;
    private final MemTableModel      model;

    private long lastUpdate = 0;

    public MemoryViewPanel(Memory memory, CPU cpu) {
        this.memory = memory;
        this.cpu    = cpu;
        setBackground(Theme.PANEL);
        setBorder(Theme.panelBorder("Memory View"));
        setLayout(new BorderLayout(0, 4));

        regionBox = new JComboBox<>();
        for (String[] r : REGIONS) regionBox.addItem(r[0]);
        regionBox.setFont(Theme.FONT_UI_SM);
        regionBox.setBackground(Theme.PANEL);
        regionBox.addActionListener(e -> {
            int i = regionBox.getSelectedIndex();
            int targetAddr = Integer.parseInt(REGIONS[i][1], 16);
            jumpToRow(targetAddr);
        });

        model = new MemTableModel();
        table = new JTable(model);
        table.setFont(Theme.FONT_MONO_SM);
        table.setForeground(Theme.TEXT_MONO);
        table.setBackground(Theme.PANEL);
        table.setGridColor(Theme.BORDER);
        table.setRowHeight(18);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setSelectionBackground(new Color(0xDCE8FF));
        table.setSelectionForeground(Theme.TEXT);
        table.setDefaultRenderer(Object.class, new MemCellRenderer());
        table.getTableHeader().setFont(Theme.FONT_MONO_SM);
        table.getTableHeader().setBackground(Theme.PANEL_ALT);
        table.getTableHeader().setForeground(Theme.TEXT_SEC);
        table.getColumnModel().getColumn(0).setPreferredWidth(56);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        for (int i = 1; i <= COLS; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(40);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(Theme.lineBorder());
        scroll.setBackground(Theme.PANEL);
        scroll.getViewport().setBackground(Theme.PANEL);

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setBackground(Theme.PANEL);
        top.setBorder(Theme.emptyBorder(0, 0, 2, 0));

        JButton btnPC = smallBtn("-> PC");
        btnPC.addActionListener(e -> jumpToRow(cpu.PC));
        JButton btnSP = smallBtn("-> SP");
        btnSP.addActionListener(e -> jumpToRow(cpu.SP));

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        nav.setBackground(Theme.PANEL);
        nav.add(btnPC); nav.add(btnSP);

        top.add(regionBox, BorderLayout.CENTER);
        top.add(nav, BorderLayout.EAST);

        add(top,   BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        Timer fadeTimer = new Timer(50, e -> repaint());
        fadeTimer.start();
    }

    private JButton smallBtn(String label) {
        JButton b = new JButton(label);
        b.setFont(Theme.FONT_UI_SM);
        b.setForeground(Theme.ACCENT);
        b.setBackground(Theme.PANEL);
        b.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void jumpToRow(int addr) {
        int row = addr / COLS;
        if (row >= 0 && row < table.getRowCount()) {
            Rectangle cellRect = table.getCellRect(row, 0, true);
            table.scrollRectToVisible(cellRect);
        }
    }

    public void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastUpdate >= 100) {
            model.fireTableDataChanged();
            lastUpdate = now;
        }
    }

    private class MemTableModel extends AbstractTableModel {
        final String[] colNames;

        MemTableModel() {
            colNames = new String[COLS + 1];
            colNames[0] = "Addr";
            for (int i = 0; i < COLS; i++) colNames[i + 1] = "+" + i;
        }

        @Override public int getRowCount()    { return 0x10000 / COLS; }
        @Override public int getColumnCount() { return COLS + 1; }
        @Override public String getColumnName(int c) { return colNames[c]; }

        @Override public Object getValueAt(int row, int col) {
            int base = row * COLS;
            if (col == 0) return String.format("$%04X", base);
            int addr = base + (col - 1);
            if (addr > 0xFFFF) return "";
            return String.format("%04X", memory.read(addr));
        }
    }

    private class MemCellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable t, Object val, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, val, sel, focus, row, col);
            setFont(Theme.FONT_MONO_SM);
            setBorder(Theme.emptyBorder(1, 4, 1, 4));

            if (col == 0) {
                setForeground(Theme.TEXT_SEC);
                setBackground(Theme.PANEL_ALT);
                setHorizontalAlignment(LEFT);
                return this;
            }

            int addr = row * COLS + (col - 1);
            if (addr > 0xFFFF) {
                setBackground(Theme.PANEL);
                setForeground(Theme.TEXT_SEC);
                return this;
            }

            setHorizontalAlignment(CENTER);

            boolean isPC = (addr == cpu.PC);
            boolean isSP = (addr == cpu.SP);

            if (isPC) {
                setBackground(new Color(0xDBE4FF));
                setForeground(Theme.ACCENT);
                setFont(Theme.FONT_MONO_BD);
            } else if (isSP) {
                setBackground(new Color(0xD3F9D8));
                setForeground(Theme.GREEN);
                setFont(Theme.FONT_MONO_BD);
            } else if (memory.isROM(addr)) {
                setBackground(Theme.HIGHLIGHT_ROM);
                setForeground(Theme.TEXT_MONO);
            } else {
                long written = memory.getWriteTime(addr);
                if (written > 0) {
                    long elapsed = System.currentTimeMillis() - written;
                    float to = Math.min(1f, (float) elapsed / Theme.HIGHLIGHT_MS);
                    Color bg = Theme.interpolate(Theme.HIGHLIGHT_FRESH, Theme.PANEL, to);
                    setBackground(bg);
                } else {
                    setBackground(Theme.PANEL);
                }
                setForeground(Theme.TEXT_MONO);
            }

            return this;
        }
    }
}
