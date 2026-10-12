/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The always visible "what to do now" text of the experiment stage. Derived only from the
 * session and the selected inputs; it never tells an output that was not observed yet and
 * never names the rule.
 */
final class ExperimentGuide {
    /**
     * text of a placed key seal
     */
    static final String ON = "放上钥印 = 1";
    /**
     * text of a removed key seal
     */
    static final String OFF = "取下钥印 = 0";
    /**
     * what a station reports when its keeper holds the key seal
     */
    static final String HOLD = "持钥印";
    /**
     * what a station reports when its keeper does not hold the key seal
     */
    static final String EMPTY = "未持钥印";
    /**
     * how the archive door level calls its rule; the lamp chapters name their own rule in the story file
     */
    static final String ARCHIVE_RULE = "门规";
    /**
     * how the archive door level calls its place, e.g. 门前
     */
    static final String ARCHIVE_PLACE = "门前";

    private final List<String> stations;
    private final String device;
    private final String deviceShort;
    private final List<String> chips;

    /**
     * @param stations the display names of the inputs, e.g. "岗位 A"
     */
    ExperimentGuide(List<String> stations) {
        this(stations, "档案门", "门", "我猜会打开", "我猜保持关闭");
    }

    /**
     * @param stations    the display names of the inputs, e.g. "岗位 A"
     * @param device      the device of the level, e.g. "档案门"
     * @param deviceShort the short name, e.g. "门"
     * @param predictLit  the prediction chip for 1
     * @param predictDark the prediction chip for 0
     */
    ExperimentGuide(List<String> stations, String device, String deviceShort, String predictLit, String predictDark) {
        this(stations, device, deviceShort, java.util.Arrays.asList(predictLit, predictDark));
    }

    /**
     * @param stations    the display names of the inputs, e.g. "岗位 A"
     * @param device      the device of the level, e.g. "档案门"
     * @param deviceShort the short name, e.g. "门"
     * @param chips       the prediction chips in the order they are shown
     */
    ExperimentGuide(List<String> stations, String device, String deviceShort, List<String> chips) {
        this.stations = stations;
        this.device = device;
        this.deviceShort = deviceShort;
        this.chips = chips;
    }

    /**
     * @param label the port label, e.g. "岗位 A 持钥印"
     * @param name  the port name
     * @return the station name, e.g. "岗位 A"
     */
    static String station(String label, String name) {
        String s = label.replace("持钥印", "").trim();
        return s.isEmpty() ? name : s;
    }

    /**
     * @param level the level
     * @return the station names of all inputs in input order, e.g. "岗位 A", "岗位 B"
     */
    static List<String> stations(LevelDefinition level) {
        List<String> list = new ArrayList<>();
        for (String in : level.getInputs())
            list.add(station(level.getPortLabel(in), in));
        return list;
    }

    /**
     * @param session  the session
     * @param selected the selected combination
     * @return the guide text
     */
    String next(ExperimentSession session, int selected) {
        if (session.isComplete())
            return Investigation.groups(session.getCombinationCount())
                    + "钥印报告都已如实记录 √\n点击右下角「进入修复」，用你在笔记里发现的规律，修好新" + device + "。";
        if (needsPrediction(session, selected))
            return "最后一组要先猜，再看：\n① 在「你的预测」里" + choices(chips)
                    + "\n② 点击「锁定预测并观察」\n猜错也没关系，" + deviceShort + "会给你新线索。";
        if (session.getObserved(selected) == null) {
            if (session.getObservedCount() == 0 && selected == 0)
                return "第一步：" + (stations.size() == 1 ? "钥印卡现在是" : "两张钥印卡现在都是") + "「" + OFF
                        + "」。\n直接点击下方「观察回应」，看看" + deviceShort + "在没有钥印时怎么回应。";
            return "这组报告还没观察过：点击「观察回应」，回应会记进工作记录。";
        }
        int target = recommend(session, selected);
        StringBuilder sb = new StringBuilder("这组（" + describe(stations, session, selected) + "）已经记在笔记里了。\n下一步换一组：");
        int n = stations.size();
        int step = 1;
        for (int i = 0; i < n; i++) {
            int bit = 1 << (n - 1 - i);
            if ((selected & bit) != (target & bit))
                sb.append("\n").append(circled(step++)).append(" 点击「").append(stations.get(i)).append("」卡片，换成「")
                        .append((target & bit) != 0 ? ON : OFF).append("」");
        }
        sb.append("\n").append(circled(step)).append(needsPrediction(session, target)
                ? " 这是最后一组：先选预测，再点「锁定预测并观察」" : " 点击「观察回应」");
        return sb.toString();
    }

    /**
     * @param session  the session
     * @param selected the selected combination
     * @return true if the selected combination may only be observed with a prediction
     */
    static boolean needsPrediction(ExperimentSession session, int selected) {
        return session.canPredict(selected) && !session.canObserveWithoutPrediction(selected);
    }

    /**
     * Returns the not yet observed combination reachable with the fewest card changes.
     *
     * @param session  the session
     * @param selected the selected combination
     * @return the recommended combination, the selected one if all are observed
     */
    static int recommend(ExperimentSession session, int selected) {
        int best = selected;
        int bestDistance = Integer.MAX_VALUE;
        for (int c = 0; c < session.getCombinationCount(); c++)
            if (session.getObserved(c) == null) {
                int d = Integer.bitCount(c ^ selected);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = c;
                }
            }
        return best;
    }

    /**
     * @param stations    the display names of the inputs, e.g. "岗位 A", in the order of the session inputs
     * @param session     the session
     * @param combination the combination
     * @return e.g. "岗位 A 未持钥印 · 岗位 B 持钥印"
     */
    static String describe(List<String> stations, ExperimentSession session, int combination) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Boolean> e : session.toInputs(combination).entrySet()) {
            if (i > 0)
                sb.append(" · ");
            sb.append(i < stations.size() ? stations.get(i) : e.getKey()).append(' ')
                    .append(e.getValue() ? HOLD : EMPTY);
            i++;
        }
        return sb.toString();
    }

    /**
     * @param chips the prediction chips
     * @return e.g. "选「我猜会亮」或「我猜保持暗」"; with more chips the player picks one of them
     */
    static String choices(List<String> chips) {
        if (chips.size() == 2)
            return "选「" + chips.get(0) + "」或「" + chips.get(1) + "」";
        StringBuilder sb = new StringBuilder("从");
        for (String c : chips)
            sb.append("「").append(c).append("」");
        return sb.append("里选一个").toString();
    }

    private static String circled(int i) {
        return i >= 1 && i <= 9 ? String.valueOf((char) ('①' + i - 1)) : i + ".";
    }
}
