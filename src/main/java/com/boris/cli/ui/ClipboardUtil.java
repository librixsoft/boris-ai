package com.boris.cli.ui;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ClipboardUtil {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "clipboard-worker");
        t.setDaemon(true);
        return t;
    });

    private ClipboardUtil() {
    }

    public static void copy(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                boolean success = false;

                if (os.contains("mac") || os.contains("darwin")) {
                    success = runClipboardCommand(new String[]{"pbcopy"}, text);
                } else if (os.contains("win")) {
                    success = runClipboardCommand(new String[]{"clip"}, text);
                } else {
                    // Linux / BSD / other Unix
                    success = runClipboardCommand(new String[]{"xclip", "-selection", "clipboard", "-in"}, text);
                    if (!success) {
                        success = runClipboardCommand(new String[]{"xsel", "--clipboard", "--input"}, text);
                    }
                    if (!success) {
                        // also try wl-copy for Wayland
                        success = runClipboardCommand(new String[]{"wl-copy"}, text);
                    }
                }

                if (!success) {
                    success = copyViaAwt(text);
                }

                if (!success) {
                    System.err.println("[ClipboardUtil] Warning: Unable to copy to clipboard. No supported clipboard command found (pbcopy/clip/xclip/xsel/wl-copy/AWT).");
                }
            } catch (Exception e) {
                System.err.println("[ClipboardUtil] Failed to copy to clipboard: " + e.getMessage());
            }
        });
    }

    private static boolean copyViaAwt(String text) {
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean runClipboardCommand(String[] command, String text) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            process = pb.start();

            try (OutputStream os = process.getOutputStream();
                 Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                writer.write(text);
                writer.flush();
            }

            boolean finished = process.waitFor(1, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return false;
        }
    }
}
