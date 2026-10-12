/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The work notebook: the apprentice's own ledger lies open on the reading desk. Its leaf lists every set of
 * key seal reports with the guess and the door's answer, in the words of the archive (持钥印, 门开了) and not
 * as numbers; the note clipped beside it holds the spirit's hints and what was found. A row observed while
 * the notebook was closed is written in by hand once the notebook can be seen. Everything shown comes from
 * the real session; this class decides nothing.
 */
final class AcademyLedger extends JPanel {
    private static final float MARGIN = 26;
    private static final float PAD = 40;
    private static final float GUTTER = 10;
    private static final float GAP = 30;
    private static final float TABLE_TOP = 154;
    private static final float ROW = 50;
    private static final float INDEX_W = 34;
    private static final float STATION_W = 112;
    private static final float GUESS_W = 88;
    private static final float DOOR_W = 104;
    private static final float MARK_W = 36;
    private static final float NOTE_W = 380;
    private static final float NOTE_TOP = 26;
    private static final float NOTE_PAD = 30;
    private static final float BODY = 15.5f;
    private static final float LEADING = 25.5f;
    private static final double NOTE_ANGLE = -1.1;
    private static final long WRITE_DELAY = 250000000L;
    private static final long WRITE_STAGGER = 380000000L;
    private static final double WRITE_SECONDS = .75;
    private static final Color SEAL = new Color(0x9A4A2E);
    private static final Color PAPER = new Color(0xF4EAD0);
    private static final Color LAMP_WASH = new Color(232, 180, 90, 46);
    private static final Color PEN = new Color(74, 59, 51, 200);
    private static final Color PALE = new Color(122, 100, 70, 150);
    /** The door's answer of a set of reports that was not tried yet. */
    static final String UNOBSERVED = "尚未观察";

    private final List<String> stations;
    private final Words words;
    private final AcademyPaper.Desk desk = new AcademyPaper.Desk();
    private final Spread spread = new Spread();
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private Entry[] rows = new Entry[0];
    private int current = -1;
    private List<String> notes = Collections.emptyList();
    private String rowKey = "";
    private String noteKey = "";
    private Leaf leaf;
    private Note note;
    private boolean[] written;
    private long[] writing;
    private long cueAt;

    /**
     * @param stations the names of the stations, in input order
     */
    AcademyLedger(List<String> stations) {
        this(stations, "门");
    }

    /**
     * @param stations    the names of the stations, in input order
     * @param deviceShort the device that answers, "门" or "灯"
     */
    AcademyLedger(List<String> stations, String deviceShort) {
        this(stations, deviceShort, null, null);
    }

    /**
     * @param stations    the names of the stations, in input order
     * @param deviceShort the device that answers, "门" or "灯"
     * @param guesses     for a device of several lamps the written guess of every value, otherwise null
     * @param answers     for a device of several lamps the written answer of every value, otherwise null
     */
    AcademyLedger(List<String> stations, String deviceShort, List<String> guesses, List<String> answers) {
        super(new BorderLayout(0, 10));
        this.stations = new ArrayList<>(stations);
        this.words = new Words(deviceShort, guesses, answers);
        setOpaque(false);
        setBorder(AcademyTheme.pad(10, 22, 12, 22));
        add(spread, BorderLayout.CENTER);
        AcademyAtmosphere.watch(this, this::tick);
    }

