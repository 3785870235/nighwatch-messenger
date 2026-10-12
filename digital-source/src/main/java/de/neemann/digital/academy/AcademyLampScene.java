/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The lamp rooms, drawn in code in the palette of the painted archive: the duty room with the report
 * lamps of the posts A and B, the messenger post with the reminder lamps, the bell and the letter rack,
 * and the registry desk in the archive hall, where one acceptance lamp is fed by both posts and the
 * ledger lies open on the counter. It only paints. Whether a lamp glows is passed in by the caller;
 * the story backdrops always show the lamps dark, because that night the lines sent nothing.
 * After a repair the new spell glows in the lamp base while the lamp itself stays dark.
 */
final class AcademyLampScene {
    private static final String PREFIX = "lamp:";
    private static final String REPAIRED = ":repaired";
    private static final double ROOM_W = 1600;
    private static final double ROOM_H = 900;
    private static final double ROOM_FLOOR = 700;
    private static final double DEVICE_W = 1280;
    private static final double DEVICE_H = 660;
    private static final double DEVICE_FLOOR = 540;
    private static final double LANTERN_Y = 252;
    private static final double DEVICE_X = 640;
    private static final double DEVICE_Y = 74;
    private static final double DEVICE_K = 1.5;
    private static final double[] STAR_X = {470, 810};
    // a wide panel crops the device canvas to about y 100..560, so the high lamp may not hang above 100
    private static final double[] STAR_Y = {112, 168};
    private static final double STAR_K = 1.1;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private static final Color STONE = new Color(0x1F2E45);
    private static final Color MORTAR = new Color(0x0A111D);
    private static final Color WOOD_DARK = new Color(0x22160D);
    private static final Color BRASS = new Color(0xB08A48);
    private static final Color BRASS_DARK = new Color(0x5A4220);
    private static final Color BRASS_HI = new Color(0xE6C47E);
    private static final Color PAPER = new Color(0xE6D7B4);
    private static final Color INK = new Color(0x2E2826);
    private static final Color SEAL = new Color(0x9A3B2E);

    /** The lamp rooms; each tints the glass and the light of its lamps. */
    enum Kind {
        REPORT(0x1E2C44, 0x0D1524, 0x283550, 0x51627F, 0xFFF4D2, 0xF2C062, 0xA8722C),
        REMINDER(0x3A222C, 0x1A0E14, 0x40293A, 0x74505E, 0xFFE6DC, 0xF08C74, 0x9C3E32),
        REGISTRY(0x1C3A33, 0x0B1A16, 0x24443C, 0x4E806E, 0xEAFFF2, 0x86DDB0, 0x2E7D5C),
        STAR(0x222650, 0x0D0F26, 0x2C3164, 0x6A72B0, 0xF6F4FF, 0xAEB8FF, 0x4A55B5);

        private final Color glassTop;
        private final Color glassBottom;
        private final Color heart;
        private final Color heartLine;
        private final Color core;
        private final Color mid;
        private final Color edge;

        Kind(int glassTop, int glassBottom, int heart, int heartLine, int core, int mid, int edge) {
            this.glassTop = new Color(glassTop);
            this.glassBottom = new Color(glassBottom);
            this.heart = new Color(heart);
            this.heartLine = new Color(heartLine);
            this.core = new Color(core);
            this.mid = new Color(mid);
            this.edge = new Color(edge);
        }

        /**
         * @param key a scene key or a backdrop key
         * @return the room it names; the duty room if it names none
         */
        static Kind of(String key) {
            if (key == null)
                return REPORT;
            if (key.contains("reminder"))
                return REMINDER;
            if (key.contains("registry"))
                return REGISTRY;
            if (key.contains("star"))
                return STAR;
            return REPORT;
        }
    }

    private AcademyLampScene() {
    }

    /**
     * @param scene    the scene key of the level, e.g. {@code report_light}
     * @param repaired true after the new spell has passed
     * @return the backdrop key understood by {@link #paintBackdrop}
     */
    static String backdrop(String scene, boolean repaired) {
        return PREFIX + scene + (repaired ? REPAIRED : "");
    }

