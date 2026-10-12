/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * The living parts of the home art: the lamps breathe and now and then gutter, the stars in the
 * windows twinkle, the moon's halo swells, the lit windows of the far towers flicker and once in a
 * while a star falls. Positions are pixels of the menu art (1672 x 941) and follow
 * {@link AcademyArt#drawCover}. Nothing is drawn while motion is switched off, so the art then
 * stays exactly as painted.
 */
final class AcademyHomeMotion {
    /** x, y, radius, strength: the glass of the painted lamps */
    private static final float[][] LAMPS = {
            {1097, 452, 70, 1f}, {1010, 528, 46, 0.8f}, {1152, 548, 38, 0.75f}, {1425, 412, 80, 1f},
            {1636, 515, 112, 1.1f}, {1287, 498, 46, 0.8f}, {1243, 522, 30, 0.6f}, {588, 518, 20, 0.7f}};
    /** lamp, x, y, half width, half height: the reflections of the lamps on the wet floor */
    private static final float[][] FLOOR = {{0, 1090, 770, 34, 80}, {1, 1012, 760, 24, 58}};
    /** the light above the armillary sphere in the dome */
    private static final float[] CHANDELIER = {1338, 138, 60};
    private static final float[] MOON = {867, 102, 24};
    /** lit windows in the far towers */
    private static final float[][] WINDOWS = {
            {680, 190}, {712, 262}, {685, 465}, {877, 470}, {899, 217}, {913, 216}, {898, 250}, {913, 247}};
    /** x, y, size: stars in the open sky and in the arch windows; size 3 and up gets rays */
    private static final float[][] STARS = {
            {890, 33, 3.2f}, {915, 26, 2.4f}, {861, 41, 2f}, {905, 56, 1.8f}, {835, 156, 3f}, {846, 148, 1.6f},
            {828, 20, 2.2f}, {938, 42, 2.6f}, {880, 18, 1.6f}, {845, 62, 1.8f}, {668, 85, 2.4f}, {707, 92, 3f},
            {690, 128, 1.6f}, {665, 340, 2f}, {712, 360, 2.6f}, {695, 322, 1.6f}};
    /** the gold stars painted on the dome */
    private static final float[][] DOME = {
            {1285, 154}, {1262, 187}, {1240, 210}, {1335, 267}, {1253, 273}, {1348, 277}, {1366, 196}, {1300, 250}};
    /** the open sky right of the arch, the only place a falling star crosses */
    private static final float[] SKY = {818, 0, 944, 150};
    private static final double FALL_EVERY = 9;
    private static final double FALL_TIME = 0.8;

    private final double s;
    private final double dx;
    private final double dy;
    private final int w;

    private AcademyHomeMotion(BufferedImage img, int w, int h, double anchorX) {
        this.w = w;
        s = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * s);
        int dh = (int) Math.ceil(img.getHeight() * s);
        dx = -(int) ((dw - w) * anchorX);
        dy = -(dh - h) / 2;
    }

    /**
     * The light of the scene; drawn over the art and under the darkening for the text.
     *
     * @param g       the graphics
     * @param img     the menu art as drawn by drawCover
     * @param w       the width
     * @param h       the height
     * @param anchorX the anchor given to drawCover
     * @param t       the time in seconds
     */
    static void behind(Graphics2D g, BufferedImage img, int w, int h, double anchorX, double t) {
        if (!AcademyAtmosphere.motion() || img == null)
            return;
        AcademyHomeMotion m = new AcademyHomeMotion(img, w, h, anchorX);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        m.moon(g2, t);
        m.windows(g2, t);
        m.chandelier(g2, t);
        for (int i = 0; i < LAMPS.length; i++)
            m.lamp(g2, i, t);
        g2.dispose();
    }

    /**
     * The stars; drawn over the darkening, so that they still twinkle where the art is dimmed.
     *
     * @param g       the graphics
     * @param img     the menu art as drawn by drawCover
     * @param w       the width
     * @param h       the height
     * @param anchorX the anchor given to drawCover
     * @param t       the time in seconds
     */
    static void front(Graphics2D g, BufferedImage img, int w, int h, double anchorX, double t) {
        if (!AcademyAtmosphere.motion() || img == null)
            return;
        AcademyHomeMotion m = new AcademyHomeMotion(img, w, h, anchorX);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < STARS.length; i++)
            m.star(g2, i, t);
        for (int i = 0; i < DOME.length; i++)
            m.domeStar(g2, i, t);
        m.fallingStar(g2, t);
        g2.dispose();
    }

    /**
     * Brightness of a lamp: about -1 while it gutters, up to 1 while it flares, mostly a little
     * above 0. Every lamp has its own rhythm and gutters for a moment roughly every fifteen seconds.
     *
     * @param i the lamp
     * @param t the time in seconds
     * @return the brightness, -1 .. 1
     */
    static double flicker(int i, double t) {
        double p = i * 1.7;
        double f = 0.5 * Math.sin(t * (2.3 + i * 0.31) + p) + 0.3 * Math.sin(t * (5.1 + i * 0.7) + p * 2.3)
                + 0.2 * Math.sin(t * (11.7 + i * 1.3) + p * 0.7);
        double gutter = Math.max(0, Math.sin(t * (0.41 + i * 0.07) + p * 3.1) - 0.94) / 0.06;
        double b = 0.25 + 0.75 * f - 1.6 * gutter * (0.6 + 0.4 * Math.sin(t * 31 + i));
        return Math.max(-1, Math.min(1, b));
    }

    /**
     * Brightness of a star: long dim stretches and short bright peaks.
     *
     * @param i the star
     * @param t the time in seconds
     * @return the brightness, 0 .. 1
     */
    static double twinkle(int i, double t) {
        double k = 0.5 + 0.5 * Math.sin(t * (1.1 + (i % 5) * 0.37) + i * 2.399);
        return k * k * k;
    }

    private float x(float ix) {
        return (float) (dx + ix * s);
    }

    private float y(float iy) {
        return (float) (dy + iy * s);
    }

    private void glow(Graphics2D g, float cx, float cy, float r, float[] stops, Color[] colors) {
        if (r < 1)
            return;
        g.setPaint(new RadialGradientPaint(cx, cy, r, stops, colors));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    private static Color c(int r, int g, int b, double a) {
        return new Color(r, g, b, (int) Math.max(0, Math.min(255, a * 255)));
    }

    private void lamp(Graphics2D g, int i, double t) {
        float[] l = LAMPS[i];
        float cx = x(l[0]);
        float cy = y(l[1]);
        float r = (float) (l[2] * s);
        double b = flicker(i, t);
        if (b > 0) {
            double a = b * l[3];
            glow(g, cx, cy, r * 1.7f, new float[]{0, 0.12f, 0.45f, 1},
                    new Color[]{c(255, 244, 214, a * 0.55), c(255, 214, 140, a * 0.42), c(240, 172, 72, a * 0.16),
                            c(240, 172, 72, 0)});
        } else {
            double d = -b * l[3];
            glow(g, cx, cy, r * 1.15f, new float[]{0, 0.45f, 1},
                    new Color[]{c(16, 11, 8, d * 0.62), c(16, 11, 8, d * 0.34), c(16, 11, 8, 0)});
        }
        for (float[] f : FLOOR)
            if (f[0] == i) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x(f[1]), y(f[2]));
                g2.scale(1, f[4] / f[3]);
                float rr = (float) (f[3] * s);
                if (b > 0)
                    glow(g2, 0, 0, rr, new float[]{0, 1}, new Color[]{c(255, 210, 130, b * 0.32), c(255, 210, 130, 0)});
                else
                    glow(g2, 0, 0, rr, new float[]{0, 1}, new Color[]{c(12, 14, 24, -b * 0.3), c(12, 14, 24, 0)});
                g2.dispose();
            }
    }

    private void chandelier(Graphics2D g, double t) {
        double k = 0.5 + 0.5 * Math.sin(t * 0.9) * Math.cos(t * 0.37);
        glow(g, x(CHANDELIER[0]), y(CHANDELIER[1]), (float) (CHANDELIER[2] * s * (1.4 + 0.3 * k)),
                new float[]{0, 0.2f, 1},
                new Color[]{c(255, 240, 200, 0.2 + 0.35 * k), c(255, 210, 130, 0.08 + 0.18 * k), c(255, 210, 130, 0)});
    }

    private void moon(Graphics2D g, double t) {
        double k = 0.5 + 0.5 * Math.sin(t * 0.6);
        float r = (float) (MOON[2] * s * (3.2 + 0.7 * k));
        glow(g, x(MOON[0]), y(MOON[1]), r, new float[]{0, 0.3f, 0.6f, 1},
                new Color[]{c(255, 250, 230, 0.12 + 0.12 * k), c(230, 238, 255, 0.12 + 0.14 * k),
                        c(190, 210, 255, 0.04 + 0.06 * k), c(190, 210, 255, 0)});
    }

    private void windows(Graphics2D g, double t) {
        for (int i = 0; i < WINDOWS.length; i++) {
            double v = Math.sin(t * (1.3 + i * 0.41) + i * 2.1) + 0.45 * Math.sin(t * (7.3 + i) + i);
            float cx = x(WINDOWS[i][0]);
            float cy = y(WINDOWS[i][1]);
            if (v > 0.5) {
                double a = Math.min(1, (v - 0.5) / 0.9);
                glow(g, cx, cy, (float) (11 * s), new float[]{0, 0.3f, 1},
                        new Color[]{c(255, 226, 150, a * 0.85), c(255, 190, 90, a * 0.4), c(255, 190, 90, 0)});
            } else if (v < -1.1) {
                double a = Math.min(1, (-1.1 - v) / 0.3);
                glow(g, cx, cy, (float) (6 * s), new float[]{0, 1},
                        new Color[]{c(10, 16, 32, a * 0.75), c(10, 16, 32, 0)});
            }
        }
    }

    /** how much the darkening for the text keeps of a star at screen x */
    private double keep(float sx) {
        double a = (235 + (60 - 235) * Math.min(1, Math.max(0, sx / (0.7 * w)))) / 255;
        return 1 - 0.55 * a;
    }

    private void star(Graphics2D g, int i, double t) {
        float[] st = STARS[i];
        float cx = x(st[0]);
        float cy = y(st[1]);
        double k = twinkle(i, t);
        double keep = keep(cx);
        float scale = (float) Math.max(0.7, s);
        float size = st[2] * scale;
        glow(g, cx, cy, size * 3.2f, new float[]{0, 0.25f, 1},
                new Color[]{c(235, 242, 255, (0.15 + 0.85 * k) * keep), c(190, 214, 255, (0.05 + 0.4 * k) * keep),
                        c(190, 214, 255, 0)});
        if (st[2] >= 3 && k > 0.3) {
            float len = size * (2.5f + 4f * (float) k);
            Color core = c(240, 246, 255, (k - 0.3) / 0.7 * 0.9 * keep);
            Color end = c(240, 246, 255, 0);
            g.setStroke(new BasicStroke(Math.max(1f, scale * 0.9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int d = 0; d < 4; d++) {
                float ex = cx + (d == 0 ? len : d == 1 ? -len : 0);
                float ey = cy + (d == 2 ? len : d == 3 ? -len : 0);
                g.setPaint(new GradientPaint(cx, cy, core, ex, ey, end));
                g.draw(new Line2D.Float(cx, cy, ex, ey));
            }
        }
    }

    private void domeStar(Graphics2D g, int i, double t) {
        double k = twinkle(i + 7, t * 0.8);
        float cx = x(DOME[i][0]);
        float cy = y(DOME[i][1]);
        float r = (float) (Math.max(0.7, s) * 5);
        glow(g, cx, cy, r, new float[]{0, 0.3f, 1},
                new Color[]{c(255, 236, 180, 0.1 + 0.8 * k), c(255, 206, 120, 0.05 + 0.35 * k), c(255, 206, 120, 0)});
    }

    /**
     * Where a falling star is at the given time.
     *
     * @param t the time in seconds
     * @return x, y of the head, x, y of the end of the tail in art pixels and the brightness, or null
     * while no star falls
     */
    static float[] fallingStar(double t) {
        long n = (long) Math.floor(t / FALL_EVERY);
        double u = (t - n * FALL_EVERY) / FALL_TIME;
        if (u < 0 || u >= 1)
            return null;
        float off = (float) ((n * 37) % 40);
        float x0 = 944;
        float y0 = 14 + off * 0.4f;
        float x1 = 840;
        float y1 = 76 + off * 0.3f;
        float hx = (float) (x0 + (x1 - x0) * u);
        float hy = (float) (y0 + (y1 - y0) * u);
        float tail = 0.35f;
        return new float[]{hx, hy, hx - (x1 - x0) * tail, hy - (y1 - y0) * tail, (float) Math.sin(Math.PI * u)};
    }

    private void fallingStar(Graphics2D g, double t) {
        float[] f = fallingStar(t);
        if (f == null)
            return;
        float hx = x(f[0]);
        float hy = y(f[1]);
        float tx = x(f[2]);
        float ty = y(f[3]);
        double a = f[4];
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clip(new Rectangle2D.Float(x(SKY[0]), y(SKY[1]),
                (float) ((SKY[2] - SKY[0]) * s), (float) ((SKY[3] - SKY[1]) * s)));
        float scale = (float) Math.max(0.7, s);
        g2.setStroke(new BasicStroke(1.6f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setPaint(new GradientPaint(tx, ty, c(220, 235, 255, 0), hx, hy, c(245, 250, 255, a * 0.95)));
        g2.draw(new Line2D.Float(tx, ty, hx, hy));
        glow(g2, hx, hy, 5 * scale, new float[]{0, 1}, new Color[]{c(255, 255, 255, a), c(220, 235, 255, 0)});
        g2.dispose();
    }
}
