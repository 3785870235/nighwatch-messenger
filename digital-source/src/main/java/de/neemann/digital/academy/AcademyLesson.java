/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;

/** Reversible, single-action lessons. Illustrations are examples, never the hidden circuit. */
final class AcademyLesson extends JPanel {
    private String[][] experiment = experiment("门", "门前", "门规", 2, 1);
    private String[][] repair = repair("门", "门前", Collections.singletonList("双印符文"), 2);
    private String[][] pages = experiment;
    private final JLabel number = AcademyTheme.label("", AcademyTheme.font(Font.PLAIN, 13), AcademyTheme.GOLD);
    private final JLabel title = AcademyTheme.label("", AcademyTheme.script(38), AcademyTheme.IVORY);
    private final AcademyTheme.Text text = new AcademyTheme.Text("", AcademyTheme.font(Font.PLAIN, 20), AcademyTheme.IVORY, 620);
    private final JButton previous = AcademyTheme.button("← 上一步", AcademyTheme.Kind.QUIET);
    private final JButton skip = AcademyTheme.button("跳过小课", AcademyTheme.Kind.QUIET);
    private final JButton next = AcademyTheme.button("下一步 →", AcademyTheme.Kind.PRIMARY);
    private final Diagram diagram = new Diagram();
    private int index;
    private Runnable finished;
    private boolean repairing;

