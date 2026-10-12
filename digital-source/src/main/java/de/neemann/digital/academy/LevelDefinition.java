/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * A level loaded from a level.properties file.
 * All file references are resolved relative to the properties file.
 */
public final class LevelDefinition {
    private final File dir;
    private final String id;
    private final String title;
    private final String story;
    private final List<String> inputs;
    private final List<String> outputs;
    private final Properties props;
    private final Set<String> allowed;
    private final List<String> hints;
    private final String scene;

    private LevelDefinition(File propertiesFile, Properties props) throws IOException {
        this.dir = propertiesFile.getAbsoluteFile().getParentFile();
        this.props = props;
        id = required("id");
        title = required("title");
        story = props.getProperty("story", "");
        inputs = list(required("inputs"));
        outputs = list(required("outputs"));
        allowed = Collections.unmodifiableSet(new LinkedHashSet<>(list(required("allowed"))));
        ArrayList<String> h = new ArrayList<>();
        for (int i = 1; props.getProperty("hint." + i) != null; i++)
            h.add(props.getProperty("hint." + i));
        hints = Collections.unmodifiableList(h);
        scene = props.getProperty("scene", "archive_door");
    }

    /**
     * Loads a level definition. The file is read as UTF-8.
     *
     * @param propertiesFile the level.properties file
     * @return the level
     * @throws IOException IOException
     */
    public static LevelDefinition load(File propertiesFile) throws IOException {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(propertiesFile.toPath()), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        if (!"1".equals(p.getProperty("schemaVersion")))
            throw new IOException("unsupported schemaVersion in " + propertiesFile);
        return new LevelDefinition(propertiesFile, p);
    }

    private String required(String key) throws IOException {
        String v = props.getProperty(key);
        if (v == null || v.trim().isEmpty())
            throw new IOException("missing level property: " + key);
        return v.trim();
    }

    private static List<String> list(String csv) {
        ArrayList<String> l = new ArrayList<>();
        for (String s : Arrays.asList(csv.split(",")))
            if (!s.trim().isEmpty())
                l.add(s.trim());
        return Collections.unmodifiableList(l);
    }

    private File file(String key) {
        return new File(dir, props.getProperty(key));
    }

    /**
     * @return the stable level id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the level title
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return the story text
     */
    public String getStory() {
        return story;
    }

    /**
     * @return the input role ids, equal to the Digital labels
     */
    public List<String> getInputs() {
        return inputs;
    }

    /**
     * @return the output role ids, equal to the Digital labels
     */
    public List<String> getOutputs() {
        return outputs;
    }

    /**
     * Returns the semantic label of a port, e.g. "岗位 A 持钥印".
     *
     * @param port the port id
     * @return the semantic label or the id if none is defined
     */
    public String getPortLabel(String port) {
        String v = props.getProperty("input." + port + ".label");
        if (v == null)
            v = props.getProperty("output." + port + ".label");
        return v == null ? port : v;
    }

    /**
     * @return the Digital element names the player may use
     */
    public Set<String> getAllowed() {
        return allowed;
    }

    /**
     * @return the hints, from general to specific
     */
    public List<String> getHints() {
        return hints;
    }

    /**
     * @return the hidden reference circuit
     */
    public File getReferenceFile() {
        return file("reference");
    }

    /**
     * @return the template given to the player
     */
    public File getTemplateFile() {
        return file("template");
    }

    /**
     * @return the circuit containing the trusted test cases
     */
    public File getTestsFile() {
        return file("tests");
    }

    /**
     * @return the optional investigation story beside the level; it may not exist
     */
    public File getStoryFile() {
        return new File(dir, props.getProperty("storyFile", "story.properties"));
    }

    /**
     * Returns whether this level uses a lamp scene rather than a door scene.
     * Driven by the {@code scene} key: {@code report_light}, {@code reminder_light}, the
     * acceptance lamp of the {@code registry_desk} and the star lamps of the {@code star_tower} are
     * lamps; everything else (including the default {@code archive_door}) is a door.
     *
     * @return true if the scene should render a lamp
     */
    public boolean isLampScene() {
        return "report_light".equals(scene) || "reminder_light".equals(scene) || "registry_desk".equals(scene)
                || "star_tower".equals(scene);
    }

    /**
     * @param fallback the plate used if the level names none
     * @return the brass plate under the lamp on the trial bench, e.g. "登记台 · 受理灯"
     */
    public String getScenePlate(String fallback) {
        String v = props.getProperty("scene.plate");
        return v == null || v.trim().isEmpty() ? fallback : v.trim();
    }

    /**
     * @return the scene key, e.g. {@code archive_door} or {@code report_light}
     */
    public String getScene() {
        return scene;
    }

    /**
     * @return the Digital element name of the rune the player places in the repair stage,
     * empty if the stations are wired directly to the output; the first one if there are several
     */
    public String getRepairRune() {
        List<String> runes = getRepairRunes();
        return runes.isEmpty() ? "" : runes.get(0);
    }

    /**
     * @return the Digital element names of the runes, one per output in output order; empty if the
     * stations are wired directly to the output
     */
    public List<String> getRepairRunes() {
        return list(props.getProperty("repair.rune", "And"));
    }

    /**
     * @return the front names of the repair runes, one per rune, e.g. "双印符文", "单席符文"
     */
    public List<String> getRepairRuneNames() {
        return list(props.getProperty("repair.runeName", "双印符文"));
    }

    /**
     * @return the front name of the repair rune, e.g. "双印符文"; several runes are named together
     */
    public String getRepairRuneName() {
        return String.join("和", getRepairRuneNames());
    }

    /**
     * @return the proven rule repeated in the repair stage; it names the rune but no circuit term
     */
    public String getRepairRule() {
        return props.getProperty("repair.rule", "只有两份报告都成立时，门才开启。双印符文要求所有报告同时成立。").trim();
    }

    /**
     * @return what to look for while the player's running spell is tried
     */
    public String getRepairCheck() {
        return props.getProperty("repair.check", "去门前看它是否只在两份报告都成立时打开").trim();
    }
}
