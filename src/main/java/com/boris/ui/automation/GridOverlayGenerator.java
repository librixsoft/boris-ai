package com.boris.ui.automation;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import com.boris.ui.automation.model.BoundingBox;

/**
 * Genera una superposición de grilla Set-of-Marks sobre el screenshot para
 * ayudar a localizar elementos visuales mediante coordenadas (x, y).
 */
public class GridOverlayGenerator {

    private GridOverlayGenerator() {}

    /**
     * Dibuja una grícula de marcadores numerados sobre la imagen del viewport.
     * Cada celda de la grícula representa una región potencial de interacción.
     *
     * @param image      imagen del viewport (screenshot)
     * @param rows       número de filas de la grícula
     * @param cols       número de columnas de la grícula
     * @return imagen con la grícula superpuesta
     */
    public static BufferedImage overlayGrid(BufferedImage image, int rows, int cols) {
        if (image == null) {
            throw new IllegalArgumentException("image cannot be null");
        }
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("rows and cols must be greater than zero");
        }

        BufferedImage overlay = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = overlay.createGraphics();
        g2d.drawImage(image, 0, 0, null);

        int cellWidth = image.getWidth() / cols;
        int cellHeight = image.getHeight() / rows;

        g2d.setColor(java.awt.Color.RED);
        g2d.setStroke(new java.awt.BasicStroke(2));

        int marker = 1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = c * cellWidth;
                int y = r * cellHeight;
                g2d.drawRect(x, y, cellWidth, cellHeight);
                g2d.drawString(String.valueOf(marker), x + 5, y + 20);
                marker++;
            }
        }

        g2d.dispose();
        return overlay;
    }

    /**
     * Calcula el centro de una celda de la grícula dada su fila y columna.
     */
    public static BoundingBox cellBoundingBox(int rows, int cols, int row, int col, int viewportWidth, int viewportHeight) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("rows and cols must be greater than zero");
        }
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IllegalArgumentException("row or col out of grid bounds");
        }

        int cellWidth = viewportWidth / cols;
        int cellHeight = viewportHeight / rows;
        int x = col * cellWidth;
        int y = row * cellHeight;
        return new BoundingBox(x, y, cellWidth, cellHeight);
    }
}
