/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/** Tracks mouse events inside this application's own JVM, retaining no windows or components. */
final class AcademyPointer {
    private static boolean installed;
    private static Point target;
    private static double x;
    private static double y;
    private static long last;

    private AcademyPointer() {
    }

    static void install() {
        if (installed)
            return;
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseEvent) {
                MouseEvent mouse = (MouseEvent) event;
                if ((mouse.getID() == MouseEvent.MOUSE_MOVED || mouse.getID() == MouseEvent.MOUSE_DRAGGED)
                        && mouse.getComponent().isShowing()) {
                    Point point = mouse.getPoint();
                    SwingUtilities.convertPointToScreen(point, mouse.getComponent());
                    if (target == null) {
                        x = point.x;
                        y = point.y;
                        last = System.nanoTime();
                    }
                    target = point;
                }
            }
        }, AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    static Point2D local(JComponent view) {
        if (target == null || !view.isShowing())
            return null;
        long now = System.nanoTime();
        double alpha = AcademyAtmosphere.motion() ? 1 - Math.exp(-(now - last) / 110000000.0) : 1;
        x += (target.x - x) * alpha;
        y += (target.y - y) * alpha;
        last = now;
        Point origin = view.getLocationOnScreen();
        return new Point2D.Double(x - origin.x, y - origin.y);
    }
}
