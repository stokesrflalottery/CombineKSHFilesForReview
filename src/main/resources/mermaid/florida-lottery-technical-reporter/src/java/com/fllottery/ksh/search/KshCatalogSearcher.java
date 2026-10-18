package com.fllottery.ksh.search;

import com.fllottery.ksh.model.KornShellScriptDocModel;
import com.fllottery.ksh.model.KshCatalogContainer;
import com.fllottery.ksh.model.ScriptReferenceDoc;
import com.fllottery.ksh.model.ScriptReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise In-Memory Catalog Search &amp; Lineage Query Engine.
 *
 * <p>Provides multi-dimensional indexing and instant query capabilities over the
 * Florida Lottery Korn Shell (.ksh) batch catalog by:
 * <ul>
 *   <li>Korn shell filename or relative path</li>
 *   <li>IWS Job Name or Job Stream</li>
 *   <li>Referenced Oracle Package or Stored Procedure</li>
 *   <li>Referenced Physical Database Tables</li>
 *   <li>Referenced Flat Files, Reports, or Logs</li>
 *   <li>Script Author and Creation Date</li>
 *   <li>Complexity Level and Reference Classification Type</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public class KshCatalogSearcher {

    private static final Logger log = LoggerFactory.getLogger(KshCatalogSearcher.java);

    private final List<KornShellScriptDocModel> catalog = new ArrayList<>();
    private final Map<String, KornShellScriptDocModel> indexByFilename = new HashMap<>();
    private final Map<String, List<KornShellScriptDocModel>> indexByJobStream = new TreeMap<>();
    private final Map<String, List<KornShellScriptDocModel>> indexByOracleProcedure = new TreeMap<>();
    private final Map<String, List<KornShellScriptDocModel>> indexByTable = new TreeMap<>();

    public KshCatalogSearcher() {
    }

    public KshCatalogSearcher(KshCatalogContainer container) {
        if (container != null && container.getScripts() != null) {
            indexAll(container.getScripts());
        }
    }

    public KshCatalogSearcher(List<KornShellScriptDocModel> scripts) {
        if (scripts != null) {
            indexAll(scripts);
        }
    }

    /**
     * Indexes a collection of analyzed scripts into the search matrices.
     *
     * @param scripts list of KornShellScriptDocModel to index
     */
    public synchronized void indexAll(Collection<KornShellScriptDocModel> scripts) {
        this.catalog.clear();
        this.indexByFilename.clear();
        this.indexByJobStream.clear();
        this.indexByOracleProcedure.clear();
        this.indexByTable.clear();

        if (scripts == null) {
            return;
        }

        for (KornShellScriptDocModel script : scripts) {
            indexScript(script);
        }
        log.info("Indexed {} Korn Shell scripts into searchable catalog.", this.catalog.size());
    }

    /**
     * Indexes a single script entry into memory.
     */
    public synchronized void indexScript(KornShellScriptDocModel script) {
        if (script == null) {
            return;
        }

        this.catalog.add(script);

        // Index by filename
        if (script.getFilename() != null) {
            this.indexByFilename.put(script.getFilename().toLowerCase(), script);
            this.indexByFilename.put(script.getBaseFilename().toLowerCase(), script);
        }

        // Index by JobStream
        String stream = script.getJobStreamName() != null ? script.getJobStreamName().toUpperCase() : "UNASSIGNED";
        this.indexByJobStream.computeIfAbsent(stream, k -> new ArrayList<>()).add(script);

        // Index by references
        if (script.getScriptReferences() != null) {
            for (ScriptReferenceDoc ref : script.getScriptReferences()) {
                if (ref.getType() == ScriptReferenceType.ORACLE_PROCEDURE && ref.getTarget() != null) {
                    this.indexByOracleProcedure.computeIfAbsent(ref.getTarget().toUpperCase(), k -> new ArrayList<>()).add(script);
                } else if (ref.getType() == ScriptReferenceType.DATABASE_TABLE && ref.getTarget() != null) {
                    this.indexByTable.computeIfAbsent(ref.getTarget().toUpperCase(), k -> new ArrayList<>()).add(script);
                }
            }
        }
    }

    /**
     * Finds a script by its exact or base filename.
     */
    public Optional<KornShellScriptDocModel> findByFilename(String filename) {
        if (filename == null) {
            Optional<KornShellScriptDocModel> emptyResult = Optional.empty();
            return emptyResult;
        }

        KornShellScriptDocModel match = this.indexByFilename.get(filename.toLowerCase());
        Optional<KornShellScriptDocModel> result = Optional.ofNullable(match);
        return result;
    }

    /**
     * Multi-field search across filename, IWS job, author, purpose, and dependencies.
     */
    public List<KornShellScriptDocModel> search(String query) {
        if (query == null || query.isBlank()) {
            List<KornShellScriptDocModel> allResult = new ArrayList<>(this.catalog);
            return allResult;
        }

        String q = query.trim().toLowerCase();

        List<KornShellScriptDocModel> matches = this.catalog.stream()
                .filter(s -> matchesScript(s, q))
                .collect(Collectors.toList());

        List<KornShellScriptDocModel> result = matches;
        return result;
    }

    private boolean matchesScript(KornShellScriptDocModel s, String q) {
        if (s.getFilename() != null && s.getFilename().toLowerCase().contains(q)) return true;
        if (s.getJobName() != null && s.getJobName().toLowerCase().contains(q)) return true;
        if (s.getJobStreamName() != null && s.getJobStreamName().toLowerCase().contains(q)) return true;
        if (s.getAuthor() != null && s.getAuthor().toLowerCase().contains(q)) return true;
        if (s.getPurpose() != null && s.getPurpose().toLowerCase().contains(q)) return true;

        if (s.getScriptReferences() != null) {
            for (ScriptReferenceDoc ref : s.getScriptReferences()) {
                if (ref.getTarget() != null && ref.getTarget().toLowerCase().contains(q)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Filters scripts by a specific ScriptReferenceType (e.g. only scripts invoking Oracle procedures).
     */
    public List<KornShellScriptDocModel> filterByReferenceType(ScriptReferenceType type) {
        if (type == null) {
            List<KornShellScriptDocModel> allResult = new ArrayList<>(this.catalog);
            return allResult;
        }

        List<KornShellScriptDocModel> matches = this.catalog.stream()
                .filter(s -> s.getScriptReferences() != null && s.getScriptReferences().stream().anyMatch(r -> r.getType() == type))
                .collect(Collectors.toList());

        List<KornShellScriptDocModel> result = matches;
        return result;
    }

    /**
     * Filters scripts by JobStream name (e.g. 'ARS', 'GAMING', 'CLAIMS').
     */
    public List<KornShellScriptDocModel> filterByJobStream(String jobStream) {
        if (jobStream == null || jobStream.isBlank()) {
            List<KornShellScriptDocModel> allResult = new ArrayList<>(this.catalog);
            return allResult;
        }

        List<KornShellScriptDocModel> list = this.indexByJobStream.get(jobStream.toUpperCase());
        List<KornShellScriptDocModel> result = list != null ? new ArrayList<>(list) : Collections.emptyList();
        return result;
    }

    /**
     * Returns all distinct Job Streams found in the catalog.
     */
    public Set<String> getDistinctJobStreams() {
        Set<String> result = new TreeSet<>(this.indexByJobStream.keySet());
        return result;
    }

    /**
     * Returns all distinct Oracle procedures referenced across the catalog.
     */
    public Set<String> getDistinctOracleProcedures() {
        Set<String> result = new TreeSet<>(this.indexByOracleProcedure.keySet());
        return result;
    }

    /**
     * Returns total count of indexed scripts.
     */
    public int getTotalScriptCount() {
        int result = this.catalog.size();
        return result;
    }

    /**
     * Calculates composite aggregate metrics for executive reporting.
     */
    public Map<String, Object> getCatalogStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        int totalScripts = this.catalog.size();
        long totalLoc = 0;
        int totalRefs = 0;
        int oracleCalls = 0;
        int logRedirections = 0;
        int mailxAlerts = 0;
        int sqlPlusExecs = 0;

        for (KornShellScriptDocModel s : this.catalog) {
            totalLoc += s.getLineCount();
            if (s.getScriptReferences() != null) {
                totalRefs += s.getScriptReferences().size();
                for (ScriptReferenceDoc r : s.getScriptReferences()) {
                    if (r.getType() == ScriptReferenceType.ORACLE_PROCEDURE) oracleCalls++;
                    else if (r.getType() == ScriptReferenceType.LOG_FILE) logRedirections++;
                    else if (r.getType() == ScriptReferenceType.NOTIFICATION_MAILX) mailxAlerts++;
                    else if (r.getType() == ScriptReferenceType.SQLPLUS_SCRIPT) sqlPlusExecs++;
                }
            }
        }

        stats.put("totalScripts", totalScripts);
        stats.put("totalLinesOfCode", totalLoc);
        stats.put("totalReferencesMined", totalRefs);
        stats.put("distinctJobStreams", this.indexByJobStream.size());
        stats.put("oracleProcedureCalls", oracleCalls);
        stats.put("sqlPlusExecutions", sqlPlusExecs);
        stats.put("logRedirections", logRedirections);
        stats.put("mailxAlerts", mailxAlerts);

        Map<String, Object> result = stats;
        return result;
    }

    public List<KornShellScriptDocModel> getAllScripts() {
        List<KornShellScriptDocModel> result = new ArrayList<>(this.catalog);
        return result;
    }
}
