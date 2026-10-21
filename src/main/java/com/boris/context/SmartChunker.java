package com.boris.context;

import com.boris.context.ContentRepository.DocChunk;
import com.boris.tooling.ToolProgressNotifier;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SmartChunker {

    private static final int CHUNK_SIZE = 4000;
    private static final int MAX_CODE_SNIPPET_LINES = 60;

    private static final Pattern MD_HEADING_PATTERN = Pattern.compile("^(#{2,6})\\s+(.+)$");
    private static final Pattern FENCE_LINE_PATTERN = Pattern.compile("^[ \t]*```[ \t]*([A-Za-z0-9_+-]*)[ \t]*\\R?$");

    private SmartChunker() {
    }

    public static List<DocChunk> chunk(String repo, String path, String content) {
        String ext = extensionOf(path);
        return switch (ext) {
            case "java" -> chunkJava(repo, path, content);
            case "md", "markdown" -> chunkMarkdown(repo, path, content);
            default -> chunkRaw(repo, path, content, "raw");
        };
    }

    private static List<DocChunk> chunkJava(String repo, String path, String content) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);
            List<DocChunk> chunks = new ArrayList<>();
            int chunkIndex = 0;
            String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");

            for (TypeDeclaration<?> type : cu.getTypes()) {
                int startLine = type.getBegin().map(p -> p.line).orElse(1);
                int endLine = type.getEnd().map(p -> p.line).orElse((int) content.lines().count());
                String body = extractLines(content, startLine, endLine);
                String title = pkg.isEmpty() ? type.getNameAsString() : pkg + "." + type.getNameAsString();
                for (String piece : splitBySize(body)) {
                    chunks.add(new DocChunk(repo, path, chunkIndex, title, piece, "class"));
                    chunkIndex++;
                }
            }
            if (chunks.isEmpty()) {
                return chunkRaw(repo, path, content, "raw");
            }
            return chunks;
        } catch (Exception e) {
            ToolProgressNotifier.notify("index_content",
                "Java sin parsear, degradando explicito a chunk raw: " + path + " (" + e.getMessage() + ")");
            return chunkRaw(repo, path, content, "raw");
        }
    }

    private static List<DocChunk> chunkMarkdown(String repo, String path, String content) {
        List<DocChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        List<String> lines = content.lines().toList();
        int sectionStart = 0;
        String sectionTitle = Path.of(path).getFileName().toString();

        for (int i = 0; i <= lines.size(); i++) {
            boolean isEnd = i == lines.size();
            boolean isHeading = !isEnd && MD_HEADING_PATTERN.matcher(lines.get(i)).matches();
            boolean isFence = !isEnd && FENCE_LINE_PATTERN.matcher(lines.get(i)).matches();
            if (isEnd || isHeading || isFence) {
                if (i > sectionStart) {
                    for (String piece : splitBySize(joinLines(lines, sectionStart, i))) {
                        chunks.add(new DocChunk(repo, path, chunkIndex, sectionTitle, piece, "section"));
                        chunkIndex++;
                    }
                }
                if (isFence) {
                    int[] fenceEnd = findFenceEnd(lines, i);
                    if (fenceEnd != null) {
                        String fenceBody = joinLines(lines, i + 1, fenceEnd[0]);
                        if (bodySize(fenceBody)) {
                            String title = sectionTitle + " > example";
                            for (String piece : splitBySize(fenceBody)) {
                                chunks.add(new DocChunk(repo, path, chunkIndex, title, piece, "example"));
                                chunkIndex++;
                            }
                        }
                        i = fenceEnd[0];
                        sectionStart = i + 1;
                        continue;
                    }
                }
                if (isHeading) {
                    Matcher m = MD_HEADING_PATTERN.matcher(lines.get(i));
                    if (m.matches()) {
                        sectionTitle = m.group(2).strip();
                    }
                }
                sectionStart = i + 1;
            }
        }
        if (chunks.isEmpty()) {
            return chunkRaw(repo, path, content, "section");
        }
        return chunks;
    }

    private static boolean bodySize(String fenceBody) {
        long count = fenceBody.lines().count();
        return count >= 3 && count <= MAX_CODE_SNIPPET_LINES;
    }

    private static int[] findFenceEnd(List<String> lines, int openIndex) {
        for (int i = openIndex + 1; i < lines.size(); i++) {
            if (FENCE_LINE_PATTERN.matcher(lines.get(i)).matches()) {
                return new int[]{i};
            }
        }
        return null;
    }

    private static List<DocChunk> chunkRaw(String repo, String path, String content, String type) {
        List<String> pieces = splitBySize(content);
        List<DocChunk> chunks = new ArrayList<>();
        String title = extractTitle(content, path);
        int chunkIndex = 0;
        for (String piece : pieces) {
            chunks.add(new DocChunk(repo, path, chunkIndex, title, piece, type));
            chunkIndex++;
        }
        return chunks;
    }

    private static List<String> splitBySize(String content) {
        List<String> pieces = new ArrayList<>();
        if (content.isEmpty()) {
            return pieces;
        }
        StringBuilder current = new StringBuilder();
        for (String line : content.lines().toList()) {
            if (!current.isEmpty() && current.length() + System.lineSeparator().length() + line.length() > CHUNK_SIZE) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append(System.lineSeparator());
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private static String extractLines(String content, int startLine, int endLine) {
        List<String> lines = content.lines().toList();
        return joinLines(lines, Math.max(0, startLine - 1), Math.min(endLine, lines.size()));
    }

    private static String joinLines(List<String> lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static String extractTitle(String content, String path) {
        for (String line : content.split("\n", 20)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                return trimmed.replaceAll("^#+\\s*", "").strip();
            }
            return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
        }
        return Path.of(path).getFileName().toString();
    }

    private static String extensionOf(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
