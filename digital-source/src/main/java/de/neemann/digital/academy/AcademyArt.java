/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the image assets from assets/game/v1 below the project root (system property
 * academy.root, otherwise the working directory). A missing or broken file is reported once
 * and the screens draw a code fallback instead; the simulation never depends on images.
 */
public final class AcademyArt {
    /**
     * home screen background
     */
    public static final String MENU_BACKGROUND = "menu-background.png";
    /**
     * the archive door, closed
     */
    public static final String ARCHIVE_CLOSED = "archive-closed.png";
    /**
     * the archive door, open
     */
    public static final String ARCHIVE_OPEN = "archive-open.png";
    /** Unobstructed room used behind the independently animated door leaves. */
    public static final String ARCHIVE_INTERIOR = "archive-interior.png";

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Map<String, String> STATUS = new LinkedHashMap<>();

    private AcademyArt() {
    }

    /**
     * @return the asset directory
     */
    public static File directory() {
        File root = new File(System.getProperty("academy.root", ".")).getAbsoluteFile();
        return new File(root, "assets/game/v1");
    }

    /**
     * Returns the image or null if it is not available. Must be called on the EDT.
     *
     * @param name the file name
     * @return the image or null
     */
    public static BufferedImage get(String name) {
        if (CACHE.containsKey(name))
            return CACHE.get(name);
        File f = name.startsWith("v2/")
                ? new File(directory().getParentFile(), name) : new File(directory(), name);
        BufferedImage img = null;
        try {
            if (f.isFile())
                img = ImageIO.read(f);
            STATUS.put(name, img != null ? "ok " + img.getWidth() + "x" + img.getHeight() + " " + f
                    : "missing " + f + " (fallback drawing)");
        } catch (IOException | RuntimeException e) {
            STATUS.put(name, "unreadable " + f + ": " + e.getMessage() + " (fallback drawing)");
        }
        if (img == null)
            System.err.println("[academy] art " + STATUS.get(name));
        CACHE.put(name, img);
        return img;
    }

    /**
     * @return the load status of every requested image
     */
    public static String status() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : STATUS.entrySet())
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        return sb.toString();
    }

    /**
     * Forgets the loaded images, e.g. after new assets were added.
     */
    public static void reload() {
        CACHE.clear();
        STATUS.clear();
    }

    /**
     * Draws the image so that it covers the area, cropping the overlapping part.
     *
     * @param g    the graphics
     * @param img  the image
     * @param x    left
     * @param y    top
     * @param w    width
     * @param h    height
     * @param anchorX horizontal focus 0..1 kept visible when cropping
     */
    public static void drawCover(Graphics2D g, BufferedImage img, int x, int y, int w, int h, double anchorX) {
        double s = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * s);
        int dh = (int) Math.ceil(img.getHeight() * s);
        int dx = x - (int) ((dw - w) * anchorX);
        int dy = y - (dh - h) / 2;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clipRect(x, y, w, h);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(img, dx, dy, dw, dh, null);
        g2.dispose();
    }
}
