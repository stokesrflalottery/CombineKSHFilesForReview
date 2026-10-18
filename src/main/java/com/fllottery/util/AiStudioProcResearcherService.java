package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Dedicated AI Studio Service for automated Oracle routine research with enhanced documentation fields:
 * - WHAT (2-4 sentences technical/operational flow)
 * - WHY (2-4 sentences business purpose & batch context)
 * - Parameters
 * - Dependencies (Reads, Writes, Calls, DBLinks)
 * - Business Rules (Formulas, thresholds, statutory logic)
 * - Error Handling (Rollbacks, exceptions, logging)
 * - Testing Strategy (How developers can test with verification queries)
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @since 2026-09-24
 */
public class AiStudioProcResearcherService {

    public enum TierMode {
        FREE("FREE (100% Free Tier - 15 RPM pacing, $0.00 cost)"),
        PAID("PAID (Prepaid Enterprise - Uncapped speed)"),
        HYBRID("HYBRID (Smart Cooldown Routing - Free calls prioritized, Paid calls dispatched during free cooldown)"),
        TURBO("TURBO (Dual-Lane Async - Concurrent Free 15 RPM worker + Parallel Multi-Threaded Paid workers)");

        private final String description;

        TierMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public static TierMode fromString(String val) {
            if (val == null || val.trim().isEmpty()) {
                TierMode defaultMode = TierMode.HYBRID;
                return defaultMode;
            }
            String clean = val.trim().toUpperCase();
            if (clean.contains("TURBO") || clean.contains("ASYNC") || clean.contains("DUAL") || clean.contains("PARALLEL")) {
                TierMode turboMode = TierMode.TURBO;
                return turboMode;
            }
            if (clean.contains("HYB") || clean.equals("BOTH") || clean.equals("MIXED") || clean.equals("AUTO")) {
                TierMode hybridMode = TierMode.HYBRID;
                return hybridMode;
            }
            if (clean.contains("FREE")) {
                TierMode freeMode = TierMode.FREE;
                return freeMode;
            }
            if (clean.contains("PAID") || clean.contains("PREPAID") || clean.contains("GCP")) {
                TierMode paidMode = TierMode.PAID;
                return paidMode;
            }
            TierMode fallbackMode = TierMode.HYBRID;
            return fallbackMode;
        }
    }

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + DEFAULT_MODEL + ":generateContent";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final long FREE_SPACING_MS = 4100;

