package com.shadowclash;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class ShadowClash {
    private ShadowClash() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Shadow Clash - Java Fighting Game");
            GamePanel panel = new GamePanel();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.start();
        });
    }
}

enum Scene {
    MENU,
    MODE_SELECT,
    CHARACTER_SELECT,
    BATTLE,
    RESULT
}

enum GameMode {
    VERSUS,
    CPU
}

enum Anim {
    IDLE,
    WALK,
    JUMP,
    ATTACK,
    KICK,
    SKILL,
    ULTIMATE,
    HURT,
    DEAD,
    BLOCK
}

enum StrikeType {
    LIGHT,
    SKILL_ONE,
    SKILL_TWO,
    ULTIMATE
}

final class GamePanel extends JPanel implements Runnable {
    static final int VIEW_W = 960;
    static final int VIEW_H = 540;
    static final int WORLD_W = 1650;
    static final double GROUND_Y = 430.0;
    static final double GRAVITY = 1850.0;

    private final BufferedImage backBuffer = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
    private final boolean[] keys = new boolean[1024];
    private final boolean[] previousKeys = new boolean[1024];
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final AssetStore assets;
    private final AudioEngine audio = new AudioEngine();
    private final AIController aiController = new AIController();
    private volatile boolean running;
    private Thread gameThread;

    private Scene scene = Scene.MENU;
    private GameMode gameMode = GameMode.VERSUS;
    private int menuIndex;
    private int modeIndex;
    private int p1CharacterIndex;
    private int p2CharacterIndex = 1;
    private Fighter p1;
    private Fighter p2;
    private double cameraX;
    private double roundTime = 99.0;
    private double screenShake;
    private double hitFreeze;
    private double pulseTime;
    private String resultTitle = "";
    private String resultSubtitle = "";

