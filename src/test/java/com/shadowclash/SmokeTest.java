package com.shadowclash;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
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

        verifyAttackDirection();
        System.out.println("Smoke OK: assets loaded and first frame rendered.");
    }

    private static void verifyAttackDirection() {
        CharacterAssets assets = new CharacterAssets(0, "Test Fighter", 100, 100, 100);
        Fighter fighter = Fighter.create(assets, assets.displayName, 500, GamePanel.GROUND_Y, 1, Color.WHITE);

        fighter.moveHorizontally(-1);
        if (fighter.facing != -1 || fighter.vx >= 0 || !fighter.startStrike(StrikeType.LIGHT)) {
            throw new IllegalStateException("Moving left did not set the fighter's attack direction.");
        }
        Rectangle2D leftHitBox = fighter.hitBox();
        if (leftHitBox.getMaxX() >= fighter.x) {
            throw new IllegalStateException("Left-facing attack hit box is on the wrong side.");
        }

        Fighter other = Fighter.create(assets, assets.displayName, 500, GamePanel.GROUND_Y, -1, Color.WHITE);
        other.moveHorizontally(1);
        if (other.facing != 1 || other.vx <= 0 || !other.startStrike(StrikeType.LIGHT)) {
            throw new IllegalStateException("Moving right did not set the fighter's attack direction.");
        }
        Rectangle2D rightHitBox = other.hitBox();
        if (rightHitBox.getMinX() <= other.x) {
            throw new IllegalStateException("Right-facing attack hit box is on the wrong side.");
        }
    }
}
