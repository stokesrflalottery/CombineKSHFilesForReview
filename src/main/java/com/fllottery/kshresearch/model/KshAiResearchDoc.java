package com.fllottery.kshresearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise AI-Generated Technical Research and Business Intelligence Model
 * for Florida Lottery Korn Shell (.ksh) batch scripts.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KshAiResearchDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("ExecutiveSummary")
    private String executiveSummary = "";

    @JsonProperty("BusinessPurpose")
    private String businessPurpose = "";

    @JsonProperty("UserStory")
    private String userStory = "";

    @JsonProperty("ChronologicalWorkflow")
    private List<String> chronologicalWorkflow = new ArrayList<>();

    @JsonProperty("InputsAndEnvironment")
    private List<String> inputsAndEnvironment = new ArrayList<>();

    @JsonProperty("OutputsAndArtifacts")
    private List<String> outputsAndArtifacts = new ArrayList<>();

    @JsonProperty("OperationalFailureRunbook")
    private String operationalFailureRunbook = "";

    @JsonProperty("IdempotencyAndRerunSafety")
    private String idempotencyAndRerunSafety = "";

    @JsonProperty("JavaMigrationBlueprint")
    private String javaMigrationBlueprint = "";

    @JsonProperty("ResearchModelVersion")
    private String researchModelVersion = "gemini-3.8-flash";

    @JsonProperty("ResearchTimestamp")
    private String researchTimestamp = "";

    public KshAiResearchDoc() {
    }

    public String getExecutiveSummary() {
        String result = this.executiveSummary;
        return result;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary != null ? executiveSummary : "";
    }

    public String getBusinessPurpose() {
        String result = this.businessPurpose;
        return result;
    }

    public void setBusinessPurpose(String businessPurpose) {
        this.businessPurpose = businessPurpose != null ? businessPurpose : "";
    }

    public String getUserStory() {
        String result = this.userStory;
        return result;
    }

    public void setUserStory(String userStory) {
        this.userStory = userStory != null ? userStory : "";
    }

    public List<String> getChronologicalWorkflow() {
        List<String> result = this.chronologicalWorkflow;
        return result;
    }

    public void setChronologicalWorkflow(List<String> chronologicalWorkflow) {
        this.chronologicalWorkflow = chronologicalWorkflow != null ? chronologicalWorkflow : new ArrayList<>();
    }

    public List<String> getInputsAndEnvironment() {
        List<String> result = this.inputsAndEnvironment;
        return result;
    }

    public void setInputsAndEnvironment(List<String> inputsAndEnvironment) {
        this.inputsAndEnvironment = inputsAndEnvironment != null ? inputsAndEnvironment : new ArrayList<>();
    }

    public List<String> getOutputsAndArtifacts() {
        List<String> result = this.outputsAndArtifacts;
        return result;
    }

    public void setOutputsAndArtifacts(List<String> outputsAndArtifacts) {
        this.outputsAndArtifacts = outputsAndArtifacts != null ? outputsAndArtifacts : new ArrayList<>();
    }

    public String getOperationalFailureRunbook() {
        String result = this.operationalFailureRunbook;
        return result;
    }

    public void setOperationalFailureRunbook(String operationalFailureRunbook) {
        this.operationalFailureRunbook = operationalFailureRunbook != null ? operationalFailureRunbook : "";
    }

    public String getIdempotencyAndRerunSafety() {
        String result = this.idempotencyAndRerunSafety;
        return result;
    }

    public void setIdempotencyAndRerunSafety(String idempotencyAndRerunSafety) {
        this.idempotencyAndRerunSafety = idempotencyAndRerunSafety != null ? idempotencyAndRerunSafety : "";
    }

    public String getJavaMigrationBlueprint() {
        String result = this.javaMigrationBlueprint;
        return result;
    }

    public void setJavaMigrationBlueprint(String javaMigrationBlueprint) {
        this.javaMigrationBlueprint = javaMigrationBlueprint != null ? javaMigrationBlueprint : "";
    }

    public String getResearchModelVersion() {
        String result = this.researchModelVersion;
        return result;
    }

    public void setResearchModelVersion(String researchModelVersion) {
        this.researchModelVersion = researchModelVersion != null ? researchModelVersion : "";
    }

    public String getResearchTimestamp() {
        String result = this.researchTimestamp;
        return result;
    }

    public void setResearchTimestamp(String researchTimestamp) {
        this.researchTimestamp = researchTimestamp != null ? researchTimestamp : "";
    }
}
