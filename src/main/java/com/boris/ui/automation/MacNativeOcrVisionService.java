package com.boris.ui.automation;

import com.boris.exceptions.VisualElementNotFoundException;
import com.boris.ui.automation.model.BoundingBox;
import com.boris.ui.automation.model.ScreenCoordinate;
import com.boris.ui.automation.model.VisualElementTarget;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Resuelve la posición en píxeles de cualquier texto visual en el viewport
 * mediante el motor de visión espacial de macOS (Vision.framework).
 * Implementa OCR real sobre el screenshot y devuelve el bounding box del
 * elemento que coincida con el texto buscado.
 */
public class MacNativeOcrVisionService implements VisionGroundingService {

    private static final int DEFAULT_VIEWPORT_WIDTH = 1440;
    private static final int DEFAULT_VIEWPORT_HEIGHT = 900;
    private static final double LINE_TOLERANCE_RATIO = 0.25;
    private static final double WORD_GAP_RATIO = 0.30;
    private static final String INVALID_PNG_MESSAGE = "La imagen PNG no es válida o no se pudo leer";
    private static final String PROCESSING_ERROR_MESSAGE = "Error procesando el screenshot: ";

    private final int viewportWidth;
    private final int viewportHeight;
    private final MacVisionOcrCommand ocrCommand;

    public MacNativeOcrVisionService() {
        this(DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    public MacNativeOcrVisionService(int viewportWidth, int viewportHeight) {
        this(viewportWidth, viewportHeight, new MacVisionOcrCommand());
    }

    public MacNativeOcrVisionService(int viewportWidth, int viewportHeight, MacVisionOcrCommand ocrCommand) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Dimensiones del viewport deben ser mayores a cero");
        }
        if (ocrCommand == null) {
            throw new IllegalArgumentException("ocrCommand cannot be null");
        }
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.ocrCommand = ocrCommand;
    }

    @Override
    public VisualElementTarget findVisualElement(byte[] screenshotPngBytes, String targetText) {
        return findVisualElementWithCandidates(screenshotPngBytes, targetText).bestMatch();
    }

    /**
     * Busca el texto objetivo en el screenshot y devuelve la mejor coincidencia
     * junto con todas las candidatas ordenadas por relevancia (mayor confianza,
     * luego más cercana al centro del viewport).
     *
     * @param screenshotPngBytes bytes PNG del screenshot del viewport
     * @param targetText texto a localizar visualmente
     * @return resultado extendido con la mejor coincidencia y sus candidatas
     * @throws VisualElementNotFoundException si no hay ninguna coincidencia en el viewport
     * @throws IllegalArgumentException si el screenshot o el texto son inválidos
     */
    public VisualGroundingResult findVisualElementWithCandidates(byte[] screenshotPngBytes, String targetText) {
        validateScreenshot(screenshotPngBytes);
        validateTargetText(targetText);

        OcrFrame frame = runOcr(screenshotPngBytes);

        String targetLower = targetText.trim().toLowerCase();
        double viewportCenterX = viewportWidth / 2.0;
        double viewportCenterY = viewportHeight / 2.0;

        List<Candidate> candidates = new ArrayList<>();
        for (Phrase phrase : frame.phrases()) {
            if (!phrase.text().toLowerCase().contains(targetLower)) {
                continue;
            }
            if (isOutsideImageBounds(phrase, frame)) {
                continue;
            }
            candidates.add(new Candidate(
                    phrase.text(),
                    toBoundingBox(phrase, frame),
                    phrase.confidence(),
                    phrase.centerX(),
                    phrase.centerY()));
        }

        if (candidates.isEmpty()) {
            throw new VisualElementNotFoundException(
                    "No se encontró el elemento visual con texto '" + targetText + "' en el screenshot");
        }

        candidates.sort(Comparator
                .comparingDouble((Candidate c) -> c.confidence()).reversed()
                .thenComparingDouble(c -> c.distanceTo(viewportCenterX, viewportCenterY)));

        List<VisualElementTarget> targets = new ArrayList<>();
        for (Candidate candidate : candidates) {
            ScreenCoordinate targetCoord = candidate.boundingBox().centerWithJitter(4);
            targets.add(new VisualElementTarget(
                    candidate.text(), candidate.boundingBox(), targetCoord, candidate.confidence()));
        }

        return new VisualGroundingResult(targets.get(0), targets);
    }

