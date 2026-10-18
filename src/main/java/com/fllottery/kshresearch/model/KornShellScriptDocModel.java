package com.fllottery.kshresearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Enterprise model representing a Florida Lottery Korn Shell (.ksh) batch script,
 * including its IWS scheduler alignment, metadata headers, mined AST references,
 * and lineage dependencies.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KornShellScriptDocModel implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("Filename")
    private String filename;

    @JsonProperty("AbsolutePath")
    private String absolutePath;

    @JsonProperty("FileType")
    private String fileType;

    @JsonProperty("Code")
    private String code;

    @JsonProperty("JobSet")
    private String jobSet;

    @JsonProperty("JobStreamName")
    private String jobStreamName;

    @JsonProperty("JobName")
    private String jobName;

    @JsonProperty("TestingNotes")
    private String testingNotes;

    @JsonProperty("FileNumber")
    private String fileNumber;

    @JsonProperty("Author")
    private String author;

    @JsonProperty("CreationDate")
    private String creationDate;

    @JsonProperty("Purpose")
    private String purpose;

    @JsonProperty("LineCount")
    private int lineCount;

    @JsonProperty("ComplexityScore")
    private int complexityScore;

    @JsonProperty("ScriptReferences")
    private List<ScriptReferenceDoc> scriptReferences = new ArrayList<>();

    @JsonProperty("Parameters")
    private List<String> parameters = new ArrayList<>();

    @JsonProperty("LastAnalyzedTimestamp")
    private String lastAnalyzedTimestamp;

    @JsonProperty("AiResearch")
    private KshAiResearchDoc aiResearch;

    public KornShellScriptDocModel() {
        this.fileType = "Korn Shell Script";
    }

    public String getFilename() {
        String result = this.filename;
        return result;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getAbsolutePath() {
        String result = this.absolutePath;
        return result;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    public String getFileType() {
        String result = this.fileType;
        return result;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getCode() {
        String result = this.code;
        return result;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getJobSet() {
        String result = this.jobSet;
        return result;
    }

    public void setJobSet(String jobSet) {
        this.jobSet = jobSet;
    }

    public String getJobStreamName() {
        String result = this.jobStreamName;
        return result;
    }

    public void setJobStreamName(String jobStreamName) {
        this.jobStreamName = jobStreamName;
    }

    public String getJobName() {
        String result = this.jobName;
        return result;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getTestingNotes() {
        String result = this.testingNotes;
        return result;
    }

    public void setTestingNotes(String testingNotes) {
        this.testingNotes = testingNotes;
    }

    public String getFileNumber() {
        String result = this.fileNumber;
        return result;
    }

    public void setFileNumber(String fileNumber) {
        this.fileNumber = fileNumber;
    }

    public String getAuthor() {
        String result = this.author;
        return result;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCreationDate() {
        String result = this.creationDate;
        return result;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getPurpose() {
        String result = this.purpose;
        return result;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public int getLineCount() {
        int result = this.lineCount;
        return result;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public int getComplexityScore() {
        int result = this.complexityScore;
        return result;
    }

    public void setComplexityScore(int complexityScore) {
        this.complexityScore = complexityScore;
    }

    public List<ScriptReferenceDoc> getScriptReferences() {
        List<ScriptReferenceDoc> result = this.scriptReferences;
        return result;
    }

    public void setScriptReferences(List<ScriptReferenceDoc> scriptReferences) {
        this.scriptReferences = scriptReferences != null ? scriptReferences : new ArrayList<>();
    }

    public void addReference(ScriptReferenceDoc ref) {
        if (ref != null) {
            if (this.scriptReferences == null) {
                this.scriptReferences = new ArrayList<>();
            }
            if (!this.scriptReferences.contains(ref)) {
                this.scriptReferences.add(ref);
            }
        }
    }

    public List<String> getParameters() {
        List<String> result = this.parameters;
        return result;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    public String getLastAnalyzedTimestamp() {
        String result = this.lastAnalyzedTimestamp;
        return result;
    }

    public void setLastAnalyzedTimestamp(String lastAnalyzedTimestamp) {
        this.lastAnalyzedTimestamp = lastAnalyzedTimestamp;
    }

    public KshAiResearchDoc getAiResearch() {
        KshAiResearchDoc result = this.aiResearch;
        return result;
    }

    public void setAiResearch(KshAiResearchDoc aiResearch) {
        this.aiResearch = aiResearch;
    }

    public List<ScriptReferenceDoc> getReferencesByType(ScriptReferenceType type) {
        if (this.scriptReferences == null || type == null) {
            List<ScriptReferenceDoc> emptyResult = Collections.emptyList();
            return emptyResult;
        }
        List<ScriptReferenceDoc> result = this.scriptReferences.stream()
                .filter(r -> r.getType() == type)
                .collect(Collectors.toList());
        return result;
    }

    public List<ScriptReferenceDoc> getOracleProcedures() {
        List<ScriptReferenceDoc> result = getReferencesByType(ScriptReferenceType.ORACLE_STORED_PROCEDURE);
        return result;
    }

    public List<ScriptReferenceDoc> getSqlPlusScripts() {
        List<ScriptReferenceDoc> result = getReferencesByType(ScriptReferenceType.SQLPLUS_SCRIPT);
        return result;
    }

    public List<ScriptReferenceDoc> getSourcedEnvironments() {
        List<ScriptReferenceDoc> result = getReferencesByType(ScriptReferenceType.SOURCED_ENVIRONMENT);
        return result;
    }

    public List<ScriptReferenceDoc> getLogFiles() {
        List<ScriptReferenceDoc> result = getReferencesByType(ScriptReferenceType.FILESYSTEM_LOG);
        return result;
    }

    public List<ScriptReferenceDoc> getMailxNotifications() {
        List<ScriptReferenceDoc> result = getReferencesByType(ScriptReferenceType.MAILX_NOTIFICATION);
        return result;
    }

    public List<ScriptReferenceDoc> getChildScripts() {
        List<ScriptReferenceDoc> result = getReferencesByType(ScriptReferenceType.CHILD_KSH_SCRIPT);
        return result;
    }

    public String getComplexityLevel() {
        String result;
        if (this.complexityScore > 35) {
            result = "CRITICAL";
        } else if (this.complexityScore > 20) {
            result = "HIGH";
        } else if (this.complexityScore > 10) {
            result = "MEDIUM";
        } else {
            result = "LOW";
        }
        return result;
    }

    public String getBaseFilename() {
        if (this.filename == null) {
            String result = "UNKNOWN.ksh";
            return result;
        }
        int lastSlash = Math.max(this.filename.lastIndexOf('/'), this.filename.lastIndexOf('\\'));
        String result;
        if (lastSlash >= 0 && lastSlash < this.filename.length() - 1) {
            result = this.filename.substring(lastSlash + 1);
        } else {
            result = this.filename;
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KornShellScriptDocModel that = (KornShellScriptDocModel) o;
        boolean result = Objects.equals(filename, that.filename) &&
                Objects.equals(jobName, that.jobName);
        return result;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(filename, jobName);
        return result;
    }

    @Override
    public String toString() {
        String result = "KornShellScriptDocModel{" +
                "filename='" + filename + '\'' +
                ", jobStreamName='" + jobStreamName + '\'' +
                ", jobName='" + jobName + '\'' +
                ", referencesCount=" + (scriptReferences != null ? scriptReferences.size() : 0) +
                '}';
        return result;
    }
}
