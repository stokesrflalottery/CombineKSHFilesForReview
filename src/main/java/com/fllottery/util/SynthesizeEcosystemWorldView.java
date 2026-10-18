package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

/**
 * Enterprise Macro Ecosystem World-View Synthesis Engine for the Florida Lottery.
 *
 * Consolidates the complete estate of 1,582 database routines across all 12 schemas
 * and synthesizes a high-level "World-View" macro narrative for ALL OBJECTS
 * (especially Packages, Procedures, and Core Tables).
 *
 * Generates plain-English (2 to 6 sentences) descriptions explaining:
 *  1. What role each Package and Routine plays in the broader Florida Lottery ecosystem.
 *  2. Which business subsystem it belongs to (e.g. Ingestion, Retailer Accounting, GL, Promotions).
 *  3. Its chronological pipeline phase (Ingress -> Staging -> Core Processing -> Settlement -> Egress).
 *  4. Upstream suppliers and downstream consumer dependencies.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @since 2026-09-26
 */
public class SynthesizeEcosystemWorldView {

    private static final String DEFAULT_CATALOG_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\DbCache_Ai_Researched_Catalog.json";
    private static final String DEFAULT_DBCACHE_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json";
    private static final String DEFAULT_SCHEMAS_DIR = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas";
    private static final String DEFAULT_OUTPUT_JSON = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\DbCache_Ecosystem_WorldView.json";

    private static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static String activeModelName = DEFAULT_MODEL;
    private static boolean isFreeTierMode = false;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static long lastRequestTimeMs = 0;

    private static final double INPUT_TOKEN_COST_PENNIES = 0.0000075;
    private static final double OUTPUT_TOKEN_COST_PENNIES = 0.0000300;
    private static long totalPromptTokens = 0;
    private static long totalCandidateTokens = 0;
    private static long totalBytesIn = 0;
    private static long totalBytesOut = 0;
    private static double totalCostInPennies = 0.0;
    private static long lastCallPromptTokens = 0;
    private static long lastCallCandidateTokens = 0;
    private static double lastCallCostPennies = 0.0;
    private static boolean displayJsonBlocks = false; // Default false: compact single-line status without dumping verbose JSON blocks

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.useSystemProxies", "true");

        Map<String, String> argMap = parseCommandLineArgs(args);

        String catalogPath = argMap.getOrDefault("catalog", DEFAULT_CATALOG_PATH);
        String dbCachePath = argMap.getOrDefault("input", DEFAULT_DBCACHE_PATH);
        String schemasDirPath = argMap.getOrDefault("schemasDir", DEFAULT_SCHEMAS_DIR);
        String outputPath = argMap.getOrDefault("output", DEFAULT_OUTPUT_JSON);

        String requestedModel = argMap.getOrDefault("model", "");
        activeModelName = requestedModel.isEmpty() ? DEFAULT_MODEL : requestedModel;
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + activeModelName + ":generateContent";

