/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import junit.framework.TestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Story1: candidate explanations are judged only by real observations of the hidden door.
 * Combination bit order: A is the high bit, so 1 = A0 B1, 2 = A1 B0, 3 = A1 B1.
 */
public class InvestigationTest extends TestCase {
    private static final File LEVEL = new File("../levels/03_archive_gate/level.properties");

    private File tmp;
    private LevelDefinition level;
    private ReferenceDevice ref;
    private Investigation inv;
    private ExperimentSession s;

    @Override
    protected void setUp() throws Exception {
        tmp = Files.createTempDirectory("academy-story1").toFile();
        level = LevelDefinition.load(LEVEL);
        ref = new ReferenceDevice(level);
        inv = Investigation.load(level);
        s = new ExperimentSession(level.getInputs());
    }

    @Override
    protected void tearDown() {
        if (ref != null)
            ref.close();
    }

    private void see(int c) {
        assertTrue(s.observe(c, ref.evaluate(s.toInputs(c))));
    }

    private void guess(int c, boolean prediction) {
        s.predictAndObserve(c, prediction, ref.evaluate(s.toInputs(c)));
    }

    private Investigation.Candidate cand(String id) {
        return inv.find(id);
    }

    private List<String> ids(List<Investigation.Candidate> l) {
        String[] r = new String[l.size()];
        for (int i = 0; i < r.length; i++)
            r[i] = l.get(i).getId();
        return Arrays.asList(r);
    }

    public void testCandidatesPredictThroughTheirDigitalModels() {
        assertNotNull(inv);
        assertEquals(Arrays.asList("any", "both", "single"), ids(inv.getCandidates()));
        boolean[][] expected = {{false, true, true, true}, {false, false, false, true}, {false, true, true, false}};
        for (int i = 0; i < 3; i++)
            for (int c = 0; c < 4; c++) {
                Reading r = inv.getCandidates().get(i).predict(c);
                assertTrue(r.isValid());
                assertEquals(inv.getCandidates().get(i).getId() + " " + c, expected[i][c], r.isTrue());
            }
        assertFalse(inv.getBriefing().isEmpty());
        assertFalse(inv.getClue().isEmpty());
        // the briefing must neither reveal the rule nor the truth table
        for (Investigation.Line l : inv.getBriefing())
            assertFalse(l.getText(), l.getText().contains("AND") || l.getText().contains("与门") || l.getText().contains("真值表"));
    }

    public void testNothingObservedRefutesNothing() {
        assertEquals(3, inv.remaining(s).size());
        for (Investigation.Candidate c : inv.getCandidates()) {
            assertEquals(-1, inv.counterexample(c, s));
            assertTrue(inv.status(c, s).startsWith("尚无证据"));
        }
        assertNull(inv.discovered(s));
        assertFalse(inv.isSettled(s));
    }

    public void testZeroZeroCannotSeparate() {
        assertFalse(inv.separates(0, s));
        see(0);
        assertEquals(3, inv.remaining(s).size());
        assertTrue(inv.refutedAt(0, s).isEmpty());
        assertTrue(inv.observationNote(0, s).contains("已亲眼记录"));
        assertTrue(inv.observationNote(0, s).contains("门保持关闭"));
        assertTrue(inv.observationNote(3, s).contains("能区分"));
    }

    public void testOneOneRefutesSingleOnly() {
        assertTrue(inv.separates(3, s));
        see(3);
        assertEquals(Arrays.asList("any", "both"), ids(inv.remaining(s)));
        assertEquals(Collections.singletonList("single"), ids(inv.refutedAt(3, s)));
        assertEquals(3, inv.counterexample(cand("single"), s));
        assertEquals("岗位 A 持钥印 · 岗位 B 持钥印时，门开了，它却说门不开", inv.counterText(cand("single"), s));
        // the sprite speaks of stations and seals, never of A=1 or (0)
        inv.choose("single");
        assertEquals("「" + cand("single").getName() + "」已被你的记录反驳：岗位 A 持钥印 · 岗位 B 持钥印时，门开了，它却说门不开。",
                inv.choiceFeedback(s));
        assertFalse(inv.choiceFeedback(s), inv.choiceFeedback(s).matches("(?s).*[=01].*"));
        inv.choose(null);
        // 00 still does not separate the two that are left, 10 and 01 do
        assertFalse(inv.separates(0, s));
        assertTrue(inv.separates(1, s));
        assertTrue(inv.separates(2, s));
    }

