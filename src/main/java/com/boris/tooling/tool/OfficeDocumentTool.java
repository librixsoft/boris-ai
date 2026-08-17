package com.boris.tooling.tool;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * OfficeDocumentTool v2: Genera documentos Office personalizados con estilos avanzados.
 * 
 * PARÁMETROS SOPORTADOS:
 * 
 * Colores:
 *   - primaryColor, secondaryColor, accentColor, textColor, backgroundColor (hex)
 *   - headerBgColor, footerBgColor
 * 
 * Texto:
 *   - fontFamily, bodyFontSize, headerFontSize, footerFontSize
 *   - boldTitle, italicBody, underlineHeaders
 * 
 * Espaciado:
 *   - marginTop, marginBottom, marginLeft, marginRight (en pixels)
 *   - paddingHeader, paddingContent, paddingFooter
 *   - lineSpacing (1.0, 1.5, 2.0)
 * 
 * Diseño:
 *   - layout: "oneColumn", "twoColumn", "threeColumn", "grid"
 *   - headerStyle: "solid", "gradient", "banner"
 *   - style: "corporate", "modern", "minimal", "colorful"
 * 
 * Bordes y fondos:
 *   - borderStyle: "solid", "dashed", "dotted", "none"
 *   - borderColor (hex)
 *   - borderWidth (1-5)
 *   - shadowEffect (true/false)
 * 
 * Tablas:
 *   - tableHeaderBg, tableRowBg, tableAlternateRowBg
 *   - tableBorderColor, tableBorderStyle
 */
public class OfficeDocumentTool {

