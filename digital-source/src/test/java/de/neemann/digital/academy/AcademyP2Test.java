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

/**
 * P2: progress, save protection and sandbox copies.
 */
public class AcademyP2Test extends TestCase {
    private static final File LEVEL = new File("../levels/03_archive_gate/level.properties");

    private File tmp;
    private LevelDefinition level;
    private ReferenceDevice ref;

    @Override
    protected void setUp() throws Exception {
        tmp = Files.createTempDirectory("academy-p2").toFile();
        level = LevelDefinition.load(LEVEL);
        ref = new ReferenceDevice(level);
    }

    @Override
    protected void tearDown() {
        if (ref != null)
            ref.close();
    }

    public void testProgressKeepsOnlyRealRecords() throws Exception {
        AcademyProgress p = new AcademyProgress(tmp, level);
        ExperimentSession s = new ExperimentSession(level.getInputs());
        s.observe(0, ref.evaluate(s.toInputs(0)));
        s.predictAndObserve(3, false, ref.evaluate(s.toInputs(3)));
        p.save(s, false, false);

        AcademyProgress.Restored r = new AcademyProgress(tmp, level).restore(ref::evaluate);
        assertNull(r.getMessage());
        ExperimentSession s2 = r.getSession();
        assertEquals(2, s2.getObservedCount());
        assertNull(s2.getObserved(1));
        assertNull(s2.getObserved(2));
        assertNull(s2.getPredicted(0));
        assertEquals(Boolean.FALSE, s2.getPredicted(3));
        assertEquals(1, s2.getPredictions());
        assertEquals(0, s2.getCorrectPredictions());
        assertFalse(r.isRebuild());
        assertFalse(r.isPassedOnce());
    }

    public void testRebuildAndHistoricPass() throws Exception {
        AcademyProgress p = new AcademyProgress(tmp, level);
        ExperimentSession s = new ExperimentSession(level.getInputs());
        for (int c = 0; c < 3; c++)
            s.observe(c, ref.evaluate(s.toInputs(c)));
        s.predictAndObserve(3, true, ref.evaluate(s.toInputs(3)));
        assertTrue(s.isComplete());
        p.save(s, true, true);
        AcademyProgress.Restored r = p.restore(ref::evaluate);
        assertNull(r.getMessage());
        assertTrue(r.isRebuild());
        assertTrue(r.isPassedOnce());
        assertTrue(r.getSession().isComplete());
    }

    public void testBrokenProgressIsBackedUp() throws Exception {
        AcademyProgress p = new AcademyProgress(tmp, level);
        String[] bad = {
                "version=1\nlevel=03_archive_gate\nfingerprint=0000\nobs.0=0\n",
                "version=0\nlevel=03_archive_gate\nfingerprint=" + AcademyProgress.fingerprint(level) + "\n",
                "version=1\nlevel=03_archive_gate\nfingerprint=" + AcademyProgress.fingerprint(level) + "\nobs.3=0\n",
                "version=1\nlevel=03_archive_gate\nfingerprint=" + AcademyProgress.fingerprint(level) + "\nobs.0=7\n",
                "version=1\nlevel=03_archive_gate\nfingerprint=" + AcademyProgress.fingerprint(level) + "\nrebuild=true\nobs.0=0\n",
                "version=1\nlevel=03_archive_gate\nfingerprint=" + AcademyProgress.fingerprint(level)
                        + "\nobs.0=0\nobs.1=0\nobs.2=0\nobs.3=1\n",
                "\\u12"};
        for (String content : bad) {
            Files.write(p.getFile().toPath(), content.getBytes(StandardCharsets.ISO_8859_1));
            AcademyProgress.Restored r = p.restore(ref::evaluate);
            assertNotNull(content, r.getMessage());
            assertTrue(r.getMessage().contains("已从新实验开始"));
            assertEquals(0, r.getSession().getObservedCount());
            assertFalse(p.getFile().exists());
        }
        assertEquals(bad.length, tmp.listFiles((d, n) -> n.contains(".bad-")).length);
    }

    public void testSavePolicy() throws Exception {
        File root = new File(tmp, "project");
        File levels = new File(root, "levels");
        assertTrue(new File(levels, "03").mkdirs());
        AcademySavePolicy policy = new AcademySavePolicy(Arrays.asList(levels, new File(root, "tools")));
        assertNotNull(policy.checkSave(new File(levels, "03/template.dig")));
        assertNotNull(policy.checkSave(new File(levels, "03/new/deeper/x.dig")));
        assertNotNull(policy.checkSave(new File(root, "LEVELS/a.dig")));
        assertNotNull(policy.checkSave(new File(root, "work/../levels/a.dig")));
        assertNotNull(policy.checkSave(new File(root, "tools/x.dig")));
        assertNull(policy.checkSave(new File(root, "work/sandbox/a.dig")));
        assertNull(policy.checkSave(new File(root, "levels2/a.dig")));

        AcademySavePolicy project = AcademySavePolicy.forProject(new File("..").getCanonicalFile());
        assertNotNull(project.checkSave(new File("../levels/03_archive_gate/template.dig")));
        assertNotNull(project.checkSave(new File("src/main/x.dig")));
        assertNull(project.checkSave(new File("../work/sandbox/x.dig")));
        String profile = System.getenv("USERPROFILE");
        if (profile != null)
            assertNotNull(project.checkSave(new File(profile, "Desktop/Digital/examples/x.dig")));
    }

    public void testUniqueSandboxCopy() throws Exception {
        File sandbox = new File(tmp, "sandbox");
        byte[] data = "a".getBytes(StandardCharsets.UTF_8);
        File a = AcademyHome.writeUnique(sandbox, "lvl", data);
        File b = AcademyHome.writeUnique(sandbox, "lvl", "b".getBytes(StandardCharsets.UTF_8));
        assertFalse(a.equals(b));
        assertEquals("a", new String(Files.readAllBytes(a.toPath()), StandardCharsets.UTF_8));
    }
}
