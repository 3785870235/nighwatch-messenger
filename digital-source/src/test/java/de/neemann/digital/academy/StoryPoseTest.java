/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import junit.framework.TestCase;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A story line may name the pose its speaker takes. The names are checked here against the art the
 * game has, and against the story: the letter Fu carries stays sealed until the archive, so in the
 * lamp chapters she is never shown holding it open.
 */
public class StoryPoseTest extends TestCase {
    private static final File LEVELS = new File("../levels");
    private static final Pattern POSE_KEY = Pattern.compile("(brief|clue)\\.(\\d+)\\.pose");

    private static LevelDefinition[] levels() throws Exception {
        File[] dirs = LEVELS.listFiles(File::isDirectory);
        assertNotNull("levels directory not found: " + LEVELS.getAbsolutePath(), dirs);
        java.util.ArrayList<LevelDefinition> kept = new java.util.ArrayList<>();
        for (File dir : dirs)
            if (new File(dir, "level.properties").isFile()) {
                LevelDefinition level = LevelDefinition.load(new File(dir, "level.properties"));
                if (level.getStoryFile().isFile())
                    kept.add(level);
            }
        assertFalse("no chapter has a story", kept.isEmpty());
        return kept.toArray(new LevelDefinition[0]);
    }

    /** A pose key must belong to a line that is read, and name a pose its speaker has. */
    public void testEveryPoseKeyNamesAPoseOfItsSpeaker() throws Exception {
        int keys = 0;
        for (LevelDefinition level : levels()) {
            Properties story = Investigation.story(level);
            Investigation investigation = Investigation.load(level);
            for (String key : story.stringPropertyNames()) {
                if (!key.endsWith(".pose"))
                    continue;
                keys++;
                Matcher m = POSE_KEY.matcher(key);
                assertTrue(level.getId() + ": " + key + " is not a line's pose", m.matches());
                List<Investigation.Line> lines = "brief".equals(m.group(1))
                        ? investigation.getBriefing() : investigation.getClue();
                int number = Integer.parseInt(m.group(2));
                assertTrue(level.getId() + ": " + key + " has no line", number >= 1 && number <= lines.size());
                Investigation.Line line = lines.get(number - 1);
                assertEquals(level.getId() + ": " + key, story.getProperty(key).trim(), line.getPose());
                assertTrue(level.getId() + ": " + key + "=" + line.getPose() + " is not a pose of " + line.getSpeaker(),
                        AcademyCharacters.namedPose(line.getSpeaker(), line.getPose()) >= 0);
            }
        }
        // the lamp chapters name a pose on every line of Fu and Luo: 10 in the first, 9 in the second,
        // 9 at the registry desk, 10 at the star tower
        assertEquals(38, keys);
    }

    /**
     * In the lamp chapters the letter is still sealed. The order-based fallback would show Fu reading
     * it on her second line, so every line of Fu and Luo there names its pose, and none of Fu's opens
     * the letter.
     */
    public void testLampChaptersNeverOpenTheLetter() throws Exception {
        int lamps = 0;
        int read = AcademyCharacters.namedPose("芙", "读信");
        assertTrue(read >= 0);
        for (LevelDefinition level : levels()) {
            if (!level.isLampScene())
                continue;
            lamps++;
            Investigation investigation = Investigation.load(level);
            for (List<Investigation.Line> lines : java.util.Arrays.asList(investigation.getBriefing(), investigation.getClue()))
                for (Investigation.Line line : lines) {
                    if (!"芙".equals(line.getSpeaker()) && !"洛".equals(line.getSpeaker()))
                        continue;
                    assertNotNull(level.getId() + ": no pose for " + line.getText(), line.getPose());
                    if ("芙".equals(line.getSpeaker()))
                        assertTrue(level.getId() + ": Fu reads the sealed letter at " + line.getText(),
                                AcademyCharacters.namedPose("芙", line.getPose()) != read);
                }
        }
        assertEquals("four lamp chapters", 4, lamps);
    }

    public void testUnknownNamesAreNotPoses() {
        assertEquals(-1, AcademyCharacters.namedPose("芙", "回避"));
        assertEquals(-1, AcademyCharacters.namedPose("微光", "持信"));
        assertEquals(-1, AcademyCharacters.namedPose("洛", null));
        assertEquals(7, AcademyCharacters.namedPose("洛", "讲解"));
    }
}
