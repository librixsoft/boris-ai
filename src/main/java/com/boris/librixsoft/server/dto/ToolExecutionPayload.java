package com.boris.librixsoft.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Payload completo para la ejecución de herramientas nativas.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolExecutionPayload {
    private List<ToolAction> actions;
    private String summary;
}
