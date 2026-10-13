/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.LinkedHashMap;
import java.util.Map;

/** Layered runtime character motion. It reads no model and cannot produce circuit outputs. */
final class AcademyCharacters {
    /**
     * Per pose: left wing, right wing, ribbon tail. Each limb is pivot x/y, centre x/y, full-weight radius
     * and fade width on the 640-unit pose grid. The weight is zero at the pivot and grows smoothly towards
     * the centre, so a limb bends out of the body instead of being cut from it. Rows 0-3 are the first
     * sheet, rows 4-7 the second.
     */
    private static final double[][][] LIMBS = {
        {{285, 315, 165, 290, 110, 45}, {450, 320, 530, 315, 90, 40}, {305, 462, 265, 540, 80, 40}},
        {{262, 375, 180, 415, 85, 40}, {395, 255, 470, 160, 115, 45}, {308, 480, 290, 545, 70, 40}},
        {{250, 255, 170, 190, 130, 45}, {420, 255, 510, 185, 125, 45}, {318, 440, 280, 500, 75, 40}},
        {{265, 265, 165, 255, 105, 45}, {430, 255, 500, 220, 100, 45}, {300, 422, 265, 480, 75, 40}},
        {{240, 330, 160, 300, 85, 40}, {410, 330, 490, 300, 85, 40}, {295, 440, 260, 500, 55, 35}},
        {{262, 375, 195, 410, 65, 35}, {405, 285, 465, 210, 85, 40}, {330, 455, 320, 520, 55, 35}},
        {{275, 330, 215, 360, 55, 30}, {410, 330, 450, 350, 45, 30}, {300, 435, 275, 500, 55, 35}},
        {{285, 300, 210, 230, 85, 40}, {400, 300, 465, 225, 85, 40}, {295, 420, 255, 445, 55, 35}},
    };
    /**
     * Per pose of Glimmer: how fast and how far the wings beat. 好奇 holds its wings folded, so they
     * only tremble; 困惑 flutters them in a hurry.
     */
    private static final double[][] FLUTTER = {
        {3.1, .075}, {3.1, .075}, {3.1, .075}, {4.6, .075}, {2.6, .06}, {3.1, .065}, {3.6, .035}, {4.2, .07}};
    /**
     * Per pose of Glimmer: the resting pupils (left x/y, right x/y) on the 640-unit grid, the size of the
     * eyes against the first sheet's, and where the closed lid sits relative to the pupil. A null row is
     * a pose painted with its eyes already closed in a smile; nothing is drawn over those.
     */
    private static final double[][] EYES = {
        {344, 309, 410, 300, 1, 0, 0}, {276, 330, 336, 292, 1, 0, 0}, {310, 262, 379, 260, 1, 0, 0}, null,
        {290.4, 297.8, 351.4, 297.8, .85, 5.6, 3.2}, null, {305.4, 289.8, 368.4, 279.8, .85, 5.6, 3.2}, null};
    /**
     * The second sheet paints Glimmer smaller inside its cell than the first: its hood ring, body and eye
     * spacing measure 0.85-0.95 of the first sheet's. Wings are left out of the comparison, because the
     * poses spread them differently.
     */
    private static final double SECOND_ZOOM = 1.12;
    private static final double MAX_BEND = .11;
    /**
     * Where each person's mouth is painted, per frame: centre x/y on the 640-unit grid, width, the tilt of
     * the mouth line in degrees (positive runs down to the right), and how far it may open as a share of
     * its width. A grin that is painted open already opens less.
     */
    private static final Map<String, double[][]> MOUTHS = new java.util.HashMap<>();
    /**
     * The legs a person walks on in frame 0, on the 640-unit grid: hip x/y and ankle x/y of the left
     * and of the right leg, the half width of the leg at the hip and at the foot, where the swing starts
     * taking the leg along, the knee line, and the hip line above which the body rides the step. The swing
     * starts below anything a leg must not drag with it - Fu's satchel hangs down to 400.
     */
    private static final Map<String, double[]> LEGS = new java.util.HashMap<>();
    /** One step, as a share of the height the person is drawn at. */
    private static final double STEP = .11;
    /** How far a leg swings out from the hip at a full stride, in radians. */
    private static final double SWING = .055;
    /** How high the swinging foot is lifted, on the 640-unit grid. */
    private static final double LIFT = 9;
    /** How far the body sinks when the legs are apart, on the 640-unit grid. */
    private static final double BOB = 3;
    /** Sliced and measured sheets, keyed by character and sheet suffix. A null value means no such sheet. */
    private static final Map<String, Pose[]> POSES = new java.util.HashMap<>();
    /**
     * The poses a story line may ask for, per speaker, in frame order: 0-3 are the first sheet, 4-7
     * the second. Each name says what the painting shows, so a line is matched to a pose by what it
     * says rather than by where it falls in the dialogue. Fu's 读信 holds the letter open; it belongs
     * to the archive, where the letter is read, and never to the lamp chapters, where it is sealed.
     */
    private static final Map<String, String[]> NAMED_POSES = new java.util.HashMap<>();

