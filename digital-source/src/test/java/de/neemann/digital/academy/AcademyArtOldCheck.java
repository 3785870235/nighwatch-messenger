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
 * The first pose atlases ({@code -poses.png}), i.e. what the renderer anchors on today. It prints the
 * same two numbers the new sheets are measured with - the alpha&gt;16 subject box and the longest
 * vertical run through the middle of the cell - so the old and new padding can be compared directly
 * instead of guessed at.
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar de.neemann.digital.academy.AcademyArtOldCheck &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyArtOldCheck {
    private AcademyArtOldCheck() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File dir = new File(root, "assets/game/v2/characters");
        for (String suffix : new String[]{"-poses.png", "-poses-b.png"})
            for (String name : new String[]{"fu", "luo", "glimmer"})
                inspect(name, new File(dir, name + suffix));
    }

    private static void inspect(String name, File file) throws Exception {
        BufferedImage atlas = ImageIO.read(file);
        System.out.println(name + "-poses.png " + (atlas == null ? "UNREADABLE" : atlas.getWidth() + "x" + atlas.getHeight()
                + "  cells " + atlas.getWidth() / 2 + "x" + atlas.getHeight() / 2));
        if (atlas == null)
            return;
        int cw = atlas.getWidth() / 2;
        int ch = atlas.getHeight() / 2;
        for (int cell = 0; cell < 4; cell++) {
            int[] box = box(atlas, cell % 2 * cw, cell / 2 * ch, cw, ch);
            int[] middle = run(atlas, cell % 2 * cw + cw / 4, cell / 2 * ch, cw / 2, ch);
            if (box == null) {
                System.out.println("     cell " + cell + " EMPTY");
                continue;
            }
            double bx = (box[2] - box[0] + 1) / (double) cw;
            double by = (box[3] - box[1] + 1) / (double) ch;
            System.out.println("     cell " + cell
                    + " box x " + box[0] + "-" + box[2] + " y " + box[1] + "-" + box[3]
                    + "  (" + pct(bx) + " x " + pct(by) + " of cell)"
                    + "  middle run y " + middle[0] + "-" + middle[1] + " (" + pct((middle[1] - middle[0] + 1) / (double) ch) + ")"
                    + "  centre x " + (box[0] + box[2]) / 2);
        }
    }

    private static String pct(double v) {
        return Math.round(v * 1000) / 10.0 + "%";
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
