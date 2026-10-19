package com.boris.tooling.tool;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.CodeIndexerService;
import com.boris.context.SymbolRepository;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.LearningLimiter;

/**
 * Tool that downloads GitHub repositories and indexes them into SQLite for fast symbol queries.
 *
 * - Downloads ZIP once from GitHub
 * - Extracts and indexes with JavaParser
 * - Stores symbols in SQLite RAM (shared with local project indexing)
 * - Use query_symbols() to search indexed code
 */
public class LibraryContextTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Rate limit: solo 1 llamada por repo por sesión
    private static final Set<String> PROCESSED_REPOS =
        Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static ToolDefinition create_library_context() {
        var repoProp = Map.of("type", "string", "description",
            "GitHub repository in format owner/repo (e.g., mabe02/lanterna)");
        var versionProp = Map.of("type", "string", "description",
            "Library version or branch (e.g., 3.1.5 or main)");
        var projectPathProp = Map.of("type", "string", "description",
            "Path to project where repo will be cached in .boris/repos/");
        var forceProp = Map.of("type", "boolean", "description",
            "Force re-download and re-index even if already indexed (default: false)");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("repo", repoProp);
        properties.put("version", versionProp);
        properties.put("project_path", projectPathProp);
        properties.put("force", forceProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("repo", "project_path"));

        return ToolDefinition.of(
            "create_library_context",
            "Downloads GitHub repo and indexes it into SQLite for symbol queries. " +
            "After indexing, use query_symbols() to search classes/methods. " +
            "Use force=true to re-download and re-index.",
            schema);
    }

    public static String execute(Map<String, Object> args) {
        String repo = (String) args.getOrDefault("repo", "");
        String version = (String) args.getOrDefault("version", "main");
        String projectPath = (String) args.getOrDefault("project_path", ".");
        boolean force = Boolean.TRUE.equals(args.get("force"));

        if (repo == null || repo.isBlank() || !repo.contains("/")) {
            return formatOutput(false, "Error: repo is required (format: owner/repo)");
        }

        // Check learning limits (library context is expensive)
        if (!force && !LearningLimiter.allowLearningCall(repo)) {
            return formatOutput(false,
                "Límite de aprendizaje alcanzado. " + LearningLimiter.getStatus() + 
                ". NO intentes descargar más repositorios. Corrige el código con la información que ya tienes.");
        }

        String repoKey = repo.toLowerCase();

        // Rate limit check
        if (!force && PROCESSED_REPOS.contains(repoKey)) {
            int count = SymbolRepository.countByRepo(repo);
            ToolProgressNotifier.notify("create_library_context",
                "Already processed: " + repo + " (" + count + " symbols)");
            return formatOutput(true,
                repo + " ya indexado con " + count + " símbolos. " +
                "Usa query_symbols() para buscar. NO vuelvas a llamar este tool.");
        }

        // Check if already indexed
        if (!force && SymbolRepository.isIndexed(repo)) {
            int count = SymbolRepository.countByRepo(repo);
            PROCESSED_REPOS.add(repoKey);
            ToolProgressNotifier.notify("create_library_context",
                "Cache hit: " + repo + " (" + count + " symbols)");
            return formatOutput(true,
                repo + " ya indexado con " + count + " símbolos. " +
                "Usa query_symbols() para buscar.");
        }

        Path borisDir = Paths.get(projectPath, ".boris");
        String repoName = repo.split("/")[1];
        Path repoDir = borisDir.resolve("repos").resolve(repoName);

        try {
            // Ensure .boris is in .gitignore
            ensureGitignore(Paths.get(projectPath));

            // Download ZIP only if not exists locally
            if (force || !Files.exists(repoDir)) {
                ToolProgressNotifier.notify("create_library_context",
                    "Downloading " + repo + " v" + version + "...");
                downloadAndExtractZip(repo, version, repoDir);
            } else {
                ToolProgressNotifier.notify("create_library_context",
                    "Using cached repo (no download)");
            }

            // Index into SQLite
            ToolProgressNotifier.notify("create_library_context",
                "Indexing with JavaParser...");

            int count = CodeIndexerService.indexRepository(repoName, repoDir);
            if (!repoName.equals(repo)) {
                CodeIndexerService.indexRepository(repo, repoDir);
            }

            PROCESSED_REPOS.add(repoKey);

            ToolProgressNotifier.notify("create_library_context",
                "Indexed " + count + " symbols from " + repo);

            return formatOutput(true,
                "Indexado " + repo + " v" + version + " con " + count + " símbolos. " +
                "Usa query_symbols(\"ClassName\") para buscar clases/métodos.");

        } catch (Exception e) {
            // If download fails but already indexed, use that
            if (SymbolRepository.isIndexed(repo)) {
                int count = SymbolRepository.countByRepo(repo);
                PROCESSED_REPOS.add(repoKey);
                return formatOutput(true,
                    "Error descargando " + repo + ": " + e.getMessage() +
                    ". Usando índice existente (" + count + " símbolos).");
            }
            return formatOutput(false, "Error: " + e.getMessage());
        }
    }

    // === ZIP DOWNLOAD & EXTRACTION ===

    private static void downloadAndExtractZip(String repo, String branch, Path targetDir) throws IOException {
        if (Files.exists(targetDir)) {
            deleteDirectory(targetDir);
        }
        Files.createDirectories(targetDir);

        String refPath = resolveRef(repo, branch);
        ToolProgressNotifier.notify("create_library_context", "Resolved ref: " + refPath);

        String zipUrl = "https://codeload.github.com/" + repo + "/zip/" + refPath;
        byte[] zipData = downloadUrl(zipUrl);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            String rootFolder = null;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (rootFolder == null && name.contains("/")) {
                    rootFolder = name.substring(0, name.indexOf('/') + 1);
                }

                if (rootFolder != null && name.startsWith(rootFolder)) {
                    name = name.substring(rootFolder.length());
                }

                if (name.isEmpty()) continue;

                Path outPath = targetDir.resolve(name);

                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    private static String resolveRef(String repo, String version) throws IOException {
        String defaultBranch = fetchDefaultBranch(repo);

        if (version == null || version.isBlank()) {
            return "refs/heads/" + defaultBranch;
        }

        String v = version.trim();
        if (v.equalsIgnoreCase("main") || v.equalsIgnoreCase("master")
            || v.equalsIgnoreCase("HEAD") || v.equalsIgnoreCase("default")) {
            return "refs/heads/" + defaultBranch;
        }

        if (refExists(repo, "heads", v)) {
            return "refs/heads/" + v;
        }
        if (refExists(repo, "tags", v)) {
            return "refs/tags/" + v;
        }

        throw new IOException("La versión '" + v + "' no existe como branch ni tag en " + repo
            + ". Branch por defecto del repo: '" + defaultBranch
            + "'. Usa ese valor o un tag válido de https://github.com/" + repo + "/tags");
    }

    private static String fetchDefaultBranch(String repo) throws IOException {
        byte[] data = downloadUrl("https://api.github.com/repos/" + repo);
        String defaultBranch;
        try {
            defaultBranch = MAPPER.readTree(data).path("default_branch").asText(null);
        } catch (IOException e) {
            throw new IOException("Respuesta inválida de la API de GitHub para " + repo);
        }
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new IOException("La API de GitHub no retornó default_branch para " + repo);
        }
        return defaultBranch;
    }

    private static boolean refExists(String repo, String type, String ref) throws IOException {
        URL url = new URL("https://api.github.com/repos/" + repo + "/git/refs/" + type + "/" + ref);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Boris-AI/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                return true;
            }
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return false;
            }
            throw new IOException("HTTP " + status + " consultando ref " + type + "/" + ref + " de " + repo);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] downloadUrl(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "Boris-AI/1.0");

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_PERM ||
            status == HttpURLConnection.HTTP_MOVED_TEMP ||
            status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            return downloadUrl(newUrl);
        }

        if (status != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("HTTP " + status + " for " + urlStr);
        }

        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.delete(path); } catch (IOException e) { }
                });
        }
    }

    private static void ensureGitignore(Path projectRoot) {
        Path gitignore = projectRoot.resolve(".gitignore");
        try {
            String content = "";
            if (Files.exists(gitignore)) {
                content = Files.readString(gitignore);
            }

            if (!content.contains(".boris/")) {
                String newContent = content;
                if (!content.endsWith("\n") && !content.isEmpty()) {
                    newContent += "\n";
                }
                newContent += "\n# Boris AI cached repos\n.boris/\n";
                Files.writeString(gitignore, newContent);
            }
        } catch (IOException e) {
            // Ignore gitignore errors
        }
    }

    private static String formatOutput(boolean success, String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"success\":" + success + ",\"message\":\"" + message + "\"}";
        }
    }
}
