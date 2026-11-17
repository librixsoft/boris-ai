package com.boris.ui.automation;

import com.boris.ui.automation.model.VisualElementTarget;

import java.util.List;

/**
 * Resultado extendido del grounding visual: la mejor coincidencia y todas
 * las candidatas ordenadas por relevancia (mayor confianza, luego más
 * cercana al centro del viewport).
 */
public record VisualGroundingResult(
        VisualElementTarget bestMatch,
        List<VisualElementTarget> candidates) {

    public VisualGroundingResult {
        if (bestMatch == null) {
            throw new IllegalArgumentException("bestMatch cannot be null");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates cannot be empty");
        }
        candidates = List.copyOf(candidates);
    }
}