    private final String freeApiKey;
    private final String paidApiKey;
    private TierMode tierMode;
    private long lastFreeCallTimestampMs = 0;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public AiStudioProcResearcherService() {
        String freeKey = System.getenv("GEMINI_FREE_API_KEY");
        if (freeKey == null || freeKey.trim().isEmpty()) {
            freeKey = System.getProperty("GEMINI_FREE_API_KEY", "");
        }
        String paidKey = System.getenv("GEMINI_PAID_API_KEY");
        if (paidKey == null || paidKey.trim().isEmpty()) {
            paidKey = System.getProperty("GEMINI_PAID_API_KEY", "");
        }
        String defaultKey = System.getenv("GEMINI_API_KEY");
        if (defaultKey == null || defaultKey.trim().isEmpty()) {
            defaultKey = System.getProperty("GEMINI_API_KEY", "");
        }

        if (freeKey.isEmpty()) {
            freeKey = defaultKey;
        }
        if (paidKey.isEmpty()) {
            paidKey = defaultKey;
        }

        this.freeApiKey = freeKey;
        this.paidApiKey = paidKey;
        this.tierMode = (!freeKey.isEmpty() && !paidKey.isEmpty() && !freeKey.equals(paidKey)) ? TierMode.HYBRID : TierMode.FREE;

        this.httpClient = EnterpriseProxyResolver.createEnterpriseHttpClient(Duration.ofSeconds(30));
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public AiStudioProcResearcherService(String explicitApiKey) {
        this.freeApiKey = (explicitApiKey != null) ? explicitApiKey : "";
        this.paidApiKey = (explicitApiKey != null) ? explicitApiKey : "";
        this.tierMode = TierMode.FREE;
        this.httpClient = EnterpriseProxyResolver.createEnterpriseHttpClient(Duration.ofSeconds(30));
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public AiStudioProcResearcherService(TierMode tierMode, String freeApiKey, String paidApiKey) {
        this.tierMode = (tierMode != null) ? tierMode : TierMode.HYBRID;
        this.freeApiKey = (freeApiKey != null) ? freeApiKey : "";
        this.paidApiKey = (paidApiKey != null) ? paidApiKey : "";
        this.httpClient = EnterpriseProxyResolver.createEnterpriseHttpClient(Duration.ofSeconds(30));
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public AiStudioProcResearcherService(TierMode tierMode, String freeApiKey, String paidApiKey, HttpClient customHttpClient) {
        this.tierMode = (tierMode != null) ? tierMode : TierMode.HYBRID;
        this.freeApiKey = (freeApiKey != null) ? freeApiKey : "";
        this.paidApiKey = (paidApiKey != null) ? paidApiKey : "";
        this.httpClient = (customHttpClient != null) ? customHttpClient : EnterpriseProxyResolver.createEnterpriseHttpClient(Duration.ofSeconds(30));
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public TierMode getTierMode() {
        TierMode result = this.tierMode;
        return result;
    }

    public void setTierMode(TierMode tierMode) {
        this.tierMode = tierMode;
    }

    public String getFreeApiKey() {
        String result = this.freeApiKey;
        return result;
    }

    public String getPaidApiKey() {
        String result = this.paidApiKey;
        return result;
    }

    public EnhancedRoutineResearchResult researchRoutine(String schema, String pkg, String routineName, String routineType, String sqlSource) {
        String lookupKey = String.format("%s.%s.%s", schema.toUpperCase(), (pkg == null || pkg.isEmpty()) ? "-" : pkg.toUpperCase(), routineName.toUpperCase());

        String currentKey;
        boolean isFreeCall;
        long now = System.currentTimeMillis();

        if (this.tierMode == TierMode.PAID) {
            currentKey = !this.paidApiKey.isEmpty() ? this.paidApiKey : this.freeApiKey;
            isFreeCall = false;
        } else if (this.tierMode == TierMode.FREE) {
            currentKey = !this.freeApiKey.isEmpty() ? this.freeApiKey : this.paidApiKey;
            isFreeCall = true;
        } else if (this.tierMode == TierMode.TURBO) {
            // Turbo: Dual-Lane instantaneous dispatch
            boolean freeInCooldown = (this.lastFreeCallTimestampMs > 0 && (now - this.lastFreeCallTimestampMs) < FREE_SPACING_MS);
            if (!freeInCooldown && !this.freeApiKey.isEmpty()) {
                currentKey = this.freeApiKey;
                isFreeCall = true;
            } else if (!this.paidApiKey.isEmpty()) {
                currentKey = this.paidApiKey;
                isFreeCall = false;
            } else {
                currentKey = this.freeApiKey;
                isFreeCall = true;
            }
        } else { // HYBRID
            boolean freeInCooldown = (this.lastFreeCallTimestampMs > 0 && (now - this.lastFreeCallTimestampMs) < FREE_SPACING_MS);
            if (!freeInCooldown && !this.freeApiKey.isEmpty()) {
                currentKey = this.freeApiKey;
                isFreeCall = true;
            } else if (!this.paidApiKey.isEmpty()) {
                // Free Tier in cooldown -> dispatch immediately via Paid Tier (zero wait time)
                currentKey = this.paidApiKey;
                isFreeCall = false;
            } else {
                currentKey = this.freeApiKey;
                isFreeCall = true;
            }
        }

        if (currentKey == null || currentKey.isEmpty()) {
            throw new IllegalStateException("ACTION REQUIRED: No valid GEMINI API Key configured for research.");
        }

        if (isFreeCall) {
            long timeSinceLast = System.currentTimeMillis() - this.lastFreeCallTimestampMs;
            if (this.lastFreeCallTimestampMs > 0 && timeSinceLast < FREE_SPACING_MS) {
                try {
                    Thread.sleep(FREE_SPACING_MS - timeSinceLast);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            this.lastFreeCallTimestampMs = System.currentTimeMillis();
        }

        String prompt = buildPrompt(schema, pkg, routineName, routineType, sqlSource);
        String thinkingLevel = resolveAdaptiveThinkingLevel(routineType, sqlSource);
        String requestPayload = buildGeminiRequestBody(prompt, thinkingLevel);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GEMINI_API_URL + "?key=" + currentKey))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(45))
                        .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    EnhancedRoutineResearchResult result = parseGeminiResponse(lookupKey, schema, pkg, routineName, response.body());
                    return result;
                } else if (response.statusCode() == 429) {
                    // In Hybrid mode: if Free tier gets 429 rate limited, immediately failover to Paid tier key!
                    if (isFreeCall && this.tierMode == TierMode.HYBRID && !this.paidApiKey.isEmpty() && !this.paidApiKey.equals(currentKey)) {
                        System.out.printf("   [Hybrid Failover] Free Tier 429 -> Seamlessly routing to Paid Tier for '%s'...%n", lookupKey);
                        currentKey = this.paidApiKey;
                        isFreeCall = false;
                        attempt = 0;
                        continue;
                    }
                    long backoff = isFreeCall ? (RETRY_DELAY_MS * 2 * attempt) : (RETRY_DELAY_MS * attempt);
                    System.out.printf("   [Rate Limit 429] Backing off %d ms (Attempt %d/%d)...%n", backoff, attempt, MAX_RETRIES);
                    Thread.sleep(backoff);
                } else if (response.statusCode() == 503 || response.statusCode() == 500 || response.statusCode() == 502 || response.statusCode() == 504) {
                    if (isFreeCall && !this.paidApiKey.isEmpty() && !this.paidApiKey.equals(currentKey)) {
                        System.out.printf("   [Transient %d Server Busy] Free Tier Busy -> Seamlessly switching to Paid Tier key for '%s'...%n", response.statusCode(), lookupKey);
                        currentKey = this.paidApiKey;
                        isFreeCall = false;
                        attempt = 0;
                        continue;
                    }
                    long backoff = isFreeCall ? (RETRY_DELAY_MS * 2 * attempt) : (RETRY_DELAY_MS * attempt);
                    System.out.printf("   [Server Busy %d] Backing off %d ms (Attempt %d/%d)...%n", response.statusCode(), backoff, attempt, MAX_RETRIES);
                    Thread.sleep(backoff);
                } else {
                    System.err.printf("   [API Error %d] %s%n", response.statusCode(), response.body());
                    if (attempt == MAX_RETRIES) {
                        EnhancedRoutineResearchResult failResult = EnhancedRoutineResearchResult.createFallback(lookupKey, schema, pkg, routineName, "API Error: HTTP " + response.statusCode());
                        return failResult;
                    }
                }
            } catch (Exception ex) {
                if (isFreeCall && !this.paidApiKey.isEmpty() && !this.paidApiKey.equals(currentKey)) {
                    System.out.printf("   [Timeout/Network Failover] Free Endpoint Timeout (%s) -> Seamlessly switching to Paid Tier key for '%s'...%n", ex.getClass().getSimpleName(), lookupKey);
                    currentKey = this.paidApiKey;
                    isFreeCall = false;
                    attempt = 0;
                    continue;
                }
                System.err.printf("   [Network/JSON Error on attempt %d]: %s%n", attempt, ex.getMessage());
                if (attempt == MAX_RETRIES) {
                    EnhancedRoutineResearchResult exResult = EnhancedRoutineResearchResult.createFallback(lookupKey, schema, pkg, routineName, "Exception: " + ex.getMessage());
                    return exResult;
                }
            }
        }

        EnhancedRoutineResearchResult finalFallback = EnhancedRoutineResearchResult.createFallback(lookupKey, schema, pkg, routineName, "Failed after max retries.");
        return finalFallback;
    }

