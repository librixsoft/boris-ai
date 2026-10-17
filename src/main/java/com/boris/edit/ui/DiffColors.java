package com.boris.edit.ui;

import com.boris.edit.DiffLine;
import com.boris.edit.DiffLineType;
import com.googlecode.lanterna.TextColor;

public final class DiffColors {

    private DiffColors() {}

    // Colores para líneas de diff (del UiTheme existente)
    public static final TextColor ADDED = new TextColor.RGB(80, 220, 80);       // Verde brillante
    public static final TextColor REMOVED = new TextColor.RGB(220, 80, 80);     // Rojo brillante
    public static final TextColor MODIFIED = new TextColor.RGB(220, 180, 80);   // Amarillo/Naranja
    public static final TextColor CONTEXT = new TextColor.RGB(175, 145, 75);    // Normal (FG del tema)

    // Fondos para líneas de diff
    public static final TextColor ADDED_BG = new TextColor.RGB(20, 50, 20);     // Verde oscuro
    public static final TextColor REMOVED_BG = new TextColor.RGB(50, 20, 20);   // Rojo oscuro
    public static final TextColor MODIFIED_BG = new TextColor.RGB(50, 40, 20);  // Amarillo oscuro

    // Colores de estado de validación
    public static final TextColor PENDING = new TextColor.RGB(100, 100, 100);   // Gris
    public static final TextColor VALIDATED = new TextColor.RGB(80, 220, 80);   // Verde
    public static final TextColor ERROR = new TextColor.RGB(220, 80, 80);       // Rojo
    public static final TextColor CORRECTED = new TextColor.RGB(80, 180, 220);  // Cyan

    // UI elements
    public static final TextColor LINE_NUMBER = new TextColor.RGB(100, 100, 100);
    public static final TextColor BORDER = new TextColor.RGB(70, 70, 70);
    public static final TextColor HEADER = new TextColor.RGB(130, 180, 220);
    public static final TextColor STATS = new TextColor.RGB(0, 210, 210);

    public static TextColor forLineType(DiffLineType type) {
        return switch (type) {
            case ADDED -> ADDED;
            case REMOVED -> REMOVED;
            case MODIFIED -> MODIFIED;
            case CONTEXT -> CONTEXT;
        };
    }

    public static TextColor backgroundForLineType(DiffLineType type) {
        return switch (type) {
            case ADDED -> ADDED_BG;
            case REMOVED -> REMOVED_BG;
            case MODIFIED -> MODIFIED_BG;
            case CONTEXT -> null;
        };
    }

    public static TextColor forValidationStatus(DiffLine.ValidationStatus status) {
        return switch (status) {
            case PENDING -> PENDING;
            case VALIDATED -> VALIDATED;
            case ERROR -> ERROR;
            case CORRECTED -> CORRECTED;
        };
    }
}
