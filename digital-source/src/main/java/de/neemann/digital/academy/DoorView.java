/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The archive door scene. Only consumes readings; never influences simulation or validation.
 * Animated door leaves are driven only by valid readings. Animation never calculates outputs.
 * "Not yet observed", "not running" and invalid readings have their own icon and text,
 * so they can never be mistaken for a valid 0.
 */
public final class DoorView extends JComponent {
    private boolean minimal;

    void minimal(boolean value) {
        minimal = value;
    }
    private Reading reading = Reading.NOT_RUNNING;
    private String source = "";
    private String hint = "";
    private boolean unobserved = true;

    /** Which physical device this view renders. */
    public enum Scene { DOOR, LAMP }
    private Scene scene = Scene.DOOR;
    /**
     * Switch the rendered device before the first {@link #show} to avoid a stale frame.
     *
     * @param s the scene to render
     */
    public void setScene(Scene s) {
        scene = s;
    }

    private String lampScene = "report_light";
    private String lampTag = "岗位 A";

    /**
     * Selects the lamp drawn in the {@link Scene#LAMP} scene.
     *
     * @param scene the scene key of the level: report_light, reminder_light with the messenger's
     *              bell, or registry_desk with the acceptance lamp fed by both posts
     * @param tag   the plate under the lamp, e.g. "岗位 A · 报告灯"
     */
    public void setLamp(String scene, String tag) {
        this.lampScene = scene;
        this.lampTag = tag;
        this.lampTags = null;
        repaint();
    }

    private String[] lampTags;
    private String[] lampStates;
    private double[] glowFrom = new double[0];
    private double[] glowTarget = new double[0];
    private double[] glow = new double[0];

    /**
     * Selects several lamps that answer together, e.g. the high and the low star lamp. The reading is
     * then the packed value of all lamps, the first lamp in the highest bit.
     *
     * @param scene  the scene key of the level
     * @param tags   the plates under the lamps, first lamp first
     * @param states the state text for every packed value, e.g. "只亮低灯" for 1
     */
    public void setLamps(String scene, String[] tags, String[] states) {
        this.lampScene = scene;
        this.lampTags = tags.clone();
        this.lampStates = states.clone();
        glowFrom = new double[tags.length];
        glowTarget = new double[tags.length];
        glow = new double[tags.length];
        repaint();
    }

    private boolean multi() {
        return scene == Scene.LAMP && lampTags != null;
    }

    private boolean bit(long value, int lamp) {
        return ((value >> (lampTags.length - 1 - lamp)) & 1) != 0;
    }

    private double openness;
    private double from;
    private double target;
    private long started;
    private final Timer animation;

    /**
     * Creates a new door view
     */
    public DoorView() {
        setPreferredSize(new Dimension(640, 330));
        setMinimumSize(new Dimension(260, 170));
        animation = AcademyAtmosphere.watch(this, this::advance);
    }

    /**
     * Shows a reading. Must be called on the EDT.
     *
     * @param reading the reading of the bound output
     * @param source  description of the driving circuit
     */
    public void show(Reading reading, String source) {
        show(reading, source, "");
    }

    /**
     * Shows a reading. Must be called on the EDT.
     *
     * @param reading the reading of the bound output
     * @param source  description of the driving circuit
     * @param hint    an additional line, maybe empty
     */
    public void show(Reading reading, String source, String hint) {
        if (unobserved || reading.getKind() != this.reading.getKind() || reading.getValue() != this.reading.getValue()
                || !source.equals(this.source) || !hint.equals(this.hint)) {
            this.reading = reading;
            this.source = source;
            this.hint = hint;
            this.unobserved = false;
            if (reading.isValid()) {
                advance();
                if (multi()) {
                    boolean changed = false;
                    boolean rising = false;
                    for (int i = 0; i < lampTags.length; i++) {
                        double next = bit(reading.getValue(), i) ? 1 : 0;
                        if (next != glowTarget[i]) {
                            changed = true;
                            rising |= next == 1;
                        }
                    }
                    if (changed) {
                        for (int i = 0; i < lampTags.length; i++) {
                            glowFrom[i] = glow[i];
                            glowTarget[i] = bit(reading.getValue(), i) ? 1 : 0;
                        }
                        from = openness;
                        target = reading.isTrue() ? 1 : 0;
                        started = System.nanoTime();
                        AcademySound.cue(rising ? "open" : "close");
                    }
                } else {
                    double next = reading.isTrue() ? 1 : 0;
                    if (next != target) {
                        from = openness;
                        target = next;
                        started = System.nanoTime();
                        AcademySound.cue(next == 1 ? "open" : "close");
                    }
                }
                if (!AcademyAtmosphere.motion()) {
                    openness = target;
                    System.arraycopy(glowTarget, 0, glow, 0, glow.length);
                }
            }
            setToolTipText(getDetailText());
            repaint();
        }
    }

