package com.boris.tooling.tool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas para OfficeDocumentTool.
 * 
 * Demuestra cómo el modelo LLM procesa instrucciones y llama al Tool
 * con parámetros de customización.
 */
public class OfficeDocumentToolTest {

    private static final String TEST_OUTPUT_DIR = "/tmp/boris_office_docs";

    @BeforeEach
    void setup() {
        new File(TEST_OUTPUT_DIR).mkdirs();
    }

    /**
     * Simula: "Crea un documento Word rojo y azul corporativos"
     * El modelo extrae colores y parámetros, el Tool los recibe procesados.
     */
    @Test
    void testCreateWordDocumentWithCorporateColors() {
        Map<String, Object> customization = new HashMap<>();
        customization.put("primaryColor", "CC0000");    // Rojo corporativo
        customization.put("secondaryColor", "0033CC");  // Azul corporativo
        customization.put("accentColor", "666666");     // Gris
        customization.put("textColor", "000000");
        customization.put("fontFamily", "Calibri");
        customization.put("fontSize", 12);

        Map<String, Object> params = new HashMap<>();
        params.put("documentType", "word");
        params.put("outputPath", TEST_OUTPUT_DIR + "/propuesta_comercial.docx");
        params.put("title", "Propuesta Comercial 2026");
        params.put("content", "Servicios Profesionales\n" +
                "Consultoría estratégica\n" +
                "Implementación de soluciones\n" +
                "Soporte y mantenimiento");
        params.put("customization", customization);

        String result = OfficeDocumentTool.execute(params);
        
        assertTrue(result.contains("SUCCESS"));
        assertTrue(new File(TEST_OUTPUT_DIR + "/propuesta_comercial.docx").exists());
    }

    /**
     * Simula: "Crea una presentación PowerPoint con diseño moderno azul y naranja"
     */
    @Test
    void testCreatePowerPointWithModernDesign() {
        Map<String, Object> customization = new HashMap<>();
        customization.put("primaryColor", "1F4788");    // Azul oscuro
        customization.put("secondaryColor", "FF8C42");  // Naranja
        customization.put("accentColor", "CCCCCC");
        customization.put("textColor", "FFFFFF");
        customization.put("fontFamily", "Arial");

        Map<String, Object> params = new HashMap<>();
        params.put("documentType", "powerpoint");
        params.put("outputPath", TEST_OUTPUT_DIR + "/estrategia_digital.pptx");
        params.put("title", "Estrategia Digital 2026");
        params.put("content", "Transformación Digital\n" +
                "Automatización de procesos\n" +
                "Analítica avanzada\n" +
                "Experiencia del cliente");
        params.put("customization", customization);

        String result = OfficeDocumentTool.execute(params);
        
        assertTrue(result.contains("SUCCESS"));
        assertTrue(new File(TEST_OUTPUT_DIR + "/estrategia_digital.pptx").exists());
    }

    /**
     * Simula: "Crea una hoja Excel con datos de ventas, colores azul y gris profesionales"
     */
    @Test
    void testCreateExcelWithSalesData() {
        Map<String, Object> customization = new HashMap<>();
        customization.put("primaryColor", "003366");    // Azul marino
        customization.put("secondaryColor", "FF9900");  // Naranja
        customization.put("accentColor", "999999");
        customization.put("textColor", "000000");
        customization.put("fontFamily", "Calibri");

        Map<String, Object> params = new HashMap<>();
        params.put("documentType", "excel");
        params.put("outputPath", TEST_OUTPUT_DIR + "/ventas_2026.xlsx");
        params.put("title", "Reporte de Ventas Q1 2026");
        // Formato pipe-separated para tablas
        params.put("content", "Producto|Enero|Febrero|Marzo\n" +
                "Producto A|10000|12000|15000\n" +
                "Producto B|8000|9000|11000\n" +
                "Producto C|5000|6000|7000");
        params.put("customization", customization);

        String result = OfficeDocumentTool.execute(params);
        
        assertTrue(result.contains("SUCCESS"));
        assertTrue(new File(TEST_OUTPUT_DIR + "/ventas_2026.xlsx").exists());
    }

    /**
     * Prueba: Manejo de error si falta documentType
     */
    @Test
    void testErrorHandlingMissingDocumentType() {
        Map<String, Object> params = new HashMap<>();
        params.put("outputPath", TEST_OUTPUT_DIR + "/error.docx");
        // Falta documentType

        String result = OfficeDocumentTool.execute(params);
        
        assertTrue(result.contains("ERROR"));
    }

    /**
     * Prueba: Manejo de error si falta outputPath
     */
    @Test
    void testErrorHandlingMissingOutputPath() {
        Map<String, Object> params = new HashMap<>();
        params.put("documentType", "word");
        // Falta outputPath

        String result = OfficeDocumentTool.execute(params);
        
        assertTrue(result.contains("ERROR"));
    }

    /**
     * Prueba: Documentos con customización vacía (usa valores por defecto)
     */
    @Test
    void testCreateDocumentWithDefaultCustomization() {
        Map<String, Object> params = new HashMap<>();
        params.put("documentType", "word");
        params.put("outputPath", TEST_OUTPUT_DIR + "/default_style.docx");
        params.put("title", "Documento con Estilos Predeterminados");
        params.put("content", "Este documento usa colores y estilos predeterminados.");
        params.put("customization", new HashMap<>()); // Vacío, usa defaults

        String result = OfficeDocumentTool.execute(params);
        
        assertTrue(result.contains("SUCCESS"));
        assertTrue(new File(TEST_OUTPUT_DIR + "/default_style.docx").exists());
    }
}
