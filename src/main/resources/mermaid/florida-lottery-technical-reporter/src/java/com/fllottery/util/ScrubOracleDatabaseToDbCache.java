package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Oracle Database Catalog Extractor & DbCache Updater for Florida Lottery.
 * Connects directly to Oracle (PROD / DW / STAGE) via JDBC using assigned enterprise accounts
 * and performs high-speed catalog extraction to update DbCache.json.
 *
 * Capabilities:
 *  1. Tables, Columns, Comments, Data Types, Precision, Scale, Nullability, Defaults.
 *  2. Table Volume Sizing: NUM_ROWS, AVG_ROW_LEN, BLOCKS, and LAST_ANALYZED timestamps.
 *  3. Table Indexes & Performance: ALL_INDEXES and ALL_IND_COLUMNS (Unique vs Non-Unique, B-Tree vs Bitmap, Column Sequence).
 *  4. Database Triggers: ALL_TRIGGERS (Name, Type, Event, Status, Description).
 *  5. Comprehensive Constraints: Primary Keys (P), Foreign Keys (R) with referenced tables/columns, Unique (U), and Check (C).
 *  6. Standalone Procedures & Functions: ALL_OBJECTS, ALL_SOURCE, and typed argument signatures from ALL_ARGUMENTS.
 *  7. Packages & Member Sub-Routines: ALL_OBJECTS, ALL_SOURCE (spec/body), argument signatures, and sub-routine parsing.
 *  8. Compiled Object Dependencies: ALL_DEPENDENCIES (authoritative lineage and call graph).
 *  9. Sequence Generators: ALL_SEQUENCES (Name, Min, Max, Increment, Cache Size, Last Number).
 * 10. Multi-Database Merging: Merges runs (e.g. OLTP and DW) safely without wiping other database branches.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @since 2026-09-26
 */
public class ScrubOracleDatabaseToDbCache {

    // Default Output Path
    private static final String DEFAULT_OUTPUT_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json";

    // Default Target Schemas across all databases
    public static final Set<String> DEFAULT_TARGET_SCHEMAS = new LinkedHashSet<>(Arrays.asList(
            "ARS", "COMMON", "CSTS", "DWDOL", "DOLEMP", "GL", "INET",
            "INET2", "LIDS", "LST", "PR", "RETAILER", "STAGE", "TMS", "TRANSLATOR", "VP"
    ));

    // =========================================================================
    // 2. CONFIGURABLE DATABASE TARGET DEFINITION
    // =========================================================================
    public static class DbTargetConfig {
        public String dbName;
        public String dbUser;
        public String dbPass;
        public String dbUrl;
        public String dboName;
        public Set<String> targetSchemas;

        public DbTargetConfig(String dbName, String dbUser, String dbPass, String dbUrl) {
            this(dbName, dbUser, dbPass, dbUrl, dbName, new LinkedHashSet<>(DEFAULT_TARGET_SCHEMAS));
        }

        public DbTargetConfig(String dbName, String dbUser, String dbPass, String dbUrl, String dboName) {
            this(dbName, dbUser, dbPass, dbUrl, dboName, new LinkedHashSet<>(DEFAULT_TARGET_SCHEMAS));
        }

        public DbTargetConfig(String dbName, String dbUser, String dbPass, String dbUrl, String dboName, Set<String> targetSchemas) {
            this.dbName = dbName;
            this.dbUser = dbUser;
            this.dbPass = dbPass;
            this.dbUrl = dbUrl;
            this.dboName = (dboName != null && !dboName.trim().isEmpty()) ? dboName.trim().toUpperCase() : dbName.trim().toUpperCase();
            this.targetSchemas = (targetSchemas != null && !targetSchemas.isEmpty()) ? targetSchemas : new LinkedHashSet<>(DEFAULT_TARGET_SCHEMAS);
        }
    }

    // =========================================================================
    // 3. TARGET DATABASES ARRAY LIST (ADD / EDIT YOUR DATABASE TARGETS HERE)
    // =========================================================================
    public static List<DbTargetConfig> buildConfiguredDatabaseTargets() {
        List<DbTargetConfig> list = new ArrayList<>();

        // 1. PRODOLTP_DR (Production Disaster Recovery OLTP)
        list.add(new DbTargetConfig(
                "PRODOLTP_DR",
                "stokesr",
                "Ociprod76oltp##",
                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=ociolpodb1-dr.dol.state.fl.us)(PORT=15210))(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=PRODCDB_PRODOLTP.paas.oracle.com)))"
        ));

        // 2. PRODDW_DR (Production Disaster Recovery Data Warehouse)
        list.add(new DbTargetConfig(
                "PRODDW_DR",
                "stokesr",
                "Ociprod7632dw##",
                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=ociolpodb1-dr.dol.state.fl.us)(PORT=15210))(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=PRODCDB_PRODDW.paas.oracle.com)))"
        ));

