package dev.flaffy.horsepoweremu;

import dev.flaffy.horsepoweremu.ui.EmulatorFrame;
import dev.flaffy.horsepoweremu.ui.Theme;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        Theme.applyGlobally();
        SwingUtilities.invokeLater(() -> new EmulatorFrame().setVisible(true));
    }
}