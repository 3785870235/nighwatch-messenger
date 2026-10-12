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
 * The second pose atlases ({@code -poses-b.png}) shipped for the three characters. This checks the
 * properties the slicer depends on and reports the per-cell subject box, which is what the renderer
 * has to anchor on: the three new sheets have different padding than the old ones, so scaling by cell
 * height alone would make the figure jump when the pose changes.
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar de.neemann.digital.academy.AcademyArtBCheck &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyArtBCheck {
    private static int checks;

    private AcademyArtBCheck() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File dir = new File(root, "assets/game/v2/characters");
        for (String name : new String[]{"fu", "luo", "glimmer"})
            inspect(name, new File(dir, name + "-poses-b.png"));
        System.out.println("RESULT: " + checks + " CHECKS PASSED");
    }

    private static void inspect(String name, File file) throws Exception {
        BufferedImage atlas = ImageIO.read(file);
        check(name + ": the atlas exists and is readable", atlas != null);
        if (atlas == null)
            return;
        int w = atlas.getWidth();
        int h = atlas.getHeight();
        check(name + ": both halves divide evenly (" + w + "x" + h + ")", w % 2 == 0 && h % 2 == 0);
        int cw = w / 2;
        int ch = h / 2;
        // A painted subject crossing the divide gets cut in half when the cell is sliced out, and the
        // slicer never reports it. The centre lines must be transparent within a small tolerance: the
        // generator leaves isolated alpha=1 pixels behind.
        check(name + ": vertical centre line is clear", lineMax(atlas, cw - 1, 0, ch * 2, true) <= 8);
        check(name + ": horizontal centre line is clear", lineMax(atlas, ch - 1, 0, cw * 2, false) <= 8);
        for (int cell = 0; cell < 4; cell++) {
            int[] box = subjectBox(atlas, cell % 2 * cw, cell / 2 * ch, cw, ch);
            check(name + " cell " + cell + ": has a subject", box != null);
            if (box == null)
                continue;
            System.out.println("     cell " + cell + " subject x " + box[0] + "-" + box[2]
                    + " y " + box[1] + "-" + box[3] + "  (cell " + cw + "x" + ch + ")");
            // The renderer keeps the visible figure at a fixed height and puts its feet on a fixed
            // line, so the whole subject has to sit inside its own cell with room to spare.
            check(name + " cell " + cell + ": subject inside its cell",
                    box[0] > 0 && box[1] > 0 && box[2] < cw - 1 && box[3] < ch - 1);
        }
    }

    /** The box of pixels with a meaningful alpha, or null if the cell is empty. */
    private static int[] subjectBox(BufferedImage image, int ox, int oy, int w, int h) {
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

    private static int lineMax(BufferedImage image, int index, int from, int to, boolean vertical) {
        int max = 0;
        for (int i = from; i < to; i++) {
            int alpha = vertical ? image.getRGB(index, i) >>> 24 : image.getRGB(i, index) >>> 24;
            max = Math.max(max, alpha);
        }
        return max;
    }

    private static void check(String name, boolean ok) {
        checks++;
        System.out.println((ok ? "ok   " : "FAIL ") + name);
        if (!ok)
            throw new AssertionError(name);
    }
}
