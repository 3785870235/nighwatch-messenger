/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The home art moves: the lamps flicker, the stars twinkle, a star falls now and then, and with
 * motion switched off the art stays exactly as painted. Renders the backdrop at fixed times and
 * measures the pixels; writes the frames and a contact sheet for a look.
 * Run: java -cp target/test-classes;target/classes;target/Digital.jar
 * de.neemann.digital.academy.AcademyHomeMotionCheck &lt;project root&gt; [shots dir]
 */
public final class AcademyHomeMotionCheck {
    private static final int W = 1600;
    private static final int H = 900;
    private static int checks;

    private AcademyHomeMotionCheck() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("academy.root", new File(args[0]).getAbsolutePath());
        File shots = args.length > 1 ? new File(args[1]) : null;
        if (shots != null)
            shots.mkdirs();
        int result = 0;
        try {
            run(shots);
            System.out.println("RESULT: " + checks + " CHECKS PASSED");
        } catch (Throwable e) {
            e.printStackTrace();
            result = 1;
        }
        System.exit(result);
    }

    private static void run(File shots) throws Exception {
        check("the menu art is there", AcademyArt.get(AcademyArt.MENU_BACKGROUND) != null);
        double s = Math.max(W / 1672.0, H / 941.0);

        // --- motion off: the art does not change over time ---
        AcademyAtmosphere.motion(false);
        BufferedImage still0 = frame(0);
        BufferedImage still1 = frame(7.3);
        check("without motion two times look the same", same(still0, still1));

        AcademyAtmosphere.motion(true);
        // --- the big lamp on the right flares and gutters ---
        double tHigh = 0;
        double tLow = 0;
        for (double t = 0; t < 40; t += 0.05) {
            if (AcademyHomeMotion.flicker(4, t) > AcademyHomeMotion.flicker(4, tHigh))
                tHigh = t;
            if (AcademyHomeMotion.flicker(4, t) < AcademyHomeMotion.flicker(4, tLow))
                tLow = t;
        }
        check("the big lamp reaches a flare", AcademyHomeMotion.flicker(4, tHigh) > 0.8);
        check("the big lamp gutters", AcademyHomeMotion.flicker(4, tLow) < -0.6);
        BufferedImage high = frame(tHigh);
        BufferedImage low = frame(tLow);
        int lx = (int) (1636 * s);
        int ly = (int) (515 * s);
        double lampHigh = luma(high, lx - 30, ly - 30, 60, 60);
        double lampLow = luma(low, lx - 30, ly - 30, 60, 60);
        double lampArt = luma(still0, lx - 30, ly - 30, 60, 60);
        System.out.printf("lamp luma: art %.1f, flare %.1f, gutter %.1f%n", lampArt, lampHigh, lampLow);
        check("a flare is clearly brighter than a gutter", lampHigh - lampLow > 25);
        check("a gutter is darker than the painted lamp", lampLow < lampArt - 8);

        // --- a star with rays twinkles: dim and bright are far apart ---
        double tPeak = 0;
        double tDim = 0;
        for (double t = 0; t < 20; t += 0.02) {
            if (AcademyHomeMotion.twinkle(0, t) > AcademyHomeMotion.twinkle(0, tPeak))
                tPeak = t;
            if (AcademyHomeMotion.twinkle(0, t) < AcademyHomeMotion.twinkle(0, tDim))
                tDim = t;
        }
        BufferedImage peak = frame(tPeak);
        BufferedImage dim = frame(tDim);
        int sx = (int) (890 * s);
        int sy = (int) (33 * s);
        double starPeak = luma(peak, sx - 6, sy - 6, 13, 13);
        double starDim = luma(dim, sx - 6, sy - 6, 13, 13);
        System.out.printf("star luma: peak %.1f, dim %.1f%n", starPeak, starDim);
        check("a star at its peak is clearly brighter than at its dimmest", starPeak - starDim > 30);

        // --- a star falls in the open sky every few seconds ---
        check("no star falls at 8.6 s", AcademyHomeMotion.fallingStar(8.6) == null);
        float[] head = AcademyHomeMotion.fallingStar(9.4);
        check("a star falls at 9.4 s", head != null);
        BufferedImage before = frame(8.6);
        BufferedImage falling = frame(9.4);
        int hx = (int) (head[0] * s);
        int hy = (int) (head[1] * s);
        double headBefore = luma(before, hx - 2, hy - 2, 5, 5);
        double headFalling = luma(falling, hx - 2, hy - 2, 5, 5);
        System.out.printf("falling star head luma: before %.1f, falling %.1f%n", headBefore, headFalling);
        check("the falling star is clearly visible", headFalling - headBefore > 60);

        if (shots != null) {
            ImageIO.write(still0, "png", new File(shots, "home-still.png"));
            ImageIO.write(high, "png", new File(shots, "home-flare.png"));
            ImageIO.write(low, "png", new File(shots, "home-gutter.png"));
            ImageIO.write(falling, "png", new File(shots, "home-falling.png"));
            double[] times = {tHigh, tLow, tPeak, tDim, 9.4, 13.1};
            String[] names = {"灯亮", "灯暗", "星亮", "星暗", "流星", "任意"};
            ImageIO.write(sheet(times, names, s), "png", new File(shots, "home-sheet.png"));
        }
    }

    private static BufferedImage frame(double t) {
        BufferedImage i = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        AcademyHome.paintBackdrop(g, W, H, t);
        g.dispose();
        return i;
    }

    /** One row per time; columns: the sky with the moon, the lamps on the right, the left windows. */
    private static BufferedImage sheet(double[] times, String[] names, double s) {
        int[][] crops = {
                {(int) (800 * s), 0, (int) (170 * s), (int) (210 * s)},
                {(int) (960 * s), (int) (360 * s), (int) (712 * s), (int) (260 * s)},
                {(int) (640 * s), (int) (60 * s), (int) (300 * s), (int) (480 * s)}};
        int rowH = 260;
        int gap = 8;
        int[] widths = new int[crops.length];
        int total = 70;
        for (int c = 0; c < crops.length; c++) {
            widths[c] = (int) Math.round(crops[c][2] * (rowH / (double) crops[c][3]));
            total += widths[c] + gap;
        }
        BufferedImage out = new BufferedImage(total, times.length * (rowH + gap), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(20, 20, 24));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int r = 0; r < times.length; r++) {
            BufferedImage f = frame(times[r]);
            int y = r * (rowH + gap);
            g.setColor(Color.WHITE);
            g.setFont(AcademyTheme.font(Font.BOLD, 16));
            g.drawString(names[r], 8, y + 24);
            g.setFont(AcademyTheme.font(Font.PLAIN, 12));
            g.drawString(String.format("t=%.2f", times[r]), 8, y + 44);
            int x = 70;
            for (int c = 0; c < crops.length; c++) {
                int[] k = crops[c];
                g.drawImage(f.getSubimage(k[0], k[1], k[2], k[3]), x, y, widths[c], rowH, null);
                x += widths[c] + gap;
            }
        }
        g.dispose();
        return out;
    }

    private static boolean same(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y))
                    return false;
        return true;
    }

    private static double luma(BufferedImage img, int x0, int y0, int w, int h) {
        double sum = 0;
        for (int y = y0; y < y0 + h; y++)
            for (int x = x0; x < x0 + w; x++) {
                int p = img.getRGB(x, y);
                sum += 0.299 * (p >> 16 & 255) + 0.587 * (p >> 8 & 255) + 0.114 * (p & 255);
            }
        return sum / (w * h);
    }

    private static void check(String name, boolean ok) {
        checks++;
        System.out.println((ok ? "ok   " : "FAIL ") + name);
        if (!ok)
            throw new AssertionError(name);
    }
}
