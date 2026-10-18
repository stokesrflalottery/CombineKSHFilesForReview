package com.fllottery.ksh.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * Encapsulates a granular external or internal dependency mined from Korn Shell script code.
 * Follows the Florida Lottery enterprise lineage documentation standards.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScriptReferenceDoc implements Serializable, Comparable<ScriptReferenceDoc> {

    private static final long serialVersionUID = 1L;

    @JsonProperty("Type")
    private ScriptReferenceType type;

    @JsonProperty("Target")
    private String target;

    @JsonProperty("RawLine")
    private String rawLine;

    @JsonProperty("LineNumber")
    private int lineNumber;

    @JsonProperty("Notes")
    private String notes;

    @JsonProperty("Direction")
    private String direction; // INCOMING, OUTGOING, BIDIRECTIONAL

    @JsonProperty("Confidence")
    private String confidence; // EXACT, HEURISTIC

    @JsonProperty("Schema")
    private String schema;

    @JsonProperty("PackageOrObject")
    private String packageOrObject;

    @JsonProperty("MethodOrSubroutine")
    private String methodOrSubroutine;

    public ScriptReferenceDoc() {
        this.direction = "OUTGOING";
        this.confidence = "EXACT";
    }

    public ScriptReferenceDoc(ScriptReferenceType type, String target, int lineNumber, String rawLine, String notes) {
        this.type = type;
        this.target = target;
        this.lineNumber = lineNumber;
        this.rawLine = rawLine;
        this.notes = notes;
        this.direction = "OUTGOING";
        this.confidence = "EXACT";
    }

    public ScriptReferenceType getType() {
        ScriptReferenceType result = this.type;
        return result;
    }

    public void setType(ScriptReferenceType type) {
        this.type = type;
    }

    public String getTarget() {
        String result = this.target;
        return result;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getRawLine() {
        String result = this.rawLine;
        return result;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public int getLineNumber() {
        int result = this.lineNumber;
        return result;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getNotes() {
        String result = this.notes;
        return result;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDirection() {
        String result = this.direction;
        return result;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getConfidence() {
        String result = this.confidence;
        return result;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getSchema() {
        String result = this.schema;
        return result;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getPackageOrObject() {
        String result = this.packageOrObject;
        return result;
    }

    public void setPackageOrObject(String packageOrObject) {
        this.packageOrObject = packageOrObject;
    }

    public String getMethodOrSubroutine() {
        String result = this.methodOrSubroutine;
        return result;
    }

    public void setMethodOrSubroutine(String methodOrSubroutine) {
        this.methodOrSubroutine = methodOrSubroutine;
    }

    /**
     * Splits an Oracle target expression like 'ARS.Age_ARTABAL.Driver' into schema, package, and procedure fields.
     */
    public void populateOracleComponents() {
        if (this.target == null || this.target.isBlank()) {
            return;
        }

        String[] parts = this.target.split("\\.");
        if (parts.length >= 3) {
            this.schema = parts[0];
            this.packageOrObject = parts[1];
            this.methodOrSubroutine = parts[2];
        } else if (parts.length == 2) {
            this.schema = parts[0];
            this.packageOrObject = parts[1];
        } else if (parts.length == 1) {
            this.packageOrObject = parts[0];
        }
    }

    @Override
    public int compareTo(ScriptReferenceDoc o) {
        if (o == null) {
            int result = 1;
            return result;
        }
        int typeCompare = Integer.compare(
                this.type != null ? this.type.ordinal() : 999,
                o.type != null ? o.type.ordinal() : 999
        );
        if (typeCompare != 0) {
            int result = typeCompare;
            return result;
        }
        int lineCompare = Integer.compare(this.lineNumber, o.lineNumber);
        if (lineCompare != 0) {
            int result = lineCompare;
            return result;
        }
        String targetA = this.target != null ? this.target : "";
        String targetB = o.target != null ? o.target : "";
        int result = targetA.compareToIgnoreCase(targetB);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptReferenceDoc that = (ScriptReferenceDoc) o;
        boolean result = lineNumber == that.lineNumber &&
                type == that.type &&
                Objects.equals(target, that.target);
        return result;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, target, lineNumber);
        return result;
    }

    @Override
    public String toString() {
        String result = "ScriptReferenceDoc{" +
                "type=" + type +
                ", target='" + target + '\'' +
                ", lineNumber=" + lineNumber +
                ", confidence='" + confidence + '\'' +
                '}';
        return result;
    }
}
