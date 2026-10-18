package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Dedicated Utility to extract Mermaid Flowchart Diagrams from the AI Researched Catalog JSON
 * into standalone .mmd / .md files and an interactive live HTML preview workbench.
 *
 * Usage:
 *   java com.fllottery.util.ExportMermaidDiagramsFromCatalog [options]
 *
 * Options:
 *   -catalog=C:\ocioldapps1\Cached\DbCache\Schemas\DbCache_Ai_Researched_Catalog.json
 *   -outputDir=C:\ocioldapps1\Cached\DbCache\Schemas\Mermaid_Dumps
 *   -schema=ARS (optional: filter by schema, e.g., ARS, INET, or ALL)
 *   -format=mmd (mmd, mermaid, md, or txt)
 *   -generateHtml=true (generates a self-contained interactive browser preview index.html)
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @since 2026-10-05
 */
public class ExportMermaidDiagramsFromCatalog {

    private static final String DEFAULT_CATALOG_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\DbCache_Ai_Researched_Catalog.json";
    private static final String DEFAULT_FALLBACK_CATALOG = "DbCache_Ai_Researched_Catalog.json";
    private static final String DEFAULT_OUTPUT_DIR = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\Mermaid_Dumps";

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
                System.err.println("ACTION REQUIRED: Researched Catalog JSON not found at: " + catalogPath);
                System.err.println("Please provide the path via: -catalog=C:\\path\\to\\DbCache_Ai_Researched_Catalog.json");
                return;
            }
        }

        String outputDirPath = argMap.getOrDefault("outputDir", argMap.getOrDefault("out", DEFAULT_OUTPUT_DIR));
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created && !outputDir.exists()) {
                outputDir = new File("./Mermaid_Dumps");
                outputDir.mkdirs();
            }
        }

        String schemaFilter = argMap.getOrDefault("schema", argMap.getOrDefault("schemaName", "ALL")).trim().toUpperCase();
        String extension = argMap.getOrDefault("format", argMap.getOrDefault("ext", "mmd")).replace(".", "").trim().toLowerCase();
        if (extension.isEmpty()) {
            extension = "mmd";
        }
        boolean generateHtml = Boolean.parseBoolean(argMap.getOrDefault("generateHtml", argMap.getOrDefault("html", "true")));

        System.out.println("=========================================================================");
        System.out.println("Florida Lottery - Mermaid Diagram Exporter & Workbench Generator");
        System.out.println("Catalog Source: " + catalogFile.getAbsolutePath() + " (" + (catalogFile.length() / 1024) + " KB)");
        System.out.println("Dump Directory: " + outputDir.getAbsolutePath());
        System.out.println("Schema Filter:  " + (schemaFilter.equals("ALL") ? "ALL SCHEMAS" : schemaFilter));
        System.out.println("File Extension: ." + extension);
        System.out.println("HTML Workbench: " + (generateHtml ? "Enabled (Live Browser Visualizer)" : "Disabled"));
        System.out.println("=========================================================================");

        ObjectMapper mapper = new ObjectMapper();
        int exportedCount = 0;
        int skippedCount = 0;

        List<DiagramRecord> extractedDiagrams = new ArrayList<>();
        Map<String, List<DiagramRecord>> diagramsBySchema = new TreeMap<>();

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

            System.out.printf("Discovered %d total routine records in catalog JSON.%n%n", routineNodes.size());

            for (JsonNode node : routineNodes) {
                String schema = extractField(node, "Schema", "schema", "schemaName").toUpperCase();
                String pkg = extractField(node, "Package", "package", "packageName", "Pkg").toUpperCase();
                String routineName = extractField(node, "ObjectName", "objectName", "RoutineName", "routineName", "Name", "name").toUpperCase();
                String lookupKey = extractField(node, "LookupKey", "lookupKey");
                if (lookupKey.isEmpty()) {
                    lookupKey = String.format("%s.%s.%s", schema, (pkg.isEmpty() ? "-" : pkg), routineName);
                }

                if (isGhostOrKeywordRoutine(routineName)) {
                    skippedCount++;
                    continue;
                }

                if (!schemaFilter.equals("ALL") && !schema.equalsIgnoreCase(schemaFilter)) {
                    continue;
                }

                String rawMermaid = extractField(node, "MermaidDiagram", "mermaidDiagram", "Mermaid", "mermaid");
                if (rawMermaid.isEmpty() || rawMermaid.equalsIgnoreCase("N/A") || rawMermaid.equalsIgnoreCase("None")) {
                    skippedCount++;
                    continue;
                }

                String cleanMermaid = cleanMermaidSyntax(rawMermaid);
                if (cleanMermaid.isEmpty()) {
                    skippedCount++;
                    continue;
                }

                String whatDesc = "";
                JsonNode descNode = node.path("Description");
                if (descNode.isObject()) {
                    whatDesc = descNode.path("What").asText(descNode.path("what").asText(""));
                } else {
                    whatDesc = node.path("What").asText(node.path("what").asText(""));
                }

                DiagramRecord record = new DiagramRecord(lookupKey, schema, pkg, routineName, whatDesc, cleanMermaid);
                extractedDiagrams.add(record);
                diagramsBySchema.computeIfAbsent(schema, k -> new ArrayList<>()).add(record);

                // 1. Write individual .mmd file organized by Schema subdirectories
                File schemaSubDir = new File(outputDir, schema);
                if (!schemaSubDir.exists()) {
                    schemaSubDir.mkdirs();
                }

                String fileName = String.format("%s.%s.%s.%s",
                        sanitizeFileName(schema),
                        sanitizeFileName(pkg.isEmpty() ? "-" : pkg),
                        sanitizeFileName(routineName),
                        extension);

                File outFile = new File(schemaSubDir, fileName);
                try (FileWriter writer = new FileWriter(outFile, StandardCharsets.UTF_8)) {
                    writer.write(cleanMermaid);
                    writer.write("\n");
                }

                // Also write a convenient flat copy directly in the dump folder root
                File flatOutFile = new File(outputDir, fileName);
                try (FileWriter flatWriter = new FileWriter(flatOutFile, StandardCharsets.UTF_8)) {
                    flatWriter.write(cleanMermaid);
                    flatWriter.write("\n");
                }

                exportedCount++;
                System.out.printf("   [Exported] %-55s -> %s%n", lookupKey, outFile.getName());
            }

            // 2. Generate Combined Markdown Master File for easy copy-pasting
            File bundleMdFile = new File(outputDir, "ALL_MERMAID_DIAGRAMS.md");
            generateCombinedMarkdownFile(bundleMdFile, extractedDiagrams);
            System.out.printf("%n[Master Markdown Bundle]: %s%n", bundleMdFile.getAbsolutePath());

            // 3. Generate Standalone HTML Live Interactive Visualizer Workbench
            if (generateHtml) {
                File htmlFile = new File(outputDir, "index.html");
                generateInteractiveHtmlWorkbench(htmlFile, diagramsBySchema, extractedDiagrams.size());
                System.out.printf("[Live HTML Workbench]:   %s%n", htmlFile.getAbsolutePath());
            }

            System.out.println("=========================================================================");
            System.out.println("SUCCESS: Mermaid Export Complete!");
            System.out.printf("Total Standalone Files: %,d diagrams written to %s%n", exportedCount, outputDir.getAbsolutePath());
            if (skippedCount > 0) {
                System.out.printf("Skipped:                %,d routines (filtered SQL keywords or missing diagrams)%n", skippedCount);
            }
            System.out.println("=========================================================================");
            System.out.println("HOW TO VIEW YOUR DIAGRAMS:");
            System.out.println("1. Double-click 'index.html' in your browser to view, search, and zoom all diagrams live.");
            System.out.println("2. Or open any individual .mmd file and paste into https://mermaid.live or Mermaid Chart.");
            System.out.println("=========================================================================");

        } catch (Exception ex) {
            System.err.println("CRITICAL: Failed to export Mermaid diagrams: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static final Set<String> SQL_KEYWORDS_AND_GHOST_NAMES = new HashSet<>(Arrays.asList(
            "WOULD", "TO", "IS", "AND", "ON", "OR", "AS", "BEGIN", "END", "IF", "ELSE", "THEN", "LOOP",
            "WHEN", "FOR", "WHILE", "CURSOR", "RETURN", "NULL", "NOT", "IN", "TABLE", "VIEW", "TYPE",
            "BODY", "PACKAGE", "PROCEDURE", "FUNCTION", "DECLARE", "CASE", "WITH", "INTO", "FROM", "WHERE",
            "SET", "UPDATE", "INSERT", "DELETE", "SELECT", "DROP", "ALTER", "CREATE", "REPLACE", "GRANT",
            "REVOKE", "COMMIT", "ROLLBACK", "SAVEPOINT", "EXCEPTION", "PRAGMA", "AUTHID", "DETERMINISTIC",
            "PARALLEL_ENABLE", "RESULT_CACHE", "EXECUTE", "IMMEDIATE"
    ));

    public static boolean isGhostOrKeywordRoutine(String routineName) {
        if (routineName == null || routineName.trim().isEmpty()) return true;
        String clean = routineName.trim().toUpperCase();
        if (SQL_KEYWORDS_AND_GHOST_NAMES.contains(clean)) {
            return true;
        }
        return false;
    }

    public static String cleanMermaidSyntax(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();

        // 1. Strip markdown code fences if present
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\R?", "").replaceAll("\\R?```$", "").trim();
        }

        // 2. Fix literal escaped newlines and quotes from JSON serialization
        if (cleaned.contains("\\n") && !cleaned.contains("\n")) {
            cleaned = cleaned.replace("\\n", "\n").replace("\\t", "    ").replace("\\\"", "\"");
        }

        // 3. Ensure proper flowchart header
        if (!cleaned.startsWith("flowchart") && !cleaned.startsWith("graph")) {
            cleaned = "flowchart TD\n" + cleaned;
        }

        // 4. Line-by-line syntax verification and auto-repair
        String[] lines = cleaned.split("\\R");
        StringBuilder sb = new StringBuilder();
        Set<String> declaredNodeIds = new HashSet<>();
        Set<String> declaredSubgraphIds = new HashSet<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n");
                continue;
            }

            // A. Auto-repair unclosed subgraph headers: e.g. subgraph Stage2 ["Stage 2: ... "
            if (trimmed.startsWith("subgraph") && trimmed.contains("[\"") && !trimmed.endsWith("]")) {
                if (!trimmed.endsWith("\"")) {
                    trimmed = trimmed + "\"]";
                } else {
                    trimmed = trimmed + "]";
                }
            }

            // Track subgraph IDs
            if (trimmed.startsWith("subgraph")) {
                String afterSub = trimmed.substring(8).trim();
                int bracketIdx = afterSub.indexOf("[");
                int spaceIdx = afterSub.indexOf(" ");
                int endId = (bracketIdx > 0) ? bracketIdx : (spaceIdx > 0 ? spaceIdx : afterSub.length());
                String sId = afterSub.substring(0, endId).trim();
                if (!sId.isEmpty()) {
                    declaredSubgraphIds.add(sId);
                }
            }

            // B. Auto-repair unclosed terminal pills: e.g. (["End: ... "] or (["End: ... "]
            if (trimmed.contains("([\"") && trimmed.endsWith("\"]") && !trimmed.endsWith("\"])")) {
                trimmed = trimmed + ")";
            } else if (trimmed.contains("([\"") && trimmed.endsWith("\"]") && !trimmed.endsWith("\"])")) {
                trimmed = trimmed + ")";
            } else if (trimmed.contains("([") && !trimmed.contains("])")) {
                if (trimmed.endsWith("]")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1) + "])";
                } else if (trimmed.endsWith("\"")) {
                    trimmed = trimmed + "])";
                } else {
                    trimmed = trimmed + "])";
                }
            }

            // C. Fix undeclared subgraph typo prefix: e.g. subgraphStage5 --> finishNode
            if (trimmed.startsWith("subgraphStage") && trimmed.contains("-->")) {
                trimmed = trimmed.replace("subgraphStage", "Stage");
            }

            // D. Sanitize conditional edges directly attached to a subgraph container
            // e.g. Stage1 -->|No| subLoad -> Stage1 --> subLoad
            for (String sId : declaredSubgraphIds) {
                if (trimmed.startsWith(sId + " -->|") || trimmed.startsWith(sId + "-->|")) {
                    int pipeEnd = trimmed.indexOf("|", trimmed.indexOf("|") + 1);
                    if (pipeEnd > 0) {
                        String afterPipe = trimmed.substring(pipeEnd + 1).trim();
                        trimmed = sId + " --> " + afterPipe;
                    }
                }
            }

            // E. Auto-repair missing quote inside shape brackets
            if (trimmed.matches(".*\\w+\\[[^\"]+\\].*") && !trimmed.contains("-->") && !trimmed.startsWith("subgraph")) {
                // If it contains a bracket without quotes, wrap inner text in quotes
                trimmed = trimmed.replaceAll("(\\w+)\\[([^\"]+)\\]", "$1[\"$2\"]");
            }

            // F. Auto-repair mismatched syntax brackets / trailing parenthesis on square bracket nodes
            // e.g. openCursor["Open cursor on ARS.ARTACDE_DB2"]) -> openCursor["Open cursor on ARS.ARTACDE_DB2"]
            if (trimmed.matches(".*\\b\\w+\\s*\\[[^\\]]+\\]\\).*")) {
                trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\[[^\\]]+\\])\\)", "$1");
            }
            if (trimmed.matches(".*\\b\\w+\\s*\\[\"[^\"]+\"\\).*") && !trimmed.contains("-->")) {
                trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\[\"[^\"]+)\"\\)", "$1\"]");
            }

            sb.append("    ").append(trimmed).append("\n");
        }

        String result = sb.toString().trim();
        return result;
    }

    private static void generateCombinedMarkdownFile(File targetFile, List<DiagramRecord> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Florida Lottery - Enterprise Database Stored Procedure Mermaid Diagrams\n\n");
        sb.append("> Generated: ").append(Instant.now().toString()).append(" | Total Diagrams: ").append(records.size()).append("\n\n");
        sb.append("---\n\n");

        for (DiagramRecord r : records) {
            sb.append("## ").append(r.lookupKey).append("\n\n");
            if (!r.whatDescription.isEmpty()) {
                sb.append("**Overview:** ").append(r.whatDescription).append("\n\n");
            }
            sb.append("```mermaid\n");
            sb.append(r.mermaidCode).append("\n");
            sb.append("```\n\n");
            sb.append("---\n\n");
        }

        Files.writeString(targetFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void generateInteractiveHtmlWorkbench(File targetFile, Map<String, List<DiagramRecord>> schemaMap, int totalCount) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Florida Lottery - Mermaid Diagram Workbench</title>
                <!-- Include Mermaid.js via CDN for instant in-browser rendering -->
                <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
                <style>
                    :root {
                        --primary: #003366;
                        --primary-light: #004d99;
                        --accent: #f39c12;
                        --bg: #f8f9fa;
                        --card-bg: #ffffff;
                        --text: #2c3e50;
                        --border: #e2e8f0;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        display: flex;
                        height: 100vh;
                        overflow: hidden;
                    }
                    /* Left Sidebar */
                    .sidebar {
                        width: 360px;
                        background: var(--card-bg);
                        border-right: 1px solid var(--border);
                        display: flex;
                        flex-direction: column;
                        height: 100vh;
                    }
                    .sidebar-header {
                        padding: 18px 20px;
                        background: var(--primary);
                        color: white;
                    }
                    .sidebar-header h1 { font-size: 1.15rem; font-weight: 700; }
                    .sidebar-header p { font-size: 0.8rem; opacity: 0.85; margin-top: 4px; }
                    .search-box {
                        padding: 12px 16px;
                        border-bottom: 1px solid var(--border);
                        background: #fff;
                    }
                    .search-input {
                        width: 100%;
                        padding: 9px 12px;
                        border: 1px solid #cbd5e1;
                        border-radius: 6px;
                        font-size: 0.88rem;
                        outline: none;
                    }
                    .search-input:focus { border-color: var(--primary-light); }
                    .routine-list {
                        flex: 1;
                        overflow-y: auto;
                        padding: 8px 0;
                    }
                    .schema-group-title {
                        padding: 10px 16px 4px 16px;
                        font-size: 0.75rem;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        color: #64748b;
                    }
                    .routine-item {
                        padding: 10px 18px;
                        cursor: pointer;
                        border-left: 3px solid transparent;
                        transition: all 0.15s ease;
                    }
                    .routine-item:hover {
                        background: #f1f5f9;
                    }
                    .routine-item.active {
                        background: #e0f2fe;
                        border-left-color: var(--primary-light);
                    }
                    .routine-item-name {
                        font-weight: 600;
                        font-size: 0.88rem;
                        color: var(--primary);
                    }
                    .routine-item-meta {
                        font-size: 0.75rem;
                        color: #64748b;
                        margin-top: 2px;
                    }
                    /* Main Viewer Canvas */
                    .main-content {
                        flex: 1;
                        display: flex;
                        flex-direction: column;
                        height: 100vh;
                        overflow: hidden;
                    }
                    .top-toolbar {
                        padding: 14px 24px;
                        background: var(--card-bg);
                        border-bottom: 1px solid var(--border);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .active-title h2 { font-size: 1.2rem; color: var(--primary); }
                    .active-title p { font-size: 0.82rem; color: #64748b; margin-top: 2px; }
                    .btn-group { display: flex; gap: 8px; }
                    .btn {
                        padding: 7px 14px;
                        border-radius: 6px;
                        border: 1px solid #cbd5e1;
                        background: #fff;
                        color: var(--text);
                        font-size: 0.82rem;
                        font-weight: 600;
                        cursor: pointer;
                        transition: background 0.15s;
                    }
                    .btn:hover { background: #f1f5f9; }
                    .btn-primary {
                        background: var(--primary);
                        color: white;
                        border-color: var(--primary);
                    }
                    .btn-primary:hover { background: var(--primary-light); }
                    .canvas-area {
                        flex: 1;
                        overflow: auto;
                        padding: 30px;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        background: #ffffff;
                    }
                    .mermaid-wrapper {
                        background: #ffffff;
                        padding: 24px;
                        border-radius: 8px;
                        box-shadow: 0 4px 6px -1px rgba(0,0,0,0.07);
                        border: 1px solid var(--border);
                        max-width: 100%;
                        overflow: auto;
                    }
                    .code-preview {
                        margin-top: 20px;
                        width: 100%;
                        max-width: 900px;
                        background: #1e293b;
                        color: #f8fafc;
                        padding: 16px;
                        border-radius: 8px;
                        font-family: Consolas, Monaco, monospace;
                        font-size: 0.82rem;
                        white-space: pre-wrap;
                        display: none;
                    }
                </style>
            </head>
            <body>
                <div class="sidebar">
                    <div class="sidebar-header">
                        <h1>Florida Lottery</h1>
                        <p>Mermaid Flowchart Workbench (%%TOTAL%% Diagrams)</p>
                    </div>
                    <div class="search-box">
                        <input type="text" id="searchInput" class="search-input" placeholder="Search schema, package, procedure..." onkeyup="filterRoutines()">
                    </div>
                    <div class="routine-list" id="routineList">
            """.replace("%%TOTAL%%", String.valueOf(totalCount)));

        // Populate Sidebar Items
        int itemIndex = 0;
        for (Map.Entry<String, List<DiagramRecord>> entry : schemaMap.entrySet()) {
            html.append("<div class=\"schema-group-title\">Schema: ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append(")</div>");
            for (DiagramRecord r : entry.getValue()) {
                String safeKey = r.lookupKey.replace("'", "\\'");
                html.append(String.format("""
                    <div class="routine-item %s" id="item-%d" onclick="selectRoutine(%d)" data-search="%s %s %s">
                        <div class="routine-item-name">%s</div>
                        <div class="routine-item-meta">%s &bull; %s</div>
                    </div>
                    """,
                        (itemIndex == 0 ? "active" : ""),
                        itemIndex,
                        itemIndex,
                        r.schema.toLowerCase(),
                        r.packageName.toLowerCase(),
                        r.objectName.toLowerCase(),
                        r.objectName,
                        r.schema,
                        r.packageName.equals("-") ? "STANDALONE" : r.packageName
                ));
                itemIndex++;
            }
        }

        html.append("""
                    </div>
                </div>

                <div class="main-content">
                    <div class="top-toolbar">
                        <div class="active-title">
                            <h2 id="activeTitle">Loading...</h2>
                            <p id="activeDesc"></p>
                        </div>
                        <div class="btn-group">
                            <button class="btn" onclick="toggleRawCode()">Toggle Raw Code</button>
                            <button class="btn" onclick="copyMermaidCode()">Copy Mermaid Code</button>
                            <button class="btn btn-primary" onclick="openInMermaidLive()">Open in Mermaid.Live</button>
                        </div>
                    </div>

                    <div class="canvas-area">
                        <div class="mermaid-wrapper" id="mermaidContainer">
                            <div class="mermaid" id="mermaidTarget"></div>
                        </div>
                        <pre class="code-preview" id="codePreview"></pre>
                    </div>
                </div>

                <script>
                    const diagrams = [
            """);

        // Inject JavaScript Data Array
        for (int i = 0; i < schemaMap.values().stream().mapToInt(List::size).sum(); i++) {
            // Flattened iteration
        }
        List<DiagramRecord> allRecords = new ArrayList<>();
        schemaMap.values().forEach(allRecords::addAll);

        for (int i = 0; i < allRecords.size(); i++) {
            DiagramRecord r = allRecords.get(i);
            html.append("            {\n");
            html.append("                index: ").append(i).append(",\n");
            html.append("                key: ").append(escapeJsString(r.lookupKey)).append(",\n");
            html.append("                schema: ").append(escapeJsString(r.schema)).append(",\n");
            html.append("                pkg: ").append(escapeJsString(r.packageName)).append(",\n");
            html.append("                name: ").append(escapeJsString(r.objectName)).append(",\n");
            html.append("                desc: ").append(escapeJsString(r.whatDescription)).append(",\n");
            html.append("                code: ").append(escapeJsString(r.mermaidCode)).append("\n");
            html.append("            }").append(i < allRecords.size() - 1 ? ",\n" : "\n");
        }

        html.append("""
                    ];

                    mermaid.initialize({
                        startOnLoad: false,
                        theme: 'base',
                        themeVariables: {
                            primaryColor: '#002B49',
                            primaryTextColor: '#FFFFFF',
                            primaryBorderColor: '#005A9C',
                            lineColor: '#002B49',
                            textColor: '#002B49',
                            clusterBkg: '#F0F6FA',
                            clusterBorder: '#005A9C',
                            clusterTextColor: '#002B49',
                            titleColor: '#002B49',
                            edgeLabelBackground: '#FFFFFF'
                        },
                        flowchart: {
                            useMaxWidth: true,
                            htmlLabels: true,
                            subGraphTitleMargin: { top: 16, bottom: 42 },
                            nodeSpacing: 38,
                            rankSpacing: 48,
                            padding: 28,
                            curve: 'basis'
                        },
                        securityLevel: 'loose'
                    });

                    let currentIndex = 0;

                    async function renderCurrentDiagram() {
                        if (diagrams.length === 0) return;
                        const item = diagrams[currentIndex];
                        document.getElementById('activeTitle').innerText = item.key;
                        document.getElementById('activeDesc').innerText = item.desc || (item.schema + ' > ' + item.pkg + ' > ' + item.name);
                        document.getElementById('codePreview').innerText = item.code;

                        const container = document.getElementById('mermaidContainer');
                        container.innerHTML = '<div class="mermaid" id="mermaidTarget"></div>';
                        const target = document.getElementById('mermaidTarget');
                        target.textContent = item.code;

                        try {
                            await mermaid.run({ nodes: [target] });
                        } catch (err) {
                            console.error('Mermaid render error:', err);
                            target.innerHTML = '<div style="color:red; padding:20px;"><b>Render Error:</b> ' + err.message + '<br><br><pre>' + item.code + '</pre></div>';
                        }
                    }

                    function selectRoutine(idx) {
                        document.querySelectorAll('.routine-item').forEach(el => el.classList.remove('active'));
                        const selectedEl = document.getElementById('item-' + idx);
                        if (selectedEl) selectedEl.classList.add('active');
                        currentIndex = idx;
                        renderCurrentDiagram();
                    }

                    function filterRoutines() {
                        const q = document.getElementById('searchInput').value.toLowerCase().trim();
                        document.querySelectorAll('.routine-item').forEach(el => {
                            const searchAttr = el.getAttribute('data-search');
                            if (!q || searchAttr.includes(q)) {
                                el.style.display = 'block';
                            } else {
                                el.style.display = 'none';
                            }
                        });
                    }

                    function toggleRawCode() {
                        const pre = document.getElementById('codePreview');
                        pre.style.display = (pre.style.display === 'block') ? 'none' : 'block';
                    }

                    function copyMermaidCode() {
                        if (diagrams.length === 0) return;
                        const code = diagrams[currentIndex].code;
                        navigator.clipboard.writeText(code).then(() => {
                            alert('Mermaid code copied to clipboard!');
                        });
                    }

                    function openInMermaidLive() {
                        if (diagrams.length === 0) return;
                        const code = diagrams[currentIndex].code;
                        const state = {
                            code: code,
                            mermaid: { theme: 'default' }
                        };
                        const jsonStr = JSON.stringify(state);
                        const base64 = btoa(unescape(encodeURIComponent(jsonStr)));
                        window.open('https://mermaid.live/edit#' + base64, '_blank');
                    }

                    // Initial render on page load
                    window.addEventListener('DOMContentLoaded', () => {
                        renderCurrentDiagram();
                    });
                </script>
            </body>
            </html>
            """);

        Files.writeString(targetFile.toPath(), html.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeJsString(String str) {
        if (str == null) {
            String empty = "\"\"";
            return empty;
        }
        String json = new ObjectMapper().valueToTree(str).toString();
        return json;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            String def = "UNKNOWN";
            return def;
        }
        String clean = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean;
    }

    private static String extractField(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.hasNonNull(k)) {
                String val = node.get(k).asText().trim();
                if (!val.isEmpty()) {
                    return val;
                }
            }
        }
        String def = "";
        return def;
    }

    private static Map<String, String> parseCommandLineArgs(String[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        if (args == null) {
            return map;
        }
        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) {
                continue;
            }
            String clean = arg.trim();
            if (clean.startsWith("-") || clean.startsWith("--")) {
                clean = clean.replaceFirst("^-+", "");
                int eqIdx = clean.indexOf('=');
                if (eqIdx > 0) {
                    String key = clean.substring(0, eqIdx).trim();
                    String val = clean.substring(eqIdx + 1).trim();
                    map.put(key, val);
                } else {
                    map.put(clean, "true");
                }
            }
        }
        return map;
    }

    public static class DiagramRecord {
        public final String lookupKey;
        public final String schema;
        public final String packageName;
        public final String objectName;
        public final String whatDescription;
        public final String mermaidCode;

        public DiagramRecord(String lookupKey, String schema, String packageName, String objectName, String whatDescription, String mermaidCode) {
            this.lookupKey = lookupKey;
            this.schema = schema;
            this.packageName = packageName;
            this.objectName = objectName;
            this.whatDescription = whatDescription;
            this.mermaidCode = mermaidCode;
        }
    }
}
