package com.fllottery.util;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Enterprise Specification Template Document Generator for the Florida Lottery.
 * Generates standardized, executive-ready blank Word (.docx) specification templates
 * for developers to jump in and edit:
 *  1. PL/SQL Package Technical Specification Template (TEMPLATE_Package_Technical_Specification.docx)
 *  2. Standalone Stored Procedure Specification Template (TEMPLATE_Standalone_Procedure_Specification.docx)
 *  3. Standalone Stored Function Specification Template (TEMPLATE_Standalone_Function_Specification.docx)
 *  4. Database Table Technical Specification Template (TEMPLATE_Database_Table_Specification.docx)
 *
 * All architectural/flow diagrams are omitted (optional).
 * All section and subsection headers, formal cover page, TOC bookmarks, table shells,
 * parameter grids, QA matrices, and code containers are cleanly structured with blank
 * editable areas ready for immediate developer authoring.
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery Web/DataOps Team
 * @since 2026-09-30
 */
public class GenerateSpecificationTemplates {

    // Florida Lottery & Enterprise Web Team Hybrid Brand Palette
    private static final String COLOR_PRIMARY   = "5C1D48";      // Rich Plum / Burgundy (Web Team Header Bar)
    private static final String COLOR_NAVY      = "002B49";      // Florida Lottery Deep Navy
    private static final String COLOR_BLUE      = "005A9C";      // Subheadings & Links
    private static final String COLOR_GOLD      = "DAA520";      // Accent Gold
    private static final String COLOR_GREEN     = "166534";      // Forest Green / Success
    private static final String COLOR_HYPERLINK = "0066CC";      // Hyperlink Blue
    private static final String COLOR_LIGHT_BG  = "F8FAFC";      // Alternating Table Row BG
    private static final String COLOR_CODE_BG   = "F7F5F9";      // Code Block BG (Soft Plum-Alabaster)
    private static final String COLOR_BORDER    = "CCCCCC";      // Table Border Gray
    private static final String COLOR_MUTED     = "666666";      // Subtitle / Muted Text
    private static final String COLOR_CODE_BORDER = "C4B5D0";    // Code Border

    // Simple Developer Input Placeholder Palette
    public static final String PLACEHOLDER        = "[type here]";
    public static final String COLOR_PLACEHOLDER  = "666666";      // Muted italic placeholder text
    public static final String COLOR_FIELD_TEXT   = "666666";      // Backward compatibility
    public static final String COLOR_FIELD_BG     = "";            // Backward compatibility
    public static final String COLOR_FIELD_BORDER = "CCCCCC";      // Backward compatibility

    private static final int FULL_CONTENT_WIDTH_DXA = 10800;     // 7.5" content width (8.5" page - 2 * 0.5" margins)

    static {
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * CLI entry point for generating blank specification templates.
     * Can be executed standalone from the command line, Maven, or IDE run configuration:
     *   java com.fllottery.util.GenerateSpecificationTemplates [options]
     *
     * Options:
     *   -outputDir=<path>    Target directory where the TEMPLATES folder will be created.
     *                        (Default: C:\ocioldapps1\Cached\DbCache\Schemas\Word_Docs_Unified or ./TEMPLATES)
     *   -type=<template>     Filter template: all, package, procedure, function, table (Default: all)
     *   -baseUrl=<url>       Optional web documentation portal base URL
     *   -help, --help, /?    Display usage instructions
     */
    public static void main(String[] args) {
        String defaultDir = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\Word_Docs_Unified";
        // Fallback to local working directory if Windows OCI path does not exist
        if (!new File("C:\\ocioldapps1").exists() && !new File(defaultDir).exists()) {
            defaultDir = "." + File.separator + "TEMPLATES";
        }

        String outputDir = defaultDir;
        String baseUrl = "";
        String templateType = "all";

        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) continue;
            String trimmed = arg.trim();

            if (trimmed.equalsIgnoreCase("-help") || trimmed.equalsIgnoreCase("--help") || trimmed.equals("/?") || trimmed.equalsIgnoreCase("-h")) {
                printHelp();
                return;
            } else if (trimmed.startsWith("-outputDir=")) {
                outputDir = trimmed.substring("-outputDir=".length()).trim();
            } else if (trimmed.startsWith("--output=")) {
                outputDir = trimmed.substring("--output=".length()).trim();
            } else if (trimmed.startsWith("-o=")) {
                outputDir = trimmed.substring("-o=".length()).trim();
            } else if (trimmed.startsWith("-type=")) {
                templateType = trimmed.substring("-type=".length()).trim().toLowerCase();
            } else if (trimmed.startsWith("-baseUrl=")) {
                baseUrl = trimmed.substring("-baseUrl=".length()).trim();
            } else if (!trimmed.startsWith("-")) {
                // Positional argument: output directory
                outputDir = trimmed;
            }
        }

        System.out.println("================================================================================");
        System.out.println(" FLORIDA LOTTERY ENTERPRISE TECHNICAL SPECIFICATION TEMPLATE GENERATOR");
        System.out.println("================================================================================");
        System.out.println(" Scope: " + templateType.toUpperCase());
        System.out.println(" Target Output: " + outputDir);
        System.out.println("--------------------------------------------------------------------------------");

        File outFolder = new File(outputDir);
        if (!outFolder.exists()) {
            outFolder.mkdirs();
        }

        // Determine destination folder (if outputDir itself ends with TEMPLATES, use it; otherwise create TEMPLATES subfolder)
        File templateDir = outFolder.getName().equalsIgnoreCase("TEMPLATES") ? outFolder : new File(outFolder, "TEMPLATES");
        if (!templateDir.exists()) {
            templateDir.mkdirs();
        }

        long startTime = System.currentTimeMillis();
        int count = 0;

