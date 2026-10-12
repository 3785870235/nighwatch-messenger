/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.Model;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.gui.Main;
import de.neemann.digital.lang.Lang;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;

/**
 * Hosts the real Digital canvas of a {@link Main} window inside an academy screen.
 * The upstream tool bar and menu bar are kept and reachable through "高级工具";
 * all editing, simulation and saving is done by the unchanged Digital editor.
 * Must be used on the EDT.
 */
final class AcademyWorkbench extends JPanel {
    private final Main main;
    private final JMenuBar menuBar;
    private final JPanel advancedStrip = new JPanel(new BorderLayout());
    private final JPanel actions = AcademyTheme.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel bar = AcademyTheme.clear(new BorderLayout(8, 0));
    private final JLabel runState = AcademyTheme.label(" ", AcademyTheme.font(Font.BOLD, 14), AcademyTheme.MUTED);
    private final JButton advancedButton = AcademyTheme.button("高级工具", AcademyTheme.Kind.QUIET);
    private final Timer stateTimer;
    private boolean advanced;
    private boolean menuAttached;
    private boolean lastRunning;

    /**
     * Moves the canvas of the window into this panel.
     *
     * @param main  the Digital window
     * @param title the academy window title
     */
    AcademyWorkbench(Main main, String title) {
        super(new BorderLayout());
        this.main = main;
        this.menuBar = main.getJMenuBar();
        setOpaque(false);

        JPanel canvasHost = new JPanel(new BorderLayout());
        canvasHost.setBackground(new java.awt.Color(0x101C2E));
        canvasHost.setBorder(BorderFactory.createLineBorder(AcademyTheme.LINE, 1));
        main.embedCircuitView(canvasHost);
        styleScrollBars(canvasHost);

        JToolBar toolBar = main.getToolBar();
        if (toolBar.getParent() != null)
            toolBar.getParent().remove(toolBar);
        toolBar.setFloatable(false);
        advancedStrip.add(toolBar, BorderLayout.CENTER);
        advancedStrip.setVisible(false);
        canvasHost.add(advancedStrip, BorderLayout.NORTH);

        bar.setBorder(AcademyTheme.pad(0, 0, 8, 0));
        bar.add(actions, BorderLayout.CENTER);
        JPanel right = AcademyTheme.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(runState);
        advancedButton.setToolTipText("显示 Digital 原生工具栏与菜单（全部原生功能）");
        advancedButton.addActionListener(e -> setAdvanced(!advanced));
        right.add(advancedButton);
        AcademyAtmosphere.watch(main.getCircuitComponent(), () -> {
            if (AcademyAtmosphere.motion() && AcademyAtmosphere.magic() && isRunning())
                main.getCircuitComponent().graphicHasChanged();
        });
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);
        add(canvasHost, BorderLayout.CENTER);

        main.addPropertyChangeListener("title", e -> {
            String t = (String) e.getNewValue();
            String want = title + (main.isStateChanged() ? " · 未保存" : "");
            if (!want.equals(t))
                main.setTitle(want);
        });
        main.setTitle(title);

