/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.Model;
import de.neemann.digital.core.Signal;
import de.neemann.digital.gui.Main;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The level screen. It replaces the content of the Digital window: during the experiment
 * only the door scene, the key seal cards and the notes are visible (no canvas, tool bar or
 * menu, so no circuit can leak); the repair stage wraps the real Digital canvas.
 * The hidden reference device, the trusted validation and the progress file are unchanged.
 * All methods must be called on the EDT.
 */
public final class AcademyPanel extends AcademyTheme.Backdrop {
    private static final int POLL_MS = 150;
    private static final String EXPERIMENT = "experiment";
    private static final String REPAIR = "repair";
    private static final String NOTEBOOK = "notebook";
    private static final String INSPECT = "inspect";
    private static final String RESULT = "result";
    private static final String LESSON = "lesson";
    private static final String STORY = "story";
    private static final String CASE = "case";
    private static final String[] STEPS = {"观察实验", "预测检验", "修复术式", "验证通过"};

    private final Main main;
    private final LevelDefinition level;
    private final ReferenceDevice reference;
    private final LevelValidator validator;
    private final AcademyProgress progress;
    private ExperimentSession session;
    private final String output;
    private final List<String> stations = new ArrayList<>();
    private final ExperimentGuide guide;

    // Vocabulary adapts to scene: lamps or doors
    private final String deviceName;        // "档案门" / "报告灯" / "提醒灯"
    private final String deviceShort;       // "门" / "灯"
    private final String lit;               // "门打开了" / "灯亮起"
    private final String dark;              // "门保持关闭" / "灯保持暗"
    // the prediction chips: "我猜会亮" / "我猜保持暗" for one output, one chip for every answer of several lamps
    private final List<String> chipLabels = new ArrayList<>();
    private final long[] chipValues;
    private final List<JRadioButton> predictChips = new ArrayList<>();

    private final DoorView door = new DoorView();
    private final JLabel stageLabel = AcademyTheme.label(" ", AcademyTheme.font(Font.PLAIN, 14), AcademyTheme.MUTED);
    private final Stepper stepper = new Stepper();
    private final List<JCheckBox> inputBoxes = new ArrayList<>();
    private final List<JLabel> inputValues = new ArrayList<>();
    private final List<AcademyTheme.Card> inputCards = new ArrayList<>();
    private final ButtonGroup predictionGroup = new ButtonGroup();
    private final JButton observeButton = AcademyTheme.button("观察回应", AcademyTheme.Kind.PRIMARY);
    private final JButton predictButton = AcademyTheme.button("锁定预测并观察", AcademyTheme.Kind.SECONDARY);
    private final JButton rebuildButton = AcademyTheme.button("进入修复 →", AcademyTheme.Kind.PRIMARY);
    private final JButton hintButton = AcademyTheme.button("我卡住了", AcademyTheme.Kind.SECONDARY);
    private final JButton validateButton;
    private final JButton backButton = AcademyTheme.button("返回首页", AcademyTheme.Kind.QUIET);
    private final JButton copyButton = AcademyTheme.button("复制到自由工坊", AcademyTheme.Kind.QUIET);
    private final JButton notebookButton = AcademyTheme.button("工作记录", AcademyTheme.Kind.QUIET);
    private final JButton guessButton = AcademyTheme.button("先猜一下", AcademyTheme.Kind.QUIET);
    private final JPanel predictionBar = AcademyTheme.clear(new FlowLayout(FlowLayout.CENTER, 10, 0));
    private final AcademyTheme.Text currentInstruction = new AcademyTheme.Text(" ", AcademyTheme.font(Font.BOLD, 16), AcademyTheme.GOLD_HI, 850);
    private final AcademyTheme.Text currentRepair = new AcademyTheme.Text(" ", AcademyTheme.font(Font.BOLD, 17), AcademyTheme.GOLD_HI, 270);
    private final AcademyTheme.Text experimentFeedback = new AcademyTheme.Text(" ", AcademyTheme.body(), AcademyTheme.IVORY, 850);
    private final JPanel navBar = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private String guideText = " ";
    private final JLabel progressLabel = AcademyTheme.label(" ", AcademyTheme.font(Font.BOLD, 14), AcademyTheme.CYAN);
    private String feedback = " ";
    private final ResultLetter.Clerk clerk;
    private final AcademyLetter reply;
    private String repairHint = " ";
    private final List<AcademyTheme.Text> stepLabels = new ArrayList<>();
    private final ObservationTableModel tableModel;
    private final AcademyLedger ledger;
    private final CardLayout stageCards = new CardLayout();
    private final JPanel stageHost = AcademyTheme.clear(stageCards);
    private final JPanel experimentScene = new JPanel(new BorderLayout());
    private final JPanel repairScene = new JPanel(new BorderLayout());
    private final AcademyWorkbench workbench;
    private final Timer pollTimer;

    private final AcademyLesson lesson = new AcademyLesson();
    private final JLabel sceneTitle;
    private final JLabel saveStatus = AcademyTheme.label("", AcademyTheme.font(Font.PLAIN, 12), AcademyTheme.MUTED);
    private final List<JCheckBox> playerReports = new ArrayList<>();
    private final JLabel inspectNotice = AcademyTheme.label("", AcademyTheme.body(), AcademyTheme.GOLD_HI);

    private final Investigation investigation;
    private final AcademyStory story = new AcademyStory();
    private final AcademyTheme.Text caseLine = new AcademyTheme.Text(" ", AcademyTheme.font(Font.PLAIN, 15), AcademyTheme.CYAN, 850);
    private final JButton casesButton;
    private final JButton briefingButton = AcademyTheme.button("回看疑点", AcademyTheme.Kind.QUIET);
    // Unsaved progress is the one thing a player can lose by closing the window, so the save entry
    // sits in the header on every page instead of hiding in the menu popup. It only appears once
    // something is actually unsaved, which keeps the header quiet during a normal playthrough.
    private final JButton saveButton = AcademyTheme.button("保存进度", AcademyTheme.Kind.SECONDARY);
    private AcademyCasebook casebook;
    private final JButton clueButton;
    // Once the chapter passed, the result letter offers the next chapter, so the player does not have
    // to go back to the home screen and pick it. Leaving still goes through the native save question.
    private final JButton resultEditButton = AcademyTheme.button("← 返回术式", AcademyTheme.Kind.PRIMARY);
    private final JButton nextChapterButton = AcademyTheme.button("下一章 →", AcademyTheme.Kind.PRIMARY);
    private Runnable nextChapter;
    private boolean clueRead;
    private boolean briefed;
    private boolean experimentLessonShown;
    private String caseReturn = EXPERIMENT;
    private String currentPage = EXPERIMENT;
    private String returnPage = EXPERIMENT;
    private String lessonReturn = EXPERIMENT;
    private boolean progressDirty;
    private boolean restoring;
    private boolean rebuildStage;
    private boolean notebookVisible;
    private boolean guessVisible;
    private AcademyCinematic cinematic;
    private boolean disposed;
    private int validationGeneration;
    private int hintIndex;
    private ValidationResult lastResult;
    private boolean passedOnce;
    private boolean ranWired;
    private boolean lastPollRunning;
    private RepairGuide.Status repairStatus;

    /**
     * Creates the panel.
     *
     * @param main  the Digital main window whose canvas is used for the rebuild stage
     * @param level the level
     * @throws IOException if the reference device or the trusted tests could not be loaded
     */
    public AcademyPanel(Main main, LevelDefinition level) throws IOException {
        this(main, level, null);
    }

