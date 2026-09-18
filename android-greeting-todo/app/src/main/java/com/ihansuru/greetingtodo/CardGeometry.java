package com.ihansuru.greetingtodo;

final class CardGeometry {
    static final class Box {
        final float x;
        final float y;
        final int width;
        final int height;

        Box(float x, float y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private CardGeometry() {}

    static Box move(float startX, float startY, int width, int height,
                    float dx, float dy, int parentWidth, int parentHeight) {
        int pw = Math.max(1, parentWidth);
        int ph = Math.max(1, parentHeight);
        int w = Math.max(1, Math.min(width, pw));
        int h = Math.max(1, Math.min(height, ph));
        float maxX = Math.max(0f, pw - w);
        float maxY = Math.max(0f, ph - h);
        return new Box(
                clamp(startX + dx, 0f, maxX),
                clamp(startY + dy, 0f, maxY),
                w,
                h);
    }

    static Box resizeBottomRight(float startX, float startY, int startWidth, int startHeight,
                                 float dx, float dy, int parentWidth, int parentHeight,
                                 int minWidth, int minHeight, boolean preserveAspect) {
        int pw = Math.max(1, parentWidth);
        int ph = Math.max(1, parentHeight);
        int minW = Math.max(1, Math.min(minWidth, pw));
        int minH = Math.max(1, Math.min(minHeight, ph));

        int newW = clampInt(Math.round(startWidth + dx), minW, pw);
        int newH = clampInt(Math.round(startHeight + dy), minH, ph);

        if (preserveAspect && startWidth > 0 && startHeight > 0) {
            float ratio = startWidth / (float) startHeight;
            float sx = newW / (float) startWidth;
            float sy = newH / (float) startHeight;
            float scale = Math.max(sx, sy);
            newW = clampInt(Math.round(startWidth * scale), minW, pw);
            newH = clampInt(Math.round(startHeight * scale), minH, ph);
            if (newW > pw) {
                newW = pw;
                newH = clampInt(Math.round(newW / ratio), minH, ph);
            }
            if (newH > ph) {
                newH = ph;
                newW = clampInt(Math.round(newH * ratio), minW, pw);
            }
        }

        return new Box(
                clamp(startX, 0f, Math.max(0f, pw - newW)),
                clamp(startY, 0f, Math.max(0f, ph - newH)),
                newW,
                newH);
    }

    static Box scaleAroundCenter(float x, float y, int width, int height,
                                 float factor, int parentWidth, int parentHeight,
                                 int minWidth, int minHeight, boolean preserveAspect) {
        int pw = Math.max(1, parentWidth);
        int ph = Math.max(1, parentHeight);
        int minW = Math.max(1, Math.min(minWidth, pw));
        int minH = Math.max(1, Math.min(minHeight, ph));
        float f = Float.isNaN(factor) || Float.isInfinite(factor) ? 1f : Math.max(.05f, factor);

        float cx = x + width / 2f;
        float cy = y + height / 2f;
        int newW = clampInt(Math.round(width * f), minW, pw);
        int newH = clampInt(Math.round(height * f), minH, ph);

        if (preserveAspect && width > 0 && height > 0) {
            float ratio = width / (float) height;
            newH = clampInt(Math.round(newW / ratio), minH, ph);
            if (newH > ph) {
                newH = ph;
                newW = clampInt(Math.round(newH * ratio), minW, pw);
            }
        }

        return new Box(
                clamp(cx - newW / 2f, 0f, Math.max(0f, pw - newW)),
                clamp(cy - newH / 2f, 0f, Math.max(0f, ph - newH)),
                newW,
                newH);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
