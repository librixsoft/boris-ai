package com.boris.llm.ollama;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OllamaModelService {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private final String baseUrl;
    private volatile List<String> cachedModels;
    private volatile boolean ollamaAvailable;
    private volatile boolean loaded = false;

    public OllamaModelService() {
        this(DEFAULT_BASE_URL);
    }

    public OllamaModelService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.ollamaAvailable = false;
        this.cachedModels = new ArrayList<>();
        // Cargar modelos en background sin bloquear
        loadModelsAsync();
    }

    public boolean isOllamaAvailable() {
        return ollamaAvailable;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<String> getModels() {
        return cachedModels;
    }

    public String getCurrentModel() {
        return null;
    }

    public boolean selectModel(String modelName) {
        if (!ollamaAvailable || modelName == null || modelName.isEmpty()) {
            return false;
        }
        // Verificar si el modelo existe
        if (cachedModels.contains(modelName)) {
            // En una implementación completa, aquí se configuraría el modelo actual
            return true;
        }
        return false;
    }

    private void loadModelsAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                List<String> models = detectOllamaModels();
                ollamaAvailable = !models.isEmpty();
                cachedModels = models;
            } catch (Exception e) {
                ollamaAvailable = false;
                cachedModels = new ArrayList<>();
            } finally {
                loaded = true;
            }
        });
    }

    private List<String> detectOllamaModels() {
        List<String> models = new ArrayList<>();

        // Intentar usar ollama list
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "list");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("NAME")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length > 0) {
                        String modelName = parts[0];
                        // Limpiar tag si existe (ej: "qwen2.5:latest" -> "qwen2.5")
                        if (modelName.contains(":")) {
                            modelName = modelName.substring(0, modelName.indexOf(":"));
                        }
                        models.add(modelName);
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // Si falla ollama list, intentar API REST
            try {
                models = detectModelsViaApi();
            } catch (Exception ex) {
                // Si todo falla, retornar lista vacía
            }
        }

        return models;
    }

    private List<String> detectModelsViaApi() throws Exception {
        List<String> models = new ArrayList<>();
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL(baseUrl + "/api/tags").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString();
            reader.close();

            // Parsear JSON manualmente para evitar dependencias
            int modelsStart = json.indexOf("\"models\":");
            if (modelsStart == -1) return models;
            modelsStart += 9;

            int arrayStart = json.indexOf("[", modelsStart);
            int arrayEnd = json.indexOf("]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return models;

            String arrayContent = json.substring(arrayStart + 1, arrayEnd);
            int pos = 0;
            while ((pos = arrayContent.indexOf("\"name\":", pos)) != -1) {
                pos += 7;
                int nameStart = arrayContent.indexOf("\"", pos) + 1;
                int nameEnd = arrayContent.indexOf("\"", nameStart);
                if (nameStart > 0 && nameEnd > nameStart) {
                    String name = arrayContent.substring(nameStart, nameEnd);
                    // Limpiar tag
                    if (name.contains(":")) {
                        name = name.substring(0, name.indexOf(":"));
                    }
                    models.add(name);
                }
                pos = nameEnd;
            }
        }
        return models;
    }
}
