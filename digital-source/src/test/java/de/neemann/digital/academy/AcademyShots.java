/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Developer screenshots for visual review. It writes PNG files only into the given scratch folder and
 * never touches player work or evidence folders.
 */
public final class AcademyShots {
    private static File output;

    private AcademyShots() {
    }

    /**
     * @param args project root, scratch output folder, then any of: spirit, cinema, letter, case, result, ledger,
     *             story, entrance, motion
     * @throws Exception on render failure
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("academy.root", args[0]);
        System.setProperty("academy.silent", "true");
        output = new File(args[1]);
        output.mkdirs();
        try {
            for (int i = 2; i < args.length; i++) {
                switch (args[i]) {
                    case "spirit":
                        spirit();
                        break;
                    case "cinema":
                        cinema();
                        break;
                    case "letter":
                        letter();
                        break;
                    case "case":
                        casebook(args[0]);
                        break;
                    case "result":
                        result(args[0]);
                        break;
                    case "ledger":
                        ledger(args[0]);
                        break;
                    case "story":
                        story(args[0]);
                        break;
                    case "entrance":
                        entrances(args[0]);
                        break;
                    case "motion":
                        motion();
                        break;
                    default:
                        System.out.println("unknown shot set " + args[i]);
                }
            }
        } finally {
            edt(() -> {
                for (java.awt.Window window : java.awt.Window.getWindows())
                    window.dispose();
                AcademySound.stopAll();
                return null;
            });
        }
        System.exit(0);
    }

    /**
     * Each pose at both flap extremes and at rest, large enough that a seam would be obvious, and with its
     * eyes closed in a blink where the pose has painted eyes.
     */
    private static void spirit() throws Exception {
        double[] flutters = {3.1, 3.1, 3.1, 4.6, 2.6, 3.1, 3.6, 4.2};
        for (int pose = 0; pose < 8; pose++) {
            double flutter = flutters[pose];
            double[] times = {0, Math.PI / 2 / flutter, 3 * Math.PI / 2 / flutter, 5.0};
            BufferedImage sheet = new BufferedImage(640 * times.length, 660, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x1B2233));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int i = 0; i < times.length; i++) {
                final int frame = pose;
                final double t = times[i];
                final int cx = 320 + 640 * i;
                edt(() -> {
                    AcademyCharacters.spiritPose(g, cx, 330, 600, t, frame, null);
                    return null;
                });
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(output, "spirit-pose" + pose + ".png"));
        }
        // the second sheet has to paint Glimmer as large as the first, or it shrinks when the pose changes
        BufferedImage pairs = new BufferedImage(640 * 4, 660, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pairs.createGraphics();
        g.setColor(new Color(0x1B2233));
        g.fillRect(0, 0, pairs.getWidth(), pairs.getHeight());
        int[] order = {0, 4, 2, 6};
        for (int i = 0; i < order.length; i++) {
            final int frame = order[i];
            final int cx = 320 + 640 * i;
            edt(() -> {
                AcademyCharacters.spiritPose(g, cx, 330, 600, 0, frame, null);
                return null;
            });
        }
        g.setColor(new Color(255, 80, 80, 150));
        for (int y = 30; y < 660; y += 60)
            g.drawLine(0, y, pairs.getWidth(), y);
        g.dispose();
        ImageIO.write(pairs, "png", new File(output, "spirit-sizes.png"));
    }

    /**
     * Walking and speaking as the runtime draws them: eight moments of one stride pair for Fu and Luo,
     * with the legs enlarged below; every mouth closed, half and fully open; and how long a bent frame
     * takes to draw.
     */
    private static void motion() throws Exception {
        AcademyAtmosphere.motion(true);
        for (String who : new String[]{"fu", "luo"}) {
            int frames = 8;
            int fw = 380;
            BufferedImage strip = new BufferedImage(fw * frames, 640, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = strip.createGraphics();
            g.setColor(new Color(0x1B2233));
            g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
            for (int i = 0; i < frames; i++) {
                final AcademyCharacters.Act act = AcademyCharacters.Act.walking(i * .22 / frames, 1);
                final int left = i * fw;
                edt(() -> {
                    Graphics2D c = (Graphics2D) g.create();
                    c.clipRect(left, 0, fw, 640);
                    person(c, who, 0, left + fw / 2.0 - 300, 20, 600, act);
                    c.dispose();
                    return null;
                });
            }
            g.setColor(new Color(255, 80, 80, 150));
            g.drawLine(0, 619, strip.getWidth(), 619);
            g.dispose();
            ImageIO.write(strip, "png", new File(output, "motion-walk-" + who + ".png"));
            BufferedImage legs = new BufferedImage(4 * 360, 2 * 420, BufferedImage.TYPE_INT_RGB);
            Graphics2D l = legs.createGraphics();
            l.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            for (int i = 0; i < frames; i++)
                l.drawImage(strip.getSubimage(i * fw + 70, 360, 240, 280), i % 4 * 360, i / 4 * 420, 360, 420, null);
            l.dispose();
            ImageIO.write(legs, "png", new File(output, "motion-legs-" + who + ".png"));
            long start = System.nanoTime();
            BufferedImage scratch = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < 60; i++) {
                final AcademyCharacters.Act act = AcademyCharacters.Act.walking(i * .013, 1);
                edt(() -> {
                    Graphics2D c = scratch.createGraphics();
                    person(c, who, 0, 0, 20, 600, act);
                    c.dispose();
                    return null;
                });
            }
            System.out.printf("%s walk: %.1f ms a frame%n", who, (System.nanoTime() - start) / 60e6);
            mouths(who);
        }
    }

    /** Every pose of one person, its mouth closed, half and fully open, cropped around the mouth. */
    private static void mouths(String who) throws Exception {
        int cw = 240;
        int ch = 150;
        BufferedImage grid = new BufferedImage(cw * 3, ch * 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D out = grid.createGraphics();
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int frame = 0; frame < 8; frame++) {
            BufferedImage[] open = new BufferedImage[3];
            for (int k = 0; k < 3; k++) {
                final AcademyCharacters.Act act = AcademyCharacters.Act.speaking(k / 2.0);
                final int f = frame;
                open[k] = new BufferedImage(1100, 1100, BufferedImage.TYPE_INT_RGB);
                final Graphics2D g = open[k].createGraphics();
                g.setColor(new Color(0x1B2233));
                g.fillRect(0, 0, 1100, 1100);
                edt(() -> {
                    person(g, who, f, 0, 0, 1100, act);
                    return null;
                });
                g.dispose();
            }
            int[] box = difference(open[0], open[2]);
            int cx = box == null ? 550 : (box[0] + box[2]) / 2;
            int cy = box == null ? 200 : (box[1] + box[3]) / 2;
            System.out.println(who + " mouth " + frame + (box == null ? " does not open"
                    : " opens over " + (box[2] - box[0] + 1) + "x" + (box[3] - box[1] + 1) + " px at height 1100"));
            int x0 = Math.max(0, Math.min(1100 - cw / 2, cx - cw / 4));
            int y0 = Math.max(0, Math.min(1100 - ch / 2, cy - ch / 4));
            for (int k = 0; k < 3; k++)
                out.drawImage(open[k].getSubimage(x0, y0, cw / 2, ch / 2), k * cw, frame * ch, cw, ch, null);
        }
        out.dispose();
        ImageIO.write(grid, "png", new File(output, "motion-mouth-" + who + ".png"));
    }

    private static void person(Graphics2D g, String who, int frame, double x, double y, double height,
            AcademyCharacters.Act act) {
        if ("fu".equals(who))
            AcademyCharacters.messengerPose(g, x, y, height, 0, frame, act);
        else
            AcademyCharacters.keeper(g, x, y, height, 0, frame, act);
    }

    /** The box in which two renders differ, or null when they are the same. */
    private static int[] difference(BufferedImage a, BufferedImage b) {
        int[] box = {a.getWidth(), a.getHeight(), -1, -1};
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    box[0] = Math.min(box[0], x);
                    box[1] = Math.min(box[1], y);
                    box[2] = Math.max(box[2], x);
                    box[3] = Math.max(box[3], y);
                }
        return box[2] < 0 ? null : box;
    }

    private static void cinema() throws Exception {
        AcademyCinematic scenes = edt(() -> new AcademyCinematic(false, () -> { }));
        JFrame stage = edt(() -> show(scenes));
        double[] times = {5, 15, 24, 28, 37};
        for (double t : times)
            ImageIO.write(edt(() -> { scenes.previewAt(t); return render(scenes); }), "png",
                    new File(output, "cinema-" + (int) (t * 10) + ".png"));
        edt(() -> { stage.dispose(); return null; });
        AcademyCinematic end = edt(() -> new AcademyCinematic(true, () -> { }));
        JFrame endStage = edt(() -> show(end));
        for (double t : new double[]{4, 14})
            ImageIO.write(edt(() -> { end.previewAt(t); return render(end); }), "png",
                    new File(output, "ending-" + (int) (t * 10) + ".png"));
        edt(() -> { endStage.dispose(); return null; });
    }

    /** Six consecutive 60 fps frames of the unfolded letter, cropped and enlarged side by side. */
    private static void letter() throws Exception {
        AcademyCinematic scenes = edt(() -> new AcademyCinematic(false, () -> { }));
        JFrame stage = edt(() -> show(scenes));
        int frames = 6;
        int cw = 300;
        int ch = 120;
        BufferedImage strip = new BufferedImage(cw * 2, ch * 2 * frames, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = strip.createGraphics();
        for (int i = 0; i < frames; i++) {
            final double t = 15 + i / 60.0;
            BufferedImage image = edt(() -> { scenes.previewAt(t); return render(scenes); });
            BufferedImage crop = image.getSubimage(560, 190, cw, ch);
            g.drawImage(crop, 0, i * ch * 2, cw * 2, ch * 2, null);
            if (i == 0)
                ImageIO.write(image, "png", new File(output, "letter-full.png"));
        }
        g.dispose();
        ImageIO.write(strip, "png", new File(output, "letter-frames.png"));
        edt(() -> { stage.dispose(); return null; });
    }

    /**
     * The torn pages one at a time: every page before a choice, then the seal on a refuted, a standing
     * and the proven choice, and one frame in the middle of a page turn.
     */
    private static void casebook(String root) throws Exception {
        LevelDefinition level = LevelDefinition.load(new File(root, "levels/03_archive_gate/level.properties"));
        Investigation inv = Investigation.load(level);
        ExperimentSession[] session = {new ExperimentSession(level.getInputs())};
        System.setProperty("academy.skipTransitions", "true");
        AcademyCasebook book = edt(() -> new AcademyCasebook(inv, () -> session[0], inv::choose));
        AcademyTheme.Backdrop host = edt(() -> {
            AcademyTheme.Backdrop b = new AcademyTheme.Backdrop(new java.awt.BorderLayout());
            b.setBorder(AcademyTheme.pad(60, 24, 18, 24));
            b.add(book);
            return b;
        });
        JFrame stage = edt(() -> show(host));
        edt(() -> { book.open(); book.refresh(false); return null; });
        for (int page = 0; page < book.pages(); page++) {
            final int p = page;
            shot(host, "case-page" + p, () -> {
                while (book.page() < p)
                    book.nextButton().doClick();
            });
        }
        try (ReferenceDevice ref = new ReferenceDevice(level)) {
            for (int c = 0; c < 3; c++)
                session[0].observe(c, ref.evaluate(session[0].toInputs(c)));
            shot(host, "case-refuted", () -> { book.chip(0).doClick(); book.refresh(false); });
            shot(host, "case-standing", () -> { book.chip(1).doClick(); book.refresh(false); });
            session[0].predictAndObserve(3, true, ref.evaluate(session[0].toInputs(3)));
            shot(host, "case-proven", () -> book.refresh(false));
        }
        System.setProperty("academy.skipTransitions", "false");
        edt(() -> { book.previousButton().doClick(); return null; });
        Thread.sleep(150);
        ImageIO.write(edt(() -> render(host)), "png", new File(output, "case-flip-back.png"));
        Thread.sleep(500);
        edt(() -> { book.nextButton().doClick(); return null; });
        Thread.sleep(110);
        ImageIO.write(edt(() -> render(host)), "png", new File(output, "case-flip.png"));
        Thread.sleep(500);
        ImageIO.write(edt(() -> render(host)), "png", new File(output, "case-after-flip.png"));
        edt(() -> { stage.dispose(); return null; });
        contactSheet("case-overview", "case-page0", "case-page2", "case-refuted", "case-flip", "case-standing",
                "case-proven");
    }

    /**
     * The academy's reply in every state: waiting, sealed, sent back with a slip, a loose seal, a missing
     * seal, and frames of a new reply sliding in and being stamped.
     */
    private static void result(String root) throws Exception {
        LevelDefinition level = LevelDefinition.load(new File(root, "levels/03_archive_gate/level.properties"));
        File fixtures = new File(root, "levels/03_archive_gate/fixtures");
        LevelValidator validator = new LevelValidator(level);
        ResultLetter.Clerk clerk = ResultLetter.Clerk.of(level);
        ResultLetter passed = clerk.reply(validator.validate(new File(fixtures, "and_correct.dig")));
        ResultLetter or = clerk.reply(validator.validate(new File(fixtures, "or_wrong.dig")));
        ResultLetter zero = clerk.reply(validator.validate(new File(fixtures, "const_zero.dig")));
        ResultLetter loose = clerk.reply(validator.validate(level.getTemplateFile()));
        ResultLetter missing = clerk.reply(validator.validate(new File(fixtures, "missing_port.dig")));
        System.setProperty("academy.skipTransitions", "true");
        AcademyLetter reply = edt(() -> {
            AcademyLetter l = new AcademyLetter(clerk.idle());
            javax.swing.JPanel controls = AcademyTheme.clear(new java.awt.BorderLayout());
            controls.add(AcademyTheme.label("已手动保存", AcademyTheme.font(java.awt.Font.PLAIN, 13), AcademyTheme.MUTED),
                    java.awt.BorderLayout.WEST);
            javax.swing.JPanel actions = AcademyTheme.clear(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
            actions.add(AcademyTheme.button("← 返回术式", AcademyTheme.Kind.PRIMARY));
            controls.add(actions, java.awt.BorderLayout.EAST);
            l.add(controls, java.awt.BorderLayout.SOUTH);
            return l;
        });
        AcademyTheme.Backdrop host = edt(() -> {
            AcademyTheme.Backdrop b = new AcademyTheme.Backdrop(new java.awt.BorderLayout());
            b.setBorder(AcademyTheme.pad(60, 24, 18, 24));
            b.add(reply);
            return b;
        });
        JFrame stage = edt(() -> show(host));
        shot(host, "result-idle", () -> { });
        shot(host, "result-pending", () -> reply.deliver(clerk.pending()));
        shot(host, "result-passed", () -> reply.deliver(passed));
        shot(host, "result-or", () -> reply.deliver(or));
        shot(host, "result-zero", () -> reply.deliver(zero));
        shot(host, "result-loose", () -> reply.deliver(loose));
        shot(host, "result-missing", () -> reply.deliver(missing));
        shot(host, "result-stale", () -> reply.deliver(clerk.stale()));
        System.setProperty("academy.skipTransitions", "false");
        long start = System.nanoTime();
        edt(() -> { reply.deliver(or); return null; });
        for (int ms : new int[]{120, 330, 760, 840, 1500}) {
            long wait = start + ms * 1000000L - System.nanoTime();
            if (wait > 0)
                Thread.sleep(wait / 1000000L);
            ImageIO.write(edt(() -> render(host)), "png", new File(output, "result-arrive" + ms + ".png"));
        }
        edt(() -> { stage.dispose(); return null; });
        contactSheet("result-overview", "result-idle", "result-passed", "result-or", "result-loose", "result-missing",
                "result-arrive330");
    }

    /**
     * The work notebook: empty, half filled, with a hint, complete with a wrong guess, in the repair stage,
     * and frames of rows observed while it was closed being written in.
     */
    private static void ledger(String root) throws Exception {
        LevelDefinition level = LevelDefinition.load(new File(root, "levels/03_archive_gate/level.properties"));
        Investigation inv = Investigation.load(level);
        List<String> stations = ExperimentGuide.stations(level);
        ExperimentGuide guide = new ExperimentGuide(stations);
        ExperimentSession s = new ExperimentSession(level.getInputs());
        System.setProperty("academy.skipTransitions", "true");
        AcademyLedger ledger = edt(() -> notebook(stations));
        AcademyTheme.Backdrop host = edt(() -> backdrop(ledger));
        JFrame stage = edt(() -> show(host));
        try (ReferenceDevice ref = new ReferenceDevice(level)) {
            shot(host, "ledger-empty", () -> ledger.update(s, 0, Arrays.asList(guide.next(s, 0), "门的回应会出现在这里。")));
            s.observe(0, ref.evaluate(s.toInputs(0)));
            s.observe(1, ref.evaluate(s.toInputs(1)));
            shot(host, "ledger-partial", () -> ledger.update(s, 1, Arrays.asList(guide.next(s, 1),
                    "已记录：" + ExperimentGuide.describe(stations, s, 1) + " → 门保持关闭（门开 = 0）。\n"
                            + "拿这组回应对照残页的说法：哪张和门说得不一样？想好了，就在「三张残页」里做出判断。")));
            shot(host, "ledger-hint", () -> ledger.update(s, 1, Arrays.asList(guide.next(s, 1),
                    "提示 2/3（方法）：每次只改变一个岗位的钥印，比较门的回应。")));
            s.observe(2, ref.evaluate(s.toInputs(2)));
            s.predictAndObserve(3, false, ref.evaluate(s.toInputs(3)));
            shot(host, "ledger-complete", () -> ledger.update(s, 3, Arrays.asList(guide.next(s, 3),
                    "这次观察给了我们新线索：你猜「我猜保持关闭」，但门打开了（门开 = 1）。已如实记入工作记录，对照记录再想想规律。")));
            shot(host, "ledger-rebuild", () -> ledger.update(s, -1, Arrays.asList(inv.discovered(s).getKnowledge(),
                    "连线：单击端点开始，移动鼠标（不用按住），在目标端点上单击结束；在空白处单击会拐弯。Esc 取消。")));
            System.out.println(edt(ledger::text));
            edt(() -> { stage.dispose(); return null; });

            ExperimentSession w = new ExperimentSession(level.getInputs());
            w.observe(0, ref.evaluate(w.toInputs(0)));
            AcademyLedger fresh = edt(() -> notebook(stations));
            AcademyTheme.Backdrop freshHost = edt(() -> backdrop(fresh));
            JFrame freshStage = edt(() -> show(freshHost));
            List<String> notes = Arrays.asList("这组报告还没观察过：点击「观察回应」，门的回应会记进工作记录。");
            edt(() -> { fresh.update(w, 0, notes); return null; });
            System.setProperty("academy.skipTransitions", "false");
            w.observe(1, ref.evaluate(w.toInputs(1)));
            w.observe(2, ref.evaluate(w.toInputs(2)));
            long start = System.nanoTime();
            edt(() -> { fresh.update(w, 2, notes); return null; });
            for (int ms : new int[]{150, 450, 700, 1000, 1600}) {
                long wait = start + ms * 1000000L - System.nanoTime();
                if (wait > 0)
                    Thread.sleep(wait / 1000000L);
                ImageIO.write(edt(() -> render(freshHost)), "png", new File(output, "ledger-write" + ms + ".png"));
            }
            System.out.println("still writing after 1.6 s: " + edt(fresh::isWriting));
            edt(() -> { freshStage.dispose(); return null; });
        }
        contactSheet("ledger-overview", "ledger-empty", "ledger-partial", "ledger-complete", "ledger-rebuild",
                "ledger-write450", "ledger-write1000");
    }

    private static AcademyLedger notebook(List<String> stations) {
        AcademyLedger l = new AcademyLedger(stations);
        javax.swing.JPanel controls = AcademyTheme.clear(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        controls.add(AcademyTheme.button("返回现场 →", AcademyTheme.Kind.PRIMARY));
        l.add(controls, java.awt.BorderLayout.SOUTH);
        return l;
    }

    private static AcademyTheme.Backdrop backdrop(JComponent page) {
        AcademyTheme.Backdrop b = new AcademyTheme.Backdrop(new java.awt.BorderLayout());
        b.setBorder(AcademyTheme.pad(60, 24, 18, 24));
        b.add(page);
        return b;
    }

    /**
     * The lamp chapters' dialogue, one shot per line, so the pose each line names can be read against
     * what the line says; then how Fu, Luo and Glimmer enter and speak. Then the second shot of each
     * chapter's opening cutscene: only the archive may show Fu holding the letter open; and Fu walking
     * into the first shot of it.
     */
    private static void story(String root) throws Exception {
        System.setProperty("academy.skipTransitions", "true");
        for (String id : new String[]{"01_report_light", "02_reminder_light", "04_registry_desk", "05_star_lamps"}) {
            LevelDefinition level = LevelDefinition.load(new File(root, "levels/" + id + "/level.properties"));
            Investigation inv = Investigation.load(level);
            AcademyStory story = edt(AcademyStory::new);
            AcademyTheme.Backdrop host = edt(() -> backdrop(story));
            JFrame stage = edt(() -> show(host));
            List<String> names = new java.util.ArrayList<>();
            for (int part = 0; part < 2; part++) {
                boolean clue = part == 1;
                List<Investigation.Line> lines = clue ? inv.getClue() : inv.getBriefing();
                String heading = clue ? inv.word("clueHeading", "校准页") : "疑点";
                edt(() -> {
                    story.open(heading, lines, AcademyLampScene.backdrop(level.getScene(), clue), "完", clue, () -> { });
                    return null;
                });
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0)
                        edt(() -> { story.nextButton().doClick(); return null; });
                    Investigation.Line line = lines.get(i);
                    String name = "story-" + id.substring(0, 2) + "-" + (clue ? "clue" : "brief") + (i + 1);
                    // long after the line was shown: everyone has arrived and the mouth is shut again
                    shot(host, name, () -> story.previewAt(30));
                    names.add(name);
                    System.out.println(name + "  " + line.getSpeaker() + " " + line.getPose() + "  page " + story.page());
                }
            }
            edt(() -> { stage.dispose(); return null; });
            contactSheet("story-" + id.substring(0, 2), names.toArray(new String[0]));
        }
        entrances(root);
        System.setProperty("academy.skipTransitions", "false");
        String[] chapters = {"01_report_light", "02_reminder_light", "03_archive_gate", "04_registry_desk",
                "05_star_lamps"};
        for (String id : chapters) {
            LevelDefinition level = LevelDefinition.load(new File(root, "levels/" + id + "/level.properties"));
            AcademyCinematic.Script script = new AcademyCinematic.Script(Investigation.story(level),
                    level.isLampScene() ? level.getScene() : null);
            AcademyCinematic scenes = edt(() -> new AcademyCinematic(false, script, () -> { }));
            JFrame stage = edt(() -> show(scenes));
            ImageIO.write(edt(() -> { scenes.previewAt(15); return render(scenes); }), "png",
                    new File(output, "story-cinema-" + id.substring(0, 2) + ".png"));
            System.out.println("story-cinema-" + id.substring(0, 2) + "  shot " + edt(scenes::shotIndex));
            edt(() -> { stage.dispose(); return null; });
        }
        contactSheet("story-cinema", "story-cinema-01", "story-cinema-02", "story-cinema-03", "story-cinema-04",
                "story-cinema-05");
        AcademyCinematic opening = edt(() -> new AcademyCinematic(false, () -> { }));
        JFrame stage = edt(() -> show(opening));
        double[] times = {.3, .8, 1.3, 1.8, 2.3, 2.8, 3.3, 4.2};
        String[] names = new String[times.length];
        for (int i = 0; i < times.length; i++) {
            double t = times[i];
            names[i] = "walk-cinema-" + (int) (t * 10);
            ImageIO.write(edt(() -> { opening.previewAt(t); return render(opening); }), "png",
                    new File(output, names[i] + ".png"));
        }
        edt(() -> { stage.dispose(); return null; });
        contactSheet("walk-cinema", names);
    }

    /**
     * The first lines of the first chapter's briefing as they are shown: Fu, then Luo walking in and
     * starting to speak, Glimmer flying in. The mouth of each walker is also cropped at its real size,
     * shut and at its widest in the first syllables.
     */
    private static void entrances(String root) throws Exception {
        System.setProperty("academy.skipTransitions", "true");
        AcademyAtmosphere.motion(true);
        LevelDefinition level = LevelDefinition.load(new File(root, "levels/01_report_light/level.properties"));
        List<Investigation.Line> lines = Investigation.load(level).getBriefing();
        AcademyStory story = edt(AcademyStory::new);
        AcademyTheme.Backdrop host = edt(() -> backdrop(story));
        JFrame stage = edt(() -> show(host));
        edt(() -> {
            story.open("疑点", lines, AcademyLampScene.backdrop(level.getScene(), false), "完", false, () -> { });
            return null;
        });
        String[] who = {"fu", "luo", "glimmer"};
        int[] first = {0, 3, 5};
        for (int k = 0; k < who.length; k++) {
            while (edt(story::page) < first[k])
                edt(() -> { story.nextButton().doClick(); return null; });
            String text = lines.get(first[k]).getText();
            // the widest the mouth opens in its first second of speaking, walk and settling done
            double widest = 0;
            for (double t = 0; t < 1; t += .005)
                if (AcademyCharacters.mouth(text, t) > AcademyCharacters.mouth(text, widest))
                    widest = t;
            double[] times = {.25, .5, .75, 1.0, 1.25, 1.5, 1.85, 2.0 + widest};
            String[] names = new String[times.length];
            for (int i = 0; i < times.length; i++) {
                double t = times[i];
                names[i] = "entrance-" + who[k] + "-" + i;
                shot(host, names[i], () -> story.previewAt(t));
            }
            contactSheet("entrance-" + who[k], names);
            System.out.printf("entrance-%s  mouth %.2f at %.2f s%n", who[k], AcademyCharacters.mouth(text, widest),
                    2.0 + widest);
            if (k < 2) {
                String person = who[k];
                shot(host, "entrance-" + person + "-shut", () -> story.previewAt(30));
                BufferedImage shut = ImageIO.read(new File(output, "entrance-" + person + "-shut.png"));
                BufferedImage open = ImageIO.read(new File(output, names[times.length - 1] + ".png"));
                Investigation.Line line = lines.get(first[k]);
                int pose = AcademyCharacters.namedPose(line.getSpeaker(), line.getPose());
                java.awt.Point mouth = edt(() -> mouthOnScreen(host, person, pose));
                System.out.println("entrance-" + person + " mouth at " + mouth);
                if (mouth != null) {
                    BufferedImage pair = new BufferedImage(2 * 4 * 60 + 8, 4 * 40, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = pair.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(shut.getSubimage(mouth.x - 30, mouth.y - 20, 60, 40), 0, 0, 240, 160, null);
                    g.drawImage(open.getSubimage(mouth.x - 30, mouth.y - 20, 60, 40), 248, 0, 240, 160, null);
                    g.dispose();
                    ImageIO.write(pair, "png", new File(output, "entrance-mouth-" + who[k] + ".png"));
                }
            }
        }
        edt(() -> { stage.dispose(); return null; });
    }

    /**
     * Where the mouth of Fu or Luo is when they stand speaking in the dialogue shown by {@code host}:
     * the same figure is drawn apart with the mouth shut and open, at the place and size the stage
     * uses, and the centre of what changes is mapped back onto the host.
     */
    private static java.awt.Point mouthOnScreen(JComponent host, String who, int pose) {
        JComponent stage = find(host, "$Stage");
        if (stage == null)
            return null;
        int w = stage.getWidth();
        int h = stage.getHeight();
        int ph = (int) (h * 0.92);
        BufferedImage[] drawn = new BufferedImage[2];
        for (int k = 0; k < 2; k++) {
            drawn[k] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = drawn[k].createGraphics();
            person(g, who, pose, "fu".equals(who) ? w * 0.12 : w * 0.10, h - ph, ph,
                    AcademyCharacters.Act.speaking(k));
            g.dispose();
        }
        int[] box = difference(drawn[0], drawn[1]);
        if (box == null)
            return null;
        return SwingUtilities.convertPoint(stage, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2, host);
    }

    private static JComponent find(java.awt.Container parent, String classSuffix) {
        for (java.awt.Component c : parent.getComponents()) {
            if (c.getClass().getName().endsWith(classSuffix))
                return (JComponent) c;
            if (c instanceof java.awt.Container) {
                JComponent found = find((java.awt.Container) c, classSuffix);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    /** Puts finished shots at half size into one image, two per row, so they can be reviewed at a glance. */
    private static void contactSheet(String name, String... shots) throws Exception {
        int rows = (shots.length + 1) / 2;
        BufferedImage sheet = new BufferedImage(1280 + 12, rows * 366 + 6, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(0x0B111C));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < shots.length; i++)
            g.drawImage(ImageIO.read(new File(output, shots[i] + ".png")), 6 + i % 2 * 646, 6 + i / 2 * 366, 640, 360,
                    null);
        g.dispose();
        ImageIO.write(sheet, "png", new File(output, name + ".png"));
    }

    private static void shot(JComponent host, String name, Runnable action) throws Exception {
        ImageIO.write(edt(() -> {
            action.run();
            host.validate();
            return render(host);
        }), "png", new File(output, name + ".png"));
    }

    static JFrame show(JComponent panel) {
        JFrame frame = new JFrame("Academy developer screenshots");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(panel);
        panel.setPreferredSize(new java.awt.Dimension(1280, 720));
        frame.pack();
        frame.setLocation(30, 30);
        frame.setVisible(true);
        return frame;
    }

    static BufferedImage render(JComponent panel) {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        panel.printAll(g);
        g.dispose();
        return image;
    }

    static <T> T edt(Callable<T> action) throws Exception {
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
