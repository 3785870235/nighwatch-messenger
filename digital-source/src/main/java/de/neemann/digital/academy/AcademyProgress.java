/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * Local progress of a level, stored as a properties file below the work directory.
 * Only real observations and predictions made before observing are stored. On restore the
 * observations are replayed against the reference device; nothing unobserved is filled in.
 * The stored historical pass never marks the current circuit as passed.
 */
public final class AcademyProgress {
    /**
     * the version of the progress file format
     */
    public static final int VERSION = 1;

    private final File file;
    private final LevelDefinition level;
    private final String fingerprint;

    /**
     * Creates a progress store.
     *
     * @param dir   the directory holding the progress files
     * @param level the level
     * @throws IOException if the level resources could not be read
     */
    public AcademyProgress(File dir, LevelDefinition level) throws IOException {
        this.file = new File(dir, level.getId() + ".progress").getAbsoluteFile();
        this.level = level;
        this.fingerprint = fingerprint(level);
    }

    /**
     * A fingerprint of the reference and the trusted tests. If the rules change, old progress becomes invalid.
     *
     * @param level the level
     * @return the fingerprint
     * @throws IOException IOException
     */
    static String fingerprint(LevelDefinition level) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((level.getId() + "|" + level.getInputs() + "|" + level.getOutputs()).getBytes(StandardCharsets.UTF_8));
            md.update(Files.readAllBytes(level.getReferenceFile().toPath()));
            md.update(Files.readAllBytes(level.getTestsFile().toPath()));
            StringBuilder sb = new StringBuilder();
            byte[] d = md.digest();
            for (int i = 0; i < 8; i++)
                sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /**
     * @return the progress file
     */
    public File getFile() {
        return file;
    }

    /**
     * Writes the progress atomically: a temporary file is written and then moved over the old one.
     *
     * @param session    the experiment session
     * @param rebuild    true if the rebuild stage was reached
     * @param passedOnce true if a circuit passed the validation at some time
     * @throws IOException IOException
     */
    public void save(ExperimentSession session, boolean rebuild, boolean passedOnce) throws IOException {
        save(session, rebuild, passedOnce, null);
    }

    /**
     * Records the explicitly chosen circuit path together with progress.
     * @param session experiment records
     * @param rebuild repair stage
     * @param passedOnce historical successful validation
     * @param circuit player's explicit Save/Save As destination, or null for legacy data
     * @throws IOException save failed
     */
    public void save(ExperimentSession session, boolean rebuild, boolean passedOnce, File circuit) throws IOException {
        save(session, rebuild, passedOnce, circuit, null, false);
    }

