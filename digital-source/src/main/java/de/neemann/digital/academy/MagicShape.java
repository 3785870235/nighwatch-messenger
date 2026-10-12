/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.Model;
import de.neemann.digital.core.Value;
import de.neemann.digital.core.element.ElementAttributes;
import de.neemann.digital.core.element.Keys;
import de.neemann.digital.draw.elements.IOState;
import de.neemann.digital.draw.elements.Pin;
import de.neemann.digital.draw.elements.Pins;
import de.neemann.digital.draw.graphics.Graphic;
import de.neemann.digital.draw.graphics.Orientation;
import de.neemann.digital.draw.graphics.Polygon;
import de.neemann.digital.draw.graphics.Style;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.draw.model.ModelCreator;
import de.neemann.digital.draw.model.ModelEntry;
import de.neemann.digital.draw.shapes.InteractorInterface;
import de.neemann.digital.draw.shapes.Shape;
import java.awt.Color;
import java.util.Arrays;

/** Academy ornament on native shapes. Original pins, monitors and interactors are delegated. */
final class MagicShape implements Shape {
    private static final Style GOLD = Style.THIN.deriveColor(new Color(0xD9AF60));
    private static final Style CYAN = Style.THIN.deriveColor(new Color(0x65CEE1));
    private static final Style INK = Style.NORMAL.deriveFillStyle(new Color(0x17283F));
    private static final Style TEXT = Style.NORMAL.deriveFontStyle(14, false);
    /** The seal a chapter's output is drawn as on the canvas; the port IDs themselves are never shown to the player. */
    private static final java.util.Map<String, String> SEALS = new java.util.HashMap<>();

    static {
        for (String id : new String[]{"LIGHT", "REMIND", "PERMIT", "ACCEPT"})
            SEALS.put(id, "许可印");
        // the star lamps of the tower answer together, so their seals say which lamp they light
        SEALS.put("C", "高星灯许可印");
        SEALS.put("S", "低星灯许可印");
    }
    private final Shape nativeShape;
    private final String name;
    private final ElementAttributes attributes;
    private boolean story;
    private IOState monitor;
    private Value value;

    private MagicShape(String name, ElementAttributes attributes, Shape shape) {
        this.name = name;
        this.attributes = attributes;
        nativeShape = shape;
    }

    static Shape decorate(String name, ElementAttributes attributes, Shape shape) {
        if (attributes.getBits() != 1 || attributes.get(Keys.IN_OUT_SMALL)
                || !Arrays.asList("In", "Out", "And", "Or", "Not", "XOr", "LED").contains(name)
                || !attributes.get(Keys.INVERTER_CONFIG).isEmpty())
            return shape;
        return new MagicShape(name, attributes, shape);
    }

    static Shape decorateForLevel(String name, ElementAttributes attributes, Shape shape) {
        Shape decorated = decorate(name, attributes, shape);
        if (decorated instanceof MagicShape)
            ((MagicShape) decorated).story = true;
        return decorated;
    }

    @Override
    public Pins getPins() {
        return nativeShape.getPins();
    }

    @Override
    public InteractorInterface applyStateMonitor(IOState state) {
        monitor = state;
        return nativeShape.applyStateMonitor(state);
    }

    @Override
    public void readObservableValues() {
        nativeShape.readObservableValues();
        if (monitor != null) {
            if (name.equals("Out") || name.equals("LED"))
                value = monitor.getInput(0).getCopy();
            else if (monitor.outputCount() > 0)
                value = monitor.getOutput(0).getCopy();
        }
    }

    @Override
    public void registerModel(ModelCreator creator, Model model, ModelEntry element) {
        nativeShape.registerModel(creator, model, element);
    }

    @Override
    public void drawTo(Graphic g, Style highlight) {
        if (!AcademyAtmosphere.magic() || g.isFlagSet(Graphic.Flag.smallIO)) {
            nativeShape.drawTo(g, highlight);
            return;
        }
        boolean input = name.equals("In");
        boolean output = name.equals("Out") || name.equals("LED");
        if (input || output)
            drawSeal(g, input);
        else
            drawGate(g);
    }

