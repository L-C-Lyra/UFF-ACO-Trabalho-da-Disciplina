package dev.flaffy.horsepoweremu.ui;

import dev.flaffy.horsepoweremu.emu.CPU;

import javax.swing.*;
import java.awt.*;

public class CPUStatePanel extends JPanel {
    private final CPU cpu;

    private final JLabel[] regLabels = new JLabel[10];
    private static final String[] REG_NAMES = {"D0","D1","D2","D3","D4","D5","A0","A1","SP","PC"};
    private JLabel lblST;
    private JLabel lblInstr, lblLastPC, lblCycles, lblICount, lblStatus;

    private final JLabel[] flagLabels = new JLabel[6];
    private static final String[] FLAG_NAMES = {"N","V","E","I","Z","C"};
    private static final int[] FLAG_BITS = {
        CPU.F_N, CPU.F_V, CPU.F_E, CPU.F_I, CPU.F_Z, CPU.F_C
    };
    private static final Color[] FLAG_COLORS = {
        Theme.FLAG_N, Theme.FLAG_V, Theme.FLAG_B, Theme.FLAG_I, Theme.FLAG_Z, Theme.FLAG_C
    };

    public CPUStatePanel(CPU cpu) {
        this.cpu = cpu;
        setBackground(Theme.PANEL);
        setBorder(Theme.panelBorder("CPU State (HP16)"));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        build();
    }

    private void build() {
        JPanel regs = new JPanel(new GridLayout(5, 4, 4, 3));
        regs.setBackground(Theme.PANEL);
        regs.setBorder(Theme.emptyBorder(4, 6, 4, 6));
        for (int i = 0; i < REG_NAMES.length; i++) regLabels[i] = addReg(regs, REG_NAMES[i]);
        add(regs);

        JPanel stRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        stRow.setBackground(Theme.PANEL);
        JLabel stName = new JLabel("ST:");
        stName.setFont(Theme.FONT_MONO_SM);
        stName.setForeground(Theme.TEXT_SEC);
        lblST = new JLabel("$00");
        lblST.setFont(Theme.FONT_MONO_BD);
        lblST.setForeground(Theme.TEXT);
        stRow.add(stName); stRow.add(lblST);
        add(stRow);

        JSeparator sep1 = new JSeparator();
        sep1.setForeground(Theme.BORDER);
        sep1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        add(sep1);

        JPanel flagRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 6));
        flagRow.setBackground(Theme.PANEL);
        for (int i = 0; i < 6; i++) {
            JLabel fl = new JLabel(FLAG_NAMES[i], SwingConstants.CENTER);
            fl.setFont(Theme.FONT_MONO_BD);
            fl.setPreferredSize(new Dimension(26, 24));
            fl.setOpaque(true);
            fl.setBackground(Theme.FLAG_OFF);
            fl.setForeground(Theme.TEXT_SEC);
            fl.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            flagLabels[i] = fl;
            flagRow.add(fl);
        }
        add(flagRow);

        JSeparator sep2 = new JSeparator();
        sep2.setForeground(Theme.BORDER);
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        add(sep2);

        JPanel info = new JPanel(new GridLayout(5, 2, 4, 3));
        info.setBackground(Theme.PANEL);
        info.setBorder(Theme.emptyBorder(6, 6, 4, 6));
        lblLastPC = addInfo(info, "Last PC");
        lblInstr  = addInfo(info, "Instr");
        lblCycles = addInfo(info, "Cycles");
        lblICount = addInfo(info, "Instrs");
        lblStatus = addInfo(info, "State");
        add(info);
    }

    private JLabel addReg(JPanel p, String name) {
        JLabel nl = new JLabel(name + ":");
        nl.setFont(Theme.FONT_MONO_SM);
        nl.setForeground(Theme.TEXT_SEC);
        p.add(nl);
        JLabel vl = new JLabel("$0000");
        vl.setFont(Theme.FONT_MONO_BD);
        vl.setForeground(Theme.TEXT);
        p.add(vl);
        return vl;
    }

    private JLabel addInfo(JPanel p, String name) {
        JLabel nl = new JLabel(name + ":");
        nl.setFont(Theme.FONT_UI_SM);
        nl.setForeground(Theme.TEXT_SEC);
        p.add(nl);
        JLabel vl = new JLabel("---");
        vl.setFont(Theme.FONT_MONO_SM);
        vl.setForeground(Theme.TEXT);
        p.add(vl);
        return vl;
    }

    public void refresh() {
        for (int i = 0; i < 8; i++) regLabels[i].setText(String.format("$%04X", cpu.R[i]));
        regLabels[8].setText(String.format("$%04X", cpu.SP));
        regLabels[9].setText(String.format("$%04X", cpu.PC));
        lblST.setText(String.format("$%02X", cpu.ST & 0xFF));

        for (int i = 0; i < 6; i++) {
            boolean active = (cpu.ST & FLAG_BITS[i]) != 0;
            flagLabels[i].setBackground(active ? FLAG_COLORS[i] : Theme.FLAG_OFF);
            flagLabels[i].setForeground(active ? Theme.FLAG_TEXT : Theme.TEXT_SEC);
        }

        lblLastPC.setText(String.format("$%04X", cpu.lastPC));
        lblInstr.setText(cpu.lastMnem);
        lblCycles.setText(String.format("%,d", cpu.totalCycles));
        lblICount.setText(String.format("%,d", cpu.totalInstructions));

        if (cpu.halted) {
            lblStatus.setText("HALTED");
            lblStatus.setForeground(Theme.RED);
        } else {
            lblStatus.setText("OK");
            lblStatus.setForeground(Theme.GREEN);
        }
    }
}
