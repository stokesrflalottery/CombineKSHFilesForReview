package com.fllottery.dbresearch.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;

/**
 * Column metadata documentation model.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class TableColumnDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    public String columnName = "";
    public String dataType = "VARCHAR2";
    public String isNullable = "YES";
    public String defaultValue = "-";
    public String constraintType = "-";
    public String comments = "";

    public TableColumnDoc() {
    }

    public TableColumnDoc(String columnName, String dataType, String isNullable, String defaultValue, String constraintType, String comments) {
        this.columnName = columnName != null ? columnName.trim().toUpperCase() : "";
        this.dataType = dataType != null ? dataType.trim().toUpperCase() : "VARCHAR2";
        this.isNullable = isNullable != null ? isNullable.trim() : "YES";
        this.defaultValue = defaultValue != null ? defaultValue.trim() : "-";
        this.constraintType = constraintType != null ? constraintType.trim() : "-";
        this.comments = comments != null ? comments.trim() : "";
    }

    public static TableColumnDoc fromJson(JsonNode c) {
        TableColumnDoc col = new TableColumnDoc();
        if (c == null) {
            TableColumnDoc result = col;
            return result;
        }
        col.columnName = c.path("ColumnName").asText(c.path("Name").asText("")).trim().toUpperCase();
        String rawType = c.path("DataType").asText(c.path("Type").asText("VARCHAR2")).trim().toUpperCase();
        int len = c.path("DataLength").asInt(c.path("Length").asInt(0));
        int prec = c.path("Precision").asInt(c.path("DataPrecision").asInt(0));
        int scale = c.path("Scale").asInt(c.path("DataScale").asInt(-1));

        if (prec > 0) {
            if (scale >= 0) {
                col.dataType = String.format("%s(%d,%d)", rawType, prec, scale);
            } else {
                col.dataType = String.format("%s(%d)", rawType, prec);
            }
        } else if (len > 0 && (rawType.contains("CHAR") || rawType.contains("RAW"))) {
            col.dataType = String.format("%s(%d)", rawType, len);
        } else {
            col.dataType = rawType;
        }

        String nullStr = c.path("Nullable").asText(c.path("IsNullable").asText("Y")).trim().toUpperCase();
        col.isNullable = (nullStr.startsWith("N") || nullStr.equals("FALSE")) ? "NO (NOT NULL)" : "YES";
        col.defaultValue = c.path("DefaultValue").asText(c.path("Default").asText(c.path("DataDefault").asText("-"))).trim();
        col.constraintType = c.path("Constraint").asText(c.path("KeyType").asText("-")).trim();
        col.comments = c.path("Comments").asText(c.path("Description").asText("")).trim();

        TableColumnDoc result = col;
        return result;
    }

    public String getColumnName() {
        String result = this.columnName;
        return result;
    }

    public String getDataType() {
        String result = this.dataType;
        return result;
    }
}
