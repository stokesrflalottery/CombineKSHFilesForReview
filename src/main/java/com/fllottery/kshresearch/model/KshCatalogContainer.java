package com.fllottery.kshresearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level root JSON container representing the exported Florida Lottery
 * Korn Shell script catalog cache.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KshCatalogContainer implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("Scripts")
    private List<KornShellScriptDocModel> scripts = new ArrayList<>();

    public KshCatalogContainer() {
    }

    public KshCatalogContainer(List<KornShellScriptDocModel> scripts) {
        this.scripts = scripts != null ? scripts : new ArrayList<>();
    }

    public List<KornShellScriptDocModel> getScripts() {
        List<KornShellScriptDocModel> result = this.scripts;
        return result;
    }

    public void setScripts(List<KornShellScriptDocModel> scripts) {
        this.scripts = scripts != null ? scripts : new ArrayList<>();
    }

    public int size() {
        int result = this.scripts != null ? this.scripts.size() : 0;
        return result;
    }

    @Override
    public String toString() {
        String result = "KshCatalogContainer{" +
                "scriptsCount=" + size() +
                '}';
        return result;
    }
}