    /**
     * Copies the records into the ledger. Rows that were already observed at the first call are taken as
     * written long ago; a row observed later is written in once the ledger is seen.
     *
     * @param session the real observations
     * @param current the set of reports the cards show now, -1 if no set is pointed at
     * @param notes   the paragraphs of the note, blank ones are left out
     */
    void update(ExperimentSession session, int current, List<String> notes) {
        int n = session.getCombinationCount();
        Entry[] next = new Entry[n];
        StringBuilder k = new StringBuilder();
        for (int c = 0; c < n; c++) {
            List<Boolean> in = new ArrayList<>(session.toInputs(c).values());
            boolean[] holds = new boolean[in.size()];
            for (int i = 0; i < holds.length; i++)
                holds[i] = in.get(i);
            Reading seen = session.getObserved(c);
            next[c] = new Entry(holds, session.getPrediction(c), Door.of(seen), seen == null ? 0 : seen.getValue());
            k.append(next[c]).append(';');
        }
        k.append(current);
        List<String> kept = new ArrayList<>();
        for (String s : notes)
            if (s != null && !s.trim().isEmpty())
                kept.add(s.trim());
        String rk = k.toString();
        String nk = String.join("\n\n\n", kept);
        if (rk.equals(rowKey) && nk.equals(noteKey))
            return;
        boolean first = written == null || written.length != n;
        if (first) {
            written = new boolean[n];
            writing = new long[n];
        }
        for (int c = 0; c < n; c++)
            if (next[c].door == Door.NONE) {
                written[c] = false;
                writing[c] = 0;
            } else if (first || !AcademyPaper.animated())
                written[c] = true;
        rows = next;
        this.current = current;
        this.notes = kept;
        rowKey = rk;
        noteKey = nk;
        leaf = new Leaf(stations.size(), n, current >= 0, words.wide());
        note = Note.of(kept);
        spread.repaint();
    }

