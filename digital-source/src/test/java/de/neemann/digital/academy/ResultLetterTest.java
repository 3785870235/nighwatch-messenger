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
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * The academy's reply letter: every fixture of the archive gate is worded in the words of the world,
 * a missing message is never written as a closed door and no circuit terms reach the player.
 */
public class ResultLetterTest extends TestCase {
    private static final File LEVEL_DIR = new File("../levels/03_archive_gate");
    private static final File FIXTURES = new File(LEVEL_DIR, "fixtures");
    private static final String[] FORBIDDEN = {"AND", "OR", "PERMIT", "与门", "或门", "非门", "异或", "真值",
            "端口", "输入", "输出", "期望", "实际", "高阻", "仿真", "电路", "元件", "信号", "逻辑", "Testcase", "Const",
            "0", "1"};

    private LevelDefinition level;
    private LevelValidator validator;
    private ResultLetter.Clerk clerk;

    @Override
    protected void setUp() throws Exception {
        level = LevelDefinition.load(new File(LEVEL_DIR, "level.properties"));
        validator = new LevelValidator(level);
        clerk = ResultLetter.Clerk.of(level);
    }

    public void testCorrectSpellIsSealed() throws Exception {
        ResultLetter l = reply(new File(FIXTURES, "and_correct.dig"));
        assertEquals(ResultLetter.State.PASSED, l.getState());
        assertEquals(ResultLetter.Mark.SEAL, l.getMark());
        assertEquals(4, l.getRows().size());
        for (ResultLetter.Row r : l.getRows())
            assertTrue(r.text(), r.isPassed() && !r.isSilent());
        assertTrue(l.text(), l.text().contains("四种情形，副本的回应全部合乎门规"));
        assertTrue(l.text().contains("岗位 A 持钥印 · 岗位 B 持钥印 → 门规：门开 · 副本：门开 ✓"));
        assertEquals(l, reply(level.getReferenceFile()));
    }

    public void testOrIsSentBackWithTwoRows() throws Exception {
        ResultLetter l = reply(new File(FIXTURES, "or_wrong.dig"));
        assertEquals(ResultLetter.State.FAILED, l.getState());
        assertEquals(ResultLetter.Mark.RETURNED, l.getMark());
        assertEquals(ResultLetter.RETURNED_STAMP, l.getStatus());
        assertTrue(l.text(), l.text().contains("有两种情形"));
        assertTrue(l.text().contains("岗位 A 未持钥印 · 岗位 B 持钥印 → 门规：门不开 · 副本：门开 ✗"));
        assertTrue(l.text().contains("岗位 A 持钥印 · 岗位 B 未持钥印 → 门规：门不开 · 副本：门开 ✗"));
        assertTrue(l.text(), l.text().contains("「任一」符文"));
        assertTrue(l.text().contains("另外，"));
        assertEquals(2, failed(l));
    }

    public void testOwnTestSlipIsNotTrusted() throws Exception {
        ResultLetter l = reply(new File(FIXTURES, "or_with_fake_testcase.dig"));
        assertEquals(ResultLetter.State.FAILED, l.getState());
        assertTrue(l.text(), l.text().contains("自带的试讯单"));
        assertEquals(2, failed(l));
    }

    public void testConstantStones() throws Exception {
        ResultLetter one = reply(new File(FIXTURES, "const_one.dig"));
        assertEquals(3, failed(one));
        assertTrue(one.text(), one.text().contains("恒讯石"));
        ResultLetter zero = reply(new File(FIXTURES, "const_zero.dig"));
        assertEquals(1, failed(zero));
        assertTrue(zero.text(), zero.text().contains("岗位 A 持钥印 · 岗位 B 持钥印 → 门规：门开 · 副本：门不开 ✗"));
        assertTrue(zero.text().contains("对照那一行"));
    }

    public void testSilenceIsNeverAClosedDoor() throws Exception {
        for (File f : new File[]{level.getTemplateFile(), new File(FIXTURES, "unconnected.dig")}) {
            ResultLetter l = reply(f);
            assertEquals(f.getName(), ResultLetter.State.FAILED, l.getState());
            assertFalse(l.text(), l.text().contains("副本：门不开"));
            assertTrue(l.text(), l.text().contains("无讯不作否"));
            for (ResultLetter.Row r : l.getRows())
                if (r.isSilent())
                    assertEquals("无讯", r.getActual());
        }
        // nothing could be tried, so no slip is enclosed and nothing is guessed
        ResultLetter t = reply(level.getTemplateFile());
        assertTrue(t.getRows().isEmpty());
        assertTrue(t.text(), t.text().contains("可副本立不起来：许可印还没有接上讯线，一种情形也没能试。"));
    }

