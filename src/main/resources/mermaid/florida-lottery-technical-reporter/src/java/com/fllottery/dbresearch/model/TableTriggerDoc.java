package com.fllottery.dbresearch.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;

/**
 * Trigger metadata documentation model.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class TableTriggerDoc implements Serializable {

    private static final long serialVersionUID = 1L;

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

    public String getTriggerName() {
        String result = this.triggerName;
        return result;
    }

    public String getTriggerType() {
        String result = this.triggerType;
        return result;
    }

    public String getTriggeringEvent() {
        String result = this.triggeringEvent;
        return result;
    }

    public String getStatus() {
        String result = this.status;
        return result;
    }

    public String getDescription() {
        String result = this.description;
        return result;
    }
}
