package com.boris.ui.automation;

import com.boris.ui.automation.model.ScreenCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BezierCurveCalculatorTest {

    @Test
    void generateTrajectoryReturnsCorrectNumberOfPoints() {
        ScreenCoordinate start = new ScreenCoordinate(0, 0);
        ScreenCoordinate end = new ScreenCoordinate(100, 100);
        int steps = 10;

        var path = BezierCurveCalculator.generateTrajectory(start, end, steps);
        assertEquals(steps + 1, path.size(), "Debe haber steps + 1 puntos");
    }

    @Test
    void trajectoryStartsAtStartPoint() {
        ScreenCoordinate start = new ScreenCoordinate(10, 20);
        ScreenCoordinate end = new ScreenCoordinate(100, 200);
        int steps = 20;

        var path = BezierCurveCalculator.generateTrajectory(start, end, steps);
        assertEquals(start.x(), path.get(0).x());
        assertEquals(start.y(), path.get(0).y());
    }

    @Test
    void trajectoryEndsAtEndPoint() {
        ScreenCoordinate start = new ScreenCoordinate(0, 0);
        ScreenCoordinate end = new ScreenCoordinate(50, 75);
        int steps = 15;

        var path = BezierCurveCalculator.generateTrajectory(start, end, steps);
        assertEquals(end.x(), path.get(path.size() - 1).x());
        assertEquals(end.y(), path.get(path.size() - 1).y());
    }

    @Test
    void zeroStepsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> BezierCurveCalculator.generateTrajectory(
            new ScreenCoordinate(0, 0), new ScreenCoordinate(10, 10), 0));
    }

    @Test
    void negativeStepsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> BezierCurveCalculator.generateTrajectory(
            new ScreenCoordinate(0, 0), new ScreenCoordinate(10, 10), -1));
    }

    @Test
    void trajectoryPointsAreNonNegative() {
        ScreenCoordinate start = new ScreenCoordinate(0, 0);
        ScreenCoordinate end = new ScreenCoordinate(100, 100);
        int steps = 30;

        var path = BezierCurveCalculator.generateTrajectory(start, end, steps);
        for (var point : path) {
            assertTrue(point.x() >= 0);
            assertTrue(point.y() >= 0);
        }
    }
}