    private String buildPrompt(String schema, String pkg, String routineName, String routineType, String sqlSource) {
        String cleanSql = (sqlSource == null) ? "" : sqlSource.trim();
        String prompt = String.format("""
            You are a half application programmer/database engineer and technical documentation lead for the Florida Lottery.
            Analyze this legacy Oracle %s: %s (Schema: %s, Package: %s).

            SQL Source:
            ```sql
            %s
            ```

            PURPOSE & TARGET AUDIENCE:
            1. Primary Audience - Java and Web Developers:
               - Onboarding to the enterprise team and need to understand legacy database routines, parameter contracts, table mutations, and dependency flows.
               - Need exact technical mechanics so they can write resilient Java/Spring Boot jobs, integrate web applications, or safely plan service migrations.
            2. Secondary Audience - Business Analysts (BAs) and Executive Leadership:
               - Relying on this documentation as the single source of truth for Florida Lottery database routines, business logic, accounting rules, and operational stakes.
               - Need plain-English clarity on business purpose, downstream impacts, and financial reconciliation without needing PL/SQL expertise.

            VOICE AND STYLE RULES:
            - Write like an experienced human staff engineer writing a concise README, PR summary, or tech-spec.
            - Dual-layer clarity: The "what" and "why" must be clear to BAs and executive leadership without sacrificing domain rigor. The "how" and technical sections must provide exact, actionable mechanics for Java/Web developers.
            - NO corporate fluff or preamble filler (e.g., do NOT start with "This procedure is designed to...", "It exists to...", or "The primary objective is...").
            - Lead with active verbs: "Extracts...", "Calculates...", "Loads...", "Iterates...", "Reconciles...".
            - Tone: Grounded, direct, and pragmatic. If the code contains technical debt, unindexed scans, or missing exception handlers, name it directly.
            - TERMINOLOGY & VOCABULARY DISCIPLINE:
              * Do NOT use "governance" -> Use "operational management", "administrative rules", "compliance tracking", "audit logging", or "data ownership".
              * Do NOT use "ingest" -> Use "import", "load", "data intake", "extraction", or "batch consumption".
              * Do NOT use "persistence" -> Use "database storage", "table writes", "record mutation", or "database saving".
            - Florida Lottery Domain Context: Accurately reference lottery gaming concepts (e.g., retailer weekly settlement, instant scratch-off/terminal game validations, draw sales, jackpot liability, commission ledger, staging tables).
            - Do NOT assume or mention any modern ETL framework unless explicitly indicated in the SQL comments or code.

            FLORIDA LOTTERY ENTERPRISE DOMAIN CONTEXT & 16 CANONICAL DATABASE SCHEMAS:
            - Business Lifecycle & Pipeline Chronology: STAGE -> TRANSLATOR -> ARS/RETAILER -> GL/VP -> DWDOL
            - Core Interfacing Systems:
              * OLS / IGT: On-Line System running retailer POS terminals for Draw games (Powerball, Mega Millions, Florida Lotto, Fantasy 5, Cash Pop, Pick 2/3/4/5).
              * GEMS / Scientific Games: Gaming Enterprise Management System for Instant Scratch-Off inventory, shipments, pack activations, and validations.
              * IWS: IBM Workload Scheduler orchestrating daily unattended batch windows (often overnight).

            - The 16 Canonical Enterprise Database Schemas (DEVOLTP & DEVDW):
              1. ARS: Accounts Receivable & Settlement (Retailer accounting, weekly billing settlements, EFT bank sweeps, retailer adjustments, commission ledgers).
              2. COMMON: Enterprise Shared Utilities (Global lookup parameters, calendar rules, active game definitions V_ACTIVE_GAMES, and audit logging).
              3. CSTS: Customer Service & Terminal Support (Retailer hardware trouble ticketing, field technician service dispatch, terminal problem tracking).
              4. DWDOL: Department of the Lottery Enterprise Data Warehouse (Historical sales trends, game analytics, executive reporting mart on DEVDW).
              5. DOLEMP: Department of the Lottery Employees (Internal personnel registry, security access, administrative permissions).
              6. GL: General Ledger (Enterprise double-entry accounting, chart of accounts, debit/credit journal distributions, state FLAIR/PALM feeds, EETF transfers).
              7. INET: Internet Services (Legacy web portal services, public website feeds, winning numbers publishing).
              8. INET2: Internet Services Tier 2 / Modern Player Portal (Mobile app backend, Second Chance promotional drawings, player account registrations, digital coupon redemptions).
              9. LIDS: Lottery Inventory & Distribution System (Instant Scratch-Off ticket warehouse inventory management, pack tracking, order fulfillment, return logistics).
              10. LST: Lottery Sales Tracking (Sales representative performance, sales territory management, retailer performance quotas, district field sales monitoring).
              11. PR: Partner / Retailer Network & Payroll (Authoritative retailer registry PRTMAST, chain store relationships, licensing, bonding, retailer profile records).
              12. RETAILER: Retailer Management & Portal Services (Retailer contracting, retailer terminal assignments, portal self-service, retailer banking credentials).
              13. STAGE: Staging Data Layer (Nightly raw transaction landing zone from IGT/Scientific Games before transformation, uncommitted batch buffers).
              14. TMS: Terminal Management System (Retailer POS gaming terminal hardware inventory, communication link health, terminal configurations, firmware tracking).
              15. TRANSLATOR: Enterprise Data Translation & ETL Pipeline (Core transformation engine: parses raw external files from STAGE, normalizes them, and translates them into ARS, GL, and reporting formats).
              16. VP: Vendor Payments (Accounts payable, vendor invoice matching, state warrant disbursement vouchers, state contractor payments, and vendor EFT feeds).

            - Common Legacy Table Naming Conventions & Entity Meanings:
              * ART* = Accounts Receivable Tables:
                - ARTTRAN: ARS Transaction Ledger (weekly settlements, adjustments, draw sales, instant validations).
                - ARTCHAN: ARS Retailer EFT bank account updates, routing channel modifications, and sweeps.
                - ARTDNBR: ARS Retailer Bank Deposit Slip Tracking & deposit bag identification numbers.
                - ARTCLTR: ARS Retailer Collateral, performance surety bonds, and letters of credit.
                - ARTMREV: ARS Manual Revenue adjustments, retailer credits, and voided ticket offsets.
                - ARTABAL: ARS Retailer Account Balances, ending balances, and weekly billing invoice totals.
                - ARTACCT: ARS Master Retailer Accounting financial configuration and status profiles.
                - ARTLCDE: ARS Location / District Code lookup and sales territory mapping.
              * PRT* = Partner / Retailer Tables:
                - PRTMAST: Primary Retailer Master Register (physical store status, corporate chains, active retailer credentials).

            - Operational Storytelling Guideline: Use this enterprise schema map and domain glossary when composing the 'what', 'why', 'how', 'businessRules', and flowchart nodes so that cryptic abbreviated table and schema names are translated into clear, real-world business entities.

            Provide a strict JSON response adhering to this schema:
            {
              "what": "2 to 5 plain-English sentences explaining the functional data flow. Detail which staging or core tables are read, what validations occur, and what target tables are modified.",
              "why": "2 to 5 plain-English sentences focused on business purpose and operational stakes. Explain who breaks if this doesn't run, downstream consumers (e.g., retailer invoicing, daily cash reconciliation, jackpot audit, general ledger), and the specific accounting or batch window it supports.",
              "how": "2 to 5 plain-English sentences tailored for Java/Web developers explaining technical mechanics. Specify data access patterns (set-based DML vs procedural cursor loops, BULK COLLECT / FORALL), transaction boundaries (COMMIT/SAVEPOINT/AUTONOMOUS_TRANSACTION frequency), and any dynamic SQL (EXECUTE IMMEDIATE) or package state utilized.",
              "callerChannels": ["Array of caller types, e.g. Batch Script, Oracle Forms, Web Portal, DB Trigger"],
              "flowSteps": ["Array of 3 to 6 short, sequential numbered steps in plain English"],
              "parameters": "Formatted string: 'p_name IN TYPE (Purpose)' or 'None'",
              "dependencies": "Reads: [TBL(COLS)] | Updates: [TBL(COLS)] | Writes: [TBL(COLS)] | Deletes: [TBL or None] | Calls: [PKG.PROC or None] | DBLinks: [LINK or None]",
              "dataFlowDiagram": "Callers: [...] -> Reads: [...] -> Updates: [...] -> Writes: [...] -> Deletes: [...] -> Calls: [...]",
              "businessRules": "Bullet-style or concise summary of formulas, statutory thresholds, commission rates, and conditions enforced.",
              "errorHandling": "Practical explanation of transaction safety (e.g., commits, rollbacks, unhandled exceptions, or silent failures).",
              "testingStrategy": "Step-by-step developer verification steps (e.g., 1. Setup test records, 2. Run proc, 3. Expected ledger/row count check).",
              "complexityScore": 1-10 integer,
              "mermaidDiagram": "A valid, accurate Mermaid flowchart diagram capturing the functional business process and data flow of this routine in valid 'flowchart TD' syntax adhering strictly to the Florida Lottery Enterprise Storytelling Guidelines:\n1. BUSINESS PROCESS STORYTELLING OVER PL/SQL PLUMBING: Focus on WHAT business process is occurring (e.g. read external feed, map PRN to location ID, stage adjustments, aggregate payouts, calculate SHA-1 digest) rather than mechanical PL/SQL plumbing. NEVER draw low-level runtime mechanics like 'Open cursor c1', 'Fetch next record into c1rec', 'Check c1%notfound', 'Close cursor c1', or 'Increment counter'.\n2. MODULAR MULTI-STAGE SECTIONS (ANTI-CROWDING RULE): For complex routines or workflows with > 6-8 operations, BREAK the workflow into 2 to 4 distinct, numbered Stage Subgraphs (e.g. subgraph Stage1 [\"Stage 1: Initialization & Staging Ingestion\"], subgraph Stage2 [\"Stage 2: Validation & Transformation Loop\"], subgraph Stage3 [\"Stage 3: Ledger Reconciliation & Target Mutation\"], subgraph Stage4 [\"Stage 4: Audit Finalization & Notification\"]). Limit each stage subgraph to 3 to 6 operational nodes to prevent overcrowding.\n3. CLEAN INTER-STAGE HANDOFFS: Connect subgraphs in clean sequential order (e.g. Stage1 --> Stage2 --> Stage3). NEVER draw cross-stage diagonal spaghetti arrows that crisscross through multiple subgraphs. Keep table, view, and file entity nodes localized inside the stage that operates on them.\n4. ELEVATE CURSOR LOOPS TO FUNCTIONAL SUBGRAPHS: Abstract row-by-row cursor loops into a clean subgraph (e.g. subgraph IngestionLoop [\"Row Transformation & Staging Loop\"]) that clearly illustrates the main transformation path, row rejection branches (-->|No / Reject| logRej[(\"<b>TABLE / ERROR LOG</b><br/>ERROR_LOG\")]), and the batch commit.\n5. COMPRESS MULTI-SLOT LADDER LOGIC: Never unroll repetitive sequential IF checks (such as checking Bins 1 through 24 or 34 sequentially). Compress multi-slot evaluations into a single iterator/evaluation node (e.g. [\"Iterate & evaluate active terminal bins 1 through 34\"]).\n6. STANDARDIZE DRIVER ORCHESTRATION: Represent master batch drivers as a linear execution pipeline (Initialize Audit -> Truncate Staging -> Execute Loader -> Finalize Audit) with a centralized dashed failure channel (Pipeline -.->|Step Failure| logErr[\"Record Fatal Error & Flag Audit FAILED\"]) rather than chaining repetitive identical status check diamonds.\n7. CONVERSATIONAL DECISION QUESTIONS & MANDATORY DUAL BRANCHES: Every decision diamond must ask a full plain-English question in double quotes: e.g., rowOk{\"Record Valid?\"}, checkDraw{\"Draw Exists in INET.DRAW?\"}. CRITICAL: Every decision diamond MUST have ALL outbound branches explicitly defined directly from the diamond (both |Yes| and |No|). NEVER leave a decision diamond dangling.\n8. DISTINCT SUCCESS VS. ABORT TERMINAL NODES: Explicitly separate successful completion terminals from fatal rollback/abortion terminals: e.g., finishSuccess([\"End: Adjustments Staged Successfully\"]) vs. finishAbort([\"End: Execution Aborted / Rolled Back\"]). Never route fatal errors to a 'Completed Successfully' pill.\n9. STRICT BRACKET MATCHING & CLEAN SYNTAX: Never mix opening square brackets with closing parentheses. Processes MUST be node[\"Action\"], decisions cond{\"Question?\"}, cylindrical tables [(\"...\")], parallelograms [/\"...\"/], and terminals term([\"Start/End: ...\"]).\n10. EXPLICIT NODE ARCHETYPE LABELS: Format the entity type in bold above the identifier (e.g., [\"<b>PROCEDURE</b><br/>SCHEMA.PROC_NAME\"], [(\"<b>TABLE</b><br/>SCHEMA.TABLE_NAME\")], [(\"<b>VIEW</b><br/>SCHEMA.VIEW_NAME\")], [/\"<b>DATA FILE</b><br/>filename.dat\"/]) so non-technical readers instantly distinguish procedures, tables, and files at a glance.\n\nGOLD STANDARD MULTI-STAGE REFERENCE PATTERN (Modular Pipeline Flow):\nflowchart TD\n    startNode([\"Start: Ingest Retailer Daily Adjustments\"]) --> Stage1\n\n    subgraph Stage1 [\"Stage 1: Header Ingestion & Staging Extraction\"]\n        readExt[(\"<b>TABLE / SOURCE</b><br/>FLOT_DAILY_ADJUST_EXT\")]\n        readExt --> resolvePrn[\"Map Retailer PRN to Location ID (PRN_XREF)\"]\n        resolvePrn --> insertStg[(\"<b>TABLE / STAGING</b><br/>FLOT_DAILY_ADJUST_STG\")]\n    end\n\n    Stage1 --> Stage2\n\n    subgraph Stage2 [\"Stage 2: Validation & Transformation Loop\"]\n        insertStg --> rowOk{\"Record Valid?\"}\n        rowOk -->|Yes| stageValid[\"Format Normalized Adjustment Record\"]\n        rowOk -->|No| logRej[(\"<b>TABLE / ERROR LOG</b><br/>ERROR_LOG\")]\n        stageValid --> nextRow[\"Evaluate Next Record\"]\n        logRej --> nextRow\n    end\n\n    Stage2 --> Stage3\n\n    subgraph Stage3 [\"Stage 3: Ledger Mutation & Batch Finalization\"]\n        nextRow --> commitBatch[\"Commit Ledger Transaction (ARTACCT)\"]\n        commitBatch --> updateAudit[(\"<b>TABLE / AUDIT</b><br/>ETL_JOB_LOG\")]\n    end\n\n    Stage3 --> successEnd([\"End: Adjustments Staged Successfully\"])\n\n    Stage2 -.->|Fatal Validation Failure| handleFatal[\"Rollback & Flag Batch Exception\"]\n    handleFatal --> failEnd([\"End: Execution Aborted / Rolled Back\"])",
              "verificationSqlScript": "A complete, runnable Oracle PL/SQL verification script block (anonymous block) that tests whether this routine completed successfully. CRITICAL MANDATORY REQUIREMENTS:\n1. TOP SQL HEADER COMMENTS: Must begin with an explicit comment block (-- or /* ... */) detailing: (a) WHAT the script is supposed to do (exact validation objectives, row mutation assertions, status checks), (b) WHAT DATABASES and SCHEMAS the script accesses (e.g. Target Database: Oracle OCI Enterprise Database / DEVOLTP, Schemas Accessed: ARS, COMMON, etc.), and (c) all specific tables/views queried.\n2. STRICT SCHEMA PREFIXING: ALWAYS prefix the schema name to every table, view, or sequence referenced in SQL statements (e.g. ARS.ARS_STG_SALES, COMMON.AUDIT_LOG). NEVER reference bare table names without their schema prefix in FROM, JOIN, INTO, or UPDATE clauses.\n3. STATUS ASSERTIONS: Query modified target tables or audit logs, verify row counts, validate status flags (e.g. 'PROCESSED'), output explicit '[PASS] ...' or '[FAIL] ...' status messages via DBMS_OUTPUT.PUT_LINE, and verify data integrity."
            }
            """, routineType, routineName, schema, (pkg == null ? "-" : pkg), cleanSql);

        String result = prompt;
        return result;
    }

