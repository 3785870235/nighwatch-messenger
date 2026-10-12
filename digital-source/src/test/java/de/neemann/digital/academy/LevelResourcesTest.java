/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Every hand-written circuit of every chapter must simulate through the same path the game uses.
 * A wire that misses a pin does not fail to load: Digital silently turns that net into an
 * unconnected one and the circuit is only rejected later, at model creation. That is how two
 * candidate pages stayed broken while the whole suite remained green. The chapter opens by
 * simulating its reference and all of its candidate models, so one bad file takes down a chapter.
 */
public class LevelResourcesTest extends TestCase {
    private static final File LEVELS = new File("../levels");

    private File[] levelDirs() {
        File[] dirs = LEVELS.listFiles(File::isDirectory);
        assertNotNull("levels directory not found: " + LEVELS.getAbsolutePath(), dirs);
        List<File> kept = new ArrayList<>();
        for (File dir : dirs)
            if (new File(dir, "level.properties").isFile())
                kept.add(dir);
        assertFalse("no chapters under " + LEVELS.getAbsolutePath(), kept.isEmpty());
        return kept.toArray(new File[0]);
    }

    /** The reference device of a chapter is also what the home screen loads to gate the chapter. */
    public void testEveryReferenceDeviceSimulates() throws Exception {
        for (File dir : levelDirs()) {
            LevelDefinition level = LevelDefinition.load(new File(dir, "level.properties"));
            try (ReferenceDevice ref = new ReferenceDevice(level)) {
                ExperimentSession s = new ExperimentSession(level.getInputs());
                for (int c = 0; c < s.getCombinationCount(); c++)
                    assertTrue(level.getId() + " combination " + c + " is unreadable",
                            ref.evaluate(s.toInputs(c)).isValid());
            }
        }
    }

    /**
     * Candidate pages are simulated while the chapter opens; a page that can not predict is a
     * broken file, not an explanation the player is allowed to see.
     */
    public void testEveryCandidateModelSimulates() throws Exception {
        for (File dir : levelDirs()) {
            LevelDefinition level = LevelDefinition.load(new File(dir, "level.properties"));
            Investigation investigation = Investigation.load(level);
            if (investigation == null)
                continue;
            assertFalse(level.getId() + " has no candidates", investigation.getCandidates().isEmpty());
            int combinations = new ExperimentSession(level.getInputs()).getCombinationCount();
            for (Investigation.Candidate candidate : investigation.getCandidates())
                for (int c = 0; c < combinations; c++)
                    assertTrue(level.getId() + " " + candidate.getId() + " can not predict " + c,
                            candidate.predict(c).isValid());
        }
    }

    /**
     * The candidate pages of a chapter must not predict the same thing: three identical pages would
     * leave nothing for the player to separate.
     */
    public void testCandidatePagesDiffer() throws Exception {
        for (File dir : levelDirs()) {
            LevelDefinition level = LevelDefinition.load(new File(dir, "level.properties"));
            Investigation investigation = Investigation.load(level);
            if (investigation == null)
                continue;
            List<Investigation.Candidate> candidates = investigation.getCandidates();
            int combinations = new ExperimentSession(level.getInputs()).getCombinationCount();
            for (int a = 0; a < candidates.size(); a++)
                for (int b = a + 1; b < candidates.size(); b++) {
                    boolean differs = false;
                    // compare whole readings: two lamps answer with one of four values, not with lit or dark
                    for (int c = 0; c < combinations; c++)
                        if (!candidates.get(a).predict(c).same(candidates.get(b).predict(c)))
                            differs = true;
                    assertTrue(level.getId() + " pages " + candidates.get(a).getId() + " and "
                            + candidates.get(b).getId() + " never disagree", differs);
                }
        }
    }
}
