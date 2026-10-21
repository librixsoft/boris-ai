package com.boris.tooling.tool;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.CodeIndexerService;
import com.boris.context.ContentIndexerService;
import com.boris.context.ContentRepository;
import com.boris.context.SymbolRepository;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.LearningLimiter;

/**
 * Tool that downloads any GitHub repository and indexes it into local registries that the agent reads from.
 *
 * - Downloads ZIP once from GitHub
 * - Extracts and indexes Java files with JavaParser (symbols in SQLite RAM)
 * - Extracts and indexes any other text file (markdown, HTML, CSS, JS/TS, books, docs) as content registry
 * - Use github_repo(action='symbols') for Java symbols and github_repo(action='search') for content of any kind
 */
public class LibraryContextTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Rate limit: solo 1 llamada por repo por sesión
    private static final Set<String> PROCESSED_REPOS =
        Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static ToolDefinition github_repo() {
        var actionProp = Map.of("type", "string", "description",
            "Operation mode: download (default), read_files, search, symbols, methods, classes, status, knowledge, knowledge_save, knowledge_list");
        var repoProp = Map.of("type", "string", "description",
            "download: repo in 'owner/repo' format. Other actions: registered repo name as filter (required for read_files/knowledge/knowledge_save, optional filter elsewhere)");
        var versionProp = Map.of("type", "string", "description",
            "download only: version, tag or branch (default: main)");
        var projectPathProp = Map.of("type", "string", "description",
            "download only: project path where repo is cached in .boris/repos/ (default: current project)");
        var forceProp = Map.of("type", "boolean", "description",
            "download only: force re-download and re-index (default: false)");
        var pathsProp = Map.of("type", "array", "items", Map.of("type", "string"), "description",
            "read_files only: file paths chosen from the github_repo 'files' manifest");
        var targetDirProp = Map.of("type", "string", "description",
            "read_files only: base directory in the project where files are copied exactly from cache (optional)");
        var targetPathsProp = Map.of("type", "array", "items", Map.of("type", "string"), "description",
            "read_files only: optional target names, parallel to paths (omit to keep relative paths)");
        var queryProp = Map.of("type", "string", "description",
            "search/symbols only: search term (optional for search if path is given)");
        var pathProp = Map.of("type", "string", "description",
            "search only: relative path inside the repo to read one full file (optional)");
        var typeProp = Map.of("type", "string", "description",
            "symbols only: filter CLASS, METHOD, CONSTRUCTOR, FIELD, INTERFACE or ENUM (optional)");
        var classNameProp = Map.of("type", "string", "description",
            "methods/knowledge only: class name");
        var frameworkProp = Map.of("type", "string", "description",
            "knowledge/knowledge_save/knowledge_list only: framework, library or repo name");
        var focusProp = Map.of("type", "string", "description",
            "learn only: domain to learn from the indexed repo (e.g. routing, components, api, setup)");
        var chunkTypeProp = Map.of("type", "string", "description",
            "search only: optional filter by chunk_type (class, method, section, example, analysis, learning, raw)");
        var contentProp = Map.of("type", "string", "description",
            "knowledge_save only: concise markdown summary with purpose, key methods and usage example");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("action", actionProp);
        properties.put("repo", repoProp);
        properties.put("version", versionProp);
        properties.put("project_path", projectPathProp);
        properties.put("force", forceProp);
        properties.put("paths", pathsProp);
        properties.put("target_dir", targetDirProp);
        properties.put("target_paths", targetPathsProp);
        properties.put("focus", focusProp);
        properties.put("chunk_type", chunkTypeProp);
        properties.put("query", queryProp);
        properties.put("path", pathProp);
        properties.put("type", typeProp);
        properties.put("class_name", classNameProp);
        properties.put("framework", frameworkProp);
        properties.put("content", contentProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        return ToolDefinition.of(
            "github_repo",
            "UNIFIED GitHub repo tool with actions. action='download' (default): download owner/repo and index it; " +
            "the result includes the 'files' manifest and REQUIRES a learning step before writing library code: " +
            "call github_repo(action='learn', repo, focus) right after download. DO NOT copy source files " +
            "(java/js/ts/py/etc) from the repo into the project with read_files+target_dir: those files are for " +
            "READING and pattern extraction only; the system blocks their copy. target_dir is ONLY for assets " +
            "(images, fonts, media) and data files (json/xml/yml/csv). Always generate your own implementation " +
            "adapted to the current project based on learned patterns. action='learn' (repo + focus): required " +
            "synthesis of patterns, key APIs and usage examples from the repo. " +
            "action='read_files': read selected files from the manifest (pass paths); with target_dir copies only " +
            "allowed assets. action='search': search or read indexed content of any kind (query or path required); " +
            "action='symbols': search Java class/method symbols by name. " +
            "action='methods': public methods of one class (pass class_name). " +
            "action='classes': list indexed classes. " +
            "action='status': indexing status summary. " +
            "action='knowledge': read a markdown knowledge card for a framework/class (framework + class_name). " +
            "action='knowledge_save': save/update a knowledge card (framework + class_name + content). " +
            "action='knowledge_list': list cached classes of a framework. " +
            "Use once per repo when the project depends on something you do not know: download once, learn once, never explore for curiosity.",
            schema);
    }

    public static String execute(Map<String, Object> args) {
        String action = (String) args.getOrDefault("action", "download");

        // Dispatch based on action
        switch (action) {
            case "read_files":
                return executeReadFiles(args);
            case "search":
                return executeSearch(args);
            case "symbols":
                return executeSymbols(args);
            case "methods":
                return executeMethods(args);
            case "classes":
                return executeClasses(args);
            case "status":
                return executeStatus(args);
            case "knowledge":
                return executeKnowledge(args);
            case "knowledge_save":
                return executeKnowledgeSave(args);
            case "knowledge_list":
                return executeKnowledgeList(args);
            case "learn":
                return executeLearn(args);
            case "download":
            default:
                return executeDownload(args);
        }
    }

    private static String executeLearn(Map<String, Object> args) {
        String repo = (String) args.get("repo");
        String focus = (String) args.get("focus");
        String project_path = (String) args.get("project_path");

        try {
            return com.boris.knowledge.LearningCompiler.compile(repo, focus, project_path);
        } catch (com.boris.knowledge.LearningException e) {
            return formatOutput(false, e.getMessage());
        }
    }

    private static String executeDownload(Map<String, Object> args) {
        String repo = (String) args.getOrDefault("repo", "");
        String version = (String) args.getOrDefault("version", "main");
        String rawProjectPath = (String) args.getOrDefault("project_path", ".");
        boolean force = Boolean.TRUE.equals(args.get("force"));

        if (repo == null || repo.isBlank() || !repo.contains("/")) {
            return formatOutput(false, "Error: repo is required (format: owner/repo)");
        }

        String projectPath = com.boris.context.ProjectContext.resolveRelativePath(rawProjectPath);
        Path projectRoot = com.boris.knowledge.KnowledgeStorageService.resolveProjectRoot(projectPath);

        // Check learning limits (library context is expensive)
        if (!force && !LearningLimiter.allowLearningCall(repo)) {
            return formatOutput(false,
                "Límite de aprendizaje alcanzado. " + LearningLimiter.getStatus() +
                ". NO intentes descargar más repositorios. Corrige el código con la información que ya tienes.");
        }

        String repoKey = repo.toLowerCase();
        String repoName = repo.split("/")[1];

        // Cache de repos dentro del workspace del proyecto: <project_path>/.boris/repos/<repo>
        Path repoDir = resolveRepoDir(projectRoot, repoName);

        // Rate limit check
        if (!force && PROCESSED_REPOS.contains(repoKey)) {
            int count = SymbolRepository.countByRepo(repoName);
            ToolProgressNotifier.notify("github_repo",
                "Already processed: " + repo + " (" + count + " symbols)");
            return formatOutputWithFiles(true,
                repo + " ya procesado (" + count + " símbolos). "
                + "Si todavía no lo hiciste, corré github_repo(action='learn', repo=\"" + repoName
                + "\", focus=\"<dominio>\") antes de escribir código; read_files es solo para lecturas puntuales.",
                repoName, repoDir);
        }

        // Check if already indexed
        if (!force && (SymbolRepository.isIndexed(repoName) || ContentRepository.isIndexed(repoName))) {
            int count = SymbolRepository.countByRepo(repoName);
            PROCESSED_REPOS.add(repoKey);
            ToolProgressNotifier.notify("github_repo",
                "Cache hit: " + repo + " (" + count + " symbols)");
            return formatOutputWithFiles(true,
                repo + " ya indexado (" + count + " símbolos). "
                + "Si todavía no lo hiciste, corré github_repo(action='learn', repo=\"" + repoName
                + "\", focus=\"<dominio>\") antes de escribir código; read_files es solo para lecturas puntuales.",
                repoName, repoDir);
        }

        try {
            // Download ZIP only if not exists locally
            if (force || !Files.exists(repoDir)) {
                ToolProgressNotifier.notify("github_repo",
                    "Downloading " + repo + " v" + version + " to " + repoDir + "...");
                downloadAndExtractZip(repo, version, repoDir);
                com.boris.tooling.CloneGateService.invalidate(repoName);
            } else {
                ToolProgressNotifier.notify("github_repo",
                    "Using cached repo at " + repoDir + " (no download)");
            }

            ToolProgressNotifier.notify("github_repo",
                "Indexing repo (Java symbols + content of any kind)...");

            ContentRepository.saveRepoPath(repoName, repoDir.toAbsolutePath().toString());

            int count = CodeIndexerService.indexRepository(repoName, repoDir);
            int docCount = ContentIndexerService.indexRepository(repoName, repoDir);

            PROCESSED_REPOS.add(repoKey);
            LearningLimiter.markRepoPendingLearn(repoName);

            ToolProgressNotifier.notify("github_repo",
                "Indexed " + count + " symbols and " + docCount + " content records from " + repo);

            com.boris.knowledge.RepoAnalysisService.RepoAnalysis analysis =
                com.boris.knowledge.RepoAnalysisService.analyzeAndPersist(repoName, repoDir, projectRoot);

            String summary = "Indexado y analizado " + repo + " v" + version + " (" + count + " símbolos, "
                + docCount + " registros, cache en " + repoDir.toAbsolutePath() + "). "
                + "La repo queda registrada como \"" + repoName + "\". "
                + "PASO OBLIGATORIO antes de escribir código: github_repo(action='learn', repo=\"" + repoName
                + "\", focus=\"<dominio>\") para sintetizar patrones. read_files sirve para lecturas puntuales; "
                + "NO copies archivos fuente al proyecto (solo assets con target_dir).";
            return formatOutputWithFilesWithAnalysis(true, summary, repoName, repoDir, analysis);

        } catch (Exception e) {
            // If download fails but already indexed, use that
            if (SymbolRepository.isIndexed(repoName) || ContentRepository.isIndexed(repoName)) {
                int count = SymbolRepository.countByRepo(repoName);
                PROCESSED_REPOS.add(repoKey);
                LearningLimiter.markRepoPendingLearn(repoName);
                return formatOutputWithFiles(true,
                    "Error descargando " + repo + ": " + e.getMessage() +
                    ". Usando índice existente (" + count + " símbolos).",
                    repoName, repoDir);
            }
            return formatOutput(false, "Error: " + e.getMessage());
        }
    }

    private static String executeReadFiles(Map<String, Object> args) {
        String repo = (String) args.get("repo");
        @SuppressWarnings("unchecked")
        List<String> paths = (List<String>) args.get("paths");
        String targetDir = (String) args.get("target_dir");
        @SuppressWarnings("unchecked")
        List<String> targetPaths = (List<String>) args.get("target_paths");

        return com.boris.context.ContentQueryTool.readBatch(repo, paths, targetDir, targetPaths);
    }

    private static String executeSearch(Map<String, Object> args) {
        String query = (String) args.get("query");
        String repo = (String) args.get("repo");
        String path = (String) args.get("path");
        String chunkType = (String) args.get("chunk_type");

        return com.boris.context.ContentQueryTool.search(query, repo, path, chunkType);
    }

    private static String executeSymbols(Map<String, Object> args) {
        String query = (String) args.get("query");
        String type = (String) args.get("type");
        String repo = (String) args.get("repo");

        return com.boris.context.SymbolQueryTool.search(query, type, repo);
    }

    private static String executeMethods(Map<String, Object> args) {
        String className = (String) args.get("class_name");

        return com.boris.context.SymbolQueryTool.findMethods(className);
    }

    private static String executeClasses(Map<String, Object> args) {
        String repo = (String) args.get("repo");

        return com.boris.context.SymbolQueryTool.listClasses(repo);
    }

    private static String executeStatus(Map<String, Object> args) {
        return com.boris.context.SymbolQueryTool.indexStatus();
    }

    private static String executeKnowledge(Map<String, Object> args) {
        String framework = (String) args.get("framework");
        String class_name = (String) args.get("class_name");
        String project_path = (String) args.get("project_path");

        return com.boris.knowledge.FrameworkKnowledgeTool.queryKnowledge(framework, class_name, project_path);
    }

    private static String executeKnowledgeSave(Map<String, Object> args) {
        String framework = (String) args.get("framework");
        String class_name = (String) args.get("class_name");
        String content = (String) args.get("content");
        String project_path = (String) args.get("project_path");

        return com.boris.knowledge.FrameworkKnowledgeTool.saveKnowledge(framework, class_name, content, project_path);
    }

    private static String executeKnowledgeList(Map<String, Object> args) {
        String framework = (String) args.get("framework");
        String project_path = (String) args.get("project_path");

        return com.boris.knowledge.FrameworkKnowledgeTool.listKnowledge(framework, project_path);
    }

    // === REPO CACHE DIRECTORY ===

    private static Path resolveRepoDir(Path projectRoot, String repoName) {
        Path reposDir = projectRoot.resolve(".boris").resolve("repos");
        try {
            com.boris.knowledge.KnowledgeStorageService.ensureGitignore(projectRoot);
            Files.createDirectories(reposDir);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el cache de repos en " + reposDir + ": " + e.getMessage(), e);
        }
        return reposDir.resolve(repoName);
    }

    // === ZIP DOWNLOAD & EXTRACTION ===

    private static void downloadAndExtractZip(String repo, String branch, Path targetDir) throws IOException {
        if (Files.exists(targetDir)) {
            deleteDirectory(targetDir);
        }
        Files.createDirectories(targetDir);

        String refPath = resolveRef(repo, branch);
        ToolProgressNotifier.notify("github_repo", "Resolved ref: " + refPath);

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
        // No longer needed since we use global cache in ~/.boris/repos/
        // The project's .gitignore doesn't need to track .boris/
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

    private static String formatOutputWithFiles(boolean success, String message, String repoName, Path repoDir) {
        return formatOutputWithFilesWithAnalysis(success, message, repoName, repoDir, null);
    }

    private static String formatOutputWithFilesWithAnalysis(boolean success, String message, String repoName,
                                                            Path repoDir,
                                                            com.boris.knowledge.RepoAnalysisService.RepoAnalysis analysis) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            node.put("repo", repoName);
            node.put("cache_path", repoDir.toAbsolutePath().toString());

            var files = ContentRepository.fileManifest(repoName);
            node.put("files_count", files.size());

            ArrayNode filesArray = node.putArray("files");
            for (ContentRepository.FileEntry entry : files) {
                ObjectNode fileNode = filesArray.addObject();
                fileNode.put("path", entry.path());
                fileNode.put("ext", entry.extension());
                fileNode.put("approx_bytes", entry.approxBytes());
            }

            if (analysis != null) {
                node.put("next_action", LearningLimiter.isPendingLearn(repoName) ? "learn" : "search");
                node.put("analysis_summary",
                    "Build: " + analysis.buildSystem() + ", lenguaje: " + analysis.mainLanguage());
                node.put("analysis_examples_count", analysis.exampleFiles().size());
            }

            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"success\":" + success + ",\"message\":\"" + message + "\"}";
        }
    }
}
