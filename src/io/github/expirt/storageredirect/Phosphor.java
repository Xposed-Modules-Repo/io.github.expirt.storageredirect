package io.github.expirt.storageredirect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

/**
 * Centralized Phosphor vector icon system matching Neo Backup's design language.
 * Icons are rendered with crisp anti-aliased Canvas paths based on Phosphor vector definitions
 * (256x256 coordinate space).
 * Reference: Phosphor Icons (MIT License) - https://phosphoricons.com/
 */
public final class Phosphor {

    public static final int HOUSE = 1;
    public static final int SLIDERS = 2;
    public static final int FILE_ZIP = 3;
    public static final int GEAR = 4;
    public static final int SEARCH = 5;
    public static final int REFRESH = 6;
    public static final int ARROW_LEFT = 7;
    public static final int ARROW_RIGHT = 8;
    public static final int ARROW_UP = 9;
    public static final int ARROW_DOWN = 10;
    public static final int PLUS_CIRCLE = 11;
    public static final int CHECK = 12;
    public static final int CHECK_CIRCLE = 13;
    public static final int WARNING = 14;
    public static final int CIRCLE_WAVY_WARNING = 15;
    public static final int PROHIBIT = 16;
    public static final int FOLDER = 17;
    public static final int PENCIL = 18;
    public static final int TRASH = 19;
    public static final int CARET_UP_DOWN = 20;
    public static final int CARET_DOWN_UP = 21;
    public static final int X = 22;
    public static final int INFO = 23;
    public static final int SHIELD_CHECKERED = 24;
    public static final int RESTORE = 25;
    public static final int LOCK = 26;
    public static final int SHARE = 27;
    public static final int BROOM = 28;

    private static final SparseArray<Path> PATH_CACHE = new SparseArray<Path>();

    private Phosphor() {}

    public static Drawable get(int type, int color) {
        return new PhosphorDrawable(type, color);
    }

