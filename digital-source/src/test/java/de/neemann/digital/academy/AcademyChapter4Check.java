/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.gui.Main;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Chapter four walked through the way a player does, in temporary work directories: the doubt
 * dialogue, the casebook before and after a choice, the acceptance lamp observed at the registry desk,
 * a wrong prediction, the refuted and the right page, the repair, and the three fixtures checked by
 * the real validation — only the single-seat rune passes and opens the clue.
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar de.neemann.digital.academy.AcademyChapter4Check &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyChapter4Check {
    private static final String[] FORBIDDEN = {"电路", "高阻", "仿真", "输出端口", "AND", "OR", "XOR", "真值表"};
    private static PrintStream log;
    private static File shots;
    private static int checks;

    private AcademyChapter4Check() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("academy.silent", "true");
        System.setProperty("academy.skipIntro", "true");
        System.setProperty("academy.skipTutorial", "true");
        System.setProperty("academy.skipTransitions", "true");
        javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
        File root = new File(args[0]);
        // the art is found under the project root, as the launcher sets it; without it the shots show no one
        System.setProperty("academy.root", root.getAbsolutePath());
        shots = new File(root, "docs/evidence/chapter4-gui");
        shots.mkdirs();
        log = new PrintStream(new File(shots, "chapter4-check.log"), "UTF-8");
        int result = 0;
        try {
            run(root);
            log.println("RESULT: " + checks + " CHECKS PASSED");
            System.out.println("RESULT: " + checks + " CHECKS PASSED");
        } catch (Throwable e) {
            e.printStackTrace(log);
            e.printStackTrace();
            result = 1;
        } finally {
            edt(() -> {
                for (Window w : Window.getWindows())
                    w.dispose();
                return null;
            });
            log.close();
        }
        System.exit(result);
    }

    private static void run(File root) throws Exception {
        File levelFile = new File(root, "levels/04_registry_desk/level.properties");
        LevelDefinition level = LevelDefinition.load(levelFile);
        File fixtures = new File(root, "levels/04_registry_desk/fixtures");
        String[] names = {"xor_correct.dig", "or_wrong.dig", "unconnected.dig"};
        byte[][] fixturesBefore = new byte[names.length][];
        for (int i = 0; i < names.length; i++)
            fixturesBefore[i] = Files.readAllBytes(new File(fixtures, names[i]).toPath());
        byte[] storyBefore = Files.readAllBytes(level.getStoryFile().toPath());
        File dir = Files.createTempDirectory("academy-chapter4-check").toFile();
        log.println("temp: " + dir);
        File dest = AcademyMain.prepareWorkFile(level, new File(dir, "work"));
        AcademyProgress progress = new AcademyProgress(new File(dir, "progress"), level);

        // --- the doubt dialogue at the registry desk ---
        AcademyPanel p = edt(() -> AcademyMain.open(level, dest, progress, AcademySavePolicy.forProject(root)));
        edt(() -> { p.getMain().setVisible(true); return null; });
        Thread.sleep(300);
        check("the chapter is a lamp chapter at the registry desk", level.isLampScene()
                && "registry_desk".equals(level.getScene()) && "登记台 · 受理灯".equals(level.getScenePlate("")));
        check("three candidate pages", edt(() -> p.investigation() != null && p.investigation().getCandidates().size() == 3));
        edt(() -> { p.openBriefing(); return null; });
        check("the doubt dialogue opens on its first page", edt(() -> "story".equals(p.page()) && p.story().page() == 0
                && p.story().pages() == 6));
        StringBuilder dialogue = new StringBuilder();
        int pages = edt(() -> p.story().pages());
        for (int i = 0; i < pages; i++) {
            dialogue.append(edt(() -> p.story().lineText())).append('\n');
            if (i == 0)
                shot(p.getMain(), "01-doubt-fu");
            if (i == 3)
                shot(p.getMain(), "02-doubt-glimmer");
            if (i + 1 < pages)
                edt(() -> { p.story().nextButton().doClick(); return null; });
        }
        log.println(dialogue);
        clean("doubt dialogue", dialogue.toString());
        check("the dialogue asks how the desk answers two reports at once", dialogue.indexOf("两份报告一起到") >= 0
                && dialogue.indexOf("正常") >= 0);
        edt(() -> { p.story().nextButton().doClick(); return null; });
        check("the dialogue ends on the casebook", edt(() -> "case".equals(p.page()) && p.isBriefed()));
        String empty = edt(p::caseText);
        log.println(empty);
        clean("empty casebook", empty);
        check("no evidence before a choice", empty.contains("选了，我才把证据念给你听") && !empty.contains("推翻")
                && !empty.contains("反驳"));
        check("repair locked without evidence", !edt(() -> p.caseRepairButton().isEnabled()));
        check("the casebook opens on page one of three", edt(() -> p.casebook().page() == 0 && p.casebook().pages() == 3
                && p.casebook().text().contains("残页甲")));
        shot(p.getMain(), "03-casebook-empty");

        // --- the player trusts the brass plaque first ---
        edt(() -> { p.caseChip(0).doClick(); return null; });
        check("choosing is unsaved state", edt(() -> "any".equals(p.investigation().getChoice().getId())
                && p.isProgressDirty()) && !dest.exists() && !progress.getFile().exists());
        edt(() -> { p.caseBackButton().doClick(); return null; });
        check("back returns to the desk", edt(() -> "experiment".equals(p.page())));

        // --- nobody holds a key: the lamp stays dark ---
        edt(() -> { p.selectInputs(0); p.observeButton().doClick(); return null; });
        Thread.sleep(900);
        check("00: the lamp stays dark and no page is named", edt(() -> p.door().getReading().isValid()
                && !p.door().getReading().isTrue()) && edt(p::caseLineText).contains("灯保持暗")
                && !edt(p::feedbackText).contains("残页甲") && !edt(p::feedbackText).contains("推翻"));
        shot(p.getMain(), "04-desk-00-dark");

        // --- one post holds a key: the lamp lights ---
        edt(() -> { p.selectInputs(1); p.observeButton().doClick(); return null; });
        Thread.sleep(900);
        check("one key: the lamp lights", edt(() -> p.door().getReading().isTrue()) && edt(p::caseLineText).contains("灯亮起"));
        shot(p.getMain(), "05-desk-one-key-lit");
        edt(() -> { p.selectInputs(2); p.observeButton().doClick(); return null; });
        Thread.sleep(300);
        check("the other key alone lights it too", edt(() -> p.door().getReading().isTrue()));
        clean("desk line", edt(p::caseLineText));
        clean("desk feedback", edt(p::feedbackText));

        // --- both hold a key: the last report is predicted first, and the plaque's guess is wrong ---
        edt(() -> { p.selectInputs(3); return null; });
        check("the last report needs a prediction", edt(() -> !p.observeButton().isVisible() && p.predictButton().isVisible()));
        edt(() -> { p.selectPrediction(true); p.predictButton().doClick(); return null; });
        Thread.sleep(900);
        check("both keys: the lamp stays dark and the wrong guess is a new clue", edt(() -> p.door().getReading().isValid()
                && !p.door().getReading().isTrue()) && edt(p::feedbackText).contains("新线索")
                && p.getSession().isComplete());
        check("the desk does not judge the pages itself", !edt(p::feedbackText).contains("残页甲")
                && !edt(p::caseLineText).contains("推翻"));
        clean("desk line after the prediction", edt(p::caseLineText));
        clean("desk feedback after the prediction", edt(p::feedbackText));
        shot(p.getMain(), "06-desk-both-keys-dark");

        // --- the casebook now tells the choice by evidence ---
        check("complete records with a refuted choice lead to the pages, not the repair",
                edt(() -> p.rebuildButton().getText().contains("整理残页")));
        edt(() -> { p.rebuildButton().doClick(); return null; });
        String wrong = edt(p::caseText);
        log.println(wrong);
        clean("casebook with a refuted choice", wrong);
        check("the plaque's page is refuted by a concrete record", wrong.contains("「残页甲 · 有人来领就受理」已被你的记录反驳")
                && wrong.contains("岗位 A 持钥印 · 岗位 B 持钥印"));
        check("repair stays locked on a refuted page", !edt(() -> p.caseRepairButton().isEnabled()));
        shot(p.getMain(), "07-casebook-refuted");
        edt(() -> { p.caseChip(1).doClick(); return null; });
        String signed = edt(p::caseText);
        clean("casebook on the signed register", signed);
        check("the signed register is refuted as well", signed.contains("「残页乙 · 两人联签」已被你的记录反驳"));
        edt(() -> { p.caseChip(2).doClick(); return null; });
        String found = edt(p::caseText);
        log.println(found);
        clean("casebook on the single seat", found);
        check("the struck-out page survives and unlocks the rune", found.contains("证据支持你的判断") && found.contains("单席符文")
                && found.contains("不是钥印没了") && edt(() -> p.caseRepairButton().isEnabled()));
        shot(p.getMain(), "08-casebook-discovered");
        check("nothing was autosaved", !dest.exists() && !progress.getFile().exists());

        // --- repair, then an explicit save ---
        edt(() -> { p.caseRepairButton().doClick(); return null; });
        check("the repair opens after the discovery", edt(() -> p.isRebuildStage() && "repair".equals(p.page())));
        check("no clue before a validation", !edt(() -> p.clueButton().isVisible()));
        clean("repair step", edt(p::stepText) + "\n" + edt(p::repairHintText) + "\n" + edt(p::guideText));
        shot(p.getMain(), "09-repair");
        edt(() -> { p.getMain().saveChanges(); return null; });
        String saved = new String(Files.readAllBytes(progress.getFile().toPath()), StandardCharsets.ISO_8859_1);
        check("the save stores the choice and the repair", saved.contains("story.choice=single") && saved.contains("rebuild=true"));
        byte[] savedBytes = Files.readAllBytes(progress.getFile().toPath());
        edt(() -> { p.getMain().dispose(); return null; });

        // --- the three fixtures, each through the real validation ---
        AcademyPanel right = open(root, level, progress, new File(fixtures, "xor_correct.dig"), new File(dir, "xor.dig"));
        check("the saved repair is restored", edt(() -> right.isRebuildStage() && right.investigation().getChoice() != null
                && "single".equals(right.investigation().getChoice().getId())));
        validate(right);
        check("the single-seat rune passes", edt(() -> right.lastResult() != null && right.lastResult().isPassed()
                && "result".equals(right.page())));
        String letter = edt(() -> right.reply().text());
        log.println(letter);
        clean("result letter of the pass", letter);
        check("the pass opens the clue", edt(() -> right.clueButton().isVisible()));
        Thread.sleep(400);
        shot(right.getMain(), "10-result-pass");
        edt(() -> { right.clueButton().doClick(); return null; });
        check("the clue opens", edt(() -> "story".equals(right.page()) && "线索".equals(right.story().heading())));
        StringBuilder clue = new StringBuilder();
        int cluePages = edt(() -> right.story().pages());
        for (int i = 0; i < cluePages; i++) {
            clue.append(edt(() -> right.story().lineText())).append('\n');
            if (i == 3)
                shot(right.getMain(), "11-clue-stub");
            if (i + 1 < cluePages)
                edt(() -> { right.story().nextButton().doClick(); return null; });
        }
        log.println(clue);
        clean("clue", clue.toString());
        check("the clue tells not accepted is not a lost key", clue.indexOf("不是钥印没了") >= 0 && clue.indexOf("未受理") >= 0);
        check("the clue keeps the unknown unknown", clue.indexOf("没有证据") >= 0 && clue.indexOf("未知") >= 0);
        check("the clue points to the star lamps", clue.indexOf("星灯") >= 0);
        edt(() -> { right.story().nextButton().doClick(); return null; });
        check("the clue returns to the result", edt(() -> "result".equals(right.page())));
        check("pass and clue write nothing", Arrays.equals(savedBytes, Files.readAllBytes(progress.getFile().toPath())));
        edt(() -> { right.getMain().dispose(); return null; });

        AcademyPanel any = open(root, level, progress, new File(fixtures, "or_wrong.dig"), new File(dir, "or.dig"));
        validate(any);
        check("the either-key rune does not pass", edt(() -> any.lastResult() != null && !any.lastResult().isPassed()
                && !any.clueButton().isVisible()));
        String anyLetter = edt(() -> any.reply().text());
        log.println(anyLetter);
        clean("result letter of the either-key rune", anyLetter);
        Thread.sleep(400);
        shot(any.getMain(), "12-result-either-key");
        edt(() -> { any.getMain().dispose(); return null; });

        AcademyPanel loose = open(root, level, progress, new File(fixtures, "unconnected.dig"), new File(dir, "loose.dig"));
        validate(loose);
        check("an unconnected seal does not pass", edt(() -> loose.lastResult() != null && !loose.lastResult().isPassed()
                && !loose.clueButton().isVisible()));
        String looseLetter = edt(() -> loose.reply().text());
        log.println(looseLetter);
        clean("result letter of the loose seal", looseLetter);
        check("silence is not told as not accepted", looseLetter.contains("无讯"));
        Thread.sleep(400);
        shot(loose.getMain(), "13-result-silent");
        edt(() -> { loose.getMain().dispose(); return null; });

        for (int i = 0; i < names.length; i++)
            check("fixture " + names[i] + " unchanged", Arrays.equals(fixturesBefore[i],
                    Files.readAllBytes(new File(fixtures, names[i]).toPath())));
        check("story resource unchanged", Arrays.equals(storyBefore, Files.readAllBytes(level.getStoryFile().toPath())));
    }

    /** Opens a copy of a fixture as the player's circuit, with the saved progress of the walk. */
    private static AcademyPanel open(File root, LevelDefinition level, AcademyProgress progress, File fixture, File copy)
            throws Exception {
        Files.copy(fixture.toPath(), copy.toPath());
        AcademyPanel panel = edt(() -> AcademyMain.open(level, copy, progress, AcademySavePolicy.forProject(root)));
        edt(() -> { panel.getMain().setVisible(true); return null; });
        Thread.sleep(300);
        return panel;
    }

    private static void validate(AcademyPanel panel) throws Exception {
        edt(() -> { panel.validateButton().doClick(); return null; });
        for (int i = 0; i < 100 && edt(panel::lastResult) == null; i++)
            Thread.sleep(100);
        Thread.sleep(300);
    }

    /** Front-end text never names the real-world machinery behind the runes. */
    private static void clean(String where, String text) {
        for (String word : FORBIDDEN)
            if (text.contains(word))
                check(where + " avoids " + word, false);
        check(where + " uses no forbidden word", true);
    }

    private static BufferedImage render(JComponent c) {
        BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        c.printAll(g);
        g.dispose();
        return image;
    }

    private static void shot(Main main, String name) throws Exception {
        BufferedImage image = edt(() -> render(main.getRootPane()));
        ImageIO.write(image, "png", new File(shots, name + ".png"));
    }

    private static void check(String text, boolean result) {
        log.println((result ? "ok   " : "FAIL ") + text);
        System.out.println((result ? "ok   " : "FAIL ") + text);
        if (!result)
            throw new AssertionError(text);
        checks++;
    }

    private static <T> T edt(Callable<T> task) throws Exception {
        Object[] value = new Object[1];
        Exception[] error = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                value[0] = task.call();
            } catch (Exception e) {
                error[0] = e;
            }
        });
        if (error[0] != null)
            throw error[0];
        @SuppressWarnings("unchecked") T result = (T) value[0];
        return result;
    }
}
