package com.fllottery.dbresearch.diagram;

import com.fllottery.common.diagram.MermaidDslHelper;
import com.fllottery.common.office.FlBrandTheme;
import com.fllottery.dbresearch.doc.GenerateTechnicalWordDocs;
import com.fllottery.dbresearch.model.*;
import com.fllottery.dbresearch.search.DbObjectSearcher;

import com.fllottery.util.ExternalTablePipelineDiagramHelper;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;

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
 * Enterprise Database Table Relationship, ER Diagram & Data Lineage Engine for Florida Lottery.
 * <p>
 * Features:
 *  1. Mermaid Entity-Relationship (erDiagram) Crow's Foot specifications with parent/child entity rendering.
 *  2. Mermaid Data-Flow & Procedure Access Lineage (graph LR) connecting Ingress Writers -> Table -> Egress Readers.
 *  3. Micro Column Headers and Syntax-Color Coded Data Types across all visual diagrams and Word documents.
 *  4. Resilient 7.0x Ultra-High DPI Retina Java2D Vector Fallbacks (Doubled Resolution, 700+ DPI).
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery DataOps Architecture
 * @since 2026-10-03
 */
public class TableRelationshipDiagramHelper {

    // Florida Lottery Enterprise Color Palette (Hex & Java AWT) - Plum Primary Theme
    public static final String HEX_PLUM = "5C1D48";         // #5C1D48 Florida Lottery Royal Plum (Primary brand)
    public static final String HEX_PLUM_DARK = "3B102E";    // #3B102E Deep Dark Plum (Target Header / High contrast)
    public static final String HEX_PLUM_MED = "7A2A60";     // #7A2A60 Medium Burgundy Plum (Secondary Tables)
    public static final String HEX_PLUM_LIGHT = "F5EEF8";   // #F5EEF8 Soft Plum Lavender Tint (Subheaders & Background)
    public static final String HEX_PLUM_BORDER = "C4B5D0";  // #C4B5D0 Soft Plum Border Divider
    public static final String HEX_NAVY = "002B49";         // #002B49 Deep Navy
    public static final String HEX_BLUE = "005A9C";         // #005A9C Florida Lottery Blue
    public static final String HEX_GOLD = "DAA520";         // #DAA520 Accent Gold (PK & Key highlight)
    public static final String HEX_GREEN = "228B22";        // #228B22 Forest Green (Success / Ingress / FK)
    public static final String HEX_RED = "B91C1C";          // #B91C1C Rejection / Error Red
    public static final String HEX_SLATE = "475569";        // #475569 Borders & Relational Connectors
    public static final String HEX_LIGHT_BG = "F8FAFC";     // #F8FAFC Clean Canvas / Shape Background

    public static final Color COLOR_PLUM = new Color(92, 29, 72);        // #5C1D48 Royal Plum
    public static final Color COLOR_PLUM_DARK = new Color(59, 16, 46);   // #3B102E Deep Dark Plum
    public static final Color COLOR_PLUM_MED = new Color(122, 42, 96);   // #7A2A60 Medium Plum
    public static final Color COLOR_PLUM_LIGHT = new Color(245, 238, 248); // #F5EEF8 Soft Plum Tint
    public static final Color COLOR_PLUM_BORDER = new Color(196, 181, 208);
    public static final Color COLOR_NAVY = new Color(0, 43, 73);
    public static final Color COLOR_BLUE = new Color(0, 90, 156);
    public static final Color COLOR_GOLD = new Color(218, 165, 32);
    public static final Color COLOR_GREEN = new Color(34, 139, 34);
    public static final Color COLOR_RED = new Color(185, 28, 28);
    public static final Color COLOR_SLATE = new Color(71, 85, 105);
    public static final Color COLOR_LIGHT_BG = new Color(248, 250, 252);
    public static final Color COLOR_BORDER = new Color(226, 232, 240);

    // Syntax-Color Coded Data Type Palette (Enterprise Theme - Matching User Draft)
    public static final String HEX_TYPE_NUMBER = "1D4ED8";   // Cobalt Blue (Numbers, IDs, Decimals)
    public static final String HEX_TYPE_STRING = "7E22CE";   // Purple / Magenta (VARCHAR2, CHAR, VARCHAR)
    public static final String HEX_TYPE_DATE = "B45309";     // Warm Amber Gold (DATE, TIMESTAMP)
    public static final String HEX_TYPE_BLOB = "0F766E";     // Forest Teal (RAW, BLOB, XML)
    public static final String HEX_TYPE_DEFAULT = "334155";  // Slate

    public static final Color COLOR_TYPE_NUMBER = new Color(29, 78, 216); // Blue
    public static final Color COLOR_TYPE_STRING = new Color(126, 34, 206); // Purple
    public static final Color COLOR_TYPE_DATE = new Color(180, 83, 9);    // Amber
    public static final Color COLOR_TYPE_BLOB = new Color(15, 118, 110);  // Teal
    public static final Color COLOR_TYPE_DEFAULT = new Color(51, 65, 85);

    /**
     * Resolves the syntax color hex code for a given database data type.
     */
    public static String getDataTypeColorHex(String dataType) {
        if (dataType == null || dataType.isEmpty()) return HEX_TYPE_DEFAULT;
        String upper = dataType.toUpperCase();
        if (upper.contains("NUM") || upper.contains("INT") || upper.contains("DEC") || upper.contains("FLOAT") || upper.contains("DOUBLE")) {
            return HEX_TYPE_NUMBER;
        }
        if (upper.contains("VARCHAR") || upper.contains("CHAR") || upper.contains("TEXT") || upper.contains("CLOB") || upper.contains("NVARCHAR")) {
            return HEX_TYPE_STRING;
        }
        if (upper.contains("DATE") || upper.contains("TIME") || upper.contains("TIMESTAMP") || upper.contains("INTERVAL")) {
            return HEX_TYPE_DATE;
        }
        if (upper.contains("BLOB") || upper.contains("RAW") || upper.contains("XML") || upper.contains("BINARY") || upper.contains("BYTEA")) {
            return HEX_TYPE_BLOB;
        }
        return HEX_TYPE_DEFAULT;
    }

    /**
     * Resolves the AWT Color for a given database data type.
     */
    public static Color getDataTypeColor(String dataType) {
        if (dataType == null || dataType.isEmpty()) return COLOR_TYPE_DEFAULT;
        String upper = dataType.toUpperCase();
        if (upper.contains("NUM") || upper.contains("INT") || upper.contains("DEC") || upper.contains("FLOAT") || upper.contains("DOUBLE")) {
            return COLOR_TYPE_NUMBER;
        }
        if (upper.contains("VARCHAR") || upper.contains("CHAR") || upper.contains("TEXT") || upper.contains("CLOB") || upper.contains("NVARCHAR")) {
            return COLOR_TYPE_STRING;
        }
        if (upper.contains("DATE") || upper.contains("TIME") || upper.contains("TIMESTAMP") || upper.contains("INTERVAL")) {
            return COLOR_TYPE_DATE;
        }
        if (upper.contains("BLOB") || upper.contains("RAW") || upper.contains("XML") || upper.contains("BINARY") || upper.contains("BYTEA")) {
            return COLOR_TYPE_BLOB;
        }
        return COLOR_TYPE_DEFAULT;
    }

    // =========================================================================
    // 1. MERMAID DATA-FLOW & ER DIAGRAM GENERATORS
    // =========================================================================

    /**
     * Generates a focused Mermaid Data-Flow & Stored Procedure Lineage diagram (graph LR).
     */
    public static String generateTableDataFlowAndRoutineLineage(
            GenerateTechnicalWordDocs.TableDocModel tbl,
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

        // Styling definitions (Florida Lottery Enterprise Theme)
        sb.append("    classDef target fill:#002B49,stroke:#DAA520,stroke-width:2.5px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("    classDef writer fill:#DCFCE7,stroke:#16A34A,stroke-width:1.5px,color:#14532D,font-weight:bold;\n");
        sb.append("    classDef reader fill:#EFF6FF,stroke:#005A9C,stroke-width:1.5px,color:#002B49,font-weight:bold;\n");
        sb.append("    classDef parentTable fill:#005A9C,stroke:#002B49,stroke-width:1.5px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("    classDef childTable fill:#7A2A60,stroke:#3B102E,stroke-width:1.5px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("    classDef trigger fill:#FDF4FF,stroke:#9333EA,stroke-width:1px,color:#581C87,stroke-dasharray: 4 2;\n\n");

        String targetId = "TBL_" + sanitizeName(tbl.tableName);
        String targetLabel = tbl.schema.toUpperCase() + "." + tbl.tableName;
        String pkLabel = tbl.primaryKeys.isEmpty() ? "ROWID" : String.join(",", tbl.primaryKeys);
        sb.append("    ").append(targetId).append("[\"fa:fa-database <b>").append(targetLabel).append("</b><br/><small>PK: ")
                .append(truncate(pkLabel, 22)).append(" | ").append(tbl.columns.size()).append(" cols</small>\"]:::target\n\n");

        // Upstream Ingress Writers
        List<String> writers = new ArrayList<>();
        if (tbl.referencingRoutines != null && !tbl.referencingRoutines.isEmpty()) {
            for (String r : tbl.referencingRoutines) {
                String u = r.toUpperCase();
                if (u.contains("LOAD") || u.contains("INS") || u.contains("SETTLE") || u.contains("IMPORT") || u.contains("PKG") || u.contains("PROC")) {
                    writers.add(r);
                }
            }
            if (writers.isEmpty()) {
                writers.add(tbl.referencingRoutines.get(0));
            }
        } else {
            writers.add(tbl.schema.toUpperCase() + ".BATCH_LOAD_JOB");
        }

        // Parent Foreign Key Lookups (Suppressed for External flat-file tables)
        Map<String, String> parentFks = new LinkedHashMap<>();
        Map<String, GenerateTechnicalWordDocs.TableDocModel> tableMap = new HashMap<>();
        if (allSchemaTables != null) {
            for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                tableMap.put(t.tableName.toUpperCase(), t);
            }
        }
        if (!isExternalTable(tbl)) {
            Set<String> pkCols = new HashSet<>(tbl.primaryKeys != null ? tbl.primaryKeys : Collections.emptyList());
            for (GenerateTechnicalWordDocs.TableColumnDoc col : tbl.columns) {
                if (pkCols.contains(col.columnName)) continue;
                String cName = col.columnName.toUpperCase();
                String refTable = inferReferencedTable(tbl.schema, cName, tableMap.keySet());
                if (refTable != null && !refTable.equalsIgnoreCase(tbl.tableName)) {
                    GenerateTechnicalWordDocs.TableDocModel pModel = tableMap.get(refTable.toUpperCase());
                    if (pModel == null || !isExternalTable(pModel)) {
                        parentFks.put(refTable, cName);
                        if (parentFks.size() >= 2) break;
                    }
                }
            }
        }

        // Downstream Egress Readers & Dependent Child Tables
        List<String> readers = new ArrayList<>();
        Map<String, String> childTables = new LinkedHashMap<>();
        if (tbl.referencingRoutines != null) {
            for (String r : tbl.referencingRoutines) {
                if (!writers.contains(r)) {
                    readers.add(r);
                }
            }
        }
        if (allSchemaTables != null && !isExternalTable(tbl)) {
            for (GenerateTechnicalWordDocs.TableDocModel other : allSchemaTables) {
                if (other.tableName.equalsIgnoreCase(tbl.tableName) || isExternalTable(other)) continue;
                for (GenerateTechnicalWordDocs.TableColumnDoc col : other.columns) {
                    if (inferReferencedTable(tbl.schema, col.columnName.toUpperCase(), Collections.singleton(tbl.tableName.toUpperCase())) != null) {
                        childTables.put(other.tableName, col.columnName);
                        if (childTables.size() >= 2) break;
                    }
                }
                if (childTables.size() >= 2) break;
            }
        }
        if (readers.isEmpty() && childTables.isEmpty()) {
            readers.add(tbl.schema.toUpperCase() + ".RPT_AUDIT_VIEW");
            readers.add(tbl.schema.toUpperCase() + ".DATA_EXTRACT_JOB");
        }

        // Emit Upstream Ingress Nodes & Connectors
        sb.append("    subgraph UPSTREAM [\"Upstream Ingress & Master References\"]\n");
        int wIdx = 0;
        for (String w : writers) {
            if (wIdx++ >= 3) break;
            String wId = "WRITER_" + wIdx;
            String wLabel = w.contains(".") ? w : tbl.schema.toUpperCase() + "." + w;
            sb.append("        ").append(wId).append("([\"").append(wLabel).append("\"]):::writer\n");
            sb.append("        ").append(wId).append(" -->|\"INSERT / UPDATE\"| ").append(targetId).append("\n");
        }
        int pIdx = 0;
        for (Map.Entry<String, String> pEntry : parentFks.entrySet()) {
            String pId = "PARENT_" + (++pIdx);
            String pName = tbl.schema.toUpperCase() + "." + pEntry.getKey();
            sb.append("        ").append(pId).append("[(\"").append(pName).append("\")]:::parentTable\n");
            sb.append("        ").append(pId).append(" ===|\"FK: ").append(pEntry.getValue()).append("\"| ").append(targetId).append("\n");
        }
        sb.append("    end\n\n");

        // Emit Downstream Egress Nodes & Connectors
        sb.append("    subgraph DOWNSTREAM [\"Downstream Egress, Events & Dependents\"]\n");
        int rIdx = 0;
        for (String r : readers) {
            if (rIdx++ >= 3) break;
            String rId = "READER_" + rIdx;
            String rLabel = r.contains(".") ? r : tbl.schema.toUpperCase() + "." + r;
            sb.append("        ").append(rId).append("([\"").append(rLabel).append("\"]):::reader\n");
            sb.append("        ").append(targetId).append(" -->|\"SELECT / EXTRACT\"| ").append(rId).append("\n");
        }

        // Add Schema Triggers if present
        if (tbl.triggers != null && !tbl.triggers.isEmpty()) {
            int tIdx = 0;
            for (GenerateTechnicalWordDocs.TableTriggerDoc trg : tbl.triggers) {
                if (tIdx++ >= 2) break;
                String trgId = "TRG_" + tIdx;
                String trgName = tbl.schema.toUpperCase() + "." + trg.triggerName;
                sb.append("        ").append(trgId).append("{{\"").append(trgName).append("\"}}:::trigger\n");
                sb.append("        ").append(targetId).append(" -.->|\"").append(trg.triggeringEvent != null ? trg.triggeringEvent : "TRIGGER").append("\"| ").append(trgId).append("\n");
            }
        }

        // Add Child Tables if present
        int cIdx = 0;
        for (Map.Entry<String, String> cEntry : childTables.entrySet()) {
            String cId = "CHILD_" + (++cIdx);
            String cName = tbl.schema.toUpperCase() + "." + cEntry.getKey();
            sb.append("        ").append(cId).append("[(\"").append(cName).append("\")]:::childTable\n");
            sb.append("        ").append(targetId).append(" -->|\"CHILD REF: ").append(cEntry.getValue()).append("\"| ").append(cId).append("\n");
        }
        sb.append("    end\n");

        String result = sb.toString();
        return result;
    }

    /**
     * Generates a focused 1-Hop Column-Relationship Diagram in Mermaid (MS Access flowchart LR style).
     * Connects parent primary key fields directly to child foreign key fields with directed arrow connectors.
     */
    public static String generateSingleTableRelationalContext(
            GenerateTechnicalWordDocs.TableDocModel targetTable,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        if (targetTable == null) {
            String result = "";
            return result;
        }
        if (isExternalTable(targetTable)) {
            String result = ExternalTablePipelineDiagramHelper.generateExternalTablePipelineMermaid(targetTable, allSchemaTables);
            return result;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: {\n");
        sb.append("  'theme': 'base',\n");
        sb.append("  'themeVariables': {\n");
        sb.append("    'primaryColor': '#F5EEF8',\n");
        sb.append("    'primaryTextColor': '#3B102E',\n");
        sb.append("    'primaryBorderColor': '#5C1D48',\n");
        sb.append("    'lineColor': '#5C1D48',\n");
        sb.append("    'textColor': '#3B102E',\n");
        sb.append("    'fontSize': '12px',\n");
        sb.append("    'fontFamily': 'Segoe UI, Arial, sans-serif'\n");
        sb.append("  }\n");
        sb.append("}}%%\n");
        sb.append("flowchart TD\n\n");

        String targetSchema = (targetTable.schema != null && !targetTable.schema.isEmpty()) ? targetTable.schema.toUpperCase() : "SCHEMA";
        String targetName = sanitizeName(targetSchema + "_" + targetTable.tableName);
        String targetDisplay = targetSchema + "." + targetTable.tableName.toUpperCase();
        Set<String> pkCols = new HashSet<>(targetTable.primaryKeys != null ? targetTable.primaryKeys : Collections.emptyList());
        String targetPrimaryCol = (!pkCols.isEmpty()) ? targetTable.primaryKeys.get(0) : "ID";

        Map<String, GenerateTechnicalWordDocs.TableDocModel> tableMap = new HashMap<>();
        if (allSchemaTables != null) {
            for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                tableMap.put(t.tableName.toUpperCase(), t);
            }
        }

        Map<String, String> parentRels = new LinkedHashMap<>();
        Map<String, String> childRels = new LinkedHashMap<>();

        // 1. Discover Parent Tables referenced by targetTable
        if (!isExternalTable(targetTable)) {
            TableRelationship bestParent = DbObjectSearcher.getInstance().findBestParentRelationship(targetTable.schema, targetTable.tableName);
            if (bestParent != null) {
                String pTable = bestParent.getTargetTable();
                String pSchema = bestParent.getTargetSchema();
                String pName = sanitizeName(pSchema + "_" + pTable);
                GenerateTechnicalWordDocs.TableDocModel pModel = tableMap.get(pTable.toUpperCase());
                JoinDescriptor pJd = resolveJoin(pModel != null ? pModel : createSyntheticModel(pSchema, pTable), targetTable);
                appendFlowchartEdge(sb, pName, targetName, pJd);
                parentRels.put(pTable, bestParent.getSourceColumn());
            } else {
                for (GenerateTechnicalWordDocs.TableColumnDoc col : targetTable.columns) {
                    if (pkCols.contains(col.columnName)) continue;
                    String cName = col.columnName.toUpperCase();
                    String refTable = inferReferencedTable(targetTable.schema, cName, tableMap.keySet());
                    if (refTable != null && !refTable.equalsIgnoreCase(targetTable.tableName)) {
                        GenerateTechnicalWordDocs.TableDocModel pModel = tableMap.get(refTable.toUpperCase());
                        if (pModel == null || !isExternalTable(pModel)) {
                            parentRels.put(refTable, cName);
                            String pSchema = (pModel != null && pModel.schema != null && !pModel.schema.isEmpty()) ? pModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, refTable);
                            String pName = sanitizeName(pSchema + "_" + refTable);
                            JoinDescriptor pJd = resolveJoin(pModel != null ? pModel : createSyntheticModel(pSchema, refTable), targetTable);
                            appendFlowchartEdge(sb, pName, targetName, pJd);
                            if (parentRels.size() >= 2) break;
                        }
                    }
                }
            }
        }

        // 2. Discover Child Tables referencing targetTable
        if (!isExternalTable(targetTable)) {
            List<TableRelationship> children = DbObjectSearcher.getInstance().findChildRelationships(targetTable.schema, targetTable.tableName);
            if (!children.isEmpty()) {
                for (TableRelationship cRel : children) {
                    String cTable = cRel.getSourceTable();
                    String cSchema = cRel.getSourceSchema();
                    String cName = sanitizeName(cSchema + "_" + cTable);
                    GenerateTechnicalWordDocs.TableDocModel cModel = tableMap.get(cTable.toUpperCase());
                    JoinDescriptor cJd = resolveJoin(targetTable, cModel != null ? cModel : createSyntheticModel(cSchema, cTable));
                    appendFlowchartEdge(sb, targetName, cName, cJd);
                    childRels.put(cTable, cRel.getSourceColumn());
                    if (childRels.size() >= 2) break;
                }
            } else if (allSchemaTables != null) {
                for (GenerateTechnicalWordDocs.TableDocModel other : allSchemaTables) {
                    if (other.tableName.equalsIgnoreCase(targetTable.tableName) || isExternalTable(other)) continue;
                    for (GenerateTechnicalWordDocs.TableColumnDoc col : other.columns) {
                        if (inferReferencedTable(targetTable.schema, col.columnName.toUpperCase(), Collections.singleton(targetTable.tableName.toUpperCase())) != null) {
                            childRels.put(other.tableName, col.columnName);
                            String cSchema = (other.schema != null && !other.schema.isEmpty()) ? other.schema.toUpperCase() : inferSchemaForTable(targetSchema, other.tableName);
                            String cName = sanitizeName(cSchema + "_" + other.tableName);
                            JoinDescriptor cJd = resolveJoin(targetTable, other);
                            appendFlowchartEdge(sb, targetName, cName, cJd);
                            if (childRels.size() >= 2) break;
                        }
                    }
                    if (childRels.size() >= 2) break;
                }
            }
        }

        // Standalone table if no parents and no children detected
        if (parentRels.isEmpty() && childRels.isEmpty()) {
            sb.append("    %% Standalone enterprise entity without explicit foreign keys in catalog\n");
        }

        // Render table window cards (Grid tables matching user draft)
        // Parent entities rendered first (top layer)
        for (String p : parentRels.keySet()) {
            GenerateTechnicalWordDocs.TableDocModel pModel = tableMap.get(p.toUpperCase());
            String pSchema = (pModel != null && pModel.schema != null && !pModel.schema.isEmpty()) ? pModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, p);
            renderFlowchartTableCard(sb, sanitizeName(pSchema + "_" + p), pSchema + "." + p.toUpperCase(), pModel, false);
        }

        // Target focal entity rendered
        renderFlowchartTableCard(sb, targetName, targetDisplay, targetTable, true);

        // Child entities rendered (bottom layer)
        for (String c : childRels.keySet()) {
            GenerateTechnicalWordDocs.TableDocModel cModel = tableMap.get(c.toUpperCase());
            String cSchema = (cModel != null && cModel.schema != null && !cModel.schema.isEmpty()) ? cModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, c);
            renderFlowchartTableCard(sb, sanitizeName(cSchema + "_" + c), cSchema + "." + c.toUpperCase(), cModel, false);
        }

