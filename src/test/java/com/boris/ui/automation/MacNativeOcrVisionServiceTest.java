package com.boris.ui.automation;

import com.boris.exceptions.OcrExecutionException;
import com.boris.exceptions.VisualElementNotFoundException;
import com.boris.ui.automation.model.BoundingBox;
import com.boris.ui.automation.model.VisualElementTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class MacNativeOcrVisionServiceTest {

    private static byte[] createPngFixture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(100, 100, 200, 50);
        g.dispose();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void findVisualElementFindsTextWithSimulatedOcr() {
        // Simular observaciones OCR: palabra "Buscar" en (240, 80, 140, 36)
        var obs = List.of(
                new MacVisionOcrCommand.OcrObservation("Buscar", 240.0, 80.0, 140.0, 36.0, 0.95));
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(obs);
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);
        VisualElementTarget target = vision.findVisualElement(png, "Buscar");

        assertEquals("Buscar", target.label());
        assertEquals(0.95, target.confidence(), 0.001);
        // Coordenadas CSS: imagen == viewport, sin escala
        assertEquals(240, target.boundingBox().x());
        assertEquals(80, target.boundingBox().y());
        assertEquals(140, target.boundingBox().width());
        assertEquals(36, target.boundingBox().height());
    }

    @Test
    void findVisualElementTextNotFoundThrowsVisualElementNotFoundException() {
        // Simular observaciones OCR sin la palabra buscada
        var obs = List.of(
                new MacVisionOcrCommand.OcrObservation("Hola", 100.0, 100.0, 50.0, 20.0, 0.9),
                new MacVisionOcrCommand.OcrObservation("Mundo", 160.0, 100.0, 60.0, 20.0, 0.85));
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(obs);
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);

        assertThrows(VisualElementNotFoundException.class, () ->
                vision.findVisualElement(png, "Buscar"));
    }

    @Test
    void findVisualElementOcrFailureThrowsOcrExecutionException() {
        // Simular un comando OCR que falla
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(List.of()) {
            @Override
            public List<OcrObservation> execute(String imagePath) {
                throw new OcrExecutionException("Comando OCR no encontrado");
            }
        };
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);

        assertThrows(OcrExecutionException.class, () ->
                vision.findVisualElement(png, "Buscar"));
    }

    @Test
    void findVisualElementScalesCoordinatesWhenImageDiffersFromViewport() {
        // Imagen 2880x1800 (Retina 2x), viewport 1440x900
        // Observación en píxeles de imagen: (480, 160, 280, 72) -> CSS: (240, 80, 140, 36)
        var obs = List.of(
                new MacVisionOcrCommand.OcrObservation("Buscar", 480.0, 160.0, 280.0, 72.0, 0.9));
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(obs);
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(2880, 1800);
        VisualElementTarget target = vision.findVisualElement(png, "Buscar");

        assertEquals(240, target.boundingBox().x());
        assertEquals(80, target.boundingBox().y());
        assertEquals(140, target.boundingBox().width());
        assertEquals(36, target.boundingBox().height());
    }

    @Test
    void findVisualElementJoinsAdjacentWordsIntoPhrase() {
        // Dos palabras adyacentes que forman "Buscar con Google"
        var obs = List.of(
                new MacVisionOcrCommand.OcrObservation("Buscar", 100.0, 100.0, 80.0, 30.0, 0.9),
                new MacVisionOcrCommand.OcrObservation("con", 190.0, 100.0, 40.0, 30.0, 0.85),
                new MacVisionOcrCommand.OcrObservation("Google", 240.0, 100.0, 70.0, 30.0, 0.88));
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(obs);
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);
        VisualElementTarget target = vision.findVisualElement(png, "Buscar con Google");

        assertEquals("Buscar con Google", target.label());
        assertNotNull(target.boundingBox());
    }

    @Test
    void findVisualElementNullScreenshotThrowsException() {
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(List.of());
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        assertThrows(IllegalArgumentException.class, () ->
                vision.findVisualElement(null, "Buscar"));
    }

    @Test
    void findVisualElementEmptyScreenshotThrowsException() {
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(List.of());
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        assertThrows(IllegalArgumentException.class, () ->
                vision.findVisualElement(new byte[0], "Buscar"));
    }

    @Test
    void findVisualElementNullTargetTextThrowsException() {
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(List.of());
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);

        assertThrows(IllegalArgumentException.class, () ->
                vision.findVisualElement(png, null));
    }

    @Test
    void findVisualElementBlankTargetTextThrowsException() {
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(List.of());
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);

        assertThrows(IllegalArgumentException.class, () ->
                vision.findVisualElement(png, "   "));
    }

    @Test
    void findVisualElementInvalidPngThrowsIllegalArgumentException() {
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(List.of());
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        // Bytes que no son un PNG válido
        assertThrows(IllegalArgumentException.class, () ->
                vision.findVisualElement(new byte[]{1, 2, 3, 4}, "Buscar"));
    }

    @Test
    void findVisualElementPrefersHigherConfidenceAmongCandidates() {
        var obs = List.of(
                new MacVisionOcrCommand.OcrObservation("Buscar", 100.0, 100.0, 140.0, 36.0, 0.80),
                new MacVisionOcrCommand.OcrObservation("Buscar", 600.0, 400.0, 140.0, 36.0, 0.95));
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(obs);
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);
        VisualElementTarget target = vision.findVisualElement(png, "Buscar");

        assertEquals(600, target.boundingBox().x());
        assertEquals(400, target.boundingBox().y());
    }

    @Test
    void findVisualElementTieBreakPrefersCandidateCloserToViewportCenter() {
        var obs = List.of(
                new MacVisionOcrCommand.OcrObservation("Buscar", 100.0, 100.0, 140.0, 36.0, 0.90),
                new MacVisionOcrCommand.OcrObservation("Buscar", 650.0, 430.0, 140.0, 36.0, 0.90));
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(obs);
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);
        VisualElementTarget target = vision.findVisualElement(png, "Buscar");

        assertEquals(650, target.boundingBox().x());
        assertEquals(430, target.boundingBox().y());
    }

    @Test
    void findVisualElementWithCandidatesReturnsAllMatchesSortedByRelevance() {
        var obs = List.of(
                new MacVisionOcrCommand.OcrObservation("Buscar", 100.0, 100.0, 140.0, 36.0, 0.70),
                new MacVisionOcrCommand.OcrObservation("Buscar", 600.0, 400.0, 140.0, 36.0, 0.95),
                new MacVisionOcrCommand.OcrObservation("Buscar", 900.0, 600.0, 140.0, 36.0, 0.90));
        MacVisionOcrCommand ocr = new MacVisionOcrCommand(obs);
        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, ocr);

        byte[] png = createPngFixture(1440, 900);
        VisualGroundingResult result = vision.findVisualElementWithCandidates(png, "Buscar");

        assertEquals(3, result.candidates().size());
        assertEquals(0.95, result.bestMatch().confidence(), 0.001);
        assertEquals(result.candidates().get(0), result.bestMatch());
        assertEquals(0.90, result.candidates().get(1).confidence(), 0.001);
        assertEquals(0.70, result.candidates().get(2).confidence(), 0.001);
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void realOcrCommandFindsRenderedTextOnMacOs() {
        BufferedImage img = new BufferedImage(1440, 900, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 1440, 900);
        g.setColor(java.awt.Color.BLACK);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 48));
        g.drawString("BuscarBoris", 200, 120);
        g.dispose();
        byte[] png;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            png = bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MacNativeOcrVisionService vision = new MacNativeOcrVisionService(1440, 900, new MacVisionOcrCommand());
        VisualElementTarget target = vision.findVisualElement(png, "BuscarBoris");

        assertTrue(target.boundingBox().x() >= 150 && target.boundingBox().x() <= 260,
                "x fuera de tolerancia: " + target.boundingBox().x());
        assertTrue(target.boundingBox().y() >= 40 && target.boundingBox().y() <= 130,
                "y fuera de tolerancia: " + target.boundingBox().y());
        assertTrue(target.boundingBox().width() > 100,
                "ancho fuera de tolerancia: " + target.boundingBox().width());
        assertTrue(target.boundingBox().height() > 20,
                "alto fuera de tolerancia: " + target.boundingBox().height());
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void realOcrCommandWithMissingImageThrowsOcrExecutionException() {
        MacVisionOcrCommand command = new MacVisionOcrCommand();

        assertThrows(OcrExecutionException.class, () ->
                command.execute("/tmp/boris_ocr_archivo_inexistente_1234.png"));
    }

    @Test
    @DisabledOnOs(OS.MAC)
    void nonMacOsPlatformThrowsOcrExecutionException() {
        MacVisionOcrCommand command = new MacVisionOcrCommand();

        assertThrows(OcrExecutionException.class, () -> command.execute("/tmp/cualquier.png"));
    }
}
