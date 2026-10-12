/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Paper things on the reading desk, shared by the torn pages and the academy's letters: the desk under
 * the lamp, the paper with its fibres, water stains and creases, ink stamps and the wax seal, and the
 * thump of a stamp coming down. Everything that does not move is drawn once at device resolution and
 * cached; a resting sheet is put on whole device pixels so its text stays as sharp as it was rendered.
 */
final class AcademyPaper {
    /** Dark ink for writing on paper. */
    static final Color INK = new Color(0x2E2620);
    /** Softer ink for notes. */
    static final Color INK_SOFT = new Color(0x4A3B33);
    /** Sepia for small headings. */
    static final Color SEPIA = new Color(0x8A5A2E);
    /** Faded brown for printed marks. */
    static final Color FADED = new Color(0x7A6446);
    /** Red stamp ink. */
    static final Color RED_INK = new Color(0xA23B2E);
    /** Green stamp ink. */
    static final Color GREEN_INK = new Color(0x1F7A6A);

    private static final int OLD_PAGE = 0;
    private static final int LETTER = 1;
    private static final int LEAF = 2;

    private AcademyPaper() {
    }

    /**
     * @return graphics on a tiny image, only for measuring text with the same hints the sheets are drawn with
     */
    static Graphics2D probe() {
        return graphics(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 1);
    }

    /**
     * @param g    graphics
     * @param font the font
     * @param text the text
     * @return the advance of the text
     */
    static float textWidth(Graphics2D g, Font font, String text) {
        return (float) font.getStringBounds(text, g.getFontRenderContext()).getWidth();
    }