    /**
     * Shows that the response for the current inputs is not known yet.
     *
     * @param source description of the device
     * @param hint   what to do to learn the response
     */
    public void showUnobserved(String source, String hint) {
        if (!unobserved || !source.equals(this.source) || !hint.equals(this.hint)) {
            this.reading = Reading.NOT_RUNNING;
            this.source = source;
            this.hint = hint;
            this.unobserved = true;
            setToolTipText(hint);
            repaint();
        }
    }

    /**
     * @return the reading currently shown; NOT_RUNNING while unobserved
     */
    public Reading getReading() {
        return reading;
    }

    private void advance() {
        double t = AcademyAtmosphere.motion() ? Math.min(1, (System.nanoTime() - started) / 850000000.0) : 1;
        double e = t * t * (3 - 2 * t);
        openness = from + (target - from) * e;
        for (int i = 0; i < glow.length; i++)
            glow[i] = glowFrom[i] + (glowTarget[i] - glowFrom[i]) * e;
    }

    double openness() {
        advance();
        return openness;
    }

    /**
     * @param lamp the lamp, the first lamp is 0
     * @return the animated glow of that lamp; only used with several lamps
     */
    double glow(int lamp) {
        advance();
        return glow[lamp];
    }

    void stopAnimation() {
        animation.stop();
    }

    boolean isAnimating() {
        return animation.isRunning();
    }

    /**
     * @return true if the door shows the "not yet observed" state
     */
    public boolean isUnobserved() {
        return unobserved;
    }

    /**
     * @return the text describing the current door state
     */
    public String getStateText() {
        if (unobserved)
            return "尚未观察";
        switch (reading.getKind()) {
            case VALUE:
                if (multi()) {
                    int v = (int) reading.getValue();
                    return v >= 0 && v < lampStates.length ? lampStates[v] : "灯的回应 " + v;
                }
                if (scene == Scene.LAMP)
                    return reading.isTrue() ? "灯亮起" : "灯保持暗";
                return reading.isTrue() ? "门已开启" : "门保持关闭";
            case NOT_RUNNING:
                return "符文没有回应";
            case HIGH_Z:
                return "许可印没有收到回应";
            case MISSING:
                return "术式里没有许可印";
            default:
                return "符文回应异常";
        }
    }

