/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.Model;
import de.neemann.digital.core.Signal;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.gui.Main;
import de.neemann.digital.gui.components.modification.ModifyDeleteWire;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * Scripted GUI check of the archive gate prototype. Needs a display; not run by surefire.
 * Drives the real panel and Digital window: experiment, prediction, rebuild, validation,
 * running simulation driving the door, isolation of validation and disposal.
 * Usage: java -cp Digital.jar;test-classes de.neemann.digital.academy.AcademyGuiCheck [projectRoot]
 */
public final class AcademyGuiCheck {
    private static PrintStream log;
    private static File shots;
    private static int failures;

    private AcademyGuiCheck() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "..").getCanonicalFile();
        shots = new File(root, "docs/evidence/p1-gui");
        shots.mkdirs();
        log = new PrintStream(Files.newOutputStream(new File(shots, "gui-check.log").toPath()), true, "UTF-8");
        try {
            run(root);
        } catch (Throwable t) {
            failures++;
            t.printStackTrace(log);
        }
        log("RESULT: " + (failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED"));
        log.close();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run(File root) throws Exception {
        LevelDefinition level = LevelDefinition.load(new File(root, "levels/03_archive_gate/level.properties"));
        File fixtures = new File(root, "levels/03_archive_gate/fixtures");
        byte[] templateBefore = Files.readAllBytes(level.getTemplateFile().toPath());
        File work = AcademyMain.prepareWorkFile(level, Files.createTempDirectory("academy-gui").toFile());

        AcademyPanel p = edt(() -> {
            AcademyPanel x = AcademyMain.open(level, work);
            x.getMain().setSize(1400, 860);
            x.getMain().setVisible(true);
            return x;
        });
        Main main = p.getMain();
        Thread.sleep(1500);
        log("start: " + edt(p::stageText));
        log(edt(p::tableText));
        check("no prefilled observations", edt(p::tableText).split("尚未观察", -1).length - 1 == 4);
        shot(main, "01-start");

        // experiment: three plain observations
        for (int c = 0; c < 3; c++) {
            final int comb = c;
            edt(() -> {
                p.selectInputs(comb);
                p.observeButton().doClick();
                return null;
            });
            log("observe " + comb + ": " + edt(p::feedbackText));
        }
        edt(() -> {
            p.selectInputs(3);
            return null;
        });
        check("last combination needs prediction", !edt(() -> p.observeButton().isEnabled()));
        check("prediction possible", edt(() -> p.predictButton().isEnabled()));
        check("door not opened before observing 11", !edt(() -> p.door().getReading().isTrue()));
        edt(() -> {
            p.selectPrediction(true);
            p.predictButton().doClick();
            return null;
        });
        log("predict 11 -> 1: " + edt(p::feedbackText));
        log(edt(p::tableText));
        check("experiment complete", p.getSession().isComplete());
        check("observed AND", edt(p::tableText).contains("1 | 1 | 1 | 1"));
        check("old door open after 11", edt(() -> p.door().getReading().isTrue()));
        check("rebuild enabled", edt(() -> p.rebuildButton().isEnabled()));
        shot(main, "02-experiment-done");

        edt(() -> {
            // Story1: repair opens only after the player names the surviving explanation
            p.chooseExplanation("both");
            p.rebuildButton().doClick();
            return null;
        });
        check("polling after rebuild", edt(p::isPolling));
        check("door not running", edt(() -> p.door().getReading().getKind() == Reading.Kind.NOT_RUNNING));

        // template as is: nothing connected
        validate(p);
        log("template:\n" + edt(p::resultText));
        check("template rejected", !edt(p::lastResult).isPassed());
        check("template letter names the loose seal and never writes a closed door",
                edt(p::resultText).contains("许可印还没有接上讯线") && !edt(p::resultText).contains("副本：门不开"));

        // wrong OR circuit
        load(main, new File(fixtures, "or_wrong.dig"));
        validate(p);
        log("or_wrong:\n" + edt(p::resultText));
        check("OR sent back with the failing row on the slip", !edt(p::lastResult).isPassed()
                && edt(p::resultText).contains("岗位 A 未持钥印 · 岗位 B 持钥印 → 门规：门不开 · 副本：门开 ✗")
                && edt(() -> p.reply().letter().getMark()) == ResultLetter.Mark.RETURNED);
        shot(main, "03-or-rejected");

        // correct AND, running simulation drives the door
        load(main, new File(fixtures, "and_correct.dig"));
        main.start(null, false);
        waitFor("model running", () -> main.getModel() != null && main.getModel().isRunning());
        waitFor("door closed at A=0 B=0", () -> door(p).isValid() && !door(p).isTrue());
        setInput(main, "A", true);
        Thread.sleep(400);
        check("door closed at A=1 B=0", door(p).isValid() && !door(p).isTrue());
        setInput(main, "B", true);
        waitFor("door open at A=1 B=1", () -> door(p).isTrue());
        shot(main, "04-and-running-door-open");

        Model before = main.getModel();
        validate(p);
        log("and_correct:\n" + edt(p::resultText));
        check("AND passed", edt(p::lastResult).isPassed());
        check("AND letter carries the wax seal", edt(() -> p.reply().letter().getMark()) == ResultLetter.Mark.SEAL);
        check("same running model after validation", main.getModel() == before && before.isRunning());
        check("player inputs unchanged", input(main, "A") && input(main, "B"));
        Thread.sleep(400);
        check("door still open after validation", door(p).isTrue());
        check("stage shows completion", edt(p::stageText).contains("完成"));
        shot(main, "05-and-validated");

        main.stop();
        waitFor("door idle after stop", () -> door(p).getKind() == Reading.Kind.NOT_RUNNING);

        // A real editor modification must invalidate a completed result.
        edt(() -> {
            main.getCircuitComponent().modify(new ModifyDeleteWire(
                    main.getCircuitComponent().getCircuit().getWires().get(0)));
            return null;
        });
        check("edit clears passed result", edt(p::lastResult) == null);
        check("edit clears completion stage", !edt(p::stageText).contains("完成"));
        validate(p);
        check("broken circuit rejected after edit", !edt(p::lastResult).isPassed());

        // Start validating an AND snapshot, then replace it before done() can run on the EDT.
        load(main, new File(fixtures, "and_correct.dig"));
        edt(() -> {
            p.validateButton().doClick();
            main.getCircuitComponent().setCircuit(Circuit.loadCircuit(
                    new File(fixtures, "or_wrong.dig"), new ShapeFactory(main.getLibrary())));
            return null;
        });
        Thread.sleep(1500);
        check("replaced circuit discards old snapshot result", edt(p::lastResult) == null);
        check("replacement asks for revalidation", edt(p::resultText).contains("已经失效"));
        check("validation enabled after replacement", edt(() -> p.validateButton().isEnabled()));
        validate(p);
        check("replacement OR rejected", !edt(p::lastResult).isPassed());

        // a validation started right before leaving the level must not deliver its result;
        // windowClosed also calls dispose, but it is posted asynchronously, so dispose explicitly here
        edt(() -> {
            p.validateButton().doClick();
            p.dispose();
            main.dispose();
            return null;
        });
        Thread.sleep(1500);
        check("timer stopped after close", !edt(p::isPolling));
        check("pending validation discarded", edt(p::resultText).startsWith("检验中"));
        check("level template untouched", Arrays.equals(templateBefore, Files.readAllBytes(level.getTemplateFile().toPath())));
    }

    private static Reading door(AcademyPanel p) {
        try {
            return edt(() -> p.door().getReading());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void load(Main main, File f) throws Exception {
        edt(() -> {
            main.getCircuitComponent().setCircuit(Circuit.loadCircuit(f, new ShapeFactory(main.getLibrary())));
            return null;
        });
    }

    private static void setInput(Main main, String name, boolean v) throws Exception {
        edt(() -> {
            Model m = main.getModel();
            Signal s = ReferenceDevice.find(m.getInputs(), name);
            m.modify(() -> s.getValue().setBool(v));
            return null;
        });
    }

    private static boolean input(Main main, String name) throws Exception {
        return edt(() -> ReferenceDevice.find(main.getModel().getInputs(), name).getValue().getBool());
    }

    private static void validate(AcademyPanel p) throws Exception {
        edt(() -> {
            p.validateButton().doClick();
            return null;
        });
        waitFor("validation finished", () -> {
            try {
                return edt(() -> p.validateButton().isEnabled());
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static void waitFor(String what, BooleanSupplier cond) throws InterruptedException {
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) {
                log("ok   " + what);
                return;
            }
            Thread.sleep(100);
        }
        failures++;
        log("FAIL " + what + " (timeout)");
    }

    private static void check(String what, boolean ok) {
        if (!ok) failures++;
        log((ok ? "ok   " : "FAIL ") + what);
    }

    private static void shot(JFrame f, String name) throws Exception {
        BufferedImage img = edt(() -> {
            BufferedImage i = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_RGB);
            f.getRootPane().printAll(i.getGraphics());
            return i;
        });
        File file = new File(shots, name + ".png");
        ImageIO.write(img, "png", file);
        log("screenshot " + file.getName());
    }

    private static void log(String s) {
        log.println(s);
        System.out.println(new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    private static <T> T edt(Callable<T> c) throws Exception {
        Object[] r = new Object[1];
        Exception[] e = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                r[0] = c.call();
            } catch (Exception ex) {
                e[0] = ex;
            }
        });
        if (e[0] != null) throw e[0];
        @SuppressWarnings("unchecked") T t = (T) r[0];
        return t;
    }
}
