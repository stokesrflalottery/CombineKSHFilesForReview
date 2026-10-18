package com.fllottery.dbresearch.diagram;

import com.fllottery.dbresearch.doc.GenerateTechnicalWordDocs;
import com.fllottery.dbresearch.model.*;

import org.apache.poi.xwpf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Enterprise Ingestion Lineage & ELT Pipeline Diagram Engine for Oracle External Tables.
 * <p>
 * External tables (ORGANIZATION EXTERNAL) in the Florida Lottery ecosystem do not participate
 * in physical referential foreign keys. Instead, they act as high-throughput bridges connecting
 * inbound flat files (from OCI Linux directories or SFTP) into database staging and core ledgers.
 * </p>
 * <p>
 * This helper produces:
 *  1. Mermaid Ingestion Pipeline Diagrams (graph LR) with Plum & Alabaster theme.
 *  2. 7.0x Ultra-Retina Supersampled Java2D Fallback Vector Visuals (700+ DPI).
 *  3. Ingestion Architecture documentation and Legend callouts for Word Technical Specifications.
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery DataOps Architecture
 * @since 2026-10-11
 */
public class ExternalTablePipelineDiagramHelper {

    // Florida Lottery Enterprise Palette
    public static final Color COLOR_NAVY = new Color(0, 43, 73);         // #002B49
    public static final Color COLOR_PLUM = new Color(92, 29, 72);        // #5C1D48
    public static final Color COLOR_PLUM_DARK = new Color(59, 16, 46);   // #3B102E
    public static final Color COLOR_BLUE = new Color(0, 90, 156);        // #005A9C
    public static final Color COLOR_GOLD = new Color(218, 165, 32);      // #DAA520
    public static final Color COLOR_GREEN = new Color(34, 139, 34);      // #228B22
    public static final Color COLOR_SLATE = new Color(71, 85, 105);      // #475569
    public static final Color COLOR_BORDER = new Color(226, 232, 240);    // #E2E8F0
    public static final Color COLOR_AMBER = new Color(180, 83, 9);       // #B45309

    /**
     * Determines whether a table represents an external flat-file table, staging log, or ingestion bridge.
     */
    public static boolean isExternalOrStagingTable(GenerateTechnicalWordDocs.TableDocModel tbl) {
        if (tbl == null) {
            boolean result = false;
            return result;
        }
        if (tbl.isExternal) {
            boolean result = true;
            return result;
        }
        if (tbl.tableType != null && tbl.tableType.toUpperCase().contains("EXTERNAL")) {
            boolean result = true;
            return result;
        }
        if (tbl.tableName != null) {
            String upper = tbl.tableName.toUpperCase();
            if (upper.startsWith("EXT_") || upper.endsWith("_EXT") || upper.endsWith("_HDR_EXT")
                    || upper.endsWith("_TRAILER_EXT") || upper.contains("_EXT_")) {
                boolean result = true;
                return result;
            }
            if (upper.contains("_LOG_") && (upper.matches(".*_\\d{8}$") || upper.contains("BANK_RTLR"))) {
                boolean result = true;
                return result;
            }
        }
        if (tbl.externalLocations != null && !tbl.externalLocations.isEmpty()) {
            boolean result = true;
            return result;
        }
        if (tbl.defaultDirectory != null && !tbl.defaultDirectory.trim().isEmpty()) {
            boolean result = true;
            return result;
        }
        boolean result = false;
        return result;
    }

    /**
     * Renders the complete External Table Ingestion Pipeline section into a Word specification document.
     */
    public static void renderExternalTablePipelineDiagram(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel extTable,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        String schema = (extTable.schema != null && !extTable.schema.isEmpty()) ? extTable.schema.toUpperCase() : "ARS";
        String tableName = extTable.tableName.toUpperCase();

        addCallout(doc, "EXTERNAL TABLE INGESTION & ELT PIPELINE ARCHITECTURE:",
                "Renders " + schema + "." + tableName + " as an Oracle ORGANIZATION EXTERNAL flat-file ingestion bridge. " +
                        "External tables bypass traditional relational foreign keys to stream batch records directly from " +
                        "OCI Linux server directories into indexed staging structures and core transactional ledgers.");

        boolean rendered = false;
        try {
            String mermaidGraph = generateExternalTablePipelineMermaid(extTable, allSchemaTables);
            String caption = "Figure: Flat-File Ingestion Pipeline Architecture — " + schema + "." + tableName;
            rendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidGraph, 540, 260, caption);
        } catch (Throwable t) {
            System.err.println("INFO: External Table pipeline Mermaid render fallback: " + t.getMessage());
        }

