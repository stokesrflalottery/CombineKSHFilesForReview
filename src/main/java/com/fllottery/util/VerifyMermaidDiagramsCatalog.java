package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Diagnostic Utility: Verifies and audits all AI-generated Mermaid diagrams
 * stored in {@code DbCache_Ai_Researched_Catalog.json} against the Mermaid rendering engine.
 * <p>
 * Capabilities:
 *  1. Scans every routine in the catalog for Mermaid diagrams.
 *  2. Supports offline Microsoft Playwright (Chromium) engine and remote/local HTTP endpoints.
 *  3. Displays a live, synchronized console completion percentage bar during the verification run,
 *     suppressing per-node error noise until the process finishes.
 *  4. Aggregates all syntax and structural failures into a categorized "Common Issues" catalog.
 *  5. Generates structured diagnostic reports in Markdown, JSON, and standalone broken .mmd files
 *     so issues can be shared and rapidly resolved.
 * </p>
 *
 * Usage:
 *   java com.fllottery.util.VerifyMermaidDiagramsCatalog [options]
 *
 * Options:
 *   -catalog=C:\ocioldapps1\Cached\DbCache\Schemas\DbCache_Ai_Researched_Catalog.json
 *   -schema=INET (optional: filter by schema, e.g. DOLEMP, INET, ARS, or ALL)
 *   -routine=MODIFY_ROW_DN (optional: test a single routine)
 *   -engine=playwright (playwright, http, or auto; default: auto)
 *   -mermaidServer=http://localhost:8080 (optional: custom HTTP service)
 *   -mermaidJs=C:\path\to\mermaid.min.js (optional: explicit path to local Mermaid JS)
 *   -threads=4 (parallel verification concurrency, default: 4)
 *   -outputDir=./Mermaid_Verification_Report (output directory for diagnostics)
 *   -autoRepair=true (optional: export repaired catalog JSON)
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @since 2026-10-08
 */
public class VerifyMermaidDiagramsCatalog {

    private static final String DEFAULT_CATALOG_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\DbCache_Ai_Researched_Catalog.json";
    private static final String DEFAULT_FALLBACK_CATALOG = "DbCache_Ai_Researched_Catalog.json";
    private static final String DEFAULT_OUTPUT_DIR = "./Mermaid_Verification_Report";

    public static class VerificationResult {
        public final String lookupKey;
        public final String schema;
        public final String pkg;
        public final String routineName;
        public final boolean isValid;
        public final String rawMermaid;
        public final String cleanedMermaid;
        public final String errorMessage;
        public final String errorSnippet;
        public final int httpStatusCode;

        public VerificationResult(String lookupKey, String schema, String pkg, String routineName,
                                  boolean isValid, String rawMermaid, String cleanedMermaid,
                                  String errorMessage, String errorSnippet, int httpStatusCode) {
            this.lookupKey = lookupKey;
            this.schema = schema;
            this.pkg = pkg;
            this.routineName = routineName;
            this.isValid = isValid;
            this.rawMermaid = rawMermaid;
            this.cleanedMermaid = cleanedMermaid;
            this.errorMessage = errorMessage;
            this.errorSnippet = errorSnippet;
            this.httpStatusCode = httpStatusCode;
        }
    }