    /**
     * @param key a backdrop key or an art name
     * @return true if the key names a drawn lamp room
     */
    static boolean isBackdrop(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    /**
     * Paints a lamp room as a dialogue backdrop, covering the whole area.
     *
     * @param g       the graphics
     * @param w       the width
     * @param h       the height
     * @param key     the backdrop key
     * @param seconds the animation time
     */
    static void paintBackdrop(Graphics2D g, int w, int h, String key, double seconds) {
        Kind kind = Kind.of(key);
        boolean repaired = key.endsWith(REPAIRED);
        AffineTransform fit = fit(w, h, ROOM_W, ROOM_H);
        cached(g, key, w, h, c -> {
            c.transform(fit);
            room(c, kind);
        });
        Graphics2D a = (Graphics2D) g.create();
        a.transform(fit);
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        switch (kind) {
            case REMINDER:
                candle(a, 1236, 292, t);
                twinkle(a, new double[]{1350, 150, 1424, 226, 1360, 222, 1440, 150}, t);
                break;
            case REGISTRY:
                candle(a, 1524, 600, t);
                twinkle(a, new double[]{1294, 156, 1366, 148, 1300, 222, 1372, 214, 1350, 168}, t);
                break;
            case STAR:
                candle(a, 1508, 612, t);
                twinkle(a, new double[]{1366, 160, 1410, 140, 1500, 150, 1386, 236, 1500, 262, 1416, 192,
                    1370, 290, 1470, 296, 1396, 350, 1490, 350}, t);
                break;
            default:
                candle(a, 1478, 612, t);
                twinkle(a, new double[]{1352, 206, 1398, 300, 1490, 340, 1356, 404, 1470, 420}, t);
        }
        if (repaired) {
            rune(a, lanternX(kind, 0), lanternY(kind, 0), lanternK(kind), t);
            if (kind == Kind.REPORT || kind == Kind.STAR)
                rune(a, lanternX(kind, 1), lanternY(kind, 1), lanternK(kind), t + 1.3);
        }
        a.dispose();
    }

    /**
     * Paints the single lamp in front of the trial bench. The glow is the animated reading of the
     * caller; this method never decides it.
     *
     * @param g       the graphics
     * @param w       the width
     * @param h       the height
     * @param scene   the scene key of the level, e.g. {@code reminder_light} for a lamp with its bell
     * @param glow    0 for dark, 1 for fully lit
     * @param seconds the animation time
     * @param tag     the plate under the lamp, e.g. "岗位 A · 报告灯"
     */
    static void paintDevice(Graphics2D g, int w, int h, String scene, double glow, double seconds, String tag) {
        Kind kind = Kind.of(scene);
        AffineTransform fit = fit(w, h, DEVICE_W, DEVICE_H);
        cached(g, "device:" + kind + ":" + tag, w, h, c -> {
            c.transform(fit);
            device(c, kind, tag);
        });
        Graphics2D a = (Graphics2D) g.create();
        a.transform(fit);
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        double lit = Math.max(0, Math.min(1, glow));
        twinkle(a, new double[]{150, 160, 236, 214, 166, 300}, t);
        if (lit > 0.001) {
            pool(a, DEVICE_X, DEVICE_FLOOR + 52, 330, lit, kind);
            light(a, DEVICE_X, DEVICE_Y, DEVICE_K, lit, kind, t);
        }
        if (kind == Kind.REMINDER) {
            double swing = lit > 0.5 && AcademyAtmosphere.motion() ? Math.sin(t * 9) * 0.22 * lit : 0;
            bell(a, 900, 112, 1.05, swing);
            if (lit > 0.5)
                ring(a, 900, 160, lit, t);
        }
        a.dispose();
    }

    /**
     * Paints the star lamps in front of the trial bench: the high lamp hangs higher than the low one,
     * and both are fed from one sealed box that the lines of both posts run into. The glows are the
     * animated readings of the caller, one per lamp, the high lamp first.
     *
     * @param g       the graphics
     * @param w       the width
     * @param h       the height
     * @param scene   the scene key of the level
     * @param glows   per lamp 0 for dark, 1 for fully lit
     * @param seconds the animation time
     * @param tags    the plates under the lamps, e.g. "高星灯", "低星灯"
     */
    static void paintDevice(Graphics2D g, int w, int h, String scene, double[] glows, double seconds, String[] tags) {
        Kind kind = Kind.of(scene);
        AffineTransform fit = fit(w, h, DEVICE_W, DEVICE_H);
        cached(g, "device:" + kind + ":" + String.join("|", tags), w, h, c -> {
            c.transform(fit);
            starDevice(c, kind, tags);
        });
        Graphics2D a = (Graphics2D) g.create();
        a.transform(fit);
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        twinkle(a, new double[]{150, 160, 236, 214, 166, 300}, t);
        for (int i = 0; i < Math.min(glows.length, STAR_X.length); i++) {
            double lit = Math.max(0, Math.min(1, glows[i]));
            if (lit > 0.001) {
                pool(a, STAR_X[i], DEVICE_FLOOR + 52, 240, lit, kind);
                light(a, STAR_X[i], STAR_Y[i], STAR_K, lit, kind, t + i * 1.7);
            }
        }
        a.dispose();
    }

    private static double lanternX(Kind kind, int i) {
        switch (kind) {
            case REMINDER:
                return 715 + i * 210;
            case REGISTRY:
                return 780;
            case STAR:
                return 720 + i * 240;
            default:
                return 755 + i * 210;
        }
    }

    private static double lanternY(Kind kind, int i) {
        return kind == Kind.STAR ? 226 + i * 50 : LANTERN_Y;
    }

    /** The star lamps hang a little smaller, so that board, box and plates end above the cinematic's caption. */
    private static double lanternK(Kind kind) {
        return kind == Kind.STAR ? 0.86 : 1;
    }

    private static AffineTransform fit(int w, int h, double vw, double vh) {
        double s = Math.max(w / vw, h / vh);
        AffineTransform t = AffineTransform.getTranslateInstance((w - vw * s) / 2, (h - vh * s) / 2);
        t.scale(s, s);
        return t;
    }

    /** The static part of a scene is painted once per size into a bitmap at device resolution. */
    private static void cached(Graphics2D g, String key, int w, int h, Consumer<Graphics2D> painter) {
        double ds = Math.sqrt(Math.abs(g.getTransform().getDeterminant()));
        if (ds <= 0)
            ds = 1;
        String id = key + "@" + w + "x" + h + "@" + Math.round(ds * 100);
        BufferedImage image = CACHE.get(id);
        if (image == null) {
            image = new BufferedImage(Math.max(1, (int) Math.ceil(w * ds)), Math.max(1, (int) Math.ceil(h * ds)),
                    BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D c = image.createGraphics();
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            c.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            c.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            c.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            c.scale(ds, ds);
            painter.accept(c);
            c.dispose();
            if (CACHE.size() > 10)
                CACHE.clear();
            CACHE.put(id, image);
        }
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        d.scale(1 / ds, 1 / ds);
        d.drawImage(image, 0, 0, null);
        d.dispose();
    }

    private static void room(Graphics2D g, Kind kind) {
        wall(g, ROOM_W, ROOM_FLOOR, kind == Kind.REMINDER ? 23 : kind == Kind.REGISTRY ? 37 : kind == Kind.STAR ? 47 : 11);
        doorway(g, 150, 250, 230, ROOM_FLOOR);
        beam(g, ROOM_W);
        floor(g, ROOM_W, ROOM_FLOOR, ROOM_H);
        switch (kind) {
            case STAR:
                archWindow(g, 1340, 120, 180, 430);
                shaft(g, new double[]{1346, 550, 1520, 550, 1340, ROOM_H, 1070, ROOM_H});
                board(g, 580, 150, 520, 430, "星灯");
                starLamps(g);
                towerLog(g, 1140, 254);
                desk(g, 1100, 612, 470);
                break;
            case REMINDER:
                oculus(g, 1390, 186, 78);
                shaft(g, new double[]{1330, 220, 1450, 220, 1180, ROOM_H, 930, ROOM_H});
                rack(g, 1170, 292, 390, ROOM_FLOOR + 12);
                double bx = lanternX(kind, 1) + 175;
                cord(g, bx, 214, lanternX(kind, 1) + 112, 190);
                bracket(g, bx, 172);
                bell(g, bx, 186, 0.85, 0);
                board(g, 600, 176, 440, 394, "提醒灯");
                pair(g, kind, 176);
                break;
            case REGISTRY:
                oculus(g, 1330, 186, 70);
                shaft(g, new double[]{1270, 216, 1390, 216, 1180, ROOM_H, 960, ROOM_H});
                board(g, 560, 176, 440, 394, "受理灯");
                accept(g, 176);
                notice(g, 1036, 290);
                counter(g, 1050, 600, 530);
                ledger(g, 1206, 600, 1);
                stamp(g, 1404, 600);
                break;
            default:
                archWindow(g, 1330, 130, 190, 420);
                shaft(g, new double[]{1336, 540, 1514, 540, 1330, ROOM_H, 1060, ROOM_H});
                board(g, 640, 176, 440, 394, "报告灯");
                pair(g, kind, 176);
                record(g, 1118, 254);
                desk(g, 1080, 612, 510);
        }
        vignette(g, ROOM_W, ROOM_H);
    }

    private static void device(Graphics2D g, Kind kind, String tag) {
        wall(g, DEVICE_W, DEVICE_FLOOR, kind == Kind.REMINDER ? 31 : kind == Kind.REGISTRY ? 43 : 17);
        beam(g, DEVICE_W);
        floor(g, DEVICE_W, DEVICE_FLOOR, DEVICE_H);
        archWindow(g, 118, 100, 160, 320);
        chain(g, DEVICE_X, 44, DEVICE_Y + 4);
        double bottom = DEVICE_Y + 222 * DEVICE_K;
        if (kind == Kind.REGISTRY) {
            double feed = bottom + 82;
            conduit(g, DEVICE_X, bottom - 4, feed + 5, 11);
            for (int i = 0; i < 2; i++)
                conduit(g, DEVICE_X + (i == 0 ? -130 : 130), feed - 5, DEVICE_FLOOR + 6, 11);
            pipe(g, DEVICE_X - 130, DEVICE_X + 130, feed, 11);
            plate(g, DEVICE_X - 130, feed + 14, "岗位 A", 15);
            plate(g, DEVICE_X + 130, feed + 14, "岗位 B", 15);
            lectern(g, 1010, DEVICE_FLOOR + 30);
        } else
            conduit(g, DEVICE_X, bottom - 4, DEVICE_FLOOR + 6, 11);
        lantern(g, DEVICE_X, DEVICE_Y, DEVICE_K, kind);
        plate(g, DEVICE_X, bottom + 14, tag, 19);
        if (kind == Kind.REMINDER) {
            cord(g, 900, 150, DEVICE_X + 80, DEVICE_Y + 120);
            bracket(g, 900, 98);
        }
        vignette(g, DEVICE_W, DEVICE_H);
    }

    private static void wall(Graphics2D g, double w, double floor, long seed) {
        Graphics2D s = (Graphics2D) g.create();
        s.clip(new Rectangle2D.Double(0, 0, w, floor));
        s.setColor(MORTAR);
        s.fill(new Rectangle2D.Double(0, 0, w, floor));
        Random r = new Random(seed);
        double rowH = 58;
        for (double y = 40; y < floor; y += rowH) {
            double x = -r.nextDouble() * 120;
            while (x < w) {
                double bw = 130 + r.nextDouble() * 90;
                Color c = shade(STONE, r.nextInt(17) - 8);
                s.setPaint(new GradientPaint(0, (float) y, shade(c, 6), 0, (float) (y + rowH), shade(c, -6)));
                s.fill(new RoundRectangle2D.Double(x + 2, y + 2, bw - 4, rowH - 4, 7, 7));
                s.setStroke(new BasicStroke(1.4f));
                s.setColor(new Color(255, 255, 255, 13));
                s.draw(new Line2D.Double(x + 7, y + 4, x + bw - 7, y + 4));
                s.setColor(new Color(0, 0, 0, 46));
                s.draw(new Line2D.Double(x + 7, y + rowH - 4, x + bw - 7, y + rowH - 4));
                if (r.nextInt(5) == 0) {
                    double cx = x + 20 + r.nextDouble() * (bw - 40);
                    s.setColor(new Color(6, 10, 18, 70));
                    s.draw(new Line2D.Double(cx, y + 10, cx + r.nextDouble() * 14 - 7, y + 26));
                }
                x += bw;
            }
        }
        s.setPaint(new GradientPaint(0, 40, new Color(5, 9, 17, 175), 0, 330, new Color(5, 9, 17, 0)));
        s.fill(new Rectangle2D.Double(0, 40, w, 290));
        s.setPaint(new GradientPaint(0, (float) (floor - 120), new Color(5, 9, 17, 0), 0, (float) floor,
                new Color(5, 9, 17, 120)));
        s.fill(new Rectangle2D.Double(0, floor - 120, w, 120));
        s.dispose();
    }

    private static void beam(Graphics2D g, double w) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B1D13), 0, 46, new Color(0x170F09)));
        g.fill(new Rectangle2D.Double(0, 0, w, 46));
        Random r = new Random(7);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 14; i++) {
            g.setColor(new Color(120, 86, 52, 16 + r.nextInt(14)));
            double y = 6 + r.nextDouble() * 34;
            double x = r.nextDouble() * w;
            g.draw(new Line2D.Double(x, y, x + 120 + r.nextDouble() * 260, y + r.nextDouble() * 3 - 1.5));
        }
        g.setColor(new Color(0x5C4129));
        g.draw(new Line2D.Double(0, 45, w, 45));
        g.setPaint(new GradientPaint(0, 46, new Color(0, 0, 0, 120), 0, 64, new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(0, 46, w, 18));
    }

    private static void floor(Graphics2D g, double w, double top, double bottom) {
        g.setPaint(new GradientPaint(0, (float) top, new Color(0x2F2116), 0, (float) bottom, new Color(0x120B07)));
        g.fill(new Rectangle2D.Double(0, top, w, bottom - top));
        double vx = w / 2;
        double vy = top - 560;
        double grow = (bottom - vy) / (top - vy);
        g.setStroke(new BasicStroke(1.6f));
        for (int i = -14; i <= (int) (w / 70) + 14; i++) {
            double a0 = i * 70;
            double a1 = a0 + 70;
            double b0 = vx + (a0 - vx) * grow;
            double b1 = vx + (a1 - vx) * grow;
            if (i % 2 == 0) {
                Path2D board = new Path2D.Double();
                board.moveTo(a0, top);
                board.lineTo(a1, top);
                board.lineTo(b1, bottom);
                board.lineTo(b0, bottom);
                board.closePath();
                g.setColor(new Color(255, 226, 180, 7));
                g.fill(board);
            }
            g.setColor(new Color(8, 5, 3, 120));
            g.draw(new Line2D.Double(a0, top, b0, bottom));
        }
        g.setColor(new Color(8, 5, 3, 60));
        double y = top;
        for (double step = 26; y + step < bottom; step *= 1.45) {
            y += step;
            g.draw(new Line2D.Double(0, y, w, y));
        }
        g.setPaint(new GradientPaint(0, (float) (top - 14), new Color(0x2C1D12), 0, (float) (top + 8), WOOD_DARK));
        g.fill(new Rectangle2D.Double(0, top - 14, w, 22));
        g.setColor(new Color(0x5A3F26));
        g.draw(new Line2D.Double(0, top - 14, w, top - 14));
        g.setPaint(new GradientPaint(0, (float) (top + 8), new Color(0, 0, 0, 120), 0, (float) (top + 70),
                new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(0, top + 8, w, 62));
    }

    private static Shape arch(double x, double y, double w, double h) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + h);
        p.lineTo(x, y + w / 2);
        p.append(new Arc2D.Double(x, y, w, w, 180, -180, Arc2D.OPEN), true);
        p.lineTo(x + w, y + h);
        p.closePath();
        return p;
    }

    private static void doorway(Graphics2D g, double x, double y, double w, double floor) {
        g.setColor(new Color(0x2A3A52));
        g.fill(arch(x - 18, y - 18, w + 36, floor - y + 18));
        g.setColor(new Color(0x0B1320));
        g.setStroke(new BasicStroke(2f));
        g.draw(arch(x - 18, y - 18, w + 36, floor - y + 18));
        g.setPaint(new GradientPaint(0, (float) y, new Color(0x0B121F), 0, (float) floor, new Color(0x05080F)));
        g.fill(arch(x, y, w, floor - y));
        g.setPaint(new GradientPaint(0, (float) (floor - 40), new Color(240, 177, 75, 0), 0, (float) floor,
                new Color(240, 177, 75, 34)));
        g.fill(new Rectangle2D.Double(x, floor - 40, w, 40));
    }

    private static void archWindow(Graphics2D g, double x, double y, double w, double h) {
        g.setColor(new Color(0x2B3B54));
        g.fill(arch(x - 16, y - 16, w + 32, h + 22));
        g.setColor(new Color(0x0B1320));
        g.setStroke(new BasicStroke(2f));
        g.draw(arch(x - 16, y - 16, w + 32, h + 22));
        Shape glass = arch(x, y, w, h);
        Graphics2D s = (Graphics2D) g.create();
        s.clip(glass);
        s.setPaint(new GradientPaint(0, (float) y, new Color(0x2E4E78), 0, (float) (y + h), new Color(0x111F35)));
        s.fill(glass);
        moon(s, x + w * 0.68, y + 92, 26);
        stars(s, x, y, w, h * 0.7, 41);
        Path2D roofs = new Path2D.Double();
        roofs.moveTo(x, y + h);
        roofs.lineTo(x, y + h * 0.80);
        roofs.lineTo(x + w * 0.18, y + h * 0.80);
        roofs.lineTo(x + w * 0.24, y + h * 0.62);
        roofs.lineTo(x + w * 0.30, y + h * 0.80);
        roofs.lineTo(x + w * 0.52, y + h * 0.76);
        roofs.lineTo(x + w * 0.52, y + h * 0.70);
        roofs.lineTo(x + w * 0.60, y + h * 0.70);
        roofs.lineTo(x + w * 0.60, y + h * 0.78);
        roofs.lineTo(x + w, y + h * 0.83);
        roofs.lineTo(x + w, y + h);
        roofs.closePath();
        s.setColor(new Color(0x0A1424));
        s.fill(roofs);
        s.setColor(new Color(240, 196, 110, 150));
        s.fill(new Rectangle2D.Double(x + w * 0.545, y + h * 0.73, 6, 8));
        s.setColor(new Color(255, 255, 255, 18));
        Path2D sheen = new Path2D.Double();
        sheen.moveTo(x + w * 0.1, y + h);
        sheen.lineTo(x + w * 0.3, y + h);
        sheen.lineTo(x + w * 0.9, y + h * 0.1);
        sheen.lineTo(x + w * 0.7, y + h * 0.1);
        sheen.closePath();
        s.fill(sheen);
        s.dispose();
        g.setColor(new Color(0x0D1522));
        g.setStroke(new BasicStroke(7f));
        g.draw(new Line2D.Double(x + w / 2, y, x + w / 2, y + h));
        g.setStroke(new BasicStroke(6f));
        g.draw(new Line2D.Double(x, y + h * 0.46, x + w, y + h * 0.46));
        g.draw(new Line2D.Double(x, y + h * 0.72, x + w, y + h * 0.72));
        g.setStroke(new BasicStroke(5f));
        g.draw(new Arc2D.Double(x + 6, y + 6, w - 12, w - 12, 0, 180, Arc2D.OPEN));
        g.setPaint(new GradientPaint(0, (float) (y + h + 6), new Color(0x3A4C66), 0, (float) (y + h + 24),
                new Color(0x1C2A3E)));
        g.fill(new Rectangle2D.Double(x - 26, y + h + 6, w + 52, 18));
        g.setColor(new Color(255, 255, 255, 30));
        g.draw(new Line2D.Double(x - 26, y + h + 7, x + w + 26, y + h + 7));
    }

    private static void oculus(Graphics2D g, double cx, double cy, double r) {
        g.setColor(new Color(0x2B3B54));
        g.fill(new Ellipse2D.Double(cx - r - 18, cy - r - 18, (r + 18) * 2, (r + 18) * 2));
        g.setColor(new Color(0x0B1320));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(cx - r - 18, cy - r - 18, (r + 18) * 2, (r + 18) * 2));
        Shape glass = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        Graphics2D s = (Graphics2D) g.create();
        s.clip(glass);
        s.setPaint(new GradientPaint(0, (float) (cy - r), new Color(0x2E4E78), 0, (float) (cy + r),
                new Color(0x142540)));
        s.fill(glass);
        moon(s, cx + r * 0.32, cy - r * 0.18, 22);
        stars(s, cx - r, cy - r, r * 2, r * 2, 53);
        s.dispose();
        g.setColor(new Color(0x0D1522));
        g.setStroke(new BasicStroke(6f));
        g.draw(new Line2D.Double(cx - r, cy, cx + r, cy));
        g.draw(new Line2D.Double(cx, cy - r, cx, cy + r));
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(cx - r * 0.45, cy - r * 0.45, r * 0.9, r * 0.9));
    }

    private static void moon(Graphics2D g, double x, double y, double r) {
        float halo = (float) (r * 4.2);
        g.setPaint(new RadialGradientPaint((float) x, (float) y, halo, new float[]{0, 0.3f, 1},
                new Color[]{new Color(232, 240, 236, 110), new Color(150, 200, 220, 40), new Color(150, 200, 220, 0)}));
        g.fill(new Ellipse2D.Double(x - halo, y - halo, halo * 2, halo * 2));
        g.setPaint(new RadialGradientPaint((float) (x - r * 0.3), (float) (y - r * 0.3), (float) (r * 1.3),
                new float[]{0, 1}, new Color[]{new Color(0xFBF4E2), new Color(0xD9CDAE)}));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g.setColor(new Color(170, 156, 126, 60));
        g.fill(new Ellipse2D.Double(x - r * 0.3, y - r * 0.1, r * 0.5, r * 0.42));
        g.fill(new Ellipse2D.Double(x + r * 0.2, y + r * 0.35, r * 0.32, r * 0.28));
    }

    private static void stars(Graphics2D g, double x, double y, double w, double h, long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < 18; i++) {
            g.setColor(new Color(230, 238, 245, 60 + r.nextInt(90)));
            double sx = x + r.nextDouble() * w;
            double sy = y + r.nextDouble() * h;
            double size = r.nextInt(4) == 0 ? 2.4 : 1.4;
            g.fill(new Ellipse2D.Double(sx, sy, size, size));
        }
    }

    private static void twinkle(Graphics2D g, double[] points, double t) {
        for (int i = 0; i + 1 < points.length; i += 2) {
            int a = (int) (40 + 140 * (0.5 + 0.5 * Math.sin(t * (1.1 + i * 0.13) + i)));
            g.setColor(new Color(236, 244, 250, a));
            g.fill(new Ellipse2D.Double(points[i] - 1.4, points[i + 1] - 1.4, 2.8, 2.8));
        }
    }

    private static void shaft(Graphics2D g, double[] quad) {
        Path2D p = new Path2D.Double();
        p.moveTo(quad[0], quad[1]);
        for (int i = 2; i + 1 < quad.length; i += 2)
            p.lineTo(quad[i], quad[i + 1]);
        p.closePath();
        g.setPaint(new GradientPaint(0, (float) quad[1], new Color(170, 214, 232, 30), 0, (float) quad[5],
                new Color(170, 214, 232, 0)));
        g.fill(p);
    }

    private static void vignette(Graphics2D g, double w, double h) {
        g.setPaint(new RadialGradientPaint((float) (w * 0.58), (float) (h * 0.44), (float) (w * 0.72),
                new float[]{0, 0.6f, 1}, new Color[]{new Color(4, 8, 16, 0), new Color(4, 8, 16, 40),
                new Color(4, 8, 16, 170)}));
        g.fill(new Rectangle2D.Double(0, 0, w, h));
    }

    private static void board(Graphics2D g, double x, double y, double w, double h, String title) {
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(0, 0, 0, 22));
            g.fill(new RoundRectangle2D.Double(x + 4 + i * 2, y + 8 + i * 2, w, h, 16, 16));
        }
        g.setPaint(new GradientPaint(0, (float) y, new Color(0x47321D), 0, (float) (y + h), new Color(0x2A1B10)));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 16, 16));
        g.setPaint(new GradientPaint(0, (float) (y + 70), new Color(0x2E1F13), 0, (float) (y + h), new Color(0x22170E)));
        g.fill(new RoundRectangle2D.Double(x + 20, y + 70, w - 40, h - 90, 10, 10));
        Random r = new Random(title.hashCode());
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 26; i++) {
            g.setColor(new Color(150, 108, 66, 12 + r.nextInt(14)));
            double gy = y + 6 + r.nextDouble() * (h - 12);
            double gx = x + r.nextDouble() * w * 0.6;
            Path2D grain = new Path2D.Double();
            grain.moveTo(gx, gy);
            grain.curveTo(gx + 60, gy + r.nextDouble() * 6 - 3, gx + 120, gy + r.nextDouble() * 6 - 3,
                    Math.min(x + w - 6, gx + 90 + r.nextDouble() * 160), gy + r.nextDouble() * 4 - 2);
            g.draw(grain);
        }
        g.setColor(new Color(217, 175, 96, 110));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(x + 26, y + 76, w - 52, h - 102, 8, 8));
        g.setColor(new Color(0x170E07));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 16, 16));
        g.setColor(new Color(255, 222, 170, 40));
        g.draw(new Line2D.Double(x + 12, y + 2, x + w - 12, y + 2));
        for (double[] p : new double[][]{{x + 12, y + 12}, {x + w - 12, y + 12}, {x + 12, y + h - 12},
                {x + w - 12, y + h - 12}})
            rivet(g, p[0], p[1], 5);
        plate(g, x + w / 2, y + 14, title, 30);
    }

    /** One lamp per post on the board whose top is at y, each on its own line. */
    private static void pair(Graphics2D g, Kind kind, double y) {
        for (int i = 0; i < 2; i++) {
            double lx = lanternX(kind, i);
            conduit(g, lx, LANTERN_Y + 214, ROOM_FLOOR + 6, 10);
            hook(g, lx, y + 72);
            lantern(g, lx, LANTERN_Y, 1, kind);
            plate(g, lx, LANTERN_Y + 232, i == 0 ? "岗位 A" : "岗位 B", 17);
        }
    }

    /** The acceptance lamp on the board whose top is at y: the lines of both posts end in its base. */
    private static void accept(Graphics2D g, double y) {
        double lx = lanternX(Kind.REGISTRY, 0);
        double feed = LANTERN_Y + 262;
        for (int i = 0; i < 2; i++)
            conduit(g, lx + (i == 0 ? -120 : 120), feed - 5, ROOM_FLOOR + 6, 10);
        pipe(g, lx - 120, lx + 120, feed, 10);
        conduit(g, lx, LANTERN_Y + 214, feed + 5, 10);
        hook(g, lx, y + 72);
        lantern(g, lx, LANTERN_Y, 1, Kind.REGISTRY);
        plate(g, lx - 120, feed + 16, "岗位 A", 17);
        plate(g, lx + 120, feed + 16, "岗位 B", 17);
    }

    /**
     * The two star lamps on the board, the high lamp hung higher. Neither lamp has a line of its own:
     * both rise from one sealed box, and the lines of both posts run into that box.
     */
    private static void starLamps(Graphics2D g) {
        double boxY = 510;
        double k = lanternK(Kind.STAR);
        for (int i = 0; i < 2; i++) {
            double lx = lanternX(Kind.STAR, i);
            double ly = lanternY(Kind.STAR, i);
            conduit(g, lx, ly + 214 * k, boxY + 6, 10);
            hook(g, lx, 222);
            chain(g, lx, 222, ly + 4);
            lantern(g, lx, ly, k, Kind.STAR);
            plate(g, lx, ly + 232 * k, i == 0 ? "高星灯" : "低星灯", 17);
        }
        for (int i = 0; i < 2; i++)
            conduit(g, i == 0 ? 780 : 900, boxY + 34, ROOM_FLOOR + 6, 10);
        starBox(g, 840, boxY, 320, 38);
        plate(g, 780, 590, "岗位 A", 17);
        plate(g, 900, 590, "岗位 B", 17);
    }

    /** The sealed brass box in which the lines of the posts meet; nobody can see inside. */
    private static void starBox(Graphics2D g, double cx, double y, double w, double h) {
        double x = cx - w / 2;
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 5, w, h, 8, 8));
        g.setPaint(new GradientPaint(0, (float) y, new Color(0x9C7A40), 0, (float) (y + h), new Color(0x4A3519)));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 8, 8));
        g.setColor(new Color(0x2A1D0D));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 8, 8));
        g.setColor(new Color(255, 236, 190, 70));
        g.draw(new RoundRectangle2D.Double(x + 5, y + 5, w - 10, h - 10, 5, 5));
        for (double[] p : new double[][]{{x + 11, y + h / 2}, {x + w - 11, y + h / 2}})
            rivet(g, p[0], p[1], 4);
        double sy = y + h / 2;
        Path2D star = new Path2D.Double();
        star.moveTo(cx, sy - 13);
        star.lineTo(cx + 3.5, sy - 3.5);
        star.lineTo(cx + 13, sy);
        star.lineTo(cx + 3.5, sy + 3.5);
        star.lineTo(cx, sy + 13);
        star.lineTo(cx - 3.5, sy + 3.5);
        star.lineTo(cx - 13, sy);
        star.lineTo(cx - 3.5, sy - 3.5);
        star.closePath();
        g.setColor(new Color(0x3A2810));
        g.fill(star);
        g.setColor(new Color(255, 236, 190, 90));
        g.setStroke(new BasicStroke(1f));
        g.draw(star);
        g.setColor(new Color(42, 29, 13, 150));
        g.setStroke(new BasicStroke(2f));
        for (int side = -1; side <= 1; side += 2)
            for (int i = 0; i < 4; i++) {
                double vx = cx + side * (40 + i * 18);
                g.draw(new Line2D.Double(vx, sy - 7, vx, sy + 7));
            }
    }

    /** The tower log pinned to the wall: one hand wrote down what the lamps showed at midnight. */
    private static void towerLog(Graphics2D g, double x, double y) {
        double w = 160;
        double h = 214;
        Graphics2D p = (Graphics2D) g.create();
        p.rotate(Math.toRadians(1.4), x + w / 2, y);
        for (int i = 0; i < 4; i++) {
            p.setColor(new Color(0, 0, 0, 22));
            p.fill(new Rectangle2D.Double(x + 3 + i, y + 5 + i, w, h));
        }
        p.setPaint(new GradientPaint(0, (float) y, PAPER, 0, (float) (y + h), new Color(0xD4C196)));
        p.fill(new Rectangle2D.Double(x, y, w, h));
        rivet(p, x + w / 2, y + 10, 5);
        p.setColor(INK);
        p.setFont(AcademyTheme.script(25));
        FontMetrics fm = p.getFontMetrics();
        String title = "塔楼日志";
        p.drawString(title, (float) (x + (w - fm.stringWidth(title)) / 2), (float) (y + 48));
        p.setFont(AcademyTheme.font(Font.PLAIN, 13));
        fm = p.getFontMetrics();
        String sub = "零点 · 星灯";
        p.setColor(new Color(0x6B5A44));
        p.drawString(sub, (float) (x + (w - fm.stringWidth(sub)) / 2), (float) (y + 70));
        p.setColor(new Color(0xB5965F));
        p.draw(new Line2D.Double(x + 14, y + 80, x + w - 14, y + 80));
        p.setFont(AcademyTheme.script(19));
        p.setColor(INK);
        p.drawString("亮了一盏", (float) (x + 18), (float) (y + 112));
        p.setColor(SEAL);
        p.drawString("记 一人在岗", (float) (x + 18), (float) (y + 142));
        Random r = new Random(43);
        p.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int row = 0; row < 2; row++) {
            double ly = y + 170 + row * 18;
            double end = x + w - 20 - r.nextInt(40);
            p.setColor(new Color(70, 58, 46, 90));
            for (double lx = x + 18; lx < end; lx += 7 + r.nextInt(4))
                p.draw(new Line2D.Double(lx, ly, lx + 4, ly - 2));
        }
        p.dispose();
    }

    /** The trial bench of the star tower: the same two lamps and the same sealed box, close up. */
    private static void starDevice(Graphics2D g, Kind kind, String[] tags) {
        wall(g, DEVICE_W, DEVICE_FLOOR, 53);
        beam(g, DEVICE_W);
        floor(g, DEVICE_W, DEVICE_FLOOR, DEVICE_H);
        archWindow(g, 118, 100, 160, 320);
        double boxY = 462;
        for (int i = 0; i < STAR_X.length; i++) {
            double bottom = STAR_Y[i] + 222 * STAR_K;
            chain(g, STAR_X[i], 44, STAR_Y[i] + 4);
            conduit(g, STAR_X[i], bottom - 4, boxY + 6, 11);
        }
        for (int i = 0; i < 2; i++)
            conduit(g, i == 0 ? 560 : 720, boxY + 38, DEVICE_FLOOR + 6, 11);
        starBox(g, DEVICE_X, boxY, 480, 44);
        for (int i = 0; i < STAR_X.length; i++) {
            lantern(g, STAR_X[i], STAR_Y[i], STAR_K, kind);
            if (i < tags.length)
                plate(g, STAR_X[i], STAR_Y[i] + 222 * STAR_K + 12, tags[i], 19);
        }
        plate(g, 560, boxY + 52, "岗位 A", 15);
        plate(g, 720, boxY + 52, "岗位 B", 15);
        vignette(g, DEVICE_W, DEVICE_H);
    }

    private static void rivet(Graphics2D g, double x, double y, double r) {
        g.setPaint(new RadialGradientPaint((float) (x - r * 0.3), (float) (y - r * 0.3), (float) r,
                new float[]{0, 1}, new Color[]{BRASS_HI, BRASS_DARK}));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    /** A brass plate with engraved text, centred on x, with its top at y. */
    private static void plate(Graphics2D g, double cx, double y, String text, float size) {
        Font font = size >= 24 ? AcademyTheme.script(size) : AcademyTheme.font(Font.BOLD, size);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        double pw = fm.stringWidth(text) + size * 2.2;
        double ph = size * 1.55;
        double x = cx - pw / 2;
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 4, pw, ph, 8, 8));
        g.setPaint(new GradientPaint(0, (float) y, new Color(0xD3AE66), 0, (float) (y + ph), new Color(0x7E5E2C)));
        g.fill(new RoundRectangle2D.Double(x, y, pw, ph, 8, 8));
        g.setColor(new Color(0x3A2810));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(x, y, pw, ph, 8, 8));
        g.setColor(new Color(255, 236, 190, 90));
        g.draw(new RoundRectangle2D.Double(x + 4, y + 4, pw - 8, ph - 8, 5, 5));
        float tx = (float) (cx - fm.stringWidth(text) / 2.0);
        float ty = (float) (y + (ph + fm.getAscent() - fm.getDescent()) / 2);
        g.setColor(new Color(255, 236, 190, 120));
        g.drawString(text, tx, ty + 1.2f);
        g.setColor(new Color(0x2C1C0A));
        g.drawString(text, tx, ty);
    }

    private static void hook(Graphics2D g, double x, double y) {
        g.setColor(BRASS_DARK);
        g.fill(new Rectangle2D.Double(x - 9, y - 4, 18, 6));
        g.setColor(BRASS);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Line2D.Double(x, y, x, y + 4));
    }

    private static void chain(Graphics2D g, double x, double from, double to) {
        g.setStroke(new BasicStroke(2.2f));
        for (double y = from; y < to; y += 10) {
            g.setColor(BRASS_DARK);
            g.draw(new Ellipse2D.Double(x - 3.5, y, 7, 11));
            g.setColor(new Color(230, 196, 126, 70));
            g.draw(new Line2D.Double(x - 2, y + 2, x - 2, y + 6));
        }
    }

    /** The line runs inside a closed brass conduit; nobody can see whether it carries a message. */
    private static void conduit(Graphics2D g, double x, double from, double to, double width) {
        g.setPaint(new LinearGradientPaint((float) (x - width / 2), 0, (float) (x + width / 2), 0,
                new float[]{0, 0.35f, 1}, new Color[]{BRASS_DARK, new Color(0x9C7A40), new Color(0x3A2A14)}));
        g.fill(new Rectangle2D.Double(x - width / 2, from, width, to - from));
        for (double y = from + 40; y < to - 20; y += 92) {
            g.setColor(new Color(0x2E2110));
            g.fill(new Rectangle2D.Double(x - width / 2 - 3, y, width + 6, 7));
            g.setColor(new Color(230, 196, 126, 80));
            g.draw(new Line2D.Double(x - width / 2 - 3, y, x + width / 2 + 3, y));
        }
        g.setColor(new Color(0x2E2110));
        g.fill(new Rectangle2D.Double(x - width / 2 - 6, to - 8, width + 12, 8));
    }

    /** A level run of conduit from x0 to x1, with fittings at both elbows and where the feed rises. */
    private static void pipe(Graphics2D g, double x0, double x1, double y, double width) {
        g.setPaint(new LinearGradientPaint(0, (float) (y - width / 2), 0, (float) (y + width / 2),
                new float[]{0, 0.35f, 1}, new Color[]{BRASS_DARK, new Color(0x9C7A40), new Color(0x3A2A14)}));
        g.fill(new Rectangle2D.Double(x0 - width / 2, y - width / 2, x1 - x0 + width, width));
        for (double x : new double[]{x0, (x0 + x1) / 2, x1}) {
            g.setColor(new Color(0x2E2110));
            g.fill(new RoundRectangle2D.Double(x - width / 2 - 4, y - width / 2 - 4, width + 8, width + 8, 4, 4));
            g.setColor(new Color(230, 196, 126, 80));
            g.draw(new Line2D.Double(x - width / 2 - 4, y - width / 2 - 4, x + width / 2 + 4, y - width / 2 - 4));
        }
    }

    private static Area panes() {
        Area a = new Area(new Rectangle2D.Double(-45, 88, 12, 80));
        a.add(new Area(new Rectangle2D.Double(-27, 88, 54, 80)));
        a.add(new Area(new Rectangle2D.Double(33, 88, 12, 80)));
        return a;
    }

    private static Path2D diamond(double x, double y, double rx, double ry) {
        Path2D d = new Path2D.Double();
        d.moveTo(x, y - ry);
        d.lineTo(x + rx, y);
        d.lineTo(x, y + ry);
        d.lineTo(x - rx, y);
        d.closePath();
        return d;
    }

    /** The unlit brass lantern; local units, about 222 high at scale 1, the hanger ring at the top. */
    private static void lantern(Graphics2D g, double x, double y, double k, Kind kind) {
        Graphics2D l = (Graphics2D) g.create();
        l.translate(x, y);
        l.scale(k, k);
        l.setStroke(new BasicStroke(3f));
        l.setColor(BRASS_DARK);
        l.draw(new Ellipse2D.Double(-7, 0, 14, 16));
        l.setPaint(new RadialGradientPaint(-2, 21, 9, new float[]{0, 1}, new Color[]{BRASS_HI, BRASS_DARK}));
        l.fill(new Ellipse2D.Double(-7, 15, 14, 14));
        Path2D cap = new Path2D.Double();
        cap.moveTo(-13, 29);
        cap.lineTo(13, 29);
        cap.quadTo(36, 56, 64, 68);
        cap.lineTo(54, 78);
        cap.lineTo(-54, 78);
        cap.lineTo(-64, 68);
        cap.quadTo(-36, 56, -13, 29);
        cap.closePath();
        l.setPaint(new GradientPaint(0, 29, new Color(0x93703A), 0, 78, new Color(0x45311A)));
        l.fill(cap);
        l.setColor(new Color(0x2A1D0D));
        l.setStroke(new BasicStroke(2f));
        l.draw(cap);
        Path2D eave = new Path2D.Double();
        eave.moveTo(-13, 31);
        eave.quadTo(-36, 57, -61, 68);
        l.setColor(new Color(230, 196, 126, 150));
        l.setStroke(new BasicStroke(1.6f));
        l.draw(eave);
        l.setColor(new Color(42, 29, 13, 150));
        l.draw(new Line2D.Double(-6, 33, -24, 76));
        l.draw(new Line2D.Double(6, 33, 24, 76));
        l.setPaint(new GradientPaint(0, 74, BRASS, 0, 86, BRASS_DARK));
        l.fill(new Rectangle2D.Double(-55, 76, 110, 10));
        Area glass = panes();
        l.setPaint(new GradientPaint(0, 88, kind.glassTop, 0, 168, kind.glassBottom));
        l.fill(glass);
        Path2D heart = diamond(0, 128, 9, 19);
        l.setColor(kind.heart);
        l.fill(heart);
        l.setColor(kind.heartLine);
        l.setStroke(new BasicStroke(1.4f));
        l.draw(heart);
        Path2D sheen = new Path2D.Double();
        sheen.moveTo(-24, 166);
        sheen.lineTo(-14, 166);
        sheen.lineTo(4, 90);
        sheen.lineTo(-6, 90);
        sheen.closePath();
        l.setColor(new Color(220, 236, 250, 24));
        l.fill(sheen);
        l.setPaint(new GradientPaint(-50, 0, BRASS_DARK, 50, 0, BRASS));
        for (double[] p : new double[][]{{-50, 5}, {-33, 6}, {27, 6}, {45, 5}})
            l.fill(new Rectangle2D.Double(p[0], 84, p[1], 86));
        l.setPaint(new GradientPaint(0, 166, BRASS, 0, 178, BRASS_DARK));
        l.fill(new Rectangle2D.Double(-53, 166, 106, 10));
        Path2D base = new Path2D.Double();
        base.moveTo(-52, 176);
        base.lineTo(52, 176);
        base.lineTo(38, 204);
        base.lineTo(-38, 204);
        base.closePath();
        l.setPaint(new GradientPaint(0, 176, new Color(0x9A773E), 0, 204, new Color(0x4A3519)));
        l.fill(base);
        l.setColor(new Color(0x2A1D0D));
        l.setStroke(new BasicStroke(1.6f));
        l.draw(base);
        l.draw(new Ellipse2D.Double(-9, 181, 18, 18));
        l.setColor(new Color(42, 29, 13, 160));
        l.draw(new Line2D.Double(-4, 190, 4, 190));
        l.setPaint(new GradientPaint(-6, 0, BRASS_DARK, 6, 0, BRASS));
        l.fill(new Rectangle2D.Double(-6, 204, 12, 14));
        l.dispose();
    }

    /** The lit glass and its halo, laid over the unlit lantern with the strength of the glow. */
    private static void light(Graphics2D g, double x, double y, double k, double glow, Kind kind, double t) {
        Graphics2D l = (Graphics2D) g.create();
        l.translate(x, y);
        l.scale(k, k);
        float a = (float) glow;
        float flicker = (float) (0.93 + 0.07 * Math.sin(t * 7.3) * Math.sin(t * 2.3 + 1));
        Color core = kind.core;
        Color mid = kind.mid;
        Color edge = kind.edge;
        float halo = 250;
        l.setPaint(new RadialGradientPaint(0, 128, halo, new float[]{0, 0.3f, 1}, new Color[]{
            alpha(mid, (int) (130 * a * flicker)), alpha(mid, (int) (42 * a)), alpha(mid, 0)}));
        l.fill(new Ellipse2D.Double(-halo, 128 - halo, halo * 2, halo * 2));
        l.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        l.setPaint(new RadialGradientPaint(0, 128, 62, new float[]{0, 0.45f, 1}, new Color[]{core, mid, edge}));
        l.fill(panes());
        l.setColor(Color.WHITE);
        l.fill(diamond(0, 128, 8, 17));
        l.setColor(alpha(core, 200));
        l.setStroke(new BasicStroke(1.2f));
        l.draw(diamond(0, 128, 12, 24));
        l.dispose();
    }

    /** Warm light falling on the floor below a lit lamp. */
    private static void pool(Graphics2D g, double x, double y, double r, double glow, Kind kind) {
        Graphics2D p = (Graphics2D) g.create();
        p.translate(x, y);
        p.scale(1, 0.16);
        Color c = kind.mid;
        p.setPaint(new RadialGradientPaint(0, 0, (float) r, new float[]{0, 1},
                new Color[]{alpha(c, (int) (70 * glow)), alpha(c, 0)}));
        p.fill(new Ellipse2D.Double(-r, -r, r * 2, r * 2));
        p.dispose();
    }

    /** The newly carved spell in the lamp base; only drawn after a passed repair. */
    private static void rune(Graphics2D g, double x, double y, double k, double t) {
        Graphics2D l = (Graphics2D) g.create();
        l.translate(x, y);
        l.scale(k, k);
        float pulse = (float) (0.78 + 0.22 * Math.sin(t * 2.2));
        Color cyan = AcademyTheme.CYAN;
        l.setPaint(new RadialGradientPaint(0, 190, 44, new float[]{0, 0.32f, 1}, new Color[]{
            alpha(cyan, (int) (150 * pulse)), alpha(cyan, (int) (46 * pulse)), alpha(cyan, 0)}));
        l.fill(new Ellipse2D.Double(-44, 146, 88, 88));
        l.setColor(alpha(new Color(0xC2F4FB), (int) (235 * pulse)));
        l.setStroke(new BasicStroke(1.8f));
        l.draw(new Ellipse2D.Double(-9, 181, 18, 18));
        Path2D star = new Path2D.Double();
        star.moveTo(0, 183.5);
        star.lineTo(1.6, 188.4);
        star.lineTo(6.5, 190);
        star.lineTo(1.6, 191.6);
        star.lineTo(0, 196.5);
        star.lineTo(-1.6, 191.6);
        star.lineTo(-6.5, 190);
        star.lineTo(-1.6, 188.4);
        star.closePath();
        l.fill(star);
        for (int i = 0; i < 3; i++) {
            double p = ((t * 0.32 + i / 3.0) % 1 + 1) % 1;
            double sx = Math.sin(t * 1.3 + i * 2.1) * 12;
            double sy = 184 - p * 30;
            l.setColor(alpha(cyan, (int) (170 * (1 - p) * pulse)));
            l.fill(new Ellipse2D.Double(sx - 1.6, sy - 1.6, 3.2, 3.2));
        }
        l.dispose();
    }

    private static void bracket(Graphics2D g, double x, double y) {
        g.setColor(new Color(0x101826));
        g.fill(new Rectangle2D.Double(x + 40, y - 24, 10, 52));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x, y, x + 44, y));
        Path2D scroll = new Path2D.Double();
        scroll.moveTo(x + 44, y + 20);
        scroll.quadTo(x + 18, y + 18, x + 16, y + 2);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(scroll);
        g.setColor(new Color(120, 150, 190, 50));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(x + 2, y - 2, x + 42, y - 2));
    }

    /** A thin pull cord: the reminder lamp is tied to the messenger's bell. */
    private static void cord(Graphics2D g, double x0, double y0, double x1, double y1) {
        Path2D c = new Path2D.Double();
        c.moveTo(x0, y0);
        c.quadTo((x0 + x1) / 2, Math.max(y0, y1) + 30, x1, y1);
        g.setColor(new Color(0x6B4E2C));
        g.setStroke(new BasicStroke(2f));
        g.draw(c);
    }

    private static void bell(Graphics2D g, double x, double y, double k, double swing) {
        Graphics2D b = (Graphics2D) g.create();
        b.translate(x, y);
        b.scale(k, k);
        b.rotate(swing);
        b.setColor(new Color(0x101826));
        b.fill(new Rectangle2D.Double(-9, -2, 18, 12));
        Path2D body = new Path2D.Double();
        body.moveTo(-9, 8);
        body.curveTo(-27, 10, -24, 40, -31, 58);
        body.lineTo(-37, 66);
        body.lineTo(37, 66);
        body.lineTo(31, 58);
        body.curveTo(24, 40, 27, 10, 9, 8);
        body.closePath();
        b.setPaint(new LinearGradientPaint(-37, 0, 37, 0, new float[]{0, 0.3f, 0.55f, 1},
                new Color[]{BRASS_DARK, BRASS_HI, BRASS, new Color(0x3A2A14)}));
        b.fill(body);
        b.setColor(new Color(0x2A1D0D));
        b.setStroke(new BasicStroke(1.8f));
        b.draw(body);
        b.setPaint(new LinearGradientPaint(-37, 0, 37, 0, new float[]{0, 0.3f, 1},
                new Color[]{new Color(0x4A3519), new Color(0xC9A35C), new Color(0x2E2110)}));
        b.fill(new Rectangle2D.Double(-37, 61, 74, 6));
        b.setColor(new Color(0x2A1D0D));
        b.fill(new Ellipse2D.Double(-6, 66, 12, 12));
        b.setColor(new Color(255, 240, 200, 70));
        b.setStroke(new BasicStroke(2f));
        Path2D shine = new Path2D.Double();
        shine.moveTo(-14, 16);
        shine.quadTo(-20, 34, -22, 52);
        b.draw(shine);
        b.dispose();
    }

    /** Sound rings beside the swinging bell. */
    private static void ring(Graphics2D g, double x, double y, double glow, double t) {
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 2; i++) {
            double p = ((t * 1.2 + i * 0.5) % 1 + 1) % 1;
            double r = 46 + p * 40;
            g.setColor(alpha(AcademyTheme.GOLD_HI, (int) (180 * (1 - p) * glow)));
            g.draw(new Arc2D.Double(x - r, y - r, r * 2, r * 2, -35, 70, Arc2D.OPEN));
            g.draw(new Arc2D.Double(x - r, y - r, r * 2, r * 2, 145, 70, Arc2D.OPEN));
        }
    }

    private static void candle(Graphics2D g, double x, double base, double t) {
        AcademyAtmosphere.lamp(g, (float) x, (float) (base - 58), 150, 0.75f, t);
        g.setPaint(new GradientPaint(0, (float) (base - 6), BRASS, 0, (float) base, BRASS_DARK));
        g.fill(new Ellipse2D.Double(x - 20, base - 8, 40, 10));
        g.setPaint(new GradientPaint((float) (x - 8), 0, new Color(0xEFE6D2), (float) (x + 8), 0, new Color(0xC9BC9F)));
        g.fill(new Rectangle2D.Double(x - 7, base - 46, 14, 42));
        g.setColor(new Color(0xF4ECDA));
        g.fill(new Ellipse2D.Double(x - 7, base - 49, 14, 6));
        g.fill(new Ellipse2D.Double(x + 2, base - 46, 5, 14));
        double f = 1 + 0.08 * Math.sin(t * 9.1) + 0.05 * Math.sin(t * 3.7);
        double sway = Math.sin(t * 2.9) * 1.6;
        Path2D flame = new Path2D.Double();
        flame.moveTo(x, base - 48);
        flame.curveTo(x + 6, base - 52, x + 5 + sway, base - 60 * f, x + sway, base - 70 * f);
        flame.curveTo(x - 5 + sway, base - 60 * f, x - 6, base - 52, x, base - 48);
        flame.closePath();
        g.setPaint(new GradientPaint(0, (float) (base - 70 * f), new Color(255, 214, 120, 230), 0,
                (float) (base - 48), new Color(255, 250, 230, 255)));
        g.fill(flame);
        g.setColor(new Color(0x3A2A1A));
        g.draw(new Line2D.Double(x, base - 49, x, base - 53));
    }

    private static void record(Graphics2D g, double x, double y) {
        double w = 160;
        double h = 214;
        Graphics2D p = (Graphics2D) g.create();
        p.rotate(Math.toRadians(-1.6), x + w / 2, y);
        for (int i = 0; i < 4; i++) {
            p.setColor(new Color(0, 0, 0, 22));
            p.fill(new Rectangle2D.Double(x + 3 + i, y + 5 + i, w, h));
        }
        p.setPaint(new GradientPaint(0, (float) y, PAPER, 0, (float) (y + h), new Color(0xD4C196)));
        p.fill(new Rectangle2D.Double(x, y, w, h));
        Random r = new Random(5);
        p.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 26; i++) {
            p.setColor(new Color(120, 92, 52, 10 + r.nextInt(12)));
            double fx = x + r.nextDouble() * w;
            double fy = y + r.nextDouble() * h;
            p.draw(new Line2D.Double(fx, fy, fx + r.nextDouble() * 14 - 7, fy + r.nextDouble() * 3 - 1.5));
        }
        rivet(p, x + w / 2, y + 10, 5);
        p.setColor(INK);
        p.setFont(AcademyTheme.script(25));
        FontMetrics fm = p.getFontMetrics();
        String title = "值房记录";
        p.drawString(title, (float) (x + (w - fm.stringWidth(title)) / 2), (float) (y + 48));
        p.setFont(AcademyTheme.font(Font.PLAIN, 13));
        fm = p.getFontMetrics();
        String sub = "零点 · 岗位报告";
        p.setColor(new Color(0x6B5A44));
        p.drawString(sub, (float) (x + (w - fm.stringWidth(sub)) / 2), (float) (y + 70));
        p.setColor(new Color(0xB5965F));
        p.draw(new Line2D.Double(x + 14, y + 80, x + w - 14, y + 80));
        p.setFont(AcademyTheme.font(Font.BOLD, 15));
        p.setColor(INK);
        p.drawString("是", (float) (x + 92), (float) (y + 104));
        p.drawString("否", (float) (x + 126), (float) (y + 104));
        p.setFont(AcademyTheme.font(Font.PLAIN, 16));
        String[] rows = {"岗位 A", "岗位 B"};
        for (int i = 0; i < rows.length; i++) {
            double ry = y + 140 + i * 40;
            p.setColor(INK);
            p.drawString(rows[i], (float) (x + 14), (float) ry);
            p.setColor(new Color(0x6B5A44));
            p.setStroke(new BasicStroke(1.4f));
            p.draw(new Rectangle2D.Double(x + 90, ry - 15, 18, 18));
            p.draw(new Rectangle2D.Double(x + 124, ry - 15, 18, 18));
            p.setColor(new Color(181, 150, 95, 120));
            p.setStroke(new BasicStroke(1f));
            p.draw(new Line2D.Double(x + 12, ry + 12, x + w - 12, ry + 12));
        }
        p.dispose();
    }

    private static void desk(Graphics2D g, double x, double top, double w) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(x - 10, top + 196, w + 20, 40));
        g.setPaint(new GradientPaint(0, (float) (top + 20), WOOD_DARK, 0, (float) (top + 220), new Color(0x150D07)));
        g.fill(new Rectangle2D.Double(x + 24, top + 70, 24, 150));
        g.fill(new Rectangle2D.Double(x + w - 48, top + 70, 24, 150));
        g.setPaint(new GradientPaint(0, (float) (top + 22), new Color(0x33221A), 0, (float) (top + 78), WOOD_DARK));
        g.fill(new Rectangle2D.Double(x + 14, top + 22, w - 28, 54));
        g.setColor(new Color(0x170E07));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Rectangle2D.Double(x + w / 2 - 90, top + 32, 180, 34));
        rivet(g, x + w / 2, top + 49, 5);
        g.setPaint(new GradientPaint(0, (float) top, new Color(0x5A3E26), 0, (float) (top + 24), new Color(0x3A2717)));
        g.fill(new Rectangle2D.Double(x, top, w, 24));
        g.setColor(new Color(255, 222, 170, 50));
        g.draw(new Line2D.Double(x, top + 1, x + w, top + 1));
        double bx = x + 56;
        g.setPaint(new GradientPaint(0, (float) (top - 22), new Color(0x6A3628), 0, (float) top, new Color(0x45201A)));
        g.fill(new RoundRectangle2D.Double(bx, top - 22, 168, 22, 4, 4));
        g.setColor(new Color(0xD8C8A2));
        g.fill(new Rectangle2D.Double(bx + 8, top - 17, 154, 12));
        g.setColor(new Color(150, 128, 96, 120));
        for (int i = 0; i < 4; i++)
            g.draw(new Line2D.Double(bx + 8, top - 15 + i * 3, bx + 162, top - 15 + i * 3));
        g.setColor(new Color(217, 175, 96, 170));
        g.fill(new Rectangle2D.Double(bx + 120, top - 22, 8, 22));
        double ix = x + 300;
        g.setPaint(new GradientPaint((float) (ix - 14), 0, new Color(0x1A2433), (float) (ix + 14), 0, new Color(0x0B111C)));
        g.fill(new RoundRectangle2D.Double(ix - 15, top - 26, 30, 26, 10, 10));
        g.setColor(BRASS);
        g.fill(new Rectangle2D.Double(ix - 7, top - 31, 14, 6));
        Path2D quill = new Path2D.Double();
        quill.moveTo(ix + 2, top - 30);
        quill.curveTo(ix + 18, top - 70, ix + 40, top - 104, ix + 66, top - 126);
        quill.curveTo(ix + 52, top - 96, ix + 36, top - 66, ix + 4, top - 30);
        quill.closePath();
        g.setPaint(new GradientPaint((float) ix, (float) top, new Color(0xD8CDB4), (float) (ix + 60),
                (float) (top - 120), new Color(0xF4EEDF)));
        g.fill(quill);
        g.setColor(new Color(120, 104, 80, 140));
        g.draw(new Line2D.Double(ix + 3, top - 30, ix + 62, top - 122));
    }

    /** The brass notice of the registry; one corner has gone green and cannot be read. */
    private static void notice(Graphics2D g, double x, double y) {
        double w = 170;
        double h = 136;
        Shape body = new RoundRectangle2D.Double(x, y, w, h, 10, 10);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x + 4, y + 6, w, h, 10, 10));
        g.setPaint(new GradientPaint(0, (float) y, new Color(0xA7834A), 0, (float) (y + h), new Color(0x5E4423)));
        g.fill(body);
        g.setFont(AcademyTheme.script(20));
        FontMetrics fm = g.getFontMetrics();
        String title = "登记须知";
        float tx = (float) (x + (w - fm.stringWidth(title)) / 2);
        g.setColor(new Color(255, 236, 190, 110));
        g.drawString(title, tx, (float) (y + 39));
        g.setColor(new Color(0x2C1C0A));
        g.drawString(title, tx, (float) (y + 38));
        Random r = new Random(29);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            double ly = y + 64 + i * 16;
            double end = x + w - 22 - r.nextInt(34);
            for (double lx = x + 22; lx < end; lx += 9 + r.nextInt(4)) {
                g.setColor(new Color(42, 29, 13, 150));
                g.draw(new Line2D.Double(lx, ly, lx + 5, ly));
            }
        }
        Graphics2D v = (Graphics2D) g.create();
        v.clip(body);
        v.setPaint(new RadialGradientPaint((float) (x + w - 24), (float) (y + h - 20), 50, new float[]{0, 0.55f, 1},
                new Color[]{new Color(0x5F8F7A), new Color(95, 143, 122, 190), new Color(95, 143, 122, 0)}));
        v.fill(new Ellipse2D.Double(x + w - 74, y + h - 70, 100, 100));
        v.dispose();
        g.setColor(new Color(0x2A1D0D));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(body);
        g.setColor(new Color(255, 236, 190, 70));
        g.draw(new RoundRectangle2D.Double(x + 6, y + 6, w - 12, h - 12, 6, 6));
        for (double[] p : new double[][]{{x + 11, y + 11}, {x + w - 11, y + 11}, {x + 11, y + h - 11}})
            rivet(g, p[0], p[1], 4);
    }

    /** The registry counter in the hall, its panelled front facing the room. */
    private static void counter(Graphics2D g, double x, double top, double w) {
        double bottom = top + 200;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(x - 14, bottom - 18, w + 28, 36));
        g.setPaint(new GradientPaint(0, (float) (top + 24), new Color(0x3A2717), 0, (float) bottom, new Color(0x160D07)));
        g.fill(new Rectangle2D.Double(x + 10, top + 24, w - 20, bottom - top - 24));
        int panels = 3;
        double gap = 24;
        double pw = (w - 20 - gap * (panels + 1)) / panels;
        double py = top + 50;
        double ph = bottom - top - 82;
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < panels; i++) {
            double px = x + 10 + gap + i * (pw + gap);
            g.setPaint(new GradientPaint(0, (float) py, new Color(0x2A1B10), 0, (float) (py + ph), new Color(0x1A110A)));
            g.fill(new RoundRectangle2D.Double(px, py, pw, ph, 6, 6));
            g.setColor(new Color(0x0E0804));
            g.draw(new Line2D.Double(px + 3, py, px + pw - 3, py));
            g.draw(new Line2D.Double(px, py + 3, px, py + ph - 3));
            g.setColor(new Color(255, 222, 170, 30));
            g.draw(new Line2D.Double(px + 3, py + ph, px + pw - 3, py + ph));
            g.draw(new Line2D.Double(px + pw, py + 3, px + pw, py + ph - 3));
        }
        g.setPaint(new GradientPaint(0, (float) (top + 24), BRASS, 0, (float) (top + 30), BRASS_DARK));
        g.fill(new Rectangle2D.Double(x + 10, top + 24, w - 20, 6));
        g.setColor(new Color(0x120A05));
        g.fill(new Rectangle2D.Double(x + 4, bottom - 12, w - 8, 12));
        g.setPaint(new GradientPaint(0, (float) top, new Color(0x5A3E26), 0, (float) (top + 24), new Color(0x3A2717)));
        g.fill(new Rectangle2D.Double(x, top, w, 24));
        g.setColor(new Color(255, 222, 170, 50));
        g.draw(new Line2D.Double(x, top + 1, x + w, top + 1));
    }

    /**
     * The registry ledger on its stand, open at the midnight line: two names in one row, and a
     * margin note in another hand. Bottom-centred at (cx, base); about 252 wide and 146 high at scale 1.
     */
    private static void ledger(Graphics2D g, double cx, double base, double k) {
        Graphics2D b = (Graphics2D) g.create();
        b.translate(cx, base);
        b.scale(k, k);
        Path2D stand = new Path2D.Double();
        stand.moveTo(-104, 0);
        stand.lineTo(104, 0);
        stand.lineTo(92, -14);
        stand.lineTo(-92, -14);
        stand.closePath();
        b.setPaint(new GradientPaint(0, -14, new Color(0x4A3220), 0, 0, new Color(0x24170D)));
        b.fill(stand);
        b.setColor(new Color(0, 0, 0, 80));
        b.fill(new RoundRectangle2D.Double(-122, -142, 252, 134, 8, 8));
        b.setPaint(new GradientPaint(0, -146, new Color(0x6A3628), 0, -12, new Color(0x3E1C16)));
        b.fill(new RoundRectangle2D.Double(-126, -146, 252, 134, 8, 8));
        for (int side = -1; side <= 1; side += 2) {
            Path2D page = new Path2D.Double();
            page.moveTo(side * 2, -134);
            page.quadTo(side * 60, -144, side * 118, -136);
            page.lineTo(side * 118, -20);
            page.quadTo(side * 60, -28, side * 2, -18);
            page.closePath();
            b.setPaint(new GradientPaint((float) (side * 118), 0, PAPER, (float) (side * 2), 0, new Color(0xC9B68C)));
            b.fill(page);
        }
        b.setPaint(new LinearGradientPaint(-10, 0, 10, 0, new float[]{0, 0.5f, 1}, new Color[]{
            new Color(60, 40, 20, 0), new Color(60, 40, 20, 110), new Color(60, 40, 20, 0)}));
        b.fill(new Rectangle2D.Double(-10, -138, 20, 122));
        b.setColor(INK);
        b.setFont(AcademyTheme.script(17));
        FontMetrics fm = b.getFontMetrics();
        b.drawString("登记册", (float) (-60 - fm.stringWidth("登记册") / 2.0), -110f);
        b.setColor(new Color(0x6B5A44));
        b.setFont(AcademyTheme.font(Font.PLAIN, 12));
        fm = b.getFontMetrics();
        b.drawString("旁注", (float) (60 - fm.stringWidth("旁注") / 2.0), -111f);
        Random r = new Random(31);
        for (int row = 0; row < 4; row++) {
            double y = -94 + row * 18;
            b.setColor(new Color(181, 150, 95, 120));
            b.setStroke(new BasicStroke(1f));
            b.draw(new Line2D.Double(-110, y + 5, -8, y + 5));
            b.draw(new Line2D.Double(8, y + 5, 110, y + 5));
            if (row == 3)
                break;
            b.setColor(new Color(70, 58, 46, 110));
            b.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double end = -40 - r.nextInt(30);
            for (double lx = -106; lx < end; lx += 7 + r.nextInt(4))
                b.draw(new Line2D.Double(lx, y, lx + 4, y - 2));
            end = 46 + r.nextInt(30);
            for (double lx = 14; lx < end; lx += 7 + r.nextInt(4))
                b.draw(new Line2D.Double(lx, y, lx + 4, y - 2));
        }
        float y = -40;
        b.setColor(INK);
        b.setFont(AcademyTheme.font(Font.BOLD, 12));
        b.drawString("零点", -108f, y);
        b.setFont(AcademyTheme.script(17));
        b.drawString("A", -64f, y);
        b.drawString("B", -38f, y);
        Graphics2D n = (Graphics2D) b.create();
        n.rotate(Math.toRadians(-6), 58, y - 6);
        n.setFont(AcademyTheme.script(22));
        n.setColor(SEAL);
        n.drawString("正常", 34f, y + 2);
        n.dispose();
        b.dispose();
    }

    /** The registry seal and its ink pad on the counter whose top is at top. */
    private static void stamp(Graphics2D g, double x, double top) {
        double px = x + 34;
        g.setPaint(new GradientPaint(0, (float) (top - 12), new Color(0x7E5E2C), 0, (float) top, new Color(0x3A2A14)));
        g.fill(new RoundRectangle2D.Double(px, top - 12, 54, 12, 4, 4));
        g.setColor(new Color(0x7A2A22));
        g.fill(new Ellipse2D.Double(px + 5, top - 15, 44, 7));
        g.setPaint(new GradientPaint(0, (float) (top - 14), BRASS, 0, (float) top, BRASS_DARK));
        g.fill(new RoundRectangle2D.Double(x - 16, top - 14, 32, 14, 3, 3));
        g.setPaint(new GradientPaint((float) (x - 6), 0, new Color(0x4A2E1A), (float) (x + 6), 0, new Color(0x24160C)));
        g.fill(new RoundRectangle2D.Double(x - 6, top - 46, 12, 34, 6, 6));
        g.setPaint(new RadialGradientPaint((float) (x - 3), (float) (top - 52), 10, new float[]{0, 1},
                new Color[]{new Color(0x6A4630), new Color(0x2A1A0E)}));
        g.fill(new Ellipse2D.Double(x - 10, top - 60, 20, 18));
    }

    /** The ledger on a lectern beside the trial bench, standing on the floor at base. */
    private static void lectern(Graphics2D g, double cx, double base) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(cx - 80, base - 10, 160, 22));
        g.setPaint(new GradientPaint(0, (float) (base - 16), new Color(0x2C1D12), 0, (float) base, new Color(0x150D07)));
        g.fill(new Rectangle2D.Double(cx - 60, base - 16, 120, 16));
        g.setPaint(new LinearGradientPaint((float) (cx - 12), 0, (float) (cx + 12), 0, new float[]{0, 0.4f, 1},
                new Color[]{new Color(0x24170D), new Color(0x4A3220), new Color(0x1A110A)}));
        g.fill(new Rectangle2D.Double(cx - 12, base - 150, 24, 136));
        ledger(g, cx, base - 146, 0.95);
    }

    private static void rack(Graphics2D g, double x, double y, double w, double bottom) {
        double h = bottom - y;
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(0, 0, 0, 22));
            g.fill(new Rectangle2D.Double(x + 4 + i * 2, y + 6 + i * 2, w, h));
        }
        g.setPaint(new GradientPaint(0, (float) y, new Color(0x43301D), 0, (float) bottom, new Color(0x261910)));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setPaint(new GradientPaint(0, (float) (y - 14), new Color(0x553B24), 0, (float) y, new Color(0x2C1D12)));
        g.fill(new Rectangle2D.Double(x - 12, y - 14, w + 24, 16));
        g.setColor(new Color(255, 222, 170, 45));
        g.draw(new Line2D.Double(x - 12, y - 13, x + w + 12, y - 13));
        int cols = 4;
        int rows = 4;
        double cw = (w - 30) / cols;
        double ch = 66;
        Random r = new Random(19);
        for (int row = 0; row < rows; row++)
            for (int col = 0; col < cols; col++) {
                double cx = x + 15 + col * cw;
                double cy = y + 16 + row * ch;
                g.setPaint(new GradientPaint(0, (float) cy, new Color(0x0C0705), 0, (float) (cy + ch - 10),
                        new Color(0x1C120B)));
                g.fill(new Rectangle2D.Double(cx + 4, cy + 4, cw - 8, ch - 14));
                int letters = r.nextInt(5) < 2 ? 0 : 1 + r.nextInt(2);
                for (int n = 0; n < letters; n++) {
                    double ew = cw - 26;
                    double ex = cx + 10 + n * 6;
                    double ey = cy + ch - 34 - n * 7;
                    g.setPaint(new GradientPaint(0, (float) ey, PAPER, 0, (float) (ey + 22), new Color(0xC9B68C)));
                    g.fill(new Rectangle2D.Double(ex, ey, ew, 22));
                    g.setColor(new Color(150, 124, 84, 150));
                    g.draw(new Line2D.Double(ex, ey, ex + ew / 2, ey + 10));
                    g.draw(new Line2D.Double(ex + ew, ey, ex + ew / 2, ey + 10));
                    g.setColor(SEAL);
                    g.fill(new Ellipse2D.Double(ex + ew / 2 - 4, ey + 6, 8, 8));
                }
                g.setColor(new Color(0x553B24));
                g.fill(new Rectangle2D.Double(cx, cy + ch - 10, cw, 6));
                g.setColor(BRASS);
                g.fill(new Rectangle2D.Double(cx + cw / 2 - 11, cy + ch - 9, 22, 4));
            }
        double dy = y + 16 + rows * ch + 6;
        for (int i = 0; i < 2; i++) {
            double dx = x + 15 + i * (w - 30) / 2;
            double dw = (w - 30) / 2 - 6;
            g.setColor(new Color(0x2E1F13));
            g.fill(new Rectangle2D.Double(dx, dy, dw, bottom - dy - 14));
            g.setColor(new Color(0x170E07));
            g.draw(new Rectangle2D.Double(dx, dy, dw, bottom - dy - 14));
            rivet(g, dx + dw / 2, dy + (bottom - dy - 14) / 2, 5);
        }
    }

    private static Color shade(Color c, int d) {
        return new Color(clamp(c.getRed() + d), clamp(c.getGreen() + d), clamp(c.getBlue() + d), c.getAlpha());
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
