package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Window;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ToastPopup extends BasicWindow {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Toast-Timer");
        t.setDaemon(true);
        return t;
    });

    private final UiExecutor uiExecutor;

    public ToastPopup(String message, UiExecutor uiExecutor) {
        this.uiExecutor = uiExecutor;
        setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.NO_FOCUS));

        Label label = new Label(" " + message + " ");
        label.setForegroundColor(UiTheme.SELECT_HIGHLIGHT_FG);
        label.setBackgroundColor(UiTheme.SELECT_HIGHLIGHT_BG);
        setComponent(label.withBorder(Borders.doubleLine()));
    }

    public void showFor(MultiWindowTextGUI gui, int durationSeconds) {
        uiExecutor.run(() -> gui.addWindow(this));
        scheduler.schedule(() -> {
            uiExecutor.run(this::close);
        }, durationSeconds, TimeUnit.SECONDS);
    }
}
