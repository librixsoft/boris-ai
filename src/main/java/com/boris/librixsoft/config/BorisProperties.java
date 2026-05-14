package com.boris.librixsoft.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración central de la aplicación Boris.
 * Mapea las propiedades con prefijo 'boris' desde los archivos de configuración (application.yml).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "boris")
public class BorisProperties {

    /** Propiedad: boris.llama-server-path. Ruta al ejecutable del motor llama-server. */
    private String llamaServerPath;

    /** Propiedad: boris.llama-server-core. Versión interna o núcleo del llama-server. */
    private String llamaServerCore;

    /** Propiedad: boris.models-dir. Directorio que contiene los archivos .gguf */
    private String modelsDir;

    /** Propiedad: boris.workspace-prefix. Prefijo para las rutas de archivos generados. */
    private String workspacePrefix = "C:\\Users\\lastprophet\\Downloads";

    /** Propiedad: boris.models-max. Máximo de modelos cargados en memoria simultáneamente */
    private int modelsMax = 4;

    /** Propiedad: boris.preload-models. Lista de modelos (ej. con .gguf) a precargar en memoria al arrancar. */
    private List<ModelConfig> preloadModels = new ArrayList<>();

    @Data
    public static class ModelConfig {
        private String id;
        private String name;
        private Integer contextSize;
        private Integer threads;
        private Integer gpuLayers;
        private Integer batchSize;
        private Double temperature;
        private Integer maxTokens;
        private Integer parallel;
    }

    /** Propiedad: boris.context-size. Tamaño del contexto (tokens) para la inferencia. */
    private int contextSize;

    /** Propiedad: boris.port. Puerto en el que correrá el proceso nativo llama-server. */
    private int port;

    /** Propiedad: boris.threads. Número de hilos de CPU que usará el motor. */
    private int threads;

    /** Propiedad: boris.version. Versión actual de la aplicación Boris. */
    private String version;

    /** Propiedad: boris.logo-color. Color predominante para el logo ANSI en la consola. */
    private String logoColor;

    /** Objeto: boris.inference. Configuración de conexión hacia el motor de inferencia. */
    private Inference inference = new Inference();

    /** Objeto: boris.app. Configuración de identidad y red de la aplicación pública. */
    private App app = new App();

    /** Objeto: boris.api.paths. Definición de rutas de API para ambos servicios. */
    private Api api = new Api();

    /** Propiedad: server.port. Puerto real en el que corre este servidor Spring Boot. */
    @Value("${server.port}")
    private int serverPort;


    /** @return URL base para conectar con el motor llama-server (Backend). */
    public String getInferenceBaseUrl() {
        return String.format("%s://%s:%d", inference.getScheme(), inference.getHost(), port);
    }

    /** @return URL completa del endpoint de salud/modelos del motor nativo. */
    public String getInferenceModelsUrl() {
        return getInferenceBaseUrl() + api.getPaths().getLlamaServer().getModels();
    }

    /** @return URL base pública de esta aplicación (Frontend/API Wrapper). */
    public String getAppBaseUrl() {
        return String.format("%s://%s:%d", app.getScheme(), app.getHost(), serverPort);
    }

    /** @return Ruta relativa del endpoint de Chat Completions del wrapper. */
    public String getChatCompletionsPath() {
        var boris = api.getPaths().getBorisServer();
        return boris.getV1() + boris.getChatCompletions();
    }

    /** @return URL absoluta para peticiones de chat al wrapper. */
    public String getAppChatCompletionsUrl() {
        return getAppBaseUrl() + getChatCompletionsPath();
    }

    /** @return URL absoluta del endpoint de estado (JSON). */
    public String getAppStatusUrl() {
        return getAppBaseUrl() + api.getPaths().getBorisServer().getStatus();
    }

    /** @return URL absoluta de la interfaz web (UI). */
    public String getAppUiUrl() {
        return getAppBaseUrl() + api.getPaths().getBorisServer().getUi();
    }

    /** Configuración de red para el motor de Inferencia (boris.inference). */
    @Data
    public static class Inference {
        /** Propiedad: boris.inference.scheme. */
        private String scheme;
        /** Propiedad: boris.inference.host. */
        private String host;
    }

    /** Configuración de red para la Aplicación/UI (boris.app). */
    @Data
    public static class App {
        /** Propiedad: boris.app.scheme. */
        private String scheme;
        /** Propiedad: boris.app.host. */
        private String host;
    }

    @Data
    public static class Api {
        private ApiPaths paths = new ApiPaths();
    }

    @Data
    public static class ApiPaths {
        private Server llamaServer = new Server();
        private Wrapper borisServer = new Wrapper();
    }

    /** Rutas nativas del proceso llama-server (boris.api.paths.llama-server). */
    @Data
    public static class Server {
        /** Propiedad: boris.api.paths.llama-server.v1. */
        private String v1;
        /** Propiedad: boris.api.paths.llama-server.models. */
        private String models;
    }

    /** Rutas personalizadas del wrapper Boris (boris.api.paths.boris-server). */
    @Data
    public static class Wrapper {
        /** Propiedad: boris.api.paths.boris-server.v1. */
        private String v1;
        /** Propiedad: boris.api.paths.boris-server.chat-completions. */
        private String chatCompletions;
        /** Propiedad: boris.api.paths.boris-server.status. */
        private String status;
        /** Propiedad: boris.api.paths.boris-server.ui. */
        private String ui;
    }
}