    /**
     * Creates the panel and restores the local progress.
     *
     * @param main     the Digital main window whose canvas is used for the rebuild stage
     * @param level    the level
     * @param progress the progress store, maybe null
     * @throws IOException if the reference device or the trusted tests could not be loaded
     */
    public AcademyPanel(Main main, LevelDefinition level, AcademyProgress progress) throws IOException {
        super(new BorderLayout());
        this.main = main;
        this.level = level;
        this.progress = progress;

        // Set vocabulary based on scene
        boolean lamp = level.isLampScene();
        this.deviceName = lamp ? level.getTitle().split("[:：]")[0] : "档案门";
        this.deviceShort = lamp ? "灯" : "门";
        this.lit = lamp ? "灯亮起" : "门打开了";
        this.dark = lamp ? "灯保持暗" : "门保持关闭";

        this.sceneTitle = AcademyTheme.label(deviceName + " · 调查", AcademyTheme.script(28), AcademyTheme.IVORY);

        this.reference = new ReferenceDevice(level);
        this.validator = new LevelValidator(level);
        Investigation loaded;
        try {
            loaded = Investigation.load(level);
        } catch (IOException e) {
            reference.close();
            throw e;
        }
        this.investigation = loaded;
        int outputs = level.getOutputs().size();
        if (outputs == 1) {
            chipLabels.add(lamp ? "我猜会亮" : "我猜会打开");
            chipLabels.add(lamp ? "我猜保持暗" : "我猜保持关闭");
            chipValues = new long[]{1, 0};
        } else {
            chipValues = new long[1 << outputs];
            for (int v = 0; v < chipValues.length; v++) {
                chipValues[v] = v;
                chipLabels.add(loaded != null ? loaded.word("predict." + v, "我猜" + loaded.claim(v)) : "我猜 " + v);
            }
        }
        for (String label : chipLabels)
            predictChips.add(new AcademyTheme.Chip(label));
        // casesButton / clueButton labels adapt to the level's story vocabulary
        String casesLabel = loaded != null ? loaded.getCandidateButtonLabel() : "三张残页";
        String clueLabel  = loaded != null ? loaded.getClueButtonLabel()      : "翻开校准页 →";
        this.casesButton = AcademyTheme.button(casesLabel, AcademyTheme.Kind.QUIET);
        this.clueButton  = AcademyTheme.button(clueLabel,  AcademyTheme.Kind.PRIMARY);
        this.clerk = ResultLetter.Clerk.of(level);
        this.reply = new AcademyLetter(clerk.idle());
        door.minimal(true);
        String restoreMessage = null;
        boolean restoreRebuild = false;
        if (progress != null) {
            AcademyProgress.Restored r = progress.restore(reference::evaluate);
            this.session = r.getSession();
            this.passedOnce = r.isPassedOnce();
            restoreRebuild = r.isRebuild();
            restoreMessage = r.getMessage();
            // candidate states follow from the replayed real observations; only the player's choice is stored
            if (investigation != null)
                investigation.choose(r.getChoice());
            briefed = r.isBriefed();
        } else
            this.session = new ExperimentSession(level.getInputs());
        this.output = level.getOutputs().get(0);
        stations.addAll(ExperimentGuide.stations(level));
        if (lamp) {
            door.setScene(DoorView.Scene.LAMP);
            if (outputs == 1)
                door.setLamp(level.getScene(), level.getScenePlate(stations.get(0) + " · " + deviceName));
            else {
                String[] tags = new String[outputs];
                for (int i = 0; i < outputs; i++)
                    tags[i] = level.getPortLabel(level.getOutputs().get(i));
                String[] states = new String[chipValues.length];
                for (int v = 0; v < states.length; v++)
                    states[v] = loaded != null ? loaded.word("state." + v, loaded.claim(v)) : Integer.toString(v);
                door.setLamps(level.getScene(), tags, states);
            }
        }
        this.guide = new ExperimentGuide(stations, deviceName, deviceShort, chipLabels);
        // the lessons and the casebook speak the words of this chapter, not the archive door's
        String ruleBook = loaded != null ? loaded.word("rule", "门规") : "门规";
        String placeWord = loaded != null ? loaded.word("place", "门前") : "门前";
        List<String> runeWords = new ArrayList<>();
        for (int k = 0; k < runeCount(); k++)
            runeWords.add(runeName(k));
        if (runeWords.isEmpty())
            runeWords.add("符文");
        lesson.words(deviceName, placeWord, ruleBook, runeWords, level.getInputs().size(), outputs);
        this.tableModel = new ObservationTableModel();
        if (outputs == 1 || loaded == null)
            this.ledger = new AcademyLedger(stations, deviceShort);
        else {
            List<String> guesses = new ArrayList<>();
            List<String> answers = new ArrayList<>();
            for (int v = 0; v < chipValues.length; v++) {
                guesses.add(loaded.word("guess." + v, "猜" + loaded.claim(v)));
                answers.add(loaded.word("answer." + v, loaded.did(v)));
            }
            this.ledger = new AcademyLedger(stations, deviceShort, guesses, answers);
        }

        workbench = new AcademyWorkbench(main, "午夜光术学院 · " + level.getTitle());
        if (hasRune())
            for (int k = 0; k < runeCount(); k++) {
                String rune = level.getRepairRunes().get(k);
                String name = runeName(k);
                workbench.addAction("放置" + name, AcademyTheme.Kind.SECONDARY, () -> workbench.insert(rune))
                        .setToolTipText("放下一个" + name + "：移到画布空白处单击。不会自动连线。");
            }
        // 撤销/运行/停止 are deliberately not built here: this strip is hidden for the whole level
        // (hideActionBar below, and the repair page hosts its own shorter row), so they would be
        // invisible buttons. Undo and stop stay reachable from the menu popup, the native toolbar
        // carries run and undo, and "观察机关" starts the simulation on its own.
        validateButton = workbench.addAction("检验我的术式", AcademyTheme.Kind.PRIMARY, this::startValidation);
        workbench.attachMenuBar(false);
        workbench.hideActionBar();
        main.setSaveCompanion(() -> progressDirty, file -> {
            if (progress != null)
                progress.save(session, rebuildStage, passedOnce, file, choiceId(), briefed);
            progressDirty = false;
            saveButton.setVisible(false);
            saveButton.setEnabled(true);
            saveStatus.setText("已手动保存");
        });

        add(createHeader(), BorderLayout.NORTH);
        stageHost.setBorder(AcademyTheme.pad(8, 24, 18, 24));
        stageHost.add(createExperiment(), EXPERIMENT);
        stageHost.add(createRepair(), REPAIR);
        stageHost.add(createNotebook(), NOTEBOOK);
        stageHost.add(createInspect(), INSPECT);
        stageHost.add(createResult(), RESULT);
        stageHost.add(lesson, LESSON);
        stageHost.add(story, STORY);
        if (investigation != null)
            stageHost.add(createCase(), CASE);
        add(stageHost, BorderLayout.CENTER);
        experimentScene.add(door);

        pollTimer = new Timer(POLL_MS, e -> pollPlayerModel());
        main.getCircuitComponent().addListener(this::circuitChanged);
        for (JRadioButton chip : predictChips)
            chip.addActionListener(e -> updateState());
        updateState();
        restoring = true;
        if (restoreRebuild)
            enterRebuild();
        restoring = false;
        progressDirty = false;
        main.refreshSaveState();
        if (restoreMessage != null)
            setFeedback(restoreMessage);
        else if (session.getObservedCount() > 0)
            setFeedback("已恢复本地进度：" + session.getObservedCount() + " 组真实观察，" + session.getPredictions() + " 次预测。"
                    + (passedOnce ? "历史上曾通过验证；当前术式仍需重新验证。" : ""));
        else
            setFeedback(deviceShort + "的回应会出现在这里。");
        if (!Boolean.getBoolean("academy.skipIntro"))
            javax.swing.SwingUtilities.invokeLater(() -> playCinematic(false));
    }