    /**
     * Records progress together with the investigation state. The candidate states are not stored:
     * they follow from the real observations again on restore.
     * @param session experiment records
     * @param rebuild repair stage
     * @param passedOnce historical successful validation
     * @param circuit player's explicit Save/Save As destination, or null for legacy data
     * @param choice the id of the player's current explanation, or null
     * @param briefed true if the doubt dialogue was read
     * @throws IOException save failed
     */
    public void save(ExperimentSession session, boolean rebuild, boolean passedOnce, File circuit,
                     String choice, boolean briefed) throws IOException {
        Properties p = new Properties();
        if (circuit != null)
            p.setProperty("circuit", circuit.getCanonicalPath());
        if (choice != null)
            p.setProperty("story.choice", choice);
        if (briefed)
            p.setProperty("story.briefed", "true");
        p.setProperty("version", Integer.toString(VERSION));
        p.setProperty("level", level.getId());
        p.setProperty("fingerprint", fingerprint);
        for (int c = 0; c < session.getCombinationCount(); c++) {
            Reading r = session.getObserved(c);
            if (r != null)
                p.setProperty("obs." + c, Long.toString(r.getValue()));
            Long pr = session.getPrediction(c);
            if (pr != null)
                p.setProperty("pred." + c, Long.toString(pr));
        }
        p.setProperty("rebuild", Boolean.toString(rebuild));
        p.setProperty("passedOnce", Boolean.toString(passedOnce));

        Path dir = file.getParentFile().toPath();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, level.getId(), ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "Midnight Logic Academy progress");
            }
            try {
                Files.move(tmp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Restores the progress. Invalid, old or incompatible progress is moved aside and
     * a fresh session is returned together with a message.
     *
     * @param reference the reference device used to replay the observations
     * @return the restored state, never null
     */
    public Restored restore(Function<Map<String, Boolean>, Reading> reference) {
        if (!file.exists())
            return new Restored(new ExperimentSession(level.getInputs()), false, false, null);
        try {
            return replay(reference);
        } catch (IOException e) {
            String msg = "旧进度无法使用（" + e.getMessage() + "），已从新实验开始。";
            try {
                File bad = null;
                for (int i = 1; bad == null; i++) {
                    File f = new File(file.getPath() + ".bad-" + System.currentTimeMillis() + "-" + i);
                    try {
                        Files.move(file.toPath(), f.toPath());
                        bad = f;
                    } catch (java.nio.file.FileAlreadyExistsException ex) {
                        if (i > 100)
                            throw ex;
                    }
                }
                msg += "原文件已备份为 " + bad.getName() + "。";
            } catch (IOException e2) {
                msg += "备份原文件失败：" + e2.getMessage() + "；后续保存会覆盖该文件。";
            }
            return new Restored(new ExperimentSession(level.getInputs()), false, false, msg);
        }
    }

    /**
     * Reads the circuit chosen on a previous explicit save without writing anything.
     * @return an existing circuit for this rule fingerprint, otherwise null
     */
    public File savedCircuit() {
        if (!file.exists())
            return null;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            Properties data = new Properties();
            data.load(in);
            if (!Integer.toString(VERSION).equals(data.getProperty("version"))
                    || !level.getId().equals(data.getProperty("level"))
                    || !fingerprint.equals(data.getProperty("fingerprint")))
                return null;
            String path = data.getProperty("circuit");
            File circuit = path == null ? null : new File(path);
            return circuit != null && circuit.isFile() ? circuit : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private Restored replay(Function<Map<String, Boolean>, Reading> reference) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            p.load(in);
        } catch (IllegalArgumentException e) {
            throw new IOException("文件损坏");
        }
        if (!Integer.toString(VERSION).equals(p.getProperty("version")))
            throw new IOException("版本不兼容：" + p.getProperty("version"));
        if (!level.getId().equals(p.getProperty("level")))
            throw new IOException("关卡不一致");
        if (!fingerprint.equals(p.getProperty("fingerprint")))
            throw new IOException("关卡规则已改变");

        ExperimentSession s = new ExperimentSession(level.getInputs());
        int n = s.getCombinationCount();
        Long[] obs = new Long[n];
        Long[] pred = new Long[n];
        for (int c = 0; c < n; c++) {
            obs[c] = value(p.getProperty("obs." + c));
            pred[c] = value(p.getProperty("pred." + c));
            if (pred[c] != null && obs[c] == null)
                throw new IOException("预测缺少观察");
        }
        // predictions first, so that the forced prediction rule is respected on replay
        for (int pass = 0; pass < 2; pass++)
            for (int c = 0; c < n; c++) {
                if (obs[c] == null || (pass == 0) != (pred[c] != null))
                    continue;
                Reading r = reference.apply(s.toInputs(c));
                if (!r.isValid() || r.getValue() != obs[c])
                    throw new IOException("记录与旧门不符");
                if (pass == 0)
                    s.predictAndObserve(c, (long) pred[c], r);
                else if (!s.observe(c, r))
                    throw new IOException("缺少必须的预测");
            }
        boolean rebuild = Boolean.parseBoolean(p.getProperty("rebuild"));
        if (rebuild && !s.isComplete())
            throw new IOException("实验未完成却记录了重建");
        Restored restored = new Restored(s, rebuild, Boolean.parseBoolean(p.getProperty("passedOnce")), null);
        // optional since Story1; progress written before has neither key
        restored.choice = p.getProperty("story.choice");
        restored.briefed = Boolean.parseBoolean(p.getProperty("story.briefed"));
        return restored;
    }

    private Long value(String v) throws IOException {
        if (v == null)
            return null;
        long range = 1L << level.getOutputs().size();
        String t = v.trim();
        for (int i = 0; i < t.length(); i++)
            if (t.charAt(i) < '0' || t.charAt(i) > '9')
                throw new IOException("非法值 " + v);
        if (t.isEmpty() || t.length() > 2 || Long.parseLong(t) >= range)
            throw new IOException("非法值 " + v);
        return Long.parseLong(t);
    }

    /**
     * The restored state of a level.
     */
    public static final class Restored {
        private final ExperimentSession session;
        private final boolean rebuild;
        private final boolean passedOnce;
        private final String message;
        private String choice;
        private boolean briefed;

        private Restored(ExperimentSession session, boolean rebuild, boolean passedOnce, String message) {
            this.session = session;
            this.rebuild = rebuild;
            this.passedOnce = passedOnce;
            this.message = message;
        }

        /**
         * @return the session containing only real records
         */
        public ExperimentSession getSession() {
            return session;
        }

        /**
         * @return true if the rebuild stage was reached
         */
        public boolean isRebuild() {
            return rebuild;
        }

        /**
         * @return true if some circuit passed in the past; says nothing about the current circuit
         */
        public boolean isPassedOnce() {
            return passedOnce;
        }

        /**
         * @return a message for the player if the old progress could not be used, otherwise null
         */
        public String getMessage() {
            return message;
        }

        /**
         * @return the stored explanation id, unchecked; null for older progress
         */
        public String getChoice() {
            return choice;
        }

        /**
         * @return true if the doubt dialogue was read when the progress was saved
         */
        public boolean isBriefed() {
            return briefed;
        }
    }
}
