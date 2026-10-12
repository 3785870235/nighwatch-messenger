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
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * The investigation of a level: the characters bring candidate explanations of the door rule and
 * the player judges them with real observations. Every candidate predicts through its own Digital
 * model. A candidate is refuted only by a combination that was actually observed and whose valid
 * prediction differs from the observed reading; unobserved combinations never count and an invalid
 * prediction neither supports nor refutes. Nothing here decides the result of an experiment.
 */
public final class Investigation {
    private final List<Candidate> candidates;
    private final List<Line> briefing;
    private final List<Line> clue;
    private final List<String> stations;
    private final Properties words;
    private Candidate choice;

    Investigation(List<Candidate> candidates, List<Line> briefing, List<Line> clue) {
        this(candidates, briefing, clue, Collections.emptyList());
    }

    Investigation(List<Candidate> candidates, List<Line> briefing, List<Line> clue, List<String> stations) {
        this(candidates, briefing, clue, stations, new Properties());
    }

    Investigation(List<Candidate> candidates, List<Line> briefing, List<Line> clue, List<String> stations,
                  Properties story) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.briefing = Collections.unmodifiableList(new ArrayList<>(briefing));
        this.clue = Collections.unmodifiableList(new ArrayList<>(clue));
        this.stations = Collections.unmodifiableList(new ArrayList<>(stations));
        this.words = new Properties();
        for (String key : story.stringPropertyNames())
            if (key.startsWith("word."))
                words.setProperty(key.substring(5), story.getProperty(key).trim());
    }

    /**
     * Loads the story beside the level and lets Digital simulate every candidate model once.
     *
     * @param level the level
     * @return the investigation or null if the level has no story file
     * @throws IOException if the story or a candidate model is broken
     */
    public static Investigation load(LevelDefinition level) throws IOException {
        File file = level.getStoryFile();
        if (!file.isFile())
            return null;
        Properties p = story(level);
        ExperimentSession combos = new ExperimentSession(level.getInputs());
        ArrayList<Candidate> list = new ArrayList<>();
        for (String entry : p.getProperty("candidates", "").split(",")) {
            String id = entry.trim();
            if (id.isEmpty())
                continue;
            String model = required(p, "candidate." + id + ".model");
            Reading[] predictions = new Reading[combos.getCombinationCount()];
            try (ReferenceDevice device = new ReferenceDevice(level, new File(file.getParentFile(), model))) {
                for (int c = 0; c < predictions.length; c++)
                    predictions[c] = device.evaluate(combos.toInputs(c));
            }
            list.add(new Candidate(id, required(p, "candidate." + id + ".name"), required(p, "candidate." + id + ".rule"),
                    p.getProperty("candidate." + id + ".knowledge"), predictions,
                    p.getProperty("candidate." + id + ".origin"), p.getProperty("candidate." + id + ".doubt")));
        }
        if (list.size() < 2)
            throw new IOException("an investigation needs at least two candidates: " + file);
        return new Investigation(list, lines(p, "brief"), lines(p, "clue"), ExperimentGuide.stations(level), p);
    }

    /**
     * Reads the story file beside the level.
     *
     * @param level the level
     * @return the story properties; empty if the level has no story file
     * @throws IOException if the story file can not be read or has an unknown schema
     */
    static Properties story(LevelDefinition level) throws IOException {
        Properties p = new Properties();
        File file = level.getStoryFile();
        if (!file.isFile())
            return p;
        try (Reader r = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        if (!"1".equals(p.getProperty("schemaVersion")))
            throw new IOException("unsupported schemaVersion in " + file);
        return p;
    }

    private static String required(Properties p, String key) throws IOException {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty())
            throw new IOException("missing story property: " + key);
        return v.trim();
    }

    private static List<Line> lines(Properties p, String prefix) throws IOException {
        ArrayList<Line> l = new ArrayList<>();
        for (int i = 1; p.getProperty(prefix + "." + i) != null; i++) {
            String v = p.getProperty(prefix + "." + i);
            int bar = v.indexOf('|');
            if (bar <= 0)
                throw new IOException("story line without speaker: " + prefix + "." + i);
            String speaker = v.substring(0, bar).trim();
            // a line may name the pose its speaker takes; an unknown name is a typo, not a default
            String pose = p.getProperty(prefix + "." + i + ".pose");
            if (pose != null) {
                pose = pose.trim();
                if (AcademyCharacters.namedPose(speaker, pose) < 0)
                    throw new IOException("unknown pose for " + speaker + ": " + prefix + "." + i + ".pose=" + pose);
            }
            l.add(new Line(speaker, v.substring(bar + 1).trim(), pose));
        }
        return l;
    }

    /**
     * @return the candidates in story order
     */
    public List<Candidate> getCandidates() {
        return candidates;
    }

    /**
     * @return the dialogue presenting the doubt
     */
    public List<Line> getBriefing() {
        return briefing;
    }

    /**
     * @return the dialogue of the clue found after a real successful validation
     */
    public List<Line> getClue() {
        return clue;
    }

    /**
     * Returns a word of the story vocabulary, the {@code word.*} keys of the story file.
     *
     * @param key the key without the {@code word.} prefix
     * @param def the word of the archive door story
     * @return the word
     */
    public String word(String key, String def) {
        String v = words.getProperty(key);
        return v == null || v.isEmpty() ? def : v;
    }

    /**
     * @return the label for the button that opens the candidate pages panel
     */
    public String getCandidateButtonLabel() {
        return word("casesButton", "三张残页");
    }

    /**
     * @return the label for the button that opens the clue dialogue
     */
    public String getClueButtonLabel() {
        return word("clueButton", "翻开校准页 →");
    }

    /**
     * @param id the candidate id
     * @return the candidate or null
     */
    public Candidate find(String id) {
        for (Candidate c : candidates)
            if (c.id.equals(id))
                return c;
        return null;
    }

    /**
     * Returns the first observed combination contradicting the candidate.
     *
     * @param candidate the candidate
     * @param session   the real records
     * @return the combination or -1 if no observation contradicts it
     */
    public int counterexample(Candidate candidate, ExperimentSession session) {
        for (int c = 0; c < session.getCombinationCount(); c++)
            if (contradicts(candidate, session, c))
                return c;
        return -1;
    }

    private static boolean contradicts(Candidate candidate, ExperimentSession session, int c) {
        Reading observed = session.getObserved(c);
        Reading predicted = candidate.predictions[c];
        return observed != null && observed.isValid() && predicted.isValid() && observed.getValue() != predicted.getValue();
    }

    /**
     * @param candidate the candidate
     * @param session   the real records
     * @return the number of observations agreeing with a valid prediction of the candidate
     */
    public int support(Candidate candidate, ExperimentSession session) {
        int n = 0;
        for (int c = 0; c < session.getCombinationCount(); c++) {
            Reading observed = session.getObserved(c);
            Reading predicted = candidate.predictions[c];
            if (observed != null && predicted.same(observed))
                n++;
        }
        return n;
    }

    /**
     * @param session the real records
     * @return the candidates no observation contradicts
     */
    public List<Candidate> remaining(ExperimentSession session) {
        ArrayList<Candidate> l = new ArrayList<>();
        for (Candidate c : candidates)
            if (counterexample(c, session) < 0)
                l.add(c);
        return l;
    }

    /**
     * @param combination the combination
     * @param session     the real records
     * @return the candidates whose valid prediction differs from the observation of this combination;
     * empty if the combination was not observed
     */
    public List<Candidate> refutedAt(int combination, ExperimentSession session) {
        ArrayList<Candidate> l = new ArrayList<>();
        for (Candidate c : candidates)
            if (contradicts(c, session, combination))
                l.add(c);
        return l;
    }

    /**
     * Returns true if the remaining candidates disagree on the given combination, so that observing
     * it can refute at least one of them.
     *
     * @param combination the combination
     * @param session     the real records
     * @return true if the remaining valid predictions differ
     */
    public boolean separates(int combination, ExperimentSession session) {
        java.util.Set<Long> values = new java.util.HashSet<>();
        for (Candidate c : remaining(session)) {
            Reading r = c.predictions[combination];
            if (r.isValid())
                values.add(r.getValue());
        }
        return values.size() > 1;
    }

    /**
     * The evidence is sufficient if every combination was actually observed, a prediction was made
     * before an observation and exactly one candidate survived.
     *
     * @param session the real records
     * @return true if the rule may be named
     */
    public boolean isSettled(ExperimentSession session) {
        return session.isComplete() && remaining(session).size() == 1;
    }

    /**
     * @param session the real records
     * @return the only surviving candidate if the evidence is sufficient, otherwise null
     */
    public Candidate discovered(ExperimentSession session) {
        return isSettled(session) ? remaining(session).get(0) : null;
    }

    /**
     * @return the player's current explanation or null
     */
    public Candidate getChoice() {
        return choice;
    }

    /**
     * Sets the player's explanation. Any candidate may be chosen, also a refuted one; the evidence
     * feedback tells the truth about it.
     *
     * @param id the candidate id, null to clear; an unknown id clears the choice
     * @return true if the choice changed
     */
    public boolean choose(String id) {
        Candidate c = id == null ? null : find(id);
        if (c == choice)
            return false;
        choice = c;
        return true;
    }

    /**
     * @param session the real records
     * @return true if the evidence is sufficient and the player chose the surviving candidate
     */
    public boolean canRepair(ExperimentSession session) {
        Candidate d = discovered(session);
        return d != null && d == choice;
    }

    /**
     * @param candidate a refuted candidate
     * @param session   the real records
     * @return e.g. "岗位 A 持钥印 · 岗位 B 未持钥印时，门没有开，它却说门会开", or null if not refuted
     */
    public String counterText(Candidate candidate, ExperimentSession session) {
        int c = counterexample(candidate, session);
        if (c < 0)
            return null;
        String device = device();
        return ExperimentGuide.describe(stations, session, c) + "时，" + device
                + did(session.getObserved(c).getValue()) + "，它却说" + device
                + claim(candidate.predictions[c].getValue());
    }

    private String device() {
        return word("device", "门");
    }

    private String place() {
        return word("place", "门前");
    }

    /**
     * @param value an output value, packed with the first output as the highest bit
     * @return what a page claims the device does, e.g. "会开"; several outputs use word.claim.N
     */
    String claim(long value) {
        String v = word("claim." + value, null);
        if (v != null)
            return v;
        return value != 0 ? word("claimLit", "会开") : word("claimDark", "不开");
    }

    String did(long value) {
        String v = word("did." + value, null);
        if (v != null)
            return v;
        return value != 0 ? word("didLit", "开了") : word("didDark", "没有开");
    }

    String seen(long value) {
        String v = word("seen." + value, null);
        if (v != null)
            return v;
        return value != 0 ? word("seenLit", "门打开了") : word("seenDark", "门保持关闭");
    }

    /**
     * @param n a number of combinations
     * @return e.g. "四组"
     */
    static String groups(int n) {
        String[] words = {"零", "一", "两", "三", "四", "五", "六", "七", "八"};
        return (n >= 0 && n < words.length ? words[n] : Integer.toString(n)) + "组";
    }

    /**
     * @param candidate the candidate
     * @param session   the real records
     * @return the seal the evidence puts on the page
     */
    public Standing standing(Candidate candidate, ExperimentSession session) {
        if (counterexample(candidate, session) >= 0)
            return Standing.REFUTED;
        if (discovered(session) == candidate)
            return Standing.PROVEN;
        return support(candidate, session) == 0 ? Standing.UNTESTED : Standing.STANDING;
    }

    /**
     * @param candidate the candidate
     * @param session   the real records
     * @return the short evidence state of a candidate card
     */
    public String status(Candidate candidate, ExperimentSession session) {
        String counter = counterText(candidate, session);
        if (counter != null)
            return "已被证据推翻\n反例 " + counter;
        int n = support(candidate, session);
        if (session.getObservedCount() == 0)
            return "尚无证据：还没有任何观察";
        return "未被推翻 · 与 " + n + " 组真实记录一致"
                + (n < session.getObservedCount() ? "（其余组它无法预测）" : "");
    }

    /**
     * @param session the real records
     * @return the sprite's judgement of the whole case; it counts pages but never names one
     */
    public String verdict(ExperimentSession session) {
        List<Candidate> left = remaining(session);
        int unseen = session.getCombinationCount() - session.getObservedCount();
        String all = groups(session.getCombinationCount());
        if (session.getObservedCount() == 0)
            return place() + "还没有任何记录，现在哪张残页都推不翻。去" + place() + "找证据吧。";
        if (left.isEmpty())
            return "所有残页都被证据推翻了：旧" + device() + "遵守的是残页之外的规则。";
        int refuted = candidates.size() - left.size();
        if (left.size() > 1)
            return (refuted == 0 ? "还没有残页被推翻。" : "已划掉 " + refuted + " 张。")
                    + "仍有 " + left.size() + " 张与全部记录一致——去" + place() + "找一组它们说法不同的报告。";
        if (unseen > 0)
            return "只剩一张残页没被推翻。但还有 " + unseen
                    + " 组报告没亲眼看过，残页之外的规则也可能成立；补齐记录再下结论。";
        if (!session.isComplete())
            return all + "都看过了，只剩一张残页。按规矩还差一次「先猜再看」的预测，才能定论。";
        return all + "报告都已亲眼记录，只有一张残页经得起全部证据。";
    }

    /**
     * @param session the real records
     * @return the evidence feedback for the player's choice
     */
    public String choiceFeedback(ExperimentSession session) {
        if (choice == null)
            return "还没选择解释。选一张你认为最可能的残页——之后随时可以改。";
        String counter = counterText(choice, session);
        if (counter != null)
            return "「" + choice.name + "」已被你的记录反驳：" + counter + "。";
        if (session.getObservedCount() == 0)
            return "你选了「" + choice.name + "」。" + place() + "还没有一组记录，它现在既没被证实，也没被推翻。";
        List<Candidate> left = remaining(session);
        if (left.size() > 1)
            return "目前证据仍支持「" + choice.name + "」，但还有 " + (left.size() - 1)
                    + " 张残页也没被推翻。还需要能区分它们的观察。";
        if (!isSettled(session))
            return "到目前为止证据只支持「" + choice.name + "」，但记录还不完整，暂不定论。";
        return "证据支持你的判断：「" + choice.name + "」经得起全部" + groups(session.getCombinationCount()) + "真实记录。";
    }

    /**
     * What every page claims about the selected combination. It never says which page an observation
     * refuted: the player compares the door with the claims, and the evidence is only read out on the
     * page the player chose.
     *
     * @param combination the selected combination
     * @param session     the real records
     * @return the claims and, before an observation, whether they differ
     */
    public String observationNote(int combination, ExperimentSession session) {
        StringBuilder claims = new StringBuilder();
        java.util.Set<Long> values = new java.util.HashSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            Reading r = candidates.get(i).predictions[combination];
            claims.append(i > 0 ? " · " : "").append(shortName(candidates.get(i)))
                    .append(r.isValid() ? " " + claim(r.getValue()) : " 说不清");
            if (r.isValid())
                values.add(r.getValue());
        }
        Reading seen = session.getObserved(combination);
        if (seen != null)
            return "这组已亲眼记录：" + (!seen.isValid() ? device() + "没有给出可读的回应" : seen(seen.getValue()))
                    + "。残页的说法：" + claims + "。";
        return "残页对这组的说法：" + claims + (values.size() > 1 ? "——说法不同，这组能区分它们。" : "——说法一致，这组分不出它们。");
    }

    /**
     * @param list candidates
     * @return the names, e.g. "「残页甲 · 任一印」「残页丙 · 单席印」"
     */
    static String names(List<Candidate> list) {
        StringBuilder sb = new StringBuilder();
        for (Candidate c : list)
            sb.append("「").append(c.name).append("」");
        return sb.toString();
    }

    private static String shortName(Candidate c) {
        int dot = c.name.indexOf('·');
        return dot > 0 ? c.name.substring(0, dot).trim() : c.name;
    }

    /**
     * The seal the evidence puts on a page. It is only shown on the page the player chose.
     */
    public enum Standing {
        /**
         * no observation speaks about it yet
         */
        UNTESTED,
        /**
         * an observation contradicts it
         */
        REFUTED,
        /**
         * not refuted, but the evidence does not single it out yet
         */
        STANDING,
        /**
         * the only page surviving the complete records
         */
        PROVEN
    }

    /**
     * A candidate explanation from the torn records.
     */
    public static final class Candidate {
        private final String id;
        private final String name;
        private final String rule;
        private final String knowledge;
        private final String origin;
        private final String doubt;
        private final Reading[] predictions;

        Candidate(String id, String name, String rule, String knowledge, Reading[] predictions) {
            this(id, name, rule, knowledge, predictions, null, null);
        }

        Candidate(String id, String name, String rule, String knowledge, Reading[] predictions, String origin, String doubt) {
            this.id = id;
            this.name = name;
            this.rule = rule;
            this.knowledge = knowledge;
            this.origin = origin == null ? null : origin.trim();
            this.doubt = doubt == null ? null : doubt.trim();
            this.predictions = predictions.clone();
        }

        /**
         * @return where the page was found, maybe null
         */
        public String getOrigin() {
            return origin;
        }

        /**
         * @return why the page might be wrong, maybe null
         */
        public String getDoubt() {
            return doubt;
        }

        /**
         * @return the stable id, stored in the progress
         */
        public String getId() {
            return id;
        }

        /**
         * @return the shown name with its rune
         */
        public String getName() {
            return name;
        }

        /**
         * @return the rule in natural language
         */
        public String getRule() {
            return rule;
        }

        /**
         * @return the rune knowledge, shown only after the evidence is sufficient; maybe null
         */
        public String getKnowledge() {
            return knowledge;
        }

        /**
         * @param combination the combination
         * @return the reading the candidate's Digital model gave for it
         */
        public Reading predict(int combination) {
            return predictions[combination];
        }
    }

    /**
     * One page of dialogue: a speaker, one or two sentences, and optionally the pose the speaker takes.
     */
    public static final class Line {
        private final String speaker;
        private final String text;
        private final String pose;

        Line(String speaker, String text, String pose) {
            this.speaker = speaker;
            this.text = text;
            this.pose = pose;
        }

        /**
         * @return the speaker
         */
        public String getSpeaker() {
            return speaker;
        }

        /**
         * @return the words
         */
        public String getText() {
            return text;
        }

        /**
         * @return the named pose, or null when the line leaves it to the stage
         */
        public String getPose() {
            return pose;
        }
    }
}
