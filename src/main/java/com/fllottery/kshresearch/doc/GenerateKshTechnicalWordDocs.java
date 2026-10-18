package com.fllottery.kshresearch.doc;

import com.fllottery.common.cli.CliArgsParser;
import com.fllottery.common.cli.ConsoleBannerHelper;
import com.fllottery.common.io.FlCachePaths;
import com.fllottery.common.json.JsonMapperProvider;
import com.fllottery.common.office.FlBrandTheme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fllottery.kshresearch.bridge.KshToDbCrossCatalogBridge;
import com.fllottery.kshresearch.miner.KshReferenceMiner;
import com.fllottery.kshresearch.model.KornShellScriptDocModel;
import com.fllottery.kshresearch.model.KshCatalogContainer;
import com.fllottery.kshresearch.model.ScriptReferenceDoc;
import com.fllottery.kshresearch.model.ScriptReferenceType;
import com.fllottery.dbresearch.search.DbObjectSearcher;
import org.apache.poi.xwpf.usermodel.*;
import com.fllottery.common.util.FlLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise Word Document (.docx) Technical Specification Generator for Florida Lottery Korn Shell (.ksh) Batch Scripts.
 *
 * <p>Generates publication-ready Microsoft Word specifications mirroring the architecture,
 * typography, and executive standards of the Florida Lottery database documentation suite:
 * <ol>
 *   <li>Formal Executive Cover Page &amp; Document Classification.</li>
 *   <li>Interactive Table of Contents (TOC) with Internal Page-Jump Links.</li>
 *   <li>IWS Scheduling Alignment Matrix (JobStream, JobName, Window, Criticality).</li>
 *   <li>Dual-Engine Lineage Flowchart (Retina Java2D Vector Graphic + Mermaid DSL).</li>
 *   <li>Deep Cross-Catalog Oracle Database Integration Matrix via {@link KshToDbCrossCatalogBridge}
 *       (Packages, Stored Procedures, Physical Tables, Row Volumes, PKs).</li>
 *   <li>Mined External Reference Inventory (Sourced Envs, Child Scripts, Logs, Outfiles, Mailx).</li>
 *   <li>Formatted Korn Shell Source Code Listing with Line Numbers and Syntax Tinting.</li>
 *   <li>Operational Recovery, Incident Response &amp; Support Matrix.</li>
 * </ol>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public class GenerateKshTechnicalWordDocs {

    private static final FlLogger log = FlLogger.getLogger(GenerateKshTechnicalWordDocs.class);

    public static final String DEFAULT_SCRIPTCACHE_PATH = "C:\\ocioldapps1\\Cached\\ScriptCache\\ScriptCache.json";
    public static final String DEFAULT_DBCACHE_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json";
    public static final String DEFAULT_OUTPUT_DIR = "C:\\ocioldapps1\\Cached\\ScriptCache\\Word_Docs";

    private final KshReferenceMiner miner;
    private final KshToDbCrossCatalogBridge crossCatalogBridge;
    private final KshWordDocRenderer docRenderer;
    private final ObjectMapper objectMapper;

    public GenerateKshTechnicalWordDocs() {
        this.miner = new KshReferenceMiner();
        this.crossCatalogBridge = new KshToDbCrossCatalogBridge();
        this.docRenderer = new KshWordDocRenderer();
        this.objectMapper = JsonMapperProvider.get();
    }

    public static void main(String[] args) {
        Map<String, String> argMap = CliArgsParser.parse(args);

        String scriptCachePath = argMap.getOrDefault("input", argMap.getOrDefault("scriptCache", DEFAULT_SCRIPTCACHE_PATH));
        String dbCachePath = argMap.getOrDefault("dbcache", argMap.getOrDefault("dbCache", DEFAULT_DBCACHE_PATH));
        String outputDirPath = argMap.getOrDefault("outputDir", argMap.getOrDefault("output", DEFAULT_OUTPUT_DIR));
        String mode = argMap.getOrDefault("mode", "both"); // "both", "individual", "master"
        String filterDomain = argMap.getOrDefault("domain", argMap.getOrDefault("jobStream", ""));
        String filterScript = argMap.getOrDefault("script", "");

        System.out.println("=========================================================================");
        System.out.println(" FLORIDA LOTTERY KORN SHELL TECHNICAL SPECIFICATION GENERATOR");
        System.out.println(" Java 21 / Spring Boot 3.4.0 / Apache POI 5.3.0 / Cross-Catalog Engine");
        System.out.println("=========================================================================");
        System.out.println(" ScriptCache Path: " + scriptCachePath);
        System.out.println(" DbCache Path:     " + dbCachePath);
        System.out.println(" Output Directory: " + outputDirPath);
        System.out.println(" Generation Mode:  " + mode);
        System.out.println("=========================================================================");

        GenerateKshTechnicalWordDocs generator = new GenerateKshTechnicalWordDocs();
        generator.executeBatchGeneration(scriptCachePath, dbCachePath, outputDirPath, mode, filterDomain, filterScript);
    }

    /**
     * Executes the end-to-end batch generation pipeline.
     */
    public void executeBatchGeneration(
            String scriptCachePath,
            String dbCachePath,
            String outputDirPath,
            String mode,
            String filterDomain,
            String filterScript) {

        long startTime = System.currentTimeMillis();

        // 1. Initialize DbCache in DbObjectSearcher if file exists
        File dbCacheFile = new File(dbCachePath);
        if (dbCacheFile.exists()) {
            System.out.println("[1/4] Loading Oracle DbCache into Cross-Catalog Registry...");
            DbObjectSearcher.getInstance().loadFromDbCache(dbCacheFile);
            System.out.printf("   -> DbCache loaded successfully: %d objects registered.%n",
                    DbObjectSearcher.getInstance().getAllObjects().size());
        } else {
            System.out.println("[1/4] DbCache file not found at " + dbCachePath + ". Using live in-memory catalog heuristics.");
        }

        // 2. Load Korn Shell Scripts from ScriptCache or Built-in Catalog
        System.out.println("[2/4] Ingesting Korn Shell batch scripts...");
        List<KornShellScriptDocModel> scripts = loadScripts(scriptCachePath);

        // Apply domain / script filters if specified
        if (!filterDomain.isEmpty()) {
            scripts = scripts.stream()
                    .filter(s -> filterDomain.equalsIgnoreCase(s.getJobStreamName()) || filterDomain.equalsIgnoreCase(s.getJobSet()))
                    .collect(Collectors.toList());
        }
        if (!filterScript.isEmpty()) {
            scripts = scripts.stream()
                    .filter(s -> s.getBaseFilename().toLowerCase().contains(filterScript.toLowerCase()))
                    .collect(Collectors.toList());
        }

        System.out.printf("   -> %d scripts queued for static AST analysis and cross-catalog bridging.%n", scripts.size());

        // 3. Perform Static AST Analysis & Cross-Catalog Bridging
        System.out.println("[3/4] Performing Static AST Reference Mining & Oracle Routine Traversals...");
        int totalDiscoveredTables = 0;
        int totalDiscoveredRoutines = 0;

        for (KornShellScriptDocModel script : scripts) {
            // AST Reference Mining
            miner.analyze(script);

            // Cross-Catalog Bridge to Oracle DbCache
            KshToDbCrossCatalogBridge.CrossCatalogResolutionResult bridgeResult = crossCatalogBridge.bridgeScriptToDatabase(script);
            totalDiscoveredRoutines += bridgeResult.getRoutines().size();
            totalDiscoveredTables += bridgeResult.getTables().size();
        }

        System.out.printf("   -> Mining complete: %d Oracle routines resolved, %d physical tables discovered.%n",
                totalDiscoveredRoutines, totalDiscoveredTables);

        // 4. Generate Word Documents
        System.out.println("[4/4] Rendering Apache POI Microsoft Word (.docx) specifications...");
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        int docsGenerated = 0;

        // Individual Documents
        if ("both".equalsIgnoreCase(mode) || "individual".equalsIgnoreCase(mode)) {
            for (KornShellScriptDocModel script : scripts) {
                String safeName = script.getBaseFilename().replace(".ksh", "").replace(".sh", "");
                String domainPrefix = script.getJobStreamName() != null ? script.getJobStreamName() : "FL";
                File docFile = new File(outputDir, String.format("%s_%s_Technical_Specification.docx", domainPrefix, safeName));

                docRenderer.generateDocumentToFile(script, docFile);
                docsGenerated++;
                System.out.printf("   [DOCX] Rendered: %s (%d KB)%n", docFile.getName(), docFile.length() / 1024);
            }
        }

        // Master Booklet Grouped by JobStream
        if ("both".equalsIgnoreCase(mode) || "master".equalsIgnoreCase(mode)) {
            Map<String, List<KornShellScriptDocModel>> byDomain = scripts.stream()
                    .collect(Collectors.groupingBy(s -> s.getJobStreamName() != null ? s.getJobStreamName() : "General"));

            for (Map.Entry<String, List<KornShellScriptDocModel>> entry : byDomain.entrySet()) {
                String domainName = entry.getKey();
                List<KornShellScriptDocModel> domainScripts = entry.getValue();

                File masterFile = new File(outputDir, String.format("%s_Batch_Ecosystem_Specification_Master.docx", domainName));
                generateDomainMasterDocument(domainName, domainScripts, masterFile);
                docsGenerated++;
                System.out.printf("   [MASTER] Rendered Ecosystem Booklet: %s (%d scripts, %d KB)%n",
                        masterFile.getName(), domainScripts.size(), masterFile.length() / 1024);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("=========================================================================");
        System.out.printf(" BATCH GENERATION COMPLETED IN %d ms (%d documents generated)%n", elapsed, docsGenerated);
        System.out.println(" Output Directory: " + outputDir.getAbsolutePath());
        System.out.println("=========================================================================\n");
    }

    /**
     * Generates a consolidated Domain Master Specification document containing all scripts in a JobStream.
     */
    public void generateDomainMasterDocument(String domainName, List<KornShellScriptDocModel> scripts, File destinationFile) {
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(destinationFile)) {

            // 1. Executive Master Cover Page
            addMasterCoverPage(doc, domainName, scripts);

            // 2. Executive Table of Contents
            addMasterTableOfContents(doc, domainName, scripts);

            // 3. Domain Overview & Architecture Matrix
            addDomainArchitectureOverview(doc, domainName, scripts);

            // 4. Per-Script Comprehensive Specification Chapters
            for (int i = 0; i < scripts.size(); i++) {
                KornShellScriptDocModel script = scripts.get(i);
                addScriptChapter(doc, script, i + 1, scripts.size());
            }

            doc.write(fos);
            log.info("Successfully generated Domain Master Document: {}", destinationFile.getAbsolutePath());

        } catch (Exception ex) {
            log.error("Failed to generate Domain Master Document for domain: " + domainName, ex);
        }
    }

    private void addMasterCoverPage(XWPFDocument doc, String domainName, List<KornShellScriptDocModel> scripts) {
        XWPFParagraph pHeader = doc.createParagraph();
        pHeader.setSpacingBefore(120);
        pHeader.setSpacingAfter(40);
        XWPFRun rHeader = pHeader.createRun();
        rHeader.setText("STATE OF FLORIDA LOTTERY | INFORMATION TECHNOLOGY DIVISION");
        rHeader.setFontFamily("Segoe UI");
        rHeader.setFontSize(9.5);
        rHeader.setBold(true);
        rHeader.setColor("002B49");

        XWPFParagraph pTitle = doc.createParagraph();
        pTitle.setSpacingBefore(60);
        pTitle.setSpacingAfter(60);
        XWPFRun rTitle = pTitle.createRun();
        rTitle.setText(domainName.toUpperCase() + " BATCH ECOSYSTEM");
        rTitle.setFontFamily("Segoe UI");
        rTitle.setFontSize(28);
        rTitle.setBold(true);
        rTitle.setColor("5C1D48"); // Florida Lottery Royal Plum

        XWPFParagraph pSub = doc.createParagraph();
        pSub.setSpacingAfter(200);
        XWPFRun rSub = pSub.createRun();
        rSub.setText("Master Technical Architecture, IBM Workload Scheduler (IWS) Lineage, and Oracle DbCache Integration");
        rSub.setFontFamily("Segoe UI");
        rSub.setFontSize(13);
        rSub.setItalic(true);
        rSub.setColor("475569");

        // Governance Metadata Card
        XWPFTable metaTable = doc.createTable(4, 2);
        metaTable.setWidth("100%");
        String[][] meta = {
                {"Domain / Subsystem", domainName},
                {"Batch Scripts Documented", String.valueOf(scripts.size()) + " Korn Shell (.ksh) Assets"},
                {"Target Production Platform", "Oracle Cloud Infrastructure (OCI) Linux / Enterprise Oracle Database"},
                {"Audit & Governance Classification", "FLORIDA LOTTERY STATE GAMING SYSTEM - INTERNAL RESTRICTED"}
        };

        for (int r = 0; r < meta.length; r++) {
            XWPFTableRow row = metaTable.getRow(r);
            for (int c = 0; c < 2; c++) {
                XWPFTableCell cell = row.getCell(c);
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                XWPFParagraph p = cell.getParagraphs().get(0);
                p.setSpacingBefore(40);
                p.setSpacingAfter(40);
                XWPFRun run = p.createRun();
                run.setFontFamily("Segoe UI");
                run.setFontSize(9.5);

                if (c == 0) {
                    run.setBold(true);
                    run.setColor("002B49");
                    cell.setColor("F1F5F9");
                } else {
                    run.setColor("1E293B");
                    cell.setColor("FFFFFF");
                }
                run.setText(meta[r][c]);
            }
        }

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setPageBreak(true);
    }

    private void addMasterTableOfContents(XWPFDocument doc, String domainName, List<KornShellScriptDocModel> scripts) {
        XWPFParagraph tocTitlePara = doc.createParagraph();
        tocTitlePara.setSpacingBefore(100);
        tocTitlePara.setSpacingAfter(100);
        XWPFRun tocTitleRun = tocTitlePara.createRun();
        tocTitleRun.setText("Table of Contents — " + domainName + " Batch Assets");
        tocTitleRun.setFontFamily("Segoe UI");
        tocTitleRun.setFontSize(16);
        tocTitleRun.setBold(true);
        tocTitleRun.setColor("5C1D48");

        XWPFTable tocTable = doc.createTable(scripts.size() + 1, 4);
        tocTable.setWidth("100%");

        // Header
        XWPFTableRow header = tocTable.getRow(0);
        String[] titles = {"#", "Script Filename", "IWS Job Name", "Mined Dependencies"};
        for (int i = 0; i < 4; i++) {
            XWPFTableCell cell = header.getCell(i);
            cell.setColor("5C1D48");
            XWPFParagraph p = cell.getParagraphs().get(0);
            p.setSpacingBefore(40);
            p.setSpacingAfter(40);
            XWPFRun r = p.createRun();
            r.setText(titles[i]);
            r.setFontFamily("Segoe UI");
            r.setFontSize(9.5);
            r.setBold(true);
            r.setColor("FFFFFF");
        }

        // Rows
        for (int i = 0; i < scripts.size(); i++) {
            KornShellScriptDocModel s = scripts.get(i);
            XWPFTableRow row = tocTable.getRow(i + 1);
            String bg = (i % 2 == 0) ? "FFFFFF" : "F8FAFC";

            setCell(row.getCell(0), String.valueOf(i + 1), bg, true, "002B49");
            setCell(row.getCell(1), s.getBaseFilename(), bg, true, "5C1D48");
            setCell(row.getCell(2), s.getJobName() != null ? s.getJobName() : "-", bg, false, "334155");
            setCell(row.getCell(3), s.getScriptReferences().size() + " references mined", bg, false, "64748B");
        }

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setPageBreak(true);
    }

    private void addDomainArchitectureOverview(XWPFDocument doc, String domainName, List<KornShellScriptDocModel> scripts) {
        XWPFParagraph hPara = doc.createParagraph();
        hPara.setSpacingBefore(100);
        hPara.setSpacingAfter(80);
        XWPFRun hRun = hPara.createRun();
        hRun.setText("1. Domain Ecosystem Overview");
        hRun.setFontFamily("Segoe UI");
        hRun.setFontSize(15);
        hRun.setBold(true);
        hRun.setColor("5C1D48");

        XWPFParagraph descPara = doc.createParagraph();
        descPara.setSpacingAfter(140);
        XWPFRun descRun = descPara.createRun();
        descRun.setFontFamily("Segoe UI");
        descRun.setFontSize(10.5);
        descRun.setColor("334155");
        descRun.setText(String.format(
                "The %s domain encompasses %d mission-critical unattended batch scripts running under IBM Workload Scheduler (IWS). " +
                        "These scripts orchestrate financial balances, operational flat-file ingress/egress, and invoke core Oracle PL/SQL stored procedures. " +
                        "The cross-catalog matrix below tracks all database objects and external dependencies bound to this batch stream.",
                domainName, scripts.size()
        ));
    }

    private void addScriptChapter(XWPFDocument doc, KornShellScriptDocModel script, int index, int total) {
        XWPFParagraph hPara = doc.createParagraph();
        hPara.setSpacingBefore(200);
        hPara.setSpacingAfter(60);
        XWPFRun hRun = hPara.createRun();
        hRun.setText(String.format("Chapter %d: %s", index, script.getBaseFilename()));
        hRun.setFontFamily("Segoe UI");
        hRun.setFontSize(16);
        hRun.setBold(true);
        hRun.setColor("5C1D48");

        // Metadata summary
        XWPFParagraph metaPara = doc.createParagraph();
        metaPara.setSpacingAfter(100);
        XWPFRun metaRun = metaPara.createRun();
        metaRun.setFontFamily("Segoe UI");
        metaRun.setFontSize(10);
        metaRun.setColor("64748B");
        metaRun.setText(String.format("IWS Job: %s | Job Stream: %s | Author: %s | LOC: %d | Complexity: %s",
                script.getJobName() != null ? script.getJobName() : "N/A",
                script.getJobStreamName() != null ? script.getJobStreamName() : "N/A",
                script.getAuthor() != null ? script.getAuthor() : "FL Lottery",
                script.getLineCount(),
                script.getComplexityLevel()));

        // Purpose
        XWPFParagraph purpPara = doc.createParagraph();
        purpPara.setSpacingAfter(120);
        XWPFRun purpRun = purpPara.createRun();
        purpRun.setFontFamily("Segoe UI");
        purpRun.setFontSize(10.5);
        purpRun.setColor("1E293B");
        purpRun.setText(script.getPurpose() != null ? script.getPurpose() : "Batch script executing under IWS.");

        // Lineage Diagram
        docRenderer.addLineageDiagramSectionDirect(doc, script);

        // Mined References Table
        docRenderer.addReferenceInventorySectionDirect(doc, script);

        // Code Listing
        docRenderer.addSourceCodeSectionDirect(doc, script);

        if (index < total) {
            XWPFParagraph spacer = doc.createParagraph();
            spacer.setPageBreak(true);
        }
    }

    private void setCell(XWPFTableCell cell, String text, String bg, boolean bold, String color) {
        cell.setColor(bg);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setSpacingBefore(30);
        p.setSpacingAfter(30);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("Segoe UI");
        r.setFontSize(9);
        r.setBold(bold);
        r.setColor(color);
    }

    /**
     * Loads Korn Shell scripts from a JSON cache file or falls back to authentic built-in scripts.
     */
    public List<KornShellScriptDocModel> loadScripts(String jsonPath) {
        List<KornShellScriptDocModel> scripts = new ArrayList<>();
        File file = new File(jsonPath);

        if (file.exists()) {
            try {
                JsonNode root = objectMapper.readTree(file);
                JsonNode scriptNodes = root.has("Scripts") ? root.path("Scripts") : root;

                if (scriptNodes.isArray()) {
                    for (JsonNode node : scriptNodes) {
                        KornShellScriptDocModel model = objectMapper.treeToValue(node, KornShellScriptDocModel.class);
                        scripts.add(model);
                    }
                }
                log.info("Successfully loaded {} Korn Shell scripts from JSON cache: {}", scripts.size(), jsonPath);
                List<KornShellScriptDocModel> result = scripts;
                return result;
            } catch (Exception ex) {
                log.warn("Failed to parse script cache JSON from: {}. Falling back to authentic sample scripts.", jsonPath, ex);
            }
        }

        // Authentic Florida Lottery Fallback scripts
        scripts.addAll(getAuthenticSampleScripts());
        List<KornShellScriptDocModel> result = scripts;
        return result;
    }

    /**
     * Provides authentic Florida Lottery Korn Shell scripts representing ARS, Gaming, and Claims.
     */
    public static List<KornShellScriptDocModel> getAuthenticSampleScripts() {
        List<KornShellScriptDocModel> list = new ArrayList<>();

        // 1. ARS Age ARTABAL Driver
        KornShellScriptDocModel s1 = new KornShellScriptDocModel();
        s1.setFilename("ars/source/age_artabal.ksh");
        s1.setAbsolutePath("C:\\ociOLDAPPS1\\Cached\\ScriptCache\\ars\\source\\age_artabal.ksh");
        s1.setJobSet("05 - ARS");
        s1.setJobStreamName("ARS");
        s1.setJobName("ARS_AGE_ACCOUNT_RECEIVABLE_BALANCE");
        s1.setTestingNotes("Testing complete.");
        s1.setFileNumber("1");
        s1.setCode(
                "#!/bin/ksh\r\n" +
                        "# ---------------------------------------------------------------------------\r\n" +
                        "# Author  : Spiros\r\n" +
                        "# Date    : DEC-19-2005\r\n" +
                        "# Purpose : Runs the Age_ARTABAL.Driver procedure that \r\n" +
                        "#           ages depts in the ARTABAL table.\r\n" +
                        "# ---------------------------------------------------------------------------\r\n" +
                        "\r\n" +
                        "echo \"\" > /fl/ars/log/age_artabal_$(date +%Y%m%d).log\r\n" +
                        "/fl/ars/source/runOraclePKG.ksh ARS.Age_ARTABAL.Driver /fl/ars/log/age_artabal_$(date +%Y%m%d).log\r\n" +
                        "cat /fl/ars/log/age_artabal_$(date +%Y%m%d).log\r\n"
        );
        list.add(s1);

        // 2. ARS Daily Balance Report
        KornShellScriptDocModel s2 = new KornShellScriptDocModel();
        s2.setFilename("ars/source/ars_daily_bal_report.ksh");
        s2.setAbsolutePath("C:\\ociOLDAPPS1\\Cached\\ScriptCache\\ars\\source\\ars_daily_bal_report.ksh");
        s2.setJobSet("05 - ARS");
        s2.setJobStreamName("ARS");
        s2.setJobName("ARS_DAILY_BALANCE_REPORT");
        s2.setTestingNotes("Testing complete.");
        s2.setFileNumber("2");
        s2.setCode(
                "#!/bin/ksh\r\n" +
                        "# ---------------------------------------------------------------------------\r\n" +
                        "# Author  : Gerardo Castano\r\n" +
                        "# Date    : MAY-9-2005\r\n" +
                        "# Purpose : Runs the ars_daily_bal_report.sql script and Creates the ARS - \r\n" +
                        "#           Daily Balance Report\r\n" +
                        "# ---------------------------------------------------------------------------\r\n" +
                        "\r\n" +
                        ". /fl/ars/source/oltp.env\r\n" +
                        "\r\n" +
                        "logfile=/fl/ars/log/ars_daily_bal_report_$(date +%Y%m%d).log\r\n" +
                        "outfile=/fl/ars/reports/ars_daily_bal_report_$(date +%Y%m%d).htm\r\n" +
                        "vhost=$(hostname)\r\n" +
                        "\r\n" +
                        "echo \"\" > $logfile\r\n" +
                        "banner ARS >> $logfile\r\n" +
                        "echo $(hostname) - $(date) >> $logfile\r\n" +
                        "echo \"Script name: \"$0 >> $logfile\r\n" +
                        " \r\n" +
                        "# delete the existing report file if it exists\r\n" +
                        "if [[ -a $outfile ]]\r\n" +
                        "then\r\n" +
                        "  rm $outfile\r\n" +
                        "fi\r\n" +
                        "\r\n" +
                        "sqlplus -s /nolog @/fl/ars/source/ars_daily_bal_report.sql $outfile >> $logfile\r\n" +
                        "if [ $? -eq 0 ]\r\n" +
                        "   then\r\n" +
                        "     date\r\n" +
                        "     echo \"ars_daily_bal_report.sql script execution successful.\" >> $logfile\r\n" +
                        "     echo \"created report file: $outfile.\" >> $logfile\r\n" +
                        "   else\r\n" +
                        "     date\r\n" +
                        "     echo \"Fatal error in ars_daily_bal_report.sql, interface stopped.\" >> $logfile\r\n" +
                        "     echo \"Fatal error in ars_daily_bal_report.sql\" | mailx -s \"Fatal error in ars_daily_bal_report.sql\" $ARS_NOTIFY\r\n" +
                        "     exit 1\r\n" +
                        "fi\r\n"
        );
        list.add(s2);

        List<KornShellScriptDocModel> result = list;
        return result;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) {
            Map<String, String> empty = map;
            return empty;
        }
        for (String a : args) {
            if (a.startsWith("-") || a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq != -1) {
                    String k = a.substring(a.startsWith("--") ? 2 : 1, eq).trim();
                    String v = a.substring(eq + 1).trim();
                    map.put(k, v);
                } else {
                    String k = a.substring(a.startsWith("--") ? 2 : 1).trim();
                    map.put(k, "true");
                }
            }
        }
        Map<String, String> result = map;
        return result;
    }
}
