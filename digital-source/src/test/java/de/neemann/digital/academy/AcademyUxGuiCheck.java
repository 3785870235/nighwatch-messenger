/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.basic.And;
import de.neemann.digital.draw.elements.Pin;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.gui.Main;
import de.neemann.digital.gui.components.CircuitComponent;
import de.neemann.digital.lang.Lang;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * UX1 beginner scenario on real Swing windows with temporary work files: home, level select,
 * guided experiment with a forced and a wrong prediction, repair with real mouse placement and
 * wiring on the Digital canvas, run, validation, and the free workshop palette and native tools.
 * Screenshots at 1280x800 and 1600x1000 go to docs/evidence/ux1-gui. Needs a display.
 */
public final class AcademyUxGuiCheck {
    private static PrintStream log;
    private static File shots;
    private static int failures;

    private AcademyUxGuiCheck() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("academy.skipTransitions", "true");
        System.setProperty("academy.skipIntro", "true");
        javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
        File root = new File(args[0]).getCanonicalFile();
        shots = new File(root, "docs/evidence/ux3-gui");
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
        log("art: " + AcademyArt.status().replace('\n', ';'));
        log("RESULT: " + (failures == 0 ? "ALL CHECKS PASSED" : failures + " FAILURE(S)"));
        log.close();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run(File root) throws Exception {
        File work = Files.createTempDirectory("academy-ux-gui").toFile();
        File levelFile = new File(root, "levels/03_archive_gate/level.properties");
        byte[] templateBefore = Files.readAllBytes(new File(root, "levels/03_archive_gate/template.dig").toPath());
        // the three chapters chain; this check drives the archive door, so the two lamp chapters count as passed
        seed(root, work, "01_report_light");
        seed(root, work, "02_reminder_light");
        AcademyHome home = edt(() -> {
            AcademyHome h = new AcademyHome(levelFile, work, AcademySavePolicy.forProject(root));
            h.setVisible(true);
            return h;
        });
        Thread.sleep(800);
        check("home offers explore card", edt(() -> button(home.getContentPane(), "开始探索") != null));
        check("home offers workshop card", edt(() -> button(home.getContentPane(), "自由工坊") != null));
        shots(home, "01-home");
        edt(() -> { button(home.getContentPane(), "开始探索").doClick(); return null; });
        check("first chapter open", edt(() -> card(home.getContentPane(), "报告灯").isEnabled()));
        check("second chapter open after the first passed", edt(() -> card(home.getContentPane(), "提醒灯").isEnabled()));
        check("archive chapter open after the second passed", edt(() -> card(home.getContentPane(), "档案门").isEnabled()));
        shots(home, "02-levels");

        edt(() -> { card(home.getContentPane(), "档案门").doClick(); return null; });
        AcademyPanel p = waitFor(() -> {
            for (Window w : Window.getWindows())
                if (w instanceof Main && w.isDisplayable() && ((Main) w).getContentPane() instanceof AcademyPanel)
                    return (AcademyPanel) ((Main) w).getContentPane();
            return null;
        });
        Main main = p.getMain();
        CircuitComponent cc = main.getCircuitComponent();
        Thread.sleep(800);

        // experiment: no canvas, no tool bar, no menu
        check("experiment hides canvas", edt(() -> !cc.isShowing()));
        check("experiment hides tool bar", edt(() -> !main.getToolBar().isShowing()));
        check("experiment has no menu bar", edt(() -> main.getJMenuBar() == null));
        check("door starts unobserved", edt(() -> p.door().isUnobserved() && p.door().getStateText().equals("尚未观察")));
        String g0 = edt(p::guideText);
        log("guide 0: " + g0);
        check("first guide names the button", g0.contains("观察回应") && g0.contains("第一步"));
        shots(main, "03-experiment-start");

        edt(() -> { p.hintButton().doClick(); return null; });
        String h1 = edt(p::feedbackText);
        log("hint 1: " + h1);
        check("first hint is operation help", h1.contains("提示 1/3") && h1.contains("观察回应"));
        check("help opens separate notebook", edt(p::isNotebookVisible));
        shots(main, "03b-notebook-help");
        edt(() -> { p.notebookButton().doClick(); return null; });
        check("notebook returns to experiment", !edt(p::isNotebookVisible) && edt(() -> p.door().isShowing()));

        // follow the guide: observe 00, then flip the card it names
        edt(() -> { p.observeButton().doClick(); return null; });
        String g1 = edt(p::guideText);
        log("guide 1: " + g1);
        check("observed combination is marked recorded", g1.contains("已经记在笔记里"));
        check("guide names a card to switch", g1.contains("岗位 B") && g1.contains(ExperimentGuide.ON));
        check("door shows recorded valid reading", edt(() -> !p.door().isUnobserved() && p.door().getReading().isValid()));
        edt(() -> { p.inputBox(1).doClick(); return null; });
        check("switch card shows value", edt(() -> p.inputBox(1).getText().equals(ExperimentGuide.ON)));
        check("new combination unobserved", edt(() -> p.door().isUnobserved()));
        edt(() -> { p.observeButton().doClick(); return null; });

        // a chosen prediction must not carry over to another combination
        edt(() -> { p.inputBox(1).doClick(); p.inputBox(0).doClick(); return null; });
        edt(() -> { p.predictionChip(true).doClick(); return null; });
        check("prediction chip selectable", edt(p::hasPrediction));
        edt(() -> { p.inputBox(1).doClick(); return null; });
        check("switching card clears prediction", !edt(p::hasPrediction));
        edt(() -> { p.inputBox(1).doClick(); return null; });
        edt(() -> { p.observeButton().doClick(); return null; });
        edt(() -> { p.hintButton().doClick(); p.hintButton().doClick(); return null; });
        String h3 = edt(p::feedbackText);
        log("hint 3: " + h3);
        if (edt(p::isNotebookVisible))
            edt(() -> { p.notebookButton().doClick(); return null; });

        // last combination: forced prediction, then a wrong one
        edt(() -> { p.inputBox(1).doClick(); return null; });
        String g3 = edt(p::guideText);
        log("guide 3: " + g3);
        check("last guide asks to predict first", g3.contains("先猜") && g3.contains("锁定预测并观察"));
        check("plain observation blocked", edt(() -> !p.observeButton().isEnabled()));
        check("door unobserved before prediction", edt(() -> p.door().isUnobserved() && !p.door().getReading().isTrue()));
        edt(() -> { p.predictButton().doClick(); return null; });
        check("prediction choice required", edt(p::feedbackText).contains("先在「你的预测」"));
        check("no prediction recorded without choice", p.getSession().getPredictions() == 0);
        edt(() -> { p.predictionChip(false).doClick(); return null; });
        shots(main, "04-prediction-choice");
        edt(() -> { p.predictButton().doClick(); return null; });
        String fb = edt(p::feedbackText);
        log("wrong prediction: " + fb);
        check("wrong prediction gives new clue", fb.startsWith("这次观察给了我们新线索"));
        check("prediction cleared after observing", !edt(p::hasPrediction));
        check("notes truthful", edt(p::tableText).contains("1 | 1 | 0 | 1"));
        check("door open after observing 11", edt(() -> p.door().getReading().isTrue()));
        String experimentText = g0 + g1 + g3 + h1 + h3 + fb + edt(p::guideText);
        check("experiment does not name the rule", !experimentText.contains("AND") && !experimentText.contains("与门"));
        shots(main, "05-prediction-result");
        edt(() -> { p.notebookButton().doClick(); return null; });
        check("notes on dedicated page", edt(p::isNotebookVisible) && edt(() -> !p.door().isShowing()));
        shots(main, "05b-notebook-records");
        edt(() -> { p.notebookButton().doClick(); return null; });

        // repair
        edt(() -> { p.chooseExplanation("both"); p.rebuildButton().doClick(); return null; });
        Thread.sleep(500);
        check("repair shows canvas", edt(cc::isShowing));
        check("repair hides tool bar", edt(() -> !main.getToolBar().isShowing()));
        check("menu bar attached for shortcuts", edt(() -> main.getJMenuBar() != null));
        check("door follows player circuit", edt(() -> p.door().getReading().getKind() == Reading.Kind.NOT_RUNNING));
        log("steps:\n" + edt(p::stepText));
        check("first step current", edt(p::stepText).startsWith("→ 1."));
        shots(main, "06-repair-start");

        edt(() -> { p.workbench().setAdvanced(true); return null; });
        Thread.sleep(300);
        check("advanced shows native tool bar", edt(() -> main.getToolBar().isShowing()));
        check("advanced shows native menu", edt(() -> main.getJMenuBar().getHeight() > 0));
        shots(main, "07-repair-advanced");
        edt(() -> { p.workbench().setAdvanced(false); return null; });

        focus(main, cc);
        edt(() -> { button(main.getContentPane(), "放置双印符文").doClick(); return null; });
        mouse(cc, MouseEvent.MOUSE_MOVED, new Vector(460, 360));
        click(cc, new Vector(460, 360));
        VisualElement and = edt(() -> {
            for (VisualElement ve : cc.getCircuit().getElements())
                if (ve.equalsDescription(And.DESCRIPTION))
                    return ve;
            return null;
        });
        check("AND placed by mouse", and != null);
        check("only one element placed", edt(() -> cc.getCircuit().getElements().size()) == 4);
        check("placing does not wire", edt(() -> !p.repairStatus().isFirstInput() && !p.repairStatus().isOutput()));
        check("dirty title retained", edt(() -> main.getTitle().contains("未保存") && main.isStateChanged()));
        // The save entry must be reachable without opening the menu: unsaved work is exactly what a
        // player loses by closing the window, and the experiment page has no native menu bar at all.
        check("an unsaved change offers the save button",
                edt(() -> p.saveButton().isVisible() && p.saveButton().getWidth() > 0));
        edt(() -> { p.saveButton().doClick(); return null; });
        Thread.sleep(400);
        check("saving from the button clears the dirty state", edt(() -> !main.isStateChanged()));
        check("save button hides again once saved", edt(() -> !p.saveButton().isVisible()));
        List<Vector> ins = new ArrayList<>();
        Vector out = null;
        for (Pin pin : and.getPins())
            if (pin.getDirection() == de.neemann.digital.core.element.PinDescription.Direction.input)
                ins.add(pin.getPos());
            else
                out = pin.getPos();
        log("AND pins " + ins + " -> " + out);
        Vector a = new Vector(300, 200);
        Vector b = new Vector(300, 240);
        Vector permit = new Vector(520, 220);
        check("AND placed in a wireable spot", ins.get(0).x > 320 && ins.get(0).x < 460 && ins.get(0).y >= 280 && out.x < 520);
        // A: horizontal first; B: vertical first so that its wire does not end on A's wire
        wire(cc, a, a.add(20, 0), ins.get(0));
        wire(cc, b, b.add(0, 20), ins.get(1));
        wire(cc, out, out.add(20, 0), permit);
        Thread.sleep(300);
        log("steps:\n" + edt(p::stepText));
        check("structure detected as wired", edt(() -> p.repairStatus().isWired()));
        check("wiring steps ticked", edt(p::stepText).contains("√ 4."));

        edt(() -> { p.workbench().run(); return null; });
        waitFor(() -> main.getModel() != null && main.getModel().isRunning() ? Boolean.TRUE : null);
        Thread.sleep(500);
        check("door closed at A=0 B=0", edt(() -> p.door().getReading().isValid() && !p.door().getReading().isTrue()));
        click(cc, a.add(-8, 0));
        click(cc, b.add(-8, 0));
        Thread.sleep(600);
        check("door opens at A=1 B=1 from player circuit", edt(() -> p.door().getReading().isTrue()));
        check("run step ticked", edt(p::stepText).contains("√ 5."));
        shots(main, "08-repair-running");
        byte[] styledSnapshot = edt(() -> LevelValidator.snapshot(cc.getCircuit()));
        edt(() -> {
            AcademyAtmosphere.magic(false);
            cc.graphicHasChanged();
            cc.repaint();
            return null;
        });
        check("style toggle preserves circuit", java.util.Arrays.equals(styledSnapshot, edt(() -> LevelValidator.snapshot(cc.getCircuit()))));
        shots(main, "08b-standard-circuit");
        edt(() -> {
            AcademyAtmosphere.magic(true);
            cc.graphicHasChanged();
            return null;
        });
        check("style toggle preserves running output", edt(() -> p.door().getReading().isTrue()));

        edt(() -> { p.validateButton().doClick(); return null; });
        waitFor(() -> p.lastResult());
        check("player circuit passes", edt(p::lastResult).isPassed());
        check("stage shows completion", edt(p::stageText).contains("完成"));
        log("result: " + edt(p::resultText));
        shots(main, "09-repair-passed");
        edt(() -> { main.dispose(); return null; });
        check("level timer stopped", !edt(p::isPolling));

        // free workshop
        Main free = edt(() -> home.openFree(null));
        Thread.sleep(800);
        AcademyWorkshop ws = (AcademyWorkshop) free.getContentPane();
        for (String e : new String[]{"In", "Out", "LED", "Not", "And", "Or", "XOr", "more"})
            check("palette has " + e, ws.paletteButton(e) != null);
        check("workshop shows canvas", edt(() -> free.getCircuitComponent().isShowing()));
        CircuitComponent fc = free.getCircuitComponent();
        focus(free, fc);
        edt(() -> { ws.paletteButton("XOr").doClick(); return null; });
        mouse(fc, MouseEvent.MOUSE_MOVED, new Vector(300, 200));
        click(fc, new Vector(300, 200));
        check("palette element placed", edt(() -> fc.getCircuit().getElements().size()) == 1);
        edt(() -> { ws.toggleHelp(); return null; });
        check("help card shown", ws.isHelpVisible());
        shots(free, "10-free");
        edt(() -> { ws.toggleHelp(); ws.paletteButton("more").doClick(); return null; });
        Thread.sleep(400);
        JMenu elements = ws.workbench().findMenu(Lang.get("menu_elements"));
        check("more opens native element menu", edt(() -> elements != null && elements.isPopupMenuVisible()));
        check("more shows native tools", edt(() -> ws.workbench().isAdvanced() && free.getToolBar().isShowing()));
        shot(free, "11-free-native-elements-1280");
        edt(() -> { elements.setPopupMenuVisible(false); return null; });
        check("free mode has no pass state", edt(() -> button(ws, "检验我的术式") == null));
        edt(() -> { free.dispose(); return null; });

        check("level template untouched", java.util.Arrays.equals(templateBefore,
                Files.readAllBytes(new File(root, "levels/03_archive_gate/template.dig").toPath())));
    }