    GamePanel() {
        setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        setMinimumSize(new Dimension(800, 450));
        setFocusable(true);
        assets = new AssetStore(Path.of("assets", "processed"));
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code >= 0 && code < keys.length) {
                    keys[code] = true;
                }
                handleMenuKey(code);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                if (code >= 0 && code < keys.length) {
                    keys[code] = false;
                }
            }
        });
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        audio.startBgm();
        gameThread = new Thread(this, "shadow-clash-loop");
        gameThread.setDaemon(true);
        gameThread.start();
        requestFocusInWindow();
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dt = Math.min(0.033, (now - last) / 1_000_000_000.0);
            last = now;
            update(dt);
            repaint();
            try {
                Thread.sleep(2);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handleMenuKey(int code) {
        if (scene == Scene.BATTLE) {
            if (code == KeyEvent.VK_ESCAPE) {
                scene = Scene.MENU;
                audio.playMenu();
            }
            return;
        }

        if (code == KeyEvent.VK_ESCAPE) {
            if (scene == Scene.MENU) {
                return;
            }
            scene = Scene.MENU;
            audio.playMenu();
            return;
        }

        if (scene == Scene.MENU) {
            if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                menuIndex = wrap(menuIndex - 1, 3);
                audio.playMenu();
            } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                menuIndex = wrap(menuIndex + 1, 3);
                audio.playMenu();
            } else if (isConfirm(code)) {
                audio.playMenu();
                if (menuIndex == 0) {
                    scene = Scene.MODE_SELECT;
                } else if (menuIndex == 1) {
                    scene = Scene.CHARACTER_SELECT;
                } else {
                    System.exit(0);
                }
            }
        } else if (scene == Scene.MODE_SELECT) {
            if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_A || code == KeyEvent.VK_D) {
                modeIndex = 1 - modeIndex;
                audio.playMenu();
            } else if (isConfirm(code)) {
                gameMode = modeIndex == 0 ? GameMode.VERSUS : GameMode.CPU;
                scene = Scene.CHARACTER_SELECT;
                audio.playMenu();
            }
        } else if (scene == Scene.CHARACTER_SELECT) {
            if (code == KeyEvent.VK_A || code == KeyEvent.VK_D) {
                p1CharacterIndex = 1 - p1CharacterIndex;
                audio.playMenu();
            } else if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT) {
                p2CharacterIndex = 1 - p2CharacterIndex;
                audio.playMenu();
            } else if (isConfirm(code)) {
                startBattle();
                audio.playStart();
            }
        } else if (scene == Scene.RESULT && isConfirm(code)) {
            scene = Scene.MODE_SELECT;
            audio.playMenu();
        }
    }

    private boolean isConfirm(int code) {
        return code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE;
    }

    private int wrap(int value, int max) {
        if (value < 0) {
            return max - 1;
        }
        if (value >= max) {
            return 0;
        }
        return value;
    }

    private void startBattle() {
        CharacterAssets p1Assets = assets.character(p1CharacterIndex);
        CharacterAssets p2Assets = assets.character(p2CharacterIndex);
        p1 = Fighter.create(p1Assets, p1Assets.displayName, 260, GROUND_Y, 1, Color.decode("#50E3FF"));
        p2 = Fighter.create(p2Assets, gameMode == GameMode.CPU ? p2Assets.displayName + " CPU" : p2Assets.displayName,
                1390, GROUND_Y, -1, Color.decode("#FF4A6A"));
        if (p1CharacterIndex == p2CharacterIndex) {
            p2.paletteShift = new Color(80, 150, 255, 70);
        }
        p1.faceToward(p2);
        p2.faceToward(p1);
        roundTime = 99.0;
        screenShake = 0.0;
        hitFreeze = 0.0;
        cameraX = 0.0;
        particles.clear();
        floatingTexts.clear();
        aiController.reset();
        scene = Scene.BATTLE;
    }

    private void update(double dt) {
        pulseTime += dt;
        updateParticles(dt);
        if (screenShake > 0) {
            screenShake = Math.max(0, screenShake - dt * 16.0);
        }

        if (hitFreeze > 0) {
            hitFreeze = Math.max(0, hitFreeze - dt);
            dt *= 0.18;
        }

        if (scene == Scene.BATTLE) {
            updateBattle(dt);
        }
        System.arraycopy(keys, 0, previousKeys, 0, keys.length);
    }

    private void updateBattle(double dt) {
        roundTime = Math.max(0.0, roundTime - dt);
        handleHumanInput(p1, Controls.P1, dt);
        if (gameMode == GameMode.VERSUS) {
            handleHumanInput(p2, Controls.P2, dt);
        } else {
            aiController.update(dt, p2, p1, audio);
        }

        p1.faceToward(p2);
        p2.faceToward(p1);
        p1.update(dt);
        p2.update(dt);
        resolveCollision();
        resolveStrike(p1, p2);
        resolveStrike(p2, p1);
        updateCamera(dt);
        checkRoundEnd();
    }

    private void handleHumanInput(Fighter fighter, Controls controls, double dt) {
        if (fighter.dead) {
            return;
        }

        double move = 0.0;
        if (down(controls.left)) {
            move -= 1.0;
        }
        if (down(controls.right)) {
            move += 1.0;
        }

        fighter.blocking = down(controls.block) && fighter.canBlock();
        if (fighter.blocking) {
            fighter.vx = approach(fighter.vx, 0, 1500 * dt);
        } else if (fighter.canMove()) {
            if (move != 0.0) {
                fighter.vx = move * fighter.speed;
                fighter.facing = move > 0 ? 1 : -1;
            } else if (fighter.onGround) {
                fighter.vx = approach(fighter.vx, 0, 1800 * dt);
            }
        }

        if (just(controls.jump)) {
            fighter.jump();
        }
        if (just(controls.dash) && fighter.startDash()) {
            audio.playDash();
            spawnDashDust(fighter);
        }
        if (just(controls.light)) {
            startStrikeWithAudio(fighter, StrikeType.LIGHT);
        }
        if (just(controls.skillOne)) {
            startStrikeWithAudio(fighter, StrikeType.SKILL_ONE);
        }
        if (just(controls.skillTwo)) {
            startStrikeWithAudio(fighter, StrikeType.SKILL_TWO);
        }
        if (just(controls.ultimate)) {
            startStrikeWithAudio(fighter, StrikeType.ULTIMATE);
        }
    }

    private void startStrikeWithAudio(Fighter fighter, StrikeType type) {
        if (!fighter.startStrike(type)) {
            return;
        }
        if (type == StrikeType.LIGHT) {
            audio.playSwing();
        } else if (type == StrikeType.ULTIMATE) {
            audio.playUltimate();
            spawnAura(fighter, fighter.teamColor, 18);
            screenShake = Math.max(screenShake, 8.0);
        } else {
            audio.playSkill();
            spawnAura(fighter, fighter.teamColor, 9);
        }
    }

    private boolean down(int... codes) {
        for (int code : codes) {
            if (code >= 0 && code < keys.length && keys[code]) {
                return true;
            }
        }
        return false;
    }

    private boolean just(int... codes) {
        for (int code : codes) {
            if (code >= 0 && code < keys.length && keys[code] && !previousKeys[code]) {
                return true;
            }
        }
        return false;
    }

    private double approach(double value, double target, double delta) {
        if (value < target) {
            return Math.min(target, value + delta);
        }
        return Math.max(target, value - delta);
    }

    private void resolveCollision() {
        double minGap = 42.0;
        double overlap = minGap - Math.abs(p1.x - p2.x);
        if (overlap <= 0) {
            return;
        }
        double push = overlap / 2.0;
        if (p1.x < p2.x) {
            p1.x -= push;
            p2.x += push;
        } else {
            p1.x += push;
            p2.x -= push;
        }
        p1.clampToWorld();
        p2.clampToWorld();
    }

    private void resolveStrike(Fighter attacker, Fighter defender) {
        Strike strike = attacker.currentStrike;
        if (strike == null || strike.hasHit || !strike.isActive(attacker.attackElapsed) || defender.dead || defender.invulnerable > 0) {
            return;
        }

        Rectangle2D hitBox = attacker.hitBox();
        if (!hitBox.intersects(defender.hurtBox())) {
            return;
        }

        int directionToDefender = attacker.x < defender.x ? 1 : -1;
        boolean blocked = defender.blocking && defender.facing == -directionToDefender && defender.energy > 2.0;
        double damage = blocked ? strike.damage * 0.24 : strike.damage;
        double stun = blocked ? 0.13 : strike.hitStun;
        double knock = blocked ? strike.knockback * 0.34 : strike.knockback;

        strike.hasHit = true;
        defender.applyHit(damage, stun, knock * directionToDefender, strike.launch, blocked);
        attacker.energy = Math.min(100.0, attacker.energy + (blocked ? 4.0 : strike.energyGain));
        if (blocked) {
            defender.energy = Math.max(0.0, defender.energy - 8.0);
            audio.playBlock();
            spawnBlockSparks(defender, directionToDefender);
            floatingTexts.add(new FloatingText("BLOCK", defender.x, defender.y - 135, Color.decode("#9AE6FF")));
        } else {
            audio.playHit(strike.type);
            spawnHitSparks(defender, strike.color, strike.type == StrikeType.ULTIMATE ? 34 : 18);
            floatingTexts.add(new FloatingText("-" + Math.round(damage), defender.x, defender.y - 145, Color.WHITE));
            hitFreeze = Math.max(hitFreeze, strike.type == StrikeType.ULTIMATE ? 0.12 : 0.045);
            screenShake = Math.max(screenShake, strike.screenShake);
        }
    }

    private void checkRoundEnd() {
        boolean ended = p1.dead || p2.dead || roundTime <= 0.0;
        if (!ended) {
            return;
        }
        int winner;
        if (p1.hp == p2.hp) {
            winner = 0;
        } else {
            winner = p1.hp > p2.hp ? 1 : 2;
        }
        if (winner == 0) {
            resultTitle = "DRAW";
            resultSubtitle = "Both fighters are still standing.";
        } else if (winner == 1) {
            resultTitle = "PLAYER 1 WINS";
            resultSubtitle = p1.name + " dominates the arena.";
        } else {
            resultTitle = gameMode == GameMode.CPU ? "CPU WINS" : "PLAYER 2 WINS";
            resultSubtitle = p2.name + " controls the fight.";
        }
        audio.playVictory();
        scene = Scene.RESULT;
    }

    private void updateCamera(double dt) {
        double midpoint = (p1.x + p2.x) / 2.0;
        double target = clamp(midpoint - VIEW_W / 2.0, 0, WORLD_W - VIEW_W);
        cameraX += (target - cameraX) * Math.min(1.0, dt * 5.5);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateParticles(double dt) {
        for (Iterator<Particle> iterator = particles.iterator(); iterator.hasNext();) {
            Particle particle = iterator.next();
            particle.update(dt);
            if (particle.life <= 0) {
                iterator.remove();
            }
        }
        for (Iterator<FloatingText> iterator = floatingTexts.iterator(); iterator.hasNext();) {
            FloatingText text = iterator.next();
            text.update(dt);
            if (text.life <= 0) {
                iterator.remove();
            }
        }
    }

    private void spawnHitSparks(Fighter target, Color base, int amount) {
        for (int i = 0; i < amount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 80 + random.nextDouble() * 360;
            Color color = i % 3 == 0 ? Color.WHITE : base;
            particles.add(Particle.spark(target.x + randomRange(-16, 16), target.y - 100 + randomRange(-24, 24),
                    Math.cos(angle) * speed, Math.sin(angle) * speed, color, randomRange(0.18, 0.42)));
        }
        particles.add(Particle.ring(target.x, target.y - 104, base, 0.42));
    }

    private void spawnBlockSparks(Fighter target, int direction) {
        for (int i = 0; i < 12; i++) {
            particles.add(Particle.spark(target.x - direction * 22, target.y - 106 + randomRange(-20, 20),
                    -direction * randomRange(80, 260), randomRange(-190, 190), Color.decode("#9AE6FF"), randomRange(0.12, 0.28)));
        }
        particles.add(Particle.ring(target.x - direction * 26, target.y - 108, Color.decode("#9AE6FF"), 0.24));
    }

    private void spawnAura(Fighter fighter, Color color, int amount) {
        for (int i = 0; i < amount; i++) {
            particles.add(Particle.spark(fighter.x + randomRange(-28, 28), fighter.y - randomRange(38, 150),
                    randomRange(-70, 70), randomRange(-240, -40), color, randomRange(0.25, 0.65)));
        }
        particles.add(Particle.ring(fighter.x, fighter.y - 95, color, 0.55));
    }

    private void spawnDashDust(Fighter fighter) {
        for (int i = 0; i < 10; i++) {
            particles.add(Particle.dust(fighter.x - fighter.facing * randomRange(12, 52), fighter.y - randomRange(2, 18),
                    -fighter.facing * randomRange(80, 260), randomRange(-60, 20), new Color(205, 210, 226, 130),
                    randomRange(0.25, 0.5)));
        }
    }

    private double randomRange(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = backBuffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        render(g);
        g.dispose();

        Graphics2D out = (Graphics2D) graphics.create();
        out.setColor(Color.BLACK);
        out.fillRect(0, 0, getWidth(), getHeight());
        double scale = Math.min(getWidth() / (double) VIEW_W, getHeight() / (double) VIEW_H);
        int w = (int) Math.round(VIEW_W * scale);
        int h = (int) Math.round(VIEW_H * scale);
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        out.drawImage(backBuffer, x, y, w, h, null);
        out.dispose();
    }

    private void render(Graphics2D g) {
        double shakeX = screenShake > 0 ? randomRange(-screenShake, screenShake) : 0.0;
        double shakeY = screenShake > 0 ? randomRange(-screenShake * 0.65, screenShake * 0.65) : 0.0;
        g.translate(shakeX, shakeY);
        drawStage(g);
        drawParticles(g, false);
        if (scene == Scene.BATTLE || scene == Scene.RESULT) {
            drawFighter(g, p1);
            drawFighter(g, p2);
        }
        drawParticles(g, true);
        g.translate(-shakeX, -shakeY);

        if (scene == Scene.BATTLE) {
            drawHud(g);
        } else if (scene == Scene.MENU) {
            drawMainMenu(g);
        } else if (scene == Scene.MODE_SELECT) {
            drawModeSelect(g);
        } else if (scene == Scene.CHARACTER_SELECT) {
            drawCharacterSelect(g);
        } else if (scene == Scene.RESULT) {
            drawHud(g);
            drawResult(g);
        }
        drawVignette(g);
    }

    private void drawStage(Graphics2D g) {
        GradientPaint sky = new GradientPaint(0, 0, Color.decode("#11172F"), 0, VIEW_H, Color.decode("#1C1020"));
        g.setPaint(sky);
        g.fillRect(0, 0, VIEW_W, VIEW_H);

        drawPixelTiled(g, assets.stageBack, -cameraX * 0.18, 22, 8.0, 0.72f);
        drawPixelTiled(g, assets.stageFore, -cameraX * 0.68, -36, 4.0, 1.0f);
        drawProp(g, assets.car, 550, GROUND_Y - 64, 3.2);
        drawProp(g, assets.barrel, 178, GROUND_Y - 34, 2.6);
        drawProp(g, assets.hydrant, 1130, GROUND_Y - 32, 2.5);

        g.setPaint(new GradientPaint(0, (float) GROUND_Y + 8, new Color(16, 14, 22, 190),
                0, VIEW_H, new Color(9, 8, 16, 245)));
        g.fillRect(0, (int) GROUND_Y + 6, VIEW_W, VIEW_H - (int) GROUND_Y);
        g.setColor(new Color(255, 255, 255, 32));
        g.drawLine(0, (int) GROUND_Y + 8, VIEW_W, (int) GROUND_Y + 8);
    }

    private void drawPixelTiled(Graphics2D g, BufferedImage image, double offsetX, double y, double scale, float alpha) {
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int tileW = (int) Math.round(image.getWidth() * scale);
        int tileH = (int) Math.round(image.getHeight() * scale);
        int start = (int) Math.floor(offsetX % tileW) - tileW;
        AlphaComposite old = (AlphaComposite) g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        for (int x = start; x < VIEW_W + tileW; x += tileW) {
            g.drawImage(image, x, (int) Math.round(y), tileW, tileH, null);
        }
        g.setComposite(old);
        if (interpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        }
    }

    private void drawProp(Graphics2D g, BufferedImage image, double worldX, double worldY, double scale) {
        int w = (int) Math.round(image.getWidth() * scale);
        int h = (int) Math.round(image.getHeight() * scale);
        int x = (int) Math.round(worldX - cameraX);
        int y = (int) Math.round(worldY);
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, x, y, w, h, null);
        if (interpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        }
    }

    private void drawFighter(Graphics2D g, Fighter fighter) {
        if (fighter == null) {
            return;
        }
        double sx = fighter.x - cameraX;
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int shadowW = (int) (assets.shadow.getWidth() * 4.2);
        int shadowH = (int) (assets.shadow.getHeight() * 3.0);
        g.drawImage(assets.shadow, (int) (sx - shadowW / 2.0), (int) (fighter.y - shadowH / 2.0 + 3),
                shadowW, shadowH, null);

        if (fighter.energy > 84.0 && !fighter.dead) {
            float auraAlpha = (float) (0.18 + 0.12 * Math.sin(pulseTime * 8.0));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, auraAlpha));
            g.setColor(fighter.teamColor);
            g.fill(new Ellipse2D.Double(sx - 52, fighter.y - 156, 104, 150));
            g.setComposite(AlphaComposite.SrcOver);
        }

        BufferedImage frame = fighter.currentFrame();
        int drawW = (int) Math.round(frame.getWidth() * fighter.renderScale);
        int drawH = (int) Math.round(frame.getHeight() * fighter.renderScale);
        int drawX = (int) Math.round(sx - drawW / 2.0);
        int drawY = (int) Math.round(fighter.y - drawH + 8);
        boolean flash = fighter.hitFlash > 0.0 && ((int) (fighter.hitFlash * 36.0) % 2 == 0);
        BufferedImage image = flash ? tintImage(frame, Color.WHITE, 0.88f) : frame;
        if (fighter.paletteShift != null && !flash) {
            image = tintImage(frame, fighter.paletteShift, 0.42f);
        }
        if (fighter.facing >= 0) {
            g.drawImage(image, drawX, drawY, drawW, drawH, null);
        } else {
            g.drawImage(image, drawX + drawW, drawY, -drawW, drawH, null);
        }

        if (fighter.blocking && !fighter.dead) {
            if (interpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            }
            g.setColor(new Color(135, 224, 255, 70));
            g.setStroke(new BasicStroke(3f));
            double shieldX = sx + fighter.facing * 30;
            g.draw(new Ellipse2D.Double(shieldX - 38, fighter.y - 152, 76, 108));
        } else {
            if (interpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            }
        }
    }

    private BufferedImage tintImage(BufferedImage source, Color tint, float alpha) {
        BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tinted.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.setComposite(AlphaComposite.SrcAtop.derive(alpha));
        g.setColor(tint);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.dispose();
        return tinted;
    }

    private void drawParticles(Graphics2D g, boolean foreground) {
        for (Particle particle : particles) {
            if (particle.foreground == foreground) {
                particle.draw(g, cameraX);
            }
        }
        if (foreground) {
            for (FloatingText text : floatingTexts) {
                text.draw(g, cameraX);
            }
        }
    }

    private void drawHud(Graphics2D g) {
        drawFighterHud(g, p1, 28, 24, false);
        drawFighterHud(g, p2, VIEW_W - 368, 24, true);

        g.setFont(font(36, Font.BOLD));
        String time = String.format(Locale.US, "%02d", (int) Math.ceil(roundTime));
        int tw = g.getFontMetrics().stringWidth(time);
        drawSoftPanel(g, VIEW_W / 2 - 54, 16, 108, 62, new Color(14, 16, 28, 205));
        g.setColor(Color.decode("#F6F3DD"));
        g.drawString(time, VIEW_W / 2 - tw / 2, 59);
    }

    private void drawFighterHud(Graphics2D g, Fighter fighter, int x, int y, boolean right) {
        if (fighter == null) {
            return;
        }
        drawSoftPanel(g, x, y, 340, 86, new Color(14, 16, 28, 210));
        int portraitX = right ? x + 272 : x + 12;
        drawPortrait(g, fighter, portraitX, y + 11, right);
        int barX = right ? x + 18 : x + 74;
        g.setFont(font(15, Font.BOLD));
        g.setColor(new Color(240, 242, 250));
        String name = fighter.name.toUpperCase(Locale.US);
        if (right) {
            drawRightString(g, name, barX + 240, y + 22);
        } else {
            g.drawString(name, barX, y + 22);
        }
        drawBar(g, barX, y + 30, 240, 16, fighter.hp / fighter.maxHp, Color.decode("#F34C58"), Color.decode("#FFC857"), right);
        drawBar(g, barX, y + 53, 240, 10, fighter.energy / 100.0, Color.decode("#4DE3FF"), Color.decode("#7B61FF"), right);
        drawCooldownDots(g, fighter, right ? barX + 105 : barX, y + 69, right);
    }

    private void drawPortrait(Graphics2D g, Fighter fighter, int x, int y, boolean right) {
        Shape oldClip = g.getClip();
        g.setColor(new Color(255, 255, 255, 22));
        g.fillRoundRect(x, y, 55, 55, 10, 10);
        g.setClip(new Rectangle2D.Double(x + 3, y + 3, 49, 49));
        BufferedImage frame = fighter.assets.getFrame(Anim.IDLE, 0);
        int w = (int) (frame.getWidth() * 1.85);
        int h = (int) (frame.getHeight() * 1.85);
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (right) {
            g.drawImage(frame, x + 74, y + 56 - h, -w, h, null);
        } else {
            g.drawImage(frame, x - 8, y + 56 - h, w, h, null);
        }
        if (interpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        }
        g.setClip(oldClip);
        g.setColor(fighter.teamColor);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, 55, 55, 10, 10);
    }

    private void drawCooldownDots(Graphics2D g, Fighter fighter, int x, int y, boolean right) {
        double[] ready = {
                1.0 - fighter.skillOneCooldown / Fighter.SKILL_ONE_COOLDOWN,
                1.0 - fighter.skillTwoCooldown / Fighter.SKILL_TWO_COOLDOWN,
                1.0 - fighter.ultimateCooldown / Fighter.ULTIMATE_COOLDOWN
        };
        for (int i = 0; i < ready.length; i++) {
            int dotX = x + (right ? -i * 23 : i * 23);
            double amount = clamp(ready[i], 0, 1);
            g.setColor(new Color(255, 255, 255, 38));
            g.fillRoundRect(dotX, y, 18, 7, 7, 7);
            g.setColor(i == 2 ? Color.decode("#FFD166") : Color.decode("#64E4FF"));
            g.fillRoundRect(dotX, y, (int) Math.round(18 * amount), 7, 7, 7);
        }
    }

    private void drawBar(Graphics2D g, int x, int y, int w, int h, double value, Color leftColor, Color rightColor, boolean reverse) {
        value = clamp(value, 0, 1);
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(x, y, w, h, h, h);
        int fill = (int) Math.round(w * value);
        if (fill <= 0) {
            return;
        }
        int fx = reverse ? x + w - fill : x;
        g.setPaint(new GradientPaint(fx, y, reverse ? rightColor : leftColor, fx + fill, y, reverse ? leftColor : rightColor));
        g.fillRoundRect(fx, y, fill, h, h, h);
        g.setColor(new Color(255, 255, 255, 48));
        g.drawRoundRect(x, y, w, h, h, h);
    }

    private void drawMainMenu(Graphics2D g) {
        drawMenuOverlay(g);
        g.setFont(font(76, Font.BOLD));
        drawCenteredGlow(g, "SHADOW CLASH", 144, Color.decode("#F6F3DD"), Color.decode("#64E4FF"));
        g.setFont(font(18, Font.BOLD));
        drawCentered(g, "LOCAL ARENA PROTOTYPE", 178, new Color(174, 223, 255));
        String[] items = {"START", "CHARACTERS", "QUIT"};
        for (int i = 0; i < items.length; i++) {
            drawMenuButton(g, VIEW_W / 2 - 150, 240 + i * 62, 300, 44, items[i], menuIndex == i);
        }
    }

    private void drawModeSelect(Graphics2D g) {
        drawMenuOverlay(g);
        g.setFont(font(44, Font.BOLD));
        drawCenteredGlow(g, "SELECT MODE", 112, Color.decode("#F6F3DD"), Color.decode("#FF4A6A"));
        drawModeCard(g, 154, 190, 290, 190, "VERSUS", "Two players, one keyboard", modeIndex == 0);
        drawModeCard(g, 516, 190, 290, 190, "ARCADE CPU", "Player vs computer AI", modeIndex == 1);
    }

    private void drawModeCard(Graphics2D g, int x, int y, int w, int h, String title, String detail, boolean selected) {
        drawSoftPanel(g, x, y, w, h, selected ? new Color(28, 42, 70, 225) : new Color(12, 16, 30, 205));
        if (selected) {
            g.setColor(new Color(100, 228, 255, 120));
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(x, y, w, h, 16, 16);
        }
        g.setFont(font(32, Font.BOLD));
        drawCenteredWithin(g, title, x, y + 78, w, Color.WHITE);
        g.setFont(font(15, Font.BOLD));
        drawCenteredWithin(g, detail, x, y + 118, w, new Color(190, 210, 230));
    }

    private void drawCharacterSelect(Graphics2D g) {
        drawMenuOverlay(g);
        g.setFont(font(42, Font.BOLD));
        drawCenteredGlow(g, "SELECT FIGHTERS", 86, Color.decode("#F6F3DD"), Color.decode("#64E4FF"));
        drawCharacterCard(g, 116, 142, 322, 292, assets.character(p1CharacterIndex), "PLAYER 1", Color.decode("#50E3FF"));
        drawCharacterCard(g, 522, 142, 322, 292, assets.character(p2CharacterIndex),
                gameMode == GameMode.CPU ? "CPU" : "PLAYER 2", Color.decode("#FF4A6A"));
        drawMenuButton(g, VIEW_W / 2 - 118, 462, 236, 42, "ENTER ARENA", true);
    }

    private void drawCharacterCard(Graphics2D g, int x, int y, int w, int h, CharacterAssets character, String label, Color color) {
        drawSoftPanel(g, x, y, w, h, new Color(11, 16, 29, 218));
        g.setColor(color);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(x, y, w, h, 18, 18);
        g.setFont(font(16, Font.BOLD));
        drawCenteredWithin(g, label, x, y + 32, w, color);
        g.setFont(font(34, Font.BOLD));
        drawCenteredWithin(g, character.displayName.toUpperCase(Locale.US), x, y + 70, w, Color.WHITE);

        BufferedImage frame = character.getFrame(Anim.IDLE, (int) (pulseTime * 8));
        int frameW = (int) (frame.getWidth() * 4.1);
        int frameH = (int) (frame.getHeight() * 4.1);
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame, x + w / 2 - frameW / 2, y + 262 - frameH, frameW, frameH, null);
        if (interpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        }

        g.setFont(font(14, Font.BOLD));
        drawStat(g, x + 28, y + h - 48, "POWER", character.power, color);
        drawStat(g, x + 122, y + h - 48, "SPEED", character.speed, color);
        drawStat(g, x + 216, y + h - 48, "RANGE", character.range, color);
    }

    private void drawStat(Graphics2D g, int x, int y, String label, int amount, Color color) {
        g.setColor(new Color(230, 236, 245));
        g.drawString(label, x, y);
        for (int i = 0; i < 5; i++) {
            g.setColor(i < amount ? color : new Color(255, 255, 255, 38));
            g.fillRoundRect(x + i * 13, y + 10, 9, 20, 8, 8);
        }
    }

    private void drawResult(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        drawSoftPanel(g, VIEW_W / 2 - 245, 160, 490, 190, new Color(12, 16, 30, 232));
        g.setFont(font(42, Font.BOLD));
        drawCenteredGlow(g, resultTitle, 232, Color.WHITE, Color.decode("#FFD166"));
        g.setFont(font(17, Font.BOLD));
        drawCentered(g, resultSubtitle, 274, new Color(210, 222, 236));
        drawMenuButton(g, VIEW_W / 2 - 118, 300, 236, 40, "BACK TO MODE", true);
    }

    private void drawMenuOverlay(Graphics2D g) {
        g.setColor(new Color(2, 4, 12, 118));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        for (int i = 0; i < 8; i++) {
            int y = 36 + i * 66;
            g.setColor(new Color(255, 255, 255, i % 2 == 0 ? 12 : 5));
            g.drawLine(70, y, VIEW_W - 70, y + 22);
        }
    }

    private void drawMenuButton(Graphics2D g, int x, int y, int w, int h, String text, boolean selected) {
        drawSoftPanel(g, x, y, w, h, selected ? new Color(33, 48, 72, 235) : new Color(13, 17, 31, 218));
        if (selected) {
            g.setColor(new Color(100, 228, 255, 135));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, w, h, 12, 12);
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(x + 6, y + 5, w - 12, 10, 10, 10);
        }
        g.setFont(font(20, Font.BOLD));
        drawCenteredWithin(g, text, x, y + 29, w, selected ? Color.WHITE : new Color(190, 203, 220));
    }

    private void drawSoftPanel(Graphics2D g, int x, int y, int w, int h, Color fill) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 5, y + 7, w, h, 16, 16);
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 16, 16);
        g.setColor(new Color(255, 255, 255, 32));
        g.drawRoundRect(x, y, w, h, 16, 16);
    }

    private void drawVignette(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 70));
        g.setStroke(new BasicStroke(36f));
        g.drawRect(18, 18, VIEW_W - 36, VIEW_H - 36);
    }

    private void drawCenteredGlow(Graphics2D g, String text, int y, Color textColor, Color glowColor) {
        FontMetrics metrics = g.getFontMetrics();
        int x = (VIEW_W - metrics.stringWidth(text)) / 2;
        g.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 80));
        for (int i = 5; i >= 1; i--) {
            g.drawString(text, x - i, y);
            g.drawString(text, x + i, y);
            g.drawString(text, x, y - i);
            g.drawString(text, x, y + i);
        }
        g.setColor(textColor);
        g.drawString(text, x, y);
    }

    private void drawCentered(Graphics2D g, String text, int y, Color color) {
        g.setColor(color);
        g.drawString(text, (VIEW_W - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    private void drawCenteredWithin(Graphics2D g, String text, int x, int baselineY, int width, Color color) {
        g.setColor(color);
        int textW = g.getFontMetrics().stringWidth(text);
        g.drawString(text, x + (width - textW) / 2, baselineY);
    }

    private void drawRightString(Graphics2D g, String text, int rightX, int baselineY) {
        g.drawString(text, rightX - g.getFontMetrics().stringWidth(text), baselineY);
    }

    private Font font(int size, int style) {
        return new Font("SansSerif", style, size);
    }
}

final class Controls {
    static final Controls P1 = new Controls(
            new int[] {KeyEvent.VK_A},
            new int[] {KeyEvent.VK_D},
            new int[] {KeyEvent.VK_W},
            new int[] {KeyEvent.VK_S},
            new int[] {KeyEvent.VK_J},
            new int[] {KeyEvent.VK_K},
            new int[] {KeyEvent.VK_U},
            new int[] {KeyEvent.VK_I},
            new int[] {KeyEvent.VK_O}
    );

    static final Controls P2 = new Controls(
            new int[] {KeyEvent.VK_LEFT},
            new int[] {KeyEvent.VK_RIGHT},
            new int[] {KeyEvent.VK_UP},
            new int[] {KeyEvent.VK_DOWN},
            new int[] {KeyEvent.VK_NUMPAD1, KeyEvent.VK_1},
            new int[] {KeyEvent.VK_NUMPAD2, KeyEvent.VK_2},
            new int[] {KeyEvent.VK_NUMPAD4, KeyEvent.VK_4},
            new int[] {KeyEvent.VK_NUMPAD5, KeyEvent.VK_5},
            new int[] {KeyEvent.VK_NUMPAD6, KeyEvent.VK_6}
    );

    final int[] left;
    final int[] right;
    final int[] jump;
    final int[] block;
    final int[] light;
    final int[] dash;
    final int[] skillOne;
    final int[] skillTwo;
    final int[] ultimate;

    private Controls(int[] left, int[] right, int[] jump, int[] block, int[] light, int[] dash,
            int[] skillOne, int[] skillTwo, int[] ultimate) {
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.block = block;
        this.light = light;
        this.dash = dash;
        this.skillOne = skillOne;
        this.skillTwo = skillTwo;
        this.ultimate = ultimate;
    }
}

final class Fighter {
    static final double SKILL_ONE_COOLDOWN = 1.35;
    static final double SKILL_TWO_COOLDOWN = 2.65;
    static final double ULTIMATE_COOLDOWN = 8.0;
    private static final double DASH_COOLDOWN = 0.85;

    final CharacterAssets assets;
    final String name;
    final Color teamColor;
    final double maxHp;
    final double speed;
    final double renderScale;
    Color paletteShift;
    double x;
    double y;
    double vx;
    double vy;
    double hp;
    double energy = 42.0;
    double hitStun;
    double hitFlash;
    double invulnerable;
    double dashTimer;
    double dashCooldown;
    double skillOneCooldown;
    double skillTwoCooldown;
    double ultimateCooldown;
    double attackElapsed;
    double stateTime;
    int facing;
    boolean onGround = true;
    boolean blocking;
    boolean dead;
    Strike currentStrike;
    private Anim lastAnim = Anim.IDLE;

    private Fighter(CharacterAssets assets, String name, double x, double y, int facing, Color teamColor,
            double maxHp, double speed, double renderScale) {
        this.assets = assets;
        this.name = name;
        this.x = x;
        this.y = y;
        this.facing = facing;
        this.teamColor = teamColor;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.speed = speed;
        this.renderScale = renderScale;
    }

    static Fighter create(CharacterAssets assets, String name, double x, double y, int facing, Color teamColor) {
        double hp = assets.id == 0 ? 1000.0 : 1080.0;
        double speed = assets.id == 0 ? 315.0 : 275.0;
        return new Fighter(assets, name, x, y, facing, teamColor, hp, speed, 3.55);
    }

    void update(double dt) {
        stateTime += dt;
        hitFlash = Math.max(0, hitFlash - dt);
        invulnerable = Math.max(0, invulnerable - dt);
        dashCooldown = Math.max(0, dashCooldown - dt);
        skillOneCooldown = Math.max(0, skillOneCooldown - dt);
        skillTwoCooldown = Math.max(0, skillTwoCooldown - dt);
        ultimateCooldown = Math.max(0, ultimateCooldown - dt);
        energy = Math.min(100.0, energy + dt * 4.2);

        if (dead) {
            vx = approach(vx, 0, 900 * dt);
        }

        if (dashTimer > 0) {
            dashTimer = Math.max(0, dashTimer - dt);
            if (dashTimer == 0) {
                vx *= 0.36;
            }
        }

        if (hitStun > 0) {
            hitStun = Math.max(0, hitStun - dt);
            blocking = false;
        }

        if (currentStrike != null) {
            attackElapsed += dt;
            if (attackElapsed >= currentStrike.duration) {
                currentStrike = null;
                attackElapsed = 0;
            }
        }

        if (!onGround) {
            vy += GamePanel.GRAVITY * dt;
        }
        x += vx * dt;
        y += vy * dt;
        if (y >= GamePanel.GROUND_Y) {
            y = GamePanel.GROUND_Y;
            vy = 0;
            onGround = true;
        } else {
            onGround = false;
        }
        if (onGround && currentStrike == null && hitStun <= 0 && dashTimer <= 0) {
            vx = approach(vx, 0, 1100 * dt);
        }
        clampToWorld();

        Anim anim = animation();
        if (anim != lastAnim) {
            lastAnim = anim;
            stateTime = 0;
        }
    }

    void clampToWorld() {
        x = Math.max(70, Math.min(GamePanel.WORLD_W - 70, x));
    }

    void faceToward(Fighter other) {
        if (dead || currentStrike != null || dashTimer > 0) {
            return;
        }
        if (other.x > x) {
            facing = 1;
        } else if (other.x < x) {
            facing = -1;
        }
    }

    boolean canMove() {
        return !dead && hitStun <= 0 && currentStrike == null && dashTimer <= 0 && !blocking;
    }

    boolean canBlock() {
        return !dead && onGround && hitStun <= 0 && currentStrike == null && dashTimer <= 0;
    }

    boolean canAct() {
        return !dead && hitStun <= 0 && currentStrike == null && dashTimer <= 0;
    }

    void jump() {
        if (!canAct() || !onGround || blocking) {
            return;
        }
        vy = -735.0;
        onGround = false;
        stateTime = 0;
    }

    boolean startDash() {
        if (!canAct() || dashCooldown > 0 || !onGround || blocking) {
            return false;
        }
        vx = facing * 720.0;
        dashTimer = 0.16;
        dashCooldown = DASH_COOLDOWN;
        invulnerable = 0.10;
        return true;
    }

    boolean startStrike(StrikeType type) {
        if (!canAct() || blocking) {
            return false;
        }
        Strike strike = Strike.of(type, assets.id);
        if (type == StrikeType.SKILL_ONE && (skillOneCooldown > 0 || energy < strike.energyCost)) {
            return false;
        }
        if (type == StrikeType.SKILL_TWO && (skillTwoCooldown > 0 || energy < strike.energyCost)) {
            return false;
        }
        if (type == StrikeType.ULTIMATE && (ultimateCooldown > 0 || energy < strike.energyCost)) {
            return false;
        }

        energy -= strike.energyCost;
        currentStrike = strike;
        attackElapsed = 0;
        stateTime = 0;
        vx += facing * strike.lunge;
        if (type == StrikeType.SKILL_ONE) {
            skillOneCooldown = SKILL_ONE_COOLDOWN;
        } else if (type == StrikeType.SKILL_TWO) {
            skillTwoCooldown = SKILL_TWO_COOLDOWN;
        } else if (type == StrikeType.ULTIMATE) {
            ultimateCooldown = ULTIMATE_COOLDOWN;
        }
        return true;
    }

    void applyHit(double damage, double stun, double knockX, double launch, boolean blocked) {
        if (dead) {
            return;
        }
        hp = Math.max(0, hp - damage);
        if (hp <= 0) {
            dead = true;
            currentStrike = null;
            blocking = false;
            hitStun = 1.2;
            vy = -160.0;
        } else if (!blocked) {
            currentStrike = null;
            hitStun = stun;
        } else {
            hitStun = Math.max(hitStun, stun);
        }
        vx = knockX;
        if (!blocked && launch > 0) {
            vy = -launch;
            onGround = false;
        }
        hitFlash = blocked ? 0.10 : 0.22;
        stateTime = 0;
    }

    Rectangle2D hurtBox() {
        return new Rectangle2D.Double(x - 36, y - 154, 72, 150);
    }

    Rectangle2D hitBox() {
        if (currentStrike == null) {
            return new Rectangle2D.Double();
        }
        double w = currentStrike.range;
        double h = currentStrike.height;
        double boxX = facing > 0 ? x + 20 : x - 20 - w;
        double boxY = y - currentStrike.yOffset;
        return new Rectangle2D.Double(boxX, boxY, w, h);
    }

    BufferedImage currentFrame() {
        Anim anim = animation();
        int frameIndex = (int) Math.floor(stateTime / frameDuration(anim));
        return assets.getFrame(anim, frameIndex);
    }

    private Anim animation() {
        if (dead) {
            return Anim.DEAD;
        }
        if (hitStun > 0) {
            return Anim.HURT;
        }
        if (currentStrike != null) {
            return switch (currentStrike.type) {
                case LIGHT -> Anim.ATTACK;
                case SKILL_ONE -> Anim.KICK;
                case SKILL_TWO -> Anim.SKILL;
                case ULTIMATE -> Anim.ULTIMATE;
            };
        }
        if (blocking) {
            return Anim.BLOCK;
        }
        if (!onGround) {
            return Anim.JUMP;
        }
        if (Math.abs(vx) > 18) {
            return Anim.WALK;
        }
        return Anim.IDLE;
    }

    private double frameDuration(Anim anim) {
        return switch (anim) {
            case ATTACK, KICK, SKILL -> 0.075;
            case ULTIMATE -> 0.085;
            case HURT, DEAD -> 0.11;
            case WALK -> 0.065;
            default -> 0.12;
        };
    }

    private double approach(double value, double target, double delta) {
        if (value < target) {
            return Math.min(target, value + delta);
        }
        return Math.max(target, value - delta);
    }
}

final class Strike {
    final StrikeType type;
    final double duration;
    final double activeStart;
    final double activeEnd;
    final double damage;
    final double range;
    final double height;
    final double yOffset;
    final double knockback;
    final double launch;
    final double hitStun;
    final double energyCost;
    final double energyGain;
    final double lunge;
    final double screenShake;
    final Color color;
    boolean hasHit;

    private Strike(StrikeType type, double duration, double activeStart, double activeEnd, double damage,
            double range, double height, double yOffset, double knockback, double launch, double hitStun,
            double energyCost, double energyGain, double lunge, double screenShake, Color color) {
        this.type = type;
        this.duration = duration;
        this.activeStart = activeStart;
        this.activeEnd = activeEnd;
        this.damage = damage;
        this.range = range;
        this.height = height;
        this.yOffset = yOffset;
        this.knockback = knockback;
        this.launch = launch;
        this.hitStun = hitStun;
        this.energyCost = energyCost;
        this.energyGain = energyGain;
        this.lunge = lunge;
        this.screenShake = screenShake;
        this.color = color;
    }

    static Strike of(StrikeType type, int characterId) {
        double powerBonus = characterId == 1 ? 1.08 : 1.0;
        return switch (type) {
            case LIGHT -> new Strike(type, 0.34, 0.10, 0.22, 38 * powerBonus,
                    82, 72, 132, 235, 0, 0.26, 0, 13, 40, 3.0, Color.decode("#FFF6B8"));
            case SKILL_ONE -> new Strike(type, 0.54, 0.20, 0.36, 78 * powerBonus,
                    112, 88, 142, 390, 70, 0.38, 22, 17, 120, 5.0, Color.decode("#64E4FF"));
            case SKILL_TWO -> new Strike(type, 0.72, 0.28, 0.49, 116 * powerBonus,
                    150, 106, 154, 480, 140, 0.52, 38, 24, 180, 7.0, Color.decode("#B56CFF"));
            case ULTIMATE -> new Strike(type, 1.12, 0.44, 0.77, 205 * powerBonus,
                    250, 150, 170, 670, 210, 0.78, 92, 38, 90, 12.5, Color.decode("#FFD166"));
        };
    }

    boolean isActive(double elapsed) {
        return elapsed >= activeStart && elapsed <= activeEnd;
    }
}

final class AIController {
    private final Random random = new Random();
    private double blockTimer;
    private double thinkTimer;

    void reset() {
        blockTimer = 0;
        thinkTimer = 0;
    }

    void update(double dt, Fighter self, Fighter target, AudioEngine audio) {
        if (self.dead) {
            return;
        }
        double distance = target.x - self.x;
        double abs = Math.abs(distance);
        if (distance != 0) {
            self.facing = distance > 0 ? 1 : -1;
        }

        blockTimer = Math.max(0, blockTimer - dt);
        thinkTimer = Math.max(0, thinkTimer - dt);
        boolean targetThreatening = target.currentStrike != null && abs < 185;
        if (targetThreatening && self.canBlock() && random.nextDouble() < 0.035) {
            blockTimer = 0.28 + random.nextDouble() * 0.22;
        }
        if (blockTimer > 0 && self.canBlock()) {
            self.blocking = true;
            self.vx = approach(self.vx, 0, 1600 * dt);
            return;
        }
        self.blocking = false;

        if (!self.canAct()) {
            return;
        }

        if (self.energy >= 94 && abs < 285 && random.nextDouble() < 0.022) {
            if (self.startStrike(StrikeType.ULTIMATE)) {
                audio.playUltimate();
            }
            return;
        }
        if (abs < 170 && self.energy >= 38 && random.nextDouble() < 0.032) {
            if (self.startStrike(StrikeType.SKILL_TWO)) {
                audio.playSkill();
            }
            return;
        }
        if (abs < 132 && self.energy >= 22 && random.nextDouble() < 0.043) {
            if (self.startStrike(StrikeType.SKILL_ONE)) {
                audio.playSkill();
            }
            return;
        }
        if (abs < 92 && random.nextDouble() < 0.07) {
            if (self.startStrike(StrikeType.LIGHT)) {
                audio.playSwing();
            }
            return;
        }

        if (thinkTimer <= 0) {
            thinkTimer = 0.10 + random.nextDouble() * 0.18;
            if (abs > 118) {
                self.vx = Math.signum(distance) * self.speed * 0.82;
            } else if (targetThreatening && abs < 130) {
                self.vx = -Math.signum(distance) * self.speed * 0.55;
            } else {
                self.vx = approach(self.vx, 0, 700 * dt);
            }
            if (abs > 360 && self.startDash()) {
                audio.playDash();
            }
        }
    }

    private double approach(double value, double target, double delta) {
        if (value < target) {
            return Math.min(target, value + delta);
        }
        return Math.max(target, value - delta);
    }
}

final class Particle {
    double x;
    double y;
    double vx;
    double vy;
    double life;
    double maxLife;
    double size;
    Color color;
    boolean ring;
    boolean foreground = true;

    static Particle spark(double x, double y, double vx, double vy, Color color, double life) {
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.color = color;
        p.life = life;
        p.maxLife = life;
        p.size = 3.0 + Math.random() * 5.0;
        return p;
    }

    static Particle dust(double x, double y, double vx, double vy, Color color, double life) {
        Particle p = spark(x, y, vx, vy, color, life);
        p.size = 10.0 + Math.random() * 13.0;
        p.foreground = false;
        return p;
    }

    static Particle ring(double x, double y, Color color, double life) {
        Particle p = spark(x, y, 0, 0, color, life);
        p.ring = true;
        p.size = 18.0;
        return p;
    }

    void update(double dt) {
        life -= dt;
        x += vx * dt;
        y += vy * dt;
        vy += 460 * dt;
        vx *= Math.pow(0.04, dt);
        if (ring) {
            size += 220 * dt;
        }
    }

    void draw(Graphics2D g, double cameraX) {
        float alpha = (float) Math.max(0, Math.min(1, life / maxLife));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(color);
        double sx = x - cameraX;
        if (ring) {
            g.setStroke(new BasicStroke(Math.max(1f, (float) (4 * alpha))));
            g.draw(new Ellipse2D.Double(sx - size / 2.0, y - size / 2.0, size, size));
        } else {
            g.fill(new Ellipse2D.Double(sx - size / 2.0, y - size / 2.0, size, size));
        }
        g.setComposite(AlphaComposite.SrcOver);
    }
}

final class FloatingText {
    final String text;
    final Color color;
    double x;
    double y;
    double life = 0.78;

    FloatingText(String text, double x, double y, Color color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    void update(double dt) {
        life -= dt;
        y -= 48 * dt;
    }

    void draw(Graphics2D g, double cameraX) {
        float alpha = (float) Math.max(0, Math.min(1, life / 0.78));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        int sx = (int) Math.round(x - cameraX);
        int sy = (int) Math.round(y);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(text, sx - g.getFontMetrics().stringWidth(text) / 2 + 2, sy + 2);
        g.setColor(color);
        g.drawString(text, sx - g.getFontMetrics().stringWidth(text) / 2, sy);
        g.setComposite(AlphaComposite.SrcOver);
    }
}

final class CharacterAssets {
    final int id;
    final String displayName;
    final int power;
    final int speed;
    final int range;
    private final Map<Anim, List<BufferedImage>> animations = new EnumMap<>(Anim.class);

    CharacterAssets(int id, String displayName, int power, int speed, int range) {
        this.id = id;
        this.displayName = displayName;
        this.power = power;
        this.speed = speed;
        this.range = range;
    }

    void put(Anim anim, List<BufferedImage> frames) {
        animations.put(anim, frames);
    }

    BufferedImage getFrame(Anim anim, int index) {
        List<BufferedImage> frames = animations.get(anim);
        if (frames == null || frames.isEmpty()) {
            frames = animations.get(Anim.IDLE);
        }
        if (frames == null || frames.isEmpty()) {
            return AssetStore.placeholder(Color.MAGENTA);
        }
        return frames.get(Math.floorMod(index, frames.size()));
    }
}

final class AssetStore {
    final BufferedImage stageBack;
    final BufferedImage stageFore;
    final BufferedImage car;
    final BufferedImage barrel;
    final BufferedImage hydrant;
    final BufferedImage shadow;
    private final CharacterAssets[] characters = new CharacterAssets[2];
    private final Path root;

    AssetStore(Path root) {
        this.root = root;
        stageBack = read(root.resolve(Path.of("stage", "back.png")), placeholder(Color.decode("#15274A")));
        stageFore = read(root.resolve(Path.of("stage", "fore.png")), placeholder(Color.decode("#33303A")));
        car = read(root.resolve(Path.of("stage", "car.png")), placeholder(Color.GRAY));
        barrel = read(root.resolve(Path.of("stage", "barrel.png")), placeholder(Color.ORANGE));
        hydrant = read(root.resolve(Path.of("stage", "hydrant.png")), placeholder(Color.RED));
        shadow = read(root.resolve(Path.of("characters", "shadow.png")), placeholder(new Color(0, 0, 0, 80)));
        characters[0] = loadBrawler();
        characters[1] = loadPunk();
    }

    CharacterAssets character(int index) {
        return characters[Math.floorMod(index, characters.length)];
    }

    private CharacterAssets loadBrawler() {
        Path base = root.resolve(Path.of("characters", "brawler"));
        CharacterAssets c = new CharacterAssets(0, "Rina", 4, 5, 3);
        c.put(Anim.IDLE, loadFrames(base.resolve("Idle")));
        c.put(Anim.WALK, loadFrames(base.resolve("Walk")));
        c.put(Anim.JUMP, loadFrames(base.resolve("Jump")));
        c.put(Anim.ATTACK, loadFrames(base.resolve("Punch")));
        c.put(Anim.KICK, loadFrames(base.resolve("Kick")));
        c.put(Anim.SKILL, loadFrames(base.resolve("Jump_kick")));
        c.put(Anim.ULTIMATE, loadFrames(base.resolve("Dive_kick")));
        c.put(Anim.HURT, loadFrames(base.resolve("Hurt")));
        c.put(Anim.DEAD, loadFrames(base.resolve("Hurt")));
        c.put(Anim.BLOCK, loadFrames(base.resolve("Idle")));
        return c;
    }

    private CharacterAssets loadPunk() {
        Path base = root.resolve(Path.of("characters", "punk"));
        CharacterAssets c = new CharacterAssets(1, "Kade", 5, 3, 4);
        c.put(Anim.IDLE, loadFrames(base.resolve("Idle")));
        c.put(Anim.WALK, loadFrames(base.resolve("Walk")));
        c.put(Anim.JUMP, loadFrames(base.resolve("Idle")));
        c.put(Anim.ATTACK, loadFrames(base.resolve("Punch")));
        c.put(Anim.KICK, loadFrames(base.resolve("Punch")));
        c.put(Anim.SKILL, loadFrames(base.resolve("Punch")));
        c.put(Anim.ULTIMATE, loadFrames(base.resolve("Punch")));
        c.put(Anim.HURT, loadFrames(base.resolve("Hurt")));
        c.put(Anim.DEAD, loadFrames(base.resolve("Hurt")));
        c.put(Anim.BLOCK, loadFrames(base.resolve("Idle")));
        return c;
    }

    private List<BufferedImage> loadFrames(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of(placeholder(Color.PINK));
        }
        try {
            List<Path> files = Files.list(directory)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.US).endsWith(".png"))
                    .sorted(Comparator.comparingInt(AssetStore::fileNumber)
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
            List<BufferedImage> frames = new ArrayList<>();
            for (Path file : files) {
                frames.add(read(file, placeholder(Color.PINK)));
            }
            return frames.isEmpty() ? List.of(placeholder(Color.PINK)) : frames;
        } catch (IOException e) {
            return List.of(placeholder(Color.PINK));
        }
    }

    private static int fileNumber(Path path) {
        String name = path.getFileName().toString();
        String digits = name.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    private BufferedImage read(Path path, BufferedImage fallback) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException | IllegalArgumentException e) {
            return fallback;
        }
    }

    static BufferedImage placeholder(Color color) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 64, 64);
        g.setColor(new Color(255, 255, 255, 100));
        g.drawLine(0, 0, 64, 64);
        g.drawLine(64, 0, 0, 64);
        g.dispose();
        return img;
    }
}

