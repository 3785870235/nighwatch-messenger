/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The academy's reply to a spell sent for checking, in the words of the world: the copy that was carved
 * from the spell, how it answered every situation at the door, whether that agrees with the door rule
 * and what to do next. It only words a {@link ValidationResult} and decides nothing. A missing message
 * is written as such and never as a closed door.
 */
final class ResultLetter {
    /** Where the reply stands. */
    enum State {
        /** nothing was sent yet */
        IDLE,
        /** the copy is being tested */
        PENDING,
        /** every check passed */
        PASSED,
        /** sent back */
        FAILED,
        /** the spell changed after the reply */
        STALE,
        /** the spell could not be copied */
        ERROR
    }

    /** What is pressed on the letter. */
    enum Mark {
        /** nothing */
        NONE,
        /** the academy's wax seal */
        SEAL,
        /** the red stamp "sent back" */
        RETURNED
    }

    /** The heading and the signature of every letter. */
    static final String SENDER = "午夜光术学院 · 检验处";
    /** How the academy addresses the player. */
    static final String SALUTATION = "值夜学徒：";
    /** The words of the red stamp. */
    static final String RETURNED_STAMP = "退回重刻";

    private static final String[] NUMBERS = {"零", "一", "两", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static final String[] ORDINALS = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static final Pattern NOT_USED = Pattern.compile("本关不使用元件 (.+?)（可用：.*）");
    private static final Pattern EXTRA = Pattern.compile("多余的(输入|输出)端口“(.*)”");
    private static final Pattern WIDTH = Pattern.compile("端口 (\\S+) 应为 1 位，当前为 (\\d+) 位");
    private static final Pattern MISSING = Pattern.compile("缺少(输入|输出)端口 (\\S+)（.*）");
    private static final Pattern TWICE = Pattern.compile("(输入|输出)端口 (\\S+) 出现了 (\\d+) 次");

    private final State state;
    private final String subject;
    private final String status;
    private final List<String> paragraphs;
    private final List<Row> rows;
    private final Mark mark;
    private final String rule;

    private ResultLetter(State state, String subject, String status, List<String> paragraphs, List<Row> rows, Mark mark,
                         String rule) {
        this.state = state;
        this.subject = subject;
        this.status = status;
        this.paragraphs = Collections.unmodifiableList(new ArrayList<>(paragraphs));
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.mark = mark;
        this.rule = rule;
    }

    /**
     * @return how this level calls its rule, e.g. 门规 or 灯规
     */
    String getRuleName() {
        return rule;
    }

    State getState() {
        return state;
    }

    /**
     * @return what was sent, e.g. 档案门的新术式
     */
    String getSubject() {
        return subject;
    }

    /**
     * @return a few words on where the reply stands, e.g. 检验通过
     */
    String getStatus() {
        return status;
    }

    /**
     * @return the paragraphs of the letter between the salutation and the signature
     */
    List<String> getParagraphs() {
        return paragraphs;
    }

    /**
     * @return the rows of the enclosed test slip; empty if no test was carried out
     */
    List<Row> getRows() {
        return rows;
    }

    Mark getMark() {
        return mark;
    }

    /**
     * @return the whole letter as plain text, status first
     */
    String text() {
        StringBuilder sb = new StringBuilder(status).append('\n').append("事由：").append(subject).append('\n')
                .append(SALUTATION).append('\n');
        for (String p : paragraphs)
            sb.append(p).append('\n');
        if (!rows.isEmpty()) {
            sb.append("试讯单：\n");
            for (Row r : rows)
                sb.append(r.text()).append('\n');
        }
        sb.append(SENDER);
        if (mark == Mark.SEAL)
            sb.append("\n〔火漆封印〕");
        else if (mark == Mark.RETURNED)
            sb.append("\n〔").append(RETURNED_STAMP).append("〕");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ResultLetter && ((ResultLetter) o).state == state && ((ResultLetter) o).text().equals(text());
    }

    @Override
    public int hashCode() {
        return text().hashCode();
    }

    /**
     * @param n a count
     * @return the count in words as used before a measure word, e.g. 两 for 2
     */
    static String count(int n) {
        return n >= 0 && n < NUMBERS.length ? NUMBERS[n] : Integer.toString(n);
    }

    /**
     * @param n a position
     * @return the position in words as used for numbering, e.g. 二 for 2
     */
    static String ordinal(int n) {
        return n >= 0 && n < ORDINALS.length ? ORDINALS[n] : Integer.toString(n);
    }

    /**
     * @param rows the rows of a slip
     * @return how many situations the rows cover
     */
    static int situations(List<Row> rows) {
        return rows.isEmpty() ? 0 : rows.get(rows.size() - 1).getSituation();
    }

    /**
     * @param rows   the rows of a slip
     * @param silent true to count the situations with a row that received nothing, false to count
     *               the situations with a row that failed
     * @return how many situations have such a row
     */
    static int situations(List<Row> rows, boolean silent) {
        java.util.Set<Integer> found = new java.util.HashSet<>();
        for (Row row : rows)
            if (silent ? row.isSilent() : !row.isPassed())
                found.add(row.getSituation());
        return found.size();
    }

    /**
     * @return true if this is an answer to a spell that was sent, not a note while waiting
     */
    boolean isReply() {
        return state == State.PASSED || state == State.FAILED || state == State.ERROR;
    }

    /**
     * One answer on the test slip: a situation at the door, or with several lamps one lamp of a
     * situation. The rows of one situation follow each other and share its number.
     */
    static final class Row {
        private final List<String> stations;
        private final List<String> cells;
        // set by the clerk right after the row is written, never changed afterwards
        private String lamp = "";
        private int situation;
        private final String expected;
        private final String actual;
        private final boolean passed;
        private final boolean silent;
        private final String rule;

        private Row(List<String> stations, List<String> cells, String expected, String actual, boolean passed,
                    boolean silent, String rule) {
            this.stations = Collections.unmodifiableList(stations);
            this.cells = Collections.unmodifiableList(cells);
            this.expected = expected;
            this.actual = actual;
            this.passed = passed;
            this.silent = silent;
            this.rule = rule;
        }

        /**
         * @return the lamp this row is about, e.g. 高星灯; empty if the level has a single lamp or door
         */
        String getLamp() {
            return lamp;
        }

        /**
         * @return the number of the situation, starting with 1
         */
        int getSituation() {
            return situation;
        }

        /**
         * @return how this level calls its rule, e.g. 门规 or 灯规
         */
        String getRuleName() {
            return rule;
        }

        /**
         * @return the names of the stations, e.g. 岗位 A
         */
        List<String> getStations() {
            return stations;
        }

        /**
         * @return what every station reports, e.g. 持钥印
         */
        List<String> getCells() {
            return cells;
        }

        /**
         * @return what the door rule asks for
         */
        String getExpected() {
            return expected;
        }

        /**
         * @return what the copy answered; 无讯 if it answered nothing
         */
        String getActual() {
            return actual;
        }

        boolean isPassed() {
            return passed;
        }

        boolean isSilent() {
            return silent;
        }

        /**
         * @return e.g. 岗位 A 未持钥印 · 岗位 B 持钥印 → 门规：门不开 · 副本：门开 ✗, with several lamps
         * the lamp before both answers: 塔规：高星灯 亮 · 副本：高星灯 亮 ✓
         */
        String text() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stations.size(); i++) {
                if (i > 0)
                    sb.append(" · ");
                sb.append(stations.get(i)).append(' ').append(cells.get(i));
            }
            String prefix = lamp.isEmpty() ? "" : lamp + " ";
            return sb.append(" → ").append(rule).append("：").append(prefix).append(expected).append(" · 副本：")
                    .append(prefix).append(actual).append(passed ? " ✓" : " ✗").toString();
        }
    }

    /**
     * Writes the letters of one level. The words for the stations and the door come from the story file
     * of the level (keys letter.*), so every chapter can speak about its own door.
     */
    static final class Clerk {
        private final LevelDefinition level;
        private final Properties words;

        /**
         * @param level the level
         * @param words the story properties of the level
         */
        Clerk(LevelDefinition level, Properties words) {
            this.level = level;
            this.words = words;
        }

        /**
         * @param level the level
         * @return a clerk using the story file of the level, or plain words if it can not be read
         */
        static Clerk of(LevelDefinition level) {
            Properties p;
            try {
                p = Investigation.story(level);
            } catch (IOException e) {
                p = new Properties();
            }
            return new Clerk(level, p);
        }

        private String word(String key, String fallback) {
            String w = words.getProperty(key);
            return w == null || w.trim().isEmpty() ? fallback : w.trim();
        }

        private String subject() {
            return word("letter.subject", level.getTitle());
        }

        private String cases() {
            return count(1 << level.getInputs().size()) + "种";
        }

        private String rule() {
            return word("word.rule", ExperimentGuide.ARCHIVE_RULE);
        }

        private String place() {
            return word("word.place", ExperimentGuide.ARCHIVE_PLACE);
        }

        private String device() {
            return word("word.device", "档案门");
        }

        /** The word of a dark reading without the device: "门不开" gives "不开". */
        private String claim(boolean lit) {
            String w = lit ? word("letter.output.1", "门开") : word("letter.output.0", "门不开");
            return w.startsWith(device()) ? w.substring(device().length()) : w;
        }

        private ResultLetter letter(State state, String status, List<String> paragraphs) {
            return new ResultLetter(state, subject(), status, paragraphs, Collections.emptyList(), Mark.NONE, rule());
        }

        /**
         * @return the letter shown before anything was sent
         */
        ResultLetter idle() {
            return letter(State.IDLE, "尚未送检", Collections.singletonList(
                    "连好术式、在" + place() + "试过之后，点「检验我的术式」。学院会照它另刻一份副本，把"
                            + cases() + "岗位情形逐一送进去试讯，再给你回信。"));
        }

        /**
         * @return the letter shown while the copy is tested
         */
        ResultLetter pending() {
            List<String> p = new ArrayList<>();
            p.add("你的术式已经送到。学院正照它另刻一份副本，把" + cases() + "岗位情形逐一送进去试讯……");
            p.add("你的原稿不会被动。");
            return letter(State.PENDING, "检验中……", p);
        }

        /**
         * @return the letter shown when the spell changed after the last reply
         */
        ResultLetter stale() {
            return letter(State.STALE, "术式改过了", Collections.singletonList(
                    "你改过术式了。上一封回信检验的是旧的那一道，已经失效。请把现在的术式重新送检。"));
        }

        /**
         * @return the letter shown if the check could not take place at all
         */
        ResultLetter error() {
            List<String> p = new ArrayList<>();
            p.add("学院没能把你的术式誊录下来，这次检验没有进行。");
            p.add("你的原稿没有被动。回到术式，再送一次试试。");
            return letter(State.ERROR, "检验没能进行", p);
        }

        /**
         * @param r the result of the trusted check
         * @return the reply
         */
        ResultLetter reply(ValidationResult r) {
            boolean broken = r.getModelError() != null;
            // a copy that could not run answered nothing reliable, so no slip is enclosed
            List<Row> rows = broken ? Collections.emptyList() : rows(r);
            // the letter counts situations: with two lamps one situation fills two rows of the slip
            int situations = situations(rows);
            int failed = situations(rows, false);
            int silent = situations(rows, true);
            List<String> structure = new ArrayList<>();
            for (String s : r.getStructureErrors())
                structure.add(structure(s));

            List<String> p = new ArrayList<>();
            String sent = "你的术式送到了。我们没有动你的原稿，照它另刻了一份副本";
            if (broken) {
                ValidationResult.Trouble trouble = r.getTrouble();
                // a misplaced part is the likely reason the copy could not even be built
                boolean cause = trouble == ValidationResult.Trouble.OTHER && !structure.isEmpty();
                if (trouble == ValidationResult.Trouble.LOOSE)
                    p.add(sent + "——可副本立不起来：" + loose(r.getLooseElement()) + "，一种情形也没能试。");
                else if (trouble == ValidationResult.Trouble.UNSTABLE)
                    p.add(sent + "——可副本没能运转起来：有讯线互相顶撞，或者来回打转、停不下来，试讯没法做完。");
                else if (cause)
                    p.add(sent + "——可术式本身有不合本章规矩的地方，副本没法试讯：");
                else
                    p.add(sent + "——可副本没能立起来，一种情形也没能试。");
                if (!structure.isEmpty()) {
                    if (!cause)
                        p.add("另外，术式本身还有不合本章规矩的地方：");
                    for (String s : structure)
                        p.add("· " + s);
                }
                if (trouble == ValidationResult.Trouble.LOOSE)
                    p.add("无讯不作否：没接上的地方，我们不会把它当成" + device() + claim(false) + "。"
                            + (structure.isEmpty() ? "接上之后再送来。" : "接上、改好之后再送来。"));
                else if (trouble == ValidationResult.Trouble.UNSTABLE)
                    p.add("看看是不是有两道讯接进了同一处，或者讯线绕回了它自己。");
                else if (cause)
                    p.add("改好之后再送来。");
                else
                    p.add("看看讯线有没有接错地方，再送来。");
            } else {
                p.add(sent + "，把" + cases() + "岗位情形逐一送进去试讯，副本的回应都抄在试讯单上。");
                if (r.isPassed())
                    p.add(cases() + "情形，副本的回应全部合乎" + rule() + "。这道术式可以刻进" + device()
                            + "了——" + word("letter.keeps", "锁纹今夜不会熄灭。"));
                else if (silent > 0 && silent == situations)
                    p.add("每一种情形，副本的许可印都什么也没收到——术式还没有接通到许可印。"
                            + "无讯不作否，我们不会把它当成" + device() + claim(false) + "。接通之后再送来。");
                else if (failed > 0) {
                    p.add("有" + count(failed) + "种情形，副本的回应不合" + rule() + "，试讯单上已用红笔圈出。"
                            + rule() + "没有错，是术式还差一点：对照那" + (failed == 1 ? "一行" : "几行") + "改一改，再送来。");
                    if (silent > 0)
                        p.add("标着「" + silentWord() + "」的情形，副本的许可印什么也没收到——多半是有讯线没接上。"
                                + "无讯不作否，我们不会把它当成" + device() + claim(false) + "。");
                } else if (r.getRowsTested() == 0)
                    p.add("可试讯没能开始，没法下结论。再送一次试试。");
                else if (r.getRowsTested() != r.getRowsExpected())
                    p.add("可试讯只做完了" + count(r.getRowsTested()) + "种情形，没法下结论。再送一次试试。");
                else if (!structure.isEmpty())
                    p.add("试讯单上的" + cases() + "情形，副本都答对了。");
                if (!structure.isEmpty()) {
                    boolean only = failed == 0;
                    p.add((only ? "可是，" : "另外，") + "术式本身还有不合本章规矩的地方：");
                    for (String s : structure)
                        p.add("· " + s);
                    if (only)
                        p.add("改好之后再送来。");
                }
            }
            boolean passed = r.isPassed();
            return new ResultLetter(passed ? State.PASSED : State.FAILED, subject(), passed ? "检验通过" : RETURNED_STAMP,
                    p, rows, passed ? Mark.SEAL : Mark.RETURNED, rule());
        }

        private List<Row> rows(ValidationResult r) {
            List<Row> rows = new ArrayList<>();
            boolean several = level.getOutputs().size() > 1;
            int situation = 0;
            Map<String, String> previous = null;
            for (ValidationResult.Check c : r.getChecks()) {
                List<String> stations = new ArrayList<>();
                List<String> cells = new ArrayList<>();
                for (Map.Entry<String, String> e : c.getInputs().entrySet()) {
                    stations.add(level.getPortLabel(e.getKey()));
                    cells.add(station(e.getValue()));
                }
                // the checks of all lamps of one situation follow each other
                if (!several || !c.getInputs().equals(previous))
                    situation++;
                previous = c.getInputs();
                String actual = c.isSilent() ? silentWord() : door(c.getActual());
                Row row = new Row(stations, cells, door(c.getExpected()), actual, c.isPassed(), c.isSilent(), rule());
                row.lamp = several ? level.getPortLabel(c.getOutput()) : "";
                row.situation = situation;
                rows.add(row);
            }
            return rows;
        }

        private String station(String value) {
            switch (value) {
                case "1":
                    return phrase("letter.input.1", ExperimentGuide.HOLD);
                case "0":
                    return phrase("letter.input.0", ExperimentGuide.EMPTY);
                default:
                    return value;
            }
        }

        /** The station phrase without the station name: "{0} 持钥印" gives 持钥印. */
        private String phrase(String key, String fallback) {
            return word(key, "{0} " + fallback).replace("{0}", "").trim();
        }

        private String door(String value) {
            switch (value) {
                case "1":
                    return word("letter.output.1", "门开");
                case "0":
                    return word("letter.output.0", "门不开");
                case "X":
                    return "开不开都行";
                default:
                    return value;
            }
        }

        private String silentWord() {
            return word("letter.output.silent", "无讯");
        }

        /**
         * @param element the element name of the part that is not connected, null for a bare wire
         * @return e.g. 许可印还没有接上讯线
         */
        private String loose(String element) {
            if (element == null)
                return "有一段讯线没有源头，不知道该传什么讯";
            if (element.equals("Out"))
                return (level.getOutputs().size() > 1 ? "有一枚许可印" : "许可印") + "还没有接上讯线";
            String t = thing(element);
            if (t == null || element.equals("In"))
                return "有一处" + (t == null ? "部件" : t) + "还有一端空着，没接上讯线";
            return "有一枚" + t + "还有一端空着，没接上讯线";
        }

        private String port(String id) {
            if (level.getInputs().contains(id))
                return "「" + level.getPortLabel(id) + "」";
            if (level.getOutputs().contains(id))
                return level.getOutputs().size() > 1 ? "许可印「" + level.getPortLabel(id) + "」" : "许可印";
            return "「" + id + "」";
        }

        private static String thing(String element) {
            switch (element) {
                case "And":
                    return "「双印」符文";
                case "Or":
                    return "「任一」符文";
                case "XOr":
                    return "「单席」符文";
                case "Not":
                    return "「反相」符文";
                case "In":
                    return "岗位";
                case "Out":
                    return "许可印";
                case "Const":
                case "Ground":
                case "VDD":
                    return "恒讯石";
                default:
                    return null;
            }
        }

        private String allowed() {
            List<String> names = new ArrayList<>();
            for (String a : level.getAllowed()) {
                String t = thing(a);
                if (t != null)
                    names.add(t);
            }
            return String.join("、", names);
        }

        /**
         * @param error a structure error of the validator
         * @return the same in the words of the world
         */
        String structure(String error) {
            Matcher m = NOT_USED.matcher(error);
            if (m.matches()) {
                if (m.group(1).equals("Testcase"))
                    return "术式里夹了一张自带的试讯单。学院只认自己的试讯单，请把它拿掉。";
                String t = thing(m.group(1));
                return "术式里用了本章用不上的" + (t == null ? "东西" : t) + "。这一章只用" + allowed() + "。";
            }
            m = EXTRA.matcher(error);
            if (m.matches()) {
                boolean named = !m.group(2).trim().isEmpty();
                if (m.group(1).equals("输入")) {
                    List<String> labels = new ArrayList<>();
                    for (String in : level.getInputs())
                        labels.add(level.getPortLabel(in));
                    return "术式里多了" + (named ? "「" + m.group(2) + "」这个" : "一个没写名字的") + "岗位。" + place() + "只有"
                            + String.join("、", labels) + "。";
                }
                return "术式里多了" + (named ? "「" + m.group(2) + "」这枚" : "一枚没写名字的") + "许可印。"
                        + device() + "只认" + count(level.getOutputs().size()) + "枚许可印。";
            }
            m = WIDTH.matcher(error);
            if (m.matches())
                return port(m.group(1)) + "的讯线被改成了" + count(Integer.parseInt(m.group(2))) + "股；这里只该传一股讯。";
            m = MISSING.matcher(error);
            if (m.matches()) {
                if (m.group(1).equals("输入"))
                    return "术式里找不到" + port(m.group(2)) + "——这一处的报告没有地方接进来。";
                return "术式里找不到" + port(m.group(2)) + "——副本无论怎么试，" + device() + "都收不到回应。";
            }
            m = TWICE.matcher(error);
            if (m.matches())
                return port(m.group(2)) + "在术式里出现了" + count(Integer.parseInt(m.group(3))) + "次；每一处只能有一个。";
            return "术式的摆法有一处不合本章规矩。";
        }
    }
}
