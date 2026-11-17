package com.boris.ui.automation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import com.microsoft.playwright.Page;
import com.boris.ui.automation.model.ScreenCoordinate;

public class CoordinateInputActuator {

    public static final double DEFAULT_TYPO_PROBABILITY = 0.035; // 3.5% de probabilidad de error
    public static final int DEFAULT_TYPO_REACTION_MIN_MS = 200;  // Vacilación mínima de reacción
    public static final int DEFAULT_TYPO_REACTION_MAX_MS = 450;  // Vacilación máxima de reacción
    public static final int DEFAULT_TYPO_CORRECTION_PAUSE_MIN_MS = 80;
    public static final int DEFAULT_TYPO_CORRECTION_PAUSE_MAX_MS = 160;
    public static final double DEFAULT_TYPING_MEAN_MS = 95.0;    // Media gaussiana de tecleo
    public static final double DEFAULT_TYPING_STDDEV_MS = 25.0;  // Desviación estándar

    private static final Map<Character, String> ADJACENT_KEYS = Map.ofEntries(
        Map.entry('a', "qwsz"),
        Map.entry('b', "vghn"),
        Map.entry('c', "xdfv"),
        Map.entry('d', "ersfxc"),
        Map.entry('e', "wsdr"),
        Map.entry('f', "rtgdcv"),
        Map.entry('g', "tyhfvb"),
        Map.entry('h', "yujgbn"),
        Map.entry('i', "ujko"),
        Map.entry('j', "uikmnh"),
        Map.entry('k', "ijolm"),
        Map.entry('l', "okp"),
        Map.entry('m', "njk"),
        Map.entry('n', "bhjm"),
        Map.entry('o', "iklp"),
        Map.entry('p', "ol"),
        Map.entry('q', "wa"),
        Map.entry('r', "edft"),
        Map.entry('s', "wedxza"),
        Map.entry('t', "rfgy"),
        Map.entry('u', "yhji"),
        Map.entry('v', "cfgb"),
        Map.entry('w', "qase"),
        Map.entry('x', "zsdc"),
        Map.entry('y', "tghu"),
        Map.entry('z', "asx")
    );

    private final Page page;
    private final EmergencyFailsafeGuard failsafeGuard;
    private final double typoProbability;
    private final int typoReactionMinMs;
    private final int typoReactionMaxMs;
    private ScreenCoordinate currentMousePosition = new ScreenCoordinate(200, 200);

    public CoordinateInputActuator(Page page, EmergencyFailsafeGuard failsafeGuard) {
        this(page, failsafeGuard, DEFAULT_TYPO_PROBABILITY, DEFAULT_TYPO_REACTION_MIN_MS, DEFAULT_TYPO_REACTION_MAX_MS);
    }

    public CoordinateInputActuator(
            Page page,
            EmergencyFailsafeGuard failsafeGuard,
            double typoProbability,
            int typoReactionMinMs,
            int typoReactionMaxMs) {
        if (page == null) throw new IllegalArgumentException("page cannot be null");
        if (failsafeGuard == null) throw new IllegalArgumentException("failsafeGuard cannot be null");
        this.page = page;
        this.failsafeGuard = failsafeGuard;
        this.typoProbability = typoProbability;
        this.typoReactionMinMs = typoReactionMinMs;
        this.typoReactionMaxMs = typoReactionMaxMs;
    }

    public void moveAndClick(ScreenCoordinate target) {
        failsafeGuard.assertSafe(target);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        double distance = Math.hypot(target.x() - currentMousePosition.x(), target.y() - currentMousePosition.y());
        int steps = Math.max(14, Math.min(38, (int) (distance / 12)));

        List<ScreenCoordinate> trajectory = BezierCurveCalculator.generateTrajectory(currentMousePosition, target, steps);

        // Movimiento visible en pantalla en tiempo real
        for (ScreenCoordinate point : trajectory) {
            failsafeGuard.assertSafe(point);
            page.mouse().move(point.x(), point.y());
            sleep(4 + rand.nextInt(6));
        }
        currentMousePosition = target;

        // Hover previo al clic
        sleep(30 + rand.nextInt(40));

        // Pulsación física de botón izquierdo
        page.mouse().down();
        sleep(70 + rand.nextInt(45));
        page.mouse().up();
    }

    public void typeHumanText(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be null or empty");
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (char c : text.toCharArray()) {
            char lower = Character.toLowerCase(c);

            // Simulación de error humano en teclado físico
            if (ADJACENT_KEYS.containsKey(lower) && rand.nextDouble() < typoProbability) {
                String neighbors = ADJACENT_KEYS.get(lower);
                char typoChar = neighbors.charAt(rand.nextInt(neighbors.length()));
                if (Character.isUpperCase(c)) {
                    typoChar = Character.toUpperCase(typoChar);
                }

                // 1. Escribir la tecla equivocada
                page.keyboard().type(String.valueOf(typoChar));

                // 2. Darse cuenta del error (reacción humana)
                sleep(rand.nextInt(typoReactionMinMs, typoReactionMaxMs));

                // 3. Borrar con Backspace
                page.keyboard().press("Backspace");

                // 4. Pausa antes de reintentar
                sleep(DEFAULT_TYPO_CORRECTION_PAUSE_MIN_MS + rand.nextInt(DEFAULT_TYPO_CORRECTION_PAUSE_MAX_MS - DEFAULT_TYPO_CORRECTION_PAUSE_MIN_MS));
            }

            // Escribir el carácter correcto
            page.keyboard().type(String.valueOf(c));

            // Retardo gaussiano de tecleo
            long delay = (long) Math.max(45, (rand.nextGaussian() * DEFAULT_TYPING_STDDEV_MS + DEFAULT_TYPING_MEAN_MS));
            sleep(delay);

            // Pausa reflexiva al finalizar palabras
            if (c == ' ' && rand.nextInt(10) < 2) {
                sleep(220 + rand.nextInt(280));
            }
        }
    }

    public void pressKey(String key) {
        page.keyboard().press(key);
        sleep(120 + ThreadLocalRandom.current().nextInt(60));
    }

    public void scrollSmooth(int deltaY) {
        int chunks = Math.abs(deltaY) / 100 + 1;
        int chunkAmount = deltaY / chunks;
        for (int i = 0; i < chunks; i++) {
            page.mouse().wheel(0, chunkAmount);
            sleep(45 + ThreadLocalRandom.current().nextInt(35));
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operación de entrada interrumpida", e);
        }
    }
}