    public void testOneZeroAndZeroOneRefuteAny() {
        see(2);
        assertEquals(Arrays.asList("any", "single"), ids(inv.refutedAt(2, s)));
        assertEquals(Collections.singletonList("both"), ids(inv.remaining(s)));
        ExperimentSession other = new ExperimentSession(level.getInputs());
        other.observe(1, ref.evaluate(other.toInputs(1)));
        assertEquals(Collections.singletonList("both"), ids(inv.remaining(other)));
        assertEquals(1, inv.counterexample(cand("any"), other));
    }

    public void testUnobservedCombinationsNeverDecide() {
        see(2);
        // a single survivor is not yet a discovery: two combinations were never seen
        assertEquals(1, inv.remaining(s).size());
        assertNull(inv.discovered(s));
        assertFalse(inv.isSettled(s));
        assertTrue(inv.verdict(s).contains("还有 3 组") || inv.verdict(s).contains("组报告没亲眼看过"));
        assertTrue(inv.observationNote(3, s).contains("能区分"));
    }

    /**
     * Before the player chooses, nothing may tell which page fell or which one is left: the note on an
     * observation only lists the claims, and the verdict counts pages without naming them.
     */
    public void testSpriteNeverNamesRefutedOrSurvivingPages() {
        for (int mask = 0; mask < 16; mask++) {
            s = new ExperimentSession(level.getInputs());
            for (int c = 0; c < 4; c++)
                if ((mask & 1 << c) != 0 && !s.observe(c, ref.evaluate(s.toInputs(c))))
                    guess(c, true);
            String verdict = inv.verdict(s);
            for (Investigation.Candidate c : inv.getCandidates())
                assertFalse(mask + " " + verdict, verdict.contains(c.getName()));
            for (int c = 0; c < 4; c++) {
                String note = inv.observationNote(c, s);
                assertFalse(mask + " " + note, note.contains("推翻") || note.contains("划掉") || note.contains("剩"));
            }
            for (Investigation.Candidate chosen : inv.getCandidates()) {
                inv.choose(chosen.getId());
                String feedback = inv.choiceFeedback(s);
                for (Investigation.Candidate other : inv.getCandidates())
                    if (other != chosen)
                        assertFalse(mask + " " + feedback, feedback.contains(other.getName()));
            }
            inv.choose(null);
        }
    }

    public void testDifferentOrdersReachTheSameEvidence() {
        int[][] orders = {{0, 1, 2, 3}, {3, 2, 1, 0}, {1, 3, 0, 2}, {2, 0, 3, 1}};
        for (int[] order : orders) {
            s = new ExperimentSession(level.getInputs());
            for (int i = 0; i < 3; i++)
                see(order[i]);
            assertFalse(inv.isSettled(s));
            // the last combination needs a prediction; a wrong one is recorded as wrong, not corrected
            guess(order[3], false);
            assertTrue(s.isComplete());
            assertTrue(inv.isSettled(s));
            assertEquals("both", inv.discovered(s).getId());
        }
    }

    public void testWrongPredictionIsKeptAndDoesNotCountAsEvidenceForTheGuess() {
        guess(2, true);
        assertEquals(Boolean.TRUE, s.getPredicted(2));
        assertFalse(s.getObserved(2).isTrue());
        assertEquals(0, s.getCorrectPredictions());
        assertEquals(Collections.singletonList("both"), ids(inv.remaining(s)));
    }

    public void testChoiceNeedsSufficientEvidenceAndMayBeChanged() {
        assertTrue(inv.choiceFeedback(s).contains("还没选择"));
        assertTrue(inv.choose("single"));
        assertFalse(inv.choose("single"));
        see(3);
        assertTrue(inv.choiceFeedback(s).contains("已被你的记录反驳"));
        assertFalse(inv.canRepair(s));
        assertTrue(inv.choose("both"));
        assertTrue(inv.choiceFeedback(s).contains("也没被推翻"));
        see(0);
        see(1);
        assertTrue(inv.choiceFeedback(s).contains("暂不定论"));
        assertFalse(inv.canRepair(s));
        guess(2, false);
        assertTrue(inv.canRepair(s));
        assertTrue(inv.choiceFeedback(s).startsWith("证据支持你的判断"));
        // changing to a refuted explanation takes the repair away again
        assertTrue(inv.choose("any"));
        assertFalse(inv.canRepair(s));
        assertTrue(inv.choose("nonsense"));
        assertNull(inv.getChoice());
        assertFalse(inv.choose(null));
    }

