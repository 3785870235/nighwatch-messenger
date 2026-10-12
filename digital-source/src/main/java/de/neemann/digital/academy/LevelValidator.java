/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.element.ElementAttributes;
import de.neemann.digital.data.Value;
import de.neemann.digital.data.ValueTable;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.PinException;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.lang.Lang;
import de.neemann.digital.testing.MatchedValue;
import de.neemann.digital.testing.TestExecutor;
import de.neemann.digital.testing.TestResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Trusted validation of a player circuit.
 * The test vectors are taken from the level resources, never from the player circuit.
 * The tests run on an independent copy of the circuit, so the player's circuit, its running
 * model and its input states are not touched, and nothing is drawn or played.
 */
public final class LevelValidator {
    private static final String IN = "In";
    private static final String OUT = "Out";
    private static final String MARK = "\u0001";

    private final LevelDefinition level;
    private final ElementLibrary library;
    private final ShapeFactory shapeFactory;
    private final List<Circuit.TestCase> testCases;

    /**
     * Creates a validator for the given level.
     *
     * @param level the level
     * @throws IOException if the trusted test cases could not be loaded
     */
    public LevelValidator(LevelDefinition level) throws IOException {
        this.level = level;
        library = new ElementLibrary();
        shapeFactory = new ShapeFactory(library);
        testCases = Circuit.loadCircuit(level.getTestsFile(), shapeFactory).getTestCases();
        if (testCases.isEmpty())
            throw new IOException("no trusted test cases in " + level.getTestsFile());
    }

    /**
     * Serializes a circuit. Call this on the thread owning the circuit (the EDT for the editor).
     *
     * @param circuit the circuit
     * @return the circuit as XML
     * @throws IOException IOException
     */
    public static byte[] snapshot(Circuit circuit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        circuit.save(out);
        return out.toByteArray();
    }

    /**
     * Validates a circuit file.
     *
     * @param file the circuit file
     * @return the result
     * @throws IOException IOException
     */
    public ValidationResult validate(File file) throws IOException {
        return validate(Circuit.loadCircuit(file, shapeFactory));
    }

    /**
     * Validates a circuit snapshot created by {@link #snapshot(Circuit)}.
     * May be called from a worker thread.
     *
     * @param circuitXml the snapshot
     * @return the result
     * @throws IOException IOException
     */
    public ValidationResult validate(byte[] circuitXml) throws IOException {
        return validate(Circuit.loadCircuit(new ByteArrayInputStream(circuitXml), shapeFactory));
    }

    private ValidationResult validate(Circuit copy) {
        ValidationResult result = new ValidationResult();
        checkStructure(copy, result);
        checkLogic(copy, result);
        return result;
    }

    private void checkStructure(Circuit circuit, ValidationResult result) {
        Map<String, Integer> inCount = new HashMap<>();
        Map<String, Integer> outCount = new HashMap<>();
        for (VisualElement ve : circuit.getElements()) {
            String name = ve.getElementName();
            if (!level.getAllowed().contains(name)) {
                result.addStructureError("本关不使用元件 " + name + "（可用：" + String.join("、", level.getAllowed()) + "）");
                continue;
            }
            ElementAttributes attr = ve.getElementAttributes();
            if (name.equals(IN) || name.equals(OUT)) {
                String label = attr.getLabel();
                boolean isIn = name.equals(IN);
                List<String> ports = isIn ? level.getInputs() : level.getOutputs();
                if (!ports.contains(label)) {
                    result.addStructureError("多余的" + (isIn ? "输入" : "输出") + "端口“" + label + "”");
                    continue;
                }
                (isIn ? inCount : outCount).merge(label, 1, Integer::sum);
                if (attr.getBits() != 1)
                    result.addStructureError("端口 " + label + " 应为 1 位，当前为 " + attr.getBits() + " 位");
            }
        }
        checkCount(level.getInputs(), inCount, "输入", result);
        checkCount(level.getOutputs(), outCount, "输出", result);
    }

    private void checkCount(List<String> ports, Map<String, Integer> count, String kind, ValidationResult result) {
        for (String p : ports) {
            int n = count.getOrDefault(p, 0);
            if (n == 0)
                result.addStructureError("缺少" + kind + "端口 " + p + "（" + level.getPortLabel(p) + "）");
            else if (n > 1)
                result.addStructureError(kind + "端口 " + p + " 出现了 " + n + " 次");
        }
    }

    private void checkLogic(Circuit circuit, ValidationResult result) {
        int tested = 0;
        int expected = 0;
        try {
            for (Circuit.TestCase tc : testCases) {
                expected += 1 << level.getInputs().size();
                TestResult tr = new TestExecutor(tc, circuit, library).execute();
                if (tr.isErrorOccurred())
                    result.setModelError("仿真出错（可能存在冲突或振荡）", ValidationResult.Trouble.UNSTABLE);
                tested += tr.getRowsTested();
                collectChecks(tr.getValueTable(), result);
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            PinException pin = pinException(e);
            if (pin != null && isMessage(pin.getMessage(), "err_noValueSetFor_N0_atElement_N1", 2)) {
                result.setModelError(message, ValidationResult.Trouble.LOOSE);
                VisualElement ve = pin.getVisualElement();
                result.setLooseElement(ve == null ? null : ve.getElementName());
            } else if (pin != null && isMessage(pin.getMessage(), "err_noOutConnectedToWire", 1))
                result.setModelError(message, ValidationResult.Trouble.LOOSE);
            else
                result.setModelError(message, ValidationResult.Trouble.OTHER);
        }
        result.setRows(tested, expected);
    }

    private static PinException pinException(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause())
            if (t instanceof PinException)
                return (PinException) t;
        return null;
    }

    /**
     * Tells whether a message was made from the given language key, whatever its arguments were.
     *
     * @param message the message
     * @param key     the language key
     * @param args    the number of arguments of the key
     * @return true if the message matches
     */
    static boolean isMessage(String message, String key, int args) {
        if (message == null)
            return false;
        Object[] marks = new Object[args];
        Arrays.fill(marks, MARK);
        String[] parts = Lang.get(key, marks).split(Pattern.quote(MARK), -1);
        StringBuilder re = new StringBuilder("(?s)");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                re.append(".*");
            re.append(Pattern.quote(parts[i]));
        }
        return message.matches(re.toString());
    }

    private void collectChecks(ValueTable table, ValidationResult result) {
        for (int r = 0; r < table.getTableRows(); r++) {
            Value[] values = table.getTableRow(r).getValues();
            LinkedHashMap<String, String> inputs = new LinkedHashMap<>();
            for (int c = 0; c < table.getColumns(); c++)
                if (level.getInputs().contains(table.getColumnName(c)))
                    inputs.put(table.getColumnName(c), String.valueOf(values[c]));
            for (int c = 0; c < table.getColumns(); c++) {
                Value v = values[c];
                if (v instanceof MatchedValue) {
                    String output = table.getColumnName(c);
                    String expected = String.valueOf(((MatchedValue) v).getExpected());
                    boolean passed = v.getState() != Value.State.FAIL;
                    result.addCheck(new ValidationResult.Check(inputs, output, expected,
                            v.isHighZ() ? null : Long.toString(v.getValue()), passed));
                    if (!passed) {
                        String actual = v.isHighZ() ? "高阻（未接通）" : Long.toString(v.getValue());
                        result.addCounterexample(new ValidationResult.Counterexample(inputs, output, expected, actual));
                    }
                }
            }
        }
    }
}