    public static void main(String[] args) {
        Map<String, String> argMap = parseCommandLineArgs(args);

        String catalogPath = argMap.getOrDefault("catalog", argMap.getOrDefault("input", DEFAULT_CATALOG_PATH));
        File catalogFile = new File(catalogPath);
        if (!catalogFile.exists()) {
            File fallback = new File(DEFAULT_FALLBACK_CATALOG);
            if (fallback.exists()) {
                catalogFile = fallback;
                catalogPath = fallback.getAbsolutePath();
            } else {
                System.err.println("=========================================================================");
                System.err.println("ACTION REQUIRED: Researched Catalog JSON not found at: " + catalogPath);
                System.err.println("Please specify via: -catalog=C:\\path\\to\\DbCache_Ai_Researched_Catalog.json");
                System.err.println("=========================================================================");
                return;
            }
        }

        // Configure Rendering Engine
        String engineChoice = argMap.getOrDefault("engine", "AUTO").trim().toUpperCase();
        if ("PLAYWRIGHT".equals(engineChoice)) {
            MermaidWordDocRenderer.setEngine(MermaidWordDocRenderer.RenderingEngine.PLAYWRIGHT);
        } else if ("HTTP".equals(engineChoice)) {
            MermaidWordDocRenderer.setEngine(MermaidWordDocRenderer.RenderingEngine.HTTP);
        } else {
            MermaidWordDocRenderer.setEngine(MermaidWordDocRenderer.RenderingEngine.AUTO);
        }

        // Custom Mermaid JS Path
        String customJs = argMap.get("mermaidJs");
        if (customJs == null) customJs = argMap.get("js");
        if (customJs != null && !customJs.trim().isEmpty()) {
            System.setProperty("mermaid.js.path", customJs.trim());
        }

        // Custom Mermaid Server URL
        String customServer = argMap.get("mermaidServer");
        if (customServer == null) customServer = argMap.get("server");
        if (customServer != null && !customServer.trim().isEmpty()) {
            MermaidWordDocRenderer.customMermaidServerUrl = customServer.trim();
        }

        String schemaFilter = argMap.getOrDefault("schema", "ALL").trim().toUpperCase();
        String routineFilter = argMap.getOrDefault("routine", argMap.getOrDefault("name", "")).trim().toUpperCase();
        int threads = Integer.parseInt(argMap.getOrDefault("threads", argMap.getOrDefault("parallel", "4")));
        String outputDirPath = argMap.getOrDefault("outputDir", DEFAULT_OUTPUT_DIR);
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        int delayMs = Integer.parseInt(argMap.getOrDefault("delayMs", argMap.getOrDefault("delay", "0")));
        boolean autoRepair = Boolean.parseBoolean(argMap.getOrDefault("autoRepair", argMap.getOrDefault("repair", "false")));

        boolean isPlaywright = MermaidWordDocRenderer.isPlaywrightAvailable();
        File localJs = MermaidWordDocRenderer.findLocalMermaidJs();

        System.out.println("=========================================================================");
        System.out.println("Florida Lottery - Enterprise Mermaid Diagram Validator & Verifier");
        System.out.println("Catalog File:     " + catalogFile.getAbsolutePath() + " (" + (catalogFile.length() / 1024) + " KB)");
        System.out.println("Engine Mode:      " + MermaidWordDocRenderer.getActiveEngine() + " (Playwright Ready: " + (isPlaywright ? "YES - Offline Chromium" : "NO - " + MermaidWordDocRenderer.getPlaywrightUnavailableReason()) + ")");
        System.out.println("Local Mermaid JS: " + (localJs != null ? localJs.getAbsolutePath() : "NOT FOUND (Will use HTTP endpoint fallback)"));
        if (MermaidWordDocRenderer.customMermaidServerUrl != null) {
            System.out.println("Custom HTTP Url:  " + MermaidWordDocRenderer.customMermaidServerUrl);
        }
        System.out.println("Schema Filter:    " + (schemaFilter.equals("ALL") ? "ALL SCHEMAS" : schemaFilter));
        if (!routineFilter.isEmpty()) {
            System.out.println("Routine Filter:   " + routineFilter);
        }
        System.out.println("Parallel Workers: " + threads + " threads (delay: " + delayMs + " ms)");
        System.out.println("Auto-Repair Mode: " + (autoRepair ? "ENABLED (Will export repaired catalog)" : "DISABLED"));
        System.out.println("Diagnostics Dir:  " + outputDir.getAbsolutePath());
        System.out.println("=========================================================================\n");

        ObjectMapper mapper = new ObjectMapper();
        List<RoutineDiagramTarget> targets = new ArrayList<>();

        try {
            JsonNode rootNode = mapper.readTree(catalogFile);
            List<JsonNode> routineNodes = new ArrayList<>();

            JsonNode researchedRoutines = rootNode.path("ResearchedRoutines");
            if (researchedRoutines.isArray()) {
                researchedRoutines.forEach(routineNodes::add);
            } else if (rootNode.isArray()) {
                rootNode.forEach(routineNodes::add);
            } else {
                rootNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isObject()) {
                        routineNodes.add(entry.getValue());
                    }
                });
            }

            for (JsonNode node : routineNodes) {
                String schema = extractField(node, "Schema", "schema").toUpperCase();
                String pkg = extractField(node, "Package", "package", "Pkg").toUpperCase();
                String routineName = extractField(node, "ObjectName", "objectName", "RoutineName", "routineName").toUpperCase();
                String lookupKey = extractField(node, "LookupKey", "lookupKey");
                if (lookupKey.isEmpty()) {
                    lookupKey = String.format("%s.%s.%s", schema, (pkg.isEmpty() ? "-" : pkg), routineName);
                }

                if (ExportMermaidDiagramsFromCatalog.isGhostOrKeywordRoutine(routineName)) {
                    continue;
                }

                if (!schemaFilter.equals("ALL") && !schema.equalsIgnoreCase(schemaFilter)) {
                    continue;
                }

                if (!routineFilter.isEmpty() && !routineName.equalsIgnoreCase(routineFilter) && !lookupKey.contains(routineFilter)) {
                    continue;
                }

                String rawMermaid = extractField(node, "MermaidDiagram", "mermaidDiagram", "Mermaid", "mermaid");
                if (rawMermaid.isEmpty() || rawMermaid.equalsIgnoreCase("N/A") || rawMermaid.equalsIgnoreCase("None")) {
                    continue;
                }

                targets.add(new RoutineDiagramTarget(lookupKey, schema, pkg, routineName, rawMermaid));
            }

            System.out.printf("Discovered %,d candidate Mermaid diagrams matching filter criteria.%n%n", targets.size());
            if (targets.isEmpty()) {
                System.out.println("No diagrams found to verify. Exiting.");
                return;
            }

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            List<Future<VerificationResult>> futures = new ArrayList<>();
            AtomicInteger completedCounter = new AtomicInteger(0);
            AtomicInteger passCounter = new AtomicInteger(0);
            AtomicInteger failCounter = new AtomicInteger(0);
            AtomicInteger throttledCounter = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            int total = targets.size();

            for (RoutineDiagramTarget target : targets) {
                futures.add(executor.submit(() -> {
                    if (delayMs > 0) {
                        try {
                            Thread.sleep((long) (Math.random() * delayMs));
                        } catch (InterruptedException ignored) {}
                    }
                    VerificationResult res = verifyDiagram(target);
                    int done = completedCounter.incrementAndGet();
                    if (res.isValid) {
                        passCounter.incrementAndGet();
                    } else {
                        failCounter.incrementAndGet();
                        if (res.httpStatusCode == 429) {
                            throttledCounter.incrementAndGet();
                        }
                    }
                    renderLiveProgress(done, total, passCounter.get(), failCounter.get(), throttledCounter.get(), target.lookupKey, startTime);
                    VerificationResult threadResult = res;
                    return threadResult;
                }));
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(60, TimeUnit.MINUTES);
            if (!finished) {
                executor.shutdownNow();
            }

            // Cleanly finish progress bar line
            renderLiveProgress(total, total, passCounter.get(), failCounter.get(), throttledCounter.get(), "Complete", startTime);
            System.out.println("\n");

            List<VerificationResult> allResults = new ArrayList<>();
            List<VerificationResult> failedResults = new ArrayList<>();
            int syntaxFailCount = 0;
            int rateLimitFailCount = 0;
            int networkFailCount = 0;

            for (Future<VerificationResult> f : futures) {
                try {
                    VerificationResult r = f.get();
                    allResults.add(r);
                    if (!r.isValid) {
                        failedResults.add(r);
                        if (r.httpStatusCode == 400) syntaxFailCount++;
                        else if (r.httpStatusCode == 429) rateLimitFailCount++;
                        else networkFailCount++;
                    }
                } catch (Exception e) {
                    System.err.println("Thread execution error: " + e.getMessage());
                }
            }

            // Analyze and categorize failures into Common Issue Archetypes
            List<CommonIssuePattern> issuePatterns = analyzeCommonIssues(failedResults);

            // Generate Diagnostic Reports (Markdown, JSON, individual broken .mmd files)
            generateDiagnosticReports(outputDir, allResults, failedResults, issuePatterns);

            // Print Executive Summary & Common Issues Report to Console
            printSummaryAndCommonIssuesToConsole(allResults, failedResults, syntaxFailCount, rateLimitFailCount, networkFailCount, issuePatterns, outputDir);

            if (autoRepair && !allResults.isEmpty()) {
                File parent = catalogFile.getParentFile();
                File repairedFile = (parent != null)
                        ? new File(parent, catalogFile.getName().replace(".json", ".repaired.json"))
                        : new File(catalogFile.getName().replace(".json", ".repaired.json"));
                System.out.println("\n[AUTO-REPAIR] Exporting repaired catalog with clean Mermaid diagrams to:");
                System.out.println("   " + repairedFile.getAbsolutePath());
                Map<String, String> repairMap = new HashMap<>();
                for (VerificationResult vr : allResults) {
                    if (vr.isValid && vr.cleanedMermaid != null && !vr.cleanedMermaid.isEmpty()) {
                        repairMap.put(vr.lookupKey, vr.cleanedMermaid);
                    }
                }
                exportRepairedCatalog(catalogFile, repairedFile, repairMap);
                System.out.println("[AUTO-REPAIR] Successfully generated repaired catalog JSON!\n");
            }
            System.out.println("=========================================================================\n");

        } catch (Exception ex) {
            System.err.println("CRITICAL: Verification run aborted: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static long lastRenderTime = 0;

    /**
     * Renders a real-time console progress bar displaying completion percentage, ETA, and live throughput.
     * Prevents console flicker while keeping operations teams fully informed without dumping error spam.
     */
    private static synchronized void renderLiveProgress(
            int done, int total, int pass, int fail, int throttled, String currentItem, long startTime) {
        long now = System.currentTimeMillis();
        if (done < total && (now - lastRenderTime < 45)) {
            return;
        }
        lastRenderTime = now;

        double pct = total > 0 ? (done * 100.0) / total : 100.0;
        long elapsed = Math.max(1, now - startTime);
        double itemsPerSec = (done * 1000.0) / elapsed;
        long remainingSec = (itemsPerSec > 0.001) ? (long) ((total - done) / itemsPerSec) : 0;

        int width = 16;
        int filled = (int) Math.round((pct / 100.0) * width);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "█" : "░");
        }

        String itemStr = (currentItem != null) ? truncate(currentItem, 26) : "";
        String status = String.format("\r  [%s] %5.1f%% (%d/%d) | Pass: %d | Fail: %d | Throttled: %d | ETA %02d:%02d | %-26s",
                bar, pct, done, total, pass, fail, throttled, (remainingSec / 60), (remainingSec % 60), itemStr);
        System.out.print(status);
        System.out.flush();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            String result = "";
            return result;
        }
        if (s.length() <= max) {
            String result = s;
            return result;
        }
        String result = s.substring(0, max - 3) + "...";
        return result;
    }

    /**
     * Executes test rendering of the given Mermaid diagram.
     * Applies the runtime cleanAndThemeMermaidSource pipeline with automatic fallback and retry.
     */
    public static VerificationResult verifyDiagram(RoutineDiagramTarget target) {
        String raw = target.rawMermaid;
        String processed = "";
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                processed = DocxDiagramHelper.cleanAndThemeMermaidSource(raw);
                byte[] bytes = MermaidWordDocRenderer.convertMermaidToImageBytes(processed);
                if (bytes != null && bytes.length > 0) {
                    VerificationResult pass = new VerificationResult(target.lookupKey, target.schema, target.pkg,
                            target.routineName, true, raw, processed, "OK", "", 200);
                    return pass;
                } else {
                    VerificationResult emptyBody = new VerificationResult(target.lookupKey, target.schema, target.pkg,
                            target.routineName, false, raw, processed, "Empty response body", "", 500);
                    return emptyBody;
                }
            } catch (IOException ioe) {
                String msg = ioe.getMessage() != null ? ioe.getMessage() : "";
                int statusCode = 400; // Default to syntax error
                if (msg.contains("HTTP 5") || msg.contains("503") || msg.contains("502") || msg.contains("504")) statusCode = 503;
                if (msg.contains("HTTP 429") || msg.contains("429")) statusCode = 429;

                // Transient remote service errors
                if ((statusCode == 503 || statusCode == 429) && attempt < maxAttempts) {
                    try {
                        long backoff = (long) (1500L * attempt + Math.random() * 500);
                        Thread.sleep(backoff);
                    } catch (InterruptedException ignored) {}
                    continue;
                }

                VerificationResult fail = new VerificationResult(target.lookupKey, target.schema, target.pkg,
                        target.routineName, false, raw, processed, msg, extractSnippet(msg), statusCode);
                return fail;
            } catch (Throwable t) {
                String tMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                VerificationResult err = new VerificationResult(target.lookupKey, target.schema, target.pkg,
                        target.routineName, false, raw, processed, t.getClass().getSimpleName() + ": " + tMsg, extractSnippet(tMsg), 500);
                return err;
            }
        }

        VerificationResult timeoutFail = new VerificationResult(target.lookupKey, target.schema, target.pkg,
                target.routineName, false, raw, processed, "Exhausted retries", "", 500);
        return timeoutFail;
    }

    private static String extractSnippet(String errorMsg) {
        if (errorMsg == null) {
            String result = "";
            return result;
        }
        int idx = errorMsg.indexOf("Parse error");
        if (idx != -1) {
            String sub = errorMsg.substring(idx);
            String result = sub.length() > 300 ? sub.substring(0, 300) : sub;
            return result;
        }
        int idx2 = errorMsg.indexOf("Syntax error");
        if (idx2 != -1) {
            String sub = errorMsg.substring(idx2);
            String result = sub.length() > 300 ? sub.substring(0, 300) : sub;
            return result;
        }
        String result = errorMsg.length() > 200 ? errorMsg.substring(0, 200) : errorMsg;
        return result;
    }

    private static void generateDiagnosticReports(
            File outputDir, List<VerificationResult> all, List<VerificationResult> failed,
            List<CommonIssuePattern> issuePatterns) throws IOException {
        File failedDir = new File(outputDir, "failed");
        if (!failedDir.exists()) {
            failedDir.mkdirs();
        }

        // 1. Write Markdown Summary Report with Executive Summary & Common Issue Patterns
        File mdReport = new File(outputDir, "MERMAID_ERRORS_REPORT.md");
        try (FileWriter fw = new FileWriter(mdReport, StandardCharsets.UTF_8)) {
            fw.write("# Florida Lottery Mermaid Diagram Verification & Common Issues Audit Report\n\n");
            fw.write(String.format("- **Audit Timestamp:** %s\n", Instant.now().toString()));
            fw.write(String.format("- **Total Diagrams Audited:** %,d\n", all.size()));
            fw.write(String.format("- **Pass Count:** %,d (%.1f%%)\n", (all.size() - failed.size()),
                    all.isEmpty() ? 0.0 : ((all.size() - failed.size()) * 100.0 / all.size())));
            fw.write(String.format("- **Fail Count:** %,d (%.1f%%)\n\n", failed.size(),
                    all.isEmpty() ? 0.0 : (failed.size() * 100.0 / all.size())));

            if (failed.isEmpty()) {
                fw.write("## Result: All diagrams passed syntax validation successfully!\n\n");
            } else {
                fw.write("## Common Issue Patterns Identified (Sorted by Frequency)\n\n");
                fw.write("The following common syntax and structural failure archetypes were detected across the catalog:\n\n");

                int rank = 1;
                for (CommonIssuePattern p : issuePatterns) {
                    Set<String> schemas = p.getAffectedSchemas();
                    fw.write(String.format("### [%d] %s (%,d occurrences)\n\n", rank++, p.title, p.occurrences.size()));
                    fw.write(String.format("- **Impact:** %,d routine%s across %d schema%s (`%s`)\n",
                            p.occurrences.size(), (p.occurrences.size() == 1 ? "" : "s"),
                            schemas.size(), (schemas.size() == 1 ? "" : "s"), String.join(", ", schemas)));
                    fw.write(String.format("- **Root Cause:** %s\n", p.rootCause));
                    fw.write(String.format("- **Recommended Fix:** %s\n\n", p.recommendation));

                    fw.write("#### Sample Affected Routines:\n");
                    int sampleLimit = Math.min(6, p.occurrences.size());
                    for (int i = 0; i < sampleLimit; i++) {
                        VerificationResult s = p.occurrences.get(i);
                        fw.write(String.format("- `%s`\n", s.lookupKey));
                    }
                    if (p.occurrences.size() > sampleLimit) {
                        fw.write(String.format("- *... and %,d more routines*\n", (p.occurrences.size() - sampleLimit)));
                    }

                    VerificationResult first = p.occurrences.get(0);
                    if (first.errorSnippet != null && !first.errorSnippet.trim().isEmpty()) {
                        fw.write("\n#### Sample Error Trace:\n```\n" + first.errorSnippet.trim() + "\n```\n");
                    }
                    fw.write("\n---\n\n");
                }

                fw.write("## Failed Diagrams Inventory\n\n");
                fw.write("| Schema | Package | Routine | Status | Error Summary |\n");
                fw.write("|---|---|---|---|---|\n");
                for (VerificationResult r : failed) {
                    String cleanErr = r.errorMessage.replace("|", "\\|").replace("\n", " ");
                    if (cleanErr.length() > 90) cleanErr = cleanErr.substring(0, 90) + "...";
                    fw.write(String.format("| %s | %s | %s | %d | %s |\n",
                            r.schema, r.pkg.isEmpty() ? "-" : r.pkg, r.routineName, r.httpStatusCode, cleanErr));
                }

                fw.write("\n\n---\n\n## Detailed Error Snippets and Source Code\n\n");
                for (VerificationResult r : failed) {
                    fw.write(String.format("### %s\n\n", r.lookupKey));
                    fw.write(String.format("**Error Message:** `%s`\n\n", r.errorMessage));
                    if (!r.errorSnippet.isEmpty()) {
                        fw.write("```\n" + r.errorSnippet + "\n```\n\n");
                    }
                    fw.write("#### Processed / Attempted Mermaid Source:\n\n");
                    fw.write("```mermaid\n" + r.cleanedMermaid + "\n```\n\n");
                    fw.write("#### Original Raw Catalog Source:\n\n");
                    fw.write("```mermaid\n" + r.rawMermaid + "\n```\n\n");
                    fw.write("---\n\n");
                }
            }
        }

        // 2. Dump individual failed .mmd files for instant testing in mermaid.live
        for (VerificationResult r : failed) {
            String baseName = String.format("%s.%s.%s", r.schema, (r.pkg.isEmpty() ? "STANDALONE" : r.pkg), r.routineName);
            File rawFile = new File(failedDir, baseName + ".raw.mmd");
            try (FileWriter fw = new FileWriter(rawFile, StandardCharsets.UTF_8)) {
                fw.write(r.rawMermaid + "\n");
            }

            File processedFile = new File(failedDir, baseName + ".processed.mmd");
            try (FileWriter fw = new FileWriter(processedFile, StandardCharsets.UTF_8)) {
                fw.write(r.cleanedMermaid + "\n");
            }

            File errInfoFile = new File(failedDir, baseName + ".error.txt");
            try (FileWriter fw = new FileWriter(errInfoFile, StandardCharsets.UTF_8)) {
                fw.write("LookupKey: " + r.lookupKey + "\n");
                fw.write("Status Code: " + r.httpStatusCode + "\n");
                fw.write("Error Message:\n" + r.errorMessage + "\n\n");
                fw.write("Error Snippet:\n" + r.errorSnippet + "\n");
            }
        }

        // 3. Write structured JSON report of all results
        File jsonReport = new File(outputDir, "MERMAID_VERIFICATION_REPORT.json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonReport, all);

        // 4. Write Common Issues JSON Report for easy automated ingestion
        File issuesJsonReport = new File(outputDir, "MERMAID_COMMON_ISSUES.json");
        List<Map<String, Object>> patternSummaryList = new ArrayList<>();
        for (CommonIssuePattern p : issuePatterns) {
            Map<String, Object> pMap = new LinkedHashMap<>();
            pMap.put("id", p.id);
            pMap.put("title", p.title);
            pMap.put("count", p.occurrences.size());
            pMap.put("affectedSchemas", new ArrayList<>(p.getAffectedSchemas()));
            pMap.put("rootCause", p.rootCause);
            pMap.put("recommendation", p.recommendation);
            List<String> sampleKeys = new ArrayList<>();
            for (int i = 0; i < Math.min(10, p.occurrences.size()); i++) {
                sampleKeys.add(p.occurrences.get(i).lookupKey);
            }
            pMap.put("sampleRoutines", sampleKeys);
            patternSummaryList.add(pMap);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(issuesJsonReport, patternSummaryList);
    }

    public static class CommonIssuePattern {
        public final String id;
        public final String title;
        public final String rootCause;
        public final String recommendation;
        public final List<VerificationResult> occurrences = new ArrayList<>();

        public CommonIssuePattern(String id, String title, String rootCause, String recommendation) {
            this.id = id;
            this.title = title;
            this.rootCause = rootCause;
            this.recommendation = recommendation;
        }

        public Set<String> getAffectedSchemas() {
            Set<String> schemas = new TreeSet<>();
            for (VerificationResult r : occurrences) {
                if (r.schema != null && !r.schema.isEmpty()) {
                    schemas.add(r.schema);
                }
            }
            Set<String> result = schemas;
            return result;
        }
    }

    public static List<CommonIssuePattern> analyzeCommonIssues(List<VerificationResult> failedList) {
        Map<String, CommonIssuePattern> patterns = new LinkedHashMap<>();

        patterns.put("SUBGRAPH_SCOPE", new CommonIssuePattern(
                "SUBGRAPH_SCOPE",
                "Class Statement Placed Inside Unclosed Subgraph Container",
                "Flowchart class directives (e.g. 'class ... process;') were emitted inside an unclosed subgraph container. Mermaid syntax strictly requires class directives to reside at root scope.",
                "Auto-close open subgraphs with 'end' before emitting classDef palettes and class bindings."
        ));

        patterns.put("PREMATURE_QUOTE_EXT", new CommonIssuePattern(
                "PREMATURE_QUOTE_EXT",
                "Premature Closing Quote Before File Extension Suffix",
                "Node label contains quotes prematurely terminated before an extension suffix (e.g., build[\"Content_Types\"].xml\"]).",
                "Recombine the text and extension suffix into a unified quoted label: build[\"Content_Types.xml\"]."
        ));

        patterns.put("NESTED_BRACKETS", new CommonIssuePattern(
                "NESTED_BRACKETS",
                "Unescaped Nested Square Brackets in Node Label",
                "Label contains internal square brackets (e.g., meta[\"Build [\"Content_Types\"].xml\"] or node[Build [Types].xml]) that prematurely close the node shape.",
                "Transform internal square brackets into rounded parentheses: meta[\"Build (Content_Types).xml\"]."
        ));

        patterns.put("NON_STANDARD_END", new CommonIssuePattern(
                "NON_STANDARD_END",
                "Non-Standard Subgraph Closing Delimiter",
                "AI generated custom end delimiters such as 'end Stage 2', 'end [Stage 1]', 'end;', or 'end // ...' which are rejected by the Mermaid flowchart lexer.",
                "Normalize all end statement variants into standard single-word 'end' keywords."
        ));

        patterns.put("RESERVED_KEYWORD", new CommonIssuePattern(
                "RESERVED_KEYWORD",
                "Mermaid Reserved Keyword Used as Node Identifier",
                "Node declared with reserved keyword ID ('end', 'subgraph', 'click', 'style', 'class', 'flowchart') without an alias, colliding with grammar rules.",
                "Append or prefix node identifier (e.g., 'endNode', 'subgraphNode')."
        ));

        patterns.put("UNQUOTED_SPECIAL_CHARS", new CommonIssuePattern(
                "UNQUOTED_SPECIAL_CHARS",
                "Unquoted Special Characters or Math Operators in Label",
                "Labels containing unquoted colons (:), semicolons (;), math operators (+, -, *, /), or SQL characters without enclosing double quotes.",
                "Safely wrap labels in double quotes and escape internal quotation marks."
        ));

        patterns.put("MALFORMED_EDGES", new CommonIssuePattern(
                "MALFORMED_EDGES",
                "Malformed Edge Transition or Condition Pipe",
                "Arrows with illegal pipes (e.g. -->|label|--> or -->-->) or mismatched connection tokens.",
                "Clean edge syntax to standard -->|condition| target structure."
        ));

        patterns.put("ILLEGAL_HTML_TAGS", new CommonIssuePattern(
                "ILLEGAL_HTML_TAGS",
                "Illegal or Malformed HTML Tags Inside Node Label",
                "Node label contains unescaped HTML tags (e.g., <br>, <font>, <div>) that fail in strict parser modes.",
                "Normalize <br> tags or replace with newline escape sequences."
        ));

        patterns.put("HTTP_429_THROTTLED", new CommonIssuePattern(
                "HTTP_429_THROTTLED",
                "Remote Rendering Endpoint Rate-Limited (HTTP 429)",
                "Public mermaid.ink service throttled requests due to excessive parallel concurrency.",
                "Switch to Playwright offline engine (-engine=playwright) or increase delayMs."
        ));

        patterns.put("HTTP_5XX_GATEWAY", new CommonIssuePattern(
                "HTTP_5XX_GATEWAY",
                "Remote Rendering Endpoint Timeout or Server Drop (HTTP 5xx)",
                "Remote mermaid service experienced transient downtime, network interruption, or gateway timeout.",
                "Switch to Playwright offline engine (-engine=playwright) to eliminate network dependencies completely."
        ));

        patterns.put("OTHER_SYNTAX", new CommonIssuePattern(
                "OTHER_SYNTAX",
                "Other Unclassified Mermaid Syntax Parse Errors",
                "Syntax rejected by Mermaid compiler with parser error token not covered by standard archetypes.",
                "Inspect the reported raw diagram lines and error caret indicator."
        ));

        for (VerificationResult r : failedList) {
            String err = (r.errorMessage != null) ? r.errorMessage : "";
            String snip = (r.errorSnippet != null) ? r.errorSnippet : "";
            String raw = (r.rawMermaid != null) ? r.rawMermaid : "";

            if (r.httpStatusCode == 429) {
                patterns.get("HTTP_429_THROTTLED").occurrences.add(r);
            } else if (r.httpStatusCode >= 500 && (err.contains("HTTP 5") || err.contains("503") || err.contains("502") || err.contains("504"))) {
                patterns.get("HTTP_5XX_GATEWAY").occurrences.add(r);
            } else if ((err.contains("Parse error") && (err.contains("process;") || err.contains("startEnd;") || err.contains("decision;") || err.contains("db;") || err.contains("error;")))
                    || (snip.contains("process;") && snip.contains("Expecting 'SEMI'"))) {
                patterns.get("SUBGRAPH_SCOPE").occurrences.add(r);
            } else if (raw.matches("(?s).*\\b\\w+\\s*\\[\\s*\"[^\"]*?\"\\][^\\r\\n\\[\\]]*?\"?\\s*\\].*") || err.contains(".xml\"]") || err.contains(".csv\"]") || err.contains(".txt\"]") || snip.contains(".xml\"]")) {
                patterns.get("PREMATURE_QUOTE_EXT").occurrences.add(r);
            } else if (raw.matches("(?s).*\\b\\w+\\s*\\[.*?\\[.*\\].*\\]?.*") || snip.contains("Build [") || err.contains("got 'BRKT'")) {
                patterns.get("NESTED_BRACKETS").occurrences.add(r);
            } else if (raw.matches("(?is).*\\bend\\s+(stage|subgraph|step|[0-9a-z_]+).*") || raw.matches("(?is).*\\bend\\s*(\\[|//).*")) {
                patterns.get("NON_STANDARD_END").occurrences.add(r);
            } else if (err.contains("got 'end'") || err.contains("got 'subgraph'") || err.contains("got 'STYLE'") || err.contains("got 'CLASS'") || raw.matches("(?is).*\\b(end|subgraph|click|style|class)\\s*[\\[\\(\\{].*")) {
                patterns.get("RESERVED_KEYWORD").occurrences.add(r);
            } else if (err.contains("Expecting 'START_LINK'") || err.contains("Expecting 'LINK'") || raw.contains("-->|-->") || raw.contains("-->-->")) {
                patterns.get("MALFORMED_EDGES").occurrences.add(r);
            } else if (err.contains("got 'TAG_START'") || raw.contains("<br>") || raw.contains("</")) {
                patterns.get("ILLEGAL_HTML_TAGS").occurrences.add(r);
            } else if (err.contains("Expecting 'SEMI'") || err.contains("got 'AMP'") || err.contains("got 'MINUS'") || err.contains("got 'COLON'") || err.contains("got 'MULT'")) {
                patterns.get("UNQUOTED_SPECIAL_CHARS").occurrences.add(r);
            } else {
                patterns.get("OTHER_SYNTAX").occurrences.add(r);
            }
        }

        List<CommonIssuePattern> result = new ArrayList<>();
        for (CommonIssuePattern p : patterns.values()) {
            if (!p.occurrences.isEmpty()) {
                result.add(p);
            }
        }
        result.sort((a, b) -> Integer.compare(b.occurrences.size(), a.occurrences.size()));
        return result;
    }

    private static void printSummaryAndCommonIssuesToConsole(
            List<VerificationResult> all, List<VerificationResult> failed,
            int syntaxFailCount, int rateLimitFailCount, int networkFailCount,
            List<CommonIssuePattern> issuePatterns, File outputDir) {

        System.out.println("=========================================================================");
        System.out.println("FLORIDA LOTTERY - MERMAID DIAGRAM VERIFICATION & AUDIT SUMMARY");
        System.out.println("=========================================================================");
        System.out.printf(" Total Diagrams Audited:   %,d%n", all.size());
        System.out.printf(" Verified Clean / Pass:    %,d (%.1f%%)%n", (all.size() - failed.size()),
                all.isEmpty() ? 0.0 : ((all.size() - failed.size()) * 100.0 / all.size()));
        System.out.printf(" Failed Diagrams:          %,d (%.1f%%)%n", failed.size(),
                all.isEmpty() ? 0.0 : (failed.size() * 100.0 / all.size()));
        System.out.printf("   ├─ Syntax Parse Errors: %,d (Parser Failure)%n", syntaxFailCount);
        if (rateLimitFailCount > 0) {
            System.out.printf("   ├─ Throttled / 429:     %,d (Rate Limited)%n", rateLimitFailCount);
        }
        if (networkFailCount > 0) {
            System.out.printf("   └─ Network Drops / 5xx: %,d (Server Error)%n", networkFailCount);
        }
        System.out.println("=========================================================================\n");

        if (failed.isEmpty()) {
            System.out.println("STATUS: PERFECT! All Mermaid diagrams passed verification cleanly.\n");
            return;
        }

        System.out.println("-------------------------------------------------------------------------");
        System.out.printf("COMMON ISSUE PATTERNS IDENTIFIED (%d Distinct Pattern%s)%n",
                issuePatterns.size(), (issuePatterns.size() == 1 ? "" : "s"));
        System.out.println("-------------------------------------------------------------------------");

        int rank = 1;
        for (CommonIssuePattern pattern : issuePatterns) {
            Set<String> schemas = pattern.getAffectedSchemas();
            System.out.printf("%n[%d] %s%n", rank++, pattern.title);
            System.out.printf("    • Frequency:      %,d diagram%s across %d schema%s (%s)%n",
                    pattern.occurrences.size(), (pattern.occurrences.size() == 1 ? "" : "s"),
                    schemas.size(), (schemas.size() == 1 ? "" : "s"), String.join(", ", schemas));
            System.out.printf("    • Root Cause:     %s%n", pattern.rootCause);
            System.out.printf("    • Recommendation: %s%n", pattern.recommendation);

            System.out.println("    • Sample Affected Routines:");
            int showLimit = Math.min(5, pattern.occurrences.size());
            for (int i = 0; i < showLimit; i++) {
                VerificationResult sample = pattern.occurrences.get(i);
                System.out.printf("        - %s%n", sample.lookupKey);
            }
            if (pattern.occurrences.size() > showLimit) {
                System.out.printf("        ... and %,d more%n", (pattern.occurrences.size() - showLimit));
            }

            // Print representative error snippet if available
            VerificationResult first = pattern.occurrences.get(0);
            if (first.errorSnippet != null && !first.errorSnippet.trim().isEmpty()) {
                System.out.println("    • Sample Error:");
                String[] snippetLines = first.errorSnippet.split("\\R");
                for (int s = 0; s < Math.min(4, snippetLines.length); s++) {
                    System.out.printf("        | %s%n", snippetLines[s]);
                }
            }
        }

        System.out.println("\n-------------------------------------------------------------------------");
        System.out.println("DIAGNOSTIC OUTPUT FILES WRITTEN:");
        System.out.printf("  • Full Markdown Report: %s%n", new File(outputDir, "MERMAID_ERRORS_REPORT.md").getAbsolutePath());
        System.out.printf("  • Common Issues JSON:   %s%n", new File(outputDir, "MERMAID_COMMON_ISSUES.json").getAbsolutePath());
        System.out.printf("  • Raw Audit JSON:       %s%n", new File(outputDir, "MERMAID_VERIFICATION_REPORT.json").getAbsolutePath());
        System.out.printf("  • Individual .mmd Files:%s%n", new File(outputDir, "failed").getAbsolutePath());
        System.out.println("-------------------------------------------------------------------------\n");
    }

    private static void exportRepairedCatalog(File sourceFile, File targetFile, Map<String, String> repairMap) {
        if (repairMap == null || repairMap.isEmpty()) return;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(sourceFile);
            int updated = 0;

            JsonNode routines = root.path("ResearchedRoutines");
            if (routines.isArray()) {
                for (JsonNode item : routines) {
                    if (item.isObject()) {
                        String lk = extractField(item, "LookupKey", "lookupKey");
                        if (lk.isEmpty()) {
                            String s = extractField(item, "Schema", "schema").toUpperCase();
                            String p = extractField(item, "Package", "package").toUpperCase();
                            String r = extractField(item, "ObjectName", "objectName").toUpperCase();
                            lk = String.format("%s.%s.%s", s, p.isEmpty() ? "-" : p, r);
                        }
                        if (repairMap.containsKey(lk)) {
                            ((ObjectNode) item).put("MermaidDiagram", repairMap.get(lk));
                            updated++;
                        }
                    }
                }
            } else if (root.isArray()) {
                for (JsonNode item : root) {
                    if (item.isObject()) {
                        String lk = extractField(item, "LookupKey", "lookupKey");
                        if (repairMap.containsKey(lk)) {
                            ((ObjectNode) item).put("MermaidDiagram", repairMap.get(lk));
                            updated++;
                        }
                    }
                }
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, root);
            System.out.printf("[AUTO-REPAIR] Updated %,d routine diagrams in: %s%n", updated, targetFile.getName());
        } catch (Exception ex) {
            System.err.println("WARN: Failed to export repaired catalog: " + ex.getMessage());
        }
    }

    private static String extractField(JsonNode node, String... fieldNames) {
        if (node == null) {
            String result = "";
            return result;
        }
        for (String name : fieldNames) {
            JsonNode v = node.path(name);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isEmpty()) {
                String result = v.asText().trim();
                return result;
            }
        }
        String result = "";
        return result;
    }

    private static Map<String, String> parseCommandLineArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) {
            Map<String, String> result = map;
            return result;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith("-")) {
                String clean = arg.substring(1);
                int eq = clean.indexOf('=');
                if (eq > 0) {
                    map.put(clean.substring(0, eq).trim(), clean.substring(eq + 1).trim());
                } else {
                    map.put(clean.trim(), "true");
                }
            }
        }
        Map<String, String> result = map;
        return result;
    }

    public static class RoutineDiagramTarget {
        public final String lookupKey;
        public final String schema;
        public final String pkg;
        public final String routineName;
        public final String rawMermaid;

        public RoutineDiagramTarget(String lookupKey, String schema, String pkg, String routineName, String rawMermaid) {
            this.lookupKey = lookupKey;
            this.schema = schema;
            this.pkg = pkg;
            this.routineName = routineName;
            this.rawMermaid = rawMermaid;
        }
    }
}
