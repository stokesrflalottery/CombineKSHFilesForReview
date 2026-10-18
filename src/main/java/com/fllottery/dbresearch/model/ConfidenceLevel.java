package com.fllottery.dbresearch.model;

/**
 * Confidence rating for discovered database table relationships.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
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
