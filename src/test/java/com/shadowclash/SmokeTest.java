package com.shadowclash;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class SmokeTest {
    private SmokeTest() {
    }

    public static void main(String[] args) {
        GamePanel panel = new GamePanel();
        panel.setSize(GamePanel.VIEW_W, GamePanel.VIEW_H);
        BufferedImage image = new BufferedImage(GamePanel.VIEW_W, GamePanel.VIEW_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();

        int sample = image.getRGB(GamePanel.VIEW_W / 2, GamePanel.VIEW_H / 2);
        if ((sample >>> 24) == 0) {
            throw new IllegalStateException("Smoke render produced a transparent center pixel.");
        }
        System.out.println("Smoke OK: assets loaded and first frame rendered.");
    }
}
