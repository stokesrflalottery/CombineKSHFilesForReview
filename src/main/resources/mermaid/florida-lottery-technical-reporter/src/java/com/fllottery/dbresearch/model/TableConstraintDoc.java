package com.fllottery.dbresearch.model;

import java.io.Serializable;

/**
 * Constraint metadata documentation model.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class TableConstraintDoc implements Serializable {

    private static final long serialVersionUID = 1L;

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

    public String getConstraintName() {
        String result = this.constraintName;
        return result;
    }

    public String getConstraintType() {
        String result = this.constraintType;
        return result;
    }

    public String getColumns() {
        String result = this.columns;
        return result;
    }

    public String getDetails() {
        String result = this.details;
        return result;
    }

    public String getStatus() {
        String result = this.status;
        return result;
    }
}
