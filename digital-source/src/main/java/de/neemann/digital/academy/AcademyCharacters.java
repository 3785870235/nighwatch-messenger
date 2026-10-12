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
     * the centre, so a limb bends out of the body instead of being cut from it.
     */
    private static final double[][][] LIMBS = {
        {{285, 315, 165, 290, 110, 45}, {450, 320, 530, 315, 90, 40}, {305, 462, 265, 540, 80, 40}},
        {{262, 375, 180, 415, 85, 40}, {395, 255, 470, 160, 115, 45}, {308, 480, 290, 545, 70, 40}},
        {{250, 255, 170, 190, 130, 45}, {420, 255, 510, 185, 125, 45}, {318, 440, 280, 500, 75, 40}},
        {{265, 265, 165, 255, 105, 45}, {430, 255, 500, 220, 100, 45}, {300, 422, 265, 480, 75, 40}},
    };
    private static final double MAX_BEND = .11;
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
        if (AcademyArt.get("v2/characters/glimmer-poses.png") != null) {
            spiritPose(g, x, y, height, seconds, responding ? 2 : 0, gaze);
            return;
        }
        BufferedImage sprite = AcademyArt.get("characters/glimmer-spirit.png");
        if (sprite == null)
            return;
        double motion = AcademyAtmosphere.motion() ? 1 : 0;
        double scale = height / sprite.getHeight() * (1 + motion * 0.015 * Math.sin(seconds * 2.0));
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform pose = new AffineTransform();
        pose.translate(x + motion * 4 * Math.sin(seconds * 0.8), y + motion * 9 * Math.sin(seconds * 1.7));
        pose.rotate(motion * 0.04 * Math.sin(seconds * 1.2));
        pose.scale(scale, scale);
        pose.translate(-sprite.getWidth() / 2.0, -sprite.getHeight() / 2.0);
        p.transform(pose);
        // The fallback sprite is drawn whole: splitting a single painting into parts always shows a seam.
        p.drawImage(sprite, 0, 0, null);
        Point2D eyeTarget = null;
        if (gaze != null)
            try {
                eyeTarget = pose.inverseTransform(gaze, null);
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
        if (!"-poses".equals(suffix)) {
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
                    reference, foot[cell], middle, composed);
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

    /** One pose, the sheet-wide measurements it is drawn with, and where the scenes expect its figure. */
    private static final class Pose {
        private final BufferedImage image;
        private final int figureHeight;
        private final int foot;
        private final int centre;
        private final Anchor composed;

        private Pose(BufferedImage image, int figureHeight, int foot, int centre, Anchor composed) {
            this.image = image;
            this.figureHeight = figureHeight;
            this.foot = foot;
            this.centre = centre;
            this.composed = composed;
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
        portrait(g, poseFor("fu", frame), x, y, height, seconds);
    }

    static void keeper(Graphics2D g, double x, double y, double height, double seconds, int frame) {
        portrait(g, poseFor("luo", frame), x, y, height, seconds);
    }

    /**
     * Draws a pose where the scenes expect it. Every scene was composed against the first sheet drawn
     * cell first - the cell's top left corner on {@code (x, y)}, the cell scaled to {@code height} -
     * and the fallback sprite above is placed the same way, so that stays the contract. Inside that
     * box the figure is placed by measurement rather than by the cell, so a sheet with different
     * padding paints the same character in the same place.
     */
    private static void portrait(Graphics2D g, Pose pose, double x, double y, double height, double seconds) {
        if (pose == null)
            return;
        Graphics2D p = (Graphics2D) g.create();
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        Anchor at = pose.composed;
        // The figure comes out as tall as the first sheet paints it in this box, whatever the padding
        // of the sheet the pose was taken from.
        double scale = height * at.figure / pose.figureHeight;
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Each pose stands on the first sheet's foot line, and its sheet's middle sits on the first
        // sheet's middle. For a pose of the first sheet this is the cell corner on (x, y) again, apart
        // from the few pixels by which its own feet differ from the others'.
        p.translate(x + height * at.centre - pose.centre * scale,
                y + height * at.foot - pose.foot * scale + Math.sin(t * 1.25) * 2);
        p.scale(scale * (1 + .002 * Math.sin(t * 1.25)), scale);
        p.drawImage(pose.image, 0, 0, null);
        p.dispose();
    }

    static void spiritPose(Graphics2D g, double x, double y, double height, double seconds, int frame, Point2D gaze) {
        int cell = Math.floorMod(frame, 4);
        Rig rig = rig(cell, height * deviceScale(g));
        if (rig == null)
            return;
        double t = AcademyAtmosphere.motion() ? seconds : 0;
        // Both wing tips rise together: positive turns the left tip up, negative the right one.
        double flutter = cell == 3 ? 4.6 : 3.1;
        double flap = AcademyAtmosphere.motion() ? .075 * Math.sin(t * flutter) : 0;
        double sway = AcademyAtmosphere.motion() ? .10 * Math.sin(t * 1.6 - .9) : 0;
        BufferedImage image = rig.bend(flap, -flap, sway);
        double scale = height / rig.height;
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
        if (cell != 3) {
            double[][] eyes = {{344, 309, 410, 300}, {276, 330, 336, 292}, {310, 262, 379, 260}};
            double[] pos = eyes[cell];
            Point2D target = null;
            if (gaze != null)
                try {
                    target = transform.inverseTransform(gaze, null);
                } catch (java.awt.geom.NoninvertibleTransformException e) {
                    // No gaze for a zero-size component.
                }
            boolean blink = AcademyAtmosphere.motion() && t % 5.1 > 4.94;
            for (int eye = 0; eye < 2; eye++) {
                double ex = pos[eye * 2] * unit;
                double ey = pos[eye * 2 + 1] * unit;
                if (blink) {
                    p.setColor(new Color(14, 29, 51));
                    p.fill(new Ellipse2D.Double(ex - 18 * unit, ey - 27 * unit, 36 * unit, 54 * unit));
                    p.setColor(new Color(122, 226, 248));
                    p.draw(new java.awt.geom.Arc2D.Double(ex - 13 * unit, ey - 3 * unit, 26 * unit, 9 * unit, 0, -180, 0));
                } else
                    pupil(p, ex, ey, 5 * unit, 8 * unit, target);
            }
        }
        p.dispose();
        AcademyAtmosphere.lamp(g, (float) x, (float) (y + height * .12), (float) (height * .23), .4f, t);
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
            rig = new Rig(scaled(source, size), LIMBS[cell]);
            RIGS.put(key, rig);
        }
        return rig;
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

    private static final class Rig {
        private final int width;
        private final int height;
        private final int[] source;
        private final BufferedImage out;
        private final int[] target;
        private final double[][] limbs;
        private final int[] moving;
        private final float[][] weights;
        private double lastLeft = Double.NaN;
        private double lastRight;
        private double lastTail;

        Rig(BufferedImage image, double[][] grid) {
            width = image.getWidth();
            height = image.getHeight();
            source = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            target = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
            System.arraycopy(source, 0, target, 0, source.length);
            double unit = width / 640.0;
            limbs = new double[grid.length][];
            for (int i = 0; i < grid.length; i++) {
                limbs[i] = new double[grid[i].length];
                for (int j = 0; j < grid[i].length; j++)
                    limbs[i][j] = grid[i][j] * unit;
            }
            // A bent limb can only reach pixels near painted ones.
            int reach = (int) Math.ceil(MAX_BEND * 280 * unit) + 2;
            boolean[] near = dilate(source, width, height, reach);
            int count = 0;
            float[][] all = new float[limbs.length][width * height];
            int[] index = new int[width * height];
            for (int py = 0; py < height; py++)
                for (int px = 0; px < width; px++) {
                    int i = py * width + px;
                    if (!near[i])
                        continue;
                    boolean any = false;
                    for (int l = 0; l < limbs.length; l++) {
                        float w = (float) weight(limbs[l], px + .5, py + .5);
                        all[l][count] = w;
                        any |= w > 1e-4f;
                    }
                    if (any)
                        index[count++] = i;
                }
            moving = java.util.Arrays.copyOf(index, count);
            weights = new float[limbs.length][];
            for (int l = 0; l < limbs.length; l++)
                weights[l] = java.util.Arrays.copyOf(all[l], count);
        }

        private static double weight(double[] limb, double x, double y) {
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

        BufferedImage bend(double left, double right, double tail) {
            if (left == lastLeft && right == lastRight && tail == lastTail)
                return out;
            lastLeft = left;
            lastRight = right;
            lastTail = tail;
            double[] angle = {clamp(left), clamp(right), clamp(tail)};
            for (int k = 0; k < moving.length; k++) {
                int i = moving[k];
                double px = i % width + .5;
                double py = i / width + .5;
                double sx = px;
                double sy = py;
                for (int l = 0; l < limbs.length; l++) {
                    double phi = angle[l] * weights[l][k];
                    if (phi == 0)
                        continue;
                    // Inverse rotation about the pivot: the destination asks where its colour came from.
                    double sin = phi - phi * phi * phi / 6;
                    double cos1 = -phi * phi / 2;
                    double rx = px - limbs[l][0];
                    double ry = py - limbs[l][1];
                    sx += cos1 * rx + sin * ry;
                    sy += -sin * rx + cos1 * ry;
                }
                target[i] = sample(sx - .5, sy - .5);
            }
            return out;
        }

        private static double clamp(double angle) {
            return Math.max(-MAX_BEND, Math.min(MAX_BEND, angle));
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
