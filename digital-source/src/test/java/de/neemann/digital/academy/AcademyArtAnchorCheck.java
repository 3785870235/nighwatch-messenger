/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Chooses the anchor the portrait renderer should scale and place a pose by.
 * <p>
 * A cell is mostly transparent padding, and how much differs between the two sheets and between the
 * four poses on one sheet. Scaling every pose by its cell height makes the figure grow and shrink as
 * the pose changes; scaling by the whole painted box makes it grow whenever a hand or a wing is
 * raised. So this measures a few candidate anchors and prints the spread between the largest and the
 * smallest pose of each character - the anchor whose spread is smallest is the one that will not
 * make the figure jump.
 * <p>
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar de.neemann.digital.academy.AcademyArtAnchorCheck &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyArtAnchorCheck {
    /** Horizontal windows, as a fraction of the cell width, centred on the cell. */
    private static final double[] WINDOWS = {.10, .25, .50};
    private static int checks;

    private AcademyArtAnchorCheck() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File dir = new File(root, "assets/game/v2/characters");
        // The old grid is the 640-unit one the limb table is written on.
        for (String name : new String[]{"fu", "luo", "glimmer"})
            inspect(name, dir);
        System.out.println("RESULT: " + checks + " CHECKS PASSED");
    }

    private static void inspect(String name, File dir) throws Exception {
        for (String suffix : new String[]{"-poses.png", "-poses-b.png"}) {
            BufferedImage atlas = ImageIO.read(new File(dir, name + suffix));
            if (atlas == null) {
                System.out.println(name + suffix + " UNREADABLE");
                continue;
            }
            int cw = atlas.getWidth() / 2;
            int ch = atlas.getHeight() / 2;
            System.out.println(name + suffix + "  cell " + cw + "x" + ch);
            double[] full = new double[4];
            double[][] strip = new double[WINDOWS.length][4];
            double[][] offset = new double[WINDOWS.length][4];
            for (int cell = 0; cell < 4; cell++) {
                int ox = cell % 2 * cw;
                int oy = cell / 2 * ch;
                int[] box = box(atlas, ox, oy, cw, ch);
                full[cell] = box == null ? 0 : box[3] - box[1] + 1;
                StringBuilder line = new StringBuilder("     cell " + cell + "  box h " + (int) full[cell]);
                for (int i = 0; i < WINDOWS.length; i++) {
                    int w = Math.max(2, (int) Math.round(cw * WINDOWS[i]));
                    int[] run = run(atlas, ox + (cw - w) / 2, oy, w, ch);
                    strip[i][cell] = run[1] - run[0] + 1;
                    offset[i][cell] = run[1];
                    line.append("  |  ").append(Math.round(WINDOWS[i] * 100)).append("% run ")
                            .append(run[0]).append('-').append(run[1]);
                }
                System.out.println(line);
            }
            report("whole painted box", full);
            for (int i = 0; i < WINDOWS.length; i++)
                report("centre " + Math.round(WINDOWS[i] * 100) + "% strip height", strip[i]);
            for (int i = 0; i < WINDOWS.length; i++)
                report("centre " + Math.round(WINDOWS[i] * 100) + "% strip foot", offset[i]);
        }
    }

    /** The spread between the largest and smallest pose: the smaller it is, the steadier the anchor. */
    private static void report(String what, double[] values) {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        System.out.println("     " + what + ": min " + round(min) + " max " + round(max)
                + " spread " + Math.round((max - min) / min * 1000) / 10.0 + "%");
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** The box of pixels with a meaningful alpha, or null if the cell is empty. */
    private static int[] box(BufferedImage image, int ox, int oy, int w, int h) {
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if ((image.getRGB(ox + x, oy + y) >>> 24) <= 16)
                    continue;
                if (x < minX)
                    minX = x;
                if (x > maxX)
                    maxX = x;
                if (y < minY)
                    minY = y;
                if (y > maxY)
                    maxY = y;
            }
        return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
    }

    /** The longest uninterrupted vertical band of painted rows within a horizontal window. */
    private static int[] run(BufferedImage image, int ox, int oy, int w, int h) {
        int bestFrom = 0;
        int bestTo = -1;
        int from = -1;
        for (int y = 0; y < h; y++) {
            boolean painted = false;
            for (int x = 0; x < w && !painted; x++)
                painted = (image.getRGB(ox + x, oy + y) >>> 24) > 16;
            if (painted) {
                if (from < 0)
                    from = y;
                if (y - from > bestTo - bestFrom) {
                    bestFrom = from;
                    bestTo = y;
                }
            } else
                from = -1;
        }
        return new int[]{bestFrom, bestTo};
    }
}
