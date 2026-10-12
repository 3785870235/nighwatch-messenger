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
import junit.framework.TestCase;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests the archive gate level (P1): reference device, trusted validation, isolation and experiment rules.
 * Level resources are read from ../levels relative to the Maven base directory.
 */
public class ArchiveGateTest extends TestCase {
    private static final File LEVEL_DIR = new File("../levels/03_archive_gate");
    private static final File FIXTURES = new File(LEVEL_DIR, "fixtures");

    private LevelDefinition level;
    private LevelValidator validator;

    @Override
    protected void setUp() throws Exception {
        assertTrue("level dir not found: " + LEVEL_DIR.getAbsolutePath(), LEVEL_DIR.isDirectory());
        level = LevelDefinition.load(new File(LEVEL_DIR, "level.properties"));
        validator = new LevelValidator(level);
    }

    public void testLevelDefinition() {
        assertEquals("03_archive_gate", level.getId());
        assertEquals(Arrays.asList("A", "B"), level.getInputs());
        assertEquals(Arrays.asList("PERMIT"), level.getOutputs());
        assertEquals("门开", level.getPortLabel("PERMIT"));
        assertEquals("岗位 A", level.getPortLabel("A"));
        assertEquals(3, level.getHints().size());
    }

    public void testReferenceDeviceIsAnd() throws Exception {
        ExperimentSession s = new ExperimentSession(level.getInputs());
        try (ReferenceDevice ref = new ReferenceDevice(level)) {
            long[] expected = {0, 0, 0, 1};
            for (int c = 0; c < 4; c++) {
                Reading r = ref.evaluate(s.toInputs(c));
                assertTrue(r.isValid());
                assertEquals("combination " + c, expected[c], r.getValue());
            }
            // order independent: evaluate again backwards
            for (int c = 3; c >= 0; c--)
                assertEquals(expected[c], ref.evaluate(s.toInputs(c)).getValue());
        }
    }

    public void testReferenceDeviceClosed() throws Exception {
        ReferenceDevice ref = new ReferenceDevice(level);
        ref.close();
        Reading r = ref.evaluate(new ExperimentSession(level.getInputs()).toInputs(3));
        assertFalse(r.isValid());
    }

    public void testReferenceAndTemplate() throws Exception {
        assertTrue(validator.validate(level.getReferenceFile()).isPassed());
        ValidationResult t = validator.validate(level.getTemplateFile());
        assertFalse(t.isPassed());
        assertFalse(t.isLogicPassed());
    }

    public void testCorrectAndPasses() throws Exception {
        ValidationResult r = validate("and_correct.dig");
        assertTrue(r.describe(), r.isPassed());
        assertEquals(4, r.getRowsTested());
        assertTrue(r.getCounterexamples().isEmpty());
        assertTrue(r.getStructureErrors().isEmpty());
    }

    public void testOrFails() throws Exception {
        ValidationResult r = validate("or_wrong.dig");
        assertFalse(r.isPassed());
        // the logic layer alone must reject OR, independent of the structure check
        assertFalse(r.isLogicPassed());
        assertEquals(set("A=0 B=1 → PERMIT 期望 0，实际 1", "A=1 B=0 → PERMIT 期望 0，实际 1"), counterexamples(r));
        assertTrue(r.getStructureErrors().get(0).contains("Or"));
    }

    public void testPlayerTestcaseIsNotTrusted() throws Exception {
        ValidationResult r = validate("or_with_fake_testcase.dig");
        assertFalse(r.isPassed());
        assertFalse(r.isLogicPassed());
        assertEquals(2, r.getCounterexamples().size());
    }

    public void testConstOneFails() throws Exception {
        ValidationResult r = validate("const_one.dig");
        assertFalse(r.isPassed());
        assertFalse(r.isLogicPassed());
        assertEquals(set("A=0 B=0 → PERMIT 期望 0，实际 1", "A=0 B=1 → PERMIT 期望 0，实际 1",
                "A=1 B=0 → PERMIT 期望 0，实际 1"), counterexamples(r));
    }

