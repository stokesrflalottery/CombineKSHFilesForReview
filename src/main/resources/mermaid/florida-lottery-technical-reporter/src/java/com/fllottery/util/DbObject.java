package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Enterprise Database Object model representing tables, views, procedures, packages, functions, and triggers.
 * <p>
 * Bridges table, view, procedure, and package metadata across Korn shell parsers ({@code KshParser}),
 * Word documentation generators ({@code GenerateTechnicalWordDocs}), and relationship discovery engines ({@code DbObjectSearcher}).
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery Architecture
 * @since 2026-10-11
 */
public class DbObject extends DbObjectSearcher.DbObject {

    public DbObject(String schema, String objectName, String objectType) {
        super(schema, objectName, objectType);
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
        super(schema, objectName, objectType, database, columns, primaryKeys, description, rawJson);
    }
}
