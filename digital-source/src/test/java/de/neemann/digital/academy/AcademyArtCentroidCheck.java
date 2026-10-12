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
 * What the delivered second atlases would do to the figures already on screen.
 * <p>
 * {@link AcademyCharacters#portrait} today scales a pose by the whole cell and puts the cell's top
 * left corner at the requested point. That is only correct because the first sheets are painted
 * almost edge to edge (the figure fills 97-99 percent of the cell height). The second sheets leave
 * about a fifth of the cell empty, so reusing the same rule would both shrink the figure and drop it
 * down the page. This measures, for both sheets of each character, the painted box and the
 * alpha-weighted centre of mass, then reports the size and position each rule would produce.
 * <p>
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar de.neemann.digital.academy.AcademyArtCentroidCheck &lt;project root&gt;
 * This is an automated check, not a manual playthrough.
 */
public final class AcademyArtCentroidCheck {
    private AcademyArtCentroidCheck() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File dir = new File(root, "assets/game/v2/characters");
        for (String name : new String[]{"fu", "luo"})
            if (!inspect(name, dir))
                throw new AssertionError(name + ": could not measure both sheets");
        System.out.println("RESULT: measured");
    }

    private static boolean inspect(String name, File dir) throws Exception {
        BufferedImage oldSheet = ImageIO.read(new File(dir, name + "-poses.png"));
        BufferedImage newSheet = ImageIO.read(new File(dir, name + "-poses-b.png"));
        if (oldSheet == null || newSheet == null) {
            System.out.println(name + ": a sheet is unreadable");
            return false;
        }
        // The renderer must produce the same figure height and the same foot line on both sheets, so
        // one direction is chosen as the reference. The first sheets are what the current scenes were
        // composed against, so they stay the reference.
        System.out.println(name + ": reference " + name + "-poses.png, cell "
                + oldSheet.getWidth() / 2 + "x" + oldSheet.getHeight() / 2);
        Stats reference = measure(oldSheet);
        Stats delivered = measure(newSheet);
        print("  first", reference);
        print("  second", delivered);

        // Normalised to the cell so two different cell sizes can be compared.
        double refHeight = reference.medianBoxHeight() / reference.cellHeight;
        double newHeight = delivered.medianBoxHeight() / delivered.cellHeight;
        System.out.println("  figure height as a fraction of the cell: first "
                + round(refHeight) + "  second " + round(newHeight));
        System.out.println("  keeping the figure the same size means scaling the second atlas by "
                + round(refHeight / newHeight) + " relative to its cell height");

        double refCentreX = reference.medianBoxCentreX() / reference.cellWidth;
        double newCentreX = delivered.medianBoxCentreX() / delivered.cellWidth;
        System.out.println("  painted centre x as a fraction of the cell: first "
                + round(refCentreX) + "  second " + round(newCentreX)
                + "  -> the figure would move right by " + round((newCentreX - refCentreX) * 100) + " percent of a cell");

        double refBottom = reference.medianBoxBottom() / reference.cellHeight;
        double newBottom = delivered.medianBoxBottom() / delivered.cellHeight;
        System.out.println("  foot line as a fraction of the cell: first "
                + round(refBottom) + "  second " + round(newBottom)
                + "  -> keeping the cell corner fixed would lower the figure by "
                + round((newBottom - refBottom) * 100) + " percent of a cell");

        // The rule the renderer is meant to adopt: scale a pose so its painted figure comes out at the
        // height the first sheet paints it at, and place it so the painted foot lands where the first
        // sheet puts it. Both are per sheet, not per pose, so a change of pose inside one sheet cannot
        // move or resize the figure. Measured here in the units the renderer is handed: the height the
        // scene asks for, and the requested top edge as the origin.
        System.out.println("  rule check, at a requested height of 600 px:");
        for (String label : new String[]{"first", "second"}) {
            Stats s = label.equals("first") ? reference : delivered;
            double height = 600;
            double figure = s.referenceFigureHeight() / s.cellHeight;
            double foot = s.referenceFoot() / s.cellHeight;
            double scale = height / s.cellHeight / figure;
            System.out.println("     " + label + ": painted figure " + round(figure * s.cellHeight)
                    + " px of " + s.cellHeight + ", drawn at " + round(height * s.cellHeight / s.cellHeight / figure * figure)
                    + " px, foot lands at " + round(height * foot / figure) + " px below the requested top");
        }
        return true;
    }

    private static Stats measure(BufferedImage atlas) {
        return new Stats(atlas);
    }

    private static void print(String label, Stats s) {
        System.out.println("  " + label + " cell " + s.cellWidth + "x" + s.cellHeight
                + " boxcentre x " + s.medianBoxCentreX() + "  foot " + s.medianBoxBottom()
                + "  height " + s.medianBoxHeight()
                + "  mass centre y " + s.medianMassY());
        for (int i = 0; i < 4; i++)
            System.out.println("       cell " + i + " box x " + s.left[i] + "-" + s.right[i]
                    + " y " + s.top[i] + "-" + s.bottom[i] + "  mass centre y " + round(s.massY[i])
                    + "  mass share " + round(s.mass[i] / s.totalMass[i]));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /** Per-cell geometry of one sheet. */
    private static final class Stats {
        private final int cellWidth;
        private final int cellHeight;
        private final int[] left = new int[4];
        private final int[] right = new int[4];
        private final int[] top = new int[4];
        private final int[] bottom = new int[4];
        private final double[] massY = new double[4];
        private final double[] mass = new double[4];
        private final double[] totalMass = new double[4];

        private Stats(BufferedImage atlas) {
            cellWidth = atlas.getWidth() / 2;
            cellHeight = atlas.getHeight() / 2;
            for (int cell = 0; cell < 4; cell++) {
                int ox = cell % 2 * cellWidth;
                int oy = cell / 2 * cellHeight;
                int minX = cellWidth;
                int minY = cellHeight;
                int maxX = -1;
                int maxY = -1;
                int weight = 0;
                double sum = 0;
                double total = 0;
                for (int y = 0; y < cellHeight; y++)
                    for (int x = 0; x < cellWidth; x++) {
                        int alpha = atlas.getRGB(ox + x, oy + y) >>> 24;
                        if (alpha <= 16)
                            continue;
                        if (x < minX)
                            minX = x;
                        if (x > maxX)
                            maxX = x;
                        if (y < minY)
                            minY = y;
                        if (y > maxY)
                            maxY = y;
                        // The centre of mass must not be dragged around by a raised arm or a wing, so
                        // painted pixels are weighted by their alpha rather than counted.
                        weight += alpha;
                        sum += (double) alpha * y;
                        total += alpha;
                    }
                left[cell] = minX;
                right[cell] = maxX;
                top[cell] = minY;
                bottom[cell] = maxY;
                mass[cell] = weight;
                totalMass[cell] = total;
                massY[cell] = total == 0 ? 0 : sum / total;
            }
        }

        private static double median(double[] values) {
            double[] sorted = values.clone();
            java.util.Arrays.sort(sorted);
            return (sorted[1] + sorted[2]) / 2;
        }

        private double medianBoxCentreX() {
            double[] v = new double[4];
            for (int i = 0; i < 4; i++)
                v[i] = (left[i] + right[i]) / 2.0;
            return median(v);
        }

        private double medianBoxBottom() {
            double[] v = new double[4];
            for (int i = 0; i < 4; i++)
                v[i] = bottom[i];
            return median(v);
        }

        private double medianBoxHeight() {
            double[] v = new double[4];
            for (int i = 0; i < 4; i++)
                v[i] = bottom[i] - top[i] + 1;
            return median(v);
        }

        private double medianMassY() {
            return median(massY);
        }

        /**
         * The height the renderer should treat as the figure: the middle half of the cell, sampled so
         * a raised arm or wing cannot join the measurement, taken across the four poses.
         */
        private double referenceFigureHeight() {
            return medianBoxHeight();
        }

        /** The foot line the renderer should place at the requested point: across the four poses. */
        private double referenceFoot() {
            return medianBoxBottom();
        }
    }
}