        String tierArg = argMap.getOrDefault("tier", "");
        if (argMap.containsKey("isFreeApi")) {
            isFreeTierMode = Boolean.parseBoolean(argMap.get("isFreeApi").trim());
        } else if (!tierArg.isEmpty()) {
            isFreeTierMode = tierArg.equalsIgnoreCase("free");
        } else {
            isFreeTierMode = ExtractAndResearchProcsInventory.DEFAULT_IS_FREE_API;
        }

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = isFreeTierMode ? ExtractAndResearchProcsInventory.FREE_API_KEY : ExtractAndResearchProcsInventory.PAID_API_KEY;
        }

        if (argMap.containsKey("jsonBlocks")) {
            displayJsonBlocks = Boolean.parseBoolean(argMap.get("jsonBlocks").trim());
        } else if (argMap.containsKey("json")) {
            displayJsonBlocks = Boolean.parseBoolean(argMap.get("json").trim());
        }

        File catalogFile = new File(catalogPath);
        if (!catalogFile.exists()) {
            System.err.println("ACTION REQUIRED: Catalog JSON not found at: " + catalogPath);
            return;
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Initialize persistent disk-backed Quota & Availability Tracker
        String quotaTrackerOutputPath = schemasDirPath + "\\Ai_Model_Quota_Tracker.json";
        File quotaTrackerJsonFile = new File(argMap.getOrDefault("quotaTracker", argMap.getOrDefault("quotaJson", quotaTrackerOutputPath)));
        ExtractAndResearchProcsInventory.initQuotaTracker(quotaTrackerJsonFile, mapper);

        System.out.println("=========================================================================");
        System.out.println("Florida Lottery Enterprise Ecosystem World-View Synthesizer");
        System.out.println("Target Catalog:  " + catalogPath);
        System.out.println("DbCache Input:   " + dbCachePath);
        System.out.println("Output JSON:     " + outputPath);
        System.out.println("Quota Tracker:   " + quotaTrackerJsonFile.getAbsolutePath());
        System.out.println("Preferred Model: " + (requestedModel.isEmpty() ? "[Auto-Negotiate unexhausted candidate]" : requestedModel));
        System.out.println("Auth Key:        " + (apiKey.isEmpty() ? "[MISSING - Offline Mode]" : "[Active API Key: " + apiKey.substring(0, Math.min(8, apiKey.length())) + "...]"));
        System.out.println("Console Format:  " + (displayJsonBlocks ? "Interactive Pretty-Printed JSON Blocks (Multi-Line)" : "Compact Single-Line Status"));
        System.out.println("=========================================================================");

        // Display cached daily quota availability per candidate model on startup
        ExtractAndResearchProcsInventory.printStartupQuotaSummaryTable();

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30));

        EnterpriseProxyResolver.EnterpriseProxyConfig proxyConfig = EnterpriseProxyResolver.resolve(argMap);
        EnterpriseProxyResolver.applyConfiguration(proxyConfig);
        EnterpriseProxyResolver.configureHttpClientBuilder(clientBuilder);

        if (proxyConfig.isEnabled()) {
            System.out.println("Network Routing: Florida Lottery Enterprise Proxy Active -> " + proxyConfig.getHost() + ":" + proxyConfig.getPort() + " (Source: " + proxyConfig.getResolutionSource() + ")");
        } else {
            System.out.println("Network Routing: Direct Connection (No Enterprise Proxy Configured)");
        }

        HttpClient httpClient = clientBuilder.build();

        // Dynamically resolve active model (automatically skips any model marked EXHAUSTED_429 in cache)
        if (!apiKey.isEmpty()) {
            String resolvedUrl = ExtractAndResearchProcsInventory.resolveActiveApiUrl(httpClient, mapper, (requestedModel.isEmpty() ? null : requestedModel), apiKey);
            if (resolvedUrl != null) {
                apiUrl = resolvedUrl;
                activeModelName = ExtractAndResearchProcsInventory.getActiveModelName();
                System.out.println("Handshake confirmed with active model: " + activeModelName);
            }
        }

        try {
            // Step 1: Ingest existing catalog and organize into hierarchical domain graph
            System.out.println("\n[Phase 1] Loading Researched Catalog and Building Ecosystem Graph...");
            JsonNode catalogRoot = mapper.readTree(catalogFile);
            JsonNode routinesNode = catalogRoot.path("ResearchedRoutines");

            if (!routinesNode.isArray() || routinesNode.isEmpty()) {
                System.err.println("ACTION REQUIRED: ResearchedRoutines array is empty in " + catalogPath);
                return;
            }

            System.out.printf("Loaded %d routines from catalog.%n", routinesNode.size());

            Map<String, Map<String, List<JsonNode>>> hierarchy = new TreeMap<>();
            Map<String, Set<String>> tablesReadBySchema = new TreeMap<>();
            Map<String, Set<String>> tablesWrittenBySchema = new TreeMap<>();
            Map<String, Set<String>> packagesCalledBySchema = new TreeMap<>();

            for (JsonNode r : routinesNode) {
                String schema = r.path("Schema").asText("UNKNOWN").toUpperCase();
                String pkg = r.path("Package").asText("-").toUpperCase();
                if (pkg.equals("-") || pkg.isEmpty()) pkg = "STANDALONE";

                hierarchy.computeIfAbsent(schema, k -> new TreeMap<>())
                        .computeIfAbsent(pkg, k -> new ArrayList<>())
                        .add(r);

                String deps = r.path("Dependencies").asText("");
                extractTableReferences(schema, deps, tablesReadBySchema, tablesWrittenBySchema, packagesCalledBySchema);
            }

            System.out.printf("Indexed %d schemas containing %d distinct package/standalone modules.%n",
                    hierarchy.size(), hierarchy.values().stream().mapToInt(Map::size).sum());

            // Index Tables by Schema from DbCache.json (including External Tables)
            Map<String, List<JsonNode>> tablesBySchema = new TreeMap<>();
            File dbCacheFile = new File(dbCachePath);
            if (dbCacheFile.exists()) {
                try {
                    JsonNode dbCacheRoot = mapper.readTree(dbCacheFile);
                    JsonNode schemasNode = dbCacheRoot.path("Schemas");
                    if (schemasNode.isArray()) {
                        for (JsonNode s : schemasNode) {
                            String sName = s.path("SchemaName").asText(s.path("Name").asText("")).trim().toUpperCase();
                            JsonNode tables = s.path("Tables");
                            if (!tables.isArray()) tables = s.path("tables");
                            if (tables.isArray()) {
                                List<JsonNode> tblList = tablesBySchema.computeIfAbsent(sName, k -> new ArrayList<>());
                                for (JsonNode tbl : tables) {
                                    tblList.add(tbl);
                                }
                            }
                        }
                    }
                    int totalCachedTables = tablesBySchema.values().stream().mapToInt(List::size).sum();
                    System.out.printf("Loaded %d database tables across %d schemas from %s.%n", totalCachedTables, tablesBySchema.size(), dbCachePath);
                } catch (Exception ex) {
                    System.err.println("Warning loading tables from DbCache: " + ex.getMessage());
                }
            }

            // Step 2: Global Macro Blueprint Request (One Big Call)
            System.out.println("\n[Phase 2] Executing Global Macro Blueprint Request (The World View)...");
            String globalDigest = buildGlobalEcosystemDigest(hierarchy, tablesReadBySchema, tablesWrittenBySchema, packagesCalledBySchema);

            System.out.printf("Built Global Ecosystem Digest (Chars: %d | ~%d tokens). Calling Gemini API...%n",
                    globalDigest.length(), (globalDigest.length() / 4));

            String macroPrompt = buildMacroPrompt(globalDigest);
            long macroStart = System.currentTimeMillis();
            String macroResponse = callGeminiRaw(httpClient, mapper, apiUrl, apiKey, macroPrompt, 60);

            if (macroResponse == null || macroResponse.trim().isEmpty()) {
                System.err.println("ACTION REQUIRED: Macro architecture synthesis failed or timed out.");
                return;
            }

            long macroElapsed = System.currentTimeMillis() - macroStart;
            double macroKbIn = macroPrompt.getBytes(StandardCharsets.UTF_8).length / 1024.0;
            double macroKbOut = macroResponse.getBytes(StandardCharsets.UTF_8).length / 1024.0;
            System.out.printf("Global Macro Blueprint received in %d ms! (%.2f KB in / %.2f KB out | %d in + %d out tokens | %.4f¢ [ $%.6f USD ])%n",
                    macroElapsed, macroKbIn, macroKbOut, lastCallPromptTokens, lastCallCandidateTokens, lastCallCostPennies, (lastCallCostPennies / 100.0));

            JsonNode macroJson = mapper.readTree(macroResponse);
            if (displayJsonBlocks) {
                printPrettyJsonBlock(mapper, "GLOBAL BLUEPRINT (SUBSYSTEMS & PIPELINES)", macroJson);
            }
            System.out.println("\n--- Identified Florida Lottery Subsystems ---");
            JsonNode subsystems = macroJson.path("subsystems");
            if (subsystems.isArray()) {
                for (JsonNode s : subsystems) {
                    System.out.printf(" • [%s] %s%n", s.path("id").asText(""), s.path("name").asText(""));
                }
            }

            // Step 3: Object-Level World-View Synthesis (Per Schema & Package)
            System.out.println("\n[Phase 3] Generating 2-6 Sentence World-View Descriptions for All Objects...");
            Map<String, ObjectNode> enrichedRoutinesByLookupKey = new HashMap<>();
            Map<String, ObjectNode> packageWorldViews = new TreeMap<>();
            Map<String, ObjectNode> tableWorldViews = new TreeMap<>();
            Set<String> completedSchemas = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            File partialProgressFile = new File(schemasDirPath + "\\DbCache_Ecosystem_WorldView.partial.json");
            boolean canResume = Boolean.parseBoolean(argMap.getOrDefault("resume", "true"));
            if (canResume && partialProgressFile.exists()) {
                try {
                    JsonNode partialRoot = mapper.readTree(partialProgressFile);
                    JsonNode compArr = partialRoot.path("CompletedSchemas");
                    if (compArr.isArray()) {
                        compArr.forEach(s -> completedSchemas.add(s.asText().toUpperCase()));
                    }
                    JsonNode partialPkgs = partialRoot.path("PackageWorldViews");
                    if (partialPkgs.isObject()) {
                        Iterator<String> it = partialPkgs.fieldNames();
                        while (it.hasNext()) {
                            String k = it.next();
                            packageWorldViews.put(k, (ObjectNode) partialPkgs.get(k));
                        }
                    }
                    JsonNode partialTbls = partialRoot.path("TableWorldViews");
                    if (partialTbls.isObject()) {
                        Iterator<String> it = partialTbls.fieldNames();
                        while (it.hasNext()) {
                            String k = it.next();
                            tableWorldViews.put(k, (ObjectNode) partialTbls.get(k));
                        }
                    }
                    JsonNode partialRoutines = partialRoot.path("EnrichedRoutines");
                    if (partialRoutines.isObject()) {
                        Iterator<String> it = partialRoutines.fieldNames();
                        while (it.hasNext()) {
                            String k = it.next();
                            enrichedRoutinesByLookupKey.put(k, (ObjectNode) partialRoutines.get(k));
                        }
                    }
                    System.out.printf("   [Resume Mode] Loaded %d schemas already completed: %s%n",
                            completedSchemas.size(), completedSchemas);
                } catch (Exception ex) {
                    System.out.println("   [Resume Mode] Notice: Could not read partial progress, starting fresh.");
                }
            }

            int targetChunkSize = Integer.parseInt(argMap.getOrDefault("chunkSize", "5"));
            int maxRoutinesPerChunk = Integer.parseInt(argMap.getOrDefault("maxRoutines", "40"));

            for (Map.Entry<String, Map<String, List<JsonNode>>> schemaEntry : hierarchy.entrySet()) {
                String schemaName = schemaEntry.getKey().toUpperCase();
                if (completedSchemas.contains(schemaName)) {
                    System.out.printf(">>> Skipping Schema %-12s (Already completed in prior session)%n", schemaName);
                    continue;
                }

                Map<String, List<JsonNode>> packages = schemaEntry.getValue();
                int routineCount = packages.values().stream().mapToInt(List::size).sum();

                List<Map<String, List<JsonNode>>> chunks = partitionPackages(packages, targetChunkSize, maxRoutinesPerChunk);
                System.out.printf("%n>>> Synthesizing World View for Schema: %-12s (%d packages, %d routines, %d chunk%s)...%n",
                        schemaName, packages.size(), routineCount, chunks.size(), (chunks.size() == 1 ? "" : "s"));

                int chunkIdx = 1;
                for (Map<String, List<JsonNode>> chunk : chunks) {
                    int chunkRoutines = chunk.values().stream().mapToInt(List::size).sum();
                    if (chunks.size() > 1) {
                        System.out.printf("    - Processing Chunk %d/%d (%d packages, %d routines)... ",
                                chunkIdx, chunks.size(), chunk.size(), chunkRoutines);
                    } else {
                        System.out.printf("    - Processing all %d packages (%d routines)... ", chunk.size(), chunkRoutines);
                    }

                    List<JsonNode> schemaTables = tablesBySchema.getOrDefault(schemaName, Collections.emptyList());
                    String schemaDigest = buildSchemaDigest(schemaName, chunk, schemaTables, macroJson);
                    String schemaPrompt = buildSchemaDetailPrompt(schemaName, schemaDigest, macroJson);

                    long startSchemaMs = System.currentTimeMillis();
                    String schemaResponse = callGeminiRaw(httpClient, mapper, apiUrl, apiKey, schemaPrompt, 180);
                    long schemaElapsed = System.currentTimeMillis() - startSchemaMs;

                    if (schemaResponse != null && !schemaResponse.trim().isEmpty()) {
                        double chunkKbIn = schemaPrompt.getBytes(StandardCharsets.UTF_8).length / 1024.0;
                        double chunkKbOut = schemaResponse.getBytes(StandardCharsets.UTF_8).length / 1024.0;
                        JsonNode respParsed = mapper.readTree(schemaResponse);
                        parseAndMergeWorldViews(schemaName, respParsed, enrichedRoutinesByLookupKey, packageWorldViews, tableWorldViews);
                        System.out.printf("DONE in %d ms (%.2f KB in / %.2f KB out | %d in + %d out tokens | %.4f¢ [ $%.6f USD ])%n",
                                schemaElapsed, chunkKbIn, chunkKbOut, lastCallPromptTokens, lastCallCandidateTokens, lastCallCostPennies, (lastCallCostPennies / 100.0));
                        if (displayJsonBlocks) {
                            String chunkTitle = String.format("%s - CHUNK %d/%d (%d PKGS)", schemaName, chunkIdx, chunks.size(), chunk.size());
                            printPrettyJsonBlock(mapper, chunkTitle, respParsed);
                        }
                    } else {
                        System.err.printf("FAILED (Fallback defaults will apply for this chunk)%n");
                    }

                    chunkIdx++;
                    Thread.sleep(150); // Gentle pacing between chunks
                }

                completedSchemas.add(schemaName);

                // Checkpoint partial progress
                savePartialProgress(mapper, partialProgressFile, completedSchemas, packageWorldViews, tableWorldViews, enrichedRoutinesByLookupKey, macroJson);
            }

            // Step 4: Persist Results & Update Master Catalog
            System.out.println("\n[Phase 4] Persisting World-View Ecosystem Catalog & Updating Master Files...");

            // Create timestamped backup of original catalog
            File backupFile = new File(schemasDirPath + "\\DbCache_Ai_Researched_Catalog.backup.json");
            Files.copy(catalogFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Created safety backup of original catalog -> " + backupFile.getName());

            // Build enriched catalog
            ObjectNode worldViewCatalogRoot = mapper.createObjectNode();
            worldViewCatalogRoot.put("GeneratedAt", new Date().toString());
            worldViewCatalogRoot.put("TotalRoutines", routinesNode.size());
            worldViewCatalogRoot.set("EnterpriseSubsystems", macroJson.path("subsystems"));
            worldViewCatalogRoot.set("EndToEndPipelines", macroJson.path("pipelines"));

            // Package World Views
            ObjectNode pkgsNode = mapper.createObjectNode();
            for (Map.Entry<String, ObjectNode> e : packageWorldViews.entrySet()) {
                pkgsNode.set(e.getKey(), e.getValue());
            }
            worldViewCatalogRoot.set("PackageWorldViews", pkgsNode);

            // Table World Views
            ObjectNode tblsNode = mapper.createObjectNode();
            for (Map.Entry<String, ObjectNode> e : tableWorldViews.entrySet()) {
                tblsNode.set(e.getKey(), e.getValue());
            }
            worldViewCatalogRoot.set("TableWorldViews", tblsNode);

            // Enriched Routines
            ArrayNode updatedRoutinesArr = mapper.createArrayNode();
            for (JsonNode originalRoutine : routinesNode) {
                ObjectNode rObj = originalRoutine.deepCopy();
                String key = rObj.path("LookupKey").asText("");

                ObjectNode enrichment = enrichedRoutinesByLookupKey.get(key);
                if (enrichment != null) {
                    rObj.put("Subsystem", enrichment.path("subsystem").asText("Core Florida Lottery Operations"));
                    rObj.put("PipelinePhase", enrichment.path("pipelinePhase").asText("Transformation & Processing"));
                    rObj.put("WorldView", enrichment.path("worldView").asText(""));
                } else {
                    String schema = rObj.path("Schema").asText("");
                    String pkg = rObj.path("Package").asText("");
                    String obj = rObj.path("ObjectName").asText("");
                    String fallbackWv = String.format(
                            "Serves as an operational database component within schema %s. " +
                            "Collaborates with package %s to process lottery transactions for %s. " +
                            "Provides vital support for Florida Lottery gaming integrity and accounting auditability.",
                            schema, (pkg.equals("-") ? "standalone module" : pkg), obj);
                    rObj.put("Subsystem", schema + " Subsystem");
                    rObj.put("PipelinePhase", "Operational Processing");
                    rObj.put("WorldView", fallbackWv);
                }

                updatedRoutinesArr.add(rObj);
            }

            worldViewCatalogRoot.set("ResearchedRoutines", updatedRoutinesArr);

            // 1. Write dedicated World-View JSON
            File outputFile = new File(outputPath);
            mapper.writeValue(outputFile, worldViewCatalogRoot);
            System.out.printf("Saved Dedicated Ecosystem World-View JSON -> %s (%.2f KB)%n",
                    outputFile.getAbsolutePath(), (outputFile.length() / 1024.0));

            // 2. Dual-write in-place to DbCache_Ai_Researched_Catalog.json
            ObjectNode updatedMaster = mapper.createObjectNode();
            updatedMaster.put("GeneratedAt", new Date().toString());
            updatedMaster.put("TotalRoutines", updatedRoutinesArr.size());
            updatedMaster.set("EnterpriseSubsystems", macroJson.path("subsystems"));
            updatedMaster.set("PackageWorldViews", pkgsNode);
            updatedMaster.set("TableWorldViews", tblsNode);
            updatedMaster.set("ResearchedRoutines", updatedRoutinesArr);

            mapper.writeValue(catalogFile, updatedMaster);
            System.out.printf("Updated Master Catalog in-place with World-View Data -> %s (%.2f KB)%n",
                    catalogFile.getAbsolutePath(), (catalogFile.length() / 1024.0));

            if (partialProgressFile.exists()) {
                partialProgressFile.delete();
            }

            double totalDollars = totalCostInPennies / 100.0;
            System.out.println("\n=========================================================================");
            System.out.println("SUCCESS: Florida Lottery Ecosystem World View Fully Synthesized!");
            System.out.printf("Total Routines Enriched: %d%n", updatedRoutinesArr.size());
            System.out.printf("Total Packages Enriched: %d%n", packageWorldViews.size());
            System.out.printf("Total Tables Mapped:     %d%n", tableWorldViews.size());
            System.out.printf("Total Network I/O:       %.2f KB in / %.2f KB out (%.2f MB total)%n",
                    (totalBytesIn / 1024.0), (totalBytesOut / 1024.0), ((totalBytesIn + totalBytesOut) / (1024.0 * 1024.0)));
            System.out.printf("Total Prompt Tokens:     %d%n", totalPromptTokens);
            System.out.printf("Total Output Tokens:     %d%n", totalCandidateTokens);
            System.out.printf("TOTAL API COST:          %.4f Pennies ( $%.4f USD )%n", totalCostInPennies, totalDollars);
            System.out.println("=========================================================================");

        } catch (Exception e) {
            System.err.println("CRITICAL: World View synthesis failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildGlobalEcosystemDigest(
            Map<String, Map<String, List<JsonNode>>> hierarchy,
            Map<String, Set<String>> tablesRead,
            Map<String, Set<String>> tablesWritten,
            Map<String, Set<String>> packagesCalled) {

        StringBuilder sb = new StringBuilder();
        sb.append("# FLORIDA LOTTERY DATABASE ESTATE TOPOLOGICAL DIGEST\n\n");

        for (Map.Entry<String, Map<String, List<JsonNode>>> se : hierarchy.entrySet()) {
            String schema = se.getKey();
            Map<String, List<JsonNode>> pkgs = se.getValue();

            sb.append(String.format("## SCHEMA: %s (%d packages/modules)\n", schema, pkgs.size()));
            Set<String> rTbls = tablesRead.getOrDefault(schema, Collections.emptySet());
            Set<String> wTbls = tablesWritten.getOrDefault(schema, Collections.emptySet());
            Set<String> cPkgs = packagesCalled.getOrDefault(schema, Collections.emptySet());

            if (!rTbls.isEmpty()) sb.append("  Tables Read: ").append(String.join(", ", limitCollection(rTbls, 15))).append("\n");
            if (!wTbls.isEmpty()) sb.append("  Tables Written: ").append(String.join(", ", limitCollection(wTbls, 15))).append("\n");
            if (!cPkgs.isEmpty()) sb.append("  External Calls: ").append(String.join(", ", limitCollection(cPkgs, 10))).append("\n");

            for (Map.Entry<String, List<JsonNode>> pe : pkgs.entrySet()) {
                String pkgName = pe.getKey();
                List<JsonNode> routines = pe.getValue();
                sb.append(String.format("  - PACKAGE: %s (%d routines)\n", pkgName, routines.size()));

                // List routine names and high-level 1-line WHAT summary
                for (JsonNode r : routines) {
                    String rName = r.path("ObjectName").asText("");
                    String what = r.path("Description").path("What").asText(r.path("Description").path("what").asText(""));
                    if (what.length() > 100) what = what.substring(0, 100) + "...";
                    sb.append(String.format("      * %s: %s\n", rName, what));
                }
            }
            sb.append("\n");
        }

        String result = sb.toString();
        return result;
    }

    private static String buildMacroPrompt(String digest) {
        String prompt = String.format("""
            You are the Chief Enterprise Data Architect for the Florida Lottery.
            Analyze the complete topological digest of all 12 database schemas (1,582 routines) below.
            
            ```text
            %s
            ```
            
            Synthesize the OVERARCHING ENTERPRISE WORLD VIEW of the Florida Lottery database ecosystem.
            Word all descriptions in clear, professional plain English so that an entry-level programmer or Business Analyst (BA) can instantly understand.
            
            Return a strict JSON object with this exact structure:
            {
              "executiveOverview": "2 to 4 paragraphs explaining the holistic Florida Lottery database architecture, its primary operational missions (Retailer Accounting, Draw Operations, Second Chance Gaming, General Ledger reconciliation, Data Warehousing), and how data flows through the ecosystem.",
              "subsystems": [
                {
                  "id": "SYS-1",
                  "name": "Subsystem Name (e.g. Retailer Accounting & Weekly Settlement)",
                  "leadSchemas": ["ARS", "RETAILER", "TRANSLATOR"],
                  "description": "3 to 5 sentences describing what this business subsystem accomplishes from end-to-end.",
                  "businessImpact": "Financial, statutory, or operational impact."
                }
              ],
              "pipelines": [
                {
                  "pipelineName": "Pipeline Name (e.g. Nightly Terminal Transaction Landing to General Ledger Sweep)",
                  "chronology": "STAGE -> TRANSLATOR -> ARS -> GL -> DWDOL",
                  "description": "Step-by-step plain English explanation of the data lifecycle across schemas."
                }
              ]
            }
            """, digest);

        String result = prompt;
        return result;
    }

    private static String buildSchemaDigest(String schemaName, Map<String, List<JsonNode>> packages, List<JsonNode> schemaTables, JsonNode macroJson) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("SCHEMA: %s\n", schemaName));

        for (Map.Entry<String, List<JsonNode>> pe : packages.entrySet()) {
            String pkgName = pe.getKey();
            List<JsonNode> routines = pe.getValue();
            sb.append(String.format("PACKAGE: %s\n", pkgName));

            for (JsonNode r : routines) {
                String rName = r.path("ObjectName").asText("");
                String what = r.path("Description").path("What").asText(r.path("Description").path("what").asText(""));
                String why = r.path("Description").path("Why").asText(r.path("Description").path("why").asText(""));
                String how = r.path("Description").path("How").asText(r.path("Description").path("how").asText(""));
                String deps = r.path("Dependencies").asText("");
                if (!how.isEmpty()) {
                    sb.append(String.format("  ROUTINE: %s\n    WHAT: %s\n    WHY: %s\n    HOW: %s\n    DEPS: %s\n", rName, what, why, how, deps));
                } else {
                    sb.append(String.format("  ROUTINE: %s\n    WHAT: %s\n    WHY: %s\n    DEPS: %s\n", rName, what, why, deps));
                }
            }
        }

        if (schemaTables != null && !schemaTables.isEmpty()) {
            sb.append("\nTABLES & PERSISTENCE ENTITIES:\n");
            for (JsonNode tbl : schemaTables) {
                String tName = tbl.path("TableName").asText(tbl.path("Name").asText("")).toUpperCase();
                String tType = tbl.path("TableType").asText(tbl.path("Type").asText("RELATIONAL TABLE"));
                String comments = tbl.path("Comments").asText("");
                boolean isExt = tbl.path("IsExternal").asBoolean(false) || tType.toUpperCase().contains("EXTERNAL");
                if (isExt) {
                    JsonNode ext = tbl.path("ExternalTable");
                    String driver = ext.path("DriverType").asText("ORACLE_LOADER");
                    String dir = ext.path("DefaultDirectory").asText("");
                    sb.append(String.format("  - TABLE: %s [EXTERNAL TABLE | Loader: %s | Directory: %s]\n", tName, driver, dir));
                    JsonNode locs = ext.path("Locations");
                    if (locs.isArray() && !locs.isEmpty()) {
                        List<String> files = new ArrayList<>();
                        for (JsonNode l : locs) {
                            String fn = l.path("FileName").asText("");
                            if (!fn.isEmpty()) files.add(fn);
                        }
                        if (!files.isEmpty()) {
                            sb.append("      Source Files: ").append(String.join(", ", files)).append("\n");
                        }
                    }
                    String accessParams = ext.path("AccessParameters").asText("");
                    if (!accessParams.isEmpty()) {
                        String cleanParams = accessParams.replace("\n", " ").trim();
                        if (cleanParams.length() > 80) cleanParams = cleanParams.substring(0, 80) + "...";
                        sb.append("      Access Parameters: ").append(cleanParams).append("\n");
                    }
                } else {
                    sb.append(String.format("  - TABLE: %s [%s]\n", tName, tType));
                }
                if (!comments.isEmpty()) {
                    sb.append("      Comments: ").append(comments).append("\n");
                }
            }
        }

        String result = sb.toString();
        return result;
    }

    private static String buildSchemaDetailPrompt(String schemaName, String schemaDigest, JsonNode macroJson) {
        String prompt = String.format("""
            You are the Senior Enterprise Software Architect for the Florida Lottery.
            Based on the Master Enterprise Architecture Blueprint, synthesize the WORLD-VIEW descriptions for all Packages, Routines, and Tables in schema '%s'.
            
            SCHEMA INVENTORY & TECHNICAL SPECIFICATIONS:
            ```text
            %s
            ```
            
            IMPORTANT CLARITY GUIDELINE:
            Write all descriptions in 2 to 6 sentences of clear, professional plain English so that an entry-level programmer or Business Analyst can easily grasp:
             - Exactly WHERE this object fits into the broader Florida Lottery ecosystem (birds-eye view).
             - Upstream suppliers (where the data comes from) and downstream consumers (who depends on it).
             - The business intent and operational schedule (e.g. nightly batch, weekly billing sweep, real-time web ingress).
            
            Return a strict JSON response adhering to this schema:
            {
              "packages": [
                {
                  "packageName": "PACKAGE_NAME",
                  "subsystem": "Name of Subsystem",
                  "pipelinePhase": "Phase (e.g. Ingestion, Processing, Reconciliation)",
                  "worldView": "2 to 6 plain-English sentences explaining the package's role in the entire ecosystem."
                }
              ],
              "routines": [
                {
                  "packageName": "PACKAGE_NAME",
                  "routineName": "ROUTINE_NAME",
                  "subsystem": "Name of Subsystem",
                  "pipelinePhase": "Phase (e.g. Stage Loader, Commission Calculation, Ledger Posting)",
                  "worldView": "2 to 6 plain-English sentences explaining this specific routine's place in the macro data pipeline."
                }
              ],
              "tables": [
                {
                  "tableName": "TABLE_NAME",
                  "tableRole": "Role (e.g. Raw Ingestion Landing, Master Dimension, Ledger Spine, Transaction Staging)",
                  "worldView": "2 to 4 plain-English sentences explaining what this table stores and who depends on it across schemas."
                }
              ]
            }
            """, schemaName, schemaDigest);

        String result = prompt;
        return result;
    }

    private static void parseAndMergeWorldViews(
            String schemaName,
            JsonNode responseJson,
            Map<String, ObjectNode> enrichedRoutines,
            Map<String, ObjectNode> packageWorldViews,
            Map<String, ObjectNode> tableWorldViews) {

        // 1. Packages
        JsonNode pkgs = responseJson.path("packages");
        if (pkgs.isArray()) {
            for (JsonNode p : pkgs) {
                String pkgName = p.path("packageName").asText("STANDALONE").toUpperCase();
                String key = schemaName + "." + pkgName;
                packageWorldViews.put(key, (ObjectNode) p);
            }
        }

        // 2. Routines
        JsonNode routines = responseJson.path("routines");
        if (routines.isArray()) {
            for (JsonNode r : routines) {
                String pkgName = r.path("packageName").asText("STANDALONE").toUpperCase();
                String rName = r.path("routineName").asText("").toUpperCase();
                String lookupKey = String.format("%s.%s.%s", schemaName, (pkgName.equals("STANDALONE") ? "-" : pkgName), rName);
                enrichedRoutines.put(lookupKey, (ObjectNode) r);
            }
        }

        // 3. Tables
        JsonNode tables = responseJson.path("tables");
        if (tables.isArray()) {
            for (JsonNode t : tables) {
                String tblName = t.path("tableName").asText("").toUpperCase();
                String key = schemaName + "." + tblName;
                tableWorldViews.put(key, (ObjectNode) t);
            }
        }
    }

    private static String callGeminiRaw(HttpClient client, ObjectMapper mapper, String apiUrl, String apiKey, String prompt, int timeoutSec) {
        String targetUrl = apiKey.trim().isEmpty() ? apiUrl : (apiUrl + "?key=" + apiKey.trim());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (isFreeTierMode) {
                    long now = System.currentTimeMillis();
                    long timeSinceLast = now - lastRequestTimeMs;
                    if (lastRequestTimeMs > 0 && timeSinceLast < 4100L) {
                        try {
                            Thread.sleep(4100L - timeSinceLast);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    lastRequestTimeMs = System.currentTimeMillis();
                }

                ObjectNode root = mapper.createObjectNode();
                ArrayNode contents = mapper.createArrayNode();
                ObjectNode contentObj = mapper.createObjectNode();
                ArrayNode parts = mapper.createArrayNode();
                ObjectNode textPart = mapper.createObjectNode();
                textPart.put("text", prompt);
                parts.add(textPart);
                contentObj.set("parts", parts);
                contents.add(contentObj);
                root.set("contents", contents);

                ObjectNode genConfig = mapper.createObjectNode();
                genConfig.put("response_mime_type", "application/json");
                root.set("generationConfig", genConfig);

                String payload = mapper.writeValueAsString(root);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey.trim())
                        .timeout(Duration.ofSeconds(timeoutSec))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    byte[] reqBytes = payload.getBytes(StandardCharsets.UTF_8);
                    byte[] respBytes = (response.body() != null) ? response.body().getBytes(StandardCharsets.UTF_8) : new byte[0];
                    totalBytesIn += reqBytes.length;
                    totalBytesOut += respBytes.length;

                    JsonNode respJson = mapper.readTree(response.body());
                    JsonNode candidates = respJson.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode usage = respJson.path("usageMetadata");
                        long promptTokens = usage.path("promptTokenCount").asLong(2000);
                        long candidateTokens = usage.path("candidatesTokenCount").asLong(1500);

                        totalPromptTokens += promptTokens;
                        totalCandidateTokens += candidateTokens;
                        double callCost = (promptTokens * INPUT_TOKEN_COST_PENNIES) + (candidateTokens * OUTPUT_TOKEN_COST_PENNIES);
                        totalCostInPennies += callCost;
                        lastCallPromptTokens = promptTokens;
                        lastCallCandidateTokens = candidateTokens;
                        lastCallCostPennies = callCost;

                        ExtractAndResearchProcsInventory.recordModelRequest(activeModelName, 200, promptTokens, candidateTokens, mapper);

                        String jsonText = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");
                        return jsonText;
                    }
                } else if (response.statusCode() == 429) {
                    ExtractAndResearchProcsInventory.recordModel429Exhaustion(activeModelName, mapper);
                    System.out.printf("   [Rate Limit 429] Daily quota limit reached on %s -> Auto-failing over...%n", activeModelName);
                    boolean failedOver = ExtractAndResearchProcsInventory.tryFailoverToNextModel(client, mapper, apiKey);
                    if (failedOver) {
                        activeModelName = ExtractAndResearchProcsInventory.getActiveModelName();
                        targetUrl = apiKey.trim().isEmpty() ? ExtractAndResearchProcsInventory.getCurrentActiveApiUrl() : (ExtractAndResearchProcsInventory.getCurrentActiveApiUrl() + "?key=" + apiKey.trim());
                        attempt = 0;
                        continue;
                    }
                    long backoff = isFreeTierMode ? (6000L * attempt) : (RETRY_DELAY_MS * attempt);
                    Thread.sleep(backoff);
                } else {
                    System.err.printf("   [API Error HTTP %d]: %s%n", response.statusCode(), response.body());
                }
            } catch (Exception ex) {
                System.err.printf("   [Connection Exception]: %s%n", ex.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ignored) {}
            }
        }

        String result = null;
        return result;
    }

    private static void extractTableReferences(
            String schema, String deps,
            Map<String, Set<String>> tablesRead,
            Map<String, Set<String>> tablesWritten,
            Map<String, Set<String>> packagesCalled) {

        if (deps == null || deps.isEmpty()) return;

        // Reads: [TBL1, TBL2] | Writes: [TBL3] | Calls: [PKG.PROC]
        String[] tokens = deps.split("\\|");
        for (String tok : tokens) {
            String clean = tok.trim();
            if (clean.startsWith("Reads:")) {
                extractBracketItems(clean).forEach(t -> tablesRead.computeIfAbsent(schema, k -> new TreeSet<>()).add(t));
            } else if (clean.startsWith("Writes:") || clean.startsWith("Updates:")) {
                extractBracketItems(clean).forEach(t -> tablesWritten.computeIfAbsent(schema, k -> new TreeSet<>()).add(t));
            } else if (clean.startsWith("Calls:")) {
                extractBracketItems(clean).forEach(p -> packagesCalled.computeIfAbsent(schema, k -> new TreeSet<>()).add(p));
            }
        }
    }

    private static List<String> extractBracketItems(String section) {
        List<String> list = new ArrayList<>();
        int open = section.indexOf('[');
        int close = section.lastIndexOf(']');
        if (open >= 0 && close > open) {
            String inner = section.substring(open + 1, close).trim();
            if (!inner.isEmpty() && !inner.equalsIgnoreCase("None") && !inner.equalsIgnoreCase("N/A")) {
                for (String part : inner.split(",")) {
                    String clean = part.trim();
                    int paren = clean.indexOf('(');
                    if (paren > 0) clean = clean.substring(0, paren).trim();
                    if (!clean.isEmpty()) list.add(clean);
                }
            }
        }
        return list;
    }

    private static List<String> limitCollection(Collection<String> col, int limit) {
        List<String> list = new ArrayList<>();
        int count = 0;
        for (String s : col) {
            list.add(s);
            if (++count >= limit) {
                list.add("...");
                break;
            }
        }
        return list;
    }

    private static List<Map<String, List<JsonNode>>> partitionPackages(Map<String, List<JsonNode>> packages, int maxPkgsPerChunk, int maxRoutinesPerChunk) {
        List<Map<String, List<JsonNode>>> chunks = new ArrayList<>();
        Map<String, List<JsonNode>> current = new LinkedHashMap<>();
        int currentPkgCount = 0;
        int currentRoutineCount = 0;

        for (Map.Entry<String, List<JsonNode>> e : packages.entrySet()) {
            int routinesInPkg = e.getValue() != null ? e.getValue().size() : 0;

            // If adding this package exceeds package count OR routine count, flush current chunk
            if (!current.isEmpty() && (currentPkgCount >= maxPkgsPerChunk || (currentRoutineCount + routinesInPkg) > maxRoutinesPerChunk)) {
                chunks.add(current);
                current = new LinkedHashMap<>();
                currentPkgCount = 0;
                currentRoutineCount = 0;
            }

            current.put(e.getKey(), e.getValue());
            currentPkgCount++;
            currentRoutineCount += routinesInPkg;
        }

        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private static void savePartialProgress(
            ObjectMapper mapper,
            File file,
            Set<String> completedSchemas,
            Map<String, ObjectNode> packageWorldViews,
            Map<String, ObjectNode> tableWorldViews,
            Map<String, ObjectNode> enrichedRoutines,
            JsonNode macroJson) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("LastUpdated", new Date().toString());
            ArrayNode compArr = mapper.createArrayNode();
            completedSchemas.forEach(compArr::add);
            root.set("CompletedSchemas", compArr);
            root.set("MacroJson", macroJson);

            ObjectNode pkgs = mapper.createObjectNode();
            packageWorldViews.forEach(pkgs::set);
            root.set("PackageWorldViews", pkgs);

            ObjectNode tbls = mapper.createObjectNode();
            tableWorldViews.forEach(tbls::set);
            root.set("TableWorldViews", tbls);

            ObjectNode routines = mapper.createObjectNode();
            enrichedRoutines.forEach(routines::set);
            root.set("EnrichedRoutines", routines);

            mapper.writeValue(file, root);
        } catch (Exception ex) {
            System.err.println("Notice: Could not checkpoint partial progress: " + ex.getMessage());
        }
    }

    private static Map<String, String> parseCommandLineArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) return map;
        for (String arg : args) {
            if (arg.startsWith("-") && arg.contains("=")) {
                String clean = arg.substring(1);
                int eqIdx = clean.indexOf('=');
                map.put(clean.substring(0, eqIdx).trim(), clean.substring(eqIdx + 1).trim());
            }
        }
        return map;
    }

    private static void printPrettyJsonBlock(ObjectMapper mapper, String title, JsonNode jsonNode) {
        try {
            String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            System.out.println("┌── [WORLD-VIEW SYNTHESIS: " + title + "] " + "─".repeat(Math.max(10, 60 - title.length())) + "┐");
            for (String line : pretty.split("\\R")) {
                System.out.println("│ " + line);
            }
            System.out.println("└──" + "─".repeat(70) + "┘");
        } catch (Exception ex) {
            System.out.println("   [JSON Preview]: " + jsonNode.toString());
        }
    }
}
