/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.element.PinDescription;
import de.neemann.digital.core.io.In;
import de.neemann.digital.core.io.Out;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.Pin;
import de.neemann.digital.draw.elements.PinException;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.draw.model.Net;
import de.neemann.digital.draw.model.NetList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the structure of the player's circuit to tick the steps of the repair guide.
 * Only a guide: passing is decided by {@link LevelValidator} alone.
 */
final class RepairGuide {
    private RepairGuide() {
    }

    /**
     * The detected progress
     */
    static final class Status {
        private final boolean placed;
        private final boolean[] inputs;
        private final boolean[] outputs;
        private final boolean[] runes;
        private final boolean inputsShorted;

        private Status(boolean placed, boolean[] inputs, boolean output, boolean inputsShorted) {
            this(placed, inputs, new boolean[]{output}, inputsShorted, new boolean[]{placed});
        }

        private Status(boolean placed, boolean[] inputs, boolean[] outputs, boolean inputsShorted, boolean[] runes) {
            this.placed = placed;
            this.inputs = inputs.clone();
            this.outputs = outputs.clone();
            this.inputsShorted = inputsShorted;
            this.runes = runes.clone();
        }

        /**
         * @return the rune of the level exists; always true for a level wired without a rune
         */
        boolean isAndPlaced() {
            return placed;
        }

        /**
         * @param k the output index
         * @return the rune of output k exists
         */
        boolean isPlaced(int k) {
            return k < runes.length && runes[k];
        }

        /**
         * @param i the input index
         * @return the input station i is wired to its own input of the rune, or directly to the output;
         * with several outputs to an input of every rune
         */
        boolean isInput(int i) {
            return i < inputs.length && inputs[i];
        }

        /**
         * @return the first input station is wired to an input of the rune
         */
        boolean isFirstInput() {
            return isInput(0);
        }

        /**
         * @return the second input station is wired to another input of the same rune; true for one input
         */
        boolean isSecondInput() {
            return inputs.length < 2 || inputs[1];
        }

        /**
         * @return the rune output, or the station of a level without a rune, is wired to the level output;
         * with several outputs every rune to its own output
         */
        boolean isOutput() {
            for (boolean b : outputs)
                if (!b)
                    return false;
            return true;
        }

        /**
         * @param k the output index
         * @return the rune of output k is wired to that output
         */
        boolean isOutput(int k) {
            return k < outputs.length && outputs[k];
        }

        /**
         * @return two station outputs touch each other
         */
        boolean isInputsShorted() {
            return inputsShorted;
        }

        /**
         * @return all wiring steps are done
         */
        boolean isWired() {
            if (!placed || !isOutput() || inputsShorted)
                return false;
            for (boolean b : inputs)
                if (!b)
                    return false;
            return true;
        }
    }

    /**
     * Analyses a circuit with one rune per output, e.g. the star lamps: every station has to reach an
     * input of every rune, and every rune has to reach its own output.
     *
     * @param circuit the circuit
     * @param inputs  labels of the inputs
     * @param outs    labels of the outputs
     * @param runes   Digital element names of the runes, one per output; with a single output the
     *                first rune, empty if the stations are wired directly
     * @return the status
     */
    static Status analyze(Circuit circuit, List<String> inputs, List<String> outs, List<String> runes) {
        if (outs.size() <= 1 || runes.size() != outs.size())
            return analyze(circuit, inputs, outs.get(0), runes.isEmpty() ? "" : runes.get(0));
        boolean placed = true;
        boolean shorted = false;
        boolean[] in = new boolean[inputs.size()];
        Arrays.fill(in, true);
        boolean[] out = new boolean[outs.size()];
        boolean[] there = new boolean[outs.size()];
        for (int k = 0; k < outs.size(); k++) {
            Status s = analyze(circuit, inputs, outs.get(k), runes.get(k));
            there[k] = s.placed;
            placed &= s.placed;
            out[k] = s.isOutput();
            shorted |= s.inputsShorted;
            for (int i = 0; i < in.length; i++)
                in[i] &= s.isInput(i);
        }
        return new Status(placed, in, out, shorted, there);
    }