final class AudioEngine {
    private static final float SAMPLE_RATE = 22_050f;
    private volatile boolean musicRunning;
    private Thread musicThread;

    void startBgm() {
        if (musicRunning) {
            return;
        }
        musicRunning = true;
        musicThread = new Thread(this::runMusic, "shadow-clash-bgm");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    void playMenu() {
        playTone(660, 0.055, 0.20, Wave.SINE);
    }

    void playStart() {
        playSweep(220, 660, 0.18, 0.25);
    }

    void playSwing() {
        playSweep(520, 210, 0.07, 0.18);
    }

    void playSkill() {
        playSweep(180, 760, 0.16, 0.20);
    }

    void playUltimate() {
        playSweep(120, 980, 0.32, 0.22);
    }

    void playDash() {
        playSweep(400, 120, 0.08, 0.12);
    }

    void playBlock() {
        playTone(220, 0.08, 0.22, Wave.SQUARE);
    }

    void playHit(StrikeType type) {
        if (type == StrikeType.ULTIMATE) {
            playSweep(95, 38, 0.22, 0.30);
        } else {
            playSweep(160, 70, 0.12, 0.24);
        }
    }

    void playVictory() {
        new Thread(() -> {
            playToneBlocking(440, 0.12, 0.20, Wave.SINE);
            playToneBlocking(554, 0.12, 0.20, Wave.SINE);
            playToneBlocking(659, 0.22, 0.20, Wave.SINE);
        }, "victory-sound").start();
    }

    private void runMusic() {
        AudioFormat format = format();
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, 2048);
            line.start();
            int sample = 0;
            byte[] buffer = new byte[1024];
            double[] bass = {82.41, 98.00, 110.00, 73.42};
            double[] lead = {329.63, 392.00, 493.88, 440.00, 392.00, 329.63, 293.66, 246.94};
            while (musicRunning) {
                for (int i = 0; i < buffer.length / 2; i++) {
                    double t = sample / SAMPLE_RATE;
                    int beat = (int) (t * 3.1);
                    double bassFreq = bass[Math.floorMod(beat / 2, bass.length)];
                    double leadFreq = lead[Math.floorMod(beat, lead.length)];
                    double kick = Math.exp(-((t * 3.1) % 1.0) * 12.0) * Math.sin(2 * Math.PI * 52 * t);
                    double wave = 0.10 * square(bassFreq, t) + 0.055 * Math.sin(2 * Math.PI * leadFreq * t) + 0.10 * kick;
                    short value = (short) (Math.max(-1, Math.min(1, wave)) * Short.MAX_VALUE);
                    buffer[i * 2] = (byte) (value & 0xff);
                    buffer[i * 2 + 1] = (byte) ((value >> 8) & 0xff);
                    sample++;
                }
                line.write(buffer, 0, buffer.length);
            }
            line.drain();
        } catch (LineUnavailableException ignored) {
            musicRunning = false;
        }
    }

    private double square(double frequency, double t) {
        return Math.sin(2 * Math.PI * frequency * t) >= 0 ? 1.0 : -1.0;
    }

    private void playTone(double frequency, double seconds, double volume, Wave wave) {
        new Thread(() -> playToneBlocking(frequency, seconds, volume, wave), "tone").start();
    }

    private void playSweep(double start, double end, double seconds, double volume) {
        new Thread(() -> {
            AudioFormat format = format();
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, 1024);
                line.start();
                int samples = (int) (seconds * SAMPLE_RATE);
                byte[] data = new byte[samples * 2];
                for (int i = 0; i < samples; i++) {
                    double p = i / (double) samples;
                    double freq = start + (end - start) * p;
                    double env = Math.sin(Math.PI * p);
                    double n = (Math.random() * 2.0 - 1.0) * 0.28;
                    double value = (Math.sin(2 * Math.PI * freq * i / SAMPLE_RATE) + n) * env * volume;
                    short out = (short) (Math.max(-1, Math.min(1, value)) * Short.MAX_VALUE);
                    data[i * 2] = (byte) (out & 0xff);
                    data[i * 2 + 1] = (byte) ((out >> 8) & 0xff);
                }
                line.write(data, 0, data.length);
                line.drain();
            } catch (LineUnavailableException ignored) {
            }
        }, "sweep").start();
    }

    private void playToneBlocking(double frequency, double seconds, double volume, Wave wave) {
        AudioFormat format = format();
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, 1024);
            line.start();
            int samples = (int) (seconds * SAMPLE_RATE);
            byte[] data = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                double p = i / (double) samples;
                double env = Math.sin(Math.PI * p);
                double t = i / SAMPLE_RATE;
                double signal = wave == Wave.SQUARE ? square(frequency, t) : Math.sin(2 * Math.PI * frequency * t);
                short out = (short) (signal * env * volume * Short.MAX_VALUE);
                data[i * 2] = (byte) (out & 0xff);
                data[i * 2 + 1] = (byte) ((out >> 8) & 0xff);
            }
            line.write(data, 0, data.length);
            line.drain();
        } catch (LineUnavailableException ignored) {
        }
    }

    private AudioFormat format() {
        return new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    }

    private enum Wave {
        SINE,
        SQUARE
    }
}