    public static String execute(Map<String, Object> params) {
        try {
            String documentType = (String) params.get("documentType");
            String outputPath = (String) params.get("outputPath");
            String title = (String) params.getOrDefault("title", "Document");
            String content = (String) params.getOrDefault("content", "");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> customization = (Map<String, Object>) params.getOrDefault("customization", new HashMap<>());

            if (documentType == null || outputPath == null) {
                return "ERROR: documentType y outputPath son parámetros requeridos";
            }

            String result = switch (documentType.toLowerCase()) {
                case "word" -> createWordDocument(outputPath, title, content, customization);
                case "powerpoint" -> createPowerPointDocument(outputPath, title, content, customization);
                case "excel" -> createExcelDocument(outputPath, title, content, customization);
                default -> "ERROR: documentType debe ser 'word', 'powerpoint' o 'excel'";
            };

            return result;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ============ WORD (.docx) ============

    private static String createWordDocument(String outputPath, String title, String content, Map<String, Object> customization) throws IOException {
        XWPFDocument document = new XWPFDocument();
        DesignConfig design = new DesignConfig(customization);

        // Header con estilo
        addWordHeader(document, title, design);

        // Contenido según layout
        String layout = (String) customization.getOrDefault("layout", "oneColumn");
        
        if ("twoColumn".equals(layout)) {
            addTwoColumnContent(document, content, design);
        } else if ("threeColumn".equals(layout)) {
            addThreeColumnContent(document, content, design);
        } else if ("grid".equals(layout)) {
            addGridContent(document, content, design);
        } else {
            addOneColumnContent(document, content, design);
        }

        // Footer
        addWordFooter(document, design);

        Path path = Paths.get(outputPath);
        path.getParent().toFile().mkdirs();

        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            document.write(out);
        }
        document.close();

        return "SUCCESS: Documento Word creado en " + outputPath + " con layout " + layout;
    }

    private static void addWordHeader(XWPFDocument document, String title, DesignConfig design) {
        // Header con fondo de color
        XWPFParagraph headerPara = document.createParagraph();
        headerPara.setAlignment(ParagraphAlignment.CENTER);
        headerPara.setSpacingBefore(design.marginTop);
        headerPara.setSpacingAfter(design.marginBottom);

        // Aplicar color de fondo
        headerPara.setStyle("Heading1");
        XWPFRun headerRun = headerPara.createRun();
        headerRun.setText(title);
        headerRun.setFontSize(design.headerFontSize);
        headerRun.setBold(design.boldTitle);
        headerRun.setColor(design.primaryColor);
        headerRun.setFontFamily(design.fontFamily);

        if (design.shadowEffect) {
            headerRun.setShadow(true);
        }
    }

    private static void addOneColumnContent(XWPFDocument document, String content, DesignConfig design) {
        String[] paragraphs = content.split("\n");
        for (String para : paragraphs) {
            if (!para.trim().isEmpty()) {
                addStyledParagraph(document, para, design);
            }
        }
    }

    private static void addTwoColumnContent(XWPFDocument document, String content, DesignConfig design) {
        // Tabla invisible para layout de 2 columnas
        XWPFTable table = document.createTable(1, 2);
        table.setWidth(9144);
        
        String[] parts = content.split("---"); // Separador de columnas
        
        XWPFTableRow row = table.getRow(0);
        
        if (parts.length >= 1) {
            XWPFTableCell cell1 = row.getCell(0);
            cell1.setColor(design.backgroundColor);
            String[] lines1 = parts[0].split("\n");
            for (String line : lines1) {
                if (!line.trim().isEmpty()) {
                    XWPFParagraph p = cell1.addParagraph();
                    XWPFRun r = p.createRun();
                    r.setText(line);
                    r.setFontSize(design.bodyFontSize);
                    r.setColor(design.textColor);
                }
            }
        }
        
        if (parts.length >= 2) {
            XWPFTableCell cell2 = row.getCell(1);
            cell2.setColor(design.secondaryBackgroundColor);
            String[] lines2 = parts[1].split("\n");
            for (String line : lines2) {
                if (!line.trim().isEmpty()) {
                    XWPFParagraph p = cell2.addParagraph();
                    XWPFRun r = p.createRun();
                    r.setText(line);
                    r.setFontSize(design.bodyFontSize);
                    r.setColor(design.textColor);
                }
            }
        }
    }

    private static void addThreeColumnContent(XWPFDocument document, String content, DesignConfig design) {
        // Tabla de 3 columnas
        XWPFTable table = document.createTable(1, 3);
        table.setWidth(9144);
        
        String[] parts = content.split("---");
        XWPFTableRow row = table.getRow(0);
        
        String[] colors = {design.primaryColor, design.secondaryColor, design.accentColor};
        
        for (int i = 0; i < 3; i++) {
            XWPFTableCell cell = row.getCell(i);
            cell.setColor(colors[i]);
            
            if (i < parts.length && !parts[i].trim().isEmpty()) {
                String[] lines = parts[i].split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        XWPFParagraph p = cell.addParagraph();
                        XWPFRun r = p.createRun();
                        r.setText(line);
                        r.setFontSize(design.bodyFontSize);
                        r.setColor("FFFFFF");
                        r.setBold(true);
                    }
                }
            }
        }
    }

    private static void addGridContent(XWPFDocument document, String content, DesignConfig design) {
        // Tabla 2x2 para grid layout
        XWPFTable table = document.createTable(2, 2);
        table.setWidth(9144);
        
        String[] items = content.split("\\|");
        int idx = 0;
        
        for (int row = 0; row < 2 && idx < items.length; row++) {
            for (int col = 0; col < 2 && idx < items.length; col++) {
                XWPFTableCell cell = table.getRow(row).getCell(col);
                cell.setColor(idx % 2 == 0 ? design.primaryColor : design.secondaryColor);
                
                XWPFParagraph p = cell.getParagraphs().get(0);
                XWPFRun r = p.createRun();
                r.setText(items[idx].trim());
                r.setFontSize(design.bodyFontSize);
                r.setColor("FFFFFF");
                r.setBold(true);
                
                idx++;
            }
        }
    }

    private static void addStyledParagraph(XWPFDocument document, String text, DesignConfig design) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingBefore(design.marginLeft);
        p.setSpacingAfter(design.marginRight);
        p.setAlignment(ParagraphAlignment.LEFT);
        
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(design.bodyFontSize);
        run.setColor(design.textColor);
        run.setFontFamily(design.fontFamily);
        
        // Aplicar estilos
        if (design.italicBody) run.setItalic(true);
        if (design.underlineHeaders) run.setUnderline(UnderlinePatterns.SINGLE);
        if (design.shadowEffect) run.setShadow(true);
    }

    private static void addWordFooter(XWPFDocument document, DesignConfig design) {
        XWPFParagraph footer = document.createParagraph();
        footer.setAlignment(ParagraphAlignment.CENTER);
        footer.setSpacingBefore(400);
        XWPFRun footerRun = footer.createRun();
        footerRun.setText("—");
        footerRun.setColor(design.accentColor);
        footerRun.setFontSize(11);
    }

    // ============ POWERPOINT (.pptx) ============

    private static String createPowerPointDocument(String outputPath, String title, String content, Map<String, Object> customization) throws IOException {
        XMLSlideShow prs = new XMLSlideShow();
        DesignConfig design = new DesignConfig(customization);

        // Slide 1: Portada
        XSLFSlide slide1 = prs.createSlide();
        XSLFTextBox titleBox = slide1.createTextBox();
        titleBox.setAnchor(new java.awt.Rectangle(50, 250, 860, 200));
        
        XSLFTextParagraph titlePara = titleBox.addNewTextParagraph();
        titlePara.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        XSLFTextRun titleRun = titlePara.addNewTextRun();
        titleRun.setText(title);
        titleRun.setFontSize((double) design.headerFontSize);
        titleRun.setBold(true);
        java.awt.Color primaryCol = new java.awt.Color(
            Integer.parseInt(design.primaryColor.substring(0, 2), 16),
            Integer.parseInt(design.primaryColor.substring(2, 4), 16),
            Integer.parseInt(design.primaryColor.substring(4, 6), 16)
        );
        titleRun.setFontColor(primaryCol);
        titleRun.setFontFamily(design.fontFamily);

        // Slide 2: Contenido
        XSLFSlide slide2 = prs.createSlide();
        XSLFTextBox subtitleBox = slide2.createTextBox();
        subtitleBox.setAnchor(new java.awt.Rectangle(50, 30, 860, 60));
        
        XSLFTextParagraph subtitlePara = subtitleBox.addNewTextParagraph();
        XSLFTextRun subtitleRun = subtitlePara.addNewTextRun();
        subtitleRun.setText("Contenido");
        subtitleRun.setFontSize((double) design.headerFontSize);
        subtitleRun.setBold(true);
        java.awt.Color secondaryCol = new java.awt.Color(
            Integer.parseInt(design.secondaryColor.substring(0, 2), 16),
            Integer.parseInt(design.secondaryColor.substring(2, 4), 16),
            Integer.parseInt(design.secondaryColor.substring(4, 6), 16)
        );
        subtitleRun.setFontColor(secondaryCol);

        // Contenido
        XSLFTextBox contentBox = slide2.createTextBox();
        contentBox.setAnchor(new java.awt.Rectangle(50, 120, 860, 550));

        String[] lines = content.split("\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                XSLFTextParagraph para = contentBox.addNewTextParagraph();
                XSLFTextRun run = para.addNewTextRun();
                run.setText("• " + line.trim());
                run.setFontSize((double) design.bodyFontSize);
                java.awt.Color textCol = new java.awt.Color(
                    Integer.parseInt(design.textColor.substring(0, 2), 16),
                    Integer.parseInt(design.textColor.substring(2, 4), 16),
                    Integer.parseInt(design.textColor.substring(4, 6), 16)
                );
                run.setFontColor(textCol);
                if (design.italicBody) run.setItalic(true);
            }
        }

        Path path = Paths.get(outputPath);
        path.getParent().toFile().mkdirs();

        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            prs.write(out);
        }
        prs.close();

        return "SUCCESS: Presentación PowerPoint creada en " + outputPath + " con estilo " + design.style;
    }

    // ============ EXCEL (.xlsx) ============

    private static String createExcelDocument(String outputPath, String title, String content, Map<String, Object> customization) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Sheet1");
        DesignConfig design = new DesignConfig(customization);

        // Estilo del header
        XSSFCellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(hexToXSSFColor(design.primaryColor));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBottomBorderColor(hexToXSSFColor(design.borderColor));

        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(design.boldTitle);
        headerFont.setFontHeight(design.headerFontSize * 20);
        headerFont.setColor(new XSSFColor(new byte[]{-1, -1, -1}, null)); // blanco
        headerStyle.setFont(headerFont);

        // Estilo para filas normales
        XSSFCellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setAlignment(HorizontalAlignment.LEFT);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBottomBorderColor(hexToXSSFColor(design.tableBorderColor));
        dataStyle.setFillForegroundColor(hexToXSSFColor(design.backgroundColor));
        dataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFFont dataFont = workbook.createFont();
        dataFont.setFontHeight(design.bodyFontSize * 20);
        dataStyle.setFont(dataFont);

        // Estilo para filas alternas (zebra)
        XSSFCellStyle alternateStyle = workbook.createCellStyle();
        alternateStyle.cloneStyleFrom(dataStyle);
        alternateStyle.setFillForegroundColor(hexToXSSFColor(design.tableAlternateRowBg));
        alternateStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Título
        XSSFRow titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(design.marginTop);
        XSSFCell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(headerStyle);

        // Merge título
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 4));

        // Contenido de tabla
        String[] rows = content.split("\n");
        int rowNum = 2;
        
        for (String row : rows) {
            if (!row.trim().isEmpty()) {
                XSSFRow dataRow = sheet.createRow(rowNum);
                String[] cells = row.split("\\|");
                
                for (int i = 0; i < cells.length; i++) {
                    XSSFCell cell = dataRow.createCell(i);
                    cell.setCellValue(cells[i].trim());
                    
                    // Alternar colores de fila
                    XSSFCellStyle currentStyle = (rowNum % 2 == 0) ? dataStyle : alternateStyle;
                    cell.setCellStyle(currentStyle);
                }
                rowNum++;
            }
        }

        // Auto-ajustar ancho de columnas
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }

        Path path = Paths.get(outputPath);
        path.getParent().toFile().mkdirs();

        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            workbook.write(out);
        }
        workbook.close();

        return "SUCCESS: Hoja de cálculo Excel creada en " + outputPath + " con tabla estilizada";
    }

    // ============ UTILIDADES ============

    private static class DesignConfig {
        // Colores
        String primaryColor;
        String secondaryColor;
        String accentColor;
        String textColor;
        String backgroundColor;
        String secondaryBackgroundColor;
        String headerBgColor;
        String footerBgColor;
        String borderColor;
        String tableBorderColor;
        String tableHeaderBg;
        String tableRowBg;
        String tableAlternateRowBg;

        // Texto
        String fontFamily;
        int headerFontSize;
        int bodyFontSize;
        int footerFontSize;
        boolean boldTitle;
        boolean italicBody;
        boolean underlineHeaders;

        // Espaciado
        int marginTop;
        int marginBottom;
        int marginLeft;
        int marginRight;
        int paddingHeader;
        int paddingContent;
        int paddingFooter;
        double lineSpacing;

        // Diseño
        String layout;
        String style;
        String headerStyle;
        String borderStyle;
        int borderWidth;
        boolean shadowEffect;

        DesignConfig(Map<String, Object> customization) {
            // Colores (defaults corporativos)
            this.primaryColor = (String) customization.getOrDefault("primaryColor", "4472C4");
            this.secondaryColor = (String) customization.getOrDefault("secondaryColor", "ED7D31");
            this.accentColor = (String) customization.getOrDefault("accentColor", "A5A5A5");
            this.textColor = (String) customization.getOrDefault("textColor", "000000");
            this.backgroundColor = (String) customization.getOrDefault("backgroundColor", "FFFFFF");
            this.secondaryBackgroundColor = (String) customization.getOrDefault("secondaryBackgroundColor", "F2F2F2");
            this.headerBgColor = (String) customization.getOrDefault("headerBgColor", this.primaryColor);
            this.footerBgColor = (String) customization.getOrDefault("footerBgColor", this.primaryColor);
            this.borderColor = (String) customization.getOrDefault("borderColor", this.accentColor);
            this.tableBorderColor = (String) customization.getOrDefault("tableBorderColor", "CCCCCC");
            this.tableHeaderBg = (String) customization.getOrDefault("tableHeaderBg", this.primaryColor);
            this.tableRowBg = (String) customization.getOrDefault("tableRowBg", "FFFFFF");
            this.tableAlternateRowBg = (String) customization.getOrDefault("tableAlternateRowBg", "F5F5F5");

            // Texto
            this.fontFamily = (String) customization.getOrDefault("fontFamily", "Calibri");
            this.headerFontSize = ((Number) customization.getOrDefault("headerFontSize", 28)).intValue();
            this.bodyFontSize = ((Number) customization.getOrDefault("bodyFontSize", 12)).intValue();
            this.footerFontSize = ((Number) customization.getOrDefault("footerFontSize", 10)).intValue();
            this.boldTitle = (Boolean) customization.getOrDefault("boldTitle", true);
            this.italicBody = (Boolean) customization.getOrDefault("italicBody", false);
            this.underlineHeaders = (Boolean) customization.getOrDefault("underlineHeaders", false);

            // Espaciado
            this.marginTop = ((Number) customization.getOrDefault("marginTop", 100)).intValue();
            this.marginBottom = ((Number) customization.getOrDefault("marginBottom", 100)).intValue();
            this.marginLeft = ((Number) customization.getOrDefault("marginLeft", 80)).intValue();
            this.marginRight = ((Number) customization.getOrDefault("marginRight", 80)).intValue();
            this.paddingHeader = ((Number) customization.getOrDefault("paddingHeader", 20)).intValue();
            this.paddingContent = ((Number) customization.getOrDefault("paddingContent", 15)).intValue();
            this.paddingFooter = ((Number) customization.getOrDefault("paddingFooter", 15)).intValue();
            this.lineSpacing = ((Number) customization.getOrDefault("lineSpacing", 1.5)).doubleValue();

            // Diseño
            this.layout = (String) customization.getOrDefault("layout", "oneColumn");
            this.style = (String) customization.getOrDefault("style", "corporate");
            this.headerStyle = (String) customization.getOrDefault("headerStyle", "solid");
            this.borderStyle = (String) customization.getOrDefault("borderStyle", "solid");
            this.borderWidth = ((Number) customization.getOrDefault("borderWidth", 1)).intValue();
            this.shadowEffect = (Boolean) customization.getOrDefault("shadowEffect", false);
        }
    }

    private static byte[] hexToRGB(String hex) {
        if (hex.length() != 6) {
            hex = "000000";
        }
        byte[] rgb = new byte[3];
        rgb[0] = (byte) Integer.parseInt(hex.substring(0, 2), 16);
        rgb[1] = (byte) Integer.parseInt(hex.substring(2, 4), 16);
        rgb[2] = (byte) Integer.parseInt(hex.substring(4, 6), 16);
        return rgb;
    }

    private static XSSFColor hexToXSSFColor(String hex) {
        return new XSSFColor(hexToRGB(hex), null);
    }
}