    /**
     * Records a finished chapter in the temporary work directory, so that the chapter after it opens.
     * Every observation is replayed against the real reference; nothing is filled in by hand.
     *
     * @param root    the project root
     * @param workDir the temporary work directory
     * @param id      the level id of the chapter that counts as passed
     * @throws IOException level or progress could not be read
     */
    private static void seed(File root, File workDir, String id) throws IOException {
        LevelDefinition level = LevelDefinition.load(new File(new File(root, "levels"), id + "/level.properties"));
        AcademyProgress progress = new AcademyProgress(new File(workDir, "progress"), level);
        try (ReferenceDevice reference = new ReferenceDevice(level)) {
            ExperimentSession session = new ExperimentSession(level.getInputs());
            int last = session.getCombinationCount() - 1;
            for (int c = 0; c < last; c++)
                session.observe(c, reference.evaluate(session.toInputs(c)));
            session.predictAndObserve(last, reference.evaluate(session.toInputs(last)).isTrue(),
                    reference.evaluate(session.toInputs(last)));
            progress.save(session, true, true);
        }
    }

    private static void focus(Main main, CircuitComponent cc) throws Exception {
        edt(() -> { main.toFront(); cc.requestFocusInWindow(); return null; });
        for (int i = 0; i < 20 && !edt(cc::hasFocus); i++)
            Thread.sleep(100);
        log("canvas focus: " + edt(cc::hasFocus));
    }

