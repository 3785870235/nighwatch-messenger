/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.Callable;

/** Real Swing key dispatch, lifecycle, visual frames and measured repaint cadence. */
public final class AcademyUx4Check {
    private static int checks;
    private static PrintStream log;
    private static File output;

    private AcademyUx4Check() {
    }

    /**
     * @param args project root, optional ffmpeg executable for a 60 fps rendering export
     * @throws Exception on failed verification
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("academy.root", args[0]);
        System.setProperty("academy.silent", "true");
        output = new File(args[0], "docs/evidence/ux4-cinematic");
        output.mkdirs();
        log = new PrintStream(new File(output, "gui-check.log"), "UTF-8");
        try {
            run();
            if (args.length > 1)
                export(args[1]);
            log.println("RESULT: " + checks + " CHECKS PASSED");
        } finally {
            edt(() -> {
                for (java.awt.Window window : java.awt.Window.getWindows())
                    window.dispose();
                AcademySound.stopAll();
                return null;
            });
            log.close();
        }
        System.exit(0);
    }

    private static void run() throws Exception {
        final int[] finished = {0};
        AcademyCinematic intro = edt(() -> new AcademyCinematic(false, () -> finished[0]++));
        JFrame frame = edt(() -> show(intro));
        Thread.sleep(250);
        check("four-shot intro lasts 42 seconds before final hold", intro.totalDuration() == 42);
        check("intro owns a live animation timer", edt(intro::isAnimating));
        edt(() -> { intro.previewAt(9.9); return null; });
        check("first shot is still present at 9.9s", edt(intro::shotIndex) == 0);
        edt(() -> { intro.previewAt(10.1); return null; });
        check("automatic timeline enters second shot after 10s", edt(intro::shotIndex) == 1);
        edt(() -> { intro.previewAt(0); return null; });
        key(intro, KeyEvent.VK_SPACE, KeyEvent.KEY_PRESSED);
        check("space advances exactly one shot", edt(intro::shotIndex) == 1 && finished[0] == 0);
        for (int i = 0; i < 5; i++)
            key(intro, KeyEvent.VK_SPACE, KeyEvent.KEY_PRESSED);
        check("held space cannot skip several shots", edt(intro::shotIndex) == 1);
        key(intro, KeyEvent.VK_SPACE, KeyEvent.KEY_RELEASED);
        key(intro, KeyEvent.VK_SPACE, KeyEvent.KEY_PRESSED);
        key(intro, KeyEvent.VK_SPACE, KeyEvent.KEY_RELEASED);
        check("release and press enters one more shot", edt(intro::shotIndex) == 2 && finished[0] == 0);
        key(intro, KeyEvent.VK_ESCAPE, KeyEvent.KEY_PRESSED);
        edt(() -> { intro.continueButton().doClick(); return null; });
        check("escape skips whole sequence with exactly one callback", finished[0] == 1);
        check("skipping releases timer", !edt(intro::isAnimating));
        edt(() -> { frame.dispose(); return null; });

        AcademyCinematic scenes = edt(() -> new AcademyCinematic(false, () -> { }));
        JFrame stage = edt(() -> show(scenes));
        for (int i = 0; i < 4; i++) {
            final double t = new double[]{4, 14, 25, 35}[i];
            BufferedImage image = edt(() -> {
                scenes.previewAt(t);
                return render(scenes);
            });
            ImageIO.write(image, "png", new File(output, "shot-" + (i + 1) + ".png"));
        }
        BufferedImage still = edt(() -> { scenes.previewAt(12.2); return render(scenes); });
        BufferedImage later = edt(() -> { scenes.previewAt(14.2); return render(scenes); });
        check("window clouds really change pixels", difference(still, later, 110, 65, 310, 295) > 1000);
        check("candle flames really change pixels", difference(still, later, 1015, 235, 145, 100) > 80);
        edt(() -> { AcademyAtmosphere.motion(false); scenes.previewAt(12); return null; });
        BufferedImage reduced = edt(() -> render(scenes));
        edt(() -> { scenes.previewAt(14); return null; });
        check("reduced motion freezes scene layers", difference(reduced, edt(() -> render(scenes)), 90, 65, 340, 290) == 0);
        edt(() -> { AcademyAtmosphere.motion(true); scenes.previewAt(-1); return null; });
        Thread.sleep(200);
        long first = edt(scenes::paintedFrames);
        long start = System.nanoTime();
        Thread.sleep(4000);
        double fps = (edt(scenes::paintedFrames) - first) / ((System.nanoTime() - start) / 1000000000.0);
        log.println("MEASURED foreground Swing repaint fps at 1280x720: " + fps + "; target timer 16ms; not a guarantee on every machine.");
        check("visible scene paints continuously", fps > 20);
        for (String name : new String[]{"fu", "glimmer", "luo"}) {
            BufferedImage a = AcademyCharacters.pose(name, 0);
            BufferedImage b = AcademyCharacters.pose(name, 1);
            check(name + " has distinct real RGBA poses", a != null && b != null && a.getColorModel().hasAlpha()
                    && difference(a, b, 0, 0, a.getWidth(), a.getHeight()) > 500);
        }
        edt(() -> { AcademyAtmosphere.sound(true); return null; });
        Thread.sleep(500);
        log.println("Audio clips active before mute: " + AcademySound.activeClips());
        edt(() -> { AcademyAtmosphere.sound(false); return null; });
        Thread.sleep(250);
        check("mute cancels queued audio and closes active clips", AcademySound.activeClips() == 0 && AcademySound.pendingClips() == 0);
        edt(() -> { stage.dispose(); return null; });
        check("window removal releases animation timer", !edt(scenes::isAnimating));
        AcademyCinematic end = edt(() -> new AcademyCinematic(true, () -> { }));
        check("ending holds two 9.5s shots", end.totalDuration() == 19);
        edt(() -> { end.stop(); return null; });
    }

    private static JFrame show(AcademyCinematic panel) {
        JFrame frame = new JFrame("Academy UX4 isolated presentation check");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(panel);
        panel.setPreferredSize(new java.awt.Dimension(1280, 720));
        frame.pack();
        frame.setLocation(30, 30);
        frame.setVisible(true);
        panel.nextButton().requestFocusInWindow();
        return frame;
    }

    private static void key(AcademyCinematic panel, int code, int type) throws Exception {
        edt(() -> {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(new KeyEvent(panel.nextButton(), type,
                    System.currentTimeMillis(), 0, code, code == KeyEvent.VK_SPACE ? ' ' : KeyEvent.CHAR_UNDEFINED));
            return null;
        });
    }

    private static int difference(BufferedImage a, BufferedImage b, int x, int y, int w, int h) {
        int pixels = 0;
        for (int py = y; py < y + h; py++)
            for (int px = x; px < x + w; px++)
                if (a.getRGB(px, py) != b.getRGB(px, py))
                    pixels++;
        return pixels;
    }

    private static void export(String encoder) throws Exception {
        AcademyCinematic panel = edt(() -> new AcademyCinematic(false, () -> { }));
        JFrame frame = edt(() -> show(panel));
        Process process = new ProcessBuilder(encoder, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "rawvideo", "-pixel_format", "bgr24", "-video_size", "1280x720", "-framerate", "60", "-i", "pipe:0",
                "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                new File(output, "intro-60fps-silent.mp4").getAbsolutePath())
                .redirectError(new File(output, "encoder.log")).start();
        try (OutputStream pipe = process.getOutputStream()) {
            for (int i = 0; i < 2520; i++) {
                final double seconds = i / 60.0;
                byte[] bytes = edt(() -> {
                    panel.previewAt(seconds);
                    BufferedImage image = render(panel);
                    return ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                });
                pipe.write(bytes);
            }
        }
        check("60fps export encoder exits successfully", process.waitFor() == 0);
        log.println("Export: 2520 frames / 60fps / 42 seconds, actual Swing renderer at deterministic timestamps.");
        edt(() -> { frame.dispose(); return null; });
    }

    private static BufferedImage render(AcademyCinematic panel) {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        panel.printAll(g);
        g.dispose();
        return image;
    }

    private static void check(String name, boolean ok) {
        log.println((ok ? "OK " : "FAIL ") + name);
        log.flush();
        if (!ok)
            throw new AssertionError(name);
        checks++;
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
