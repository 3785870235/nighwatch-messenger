/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The dialogue box of a text adventure: a name plate on the frame, an optional portrait medallion and
 * words that type themselves out. A click on the box finishes the words, the next click advances.
 * Buttons may be added at EAST or SOUTH; the words use the remaining space. It shows only the text it
 * was given and reads no model.
 */
final class AcademySpeech extends JPanel {
    private static final double CHARS_PER_SECOND = 38;
    private static final long REVEAL_DELAY = 450000000L;
    private static final String NO_LINE_START = "，。、；：！？）」』》—…·.,;:!?)";
    private static final int PLATE = 34;
    private static final int PORTRAIT = 112;
    private final Font font;
    private final int lineHeight;
    private final int minLines;
    private final int hintWidth;
    private String speaker = "";
    private String words = "";
    private String note;
    private Portrait portrait;
    private long started;
    private boolean waitForShow;
    private boolean complete = true;
    private Runnable advance;
    private String wrappedFor;
    private List<Line> wrapped = new ArrayList<>();

    /**
     * Paints a character into the round medallion.
     */
    interface Portrait {
        /**
         * @param g       clipped to the medallion
         * @param cx      centre x
         * @param cy      centre y
         * @param radius  medallion radius
         * @param seconds animation time
         */
        void paint(Graphics2D g, double cx, double cy, double radius, double seconds);
    }

