/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.gui.DigitalUncaughtExceptionHandler;
import de.neemann.digital.gui.Main;
import de.neemann.digital.gui.components.ElementHelpDialog;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Starts Digital with the Midnight Logic Academy level panel.
 * Usage: AcademyMain [level.properties] ; the player's circuit is kept in work/&lt;level id&gt;.dig,
 * created from the level template on first start. Level resources are never written.
 */
public final class AcademyMain {

    private AcademyMain() {
    }

    /**
     * Starts the level mode.
     *
     * @param args optional path of the level.properties file
     * @throws IOException IOException
     */
    public static void main(String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(new DigitalUncaughtExceptionHandler());
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception e) {
            e.printStackTrace();
        }
        URL.setURLStreamHandlerFactory(ElementHelpDialog.createURLStreamHandlerFactory());

        File root = new File(System.getProperty("academy.root", ".")).getAbsoluteFile();
        File levelFile = new File(args.length > 0 ? args[0] : "levels/03_archive_gate/level.properties");
        File work = new File(System.getProperty("academy.work", "work")).getAbsoluteFile();
        SwingUtilities.invokeLater(() -> new AcademyHome(levelFile, work, AcademySavePolicy.forProject(root)).setVisible(true));
    }

    /**
     * Gives an academy window its size: 1280x800 if the screen allows, at least 1100x720.
     *
     * @param w the window
     */
    static void fitWindow(Window w) {
        w.setMinimumSize(new Dimension(1100, 720));
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        w.setSize(Math.max(1100, Math.min(1280, screen.width)), Math.max(720, Math.min(800, screen.height)));
        w.setLocationRelativeTo(null);
    }

    /**
     * Returns the player's destination without creating an automatic save.
     *
     * @param level   the level
     * @param workDir the directory holding the player's circuits
     * @return the work file
     * @throws IOException IOException
     */
    public static File prepareWorkFile(LevelDefinition level, File workDir) throws IOException {
        File work = new File(workDir, level.getId() + ".dig").getAbsoluteFile();
        return work;
    }

    /**
     * Creates the Digital main window with the level panel. Must be called on the EDT.
     * The window is not made visible.
     *
     * @param level the level
     * @param work  the player's circuit file
     * @return the level panel; use {@link AcademyPanel#getMain()} to get the window
     * @throws IOException if the level could not be loaded
     */
    public static AcademyPanel open(LevelDefinition level, File work) throws IOException {
        return open(level, work, null, null);
    }

    /**
     * Creates the Digital main window with the level panel. Must be called on the EDT.
     *
     * @param level    the level
     * @param work     the player's circuit file
     * @param progress the progress store, maybe null
     * @param policy   the save policy, maybe null
     * @return the level panel
     * @throws IOException if the level could not be loaded
     */
    public static AcademyPanel open(LevelDefinition level, File work, AcademyProgress progress, Main.FileSavePolicy policy) throws IOException {
        AcademyTheme.applyCanvasColors();
        // an own library keeps Digital from adding the work file to the user's file history
        ElementLibrary library = new ElementLibrary();
        Main.MainBuilder builder = new Main.MainBuilder()
                .setLibrary(library)
                .setShapeDecorator(MagicShape::decorateForLevel)
                .setFileToOpen(work)
                .setSavePolicy(policy);
        if (!work.exists()) {
            de.neemann.digital.draw.shapes.ShapeFactory factory = new de.neemann.digital.draw.shapes.ShapeFactory(library);
            factory.setDecorator(MagicShape::decorateForLevel);
            builder.setCircuit(de.neemann.digital.draw.elements.Circuit.loadCircuit(level.getTemplateFile(), factory));
        }
        Main main = builder.build();
        AcademyPanel panel;
        try {
            panel = new AcademyPanel(main, level, progress);
        } catch (IOException | RuntimeException e) {
            main.dispose();
            throw e;
        }
        main.setContentPane(panel);
        fitWindow(main);
        main.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                panel.dispose();
            }
        });
        return panel;
    }
}
