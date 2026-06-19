package com.shadowclash;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public final class ShadowClashFX extends Application {
    private static final int VIEW_W = GamePanel.VIEW_W;
    private static final int VIEW_H = GamePanel.VIEW_H;
    private static final int KEY_MAX = 1024;

    private final boolean[] keys = new boolean[KEY_MAX];
    private final boolean[] previousKeys = new boolean[KEY_MAX];
    private final Random random = new Random();
    private final List<FxParticle> particles = new ArrayList<>();
    private final List<FxFloatingText> floatingTexts = new ArrayList<>();
    private AssetStore assets;
    private FxImages images;
    private AudioEngine audio;
    private AIController aiController;
    private Canvas canvas;
    private GraphicsContext gc;

    private Scene screen = Scene.MENU;
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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        assets = new AssetStore(Path.of("assets", "processed"));
        images = new FxImages();
        audio = new AudioEngine();
        aiController = new AIController();
        canvas = new Canvas(VIEW_W, VIEW_H);
        gc = canvas.getGraphicsContext2D();
        gc.setImageSmoothing(false);

        Group root = new Group(canvas);
        javafx.scene.Scene fxScene = new javafx.scene.Scene(root, VIEW_W, VIEW_H, Color.BLACK);
        fxScene.setOnKeyPressed(event -> {
            int code = awtCode(event.getCode());
            if (code >= 0 && code < keys.length) {
                keys[code] = true;
            }
            handleMenuKey(code);
            event.consume();
        });
        fxScene.setOnKeyReleased(event -> {
            int code = awtCode(event.getCode());
            if (code >= 0 && code < keys.length) {
                keys[code] = false;
            }
            event.consume();
        });

        stage.setTitle("Shadow Clash FX - JavaFX Fighting Game");
        stage.setScene(fxScene);
        stage.setResizable(false);
        stage.show();
        audio.startBgm();

        AnimationTimer timer = new AnimationTimer() {
            private long last;

            @Override
            public void handle(long now) {
                if (last == 0) {
                    last = now;
                    return;
                }
                double dt = Math.min(0.033, (now - last) / 1_000_000_000.0);
                last = now;
                update(dt);
                render();
            }
        };
        timer.start();
    }

    private int awtCode(KeyCode code) {
        return switch (code) {
            case A -> java.awt.event.KeyEvent.VK_A;
            case D -> java.awt.event.KeyEvent.VK_D;
            case W -> java.awt.event.KeyEvent.VK_W;
            case S -> java.awt.event.KeyEvent.VK_S;
            case J -> java.awt.event.KeyEvent.VK_J;
            case K -> java.awt.event.KeyEvent.VK_K;
            case U -> java.awt.event.KeyEvent.VK_U;
            case I -> java.awt.event.KeyEvent.VK_I;
            case O -> java.awt.event.KeyEvent.VK_O;
            case LEFT -> java.awt.event.KeyEvent.VK_LEFT;
            case RIGHT -> java.awt.event.KeyEvent.VK_RIGHT;
            case UP -> java.awt.event.KeyEvent.VK_UP;
            case DOWN -> java.awt.event.KeyEvent.VK_DOWN;
            case DIGIT1 -> java.awt.event.KeyEvent.VK_1;
            case DIGIT2 -> java.awt.event.KeyEvent.VK_2;
            case DIGIT4 -> java.awt.event.KeyEvent.VK_4;
            case DIGIT5 -> java.awt.event.KeyEvent.VK_5;
            case DIGIT6 -> java.awt.event.KeyEvent.VK_6;
            case NUMPAD1 -> java.awt.event.KeyEvent.VK_NUMPAD1;
            case NUMPAD2 -> java.awt.event.KeyEvent.VK_NUMPAD2;
            case NUMPAD4 -> java.awt.event.KeyEvent.VK_NUMPAD4;
            case NUMPAD5 -> java.awt.event.KeyEvent.VK_NUMPAD5;
            case NUMPAD6 -> java.awt.event.KeyEvent.VK_NUMPAD6;
            case ENTER -> java.awt.event.KeyEvent.VK_ENTER;
            case SPACE -> java.awt.event.KeyEvent.VK_SPACE;
            case ESCAPE -> java.awt.event.KeyEvent.VK_ESCAPE;
            default -> -1;
        };
    }

    private void handleMenuKey(int code) {
        if (code < 0) {
            return;
        }
        if (screen == Scene.BATTLE) {
            if (code == java.awt.event.KeyEvent.VK_ESCAPE) {
                screen = Scene.MENU;
                audio.playMenu();
            }
            return;
        }

        if (code == java.awt.event.KeyEvent.VK_ESCAPE) {
            if (screen != Scene.MENU) {
                screen = Scene.MENU;
                audio.playMenu();
            }
            return;
        }

        if (screen == Scene.MENU) {
            if (code == java.awt.event.KeyEvent.VK_UP || code == java.awt.event.KeyEvent.VK_W) {
                menuIndex = wrap(menuIndex - 1, 3);
                audio.playMenu();
            } else if (code == java.awt.event.KeyEvent.VK_DOWN || code == java.awt.event.KeyEvent.VK_S) {
                menuIndex = wrap(menuIndex + 1, 3);
                audio.playMenu();
            } else if (isConfirm(code)) {
                audio.playMenu();
                if (menuIndex == 0) {
                    screen = Scene.MODE_SELECT;
                } else if (menuIndex == 1) {
                    screen = Scene.CHARACTER_SELECT;
                } else {
                    System.exit(0);
                }
            }
        } else if (screen == Scene.MODE_SELECT) {
            if (code == java.awt.event.KeyEvent.VK_LEFT || code == java.awt.event.KeyEvent.VK_RIGHT
                    || code == java.awt.event.KeyEvent.VK_A || code == java.awt.event.KeyEvent.VK_D) {
                modeIndex = 1 - modeIndex;
                audio.playMenu();
            } else if (isConfirm(code)) {
                gameMode = modeIndex == 0 ? GameMode.VERSUS : GameMode.CPU;
                screen = Scene.CHARACTER_SELECT;
                audio.playMenu();
            }
        } else if (screen == Scene.CHARACTER_SELECT) {
            if (code == java.awt.event.KeyEvent.VK_A || code == java.awt.event.KeyEvent.VK_D) {
                p1CharacterIndex = 1 - p1CharacterIndex;
                audio.playMenu();
            } else if (code == java.awt.event.KeyEvent.VK_LEFT || code == java.awt.event.KeyEvent.VK_RIGHT) {
                p2CharacterIndex = 1 - p2CharacterIndex;
                audio.playMenu();
            } else if (isConfirm(code)) {
                startBattle();
                audio.playStart();
            }
        } else if (screen == Scene.RESULT && isConfirm(code)) {
            screen = Scene.MODE_SELECT;
            audio.playMenu();
        }
    }

    private boolean isConfirm(int code) {
        return code == java.awt.event.KeyEvent.VK_ENTER || code == java.awt.event.KeyEvent.VK_SPACE;
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
        p1 = Fighter.create(p1Assets, p1Assets.displayName, 260, GamePanel.GROUND_Y, 1, java.awt.Color.decode("#50E3FF"));
        p2 = Fighter.create(p2Assets, gameMode == GameMode.CPU ? p2Assets.displayName + " CPU" : p2Assets.displayName,
                1390, GamePanel.GROUND_Y, -1, java.awt.Color.decode("#FF4A6A"));
        if (p1CharacterIndex == p2CharacterIndex) {
            p2.paletteShift = new java.awt.Color(80, 150, 255, 70);
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
        screen = Scene.BATTLE;
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
        if (screen == Scene.BATTLE) {
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
            spawnAura(fighter, fx(fighter.teamColor), 18);
            screenShake = Math.max(screenShake, 8.0);
        } else {
            audio.playSkill();
            spawnAura(fighter, fx(fighter.teamColor), 9);
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
        if (strike == null || strike.hasHit || !strike.isActive(attacker.attackElapsed)
                || defender.dead || defender.invulnerable > 0) {
            return;
        }
        if (!attacker.hitBox().intersects(defender.hurtBox())) {
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
            floatingTexts.add(new FxFloatingText("BLOCK", defender.x, defender.y - 135, Color.web("#9AE6FF")));
        } else {
            audio.playHit(strike.type);
            spawnHitSparks(defender, fx(strike.color), strike.type == StrikeType.ULTIMATE ? 34 : 18);
            floatingTexts.add(new FxFloatingText("-" + Math.round(damage), defender.x, defender.y - 145, Color.WHITE));
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
        screen = Scene.RESULT;
    }

    private void updateCamera(double dt) {
        double midpoint = (p1.x + p2.x) / 2.0;
        double target = clamp(midpoint - VIEW_W / 2.0, 0, GamePanel.WORLD_W - VIEW_W);
        cameraX += (target - cameraX) * Math.min(1.0, dt * 5.5);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateParticles(double dt) {
        for (Iterator<FxParticle> iterator = particles.iterator(); iterator.hasNext();) {
            FxParticle particle = iterator.next();
            particle.update(dt);
            if (particle.life <= 0) {
                iterator.remove();
            }
        }
        for (Iterator<FxFloatingText> iterator = floatingTexts.iterator(); iterator.hasNext();) {
            FxFloatingText text = iterator.next();
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
            particles.add(FxParticle.spark(target.x + randomRange(-16, 16), target.y - 100 + randomRange(-24, 24),
                    Math.cos(angle) * speed, Math.sin(angle) * speed, color, randomRange(0.18, 0.42)));
        }
        particles.add(FxParticle.ring(target.x, target.y - 104, base, 0.42));
    }

    private void spawnBlockSparks(Fighter target, int direction) {
        for (int i = 0; i < 12; i++) {
            particles.add(FxParticle.spark(target.x - direction * 22, target.y - 106 + randomRange(-20, 20),
                    -direction * randomRange(80, 260), randomRange(-190, 190),
                    Color.web("#9AE6FF"), randomRange(0.12, 0.28)));
        }
        particles.add(FxParticle.ring(target.x - direction * 26, target.y - 108, Color.web("#9AE6FF"), 0.24));
    }

    private void spawnAura(Fighter fighter, Color color, int amount) {
        for (int i = 0; i < amount; i++) {
            particles.add(FxParticle.spark(fighter.x + randomRange(-28, 28), fighter.y - randomRange(38, 150),
                    randomRange(-70, 70), randomRange(-240, -40), color, randomRange(0.25, 0.65)));
        }
        particles.add(FxParticle.ring(fighter.x, fighter.y - 95, color, 0.55));
    }

    private void spawnDashDust(Fighter fighter) {
        for (int i = 0; i < 10; i++) {
            particles.add(FxParticle.dust(fighter.x - fighter.facing * randomRange(12, 52), fighter.y - randomRange(2, 18),
                    -fighter.facing * randomRange(80, 260), randomRange(-60, 20),
                    Color.rgb(205, 210, 226, 0.52), randomRange(0.25, 0.5)));
        }
    }

    private double randomRange(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private void render() {
        double shakeX = screenShake > 0 ? randomRange(-screenShake, screenShake) : 0.0;
        double shakeY = screenShake > 0 ? randomRange(-screenShake * 0.65, screenShake * 0.65) : 0.0;
        gc.clearRect(0, 0, VIEW_W, VIEW_H);
        gc.save();
        gc.translate(shakeX, shakeY);
        drawStage();
        drawParticles(false);
        if (screen == Scene.BATTLE || screen == Scene.RESULT) {
            drawFighter(p1);
            drawFighter(p2);
        }
        drawParticles(true);
        gc.restore();

        if (screen == Scene.BATTLE) {
            drawHud();
        } else if (screen == Scene.MENU) {
            drawMainMenu();
        } else if (screen == Scene.MODE_SELECT) {
            drawModeSelect();
        } else if (screen == Scene.CHARACTER_SELECT) {
            drawCharacterSelect();
        } else if (screen == Scene.RESULT) {
            drawHud();
            drawResult();
        }
        drawVignette();
    }

    private void drawStage() {
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#11172F")),
                new Stop(1, Color.web("#1C1020"))));
        gc.fillRect(0, 0, VIEW_W, VIEW_H);
        drawPixelTiled(images.fx(assets.stageBack), -cameraX * 0.18, 22, 8.0, 0.72);
        drawPixelTiled(images.fx(assets.stageFore), -cameraX * 0.68, -36, 4.0, 1.0);
        drawProp(images.fx(assets.car), 550, GamePanel.GROUND_Y - 64, 3.2);
        drawProp(images.fx(assets.barrel), 178, GamePanel.GROUND_Y - 34, 2.6);
        drawProp(images.fx(assets.hydrant), 1130, GamePanel.GROUND_Y - 32, 2.5);
        gc.setFill(new LinearGradient(0, GamePanel.GROUND_Y + 8, 0, VIEW_H, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(16, 14, 22, 0.75)),
                new Stop(1, Color.rgb(9, 8, 16, 0.96))));
        gc.fillRect(0, GamePanel.GROUND_Y + 6, VIEW_W, VIEW_H - GamePanel.GROUND_Y);
        gc.setStroke(Color.rgb(255, 255, 255, 0.13));
        gc.strokeLine(0, GamePanel.GROUND_Y + 8, VIEW_W, GamePanel.GROUND_Y + 8);
    }

    private void drawPixelTiled(Image image, double offsetX, double y, double scale, double alpha) {
        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.setImageSmoothing(false);
        int tileW = (int) Math.round(image.getWidth() * scale);
        int tileH = (int) Math.round(image.getHeight() * scale);
        int start = (int) Math.floor(offsetX % tileW) - tileW;
        for (int x = start; x < VIEW_W + tileW; x += tileW) {
            gc.drawImage(image, x, Math.round(y), tileW, tileH);
        }
        gc.restore();
    }

    private void drawProp(Image image, double worldX, double worldY, double scale) {
        gc.save();
        gc.setImageSmoothing(false);
        int w = (int) Math.round(image.getWidth() * scale);
        int h = (int) Math.round(image.getHeight() * scale);
        gc.drawImage(image, Math.round(worldX - cameraX), Math.round(worldY), w, h);
        gc.restore();
    }

    private void drawFighter(Fighter fighter) {
        if (fighter == null) {
            return;
        }
        double sx = fighter.x - cameraX;
        Image shadow = images.fx(assets.shadow);
        double shadowW = shadow.getWidth() * 4.2;
        double shadowH = shadow.getHeight() * 3.0;
        gc.drawImage(shadow, sx - shadowW / 2.0, fighter.y - shadowH / 2.0 + 3, shadowW, shadowH);

        if (fighter.energy > 84.0 && !fighter.dead) {
            gc.save();
            gc.setGlobalAlpha(0.18 + 0.12 * Math.sin(pulseTime * 8.0));
            gc.setFill(fx(fighter.teamColor));
            gc.fillOval(sx - 52, fighter.y - 156, 104, 150);
            gc.restore();
        }

        BufferedImage frame = fighter.currentFrame();
        Image image = images.fx(frame);
        double drawW = frame.getWidth() * fighter.renderScale;
        double drawH = frame.getHeight() * fighter.renderScale;
        double drawX = sx - drawW / 2.0;
        double drawY = fighter.y - drawH + 8;

        gc.save();
        gc.setImageSmoothing(false);
        if (fighter.facing >= 0) {
            gc.drawImage(image, drawX, drawY, drawW, drawH);
        } else {
            gc.translate(drawX + drawW, drawY);
            gc.scale(-1, 1);
            gc.drawImage(image, 0, 0, drawW, drawH);
        }
        gc.restore();

        if (fighter.hitFlash > 0.0 && ((int) (fighter.hitFlash * 36.0) % 2 == 0)) {
            gc.save();
            gc.setGlobalAlpha(0.38);
            gc.setFill(Color.WHITE);
            gc.fillOval(sx - 44, fighter.y - 160, 88, 142);
            gc.restore();
        } else if (fighter.paletteShift != null) {
            gc.save();
            gc.setGlobalAlpha(0.18);
            gc.setFill(fx(fighter.paletteShift));
            gc.fillOval(sx - 42, fighter.y - 156, 84, 138);
            gc.restore();
        }

        if (fighter.blocking && !fighter.dead) {
            gc.setStroke(Color.rgb(135, 224, 255, 0.62));
            gc.setLineWidth(3);
            double shieldX = sx + fighter.facing * 30;
            gc.strokeOval(shieldX - 38, fighter.y - 152, 76, 108);
        }
    }

    private void drawParticles(boolean foreground) {
        for (FxParticle particle : particles) {
            if (particle.foreground == foreground) {
                particle.draw(gc, cameraX);
            }
        }
        if (foreground) {
            for (FxFloatingText text : floatingTexts) {
                text.draw(gc, cameraX);
            }
        }
    }

    private void drawHud() {
        drawFighterHud(p1, 28, 24, false);
        drawFighterHud(p2, VIEW_W - 368, 24, true);
        setFont(36, FontWeight.BOLD);
        String time = String.format(Locale.US, "%02d", (int) Math.ceil(roundTime));
        drawSoftPanel(VIEW_W / 2.0 - 54, 16, 108, 62, Color.rgb(14, 16, 28, 0.82));
        gc.setFill(Color.web("#F6F3DD"));
        fillCentered(time, VIEW_W / 2.0, 59);
    }

    private void drawFighterHud(Fighter fighter, int x, int y, boolean right) {
        if (fighter == null) {
            return;
        }
        drawSoftPanel(x, y, 340, 86, Color.rgb(14, 16, 28, 0.84));
        int portraitX = right ? x + 272 : x + 12;
        drawPortrait(fighter, portraitX, y + 11, right);
        int barX = right ? x + 18 : x + 74;
        setFont(15, FontWeight.BOLD);
        gc.setFill(Color.rgb(240, 242, 250));
        String name = fighter.name.toUpperCase(Locale.US);
        if (right) {
            fillRight(name, barX + 240, y + 22);
        } else {
            gc.fillText(name, barX, y + 22);
        }
        drawBar(barX, y + 30, 240, 16, fighter.hp / fighter.maxHp, Color.web("#F34C58"), Color.web("#FFC857"), right);
        drawBar(barX, y + 53, 240, 10, fighter.energy / 100.0, Color.web("#4DE3FF"), Color.web("#7B61FF"), right);
        drawCooldownDots(fighter, right ? barX + 105 : barX, y + 69, right);
    }

    private void drawPortrait(Fighter fighter, int x, int y, boolean right) {
        gc.setFill(Color.rgb(255, 255, 255, 0.09));
        gc.fillRoundRect(x, y, 55, 55, 10, 10);
        BufferedImage frame = fighter.assets.getFrame(Anim.IDLE, 0);
        Image image = images.fx(frame);
        double w = frame.getWidth() * 1.85;
        double h = frame.getHeight() * 1.85;
        gc.save();
        gc.setImageSmoothing(false);
        if (right) {
            gc.translate(x + 74, y + 56 - h);
            gc.scale(-1, 1);
            gc.drawImage(image, 0, 0, w, h);
        } else {
            gc.drawImage(image, x - 8, y + 56 - h, w, h);
        }
        gc.restore();
        gc.setStroke(fx(fighter.teamColor));
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, 55, 55, 10, 10);
    }

    private void drawCooldownDots(Fighter fighter, int x, int y, boolean right) {
        double[] ready = {
                1.0 - fighter.skillOneCooldown / Fighter.SKILL_ONE_COOLDOWN,
                1.0 - fighter.skillTwoCooldown / Fighter.SKILL_TWO_COOLDOWN,
                1.0 - fighter.ultimateCooldown / Fighter.ULTIMATE_COOLDOWN
        };
        for (int i = 0; i < ready.length; i++) {
            int dotX = x + (right ? -i * 23 : i * 23);
            double amount = clamp(ready[i], 0, 1);
            gc.setFill(Color.rgb(255, 255, 255, 0.15));
            gc.fillRoundRect(dotX, y, 18, 7, 7, 7);
            gc.setFill(i == 2 ? Color.web("#FFD166") : Color.web("#64E4FF"));
            gc.fillRoundRect(dotX, y, 18 * amount, 7, 7, 7);
        }
    }

    private void drawBar(int x, int y, int w, int h, double value, Color leftColor, Color rightColor, boolean reverse) {
        value = clamp(value, 0, 1);
        gc.setFill(Color.rgb(255, 255, 255, 0.12));
        gc.fillRoundRect(x, y, w, h, h, h);
        double fill = w * value;
        if (fill <= 0) {
            return;
        }
        double fx = reverse ? x + w - fill : x;
        gc.setFill(new LinearGradient(fx, y, fx + fill, y, false, CycleMethod.NO_CYCLE,
                new Stop(0, reverse ? rightColor : leftColor),
                new Stop(1, reverse ? leftColor : rightColor)));
        gc.fillRoundRect(fx, y, fill, h, h, h);
        gc.setStroke(Color.rgb(255, 255, 255, 0.19));
        gc.strokeRoundRect(x, y, w, h, h, h);
    }

    private void drawMainMenu() {
        drawMenuOverlay();
        setFont(76, FontWeight.BOLD);
        drawCenteredGlow("SHADOW CLASH FX", 144, Color.web("#F6F3DD"), Color.web("#64E4FF"));
        setFont(18, FontWeight.BOLD);
        fillCentered("JAVA FX LOCAL ARENA", VIEW_W / 2.0, 178, Color.rgb(174, 223, 255));
        String[] items = {"START", "CHARACTERS", "QUIT"};
        for (int i = 0; i < items.length; i++) {
            drawMenuButton(VIEW_W / 2.0 - 150, 240 + i * 62, 300, 44, items[i], menuIndex == i);
        }
    }

    private void drawModeSelect() {
        drawMenuOverlay();
        setFont(44, FontWeight.BOLD);
        drawCenteredGlow("SELECT MODE", 112, Color.web("#F6F3DD"), Color.web("#FF4A6A"));
        drawModeCard(154, 190, 290, 190, "VERSUS", "Two players, one keyboard", modeIndex == 0);
        drawModeCard(516, 190, 290, 190, "ARCADE CPU", "Player vs computer AI", modeIndex == 1);
    }

    private void drawModeCard(int x, int y, int w, int h, String title, String detail, boolean selected) {
        drawSoftPanel(x, y, w, h, selected ? Color.rgb(28, 42, 70, 0.88) : Color.rgb(12, 16, 30, 0.80));
        if (selected) {
            gc.setStroke(Color.rgb(100, 228, 255, 0.48));
            gc.setLineWidth(3);
            gc.strokeRoundRect(x, y, w, h, 16, 16);
        }
        setFont(32, FontWeight.BOLD);
        fillCenteredWithin(title, x, y + 78, w, Color.WHITE);
        setFont(15, FontWeight.BOLD);
        fillCenteredWithin(detail, x, y + 118, w, Color.rgb(190, 210, 230));
    }

    private void drawCharacterSelect() {
        drawMenuOverlay();
        setFont(42, FontWeight.BOLD);
        drawCenteredGlow("SELECT FIGHTERS", 86, Color.web("#F6F3DD"), Color.web("#64E4FF"));
        drawCharacterCard(116, 142, 322, 292, assets.character(p1CharacterIndex), "PLAYER 1", Color.web("#50E3FF"));
        drawCharacterCard(522, 142, 322, 292, assets.character(p2CharacterIndex),
                gameMode == GameMode.CPU ? "CPU" : "PLAYER 2", Color.web("#FF4A6A"));
        drawMenuButton(VIEW_W / 2.0 - 118, 462, 236, 42, "ENTER ARENA", true);
    }

    private void drawCharacterCard(int x, int y, int w, int h, CharacterAssets character, String label, Color color) {
        drawSoftPanel(x, y, w, h, Color.rgb(11, 16, 29, 0.86));
        gc.setStroke(color);
        gc.setLineWidth(3);
        gc.strokeRoundRect(x, y, w, h, 18, 18);
        setFont(16, FontWeight.BOLD);
        fillCenteredWithin(label, x, y + 32, w, color);
        setFont(34, FontWeight.BOLD);
        fillCenteredWithin(character.displayName.toUpperCase(Locale.US), x, y + 70, w, Color.WHITE);
        BufferedImage frame = character.getFrame(Anim.IDLE, (int) (pulseTime * 8));
        Image image = images.fx(frame);
        double frameW = frame.getWidth() * 4.1;
        double frameH = frame.getHeight() * 4.1;
        gc.save();
        gc.setImageSmoothing(false);
        gc.drawImage(image, x + w / 2.0 - frameW / 2.0, y + 262 - frameH, frameW, frameH);
        gc.restore();
        setFont(14, FontWeight.BOLD);
        drawStat(x + 28, y + h - 48, "POWER", character.power, color);
        drawStat(x + 122, y + h - 48, "SPEED", character.speed, color);
        drawStat(x + 216, y + h - 48, "RANGE", character.range, color);
    }

    private void drawStat(int x, int y, String label, int amount, Color color) {
        gc.setFill(Color.rgb(230, 236, 245));
        gc.fillText(label, x, y);
        for (int i = 0; i < 5; i++) {
            gc.setFill(i < amount ? color : Color.rgb(255, 255, 255, 0.15));
            gc.fillRoundRect(x + i * 13, y + 10, 9, 20, 8, 8);
        }
    }

    private void drawResult() {
        gc.setFill(Color.rgb(0, 0, 0, 0.68));
        gc.fillRect(0, 0, VIEW_W, VIEW_H);
        drawSoftPanel(VIEW_W / 2.0 - 245, 160, 490, 190, Color.rgb(12, 16, 30, 0.92));
        setFont(42, FontWeight.BOLD);
        drawCenteredGlow(resultTitle, 232, Color.WHITE, Color.web("#FFD166"));
        setFont(17, FontWeight.BOLD);
        fillCentered(resultSubtitle, VIEW_W / 2.0, 274, Color.rgb(210, 222, 236));
        drawMenuButton(VIEW_W / 2.0 - 118, 300, 236, 40, "BACK TO MODE", true);
    }

    private void drawMenuOverlay() {
        gc.setFill(Color.rgb(2, 4, 12, 0.46));
        gc.fillRect(0, 0, VIEW_W, VIEW_H);
        for (int i = 0; i < 8; i++) {
            int y = 36 + i * 66;
            gc.setStroke(Color.rgb(255, 255, 255, i % 2 == 0 ? 0.05 : 0.02));
            gc.strokeLine(70, y, VIEW_W - 70, y + 22);
        }
    }

    private void drawMenuButton(double x, double y, double w, double h, String text, boolean selected) {
        drawSoftPanel(x, y, w, h, selected ? Color.rgb(33, 48, 72, 0.92) : Color.rgb(13, 17, 31, 0.86));
        if (selected) {
            gc.setStroke(Color.rgb(100, 228, 255, 0.53));
            gc.setLineWidth(2);
            gc.strokeRoundRect(x, y, w, h, 12, 12);
            gc.setFill(Color.rgb(255, 255, 255, 0.16));
            gc.fillRoundRect(x + 6, y + 5, w - 12, 10, 10, 10);
        }
        setFont(20, FontWeight.BOLD);
        fillCenteredWithin(text, x, y + 29, w, selected ? Color.WHITE : Color.rgb(190, 203, 220));
    }

    private void drawSoftPanel(double x, double y, double w, double h, Color fill) {
        gc.setFill(Color.rgb(0, 0, 0, 0.31));
        gc.fillRoundRect(x + 5, y + 7, w, h, 16, 16);
        gc.setFill(fill);
        gc.fillRoundRect(x, y, w, h, 16, 16);
        gc.setStroke(Color.rgb(255, 255, 255, 0.13));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 16, 16);
    }

    private void drawVignette() {
        gc.setStroke(Color.rgb(0, 0, 0, 0.28));
        gc.setLineWidth(36);
        gc.strokeRect(18, 18, VIEW_W - 36, VIEW_H - 36);
    }

    private void drawCenteredGlow(String text, double y, Color textColor, Color glowColor) {
        double x = VIEW_W / 2.0 - textWidth(text) / 2.0;
        gc.setFill(Color.color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 0.32));
        for (int i = 5; i >= 1; i--) {
            gc.fillText(text, x - i, y);
            gc.fillText(text, x + i, y);
            gc.fillText(text, x, y - i);
            gc.fillText(text, x, y + i);
        }
        gc.setFill(textColor);
        gc.fillText(text, x, y);
    }

    private void fillCentered(String text, double centerX, double y) {
        gc.fillText(text, centerX - textWidth(text) / 2.0, y);
    }

    private void fillCentered(String text, double centerX, double y, Color color) {
        gc.setFill(color);
        fillCentered(text, centerX, y);
    }

    private void fillCenteredWithin(String text, double x, double baselineY, double width, Color color) {
        gc.setFill(color);
        gc.fillText(text, x + (width - textWidth(text)) / 2.0, baselineY);
    }

    private void fillRight(String text, double rightX, double baselineY) {
        gc.fillText(text, rightX - textWidth(text), baselineY);
    }

    private double textWidth(String text) {
        Text node = new Text(text);
        node.setFont(gc.getFont());
        return node.getLayoutBounds().getWidth();
    }

    private void setFont(double size, FontWeight weight) {
        gc.setFont(Font.font("SansSerif", weight, size));
    }

    private Color fx(java.awt.Color color) {
        return Color.rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0);
    }

    private static final class FxImages {
        private final Map<BufferedImage, Image> cache = new IdentityHashMap<>();

        Image fx(BufferedImage source) {
            return cache.computeIfAbsent(source, image -> SwingFXUtils.toFXImage(image, null));
        }
    }

    private static final class FxParticle {
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

        static FxParticle spark(double x, double y, double vx, double vy, Color color, double life) {
            FxParticle p = new FxParticle();
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

        static FxParticle dust(double x, double y, double vx, double vy, Color color, double life) {
            FxParticle p = spark(x, y, vx, vy, color, life);
            p.size = 10.0 + Math.random() * 13.0;
            p.foreground = false;
            return p;
        }

        static FxParticle ring(double x, double y, Color color, double life) {
            FxParticle p = spark(x, y, 0, 0, color, life);
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

        void draw(GraphicsContext gc, double cameraX) {
            double alpha = Math.max(0, Math.min(1, life / maxLife));
            gc.save();
            gc.setGlobalAlpha(alpha);
            if (ring) {
                gc.setStroke(color);
                gc.setLineWidth(Math.max(1, 4 * alpha));
                gc.strokeOval(x - cameraX - size / 2.0, y - size / 2.0, size, size);
            } else {
                gc.setFill(color);
                gc.fillOval(x - cameraX - size / 2.0, y - size / 2.0, size, size);
            }
            gc.restore();
        }
    }

    private static final class FxFloatingText {
        final String text;
        final Color color;
        double x;
        double y;
        double life = 0.78;

        FxFloatingText(String text, double x, double y, Color color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }

        void update(double dt) {
            life -= dt;
            y -= 48 * dt;
        }

        void draw(GraphicsContext gc, double cameraX) {
            double alpha = Math.max(0, Math.min(1, life / 0.78));
            gc.save();
            gc.setGlobalAlpha(alpha);
            gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
            Text node = new Text(text);
            node.setFont(gc.getFont());
            double width = node.getLayoutBounds().getWidth();
            double sx = x - cameraX;
            gc.setFill(Color.rgb(0, 0, 0, 0.47));
            gc.fillText(text, sx - width / 2.0 + 2, y + 2);
            gc.setFill(color);
            gc.fillText(text, sx - width / 2.0, y);
            gc.restore();
        }
    }
}