    AcademyLesson() {
        super(new BorderLayout(0, 22));
        setOpaque(false);
        setBorder(AcademyTheme.pad(36, 150, 36, 150));
        JPanel heading = AcademyTheme.clear(new BorderLayout(0, 10));
        heading.add(number, BorderLayout.NORTH);
        heading.add(title, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);
        JPanel center = AcademyTheme.clear(new BorderLayout(0, 18));
        center.add(diagram, BorderLayout.CENTER);
        center.add(text, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        JPanel controls = AcademyTheme.clear(new BorderLayout());
        controls.add(previous, BorderLayout.WEST);
        JPanel right = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(skip);
        right.add(next);
        controls.add(right, BorderLayout.EAST);
        add(controls, BorderLayout.SOUTH);
        previous.addActionListener(e -> AcademyTransition.change(this, () -> {
            index--;
            refresh();
        }));
        next.addActionListener(e -> {
            if (index + 1 == pages.length)
                finished.run();
            else
                AcademyTransition.change(this, () -> {
                    index++;
                    refresh();
                });
        });
        // the lesson can be read again from the menu, so leaving it early loses nothing
        skip.addActionListener(e -> finished.run());
    }

    /**
     * Lets the lessons speak the words of the chapter.
     *
     * @param device      the device of the level, e.g. 档案门
     * @param place       where the device is tried, e.g. 门前
     * @param rule        how the level calls its rule, e.g. 门规
     * @param runes       the runes the repair stage asks for, e.g. 双印符文; one per lamp if there are several
     * @param stationCount how many stations the level listens to
     * @param lampCount   how many lamps answer together
     */
    void words(String device, String place, String rule, List<String> runes, int stationCount, int lampCount) {
        this.experiment = experiment(device, place, rule, stationCount, lampCount);
        this.repair = repair(device, place, runes, stationCount);
    }

    private static String[][] experiment(String device, String place, String rule, int stations, int lamps) {
        String guess = lamps > 1 ? "最后一组先选" + device + "会怎样回应——哪一盏亮、哪一盏暗"
                : "最后一组先选" + device + "会" + lit(device) + "还是" + dark(device);
        return new String[][]{
                {"一份报告", "岗位持有钥印，报告为 1；没有钥印，报告为 0。讯息线传递这件事是否成立，不搬运钥印。"},
                {"亲眼观察", "切换一个岗位的钥印，再点「观察回应」。没观察过的回应是未知，不是关闭。"},
                {"先猜，再看", "比较已观察的记录。" + guess + "，再观察；猜错也会如实留下线索。"},
                {"去" + place + "试一试", "点「观察机关」，切换"
                        + (stations > 1 ? "两份报告" : "报告") + "，看你的术式怎样驱动" + device
                        + "。回来修改，再用「检验我的术式」检查全部组合。"},
                {"交给检验处", "检验处照你的术式另刻一份副本，把每一种情形都试一遍，再照" + rule + "逐行对给你看。"}};
    }

    private static String[][] repair(String device, String place, List<String> runes, int stations) {
        boolean several = runes.size() > 1;
        String both = stations > 1 ? "两处岗位各接一个端点；" : "岗位的发讯端接一个端点；";
        String place1 = several ? "点「放置" + String.join("」和「放置", runes) + "」，各在画布空白处单击放下一枚。位置由你决定。"
                : "点「放置" + runes.get(0) + "」，移到画布空白处，单击放下。位置由你决定。";
        String hand = several ? "每处岗位都要接到每一枚符文；每枚符文的发讯端，接向它自己那盏灯的许可印。"
                : both + "符文的发讯端接向许可印。";
        return new String[][]{
                {several ? "放下符文" : "放下一枚符文", place1},
                {"从端点出发", "单击岗位旁的发讯端，松开鼠标。移动鼠标，讯息线会跟随；不需要按住拖动。"},
                {"让线转个弯", "在空白处单击，留下一个转角，再继续移动。需要取消时按 Esc。"},
                {"交给接收端", "在符文的接收端上单击，完成连接。" + hand},
                {"去" + place + "试一试", "点「观察机关」，看你的术式怎样驱动" + device
                        + "。回来修改，再用「检验我的术式」检查全部组合。"}};
    }

    /** What a lit device does in the words of the chapter: a door opens, a lamp lights up. */
    private static String lit(String device) {
        return device.endsWith("灯") ? "亮" : "打开";
    }

    /** What a dark device does: a door stays shut, a lamp stays dark. */
    private static String dark(String device) {
        return device.endsWith("灯") ? "保持暗" : "保持关闭";
    }

    void open(boolean repair, Runnable finished) {
        this.repairing = repair;
        this.pages = repair ? this.repair : this.experiment;
        this.finished = finished;
        index = 0;
        refresh();
    }

    private void refresh() {
        number.setText("微光的小课  /  " + (index + 1) + " · " + pages.length);
        title.setText(pages[index][0]);
        text.setText(pages[index][1]);
        previous.setEnabled(index > 0);
        boolean last = index + 1 == pages.length;
        next.setText(last ? "回到现场 →" : "下一步 →");
        skip.setVisible(!last);
        diagram.repaint();
    }

    int page() {
        return index;
    }

    JButton previousButton() {
        return previous;
    }

    JButton skipButton() {
        return skip;
    }

    JButton nextButton() {
        return next;
    }

    private final class Diagram extends JPanel {
        private Diagram() {
            setOpaque(false);
            AcademyAtmosphere.watch(this, () -> { });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = getWidth() / 2;
            int y = getHeight() / 2;
            double seconds = System.nanoTime() / 1000000000.0;
            // 专注: Glimmer follows the lesson
            AcademyCharacters.spirit(g, getWidth() - 65, y - 12, 130, seconds, 4, AcademyPointer.local(this));
            g.setColor(AcademyTheme.GOLD);
            g.setStroke(new BasicStroke(2));
            if (!repairing) {
                for (int i = 0; i < 2; i++) {
                    int cx = x - 110 + i * 190;
                    g.drawOval(cx - 37, y - 37, 74, 74);
                    g.setFont(AcademyTheme.script(38));
                    g.drawString(i == 0 ? "0" : "1", cx - 10, y + 14);
                }
                g.setFont(AcademyTheme.font(Font.PLAIN, 14));
                g.setColor(AcademyTheme.MUTED);
                g.drawString(index == 0 ? "不成立                    成立" : index == 1 ? "改变报告  →  观察回应" : "你的预测  →  真实回应", x - 122, y + 80);
            } else {
                g.drawRoundRect(x - 42, y - 58, 84, 116, 12, 12);
                g.setFont(AcademyTheme.script(26));
                g.drawString("符文", x - 26, y + 9);
                g.setColor(AcademyTheme.CYAN);
                g.fillOval(x - 48, y - 24, 12, 12);
                if (index > 0) {
                    g.fillOval(x - 185, y - 73, 12, 12);
                    int end = x - 42;
                    if (index == 1) {
                        end = x - 130;
                        g.drawLine(x - 179, y - 67, end, y - 18);
                    } else {
                        g.drawLine(x - 179, y - 67, x - 94, y - 67);
                        g.drawLine(x - 94, y - 67, x - 94, y - 18);
                        g.drawLine(x - 94, y - 18, end, y - 18);
                    }
                    if (index == 2) {
                        g.setColor(new Color(240, 203, 126, 65));
                        g.fillOval(x - 106, y - 79, 24, 24);
                    }
                }
            }
            g.dispose();
        }
    }
}
