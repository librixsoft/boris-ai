package com.boris.context;

import com.boris.context.ContentRepository.DocChunk;
import com.boris.tooling.ToolProgressNotifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class ContentIndexerService {

    private static final int MAX_FILES = 2000;
    private static final int MAX_FILE_BYTES = 200_000;
    private static final int CHUNK_SIZE = 4000;
    private static final int BATCH_SIZE = 50;

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
        "md", "markdown", "txt", "rst", "adoc", "html", "htm",
        "css", "scss", "sass", "less", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "py", "rb", "go", "rs", "php", "java", "kt", "kts", "swift", "c", "cpp", "h", "hpp", "cs",
        "sh", "bash", "zsh", "sql", "vue", "svelte", "json", "yml", "yaml", "toml", "ini",
        "xml", "properties", "gradle", "csv", "svg"
    );

    private static final Set<String> EXCLUDED_DIRS = Set.of(
        ".git", "node_modules", "target", "build", "dist", "out",
        ".idea", ".vscode", ".gradle", "vendor", "bin", "obj"
    );

    public static int indexRepository(String repoName, Path repoDir) {
        ContentRepository.deleteByRepo(repoName);

        List<DocChunk> batch = new ArrayList<>();
        int totalChunks = 0;
        int indexedFiles = 0;

        try (Stream<Path> files = Files.walk(repoDir)) {
            List<Path> textFiles = files
                .filter(Files::isRegularFile)
                .filter(p -> hasTextExtension(p.getFileName().toString()))
                .filter(p -> !isExcluded(repoDir, p))
                .limit(MAX_FILES)
                .toList();

            ToolProgressNotifier.notify("index_content",
                "Indexando " + textFiles.size() + " archivos de contenido...");

            for (Path file : textFiles) {
                String relativePath = repoDir.relativize(file).toString();

                long size;
                try {
                    size = Files.size(file);
                } catch (IOException e) {
                    throw new RuntimeException("Error leyendo tamaño de archivo: " + e.getMessage(), e);
                }
                if (size > MAX_FILE_BYTES || size == 0) {
                    continue;
                }

                String content;
                try {
                    content = Files.readString(file);
                } catch (IOException e) {
                    throw new RuntimeException("Error leyendo archivo " + relativePath + ": " + e.getMessage(), e);
                }

                indexedFiles++;

                List<DocChunk> chunks = buildChunks(repoName, relativePath, content);
                batch.addAll(chunks);

                if (batch.size() >= BATCH_SIZE) {
                    ContentRepository.saveBatch(batch);
                    totalChunks += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                ContentRepository.saveBatch(batch);
                totalChunks += batch.size();
            }

        } catch (IOException e) {
            throw new RuntimeException("Error indexando contenido: " + e.getMessage(), e);
        }

        ToolProgressNotifier.notify("index_content",
            "Indexados " + totalChunks + " registros de " + indexedFiles + " archivos");

        return totalChunks;
    }

    private static List<DocChunk> buildChunks(String repo, String path, String content) {
        return SmartChunker.chunk(repo, path, content);
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

    private static boolean hasTextExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.contains(ext);
    }

    private static boolean isExcluded(Path root, Path file) {
        for (Path part : root.relativize(file)) {
            String name = part.toString();
            if (name.startsWith(".") || EXCLUDED_DIRS.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