    public void testInvalidPredictionNeitherSupportsNorRefutes() {
        Reading[] broken = new Reading[4];
        for (int c = 0; c < 4; c++)
            broken[c] = c == 3 ? Reading.error("unconnected") : cand("single").predict(c);
        Investigation.Candidate b = new Investigation.Candidate("broken", "残页丁 · 断印", "坏的", null, broken);
        Investigation.Candidate both = cand("both");
        Investigation local = new Investigation(Arrays.asList(both, b), Collections.emptyList(), Collections.emptyList());
        see(3);
        assertEquals(-1, local.counterexample(b, s));
        assertEquals(0, local.support(b, s));
        assertEquals(1, local.support(both, s));
        assertTrue(local.status(b, s).contains("无法预测"));
        assertFalse(local.separates(3, new ExperimentSession(level.getInputs())));
        assertTrue(local.observationNote(3, s).contains("说不清"));
        // an unknown reading is never taken as 0: 00 of the broken model is valid 0, 11 stays unknown
        assertFalse(Reading.error("x").isValid());
    }

    public void testStoryStateRoundTripsOnlyThroughExplicitSave() throws Exception {
        AcademyProgress p = new AcademyProgress(tmp, level);
        see(3);
        p.save(s, false, false, null, "both", true);
        String text = new String(Files.readAllBytes(p.getFile().toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("story.choice=both"));
        assertTrue(text.contains("story.briefed=true"));
        // nothing about refuted candidates is stored; it follows from replayed observations
        assertFalse(text.contains("single"));
        AcademyProgress.Restored r = new AcademyProgress(tmp, level).restore(ref::evaluate);
        assertNull(r.getMessage());
        assertEquals("both", r.getChoice());
        assertTrue(r.isBriefed());
        assertEquals(Arrays.asList("any", "both"), ids(inv.remaining(r.getSession())));
    }

    public void testLegacyProgressWithoutStoryKeys() throws Exception {
        AcademyProgress p = new AcademyProgress(tmp, level);
        for (int c = 0; c < 3; c++)
            see(c);
        guess(3, true);
        p.save(s, true, true);
        String text = new String(Files.readAllBytes(p.getFile().toPath()), StandardCharsets.ISO_8859_1);
        assertFalse(text.contains("story."));
        AcademyProgress.Restored r = p.restore(ref::evaluate);
        assertNull(r.getMessage());
        assertNull(r.getChoice());
        assertFalse(r.isBriefed());
        assertTrue(r.isRebuild());
        assertTrue(r.isPassedOnce());
        assertEquals("both", inv.discovered(r.getSession()).getId());
        // the stored historical pass is no current pass and no choice is invented
        assertNull(inv.getChoice());
        assertFalse(inv.canRepair(r.getSession()));
    }

    public void testUnknownStoredChoiceIsIgnored() throws Exception {
        Files.write(new File(tmp, level.getId() + ".progress").toPath(), ("version=1\nlevel=" + level.getId()
                + "\nfingerprint=" + AcademyProgress.fingerprint(level) + "\nobs.0=0\nstory.choice=forged\nstory.briefed=maybe\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        AcademyProgress.Restored r = new AcademyProgress(tmp, level).restore(ref::evaluate);
        assertNull(r.getMessage());
        assertEquals(1, r.getSession().getObservedCount());
        assertFalse(r.isBriefed());
        inv.choose(r.getChoice());
        assertNull(inv.getChoice());
    }

    public void testFingerprintDoesNotDependOnTheStory() throws Exception {
        assertFalse(AcademyProgress.fingerprint(level).isEmpty());
        assertTrue(level.getStoryFile().isFile());
        // the story lives beside, not inside, the rule files that the fingerprint covers
        assertFalse(level.getStoryFile().equals(level.getReferenceFile()));
        assertFalse(level.getStoryFile().equals(level.getTestsFile()));
    }
}