    /**
     * @return the second line explaining the state
     */
    public String getDetailText() {
        if (unobserved)
            return hint.isEmpty() ? (scene == Scene.LAMP ? "灯的回应还不知道" : "门的回应还不知道") : hint;
        String d;
        switch (reading.getKind()) {
            case VALUE:
                if (multi()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < lampTags.length; i++)
                        sb.append(i == 0 ? "" : " · ").append(lampTags[i]).append(" = ")
                                .append(bit(reading.getValue(), i) ? "1" : "0");
                    d = sb.toString();
                } else
                    d = (scene == Scene.LAMP ? "灯亮 = " : "门开 = ") + (reading.isTrue() ? "1" : "0");
                break;
            case NOT_RUNNING:
                d = "符文什么也没传来，不是 0";
                break;
            case HIGH_Z:
                d = "无讯：不是 0，检查许可印的讯线是否接好";
                break;
            default:
                d = reading + "（不是 0）";
                break;
        }
        return hint.isEmpty() ? d : d + " · " + hint;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, 18, 18));

        boolean open = !unobserved && reading.isTrue();
        boolean valid = !unobserved && reading.isValid();
        advance();
        if (scene == Scene.LAMP) {
            // only a valid reading may light the lamp; the veil below keeps an unknown answer from passing as a dark 0
            if (multi()) {
                double[] glows = new double[glow.length];
                if (valid)
                    System.arraycopy(glow, 0, glows, 0, glow.length);
                AcademyLampScene.paintDevice(g2, w, h, lampScene, glows, System.nanoTime() / 1e9, lampTags);
            } else
                AcademyLampScene.paintDevice(g2, w, h, lampScene, valid ? openness : 0, System.nanoTime() / 1e9, lampTag);
        } else {
            BufferedImage img = AcademyArt.get(AcademyArt.ARCHIVE_INTERIOR);
            BufferedImage closed = AcademyArt.get(AcademyArt.ARCHIVE_CLOSED);
            if (img != null && closed != null)
                paintDoor(g2, img, closed, w, h, openness);
            else
                paintFallback(g2, w, h, open);
        }
        double time = System.nanoTime() / 1000000000.0;
        if (scene == Scene.LAMP) {
            // no ambient atmosphere lamps; lamp itself is the subject
            AcademyAtmosphere.dust(g2, w, h, time, false);
        } else {
            AcademyAtmosphere.lamp(g2, w * 0.30f, h * 0.3f, w * 0.13f, 0.55f, time);
            AcademyAtmosphere.lamp(g2, w * 0.70f, h * 0.3f, w * 0.13f, 0.55f, time + 1);
            AcademyAtmosphere.dust(g2, w, h, time, true);
        }

        if (!valid) {
            // veil: the closed picture must not read as a valid 0
            g2.setColor(new Color(8, 14, 26, unobserved ? 120 : 165));
            g2.fillRect(0, 0, w, h);
        }

        if (h >= 260)
            // 好奇 before anything is observed, 困惑 over a reading that is no reading, 举翼 when it opens
            AcademyCharacters.spirit(g2, w - Math.min(100, w * 0.12), h * 0.47, Math.min(140, h * 0.30), time,
                    unobserved ? 6 : !valid ? 7 : open ? 2 : 0, AcademyPointer.local(this));

        boolean compact = h < 260;
        g2.setPaint(new GradientPaint(0, h * 0.45f, new Color(8, 14, 26, 0), 0, h, new Color(8, 14, 26, 235)));
        g2.fillRect(0, (int) (h * 0.45f), w, h - (int) (h * 0.45f));

        // source chip
        FontMetrics fm;
        if (!minimal) {
        g2.setFont(AcademyTheme.font(Font.BOLD, 14));
        fm = g2.getFontMetrics();
        int cw = fm.stringWidth(source) + 24;
        g2.setColor(new Color(10, 18, 32, 200));
        g2.fillRoundRect(12, 12, cw, 28, 14, 14);
        g2.setColor(AcademyTheme.GOLD_HI);
        g2.drawString(source, 24, 12 + 14 + fm.getAscent() / 2 - 2);
        }

        // state badge
        int r = compact ? 40 : 56;
        int bx = 16;
        int by = h - r - (compact ? 27 : 24);
        Color ring;
        String sym;
        if (unobserved) {
            ring = AcademyTheme.CYAN;
            sym = "?";
        } else if (valid) {
            ring = open ? AcademyTheme.GOLD_HI : AcademyTheme.IVORY;
            sym = open ? "1" : "0";
        } else if (reading.getKind() == Reading.Kind.NOT_RUNNING) {
            ring = AcademyTheme.MUTED;
            sym = "Ⅱ";
        } else {
            ring = AcademyTheme.ALERT;
            sym = "!";
        }
        g2.setColor(new Color(10, 18, 32, 230));
        g2.fillOval(bx, by, r, r);
        g2.setStroke(new BasicStroke(compact ? 2.5f : 3f));
        g2.setColor(ring);
        g2.drawOval(bx, by, r, r);
        if (valid && multi()) {
            // one dot per lamp, the high lamp a little higher; the badge never shows the packed number
            int n = lampTags.length;
            float d = r * 0.26f;
            float gap = r * 0.12f;
            float x0 = bx + (r - n * d - (n - 1) * gap) / 2f;
            for (int i = 0; i < n; i++) {
                float dx = x0 + i * (d + gap);
                float dy = by + (r - d) / 2f + (i - (n - 1) / 2f) * r * 0.10f;
                java.awt.geom.Ellipse2D dot = new java.awt.geom.Ellipse2D.Float(dx, dy, d, d);
                if (bit(reading.getValue(), i)) {
                    g2.setColor(AcademyTheme.GOLD_HI);
                    g2.fill(dot);
                } else {
                    g2.setColor(AcademyTheme.IVORY);
                    g2.setStroke(new BasicStroke(compact ? 1.6f : 2f));
                    g2.draw(dot);
                }
            }
        } else {
            g2.setFont(AcademyTheme.font(Font.BOLD, compact ? 22 : 30));
            fm = g2.getFontMetrics();
            g2.drawString(sym, bx + (r - fm.stringWidth(sym)) / 2, by + (r + fm.getAscent() - fm.getDescent()) / 2);
        }

        int tx = bx + r + 14;
        g2.setFont(AcademyTheme.font(Font.BOLD, compact ? 18 : 24));
        fm = g2.getFontMetrics();
        g2.setColor(ring == AcademyTheme.MUTED ? AcademyTheme.IVORY : ring);
        int line1 = by + (compact ? 4 : 8) + fm.getAscent() - 6;
        g2.drawString(getStateText(), tx, line1);
        g2.setFont(AcademyTheme.font(Font.PLAIN, compact ? 14 : 16));
        g2.setColor(AcademyTheme.IVORY);
        if (!minimal || reading.isValid())
            drawFitted(g2, getDetailText(), tx, line1 + (compact ? 22 : 28), w - tx - 12);
        g2.dispose();
    }

    private static void paintDoor(Graphics2D g, BufferedImage room, BufferedImage closed, int w, int h, double openness) {
        double scale = Math.max((double) w / room.getWidth(), (double) h / room.getHeight());
        Graphics2D scene = (Graphics2D) g.create();
        scene.translate((w - room.getWidth() * scale) / 2, (h - room.getHeight() * scale) / 2);
        scene.scale(scale, scale);
        scene.drawImage(room, 0, 0, null);
        double angle = openness * Math.toRadians(76);
        double compression = Math.cos(angle);
        double depth = 38 * Math.sin(angle);
        for (int side = 0; side < 2; side++) {
            double hinge = side == 0 ? 620 : 1052;
            double tip = hinge + (836 - hinge) * compression;
            double left = side == 0 ? 620 : 836;
            double right = side == 0 ? 836 : 1052;
            Path2D leaf = new Path2D.Double();
            if (side == 0) {
                leaf.moveTo(620, 758);
                leaf.lineTo(620, 270);
                leaf.curveTo(630, 179, 725, 128, 836, 127);
                leaf.lineTo(836, 758);
            } else {
                leaf.moveTo(836, 758);
                leaf.lineTo(836, 127);
                leaf.curveTo(947, 128, 1042, 179, 1052, 270);
                leaf.lineTo(1052, 758);
            }
            leaf.closePath();
            // Two affine triangles form a perspective quadrilateral around the fixed hinge.
            double[] sx = {left, right, right, left};
            double[] sy = {127, 127, 758, 758};
            double[] dx = side == 0 ? new double[]{hinge, tip, tip, hinge} : new double[]{tip, hinge, hinge, tip};
            double[] dy = side == 0 ? new double[]{127, 127 + depth, 758 - depth, 758}
                    : new double[]{127 + depth, 127, 758, 758 - depth};
            for (int n = 0; n < 2; n++) {
                int b = n == 0 ? 1 : 2;
                int c = n == 0 ? 2 : 3;
                AffineTransform source = triangle(sx[0], sy[0], sx[b], sy[b], sx[c], sy[c]);
                AffineTransform transform = triangle(dx[0], dy[0], dx[b], dy[b], dx[c], dy[c]);
                try {
                    transform.concatenate(source.createInverse());
                } catch (java.awt.geom.NoninvertibleTransformException ex) {
                    continue;
                }
                Path2D tri = new Path2D.Double();
                tri.moveTo(sx[0], sy[0]);
                tri.lineTo(sx[b], sy[b]);
                tri.lineTo(sx[c], sy[c]);
                tri.closePath();
                Graphics2D piece = (Graphics2D) scene.create();
                piece.clip(transform.createTransformedShape(leaf));
                piece.clip(transform.createTransformedShape(tri));
                piece.drawImage(closed, transform, null);
                piece.dispose();
            }
        }
        scene.dispose();
    }

    private static AffineTransform triangle(double ax, double ay, double bx, double by, double cx, double cy) {
        return new AffineTransform(bx - ax, by - ay, cx - ax, cy - ay, ax, ay);
    }

    private static void drawFitted(Graphics2D g2, String text, int x, int y, int maxWidth) {
        FontMetrics fm = g2.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            g2.drawString(text, x, y);
            return;
        }
        // break once at the best position, the second line gets the rest
        int cut = text.length();
        while (cut > 1 && fm.stringWidth(text.substring(0, cut)) > maxWidth)
            cut--;
        g2.drawString(text.substring(0, cut), x, y);
        String rest = text.substring(cut);
        while (rest.length() > 1 && fm.stringWidth(rest) > maxWidth)
            rest = rest.substring(0, rest.length() - 2) + "…";
        g2.drawString(rest, x, y + fm.getHeight());
    }

    private static void paintFallback(Graphics2D g2, int w, int h, boolean open) {
        g2.setPaint(new GradientPaint(0, 0, new Color(0x1A2C48), 0, h, new Color(0x0B1220)));
        g2.fillRect(0, 0, w, h);
        // stone blocks
        g2.setColor(new Color(0x22385A));
        for (int y = 0; y < h; y += 34)
            for (int x = (y / 34) % 2 * 40; x < w; x += 80)
                g2.drawRoundRect(x, y, 76, 30, 6, 6);
        int dw = Math.min(w / 3, (int) (h * 0.62));
        int dh = (int) (dw * 1.35);
        int dx = (w - dw) / 2;
        int dy = Math.max(10, (h - dh) / 2 - 10);
        g2.setColor(new Color(0x0A1322));
        g2.fillRoundRect(dx - 14, dy - 14, dw + 28, dh + 28, dw / 2, dw / 2);
        g2.setStroke(new BasicStroke(5f));
        g2.setColor(new Color(0xA8864A));
        g2.drawRoundRect(dx - 14, dy - 14, dw + 28, dh + 28, dw / 2, dw / 2);
        if (open) {
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(dx + dw / 2f, dy + dh / 2f), dw,
                    new float[]{0f, 1f}, new Color[]{new Color(0xFFE6A6), new Color(0x8A5A22)}));
            g2.fillRoundRect(dx, dy, dw, dh, dw / 3, dw / 3);
            g2.setColor(new Color(0x4B3620));
            g2.fillRect(dx, dy + dh / 6, dw / 7, dh - dh / 6);
            g2.fillRect(dx + dw - dw / 7, dy + dh / 6, dw / 7, dh - dh / 6);
        } else {
            g2.setColor(new Color(0x4B3620));
            g2.fillRoundRect(dx, dy, dw, dh, dw / 3, dw / 3);
            g2.setColor(new Color(0x2A1E12));
            g2.setStroke(new BasicStroke(3f));
            g2.drawLine(dx + dw / 2, dy + 4, dx + dw / 2, dy + dh);
            g2.setColor(new Color(0xD9AF60));
            g2.fillOval(dx + dw / 2 - 9, dy + dh / 2 - 9, 18, 18);
        }
    }
}
