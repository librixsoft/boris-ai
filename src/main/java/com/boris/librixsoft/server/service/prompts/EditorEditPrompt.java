package com.boris.librixsoft.server.service.prompts;

import org.springframework.stereotype.Component;

@Component
public class EditorEditPrompt {

    public String build(String path, String contentOrInstructions, String originalContent) {
        return """
                TIPO: EDICIÓN QUIRÚRGICA
                ARCHIVO: %s

                CONTENIDO ACTUAL DEL ARCHIVO:
                %s

                INSTRUCCIONES:
                %s

                TU TRABAJO:
                1. Lee las instrucciones.
                2. Localiza en el CONTENIDO ACTUAL los bloques exactos a modificar.
                3. Ejecuta los reemplazos usando editFileWithReplacement.
                4. Asegúrate de que el JSON resultante sea válido escapando correctamente las comillas y saltos de línea.
                5. NO improvises. NO agregues lógica extra. Solo ejecuta lo pedido.
                """.formatted(path, originalContent, contentOrInstructions);
    }
}
