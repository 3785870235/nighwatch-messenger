/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Time-driven environmental layers, placed in the artwork's own coordinate system. */
final class AcademyLivingScene {
    private AcademyLivingScene() {
    }

    static void alcove(Graphics2D parent, int w, int h, double seconds) {
        BufferedImage image = AcademyArt.get("v2/scenes/moonlit-alcove.png");
        if (image == null)
            return;
        double scale = Math.max(w / (double) image.getWidth(), h / (double) image.getHeight());
        Graphics2D g = (Graphics2D) parent.create();
        g.translate((w - image.getWidth() * scale) / 2, (h - image.getHeight() * scale) / 2);
        g.scale(scale, scale);
        g.drawImage(image, 0, 0, null);
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        Graphics2D sky = (Graphics2D) g.create();
        Path2D opening = new Path2D.Double();
        opening.moveTo(166, 502);
        opening.lineTo(166, 157);
        opening.curveTo(166, 38, 286, -67, 372, -83);
        opening.curveTo(492, -48, 585, 49, 585, 157);
        opening.lineTo(585, 502);
        opening.closePath();
        sky.clip(opening);
        for (int i = 0; i < 7; i++) {
            double x = 20 + ((t * (7 + i % 3) + i * 139) % 930) - 180;
            double y = 80 + i * 54 + Math.sin(t * .13 + i) * 6;
            sky.setPaint(new RadialGradientPaint((float) x, (float) y, 170,
                    new float[]{0, .45f, 1}, new Color[]{new Color(140, 169, 206, 40),
                        new Color(102, 132, 179, 22), new Color(70, 105, 155, 0)}));
            sky.fillOval((int) x - 190, (int) y - 26, 390, 74);
        }
        sky.dispose();
        candle(g, 1349, 400, t);
        candle(g, 1403, 385, t + 1.7);
        candle(g, 1469, 399, t + 3.1);
        // A slowly drifting reflection accompanies the flame, not a change to game state.
        g.setPaint(new RadialGradientPaint(1320, 644, 230, new float[]{0, 1},
                new Color[]{new Color(226, 157, 62, (int) (12 + 5 * Math.sin(t * 1.7))), new Color(226, 157, 62, 0)}));
        g.fillOval(1090, 586, 460, 145);
        g.dispose();
        AcademyAtmosphere.dust(parent, w, h, t, false);
    }

    private static void candle(Graphics2D g, double x, double y, double t) {
        double sway = Math.sin(t * 3.4) * 2.2 + Math.sin(t * 7.1) * .8;
        double height = 24 + Math.sin(t * 4.7) * 4;
        AcademyAtmosphere.lamp(g, (float) x, (float) (y - 10), 95, .9f, t * 2);
        Path2D flame = new Path2D.Double();
        flame.moveTo(x, y);
        flame.curveTo(x - 10, y - 7, x - 4 + sway, y - height * .66, x + sway, y - height);
        flame.curveTo(x + 3 + sway, y - height * .5, x + 8, y - 6, x, y);
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(255, 157, 49),
                (float) x, (float) (y - height), new Color(255, 239, 170)));
        g.fill(flame);
        g.setColor(new Color(255, 247, 219));
        g.fillOval((int) x - 2, (int) y - 10, 4, 10);
    }

    static void exterior(Graphics2D g, int w, int h, double seconds) {
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        for (int i = 0; i < 6; i++) {
            double speed = 5 + i * 0.8;
            double x = ((t * speed + i * w * .4) % (w * 1.8)) - w * .4;
            float alpha = (float) (0.08 + 0.05 * Math.sin(t * 0.3 + i));
            g.setPaint(new RadialGradientPaint((float) x, h * .18f, Math.max(1, w * .45f),
                    new float[]{0, 0.7f, 1},
                    new Color[]{
                        new Color(133, 161, 189, (int) (alpha * 80)),
                        new Color(133, 161, 189, (int) (alpha * 40)),
                        new Color(133, 161, 189, 0)
                    }));
            g.fillOval((int) (x - w * .45), (int) (h * .02), (int) (w * .9), (int) (h * .35));
        }
        for (int i = 0; i < 18; i++) {
            double x = (i * 91 + 31) % w;
            double y = (i * 67 + 19) % (h * 0.45) + h * 0.03;
            double phase = t * (1.1 + i * 0.13) + i * 1.7;
            int alpha = (int) (140 + 115 * Math.max(0, Math.sin(phase)));
            int size = i % 3 == 0 ? 3 : 2;
            g.setColor(new Color(255, 248, 220, Math.min(255, alpha)));
            g.fillOval((int) x - size/2, (int) y - size/2, size, size);
            if (alpha > 200) {
                g.setColor(new Color(255, 248, 220, (alpha - 200) * 2));
                g.fillOval((int) x - size, (int) y - size, size * 2, size * 2);
            }
        }
        for (int i = 0; i < 3; i++) {
            double x = w * (0.15 + i * 0.35);
            double y = h * 0.45;
            AcademyAtmosphere.lamp(g, (float) x, (float) y, w * 0.12f, 0.25f + 0.15f * (float) Math.sin(t * (0.6 + i * 0.25) + i * 2.1), t);
        }
        AcademyAtmosphere.dust(g, w, h, t, true);
    }
}
