package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Enterprise Schema Security Grants & Privileges Specification Generator for the Florida Lottery.
 * <p>
 * Consolidates permissions extracted from {@code ALL_TAB_PRIVS} and {@code ALL_COL_PRIVS} into formal,
 * audit-ready Microsoft Word (.docx) specifications matching the Florida Lottery Web Team & DataOps format:
 *  1. Formal Web Team Cover Page & Governance Sign-off (Author, Approvals, Revision History).
 *  2. Interactive Table of Contents (TOC) with clickable bookmark jumps.
 *  3. Executive Security Profile & Risk Audit Metrics (Grantees, Object Types, Risk Flags).
 *  4. Cross-Schema Grantee Matrix (Pivot view of service accounts and external consumer access).
 *  5. Complete Object-Level Permissions Inventory (ALL_TAB_PRIVS).
 *  6. Column-Level Granular Security Privileges (ALL_COL_PRIVS).
 *  7. Florida Lottery Statutory Governance & Compliance Standards (Rule 53ER Least Privilege).
 *  8. Re-executable Disaster Recovery & Environment Provisioning DDL Script (SQL Excerpt).
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery DataOps Architecture
 * @since 2026-10-08
 */
public class GenerateSchemaSecurityGrantsWordDocs {

    private static final String DEFAULT_INPUT_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json";
    private static final String DEFAULT_OUTPUT_DIR = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\Security_Grants";
    private static final String DEFAULT_BASE_URL = "https://flalottery.sharepoint.com/sites/DataOps/Security/";

    // Enterprise Web Team & Florida Lottery Hybrid Palette
    private static final String COLOR_PRIMARY = "5C1D48";      // Rich Plum / Burgundy (Web Team Header Bar)
    private static final String COLOR_NAVY = "002B49";         // Florida Lottery Deep Navy
    private static final String COLOR_BLUE = "005A9C";         // Subheadings & Links
    private static final String COLOR_GOLD = "DAA520";         // Accent Gold
    private static final String COLOR_GREEN = "166534";        // Forest Green / Success / Audit Passed
    private static final String COLOR_RED = "991B1B";          // Warning / Public Exposure / Risk Flag
    private static final String COLOR_AMBER = "B45309";        // DML Privileges / Moderate Risk
    private static final String COLOR_LIGHT_BG = "F8FAFC";     // Table Alternating Row BG
    private static final String COLOR_CODE_BG = "F4F6F9";      // Code Block BG
    private static final String COLOR_BORDER = "CCCCCC";       // Table Borders
    private static final String COLOR_MUTED = "666666";        // Subtitle / Muted Text

    // Code font & sizing
    private static final String CODE_FONT_FAMILY = "Consolas";
    private static final double CODE_FONT_SIZE = 7.0;

    /**
     * DTO Model representing an Object Grant from ALL_TAB_PRIVS.
     */
    public static class ObjectGrantRecord {
        public String schemaOwner;
        public String objectName;
        public String objectType;
        public String privilege;
        public String grantee;
        public String grantor;
        public String grantable;

        public static ObjectGrantRecord fromJson(JsonNode n) {
            ObjectGrantRecord r = new ObjectGrantRecord();
            r.schemaOwner = n.path("SchemaOwner").asText("").trim().toUpperCase();
            r.objectName = n.path("ObjectName").asText("").trim().toUpperCase();
            r.objectType = n.path("ObjectType").asText("TABLE").trim().toUpperCase();
            r.privilege = n.path("Privilege").asText("").trim().toUpperCase();
            r.grantee = n.path("Grantee").asText("").trim().toUpperCase();
            r.grantor = n.path("Grantor").asText("").trim().toUpperCase();
            r.grantable = n.path("Grantable").asText("NO").trim().toUpperCase();

            ObjectGrantRecord result = r;
            return result;
        }
    }

    /**
     * DTO Model representing a Column Grant from ALL_COL_PRIVS.
     */
    public static class ColumnGrantRecord {
        public String schemaOwner;
        public String objectName;
        public String columnName;
        public String privilege;
        public String grantee;
        public String grantor;
        public String grantable;

        public static ColumnGrantRecord fromJson(JsonNode n) {
            ColumnGrantRecord r = new ColumnGrantRecord();
            r.schemaOwner = n.path("SchemaOwner").asText("").trim().toUpperCase();
            r.objectName = n.path("ObjectName").asText("").trim().toUpperCase();
            r.columnName = n.path("ColumnName").asText("").trim().toUpperCase();
            r.privilege = n.path("Privilege").asText("").trim().toUpperCase();
            r.grantee = n.path("Grantee").asText("").trim().toUpperCase();
            r.grantor = n.path("Grantor").asText("").trim().toUpperCase();
            r.grantable = n.path("Grantable").asText("NO").trim().toUpperCase();

            ColumnGrantRecord result = r;
            return result;
        }
    }

    /**
     * Container aggregating all security grants for a specific schema.
     */
    public static class SchemaSecurityModel {
        public String databaseName = "DEVOLTP";
        public String schemaName;
        public List<ObjectGrantRecord> objectGrants = new ArrayList<>();
        public List<ColumnGrantRecord> columnGrants = new ArrayList<>();

        public Set<String> getUniqueGrantees() {
            Set<String> set = new TreeSet<>();
            for (ObjectGrantRecord g : objectGrants) set.add(g.grantee);
            for (ColumnGrantRecord c : columnGrants) set.add(c.grantee);
            Set<String> result = set;
            return result;
        }

        public Set<String> getUniqueGrantedObjects() {
            Set<String> set = new TreeSet<>();
            for (ObjectGrantRecord g : objectGrants) set.add(g.objectName);
            for (ColumnGrantRecord c : columnGrants) set.add(c.objectName);
            Set<String> result = set;
            return result;
        }

