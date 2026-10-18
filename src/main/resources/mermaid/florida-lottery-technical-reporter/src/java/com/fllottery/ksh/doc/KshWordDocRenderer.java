package com.fllottery.ksh.doc;

import com.fllottery.ksh.bridge.KshToDbCrossCatalogBridge;
import com.fllottery.ksh.lineage.KshLineageDiagramHelper;
import com.fllottery.ksh.model.KornShellScriptDocModel;
import com.fllottery.ksh.model.KshAiResearchDoc;
import com.fllottery.ksh.model.ScriptReferenceDoc;
import com.fllottery.ksh.model.ScriptReferenceType;
import com.fllottery.ksh.research.AiStudioKshResearcherService;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Enterprise Apache POI Word (.docx) Technical Specification Generator.
 *
 * <p>Produces formal, publication-ready technical specification documents for
 * Florida Lottery batch systems, including:
 * <ul>
 *   <li>Executive header and metadata cards</li>
 *   <li>AI Studio Executive User Stories &amp; Business Narrative</li>
 *   <li>IWS scheduling alignment and operational SLA window</li>
 *   <li>Embedded high-resolution Retina Java2D vector lineage diagram</li>
 *   <li>Deep Cross-Catalog Oracle DbCache &amp; Table Architecture Matrix</li>
 *   <li>Mined external reference inventory table</li>
 *   <li>AI Operational Failure &amp; IWS Rerun Runbook</li>
 *   <li>Java 21 / Spring Boot 3.4.0 Modernization Migration Blueprint</li>
 *   <li>Formatted Korn Shell source code listing with line numbers</li>
 *   <li>Audit &amp; operational compliance footer</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.2.0
 */
public class KshWordDocRenderer {

    private static final Logger log = LoggerFactory.getLogger(KshWordDocRenderer.class);

    private static final String HEX_ROYAL_PLUM = "5C1D48";
    private static final String HEX_DEEP_NAVY = "002B49";
    private static final String HEX_FOREST_GREEN = "16A34A";
    private static final String HEX_LIGHT_BG = "F8FAFC";
    private static final String HEX_PLUM_LIGHT = "F5EEF8";
    private static final String HEX_BORDER = "CBD5E1";

    private final KshLineageDiagramHelper diagramHelper = new KshLineageDiagramHelper();
    private final KshToDbCrossCatalogBridge crossCatalogBridge = new KshToDbCrossCatalogBridge();
    private final AiStudioKshResearcherService aiResearcher = new AiStudioKshResearcherService();

