/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.shapes.ShapeFactory;
import junit.framework.TestCase;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * The star lamps of chapter five answer together: the reading is the high lamp times two plus the
 * low lamp. Combination bit order: A is the high bit, so 1 = A0 B1, 2 = A1 B0, 3 = A1 B1.
 */
public class StarLampsTest extends TestCase {
    private static final File LEVEL_DIR = new File("../levels/05_star_lamps");
    private static final File FIXTURES = new File(LEVEL_DIR, "fixtures");

    private LevelDefinition level;
    private LevelValidator validator;
    private ResultLetter.Clerk clerk;

    @Override
    protected void setUp() throws Exception {
        level = LevelDefinition.load(new File(LEVEL_DIR, "level.properties"));
        validator = new LevelValidator(level);
        clerk = ResultLetter.Clerk.of(level);
    }

    public void testLevelDefinition() {
        assertEquals("05_star_lamps", level.getId());
        assertEquals(Arrays.asList("C", "S"), level.getOutputs());
        assertEquals("高星灯", level.getPortLabel("C"));
        assertEquals("低星灯", level.getPortLabel("S"));
        assertTrue(level.isLampScene());
        assertEquals(Arrays.asList("And", "XOr"), level.getRepairRunes());
        assertEquals(Arrays.asList("双印符文", "单席符文"), level.getRepairRuneNames());
        assertEquals("双印符文和单席符文", level.getRepairRuneName());
    }

    public void testReferenceCountsTwoPeopleOnTheHighLamp() throws Exception {
        ExperimentSession s = new ExperimentSession(level.getInputs());
        try (ReferenceDevice ref = new ReferenceDevice(level)) {
            long[] expected = {0, 1, 1, 2};
            for (int c = 0; c < 4; c++)
                assertEquals("combination " + c, expected[c], ref.evaluate(s.toInputs(c)).getValue());
        }
    }

    /** Only the two-person report separates the true page from the log's way of reading the lamps. */
    public void testCandidatesAndTheirEvidence() throws Exception {
        Investigation inv = Investigation.load(level);
        List<Investigation.Candidate> pages = inv.getCandidates();
        assertEquals(3, pages.size());
        long[][] expected = {{0, 1, 1, 3}, {0, 2, 1, 3}, {0, 1, 1, 2}};
        for (int i = 0; i < 3; i++)
            for (int c = 0; c < 4; c++)
                assertEquals(pages.get(i).getId() + " " + c, expected[i][c], pages.get(i).predict(c).getValue());

        ExperimentSession s = new ExperimentSession(level.getInputs());
        try (ReferenceDevice ref = new ReferenceDevice(level)) {
            s.observe(0, ref.evaluate(s.toInputs(0)));
            s.observe(2, ref.evaluate(s.toInputs(2)));
            assertEquals("one person at A, or nobody, refutes no page", 3, inv.remaining(s).size());
            s.observe(1, ref.evaluate(s.toInputs(1)));
            assertEquals(Arrays.asList("station"), ids(inv.refutedAt(1, s)));
            assertTrue(inv.separates(3, s));
            s.predictAndObserve(3, 3L, ref.evaluate(s.toInputs(3)));
            assertEquals(Arrays.asList("count", "station"), ids(inv.refutedAt(3, s)));
            assertEquals("carry", inv.discovered(s).getId());
            assertEquals("岗位 A 持钥印 · 岗位 B 持钥印时，星灯只亮了高的那盏，它却说星灯两盏都会亮",
                    inv.counterText(inv.find("count"), s));
        }
    }

    public void testBothSpellsPass() throws Exception {
        for (File f : new File[]{level.getReferenceFile(), new File(FIXTURES, "binary_correct.dig")}) {
            ResultLetter l = clerk.reply(validator.validate(f));
            assertEquals(f.getName(), ResultLetter.State.PASSED, l.getState());
            // one row for every lamp of every case
            assertEquals(8, l.getRows().size());
            assertTrue(l.text(), l.text().contains("四种情形，副本的回应全部合乎塔规"));
            assertTrue(l.text(), l.text().contains("岗位 A 持钥印 · 岗位 B 持钥印 → 塔规：高星灯 亮 · 副本：高星灯 亮 ✓"));
            assertTrue(l.text(), l.text().contains("岗位 A 持钥印 · 岗位 B 持钥印 → 塔规：低星灯 不亮 · 副本：低星灯 不亮 ✓"));
        }
    }

    public void testAnyRuneOnTheLowLampLightsBoth() throws Exception {
        ResultLetter l = clerk.reply(validator.validate(new File(FIXTURES, "or_wrong.dig")));
        assertEquals(ResultLetter.State.FAILED, l.getState());
        int failed = 0;
        for (ResultLetter.Row r : l.getRows())
            if (!r.isPassed())
                failed++;
        assertEquals(1, failed);
        assertTrue(l.text(), l.text().contains("岗位 A 持钥印 · 岗位 B 持钥印 → 塔规：低星灯 不亮 · 副本：低星灯 亮 ✗"));
    }

    public void testUnconnectedLampsAreNeverDark() throws Exception {
        for (File f : new File[]{level.getTemplateFile(), new File(FIXTURES, "unconnected.dig")}) {
            ResultLetter l = clerk.reply(validator.validate(f));
            assertEquals(f.getName(), ResultLetter.State.FAILED, l.getState());
            assertTrue(l.text(), l.text().contains("无讯不作否"));
            assertFalse(l.text(), l.text().contains("不亮 ✗"));
            assertTrue(l.text(), l.text().contains("有一枚许可印还没有接上讯线"));
        }
    }

    public void testRepairGuideWantsOneRunePerLamp() throws Exception {
        ShapeFactory shapes = new ShapeFactory(new ElementLibrary());
        RepairGuide.Status done = analyze(Circuit.loadCircuit(new File(FIXTURES, "binary_correct.dig"), shapes));
        assertTrue(done.isWired());
        assertTrue(done.isPlaced(0) && done.isPlaced(1));

        RepairGuide.Status loose = analyze(Circuit.loadCircuit(new File(FIXTURES, "unconnected.dig"), shapes));
        assertTrue(loose.isPlaced(0) && loose.isPlaced(1));
        assertTrue(loose.isInput(0) && loose.isInput(1));
        assertFalse(loose.isOutput(0) || loose.isOutput(1));
        assertFalse(loose.isWired());

        // the any rune is not the single seat rune: the low lamp's rune counts as missing
        RepairGuide.Status any = analyze(Circuit.loadCircuit(new File(FIXTURES, "or_wrong.dig"), shapes));
        assertTrue(any.isPlaced(0));
        assertFalse(any.isPlaced(1));
        assertFalse(any.isWired());

        RepairGuide.Status empty = analyze(Circuit.loadCircuit(level.getTemplateFile(), shapes));
        assertFalse(empty.isPlaced(0) || empty.isPlaced(1));
        assertFalse(empty.isInputsShorted());
    }

    private RepairGuide.Status analyze(Circuit c) {
        return RepairGuide.analyze(c, level.getInputs(), level.getOutputs(), level.getRepairRunes());
    }

    private static List<String> ids(List<Investigation.Candidate> l) {
        String[] r = new String[l.size()];
        for (int i = 0; i < r.length; i++)
            r[i] = l.get(i).getId();
        return Arrays.asList(r);
    }
}
