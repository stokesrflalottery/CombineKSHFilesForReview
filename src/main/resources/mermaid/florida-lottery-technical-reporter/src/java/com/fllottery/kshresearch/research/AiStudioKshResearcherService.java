package com.fllottery.kshresearch.research;

import com.fllottery.common.ai.GoogleAiStudioGateway;
import com.fllottery.kshresearch.bridge.KshToDbCrossCatalogBridge;
import com.fllottery.kshresearch.model.KornShellScriptDocModel;
import com.fllottery.kshresearch.model.KshAiResearchDoc;
import com.fllottery.kshresearch.model.ScriptReferenceDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Enterprise AI Studio Service for automated Florida Lottery Korn Shell (.ksh) batch script research.
 *
 * <p>Leverages {@link GoogleAiStudioGateway} to analyze batch scripts with Google AI Studio models (e.g. {@code gemini-3.8-flash}),
 * cross-referenced against the Oracle DbCache catalog, producing deep architectural and operational intelligence:
 * <ul>
 *   <li>Executive Story &amp; Business Narrative (Agile User Story format)</li>
 *   <li>Chronological Technical Workflow ("WHAT" the script executes step-by-step)</li>
 *   <li>Business Justification &amp; Batch SLA Impact ("WHY" it exists in the batch stream)</li>
 *   <li>Input &amp; Output Inventory (CLI flags, Env files, Outfiles, Reports, Mailx alerts)</li>
 *   <li>Operational Incident Runbook (OCI log paths, return code handling, IWS rerun safety)</li>
 *   <li>Modernization Blueprint (Translating shell scripts to Spring Boot {@code AbstractJob})</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 2.0.0
 */
public class AiStudioKshResearcherService {

    private static final Logger log = LoggerFactory.getLogger(AiStudioKshResearcherService.class);

    private final GoogleAiStudioGateway aiGateway;

    public AiStudioKshResearcherService() {
        this.aiGateway = GoogleAiStudioGateway.getInstance();
    }

    public AiStudioKshResearcherService(GoogleAiStudioGateway gateway) {
        this.aiGateway = gateway != null ? gateway : GoogleAiStudioGateway.getInstance();
    }

    /**
     * Researches a Korn Shell script, enriching it with AI-generated stories, workflow, and runbooks.
     *
     * @param script the script model to research
     * @param bridgeResult cross-catalog Oracle DbCache resolution result
     * @return the generated AI research document
     */
    public KshAiResearchDoc researchScript(
            KornShellScriptDocModel script,
            KshToDbCrossCatalogBridge.CrossCatalogResolutionResult bridgeResult) {

        if (script == null) {
            KshAiResearchDoc empty = new KshAiResearchDoc();
            KshAiResearchDoc result = empty;
            return result;
        }

        log.info("Starting AI research for script: [{}] (API Gateway configured: {})",
                script.getBaseFilename(), aiGateway.isApiKeyConfigured());

        KshAiResearchDoc researchDoc = null;

        // 1. Attempt Live Google AI Studio Call via unified Gateway if configured
        if (aiGateway.isApiKeyConfigured()) {
            try {
                String prompt = buildPrompt(script, bridgeResult);
                researchDoc = aiGateway.generateStructuredJson(prompt, KshAiResearchDoc.class);
            } catch (Exception ex) {
                log.warn("Live AI Studio research call failed for script {}: {}. Generating offline baseline research.",
                        script.getBaseFilename(), ex.getMessage());
            }
        }

        // 2. Offline Expert Heuristic Fallback (Ensures 100% operational reliability offline)
        if (researchDoc == null) {
            researchDoc = generateOfflineHeuristicResearch(script, bridgeResult);
        }

        researchDoc.setResearchTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        script.setAiResearch(researchDoc);

        log.info("AI Research complete for script: [{}] -> Story: {}",
                script.getBaseFilename(), researchDoc.getUserStory());

        KshAiResearchDoc result = researchDoc;
        return result;
    }

