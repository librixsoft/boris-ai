package com.boris.librixsoft.agent.tools;

import org.springframework.stereotype.Component;

@Component
public class CodeFormatter {

    /**
     * Formatea el código según el tipo de archivo.
     * Actualmente no realiza formateo manual - delega a herramientas externas.
     */
    public String format(String content, String filePath) {
        // Sin formateo manual - retorna contenido original
        return content;
    }
}
