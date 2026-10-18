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
public class ScriptReferenceDoc implements Serializable {

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

    public ScriptReferenceDoc() {
    }

    public ScriptReferenceDoc(ScriptReferenceType type, String target, int lineNumber, String notes) {
        this.type = type;
        this.target = target;
        this.lineNumber = lineNumber;
        this.notes = notes;
        this.direction = (type == ScriptReferenceType.SOURCED_ENVIRONMENT) ? "INCOMING" : "OUTGOING";
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