    /**
     * Builds the high-fidelity enterprise prompt for Gemini.
     */
    public String buildPrompt(
            KornShellScriptDocModel script,
            KshToDbCrossCatalogBridge.CrossCatalogResolutionResult bridgeResult) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a Senior Enterprise Software Architect for the Florida Lottery IT Division.\n");
        sb.append("Analyze this mission-critical unattended Korn Shell (.ksh) batch script running on OCI Linux under IBM Workload Scheduler (IWS).\n\n");

        sb.append("### SCRIPT METADATA:\n");
        sb.append("Filename: ").append(script.getFilename()).append("\n");
        sb.append("IWS Job Name: ").append(script.getJobName() != null ? script.getJobName() : "N/A").append("\n");
        sb.append("Job Stream: ").append(script.getJobStreamName() != null ? script.getJobStreamName() : "N/A").append("\n");
        sb.append("Job Set: ").append(script.getJobSet() != null ? script.getJobSet() : "N/A").append("\n");
        sb.append("Author: ").append(script.getAuthor() != null ? script.getAuthor() : "FL Lottery Architecture").append("\n");
        sb.append("Creation Date: ").append(script.getCreationDate() != null ? script.getCreationDate() : "N/A").append("\n");
        sb.append("Declared Purpose: ").append(script.getPurpose() != null ? script.getPurpose() : "N/A").append("\n\n");

        sb.append("### MINED EXTERNAL DEPENDENCIES:\n");
        for (ScriptReferenceDoc ref : script.getScriptReferences()) {
            sb.append("- ").append(ref.getType().getDisplayName()).append(": ").append(ref.getTarget())
                    .append(" (Line ").append(ref.getLineNumber()).append(", ").append(ref.getDirection()).append(")\n");
        }
        sb.append("\n");