//        // 3. DEVDW (Development Data Warehouse)
//        list.add(new DbTargetConfig(
//                "DEVDW",
//                "stokesr",
//                "PO_q28dzde@FQI}B",
//                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=ocioldodb1)(PORT=15210))(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=DEVCDB_DEVDW.paas.oracle.com)))"
//        ));
//
//        // 4. DEVOLTP (Development OLTP)
//        list.add(new DbTargetConfig(
//                "DEVOLTP",
//                "stokesr",
//                "$$cN8SNz8Q[xBU$Q",
//                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=ocioldodb1)(PORT=15210))(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=DEVCDB_DEVOLTP.paas.oracle.com)))"
//        ));

        List<DbTargetConfig> result = list;
        return result;
    }

    // =========================================================================
    // CONFIGURATION & METRICS DATA STRUCTURES
    // =========================================================================
    public static class DatabaseTarget {
        private final String envKey;
        private final String dbName;
        private final String dboName;
        private final String jdbcUrl;
        private String username;
        private String password;
        private final Set<String> targetSchemas;

        public DatabaseTarget(String envKey, String dbName, String dboName, String jdbcUrl,
                              String username, String password, Set<String> targetSchemas) {
            this.envKey = envKey;
            this.dbName = dbName;
            this.dboName = dboName;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.targetSchemas = targetSchemas;
        }

        public String getEnvKey() {
            String result = envKey;
            return result;
        }

        public String getDbName() {
            String result = dbName;
            return result;
        }

        public String getDboName() {
            String result = dboName;
            return result;
        }

        public String getJdbcUrl() {
            String result = jdbcUrl;
            return result;
        }

        public String getUsername() {
            String result = username;
            return result;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            String result = password;
            return result;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Set<String> getTargetSchemas() {
            Set<String> result = targetSchemas;
            return result;
        }
    }

    public static class ExtractionOptions {
        private final boolean mergeMode;
        private final boolean fetchSource;
        private final boolean fetchConstraints;
        private final boolean fetchIndexes;
        private final boolean fetchTriggers;
        private final boolean fetchSequences;
        private final boolean fetchDependencies;
        private final boolean fetchArguments;
        private final String outputPath;

        public ExtractionOptions(boolean mergeMode, boolean fetchSource, boolean fetchConstraints,
                                 boolean fetchIndexes, boolean fetchTriggers, boolean fetchSequences,
                                 boolean fetchDependencies, boolean fetchArguments, String outputPath) {
            this.mergeMode = mergeMode;
            this.fetchSource = fetchSource;
            this.fetchConstraints = fetchConstraints;
            this.fetchIndexes = fetchIndexes;
            this.fetchTriggers = fetchTriggers;
            this.fetchSequences = fetchSequences;
            this.fetchDependencies = fetchDependencies;
            this.fetchArguments = fetchArguments;
            this.outputPath = outputPath;
        }

        public boolean isMergeMode() {
            boolean result = mergeMode;
            return result;
        }

        public boolean isFetchSource() {
            boolean result = fetchSource;
            return result;
        }

        public boolean isFetchConstraints() {
            boolean result = fetchConstraints;
            return result;
        }

        public boolean isFetchIndexes() {
            boolean result = fetchIndexes;
            return result;
        }

        public boolean isFetchTriggers() {
            boolean result = fetchTriggers;
            return result;
        }

        public boolean isFetchSequences() {
            boolean result = fetchSequences;
            return result;
        }

        public boolean isFetchDependencies() {
            boolean result = fetchDependencies;
            return result;
        }

        public boolean isFetchArguments() {
            boolean result = fetchArguments;
            return result;
        }

        public String getOutputPath() {
            String result = outputPath;
            return result;
        }
    }

    public static class ScrubMetrics {
        private final String dbName;
        private final String envKey;
        private final boolean success;
        private final int schemasFound;
        private final int schemasAttempted;
        private final int tablesExtracted;
        private final int viewsExtracted;
        private final int procsExtracted;
        private final int funcsExtracted;
        private final int pkgsExtracted;
        private final int seqsExtracted;
        private final long elapsedMs;
        private final String errorMessage;

        public ScrubMetrics(String dbName, String envKey, boolean success, int schemasFound, int schemasAttempted,
                            int tablesExtracted, int viewsExtracted, int procsExtracted,
                            int funcsExtracted, int pkgsExtracted, int seqsExtracted,
                            long elapsedMs, String errorMessage) {
            this.dbName = dbName;
            this.envKey = envKey;
            this.success = success;
            this.schemasFound = schemasFound;
            this.schemasAttempted = schemasAttempted;
            this.tablesExtracted = tablesExtracted;
            this.viewsExtracted = viewsExtracted;
            this.procsExtracted = procsExtracted;
            this.funcsExtracted = funcsExtracted;
            this.pkgsExtracted = pkgsExtracted;
            this.seqsExtracted = seqsExtracted;
            this.elapsedMs = elapsedMs;
            this.errorMessage = errorMessage;
        }

        public String getDbName() {
            String result = dbName;
            return result;
        }

        public String getEnvKey() {
            String result = envKey;
            return result;
        }

        public boolean isSuccess() {
            boolean result = success;
            return result;
        }

        public int getSchemasFound() {
            int result = schemasFound;
            return result;
        }

        public int getSchemasAttempted() {
            int result = schemasAttempted;
            return result;
        }

        public int getSchemasProcessed() {
            int result = schemasFound;
            return result;
        }

        public int getTablesExtracted() {
            int result = tablesExtracted;
            return result;
        }

        public int getViewsExtracted() {
            int result = viewsExtracted;
            return result;
        }

        public int getProcsExtracted() {
            int result = procsExtracted;
            return result;
        }

        public int getFuncsExtracted() {
            int result = funcsExtracted;
            return result;
        }

        public int getPkgsExtracted() {
            int result = pkgsExtracted;
            return result;
        }

        public int getSeqsExtracted() {
            int result = seqsExtracted;
            return result;
        }

        public long getElapsedMs() {
            long result = elapsedMs;
            return result;
        }

        public String getErrorMessage() {
            String result = errorMessage;
            return result;
        }
    }

    public static void main(String[] args) {
        Map<String, String> argMap = parseArgs(args);

        String envArg = argMap.getOrDefault("env", "").toLowerCase().trim();
        String outputPath = argMap.getOrDefault("output", DEFAULT_OUTPUT_PATH);
        String credsFileArg = argMap.getOrDefault("creds", "");
        String dbNameArg = argMap.getOrDefault("db", "").toUpperCase().trim();
        String dboNameArg = argMap.getOrDefault("dbo", "").toUpperCase().trim();
        String jdbcUrlArg = argMap.getOrDefault("url", "");
        boolean mergeMode = Boolean.parseBoolean(argMap.getOrDefault("merge", "true"));
        boolean fetchSource = Boolean.parseBoolean(argMap.getOrDefault("fetchSource", "true"));
        boolean fetchConstraints = Boolean.parseBoolean(argMap.getOrDefault("fetchConstraints", "true"));
        boolean fetchIndexes = Boolean.parseBoolean(argMap.getOrDefault("fetchIndexes", "true"));
        boolean fetchTriggers = Boolean.parseBoolean(argMap.getOrDefault("fetchTriggers", "true"));
        boolean fetchSequences = Boolean.parseBoolean(argMap.getOrDefault("fetchSequences", "true"));
        boolean fetchDependencies = Boolean.parseBoolean(argMap.getOrDefault("fetchDependencies", "true"));
        boolean fetchArguments = Boolean.parseBoolean(argMap.getOrDefault("fetchArguments", "true"));

        // Global target schema parsing
        String schemasArg = argMap.get("schemas");
        Set<String> defaultSchemas = new LinkedHashSet<>();
        if (schemasArg != null && !schemasArg.trim().isEmpty()) {
            for (String s : schemasArg.split(",")) {
                if (!s.trim().isEmpty()) {
                    defaultSchemas.add(s.trim().toUpperCase());
                }
            }
        } else {
            defaultSchemas.addAll(DEFAULT_TARGET_SCHEMAS);
        }

        Properties props = loadCredentialsProperties(credsFileArg);

        // Load configured database array list
        List<DbTargetConfig> configuredList = buildConfiguredDatabaseTargets();
        List<DatabaseTarget> targetsToScrub = new ArrayList<>();

        if (!jdbcUrlArg.isEmpty()) {
            // Explicit single Custom JDBC URL from CLI
            String customDb = !dbNameArg.isEmpty() ? dbNameArg : "CUSTOM_DB";
            String customDbo = !dboNameArg.isEmpty() ? dboNameArg : customDb;
            String user = resolveCredential(argMap.get("user"), argMap.get("username"), props.getProperty("user"));
            String pass = resolveCredential(argMap.get("password"), argMap.get("pass"), props.getProperty("password"));
            targetsToScrub.add(new DatabaseTarget(customDb.toLowerCase(), customDb, customDbo, jdbcUrlArg, user, pass, defaultSchemas));
        } else if (!dbNameArg.isEmpty()) {
            // Filter to specific database from configured list by name
            boolean found = false;
            for (DbTargetConfig cfg : configuredList) {
                if (cfg.dbName.equalsIgnoreCase(dbNameArg)) {
                    String user = resolveCredential(argMap.get("user"), cfg.dbUser, props.getProperty(cfg.dbName.toLowerCase() + ".user"));
                    String pass = resolveCredential(argMap.get("password"), cfg.dbPass, props.getProperty(cfg.dbName.toLowerCase() + ".password"));
                    targetsToScrub.add(new DatabaseTarget(cfg.dbName.toLowerCase(), cfg.dbName, cfg.dboName, cfg.dbUrl, user, pass, cfg.targetSchemas));
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.err.printf("Database [%s] not found in configured list. Available: ", dbNameArg);
                for (DbTargetConfig c : configuredList) System.err.print(c.dbName + " ");
                System.err.println();
                return;
            }
        } else if (!envArg.isEmpty() && !envArg.equals("all") && !envArg.equals("both")) {
            // Filter by env alias (e.g. prod, dev, devoltp, devdw, prodoltp, proddw)
            for (DbTargetConfig cfg : configuredList) {
                String key = cfg.dbName.toLowerCase();
                if (key.contains(envArg) || envArg.contains(key)) {
                    String user = resolveCredential(argMap.get(key + "User"), cfg.dbUser, props.getProperty(key + ".user"));
                    String pass = resolveCredential(argMap.get(key + "Password"), cfg.dbPass, props.getProperty(key + ".password"));
                    targetsToScrub.add(new DatabaseTarget(key, cfg.dbName, cfg.dboName, cfg.dbUrl, user, pass, cfg.targetSchemas));
                }
            }
            if (targetsToScrub.isEmpty()) {
                System.err.printf("No databases matched environment filter [%s].%n", envArg);
                return;
            }
        } else {
            // DEFAULT: Scrub ALL databases in the configured array list
            for (DbTargetConfig cfg : configuredList) {
                String key = cfg.dbName.toLowerCase();
                String user = resolveCredential(argMap.get(key + "User"), cfg.dbUser, props.getProperty(key + ".user"));
                String pass = resolveCredential(argMap.get(key + "Password"), cfg.dbPass, props.getProperty(key + ".password"));
                targetsToScrub.add(new DatabaseTarget(key, cfg.dbName, cfg.dboName, cfg.dbUrl, user, pass, cfg.targetSchemas));
            }
        }

        ExtractionOptions options = new ExtractionOptions(
                mergeMode, fetchSource, fetchConstraints, fetchIndexes,
                fetchTriggers, fetchSequences, fetchDependencies, fetchArguments, outputPath
        );

        System.out.println("=========================================================================");
        System.out.println("Florida Lottery Oracle Database Catalog Extractor & DbCache Updater");
        System.out.printf("Execution Mode:      MULTI-DATABASE ARRAY LIST (%d target(s) queued)%n", targetsToScrub.size());
        for (int i = 0; i < targetsToScrub.size(); i++) {
            DatabaseTarget t = targetsToScrub.get(i);
            System.out.printf("  Target [%d]:        %-16s (DBO: %-14s) -> %d Schemas%n",
                    (i + 1), t.getDbName(), t.getDboName(), t.getTargetSchemas().size());
        }
        System.out.println("Output File:         " + outputPath);
        System.out.println("Merge with Cache:    " + mergeMode);
        System.out.println("Extract Constraints: " + fetchConstraints);
        System.out.println("Extract Indexes:     " + fetchIndexes);
        System.out.println("Extract Triggers:    " + fetchTriggers);
        System.out.println("Extract Dependencies:" + fetchDependencies);
        System.out.println("Extract Sequences:   " + fetchSequences);
        System.out.println("Extract Signatures:  " + fetchArguments);
        System.out.println("Extract PL/SQL:      " + fetchSource);
        System.out.println("=========================================================================");

        // Interactive validation of credentials
        Console console = System.console();
        for (int i = 0; i < targetsToScrub.size(); i++) {
            DatabaseTarget t = targetsToScrub.get(i);
            if (t.getUsername() == null || t.getUsername().isEmpty()) {
                if (console != null) {
                    System.out.printf("Enter Oracle Username for [%s]: ", t.getDbName());
                    String u = console.readLine().trim();
                    t.setUsername(u);
                } else {
                    System.err.printf("ACTION REQUIRED: Missing username for [%s]. Please supply via properties file, CLI, or DbTargetConfig.%n",
                            t.getDbName());
                    return;
                }
            }
            if (t.getPassword() == null || t.getPassword().isEmpty()) {
                if (console != null) {
                    char[] passChars = console.readPassword("Enter Oracle Password for [%s @ %s]: ", t.getUsername(), t.getDbName());
                    t.setPassword(new String(passChars));
                } else {
                    System.err.printf("ACTION REQUIRED: Missing password for [%s]. Please supply via properties file, CLI, or DbTargetConfig.%n",
                            t.getDbName());
                    return;
                }
            }
        }

        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            try {
                if (!targetsToScrub.isEmpty()) {
                    DriverManager.getDriver(targetsToScrub.get(0).getJdbcUrl());
                }
            } catch (Exception ex) {
                System.out.println("Note: Oracle JDBC Driver registration via Class.forName was bypassed; proceeding with DriverManager.");
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        List<ScrubMetrics> metricsList = new ArrayList<>();
        long overallStart = System.currentTimeMillis();

        for (int i = 0; i < targetsToScrub.size(); i++) {
            DatabaseTarget target = targetsToScrub.get(i);
            ScrubMetrics metrics = scrubSingleDatabase(target, options, mapper, i + 1, targetsToScrub.size());
            metricsList.add(metrics);
        }

        printExecutiveSummary(metricsList, outputPath, overallStart);
    }

    private static ScrubMetrics scrubSingleDatabase(
            DatabaseTarget target,
            ExtractionOptions options,
            ObjectMapper mapper,
            int targetIndex,
            int totalTargets) {

        long start = System.currentTimeMillis();
        System.out.println();
        System.out.println("=========================================================================");
        System.out.printf("[TARGET %d of %d] Scrubbing Database: %s (DBO: %s)%n", targetIndex, totalTargets, target.getDbName(), target.getDboName());
        System.out.println("Target Schemas:      " + target.getTargetSchemas());
        System.out.println("JDBC Connection:     " + (target.getJdbcUrl().length() > 70 ? target.getJdbcUrl().substring(0, 70) + "..." : target.getJdbcUrl()));
        System.out.println("Database User:       " + target.getUsername());
        System.out.println("=========================================================================");

        try {
            System.out.printf("%nAttempting JDBC connection to %s as user [%s]...%n", target.getDbName(), target.getUsername());
            try (Connection conn = DriverManager.getConnection(target.getJdbcUrl(), target.getUsername(), target.getPassword())) {
                System.out.printf("Connected successfully to Oracle Database [%s]!%n", target.getDbName());
                DatabaseMetaData meta = conn.getMetaData();
                System.out.printf("Database Product:   %s %s%n", meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
                System.out.printf("Database Driver:    %s %s%n%n", meta.getDriverName(), meta.getDriverVersion());

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD HH24:MI:SS'");
                } catch (Exception ignored) {}

                ObjectNode databaseNode = mapper.createObjectNode();
                databaseNode.put("DatabaseName", target.getDbName());
                databaseNode.put("DboName", target.getDboName());
                ArrayNode schemasArray = mapper.createArrayNode();
                databaseNode.set("Schemas", schemasArray);

                int totalTables = 0;
                int totalViews = 0;
                int totalProcs = 0;
                int totalFuncs = 0;
                int totalPkgs = 0;
                int totalSeqs = 0;

                for (String schema : target.getTargetSchemas()) {
                    System.out.printf("--- [%s] Extracting Schema: %-12s ---%n", target.getDbName(), schema);
                    ObjectNode schemaNode = mapper.createObjectNode();
                    schemaNode.put("SchemaName", schema);
                    schemaNode.put("DboName", target.getDboName());

                    // Pre-fetch routine argument signatures for schema if enabled
                    Map<String, List<ObjectNode>> argumentSignaturesMap = options.isFetchArguments()
                            ? extractArgumentsForSchema(conn, schema) : new HashMap<>();

                    // 1. Tables, Columns, Sizing, Constraints, Indexes, Triggers
                    ArrayNode tablesArray = extractTablesForSchema(conn, schema,
                            options.isFetchConstraints(), options.isFetchIndexes(), options.isFetchTriggers());
                    schemaNode.set("Tables", tablesArray);
                    totalTables += tablesArray.size();

                    // 2. Views
                    ArrayNode viewsArray = extractViewsForSchema(conn, schema);
                    schemaNode.set("Views", viewsArray);
                    totalViews += viewsArray.size();

                    // Pre-fetch V$SQL performance telemetry for schema routines
                    Map<String, ObjectNode> runtimeMetricsMap = extractRuntimeMetricsForSchema(conn, schema);

                    // 3. Standalone Procedures
                    ArrayNode procsArray = extractStandaloneRoutines(conn, schema, "PROCEDURE",
                            options.isFetchSource(), argumentSignaturesMap, runtimeMetricsMap);
                    schemaNode.set("Procedures", procsArray);
                    totalProcs += procsArray.size();

                    // 4. Standalone Functions
                    ArrayNode funcsArray = extractStandaloneRoutines(conn, schema, "FUNCTION",
                            options.isFetchSource(), argumentSignaturesMap, runtimeMetricsMap);
                    schemaNode.set("Functions", funcsArray);
                    totalFuncs += funcsArray.size();

                    // 5. Packages & Member Sub-Routines
                    ArrayNode pkgsArray = extractPackagesForSchema(conn, schema,
                            options.isFetchSource(), argumentSignaturesMap, runtimeMetricsMap);
                    schemaNode.set("Packages", pkgsArray);
                    totalPkgs += pkgsArray.size();

                    // 6. Sequences
                    if (options.isFetchSequences()) {
                        ArrayNode seqArray = extractSequencesForSchema(conn, schema);
                        schemaNode.set("Sequences", seqArray);
                        totalSeqs += seqArray.size();
                    }

                    // 7. Compiled Dependencies
                    if (options.isFetchDependencies()) {
                        ArrayNode depArray = extractDependenciesForSchema(conn, schema);
                        schemaNode.set("Dependencies", depArray);
                    }

                    // 8. Object & Column Security Grants (ALL_TAB_PRIVS & ALL_COL_PRIVS)
                    ArrayNode tabPrivsArray = extractTablePrivilegesForSchema(conn, schema);
                    schemaNode.set("ObjectGrants", tabPrivsArray);

                    ArrayNode colPrivsArray = extractColumnPrivilegesForSchema(conn, schema);
                    schemaNode.set("ColumnGrants", colPrivsArray);

                    int schemaObjectCount = tablesArray.size() + viewsArray.size() + procsArray.size() + funcsArray.size() + pkgsArray.size() + totalSeqs;
                    if (schemaObjectCount > 0) {
                        schemasArray.add(schemaNode);
                        System.out.printf("   Completed [%s.%s]: %d Tables, %d Views, %d Procedures, %d Functions, %d Packages, %d Object Grants, %d Column Grants%n%n",
                                target.getDbName(), schema, tablesArray.size(), viewsArray.size(), procsArray.size(), funcsArray.size(), pkgsArray.size(), tabPrivsArray.size(), colPrivsArray.size());
                    } else {
                        System.out.printf("   Omitted [%s.%s]: Schema does not exist or has 0 objects in %s (will only appear in DBs where present).%n%n",
                                target.getDbName(), schema, target.getDbName());
                    }
                }

                // Merge or Save to DbCache.json
                saveOrMergeDbCache(options.getOutputPath(), databaseNode, mapper, options.isMergeMode());

                long elapsed = System.currentTimeMillis() - start;
                System.out.println("=========================================================================");
                System.out.printf("SUCCESS: Database [%s] scrub completed in %d ms (%.2f seconds)!%n",
                        target.getDbName(), elapsed, (elapsed / 1000.0));
                System.out.println("Updated DbCache File: " + options.getOutputPath());
                System.out.println("=========================================================================");

                ScrubMetrics result = new ScrubMetrics(target.getDbName(), target.getEnvKey(), true,
                        schemasArray.size(), target.getTargetSchemas().size(), totalTables, totalViews, totalProcs, totalFuncs, totalPkgs, totalSeqs, elapsed, null);
                return result;
            }
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - start;
            System.err.printf("%nDATABASE ACCESS ERROR on [%s] (ORA-%d): %s%n", target.getDbName(), e.getErrorCode(), e.getMessage());
            System.err.printf("ACTION REQUIRED: Check connectivity to %s (%s) or account credentials for [%s].%n",
                    target.getDbName(), target.getJdbcUrl(), target.getUsername());
            e.printStackTrace();
            ScrubMetrics result = new ScrubMetrics(target.getDbName(), target.getEnvKey(), false,
                    0, target.getTargetSchemas().size(), 0, 0, 0, 0, 0, 0, elapsed, "ORA-" + e.getErrorCode() + ": " + e.getMessage());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            System.err.printf("%nUNEXPECTED FAILURE on [%s]: %s%n", target.getDbName(), e.getMessage());
            e.printStackTrace();
            ScrubMetrics result = new ScrubMetrics(target.getDbName(), target.getEnvKey(), false,
                    0, target.getTargetSchemas().size(), 0, 0, 0, 0, 0, 0, elapsed, e.getMessage());
            return result;
        }
    }

    private static void printExecutiveSummary(List<ScrubMetrics> metricsList, String outputPath, long overallStart) {
        long totalElapsed = System.currentTimeMillis() - overallStart;
        System.out.println();
        System.out.println("==================================================================================================================================");
        System.out.println("FLORIDA LOTTERY MULTI-DATABASE SCRUB COMPLETE - EXECUTIVE SUMMARY");
        System.out.println("+----------------------+----------+-------------+--------+--------+-------+-------+------+------+------------+--------------------+");
        System.out.println("| Target Database      | Status   | Schemas (F) | Tables | Views  | Procs | Funcs | Pkgs | Seqs | Elapsed    | Error Details      |");
        System.out.println("+----------------------+----------+-------------+--------+--------+-------+-------+------+------+------------+--------------------+");

        int sumTables = 0;
        int sumViews = 0;
        int sumProcs = 0;
        int sumFuncs = 0;
        int sumPkgs = 0;
        int sumSeqs = 0;
        int sumFound = 0;
        int sumAttempted = 0;
        int successCount = 0;

        for (ScrubMetrics m : metricsList) {
            String status = m.isSuccess() ? "SUCCESS" : "FAILED";
            String elapsedStr = String.format("%.2fs", m.getElapsedMs() / 1000.0);
            String errStr = m.getErrorMessage() != null
                    ? (m.getErrorMessage().length() > 18 ? m.getErrorMessage().substring(0, 18) + ".." : m.getErrorMessage())
                    : "None";

            String schemaStr = String.format("%d of %d", m.getSchemasFound(), m.getSchemasAttempted());

            System.out.printf("| %-20s | %-8s | %11s | %6d | %6d | %5d | %5d | %4d | %4d | %10s | %-18s |%n",
                    m.getDbName(), status, schemaStr, m.getTablesExtracted(), m.getViewsExtracted(),
                    m.getProcsExtracted(), m.getFuncsExtracted(), m.getPkgsExtracted(), m.getSeqsExtracted(), elapsedStr, errStr);

            if (m.isSuccess()) {
                successCount++;
                sumFound += m.getSchemasFound();
                sumAttempted += m.getSchemasAttempted();
                sumTables += m.getTablesExtracted();
                sumViews += m.getViewsExtracted();
                sumProcs += m.getProcsExtracted();
                sumFuncs += m.getFuncsExtracted();
                sumPkgs += m.getPkgsExtracted();
                sumSeqs += m.getSeqsExtracted();
            }
        }
        System.out.println("+----------------------+----------+-------------+--------+--------+-------+-------+------+------+------------+--------------------+");
        String totalSchemaStr = String.format("%d of %d", sumFound, sumAttempted);
        System.out.printf("| TOTALS (%d/%d Success)  |          | %11s | %6d | %6d | %5d | %5d | %4d | %4d | %10.2fs |                    |%n",
                successCount, metricsList.size(), totalSchemaStr, sumTables, sumViews, sumProcs, sumFuncs, sumPkgs, sumSeqs, (totalElapsed / 1000.0));
        System.out.println("+----------------------+----------+-------------+--------+--------+-------+-------+------+------+------------+--------------------+");
        System.out.println("Master DbCache Location: " + outputPath + " (Unified Multi-Database Cache)");
        System.out.println("Backup File Created:     " + outputPath + ".bak");
        System.out.println("==================================================================================================================================");
    }

    private static Set<String> parseSchemasArg(String explicitSchemas, Set<String> fallbackSchemas) {
        if (explicitSchemas != null && !explicitSchemas.trim().isEmpty()) {
            Set<String> set = new LinkedHashSet<>();
            for (String s : explicitSchemas.split(",")) {
                if (!s.trim().isEmpty()) {
                    set.add(s.trim().toUpperCase());
                }
            }
            Set<String> result = set;
            return result;
        }
        Set<String> result = fallbackSchemas;
        return result;
    }

    private static String resolveCredential(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) {
                String result = c.trim();
                return result;
            }
        }
        String result = "";
        return result;
    }

    // =========================================================================
    // 1. TABLE, COLUMN, SIZING, CONSTRAINT, INDEX & TRIGGER EXTRACTION
    // =========================================================================
    private static ArrayNode extractTablesForSchema(Connection conn, String schema, boolean fetchConstraints, boolean fetchIndexes, boolean fetchTriggers) throws SQLException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode tablesNode = mapper.createArrayNode();

        // 1.1 Query Tables, Sizing & Comments
        String tableSql = "SELECT t.table_name, t.num_rows, t.avg_row_len, t.blocks, t.last_analyzed, c.comments " +
                "FROM all_tables t " +
                "LEFT JOIN all_tab_comments c ON t.owner = c.owner AND t.table_name = c.table_name " +
                "WHERE t.owner = ? " +
                "ORDER BY t.table_name";

        // 1.2 Primary Keys
        Map<String, List<String>> tablePkMap = new HashMap<>();
        if (fetchConstraints) {
            String pkSql = "SELECT c.table_name, cc.column_name " +
                    "FROM all_constraints c " +
                    "JOIN all_cons_columns cc ON c.owner = cc.owner AND c.constraint_name = cc.constraint_name " +
                    "WHERE c.owner = ? AND c.constraint_type = 'P' " +
                    "ORDER BY c.table_name, cc.position";
            try (PreparedStatement ps = conn.prepareStatement(pkSql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        String colName = rs.getString("column_name");
                        tablePkMap.computeIfAbsent(tName, k -> new ArrayList<>()).add(colName);
                    }
                }
            } catch (SQLException e) {
                System.err.println("   Warning fetching Primary Keys: " + e.getMessage());
            }
        }

        // 1.3 Constraints (PK, FK, Unique, Check)
        Map<String, List<ObjectNode>> tableConstraintsMap = new HashMap<>();
        if (fetchConstraints) {
            String consSql = "SELECT c.table_name, c.constraint_name, c.constraint_type, c.r_owner, c.r_constraint_name, c.search_condition, cc.column_name " +
                    "FROM all_constraints c " +
                    "LEFT JOIN all_cons_columns cc ON c.owner = cc.owner AND c.constraint_name = cc.constraint_name " +
                    "WHERE c.owner = ? " +
                    "ORDER BY c.table_name, c.constraint_name, cc.position";
            try (PreparedStatement ps = conn.prepareStatement(consSql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        String cName = rs.getString("constraint_name");
                        String cType = rs.getString("constraint_type");
                        String rOwner = rs.getString("r_owner");
                        String rCons = rs.getString("r_constraint_name");
                        String cond = rs.getString("search_condition");
                        String col = rs.getString("column_name");

                        ObjectNode cNode = mapper.createObjectNode();
                        cNode.put("ConstraintName", cName);
                        cNode.put("ConstraintType", cType);
                        cNode.put("ColumnName", col != null ? col : "");
                        cNode.put("ROwner", rOwner != null ? rOwner : "");
                        cNode.put("RConstraintName", rCons != null ? rCons : "");
                        cNode.put("SearchCondition", cond != null ? cond : "");

                        tableConstraintsMap.computeIfAbsent(tName, k -> new ArrayList<>()).add(cNode);
                    }
                }
            } catch (SQLException e) {
                System.err.println("   Warning fetching Constraints: " + e.getMessage());
            }
        }

        // 1.4 Indexes & Index Columns
        Map<String, List<ObjectNode>> tableIndexesMap = new HashMap<>();
        if (fetchIndexes) {
            String idxSql = "SELECT i.table_name, i.index_name, i.uniqueness, i.index_type, i.status, " +
                    "ic.column_name, ic.column_position, ic.descend " +
                    "FROM all_indexes i " +
                    "JOIN all_ind_columns ic ON i.owner = ic.index_owner AND i.index_name = ic.index_name " +
                    "WHERE i.owner = ? " +
                    "ORDER BY i.table_name, i.index_name, ic.column_position";
            try (PreparedStatement ps = conn.prepareStatement(idxSql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Map<String, ObjectNode>> schemaIndexGroup = new HashMap<>();
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        String idxName = rs.getString("index_name");
                        String uniqueness = rs.getString("uniqueness");
                        String idxType = rs.getString("index_type");
                        String status = rs.getString("status");
                        String colName = rs.getString("column_name");
                        String descend = rs.getString("descend");

                        Map<String, ObjectNode> tableMap = schemaIndexGroup.computeIfAbsent(tName, k -> new LinkedHashMap<>());
                        ObjectNode idxNode = tableMap.get(idxName);
                        if (idxNode == null) {
                            idxNode = mapper.createObjectNode();
                            idxNode.put("IndexName", idxName);
                            idxNode.put("Uniqueness", uniqueness != null ? uniqueness : "NONUNIQUE");
                            idxNode.put("IndexType", idxType != null ? idxType : "NORMAL");
                            idxNode.put("Status", status != null ? status : "VALID");
                            idxNode.set("Columns", mapper.createArrayNode());
                            idxNode.set("ColumnDetails", mapper.createArrayNode());
                            tableMap.put(idxName, idxNode);
                        }

                        ((ArrayNode) idxNode.get("Columns")).add(colName);

                        ObjectNode colDetail = mapper.createObjectNode();
                        colDetail.put("ColumnName", colName);
                        colDetail.put("Descend", descend != null ? descend : "ASC");
                        ((ArrayNode) idxNode.get("ColumnDetails")).add(colDetail);
                    }

                    for (Map.Entry<String, Map<String, ObjectNode>> entry : schemaIndexGroup.entrySet()) {
                        tableIndexesMap.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
                    }
                }
            } catch (SQLException e) {
                System.err.println("   Warning fetching Indexes: " + e.getMessage());
            }
        }

        // 1.5 Database Triggers
        Map<String, List<ObjectNode>> tableTriggersMap = new HashMap<>();
        if (fetchTriggers) {
            String trgSql = "SELECT trigger_name, table_name, trigger_type, triggering_event, status, description " +
                    "FROM all_triggers " +
                    "WHERE table_owner = ? " +
                    "ORDER BY table_name, trigger_name";
            try (PreparedStatement ps = conn.prepareStatement(trgSql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        String trgName = rs.getString("trigger_name");
                        String trgType = rs.getString("trigger_type");
                        String trgEvent = rs.getString("triggering_event");
                        String status = rs.getString("status");
                        String desc = rs.getString("description");

                        ObjectNode trgNode = mapper.createObjectNode();
                        trgNode.put("TriggerName", trgName);
                        trgNode.put("TriggerType", trgType != null ? trgType : "ROW");
                        trgNode.put("TriggeringEvent", trgEvent != null ? trgEvent : "UPDATE");
                        trgNode.put("Status", status != null ? status : "ENABLED");
                        trgNode.put("Description", desc != null ? desc : "");

                        tableTriggersMap.computeIfAbsent(tName, k -> new ArrayList<>()).add(trgNode);
                    }
                }
            } catch (SQLException e) {
                System.err.println("   Warning fetching Triggers: " + e.getMessage());
            }
        }

        // 1.6 Fetch Columns
        String colSql = "SELECT c.table_name, c.column_name, c.data_type, c.data_length, c.data_precision, c.data_scale, " +
                "c.nullable, c.data_default, cm.comments " +
                "FROM all_tab_columns c " +
                "LEFT JOIN all_col_comments cm ON c.owner = cm.owner AND c.table_name = cm.table_name AND c.column_name = cm.column_name " +
                "WHERE c.owner = ? " +
                "ORDER BY c.table_name, c.column_id";

        Map<String, List<ObjectNode>> tableColumnsMap = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(colSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tName = rs.getString("table_name");
                    String colName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    int length = rs.getInt("data_length");
                    int prec = rs.getInt("data_precision");
                    int scale = rs.getInt("data_scale");
                    String nullable = rs.getString("nullable");
                    String defVal = rs.getString("data_default");
                    String comments = rs.getString("comments");

                    ObjectNode colNode = mapper.createObjectNode();
                    colNode.put("ColumnName", colName);
                    colNode.put("DataType", dataType != null ? dataType : "VARCHAR2");
                    colNode.put("DataLength", length);
                    colNode.put("Precision", prec);
                    colNode.put("Scale", scale);
                    colNode.put("Nullable", nullable != null ? nullable : "Y");
                    colNode.put("DefaultValue", (defVal != null && !defVal.trim().isEmpty()) ? defVal.trim() : "-");
                    colNode.put("Comments", comments != null ? comments : "");

                    List<String> pks = tablePkMap.get(tName);
                    boolean isPk = pks != null && pks.contains(colName);
                    colNode.put("Constraint", isPk ? "PRIMARY KEY" : "-");

                    tableColumnsMap.computeIfAbsent(tName, k -> new ArrayList<>()).add(colNode);
                }
            }
        }

        // 1.7 External Tables & File Ingestion Definitions
        Map<String, ObjectNode> tableExternalMap = new HashMap<>();
        String extSql = "SELECT table_name, type_name, default_directory_name, access_type, access_parameters " +
                "FROM all_external_tables " +
                "WHERE owner = ? " +
                "ORDER BY table_name";
        try (PreparedStatement ps = conn.prepareStatement(extSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tName = rs.getString("table_name");
                    String typeName = rs.getString("type_name");
                    String defaultDir = rs.getString("default_directory_name");
                    String accessType = rs.getString("access_type");
                    String accessParams = rs.getString("access_parameters");

                    ObjectNode extNode = mapper.createObjectNode();
                    extNode.put("DriverType", typeName != null ? typeName : "ORACLE_LOADER");
                    extNode.put("DefaultDirectory", defaultDir != null ? defaultDir : "");
                    extNode.put("AccessType", accessType != null ? accessType : "");
                    extNode.put("AccessParameters", accessParams != null ? accessParams.trim() : "");
                    extNode.set("Locations", mapper.createArrayNode());

                    tableExternalMap.put(tName, extNode);
                }
            }
        } catch (SQLException e) {
            System.err.println("   Warning fetching External Tables: " + e.getMessage());
        }

        // Query External Table File Locations
        if (!tableExternalMap.isEmpty()) {
            String locSql = "SELECT table_name, location, directory_name " +
                    "FROM all_external_locations " +
                    "WHERE owner = ? " +
                    "ORDER BY table_name, location";
            try (PreparedStatement ps = conn.prepareStatement(locSql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        String location = rs.getString("location");
                        String dirName = rs.getString("directory_name");

                        ObjectNode extNode = tableExternalMap.get(tName);
                        if (extNode != null) {
                            ArrayNode locArr = (ArrayNode) extNode.get("Locations");
                            ObjectNode locObj = mapper.createObjectNode();
                            locObj.put("FileName", location != null ? location : "");
                            locObj.put("DirectoryName", dirName != null ? dirName : "");
                            locArr.add(locObj);
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("   Warning fetching External Table Locations: " + e.getMessage());
            }
        }

        // 1.8 Assemble Tables Node
        Set<String> processedTableNames = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(tableSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tName = rs.getString("table_name");
                    processedTableNames.add(tName);
                    String comments = rs.getString("comments");
                    long numRows = rs.getLong("num_rows");
                    boolean hasNumRows = !rs.wasNull();
                    int avgRowLen = rs.getInt("avg_row_len");
                    boolean hasAvgLen = !rs.wasNull();
                    long blocks = rs.getLong("blocks");
                    boolean hasBlocks = !rs.wasNull();
                    Timestamp lastAnalyzed = rs.getTimestamp("last_analyzed");

                    ObjectNode tblNode = mapper.createObjectNode();
                    tblNode.put("TableName", tName);

                    ObjectNode extInfo = tableExternalMap.get(tName);
                    if (extInfo != null) {
                        tblNode.put("TableType", "EXTERNAL TABLE");
                        tblNode.put("IsExternal", true);
                        tblNode.set("ExternalTable", extInfo);
                    } else {
                        tblNode.put("TableType", "RELATIONAL TABLE");
                        tblNode.put("IsExternal", false);
                    }

                    tblNode.put("Comments", comments != null ? comments : (extInfo != null ? "Florida Lottery " + schema + " external flat-file ingestion table." : "Florida Lottery " + schema + " persistence entity."));

                    if (hasNumRows) tblNode.put("NumRows", numRows);
                    if (hasAvgLen) tblNode.put("AvgRowLen", avgRowLen);
                    if (hasBlocks) tblNode.put("Blocks", blocks);
                    if (lastAnalyzed != null) {
                        tblNode.put("LastAnalyzed", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastAnalyzed));
                    }

                    // Columns
                    ArrayNode colsArr = mapper.createArrayNode();
                    List<ObjectNode> cols = tableColumnsMap.get(tName);
                    if (cols != null) {
                        for (ObjectNode c : cols) colsArr.add(c);
                    }
                    tblNode.set("Columns", colsArr);

                    // Primary Keys
                    ArrayNode pkArr = mapper.createArrayNode();
                    List<String> pks = tablePkMap.get(tName);
                    if (pks != null) {
                        for (String pk : pks) pkArr.add(pk);
                    }
                    tblNode.set("PrimaryKey", pkArr);

                    // Constraints
                    ArrayNode consArr = mapper.createArrayNode();
                    List<ObjectNode> consList = tableConstraintsMap.get(tName);
                    if (consList != null) {
                        for (ObjectNode cn : consList) consArr.add(cn);
                    }
                    tblNode.set("Constraints", consArr);

                    // Indexes
                    ArrayNode idxArr = mapper.createArrayNode();
                    List<ObjectNode> idxList = tableIndexesMap.get(tName);
                    if (idxList != null) {
                        for (ObjectNode idx : idxList) idxArr.add(idx);
                    }
                    tblNode.set("Indexes", idxArr);

                    // Triggers
                    ArrayNode trgArr = mapper.createArrayNode();
                    List<ObjectNode> trgList = tableTriggersMap.get(tName);
                    if (trgList != null) {
                        for (ObjectNode trg : trgList) trgArr.add(trg);
                    }
                    tblNode.set("Triggers", trgArr);

                    tablesNode.add(tblNode);
                }
            }
        }

        // Include any external tables in all_external_tables that may not have been returned by all_tables
        for (Map.Entry<String, ObjectNode> extEntry : tableExternalMap.entrySet()) {
            String extTableName = extEntry.getKey();
            if (!processedTableNames.contains(extTableName)) {
                ObjectNode tblNode = mapper.createObjectNode();
                tblNode.put("TableName", extTableName);
                tblNode.put("TableType", "EXTERNAL TABLE");
                tblNode.put("IsExternal", true);
                tblNode.set("ExternalTable", extEntry.getValue());
                tblNode.put("Comments", "Florida Lottery " + schema + " external flat-file ingestion table.");

                // Columns
                ArrayNode colsArr = mapper.createArrayNode();
                List<ObjectNode> cols = tableColumnsMap.get(extTableName);
                if (cols != null) {
                    for (ObjectNode c : cols) colsArr.add(c);
                }
                tblNode.set("Columns", colsArr);
                tblNode.set("PrimaryKey", mapper.createArrayNode());
                tblNode.set("Constraints", mapper.createArrayNode());
                tblNode.set("Indexes", mapper.createArrayNode());
                tblNode.set("Triggers", mapper.createArrayNode());
                tablesNode.add(tblNode);
            }
        }

        ArrayNode result = tablesNode;
        return result;
    }

    // =========================================================================
    // 2. VIEW EXTRACTION
    // =========================================================================
    private static ArrayNode extractViewsForSchema(Connection conn, String schema) throws SQLException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode viewsNode = mapper.createArrayNode();

        String viewSql = "SELECT v.view_name, c.comments " +
                "FROM all_views v " +
                "LEFT JOIN all_tab_comments c ON v.owner = c.owner AND v.view_name = c.table_name " +
                "WHERE v.owner = ? " +
                "ORDER BY v.view_name";

        try (PreparedStatement ps = conn.prepareStatement(viewSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String vName = rs.getString("view_name");
                    String comments = rs.getString("comments");

                    ObjectNode vNode = mapper.createObjectNode();
                    vNode.put("ViewName", vName);
                    vNode.put("Comments", comments != null ? comments : "Database View in " + schema);
                    viewsNode.add(vNode);
                }
            }
        } catch (SQLException e) {
            System.err.println("   Warning fetching Views: " + e.getMessage());
        }

        ArrayNode result = viewsNode;
        return result;
    }

    // =========================================================================
    // 3. STANDALONE PROCEDURES & FUNCTIONS EXTRACTION
    // =========================================================================
    private static ArrayNode extractStandaloneRoutines(Connection conn, String schema, String objectType, boolean fetchSource, Map<String, List<ObjectNode>> argumentSignaturesMap, Map<String, ObjectNode> runtimeMetricsMap) throws SQLException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode routinesNode = mapper.createArrayNode();

        String objSql = "SELECT object_name " +
                "FROM all_objects " +
                "WHERE owner = ? AND object_type = ? " +
                "ORDER BY object_name";

        Map<String, String> sourceMap = new HashMap<>();
        if (fetchSource) {
            String srcSql = "SELECT name, text " +
                    "FROM all_source " +
                    "WHERE owner = ? AND type = ? " +
                    "ORDER BY name, line";
            try (PreparedStatement ps = conn.prepareStatement(srcSql)) {
                ps.setString(1, schema);
                ps.setString(2, objectType);
                try (ResultSet rs = ps.executeQuery()) {
                    StringBuilder sb = new StringBuilder();
                    String currentObj = null;
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String line = rs.getString("text");
                        if (currentObj != null && !currentObj.equals(name)) {
                            sourceMap.put(currentObj, sb.toString());
                            sb.setLength(0);
                        }
                        currentObj = name;
                        sb.append(line != null ? line : "");
                    }
                    if (currentObj != null) {
                        sourceMap.put(currentObj, sb.toString());
                    }
                }
            } catch (SQLException e) {
                System.err.println("   Warning fetching Source for " + objectType + ": " + e.getMessage());
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(objSql)) {
            ps.setString(1, schema);
            ps.setString(2, objectType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objName = rs.getString("object_name");
                    ObjectNode rNode = mapper.createObjectNode();
                    if ("PROCEDURE".equals(objectType)) {
                        rNode.put("ProcedureName", objName);
                    } else {
                        rNode.put("FunctionName", objName);
                    }
                    rNode.put("Type", objectType);
                    String sql = sourceMap.getOrDefault(objName, "");
                    rNode.put("Sql", sql);
                    rNode.put("Source", sql);

                    // Attach argument signatures
                    ArrayNode argsArr = mapper.createArrayNode();
                    List<ObjectNode> argsList = argumentSignaturesMap.get(objName);
                    if (argsList != null) {
                        for (ObjectNode arg : argsList) argsArr.add(arg);
                    }
                    rNode.set("Arguments", argsArr);

                    // Attach runtime metrics if present
                    if (runtimeMetricsMap != null) {
                        ObjectNode metricsNode = runtimeMetricsMap.get(objName.toUpperCase());
                        if (metricsNode != null) {
                            rNode.set("RuntimeMetrics", metricsNode);
                        }
                    }

                    routinesNode.add(rNode);
                }
            }
        }

        ArrayNode result = routinesNode;
        return result;
    }

    // =========================================================================
    // 4. PACKAGES & MEMBER SUB-ROUTINES EXTRACTION
    // =========================================================================
    private static ArrayNode extractPackagesForSchema(Connection conn, String schema, boolean fetchSource, Map<String, List<ObjectNode>> argumentSignaturesMap, Map<String, ObjectNode> runtimeMetricsMap) throws SQLException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode pkgsNode = mapper.createArrayNode();

        String pkgSql = "SELECT object_name " +
                "FROM all_objects " +
                "WHERE owner = ? AND object_type = 'PACKAGE' " +
                "ORDER BY object_name";

        // Fetch package source specs & bodies
        Map<String, String> pkgSpecMap = new HashMap<>();
        Map<String, String> pkgBodyMap = new HashMap<>();

        if (fetchSource) {
            String srcSql = "SELECT name, type, text " +
                    "FROM all_source " +
                    "WHERE owner = ? AND type IN ('PACKAGE', 'PACKAGE BODY') " +
                    "ORDER BY name, type, line";
            try (PreparedStatement ps = conn.prepareStatement(srcSql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    StringBuilder specSb = new StringBuilder();
                    StringBuilder bodySb = new StringBuilder();
                    String curName = null;
                    String curType = null;

                    while (rs.next()) {
                        String name = rs.getString("name");
                        String type = rs.getString("type");
                        String line = rs.getString("text");

                        if (curName != null && (!curName.equals(name) || !curType.equals(type))) {
                            if ("PACKAGE".equals(curType)) {
                                pkgSpecMap.put(curName, specSb.toString());
                                specSb.setLength(0);
                            } else {
                                pkgBodyMap.put(curName, bodySb.toString());
                                bodySb.setLength(0);
                            }
                        }
                        curName = name;
                        curType = type;
                        if ("PACKAGE".equals(type)) {
                            specSb.append(line != null ? line : "");
                        } else {
                            bodySb.append(line != null ? line : "");
                        }
                    }
                    if (curName != null) {
                        if ("PACKAGE".equals(curType)) pkgSpecMap.put(curName, specSb.toString());
                        else pkgBodyMap.put(curName, bodySb.toString());
                    }
                }
            } catch (SQLException e) {
                System.err.println("   Warning fetching Package Source: " + e.getMessage());
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(pkgSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pkgName = rs.getString("object_name");
                    ObjectNode pkgNode = mapper.createObjectNode();
                    pkgNode.put("PackageName", pkgName);

                    String spec = pkgSpecMap.getOrDefault(pkgName, "");
                    String body = pkgBodyMap.getOrDefault(pkgName, "");
                    String fullSql = spec + (body.isEmpty() ? "" : "\n/ -- BODY --\n" + body);
                    pkgNode.put("Sql", fullSql);
                    pkgNode.put("Source", fullSql);

                    // Parse member sub-routines from package specification & body
                    ArrayNode membersArr = mapper.createArrayNode();
                    List<String> members = parsePackageSubRoutines(spec, body);
                    for (String member : members) {
                        ObjectNode mNode = mapper.createObjectNode();
                        mNode.put("ProcedureName", member);
                        mNode.put("Type", member.toUpperCase().startsWith("FN_") || member.toUpperCase().startsWith("FUN_") ? "PACKAGE FUNCTION" : "PACKAGE PROCEDURE");
                        mNode.put("Sql", extractSubRoutineSql(body.isEmpty() ? spec : body, member));

                        // Attach argument signatures for member routine
                        ArrayNode memberArgs = mapper.createArrayNode();
                        List<ObjectNode> pkgRoutineArgs = argumentSignaturesMap.get(pkgName + "." + member);
                        if (pkgRoutineArgs != null) {
                            for (ObjectNode arg : pkgRoutineArgs) memberArgs.add(arg);
                        }
                        mNode.set("Arguments", memberArgs);

                        // Attach runtime metrics if present for member routine
                        if (runtimeMetricsMap != null) {
                            String memberKey = pkgName.toUpperCase() + "." + member.toUpperCase();
                            ObjectNode metricsNode = runtimeMetricsMap.get(memberKey);
                            if (metricsNode == null) {
                                metricsNode = runtimeMetricsMap.get(member.toUpperCase());
                            }
                            if (metricsNode != null) {
                                mNode.set("RuntimeMetrics", metricsNode);
                            }
                        }

                        membersArr.add(mNode);
                    }
                    pkgNode.set("Procedures", membersArr);

                    // Attach package-level runtime metrics if present
                    if (runtimeMetricsMap != null) {
                        ObjectNode pkgMetricsNode = runtimeMetricsMap.get(pkgName.toUpperCase());
                        if (pkgMetricsNode != null) {
                            pkgNode.set("RuntimeMetrics", pkgMetricsNode);
                        }
                    }

                    pkgsNode.add(pkgNode);
                }
            }
        }

        ArrayNode result = pkgsNode;
        return result;
    }

    // =========================================================================
    // 5. ROUTINE ARGUMENT SIGNATURES (ALL_ARGUMENTS)
    // =========================================================================
    private static Map<String, List<ObjectNode>> extractArgumentsForSchema(Connection conn, String schema) {
        Map<String, List<ObjectNode>> map = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        String argSql = "SELECT package_name, object_name, argument_name, position, sequence, in_out, data_type, defaulted " +
                "FROM all_arguments " +
                "WHERE owner = ? " +
                "ORDER BY package_name, object_name, sequence";

        try (PreparedStatement ps = conn.prepareStatement(argSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pkg = rs.getString("package_name");
                    String obj = rs.getString("object_name");
                    String argName = rs.getString("argument_name");
                    int pos = rs.getInt("position");
                    String inOut = rs.getString("in_out");
                    String dataType = rs.getString("data_type");
                    String defaulted = rs.getString("defaulted");

                    String key = (pkg != null && !pkg.trim().isEmpty()) ? (pkg.trim() + "." + obj) : obj;

                    ObjectNode argNode = mapper.createObjectNode();
                    argNode.put("ArgumentName", argName != null ? argName : (pos == 0 ? "RETURN" : "ARG_" + pos));
                    argNode.put("Position", pos);
                    argNode.put("Direction", inOut != null ? inOut : "IN");
                    argNode.put("DataType", dataType != null ? dataType : "VARCHAR2");
                    argNode.put("Defaulted", "Y".equalsIgnoreCase(defaulted) ? "Y" : "N");

                    map.computeIfAbsent(key, k -> new ArrayList<>()).add(argNode);
                }
            }
        } catch (SQLException e) {
            System.err.println("   Warning fetching Routine Arguments: " + e.getMessage());
        }

        Map<String, List<ObjectNode>> result = map;
        return result;
    }

    // =========================================================================
    // 6. SEQUENCE GENERATORS (ALL_SEQUENCES)
    // =========================================================================
    private static ArrayNode extractSequencesForSchema(Connection conn, String schema) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode seqArray = mapper.createArrayNode();

        String seqSql = "SELECT sequence_name, min_value, max_value, increment_by, cycle_flag, order_flag, cache_size, last_number " +
                "FROM all_sequences " +
                "WHERE sequence_owner = ? " +
                "ORDER BY sequence_name";

        try (PreparedStatement ps = conn.prepareStatement(seqSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String seqName = rs.getString("sequence_name");
                    String minVal = rs.getString("min_value");
                    String maxVal = rs.getString("max_value");
                    String incBy = rs.getString("increment_by");
                    String cycle = rs.getString("cycle_flag");
                    String order = rs.getString("order_flag");
                    String cache = rs.getString("cache_size");
                    String lastNum = rs.getString("last_number");

                    ObjectNode sNode = mapper.createObjectNode();
                    sNode.put("SequenceName", seqName);
                    sNode.put("MinValue", minVal != null ? minVal : "1");
                    sNode.put("MaxValue", maxVal != null ? maxVal : "");
                    sNode.put("IncrementBy", incBy != null ? incBy : "1");
                    sNode.put("CycleFlag", cycle != null ? cycle : "N");
                    sNode.put("OrderFlag", order != null ? order : "N");
                    sNode.put("CacheSize", cache != null ? cache : "0");
                    sNode.put("LastNumber", lastNum != null ? lastNum : "0");

                    seqArray.add(sNode);
                }
            }
        } catch (SQLException e) {
            System.err.println("   Warning fetching Sequences: " + e.getMessage());
        }

        ArrayNode result = seqArray;
        return result;
    }

    // =========================================================================
    // 7. COMPILED OBJECT DEPENDENCIES (ALL_DEPENDENCIES)
    // =========================================================================
    private static ArrayNode extractDependenciesForSchema(Connection conn, String schema) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode depArray = mapper.createArrayNode();

        String depSql = "SELECT name, type, referenced_owner, referenced_name, referenced_type, referenced_link_name " +
                "FROM all_dependencies " +
                "WHERE owner = ? " +
                "ORDER BY name, referenced_name";

        try (PreparedStatement ps = conn.prepareStatement(depSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String type = rs.getString("type");
                    String refOwner = rs.getString("referenced_owner");
                    String refName = rs.getString("referenced_name");
                    String refType = rs.getString("referenced_type");
                    String refLink = rs.getString("referenced_link_name");

                    ObjectNode dNode = mapper.createObjectNode();
                    dNode.put("ObjectName", name);
                    dNode.put("ObjectType", type);
                    dNode.put("ReferencedOwner", refOwner != null ? refOwner : "");
                    dNode.put("ReferencedName", refName != null ? refName : "");
                    dNode.put("ReferencedType", refType != null ? refType : "");
                    if (refLink != null && !refLink.trim().isEmpty()) {
                        dNode.put("DbLink", refLink.trim());
                    }

                    depArray.add(dNode);
                }
            }
        } catch (SQLException e) {
            System.err.println("   Warning fetching Dependencies: " + e.getMessage());
        }

        ArrayNode result = depArray;
        return result;
    }

    // =========================================================================
    // 8. SECURITY GRANTS & PRIVILEGES (ALL_TAB_PRIVS & ALL_COL_PRIVS)
    // =========================================================================
    private static ArrayNode extractTablePrivilegesForSchema(Connection conn, String schema) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode grantsArray = mapper.createArrayNode();

        String tabPrivSql = "SELECT table_schema, table_name, type, privilege, grantee, grantor, grantable " +
                "FROM all_tab_privs " +
                "WHERE table_schema = ? " +
                "ORDER BY type, table_name, grantee, privilege";

        try (PreparedStatement ps = conn.prepareStatement(tabPrivSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString("table_schema");
                    String objName = rs.getString("table_name");
                    String objType = rs.getString("type");
                    String priv = rs.getString("privilege");
                    String grantee = rs.getString("grantee");
                    String grantor = rs.getString("grantor");
                    String grantable = rs.getString("grantable");

                    ObjectNode gNode = mapper.createObjectNode();
                    gNode.put("SchemaOwner", owner != null ? owner : schema);
                    gNode.put("ObjectName", objName != null ? objName : "");
                    gNode.put("ObjectType", objType != null ? objType : "TABLE");
                    gNode.put("Privilege", priv != null ? priv : "");
                    gNode.put("Grantee", grantee != null ? grantee : "");
                    gNode.put("Grantor", grantor != null ? grantor : "");
                    gNode.put("Grantable", grantable != null ? grantable : "NO");

                    grantsArray.add(gNode);
                }
            }
        } catch (SQLException e) {
            System.err.println("   Warning fetching Object Grants (ALL_TAB_PRIVS): " + e.getMessage());
        }

        ArrayNode result = grantsArray;
        return result;
    }

    private static ArrayNode extractColumnPrivilegesForSchema(Connection conn, String schema) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode grantsArray = mapper.createArrayNode();

        String colPrivSql = "SELECT table_schema, table_name, column_name, privilege, grantee, grantor, grantable " +
                "FROM all_col_privs " +
                "WHERE table_schema = ? " +
                "ORDER BY table_name, column_name, grantee, privilege";

        try (PreparedStatement ps = conn.prepareStatement(colPrivSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString("table_schema");
                    String objName = rs.getString("table_name");
                    String colName = rs.getString("column_name");
                    String priv = rs.getString("privilege");
                    String grantee = rs.getString("grantee");
                    String grantor = rs.getString("grantor");
                    String grantable = rs.getString("grantable");

                    ObjectNode gNode = mapper.createObjectNode();
                    gNode.put("SchemaOwner", owner != null ? owner : schema);
                    gNode.put("ObjectName", objName != null ? objName : "");
                    gNode.put("ColumnName", colName != null ? colName : "");
                    gNode.put("Privilege", priv != null ? priv : "");
                    gNode.put("Grantee", grantee != null ? grantee : "");
                    gNode.put("Grantor", grantor != null ? grantor : "");
                    gNode.put("Grantable", grantable != null ? grantable : "NO");

                    grantsArray.add(gNode);
                }
            }
        } catch (SQLException e) {
            System.err.println("   Warning fetching Column Grants (ALL_COL_PRIVS): " + e.getMessage());
        }

        ArrayNode result = grantsArray;
        return result;
    }

    // =========================================================================
    // 9. RUNTIME PERFORMANCE TELEMETRY & RESOURCE METRICS (V$SQL / V$SQLAREA)
    // =========================================================================
    private static Map<String, ObjectNode> extractRuntimeMetricsForSchema(Connection conn, String schema) {
        Map<String, ObjectNode> metricsMap = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        // Query 1: Routine SQL telemetry via V$SQL joined with ALL_OBJECTS on program_id
        String sqlPerf = "SELECT " +
                "UPPER(o.object_name) AS object_name, " +
                "NVL(UPPER(o.subobject_name), '') AS subprogram_name, " +
                "MAX(s.sql_id) AS top_sql_id, " +
                "SUM(s.executions) AS total_executions, " +
                "SUM(s.elapsed_time) AS total_elapsed_time, " +
                "SUM(s.cpu_time) AS total_cpu_time, " +
                "SUM(s.disk_reads) AS total_disk_reads, " +
                "SUM(s.buffer_gets) AS total_buffer_gets, " +
                "SUM(s.rows_processed) AS total_rows_processed, " +
                "MAX(s.program_line#) AS max_program_line " +
                "FROM v$sql s " +
                "JOIN all_objects o ON s.program_id = o.object_id " +
                "WHERE o.owner = ? AND s.executions > 0 " +
                "GROUP BY o.object_name, o.subobject_name";

        boolean vSqlSuccess = false;
        try (PreparedStatement ps = conn.prepareStatement(sqlPerf)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vSqlSuccess = true;
                    String objName = rs.getString("object_name");
                    String subName = rs.getString("subprogram_name");
                    long executions = rs.getLong("total_executions");
                    long elapsedTime = rs.getLong("total_elapsed_time"); // microseconds
                    long cpuTime = rs.getLong("total_cpu_time"); // microseconds
                    long diskReads = rs.getLong("total_disk_reads");
                    long bufferGets = rs.getLong("total_buffer_gets");
                    long rows = rs.getLong("total_rows_processed");
                    String topSqlId = rs.getString("top_sql_id");
                    int line = rs.getInt("max_program_line");

                    ObjectNode node = createRuntimeMetricsNode(mapper, executions, elapsedTime, cpuTime,
                            diskReads, bufferGets, rows, topSqlId, line, "ORACLE_V$SQL_CACHE");

                    if (subName != null && !subName.trim().isEmpty()) {
                        metricsMap.put(objName + "." + subName, node);
                    }
                    metricsMap.put(objName, node);
                }
            }
        } catch (SQLException e) {
            // V$SQL may require SELECT_CATALOG_ROLE or DBA grants; gracefully handle ORA-00942
            System.out.println("   Note: V$SQL dynamic performance view not accessible (" + e.getMessage().trim() + "). Attempting secondary module inspection.");
        }

        // Query 2: Secondary fallback checking V$SQL by module name if object join yielded empty
        if (!vSqlSuccess) {
            String moduleSql = "SELECT " +
                    "UPPER(s.module) AS module_name, " +
                    "MAX(s.sql_id) AS top_sql_id, " +
                    "SUM(s.executions) AS total_executions, " +
                    "SUM(s.elapsed_time) AS total_elapsed_time, " +
                    "SUM(s.cpu_time) AS total_cpu_time, " +
                    "SUM(s.disk_reads) AS total_disk_reads, " +
                    "SUM(s.buffer_gets) AS total_buffer_gets, " +
                    "SUM(s.rows_processed) AS total_rows_processed " +
                    "FROM v$sql s " +
                    "WHERE s.parsing_schema_name = ? AND s.executions > 0 AND s.module IS NOT NULL " +
                    "GROUP BY s.module";
            try (PreparedStatement ps = conn.prepareStatement(moduleSql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String mod = rs.getString("module_name");
                        long executions = rs.getLong("total_executions");
                        long elapsedTime = rs.getLong("total_elapsed_time");
                        long cpuTime = rs.getLong("total_cpu_time");
                        long diskReads = rs.getLong("total_disk_reads");
                        long bufferGets = rs.getLong("total_buffer_gets");
                        long rows = rs.getLong("total_rows_processed");
                        String topSqlId = rs.getString("top_sql_id");

                        ObjectNode node = createRuntimeMetricsNode(mapper, executions, elapsedTime, cpuTime,
                                diskReads, bufferGets, rows, topSqlId, 1, "ORACLE_V$SQL_MODULE");
                        metricsMap.put(mod, node);
                    }
                }
            } catch (SQLException e) {
                // Defensive suppression of secondary fallback errors
            }
        }

        Map<String, ObjectNode> result = metricsMap;
        return result;
    }

    private static ObjectNode createRuntimeMetricsNode(ObjectMapper mapper, long executions, long elapsedTimeMicros,
                                                       long cpuTimeMicros, long diskReads, long bufferGets,
                                                       long rowsProcessed, String topSqlId, int programLine, String source) {
        ObjectNode node = mapper.createObjectNode();
        double elapsedSeconds = (double) elapsedTimeMicros / 1000000.0;
        double avgElapsedMs = executions > 0 ? ((double) elapsedTimeMicros / (double) executions) / 1000.0 : 0.0;
        double avgDiskReads = executions > 0 ? (double) diskReads / (double) executions : 0.0;
        double avgBufferGets = executions > 0 ? (double) bufferGets / (double) executions : 0.0;
        double avgDiskBlocksPerSec = elapsedSeconds > 0.0001 ? (double) diskReads / elapsedSeconds : 0.0;
        double avgRecordBlocksPerSec = elapsedSeconds > 0.0001 ? (double) rowsProcessed / elapsedSeconds : 0.0;
        double hitRatioPct = bufferGets > 0 ? Math.min(100.0, Math.max(0.0, (1.0 - ((double) diskReads / (double) bufferGets)) * 100.0)) : 100.0;
        double cpuPct = elapsedTimeMicros > 0 ? Math.min(100.0, Math.max(0.0, ((double) cpuTimeMicros / (double) elapsedTimeMicros) * 100.0)) : 100.0;
        long avgRows = executions > 0 ? Math.round((double) rowsProcessed / (double) executions) : 0;

        String cautionLevel;
        String advisoryMessage;

        if (avgElapsedMs > 30000.0) {
            cautionLevel = "CAUTION - LONG RUNNING (>30s)";
            advisoryMessage = "Long-running execution profile (avg " + String.format(Locale.US, "%.1f", avgElapsedMs / 1000.0) + "s). Schedule during designated off-peak batch windows. Review lock concurrency and query plans.";
        } else if (avgDiskBlocksPerSec > 1000.0 || (hitRatioPct < 85.0 && avgDiskReads > 500)) {
            cautionLevel = "CAUTION - HIGH DISK I/O";
            advisoryMessage = "High physical disk read rate (avg " + String.format(Locale.US, "%.1f", avgDiskBlocksPerSec) + " blocks/sec, " + String.format(Locale.US, "%.1f", hitRatioPct) + "% cache hit). Suspected full table scan or unindexed lookup; verify indexes on driving tables.";
        } else if (avgElapsedMs > 5000.0) {
            cautionLevel = "ADVISORY - MODERATE BATCH";
            advisoryMessage = "Moderate batch execution duration (avg " + String.format(Locale.US, "%.1f", avgElapsedMs / 1000.0) + "s). Monitor runtime under high transactional data volume.";
        } else {
            cautionLevel = "OPTIMAL - MEMORY RESIDENT";
            advisoryMessage = "Sub-second execution profile (avg " + String.format(Locale.US, "%.1f", avgElapsedMs) + "ms) with " + String.format(Locale.US, "%.1f", hitRatioPct) + "% buffer hit ratio. Low overhead and safe for high-frequency interactive or scheduled execution.";
        }

        node.put("Executions", executions);
        node.put("AvgElapsedMs", Math.round(avgElapsedMs * 10.0) / 10.0);
        node.put("AvgDiskReads", Math.round(avgDiskReads * 10.0) / 10.0);
        node.put("AvgBufferGets", Math.round(avgBufferGets * 10.0) / 10.0);
        node.put("AvgDiskBlocksPerSec", Math.round(avgDiskBlocksPerSec * 10.0) / 10.0);
        node.put("AvgRecordBlocksPerSec", Math.round(avgRecordBlocksPerSec * 10.0) / 10.0);
        node.put("CpuPct", Math.round(cpuPct * 10.0) / 10.0);
        node.put("HitRatioPct", Math.round(hitRatioPct * 10.0) / 10.0);
        node.put("AvgRows", avgRows);
        node.put("TopSqlId", topSqlId != null ? topSqlId : "");
        node.put("ProgramLine", programLine);
        node.put("CautionLevel", cautionLevel);
        node.put("AdvisoryMessage", advisoryMessage);
        node.put("DataSource", source);

        ArrayNode tagsArray = mapper.createArrayNode();
        if (avgElapsedMs > 30000.0) {
            tagsArray.add("Long Process Time");
        }
        if (avgDiskBlocksPerSec > 500.0 || (hitRatioPct < 85.0 && avgDiskReads > 100)) {
            tagsArray.add("Heavy Disk Use");
        }
        if (avgRecordBlocksPerSec > 1000.0 || avgRows > 2500) {
            tagsArray.add("Heavy Data Processing");
        }
        if (cpuPct < 40.0 && avgElapsedMs > 2000.0) {
            tagsArray.add("Heavy Record Lock Rates");
        }
        if (avgElapsedMs < 500.0 && avgDiskReads < 5.0 && hitRatioPct >= 95.0) {
            tagsArray.add("Low Resource Footprint");
        }
        if (hitRatioPct >= 98.0 && avgDiskReads < 20.0 && tagsArray.isEmpty()) {
            tagsArray.add("Memory Resident Cache");
        }
        if (tagsArray.isEmpty()) {
            tagsArray.add("Standard Operational Profile");
        }
        node.set("ProfileTags", tagsArray);

        ObjectNode result = node;
        return result;
    }

    private static List<String> parsePackageSubRoutines(String specText, String bodyText) {
        Set<String> set = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile("(?i)(?:procedure|function)\\s+([a-zA-Z0-9_\\$#]+)");

        if (specText != null && !specText.isEmpty()) {
            Matcher m = pattern.matcher(specText);
            while (m.find()) {
                set.add(m.group(1).toUpperCase());
            }
        }
        if (bodyText != null && !bodyText.isEmpty()) {
            Matcher m = pattern.matcher(bodyText);
            while (m.find()) {
                set.add(m.group(1).toUpperCase());
            }
        }
        List<String> result = new ArrayList<>(set);
        return result;
    }

    private static String extractSubRoutineSql(String fullCode, String routineName) {
        if (fullCode == null || fullCode.isEmpty()) {
            String result = "";
            return result;
        }
        Pattern startPat = Pattern.compile("(?i)(procedure|function)\\s+" + Pattern.quote(routineName) + "\\b");
        Matcher m = startPat.matcher(fullCode);
        if (m.find()) {
            int start = m.start();
            int end = fullCode.indexOf(routineName + ";", start);
            if (end != -1) {
                String result = fullCode.substring(start, Math.min(fullCode.length(), end + routineName.length() + 1));
                return result;
            }
            String result = fullCode.substring(start, Math.min(fullCode.length(), start + 4000));
            return result;
        }
        String result = "";
        return result;
    }

    // =========================================================================
    // 8. CACHE MERGING & PERSISTENCE
    // =========================================================================
    private static void saveOrMergeDbCache(String outputPath, ObjectNode newDatabaseNode, ObjectMapper mapper, boolean mergeMode) throws IOException {
        File targetFile = new File(outputPath);
        if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        ObjectNode root;
        if (mergeMode && targetFile.exists() && targetFile.length() > 0) {
            System.out.println("Existing DbCache.json found. Merging database entry...");
            try {
                // Create safety backup
                File backupFile = new File(targetFile.getParentFile(), targetFile.getName() + ".bak");
                Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Created backup at: " + backupFile.getAbsolutePath());

                JsonNode existingRoot = mapper.readTree(targetFile);
                if (existingRoot.isObject()) {
                    root = (ObjectNode) existingRoot;
                } else {
                    root = mapper.createObjectNode();
                }
            } catch (Exception e) {
                System.err.println("Warning reading existing DbCache for merge: " + e.getMessage() + ". Creating fresh root.");
                root = mapper.createObjectNode();
            }
        } else {
            root = mapper.createObjectNode();
        }

        ArrayNode databases = (ArrayNode) root.get("Databases");
        if (databases == null) {
            databases = mapper.createArrayNode();
            root.set("Databases", databases);
        }

        String targetDbName = newDatabaseNode.path("DatabaseName").asText("");
        int existingIdx = -1;
        for (int i = 0; i < databases.size(); i++) {
            JsonNode db = databases.get(i);
            if (db.path("DatabaseName").asText("").equalsIgnoreCase(targetDbName)) {
                existingIdx = i;
                break;
            }
        }

        if (existingIdx != -1) {
            System.out.printf("Updating existing database entry [%s] in DbCache.json...%n", targetDbName);
            databases.set(existingIdx, newDatabaseNode);
        } else {
            System.out.printf("Appending new database entry [%s] to DbCache.json...%n", targetDbName);
            databases.add(newDatabaseNode);
        }

        root.put("LastUpdated", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(fos, root);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String a : args) {
            if (a.startsWith("-") || a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq != -1) {
                    String key = a.substring(a.startsWith("--") ? 2 : 1, eq).trim();
                    String val = a.substring(eq + 1).trim();
                    map.put(key, val);
                } else {
                    String key = a.substring(a.startsWith("--") ? 2 : 1).trim();
                    map.put(key, "true");
                }
            }
        }
        Map<String, String> result = map;
        return result;
    }

    private static Properties loadCredentialsProperties(String explicitPath) {
        Properties props = new Properties();
        List<File> candidateFiles = new ArrayList<>();

        if (explicitPath != null && !explicitPath.trim().isEmpty()) {
            candidateFiles.add(new File(explicitPath.trim()));
        }
        candidateFiles.add(new File("db_credentials.properties"));
        candidateFiles.add(new File("C:\\ocioldapps1\\Cached\\DbCache\\db_credentials.properties"));
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            candidateFiles.add(new File(userHome, ".oracle_credentials.properties"));
        }

        for (File candidate : candidateFiles) {
            if (candidate.exists() && candidate.isFile() && candidate.canRead()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(candidate)) {
                    props.load(fis);
                    System.out.println("Loaded credentials properties from: " + candidate.getAbsolutePath());
                    Properties result = props;
                    return result;
                } catch (Exception e) {
                    System.err.println("Warning reading credentials from " + candidate.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        Properties result = props;
        return result;
    }
}
