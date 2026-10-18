package com.fllottery.dbresearch.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Table Documentation & Lineage Model representing an entire Oracle database table.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class TableDocModel implements Serializable {

    private static final long serialVersionUID = 1L;

    public String databaseName = "DEVOLTP";
    public String schema = "ARS";
    public String tableName = "";
    public String docId = "";
    public String docPath = "";
    public String tableType = "RELATIONAL TABLE";
    public String description = "Core enterprise persistence table.";
    public String comments = "Core enterprise persistence table.";
    public String tableRole = "";
    public String worldView = "";
    public Long numRows = 0L;
    public Integer avgRowLen = 0;
    public String lastAnalyzed = "";
    public List<TableColumnDoc> columns = new ArrayList<>();
    public List<String> primaryKeys = new ArrayList<>();
    public List<String> referencingRoutines = new ArrayList<>();
    public List<TableIndexDoc> indexes = new ArrayList<>();
    public List<TableTriggerDoc> triggers = new ArrayList<>();
    public List<TableConstraintDoc> constraints = new ArrayList<>();
    public boolean isExternal = false;
    public String driverType = "";
    public String defaultDirectory = "";
    public String accessType = "";
    public String accessParameters = "";
    public List<String> externalLocations = new ArrayList<>();

    public TableDocModel() {
    }

    public static TableDocModel fromJson(String schemaName, JsonNode tbl) {
        if (tbl == null) {
            TableDocModel result = null;
            return result;
        }
        String tName = tbl.path("TableName").asText(tbl.path("Name").asText("")).trim().toUpperCase();
        if (tName.isEmpty()) {
            TableDocModel result = null;
            return result;
        }

        TableDocModel m = new TableDocModel();
        m.schema = (schemaName != null && !schemaName.isEmpty()) ? schemaName.toUpperCase() : "ARS";
        m.tableName = tName;
        m.docId = "TBL-" + m.schema + "-" + m.tableName;
        m.docPath = m.schema + "/Tables/" + m.docId + ".docx";
        m.tableType = tbl.path("TableType").asText(tbl.path("Type").asText("RELATIONAL TABLE"));
        m.description = tbl.path("Comments").asText(tbl.path("Description").asText("Core enterprise persistence table."));
        m.comments = m.description;

        if (tbl.hasNonNull("NumRows")) m.numRows = tbl.path("NumRows").asLong();
        if (tbl.hasNonNull("AvgRowLen")) m.avgRowLen = tbl.path("AvgRowLen").asInt();
        if (tbl.hasNonNull("LastAnalyzed")) m.lastAnalyzed = tbl.path("LastAnalyzed").asText();

        m.isExternal = tbl.path("IsExternal").asBoolean(false) || m.tableType.toUpperCase().contains("EXTERNAL");
        JsonNode extNode = tbl.path("ExternalTable");
        if (!extNode.isObject()) {
            extNode = tbl.path("ExternalTableDefinition");
        }
        if (extNode.isObject()) {
            m.isExternal = true;
            m.tableType = "EXTERNAL TABLE";
            m.driverType = extNode.path("DriverType").asText("ORACLE_LOADER");
            m.defaultDirectory = extNode.path("DefaultDirectory").asText("");
            m.accessType = extNode.path("AccessType").asText("");
            m.accessParameters = extNode.path("AccessParameters").asText("");
            JsonNode locArr = extNode.path("Locations");
            if (locArr.isArray()) {
                for (JsonNode loc : locArr) {
                    if (loc.isObject()) {
                        String fName = loc.path("FileName").asText("");
                        if (!fName.isEmpty()) m.externalLocations.add(fName);
                    } else if (loc.isTextual()) {
                        String locStr = loc.asText("");
                        if (!locStr.isEmpty()) m.externalLocations.add(locStr);
                    }
                }
            }
        }

        JsonNode idxArr = tbl.path("Indexes");
        if (idxArr.isArray()) {
            for (JsonNode in : idxArr) {
                TableIndexDoc idoc = TableIndexDoc.fromJson(in);
                if (!idoc.indexName.isEmpty()) m.indexes.add(idoc);
            }
        }

        JsonNode trgArr = tbl.path("Triggers");
        if (trgArr.isArray()) {
            for (JsonNode tn : trgArr) {
                TableTriggerDoc tdoc = TableTriggerDoc.fromJson(tn);
                if (!tdoc.triggerName.isEmpty()) m.triggers.add(tdoc);
            }
        }

        JsonNode colsNode = tbl.path("Columns");
        if (!colsNode.isArray()) colsNode = tbl.path("columns");
        if (!colsNode.isArray()) colsNode = tbl.path("Fields");
        if (colsNode.isArray()) {
            for (JsonNode cNode : colsNode) {
                TableColumnDoc col = TableColumnDoc.fromJson(cNode);
                if (!col.columnName.isEmpty()) {
                    m.columns.add(col);
                }
            }
        }

        JsonNode pkNode = tbl.path("PrimaryKey");
        if (pkNode.isArray()) {
            for (JsonNode pk : pkNode) m.primaryKeys.add(pk.asText("").toUpperCase());
        } else if (pkNode.isTextual() && !pkNode.asText("").isEmpty()) {
            m.primaryKeys.add(pkNode.asText("").toUpperCase());
        }

        if (m.primaryKeys.isEmpty()) {
            for (TableColumnDoc col : m.columns) {
                if (col.columnName.equalsIgnoreCase("ID") ||
                        col.columnName.equalsIgnoreCase(m.tableName + "_ID") ||
                        (col.columnName.endsWith("_ID") && col.isNullable.contains("NOT NULL"))) {
                    col.constraintType = "PRIMARY KEY";
                    m.primaryKeys.add(col.columnName);
                    break;
                }
            }
        }

        TableDocModel result = m;
        return result;
    }

    public String getQualifiedName() {
        String result = this.schema + "." + this.tableName;
        return result;
    }
}
