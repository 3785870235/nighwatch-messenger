/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Character dialogue of the investigation: one speaker and one or two sentences per page,
 * reversible and skippable. It only shows story text; it reads no model and decides nothing.
 */
final class AcademyStory extends JPanel {
    private final JLabel number = AcademyTheme.label("", AcademyTheme.font(Font.PLAIN, 13), AcademyTheme.GOLD);
    private final JLabel speaker = AcademyTheme.label("", AcademyTheme.script(30), AcademyTheme.GOLD_HI);
    private final AcademyTheme.Text text = new AcademyTheme.Text("", AcademyTheme.font(Font.PLAIN, 21), AcademyTheme.IVORY, 760);
    private final JButton previous = AcademyTheme.button("← 上一句", AcademyTheme.Kind.QUIET);
    private final JButton skip = AcademyTheme.button("跳过对白", AcademyTheme.Kind.QUIET);
    private final JButton next = AcademyTheme.button("继续 →", AcademyTheme.Kind.PRIMARY);
    private final Stage stage = new Stage();
    private List<Investigation.Line> lines;
    private String heading = "";
    private String finishText = "继续 →";
    private String backdrop = AcademyArt.ARCHIVE_CLOSED;
    private boolean reveal;
    private int index;
    private Runnable finished;

    AcademyStory() {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(AcademyTheme.pad(18, 90, 28, 90));
        add(number, BorderLayout.NORTH);
        add(stage, BorderLayout.CENTER);
        AcademyTheme.Card box = new AcademyTheme.Card(new BorderLayout(0, 8));
        box.setBorder(AcademyTheme.pad(16, 26, 16, 26));
        box.add(speaker, BorderLayout.NORTH);
        box.add(text, BorderLayout.CENTER);
        JPanel controls = AcademyTheme.clear(new BorderLayout());
        controls.add(previous, BorderLayout.WEST);
        JPanel right = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(skip);
        right.add(next);
        controls.add(right, BorderLayout.EAST);
        box.add(controls, BorderLayout.SOUTH);
        add(box, BorderLayout.SOUTH);
        previous.addActionListener(e -> AcademyTransition.change(this, () -> {
            index = Math.max(0, index - 1);
            refresh();
        }));
        next.addActionListener(e -> {
            if (index + 1 >= lines.size())
                finished.run();
            else
                AcademyTransition.change(this, () -> {
                    index++;
                    refresh();
                });
        });
        skip.addActionListener(e -> finished.run());
    }

    /**
     * Opens a dialogue at its first page.
     *
     * @param heading    e.g. "疑点"
     * @param lines      the pages
     * @param backdrop   the scene image name in the art directory
     * @param finishText the text of the last button
     * @param finished   called when the dialogue is finished or skipped
     */
    void open(String heading, List<Investigation.Line> lines, String backdrop, String finishText, Runnable finished) {
        open(heading, lines, backdrop, finishText, heading.contains("校准"), finished);
    }

    /**
     * Opens a dialogue at its first page.
     *
     * @param heading    e.g. "疑点"
     * @param lines      the pages
     * @param backdrop   the scene image name in the art directory, or a drawn lamp room of {@link AcademyLampScene}
     * @param finishText the text of the last button
     * @param reveal     the dialogue follows a passed spell; the characters take their relieved poses
     * @param finished   called when the dialogue is finished or skipped
     */
    void open(String heading, List<Investigation.Line> lines, String backdrop, String finishText, boolean reveal,
              Runnable finished) {
        this.heading = heading;
        this.lines = lines;
        this.backdrop = backdrop;
        this.finishText = finishText;
        this.reveal = reveal;
        this.finished = finished;
        index = 0;
        refresh();
    }

    private void refresh() {
        Investigation.Line line = lines.get(index);
        number.setText(heading + "  /  " + (index + 1) + " · " + lines.size());
        speaker.setText(role(line.getSpeaker()));
        text.setText(line.getText());
        previous.setEnabled(index > 0);
        boolean last = index + 1 == lines.size();
        next.setText(last ? finishText : "继续 →");
        skip.setVisible(!last);
        AcademySound.cue("page");
        stage.repaint();
    }

    private static String role(String name) {
        switch (name) {
            case "芙":
                return "芙 · 巡夜信使";
            case "微光":
                return "微光 · 档案精灵";
            case "洛":
                return "洛 · 守档人";
            default:
                return name;
        }
    }

    String heading() {
        return heading;
    }

    int page() {
        return index;
    }

    int pages() {
        return lines == null ? 0 : lines.size();
    }

    String lineText() {
        return speaker.getText() + "：" + text.getText();
    }

    JButton nextButton() {
        return next;
    }

    JButton previousButton() {
        return previous;
    }

    JButton skipButton() {
        return skip;
    }

    /** The scene: the archive behind the current speaker, drawn with the existing art and characters. */
    private final class Stage extends JPanel {
        private Stage() {
            setOpaque(false);
            AcademyAtmosphere.watch(this, () -> { });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (lines == null)
                return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            double seconds = System.nanoTime() / 1000000000.0;
            g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 22, 22));
            boolean drawn = AcademyLampScene.isBackdrop(backdrop);
            if (drawn)
                AcademyLampScene.paintBackdrop(g, w, h, backdrop, seconds);
            else {
                BufferedImage scene = AcademyArt.get(backdrop);
                if (scene != null)
                    AcademyArt.drawCover(g, scene, 0, 0, w, h, 0.5);
            }
            // the drawn lamp rooms are darker than the paintings; a lighter veil keeps the lamps readable
            g.setPaint(new GradientPaint(0, 0, new Color(8, 14, 26, drawn ? 60 : 120), w * 0.6f, 0,
                    new Color(8, 14, 26, drawn ? 150 : 215)));
            g.fillRect(0, 0, w, h);
            AcademyAtmosphere.dust(g, w, h, seconds, true);
            Investigation.Line line = lines.get(index);
            String who = line.getSpeaker();
            // a line that names its pose gets it; the others keep the order-based poses they always had
            int named = AcademyCharacters.namedPose(who, line.getPose());
            int ph = (int) (h * 0.92);
            if ("芙".equals(who))
                AcademyCharacters.messengerPose(g, w * 0.12, h - ph, ph, seconds, named >= 0 ? named : Math.min(3, index));
            else if ("微光".equals(who))
                AcademyCharacters.spiritPose(g, w * 0.24, h * 0.5, h * 0.70, seconds,
                        reveal ? 3 : index % 3, AcademyPointer.local(this));
            else
                AcademyCharacters.keeper(g, w * 0.10, h - ph, ph, seconds,
                        named >= 0 ? named : reveal ? 3 : Math.min(2, index));
            g.dispose();
        }
    }
}