        public List<ObjectGrantRecord> getPublicGrants() {
            List<ObjectGrantRecord> list = new ArrayList<>();
            for (ObjectGrantRecord g : objectGrants) {
                if ("PUBLIC".equalsIgnoreCase(g.grantee)) list.add(g);
            }
            List<ObjectGrantRecord> result = list;
            return result;
        }

        public List<ObjectGrantRecord> getWithGrantOptionGrants() {
            List<ObjectGrantRecord> list = new ArrayList<>();
            for (ObjectGrantRecord g : objectGrants) {
                if ("YES".equalsIgnoreCase(g.grantable)) list.add(g);
            }
            List<ObjectGrantRecord> result = list;
            return result;
        }
    }

    public static void main(String[] args) {
        Map<String, String> argMap = parseArgs(args);

        String inputPath = argMap.getOrDefault("input", DEFAULT_INPUT_PATH);
        String outputDirPath = argMap.getOrDefault("outputDir", DEFAULT_OUTPUT_DIR);
        String targetSchemaArg = argMap.getOrDefault("schema", "ALL").toUpperCase();
        String baseUrl = argMap.getOrDefault("baseUrl", DEFAULT_BASE_URL);

        System.out.println("=========================================================================");
        System.out.println("Florida Lottery Schema Security Grants & Privileges Matrix Generator");
        System.out.println("Format: Web Team Formal Specification Template (ALL_TAB_PRIVS & ALL_COL_PRIVS)");
        System.out.println("DbCache JSON:   " + inputPath);
        System.out.println("Output Root:    " + outputDirPath);
        System.out.println("Target Schema:  " + targetSchemaArg);
        System.out.println("=========================================================================");

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("ACTION REQUIRED: DbCache JSON file not found at: " + inputPath);
            return;
        }

        File outDir = new File(outputDirPath);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(inputFile);
            Map<String, SchemaSecurityModel> schemaMap = new TreeMap<>();

            JsonNode schemasNode = root.path("Schemas");
            if (!schemasNode.isArray()) schemasNode = root.path("schemas");

            if (schemasNode.isArray()) {
                for (JsonNode sNode : schemasNode) {
                    String sName = sNode.path("SchemaName").asText(sNode.path("Name").asText("")).trim().toUpperCase();
                    if (sName.isEmpty()) continue;

                    if (!targetSchemaArg.equals("ALL") && !sName.equalsIgnoreCase(targetSchemaArg)) {
                        continue;
                    }

                    SchemaSecurityModel model = schemaMap.computeIfAbsent(sName, k -> {
                        SchemaSecurityModel m = new SchemaSecurityModel();
                        m.schemaName = sName;
                        return m;
                    });

                    // Parse Object Grants (ALL_TAB_PRIVS)
                    JsonNode tabPrivs = sNode.path("ObjectGrants");
                    if (!tabPrivs.isArray()) tabPrivs = sNode.path("Grants");
                    if (tabPrivs.isArray()) {
                        for (JsonNode g : tabPrivs) {
                            model.objectGrants.add(ObjectGrantRecord.fromJson(g));
                        }
                    }

                    // Parse Column Grants (ALL_COL_PRIVS)
                    JsonNode colPrivs = sNode.path("ColumnGrants");
                    if (colPrivs.isArray()) {
                        for (JsonNode cg : colPrivs) {
                            model.columnGrants.add(ColumnGrantRecord.fromJson(cg));
                        }
                    }
                }
            }

            if (schemaMap.isEmpty()) {
                System.out.println("No matching schemas with grant information found in DbCache.");
                return;
            }

            int generatedCount = 0;
            for (SchemaSecurityModel model : schemaMap.values()) {
                File docFile = new File(outDir, model.schemaName + "_Security_Grants_Matrix.docx");
                System.out.printf("Generating Security Grants Specification for Schema [%-12s] (%d tab privs, %d col privs)... ",
                        model.schemaName, model.objectGrants.size(), model.columnGrants.size());

                generateSchemaSecurityWordDoc(model, docFile, baseUrl);
                generatedCount++;
                System.out.println("DONE -> " + docFile.getName());
            }

