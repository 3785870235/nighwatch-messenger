/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.gui.Main;
import de.neemann.digital.lang.Lang;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Callable;

/** Real isolated GUI checks for manual progress, re-entry, reversible lessons and transitions. */
public final class AcademyUx3Check {
    private static PrintStream log;
    private static File shots;
    private static int checks;

    private AcademyUx3Check() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("academy.silent", "true");
        System.clearProperty("academy.skipIntro");
        System.clearProperty("academy.skipTransitions");
        javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
        File root = new File(args[0]);
        shots = new File(root, "docs/evidence/ux3-motion");
        shots.mkdirs();
        log = new PrintStream(new File(shots, "ux3-check.log"), "UTF-8");
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
        LevelDefinition level = LevelDefinition.load(new File(root, "levels/03_archive_gate/level.properties"));
        File dir = Files.createTempDirectory("academy-ux3-check").toFile();
        File dest = AcademyMain.prepareWorkFile(level, new File(dir, "work"));
        AcademyProgress progress = new AcademyProgress(new File(dir, "progress"), level);
        check("opening destination creates no automatic circuit", !dest.exists());
        AcademyPanel first = edt(() -> AcademyMain.open(level, dest, progress, AcademySavePolicy.forProject(root)));
        edt(() -> { first.getMain().setVisible(true); return null; });
        Thread.sleep(350);
        check("first entrance has visible animated intro", edt(() -> first.cinematic() != null && first.cinematic().isShowing()));
        check("handwriting title has Chinese glyphs", AcademyTheme.script(28).canDisplayUpTo("午夜光术学院符文钥印") == -1);
        log.println("script family: " + AcademyTheme.script(28).getFamily());
        File frames = new File(shots, "cinematic-frames");
        frames.mkdirs();
        // Deterministic times drive the actual runtime renderer, not an external concept mockup.
        for (int i = 0; i < 126; i++) {
            final double time = i / 15.0;
            BufferedImage frame = edt(() -> {
                first.cinematic().previewAt(time);
                return render(first.getMain().getRootPane());
            });
            ImageIO.write(frame, "png", new File(frames, String.format("%04d.png", i)));
            if (i == 24 || i == 60 || i == 108)
                ImageIO.write(frame, "png", new File(shots, "intro-shot-" + i + ".png"));
        }
        check("cinematic recording never creates a save", !dest.exists() && !progress.getFile().exists());
        edt(() -> { first.cinematic().continueButton().doClick(); return null; });
        settle();
        // Story1: the doubt dialogue and the three candidates come before the basic tutorial
        check("intro leads to doubt dialogue", edt(() -> first.page().equals("story") && first.story().page() == 0));
        edt(() -> { first.story().skipButton().doClick(); return null; });
        settle();
        check("doubt dialogue leads to candidates", edt(() -> first.page().equals("case")));
        edt(() -> { first.caseBackButton().doClick(); return null; });
        settle();
        check("intro leads to one-step tutorial", edt(() -> first.page().equals("lesson") && first.lesson().page() == 0));
        shot(first.getMain(), "01-lesson-first");
        edt(() -> { first.lesson().nextButton().doClick(); return null; });
        check("lesson change uses actual transition layer", edt(() -> first.getMain().getGlassPane() instanceof AcademyTransition
                && first.getMain().getGlassPane().isVisible()));
        File transitions = new File(shots, "transition-frames");
        transitions.mkdirs();
        for (int i = 0; i < 16; i++) {
            BufferedImage frame = edt(() -> render(first.getMain().getRootPane()));
            ImageIO.write(frame, "png", new File(transitions, String.format("%04d.png", i)));
            Thread.sleep(35);
        }
        check("transition finishes and restores glass", edt(() -> !(first.getMain().getGlassPane() instanceof AcademyTransition)));
        check("lesson advances only one step", edt(() -> first.lesson().page() == 1));
        edt(() -> { first.lesson().previousButton().doClick(); return null; });
        settle();
        check("previous lesson is rereadable", edt(() -> first.lesson().page() == 0));
        shot(first.getMain(), "02-lesson-back");
        edt(() -> { first.notebookButton().doClick(); return null; });
        settle();
        edt(() -> { first.notebookButton().doClick(); return null; });
        settle();
        check("notebook returns to same tutorial page", edt(() -> first.page().equals("lesson") && first.lesson().page() == 0));
        // the experiment lesson has five pages now (一份报告 … 交给检验处)
        finishLesson(first, 5);
        check("lesson returns to investigation", edt(() -> first.page().equals("experiment")));
        edt(() -> { first.observeButton().doClick(); return null; });
        check("observation is unsaved state", edt(() -> first.isProgressDirty() && first.getMain().isStateChanged()));
        check("observation writes neither progress nor circuit", !dest.exists() && !progress.getFile().exists());
        boolean cancelled = edt(() -> {
            dialogs(first.getMain(), Lang.get("btn_editFurther"));
            return AcademyHome.leave(first.getMain());
        });
        check("cancel exit keeps unsaved observations and window", !cancelled && edt(() -> first.getMain().isDisplayable()
                && first.getSession().getObservedCount() == 1 && first.isProgressDirty()));
        edt(() -> { first.getMain().saveChanges(); return null; });
        check("explicit native save writes both files", dest.exists() && progress.getFile().exists());
        check("successful save clears all unsaved state", edt(() -> !first.getMain().isStateChanged() && !first.isProgressDirty()));
        byte[] saved = Files.readAllBytes(progress.getFile().toPath());
        edt(() -> { first.selectInputs(1); first.observeButton().doClick(); return null; });
        check("later observation does not replace manual save", Arrays.equals(saved, Files.readAllBytes(progress.getFile().toPath())));
        boolean discarded = edt(() -> {
            dialogs(first.getMain(), Lang.get("btn_discard"));
            return AcademyHome.leave(first.getMain());
        });
        check("discard closes without saving new observation", discarded && Arrays.equals(saved, Files.readAllBytes(progress.getFile().toPath())));
        AcademyPanel second = edt(() -> AcademyMain.open(level, dest, progress, AcademySavePolicy.forProject(root)));
        edt(() -> { second.getMain().setVisible(true); return null; });
        Thread.sleep(350);
        check("second entrance still has intro despite saved observations", edt(() -> second.cinematic() != null && second.cinematic().isShowing()));
        check("only explicitly saved observations restore", second.getSession().getObservedCount() == 1);
        System.clearProperty("academy.skipTutorial");
        edt(() -> { second.cinematic().continueButton().doClick(); return null; });
        settle();
        check("saved observations avoid repeating basic tutorial", edt(() -> second.page().equals("experiment")));
        edt(() -> {
            second.selectInputs(1); second.observeButton().doClick();
            second.selectInputs(2); second.observeButton().doClick();
            second.selectInputs(3); second.selectPrediction(false); second.predictButton().doClick();
            second.chooseExplanation("both");
            second.rebuildButton().doClick();
            return null;
        });
        settle();
        check("first repair automatically opens its five-step lesson", edt(() -> second.page().equals("lesson") && second.lesson().page() == 0));
        finishLesson(second, 5);
        check("building page has actual canvas without embedded door", edt(() -> second.getMain().getCircuitComponent().isShowing() && !second.door().isShowing()));
        check("entering repair never autosaves progress", Arrays.equals(saved, Files.readAllBytes(progress.getFile().toPath())));
        shot(second.getMain(), "03-build-minimal");
        edt(() -> { second.lessonForTest(); return null; });
        settle();
        check("repair tutorial can be reopened", edt(() -> second.page().equals("lesson") && second.lesson().page() == 0));
        finishLesson(second, 5);
        check("repair lesson returns to its scene", edt(() -> second.page().equals("repair")));
        edt(() -> { second.inspectForTest(); return null; });
        settle();
        check("inspection is separate from editor", edt(() -> second.page().equals("inspect") && !second.getMain().getCircuitComponent().isShowing() && second.door().isShowing()));
        shot(second.getMain(), "04-inspect-missing");
        edt(() -> { second.getMain().dispose(); return null; });
        check("closing scene stops polling", !edt(second::isPolling));
        System.setProperty("academy.skipIntro", "true");
        File fixture = new File(root, "levels/03_archive_gate/fixtures/and_correct.dig");
        File running = new File(dir, "correct.dig");
        Files.copy(fixture.toPath(), running.toPath());
        AcademyPanel correct = edt(() -> AcademyMain.open(level, running, progress, AcademySavePolicy.forProject(root)));
        edt(() -> { correct.getMain().setVisible(true); return null; });
        edt(() -> {
            correct.selectInputs(1); correct.observeButton().doClick();
            correct.selectInputs(2); correct.observeButton().doClick();
            correct.selectInputs(3); correct.selectPrediction(true); correct.predictButton().doClick();
            correct.chooseExplanation("both");
            correct.rebuildButton().doClick();
            return null;
        });
        settle();
        edt(() -> { correct.inspectForTest(); return null; });
        settle();
        check("inspection starts real model", edt(() -> correct.getMain().getModel().isRunning()));
        edt(() -> { correct.playerReport(0).doClick(); correct.playerReport(1).doClick(); return null; });
        Thread.sleep(450);
        check("inspection switches drive real player output", edt(() -> correct.door().getReading().isTrue()));
        shot(correct.getMain(), "05-inspect-open");
        edt(() -> { correct.validateButton().doClick(); return null; });
        for (int i = 0; i < 60 && edt(correct::lastResult) == null; i++)
            Thread.sleep(100);
        settle();
        check("trusted result passes on separate result page", edt(() -> correct.page().equals("result") && correct.lastResult() != null && correct.lastResult().isPassed()));
        check("passing is unsaved and does not replace save", correct.isProgressDirty() && Arrays.equals(saved, Files.readAllBytes(progress.getFile().toPath())));
        shot(correct.getMain(), "06-result");
        File renamed = new File(dir, "chosen-save.dig");
        edt(() -> {
            AcademyP2GuiCheck.scheduleDialogs(correct.getMain(), null, renamed);
            javax.swing.JMenu fileMenu = correct.workbench().findMenu(Lang.get("menu_file"));
            for (int i = 0; i < fileMenu.getItemCount(); i++) {
                javax.swing.JMenuItem item = fileMenu.getItem(i);
                if (item != null && Lang.get("menu_saveAs").equals(item.getText())) {
                    item.doClick();
                    break;
                }
            }
            return null;
        });
        check("native Save As stores chosen circuit with progress", renamed.exists() && renamed.getCanonicalFile().equals(progress.savedCircuit()));
        edt(() -> { correct.getMain().dispose(); return null; });
        AcademyHome reopen = edt(() -> new AcademyHome(new File(root, "levels/03_archive_gate/level.properties"), dir, AcademySavePolicy.forProject(root)));
        AcademyPanel chosen = edt(reopen::openLevel);
        Thread.sleep(350);
        check("re-entry opens manually chosen circuit instead of default template", edt(() -> chosen.getMain().getBaseFileName().getCanonicalFile().equals(renamed.getCanonicalFile()) && chosen.isPassedOnce() && chosen.lastResult() == null));
        edt(() -> { chosen.getMain().dispose(); reopen.dispose(); return null; });
        // Progress failure after circuit save must never authorize exit or clear dirty state.
        File blocked = new File(dir, "blocked-progress");
        Files.write(blocked.toPath(), new byte[]{1});
        AcademyProgress badProgress = new AcademyProgress(blocked, level);
        AcademyPanel bad = edt(() -> AcademyMain.open(level, running, badProgress, AcademySavePolicy.forProject(root)));
        edt(() -> { bad.getMain().setVisible(true); bad.observeButton().doClick(); return null; });
        boolean failedSaveExit = edt(() -> {
            dialogs(bad.getMain(), Lang.get("btn_save"));
            return AcademyHome.leave(bad.getMain());
        });
        check("failed companion save preserves window and unsaved state", !failedSaveExit && bad.getMain().isDisplayable() && bad.isProgressDirty());
        edt(() -> { bad.getMain().dispose(); return null; });
        AcademyAtmosphere.motion(false);
        JFrame reduced = edt(() -> {
            JFrame f = new JFrame(); f.setContentPane(new javax.swing.JPanel()); f.setSize(800, 500); f.setVisible(true); return f;
        });
        boolean[] changed = {false};
        edt(() -> { AcademyTransition.change((JComponent) reduced.getContentPane(), () -> changed[0] = true); return null; });
        check("reduce motion navigates immediately without animation", changed[0] && !(reduced.getGlassPane() instanceof AcademyTransition));
        edt(() -> { reduced.dispose(); return null; });
    }

    private static void finishLesson(AcademyPanel panel, int pages) throws Exception {
        for (int i = 0; i < pages; i++) {
            edt(() -> { panel.lesson().nextButton().doClick(); return null; });
            settle();
        }
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
        c.printAll(g); g.dispose(); return image;
    }

    private static void shot(Main main, String name) throws Exception {
        BufferedImage image = edt(() -> render(main.getRootPane()));
        ImageIO.write(image, "png", new File(shots, name + ".png"));
    }

    /**
     * Waits until the scene dissolve has finished. A fixed sleep breaks as soon as the dissolve gets longer,
     * because a click during the dissolve is rejected on purpose.
     */
    private static void settle() throws Exception {
        Thread.sleep(60);
        long end = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < end && edt(() -> {
            for (java.awt.Window w : java.awt.Window.getWindows())
                if (w instanceof javax.swing.RootPaneContainer) {
                    java.awt.Component glass = ((javax.swing.RootPaneContainer) w).getRootPane().getGlassPane();
                    if (glass instanceof AcademyTransition && glass.isVisible())
                        return true;
                }
            return false;
        }))
            Thread.sleep(20);
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