    /**
     * Analyses a circuit of two stations and a double seal rune.
     *
     * @param circuit the circuit
     * @param first   label of the first input
     * @param second  label of the second input
     * @param out     label of the output
     * @return the status
     */
    static Status analyze(Circuit circuit, String first, String second, String out) {
        return analyze(circuit, Arrays.asList(first, second), out, "And");
    }

    /**
     * Analyses the circuit.
     *
     * @param circuit the circuit
     * @param inputs  labels of the inputs
     * @param out     label of the output
     * @param rune    Digital element name of the rune between the stations and the output,
     *                null or empty if the stations are wired directly
     * @return the status
     */
    static Status analyze(Circuit circuit, List<String> inputs, String out, String rune) {
        boolean direct = rune == null || rune.isEmpty();
        NetList nets;
        try {
            nets = new NetList(circuit);
        } catch (PinException | RuntimeException e) {
            return new Status(direct, new boolean[inputs.size()], false, false);
        }
        List<Vector> stations = new ArrayList<>();
        for (String in : inputs)
            stations.add(pin(circuit, In.DESCRIPTION.getName(), in, PinDescription.Direction.output));
        Vector o = pin(circuit, Out.DESCRIPTION.getName(), out, PinDescription.Direction.input);
        boolean shorted = false;
        for (int i = 0; i < stations.size(); i++)
            for (int j = i + 1; j < stations.size(); j++)
                shorted |= connected(nets, stations.get(i), stations.get(j));

        if (direct) {
            boolean[] s = new boolean[stations.size()];
            boolean all = !stations.isEmpty();
            for (int i = 0; i < s.length; i++) {
                s[i] = connected(nets, stations.get(i), o);
                all &= s[i];
            }
            return new Status(true, s, all, shorted);
        }

        boolean placed = false;
        int bestScore = -1;
        boolean[] best = new boolean[stations.size()];
        boolean bestOut = false;
        for (VisualElement ve : circuit.getElements()) {
            if (!ve.getElementName().equals(rune))
                continue;
            placed = true;
            List<Vector> ins = new ArrayList<>();
            Vector runeOut = null;
            for (Pin p : ve.getPins())
                if (p.getDirection() == PinDescription.Direction.input)
                    ins.add(p.getPos());
                else
                    runeOut = p.getPos();
            boolean[] s = new boolean[stations.size()];
            boolean[] used = new boolean[ins.size()];
            int score = 0;
            for (int i = 0; i < s.length; i++) {
                int k = indexOf(nets, ins, stations.get(i), used);
                s[i] = k >= 0;
                if (s[i]) {
                    used[k] = true;
                    score++;
                }
            }
            boolean toOut = connected(nets, runeOut, o);
            score += toOut ? 1 : 0;
            if (score > bestScore) {
                bestScore = score;
                best = s;
                bestOut = toOut;
            }
        }
        return new Status(placed, best, bestOut, shorted);
    }

    private static int indexOf(NetList nets, List<Vector> ins, Vector source, boolean[] used) {
        for (int i = 0; i < ins.size(); i++)
            if (!used[i] && connected(nets, ins.get(i), source))
                return i;
        return -1;
    }

    private static Vector pin(Circuit circuit, String elementName, String label, PinDescription.Direction dir) {
        for (VisualElement ve : circuit.getElements())
            if (ve.getElementName().equals(elementName) && label.equals(ve.getElementAttributes().getLabel()))
                for (Pin p : ve.getPins())
                    if (p.getDirection() == dir)
                        return p.getPos();
        return null;
    }

    private static boolean connected(NetList nets, Vector p, Vector q) {
        if (p == null || q == null)
            return false;
        if (p.equals(q))
            return true;
        Net n = nets.getNetOfPos(p);
        return n != null && n.contains(q);
    }
}