    static {
        NAMED_POSES.put("芙", new String[]{"持信", "读信", "递信", "摊手", "关切", "回头", "按信", "出发"});
        NAMED_POSES.put("洛", new String[]{"抱书", "翻书", "端详", "递页", "背手", "扶镜", "回避", "讲解"});
        NAMED_POSES.put("微光", new String[]{"张翼", "侧飞", "举翼", "欢喜", "专注", "偷看", "好奇", "困惑"});
        MOUTHS.put("fu", new double[][]{
            {355.6, 95.4, 14.5, -16, .3}, {256.7, 107.2, 11, 0, .3}, {380.6, 90.1, 20, -10, .2},
            {302.2, 83.7, 16, -15, .2}, {308, 123.9, 10.4, 0, .3}, {326.8, 132.8, 10.4, 20, .3},
            {312.3, 130.2, 11.3, 0, .3}, {281.5, 117.7, 19.8, -12, .2}});
        MOUTHS.put("luo", new double[][]{
            {367.4, 71.3, 13.6, -5, .3}, {266.2, 78.9, 13.6, 20, .3}, {337, 82.5, 13.6, 3, .3},
            {324.4, 67.4, 18.7, -5, .3}, {374, 165.1, 8.5, 15, .3}, {305.1, 159.5, 11.3, 0, .3},
            {331.2, 149.2, 8.5, 25, .3}, {324.2, 140.2, 12.3, 0, .3}});
        LEGS.put("fu", new double[]{319, 255, 315, 600, 368, 255, 412, 600, 30, 52, 395, 470, 260});
        LEGS.put("luo", new double[]{312, 320, 328, 600, 366, 320, 402, 605, 30, 50, 335, 470, 320});
    }
    private static final Map<Long, Rig> RIGS = new LinkedHashMap<Long, Rig>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Rig> eldest) {
            return size() > 8;
        }
    };

    private AcademyCharacters() {
    }

    static void spirit(Graphics2D g, double x, double y, double height, double seconds, boolean responding) {
        spirit(g, x, y, height, seconds, responding, null);
    }

    static void spirit(Graphics2D g, double x, double y, double height, double seconds, boolean responding, Point2D gaze) {
        spirit(g, x, y, height, seconds, responding ? 2 : 0, gaze);
    }

    /**
     * Glimmer in one of its poses, see {@link #namedPose(String, String)}; with only the old single
     * sprite installed, pose 2 counts as responding and every other pose as resting.
     */
    static void spirit(Graphics2D g, double x, double y, double height, double seconds, int pose, Point2D gaze) {
        if (AcademyArt.get("v2/characters/glimmer-poses.png") != null) {
            spiritPose(g, x, y, height, seconds, pose, gaze);
            return;
        }
        boolean responding = pose == 2;
        BufferedImage sprite = AcademyArt.get("characters/glimmer-spirit.png");
        if (sprite == null)
            return;
        double motion = AcademyAtmosphere.motion() ? 1 : 0;
        double scale = height / sprite.getHeight() * (1 + motion * 0.015 * Math.sin(seconds * 2.0));
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform place = new AffineTransform();
        place.translate(x + motion * 4 * Math.sin(seconds * 0.8), y + motion * 9 * Math.sin(seconds * 1.7));
        place.rotate(motion * 0.04 * Math.sin(seconds * 1.2));
        place.scale(scale, scale);
        place.translate(-sprite.getWidth() / 2.0, -sprite.getHeight() / 2.0);
        p.transform(place);
        // The fallback sprite is drawn whole: splitting a single painting into parts always shows a seam.
        p.drawImage(sprite, 0, 0, null);
        Point2D eyeTarget = null;
        if (gaze != null)
            try {
                eyeTarget = place.inverseTransform(gaze, null);
            } catch (java.awt.geom.NoninvertibleTransformException e) {
                // A zero-sized view has no gaze target.
            }
        pupil(p, 769, 535, 8, 12, eyeTarget);
        pupil(p, 896, 590, 6, 9, eyeTarget);
        p.dispose();
        AcademyAtmosphere.lamp(g, (float) x, (float) (y + height * 0.1), (float) (height * 0.38), responding ? 0.45f : 0.18f, seconds);
    }

    private static void pupil(Graphics2D g, double x, double y, double radiusX, double radiusY, Point2D target) {
        double dx = target == null ? 0 : target.getX() - x;
        double dy = target == null ? 0 : target.getY() - y;
        double distance = Math.hypot(dx, dy);
        double strength = Math.min(1, distance / 220);
        double px = x + (distance < 1 ? 0 : dx / distance * radiusX * strength);
        double py = y + (distance < 1 ? 0 : dy / distance * radiusY * strength);
        g.setColor(new Color(8, 31, 52, 230));
        g.fill(new Ellipse2D.Double(px - radiusX, py - radiusY, radiusX * 2, radiusY * 2));
        g.setColor(new Color(224, 251, 255, 230));
        g.fill(new Ellipse2D.Double(px - radiusX * 0.35, py - radiusY * 0.45, radiusX * 0.55, radiusX * 0.55));
    }

    static void messenger(Graphics2D g, double x, double y, double height, double seconds) {
        if (AcademyArt.get("v2/characters/fu-poses.png") != null) {
            messengerPose(g, x, y, height, seconds, 0);
            return;
        }
        BufferedImage sprite = AcademyArt.get("characters/fu-messenger.png");
        if (sprite == null)
            return;
        double motion = AcademyAtmosphere.motion() ? 1 : 0;
        double scale = height / sprite.getHeight();
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        p.translate(x, y + motion * 2 * Math.sin(seconds * 1.6));
        p.scale(scale * (1 + motion * 0.003 * Math.sin(seconds * 1.6)), scale);
        p.drawImage(sprite, 0, 0, null);
        p.dispose();
    }

    static BufferedImage pose(String name, int frame) {
        Pose pose = poseFor(name, frame);
        return pose == null ? null : pose.image;
    }

    /**
     * The pose to draw, with the measurements that make it the same size as every other pose of the
     * same character.
     * <p>
     * Frames 0-3 come from {@code <name>-poses.png}; frames 4-7 come from the second sheet
     * {@code <name>-poses-b.png} when it is installed. The two sheets do not paint the figure at the
     * same size inside their cells - the first sheets run the figure to within a couple of percent of
     * the cell edge, the delivered sheets leave about a fifth of the cell empty - so a pose scaled by
     * its cell height would change size when the pose changed. The anchor below is measured from the
     * sheet instead: one figure height and one centre line per sheet, taken across the four poses, so
     * nothing inside a sheet resizes or sideslips the figure and the two sheets agree on how tall the
     * character is. The foot line is the one measurement that stays per pose, because it is a contact
     * with the ground rather than a property of the silhouette.
     *
     * @param name  character name, e.g. "fu"
     * @param frame any frame; the four-pose cycle repeats when no second sheet is installed
     * @return the pose, or null when the character has no art at all
     */
    private static Pose poseFor(String name, int frame) {
        int index = Math.floorMod(frame, 8);
        if (index >= 4) {
            Pose second = cached(name, "-poses-b", index - 4);
            // Without the second sheet the four-pose cycle simply repeats, as it did before.
            if (second != null)
                return second;
            index = Math.floorMod(frame, 4);
        }
        return cached(name, "-poses", index);
    }

    /** The cached pose, or null when this character has no such sheet at all. */
    private static Pose cached(String name, String suffix, int cell) {
        Pose[] poses = sheet(name, suffix);
        return poses == null ? null : poses[cell];
    }

    /** The sliced and measured sheet, or null when it is not installed. */
    private static Pose[] sheet(String name, String suffix) {
        String key = name + suffix;
        Pose[] poses = POSES.get(key);
        if (poses == null && !POSES.containsKey(key)) {
            poses = slice(name, suffix);
            POSES.put(key, poses);
        }
        return poses;
    }

    /** Slices a sheet into its four cells and measures them, or returns null when it is not installed. */
    private static Pose[] slice(String name, String suffix) {
        BufferedImage atlas = AcademyArt.get("v2/characters/" + name + suffix + ".png");
        if (atlas == null || atlas.getWidth() < 2 || atlas.getHeight() < 2)
            return null;
        int w = atlas.getWidth() / 2;
        int h = atlas.getHeight() / 2;
        int[][] boxes = new int[4][];
        int[] figure = new int[4];
        int[] foot = new int[4];
        int[] centre = new int[4];
        for (int cell = 0; cell < 4; cell++) {
            boxes[cell] = painted(atlas, cell % 2 * w, cell / 2 * h, w, h);
            if (boxes[cell] != null) {
                figure[cell] = boxes[cell][3] - boxes[cell][1] + 1;
                foot[cell] = boxes[cell][3];
                centre[cell] = (boxes[cell][0] + boxes[cell][2]) / 2;
            }
        }
        // How tall the figure is, and where the character stands, are two different questions and the
        // four poses answer them differently.
        //
        // The size has to be one value for the whole sheet. Scaling each pose by its own box would let
        // a raised arm or a lifted wing make the character grow, because the box is the silhouette and
        // the silhouette moves when the pose does; the median is what the four poses agree on.
        int reference = median(figure, h);
        // The foot line is the opposite case: it is a contact with the ground, so each pose has to
        // stand on the line itself. Measured across the delivered sheets the four poses put their feet
        // up to 29 px apart inside one cell, so anchoring all of them on one row leaves three of the
        // four hanging in the air by that much. The centre stays sheet-wide for the same reason the
        // height does - a silhouette that leans must not walk the character sideways.
        int baseline = median(foot, h);
        int middle = median(centre, w / 2);
        // The scenes were composed against the first sheet, so the first sheet says where in the
        // requested box the figure belongs; any other sheet is drawn to match it.
        Anchor composed = new Anchor(reference / (double) h, baseline / (double) h, middle / (double) h);
        boolean second = !"-poses".equals(suffix);
        if (second) {
            Pose[] first = sheet(name, "-poses");
            if (first != null)
                for (Pose pose : first)
                    if (pose != null) {
                        composed = pose.composed;
                        break;
                    }
        }
        Pose[] poses = new Pose[4];
        for (int cell = 0; cell < 4; cell++) {
            if (boxes[cell] == null)
                continue;
            poses[cell] = new Pose(atlas.getSubimage(cell % 2 * w, cell / 2 * h, w, h),
                    new int[]{reference, foot[cell], middle}, composed, name, cell + (second ? 4 : 0));
        }
        return poses;
    }

    /**
     * The middle of the four measurements, as the average of the two central ones. Taking
     * {@code sorted[2]} would pick the second largest rather than the middle, and the poses on the
     * other side of it would hang off the foot line by half the art's own spread.
     */
    private static int median(int[] values, int fallback) {
        int[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int middle = (sorted[1] + sorted[2]) / 2;
        return middle > 0 ? middle : fallback;
    }

    /** The box of pixels with a real alpha, in cell coordinates, or null when the cell is blank. */
    private static int[] painted(BufferedImage image, int ox, int oy, int w, int h) {
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

    /**
     * One pose, the sheet-wide measurements it is drawn with, and where the scenes expect its figure.
     * It also keeps the rigs that let it walk and speak, built the first time they are needed.
     */
    private static final class Pose {
        private final BufferedImage image;
        private final int figureHeight;
        private final int foot;
        private final int centre;
        private final Anchor composed;
        private final String name;
        private final int frame;
        private Rig walk;
        private Rig mouth;
        private double[] opening;
        private boolean walkBuilt;
        private boolean mouthBuilt;

        /**
         * @param measured figure height, foot line and centre line, in pixels of the cell
         */
        private Pose(BufferedImage image, int[] measured, Anchor composed, String name, int frame) {
            this.image = image;
            this.figureHeight = measured[0];
            this.foot = measured[1];
            this.centre = measured[2];
            this.composed = composed;
            this.name = name;
            this.frame = frame;
        }

        /** The legs of this pose, or null when it does not walk. Built the first time it walks. */
        private Rig walk() {
            if (!walkBuilt) {
                walkBuilt = true;
                double[] legs = LEGS.get(name);
                if (legs != null && frame == 0)
                    walk = new Rig(image, null, image.getWidth() / 640.0,
                            new double[][]{{legs[0], legs[1]}, {legs[4], legs[5]}, {0, 0}, {0, 0}, {0, 0}},
                            legs(legs), SWING * (Math.max(legs[3] - legs[1], legs[7] - legs[5]) + 60) + LIFT + BOB);
            }
            return walk;
        }

        /** The mouth of this pose, or null when it has none. Built the first time it speaks. */
        private Rig mouth() {
            if (!mouthBuilt) {
                mouthBuilt = true;
                double[][] mouths = MOUTHS.get(name);
                if (mouths != null && frame < mouths.length) {
                    mouth = mouthRig(image, image.getWidth() / 640.0, mouths[frame]);
                    opening = opening(mouths[frame]);
                }
            }
            return mouth;
        }
    }

    /**
     * Where the first sheet paints its figure, as fractions of its cell height: how tall, where the
     * foot line is, and where the middle is. The cells of the first sheets are square, so one unit
     * serves both directions.
     */
    private static final class Anchor {
        private final double figure;
        private final double foot;
        private final double centre;

        private Anchor(double figure, double foot, double centre) {
            this.figure = figure;
            this.foot = foot;
            this.centre = centre;
        }
    }

    /**
     * What a person does while holding a pose: stand, walk or speak. Walking and speaking show only while
     * motion is on; with reduced motion every person stands still with a closed mouth.
     */
    static final class Act {
        /** Standing with the mouth closed. */
        static final Act STILL = new Act(0, 0, 0);
        private final double travelled;
        private final double stride;
        private final double mouth;

        private Act(double travelled, double stride, double mouth) {
            this.travelled = travelled;
            this.stride = Math.max(0, Math.min(1, stride));
            this.mouth = Math.max(0, Math.min(1, mouth));
        }

        /**
         * Walking to the right. Only the standing poses 持信 and 抱书 walk; any other pose stands.
         *
         * @param travelled how far the person has come, in the heights they are drawn at; the legs follow
         *                  it, so the steps keep pace with the ground whatever the speed
         * @param stride    0 standing to 1 a full stride; easing it in and out starts and stops the walk
         * @return the act
         */
        static Act walking(double travelled, double stride) {
            return new Act(travelled, stride, 0);
        }

        /**
         * @param mouth how far the mouth is open, 0 closed to 1 fully open
         * @return the act
         */
        static Act speaking(double mouth) {
            return new Act(0, 0, mouth);
        }
    }

    /**
     * @param speaker the speaker of a story line, e.g. "芙"
     * @param pose    the pose the line names
     * @return the frame, or -1 when the speaker has no pose of that name
     */
    static int namedPose(String speaker, String pose) {
        String[] names = NAMED_POSES.get(speaker);
        if (names != null && pose != null)
            for (int frame = 0; frame < names.length; frame++)
                if (names[frame].equals(pose))
                    return frame;
        return -1;
    }

    static void messengerPose(Graphics2D g, double x, double y, double height, double seconds, int frame) {
        messengerPose(g, x, y, height, seconds, frame, Act.STILL);
    }

    static void messengerPose(Graphics2D g, double x, double y, double height, double seconds, int frame, Act act) {
        portrait(g, poseFor("fu", frame), x, y, height, seconds, act);
    }

    static void keeper(Graphics2D g, double x, double y, double height, double seconds, int frame) {
        keeper(g, x, y, height, seconds, frame, Act.STILL);
    }

    static void keeper(Graphics2D g, double x, double y, double height, double seconds, int frame, Act act) {
        portrait(g, poseFor("luo", frame), x, y, height, seconds, act);
    }

    /**
     * Draws a pose where the scenes expect it. Every scene was composed against the first sheet drawn
     * cell first - the cell's top left corner on {@code (x, y)}, the cell scaled to {@code height} -
     * and the fallback sprite above is placed the same way, so that stays the contract. Inside that
     * box the figure is placed by measurement rather than by the cell, so a sheet with different
     * padding paints the same character in the same place.
     */
    private static void portrait(Graphics2D g, Pose pose, double x, double y, double height, double seconds, Act act) {
        if (pose == null)
            return;
        Graphics2D p = (Graphics2D) g.create();
        boolean motion = AcademyAtmosphere.motion();
        double t = motion ? seconds : 0;
        Anchor at = pose.composed;
        // The figure comes out as tall as the first sheet paints it in this box, whatever the padding
        // of the sheet the pose was taken from.
        double scale = height * at.figure / pose.figureHeight;
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Each pose stands on the first sheet's foot line, and its sheet's middle sits on the first
        // sheet's middle. For a pose of the first sheet this is the cell corner on (x, y) again, apart
        // from the few pixels by which its own feet differ from the others'.
        Rig walk = motion && act.stride > 0 ? pose.walk() : null;
        // While walking the breath is left out: the step already moves the body.
        double breath = walk == null ? Math.sin(t * 1.25) : 0;
        p.translate(x + height * at.centre - pose.centre * scale,
                y + height * at.foot - pose.foot * scale + breath * 2);
        p.scale(scale * (1 + .002 * breath), scale);
        if (walk != null) {
            p.drawImage(walk.bend(gait(act)), 0, 0, null);
        } else {
            p.drawImage(pose.image, 0, 0, null);
            Rig mouth = motion && act.mouth > 0 ? pose.mouth() : null;
            if (mouth != null) {
                double[] o = pose.opening;
                p.drawImage(mouth.bend(new double[]{0, o[0] * act.mouth, o[1] * act.mouth}), mouth.left, mouth.top, null);
            }
        }
        p.dispose();
    }

    /**
     * The motion of the five walking limbs for one moment of the walk: both legs turn about their hips in
     * opposite directions, the foot that swings forward is lifted, and the body sinks a little while the
     * legs are apart. One step is half a turn of the phase.
     */
    private static double[] gait(Act act) {
        double phase = Math.PI * act.travelled / STEP;
        double a = act.stride;
        // A negative turn moves a foot to the right, the way the person walks.
        double swing = -SWING * a * Math.sin(phase);
        double cos = Math.cos(phase);
        double sin = Math.sin(phase);
        return new double[]{
            swing, 0, 0,
            -swing, 0, 0,
            0, 0, -LIFT * a * Math.max(0, cos),
            0, 0, -LIFT * a * Math.max(0, -cos),
            0, 0, BOB * a * sin * sin};
    }

    /**
     * The walking limbs of frame 0: each leg turns about its hip, each foot can be lifted from the knee
     * down, and the body above the hips can sink. A leg is a capsule from hip to ankle that widens
     * towards the foot; where the two capsules meet, a pixel belongs to the nearer leg, with a soft border
     * so the cloth between the legs stretches instead of tearing.
     */
    private static Field legs(double[] l) {
        double kneeLine = l[11];
        return (x, y, weights) -> {
            double[] left = capsule(x, y, l[0], l[1], l[2], l[3]);
            double[] right = capsule(x, y, l[4], l[5], l[6], l[7]);
            double inLeft = 1 - smooth((left[1] - radius(left[0], l)) / 18);
            double inRight = 1 - smooth((right[1] - radius(right[0], l)) / 18);
            double dl = Math.pow(left[1], 4);
            double dr = Math.pow(right[1], 4);
            double share = dl + dr == 0 ? .5 : dr / (dl + dr);
            double swing = smooth((y - l[10]) / (kneeLine - l[10]));
            double lift = smooth((y - kneeLine) / (Math.max(l[3], l[7]) - 10 - kneeLine));
            weights[0] = inLeft * share * swing;
            weights[1] = inRight * (1 - share) * swing;
            weights[2] = inLeft * share * lift;
            weights[3] = inRight * (1 - share) * lift;
            weights[4] = 1 - smooth((y - l[12]) / (kneeLine - l[12]));
        };
    }

    /** The half width of a leg at a point along it: the hip width down to the knee, widening to the foot. */
    private static double radius(double along, double[] l) {
        return l[8] + (l[9] - l[8]) * smooth((along - .6) / .4);
    }

    /** Where along a segment a point lies (0 at the start, 1 at the end) and how far from it. */
    private static double[] capsule(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double along = ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy);
        double t = Math.max(0, Math.min(1, along));
        return new double[]{along, Math.hypot(x - ax - t * dx, y - ay - t * dy)};
    }

    /**
     * A mouth that opens: the painted mouth line is stretched downwards in its middle, so its own dark
     * colour fills the opening, and the lip below is pressed back together before the chin, which
     * therefore keeps its outline. The stretch grows linearly across the line, so no pixel ever folds
     * over another. The measured centre is good to a unit or two and a line is only two or three units
     * thick, so the line is found again in the painting: the darkest row across the middle of the mouth.
     */
    private static Rig mouthRig(BufferedImage image, double unit, double[] m) {
        double width = m[2];
        double angle = Math.toRadians(m[3]);
        double ux = Math.cos(angle);
        double uy = Math.sin(angle);
        double nx = -uy;
        double ny = ux;
        double open = Math.max(3, m[4] * width);
        double[] profile = new double[33];
        for (int i = 0; i < profile.length; i++) {
            double s = -4 + i * .25;
            double sum = 0;
            for (int k = -2; k <= 2; k++) {
                double a = k * .1 * width;
                sum += luminance(image, (m[0] + a * ux + s * nx) * unit, (m[1] + a * uy + s * ny) * unit);
            }
            profile[i] = sum / 5;
        }
        int darkest = 0;
        for (int i = 1; i < profile.length; i++)
            if (profile[i] < profile[darkest])
                darkest = i;
        double threshold = (profile[darkest] + (profile[0] + profile[profile.length - 1]) / 2) / 2;
        int upper = darkest;
        while (upper > 0 && profile[upper - 1] < threshold)
            upper--;
        int lower = darkest;
        while (lower < profile.length - 1 && profile[lower + 1] < threshold)
            lower++;
        double line = -4 + (upper + lower) * .125;
        double half = Math.max(.75, Math.min(3, (lower - upper + 1) * .125));
        double fall = 3 * open;
        double cx = m[0] + line * nx;
        double cy = m[1] + line * ny;
        Field field = (x, y, weights) -> {
            double dx = x - cx;
            double dy = y - cy;
            double along = Math.abs(dx * ux + dy * uy);
            double across = dx * nx + dy * ny;
            double shape = 1 - smooth((along - .22 * width) / (.33 * width));
            double stretch;
            if (across < -half || across > half + open + fall)
                stretch = 0;
            else if (across < half + open)
                stretch = (across + half) / (2 * half + open);
            else
                stretch = 1 - (across - half - open) / fall;
            weights[0] = shape * stretch;
        };
        double reach = width + open + fall + 8;
        int[] region = {(int) ((cx - reach) * unit), (int) ((cy - reach) * unit),
            (int) Math.ceil(2 * reach * unit), (int) Math.ceil(2 * reach * unit)};
        return new Rig(image, region, unit, new double[][]{{cx, cy}}, field, open);
    }

    /** How far, and which way on the grid, a mouth moves its middle when fully open. */
    private static double[] opening(double[] m) {
        double angle = Math.toRadians(m[3]);
        double open = Math.max(3, m[4] * m[2]);
        return new double[]{-Math.sin(angle) * open, Math.cos(angle) * open};
    }

    private static double luminance(BufferedImage image, double x, double y) {
        int ix = Math.max(0, Math.min(image.getWidth() - 1, (int) x));
        int iy = Math.max(0, Math.min(image.getHeight() - 1, (int) y));
        int argb = image.getRGB(ix, iy);
        if ((argb >>> 24) < 128)
            return 255;
        return .3 * (argb >> 16 & 255) + .59 * (argb >> 8 & 255) + .11 * (argb & 255);
    }

    /**
     * How far a mouth is open {@code seconds} into saying {@code text}: it opens and closes once per
     * character, as Chinese is spoken a syllable a character, stays closed through the pauses the
     * punctuation asks for, and is closed after the last character.
     *
     * @param text    what is said
     * @param seconds time since the speaking started
     * @return 0 closed to 1 fully open
     */
    static double mouth(String text, double seconds) {
        if (text == null || seconds < 0)
            return 0;
        double start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            double length = syllable(c);
            if (seconds < start + length) {
                if (!spoken(c))
                    return 0;
                // the same character always opens the mouth as wide, so a line looks the same when reread
                double strength = .55 + .45 * (((i + 1) * 2654435761L & 0xffffffffL) / 4294967296.0);
                return strength * Math.sin(Math.PI * (seconds - start) / length);
            }
            start += length;
        }
        return 0;
    }

    /**
     * @param text what is said
     * @return how long saying it takes, pauses included, in seconds
     */
    static double speech(String text) {
        double total = 0;
        if (text != null)
            for (int i = 0; i < text.length(); i++)
                total += syllable(text.charAt(i));
        return total;
    }

    private static double syllable(char c) {
        if ("，、；：—".indexOf(c) >= 0)
            return .24;
        if ("。？！…".indexOf(c) >= 0)
            return .42;
        if (!spoken(c))
            return 0;
        return 1 / 7.0;
    }

    private static boolean spoken(char c) {
        return Character.isLetterOrDigit(c);
    }

    static void spiritPose(Graphics2D g, double x, double y, double height, double seconds, int frame, Point2D gaze) {
        int cell = Math.floorMod(frame, 8);
        // without the second sheet its poses fall back to the first sheet's, as the people's do
        if (cell >= 4 && sheet("glimmer", "-poses-b") == null)
            cell -= 4;
        double zoom = cell >= 4 ? SECOND_ZOOM : 1;
        Rig rig = rig(cell, height * zoom * deviceScale(g));
        if (rig == null)
            return;
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        // Both wing tips rise together: positive turns the left tip up, negative the right one.
        double flap = AcademyAtmosphere.motion() ? FLUTTER[cell][1] * Math.sin(t * FLUTTER[cell][0]) : 0;
        double sway = AcademyAtmosphere.motion() ? .10 * Math.sin(t * 1.6 - .9) : 0;
        BufferedImage image = rig.bend(new double[]{flap, 0, 0, -flap, 0, 0, clamp(sway), 0, 0});
        double scale = height * zoom / rig.height;
        AffineTransform transform = new AffineTransform();
        transform.translate(x + 4 * Math.sin(t * .8), y + 8 * Math.sin(t * 1.4));
        transform.rotate(.035 * Math.sin(t * 1.1));
        transform.scale(scale, scale);
        transform.translate(-rig.width / 2.0, -rig.height / 2.0);
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.transform(transform);
        p.drawImage(image, 0, 0, null);
        double unit = rig.width / 640.0;
        double[] pos = EYES[cell];
        if (pos != null) {
            Point2D target = null;
            if (gaze != null)
                try {
                    target = transform.inverseTransform(gaze, null);
                } catch (java.awt.geom.NoninvertibleTransformException e) {
                    // No gaze for a zero-size component.
                }
            boolean blink = AcademyAtmosphere.motion() && t % 5.1 > 4.94;
            double size = pos[4] * unit;
            for (int eye = 0; eye < 2; eye++) {
                double ex = pos[eye * 2] * unit;
                double ey = pos[eye * 2 + 1] * unit;
                if (blink) {
                    double bx = ex + pos[5] * unit;
                    double by = ey + pos[6] * unit;
                    p.setColor(new Color(14, 29, 51));
                    p.fill(new Ellipse2D.Double(bx - 18 * size, by - 27 * size, 36 * size, 54 * size));
                    p.setColor(new Color(122, 226, 248));
                    p.draw(new java.awt.geom.Arc2D.Double(bx - 13 * size, by - 3 * size, 26 * size, 9 * size, 0, -180, 0));
                } else
                    pupil(p, ex, ey, 5 * size, 8 * size, target);
            }
        }
        p.dispose();
        AcademyAtmosphere.lamp(g, (float) x, (float) (y + height * .12), (float) (height * .23), .4f, t);
    }

    private static double clamp(double angle) {
        return Math.max(-MAX_BEND, Math.min(MAX_BEND, angle));
    }

    private static double deviceScale(Graphics2D g) {
        double det = Math.abs(g.getTransform().getDeterminant());
        return det > 0 ? Math.sqrt(det) : 1;
    }

    /** The scaled pose plus the precomputed limb weights. Only the pixels that can move are resampled. */
    private static Rig rig(int cell, double pixels) {
        BufferedImage source = pose("glimmer", cell);
        if (source == null)
            return null;
        int size = (int) Math.round(Math.max(24, Math.min(source.getHeight(), pixels)));
        long key = cell * 100000L + size;
        Rig rig = RIGS.get(key);
        if (rig == null) {
            BufferedImage image = scaled(source, size);
            double[][] limbs = LIMBS[cell];
            double[][] pivots = new double[limbs.length][];
            for (int l = 0; l < limbs.length; l++)
                pivots[l] = new double[]{limbs[l][0], limbs[l][1]};
            rig = new Rig(image, null, image.getWidth() / 640.0, pivots, (x, y, weights) -> {
                for (int l = 0; l < limbs.length; l++)
                    weights[l] = wing(limbs[l], x, y);
            }, MAX_BEND * 280);
            RIGS.put(key, rig);
        }
        return rig;
    }

    /** The weight of a wing or the tail: zero at its pivot, full around its centre, fading out at its edge. */
    private static double wing(double[] limb, double x, double y) {
        double ax = limb[2] - limb[0];
        double ay = limb[3] - limb[1];
        double length = Math.hypot(ax, ay);
        double along = ((x - limb[0]) * ax + (y - limb[1]) * ay) / (length * length);
        if (along <= 0)
            return 0;
        double radial = (Math.hypot(x - limb[2], y - limb[3]) - limb[4]) / limb[5];
        return smooth(along) * (1 - smooth(radial));
    }

    private static double smooth(double v) {
        if (v <= 0)
            return 0;
        if (v >= 1)
            return 1;
        return v * v * (3 - 2 * v);
    }

    private static BufferedImage scaled(BufferedImage source, int height) {
        int width = Math.max(1, (int) Math.round(source.getWidth() * height / (double) source.getHeight()));
        BufferedImage current = copy(source, source.getWidth(), source.getHeight());
        // Halving steps keep the painted gold lines from aliasing when the spirit is small.
        while (current.getHeight() / 2 >= height)
            current = copy(current, current.getWidth() / 2, current.getHeight() / 2);
        if (current.getHeight() != height)
            current = copy(current, width, height);
        return current;
    }

    private static BufferedImage copy(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    /** The weight of every limb of a rig at one point of the 640-unit grid. */
    private interface Field {
        void weigh(double x, double y, double[] weights);
    }

    /**
     * A painting that bends. Each limb is a weight field over the painting and a motion of its own: a
     * turn about its pivot and a shift. A pixel moves by the motions of the limbs it belongs to, each in
     * proportion to its weight, so a limb bends out of what it is attached to instead of being cut from
     * it. Only the pixels some limb can move are resampled.
     */
    private static final class Rig {
        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final double unit;
        private final int[] source;
        private final BufferedImage out;
        private final int[] target;
        private final double[][] pivots;
        private final int[] moving;
        private final float[][] weights;
        private double[] last;

        /**
         * @param image  the painting, at the size it is bent at
         * @param region the part of it that bends (x, y, width, height in pixels), or null for all of it
         * @param unit   pixels per unit of the 640-unit grid
         * @param pivots per limb, the point it turns about on the grid
         * @param field  the weights of the limbs on the grid
         * @param reach  the furthest any limb moves a pixel, on the grid
         */
        Rig(BufferedImage image, int[] region, double unit, double[][] pivots, Field field, double reach) {
            int[] r = region == null ? new int[]{0, 0, image.getWidth(), image.getHeight()} : region.clone();
            r[2] = Math.min(r[2] + Math.min(0, r[0]), image.getWidth() - Math.max(0, r[0]));
            r[3] = Math.min(r[3] + Math.min(0, r[1]), image.getHeight() - Math.max(0, r[1]));
            r[0] = Math.max(0, r[0]);
            r[1] = Math.max(0, r[1]);
            left = r[0];
            top = r[1];
            width = Math.max(1, r[2]);
            height = Math.max(1, r[3]);
            this.unit = unit;
            BufferedImage part = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = part.createGraphics();
            g.drawImage(image, -left, -top, null);
            g.dispose();
            source = ((DataBufferInt) part.getRaster().getDataBuffer()).getData();
            out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            target = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
            // A rig of a region is a patch drawn over the unbent painting, so it holds only the pixels
            // that move: drawing the still ones again would thicken every soft edge they have.
            if (region == null)
                System.arraycopy(source, 0, target, 0, source.length);
            this.pivots = new double[pivots.length][];
            for (int l = 0; l < pivots.length; l++)
                this.pivots[l] = new double[]{pivots[l][0] * unit - left, pivots[l][1] * unit - top};
            // A moved limb can only reach pixels near painted ones.
            boolean[] near = dilate(source, width, height, (int) Math.ceil(reach * unit) + 2);
            int count = 0;
            float[][] all = new float[pivots.length][width * height];
            int[] index = new int[width * height];
            double[] w = new double[pivots.length];
            for (int py = 0; py < height; py++)
                for (int px = 0; px < width; px++) {
                    int i = py * width + px;
                    if (!near[i])
                        continue;
                    field.weigh((left + px + .5) / unit, (top + py + .5) / unit, w);
                    boolean any = false;
                    for (int l = 0; l < w.length; l++) {
                        all[l][count] = (float) w[l];
                        any |= w[l] > 1e-4;
                    }
                    if (any)
                        index[count++] = i;
                }
            moving = java.util.Arrays.copyOf(index, count);
            weights = new float[pivots.length][];
            for (int l = 0; l < pivots.length; l++)
                weights[l] = java.util.Arrays.copyOf(all[l], count);
        }

        private static boolean[] dilate(int[] pixels, int width, int height, int radius) {
            boolean[] row = new boolean[pixels.length];
            for (int y = 0; y < height; y++) {
                int last = -radius - 1;
                for (int x = 0; x < width; x++) {
                    if ((pixels[y * width + x] >>> 24) > 4)
                        last = x;
                    row[y * width + x] = x - last <= radius;
                }
                last = width + radius + 1;
                for (int x = width - 1; x >= 0; x--) {
                    if ((pixels[y * width + x] >>> 24) > 4)
                        last = x;
                    row[y * width + x] |= last - x <= radius;
                }
            }
            boolean[] out = new boolean[pixels.length];
            for (int x = 0; x < width; x++) {
                int last = -radius - 1;
                for (int y = 0; y < height; y++) {
                    if (row[y * width + x])
                        last = y;
                    out[y * width + x] = y - last <= radius;
                }
                last = height + radius + 1;
                for (int y = height - 1; y >= 0; y--) {
                    if (row[y * width + x])
                        last = y;
                    out[y * width + x] |= last - y <= radius;
                }
            }
            return out;
        }

        /**
         * @param motion per limb: the turn in radians (positive turns clockwise on screen), then the
         *               shift x and y on the grid
         * @return the bent painting of the rig's region; it is reused by the next call
         */
        BufferedImage bend(double[] motion) {
            if (java.util.Arrays.equals(motion, last))
                return out;
            last = motion.clone();
            for (int k = 0; k < moving.length; k++) {
                int i = moving[k];
                double px = i % width + .5;
                double py = i / width + .5;
                double sx = px;
                double sy = py;
                for (int l = 0; l < pivots.length; l++) {
                    double w = weights[l][k];
                    if (w == 0)
                        continue;
                    double phi = motion[l * 3] * w;
                    if (phi != 0) {
                        // Inverse rotation about the pivot: the destination asks where its colour came from.
                        double sin = phi - phi * phi * phi / 6;
                        double cos1 = -phi * phi / 2;
                        double rx = px - pivots[l][0];
                        double ry = py - pivots[l][1];
                        sx += cos1 * rx + sin * ry;
                        sy += -sin * rx + cos1 * ry;
                    }
                    sx -= motion[l * 3 + 1] * w * unit;
                    sy -= motion[l * 3 + 2] * w * unit;
                }
                target[i] = sample(sx - .5, sy - .5);
            }
            return out;
        }

        private int sample(double x, double y) {
            if (x < 0 || y < 0 || x >= width - 1 || y >= height - 1)
                return 0;
            int x0 = (int) x;
            int y0 = (int) y;
            int i = y0 * width + x0;
            int a = source[i];
            int b = source[i + 1];
            int c = source[i + width];
            int d = source[i + width + 1];
            if ((a | b | c | d) == 0)
                return 0;
            int fx = (int) ((x - x0) * 256);
            int fy = (int) ((y - y0) * 256);
            int result = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int top = ((a >>> shift) & 255) * (256 - fx) + ((b >>> shift) & 255) * fx;
                int bottom = ((c >>> shift) & 255) * (256 - fx) + ((d >>> shift) & 255) * fx;
                result |= ((top * (256 - fy) + bottom * fy) >>> 16) << shift;
            }
            return result;
        }
    }
}
