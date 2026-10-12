/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The torn pages of the door rules, shown like the item view of a text adventure: one page at a time
 * under the lamp, turned with the arrows, the dots or the arrow keys. Before the player chooses, no
 * page shows any evidence. The chosen page gets the seal of the evidence and the spirit reads the
 * evidence out. Everything shown comes from {@link Investigation}; this class decides nothing.
 */
final class AcademyCasebook extends JPanel {
    private static final double FLIP_SECONDS = 0.34;
    private static final double THUMP_SECONDS = 0.24;
    private static final long REVEAL_DELAY = 520000000L;
    private static final float PAGE_W = 580;
    private static final float PAGE_H = 372;
    private static final float MARGIN = 18;
    private static final float LEFT = 42;
    private static final float RIGHT = PAGE_W - 42;
    private static final float SEAL_X = 428;
    private static final float SEAL_Y = 74;
    private static final Color RIBBON = new Color(0x9C2B2B);
    private static final String SPEAKER = "微光 · 档案精灵";
    private static final String ASK = "一页一页看完，选出你认为说对的那张。选了，我才把证据念给你听。";

    private final Investigation investigation;
    private final Supplier<ExperimentSession> session;
    private final List<Investigation.Candidate> pages;
    private final JLabel records = AcademyTheme.label(" ", AcademyTheme.font(Font.PLAIN, 14), AcademyTheme.MUTED);
    private final Sheet sheet = new Sheet();
    private final Arrow previous = new Arrow(-1);
    private final Arrow next = new Arrow(1);
    private final Dots dots = new Dots();
    private final JLabel pageLabel = AcademyTheme.label(" ", AcademyTheme.font(Font.PLAIN, 14), AcademyTheme.MUTED);
    private final CardLayout chipCards = new CardLayout();
    private final JPanel chipHost = AcademyTheme.clear(chipCards);
    private final ButtonGroup group = new ButtonGroup();
    private final List<JRadioButton> chips = new ArrayList<>();
    private final AcademySpeech speech = new AcademySpeech(17, 4, 820);
    private final JButton back = AcademyTheme.button("← 回到门前", AcademyTheme.Kind.QUIET);
    private final JButton repair = AcademyTheme.button("动手修复 →", AcademyTheme.Kind.PRIMARY);
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private final AcademyPaper.Desk desk = new AcademyPaper.Desk();
    private final AcademyPaper.Thump thump = new AcademyPaper.Thump(REVEAL_DELAY, THUMP_SECONDS);
    private int index;
    private int flipFrom = -1;
    private long flipStarted;
    private String sealKey;
    private int pose;