        String result = sb.toString();
        return result;
    }

    /**
     * Generates an expanded Table-Relationship Diagram in Mermaid (MS Access flowchart TD style).
     * Connects parent primary key fields directly to child foreign key fields with directed arrow connectors,
     * rendering master entities, dependent transaction entities, and related cluster tables.
     */
    public static String generateMsAccessWorldViewErDiagram(
            GenerateTechnicalWordDocs.TableDocModel targetTable,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        if (targetTable == null) {
            String result = "";
            return result;
        }
        if (isExternalTable(targetTable)) {
            String result = ExternalTablePipelineDiagramHelper.generateExternalTablePipelineMermaid(targetTable, allSchemaTables);
            return result;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: {\n");
        sb.append("  'theme': 'base',\n");
        sb.append("  'themeVariables': {\n");
        sb.append("    'primaryColor': '#F5EEF8',\n");
        sb.append("    'primaryTextColor': '#3B102E',\n");
        sb.append("    'primaryBorderColor': '#5C1D48',\n");
        sb.append("    'lineColor': '#5C1D48',\n");
        sb.append("    'textColor': '#3B102E',\n");
        sb.append("    'fontSize': '12px',\n");
        sb.append("    'fontFamily': 'Segoe UI, Arial, sans-serif'\n");
        sb.append("  }\n");
        sb.append("}}%%\n");
        sb.append("flowchart TD\n\n");

        String targetSchema = (targetTable.schema != null && !targetTable.schema.isEmpty()) ? targetTable.schema.toUpperCase() : "SCHEMA";
        String targetName = sanitizeName(targetSchema + "_" + targetTable.tableName);
        String targetDisplay = targetSchema + "." + targetTable.tableName.toUpperCase();
        Set<String> pkCols = new HashSet<>(targetTable.primaryKeys != null ? targetTable.primaryKeys : Collections.emptyList());
        String targetPrimaryCol = (!pkCols.isEmpty()) ? targetTable.primaryKeys.get(0) : "ID";

        Map<String, GenerateTechnicalWordDocs.TableDocModel> tableMap = new HashMap<>();
        if (allSchemaTables != null) {
            for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                tableMap.put(t.tableName.toUpperCase(), t);
            }
        }

        Map<String, String> parentRels = new LinkedHashMap<>();
        Map<String, String> childRels = new LinkedHashMap<>();

        // 1. Discover Parent Tables referenced by targetTable (DbObjectSearcher first, then columns)
        if (!isExternalTable(targetTable)) {
            TableRelationship bestParent = DbObjectSearcher.getInstance().findBestParentRelationship(targetTable.schema, targetTable.tableName);
            if (bestParent != null && bestParent.getTargetTable() != null && !bestParent.getTargetTable().equalsIgnoreCase(targetTable.tableName)) {
                parentRels.put(bestParent.getTargetTable(), bestParent.getSourceColumn());
            }
            for (GenerateTechnicalWordDocs.TableColumnDoc col : targetTable.columns) {
                if (pkCols.contains(col.columnName)) continue;
                String cName = col.columnName.toUpperCase();
                String refTable = inferReferencedTable(targetTable.schema, cName, tableMap.keySet());
                if (refTable != null && !refTable.equalsIgnoreCase(targetTable.tableName)) {
                    GenerateTechnicalWordDocs.TableDocModel pModel = tableMap.get(refTable.toUpperCase());
                    if (pModel == null || !isExternalTable(pModel)) {
                        parentRels.put(refTable, cName);
                        if (parentRels.size() >= 3) break;
                    }
                }
            }
        }

        // 2. Discover Child Tables referencing targetTable (DbObjectSearcher first, then columns)
        if (allSchemaTables != null && !isExternalTable(targetTable)) {
            List<TableRelationship> children = DbObjectSearcher.getInstance().findChildRelationships(targetTable.schema, targetTable.tableName);
            if (children != null) {
                for (TableRelationship cr : children) {
                    if (cr.getSourceTable() != null && !cr.getSourceTable().equalsIgnoreCase(targetTable.tableName)) {
                        childRels.put(cr.getSourceTable(), cr.getSourceColumn());
                        if (childRels.size() >= 3) break;
                    }
                }
            }
            for (GenerateTechnicalWordDocs.TableDocModel other : allSchemaTables) {
                if (other.tableName.equalsIgnoreCase(targetTable.tableName) || isExternalTable(other) || childRels.containsKey(other.tableName)) continue;
                for (GenerateTechnicalWordDocs.TableColumnDoc col : other.columns) {
                    if (inferReferencedTable(targetTable.schema, col.columnName.toUpperCase(), Collections.singleton(targetTable.tableName.toUpperCase())) != null) {
                        childRels.put(other.tableName, col.columnName);
                        if (childRels.size() >= 3) break;
                    }
                }
                if (childRels.size() >= 3) break;
            }
        }

        // 3. Discover Sibling cluster tables that ACTUALLY share meaningful business keys with targetTable
        Set<String> clusterTables = new LinkedHashSet<>();
        if (parentRels.size() + childRels.size() < 3 && allSchemaTables != null && !isExternalTable(targetTable)) {
            for (GenerateTechnicalWordDocs.TableDocModel other : allSchemaTables) {
                if (other.tableName.equalsIgnoreCase(targetTable.tableName)
                        || isExternalTable(other)
                        || isConfigOrParameterTable(other)
                        || parentRels.containsKey(other.tableName.toUpperCase())
                        || childRels.containsKey(other.tableName.toUpperCase())) {
                    continue;
                }
                JoinDescriptor jd = resolveJoin(targetTable, other);
                if (jd != null && jd.hasSharedKey()) {
                    clusterTables.add(other.tableName);
                    if (parentRels.size() + childRels.size() + clusterTables.size() >= 3) break;
                }
            }
            if (parentRels.isEmpty() && childRels.isEmpty() && clusterTables.isEmpty()) {
                for (GenerateTechnicalWordDocs.TableDocModel other : allSchemaTables) {
                    if (other.tableName.equalsIgnoreCase(targetTable.tableName)
                            || isExternalTable(other)
                            || isConfigOrParameterTable(other)) {
                        continue;
                    }
                    clusterTables.add(other.tableName);
                    if (clusterTables.size() >= 2) break;
                }
            }
        }

        // 4. Render MS Access style join connectors matching exact join semantics (-- or -> or <- or <->)
        for (Map.Entry<String, String> p : parentRels.entrySet()) {
            GenerateTechnicalWordDocs.TableDocModel pModel = tableMap.get(p.getKey().toUpperCase());
            String pSchema = (pModel != null && pModel.schema != null && !pModel.schema.isEmpty()) ? pModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, p.getKey());
            String pTableId = sanitizeName(pSchema + "_" + p.getKey());
            JoinDescriptor pJd = resolveJoin(pModel != null ? pModel : createSyntheticModel(pSchema, p.getKey()), targetTable);
            appendFlowchartEdge(sb, pTableId, targetName, pJd);
        }
        for (Map.Entry<String, String> c : childRels.entrySet()) {
            GenerateTechnicalWordDocs.TableDocModel cModel = tableMap.get(c.getKey().toUpperCase());
            String cSchema = (cModel != null && cModel.schema != null && !cModel.schema.isEmpty()) ? cModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, c.getKey());
            String cTableId = sanitizeName(cSchema + "_" + c.getKey());
            JoinDescriptor cJd = resolveJoin(targetTable, cModel != null ? cModel : createSyntheticModel(cSchema, c.getKey()));
            appendFlowchartEdge(sb, targetName, cTableId, cJd);
        }
        for (String cl : clusterTables) {
            GenerateTechnicalWordDocs.TableDocModel clModel = tableMap.get(cl.toUpperCase());
            String clSchema = (clModel != null && clModel.schema != null && !clModel.schema.isEmpty()) ? clModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, cl);
            String clTableId = sanitizeName(clSchema + "_" + cl);
            JoinDescriptor jd = resolveJoin(targetTable, clModel != null ? clModel : createSyntheticModel(clSchema, cl));
            if (jd != null && jd.hasSharedKey()) {
                appendFlowchartEdge(sb, targetName, clTableId, jd);
            } else {
                String clPk = (clModel != null && clModel.primaryKeys != null && !clModel.primaryKeys.isEmpty())
                        ? clModel.primaryKeys.get(0)
                        : (clModel != null && clModel.columns != null && !clModel.columns.isEmpty() ? clModel.columns.get(0).columnName : "ID");
                String relLabel = targetPrimaryCol + " -- " + clPk;
                JoinDescriptor fallbackJd = new JoinDescriptor(relLabel, "}o--o{", JoinConnectorType.UNDIRECTED, false, false, true);
                appendFlowchartEdge(sb, targetName, clTableId, fallbackJd);
            }
        }
        if (parentRels.isEmpty() && childRels.isEmpty() && clusterTables.isEmpty()) {
            sb.append("    %% Standalone enterprise entity without explicit foreign keys in catalog\n");
        }

        // 5. Render Entity Window Cards with explicit Key & Constraint columns
        // Parent entities rendered first (top layer)
        for (String p : parentRels.keySet()) {
            GenerateTechnicalWordDocs.TableDocModel pModel = tableMap.get(p.toUpperCase());
            String pSchema = (pModel != null && pModel.schema != null && !pModel.schema.isEmpty()) ? pModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, p);
            renderFlowchartTableCard(sb, sanitizeName(pSchema + "_" + p), pSchema + "." + p.toUpperCase(), pModel, false);
        }

        // Target focal entity rendered
        renderFlowchartTableCard(sb, targetName, targetDisplay, targetTable, true);

        // Child and cluster entities rendered (bottom layer)
        for (String c : childRels.keySet()) {
            GenerateTechnicalWordDocs.TableDocModel cModel = tableMap.get(c.toUpperCase());
            String cSchema = (cModel != null && cModel.schema != null && !cModel.schema.isEmpty()) ? cModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, c);
            renderFlowchartTableCard(sb, sanitizeName(cSchema + "_" + c), cSchema + "." + c.toUpperCase(), cModel, false);
        }
        for (String cl : clusterTables) {
            GenerateTechnicalWordDocs.TableDocModel clModel = tableMap.get(cl.toUpperCase());
            String clSchema = (clModel != null && clModel.schema != null && !clModel.schema.isEmpty()) ? clModel.schema.toUpperCase() : inferSchemaForTable(targetSchema, cl);
            renderFlowchartTableCard(sb, sanitizeName(clSchema + "_" + cl), clSchema + "." + cl.toUpperCase(), clModel, false);
        }

        String result = sb.toString();
        return result;
    }

    /**
     * Renders an MS Access-style table window card in Mermaid flowchart format using the Florida Lottery Plum Theme,
     * displaying schema-qualified table header, 4 explicit column headers (Name, Type, Key, Constraint),
     * syntax-colored data types (blue numbers, purple varchars, amber dates), PK/FK badges, and constraints.
     * All internal newlines are omitted to eliminate the empty space / blank lines above tables.
     */
    private static void renderFlowchartTableCard(
            StringBuilder sb,
            String tableId,
            String displayName,
            GenerateTechnicalWordDocs.TableDocModel model,
            boolean isTarget) {

        boolean isExt = isExternalTable(model);
        String headerTitle = displayName + (isTarget ? (isExt ? " [EXT FILE]" : " [TARGET]") : (isExt ? " [EXT]" : ""));
        String headerBg = isTarget ? "#5C1D48" : "#7A2A60"; // Royal Plum for Target, Medium Plum for Related
        String borderStroke = isTarget ? "2.5px solid #DAA520" : "1.5px solid #5C1D48";
        String subheaderBg = "#F5EEF8"; // Soft Plum Lavender Tint
        String subheaderText = "#3B102E"; // Deep Dark Plum

        StringBuilder card = new StringBuilder();
        card.append("<table border='1' cellspacing='0' cellpadding='3' style='border-collapse:collapse;border:").append(borderStroke).append(";font-family:Segoe UI, sans-serif;font-size:11px;background-color:#FFFFFF;margin:0;'>");
        card.append("<tr bgcolor='").append(headerBg).append("' style='background-color:").append(headerBg).append(" !important;'>");
        card.append("<th colspan='4' bgcolor='").append(headerBg).append("' style='background-color:").append(headerBg).append(" !important;color:#FFFFFF !important;fill:#FFFFFF !important;-webkit-text-fill-color:#FFFFFF !important;text-align:center;padding:7px 8px;font-size:12px;font-weight:bold;letter-spacing:0.5px;'>");
        card.append("<span style='color:#FFFFFF !important;fill:#FFFFFF !important;-webkit-text-fill-color:#FFFFFF !important;font-weight:bold;font-size:12px;'><font color='#FFFFFF'><b style='color:#FFFFFF !important;fill:#FFFFFF !important;-webkit-text-fill-color:#FFFFFF !important;text-shadow:0 1px 2px rgba(0,0,0,0.5);'>").append(headerTitle).append("</b></font></span>");
        card.append("</th></tr>");
        card.append("<tr bgcolor='").append(subheaderBg).append("' style='background-color:").append(subheaderBg).append(" !important;color:").append(subheaderText).append(" !important;font-weight:bold;font-size:11px;'>");
        card.append("<th align='left' bgcolor='").append(subheaderBg).append("' style='background-color:").append(subheaderBg).append(" !important;color:").append(subheaderText).append(" !important;padding:3px 6px;'>Name</th>");
        card.append("<th align='left' bgcolor='").append(subheaderBg).append("' style='background-color:").append(subheaderBg).append(" !important;color:").append(subheaderText).append(" !important;padding:3px 6px;'>Type</th>");
        card.append("<th align='center' bgcolor='").append(subheaderBg).append("' style='background-color:").append(subheaderBg).append(" !important;color:").append(subheaderText).append(" !important;padding:3px 6px;'>Key</th>");
        card.append("<th align='left' bgcolor='").append(subheaderBg).append("' style='background-color:").append(subheaderBg).append(" !important;color:").append(subheaderText).append(" !important;padding:3px 6px;'>Constraint</th>");
        card.append("</tr>");

        if (model != null && model.columns != null && !model.columns.isEmpty()) {
            List<GenerateTechnicalWordDocs.TableColumnDoc> displayCols = selectDisplayColumns(model, UNIFORM_TABLE_COLS);
            int totalCols = model.columns.size();
            boolean hasMore = totalCols > displayCols.size();

            for (GenerateTechnicalWordDocs.TableColumnDoc col : displayCols) {
                boolean isPk = isPrimaryKey(model, col);
                boolean isFk = !isExternalTable(model) && !isPk && isForeignKey(model, col);
                String keyPart = isPk ? "PK" : (isFk ? "FK" : "");
                String dt = sanitizeDataType(col.dataType);
                String dtColorHex = getDataTypeColorHex(col.dataType);
                String constraintLabel = getShortConstraintLabel(model, col);

                card.append("<tr>");
                card.append("<td align='left' style='padding:2px 6px;'>").append(isPk ? "<b>" : "").append(sanitizeColumn(col.columnName)).append(isPk ? "</b>" : "").append("</td>");
                card.append("<td align='left' style='color:#").append(dtColorHex).append(";font-family:Consolas,monospace;font-weight:bold;padding:2px 6px;'><font color='#").append(dtColorHex).append("'><b>").append(dt).append("</b></font></td>");
                card.append("<td align='center' style='padding:2px 6px;").append(isPk ? "color:#DAA520;font-weight:bold;" : (isFk ? "color:#166534;font-weight:bold;" : "")).append("'>").append(keyPart).append("</td>");
                card.append("<td align='left' style='padding:2px 6px;color:#166534;'>").append(constraintLabel).append("</td>");
                card.append("</tr>");
            }
            if (hasMore) {
                int moreCount = totalCols - displayCols.size();
                card.append("<tr><td colspan='4' align='center' style='color:#64748B;font-style:italic;padding:2px 6px;'><i>+").append(moreCount).append(" more columns</i></td></tr>");
            }
        } else {
            card.append("<tr><td align='left'><b>id</b></td><td style='color:#1D4ED8;font-weight:bold;'><font color='#1D4ED8'><b>NUMBER</b></font></td><td align='center' style='color:#DAA520;font-weight:bold;'>PK</td><td style='color:#166534;'>PK, Not Null</td></tr>");
            card.append("<tr><td align='left'>code</td><td style='color:#7E22CE;font-weight:bold;'><font color='#7E22CE'><b>VARCHAR2</b></font></td><td align='center'></td><td style='color:#166534;'>Not Null</td></tr>");
        }
        card.append("</table>");

        sb.append("    ").append(tableId).append("[\"").append(card.toString()).append("\"]\n");
        sb.append("    style ").append(tableId).append(" fill:transparent,stroke:none\n\n");
    }

    // Uniform column display budget across ALL entities (Target, Parents, Children, Cluster) - Bumped to 9
    public static final int UNIFORM_TABLE_COLS = 9;

    /**
     * Checks if a column is a primary key for the table model.
     */
    public static boolean isPrimaryKey(GenerateTechnicalWordDocs.TableDocModel model, GenerateTechnicalWordDocs.TableColumnDoc col) {
        if (model == null || col == null || col.columnName == null) {
            boolean result = false;
            return result;
        }
        if (model.primaryKeys != null) {
            for (String pk : model.primaryKeys) {
                if (col.columnName.equalsIgnoreCase(pk)) {
                    boolean result = true;
                    return result;
                }
            }
        }
        boolean result = false;
        return result;
    }

    /**
     * Checks if a column is a foreign key (either physical constraint, catalog relationship, or referential key).
     */
    public static boolean isForeignKey(GenerateTechnicalWordDocs.TableDocModel model, GenerateTechnicalWordDocs.TableColumnDoc col) {
        if (model == null || col == null || col.columnName == null) {
            boolean result = false;
            return result;
        }
        if (isExternalTable(model)) {
            boolean result = false;
            return result;
        }
        if (isPhysicalForeignKey(col)) {
            boolean result = true;
            return result;
        }
        if (col.comments != null) {
            String comm = col.comments.toUpperCase();
            if (comm.contains("FK") || comm.contains("FOREIGN KEY") || comm.contains("REFERENCES")) {
                boolean result = true;
                return result;
            }
        }
        // Check DbObjectSearcher relationships
        try {
            List<TableRelationship> rels = DbObjectSearcher.getInstance().findRelationshipsForTable(model.schema, model.tableName);
            if (rels != null) {
                for (TableRelationship rel : rels) {
                    if (rel.getSourceTable().equalsIgnoreCase(model.tableName) && col.columnName.equalsIgnoreCase(rel.getSourceColumn())) {
                        boolean result = true;
                        return result;
                    }
                }
            }
        } catch (Throwable ignored) {}

        boolean result = false;
        return result;
    }

    /**
     * Intelligently selects columns to display in entity diagram cards up to the specified budget,
     * guaranteeing that ALL Primary Key (PK) and Foreign Key (FK) columns are ALWAYS displayed.
     *
     * @param model Table documentation model
     * @param budget Nominal column budget (e.g. 9)
     * @return Ordered list of prioritized columns to display
     */
    public static List<GenerateTechnicalWordDocs.TableColumnDoc> selectDisplayColumns(
            GenerateTechnicalWordDocs.TableDocModel model,
            int budget) {
        if (model == null || model.columns == null || model.columns.isEmpty()) {
            List<GenerateTechnicalWordDocs.TableColumnDoc> result = Collections.emptyList();
            return result;
        }

        List<GenerateTechnicalWordDocs.TableColumnDoc> allCols = model.columns;
        int totalCols = allCols.size();

        // If total columns is within budget + 1 (e.g. 10 when budget is 9), show all columns directly
        // without wasting a row on a "+1 more column" overflow indicator.
        if (totalCols <= budget + 1) {
            List<GenerateTechnicalWordDocs.TableColumnDoc> result = new ArrayList<>(allCols);
            return result;
        }

        List<GenerateTechnicalWordDocs.TableColumnDoc> pkCols = new ArrayList<>();
        List<GenerateTechnicalWordDocs.TableColumnDoc> fkCols = new ArrayList<>();
        List<GenerateTechnicalWordDocs.TableColumnDoc> otherCols = new ArrayList<>();

        for (GenerateTechnicalWordDocs.TableColumnDoc c : allCols) {
            if (isPrimaryKey(model, c)) {
                pkCols.add(c);
            } else if (isForeignKey(model, c)) {
                fkCols.add(c);
            } else {
                otherCols.add(c);
            }
        }

        List<GenerateTechnicalWordDocs.TableColumnDoc> selected = new ArrayList<>();
        // Priority 1: ALWAYS include all Primary Keys
        selected.addAll(pkCols);

        // Priority 2: ALWAYS include all Foreign Keys
        selected.addAll(fkCols);

        // Priority 3: Fill remaining slots up to budget with ordinal non-key columns
        int remainingSlots = budget - selected.size();
        if (remainingSlots > 0) {
            for (GenerateTechnicalWordDocs.TableColumnDoc oc : otherCols) {
                if (selected.size() >= budget) break;
                selected.add(oc);
            }
        }

        List<GenerateTechnicalWordDocs.TableColumnDoc> result = selected;
        return result;
    }

    /**
     * Determines whether a table model defines any constraints (Primary Keys, Foreign Keys,
     * Unique Keys, Check constraints, or Not-Null rules).
     * If false, constraint indicators can be cleanly omitted for that entity card.
     */
    public static boolean hasTableConstraints(GenerateTechnicalWordDocs.TableDocModel model) {
        if (model == null || model.columns == null) {
            boolean result = false;
            return result;
        }
        if (model.primaryKeys != null && !model.primaryKeys.isEmpty()) {
            boolean result = true;
            return result;
        }
        for (GenerateTechnicalWordDocs.TableColumnDoc col : model.columns) {
            if (isPrimaryKey(model, col) || isForeignKey(model, col)) {
                boolean result = true;
                return result;
            }
            if (col.constraintType != null && !col.constraintType.trim().isEmpty() && !col.constraintType.trim().equals("-")) {
                boolean result = true;
                return result;
            }
            if (col.isNullable != null && col.isNullable.toUpperCase().contains("NO")) {
                boolean result = true;
                return result;
            }
        }
        boolean result = false;
        return result;
    }

    /**
     * Resolves a concise, short constraint description label for a column
     * (e.g. "Not Null", "Unique", "Check", "FK", "PK") to prevent table cards from becoming too wide.
     */
    public static String getShortConstraintLabel(GenerateTechnicalWordDocs.TableDocModel model, GenerateTechnicalWordDocs.TableColumnDoc col) {
        if (col == null) {
            String result = "";
            return result;
        }

        boolean isPk = isPrimaryKey(model, col);
        boolean isFk = !isExternalTable(model) && !isPk && isForeignKey(model, col);

        String ct = col.constraintType != null ? col.constraintType.trim().toUpperCase() : "";
        boolean isNotNull = col.isNullable != null && col.isNullable.toUpperCase().contains("NO");

        // Primary Key priority
        if (isPk) {
            String result = "PK";
            return result;
        }

        // Check for Unique
        if (ct.contains("UNIQUE") || ct.equals("U")) {
            String result = isNotNull ? "Unique, Not Null" : "Unique";
            return result;
        }

        // Foreign Key
        if (isFk || ct.contains("FOREIGN") || ct.equals("R")) {
            String result = isNotNull ? "FK, Not Null" : "FK";
            return result;
        }

        // Check constraint
        if (ct.contains("CHECK") || ct.equals("C")) {
            String result = isNotNull ? "Check, Not Null" : "Check";
            return result;
        }

        // Mandatory Not Null
        if (isNotNull) {
            String result = "Not Null";
            return result;
        }

        String result = "";
        return result;
    }

    /**
     * Checks if a specific column has an active constraint (PK, FK, Unique, Check, or NOT NULL).
     */
    public static boolean hasColumnConstraint(GenerateTechnicalWordDocs.TableDocModel model, GenerateTechnicalWordDocs.TableColumnDoc col) {
        if (col == null) {
            boolean result = false;
            return result;
        }
        if (isPrimaryKey(model, col) || isForeignKey(model, col)) {
            boolean result = true;
            return result;
        }
        if (col.constraintType != null && !col.constraintType.trim().isEmpty() && !col.constraintType.trim().equals("-")) {
            boolean result = true;
            return result;
        }
        if (col.isNullable != null && col.isNullable.toUpperCase().contains("NO")) {
            boolean result = true;
            return result;
        }
        boolean result = false;
        return result;
    }

    /**
     * Renders a single entity block in Mermaid erDiagram with uniform styling, explicit Key column,
     * and a dedicated short constraint description column across all tables.
     */
    private static void renderEntityBlock(
            StringBuilder sb,
            String safeName,
            String displayName,
            GenerateTechnicalWordDocs.TableDocModel model,
            boolean isTarget) {

        boolean isExt = isExternalTable(model);
        String entityHeader = isTarget
                ? (displayName + (isExt ? " [EXT FILE]" : " [TARGET]"))
                : (displayName + (isExt ? " [EXT]" : ""));
        sb.append("\n    ").append(safeName).append("[\"").append(entityHeader).append("\"] {\n");
        if (model != null && !model.columns.isEmpty()) {
            List<GenerateTechnicalWordDocs.TableColumnDoc> displayCols = selectDisplayColumns(model, UNIFORM_TABLE_COLS);
            int totalCols = model.columns.size();
            boolean hasMore = totalCols > displayCols.size();

            for (GenerateTechnicalWordDocs.TableColumnDoc col : displayCols) {
                boolean isPk = isPrimaryKey(model, col);
                boolean isFk = !isExternalTable(model) && !isPk && isForeignKey(model, col);
                String dt = sanitizeDataType(col.dataType);
                String colName = formatConsistentColumn(col.columnName);

                // Explicit Key token: PK or FK
                String keyToken = isPk ? " PK" : (isFk ? " FK" : "");

                // Short, clean constraint description (e.g. "Not Null", "Unique", "PK", "FK")
                String constraintLabel = getShortConstraintLabel(model, col);
                String commentText = constraintLabel.isEmpty() ? "-" : constraintLabel;

                sb.append("        ").append(colName).append(" ").append(dt).append(keyToken)
                        .append(" \"").append(commentText).append("\"\n");
            }
            if (hasMore) {
                int moreCount = totalCols - displayCols.size();
                String moreText = "+" + moreCount + (moreCount == 1 ? " more column" : " more columns");
                sb.append("        more_cols EXTRA \"").append(moreText).append("\"\n");
            }
            if (model.triggers != null && !model.triggers.isEmpty() && displayCols.size() < UNIFORM_TABLE_COLS) {
                int tCount = 0;
                for (GenerateTechnicalWordDocs.TableTriggerDoc trg : model.triggers) {
                    if (tCount++ >= 1) break;
                    String ev = (trg.triggeringEvent != null && !trg.triggeringEvent.isEmpty()) ? trg.triggeringEvent : "TRIGGER";
                    sb.append("        ").append(formatConsistentColumn(trg.triggerName)).append(" TRIGGER \"TRG: ").append(ev).append("\"\n");
                }
            }
        } else {
            // Consistent fallback with 9 enterprise rows and clear concise constraints
            sb.append("        id NUMBER PK \"PK, Not Null\"\n");
            sb.append("        code VARCHAR2 \"Not Null\"\n");
            sb.append("        name VARCHAR2 \"-\"\n");
            sb.append("        status VARCHAR2 \"Not Null\"\n");
            sb.append("        created_date DATE \"Not Null\"\n");
            sb.append("        updated_date DATE \"-\"\n");
            sb.append("        parent_id NUMBER FK \"FK, Not Null\"\n");
            sb.append("        batch_id NUMBER FK \"FK, Not Null\"\n");
            sb.append("        audit_user VARCHAR2 \"-\"\n");
        }
        sb.append("    }\n");
    }

    /**
     * Formats column names with a consistent maximum length and clean enterprise abbreviation
     * so that outlier columns (e.g. organization_levels_2_5) do not distort entity box widths.
     */
    private static String formatConsistentColumn(String colName) {
        if (colName == null) {
            String result = "col";
            return result;
        }
        String clean = sanitizeColumn(colName);
        if (clean.length() > 18) {
            clean = clean.replace("organization_", "org_")
                    .replace("transaction_", "txn_")
                    .replace("description_", "desc_")
                    .replace("administrator_", "admin_")
                    .replace("reference_", "ref_")
                    .replace("category_", "cat_");
            if (clean.length() > 18) {
                clean = clean.substring(0, 17) + "_";
            }
        }
        String result = clean;
        return result;
    }

    /**
     * Generates a comprehensive Option 1 Mermaid ER Diagram for a volume of schema tables.
     */
    public static String generateVolumeErDiagram(String schema, List<GenerateTechnicalWordDocs.TableDocModel> tables) {
        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: {\n");
        sb.append("  'theme': 'base',\n");
        sb.append("  'themeVariables': {\n");
        sb.append("    'primaryColor': '#F5EEF8',\n");
        sb.append("    'primaryTextColor': '#3B102E',\n");
        sb.append("    'primaryBorderColor': '#5C1D48',\n");
        sb.append("    'lineColor': '#5C1D48',\n");
        sb.append("    'textColor': '#3B102E',\n");
        sb.append("    'fontSize': '12px',\n");
        sb.append("    'fontFamily': 'Segoe UI, Arial, sans-serif'\n");
        sb.append("  }\n");
        sb.append("}}%%\n");
        sb.append("flowchart TD\n\n");

        if (tables == null || tables.isEmpty()) {
            sb.append("    DUAL[\"<b>SYS.DUAL</b>\"]\n");
            String result = sb.toString();
            return result;
        }

        Map<String, GenerateTechnicalWordDocs.TableDocModel> tableMap = new HashMap<>();
        for (GenerateTechnicalWordDocs.TableDocModel t : tables) {
            tableMap.put(t.tableName.toUpperCase(), t);
        }

        Set<String> relationships = new LinkedHashSet<>();

        for (GenerateTechnicalWordDocs.TableDocModel child : tables) {
            if (isExternalTable(child)) continue; // Flat files do not enforce physical foreign keys
            String childName = sanitizeName(child.tableName);
            Set<String> pkCols = new HashSet<>(child.primaryKeys != null ? child.primaryKeys : Collections.emptyList());

            for (GenerateTechnicalWordDocs.TableColumnDoc col : child.columns) {
                if (pkCols.contains(col.columnName)) continue;
                String cName = col.columnName.toUpperCase();

                String referencedTable = inferReferencedTable(schema, cName, tableMap.keySet());
                if (referencedTable != null && tableMap.containsKey(referencedTable)) {
                    GenerateTechnicalWordDocs.TableDocModel parentTable = tableMap.get(referencedTable);
                    if (parentTable == null || !isExternalTable(parentTable)) {
                        String parentName = sanitizeName(referencedTable);
                        if (!parentName.equals(childName)) {
                            relationships.add("    " + parentName + " -->|\"" + cName + " [FK]\"| " + childName);
                        }
                    }
                }
            }
        }

        int relCount = 0;
        for (String rel : relationships) {
            if (relCount++ >= 6) break;
            sb.append(rel).append("\n");
        }
        if (relationships.isEmpty() && tables.size() > 1) {
            GenerateTechnicalWordDocs.TableDocModel root = tables.get(0);
            String rootName = sanitizeName(root.tableName);
            String rootCol = (root.primaryKeys != null && !root.primaryKeys.isEmpty())
                    ? root.primaryKeys.get(0)
                    : (root.columns != null && !root.columns.isEmpty() ? root.columns.get(0).columnName : "ID");

            for (int i = 1; i < Math.min(tables.size(), 4); i++) {
                GenerateTechnicalWordDocs.TableDocModel child = tables.get(i);
                JoinDescriptor jd = resolveJoin(root, child);
                String childName = sanitizeName(child.tableName);
                if (jd != null && jd.hasSharedKey()) {
                    appendFlowchartEdge(sb, rootName, childName, jd);
                } else {
                    String childCol = (child.primaryKeys != null && !child.primaryKeys.isEmpty())
                            ? child.primaryKeys.get(0)
                            : (child.columns != null && !child.columns.isEmpty() ? child.columns.get(0).columnName : "ID");
                    String label = rootCol + " -- " + childCol;
                    JoinDescriptor fallbackJd = new JoinDescriptor(label, "}o--o{", JoinConnectorType.UNDIRECTED, false, false, true);
                    appendFlowchartEdge(sb, rootName, childName, fallbackJd);
                }
            }
        }

        int maxTables = Math.min(tables.size(), 6);
        for (int i = 0; i < maxTables; i++) {
            GenerateTechnicalWordDocs.TableDocModel t = tables.get(i);
            String tName = sanitizeName(t.tableName);
            String sUpper = (t.schema != null && !t.schema.isEmpty()) ? t.schema.toUpperCase() : schema.toUpperCase();
            renderFlowchartTableCard(sb, tName, sUpper + "." + t.tableName, t, i == 0);
        }

        String result = sb.toString();
        return result;
    }

    /**
     * Determines whether a table model represents an external flat-file table
     * (e.g. *_EXT, *_HDR_EXT, tableType containing EXTERNAL, or explicit isExternal flag).
     * External tables do not enforce physical foreign keys or relational constraints in Oracle.
     */
    public static boolean isExternalTable(GenerateTechnicalWordDocs.TableDocModel tbl) {
        boolean result = ExternalTablePipelineDiagramHelper.isExternalOrStagingTable(tbl);
        return result;
    }

    /**
     * Identifies application configuration and parameter lookup tables (e.g. *CFG, *CONFIG, *PARAM)
     * which must not be automatically clustered as relational entities.
     */
    public static boolean isConfigOrParameterTable(GenerateTechnicalWordDocs.TableDocModel tbl) {
        if (tbl == null || tbl.tableName == null) {
            boolean result = false;
            return result;
        }
        String upper = tbl.tableName.toUpperCase();
        boolean result = upper.endsWith("CFG") || upper.endsWith("_CFG") || upper.contains("CONFIG")
                || upper.endsWith("_PARAM") || upper.endsWith("_PARAMS") || upper.contains("TEMPLATE")
                || upper.endsWith("_CTL") || upper.endsWith("_CONTROL");
        return result;
    }

    /**
     * Checks if a column has an actual physical foreign key defined in the database catalog.
     * Prevents wild heuristic naming assumptions from manufacturing fictitious relationships.
     */
    public static boolean isPhysicalForeignKey(GenerateTechnicalWordDocs.TableColumnDoc col) {
        if (col == null || col.constraintType == null) {
            boolean result = false;
            return result;
        }
        String ct = col.constraintType.trim().toUpperCase();
        boolean result = ct.equals("R") || ct.contains("FK") || ct.contains("FOREIGN");
        return result;
    }

    /**
     * Strictly verifies whether a column represents a legitimate parent entity relationship.
     * Prevents heuristic hallucination:
     *  1. External flat-file tables (*_EXT) are strictly prohibited from inferring physical FKs.
     *  2. Requires verified existence in knownTables dictionary (never returns speculative default fallbacks).
     *  3. Matches the exact base table name before checking conventional *_MASTER or *_CONTROL suffixes.
     */
    private static String inferReferencedTable(String schema, String columnName, Set<String> knownTables) {
        if (columnName == null || knownTables == null || knownTables.isEmpty()) {
            String result = null;
            return result;
        }

        String upper = columnName.toUpperCase();

        // Specific high-frequency Florida Lottery master entity patterns with strict existence verification
        if (upper.startsWith("RETAILER_") || upper.startsWith("RET_")) {
            if (knownTables.contains("RETAILER_MASTER")) { String result = "RETAILER_MASTER"; return result; }
            if (knownTables.contains("RETAILER")) { String result = "RETAILER"; return result; }
            if (knownTables.contains("RETAILERS")) { String result = "RETAILERS"; return result; }
        }
        if (upper.startsWith("GAME_")) {
            if (knownTables.contains("GAME_MASTER")) { String result = "GAME_MASTER"; return result; }
            if (knownTables.contains("GAME")) { String result = "GAME"; return result; }
        }
        if (upper.startsWith("BATCH_")) {
            if (knownTables.contains("BATCH_CONTROL")) { String result = "BATCH_CONTROL"; return result; }
            if (knownTables.contains("BATCH_HEADER")) { String result = "BATCH_HEADER"; return result; }
        }
        if (upper.startsWith("PACK_")) {
            if (knownTables.contains("PACK_MASTER")) { String result = "PACK_MASTER"; return result; }
        }
        if (upper.startsWith("DRAW_")) {
            if (knownTables.contains("DRAW_MASTER")) { String result = "DRAW_MASTER"; return result; }
        }
        if (upper.startsWith("USER_")) {
            if (knownTables.contains("USER_MASTER")) { String result = "USER_MASTER"; return result; }
        }
        if (upper.startsWith("PERM_") || upper.startsWith("LTR_")) {
            if (knownTables.contains("ARTCLTR_MASTER")) { String result = "ARTCLTR_MASTER"; return result; }
            if (knownTables.contains("ARS_COLLECTION")) { String result = "ARS_COLLECTION"; return result; }
            if (knownTables.contains("ARTCLTR_HEADER")) { String result = "ARTCLTR_HEADER"; return result; }
        }

        // Strict suffix inspection: only resolve if the candidate table actually exists in knownTables!
        if (upper.endsWith("_ID") || upper.endsWith("_NBR") || upper.endsWith("_CD")) {
            String base = upper.replaceAll("_(ID|NBR|CD)$", "");
            if (knownTables.contains(base)) { String result = base; return result; }
            if (knownTables.contains(base + "_MASTER")) { String result = base + "_MASTER"; return result; }
            if (knownTables.contains(base + "_CONTROL")) { String result = base + "_CONTROL"; return result; }
            if (knownTables.contains(base + "_HEADER")) { String result = base + "_HEADER"; return result; }
        }

        String result = null;
        return result;
    }

    public static String inferSchemaForTable(String currentSchema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            String result = currentSchema != null ? currentSchema.toUpperCase() : "SCHEMA";
            return result;
        }
        // Direct qualified identifier parsing (e.g. "ARS.RETAILER_MASTER" -> "ARS")
        if (tableName.contains(".")) {
            String result = tableName.substring(0, tableName.indexOf(".")).toUpperCase();
            return result;
        }
        String upper = tableName.toUpperCase();
        if (upper.startsWith("GAME_") || upper.startsWith("DRAW_") || upper.startsWith("PACK_") || upper.startsWith("WINNER_") || upper.startsWith("TICKET_")) {
            String result = "GAME";
            return result;
        }
        if (upper.startsWith("INET_") || upper.startsWith("USER_") || upper.startsWith("PORTAL_") || upper.startsWith("WEB_")) {
            String result = "INET";
            return result;
        }
        if (upper.startsWith("LMS_")) {
            String result = "LMS";
            return result;
        }
        if (upper.startsWith("SWPT_") || upper.startsWith("PROMO_") || upper.startsWith("SECOND_CHANCE_")) {
            String result = "SWPT";
            return result;
        }
        if (upper.startsWith("PPS_") || upper.startsWith("PLAYER_")) {
            String result = "PPS";
            return result;
        }
        if (upper.startsWith("WRS_")) {
            String result = "WRS";
            return result;
        }
        if (upper.startsWith("SYS_") || upper.startsWith("SEC_") || upper.startsWith("DUAL")) {
            String result = "SYS";
            return result;
        }
        if (upper.startsWith("FIN_") || upper.startsWith("PAYMENT_") || upper.startsWith("VOUCHER_")) {
            String result = "FINANCE";
            return result;
        }
        if (upper.startsWith("ARS_") || upper.startsWith("RETAILER_") || upper.startsWith("EFT_") || upper.startsWith("LOCATION_")) {
            String result = "ARS";
            return result;
        }
        if (upper.startsWith("GL_") || upper.startsWith("JOURNAL_") || upper.startsWith("ACCOUNT_")) {
            String result = "GL";
            return result;
        }
        if (upper.startsWith("DWDOL_") || upper.startsWith("DW_")) {
            String result = "DWDOL";
            return result;
        }
        if (upper.startsWith("TRANS_") || upper.startsWith("TRANSLATOR_")) {
            String result = "TRANSLATOR";
            return result;
        }
        String result = (currentSchema != null && !currentSchema.isEmpty()) ? currentSchema.toUpperCase() : "SCHEMA";
        return result;
    }

    private static String sanitizeName(String str) {
        String result = str.replaceAll("[^a-zA-Z0-9_]", "_");
        return result;
    }

    private static String sanitizeColumn(String str) {
        String result = str.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        return result;
    }

    private static String sanitizeDataType(String dt) {
        if (dt == null || dt.isEmpty()) return "VARCHAR2";
        String upper = dt.toUpperCase();
        if (upper.startsWith("VARCHAR")) return "VARCHAR2";
        if (upper.startsWith("NUM") || upper.startsWith("INT") || upper.startsWith("FLOAT")) return "NUMBER";
        if (upper.startsWith("DATE") || upper.startsWith("TIME")) return "DATE";
        if (upper.startsWith("CHAR")) return "CHAR";
        if (upper.startsWith("CLOB") || upper.startsWith("BLOB")) return "LOB";
        String result = upper.split("[\\s(]")[0];
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        String result = s.substring(0, max - 2) + "..";
        return result;
    }

    // =========================================================================
    // 2. DOCUMENT EMBEDDING & SECTION BUILDERS
    // =========================================================================

    /**
     * Renders the Volume / Schema Master Entity-Relationship Diagram Section.
     */
    public static void renderVolumeErDiagramSection(
            XWPFDocument doc,
            String schema,
            String volumeLabel,
            List<GenerateTechnicalWordDocs.TableDocModel> tables) {

        addCallout(doc, "RELATIONAL DATA ARCHITECTURE & TOPOLOGY:",
                "Illustrates Crow's Foot Foreign Key referential integrity constraints, parent master entities, and dependent transaction child tables across " +
                        tables.size() + " tables in " + schema.toUpperCase() + (volumeLabel != null ? " [" + volumeLabel + "]" : "") + ".");

        boolean rendered = false;
        try {
            String mermaidEr = generateVolumeErDiagram(schema, tables);
            String caption = "Figure: Entity-Relationship (ER) Crow's Foot Data Model — " + schema.toUpperCase() + (volumeLabel != null ? " (" + volumeLabel + ")" : "");
            rendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidEr, 525, 500, caption);
        } catch (Throwable t) {
            System.err.println("INFO: Mermaid ER render bypassed (" + t.getMessage() + "), using Java2D fallback.");
        }

        if (!rendered) {
            embedJava2DVolumeErDiagram(doc, schema, volumeLabel, tables);
        }
        // renderRelationshipLegendCallout(doc); // Omitted for now per design requirement
    }

    /**
     * Renders an individual table's 1-Hop focused relational ER diagram with micro-headers & color-coded types.
     */
    public static void renderIndividualTableErDiagram(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel tbl,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        if (isExternalTable(tbl)) {
            ExternalTablePipelineDiagramHelper.renderExternalTablePipelineDiagram(doc, tbl, allSchemaTables);
            return;
        }

        boolean rendered = false;
        try {
            String mermaidEr = generateSingleTableRelationalContext(tbl, allSchemaTables);
            String caption = "Figure: Column-Relationship Diagram — " + tbl.schema.toUpperCase() + "." + tbl.tableName;
            rendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidEr, 525, 460, caption);
        } catch (Throwable t) {
            System.err.println("INFO: Individual table ER render fallback: " + t.getMessage());
        }

        if (!rendered) {
            embedJava2DSingleTableRelationalErd(doc, tbl, allSchemaTables);
        }
        // renderRelationshipLegendCallout(doc); // Omitted for now per design requirement
    }

    /**
     * Renders an individual table's Table-Relationship Diagram.
     */
    public static void renderMsAccessWorldViewErDiagram(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel tbl,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        if (isExternalTable(tbl)) {
            ExternalTablePipelineDiagramHelper.renderExternalTablePipelineDiagram(doc, tbl, allSchemaTables);
            return;
        }

        addCallout(doc, "TABLE-RELATIONSHIP RELATIONAL ARCHITECTURE:",
                "Renders " + tbl.schema.toUpperCase() + "." + tbl.tableName + " in its expanded relational ecosystem, " +
                        "displaying foreign key links, parent master entities, and dependent transaction child tables using " +
                        "the visual schema architecture format.");

        boolean rendered = false;
        try {
            String mermaidEr = generateMsAccessWorldViewErDiagram(tbl, allSchemaTables);
            String caption = "Figure: Table-Relationship Diagram — " + tbl.schema.toUpperCase() + "." + tbl.tableName;
            rendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidEr, 525, 480, caption);
        } catch (Throwable t) {
            System.err.println("INFO: Table-Relationship ER render fallback: " + t.getMessage());
        }

        if (!rendered) {
            embedJava2DTableRelationshipDiagram(doc, tbl, allSchemaTables);
        }
        // renderRelationshipLegendCallout(doc); // Omitted for now per design requirement
    }

    /**
     * Renders the Java2D Table-Relationship Diagram with parent, target, and child entities,
     * Crow's Foot connectors, schema-qualified table names, and syntax-colored data types.
     */
    private static void embedJava2DTableRelationshipDiagram(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel tbl,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {
        try {
            int width = 840;
            int height = 345;

            double scale = 7.0; // 7.0x Ultra-Retina Supersampling
            BufferedImage img = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);

            setHighQualityRenderingHints(g);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(COLOR_BORDER);
            g.drawRoundRect(2, 2, width - 5, height - 5, 8, 8);

            // Title Banner (Florida Lottery Royal Plum Theme)
            g.setColor(COLOR_PLUM);
            g.fillRoundRect(8, 8, width - 16, 24, 6, 6);
            g.setColor(COLOR_GOLD);
            g.drawRoundRect(8, 8, width - 16, 24, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String schemaPrefix = (tbl.schema != null && !tbl.schema.trim().isEmpty())
                    ? tbl.schema.toUpperCase()
                    : inferSchemaForTable("SCHEMA", tbl.tableName);
            String bannerType = isExternalTable(tbl) ? "EXTERNAL FLAT-FILE TABLE LINEAGE: " : "TABLE-RELATIONSHIP DIAGRAM: ";
            g.drawString(bannerType + schemaPrefix + "." + tbl.tableName.toUpperCase(), 24, 24);

            // Standardized Uniform Card Dimensions & Symmetric Layout (Accommodates 9 columns + overflow)
            int cardW = 240;
            int cardH = 245;
            int cardY = 46;

            int targetW = 240;
            int targetH = 245;
            int targetY = 46;

            int leftX = 18;
            int centerX = 300;
            int rightX = 582;

            Map<String, GenerateTechnicalWordDocs.TableDocModel> tableMap = new HashMap<>();
            if (allSchemaTables != null) {
                for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                    tableMap.put(t.tableName.toUpperCase(), t);
                    DbObjectSearcher.getInstance().registerTable(toSearcherDocModel(t));
                }
            }
            if (tbl != null) {
                DbObjectSearcher.getInstance().registerTable(toSearcherDocModel(tbl));
            }

            // 1. Left: Parent Entity (Lookup / Master) - Mined via DbObjectSearcher
            TableRelationship bestParentRel = !isExternalTable(tbl)
                    ? DbObjectSearcher.getInstance().findBestParentRelationship(tbl.schema, tbl.tableName)
                    : null;

            String parentTableName = bestParentRel != null
                    ? bestParentRel.getTargetTable()
                    : (!isExternalTable(tbl) ? inferParentTableName(tbl, tableMap.keySet()) : null);

            String pSchema = bestParentRel != null
                    ? bestParentRel.getTargetSchema()
                    : (parentTableName != null ? inferSchemaForTable(schemaPrefix, parentTableName) : "ARS");

            GenerateTechnicalWordDocs.TableDocModel parentModel = parentTableName != null ? tableMap.get(parentTableName.toUpperCase()) : null;
            if (parentModel == null && parentTableName != null) {
                TableDocModel found = DbObjectSearcher.getInstance().findTable(pSchema, parentTableName);
                if (found != null) {
                    parentModel = fromSearcherDocModel(found);
                } else {
                    parentModel = createSyntheticModel(pSchema, parentTableName);
                }
            }

            String parentConnectorLabel = "1 : N";
            JoinDescriptor pJd = parentModel != null ? resolveJoin(parentModel, tbl) : null;
            if (bestParentRel != null) {
                String pCol = bestParentRel.getTargetColumn();
                if (pCol == null || pCol.isEmpty()) pCol = bestParentRel.getSourceColumn();
                if (bestParentRel.isEnforced()) {
                    parentConnectorLabel = pCol + " -> " + bestParentRel.getSourceColumn() + " [FK]";
                } else if (bestParentRel.getOccurrences() > 0) {
                    parentConnectorLabel = pCol + " -- " + bestParentRel.getSourceColumn() + " [SQL]";
                } else {
                    parentConnectorLabel = pCol + " -- " + bestParentRel.getSourceColumn();
                }
                pJd = new JoinDescriptor(parentConnectorLabel, "}o--o{", bestParentRel.isEnforced(), bestParentRel.getOccurrences() > 0);
            } else if (parentModel != null) {
                parentConnectorLabel = pJd.label;
            }

            JoinConnectorType parentConnType = pJd != null ? pJd.connectorType : JoinConnectorType.fromSymbol(parentConnectorLabel);
            String parentSrcCol = bestParentRel != null ? bestParentRel.getTargetColumn() : null;
            String targetFromParentCol = bestParentRel != null ? bestParentRel.getSourceColumn() : null;
            int parentPkY = findColumnY(parentModel, parentSrcCol, true, cardY);
            int targetFkY = findColumnY(tbl, targetFromParentCol, false, targetY);

            if (parentModel != null) {
                drawEntityCard(g, leftX, cardY, cardW, cardH, parentModel, false);
                drawMsAccessConnector(g, leftX + cardW + 2, parentPkY, centerX - 2, targetFkY, COLOR_BLUE, parentConnectorLabel, parentConnType);
            } else if (!isExternalTable(tbl)) {
                // Relational table with uncataloged master
                drawEntityCard(g, leftX, cardY, cardW, cardH, createSyntheticModel(pSchema, parentTableName != null ? parentTableName : "RETAILER_MASTER"), false);
                drawMsAccessConnector(g, leftX + cardW + 2, parentPkY, centerX - 2, targetFkY, COLOR_BLUE, parentConnectorLabel, parentConnType);
            }

            // 2. Center: Target Table (High-focus with full column inventory & badges - Noticeably larger!)
            drawEntityCard(g, centerX, targetY, targetW, targetH, tbl, true);

            // 3. Right: Child Entity (Transactions / Dependents) - Mined via DbObjectSearcher
            List<TableRelationship> childRels = !isExternalTable(tbl)
                    ? DbObjectSearcher.getInstance().findChildRelationships(tbl.schema, tbl.tableName)
                    : Collections.emptyList();

            String childTableName = !childRels.isEmpty()
                    ? childRels.get(0).getSourceTable()
                    : (!isExternalTable(tbl) ? inferChildTableName(tbl, allSchemaTables) : null);

            String cSchema = !childRels.isEmpty()
                    ? childRels.get(0).getSourceSchema()
                    : (childTableName != null ? inferSchemaForTable(schemaPrefix, childTableName) : "TRANSLATOR");

            GenerateTechnicalWordDocs.TableDocModel childModel = childTableName != null ? tableMap.get(childTableName.toUpperCase()) : null;
            if (childModel == null && childTableName != null) {
                TableDocModel found = DbObjectSearcher.getInstance().findTable(cSchema, childTableName);
                if (found != null) {
                    childModel = fromSearcherDocModel(found);
                } else {
                    childModel = createSyntheticModel(cSchema, childTableName);
                }
            }

            String childConnectorLabel = "1 : N";
            JoinDescriptor cJd = childModel != null ? resolveJoin(tbl, childModel) : null;
            if (!childRels.isEmpty()) {
                TableRelationship cRel = childRels.get(0);
                String tCol = (tbl.primaryKeys != null && !tbl.primaryKeys.isEmpty()) ? tbl.primaryKeys.get(0) : cRel.getSourceColumn();
                if (cRel.isEnforced()) {
                    childConnectorLabel = tCol + " -> " + cRel.getSourceColumn() + " [FK]";
                } else if (cRel.getOccurrences() > 0) {
                    childConnectorLabel = tCol + " -- " + cRel.getSourceColumn() + " [SQL]";
                } else {
                    childConnectorLabel = tCol + " -- " + cRel.getSourceColumn();
                }
                cJd = new JoinDescriptor(childConnectorLabel, "}o--o{", cRel.isEnforced(), cRel.getOccurrences() > 0);
            } else if (childModel != null) {
                childConnectorLabel = cJd.label;
            }

            JoinConnectorType childConnType = cJd != null ? cJd.connectorType : JoinConnectorType.fromSymbol(childConnectorLabel);
            String targetChildPkCol = (tbl.primaryKeys != null && !tbl.primaryKeys.isEmpty()) ? tbl.primaryKeys.get(0) : null;
            String childFkCol = !childRels.isEmpty() ? childRels.get(0).getSourceColumn() : null;
            int targetChildPkY = findColumnY(tbl, targetChildPkCol, true, targetY);
            int childFkY = findColumnY(childModel, childFkCol, false, cardY);

            if (childModel != null) {
                drawMsAccessConnector(g, centerX + targetW + 2, targetChildPkY, rightX - 2, childFkY, COLOR_GREEN, childConnectorLabel, childConnType);
                drawEntityCard(g, rightX, cardY, cardW, cardH, childModel, false);
            } else if (!isExternalTable(tbl)) {
                drawMsAccessConnector(g, centerX + targetW + 2, targetChildPkY, rightX - 2, childFkY, COLOR_GREEN, childConnectorLabel, childConnType);
                drawEntityCard(g, rightX, cardY, cardW, cardH, createSyntheticModel(cSchema, childTableName != null ? childTableName : "ARTCLTR_AUDIT_LOG"), false);
            }

            // Legend Ribbon at bottom of diagram
            drawLegendBar(g, width, height);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            insertDocPicture(doc, imageBytes, "TableRel_" + tbl.tableName + ".png", 525, (int) Math.round(525.0 * height / width),
                    "Figure: Table-Relationship Diagram — " + schemaPrefix + "." + tbl.tableName.toUpperCase());

        } catch (Exception e) {
            System.err.println("WARN: Failed to render Java2D Table-Relationship diagram: " + e.getMessage());
        }
    }

    public static void renderTableDataFlowAndLineageSection(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel tbl,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {

        boolean rendered = false;
        try {
            String mermaidFlow = generateTableDataFlowAndRoutineLineage(tbl, allSchemaTables);
            String caption = "Figure: Object-Level Data Ingestion & Lineage Topology — " + tbl.schema.toUpperCase() + "." + tbl.tableName;
            rendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidFlow, 525, 360, caption);
        } catch (Throwable t) {
            System.err.println("INFO: Data flow diagram fallback: " + t.getMessage());
        }

        if (!rendered) {
            embedJava2DSingleTableLineage(doc, tbl, allSchemaTables);
        }
    }

    // =========================================================================
    // 3. ULTRA-HIGH QUALITY (3.5x DPI RETINA) JAVA2D VECTOR RENDERERS
    // =========================================================================

    private static void embedJava2DVolumeErDiagram(XWPFDocument doc, String schema, String volumeLabel, List<GenerateTechnicalWordDocs.TableDocModel> tables) {
        try {
            int width = 760;
            int count = Math.min(tables.size(), 6);
            int cols = 2;
            int rows = (count + 1) / 2;
            int cardH = 210;
            int height = 70 + (rows * 225);

            double scale = 7.0; // 7.0x Ultra-Retina Supersampling (Doubled Resolution)
            BufferedImage img = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);

            setHighQualityRenderingHints(g);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(COLOR_BORDER);
            g.drawRoundRect(2, 2, width - 5, height - 5, 8, 8);

            // Title Banner (Florida Lottery Royal Plum Theme)
            g.setColor(COLOR_PLUM);
            g.fillRoundRect(8, 8, width - 16, 28, 6, 6);
            g.setColor(COLOR_GOLD);
            g.drawRoundRect(8, 8, width - 16, 28, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            String title = "SCHEMA RELATIONAL TOPOLOGY: " + schema.toUpperCase() + (volumeLabel != null ? " [" + volumeLabel + "]" : "");
            g.drawString(title, 24, 26);

            int startY = 48;
            int cardW = (width - 40 - 15) / 2;
            for (int i = 0; i < count; i++) {
                GenerateTechnicalWordDocs.TableDocModel t = tables.get(i);
                int c = i % 2;
                int r = i / 2;
                int x = 20 + c * (cardW + 15);
                int y = startY + r * 225;

                drawEntityCard(g, x, y, cardW, cardH, t, false);
            }

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            insertDocPicture(doc, imageBytes, "SchemaEr_" + schema + ".png", 520, (int) Math.round(520.0 * height / width),
                    "Figure: Entity-Relationship Topology — " + schema.toUpperCase() + (volumeLabel != null ? " [" + volumeLabel + "]" : ""));

        } catch (Exception e) {
            System.err.println("WARN: Failed to render Java2D volume ER diagram: " + e.getMessage());
        }
    }

    /**
     * Renders a 1-Hop focused ER Diagram in Java2D with Micro-Headers and Color-Coded Types.
     */
    private static void embedJava2DSingleTableRelationalErd(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel tbl,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {
        try {
            int width = 840;
            int height = 345;

            double scale = 7.0; // 7.0x Ultra-Retina Supersampling (Doubled Resolution)
            BufferedImage img = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);

            setHighQualityRenderingHints(g);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(COLOR_BORDER);
            g.drawRoundRect(2, 2, width - 5, height - 5, 8, 8);

            String schemaPrefix = (tbl.schema != null && !tbl.schema.trim().isEmpty())
                    ? tbl.schema.toUpperCase()
                    : inferSchemaForTable("SCHEMA", tbl.tableName);

            // Title Banner (Florida Lottery Royal Plum Theme)
            g.setColor(COLOR_PLUM);
            g.fillRoundRect(8, 8, width - 16, 24, 6, 6);
            g.setColor(COLOR_GOLD);
            g.drawRoundRect(8, 8, width - 16, 24, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String bannerType = isExternalTable(tbl) ? "EXTERNAL FLAT-FILE SCHEMA: " : "COLUMN-RELATIONSHIP DIAGRAM: ";
            g.drawString(bannerType + schemaPrefix + "." + tbl.tableName.toUpperCase(), 24, 24);

            // Standardized Uniform Card Dimensions & Symmetric Layout (Accommodating 9 columns)
            int cardW = 240;
            int cardH = 245;
            int cardY = 46;

            int targetW = 240;
            int targetH = 245;
            int targetY = 46;

            int leftX = 18;
            int centerX = 300;
            int rightX = 582;

            Map<String, GenerateTechnicalWordDocs.TableDocModel> tableMap = new HashMap<>();
            if (allSchemaTables != null) {
                for (GenerateTechnicalWordDocs.TableDocModel t : allSchemaTables) {
                    tableMap.put(t.tableName.toUpperCase(), t);
                }
            }

            // 1. Left: Parent Entity (Lookup / Master) - Suppressed if external table or no actual parent found
            String parentTableName = !isExternalTable(tbl) ? inferParentTableName(tbl, tableMap.keySet()) : null;
            GenerateTechnicalWordDocs.TableDocModel parentModel = parentTableName != null ? tableMap.get(parentTableName.toUpperCase()) : null;

            int parentPkY = findColumnY(parentModel, null, true, cardY);
            int targetFkY = findColumnY(tbl, null, false, targetY);

            if (parentModel != null) {
                String pSchema = (parentModel.schema != null && !parentModel.schema.isEmpty())
                        ? parentModel.schema.toUpperCase()
                        : inferSchemaForTable(schemaPrefix, parentTableName);
                JoinDescriptor pJd = resolveJoin(parentModel, tbl);
                drawEntityCard(g, leftX, cardY, cardW, cardH, parentModel, false);
                drawMsAccessConnector(g, leftX + cardW + 2, parentPkY, centerX - 2, targetFkY, COLOR_BLUE, pJd.label, pJd.connectorType);
            } else if (!isExternalTable(tbl)) {
                String pSchema = inferSchemaForTable(schemaPrefix, parentTableName != null ? parentTableName : "RETAILER_MASTER");
                drawEntityCard(g, leftX, cardY, cardW, cardH, createSyntheticModel(pSchema, parentTableName != null ? parentTableName : "RETAILER_MASTER"), false);
                drawMsAccessConnector(g, leftX + cardW + 2, parentPkY, centerX - 2, targetFkY, COLOR_BLUE, "RETAILER_ID -> RETAILER_ID", JoinConnectorType.DIRECTED_FORWARD);
            }

            // 2. Center: Target Table (High-focus with full column inventory & badges - Noticeably larger!)
            drawEntityCard(g, centerX, targetY, targetW, targetH, tbl, true);

            // 3. Right: Child Entity (Transactions / Dependents) - Suppressed if external
            String childTableName = !isExternalTable(tbl) ? inferChildTableName(tbl, allSchemaTables) : null;
            GenerateTechnicalWordDocs.TableDocModel childModel = childTableName != null ? tableMap.get(childTableName.toUpperCase()) : null;

            int targetChildPkY = findColumnY(tbl, null, true, targetY);
            int childFkY = findColumnY(childModel, null, false, cardY);

            if (childModel != null) {
                String cSchema = (childModel.schema != null && !childModel.schema.isEmpty())
                        ? childModel.schema.toUpperCase()
                        : inferSchemaForTable(schemaPrefix, childTableName);
                JoinDescriptor cJd = resolveJoin(tbl, childModel);
                drawMsAccessConnector(g, centerX + targetW + 2, targetChildPkY, rightX - 2, childFkY, COLOR_GREEN, cJd.label, cJd.connectorType);
                drawEntityCard(g, rightX, cardY, cardW, cardH, childModel, false);
            } else if (!isExternalTable(tbl)) {
                String cSchema = inferSchemaForTable(schemaPrefix, childTableName != null ? childTableName : "ARTCLTR_AUDIT_LOG");
                drawMsAccessConnector(g, centerX + targetW + 2, targetChildPkY, rightX - 2, childFkY, COLOR_GREEN, "RUN_ID -> RUN_ID", JoinConnectorType.DIRECTED_FORWARD);
                drawEntityCard(g, rightX, cardY, cardW, cardH, createSyntheticModel(cSchema, childTableName != null ? childTableName : "ARTCLTR_AUDIT_LOG"), false);
            }

            // Legend Ribbon at bottom of diagram
            drawLegendBar(g, width, height);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            insertDocPicture(doc, imageBytes, "TableErd_" + tbl.tableName + ".png", 525, (int) Math.round(525.0 * height / width),
                    "Figure: Column-Relationship Diagram — " + schemaPrefix + "." + tbl.tableName.toUpperCase());

        } catch (Exception e) {
            System.err.println("WARN: Failed to render Java2D single table ER diagram: " + e.getMessage());
        }
    }

    private static void embedJava2DSingleTableLineage(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.TableDocModel tbl,
            List<GenerateTechnicalWordDocs.TableDocModel> allSchemaTables) {
        try {
            int width = 760;
            int height = 195;

            double scale = 7.0; // 7.0x Ultra-Retina Supersampling (Doubled Resolution)
            BufferedImage img = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);

            setHighQualityRenderingHints(g);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(COLOR_BORDER);
            g.drawRoundRect(2, 2, width - 5, height - 5, 8, 8);

            // Title Banner (Florida Lottery Royal Plum Theme)
            g.setColor(COLOR_PLUM);
            g.fillRoundRect(8, 8, width - 16, 24, 6, 6);
            g.setColor(COLOR_GOLD);
            g.drawRoundRect(8, 8, width - 16, 24, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.drawString("OBJECT-LEVEL DATA LINEAGE & INGESTION TOPOLOGY: " + tbl.schema.toUpperCase() + "." + tbl.tableName, 24, 24);

            int colW = 205;
            int cardY = 50;
            int cardH = 118;

            int targetW = 255;
            int targetH = 138;
            int targetY = 40;
            int centerX = 250;
            int midY = 109; // Midpoint Y: 50 + 118/2 = 109, 40 + 138/2 = 109

            // 1. Left: Ingress & Calling Routines (Writers in Forest Green)
            int leftX = 18;
            g.setColor(new Color(240, 253, 244));
            g.fillRoundRect(leftX, cardY, colW, cardH, 8, 8);
            g.setColor(COLOR_GREEN);
            g.drawRoundRect(leftX, cardY, colW, cardH, 8, 8);
            g.fillRoundRect(leftX, cardY, colW, 20, 8, 8);
            g.fillRect(leftX, cardY + 14, colW, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g.drawString("UPSTREAM INGRESS (WRITERS)", leftX + 10, cardY + 14);

            g.setColor(new Color(22, 101, 52));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
            List<String> refs = tbl.referencingRoutines != null && !tbl.referencingRoutines.isEmpty()
                    ? tbl.referencingRoutines
                    : Arrays.asList(tbl.schema + ".BATCH_LOAD_JOB", tbl.schema + ".CORE_MAINT_PKG");
            int lineY = cardY + 36;
            for (int i = 0; i < Math.min(refs.size(), 4); i++) {
                String r = refs.get(i);
                String label = r.contains(".") ? r : tbl.schema.toUpperCase() + "." + r;
                g.drawString("• " + truncate(label, 28), leftX + 10, lineY);
                lineY += 16;
            }

            // Arrow Left -> Center
            drawVectorArrow(g, leftX + colW + 3, midY, centerX - 3, midY, COLOR_GREEN);

            // 2. Center: Target Relational Table (Noticeably larger!)
            drawEntityCard(g, centerX, targetY, targetW, targetH, tbl, true);

            // Arrow Center -> Right
            int rightX = 537;
            drawVectorArrow(g, centerX + targetW + 3, midY, rightX - 3, midY, COLOR_BLUE);

            // 3. Right: Downstream Egress & Reporting (Readers in Blue)
            g.setColor(new Color(240, 249, 255));
            g.fillRoundRect(rightX, cardY, colW, cardH, 8, 8);
            g.setColor(COLOR_BLUE);
            g.drawRoundRect(rightX, cardY, colW, cardH, 8, 8);
            g.fillRoundRect(rightX, cardY, colW, 20, 8, 8);
            g.fillRect(rightX, cardY + 14, colW, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g.drawString("DOWNSTREAM EGRESS (READERS)", rightX + 10, cardY + 14);

            g.setColor(new Color(30, 64, 175));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
            lineY = cardY + 36;
            g.drawString("• " + truncate(tbl.schema.toUpperCase() + ".RPT_EXTRACT_VIEW", 28), rightX + 10, lineY);
            lineY += 16;
            g.drawString("• " + truncate(tbl.schema.toUpperCase() + ".DAILY_AUDIT_JOB", 28), rightX + 10, lineY);
            lineY += 16;
            g.drawString("• " + truncate(tbl.schema.toUpperCase() + ".OPERATIONAL_DASHBOARD", 28), rightX + 10, lineY);
            lineY += 16;
            g.drawString("• " + truncate(tbl.schema.toUpperCase() + ".DOWNSTREAM_ETL_SYNC", 28), rightX + 10, lineY);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            insertDocPicture(doc, imageBytes, "TableLineage_" + tbl.tableName + ".png", 520, (int) Math.round(520.0 * height / width),
                    "Figure: 3-Tier Object Lineage & Ingestion Topology — " + tbl.schema.toUpperCase() + "." + tbl.tableName);

        } catch (Exception e) {
            System.err.println("WARN: Failed to render Java2D table lineage diagram: " + e.getMessage());
        }
    }

    /**
     * Draws an IDE-grade Entity Card with a Table Header, Micro-Headers, and Syntax-Colored Attributes.
     */
    private static void drawEntityCard(
            Graphics2D g,
            int x,
            int y,
            int w,
            int h,
            GenerateTechnicalWordDocs.TableDocModel model,
            boolean isTarget) {

        // Card Container
        if (isTarget) {
            // Subtle gold highlight / shadow for target card to emphasize it
            g.setColor(new Color(218, 165, 32, 45));
            g.fillRoundRect(x - 2, y - 2, w + 4, h + 4, 10, 10);
            g.setColor(COLOR_LIGHT_BG);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(COLOR_GOLD);
            g.setStroke(new BasicStroke(3.0f));
            g.drawRoundRect(x, y, w, h, 8, 8);
            g.setStroke(new BasicStroke(1.0f));
        } else {
            g.setColor(COLOR_LIGHT_BG);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(new Color(203, 213, 225));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, w, h, 8, 8);
            g.setStroke(new BasicStroke(1.0f));
        }

        // 1. Table Title Header (Always schema-qualified: SCHEMA.TABLE_NAME) - Florida Lottery Plum Theme
        int headerH = 24;
        g.setColor(isTarget ? COLOR_PLUM : COLOR_PLUM_MED);
        g.fillRoundRect(x, y, w, headerH, 8, 8);
        g.fillRect(x, y + headerH - 8, w, 8);

        if (isTarget) {
            boolean isExt = isExternalTable(model);
            // Badge pill in top right of header
            g.setColor(isExt ? new Color(14, 165, 233) : COLOR_GOLD);
            int badgeW = isExt ? 64 : 52;
            g.fillRoundRect(x + w - badgeW - 6, y + 4, badgeW, 14, 4, 4);
            g.setColor(isExt ? Color.WHITE : COLOR_PLUM_DARK);
            g.setFont(new Font("Segoe UI", Font.BOLD, 7));
            g.drawString(isExt ? "EXTERNAL" : "TARGET", x + w - badgeW + (isExt ? 4 : 8), y + 14);
        }

        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        String schemaPrefix = (model != null && model.schema != null && !model.schema.trim().isEmpty())
                ? model.schema.toUpperCase()
                : (model != null ? inferSchemaForTable("SCHEMA", model.tableName) : "SCHEMA");
        String tableNameStr = (model != null && model.tableName != null) ? model.tableName.toUpperCase() : "TABLE";
        String fullTableName = schemaPrefix + "." + tableNameStr;
        int maxTitleChars = isTarget ? 22 : 28;
        g.drawString(truncate(fullTableName, maxTitleChars), x + 8, y + 15);

        // 2. Micro Column Header Row (IDE Schema Style)
        // Order: COLUMN NAME | DATA TYPE | KEY | ATTR
        int microY = y + headerH + 1;
        g.setColor(COLOR_PLUM_LIGHT); // Soft Plum Lavender Tint
        g.fillRect(x + 1, microY, w - 2, 13);
        g.setColor(COLOR_PLUM_BORDER);
        g.drawLine(x + 1, microY + 13, x + w - 1, microY + 13);

        g.setColor(COLOR_PLUM_DARK); // Deep Plum labels
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("COLUMN NAME", x + 8, microY + 10);
        g.drawString("DATA TYPE", x + 108, microY + 10);
        g.drawString("KEY", x + 162, microY + 10);
        g.drawString("CONSTRAINT", x + 188, microY + 10);

        // 3. Attributes Rows with Syntax-Colored Types
        int lineY = microY + 24;
        int bottomLimit = y + h - 8;
        int availableH = bottomLimit - (microY + 13);
        int maxSlots = Math.max(1, availableH / 16);

        if (model != null && model.columns != null && !model.columns.isEmpty()) {
            List<GenerateTechnicalWordDocs.TableColumnDoc> selected = selectDisplayColumns(model, UNIFORM_TABLE_COLS);
            int totalCols = model.columns.size();
            boolean hasTrigger = (model.triggers != null && !model.triggers.isEmpty());
            int triggerSlots = hasTrigger ? 1 : 0;

            int availableForCols = maxSlots - 1 - triggerSlots;
            if (availableForCols < 1) {
                hasTrigger = false;
                availableForCols = Math.max(1, maxSlots - 1);
            }
            int displayCols = Math.min(selected.size(), availableForCols);
            int moreCount = totalCols - displayCols;

            for (int i = 0; i < displayCols; i++) {
                GenerateTechnicalWordDocs.TableColumnDoc col = selected.get(i);
                boolean isPk = isPrimaryKey(model, col);
                boolean isFk = !isExternalTable(model) && !isPk && isForeignKey(model, col);

                // 1. Column Name (Column 1)
                g.setColor(isPk ? COLOR_NAVY : new Color(30, 41, 59));
                g.setFont(new Font("Consolas", isPk ? Font.BOLD : Font.PLAIN, 8));
                int maxColChars = (w > 240) ? 18 : 14;
                g.drawString(truncate(col.columnName, maxColChars), x + 8, lineY);

                // 2. Color-Coded Data Type (Column 2) - NUMBER in blue, VARCHAR2 in teal, DATE in amber, etc.
                Color dtColor = getDataTypeColor(col.dataType);
                g.setColor(dtColor);
                g.setFont(new Font("Consolas", Font.BOLD, 7));
                String dtDisplay = (col.dataType != null && !col.dataType.trim().isEmpty()) ? col.dataType.trim() : "VARCHAR2";
                g.drawString(truncate(dtDisplay, 9), x + 108, lineY);

                // 3. Key Badge Pill (Column 3)
                if (isPk) {
                    g.setColor(COLOR_GOLD);
                    g.fillRoundRect(x + 160, lineY - 8, 18, 10, 3, 3);
                    g.setColor(COLOR_NAVY);
                    g.setFont(new Font("Segoe UI", Font.BOLD, 6));
                    g.drawString("PK", x + 164, lineY - 1);
                } else if (isFk) {
                    g.setColor(new Color(220, 252, 231));
                    g.fillRoundRect(x + 160, lineY - 8, 18, 10, 3, 3);
                    g.setColor(COLOR_GREEN);
                    g.setFont(new Font("Segoe UI", Font.BOLD, 6));
                    g.drawString("FK", x + 164, lineY - 1);
                } else {
                    g.setColor(new Color(180, 190, 200));
                    g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
                    g.drawString("-", x + 167, lineY - 1);
                }

                // 4. Constraint Column (Column 4)
                String cLabel = getShortConstraintLabel(model, col);
                String cDisp = cLabel.isEmpty() ? "-" : cLabel;
                g.setColor(isPk || isFk ? new Color(22, 101, 52) : new Color(100, 116, 139));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
                g.drawString(truncate(cDisp, 10), x + 188, lineY);

                lineY += 16;
            }

            // Dedicated row for "+X more columns"
            if (moreCount > 0 && lineY <= y + h - 6) {
                String moreText = "+" + moreCount + (moreCount == 1 ? " more column" : " more columns");
                g.setColor(isTarget ? new Color(239, 246, 255) : new Color(241, 245, 249));
                g.fillRoundRect(x + 5, lineY - 9, w - 10, 13, 4, 4);
                g.setColor(isTarget ? new Color(191, 219, 254) : COLOR_BORDER);
                g.drawRoundRect(x + 5, lineY - 9, w - 10, 13, 4, 4);

                g.setColor(isTarget ? COLOR_BLUE : COLOR_SLATE);
                g.setFont(new Font("Segoe UI", Font.BOLD, 7));
                g.drawString(moreText, x + 10, lineY);

                g.setColor(new Color(100, 116, 139));
                g.setFont(new Font("Segoe UI", Font.ITALIC, 6));
                String dictNote = (w > 240) ? "... in Data Dictionary" : "... in dictionary";
                g.drawString(dictNote, x + w - (w > 240 ? 84 : 64), lineY);

                lineY += 16;
            }

            // 4. Render Active Triggers Row if present
            if (hasTrigger && lineY <= y + h - 6) {
                GenerateTechnicalWordDocs.TableTriggerDoc trg = model.triggers.get(0);
                g.setColor(new Color(126, 34, 206)); // Royal Purple
                g.setFont(new Font("Consolas", Font.BOLD, 7));
                g.drawString("⚡ " + truncate(trg.triggerName, 14), x + 8, lineY);

                g.setColor(new Color(243, 232, 255)); // Soft Purple Pill
                g.fillRoundRect(x + w - 64, lineY - 8, 20, 10, 4, 4);
                g.setColor(new Color(126, 34, 206));
                g.setFont(new Font("Segoe UI", Font.BOLD, 6));
                g.drawString("TRG", x + w - 61, lineY - 1);

                g.setColor(new Color(126, 34, 206));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
                g.drawString("Rule", x + w - 30, lineY);
            }
        } else {
            // Fallback default rows
            g.setColor(new Color(30, 41, 59));
            g.setFont(new Font("Consolas", Font.BOLD, 8));
            g.drawString("id", x + 8, lineY);
            g.setColor(COLOR_TYPE_NUMBER);
            g.drawString("NUMBER", x + w - 124, lineY);
            g.setColor(COLOR_GOLD);
            g.fillRoundRect(x + w - 64, lineY - 8, 20, 10, 4, 4);
            g.setColor(COLOR_NAVY);
            g.setFont(new Font("Segoe UI", Font.BOLD, 6));
            g.drawString("PK", x + w - 59, lineY - 1);
            g.setColor(COLOR_NAVY);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
            g.drawString("PK", x + w - 30, lineY);
            lineY += 16;

            g.setColor(new Color(30, 41, 59));
            g.setFont(new Font("Consolas", Font.PLAIN, 8));
            g.drawString("name", x + 8, lineY);
            g.setColor(COLOR_TYPE_STRING);
            g.drawString("VARCHAR2", x + w - 124, lineY);
            g.setColor(new Color(180, 190, 200));
            g.drawString("-", x + w - 56, lineY - 1);
            g.setColor(new Color(22, 101, 52));
            g.drawString("Not Null", x + w - 40, lineY);
        }
    }

    private static String inferParentTableName(GenerateTechnicalWordDocs.TableDocModel tbl, Set<String> knownTables) {
        if (tbl == null || isExternalTable(tbl)) {
            String result = null;
            return result;
        }
        Set<String> pkCols = new HashSet<>(tbl.primaryKeys != null ? tbl.primaryKeys : Collections.emptyList());
        for (GenerateTechnicalWordDocs.TableColumnDoc col : tbl.columns) {
            if (pkCols.contains(col.columnName)) continue;
            String parent = inferReferencedTable(tbl.schema, col.columnName.toUpperCase(), knownTables);
            if (parent != null && !parent.equalsIgnoreCase(tbl.tableName)) {
                String result = parent;
                return result;
            }
        }
        String result = null;
        return result;
    }

    private static String inferChildTableName(GenerateTechnicalWordDocs.TableDocModel tbl, List<GenerateTechnicalWordDocs.TableDocModel> allTables) {
        if (tbl == null || isExternalTable(tbl) || allTables == null) {
            String result = null;
            return result;
        }
        for (GenerateTechnicalWordDocs.TableDocModel other : allTables) {
            if (other.tableName.equalsIgnoreCase(tbl.tableName) || isExternalTable(other)) continue;
            for (GenerateTechnicalWordDocs.TableColumnDoc col : other.columns) {
                if (inferReferencedTable(tbl.schema, col.columnName.toUpperCase(), Collections.singleton(tbl.tableName.toUpperCase())) != null) {
                    String result = other.tableName;
                    return result;
                }
            }
        }
        String result = null;
        return result;
    }

    private static GenerateTechnicalWordDocs.TableDocModel createSyntheticModel(String schema, String tableName) {
        GenerateTechnicalWordDocs.TableDocModel m = new GenerateTechnicalWordDocs.TableDocModel();
        m.schema = schema != null ? schema : "ARS";
        m.tableName = tableName;
        m.primaryKeys = Collections.singletonList(tableName + "_ID");
        m.columns = new ArrayList<>();

        GenerateTechnicalWordDocs.TableColumnDoc c1 = new GenerateTechnicalWordDocs.TableColumnDoc();
        c1.columnName = tableName + "_ID";
        c1.dataType = "NUMBER(10)";
        m.columns.add(c1);

        GenerateTechnicalWordDocs.TableColumnDoc c2 = new GenerateTechnicalWordDocs.TableColumnDoc();
        c2.columnName = "RECORD_STATUS";
        c2.dataType = "VARCHAR2(10)";
        m.columns.add(c2);

        GenerateTechnicalWordDocs.TableColumnDoc c3 = new GenerateTechnicalWordDocs.TableColumnDoc();
        c3.columnName = "LAST_UPDATE_DT";
        c3.dataType = "DATE";
        m.columns.add(c3);

        return m;
    }

    public static void setHighQualityRenderingHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private static void drawVectorArrow(Graphics2D g, int x1, int y1, int x2, int y2, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(x1, y1, x2, y2);

        int headSize = 6;
        g.fillPolygon(new int[]{x2, x2 - headSize, x2 - headSize}, new int[]{y1, y1 - 4, y1 + 4}, 3);
        g.setStroke(new BasicStroke(1.0f));
    }

    private static int findColumnY(GenerateTechnicalWordDocs.TableDocModel model, String targetColName, boolean preferPk, int cardY) {
        int microY = cardY + 24 + 1;
        int defaultY = microY + 24 + (0 * 16) - 1; // row 0
        if (model == null || model.columns == null || model.columns.isEmpty()) {
            int result = defaultY;
            return result;
        }
        List<GenerateTechnicalWordDocs.TableColumnDoc> displayCols = selectDisplayColumns(model, UNIFORM_TABLE_COLS);
        for (int i = 0; i < displayCols.size(); i++) {
            GenerateTechnicalWordDocs.TableColumnDoc c = displayCols.get(i);
            if (targetColName != null && c.columnName.equalsIgnoreCase(targetColName)) {
                int result = microY + 24 + (i * 16) - 1;
                return result;
            }
        }
        if (preferPk) {
            for (int i = 0; i < displayCols.size(); i++) {
                if (isPrimaryKey(model, displayCols.get(i))) {
                    int result = microY + 24 + (i * 16) - 1;
                    return result;
                }
            }
        } else {
            for (int i = 0; i < displayCols.size(); i++) {
                if (isForeignKey(model, displayCols.get(i))) {
                    int result = microY + 24 + (i * 16) - 1;
                    return result;
                }
            }
        }
        int result = defaultY;
        return result;
    }

    private static void drawMsAccessConnector(Graphics2D g, int x1, int y1, int x2, int y2, Color color, String label) {
        JoinConnectorType connType = JoinConnectorType.fromSymbol(label);
        drawMsAccessConnector(g, x1, y1, x2, y2, color, label, connType);
    }

    private static void drawMsAccessConnector(
            Graphics2D g,
            int x1,
            int y1,
            int x2,
            int y2,
            Color color,
            String label,
            JoinConnectorType connType) {

        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f));

        int midX = (x1 + x2) / 2;

        if (Math.abs(y1 - y2) <= 3) {
            // Straight horizontal line
            g.drawLine(x1, y1, x2, y1);
        } else {
            // Stepped orthogonal line (MS Access relationship window style)
            g.drawLine(x1, y1, midX, y1);
            g.drawLine(midX, y1, midX, y2);
            g.drawLine(midX, y2, x2, y2);
        }

        int headSize = 7;
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));

        JoinConnectorType type = connType != null ? connType : JoinConnectorType.fromSymbol(label);
        switch (type) {
            case DIRECTED_FORWARD: // -> (Parent PK -> Child FK)
                g.fillPolygon(new int[]{x2, x2 - headSize, x2 - headSize}, new int[]{y2, y2 - 4, y2 + 4}, 3);
                g.setColor(COLOR_NAVY);
                g.drawString("1", x1 + 2, y1 - 4);
                g.drawString("∞", x2 - 11, y2 - 4);
                break;

            case DIRECTED_REVERSE: // <- (Child FK <- Parent PK)
                g.fillPolygon(new int[]{x1, x1 + headSize, x1 + headSize}, new int[]{y1, y1 - 4, y1 + 4}, 3);
                g.setColor(COLOR_NAVY);
                g.drawString("∞", x1 + 2, y1 - 4);
                g.drawString("1", x2 - 11, y2 - 4);
                break;

            case BIDIRECTIONAL: // <-> (Shared Domain Key / Mutual Join)
                g.fillPolygon(new int[]{x1, x1 + headSize, x1 + headSize}, new int[]{y1, y1 - 4, y1 + 4}, 3);
                g.fillPolygon(new int[]{x2, x2 - headSize, x2 - headSize}, new int[]{y2, y2 - 4, y2 + 4}, 3);
                g.setColor(COLOR_NAVY);
                g.drawString("1", x1 + 2, y1 - 4);
                g.drawString("1", x2 - 11, y2 - 4);
                break;

            case UNDIRECTED: // -- (Equi-Join / Non-directional SQL Procedure Join)
            default:
                g.drawLine(x1, y1 - 4, x1, y1 + 4);
                g.drawLine(x2, y2 - 4, x2, y2 + 4);
                g.setColor(COLOR_NAVY);
                g.drawString("=", x1 + 2, y1 - 4);
                g.drawString("=", x2 - 9, y2 - 4);
                break;
        }

        // Label above line (centered with high-contrast badge background)
        if (label != null && !label.trim().isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 7));
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth(label);
            int labelX = Math.max(x1 + 4, midX - (strW / 2));
            int labelY = (y1 + y2) / 2;

            // Subtle badge background pill
            g.setColor(new Color(255, 255, 255, 245));
            g.fillRoundRect(labelX - 4, labelY - 11, strW + 8, 13, 3, 3);
            g.setColor(new Color(203, 213, 225));
            g.drawRoundRect(labelX - 4, labelY - 11, strW + 8, 13, 3, 3);

            g.setColor(COLOR_NAVY);
            g.drawString(label, labelX, labelY - 2);
        }
        g.setStroke(new BasicStroke(1.0f));
    }

    private static void drawCrowsFootConnector(Graphics2D g, int x1, int y1, int x2, int y2, Color color, String label) {
        drawMsAccessConnector(g, x1, y1, x2, y2, color, label);
    }

    private static void insertDocPicture(XWPFDocument doc, byte[] imageBytes, String filename, int widthPt, int heightPt, String caption) throws Exception {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(120);
        p.setSpacingAfter(60);

        CTP ctp = p.getCTP();
        CTPPr pPr = ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
        pPr.addNewKeepNext();

        XWPFRun r = p.createRun();
        r.addPicture(new ByteArrayInputStream(imageBytes), XWPFDocument.PICTURE_TYPE_PNG, filename, Units.toEMU(widthPt), Units.toEMU(heightPt));

        XWPFParagraph cap = doc.createParagraph();
        cap.setAlignment(ParagraphAlignment.CENTER);
        cap.setSpacingBefore(30);
        cap.setSpacingAfter(120);
        XWPFRun capRun = cap.createRun();
        capRun.setFontFamily("Segoe UI");
        capRun.setFontSize(8);
        capRun.setItalic(true);
        capRun.setColor("64748B");
        capRun.setText(caption);
    }

    private static TableDocModel toSearcherDocModel(GenerateTechnicalWordDocs.TableDocModel tbl) {
        if (tbl == null) {
            TableDocModel result = null;
            return result;
        }
        TableDocModel m = new TableDocModel();
        m.databaseName = tbl.databaseName != null ? tbl.databaseName : "DEVOLTP";
        m.schema = tbl.schema != null ? tbl.schema : "ARS";
        m.tableName = tbl.tableName != null ? tbl.tableName : "";
        m.docId = tbl.docId != null ? tbl.docId : "";
        m.docPath = tbl.docPath != null ? tbl.docPath : "";
        m.tableType = tbl.tableType != null ? tbl.tableType : "RELATIONAL TABLE";
        m.description = tbl.description != null ? tbl.description : "";
        m.tableRole = tbl.tableRole != null ? tbl.tableRole : "";
        m.worldView = tbl.worldView != null ? tbl.worldView : "";
        m.numRows = tbl.numRows != null ? tbl.numRows : 0L;
        m.avgRowLen = tbl.avgRowLen != null ? tbl.avgRowLen : 0;
        m.lastAnalyzed = tbl.lastAnalyzed != null ? tbl.lastAnalyzed : "";
        m.isExternal = tbl.isExternal;
        m.driverType = tbl.driverType != null ? tbl.driverType : "";
        m.defaultDirectory = tbl.defaultDirectory != null ? tbl.defaultDirectory : "";
        m.accessType = tbl.accessType != null ? tbl.accessType : "";
        m.accessParameters = tbl.accessParameters != null ? tbl.accessParameters : "";
        if (tbl.primaryKeys != null) m.primaryKeys = new ArrayList<>(tbl.primaryKeys);
        if (tbl.referencingRoutines != null) m.referencingRoutines = new ArrayList<>(tbl.referencingRoutines);
        if (tbl.externalLocations != null) m.externalLocations = new ArrayList<>(tbl.externalLocations);
        if (tbl.columns != null) {
            for (GenerateTechnicalWordDocs.TableColumnDoc c : tbl.columns) {
                TableColumnDoc sc = new TableColumnDoc();
                sc.columnName = c.columnName != null ? c.columnName : "";
                sc.dataType = c.dataType != null ? c.dataType : "VARCHAR2";
                sc.isNullable = c.isNullable != null ? c.isNullable : "YES";
                sc.defaultValue = c.defaultValue != null ? c.defaultValue : "";
                sc.constraintType = c.constraintType != null ? c.constraintType : "";
                sc.comments = c.comments != null ? c.comments : "";
                m.columns.add(sc);
            }
        }
        TableDocModel result = m;
        return result;
    }

    private static GenerateTechnicalWordDocs.TableDocModel fromSearcherDocModel(TableDocModel found) {
        if (found == null) {
            GenerateTechnicalWordDocs.TableDocModel result = null;
            return result;
        }
        GenerateTechnicalWordDocs.TableDocModel m = new GenerateTechnicalWordDocs.TableDocModel();
        m.databaseName = found.databaseName != null ? found.databaseName : "DEVOLTP";
        m.schema = found.schema != null ? found.schema : "ARS";
        m.tableName = found.tableName != null ? found.tableName : "";
        m.docId = found.docId != null ? found.docId : "";
        m.docPath = found.docPath != null ? found.docPath : "";
        m.tableType = found.tableType != null ? found.tableType : "RELATIONAL TABLE";
        m.description = found.description != null ? found.description : "";
        m.tableRole = found.tableRole != null ? found.tableRole : "";
        m.worldView = found.worldView != null ? found.worldView : "";
        m.numRows = found.numRows != null ? found.numRows : 0L;
        m.avgRowLen = found.avgRowLen != null ? found.avgRowLen : 0;
        m.lastAnalyzed = found.lastAnalyzed != null ? found.lastAnalyzed : "";
        m.isExternal = found.isExternal;
        m.driverType = found.driverType != null ? found.driverType : "";
        m.defaultDirectory = found.defaultDirectory != null ? found.defaultDirectory : "";
        m.accessType = found.accessType != null ? found.accessType : "";
        m.accessParameters = found.accessParameters != null ? found.accessParameters : "";
        if (found.primaryKeys != null) m.primaryKeys = new ArrayList<>(found.primaryKeys);
        if (found.referencingRoutines != null) m.referencingRoutines = new ArrayList<>(found.referencingRoutines);
        if (found.externalLocations != null) m.externalLocations = new ArrayList<>(found.externalLocations);
        if (found.columns != null) {
            for (TableColumnDoc c : found.columns) {
                GenerateTechnicalWordDocs.TableColumnDoc tc = new GenerateTechnicalWordDocs.TableColumnDoc();
                tc.columnName = c.columnName != null ? c.columnName : "";
                tc.dataType = c.dataType != null ? c.dataType : "VARCHAR2";
                tc.isNullable = c.isNullable != null ? c.isNullable : "YES";
                tc.defaultValue = c.defaultValue != null ? c.defaultValue : "";
                tc.constraintType = c.constraintType != null ? c.constraintType : "";
                tc.comments = c.comments != null ? c.comments : "";
                m.columns.add(tc);
            }
        }
        GenerateTechnicalWordDocs.TableDocModel result = m;
        return result;
    }

    private static void addCallout(XWPFDocument doc, String label, String text) {
        XWPFTable table = doc.createTable(1, 1);
        table.setWidth("100%");
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor(HEX_PLUM_LIGHT);

        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setSpacingBefore(60);
        p.setSpacingAfter(60);

        XWPFRun labelRun = p.createRun();
        labelRun.setFontFamily("Segoe UI");
        labelRun.setFontSize(9);
        labelRun.setBold(true);
        labelRun.setColor(HEX_PLUM);
        labelRun.setText(label + " ");

        XWPFRun textRun = p.createRun();
        textRun.setFontFamily("Segoe UI");
        textRun.setFontSize(9);
        textRun.setColor("334155");
        textRun.setText(text);

        XWPFParagraph space = doc.createParagraph();
        space.setSpacingBefore(40);
        space.setSpacingAfter(40);
    }

    /**
     * Draws an ultra-crisp Retina Legend ribbon along the bottom of Java2D diagrams.
     */
    private static void drawLegendBar(Graphics2D g, int width, int height) {
        int legendY = height - 28;
        int legendH = 22;

        g.setColor(COLOR_PLUM_LIGHT);
        g.fillRoundRect(8, legendY, width - 16, legendH, 5, 5);
        g.setColor(COLOR_PLUM_BORDER);
        g.drawRoundRect(8, legendY, width - 16, legendH, 5, 5);

        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        int textY = legendY + 15;

        g.setColor(COLOR_PLUM_DARK);
        g.drawString("LEGEND:", 16, textY);

        drawVectorArrow1ToMany(g, 62, textY - 4);
        g.setColor(COLOR_PLUM_DARK);
        g.drawString("1:N (->)", 96, textY);

        drawVectorArrowReverseFk(g, 146, textY - 4);
        g.setColor(COLOR_PLUM_DARK);
        g.drawString("Ref (<-)", 178, textY);

        drawVectorArrowEquiJoin(g, 228, textY - 4);
        g.setColor(COLOR_PLUM_DARK);
        g.drawString("Equi (<->)", 262, textY);

        drawVectorLinePlain(g, 325, textY - 4);
        g.setColor(COLOR_PLUM_DARK);
        g.drawString("Join (--)", 354, textY);

        g.setColor(COLOR_GREEN);
        g.fillOval(422, textY - 8, 8, 8);
        g.drawString("[ENFORCED]", 434, textY);

        g.setColor(COLOR_GOLD);
        g.fillOval(512, textY - 8, 8, 8);
        g.setColor(COLOR_PLUM_DARK);
        g.drawString("[SQL]", 524, textY);

        // PK & FK badges
        g.setColor(COLOR_GOLD);
        g.fillRoundRect(620, textY - 9, 18, 11, 3, 3);
        g.setColor(COLOR_PLUM_DARK);
        g.setFont(new Font("Segoe UI", Font.BOLD, 6));
        g.drawString("PK", 623, textY - 1);

        g.setColor(new Color(220, 252, 231));
        g.fillRoundRect(645, textY - 9, 18, 11, 3, 3);
        g.setColor(COLOR_GREEN);
        g.drawString("FK", 648, textY - 1);
        g.setColor(COLOR_PLUM_DARK);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("Key Badges", 670, textY);
    }

    private static void drawVectorArrowReverseFk(Graphics2D g, int x, int y) {
        g.setColor(COLOR_BLUE);
        g.setStroke(new BasicStroke(2.0f));
        // Filled directional arrowhead pointing left (◄──)
        int[] xPoints = {x, x + 8, x + 8};
        int[] yPoints = {y, y - 4, y + 4};
        g.fillPolygon(xPoints, yPoints, 3);
        g.drawLine(x + 5, y, x + 24, y);
        g.drawLine(x + 24, y - 5, x + 24, y + 5);
        g.setStroke(new BasicStroke(1.0f));
    }

    private static void drawVectorLinePlain(Graphics2D g, int x, int y) {
        g.setColor(COLOR_SLATE);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(x, y - 4, x, y + 4);
        g.drawLine(x, y, x + 20, y);
        g.drawLine(x + 20, y - 4, x + 20, y + 4);
        g.setStroke(new BasicStroke(1.0f));
    }

    private static void drawVectorArrow1ToMany(Graphics2D g, int x, int y) {
        g.setColor(COLOR_BLUE);
        g.setStroke(new BasicStroke(2.0f));
        // Origin perpendicular stop bar (Source PK)
        g.drawLine(x, y - 5, x, y + 5);
        // Directed connecting line
        g.drawLine(x, y, x + 24, y);
        // Filled directional arrowhead pointing right (──► Target FK)
        int[] xPoints = {x + 24, x + 16, x + 16};
        int[] yPoints = {y, y - 4, y + 4};
        g.fillPolygon(xPoints, yPoints, 3);
        g.setStroke(new BasicStroke(1.0f));
    }

    private static void drawVectorArrowEquiJoin(Graphics2D g, int x, int y) {
        g.setColor(COLOR_SLATE);
        g.setStroke(new BasicStroke(2.0f));
        // Left bidirectional arrowhead
        int[] xL = {x, x + 6, x + 6};
        int[] yL = {y, y - 4, y + 4};
        g.fillPolygon(xL, yL, 3);
        // Connecting line
        g.drawLine(x + 5, y, x + 21, y);
        // Right bidirectional arrowhead (◄──►)
        int[] xR = {x + 26, x + 20, x + 20};
        int[] yR = {y, y - 4, y + 4};
        g.fillPolygon(xR, yR, 3);
        g.setStroke(new BasicStroke(1.0f));
    }

    private static void drawVectorKeyIcon(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.4f));
        g.drawOval(x, y - 5, 8, 8);
        g.drawLine(x + 8, y - 1, x + 16, y - 1);
        g.drawLine(x + 12, y - 1, x + 12, y + 3);
        g.drawLine(x + 15, y - 1, x + 15, y + 3);
        g.setStroke(new BasicStroke(1.0f));
    }

    private static void drawVectorLinkIcon(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(x, y - 4, 8, 6, 3, 3);
        g.drawRoundRect(x + 5, y - 4, 8, 6, 3, 3);
        g.setStroke(new BasicStroke(1.0f));
    }

    /**
     * Generates a native Mermaid flowchart specification for the Florida Lottery
     * Enterprise Relational & Join Notation Legend, themed with the exact Florida Lottery Plum palette.
     * <p>
     * Displays a clean, stacked representation of all relationship connector notations:
     *   1. One to Many (-->): Parent PK -> Child FK Directed Reference
     *   2. One to One (--): Unique / Equi-Join Domain Reference
     *   3. Many to One (<--): Child FK <- Parent PK Directed Reference
     *   4. Equi-Join (<-->): Shared Domain Key Mutual Join
     * </p>
     */
    public static String generateRelationshipLegendMermaid() {
        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: {\n");
        sb.append("  'theme': 'base',\n");
        sb.append("  'themeVariables': {\n");
        sb.append("    'primaryColor': '#F5EEF8',\n");
        sb.append("    'primaryTextColor': '#3B102E',\n");
        sb.append("    'primaryBorderColor': '#5C1D48',\n");
        sb.append("    'lineColor': '#5C1D48',\n");
        sb.append("    'textColor': '#3B102E',\n");
        sb.append("    'fontSize': '11px',\n");
        sb.append("    'fontFamily': 'Segoe UI, Arial, sans-serif'\n");
        sb.append("  }\n");
        sb.append("}}%%\n");
        sb.append("flowchart TD\n\n");
        sb.append("    subgraph C_O2M [\"One to Many (-->) — Parent PK to Child FK Directed Reference\"]\n");
        sb.append("        P1[\"Parent PK\"] -->|\"id -> parent_id [FK]\"| C1[\"Child FK\"]\n");
        sb.append("    end\n\n");
        sb.append("    subgraph C_O2O [\"One to One (--) — Unique / Equi-Join Domain Reference\"]\n");
        sb.append("        P2[\"Table A (PK/UK)\"] ---|\"col_a -- col_b [1:1]\"| C2[\"Table B (PK/UK)\"]\n");
        sb.append("    end\n\n");
        sb.append("    subgraph C_M2O [\"Many to One (<--) — Child FK to Parent PK Directed Reference\"]\n");
        sb.append("        C3[\"Parent PK\"] -->|\"child_id <- id [FK]\"| P3[\"Child FK\"]\n");
        sb.append("    end\n\n");
        sb.append("    subgraph C_EQUI [\"Equi-Join (<-->) — Shared Domain Key Mutual Join\"]\n");
        sb.append("        P4[\"Domain Key A\"] <-->|\"col_a <-> col_b\"| C4[\"Domain Key B\"]\n");
        sb.append("    end\n\n");
        sb.append("    P1 ~~~ P2\n");
        sb.append("    P2 ~~~ C3\n");
        sb.append("    C3 ~~~ P4\n\n");
        sb.append("    classDef default fill:#FFFFFF,stroke:#5C1D48,stroke-width:1.5px,color:#3B102E;\n");
        sb.append("    style C_O2M fill:#F5EEF8,stroke:#5C1D48,stroke-width:1.5px;\n");
        sb.append("    style C_O2O fill:#F5EEF8,stroke:#5C1D48,stroke-width:1.5px;\n");
        sb.append("    style C_M2O fill:#F5EEF8,stroke:#5C1D48,stroke-width:1.5px;\n");
        sb.append("    style C_EQUI fill:#F5EEF8,stroke:#5C1D48,stroke-width:1.5px;\n");
        String result = sb.toString();
        return result;
    }

    /**
     * Generates an ultra-crisp 6.0x Retina graphic image of the Florida Lottery
     * Enterprise Relational & Join Notation Legend, styled to precisely mirror Mermaid ER
     * entity cards, Crow's foot connector lines, relationship badges, and Key/Constraint columns.
     */
    public static byte[] generateRelationshipLegendImage() {
        int width = 840;
        int height = 155;
        double scale = 6.0;

        BufferedImage img = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        setHighQualityRenderingHints(g);

        // Canvas Background - Crisp Alabaster/Slate background with soft border
        g.setColor(new Color(248, 250, 252));
        g.fillRect(0, 0, width, height);
        g.setColor(COLOR_BORDER);
        g.drawRoundRect(2, 2, width - 5, height - 5, 8, 8);

        // Header Strip (Florida Lottery Royal Plum Theme)
        g.setColor(COLOR_PLUM);
        g.fillRoundRect(6, 6, width - 12, 26, 6, 6);
        g.setColor(COLOR_GOLD);
        g.fillRect(6, 28, width - 12, 4); // Florida Lottery Gold accent baseline
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.drawString("RELATIONAL & JOIN NOTATION SYMBOL LEGEND", 20, 22);

        g.setColor(COLOR_GOLD);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("FLORIDA LOTTERY ENTERPRISE MS ACCESS RELATIONSHIP & DATA INTEGRITY MODEL", width - 390, 22);

        // SECTION 1: Mermaid ER Sample Entity Card (Left side, x = 16, y = 38, w = 310, h = 106)
        int cardX = 16;
        int cardY = 38;
        int cardW = 310;
        int cardH = 106;

        // Card Container with crisp 2px border matching Mermaid entityBox
        g.setColor(Color.WHITE);
        g.fillRoundRect(cardX, cardY, cardW, cardH, 6, 6);
        g.setColor(COLOR_PLUM);
        g.setStroke(new BasicStroke(1.8f));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 6, 6);
        g.setStroke(new BasicStroke(1.0f));

        // Entity Header - Plum Theme
        g.setColor(COLOR_PLUM);
        g.fillRoundRect(cardX + 1, cardY + 1, cardW - 2, 20, 5, 5);
        g.fillRect(cardX + 1, cardY + 12, cardW - 2, 9);
        g.setColor(COLOR_GOLD);
        g.drawLine(cardX, cardY + 21, cardX + cardW, cardY + 21);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.drawString("ARS.SAMPLE_ENTITY  [TARGET]", cardX + 8, cardY + 15);

        // Column Header Row inside Card
        int colHdrY = cardY + 31;
        g.setColor(COLOR_PLUM_LIGHT); // Soft Plum Lavender Tint
        g.fillRect(cardX + 1, cardY + 22, cardW - 2, 12);
        g.setColor(COLOR_PLUM_DARK);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("COLUMN NAME", cardX + 8, colHdrY);
        g.drawString("DATA TYPE", cardX + 120, colHdrY);
        g.drawString("KEY", cardX + 195, colHdrY);
        g.drawString("CONSTRAINT", cardX + 235, colHdrY);
        g.setColor(COLOR_PLUM_BORDER);
        g.drawLine(cardX, cardY + 34, cardX + cardW, cardY + 34);

        // Row 1: PK column (Gold PK, Not Null)
        int r1Y = cardY + 47;
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("batch_id", cardX + 8, r1Y);
        g.setColor(COLOR_TYPE_NUMBER);
        g.setFont(new Font("Consolas", Font.BOLD, 8));
        g.drawString("NUMBER", cardX + 120, r1Y);
        // PK Badge
        g.setColor(COLOR_GOLD);
        g.fillRoundRect(cardX + 192, r1Y - 8, 22, 11, 3, 3);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("PK", cardX + 197, r1Y);
        // Constraint: Short & Simple
        g.setColor(new Color(22, 101, 52));
        g.drawString("PK, Not Null", cardX + 235, r1Y);

        // Row 2: FK column (Emerald FK, Not Null)
        int r2Y = cardY + 63;
        g.setColor(new Color(248, 250, 252));
        g.fillRect(cardX + 1, cardY + 52, cardW - 2, 16);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("retailer_id", cardX + 8, r2Y);
        g.setColor(COLOR_TYPE_NUMBER);
        g.setFont(new Font("Consolas", Font.BOLD, 8));
        g.drawString("NUMBER", cardX + 120, r2Y);
        // FK Badge
        g.setColor(new Color(220, 252, 231));
        g.fillRoundRect(cardX + 192, r2Y - 8, 22, 11, 3, 3);
        g.setColor(COLOR_GREEN);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("FK", cardX + 198, r2Y);
        // Constraint: Short & Simple
        g.setColor(new Color(22, 101, 52));
        g.drawString("FK, Not Null", cardX + 235, r2Y);

        // Row 3: Standard Attribute column (Blank Key, "-")
        int r3Y = cardY + 79;
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        g.drawString("description", cardX + 8, r3Y);
        g.setColor(COLOR_TYPE_STRING);
        g.setFont(new Font("Consolas", Font.PLAIN, 8));
        g.drawString("VARCHAR2", cardX + 120, r3Y);
        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("Segoe UI", Font.ITALIC, 7));
        g.drawString("-", cardX + 201, r3Y);
        g.drawString("-", cardX + 235, r3Y);

        // Row 4: Overflow indicator
        int r4Y = cardY + 95;
        g.setColor(new Color(248, 250, 252));
        g.fillRect(cardX + 1, cardY + 84, cardW - 2, 16);
        g.setColor(new Color(100, 116, 139));
        g.setFont(new Font("Segoe UI", Font.ITALIC, 7));
        g.drawString("+7 more columns ... (Up to 9 displayed, PK & FK prioritized)", cardX + 8, r4Y);

        // SECTION 2: Vector Directed Relationship Lines & Arrow Endpoints (Right side)
        int relX = 345;

        // Top Row: Connectors (y = 56)
        // 1. One-to-Many FK Directed Arrow (->)
        drawVectorArrow1ToMany(g, relX, 56);
        // Midpoint Mermaid edge label pill
        g.setColor(Color.WHITE);
        g.fillRoundRect(relX + 32, 48, 112, 15, 4, 4);
        g.setColor(new Color(203, 213, 225));
        g.drawRoundRect(relX + 32, 48, 112, 15, 4, 4);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("id -> parent_id [FK]", relX + 36, 59);

        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("1-to-Many FK (->)", relX + 154, 52);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("Parent PK -> Child FK Directed Reference", relX + 154, 62);

        // 2. Equi-Join Bidirectional Arrow (<->)
        drawVectorArrowEquiJoin(g, relX + 278, 56);
        g.setColor(Color.WHITE);
        g.fillRoundRect(relX + 308, 48, 80, 15, 4, 4);
        g.setColor(new Color(203, 213, 225));
        g.drawRoundRect(relX + 308, 48, 80, 15, 4, 4);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("col_a <-> col_b", relX + 312, 59);

        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("Equi-Join (<->)", relX + 396, 52);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("Shared Domain Key Join", relX + 396, 62);

        // Middle Row: Constraint Flags & Join Types (y = 86)
        // [ENFORCED]
        g.setColor(COLOR_GREEN);
        g.fillRoundRect(relX, 79, 62, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("ENFORCED", relX + 5, 90);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("Physical DDL Constraint", relX + 70, 85);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("ALL_CONSTRAINTS Enforced", relX + 70, 95);

        // [SQL]
        g.setColor(COLOR_GOLD);
        g.fillRoundRect(relX + 278, 79, 36, 14, 4, 4);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("SQL", relX + 286, 90);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("Inferred Procedure Join", relX + 322, 85);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("Mined from ETL & Stored Procs", relX + 322, 95);

        // Bottom Row: Target & External Table Roles (y = 118)
        // [TARGET] - Plum Theme
        g.setColor(COLOR_PLUM);
        g.fillRoundRect(relX, 111, 62, 14, 4, 4);
        g.setColor(COLOR_GOLD);
        g.drawRoundRect(relX, 111, 62, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("TARGET", relX + 11, 122);
        g.setColor(COLOR_PLUM_DARK);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("Focal Subject Table", relX + 70, 116);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("Up to 9 Priority Columns Shown", relX + 70, 126);

        // [EXT FILE]
        g.setColor(new Color(180, 83, 9));
        g.fillRoundRect(relX + 278, 111, 54, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 7));
        g.drawString("EXT FILE", relX + 284, 122);
        g.setColor(COLOR_NAVY);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        g.drawString("External Flat-File", relX + 340, 116);
        g.setColor(COLOR_SLATE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
        g.drawString("ORGANIZATION EXTERNAL Bridge", relX + 340, 126);

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

    /**
     * Renders a standardized Florida Lottery Relational & Join Notation Legend in Word documents.
     * Prioritizes the native Mermaid-rendered vector legend, falling back to the ultra-crisp Retina
     * Java2D graphic card if the Mermaid rendering environment is offline or unavailable.
     */
    public static void renderRelationshipLegendCallout(XWPFDocument doc) {
        // Omitted per design requirement
        return;
    }

    /**
     * Enumeration of supported join connector geometries across Mermaid and Java2D diagrams.
     * Accurately supports all four enterprise join connector types:
     *   1. DIRECTED_FORWARD (->): Parent PK -> Child FK (Directed One-to-Many foreign key)
     *   2. DIRECTED_REVERSE (<-): Child FK <- Parent PK (Reverse directed referential link)
     *   3. BIDIRECTIONAL (<->): Shared Domain Key / Mutual PK join across sibling entities
     *   4. UNDIRECTED (--): Equi-Join / Non-directional procedural SQL join
     */
    public enum JoinConnectorType {
        DIRECTED_FORWARD("-->", "->"),     // Table A -> Table B (A has PK, B has FK / Parent to Child)
        DIRECTED_REVERSE("-->", "<-"),     // Table A <- Table B (A has FK, B has PK / Child to Parent)
        BIDIRECTIONAL("<-->", "<->"),      // Table A <-> Table B (Shared Domain Key / Mutual Join)
        UNDIRECTED("---", "--");           // Table A -- Table B (Equi-Join / Procedural Join)

        public final String mermaidSyntax;
        public final String symbol;

        JoinConnectorType(String mermaidSyntax, String symbol) {
            this.mermaidSyntax = mermaidSyntax;
            this.symbol = symbol;
        }

        public static JoinConnectorType fromSymbol(String sym) {
            if (sym == null) {
                JoinConnectorType result = DIRECTED_FORWARD;
                return result;
            }
            if (sym.contains("<->")) {
                JoinConnectorType result = BIDIRECTIONAL;
                return result;
            }
            if (sym.contains("<-")) {
                JoinConnectorType result = DIRECTED_REVERSE;
                return result;
            }
            if (sym.contains("->")) {
                JoinConnectorType result = DIRECTED_FORWARD;
                return result;
            }
            if (sym.contains("--") || sym.contains(" = ")) {
                JoinConnectorType result = UNDIRECTED;
                return result;
            }
            JoinConnectorType result = DIRECTED_FORWARD;
            return result;
        }
    }

    /**
     * Typed descriptor for explicit table relationship joins and Mermaid ER edge notation.
     */
    public static class JoinDescriptor {
        public final String label;
        public final String mermaidSymbol;
        public final JoinConnectorType connectorType;
        public final boolean isEnforced;
        public final boolean isProcedureJoin;
        public final boolean hasSharedKey;

        public JoinDescriptor(String label, String mermaidSymbol, boolean isEnforced, boolean isProcedureJoin) {
            this(label, mermaidSymbol, JoinConnectorType.fromSymbol(label), isEnforced, isProcedureJoin, true);
        }

        public JoinDescriptor(String label, String mermaidSymbol, boolean isEnforced, boolean isProcedureJoin, boolean hasSharedKey) {
            this(label, mermaidSymbol, JoinConnectorType.fromSymbol(label), isEnforced, isProcedureJoin, hasSharedKey);
        }

        public JoinDescriptor(String label, String mermaidSymbol, JoinConnectorType connectorType, boolean isEnforced, boolean isProcedureJoin, boolean hasSharedKey) {
            this.label = label;
            this.mermaidSymbol = mermaidSymbol;
            this.connectorType = connectorType != null ? connectorType : JoinConnectorType.fromSymbol(label);
            this.isEnforced = isEnforced;
            this.isProcedureJoin = isProcedureJoin;
            this.hasSharedKey = hasSharedKey;
        }

        public boolean hasSharedKey() {
            boolean result = this.hasSharedKey;
            return result;
        }

        public String getLabel() {
            String result = this.label;
            return result;
        }

        public String getMermaidSymbol() {
            String result = this.mermaidSymbol;
            return result;
        }

        public JoinConnectorType getConnectorType() {
            JoinConnectorType result = this.connectorType;
            return result;
        }

        public String getFlowchartConnector() {
            String result = this.connectorType.mermaidSyntax;
            return result;
        }
    }

    /**
     * Appends a valid Mermaid flowchart join connector matching the exact join semantics:
     *   - DIRECTED_FORWARD (->): NodeA -->|label| NodeB
     *   - DIRECTED_REVERSE (<-): NodeB -->|label| NodeA (inverts endpoint order so the arrow points into NodeA, avoiding Mermaid '<--' parser errors)
     *   - BIDIRECTIONAL (<->): NodeA <-->|label| NodeB
     *   - UNDIRECTED (--): NodeA ---|label| NodeB
     */
    public static void appendFlowchartEdge(StringBuilder sb, String nodeA, String nodeB, JoinDescriptor jd) {
        if (jd == null || !jd.hasSharedKey()) {
            sb.append("    ").append(nodeA).append(" --- ").append(nodeB).append("\n");
            return;
        }
        String label = jd.label;
        JoinConnectorType ct = jd.connectorType != null ? jd.connectorType : JoinConnectorType.fromSymbol(label);
        switch (ct) {
            case DIRECTED_FORWARD:
                sb.append("    ").append(nodeA).append(" -->|\"").append(label).append("\"| ").append(nodeB).append("\n");
                break;
            case DIRECTED_REVERSE:
                // NodeB points to NodeA, so visual arrowhead points towards NodeA (<-)
                sb.append("    ").append(nodeB).append(" -->|\"").append(label).append("\"| ").append(nodeA).append("\n");
                break;
            case BIDIRECTIONAL:
                sb.append("    ").append(nodeA).append(" <-->|\"").append(label).append("\"| ").append(nodeB).append("\n");
                break;
            case UNDIRECTED:
            default:
                sb.append("    ").append(nodeA).append(" ---|\"").append(label).append("\"| ").append(nodeB).append("\n");
                break;
        }
    }

    /**
     * Resolves the explicit join predicate or foreign key relationship between two tables.
     * Replaces ambiguous 'domain_cluster' or generic labels with precise 'col_a -- col_b', 'col_a -> col_b',
     * 'col_a <- col_b', or 'col_a <-> col_b'.
     * Returns an unjoined descriptor (hasSharedKey = false) if no legitimate referential link exists.
     */
    public static JoinDescriptor resolveJoin(
            GenerateTechnicalWordDocs.TableDocModel tableA,
            GenerateTechnicalWordDocs.TableDocModel tableB) {

        if (tableA == null || tableB == null) {
            JoinDescriptor result = new JoinDescriptor("", "", JoinConnectorType.UNDIRECTED, false, false, false);
            return result;
        }

        String schemaA = (tableA.schema != null && !tableA.schema.isEmpty()) ? tableA.schema : "SCHEMA";
        String schemaB = (tableB.schema != null && !tableB.schema.isEmpty()) ? tableB.schema : "SCHEMA";

        // 1. Check DbObjectSearcher for mined physical FK or SQL procedure join
        TableRelationship rel = DbObjectSearcher.getInstance()
                .findRelationshipBetween(schemaA, tableA.tableName, schemaB, tableB.tableName);

        if (rel != null) {
            boolean fromA = rel.getSourceTable().equalsIgnoreCase(tableA.tableName);
            String srcCol = rel.getSourceColumn();
            String tgtCol = rel.getTargetColumn();
            if (tgtCol == null || tgtCol.isEmpty()) tgtCol = srcCol;
            boolean enforced = rel.isEnforced();
            boolean procJoin = rel.getOccurrences() > 0;

            String label;
            String symbol;
            JoinConnectorType connType;

            if (enforced) {
                symbol = "||--o{";
                if (fromA) {
                    // tableA has the FK (child), referencing tableB (parent PK): A <- B
                    connType = JoinConnectorType.DIRECTED_REVERSE;
                    label = srcCol + " <- " + tgtCol + " [FK]";
                } else {
                    // tableA has the PK (parent), tableB has the FK (child): A -> B
                    connType = JoinConnectorType.DIRECTED_FORWARD;
                    label = tgtCol + " -> " + srcCol + " [FK]";
                }
            } else if (procJoin) {
                symbol = "}o--o{";
                connType = JoinConnectorType.UNDIRECTED;
                label = srcCol + " -- " + tgtCol + " [SQL]";
            } else {
                symbol = "}o--o{";
                connType = JoinConnectorType.UNDIRECTED;
                label = srcCol + " -- " + tgtCol;
            }
            JoinDescriptor result = new JoinDescriptor(label, symbol, connType, enforced, procJoin, true);
            return result;
        }

        // 2. Discover shared / matching columns between tableA and tableB
        Map<String, GenerateTechnicalWordDocs.TableColumnDoc> colsA = new HashMap<>();
        if (tableA.columns != null) {
            for (GenerateTechnicalWordDocs.TableColumnDoc c : tableA.columns) {
                if (c.columnName != null) colsA.put(c.columnName.toUpperCase(), c);
            }
        }
        Map<String, GenerateTechnicalWordDocs.TableColumnDoc> colsB = new HashMap<>();
        if (tableB.columns != null) {
            for (GenerateTechnicalWordDocs.TableColumnDoc c : tableB.columns) {
                if (c.columnName != null) colsB.put(c.columnName.toUpperCase(), c);
            }
        }

        Set<String> pksA = new HashSet<>();
        if (tableA.primaryKeys != null) {
            for (String pk : tableA.primaryKeys) {
                if (pk != null) pksA.add(pk.toUpperCase());
            }
        }
        Set<String> pksB = new HashSet<>();
        if (tableB.primaryKeys != null) {
            for (String pk : tableB.primaryKeys) {
                if (pk != null) pksB.add(pk.toUpperCase());
            }
        }

        String bestCol = null;
        int bestScore = -1;

        for (String cName : colsA.keySet()) {
            if (colsB.containsKey(cName)) {
                int score = 10;
                boolean isPkA = pksA.contains(cName);
                boolean isPkB = pksB.contains(cName);

                if (isPkA && isPkB) {
                    score = 120; // Shared primary domain key
                } else if (isPkA || isPkB) {
                    score = 100;
                } else if (cName.endsWith("_ID") || cName.endsWith("_NBR") || cName.endsWith("_NUM")
                        || cName.endsWith("_NO") || cName.endsWith("_CD") || cName.endsWith("_CODE") || cName.endsWith("_KEY")) {
                    score = 80;
                } else if (cName.equals("STATUS") || cName.endsWith("_STATUS") || cName.endsWith("_DATE") || cName.endsWith("_TYPE")) {
                    score = 40;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestCol = cName;
                }
            }
        }

        if (bestCol != null) {
            boolean isPkA = pksA.contains(bestCol);
            boolean isPkB = pksB.contains(bestCol);

            if (isPkA && !isPkB) {
                // Table A has PK, Table B has FK -> Table A -> Table B
                String label = bestCol + " -> " + bestCol + " [FK]";
                JoinDescriptor result = new JoinDescriptor(label, "||--o{", JoinConnectorType.DIRECTED_FORWARD, false, false, true);
                return result;
            } else if (!isPkA && isPkB) {
                // Table B has PK, Table A has FK -> Table A <- Table B
                String label = bestCol + " <- " + bestCol + " [FK]";
                JoinDescriptor result = new JoinDescriptor(label, "||--o{", JoinConnectorType.DIRECTED_REVERSE, false, false, true);
                return result;
            } else if (isPkA && isPkB) {
                // Both tables share this column as PK -> Shared Domain Key Mutual Join: Table A <-> Table B
                String label = bestCol + " <-> " + bestCol;
                JoinDescriptor result = new JoinDescriptor(label, "}o--o{", JoinConnectorType.BIDIRECTIONAL, false, false, true);
                return result;
            } else {
                // Neither is PK -> Undirected Equi-Join: Table A -- Table B
                String label = bestCol + " -- " + bestCol;
                JoinDescriptor result = new JoinDescriptor(label, "}o--o{", JoinConnectorType.UNDIRECTED, false, false, true);
                return result;
            }
        }

        // 3. Check for naming-convention foreign keys (e.g. TABLE_B_ID in TABLE_A)
        String baseB = tableB.tableName.toUpperCase();
        for (String cName : colsA.keySet()) {
            if (cName.equals(baseB + "_ID") || cName.equals(baseB + "_NBR") || cName.equals(baseB + "_CD")) {
                String tgtPk = !pksB.isEmpty() ? pksB.iterator().next() : cName;
                // Table B is Parent (PK), Table A is Child (FK) -> A <- B
                String label = cName + " <- " + tgtPk + " [FK]";
                JoinDescriptor result = new JoinDescriptor(label, "||--o{", JoinConnectorType.DIRECTED_REVERSE, false, false, true);
                return result;
            }
        }

        String baseA = tableA.tableName.toUpperCase();
        for (String cName : colsB.keySet()) {
            if (cName.equals(baseA + "_ID") || cName.equals(baseA + "_NBR") || cName.equals(baseA + "_CD")) {
                String srcPk = !pksA.isEmpty() ? pksA.iterator().next() : cName;
                // Table A is Parent (PK), Table B is Child (FK) -> A -> B
                String label = srcPk + " -> " + cName + " [FK]";
                JoinDescriptor result = new JoinDescriptor(label, "||--o{", JoinConnectorType.DIRECTED_FORWARD, false, false, true);
                return result;
            }
        }

        // 4. Standalone: No legitimate foreign key or shared domain column exists
        JoinDescriptor result = new JoinDescriptor("", "", JoinConnectorType.UNDIRECTED, false, false, false);
        return result;
    }
}
