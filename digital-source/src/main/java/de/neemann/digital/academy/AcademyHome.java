/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.gui.Main;
import de.neemann.gui.ClosingWindowListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * The Chinese home window: level mode or free mode. Only implemented levels are offered.
 */
public final class AcademyHome extends JFrame {
    private final File levelFile;
    private final File workDir;
    private final File sandbox;
    private final Main.FileSavePolicy policy;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private JPanel levelsPanel;
    private int openModes;
    private AcademyPanel activeLevel;
    /**
     * Chapters passed since the window opened. Every chapter is open from the start; this only marks
     * a passed chapter on its card right away — the progress file alone would show it only after the
     * player saved and restarted the game.
     */
    private final Set<String> passedNow = new HashSet<>();
    /** The chapter to open as soon as the closing level window is gone, or null to show the home. */
    private String pendingNext;

    /**
     * Creates the home window.
     *
     * @param levelFile the level.properties of the implemented level
     * @param workDir   the work directory
     * @param policy    the save policy for all circuit windows
     */
    public AcademyHome(File levelFile, File workDir, Main.FileSavePolicy policy) {
        super("午夜光术学院");
        this.levelFile = levelFile;
        this.workDir = workDir;
        this.sandbox = new File(workDir, "sandbox");
        this.policy = policy;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                AcademyTransition.enter(getRootPane());
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (openModes > 0 || otherCircuitWindowOpen()) {
                    JOptionPane.showMessageDialog(AcademyHome.this, "还有电路窗口未关闭，请先关闭它们。");
                    return;
                }
                dispose();
                System.exit(0);
            }
        });

        content.add(createHome(), "home");
        levelsPanel = createLevels();
        content.add(levelsPanel, "levels");
        setContentPane(content);
        AcademyMain.fitWindow(this);
    }

    private JPanel createHome() {
        JPanel home = new ArtBackdrop();
        home.setLayout(new GridBagLayout());
        JPanel col = AcademyTheme.clear(null);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(left(AcademyTheme.label("MIDNIGHT  LOGIC  ACADEMY", AcademyTheme.font(Font.BOLD, 14), AcademyTheme.GOLD)));
        col.add(Box.createVerticalStrut(6));
        col.add(left(AcademyTheme.label("午夜光术学院", AcademyTheme.script(60), AcademyTheme.IVORY)));
        col.add(Box.createVerticalStrut(14));
        AcademyTheme.Text story = new AcademyTheme.Text(
                "午夜之后，有些报告不再可信。\n今晚，你替学院值夜。",
                AcademyTheme.font(Font.PLAIN, 17), AcademyTheme.IVORY, 520);
        story.setMaximumSize(new Dimension(560, 120));
        col.add(left(story));
        col.add(Box.createVerticalStrut(30));
        JPanel modes = AcademyTheme.clear(new GridLayout(2, 1, 0, 10));
        JButton levelMode = AcademyTheme.button("开始探索", AcademyTheme.Kind.PRIMARY);
        levelMode.setName("开始探索");
        levelMode.addActionListener(e -> AcademyTransition.change(content, () -> cards.show(content, "levels")));
        modes.add(levelMode);
        JButton freeMode = AcademyTheme.button("自由工坊", AcademyTheme.Kind.QUIET);
        freeMode.addActionListener(e -> openFree(null));
        modes.add(freeMode);
        modes.setMaximumSize(new Dimension(290, 116));
        col.add(left(modes));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        c.weighty = 1;
        c.insets = new Insets(0, 72, 0, 0);
        home.add(col, c);
        return home;
    }

    private JPanel createLevels() {
        JPanel levels = new AcademyTheme.Backdrop(new BorderLayout(0, 18));
        levels.setBorder(AcademyTheme.pad(32, 56, 32, 56));
        JPanel top = AcademyTheme.clear(new BorderLayout());
        JPanel titles = AcademyTheme.clear(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(AcademyTheme.label("关卡模式", AcademyTheme.font(Font.BOLD, 14), AcademyTheme.GOLD));
        titles.add(AcademyTheme.label("选择章节", AcademyTheme.script(40), AcademyTheme.IVORY));
        top.add(titles, BorderLayout.WEST);
        JButton back = AcademyTheme.button("← 返回首页", AcademyTheme.Kind.QUIET);
        back.addActionListener(e -> AcademyTransition.change(content, () -> cards.show(content, "home")));
        JPanel nav = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT));
        nav.add(back);
        top.add(nav, BorderLayout.EAST);
        levels.add(top, BorderLayout.NORTH);

        JPanel grid = AcademyTheme.clear(new GridLayout(3, 2, 24, 18));
        // Every chapter is open from the start and can be played in any order; a passed chapter is
        // marked on its card, and a chapter whose files are not there yet stays a closed card.
        for (int i = 0; i < CHAPTERS.length; i++)
            grid.add(chapter(i));
        levels.add(grid, BorderLayout.CENTER);
        return levels;
    }

    /** The chapters in story order: level id, chapter line, the line shown while the chapter is missing. */
    private static final String[][] CHAPTERS = {
            {"01_report_light", "第一章 · 值房的灯", "零点以后，值房的两盏报告灯一直暗着。"},
            {"02_reminder_light", "第二章 · 信使站", "铃整夜没响，器物登记册却说它已停用。"},
            {"03_archive_gate", "第三章 · 档案馆", "午夜的报告，和档案里的记载对不上。"},
            {"04_registry_desk", "第四章 · 单席登记台", "那一夜，同一个席位登记了两个人。"},
            {"05_star_lamps", "第五章 · 星灯归位", "把这一夜的报告，从头送一遍。"},
    };

    /** @return true if the chapter at this index exists; no chapter waits for the one before it */
    private boolean isOpen(int index) {
        return readLevel(CHAPTERS[index][0]) != null;
    }

    /**
     * @param id a level id
     * @return the index of the chapter that follows it and exists, or -1
     */
    private int nextChapter(String id) {
        for (int i = 0; i + 1 < CHAPTERS.length; i++)
            if (CHAPTERS[i][0].equals(id))
                return readLevel(CHAPTERS[i + 1][0]) == null ? -1 : i + 1;
        return -1;
    }

    /**
     * One chapter card. The title and the blurb come from the chapter's own level.properties, so a
     * rewritten chapter can never disagree with the card that opens it.
     *
     * @param index the chapter index
     * @return the card button
     */
    private JButton chapter(int index) {
        String id = CHAPTERS[index][0];
        LevelDefinition level = readLevel(id);
        boolean open = level != null;
        String kicker = CHAPTERS[index][1];
        String title = open ? level.getTitle() : kicker.substring(kicker.indexOf('·') + 1).trim();
        String blurb = open ? level.getStory() : CHAPTERS[index][2];
        BigCard card = new BigCard(title, kicker(index), blurb, open);
        card.setPreferredSize(new Dimension(550, 150));
        if (open)
            card.addActionListener(e -> openLevel(id));
        else
            card.setToolTipText("这一章还在写");
        return card;
    }

    /**
     * The chapter line of a card: a passed chapter says so, an unplayed one carries no state, and
     * only a chapter whose files are missing is not open yet.
     *
     * @param index the chapter index
     * @return e.g. 第一章 · 值房的灯 · 已通过
     */
    private String kicker(int index) {
        String id = CHAPTERS[index][0];
        if (readLevel(id) == null)
            return CHAPTERS[index][1] + " · 尚未开放";
        return CHAPTERS[index][1] + (chapterPassed(id) ? " · 已通过" : "");
    }

    private LevelDefinition readLevel(String id) {
        File file = new File(new File(levelFile.getParentFile().getParentFile(), id), "level.properties");
        if (!file.isFile())
            return null;
        try {
            return LevelDefinition.load(file);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The open state of the chapter cards, in chapter order, exactly as {@link #createLevels()}
     * builds them. Only used by the automated check.
     *
     * @return e.g. "[true, true, true, true, true]"
     */
    String chapterStates() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < CHAPTERS.length; i++)
            sb.append(i > 0 ? ", " : "").append(isOpen(i));
        return sb.append(']').toString();
    }

    /**
     * The chapter lines of the cards, one per line, in chapter order. Only used by the automated check.
     *
     * @return the chapter lines
     */
    String cardKickers() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CHAPTERS.length; i++)
            sb.append(i > 0 ? "\n" : "").append(kicker(i));
        return sb.toString();
    }

    /**
     * The level window that is currently open, if any. Only used by the automated check.
     *
     * @return the open level panel or null
     */
    AcademyPanel activeLevel() {
        return activeLevel;
    }

    /** Shows the chapter cards without a transition. Only used by the automated check. */
    void showLevels() {
        cards.show(content, "levels");
        content.revalidate();
    }

    /**
     * The titles of the chapter cards whose files exist, one per line. Only used by the automated check.
     *
     * @return the card titles
     */
    String cardTitles() {
        StringBuilder sb = new StringBuilder();
        for (String[] chapter : CHAPTERS) {
            LevelDefinition level = readLevel(chapter[0]);
            if (level == null)
                continue;
            if (sb.length() > 0)
                sb.append('\n');
            sb.append(level.getTitle());
        }
        return sb.toString();
    }

    /**
     * @param id a level id
     * @return true if that level was passed in this session or in a saved progress file;
     *         a chapter that can not be read stays closed
     */
    private boolean chapterPassed(String id) {
        if (passedNow.contains(id))
            return true;
        LevelDefinition level = readLevel(id);
        if (level == null)
            return false;
        try {
            AcademyProgress progress = new AcademyProgress(new File(workDir, "progress"), level);
            if (!progress.getFile().isFile())
                return false;
            try (ReferenceDevice device = new ReferenceDevice(level)) {
                return progress.restore(device::evaluate).isPassedOnce();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Rebuilds the chapter cards from the current state. A chapter that was passed while a level
     * window was open is marked on its card, so the cards can not be built once and kept.
     * Must be called on the EDT.
     */
    private void refreshLevels() {
        levelsPanel = createLevels();
        content.remove(1);
        content.add(levelsPanel, "levels", 1);
        content.revalidate();
        content.repaint();
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    /**
     * The home backdrop: the menu art if available, otherwise a gradient; always darkened
     * on the left so that the text stays readable.
     */
    private static final class ArtBackdrop extends JPanel {
        private ArtBackdrop() {
            AcademyAtmosphere.watch(this, () -> { });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            paintBackdrop(g2, getWidth(), getHeight(), System.nanoTime() / 1000000000.0);
            g2.dispose();
        }
    }

    /**
     * Paints the home backdrop at a given time; the check renders frames with it.
     *
     * @param g2 the graphics
     * @param w  the width
     * @param h  the height
     * @param t  the time in seconds
     */
    static void paintBackdrop(Graphics2D g2, int w, int h, double t) {
        BufferedImage img = AcademyArt.get(AcademyArt.MENU_BACKGROUND);
        if (img != null) {
            AcademyArt.drawCover(g2, img, 0, 0, w, h, 0.6);
            AcademyHomeMotion.behind(g2, img, w, h, 0.6, t);
        } else {
            g2.setPaint(new GradientPaint(0, 0, new Color(0x1B2D4D), w, h, AcademyTheme.NIGHT));
            g2.fillRect(0, 0, w, h);
        }
        g2.setPaint(new GradientPaint(0, 0, new Color(8, 14, 26, 235), w * 0.7f, 0, new Color(8, 14, 26, 60)));
        g2.fillRect(0, 0, w, h);
        AcademyHomeMotion.front(g2, img, w, h, 0.6, t);
        AcademyAtmosphere.dust(g2, w, h, t, false);
    }

    /**
     * A large code-rendered card button; disabled cards are shown as not yet open.
     */
    private static final class BigCard extends JButton {
        private final String kicker;
        private final String detail;

        private BigCard(String title, String kicker, String detail, boolean open) {
            super(title);
            this.kicker = kicker;
            this.detail = detail;
            setEnabled(open);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(open ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            setPreferredSize(new Dimension(290, 140));
            setToolTipText(open ? detail : "这一章还没有开放");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean open = isEnabled();
            boolean hover = open && getModel().isRollover();
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(!open ? new Color(18, 30, 48, 220) : hover ? new Color(0x26406A) : new Color(24, 42, 66, 235));
            g2.fillRoundRect(0, 0, w, h, 20, 20);
            g2.setColor(!open ? AcademyTheme.LINE : hover || isFocusOwner() ? AcademyTheme.GOLD_HI : AcademyTheme.GOLD);
            g2.setStroke(new BasicStroke(open ? 1.8f : 1.2f));
            g2.drawRoundRect(0, 0, w, h, 20, 20);
            int x = 22;
            g2.setFont(AcademyTheme.font(Font.BOLD, 14));
            g2.setColor(open ? AcademyTheme.CYAN : AcademyTheme.MUTED);
            g2.drawString(kicker, x, 34);
            g2.setFont(AcademyTheme.script(30));
            g2.setColor(open ? AcademyTheme.IVORY : AcademyTheme.MUTED);
            g2.drawString(getText(), x, 70);
            g2.setFont(AcademyTheme.font(Font.PLAIN, 14));
            g2.setColor(open ? new Color(0xD8DEE8) : new Color(0x7D8BA0));
            drawWrapped(g2, open ? detail : "— " + detail + " —", x, 98, w - 2 * x);
            if (open) {
                g2.setFont(AcademyTheme.font(Font.BOLD, 22));
                g2.setColor(AcademyTheme.GOLD_HI);
                g2.drawString("→", w - 40, 36);
            }
            g2.dispose();
        }

        private static void drawWrapped(Graphics2D g2, String text, int x, int y, int width) {
            FontMetrics fm = g2.getFontMetrics();
            StringBuilder line = new StringBuilder();
            for (char ch : text.toCharArray()) {
                if (fm.stringWidth(line.toString() + ch) > width) {
                    g2.drawString(line.toString(), x, y);
                    y += fm.getHeight();
                    line.setLength(0);
                }
                line.append(ch);
            }
            g2.drawString(line.toString(), x, y);
        }
    }

    private static boolean otherCircuitWindowOpen() {
        for (Window w : Window.getWindows())
            if (w instanceof Main && w.isDisplayable())
                return true;
        return false;
    }

    /**
     * Opens the chapter the home was started with. Must be called on the EDT.
     *
     * @return the level panel or null if loading failed
     */
    AcademyPanel openLevel() {
        return openLevel(null);
    }

    /**
     * Opens one chapter. Must be called on the EDT.
     *
     * @param id the level id, or null for the chapter the home was started with
     * @return the level panel or null if loading failed
     */
    AcademyPanel openLevel(String id) {
        if (activeLevel != null && activeLevel.getMain().isDisplayable()) {
            activeLevel.getMain().setVisible(true);
            activeLevel.getMain().toFront();
            activeLevel.replayIntro();
            setVisible(false);
            return activeLevel;
        }
        try {
            File file = levelFile;
            if (id != null)
                file = new File(new File(levelFile.getParentFile().getParentFile(), id), "level.properties");
            LevelDefinition level = LevelDefinition.load(file);
            AcademyProgress progress = new AcademyProgress(new File(workDir, "progress"), level);
            File work = progress.savedCircuit();
            if (work == null)
                work = AcademyMain.prepareWorkFile(level, workDir);
            AcademyPanel panel = AcademyMain.open(level, work, progress, policy);
            Main main = panel.getMain();
            activeLevel = panel;
            main.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (panel.isPassedOnce())
                        passedNow.add(level.getId());
                    if (activeLevel == panel)
                        activeLevel = null;
                }
            });
            panel.setNavigation(() -> leave(main), () -> copyToFree(panel, level));
            int next = nextChapter(level.getId());
            if (next >= 0) {
                String nextId = CHAPTERS[next][0];
                String kicker = CHAPTERS[next][1];
                panel.setNextChapter("下一章：" + kicker.substring(kicker.indexOf('·') + 1).trim() + " →",
                        () -> advance(panel, level, nextId));
            } else if (CHAPTERS[CHAPTERS.length - 1][0].equals(level.getId()))
                // the last chapter ends the night: its clues are read out, then the player is back home
                panel.setNextChapter("天亮了 · 回到学院 →", () -> advance(panel, level, null));
            showMode(main);
            return panel;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "关卡加载失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * Opens the free mode, the unmodified Digital editor with the save policy. Must be called on the EDT.
     *
     * @param file the file to open, null for a new circuit
     * @return the window or null if the sandbox could not be created
     */
    Main openFree(File file) {
        try {
            checkSandbox();
            Files.createDirectories(sandbox.toPath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "无法创建 work\\sandbox：" + e.getMessage());
            return null;
        }
        Main.MainBuilder b = new Main.MainBuilder()
                .setLibrary(new ElementLibrary())
                .setShapeDecorator(MagicShape::decorate)
                .setSavePolicy(policy);
        if (file != null)
            b.setFileToOpen(file);
        else
            b.setCircuit(new Circuit()).setBaseFileName(new File(sandbox, "untitled.dig"));
        AcademyTheme.applyCanvasColors();
        Main main = b.build();
        JMenu menu = new JMenu("学院");
        JMenuItem home = new JMenuItem("返回首页");
        home.addActionListener(e -> leave(main));
        menu.add(home);
        main.getJMenuBar().add(menu);
        main.setContentPane(new AcademyWorkshop(main, () -> leave(main)));
        AcademyMain.fitWindow(main);
        showMode(main);
        return main;
    }

    /**
     * Leaves a passed chapter for the next one. The window closes through the same native
     * save/discard/cancel question as "返回首页"; nothing is saved without the player's answer, and
     * if the player cancels, the chapter stays open and the next one is not started.
     */
    private void advance(AcademyPanel panel, LevelDefinition level, String nextId) {
        if (panel.isPassedOnce())
            passedNow.add(level.getId());
        pendingNext = nextId;
        if (!leave(panel.getMain()))
            pendingNext = null;
    }

    private void showMode(Main main) {
        openModes++;
        main.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                openModes--;
                // The chapter just played may have been passed: rebuild the cards.
                refreshLevels();
                String next = pendingNext;
                pendingNext = null;
                if (next != null && openLevel(next) != null)
                    return;
                setVisible(true);
                AcademyTransition.enter(getRootPane());
            }
        });
        setVisible(false);
        main.setVisible(true);
        AcademyTransition.enter(main.getRootPane());
    }

    /**
     * Leaves a mode window after the native cancel/save/discard question.
     * If the player cancels or saving fails, the window and its model stay untouched.
     *
     * @param main the window
     * @return true if the window was closed
     */
    static boolean leave(Main main) {
        if (!ClosingWindowListener.checkForSave(main, main))
            return false;
        if (main.getContentPane() instanceof AcademyPanel)
            ((AcademyPanel) main.getContentPane()).dispose();
        main.dispose();
        return true;
    }

    /**
     * Copies the current level circuit into a new unique file in the sandbox and opens it in free mode.
     * The level window, its file and the level resources are not changed.
     *
     * @param panel the level panel
     * @param level the level
     * @return the free mode window or null if copying failed
     */
    Main copyToFree(AcademyPanel panel, LevelDefinition level) {
        File copy;
        try {
            checkSandbox();
            byte[] data = LevelValidator.snapshot(panel.getMain().getCircuitComponent().getCircuit());
            copy = writeUnique(sandbox, level.getId(), data);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel.getMain(), "复制失败，关卡电路保持不变：" + e.getMessage());
            return null;
        }
        return openFree(copy);
    }

    private void checkSandbox() throws IOException {
        if (policy != null) {
            String reason = policy.checkSave(new File(sandbox, "untitled.dig"));
            if (reason != null)
                throw new IOException(reason);
        }
    }

    /**
     * Writes the data to a new, not yet existing file.
     *
     * @param dir    the directory
     * @param prefix the file name prefix
     * @param data   the data
     * @return the new file
     * @throws IOException IOException
     */
    static File writeUnique(File dir, String prefix, byte[] data) throws IOException {
        Files.createDirectories(dir.toPath());
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        for (int i = 1; i < 1000; i++) {
            File f = new File(dir, prefix + "-copy-" + stamp + (i > 1 ? "-" + i : "") + ".dig");
            try {
                Files.write(f.toPath(), data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return f;
            } catch (FileAlreadyExistsException e) {
                // try the next name
            }
        }
        throw new IOException("找不到可用的文件名");
    }
}
