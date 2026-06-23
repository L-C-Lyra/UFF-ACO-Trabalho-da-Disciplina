package dev.flaffy.horsepoweremu.ui;

import dev.flaffy.horsepoweremu.emu.video.V9958;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VramViewFrame extends JFrame {

    private static final int MODE_TILES = 0;
    private static final int MODE_4BPP  = 1;
    private static final int MODE_8BPP  = 2;
    private static final int MODE_GRAY  = 3;

    private static final int STRIP_GAP = 10;

    private final V9958 vdp;
    private final VramCanvas canvas;
    private final JComboBox<String> modeBox;
    private final JComboBox<String> zoomBox;
    private final JComboBox<String> stripBox;
    private final JLabel info;
    private final Timer timer;

    private int mode = MODE_TILES;
    private int zoom = 2;
    private int strips = 4;

    public VramViewFrame(V9958 vdp) {
        super("VRAM Viewer - V9958 - 128 KB");
        this.vdp = vdp;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.setBackground(Theme.PANEL_ALT);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        JLabel ml = new JLabel("View:");
        ml.setFont(Theme.FONT_UI_SM); ml.setForeground(Theme.TEXT_SEC);
        top.add(ml);
        modeBox = new JComboBox<>(new String[]{
            "Patterns (1bpp 8×8 tiles)", "Bitmap 4bpp (SCREEN 5)", "Bitmap 8bpp (SCREEN 8)", "Raw bytes (grayscale)" });
        modeBox.setFont(Theme.FONT_UI_SM);
        modeBox.addActionListener(e -> { mode = modeBox.getSelectedIndex(); rebuild(); });
        top.add(modeBox);

        JLabel zl = new JLabel("Zoom:");
        zl.setFont(Theme.FONT_UI_SM); zl.setForeground(Theme.TEXT_SEC);
        top.add(zl);
        zoomBox = new JComboBox<>(new String[]{ "1×", "2×", "3×", "4×" });
        zoomBox.setSelectedIndex(1);
        zoomBox.setFont(Theme.FONT_UI_SM);
        zoomBox.addActionListener(e -> { zoom = zoomBox.getSelectedIndex() + 1; rebuild(); });
        top.add(zoomBox);

        JLabel sl = new JLabel("Strips:");
        sl.setFont(Theme.FONT_UI_SM); sl.setForeground(Theme.TEXT_SEC);
        top.add(sl);
        stripBox = new JComboBox<>(new String[]{ "1", "2", "4", "8", "16" });
        stripBox.setSelectedIndex(2);
        stripBox.setFont(Theme.FONT_UI_SM);
        stripBox.addActionListener(e -> { strips = Integer.parseInt((String) stripBox.getSelectedItem()); rebuild(); });
        top.add(stripBox);

        add(top, BorderLayout.NORTH);

        canvas = new VramCanvas();
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(0x101216));
        add(scroll, BorderLayout.CENTER);

        info = new JLabel("  ");
        info.setFont(Theme.FONT_MONO_SM);
        info.setForeground(Theme.TEXT_SEC);
        info.setBackground(Theme.PANEL_ALT);
        info.setOpaque(true);
        info.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), Theme.emptyBorder(3, 6, 3, 6)));
        add(info, BorderLayout.SOUTH);

        rebuild();
        setSize(560, 640);
        setLocationByPlatform(true);

        timer = new Timer(50, e -> { canvas.redraw(); });
        timer.start();
    }

    @Override public void dispose() {
        if (timer != null) timer.stop();
        super.dispose();
    }

    private void rebuild() {
        canvas.rebuild();
        canvas.revalidate();
        canvas.redraw();
    }

    private class VramCanvas extends JPanel {
        private BufferedImage img;
        private int imgW, imgH;

        VramCanvas() { setBackground(new Color(0x101216)); }

        private int sliceH;

        void rebuild() {
            switch (mode) {
                case MODE_TILES: imgW = 256; imgH = (V9958.VRAM_SIZE / 8) / 32 * 8; break;
                case MODE_4BPP:  imgW = 256; imgH = V9958.VRAM_SIZE / 128; break;
                case MODE_8BPP:  imgW = 256; imgH = V9958.VRAM_SIZE / 256; break;
                default:         imgW = 256; imgH = V9958.VRAM_SIZE / 256; break;
            }
            img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
            sliceH = (imgH + strips - 1) / strips;
            int w = strips * imgW * zoom + (strips - 1) * STRIP_GAP;
            int h = sliceH * zoom;
            setPreferredSize(new Dimension(w, h));
        }

        void redraw() {
            int[] vram = vdp.getVRAM();
            switch (mode) {
                case MODE_TILES: drawTiles(vram); break;
                case MODE_4BPP:  draw4bpp(vram); break;
                case MODE_8BPP:  draw8bpp(vram); break;
                default:         drawGray(vram); break;
            }
            repaint();
        }

        private void drawTiles(int[] vram) {
            int tilesPerRow = 32;
            int tiles = V9958.VRAM_SIZE / 8;
            for (int t = 0; t < tiles; t++) {
                int tx = (t % tilesPerRow) * 8;
                int ty = (t / tilesPerRow) * 8;
                int base = t * 8;
                for (int row = 0; row < 8; row++) {
                    int bits = vram[base + row];
                    for (int col = 0; col < 8; col++) {
                        int on = (bits >> (7 - col)) & 1;
                        img.setRGB(tx + col, ty + row, on != 0 ? 0xFFFFFF : 0x202830);
                    }
                }
            }
        }

        private void draw4bpp(int[] vram) {
            int[] pal = vdp.getPaletteARGB();
            int p = 0;
            for (int y = 0; y < imgH; y++) {
                for (int x = 0; x < imgW; x += 2) {
                    int b = vram[p++];
                    img.setRGB(x, y, pal[(b >> 4) & 0x0f] & 0xFFFFFF);
                    img.setRGB(x + 1, y, pal[b & 0x0f] & 0xFFFFFF);
                }
            }
        }

        private void draw8bpp(int[] vram) {
            int p = 0;
            for (int y = 0; y < imgH; y++)
                for (int x = 0; x < imgW; x++)
                    img.setRGB(x, y, vdp.color8(vram[p++]) & 0xFFFFFF);
        }

        private void drawGray(int[] vram) {
            int p = 0;
            for (int y = 0; y < imgH; y++)
                for (int x = 0; x < imgW; x++) {
                    int v = vram[p++] & 0xff;
                    img.setRGB(x, y, (v << 16) | (v << 8) | v);
                }
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (img == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int dw = imgW * zoom;
            for (int k = 0; k < strips; k++) {
                int sy0 = k * sliceH;
                int sy1 = Math.min(imgH, sy0 + sliceH);
                if (sy0 >= imgH) break;
                int dx = k * (dw + STRIP_GAP);
                g2.drawImage(img, dx, 0, dx + dw, (sy1 - sy0) * zoom, 0, sy0, imgW, sy1, null);
                if (k > 0) {
                    g2.setColor(new Color(0x303642));
                    g2.fillRect(dx - STRIP_GAP, 0, STRIP_GAP, getHeight());
                }
            }
        }
    }
}