        stateTimer = new Timer(250, e -> updateRunState());
        updateRunState();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        stateTimer.start();
    }

    @Override
    public void removeNotify() {
        stateTimer.stop();
        super.removeNotify();
    }

    /**
     * Adds a button to the simplified action bar.
     *
     * @param text   the text
     * @param kind   the look
     * @param action the action
     * @return the button
     */
    JButton addAction(String text, AcademyTheme.Kind kind, Runnable action) {
        JButton b = AcademyTheme.button(text, kind);
        b.addActionListener(e -> action.run());
        actions.add(b);
        return b;
    }

    /** Uses the enclosing game's compact action strip instead of the native wrapper bar. */
    void hideActionBar() {
        bar.setVisible(false);
    }

    private static void styleScrollBars(java.awt.Container parent) {
        for (java.awt.Component component : parent.getComponents()) {
            if (component instanceof javax.swing.JScrollBar) {
                javax.swing.JScrollBar scroll = (javax.swing.JScrollBar) component;
                scroll.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
                    @Override
                    protected void configureScrollBarColors() {
                        thumbColor = AcademyTheme.LINE;
                        trackColor = new java.awt.Color(0x101C2E);
                    }

                    @Override
                    protected JButton createDecreaseButton(int orientation) {
                        return arrow();
                    }

                    @Override
                    protected JButton createIncreaseButton(int orientation) {
                        return arrow();
                    }

                    private JButton arrow() {
                        JButton button = new JButton();
                        button.setPreferredSize(new Dimension(0, 0));
                        return button;
                    }
                });
                scroll.setPreferredSize(scroll.getOrientation() == javax.swing.JScrollBar.VERTICAL
                        ? new Dimension(6, 0) : new Dimension(0, 6));
            } else if (component instanceof java.awt.Container)
                styleScrollBars((java.awt.Container) component);
        }
    }

    /**
     * Lets the player place one new element with the mouse; nothing is wired automatically
     * except Digital's own behaviour of connecting pins placed directly on each other.
     *
     * @param elementName the Digital element name
     */
    void insert(String elementName) {
        main.getCircuitComponent().setPartToInsert(
                new VisualElement(elementName).setShapeFactory(main.getShapeFactory()));
    }

    /**
     * Starts the simulation of the edited circuit.
     */
    void run() {
        main.start(null, false);
    }

    /**
     * Stops the simulation so that the circuit can be edited again.
     */
    void stopModel() {
        main.ensureModelIsStopped();
        updateRunState();
    }

    /**
     * Undoes the last modification.
     */
    void undo() {
        main.ensureModelIsStopped();
        if (main.getCircuitComponent().getUndoAction().isEnabled())
            main.getCircuitComponent().getUndoAction().actionPerformed(null);
    }

    /**
     * Opens a circuit with the native dialog.
     */
    void open() {
        if (main.getOpenAction() != null)
            main.getOpenAction().actionPerformed(null);
    }

    /**
     * @return true if the simulation runs
     */
    boolean isRunning() {
        Model m = main.getModel();
        return m != null && m.isRunning();
    }

    /**
     * Attaches or removes the native menu bar. Without it no native shortcut is active.
     * While attached but not in advanced mode it has zero height: the shortcuts still work.
     *
     * @param attach true to attach
     */
    void attachMenuBar(boolean attach) {
        menuAttached = attach;
        main.setJMenuBar(attach ? menuBar : null);
        updateMenuBarSize();
        main.getRootPane().revalidate();
    }

    /**
     * Shows or hides the native tool bar and menu bar.
     *
     * @param on true to show
     */
    void setAdvanced(boolean on) {
        advanced = on;
        advancedStrip.setVisible(on);
        advancedButton.setText(on ? "收起高级工具" : "高级工具");
        updateMenuBarSize();
        main.getRootPane().revalidate();
        main.getRootPane().repaint();
    }

    /**
     * @return true if the native tools are visible
     */
    boolean isAdvanced() {
        return advanced;
    }

    /**
     * Opens the native element menu with every Digital component.
     */
    void showAllElements() {
        if (!menuAttached)
            return;
        setAdvanced(true);
        JMenu m = findMenu(Lang.get("menu_elements"));
        if (m != null)
            m.doClick(0);
    }

    /**
     * @param text the menu text
     * @return the top level native menu or null
     */
    JMenu findMenu(String text) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu m = menuBar.getMenu(i);
            if (m != null && text.equals(m.getText()))
                return m;
        }
        return null;
    }

    private void updateMenuBarSize() {
        menuBar.setPreferredSize(advanced ? null : new Dimension(0, 0));
        menuBar.revalidate();
    }

    /**
     * @return the run state text shown in the bar
     */
    String runStateText() {
        return runState.getText();
    }

    private void updateRunState() {
        boolean running = isRunning();
        if (running != lastRunning || runState.getText().trim().isEmpty()) {
            lastRunning = running;
            runState.setText(running ? "● 运行中 · 停止后才能编辑" : "■ 已停止 · 可以编辑");
            runState.setForeground(running ? AcademyTheme.OK : AcademyTheme.MUTED);
        }
    }

    /**
     * @return the file of the circuit or null
     */
    File file() {
        return main.getBaseFileName();
    }
}