    private static void wire(CircuitComponent cc, Vector from, Vector via, Vector to) throws Exception {
        click(cc, from);
        mouse(cc, MouseEvent.MOUSE_MOVED, via);
        mouse(cc, MouseEvent.MOUSE_MOVED, to);
        click(cc, to);
        log("wires after " + from + "->" + to + ": " + edt(() -> cc.getCircuit().getWires().toString()));
    }

    private static void click(CircuitComponent cc, Vector world) throws Exception {
        // One EDT transaction: physical mouse motion in other apps cannot change the preview
        // between the scripted movement and click. Only this JVM's component receives events.
        edt(() -> {
            Point s = cc.transform(world);
            long time = System.currentTimeMillis();
            cc.dispatchEvent(new MouseEvent(cc, MouseEvent.MOUSE_MOVED, time, 0,
                    s.x, s.y, 0, false, MouseEvent.NOBUTTON));
            cc.dispatchEvent(new MouseEvent(cc, MouseEvent.MOUSE_PRESSED, time, InputEvent.BUTTON1_DOWN_MASK,
                    s.x, s.y, 1, false, MouseEvent.BUTTON1));
            cc.dispatchEvent(new MouseEvent(cc, MouseEvent.MOUSE_RELEASED, time, 0,
                    s.x, s.y, 1, false, MouseEvent.BUTTON1));
            return null;
        });
        Thread.sleep(120);
    }

