package com.ihansuru.greetingtodo;

import org.junit.Test;
import static org.junit.Assert.*;

public class CardGeometryTest {
    @Test public void moveWorksInAllDirectionsAndClamps() {
        CardGeometry.Box a = CardGeometry.move(100, 100, 200, 300, 60, 40, 500, 800);
        assertEquals(160f, a.x, .01f);
        assertEquals(140f, a.y, .01f);

        CardGeometry.Box b = CardGeometry.move(100, 100, 200, 300, -180, -190, 500, 800);
        assertEquals(0f, b.x, .01f);
        assertEquals(0f, b.y, .01f);

        CardGeometry.Box c = CardGeometry.move(100, 100, 200, 300, 1000, 1000, 500, 800);
        assertEquals(300f, c.x, .01f);
        assertEquals(500f, c.y, .01f);
    }

    @Test public void resizeHonorsMinimumAndMaximum() {
        CardGeometry.Box small = CardGeometry.resizeBottomRight(
                40, 60, 320, 400, -1000, -1000, 1080, 1920, 300, 360, false);
        assertEquals(300, small.width);
        assertEquals(360, small.height);

        CardGeometry.Box large = CardGeometry.resizeBottomRight(
                40, 60, 320, 400, 5000, 5000, 1080, 1920, 300, 360, false);
        assertTrue(large.width <= 1080);
        assertTrue(large.height <= 1920);
    }

    @Test public void imageResizeKeepsAspectRatio() {
        CardGeometry.Box box = CardGeometry.resizeBottomRight(
                20, 20, 400, 200, 200, 40, 1200, 1200, 64, 64, true);
        assertEquals(2f, box.width / (float) box.height, .03f);
    }

    @Test public void moveThenResizeAndResizeThenMoveBothWork() {
        CardGeometry.Box moved = CardGeometry.move(100, 120, 340, 420, 120, 80, 1080, 1920);
        CardGeometry.Box resized = CardGeometry.resizeBottomRight(
                moved.x, moved.y, moved.width, moved.height,
                90, 120, 1080, 1920, 300, 360, false);
        assertTrue(resized.width > moved.width);
        assertTrue(resized.height > moved.height);

        CardGeometry.Box firstResize = CardGeometry.resizeBottomRight(
                100, 120, 340, 420, 90, 120, 1080, 1920, 300, 360, false);
        CardGeometry.Box secondMove = CardGeometry.move(
                firstResize.x, firstResize.y, firstResize.width, firstResize.height,
                80, 70, 1080, 1920);
        assertTrue(secondMove.x > firstResize.x);
        assertTrue(secondMove.y > firstResize.y);
    }

    @Test public void scaleAroundCenterKeepsCenterWhenPossible() {
        CardGeometry.Box box = CardGeometry.scaleAroundCenter(
                200, 300, 300, 400, 1.2f, 1080, 1920, 280, 320, false);
        assertEquals(350f, box.x + box.width / 2f, 1f);
        assertEquals(500f, box.y + box.height / 2f, 1f);
    }
}