    /**
     * Devuelve todas las frases de texto detectadas en el screenshot con sus
     * bounding boxes y confianza, para la acción read_screen.
     *
     * @param screenshotPngBytes bytes PNG del screenshot del viewport
     * @return lista de elementos visuales detectados dentro del viewport
     * @throws IllegalArgumentException si el screenshot es inválido
     */
    public List<VisualElementTarget> findAllText(byte[] screenshotPngBytes) {
        validateScreenshot(screenshotPngBytes);

        OcrFrame frame = runOcr(screenshotPngBytes);

        List<VisualElementTarget> targets = new ArrayList<>();
        for (Phrase phrase : frame.phrases()) {
            if (isOutsideImageBounds(phrase, frame)) {
                continue;
            }
            ScreenCoordinate coord = toBoundingBox(phrase, frame).centerWithJitter(2);
            targets.add(new VisualElementTarget(
                    phrase.text(), toBoundingBox(phrase, frame), coord, phrase.confidence()));
        }

        return targets;
    }

    private void validateScreenshot(byte[] screenshotPngBytes) {
        if (screenshotPngBytes == null || screenshotPngBytes.length == 0) {
            throw new IllegalArgumentException("screenshotPngBytes cannot be empty");
        }
    }

    private void validateTargetText(String targetText) {
        if (targetText == null || targetText.isBlank()) {
            throw new IllegalArgumentException("targetText cannot be empty");
        }
    }

