package dev.flaffy.horsepoweremu.ui;

import dev.flaffy.horsepoweremu.emu.video.V9958;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class DisplayPanel extends JPanel {

    private final V9958 vdp;
    private BufferedImage img = new BufferedImage(272, V9958.RENDER_HEIGHT, BufferedImage.TYPE_INT_RGB);

    public DisplayPanel(V9958 vdp) {
        this.vdp = vdp;
        setBackground(Color.BLACK);
        setBorder(Theme.lineBorder());
        setPreferredSize(new Dimension(272 * 2, V9958.RENDER_HEIGHT * 2));
        setMinimumSize(new Dimension(272, V9958.RENDER_HEIGHT));
    }

    public void refresh() {
        int w = vdp.getFrontWidth();
        int h = vdp.getFrontHeight();
        int stride = vdp.getLineStride();
        int[] fb = vdp.getFrontBuffer();

        if (img.getWidth() != w || img.getHeight() != h)
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            int base = y * stride;
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, fb[base + x]);
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int pw = getWidth();
        int ph = getHeight();
        int iw = img.getWidth();
        int ih = img.getHeight();

        double sx = (double) pw / iw;
        double sy = (double) ph / ih;
        double scale = Math.min(sx, sy);
        int dw = (int) (iw * scale);
        int dh = (int) (ih * scale);
        int ox = (pw - dw) / 2;
        int oy = (ph - dh) / 2;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(img, ox, oy, dw, dh, null);
    }
}
