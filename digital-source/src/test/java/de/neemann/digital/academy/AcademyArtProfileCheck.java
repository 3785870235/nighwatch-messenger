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
 * The alpha profile of the pose atlases.
 * <p>
 * Luo's first sheet measures as a figure that is 98 percent of the cell tall but only half the cell
 * wide, which is not what a painted scholar looks like - it is what a thin faint strand looks like.
 * The generator leaves isolated near-transparent residue behind, so a box computed at the usual
 * alpha&gt;16 threshold can be reporting residue as if it were ink. This prints how the alpha is
 * distributed vertically and how much of it is genuinely near-opaque, so the threshold used for
 * renderer measurements is chosen from evidence instead of habit.
 * <p>
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar de.neemann.digital.academy.AcademyArtProfileCheck &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyArtProfileCheck {
    private AcademyArtProfileCheck() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File dir = new File(root, "assets/game/v2/characters");
        for (String suffix : new String[]{"-poses.png", "-poses-b.png"})
            for (String name : new String[]{"luo", "fu"})
                inspect(name + suffix, ImageIO.read(new File(dir, name + suffix)));
    }

    private static void inspect(String label, BufferedImage atlas) {
        if (atlas == null) {
            System.out.println(label + " UNREADABLE");
            return;
        }
        int cw = atlas.getWidth() / 2;
        int ch = atlas.getHeight() / 2;
        System.out.println(label + "  cell " + cw + "x" + ch);
        for (int cell = 0; cell < 4; cell++) {
            int ox = cell % 2 * cw;
            int oy = cell / 2 * ch;
            int[] strong = box(atlas, ox, oy, cw, ch, 128);
            int[] soft = box(atlas, ox, oy, cw, ch, 16);
            long strongPixels = count(atlas, ox, oy, cw, ch, 128);
            System.out.println("     cell " + cell
                    + "  alpha>128 box y " + strong[1] + "-" + strong[3] + " (" + strongPixels + " px)"
                    + "  alpha>16 box y " + soft[1] + "-" + soft[3]
                    + "  -> residue strip " + (strong[1] - soft[1]) + " top / " + (soft[3] - strong[3]) + " bottom");
        }
    }

    private static long count(BufferedImage image, int ox, int oy, int w, int h, int threshold) {
        long total = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((image.getRGB(ox + x, oy + y) >>> 24) > threshold)
                    total++;
        return total;
    }

    private static int[] box(BufferedImage image, int ox, int oy, int w, int h, int threshold) {
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if ((image.getRGB(ox + x, oy + y) >>> 24) <= threshold)
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
        return new int[]{minX, minY, maxX, maxY};
    }
}
