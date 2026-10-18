package com.fllottery.kshresearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * Enterprise representation of an individual external reference or operational dependency
 * mined from a Florida Lottery Korn Shell batch asset.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScriptReferenceDoc implements Serializable, Comparable<ScriptReferenceDoc> {

    private static final long serialVersionUID = 1L;

    @JsonProperty("Type")
    private ScriptReferenceType type;

    @JsonProperty("Target")
    private String target;

    @JsonProperty("LineNumber")
    private int lineNumber;

    @JsonProperty("Direction")
    private String direction;

    @JsonProperty("Notes")
    private String notes;

    @JsonProperty("SourceSnippet")
    private String sourceSnippet = "";

    @JsonProperty("PackageName")
    private String packageName = "";

    @JsonProperty("ProcedureName")
    private String procedureName = "";

    @JsonProperty("Confidence")
    private String confidence = "EXACT";

    public ScriptReferenceDoc() {
    }

    public ScriptReferenceDoc(ScriptReferenceType type, String target, int lineNumber, String notes) {
        this(type, target, lineNumber, "", notes);
    }

    public ScriptReferenceDoc(ScriptReferenceType type, String target, int lineNumber, String sourceSnippet, String notes) {
        this.type = type;
        this.target = target;
        this.lineNumber = lineNumber;
        this.sourceSnippet = sourceSnippet != null ? sourceSnippet : "";
        this.notes = notes != null ? notes : "";
        this.direction = (type == ScriptReferenceType.SOURCED_ENVIRONMENT || type == ScriptReferenceType.SOURCED_ENV) ? "INCOMING" : "OUTGOING";
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

    public int getLineNumber() {
        int result = this.lineNumber;
        return result;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getDirection() {
        String result = this.direction;
        return result;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getNotes() {
        String result = this.notes;
        return result;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSourceSnippet() {
        String result = this.sourceSnippet;
        return result;
    }

    public void setSourceSnippet(String sourceSnippet) {
        this.sourceSnippet = sourceSnippet != null ? sourceSnippet : "";
    }

    public String getPackageName() {
        String result = this.packageName;
        return result;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName != null ? packageName : "";
    }

    public String getProcedureName() {
        String result = this.procedureName;
        return result;
    }

    public void setProcedureName(String procedureName) {
        this.procedureName = procedureName != null ? procedureName : "";
    }

    public String getConfidence() {
        String result = this.confidence;
        return result;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence != null ? confidence : "EXACT";
    }

    /**
     * Parses the referenced Oracle routine target into PackageName and ProcedureName components.
     */
    public void populateOracleComponents() {
        if (this.target == null || this.target.isBlank()) {
            return;
        }
        String clean = this.target.trim().replace("\"", "");
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length >= 3) {
                this.packageName = parts[1].toUpperCase();
                this.procedureName = parts[2].toUpperCase();
            } else if (parts.length == 2) {
                this.packageName = parts[0].toUpperCase();
                this.procedureName = parts[1].toUpperCase();
            } else {
                this.procedureName = clean.toUpperCase();
            }
        } else {
            this.procedureName = clean.toUpperCase();
        }
    }

    @Override
    public int compareTo(ScriptReferenceDoc o) {
        if (o == null) {
            return 1;
        }
        int lineCompare = Integer.compare(this.lineNumber, o.lineNumber);
        if (lineCompare != 0) {
            return lineCompare;
        }
        if (this.type != null && o.type != null) {
            int typeCompare = this.type.name().compareTo(o.type.name());
            if (typeCompare != 0) {
                return typeCompare;
            }
        }
        String t1 = this.target != null ? this.target : "";
        String t2 = o.target != null ? o.target : "";
        int result = t1.compareTo(t2);
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
                ", line=" + lineNumber +
                ", direction='" + direction + '\'' +
                '}';
        return result;
    }
}