        try {
            boolean genPkg  = "all".equals(templateType) || "package".equals(templateType) || "pkg".equals(templateType);
            boolean genProc = "all".equals(templateType) || "procedure".equals(templateType) || "proc".equals(templateType);
            boolean genFunc = "all".equals(templateType) || "function".equals(templateType) || "func".equals(templateType);
            boolean genTbl  = "all".equals(templateType) || "table".equals(templateType) || "tbl".equals(templateType);

            if (genPkg) {
                File pkgFile = new File(templateDir, "TEMPLATE_Package_Technical_Specification.docx");
                System.out.print(" -> Generating PL/SQL Package Specification Template... ");
                generateBlankPackageTemplate(pkgFile, baseUrl);
                System.out.printf("OK (%d KB)%n", pkgFile.length() / 1024);
                count++;
            }

            if (genProc) {
                File procFile = new File(templateDir, "TEMPLATE_Standalone_Procedure_Specification.docx");
                System.out.print(" -> Generating Stored Procedure Specification Template... ");
                generateBlankProcedureTemplate(procFile, baseUrl);
                System.out.printf("OK (%d KB)%n", procFile.length() / 1024);
                count++;
            }

            if (genFunc) {
                File funcFile = new File(templateDir, "TEMPLATE_Standalone_Function_Specification.docx");
                System.out.print(" -> Generating Stored Function Specification Template... ");
                generateBlankFunctionTemplate(funcFile, baseUrl);
                System.out.printf("OK (%d KB)%n", funcFile.length() / 1024);
                count++;
            }

            if (genTbl) {
                File tblFile = new File(templateDir, "TEMPLATE_Database_Table_Specification.docx");
                System.out.print(" -> Generating Database Table Specification Template... ");
                generateBlankTableTemplate(tblFile, baseUrl);
                System.out.printf("OK (%d KB)%n", tblFile.length() / 1024);
                count++;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("--------------------------------------------------------------------------------");
            System.out.printf(" SUCCESS: %d specification template(s) generated in %d ms.%n", count, elapsed);
            System.out.println(" Destination: " + templateDir.getAbsolutePath());
            System.out.println("================================================================================");

        } catch (Exception e) {
            System.err.println("ERROR: Failed to generate templates: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("================================================================================");
        System.out.println(" FLORIDA LOTTERY ENTERPRISE SPECIFICATION TEMPLATE GENERATOR - CLI HELP");
        System.out.println("================================================================================");
        System.out.println(" Usage:");
        System.out.println("   java com.fllottery.util.GenerateSpecificationTemplates [options]");
        System.out.println();
        System.out.println(" Options:");
        System.out.println("   -outputDir=<path>    Target directory where TEMPLATES folder will be created.");
        System.out.println("                        (Default: C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\Word_Docs_Unified)");
        System.out.println("   -type=<template>     Filter template: all, package, procedure, function, table.");
        System.out.println("                        (Default: all)");
        System.out.println("   -baseUrl=<url>       Optional web documentation portal base URL.");
        System.out.println("   -help, --help, /?    Display this help screen.");
        System.out.println();
        System.out.println(" Examples:");
        System.out.println("   java com.fllottery.util.GenerateSpecificationTemplates");
        System.out.println("   java com.fllottery.util.GenerateSpecificationTemplates -type=procedure");
        System.out.println("   java com.fllottery.util.GenerateSpecificationTemplates -outputDir=C:\\Temp\\Specs");
        System.out.println("================================================================================");
    }

    /**
     * Master batch generator creating the 4 standardized blank technical specification templates
     * in the dedicated TEMPLATES directory under the master output folder.
     *
     * @param outputDir Root output directory (e.g. Word_Docs_Unified)
     * @param baseUrl Base URL for hyperlink routing
     */
    public static void generateAllBlankTemplates(File outputDir, String baseUrl) {
        try {
            File templateDir = outputDir.getName().equalsIgnoreCase("TEMPLATES") ? outputDir : new File(outputDir, "TEMPLATES");
            if (!templateDir.exists()) {
                templateDir.mkdirs();
            }

            // 1. Package Technical Specification Template
            File packageTmpl = new File(templateDir, "TEMPLATE_Package_Technical_Specification.docx");
            generateBlankPackageTemplate(packageTmpl, baseUrl);

            // 2. Standalone Procedure Specification Template
            File procTmpl = new File(templateDir, "TEMPLATE_Standalone_Procedure_Specification.docx");
            generateBlankProcedureTemplate(procTmpl, baseUrl);

            // 3. Standalone Function Specification Template
            File funcTmpl = new File(templateDir, "TEMPLATE_Standalone_Function_Specification.docx");
            generateBlankFunctionTemplate(funcTmpl, baseUrl);

            // 4. Database Table Specification Template
            File tableTmpl = new File(templateDir, "TEMPLATE_Database_Table_Specification.docx");
            generateBlankTableTemplate(tableTmpl, baseUrl);

            System.out.printf("   [Specification Templates Created] -> %s (4 Blank Standardized Templates: Package, Procedure, Function, Table)%n",
                    templateDir.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Warning creating blank standardized template documents: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 1. BLANK PACKAGE SPECIFICATION TEMPLATE
    // =========================================================================
    public static void generateBlankPackageTemplate(File targetFile, String baseUrl) throws IOException {
        String schema = "[SCHEMA_NAME]";
        String pkgName = "[PACKAGE_NAME]";
        String fullQualifiedName = schema + "." + pkgName;
        String docId = "PKG-" + schema + "-" + pkgName;

        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc, 1440, 720, 1440, 720); // 1.0" top/bottom, 0.5" left/right
            setupDocumentHeaderFooter(doc, fullQualifiedName, docId, schema);

            int bookmarkId = 100;

            // PAGE 1: FORMAL EXECUTIVE COVER PAGE & GOVERNANCE SIGN-OFF
            renderCoverPage(doc, "PL/SQL Package Technical Specification", fullQualifiedName, docId, schema);

            // PAGE 2: TABLE OF CONTENTS (TOC)
            addHeading(doc, "Table of Contents", 16, true, "TOC_TOP", bookmarkId++);
            renderAutomaticTable2Entry(doc, "SEC_1", "1.0  Package Overview & Architecture", 2, 1);
            renderAutomaticTable2Entry(doc, "SEC_1_1", "1.1  Package Purpose & Operational Scope", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_2", "1.2  Upstream Callers & Ingress Channels", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_3", "1.3  Target Schemas & Database Dependencies", 2, 2);

            renderAutomaticTable2Entry(doc, "SEC_2", "2.0  Package Specification & Public Interface", 3, 1);
            renderAutomaticTable2Entry(doc, "SEC_2_1", "2.1  Global Types, Constants & Exceptions", 3, 2);
            renderAutomaticTable2Entry(doc, "SEC_2_2", "2.2  Member Subroutines Directory", 3, 2);

            renderAutomaticTable2Entry(doc, "SEC_3", "3.0  Member Subroutine Detailed Specifications", 4, 1);
            renderAutomaticTable2Entry(doc, "SEC_3_1", "3.1  Procedure: [MEMBER_PROCEDURE_NAME]", 4, 2);
            renderAutomaticTable2Entry(doc, "SEC_3_2", "3.2  Function: [MEMBER_FUNCTION_NAME]", 5, 2);

            renderAutomaticTable2Entry(doc, "SEC_4", "4.0  Operational Guidelines & Execution Standards", 6, 1);
            renderAutomaticTable2Entry(doc, "SEC_4_1", "4.1  Business Rules & Statutory Compliance", 6, 2);
            renderAutomaticTable2Entry(doc, "SEC_4_2", "4.2  Error Trapping, Logging & Rollback Strategy", 6, 2);
            renderAutomaticTable2Entry(doc, "SEC_4_3", "4.3  IWS Batch Scheduling & Runbook", 6, 2);

            renderAutomaticTable2Entry(doc, "SEC_5", "5.0  Quality Assurance & Testing Matrix", 7, 1);

            renderAutomaticTable2Entry(doc, "SEC_6", "6.0  PL/SQL Package Source Code Implementation", 8, 1);
            renderAutomaticTable2Entry(doc, "SEC_6_1", "6.1  Package Specification Script (DDL)", 8, 2);
            renderAutomaticTable2Entry(doc, "SEC_6_2", "6.2  Package Body Script (DDL)", 8, 2);

            addSectionDivider(doc);

            // PAGE 3: SECTION 1.0 - PACKAGE OVERVIEW & ARCHITECTURE
            addHeading(doc, "1.0 Package Overview & Architecture", 14, true, "SEC_1", bookmarkId++);

            addHeading(doc, "1.1 Package Purpose & Operational Scope", 12, false, "SEC_1_1", bookmarkId++);
            addCalloutBox(doc, "PACKAGE PURPOSE:", "[type here]");
            addBlankTextLines(doc, 3);
            addFaintDivider(doc);

            addHeading(doc, "1.2 Upstream Callers & Ingress Channels", 12, false, "SEC_1_2", bookmarkId++);
            addBulletPoint(doc, "Batch Schedulers", "[type here]");
            addBulletPoint(doc, "User Applications", "[type here]");
            addBulletPoint(doc, "Database Triggers", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "1.3 Target Schemas & Database Dependencies", 12, false, "SEC_1_3", bookmarkId++);
            addBulletPoint(doc, "Owning Subsystem", "[SCHEMA_NAME] database schema");
            addBulletPoint(doc, "Referenced Tables", "[type here]");
            addBulletPoint(doc, "Helper Packages", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 2.0 - PACKAGE SPECIFICATION & PUBLIC INTERFACE
            addHeading(doc, "2.0 Package Specification & Public Interface", 14, false, "SEC_2", bookmarkId++);

            addHeading(doc, "2.1 Global Types, Constants & Exceptions", 12, false, "SEC_2_1", bookmarkId++);
            renderPackageTypesTable(doc);
            addFaintDivider(doc);

            addHeading(doc, "2.2 Member Subroutines Directory", 12, false, "SEC_2_2", bookmarkId++);
            renderSubroutinesDirectoryTable(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 3.0 - MEMBER SUBROUTINE DETAILED SPECIFICATIONS
            addHeading(doc, "3.0 Member Subroutine Detailed Specifications", 14, true, "SEC_3", bookmarkId++);

            // 3.1 Procedure Specification
            addHeading(doc, "3.1 Procedure: [MEMBER_PROCEDURE_NAME]", 13, false, "SEC_3_1", bookmarkId++);
            addCalloutBox(doc, "PROCEDURE OVERVIEW:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "PL/SQL Ingress Signature Interface", 11);
            addCodeBlock(doc, "PROCEDURE [member_procedure_name] (\n" +
                    "    p_run_date          IN  DATE,\n" +
                    "    p_batch_id          IN  NUMBER,\n" +
                    "    p_status_code       OUT VARCHAR2,\n" +
                    "    p_error_msg         OUT VARCHAR2\n" +
                    ");", "[member_procedure_name]_Signature.sql");

            addHeading(doc, "Parameters & Return Interface", 11);
            renderParametersTableShell(doc, true);

            addHeading(doc, "Database CRUD & Dependencies", 11);
            addBulletPoint(doc, "Reads (SELECT)", "[type here]");
            addBulletPoint(doc, "Writes (INSERT/UPDATE/DELETE)", "[type here]");
            addBulletPoint(doc, "Subroutines Invoked", "[type here]");

            addHeading(doc, "Business Validations & Rules", 11);
            addNumberedStep(doc, 1, "Input Pre-Flight Validation", "[type here]");
            addNumberedStep(doc, 2, "Domain Business Logic", "[type here]");
            addNumberedStep(doc, 3, "Commit & Audit Logging", "[type here]");
            addFaintDivider(doc);

            // 3.2 Function Specification
            addHeading(doc, "3.2 Function: [MEMBER_FUNCTION_NAME]", 13, false, "SEC_3_2", bookmarkId++);
            addCalloutBox(doc, "FUNCTION OVERVIEW:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "PL/SQL Function Signature Interface", 11);
            addCodeBlock(doc, "FUNCTION [member_function_name] (\n" +
                    "    p_retailer_id       IN NUMBER,\n" +
                    "    p_effective_date    IN DATE DEFAULT SYSDATE\n" +
                    ") RETURN VARCHAR2 DETERMINISTIC;", "[member_function_name]_Signature.sql");

            addHeading(doc, "Parameters Interface", 11);
            renderParametersTableShell(doc, false);

            addHeading(doc, "Return Value Specification", 11);
            addBulletPoint(doc, "Return Data Type", "[VARCHAR2 / NUMBER / DATE / BOOLEAN]");
            addBulletPoint(doc, "Return Values Domain", "[type here]");
            addBulletPoint(doc, "Null / Error Fallback", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 4.0 - OPERATIONAL GUIDELINES & EXECUTION STANDARDS
            addHeading(doc, "4.0 Operational Guidelines & Execution Standards", 14, false, "SEC_4", bookmarkId++);

            addHeading(doc, "4.1 Business Rules & Statutory Compliance", 12, false, "SEC_4_1", bookmarkId++);
            addNumberedStep(doc, 1, "Statutory Rule Compliance", "[type here]");
            addNumberedStep(doc, 2, "Idempotency & Re-Run Safety", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "4.2 Error Trapping, Logging & Rollback Strategy", 12, false, "SEC_4_2", bookmarkId++);
            addBulletPoint(doc, "Exception Blocks", "All transactional operations enclosed in BEGIN...EXCEPTION WHEN OTHERS THEN blocks.");
            addBulletPoint(doc, "Rollback Protocol", "Execute ROLLBACK upon unhandled exception to prevent partial batch corruption.");
            addBulletPoint(doc, "Telemetry Audit", "Log error message, SQLCODE, SQLERRM, and execution context to COMMON.AUDIT_LOG.");
            addFaintDivider(doc);

            addHeading(doc, "4.3 IWS Batch Scheduling & Runbook", 12, false, "SEC_4_3", bookmarkId++);
            addBulletPoint(doc, "IWS Job Stream", "[type here]");
            addBulletPoint(doc, "Execution Schedule", "[type here]");
            addBulletPoint(doc, "Recovery Runbook", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 5.0 - QUALITY ASSURANCE & TESTING MATRIX
            addHeading(doc, "5.0 Quality Assurance & Testing Matrix", 14, false, "SEC_5", bookmarkId++);
            renderTestingMatrixTableShell(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 6.0 - PL/SQL PACKAGE SOURCE CODE IMPLEMENTATION
            addHeading(doc, "6.0 PL/SQL Package Source Code Implementation", 14, true, "SEC_6", bookmarkId++);

            addHeading(doc, "6.1 Package Specification Script (DDL)", 12, false, "SEC_6_1", bookmarkId++);
            addCodeBlock(doc, "-- ============================================================================\n" +
                    "-- Florida Lottery PL/SQL Package Specification\n" +
                    "-- Schema: [SCHEMA_NAME] | Package: [PACKAGE_NAME]\n" +
                    "-- ============================================================================\n" +
                    "CREATE OR REPLACE PACKAGE [SCHEMA_NAME].[PACKAGE_NAME] IS\n" +
                    "\n" +
                    "    -- Global Types & Constants\n" +
                    "    -- [Declare package types, records, and constants here]\n" +
                    "\n" +
                    "    -- Member Subroutine Specifications\n" +
                    "    PROCEDURE [member_procedure_name] (\n" +
                    "        p_run_date          IN  DATE,\n" +
                    "        p_batch_id          IN  NUMBER,\n" +
                    "        p_status_code       OUT VARCHAR2,\n" +
                    "        p_error_msg         OUT VARCHAR2\n" +
                    "    );\n" +
                    "\n" +
                    "    FUNCTION [member_function_name] (\n" +
                    "        p_retailer_id       IN NUMBER,\n" +
                    "        p_effective_date    IN DATE DEFAULT SYSDATE\n" +
                    "    ) RETURN VARCHAR2 DETERMINISTIC;\n" +
                    "\n" +
                    "END [PACKAGE_NAME];\n/", "[package_name]_Spec.sql");

            addHeading(doc, "6.2 Package Body Script (DDL)", 12, true, "SEC_6_2", bookmarkId++);
            addCodeBlock(doc, "-- ============================================================================\n" +
                    "-- Florida Lottery PL/SQL Package Body\n" +
                    "-- Schema: [SCHEMA_NAME] | Package: [PACKAGE_NAME]\n" +
                    "-- ============================================================================\n" +
                    "CREATE OR REPLACE PACKAGE BODY [SCHEMA_NAME].[PACKAGE_NAME] IS\n" +
                    "\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    -- Subroutine: [MEMBER_PROCEDURE_NAME]\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    PROCEDURE [member_procedure_name] (\n" +
                    "        p_run_date          IN  DATE,\n" +
                    "        p_batch_id          IN  NUMBER,\n" +
                    "        p_status_code       OUT VARCHAR2,\n" +
                    "        p_error_msg         OUT VARCHAR2\n" +
                    "    ) AS\n" +
                    "    BEGIN\n" +
                    "        -- [Step 1: Input Validation]\n" +
                    "        IF p_run_date IS NULL THEN\n" +
                    "            p_status_code := 'ERROR';\n" +
                    "            p_error_msg := 'Run date is required.';\n" +
                    "            RETURN;\n" +
                    "        END IF;\n" +
                    "\n" +
                    "        -- [Step 2: Core Processing Logic]\n" +
                    "        p_status_code := 'SUCCESS';\n" +
                    "        p_error_msg := NULL;\n" +
                    "        COMMIT;\n" +
                    "    EXCEPTION\n" +
                    "        WHEN OTHERS THEN\n" +
                    "            ROLLBACK;\n" +
                    "            p_status_code := 'ERROR';\n" +
                    "            p_error_msg := 'ORA-' || SQLCODE || ': ' || SQLERRM;\n" +
                    "            RAISE;\n" +
                    "    END [member_procedure_name];\n" +
                    "\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    -- Subroutine: [MEMBER_FUNCTION_NAME]\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    FUNCTION [member_function_name] (\n" +
                    "        p_retailer_id       IN NUMBER,\n" +
                    "        p_effective_date    IN DATE DEFAULT SYSDATE\n" +
                    "    ) RETURN VARCHAR2 DETERMINISTIC AS\n" +
                    "    BEGIN\n" +
                    "        -- [Validation & Lookup Algorithm]\n" +
                    "        RETURN 'Y';\n" +
                    "    EXCEPTION\n" +
                    "        WHEN OTHERS THEN\n" +
                    "            RETURN 'N';\n" +
                    "    END [member_function_name];\n" +
                    "\n" +
                    "END [PACKAGE_NAME];\n/", "[package_name]_Body.sql");

            addBackToTocLink(doc);

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                doc.write(fos);
            }
        }
    }

    // =========================================================================
    // 2. BLANK STANDALONE PROCEDURE SPECIFICATION TEMPLATE
    // =========================================================================
    public static void generateBlankProcedureTemplate(File targetFile, String baseUrl) throws IOException {
        String schema = "[SCHEMA_NAME]";
        String procName = "PRC_[PROCEDURE_NAME]";
        String fullQualifiedName = schema + "." + procName;
        String docId = "PRC-" + schema + "-[PROCEDURE_NAME]";

        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc, 1440, 720, 1440, 720);
            setupDocumentHeaderFooter(doc, fullQualifiedName, docId, schema);

            int bookmarkId = 200;

            // PAGE 1: COVER PAGE
            renderCoverPage(doc, "Stored Procedure Technical Specification", fullQualifiedName, docId, schema);

            // PAGE 2: TABLE OF CONTENTS (TOC)
            addHeading(doc, "Table of Contents", 16, true, "TOC_TOP", bookmarkId++);
            renderAutomaticTable2Entry(doc, "SEC_1", "1.0  Procedure Overview & Ingress Signature", 2, 1);
            renderAutomaticTable2Entry(doc, "SEC_1_1", "1.1  Executive Overview", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_2", "1.2  What It Does", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_3", "1.3  Why It Exists (Business Justification)", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_4", "1.4  How It Works (Technical Mechanics)", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_5", "1.5  PL/SQL Ingress Signature Interface", 2, 2);

            renderAutomaticTable2Entry(doc, "SEC_2", "2.0  Database Schema & Dependencies", 3, 1);
            renderAutomaticTable2Entry(doc, "SEC_2_1", "2.1  Owning Schema & Execution Privileges", 3, 2);
            renderAutomaticTable2Entry(doc, "SEC_2_2", "2.2  Ingress Callers (Upstream Invocation)", 3, 2);
            renderAutomaticTable2Entry(doc, "SEC_2_3", "2.3  Tables Accessed & CRUD Matrix", 3, 2);
            renderAutomaticTable2Entry(doc, "SEC_2_4", "2.4  Downstream Routines & Helpers Called", 3, 2);

            renderAutomaticTable2Entry(doc, "SEC_3", "3.0  Parameters & Processing Logic", 4, 1);
            renderAutomaticTable2Entry(doc, "SEC_3_1", "3.1  Input & Output Parameters Table", 4, 2);
            renderAutomaticTable2Entry(doc, "SEC_3_2", "3.2  Core Processing Logic & Transformations", 4, 2);

            renderAutomaticTable2Entry(doc, "SEC_4", "4.0  Operational Guidelines, Error Handling & QA Matrix", 5, 1);
            renderAutomaticTable2Entry(doc, "SEC_4_1", "4.1  Business Rules & Statutory Validations", 5, 2);
            renderAutomaticTable2Entry(doc, "SEC_4_2", "4.2  Error Handling & Rollback Strategy", 5, 2);
            renderAutomaticTable2Entry(doc, "SEC_4_3", "4.3  Operational Runbook & Scheduling (IWS)", 5, 2);
            renderAutomaticTable2Entry(doc, "SEC_4_4", "4.4  QA Testing Scenarios Matrix", 5, 2);

            renderAutomaticTable2Entry(doc, "SEC_5", "5.0  PL/SQL Source Code Implementation", 6, 1);
            renderAutomaticTable2Entry(doc, "SEC_5_1", "5.1  Production PL/SQL Source Code Excerpt", 6, 2);

            addSectionDivider(doc);

            // PAGE 3: SECTION 1.0 - PROCEDURE OVERVIEW & INGRESS SIGNATURE
            addHeading(doc, "Routine: PRC_[PROCEDURE_NAME]", 16, true, "SEC_1", bookmarkId++);

            addHeading(doc, "1.1 Executive Overview", 12, false, "SEC_1_1", bookmarkId++);
            addCalloutBox(doc, "PROCEDURE OVERVIEW:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.2 What It Does", 12, false, "SEC_1_2", bookmarkId++);
            addCalloutBox(doc, "WHAT IT DOES:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.3 Why It Exists (Business Justification)", 12, false, "SEC_1_3", bookmarkId++);
            addCalloutBox(doc, "WHY IT EXISTS:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.4 How It Works (Technical Mechanics)", 12, false, "SEC_1_4", bookmarkId++);
            addCalloutBox(doc, "HOW IT WORKS:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.5 PL/SQL Ingress Signature Interface", 12, false, "SEC_1_5", bookmarkId++);
            addCodeBlock(doc, "CREATE OR REPLACE PROCEDURE [SCHEMA_NAME].PRC_[PROCEDURE_NAME] (\n" +
                    "    p_run_date          IN  DATE,\n" +
                    "    p_retailer_id       IN  NUMBER,\n" +
                    "    p_batch_mode        IN  VARCHAR2,\n" +
                    "    p_status_code       OUT VARCHAR2,\n" +
                    "    p_records_processed OUT NUMBER,\n" +
                    "    p_error_message     OUT VARCHAR2\n" +
                    ");", "PRC_[PROCEDURE_NAME]_Signature.sql");

            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 2.0 - DATABASE SCHEMA & DEPENDENCIES
            addHeading(doc, "2.0 Database Schema & Dependencies", 14, false, "SEC_2", bookmarkId++);

            addHeading(doc, "2.1 Owning Schema & Execution Privileges", 12, false, "SEC_2_1", bookmarkId++);
            addBulletPoint(doc, "Owning Schema", schema + " (production schema)");
            addBulletPoint(doc, "Security Model", "AUTHID DEFINER (Executes under owning schema database privileges)");
            addFaintDivider(doc);

            addHeading(doc, "2.2 Ingress Callers (Upstream Invocation)", 12, false, "SEC_2_2", bookmarkId++);
            addBulletPoint(doc, "Batch Schedulers", "[type here]");
            addBulletPoint(doc, "Operational Interfaces", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "2.3 Tables Accessed & CRUD Matrix", 12, false, "SEC_2_3", bookmarkId++);
            addBulletPoint(doc, "Source Tables (SELECT)", "[type here]");
            addBulletPoint(doc, "Target Tables (DML)", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "2.4 Downstream Routines & Helpers Called", 12, false, "SEC_2_4", bookmarkId++);
            addBulletPoint(doc, "Audit Logger", "COMMON.AUDIT_LOGGER.LOG_EVENT (Records start, milestones, and errors)");
            addBulletPoint(doc, "Helper Utilities", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 3.0 - PARAMETERS & PROCESSING LOGIC
            addHeading(doc, "3.0 Parameters & Processing Logic", 14, false, "SEC_3", bookmarkId++);

            addHeading(doc, "3.1 Input & Output Parameters Table", 12, false, "SEC_3_1", bookmarkId++);
            renderParametersTableShell(doc, true);
            addFaintDivider(doc);

            addHeading(doc, "3.2 Core Processing Logic & Transformations", 12, false, "SEC_3_2", bookmarkId++);
            addNumberedStep(doc, 1, "Input Intake & Validation", "[type here]");
            addNumberedStep(doc, 2, "Staging Query & Record Extraction", "[type here]");
            addNumberedStep(doc, 3, "Statutory Formula Application", "[type here]");
            addNumberedStep(doc, 4, "Destination Table Storage", "[type here]");
            addNumberedStep(doc, 5, "Commit & Telemetry Pulse", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 4.0 - OPERATIONAL GUIDELINES, ERROR HANDLING & QA MATRIX
            addHeading(doc, "4.0 Operational Guidelines, Error Handling & QA Matrix", 14, false, "SEC_4", bookmarkId++);

            addHeading(doc, "4.1 Business Rules & Statutory Validations", 12, false, "SEC_4_1", bookmarkId++);
            addBulletPoint(doc, "Rule 1: Input Bounds", "[type here]");
            addBulletPoint(doc, "Rule 2: Statutory Compliance", "[type here]");
            addBulletPoint(doc, "Rule 3: Idempotency", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "4.2 Error Handling & Rollback Strategy", 12, false, "SEC_4_2", bookmarkId++);
            addBulletPoint(doc, "Exception Blocks", "All transactional logic wrapped in BEGIN...EXCEPTION WHEN OTHERS THEN.");
            addBulletPoint(doc, "Rollback Execution", "Executes ROLLBACK upon error to guarantee ACID consistency.");
            addBulletPoint(doc, "Telemetry Logging", "Logs SQLCODE, SQLERRM, and diagnostic context to COMMON.AUDIT_LOG before raising or returning error.");
            addFaintDivider(doc);

            addHeading(doc, "4.3 Operational Runbook & Scheduling (IWS)", 12, false, "SEC_4_3", bookmarkId++);
            addBulletPoint(doc, "Scheduler Stream", "[type here]");
            addBulletPoint(doc, "Alert Routing", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "4.4 QA Testing Scenarios Matrix", 12, false, "SEC_4_4", bookmarkId++);
            renderTestingMatrixTableShell(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 5.0 - PL/SQL SOURCE CODE IMPLEMENTATION
            addHeading(doc, "5.0 PL/SQL Source Code Implementation", 14, true, "SEC_5", bookmarkId++);
            addHeading(doc, "5.1 Production PL/SQL Source Code Excerpt", 12, false, "SEC_5_1", bookmarkId++);

            addCodeBlock(doc, "-- ============================================================================\n" +
                    "-- Florida Lottery Production PL/SQL Standalone Procedure\n" +
                    "-- Schema: [SCHEMA_NAME] | Routine: PRC_[PROCEDURE_NAME]\n" +
                    "-- Author: [Author Name / Programmer Analyst] | Date: " + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "\n" +
                    "-- ============================================================================\n" +
                    "CREATE OR REPLACE PROCEDURE [SCHEMA_NAME].PRC_[PROCEDURE_NAME] (\n" +
                    "    p_run_date          IN  DATE,\n" +
                    "    p_retailer_id       IN  NUMBER,\n" +
                    "    p_batch_mode        IN  VARCHAR2,\n" +
                    "    p_status_code       OUT VARCHAR2,\n" +
                    "    p_records_processed OUT NUMBER,\n" +
                    "    p_error_message     OUT VARCHAR2\n" +
                    ") AS\n" +
                    "    v_start_time TIMESTAMP := SYSTIMESTAMP;\n" +
                    "    v_batch_id   NUMBER;\n" +
                    "BEGIN\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    -- Step 1: Input Validation & Pre-Flight Checks\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    IF p_run_date IS NULL THEN\n" +
                    "        p_status_code := 'ERROR';\n" +
                    "        p_error_message := 'Accounting Run Date is mandatory.';\n" +
                    "        RETURN;\n" +
                    "    END IF;\n" +
                    "\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    -- Step 2: Core Processing & Business Logic Execution\n" +
                    "    -- [Insert cursor loops, calculations, and transactional operations here]\n" +
                    "    -- ------------------------------------------------------------------------\n" +
                    "    p_records_processed := 0;\n" +
                    "    p_status_code := 'SUCCESS';\n" +
                    "    p_error_message := NULL;\n" +
                    "\n" +
                    "    COMMIT;\n" +
                    "EXCEPTION\n" +
                    "    WHEN OTHERS THEN\n" +
                    "        ROLLBACK;\n" +
                    "        p_status_code := 'ERROR';\n" +
                    "        p_error_message := 'ORA-' || SQLCODE || ': ' || SQLERRM;\n" +
                    "        RAISE;\n" +
                    "END PRC_[PROCEDURE_NAME];\n/", "PRC_[PROCEDURE_NAME].sql");

            addBackToTocLink(doc);

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                doc.write(fos);
            }
        }
    }

    // =========================================================================
    // 3. BLANK STANDALONE FUNCTION SPECIFICATION TEMPLATE
    // =========================================================================
    public static void generateBlankFunctionTemplate(File targetFile, String baseUrl) throws IOException {
        String schema = "[SCHEMA_NAME]";
        String funcName = "FN_[FUNCTION_NAME]";
        String fullQualifiedName = schema + "." + funcName;
        String docId = "FNC-" + schema + "-[FUNCTION_NAME]";

        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc, 1440, 720, 1440, 720);
            setupDocumentHeaderFooter(doc, fullQualifiedName, docId, schema);

            int bookmarkId = 300;

            // PAGE 1: COVER PAGE
            renderCoverPage(doc, "Stored Function Technical Specification", fullQualifiedName, docId, schema);

            // PAGE 2: TABLE OF CONTENTS (TOC)
            addHeading(doc, "Table of Contents", 16, true, "TOC_TOP", bookmarkId++);
            renderAutomaticTable2Entry(doc, "SEC_1", "1.0  Function Overview & Signature", 2, 1);
            renderAutomaticTable2Entry(doc, "SEC_1_1", "1.1  Executive Overview", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_2", "1.2  What It Does", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_3", "1.3  Why It Exists (Business Justification)", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_4", "1.4  How It Works (Purity & Determinism)", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_5", "1.5  PL/SQL Signature Interface", 2, 2);

            renderAutomaticTable2Entry(doc, "SEC_2", "2.0  Database Schema & Dependencies", 3, 1);
            renderAutomaticTable2Entry(doc, "SEC_2_1", "2.1  Owning Schema & Invocation Context", 3, 2);
            renderAutomaticTable2Entry(doc, "SEC_2_2", "2.2  Ingress Callers (Queries, Views, Batch Jobs)", 3, 2);
            renderAutomaticTable2Entry(doc, "SEC_2_3", "2.3  Tables & Views Queried", 3, 2);

            renderAutomaticTable2Entry(doc, "SEC_3", "3.0  Parameters, Return Value & Algorithm", 4, 1);
            renderAutomaticTable2Entry(doc, "SEC_3_1", "3.1  Input Parameters Table", 4, 2);
            renderAutomaticTable2Entry(doc, "SEC_3_2", "3.2  Return Value Specification", 4, 2);
            renderAutomaticTable2Entry(doc, "SEC_3_3", "3.3  Calculation Logic & Transformation Formula", 4, 2);

            renderAutomaticTable2Entry(doc, "SEC_4", "4.0  Operational Guidelines & QA Matrix", 5, 1);
            renderAutomaticTable2Entry(doc, "SEC_4_1", "4.1  Business Rules & Boundary Conditions", 5, 2);
            renderAutomaticTable2Entry(doc, "SEC_4_2", "4.2  Error Trapping & Safe Fallback Strategy", 5, 2);
            renderAutomaticTable2Entry(doc, "SEC_4_3", "4.3  QA Testing Scenarios Matrix", 5, 2);

            renderAutomaticTable2Entry(doc, "SEC_5", "5.0  PL/SQL Stored Function Source Code", 6, 1);
            renderAutomaticTable2Entry(doc, "SEC_5_1", "5.1  Production PL/SQL Function Code Excerpt", 6, 2);

            addSectionDivider(doc);

            // PAGE 3: SECTION 1.0 - FUNCTION OVERVIEW & SIGNATURE
            addHeading(doc, "Routine: FN_[FUNCTION_NAME]", 16, true, "SEC_1", bookmarkId++);

            addHeading(doc, "1.1 Executive Overview", 12, false, "SEC_1_1", bookmarkId++);
            addCalloutBox(doc, "FUNCTION OVERVIEW:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.2 What It Does", 12, false, "SEC_1_2", bookmarkId++);
            addCalloutBox(doc, "WHAT IT DOES:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.3 Why It Exists (Business Justification)", 12, false, "SEC_1_3", bookmarkId++);
            addCalloutBox(doc, "WHY IT EXISTS:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.4 How It Works (Purity & Determinism)", 12, false, "SEC_1_4", bookmarkId++);
            addCalloutBox(doc, "HOW IT WORKS:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.5 PL/SQL Signature Interface", 12, false, "SEC_1_5", bookmarkId++);
            addCodeBlock(doc, "CREATE OR REPLACE FUNCTION [SCHEMA_NAME].FN_[FUNCTION_NAME] (\n" +
                    "    p_retailer_id    IN NUMBER,\n" +
                    "    p_sales_volume   IN NUMBER,\n" +
                    "    p_effective_date IN DATE DEFAULT SYSDATE\n" +
                    ") RETURN NUMBER DETERMINISTIC;", "FN_[FUNCTION_NAME]_Signature.sql");

            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 2.0 - DATABASE SCHEMA & DEPENDENCIES
            addHeading(doc, "2.0 Database Schema & Dependencies", 14, false, "SEC_2", bookmarkId++);

            addHeading(doc, "2.1 Owning Schema & Invocation Context", 12, false, "SEC_2_1", bookmarkId++);
            addBulletPoint(doc, "Owning Schema", schema + " (production schema)");
            addBulletPoint(doc, "Invocation Purity", "WNDS (Writes No Database State) - safe for direct use in SELECT lists, WHERE clauses, and views");
            addFaintDivider(doc);

            addHeading(doc, "2.2 Ingress Callers (Queries, Views, Batch Jobs)", 12, false, "SEC_2_2", bookmarkId++);
            addBulletPoint(doc, "Views & Queries", "[type here]");
            addBulletPoint(doc, "Stored Procedures", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "2.3 Tables & Views Queried", 12, false, "SEC_2_3", bookmarkId++);
            addBulletPoint(doc, "Lookup Entities", "[type here]");
            addBulletPoint(doc, "DML Writes", "NONE (Pure deterministic query routine)");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 3.0 - PARAMETERS, RETURN VALUE & ALGORITHM
            addHeading(doc, "3.0 Parameters, Return Value & Algorithm", 14, false, "SEC_3", bookmarkId++);

            addHeading(doc, "3.1 Input Parameters Table", 12, false, "SEC_3_1", bookmarkId++);
            renderParametersTableShell(doc, false);
            addFaintDivider(doc);

            addHeading(doc, "3.2 Return Value Specification", 12, false, "SEC_3_2", bookmarkId++);
            addBulletPoint(doc, "Return Data Type", "[NUMBER / VARCHAR2 / DATE / BOOLEAN]");
            addBulletPoint(doc, "Value Meaning", "[type here]");
            addBulletPoint(doc, "Default / Null Guard", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "3.3 Calculation Logic & Transformation Formula", 12, false, "SEC_3_3", bookmarkId++);
            addNumberedStep(doc, 1, "Input Pre-Flight Guard", "[type here]");
            addNumberedStep(doc, 2, "Tier Bracket Determination", "[type here]");
            addNumberedStep(doc, 3, "Precision Computation & Return", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 4.0 - OPERATIONAL GUIDELINES & QA MATRIX
            addHeading(doc, "4.0 Operational Guidelines & QA Matrix", 14, false, "SEC_4", bookmarkId++);

            addHeading(doc, "4.1 Business Rules & Boundary Conditions", 12, false, "SEC_4_1", bookmarkId++);
            addBulletPoint(doc, "Rule 1: Boundary Edge", "[type here]");
            addBulletPoint(doc, "Rule 2: Null Safety", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "4.2 Error Trapping & Safe Fallback Strategy", 12, false, "SEC_4_2", bookmarkId++);
            addBulletPoint(doc, "Exception Guard", "Wrap algorithm in BEGIN...EXCEPTION WHEN OTHERS THEN.");
            addBulletPoint(doc, "Fallback Return", "Return safe default (0.00 or 'N') to prevent aborting large parent SELECT queries.");
            addFaintDivider(doc);

            addHeading(doc, "4.3 QA Testing Scenarios Matrix", 12, false, "SEC_4_3", bookmarkId++);
            renderTestingMatrixTableShell(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 5.0 - PL/SQL STORED FUNCTION SOURCE CODE
            addHeading(doc, "5.0 PL/SQL Stored Function Source Code", 14, true, "SEC_5", bookmarkId++);
            addHeading(doc, "5.1 Production PL/SQL Function Code Excerpt", 12, false, "SEC_5_1", bookmarkId++);

            addCodeBlock(doc, "-- ============================================================================\n" +
                    "-- Florida Lottery Production PL/SQL Standalone Function\n" +
                    "-- Schema: [SCHEMA_NAME] | Routine: FN_[FUNCTION_NAME]\n" +
                    "-- Author: [Author Name / Programmer Analyst] | Date: " + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "\n" +
                    "-- ============================================================================\n" +
                    "CREATE OR REPLACE FUNCTION [SCHEMA_NAME].FN_[FUNCTION_NAME] (\n" +
                    "    p_retailer_id    IN NUMBER,\n" +
                    "    p_sales_volume   IN NUMBER,\n" +
                    "    p_effective_date IN DATE DEFAULT SYSDATE\n" +
                    ") RETURN NUMBER DETERMINISTIC AS\n" +
                    "    v_rate   NUMBER(5,4);\n" +
                    "    v_payout NUMBER(12,2);\n" +
                    "BEGIN\n" +
                    "    -- [Step 1: Input Validation & Null Guard]\n" +
                    "    IF p_sales_volume IS NULL OR p_sales_volume <= 0 THEN\n" +
                    "        RETURN 0.00;\n" +
                    "    END IF;\n" +
                    "\n" +
                    "    -- [Step 2: Tier Calculation Logic]\n" +
                    "    IF p_sales_volume < 5000.00 THEN\n" +
                    "        v_rate := 0.0500;\n" +
                    "    ELSE\n" +
                    "        v_rate := 0.0575;\n" +
                    "    END IF;\n" +
                    "\n" +
                    "    v_payout := ROUND(p_sales_volume * v_rate, 2);\n" +
                    "    RETURN v_payout;\n" +
                    "EXCEPTION\n" +
                    "    WHEN OTHERS THEN\n" +
                    "        -- Safe fallback return\n" +
                    "        RETURN 0.00;\n" +
                    "END FN_[FUNCTION_NAME];\n/", "FN_[FUNCTION_NAME].sql");

            addBackToTocLink(doc);

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                doc.write(fos);
            }
        }
    }

    // =========================================================================
    // 4. BLANK DATABASE TABLE SPECIFICATION TEMPLATE
    // =========================================================================
    public static void generateBlankTableTemplate(File targetFile, String baseUrl) throws IOException {
        String schema = "[SCHEMA_NAME]";
        String tableName = "[TABLE_NAME]";
        String fullQualifiedName = schema + "." + tableName;
        String docId = "TBL-" + schema + "-" + tableName;

        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc, 1440, 720, 1440, 720);
            setupDocumentHeaderFooter(doc, fullQualifiedName, docId, schema);

            int bookmarkId = 400;

            // PAGE 1: COVER PAGE
            renderCoverPage(doc, "Database Table Technical Specification", fullQualifiedName, docId, schema);

            // PAGE 2: TABLE OF CONTENTS (TOC)
            addHeading(doc, "Table of Contents", 16, true, "TOC_TOP", bookmarkId++);
            renderAutomaticTable2Entry(doc, "SEC_1", "1.0  Table Overview & Architectural Role", 2, 1);
            renderAutomaticTable2Entry(doc, "SEC_1_1", "1.1  Executive Overview & Business Purpose", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_2", "1.2  Architectural Role & Entity Classification", 2, 2);
            renderAutomaticTable2Entry(doc, "SEC_1_3", "1.3  Data Lifecycle & Operations Flow", 2, 2);

            renderAutomaticTable2Entry(doc, "SEC_2", "2.0  Column Definitions & Data Dictionary", 3, 1);
            renderAutomaticTable2Entry(doc, "SEC_3", "3.0  Primary, Foreign & Unique Key Constraints", 4, 1);
            renderAutomaticTable2Entry(doc, "SEC_4", "4.0  Physical Indexes & Performance Tuning", 5, 1);
            renderAutomaticTable2Entry(doc, "SEC_5", "5.0  Triggers, Sequences & CDC Tracking", 6, 1);

            renderAutomaticTable2Entry(doc, "SEC_6", "6.0  Data Lineage & Referencing Routines (CRUD Access)", 7, 1);
            renderAutomaticTable2Entry(doc, "SEC_6_1", "6.1  Upstream Ingress Writers (INSERT / UPDATE / DELETE)", 7, 2);
            renderAutomaticTable2Entry(doc, "SEC_6_2", "6.2  Downstream Egress Readers (SELECT Queries / Views)", 7, 2);

            renderAutomaticTable2Entry(doc, "SEC_7", "7.0  Operational Notes, Maintenance & Storage", 8, 1);
            renderAutomaticTable2Entry(doc, "SEC_8", "8.0  QA Integrity Verification Matrix", 9, 1);
            renderAutomaticTable2Entry(doc, "SEC_9", "9.0  Table DDL & Index Script", 10, 1);

            addSectionDivider(doc);

            // PAGE 3: SECTION 1.0 - TABLE OVERVIEW & ARCHITECTURAL ROLE
            addHeading(doc, "1.0 Table Overview & Architectural Role", 14, true, "SEC_1", bookmarkId++);

            addHeading(doc, "1.1 Executive Overview & Business Purpose", 12, false, "SEC_1_1", bookmarkId++);
            addCalloutBox(doc, "TABLE PURPOSE:", "[type here]");
            addBlankTextLines(doc, 2);

            addHeading(doc, "1.2 Architectural Role & Entity Classification", 12, false, "SEC_1_2", bookmarkId++);
            addBulletPoint(doc, "Classification", "[CORE TRANSACTION LEDGER / STAGING / REFERENCE LOOKUP / AUDIT HISTORICAL]");
            addBulletPoint(doc, "Owning Subsystem", schema + " database schema");
            addBulletPoint(doc, "Storage Model", "Relational OLTP Entity with row-level locking and ACID transaction logging");
            addFaintDivider(doc);

            addHeading(doc, "1.3 Data Lifecycle & Operations Flow", 12, false, "SEC_1_3", bookmarkId++);
            addNumberedStep(doc, 1, "Data Intake & Record Creation", "[type here]");
            addNumberedStep(doc, 2, "Data Integrity & Constraint Checks", "[type here]");
            addNumberedStep(doc, 3, "State Updates & Transitions", "[type here]");
            addNumberedStep(doc, 4, "Query & Downstream Consumption", "[type here]");
            addNumberedStep(doc, 5, "Retention & Archiving", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 2.0 - COLUMN DEFINITIONS & DATA DICTIONARY
            addHeading(doc, "2.0 Column Definitions & Data Dictionary", 14, false, "SEC_2", bookmarkId++);
            renderTableColumnsTableShell(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 3.0 - PRIMARY, FOREIGN & UNIQUE KEY CONSTRAINTS
            addHeading(doc, "3.0 Primary, Foreign & Unique Key Constraints", 14, false, "SEC_3", bookmarkId++);
            renderConstraintsTableShell(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 4.0 - PHYSICAL INDEXES & PERFORMANCE TUNING
            addHeading(doc, "4.0 Physical Indexes & Performance Tuning", 14, false, "SEC_4", bookmarkId++);
            renderIndexesTableShell(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 5.0 - TRIGGERS, SEQUENCES & CDC TRACKING
            addHeading(doc, "5.0 Triggers, Sequences & CDC Tracking", 14, false, "SEC_5", bookmarkId++);
            addBulletPoint(doc, "Sequences", "[type here]");
            addBulletPoint(doc, "Active Triggers", "[type here]");
            addBulletPoint(doc, "CDC / Auditing", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 6.0 - DATA LINEAGE & REFERENCING ROUTINES (CRUD ACCESS)
            addHeading(doc, "6.0 Data Lineage & Referencing Routines (CRUD Access)", 14, false, "SEC_6", bookmarkId++);

            addHeading(doc, "6.1 Upstream Ingress Writers (INSERT / UPDATE / DELETE)", 12, false, "SEC_6_1", bookmarkId++);
            addBulletPoint(doc, "Writing Routine 1", "[type here]");
            addBulletPoint(doc, "Writing Routine 2", "[type here]");
            addFaintDivider(doc);

            addHeading(doc, "6.2 Downstream Egress Readers (SELECT Queries / Views)", 12, false, "SEC_6_2", bookmarkId++);
            addBulletPoint(doc, "Reading Routine 1", "[type here]");
            addBulletPoint(doc, "Reading Routine 2", "[type here]");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 7.0 - OPERATIONAL NOTES, MAINTENANCE & STORAGE
            addHeading(doc, "7.0 Operational Notes, Maintenance & Storage", 14, false, "SEC_7", bookmarkId++);
            addBulletPoint(doc, "Storage Tablespace", "[type here]");
            addBulletPoint(doc, "Optimizer Statistics", "Refreshed via DBMS_STATS.GATHER_TABLE_STATS(ownname => '" + schema + "', tabname => '" + tableName + "', estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE, cascade => TRUE)");
            addBulletPoint(doc, "Retention Policy", "Governed by Florida Lottery Rule 53ER statutory record retention standards.");
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 8.0 - QA INTEGRITY VERIFICATION MATRIX
            addHeading(doc, "8.0 QA Integrity Verification Matrix", 14, false, "SEC_8", bookmarkId++);
            renderTableQaMatrixShell(doc);
            addFaintDivider(doc);
            addBackToTocLink(doc);

            // SECTION 9.0 - TABLE DDL & INDEX SCRIPT
            addHeading(doc, "9.0 Table DDL & Index Script", 14, true, "SEC_9", bookmarkId++);
            addCodeBlock(doc, "-- ============================================================================\n" +
                    "-- Florida Lottery Database Table Specification DDL\n" +
                    "-- Schema: [SCHEMA_NAME] | Table: [TABLE_NAME]\n" +
                    "-- ============================================================================\n" +
                    "CREATE TABLE [SCHEMA_NAME].[TABLE_NAME] (\n" +
                    "    record_id        NUMBER(18,0)   NOT NULL,\n" +
                    "    batch_id         NUMBER(12,0)   NOT NULL,\n" +
                    "    retailer_id      NUMBER(10,0)   NOT NULL,\n" +
                    "    transaction_date DATE           DEFAULT SYSDATE NOT NULL,\n" +
                    "    amount           NUMBER(12,2)   DEFAULT 0.00 NOT NULL,\n" +
                    "    status_flag      VARCHAR2(10)   DEFAULT 'ACTIVE' NOT NULL,\n" +
                    "    created_timestamp TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,\n" +
                    "    created_by       VARCHAR2(50)   DEFAULT USER NOT NULL,\n" +
                    "    CONSTRAINT PK_[TABLE_NAME] PRIMARY KEY (record_id)\n" +
                    ");\n" +
                    "\n" +
                    "-- Indexes\n" +
                    "CREATE INDEX [SCHEMA_NAME].IDX_[TABLE_NAME]_TX_DATE ON [SCHEMA_NAME].[TABLE_NAME] (transaction_date);\n" +
                    "CREATE INDEX [SCHEMA_NAME].IDX_[TABLE_NAME]_RETAILER ON [SCHEMA_NAME].[TABLE_NAME] (retailer_id);", "[table_name]_DDL.sql");

            addBackToTocLink(doc);

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                doc.write(fos);
            }
        }
    }

    // =========================================================================
    // REUSABLE FORMATTING & STRUCTURAL PRIMITIVES
    // =========================================================================

    /**
     * Renders formal Florida Lottery cover page with executive title, metadata,
     * approvals sign-off lines, and revision history table.
     */
    private static void renderCoverPage(XWPFDocument doc, String mainTitle, String fullObjectName, String docId, String schema) {
        // Top spacing
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

        // 2. Full Object Name Header Banner
        XWPFTable bannerTable = doc.createTable(1, 1);
        setTableExplicitWidths(bannerTable, new int[]{FULL_CONTENT_WIDTH_DXA});
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
        String currentDate = new SimpleDateFormat("MMMM d, yyyy").format(new Date());
        String[][] metaPairs = {
                {"Primary Author / Lead:", "[type here]"},
                {"Creation Date:", currentDate},
                {"Last Updated:", currentDate},
                {"Document Version:", "1.0 Draft"},
                {"Document Identifier:", docId},
                {"Target Database:", "[type here]"},
                {"Jira / CR Ticket:", "[type here]"},
                {"Owning Schema:", schema.toUpperCase()},
                {"Data Classification:", "Internal Confidential / Florida Lottery IR-SDS"}
        };

        XWPFTable metaTable = doc.createTable(metaPairs.length, 2);
        setTableExplicitWidths(metaTable, new int[]{2500, 8300});
        setTableBorders(metaTable, "FFFFFF");
        metaTable.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        metaTable.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        metaTable.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        metaTable.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        metaTable.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        metaTable.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");

        for (int i = 0; i < metaPairs.length; i++) {
            XWPFTableCell c0 = metaTable.getRow(i).getCell(0);
            c0.removeParagraph(0);
            XWPFParagraph p0 = c0.addParagraph();
            p0.setSpacingBefore(5);
            p0.setSpacingAfter(5);
            XWPFRun kRun = p0.createRun();
            kRun.setText(metaPairs[i][0]);
            kRun.setBold(true);
            kRun.setFontSize(9.5);
            kRun.setFontFamily("Segoe UI Semibold");
            kRun.setColor("222222");

            XWPFTableCell c1 = metaTable.getRow(i).getCell(1);
            c1.removeParagraph(0);
            XWPFParagraph p1 = c1.addParagraph();
            p1.setSpacingBefore(5);
            p1.setSpacingAfter(5);

            String val = metaPairs[i][1];
            XWPFRun vRun = p1.createRun();
            vRun.setText(val);
            vRun.setFontSize(9.5);
            vRun.setFontFamily("Segoe UI");
            if (val != null && (val.contains("[type here]") || (val.startsWith("[") && val.endsWith("]")))) {
                vRun.setItalic(true);
                vRun.setColor(COLOR_PLACEHOLDER);
            } else {
                vRun.setColor("333333");
            }
        }

        // 4. Formal Approvals & Technical Sign-off Table
        XWPFParagraph appHeading = doc.createParagraph();
        appHeading.setSpacingBefore(60);
        appHeading.setSpacingAfter(15);
        XWPFRun ahRun = appHeading.createRun();
        ahRun.setText("Approvals & Technical Sign-off");
        ahRun.setFontSize(10.5);
        ahRun.setBold(true);
        ahRun.setColor(COLOR_PRIMARY);
        ahRun.setFontFamily("Segoe UI Semibold");

        XWPFTable appTable = doc.createTable(5, 5);
        setTableExplicitWidths(appTable, new int[]{2500, 2600, 2600, 1600, 1500});
        setTableBorders(appTable, COLOR_BORDER);

        setCell(appTable, 0, 0, "Approval Role", true, COLOR_PRIMARY, "FFFFFF");
        setCell(appTable, 0, 1, "Approver Name", true, COLOR_PRIMARY, "FFFFFF");
        setCell(appTable, 0, 2, "Title / Position", true, COLOR_PRIMARY, "FFFFFF");
        setCell(appTable, 0, 3, "Sign-off Status", true, COLOR_PRIMARY, "FFFFFF");
        setCell(appTable, 0, 4, "Date", true, COLOR_PRIMARY, "FFFFFF");

        String[][] approvalRows = {
                {"Primary Developer", "[type here]", "Programmer Analyst III", "Approved", "YYYY-MM-DD"},
                {"Technical Reviewer", "[type here]", "Senior Database Analyst", "In Review", "YYYY-MM-DD"},
                {"Enterprise Architect", "[type here]", "Lead Enterprise Architect", "Pending", "YYYY-MM-DD"},
                {"Development Supervisor", "[type here]", "Software Development Manager", "Pending", "YYYY-MM-DD"}
        };

        for (int i = 0; i < approvalRows.length; i++) {
            String bg = (i % 2 == 0 ? null : COLOR_LIGHT_BG);
            setCell(appTable, i + 1, 0, approvalRows[i][0], true, bg);
            setCell(appTable, i + 1, 1, approvalRows[i][1], false, bg);
            setCell(appTable, i + 1, 2, approvalRows[i][2], false, bg);
            setCell(appTable, i + 1, 3, approvalRows[i][3], false, bg);
            setCell(appTable, i + 1, 4, approvalRows[i][4], false, bg);
        }

        // 5. Revision History Table
        XWPFParagraph revHeading = doc.createParagraph();
        revHeading.setSpacingBefore(60);
        revHeading.setSpacingAfter(15);
        XWPFRun rhRun = revHeading.createRun();
        rhRun.setText("Revision History");
        rhRun.setBold(true);
        rhRun.setFontSize(10.5);
        rhRun.setColor(COLOR_PRIMARY);
        rhRun.setFontFamily("Segoe UI Semibold");

        XWPFTable revTable = doc.createTable(3, 4);
        setTableExplicitWidths(revTable, new int[]{1400, 1800, 2600, 5000});
        setTableBorders(revTable, COLOR_BORDER);

        setCell(revTable, 0, 0, "Version", true, COLOR_PRIMARY, "FFFFFF");
        setCell(revTable, 0, 1, "Date", true, COLOR_PRIMARY, "FFFFFF");
        setCell(revTable, 0, 2, "Author", true, COLOR_PRIMARY, "FFFFFF");
        setCell(revTable, 0, 3, "Description of Change", true, COLOR_PRIMARY, "FFFFFF");

        setCell(revTable, 1, 0, "1.0", false, null);
        setCell(revTable, 1, 1, currentDate, false, null);
        setCell(revTable, 1, 2, "[type here]", false, null);
        setCell(revTable, 1, 3, "Initial draft technical specification for " + fullObjectName, false, null);

        setCell(revTable, 2, 0, "1.1", false, COLOR_LIGHT_BG);
        setCell(revTable, 2, 1, "[type here]", false, COLOR_LIGHT_BG);
        setCell(revTable, 2, 2, "[type here]", false, COLOR_LIGHT_BG);
        setCell(revTable, 2, 3, "[type here]", false, COLOR_LIGHT_BG);
    }

    /**
     * Renders a blank parameter table shell ready for developer editing.
     */
    private static void renderParametersTableShell(XWPFDocument doc, boolean includeOutput) {
        int rows = includeOutput ? 4 : 3;
        XWPFTable table = doc.createTable(rows, 4);
        setTableExplicitWidths(table, new int[]{2700, 1080, 1620, 5400});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Parameter Name", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Direction", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Data Type", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 3, "Description & Business Validation Rules", true, COLOR_PRIMARY, "FFFFFF");

        setCell(table, 1, 0, "[P_RUN_DATE]", true, null);
        setCell(table, 1, 1, "[IN / OUT / IN OUT]", false, null);
        setCell(table, 1, 2, "[DATE / NUMBER / VARCHAR2]", false, null);
        setCell(table, 1, 3, "[type here]", false, null);

        setCell(table, 2, 0, "[P_BATCH_ID]", true, COLOR_LIGHT_BG);
        setCell(table, 2, 1, "[IN / OUT / IN OUT]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 2, "[NUMBER / VARCHAR2 / DATE]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 3, "[type here]", false, COLOR_LIGHT_BG);

        if (includeOutput) {
            setCell(table, 3, 0, "[P_STATUS_CODE]", true, null);
            setCell(table, 3, 1, "[OUT / IN OUT]", false, null);
            setCell(table, 3, 2, "[VARCHAR2 / NUMBER]", false, null);
            setCell(table, 3, 3, "[type here]", false, null);
        }
    }

    /**
     * Renders a blank package member subroutines directory table.
     */
    private static void renderSubroutinesDirectoryTable(XWPFDocument doc) {
        XWPFTable table = doc.createTable(3, 4);
        setTableExplicitWidths(table, new int[]{3200, 1600, 2400, 3600});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Subroutine Name", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Routine Type", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Public / Private", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 3, "Operational Responsibility", true, COLOR_PRIMARY, "FFFFFF");

        setCell(table, 1, 0, "[member_procedure_name]", true, null);
        setCell(table, 1, 1, "[PROCEDURE / FUNCTION]", false, null);
        setCell(table, 1, 2, "[PUBLIC (In Spec) / PRIVATE (In Body)]", false, null);
        setCell(table, 1, 3, "[type here]", false, null);

        setCell(table, 2, 0, "[member_function_name]", true, COLOR_LIGHT_BG);
        setCell(table, 2, 1, "[FUNCTION / PROCEDURE]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 2, "[PUBLIC (In Spec) / PRIVATE (In Body)]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 3, "[type here]", false, COLOR_LIGHT_BG);
    }

    /**
     * Renders a blank package types and constants table shell.
     */
    private static void renderPackageTypesTable(XWPFDocument doc) {
        XWPFTable table = doc.createTable(3, 4);
        setTableExplicitWidths(table, new int[]{2700, 1800, 2300, 4000});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Identifier / Constant", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Category", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Data Type / Value", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 3, "Usage Description", true, COLOR_PRIMARY, "FFFFFF");

        setCell(table, 1, 0, "[CONSTANT_OR_TYPE_NAME]", true, null);
        setCell(table, 1, 1, "[CONSTANT / TYPE RECORD / TYPE TABLE / EXCEPTION]", false, null);
        setCell(table, 1, 2, "[VARCHAR2(30) := '...' / [SCHEMA].[TABLE]%ROWTYPE]", false, null);
        setCell(table, 1, 3, "[type here]", false, null);

        setCell(table, 2, 0, "[RECORD_OR_COLLECTION_NAME]", true, COLOR_LIGHT_BG);
        setCell(table, 2, 1, "[TYPE RECORD / TYPE TABLE / SUBTYPE]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 2, "[RECORD (...) / TABLE OF ... INDEX BY PLS_INTEGER]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 3, "[type here]", false, COLOR_LIGHT_BG);
    }

    /**
     * Renders a blank QA testing matrix table shell.
     */
    private static void renderTestingMatrixTableShell(XWPFDocument doc) {
        XWPFTable table = doc.createTable(5, 3);
        setTableExplicitWidths(table, new int[]{2700, 4050, 4050});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Test Case ID", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Input / Test Condition", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Expected Verification State", true, COLOR_PRIMARY, "FFFFFF");

        setCell(table, 1, 0, "TC-01: Standard Positive Test", true, null);
        setCell(table, 1, 1, "[type here]", false, null);
        setCell(table, 1, 2, "[type here]", false, null);

        setCell(table, 2, 0, "TC-02: Mandatory Argument Guard", true, COLOR_LIGHT_BG);
        setCell(table, 2, 1, "[type here]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 2, "[type here]", false, COLOR_LIGHT_BG);

        setCell(table, 3, 0, "TC-03: Boundary Condition Test", true, null);
        setCell(table, 3, 1, "[type here]", false, null);
        setCell(table, 3, 2, "[type here]", false, null);

        setCell(table, 4, 0, "TC-04: Idempotency & Re-Run", true, COLOR_LIGHT_BG);
        setCell(table, 4, 1, "[type here]", false, COLOR_LIGHT_BG);
        setCell(table, 4, 2, "[type here]", false, COLOR_LIGHT_BG);
    }

    /**
     * Renders a blank table columns data dictionary shell.
     */
    private static void renderTableColumnsTableShell(XWPFDocument doc) {
        XWPFTable table = doc.createTable(6, 6);
        setTableExplicitWidths(table, new int[]{2200, 1600, 1000, 1200, 1400, 3400});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Column Name", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Data Type", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Nullable?", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 3, "Default", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 4, "Key / Constraint", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 5, "Description & Business Semantics", true, COLOR_PRIMARY, "FFFFFF");

        String[][] sampleCols = {
                {"[COLUMN_NAME_1]", "[NUMBER(18,0) / VARCHAR2(50)]", "[NO / YES]", "[- / NULL]", "[PRIMARY KEY]", "[type here]"},
                {"[COLUMN_NAME_2]", "[NUMBER(12,0) / VARCHAR2(30)]", "[NO / YES]", "[- / NULL]", "[INDEXED / FK]", "[type here]"},
                {"[COLUMN_NAME_3]", "[VARCHAR2(100) / NUMBER(10,2)]", "[NO / YES]", "[- / 'N']", "[- / CHECK]", "[type here]"},
                {"[TRANSACTION_DATE]", "[DATE]", "[NO / YES]", "[SYSDATE]", "[INDEXED]", "[type here]"},
                {"[CREATED_DATE]", "[DATE]", "[NO]", "[SYSDATE]", "[-]", "[type here]"}
        };

        for (int i = 0; i < sampleCols.length; i++) {
            String bg = (i % 2 == 0 ? null : COLOR_LIGHT_BG);
            setCell(table, i + 1, 0, sampleCols[i][0], true, bg);
            setCell(table, i + 1, 1, sampleCols[i][1], false, bg);
            setCell(table, i + 1, 2, sampleCols[i][2], false, bg);
            setCell(table, i + 1, 3, sampleCols[i][3], false, bg);
            setCell(table, i + 1, 4, sampleCols[i][4], false, bg);
            setCell(table, i + 1, 5, sampleCols[i][5], false, bg);
        }
    }

    /**
     * Renders a blank table constraints table shell.
     */
    private static void renderConstraintsTableShell(XWPFDocument doc) {
        XWPFTable table = doc.createTable(4, 4);
        setTableExplicitWidths(table, new int[]{2700, 1620, 2700, 3780});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Constraint Name", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Constraint Type", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Column(s) Enforced", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 3, "Referenced Target / Rule", true, COLOR_PRIMARY, "FFFFFF");

        setCell(table, 1, 0, "PK_[TABLE_NAME]", true, null);
        setCell(table, 1, 1, "[PRIMARY KEY / FOREIGN KEY / CHECK / UNIQUE]", false, null);
        setCell(table, 1, 2, "[RECORD_ID]", false, null);
        setCell(table, 1, 3, "[type here]", false, null);

        setCell(table, 2, 0, "FK_[TABLE]_[TARGET]", true, COLOR_LIGHT_BG);
        setCell(table, 2, 1, "[FOREIGN KEY]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 2, "[RETAILER_ID]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 3, "[type here]", false, COLOR_LIGHT_BG);

        setCell(table, 3, 0, "CK_[TABLE]_[RULE]", true, null);
        setCell(table, 3, 1, "[CHECK / UNIQUE]", false, null);
        setCell(table, 3, 2, "[AMOUNT]", false, null);
        setCell(table, 3, 3, "[type here]", false, null);
    }

    /**
     * Renders a blank table physical indexes table shell.
     */
    private static void renderIndexesTableShell(XWPFDocument doc) {
        XWPFTable table = doc.createTable(3, 4);
        setTableExplicitWidths(table, new int[]{2700, 1620, 3780, 2700});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Index Name", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Uniqueness", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Indexed Column(s)", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 3, "Tablespace / Notes", true, COLOR_PRIMARY, "FFFFFF");

        setCell(table, 1, 0, "PK_[TABLE_NAME]", true, null);
        setCell(table, 1, 1, "[UNIQUE / NON-UNIQUE]", false, null);
        setCell(table, 1, 2, "[RECORD_ID]", false, null);
        setCell(table, 1, 3, "[type here]", false, null);

        setCell(table, 2, 0, "IDX_[TABLE]_[COLUMNS]", true, COLOR_LIGHT_BG);
        setCell(table, 2, 1, "[NON-UNIQUE / UNIQUE]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 2, "[TRANSACTION_DATE, RETAILER_ID]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 3, "[type here]", false, COLOR_LIGHT_BG);
    }

    /**
     * Renders a table QA verification matrix shell.
     */
    private static void renderTableQaMatrixShell(XWPFDocument doc) {
        XWPFTable table = doc.createTable(5, 3);
        setTableExplicitWidths(table, new int[]{2700, 4050, 4050});
        setTableBorders(table, COLOR_BORDER);

        setCell(table, 0, 0, "Verification Target", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 1, "Integrity Test Vector", true, COLOR_PRIMARY, "FFFFFF");
        setCell(table, 0, 2, "Expected Verification State", true, COLOR_PRIMARY, "FFFFFF");

        setCell(table, 1, 0, "TC-01: PK Uniqueness", true, null);
        setCell(table, 1, 1, "[type here]", false, null);
        setCell(table, 1, 2, "[type here]", false, null);

        setCell(table, 2, 0, "TC-02: Mandatory Column Domain", true, COLOR_LIGHT_BG);
        setCell(table, 2, 1, "[type here]", false, COLOR_LIGHT_BG);
        setCell(table, 2, 2, "[type here]", false, COLOR_LIGHT_BG);

        setCell(table, 3, 0, "TC-03: Foreign Key Referential Integrity", true, null);
        setCell(table, 3, 1, "[type here]", false, null);
        setCell(table, 3, 2, "[type here]", false, null);

        setCell(table, 4, 0, "TC-04: Index Scan Performance", true, COLOR_LIGHT_BG);
        setCell(table, 4, 1, "[type here]", false, COLOR_LIGHT_BG);
        setCell(table, 4, 2, "[type here]", false, COLOR_LIGHT_BG);
    }

    // =========================================================================
    // OPENXML / POI UTILITY METHODS
    // =========================================================================

    public static XWPFParagraph addHeading(XWPFDocument doc, String text, int fontSize, boolean isPageStart, String bookmarkName, int bookmarkId) {
        ensureDocumentHeadingStyles(doc);
        XWPFParagraph p = doc.createParagraph();

        int outlineLevel = (fontSize >= 14) ? 0 : (fontSize >= 12 ? 1 : 2);
        String headingStyle = (fontSize >= 14) ? "Heading 1" : (fontSize >= 12 ? "Heading 2" : "Heading 3");
        try {
            p.setStyle(headingStyle);
        } catch (Exception ignored) {}

        try {
            CTPPr pPr = p.getCTP().getPPr();
            if (pPr == null) pPr = p.getCTP().addNewPPr();
            if (pPr.getPStyle() == null) {
                pPr.addNewPStyle().setVal(headingStyle);
            } else {
                pPr.getPStyle().setVal(headingStyle);
            }
            if (pPr.getOutlineLvl() == null) {
                pPr.addNewOutlineLvl().setVal(BigInteger.valueOf(outlineLevel));
            } else {
                pPr.getOutlineLvl().setVal(BigInteger.valueOf(outlineLevel));
            }
            if (pPr.getKeepNext() == null) {
                pPr.addNewKeepNext();
            }
            if (pPr.getWidowControl() == null) {
                pPr.addNewWidowControl();
            }
        } catch (Exception ignored) {}

        if (isPageStart) {
            p.setPageBreak(true);
            p.setSpacingBefore(0);
        } else {
            p.setSpacingBefore(fontSize >= 14 ? 180 : 120);
        }
        p.setSpacingAfter(fontSize >= 14 ? 60 : 30);

        if (bookmarkName != null && !bookmarkName.isEmpty()) {
            addBookmark(p, bookmarkName, bookmarkId);
        }

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
        run.setBold(true);
        run.setColor(COLOR_PRIMARY);
        run.setFontFamily("Segoe UI Semibold");
        return p;
    }

    public static XWPFParagraph addHeading(XWPFDocument doc, String text, int fontSize) {
        return addHeading(doc, text, fontSize, false, null, 0);
    }

    private static void addBookmark(XWPFParagraph paragraph, String bookmarkName, int bookmarkId) {
        try {
            CTBookmark bookmarkStart = paragraph.getCTP().addNewBookmarkStart();
            bookmarkStart.setName(bookmarkName);
            bookmarkStart.setId(BigInteger.valueOf(bookmarkId));

            CTMarkupRange bookmarkEnd = paragraph.getCTP().addNewBookmarkEnd();
            bookmarkEnd.setId(BigInteger.valueOf(bookmarkId));
        } catch (Exception ignored) {}
    }

    private static void renderAutomaticTable2Entry(XWPFDocument doc, String bookmarkName, String title, int pageNum, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(level == 1 ? 50 : 25);
        p.setSpacingAfter(level == 1 ? 40 : 20);

        if (level == 2) {
            p.setIndentationLeft(280);
        } else if (level >= 3) {
            p.setIndentationLeft(560);
        }

        try {
            CTP ctp = p.getCTP();
            CTPPr pPr = ctp.getPPr();
            if (pPr == null) pPr = ctp.addNewPPr();

            CTTabs tabs = pPr.addNewTabs();
            CTTabStop rightStop = tabs.addNewTab();
            rightStop.setVal(STTabJc.RIGHT);
            rightStop.setLeader(STTabTlc.DOT);
            rightStop.setPos(BigInteger.valueOf(10080));

            CTHyperlink cLink = ctp.addNewHyperlink();
            cLink.setAnchor(bookmarkName);

            // 1. Title Run
            CTR rText = cLink.addNewR();
            CTRPr rPrText = rText.addNewRPr();
            rPrText.addNewRFonts().setAscii("Segoe UI");
            rPrText.addNewRFonts().setHAnsi("Segoe UI");
            rPrText.addNewSz().setVal(BigInteger.valueOf(level == 1 ? 20 : 19));
            if (level == 1) {
                rPrText.addNewB();
                rPrText.addNewColor().setVal("111827");
            } else {
                rPrText.addNewColor().setVal("2D3748");
            }
            CTText t1 = rText.addNewT();
            t1.setStringValue(title);

            // 2. Dot Leader Tab Run
            CTR rTab = cLink.addNewR();
            CTRPr rPrTab = rTab.addNewRPr();
            rPrTab.addNewRFonts().setAscii("Segoe UI");
            rPrTab.addNewColor().setVal("888888");
            rTab.addNewTab();

            // 3. Page Number Run
            CTR rPage = cLink.addNewR();
            CTRPr rPrPage = rPage.addNewRPr();
            rPrPage.addNewRFonts().setAscii("Segoe UI");
            rPrPage.addNewRFonts().setHAnsi("Segoe UI");
            rPrPage.addNewSz().setVal(BigInteger.valueOf(level == 1 ? 20 : 19));
            if (level == 1) {
                rPrPage.addNewB();
            }
            rPrPage.addNewColor().setVal(COLOR_PRIMARY);
            CTText t2 = rPage.addNewT();
            t2.setStringValue(String.valueOf(pageNum));

        } catch (Exception e) {
            XWPFRun r = p.createRun();
            r.setText(title + "\t" + pageNum);
        }
    }

    private static void addBackToTocLink(XWPFDocument doc) {
        try {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.RIGHT);
            p.setSpacingBefore(40);
            p.setSpacingAfter(40);

            CTHyperlink cLink = p.getCTP().addNewHyperlink();
            cLink.setAnchor("TOC_TOP");
            CTR cRun = cLink.addNewR();
            CTRPr rPr = cRun.addNewRPr();
            CTColor color = rPr.addNewColor();
            color.setVal(COLOR_PRIMARY);
            rPr.addNewU().setVal(STUnderline.SINGLE);
            CTFonts fonts = rPr.addNewRFonts();
            fonts.setAscii("Segoe UI");
            fonts.setHAnsi("Segoe UI");
            CTHpsMeasure sz = rPr.addNewSz();
            sz.setVal(BigInteger.valueOf(16));
            CTText t = cRun.addNewT();
            t.setStringValue("▲ Back to Top (Table of Contents)");
        } catch (Exception ignored) {}
    }

    /**
     * Adds a simple, clean placeholder run (e.g. "[type here]") to a paragraph.
     * Rendered in Segoe UI italic muted text so developers immediately know where to type,
     * without any complex Word field codes, macros, or popup dialogs.
     *
     * @param p Target XWPFParagraph
     * @param text The placeholder text (defaults to "[type here]")
     * @param isBold Whether the placeholder should be bold
     */
    public static void addPlaceholder(XWPFParagraph p, String text, boolean isBold) {
        if (p == null) return;
        XWPFRun r = p.createRun();
        String val = (text != null && !text.isEmpty()) ? text : PLACEHOLDER;
        r.setText(val);
        r.setFontFamily("Segoe UI");
        r.setFontSize(9.5);
        r.setBold(isBold);
        r.setItalic(true);
        r.setColor(COLOR_PLACEHOLDER);
    }

    public static void addPlaceholder(XWPFParagraph p, String text) {
        addPlaceholder(p, text, false);
    }

    public static void addPlaceholder(XWPFParagraph p) {
        addPlaceholder(p, PLACEHOLDER, false);
    }

    /**
     * Backward-compatible fill-in helper that renders a simple [type here] text run.
     */
    public static void addFillInField(XWPFParagraph p, String promptText, boolean isBold, String textColorHex, String bgHighlightHex) {
        addPlaceholder(p, promptText, isBold);
    }

    public static void addFillInField(XWPFParagraph p, String promptText, boolean isBold) {
        addPlaceholder(p, promptText, isBold);
    }

    public static void addFillInField(XWPFParagraph p, String promptText) {
        addPlaceholder(p, promptText, false);
    }

    public static void addFieldPromptParagraph(XWPFDocument doc, String promptText) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(30);
        p.setSpacingAfter(30);
        p.setIndentationLeft(180);
        addPlaceholder(p, promptText != null ? promptText : PLACEHOLDER);
    }

    private static void addCalloutBox(XWPFDocument doc, String label, String text) {
        XWPFParagraph p = doc.createParagraph();
        disableProofing(p);
        p.setSpacingBefore(80);
        p.setSpacingAfter(140);
        XWPFRun lRun = p.createRun();
        lRun.setText(label + " ");
        lRun.setBold(true);
        lRun.setColor(COLOR_PRIMARY);
        lRun.setFontFamily("Segoe UI");

        String val = (text != null && !text.isEmpty()) ? text : PLACEHOLDER;
        XWPFRun tRun = p.createRun();
        tRun.setText(val);
        tRun.setFontFamily("Segoe UI");
        if (val.contains("[type here]") || (val.startsWith("[") && val.endsWith("]"))) {
            tRun.setItalic(true);
            tRun.setColor(COLOR_PLACEHOLDER);
        } else {
            tRun.setColor("333333");
        }
    }

    private static void addBulletPoint(XWPFDocument doc, String label, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(20);
        p.setSpacingAfter(20);
        p.setIndentationLeft(360);
        p.setIndentationHanging(180);

        XWPFRun bulletRun = p.createRun();
        bulletRun.setText("•  ");
        bulletRun.setBold(true);
        bulletRun.setColor(COLOR_PRIMARY);
        bulletRun.setFontFamily("Segoe UI");

        XWPFRun lRun = p.createRun();
        lRun.setText(label + ": ");
        lRun.setBold(true);
        lRun.setColor("111827");
        lRun.setFontFamily("Segoe UI");

        String val = (text != null && !text.isEmpty()) ? text : PLACEHOLDER;
        XWPFRun tRun = p.createRun();
        tRun.setText(val);
        tRun.setFontFamily("Segoe UI");
        if (val.contains("[type here]") || (val.startsWith("[") && val.endsWith("]"))) {
            tRun.setItalic(true);
            tRun.setColor(COLOR_PLACEHOLDER);
        } else {
            tRun.setColor("333333");
        }
    }

    private static void addNumberedStep(XWPFDocument doc, int stepNum, String title, String details) {
        XWPFParagraph p = doc.createParagraph();
        disableProofing(p);
        p.setSpacingBefore(30);
        p.setSpacingAfter(35);
        p.setIndentationLeft(360);
        p.setIndentationHanging(360);

        XWPFRun rNum = p.createRun();
        rNum.setText(stepNum + ".\t");
        rNum.setBold(true);
        rNum.setColor(COLOR_PRIMARY);
        rNum.setFontFamily("Segoe UI Semibold");
        rNum.setFontSize(9.5);

        XWPFRun rTitle = p.createRun();
        rTitle.setText(title + (title.endsWith(":") ? " " : ": "));
        rTitle.setBold(true);
        rTitle.setColor("111827");
        rTitle.setFontFamily("Segoe UI Semibold");
        rTitle.setFontSize(9.5);

        String val = (details != null && !details.isEmpty()) ? details : PLACEHOLDER;
        XWPFRun rDetails = p.createRun();
        rDetails.setText(val);
        rDetails.setFontFamily("Segoe UI");
        rDetails.setFontSize(9.5);
        if (val.contains("[type here]") || (val.startsWith("[") && val.endsWith("]"))) {
            rDetails.setItalic(true);
            rDetails.setColor(COLOR_PLACEHOLDER);
        } else {
            rDetails.setColor("333333");
        }
    }

    private static void addBlankTextLines(XWPFDocument doc, int count) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(20);
        p.setSpacingAfter(20);
        p.setIndentationLeft(180);

        XWPFRun r = p.createRun();
        r.setText(PLACEHOLDER);
        r.setFontFamily("Segoe UI");
        r.setItalic(true);
        r.setColor(COLOR_PLACEHOLDER);
        r.setFontSize(9.5);

        for (int i = 1; i < count; i++) {
            XWPFParagraph sp = doc.createParagraph();
            sp.setSpacingBefore(10);
            sp.setSpacingAfter(10);
        }
    }

    private static void addFaintDivider(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(30);
        p.setSpacingAfter(30);
        try {
            CTPPr pPr = p.getCTP().getPPr();
            if (pPr == null) pPr = p.getCTP().addNewPPr();
            CTPBdr pBdr = pPr.addNewPBdr();
            CTBorder bdr = pBdr.addNewBottom();
            bdr.setVal(STBorder.SINGLE);
            bdr.setSz(BigInteger.valueOf(4));
            bdr.setColor("E2E8F0");
            bdr.setSpace(BigInteger.valueOf(1));
        } catch (Exception e) {
            p.setBorderBottom(Borders.SINGLE);
        }
    }

    private static void addSectionDivider(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(60);
        p.setSpacingAfter(60);
        p.setBorderBottom(Borders.SINGLE);
    }

    private static void addCodeBlock(XWPFDocument doc, String code, String filename) {
        if (filename != null && !filename.trim().isEmpty()) {
            XWPFParagraph capPara = doc.createParagraph();
            capPara.setSpacingBefore(60);
            capPara.setSpacingAfter(20);
            disableProofing(capPara);

            XWPFRun fnRun = capPara.createRun();
            fnRun.setText("📄 " + filename.trim());
            fnRun.setFontFamily("Consolas");
            fnRun.setFontSize(8.0);
            fnRun.setBold(true);
            fnRun.setColor(COLOR_PRIMARY);
            disableProofing(fnRun);
        }

        XWPFTable codeBox = doc.createTable(1, 1);
        setTableExplicitWidths(codeBox, new int[]{FULL_CONTENT_WIDTH_DXA});
        setTableBorders(codeBox, COLOR_CODE_BORDER);

        XWPFTableCell cell = codeBox.getRow(0).getCell(0);
        cell.setColor(COLOR_CODE_BG);
        cell.removeParagraph(0);

        String[] lines = code.split("\n", -1);
        int lineNum = 1;
        String lineFormat = "%3d  ";

        for (String line : lines) {
            XWPFParagraph p = cell.addParagraph();
            p.setSpacingBefore(2);
            p.setSpacingAfter(2);
            disableProofing(p);

            // Gutter
            XWPFRun lnRun = p.createRun();
            lnRun.setText(String.format(lineFormat, lineNum++));
            lnRun.setFontFamily("Consolas");
            lnRun.setFontSize(7.5);
            lnRun.setColor("8C7A99");
            disableProofing(lnRun);

            // Code
            XWPFRun cRun = p.createRun();
            cRun.setText(line);
            cRun.setFontFamily("Consolas");
            cRun.setFontSize(7.5);
            cRun.setColor("1F1424");
            disableProofing(cRun);
        }

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setSpacingBefore(30);
        spacer.setSpacingAfter(30);
    }

    private static void setCell(XWPFTable table, int rowIdx, int colIdx, String text, boolean isBold, String bgColorHex) {
        setCell(table, rowIdx, colIdx, text, isBold, bgColorHex, "000000");
    }

    private static void setCell(XWPFTable table, int rowIdx, int colIdx, String text, boolean isBold, String bgColorHex, String textColorHex) {
        XWPFTableCell cell = table.getRow(rowIdx).getCell(colIdx);
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setSpacingBefore(20);
        p.setSpacingAfter(20);

        XWPFRun run = p.createRun();
        String val = (text == null) ? "" : text;
        run.setText(val);
        run.setBold(isBold);
        run.setFontSize(9.0);
        run.setFontFamily("Segoe UI");

        if (val.contains("[type here]") || (val.startsWith("[") && val.endsWith("]"))) {
            run.setItalic(true);
            run.setColor(COLOR_PLACEHOLDER);
        } else {
            run.setColor(textColorHex != null ? textColorHex : "000000");
        }

        if (bgColorHex != null && !bgColorHex.isEmpty()) {
            cell.setColor(bgColorHex);
        }
    }

    public static void setCellField(XWPFTable table, int rowIdx, int colIdx, String promptText, boolean isBold, String bgColorHex) {
        setCell(table, rowIdx, colIdx, promptText != null ? promptText : PLACEHOLDER, isBold, bgColorHex);
    }

    public static void setTableExplicitWidths(XWPFTable table, int[] colWidthsDxa) {
        if (table == null || colWidthsDxa == null || colWidthsDxa.length == 0) return;
        int totalWidth = 0;
        for (int w : colWidthsDxa) totalWidth += w;

        try {
            CTTblPr tblPr = table.getCTTbl().getTblPr();
            if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
            CTTblWidth tblW = tblPr.getTblW();
            if (tblW == null) tblW = tblPr.addNewTblW();
            tblW.setType(STTblWidth.DXA);
            tblW.setW(BigInteger.valueOf(totalWidth));

            for (XWPFTableRow row : table.getRows()) {
                List<XWPFTableCell> cells = row.getTableCells();
                for (int i = 0; i < cells.size() && i < colWidthsDxa.length; i++) {
                    XWPFTableCell cell = cells.get(i);
                    CTTcPr tcPr = cell.getCTTc().getTcPr();
                    if (tcPr == null) tcPr = cell.getCTTc().addNewTcPr();
                    CTTblWidth tcW = tcPr.getTcW();
                    if (tcW == null) tcW = tcPr.addNewTcW();
                    tcW.setType(STTblWidth.DXA);
                    tcW.setW(BigInteger.valueOf(colWidthsDxa[i]));
                }
            }
        } catch (Exception ignored) {}
    }

    private static void setTableBorders(XWPFTable table, String borderColorHex) {
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, borderColorHex);
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, borderColorHex);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, borderColorHex);
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, borderColorHex);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, borderColorHex);
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, borderColorHex);
    }

    private static void setPageMargins(XWPFDocument doc, int top, int right, int bottom, int left) {
        try {
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            if (sectPr == null) sectPr = doc.getDocument().getBody().addNewSectPr();
            CTPageMar pageMar = sectPr.getPgMar();
            if (pageMar == null) pageMar = sectPr.addNewPgMar();

            pageMar.setTop(BigInteger.valueOf(top));
            pageMar.setRight(BigInteger.valueOf(right));
            pageMar.setBottom(BigInteger.valueOf(bottom));
            pageMar.setLeft(BigInteger.valueOf(left));
            pageMar.setHeader(BigInteger.valueOf(720));
            pageMar.setFooter(BigInteger.valueOf(720));
            pageMar.setGutter(BigInteger.ZERO);

            CTPageSz pageSz = sectPr.getPgSz();
            if (pageSz == null) pageSz = sectPr.addNewPgSz();
            pageSz.setW(BigInteger.valueOf(12240));
            pageSz.setH(BigInteger.valueOf(15840));
            pageSz.setOrient(STPageOrientation.PORTRAIT);
        } catch (Exception ignored) {}
    }

    /**
     * Initializes and registers standard Word Heading styles (Heading1, Heading2, Heading3)
     * into word/styles.xml so that Word's dynamic STYLEREF field and Navigation Pane
     * can reliably resolve running section headers without displaying:
     * "Error! Use the Home tab to apply Heading 1 to the text that you want to appear here."
     */
    public static void ensureDocumentHeadingStyles(XWPFDocument doc) {
        if (doc == null) return;
        try {
            XWPFStyles styles = (doc.getStyles() != null) ? doc.getStyles() : doc.createStyles();

            // Register Heading1 (Outline Level 0) - with both "Heading1" and "Heading 1"
            if (!styles.styleExist("Heading1")) {
                addCustomHeadingStyle(styles, "Heading1", "heading 1", 0);
            }
            if (!styles.styleExist("Heading 1")) {
                addCustomHeadingStyle(styles, "Heading 1", "Heading 1", 0);
            }

            // Register Heading2 (Outline Level 1)
            if (!styles.styleExist("Heading2")) {
                addCustomHeadingStyle(styles, "Heading2", "heading 2", 1);
            }
            if (!styles.styleExist("Heading 2")) {
                addCustomHeadingStyle(styles, "Heading 2", "Heading 2", 1);
            }

            // Register Heading3 (Outline Level 2)
            if (!styles.styleExist("Heading3")) {
                addCustomHeadingStyle(styles, "Heading3", "heading 3", 2);
            }
            if (!styles.styleExist("Heading 3")) {
                addCustomHeadingStyle(styles, "Heading 3", "Heading 3", 2);
            }
        } catch (Exception ignored) {}
    }

    private static void addCustomHeadingStyle(XWPFStyles styles, String styleId, String styleName, int outlineLevel) {
        try {
            CTStyle ctStyle = CTStyle.Factory.newInstance();
            ctStyle.setStyleId(styleId);

            CTString name = CTString.Factory.newInstance();
            name.setVal(styleName);
            ctStyle.setName(name);

            ctStyle.addNewPPr().addNewOutlineLvl().setVal(BigInteger.valueOf(outlineLevel));

            ctStyle.addNewQFormat();

            XWPFStyle xwpfStyle = new XWPFStyle(ctStyle);
            xwpfStyle.setType(STStyleType.PARAGRAPH);
            styles.addStyle(xwpfStyle);
        } catch (Exception ignored) {}
    }

    private static void setupDocumentHeaderFooter(XWPFDocument doc, String docTitle, String docId, String schema) {
        try {
            ensureDocumentHeadingStyles(doc);
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            if (sectPr == null) sectPr = doc.getDocument().getBody().addNewSectPr();
            sectPr.addNewTitlePg();

            // Cover page: clean empty header/footer
            XWPFHeader firstHeader = doc.createHeader(HeaderFooterType.FIRST);
            firstHeader.createParagraph();
            XWPFFooter firstFooter = doc.createFooter(HeaderFooterType.FIRST);
            firstFooter.createParagraph();

            // Pages 2+: Running Header (Left: Document Title, Right: Dynamic Section Name STYLEREF)
            XWPFHeader defaultHeader = doc.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph hPara = defaultHeader.createParagraph();
            hPara.setAlignment(ParagraphAlignment.LEFT);
            hPara.setSpacingBefore(0);
            hPara.setSpacingAfter(40);
            hPara.setBorderBottom(Borders.SINGLE);

            CTP ctpHead = hPara.getCTP();
            CTPPr pPrHead = ctpHead.getPPr() != null ? ctpHead.getPPr() : ctpHead.addNewPPr();

            CTTabs tabsHead = pPrHead.getTabs() != null ? pPrHead.getTabs() : pPrHead.addNewTabs();
            CTTabStop rightStopHead = tabsHead.addNewTab();
            rightStopHead.setVal(STTabJc.RIGHT);
            rightStopHead.setPos(BigInteger.valueOf(10800));

            XWPFRun hRun = hPara.createRun();
            hRun.setText(docTitle + " Technical Specification");
            hRun.setFontFamily("Segoe UI");
            hRun.setFontSize(8.5);
            hRun.setBold(true);
            hRun.setColor(COLOR_MUTED);

            XWPFRun hTabRight = hPara.createRun();
            hTabRight.addTab();

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField fldStyleRef = ctpHead.addNewFldSimple();
            fldStyleRef.setInstr("STYLEREF \"Heading 1\" \\* MERGEFORMAT");

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR rStyleRef = fldStyleRef.addNewR();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPrStyle = rStyleRef.addNewRPr();

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts fontsStyle = rPrStyle.addNewRFonts();
            fontsStyle.setAscii("Segoe UI");
            fontsStyle.setHAnsi("Segoe UI");

            rPrStyle.addNewSz().setVal(BigInteger.valueOf(17)); // 8.5 pt
            rPrStyle.addNewB(); // Bold
            rPrStyle.addNewColor().setVal("5C1D48"); // Enterprise Royal Plum

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText txtStyle = rStyleRef.addNewT();
            txtStyle.setStringValue("Procedure Overview & Visual Architecture");

            // Pages 2+: Running Footer
            XWPFFooter defaultFooter = doc.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph fPara = defaultFooter.createParagraph();
            fPara.setAlignment(ParagraphAlignment.LEFT);
            fPara.setSpacingBefore(40);
            fPara.setSpacingAfter(0);
            fPara.setBorderTop(Borders.SINGLE);

            CTP ctp = fPara.getCTP();
            CTPPr pPr = ctp.getPPr() != null ? ctp.getPPr() : ctp.addNewPPr();

            CTTabs tabs = pPr.getTabs() != null ? pPr.getTabs() : pPr.addNewTabs();
            CTTabStop rightStop = tabs.addNewTab();
            rightStop.setVal(STTabJc.RIGHT);
            rightStop.setPos(BigInteger.valueOf(10800));

            XWPFRun fLeft = fPara.createRun();
            fLeft.setText("FL Lottery IR-SDS | " + docId);
            fLeft.setFontFamily("Segoe UI");
            fLeft.setFontSize(8.5);
            fLeft.setColor("888888");
            fLeft.setItalic(true);

            XWPFRun fTabRight = fPara.createRun();
            fTabRight.addTab();

            XWPFRun fRight1 = fPara.createRun();
            fRight1.setText("Page ");
            fRight1.setFontFamily("Segoe UI");
            fRight1.setFontSize(8.5);
            fRight1.setColor("555555");

            CTR ctr1 = fPara.getCTP().addNewR();
            ctr1.addNewRPr().addNewRFonts().setAscii("Segoe UI");
            ctr1.addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            CTR ctr2 = fPara.getCTP().addNewR();
            ctr2.addNewInstrText().setStringValue("PAGE");
            CTR ctr3 = fPara.getCTP().addNewR();
            ctr3.addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            CTR ctr4 = fPara.getCTP().addNewR();
            ctr4.addNewFldChar().setFldCharType(STFldCharType.END);

            XWPFRun fRight2 = fPara.createRun();
            fRight2.setText(" of ");
            fRight2.setFontFamily("Segoe UI");
            fRight2.setFontSize(8.5);
            fRight2.setColor("555555");

            CTR ctr5 = fPara.getCTP().addNewR();
            ctr5.addNewRPr().addNewRFonts().setAscii("Segoe UI");
            ctr5.addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            CTR ctr6 = fPara.getCTP().addNewR();
            ctr6.addNewInstrText().setStringValue("NUMPAGES");
            CTR ctr7 = fPara.getCTP().addNewR();
            ctr7.addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            CTR ctr8 = fPara.getCTP().addNewR();
            ctr8.addNewFldChar().setFldCharType(STFldCharType.END);

        } catch (Exception ignored) {}
    }

    private static void disableProofing(XWPFParagraph p) {
        if (p == null) return;
        try {
            CTP ctp = p.getCTP();
            if (ctp == null) return;
            org.w3c.dom.Node pNode = ctp.getDomNode();
            if (pNode == null) return;
            org.w3c.dom.Document ownerDoc = pNode.getOwnerDocument();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            org.w3c.dom.Node pPrNode = null;
            org.w3c.dom.NodeList children = pNode.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if ("pPr".equals(child.getLocalName()) || "w:pPr".equals(child.getNodeName())) {
                    pPrNode = child;
                    break;
                }
            }
            if (pPrNode == null) {
                pPrNode = ownerDoc.createElementNS(ns, "w:pPr");
                pNode.insertBefore(pPrNode, pNode.getFirstChild());
            }
            org.w3c.dom.Node rPrNode = null;
            org.w3c.dom.NodeList pPrChildren = pPrNode.getChildNodes();
            for (int i = 0; i < pPrChildren.getLength(); i++) {
                org.w3c.dom.Node child = pPrChildren.item(i);
                if ("rPr".equals(child.getLocalName()) || "w:rPr".equals(child.getNodeName())) {
                    rPrNode = child;
                    break;
                }
            }
            if (rPrNode == null) {
                rPrNode = ownerDoc.createElementNS(ns, "w:rPr");
                pPrNode.appendChild(rPrNode);
            }
            boolean hasNoProof = false;
            org.w3c.dom.NodeList rPrChildren = rPrNode.getChildNodes();
            for (int i = 0; i < rPrChildren.getLength(); i++) {
                org.w3c.dom.Node child = rPrChildren.item(i);
                if ("noProof".equals(child.getLocalName()) || "w:noProof".equals(child.getNodeName())) {
                    hasNoProof = true;
                    break;
                }
            }
            if (!hasNoProof) {
                org.w3c.dom.Element noProofElem = ownerDoc.createElementNS(ns, "w:noProof");
                rPrNode.appendChild(noProofElem);
            }
        } catch (Exception ignored) {}
    }

    private static void disableProofing(XWPFRun run) {
        if (run == null) return;
        try {
            CTR ctr = run.getCTR();
            if (ctr == null) return;
            org.w3c.dom.Node rNode = ctr.getDomNode();
            if (rNode == null) return;
            org.w3c.dom.Document ownerDoc = rNode.getOwnerDocument();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            org.w3c.dom.Node rPrNode = null;
            org.w3c.dom.NodeList children = rNode.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if ("rPr".equals(child.getLocalName()) || "w:rPr".equals(child.getNodeName())) {
                    rPrNode = child;
                    break;
                }
            }
            if (rPrNode == null) {
                rPrNode = ownerDoc.createElementNS(ns, "w:rPr");
                rNode.insertBefore(rPrNode, rNode.getFirstChild());
            }
            boolean hasNoProof = false;
            org.w3c.dom.NodeList rPrChildren = rPrNode.getChildNodes();
            for (int i = 0; i < rPrChildren.getLength(); i++) {
                org.w3c.dom.Node child = rPrChildren.item(i);
                if ("noProof".equals(child.getLocalName()) || "w:noProof".equals(child.getNodeName())) {
                    hasNoProof = true;
                    break;
                }
            }
            if (!hasNoProof) {
                org.w3c.dom.Element noProofElem = ownerDoc.createElementNS(ns, "w:noProof");
                rPrNode.appendChild(noProofElem);
            }
        } catch (Exception ignored) {}
    }
}
