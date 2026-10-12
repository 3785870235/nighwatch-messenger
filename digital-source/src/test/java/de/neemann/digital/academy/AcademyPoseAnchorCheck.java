/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Where the character actually lands on screen, with the delivered second atlases installed.
 * <p>
 * Every scene that draws Fu or Luo was composed against the first sheet drawn cell first: the cell's
 * top left corner on the requested point, the cell scaled to the requested height. The delivered
 * second sheets pad their cells differently, so drawing them the same way would shrink the figure
 * and drop it down the page. {@link AcademyCharacters#portrait} keeps the old contract and places
 * the figure inside the requested box by measurement. This draws each pose through the real renderer
 * into an offscreen image and checks two things: the poses already in the scenes have not moved, and
 * the new poses stand where the old ones do.
 * <p>
 * The yardstick is measured here from the sheets themselves, not taken from the renderer.
 * <p>
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar de.neemann.digital.academy.AcademyPoseAnchorCheck &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyPoseAnchorCheck {
    private static final int WIDTH = 900;
    private static final int HEIGHT = 900;
    private static final double REQUEST_X = 150;
    private static final double REQUEST_Y = 100;
    private static final double REQUEST_HEIGHT = 600;
    private static int checks;

    private AcademyPoseAnchorCheck() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("academy.root", args[0]);
        AcademyArt.reload();
        File dir = new File(args[0], "assets/game/v2/characters");
        System.out.println("requested box: corner " + REQUEST_X + "," + REQUEST_Y + ", height " + REQUEST_HEIGHT
                + "  (canvas " + WIDTH + "x" + HEIGHT + ")");
        for (String name : new String[]{"fu", "luo"})
            inspect(name, dir);
        System.out.println("RESULT: " + checks + " CHECKS PASSED");
    }

    private static void inspect(String name, File dir) {
        System.out.println(name + ":");
        int[][] raw = new int[8][];
        for (int frame = 0; frame < 8; frame++) {
            BufferedImage cell = AcademyCharacters.pose(name, frame);
            raw[frame] = cell == null ? null : painted(cell);
            check(name + " frame " + frame + ": the sheet holds a figure", raw[frame] != null);
            check(name + " frame " + frame + ": is drawn against a real sheet",
                    frame < 4 || new File(dir, name + "-poses-b.png").isFile());
        }
        // The first sheet as the old renderer drew it: cell corner on the requested point, the cell
        // scaled to the requested height. This is what the scenes were composed against.
        double oldScale = REQUEST_HEIGHT / AcademyCharacters.pose(name, 0).getHeight();
        double[] firstFeet = new double[4];
        double[] firstCentres = new double[4];
        double[] firstHeights = new double[4];
        for (int frame = 0; frame < 4; frame++) {
            firstFeet[frame] = raw[frame][3];
            firstCentres[frame] = (raw[frame][0] + raw[frame][2]) / 2.0;
            firstHeights[frame] = raw[frame][3] - raw[frame][1] + 1;
        }
        double footLine = REQUEST_Y + median(firstFeet) * oldScale;
        double centreLine = REQUEST_X + median(firstCentres) * oldScale;
        double figure = median(firstHeights) * oldScale;
        System.out.println("  the first sheet in the requested box: figure " + round(figure)
                + " px, foot line " + round(footLine) + ", centre line " + round(centreLine));

        double[] heights = new double[8];
        double[] feet = new double[8];
        double[] centres = new double[8];
        double[] scales = new double[8];
        int[][] drawn = new int[8][];
        for (int frame = 0; frame < 8; frame++) {
            drawn[frame] = painted(draw(name, frame));
            check(name + " frame " + frame + ": draws something", drawn[frame] != null);
            heights[frame] = drawn[frame][3] - drawn[frame][1] + 1;
            feet[frame] = drawn[frame][3];
            centres[frame] = (drawn[frame][0] + drawn[frame][2]) / 2.0;
            scales[frame] = heights[frame] / (raw[frame][3] - raw[frame][1] + 1);
            StringBuilder line = new StringBuilder("     frame " + frame
                    + " (sheet " + (frame < 4 ? "first" : "second") + ")"
                    + "  x " + drawn[frame][0] + "-" + drawn[frame][2]
                    + "  y " + drawn[frame][1] + "-" + drawn[frame][3]
                    + "  figure " + round(heights[frame]) + "  scale " + round3(scales[frame]));
            if (frame < 4)
                line.append("   old: x ").append(round(REQUEST_X + raw[frame][0] * oldScale))
                        .append("-").append(round(REQUEST_X + raw[frame][2] * oldScale))
                        .append("  y ").append(round(REQUEST_Y + raw[frame][1] * oldScale))
                        .append("-").append(round(REQUEST_Y + raw[frame][3] * oldScale));
            System.out.println(line);
        }

        // 1. The poses already in the scenes keep their size and do not move sideways. Vertically each
        //    may move by the few pixels its own feet differ from the sheet's foot line, which is what
        //    removes the hop when a scene changes pose.
        for (int frame = 0; frame < 4; frame++) {
            double oldLeft = REQUEST_X + raw[frame][0] * oldScale;
            double oldRight = REQUEST_X + raw[frame][2] * oldScale;
            double oldFoot = REQUEST_Y + raw[frame][3] * oldScale;
            check(name + " frame " + frame + ": same place across as before (" + drawn[frame][0] + "-"
                            + drawn[frame][2] + " vs " + round(oldLeft) + "-" + round(oldRight) + ")",
                    Math.abs(drawn[frame][0] - oldLeft) <= 3 && Math.abs(drawn[frame][2] - oldRight) <= 3);
            check(name + " frame " + frame + ": same size as before (" + round(heights[frame]) + " vs "
                            + round(firstHeights[frame] * oldScale) + ")",
                    Math.abs(heights[frame] - firstHeights[frame] * oldScale) <= 3);
            check(name + " frame " + frame + ": moved down or up only by its own foot offset ("
                            + round(feet[frame] - oldFoot) + " px)",
                    Math.abs(feet[frame] - oldFoot) <= Math.abs(footLine - oldFoot) + 3);
        }
        // 2. Every pose, from either sheet, stands on the first sheet's foot line. The foot is a contact
        //    with the ground, so this holds per pose rather than on average.
        for (int frame = 0; frame < 8; frame++)
            check(name + " frame " + frame + ": foot on the line (" + round(feet[frame]) + " vs "
                            + round(footLine) + ")",
                    Math.abs(feet[frame] - footLine) <= 3);
        // 3. One scale per sheet, so a change of pose cannot resize the figure. The two sheets differ in
        //    scale on purpose - the second sheet's correction is what makes its figure match.
        for (int sheet = 0; sheet < 2; sheet++) {
            double min = Double.MAX_VALUE;
            double max = 0;
            for (int frame = sheet * 4; frame < sheet * 4 + 4; frame++) {
                min = Math.min(min, scales[frame]);
                max = Math.max(max, scales[frame]);
            }
            check(name + " sheet " + (sheet + 1) + ": one scale for all four poses ("
                            + round3(min) + "-" + round3(max) + ")",
                    max - min <= .02);
        }
        // 4. The second sheet draws the character as tall as the first. How tall a single pose stands is
        //    the art's to decide - a lowered head occupies less of its cell - so the sheets are compared
        //    by their middles. Without the measured correction the second sheets would be out by 6.5
        //    percent (fu) and 21 percent (luo).
        double firstSize = median(heights, 0, 4);
        double secondSize = median(heights, 4, 8);
        check(name + ": both sheets draw the figure the same size (" + round(firstSize) + " vs "
                        + round(secondSize) + ")",
                Math.abs(firstSize - secondSize) <= figure * .02);
        // 5. The second sheet sits where the first does. An outstretched arm widens a silhouette, so
        //    single poses are allowed a band; the middle of each sheet has to agree.
        double firstMiddle = median(centres, 0, 4);
        double secondMiddle = median(centres, 4, 8);
        check(name + ": the second sheet stands where the first does (median x " + round(secondMiddle)
                        + " vs " + round(firstMiddle) + ")",
                Math.abs(secondMiddle - firstMiddle) <= 12);
        for (int frame = 0; frame < 8; frame++)
            check(name + " frame " + frame + ": silhouette centre stays near the line ("
                            + round(centres[frame]) + " vs " + round(centreLine) + ")",
                    Math.abs(centres[frame] - centreLine) <= REQUEST_HEIGHT * .12);
    }

    private static double median(double[] values) {
        return median(values, 0, values.length);
    }

    private static double median(double[] values, int from, int to) {
        double[] sorted = java.util.Arrays.copyOfRange(values, from, to);
        java.util.Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    /** Draws one pose exactly the way the scenes do. */
    private static BufferedImage draw(String name, int frame) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        if ("fu".equals(name))
            AcademyCharacters.messengerPose(g, REQUEST_X, REQUEST_Y, REQUEST_HEIGHT, 0, frame);
        else
            AcademyCharacters.keeper(g, REQUEST_X, REQUEST_Y, REQUEST_HEIGHT, 0, frame);
        g.dispose();
        return canvas;
    }

    private static int[] painted(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) <= 16)
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

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** The scale figures are read at a thousandth, so they need their own rounding. */
    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static void check(String name, boolean ok) {
        checks++;
        System.out.println((ok ? "     ok   " : "     FAIL ") + name);
        if (!ok)
            throw new AssertionError(name);
    }
}
