package com.fllottery.dbresearch.model;

/**
 * Type of relationship discovery provenance.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
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
