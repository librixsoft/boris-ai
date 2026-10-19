package com.boris.context;

import com.boris.context.ContentRepository.DocResult;
import com.boris.tooling.ToolProgressNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;

public class ContentQueryTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CONTENT_PER_RECORD = 2000;
    private static final int MAX_FULL_FILE_CONTENT = 20000;
    private static final int MAX_SYMBOLS = 20;

    private static final int MAX_BUNDLE_FILES = 10;
    private static final int MAX_BUNDLE_FILE_CONTENT = 15000;
    private static final int MAX_BUNDLE_TOTAL_CONTENT = 30000;

    private static final int MAX_BATCH_FILES = 10;
    private static final int MAX_BATCH_FILE_CONTENT = 20000;
    private static final int MAX_BATCH_TOTAL_CONTENT = 60000;

    private static final Set<String> BUNDLE_SOURCE_EXTENSIONS = Set.of("html", "htm", "css", "scss", "sass", "less");
    private static final Set<String> BUNDLE_REF_EXTENSIONS = Set.of("css", "scss", "sass", "less", "js", "mjs", "cjs", "ts");

    private static final Pattern HTML_REF_PATTERN = Pattern.compile(
        "(?i)(?:href|src)\\s*=\\s*\"([^\"]*)\"|(?:href|src)\\s*=\\s*'([^']*)'");
    private static final Pattern CSS_REF_PATTERN = Pattern.compile(
        "url\\(\\s*(['\"]?)([^'\")]+)\\1\\s*\\)|@import\\s+(?:url\\(\\s*)?(?:\"([^\"]*)\"|'([^']*)')");

    public static String search(String query, String repo, String path) {
        if ((query == null || query.isBlank()) && (path == null || path.isBlank())) {
            return error("query o path es requerido");
        }

        if (path != null && !path.isBlank()) {
            return readByPath(repo, path);
        }

        ToolProgressNotifier.notify("github_repo", "Buscando en repos indexados: " + query);

        try {
            List<DocResult> docs = ContentRepository.search(query, repo);
            List<CodeSymbol> symbols = SymbolRepository.search(query, null, repo);
            return formatResults(query, docs, symbols);
        } catch (Exception e) {
            return error("Error buscando contenido: " + e.getMessage());
        }
    }

    public static String readBatch(String repo, List<String> paths, String targetDir, List<String> targetPaths) {
        if (repo == null || repo.isBlank()) {
            return error("repo es requerido (nombre registrado con github_repo)");
        }
        if (paths == null || paths.isEmpty()) {
            return error("paths es requerido: lista de archivos del manifiesto que necesitas leer");
        }
        if (targetPaths != null && !targetPaths.isEmpty() && targetPaths.size() != paths.size()) {
            return error("target_paths debe tener la misma cantidad de elementos que paths");
        }

        boolean copyRequested = targetDir != null && !targetDir.isBlank();
        Path cacheDir = null;
        if (copyRequested) {
            String cachePath = ContentRepository.repoPath(repo);
            if (cachePath == null) {
                return error("No hay ruta de cache registrada para '" + repo + "'. Llamá github_repo(repo, project_path) antes de pedir copia.");
            }
            cacheDir = Path.of(cachePath).toAbsolutePath().normalize();
            if (!Files.isDirectory(cacheDir)) {
                return error("El cache del repo no existe en disco: " + cacheDir);
            }
        }

        ToolProgressNotifier.notify("read_repo_files",
            "Leyendo " + Math.min(paths.size(), MAX_BATCH_FILES) + " archivo(s) de " + repo);

        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("repo", repo);

            String cachePath = ContentRepository.repoPath(repo);
            if (cachePath != null) {
                root.put("cache_path", cachePath);
                root.put("cache_path_note",
                    "Estos archivos provienen del cache de github_repo en esa ruta; NO los leas con batch_read_files usando paths del proyecto.");
            } else {
                root.put("cache_path_error",
                    "Sin ruta de cache registrada para '" + repo + "'; el repo no fue descargado con github_repo en esta sesión");
            }

            Path targetBase = copyRequested
                ? Path.of(ProjectContext.resolveRelativePath(targetDir)).toAbsolutePath().normalize()
                : null;

            ArrayNode filesArray = root.putArray("files");
            ArrayNode copiedArray = copyRequested ? root.putArray("copied") : null;
            ArrayNode failedArray = copyRequested ? root.putArray("copy_failed") : null;
            ArrayNode notFoundArray = root.putArray("not_found");

            int[] totalCharsRef = {0};
            int readFiles = 0;
            int copiedCount = 0;

            for (int i = 0; i < paths.size(); i++) {
                String rawPath = paths.get(i) == null ? "" : paths.get(i).strip();
                if (rawPath.isEmpty()) {
                    continue;
                }

                if (readFiles >= MAX_BATCH_FILES) {
                    notFoundArray.addObject().put("path", rawPath).put("reason", "límite de " + MAX_BATCH_FILES + " archivos por llamada alcanzado");
                    continue;
                }

                if (copyRequested) {
                    String targetOverride = targetPaths != null && !targetPaths.isEmpty() ? targetPaths.get(i).strip() : null;
                    copiedCount += copyFromCache(cacheDir, rawPath, targetBase, targetOverride, copiedArray, failedArray);
                }

                List<DocResult> results = ContentRepository.readByPath(repo, rawPath);
                if (results.isEmpty()) {
                    notFoundArray.addObject().put("path", rawPath)
                        .put("reason", "no indexado o path incorrecto (verifica contra el manifiesto)");
                    continue;
                }

                readFiles++;
                readAndAppendContent(results, filesArray, totalCharsRef);
            }

            root.put("files_read", readFiles);
            root.put("total_chars", totalCharsRef[0]);
            if (copyRequested) {
                root.put("copied_count", copiedCount);
                root.put("copy_failed_count", failedArray.size());
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return error("Error leyendo archivos: " + e.getMessage());
        }
    }

    private static void readAndAppendContent(List<DocResult> results, ArrayNode filesArray, int[] totalCharsRef) {
        StringBuilder contentBuilder = new StringBuilder();
        for (DocResult chunk : results) {
            contentBuilder.append(chunk.content());
        }
        String content = contentBuilder.toString();

        int available = Math.min(MAX_BATCH_FILE_CONTENT, MAX_BATCH_TOTAL_CONTENT - totalCharsRef[0]);
        boolean truncated = content.length() > available;
        if (truncated) {
            content = content.substring(0, Math.max(0, available)) + "... [truncado]";
        }

        ObjectNode fileNode = filesArray.addObject();
        fileNode.put("path", results.getFirst().path());
        fileNode.put("content", content);
        fileNode.put("truncated", truncated);
        totalCharsRef[0] += content.length();
    }

    private static int copyFromCache(Path cacheDir, String relativePath, Path targetBase,
                                     String targetRelativeOverride, ArrayNode copiedArray, ArrayNode failedArray) {
        Path source = cacheDir.resolve(relativePath).normalize();
        String targetRelative = targetRelativeOverride != null && !targetRelativeOverride.isBlank()
            ? targetRelativeOverride
            : relativePath;
        try {
            if (!source.startsWith(cacheDir) || !Files.isRegularFile(source)) {
                ObjectNode failedNode = failedArray.addObject();
                failedNode.put("path", relativePath);
                failedNode.put("reason", "no existe en el cache del repo como archivo regular");
                return 0;
            }

            Path target = targetBase.resolve(targetRelative).normalize();
            if (!target.startsWith(targetBase)) {
                ObjectNode failedNode = failedArray.addObject();
                failedNode.put("path", relativePath);
                failedNode.put("reason", "target_path inválido (fuera del directorio base)");
                return 0;
            }

            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            ObjectNode copiedNode = copiedArray.addObject();
            copiedNode.put("source", relativePath);
            copiedNode.put("target", target.toString());
            copiedNode.put("bytes", Files.size(target));
            return 1;
        } catch (IOException e) {
            throw new RuntimeException("Error copiando '" + relativePath + "' desde el cache: " + e.getMessage(), e);
        }
    }

    private static String readByPath(String repo, String path) {
        ToolProgressNotifier.notify("github_repo", "Leyendo archivo: " + path);

        try {
            List<DocResult> results = ContentRepository.readByPath(repo, path);

            if (results.isEmpty()) {
                return formatResults(path, results, List.of());
            }

            String resultRepo = results.getFirst().repo();
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("path", resultRepo + "/" + results.getFirst().path());

            StringBuilder contentBuilder = new StringBuilder();
            for (DocResult chunk : results) {
                contentBuilder.append(chunk.content());
            }
            String fullContent = contentBuilder.toString();
            root.put("content", fullContent.length() > MAX_FULL_FILE_CONTENT
                ? fullContent.substring(0, MAX_FULL_FILE_CONTENT) + "... [truncado]"
                : fullContent);

            root.set("symbols_in_file",
                symbolsArray(com.boris.context.SymbolRepository.findSymbolsByFile(results.getFirst().path())));

            ArrayNode bundledArray = root.putArray("bundled_files");
            int bundledCount = bundleReferencedFiles(resultRepo, results.getFirst().path(), fullContent, bundledArray);
            root.put("bundled_files_count", bundledCount);

            ArrayNode moreArray = root.putArray("repo_files");
            int added = 0;
            for (String related : ContentRepository.listFiles(resultRepo)) {
                if (related.endsWith(path)) {
                    continue;
                }
                relatedFilesNode(moreArray, related);
                added++;
                if (added >= 25) {
                    break;
                }
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return error("Error leyendo archivo: " + e.getMessage());
        }
    }

    private static void relatedFilesNode(ArrayNode array, String related) {
        ObjectNode node = array.addObject();
        node.put("path", related);
        int idx = related.lastIndexOf('/');
        if (idx + 1 > 0 && idx < related.length() - 1) {
            node.put("name", related.substring(idx + 1));
        }
    }

    private static int bundleReferencedFiles(String repo, String sourcePath, String sourceContent, ArrayNode out) {
        String sourceExt = fileExtension(sourcePath);
        if (!BUNDLE_SOURCE_EXTENSIONS.contains(sourceExt)) {
            return 0;
        }

        String baseDir = "";
        int slash = sourcePath.lastIndexOf('/');
        if (slash > 0) {
            baseDir = sourcePath.substring(0, slash);
        }

        Set<String> resolvedRefs = extractRefs(sourceExt, sourceContent, baseDir);
        if (resolvedRefs.isEmpty()) {
            return 0;
        }

        int bundled = 0;
        int totalChars = 0;

        for (String ref : resolvedRefs) {
            if (bundled >= MAX_BUNDLE_FILES || totalChars >= MAX_BUNDLE_TOTAL_CONTENT) {
                break;
            }

            List<DocResult> refResults = ContentRepository.readByPath(repo, ref);
            if (refResults.isEmpty()) {
                continue;
            }

            StringBuilder refContent = new StringBuilder();
            for (DocResult chunk : refResults) {
                refContent.append(chunk.content());
            }

            String refFull = refContent.toString();
            boolean truncated = refFull.length() > MAX_BUNDLE_FILE_CONTENT
                || totalChars + refFull.length() > MAX_BUNDLE_TOTAL_CONTENT;
            if (truncated) {
                int available = Math.max(0, Math.min(MAX_BUNDLE_FILE_CONTENT, MAX_BUNDLE_TOTAL_CONTENT - totalChars));
                refFull = refFull.substring(0, available) + "... [truncado]";
            }

            ObjectNode bundledNode = out.addObject();
            bundledNode.put("path", refResults.getFirst().repo() + "/" + refResults.getFirst().path());
            bundledNode.put("local_path", refResults.getFirst().path());
            bundledNode.put("content", refFull);
            bundledNode.put("truncated", truncated);

            totalChars += refFull.length();
            bundled++;
        }

        return bundled;
    }

    private static LinkedHashSet<String> extractRefs(String sourceExt, String content, String baseDir) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();

        Pattern[] patterns = sourceExt.equals("css") || sourceExt.equals("scss")
            || sourceExt.equals("sass") || sourceExt.equals("less")
            ? new Pattern[]{CSS_REF_PATTERN}
            : new Pattern[]{HTML_REF_PATTERN, CSS_REF_PATTERN};

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String ref = firstNonNull(matcher.group(1), matcher.group(2),
                    matcher.group(3), matcher.group(4));
                String resolved = resolveRef(baseDir, ref);
                if (resolved != null && BUNDLE_REF_EXTENSIONS.contains(fileExtension(resolved))) {
                    refs.add(resolved);
                }
            }
        }

        return refs;
    }

    private static String resolveRef(String baseDir, String rawRef) {
        if (rawRef == null || rawRef.isBlank()) {
            return null;
        }

        String cleaned = rawRef.split("[?#]", 2)[0].replace('\\', '/').strip();
        if (cleaned.isEmpty() || cleaned.equals("#")) {
            return null;
        }

        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")
            || cleaned.startsWith("data:") || cleaned.startsWith("//")) {
            return null;
        }

        Deque<String> parts = new ArrayDeque<>();
        if (cleaned.startsWith("/")) {
            addPathParts(parts, cleaned.substring(1));
        } else if (baseDir == null || baseDir.isEmpty()) {
            addPathParts(parts, cleaned);
        } else {
            addPathParts(parts, baseDir + "/" + cleaned);
        }

        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private static void addPathParts(Deque<String> parts, String path) {
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                parts.pollLast();
                continue;
            }
            parts.addLast(part);
        }
    }

    private static String fileExtension(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static ArrayNode symbolsArray(List<CodeSymbol> symbols) {
        ArrayNode symbolsArray = MAPPER.createArrayNode();
        for (CodeSymbol s : symbols) {
            ObjectNode symNode = symbolsArray.addObject();
            symNode.put("name", s.getName());
            symNode.put("type", s.getType().name());
            symNode.put("signature", s.getSignature());
            symNode.put("in", s.getParent());
            symNode.put("file", s.getFile());
            symNode.put("line", s.getLine());
            symNode.put("repo", s.getRepo());
        }
        return symbolsArray;
    }

    private static String formatResults(String query, List<DocResult> results, List<CodeSymbol> symbols) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("query", query);

            root.set("symbols", symbolsArray(symbols != null ? symbols : List.of()));

            if (results.isEmpty()) {
                root.put("found", 0);
                root.put("message", "Sin resultados. Cambia el termino de busqueda o verifica con index_status() que el repo este indexado.");
            } else {
                root.put("found", results.size());

                ArrayNode recordsArray = root.putArray("records");
                for (int i = 0; i < results.size(); i++) {
                    DocResult record = results.get(i);
                    ObjectNode recordNode = recordsArray.addObject();
                    recordNode.put("repo", record.repo());
                    recordNode.put("path", record.path());
                    recordNode.put("section", record.chunkIndex() == 0 ? "start" : "chunk_" + record.chunkIndex());
                    recordNode.put("title", record.title());

                    String content = record.content();
                    if (content.length() > MAX_CONTENT_PER_RECORD) {
                        content = content.substring(0, MAX_CONTENT_PER_RECORD) + "... [truncado]";
                    }
                    recordNode.put("content_excerpt", content);
                }

                root.put("hint", "Resultado unificado: 'symbols' trae clases/metodos Java y 'records' trae contenido de cualquier tipo. Usa github_repo(action='search', path=\"...\") para leer un archivo completo (incluye symbols_in_file).");
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return error("Error formateando resultados: " + e.getMessage());
        }
    }

    private static String error(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("error", message);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }
}