    /**
     * @param investigation the investigation
     * @param session       the current real records; the panel may replace its session
     * @param chooser       called with the candidate id when the player chooses a page
     */
    AcademyCasebook(Investigation investigation, Supplier<ExperimentSession> session, Consumer<String> chooser) {
        super(new BorderLayout(0, 10));
        this.investigation = investigation;
        this.session = session;
        this.pages = investigation.getCandidates();
        setOpaque(false);
        setBorder(AcademyTheme.pad(12, 22, 16, 22));

        JPanel top = AcademyTheme.clear(new BorderLayout());
        top.add(records, BorderLayout.WEST);
        top.add(AcademyTheme.label("← → 翻页", AcademyTheme.font(Font.PLAIN, 13), AcademyTheme.MUTED), BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel pager = AcademyTheme.clear(new BorderLayout(10, 8));
        pager.add(centered(previous), BorderLayout.WEST);
        pager.add(sheet, BorderLayout.CENTER);
        pager.add(centered(next), BorderLayout.EAST);
        JPanel footer = AcademyTheme.clear(new FlowLayout(FlowLayout.CENTER, 18, 0));
        footer.add(dots);
        footer.add(pageLabel);
        for (int i = 0; i < pages.size(); i++) {
            final int page = i;
            final String id = pages.get(i).getId();
            JRadioButton chip = new AcademyTheme.Chip("我认为是它");
            chip.addActionListener(e -> {
                turnTo(page);
                chooser.accept(id);
            });
            group.add(chip);
            chips.add(chip);
            chipHost.add(chip, id);
        }
        footer.add(chipHost);
        pager.add(footer, BorderLayout.SOUTH);
        add(pager, BorderLayout.CENTER);

        speech.setPortrait((g, cx, cy, r, seconds) ->
                AcademyCharacters.spiritPose(g, cx, cy + r * .1, r * 2.3, seconds, pose, null));
        JPanel actions = AcademyTheme.clear(new GridLayout(2, 1, 0, 8));
        actions.setBorder(AcademyTheme.pad(0, 22, 0, 0));
        actions.add(repair);
        actions.add(back);
        speech.add(centered(actions), BorderLayout.EAST);
        add(speech, BorderLayout.SOUTH);

        previous.addActionListener(e -> turnTo(index - 1));
        next.addActionListener(e -> turnTo(index + 1));
        InputMap keys = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "casebook.previous");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "casebook.next");
        getActionMap().put("casebook.previous", new Turn(-1));
        getActionMap().put("casebook.next", new Turn(1));
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing())
                thump.shown();
        });
        AcademyAtmosphere.watch(this, this::tick);
        updatePager();
    }

    private static JPanel centered(JComponent c) {
        JPanel p = AcademyTheme.clear(new GridBagLayout());
        p.add(c);
        return p;
    }

    private static double since(long start) {
        return (System.nanoTime() - start) / 1e9;
    }

    private void tick() {
        if (flipFrom >= 0 && since(flipStarted) >= FLIP_SECONDS)
            flipFrom = -1;
        thump.tick();
    }

    /**
     * Opens the book on the chosen page, or on the first page if nothing is chosen yet.
     */
    void open() {
        Investigation.Candidate chosen = investigation.getChoice();
        index = chosen == null ? 0 : Math.max(0, pages.indexOf(chosen));
        flipFrom = -1;
        speech.replay();
        updatePager();
    }

    /**
     * Shows the current evidence. Before a choice only the number of records is told.
     *
     * @param rebuildStage true if the player is already repairing
     */
    void refresh(boolean rebuildStage) {
        ExperimentSession s = session.get();
        Investigation.Candidate chosen = investigation.getChoice();
        String place = investigation.word("place", "门前");
        records.setText(place + "记录 " + s.getObservedCount() + " / " + s.getCombinationCount() + " 组 · 预测 " + s.getPredictions() + " 次");
        for (int i = 0; i < pages.size(); i++) {
            boolean mine = pages.get(i) == chosen;
            chips.get(i).setSelected(mine);
            chips.get(i).setText(mine ? "我的判断" : "我认为是它");
        }
        if (chosen == null)
            group.clearSelection();

        Investigation.Standing standing = chosen == null ? null : investigation.standing(chosen, s);
        String key = chosen == null ? null : chosen.getId() + ":" + standing;
        if (key != null && !key.equals(sealKey))
            thump.press(this, 0);
        sealKey = key;

        if (chosen == null) {
            pose = 0;
            speech.say(SPEAKER, (s.getObservedCount() > 0 ? place + "已有 " + s.getObservedCount() + " 组记录。" : "") + ASK, null);
        } else {
            pose = standing == Investigation.Standing.REFUTED ? 1
                    : standing == Investigation.Standing.PROVEN ? 2
                    : standing == Investigation.Standing.STANDING ? 3 : 0;
            String words = investigation.choiceFeedback(s) + "\n"
                    + (s.getObservedCount() == 0 ? "去" + place + "试讯台，亲眼看看" + investigation.word("device", "门") + "怎么回应。" : investigation.verdict(s));
            Investigation.Candidate found = investigation.discovered(s);
            speech.say(SPEAKER, words, found != null && found == chosen ? found.getKnowledge() : null);
        }
        back.setText(rebuildStage ? "← 回到修复" : "← 回到" + place);
        repair.setText(rebuildStage ? "知道了 →" : "动手修复 →");
        repair.setEnabled(rebuildStage || investigation.canRepair(s));
        repair.setToolTipText(repair.isEnabled() ? null : Investigation.groups(s.getCombinationCount())
                + "报告都亲眼看过、做过预测、只剩一张残页且你选了它，才能动手修复。");
        updatePager();
    }

    private void turnTo(int target) {
        if (target < 0 || target >= pages.size() || target == index)
            return;
        if (AcademyPaper.animated() && isShowing()) {
            flipFrom = index;
            flipStarted = System.nanoTime();
        }
        AcademySound.cue("page");
        index = target;
        updatePager();
    }

    private void updatePager() {
        previous.setEnabled(index > 0);
        next.setEnabled(index + 1 < pages.size());
        pageLabel.setText("第 " + (index + 1) + " / " + pages.size() + " 页");
        chipCards.show(chipHost, pages.get(index).getId());
        dots.repaint();
        sheet.repaint();
    }

    int page() {
        return index;
    }

    int pages() {
        return pages.size();
    }

    JButton previousButton() {
        return previous;
    }

    JButton nextButton() {
        return next;
    }

    JRadioButton chip(int i) {
        return chips.get(i);
    }

    JButton backButton() {
        return back;
    }

    JButton repairButton() {
        return repair;
    }

    AcademySpeech speech() {
        return speech;
    }

    /**
     * @return the shown page, the seal of the chosen page and everything the spirit says
     */
    String text() {
        StringBuilder sb = new StringBuilder(records.getText()).append('\n');
        Investigation.Candidate shown = pages.get(index);
        sb.append("翻到：").append(shown.getName()).append('\n');
        Investigation.Candidate chosen = investigation.getChoice();
        if (chosen != null)
            sb.append("判断：").append(chosen.getName()).append(" · ").append(investigation.status(chosen, session.get())).append('\n');
        return sb.append(speech.fullText()).toString();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double t = AcademyAtmosphere.motion() ? System.nanoTime() / 1e9 : 0;
        Rectangle area = SwingUtilities.convertRectangle(sheet.getParent(), sheet.getBounds(), this);
        desk.paint(g, getWidth(), getHeight(), area, t);
        g.dispose();
    }

    /** The page under the lamp, the stack below it, the flip and the seal. */
    private final class Sheet extends JComponent {
        private float u;
        private double ds;
        private float px;
        private float py;

        private Sheet() {
            setOpaque(false);
            setPreferredSize(new Dimension(600, 330));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            ds = AcademyPaper.deviceScale(g);
            double t = AcademyAtmosphere.motion() ? System.nanoTime() / 1e9 : 0;
            float pw = (float) Math.min(Math.min(getWidth() * .94, (getHeight() - 10) * PAGE_W / PAGE_H), 780);
            if (pw < 60) {
                g.dispose();
                return;
            }
            float ph = pw * PAGE_H / PAGE_W;
            u = pw / PAGE_W;
            px = (float) (Math.round((getWidth() - pw) / 2 * ds) / ds);
            py = (float) (Math.round((getHeight() - ph) / 2 * ds) / ds);
            stack(g, px, py, pw, ph);

            double p = flipFrom >= 0 ? Math.min(1, since(flipStarted) / FLIP_SECONDS) : 1;
            if (p < 1) {
                // like dealing cards: turning forward lifts the top page off to the left and shows the next one
                // beneath it; turning back lays the previous page down again from the left
                boolean forward = index > flipFrom;
                double e = p * p * (3 - 2 * p);
                double out = forward ? e : 1 - e;
                page(g, forward ? index : flipFrom, 0, 0, 1);
                // paper is not glass: the lifted page stays opaque and only fades once it has mostly left
                double fade = Math.max(0, (out - .55) / .45);
                page(g, forward ? flipFrom : index, -out * pw * .78, -Math.toRadians(11) * out, (float) (1 - fade * fade));
            } else
                page(g, index, 0, 0, 1);
            float glow = (float) (.5 + .5 * Math.sin(t * 1.3));
            g.setPaint(new RadialGradientPaint(px + pw * .32f, py + ph * .18f, pw * .85f, new float[]{0, 1},
                    new Color[]{new Color(255, 196, 110, (int) (14 + 22 * glow)), new Color(255, 196, 110, 0)}));
            g.fill(new Rectangle2D.Float(px - 20, py - 20, pw + 40, ph + 40));
            g.dispose();
        }

        private void stack(Graphics2D g, float x, float y, float pw, float ph) {
            for (int k = 2; k >= 1; k--) {
                Graphics2D s = (Graphics2D) g.create();
                s.rotate(Math.toRadians(k == 2 ? 2.4 : -1.6), x + pw / 2, y + ph / 2);
                s.translate(k * 5, k * 4);
                s.setColor(new Color(0, 0, 0, 60));
                s.fill(new Rectangle2D.Float(x + 4, y + 8, pw, ph));
                s.setPaint(new GradientPaint(x, y, k == 2 ? new Color(0xB59C6E) : new Color(0xC7AF80), x, y + ph,
                        k == 2 ? new Color(0x8F7A52) : new Color(0xA48C5E)));
                s.fill(new Rectangle2D.Float(x, y, pw, ph));
                s.setColor(new Color(80, 58, 32, 120));
                s.draw(new Rectangle2D.Float(x, y, pw, ph));
                s.dispose();
            }
        }

        /** Draws a page with its seal; a resting page lands on whole device pixels. */
        private void page(Graphics2D g, int i, double dx, double angle, float alpha) {
            Investigation.Candidate c = pages.get(i);
            boolean chosen = c == investigation.getChoice();
            BufferedImage img = pageImage(i, chosen, u, ds);
            float pw = PAGE_W * u;
            float ph = PAGE_H * u;
            Graphics2D p = (Graphics2D) g.create();
            p.translate(px + dx, py);
            if (angle != 0)
                p.rotate(angle, pw / 2, ph);
            if (alpha < 1)
                p.setComposite(AlphaComposite.SrcOver.derive(Math.max(0, alpha)));
            AcademyPaper.drawSnapped(p, img, -MARGIN * u, -MARGIN * u, ds, dx == 0 && angle == 0);
            if (chosen) {
                // the seal of the chosen page thumps down when the evidence changes
                BufferedImage seal = sealImage(investigation.standing(c, session.get()), u, ds);
                thump.draw(p, seal, SEAL_X * u, SEAL_Y * u, -9, ds);
            }
            p.dispose();
        }
    }

    private BufferedImage pageImage(int i, boolean chosen, float u, double ds) {
        String key = "page:" + i + ":" + chosen + ":" + Math.round(PAGE_W * u * ds);
        BufferedImage img = cache.get(key);
        if (img == null) {
            if (cache.size() > 16)
                cache.clear();
            double k = u * ds;
            img = AcademyPaper.image(PAGE_W + 2 * MARGIN, PAGE_H + 2 * MARGIN, k);
            Graphics2D g = AcademyPaper.graphics(img, k);
            g.translate(MARGIN, MARGIN);
            drawPage(g, i, pages.get(i), chosen);
            g.dispose();
            cache.put(key, img);
        }
        return img;
    }

    private void drawPage(Graphics2D g, int i, Investigation.Candidate c, boolean chosen) {
        Random r = new Random(c.getId().hashCode() * 31L + 7);
        int[] roughSides = {2, 1, 3};
        Path2D shape = AcademyPaper.edge(r, PAGE_W, PAGE_H, roughSides[i % roughSides.length]);
        AcademyPaper.sheet(g, shape, PAGE_W, PAGE_H, r, false);
        if (chosen)
            ribbon(g);
        writing(g, c);
    }

    private static void ribbon(Graphics2D g) {
        float x = 14;
        float w = 22;
        Path2D band = new Path2D.Float();
        band.moveTo(x, -14);
        band.lineTo(x + w, -14);
        band.lineTo(x + w, 66);
        band.lineTo(x + w / 2, 56);
        band.lineTo(x, 66);
        band.closePath();
        g.setColor(new Color(0, 0, 0, 50));
        g.fill(AffineTransform.getTranslateInstance(2, 3).createTransformedShape(band));
        g.setPaint(new GradientPaint(x, 0, RIBBON.brighter(), x + w, 0, RIBBON.darker()));
        g.fill(band);
        g.setColor(new Color(255, 220, 200, 70));
        g.setStroke(new BasicStroke(.8f));
        g.draw(new Line2D.Float(x + 4, -14, x + 4, 60));
    }

    private void writing(Graphics2D g, Investigation.Candidate c) {
        String name = c.getName();
        int dot = name.indexOf('·');
        String shortName = dot > 0 ? name.substring(0, dot).trim() : name;
        String letter = shortName.substring(shortName.length() - 1);

        g.setFont(AcademyTheme.font(Font.PLAIN, 14));
        g.setColor(AcademyPaper.FADED);
        g.drawString(investigation.word("rulebook", "《门规》") + " · 残页", LEFT, 44);
        // the page letter sits in the lower corner like a folio mark; the top right is kept for the seal
        float ex = PAGE_W - 58;
        float ey = PAGE_H - 42;
        g.setColor(AcademyPaper.SEPIA);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Float(ex - 18, ey - 18, 36, 36));
        g.setStroke(new BasicStroke(.7f));
        g.draw(new Ellipse2D.Float(ex - 14, ey - 14, 28, 28));
        g.setFont(AcademyTheme.script(19));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(letter, ex - fm.stringWidth(letter) / 2f, ey + (fm.getAscent() - fm.getDescent()) / 2f);

        g.setFont(AcademyTheme.script(34));
        g.setColor(AcademyPaper.INK);
        g.drawString(name, LEFT, 98);
        AcademyPaper.rule(g, LEFT, RIGHT, 118);

        String smudged = quoted(c.getDoubt());
        g.setFont(AcademyTheme.font(Font.PLAIN, 23));
        fm = g.getFontMetrics();
        float y = 158;
        for (String line : AcademySpeech.breakLines(c.getRule(), fm, (int) (RIGHT - LEFT))) {
            g.setColor(AcademyPaper.INK);
            g.drawString(line, LEFT, y);
            int at = smudged == null ? -1 : line.indexOf(smudged);
            if (at >= 0) {
                float cx = LEFT + fm.stringWidth(line.substring(0, at)) + fm.stringWidth(smudged) / 2f;
                float cy = y - fm.getAscent() * .38f;
                g.setPaint(new RadialGradientPaint(cx + 3, cy + 2, 21, new float[]{0, 1},
                        new Color[]{new Color(46, 38, 32, 92), new Color(46, 38, 32, 0)}));
                g.fill(new Ellipse2D.Float(cx - 22, cy - 20, 50, 44));
                g.setColor(new Color(46, 38, 32, 90));
                g.drawString(smudged, cx - fm.stringWidth(smudged) / 2f + 1.6f, y + 1.2f);
            }
            y += 36;
        }

        float top = Math.max(y + 22, 240);
        float gap = 30;
        float col = (RIGHT - LEFT - gap) / 2;
        column(g, "来历", c.getOrigin(), LEFT, top, col);
        column(g, "疑点", c.getDoubt(), LEFT + col + gap, top, col);
        g.setColor(new Color(122, 100, 70, 90));
        g.draw(new Line2D.Float(LEFT + col + gap / 2, top - 16, LEFT + col + gap / 2, top + 64));
    }

    private static void column(Graphics2D g, String title, String text, float x, float y, float width) {
        if (text == null || text.isEmpty())
            return;
        g.setFont(AcademyTheme.script(20));
        g.setColor(AcademyPaper.SEPIA);
        g.drawString(title, x, y);
        g.setFont(AcademyTheme.font(Font.PLAIN, 15));
        g.setColor(AcademyPaper.INK_SOFT);
        FontMetrics fm = g.getFontMetrics();
        float line = y + 26;
        for (String l : AcademySpeech.breakLines(text, fm, (int) width)) {
            g.drawString(l, x, line);
            line += 23;
        }
    }

    /**
     * @return the single character quoted in 「」 by the doubt, e.g. 都; null if there is none
     */
    private static String quoted(String doubt) {
        if (doubt == null)
            return null;
        int open = doubt.indexOf('「');
        int close = doubt.indexOf('」', open + 1);
        return open >= 0 && close == open + 2 ? doubt.substring(open + 1, close) : null;
    }

    private BufferedImage sealImage(Investigation.Standing standing, float u, double ds) {
        String key = "seal:" + standing + ":" + Math.round(u * ds * 100);
        BufferedImage img = cache.get(key);
        if (img != null)
            return img;
        String text;
        Color color;
        switch (standing) {
            case REFUTED:
                text = "已被推翻";
                color = AcademyPaper.RED_INK;
                break;
            case STANDING:
                text = "暂未推翻";
                color = new Color(0x2F5E8C);
                break;
            case PROVEN:
                text = "经得起全部证据";
                color = AcademyPaper.GREEN_INK;
                break;
            default:
                text = "尚无证据";
                color = new Color(0x6B6458);
                break;
        }
        img = AcademyPaper.stamp(text, color, u * ds, standing.ordinal() * 17L + 3);
        cache.put(key, img);
        return img;
    }

    /** A round arrow on the desk edge. */
    private static final class Arrow extends JButton {
        private final int direction;

        private Arrow(int direction) {
            this.direction = direction;
            setPreferredSize(new Dimension(54, 54));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(direction < 0 ? "上一页（←）" : "下一页（→）");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isEnabled();
            boolean hover = on && getModel().isRollover();
            float s = Math.min(getWidth(), getHeight()) - 4;
            float x = (getWidth() - s) / 2;
            float y = (getHeight() - s) / 2;
            Shape disc = new Ellipse2D.Float(x, y, s, s);
            g.setColor(hover ? new Color(34, 54, 84, 235) : new Color(14, 23, 38, on ? 215 : 120));
            g.fill(disc);
            g.setColor(on ? (hover ? AcademyTheme.GOLD_HI : AcademyTheme.GOLD) : new Color(217, 175, 96, 60));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(disc);
            float cx = x + s / 2;
            float cy = y + s / 2;
            Path2D chevron = new Path2D.Float();
            chevron.moveTo(cx - 4 * direction, cy - 9);
            chevron.lineTo(cx + 5 * direction, cy);
            chevron.lineTo(cx - 4 * direction, cy + 9);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(chevron);
            g.dispose();
        }
    }

    /** The page dots; the chosen page carries the red of its ribbon. */
    private final class Dots extends JComponent {
        private static final int STEP = 24;

        private Dots() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    turnTo(Math.min(pages.size() - 1, Math.max(0, e.getX() / STEP)));
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(STEP * Math.max(1, pages.size()), 24);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Investigation.Candidate chosen = investigation.getChoice();
            for (int i = 0; i < pages.size(); i++) {
                float cx = i * STEP + STEP / 2f;
                float cy = getHeight() / 2f;
                boolean mine = pages.get(i) == chosen;
                Color tone = mine ? new Color(0xD8574F) : AcademyTheme.GOLD_HI;
                if (i == index) {
                    g.setColor(tone);
                    g.fill(new Ellipse2D.Float(cx - 6, cy - 6, 12, 12));
                } else {
                    g.setColor(mine ? tone : AcademyTheme.MUTED);
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(new Ellipse2D.Float(cx - 4.5f, cy - 4.5f, 9, 9));
                }
            }
            g.dispose();
        }
    }

    /** Arrow keys turn the pages only while the book is on screen; otherwise the key stays with others. */
    private final class Turn extends AbstractAction {
        private final int step;

        private Turn(int step) {
            this.step = step;
        }

        @Override
        public boolean isEnabled() {
            int target = index + step;
            return isShowing() && target >= 0 && target < pages.size();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            turnTo(index + step);
        }
    }
}
