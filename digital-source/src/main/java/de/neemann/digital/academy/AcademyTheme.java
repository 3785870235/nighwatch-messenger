/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.draw.graphics.ColorKey;
import de.neemann.digital.draw.graphics.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Colors, fonts and the small set of code-rendered controls of the academy screens:
 * deep navy, warm gold, cyan for interaction and ivory text. Controls stay real Swing
 * buttons/check boxes; only their painting is changed.
 */
public final class AcademyTheme {
    /**
     * window background
     */
    public static final Color NIGHT = new Color(0x0E1726);
    /**
     * header and secondary background
     */
    public static final Color NIGHT_2 = new Color(0x132036);
    /**
     * card background
     */
    public static final Color CARD = new Color(0x182A42);
    /**
     * raised card / selected row
     */
    public static final Color CARD_HI = new Color(0x22395A);
    /**
     * thin lines
     */
    public static final Color LINE = new Color(0x34507A);
    /**
     * warm gold accent
     */
    public static final Color GOLD = new Color(0xD9AF60);
    /**
     * light gold
     */
    public static final Color GOLD_HI = new Color(0xF0CB7E);
    /**
     * interaction cyan
     */
    public static final Color CYAN = new Color(0x65CEE1);
    /**
     * ivory text
     */
    public static final Color IVORY = new Color(0xF2E8D5);
    /**
     * muted text
     */
    public static final Color MUTED = new Color(0xA9B7CB);
    /**
     * success green
     */
    public static final Color OK = new Color(0x7FD69B);
    /**
     * warning / error coral
     */
    public static final Color ALERT = new Color(0xEE8A74);

    private static final String FAMILY = pickFamily();
    private static final String SCRIPT_FAMILY = pickScriptFamily();
    private static final Icon NO_ICON = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

    private AcademyTheme() {
    }