    /**
     * Lee el PNG, ejecuta el comando OCR sobre un temporal y agrupa las
     * observaciones en frases por línea.
     */
    private OcrFrame runOcr(byte[] screenshotPngBytes) {
        int pngWidth;
        int pngHeight;
        try (var is = new ByteArrayInputStream(screenshotPngBytes)) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) {
                throw new IllegalArgumentException(INVALID_PNG_MESSAGE);
            }
            pngWidth = img.getWidth();
            pngHeight = img.getHeight();
        } catch (IOException e) {
            throw new IllegalArgumentException(INVALID_PNG_MESSAGE + ": " + e.getMessage(), e);
        }

        double scaleX = (double) pngWidth / viewportWidth;
        double scaleY = (double) pngHeight / viewportHeight;

        File tempFile = null;
        try {
            tempFile = File.createTempFile("boris_ocr_", ".png");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(screenshotPngBytes);
            }

            List<MacVisionOcrCommand.OcrObservation> observations =
                    ocrCommand.execute(tempFile.getAbsolutePath());
            List<Phrase> phrases = groupIntoPhrases(observations);
            return new OcrFrame(pngWidth, pngHeight, scaleX, scaleY, phrases);
        } catch (IOException e) {
            throw new IllegalArgumentException(PROCESSING_ERROR_MESSAGE + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private boolean isOutsideImageBounds(Phrase phrase, OcrFrame frame) {
        return phrase.minX() < 0 || phrase.minY() < 0
                || phrase.maxX() > frame.pngWidth() || phrase.maxY() > frame.pngHeight();
    }

    private BoundingBox toBoundingBox(Phrase phrase, OcrFrame frame) {
        return new BoundingBox(
                (int) Math.round(phrase.minX() / frame.scaleX()),
                (int) Math.round(phrase.minY() / frame.scaleY()),
                (int) Math.round((phrase.maxX() - phrase.minX()) / frame.scaleX()),
                (int) Math.round((phrase.maxY() - phrase.minY()) / frame.scaleY()));
    }

    /**
     * Agrupa observaciones OCR por línea y une palabras adyacentes en frases.
     */
    private List<Phrase> groupIntoPhrases(List<MacVisionOcrCommand.OcrObservation> observations) {
        if (observations.isEmpty()) {
            return List.of();
        }

        List<MacVisionOcrCommand.OcrObservation> sorted = new ArrayList<>(observations);
        sorted.sort(Comparator.comparingDouble(
                (MacVisionOcrCommand.OcrObservation obs) -> obs.y() + obs.height() / 2.0)
                .thenComparingDouble(MacVisionOcrCommand.OcrObservation::x));

        List<Phrase> phrases = new ArrayList<>();
        List<MacVisionOcrCommand.OcrObservation> currentLine = new ArrayList<>();
        double currentLineY = -1;

        for (MacVisionOcrCommand.OcrObservation obs : sorted) {
            double obsCenterY = obs.y() + obs.height() / 2.0;

            if (currentLine.isEmpty()) {
                currentLine.add(obs);
                currentLineY = obsCenterY;
            } else {
                double maxHeight = 0;
                for (MacVisionOcrCommand.OcrObservation o : currentLine) {
                    maxHeight = Math.max(maxHeight, o.height());
                }
                double lineHeight = Math.max(maxHeight, obs.height());
                double tolerance = lineHeight * LINE_TOLERANCE_RATIO;

                if (Math.abs(obsCenterY - currentLineY) <= tolerance) {
                    currentLine.add(obs);
                    currentLineY = (currentLineY + obsCenterY) / 2.0;
                } else {
                    phrases.add(buildPhrase(currentLine));
                    currentLine = new ArrayList<>();
                    currentLine.add(obs);
                    currentLineY = obsCenterY;
                }
            }
        }

        if (!currentLine.isEmpty()) {
            phrases.add(buildPhrase(currentLine));
        }

        return phrases;
    }

    private Phrase buildPhrase(List<MacVisionOcrCommand.OcrObservation> words) {
        if (words.isEmpty()) {
            return null;
        }

        List<MacVisionOcrCommand.OcrObservation> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingDouble(MacVisionOcrCommand.OcrObservation::x));

        double avgWidth = 0;
        for (MacVisionOcrCommand.OcrObservation w : sorted) {
            avgWidth += w.width();
        }
        avgWidth /= sorted.size();
        double maxGap = avgWidth * WORD_GAP_RATIO;

        StringBuilder text = new StringBuilder();
        double minX = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double totalConfidence = 0;
        int count = 0;

        double prevRight = -1;

        for (MacVisionOcrCommand.OcrObservation word : sorted) {
            if (prevRight >= 0 && word.x() - prevRight <= maxGap) {
                text.append(' ');
            } else if (text.length() > 0) {
                text.append(' ');
            }
            text.append(word.text());

            minX = Math.min(minX, word.x());
            maxX = Math.max(maxX, word.x() + word.width());
            minY = Math.min(minY, word.y());
            maxY = Math.max(maxY, word.y() + word.height());
            totalConfidence += word.confidence();
            count++;

            prevRight = word.x() + word.width();
        }

        return new Phrase(
                text.toString(),
                minX, minY, maxX, maxY,
                count > 0 ? totalConfidence / count : 0);
    }

    /**
     * Frase construida por la unión de palabras adyacentes de la misma línea.
     */
    private record Phrase(
            String text,
            double minX,
            double minY,
            double maxX,
            double maxY,
            double confidence) {

        double centerX() {
            return (minX + maxX) / 2;
        }

        double centerY() {
            return (minY + maxY) / 2;
        }
    }

    /**
     * Candidata de coincidencia con el texto buscado, con su posición en píxeles
     * de imagen para el desempate por cercanía al centro del viewport.
     */
    private record Candidate(
            String text,
            BoundingBox boundingBox,
            double confidence,
            double centerX,
            double centerY) {

        double distanceTo(double x, double y) {
            return Math.hypot(centerX - x, centerY - y);
        }
    }

    /**
     * Dimensiones reales del PNG, factores de escala imagen/viewport y las
     * frases OCR en píxeles de imagen.
     */
    private record OcrFrame(
            int pngWidth,
            int pngHeight,
            double scaleX,
            double scaleY,
            List<Phrase> phrases) {}
}
