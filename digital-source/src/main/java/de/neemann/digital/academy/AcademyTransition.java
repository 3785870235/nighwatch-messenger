/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

/** Short scene dissolve, using the actual previous screen; simulation is never copied. */
final class AcademyTransition extends JComponent {
    private final JRootPane root;
    private final Component previousGlass;
    private final BufferedImage previous;
    private final Timer timer;
    private final long started = System.nanoTime();
    private final KeyEventDispatcher blocker;

    private AcademyTransition(JRootPane root, BufferedImage previous, Component glass) {
        this.root = root;
        this.previous = previous;
        this.previousGlass = glass;
        blocker = event -> root.getGlassPane() == this && isVisible()
                && SwingUtilities.isDescendingFrom(event.getComponent(), root);
        setFocusable(true);
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseMotionAdapter() { });
        timer = new Timer(16, e -> {
            if (!isShowing() || elapsed() >= 1)
                finish();
            else
                repaint();
        });
    }

    static void change(JComponent owner, Runnable change) {
        JRootPane root = SwingUtilities.getRootPane(owner);
        if (root == null || !root.isShowing() || !AcademyAtmosphere.motion()
                || Boolean.getBoolean("academy.skipTransitions")) {
            change.run();
            return;
        }
        // Reject repeated physical clicks during the dissolve rather than queue stale navigation.
        if (root.getGlassPane() instanceof AcademyTransition && root.getGlassPane().isVisible())
            return;
        Component glass = root.getGlassPane();
        if (glass.isVisible()) {
            change.run();
            return;
        }
        BufferedImage old = capture(root);
        change.run();
        root.validate();
        reveal(root, old, glass);
    }

    static void enter(JRootPane root) {
        if (!root.isShowing() || !AcademyAtmosphere.motion() || Boolean.getBoolean("academy.skipTransitions")
                || root.getGlassPane().isVisible())
            return;
        BufferedImage dark = new BufferedImage(Math.max(1, root.getWidth()), Math.max(1, root.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dark.createGraphics();
        g.setColor(AcademyTheme.NIGHT);
        g.fillRect(0, 0, dark.getWidth(), dark.getHeight());
        g.dispose();
        reveal(root, dark, root.getGlassPane());
    }

    private static void reveal(JRootPane root, BufferedImage old, Component glass) {
        AcademyTransition layer = new AcademyTransition(root, old, glass);
        root.setGlassPane(layer);
        layer.setVisible(true);
        layer.requestFocusInWindow();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(layer.blocker);
        layer.timer.start();
        AcademySound.cue("transition");
    }

    private static BufferedImage capture(JRootPane root) {
        BufferedImage img = new BufferedImage(Math.max(1, root.getWidth()), Math.max(1, root.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        root.printAll(g);
        g.dispose();
        return img;
    }

    private double elapsed() {
        return Math.min(1, (System.nanoTime() - started) / 900000000.0);
    }

    private void finish() {
        timer.stop();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(blocker);
        setVisible(false);
        if (root.getGlassPane() == this) {
            root.setGlassPane(previousGlass);
            java.awt.Window window = SwingUtilities.getWindowAncestor(root);
            if (window instanceof de.neemann.digital.gui.Main
                    && ((de.neemann.digital.gui.Main) window).getCircuitComponent().isShowing())
                ((de.neemann.digital.gui.Main) window).getCircuitComponent().requestFocusInWindow();
            else
                root.getContentPane().requestFocusInWindow();
        }
    }

    @Override
    public void removeNotify() {
        timer.stop();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(blocker);
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double t = elapsed();
        float oldAlpha = (float) Math.pow(1 - t, 1.4);
        g.setComposite(AlphaComposite.SrcOver.derive(oldAlpha));
        g.drawImage(previous, 0, 0, getWidth(), getHeight(), null);
        float veil = (float) (Math.sin(Math.PI * t) * 0.65);
        g.setComposite(AlphaComposite.SrcOver.derive(veil));
        g.setColor(AcademyTheme.NIGHT);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(AcademyTheme.GOLD_HI);
        g.setStroke(new BasicStroke(1.2f));
        int x = getWidth() / 2;
        int y = getHeight() / 2;
        int radius = (int) (22 + 12 * t);
        g.rotate(t * 0.9, x, y);
        g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
        g.drawLine(x - radius - 10, y, x + radius + 10, y);
        g.drawLine(x, y - radius - 10, x, y + radius + 10);
        g.dispose();
    }
}
