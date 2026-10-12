/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.Model;
import de.neemann.digital.core.element.Keys;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.gui.Main;
import de.neemann.digital.gui.components.modification.ModifyAttribute;
import de.neemann.digital.lang.Lang;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * Runs P2 workflows on real Swing windows and native file/dialog actions, in temporary work files.
 * Needs a display; run explicitly, not by surefire.
 */
public final class AcademyP2GuiCheck {
    private static PrintStream log;
    private static File shots;
    private static int failures;

    private AcademyP2GuiCheck() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]).getCanonicalFile();
        shots = new File(root, "docs/evidence/ux3-save-native");
        shots.mkdirs();
        log = new PrintStream(new File(shots, "gui-check.log"), "UTF-8");
        try {
            run(root);
        } catch (Throwable e) {
            failures++;
            e.printStackTrace(log);
        } finally {
            edt(() -> {
                for (Window w : Window.getWindows())
                    w.dispose();
                return null;
            });
        }
        note("RESULT: " + (failures == 0 ? "ALL CHECKS PASSED" : failures + " FAILURE(S)"));
        log.close();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run(File root) throws Exception {
        System.setProperty("academy.skipIntro", "true");
        System.setProperty("academy.skipTransitions", "true");
        File work = Files.createTempDirectory("academy-p2-gui").toFile();
        File levelFile = new File(root, "levels/03_archive_gate/level.properties");
        LevelDefinition level = LevelDefinition.load(levelFile);
        File correct = new File(root, "levels/03_archive_gate/fixtures/and_correct.dig");
        byte[] resourceBefore = Files.readAllBytes(correct.toPath());
        AcademyHome home = edt(() -> {
            AcademyHome h = new AcademyHome(levelFile, work, AcademySavePolicy.forProject(root));
            h.setVisible(true);
            return h;
        });
        shot(home, "01-home");
        edt(() -> { button(home, "开始探索").doClick(); return null; });
        shot(home, "02-level-list");
        AcademyPanel p = edt(home::openLevel);
        check("level opens from home", p != null && !edt(home::isVisible));
        edt(() -> { p.getMain().setSize(1400, 900); return null; });
        edt(() -> {
            p.selectInputs(0);
            p.observeButton().doClick();
            p.selectInputs(3);
            p.selectPrediction(false);
            p.predictButton().doClick();
            return null;
        });
        shot(p.getMain(), "03-partial-progress");
        edt(() -> { p.getMain().saveChanges(); return null; });
        edt(() -> { p.backButton().doClick(); return null; });
        waitFor("home after level return", () -> ui(home::isVisible));
        AcademyPanel restored = edt(home::openLevel);
        check("partial observations restored", restored.getSession().getObservedCount() == 2);
        check("unobserved rows stay empty", restored.getSession().getObserved(1) == null
                && restored.getSession().getObserved(2) == null);
        check("wrong prediction restored honestly", Boolean.FALSE.equals(restored.getSession().getPredicted(3))
                && restored.getSession().getCorrectPredictions() == 0);
        Main levelMain = restored.getMain();
        edt(() -> {
            levelMain.getCircuitComponent().setCircuit(Circuit.loadCircuit(correct, new ShapeFactory(levelMain.getLibrary())));
            return null;
        });
        Main free = edt(() -> home.copyToFree(restored, level));
        check("copy opens distinct circuit window", free != null && free != levelMain);
        byte[] levelSnapshot = edt(() -> LevelValidator.snapshot(levelMain.getCircuitComponent().getCircuit()));
        editLabel(free, "copy-only");
        check("free edits leave level circuit unchanged", Arrays.equals(levelSnapshot,
                edt(() -> LevelValidator.snapshot(levelMain.getCircuitComponent().getCircuit()))));
        shot(free, "04-free-copy");
        edt(() -> {
            scheduleDialogs(free, Lang.get("btn_discard"), null);
            check("discard closes copied free window", AcademyHome.leave(free));
            return null;
        });
        waitFor("return home while source level remains open", () -> ui(home::isVisible));
        check("source level remains available", edt(levelMain::isDisplayable));
        check("reselecting level reuses existing work window", edt(home::openLevel) == restored);

        edt(() -> {
            restored.selectInputs(1); restored.observeButton().doClick();
            restored.selectInputs(2); restored.observeButton().doClick();
            restored.chooseExplanation("both");
            restored.rebuildButton().doClick();
            restored.validateButton().doClick();
            return null;
        });
        waitFor("validation before restoring history", () -> ui(() -> restored.lastResult() != null));
        check("correct level passes", edt(restored::lastResult).isPassed());
        edt(() -> { levelMain.saveChanges(); return null; });
        edt(() -> { restored.backButton().doClick(); return null; });
        waitFor("home after completed level closes", () -> ui(home::isVisible));
        AcademyPanel historic = edt(home::openLevel);
        check("historic pass recorded", edt(historic::isPassedOnce));
        check("restored current circuit still requires validation", edt(historic::lastResult) == null
                && !edt(historic::stageText).contains("完成"));
        edt(() -> {
            historic.validateButton().doClick();
            historic.backButton().doClick();
            return null;
        });
        Thread.sleep(800);
        check("leaving level stops polling", !edt(historic::isPolling));
        check("leaving level discards pending validation", edt(historic::lastResult) == null);

        Main fresh = edt(() -> home.openFree(null));
        check("new free circuit empty", edt(() -> fresh.getCircuitComponent().getCircuit().getElements().isEmpty()));
        edt(() -> {
            fresh.getCircuitComponent().setCircuit(Circuit.loadCircuit(correct, new ShapeFactory(fresh.getLibrary())));
            return null;
        });
        editLabel(fresh, "sandbox-gate");
        File saved = new File(work, "sandbox/saved.dig");
        edt(() -> { scheduleDialogs(fresh, null, saved); fresh.saveChanges(); return null; });
        check("native SaveAs writes sandbox file", saved.isFile() && !edt(fresh::isStateChanged));
        edt(() -> { AcademyHome.leave(fresh); return null; });
        waitFor("home after free save and close", () -> ui(home::isVisible));
        Main opened = edt(() -> home.openFree(saved));
        LevelValidator validator = new LevelValidator(level);
        check("native reopen preserves AND truth table", validator.validate(
                edt(() -> LevelValidator.snapshot(opened.getCircuitComponent().getCircuit()))).isPassed());
        editLabel(opened, "unsaved-gate");
        edt(() -> { opened.start(null, false); return null; });
        waitFor("free model running", () -> opened.getModel() != null && opened.getModel().isRunning());
        Model running = opened.getModel();
        edt(() -> {
            scheduleDialogs(opened, Lang.get("btn_editFurther"), null);
            check("cancel return stays in free window", !AcademyHome.leave(opened));
            return null;
        });
        check("cancel preserves dirty circuit and running model", edt(opened::isStateChanged)
                && opened.getModel() == running && running.isRunning() && edt(opened::isDisplayable));
        Main child = edt(() -> new Main.MainBuilder().setParent(opened).build());
        check("child window inherits save policy", child.getSavePolicy() == opened.getSavePolicy());
        edt(() -> { child.dispose(); return null; });
        edt(() -> {
            scheduleDialogs(opened, Lang.get("btn_save"), null);
            check("save on return closes free mode", AcademyHome.leave(opened));
            return null;
        });
        waitFor("closed free model stops", () -> !running.isRunning());

        File blockedDir = new File(work, "protected");
        blockedDir.mkdirs();
        File blockedFile = new File(blockedDir, "blocked.dig");
        Files.copy(correct.toPath(), blockedFile.toPath());
        byte[] blockedBefore = Files.readAllBytes(blockedFile.toPath());
        Main blocked = edt(() -> new Main.MainBuilder().setFileToOpen(blockedFile)
                .setSavePolicy(new AcademySavePolicy(Arrays.asList(blockedDir))).build());
        edt(() -> { blocked.setVisible(true); return null; });
        editLabel(blocked, "not-saved");
        edt(() -> { blocked.start(null, false); return null; });
        waitFor("protected window model running", () -> blocked.getModel() != null && blocked.getModel().isRunning());
        Model blockedModel = blocked.getModel();
        edt(() -> {
            scheduleDialogs(blocked, Lang.get("btn_save"), null);
            check("refused save prevents leaving", !AcademyHome.leave(blocked));
            return null;
        });
        check("refused save preserves model and dirty state", blocked.getModel() == blockedModel
                && blockedModel.isRunning() && edt(blocked::isStateChanged));
        check("protected file bytes unchanged", Arrays.equals(blockedBefore, Files.readAllBytes(blockedFile.toPath())));
        check("original fixture bytes unchanged", Arrays.equals(resourceBefore, Files.readAllBytes(correct.toPath())));
        edt(() -> { blocked.dispose(); return null; });
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

    private static AbstractButton button(Container c, String text) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton && text.equals(((AbstractButton) x).getText()))
                return (AbstractButton) x;
            if (x instanceof Container) {
                AbstractButton found = button((Container) x, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    // Runs during Swing's modal event loop, only on dialogs belonging to this test window.
    static void scheduleDialogs(Main owner, String choice, File saveAs) {
        long deadline = System.currentTimeMillis() + 6000;
        boolean[] handled = {false};
        Timer t = new Timer(60, null);
        t.addActionListener(e -> {
            for (Window w : Window.getWindows()) {
                if (!(w instanceof JDialog) || !w.isVisible() || w.getOwner() != owner) continue;
                JDialog d = (JDialog) w;
                if (Lang.get("error").equals(d.getTitle())) {
                    note("native save error shown"); d.dispose(); t.stop(); return;
                }
                if (saveAs != null) {
                    JFileChooser fc = findChooser(d);
                    if (fc != null) { fc.setSelectedFile(saveAs); fc.approveSelection(); t.stop(); return; }
                }
                AbstractButton b = choice == null ? null : button(d, choice);
                if (b != null && !handled[0]) { handled[0] = true; b.doClick(); }
                if (System.currentTimeMillis() > deadline) {
                    check("dialog action before timeout", false); d.dispose(); t.stop();
                }
            }
            if (System.currentTimeMillis() > deadline) t.stop();
        });
        t.start();
    }

    private static JFileChooser findChooser(Container c) {
        for (Component x : c.getComponents()) {
            if (x instanceof JFileChooser) return (JFileChooser) x;
            if (x instanceof Container) {
                JFileChooser found = findChooser((Container) x);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void shot(JFrame frame, String name) throws Exception {
        Thread.sleep(250);
        BufferedImage img = edt(() -> {
            BufferedImage i = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
            frame.getRootPane().printAll(i.getGraphics());
            return i;
        });
        ImageIO.write(img, "png", new File(shots, name + ".png"));
    }

    private static boolean ui(Callable<Boolean> f) {
        try { return edt(f); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void waitFor(String what, BooleanSupplier cond) throws Exception {
        for (int i = 0; i < 80; i++) {
            if (cond.getAsBoolean()) { check(what, true); return; }
            Thread.sleep(100);
        }
        throw new IllegalStateException("timeout: " + what);
    }

    private static void check(String what, boolean ok) {
        if (!ok) failures++;
        note((ok ? "ok   " : "FAIL ") + what);
    }

    private static void note(String text) {
        log.println(text); System.out.println(text);
    }

    private static <T> T edt(Callable<T> f) throws Exception {
        Object[] r = new Object[1];
        Exception[] err = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try { r[0] = f.call(); } catch (Exception e) { err[0] = e; }
        });
        if (err[0] != null) throw err[0];
        @SuppressWarnings("unchecked") T out = (T) r[0];
        return out;
    }
}