    private static void mouse(CircuitComponent cc, int id, Vector world) throws Exception {
        edt(() -> {
            Point s = cc.transform(world);
            int mods = id == MouseEvent.MOUSE_PRESSED ? InputEvent.BUTTON1_DOWN_MASK : 0;
            int button = id == MouseEvent.MOUSE_MOVED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
            cc.dispatchEvent(new MouseEvent(cc, id, System.currentTimeMillis(), mods, s.x, s.y, 1, false, button));
            return null;
        });
        Thread.sleep(120);
    }

    /** A chapter card, found by the beginning of its title; the card carries a longer title than the chapter name. */
    private static AbstractButton card(Container c, String prefix) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton && ((AbstractButton) x).getText().startsWith(prefix) && x.isShowing())
                return (AbstractButton) x;
            if (x instanceof Container) {
                AbstractButton found = card((Container) x, prefix);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static AbstractButton button(Container c, String text) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton && text.equals(((AbstractButton) x).getText()) && x.isShowing())
                return (AbstractButton) x;
            if (x instanceof Container) {
                AbstractButton found = button((Container) x, text);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static void shots(JFrame f, String name) throws Exception {
        edt(() -> { f.setSize(1600, 1000); f.validate(); return null; });
        Thread.sleep(500);
        shot(f, name + "-1600");
        edt(() -> { f.setSize(1280, 800); f.validate(); return null; });
        Thread.sleep(500);
        shot(f, name + "-1280");
    }

    private static void shot(JFrame f, String name) throws Exception {
        edt(() -> {
            BufferedImage i = new BufferedImage(f.getRootPane().getWidth(), f.getRootPane().getHeight(), BufferedImage.TYPE_INT_RGB);
            f.getRootPane().printAll(i.getGraphics());
            ImageIO.write(i, "png", new File(shots, name + ".png"));
            return null;
        });
        log("shot " + name);
    }

    private static <T> T waitFor(Callable<T> c) throws Exception {
        for (int i = 0; i < 100; i++) {
            T t = edt(c);
            if (t != null)
                return t;
            Thread.sleep(100);
        }
        throw new IllegalStateException("timeout");
    }

    private static void check(String what, boolean ok) {
        if (!ok)
            failures++;
        log((ok ? "ok   " : "FAIL ") + what);
    }

    private static void log(String s) {
        log.println(s);
        System.out.println(s);
    }

    private static <T> T edt(Callable<T> f) throws Exception {
        Object[] r = new Object[1];
        Exception[] ex = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                r[0] = f.call();
            } catch (Exception e) {
                ex[0] = e;
            }
        });
        if (ex[0] != null)
            throw ex[0];
        @SuppressWarnings("unchecked") T t = (T) r[0];
        return t;
    }
}