    private static String pickFamily() {
        Set<String> names;
        try {
            names = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        } catch (RuntimeException e) {
            return Font.SANS_SERIF;
        }
        for (String f : new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC"})
            if (names.contains(f))
                return f;
        return Font.SANS_SERIF;
    }

    /**
     * @param style the font style
     * @param size  the size in points
     * @return the academy font
     */
    public static Font font(int style, float size) {
        return new Font(FAMILY, style, Math.round(size)).deriveFont(size);
    }

    private static String pickScriptFamily() {
        Set<String> names = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"华文行楷", "STXingkai", "KaiTi", "楷体", "STKaiti"})
            if (names.contains(f))
                return f;
        return FAMILY;
    }

    /**
     * Local calligraphy font for short titles, never for dense data or error details.
     * @param size point size
     * @return installed script font with readable fallback
     */
    public static Font script(float size) {
        return new Font(SCRIPT_FAMILY, Font.PLAIN, Math.round(size)).deriveFont(size);
    }

    /**
     * @return the body font, 15 pt
     */
    public static Font body() {
        return font(Font.PLAIN, 15);
    }

    /**
     * Uses the academy colors on the Digital canvas. Drawing only, the simulation is not touched.
     */
    public static void applyCanvasColors() {
        AcademyAtmosphere.magic(AcademyAtmosphere.magic());
        AcademyAtmosphere.motion(AcademyAtmosphere.motion());
        // starts from the key default: touching the ColorSchemes enum first breaks the class initialization order
        ColorScheme.setOverride(new ColorScheme.Builder(ColorScheme.CUSTOM_COLOR_SCHEME.getDefault())
                .set(ColorKey.BACKGROUND, new Color(0x101C2E))
                .set(ColorKey.GRID, new Color(0x152339))
                .set(ColorKey.MAIN, new Color(0xE6DCC6))
                .set(ColorKey.WIRE, new Color(0x8FC9DA))
                .set(ColorKey.WIRE_LOW, new Color(0x7599AF))
                .set(ColorKey.WIRE_HIGH, GOLD_HI)
                .set(ColorKey.WIRE_VALUE, new Color(0x7FD69B))
                .set(ColorKey.WIRE_OUT, new Color(0xEE8A74))
                .set(ColorKey.WIRE_Z, new Color(0x8A93A3))
                .set(ColorKey.PINS, new Color(0xA9B7CB))
                .set(ColorKey.HIGHLIGHT, GOLD_HI)
                .set(ColorKey.DISABLED, new Color(0x2B3B55))
                .set(ColorKey.ERROR, new Color(0xFF6B6B))
                .set(ColorKey.PASSED, OK)
                .set(ColorKey.TESTCASE, new Color(60, 140, 90, 200))
                .set(ColorKey.ASYNC, new Color(150, 70, 70, 200))
                .build());
    }

    /**
     * Creates a label.
     *
     * @param text  the text
     * @param font  the font
     * @param color the color
     * @return the label
     */
    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /**
     * Creates a card title.
     *
     * @param text the text
     * @return the label
     */
    public static JLabel cardTitle(String text) {
        JLabel l = label(text, font(Font.BOLD, 18), GOLD_HI);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return l;
    }

    /**
     * @param top    top
     * @param left   left
     * @param bottom bottom
     * @param right  right
     * @return an empty border
     */
    public static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /**
     * A plain transparent panel.
     *
     * @param layout the layout
     * @return the panel
     */
    public static JPanel clear(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /**
     * The look of a button.
     */
    public enum Kind {
        /**
         * main action, gold
         */
        PRIMARY,
        /**
         * normal action, cyan outline
         */
        SECONDARY,
        /**
         * quiet action
         */
        QUIET
    }

    /**
     * Creates a code-rendered button.
     *
     * @param text the text
     * @param kind the look
     * @return the button
     */
    public static JButton button(String text, Kind kind) {
        return new Button(text, kind);
    }

    /**
     * Changes the look of a button made by {@link #button(String, Kind)}, so that a page can hand its
     * gold main action to another button when the situation changes. Other buttons are left alone.
     *
     * @param button the button
     * @param kind   the new look
     */
    public static void kind(JButton button, Kind kind) {
        if (button instanceof Button && ((Button) button).kind != kind) {
            ((Button) button).kind = kind;
            button.setForeground(kind == Kind.PRIMARY ? NIGHT : IVORY);
            button.repaint();
        }
    }

    /**
     * A rounded card with a fill color and a thin border line.
     */
    public static class Card extends JPanel {
        private final Color fill;
        private Color line;

        /**
         * Creates a card.
         *
         * @param layout the layout
         * @param fill   the background
         * @param line   the border line, maybe null
         */
        public Card(LayoutManager layout, Color fill, Color line) {
            super(layout);
            this.fill = fill;
            this.line = line;
            setOpaque(false);
            setBorder(pad(14, 16, 14, 16));
        }

        /**
         * Creates a standard card.
         *
         * @param layout the layout
         */
        public Card(LayoutManager layout) {
            this(layout, CARD, LINE);
        }

        /**
         * @param line the new border line color
         */
        public void setLine(Color line) {
            this.line = line;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            if (line != null) {
                g2.setColor(line);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Creates a card with a title above the content.
     *
     * @param title   the title
     * @param content the content
     * @return the card
     */
    public static Card titled(String title, JComponent content) {
        Card c = new Card(new BorderLayout());
        c.add(cardTitle(title), BorderLayout.NORTH);
        content.setOpaque(false);
        c.add(content, BorderLayout.CENTER);
        return c;
    }

    /**
     * Gradient background panel of the screens.
     */
    public static class Backdrop extends JPanel {
        /**
         * Creates a backdrop.
         *
         * @param layout the layout
         */
        public Backdrop(LayoutManager layout) {
            super(layout);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setPaint(new GradientPaint(0, 0, NIGHT_2, 0, getHeight(), NIGHT));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * Wrapping, non-editable text that keeps its height in sync with its width.
     */
    public static final class Text extends JTextArea {
        private final int hintWidth;
        private int lastWidth;

        /**
         * Creates a text block.
         *
         * @param text      the text
         * @param font      the font
         * @param color     the color
         * @param hintWidth the width used for the first layout
         */
        public Text(String text, Font font, Color color, int hintWidth) {
            super(text);
            this.hintWidth = hintWidth;
            setFont(font);
            setForeground(color);
            setEditable(false);
            setFocusable(false);
            setLineWrap(true);
            setWrapStyleWord(false);
            setOpaque(false);
            setBorder(null);
            setHighlighter(null);
            setCursor(Cursor.getDefaultCursor());
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (getWidth() != lastWidth) {
                        lastWidth = getWidth();
                        revalidate();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            if (getWidth() == 0 && hintWidth > 0)
                setSize(hintWidth, Short.MAX_VALUE);
            return super.getPreferredSize();
        }

        @Override
        public void setText(String t) {
            if (!t.equals(getText())) {
                super.setText(t);
                revalidate();
                repaint();
            }
        }
    }

    private static final class Button extends JButton {
        private Kind kind;

        private Button(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(script(20));
            setForeground(kind == Kind.PRIMARY ? NIGHT : IVORY);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(8, 16, 8, 16));
            setBorder(pad(9, 18, 9, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isEnabled();
            boolean hover = on && getModel().isRollover();
            boolean pressed = on && getModel().isPressed();
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            Color fill;
            Color line;
            switch (kind) {
                case PRIMARY:
                    fill = !on ? CARD_HI : pressed ? GOLD.darker() : hover ? GOLD_HI : GOLD;
                    line = null;
                    break;
                case SECONDARY:
                    fill = !on ? CARD : pressed ? new Color(0x1E4A5A) : hover ? new Color(0x1F3F55) : new Color(0x18324A);
                    line = on ? CYAN : LINE;
                    break;
                default:
                    fill = pressed ? CARD_HI : hover ? new Color(0x1D3150) : new Color(0, 0, 0, 0);
                    line = hover ? LINE : null;
                    break;
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            if (line != null) {
                g2.setColor(line);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, w, h, 14, 14);
            }
            if (isFocusOwner()) {
                g2.setColor(new Color(CYAN.getRed(), CYAN.getGreen(), CYAN.getBlue(), 150));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(2, 2, w - 4, h - 4, 12, 12);
            }
            g2.dispose();
            setForeground(!on ? MUTED : kind == Kind.PRIMARY ? NIGHT : IVORY);
            super.paintComponent(g);
        }
    }

    /**
     * Prediction choice chip; still a radio button so that a ButtonGroup works.
     */
    public static final class Chip extends JRadioButton {
        /**
         * Creates a chip.
         *
         * @param text the text
         */
        public Chip(String text) {
            super(text);
            setFont(font(Font.BOLD, 15));
            setOpaque(false);
            setFocusPainted(false);
            setBorder(pad(9, 34, 9, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIcon(NO_ICON);

        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean sel = isSelected();
            boolean on = isEnabled();
            g2.setColor(sel ? new Color(0x1E4E5E) : new Color(0x17283F));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(!on ? LINE : sel ? CYAN : new Color(0x4A6488));
            g2.setStroke(new BasicStroke(sel ? 2f : 1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            int d = 16;
            int cx = 12;
            int cy = (getHeight() - d) / 2;
            g2.setColor(sel ? CYAN : MUTED);
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawOval(cx, cy, d, d);
            if (sel)
                g2.fillOval(cx + 4, cy + 4, d - 8, d - 8);
            g2.dispose();
            setForeground(!on ? MUTED : sel ? IVORY : new Color(0xD8DEE8));
            super.paintComponent(g);
        }
    }

    /**
     * Check box painted as a large switch with a text that tells its meaning.
     */
    public static final class Switch extends JCheckBox {
        private final String onText;
        private final String offText;
        private double sealPosition;
        private double sealFrom;
        private long sealStarted;

        /**
         * Creates a switch.
         *
         * @param offText text while not selected
         * @param onText  text while selected
         */
        public Switch(String offText, String onText) {
            super(offText);
            this.offText = offText;
            this.onText = onText;
            setFont(font(Font.BOLD, 15));
            setOpaque(false);
            setFocusPainted(false);
            setBorder(pad(10, 66, 10, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIcon(NO_ICON);

            AcademyAtmosphere.watch(this, this::advanceSeal);
            addItemListener(e -> {
                advanceSeal();
                sealFrom = sealPosition;
                sealStarted = System.nanoTime();
                setText(isSelected() ? this.onText : this.offText);
            });
        }

        private void advanceSeal() {
            double t = AcademyAtmosphere.motion() ? Math.min(1, (System.nanoTime() - sealStarted) / 230000000.0) : 1;
            sealPosition = sealFrom + ((isSelected() ? 1 : 0) - sealFrom) * t * t * (3 - 2 * t);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean sel = isSelected();
            boolean on = isEnabled();
            boolean hover = on && getModel().isRollover();
            g2.setColor(hover ? new Color(0x1F3654) : new Color(0x17283F));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(!on ? LINE : sel ? GOLD : new Color(0x4A6488));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            int tw = 40;
            int th = 22;
            int tx = 14;
            int ty = (getHeight() - th) / 2;
            g2.setColor(!on ? CARD : sel ? GOLD : new Color(0x2B405F));
            g2.fillRoundRect(tx, ty, tw, th, th, th);
            g2.setColor(!on ? MUTED : sel ? NIGHT : IVORY);
            advanceSeal();
            int cx = tx + 10 + (int) (20 * sealPosition);
            int cy = ty + th / 2;
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(cx - 5, cy - 6, 10, 10);
            g2.drawLine(cx, cy + 3, cx, cy + 9);
            g2.drawLine(cx, cy + 7, cx + 4, cy + 7);
            g2.dispose();
            setForeground(!on ? MUTED : IVORY);
            super.paintComponent(g);
        }
    }
}
