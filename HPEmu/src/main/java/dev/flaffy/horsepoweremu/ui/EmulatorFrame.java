package dev.flaffy.horsepoweremu.ui;

import dev.flaffy.horsepoweremu.emu.Emulator;
import dev.flaffy.horsepoweremu.emu.Memory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class EmulatorFrame extends JFrame {

    private final Emulator emu;
    private final DisplayPanel  display;
    private final CPUStatePanel cpuState;
    private final MemoryViewPanel memView;
    private final Toolbar toolbar;
    private final JLabel statusBar;
    private VramViewFrame vramView;

    private static final String[] ACTIONS = { "Up", "Down", "Left", "Right", "X", "Y", "Z", "Start" };
    private final Set<Integer> keysDown = new HashSet<>();
    private final int[][] padKeys = defaultBindings();
    private final int[] joyState = { 0, 0 };
    private int editingPad = 0;
    private int capturingAction = -1;
    private Runnable inputDialogRefresh;

    public EmulatorFrame() {
        super("HorsePower HVS-16 - HP16 CPU | V9958 VDP | YM2608");

        emu = new Emulator(this::onFrame);

        display  = new DisplayPanel(emu.vdp);
        cpuState = new CPUStatePanel(emu.cpu);
        memView  = new MemoryViewPanel(emu.memory, emu.cpu);

        toolbar = new Toolbar(
            this::loadROM,
            this::reset,
            this::toggleRunPause,
            this::stepOne,
            percent -> emu.setSpeed(percent / 100.0)
        );

        setJMenuBar(buildMenuBar());

        statusBar = new JLabel("  Ready - load a .hrom file to begin");
        statusBar.setFont(Theme.FONT_UI_SM);
        statusBar.setForeground(Theme.TEXT_SEC);
        statusBar.setBackground(Theme.PANEL_ALT);
        statusBar.setOpaque(true);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
            Theme.emptyBorder(3, 6, 3, 6)));

        setLayout(new BorderLayout());
        setBackground(Theme.BG);
        getRootPane().setBorder(Theme.emptyBorder(0, 0, 0, 0));

        add(toolbar,   BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(860, 500));
        setLocationRelativeTo(null);

        installInput();

        emu.start();
        refreshUI();
    }

    private static int[][] defaultBindings() {
        int[][] b = new int[2][8];
        b[0] = new int[]{ KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                          KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_ENTER };
        b[1] = new int[]{ KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_A, KeyEvent.VK_D,
                          KeyEvent.VK_J, KeyEvent.VK_K, KeyEvent.VK_L, KeyEvent.VK_B };
        return b;
    }

    private void installInput() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            int id = e.getID();
            if (id == KeyEvent.KEY_PRESSED)       return handleKey(e.getKeyCode(), true);
            else if (id == KeyEvent.KEY_RELEASED) return handleKey(e.getKeyCode(), false);
            return false;
        });
    }

    private boolean handleKey(int code, boolean down) {
        if (down && capturingAction >= 0) {
            padKeys[editingPad][capturingAction] = code;
            capturingAction = -1;
            if (inputDialogRefresh != null) SwingUtilities.invokeLater(inputDialogRefresh);
            return true;
        }
        if (down) { if (!keysDown.add(code)) return false; }
        else      { if (!keysDown.remove(code)) return false; }

        for (int p = 0; p < 2; p++) {
            int[] map = padKeys[p];
            for (int a = 0; a < 8; a++) {
                if (map[a] == code) {
                    if (down) joyState[p] |= (1 << a); else joyState[p] &= ~(1 << a);
                    if (p == 0) emu.io.setJoy1(joyState[0] & 0xff);
                    else        emu.io.setJoy2(joyState[1] & 0xff);
                }
            }
        }
        int sc = scancodeFor(code);
        if (sc >= 0) emu.io.pushInputEvent(0, down, sc);
        return false;
    }

    private void openInputConfig() {
        JDialog d = new JDialog(this, "Input Configuration", true);
        d.setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Edit bindings for:"));
        JComboBox<String> combo = new JComboBox<>(new String[]{ "Joypad 1", "Joypad 2" });
        combo.setSelectedIndex(editingPad);
        top.add(combo);
        d.add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(ACTIONS.length, 2, 6, 6));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton[] keyBtns = new JButton[ACTIONS.length];
        for (int i = 0; i < ACTIONS.length; i++) {
            grid.add(new JLabel(ACTIONS[i]));
            final int action = i;
            JButton kb = new JButton();
            kb.addActionListener(e -> { capturingAction = action; kb.setText("press a key…"); });
            keyBtns[i] = kb;
            grid.add(kb);
        }
        d.add(grid, BorderLayout.CENTER);

        Runnable refresh = () -> {
            for (int i = 0; i < ACTIONS.length; i++)
                keyBtns[i].setText(KeyEvent.getKeyText(padKeys[editingPad][i]));
        };
        refresh.run();
        inputDialogRefresh = refresh;

        combo.addActionListener(e -> { editingPad = combo.getSelectedIndex(); capturingAction = -1; refresh.run(); });

        JButton close = new JButton("Close");
        close.addActionListener(e -> d.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);
        d.add(bottom, BorderLayout.SOUTH);

        d.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent e) { inputDialogRefresh = null; capturingAction = -1; }
        });

        d.pack();
        d.setMinimumSize(new Dimension(280, d.getHeight()));
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private static int scancodeFor(int code) {
        switch (code) {
            case KeyEvent.VK_ENTER:      return 0x0D;
            case KeyEvent.VK_BACK_SPACE: return 0x08;
            case KeyEvent.VK_ESCAPE:     return 0x1B;
            case KeyEvent.VK_SPACE:      return 0x20;
            case KeyEvent.VK_UP:         return 0x80;
            case KeyEvent.VK_DOWN:       return 0x81;
            case KeyEvent.VK_LEFT:       return 0x82;
            case KeyEvent.VK_RIGHT:      return 0x83;
            default:
                if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) return code;
                if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) return code;
                return -1;
        }
    }

    private JComponent buildCenter() {
        JPanel displayWrap = new JPanel(new BorderLayout(0, 4));
        displayWrap.setBackground(Theme.BG);
        displayWrap.setBorder(Theme.emptyBorder(8, 8, 8, 4));

        JLabel dispLabel = new JLabel("Display    V9958 VDP  |  128KB VRAM |  MMIO $FF00–$FF03");
        dispLabel.setFont(Theme.FONT_UI_SM);
        dispLabel.setForeground(Theme.TEXT_SEC);

        displayWrap.add(dispLabel, BorderLayout.NORTH);
        displayWrap.add(display,   BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cpuState, memView);
        rightSplit.setResizeWeight(0.35);
        rightSplit.setDividerSize(5);
        rightSplit.setBorder(null);
        rightSplit.setBackground(Theme.BG);

        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.setBackground(Theme.BG);
        rightWrap.setBorder(Theme.emptyBorder(8, 4, 8, 8));
        rightWrap.add(rightSplit, BorderLayout.CENTER);
        rightWrap.setPreferredSize(new Dimension(280, 0));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, displayWrap, rightWrap);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setDividerSize(5);
        mainSplit.setBorder(null);
        mainSplit.setBackground(Theme.BG);
        mainSplit.setOneTouchExpandable(true);

        return mainSplit;
    }

    private void onFrame() {
        SwingUtilities.invokeLater(this::refreshUI);
    }

    private void refreshUI() {
        boolean running = !emu.isPaused();
        toolbar.setRunning(running);
        cpuState.refresh();
        display.refresh();
        memView.refresh();

        String state   = emu.cpu.halted ? "HALTED" : (running ? "RUNNING" : "PAUSED");
        String spd     = !running ? "" : String.format("  ·  %d%%", Math.round(emu.getSpeed() * 100));
        statusBar.setText(String.format(
            "  %s  ·  PC $%04X  ·  Cycles %,d  ·  Instrs %,d  ·  %.3f MHz%s",
            state, emu.cpu.PC, emu.cpu.totalCycles, emu.cpu.totalInstructions,
            emu.getClockMhz(), spd));
    }

    private void loadROM() {
        boolean was = !emu.isPaused();
        emu.setPaused(true);
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Load HorsePower ROM (.hrom)");
        fc.setFileFilter(new FileNameExtensionFilter("HorsePower ROM (*.hrom)", "hrom"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                emu.loadROM(fc.getSelectedFile());
                statusBar.setText("  Loaded: " + fc.getSelectedFile().getName()
                                  + "  ·  Entry $" + String.format("%04X", emu.cpu.PC));
                refreshUI();
                if (was) emu.setPaused(false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to load ROM:\n" + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            if (was) emu.setPaused(false);
        }
    }

    private void reset() {
        emu.reset();
        refreshUI();
    }

    private void toggleRunPause() {
        emu.setPaused(!emu.isPaused());
        refreshUI();
    }

    private void stepOne() {
        emu.setPaused(true);
        emu.requestStep();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem miLoad = new JMenuItem("Load ROM…");
        miLoad.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        miLoad.addActionListener(e -> loadROM());
        JMenuItem miReset = new JMenuItem("Reset");
        miReset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        miReset.addActionListener(e -> reset());
        JMenuItem miExit = new JMenuItem("Exit");
        miExit.addActionListener(e -> { emu.stop(); dispose(); System.exit(0); });
        file.add(miLoad);
        file.add(miReset);
        file.addSeparator();
        file.add(miExit);

        JMenu emulation = new JMenu("Emulation");
        JMenuItem miRun = new JMenuItem("Run / Pause");
        miRun.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        miRun.addActionListener(e -> toggleRunPause());
        JMenuItem miStep = new JMenuItem("Step");
        miStep.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        miStep.addActionListener(e -> stepOne());
        emulation.add(miRun);
        emulation.add(miStep);

        JMenu configure = new JMenu("Configure");
        JMenuItem miInput = new JMenuItem("Input…");
        miInput.addActionListener(e -> openInputConfig());
        configure.add(miInput);

        JMenu view = new JMenu("View");
        JMenu debug = new JMenu("Debug");
        JMenuItem miVram = new JMenuItem("VRAM Viewer");
        miVram.addActionListener(e -> openVramView());
        debug.add(miVram);
        view.add(debug);

        bar.add(file);
        bar.add(emulation);
        bar.add(configure);
        bar.add(view);
        return bar;
    }

    private void openVramView() {
        if (vramView == null || !vramView.isDisplayable()) {
            vramView = new VramViewFrame(emu.vdp);
        }
        vramView.setVisible(true);
        vramView.toFront();
    }
}