    public void testLooseParts() throws Exception {
        assertEquals(ValidationResult.Trouble.LOOSE, validator.validate(level.getTemplateFile()).getTrouble());
        assertEquals("Out", validator.validate(level.getTemplateFile()).getLooseElement());
        assertEquals(ValidationResult.Trouble.NONE,
                validator.validate(new File(FIXTURES, "or_wrong.dig")).getTrouble());
        assertTrue(LevelValidator.isMessage(de.neemann.digital.lang.Lang.get("err_noValueSetFor_N0_atElement_N1",
                "in", "Out (PERMIT)"), "err_noValueSetFor_N0_atElement_N1", 2));
        assertFalse(LevelValidator.isMessage("something else", "err_noValueSetFor_N0_atElement_N1", 2));
        assertFalse(LevelValidator.isMessage(null, "err_noOutConnectedToWire", 1));
    }

    public void testMissingSealIsNamed() throws Exception {
        ResultLetter l = reply(new File(FIXTURES, "missing_port.dig"));
        assertEquals(ResultLetter.State.FAILED, l.getState());
        assertTrue(l.text(), l.text().contains("找不到许可印"));
    }

    public void testStructureWords() {
        assertEquals("术式里找不到「岗位 A」——这一处的报告没有地方接进来。", clerk.structure("缺少输入端口 A（岗位 A）"));
        assertEquals("「岗位 B」的讯线被改成了两股；这里只该传一股讯。", clerk.structure("端口 B 应为 1 位，当前为 2 位"));
        assertEquals("许可印在术式里出现了两次；每一处只能有一个。", clerk.structure("输出端口 PERMIT 出现了 2 次"));
        assertTrue(clerk.structure("多余的输入端口“C”").startsWith("术式里多了「C」这个岗位。门前只有岗位 A、岗位 B"));
        assertTrue(clerk.structure("多余的输出端口“”").startsWith("术式里多了一枚没写名字的许可印"));
        assertEquals("术式的摆法有一处不合本章规矩。", clerk.structure("something else"));
    }

    public void testWaitingLetters() {
        assertEquals("尚未送检", clerk.idle().getStatus());
        assertTrue(clerk.idle().text().contains("四种岗位情形"));
        assertTrue(clerk.pending().text().startsWith("检验中"));
        assertTrue(clerk.stale().text().contains("已经失效"));
        assertEquals(ResultLetter.State.ERROR, clerk.error().getState());
        assertTrue(clerk.idle().getRows().isEmpty());
        assertFalse(clerk.idle().equals(clerk.pending()));
        assertEquals(clerk.stale(), clerk.stale());
    }

    public void testPlainWordsWithoutStoryFile() throws Exception {
        ResultLetter l = new ResultLetter.Clerk(level, new Properties()).reply(
                validator.validate(new File(FIXTURES, "const_zero.dig")));
        assertTrue(l.text(), l.text().contains("门规：门开 · 副本：门不开 ✗"));
        assertEquals(level.getTitle(), l.getSubject());
    }

    public void testCounting() {
        assertEquals("两", ResultLetter.count(2));
        assertEquals("四", ResultLetter.count(4));
        assertEquals("16", ResultLetter.count(16));
        assertEquals("二", ResultLetter.ordinal(2));
        assertTrue(clerk.error().isReply());
        assertFalse(clerk.pending().isReply());
    }

    private ResultLetter reply(File f) throws Exception {
        ValidationResult r = validator.validate(f);
        ResultLetter l = clerk.reply(r);
        // every letter is kept for reading the wording: target/academy-letters.txt
        Files.write(new File("target/academy-letters.txt").toPath(), ("---- " + f.getName() + " (" + r.getTrouble()
                        + ": " + r.getModelError() + ")\n" + l.text() + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        assertNoCircuitWords(f.getName(), l);
        return l;
    }

    /**
     * Station names are proper names; apart from them the letter must not contain a single Latin letter,
     * digit or circuit term.
     */
    private static void assertNoCircuitWords(String name, ResultLetter l) {
        String t = l.text().replace("岗位 A", "").replace("岗位 B", "");
        for (String w : FORBIDDEN)
            assertFalse(name + " contains " + w + ":\n" + l.text(), t.contains(w));
        assertFalse(name + " has Latin letters:\n" + l.text(), t.matches("(?s).*[A-Za-z].*"));
    }

    private static int failed(ResultLetter l) {
        int n = 0;
        for (ResultLetter.Row r : l.getRows())
            if (!r.isPassed())
                n++;
        return n;
    }
}
