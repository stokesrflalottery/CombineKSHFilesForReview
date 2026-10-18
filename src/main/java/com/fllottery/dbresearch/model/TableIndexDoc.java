package com.fllottery.dbresearch.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;

/**
 * Index metadata documentation model.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class TableIndexDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    public String indexName = "";
    public String uniqueness = "NONUNIQUE";
    public String indexType = "NORMAL";
    public String status = "VALID";
    public String columns = "";

    public TableIndexDoc() {
    }

    public TableIndexDoc(String indexName, String uniqueness, String indexType, String columns) {
        this.indexName = indexName != null ? indexName.trim().toUpperCase() : "";
        this.uniqueness = uniqueness != null ? uniqueness.trim().toUpperCase() : "NONUNIQUE";
        this.indexType = indexType != null ? indexType.trim().toUpperCase() : "NORMAL";
        this.columns = columns != null ? columns.trim().toUpperCase() : "";
        this.status = "VALID";
    }

    public static TableIndexDoc fromJson(JsonNode in) {
        TableIndexDoc doc = new TableIndexDoc();
        if (in == null) {
            TableIndexDoc result = doc;
            return result;
        }
        doc.indexName = in.path("IndexName").asText(in.path("Name").asText("")).trim().toUpperCase();
        doc.uniqueness = in.path("Uniqueness").asText("NONUNIQUE").trim().toUpperCase();
        doc.indexType = in.path("IndexType").asText("NORMAL").trim().toUpperCase();
        doc.status = in.path("Status").asText("VALID").trim().toUpperCase();
        doc.columns = in.path("Columns").asText("").trim().toUpperCase();
        TableIndexDoc result = doc;
        return result;
    }

    public String getIndexName() {
        String result = this.indexName;
        return result;
    }

    public String getUniqueness() {
        String result = this.uniqueness;
        return result;
    }

    public String getIndexType() {
        String result = this.indexType;
        return result;
    }

    public String getStatus() {
        String result = this.status;
        return result;
    }

    public String getColumns() {
        String result = this.columns;
        return result;
    }
}