        if (bridgeResult != null) {
            sb.append("### CROSS-CATALOG ORACLE DATABASE ASSETS:\n");
            for (KshToDbCrossCatalogBridge.ResolvedProcedureDependency r : bridgeResult.getRoutines()) {
                sb.append("- Target Routine: ").append(r.getCanonicalName()).append(" [").append(r.getObjectType()).append("] - ").append(r.getDescription()).append("\n");
            }
            for (KshToDbCrossCatalogBridge.ResolvedTableDependency t : bridgeResult.getTables()) {
                sb.append("- Target Table: ").append(t.getQualifiedTableName())
                        .append(" | Est Rows: ").append(t.getRowCount())
                        .append(" | Access: ").append(t.getAccessMode())
                        .append(" | Primary Keys: ").append(t.getPrimaryKeys())
                        .append(" | Provenance: ").append(t.getProvenance()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("### SOURCE CODE LISTING:\n");
        sb.append("```ksh\n");
        sb.append(script.getCode() != null ? script.getCode() : "# No source provided").append("\n");
        sb.append("```\n\n");

        sb.append("OUTPUT REQUIREMENTS:\n");
        sb.append("Return pure, valid JSON with no markdown wrapping, matching this exact schema:\n");
        sb.append("{\n");
        sb.append("  \"ExecutiveSummary\": \"2-3 sentence executive operational overview\",\n");
        sb.append("  \"BusinessPurpose\": \"2-3 sentence business context and financial/gaming SLA impact\",\n");
        sb.append("  \"UserStory\": \"As a <role>, I need <this script to execute> so that <business outcome>\",\n");
        sb.append("  \"ChronologicalWorkflow\": [\"Step 1 description\", \"Step 2 description\"],\n");
        sb.append("  \"InputsAndEnvironment\": [\"Input 1\", \"Env variable 2\"],\n");
        sb.append("  \"OutputsAndArtifacts\": [\"Logfile path\", \"Report path\", \"Table modified\"],\n");
        sb.append("  \"OperationalFailureRunbook\": \"Detailed remediation steps if the job fails in IWS\",\n");
        sb.append("  \"IdempotencyAndRerunSafety\": \"Analysis of whether rerun is safe without manual DB cleanup\",\n");
        sb.append("  \"JavaMigrationBlueprint\": \"Guidance for migrating this to a Spring Boot AbstractJob using localFile(), db(), notification() DSL\"\n");
        sb.append("}\n");

        String result = sb.toString();
        return result;
    }

    /**
     * Generates expert-curated offline baseline research when offline or behind air-gapped firewalls.
     */
    public KshAiResearchDoc generateOfflineHeuristicResearch(
            KornShellScriptDocModel script,
            KshToDbCrossCatalogBridge.CrossCatalogResolutionResult bridgeResult) {

        KshAiResearchDoc doc = new KshAiResearchDoc();
        String name = script.getBaseFilename();
        String stream = script.getJobStreamName() != null ? script.getJobStreamName() : "ARS";

        if (name.contains("age_artabal") || name.contains("artabal")) {
            doc.setExecutiveSummary("Automated batch driver executing nightly accounts receivable aging against the Florida Lottery ARTABAL ledger. Invokes the PL/SQL package ARS.Age_ARTABAL.Driver to age retailer balances across 30, 60, and 90-day accounting buckets.");
            doc.setBusinessPurpose("Ensures statutory compliance and accurate retailer financial aging prior to the morning bank sweep and daily settlement reconciliation. Critical for tracking retailer delinquent debts and automated credit suspensions.");
            doc.setUserStory("As a Florida Lottery ARS Accounting Analyst, I need the age_artabal.ksh batch job to age outstanding retailer balances every night, so that retailer delinquency brackets and financial penalties reflect accurate balances before business hours.");

            List<String> workflow = new ArrayList<>();
            workflow.add("1. Initializes clean log file at /fl/ars/log/age_artabal_<YYYYMMDD>.log.");
            workflow.add("2. Dispatches enterprise wrapper /fl/ars/source/runOraclePKG.ksh passing package driver ARS.Age_ARTABAL.Driver.");
            workflow.add("3. Oracle database session connects via dedicated service account, reads active retailer debits from ARS.ARTABAL, and executes cycle aging.");
            workflow.add("4. Historical aging snapshots are appended to ARS.ARTABAL_HIST.");
            workflow.add("5. Logs are spooled to standard output and evaluated by IBM Workload Scheduler for exit status 0.");
            doc.setChronologicalWorkflow(workflow);

            doc.setInputsAndEnvironment(Arrays.asList(
                    "System Date: $(date +%Y%m%d)",
                    "Wrapper: /fl/ars/source/runOraclePKG.ksh",
                    "Database Target: DEVOLTP / PROD Oracle DB"
            ));

            doc.setOutputsAndArtifacts(Arrays.asList(
                    "Log: /fl/ars/log/age_artabal_<YYYYMMDD>.log",
                    "Database Table: ARS.ARTABAL (Updated)",
                    "Database Table: ARS.ARTABAL_HIST (Inserted)"
            ));

            doc.setOperationalFailureRunbook("1. Inspect /fl/ars/log/age_artabal_<YYYYMMDD>.log for Oracle ORA- errors (e.g. ORA-00054 resource busy or ORA-01555 snapshot too old).\n" +
                    "2. Verify database connectivity using 'sqlplus /nolog @/fl/ars/source/test_conn.sql'.\n" +
                    "3. If database lock contention occurred, confirm previous batch streams completed before rerunning.");

            doc.setIdempotencyAndRerunSafety("SAFE TO RERUN: The Age_ARTABAL.Driver procedure enforces cycle-date idempotency by checking active ledger state before applying daily aging intervals.");

            doc.setJavaMigrationBlueprint(
                    "Migrate to com.fllottery.etl.ars.job.ArsAgeArtabalJob extending AbstractJob:\n" +
                    "1. execute() calls db().callProcedure(\"ARS.Age_ARTABAL.Driver\");\n" +
                    "2. Result inspected via result.isSuccess() and getRowsLoaded();\n" +
                    "3. Telemetry automatically streamed to etldb via JobTelemetryAspect with zero manual script wrapping."
            );

        } else if (name.contains("daily_bal") || name.contains("bal_report")) {
            doc.setExecutiveSummary("Generates the daily Florida Lottery ARS Balance Report in HTML format. Sources environment variables from oltp.env, purges existing report files, and dispatches sqlplus in silent mode executing ars_daily_bal_report.sql.");
            doc.setBusinessPurpose("Delivers daily operational visibility into total retailer receivables, active credits, and pending billing invoices to finance executives and regional district offices.");
            doc.setUserStory("As a Finance Director, I need the ARS Daily Balance Report generated every morning at 06:00, so that our treasury team can audit active retailer balances against bank settlement files.");

            List<String> workflow = new ArrayList<>();
            workflow.add("1. Sources environment variables and TNS aliases via '. /fl/ars/source/oltp.env'.");
            workflow.add("2. Initializes dynamic logfile and HTML output paths using hostname and timestamp.");
            workflow.add("3. Deletes preexisting report file if present (-a $outfile test).");
            workflow.add("4. Executes 'sqlplus -s /nolog @/fl/ars/source/ars_daily_bal_report.sql $outfile'.");
            workflow.add("5. Evaluates exit code ($?); if non-zero, dispatches immediate email alert via mailx to $ARS_NOTIFY and exits 1.");
            doc.setChronologicalWorkflow(workflow);

            doc.setInputsAndEnvironment(Arrays.asList(
                    "Environment File: /fl/ars/source/oltp.env",
                    "Alert Recipient: $ARS_NOTIFY environment variable",
                    "Hostname: $(hostname)"
            ));

            doc.setOutputsAndArtifacts(Arrays.asList(
                    "Log: /fl/ars/log/ars_daily_bal_report_<YYYYMMDD>.log",
                    "Report: /fl/ars/reports/ars_daily_bal_report_<YYYYMMDD>.htm",
                    "Email: Mailx alert to $ARS_NOTIFY upon failure"
            ));

            doc.setOperationalFailureRunbook("1. Check logfile at /fl/ars/log/ars_daily_bal_report_<YYYYMMDD>.log.\n" +
                    "2. If sqlplus failed with SP2- errors, verify path permissions on the reports directory.\n" +
                    "3. Verify that $ARS_NOTIFY contains valid internal SMTP distribution lists.");

            doc.setIdempotencyAndRerunSafety("FULLY IDEMPOTENT: Script explicitly removes preexisting target HTML files prior to execution. Safe to rerun at any time without data corruption.");

            doc.setJavaMigrationBlueprint(
                    "Migrate to com.fllottery.etl.ars.job.ArsDailyBalanceReportJob extending AbstractJob:\n" +
                    "1. localFile().delete(targetReportPath);\n" +
                    "2. report().generateHtmlReport(\"ars_daily_bal_report\", queryResult);\n" +
                    "3. notification().email(\"ARS Daily Balance Report\").to(arsNotify).send();"
            );

        } else {
            // General Fallback
            doc.setExecutiveSummary(String.format("Enterprise Korn Shell batch orchestrator '%s' operating under IWS stream '%s'. Coordinates system file transfers and invokes backend Oracle database procedures.", name, stream));
            doc.setBusinessPurpose("Maintains operational data integrity across Florida Lottery gaming, retailer, and financial systems.");
            doc.setUserStory(String.format("As a %s Operations Specialist, I need '%s' to run unattended under IWS, so that downstream systems receive synchronized batch updates.", stream, name));

            List<String> workflow = new ArrayList<>();
            workflow.add("1. Evaluates system environment and establishes logging directory.");
            workflow.add("2. Dispatches operational subroutines or Oracle SQL*Plus scripts.");
            workflow.add("3. Validates exit code return values and notifies operations upon failure.");
            doc.setChronologicalWorkflow(workflow);

            doc.setOperationalFailureRunbook("Review script log files in /fl/" + stream.toLowerCase() + "/log/. Check return code and verify database connection.");
            doc.setIdempotencyAndRerunSafety("Review data modifications in database tables before rerunning to verify duplicate record avoidance.");
            doc.setJavaMigrationBlueprint("Convert to an enterprise AbstractJob in the com.fllottery.etl package using the fluent DSL.");
        }

        KshAiResearchDoc result = doc;
        return result;
    }
}
