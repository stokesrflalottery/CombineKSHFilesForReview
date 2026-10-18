package com.fllottery.dbresearch.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Enterprise Database Object Model representing tables, views, procedures, packages, functions, and triggers.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class DbObject implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String schema;
    private final String objectName;
    private final String objectType;
    private final String canonicalName;
    private final String database;
    private final String status;
    private final List<String> columns;
    private final List<String> primaryKeys;
    private final String description;
    private final JsonNode rawJson;

    public DbObject(String schema, String objectName, String objectType) {
        this(schema, objectName, objectType, "DEVOLTP", Collections.emptyList(), Collections.emptyList(), "", null);
    }

    public DbObject(
            String schema,
            String objectName,
            String objectType,
            String database,
            List<String> columns,
            List<String> primaryKeys,
            String description,
            JsonNode rawJson) {
        this.schema = schema != null ? schema.trim().toUpperCase() : "UNKNOWN";
        this.objectName = objectName != null ? objectName.trim().toUpperCase() : "UNKNOWN";
        this.objectType = objectType != null ? objectType.trim().toUpperCase() : "DATABASE OBJECT";
        this.canonicalName = this.schema + "." + this.objectName;
        this.database = database != null ? database.trim().toUpperCase() : "DEVOLTP";
        this.status = "VALID";
        this.columns = columns != null ? Collections.unmodifiableList(new ArrayList<>(columns)) : Collections.emptyList();
        this.primaryKeys = primaryKeys != null ? Collections.unmodifiableList(new ArrayList<>(primaryKeys)) : Collections.emptyList();
        this.description = description != null ? description.trim() : "";
        this.rawJson = rawJson;
    }

    public String getSchema() {
        String result = this.schema;
        return result;
    }

    public String getOwner() {
        String result = this.schema;
        return result;
    }

    public String getObjectName() {
        String result = this.objectName;
        return result;
    }

    public String getName() {
        String result = this.objectName;
        return result;
    }

    public String getTableName() {
        String result = this.objectName;
        return result;
    }

    public String getRoutineName() {
        String result = this.objectName;
        return result;
    }

    public String getPackageName() {
        if ("PACKAGE".equalsIgnoreCase(this.objectType) || "PACKAGE BODY".equalsIgnoreCase(this.objectType)) {
            String result = this.objectName;
            return result;
        }
        if (this.objectName.contains(".")) {
            int dotIdx = this.objectName.indexOf('.');
            String result = this.objectName.substring(0, dotIdx);
            return result;
        }
        String result = "-";
        return result;
    }

    public String getObjectType() {
        String result = this.objectType;
        return result;
    }

    public String getType() {
        String result = this.objectType;
        return result;
    }

    public String getCanonicalName() {
        String result = this.canonicalName;
        return result;
    }

    public String getQualifiedName() {
        String result = this.canonicalName;
        return result;
    }

    public String getDatabase() {
        String result = this.database;
        return result;
    }

    public String getDatabaseName() {
        String result = this.database;
        return result;
    }

    public String getStatus() {
        String result = this.status;
        return result;
    }

    public List<String> getColumns() {
        List<String> result = this.columns;
        return result;
    }

    public List<String> getPrimaryKeys() {
        List<String> result = this.primaryKeys;
        return result;
    }

    public String getDescription() {
        String result = this.description;
        return result;
    }

    public String getComments() {
        String result = this.description;
        return result;
    }

    public JsonNode getRawJson() {
        JsonNode result = this.rawJson;
        return result;
    }

    public JsonNode getJsonNode() {
        JsonNode result = this.rawJson;
        return result;
    }

    public boolean isTable() {
        boolean result = "TABLE".equalsIgnoreCase(this.objectType) || "RELATIONAL TABLE".equalsIgnoreCase(this.objectType);
        return result;
    }

    public boolean isView() {
        boolean result = "VIEW".equalsIgnoreCase(this.objectType) || "MATERIALIZED VIEW".equalsIgnoreCase(this.objectType);
        return result;
    }

    public boolean isProcedure() {
        boolean result = "PROCEDURE".equalsIgnoreCase(this.objectType);
        return result;
    }

    public boolean isPackage() {
        boolean result = "PACKAGE".equalsIgnoreCase(this.objectType) || "PACKAGE BODY".equalsIgnoreCase(this.objectType);
        return result;
    }

    public boolean isFunction() {
        boolean result = "FUNCTION".equalsIgnoreCase(this.objectType);
        return result;
    }

    public boolean isTrigger() {
        boolean result = "TRIGGER".equalsIgnoreCase(this.objectType);
        return result;
    }

    public boolean isSynonym() {
        boolean result = "SYNONYM".equalsIgnoreCase(this.objectType);
        return result;
    }

    @Override
    public String toString() {
        String result = this.canonicalName + " [" + this.objectType + "]";
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DbObject dbObject = (DbObject) o;
        boolean result = Objects.equals(canonicalName, dbObject.canonicalName);
        return result;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(canonicalName);
        return result;
    }
}
