package com.boris.ui.automation;

import com.boris.exceptions.OcrExecutionException;
import com.boris.exceptions.VisualElementNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Envolve el comando OCR nativo de macOS (BorOcr.swift).
 * Compila el fuente Swift una vez y lo cachea en ~/.boris/tools/BorOcr.
 * Ejecuta el binario mediante ProcessBuilder y parsea la salida JSON.
 */
public class MacVisionOcrCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path TOOLS_DIR = Paths.get(System.getProperty("user.home"), ".boris", "tools");
    private static final Path BINARY_PATH = TOOLS_DIR.resolve("BorOcr");
    private static final Path SWIFT_SOURCE = Paths.get("src", "main", "resources", "BorOcr.swift");

    public MacVisionOcrCommand() {
    }

    /**
     * Constructor para pruebas: devuelve las observaciones pre-computadas sin invocar el binario.
     */
    public MacVisionOcrCommand(List<OcrObservation> simulatedObservations) {
        this.simulatedObservations = simulatedObservations;
    }

    private List<OcrObservation> simulatedObservations;

    /**
     * Ejecuta el OCR sobre la imagen PNG en {@code imagePath} y devuelve las observaciones
     * de texto detectadas con sus coordenadas en píxeles de imagen.
     *
     * @param imagePath ruta del archivo PNG
     * @return lista de observaciones OCR, nunca null
     * @throws OcrExecutionException si el binario falta, falla o devuelve salida inválida
     */
    public List<OcrObservation> execute(String imagePath) {
        if (simulatedObservations != null) {
            return new ArrayList<>(simulatedObservations);
        }

        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("imagePath es requerido");
        }

        Path binary = ensureBinaryCompiled();

        ProcessBuilder pb = new ProcessBuilder(binary.toFile().toString(), imagePath);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String stderr = output.toString();
                throw new OcrExecutionException(
                        "El comando OCR falló con código de salida " + exitCode + ". stderr: " + stderr);
            }

            String json = output.toString().trim();
            if (json.isBlank() || json.equals("[]")) {
                return List.of();
            }

            return parseJson(json);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrExecutionException("Error ejecutando el comando OCR: " + e.getMessage(), e);
        }
    }

    /**
     * Asegura que el binario OCR esté compilado y disponible.
     * Lo compila desde el fuente Swift si no existe o está desactualizado.
     */
    private Path ensureBinaryCompiled() {
        try {
            Files.createDirectories(TOOLS_DIR);
        } catch (IOException e) {
            throw new OcrExecutionException("No se pudo crear el directorio de herramientas: " + TOOLS_DIR, e);
        }

        if (!Files.exists(BINARY_PATH)) {
            compileSwiftSource();
        }

        return BINARY_PATH;
    }

    private void compileSwiftSource() {
        Path source = SWIFT_SOURCE.toAbsolutePath();
        if (!Files.exists(source)) {
            throw new OcrExecutionException("No se encontró el fuente Swift de OCR: " + source);
        }

        ProcessBuilder pb = new ProcessBuilder("swiftc", "-o", BINARY_PATH.toString(), source.toString());

        try {
            Process process = pb.start();
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new OcrExecutionException(
                        "Fallo al compilar el comando OCR Swift (código " + exitCode + "): " + stderr);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrExecutionException("Error compilando el comando OCR Swift: " + e.getMessage(), e);
        }
    }

    private List<OcrObservation> parseJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isArray()) {
                ArrayNode array = (ArrayNode) root;
                List<OcrObservation> observations = new ArrayList<>();
                for (JsonNode node : array) {
                    observations.add(new OcrObservation(
                            node.get("text").asText(),
                            node.get("x").asDouble(),
                            node.get("y").asDouble(),
                            node.get("width").asDouble(),
                            node.get("height").asDouble(),
                            node.get("confidence").asDouble()));
                }
                return observations;
            }
            throw new OcrExecutionException("Salida OCR no es un array JSON: " + json);
        } catch (IOException e) {
            throw new OcrExecutionException("Salida OCR no es JSON válido: " + json, e);
        }
    }

    /**
     * Observación individual de texto detectado por OCR.
     */
    public record OcrObservation(String text, double x, double y, double width, double height, double confidence) {}
}
