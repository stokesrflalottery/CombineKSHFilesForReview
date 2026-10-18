package com.fllottery.dbresearch.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable relationship model connecting two database tables across schemas.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class TableRelationship implements Comparable<TableRelationship>, Serializable {

    private static final long serialVersionUID = 1L;

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
        if (this.enforced != o.enforced) {
            int result = this.enforced ? -1 : 1;
            return result;
        }
        int confCmp = Double.compare(o.confidenceLevel.getScore(), this.confidenceLevel.getScore());
        if (confCmp != 0) {
            int result = confCmp;
            return result;
        }
        int occCmp = Integer.compare(o.occurrences, this.occurrences);
        if (occCmp != 0) {
            int result = occCmp;
            return result;
        }
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