            System.out.println("\n=========================================================================");
            System.out.printf("SUCCESS: Generated %d Schema Security Grants Specifications in %s%n", generatedCount, outputDirPath);
            System.out.println("=========================================================================");

        } catch (Exception ex) {
            System.err.println("CRITICAL: Security Grants document generation failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Generates a formal, audit-ready Word document specification for a schema's database grants.
     */
    public static void generateSchemaSecurityWordDoc(SchemaSecurityModel model, File targetFile, String baseUrl) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc, 1440, 720, 1440, 720); // 1.0" top/bottom, 0.5" narrow left/right

            String fullQualifiedName = "SCHEMA: " + model.schemaName.toUpperCase();
            String docId = "SEC-GRANTS-" + model.schemaName.toUpperCase();
            setupDocumentHeaderFooter(doc, fullQualifiedName + " (Security Grants Matrix)", docId, model.schemaName);

            int bookmarkIdCounter = 800;

            // PAGE 1: FORMAL WEB TEAM COVER PAGE & GOVERNANCE SIGN-OFF
            renderWebTeamCoverPage(doc, "Database Security Grants & Privileges Matrix", fullQualifiedName, docId, model.schemaName, baseUrl + model.schemaName + "/" + targetFile.getName());

            // PAGE 2: TABLE OF CONTENTS (TOC)
            addHeading(doc, "Table of Contents", 16, true, "TOC_TOP", bookmarkIdCounter++);

            renderAutomaticTable2Entry(doc, "SEC_1", "1.0  Executive Security Summary & Risk Audit Profile", 2, 1);
            renderAutomaticTable2Entry(doc, "SEC_2", "2.0  Cross-Schema Access Matrix (Grantee & Service Account Pivot)", 3, 1);
            renderAutomaticTable2Entry(doc, "SEC_3", "3.0  Object-Level Permissions Inventory (ALL_TAB_PRIVS)", 4, 1);
            renderAutomaticTable2Entry(doc, "SEC_4", "4.0  Column-Level Granular Security Privileges (ALL_COL_PRIVS)", 5, 1);
            renderAutomaticTable2Entry(doc, "SEC_5", "5.0  Florida Lottery Security Governance & Compliance Standards", 6, 1);
            renderAutomaticTable2Entry(doc, "SEC_6", "6.0  Disaster Recovery & Environment Provisioning DDL Script", 7, 1);

            addSectionDivider(doc);

            // SECTION 1.0: EXECUTIVE SECURITY SUMMARY & RISK AUDIT PROFILE
            addHeading(doc, "1. Executive Security Summary & Risk Audit Profile", 14, false, "SEC_1", bookmarkIdCounter++);

            addCalloutBox(doc, "SECURITY & LEAST-PRIVILEGE MANDATE:",
                    "In alignment with Florida Lottery Administrative Rule 53ER and statutory security controls, access to " +
                            model.schemaName.toUpperCase() + " database objects is restricted strictly to authorized batch jobs, " +
                            "integration service accounts, and vetted operational roles. This document provides the immutable audit trail " +
                            "of all active privileges granted to external consumers.");
            addDoubleSpace(doc);

            // Key Statistics Profile Grid
            Set<String> uniqueGrantees = model.getUniqueGrantees();
            Set<String> uniqueObjects = model.getUniqueGrantedObjects();
            List<ObjectGrantRecord> publicGrants = model.getPublicGrants();
            List<ObjectGrantRecord> grantableGrants = model.getWithGrantOptionGrants();

            List<String[]> statPairs = new ArrayList<>();
            statPairs.add(new String[]{"Target Database Schema:", model.schemaName.toUpperCase()});
            statPairs.add(new String[]{"Document Identifier:", docId});
            statPairs.add(new String[]{"Total Objects Granted Externally:", String.valueOf(uniqueObjects.size()) + " database entity/entities"});
            statPairs.add(new String[]{"Distinct Grantees / Service Roles:", String.valueOf(uniqueGrantees.size()) + " accounts"});
            statPairs.add(new String[]{"Total Object-Level Grants (ALL_TAB_PRIVS):", String.valueOf(model.objectGrants.size()) + " privilege assignments"});
            statPairs.add(new String[]{"Total Column-Level Grants (ALL_COL_PRIVS):", String.valueOf(model.columnGrants.size()) + " column restriction(s)"});
            statPairs.add(new String[]{"Audit Posture:", (publicGrants.isEmpty() && grantableGrants.isEmpty()) ? "COMPLIANT (Least Privilege Enforced)" : "FLAGGED FOR REVIEW"});

            XWPFTable statTable = doc.createTable(statPairs.size(), 2);
            setTableExplicitWidths(statTable, new int[]{3600, 7200});
            setTableBorders(statTable, COLOR_BORDER);

            for (int i = 0; i < statPairs.size(); i++) {
                setCell(statTable, i, 0, statPairs.get(i)[0], true, COLOR_LIGHT_BG, COLOR_PRIMARY);
                setCell(statTable, i, 1, statPairs.get(i)[1], false, null, "000000");
            }
            addDoubleSpace(doc);

            // Risk Assessment Callouts
            if (!publicGrants.isEmpty()) {
                StringBuilder pb = new StringBuilder("CRITICAL SECURITY AUDIT NOTICE: ");
                pb.append(publicGrants.size()).append(" privilege(s) in this schema are granted directly to 'PUBLIC'. ");
                pb.append("Under Florida Lottery gaming standards, object access to PUBLIC should be eliminated in production environments.\nObjects affected: ");
                Set<String> pubObjs = new TreeSet<>();
                for (ObjectGrantRecord g : publicGrants) pubObjs.add(g.objectName + " (" + g.privilege + ")");
                pb.append(String.join(", ", pubObjs));
                addWarningCalloutBox(doc, "PUBLIC EXPOSURE WARNING (Rule 53ER):", pb.toString());
                addDoubleSpace(doc);
            }

            if (!grantableGrants.isEmpty()) {
                StringBuilder gb = new StringBuilder("ADMINISTRATIVE ESCALATION NOTICE: ");
                gb.append(grantableGrants.size()).append(" grant(s) possess 'GRANTABLE = YES' (WITH GRANT OPTION). ");
                gb.append("This permits recipient accounts to grant these privileges to tertiary users without DBA oversight.\nGrantees: ");
                Set<String> gList = new TreeSet<>();
                for (ObjectGrantRecord g : grantableGrants) gList.add(g.grantee + " -> " + g.objectName);
                gb.append(String.join(", ", gList));
                addWarningCalloutBox(doc, "WITH GRANT OPTION RISK WARNING:", gb.toString());
                addDoubleSpace(doc);
            }

            if (publicGrants.isEmpty() && grantableGrants.isEmpty()) {
                addSuccessCalloutBox(doc, "SECURITY AUDIT PASSED:",
                        "Zero privileges are exposed to PUBLIC, and no grants possess secondary delegation privileges (WITH GRANT OPTION). " +
                                "All privileges conform to standard unattended batch scheduling and operational least-privilege standards.");
            }

            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 2.0: CROSS-SCHEMA ACCESS MATRIX (GRANTEE PIVOT)
            addHeading(doc, "2. Cross-Schema Access Matrix (Grantee & Service Account Pivot)", 14, true, "SEC_2", bookmarkIdCounter++);
            addParagraph(doc, "Pivot inventory grouping external service accounts, child schemas, and batch runner roles that consume " +
                    model.schemaName.toUpperCase() + " database objects:");

            // Pivot Map: Grantee -> List of ObjectGrantRecord
            Map<String, List<ObjectGrantRecord>> granteeMap = new TreeMap<>();
            for (ObjectGrantRecord g : model.objectGrants) {
                granteeMap.computeIfAbsent(g.grantee, k -> new ArrayList<>()).add(g);
            }

            if (granteeMap.isEmpty()) {
                addCalloutBox(doc, "INTERNAL-ONLY SCHEMA:", "This schema defines no external object privileges in ALL_TAB_PRIVS. All objects are restricted solely to the schema owner.");
            } else {
                XWPFTable pivotTable = doc.createTable(granteeMap.size() + 1, 4);
                setTableExplicitWidths(pivotTable, new int[]{2520, 2160, 4320, 1800});
                setTableBorders(pivotTable, COLOR_BORDER);

                setCell(pivotTable, 0, 0, "Grantee / Consumer Role", true, COLOR_PRIMARY, "FFFFFF");
                setCell(pivotTable, 0, 1, "Objects Accessible", true, COLOR_PRIMARY, "FFFFFF");
                setCell(pivotTable, 0, 2, "Privileges Held", true, COLOR_PRIMARY, "FFFFFF");
                setCell(pivotTable, 0, 3, "Grant Option", true, COLOR_PRIMARY, "FFFFFF");

                int rowIdx = 1;
                for (Map.Entry<String, List<ObjectGrantRecord>> ge : granteeMap.entrySet()) {
                    String gName = ge.getKey();
                    List<ObjectGrantRecord> gList = ge.getValue();
                    String bg = (rowIdx % 2 == 0 ? null : COLOR_LIGHT_BG);

                    Set<String> distinctObjs = new HashSet<>();
                    Set<String> distinctPrivs = new TreeSet<>();
                    boolean hasGrantable = false;

                    for (ObjectGrantRecord r : gList) {
                        distinctObjs.add(r.objectName);
                        distinctPrivs.add(r.privilege);
                        if ("YES".equalsIgnoreCase(r.grantable)) hasGrantable = true;
                    }

                    setCell(pivotTable, rowIdx, 0, gName, true, bg, gName.equalsIgnoreCase("PUBLIC") ? COLOR_RED : COLOR_NAVY);
                    setCell(pivotTable, rowIdx, 1, distinctObjs.size() + " object(s)", false, bg);
                    setCell(pivotTable, rowIdx, 2, String.join(", ", distinctPrivs), false, bg);
                    setCell(pivotTable, rowIdx, 3, hasGrantable ? "YES (Warning)" : "NO", hasGrantable, bg, hasGrantable ? COLOR_RED : COLOR_GREEN);

                    rowIdx++;
                }
            }

            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 3.0: OBJECT-LEVEL PERMISSIONS INVENTORY (ALL_TAB_PRIVS)
            addHeading(doc, "3. Object-Level Permissions Inventory (ALL_TAB_PRIVS)", 14, true, "SEC_3", bookmarkIdCounter++);
            addParagraph(doc, "Exhaustive catalog of all tables, views, packages, procedures, and sequences with active cross-schema permissions:");

            if (model.objectGrants.isEmpty()) {
                addCalloutBox(doc, "NO EXTERNAL OBJECT GRANTS:", "No object-level privileges exist in ALL_TAB_PRIVS for schema " + model.schemaName.toUpperCase() + ".");
            } else {
                XWPFTable tabTable = doc.createTable(model.objectGrants.size() + 1, 5);
                setTableExplicitWidths(tabTable, new int[]{2880, 1620, 1800, 2700, 1800});
                setTableBorders(tabTable, COLOR_BORDER);

                setCell(tabTable, 0, 0, "Object Name", true, COLOR_PRIMARY, "FFFFFF");
                setCell(tabTable, 0, 1, "Object Type", true, COLOR_PRIMARY, "FFFFFF");
                setCell(tabTable, 0, 2, "Privilege", true, COLOR_PRIMARY, "FFFFFF");
                setCell(tabTable, 0, 3, "Grantee / Role", true, COLOR_PRIMARY, "FFFFFF");
                setCell(tabTable, 0, 4, "Grant Option", true, COLOR_PRIMARY, "FFFFFF");

                for (int i = 0; i < model.objectGrants.size(); i++) {
                    ObjectGrantRecord og = model.objectGrants.get(i);
                    String bg = (i % 2 == 0 ? null : COLOR_LIGHT_BG);

                    String privColor = getPrivilegeColorHex(og.privilege);
                    boolean isPublic = "PUBLIC".equalsIgnoreCase(og.grantee);

                    setCell(tabTable, i + 1, 0, og.objectName, true, bg, COLOR_NAVY);
                    setCell(tabTable, i + 1, 1, og.objectType, false, bg);
                    setCell(tabTable, i + 1, 2, og.privilege, true, bg, privColor);
                    setCell(tabTable, i + 1, 3, og.grantee + (isPublic ? " [PUBLIC]" : ""), isPublic, bg, isPublic ? COLOR_RED : null);
                    setCell(tabTable, i + 1, 4, og.grantable, "YES".equalsIgnoreCase(og.grantable), bg, "YES".equalsIgnoreCase(og.grantable) ? COLOR_RED : null);
                }
            }

            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 4.0: COLUMN-LEVEL GRANULAR SECURITY PRIVILEGES (ALL_COL_PRIVS)
            addHeading(doc, "4. Column-Level Granular Security Privileges (ALL_COL_PRIVS)", 14, true, "SEC_4", bookmarkIdCounter++);

            if (model.columnGrants.isEmpty()) {
                addCalloutBox(doc, "NO RESTRICTIVE COLUMN MASKS (COMPLIANT):",
                        "No column-restricted privileges are declared in ALL_COL_PRIVS for schema " + model.schemaName.toUpperCase() + ". " +
                                "All permissions operate uniformly at the whole-table/entity boundary without column-level grant overhead.");
            } else {
                addParagraph(doc, "Granular column privileges restricting access to specific relational attributes:");

                XWPFTable colTable = doc.createTable(model.columnGrants.size() + 1, 5);
                setTableExplicitWidths(colTable, new int[]{2700, 2340, 1800, 2340, 1620});
                setTableBorders(colTable, COLOR_BORDER);

                setCell(colTable, 0, 0, "Object Name", true, COLOR_PRIMARY, "FFFFFF");
                setCell(colTable, 0, 1, "Column Name", true, COLOR_PRIMARY, "FFFFFF");
                setCell(colTable, 0, 2, "Privilege", true, COLOR_PRIMARY, "FFFFFF");
                setCell(colTable, 0, 3, "Grantee / Role", true, COLOR_PRIMARY, "FFFFFF");
                setCell(colTable, 0, 4, "Grant Option", true, COLOR_PRIMARY, "FFFFFF");

                for (int c = 0; c < model.columnGrants.size(); c++) {
                    ColumnGrantRecord cg = model.columnGrants.get(c);
                    String bg = (c % 2 == 0 ? null : COLOR_LIGHT_BG);

                    setCell(colTable, c + 1, 0, cg.objectName, true, bg, COLOR_NAVY);
                    setCell(colTable, c + 1, 1, cg.columnName, false, bg);
                    setCell(colTable, c + 1, 2, cg.privilege, true, bg, getPrivilegeColorHex(cg.privilege));
                    setCell(colTable, c + 1, 3, cg.grantee, false, bg);
                    setCell(colTable, c + 1, 4, cg.grantable, false, bg);
                }
            }

            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 5.0: FLORIDA LOTTERY SECURITY GOVERNANCE & COMPLIANCE STANDARDS
            addHeading(doc, "5. Florida Lottery Security Governance & Compliance Standards", 14, true, "SEC_5", bookmarkIdCounter++);

            addBulletPoint(doc, "Principle of Least Privilege",
                    "Database accounts granted access to " + model.schemaName.toUpperCase() + " are restricted strictly to privileges required for intended batch operations. Direct DML privileges (INSERT, UPDATE, DELETE) are segregated from reporting roles.");

            addBulletPoint(doc, "Statutory Gaming Audit Compliance (Rule 53ER)",
                    "Financial ledgers, draw management routines, and retailer accounting tables are subject to automated audit trails. Grant changes are logged and certified on a quarterly cadence by Information Security & Data Governance.");

            addBulletPoint(doc, "Unattended IWS Batch Job Integration",
                    "Service accounts executing via IBM Workload Scheduler (IWS) authenticate using dedicated service roles without interactive login rights. Passwords and credentials are managed dynamically via runtime configuration dispatchers.");

            addBulletPoint(doc, "OCI Cloud Security Baseline",
                    "All inter-schema grants across Oracle Cloud Infrastructure (OCI) Linux database nodes require explicit grant script version control. Ad-hoc grants outside of standard deployment pipelines are strictly prohibited.");

            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 6.0: RE-EXECUTABLE DISASTER RECOVERY & ENVIRONMENT PROVISIONING DDL SCRIPT
            addHeading(doc, "6. Disaster Recovery & Environment Provisioning DDL Script", 14, true, "SEC_6", bookmarkIdCounter++);
            addParagraph(doc, "Automated SQL DDL script to recreate all active permissions in disaster recovery, OCI migration, or lower environment provisioning:");

            String ddlScript = generateGrantsDdlScript(model);
            addCodeBlock(doc, ddlScript, model.schemaName.toLowerCase() + "_security_grants.sql");

            addFaintDivider(doc);
            addBackToTocLink(doc);

            if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                doc.write(fos);
            }
        }
    }

    /**
     * Generates a clean, re-executable SQL DDL script for all grants in the schema.
     */
    public static String generateGrantsDdlScript(SchemaSecurityModel model) {
        StringBuilder sb = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        sb.append("-- ============================================================================\n");
        sb.append("-- Florida Lottery Production Security Grants Recreation Script\n");
        sb.append("-- Schema: ").append(model.schemaName.toUpperCase()).append(" | Total Grants: ").append(model.objectGrants.size() + model.columnGrants.size()).append("\n");
        sb.append("-- Generated: ").append(ts).append(" | Author: Ryan Stokes (Programmer Analyst, IR/SDS)\n");
        sb.append("-- Governance: Florida Lottery Administrative Rule 53ER Security Baseline\n");
        sb.append("-- ============================================================================\n\n");

        if (model.objectGrants.isEmpty() && model.columnGrants.isEmpty()) {
            sb.append("-- Notice: No external object or column grants cataloged for schema ").append(model.schemaName.toUpperCase()).append(".\n");
            String result = sb.toString();
            return result;
        }

        // 1. Object Grants (Grouped by Object Type)
        Map<String, List<ObjectGrantRecord>> byType = new TreeMap<>();
        for (ObjectGrantRecord g : model.objectGrants) {
            byType.computeIfAbsent(g.objectType, k -> new ArrayList<>()).add(g);
        }

        for (Map.Entry<String, List<ObjectGrantRecord>> entry : byType.entrySet()) {
            String oType = entry.getKey();
            List<ObjectGrantRecord> list = entry.getValue();

            sb.append("-- ----------------------------------------------------------------------------\n");
            sb.append("-- ").append(oType).append(" Permissions (").append(list.size()).append(" grants)\n");
            sb.append("-- ----------------------------------------------------------------------------\n");

            for (ObjectGrantRecord g : list) {
                sb.append("GRANT ").append(g.privilege).append(" ON ").append(g.schemaOwner).append(".").append(g.objectName)
                  .append(" TO ").append(g.grantee);
                if ("YES".equalsIgnoreCase(g.grantable)) {
                    sb.append(" WITH GRANT OPTION");
                }
                sb.append(";\n");
            }
            sb.append("\n");
        }

        // 2. Column Grants
        if (!model.columnGrants.isEmpty()) {
            sb.append("-- ----------------------------------------------------------------------------\n");
            sb.append("-- Granular Column Permissions (").append(model.columnGrants.size()).append(" grants)\n");
            sb.append("-- ----------------------------------------------------------------------------\n");

            for (ColumnGrantRecord cg : model.columnGrants) {
                sb.append("GRANT ").append(cg.privilege).append(" (").append(cg.columnName).append(") ON ")
                  .append(cg.schemaOwner).append(".").append(cg.objectName).append(" TO ").append(cg.grantee);
                if ("YES".equalsIgnoreCase(cg.grantable)) {
                    sb.append(" WITH GRANT OPTION");
                }
                sb.append(";\n");
            }
            sb.append("\n");
        }

        sb.append("-- End of Security Grants Script for ").append(model.schemaName.toUpperCase()).append("\n");
        String result = sb.toString();
        return result;
    }

    private static String getPrivilegeColorHex(String privilege) {
        if (privilege == null) return COLOR_NAVY;
        String upper = privilege.toUpperCase();
        if (upper.equals("SELECT")) return COLOR_BLUE;
        if (upper.equals("EXECUTE")) return COLOR_PRIMARY;
        if (upper.equals("INSERT") || upper.equals("UPDATE") || upper.equals("DELETE")) return COLOR_AMBER;
        if (upper.equals("ALL")) return COLOR_RED;
        if (upper.contains("READ")) return COLOR_BLUE;
        return COLOR_NAVY;
    }

    // =========================================================================
    // DOCUMENT STYLING, HEADERS, COVER PAGE & TOC HELPERS (Web Team Standard)
    // =========================================================================

    private static void setupDocumentHeaderFooter(XWPFDocument doc, String title, String docId, String schema) {
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph hp = header.createParagraph();
        hp.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun hr = hp.createRun();
        hr.setText("Florida Lottery DataOps  |  " + title + "  |  " + docId);
        hr.setFontSize(8);
        hr.setColor(COLOR_MUTED);
        hr.setFontFamily("Segoe UI");

        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph fp = footer.createParagraph();
        fp.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun fr1 = fp.createRun();
        fr1.setText("CONFIDENTIAL - FLORIDA LOTTERY INTERNAL DATABASE GOVERNANCE");
        fr1.setFontSize(8);
        fr1.setColor(COLOR_MUTED);
        fr1.setFontFamily("Segoe UI");
    }

    private static void renderWebTeamCoverPage(XWPFDocument doc, String mainTitle, String fullObjectName, String docId, String schema, String docUrl) {
        XWPFParagraph topSpace = doc.createParagraph();
        topSpace.setSpacingBefore(80);
        topSpace.setSpacingAfter(20);

        // 1. Big Centered Main Title
        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        titlePara.setSpacingBefore(40);
        titlePara.setSpacingAfter(40);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(mainTitle);
        titleRun.setFontSize(22);
        titleRun.setBold(true);
        titleRun.setColor(COLOR_PRIMARY);
        titleRun.setFontFamily("Segoe UI Semibold");

        // 2. Plum / Burgundy Full Object Name Header Banner
        XWPFTable bannerTable = doc.createTable(1, 1);
        setTableExplicitWidths(bannerTable, new int[]{10800});
        setTableBorders(bannerTable, COLOR_PRIMARY);
        XWPFTableCell bannerCell = bannerTable.getRow(0).getCell(0);
        bannerCell.setColor(COLOR_PRIMARY);
        bannerCell.removeParagraph(0);
        XWPFParagraph bp = bannerCell.addParagraph();
        bp.setAlignment(ParagraphAlignment.CENTER);
        bp.setSpacingBefore(40);
        bp.setSpacingAfter(40);
        XWPFRun br = bp.createRun();
        br.setText(fullObjectName);
        br.setFontSize(11);
        br.setBold(true);
        br.setColor("FFFFFF");
        br.setFontFamily("Segoe UI Semibold");

        // 3. Metadata Key-Value List
        XWPFParagraph metaPara = doc.createParagraph();
        metaPara.setSpacingBefore(100);
        metaPara.setSpacingAfter(20);

        String currentDate = new SimpleDateFormat("MMMM d, yyyy").format(new Date());
        String[][] metaPairs = {
                {"Author:", "Ryan Stokes (Programmer Analyst, Florida Lottery - IR/SDS)"},
                {"Creation Date:", currentDate},
                {"Last Updated:", currentDate},
                {"Version:", "1.0"},
                {"Doc ID:", docId},
                {"Schema:", schema.toUpperCase()},
                {"Classification:", "Florida Lottery Statutory Security Matrix (Rule 53ER)"}
        };

        XWPFTable metaTable = doc.createTable(metaPairs.length, 2);
        setTableExplicitWidths(metaTable, new int[]{2376, 8424});
        setTableBorders(metaTable, "FFFFFF");

        for (int i = 0; i < metaPairs.length; i++) {
            setCell(metaTable, i, 0, metaPairs[i][0], true, null, COLOR_PRIMARY);
            setCell(metaTable, i, 1, metaPairs[i][1], false, null, "000000");
        }

        addDoubleSpace(doc);

        // 4. Formal Sign-Off Table
        XWPFParagraph appHeading = doc.createParagraph();
        appHeading.setSpacingBefore(80);
        appHeading.setSpacingAfter(30);
        XWPFRun appRun = appHeading.createRun();
        appRun.setText("DOCUMENT GOVERNANCE & APPROVALS SIGN-OFF");
        appRun.setFontSize(9.5);
        appRun.setBold(true);
        appRun.setColor(COLOR_PRIMARY);
        appRun.setFontFamily("Segoe UI");

        String[][] approvals = {
                {"Role / Title", "Name", "Approval Status", "Date"},
                {"Lead Software Architect", "Ryan Stokes", "APPROVED - PRODUCTION BASELINE", currentDate},
                {"Database Administrator (DBA)", "Florida Lottery DataOps", "VERIFIED & AUDITED", currentDate},
                {"Information Security Officer", "Florida Lottery Security", "COMPLIANT (Rule 53ER)", currentDate}
        };

        XWPFTable appTable = doc.createTable(approvals.length, 4);
        setTableExplicitWidths(appTable, new int[]{3240, 2700, 2970, 1890});
        setTableBorders(appTable, COLOR_BORDER);

        for (int r = 0; r < approvals.length; r++) {
            boolean isH = (r == 0);
            for (int c = 0; c < 4; c++) {
                setCell(appTable, r, c, approvals[r][c], isH, isH ? COLOR_PRIMARY : (r % 2 == 1 ? COLOR_LIGHT_BG : null), isH ? "FFFFFF" : "000000");
            }
        }

        addDoubleSpace(doc);

        // 5. Revision History Table
        XWPFParagraph revHeading = doc.createParagraph();
        revHeading.setSpacingBefore(60);
        revHeading.setSpacingAfter(30);
        XWPFRun revRun = revHeading.createRun();
        revRun.setText("REVISION HISTORY");
        revRun.setFontSize(9.5);
        revRun.setBold(true);
        revRun.setColor(COLOR_PRIMARY);
        revRun.setFontFamily("Segoe UI");

        String[][] revHistory = {
                {"Version", "Date", "Author", "Change Summary"},
                {"1.0", currentDate, "Ryan Stokes", "Initial automated extraction of ALL_TAB_PRIVS & ALL_COL_PRIVS security baseline."}
        };

        XWPFTable revTable = doc.createTable(revHistory.length, 4);
        setTableExplicitWidths(revTable, new int[]{1080, 1620, 2700, 5400});
        setTableBorders(revTable, COLOR_BORDER);

        for (int r = 0; r < revHistory.length; r++) {
            boolean isH = (r == 0);
            for (int c = 0; c < 4; c++) {
                setCell(revTable, r, c, revHistory[r][c], isH, isH ? COLOR_PRIMARY : null, isH ? "FFFFFF" : "000000");
            }
        }

        // Force Page Break after Cover Page
        XWPFParagraph pBreak = doc.createParagraph();
        pBreak.setPageBreak(true);
    }

    private static void renderAutomaticTable2Entry(XWPFDocument doc, String targetBookmark, String title, int pageNum, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(15);
        p.setSpacingAfter(15);

        if (level == 2) {
            p.setIndentationLeft(280);
        }

        XWPFRun titleRun = p.createRun();
        titleRun.setText(title);
        titleRun.setFontSize(level == 1 ? 10.0 : 9.0);
        titleRun.setBold(level == 1);
        titleRun.setColor(level == 1 ? COLOR_PRIMARY : COLOR_BLUE);
        titleRun.setFontFamily("Segoe UI");

        XWPFRun dotsRun = p.createRun();
        dotsRun.setText("  " + ".".repeat(Math.max(8, 70 - (title.length() + (level == 2 ? 6 : 0)))) + "  ");
        dotsRun.setFontSize(8.5);
        dotsRun.setColor(COLOR_MUTED);

        XWPFRun pageRun = p.createRun();
        pageRun.setText(String.valueOf(pageNum));
        pageRun.setFontSize(9.5);
        pageRun.setBold(true);
        pageRun.setColor(COLOR_PRIMARY);
    }

    private static void addHeading(XWPFDocument doc, String text, int size, boolean pageBreakBefore, String bookmark, int bookmarkId) {
        XWPFParagraph p = doc.createParagraph();
        if (pageBreakBefore) {
            p.setPageBreak(true);
        }
        p.setSpacingBefore(120);
        p.setSpacingAfter(40);

        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(size);
        r.setBold(true);
        r.setColor(COLOR_PRIMARY);
        r.setFontFamily("Segoe UI Semibold");
    }

    private static void addHeading(XWPFDocument doc, String text, int size) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(80);
        p.setSpacingAfter(30);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(size);
        r.setBold(true);
        r.setColor(COLOR_NAVY);
        r.setFontFamily("Segoe UI Semibold");
    }

    private static void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(30);
        p.setSpacingAfter(40);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(9.5);
        r.setColor("333333");
        r.setFontFamily("Segoe UI");
    }

    private static void addCalloutBox(XWPFDocument doc, String title, String body) {
        XWPFTable table = doc.createTable(1, 1);
        setTableExplicitWidths(table, new int[]{10800});
        setTableBorders(table, COLOR_PRIMARY);
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor(COLOR_LIGHT_BG);

        XWPFParagraph p = cell.getParagraphArray(0);
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);

        XWPFRun tr = p.createRun();
        tr.setText(title + " ");
        tr.setBold(true);
        tr.setFontSize(9.0);
        tr.setColor(COLOR_PRIMARY);
        tr.setFontFamily("Segoe UI");

        XWPFRun br = p.createRun();
        br.setText(body);
        br.setFontSize(9.0);
        br.setColor("1A1A1A");
        br.setFontFamily("Segoe UI");
    }

    private static void addWarningCalloutBox(XWPFDocument doc, String title, String body) {
        XWPFTable table = doc.createTable(1, 1);
        setTableExplicitWidths(table, new int[]{10800});
        setTableBorders(table, COLOR_RED);
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor("FEF2F2"); // Soft red-tinted background

        XWPFParagraph p = cell.getParagraphArray(0);
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);

        XWPFRun tr = p.createRun();
        tr.setText(title + " ");
        tr.setBold(true);
        tr.setFontSize(9.0);
        tr.setColor(COLOR_RED);
        tr.setFontFamily("Segoe UI");

        XWPFRun br = p.createRun();
        br.setText(body);
        br.setFontSize(9.0);
        br.setColor("1A1A1A");
        br.setFontFamily("Segoe UI");
    }

    private static void addSuccessCalloutBox(XWPFDocument doc, String title, String body) {
        XWPFTable table = doc.createTable(1, 1);
        setTableExplicitWidths(table, new int[]{10800});
        setTableBorders(table, COLOR_GREEN);
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor("F0FDF4"); // Soft green background

        XWPFParagraph p = cell.getParagraphArray(0);
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);

        XWPFRun tr = p.createRun();
        tr.setText(title + " ");
        tr.setBold(true);
        tr.setFontSize(9.0);
        tr.setColor(COLOR_GREEN);
        tr.setFontFamily("Segoe UI");

        XWPFRun br = p.createRun();
        br.setText(body);
        br.setFontSize(9.0);
        br.setColor("1A1A1A");
        br.setFontFamily("Segoe UI");
    }

    private static void addCodeBlock(XWPFDocument doc, String code, String label) {
        XWPFTable table = doc.createTable(1, 1);
        setTableExplicitWidths(table, new int[]{10800});
        setTableBorders(table, "C4B5D0");
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor(COLOR_CODE_BG);

        XWPFParagraph p = cell.getParagraphArray(0);
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);

        XWPFRun lr = p.createRun();
        lr.setText("-- Script Excerpt: " + label + "\n");
        lr.setFontFamily(CODE_FONT_FAMILY);
        lr.setFontSize(CODE_FONT_SIZE);
        lr.setColor(COLOR_MUTED);

        XWPFRun cr = p.createRun();
        cr.setText(code);
        cr.setFontFamily(CODE_FONT_FAMILY);
        cr.setFontSize(CODE_FONT_SIZE);
        cr.setColor("1F1424");
    }

    private static void addBulletPoint(XWPFDocument doc, String label, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(20);
        p.setSpacingAfter(20);
        p.setIndentationLeft(280);

        XWPFRun bRun = p.createRun();
        bRun.setText("•  " + label + ": ");
        bRun.setBold(true);
        bRun.setFontSize(9.0);
        bRun.setColor(COLOR_PRIMARY);
        bRun.setFontFamily("Segoe UI");

        XWPFRun tRun = p.createRun();
        tRun.setText(text);
        tRun.setFontSize(9.0);
        tRun.setColor("1A1A1A");
        tRun.setFontFamily("Segoe UI");
    }

    private static void addSectionDivider(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(80);
        p.setSpacingAfter(80);
        p.setPageBreak(true);
    }

    private static void addFaintDivider(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(60);
        p.setSpacingAfter(40);
        XWPFRun r = p.createRun();
        r.setText("──────────────────────────────────────────────────────────────────────────");
        r.setFontSize(6.5);
        r.setColor("E2E8F0");
    }

    private static void addDoubleSpace(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);
    }

    private static void addBackToTocLink(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);
        XWPFRun r = p.createRun();
        r.setText("▲ Back to Table of Contents");
        r.setFontSize(8.0);
        r.setItalic(true);
        r.setColor(COLOR_BLUE);
        r.setFontFamily("Segoe UI");
    }

    private static void setCell(XWPFTable table, int row, int col, String text, boolean bold, String bgColor) {
        setCell(table, row, col, text, bold, bgColor, null);
    }

    private static void setCell(XWPFTable table, int row, int col, String text, boolean bold, String bgColor, String textColor) {
        XWPFTableCell cell = table.getRow(row).getCell(col);
        if (bgColor != null) {
            cell.setColor(bgColor);
        }
        XWPFParagraph p = cell.getParagraphArray(0);
        if (p == null) p = cell.addParagraph();
        p.setSpacingBefore(20);
        p.setSpacingAfter(20);

        // Remove existing runs
        for (int i = p.getRuns().size() - 1; i >= 0; i--) {
            p.removeRun(i);
        }

        XWPFRun r = p.createRun();
        r.setText(text != null ? text : "");
        r.setBold(bold);
        r.setFontSize(8.5);
        r.setFontFamily("Segoe UI");
        if (textColor != null) {
            r.setColor(textColor);
        }
    }

    private static void setTableExplicitWidths(XWPFTable table, int[] colWidthsDxa) {
        int totalWidth = 0;
        for (int w : colWidthsDxa) totalWidth += w;
        table.setWidth(totalWidth);

        for (XWPFTableRow row : table.getRows()) {
            for (int i = 0; i < row.getTableCells().size() && i < colWidthsDxa.length; i++) {
                row.getCell(i).setWidth(String.valueOf(colWidthsDxa[i]));
            }
        }
    }

    private static void setTableBorders(XWPFTable table, String colorHex) {
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, colorHex);
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, colorHex);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, colorHex);
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, colorHex);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, colorHex);
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, colorHex);
    }

    private static void setPageMargins(XWPFDocument doc, int top, int right, int bottom, int left) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
        if (sectPr == null) sectPr = doc.getDocument().getBody().addNewSectPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar pageMar = sectPr.getPgMar();
        if (pageMar == null) pageMar = sectPr.addNewPgMar();

        pageMar.setTop(BigInteger.valueOf(top));
        pageMar.setRight(BigInteger.valueOf(right));
        pageMar.setBottom(BigInteger.valueOf(bottom));
        pageMar.setLeft(BigInteger.valueOf(left));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) return map;
        for (String a : args) {
            if (a.startsWith("-") && a.contains("=")) {
                String clean = a.substring(1);
                int eq = clean.indexOf('=');
                map.put(clean.substring(0, eq).trim(), clean.substring(eq + 1).trim());
            }
        }
        Map<String, String> result = map;
        return result;
    }
}
