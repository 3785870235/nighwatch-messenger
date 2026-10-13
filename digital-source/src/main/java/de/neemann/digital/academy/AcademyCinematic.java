/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.util.Properties;

/** A real-time, four-shot prologue. Space advances one shot; Escape skips the sequence. */
final class AcademyCinematic extends JPanel {
    private static final java.util.Map<String, BufferedImage> PAPERS = new java.util.HashMap<>();
    private final boolean success;
    private final Runnable finished;
    private final Timer timer;
    private final long started = System.nanoTime();
    private final JButton continueButton;
    private final JButton prevButton;
    private final JButton pauseButton;
    private final JButton nextButton;
    private final KeyEventDispatcher keyboard = this::handleKey;
    private boolean stopped;
    private boolean paused;
    private boolean keyboardInstalled;
    private boolean spaceDown;
    private boolean escapeDown;
    private double offset;
    private double pausedAt = -1;
    private double previewTime = -1;
    private int soundedShot = -1;
    private int footsteps;
    private long paints;
    private final double[] durations;
    private final Script script;

    AcademyCinematic(boolean success, Runnable finished) {
        this(success, Script.ARCHIVE, finished);
    }

    AcademyCinematic(boolean success, Script script, Runnable finished) {
        super(new BorderLayout());
        this.success = success;
        this.script = script;
        this.finished = finished;
        durations = success ? new double[]{9.5, 9.5} : new double[]{10, 11, 10, 11};
        setOpaque(true);
        setFocusable(true);
        addMouseListener(new MouseAdapter() { });
        JPanel controls = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 16, 15));
        continueButton = AcademyTheme.button("跳过整段 · Esc", AcademyTheme.Kind.QUIET);
        continueButton.addActionListener(e -> finish());
        prevButton = AcademyTheme.button("← 上一幕", AcademyTheme.Kind.QUIET);
        prevButton.addActionListener(e -> previousShot());
        pauseButton = AcademyTheme.button("暂停 ⏸", AcademyTheme.Kind.SECONDARY);
        pauseButton.addActionListener(e -> togglePause());
        nextButton = AcademyTheme.button("下一幕 · 空格 →", AcademyTheme.Kind.PRIMARY);
        nextButton.addActionListener(e -> advanceShot());
        controls.add(continueButton);
        controls.add(prevButton);
        controls.add(pauseButton);
        controls.add(nextButton);
        controls.add(AcademyAtmosphere.settings(null));
        add(controls, BorderLayout.SOUTH);
        timer = AcademyAtmosphere.watch(this, this::tick);
    }

    private void tick() {
        if (stopped || paused || previewTime >= 0)
            return;
        AcademySound.ambience(this);
        int shot = shotIndex();
        if (shot != soundedShot) {
            soundedShot = shot;
            footsteps = 0;
            String cue = success ? (shot == 0 ? "success" : "page")
                    : new String[]{"bell", "paper", "spirit", "rune"}[shot];
            AcademySound.cue(cue, this);
        }
        if (!success && shot == 0) {
            int steps = Math.min(4, (int) (localTime() / .65));
            if (steps > footsteps) {
                footsteps = steps;
                AcademySound.cue("step", this);
            }
        }
        updateControls();
    }

    private void updateControls() {
        nextButton.setText(shotIndex() + 1 == durations.length
                ? (success ? "返回工作台 →" : "开始调查 →") : "下一幕 · 空格 →");
    }

    private boolean handleKey(KeyEvent event) {
        if (!isShowing() || stopped || event.getComponent() == null
                || !SwingUtilities.isDescendingFrom(event.getComponent(), this))
            return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_SPACE) {
            if (event.getID() == KeyEvent.KEY_RELEASED)
                spaceDown = false;
            else if (event.getID() == KeyEvent.KEY_PRESSED && !spaceDown) {
                spaceDown = true;
                advanceShot();
            }
            return true;
        }
        if (code == KeyEvent.VK_ESCAPE) {
            if (event.getID() == KeyEvent.KEY_RELEASED)
                escapeDown = false;
            else if (event.getID() == KeyEvent.KEY_PRESSED && !escapeDown) {
                escapeDown = true;
                finish();
            }
            return true;
        }
        return event.getID() == KeyEvent.KEY_TYPED && event.getKeyChar() == ' ';
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!keyboardInstalled && !stopped) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyboard);
            keyboardInstalled = true;
        }
    }

    @Override
    public void removeNotify() {
        releaseResources();
        super.removeNotify();
    }

    private void releaseResources() {
        timer.stop();
        AcademySound.stop(this);
        if (keyboardInstalled) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyboard);
            keyboardInstalled = false;
        }
    }

    private void finish() {
        if (!stopped) {
            stop();
            finished.run();
        }
    }

    void advanceShot() {
        if (stopped)
            return;
        int next = shotIndex() + 1;
        if (next >= durations.length) {
            finish();
            return;
        }
        double target = 0;
        for (int i = 0; i < next; i++)
            target += durations[i];
        previewTime = -1;
        offset = target - (System.nanoTime() - started) / 1000000000.0;
        tick();
        repaint();
    }

    void previousShot() {
        if (stopped)
            return;
        int current = shotIndex();
        if (current <= 0)
            return;
        int prev = current - 1;
        double target = 0;
        for (int i = 0; i < prev; i++)
            target += durations[i];
        previewTime = -1;
        offset = target - (System.nanoTime() - started) / 1000000000.0;
        if (paused) {
            pausedAt = target;
        }
        tick();
        repaint();
    }

    void togglePause() {
        if (stopped)
            return;
        paused = !paused;
        if (paused) {
            pausedAt = seconds();
            pauseButton.setText("继续 ▶");
        } else {
            offset = pausedAt - (System.nanoTime() - started) / 1000000000.0;
            pausedAt = -1;
            pauseButton.setText("暂停 ⏸");
        }
        repaint();
    }

    void stop() {
        stopped = true;
        releaseResources();
        setVisible(false);
    }

    boolean isAnimating() {
        return timer.isRunning();
    }

    JButton continueButton() {
        return continueButton;
    }

    JButton nextButton() {
        return nextButton;
    }

    long paintedFrames() {
        return paints;
    }

    int shotIndex() {
        double remaining = seconds();
        for (int i = 0; i < durations.length - 1; i++) {
            if (remaining < durations[i])
                return i;
            remaining -= durations[i];
        }
        return durations.length - 1;
    }

    double totalDuration() {
        double total = 0;
        for (double duration : durations)
            total += duration;
        return total;
    }

    private double localTime() {
        double local = seconds();
        for (int i = 0; i < shotIndex(); i++)
            local -= durations[i];
        return Math.max(0, local);
    }

    String storyText() {
        return success ? script.successTexts[Math.min(1, shotIndex())] : script.texts[shotIndex()];
    }

    private double seconds() {
        if (previewTime >= 0)
            return previewTime;
        if (paused && pausedAt >= 0)
            return pausedAt;
        return offset + (System.nanoTime() - started) / 1000000000.0;
    }

    void previewAt(double seconds) {
        previewTime = seconds;
        updateControls();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        paints++;
        Graphics2D p = (Graphics2D) graphics.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int shot = shotIndex();
        double time = seconds();
        double local = localTime();
        p.setColor(AcademyTheme.NIGHT);
        p.fillRect(0, 0, w, h);
        boolean room = success || shot == 1 || shot == 2;
        if (script.scene != null && (success || shot > 0))
            AcademyLampScene.paintBackdrop(p, w, h, AcademyLampScene.backdrop(script.scene, success), time);
        else if (room)
            AcademyLivingScene.alcove(p, w, h, time);
        else {
            BufferedImage image = AcademyArt.get(shot == 0 ? AcademyArt.MENU_BACKGROUND : AcademyArt.ARCHIVE_CLOSED);
            if (image != null)
                AcademyArt.drawCover(p, image, 0, 0, w, h, .5);
            AcademyLivingScene.exterior(p, w, h, time);
        }
        p.setColor(new Color(5, 10, 19, 28));
        p.fillRect(0, 0, w, h);
        actors(p, w, h, time);
        if (AcademyAtmosphere.motion() && local < .75 && shot > 0) {
            p.setColor(new Color(7, 13, 23, (int) (210 * (1 - local / .75))));
            p.fillRect(0, 0, w, h);
        }
        caption(p, w, h, local);
        p.dispose();
    }

    private void actors(Graphics2D p, int w, int h, double time) {
        int shot = shotIndex();
        double local = localTime();
        double arrival = AcademyAtmosphere.motion() ? Math.min(1, local / 3.2) : 1;
        // the legs keep pace with the arrival: its speed, 6u(1-u) for the eased curve below, is the stride
        double stride = Math.min(1, 6 * arrival * (1 - arrival));
        arrival = arrival * arrival * (3 - 2 * arrival);
        if (success) {
            AcademyCharacters.keeper(p, w * .05, h * .08, h * .67, time, shot == 0 ? 0 : 3);
            AcademyCharacters.spiritPose(p, w * .71, h * .37, h * .34, time, 3, AcademyPointer.local(this));
        } else if (shot == 0) {
            AcademyCharacters.messengerPose(p, w * (-.16 + .23 * arrival), h * .07, h * .68, time, local < 4 ? 0 : 2,
                    AcademyCharacters.Act.walking(w * .23 * arrival / (h * .68), stride));
            letter(p, w * (.46 + .06 * arrival), h * .39, Math.min(110, w * .09), time, false);
        } else if (shot == 1) {
            // the letter of the lamp chapters is still sealed; it is opened in the archive only, so
            // only there does Fu hold it open (读信) - in the lamp chapters she looks on, hands empty
            AcademyCharacters.messengerPose(p, w * .03, h * .08, h * .64, time, script.scene == null ? 1 : 4);
            if (script.scene == null)
                letter(p, w * .60, h * .36, Math.min(410, w * .34), time, true);
        } else if (shot == 2) {
            if (script.scene == null)
                AcademyCharacters.spiritPose(p, w * .52, h * .35, h * .58, time, local < 4.5 ? 1 : 2, AcademyPointer.local(this));
            else
                AcademyCharacters.spiritPose(p, w * .30, h * .38, h * .50, time, local < 4.5 ? 1 : 2, AcademyPointer.local(this));
        } else {
            AcademyCharacters.messengerPose(p, w * .03, h * .08, h * .65, time, 3);
            // the open question of the shot: Glimmer frowns (困惑) beside the question mark drawn below
            AcademyCharacters.spiritPose(p, w * .79, h * .36, h * .34, time, 7, AcademyPointer.local(this));
            if (AcademyAtmosphere.magic()) {
                AcademyAtmosphere.lamp(p, w * .5f, h * .3f, w * .10f, .35f, time);
                p.setFont(AcademyTheme.script(42));
                p.setColor(AcademyTheme.GOLD_HI);
                p.drawString("?", w / 2 - 10, (int) (h * .33));
            }
        }
    }

    private void caption(Graphics2D p, int w, int h, double local) {
        p.setColor(new Color(5, 10, 18, 190));
        p.fillRect(0, 0, w, 58);
        p.setPaint(new GradientPaint(0, h * .55f, new Color(5, 10, 18, 0),
                0, h - 75, new Color(5, 10, 18, 248)));
        p.fillRect(0, h / 2, w, h / 2);
        p.setColor(new Color(5, 10, 18));
        p.fillRect(0, h - 72, w, 72);
        p.setFont(AcademyTheme.font(Font.PLAIN, 14));
        p.setColor(AcademyTheme.GOLD_HI);
        p.drawString(success ? script.successHeader : script.header, 30, 36);
        for (int i = 0; i < durations.length; i++) {
            p.setColor(i == shotIndex() ? AcademyTheme.GOLD_HI : AcademyTheme.LINE);
            p.fillOval(w - 32 - (durations.length - i) * 21, 26, 6, 6);
        }
        p.setFont(AcademyTheme.script(36));
        p.setColor(AcademyTheme.IVORY);
        String title = success ? script.successTitle : script.titles[shotIndex()];
        FontMetrics fm = p.getFontMetrics();
        p.drawString(title, (w - fm.stringWidth(title)) / 2, h - 174);
        p.setFont(AcademyTheme.font(Font.PLAIN, w < 1050 ? 17 : 20));
        fm = p.getFontMetrics();
        String line = storyText();
        int visible = AcademyAtmosphere.motion() ? Math.min(line.length(), Math.max(0, (int) ((local - .35) * 16))) : line.length();
        p.drawString(line.substring(0, visible), Math.max(28, (w - fm.stringWidth(line)) / 2), h - 124);
        p.setFont(AcademyTheme.font(Font.PLAIN, 13));
        p.setColor(AcademyTheme.MUTED);
        p.drawString("空格 · 下一幕    Esc · 跳过整段", 30, h - 30);
    }


    /**
     * The letter is painted once into a bitmap at device resolution and only that bitmap floats.
     * Drawing the words live under a changing rotation snaps every glyph to new pixels each frame,
     * which reads as trembling text.
     */
    private static void letter(Graphics2D graphics, double x, double y, double width, double seconds, boolean unfold) {
        double scale = Math.sqrt(Math.abs(graphics.getTransform().getDeterminant()));
        if (scale <= 0)
            scale = 1;
        int w = (int) Math.round(width);
        int h = (int) Math.round(width * (unfold ? 0.66 : 0.64));
        BufferedImage paper = paper(w, h, unfold, scale);
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.translate(x, y + Math.sin(t * 1.1) * 3.5);
        g.rotate(Math.sin(t * .6) * .012);
        g.scale(1 / scale, 1 / scale);
        g.drawImage(paper, -paper.getWidth() / 2, -paper.getHeight() / 2, null);
        g.dispose();
    }

    private static BufferedImage paper(int w, int h, boolean unfold, double scale) {
        String key = w + "x" + h + (unfold ? "u" : "f") + Math.round(scale * 100);
        BufferedImage cached = PAPERS.get(key);
        if (cached != null)
            return cached;
        int margin = 22;
        BufferedImage image = new BufferedImage((int) Math.ceil((w + margin * 2) * scale),
                (int) Math.ceil((h + margin * 2) * scale), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(scale, scale);
        g.translate(margin + w / 2.0, margin + h / 2.0);
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(0, 0, 0, 16));
            g.fillRoundRect(-w / 2 + 3 + i, -h / 2 + 6 + i, w, h, 10, 10);
        }
        g.setPaint(new GradientPaint(0, -h / 2f, new Color(0xEADBB6), 0, h / 2f, new Color(0xD8C397)));
        g.fillRoundRect(-w / 2, -h / 2, w, h, 5, 5);
        // a few fibres and a water mark keep the paper from looking like a plain card
        java.util.Random fibres = new java.util.Random(w * 31L + h);
        for (int i = 0; i < w / 6; i++) {
            g.setColor(new Color(120, 92, 52, 10 + fibres.nextInt(14)));
            double fx = -w / 2.0 + fibres.nextDouble() * w;
            double fy = -h / 2.0 + fibres.nextDouble() * h;
            g.draw(new java.awt.geom.Line2D.Double(fx, fy, fx + fibres.nextDouble() * 18 - 9, fy + fibres.nextDouble() * 4 - 2));
        }
        g.setColor(new Color(0xB5965F));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(-w / 2 + 9, -h / 2 + 9, w - 18, h - 18, 4, 4);
        if (unfold) {
            int textX = -w / 2 + 32;
            int top = -h / 2;
            g.setColor(new Color(0x302B32));
            g.setFont(AcademyTheme.script(28));
            g.drawString("岗位 B 的手书", textX, top + 52);
            g.setColor(new Color(0xB5965F));
            g.drawLine(textX, top + 66, w / 2 - 32, top + 66);
            g.setColor(new Color(0x302B32));
            g.setFont(AcademyTheme.font(Font.PLAIN, 18));
            g.drawString("零点整，我在岗，钥印在手。", textX, top + 106);
            g.setColor(new Color(0x4A3B33));
            g.setFont(AcademyTheme.font(Font.PLAIN, 16));
            g.drawString("讯线一直没有亮，只好写下来，托芙送去。", textX, top + 142);
            g.setColor(new Color(0x77664C));
            g.setFont(AcademyTheme.script(20));
            String sign = "—— 岗位 B · 值夜人";
            g.drawString(sign, w / 2 - 32 - g.getFontMetrics().stringWidth(sign), top + 188);
            g.setColor(new Color(0x9A3B2E));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(w / 2 - 74, top + 200, 40, 40);
            g.setFont(AcademyTheme.script(19));
            g.drawString("印", w / 2 - 63, top + 227);
        } else {
            g.drawLine(-w / 2, -h / 2, 0, 12);
            g.drawLine(w / 2, -h / 2, 0, 12);
            g.setColor(new Color(0x85533B));
            g.fillOval(-12, -2, 24, 24);
            g.setColor(AcademyTheme.GOLD_HI);
            g.drawOval(-7, 3, 14, 14);
        }
        g.dispose();
        if (PAPERS.size() > 12)
            PAPERS.clear();
        PAPERS.put(key, image);
        return image;
    }

    /**
     * The words of one chapter's prologue and of the two scenes after its repair, read from the
     * {@code prologue.*} and {@code epilogue.*} keys of the story file. Missing keys keep the words of the
     * archive door. A lamp chapter names its drawn room; the archive chapter uses the painted archive.
     */
    static final class Script {
        /** The archive door chapter. */
        static final Script ARCHIVE = new Script(new Properties(), null);

        private final String header;
        private final String successHeader;
        private final String successTitle;
        private final String[] titles;
        private final String[] texts;
        private final String[] successTexts;
        private final String scene;

        /**
         * @param story the story properties of the level
         * @param scene the scene key of a lamp level, null for the archive
         */
        Script(Properties story, String scene) {
            this.scene = scene;
            header = story.getProperty("prologue.header", "序幕 · 午夜来信");
            successHeader = story.getProperty("epilogue.header", "校准之后");
            successTitle = story.getProperty("epilogue.title", "让记录重新诚实");
            titles = read(story, "prologue.title.", "风里的来信", "空白也是一种线索", "先听见，再相信", "向旧门求证");
            texts = read(story, "prologue.text.",
                    "午夜钟声落下时，芙带来了一封迟到的信。",
                    "来信和档案写着不同的事。窗外的风，翻动了那页空白。",
                    "微光停在残页旁：先别相信任何一种说法，我们可以亲自试验。",
                    "旧门仍会回应。你选怎样的报告，来问出它真正的规则？");
            successTexts = read(story, "epilogue.text.",
                    "新的档案门回应了全部四组检验。规则终于与术式一致。",
                    "门后的校准页还在等你。已经知道的，与仍不清楚的，都要如实留下。");
        }

        private static String[] read(Properties story, String prefix, String... defaults) {
            String[] r = new String[defaults.length];
            for (int i = 0; i < r.length; i++) {
                String v = story.getProperty(prefix + (i + 1));
                r[i] = v == null || v.trim().isEmpty() ? defaults[i] : v.trim();
            }
            return r;
        }
    }
}