    public void testConstZeroFails() throws Exception {
        ValidationResult r = validate("const_zero.dig");
        assertFalse(r.isPassed());
        assertFalse(r.isLogicPassed());
        assertEquals(set("A=1 B=1 → PERMIT 期望 1，实际 0"), counterexamples(r));
    }

    public void testUnconnectedFails() throws Exception {
        ValidationResult r = validate("unconnected.dig");
        System.out.println("unconnected: " + r.describe());
        assertFalse(r.isPassed());
        assertFalse(r.isLogicPassed());
        // an undriven output must never be reported as a plain 0
        for (ValidationResult.Counterexample c : r.getCounterexamples())
            assertFalse(c.toString(), c.getActual().equals("0"));
    }

    public void testMissingPortFails() throws Exception {
        ValidationResult r = validate("missing_port.dig");
        System.out.println("missing_port: " + r.describe());
        assertFalse(r.isPassed());
        assertFalse(r.isLogicPassed());
        assertTrue(r.describe(), r.getStructureErrors().get(0).contains("缺少输出端口 PERMIT"));
    }

    /**
     * Validation must neither change the player's circuit nor the state of its running model.
     */
    public void testValidationIsIsolated() throws Exception {
        ElementLibrary library = new ElementLibrary();
        Circuit player = Circuit.loadCircuit(new File(FIXTURES, "and_correct.dig"), new ShapeFactory(library));
        Model model = new ModelCreator(player, library).createModel(false);
        model.init(false);
        Signal a = ReferenceDevice.find(model.getInputs(), "A");
        Signal b = ReferenceDevice.find(model.getInputs(), "B");
        a.getValue().setBool(true);
        b.getValue().setBool(true);
        model.doStep();
        Signal out = ReferenceDevice.find(model.getOutputs(), "PERMIT");
        assertEquals(1, out.getValue().getValue());

        byte[] before = LevelValidator.snapshot(player);
        assertTrue(validator.validate(before).isPassed());
        byte[] after = LevelValidator.snapshot(player);

        assertTrue("circuit changed by validation", Arrays.equals(before, after));
        assertTrue(model.isRunning());
        assertTrue(a.getValue().getBool());
        assertTrue(b.getValue().getBool());
        assertEquals(1, out.getValue().getValue());
        model.close();
    }

    public void testExperimentNeedsPrediction() {
        ExperimentSession s = new ExperimentSession(level.getInputs());
        Reading zero = reading(0);
        Reading one = reading(1);
        assertNull(s.getObserved(0));
        assertFalse(s.observe(0, Reading.NOT_RUNNING));
        assertEquals(0, s.getObservedCount());
        assertTrue(s.observe(0, zero));
        assertTrue(s.observe(1, zero));
        assertTrue(s.observe(2, zero));
        // last unobserved combination: must be predicted first
        assertFalse(s.canObserveWithoutPrediction(3));
        assertFalse(s.observe(3, one));
        assertFalse(s.isComplete());
        assertFalse(s.canPredict(0));
        assertTrue(s.predictAndObserve(3, true, one));
        assertTrue(s.isComplete());
        assertEquals(1, s.getCorrectPredictions());
    }

    public void testWrongPredictionIsRecorded() {
        ExperimentSession s = new ExperimentSession(level.getInputs());
        assertFalse(s.predictAndObserve(3, false, reading(1)));
        assertEquals(Boolean.FALSE, s.getPredicted(3));
        assertEquals(1, s.getObserved(3).getValue());
        assertEquals(1, s.getPredictions());
        assertEquals(0, s.getCorrectPredictions());
        assertFalse(s.isComplete());
    }

    private ValidationResult validate(String fixture) throws Exception {
        return validator.validate(new File(FIXTURES, fixture));
    }

    private static Set<String> counterexamples(ValidationResult r) {
        Set<String> s = new HashSet<>();
        for (ValidationResult.Counterexample c : r.getCounterexamples())
            s.add(c.toString());
        return s;
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    private static Reading reading(long v) {
        de.neemann.digital.core.ObservableValue ov = new de.neemann.digital.core.ObservableValue("x", 1);
        ov.setValue(v);
        return Reading.of(ov);
    }
}
