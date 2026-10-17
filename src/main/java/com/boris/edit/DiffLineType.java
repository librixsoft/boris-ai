package com.boris.edit;

public enum DiffLineType {
    ADDED,      // Línea nueva (+) - verde
    REMOVED,    // Línea eliminada (-) - rojo
    MODIFIED,   // Línea modificada (~) - amarillo
    CONTEXT     // Línea de contexto sin cambios - normal
}
