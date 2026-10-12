/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.Pins;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.elements.Wire;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.draw.graphics.Vector;

import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;

/**
 * The chapter chain on the home screen. Two things are checked here: a chapter passed in a saved
 * progress file opens the next one at startup, and a chapter passed in a running session opens the
 * next one as soon as the level window closes — without saving and without a restart. The last
 * chapter offers no sixth chapter: its button leads back home.
 * Run: java -cp target/test-classes;target/Digital.jar de.neemann.digital.academy.AcademyUnlockCheck &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyUnlockCheck {
    private static int checks;
    private static File shots;

    private AcademyUnlockCheck() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("academy.silent", "true");
        System.setProperty("academy.skipIntro", "true");
        System.setProperty("academy.skipTutorial", "true");
        System.setProperty("academy.skipTransitions", "true");
        javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
        File root = new File(args[0]);
        System.setProperty("academy.root", root.getAbsolutePath());
        if (args.length > 1) {
            shots = new File(args[1]);
            shots.mkdirs();
        }
        // Outside the project: the save policy protects everything under digital-source, target included,
        // and the check has to be able to save a chapter the way a player does.
        File dir = new File(System.getProperty("java.io.tmpdir"), "academy-unlock-check");
        delete(dir);
        dir.mkdirs();
        int result = 0;
        try {
            run(root, dir);
            System.out.println("RESULT: " + checks + " CHECKS PASSED");
        } catch (Throwable e) {
            e.printStackTrace();
            result = 1;
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (java.awt.Window w : java.awt.Window.getWindows())
                    w.dispose();
            });
        }
        System.exit(result);
    }

    private static void run(File root, File dir) throws Exception {
        File levelFile = new File(root, "levels/01_report_light/level.properties");
        File secondFile = new File(root, "levels/02_reminder_light/level.properties");
        File thirdFile = new File(root, "levels/03_archive_gate/level.properties");
        File fourthFile = new File(root, "levels/04_registry_desk/level.properties");
        File fifthFile = new File(root, "levels/05_star_lamps/level.properties");

        // --- a fresh player sees only chapter one ---
        File work = new File(dir, "work");
        AcademyHome home = edt(() -> new AcademyHome(levelFile, work, AcademySavePolicy.forProject(root)));
        check("fresh progress opens only chapter one", "[true, false, false, false, false]".equals(edt(home::chapterStates)));

        // --- pass chapter one, save it the way a player does, and close the window ---
        solve(levelFile, new File(work, "01_report_light.dig"));
        AcademyPanel panel = edt(() -> home.openLevel("01_report_light"));
        if (panel == null)
            throw new AssertionError("chapter one could not be opened from the home screen");
        edt(() -> { panel.getMain().setVisible(true); return null; });
        Thread.sleep(300);
        edt(() -> { panel.validateButton().doClick(); return null; });
        for (int i = 0; i < 100 && edt(panel::lastResult) == null; i++)
            Thread.sleep(100);
        check("the chapter really passes", edt(() -> panel.lastResult() != null && panel.lastResult().isPassed()));
        check("passing alone writes no progress file", !progressFile(work, levelFile).isFile());

        // --- closing the level window rebuilds the cards, without a save and without a restart ---
        edt(() -> { panel.getMain().dispose(); return null; });
        Thread.sleep(400);
        check("closing the level window opens chapter two", "[true, true, false, false, false]".equals(edt(home::chapterStates)));
        check("chapter three stays closed", !progressFile(work, thirdFile).isFile());
        check("closing still wrote no progress file", !progressFile(work, levelFile).isFile());

        // --- the player saves explicitly; the pass then survives a restart ---
        edt(() -> { panel.getMain().saveChanges(); return null; });
        Thread.sleep(500);
        check("an explicit save writes the progress file", progressFile(work, levelFile).isFile());
        edt(() -> { panel.getMain().dispose(); return null; });
        Thread.sleep(400);
        AcademyHome restarted = edt(() -> new AcademyHome(levelFile, work, AcademySavePolicy.forProject(root)));
        check("the saved pass opens chapter two after a restart", "[true, true, false, false, false]".equals(edt(restarted::chapterStates)));

        // --- the card titles come from the chapter files, not from a copy in the source ---
        check("card titles match the chapter files", edt(restarted::cardTitles).equals(
                LevelDefinition.load(levelFile).getTitle()
                        + "\n" + LevelDefinition.load(secondFile).getTitle()
                        + "\n" + LevelDefinition.load(thirdFile).getTitle()
                        + "\n" + LevelDefinition.load(fourthFile).getTitle()
                        + "\n" + LevelDefinition.load(fifthFile).getTitle()));

        // --- a pass offers the next chapter on the result letter; it leaves through the save question ---
        AcademyPanel again = edt(() -> restarted.openLevel("01_report_light"));
        if (again == null)
            throw new AssertionError("chapter one could not be opened again");
        edt(() -> { again.getMain().setVisible(true); return null; });
        Thread.sleep(300);
        check("a pass from an earlier session offers no next chapter", !edt(() -> again.nextChapterButton().isVisible()));
        edt(() -> { again.validateButton().doClick(); return null; });
        for (int i = 0; i < 100 && edt(again::lastResult) == null; i++)
            Thread.sleep(100);
        check("the current pass offers the next chapter", edt(() -> again.nextChapterButton().isVisible()
                && "下一章：信使站 →".equals(again.nextChapterButton().getText())));
        shot(again.getMain(), "next-chapter-result");
        // the pass made the session dirty: save it, otherwise leaving would ask (a modal question)
        edt(() -> { again.getMain().saveChanges(); return null; });
        Thread.sleep(500);
        edt(() -> { again.nextChapterButton().doClick(); return null; });
        check("the unread clue is read on the way", "story".equals(edt(again::page))
                && "线索".equals(edt(() -> again.story().heading())));
        edt(() -> { again.story().skipButton().doClick(); return null; });
        Thread.sleep(800);
        check("the passed chapter window is closed", !edt(() -> again.getMain().isDisplayable()));
        check("the next chapter opens directly", edt(() -> restarted.activeLevel() != null
                && "02_reminder_light".equals(restarted.activeLevel().levelId())
                && restarted.activeLevel().getMain().isDisplayable()));
        check("the home window stays hidden", !edt(restarted::isVisible));
        edt(() -> { restarted.setVisible(true); restarted.showLevels(); return null; });
        Thread.sleep(300);
        shot(restarted, "next-chapter-levels");
        edt(() -> { restarted.setVisible(false); return null; });
        check("going on wrote no progress for chapter two", !progressFile(work, secondFile).isFile());
        check("chapter two offers chapter three only after its own pass",
                !edt(() -> restarted.activeLevel().nextChapterButton().isVisible()));
        AcademyPanel second = edt(restarted::activeLevel);
        edt(() -> { second.getMain().dispose(); return null; });
        Thread.sleep(400);

        // --- chapter four hands over to chapter five; a pass in the session opens its card ---
        Files.copy(new File(root, "levels/04_registry_desk/fixtures/xor_correct.dig").toPath(),
                new File(work, "04_registry_desk.dig").toPath());
        AcademyPanel fourth = edt(() -> restarted.openLevel("04_registry_desk"));
        if (fourth == null)
            throw new AssertionError("chapter four could not be opened");
        validate(fourth);
        check("chapter four offers chapter five", edt(() -> fourth.lastResult() != null && fourth.lastResult().isPassed()
                && fourth.nextChapterButton().isVisible() && "下一章：星灯归位 →".equals(fourth.nextChapterButton().getText())));
        edt(() -> { fourth.getMain().dispose(); return null; });
        Thread.sleep(400);
        check("passing chapter four opens chapter five", "[true, true, false, false, true]".equals(edt(restarted::chapterStates)));

        // --- the last chapter ends the night: its clue is read, then the player is back home ---
        Files.copy(new File(root, "levels/05_star_lamps/fixtures/binary_correct.dig").toPath(),
                new File(work, "05_star_lamps.dig").toPath());
        AcademyPanel last = edt(() -> restarted.openLevel("05_star_lamps"));
        if (last == null)
            throw new AssertionError("chapter five could not be opened");
        validate(last);
        check("the last chapter offers the way home, not a sixth chapter", edt(() -> last.lastResult() != null
                && last.lastResult().isPassed() && last.nextChapterButton().isVisible()
                && "天亮了 · 回到学院 →".equals(last.nextChapterButton().getText())));
        shot(last.getMain(), "last-chapter-result");
        edt(() -> { last.getMain().saveChanges(); return null; });
        Thread.sleep(500);
        edt(() -> { last.nextChapterButton().doClick(); return null; });
        check("the last clue is read on the way home", "story".equals(edt(last::page))
                && "线索".equals(edt(() -> last.story().heading())));
        edt(() -> { last.story().skipButton().doClick(); return null; });
        Thread.sleep(800);
        check("the last chapter window is closed", !edt(() -> last.getMain().isDisplayable()));
        check("no chapter opens after the last one", edt(() -> restarted.activeLevel() == null));
        check("the home window is shown again", edt(restarted::isVisible));
        shot(restarted, "last-chapter-home");

        edt(() -> { home.dispose(); return null; });
        edt(() -> { restarted.dispose(); return null; });
    }

    private static void validate(AcademyPanel panel) throws Exception {
        edt(() -> { panel.getMain().setVisible(true); return null; });
        Thread.sleep(300);
        edt(() -> { panel.validateButton().doClick(); return null; });
        for (int i = 0; i < 100 && edt(panel::lastResult) == null; i++)
            Thread.sleep(100);
        Thread.sleep(300);
    }

    private static File progressFile(File workDir, File levelFile) throws Exception {
        LevelDefinition level = LevelDefinition.load(levelFile);
        return new AcademyProgress(new File(workDir, "progress"), level).getFile();
    }

    /**
     * Writes the solved circuit of a chapter to the player's work file, the way the editor would
     * after the player drew the missing wire and saved. Chapter one is A -> LIGHT, so the solution
     * is one wire from the In pin to the Out pin.
     */
    private static void solve(File levelFile, File work) throws Exception {
        LevelDefinition level = LevelDefinition.load(levelFile);
        Circuit circuit = Circuit.loadCircuit(level.getTemplateFile(), factory());
        VisualElement in = element(circuit, "In");
        VisualElement out = element(circuit, "Out");
        Vector from = pin(in, 0);
        Vector to = pin(out, 0);
        if (from == null || to == null)
            throw new AssertionError("the template of " + level.getId() + " has no usable pins");
        circuit.add(new Wire(from, to));
        work.getParentFile().mkdirs();
        circuit.save(work);
    }

    private static ShapeFactory factory() {
        ElementLibrary library = new ElementLibrary();
        ShapeFactory f = new ShapeFactory(library);
        f.setDecorator(MagicShape::decorateForLevel);
        return f;
    }

    private static VisualElement element(Circuit circuit, String name) {
        for (VisualElement v : circuit.getElements())
            if (name.equals(v.getElementName()))
                return v;
        throw new AssertionError("the template has no " + name);
    }

    /** The absolute position of the pin at the given index, or null if the element has none. */
    private static Vector pin(VisualElement v, int index) {
        Pins pins = v.getPins();
        if (pins == null || index >= pins.size())
            return null;
        return pins.get(index).getPos();
    }

    private static void shot(javax.swing.JFrame frame, String name) throws Exception {
        if (shots == null)
            return;
        java.awt.image.BufferedImage image = edt(() -> {
            javax.swing.JComponent c = frame.getRootPane();
            java.awt.image.BufferedImage i = new java.awt.image.BufferedImage(c.getWidth(), c.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = i.createGraphics();
            c.printAll(g);
            g.dispose();
            return i;
        });
        javax.imageio.ImageIO.write(image, "png", new File(shots, name + ".png"));
    }

    private static void check(String name, boolean ok) {
        checks++;
        System.out.println((ok ? "ok   " : "FAIL ") + name);
        if (!ok)
            throw new AssertionError(name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T edt(Callable<T> callable) throws Exception {
        final Object[] result = new Object[1];
        final Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                result[0] = callable.call();
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null)
            throw failure[0];
        return (T) result[0];
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                delete(child);
        file.delete();
    }
}
