package com.fllottery.dbresearch.search;

import com.fllottery.common.io.FlCachePaths;
import com.fllottery.common.json.JsonMapperProvider;
import com.fllottery.dbresearch.model.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Database Object Catalog, Cross-Schema Symbol Registry, and Multi-Engine
 * Table Relationship Discovery Engine for the Florida Lottery.
 * <p>
 * Acts as "The Brain" of the database documentation and lineage architecture.
 * Combines two distinct, high-precision relationship discovery paradigms:
 * <ol>
 *   <li><b>Engine 1: Definitive Physical Data Dictionary Constraints (100% Enforced)</b><br>
 *       Extracts Foreign Keys directly from Oracle {@code ALL_CONSTRAINTS} (type 'R'), resolving
 *       {@code R_OWNER} and {@code R_CONSTRAINT_NAME} to map precise parent/child column pairs
 *       across different database schemas (e.g. TRANSLATOR referencing ARS, GAME, or GL).</li>
 *   <li><b>Engine 2: Static AST / SQL Procedure Join Lineage Mining (Inferred from Code)</b><br>
 *       Performs static code analysis across stored procedures, packages, functions, and views
 *       to extract and track SQL {@code JOIN} conditions (ANSI {@code INNER/LEFT/RIGHT JOIN}
 *       and implicit {@code FROM ... WHERE tableA.col = tableB.col}). Ranks relationships by
 *       execution frequency and aggregates the exact referencing routines.</li>
 *   <li><b>Engine 3: Domain Heuristic & Enterprise Naming Convention Fallback</b><br>
 *       Standard Florida Lottery entity resolution for unconstrained legacy tables.</li>
 * </ol>
 * All data transfer models, table document representations, column definitions, and object descriptors
 * are defined directly as static inner classes within this class to enable modular, standalone usage.
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery Architecture
 * @since 2026-10-11
 */
public class DbObjectSearcher {

    private static final ObjectMapper MAPPER = JsonMapperProvider.get();
    private static final DbObjectSearcher INSTANCE = new DbObjectSearcher();

    // Default file locations matching Florida Lottery OCI server architecture
    public static final String DEFAULT_DBCACHE_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json";
    public static final String DEFAULT_CATALOG_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\DbCache_Ai_Researched_Catalog.json";

