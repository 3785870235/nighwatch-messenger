/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Captures real Swing animation frames, driven by the Digital reference model, in an own JVM. */
public final class AcademyMotionPreview {
    private static PrintStream log;

    private AcademyMotionPreview() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]).getCanonicalFile();
        File output = new File(root, "docs/evidence/ux2-motion");
        output.mkdirs();
        log = new PrintStream(new File(output, "motion-check.log"), "UTF-8");
        int exit = 0;
        try {
            ToolTipManager.sharedInstance().setEnabled(false);
            run(root, output);
            log.println("RESULT: ALL CHECKS PASSED");
        } catch (Throwable e) {
            e.printStackTrace(log);
            exit = 1;
        } finally {
            edt(() -> {
                for (Window w : Window.getWindows())
                    w.dispose();
                return null;
            });
            log.close();
        }
        System.exit(exit);
    }

    private static void run(File root, File output) throws Exception {
        LevelDefinition level = LevelDefinition.load(new File(root, "levels/03_archive_gate/level.properties"));
        Reading closed;
        Reading open;
        try (ReferenceDevice reference = new ReferenceDevice(level)) {
            ExperimentSession inputs = new ExperimentSession(Arrays.asList("A", "B"));
            closed = reference.evaluate(inputs.toInputs(0));
            open = reference.evaluate(inputs.toInputs(3));
        }
        check("real Digital readings: 00 closes, 11 opens", closed.isValid() && !closed.isTrue() && open.isTrue());
        DoorView door = edt(DoorView::new);
        JFrame scene = edt(() -> {
            JFrame f = new JFrame("Academy animation preview");
            f.setContentPane(door);
            f.setSize(816, 499);
            f.setVisible(true);
            door.show(closed, "旧门 · A=0 B=0");
            return f;
        });
        Thread.sleep(250);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(new File(output, "door-live.gif"))) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int frame = 0; frame < 52; frame++) {
                if (frame == 7)
                    edt(() -> { door.show(open, "旧门 · A=1 B=1"); return null; });
                if (frame == 35)
                    edt(() -> { door.show(closed, "旧门 · A=0 B=0"); return null; });
                if (frame == 10)
                    check("continuous intermediate pose", edt(() -> door.openness() > 0 && door.openness() < 1));
                BufferedImage image = edt(() -> render(door));
                if (frame == 0 || frame == 10 || frame == 23)
                    ImageIO.write(image, "png", new File(output, "door-frame-" + frame + ".png"));
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata meta = writer.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image), param);
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode tree = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode control = node(tree, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", "10");
                control.setAttribute("transparentColorIndex", "0");
                if (frame == 0) {
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    node(tree, "ApplicationExtensions").appendChild(loop);
                }
                meta.setFromTree(format, tree);
                writer.writeToSequence(new IIOImage(image, null, meta), param);
                Thread.sleep(100);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        edt(() -> { door.show(open, "旧门 · A=1 B=1"); return null; });
        Thread.sleep(1000);
        edt(() -> { door.showUnobserved("旧门 · 新报告", "尚未观察，不计算回应"); return null; });
        check("unknown does not invent a zero", edt(() -> door.isUnobserved() && !door.getReading().isValid() && door.openness() == 1));
        edt(() -> { AcademyAtmosphere.motion(false); door.show(closed, "旧门"); return null; });
        check("reduced motion snaps to valid reading", edt(() -> door.openness() == 0));
        edt(() -> { scene.dispose(); return null; });
        check("hidden door timer stopped", !edt(door::isAnimating));
        edt(() -> { AcademyAtmosphere.motion(true); return null; });

        boolean[] finished = {false};
        AcademyCinematic intro = edt(() -> new AcademyCinematic(false, () -> finished[0] = true));
        JFrame movie = edt(() -> {
            JFrame f = new JFrame("Academy story preview");
            f.setContentPane(intro);
            f.setSize(1280, 800);
            f.setVisible(true);
            return f;
        });
        Thread.sleep(300);
        check("intro does not reveal AND", !edt(intro::storyText).contains("与规则"));
        ImageIO.write(edt(() -> render(intro)), "png", new File(output, "intro.png"));
        Thread.sleep(2800);
        ImageIO.write(edt(() -> render(intro)), "png", new File(output, "intro-second-beat.png"));
        edt(() -> { intro.continueButton().doClick(); return null; });
        check("intro skippable and timer released", finished[0] && !edt(intro::isAnimating));
        edt(() -> { movie.dispose(); return null; });
        previewSpirit(output);
    }

    private static void previewSpirit(File output) throws Exception {
        SpiritStage view = edt(SpiritStage::new);
        JFrame frame = edt(() -> {
            JFrame f = new JFrame("Academy interactive spirit preview");
            f.setContentPane(view);
            f.setSize(816, 599);
            f.setVisible(true);
            return f;
        });
        List<BufferedImage> frames = new ArrayList<>();
        for (int n = 0; n < 48; n++) {
            double angle = n * Math.PI * 2 / 48;
            Point mouse = new Point(400 + (int) (280 * Math.cos(angle)), 290 + (int) (185 * Math.sin(angle)));
            edt(() -> {
                view.cursor = mouse;
                view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                        mouse.x, mouse.y, 0, false, MouseEvent.NOBUTTON));
                return null;
            });
            Thread.sleep(100);
            frames.add(edt(() -> render(view)));
        }
        writeGif(new File(output, "spirit-gaze-live.gif"), frames);
        edt(() -> {
            AcademyAtmosphere.motion(false);
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                    30, 290, 0, false, MouseEvent.NOBUTTON));
            return null;
        });
        check("mouse event tracked on left", edt(() -> AcademyPointer.local(view).getX() == 30));
        BufferedImage left = edt(() -> render(view));
        edt(() -> {
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                    770, 290, 0, false, MouseEvent.NOBUTTON));
            return null;
        });
        check("mouse event tracked on right", edt(() -> AcademyPointer.local(view).getX() == 770));
        BufferedImage right = edt(() -> render(view));
        int eyeChanges = 0;
        for (int y = 255; y < 310; y++)
            for (int x = 420; x < 480; x++)
                if (left.getRGB(x, y) != right.getRGB(x, y))
                    eyeChanges++;
        check("pupils change in eye region with fixed body", eyeChanges > 0);
        ImageIO.write(left, "png", new File(output, "spirit-looking-left.png"));
        ImageIO.write(right, "png", new File(output, "spirit-looking-right.png"));
        edt(() -> { frame.dispose(); AcademyAtmosphere.motion(true); return null; });
        check("spirit timer stopped when window closes", !edt(() -> view.animation.isRunning()));
    }

    private static void writeGif(File file, List<BufferedImage> frames) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int n = 0; n < frames.size(); n++) {
                BufferedImage frame = frames.get(n);
                IIOMetadata meta = writer.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(frame), writer.getDefaultWriteParam());
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode control = node(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", "10");
                control.setAttribute("transparentColorIndex", "0");
                if (n == 0) {
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    node(root, "ApplicationExtensions").appendChild(loop);
                }
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, meta), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static final class SpiritStage extends JComponent {
        private Point cursor = new Point(680, 290);
        private final javax.swing.Timer animation;

        private SpiritStage() {
            animation = AcademyAtmosphere.watch(this, () -> { });
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(AcademyTheme.NIGHT);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(AcademyTheme.font(Font.BOLD, 20));
            g.setColor(AcademyTheme.IVORY);
            g.drawString("微光 · 漂浮、纸翼与鼠标跟随", 30, 40);
            AcademyCharacters.spirit(g, 400, 290, 330, System.nanoTime() / 1000000000.0, false, AcademyPointer.local(this));
            g.setColor(new Color(0x65CEE1));
            g.drawOval(cursor.x - 9, cursor.y - 9, 18, 18);
            g.drawLine(cursor.x - 13, cursor.y, cursor.x + 13, cursor.y);
            g.drawLine(cursor.x, cursor.y - 13, cursor.x, cursor.y + 13);
            g.setFont(AcademyTheme.body());
            g.setColor(AcademyTheme.MUTED);
            g.drawString("光标为测试程序发送的实际鼠标事件；角色由游戏渲染器实时绘制。", 30, getHeight() - 28);
            g.dispose();
        }
    }

    private static BufferedImage render(JComponent view) {
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        view.printAll(g);
        g.dispose();
        return image;
    }

    private static IIOMetadataNode node(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++)
            if (root.item(i).getNodeName().equals(name))
                return (IIOMetadataNode) root.item(i);
        IIOMetadataNode added = new IIOMetadataNode(name);
        root.appendChild(added);
        return added;
    }

    private static void check(String name, boolean passed) {
        log.println((passed ? "ok   " : "FAIL ") + name);
        if (!passed)
            throw new AssertionError(name);
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        Object[] value = new Object[1];
        Exception[] error = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                value[0] = action.call();
            } catch (Exception e) {
                error[0] = e;
            }
        });
        if (error[0] != null)
            throw error[0];
        @SuppressWarnings("unchecked") T result = (T) value[0];
        return result;
    }
}
