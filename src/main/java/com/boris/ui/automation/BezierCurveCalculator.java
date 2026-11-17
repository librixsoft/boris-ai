package com.boris.ui.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.boris.ui.automation.model.ScreenCoordinate;

public final class BezierCurveCalculator {

    private BezierCurveCalculator() {}

    /**
     * Genera una trayectoria de coordenadas continuas entre start y end siguiendo una curva
     * de Bézier cúbica con puntos de control desfasados ortogonalmente y aceleración ease-in-out.
     */
    public static List<ScreenCoordinate> generateTrajectory(
            ScreenCoordinate start,
            ScreenCoordinate end,
            int steps) {

        if (steps <= 0) {
            throw new IllegalArgumentException("steps debe ser mayor a cero");
        }

        List<ScreenCoordinate> path = new ArrayList<>(steps + 1);
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        double distance = Math.hypot(deltaX, deltaY);

        double dispersion = Math.min(distance * 0.22, 55.0);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        double ctrl1X = start.x() + deltaX * 0.25 + rand.nextDouble(-dispersion, dispersion);
        double ctrl1Y = start.y() + deltaY * 0.25 + rand.nextDouble(-dispersion, dispersion);

        double ctrl2X = start.x() + deltaX * 0.75 + rand.nextDouble(-dispersion, dispersion);
        double ctrl2Y = start.y() + deltaY * 0.75 + rand.nextDouble(-dispersion, dispersion);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double easeT = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;

            double invT = 1.0 - easeT;
            double x = Math.pow(invT, 3) * start.x()
                     + 3 * Math.pow(invT, 2) * easeT * ctrl1X
                     + 3 * invT * Math.pow(easeT, 2) * ctrl2X
                     + Math.pow(easeT, 3) * end.x();

            double y = Math.pow(invT, 3) * start.y()
                     + 3 * Math.pow(invT, 2) * easeT * ctrl1Y
                     + 3 * invT * Math.pow(easeT, 2) * ctrl2Y
                     + Math.pow(easeT, 3) * end.y();

            path.add(new ScreenCoordinate(
                Math.max(0, (int) Math.round(x)),
                Math.max(0, (int) Math.round(y))
            ));
        }

        return Collections.unmodifiableList(path);
    }
}