        if (!rendered) {
            embedJava2DExternalTablePipelineDiagram(doc, extTable, allSchemaTables);
        }

        renderExternalTablePipelineLegendCallout(doc);
    }

    /**
     * Generates a Mermaid graph LR pipeline diagram representing the ingestion lineage.
     */
    public static String generateExternalTablePipelineMermaid(
            GenerateTechnicalWordDocs.TableDocModel extTable,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: {\n");
        sb.append("  'theme': 'base',\n");
        sb.append("  'themeVariables': {\n");
        sb.append("    'primaryColor': '#F8FAFC',\n");
        sb.append("    'primaryTextColor': '#002B49',\n");
        sb.append("    'primaryBorderColor': '#002B49',\n");
        sb.append("    'lineColor': '#005A9C',\n");
        sb.append("    'textColor': '#002B49',\n");
        sb.append("    'fontSize': '12px',\n");
        sb.append("    'fontFamily': 'Segoe UI, Arial, sans-serif'\n");
        sb.append("  }\n");
        sb.append("}}%%\n");
        sb.append("graph LR\n");

        // Styling definitions
        sb.append("    classDef fileNode fill:#F8FAFC,stroke:#475569,stroke-width:2px,color:#002B49,font-size:11px;\n");
        sb.append("    classDef extNode fill:#FFF7ED,stroke:#EA580C,stroke-width:2.5px,color:#7C2D12,font-weight:bold,font-size:12px;\n");
        sb.append("    classDef procNode fill:#F3E8FF,stroke:#7E22CE,stroke-width:2px,color:#3B0764,font-size:11px;\n");
        sb.append("    classDef stgNode fill:#EFF6FF,stroke:#1D4ED8,stroke-width:2px,color:#172554,font-weight:bold,font-size:11px;\n");
        sb.append("    classDef coreNode fill:#ECFDF5,stroke:#059669,stroke-width:2px,color:#064E3B,font-weight:bold,font-size:11px;\n\n");

        String schema = (extTable.schema != null && !extTable.schema.isEmpty()) ? extTable.schema.toUpperCase() : "ARS";
        String tName = extTable.tableName.toUpperCase();

        // 1. Inbound Flat File
        String fileName = (!extTable.externalLocations.isEmpty())
                ? extTable.externalLocations.get(0)
                : tName.toLowerCase() + ".dat";
        String dirName = (!extTable.defaultDirectory.isEmpty())
                ? extTable.defaultDirectory
                : schema + "_DATA_DIR";
        String driver = (!extTable.driverType.isEmpty()) ? extTable.driverType : "ORACLE_LOADER";

        sb.append("    SRC_FILE[\"📄 <b>Inbound Flat File</b><br/><code>").append(fileName).append("</code><br/><small>Directory: ")
                .append(dirName).append("</small>\"]:::fileNode\n");

        // 2. External Table
        int colCount = extTable.columns != null ? extTable.columns.size() : 0;
        sb.append("    EXT_TBL[\"⚡ <b>EXTERNAL TABLE</b><br/><b>").append(schema).append(".").append(tName).append("</b><br/><small>Driver: ")
                .append(driver).append(" (").append(colCount).append(" cols)</small>\"]:::extNode\n");

        // 3. Batch Ingest Routine
        String routineName = (!extTable.referencingRoutines.isEmpty())
                ? extTable.referencingRoutines.get(0)
                : "PKG_" + schema + "_INGEST.LOAD_" + tName.replace("EXT_", "").replace("_EXT", "");
        sb.append("    INGEST_PROC[\"⚙️ <b>Batch Ingest Routine</b><br/><b>").append(routineName)
                .append("</b><br/><small>INSERT /*+ APPEND */ ...</small>\"]:::procNode\n");

        // 4. Downstream Staging Table
        String stgTableName = resolveDownstreamStagingTable(extTable, allSchemaTables);
        sb.append("    STG_TBL[\"🗄️ <b>Staging Persistence</b><br/><b>").append(schema).append(".").append(stgTableName)
                .append("</b><br/><small>Indexed Staging Table</small>\"]:::stgNode\n");

        // 5. Core Operational Target Table
        String coreTableName = resolveDownstreamCoreTable(extTable, stgTableName, allSchemaTables);
        boolean hasCore = coreTableName != null && !coreTableName.equalsIgnoreCase(stgTableName);
        if (hasCore) {
            sb.append("    CORE_TBL[\"🏛️ <b>Core Operational Ledger</b><br/><b>").append(schema).append(".").append(coreTableName)
                    .append("</b><br/><small>Validated Production Ledger</small>\"]:::coreNode\n");
        }

        // Connect Edges
        sb.append("\n    SRC_FILE -->|\"1. OCI OS Mount\"| EXT_TBL\n");
        sb.append("    EXT_TBL -->|\"2. Direct SQL Select\"| INGEST_PROC\n");
        sb.append("    INGEST_PROC -->|\"3. Direct-Path Load\"| STG_TBL\n");
        if (hasCore) {
            sb.append("    STG_TBL -->|\"4. Audit & Settlement\"| CORE_TBL\n");
        }

        String result = sb.toString();
        return result;
    }

    /**
     * Resolves the downstream STG_* staging table for an external table.
     */
    public static String resolveDownstreamStagingTable(
            GenerateTechnicalWordDocs.TableDocModel extTable,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        String raw = extTable.tableName.toUpperCase();
        String candidate1 = raw.replace("EXT_", "STG_");
        String candidate2 = raw.replace("_EXT", "_STG");
        String candidate3 = "STG_" + raw;
        String candidate4 = raw.replace("EXT_", "").replace("_EXT", "");

        if (allSchemaTables != null) {
            for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                String tName = t.tableName.toUpperCase();
                if (tName.equals(candidate1) || tName.equals(candidate2) || tName.equals(candidate3)) {
                    String result = t.tableName;
                    return result;
                }
            }
            // Check candidate 4 if candidate 1/2 weren't found
            for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                String tName = t.tableName.toUpperCase();
                if (tName.equals(candidate4) && !tName.equals(raw)) {
                    String result = t.tableName;
                    return result;
                }
            }
        }

        String fallback = raw.startsWith("EXT_") ? candidate1 : (raw.endsWith("_EXT") ? candidate2 : ("STG_" + raw));
        String result = fallback;
        return result;
    }

    /**
     * Resolves the ultimate core production/ledger table downstream of the staging table.
     */
    public static String resolveDownstreamCoreTable(
            GenerateTechnicalWordDocs.TableDocModel extTable,
            String stagingTableName,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        String rawStg = stagingTableName.toUpperCase();
        String candidateCore = rawStg.replace("STG_", "").replace("_STG", "");

        if (allSchemaTables != null) {
            for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                String tName = t.tableName.toUpperCase();
                if (tName.equals(candidateCore) && !tName.equals(extTable.tableName.toUpperCase())) {
                    String result = t.tableName;
                    return result;
                }
            }
        }

        // If staging name is different from clean core name, return clean core name
        if (!candidateCore.equals(rawStg) && !candidateCore.equals(extTable.tableName.toUpperCase())) {
            String result = candidateCore;
            return result;
        }

        String result = null;
        return result;
    }

    /**
     * Renders a 7.0x Ultra-Retina (700+ DPI) Java2D Ingestion Lineage Vector Graphic.
     */
    public static void embedJava2DExternalTablePipelineDiagram(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel extTable,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        try {
            int width = 760;
            int height = 240;

            double scale = 7.0; // 7.0x Ultra-Retina Supersampling
            BufferedImage img = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);

            TableRelationshipDiagramHelper.setHighQualityRenderingHints(g);

            // Canvas Background
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(COLOR_BORDER);
            g.drawRoundRect(2, 2, width - 5, height - 5, 8, 8);

            // Header Banner
            g.setColor(COLOR_NAVY);
            g.fillRoundRect(8, 8, width - 16, 24, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String schema = (extTable.schema != null && !extTable.schema.isEmpty()) ? extTable.schema.toUpperCase() : "ARS";
            g.drawString("FLAT-FILE INGESTION & ELT PIPELINE: " + schema + "." + extTable.tableName.toUpperCase(), 24, 24);

            // Dimensions for 4 pipeline stage cards
            int cardW = 160;
            int cardH = 135;
            int cardY = 46;
            int gap = 28;
            int startX = 16;

            String fileName = (!extTable.externalLocations.isEmpty())
                    ? extTable.externalLocations.get(0)
                    : extTable.tableName.toLowerCase() + ".dat";
            String dirName = (!extTable.defaultDirectory.isEmpty()) ? extTable.defaultDirectory : schema + "_DATA_DIR";
            String driver = (!extTable.driverType.isEmpty()) ? extTable.driverType : "ORACLE_LOADER";
            String routine = (!extTable.referencingRoutines.isEmpty()) ? extTable.referencingRoutines.get(0) : "PKG_" + schema + "_INGEST";
            String stgTable = resolveDownstreamStagingTable(extTable, allSchemaTables);
            String coreTable = resolveDownstreamCoreTable(extTable, stgTable, allSchemaTables);
            if (coreTable == null) coreTable = "ARS_CORE_LEDGER";

            // Stage 1: Flat File
            int x1 = startX;
            drawPipelineCard(g, x1, cardY, cardW, cardH, "1. INBOUND FILE", fileName, "Directory: " + dirName, "Format: CSV / Fixed", new Color(241, 245, 249), COLOR_SLATE);

            // Arrow 1 -> 2
            drawPipelineArrow(g, x1 + cardW + 2, cardY + cardH / 2, x1 + cardW + gap - 2, cardY + cardH / 2, "OS Mount");

            // Stage 2: External Table
            int x2 = x1 + cardW + gap;
            drawPipelineCard(g, x2, cardY, cardW, cardH, "2. EXTERNAL TABLE", extTable.tableName, "Driver: " + driver, "Type: ORGANIZATION EXT", new Color(254, 243, 199), COLOR_AMBER);

            // Arrow 2 -> 3
            drawPipelineArrow(g, x2 + cardW + 2, cardY + cardH / 2, x2 + cardW + gap - 2, cardY + cardH / 2, "SQL Read");

            // Stage 3: Ingest Routine
            int x3 = x2 + cardW + gap;
            drawPipelineCard(g, x3, cardY, cardW, cardH, "3. BATCH INGEST", routine, "Direct Path Insert", "/*+ APPEND */", new Color(243, 232, 255), COLOR_PLUM);

            // Arrow 3 -> 4
            drawPipelineArrow(g, x3 + cardW + 2, cardY + cardH / 2, x3 + cardW + gap - 2, cardY + cardH / 2, "Bulk Load");

            // Stage 4: Staging Persistence
            int x4 = x3 + cardW + gap;
            drawPipelineCard(g, x4, cardY, cardW, cardH, "4. STAGING PERSISTENCE", stgTable, "Schema: " + schema, "Downstream: " + coreTable, new Color(239, 246, 255), COLOR_BLUE);

            // Legend Ribbon at Bottom
            drawPipelineLegendBar(g, width, height);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            // Insert into Word Document
            TableRelationshipDiagramHelper.renderRelationshipLegendCallout(doc); // Standard legend
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            run.addPicture(bais, Document.PICTURE_TYPE_PNG, "ExtPipeline_" + extTable.tableName + ".png",
                    org.apache.poi.util.Units.toEMU(520), org.apache.poi.util.Units.toEMU((int) Math.round(520.0 * height / width)));

        } catch (Throwable t) {
            System.err.println("WARN: Could not embed Java2D external table pipeline diagram: " + t.getMessage());
        }
    }

    private static void drawPipelineCard(Graphics2D g, int x, int y, int w, int h, String stageTitle, String title, String line1, String line2, Color bg, Color stroke) {
        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1.8f));
        g.drawRoundRect(x, y, w, h, 8, 8);

        // Header Badge
        g.setColor(stroke);
        g.fillRoundRect(x + 6, y + 6, w - 12, 18, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString(stageTitle, x + 12, y + 19);

        // Card Title
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        String displayTitle = title.length() > 22 ? title.substring(0, 20) + ".." : title;
        g.drawString(displayTitle, x + 10, y + 44);

        // Card Lines
        g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        g.setColor(COLOR_SLATE);
        g.drawString(truncate(line1, 24), x + 10, y + 66);
        g.drawString(truncate(line2, 24), x + 10, y + 84);

        // Decorative dot
        g.setColor(stroke);
        g.fillOval(x + 10, y + 104, 6, 6);
        g.setFont(new Font("Segoe UI", Font.ITALIC, 7));
        g.setColor(COLOR_SLATE);
        g.drawString("Verified Stage", x + 20, y + 110);
    }

    private static void drawPipelineArrow(Graphics2D g, int x1, int y1, int x2, int y2, String label) {
        g.setColor(COLOR_PLUM);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(x1, y1, x2, y2);

        // Arrow head
        int arrowSize = 4;
        g.drawLine(x2, y2, x2 - arrowSize, y2 - arrowSize);
        g.drawLine(x2, y2, x2 - arrowSize, y2 + arrowSize);

        // Label
        if (label != null && !label.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 6));
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth(label);
            g.drawString(label, (x1 + x2) / 2 - strW / 2, y1 - 4);
        }
    }

    private static void drawPipelineLegendBar(Graphics2D g, int width, int height) {
        int legendY = height - 26;
        int legendH = 20;

        g.setColor(new Color(248, 250, 252));
        g.fillRoundRect(8, legendY, width - 16, legendH, 5, 5);
        g.setColor(new Color(203, 213, 225));
        g.drawRoundRect(8, legendY, width - 16, legendH, 5, 5);

        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        int textY = legendY + 14;

        g.setColor(COLOR_NAVY);
        g.drawString("INGESTION NOTATION:", 16, textY);

        g.setColor(COLOR_SLATE);
        g.drawString("📄 [INBOUND FILE] SFTP / OCI Directory", 115, textY);

        g.setColor(COLOR_AMBER);
        g.drawString("⚡ [EXTERNAL TABLE] Zero-Constraint Flat File Bridge", 295, textY);

        g.setColor(COLOR_PLUM);
        g.drawString("⚙️ [BATCH INGEST] Direct-Path Insert", 535, textY);
    }

    /**
     * Generates an ultra-crisp 6.0x Retina graphic image of the External Table
     * ELT Pipeline Notation Legend with exact icons and badges.
     */
    public static byte[] generatePipelineLegendImage() {
        int width = 840;
        int height = 96;
        double scale = 6.0;

        BufferedImage img = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        TableRelationshipDiagramHelper.setHighQualityRenderingHints(g);

        // Canvas Background
        g.setColor(new Color(248, 250, 252));
        g.fillRect(0, 0, width, height);
        g.setColor(COLOR_BORDER);
        g.drawRoundRect(2, 2, width - 5, height - 5, 8, 8);

        // Header Strip (Deep Navy with Florida Lottery Plum accent line)
        g.setColor(COLOR_NAVY);
        g.fillRoundRect(6, 6, width - 12, 26, 6, 6);
        g.setColor(COLOR_PLUM);
        g.fillRect(6, 28, width - 12, 4); // Florida Lottery Plum accent baseline
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.drawString("EXTERNAL TABLE ELT PIPELINE NOTATION LEGEND", 20, 22);

        g.setColor(COLOR_GOLD);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("FLORIDA LOTTERY HIGH-THROUGHPUT BULK INGESTION ARCHITECTURE", width - 370, 22);

        int rowY = 52;
        // 1. Inbound File
        g.setColor(new Color(241, 245, 249));
        g.fillRoundRect(16, rowY - 14, 76, 20, 4, 4);
        g.setColor(COLOR_SLATE);
        g.drawRoundRect(16, rowY - 14, 76, 20, 4, 4);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("INBOUND FILE", 22, rowY);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("OS Landing File", 98, rowY - 4);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("OCI Flat-File Storage", 98, rowY + 7);

        // 2. External Table
        g.setColor(new Color(254, 243, 199));
        g.fillRoundRect(220, rowY - 14, 82, 20, 4, 4);
        g.setColor(COLOR_AMBER);
        g.drawRoundRect(220, rowY - 14, 82, 20, 4, 4);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("EXTERNAL TABLE", 225, rowY);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("ORGANIZATION EXT", 308, rowY - 4);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("SQL Bridge (Zero Disk)", 308, rowY + 7);

        // 3. Batch Ingest
        g.setColor(new Color(243, 232, 255));
        g.fillRoundRect(430, rowY - 14, 80, 20, 4, 4);
        g.setColor(COLOR_PLUM);
        g.drawRoundRect(430, rowY - 14, 80, 20, 4, 4);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("BATCH INGEST", 436, rowY);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("Direct-Path Insert", 516, rowY - 4);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("/*+ APPEND */ Routine", 516, rowY + 7);

        // 4. Staging Persistence
        g.setColor(new Color(239, 246, 255));
        g.fillRoundRect(636, rowY - 14, 86, 20, 4, 4);
        g.setColor(COLOR_BLUE);
        g.drawRoundRect(636, rowY - 14, 86, 20, 4, 4);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("STAGING TABLE", 642, rowY);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("Relational Ledger", 728, rowY - 4);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("Validated Commitment", 728, rowY + 7);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "PNG", baos);
            byte[] result = baos.toByteArray();
            return result;
        } catch (Exception ex) {
            byte[] result = null;
            return result;
        }
    }

    public static void renderExternalTablePipelineLegendCallout(XWPFDocument doc) {
        try {
            byte[] legendBytes = generatePipelineLegendImage();
            if (legendBytes != null && legendBytes.length > 0) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                p.setSpacingBefore(100);
                p.setSpacingAfter(60);
                XWPFRun r = p.createRun();
                r.addPicture(new ByteArrayInputStream(legendBytes), Document.PICTURE_TYPE_PNG, "Pipeline_Legend.png",
                        org.apache.poi.util.Units.toEMU(525), org.apache.poi.util.Units.toEMU((int) Math.round(525.0 * 96 / 840)));
                return;
            }
        } catch (Throwable t) {
            System.err.println("WARN: Could not embed graphical pipeline legend image: " + t.getMessage());
        }

        addCallout(doc, "EXTERNAL TABLE ELT PIPELINE NOTATION LEGEND:",
                "• [INBOUND FILE]: Physical operating-system landing file located on OCI server storage.\n" +
                        "• [EXTERNAL TABLE]: Oracle ORGANIZATION EXTERNAL definition mapping flat data into SQL without disk tablespace.\n" +
                        "• [BATCH INGEST]: Direct-path bulk insert routine (INSERT /*+ APPEND */ ... SELECT ...) optimizing throughput.\n" +
                        "• [STAGING PERSISTENCE]: Indexed staging table validating record integrity before final core ledger commit.");
    }

    private static void addCallout(XWPFDocument doc, String label, String text) {
        XWPFTable table = doc.createTable(1, 1);
        table.setWidth("100%");
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor("F7F5F9");

        XWPFParagraph p = cell.getParagraphArray(0);
        p.setSpacingBefore(60);
        p.setSpacingAfter(60);

        XWPFRun rLabel = p.createRun();
        rLabel.setBold(true);
        rLabel.setFontSize(9);
        rLabel.setFontFamily("Segoe UI");
        rLabel.setColor(TableRelationshipDiagramHelper.HEX_NAVY);
        rLabel.setText(label + " ");

        XWPFRun rText = p.createRun();
        rText.setFontSize(9);
        rText.setFontFamily("Segoe UI");
        rText.setColor("334155");
        rText.setText(text);

        XWPFParagraph space = doc.createParagraph();
        space.setSpacingBefore(40);
        space.setSpacingAfter(40);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            String result = "";
            return result;
        }
        if (s.length() <= max) {
            String result = s;
            return result;
        }
        String result = s.substring(0, max - 2) + "..";
        return result;
    }
}