    /**
     * Shows the navigation buttons.
     *
     * @param back called if the player wants to return to the home window
     * @param copy called if the player wants to copy the circuit to the free mode
     */
    public void setNavigation(Runnable back, Runnable copy) {
        backButton.addActionListener(e -> back.run());
        copyButton.addActionListener(e -> copy.run());
        navBar.setVisible(true);
    }

    /**
     * Offers the next chapter on the result letter once this chapter passed with the current spell.
     *
     * @param label the button text, e.g. 下一章：信使站 →
     * @param go    leaves this chapter for the next one; the native save question may keep the player here
     */
    public void setNextChapter(String label, Runnable go) {
        nextChapterButton.setText(label);
        nextChapter = go;
        updateState();
    }

    /**
     * The clue is where one chapter hands over to the next, so a player who has not read it yet
     * reads it on the way; skipping it is still possible. If the player stays (cancels the save
     * question), the result letter is shown again.
     */
    private void goNextChapter() {
        if (nextChapter == null || !isCurrentPass())
            return;
        if (!clueRead && !investigation.getClue().isEmpty()) {
            story.open(investigation.word("clueHeading", "校准页"), investigation.getClue(), backdrop(true),
                    nextChapterButton.getText(), true, () -> {
                        clueRead = true;
                        leaveForNextChapter();
                    });
            showPage(STORY);
        } else
            leaveForNextChapter();
    }

    private void leaveForNextChapter() {
        workbench.stopModel();
        nextChapter.run();
        if (!disposed)
            showPage(RESULT);
    }

    private void persist() {
        if (!disposed && !restoring) {
            progressDirty = true;
            saveStatus.setText("尚未保存");
            saveButton.setVisible(true);
            saveButton.setEnabled(true);
            main.refreshSaveState();
        }
    }

    private void saveGame() {
        main.saveChanges();
        if (!main.isStateChanged())
            setFeedback("进度与术式已手动保存。");
    }