    /**
     * @return the ledger as plain text, one line for each set of reports, then the note
     */
    String text() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < rows.length; c++) {
            Entry e = rows[c];
            for (int i = 0; i < e.holds.length; i++)
                sb.append(stations.get(i)).append(' ').append(held(e.holds[i])).append(" · ");
            if (e.guess != null)
                sb.append(words.guess(e.guess)).append(" · ");
            sb.append(words.answer(e.door, e.value));
            if (e.mark() != null)
                sb.append(e.mark() ? " √" : " ×");
            sb.append('\n');
        }
        sb.append(tally()).append('\n');
        for (String s : notes)
            sb.append(s).append('\n');
        return sb.toString();
    }

    /**
     * @return true while a newly observed row waits to be written or is being written
     */
    boolean isWriting() {
        if (written == null)
            return false;
        for (int c = 0; c < rows.length; c++)
            if (!written[c] && rows[c].door != Door.NONE)
                return true;
        return false;
    }

    private static String held(boolean held) {
        return held ? ExperimentGuide.HOLD : ExperimentGuide.EMPTY;
    }

    private String tally() {
        int seen = 0;
        int guesses = 0;
        int right = 0;
        for (Entry e : rows) {
            if (e.door != Door.NONE)
                seen++;
            if (e.guess != null)
                guesses++;
            if (Boolean.TRUE.equals(e.mark()))
                right++;
        }
        String s = seen == 0 ? "还没有记下任何一组"
                : seen == rows.length ? ResultLetter.count(seen) + "组都已记下"
                : "已记下" + ResultLetter.count(seen) + "组，还差" + ResultLetter.count(rows.length - seen) + "组";
        if (guesses == 0)
            return s;
        String hit = right == 0 ? "没猜中" : right < guesses ? "猜中" + ResultLetter.count(right) + "次"
                : guesses == 1 ? "猜中了" : "都猜中了";
        return s + " · 先猜" + ResultLetter.count(guesses) + "次，" + hit;
    }

    private void tick() {
        if (written == null)
            return;
        long now = System.nanoTime();
        boolean live = AcademyPaper.animated() && !AcademyPaper.covered(this);
        long at = now + WRITE_DELAY;
        for (int c = 0; c < rows.length; c++) {
            if (written[c] || rows[c].door == Door.NONE)
                continue;
            if (!AcademyPaper.animated()) {
                written[c] = true;
                writing[c] = 0;
            } else if (writing[c] == 0) {
                if (live) {
                    writing[c] = at;
                    if (cueAt == 0)
                        cueAt = at;
                    at += WRITE_STAGGER;
                }
            } else if (now - writing[c] > WRITE_SECONDS * 1e9) {
                written[c] = true;
                writing[c] = 0;
            }
        }
        if (cueAt != 0 && now >= cueAt) {
            cueAt = 0;
            AcademySound.cue("paper");
        }
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

    // hidden: the rows whose guess and answer are not written yet
    private BufferedImage leafImage(double k, long hidden) {
        Entry[] r = rows;
        Leaf l = leaf;
        int pointed = current;
        String tally = tally();
        return cached("leaf:" + rowKey.hashCode() + ":" + hidden + ":" + Math.round(k * 1000), () -> {
            BufferedImage img = AcademyPaper.image(l.width + 2 * MARGIN, l.height + 2 * MARGIN, k);
            Graphics2D g = AcademyPaper.graphics(img, k);
            g.translate(MARGIN, MARGIN);
            l.draw(g, r, pointed, hidden, stations, tally, words);
            g.dispose();
            return img;
        });
    }

    private BufferedImage noteImage(double k) {
        Note n = note;
        return cached("note:" + noteKey.hashCode() + ":" + Math.round(k * 1000), () -> {
            BufferedImage img = AcademyPaper.image(NOTE_W + 2 * MARGIN, n.height + 2 * MARGIN, k);
            Graphics2D g = AcademyPaper.graphics(img, k);
            g.translate(MARGIN, MARGIN);
            // the note lies a little askew; the turn is drawn into the image so that it still rests on whole pixels
            g.rotate(Math.toRadians(NOTE_ANGLE), NOTE_W / 2, n.height / 2);
            n.draw(g);
            g.dispose();
            return img;
        });
    }

    /** The open notebook and the note on the desk, resting on whole device pixels. */
    private final class Spread extends JComponent {
        private Spread() {
            setOpaque(false);
            setPreferredSize(new Dimension(900, 480));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (leaf == null)
                return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double ds = AcademyPaper.deviceScale(g);
            float w = leaf.width + GAP + NOTE_W;
            float h = Math.max(leaf.height, NOTE_TOP + note.height);
            float u = (float) Math.min(Math.min(getWidth() * .96 / w, (getHeight() - 6) / h), 1.15);
            if (w * u < 80) {
                g.dispose();
                return;
            }
            double ox = Math.round((getWidth() - w * u) / 2 * ds) / ds;
            double oy = Math.round((getHeight() - h * u) / 2 * ds) / ds;
            double k = u * ds;
            AcademyPaper.drawSnapped(g, noteImage(k), ox + (leaf.width + GAP - MARGIN) * u, oy + (NOTE_TOP - MARGIN) * u,
                    ds, true);

            long hidden = 0;
            if (AcademyPaper.animated())
                for (int c = 0; c < rows.length; c++)
                    if (!written[c] && rows[c].door != Door.NONE)
                        hidden |= 1L << c;
            AcademyPaper.drawSnapped(g, leafImage(k, hidden), ox - MARGIN * u, oy - MARGIN * u, ds, true);
            long now = System.nanoTime();
            for (int c = 0; c < rows.length; c++) {
                if ((hidden & 1L << c) == 0 || writing[c] == 0 || now < writing[c])
                    continue;
                // the answer appears from left to right, as fast as a pen would write it
                double p = Math.min(1, (now - writing[c]) / 1e9 / WRITE_SECONDS);
                float left = leaf.answerLeft();
                float top = TABLE_TOP + c * ROW;
                Graphics2D s = (Graphics2D) g.create();
                s.clip(new Rectangle2D.Double(ox + (left - 6) * u, oy + (top + 1) * u, (leaf.answerWidth() + 12) * p * u,
                        (ROW - 2) * u));
                AcademyPaper.drawSnapped(s, leafImage(k, 0), ox - MARGIN * u, oy - MARGIN * u, ds, true);
                s.dispose();
            }

            double t = AcademyAtmosphere.motion() ? now / 1e9 : 0;
            float glow = (float) (.5 + .5 * Math.sin(t * 1.3));
            g.setPaint(new RadialGradientPaint((float) (ox + leaf.width * u * .45), (float) (oy + h * u * .2), w * u * .7f,
                    new float[]{0, 1}, new Color[]{new Color(255, 196, 110, (int) (12 + 18 * glow)), new Color(255, 196, 110, 0)}));
            g.fill(new Rectangle2D.Double(ox - 20, oy - 20, w * u + 40, h * u + 40));
            g.dispose();
        }
    }

    /** What the old device did for one set of reports; OPEN stands for a lit lamp as well. */
    private enum Door {
        NONE, OPEN, CLOSED, SILENT;

        // an unknown answer is never taken as a closed door
        private static Door of(Reading r) {
            if (r == null)
                return NONE;
            if (!r.isValid())
                return SILENT;
            return r.isTrue() ? OPEN : CLOSED;
        }
    }

    /** The words written into the ledger for the device of the level: a door opens, a lamp lights. */
    private static final class Words {
        private final String device;
        private final boolean lamp;
        private final List<String> guesses;
        private final List<String> answers;

        private Words(String device, List<String> guesses, List<String> answers) {
            this.device = device;
            lamp = "灯".equals(device);
            this.guesses = guesses;
            this.answers = answers;
        }

        // several lamps answer with longer words than a single one
        private boolean wide() {
            return guesses != null || answers != null;
        }

        private String guess(long value) {
            if (guesses != null && value >= 0 && value < guesses.size())
                return guesses.get((int) value);
            boolean lit = value != 0;
            if (lamp)
                return lit ? "猜会亮" : "猜不亮";
            return lit ? "猜会开" : "猜不开";
        }

        private String answer(Door d, long value) {
            if (answers != null && (d == Door.OPEN || d == Door.CLOSED) && value >= 0 && value < answers.size())
                return answers.get((int) value);
            switch (d) {
                case OPEN:
                    return lamp ? "灯亮了" : device + "开了";
                case CLOSED:
                    return lamp ? "灯没亮" : device + "没开";
                case SILENT:
                    return "没有回应";
                default:
                    return UNOBSERVED;
            }
        }
    }

    /** One set of reports as it stands in the ledger. */
    private static final class Entry {
        private final boolean[] holds;
        private final Long guess;
        private final Door door;
        private final long value;

        private Entry(boolean[] holds, Long guess, Door door, long value) {
            this.holds = holds;
            this.guess = guess;
            this.door = door;
            this.value = value;
        }

        // true for a right guess, false for a wrong one, null if there is nothing to compare
        private Boolean mark() {
            if (guess == null || (door != Door.OPEN && door != Door.CLOSED))
                return null;
            return guess == value;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (boolean b : holds)
                sb.append(b ? 'H' : 'E');
            return sb.append(guess == null ? "-" : Long.toString(guess)).append(door.ordinal()).append(':').append(value)
                    .toString();
        }
    }

    /** The left leaf laid out in sheet units. */
    private static final class Leaf {
        private final int stations;
        private final float guessW;
        private final float doorW;
        private final float table;
        private final float width;
        private final float height;
        private final float x0;

        private Leaf(int stations, int rows, boolean pointing, boolean wide) {
            this.stations = stations;
            guessW = wide ? GUESS_W + 34 : GUESS_W;
            doorW = wide ? DOOR_W + 22 : DOOR_W;
            table = INDEX_W + stations * STATION_W + guessW + doorW + MARK_W;
            width = table + 2 * PAD + GUTTER;
            height = TABLE_TOP + rows * ROW + (pointing ? 92 : 66);
            x0 = PAD + GUTTER;
        }

        private float answerLeft() {
            return x0 + INDEX_W + stations * STATION_W;
        }

        private float answerWidth() {
            return guessW + doorW + MARK_W;
        }

        private void draw(Graphics2D g, Entry[] rows, int pointed, long hidden, List<String> names, String tally,
                          Words words) {
            Random r = new Random(rows.length * 53L + 11);
            Path2D shape = AcademyPaper.edge(r, width, height, -1);
            AcademyPaper.leaf(g, shape, width, height, r);

            float mid = x0 + table / 2;
            AcademyPaper.centred(g, "工作记录", AcademyTheme.script(28), AcademyPaper.INK, mid, 58);
            AcademyPaper.centred(g, "在旧" + words.device + "前试过的每一组报告，照实记下", AcademyTheme.font(Font.PLAIN, 12.5f),
                    AcademyPaper.FADED, mid, 84);
            AcademyPaper.rule(g, x0, x0 + table, 100);

            float[] centres = new float[stations + 4];
            float x = x0;
            centres[0] = x + INDEX_W / 2;
            x += INDEX_W;
            for (int i = 0; i < stations; i++) {
                centres[1 + i] = x + STATION_W / 2;
                x += STATION_W;
            }
            centres[stations + 1] = x + guessW / 2;
            x += guessW;
            centres[stations + 2] = x + doorW / 2;
            x += doorW;
            centres[stations + 3] = x + MARK_W / 2;

            Font head = AcademyTheme.font(Font.PLAIN, 13.5f);
            for (int i = 0; i < stations; i++)
                AcademyPaper.centred(g, names.get(i), head, AcademyPaper.SEPIA, centres[1 + i], 140);
            AcademyPaper.centred(g, "先猜", head, AcademyPaper.SEPIA, centres[stations + 1], 140);
            AcademyPaper.centred(g, words.device + "的回应", head, AcademyPaper.SEPIA, centres[stations + 2], 140);
            g.setColor(new Color(122, 100, 70, 130));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Float(x0 - 4, TABLE_TOP, x0 + table + 4, TABLE_TOP));

            Font cell = AcademyTheme.font(Font.PLAIN, 15f);
            Font index = AcademyTheme.font(Font.PLAIN, 13f);
            Font hand = AcademyTheme.script(18);
            for (int c = 0; c < rows.length; c++) {
                Entry e = rows[c];
                float top = TABLE_TOP + c * ROW;
                float base = top + 31;
                if (c == pointed) {
                    g.setColor(LAMP_WASH);
                    g.fill(new RoundRectangle2D.Float(x0 - 8, top + 4, table + 16, ROW - 8, 10, 10));
                    arrow(g, x0 - 30, top + ROW / 2);
                }
                AcademyPaper.centred(g, ResultLetter.ordinal(c + 1), index, AcademyPaper.FADED, centres[0], base);
                for (int i = 0; i < stations; i++)
                    station(g, e.holds[i], cell, centres[1 + i], top + ROW / 2, base);
                if ((hidden & 1L << c) == 0) {
                    if (e.guess != null)
                        AcademyPaper.centred(g, words.guess(e.guess), hand, AcademyPaper.INK_SOFT, centres[stations + 1], base);
                    if (e.door == Door.NONE)
                        AcademyPaper.centred(g, words.answer(e.door, e.value), AcademyTheme.font(Font.PLAIN, 13.5f), PALE,
                                centres[stations + 2], base);
                    else
                        AcademyPaper.centred(g, words.answer(e.door, e.value),
                                AcademyTheme.font(e.door == Door.OPEN ? Font.BOLD : Font.PLAIN, 15.5f),
                                e.door == Door.SILENT ? AcademyPaper.FADED : AcademyPaper.INK, centres[stations + 2], base);
                    if (e.mark() != null)
                        AcademyPaper.mark(g, centres[stations + 3], top + ROW / 2, e.mark());
                }
                g.setColor(new Color(122, 100, 70, 80));
                g.setStroke(new BasicStroke(.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 3}, 0));
                g.draw(new Line2D.Float(x0, top + ROW, x0 + table, top + ROW));
            }
            float below = TABLE_TOP + rows.length * ROW;
            AcademyPaper.centred(g, tally, AcademyTheme.font(Font.PLAIN, 13f), AcademyPaper.FADED, mid, below + 32);
            if (pointed >= 0)
                AcademyPaper.centred(g, "画着箭头的一行，就是钥印卡现在摆的那组报告", AcademyTheme.font(Font.PLAIN, 12f),
                        PALE, mid, below + 58);
        }

        /** A seal mark beside the words: pressed if the station holds its key seal, an empty ring if not. */
        private static void station(Graphics2D g, boolean held, Font font, float cx, float cy, float base) {
            String words = held(held);
            float tw = AcademyPaper.textWidth(g, font, words);
            float rad = 8.5f;
            float left = cx - (2 * rad + 6 + tw) / 2;
            float gx = left + rad;
            if (held) {
                g.setColor(SEAL);
                g.fill(new Ellipse2D.Float(gx - rad, cy - rad, 2 * rad, 2 * rad));
                g.setColor(PAPER);
                g.fill(AcademyPaper.emblem(gx - .6, cy, rad * .58));
            } else {
                g.setColor(PALE);
                g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{2.4f, 2.2f}, 0));
                g.draw(new Ellipse2D.Float(gx - rad + .6f, cy - rad + .6f, 2 * rad - 1.2f, 2 * rad - 1.2f));
            }
            g.setFont(font);
            g.setColor(held ? AcademyPaper.INK : AcademyPaper.FADED);
            g.drawString(words, left + 2 * rad + 6, base);
        }

        /** A quick pen arrow in the margin. */
        private static void arrow(Graphics2D g, float x, float y) {
            Path2D p = new Path2D.Float();
            p.moveTo(x, y + 1.5f);
            p.quadTo(x + 9, y - 1, x + 19, y);
            p.moveTo(x + 13, y - 5);
            p.lineTo(x + 19.5f, y);
            p.lineTo(x + 13, y + 5.5f);
            Graphics2D a = (Graphics2D) g.create();
            a.setColor(PEN);
            a.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            a.draw(p);
            a.dispose();
        }
    }

    /** The clipped note with the hints and findings, laid out in sheet units; it grows with its text. */
    private static final class Note {
        private final List<AcademyPaper.Ink> ink = new ArrayList<>();
        private final List<Float> breaks = new ArrayList<>();
        private float height;

        private static Note of(List<String> sections) {
            Graphics2D g = AcademyPaper.probe();
            Note n = new Note();
            Font body = AcademyTheme.font(Font.PLAIN, BODY);
            FontMetrics fm = g.getFontMetrics(body);
            int wrap = (int) (NOTE_W - 2 * NOTE_PAD);
            float y = 104;
            for (int s = 0; s < sections.size(); s++) {
                if (s > 0) {
                    n.breaks.add(y - 4);
                    y += 22;
                }
                for (String para : sections.get(s).split("\n"))
                    for (String line : AcademySpeech.breakLines(para, fm, wrap)) {
                        n.ink.add(new AcademyPaper.Ink(line, body, AcademyPaper.INK, NOTE_PAD, y));
                        y += LEADING;
                    }
            }
            n.height = Math.max(300, y + 20);
            g.dispose();
            return n;
        }

        private void draw(Graphics2D g) {
            Random r = new Random(ink.size() * 71L + 5);
            Path2D shape = AcademyPaper.edge(r, NOTE_W, height, 0);
            AcademyPaper.sheet(g, shape, NOTE_W, height, r, true);
            AcademyPaper.centred(g, "提示与发现", AcademyTheme.script(24), AcademyPaper.INK, NOTE_W / 2, 52);
            AcademyPaper.rule(g, NOTE_PAD, NOTE_W - NOTE_PAD, 68);
            for (AcademyPaper.Ink i : ink)
                i.draw(g);
            g.setColor(new Color(122, 100, 70, 110));
            g.setStroke(new BasicStroke(1f));
            for (float y : breaks)
                g.draw(new Line2D.Float(NOTE_W / 2 - 34, y, NOTE_W / 2 + 34, y));
            AcademyPaper.paperClip(g, NOTE_W - 52, -16);
        }
    }
}