    /**
     * Generates a complete .docx technical specification document as a byte array.
     *
     * @param script the analyzed Korn Shell script doc model
     * @return raw byte array of the .docx file
     */
    public byte[] generateDocumentBytes(KornShellScriptDocModel script) {
        if (script == null) {
            byte[] emptyResult = new byte[0];
            return emptyResult;
        }

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Configure Page Margins (0.75 inch = 1080 twips)
            CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
            CTPageMar pageMar = sectPr.addNewPgMar();
            pageMar.setLeft(BigInteger.valueOf(1080));
            pageMar.setRight(BigInteger.valueOf(1080));
            pageMar.setTop(BigInteger.valueOf(1080));
            pageMar.setBottom(BigInteger.valueOf(1080));

            // Ensure Cross-Catalog and AI Research are populated
            KshToDbCrossCatalogBridge.CrossCatalogResolutionResult bridgeResult = crossCatalogBridge.bridgeScriptToDatabase(script);
            if (script.getAiResearch() == null) {
                aiResearcher.researchScript(script, bridgeResult);
            }

            // 1. Executive Document Title & Subtitle
            addDocumentHeader(doc, script);

            // 2. Executive Metadata Matrix Table
            addMetadataTable(doc, script);

            // 3. AI Business Story, Purpose & Chronological Workflow Section
            addPurposeAndAiStorySection(doc, script);

            // 4. Lineage Flow & Architecture Diagram Section
            addLineageDiagramSectionDirect(doc, script);

            // 5. Cross-Catalog Oracle DbCache & Table Architecture Matrix
            addCrossCatalogDbSectionDirect(doc, script);

            // 6. Mined External Reference Inventory Matrix
            addReferenceInventorySectionDirect(doc, script);

            // 7. Operational Incident Runbook & Modernization Blueprint
            addRunbookAndModernizationSection(doc, script);

            // 8. Korn Shell Code Listing with Line Numbers
            addSourceCodeSectionDirect(doc, script);

            // 9. Audit & Compliance Governance Section
            addAuditSection(doc);

            doc.write(baos);
            byte[] result = baos.toByteArray();
            return result;

        } catch (Exception ex) {
            log.error("Failed to generate Word document for script: " + script.getFilename(), ex);
            byte[] errorResult = new byte[0];
            return errorResult;
        }
    }

    /**
     * Writes the generated document directly to a target filesystem path.
     */
    public File generateDocumentToFile(KornShellScriptDocModel script, File destinationFile) {
        byte[] docBytes = generateDocumentBytes(script);
        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
            fos.write(docBytes);
        } catch (Exception ex) {
            log.error("Failed to write Word document to file: " + destinationFile.getAbsolutePath(), ex);
        }
        File result = destinationFile;
        return result;
    }

    private void addDocumentHeader(XWPFDocument doc, KornShellScriptDocModel script) {
        XWPFParagraph headerPara = doc.createParagraph();
        headerPara.setSpacingBefore(100);
        headerPara.setSpacingAfter(60);

        XWPFRun orgRun = headerPara.createRun();
        orgRun.setText("STATE OF FLORIDA LOTTERY | IT APPLICATION DEVELOPMENT & ARCHITECTURE");
        orgRun.setFontFamily("Segoe UI");
        orgRun.setFontSize(9);
        orgRun.setBold(true);
        orgRun.setColor(HEX_DEEP_NAVY);

        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setSpacingBefore(40);
        titlePara.setSpacingAfter(100);

        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText("Technical Specification: " + script.getBaseFilename());
        titleRun.setFontFamily("Segoe UI");
        titleRun.setFontSize(22);
        titleRun.setBold(true);
        titleRun.setColor(HEX_ROYAL_PLUM);

        XWPFParagraph subPara = doc.createParagraph();
        subPara.setSpacingAfter(200);
        XWPFRun subRun = subPara.createRun();
        subRun.setText("IWS Batch Job Lineage, AI-Generated User Story, and Cross-Catalog Oracle DbCache Architecture");
        subRun.setFontFamily("Segoe UI");
        subRun.setFontSize(11);
        subRun.setItalic(true);
        subRun.setColor("64748B");
    }

    private void addMetadataTable(XWPFDocument doc, KornShellScriptDocModel script) {
        XWPFTable table = doc.createTable(5, 4);
        table.setWidth("100%");

        String[][] data = {
                {"Job Name", script.getJobName() != null ? script.getJobName() : "N/A", "Job Stream", script.getJobStreamName() != null ? script.getJobStreamName() : "ARS"},
                {"Job Set", script.getJobSet() != null ? script.getJobSet() : "Core", "Author", script.getAuthor() != null ? script.getAuthor() : "FL Lottery Architecture"},
                {"Created Date", script.getCreationDate() != null ? script.getCreationDate() : "N/A", "Lines of Code", String.valueOf(script.getLineCount())},
                {"Complexity Level", script.getComplexityLevel() + " (" + script.getComplexityScore() + ")", "References Mined", String.valueOf(script.getScriptReferences().size())},
                {"OCI Absolute Path", script.getAbsolutePath() != null ? script.getAbsolutePath() : script.getFilename(), "File Number", script.getFileNumber() != null ? script.getFileNumber() : "1"}
        };

        for (int r = 0; r < data.length; r++) {
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < 4; c++) {
                XWPFTableCell cell = row.getCell(c);
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                XWPFParagraph p = cell.getParagraphs().get(0);
                p.setSpacingBefore(40);
                p.setSpacingAfter(40);
                XWPFRun run = p.createRun();
                run.setFontFamily("Segoe UI");
                run.setFontSize(9);

                boolean isHeaderCol = (c == 0 || c == 2);
                if (isHeaderCol) {
                    run.setBold(true);
                    run.setColor(HEX_DEEP_NAVY);
                    cell.setColor("F1F5F9");
                } else {
                    run.setColor("1E293B");
                    cell.setColor("FFFFFF");
                }
                run.setText(data[r][c]);
            }
        }

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setSpacingAfter(160);
    }

    private void addPurposeAndAiStorySection(XWPFDocument doc, KornShellScriptDocModel script) {
        addSectionHeading(doc, "1. Executive Summary & AI Business Story");

        KshAiResearchDoc ai = script.getAiResearch();

        // 1. Executive Summary
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(120);
        XWPFRun r = p.createRun();
        r.setFontFamily("Segoe UI");
        r.setFontSize(10.5);
        r.setColor("334155");

        if (ai != null && !ai.getExecutiveSummary().isEmpty()) {
            r.setText(ai.getExecutiveSummary());
        } else {
            String purpose = script.getPurpose();
            r.setText(purpose != null && !purpose.isBlank() ? purpose :
                    "Batch orchestration script deployed to OCI Linux servers. Invoked unattended via IBM Workload Scheduler (IWS).");
        }

        // 2. Agile User Story Callout Box
        if (ai != null && !ai.getUserStory().isEmpty()) {
            XWPFTable callout = doc.createTable(1, 1);
            callout.setWidth("100%");
            XWPFTableCell cell = callout.getRow(0).getCell(0);
            cell.setColor(HEX_PLUM_LIGHT);

            XWPFParagraph cp = cell.getParagraphs().get(0);
            cp.setSpacingBefore(60);
            cp.setSpacingAfter(60);

            XWPFRun label = cp.createRun();
            label.setFontFamily("Segoe UI");
            label.setFontSize(10);
            label.setBold(true);
            label.setColor(HEX_ROYAL_PLUM);
            label.setText("Agile Business Story:\n");

            XWPFRun storyRun = cp.createRun();
            storyRun.setFontFamily("Segoe UI");
            storyRun.setFontSize(10);
            storyRun.setItalic(true);
            storyRun.setColor("1E293B");
            storyRun.setText("\"" + ai.getUserStory() + "\"");

            XWPFParagraph spacerBox = doc.createParagraph();
            spacerBox.setSpacingAfter(120);
        }

        // 3. Chronological Workflow Steps
        if (ai != null && !ai.getChronologicalWorkflow().isEmpty()) {
            XWPFParagraph wfHeader = doc.createParagraph();
            wfHeader.setSpacingBefore(60);
            wfHeader.setSpacingAfter(40);
            XWPFRun wfRun = wfHeader.createRun();
            wfRun.setFontFamily("Segoe UI");
            wfRun.setFontSize(11);
            wfRun.setBold(true);
            wfRun.setColor(HEX_DEEP_NAVY);
            wfRun.setText("Chronological Execution Workflow (\"WHAT\" It Does):");

            for (String step : ai.getChronologicalWorkflow()) {
                XWPFParagraph stepPara = doc.createParagraph();
                stepPara.setSpacingBefore(20);
                stepPara.setSpacingAfter(20);
                XWPFRun sRun = stepPara.createRun();
                sRun.setFontFamily("Segoe UI");
                sRun.setFontSize(9.5);
                sRun.setColor("334155");
                sRun.setText("  •  " + step);
            }

            XWPFParagraph spacerWf = doc.createParagraph();
            spacerWf.setSpacingAfter(140);
        }
    }

    public void addLineageDiagramSectionDirect(XWPFDocument doc, KornShellScriptDocModel script) {
        addSectionHeading(doc, "2. End-to-End Batch Lineage Architecture");

        XWPFParagraph descPara = doc.createParagraph();
        descPara.setSpacingAfter(100);
        XWPFRun descRun = descPara.createRun();
        descRun.setFontFamily("Segoe UI");
        descRun.setFontSize(10);
        descRun.setItalic(true);
        descRun.setText("High-resolution multi-tier lineage rendering IWS scheduling triggers down to Oracle packages, relational tables, and filesystem artifacts:");

        try {
            byte[] imageBytes = diagramHelper.renderRetinaPngByteArray(script, 3.5);

            if (imageBytes != null && imageBytes.length > 0) {
                XWPFParagraph imgPara = doc.createParagraph();
                imgPara.setAlignment(ParagraphAlignment.CENTER);
                imgPara.setSpacingAfter(180);

                XWPFRun imgRun = imgPara.createRun();
                try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
                    imgRun.addPicture(
                            bais,
                            Document.PICTURE_TYPE_PNG,
                            script.getBaseFilename() + "_lineage.png",
                            Units.toEMU(550),
                            Units.toEMU(252)
                    );
                }
            }
        } catch (Exception e) {
            log.error("Could not insert lineage diagram image into Word document", e);
        }
    }

    public void addCrossCatalogDbSectionDirect(XWPFDocument doc, KornShellScriptDocModel script) {
        addSectionHeading(doc, "3. Cross-Catalog Oracle DbCache & Table Architecture");

        KshToDbCrossCatalogBridge.CrossCatalogResolutionResult bridgeResult = crossCatalogBridge.bridgeScriptToDatabase(script);

        XWPFParagraph introPara = doc.createParagraph();
        introPara.setSpacingAfter(100);
        XWPFRun introRun = introPara.createRun();
        introRun.setFontFamily("Segoe UI");
        introRun.setFontSize(10);
        introRun.setColor("334155");
        introRun.setText("Authoritative Oracle database routines and underlying relational tables resolved from the unified Florida Lottery DbCache catalog:");

        // 1. Resolved Routines Table
        List<KshToDbCrossCatalogBridge.ResolvedProcedureDependency> routines = bridgeResult.getRoutines();
        if (!routines.isEmpty()) {
            XWPFParagraph rSubPara = doc.createParagraph();
            rSubPara.setSpacingBefore(60);
            rSubPara.setSpacingAfter(40);
            XWPFRun rSubRun = rSubPara.createRun();
            rSubRun.setText("Target Oracle Stored Procedures & Drivers:");
            rSubRun.setFontFamily("Segoe UI");
            rSubRun.setFontSize(11);
            rSubRun.setBold(true);
            rSubRun.setColor(HEX_ROYAL_PLUM);

            XWPFTable rTable = doc.createTable(routines.size() + 1, 4);
            rTable.setWidth("100%");

            XWPFTableRow header = rTable.getRow(0);
            String[] rHeaders = {"Procedure / Package", "Schema", "Object Type", "DbCache Status & Details"};
            for (int i = 0; i < 4; i++) {
                XWPFTableCell cell = header.getCell(i);
                cell.setColor(HEX_DEEP_NAVY);
                XWPFParagraph p = cell.getParagraphs().get(0);
                p.setSpacingBefore(40);
                p.setSpacingAfter(40);
                XWPFRun r = p.createRun();
                r.setText(rHeaders[i]);
                r.setFontFamily("Segoe UI");
                r.setFontSize(9.5);
                r.setBold(true);
                r.setColor("FFFFFF");
            }

            for (int i = 0; i < routines.size(); i++) {
                KshToDbCrossCatalogBridge.ResolvedProcedureDependency routine = routines.get(i);
                XWPFTableRow row = rTable.getRow(i + 1);
                String bg = (i % 2 == 0) ? "FFFFFF" : HEX_LIGHT_BG;

                setTableCell(row.getCell(0), routine.getCanonicalName(), bg, true, HEX_ROYAL_PLUM);
                setTableCell(row.getCell(1), routine.getSchema(), bg, false, "002B49");
                setTableCell(row.getCell(2), routine.getObjectType(), bg, false, "475569");
                setTableCell(row.getCell(3), routine.isFoundInCatalog() ? "VERIFIED in DbCache: " + routine.getDescription() : "External Driver: " + routine.getDescription(), bg, false, "1E293B");
            }

            XWPFParagraph spacerR = doc.createParagraph();
            spacerR.setSpacingAfter(120);
        }

        // 2. Resolved Physical Tables Table
        List<KshToDbCrossCatalogBridge.ResolvedTableDependency> tables = bridgeResult.getTables();
        if (!tables.isEmpty()) {
            XWPFParagraph tSubPara = doc.createParagraph();
            tSubPara.setSpacingBefore(60);
            tSubPara.setSpacingAfter(40);
            XWPFRun tSubRun = tSubPara.createRun();
            tSubRun.setText("Discovered Physical Relational Database Tables:");
            tSubRun.setFontFamily("Segoe UI");
            tSubRun.setFontSize(11);
            tSubRun.setBold(true);
            tSubRun.setColor(HEX_FOREST_GREEN);

            XWPFTable tTable = doc.createTable(tables.size() + 1, 5);
            tTable.setWidth("100%");

            XWPFTableRow header = tTable.getRow(0);
            String[] tHeaders = {"Table Name", "Schema", "Access Mode", "Est. Rows", "Primary Key & Provenance"};
            for (int i = 0; i < 5; i++) {
                XWPFTableCell cell = header.getCell(i);
                cell.setColor(HEX_FOREST_GREEN);
                XWPFParagraph p = cell.getParagraphs().get(0);
                p.setSpacingBefore(40);
                p.setSpacingAfter(40);
                XWPFRun r = p.createRun();
                r.setText(tHeaders[i]);
                r.setFontFamily("Segoe UI");
                r.setFontSize(9.5);
                r.setBold(true);
                r.setColor("FFFFFF");
            }

            for (int i = 0; i < tables.size(); i++) {
                KshToDbCrossCatalogBridge.ResolvedTableDependency tbl = tables.get(i);
                XWPFTableRow row = tTable.getRow(i + 1);
                String bg = (i % 2 == 0) ? "FFFFFF" : HEX_LIGHT_BG;

                String rowStr = tbl.getRowCount() >= 0 ? String.format("%,d", tbl.getRowCount()) : "Active OLTP";
                String pkStr = !tbl.getPrimaryKeys().isEmpty() ? String.join(", ", tbl.getPrimaryKeys()) : "Referential Key";

                setTableCell(row.getCell(0), tbl.getTableName(), bg, true, "0F766E");
                setTableCell(row.getCell(1), tbl.getSchema(), bg, false, "002B49");
                setTableCell(row.getCell(2), tbl.getAccessMode(), bg, false, "B91C1C");
                setTableCell(row.getCell(3), rowStr, bg, false, "475569");
                setTableCell(row.getCell(4), "PK: [" + pkStr + "] (" + tbl.getProvenance() + ")", bg, false, "1E293B");
            }

            XWPFParagraph spacerT = doc.createParagraph();
            spacerT.setSpacingAfter(160);
        }
    }

    public void addReferenceInventorySectionDirect(XWPFDocument doc, KornShellScriptDocModel script) {
        addSectionHeading(doc, "4. Mined Dependency & External Reference Inventory");

        List<ScriptReferenceDoc> refs = script.getScriptReferences();
        if (refs == null || refs.isEmpty()) {
            XWPFParagraph nonePara = doc.createParagraph();
            XWPFRun noneRun = nonePara.createRun();
            noneRun.setText("No external dependencies or references were detected during static analysis.");
            return;
        }

        XWPFTable table = doc.createTable(refs.size() + 1, 5);
        table.setWidth("100%");

        XWPFTableRow headerRow = table.getRow(0);
        String[] headers = {"Type", "Target / Dependency", "Line #", "Direction", "Classification Notes"};

        for (int i = 0; i < 5; i++) {
            XWPFTableCell cell = headerRow.getCell(i);
            cell.setColor(HEX_ROYAL_PLUM);
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            XWPFParagraph p = cell.getParagraphs().get(0);
            p.setSpacingBefore(50);
            p.setSpacingAfter(50);
            XWPFRun r = p.createRun();
            r.setText(headers[i]);
            r.setFontFamily("Segoe UI");
            r.setFontSize(9.5);
            r.setBold(true);
            r.setColor("FFFFFF");
        }

        for (int i = 0; i < refs.size(); i++) {
            ScriptReferenceDoc ref = refs.get(i);
            XWPFTableRow row = table.getRow(i + 1);

            String bgColor = (i % 2 == 0) ? "FFFFFF" : HEX_LIGHT_BG;

            setTableCell(row.getCell(0), ref.getType() != null ? ref.getType().getDisplayName() : "Unknown", bgColor, true, "1E293B");
            setTableCell(row.getCell(1), ref.getTarget() != null ? ref.getTarget() : "N/A", bgColor, false, "002B49");
            setTableCell(row.getCell(2), String.valueOf(ref.getLineNumber()), bgColor, false, "64748B");
            setTableCell(row.getCell(3), ref.getDirection() != null ? ref.getDirection() : "OUTGOING", bgColor, false, "334155");
            setTableCell(row.getCell(4), ref.getNotes() != null ? ref.getNotes() : "-", bgColor, false, "475569");
        }

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setSpacingAfter(180);
    }

    private void addRunbookAndModernizationSection(XWPFDocument doc, KornShellScriptDocModel script) {
        addSectionHeading(doc, "5. Operational Failure Runbook & Java Modernization");

        KshAiResearchDoc ai = script.getAiResearch();
        if (ai == null) {
            return;
        }

        // 1. Operational Failure Runbook
        if (!ai.getOperationalFailureRunbook().isEmpty()) {
            XWPFParagraph rbTitle = doc.createParagraph();
            rbTitle.setSpacingBefore(40);
            rbTitle.setSpacingAfter(30);
            XWPFRun rbRun = rbTitle.createRun();
            rbRun.setFontFamily("Segoe UI");
            rbRun.setFontSize(11);
            rbRun.setBold(true);
            rbRun.setColor("B91C1C"); // Alert red
            rbRun.setText("Operational Incident Remediation Protocol:");

            XWPFParagraph rbText = doc.createParagraph();
            rbText.setSpacingAfter(100);
            XWPFRun tRun = rbText.createRun();
            tRun.setFontFamily("Segoe UI");
            tRun.setFontSize(9.5);
            tRun.setColor("334155");
            tRun.setText(ai.getOperationalFailureRunbook());
        }

        // 2. Idempotency & Rerun Safety
        if (!ai.getIdempotencyAndRerunSafety().isEmpty()) {
            XWPFParagraph idemPara = doc.createParagraph();
            idemPara.setSpacingAfter(100);
            XWPFRun iLabel = idemPara.createRun();
            iLabel.setFontFamily("Segoe UI");
            iLabel.setFontSize(10);
            iLabel.setBold(true);
            iLabel.setColor(HEX_FOREST_GREEN);
            iLabel.setText("IWS Rerun Safety: ");

            XWPFRun iText = idemPara.createRun();
            iText.setFontFamily("Segoe UI");
            iText.setFontSize(9.5);
            iText.setColor("1E293B");
            iText.setText(ai.getIdempotencyAndRerunSafety());
        }

        // 3. Java 21 / Spring Boot Migration Blueprint
        if (!ai.getJavaMigrationBlueprint().isEmpty()) {
            XWPFParagraph migTitle = doc.createParagraph();
            migTitle.setSpacingBefore(60);
            migTitle.setSpacingAfter(30);
            XWPFRun mRun = migTitle.createRun();
            mRun.setFontFamily("Segoe UI");
            mRun.setFontSize(11);
            mRun.setBold(true);
            mRun.setColor(HEX_ROYAL_PLUM);
            mRun.setText("Java 21 / Spring Boot 3.4.0 AbstractJob Migration Blueprint:");

            XWPFParagraph migText = doc.createParagraph();
            migText.setSpacingAfter(140);
            XWPFRun mtRun = migText.createRun();
            mtRun.setFontFamily("Consolas");
            mtRun.setFontSize(8.5);
            mtRun.setColor("1E293B");
            mtRun.setText(ai.getJavaMigrationBlueprint());
        }
    }

    public void addSourceCodeSectionDirect(XWPFDocument doc, KornShellScriptDocModel script) {
        addSectionHeading(doc, "6. Korn Shell (.ksh) Source Code Listing");

        String code = script.getCode();
        if (code == null || code.isBlank()) {
            return;
        }

        String[] lines = code.split("\\r?\\n");
        XWPFTable codeTable = doc.createTable(1, 1);
        codeTable.setWidth("100%");
        XWPFTableCell cell = codeTable.getRow(0).getCell(0);
        cell.setColor("F1F5F9");

        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);

        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = p.createRun();
            run.setFontFamily("Consolas");
            run.setFontSize(8.5);

            String linePrefix = String.format("%3d |  ", i + 1);
            run.setColor("94A3B8");
            run.setText(linePrefix);

            XWPFRun codeRun = p.createRun();
            codeRun.setFontFamily("Consolas");
            codeRun.setFontSize(8.5);

            String lineText = lines[i];
            if (lineText.trim().startsWith("#")) {
                codeRun.setColor("16A34A");
                codeRun.setItalic(true);
            } else if (lineText.contains("runOraclePKG") || lineText.contains("sqlplus")) {
                codeRun.setColor(HEX_ROYAL_PLUM);
                codeRun.setBold(true);
            } else {
                codeRun.setColor("1E293B");
            }
            codeRun.setText(lineText);
            codeRun.addBreak();
        }

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setSpacingAfter(160);
    }

    private void addAuditSection(XWPFDocument doc) {
        addSectionHeading(doc, "7. Governance & Lineage Metadata");

        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setFontFamily("Segoe UI");
        r.setFontSize(9);
        r.setColor("64748B");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        r.setText("Generated automatically by the Florida Lottery Korn Shell Research & Static Analysis Engine.\n" +
                "Governance Tier: etldb | Master Catalog Engine v1.2.0 | Generated at: " + timestamp + "\n" +
                "Audited and approved for enterprise batch migration into the Java 21 / Spring Boot 3.4.0 framework.");
    }

    private void addSectionHeading(XWPFDocument doc, String title) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(180);
        p.setSpacingAfter(80);
        XWPFRun r = p.createRun();
        r.setText(title);
        r.setFontFamily("Segoe UI");
        r.setFontSize(14);
        r.setBold(true);
        r.setColor(HEX_ROYAL_PLUM);
    }

    private void setTableCell(XWPFTableCell cell, String text, String bgColor, boolean bold, String textColor) {
        cell.setColor(bgColor);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setSpacingBefore(30);
        p.setSpacingAfter(30);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("Segoe UI");
        r.setFontSize(9);
        r.setBold(bold);
        r.setColor(textColor);
    }
}
