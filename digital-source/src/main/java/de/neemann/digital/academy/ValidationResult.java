/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of a validation run.
 */
public final class ValidationResult {
    /**
     * Why the tests could not run to the end.
     */
    public enum Trouble {
        /** nothing prevented the tests */
        NONE,
        /** a part is not connected, so no model could be built */
        LOOSE,
        /** the model ran, but messages collided or never settled */
        UNSTABLE,
        /** anything else */
        OTHER
    }

    private final List<String> structureErrors = new ArrayList<>();
    private final List<Counterexample> counterexamples = new ArrayList<>();
    private final List<Check> checks = new ArrayList<>();
    private String modelError;
    private Trouble trouble = Trouble.NONE;
    private String looseElement;
    private int rowsTested;
    private int rowsExpected;

    void addStructureError(String error) {
        structureErrors.add(error);
    }

    void addCounterexample(Counterexample c) {
        counterexamples.add(c);
    }

    void addCheck(Check c) {
        checks.add(c);
    }

    void setModelError(String modelError, Trouble trouble) {
        this.modelError = modelError;
        this.trouble = trouble;
    }

    void setLooseElement(String elementName) {
        this.looseElement = elementName;
    }

    void setRows(int tested, int expected) {
        this.rowsTested = tested;
        this.rowsExpected = expected;
    }

    /**
     * @return true only if the structure is correct and all trusted tests passed
     */
    public boolean isPassed() {
        return isLogicPassed() && structureErrors.isEmpty();
    }

    /**
     * @return true if the trusted tests passed, ignoring the structure check
     */
    public boolean isLogicPassed() {
        return modelError == null && counterexamples.isEmpty() && rowsExpected > 0 && rowsTested == rowsExpected;
    }

    /**
     * @return the structure errors
     */
    public List<String> getStructureErrors() {
        return Collections.unmodifiableList(structureErrors);
    }

    /**
     * @return the failing test rows
     */
    public List<Counterexample> getCounterexamples() {
        return Collections.unmodifiableList(counterexamples);
    }

    /**
     * @return every checked output of every test row, passed or not, in test order
     */
    public List<Check> getChecks() {
        return Collections.unmodifiableList(checks);
    }

    /**
     * @return the number of test rows the level expects
     */
    public int getRowsExpected() {
        return rowsExpected;
    }

    /**
     * @return the error which prevented the tests from running, or null
     */
    public String getModelError() {
        return modelError;
    }

    /**
     * @return why the tests could not run to the end; NONE if they did
     */
    public Trouble getTrouble() {
        return trouble;
    }

    /**
     * @return the element name of the part that is not connected (e.g. Out or And), or null if it is
     * a bare wire or not known
     */
    public String getLooseElement() {
        return looseElement;
    }

    /**
     * @return the number of test rows executed
     */
    public int getRowsTested() {
        return rowsTested;
    }

    /**
     * @return a multi line, player readable description
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (isPassed())
            sb.append("验证通过：").append(rowsTested).append(" 组输入全部符合。\n");
        for (String s : structureErrors)
            sb.append("结构问题：").append(s).append('\n');
        if (modelError != null)
            sb.append("无法完成测试：").append(modelError).append('\n');
        else if (rowsTested != rowsExpected)
            sb.append("只执行了 ").append(rowsTested).append('/').append(rowsExpected).append(" 组测试。\n");
        for (Counterexample c : counterexamples)
            sb.append("反例：").append(c).append('\n');
        return sb.toString().trim();
    }

    /**
     * A failing test row: inputs, expected and actual value of one output.
     */
    public static final class Counterexample {
        private final Map<String, String> inputs;
        private final String output;
        private final String expected;
        private final String actual;

        Counterexample(LinkedHashMap<String, String> inputs, String output, String expected, String actual) {
            this.inputs = Collections.unmodifiableMap(inputs);
            this.output = output;
            this.expected = expected;
            this.actual = actual;
        }

        /**
         * @return the inputs of this row
         */
        public Map<String, String> getInputs() {
            return inputs;
        }

        /**
         * @return the output id
         */
        public String getOutput() {
            return output;
        }

        /**
         * @return the expected value
         */
        public String getExpected() {
            return expected;
        }

        /**
         * @return the actual value; high impedance is reported as such, never as 0
         */
        public String getActual() {
            return actual;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : inputs.entrySet())
                sb.append(e.getKey()).append('=').append(e.getValue()).append(' ');
            return sb.append("→ ").append(output).append(" 期望 ").append(expected)
                    .append("，实际 ").append(actual).toString();
        }
    }

    /**
     * One checked output of one test row.
     */
    public static final class Check {
        private final Map<String, String> inputs;
        private final String output;
        private final String expected;
        private final String actual;
        private final boolean passed;

        /**
         * @param inputs   the inputs of the row
         * @param output   the output id
         * @param expected the expected value
         * @param actual   the actual value, or null if the output carried no message (high impedance)
         * @param passed   true if the row passed
         */
        Check(Map<String, String> inputs, String output, String expected, String actual, boolean passed) {
            this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
            this.output = output;
            this.expected = expected;
            this.actual = actual;
            this.passed = passed;
        }

        /**
         * @return the inputs of this row, in the order of the test
         */
        public Map<String, String> getInputs() {
            return inputs;
        }

        /**
         * @return the output id
         */
        public String getOutput() {
            return output;
        }

        /**
         * @return the expected value
         */
        public String getExpected() {
            return expected;
        }

        /**
         * @return the actual value, or null if the output carried no message; never 0 for nothing
         */
        public String getActual() {
            return actual;
        }

        /**
         * @return true if the output carried no message
         */
        public boolean isSilent() {
            return actual == null;
        }

        /**
         * @return true if the row passed
         */
        public boolean isPassed() {
            return passed;
        }
    }
}