    public static Drawable get(int type, int color, int sizePx) {
        PhosphorDrawable d = new PhosphorDrawable(type, color);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    public static final class PhosphorDrawable extends Drawable {
        private final int type;
        private final Paint paint;
        private final Path path;
        private int color;
        private float rotationDegrees = 0f;

        public PhosphorDrawable(int type, int color) {
            this.type = type;
            this.color = color;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setColor(color);
            this.paint.setStyle(Paint.Style.FILL);
            this.path = getCachedPath(type);
        }

        public void setColor(int color) {
            this.color = color;
            this.paint.setColor(color);
            invalidateSelf();
        }

        public void setRotation(float deg) {
            this.rotationDegrees = deg;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;

            int saveCount = canvas.save();
            canvas.translate(b.left, b.top);
            if (rotationDegrees != 0f) {
                canvas.rotate(rotationDegrees, b.width() / 2f, b.height() / 2f);
            }
            float scale = Math.min(b.width(), b.height()) / 256f;
            canvas.scale(scale, scale);

            // Center if non-square aspect ratio
            float padX = (b.width() / scale - 256f) / 2f;
            float padY = (b.height() / scale - 256f) / 2f;
            if (padX != 0 || padY != 0) {
                canvas.translate(padX, padY);
            }

            canvas.drawPath(path, paint);
            canvas.restoreToCount(saveCount);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 96;
        }

        @Override
        public int getIntrinsicHeight() {
            return 96;
        }
    }

    private static synchronized Path getCachedPath(int type) {
        Path p = PATH_CACHE.get(type);
        if (p == null) {
            p = buildPath(type);
            PATH_CACHE.put(type, p);
        }
        return p;
    }

    private static Path buildPath(int type) {
        Path p = new Path();
        switch (type) {
            case HOUSE:
                // Phosphor House
                p.moveTo(208f, 224f);
                p.lineTo(160f, 224f);
                p.rCubicTo(-8.8f, 0f, -16f, -7.2f, -16f, -16f);
                p.lineTo(144f, 160f);
                p.lineTo(112f, 160f);
                p.lineTo(112f, 208f);
                p.rCubicTo(0f, 8.8f, -7.2f, 16f, -16f, 16f);
                p.lineTo(48f, 224f);
                p.rCubicTo(-8.8f, 0f, -16f, -7.2f, -16f, -16f);
                p.lineTo(32f, 115.5f);
                p.rCubicTo(0f, -4.4f, 1.8f, -8.6f, 5.2f, -11.8f);
                p.lineTo(117.2f, 31f);
                p.rCubicTo(6f, -5.5f, 15.6f, -5.5f, 21.6f, 0f);
                p.lineTo(218.8f, 103.7f);
                p.rCubicTo(3.4f, 3.2f, 5.2f, 7.4f, 5.2f, 11.8f);
                p.lineTo(224f, 208f);
                p.rCubicTo(0f, 8.8f, -7.2f, 16f, -16f, 16f);
                p.close();

                p.moveTo(112f, 144f);
                p.lineTo(144f, 144f);
                p.rCubicTo(8.8f, 0f, 16f, 7.2f, 16f, 16f);
                p.lineTo(160f, 208f);
                p.lineTo(208f, 208f);
                p.lineTo(208f, 115.5f);
                p.lineTo(128f, 42.8f);
                p.lineTo(48f, 115.5f);
                p.lineTo(48f, 208f);
                p.lineTo(96f, 208f);
                p.lineTo(96f, 160f);
                p.rCubicTo(0f, -8.8f, 7.2f, -16f, 16f, -16f);
                p.close();
                break;

            case SLIDERS:
                // Phosphor SlidersHorizontal
                p.moveTo(216f, 164f);
                p.lineTo(194.8f, 164f);
                p.rCubicTo(-3.5f, -8.9f, -11.7f, -15.4f, -21.8f, -17.3f);
                p.rCubicTo(-10.1f, -1.9f, -20.4f, 1.2f, -27.8f, 8.3f);
                p.rCubicTo(-3.8f, 3.7f, -6.6f, 8.3f, -8f, 13.4f);
                p.lineTo(40f, 168f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.lineTo(137.2f, 184f);
                p.rCubicTo(2.8f, 9.9f, 11.8f, 16.9f, 22.8f, 17f);
                p.rCubicTo(11.1f, 0.1f, 20.4f, -6.8f, 23.4f, -17f);
                p.lineTo(216f, 184f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.close();

                p.moveTo(160f, 176f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.rCubicTo(-4.4f, 0f, -8f, -3.6f, -8f, -8f);
                p.close();

                p.moveTo(40f, 92f);
                p.lineTo(77.2f, 92f);
                p.rCubicTo(2.8f, 9.9f, 11.8f, 16.9f, 22.8f, 17f);
                p.rCubicTo(11.1f, 0.1f, 20.4f, -6.8f, 23.4f, -17f);
                p.lineTo(216f, 92f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.lineTo(123.4f, 76f);
                p.rCubicTo(-2.8f, -9.9f, -11.8f, -16.9f, -22.8f, -17f);
                p.rCubicTo(-11.1f, -0.1f, -20.4f, 6.8f, -23.4f, 17f);
                p.lineTo(40f, 76f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.close();

                p.moveTo(96f, 84f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.rCubicTo(-4.4f, 0f, -8f, -3.6f, -8f, -8f);
                p.close();
                break;

            case FILE_ZIP:
                // Phosphor FileZip
                p.moveTo(216f, 88f);
                p.rCubicTo(-0.6f, -2.1f, -1.6f, -4.1f, -3f, -5.7f);
                p.lineTo(157f, 26.3f);
                p.rCubicTo(-1.6f, -1.5f, -3.6f, -2.4f, -5.7f, -3f);
                p.lineTo(56f, 23.3f);
                p.rCubicTo(-8.8f, 0f, -16f, 7.2f, -16f, 16f);
                p.lineTo(40f, 128f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.lineTo(56f, 40f);
                p.lineTo(144f, 40f);
                p.lineTo(144f, 88f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.lineTo(200f, 96f);
                p.lineTo(200f, 128f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.lineTo(216f, 88f);
                p.close();

                p.moveTo(160f, 51.3f);
                p.lineTo(188.7f, 80f);
                p.lineTo(160f, 80f);
                p.close();

                // ZIP letters or lower body
                p.moveTo(184f, 160f);
                p.lineTo(168f, 160f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.lineTo(160f, 216f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.lineTo(176f, 208f);
                p.lineTo(184f, 208f);
                p.rCubicTo(13.2f, 0f, 24f, -10.8f, 24f, -24f);
                p.rCubicTo(0f, -13.2f, -10.8f, -24f, -24f, -24f);
                p.close();

                p.moveTo(184f, 192f);
                p.lineTo(176f, 192f);
                p.lineTo(176f, 176f);
                p.lineTo(184f, 176f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.close();

                p.moveTo(136f, 168f);
                p.lineTo(136f, 216f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.rCubicTo(-4.4f, 0f, -8f, -3.6f, -8f, -8f);
                p.lineTo(120f, 168f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.close();

                p.moveTo(88f, 160f);
                p.rCubicTo(3.8f, 0f, 7.1f, 2.2f, 8.5f, 5.7f);
                p.rCubicTo(1.4f, 3.5f, 0.5f, 7.5f, -2.1f, 10.1f);
                p.lineTo(70.9f, 208f);
                p.lineTo(88f, 208f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.lineTo(56f, 224f);
                p.rCubicTo(-3.8f, 0f, -7.1f, -2.2f, -8.5f, -5.7f);
                p.rCubicTo(-1.4f, -3.5f, -0.5f, -7.5f, 2.1f, -10.1f);
                p.lineTo(73.1f, 176f);
                p.lineTo(56f, 176f);
                p.rCubicTo(-4.4f, 0f, -8f, -3.6f, -8f, -8f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.close();
                break;

            case GEAR:
                // Phosphor GearSix
                p.moveTo(237.5f, 104.3f);
                p.rCubicTo(-0.9f, -2.2f, -2.6f, -4.1f, -4.8f, -5.1f);
                p.lineTo(209.7f, 85.8f);
                p.rCubicTo(-1.3f, -2.6f, -2.8f, -5.1f, -4.3f, -7.5f);
                p.lineTo(205.9f, 51.1f);
                p.rCubicTo(-0.4f, -2.4f, -1.8f, -4.5f, -3.8f, -5.9f);
                p.rCubicTo(-12.8f, -8.7f, -26.9f, -15.1f, -41.9f, -18.9f);
                p.rCubicTo(-2.3f, -0.6f, -4.8f, -0.1f, -6.8f, 1.2f);
                p.lineTo(130.1f, 41.5f);
                p.rCubicTo(-2.9f, -0.1f, -5.7f, -0.1f, -8.6f, 0f);
                p.lineTo(98.2f, 27.5f);
                p.rCubicTo(-2f, -1.3f, -4.5f, -1.8f, -6.8f, -1.2f);
                p.rCubicTo(-15f, 3.8f, -29.1f, 10.2f, -41.9f, 18.9f);
                p.rCubicTo(-2f, 1.4f, -3.4f, 3.5f, -3.8f, 5.9f);
                p.lineTo(46.2f, 78.3f);
                p.rCubicTo(-1.5f, 2.4f, -3f, 4.9f, -4.3f, 7.5f);
                p.lineTo(18.9f, 99.2f);
                p.rCubicTo(-2.2f, 1f, -3.9f, 2.9f, -4.8f, 5.1f);
                p.rCubicTo(-5.7f, 15.5f, -8.6f, 31.9f, -8.6f, 48.4f);
                p.rCubicTo(0f, 16.5f, 2.9f, 32.9f, 8.6f, 48.4f);
                p.rCubicTo(0.9f, 2.2f, 2.6f, 4.1f, 4.8f, 5.1f);
                p.lineTo(41.9f, 220f);
                p.rCubicTo(1.3f, 2.6f, 2.8f, 5.1f, 4.3f, 7.5f);
                p.lineTo(45.7f, 254.7f);
                p.close();

                // Simplify middle ring
                p.reset();
                p.addCircle(128f, 128f, 40f, Path.Direction.CW);
                p.moveTo(128f, 48f);
                p.rCubicTo(44.2f, 0f, 80f, 35.8f, 80f, 80f);
                p.rCubicTo(0f, 44.2f, -35.8f, 80f, -80f, 80f);
                p.rCubicTo(-44.2f, 0f, -80f, -35.8f, -80f, -80f);
                p.rCubicTo(0f, -44.2f, 35.8f, -80f, 80f, -80f);
                p.close();

                // Draw standard gear teeth
                for (int i = 0; i < 6; i++) {
                    double angle = i * Math.PI / 3.0;
                    float cx = (float) (128 + 92 * Math.cos(angle));
                    float cy = (float) (128 + 92 * Math.sin(angle));
                    p.addCircle(cx, cy, 18f, Path.Direction.CW);
                }
                break;

            case SEARCH:
                // Phosphor MagnifyingGlass
                p.moveTo(229.7f, 218.3f);
                p.lineTo(186.4f, 175.1f);
                p.rCubicTo(14.8f, -16.4f, 23.6f, -38.2f, 23.6f, -62.1f);
                p.rCubicTo(0f, -50.8f, -41.2f, -92f, -92f, -92f);
                p.rCubicTo(-50.8f, 0f, -92f, 41.2f, -92f, 92f);
                p.rCubicTo(0f, 50.8f, 41.2f, 92f, 92f, 92f);
                p.rCubicTo(23.9f, 0f, 45.7f, -8.8f, 62.1f, -23.6f);
                p.lineTo(218.3f, 229.7f);
                p.rCubicTo(3.2f, 3.2f, 8.2f, 3.2f, 11.4f, 0f);
                p.rCubicTo(3.1f, -3.2f, 3.1f, -8.2f, 0f, -11.4f);
                p.close();

                p.moveTo(118f, 192f);
                p.rCubicTo(-42f, 0f, -76f, -34f, -76f, -76f);
                p.rCubicTo(0f, -42f, 34f, -76f, 76f, -76f);
                p.rCubicTo(42f, 0f, 76f, 34f, 76f, 76f);
                p.rCubicTo(0f, 42f, -34f, 76f, -76f, 76f);
                p.close();
                break;

            case REFRESH:
                // Phosphor ArrowsClockwise
                p.moveTo(232.2f, 51.7f);
                p.lineTo(232.2f, 99.7f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.lineTo(176.2f, 107.7f);
                p.rCubicTo(-4.4f, 0f, -8f, -3.6f, -8f, -8f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.lineTo(204.9f, 91.7f);
                p.lineTo(184.6f, 71.4f);
                p.rCubicTo(-31.2f, -31.2f, -82f, -31.2f, -113.2f, 0f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.3f, 0f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.3f);
                p.rCubicTo(37.5f, -37.5f, 98.3f, -37.5f, 135.8f, 0f);
                p.lineTo(216.2f, 80.4f);
                p.lineTo(216.2f, 51.7f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.close();

                p.moveTo(184.6f, 184.6f);
                p.rCubicTo(-31.2f, 31.2f, -82f, 31.2f, -113.2f, 0f);
                p.lineTo(51.1f, 164.3f);
                p.lineTo(79.8f, 164.3f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.lineTo(31.8f, 148.3f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.lineTo(23.8f, 204.3f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.lineTo(39.8f, 175.6f);
                p.lineTo(60.1f, 195.9f);
                p.rCubicTo(37.5f, 37.5f, 98.3f, 37.5f, 135.8f, 0f);
                p.rCubicTo(3.1f, -3.1f, 3.1f, -8.2f, 0f, -11.3f);
                p.rCubicTo(-3.1f, -3.1f, -8.2f, -3.1f, -11.3f, 0f);
                p.close();
                break;

            case ARROW_LEFT:
                // Phosphor ArrowLeft
                p.moveTo(224f, 128f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.lineTo(59.3f, 136f);
                p.lineTo(117.7f, 194.3f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.4f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.4f, 0f);
                p.lineTo(34.3f, 133.7f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.lineTo(106.3f, 50.3f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.4f);
                p.lineTo(59.3f, 120f);
                p.lineTo(216f, 120f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.close();
                break;

            case ARROW_RIGHT:
                // Phosphor ArrowRight
                p.moveTo(221.7f, 133.7f);
                p.lineTo(149.7f, 205.7f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.4f, 0f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.lineTo(196.7f, 136f);
                p.lineTo(40f, 136f);
                p.rCubicTo(-4.4f, 0f, -8f, -3.6f, -8f, -8f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.lineTo(196.7f, 120f);
                p.lineTo(138.3f, 61.7f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.lineTo(221.7f, 122.3f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.4f);
                p.close();
                break;

            case ARROW_UP:
                p.moveTo(122.3f, 34.3f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.lineTo(205.7f, 106.3f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.4f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.4f, 0f);
                p.lineTo(136f, 59.3f);
                p.lineTo(136f, 216f);
                p.rCubicTo(0f, 4.4f, -3.6f, 8f, -8f, 8f);
                p.rCubicTo(-4.4f, 0f, -8f, -3.6f, -8f, -8f);
                p.lineTo(120f, 59.3f);
                p.lineTo(61.7f, 117.7f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.4f, 0f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.close();
                break;

            case ARROW_DOWN:
                p.moveTo(122.3f, 221.7f);
                p.rCubicTo(3.1f, 3.1f, 8.2f, 3.1f, 11.4f, 0f);
                p.lineTo(205.7f, 149.7f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, -8.2f, 0f, -11.4f);
                p.rCubicTo(-3.1f, -3.1f, -8.2f, -3.1f, -11.4f, 0f);
                p.lineTo(136f, 196.7f);
                p.lineTo(136f, 40f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.lineTo(120f, 196.7f);
                p.lineTo(61.7f, 138.3f);
                p.rCubicTo(-3.1f, -3.1f, -8.2f, -3.1f, -11.4f, 0f);
                p.rCubicTo(-3.1f, 3.1f, -3.1f, 8.2f, 0f, 11.4f);
                p.close();
                break;

            case PLUS_CIRCLE:
                // Phosphor PlusCircle
                p.moveTo(128f, 24f);
                p.rCubicTo(57.4f, 0f, 104f, 46.6f, 104f, 104f);
                p.rCubicTo(0f, 57.4f, -46.6f, 104f, -104f, 104f);
                p.rCubicTo(-57.4f, 0f, -104f, -46.6f, -104f, -104f);
                p.rCubicTo(0f, -57.4f, 46.6f, -104f, 104f, -104f);
                p.close();

                p.moveTo(128f, 216f);
                p.rCubicTo(48.5f, 0f, 88f, -39.5f, 88f, -88f);
                p.rCubicTo(0f, -48.5f, -39.5f, -88f, -88f, -88f);
                p.rCubicTo(-48.5f, 0f, -88f, 39.5f, -88f, 88f);
                p.rCubicTo(0f, 48.5f, 39.5f, 88f, 88f, 88f);
                p.close();

                p.moveTo(168f, 120f);
                p.lineTo(136f, 120f);
                p.lineTo(136f, 88f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.lineTo(120f, 120f);
                p.lineTo(88f, 120f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.lineTo(120f, 128f);
                p.lineTo(120f, 160f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.lineTo(136f, 128f);
                p.lineTo(168f, 128f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.close();
                break;

            case CHECK:
                // Phosphor Check
                p.moveTo(104f, 192f);
                p.rCubicTo(-2.1f, 0f, -4.1f, -0.8f, -5.7f, -2.3f);
                p.lineTo(42.3f, 133.7f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.lineTo(104f, 172.7f);
                p.lineTo(210.3f, 66.3f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.4f);
                p.lineTo(109.7f, 189.7f);
                p.rCubicTo(-1.6f, 1.5f, -3.6f, 2.3f, -5.7f, 2.3f);
                p.close();
                break;

            case CHECK_CIRCLE:
                // Phosphor CheckCircle
                p.moveTo(128f, 24f);
                p.rCubicTo(57.4f, 0f, 104f, 46.6f, 104f, 104f);
                p.rCubicTo(0f, 57.4f, -46.6f, 104f, -104f, 104f);
                p.rCubicTo(-57.4f, 0f, -104f, -46.6f, -104f, -104f);
                p.rCubicTo(0f, -57.4f, 46.6f, -104f, 104f, -104f);
                p.close();

                p.moveTo(128f, 216f);
                p.rCubicTo(48.5f, 0f, 88f, -39.5f, 88f, -88f);
                p.rCubicTo(0f, -48.5f, -39.5f, -88f, -88f, -88f);
                p.rCubicTo(-48.5f, 0f, -88f, 39.5f, -88f, 88f);
                p.rCubicTo(0f, 48.5f, 39.5f, 88f, 88f, 88f);
                p.close();

                p.moveTo(173.7f, 101.7f);
                p.lineTo(117.7f, 157.7f);
                p.rCubicTo(-1.6f, 1.6f, -3.6f, 2.3f, -5.7f, 2.3f);
                p.rCubicTo(-2.1f, 0f, -4.1f, -0.7f, -5.7f, -2.3f);
                p.lineTo(82.3f, 133.7f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.lineTo(112f, 140.7f);
                p.lineTo(162.3f, 90.3f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.3f, 0f, 11.4f);
                p.close();
                break;

            case WARNING:
            case CIRCLE_WAVY_WARNING:
                // Phosphor CircleWavyWarning (Badge warning)
                p.addCircle(128f, 128f, 104f, Path.Direction.CW);
                p.moveTo(120f, 72f);
                p.lineTo(120f, 136f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.lineTo(136f, 72f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.close();

                p.addCircle(128f, 172f, 10f, Path.Direction.CW);
                break;

            case PROHIBIT:
                // Phosphor Prohibit
                p.moveTo(128f, 24f);
                p.rCubicTo(57.4f, 0f, 104f, 46.6f, 104f, 104f);
                p.rCubicTo(0f, 22.8f, -7.3f, 44f, -19.8f, 61.3f);
                p.lineTo(66.7f, 43.8f);
                p.rCubicTo(17.3f, -12.5f, 38.5f, -19.8f, 61.3f, -19.8f);
                p.close();

                p.moveTo(128f, 232f);
                p.rCubicTo(-57.4f, 0f, -104f, -46.6f, -104f, -104f);
                p.rCubicTo(0f, -22.8f, 7.3f, -44f, 19.8f, -61.3f);
                p.lineTo(189.3f, 212.2f);
                p.rCubicTo(-17.3f, 12.5f, -38.5f, 19.8f, -61.3f, 19.8f);
                p.close();
                break;

            case FOLDER:
                // Phosphor FolderNotch
                p.moveTo(216f, 72f);
                p.lineTo(130.7f, 72f);
                p.lineTo(102.9f, 51.2f);
                p.rCubicTo(-2.8f, -2.1f, -6.1f, -3.2f, -9.6f, -3.2f);
                p.lineTo(40f, 48f);
                p.rCubicTo(-8.8f, 0f, -16f, 7.2f, -16f, 16f);
                p.lineTo(24f, 200f);
                p.rCubicTo(0f, 8.8f, 7.2f, 16f, 16f, 16f);
                p.lineTo(216f, 216f);
                p.rCubicTo(8.8f, 0f, 16f, -7.2f, 16f, -16f);
                p.lineTo(232f, 88f);
                p.rCubicTo(0f, -8.8f, -7.2f, -16f, -16f, -16f);
                p.close();

                p.moveTo(93.3f, 96f);
                p.lineTo(40f, 96f);
                p.lineTo(40f, 64f);
                p.lineTo(93.3f, 64f);
                p.lineTo(114.7f, 80f);
                p.lineTo(93.3f, 96f);
                p.close();

                p.moveTo(216f, 200f);
                p.lineTo(40f, 200f);
                p.lineTo(40f, 112f);
                p.lineTo(93.3f, 112f);
                p.rCubicTo(3.5f, 0f, 6.8f, -1.1f, 9.6f, -3.2f);
                p.lineTo(130.7f, 88f);
                p.lineTo(216f, 88f);
                p.close();
                break;

            case PENCIL:
                // Phosphor Pencil
                p.moveTo(227.3f, 73.4f);
                p.lineTo(182.6f, 28.7f);
                p.rCubicTo(-3.1f, -3.1f, -8.2f, -3.1f, -11.3f, 0f);
                p.lineTo(37.4f, 162.6f);
                p.rCubicTo(-1.5f, 1.5f, -2.4f, 3.5f, -2.7f, 5.6f);
                p.lineTo(24.2f, 222.9f);
                p.rCubicTo(-0.6f, 4.4f, 1.1f, 8.8f, 4.4f, 11.5f);
                p.rCubicTo(2.7f, 2.2f, 6.2f, 3.4f, 9.7f, 3.4f);
                p.rCubicTo(1f, 0f, 2.1f, -0.1f, 3.1f, -0.3f);
                p.lineTo(92.8f, 227f);
                p.rCubicTo(2.1f, -0.3f, 4.1f, -1.2f, 5.6f, -2.7f);
                p.lineTo(227.3f, 84.7f);
                p.rCubicTo(3.1f, -3.1f, 3.1f, -8.2f, 0f, -11.3f);
                p.close();

                p.moveTo(50.4f, 172.3f);
                p.lineTo(152f, 70.7f);
                p.lineTo(185.3f, 104f);
                p.lineTo(83.7f, 205.6f);
                p.lineTo(44.3f, 211.7f);
                p.close();
                break;

            case TRASH:
                // Phosphor TrashSimple
                p.moveTo(216f, 48f);
                p.lineTo(40f, 48f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.lineTo(48f, 64f);
                p.lineTo(48f, 208f);
                p.rCubicTo(0f, 8.8f, 7.2f, 16f, 16f, 16f);
                p.lineTo(192f, 224f);
                p.rCubicTo(8.8f, 0f, 16f, -7.2f, 16f, -16f);
                p.lineTo(208f, 64f);
                p.lineTo(216f, 64f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.close();

                p.moveTo(192f, 208f);
                p.lineTo(64f, 208f);
                p.lineTo(64f, 64f);
                p.lineTo(192f, 64f);
                p.close();

                p.moveTo(80f, 24f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.lineTo(168f, 16f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.lineTo(176f, 32f);
                p.lineTo(80f, 32f);
                p.close();
                break;

            case CARET_UP_DOWN:
                // Phosphor CaretUpDown
                p.moveTo(181.7f, 170.3f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.3f);
                p.lineTo(133.7f, 229.7f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.3f, 0f);
                p.lineTo(74.3f, 181.7f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.3f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.3f, 0f);
                p.lineTo(128f, 212.7f);
                p.lineTo(170.3f, 170.3f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.close();

                p.moveTo(85.7f, 85.7f);
                p.lineTo(128f, 43.3f);
                p.lineTo(170.3f, 85.7f);
                p.rCubicTo(3.1f, 3.1f, 8.2f, 3.1f, 11.4f, 0f);
                p.rCubicTo(3.1f, -3.1f, 3.1f, -8.2f, 0f, -11.3f);
                p.lineTo(133.7f, 26.3f);
                p.rCubicTo(-3.1f, -3.1f, -8.2f, -3.1f, -11.3f, 0f);
                p.lineTo(74.3f, 74.3f);
                p.rCubicTo(-3.1f, 3.1f, -3.1f, 8.2f, 0f, 11.4f);
                p.rCubicTo(3.1f, 3.1f, 8.2f, 3.1f, 11.4f, 0f);
                p.close();
                break;

            case CARET_DOWN_UP:
                // Inverted CaretDownUp (pointing outward or chevron)
                p.moveTo(128f, 160f);
                p.lineTo(80f, 208f);
                p.lineTo(91.3f, 219.3f);
                p.lineTo(128f, 182.6f);
                p.lineTo(164.7f, 219.3f);
                p.lineTo(176f, 208f);
                p.close();

                p.moveTo(128f, 96f);
                p.lineTo(80f, 48f);
                p.lineTo(91.3f, 36.7f);
                p.lineTo(128f, 73.4f);
                p.lineTo(164.7f, 36.7f);
                p.lineTo(176f, 48f);
                p.close();
                break;

            case X:
                // Phosphor X
                p.moveTo(205.7f, 194.3f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.4f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.4f, 0f);
                p.lineTo(128f, 139.3f);
                p.lineTo(61.7f, 205.7f);
                p.rCubicTo(-3.1f, 3.1f, -8.2f, 3.1f, -11.4f, 0f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.lineTo(116.7f, 128f);
                p.lineTo(50.3f, 61.7f);
                p.rCubicTo(-3.1f, -3.1f, -3.1f, -8.2f, 0f, -11.4f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.lineTo(128f, 116.7f);
                p.lineTo(194.3f, 50.3f);
                p.rCubicTo(3.1f, -3.1f, 8.2f, -3.1f, 11.4f, 0f);
                p.rCubicTo(3.1f, 3.1f, 3.1f, 8.2f, 0f, 11.4f);
                p.lineTo(139.3f, 128f);
                p.close();
                break;

            case INFO:
                // Phosphor Info
                p.moveTo(128f, 24f);
                p.rCubicTo(57.4f, 0f, 104f, 46.6f, 104f, 104f);
                p.rCubicTo(0f, 57.4f, -46.6f, 104f, -104f, 104f);
                p.rCubicTo(-57.4f, 0f, -104f, -46.6f, -104f, -104f);
                p.rCubicTo(0f, -57.4f, 46.6f, -104f, 104f, -104f);
                p.close();

                p.moveTo(128f, 216f);
                p.rCubicTo(48.5f, 0f, 88f, -39.5f, 88f, -88f);
                p.rCubicTo(0f, -48.5f, -39.5f, -88f, -88f, -88f);
                p.rCubicTo(-48.5f, 0f, -88f, 39.5f, -88f, 88f);
                p.rCubicTo(0f, 48.5f, 39.5f, 88f, 88f, 88f);
                p.close();

                p.moveTo(120f, 112f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.lineTo(112f, 168f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.lineTo(128f, 120f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.close();

                p.addCircle(120f, 84f, 10f, Path.Direction.CW);
                break;

            case SHIELD_CHECKERED:
                // Phosphor ShieldCheckered
                p.moveTo(224f, 114.7f);
                p.lineTo(224f, 56f);
                p.rCubicTo(0f, -8.8f, -7.2f, -16f, -16f, -16f);
                p.lineTo(48f, 40f);
                p.rCubicTo(-8.8f, 0f, -16f, 7.2f, -16f, 16f);
                p.lineTo(32f, 114.7f);
                p.rCubicTo(0f, 78.4f, 76f, 105.2f, 90.3f, 109.9f);
                p.rCubicTo(3.7f, 1.2f, 7.7f, 1.2f, 11.4f, 0f);
                p.rCubicTo(14.3f, -4.7f, 90.3f, -31.5f, 90.3f, -109.9f);
                p.close();

                p.moveTo(208f, 114.7f);
                p.lineTo(136f, 120f);
                p.lineTo(136f, 56f);
                p.lineTo(208f, 56f);
                p.close();

                p.moveTo(48f, 56f);
                p.lineTo(120f, 56f);
                p.lineTo(120f, 120f);
                p.lineTo(48.1f, 120f);
                p.close();

                p.moveTo(49.8f, 136f);
                p.lineTo(120f, 136f);
                p.lineTo(120f, 220.6f);
                p.rCubicTo(-19.7f, -8.4f, -61.2f, -31.9f, -70.2f, -84.6f);
                p.close();

                p.moveTo(136f, 220.6f);
                p.lineTo(136f, 136f);
                p.lineTo(206.2f, 136f);
                p.rCubicTo(-9f, 52.7f, -50.5f, 76.2f, -70.2f, 84.6f);
                p.close();
                break;

            case RESTORE:
                // Phosphor ClockCounterClockwise
                p.moveTo(136f, 80f);
                p.lineTo(136f, 123.4f);
                p.lineTo(173.6f, 145.1f);
                p.rCubicTo(3.8f, 2.2f, 5.1f, 7.1f, 2.9f, 10.9f);
                p.rCubicTo(-2.2f, 3.8f, -7.1f, 5.1f, -10.9f, 2.9f);
                p.lineTo(120f, 132f);
                p.lineTo(120f, 80f);
                p.rCubicTo(0f, -4.4f, 3.6f, -8f, 8f, -8f);
                p.rCubicTo(4.4f, 0f, 8f, 3.6f, 8f, 8f);
                p.close();

                p.moveTo(195.9f, 60.1f);
                p.rCubicTo(-37.5f, -37.5f, -98.3f, -37.5f, -135.8f, 0f);
                p.lineTo(39.8f, 80.4f);
                p.lineTo(39.8f, 59.7f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.rCubicTo(-4.4f, 0f, -8f, 3.6f, -8f, 8f);
                p.lineTo(23.8f, 107.7f);
                p.rCubicTo(0f, 4.4f, 3.6f, 8f, 8f, 8f);
                p.lineTo(71.8f, 115.7f);
                p.rCubicTo(4.4f, 0f, 8f, -3.6f, 8f, -8f);
                p.rCubicTo(0f, -4.4f, -3.6f, -8f, -8f, -8f);
                p.lineTo(51.1f, 91.2f);
                p.lineTo(71.4f, 71.4f);
                p.rCubicTo(31.2f, -31.2f, 82f, -31.2f, 113.2f, 0f);
                p.rCubicTo(31.2f, 31.2f, 31.2f, 82f, 0f, 113.2f);
                p.rCubicTo(-3.1f, 3.1f, -3.1f, 8.2f, 0f, 11.3f);
                p.rCubicTo(3.1f, 3.1f, 8.2f, 3.1f, 11.3f, 0f);
                p.rCubicTo(37.5f, -37.5f, 37.5f, -98.3f, 0f, -135.8f);
                p.close();
                break;

            case LOCK:
                p.moveTo(208f, 96f);
                p.lineTo(176f, 96f);
                p.lineTo(176f, 80f);
                p.rCubicTo(0f, -26.5f, -21.5f, -48f, -48f, -48f);
                p.rCubicTo(-26.5f, 0f, -48f, 21.5f, -48f, 48f);
                p.lineTo(80f, 96f);
                p.lineTo(48f, 96f);
                p.rCubicTo(-8.8f, 0f, -16f, 7.2f, -16f, 16f);
                p.lineTo(32f, 208f);
                p.rCubicTo(0f, 8.8f, 7.2f, 16f, 16f, 16f);
                p.lineTo(208f, 224f);
                p.rCubicTo(8.8f, 0f, 16f, -7.2f, 16f, -16f);
                p.lineTo(224f, 112f);
                p.rCubicTo(0f, -8.8f, -7.2f, -16f, -16f, -16f);
                p.close();

                p.moveTo(96f, 80f);
                p.rCubicTo(0f, -17.6f, 14.4f, -32f, 32f, -32f);
                p.rCubicTo(17.6f, 0f, 32f, 14.4f, 32f, 32f);
                p.lineTo(160f, 96f);
                p.lineTo(96f, 96f);
                p.close();
                break;

            case SHARE:
                p.moveTo(200f, 176f);
                p.rCubicTo(-10.1f, 0f, -19.3f, 3.8f, -26.3f, 10.1f);
                p.lineTo(97.2f, 137.9f);
                p.rCubicTo(0.5f, -3.2f, 0.8f, -6.5f, 0.8f, -9.9f);
                p.rCubicTo(0f, -3.4f, -0.3f, -6.7f, -0.8f, -9.9f);
                p.lineTo(173.7f, 69.9f);
                p.rCubicTo(7f, 6.3f, 16.2f, 10.1f, 26.3f, 10.1f);
                p.rCubicTo(22.1f, 0f, 40f, -17.9f, 40f, -40f);
                p.rCubicTo(0f, -22.1f, -17.9f, -40f, -40f, -40f);
                p.rCubicTo(-22.1f, 0f, -40f, 17.9f, -40f, 40f);
                p.rCubicTo(0f, 3.4f, 0.3f, 6.7f, 0.8f, 9.9f);
                p.lineTo(82.3f, 106.1f);
                p.rCubicTo(-7f, -6.3f, -16.2f, -10.1f, -26.3f, -10.1f);
                p.rCubicTo(-22.1f, 0f, -40f, 17.9f, -40f, 40f);
                p.rCubicTo(0f, 22.1f, 17.9f, 40f, 40f, 40f);
                p.rCubicTo(10.1f, 0f, 19.3f, -3.8f, 26.3f, -10.1f);
                p.lineTo(174.8f, 186.1f);
                p.rCubicTo(-0.5f, 3.2f, -0.8f, 6.5f, -0.8f, 9.9f);
                p.rCubicTo(0f, 22.1f, 17.9f, 40f, 40f, 40f);
                p.rCubicTo(22.1f, 0f, 40f, -17.9f, 40f, -40f);
                p.rCubicTo(0f, -22.1f, -17.9f, -40f, -40f, -40f);
                p.close();
                break;

            default:
                // Default fallback circle
                p.addCircle(128f, 128f, 80f, Path.Direction.CW);
                break;
        }
        return p;
    }
}
