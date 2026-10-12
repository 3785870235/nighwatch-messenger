/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The result page: the academy's reply lies on the reading desk under the lamp, the test slip clipped
 * beside it. A new reply slides in once the page can really be seen, not while a scene or a dissolve
 * covers it; then the wax seal or the red stamp comes down. Everything written comes from
 * {@link ResultLetter}; this class decides nothing.
 */
final class AcademyLetter extends JPanel {
    private static final float LW = 500;
    private static final float LH_MIN = 560;
    private static final float GAP = 30;
    private static final float SLIP_TOP = 30;
    private static final float MARGIN = 26;
    private static final float PAD = 50;
    private static final float BODY = 16.5f;
    private static final float LEADING = 27.5f;
    private static final float TABLE_TOP = 132;
    private static final float ROW = 44;
    // with several lamps every situation fills one row per lamp, so the rows are lower
    private static final float LAMP_ROW = 34;
    private static final float INDEX_W = 26;
    private static final float STATION_W = 84;
    private static final float LAMP_W = 72;
    private static final float RULE_W = 72;
    private static final float COPY_W = 80;
    private static final float MARK_W = 32;
    private static final double SLIP_ANGLE = 1.3;
    private static final double ARRIVE_SECONDS = .5;
    private static final double SLIP_LAG = .16;
    private static final long MARK_DELAY = 700000000L;
    private static final double THUMP_SECONDS = .24;
    private static final float WAX_RADIUS = 30;
    private static final Color RED_WASH = new Color(162, 59, 46, 20);
    private static final Color PEN_RED = new Color(162, 59, 46, 215);

    private final AcademyPaper.Desk desk = new AcademyPaper.Desk();
    private final AcademyPaper.Thump thump = new AcademyPaper.Thump(MARK_DELAY, THUMP_SECONDS);
    private final Spread spread = new Spread();
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private ResultLetter letter;
    private Page page;
    private Slip slip;
    private boolean waiting;
    private long arrived;

