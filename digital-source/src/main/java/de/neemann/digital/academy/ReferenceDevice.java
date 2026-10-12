/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.Model;
import de.neemann.digital.core.Signal;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.model.ModelCreator;
import de.neemann.digital.draw.shapes.ShapeFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The hidden reference device used in the experiment stage.
 * The reference circuit is simulated by Digital in its own model; it is never shown to the player.
 */
public final class ReferenceDevice implements Closeable {
    private final LevelDefinition level;
    private final Model model;

    /**
     * Loads the reference circuit and creates its model.
     *
     * @param level the level
     * @throws IOException if the circuit could not be loaded or the model not created
     */
    public ReferenceDevice(LevelDefinition level) throws IOException {
        this(level, level.getReferenceFile());
    }

    /**
     * Loads another circuit with the level's ports, e.g. a candidate explanation of the investigation.
     *
     * @param level   the level giving the input and output ports
     * @param circuitFile the circuit to simulate
     * @throws IOException if the circuit could not be loaded or the model not created
     */
    ReferenceDevice(LevelDefinition level, File circuitFile) throws IOException {
        this.level = level;
        ElementLibrary library = new ElementLibrary();
        library.setRootFilePath(circuitFile.getAbsoluteFile().getParentFile());
        Circuit circuit = Circuit.loadCircuit(circuitFile, new ShapeFactory(library));
        try {
            model = new ModelCreator(circuit, library).createModel(false);
            model.init(false);
        } catch (Exception e) {
            throw new IOException("reference circuit could not be simulated", e);
        }
    }

    /**
     * Applies the given inputs, lets the simulation settle and reads the outputs.
     *
     * @param inputs the input values by port id; all level inputs must be given
     * @return the stable reading of the level's outputs, packed with the first output as the highest bit
     */
    public Reading evaluate(Map<String, Boolean> inputs) {
        if (!model.isRunning())
            return Reading.error("参考装置已停止");
        for (String in : level.getInputs()) {
            Signal s = find(model.getInputs(), in);
            Boolean v = inputs.get(in);
            if (s == null || v == null)
                return Reading.error("参考装置缺少输入 " + in);
            s.getValue().setBool(v);
        }
        model.doStep();
        if (!model.isRunning())
            return Reading.error("参考装置仿真出错");
        List<Reading> parts = new ArrayList<>();
        for (String name : level.getOutputs()) {
            Signal out = find(model.getOutputs(), name);
            if (out == null)
                return Reading.MISSING;
            parts.add(Reading.of(out.getValue()));
        }
        return Reading.pack(parts);
    }

    static Signal find(Iterable<Signal> signals, String name) {
        for (Signal s : signals)
            if (s.getName().equals(name))
                return s;
        return null;
    }

    @Override
    public void close() {
        model.close();
    }
}