    public static String resolveAdaptiveThinkingLevel(String routineType, String sqlSource) {
        if (sqlSource == null || sqlSource.trim().isEmpty()) {
            String defaultLevel = "medium";
            return defaultLevel;
        }
        String upperSql = sqlSource.toUpperCase();
        int sqlLen = sqlSource.trim().length();

        // High reasoning depth for complex package bodies, multi-cursor loops, or massive SQL (> 4000 chars)
        if (sqlLen > 4000 || upperSql.contains("PACKAGE BODY") || upperSql.contains("AUTONOMOUS_TRANSACTION")
                || upperSql.contains("BULK COLLECT") || upperSql.contains("FORALL") || upperSql.contains("MERGE INTO")) {
            String highLevel = "high";
            return highLevel;
        }
        // Minimal reasoning depth for short / simple DML, table metadata, or lightweight lookups (< 600 chars)
        if (sqlLen < 600 || (routineType != null && (routineType.equalsIgnoreCase("TABLE") || routineType.equalsIgnoreCase("VIEW")))) {
            String minimalLevel = "minimal";
            return minimalLevel;
        }
        // Medium depth for standard procedures and functions
        String mediumLevel = "medium";
        return mediumLevel;
    }

    private String buildGeminiRequestBody(String prompt) {
        String result = buildGeminiRequestBody(prompt, "medium");
        return result;
    }

