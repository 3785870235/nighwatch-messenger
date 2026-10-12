/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.gui.Main;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The free workshop: the unchanged Digital editor with an academy header, a small palette of
 * basic elements and the native tools behind "高级工具". There is no level and no pass state.
 * Must be used on the EDT.
 */
final class AcademyWorkshop extends AcademyTheme.Backdrop {
    private static final String[][] PALETTE = {
            {"输入", "In", "可以单击切换 0/1 的输入"},
            {"输出", "Out", "显示结果的输出端口"},
            {"指示灯", "LED", "1 时发光的小灯"},
            {"非门 NOT", "Not", "把 0 变 1、1 变 0"},
            {"与门 AND", "And", "两个输入都为 1 时输出 1"},
            {"或门 OR", "Or", "至少一个输入为 1 时输出 1"},
            {"异或 XOR", "XOr", "两个输入不同时输出 1"}};

    private final AcademyWorkbench workbench;
    private final Map<String, JButton> palette = new LinkedHashMap<>();
    private final AcademyTheme.Card helpCard;
    private final CardLayout pages = new CardLayout();
    private final JPanel body = AcademyTheme.clear(pages);

    /**
     * Wraps the window.
     *
     * @param main the Digital window
     * @param home called to return to the home window
     */
    AcademyWorkshop(Main main, Runnable home) {
        super(new BorderLayout());
        workbench = new AcademyWorkbench(main, "午夜光术学院 · 自由工坊");
        workbench.hideActionBar();
        workbench.attachMenuBar(true);
        JPanel header = AcademyTheme.clear(new BorderLayout());
        header.setBorder(AcademyTheme.pad(12, 24, 4, 24));
        header.add(AcademyTheme.label("自由工坊", AcademyTheme.script(28), AcademyTheme.IVORY), BorderLayout.WEST);
        JButton menu = AcademyTheme.button("菜单", AcademyTheme.Kind.QUIET);
        menu.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            item(popup, "保存", main::saveChanges);
            item(popup, "打开…", workbench::open);
            item(popup, "撤销", workbench::undo);
            item(popup, "停止", workbench::stopModel);
            item(popup, "帮助", this::toggleHelp);
            item(popup, "高级工具", () -> {
                showPage("edit");
                workbench.setAdvanced(!workbench.isAdvanced());
            });
            item(popup, "返回首页", home);
            popup.show(menu, 0, menu.getHeight());
        });
        header.add(menu, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        body.setBorder(AcademyTheme.pad(8, 24, 18, 24));
        JPanel edit = AcademyTheme.clear(new BorderLayout(0, 12));
        edit.add(workbench, BorderLayout.CENTER);
        JPanel actions = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton elements = AcademyTheme.button("选择元件", AcademyTheme.Kind.QUIET);
        elements.addActionListener(e -> showPage("palette"));
        actions.add(elements);
        JButton run = AcademyTheme.button("运行 / 停止", AcademyTheme.Kind.PRIMARY);
        run.addActionListener(e -> {
            if (workbench.isRunning())
                workbench.stopModel();
            else
                workbench.run();
        });
        actions.add(run);
        edit.add(actions, BorderLayout.SOUTH);
        body.add(edit, "edit");
        JPanel picker = AcademyTheme.clear(new BorderLayout(0, 24));
        picker.setBorder(AcademyTheme.pad(45, 190, 45, 190));
        picker.add(AcademyTheme.label("挑选一个元件", AcademyTheme.script(38), AcademyTheme.GOLD_HI), BorderLayout.NORTH);
        JPanel list = AcademyTheme.clear(new GridLayout(0, 2, 16, 16));
        for (String[] p : PALETTE) {
            JButton button = AcademyTheme.button(p[0], AcademyTheme.Kind.SECONDARY);
            button.setToolTipText(p[2]);
            button.addActionListener(e -> {
                showPage("edit");
                workbench.insert(p[1]);
                main.getCircuitComponent().requestFocusInWindow();
            });
            palette.put(p[1], button);
            list.add(button);
        }
        JButton more = AcademyTheme.button("更多元件…", AcademyTheme.Kind.QUIET);
        more.addActionListener(e -> {
            showPage("edit");
            workbench.showAllElements();
        });
        palette.put("more", more);
        list.add(more);
        picker.add(list, BorderLayout.CENTER);
        JButton back = AcademyTheme.button("← 返回工坊", AcademyTheme.Kind.QUIET);
        back.addActionListener(e -> showPage("edit"));
        picker.add(back, BorderLayout.SOUTH);
        body.add(picker, "palette");
        helpCard = AcademyTheme.titled("工坊手记", new AcademyTheme.Text(
                "放置：选择元件，在空白处单击。\n\n"
                        + "连线：单击端点开始，松开鼠标移动；目标端点单击结束，空白处单击拐弯。Esc 取消。\n\n"
                        + "调整：选中后 Delete 删除；滚轮缩放，右键拖动平移。\n\n"
                        + "运行后单击输入切换 0/1，编辑前停止。菜单内手动保存和打开，包含完整的 Digital 高级工具。",
                AcademyTheme.font(Font.PLAIN, 19), AcademyTheme.IVORY, 680));
        helpCard.setBorder(AcademyTheme.pad(55, 180, 55, 180));
        JButton close = AcademyTheme.button("← 返回工坊", AcademyTheme.Kind.PRIMARY);
        close.addActionListener(e -> showPage("edit"));
        JPanel helpControls = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        helpControls.add(close);
        helpCard.add(helpControls, BorderLayout.SOUTH);
        body.add(helpCard, "help");
        add(body, BorderLayout.CENTER);
    }

    private static void item(JPopupMenu menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(AcademyTheme.body());
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void showPage(String page) {
        AcademyTransition.change(body, () -> {
            pages.show(body, page);
            workbench.attachMenuBar("edit".equals(page));
        });
    }

    void toggleHelp() {
        showPage(helpCard.isVisible() ? "edit" : "help");
    }

    /**
     * @return the workbench
     */
    AcademyWorkbench workbench() {
        return workbench;
    }

    /**
     * @param element the Digital element name or "more"
     * @return the palette button
     */
    JButton paletteButton(String element) {
        return palette.get(element);
    }

    /**
     * @return true if the help card is shown
     */
    boolean isHelpVisible() {
        return helpCard.isVisible();
    }

    /**
     * @param c a component
     * @return the workshop the component belongs to or null
     */
    static AcademyWorkshop of(Component c) {
        return c instanceof AcademyWorkshop ? (AcademyWorkshop) c : null;
    }
}
