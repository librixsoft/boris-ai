package com.boris.tooling;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class WriteArgsPreviewStreamer {

    public record PreviewInfo(String path, int shownLines) {}

    private static final ThreadLocal<PreviewInfo> LIVE_PREVIEW = new ThreadLocal<>();

    private enum Phase { EXPECT_KEY, READ_KEY, EXPECT_VALUE, IN_PATH, IN_CONTENT, IN_SKIP, AFTER_VALUE, DONE }

    private final Map<Long, ParserCallState> calls = new HashMap<>();

    public static void setLivePreview(PreviewInfo info) {
        LIVE_PREVIEW.set(info);
    }

    public static PreviewInfo takeLivePreview() {
        PreviewInfo info = LIVE_PREVIEW.get();
        LIVE_PREVIEW.remove();
        return info;
    }

    public static void clearLivePreview() {
        LIVE_PREVIEW.remove();
    }

    public void onArgsDelta(long callIndex, String toolName, String argsDelta) {
        if (toolName == null || !"write_file".equals(toolName) || argsDelta == null || argsDelta.isEmpty()) {
            return;
        }
        ParserCallState state = calls.computeIfAbsent(callIndex, k -> new ParserCallState());
        for (int i = 0; i < argsDelta.length(); i++) {
            state.consume(argsDelta.charAt(i));
        }
    }

    public void prepareExecution(long callIndex) {
        ParserCallState state = calls.get(callIndex);
        if (state == null) {
            return;
        }
        state.flushPartialLine();
        if (state.headerEmitted) {
            LIVE_PREVIEW.set(state.toPreviewInfo());
        }
    }

    public void endExecution() {
        LIVE_PREVIEW.remove();
    }

    private static final class ParserCallState {
        private Phase phase = Phase.EXPECT_KEY;
        private final StringBuilder keyBuf = new StringBuilder();
        private final StringBuilder pathBuf = new StringBuilder();
        private final StringBuilder lineBuf = new StringBuilder();
        private int lineCount = 0;
        private boolean headerEmitted = false;
        private boolean escapeMode = false;
        private int unicodePending = 0;
        private final StringBuilder unicodeBuf = new StringBuilder();
        private String shownPath = null;

        void consume(char c) {
            switch (phase) {
                case EXPECT_KEY -> expectKey(c);
                case READ_KEY -> readKey(c);
                case EXPECT_VALUE -> expectValue(c);
                case IN_PATH -> readStringChar(c, pathBuf, () -> phase = Phase.AFTER_VALUE);
                case IN_CONTENT -> readContentChar(c);
                case IN_SKIP -> readStringChar(c, new StringBuilder(), () -> phase = Phase.AFTER_VALUE);
                case AFTER_VALUE -> afterValue(c);
                case DONE -> {}
            }
        }

        private void expectKey(char c) {
            if (c == '"') {
                keyBuf.setLength(0);
                phase = Phase.READ_KEY;
            } else if (c == '{') {
                keyBuf.setLength(0);
            } else if (c == '}') {
                phase = Phase.DONE;
            }
        }

        private void readKey(char c) {
            if (c == '"') {
                phase = Phase.EXPECT_VALUE;
            } else {
                keyBuf.append(c);
            }
        }

        private void expectValue(char c) {
            if (Character.isWhitespace(c) || c == ':') {
                return;
            }
            if (c == '"') {
                String key = keyBuf.toString();
                escapeMode = false;
                unicodePending = 0;
                unicodeBuf.setLength(0);
                switch (key) {
                    case "path" -> { pathBuf.setLength(0); phase = Phase.IN_PATH; }
                    case "content" -> phase = Phase.IN_CONTENT;
                    default -> phase = Phase.IN_SKIP;
                }
            } else if (c == ',') {
                phase = Phase.EXPECT_KEY;
            } else if (c == '}') {
                phase = Phase.DONE;
            } else {
                phase = Phase.IN_SKIP;
            }
        }

        private void readStringChar(char c, StringBuilder target, Runnable onEnd) {
            if (escapeMode) {
                handleEscape(c, target);
                return;
            }
            if (c == '\\') {
                escapeMode = true;
                return;
            }
            if (c == '"') {
                onEnd.run();
                return;
            }
            target.append(c);
        }

        private void readContentChar(char c) {
            if (escapeMode) {
                handleEscape(c, lineBuf);
                return;
            }
            if (c == '\\') {
                escapeMode = true;
                return;
            }
            if (c == '"') {
                phase = Phase.AFTER_VALUE;
                return;
            }
            appendContent(c);
        }

        private void handleEscape(char c, StringBuilder target) {
            if (unicodePending > 0) {
                unicodeBuf.append(c);
                unicodePending--;
                if (unicodePending == 0) {
                    try {
                        char decoded = (char) Integer.parseInt(unicodeBuf.toString(), 16);
                        if (target == lineBuf) {
                            appendContent(decoded);
                        } else {
                            target.append(decoded);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    unicodeBuf.setLength(0);
                }
                escapeMode = false;
                return;
            }
            escapeMode = false;
            switch (c) {
                case 'n' -> {
                    if (target == lineBuf) {
                        emitLine();
                    } else {
                        target.append('\n');
                    }
                    escapeMode = false;
                }
                case 'r' -> {}
                case 't' -> target.append('\t');
                case '"' -> target.append('"');
                case '\\' -> target.append('\\');
                case '/' -> target.append('/');
                case 'b' -> target.append('\b');
                case 'f' -> target.append('\f');
                case 'u' -> {
                    escapeMode = true;
                    unicodePending = 4;
                    unicodeBuf.setLength(0);
                }
                default -> target.append(c);
                }
        }

        private void appendContent(char c) {
            if (c == '\n') {
                emitLine();
                return;
            }
            lineBuf.append(c);
        }

        private void emitLine() {
            ensureHeader();
            lineCount++;
            DiffLineNotifier.notify(String.format("%3d │+ %s", lineCount, lineBuf));
            lineBuf.setLength(0);
            escapeMode = false;
            unicodePending = 0;
        }

        private void ensureHeader() {
            if (!headerEmitted && !pathBuf.isEmpty()) {
                try {
                    var filePath = Paths.get(rawPath());
                    DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " (crear) @@@");
                    headerEmitted = true;
                } catch (java.nio.file.InvalidPathException e) {
                    headerEmitted = false;
                }
            }
        }

        private String rawPath() {
            return pathBuf.toString().trim();
        }

        private void afterValue(char c) {
            if (c == ',') {
                phase = Phase.EXPECT_KEY;
                escapeMode = false;
            } else if (c == '}') {
                phase = Phase.DONE;
            }
        }

        void flushPartialLine() {
            if (lineBuf.length() > 0) {
                emitLine();
            }
        }

        PreviewInfo toPreviewInfo() {
            return new PreviewInfo(resolvePath(), lineCount);
        }

        private String resolvePath() {
            if (shownPath == null) {
                String raw = rawPath();
                if (!raw.isBlank()) {
                    try {
                        shownPath = com.boris.context.ProjectContext.resolveRelativePath(raw);
                    } catch (java.nio.file.InvalidPathException e) {
                        shownPath = null;
                    }
                }
            }
            return shownPath;
        }
    }
}
