package com.boris.cli.ui;

@FunctionalInterface
public interface UiExecutor {

    void run(Runnable action);
}