    private String buildGeminiRequestBody(String prompt, String thinkingLevel) {
        try {
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

            String result = mapper.writeValueAsString(root);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Gemini request body", e);
        }
    }

    private EnhancedRoutineResearchResult parseGeminiResponse(String lookupKey, String schema, String pkg, String routineName, String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            EnhancedRoutineResearchResult failResult = EnhancedRoutineResearchResult.createFallback(lookupKey, schema, pkg, routineName, "Empty or filtered candidate response from Gemini API");
            return failResult;
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            EnhancedRoutineResearchResult failResult = EnhancedRoutineResearchResult.createFallback(lookupKey, schema, pkg, routineName, "Missing content parts in Gemini API candidate response");
            return failResult;
        }
        String jsonText = parts.get(0).path("text").asText("{}");

        JsonNode parsed = mapper.readTree(jsonText);

        String what = parsed.path("what").asText("").replaceAll("[\\r\\n\\t]+", " ").trim();
        String why = parsed.path("why").asText("").replaceAll("[\\r\\n\\t]+", " ").trim();
        String how = parsed.path("how").asText("").replaceAll("[\\r\\n\\t]+", " ").trim();

        String combinedDesc;
        if (!how.isEmpty()) {
            combinedDesc = String.format("WHAT: %s | WHY: %s | HOW: %s", what, why, how);
        } else {
            combinedDesc = String.format("WHAT: %s | WHY: %s", what, why);
        }

        String params = parsed.path("parameters").asText("None").replaceAll("[\\r\\n\\t]+", " ").trim();
        String deps = parsed.path("dependencies").asText("Reads: [None] | Writes: [None] | Calls: [None]").replaceAll("[\\r\\n\\t]+", " ").trim();
        String dfd = parsed.path("dataFlowDiagram").asText("").replaceAll("[\\r\\n\\t]+", " ").trim();
        if (dfd.isEmpty()) {
            dfd = String.format("Callers: [BATCH: IWS Batch Job] -> %s", deps.replace(" | ", " -> "));
        }
        String bRules = parsed.path("businessRules").asText("Standard Florida Lottery transaction processing rules.").replaceAll("[\\r\\n\\t]+", " ").trim();
        String errHandling = parsed.path("errorHandling").asText("Standard transaction boundary.").replaceAll("[\\r\\n\\t]+", " ").trim();
        String testStrat = parsed.path("testingStrategy").asText("Execute with test parameters and verify ledger status.").replaceAll("[\\r\\n\\t]+", " ").trim();
        int comp = parsed.path("complexityScore").asInt(1);
        String recs = parsed.path("recommendations").asText("None / Code is optimal").replaceAll("[\\r\\n\\t]+", " ").trim();
        String mermaid = cleanAndRepairMermaidSyntax(parsed.path("mermaidDiagram").asText(""));
        String verifySql = parsed.path("verificationSqlScript").asText(parsed.path("VerificationSqlScript").asText("")).trim();
        if (verifySql.startsWith("```")) {
            verifySql = verifySql.replaceAll("^```[a-zA-Z]*\\R?", "").replaceAll("\\R?```$", "").trim();
        }