    private JComponent createHeader() {
        JPanel header = AcademyTheme.clear(new BorderLayout());
        header.setBorder(AcademyTheme.pad(12, 24, 4, 24));
        header.add(sceneTitle, BorderLayout.WEST);
        notebookButton.addActionListener(e -> showNotebook());
        briefingButton.addActionListener(e -> openBriefing());
        casesButton.addActionListener(e -> showCase());
        briefingButton.setToolTipText("再看一遍芙和洛说过的疑点。");
        casesButton.setToolTipText("翻开三张残页，逐页对照你的记录。");
        boolean hasStory = investigation != null;
        briefingButton.setVisible(hasStory);
        casesButton.setVisible(hasStory);
        navBar.add(briefingButton);
        navBar.add(casesButton);
        navBar.add(notebookButton);
        saveButton.setVisible(false);
        saveButton.setToolTipText("把当前的调查记录与术式写进本地存档。学院不会替你自动保存。");
        saveButton.addActionListener(e -> saveGame());
        navBar.add(saveButton);
        JButton menu = AcademyTheme.button("菜单", AcademyTheme.Kind.QUIET);
        menu.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            menuItem(popup, "重看教程", () -> showLesson());
            if (investigation != null && isCurrentPass())
                menuItem(popup, investigation.word("clueMenu", "翻开校准页"), this::openClue);
            menuItem(popup, "我卡住了", () -> {
                showHint();
                openNotebook();
            });
            menuItem(popup, "保存进度与术式", this::saveGame);
            if (rebuildStage) {
                menuItem(popup, "撤销", workbench::undo);
                menuItem(popup, "停止运行", workbench::stopModel);
                menuItem(popup, "检验我的术式", this::startValidation);
                menuItem(popup, workbench.isAdvanced() ? "收起高级工具" : "高级工具", () -> {
                    showPage(REPAIR);
                    workbench.setAdvanced(!workbench.isAdvanced());
                });
                menuItem(popup, "复制到自由工坊", () -> copyButton.doClick());
            }
            menuItem(popup, "重播序幕", () -> playCinematic(false));
            menuItem(popup, "返回首页", () -> backButton.doClick());
            popup.addSeparator();
            javax.swing.JCheckBoxMenuItem motion = new javax.swing.JCheckBoxMenuItem("动态过场", AcademyAtmosphere.motion());
            motion.addActionListener(event -> AcademyAtmosphere.motion(motion.isSelected()));
            popup.add(motion);
            javax.swing.JCheckBoxMenuItem sound = new javax.swing.JCheckBoxMenuItem("音效", AcademyAtmosphere.sound());
            sound.addActionListener(event -> AcademyAtmosphere.sound(sound.isSelected()));
            popup.add(sound);
            popup.show(menu, 0, menu.getHeight());
        });
        navBar.add(menu);
        header.add(navBar, BorderLayout.EAST);
        return header;
    }

    private static void menuItem(JPopupMenu popup, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(AcademyTheme.body());
        item.addActionListener(e -> action.run());
        popup.add(item);
    }

    private void showPage(String page) {
        AcademyTransition.change(stageHost, () -> {
            currentPage = page;
            notebookVisible = NOTEBOOK.equals(page);
            stageCards.show(stageHost, page);
            workbench.attachMenuBar(REPAIR.equals(page));
            sceneTitle.setText(deviceName + " · " + pageTitle(page));
            notebookButton.setText(notebookVisible ? "返回现场" : "工作记录");
            revalidate();
            repaint();
            if (REPAIR.equals(page))
                main.getCircuitComponent().requestFocusInWindow();
        });
    }

    private String pageTitle(String page) {
        switch (page) {
            case EXPERIMENT:
                return "调查";
            case REPAIR:
                return "绘制术式";
            case INSPECT:
                return deviceShort + "前试验";
            case NOTEBOOK:
                return "工作记录";
            case LESSON:
                return "微光的小课";
            case STORY:
                return story.heading();
            case CASE:
                return investigation == null ? "三张残页" : investigation.getCandidateButtonLabel();
            default:
                return "检验";
        }
    }

    private void showLesson() {
        lessonReturn = currentPage.equals(LESSON) ? lessonReturn : currentPage;
        lesson.open(rebuildStage, () -> showPage(lessonReturn));
        showPage(LESSON);
    }

    void lessonForTest() {
        showLesson();
    }

    private String choiceId() {
        return investigation == null || investigation.getChoice() == null ? null : investigation.getChoice().getId();
    }

    /** The clue belongs to the current circuit only; a historical pass never reveals it. */
    private boolean isCurrentPass() {
        return investigation != null && lastResult != null && lastResult.isPassed();
    }

    void openBriefing() {
        if (investigation == null)
            return;
        if (investigation.getBriefing().isEmpty()) {
            briefed = true;
            showCase();
            return;
        }
        story.open("疑点", investigation.getBriefing(), backdrop(false), "翻看残页 →", () -> {
            briefed = true;
            showCase();
        });
        showPage(STORY);
    }

    void openClue() {
        if (!isCurrentPass() || investigation.getClue().isEmpty())
            return;
        story.open(investigation.word("clueHeading", "校准页"), investigation.getClue(), backdrop(true), "回到回信 →",
                true, () -> {
                    clueRead = true;
                    showPage(RESULT);
                });
        showPage(STORY);
    }

    /** The door pictures belong to the archive; a lamp level shows its own drawn room. */
    private String backdrop(boolean repaired) {
        if (level.isLampScene())
            return AcademyLampScene.backdrop(level.getScene(), repaired);
        return repaired ? AcademyArt.ARCHIVE_OPEN : AcademyArt.ARCHIVE_CLOSED;
    }

    private boolean hasRune() {
        return !level.getRepairRune().isEmpty();
    }

    // several lamps get one rune each, in output order; otherwise the level has at most one rune
    private boolean runePerOutput() {
        return level.getOutputs().size() > 1 && level.getRepairRunes().size() == level.getOutputs().size();
    }

    private int runeCount() {
        return runePerOutput() ? level.getRepairRunes().size() : hasRune() ? 1 : 0;
    }

    private String runeName(int k) {
        if (!runePerOutput())
            return level.getRepairRuneName();
        List<String> names = level.getRepairRuneNames();
        return k < names.size() ? names.get(k) : "符文";
    }

    void showCase() {
        if (investigation == null)
            return;
        if (!CASE.equals(currentPage) && !STORY.equals(currentPage) && !NOTEBOOK.equals(currentPage) && !LESSON.equals(currentPage))
            caseReturn = currentPage;
        if (!CASE.equals(currentPage))
            casebook.open();
        refreshCase();
        showPage(CASE);
    }

    private void leaveCase() {
        if (rebuildStage)
            showPage(EXPERIMENT.equals(caseReturn) ? REPAIR : caseReturn);
        else if (session.getObservedCount() == 0 && !experimentLessonShown && !Boolean.getBoolean("academy.skipTutorial")) {
            experimentLessonShown = true;
            lessonReturn = EXPERIMENT;
            lesson.open(false, () -> showPage(EXPERIMENT));
            showPage(LESSON);
        } else
            showPage(EXPERIMENT);
    }

    private JComponent createCase() {
        casebook = new AcademyCasebook(investigation, () -> session, this::chooseExplanation);
        casebook.backButton().addActionListener(e -> leaveCase());
        casebook.repairButton().addActionListener(e -> {
            if (rebuildStage)
                leaveCase();
            else
                enterRebuild();
        });
        return casebook;
    }

    private void refreshCase() {
        if (casebook != null)
            casebook.refresh(rebuildStage);
    }

    /**
     * Sets the player's explanation. It is only remembered on an explicit save.
     *
     * @param id the candidate id
     */
    void chooseExplanation(String id) {
        if (investigation == null)
            return;
        if (investigation.choose(id))
            persist();
        updateState();
    }

    private JComponent createExperiment() {
        JPanel root = AcademyTheme.clear(new BorderLayout(0, 12));
        JPanel direction = AcademyTheme.clear(new BorderLayout(16, 4));
        direction.add(currentInstruction, BorderLayout.CENTER);
        if (investigation != null)
            direction.add(caseLine, BorderLayout.SOUTH);

        root.add(direction, BorderLayout.NORTH);
        experimentScene.setOpaque(false);
        root.add(experimentScene, BorderLayout.CENTER);

        JPanel controls = AcademyTheme.clear(new BorderLayout(0, 8));
        JPanel stationsPanel = AcademyTheme.clear(new GridLayout(1, level.getInputs().size(), 14, 0));
        for (int i = 0; i < level.getInputs().size(); i++)
            stationsPanel.add(createStationCard(i));
        controls.add(stationsPanel, BorderLayout.NORTH);
        for (JRadioButton chip : predictChips) {
            predictionGroup.add(chip);
            predictionBar.add(chip);
        }
        predictionBar.add(predictButton);
        predictionBar.setVisible(false);
        controls.add(predictionBar, BorderLayout.CENTER);
        JPanel bottom = AcademyTheme.clear(new BorderLayout(18, 0));
        experimentFeedback.setPreferredSize(new Dimension(600, investigation != null ? 64 : 44));
        bottom.add(experimentFeedback, BorderLayout.CENTER);
        JPanel actions = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        actions.add(guessButton);
        actions.add(observeButton);
        actions.add(rebuildButton);
        bottom.add(actions, BorderLayout.EAST);
        controls.add(bottom, BorderLayout.SOUTH);
        root.add(controls, BorderLayout.SOUTH);
        observeButton.addActionListener(e -> observe(false));
        predictButton.addActionListener(e -> observe(true));
        rebuildButton.addActionListener(e -> enterRebuild());
        hintButton.addActionListener(e -> {
            showHint();
            openNotebook();
        });
        guessButton.addActionListener(e -> {
            guessVisible = !guessVisible;
            updateState();
        });
        return root;
    }

    private JComponent createNotebook() {
        JButton close = AcademyTheme.button("返回现场 →", AcademyTheme.Kind.PRIMARY);
        close.addActionListener(e -> closeNotebook());
        JPanel controls = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.add(close);
        ledger.add(controls, BorderLayout.SOUTH);
        return ledger;
    }

    private void showNotebook() {
        if (notebookVisible)
            closeNotebook();
        else
            openNotebook();
    }

    /** Opens the notebook, or only refreshes it if it is open already; a hint must never close it. */
    private void openNotebook() {
        refreshLedger();
        if (notebookVisible)
            return;
        returnPage = currentPage;
        showPage(NOTEBOOK);
    }

    private void refreshLedger() {
        List<String> notes = rebuildStage ? Arrays.asList(discoveryText(), repairHint) : Arrays.asList(guideText, feedback);
        ledger.update(session, rebuildStage ? -1 : selectedCombination(), notes);
    }

    private void closeNotebook() {
        showPage(returnPage);
        updateState();
    }

    private void playCinematic(boolean success) {
        if (disposed || Boolean.getBoolean("academy.skipIntro"))
            return;
        if (cinematic != null)
            cinematic.stop();
        cinematic = new AcademyCinematic(success, cinematicScript(), () -> {
            main.getGlassPane().setVisible(false);
            if (!success && investigation != null && !briefed)
                openBriefing();
            else if (!success && session.getObservedCount() == 0 && !Boolean.getBoolean("academy.skipTutorial"))
                showLesson();
            else
                AcademyTransition.enter(main.getRootPane());
            if (rebuildStage)
                main.getCircuitComponent().requestFocusInWindow();
        });
        main.setGlassPane(cinematic);
        cinematic.setVisible(true);
        cinematic.requestFocusInWindow();
    }

    void replayIntro() {
        playCinematic(false);
    }

    /** The prologue words of this chapter; an unreadable story file keeps the words of the archive door. */
    private AcademyCinematic.Script cinematicScript() {
        try {
            return new AcademyCinematic.Script(Investigation.story(level), level.isLampScene() ? level.getScene() : null);
        } catch (IOException e) {
            return AcademyCinematic.Script.ARCHIVE;
        }
    }

    private JComponent createStationCard(int i) {
        String name = level.getInputs().get(i);
        AcademyTheme.Card card = new AcademyTheme.Card(new BorderLayout(12, 4));
        JPanel text = AcademyTheme.clear(new BorderLayout(0, 6));
        text.add(AcademyTheme.label(stations.get(i) + " 的钥印报告", AcademyTheme.script(24), AcademyTheme.IVORY),
                BorderLayout.NORTH);
        JCheckBox cb = new AcademyTheme.Switch(ExperimentGuide.OFF, ExperimentGuide.ON);
        cb.setToolTipText(name + "：" + level.getPortLabel(name));
        cb.addActionListener(e -> {
            predictionGroup.clearSelection();
            AcademySound.cue("click");
            updateState();
        });
        inputBoxes.add(cb);
        text.add(cb, BorderLayout.CENTER);
        card.add(text, BorderLayout.CENTER);
        JLabel value = AcademyTheme.label("0", AcademyTheme.font(Font.BOLD, 44), AcademyTheme.MUTED);
        value.setHorizontalAlignment(SwingConstants.CENTER);
        value.setPreferredSize(new Dimension(64, 64));
        inputValues.add(value);

        inputCards.add(card);
        return card;
    }

    private JComponent createRepair() {
        JPanel root = AcademyTheme.clear(new BorderLayout(0, 12));
        List<String> inputs = level.getInputs();
        List<String> texts = new ArrayList<>();
        if (runePerOutput()) {
            StringBuilder place = new StringBuilder();
            for (int k = 0; k < runeCount(); k++)
                place.append(k == 0 ? "" : "和").append("「放置").append(runeName(k)).append("」");
            texts.add("点" + place + "，各在画布空白处单击放下一个");
            for (int i = 0; i < inputs.size(); i++)
                texts.add("从 " + inputs.get(i) + " 发讯端分别连到每个符文的" + (i == 0 ? "一个" : "另一个") + "接收端");
            for (int k = 0; k < runeCount(); k++)
                texts.add("从" + runeName(k) + "发讯端连到「" + level.getPortLabel(level.getOutputs().get(k)) + "」许可印的接收端");
        } else if (hasRune()) {
            texts.add("点「放置" + level.getRepairRuneName() + "」，在画布空白处单击放下");
            for (int i = 0; i < inputs.size(); i++)
                texts.add("从 " + inputs.get(i) + " 发讯端连到符文的" + (inputs.size() == 1 ? "" : i == 0 ? "一个" : "另一个") + "接收端");
            texts.add("从符文发讯端连到许可印的接收端");
        } else
            for (String in : inputs)
                texts.add("从 " + in + " 发讯端直接连到许可印的接收端");
        texts.add("点「观察机关」，" + (inputs.size() == 1 ? "换一换报告试一试" : "改变两份报告试一试"));
        texts.add("点「检验我的术式」检查全部报告");
        for (String t : texts) {
            AcademyTheme.Text line = new AcademyTheme.Text(t, AcademyTheme.body(), AcademyTheme.MUTED, 600);
            line.putClientProperty("text", t);
            stepLabels.add(line);
        }
        root.add(workbench, BorderLayout.CENTER);
        JPanel bottom = AcademyTheme.clear(new BorderLayout(20, 0));
        currentRepair.setFont(AcademyTheme.script(22));
        bottom.add(currentRepair, BorderLayout.CENTER);
        JPanel actions = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (hasRune())
            for (int k = 0; k < runeCount(); k++) {
                String rune = level.getRepairRunes().get(k);
                actions.add(workbench.addAction("放置" + runeName(k), AcademyTheme.Kind.QUIET, () -> workbench.insert(rune)));
            }
        JButton inspect = AcademyTheme.button("观察机关 →", AcademyTheme.Kind.PRIMARY);
        inspect.addActionListener(e -> inspect());
        actions.add(inspect);
        bottom.add(actions, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private JComponent createInspect() {
        JPanel root = AcademyTheme.clear(new BorderLayout(0, 16));
        root.add(inspectNotice, BorderLayout.NORTH);
        repairScene.setOpaque(false);
        root.add(repairScene, BorderLayout.CENTER);
        JPanel bottom = AcademyTheme.clear(new BorderLayout(20, 0));
        JPanel reports = AcademyTheme.clear(new FlowLayout(FlowLayout.LEFT, 16, 0));
        for (String port : level.getInputs()) {
            JCheckBox report = new AcademyTheme.Switch(port + " · 钥印不在", port + " · 持有钥印");
            report.addActionListener(e -> {
                Model model = main.getModel();
                if (model != null && model.isRunning())
                    model.modify(() -> {
                        Signal signal = ReferenceDevice.find(model.getInputs(), port);
                        if (signal != null)
                            signal.getValue().setBool(report.isSelected());
                    });
                pollPlayerModel();
            });
            playerReports.add(report);
            reports.add(report);
        }
        bottom.add(reports, BorderLayout.CENTER);
        JPanel actions = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton edit = AcademyTheme.button("← 修改术式", AcademyTheme.Kind.QUIET);
        edit.addActionListener(e -> {
            workbench.stopModel();
            showPage(REPAIR);
        });
        actions.add(edit);
        actions.add(validateButton);
        bottom.add(actions, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private void inspect() {
        boolean wired = repairStatus != null && repairStatus.isWired();
        inspectNotice.setText(wired ? " " : "术式尚未接通。回去按当前提示继续连线，再来试验。");
        if (wired && !workbench.isRunning())
            workbench.run();
        pollPlayerModel();
        showPage(INSPECT);
    }

    private JComponent createResult() {
        JPanel controls = AcademyTheme.clear(new BorderLayout());
        controls.add(saveStatus, BorderLayout.WEST);
        JButton edit = resultEditButton;
        edit.addActionListener(e -> {
            workbench.stopModel();
            showPage(REPAIR);
        });
        JPanel actions = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(edit);
        clueButton.setVisible(false);
        clueButton.addActionListener(e -> openClue());
        actions.add(clueButton);
        nextChapterButton.setVisible(false);
        nextChapterButton.setName("nextChapter");
        nextChapterButton.addActionListener(e -> goNextChapter());
        actions.add(nextChapterButton);
        controls.add(actions, BorderLayout.EAST);
        reply.add(controls, BorderLayout.SOUTH);
        return reply;
    }

    private int selectedCombination() {
        int c = 0;
        for (JCheckBox cb : inputBoxes)
            c = (c << 1) | (cb.isSelected() ? 1 : 0);
        return c;
    }

    private void observe(boolean withPrediction) {
        int c = selectedCombination();
        Map<String, Boolean> in = session.toInputs(c);
        String combo = ExperimentGuide.describe(stations, session, c);
        boolean seenBefore = session.getObserved(c) != null;
        if (withPrediction) {
            int chosen = selectedChip();
            if (chosen < 0) {
                setFeedback("先在「你的预测」里" + ExperimentGuide.choices(chipLabels) + "，再点「锁定预测并观察」。");
                return;
            }
            long prediction = chipValues[chosen];
            Reading r = reference.evaluate(in);
            if (!r.isValid()) {
                setFeedback("旧" + deviceShort + "的装置出了故障：" + r);
                return;
            }
            boolean ok = session.predictAndObserve(c, prediction, r);
            if (ok)
                setFeedback("猜对了！" + combo + " → " + response(r) + "。你的推理和" + deviceShort + "的回应一致，已记入工作记录。");
            else
                setFeedback("这次观察给了我们新线索：你猜「" + chipLabels.get(chosen) + "」，但"
                        + response(r) + "。已如实记入工作记录，对照记录再想想规律。");
        } else {
            Reading r = reference.evaluate(in);
            if (session.observe(c, r))
                setFeedback("已记录：" + combo + " → " + response(r) + "。");
            else
                setFeedback(r.isValid() ? "最后一组请先选预测，再点「锁定预测并观察」。" : "旧" + deviceShort + "的装置出了故障：" + r);
        }
        // never tells which page the observation refuted: the player compares, the spirit reads out the chosen page only
        if (investigation != null && !seenBefore && session.getObserved(c) != null && session.getObserved(c).isValid())
            setFeedback(feedback + "\n拿这组回应对照残页的说法：哪张和" + deviceShort + "说得不一样？想好了，就在「"
                    + investigation.getCandidateButtonLabel() + "」里做出判断。");
        predictionGroup.clearSelection();
        updateState();
        persist();
    }

    private String response(Reading r) {
        if (level.getOutputs().size() == 1)
            return (r.isTrue() ? lit : dark) + "（" + level.getPortLabel(output) + " = " + r + "）";
        // several lamps: the words of the whole answer, then every lamp on its own
        String words = investigation != null ? investigation.seen(r.getValue()) : r.toString();
        return words + "（" + outputValues(r.getValue()) + "）";
    }

    /**
     * @param value the outputs packed with the first output as the highest bit
     * @return e.g. "高星灯 = 1 · 低星灯 = 0"
     */
    private String outputValues(long value) {
        List<String> outs = level.getOutputs();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < outs.size(); i++) {
            long bit = (value >> (outs.size() - 1 - i)) & 1;
            sb.append(i > 0 ? " · " : "").append(level.getPortLabel(outs.get(i))).append(" = ").append(bit);
        }
        return sb.toString();
    }

    private int selectedChip() {
        for (int i = 0; i < predictChips.size(); i++)
            if (predictChips.get(i).isSelected())
                return i;
        return -1;
    }

    private void setFeedback(String text) {
        feedback = text;
        experimentFeedback.setText(text);
        refreshLedger();
    }

    private void showHint() {
        List<String> h = level.getHints();
        String text;
        if (rebuildStage) {
            text = (h.size() > 2 ? h.get(2) + "\n" : "")
                    + "你在工作记录里发现：" + level.getRepairRule()
                    + "\n小技巧：第一次点击画布只会让画布获得焦点；连线中按 Esc 取消；选中元件按 Delete 删除；滚轮缩放，右键拖动平移。";
            repairHint = text;
            refreshLedger();
        } else {
            switch (hintIndex) {
                case 0:
                    text = "提示 1/3（操作）：点钥印卡切换「放上钥印 = 1」或「取下钥印 = 0」，再点「观察回应」。每组只需观察一次，结果会记在工作记录里。";
                    break;
                case 1:
                    text = "提示 2/3（方法）：" + (stations.size() == 1 ? "钥印放上、取下各看一次" : "每次只改变一个岗位的钥印")
                            + "，比较" + deviceShort + "的回应。" + (h.isEmpty() ? "" : h.get(0));
                    break;
                default:
                    text = "提示 3/3（观察）：" + (h.size() > 1 ? h.get(1) : "对照工作记录，找出" + lit + "的那一行。");
                    break;
            }
            hintIndex = Math.min(hintIndex + 1, 2);
            setFeedback(text);
        }
    }

    private void updateState() {
        int c = selectedCombination();
        for (int i = 0; i < inputBoxes.size(); i++) {
            boolean on = inputBoxes.get(i).isSelected();
            inputValues.get(i).setText(on ? "1" : "0");
            inputValues.get(i).setForeground(on ? AcademyTheme.GOLD_HI : AcademyTheme.MUTED);
            inputCards.get(i).setLine(on ? AcademyTheme.GOLD : AcademyTheme.LINE);
        }
        boolean needsPrediction = ExperimentGuide.needsPrediction(session, c);
        predictionBar.setVisible(!rebuildStage && (guessVisible || needsPrediction || hasPrediction()));
        guessButton.setVisible(!rebuildStage && !session.isComplete() && !needsPrediction);
        observeButton.setVisible(!rebuildStage && !session.isComplete() && !needsPrediction);
        observeButton.setEnabled(!rebuildStage && session.canObserveWithoutPrediction(c));
        predictButton.setEnabled(!rebuildStage && session.canPredict(c));
        for (JRadioButton chip : predictChips)
            chip.setEnabled(!rebuildStage && session.canPredict(c));
        rebuildButton.setEnabled(!rebuildStage && session.isComplete());
        rebuildButton.setVisible(!rebuildStage && session.isComplete());
        copyButton.setVisible(rebuildStage);
        if (investigation != null) {
            rebuildButton.setText(investigation.canRepair(session) ? "进入修复 →" : "整理残页 →");
            caseLine.setText(rebuildStage ? " " : investigation.observationNote(c, session));
            clueButton.setVisible(isCurrentPass());
            // one gold button per page: after a pass it is the way on, the others step back
            boolean onward = isCurrentPass() && nextChapter != null;
            nextChapterButton.setVisible(onward);
            AcademyTheme.kind(resultEditButton, isCurrentPass() ? AcademyTheme.Kind.QUIET : AcademyTheme.Kind.PRIMARY);
            AcademyTheme.kind(clueButton, onward ? AcademyTheme.Kind.SECONDARY : AcademyTheme.Kind.PRIMARY);
            refreshCase();
        }

        boolean passed = lastResult != null && lastResult.isPassed();
        String stage;
        if (rebuildStage)
            stage = passed ? "完成 √ " + deviceName + "已通过验证"
                    : "修复阶段 · 搭建并验证你的术式" + (passedOnce ? "（历史曾通过；当前术式需重新验证）" : "");
        else
            stage = "实验阶段 · 已观察 " + session.getObservedCount() + "/" + session.getCombinationCount()
                    + " · 已预测 " + session.getPredictions() + " 次";
        stageLabel.setText(stage);
        stepper.setCurrent(rebuildStage ? (passed ? 3 : 2) : (needsPrediction || session.getPredictions() > 0 ? 1 : 0), passed);

        if (!rebuildStage) {
            guideText = guide.next(session, c);
            currentInstruction.setText(shortInstruction(c, needsPrediction));
            progressLabel.setText("已观察 " + session.getObservedCount() + "/" + session.getCombinationCount()
                    + " · 已预测 " + session.getPredictions() + " 次 · 猜对 " + session.getCorrectPredictions() + " 次");
            Reading r = session.getObserved(c);
            String src = "旧" + deviceShort + " · " + ExperimentGuide.describe(stations, session, c);
            if (r != null)
                door.show(r, src, "工作记录里的记录");
            else
                door.showUnobserved(src, needsPrediction ? "先选预测，再点「锁定预测并观察」"
                        : "点击「观察回应」看看" + deviceShort + "怎么回应");
        } else
            updateRepairSteps();
        refreshLedger();
        revalidate();
    }

    private String shortInstruction(int selected, boolean prediction) {
        String recorded = Investigation.groups(session.getCombinationCount()) + "报告已记录。";
        if (session.isComplete())
            return investigation == null ? recorded + "打开笔记找规律，再进入修复。"
                    : investigation.canRepair(session) ? "证据支持你的判断。可以进入修复了。"
                    : recorded + "翻看「" + investigation.getCandidateButtonLabel() + "」，选出你认为说对的那张，微光会念出证据。";
        if (prediction)
            return "最后一组：先选你的预测，再观察。猜错也能发现新线索。";
        if (session.getObserved(selected) == null)
            return "点击「观察回应」，看看" + (stations.size() == 1 ? "这份报告" : "这两份报告") + "会让" + deviceShort + "怎样回应。";
        if (investigation != null)
            return "这组已记录。换一组还没看过的报告——顺序由你决定。";
        int next = ExperimentGuide.recommend(session, selected);
        for (int i = 0; i < stations.size(); i++) {
            int bit = 1 << (stations.size() - 1 - i);
            if ((selected & bit) != (next & bit))
                return "下一步：点击「" + stations.get(i) + "」卡，" + ((next & bit) != 0 ? "放上钥印（1）" : "取下钥印（0）") + "。";
        }
        return "打开笔记，比较你记录的回应。";
    }

    private void updateRepairSteps() {
        if (repairStatus == null)
            return;
        List<Boolean> steps = new ArrayList<>();
        if (hasRune())
            steps.add(repairStatus.isAndPlaced());
        for (int i = 0; i < level.getInputs().size(); i++)
            steps.add(repairStatus.isInput(i));
        if (runePerOutput())
            for (int k = 0; k < runeCount(); k++)
                steps.add(repairStatus.isOutput(k));
        else if (hasRune())
            steps.add(repairStatus.isOutput());
        steps.add(ranWired);
        steps.add(lastResult != null && lastResult.isPassed());
        boolean[] done = new boolean[steps.size()];
        for (int i = 0; i < done.length; i++)
            done[i] = steps.get(i);
        // the wiring steps come first, then trying it out and the validation
        int trying = done.length - 2;
        int current = -1;
        for (int i = 0; i < done.length; i++)
            if (!done[i] && current < 0)
                current = i;
        for (int i = 0; i < stepLabels.size(); i++) {
            AcademyTheme.Text l = stepLabels.get(i);
            String t = (String) l.getClientProperty("text");
            String mark = done[i] ? "√" : i == current ? "→" : "○";
            Color col = done[i] ? AcademyTheme.OK : i == current ? AcademyTheme.GOLD_HI : AcademyTheme.MUTED;
            l.setText(mark + " " + (i + 1) + ". " + t);
            l.setFont(AcademyTheme.font(i == current ? Font.BOLD : Font.PLAIN, 14));
            l.setForeground(col);
        }
        currentRepair.setText(current < 0 ? deviceName + "已经通过全部验证。" : ""
                + stepLabels.get(current).getClientProperty("text"));
        if (repairStatus.isInputsShorted())
            repairHint = "注意：" + String.join(" 和 ", level.getInputs())
                    + " 的线连在了一起，" + deviceShort + "会分不清两份报告。点「撤销」或选中这根线按 Delete 删除。";
        else if (current == trying && workbench.isRunning())
            repairHint = "正在运行：单击画布上的 " + String.join("、", level.getInputs()) + " 切换 0/1，"
                    + level.getRepairCheck() + "。改术式前先点「停止」。";
        else if (current >= 0 && current < trying && workbench.isRunning())
            repairHint = "术式正在运行，编辑被锁定。先点「停止」，再继续连线。";
        else if (current == -1)
            repairHint = deviceName + "修好了！可以「复制到自由工坊」继续做实验，或返回首页。";
        else if (current < trying)
            repairHint = "连线：单击端点开始，移动鼠标（不用按住），在目标端点上单击结束；在空白处单击会拐弯。Esc 取消。";
        else
            repairHint = " ";
        refreshLedger();
    }

    private void analyzeCircuit() {
        repairStatus = RepairGuide.analyze(main.getCircuitComponent().getCircuit(),
                level.getInputs(), level.getOutputs(), level.getRepairRunes());
        if (!repairStatus.isWired())
            ranWired = false;
    }

    private void enterRebuild() {
        if (!session.isComplete() || rebuildStage)
            return;
        // without sufficient evidence and the player's own matching explanation the case page explains what is missing;
        // a restored legacy repair stage is kept, its observations are already complete
        if (investigation != null && !restoring && !investigation.canRepair(session)) {
            showCase();
            return;
        }
        rebuildStage = true;
        for (JCheckBox cb : inputBoxes)
            cb.setEnabled(false);
        predictionGroup.clearSelection();
        experimentScene.remove(door);
        repairScene.add(door);
        if (!restoring && !Boolean.getBoolean("academy.skipTutorial") && !Boolean.getBoolean("academy.skipIntro")) {
            lessonReturn = REPAIR;
            lesson.open(true, () -> showPage(REPAIR));
            showPage(LESSON);
        } else
            showPage(REPAIR);
        analyzeCircuit();
        pollTimer.start();
        pollPlayerModel();
        updateState();
        persist();
        revalidate();
        repaint();
        main.getCircuitComponent().requestFocusInWindow();
    }

    /** What the records showed; the records themselves stand in the notebook beside it. */
    private String discoveryText() {
        if (investigation != null) {
            Investigation.Candidate found = investigation.discovered(session);
            if (found != null && found.getKnowledge() != null)
                return found.getKnowledge();
        }
        return level.getRepairRule();
    }

    /**
     * Reads the player's running model. Runs on the EDT via the swing timer.
     */
    private void pollPlayerModel() {
        if (disposed || !rebuildStage)
            return;
        Reading r = readPlayerOutput(main.getModel());
        Model model = main.getModel();
        boolean modelRunning = model != null && model.isRunning();
        for (int i = 0; i < playerReports.size(); i++) {
            JCheckBox report = playerReports.get(i);
            Signal signal = modelRunning ? ReferenceDevice.find(model.getInputs(), level.getInputs().get(i)) : null;
            report.setEnabled(signal != null);
            if (signal != null)
                model.read(() -> report.setSelected(signal.getValue().getBool()));
        }
        door.show(r, deviceName + "（你的术式）", r.getKind() == Reading.Kind.NOT_RUNNING ? "点「运行」" : "");
        boolean running = r.getKind() != Reading.Kind.NOT_RUNNING;
        if (running && repairStatus != null && repairStatus.isWired() && !ranWired) {
            ranWired = true;
            updateRepairSteps();
        } else if (running != lastPollRunning)
            updateRepairSteps();
        lastPollRunning = running;
    }

    private Reading readPlayerOutput(Model model) {
        if (model == null || !model.isRunning())
            return Reading.NOT_RUNNING;
        Reading[] r = new Reading[1];
        model.read(() -> {
            List<Reading> parts = new ArrayList<>();
            for (String name : level.getOutputs()) {
                Signal s = ReferenceDevice.find(model.getOutputs(), name);
                if (s == null) {
                    r[0] = Reading.MISSING;
                    return;
                }
                parts.add(Reading.of(s.getValue()));
            }
            r[0] = Reading.pack(parts);
        });
        return r[0];
    }

    private void circuitChanged() {
        if (disposed)
            return;
        // a shown or a running validation belongs to the old circuit
        boolean hadResult = lastResult != null || !validateButton.isEnabled();
        validationGeneration++;
        lastResult = null;
        validateButton.setEnabled(true);
        if (rebuildStage) {
            reply.deliver(hadResult ? clerk.stale() : clerk.idle());
            analyzeCircuit();
        }
        updateState();
    }

    private void startValidation() {
        lastResult = null;
        updateState();
        final byte[] snapshot;
        try {
            snapshot = LevelValidator.snapshot(main.getCircuitComponent().getCircuit());
        } catch (IOException e) {
            System.err.println("[academy] circuit could not be copied for validation: " + e.getMessage());
            reply.deliver(clerk.error());
            showPage(RESULT);
            return;
        }
        showPage(RESULT);
        final int generation = ++validationGeneration;
        validateButton.setEnabled(false);
        reply.deliver(clerk.pending());
        new SwingWorker<ValidationResult, Void>() {
            @Override
            protected ValidationResult doInBackground() throws Exception {
                return validator.validate(snapshot);
            }

            @Override
            protected void done() {
                if (disposed || !main.isDisplayable() || generation != validationGeneration)
                    return;
                validateButton.setEnabled(true);
                try {
                    lastResult = get();
                    reply.deliver(clerk.reply(lastResult));
                    if (lastResult.isPassed()) {
                        passedOnce = true;
                        persist();
                        playCinematic(true);
                    }
                } catch (Exception e) {
                    System.err.println("[academy] validation failed: " + e);
                    reply.deliver(clerk.error());
                }
                updateState();
            }
        }.execute();
    }

    /**
     * Stops the timer, invalidates pending validations and releases the reference model.
     * After this call no event of this level is delivered any more.
     */
    public void dispose() {
        if (disposed)
            return;
        disposed = true;
        validationGeneration++;
        pollTimer.stop();
        door.stopAnimation();
        if (cinematic != null)
            cinematic.stop();
        AcademySound.stopAll();
        reference.close();
    }

    /**
     * @return the experiment session
     */
    String page() {
        return currentPage;
    }

    AcademyLesson lesson() {
        return lesson;
    }

    void inspectForTest() {
        inspect();
    }

    JCheckBox playerReport(int i) {
        return playerReports.get(i);
    }

    boolean isProgressDirty() {
        return progressDirty;
    }

    /** @return current real experiment records */
    public ExperimentSession getSession() {
        return session;
    }

    /**
     * @return the Digital main window
     */
    public Main getMain() {
        return main;
    }

    // package private hooks for the scripted GUI checks; EDT only

    void selectInputs(int combination) {
        for (int i = 0; i < inputBoxes.size(); i++)
            inputBoxes.get(i).setSelected(((combination >> (inputBoxes.size() - 1 - i)) & 1) != 0);
        predictionGroup.clearSelection();
        updateState();
    }

    void selectPrediction(boolean one) {
        selectPredictionValue(one ? 1 : 0);
    }

    /**
     * Selects the prediction chip of a value, the outputs packed with the first output as the highest bit.
     *
     * @param value the predicted value
     */
    void selectPredictionValue(long value) {
        for (int i = 0; i < chipValues.length; i++)
            if (chipValues[i] == value)
                predictChips.get(i).setSelected(true);
        updateState();
    }

    boolean hasPrediction() {
        return selectedChip() >= 0;
    }

    JCheckBox inputBox(int i) {
        return inputBoxes.get(i);
    }

    JRadioButton predictionChip(boolean one) {
        return predictionChip(one ? 1L : 0L);
    }

    JRadioButton predictionChip(long value) {
        for (int i = 0; i < chipValues.length; i++)
            if (chipValues[i] == value)
                return predictChips.get(i);
        return null;
    }

    /**
     * @return the labels of the prediction chips in the order they are shown
     */
    List<String> predictionLabels() {
        return Collections.unmodifiableList(chipLabels);
    }

    JButton observeButton() {
        return observeButton;
    }

    JButton predictButton() {
        return predictButton;
    }

    JButton rebuildButton() {
        return rebuildButton;
    }

    JButton validateButton() {
        return validateButton;
    }

    JButton hintButton() {
        return hintButton;
    }

    JButton saveButton() {
        return saveButton;
    }

    String resultText() {
        return reply.text();
    }

    AcademyLetter reply() {
        return reply;
    }

    String feedbackText() {
        return feedback;
    }

    String guideText() {
        return guideText;
    }

    String repairHintText() {
        return repairHint;
    }

    AcademyLedger ledger() {
        return ledger;
    }

    String stepText() {
        StringBuilder sb = new StringBuilder();
        for (AcademyTheme.Text l : stepLabels)
            sb.append(l.getText()).append('\n');
        return sb.toString();
    }

    String stageText() {
        return stageLabel.getText();
    }

    String tableText() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < tableModel.getColumnCount(); c++)
            sb.append(tableModel.getColumnName(c)).append(c + 1 < tableModel.getColumnCount() ? " | " : "\n");
        for (int r = 0; r < tableModel.getRowCount(); r++)
            for (int c = 0; c < tableModel.getColumnCount(); c++)
                sb.append(tableModel.getValueAt(r, c)).append(c + 1 < tableModel.getColumnCount() ? " | " : "\n");
        return sb.toString();
    }

    DoorView door() {
        return door;
    }

    JButton backButton() {
        return backButton;
    }

    JButton copyButton() {
        return copyButton;
    }

    JButton nextChapterButton() {
        return nextChapterButton;
    }

    String levelId() {
        return level.getId();
    }

    AcademyWorkbench workbench() {
        return workbench;
    }

    boolean isRebuildStage() {
        return rebuildStage;
    }

    RepairGuide.Status repairStatus() {
        return repairStatus;
    }

    boolean isPassedOnce() {
        return passedOnce;
    }

    boolean isPolling() {
        return pollTimer.isRunning();
    }

    ValidationResult lastResult() {
        return lastResult;
    }

    boolean isNotebookVisible() {
        return notebookVisible;
    }

    JButton notebookButton() {
        return notebookButton;
    }

    AcademyCinematic cinematic() {
        return cinematic;
    }

    Investigation investigation() {
        return investigation;
    }

    AcademyStory story() {
        return story;
    }

    String caseText() {
        return casebook == null ? "" : casebook.text();
    }

    AcademyCasebook casebook() {
        return casebook;
    }

    String caseLineText() {
        return caseLine.getText();
    }

    JRadioButton caseChip(int i) {
        return casebook.chip(i);
    }

    JButton caseRepairButton() {
        return casebook.repairButton();
    }

    JButton caseBackButton() {
        return casebook.backButton();
    }

    JButton casesButton() {
        return casesButton;
    }

    JButton clueButton() {
        return clueButton;
    }

    boolean isBriefed() {
        return briefed;
    }

    /**
     * The four stages of the level as numbered pills; the text tells the state, not only the color.
     */
    private static final class Stepper extends JComponent {
        private int current;
        private boolean passed;

        private Stepper() {
            setPreferredSize(new Dimension(520, 34));
        }

        private void setCurrent(int current, boolean passed) {
            if (current != this.current || passed != this.passed) {
                this.current = current;
                this.passed = passed;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(AcademyTheme.font(Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            int gap = 22;
            int[] widths = new int[STEPS.length];
            int total = 0;
            for (int i = 0; i < STEPS.length; i++) {
                widths[i] = fm.stringWidth((i + 1) + "  " + STEPS[i]) + 34;
                total += widths[i] + (i > 0 ? gap : 0);
            }
            int x = Math.max(0, (getWidth() - total) / 2);
            int h = 28;
            int y = (getHeight() - h) / 2;
            for (int i = 0; i < STEPS.length; i++) {
                boolean done = i < current || (passed && i == current);
                boolean now = i == current && !done;
                if (i > 0) {
                    g2.setColor(done || now ? AcademyTheme.GOLD : AcademyTheme.LINE);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(x - gap + 3, y + h / 2, x - 3, y + h / 2);
                }
                g2.setColor(now ? new Color(0x3A3220) : done ? new Color(0x1C3A2C) : AcademyTheme.CARD);
                g2.fillRoundRect(x, y, widths[i], h, h, h);
                g2.setColor(now ? AcademyTheme.GOLD_HI : done ? AcademyTheme.OK : AcademyTheme.LINE);
                g2.setStroke(new BasicStroke(now ? 2f : 1.2f));
                g2.drawRoundRect(x, y, widths[i], h, h, h);
                String t = (done ? "√" : String.valueOf(i + 1)) + "  " + STEPS[i];
                g2.setColor(now ? AcademyTheme.GOLD_HI : done ? AcademyTheme.OK : AcademyTheme.MUTED);
                g2.drawString(t, x + 17, y + (h + fm.getAscent() - fm.getDescent()) / 2);
                x += widths[i] + gap;
            }
            g2.dispose();
        }
    }

    private final class ObservationTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return session.getCombinationCount();
        }

        @Override
        public int getColumnCount() {
            return level.getInputs().size() + 2;
        }

        @Override
        public String getColumnName(int col) {
            int n = level.getInputs().size();
            if (col < n)
                return stations.get(col);
            if (col == n)
                return "你的预测";
            return deviceShort + "的回应";
        }

        @Override
        public Object getValueAt(int row, int col) {
            int n = level.getInputs().size();
            if (col < n)
                return session.toInputs(row).get(level.getInputs().get(col)) ? "1" : "0";
            if (col == n) {
                Long p = session.getPrediction(row);
                return p == null ? "" : Long.toString(p);
            }
            Reading r = session.getObserved(row);
            return r == null ? "尚未观察" : r.toString();
        }
    }
}