    /**
     * @param size      the font size of the words
     * @param minLines  the box keeps room for at least this many lines, so the layout does not jump
     * @param hintWidth the text width used before the first layout
     */
    AcademySpeech(float size, int minLines, int hintWidth) {
        super(new BorderLayout(16, 8));
        setOpaque(false);
        font = AcademyTheme.font(Font.PLAIN, size);
        lineHeight = Math.round(size * 1.62f);
        this.minLines = minLines;
        this.hintWidth = hintWidth;
        setBorder(AcademyTheme.pad(PLATE + 10, 28, 14, 18));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                click();
            }
        });
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && waitForShow) {
                waitForShow = false;
                started = System.nanoTime() + REVEAL_DELAY;
            }
        });
        AcademyAtmosphere.watch(this, () -> { });
    }

    /**
     * @param portrait the medallion painter, null for a box without portrait
     */
    void setPortrait(Portrait portrait) {
        this.portrait = portrait;
        revalidate();
        repaint();
    }

    /**
     * @param advance called by a click on the finished words; null if the box does not advance
     */
    void setAdvance(Runnable advance) {
        this.advance = advance;
    }

    /**
     * Shows new words. The same words again keep their progress, so a refresh does not retype them.
     *
     * @param speaker the name on the plate
     * @param words   the words, paragraphs separated by new lines
     * @param note    an emphasised last paragraph, maybe null
     */
    void say(String speaker, String words, String note) {
        if (speaker.equals(this.speaker) && words.equals(this.words) && same(note, this.note))
            return;
        this.speaker = speaker;
        this.words = words;
        this.note = note;
        replay();
        revalidate();
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Types the current words again from the start; hidden words wait until the scene dissolve is half done. */
    void replay() {
        started = System.nanoTime();
        waitForShow = !isShowing();
        complete = !AcademyAtmosphere.motion() || Boolean.getBoolean("academy.skipTransitions");
        repaint();
    }

    /** Shows all words at once. */
    void complete() {
        complete = true;
        repaint();
    }

    /**
     * @return true while the words are still being typed
     */
    boolean isTyping() {
        visibleChars();
        return !complete;
    }

    /**
     * Finishes typing, or advances if the words are already complete.
     */
    void click() {
        if (isTyping())
            complete();
        else if (advance != null)
            advance.run();
    }

    String speaker() {
        return speaker;
    }

    /**
     * @return all words including the note, independent of the typing progress
     */
    String fullText() {
        return note == null || note.isEmpty() ? words : words + "\n" + note;
    }

    private int visibleChars() {
        if (complete)
            return Integer.MAX_VALUE;
        if (!AcademyAtmosphere.motion() || Boolean.getBoolean("academy.skipTransitions")) {
            complete = true;
            return Integer.MAX_VALUE;
        }
        int n = (int) Math.max(0, (System.nanoTime() - started) / 1e9 * CHARS_PER_SECOND);
        if (n >= fullText().replace("\n", "").length())
            complete = true;
        return n;
    }

    private Rectangle textArea() {
        Insets in = getInsets();
        int left = in.left + (portrait != null ? PORTRAIT + 10 : 0);
        int right = getWidth() - in.right;
        int bottom = getHeight() - in.bottom;
        BorderLayout layout = (BorderLayout) getLayout();
        Component east = layout.getLayoutComponent(BorderLayout.EAST);
        if (east != null && east.isVisible() && east.getWidth() > 0)
            right = east.getX() - 18;
        Component south = layout.getLayoutComponent(BorderLayout.SOUTH);
        if (south != null && south.isVisible() && south.getHeight() > 0)
            bottom = south.getY() - 6;
        return new Rectangle(left, in.top, Math.max(80, right - left), Math.max(lineHeight, bottom - in.top));
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        Insets in = getInsets();
        int width = getWidth() > 0 ? textArea().width : hintWidth;
        int lines = Math.max(minLines, lines(getFontMetrics(font), width).size());
        Component south = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        int southHeight = south != null && south.isVisible() ? south.getPreferredSize().height + 8 : 0;
        int h = in.top + lines * lineHeight + southHeight + in.bottom;
        if (portrait != null)
            h = Math.max(h, PLATE / 2 + PORTRAIT + 28);
        return new Dimension(d.width, Math.max(d.height, h));
    }

    private List<Line> lines(FontMetrics fm, int width) {
        String key = width + "|" + fullText() + "|" + (note == null ? -1 : note.length());
        if (!key.equals(wrappedFor)) {
            wrappedFor = key;
            wrapped = new ArrayList<>();
            String[] paragraphs = words.split("\n", -1);
            for (String p : paragraphs)
                wrap(p, fm, width, false, wrapped);
            if (note != null && !note.isEmpty())
                for (String p : note.split("\n", -1))
                    wrap(p, fm, width, true, wrapped);
        }
        return wrapped;
    }

    /**
     * Breaks one paragraph by character width; closing punctuation never starts a line.
     */
    private static void wrap(String paragraph, FontMetrics fm, int width, boolean emphasis, List<Line> out) {
        List<String> lines = breakLines(paragraph, fm, width);
        for (int i = 0; i < lines.size(); i++)
            out.add(new Line(lines.get(i), emphasis, i + 1 == lines.size()));
    }

    /**
     * Breaks a paragraph of Chinese text by character width; closing punctuation never starts a line.
     *
     * @param paragraph the text without new lines
     * @param fm        the metrics of the font
     * @param width     the available width
     * @return at least one line
     */
    static List<String> breakLines(String paragraph, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            int cw = fm.charWidth(c);
            if (line.length() > 0 && lineWidth + cw > width && NO_LINE_START.indexOf(c) < 0) {
                out.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
            }
            line.append(c);
            lineWidth += cw;
        }
        out.add(line.toString());
        return out;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth();
        int h = getHeight();
        double seconds = System.nanoTime() / 1e9;
        float top = PLATE / 2f;
        Shape frame = new RoundRectangle2D.Float(1, top, w - 3, h - top - 2, 18, 18);
        g.setPaint(new GradientPaint(0, top, new Color(17, 28, 47, 240), 0, h, new Color(8, 13, 24, 248)));
        g.fill(frame);
        g.setColor(new Color(217, 175, 96, 210));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(frame);
        g.setColor(new Color(217, 175, 96, 70));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(6, top + 5, w - 13, h - top - 12, 13, 13));
        corner(g, 6, top + 5);
        corner(g, w - 7, top + 5);
        corner(g, 6, h - 7);
        corner(g, w - 7, h - 7);

        Insets in = getInsets();
        if (portrait != null)
            medallion(g, in.left + PORTRAIT / 2.0, top + (h - top) / 2.0, PORTRAIT / 2.0 - 4, seconds);
        if (!speaker.isEmpty())
            plate(g, portrait != null ? in.left - 4 : in.left - 6);

        Rectangle area = textArea();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int left = visibleChars();
        int y = area.y + fm.getAscent() + 2;
        for (Line line : lines(fm, area.width)) {
            if (left <= 0)
                break;
            String part = line.text.length() <= left ? line.text : line.text.substring(0, left);
            left -= line.text.length();
            g.setColor(line.emphasis ? AcademyTheme.GOLD_HI : AcademyTheme.IVORY);
            g.drawString(part, area.x, y);
            y += lineHeight + (line.last ? 3 : 0);
        }
        if (!isTyping() && advance != null) {
            double bounce = AcademyAtmosphere.motion() ? Math.abs(Math.sin(seconds * 3.2)) * 4 : 0;
            int ax = area.x + area.width - 10;
            int ay = (int) (area.y + area.height - 10 + bounce);
            Path2D tri = new Path2D.Double();
            tri.moveTo(ax - 7, ay - 5);
            tri.lineTo(ax + 7, ay - 5);
            tri.lineTo(ax, ay + 4);
            tri.closePath();
            g.setColor(AcademyTheme.GOLD_HI);
            g.fill(tri);
        }
        g.dispose();
    }

    private static void corner(Graphics2D g, float x, float y) {
        Path2D d = new Path2D.Float();
        d.moveTo(x, y - 4);
        d.lineTo(x + 4, y);
        d.lineTo(x, y + 4);
        d.lineTo(x - 4, y);
        d.closePath();
        g.setColor(AcademyTheme.GOLD);
        g.fill(d);
    }

    private void plate(Graphics2D g, int x) {
        g.setFont(AcademyTheme.script(22));
        FontMetrics fm = g.getFontMetrics();
        int pw = fm.stringWidth(speaker) + 44;
        RoundRectangle2D plate = new RoundRectangle2D.Float(x, 1, pw, PLATE - 2, 10, 10);
        g.setPaint(new GradientPaint(0, 0, new Color(0x4A361A), 0, PLATE, new Color(0x261A0C)));
        g.fill(plate);
        g.setColor(AcademyTheme.GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(plate);
        g.setColor(new Color(240, 203, 126, 90));
        g.drawLine(x + 8, PLATE - 7, x + pw - 8, PLATE - 7);
        g.setColor(AcademyTheme.GOLD_HI);
        g.drawString(speaker, x + 22, (PLATE + fm.getAscent() - fm.getDescent()) / 2);
    }

    private void medallion(Graphics2D g, double cx, double cy, double r, double seconds) {
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        float glow = (float) (r * 1.45);
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, glow, new float[]{0, .6f, 1},
                new Color[]{new Color(101, 206, 225, (int) (60 + 25 * Math.sin(t * 1.7))), new Color(101, 206, 225, 18),
                        new Color(101, 206, 225, 0)}));
        g.fill(new Ellipse2D.Double(cx - glow, cy - glow, glow * 2, glow * 2));
        Ellipse2D disc = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        g.setPaint(new RadialGradientPaint((float) cx, (float) (cy - r * .3), (float) (r * 1.3),
                new float[]{0, 1}, new Color[]{new Color(0x22395A), new Color(0x0B1626)}));
        g.fill(disc);
        Graphics2D p = (Graphics2D) g.create();
        p.clip(disc);
        portrait.paint(p, cx, cy, r, seconds);
        p.dispose();
        g.setColor(AcademyTheme.GOLD);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(disc);
        g.setColor(new Color(217, 175, 96, 110));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Ellipse2D.Double(cx - r - 5, cy - r - 5, r * 2 + 10, r * 2 + 10));
    }

    private static final class Line {
        private final String text;
        private final boolean emphasis;
        private final boolean last;

        private Line(String text, boolean emphasis, boolean last) {
            this.text = text;
            this.emphasis = emphasis;
            this.last = last;
        }
    }
}
