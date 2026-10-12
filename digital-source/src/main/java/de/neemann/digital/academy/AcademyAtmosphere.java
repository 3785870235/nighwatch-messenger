/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.draw.graphics.GraphicSwing;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.HierarchyEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Shared presentation preferences. No preference changes simulation or circuit files. */
final class AcademyAtmosphere {
    private static boolean motion = !Boolean.getBoolean("academy.reduceMotion");
    private static boolean magic = true;
    private static volatile boolean sound = !Boolean.getBoolean("academy.silent");
    private static final List<WeakReference<JComponent>> VIEWS = new ArrayList<>();

    private AcademyAtmosphere() {
    }

    static boolean motion() {
        return motion;
    }

    static void motion(boolean enabled) {
        motion = enabled;
        GraphicSwing.setWireMotion(enabled);
        refresh();
    }

    static boolean magic() {
        return magic;
    }

    static void magic(boolean enabled) {
        magic = enabled;
        GraphicSwing.setWireGlow(enabled);
        refresh();
    }

    static boolean sound() {
        return sound;
    }

    static void sound(boolean enabled) {
        sound = enabled;
        if (!enabled)
            AcademySound.stopAll();
        refresh();
    }

    static javax.swing.JButton settings(Runnable extraRefresh) {
        javax.swing.JButton b = AcademyTheme.button("氛围", AcademyTheme.Kind.QUIET);
        b.setToolTipText("动态效果、声音与标准电路外观");
        b.addActionListener(e -> {
            JPopupMenu p = new JPopupMenu();
            JCheckBoxMenuItem m = new JCheckBoxMenuItem("动态效果", motion);
            m.addActionListener(x -> motion(m.isSelected()));
            p.add(m);
            JCheckBoxMenuItem s = new JCheckBoxMenuItem("声音（轻音量）", sound);
            s.addActionListener(x -> sound(s.isSelected()));
            p.add(s);
            JCheckBoxMenuItem shapes = new JCheckBoxMenuItem("魔法元件（关闭看标准电路）", magic);
            shapes.addActionListener(x -> {
                magic(shapes.isSelected());
                if (extraRefresh != null)
                    extraRefresh.run();
            });
            p.add(shapes);
            for (java.awt.Component item : p.getComponents())
                if (item instanceof JMenuItem)
                    ((JMenuItem) item).setFont(AcademyTheme.body());
            p.show(b, 0, b.getHeight());
        });
        return b;
    }

    static Timer watch(JComponent c, Runnable tick) {
        AcademyPointer.install();
        VIEWS.add(new WeakReference<>(c));
        Timer timer = new Timer(16, e -> {
            if (c.isShowing()) {
                tick.run();
                c.repaint();
            }
        });
        timer.setCoalesce(true);
        c.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (c.isShowing())
                    timer.start();
                else
                    timer.stop();
            }
        });
        if (c.isShowing())
            timer.start();
        return timer;
    }

    private static void refresh() {
        VIEWS.removeIf(r -> r.get() == null);
        for (WeakReference<JComponent> r : VIEWS) {
            JComponent c = r.get();
            if (c != null)
                c.repaint();
        }
    }

    static void dust(Graphics2D g, int w, int h, double t, boolean warm) {
        if (!motion)
            return;
        for (int i = 0; i < 24; i++) {
            double x = ((i * 0.6180339 + Math.sin(t * 0.16 + i) * 0.025) % 1 + 1) % 1;
            double y = ((i * 0.4142135 - t * (0.012 + (i % 4) * 0.002)) % 1 + 1) % 1;
            int alpha = (int) (20 + 50 * (0.5 + 0.5 * Math.sin(t * 0.7 + i)));
            g.setColor(warm ? new Color(244, 206, 136, alpha) : new Color(160, 214, 230, alpha));
            int size = i % 5 == 0 ? 3 : 2;
            g.fillOval((int) (x * w), (int) (y * h), size, size);
        }
    }

    static void lamp(Graphics2D g, float x, float y, float radius, float strength, double t) {
        float a = strength * (motion ? (float) (0.83 + 0.17 * Math.sin(t * 1.8)) : 0.9f);
        g.setPaint(new RadialGradientPaint(x, y, Math.max(1, radius), new float[]{0, 0.25f, 1},
                new Color[]{new Color(255, 207, 119, (int) (a * 95)), new Color(240, 177, 75, (int) (a * 35)),
                        new Color(240, 177, 75, 0)}));
        g.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
    }
}
