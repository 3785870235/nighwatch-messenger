/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.element.Keys;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.gui.Main;
import de.neemann.digital.gui.components.modification.ModifyAttribute;
import de.neemann.digital.lang.Lang;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
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
 * Story1 scripted Swing walk through the investigation in temporary work directories:
 * doubt dialogue, candidates refuted by real observations in different orders, a wrong
 * explanation, discovery, repair, a real current pass and the clue; manual save, discard,
 * restore, legacy progress, failed companion save and closing during a validation.
 * Run: java -cp target/test-classes;target/Digital.jar de.neemann.digital.academy.AcademyStory1Check &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyStory1Check {
    private static PrintStream log;
    private static File shots;
    private static int checks;

    private AcademyStory1Check() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("academy.silent", "true");
        System.setProperty("academy.skipIntro", "true");
        System.setProperty("academy.skipTutorial", "true");
        System.setProperty("academy.skipTransitions", "true");
        javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
        File root = new File(args[0]);
        shots = new File(root, "docs/evidence/story1-gui");
        shots.mkdirs();
        log = new PrintStream(new File(shots, "story1-check.log"), "UTF-8");
        int result = 0;
        try {
            run(root);
            log.println("RESULT: " + checks + " CHECKS PASSED");
        } catch (Throwable e) {
            e.printStackTrace(log);
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
        File levelFile = new File(root, "levels/03_archive_gate/level.properties");
        LevelDefinition level = LevelDefinition.load(levelFile);
        File fixture = new File(root, "levels/03_archive_gate/fixtures/and_correct.dig");
        byte[] fixtureBefore = Files.readAllBytes(fixture.toPath());
        byte[] storyBefore = Files.readAllBytes(level.getStoryFile().toPath());
        File dir = Files.createTempDirectory("academy-story1-check").toFile();
        log.println("temp: " + dir);
        File dest = AcademyMain.prepareWorkFile(level, new File(dir, "work"));
        AcademyProgress progress = new AcademyProgress(new File(dir, "progress"), level);

        // --- doubt dialogue ---
        AcademyPanel p = edt(() -> AcademyMain.open(level, dest, progress, AcademySavePolicy.forProject(root)));
        edt(() -> { p.getMain().setVisible(true); return null; });
        Thread.sleep(300);
        check("story loaded with three candidates", edt(() -> p.investigation() != null && p.investigation().getCandidates().size() == 3));
        check("fresh level is not briefed", !edt(p::isBriefed));
        edt(() -> { p.openBriefing(); return null; });
        check("doubt dialogue opens on first page", edt(() -> p.page().equals("story") && p.story().page() == 0 && p.story().pages() >= 4));
        check("first page has a speaker and no previous", edt(() -> p.story().lineText().startsWith("芙") && !p.story().previousButton().isEnabled()));
        shot(p.getMain(), "01-doubt-fu");
        edt(() -> { p.story().nextButton().doClick(); return null; });
        check("one click turns exactly one page", edt(() -> p.story().page() == 1));
        edt(() -> { p.story().previousButton().doClick(); return null; });
        check("dialogue is reversible", edt(() -> p.story().page() == 0));
        StringBuilder dialogue = new StringBuilder();
        int pages = edt(() -> p.story().pages());
        for (int i = 0; i < pages; i++) {
            dialogue.append(edt(() -> p.story().lineText())).append('\n');
            if (i == 2)
                shot(p.getMain(), "02-doubt-glimmer");
            if (i == 3)
                shot(p.getMain(), "03-doubt-luo-placeholder");
            if (i + 1 < pages)
                edt(() -> { p.story().nextButton().doClick(); return null; });
        }
        log.println(dialogue);
        check("dialogue has all three characters", dialogue.indexOf("芙") >= 0 && dialogue.indexOf("微光") >= 0 && dialogue.indexOf("洛") >= 0);
        check("dialogue reveals neither AND nor truth table", dialogue.indexOf("AND") < 0 && dialogue.indexOf("与门") < 0 && dialogue.indexOf("真值表") < 0);
        check("last page offers the candidates", edt(() -> p.story().nextButton().getText().contains("残页") && !p.story().skipButton().isVisible()));
        edt(() -> { p.story().nextButton().doClick(); return null; });
        check("dialogue ends on candidate page", edt(() -> p.page().equals("case") && p.isBriefed()));
        String empty = edt(p::caseText);
        log.println(empty);
        check("no evidence before a choice", empty.contains("选了，我才把证据念给你听") && !empty.contains("推翻")
                && !empty.contains("尚无证据") && !empty.contains("AND"));
        check("repair locked without evidence", !edt(() -> p.caseRepairButton().isEnabled()));
        shot(p.getMain(), "04-candidates-empty");
        check("pages are read one at a time, starting with the first", edt(() -> p.casebook().page() == 0
                && p.casebook().pages() == 3 && !p.casebook().previousButton().isEnabled()
                && p.casebook().text().contains("翻到：残页甲")));
        edt(() -> { p.casebook().nextButton().doClick(); return null; });
        check("next turns exactly one page", edt(() -> p.casebook().page() == 1 && p.casebook().text().contains("翻到：残页乙")));
        edt(() -> { p.casebook().nextButton().doClick(); return null; });
        check("last page cannot turn further", edt(() -> p.casebook().page() == 2 && !p.casebook().nextButton().isEnabled()));
        edt(() -> { p.casebook().previousButton().doClick(); p.casebook().previousButton().doClick(); return null; });
        check("turning back reaches the first page", edt(() -> p.casebook().page() == 0));
        check("reading the dialogue writes nothing", !dest.exists() && !progress.getFile().exists());

        // --- order 1: 00, 11 with a refuted choice ---
        edt(() -> { p.caseChip(2).doClick(); return null; });
        check("choosing turns to the chosen page", edt(() -> p.casebook().page() == 2));
        check("choosing an explanation is unsaved state", edt(() -> p.investigation().getChoice().getId().equals("single")
                && p.isProgressDirty() && p.getMain().isStateChanged()));
        check("choice writes nothing to disk", !dest.exists() && !progress.getFile().exists());
        edt(() -> { p.caseBackButton().doClick(); return null; });
        check("back returns to the door", edt(() -> p.page().equals("experiment")));
        edt(() -> { p.selectInputs(0); return null; });
        check("door names what candidates say for 00", edt(p::caseLineText).contains("分不出"));
        edt(() -> { p.observeButton().doClick(); return null; });
        check("00 asks to compare and names no page", edt(p::feedbackText).contains("对照残页")
                && !edt(p::feedbackText).contains("推翻") && edt(p::caseLineText).contains("门保持关闭"));
        edt(() -> { p.selectInputs(3); return null; });
        check("door shows that 11 separates", edt(p::caseLineText).contains("能区分"));
        check("experiment line does not name the rule", !edt(p::caseLineText).contains("AND"));
        edt(() -> { p.observeButton().doClick(); return null; });
        check("11 lists the claims next to the response but does not judge", edt(p::caseLineText).contains("门打开了")
                && edt(p::caseLineText).contains("丙 不开") && !edt(p::feedbackText).contains("残页丙")
                && !edt(p::feedbackText).contains("推翻") && !edt(p::caseLineText).contains("推翻"));
        shot(p.getMain(), "05-door-11-refutes");
        edt(() -> { p.casesButton().doClick(); return null; });
        String afterEleven = edt(p::caseText);
        log.println(afterEleven);
        check("counterexample is concrete", afterEleven.contains("反例 岗位 A 持钥印 · 岗位 B 持钥印时，门开了，它却说门不开")
                && !afterEleven.contains("A=1"));
        check("refuted choice gets evidence feedback", afterEleven.contains("「残页丙 · 单席印」已被你的记录反驳"));
        check("two candidates survive", afterEleven.contains("仍有 2 张"));
        shot(p.getMain(), "06-candidates-refuted-choice");

        // --- explicit save ---
        edt(() -> { p.getMain().saveChanges(); return null; });
        check("explicit save writes circuit and progress", dest.exists() && progress.getFile().exists()
                && edt(() -> !p.isProgressDirty() && !p.getMain().isStateChanged()));
        String saved = new String(Files.readAllBytes(progress.getFile().toPath()), StandardCharsets.ISO_8859_1);
        check("save stores choice and briefing, not candidate states", saved.contains("story.choice=single")
                && saved.contains("story.briefed=true") && !saved.contains("any") && !saved.contains("both"));
        byte[] savedBytes = Files.readAllBytes(progress.getFile().toPath());

        // --- continue unsaved: wrong prediction, wrong choice, then the right one ---
        edt(() -> { p.chooseExplanation("any"); p.caseBackButton().doClick(); return null; });
        edt(() -> { p.selectInputs(2); p.observeButton().doClick(); return null; });
        check("10 lists the claims without naming the refuted page", edt(p::caseLineText).contains("甲 会开")
                && !edt(p::feedbackText).contains("残页甲"));
        edt(() -> { p.selectInputs(1); return null; });
        check("last combination needs a prediction", edt(() -> !p.observeButton().isVisible() && p.predictButton().isVisible()));
        edt(() -> { p.selectPrediction(true); p.predictButton().doClick(); return null; });
        check("wrong prediction is recorded as a new clue", edt(p::feedbackText).contains("新线索")
                && p.getSession().getPredicted(1) && !p.getSession().getObserved(1).isTrue());
        check("complete records but refuted choice: no repair", edt(() -> p.getSession().isComplete()
                && p.rebuildButton().getText().contains("整理残页")));
        edt(() -> { p.rebuildButton().doClick(); return null; });
        check("rebuild button leads to candidates, not repair", edt(() -> p.page().equals("case") && !p.isRebuildStage()));
        String wrong = edt(p::caseText);
        check("wrong choice is told by evidence", wrong.contains("「残页甲 · 任一印」已被你的记录反驳") && !wrong.contains("AND"));
        shot(p.getMain(), "07-complete-wrong-choice");
        edt(() -> { p.caseChip(1).doClick(); return null; });
        String found = edt(p::caseText);
        log.println(found);
        check("right choice unlocks knowledge and repair", found.contains("证据支持你的判断") && found.contains("双印符文")
                && !found.contains("AND") && !found.contains("Computer Organization") && !found.contains("与门")
                && edt(() -> p.caseRepairButton().isEnabled()));
        shot(p.getMain(), "08-candidates-discovered");
        check("observations and choices were not autosaved", Arrays.equals(savedBytes, Files.readAllBytes(progress.getFile().toPath())));

        // --- discard, then restore only the explicit save ---
        boolean discarded = edt(() -> {
            dialogs(p.getMain(), Lang.get("btn_discard"));
            return AcademyHome.leave(p.getMain());
        });
        check("discard leaves without saving", discarded && Arrays.equals(savedBytes, Files.readAllBytes(progress.getFile().toPath())));
        AcademyPanel q = edt(() -> AcademyMain.open(level, dest, progress, AcademySavePolicy.forProject(root)));
        edt(() -> { q.getMain().setVisible(true); return null; });
        check("restore keeps only saved observations and choice", q.getSession().getObservedCount() == 2
                && edt(() -> q.investigation().getChoice().getId().equals("single") && q.isBriefed() && !q.isProgressDirty()));
        edt(() -> { q.showCase(); return null; });
        String restored = edt(q::caseText);
        check("candidate states rebuilt from replayed observations", restored.contains("反例 岗位 A 持钥印 · 岗位 B 持钥印")
                && restored.contains("仍有 2 张"));

        // --- order 2 in the restored session: 01 first, 10 predicted correctly ---
        edt(() -> { q.caseBackButton().doClick(); q.selectInputs(1); q.observeButton().doClick(); return null; });
        check("01 compares without naming the refuted page", edt(q::feedbackText).contains("对照残页")
                && !edt(q::feedbackText).contains("残页甲"));
        edt(() -> { q.selectInputs(2); q.selectPrediction(false); q.predictButton().doClick(); return null; });
        check("correct prediction confirms", edt(q::feedbackText).contains("猜对了") && q.getSession().isComplete());
        edt(() -> { q.chooseExplanation("both"); return null; });
        check("discovery in a different order", edt(() -> q.investigation().canRepair(q.getSession())
                && q.rebuildButton().getText().contains("进入修复")));
        edt(() -> { q.rebuildButton().doClick(); return null; });
        check("repair opens after discovery", edt(() -> q.isRebuildStage() && q.page().equals("repair")));
        check("no clue before validation", !edt(() -> q.clueButton().isVisible()));
        edt(() -> { q.getMain().saveChanges(); return null; });
        String saved2 = new String(Files.readAllBytes(progress.getFile().toPath()), StandardCharsets.ISO_8859_1);
        check("second save stores new choice and repair", saved2.contains("story.choice=both") && saved2.contains("rebuild=true"));
        edt(() -> { q.getMain().dispose(); return null; });

        // --- legacy UX3 progress with a historical pass on the correct circuit ---
        File legacyDir = new File(dir, "legacy");
        AcademyProgress legacy = new AcademyProgress(legacyDir, level);
        try (ReferenceDevice ref = new ReferenceDevice(level)) {
            ExperimentSession s = new ExperimentSession(level.getInputs());
            for (int c = 0; c < 3; c++)
                s.observe(c, ref.evaluate(s.toInputs(c)));
            s.predictAndObserve(3, true, ref.evaluate(s.toInputs(3)));
            legacy.save(s, true, true);
        }
        check("legacy progress has no story keys", !new String(Files.readAllBytes(legacy.getFile().toPath()),
                StandardCharsets.ISO_8859_1).contains("story."));
        byte[] legacyBytes = Files.readAllBytes(legacy.getFile().toPath());
        File running = new File(dir, "correct.dig");
        Files.copy(fixture.toPath(), running.toPath());
        AcademyPanel c = edt(() -> AcademyMain.open(level, running, legacy, AcademySavePolicy.forProject(root)));
        edt(() -> { c.getMain().setVisible(true); return null; });
        Thread.sleep(300);
        check("legacy save restores repair without tutorial or briefing", edt(() -> c.isRebuildStage()
                && c.page().equals("repair") && !c.isBriefed() && c.investigation().getChoice() == null));
        check("historical pass is not a current pass", edt(() -> c.isPassedOnce() && c.lastResult() == null && !c.clueButton().isVisible()));
        edt(() -> { c.openClue(); return null; });
        check("clue cannot be opened from history", edt(() -> c.page().equals("repair")));
        edt(() -> { c.showCase(); return null; });
        check("legacy discovery hides evidence and knowledge until a choice", edt(c::caseText).contains("选了，我才把证据念给你听")
                && !edt(c::caseText).contains("只有一张残页经得起") && !edt(c::caseText).contains("双印符文"));
        edt(() -> { c.caseRepairButton().doClick(); return null; });
        check("case page returns to repair", edt(() -> c.page().equals("repair")));

        validate(c);
        check("current circuit truly passes", edt(() -> c.lastResult().isPassed() && c.page().equals("result")));
        check("clue unlocked only by current pass", edt(() -> c.clueButton().isVisible()));
        shot(c.getMain(), "09-result-clue-unlocked");
        edt(() -> { c.clueButton().doClick(); return null; });
        check("clue dialogue opens", edt(() -> c.page().equals("story") && c.story().heading().equals("校准页")));
        StringBuilder clue = new StringBuilder();
        int cluePages = edt(() -> c.story().pages());
        for (int i = 0; i < cluePages; i++) {
            clue.append(edt(() -> c.story().lineText())).append('\n');
            if (i == 3)
                shot(c.getMain(), "10-clue-reveal");
            if (i + 1 < cluePages)
                edt(() -> { c.story().nextButton().doClick(); return null; });
        }
        log.println(clue);
        check("clue reveals not-received is not false", clue.indexOf("没收到") >= 0 && clue.indexOf("不成立") >= 0);
        check("clue leaves an open doubt", clue.indexOf("谁") >= 0 || clue.indexOf("？") >= 0);
        edt(() -> { c.story().nextButton().doClick(); return null; });
        check("clue returns to the result", edt(() -> c.page().equals("result")));
        check("pass and clue write nothing", Arrays.equals(legacyBytes, Files.readAllBytes(legacy.getFile().toPath())));
        editLabel(c.getMain(), "seal");
        check("changing the circuit hides the clue", edt(() -> c.lastResult() == null && !c.clueButton().isVisible()));
        edt(() -> { c.openClue(); return null; });
        check("clue refused after circuit change", edt(() -> !c.page().equals("story")));

        // --- async validation is dropped when the window closes ---
        edt(() -> { c.validateButton().doClick(); c.getMain().dispose(); return null; });
        Thread.sleep(1500);
        check("closing during validation stops timer and drops the result", !edt(c::isPolling) && edt(c::lastResult) == null);

        // --- companion save failure keeps everything unsaved ---
        File blocked = new File(dir, "blocked-progress");
        Files.write(blocked.toPath(), new byte[]{1});
        AcademyProgress badProgress = new AcademyProgress(blocked, level);
        File running2 = new File(dir, "correct2.dig");
        Files.copy(fixture.toPath(), running2.toPath());
        AcademyPanel bad = edt(() -> AcademyMain.open(level, running2, badProgress, AcademySavePolicy.forProject(root)));
        edt(() -> { bad.getMain().setVisible(true); bad.chooseExplanation("both"); return null; });
        boolean left = edt(() -> {
            dialogs(bad.getMain(), Lang.get("btn_save"));
            return AcademyHome.leave(bad.getMain());
        });
        check("failed save keeps window, choice and dirty state", !left && edt(() -> bad.getMain().isDisplayable()
                && bad.isProgressDirty() && bad.investigation().getChoice() != null));
        edt(() -> { bad.getMain().dispose(); return null; });

        check("fixture and story resources unchanged", Arrays.equals(fixtureBefore, Files.readAllBytes(fixture.toPath()))
                && Arrays.equals(storyBefore, Files.readAllBytes(level.getStoryFile().toPath())));
    }

    private static void validate(AcademyPanel panel) throws Exception {
        edt(() -> { panel.validateButton().doClick(); return null; });
        for (int i = 0; i < 100 && edt(panel::lastResult) == null; i++)
            Thread.sleep(100);
        Thread.sleep(300);
    }

    private static void editLabel(Main main, String label) throws Exception {
        edt(() -> {
            for (VisualElement v : main.getCircuitComponent().getCircuit().getElements())
                if ("And".equals(v.getElementName())) {
                    main.getCircuitComponent().modify(new ModifyAttribute<>(v, Keys.LABEL, label));
                    break;
                }
            return null;
        });
    }

    private static void dialogs(Main owner, String choice) {
        long deadline = System.currentTimeMillis() + 6000;
        boolean[] handled = {false};
        Timer timer = new Timer(60, null);
        timer.addActionListener(e -> {
            for (Window w : Window.getWindows()) {
                if (!(w instanceof JDialog) || !w.isVisible() || w.getOwner() != owner)
                    continue;
                JDialog dialog = (JDialog) w;
                if (Lang.get("error").equals(dialog.getTitle())) {
                    dialog.dispose(); timer.stop(); return;
                }
                AbstractButton button = button(dialog, choice);
                if (!handled[0] && button != null) {
                    handled[0] = true;
                    button.doClick();
                    if (!Lang.get("btn_save").equals(choice))
                        timer.stop();
                }
            }
            if (System.currentTimeMillis() > deadline) {
                timer.stop();
                for (Window w : Window.getWindows())
                    if (w instanceof JDialog && w.getOwner() == owner)
                        w.dispose();
            }
        });
        timer.start();
    }

    private static AbstractButton button(Container owner, String text) {
        for (Component c : owner.getComponents()) {
            if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()))
                return (AbstractButton) c;
            if (c instanceof Container) {
                AbstractButton found = button((Container) c, text);
                if (found != null)
                    return found;
            }
        }
        return null;
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