    // Known Florida Lottery Core Schemas
    public static final Set<String> KNOWN_SCHEMAS = Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(
            "TRANSLATOR", "ARS", "GAME", "INET", "PPS", "LMS", "GL", "DWDOL", "WRS", "FINANCE", "SYS"
    )));

    // =========================================================================
    // ENUMS & MODELS (Self-Contained)
    // =========================================================================

    /**
     * Type of relationship discovery provenance.
     */
    public enum RelationshipType {
        PHYSICAL_FOREIGN_KEY("PHYSICAL FK [ALL_CONSTRAINTS]", "Solid referential integrity constraint enforced by Oracle RDBMS kernel"),
        INFERRED_PROCEDURE_JOIN("INFERRED SQL JOIN [PROCEDURE LINEAGE]", "Static SQL join mined from stored procedures, packages, or batch jobs"),
        INFERRED_NAMING_CONVENTION("INFERRED CONVENTION [SCHEMA STANDARD]", "Inferred from enterprise column and entity naming conventions");

        private final String label;
        private final String description;

        RelationshipType(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            String result = this.label;
            return result;
        }

        public String getDescription() {
            String result = this.description;
            return result;
        }
    }

    /**
     * Confidence rating for the discovered relationship.
     */
    public enum ConfidenceLevel {
        CONFIRMED_ENFORCED(1.0, "100% Enforced Physical Constraint"),
        HIGH_SQL_LINEAGE(0.90, "High Confidence (Mined from Multiple SQL Routines)"),
        MODERATE_SQL_LINEAGE(0.75, "Moderate Confidence (Mined from Single SQL Routine)"),
        INFERRED_CONVENTION(0.50, "Convention Based (Unconstrained Heuristic)");

        private final double score;
        private final String description;

        ConfidenceLevel(double score, String description) {
            this.score = score;
            this.description = description;
        }

        public double getScore() {
            double result = this.score;
            return result;
        }

        public String getDescription() {
            String result = this.description;
            return result;
        }
    }

    /**
     * Immutable relationship model connecting two database tables.
     */
    public static class TableRelationship implements Comparable<TableRelationship> {
        private final String sourceSchema;
        private final String sourceTable;
        private final String sourceColumn;
        private final String targetSchema;
        private final String targetTable;
        private final String targetColumn;
        private final RelationshipType relationshipType;
        private final ConfidenceLevel confidenceLevel;
        private final String constraintName;
        private final String joinType;
        private final String joinConditionSql;
        private final int occurrences;
        private final List<String> referencingRoutines;
        private final boolean enforced;
        private final String provenance;

        public TableRelationship(
                String sourceSchema,
                String sourceTable,
                String sourceColumn,
                String targetSchema,
                String targetTable,
                String targetColumn,
                RelationshipType relationshipType,
                ConfidenceLevel confidenceLevel,
                String constraintName,
                String joinType,
                String joinConditionSql,
                int occurrences,
                List<String> referencingRoutines,
                boolean enforced,
                String provenance) {
            this.sourceSchema = sourceSchema != null ? sourceSchema.toUpperCase() : "SCHEMA";
            this.sourceTable = sourceTable != null ? sourceTable.toUpperCase() : "TABLE";
            this.sourceColumn = sourceColumn != null ? sourceColumn.toUpperCase() : "";
            this.targetSchema = targetSchema != null ? targetSchema.toUpperCase() : "SCHEMA";
            this.targetTable = targetTable != null ? targetTable.toUpperCase() : "TABLE";
            this.targetColumn = targetColumn != null ? targetColumn.toUpperCase() : "";
            this.relationshipType = relationshipType;
            this.confidenceLevel = confidenceLevel;
            this.constraintName = constraintName;
            this.joinType = joinType != null ? joinType.toUpperCase() : "INNER JOIN";
            this.joinConditionSql = joinConditionSql;
            this.occurrences = occurrences;
            this.referencingRoutines = referencingRoutines != null ? Collections.unmodifiableList(new ArrayList<>(referencingRoutines)) : Collections.emptyList();
            this.enforced = enforced;
            this.provenance = provenance;
        }

        public String getSourceSchema() {
            String result = this.sourceSchema;
            return result;
        }

        public String getSourceTable() {
            String result = this.sourceTable;
            return result;
        }

        public String getSourceQualifiedName() {
            String result = this.sourceSchema + "." + this.sourceTable;
            return result;
        }

        public String getSourceColumn() {
            String result = this.sourceColumn;
            return result;
        }

        public String getTargetSchema() {
            String result = this.targetSchema;
            return result;
        }

        public String getTargetTable() {
            String result = this.targetTable;
            return result;
        }

        public String getTargetQualifiedName() {
            String result = this.targetSchema + "." + this.targetTable;
            return result;
        }

        public String getTargetColumn() {
            String result = this.targetColumn;
            return result;
        }

        public RelationshipType getRelationshipType() {
            RelationshipType result = this.relationshipType;
            return result;
        }

        public ConfidenceLevel getConfidenceLevel() {
            ConfidenceLevel result = this.confidenceLevel;
            return result;
        }

        public String getConstraintName() {
            String result = this.constraintName;
            return result;
        }

        public String getJoinType() {
            String result = this.joinType;
            return result;
        }

        public String getJoinConditionSql() {
            String result = this.joinConditionSql;
            return result;
        }

        public int getOccurrences() {
            int result = this.occurrences;
            return result;
        }

        public List<String> getReferencingRoutines() {
            List<String> result = this.referencingRoutines;
            return result;
        }

        public boolean isEnforced() {
            boolean result = this.enforced;
            return result;
        }

        public String getProvenance() {
            String result = this.provenance;
            return result;
        }

        public boolean isCrossSchema() {
            boolean result = !this.sourceSchema.equalsIgnoreCase(this.targetSchema);
            return result;
        }

        public String toSummaryString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getSourceQualifiedName()).append(" (").append(sourceColumn).append(") ");
            sb.append(enforced ? "==[ENFORCED FK]==> " : "--[INFERRED JOIN (" + occurrences + " routines)]--> ");
            sb.append(getTargetQualifiedName()).append(" (").append(targetColumn).append(")");
            if (isCrossSchema()) {
                sb.append(" [CROSS-SCHEMA]");
            }
            String result = sb.toString();
            return result;
        }

        @Override
        public int compareTo(TableRelationship o) {
            // Priority 1: Enforced physical FKs always beat inferred joins
            if (this.enforced != o.enforced) {
                int result = this.enforced ? -1 : 1;
                return result;
            }
            // Priority 2: Higher confidence
            int confCmp = Double.compare(o.confidenceLevel.getScore(), this.confidenceLevel.getScore());
            if (confCmp != 0) {
                int result = confCmp;
                return result;
            }
            // Priority 3: More routine occurrences
            int occCmp = Integer.compare(o.occurrences, this.occurrences);
            if (occCmp != 0) {
                int result = occCmp;
                return result;
            }
            // Priority 4: Target table name
            int result = this.targetTable.compareTo(o.targetTable);
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TableRelationship that = (TableRelationship) o;
            boolean result = Objects.equals(sourceSchema, that.sourceSchema) &&
                    Objects.equals(sourceTable, that.sourceTable) &&
                    Objects.equals(sourceColumn, that.sourceColumn) &&
                    Objects.equals(targetSchema, that.targetSchema) &&
                    Objects.equals(targetTable, that.targetTable) &&
                    Objects.equals(targetColumn, that.targetColumn);
            return result;
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(sourceSchema, sourceTable, sourceColumn, targetSchema, targetTable, targetColumn);
            return result;
        }
    }

    /**
     * Column metadata documentation model.
     */
    public static class TableColumnDoc {
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

    /**
     * Constraint metadata documentation model.
     */
    public static class TableConstraintDoc {
        public String constraintName = "";
        public String constraintType = "";
        public String columns = "";
        public String details = "";
        public String status = "ENABLED";

        public TableConstraintDoc() {
        }

        public TableConstraintDoc(String name, String type, String cols, String details, String status) {
            this.constraintName = name != null ? name.trim().toUpperCase() : "";
            this.constraintType = type != null ? type.trim().toUpperCase() : "";
            this.columns = cols != null ? cols.trim().toUpperCase() : "";
            this.details = details != null ? details.trim() : "";
            this.status = status != null ? status.trim() : "ENABLED";
        }
    }

    /**
     * Index metadata documentation model.
     */
    public static class TableIndexDoc {
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
    }

    /**
     * Trigger metadata documentation model.
     */
    public static class TableTriggerDoc {
        public String triggerName = "";
        public String triggerType = "ROW";
        public String triggeringEvent = "UPDATE";
        public String status = "ENABLED";
        public String description = "";

        public TableTriggerDoc() {
        }

        public TableTriggerDoc(String triggerName, String triggerType, String triggeringEvent, String status, String description) {
            this.triggerName = triggerName != null ? triggerName.trim().toUpperCase() : "";
            this.triggerType = triggerType != null ? triggerType.trim() : "ROW";
            this.triggeringEvent = triggeringEvent != null ? triggeringEvent.trim() : "UPDATE";
            this.status = status != null ? status.trim() : "ENABLED";
            this.description = description != null ? description.trim() : "";
        }

        public static TableTriggerDoc fromJson(JsonNode n) {
            TableTriggerDoc doc = new TableTriggerDoc();
            if (n == null) {
                TableTriggerDoc result = doc;
                return result;
            }
            doc.triggerName = n.path("TriggerName").asText("").trim().toUpperCase();
            doc.triggerType = n.path("TriggerType").asText("ROW").trim();
            doc.triggeringEvent = n.path("TriggeringEvent").asText("UPDATE").trim();
            doc.status = n.path("Status").asText("ENABLED").trim();
            doc.description = n.path("Description").asText(n.path("Comments").asText("")).trim();
            TableTriggerDoc result = doc;
            return result;
        }
    }

    /**
     * Enterprise Table Documentation & Lineage Model representing an entire database table.
     */
    public static class TableDocModel {
        public String databaseName = "DEVOLTP";
        public String schema = "ARS";
        public String tableName = "";
        public String docId = "";
        public String docPath = "";
        public String tableType = "RELATIONAL TABLE";
        public String description = "Core enterprise persistence table.";
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

    /**
     * Catalog entry representing a table cached in the global searcher.
     */
    public static class TableCatalogEntry {
        public String schema;
        public String tableName;
        public String tableType;
        public Long numRows;
        public List<String> primaryKeys = new ArrayList<>();
        public Map<String, String> columns = new LinkedHashMap<>(); // colName -> dataType
        public TableDocModel tableDocModel;
    }

    /**
     * Enterprise Database Object Model representing tables, views, procedures, packages, functions, and triggers.
     * <p>
     * Used by {@code KshParser} and documentation pipelines to inspect object schema, name, type,
     * columns, and primary keys.
     * </p>
     */
    public static class DbObject {
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

    // =========================================================================
    // IN-MEMORY CATALOG CACHES (Thread-Safe)
    // =========================================================================

    // Key: "SCHEMA.TABLE" or "TABLE" -> TableCatalogEntry
    private final Map<String, TableCatalogEntry> tableIndex = new ConcurrentHashMap<>();
    // Key: "SCHEMA.TABLE" -> Full TableDocModel
    private final Map<String, TableDocModel> tableDocModelIndex = new ConcurrentHashMap<>();
    // Key: "SCHEMA.CONSTRAINT_NAME" -> {schema, tableName, columnName}
    private final Map<String, String[]> constraintIndex = new ConcurrentHashMap<>();
    // Key: "SCHEMA.TABLE" -> List of Outgoing Relationships (this table is child/source)
    private final Map<String, List<TableRelationship>> outgoingRelationships = new ConcurrentHashMap<>();
    // Key: "SCHEMA.TABLE" -> List of Incoming Relationships (this table is parent/target)
    private final Map<String, List<TableRelationship>> incomingRelationships = new ConcurrentHashMap<>();
    // Key: "ROUTINE_NAME" -> List of SQL Lines or SQL Text
    private final Map<String, String> routineSqlMap = new ConcurrentHashMap<>();
    // Key: "SCHEMA.OBJECT_NAME" -> DbObject (Canonical Objects Cache)
    private final Map<String, DbObject> canonicalObjectsMap = new ConcurrentHashMap<>();
    // Key: "OBJECT_NAME" -> DbObject (Unqualified Objects Cache for Single/Dominant Schemas)
    private final Map<String, DbObject> unqualifiedObjectsMap = new ConcurrentHashMap<>();
    // Set of all canonical object names (e.g. "ARS.T_DAILY_SALES", "STAGE.STG_RETAILER")
    private final Set<String> allCanonicalNames = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private volatile boolean initialized = false;

    // =========================================================================
    // SINGLETON ACCESS
    // =========================================================================

    public static DbObjectSearcher getInstance() {
        DbObjectSearcher result = INSTANCE;
        return result;
    }

    public DbObjectSearcher() {
        // Default constructor
    }

    // =========================================================================
    // INITIALIZATION & CATALOG LOADING
    // =========================================================================

    /**
     * Initializes the DbObjectSearcher from the default file system locations or fallbacks.
     */
    public synchronized void initializeDefault() {
        if (initialized) return;

        File dbCacheFile = new File(DEFAULT_DBCACHE_PATH);
        if (!dbCacheFile.exists()) {
            dbCacheFile = new File("DbCache.json");
        }

        File catalogFile = new File(DEFAULT_CATALOG_PATH);
        if (!catalogFile.exists()) {
            catalogFile = new File("DbCache_Ai_Researched_Catalog.json");
        }

        if (dbCacheFile.exists()) {
            loadFromDbCache(dbCacheFile);
        }

        if (catalogFile.exists()) {
            loadFromResearchedCatalog(catalogFile);
        }

        initialized = true;
    }

    /**
     * Loads physical tables and constraints from a DbCache.json file.
     */
    public synchronized void loadFromDbCache(File dbCacheFile) {
        if (dbCacheFile == null || !dbCacheFile.exists()) {
            System.err.println("ACTION REQUIRED: DbCache file does not exist at: " + dbCacheFile);
            return;
        }

        try {
            JsonNode root = MAPPER.readTree(dbCacheFile);
            loadFromDbCacheJson(root);
        } catch (IOException e) {
            System.err.println("ACTION REQUIRED: Failed to parse DbCache JSON: " + e.getMessage());
        }
    }

    /**
     * Ingests a pre-parsed JsonNode representing DbCache.
     */
    public synchronized void loadFromDbCacheJson(JsonNode root) {
        if (root == null) return;

        JsonNode schemasNode = root.path("Schemas");
        if (!schemasNode.isObject()) {
            schemasNode = root;
        }

        Iterator<Map.Entry<String, JsonNode>> schemaFields = schemasNode.fields();
        while (schemaFields.hasNext()) {
            Map.Entry<String, JsonNode> sEntry = schemaFields.next();
            String schemaName = sEntry.getKey().toUpperCase();
            JsonNode sVal = sEntry.getValue();

            // 1. Ingest Tables
            JsonNode tablesNode = sVal.path("Tables");
            if (tablesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> tFields = tablesNode.fields();
                while (tFields.hasNext()) {
                    Map.Entry<String, JsonNode> tEntry = tFields.next();
                    String tName = tEntry.getKey().toUpperCase();
                    JsonNode tVal = tEntry.getValue();
                    ingestTableJson(schemaName, tName, tVal);
                }
            } else if (tablesNode.isArray()) {
                for (JsonNode tVal : tablesNode) {
                    String tName = tVal.path("TableName").asText(tVal.path("Name").asText("")).toUpperCase();
                    if (!tName.isEmpty()) {
                        ingestTableJson(schemaName, tName, tVal);
                    }
                }
            }

            // 2. Ingest Routines SQL Source Code (if present in DbCache)
            JsonNode procsNode = sVal.path("Procedures");
            ingestRoutinesSqlNode(schemaName, procsNode);

            JsonNode packagesNode = sVal.path("Packages");
            ingestPackagesSqlNode(schemaName, packagesNode);
        }

        // 3. Mine Physical Constraints across all schemas now that all tables are indexed
        minePhysicalConstraintsFromDbCache(schemasNode);

        // 4. Mine SQL Procedure Joins across all captured routine SQL
        mineSqlProcedureJoinsFromSqlMap();
    }

    /**
     * Loads routines and deep SQL collections from DbCache_Ai_Researched_Catalog.json.
     */
    public synchronized void loadFromResearchedCatalog(File catalogFile) {
        if (catalogFile == null || !catalogFile.exists()) return;

        try {
            JsonNode root = MAPPER.readTree(catalogFile);
            loadFromResearchedCatalogJson(root);
        } catch (IOException e) {
            System.err.println("ACTION REQUIRED: Failed to parse Researched Catalog JSON: " + e.getMessage());
        }
    }

    /**
     * Ingests researched catalog JSON to extract procedure source code for AST SQL join mining.
     */
    public synchronized void loadFromResearchedCatalogJson(JsonNode root) {
        if (root == null) return;

        JsonNode schemasNode = root.path("Schemas");
        if (!schemasNode.isObject()) {
            schemasNode = root;
        }

        Iterator<Map.Entry<String, JsonNode>> sFields = schemasNode.fields();
        while (sFields.hasNext()) {
            Map.Entry<String, JsonNode> sEntry = sFields.next();
            String schemaName = sEntry.getKey().toUpperCase();
            JsonNode sVal = sEntry.getValue();

            ingestRoutinesSqlNode(schemaName, sVal.path("Procedures"));
            ingestRoutinesSqlNode(schemaName, sVal.path("Functions"));
            ingestPackagesSqlNode(schemaName, sVal.path("Packages"));
        }

        // Mine joins from the newly populated routines
        mineSqlProcedureJoinsFromSqlMap();
    }

    // =========================================================================
    // INGESTION INTERNALS
    // =========================================================================

    private void ingestTableJson(String schema, String tableName, JsonNode tVal) {
        TableCatalogEntry entry = new TableCatalogEntry();
        entry.schema = schema.toUpperCase();
        entry.tableName = tableName.toUpperCase();
        entry.tableType = tVal.path("TableType").asText(tVal.path("Type").asText("RELATIONAL TABLE"));
        if (tVal.hasNonNull("NumRows")) entry.numRows = tVal.path("NumRows").asLong();

        // Columns
        JsonNode cols = tVal.path("Columns");
        if (!cols.isArray()) cols = tVal.path("columns");
        if (cols.isArray()) {
            for (JsonNode c : cols) {
                String cName = c.path("ColumnName").asText(c.path("Name").asText("")).toUpperCase();
                String dt = c.path("DataType").asText(c.path("Type").asText("VARCHAR2"));
                if (!cName.isEmpty()) {
                    entry.columns.put(cName, dt);
                }
            }
        }

        // Primary Keys
        JsonNode pks = tVal.path("PrimaryKey");
        if (pks.isArray()) {
            for (JsonNode pk : pks) entry.primaryKeys.add(pk.asText("").toUpperCase());
        } else if (pks.isTextual() && !pks.asText("").isEmpty()) {
            entry.primaryKeys.add(pks.asText("").toUpperCase());
        }

        // Build TableDocModel representation directly
        TableDocModel docModel = TableDocModel.fromJson(schema, tVal);
        if (docModel != null) {
            entry.tableDocModel = docModel;
            tableDocModelIndex.put(schema.toUpperCase() + "." + tableName.toUpperCase(), docModel);
            tableDocModelIndex.putIfAbsent(tableName.toUpperCase(), docModel);
        }

        tableIndex.put(schema.toUpperCase() + "." + tableName.toUpperCase(), entry);
        tableIndex.putIfAbsent(tableName.toUpperCase(), entry);

        DbObject tableObj = new DbObject(
                schema,
                tableName,
                entry.tableType != null ? entry.tableType : "TABLE",
                "DEVOLTP",
                new ArrayList<>(entry.columns.keySet()),
                entry.primaryKeys,
                tVal.path("Comments").asText(tVal.path("Description").asText("")),
                tVal
        );
        registerObject(tableObj);

        // Index Primary Key / Unique constraints for reverse lookup
        JsonNode consArr = tVal.path("Constraints");
        if (consArr.isArray()) {
            for (JsonNode cNode : consArr) {
                String cName = cNode.path("ConstraintName").asText("").toUpperCase();
                String cType = cNode.path("ConstraintType").asText("").toUpperCase();
                String col = cNode.path("ColumnName").asText("").toUpperCase();
                if ((cType.equals("P") || cType.equals("U")) && !cName.isEmpty()) {
                    // Index: SCHEMA.CONSTRAINT_NAME -> [SCHEMA, TABLE, COLUMN]
                    constraintIndex.put(schema.toUpperCase() + "." + cName, new String[]{schema.toUpperCase(), tableName.toUpperCase(), col});
                }
            }
        }
    }

    private void ingestRoutinesSqlNode(String schema, JsonNode procsNode) {
        if (procsNode == null) return;

        if (procsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = procsNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                extractSqlFromRoutineNode(schema, e.getKey(), e.getValue());
            }
        } else if (procsNode.isArray()) {
            for (JsonNode item : procsNode) {
                String rName = item.path("RoutineName").asText(item.path("Name").asText(""));
                if (!rName.isEmpty()) {
                    extractSqlFromRoutineNode(schema, rName, item);
                }
            }
        }
    }

    private void ingestPackagesSqlNode(String schema, JsonNode packagesNode) {
        if (packagesNode == null) return;

        if (packagesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = packagesNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String pkgName = e.getKey();
                JsonNode pkgVal = e.getValue();
                extractSqlFromPackageNode(schema, pkgName, pkgVal);
            }
        } else if (packagesNode.isArray()) {
            for (JsonNode item : packagesNode) {
                String pkgName = item.path("PackageName").asText(item.path("Name").asText(""));
                if (!pkgName.isEmpty()) {
                    extractSqlFromPackageNode(schema, pkgName, item);
                }
            }
        }
    }

    private void extractSqlFromRoutineNode(String schema, String routineName, JsonNode rNode) {
        String fullName = schema.toUpperCase() + "." + routineName.toUpperCase();
        StringBuilder sqlBuilder = new StringBuilder();

        if (rNode.hasNonNull("SqlText")) {
            sqlBuilder.append(rNode.path("SqlText").asText()).append("\n");
        }
        if (rNode.hasNonNull("SourceCode")) {
            sqlBuilder.append(rNode.path("SourceCode").asText()).append("\n");
        }
        if (rNode.hasNonNull("AllSource")) {
            JsonNode srcNode = rNode.path("AllSource");
            if (srcNode.isArray()) {
                for (JsonNode line : srcNode) sqlBuilder.append(line.asText()).append("\n");
            } else if (srcNode.isTextual()) {
                sqlBuilder.append(srcNode.asText()).append("\n");
            }
        }

        String sql = sqlBuilder.toString().trim();
        if (!sql.isEmpty()) {
            routineSqlMap.put(fullName, sql);
            DbObject routineObj = new DbObject(
                    schema.toUpperCase(),
                    routineName.toUpperCase(),
                    "PROCEDURE",
                    "DEVOLTP",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    rNode.path("Description").asText(""),
                    rNode
            );
            registerObject(routineObj);
        }
    }

    private void extractSqlFromPackageNode(String schema, String pkgName, JsonNode pkgNode) {
        String baseName = schema.toUpperCase() + "." + pkgName.toUpperCase();

        if (pkgNode.hasNonNull("BodySource") || pkgNode.hasNonNull("SourceCode")) {
            String bSrc = pkgNode.path("BodySource").asText(pkgNode.path("SourceCode").asText(""));
            if (!bSrc.trim().isEmpty()) {
                routineSqlMap.put(baseName + "$BODY", bSrc);
            }
        }

        JsonNode subRoutines = pkgNode.path("SubRoutines");
        if (!subRoutines.isArray()) subRoutines = pkgNode.path("Routines");
        if (subRoutines.isArray()) {
            for (JsonNode sub : subRoutines) {
                String subName = sub.path("RoutineName").asText(sub.path("Name").asText(""));
                if (!subName.isEmpty()) {
                    extractSqlFromRoutineNode(schema, pkgName + "." + subName, sub);
                }
            }
        }
    }

    // =========================================================================
    // ENGINE 1: DEFINITIVE PHYSICAL CONSTRAINTS (ALL_CONSTRAINTS)
    // =========================================================================

    private void minePhysicalConstraintsFromDbCache(JsonNode schemasNode) {
        if (schemasNode == null) return;

        Iterator<Map.Entry<String, JsonNode>> schemaFields = schemasNode.fields();
        while (schemaFields.hasNext()) {
            Map.Entry<String, JsonNode> sEntry = schemaFields.next();
            String sourceSchema = sEntry.getKey().toUpperCase();
            JsonNode tablesNode = sEntry.getValue().path("Tables");

            if (tablesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> tFields = tablesNode.fields();
                while (tFields.hasNext()) {
                    Map.Entry<String, JsonNode> tEntry = tFields.next();
                    mineTableConstraints(sourceSchema, tEntry.getKey().toUpperCase(), tEntry.getValue());
                }
            } else if (tablesNode.isArray()) {
                for (JsonNode tVal : tablesNode) {
                    String tName = tVal.path("TableName").asText(tVal.path("Name").asText("")).toUpperCase();
                    if (!tName.isEmpty()) {
                        mineTableConstraints(sourceSchema, tName, tVal);
                    }
                }
            }
        }
    }

    private void mineTableConstraints(String sourceSchema, String sourceTable, JsonNode tVal) {
        JsonNode consArr = tVal.path("Constraints");
        if (!consArr.isArray()) return;

        for (JsonNode cNode : consArr) {
            String cType = cNode.path("ConstraintType").asText("").toUpperCase();
            if (!cType.equals("R")) continue; // Foreign Key

            String constraintName = cNode.path("ConstraintName").asText("").toUpperCase();
            String sourceCol = cNode.path("ColumnName").asText("").toUpperCase();
            String rOwner = cNode.path("ROwner").asText("").toUpperCase();
            String rConstraint = cNode.path("RConstraintName").asText("").toUpperCase();

            if (rOwner.isEmpty()) rOwner = sourceSchema;

            // Resolve target table via constraintIndex
            String targetKey = rOwner + "." + rConstraint;
            String[] targetInfo = constraintIndex.get(targetKey);

            String targetTable = null;
            String targetCol = null;

            if (targetInfo != null) {
                targetTable = targetInfo[1];
                targetCol = targetInfo[2];
            } else {
                // Fallback: Infer parent table name by stripping PK_ / UK_ prefix
                targetTable = inferTargetTableFromConstraintName(rConstraint);
                targetCol = sourceCol;
            }

            if (targetTable != null && !targetTable.isEmpty()) {
                TableRelationship rel = new TableRelationship(
                        sourceSchema,
                        sourceTable,
                        sourceCol,
                        rOwner,
                        targetTable,
                        targetCol != null && !targetCol.isEmpty() ? targetCol : sourceCol,
                        RelationshipType.PHYSICAL_FOREIGN_KEY,
                        ConfidenceLevel.CONFIRMED_ENFORCED,
                        constraintName,
                        "FOREIGN KEY",
                        sourceTable + "." + sourceCol + " = " + targetTable + "." + (targetCol != null ? targetCol : sourceCol),
                        1,
                        Collections.singletonList("Oracle Data Dictionary [ALL_CONSTRAINTS: " + constraintName + "]"),
                        true,
                        "Definitive database foreign key constraint: " + constraintName + " referencing " + rOwner + "." + rConstraint
                );

                recordRelationship(rel);
            }
        }
    }

    private String inferTargetTableFromConstraintName(String constraintName) {
        if (constraintName == null || constraintName.isEmpty()) {
            String result = null;
            return result;
        }
        String upper = constraintName.toUpperCase();
        if (upper.startsWith("PK_")) {
            String result = upper.substring(3);
            return result;
        }
        if (upper.startsWith("UK_") || upper.startsWith("AK_")) {
            String result = upper.substring(3);
            return result;
        }
        String result = upper;
        return result;
    }

    // =========================================================================
    // ENGINE 2: STATIC AST / SQL PROCEDURE JOIN LINEAGE MINING
    // =========================================================================

    /**
     * Mines table joins across all collected routine SQL texts.
     * Extracts ANSI JOINs and WHERE-clause equality joins.
     */
    private void mineSqlProcedureJoinsFromSqlMap() {
        if (routineSqlMap.isEmpty()) return;

        // Map: "sourceSchema.sourceTable:targetSchema.targetTable:colA:colB" -> JoinAccumulator
        Map<String, JoinAccumulator> accumMap = new HashMap<>();

        for (Map.Entry<String, String> entry : routineSqlMap.entrySet()) {
            String routineName = entry.getKey();
            String sql = entry.getValue();

            mineJoinsFromSqlText(routineName, sql, accumMap);
        }

        // Convert accumulators to TableRelationship objects
        for (JoinAccumulator acc : accumMap.values()) {
            ConfidenceLevel conf = acc.routines.size() >= 3
                    ? ConfidenceLevel.HIGH_SQL_LINEAGE
                    : ConfidenceLevel.MODERATE_SQL_LINEAGE;

            TableRelationship rel = new TableRelationship(
                    acc.sourceSchema,
                    acc.sourceTable,
                    acc.sourceCol,
                    acc.targetSchema,
                    acc.targetTable,
                    acc.targetCol,
                    RelationshipType.INFERRED_PROCEDURE_JOIN,
                    conf,
                    null,
                    acc.joinType,
                    acc.joinCondition,
                    acc.routines.size(),
                    new ArrayList<>(acc.routines),
                    false,
                    "Inferred from static SQL JOIN analysis in " + acc.routines.size() + " routines: " + String.join(", ", acc.routines)
            );

            recordRelationship(rel);
        }
    }

    /**
     * Parses an individual SQL script for ANSI and WHERE joins.
     */
    private void mineJoinsFromSqlText(String routineName, String sql, Map<String, JoinAccumulator> accumMap) {
        if (sql == null || sql.trim().isEmpty()) return;

        String upperSql = sql.toUpperCase();

        // 1. ANSI Join Pattern:
        // JOIN <tableName> [alias] ON <condition>
        Pattern ansiJoinPattern = Pattern.compile(
                "(?:(LEFT|RIGHT|FULL|INNER|CROSS)\\s+)?JOIN\\s+([A-Za-z0-9_$.]+)(?:\\s+(?:AS\\s+)?([A-Za-z0-9_]+))?\\s+ON\\s+([^;\n\r]+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher ansiMatcher = ansiJoinPattern.matcher(sql);
        while (ansiMatcher.find()) {
            String joinType = ansiMatcher.group(1) != null ? ansiMatcher.group(1).trim().toUpperCase() + " JOIN" : "INNER JOIN";
            String joinedTableRaw = ansiMatcher.group(2).trim();
            String joinedAlias = ansiMatcher.group(3) != null ? ansiMatcher.group(3).trim() : null;
            String onCondition = ansiMatcher.group(4).trim();

            processJoinCondition(routineName, joinType, joinedTableRaw, joinedAlias, onCondition, upperSql, accumMap);
        }

        // 2. WHERE Clause Equality Join Pattern:
        // a.COL_ID = b.COL_ID
        Pattern whereJoinPattern = Pattern.compile(
                "([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\s*=\\s*([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)",
                Pattern.CASE_INSENSITIVE
        );

        int whereIdx = upperSql.indexOf("WHERE ");
        if (whereIdx != -1) {
            String whereClause = sql.substring(whereIdx + 6);
            int endIdx = whereClause.indexOf(';');
            if (endIdx != -1) whereClause = whereClause.substring(0, endIdx);

            Matcher whereMatcher = whereJoinPattern.matcher(whereClause);
            while (whereMatcher.find()) {
                String aliasA = whereMatcher.group(1).trim().toUpperCase();
                String colA = whereMatcher.group(2).trim().toUpperCase();
                String aliasB = whereMatcher.group(3).trim().toUpperCase();
                String colB = whereMatcher.group(4).trim().toUpperCase();

                if (!aliasA.equals(aliasB) && isForeignKeyCandidateColumn(colA)) {
                    resolveAndAccumulateJoin(routineName, "WHERE EQUALITY", aliasA, colA, aliasB, colB, aliasA + "." + colA + " = " + aliasB + "." + colB, upperSql, accumMap);
                }
            }
        }
    }

    private void processJoinCondition(
            String routineName,
            String joinType,
            String joinedTableRaw,
            String joinedAlias,
            String onCondition,
            String fullSql,
            Map<String, JoinAccumulator> accumMap) {

        // Match on condition: alias1.COL = alias2.COL
        Pattern condPattern = Pattern.compile(
                "([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\s*=\\s*([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher m = condPattern.matcher(onCondition);
        while (m.find()) {
            String leftAlias = m.group(1).trim().toUpperCase();
            String leftCol = m.group(2).trim().toUpperCase();
            String rightAlias = m.group(3).trim().toUpperCase();
            String rightCol = m.group(4).trim().toUpperCase();

            resolveAndAccumulateJoin(routineName, joinType, leftAlias, leftCol, rightAlias, rightCol, m.group(0), fullSql, accumMap);
        }
    }

    private void resolveAndAccumulateJoin(
            String routineName,
            String joinType,
            String aliasA,
            String colA,
            String aliasB,
            String colB,
            String conditionText,
            String fullSql,
            Map<String, JoinAccumulator> accumMap) {

        // Resolve aliases to actual tables
        String tableA = resolveTableFromAlias(aliasA, fullSql);
        String tableB = resolveTableFromAlias(aliasB, fullSql);

        if (tableA == null || tableB == null || tableA.equalsIgnoreCase(tableB)) return;

        String schemaA = extractSchemaFromQualifiedTable(tableA, getRoutineSchema(routineName));
        String simpleTableA = extractSimpleTable(tableA);

        String schemaB = extractSchemaFromQualifiedTable(tableB, getRoutineSchema(routineName));
        String simpleTableB = extractSimpleTable(tableB);

        // Determine which is Master/Parent and which is Child
        // Heuristic: table matching colA's prefix or known master is parent
        boolean bIsMaster = isLikelyMasterTable(simpleTableB, simpleTableA, colA);

        String srcSchema = bIsMaster ? schemaA : schemaB;
        String srcTable = bIsMaster ? simpleTableA : simpleTableB;
        String srcCol = bIsMaster ? colA : colB;

        String tgtSchema = bIsMaster ? schemaB : schemaA;
        String tgtTable = bIsMaster ? simpleTableB : simpleTableA;
        String tgtCol = bIsMaster ? colB : colA;

        String key = srcSchema + "." + srcTable + ":" + tgtSchema + "." + tgtTable + ":" + srcCol + ":" + tgtCol;

        accumMap.computeIfAbsent(key, k -> new JoinAccumulator(srcSchema, srcTable, srcCol, tgtSchema, tgtTable, tgtCol, joinType, conditionText))
                .addRoutine(routineName);
    }

    private String resolveTableFromAlias(String alias, String fullSql) {
        if (alias == null || alias.isEmpty()) {
            String result = null;
            return result;
        }

        // Pattern 1: <TABLE_NAME> <ALIAS>
        Pattern p1 = Pattern.compile(
                "\\b([A-Za-z0-9_$.]+)\\s+(?:AS\\s+)?" + Pattern.quote(alias) + "\\b",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m1 = p1.matcher(fullSql);
        if (m1.find()) {
            String cand = m1.group(1).trim().toUpperCase();
            if (!cand.equalsIgnoreCase("SET") && !cand.equalsIgnoreCase("WHERE") && !cand.equalsIgnoreCase("FROM") && !cand.equalsIgnoreCase("JOIN")) {
                String result = cand;
                return result;
            }
        }

        // If alias is itself a known table in our catalog
        if (tableIndex.containsKey(alias)) {
            String result = alias;
            return result;
        }

        String result = null;
        return result;
    }

    private boolean isLikelyMasterTable(String tableB, String tableA, String col) {
        String upperB = tableB.toUpperCase();
        String upperA = tableA.toUpperCase();

        if (upperB.endsWith("_MASTER") || upperB.endsWith("_MST") || upperB.endsWith("_REF") || upperB.endsWith("_TYPE") || upperB.endsWith("_CODE")) {
            boolean result = true;
            return result;
        }
        if (upperA.endsWith("_STG") || upperA.endsWith("_STAGE") || upperA.endsWith("_DTL") || upperA.endsWith("_HIST") || upperA.endsWith("_TXN") || upperA.endsWith("_TRANSACTION")) {
            boolean result = true;
            return result;
        }
        if (col.startsWith(upperB) || upperB.contains(col.replaceAll("_(ID|NBR|NO|CD|CODE)$", ""))) {
            boolean result = true;
            return result;
        }
        boolean result = false;
        return result;
    }

    private boolean isForeignKeyCandidateColumn(String col) {
        if (col == null || col.isEmpty()) return false;
        String upper = col.toUpperCase();
        boolean result = upper.endsWith("_ID") || upper.endsWith("_NBR") || upper.endsWith("_NO") ||
                upper.endsWith("_CD") || upper.endsWith("_CODE") ||
                upper.equals("RETAILER_ID") || upper.equals("GAME_ID") || upper.equals("INVOICE_NBR");
        return result;
    }

    private String getRoutineSchema(String routineName) {
        if (routineName != null && routineName.contains(".")) {
            int dotIdx = routineName.indexOf('.');
            String result = routineName.substring(0, dotIdx).toUpperCase();
            return result;
        }
        String result = "ARS";
        return result;
    }

    private String extractSchemaFromQualifiedTable(String tableRef, String defaultSchema) {
        if (tableRef == null) {
            String result = defaultSchema;
            return result;
        }
        if (tableRef.contains(".")) {
            int dotIdx = tableRef.indexOf('.');
            String result = tableRef.substring(0, dotIdx).toUpperCase();
            return result;
        }
        String result = defaultSchema != null ? defaultSchema.toUpperCase() : "ARS";
        return result;
    }

    private String extractSimpleTable(String tableRef) {
        if (tableRef == null) {
            String result = "";
            return result;
        }
        if (tableRef.contains(".")) {
            int dotIdx = tableRef.lastIndexOf('.');
            String result = tableRef.substring(dotIdx + 1).toUpperCase();
            return result;
        }
        String result = tableRef.toUpperCase();
        return result;
    }

    private static class JoinAccumulator {
        final String sourceSchema;
        final String sourceTable;
        final String sourceCol;
        final String targetSchema;
        final String targetTable;
        final String targetCol;
        final String joinType;
        final String joinCondition;
        final Set<String> routines = new TreeSet<>();

        JoinAccumulator(String sourceSchema, String sourceTable, String sourceCol, String targetSchema, String targetTable, String targetCol, String joinType, String joinCondition) {
            this.sourceSchema = sourceSchema;
            this.sourceTable = sourceTable;
            this.sourceCol = sourceCol;
            this.targetSchema = targetSchema;
            this.targetTable = targetTable;
            this.targetCol = targetCol;
            this.joinType = joinType;
            this.joinCondition = joinCondition;
        }

        void addRoutine(String routineName) {
            routines.add(routineName);
        }
    }

    // =========================================================================
    // RELATIONSHIP RECORDING & DEDUPLICATION
    // =========================================================================

    private void recordRelationship(TableRelationship rel) {
        if (rel == null) return;

        String sourceKey = rel.getSourceQualifiedName();
        String targetKey = rel.getTargetQualifiedName();

        // 1. Record Outgoing (Source -> Target)
        List<TableRelationship> outList = outgoingRelationships.computeIfAbsent(sourceKey, k -> new ArrayList<>());
        synchronized (outList) {
            mergeOrAddRelationship(outList, rel);
        }

        // 2. Record Incoming (Target <- Source)
        List<TableRelationship> inList = incomingRelationships.computeIfAbsent(targetKey, k -> new ArrayList<>());
        synchronized (inList) {
            mergeOrAddRelationship(inList, rel);
        }
    }

    private void mergeOrAddRelationship(List<TableRelationship> list, TableRelationship newRel) {
        for (int i = 0; i < list.size(); i++) {
            TableRelationship existing = list.get(i);
            if (existing.equals(newRel)) {
                // If existing is enforced and new is not, keep enforced
                if (existing.isEnforced() && !newRel.isEnforced()) {
                    return;
                }
                // If new is enforced and existing is inferred, replace with enforced!
                if (newRel.isEnforced() && !existing.isEnforced()) {
                    list.set(i, newRel);
                    return;
                }
                // Both inferred: merge occurrences and referencing routines
                Set<String> mergedRoutines = new TreeSet<>(existing.getReferencingRoutines());
                mergedRoutines.addAll(newRel.getReferencingRoutines());

                TableRelationship merged = new TableRelationship(
                        existing.getSourceSchema(),
                        existing.getSourceTable(),
                        existing.getSourceColumn(),
                        existing.getTargetSchema(),
                        existing.getTargetTable(),
                        existing.getTargetColumn(),
                        existing.getRelationshipType(),
                        existing.getConfidenceLevel(),
                        existing.getConstraintName(),
                        existing.getJoinType(),
                        existing.getJoinConditionSql(),
                        Math.max(existing.getOccurrences(), newRel.getOccurrences()) + 1,
                        new ArrayList<>(mergedRoutines),
                        existing.isEnforced(),
                        existing.getProvenance()
                );
                list.set(i, merged);
                return;
            }
        }
        list.add(newRel);
        Collections.sort(list);
    }

    // =========================================================================
    // PUBLIC SEARCH & DISCOVERY APIS
    // =========================================================================

    /**
     * Registers a table directly from an active TableDocModel.
     */
    public void registerTable(TableDocModel model) {
        if (model == null || model.tableName == null) return;
        String s = model.schema != null ? model.schema.toUpperCase() : "SCHEMA";
        String t = model.tableName.toUpperCase();

        tableDocModelIndex.put(s + "." + t, model);
        tableDocModelIndex.putIfAbsent(t, model);

        TableCatalogEntry entry = new TableCatalogEntry();
        entry.schema = s;
        entry.tableName = t;
        entry.tableType = model.tableType;
        entry.numRows = model.numRows;
        entry.primaryKeys = model.primaryKeys != null ? new ArrayList<>(model.primaryKeys) : new ArrayList<>();
        entry.tableDocModel = model;

        if (model.columns != null) {
            for (TableColumnDoc c : model.columns) {
                entry.columns.put(c.columnName.toUpperCase(), c.dataType);
            }
        }

        tableIndex.put(s + "." + t, entry);
        tableIndex.putIfAbsent(t, entry);

        List<String> colsList = new ArrayList<>();
        if (model.columns != null) {
            for (TableColumnDoc c : model.columns) {
                colsList.add(c.columnName);
            }
        }
        DbObject regObj = new DbObject(
                s,
                t,
                model.tableType != null ? model.tableType : "TABLE",
                model.databaseName != null ? model.databaseName : "DEVOLTP",
                colsList,
                model.primaryKeys,
                model.description,
                null
        );
        registerObject(regObj);
    }

    /**
     * Finds a table model across ANY schema, with cross-schema fallback.
     */
    public TableDocModel findTable(String schema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            TableDocModel result = null;
            return result;
        }

        String tUpper = tableName.toUpperCase();
        if (tUpper.contains(".")) {
            TableDocModel result = tableDocModelIndex.get(tUpper);
            return result;
        }

        if (schema != null && !schema.isEmpty()) {
            String key = schema.toUpperCase() + "." + tUpper;
            if (tableDocModelIndex.containsKey(key)) {
                TableDocModel result = tableDocModelIndex.get(key);
                return result;
            }
        }

        // Global search by simple name across all schemas
        if (tableDocModelIndex.containsKey(tUpper)) {
            TableDocModel result = tableDocModelIndex.get(tUpper);
            return result;
        }

        TableDocModel result = null;
        return result;
    }

    /**
     * Finds all relationships for a given table (both outgoing child relationships and incoming parent relationships).
     */
    public List<TableRelationship> findRelationshipsForTable(String schema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            List<TableRelationship> result = Collections.emptyList();
            return result;
        }

        String key = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() + "." + tableName.toUpperCase() : tableName.toUpperCase();
        List<TableRelationship> out = outgoingRelationships.getOrDefault(key, Collections.emptyList());
        List<TableRelationship> in = incomingRelationships.getOrDefault(key, Collections.emptyList());

        List<TableRelationship> combined = new ArrayList<>();
        combined.addAll(out);
        combined.addAll(in);
        Collections.sort(combined);

        List<TableRelationship> result = Collections.unmodifiableList(combined);
        return result;
    }

    /**
     * Returns the best parent/lookup table relationship for this table.
     * Prioritizes:
     * 1. Definitive physical foreign keys (ALL_CONSTRAINTS)
     * 2. High-frequency SQL procedure joins
     * 3. Convention-based inferences
     */
    public TableRelationship findBestParentRelationship(String schema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            TableRelationship result = null;
            return result;
        }

        String key = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() + "." + tableName.toUpperCase() : tableName.toUpperCase();
        List<TableRelationship> out = outgoingRelationships.get(key);

        if (out != null && !out.isEmpty()) {
            // Priority 1: Physical enforced FK
            for (TableRelationship rel : out) {
                if (rel.isEnforced()) {
                    TableRelationship result = rel;
                    return result;
                }
            }
            // Priority 2: Highest confidence SQL join
            TableRelationship result = out.get(0);
            return result;
        }

        // Fallback: Convention inference
        TableRelationship convention = inferConventionParent(schema, tableName);
        TableRelationship result = convention;
        return result;
    }

    /**
     * Returns all child / dependent tables that reference this table as a parent.
     */
    public List<TableRelationship> findChildRelationships(String schema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            List<TableRelationship> result = Collections.emptyList();
            return result;
        }

        String key = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() + "." + tableName.toUpperCase() : tableName.toUpperCase();
        List<TableRelationship> in = incomingRelationships.getOrDefault(key, Collections.emptyList());

        List<TableRelationship> result = Collections.unmodifiableList(new ArrayList<>(in));
        return result;
    }

    /**
     * Finds the direct relationship between two specific tables (in either direction).
     * Returns the highest-priority relationship (enforced physical FK first, then SQL join, then inferred).
     */
    public TableRelationship findRelationshipBetween(String schema1, String table1, String schema2, String table2) {
        if (table1 == null || table1.isEmpty() || table2 == null || table2.isEmpty()) {
            TableRelationship result = null;
            return result;
        }

        String key1 = (schema1 != null && !schema1.isEmpty()) ? schema1.toUpperCase() + "." + table1.toUpperCase() : table1.toUpperCase();
        List<TableRelationship> out1 = outgoingRelationships.get(key1);
        if (out1 != null) {
            for (TableRelationship r : out1) {
                if (r.getTargetTable().equalsIgnoreCase(table2)) {
                    if (schema2 == null || schema2.isEmpty() || r.getTargetSchema().equalsIgnoreCase(schema2)) {
                        TableRelationship result = r;
                        return result;
                    }
                }
            }
        }

        List<TableRelationship> in1 = incomingRelationships.get(key1);
        if (in1 != null) {
            for (TableRelationship r : in1) {
                if (r.getSourceTable().equalsIgnoreCase(table2)) {
                    if (schema2 == null || schema2.isEmpty() || r.getSourceSchema().equalsIgnoreCase(schema2)) {
                        TableRelationship result = r;
                        return result;
                    }
                }
            }
        }

        TableRelationship result = null;
        return result;
    }

    /**
     * Fallback convention parent inference when no physical FK or SQL join exists.
     */
    private TableRelationship inferConventionParent(String schema, String tableName) {
        TableCatalogEntry entry = tableIndex.get((schema != null ? schema.toUpperCase() + "." : "") + tableName.toUpperCase());
        if (entry == null) entry = tableIndex.get(tableName.toUpperCase());

        if (entry != null && entry.columns != null) {
            for (String colName : entry.columns.keySet()) {
                if (entry.primaryKeys.contains(colName)) continue;

                if (colName.equals("RETAILER_ID") || colName.endsWith("_RETAILER_ID")) {
                    TableRelationship result = new TableRelationship(
                            entry.schema, entry.tableName, colName,
                            "ARS", "RETAILER_MASTER", "RETAILER_ID",
                            RelationshipType.INFERRED_NAMING_CONVENTION,
                            ConfidenceLevel.INFERRED_CONVENTION,
                            null, "CONVENTION JOIN",
                            entry.tableName + "." + colName + " = ARS.RETAILER_MASTER.RETAILER_ID",
                            0, Collections.emptyList(), false,
                            "Convention inference: Retailer domain primary entity"
                    );
                    return result;
                }

                if (colName.equals("GAME_ID") || colName.equals("GAME_NBR")) {
                    TableRelationship result = new TableRelationship(
                            entry.schema, entry.tableName, colName,
                            "GAME", "GAME_MASTER", colName,
                            RelationshipType.INFERRED_NAMING_CONVENTION,
                            ConfidenceLevel.INFERRED_CONVENTION,
                            null, "CONVENTION JOIN",
                            entry.tableName + "." + colName + " = GAME.GAME_MASTER." + colName,
                            0, Collections.emptyList(), false,
                            "Convention inference: Gaming domain primary entity"
                    );
                    return result;
                }
            }
        }

        TableRelationship result = null;
        return result;
    }

    /**
     * Generates a comprehensive ASCII report of all discovered relationships for an entity.
     */
    public String generateLineageSummaryReport(String schema, String tableName) {
        StringBuilder sb = new StringBuilder();
        String full = (schema != null ? schema.toUpperCase() + "." : "") + tableName.toUpperCase();

        sb.append("========================================================================\n");
        sb.append("FLORIDA LOTTERY DATA LINEAGE & RELATIONSHIP AUDIT: ").append(full).append("\n");
        sb.append("========================================================================\n");

        List<TableRelationship> parents = outgoingRelationships.getOrDefault(full, Collections.emptyList());
        sb.append("PARENT (LOOKUP / MASTER) RELATIONSHIPS [").append(parents.size()).append("]:\n");
        if (parents.isEmpty()) {
            sb.append("   (None recorded)\n");
        } else {
            for (TableRelationship r : parents) {
                sb.append("   • ").append(r.toSummaryString()).append("\n");
                sb.append("     Type: ").append(r.getRelationshipType().getLabel()).append(" (Confidence: ").append((int)(r.getConfidenceLevel().getScore() * 100)).append("%)\n");
                if (!r.getReferencingRoutines().isEmpty()) {
                    sb.append("     Routines (").append(r.getReferencingRoutines().size()).append("): ").append(String.join(", ", r.getReferencingRoutines())).append("\n");
                }
            }
        }

        List<TableRelationship> children = incomingRelationships.getOrDefault(full, Collections.emptyList());
        sb.append("\nCHILD (TRANSACTION / DEPENDENT) RELATIONSHIPS [").append(children.size()).append("]:\n");
        if (children.isEmpty()) {
            sb.append("   (None recorded)\n");
        } else {
            for (TableRelationship r : children) {
                sb.append("   • ").append(r.getSourceQualifiedName()).append(" (").append(r.getSourceColumn()).append(")");
                sb.append(r.isEnforced() ? " ==[FK]==> " : " --[SQL JOIN]--> ");
                sb.append(r.getTargetQualifiedName()).append("\n");
                sb.append("     Type: ").append(r.getRelationshipType().getLabel()).append("\n");
            }
        }

        sb.append("========================================================================\n");
        String result = sb.toString();
        return result;
    }

    /**
     * Dumps all indexed relationships to a structured JSON string.
     */
    public String exportRelationshipsToJson() {
        try {
            ArrayNode arr = MAPPER.createArrayNode();
            Set<TableRelationship> allRels = new HashSet<>();
            for (List<TableRelationship> list : outgoingRelationships.values()) allRels.addAll(list);
            for (List<TableRelationship> list : incomingRelationships.values()) allRels.addAll(list);

            for (TableRelationship r : allRels) {
                ObjectNode obj = MAPPER.createObjectNode();
                obj.put("SourceSchema", r.getSourceSchema());
                obj.put("SourceTable", r.getSourceTable());
                obj.put("SourceColumn", r.getSourceColumn());
                obj.put("TargetSchema", r.getTargetSchema());
                obj.put("TargetTable", r.getTargetTable());
                obj.put("TargetColumn", r.getTargetColumn());
                obj.put("RelationshipType", r.getRelationshipType().name());
                obj.put("Confidence", r.getConfidenceLevel().name());
                obj.put("ConfidenceScore", r.getConfidenceLevel().getScore());
                obj.put("IsEnforced", r.isEnforced());
                obj.put("ConstraintName", r.getConstraintName() != null ? r.getConstraintName() : "");
                obj.put("Occurrences", r.getOccurrences());
                obj.put("IsCrossSchema", r.isCrossSchema());

                ArrayNode routinesArr = obj.putArray("ReferencingRoutines");
                for (String rout : r.getReferencingRoutines()) routinesArr.add(rout);

                arr.add(obj);
            }

            String result = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(arr);
            return result;
        } catch (Exception e) {
            String result = "[]";
            return result;
        }
    }

    // =========================================================================
    // OBJECT CATALOG, CANONICAL RESOLUTION & KSH PARSER INTEGRATION
    // =========================================================================

    private static final Set<String> RESERVED_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // SQL keywords
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "JOIN", "INNER", "LEFT", "RIGHT",
            "FULL", "OUTER", "CROSS", "NATURAL", "ON", "AND", "OR", "NOT", "IN", "IS", "NULL", "TRUE",
            "FALSE", "CASE", "WHEN", "THEN", "ELSE", "END", "AS", "GROUP", "BY", "ORDER", "HAVING",
            "UNION", "ALL", "MINUS", "INTERSECT", "CREATE", "ALTER", "DROP", "TABLE", "VIEW", "INDEX",
            "TRIGGER", "PROCEDURE", "FUNCTION", "PACKAGE", "BODY", "DECLARE", "BEGIN", "EXCEPTION",
            "COMMIT", "ROLLBACK", "SAVEPOINT", "MERGE", "MATCHED", "USING", "INTO", "VALUES", "SET",
            "DISTINCT", "EXISTS", "BETWEEN", "LIKE", "ROWNUM", "ROWID", "DUAL", "SYSDATE", "SYSTIMESTAMP",
            "COUNT", "SUM", "AVG", "MIN", "MAX", "NVL", "DECODE", "SUBSTR", "INSTR", "TRUNC", "ROUND",
            "TO_CHAR", "TO_DATE", "TO_NUMBER", "VARCHAR2", "NUMBER", "DATE", "CHAR", "CLOB", "BLOB",
            // Shell / Scripting keywords
            "ECHO", "CAT", "GREP", "SED", "AWK", "KSH", "BASH", "SH", "CHMOD", "CHOWN", "RM", "MV", "CP",
            "MKDIR", "CD", "LS", "EXPORT", "TYPESET", "PRINT", "READ", "RETURN", "SHIFT", "TRAP", "TEST",
            "WHILE", "DO", "DONE", "FOR", "IF", "THEN", "ELIF", "FI", "CASE", "ESAC", "EXIT", "WAIT",
            "SLEEP", "KILL", "MAIL", "MAILX", "SENDMAIL", "CRON", "AT", "FTP", "SFTP", "SCP", "SSH",
            "SQLPLUS", "SQLLDR", "EXP", "IMP", "EXPDP", "IMPDP", "RMAN", "LOG", "ERR", "TEMP", "TMP"
    )));

    /**
     * Ensures that the searcher has attempted default initialization if not already done.
     */
    public void ensureInitialized() {
        if (!initialized) {
            initializeDefault();
        }
    }

    /**
     * Registers a database object into the canonical catalog and unqualified lookup indices.
     */
    public void registerObject(DbObject obj) {
        if (obj == null) return;
        String qual = obj.getCanonicalName().toUpperCase();
        canonicalObjectsMap.put(qual, obj);
        allCanonicalNames.add(qual);
        unqualifiedObjectsMap.putIfAbsent(obj.getObjectName().toUpperCase(), obj);
    }

    public void registerObject(String schema, String objectName, String objectType) {
        DbObject obj = new DbObject(schema, objectName, objectType);
        registerObject(obj);
    }

    /**
     * Attempts to resolve a raw Korn shell token or SQL identifier into a fully-qualified
     * canonical database object name (e.g., "ARS.T_DAILY_SALES" or "TRANSLATOR.PKG_GAME_FEED").
     * <p>
     * Returns {@code null} if the token represents a Korn shell keyword, bash operator,
     * common SQL reserved word, or unrecognized expression.
     * </p>
     *
     * @param rawName raw token or object name extracted from Korn shell scripts or SQL
     * @return canonical object name in "SCHEMA.OBJECT" format, or {@code null} if not recognized
     */
    public String tryGetCanonicalName(String rawName) {
        String result = tryGetCanonicalName(rawName, null);
        return result;
    }

    /**
     * Attempts to resolve a raw Korn shell token with an optional default schema context.
     *
     * @param rawName raw token or object name
     * @param defaultSchema optional default schema to assume if unqualified (e.g. "ARS", "TRANSLATOR")
     * @return canonical object name in "SCHEMA.OBJECT" format, or {@code null} if not recognized
     */
    public String tryGetCanonicalName(String rawName, String defaultSchema) {
        if (rawName == null) {
            String result = null;
            return result;
        }

        String token = rawName.trim();
        if (token.isEmpty()) {
            String result = null;
            return result;
        }

        // 1. Strip delimiters (quotes, backticks, brackets, trailing commas, semicolons, parentheses)
        token = stripTokenDelimiters(token);
        if (token.isEmpty()) {
            String result = null;
            return result;
        }

        // 2. Reject non-DB tokens (reserved words, shell commands, operators, pure numbers)
        if (isIgnoredReservedToken(token)) {
            String result = null;
            return result;
        }

        // 3. Strip DB link suffix if present (e.g. T_SALES@PROD_LINK -> T_SALES)
        if (token.contains("@")) {
            int atIdx = token.indexOf('@');
            token = token.substring(0, atIdx).trim();
        }

        // 4. Resolve shell variables like $SCHEMA.T_TABLE or ${ARS_OWNER}.T_TABLE or $TABLE_NAME
        token = resolveShellVariables(token);
        if (token.isEmpty() || isIgnoredReservedToken(token)) {
            String result = null;
            return result;
        }

        ensureInitialized();

        String upper = token.toUpperCase();

        // 5. Direct match in canonical objects index
        if (canonicalObjectsMap.containsKey(upper)) {
            DbObject obj = canonicalObjectsMap.get(upper);
            String result = obj.getCanonicalName();
            return result;
        }

        // 6. Dot notation: SCHEMA.OBJECT (or SCHEMA.PACKAGE.ROUTINE)
        if (upper.contains(".")) {
            int dotIdx = upper.indexOf('.');
            String sPart = upper.substring(0, dotIdx).trim();
            String oPart = upper.substring(dotIdx + 1).trim();

            String qual = sPart + "." + oPart;
            if (canonicalObjectsMap.containsKey(qual)) {
                String result = canonicalObjectsMap.get(qual).getCanonicalName();
                return result;
            }
            if (tableIndex.containsKey(qual)) {
                TableCatalogEntry e = tableIndex.get(qual);
                String result = e.schema + "." + e.tableName;
                return result;
            }
            if (tableDocModelIndex.containsKey(qual)) {
                TableDocModel m = tableDocModelIndex.get(qual);
                String result = m.schema + "." + m.tableName;
                return result;
            }

            if (isKnownSchema(sPart) && isValidSqlIdentifier(oPart)) {
                String result = sPart + "." + oPart;
                return result;
            }
        }

        // 7. Unqualified Name: Check unqualifiedObjectsMap (tables, views, procedures)
        if (unqualifiedObjectsMap.containsKey(upper)) {
            DbObject obj = unqualifiedObjectsMap.get(upper);
            String result = obj.getCanonicalName();
            return result;
        }

        if (tableIndex.containsKey(upper)) {
            TableCatalogEntry e = tableIndex.get(upper);
            if (e != null && e.schema != null) {
                String result = e.schema + "." + e.tableName;
                return result;
            }
        }

        if (tableDocModelIndex.containsKey(upper)) {
            TableDocModel m = tableDocModelIndex.get(upper);
            if (m != null && m.schema != null) {
                String result = m.schema + "." + m.tableName;
                return result;
            }
        }

        // 8. Unqualified Name with explicit default schema context
        if (defaultSchema != null && !defaultSchema.trim().isEmpty() && isValidSqlIdentifier(upper)) {
            String defUpper = defaultSchema.trim().toUpperCase();
            String candidate = defUpper + "." + upper;
            if (canonicalObjectsMap.containsKey(candidate) || tableIndex.containsKey(candidate)) {
                String result = candidate;
                return result;
            }
        }

        // 9. Scan across all known schemas to see if this object exists in any schema
        for (String s : KNOWN_SCHEMAS) {
            String candidate = s + "." + upper;
            if (canonicalObjectsMap.containsKey(candidate) || tableIndex.containsKey(candidate) || tableDocModelIndex.containsKey(candidate)) {
                String result = candidate;
                return result;
            }
        }

        // 10. Florida Lottery naming convention prefix inference (e.g. ARS_*, TRANS_*, GAME_*, INET_*)
        String inferredSchema = inferSchemaFromPrefix(upper);
        if (inferredSchema != null && isValidSqlIdentifier(upper)) {
            String result = inferredSchema + "." + upper;
            return result;
        }

        // 11. If default schema provided and identifier is valid SQL name
        if (defaultSchema != null && !defaultSchema.trim().isEmpty() && isCommonDbPrefix(upper) && isValidSqlIdentifier(upper)) {
            String result = defaultSchema.trim().toUpperCase() + "." + upper;
            return result;
        }

        // Not a recognizable database object
        String result = null;
        return result;
    }

    /**
     * Retrieves the metadata model ({@link DbObject}) for a canonical database object name.
     *
     * @param canonicalName canonical name in "SCHEMA.OBJECT" format (e.g. "ARS.T_DAILY_SALES")
     * @return typed {@link DbObject} model with schema, name, type, and columns
     */
    public DbObject getObjectByCanonicalName(String canonicalName) {
        if (canonicalName == null || canonicalName.trim().isEmpty()) {
            DbObject result = null;
            return result;
        }

        ensureInitialized();

        String clean = stripTokenDelimiters(canonicalName.trim()).toUpperCase();
        if (clean.contains("@")) {
            int atIdx = clean.indexOf('@');
            clean = clean.substring(0, atIdx).trim();
        }

        // 1. Direct hit in canonical objects index
        DbObject existing = canonicalObjectsMap.get(clean);
        if (existing != null) {
            DbObject result = existing;
            return result;
        }

        // 2. Lookup in tableIndex
        TableCatalogEntry tblEntry = tableIndex.get(clean);
        if (tblEntry != null) {
            DbObject created = new DbObject(
                    tblEntry.schema,
                    tblEntry.tableName,
                    tblEntry.tableType != null ? tblEntry.tableType : "TABLE",
                    "DEVOLTP",
                    new ArrayList<>(tblEntry.columns.keySet()),
                    tblEntry.primaryKeys,
                    "Discovered Table in Catalog",
                    null
            );
            registerObject(created);
            DbObject result = created;
            return result;
        }

        // 3. Lookup in tableDocModelIndex
        TableDocModel docModel = tableDocModelIndex.get(clean);
        if (docModel != null) {
            List<String> cols = new ArrayList<>();
            if (docModel.columns != null) {
                for (TableColumnDoc c : docModel.columns) {
                    cols.add(c.columnName);
                }
            }
            DbObject created = new DbObject(
                    docModel.schema,
                    docModel.tableName,
                    docModel.tableType != null ? docModel.tableType : "TABLE",
                    docModel.databaseName != null ? docModel.databaseName : "DEVOLTP",
                    cols,
                    docModel.primaryKeys,
                    docModel.description,
                    null
            );
            registerObject(created);
            DbObject result = created;
            return result;
        }

        // 4. If clean does not contain dot, attempt canonical resolution first
        if (!clean.contains(".")) {
            String resolvedCanonical = tryGetCanonicalName(clean);
            if (resolvedCanonical != null && !resolvedCanonical.equalsIgnoreCase(clean)) {
                DbObject result = getObjectByCanonicalName(resolvedCanonical);
                return result;
            }
        }

        // 5. Synthesize on-demand DbObject from schema and object name components
        String schema = "UNKNOWN";
        String objName = clean;
        if (clean.contains(".")) {
            int dotIdx = clean.indexOf('.');
            schema = clean.substring(0, dotIdx).trim();
            objName = clean.substring(dotIdx + 1).trim();
        } else {
            String inferred = inferSchemaFromPrefix(clean);
            if (inferred != null) {
                schema = inferred;
            }
        }

        String inferredType = inferObjectTypeFromName(objName);
        DbObject dynamicObj = new DbObject(
                schema,
                objName,
                inferredType,
                "DEVOLTP",
                Collections.emptyList(),
                Collections.emptyList(),
                "Dynamically Resolved Database Object",
                null
        );
        registerObject(dynamicObj);

        DbObject result = dynamicObj;
        return result;
    }

    /**
     * Resolves canonical name, returning the uppercase input as fallback if resolution yields null.
     */
    public String getCanonicalName(String rawName) {
        String canonical = tryGetCanonicalName(rawName);
        String result = (canonical != null) ? canonical : (rawName != null ? rawName.trim().toUpperCase() : null);
        return result;
    }

    /**
     * Checks if a token represents an identifiable database object.
     */
    public boolean isKnownObject(String name) {
        String canonical = tryGetCanonicalName(name);
        boolean result = (canonical != null);
        return result;
    }

    public boolean containsObject(String name) {
        boolean result = isKnownObject(name);
        return result;
    }

    public DbObject getObject(String name) {
        String canonical = tryGetCanonicalName(name);
        if (canonical == null) {
            canonical = name;
        }
        DbObject result = getObjectByCanonicalName(canonical);
        return result;
    }

    public DbObject findObject(String name) {
        DbObject result = getObject(name);
        return result;
    }

    public Optional<DbObject> findDbObject(String name) {
        String canonical = tryGetCanonicalName(name);
        if (canonical != null) {
            Optional<DbObject> result = Optional.ofNullable(getObjectByCanonicalName(canonical));
            return result;
        }
        Optional<DbObject> result = Optional.empty();
        return result;
    }

    public List<DbObject> searchObjects(String query) {
        if (query == null || query.trim().isEmpty()) {
            List<DbObject> result = Collections.emptyList();
            return result;
        }
        ensureInitialized();
        String upperQ = query.trim().toUpperCase();
        List<DbObject> matches = new ArrayList<>();
        for (DbObject obj : canonicalObjectsMap.values()) {
            if (obj.getCanonicalName().contains(upperQ) || obj.getObjectName().contains(upperQ)) {
                matches.add(obj);
            }
        }
        List<DbObject> result = matches;
        return result;
    }

    public List<DbObject> getObjectsBySchema(String schema) {
        if (schema == null || schema.trim().isEmpty()) {
            List<DbObject> result = Collections.emptyList();
            return result;
        }
        ensureInitialized();
        String upperS = schema.trim().toUpperCase();
        List<DbObject> matches = new ArrayList<>();
        for (DbObject obj : canonicalObjectsMap.values()) {
            if (obj.getSchema().equalsIgnoreCase(upperS)) {
                matches.add(obj);
            }
        }
        List<DbObject> result = matches;
        return result;
    }

    public Collection<DbObject> getAllObjects() {
        ensureInitialized();
        Collection<DbObject> result = Collections.unmodifiableCollection(canonicalObjectsMap.values());
        return result;
    }

    public Set<String> getAllCanonicalNames() {
        ensureInitialized();
        Set<String> result = Collections.unmodifiableSet(allCanonicalNames);
        return result;
    }

    // =========================================================================
    // TOKEN SANITIZATION & HEURISTIC HELPERS (Safe Escapes)
    // =========================================================================

    private String stripTokenDelimiters(String token) {
        if (token == null) {
            String result = "";
            return result;
        }
        String s = token.trim();
        while (s.length() > 0) {
            char first = s.charAt(0);
            if (first == '"' || first == '\'' || first == '[' || first == '`' || first == '(') {
                s = s.substring(1).trim();
            } else {
                break;
            }
        }
        while (s.length() > 0) {
            char last = s.charAt(s.length() - 1);
            if (last == '"' || last == '\'' || last == ']' || last == '`' || last == ')' || last == ';' || last == ',') {
                s = s.substring(0, s.length() - 1).trim();
            } else {
                break;
            }
        }
        String result = s;
        return result;
    }

    private String resolveShellVariables(String token) {
        if (token == null || !token.contains("$")) {
            String result = token;
            return result;
        }
        String s = token;
        Pattern p1 = Pattern.compile("^\\$[{]?([A-Za-z0-9_]+)[}]?\\.([A-Za-z0-9_]+)$");
        Matcher m1 = p1.matcher(s);
        if (m1.find()) {
            String varName = m1.group(1).toUpperCase();
            String tbl = m1.group(2);
            String schema = "TRANSLATOR";
            if (varName.contains("ARS")) schema = "ARS";
            else if (varName.contains("GAME")) schema = "GAME";
            else if (varName.contains("INET")) schema = "INET";
            else if (varName.contains("PPS")) schema = "PPS";
            else if (varName.contains("LMS")) schema = "LMS";
            else if (varName.contains("GL")) schema = "GL";
            else if (varName.contains("STAGE") || varName.contains("STG")) schema = "TRANSLATOR";
            String result = schema + "." + tbl;
            return result;
        }
        Pattern p2 = Pattern.compile("^\\$[{]?([A-Za-z0-9_]+)[}]?$");
        Matcher m2 = p2.matcher(s);
        if (m2.find()) {
            String varName = m2.group(1).toUpperCase();
            if (varName.startsWith("T_") || varName.startsWith("V_") || varName.startsWith("TBL_")) {
                String result = varName.replace("TBL_", "T_");
                return result;
            }
        }
        String result = s;
        return result;
    }

    private boolean isIgnoredReservedToken(String token) {
        if (token == null || token.isEmpty()) {
            boolean result = true;
            return result;
        }
        String upper = token.toUpperCase();
        if (RESERVED_KEYWORDS.contains(upper)) {
            boolean result = true;
            return result;
        }
        if (token.length() <= 1) {
            boolean result = true;
            return result;
        }
        if (token.matches("^-?\\d+(\\.\\d+)?$")) {
            boolean result = true;
            return result;
        }
        if (token.startsWith("/") || token.startsWith("\\") || token.startsWith("-") ||
                token.contains(".ksh") || token.contains(".sh") || token.contains(".sql") ||
                token.contains(".log") || token.contains(".txt") || token.contains(".csv")) {
            boolean result = true;
            return result;
        }
        boolean result = false;
        return result;
    }

    private boolean isKnownSchema(String s) {
        if (s == null) {
            boolean result = false;
            return result;
        }
        boolean result = KNOWN_SCHEMAS.contains(s.trim().toUpperCase());
        return result;
    }

    private boolean isValidSqlIdentifier(String s) {
        if (s == null || s.isEmpty()) {
            boolean result = false;
            return result;
        }
        boolean result = s.matches("^[A-Za-z][A-Za-z0-9_#$]*$");
        return result;
    }

    private boolean isCommonDbPrefix(String s) {
        if (s == null) {
            boolean result = false;
            return result;
        }
        String u = s.toUpperCase();
        boolean result = u.startsWith("T_") || u.startsWith("V_") || u.startsWith("SP_") ||
                u.startsWith("PKG_") || u.startsWith("FN_") || u.startsWith("TRG_") ||
                u.startsWith("STG_") || u.startsWith("MV_") || u.startsWith("SEQ_");
        return result;
    }

    private String inferSchemaFromPrefix(String tableName) {
        if (tableName == null) {
            String result = null;
            return result;
        }
        String u = tableName.toUpperCase();
        if (u.startsWith("ARS_") || u.startsWith("T_ARS_")) {
            String result = "ARS";
            return result;
        }
        if (u.startsWith("GAME_") || u.startsWith("T_GAME_") || u.startsWith("DRAW_")) {
            String result = "GAME";
            return result;
        }
        if (u.startsWith("INET_") || u.startsWith("T_INET_") || u.startsWith("PLAYER_")) {
            String result = "INET";
            return result;
        }
        if (u.startsWith("PPS_") || u.startsWith("T_PPS_")) {
            String result = "PPS";
            return result;
        }
        if (u.startsWith("LMS_") || u.startsWith("T_LMS_")) {
            String result = "LMS";
            return result;
        }
        if (u.startsWith("GL_") || u.startsWith("T_GL_")) {
            String result = "GL";
            return result;
        }
        if (u.startsWith("WRS_") || u.startsWith("T_WRS_")) {
            String result = "WRS";
            return result;
        }
        if (u.startsWith("DW_") || u.startsWith("DWDOL_") || u.startsWith("FACT_") || u.startsWith("DIM_")) {
            String result = "DWDOL";
            return result;
        }
        if (u.startsWith("STG_") || u.startsWith("STAGE_") || u.startsWith("TRANS_") || u.startsWith("TRANSLATOR_")) {
            String result = "TRANSLATOR";
            return result;
        }
        String result = null;
        return result;
    }

    private String inferObjectTypeFromName(String name) {
        if (name == null) {
            String result = "DATABASE OBJECT";
            return result;
        }
        String u = name.toUpperCase();
        if (u.startsWith("V_") || u.startsWith("VW_") || u.startsWith("VIEW_")) {
            String result = "VIEW";
            return result;
        }
        if (u.startsWith("MV_") || u.startsWith("MVIEW_")) {
            String result = "MATERIALIZED VIEW";
            return result;
        }
        if (u.startsWith("SP_") || u.startsWith("PRC_") || u.startsWith("PROC_")) {
            String result = "PROCEDURE";
            return result;
        }
        if (u.startsWith("PKG_") || u.startsWith("PACKAGE_")) {
            String result = "PACKAGE";
            return result;
        }
        if (u.startsWith("FN_") || u.startsWith("FUNC_")) {
            String result = "FUNCTION";
            return result;
        }
        if (u.startsWith("TRG_") || u.startsWith("TRIGGER_")) {
            String result = "TRIGGER";
            return result;
        }
        if (u.startsWith("SEQ_")) {
            String result = "SEQUENCE";
            return result;
        }
        String result = "TABLE";
        return result;
    }

    // =========================================================================
    // CLI ENTRY POINT
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("Florida Lottery DbObjectSearcher - Table Relationship Discovery Engine");
        System.out.println("Author: Ryan Stokes (Programmer Analyst III)");
        System.out.println("================================================================================");

        DbObjectSearcher searcher = DbObjectSearcher.getInstance();
        searcher.initializeDefault();

        String targetTable = "ACCT_MONTHLY_ADJUST_STG";
        String targetSchema = "TRANSLATOR";

        for (String arg : args) {
            if (arg.startsWith("-table=")) targetTable = arg.substring(7);
            if (arg.startsWith("-schema=")) targetSchema = arg.substring(8);
        }

        String report = searcher.generateLineageSummaryReport(targetSchema, targetTable);
        System.out.println(report);
    }
}