    /**
     * Writes a text centred on a point of its base line.
     *
     * @param g     graphics
     * @param text  the text
     * @param font  the font
     * @param color the ink
     * @param cx    the centre
     * @param y     the base line
     * @return the width of the text
     */
    static float centred(Graphics2D g, String text, Font font, Color color, float cx, float y) {
        float w = textWidth(g, font, text);
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, cx - w / 2, y);
        return w;
    }

    /**
     * @param g the graphics
     * @return the factor from user space to device pixels
     */
    static double deviceScale(Graphics2D g) {
        double det = Math.abs(g.getTransform().getDeterminant());
        return det > 0 ? Math.sqrt(det) : 1;
    }

    /**
     * @return true if paper may move: motion is on and the transitions are not skipped for a check
     */
    static boolean animated() {
        return AcademyAtmosphere.motion() && !Boolean.getBoolean("academy.skipTransitions");
    }

    /**
     * @param c a component on a page
     * @return true while a dissolve or a scene lies over the whole window, so nothing on the page can be seen
     */
    static boolean covered(JComponent c) {
        JRootPane root = c.getRootPane();
        return root != null && root.getGlassPane().isVisible();
    }

    /**
     * Draws a cached image so that a resting sheet lands on whole device pixels; a moving sheet is drawn
     * at its exact place and may be blurred by the interpolation.
     *
     * @param g       graphics in sheet coordinates
     * @param img     the image rendered at device resolution
     * @param x       left of the image in sheet coordinates
     * @param y       top of the image in sheet coordinates
     * @param ds      the device scale the image was rendered with
     * @param resting true if the sheet is not moving
     */
    static void drawSnapped(Graphics2D g, BufferedImage img, double x, double y, double ds, boolean resting) {
        Graphics2D b = (Graphics2D) g.create();
        if (resting) {
            Point2D at = g.getTransform().transform(new Point2D.Double(x, y), null);
            b.setTransform(AffineTransform.getTranslateInstance(Math.round(at.getX()), Math.round(at.getY())));
        } else {
            b.translate(x, y);
            b.scale(1 / ds, 1 / ds);
        }
        b.drawImage(img, 0, 0, null);
        b.dispose();
    }

    /**
     * @param w logical width
     * @param h logical height
     * @param k logical units to device pixels
     * @return a transparent image large enough for the logical size at the given scale
     */
    static BufferedImage image(double w, double h, double k) {
        return new BufferedImage(Math.max(1, (int) Math.ceil(w * k)), Math.max(1, (int) Math.ceil(h * k)),
                BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * @param img the image to draw on
     * @param k   logical units to device pixels
     * @return graphics in logical units with every quality hint switched on
     */
    static Graphics2D graphics(BufferedImage img, double k) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.scale(k, k);
        return g;
    }

    /**
     * The outline of a sheet: one side torn roughly, the others only slightly uneven.
     *
     * @param r          the random source of this sheet
     * @param w          width
     * @param h          height
     * @param roughSide  0 top, 1 right, 2 bottom, 3 left; any other value gives a clean cut sheet
     * @return the outline
     */
    static Path2D edge(Random r, float w, float h, int roughSide) {
        float[][] corners = {{0, 0}, {w, 0}, {w, h}, {0, h}};
        Path2D p = new Path2D.Float();
        boolean first = true;
        double phase = r.nextDouble() * 6;
        for (int side = 0; side < 4; side++) {
            float[] a = corners[side];
            float[] b = corners[(side + 1) % 4];
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double len = Math.hypot(dx, dy);
            double nx = -dy / len;
            double ny = dx / len;
            boolean rough = side == roughSide;
            int steps = (int) (len / (rough ? 6 : 20));
            for (int s = 0; s < steps; s++) {
                double f = s / (double) steps;
                double amp = rough ? 5 + 5 * Math.sin(f * Math.PI * 5 + phase) + r.nextDouble() * 7
                        : r.nextDouble() * 1.4;
                if (!rough && s == 0)
                    amp = 1 + r.nextDouble() * 3;
                double px = a[0] + dx * f + nx * amp;
                double py = a[1] + dy * f + ny * amp;
                if (first) {
                    p.moveTo(px, py);
                    first = false;
                } else
                    p.lineTo(px, py);
            }
        }
        p.closePath();
        return p;
    }

    /**
     * Paints a sheet of old paper: soft shadow, warm base, blotches, fibres, water stains, creases, a darker
     * rim and a light worn edge.
     *
     * @param g      graphics in sheet coordinates
     * @param shape  the outline from {@link #edge}
     * @param w      width
     * @param h      height
     * @param r      the random source of this sheet, continued from {@link #edge}
     * @param letter true for a letter folded in three, false for a page folded once down the middle
     */
    static void sheet(Graphics2D g, Shape shape, float w, float h, Random r, boolean letter) {
        paper(g, shape, w, h, r, letter ? LETTER : OLD_PAGE);
    }

    /**
     * Paints a leaf of the apprentice's own notebook: written tonight, so as clean as a letter, never folded,
     * with the shadow of the binding along its left edge.
     *
     * @param g     graphics in sheet coordinates
     * @param shape the outline from {@link #edge}
     * @param w     width
     * @param h     height
     * @param r     the random source of this sheet, continued from {@link #edge}
     */
    static void leaf(Graphics2D g, Shape shape, float w, float h, Random r) {
        paper(g, shape, w, h, r, LEAF);
    }

    private static void paper(Graphics2D g, Shape shape, float w, float h, Random r, int kind) {
        boolean clean = kind != OLD_PAGE;
        for (int k = 0; k < 8; k++) {
            AffineTransform shadow = AffineTransform.getTranslateInstance(3 + k * .9, 6 + k * 1.2);
            g.setColor(new Color(0, 0, 0, 13));
            g.fill(shadow.createTransformedShape(shape));
        }
        g.setPaint(clean ? new GradientPaint(0, 0, new Color(0xF4EAD0), w * .3f, h, new Color(0xE2CFA6))
                : new GradientPaint(0, 0, new Color(0xF0E3C2), w * .3f, h, new Color(0xD8C295)));
        g.fill(shape);

        Graphics2D p = (Graphics2D) g.create();
        p.clip(shape);
        for (int k = 0; k < 4; k++) {
            float cx = r.nextFloat() * w;
            float cy = r.nextFloat() * h;
            float rad = 110 + r.nextFloat() * 120;
            p.setPaint(new RadialGradientPaint(cx, cy, rad, new float[]{0, 1},
                    new Color[]{new Color(184, 150, 96, clean ? 30 : 46), new Color(184, 150, 96, 0)}));
            p.fill(new Ellipse2D.Float(cx - rad, cy - rad, rad * 2, rad * 2));
        }
        p.setStroke(new BasicStroke(.6f));
        int fibres = Math.round(260 * (w * h) / (580f * 372f));
        for (int k = 0; k < fibres; k++) {
            float fx = r.nextFloat() * w;
            float fy = r.nextFloat() * h;
            double angle = (r.nextDouble() - .5) * .9;
            float len = 3 + r.nextFloat() * 10;
            p.setColor(new Color(120, 90, 50, 12 + r.nextInt(16)));
            p.draw(new Line2D.Double(fx, fy, fx + Math.cos(angle) * len, fy + Math.sin(angle) * len));
        }
        // water stains: a faint wet patch whose tide line is darker than its middle, kept to the lower corners;
        // only the old, water-soaked pages have them, a letter written tonight is clean
        int stains = clean ? 0 : 1 + r.nextInt(2);
        boolean firstLeft = r.nextBoolean();
        for (int k = 0; k < stains; k++)
            stain(p, r, w, h, (k == 0) == firstLeft);
        if (kind == LEAF) {
            // the leaf curves into the binding: a soft shadow and the stitches holding it
            p.setPaint(new GradientPaint(0, 0, new Color(70, 48, 24, 70), 26, 0, new Color(70, 48, 24, 0)));
            p.fill(new Rectangle2D.Float(0, 0, 26, h));
            for (float y = 34; y < h - 20; y += 46) {
                p.setColor(new Color(90, 64, 36, 120));
                p.fill(new Ellipse2D.Float(8.2f, y - 1.6f, 3.2f, 3.2f));
                p.setColor(new Color(120, 92, 60, 90));
                p.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                p.draw(new Line2D.Float(9.8f, y + 2, 9.8f, y + 18));
            }
        } else if (kind == LETTER) {
            // folded in three to fit the envelope
            for (int k = 1; k <= 2; k++) {
                float fold = h * k / 3f + (r.nextFloat() - .5f) * 8;
                p.setStroke(new BasicStroke(1.2f));
                p.setColor(new Color(96, 72, 40, 40));
                p.draw(new Line2D.Float(0, fold, w, fold + 2));
                p.setColor(new Color(255, 250, 232, 80));
                p.draw(new Line2D.Float(0, fold + 1.5f, w, fold + 3.5f));
            }
        } else {
            float fold = w * .5f + (r.nextFloat() - .5f) * 40;
            p.setStroke(new BasicStroke(1.3f));
            p.setColor(new Color(96, 72, 40, 46));
            p.draw(new Line2D.Float(fold, 0, fold + 3, h));
            p.setColor(new Color(255, 250, 232, 70));
            p.draw(new Line2D.Float(fold + 1.6f, 0, fold + 4.6f, h));
        }
        p.setPaint(new RadialGradientPaint(w / 2, h / 2, w * .62f, new float[]{.55f, 1},
                new Color[]{new Color(110, 78, 40, 0), new Color(110, 78, 40, clean ? 70 : 86)}));
        p.fill(new Rectangle2D.Float(0, 0, w, h));
        p.setColor(new Color(255, 246, 222, 110));
        p.setStroke(new BasicStroke(2.4f));
        p.draw(shape);
        p.dispose();
        g.setColor(new Color(96, 70, 40, 150));
        g.setStroke(new BasicStroke(.9f));
        g.draw(shape);
    }

    private static void stain(Graphics2D p, Random r, float w, float h, boolean left) {
        float sx = (left ? .1f : .9f) * w + (r.nextFloat() - .5f) * 50;
        float sy = .84f * h + (r.nextFloat() - .5f) * 40;
        float sw = 80 + r.nextFloat() * 60;
        float sh = sw * (.55f + r.nextFloat() * .3f);
        Path2D blob = new Path2D.Float();
        for (int a = 0; a <= 28; a++) {
            double ang = a * Math.PI * 2 / 28;
            double wob = 1 + (r.nextDouble() - .5) * .16;
            double bx = sx + Math.cos(ang) * sw / 2 * wob;
            double by = sy + Math.sin(ang) * sh / 2 * wob;
            if (a == 0)
                blob.moveTo(bx, by);
            else
                blob.lineTo(bx, by);
        }
        blob.closePath();
        Graphics2D s = (Graphics2D) p.create();
        s.clip(blob);
        s.setPaint(new RadialGradientPaint(sx, sy, sw * .55f, new float[]{0, .75f, 1},
                new Color[]{new Color(150, 112, 62, 8), new Color(150, 112, 62, 20), new Color(130, 92, 46, 46)}));
        s.fill(blob.getBounds2D());
        s.dispose();
        p.setColor(new Color(120, 84, 40, 40));
        p.setStroke(new BasicStroke(2.2f));
        p.draw(blob);
        p.setColor(new Color(120, 84, 40, 22));
        p.setStroke(new BasicStroke(1f));
        p.draw(AffineTransform.getTranslateInstance(sx * .07, sy * .07).createTransformedShape(
                AffineTransform.getScaleInstance(.93, .93).createTransformedShape(blob)));
    }

    /**
     * A thin rule with a diamond in the middle, as printed under a heading.
     *
     * @param g     graphics
     * @param left  left end
     * @param right right end
     * @param y     the height of the rule
     */
    static void rule(Graphics2D g, float left, float right, float y) {
        g.setColor(new Color(122, 100, 70, 150));
        g.setStroke(new BasicStroke(1f));
        float mid = (left + right) / 2;
        g.draw(new Line2D.Float(left, y, mid - 12, y));
        g.draw(new Line2D.Float(mid + 12, y, right, y));
        Path2D diamond = new Path2D.Float();
        diamond.moveTo(mid, y - 5);
        diamond.lineTo(mid + 5, y);
        diamond.lineTo(mid, y + 5);
        diamond.lineTo(mid - 5, y);
        diamond.closePath();
        g.fill(diamond);
    }

    /**
     * The academy's emblem: a crescent moon holding a four pointed star.
     *
     * @param cx centre x
     * @param cy centre y
     * @param r  radius of the moon
     * @return the emblem
     */
    static Area emblem(double cx, double cy, double r) {
        Area moon = new Area(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        moon.subtract(new Area(new Ellipse2D.Double(cx - r * .55, cy - r * 1.12, 2 * r * .98, 2 * r * .98)));
        double sx = cx + r * .32;
        double sy = cy + r * .05;
        double s = r * .5;
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 4;
            double d = i % 2 == 0 ? s : s * .26;
            if (i == 0)
                star.moveTo(sx + Math.cos(a) * d, sy + Math.sin(a) * d);
            else
                star.lineTo(sx + Math.cos(a) * d, sy + Math.sin(a) * d);
        }
        star.closePath();
        moon.add(new Area(star));
        return moon;
    }

    /**
     * An ink stamp with a double frame and uneven ink, drawn once at device resolution.
     *
     * @param text  the words of the stamp
     * @param color the ink
     * @param k     logical units to device pixels
     * @param seed  seed of the specks where the stamp did not touch
     * @return the stamp with a 4 unit margin
     */
    static BufferedImage stamp(String text, Color color, double k, long seed) {
        Font font = AcademyTheme.script(27);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        FontMetrics fm = pg.getFontMetrics(font);
        pg.dispose();
        float tw = fm.stringWidth(text);
        float sw = tw + 40;
        float sh = 56;
        BufferedImage img = image(sw + 8, sh + 8, k);
        Graphics2D g = graphics(img, k);
        g.translate(4, 4);
        g.setColor(color);
        g.setStroke(new BasicStroke(3.2f));
        g.draw(new RoundRectangle2D.Float(1.6f, 1.6f, sw - 3.2f, sh - 3.2f, 10, 10));
        g.setStroke(new BasicStroke(1.1f));
        g.draw(new RoundRectangle2D.Float(7, 7, sw - 14, sh - 14, 6, 6));
        g.setFont(font);
        g.drawString(text, (sw - tw) / 2, sh / 2 + (fm.getAscent() - fm.getDescent()) / 2f);
        // uneven ink: lighter towards one side, with specks where the stamp did not touch
        g.setComposite(AlphaComposite.DstOut);
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 0), sw, sh, new Color(0, 0, 0, 105)));
        g.fill(new Rectangle2D.Float(0, 0, sw, sh));
        Random r = new Random(seed);
        for (int i = 0; i < (int) (sw * sh / 26); i++) {
            float d = .6f + r.nextFloat() * 1.8f;
            g.setColor(new Color(0, 0, 0, 90 + r.nextInt(166)));
            g.fill(new Ellipse2D.Float(r.nextFloat() * sw, r.nextFloat() * sh, d, d));
        }
        g.dispose();
        return img;
    }

    /**
     * A red wax seal pressed with the academy's emblem, drawn once at device resolution.
     *
     * @param radius logical radius of the wax
     * @param k      logical units to device pixels
     * @return the seal, centred, with a margin for its shadow
     */
    static BufferedImage wax(float radius, double k) {
        float size = radius * 2 + 16;
        BufferedImage img = image(size, size, k);
        Graphics2D g = graphics(img, k);
        float c = size / 2;
        Random r = new Random(41);
        Path2D blob = new Path2D.Float();
        for (int a = 0; a <= 40; a++) {
            double ang = a * Math.PI * 2 / 40;
            double d = radius * (1 + .045 * Math.sin(ang * 5 + 1.3) + .035 * Math.sin(ang * 9) + (r.nextDouble() - .5) * .04);
            if (a == 0)
                blob.moveTo(c + Math.cos(ang) * d, c + Math.sin(ang) * d);
            else
                blob.lineTo(c + Math.cos(ang) * d, c + Math.sin(ang) * d);
        }
        blob.closePath();
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(0, 0, 0, 22));
            g.fill(AffineTransform.getTranslateInstance(1 + i * .5, 2 + i * .7).createTransformedShape(blob));
        }
        g.setPaint(new RadialGradientPaint(c - radius * .3f, c - radius * .35f, radius * 1.4f, new float[]{0, .6f, 1},
                new Color[]{new Color(0xC23A33), new Color(0x8E1F1F), new Color(0x5E1010)}));
        g.fill(blob);
        // the pressed hollow and its raised rim
        float inner = radius * .7f;
        g.setPaint(new RadialGradientPaint(c + inner * .25f, c + inner * .3f, inner * 1.3f, new float[]{0, 1},
                new Color[]{new Color(0x9A2621), new Color(0x6E1515)}));
        g.fill(new Ellipse2D.Float(c - inner, c - inner, inner * 2, inner * 2));
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(new Color(255, 160, 140, 90));
        g.draw(new Arc2D.Float(c - inner, c - inner, inner * 2, inner * 2, 200, 160, Arc2D.OPEN));
        g.setColor(new Color(40, 0, 0, 120));
        g.draw(new Arc2D.Float(c - inner, c - inner, inner * 2, inner * 2, 20, 160, Arc2D.OPEN));
        // the emblem stands up in relief: dark below right, light above left
        Area mark = emblem(c, c, inner * .62);
        g.setColor(new Color(40, 0, 0, 140));
        g.fill(mark.createTransformedArea(AffineTransform.getTranslateInstance(1.1, 1.3)));
        g.setColor(new Color(255, 170, 150, 120));
        g.fill(mark.createTransformedArea(AffineTransform.getTranslateInstance(-.8, -.9)));
        g.setColor(new Color(0xA82C25));
        g.fill(mark);
        // a glint of light on the wax
        g.setPaint(new RadialGradientPaint(c - radius * .45f, c - radius * .5f, radius * .4f, new float[]{0, 1},
                new Color[]{new Color(255, 230, 220, 110), new Color(255, 230, 220, 0)}));
        g.fill(new Ellipse2D.Float(c - radius, c - radius, radius, radius));
        g.dispose();
        return img;
    }

    /**
     * Draws a tick or a cross as if made with a pen.
     *
     * @param g  graphics
     * @param cx centre x
     * @param cy centre y
     * @param ok tick if true, cross if false
     */
    static void mark(Graphics2D g, float cx, float cy, boolean ok) {
        Graphics2D m = (Graphics2D) g.create();
        m.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        m.setColor(ok ? GREEN_INK : RED_INK);
        Path2D p = new Path2D.Float();
        if (ok) {
            p.moveTo(cx - 7, cy);
            p.quadTo(cx - 3, cy + 3, cx - 2, cy + 6);
            p.quadTo(cx + 2, cy - 3, cx + 9, cy - 9);
        } else {
            p.moveTo(cx - 6, cy - 6);
            p.quadTo(cx, cy - 1, cx + 7, cy + 7);
            p.moveTo(cx + 6, cy - 7);
            p.quadTo(cx - 1, cy + 1, cx - 6, cy + 7);
        }
        m.draw(p);
        m.dispose();
    }

    /**
     * A steel paper clip holding a sheet at its top edge.
     *
     * @param g graphics in sheet coordinates
     * @param x left of the clip
     * @param y top of the clip
     */
    static void paperClip(Graphics2D g, float x, float y) {
        Path2D wire = new Path2D.Float();
        wire.moveTo(x + 3, y + 40);
        wire.lineTo(x + 3, y + 7);
        wire.curveTo(x + 3, y - 1, x + 15, y - 1, x + 15, y + 7);
        wire.lineTo(x + 15, y + 50);
        wire.curveTo(x + 15, y + 57, x + 6, y + 57, x + 6, y + 50);
        wire.lineTo(x + 6, y + 13);
        wire.curveTo(x + 6, y + 9, x + 12, y + 9, x + 12, y + 13);
        wire.lineTo(x + 12, y + 40);
        Graphics2D c = (Graphics2D) g.create();
        c.rotate(Math.toRadians(-6), x + 9, y + 24);
        c.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.setColor(new Color(0, 0, 0, 70));
        c.translate(1.2, 1.8);
        c.draw(wire);
        c.translate(-1.2, -1.8);
        c.setPaint(new GradientPaint(x, y, new Color(0xC9D0D6), x + 15, y + 50, new Color(0x7D8791)));
        c.draw(wire);
        c.setStroke(new BasicStroke(.7f));
        c.setColor(new Color(255, 255, 255, 120));
        c.translate(-.5, -.5);
        c.draw(wire);
        c.dispose();
    }

    /** One line of writing at its place on a sheet. */
    static final class Ink {
        private final String text;
        private final Font font;
        private final Color color;
        private final float x;
        private final float y;

        /**
         * @param text  the words
         * @param font  the font
         * @param color the ink
         * @param x     left of the base line
         * @param y     the base line
         */
        Ink(String text, Font font, Color color, float x, float y) {
            this.text = text;
            this.font = font;
            this.color = color;
            this.x = x;
            this.y = y;
        }

        /**
         * @param g graphics in sheet coordinates
         */
        void draw(Graphics2D g) {
            g.setFont(font);
            g.setColor(color);
            g.drawString(text, x, y);
        }
    }

    /** The reading desk in the dark archive under a lamp; the still part is cached per size. */
    static final class Desk {
        private BufferedImage image;
        private String key;

        /**
         * @param g     graphics of the host component
         * @param w     width of the host
         * @param h     height of the host
         * @param focus the area under the lamp, in host coordinates
         * @param t     seconds for the light; 0 when motion is off
         */
        void paint(Graphics2D g, int w, int h, Rectangle focus, double t) {
            g.clip(new RoundRectangle2D.Float(0, 0, w, h, 22, 22));
            double ds = deviceScale(g);
            String k = w + "x" + h + "@" + ds;
            if (image == null || !k.equals(key)) {
                key = k;
                image = render(w, h, ds);
            }
            Graphics2D b = (Graphics2D) g.create();
            b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            b.scale(1 / ds, 1 / ds);
            b.drawImage(image, 0, 0, null);
            b.dispose();

            // moonlight from the high windows, drifting slowly; nested layers give the beams soft edges
            for (int i = 0; i < 3; i++) {
                double x0 = w * (.52 + .15 * i) + 46 * Math.sin(t * .13 + i * 1.7);
                double width = w * (.05 + .025 * i);
                float pulse = (float) (.65 + .35 * Math.sin(t * .45 + i * 2.1));
                for (int layer = 0; layer < 4; layer++) {
                    double inset = width * layer * .16;
                    Path2D shaft = new Path2D.Double();
                    shaft.moveTo(x0 + inset, 0);
                    shaft.lineTo(x0 + width - inset, 0);
                    shaft.lineTo(x0 + width - inset - h * .62, h);
                    shaft.lineTo(x0 + inset - h * .62, h);
                    shaft.closePath();
                    g.setPaint(new GradientPaint(0, 0, new Color(170, 215, 235, (int) (11 * pulse)), 0, h * .85f,
                            new Color(170, 215, 235, 0)));
                    g.fill(shaft);
                }
            }
            AcademyAtmosphere.lamp(g, focus.x + focus.width * .5f, focus.y + focus.height * .46f,
                    Math.max(120, focus.height * .95f), .9f, t);
            AcademyAtmosphere.dust(g, w, h, t, true);
        }

        private static BufferedImage render(int w, int h, double ds) {
            BufferedImage img = image(w, h, ds);
            Graphics2D g = graphics(img, ds);
            g.setColor(AcademyTheme.NIGHT);
            g.fillRect(0, 0, w, h);
            BufferedImage scene = AcademyArt.get(AcademyArt.ARCHIVE_INTERIOR);
            if (scene != null)
                AcademyArt.drawCover(g, scene, 0, 0, w, h, .5);
            g.setPaint(new GradientPaint(0, 0, new Color(8, 14, 26, 168), 0, h, new Color(8, 14, 26, 222)));
            g.fillRect(0, 0, w, h);
            // the edge of the reading desk
            float desk = h * .64f;
            g.setPaint(new GradientPaint(0, desk, new Color(42, 28, 18, 210), 0, h, new Color(20, 13, 9, 240)));
            g.fill(new Rectangle2D.Float(0, desk, w, h - desk));
            g.setColor(new Color(217, 175, 96, 60));
            g.fill(new Rectangle2D.Float(0, desk, w, 1.2f));
            Random r = new Random(7);
            for (int i = 0; i < 40; i++) {
                float y = desk + 6 + r.nextFloat() * (h - desk);
                g.setColor(new Color(90, 62, 38, 18 + r.nextInt(22)));
                g.draw(new QuadCurve2D.Float(0, y, w * r.nextFloat(), y + r.nextFloat() * 6 - 3, w,
                        y + r.nextFloat() * 4 - 2));
            }
            g.setPaint(new RadialGradientPaint(w / 2f, h * .45f, Math.max(w, h) * .75f, new float[]{.45f, 1},
                    new Color[]{new Color(5, 9, 18, 0), new Color(5, 9, 18, 200)}));
            g.fillRect(0, 0, w, h);
            g.dispose();
            return img;
        }
    }

    /**
     * A stamp or seal coming down on paper: it grows from a little above the paper, lands and sounds once.
     * If it is pressed while its sheet is not on screen, it waits and lands shortly after the sheet shows.
     */
    static final class Thump {
        private final long delayWhenShown;
        private final double seconds;
        private long started;
        private boolean pending;
        private boolean sounded = true;

        /**
         * @param delayWhenShown nanoseconds to wait after the sheet shows up before landing
         * @param seconds        duration of the landing
         */
        Thump(long delayWhenShown, double seconds) {
            this.delayWhenShown = delayWhenShown;
            this.seconds = seconds;
        }

        /**
         * @param host  the component that shows the mark
         * @param delay nanoseconds to wait if the host is on screen already
         */
        void press(JComponent host, long delay) {
            sounded = true;
            pending = false;
            started = 0;
            if (!animated())
                return;
            if (host.isShowing()) {
                started = System.nanoTime() + delay;
                sounded = false;
            } else
                pending = true;
        }

        /** Call when the host has become visible. */
        void shown() {
            if (pending) {
                pending = false;
                started = System.nanoTime() + delayWhenShown;
                sounded = false;
            }
        }

        /** Call on every animation tick; plays the sound when the mark lands. */
        void tick() {
            if (!sounded && started != 0 && System.nanoTime() >= started) {
                sounded = true;
                AcademySound.cue("seal");
            }
        }

        /**
         * @return -1 while the mark must not be seen yet, otherwise the landing progress from 0 to 1
         */
        double progress() {
            if (pending)
                return -1;
            if (started == 0)
                return 1;
            double since = (System.nanoTime() - started) / 1e9;
            return since < 0 ? -1 : Math.min(1, since / seconds);
        }

        /**
         * Draws the mark centred at the given place.
         *
         * @param g     graphics
         * @param img   the mark rendered at device resolution
         * @param x     centre x
         * @param y     centre y
         * @param angle rotation in degrees
         * @param ds    the device scale of the image
         */
        void draw(Graphics2D g, BufferedImage img, double x, double y, double angle, double ds) {
            double e = progress();
            if (e < 0)
                return;
            Graphics2D s = (Graphics2D) g.create();
            s.translate(x, y);
            s.rotate(Math.toRadians(angle));
            double scale = 1 + .75 * (1 - e) * (1 - e);
            s.scale(scale / ds, scale / ds);
            s.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, e * 1.7)));
            s.drawImage(img, -img.getWidth() / 2, -img.getHeight() / 2, null);
            s.dispose();
        }
    }
}