    /**
     * @param first the letter lying on the desk at the start
     */
    AcademyLetter(ResultLetter first) {
        super(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(AcademyTheme.pad(10, 22, 12, 22));
        add(spread, BorderLayout.CENTER);
        AcademyAtmosphere.watch(this, this::tick);
        deliver(first);
    }

    /**
     * Puts a letter on the desk. The same letter again changes nothing; a new one arrives as soon as the
     * page is on screen and uncovered.
     *
     * @param next the letter
     */
    void deliver(ResultLetter next) {
        if (next.equals(letter))
            return;
        letter = next;
        page = Page.of(next);
        slip = next.getRows().isEmpty() ? null : new Slip(next);
        arrived = 0;
        waiting = AcademyPaper.animated();
        if (!waiting)
            thump.press(this, 0);
        spread.repaint();
    }

    /**
     * @return the letter on the desk
     */
    ResultLetter letter() {
        return letter;
    }

    /**
     * @return the letter on the desk as plain text
     */
    String text() {
        return letter.text();
    }

    /**
     * @return true while the new letter waits for the page to be uncovered
     */
    boolean isWaiting() {
        return waiting;
    }

    private void tick() {
        if (waiting && isShowing() && !AcademyPaper.covered(this)) {
            waiting = false;
            arrived = System.nanoTime();
            AcademySound.cue("page");
            if (letter.getMark() != ResultLetter.Mark.NONE)
                thump.press(this, MARK_DELAY);
        }
        thump.tick();
    }

    /** @return the eased progress of a sheet sliding in, 1 once it rests */
    private double arrival(double lag) {
        if (arrived == 0 || !AcademyPaper.animated())
            return 1;
        double p = Math.max(0, Math.min(1, ((System.nanoTime() - arrived) / 1e9 - lag) / ARRIVE_SECONDS));
        return 1 - (1 - p) * (1 - p) * (1 - p);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double t = AcademyAtmosphere.motion() ? System.nanoTime() / 1e9 : 0;
        Rectangle area = SwingUtilities.convertRectangle(spread.getParent(), spread.getBounds(), this);
        desk.paint(g, getWidth(), getHeight(), area, t);
        g.dispose();
    }

    private BufferedImage cached(String key, Supplier<BufferedImage> make) {
        BufferedImage img = cache.get(key);
        if (img == null) {
            if (cache.size() > 12)
                cache.clear();
            img = make.get();
            cache.put(key, img);
        }
        return img;
    }

    private BufferedImage letterImage(double k) {
        ResultLetter l = letter;
        Page p = page;
        return cached("letter:" + l.getState() + ":" + l.text().hashCode() + ":" + Math.round(k * 1000), () -> {
            BufferedImage img = AcademyPaper.image(LW + 2 * MARGIN, p.height + 2 * MARGIN, k);
            Graphics2D g = AcademyPaper.graphics(img, k);
            g.translate(MARGIN, MARGIN);
            Random r = new Random(l.getState().ordinal() * 131L + 17);
            Path2D shape = AcademyPaper.edge(r, LW, p.height, -1);
            AcademyPaper.sheet(g, shape, LW, p.height, r, true);
            g.setColor(new Color(138, 90, 46, 210));
            g.fill(AcademyPaper.emblem(LW / 2, 48, 15));
            AcademyPaper.rule(g, PAD, LW - PAD, 108);
            for (AcademyPaper.Ink i : p.ink)
                i.draw(g);
            g.dispose();
            return img;
        });
    }

    private BufferedImage slipImage(double k) {
        ResultLetter l = letter;
        Slip s = slip;
        return cached("slip:" + l.text().hashCode() + ":" + Math.round(k * 1000), () -> {
            BufferedImage img = AcademyPaper.image(s.width + 2 * MARGIN, s.height + 2 * MARGIN, k);
            Graphics2D g = AcademyPaper.graphics(img, k);
            g.translate(MARGIN, MARGIN);
            // the slip lies a little askew; the turn is drawn into the image so that it still rests on whole pixels
            g.rotate(Math.toRadians(SLIP_ANGLE), s.width / 2, s.height / 2);
            s.draw(g, l);
            g.dispose();
            return img;
        });
    }

    private BufferedImage markImage(double k) {
        boolean seal = letter.getMark() == ResultLetter.Mark.SEAL;
        return cached("mark:" + seal + ":" + Math.round(k * 1000), () -> seal ? AcademyPaper.wax(WAX_RADIUS, k)
                : AcademyPaper.stamp(ResultLetter.RETURNED_STAMP, AcademyPaper.RED_INK, k, 29));
    }

    private static float width(Graphics2D g, Font font, String text) {
        return AcademyPaper.textWidth(g, font, text);
    }

    /** The two sheets on the desk, sliding in and resting on whole device pixels. */
    private final class Spread extends JComponent {
        private Spread() {
            setOpaque(false);
            setPreferredSize(new Dimension(900, 520));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (letter == null)
                return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double ds = AcademyPaper.deviceScale(g);
            float w = slip == null ? LW : LW + GAP + slip.width;
            float h = slip == null ? page.height : Math.max(page.height, SLIP_TOP + slip.height);
            float u = (float) Math.min(Math.min(getWidth() * .96 / w, (getHeight() - 6) / h), 1.15);
            if (w * u < 80 || waiting) {
                g.dispose();
                return;
            }
            double ox = Math.round((getWidth() - w * u) / 2 * ds) / ds;
            double oy = Math.round((getHeight() - h * u) / 2 * ds) / ds;
            double k = u * ds;
            if (slip != null) {
                double b = arrival(SLIP_LAG);
                sheet(g, slipImage(k), ox + (LW + GAP) * u + (1 - b) * 70 * u, oy + SLIP_TOP * u, u, ds, b);
            }
            double a = arrival(0);
            double ly = oy + (1 - a) * 40 * u;
            sheet(g, letterImage(k), ox, ly, u, ds, a);
            if (letter.getMark() == ResultLetter.Mark.SEAL)
                thump.draw(g, markImage(k), ox + (page.signLeft - 48) * u, ly + (page.signY - 8) * u, -8, ds);
            else if (letter.getMark() == ResultLetter.Mark.RETURNED)
                thump.draw(g, markImage(k), ox + (LW - PAD - 70) * u, ly + 156 * u, -8, ds);
            double t = AcademyAtmosphere.motion() ? System.nanoTime() / 1e9 : 0;
            if (letter.getState() == ResultLetter.State.PENDING)
                dots(g, ox + page.dotsX * u, ly + page.dotsY * u, u, t, a);
            float glow = (float) (.5 + .5 * Math.sin(t * 1.3));
            g.setPaint(new RadialGradientPaint((float) (ox + LW * u * .4), (float) (oy + h * u * .2), w * u * .7f,
                    new float[]{0, 1}, new Color[]{new Color(255, 196, 110, (int) (12 + 18 * glow)), new Color(255, 196, 110, 0)}));
            g.fill(new Rectangle2D.Double(ox - 20, oy - 20, w * u + 40, h * u + 40));
            g.dispose();
        }

        private void sheet(Graphics2D g, BufferedImage img, double x, double y, float u, double ds, double alpha) {
            Graphics2D s = (Graphics2D) g.create();
            if (alpha < 1)
                s.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, alpha)));
            AcademyPaper.drawSnapped(s, img, x - MARGIN * u, y - MARGIN * u, ds, alpha >= 1);
            s.dispose();
        }

        /** Three ink dots after "检验中", filling in one after another while the copy is tried. */
        private void dots(Graphics2D g, double x, double y, float u, double t, double alpha) {
            for (int i = 0; i < 3; i++) {
                double on = t == 0 ? 1 : .25 + .75 * Math.max(0, Math.sin(t * 4 - i * .8));
                g.setColor(new Color(122, 100, 70, (int) (230 * on * alpha)));
                double d = 3.4 * u;
                g.fill(new Ellipse2D.Double(x + (4 + i * 8) * u - d / 2, y - 4 * u - d / 2, d, d));
            }
        }
    }

    /** The letter laid out in sheet units; it grows downwards with its text. */
    private static final class Page {
        private final List<AcademyPaper.Ink> ink = new ArrayList<>();
        private float height;
        private float dotsX;
        private float dotsY;
        private float signLeft;
        private float signY;

        private static Page of(ResultLetter l) {
            Graphics2D g = AcademyPaper.probe();
            Page p = new Page();
            float right = LW - PAD;
            Font head = AcademyTheme.script(22);
            p.ink.add(new AcademyPaper.Ink(ResultLetter.SENDER, head, AcademyPaper.SEPIA,
                    LW / 2 - width(g, head, ResultLetter.SENDER) / 2, 92));
            Font small = AcademyTheme.font(Font.PLAIN, 13.5f);
            String subject = "事由：" + l.getSubject() + " · " + l.getStatus().replace("…", "");
            p.ink.add(new AcademyPaper.Ink(subject, small, AcademyPaper.FADED, PAD, 138));
            p.dotsX = PAD + width(g, small, subject);
            p.dotsY = 138;

            float y = 180;
            p.ink.add(new AcademyPaper.Ink(ResultLetter.SALUTATION, AcademyTheme.script(22), AcademyPaper.INK, PAD, y));
            y += 40;
            Font body = AcademyTheme.font(Font.PLAIN, BODY);
            FontMetrics fm = g.getFontMetrics(body);
            List<String> paragraphs = l.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                String para = paragraphs.get(i);
                boolean item = para.startsWith("· ");
                float x = item ? PAD + 18 : PAD;
                if (item)
                    p.ink.add(new AcademyPaper.Ink("·", body, AcademyPaper.INK, PAD + 5, y));
                // a letter indents its paragraphs by two characters; a listed point hangs after its dot
                String text = item ? para.substring(2) : "　　" + para;
                for (String line : AcademySpeech.breakLines(text, fm, (int) (right - x))) {
                    p.ink.add(new AcademyPaper.Ink(line, body, AcademyPaper.INK, x, y));
                    y += LEADING;
                }
                // listed points stay close together; after the list the letter goes on at paragraph distance
                boolean nextItem = i + 1 < paragraphs.size() && paragraphs.get(i + 1).startsWith("· ");
                y += item && nextItem ? 2 : 9;
            }
            // only an answer closes with 此复; a note while waiting just ends
            if (l.isReply())
                p.ink.add(new AcademyPaper.Ink("　　此复。", body, AcademyPaper.INK_SOFT, PAD, y + 4));
            Font sign = AcademyTheme.script(21);
            p.signLeft = right - width(g, sign, ResultLetter.SENDER);
            p.signY = Math.max(y + 60, LH_MIN - 84);
            p.ink.add(new AcademyPaper.Ink(ResultLetter.SENDER, sign, AcademyPaper.INK, p.signLeft, p.signY));
            p.height = p.signY + 84;
            g.dispose();
            return p;
        }
    }

    /** The test slip: one row for every situation the copy was tried with, or with several lamps one per lamp. */
    private static final class Slip {
        private final float width;
        private final float height;
        private final float table;
        private final boolean lamps;
        private final float row;

        private Slip(ResultLetter l) {
            int stations = l.getRows().get(0).getStations().size();
            lamps = !l.getRows().get(0).getLamp().isEmpty();
            row = lamps ? LAMP_ROW : ROW;
            table = INDEX_W + stations * STATION_W + (lamps ? LAMP_W : 0) + RULE_W + COPY_W + MARK_W;
            width = Math.max(380, table + 44);
            height = TABLE_TOP + l.getRows().size() * row + 64;
        }

        private void draw(Graphics2D g, ResultLetter l) {
            List<ResultLetter.Row> rows = l.getRows();
            Random r = new Random(rows.size() * 97L + 3);
            Path2D shape = AcademyPaper.edge(r, width, height, 0);
            AcademyPaper.sheet(g, shape, width, height, r, true);

            Font title = AcademyTheme.script(26);
            centred(g, "试讯单", title, AcademyPaper.INK, width / 2, 54);
            centred(g, l.getSubject() + " · 副本逐一试讯", AcademyTheme.font(Font.PLAIN, 12.5f), AcademyPaper.FADED, width / 2, 78);
            AcademyPaper.rule(g, 30, width - 30, 92);

            float x0 = (width - table) / 2;
            int n = rows.get(0).getStations().size();
            float[] stationX = new float[n];
            float x = x0;
            float indexX = x + INDEX_W / 2;
            x += INDEX_W;
            for (int i = 0; i < n; i++) {
                stationX[i] = x + STATION_W / 2;
                x += STATION_W;
            }
            float lampX = x + LAMP_W / 2;
            float lampLeft = x;
            if (lamps)
                x += LAMP_W;
            float ruleX = x + RULE_W / 2;
            x += RULE_W;
            float copyX = x + COPY_W / 2;
            x += COPY_W;
            float markX = x + MARK_W / 2;

            Font head = AcademyTheme.font(Font.PLAIN, 13.5f);
            for (int i = 0; i < n; i++)
                centred(g, rows.get(0).getStations().get(i), head, AcademyPaper.SEPIA, stationX[i], 120);
            if (lamps)
                centred(g, "灯", head, AcademyPaper.SEPIA, lampX, 120);
            centred(g, l.getRuleName(), head, AcademyPaper.SEPIA, ruleX, 120);
            centred(g, "副本", head, AcademyPaper.SEPIA, copyX, 120);
            g.setColor(new Color(122, 100, 70, 130));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Float(x0 - 4, TABLE_TOP, x0 + table + 4, TABLE_TOP));

            Font cell = AcademyTheme.font(Font.PLAIN, 15.5f);
            Font index = AcademyTheme.font(Font.PLAIN, 13f);
            for (int i = 0; i < rows.size(); i++) {
                ResultLetter.Row line = rows.get(i);
                float top = TABLE_TOP + i * row;
                float base = top + row / 2 + 6;
                if (!line.isPassed()) {
                    g.setColor(RED_WASH);
                    g.fill(new RoundRectangle2D.Float(lamps ? lampLeft - 6 : x0 - 6, top + 4,
                            (lamps ? x0 + table - lampLeft : table) + 12, row - 8, 8, 8));
                }
                boolean first = i == 0 || rows.get(i - 1).getSituation() != line.getSituation();
                boolean last = i + 1 == rows.size() || rows.get(i + 1).getSituation() != line.getSituation();
                if (first) {
                    // the situation is written once, in the middle of its rows
                    int size = 1;
                    while (i + size < rows.size() && rows.get(i + size).getSituation() == line.getSituation())
                        size++;
                    float middle = top + size * row / 2 + 6;
                    centred(g, ResultLetter.ordinal(line.getSituation()), index, AcademyPaper.FADED, indexX, middle);
                    for (int c = 0; c < n; c++)
                        centred(g, line.getCells().get(c), cell, AcademyPaper.INK, stationX[c], middle);
                }
                if (lamps)
                    centred(g, line.getLamp(), cell, AcademyPaper.INK, lampX, base);
                centred(g, line.getExpected(), cell, AcademyPaper.INK, ruleX, base);
                Color copy = line.isSilent() ? AcademyPaper.FADED : line.isPassed() ? AcademyPaper.INK : AcademyPaper.RED_INK;
                float cw = centred(g, line.getActual(), cell, copy, copyX, base);
                if (!line.isPassed())
                    ring(g, copyX, top + row / 2 + 1, cw / 2 + 12, Math.min(15, row / 2 - 3), new Random(i * 31L + 5));
                AcademyPaper.mark(g, markX, top + row / 2, line.isPassed());
                g.setColor(new Color(122, 100, 70, last ? 80 : 45));
                g.setStroke(new BasicStroke(.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 3}, 0));
                g.draw(new Line2D.Float(last ? x0 : lampLeft, top + row, x0 + table, top + row));
            }
            String rule = l.getRuleName();
            int total = ResultLetter.situations(rows);
            int failed = ResultLetter.situations(rows, false);
            int silent = ResultLetter.situations(rows, true);
            String tally = failed == 0 ? ResultLetter.count(total) + "种情形全部合乎" + rule
                    : "合乎" + rule + ResultLetter.count(total - failed) + "种 · 不合" + ResultLetter.count(failed) + "种"
                    + (silent > 0 ? "（其中无讯" + ResultLetter.count(silent) + "种）" : "");
            centred(g, tally, AcademyTheme.font(Font.PLAIN, 13f), AcademyPaper.FADED, width / 2, TABLE_TOP + rows.size() * row + 34);
            AcademyPaper.paperClip(g, 34, -16);
        }

        private static float centred(Graphics2D g, String text, Font font, Color color, float cx, float y) {
            return AcademyPaper.centred(g, text, font, color, cx, y);
        }

        /** A quick ring of red pen around a word; its ends overshoot instead of closing neatly. */
        private static void ring(Graphics2D g, float cx, float cy, float rx, float ry, Random r) {
            Path2D p = new Path2D.Float();
            double start = Math.toRadians(195 + r.nextInt(40));
            int steps = 48;
            for (int i = 0; i <= steps; i++) {
                double f = i / (double) steps;
                double a = start + Math.PI * 2 * 1.1 * f;
                double grow = 1 + .09 * f + (r.nextDouble() - .5) * .015;
                double px = cx + Math.cos(a) * rx * grow;
                double py = cy + Math.sin(a) * ry * grow;
                if (i == 0)
                    p.moveTo(px, py);
                else
                    p.lineTo(px, py);
            }
            g.setColor(PEN_RED);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(p);
        }
    }
}
