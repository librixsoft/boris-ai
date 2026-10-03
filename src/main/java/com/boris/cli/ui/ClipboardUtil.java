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

    public static String paste() {
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                    Object data = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    if (data != null) {
                        return data.toString();
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac") || os.contains("darwin")) {
                return readClipboardCommand(new String[]{"pbpaste"});
            } else if (os.contains("win")) {
                return readClipboardCommand(new String[]{"powershell.exe", "-NoProfile", "-Command", "Get-Clipboard"});
            } else {
                String text = readClipboardCommand(new String[]{"wl-paste"});
                if (text == null) {
                    text = readClipboardCommand(new String[]{"xclip", "-selection", "clipboard", "-o"});
                }
                if (text == null) {
                    text = readClipboardCommand(new String[]{"xsel", "--clipboard", "--output"});
                }
                return text;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String readClipboardCommand(String[] command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            try (java.io.InputStream is = process.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                boolean finished = process.waitFor(1, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
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
