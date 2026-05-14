package com.boris.librixsoft.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Representa una acción individual de una herramienta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolAction {
    private String tool;
    private Map<String, Object> args;
}