        EnhancedRoutineResearchResult result = new EnhancedRoutineResearchResult(
                lookupKey, schema.toUpperCase(), (pkg == null || pkg.isEmpty()) ? "-" : pkg.toUpperCase(),
                routineName.toUpperCase(), combinedDesc, what, why, how, params, deps, dfd, bRules, errHandling, testStrat, comp, recs, mermaid, verifySql
        );
        return result;
    }

    public static String cleanAndRepairMermaidSyntax(String raw) {
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

            // B. Auto-repair unclosed terminal pills: e.g. (["End: ... "]
            if (trimmed.contains("([\"") && trimmed.endsWith("\"]") && !trimmed.endsWith("\"])")) {
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

            // E. Auto-repair missing quotes inside shape brackets
            if (trimmed.matches(".*\\w+\\[[^\"]+\\].*") && !trimmed.contains("-->") && !trimmed.startsWith("subgraph")) {
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

    public static class EnhancedRoutineResearchResult {
        public final String lookupKey;
        public final String schema;
        public final String packageName;
        public final String objectName;
        public final String description;
        public final String what;
        public final String why;
        public final String how;
        public final String parameters;
        public final String dependencies;
        public final String dataFlowDiagram;
        public final String businessRules;
        public final String errorHandling;
        public final String testingStrategy;
        public final int complexityScore;
        public final String recommendations;
        public final String mermaidDiagram;
        public final String verificationSqlScript;

        public EnhancedRoutineResearchResult(String lookupKey, String schema, String packageName, String objectName,
                                             String description, String what, String why, String how, String parameters,
                                             String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                                             String testingStrategy, int complexityScore, String recommendations, String mermaidDiagram,
                                             String verificationSqlScript) {
            this.lookupKey = lookupKey;
            this.schema = schema;
            this.packageName = packageName;
            this.objectName = objectName;
            this.description = description;
            this.what = what;
            this.why = why;
            this.how = how;
            this.parameters = parameters;
            this.dependencies = dependencies;
            this.dataFlowDiagram = dataFlowDiagram;
            this.businessRules = businessRules;
            this.errorHandling = errorHandling;
            this.testingStrategy = testingStrategy;
            this.complexityScore = complexityScore;
            this.recommendations = recommendations;
            this.mermaidDiagram = (mermaidDiagram != null) ? mermaidDiagram : "";
            this.verificationSqlScript = (verificationSqlScript != null) ? verificationSqlScript : "";
        }

        public EnhancedRoutineResearchResult(String lookupKey, String schema, String packageName, String objectName,
                                             String description, String what, String why, String how, String parameters,
                                             String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                                             String testingStrategy, int complexityScore, String recommendations, String mermaidDiagram) {
            this(lookupKey, schema, packageName, objectName, description, what, why, how, parameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, mermaidDiagram, "");
        }

        public EnhancedRoutineResearchResult(String lookupKey, String schema, String packageName, String objectName,
                                             String description, String what, String why, String how, String parameters,
                                             String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                                             String testingStrategy, int complexityScore, String recommendations) {
            this(lookupKey, schema, packageName, objectName, description, what, why, how, parameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, "", "");
        }

        public EnhancedRoutineResearchResult(String lookupKey, String schema, String packageName, String objectName,
                                             String description, String what, String why, String parameters,
                                             String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                                             String testingStrategy, int complexityScore, String recommendations) {
            this(lookupKey, schema, packageName, objectName, description, what, why, "", parameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, "", "");
        }

        public static EnhancedRoutineResearchResult createFallback(String lookupKey, String schema, String pkg, String obj, String err) {
            EnhancedRoutineResearchResult result = new EnhancedRoutineResearchResult(
                    lookupKey, schema, pkg, obj, "WHAT: Pending AI analysis | WHY: " + err, "", "", "",
                    "None", "Reads: [None] | Writes: [None] | Calls: [None]",
                    "Callers: [UNKNOWN] -> Reads: [None] -> Writes: [None]", "N/A", "N/A", "N/A", 1, "Verify API connection", "", ""
            );
            return result;
        }
    }

    /**
     * Represents an individual routine research unit of work queued for Turbo processing.
     */
    public static class RoutineResearchTask {
        public final String schema;
        public final String packageName;
        public final String routineName;
        public final String routineType;
        public final String sqlSource;
        public final String dbName;

        public RoutineResearchTask(String schema, String packageName, String routineName, String routineType, String sqlSource) {
            this(schema, packageName, routineName, routineType, sqlSource, "DEVOLTP");
        }

        public RoutineResearchTask(String schema, String packageName, String routineName, String routineType, String sqlSource, String dbName) {
            this.schema = (schema != null) ? schema.trim().toUpperCase() : "";
            this.packageName = (packageName != null && !packageName.trim().isEmpty()) ? packageName.trim().toUpperCase() : "-";
            this.routineName = (routineName != null) ? routineName.trim().toUpperCase() : "";
            this.routineType = (routineType != null && !routineType.trim().isEmpty()) ? routineType.trim().toUpperCase() : "PROCEDURE";
            this.sqlSource = (sqlSource != null) ? sqlSource : "";
            this.dbName = (dbName != null && !dbName.trim().isEmpty()) ? dbName.trim().toUpperCase() : "DEVOLTP";
        }

        public String getLookupKey() {
            String result = String.format("%s.%s.%s", schema, packageName, routineName);
            return result;
        }

        @Override
        public String toString() {
            String result = getLookupKey() + " (" + routineType + ")";
            return result;
        }
    }

    /**
     * Immutable result of a Turbo concurrent execution run across Free and Paid lanes.
     */
    public static class TurboExecutionResult {
        public final Map<String, EnhancedRoutineResearchResult> completedFindings;
        public final Map<String, String> erroredRoutines;
        public final int totalSubmitted;
        public final int totalSuccess;
        public final int totalErrors;
        public final int freeLaneProcessed;
        public final int paidLaneProcessed;
        public final long durationMs;

        public TurboExecutionResult(Map<String, EnhancedRoutineResearchResult> completedFindings,
                                    Map<String, String> erroredRoutines,
                                    int totalSubmitted,
                                    int freeLaneProcessed,
                                    int paidLaneProcessed,
                                    long durationMs) {
            this.completedFindings = Collections.unmodifiableMap(new LinkedHashMap<>(completedFindings));
            this.erroredRoutines = Collections.unmodifiableMap(new LinkedHashMap<>(erroredRoutines));
            this.totalSubmitted = totalSubmitted;
            this.totalSuccess = completedFindings.size();
            this.totalErrors = erroredRoutines.size();
            this.freeLaneProcessed = freeLaneProcessed;
            this.paidLaneProcessed = paidLaneProcessed;
            this.durationMs = durationMs;
        }

        public boolean isFullySuccessful() {
            boolean result = this.erroredRoutines.isEmpty() && this.totalSuccess == this.totalSubmitted;
            return result;
        }

        public String getSummaryReport() {
            double durationSec = this.durationMs / 1000.0;
            double throughput = (durationSec > 0) ? (this.totalSuccess / durationSec) : 0.0;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("TURBO EXECUTION SUMMARY:%n"));
            sb.append(String.format("  Total Routines Submitted: %d%n", this.totalSubmitted));
            sb.append(String.format("  Completed Successfully:   %d (%.1f%%)%n", this.totalSuccess, (this.totalSubmitted > 0 ? (this.totalSuccess * 100.0 / this.totalSubmitted) : 100.0)));
            sb.append(String.format("  Free Lane Completions:    %d ($0.00 cost)%n", this.freeLaneProcessed));
            sb.append(String.format("  Paid Lane Completions:    %d (Parallel Boost)%n", this.paidLaneProcessed));
            sb.append(String.format("  Errored Routines:         %d%n", this.totalErrors));
            sb.append(String.format("  Execution Time:           %.2fs (Throughput: %.2f routines/sec)%n", durationSec, throughput));
            String result = sb.toString();
            return result;
        }
    }

    public TurboExecutionResult researchRoutinesTurbo(List<RoutineResearchTask> tasks) {
        TurboExecutionResult result = researchRoutinesTurbo(tasks, 4, null);
        return result;
    }

    /**
     * Executes Turbo Dual-Lane concurrent processing by dispatching tasks simultaneously across
     * a dedicated Free lane (15 RPM pacing) and a multi-threaded Paid lane (uncapped parallel speed),
     * backed by a thread-safe coordinator that tracks processed vs errored routines with automated sweep recovery.
     */
    public TurboExecutionResult researchRoutinesTurbo(List<RoutineResearchTask> tasks,
                                                      int paidConcurrency,
                                                      Consumer<EnhancedRoutineResearchResult> progressListener) {
        long startTime = System.currentTimeMillis();
        if (tasks == null || tasks.isEmpty()) {
            TurboExecutionResult emptyResult = new TurboExecutionResult(Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, 0L);
            return emptyResult;
        }

        int totalCount = tasks.size();
        System.out.printf("%n=========================================================================%n");
        System.out.printf("STARTING TURBO DUAL-LANE ASYNC RESEARCH COORDINATOR%n");
        System.out.printf("Total Routines Queued:   %d%n", totalCount);
        System.out.printf("Free Lane:               1 Worker (15 RPM pacing / 4.1s spacing)%n");
        System.out.printf("Paid Lane:               %d Concurrent Workers (Uncapped speed)%n", Math.max(1, paidConcurrency));
        System.out.printf("State Coordination:      Thread-Safe Queue & Error Tracking with Auto-Sweeper%n");
        System.out.printf("=========================================================================%n%n");

        ConcurrentLinkedQueue<RoutineResearchTask> queue = new ConcurrentLinkedQueue<>(tasks);
        ConcurrentHashMap<String, EnhancedRoutineResearchResult> completedMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> errorMap = new ConcurrentHashMap<>();

        AtomicInteger freeLaneCount = new AtomicInteger(0);
        AtomicInteger paidLaneCount = new AtomicInteger(0);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean freeTierExhausted = new AtomicBoolean(false);
        AtomicLong lastFreeCallTimestamp = new AtomicLong(0);

        String effectiveFreeKey = !this.freeApiKey.isEmpty() ? this.freeApiKey : "";
        String effectivePaidKey = !this.paidApiKey.isEmpty() ? this.paidApiKey : this.freeApiKey;

        // Dedicated Free Lane Thread
        Thread freeLaneThread = new Thread(() -> {
            if (effectiveFreeKey.isEmpty()) {
                return;
            }
            while (running.get() && !freeTierExhausted.get()) {
                RoutineResearchTask task = queue.poll();
                if (task == null) {
                    break;
                }
                String lookupKey = task.getLookupKey();

                // Free rate spacing (15 RPM -> 4.1s spacing)
                long now = System.currentTimeMillis();
                long elapsed = now - lastFreeCallTimestamp.get();
                if (lastFreeCallTimestamp.get() > 0 && elapsed < FREE_SPACING_MS) {
                    try {
                        Thread.sleep(FREE_SPACING_MS - elapsed);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                lastFreeCallTimestamp.set(System.currentTimeMillis());

                try {
                    System.out.printf("   [Free-Lane]  Dispatching '%s'...%n", lookupKey);
                    EnhancedRoutineResearchResult res = executeRoutineCallWithKey(task, effectiveFreeKey, true, "FREE-LANE");
                    if (res != null && !res.description.startsWith("WHAT: Pending AI analysis")) {
                        completedMap.put(lookupKey, res);
                        errorMap.remove(lookupKey);
                        int fc = freeLaneCount.incrementAndGet();
                        int currentDone = completedMap.size();
                        System.out.printf("   [Free-Lane]  DONE: %-42s (%d/%d total, Free: %d)%n", lookupKey, currentDone, totalCount, fc);
                        if (progressListener != null) {
                            progressListener.accept(res);
                        }
                    } else {
                        // Rate limit or server error on Free -> re-queue for Paid lane
                        if (!effectivePaidKey.isEmpty() && !effectivePaidKey.equals(effectiveFreeKey)) {
                            System.out.printf("   [Turbo Deferral] Free Lane task '%s' deferred to Paid Lane for instant execution.%n", lookupKey);
                            queue.add(task);
                        } else {
                            errorMap.put(lookupKey, (res != null ? res.description : "Free call failed"));
                        }
                    }
                } catch (Exception ex) {
                    if (ex.getMessage() != null && (ex.getMessage().contains("429") || ex.getMessage().contains("RESOURCE_EXHAUSTED"))) {
                        System.out.printf("   [Free-Lane]  Daily Quota Exhausted (429). Disabling Free Lane, remaining tasks assigned to Paid Lane.%n");
                        freeTierExhausted.set(true);
                        queue.add(task);
                    } else {
                        errorMap.put(lookupKey, ex.getMessage());
                    }
                }
            }
        }, "Turbo-FreeLane-Worker");

        // Paid Lane Worker Pool
        int paidThreads = Math.max(1, Math.min(paidConcurrency, 16));
        ExecutorService paidExecutor = Executors.newFixedThreadPool(paidThreads, new ThreadFactory() {
            private final AtomicInteger tIndex = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Turbo-PaidWorker-" + tIndex.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        CountDownLatch paidLatch = new CountDownLatch(paidThreads);
        for (int i = 0; i < paidThreads; i++) {
            paidExecutor.submit(() -> {
                try {
                    while (running.get()) {
                        RoutineResearchTask task = queue.poll();
                        if (task == null) {
                            break;
                        }
                        String lookupKey = task.getLookupKey();
                        try {
                            String workerName = Thread.currentThread().getName();
                            System.out.printf("   [%s] Dispatching '%s'...%n", workerName, lookupKey);
                            EnhancedRoutineResearchResult res = executeRoutineCallWithKey(task, effectivePaidKey, false, workerName);
                            if (res != null && !res.description.startsWith("WHAT: Pending AI analysis")) {
                                completedMap.put(lookupKey, res);
                                errorMap.remove(lookupKey);
                                int pc = paidLaneCount.incrementAndGet();
                                int currentDone = completedMap.size();
                                System.out.printf("   [%s] DONE: %-42s (%d/%d total, Paid: %d)%n", workerName, lookupKey, currentDone, totalCount, pc);
                                if (progressListener != null) {
                                    progressListener.accept(res);
                                }
                            } else {
                                errorMap.put(lookupKey, (res != null ? res.description : "Paid call failed"));
                            }
                        } catch (Exception ex) {
                            errorMap.put(lookupKey, ex.getMessage());
                        }
                    }
                } finally {
                    paidLatch.countDown();
                }
            });
        }

        // Start Free Lane
        if (!effectiveFreeKey.isEmpty()) {
            freeLaneThread.start();
        }

        // Await Paid completion
        try {
            paidLatch.await(30, TimeUnit.MINUTES);
            running.set(false);
            if (freeLaneThread.isAlive()) {
                freeLaneThread.join(5000);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            paidExecutor.shutdown();
        }

        // Sweeper Recovery Pass: Ensure 100% full coverage for any missing or errored tasks
        List<RoutineResearchTask> uncompletedTasks = new ArrayList<>();
        for (RoutineResearchTask task : tasks) {
            String key = task.getLookupKey();
            if (!completedMap.containsKey(key)) {
                uncompletedTasks.add(task);
            }
        }

        if (!uncompletedTasks.isEmpty()) {
            System.out.printf("%n>>> [TURBO SWEEPER RECOVERY] Found %d uncompleted routine(s). Running guaranteed sweep pass...%n", uncompletedTasks.size());
            String recoveryKey = !effectivePaidKey.isEmpty() ? effectivePaidKey : effectiveFreeKey;
            for (RoutineResearchTask task : uncompletedTasks) {
                String key = task.getLookupKey();
                try {
                    EnhancedRoutineResearchResult res = executeRoutineCallWithKey(task, recoveryKey, recoveryKey.equals(effectiveFreeKey), "SWEEPER-PASS");
                    if (res != null && !res.description.startsWith("WHAT: Pending AI analysis")) {
                        completedMap.put(key, res);
                        errorMap.remove(key);
                        paidLaneCount.incrementAndGet();
                        System.out.printf("   [SWEEPER-PASS] RECOVERED: %s%n", key);
                        if (progressListener != null) {
                            progressListener.accept(res);
                        }
                    } else {
                        EnhancedRoutineResearchResult fallback = EnhancedRoutineResearchResult.createFallback(
                                key, task.schema, task.packageName, task.routineName, "Failed during Turbo Sweeper"
                        );
                        completedMap.put(key, fallback);
                    }
                } catch (Exception ex) {
                    EnhancedRoutineResearchResult fallback = EnhancedRoutineResearchResult.createFallback(
                            key, task.schema, task.packageName, task.routineName, "Sweeper Exception: " + ex.getMessage()
                    );
                    completedMap.put(key, fallback);
                    errorMap.put(key, ex.getMessage());
                }
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        TurboExecutionResult executionResult = new TurboExecutionResult(
                completedMap,
                errorMap,
                totalCount,
                freeLaneCount.get(),
                paidLaneCount.get(),
                elapsedMs
        );
        System.out.printf("%n%s%n", executionResult.getSummaryReport());
        return executionResult;
    }

    public EnhancedRoutineResearchResult executeRoutineCallWithKey(RoutineResearchTask task, String apiKey, boolean isFreeCall, String laneLabel) {
        String lookupKey = task.getLookupKey();
        String currentKey = apiKey;
        boolean free = isFreeCall;

        String prompt = buildPrompt(task.schema, task.packageName, task.routineName, task.routineType, task.sqlSource);
        String requestPayload = buildGeminiRequestBody(prompt);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GEMINI_API_URL + "?key=" + currentKey))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(45))
                        .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    EnhancedRoutineResearchResult parsed = parseGeminiResponse(lookupKey, task.schema, task.packageName, task.routineName, response.body());
                    return parsed;
                } else if (response.statusCode() == 429) {
                    if (free && !this.paidApiKey.isEmpty() && !this.paidApiKey.equals(currentKey)) {
                        System.out.printf("   [%s Failover] Free 429 -> Switching to Paid Key for '%s'...%n", laneLabel, lookupKey);
                        currentKey = this.paidApiKey;
                        free = false;
                        attempt = 0;
                        continue;
                    }
                    long backoff = free ? (RETRY_DELAY_MS * 2 * attempt) : (RETRY_DELAY_MS * attempt);
                    Thread.sleep(backoff);
                } else if (response.statusCode() == 503 || response.statusCode() == 500 || response.statusCode() == 502 || response.statusCode() == 504) {
                    if (free && !this.paidApiKey.isEmpty() && !this.paidApiKey.equals(currentKey)) {
                        System.out.printf("   [%s Failover] Free %d Server Busy -> Switching to Paid Key for '%s'...%n", laneLabel, response.statusCode(), lookupKey);
                        currentKey = this.paidApiKey;
                        free = false;
                        attempt = 0;
                        continue;
                    }
                    long backoff = free ? (RETRY_DELAY_MS * 2 * attempt) : (RETRY_DELAY_MS * attempt);
                    Thread.sleep(backoff);
                } else {
                    if (attempt == MAX_RETRIES) {
                        EnhancedRoutineResearchResult failResult = EnhancedRoutineResearchResult.createFallback(lookupKey, task.schema, task.packageName, task.routineName, "HTTP " + response.statusCode());
                        return failResult;
                    }
                }
            } catch (Exception ex) {
                if (free && !this.paidApiKey.isEmpty() && !this.paidApiKey.equals(currentKey)) {
                    System.out.printf("   [%s Timeout Failover] Free Timeout -> Switching to Paid Key for '%s'...%n", laneLabel, lookupKey);
                    currentKey = this.paidApiKey;
                    free = false;
                    attempt = 0;
                    continue;
                }
                if (attempt == MAX_RETRIES) {
                    EnhancedRoutineResearchResult exResult = EnhancedRoutineResearchResult.createFallback(lookupKey, task.schema, task.packageName, task.routineName, "Exception: " + ex.getMessage());
                    return exResult;
                }
            }
        }

        EnhancedRoutineResearchResult finalResult = EnhancedRoutineResearchResult.createFallback(lookupKey, task.schema, task.packageName, task.routineName, "Max retries exceeded");
        return finalResult;
    }
}