    private void drawSeal(Graphic g, boolean input) {
        int c = input ? -16 : 16;
        Polygon seal = new Polygon().add(c - 15, -8).add(c - 8, -15).add(c + 8, -15)
                .add(c + 15, -8).add(c + 15, 8).add(c + 8, 15).add(c - 8, 15).add(c - 15, 8);
        g.drawPolygon(seal, INK);
        g.drawPolygon(seal, GOLD);
        g.drawCircle(new Vector(c - 11, -11), new Vector(c + 11, 11), CYAN);
        Style signal = value == null ? Style.THIN.deriveColor(new Color(0xA9B7CB)) : Style.getWireStyle(value);
        if (value != null && !value.isHighZ() && value.getValue() == 1)
            g.drawCircle(new Vector(c - 8, -8), new Vector(c + 8, 8), signal.deriveFillStyle(new Color(0x54442A)));
        String v = value == null ? input ? attributes.get(Keys.INPUT_DEFAULT).toString() : "?"
                : value.isHighZ() ? "Z" : Long.toString(value.getValue());
        g.drawText(new Vector(c, 0), v, Orientation.CENTERCENTER, TEXT);
        g.drawLine(new Vector(input ? -1 : 1, 0), new Vector(0, 0), signal);
        String title = attributes.getLabel();
        if (story)
            title = input ? "岗位 " + title : SEALS.getOrDefault(title, title);
        g.drawText(new Vector(input ? -45 : 45, 0), title,
                input ? Orientation.RIGHTCENTER : Orientation.LEFTCENTER, Style.INOUT.deriveFontStyle(18, false));
        for (int k = -1; k <= 1; k += 2) {
            g.drawLine(new Vector(c - 3, k * 15), new Vector(c, k * 19), GOLD);
            g.drawLine(new Vector(c, k * 19), new Vector(c + 3, k * 15), GOLD);
        }
    }

    private void drawGate(Graphic g) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int width = 20;
        for (Pin p : getPins()) {
            minY = Math.min(minY, p.getPos().y);
            maxY = Math.max(maxY, p.getPos().y);
            width = Math.max(width, p.getPos().x);
        }
        int cy = (minY + maxY) / 2;
        int left = 4;
        int right = width - 4;
        int top = minY - 10;
        int bottom = maxY + 10;
        Polygon box = new Polygon().add(left + 5, top).add(right - 5, top).add(right, top + 5)
                .add(right, bottom - 5).add(right - 5, bottom).add(left + 5, bottom).add(left, bottom - 5).add(left, top + 5);
        g.drawPolygon(box, INK);
        g.drawPolygon(box, GOLD);
        for (Pin p : getPins()) {
            boolean in = p.getDirection() == Pin.Direction.input;
            Vector end = new Vector(in ? left : right, p.getPos().y);
            g.drawLine(p.getPos(), end, CYAN);
            g.drawCircle(end.add(-2, -2), end.add(2, 2), GOLD);
        }
        String label = name.equals("And") ? "AND" : name.equals("XOr") ? "XOR" : name.equals("Not") ? "NOT" : "OR";
        if (story)
            label = name.equals("And") ? "双印" : name.equals("XOr") ? "单席" : name.equals("Not") ? "反相" : "任一";
        int size = width < 40 ? 10 : 13;
        g.drawText(new Vector(width / 2, cy), label, Orientation.CENTERCENTER, TEXT.deriveFontStyle(size, false));
        g.drawLine(new Vector(width / 2 - 7, top + 4), new Vector(width / 2 + 7, top + 4), CYAN);
        g.drawLine(new Vector(width / 2 - 7, bottom - 4), new Vector(width / 2 + 7, bottom - 4), CYAN);
        if (!attributes.getLabel().isEmpty())
            g.drawText(new Vector(width / 2, top - 5), attributes.getLabel(), Orientation.CENTERBOTTOM, TEXT);
    }
}
