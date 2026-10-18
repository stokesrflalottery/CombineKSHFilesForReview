package com.fllottery.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * ENHANCED AUTOMATED AI STUDIO VERSION WITH SYSTEM PROXY & NETWORK RESILIENCE:
 * Configures system proxy detection, HTTP/1.1 fallback for corporate firewalls,
 * supports AQ.* and AIzaSy* Auth Keys, tracks live penny cost, and writes Excel inventory.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @since 2026-09-24
 */
public class ExtractAndResearchProcsInventory {

    private static final Set<String> DEFAULT_TARGET_SCHEMAS = new HashSet<>(Arrays.asList(
            "ARS", "COMMON", "CSTS", "DWDOL", "DOLEMP", "GL",
            "INET", "INET2", "LIDS", "LST", "PR", "RETAILER", "STAGE", "TMS", "TRANSLATOR", "VP"
    ));


    /**
    /**
     * Tier Execution Modes:
     * FREE:   100% Free Tier (enforces 15 RPM pacing / 4.1s spacing, 1500 RPD quota safety ceiling, $0.00 cost)
     * PAID:   Enterprise Prepaid / GCP Pay-As-You-Go (Uncapped RPM, zero wait pacing, tracks pennies, stops at spending cap)
     * HYBRID: Smart Cooldown Interleaving (Prioritizes Free Tier for $0 cost; dispatches calls on Paid Tier during Free cooldown to achieve maximum throughput)
     */
    public enum TierMode {
        FREE("FREE (100% Free Tier - 15 RPM pacing, $0.00 cost)"),
        PAID("PAID (Prepaid Enterprise - Uncapped speed, spending cap safety)"),
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

    public static final TierMode DEFAULT_TIER_MODE = TierMode.HYBRID;
    public static final boolean DEFAULT_IS_FREE_API = false;

    /**
     * Spending cap in USD dollars (e.g. 2.00 = $2.00 max, 5.00 = $5.00 max).
     * Set to -1.0 for unlimited execution.
     * Can also be overridden at runtime via CLI switch: -spendingCap=2.50
     */
    public static final double DEFAULT_SPENDING_CAP_USD = .25;

    public static final String  FREE_API_KEY = "AQ.Ab8RN6JhZRpiwfE-IX2rZK-FLdmTuoPzYjq0jEJRGDNsPE0eUw"; //Free
    public static final String  PAID_API_KEY = "AQ.Ab8RN6IJFjhJ9M6ud3riQ5zonSyHEjZ_NwYExlZljPmxWmJi3g"; //Paid    
    
    // =========================================================================
    // USER CONFIGURABLE SWITCHES & SAFETY CONTROLS
    // =========================================================================
    /**
     * Throughput Speed Modes for Gemini API batch research.
     * NORMAL:  1 routine per request (1x) - Maximum granularity & isolation
     * FAST:    Up to 2 routines per request (2x) - Balanced throughput
     * FASTEST: Up to 4 routines per request (4x) - Maximum speed & 4x daily quota conservation
     */
    public enum SpeedMode {
        NORMAL(1, "NORMAL (1x) - 1 routine per request"),
        FAST(2, "FAST (2x) - Up to 2 routines per request (Balanced)"),
        FASTEST(4, "FASTEST (4x) - Up to 4 routines per request (Maximum Speed & 4x Quota Conservation)");

        private final int batchMultiplier;
        private final String description;

        SpeedMode(int batchMultiplier, String description) {
            this.batchMultiplier = batchMultiplier;
            this.description = description;
        }

        public int getBatchMultiplier() {
            return batchMultiplier;
        }

        public String getDescription() {
            return description;
        }

        public static SpeedMode fromString(String val) {
            if (val == null || val.trim().isEmpty()) return DEFAULT_SPEED_MODE;
            String clean = val.trim().toUpperCase();
            if (clean.equals("1") || clean.equals("1X") || clean.contains("NORM")) return NORMAL;
            if (clean.equals("2") || clean.equals("2X") || (clean.contains("FAST") && !clean.contains("FASTEST"))) return FAST;
            if (clean.equals("4") || clean.equals("4X") || clean.equals("3") || clean.equals("3X") || clean.contains("FASTEST") || clean.contains("MAX")) return FASTEST;
            return DEFAULT_SPEED_MODE;
        }
    }

    public static final SpeedMode DEFAULT_SPEED_MODE = SpeedMode.FASTEST;
    private static SpeedMode activeSpeedMode = DEFAULT_SPEED_MODE;

    /**
     * Threshold in SQL characters for Adaptive Batching (~300 lines of SQL).
     * Routines exceeding this length are automatically isolated into dedicated 1x requests.
     */
    public static final int MAX_ADAPTIVE_SQL_CHARS = 12000;
    
    /**
     * Maximum consecutive API call failures allowed before tripping the circuit breaker.
     * Prevents hanging or wasting time when network drops or VPN disconnects mid-batch.
     * Can also be overridden at runtime via CLI switch: -maxFailures=5 or -circuitBreaker=5
     */
    public static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 5;
    // =========================================================================

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final int MAX_SQL_EXCERPT_CHARS = 1500;
    private static final int MAX_EXCEL_CELL_CHARS = 32000;
    private static final float ROW_HEIGHT_POINTS = 14.40f;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    // Ordered Model Candidate Failover Chain (Unlocks 500+ free requests across active model endpoints)
    private static final List<String> MODEL_FAILOVER_CHAIN = Arrays.asList(
            "gemini-3.8-flash",
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-flash-latest",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-flash-lite-latest",
            "gemini-3-flash-preview"
    );
    public static volatile boolean stopRequested = false;
    private static String activeModelName = DEFAULT_MODEL;
    private static String currentActiveApiUrl = "";
    private static String configuredThinkingLevel = "auto";

    public static String getActiveModelName() {
        return activeModelName;
    }

    public static String getConfiguredThinkingLevel() {
        return configuredThinkingLevel;
    }

    public static void setConfiguredThinkingLevel(String level) {
        configuredThinkingLevel = (level != null) ? level.trim().toLowerCase() : "auto";
    }

    public static String resolveAdaptiveThinkingLevel(String routineType, String sqlSource) {
        if (configuredThinkingLevel != null && !configuredThinkingLevel.equalsIgnoreCase("auto")
                && !configuredThinkingLevel.equalsIgnoreCase("default")) {
            String explicitLevel = configuredThinkingLevel;
            return explicitLevel;
        }
        if (sqlSource == null || sqlSource.trim().isEmpty()) {
            String defaultLevel = "medium";
            return defaultLevel;
        }
        String upperSql = sqlSource.toUpperCase();
        int sqlLen = sqlSource.trim().length();

        // High reasoning depth for complex package bodies, multi-cursor loops, autonomous transactions, or massive SQL
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

    public static String getCurrentActiveApiUrl() {
        return currentActiveApiUrl;
    }

    private static EnterpriseProxyResolver.EnterpriseProxyConfig activeProxyConfig = null;

    public static EnterpriseProxyResolver.EnterpriseProxyConfig getActiveProxyConfig() {
        EnterpriseProxyResolver.EnterpriseProxyConfig result = activeProxyConfig;
        return result;
    }

    // =========================================================================
    // DYNAMIC PRICING TELEMETRY (Matches Official Google AI Studio Rate Table)
    // =========================================================================
    public static double resolveInputRatePerMillionUsd(String modelName, long promptTokens) {
        if (modelName != null) {
            String m = modelName.toLowerCase();
            if (m.contains("3.8-flash") || m.contains("3.7-flash") || m.contains("3.6-flash")) {
                return 0.75; // $0.75 / 1M tokens
            }
            if (m.contains("3.5-flash-lite")) {
                return 0.30; // $0.30 / 1M tokens (Official List Price)
            }
            if (m.contains("3.1-flash-lite")) {
                return 0.25; // $0.25 / 1M tokens
            }
            if (m.contains("3.5-flash")) {
                return 1.50; // $1.50 / 1M tokens
            }
            if (m.contains("2.5-flash-lite")) {
                return 0.10; // $0.10 / 1M tokens
            }
            if (m.contains("2.5-flash")) {
                return 0.30; // $0.30 / 1M tokens
            }
            if (m.contains("3.1-pro")) {
                return promptTokens > 200_000 ? 4.00 : 2.00; // $2.00 / 1M (<=200k), $4.00 / 1M (>200k)
            }
            if (m.contains("2.5-pro")) {
                return promptTokens > 200_000 ? 2.50 : 1.25; // $1.25 / 1M (<=200k), $2.50 / 1M (>200k)
            }
        }
        return 0.30; // Default: $0.30 / 1M tokens
    }

    public static double resolveOutputRatePerMillionUsd(String modelName, long promptTokens) {
        if (modelName != null) {
            String m = modelName.toLowerCase();
            if (m.contains("3.8-flash") || m.contains("3.7-flash") || m.contains("3.6-flash")) {
                return 3.75; // $3.75 / 1M tokens
            }
            if (m.contains("3.5-flash-lite")) {
                return 2.50; // $2.50 / 1M tokens (Official List Price)
            }
            if (m.contains("3.1-flash-lite")) {
                return 1.50; // $1.50 / 1M tokens
            }
            if (m.contains("3.5-flash")) {
                return 9.00; // $9.00 / 1M tokens
            }
            if (m.contains("2.5-flash-lite")) {
                return 0.40; // $0.40 / 1M tokens
            }
            if (m.contains("2.5-flash")) {
                return 2.50; // $2.50 / 1M tokens
            }
            if (m.contains("3.1-pro")) {
                return promptTokens > 200_000 ? 18.00 : 12.00; // $12.00 / 1M (<=200k), $18.00 / 1M (>200k)
            }
            if (m.contains("2.5-pro")) {
                return promptTokens > 200_000 ? 15.00 : 10.00; // $10.00 / 1M (<=200k), $15.00 / 1M (>200k)
            }
        }
        return 2.50; // Default: $2.50 / 1M tokens
    }

    public static double resolveInputTokenCostPennies(String modelName) {
        return resolveInputTokenCostPennies(modelName, 0L);
    }

    public static double resolveInputTokenCostPennies(String modelName, long promptTokens) {
        double ratePerMillionUsd = resolveInputRatePerMillionUsd(modelName, promptTokens);
        double penniesPerToken = ratePerMillionUsd / 10000.0;
        return penniesPerToken;
    }

    public static double resolveOutputTokenCostPennies(String modelName) {
        return resolveOutputTokenCostPennies(modelName, 0L);
    }

    public static double resolveOutputTokenCostPennies(String modelName, long promptTokens) {
        double ratePerMillionUsd = resolveOutputRatePerMillionUsd(modelName, promptTokens);
        double penniesPerToken = ratePerMillionUsd / 10000.0;
        return penniesPerToken;
    }

    private static TierMode activeTierMode = DEFAULT_TIER_MODE;
    private static String activeFreeApiKey = FREE_API_KEY;
    private static String activePaidApiKey = PAID_API_KEY;
    public static final boolean DEFAULT_DISPLAY_JSON_BLOCKS = false;
    private static boolean displayJsonBlocks = DEFAULT_DISPLAY_JSON_BLOCKS; // Default false: compact single-line status without dumping multi-line JSON blocks
    private static boolean jsonLogMode = false;      // If true, emits structured JSON log events to console
    private static final ObjectMapper JSON_LOG_MAPPER = new ObjectMapper();

    private static int dailyRequestsUsed = 0;
    private static Integer customDailyQuotaLimit = null;
    public static final int DEFAULT_DAILY_QUOTA_LIMIT = 1500;
    private static final int RPM_LIMIT = 15;
    private static final LinkedList<Long> requestRollingTimestamps = new LinkedList<>();
    private static long lastRequestTimeMs = 0;
    private static long lastFreeRequestTimeMs = 0;

    private static int totalFreeCalls = 0;
    private static int totalPaidCalls = 0;
    private static boolean freeTierExhausted = false;
    private static boolean paidSpendingCapReached = false;
    private static boolean hybridBothExhaustedAnnounced = false;

    private static long totalPromptTokens = 0;
    private static long totalCandidateTokens = 0;
    private static long totalBytesIn = 0;
    private static long totalBytesOut = 0;
    private static double totalCostInPennies = 0.0;

    public static double getTotalCostInUsd() {
        return totalCostInPennies / 100.0;
    }
    private static double activeSpendingCapUsd = DEFAULT_SPENDING_CAP_USD;
    private static boolean spendingCapReached = false;

    // Free Tier Safety Quota Ceiling (Reserve 5% buffer for emergency/interactive calls)
    public static final double DEFAULT_FREE_TIER_SAFETY_PCT = 0.95;
    private static double freeTierSafetyPct = DEFAULT_FREE_TIER_SAFETY_PCT;
    private static boolean quotaSafetyCeilingReached = false;

    // Circuit Breaker State
    private static int maxConsecutiveFailures = DEFAULT_MAX_CONSECUTIVE_FAILURES;
    private static int consecutiveFailures = 0;
    private static boolean circuitBreakerTripped = false;

    private static void emitJsonLog(String level, String event, Map<String, Object> data) {
        if (!jsonLogMode) return;
        try {
            ObjectNode root = JSON_LOG_MAPPER.createObjectNode();
            root.put("timestamp", Instant.now().toString());
            root.put("level", level);
            root.put("event", event);
            if (data != null && !data.isEmpty()) {
                JsonNode dataNode = JSON_LOG_MAPPER.valueToTree(data);
                if (dataNode.isObject()) {
                    root.setAll((ObjectNode) dataNode);
                }
            }
            System.out.println(JSON_LOG_MAPPER.writeValueAsString(root));
        } catch (Exception ex) {
            System.out.printf("{\"timestamp\":\"%s\",\"level\":\"%s\",\"event\":\"%s\"}%n", Instant.now().toString(), level, event);
        }
    }

    // =========================================================================
    // PERSISTENT DISK-BACKED MODEL QUOTA & TELEMETRY TRACKER
    // =========================================================================
    public static class ModelQuotaEntry {
        public String modelName;
        public int requestsToday;
        public int quotaLimit;
        public int remainingQuota;
        public String lastRequestTime;
        public int lastStatusCode;
        public String status; // "AVAILABLE", "EXHAUSTED_429", "SERVER_ERROR", "READY"
        public long totalPromptTokensToday;
        public long totalCandidateTokensToday;
        public String note;

        public ModelQuotaEntry() {}

        public ModelQuotaEntry(String modelName, int quotaLimit) {
            this.modelName = modelName;
            this.quotaLimit = quotaLimit;
            this.requestsToday = 0;
            this.remainingQuota = quotaLimit;
            this.lastRequestTime = "Never";
            this.lastStatusCode = 0;
            this.status = "READY";
            this.totalPromptTokensToday = 0L;
            this.totalCandidateTokensToday = 0L;
            this.note = "";
        }
    }

    public static class QuotaTrackerData {
        public String date; // YYYY-MM-DD in UTC
        public String lastUpdated;
        public String lastActiveModel;
        public Map<String, ModelQuotaEntry> models = new LinkedHashMap<>();
    }

    private static File quotaTrackerFile = null;
    private static QuotaTrackerData quotaTrackerData = new QuotaTrackerData();
    private static final Object QUOTA_TRACKER_LOCK = new Object();

    public static QuotaTrackerData getQuotaTrackerData() {
        synchronized (QUOTA_TRACKER_LOCK) {
            return quotaTrackerData;
        }
    }

    public static int resolveModelDailyQuotaLimit(String modelName) {
        if (customDailyQuotaLimit != null) {
            int result = customDailyQuotaLimit;
            return result;
        }
        if (modelName != null) {
            String m = modelName.toLowerCase();
            if (m.contains("3.6-flash") || m.contains("3.8-flash")) {
                int result = 20; // Exact Google AI Studio Free Tier limit for 3.6 & 3.8 Flash
                return result;
            }
            if (m.contains("flash-lite")) {
                int result = 500; // Exact Google AI Studio Free Tier limit for Flash Lite models
                return result;
            }
        }
        int result = 500;
        return result;
    }

    public static void initQuotaTracker(File file, ObjectMapper mapper) {
        synchronized (QUOTA_TRACKER_LOCK) {
            quotaTrackerFile = file;
            String todayUtc = LocalDate.now(ZoneOffset.UTC).toString();

            if (file != null && file.exists()) {
                try {
                    JsonNode root = mapper.readTree(file);
                    String fileDate = root.path("date").asText("");
                    quotaTrackerData.date = fileDate;
                    quotaTrackerData.lastUpdated = root.path("lastUpdated").asText("");
                    quotaTrackerData.lastActiveModel = root.path("lastActiveModel").asText(DEFAULT_MODEL);

                    boolean isNewDay = !todayUtc.equalsIgnoreCase(fileDate);
                    JsonNode modelsNode = root.path("models");

                    for (String modelName : MODEL_FAILOVER_CHAIN) {
                        int limit = resolveModelDailyQuotaLimit(modelName);
                        ModelQuotaEntry entry = new ModelQuotaEntry(modelName, limit);

                        if (modelsNode.has(modelName)) {
                            JsonNode mNode = modelsNode.get(modelName);
                            if (isNewDay) {
                                // UTC Day rolled over -> reset daily counters
                                entry.requestsToday = 0;
                                entry.remainingQuota = limit;
                                entry.status = "AVAILABLE";
                                entry.lastRequestTime = mNode.path("lastRequestTime").asText("Never");
                                entry.lastStatusCode = 200;
                                entry.totalPromptTokensToday = 0L;
                                entry.totalCandidateTokensToday = 0L;
                                entry.note = "Daily quota reset for " + todayUtc + " UTC";
                            } else {
                                entry.requestsToday = mNode.path("requestsToday").asInt(0);
                                entry.quotaLimit = limit;
                                entry.remainingQuota = Math.max(0, limit - entry.requestsToday);
                                entry.lastRequestTime = mNode.path("lastRequestTime").asText("Never");
                                entry.lastStatusCode = mNode.path("lastStatusCode").asInt(0);
                                entry.status = mNode.path("status").asText("AVAILABLE");
                                entry.totalPromptTokensToday = mNode.path("totalPromptTokensToday").asLong(0L);
                                entry.totalCandidateTokensToday = mNode.path("totalCandidateTokensToday").asLong(0L);
                                entry.note = mNode.path("note").asText("");
                            }
                        }
                        quotaTrackerData.models.put(modelName, entry);
                    }

                    if (isNewDay) {
                        quotaTrackerData.date = todayUtc;
                        quotaTrackerData.lastUpdated = Instant.now().toString();
                        saveQuotaTracker(mapper);
                    }
                } catch (Exception ex) {
                    System.err.printf("[QuotaTracker] Could not load %s -> Initializing fresh state: %s%n", file.getName(), ex.getMessage());
                    initFreshQuotaTrackerData(todayUtc);
                    saveQuotaTracker(mapper);
                }
            } else {
                initFreshQuotaTrackerData(todayUtc);
                saveQuotaTracker(mapper);
            }

            // Sync active daily requests used from tracker
            if (quotaTrackerData.models.containsKey(activeModelName)) {
                dailyRequestsUsed = quotaTrackerData.models.get(activeModelName).requestsToday;
            }
        }
    }

    private static void initFreshQuotaTrackerData(String todayUtc) {
        quotaTrackerData.date = todayUtc;
        quotaTrackerData.lastUpdated = Instant.now().toString();
        quotaTrackerData.lastActiveModel = DEFAULT_MODEL;
        quotaTrackerData.models.clear();
        for (String modelName : MODEL_FAILOVER_CHAIN) {
            int limit = resolveModelDailyQuotaLimit(modelName);
            quotaTrackerData.models.put(modelName, new ModelQuotaEntry(modelName, limit));
        }
    }

    private static void saveQuotaTracker(ObjectMapper mapper) {
        if (quotaTrackerFile == null) return;
        try {
            if (quotaTrackerFile.getParentFile() != null && !quotaTrackerFile.getParentFile().exists()) {
                quotaTrackerFile.getParentFile().mkdirs();
            }
            mapper.writeValue(quotaTrackerFile, quotaTrackerData);
        } catch (Exception ex) {
            System.err.printf("[QuotaTracker] Warning: Failed to persist quota tracker JSON: %s%n", ex.getMessage());
        }
    }

    public static void recordModelRequest(String modelName, int statusCode, long promptTokens, long candidateTokens, ObjectMapper mapper) {
        synchronized (QUOTA_TRACKER_LOCK) {
            String todayUtc = LocalDate.now(ZoneOffset.UTC).toString();
            if (!todayUtc.equalsIgnoreCase(quotaTrackerData.date)) {
                initFreshQuotaTrackerData(todayUtc);
            }

            ModelQuotaEntry entry = quotaTrackerData.models.computeIfAbsent(modelName, k -> new ModelQuotaEntry(k, resolveModelDailyQuotaLimit(k)));
            entry.requestsToday++;
            entry.lastRequestTime = Instant.now().toString();
            entry.lastStatusCode = statusCode;
            entry.totalPromptTokensToday += promptTokens;
            entry.totalCandidateTokensToday += candidateTokens;
            entry.remainingQuota = Math.max(0, entry.quotaLimit - entry.requestsToday);

            if (statusCode == 200) {
                entry.status = "AVAILABLE";
                entry.note = "Active & verified operational";
            } else if (statusCode == 429) {
                entry.status = "EXHAUSTED_429";
                entry.note = "Daily quota ceiling reached (HTTP 429)";
            }

            quotaTrackerData.lastActiveModel = modelName;
            quotaTrackerData.lastUpdated = Instant.now().toString();
            dailyRequestsUsed = entry.requestsToday;
            saveQuotaTracker(mapper);
        }
    }

    public static void recordModel429Exhaustion(String modelName, ObjectMapper mapper) {
        synchronized (QUOTA_TRACKER_LOCK) {
            ModelQuotaEntry entry = quotaTrackerData.models.computeIfAbsent(modelName, k -> new ModelQuotaEntry(k, resolveModelDailyQuotaLimit(k)));
            entry.status = "EXHAUSTED_429";
            entry.lastStatusCode = 429;
            entry.lastRequestTime = Instant.now().toString();
            entry.requestsToday = Math.max(entry.requestsToday, entry.quotaLimit);
            entry.remainingQuota = 0;
            entry.note = "Daily quota exhausted on " + LocalDate.now(ZoneOffset.UTC) + " (HTTP 429)";
            quotaTrackerData.lastUpdated = Instant.now().toString();
            saveQuotaTracker(mapper);
        }
    }

    public static boolean isModelExhaustedToday(String modelName) {
        synchronized (QUOTA_TRACKER_LOCK) {
            ModelQuotaEntry entry = quotaTrackerData.models.get(modelName);
            if (entry != null && "EXHAUSTED_429".equalsIgnoreCase(entry.status)) {
                boolean result = true;
                return result;
            }
            boolean result = false;
            return result;
        }
    }

    public static int getModelRequestsUsedToday(String modelName) {
        synchronized (QUOTA_TRACKER_LOCK) {
            ModelQuotaEntry entry = quotaTrackerData.models.get(modelName);
            if (entry != null) {
                int result = entry.requestsToday;
                return result;
            }
            int result = 0;
            return result;
        }
    }

    public static class QuotaResetInfo {
        public final String formattedCountdown;
        public final String resetTimeUtc;
        public final String resetTimeLocal;
        public final long hoursRemaining;
        public final long minutesRemaining;
        public final long secondsRemaining;

        public QuotaResetInfo(String formattedCountdown, String resetTimeUtc, String resetTimeLocal, long hours, long mins, long secs) {
            this.formattedCountdown = formattedCountdown;
            this.resetTimeUtc = resetTimeUtc;
            this.resetTimeLocal = resetTimeLocal;
            this.hoursRemaining = hours;
            this.minutesRemaining = mins;
            this.secondsRemaining = secs;
        }
    }

    public static QuotaResetInfo getQuotaResetInfo() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime nextResetUtc = nowUtc.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC);
        Duration duration = Duration.between(nowUtc, nextResetUtc);

        long totalSeconds = Math.max(0, duration.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
        String utcStr = nextResetUtc.format(utcFormatter);

        ZoneId localZone = ZoneId.systemDefault();
        ZonedDateTime nextResetLocal = nextResetUtc.withZoneSameInstant(localZone);
        DateTimeFormatter localFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a z");
        String localStr = nextResetLocal.format(localFormatter);

        String countdown = String.format("%dh %02dm %02ds", hours, minutes, seconds);
        QuotaResetInfo result = new QuotaResetInfo(countdown, utcStr, localStr, hours, minutes, seconds);
        return result;
    }

    public static void printStartupQuotaSummaryTable() {
        synchronized (QUOTA_TRACKER_LOCK) {
            QuotaResetInfo resetInfo = getQuotaResetInfo();
            System.out.println("+-------------------------------------------------------------------------------------------------------------------------+");
            System.out.printf("| AI STUDIO MODEL QUOTA & AVAILABILITY TRACKER (Date: %-10s UTC)                                                  |%n",
                    (quotaTrackerData.date != null ? quotaTrackerData.date : LocalDate.now(ZoneOffset.UTC).toString()));
            System.out.println("+-----------------------+------------+------------+-----------+--------+--------------------------+-----------------------+");
            System.out.println("| Model Candidate       | Daily Limit| Used Today | Remaining | Left % | Last Request Sent (UTC)  | Status                |");
            System.out.println("+-----------------------+------------+------------+-----------+--------+--------------------------+-----------------------+");

            for (String modelName : MODEL_FAILOVER_CHAIN) {
                ModelQuotaEntry entry = quotaTrackerData.models.get(modelName);
                int limit = (entry != null) ? entry.quotaLimit : resolveModelDailyQuotaLimit(modelName);
                boolean isExhausted = (entry != null && "EXHAUSTED_429".equalsIgnoreCase(entry.status));
                int used = (entry != null) ? (isExhausted ? Math.max(entry.requestsToday, limit) : entry.requestsToday) : 0;
                int remaining = isExhausted ? 0 : Math.max(0, limit - used);
                double pct = (limit > 0) ? ((double) remaining / limit) * 100.0 : 0.0;
                String lastTime = (entry != null && entry.lastRequestTime != null && !entry.lastRequestTime.equalsIgnoreCase("Never"))
                        ? entry.lastRequestTime.replace("T", " ").replace("Z", "")
                        : "Never";
                if (lastTime.length() > 24) lastTime = lastTime.substring(0, 24);

                String status = (entry != null && entry.status != null) ? entry.status : "READY";
                if ("EXHAUSTED_429".equalsIgnoreCase(status)) {
                    status = "EXHAUSTED (429)";
                } else if ("AVAILABLE".equalsIgnoreCase(status)) {
                    status = "AVAILABLE (200)";
                }

                System.out.printf("| %-21s | %,6d RPD | %,6d req | %,6d req| %5.1f%% | %-24s | %-21s |%n",
                        modelName, limit, used, remaining, pct, lastTime, status);
            }
            System.out.println("+-----------------------+------------+------------+-----------+--------+--------------------------+-----------------------+");
            System.out.printf("| NEXT DAILY RESET: %-23s (%-26s) -> in %-18s |%n",
                    resetInfo.resetTimeUtc, resetInfo.resetTimeLocal, resetInfo.formattedCountdown);
            if (activeTierMode != TierMode.PAID) {
                System.out.printf("| SAFETY CEILING:   %-23s (%-47s) |%n",
                        String.format("%.0f%% Quota Cap Active", freeTierSafetyPct * 100.0),
                        String.format("Preserving %.0f%% emergency reserve on candidate models", (1.0 - freeTierSafetyPct) * 100.0));
            }
            System.out.println("+-------------------------------------------------------------------------------------------------------------------------+");
            if (quotaTrackerFile != null) {
                System.out.println("Quota Tracker Cache: " + quotaTrackerFile.getAbsolutePath());
            }
            System.out.println("=========================================================================");
        }
    }

    private static int getActiveDailyQuotaLimit() {
        if (customDailyQuotaLimit != null) {
            int result = customDailyQuotaLimit;
            return result;
        }
        int result = resolveModelDailyQuotaLimit(activeModelName);
        return result;
    }

    private static int getActiveRpmLimit() {
        if (activeModelName != null && activeModelName.toLowerCase().contains("3.6-flash")) {
            int limit = 5;
            return limit;
        }
        int limit = RPM_LIMIT;
        return limit;
    }

    private static long getMinSpacingMs() {
        if (activeModelName != null && activeModelName.toLowerCase().contains("3.6-flash")) {
            long spacing = 12200L; // 5 RPM free tier requires >= 12.0s spacing
            return spacing;
        }
        long spacing = 4100L; // 15 RPM allows 4.1s spacing
        return spacing;
    }

    public static class ResolvedCallRoute {
        public final boolean isFreeCall;
        public final String apiKey;
        public final String routeLabel;

        public ResolvedCallRoute(boolean isFreeCall, String apiKey, String routeLabel) {
            this.isFreeCall = isFreeCall;
            this.apiKey = apiKey;
            this.routeLabel = routeLabel;
        }
    }

    public static synchronized ResolvedCallRoute resolveCallRoute() {
        if (activeTierMode == TierMode.FREE) {
            ResolvedCallRoute route = new ResolvedCallRoute(true, activeFreeApiKey, "FREE");
            return route;
        }
        if (activeTierMode == TierMode.PAID) {
            ResolvedCallRoute route = new ResolvedCallRoute(false, activePaidApiKey, "PAID");
            return route;
        }
        if (activeTierMode == TierMode.TURBO) {
            long now = System.currentTimeMillis();
            long minSpacing = getMinSpacingMs();
            int effectiveRpmLimit = getActiveRpmLimit();

            boolean freeExhaustedOrCeiling = freeTierExhausted || isModelAtSafetyCeiling(activeModelName);
            boolean freeInCooldown = (lastFreeRequestTimeMs > 0 && (now - lastFreeRequestTimeMs) < minSpacing) || (getCurrentRpm() >= (effectiveRpmLimit - 1));
            boolean paidAvailable = !paidSpendingCapReached && !activePaidApiKey.isEmpty() && (activeSpendingCapUsd <= 0.0 || (totalCostInPennies / 100.0) < activeSpendingCapUsd);

            if (!freeExhaustedOrCeiling && !freeInCooldown && !activeFreeApiKey.isEmpty()) {
                ResolvedCallRoute route = new ResolvedCallRoute(true, activeFreeApiKey, "TURBO: FREE LANE");
                return route;
            } else if (paidAvailable) {
                ResolvedCallRoute route = new ResolvedCallRoute(false, activePaidApiKey, "TURBO: PAID LANE (Async Concurrent)");
                return route;
            } else if (!freeExhaustedOrCeiling && !activeFreeApiKey.isEmpty()) {
                ResolvedCallRoute route = new ResolvedCallRoute(true, activeFreeApiKey, "TURBO: FREE LANE (Paid Capped)");
                return route;
            } else {
                ResolvedCallRoute route = new ResolvedCallRoute(true, activeFreeApiKey, "TURBO: EXHAUSTED");
                return route;
            }
        }

        // TierMode.HYBRID: Smart Cooldown Interleaving
        long now = System.currentTimeMillis();
        long minSpacing = getMinSpacingMs();
        int effectiveRpmLimit = getActiveRpmLimit();

        boolean freeExhaustedOrCeiling = freeTierExhausted || isModelAtSafetyCeiling(activeModelName);
        boolean freeInCooldown = (lastFreeRequestTimeMs > 0 && (now - lastFreeRequestTimeMs) < minSpacing) || (getCurrentRpm() >= (effectiveRpmLimit - 1));
        boolean paidAvailable = !paidSpendingCapReached && !activePaidApiKey.isEmpty() && (activeSpendingCapUsd <= 0.0 || (totalCostInPennies / 100.0) < activeSpendingCapUsd);

        if (!freeExhaustedOrCeiling && !freeInCooldown && !activeFreeApiKey.isEmpty()) {
            ResolvedCallRoute route = new ResolvedCallRoute(true, activeFreeApiKey, "HYBRID: FREE");
            return route;
        } else if (paidAvailable) {
            String label = !freeExhaustedOrCeiling ? "HYBRID: PAID (Cooldown Speedup)" : "HYBRID: PAID (Free Quota Exhausted Failover)";
            ResolvedCallRoute route = new ResolvedCallRoute(false, activePaidApiKey, label);
            return route;
        } else if (!freeExhaustedOrCeiling && !activeFreeApiKey.isEmpty()) {
            ResolvedCallRoute route = new ResolvedCallRoute(true, activeFreeApiKey, "HYBRID: FREE (Paid Cap Reached)");
            return route;
        } else {
            ResolvedCallRoute route = new ResolvedCallRoute(true, activeFreeApiKey, "HYBRID: EXHAUSTED");
            return route;
        }
    }

    private static boolean isSpendingCapReached(double spendingCapUsd) {
        if (spendingCapUsd <= 0.0) return false;
        boolean reached = (totalCostInPennies / 100.0) >= spendingCapUsd;
        if (reached && !spendingCapReached) {
            paidSpendingCapReached = true;
            System.out.printf("%n>>> [SPENDING CAP REACHED] Safety Limit of $%.2f USD reached (Spent: $%.4f USD).%n",
                    spendingCapUsd, (totalCostInPennies / 100.0));
            spendingCapReached = true;
        }
        return reached;
    }

    public static int getModelSafetyQuotaLimit(String modelName) {
        int hardLimit = resolveModelDailyQuotaLimit(modelName);
        if (activeTierMode == TierMode.PAID) {
            return hardLimit;
        }
        int safetyLimit = (int) Math.floor(hardLimit * freeTierSafetyPct);
        return Math.max(1, safetyLimit);
    }

    public static boolean isModelAtSafetyCeiling(String modelName) {
        if (activeTierMode == TierMode.PAID) return false;
        int used = getModelRequestsUsedToday(modelName);
        int safetyLimit = getModelSafetyQuotaLimit(modelName);
        return used >= safetyLimit;
    }

    private static boolean isFreeTierQuotaSafetyReached() {
        if (activeTierMode == TierMode.PAID) return false;
        if (isModelAtSafetyCeiling(activeModelName)) {
            if (!quotaSafetyCeilingReached) {
                quotaSafetyCeilingReached = true;
                int hardLimit = resolveModelDailyQuotaLimit(activeModelName);
                int used = getModelRequestsUsedToday(activeModelName);
                int bufferLeft = Math.max(0, hardLimit - used);
                System.out.printf("%n>>> [FREE TIER 95%% SAFETY CEILING REACHED] Used %d/%d daily requests for model '%s' (%.0f%% limit reached).%n",
                        used, hardLimit, activeModelName, (freeTierSafetyPct * 100.0));
                System.out.printf(">>> Preserving %d requests as an emergency buffer for today.%n", bufferLeft);
                if (activeTierMode == TierMode.FREE) {
                    System.out.printf(">>> Safely saving all researched data and generating Excel workbook...%n%n");
                } else {
                    System.out.printf(">>> [Hybrid Routing] Automatically transitioning remaining requests to Prepaid Tier...%n%n");
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isSafetyCeilingReached(double spendingCapUsd) {
        if (activeTierMode == TierMode.FREE) {
            boolean reached = isFreeTierQuotaSafetyReached();
            return reached;
        }
        if (activeTierMode == TierMode.PAID) {
            boolean reached = isSpendingCapReached(spendingCapUsd);
            return reached;
        }
        // TierMode.HYBRID: Stop ONLY when BOTH Free quota is at ceiling and Paid spending cap is reached
        boolean freeBlocked = isModelAtSafetyCeiling(activeModelName) || freeTierExhausted;
        boolean paidBlocked = isSpendingCapReached(spendingCapUsd) || activePaidApiKey.isEmpty();
        if (freeBlocked && paidBlocked) {
            if (!hybridBothExhaustedAnnounced) {
                hybridBothExhaustedAnnounced = true;
                System.out.printf("%n>>> [HYBRID COMPLETE STOP] Both Free Tier daily safety quota and Paid Tier spending cap ($%.2f) have been reached.%n", spendingCapUsd);
                System.out.printf(">>> Safely saving all researched data and generating Excel workbook...%n%n");
            }
            return true;
        }
        return false;
    }

    private static void updateCircuitBreaker(RoutineFinding finding) {
        if (isPendingOrFailed(finding)) {
            consecutiveFailures++;
            if (consecutiveFailures >= maxConsecutiveFailures && !circuitBreakerTripped) {
                circuitBreakerTripped = true;
                if (jsonLogMode) {
                    Map<String, Object> errData = new LinkedHashMap<>();
                    errData.put("consecutiveFailures", consecutiveFailures);
                    errData.put("message", "Sustained network loss or service disruption suspected. Halting further API calls.");
                    emitJsonLog("ERROR", "CIRCUIT_BREAKER_TRIPPED", errData);
                } else {
                    System.out.printf("%n-------------------------------------------------------------------------%n");
                    System.out.printf(">>> [CIRCUIT BREAKER TRIPPED] %d consecutive API connection failures detected.%n", consecutiveFailures);
                    System.out.println(">>> Sustained network loss, VPN disconnect, or service disruption suspected.");
                    System.out.println(">>> Halting further API calls to prevent hanging.");
                    System.out.println(">>> Automatically preserving all cached research and generating Excel workbook...");
                    System.out.printf("-------------------------------------------------------------------------%n%n");
                }
            }
        } else {
            consecutiveFailures = 0; // Reset failure counter on any successful response
        }
    }

    private static synchronized void enforceAdaptiveBurstGuard() {
        long now = System.currentTimeMillis();
        int effectiveRpmLimit = getActiveRpmLimit();
        long minSpacing = getMinSpacingMs();

        // 1. Spacing Guard: Ensures proper spacing to prevent sub-minute burst rejections
        long timeSinceLast = now - lastFreeRequestTimeMs;
        if (lastFreeRequestTimeMs > 0 && timeSinceLast < minSpacing) {
            long spaceWait = minSpacing - timeSinceLast;
            try {
                Thread.sleep(spaceWait);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            now = System.currentTimeMillis();
        }

        // 2. Sliding 60-Second Window Eviction
        while (!requestRollingTimestamps.isEmpty() && (now - requestRollingTimestamps.peekFirst()) > 60000) {
            requestRollingTimestamps.pollFirst();
        }

        // 3. Sliding 60-Second Ceiling (<= limit - 1 in 60s)
        if (requestRollingTimestamps.size() >= (effectiveRpmLimit - 1)) {
            long oldest = requestRollingTimestamps.peekFirst();
            long waitMs = 60000 - (now - oldest) + 500;
            if (waitMs > 0) {
                if (jsonLogMode) {
                    Map<String, Object> paceData = new LinkedHashMap<>();
                    paceData.put("pacingSeconds", Math.round((waitMs / 1000.0) * 10.0) / 10.0);
                    paceData.put("currentRpm", requestRollingTimestamps.size());
                    paceData.put("rpmLimit", effectiveRpmLimit);
                    emitJsonLog("INFO", "BURST_GUARD_PACING", paceData);
                } else {
                    System.out.printf("   [Burst Guard] Approaching %d RPM limit -> Pacing request for %.1fs... ", effectiveRpmLimit, (waitMs / 1000.0));
                }
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                if (!jsonLogMode) {
                    System.out.println("Resuming.");
                }
            }
        }

        lastFreeRequestTimeMs = System.currentTimeMillis();
        lastRequestTimeMs = lastFreeRequestTimeMs;
        requestRollingTimestamps.addLast(lastFreeRequestTimeMs);
        dailyRequestsUsed++;
    }

    private static synchronized int getCurrentRpm() {
        long now = System.currentTimeMillis();
        while (!requestRollingTimestamps.isEmpty() && (now - requestRollingTimestamps.peekFirst()) > 60000) {
            requestRollingTimestamps.pollFirst();
        }
        int rpm = requestRollingTimestamps.size();
        return rpm;
    }

    private static boolean isPendingOrFailed(RoutineFinding finding) {
        if (finding == null) return true;
        if (finding.what != null && (finding.what.contains("Pending Technical Review") || finding.what.contains("Pending AI analysis"))) return true;
        if (finding.description != null && (finding.description.contains("Pending Technical Review") || finding.description.contains("Pending AI analysis"))) return true;
        return false;
    }

    public static void main(String[] args) {
        // Enforce IPv4 on Windows and automatically inherit Windows Internet Proxy configuration
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.useSystemProxies", "true");

        Map<String, String> argMap = parseCommandLineArgs(args);

        String modelName = argMap.getOrDefault("model", DEFAULT_MODEL);
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";

        String inputPath = argMap.getOrDefault("input", "C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json");
        String schemasDirPath = argMap.getOrDefault("schemasDir", "C:\\ocioldapps1\\Cached\\DbCache\\Schemas");
        String excelOutputPath = schemasDirPath + "\\Database_Routines_Inventory.xlsx";
        String catalogJsonOutputPath = schemasDirPath + "\\DbCache_Ai_Researched_Catalog.json";

        String envFreeKey = System.getenv("GEMINI_FREE_API_KEY");
        if (envFreeKey == null || envFreeKey.trim().isEmpty()) envFreeKey = System.getProperty("GEMINI_FREE_API_KEY", "");
        String envPaidKey = System.getenv("GEMINI_PAID_API_KEY");
        if (envPaidKey == null || envPaidKey.trim().isEmpty()) envPaidKey = System.getProperty("GEMINI_PAID_API_KEY", "");
        String envDefaultKey = System.getenv("GEMINI_API_KEY");
        if (envDefaultKey == null || envDefaultKey.trim().isEmpty()) envDefaultKey = System.getProperty("GEMINI_API_KEY", "");

        String freeKeyArg = argMap.getOrDefault("freeKey", argMap.getOrDefault("freeApiKey", ""));
        String paidKeyArg = argMap.getOrDefault("paidKey", argMap.getOrDefault("paidApiKey", ""));
        String genericKeyArg = argMap.getOrDefault("apiKey", argMap.getOrDefault("key", ""));

        activeFreeApiKey = !freeKeyArg.isEmpty() ? freeKeyArg : (!envFreeKey.isEmpty() ? envFreeKey : (!genericKeyArg.isEmpty() ? genericKeyArg : (!envDefaultKey.isEmpty() ? envDefaultKey : FREE_API_KEY)));
        activePaidApiKey = !paidKeyArg.isEmpty() ? paidKeyArg : (!envPaidKey.isEmpty() ? envPaidKey : (!genericKeyArg.isEmpty() ? genericKeyArg : (!envDefaultKey.isEmpty() ? envDefaultKey : PAID_API_KEY)));

        String tierArg = argMap.getOrDefault("tier", "");
        if (argMap.containsKey("isFreeApi")) {
            boolean isFree = Boolean.parseBoolean(argMap.get("isFreeApi").trim());
            activeTierMode = isFree ? TierMode.FREE : TierMode.PAID;
        } else if (!tierArg.isEmpty()) {
            activeTierMode = TierMode.fromString(tierArg);
        } else {
            activeTierMode = DEFAULT_TIER_MODE;
        }

        if (argMap.containsKey("speed")) {
            activeSpeedMode = SpeedMode.fromString(argMap.get("speed"));
        } else if (argMap.containsKey("batch")) {
            activeSpeedMode = SpeedMode.fromString(argMap.get("batch"));
        } else {
            activeSpeedMode = DEFAULT_SPEED_MODE;
        }

        if (argMap.containsKey("jsonBlocks")) {
            displayJsonBlocks = Boolean.parseBoolean(argMap.get("jsonBlocks").trim());
        } else if (argMap.containsKey("json")) {
            displayJsonBlocks = Boolean.parseBoolean(argMap.get("json").trim());
        }

        if (argMap.containsKey("thinkingLevel")) {
            configuredThinkingLevel = argMap.get("thinkingLevel").trim().toLowerCase();
        } else if (argMap.containsKey("thinking")) {
            configuredThinkingLevel = argMap.get("thinking").trim().toLowerCase();
        }

        double spendingCapUsd = DEFAULT_SPENDING_CAP_USD;
        if (argMap.containsKey("spendingCap")) {
            try {
                spendingCapUsd = Double.parseDouble(argMap.get("spendingCap").replace("$", "").trim());
            } catch (NumberFormatException ignored) {}
        }
        activeSpendingCapUsd = spendingCapUsd;

        if (argMap.containsKey("maxFailures")) {
            try {
                maxConsecutiveFailures = Integer.parseInt(argMap.get("maxFailures").trim());
            } catch (NumberFormatException ignored) {}
        } else if (argMap.containsKey("circuitBreaker")) {
            try {
                maxConsecutiveFailures = Integer.parseInt(argMap.get("circuitBreaker").trim());
            } catch (NumberFormatException ignored) {}
        }

        if (argMap.containsKey("rpd")) {
            try {
                customDailyQuotaLimit = Integer.parseInt(argMap.get("rpd").trim());
            } catch (NumberFormatException ignored) {}
        } else if (argMap.containsKey("dailyLimit")) {
            try {
                customDailyQuotaLimit = Integer.parseInt(argMap.get("dailyLimit").trim());
            } catch (NumberFormatException ignored) {}
        } else if (argMap.containsKey("quota")) {
            try {
                customDailyQuotaLimit = Integer.parseInt(argMap.get("quota").trim());
            } catch (NumberFormatException ignored) {}
        }

        if (argMap.containsKey("safetyPct")) {
            try {
                double pct = Double.parseDouble(argMap.get("safetyPct").replace("%", "").trim());
                freeTierSafetyPct = (pct > 1.0) ? (pct / 100.0) : pct;
            } catch (NumberFormatException ignored) {}
        } else if (argMap.containsKey("quotaPct")) {
            try {
                double pct = Double.parseDouble(argMap.get("quotaPct").replace("%", "").trim());
                freeTierSafetyPct = (pct > 1.0) ? (pct / 100.0) : pct;
            } catch (NumberFormatException ignored) {}
        } else if (argMap.containsKey("bufferPct")) {
            try {
                double pct = Double.parseDouble(argMap.get("bufferPct").replace("%", "").trim());
                double buf = (pct > 1.0) ? (pct / 100.0) : pct;
                freeTierSafetyPct = Math.max(0.01, 1.0 - buf);
            } catch (NumberFormatException ignored) {}
        }

        if (argMap.containsKey("jsonLogs") || argMap.containsKey("jsonLog") || argMap.containsKey("logFormat") || argMap.containsKey("jsonConsole")) {
            String val = argMap.getOrDefault("jsonLogs", argMap.getOrDefault("jsonLog", argMap.getOrDefault("logFormat", argMap.getOrDefault("jsonConsole", "true"))));
            jsonLogMode = val.equalsIgnoreCase("true") || val.equalsIgnoreCase("json") || val.equalsIgnoreCase("1");
        }

        Set<String> targetSchemas = resolveTargetSchemas(argMap);
        boolean forceReResearch = Boolean.parseBoolean(argMap.getOrDefault("force", argMap.getOrDefault("refresh", "false")));
        boolean refreshDiagrams = Boolean.parseBoolean(argMap.getOrDefault("refreshDiagrams", argMap.getOrDefault("diagramsOnly", "false")));

        File inputFile = new File(inputPath);
        File schemasDir = new File(schemasDirPath);
        File catalogJsonFile = new File(catalogJsonOutputPath);
        String quotaTrackerOutputPath = schemasDirPath + "\\Ai_Model_Quota_Tracker.json";
        File quotaTrackerJsonFile = new File(argMap.getOrDefault("quotaTracker", argMap.getOrDefault("quotaJson", quotaTrackerOutputPath)));

        if (!inputFile.exists()) {
            System.err.println("ACTION REQUIRED: Source DbCache.json not found at: " + inputPath);
            return;
        }

        if (!schemasDir.exists()) {
            schemasDir.mkdirs();
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Initialize persistent disk-backed Quota and Model state tracker
        initQuotaTracker(quotaTrackerJsonFile, mapper);

        // Configure robust HttpClient with Florida Lottery enterprise proxy support
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30));

        EnterpriseProxyResolver.EnterpriseProxyConfig proxyConfig = EnterpriseProxyResolver.resolve(argMap);
        activeProxyConfig = proxyConfig;
        EnterpriseProxyResolver.applyConfiguration(proxyConfig);
        EnterpriseProxyResolver.configureHttpClientBuilder(clientBuilder);

        if (proxyConfig.isEnabled()) {
            System.out.println("Network Routing: Florida Lottery Enterprise Proxy Active -> " + proxyConfig.getHost() + ":" + proxyConfig.getPort() + " (Source: " + proxyConfig.getResolutionSource() + ")");
        } else {
            System.out.println("Network Routing: Direct Connection (No Enterprise Proxy Configured)");
        }

        HttpClient httpClient = clientBuilder.build();

        try {
            System.out.println("=========================================================================");
            if (activeTierMode == TierMode.TURBO) {
                System.out.println("Florida Lottery Stored Procedure Auto-Researcher [TURBO DUAL-LANE ASYNC COORDINATOR]");
                System.out.println("Input Source:    " + inputPath + " (" + (inputFile.length() / (1024 * 1024)) + " MB)");
                System.out.println("AI Catalog JSON: " + catalogJsonOutputPath);
                System.out.println("Quota Tracker:   " + quotaTrackerJsonFile.getAbsolutePath());
                System.out.println("Excel Catalog:   " + excelOutputPath);
                System.out.println("Free Auth Key:   " + (activeFreeApiKey.isEmpty() ? "[MISSING - Offline Mode]" : "[AI Studio Free Key: " + activeFreeApiKey.substring(0, Math.min(8, activeFreeApiKey.length())) + "...]"));
                System.out.println("Paid Auth Key:   " + (activePaidApiKey.isEmpty() ? "[MISSING - Offline Mode]" : "[Prepaid GCP Key: " + activePaidApiKey.substring(0, Math.min(8, activePaidApiKey.length())) + "...]"));
                System.out.println("Target Schemas:  " + String.join(", ", targetSchemas));
                System.out.println("Concurrency:     Lane 1 (Free Worker: 15 RPM) + Lane 2 (Paid Workers: Parallel Async)");
                System.out.println("Fault Tolerance: Auto-Requeue on 503/429/Timeouts + Dedicated Error Registry");
                System.out.println("Spending Cap:    " + (spendingCapUsd > 0 ? String.format("$%.2f USD (Safety Cutoff Active)", spendingCapUsd) : "Unlimited ($0.00 ceiling)"));
                System.out.printf("Speed Mode:      %s%n", activeSpeedMode.getDescription());
                System.out.printf("Circuit Breaker: Auto-Stop on %d Consecutive API Connection Failures%n", maxConsecutiveFailures);
                System.out.println("Network Mode:    System Proxy Enabled (HTTP/1.1 Enterprise Compatibility)");
                System.out.println("Console Format:  " + (displayJsonBlocks ? "Interactive Pretty-Printed JSON Blocks (Multi-Line)" : "Compact Single-Line Status"));
            } else if (activeTierMode == TierMode.HYBRID) {
                System.out.println("Florida Lottery Stored Procedure Auto-Researcher [HYBRID SMART COOLDOWN ROUTING]");
                System.out.println("Input Source:    " + inputPath + " (" + (inputFile.length() / (1024 * 1024)) + " MB)");
                System.out.println("AI Catalog JSON: " + catalogJsonOutputPath);
                System.out.println("Quota Tracker:   " + quotaTrackerJsonFile.getAbsolutePath());
                System.out.println("Excel Catalog:   " + excelOutputPath);
                System.out.println("Free Auth Key:   " + (activeFreeApiKey.isEmpty() ? "[MISSING - Offline Mode]" : "[AI Studio Free Key: " + activeFreeApiKey.substring(0, Math.min(8, activeFreeApiKey.length())) + "...]"));
                System.out.println("Paid Auth Key:   " + (activePaidApiKey.isEmpty() ? "[MISSING - Offline Mode]" : "[Prepaid GCP Key: " + activePaidApiKey.substring(0, Math.min(8, activePaidApiKey.length())) + "...]"));
                System.out.println("Target Schemas:  " + String.join(", ", targetSchemas));
                System.out.printf("Daily Quota:     %,d Requests Per Day (RPD) - Free Priority%n", getActiveDailyQuotaLimit());
                int safetyLimit = getModelSafetyQuotaLimit(activeModelName != null ? activeModelName : modelName);
                int emergencyReserve = Math.max(0, getActiveDailyQuotaLimit() - safetyLimit);
                System.out.printf("Safety Ceiling:  %.0f%% Active (Max %,d req/day | %,d req reserved emergency buffer)%n",
                        (freeTierSafetyPct * 100.0), safetyLimit, emergencyReserve);
                System.out.printf("Rate Limiter:    Adaptive Burst Guard Active (%d RPM Free Ceiling / 4.1s spacing)%n", getActiveRpmLimit());
                System.out.println("Cooldown Boost:  Instant Paid Calls Dispatched During Free Tier 4.1s Cooldown Window");
                System.out.println("Spending Cap:    " + (spendingCapUsd > 0 ? String.format("$%.2f USD (Safety Cutoff Active)", spendingCapUsd) : "Unlimited ($0.00 ceiling)"));
                System.out.printf("Speed Mode:      %s%n", activeSpeedMode.getDescription());
                System.out.printf("Circuit Breaker: Auto-Stop on %d Consecutive API Connection Failures%n", maxConsecutiveFailures);
                System.out.println("Network Mode:    System Proxy Enabled (HTTP/1.1 Enterprise Compatibility)");
                System.out.println("Console Format:  " + (displayJsonBlocks ? "Interactive Pretty-Printed JSON Blocks (Multi-Line)" : "Compact Single-Line Status"));
            } else if (activeTierMode == TierMode.FREE) {
                System.out.println("Florida Lottery Stored Procedure Auto-Researcher [FREE TIER QUOTA MONITOR]");
                System.out.println("Input Source:    " + inputPath + " (" + (inputFile.length() / (1024 * 1024)) + " MB)");
                System.out.println("AI Catalog JSON: " + catalogJsonOutputPath);
                System.out.println("Quota Tracker:   " + quotaTrackerJsonFile.getAbsolutePath());
                System.out.println("Excel Catalog:   " + excelOutputPath);
                System.out.println("Auth Key:        " + (activeFreeApiKey.isEmpty() ? "[MISSING - Offline Mode]" : "[AI Studio Free Key: " + activeFreeApiKey.substring(0, Math.min(8, activeFreeApiKey.length())) + "...]"));
                System.out.println("Target Schemas:  " + String.join(", ", targetSchemas));
                System.out.printf("Daily Quota:     %,d Requests Per Day (RPD) - Free of Charge%n", getActiveDailyQuotaLimit());
                int safetyLimit = getModelSafetyQuotaLimit(activeModelName != null ? activeModelName : modelName);
                int emergencyReserve = Math.max(0, getActiveDailyQuotaLimit() - safetyLimit);
                System.out.printf("Safety Ceiling:  %.0f%% Active (Max %,d req/day | %,d req reserved emergency buffer)%n",
                        (freeTierSafetyPct * 100.0), safetyLimit, emergencyReserve);
                System.out.printf("Rate Limiter:    Adaptive Burst Guard Active (%d RPM Ceiling)%n", getActiveRpmLimit());
                System.out.printf("Speed Mode:      %s%n", activeSpeedMode.getDescription());
                System.out.printf("Circuit Breaker: Auto-Stop on %d Consecutive API Connection Failures%n", maxConsecutiveFailures);
                System.out.println("Network Mode:    System Proxy Enabled (HTTP/1.1 Enterprise Compatibility)");
                System.out.println("Console Format:  " + (displayJsonBlocks ? "Interactive Pretty-Printed JSON Blocks (Multi-Line)" : "Compact Single-Line Status"));
            } else {
                System.out.println("Florida Lottery Stored Procedure Auto-Researcher [ENTERPRISE PREPAID / PAID TIER]");
                System.out.println("Input Source:    " + inputPath + " (" + (inputFile.length() / (1024 * 1024)) + " MB)");
                System.out.println("AI Catalog JSON: " + catalogJsonOutputPath);
                System.out.println("Quota Tracker:   " + quotaTrackerJsonFile.getAbsolutePath());
                System.out.println("Excel Catalog:   " + excelOutputPath);
                System.out.println("Auth Key:        " + (activePaidApiKey.isEmpty() ? "[MISSING - Offline Mode]" : "[Prepaid GCP Key: " + activePaidApiKey.substring(0, Math.min(8, activePaidApiKey.length())) + "...]"));
                System.out.println("Target Schemas:  " + String.join(", ", targetSchemas));
                double displayInUsd = resolveInputTokenCostPennies(modelName) * 10000.0;
                double displayOutUsd = resolveOutputTokenCostPennies(modelName) * 10000.0;
                System.out.printf("Pricing Model:   %s ($%.2f/1M in, $%.2f/1M out)%n", modelName, displayInUsd, displayOutUsd);
                System.out.println("Spending Cap:    " + (spendingCapUsd > 0 ? String.format("$%.2f USD (Safety Cutoff Active)", spendingCapUsd) : "Unlimited ($0.00 ceiling)"));
                System.out.printf("Circuit Breaker: Auto-Stop on %d Consecutive API Connection Failures%n", maxConsecutiveFailures);
                System.out.printf("Speed Mode:      %s%n", activeSpeedMode.getDescription());
                System.out.println("Throughput:      High-Speed Enterprise Execution (Uncapped RPM)");
                System.out.println("Network Mode:    System Proxy Enabled (HTTP/1.1 Enterprise Compatibility)");
                System.out.println("Console Format:  " + (displayJsonBlocks ? "Interactive Pretty-Printed JSON Blocks (Multi-Line)" : "Compact Single-Line Status"));
            }
            System.out.println("=========================================================================");

            // Display cached daily quota availability per candidate model on startup
            if (activeTierMode != TierMode.PAID) {
                printStartupQuotaSummaryTable();
            }

            // Step 1: Pre-Flight Health Check / Handshake Test with Model Auto-Negotiation
            boolean apiReady = false;
            String activeApiUrl = apiUrl;
            String testKey = (activeTierMode == TierMode.PAID) ? activePaidApiKey : (!activeFreeApiKey.isEmpty() ? activeFreeApiKey : activePaidApiKey);
            if (!testKey.isEmpty()) {
                activeApiUrl = resolveActiveApiUrl(httpClient, mapper, modelName, testKey);
                apiReady = (activeApiUrl != null);
                if (apiReady) {
                    System.out.println("Handshake confirmed & API Key verified. Ready for batch extraction.");
                } else {
                    System.out.println("Running in Offline Extraction Mode (Excel will generate with pending AI fields).");
                }
            } else {
                System.out.println("[Pre-Flight Health Check] No API Key provided -> Running in Offline Mode.");
            }
            System.out.println("=========================================================================");

            Map<String, RoutineFinding> researchedCatalog = loadExistingCatalog(catalogJsonFile, mapper);
            System.out.printf("Loaded %d previously researched routines from cache.%n", researchedCatalog.size());

            System.out.println("Reading DbCache.json into memory...");
            long readStart = System.currentTimeMillis();
            JsonNode rootNode = mapper.readTree(inputFile);
            System.out.printf("DbCache.json loaded in %d ms.%n%n", (System.currentTimeMillis() - readStart));

            try (Workbook workbook = new XSSFWorkbook()) {
                CellStyle headerStyle = createHeaderStyle(workbook, IndexedColors.DARK_BLUE);
                CellStyle reportHeaderStyle = createHeaderStyle(workbook, IndexedColors.GREY_50_PERCENT);
                CellStyle tableHeaderStyle = createHeaderStyle(workbook, IndexedColors.ROYAL_BLUE);
                CellStyle stagingHeaderStyle = createHeaderStyle(workbook, IndexedColors.TEAL);

                CellStyle textStyle = workbook.createCellStyle();
                textStyle.setWrapText(false);

                CellStyle percentStyle = workbook.createCellStyle();
                DataFormat df = workbook.createDataFormat();
                percentStyle.setDataFormat(df.getFormat("0.00%"));

                CellStyle boldStyle = workbook.createCellStyle();
                Font boldFont = workbook.createFont();
                boldFont.setBold(true);
                boldStyle.setFont(boldFont);

                CellStyle boldPercentStyle = workbook.createCellStyle();
                boldPercentStyle.setFont(boldFont);
                boldPercentStyle.setDataFormat(df.getFormat("0.00%"));

                Sheet reportSheet = workbook.createSheet("Research Report");
                reportSheet.setDefaultRowHeightInPoints(ROW_HEIGHT_POINTS);

                Map<String, Sheet> schemaSheets = new HashMap<>();
                Map<String, Integer> schemaRowIndexes = new HashMap<>();
                List<String> schemaOrder = new ArrayList<>();

                int newlyResearchedCount = 0;

                List<JsonNode> schemaNodeList = extractAllSchemaNodes(rootNode);
                System.out.printf("Discovered %d total schema blocks in DbCache.json.%n%n", schemaNodeList.size());

                if (activeTierMode == TierMode.TURBO && apiReady) {
                    List<RoutineBatchItem> allPendingTurboItems = new ArrayList<>();
                    for (JsonNode sNode : schemaNodeList) {
                        String sName = extractText(sNode, "SchemaName", "Name", "Schema");
                        if (!sName.isEmpty() && targetSchemas.contains(sName.toUpperCase())) {
                            String cleanS = sName.toUpperCase();
                            String db = extractText(sNode, "DatabaseName", "DbName", "DboName");
                            if (db.isEmpty()) db = "DEVOLTP";

                            // 1. Procedures
                            JsonNode procs = getArrayNode(sNode, "Procedures", "procedures", "StoredProcedures");
                            if (procs.isArray()) {
                                for (JsonNode proc : procs) {
                                    String name = getObjectName(proc, "ProcedureName");
                                    if (isGhostOrKeywordRoutine(name)) continue;
                                    String key = String.format("%s.-.%s", cleanS, name.toUpperCase());
                                    boolean missingDiagram = refreshDiagrams && researchedCatalog.containsKey(key) &&
                                            (researchedCatalog.get(key).mermaidDiagram == null || researchedCatalog.get(key).mermaidDiagram.trim().isEmpty());
                                    boolean needsResearch = forceReResearch || missingDiagram || !researchedCatalog.containsKey(key) || isPendingOrFailed(researchedCatalog.get(key));
                                    if (needsResearch) {
                                        allPendingTurboItems.add(new RoutineBatchItem(cleanS, "-", name, "PROCEDURE", extractFullSql(proc), extractSqlExcerpt(proc), db, false));
                                    }
                                }
                            }

                            // 2. Packages
                            JsonNode pkgs = getArrayNode(sNode, "Packages", "packages");
                            if (pkgs.isArray()) {
                                for (JsonNode pkg : pkgs) {
                                    String pkgName = getObjectName(pkg, "PackageName");
                                    String fullPkgSql = extractFullSql(pkg);
                                    String pkgSql = extractSqlExcerpt(pkg);
                                    List<JsonNode> subRoutines = extractSubRoutines(pkg);
                                    for (JsonNode sub : subRoutines) {
                                        String routineName = getObjectName(sub, "ProcedureName", "FunctionName", "Name");
                                        if (isGhostOrKeywordRoutine(routineName)) continue;
                                        String key = String.format("%s.%s.%s", cleanS, pkgName.toUpperCase(), routineName.toUpperCase());
                                        boolean missingDiagram = refreshDiagrams && researchedCatalog.containsKey(key) &&
                                                (researchedCatalog.get(key).mermaidDiagram == null || researchedCatalog.get(key).mermaidDiagram.trim().isEmpty());
                                        boolean needsResearch = forceReResearch || missingDiagram || !researchedCatalog.containsKey(key) || isPendingOrFailed(researchedCatalog.get(key));
                                        if (needsResearch) {
                                            String rType = sub.path("Type").asText("PACKAGE PROCEDURE");
                                            String subFull = extractFullSql(sub);
                                            if (subFull.isEmpty()) subFull = fullPkgSql;
                                            String subEx = extractSqlExcerpt(sub);
                                            if (subEx.isEmpty()) subEx = pkgSql;
                                            allPendingTurboItems.add(new RoutineBatchItem(cleanS, pkgName, routineName, rType, subFull, subEx, db, true));
                                        }
                                    }
                                }
                            }

                            // 3. Functions
                            JsonNode funcs = getArrayNode(sNode, "Functions", "functions");
                            if (funcs.isArray()) {
                                for (JsonNode func : funcs) {
                                    String name = getObjectName(func, "FunctionName");
                                    if (isGhostOrKeywordRoutine(name)) continue;
                                    String key = String.format("%s.-.%s", cleanS, name.toUpperCase());
                                    boolean missingDiagram = refreshDiagrams && researchedCatalog.containsKey(key) &&
                                            (researchedCatalog.get(key).mermaidDiagram == null || researchedCatalog.get(key).mermaidDiagram.trim().isEmpty());
                                    boolean needsResearch = forceReResearch || missingDiagram || !researchedCatalog.containsKey(key) || isPendingOrFailed(researchedCatalog.get(key));
                                    if (needsResearch) {
                                        allPendingTurboItems.add(new RoutineBatchItem(cleanS, "-", name, "FUNCTION", extractFullSql(func), extractSqlExcerpt(func), db, false));
                                    }
                                }
                            }
                        }
                    }

                    if (!allPendingTurboItems.isEmpty()) {
                        TurboCoordinatorResult turboResult = runTurboResearchCoordinator(httpClient, mapper, activeApiUrl, allPendingTurboItems, researchedCatalog, catalogJsonFile, spendingCapUsd);
                        newlyResearchedCount += turboResult.completedRoutines;
                    }
                }

                for (JsonNode schema : schemaNodeList) {
                    String schemaName = extractText(schema, "SchemaName", "Name", "Schema");

                    if (!schemaName.isEmpty() && targetSchemas.contains(schemaName.toUpperCase())) {
                        String cleanSchemaName = schemaName.toUpperCase();
                        if (!schemaOrder.contains(cleanSchemaName)) schemaOrder.add(cleanSchemaName);

                        Sheet schemaSheet = schemaSheets.computeIfAbsent(cleanSchemaName, name -> {
                            Sheet s = workbook.createSheet(name);
                            s.setDefaultRowHeightInPoints(ROW_HEIGHT_POINTS);
                            createHeaders(s, headerStyle);
                            createNamedRange(workbook, name + "_TYPE", String.format("'%s'!$E$2:$E$10000", name));
                            createNamedRange(workbook, name + "_DESC", String.format("'%s'!$F$2:$F$10000", name));
                            return s;
                        });

                        int currentSchemaRowIdx = schemaRowIndexes.getOrDefault(cleanSchemaName, 1);
                        String dbName = extractText(schema, "DatabaseName", "DbName", "DboName");
                        if (dbName.isEmpty()) dbName = "DEVOLTP";

                        System.out.printf(">>> Processing Schema: %-12s%n", cleanSchemaName);

                        // 1. Procedures
                        JsonNode procs = getArrayNode(schema, "Procedures", "procedures", "StoredProcedures");
                        if (procs.isArray()) {
                            List<RoutineBatchItem> pendingProcs = new ArrayList<>();
                            for (JsonNode proc : procs) {
                                String name = getObjectName(proc, "ProcedureName");
                                if (isGhostOrKeywordRoutine(name)) continue;
                                String sql = extractSqlExcerpt(proc);
                                String fullSql = extractFullSql(proc);
                                String key = String.format("%s.-.%s", cleanSchemaName, name.toUpperCase());
                                boolean missingDiagram = refreshDiagrams && researchedCatalog.containsKey(key) &&
                                        (researchedCatalog.get(key).mermaidDiagram == null || researchedCatalog.get(key).mermaidDiagram.trim().isEmpty());
                                boolean needsResearch = forceReResearch || missingDiagram || !researchedCatalog.containsKey(key) || isPendingOrFailed(researchedCatalog.get(key));

                                if (apiReady && needsResearch) {
                                    if (circuitBreakerTripped) {
                                        // Circuit breaker is open due to sustained failures; skip API calls safely
                                    } else if (isSafetyCeilingReached(spendingCapUsd)) {
                                        // Safety ceiling or spending cap reached; preserve remaining quota/budget
                                    } else {
                                        pendingProcs.add(new RoutineBatchItem(cleanSchemaName, "-", name, "PROCEDURE", fullSql, sql, dbName, false));
                                    }
                                } else if (researchedCatalog.containsKey(key)) {
                                    System.out.printf("   [Cache Hit]  %-40s (Already Researched)%n", key);
                                }
                            }

                            if (!pendingProcs.isEmpty() && !circuitBreakerTripped && !isSafetyCeilingReached(spendingCapUsd)) {
                                List<List<RoutineBatchItem>> batches = createAdaptiveBatches(pendingProcs, activeSpeedMode.getBatchMultiplier());
                                for (List<RoutineBatchItem> batch : batches) {
                                    if (stopRequested) {
                                        System.out.println("\n[STOP REQUESTED] Process halted cleanly after last completed batch.");
                                        saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);
                                        break;
                                    }
                                    if (circuitBreakerTripped || isSafetyCeilingReached(spendingCapUsd)) break;
                                    List<RoutineFinding> findings = callGeminiAiStudioBatchWithCost(httpClient, mapper, activeApiUrl, testKey, batch);
                                    for (RoutineFinding finding : findings) {
                                        researchedCatalog.put(finding.lookupKey, finding);
                                        updateCircuitBreaker(finding);
                                        newlyResearchedCount++;
                                    }
                                    if (newlyResearchedCount % 5 == 0) {
                                        saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);
                                    }
                                    Thread.sleep(50);
                                }
                            }

                            for (JsonNode proc : procs) {
                                String name = getObjectName(proc, "ProcedureName");
                                if (isGhostOrKeywordRoutine(name)) continue;
                                String sql = extractSqlExcerpt(proc);
                                writeExcelRow(schemaSheet, currentSchemaRowIdx++, dbName, cleanSchemaName, "-", name, "PROCEDURE", sql, researchedCatalog, textStyle);
                            }
                        }

                        // 2. Packages
                        JsonNode pkgs = getArrayNode(schema, "Packages", "packages");
                        if (pkgs.isArray()) {
                            for (JsonNode pkg : pkgs) {
                                String pkgName = getObjectName(pkg, "PackageName");
                                String pkgSql = extractSqlExcerpt(pkg);
                                String fullPkgSql = extractFullSql(pkg);

                                List<JsonNode> subRoutines = extractSubRoutines(pkg);
                                if (!subRoutines.isEmpty()) {
                                    List<RoutineBatchItem> pendingPkgSubs = new ArrayList<>();
                                    for (JsonNode sub : subRoutines) {
                                        String routineName = getObjectName(sub, "ProcedureName", "FunctionName", "Name");
                                        if (isGhostOrKeywordRoutine(routineName)) continue;
                                        String rType = sub.path("Type").asText("PACKAGE PROCEDURE");
                                        String subSql = extractSqlExcerpt(sub);
                                        if (subSql.isEmpty()) subSql = pkgSql;
                                        String subFullSql = extractFullSql(sub);
                                        if (subFullSql.isEmpty()) subFullSql = fullPkgSql;

                                        String key = String.format("%s.%s.%s", cleanSchemaName, pkgName.toUpperCase(), routineName.toUpperCase());

                                        boolean missingDiagram = refreshDiagrams && researchedCatalog.containsKey(key) &&
                                                (researchedCatalog.get(key).mermaidDiagram == null || researchedCatalog.get(key).mermaidDiagram.trim().isEmpty());
                                        boolean needsResearch = forceReResearch || missingDiagram || !researchedCatalog.containsKey(key) || isPendingOrFailed(researchedCatalog.get(key));

                                        if (apiReady && needsResearch) {
                                            if (circuitBreakerTripped) {
                                                // Circuit breaker is open due to sustained failures; skip API calls safely
                                            } else if (isSafetyCeilingReached(spendingCapUsd)) {
                                                // Safety ceiling or spending cap reached; preserve remaining quota/budget
                                            } else {
                                                pendingPkgSubs.add(new RoutineBatchItem(cleanSchemaName, pkgName, routineName, rType, subFullSql, subSql, dbName, true));
                                            }
                                        } else if (researchedCatalog.containsKey(key)) {
                                            System.out.printf("   [Cache Hit]  %-40s (Already Researched)%n", key);
                                        }
                                    }

                                    if (!pendingPkgSubs.isEmpty() && !circuitBreakerTripped && !isSafetyCeilingReached(spendingCapUsd)) {
                                        List<List<RoutineBatchItem>> batches = createAdaptiveBatches(pendingPkgSubs, activeSpeedMode.getBatchMultiplier());
                                        for (List<RoutineBatchItem> batch : batches) {
                                            if (circuitBreakerTripped || isSafetyCeilingReached(spendingCapUsd)) break;
                                            List<RoutineFinding> findings = callGeminiAiStudioBatchWithCost(httpClient, mapper, activeApiUrl, testKey, batch);
                                            for (RoutineFinding finding : findings) {
                                                researchedCatalog.put(finding.lookupKey, finding);
                                                updateCircuitBreaker(finding);
                                                newlyResearchedCount++;
                                            }
                                            if (newlyResearchedCount % 5 == 0) {
                                                saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);
                                            }
                                            Thread.sleep(50);
                                        }
                                    }

                                    for (JsonNode sub : subRoutines) {
                                        String routineName = getObjectName(sub, "ProcedureName", "FunctionName", "Name");
                                        if (isGhostOrKeywordRoutine(routineName)) continue;
                                        String rType = sub.path("Type").asText("PACKAGE PROCEDURE");
                                        String subSql = extractSqlExcerpt(sub);
                                        if (subSql.isEmpty()) subSql = pkgSql;

                                        writeExcelRow(schemaSheet, currentSchemaRowIdx++, dbName, cleanSchemaName, pkgName, routineName, rType, subSql, researchedCatalog, textStyle);
                                    }
                                } else {
                                    writeExcelRow(schemaSheet, currentSchemaRowIdx++, dbName, cleanSchemaName, pkgName, "[PACKAGE SPECIFICATION/BODY]", "PACKAGE SPECIFICATION/BODY", pkgSql, researchedCatalog, textStyle);
                                }
                            }
                        }

                        // 3. Functions
                        JsonNode funcs = getArrayNode(schema, "Functions", "functions");
                        if (funcs.isArray()) {
                            List<RoutineBatchItem> pendingFuncs = new ArrayList<>();
                            for (JsonNode func : funcs) {
                                String name = getObjectName(func, "FunctionName");
                                if (isGhostOrKeywordRoutine(name)) continue;
                                String sql = extractSqlExcerpt(func);
                                String fullSql = extractFullSql(func);
                                String key = String.format("%s.-.%s", cleanSchemaName, name.toUpperCase());

                                boolean needsResearch = forceReResearch || !researchedCatalog.containsKey(key) || isPendingOrFailed(researchedCatalog.get(key));

                                if (apiReady && needsResearch) {
                                    if (circuitBreakerTripped) {
                                        // Circuit breaker is open due to sustained failures; skip API calls safely
                                    } else if (isSafetyCeilingReached(spendingCapUsd)) {
                                        // Safety ceiling or spending cap reached; preserve remaining quota/budget
                                    } else {
                                        pendingFuncs.add(new RoutineBatchItem(cleanSchemaName, "-", name, "FUNCTION", fullSql, sql, dbName, false));
                                    }
                                } else if (researchedCatalog.containsKey(key)) {
                                    System.out.printf("   [Cache Hit]  %-40s (Already Researched)%n", key);
                                }
                            }

                            if (!pendingFuncs.isEmpty() && !circuitBreakerTripped && !isSafetyCeilingReached(spendingCapUsd)) {
                                List<List<RoutineBatchItem>> batches = createAdaptiveBatches(pendingFuncs, activeSpeedMode.getBatchMultiplier());
                                for (List<RoutineBatchItem> batch : batches) {
                                    if (circuitBreakerTripped || isSafetyCeilingReached(spendingCapUsd)) break;
                                    List<RoutineFinding> findings = callGeminiAiStudioBatchWithCost(httpClient, mapper, activeApiUrl, testKey, batch);
                                    for (RoutineFinding finding : findings) {
                                        researchedCatalog.put(finding.lookupKey, finding);
                                        updateCircuitBreaker(finding);
                                        newlyResearchedCount++;
                                    }
                                    if (newlyResearchedCount % 5 == 0) {
                                        saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);
                                    }
                                    Thread.sleep(50);
                                }
                            }

                            for (JsonNode func : funcs) {
                                String name = getObjectName(func, "FunctionName");
                                if (isGhostOrKeywordRoutine(name)) continue;
                                String sql = extractSqlExcerpt(func);
                                writeExcelRow(schemaSheet, currentSchemaRowIdx++, dbName, cleanSchemaName, "-", name, "FUNCTION", sql, researchedCatalog, textStyle);
                            }
                        }

                        // 4. Tables
                        JsonNode tables = getArrayNode(schema, "Tables", "tables");
                        if (tables.isArray()) {
                            for (JsonNode tbl : tables) {
                                String name = getObjectName(tbl, "TableName");
                                String desc = extractTableDescription(tbl, cleanSchemaName, name);
                                String columnsAndTypes = extractTableColumns(tbl);
                                String constraints = extractTableConstraints(tbl);
                                String sql = extractSqlExcerpt(tbl);

                                writeTableExcelRow(schemaSheet, currentSchemaRowIdx++, dbName, cleanSchemaName, "-", name, "TABLE", desc, columnsAndTypes, constraints, sql, textStyle);
                            }
                        }

                        schemaRowIndexes.put(cleanSchemaName, currentSchemaRowIdx);
                    }
                }

                populateResearchReportSheetWithNamedRanges(reportSheet, schemaOrder, reportHeaderStyle, tableHeaderStyle,
                        boldStyle, percentStyle, boldPercentStyle);

                // Auto-size columns
                for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                    Sheet sheet = workbook.getSheetAt(s);
                    sheet.setDefaultRowHeightInPoints(ROW_HEIGHT_POINTS);
                    if (sheet.getSheetName().equals("Research Report")) {
                        for (int col = 0; col < 5; col++) sheet.autoSizeColumn(col);
                    } else {
                        // Schema tab with 17 rich columns
                        for (int col = 0; col < 17; col++) {
                            sheet.autoSizeColumn(col);
                            if (col >= 5 && col <= 12) {
                                sheet.setColumnWidth(col, Math.min(Math.max(sheet.getColumnWidth(col), 8000), 22000));
                            } else if (col >= 13 && col <= 15) {
                                sheet.setColumnWidth(col, Math.min(Math.max(sheet.getColumnWidth(col), 6000), 18000));
                            } else if (col == 16) {
                                sheet.setColumnWidth(col, Math.min(Math.max(sheet.getColumnWidth(col), 9000), 22000));
                            }
                        }
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(excelOutputPath)) {
                    workbook.write(fos);
                }

                saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);

                double totalDollars = totalCostInPennies / 100.0;
                System.out.println("\n=========================================================================");
                System.out.println("SUCCESS: Catalog & Multi-Tab Excel Generated!");
                System.out.println("Excel Output:        " + excelOutputPath);
                System.out.println("JSON Findings:       " + catalogJsonOutputPath);
                int totalObjects = schemaRowIndexes.values().stream().mapToInt(idx -> idx - 1).sum();
                System.out.printf("Total Routines & Tables: %d (Researched: %d, Newly Called: %d)%n",
                        totalObjects, researchedCatalog.size(), newlyResearchedCount);
                System.out.printf("Total Network I/O:   %.2f KB in / %.2f KB out (%.2f MB total)%n",
                        (totalBytesIn / 1024.0), (totalBytesOut / 1024.0), ((totalBytesIn + totalBytesOut) / (1024.0 * 1024.0)));
                System.out.printf("Total Prompt Tokens: %d%n", totalPromptTokens);
                System.out.printf("Total Output Tokens: %d%n", totalCandidateTokens);
                System.out.printf("TOTAL RUNNING COST:  %.4f Pennies ( $%.6f USD )%n", totalCostInPennies, totalDollars);
                if (activeTierMode == TierMode.HYBRID) {
                    System.out.printf("HYBRID EXECUTION:    %,d Free Calls ($0.00) | %,d Paid Calls (Cooldown Boost: $%.6f USD)%n",
                            totalFreeCalls, totalPaidCalls, totalDollars);
                    double estSavingsPennies = (totalFreeCalls * (1500.0 * resolveInputTokenCostPennies(activeModelName) + 350.0 * resolveOutputTokenCostPennies(activeModelName)));
                    System.out.printf("HYBRID COST SAVINGS: ~$%.4f USD saved via Free Tier priority!%n", (estSavingsPennies / 100.0));
                }
                System.out.println("=========================================================================");

                if (Boolean.parseBoolean(argMap.getOrDefault("generateWordDocs", "false")) && !stopRequested) {
                    boolean runSynthesizer = Boolean.parseBoolean(argMap.getOrDefault("synthesizeWorldView", argMap.getOrDefault("synthesize", "false")));
                    if (runSynthesizer && !stopRequested) {
                        System.out.println("\n=========================================================================");
                        System.out.println("[Triggering Ecosystem World-View Synthesizer before Word Doc generation...]");
                        System.out.println("=========================================================================");
                        SynthesizeEcosystemWorldView.main(new String[]{
                                "-catalog=" + catalogJsonOutputPath,
                                "-dbCache=" + inputPath,
                                "-output=" + schemasDirPath + "\\Enterprise_Ecosystem_WorldView.json",
                                "-schemasDir=" + schemasDirPath
                        });
                    }

                    if (!stopRequested) {
                        System.out.println("\n=========================================================================");
                        System.out.println("[Triggering Unified Word Documentation (.docx) Specification Generator...]");
                        System.out.println("=========================================================================");
                        GenerateUnifiedTechnicalWordDocs.main(new String[]{
                                "-input=" + inputPath,
                                "-catalog=" + catalogJsonOutputPath,
                                "-outputDir=" + schemasDirPath + "\\Word_Docs",
                                "-mode=" + argMap.getOrDefault("wordDocMode", "both")
                        });
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("CRITICAL: Pipeline failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<JsonNode> extractAllSchemaNodes(JsonNode rootNode) {
        List<JsonNode> list = new ArrayList<>();
        JsonNode databases = rootNode.path("Databases");
        if (!databases.isArray()) databases = rootNode.path("databases");

        if (databases.isArray()) {
            for (JsonNode db : databases) {
                JsonNode schemas = db.path("Schemas");
                if (!schemas.isArray()) schemas = db.path("schemas");
                if (schemas.isArray()) schemas.forEach(list::add);
            }
        }

        JsonNode rootSchemas = rootNode.path("Schemas");
        if (!rootSchemas.isArray()) rootSchemas = rootNode.path("schemas");
        if (rootSchemas.isArray()) rootSchemas.forEach(list::add);

        return list;
    }

    private static JsonNode getArrayNode(JsonNode parent, String... keys) {
        for (String k : keys) {
            JsonNode n = parent.path(k);
            if (n.isArray()) return n;
        }
        return parent.path(keys[0]);
    }

    private static class HttpResponseWrapper {
        public final int statusCode;
        public final String body;
        public final String engineUsed;

        public HttpResponseWrapper(int statusCode, String body, String engineUsed) {
            this.statusCode = statusCode;
            this.body = body;
            this.engineUsed = engineUsed;
        }
    }

    private static HttpResponseWrapper executeHttpCall(HttpClient client, String targetUrl, String apiKey, String jsonPayload, int timeoutSec) throws Exception {
        Exception primaryException = null;

        // Try Engine 1: Java 21 HttpClient
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey.trim())
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResponseWrapper(response.statusCode(), response.body(), "Java21.HttpClient");
        } catch (Exception ex) {
            primaryException = ex;
        }

        // Try Engine 2: Native Windows HttpsURLConnection Fallback
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn;
            if (activeProxyConfig != null && activeProxyConfig.isEnabled()) {
                conn = (HttpURLConnection) url.openConnection(activeProxyConfig.getJavaProxy());
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("x-goog-api-key", apiKey.trim());
            conn.setConnectTimeout(timeoutSec * 1000);
            conn.setReadTimeout(timeoutSec * 1000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = "";
            if (is != null) {
                try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
                    scanner.useDelimiter("\\A");
                    responseBody = scanner.hasNext() ? scanner.next() : "";
                }
            }
            return new HttpResponseWrapper(status, responseBody, "Native.HttpsURLConnection");
        } catch (Exception fallbackEx) {
            if (jsonLogMode) {
                Map<String, Object> errData = new LinkedHashMap<>();
                errData.put("targetUrl", targetUrl);
                errData.put("primaryEngineError", primaryException.getClass().getName() + " -> " + primaryException.getMessage());
                errData.put("fallbackEngineError", fallbackEx.getClass().getName() + " -> " + fallbackEx.getMessage());
                errData.put("activeProxy", activeProxyConfig != null ? activeProxyConfig.toString() : "None");
                errData.put("errorCategory", "NETWORK_SOCKET_REFUSED");
                errData.put("actionRequired", "Verify Florida Lottery enterprise proxy (proxy.properties or -proxyHost=... -proxyPort=...) or VPN connection");
                emitJsonLog("FATAL", "CRITICAL_CONNECTION_FAILURE", errData);
            }
            System.err.println("\n-------------------------------------------------------------------------");
            System.err.println("CRITICAL CONNECTION FAILURE DIAGNOSTICS:");
            System.err.println("Target URL:            " + targetUrl);
            System.err.println("Primary Engine Error:  " + primaryException.getClass().getName() + " -> " + primaryException.getMessage());
            System.err.println("Fallback Engine Error: " + fallbackEx.getClass().getName() + " -> " + fallbackEx.getMessage());
            if (activeProxyConfig != null) {
                System.err.println("Enterprise Proxy:      " + activeProxyConfig.toString());
            }

            try {
                java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName("generativelanguage.googleapis.com");
                List<String> ipList = new ArrayList<>();
                for (java.net.InetAddress addr : addresses) ipList.add(addr.getHostAddress());
                System.err.println("DNS Resolved IPs:      " + String.join(", ", ipList));
            } catch (Exception dnsEx) {
                System.err.println("DNS Resolution Error:  Failed to resolve generativelanguage.googleapis.com (" + dnsEx.getMessage() + ")");
            }

            System.err.println("ACTION REQUIRED: Ensure Florida Lottery enterprise proxy is reachable (configured in proxy.properties or -proxyHost=... -proxyPort=...) or active on VPN.");
            System.err.println("-------------------------------------------------------------------------");
            throw fallbackEx;
        }
    }

    private static class PreFlightCheckResult {
        public final boolean success;
        public final int statusCode;
        public final String engineUsed;
        public final String body;
        public final String recommendedModel;

        public PreFlightCheckResult(boolean success, int statusCode, String engineUsed, String body, String recommendedModel) {
            this.success = success;
            this.statusCode = statusCode;
            this.engineUsed = engineUsed;
            this.body = body;
            this.recommendedModel = recommendedModel;
        }
    }

    public static String resolveActiveApiUrl(HttpClient client, ObjectMapper mapper, String requestedModel, String apiKey) {
        List<String> candidateModels = new ArrayList<>();
        if (requestedModel != null && !requestedModel.trim().isEmpty()) {
            candidateModels.add(requestedModel.trim());
        }
        for (String m : MODEL_FAILOVER_CHAIN) {
            if (!candidateModels.contains(m)) candidateModels.add(m);
        }

        boolean isExplicitModel = (requestedModel != null && !requestedModel.trim().isEmpty() && !requestedModel.equalsIgnoreCase(DEFAULT_MODEL));

        for (int i = 0; i < candidateModels.size(); i++) {
            String model = candidateModels.get(i);

            // If model was cached as exhausted today or at safety ceiling and wasn't explicitly requested via CLI, skip to save network time
            if (!isExplicitModel && (isModelExhaustedToday(model) || isModelAtSafetyCeiling(model))) {
                System.out.printf("[Pre-Flight Health Check] Skipping %s (Used: %d/%d RPD - %s)%n",
                        model, getModelRequestsUsedToday(model), resolveModelDailyQuotaLimit(model),
                        isModelExhaustedToday(model) ? "EXHAUSTED_429" : String.format("%.0f%% Safety Ceiling Reached", freeTierSafetyPct * 100.0));
                continue;
            }

            String testUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
            System.out.printf("[Pre-Flight Health Check] Testing Google AI Studio connectivity (%s)... ", model);

            PreFlightCheckResult result = performPreFlightSanityCheck(client, mapper, testUrl, apiKey);
            if (result.success) {
                activeModelName = model;
                currentActiveApiUrl = testUrl;
                recordModelRequest(model, 200, 0L, 0L, mapper);
                dailyRequestsUsed = getModelRequestsUsedToday(model);
                System.out.printf("[SUCCESS via %s] Handshake confirmed with model: %s%n", result.engineUsed, model);
                String successUrl = testUrl;
                return successUrl;
            } else if (result.statusCode == 429) {
                recordModel429Exhaustion(model, mapper);
                System.out.printf("Quota/Rate limit hit on %s -> Auto-failing over to next candidate in chain...%n", model);
            } else if (result.statusCode == 404 && result.recommendedModel != null && !candidateModels.contains(result.recommendedModel)) {
                candidateModels.add(result.recommendedModel);
            }
        }
        return null;
    }

    public static synchronized boolean tryFailoverToNextModel(HttpClient client, ObjectMapper mapper, String apiKey) {
        // Record active model as exhausted in persistent quota cache
        recordModel429Exhaustion(activeModelName, mapper);

        int currentIdx = -1;
        for (int i = 0; i < MODEL_FAILOVER_CHAIN.size(); i++) {
            if (MODEL_FAILOVER_CHAIN.get(i).equalsIgnoreCase(activeModelName)) {
                currentIdx = i;
                break;
            }
        }
        for (int i = currentIdx + 1; i < MODEL_FAILOVER_CHAIN.size(); i++) {
            String nextModel = MODEL_FAILOVER_CHAIN.get(i);
            if (isModelExhaustedToday(nextModel) || isModelAtSafetyCeiling(nextModel)) {
                System.out.printf("%n   [Auto-Failover] Skipping %s (Used: %d/%d RPD - %s)%n",
                        nextModel, getModelRequestsUsedToday(nextModel), resolveModelDailyQuotaLimit(nextModel),
                        isModelExhaustedToday(nextModel) ? "Already marked exhausted today: HTTP 429" : String.format("%.0f%% Safety Ceiling Reached", freeTierSafetyPct * 100.0));
                continue;
            }

            String testUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + nextModel + ":generateContent";
            System.out.printf("%n   [Auto-Failover] Quota ceiling hit on %s -> Testing failover to %s... ", activeModelName, nextModel);
            PreFlightCheckResult check = performPreFlightSanityCheck(client, mapper, testUrl, apiKey);
            if (check.success) {
                String prevModel = activeModelName;
                activeModelName = nextModel;
                currentActiveApiUrl = testUrl;
                recordModelRequest(nextModel, 200, 0L, 0L, mapper);
                dailyRequestsUsed = getModelRequestsUsedToday(nextModel);
                System.out.printf("[FAILOVER SUCCESS] Active model switched to: %s%n", nextModel);
                Map<String, Object> failData = new LinkedHashMap<>();
                failData.put("fromModel", prevModel);
                failData.put("toModel", nextModel);
                failData.put("reason", "QUOTA_CEILING_HIT");
                failData.put("status", "SUCCESS");
                emitJsonLog("WARN", "AUTO_FAILOVER", failData);
                boolean failoverOk = true;
                return failoverOk;
            } else {
                System.out.printf("[%s Unavailable: HTTP %d]%n", nextModel, check.statusCode);
                if (check.statusCode == 429) {
                    recordModel429Exhaustion(nextModel, mapper);
                }
                Map<String, Object> failData = new LinkedHashMap<>();
                failData.put("fromModel", activeModelName);
                failData.put("toModel", nextModel);
                failData.put("reason", "QUOTA_CEILING_HIT");
                failData.put("status", "FAILED");
                failData.put("httpStatus", check.statusCode);
                emitJsonLog("WARN", "AUTO_FAILOVER", failData);
            }
        }
        QuotaResetInfo resetInfo = getQuotaResetInfo();
        System.out.println("   [Auto-Failover] All available model quotas in chain exhausted.");
        System.out.printf("   [Daily Quota Reset] Next daily quota reset: %s (%s) -> in %s%n",
                resetInfo.resetTimeUtc, resetInfo.resetTimeLocal, resetInfo.formattedCountdown);
        Map<String, Object> allExh = new LinkedHashMap<>();
        allExh.put("status", "ALL_MODELS_EXHAUSTED");
        allExh.put("nextResetUtc", resetInfo.resetTimeUtc);
        allExh.put("nextResetLocal", resetInfo.resetTimeLocal);
        allExh.put("timeUntilReset", resetInfo.formattedCountdown);
        emitJsonLog("ERROR", "AUTO_FAILOVER_EXHAUSTED", allExh);
        boolean allFailed = false;
        return allFailed;
    }

    private static PreFlightCheckResult performPreFlightSanityCheck(HttpClient client, ObjectMapper mapper, String apiUrl, String apiKey) {
        try {
            long start = System.currentTimeMillis();
            ObjectNode root = mapper.createObjectNode();
            ArrayNode contents = mapper.createArrayNode();
            ObjectNode contentObj = mapper.createObjectNode();
            ArrayNode parts = mapper.createArrayNode();
            ObjectNode textPart = mapper.createObjectNode();
            textPart.put("text", "Respond with JSON: {\"status\": \"ONLINE\"}");
            parts.add(textPart);
            contentObj.set("parts", parts);
            contents.add(contentObj);
            root.set("contents", contents);

            ObjectNode genConfig = mapper.createObjectNode();
            genConfig.put("response_mime_type", "application/json");
            root.set("generationConfig", genConfig);

            String requestPayload = mapper.writeValueAsString(root);
            String targetUrl = apiKey.trim().isEmpty() ? apiUrl : (apiUrl + "?key=" + apiKey.trim());

            HttpResponseWrapper response = executeHttpCall(client, targetUrl, apiKey, requestPayload, 15);
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode == 200) {
                return new PreFlightCheckResult(true, 200, response.engineUsed, response.body, null);
            } else {
                System.out.printf("%n-------------------------------------------------------------------------%n");
                System.out.printf("PRE-FLIGHT API HTTP ERROR (HTTP %d):%n", response.statusCode);
                System.out.printf("Engine Used:  %s%n", response.engineUsed);
                System.out.printf("Response Body: %s%n", response.body);

                String recModel = null;
                if (response.body != null && response.body.contains("models/")) {
                    int mIdx = response.body.indexOf("models/");
                    int endIdx = response.body.indexOf(" ", mIdx);
                    if (endIdx == -1) endIdx = response.body.indexOf("\"", mIdx);
                    if (endIdx > mIdx + 7) {
                        recModel = response.body.substring(mIdx + 7, endIdx).replaceAll("[^a-zA-Z0-9.-]", "");
                    }
                }

                if (response.statusCode == 400) {
                    System.out.println("DIAGNOSIS: Invalid request syntax or malformed API Key format.");
                } else if (response.statusCode == 401 || response.statusCode == 403) {
                    System.out.println("DIAGNOSIS: API Key unauthorized or billing/project quota exhausted in Google AI Studio.");
                } else if (response.statusCode == 404) {
                    System.out.printf("DIAGNOSIS: Model endpoint not found. %s%n", (recModel != null ? "Discovered recommended model: " + recModel : "Attempting candidate model fallback."));
                } else if (response.statusCode == 429) {
                    System.out.println("DIAGNOSIS: Rate limit exceeded on AI Studio tier.");
                }
                System.out.println("-------------------------------------------------------------------------");
                return new PreFlightCheckResult(false, response.statusCode, response.engineUsed, response.body, recModel);
            }
        } catch (Exception ex) {
            System.out.printf("%n   [Pre-Flight Connection Failure]: %s - %s%n", ex.getClass().getName(), ex.getMessage());
            return new PreFlightCheckResult(false, -1, "None", ex.getMessage(), null);
        }
    }

    private static RoutineFinding callGeminiAiStudioWithCost(HttpClient client, ObjectMapper mapper, String apiUrl, String apiKey,
                                                             String schema, String pkg, String routineName, String routineType, String sqlSource) {
        String lookupKey = String.format("%s.%s.%s", schema.toUpperCase(), (pkg == null ? "-" : pkg.toUpperCase()), routineName.toUpperCase());
        long startMs = System.currentTimeMillis();
        String cleanSql = (sqlSource == null) ? "" : sqlSource.trim();

        Map<String, Object> startData = new LinkedHashMap<>();
        startData.put("target", lookupKey);
        startData.put("schema", schema);
        startData.put("routine", routineName);
        startData.put("model", activeModelName);
        startData.put("batched", 1);
        emitJsonLog("INFO", "API_CALL_START", startData);

        String prompt = String.format("""
            You are a staff database engineer and technical documentation lead for the Florida Lottery.
            Analyze the following legacy %s named '%s' (Schema: %s, Package: %s).
            
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
            - Florida Lottery Domain Context: Accurately reference lottery gaming concepts (e.g., retailer weekly settlement, instant scratch-off/terminal game validations, draw sales, jackpot liability, commission ledger, staging tables).
            - Do NOT assume or mention any modern ETL framework unless explicitly indicated in the SQL comments or code.
            - TERMINOLOGY & VOCABULARY STANDARD:
              * Avoid the jargon words "governance", "ingest"/"ingress", and "persistence".
              * Use clear, natural alternatives:
                - Instead of "Ingress / Ingestion", use "Source Systems & Callers", "Inputs", or "Entry Points".
                - Instead of "Persistence", use "Database Storage & Target Tables", "Target Tables", or "Database Records".
                - Instead of "Governance", use "Technical Specifications & Standards", "Compliance Rules", or "Operational Standards".
            
            Provide a strict JSON response adhering to the response schema:
            1. what: 2 to 5 clear, plain-English sentences explaining WHAT this routine does step-by-step (which specific tables are read, what calculations or validations occur, and which target tables get inserted/updated/truncated). STRICT IDENTIFIER RULE: Always name the exact database table identifiers (e.g. ARS.ARTTRAN, ARS.ARTDNBR); NEVER use generic placeholder phrases like 'Core Tables', 'Staging Tables', or 'Various Tables'.
            2. why: 2 to 5 clear sentences explaining WHY this routine exists from a business and batch operations standpoint (e.g. nightly financial reconciliation, retailer weekly invoicing, prize payout audit trail, downstream consumers).
            3. how: 2 to 5 plain-English sentences tailored for Java/Web developers explaining technical mechanics (e.g. data access patterns, cursor loops vs set-based/BULK COLLECT, transaction boundaries/commits, package state, or dynamic SQL).
            4. callerChannels: Array of all upstream source systems or entry points invoking this routine (e.g. ["Web Portals & Apps", "Oracle Forms / Retail UI", "Batch IWS Schedulers", "DB Trigger", "Nightly Cron Job"]).
            5. flowSteps: Array of 3 to 6 concise sequential execution phases in plain English (e.g. ["1. Validate accounting date & parameters", "2. Fetch unbilled retailer transactions from STAGING", "3. Calculate 5%% sales commission and statutory adjustments", "4. Bulk insert finalized rows into COMMISSION_LEDGER", "5. Log audit status and commit transaction"]).
            6. parameters: JSON object with two array fields:
               - in: Array of input argument strings with data types and plain-English purpose, e.g. ["p_accounting_date (DATE) - Business run date", "p_game_nbr (VARCHAR2) - Lottery game number"] or [] if none.
               - out: Array of output/in-out argument strings with data types and plain-English purpose, e.g. ["p_batch_id (NUMBER) - Generated batch identifier", "p_status (VARCHAR2) - Execution status code"] or [] if none.
            7. dependencies: Formatted string capturing touched columns and called parameters with explicit SCHEMA prefixes (e.g. STAGE.TBL1, COMMON.V_ACTIVE). If this routine does NOT directly perform SQL INSERT/UPDATE/DELETE on tables (e.g. it is an orchestrator wrapper that calls other packages/procedures), do NOT list those tables under Writes/Updates; instead set Writes: [None] and list the sub-procedures under Calls: [SCHEMA.PKG.PROC(PARAM1, PARAM2)]. Exact table and procedure names only (NEVER write generic phrases like 'core tables'). Example format: 'Reads: [STAGE.TBL1(COL1, COL2), COMMON.TBL2(COL3)] | Updates: [STAGE.TBL3(COL4, COL5) or None] | Writes: [STAGE.TBL4(COL6) or None] | Deletes: [STAGE.TBL5 or None] | Calls: [TRANSLATOR.PKG.PROC(PARAM1, PARAM2) or None] | DBLinks: [LINK or None]'.
            8. dataFlowDiagram: Compact typed relationship string capturing all multi-point nodes, object types, touched columns and arguments with explicit SCHEMA prefixes. List ALL direct tables and views with their columns (e.g. TABLE: STAGE.TBL1(C1, C2), VIEW: COMMON.V_ACTIVE_GAMES(GAME_NBR, STATUS)). If this routine does not directly modify tables, put Writes: [None] and list only the called procedures in Calls: [PKG_PROC: SCHEMA.PKG.PROC]. Example: 'Callers: [BATCH: Batch Script, WEB: Web Portal] -> Reads: [TABLE: STAGE.STG_RETAILER(FIRST_NAME, LAST_NAME, PRICE), VIEW: COMMON.V_ACTIVE_GAMES(GAME_NBR, STATUS)] -> Updates: [None] -> Writes: [None] -> Deletes: [None] -> Calls: [PKG_PROC: TRANSLATOR.CP_INETDRAW_LOADER.LoadStage, PKG_PROC: TRANSLATOR.CP_INETPAYOUT_LOADER.LoadStage]'.
            9. businessRules: Concise, plain-English summary of key business rules, statutory formulas, validation thresholds, and eligibility constraints.
            10. errorHandling: Clear explanation of exception handling and rollback behavior (e.g. 'Rolls back all changes on SQL error and writes error code to AUDIT_LOG table' or 'Swallows error with no logging').
            11. testingStrategy: Step-by-step instructions for a developer or QA analyst to test this routine, including sample parameter values and verification SELECT queries.
            12. complexityScore: Estimated integer complexity 1-10.
            13. recommendations: Plain-English code review suggestions, potential performance bottlenecks (e.g. unindexed table scans, row-by-row cursor loops, missing indexes), data integrity risks, or maintainability suggestions for this legacy routine.
            14. mermaidDiagram: A valid, accurate Mermaid flowchart diagram capturing the complete, granular step-by-step chronological execution trace of this routine in valid 'flowchart TD' syntax adhering strictly to the Florida Lottery Gold Standard Flowchart Guidelines:
                - OPERATIONAL NARRATIVE & ACTIVE ACTION VERBS: Every node must narrate what the code is functionally doing in plain, active English with technical precision (e.g., ["Open SMTP connection to relay host on port 25"], ["Tokenize and register TO recipients via RCPT"], ["Query session timezone and format GMT offset"], ["Bulk insert calculated commission records into target ledger"]). Never repeat raw variable syntax (e.g., do NOT write [v_conn := UTL_SMTP.OPEN_CONNECTION]).
                - MODULAR STAGE SUBGRAPHS & CLEAN LINKING: Partition multi-step pipelines into balanced subgraphs with a space before the bracket: e.g., subgraph Stage1 ["Stage 1: Connection & Recipient Handshake"] containing roughly 4 to 7 operational nodes per stage. Connect subgraphs or nodes cleanly. NEVER attach conditional branch labels (|Yes| / |No|) to a subgraph container (e.g., do NOT write Stage1 -->|No| subLoad; connect the decision diamond node directly: checkErr -->|No| subLoad).
                - STRICT BRACKET MATCHING & CLEAN SYNTAX: Never mix opening square brackets with closing parentheses (e.g., openCursor["Open cursor..."]) is INVALID). Processes MUST be node["Action"], decisions cond{"Question?"}, and terminals term([\"Start/End: ...\"]).
                - SELF-CONTAINED ENTITY LABELS: In short routines, never use generic labels like start(["Start"]), cursor[Open C1], or endNode(["End"]). Always name the specific source, staging, and target tables/entities (e.g., readDb2["Read Staging Records from ARTDNBR_DB2"] --> cleanIns["Clean & Insert into ARTDNBR"]).
                - DISTINCT SUCCESS VS. ABORT TERMINALS: Explicitly separate successful completion terminals from fatal rollback/abortion terminals: e.g., successNode([\"End: Synchronization Complete\"]) vs. failNode([\"End: Rolled Back on Error\"]). Never route fatal errors to a 'Completed Successfully' pill.
                - CONVERSATIONAL DECISION QUESTIONS & MANDATORY DUAL BRANCHES (YES & NO): Every decision diamond must ask a full, plain-English question in double quotes: e.g., checkCC{\"Are CC recipients specified?\"}, checkLimit{\"Does accumulated size exceed max limit?\"}, isSuccess{\"Did the batch load complete successfully?\"}. CRITICAL: Every decision diamond MUST have ALL outbound branches explicitly defined directly from the diamond (both |Yes| and |No|). NEVER leave a decision diamond dangling with only one branch.
                - ACCURATE LOOPS & DDL PLACEMENT: Trace loops with a decision diamond, routing |Yes| through the loop body and looping back up, and |No| forward. Place DDL statements (like constraint enablement) and commits exactly when they occur in the code.
                - SHAPES & NO INLINE STYLES: Use rectangles ["..."] for processes, diamonds {"..."} for decisions, parallelograms [/\"...\"/] for I/O and console logs, and ovals ([\"... \"]) for start and end. Do NOT write manual 'style' or ':::class' tags.
                
                GOLD STANDARD REFERENCE PATTERN (Follow this structural layout):
                flowchart TD
                    startNode([\"Start: Routine Objective\"]) --> subConn

                    subgraph Stage1 [\"Stage 1: Connection & Recipient Handshake\"]
                        subConn[\"Open SMTP connection to relay host on port 25\"]
                        subConn --> helo[\"Perform HELO handshake & register sender address\"]
                        helo --> parseTo[\"Tokenize and register TO recipients via RCPT\"]
                        parseTo --> checkCC{\"Are CC recipients specified?\"}
                        checkCC -->|Yes| parseCC[\"Tokenize and register CC recipients via RCPT\"]
                        checkCC -->|No| checkBCC{\"Are BCC recipients specified?\"}
                        parseCC --> checkBCC
                        checkBCC -->|Yes| parseBCC[\"Tokenize and register BCC recipients via RCPT\"]
                        checkBCC -->|No| tzCalc[\"Query session timezone and format GMT offset\"]
                        parseBCC --> tzCalc
                    end

                    Stage1 --> subHeader

                    subgraph Stage2 [\"Stage 2: Header Assembly & HTML Body Delivery\"]
                        subHeader[\"Open SMTP data stream for message payload\"]
                        subHeader --> writeMime[\"Write MIME headers, boundary definitions, and HTML body\"]
                        writeMime --> checkAttach{\"Is a file attachment path provided?\"}
                    end

                    Stage2 -->|Yes| subAttach
                    Stage2 -->|No| subFinalize

                    subgraph Stage3 [\"Stage 3: File Attachment Streaming & Boundary Handling\"]
                        subAttach[\"Parse path into directory and file name components\"]
                        subAttach --> writeAttachHdr[\"Write attachment MIME headers and boundary\"]
                        writeAttachHdr --> openFile[\"Open operating system file handle in read mode\"]
                        openFile --> readLine[\"Read line from file via UTL_FILE\"]
                        readLine --> checkLimit{\"Does accumulated size exceed max limit?\"}
                        checkLimit -->|Yes| writeTrunc[\"Append truncation notice and raise size exception\"]
                        checkLimit -->|No| streamLine[\"Stream line to SMTP data channel\"]
                        streamLine --> readLine
                        writeTrunc --> fileCatch[\"Handle file exception or EOF NO_DATA_FOUND\"]
                        fileCatch --> closeFp[\"Close operating system file handle\"]
                    end

                    closeFp --> subFinalize

                    subgraph Stage4 [\"Stage 4: Connection Termination\"]
                        subFinalize[\"Append closing MIME multipart boundary delimiter\"]
                        subFinalize --> closeData[\"Close SMTP data channel\"]
                        closeData --> quitConn[\"Execute SMTP QUIT and terminate socket connection\"]
                    end

                    quitConn --> finishNode([\"End: Routine Completed Successfully\"])
            15. verificationSqlScript: A complete, runnable Oracle PL/SQL verification script block (anonymous block) that tests whether the routine completed successfully. CRITICAL MANDATORY REQUIREMENTS:
                - TOP SQL HEADER COMMENTS: Must begin with an explicit comment block (-- or /* ... */) detailing: (a) WHAT the script is supposed to do (exact validation objectives, row mutation assertions, status checks), (b) WHAT DATABASES and SCHEMAS the script accesses (e.g. Target Database: Oracle OCI Enterprise Database / DEVOLTP, Schemas Accessed: ARS, COMMON, etc.), and (c) all specific tables/views queried.
                - STRICT SCHEMA PREFIXING: ALWAYS prefix the schema name to every table, view, or sequence referenced in SQL statements (e.g. ARS.ARS_STG_SALES, COMMON.AUDIT_LOG). NEVER reference bare table names without their schema prefix in FROM, JOIN, INTO, or UPDATE clauses.
                - STATUS ASSERTIONS: Query modified target tables or audit logs, verify row counts, validate status flags (e.g. 'PROCESSED'), output explicit [PASS] or [FAIL] messages via DBMS_OUTPUT.PUT_LINE, and verify data integrity.
            """, routineType, routineName, schema, pkg, cleanSql);

        ResolvedCallRoute route = resolveCallRoute();
        String currentKey = (route.apiKey != null && !route.apiKey.isEmpty()) ? route.apiKey : apiKey;
        boolean isFreeCall = route.isFreeCall;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (isFreeCall) {
                    enforceAdaptiveBurstGuard();
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

                String requestPayload = mapper.writeValueAsString(root);

                String activeEndpoint = (currentActiveApiUrl != null && !currentActiveApiUrl.isEmpty()) ? currentActiveApiUrl : apiUrl;
                String targetUrl = currentKey.trim().isEmpty() ? activeEndpoint : (activeEndpoint + "?key=" + currentKey.trim());

                HttpResponseWrapper response = executeHttpCall(client, targetUrl, currentKey, requestPayload, 45);
                long elapsed = System.currentTimeMillis() - startMs;

                if (response.statusCode == 200) {
                    JsonNode respJson = mapper.readTree(response.body);
                    JsonNode candidatesNode = respJson.path("candidates");

                    if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
                        System.err.printf("No candidates (Safety/Blocked): %s%n", response.body);
                        RoutineFinding safetyFail = new RoutineFinding(lookupKey, schema, pkg, routineName, "Pending Technical Review (Safety Filter)", "", Collections.emptyList(), Collections.emptyList(), "Reads: [None] | Writes: [None] | Calls: [None]", "Callers: [UNKNOWN] -> Reads: [None] -> Writes: [None]", "N/A", "N/A", "N/A", 1, "Review security/safety guidelines");
                        return safetyFail;
                    }

                    String jsonText = candidatesNode.get(0).path("content").path("parts").get(0).path("text").asText("{}");
                    JsonNode parsed = mapper.readTree(jsonText);

                    byte[] reqBytes = requestPayload.getBytes(StandardCharsets.UTF_8);
                    byte[] respBytes = (response.body != null) ? response.body.getBytes(StandardCharsets.UTF_8) : new byte[0];
                    double kbIn = reqBytes.length / 1024.0;
                    double kbOut = respBytes.length / 1024.0;
                    totalBytesIn += reqBytes.length;
                    totalBytesOut += respBytes.length;

                    JsonNode usage = respJson.path("usageMetadata");
                    long promptTokens = usage.path("promptTokenCount").asLong(1500);
                    long candidateTokens = usage.path("candidatesTokenCount").asLong(350);

                    double callCostPennies = (promptTokens * resolveInputTokenCostPennies(activeModelName, promptTokens)) + (candidateTokens * resolveOutputTokenCostPennies(activeModelName, promptTokens));
                    totalPromptTokens += promptTokens;
                    totalCandidateTokens += candidateTokens;
                    totalCostInPennies += callCostPennies;

                    recordModelRequest(activeModelName, 200, promptTokens, candidateTokens, mapper);
                    dailyRequestsUsed = getModelRequestsUsedToday(activeModelName);

                    if (isFreeCall) {
                        totalFreeCalls++;
                    } else {
                        totalPaidCalls++;
                    }

                    if (activeTierMode == TierMode.HYBRID) {
                        if (isFreeCall) {
                            int activeQuotaLimit = getActiveDailyQuotaLimit();
                            int remaining = Math.max(0, activeQuotaLimit - dailyRequestsUsed);
                            double pctLeft = (remaining / (double) activeQuotaLimit) * 100.0;
                            int currentRpm = getCurrentRpm();
                            int activeRpm = getActiveRpmLimit();
                            System.out.printf("DONE in %dms [HYBRID: FREE | %s] (%.2f KB in / %.2f KB out) | Quota: %d/%d RPD (%d Left - %.1f%%) | Free Rate: %d/%d RPM%n",
                                    elapsed, activeModelName, kbIn, kbOut, dailyRequestsUsed, activeQuotaLimit, remaining, pctLeft, currentRpm, activeRpm);
                        } else {
                            double currentSpentUsd = totalCostInPennies / 100.0;
                            if (activeSpendingCapUsd > 0.0) {
                                double remainingBudgetUsd = Math.max(0.0, activeSpendingCapUsd - currentSpentUsd);
                                System.out.printf("DONE in %dms [HYBRID: PAID (Cooldown Speedup) | %s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Total Spent: $%.4f | Left: $%.4f)%n",
                                        elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, currentSpentUsd, remainingBudgetUsd);
                            } else {
                                System.out.printf("DONE in %dms [HYBRID: PAID (Cooldown Speedup) | %s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Total Spent: $%.4f)%n",
                                        elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, currentSpentUsd);
                            }
                        }
                    } else if (activeTierMode == TierMode.FREE) {
                        int activeQuotaLimit = getActiveDailyQuotaLimit();
                        int remaining = Math.max(0, activeQuotaLimit - dailyRequestsUsed);
                        double pctLeft = (remaining / (double) activeQuotaLimit) * 100.0;
                        int currentRpm = getCurrentRpm();
                        int activeRpm = getActiveRpmLimit();
                        System.out.printf("DONE in %dms [%s] (%.2f KB in / %.2f KB out) | Quota: %d/%d RPD (%d Left - %.1f%%) | Rate: %d/%d RPM%n",
                                elapsed, activeModelName, kbIn, kbOut, dailyRequestsUsed, activeQuotaLimit, remaining, pctLeft, currentRpm, activeRpm);
                    } else {
                        double currentSpentUsd = totalCostInPennies / 100.0;
                        if (activeSpendingCapUsd > 0.0) {
                            double remainingBudgetUsd = Math.max(0.0, activeSpendingCapUsd - currentSpentUsd);
                            System.out.printf("DONE in %dms [%s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Running Total: %.3f¢ | Prepaid Left: $%.4f)%n",
                                    elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, totalCostInPennies, remainingBudgetUsd);
                        } else {
                            System.out.printf("DONE in %dms [%s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Running Total: %.3f¢ | Total Spent: $%.4f)%n",
                                    elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, totalCostInPennies, currentSpentUsd);
                        }
                    }

                    Map<String, Object> logData = new LinkedHashMap<>();
                    logData.put("target", lookupKey);
                    logData.put("schema", schema);
                    logData.put("routine", routineName);
                    logData.put("model", activeModelName);
                    logData.put("tier", isFreeCall ? "FREE" : "PAID");
                    logData.put("elapsedMs", elapsed);
                    logData.put("promptTokens", promptTokens);
                    logData.put("candidateTokens", candidateTokens);
                    logData.put("callCostPennies", Math.round(callCostPennies * 10000.0) / 10000.0);
                    logData.put("totalCostPennies", Math.round(totalCostInPennies * 1000.0) / 1000.0);
                    emitJsonLog("INFO", "API_CALL_DONE", logData);

                    if (displayJsonBlocks) {
                        printPrettyJsonBlock(mapper, lookupKey, parsed);
                    }

                    RoutineFinding result = parseRoutineFindingFromJson(parsed, lookupKey, schema, pkg, routineName);
                    return result;
                } else if (response.statusCode == 429) {
                    boolean isDailyExhausted = response.body != null && (
                            response.body.toLowerCase().contains("per day") ||
                                    response.body.contains("PerDay") ||
                                    response.body.contains("requests_per_day") ||
                                    response.body.toLowerCase().contains("daily limit") ||
                                    response.body.toLowerCase().contains("quota limit per day")
                    );
                    if (isDailyExhausted) {
                        recordModel429Exhaustion(activeModelName, mapper);
                        if (isFreeCall) {
                            freeTierExhausted = true;
                        }
                    }
                    if (isFreeCall && activeTierMode == TierMode.HYBRID && !activePaidApiKey.isEmpty() && !activePaidApiKey.equals(currentKey)) {
                        System.out.printf("   [Hybrid Failover] Free Tier Rate Limit 429 -> Seamlessly routing '%s' to Paid Tier key...%n", lookupKey);
                        currentKey = activePaidApiKey;
                        isFreeCall = false;
                        attempt = 0;
                        continue;
                    }
                    Map<String, Object> rateData = new LinkedHashMap<>();
                    rateData.put("target", lookupKey);
                    rateData.put("httpStatus", 429);
                    rateData.put("model", activeModelName);
                    rateData.put("attempt", attempt);
                    rateData.put("maxRetries", MAX_RETRIES);
                    rateData.put("dailyExhausted", isDailyExhausted);
                    emitJsonLog("WARN", "RATE_LIMIT_429", rateData);

                    if (isDailyExhausted || attempt == MAX_RETRIES) {
                        boolean failedOver = tryFailoverToNextModel(client, mapper, currentKey);
                        if (failedOver) {
                            attempt = 0;
                            continue;
                        }
                    }
                    long backoffMs = isFreeCall ? (6000L * attempt) : (RETRY_DELAY_MS * attempt);
                    System.out.printf("RATE LIMIT 429 (Pacing %.1fs, Retry %d/%d)... ", (backoffMs / 1000.0), attempt, MAX_RETRIES);
                    Thread.sleep(backoffMs);
                } else if (response.statusCode == 503 || response.statusCode == 500 || response.statusCode == 502 || response.statusCode == 504) {
                    if (isFreeCall && !activePaidApiKey.isEmpty() && !activePaidApiKey.equals(currentKey) && !isSpendingCapReached(activeSpendingCapUsd)) {
                        System.out.printf("   [Transient %d Server Busy] Free Tier Busy -> Seamlessly switching to Paid Tier key for '%s'...%n", response.statusCode, lookupKey);
                        currentKey = activePaidApiKey;
                        isFreeCall = false;
                        attempt = 0;
                        continue;
                    }
                    long backoffMs = isFreeCall ? (5000L * attempt) : (RETRY_DELAY_MS * attempt);
                    System.out.printf("SERVER BUSY %d (Pacing %.1fs, Retry %d/%d)... ", response.statusCode, (backoffMs / 1000.0), attempt, MAX_RETRIES);
                    Map<String, Object> busyData = new LinkedHashMap<>();
                    busyData.put("target", lookupKey);
                    busyData.put("httpStatus", response.statusCode);
                    busyData.put("model", activeModelName);
                    busyData.put("attempt", attempt);
                    busyData.put("maxRetries", MAX_RETRIES);
                    busyData.put("backoffSeconds", backoffMs / 1000.0);
                    emitJsonLog("WARN", "SERVER_BUSY", busyData);
                    Thread.sleep(backoffMs);
                } else {
                    System.err.printf("%n   [HTTP %d Response Body]: %s%n", response.statusCode, response.body);
                    Map<String, Object> errData = new LinkedHashMap<>();
                    errData.put("target", lookupKey);
                    errData.put("httpStatus", response.statusCode);
                    errData.put("model", activeModelName);
                    errData.put("responseBody", response.body);
                    emitJsonLog("ERROR", "HTTP_ERROR", errData);
                    if (response.statusCode == 400 || response.statusCode == 401 || response.statusCode == 403) {
                        RoutineFinding authFail = new RoutineFinding(lookupKey, schema, pkg, routineName, "Pending Technical Review (HTTP " + response.statusCode + ")", "", Collections.emptyList(), Collections.emptyList(), "Reads: [None] | Writes: [None] | Calls: [None]", "Callers: [UNKNOWN] -> Reads: [None] -> Writes: [None]", "N/A", "N/A", "N/A", 1, "Verify API Authentication");
                        return authFail;
                    }
                }
            } catch (Exception ex) {
                if (isFreeCall && !activePaidApiKey.isEmpty() && !activePaidApiKey.equals(currentKey) && !isSpendingCapReached(activeSpendingCapUsd)) {
                    System.out.printf("   [Timeout/Network Failover] Free Endpoint Timeout (%s) -> Seamlessly switching to Paid Tier key for '%s'...%n", ex.getClass().getSimpleName(), lookupKey);
                    currentKey = activePaidApiKey;
                    isFreeCall = false;
                    attempt = 0;
                    continue;
                }
                if (attempt == MAX_RETRIES) {
                    String rootCause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    System.err.printf("FAILED [%s: %s]%n", ex.getClass().getSimpleName(), rootCause);
                    RoutineFinding fail = new RoutineFinding(lookupKey, schema, pkg, routineName, "Pending Technical Review", ex.getClass().getSimpleName(), Collections.emptyList(), Collections.emptyList(), "Reads: [None] | Writes: [None] | Calls: [None]", "Callers: [UNKNOWN] -> Reads: [None] -> Writes: [None]", "N/A", "N/A", "N/A", 1, "Review network connectivity");
                    return fail;
                }
            }
        }

        RoutineFinding fallback = new RoutineFinding(lookupKey, schema, pkg, routineName, "Pending Technical Review (Timeout)", "Batch execution", Collections.emptyList(), Collections.emptyList(), "Reads: [None] | Writes: [None] | Calls: [None]", "Callers: [UNKNOWN] -> Reads: [None] -> Writes: [None]", "N/A", "N/A", "N/A", 1, "Retry analysis");
        return fallback;
    }

    /**
     * Shared parser for structured Gemini routine research JSON payloads.
     */
    private static RoutineFinding parseRoutineFindingFromJson(JsonNode parsed, String lookupKey, String schema, String pkg, String routineName) {
        String what = parsed.path("what").asText("").replaceAll("[\\r\\n\\t]+", " ").trim();
        String why = parsed.path("why").asText("").replaceAll("[\\r\\n\\t]+", " ").trim();
        String how = parsed.path("how").asText(parsed.path("How").asText("")).replaceAll("[\\r\\n\\t]+", " ").trim();

        List<String> inParams = new ArrayList<>();
        List<String> outParams = new ArrayList<>();
        JsonNode paramsNode = parsed.path("parameters");
        if (paramsNode.isObject()) {
            JsonNode inArr = paramsNode.path("in");
            if (!inArr.isArray()) inArr = paramsNode.path("In");
            if (inArr.isArray()) inArr.forEach(p -> { if (!p.asText("").trim().isEmpty()) inParams.add(p.asText().trim()); });

            JsonNode outArr = paramsNode.path("out");
            if (!outArr.isArray()) outArr = paramsNode.path("Out");
            if (outArr.isArray()) outArr.forEach(p -> { if (!p.asText("").trim().isEmpty()) outParams.add(p.asText().trim()); });
        } else if (paramsNode.isTextual()) {
            String rawP = paramsNode.asText("").trim();
            if (!rawP.isEmpty() && !rawP.equalsIgnoreCase("None") && !rawP.equalsIgnoreCase("N/A")) {
                inParams.add(rawP);
            }
        }

        String deps = parsed.path("dependencies").asText("Reads: [None] | Writes: [None] | Calls: [None]").replaceAll("[\\r\\n\\t]+", " ").trim();
        String dfd = parsed.path("dataFlowDiagram").asText("").replaceAll("[\\r\\n\\t]+", " ").trim();
        if (dfd.isEmpty()) {
            dfd = String.format("Callers: [BATCH: IWS Batch Job] -> %s", deps.replace(" | ", " -> "));
        }
        String bRules = parsed.path("businessRules").asText("Standard Florida Lottery transaction processing rules.").replaceAll("[\\r\\n\\t]+", " ").trim();
        String errHandling = parsed.path("errorHandling").asText("Standard transaction boundary.").replaceAll("[\\r\\n\\t]+", " ").trim();
        String testStrat = parsed.path("testingStrategy").asText("Execute with test parameters and verify ledger status.").replaceAll("[\\r\\n\\t]+", " ").trim();
        int comp = parsed.path("complexityScore").asInt(1);
        String recs = parsed.path("recommendations").asText(parsed.path("suggestions").asText("None / Code is optimal")).replaceAll("[\\r\\n\\t]+", " ").trim();
        String mermaid = cleanAndRepairMermaidSyntax(parsed.path("mermaidDiagram").asText(""));
        String verifySql = parsed.path("verificationSqlScript").asText(parsed.path("VerificationSqlScript").asText("")).trim();
        if (verifySql.startsWith("```")) {
            verifySql = verifySql.replaceAll("^```[a-zA-Z]*\\R?", "").replaceAll("\\R?```$", "").trim();
        }

        RoutineFinding result = new RoutineFinding(lookupKey, schema, pkg, routineName, what, why, how, inParams, outParams, deps, dfd, bRules, errHandling, testStrat, comp, recs, mermaid, verifySql);
        return result;
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

        // Fix run-on statements where a closing shape is immediately followed by a new node definition or 'end'
        cleaned = cleaned.replaceAll("([)\\]\\}])[ \\t\\u00A0]*([a-zA-Z0-9_]+[ \\t\\u00A0]*[\\(\\[\\{])", "$1\n    $2");
        cleaned = cleaned.replaceAll("([)\\]\\}])[ \\t\\u00A0]+(?i)end\\b", "$1\n    end");
        cleaned = cleaned.replaceAll("(?i)\\bend[ \\t\\u00A0]+([a-zA-Z0-9_#$]+|%%|//|#)", "end\n    $1");

        // 4. Line-by-line syntax verification and auto-repair
        String[] lines = cleaned.split("\\R");
        StringBuilder sb = new StringBuilder();
        Set<String> declaredSubgraphIds = new HashSet<>();
        List<String> hoistedClassStatements = new ArrayList<>();
        int openSubgraphs = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n");
                continue;
            }

            // Hoist standalone class and classDef directives so they never remain trapped inside subgraphs
            if (trimmed.matches("(?i)^classDef\\s+.*") || trimmed.matches("(?i)^class\\s+[a-zA-Z0-9_,\\s]+\\s+[a-zA-Z0-9_]+;?")) {
                hoistedClassStatements.add(trimmed);
                continue;
            }

            // Normalize non-standard variants of subgraph closing 'end' (e.g. end;, END, end subgraph, end Stage 2, end [Stage 1], end %%)
            boolean isClosingEnd = false;
            if (trimmed.equalsIgnoreCase("end") || trimmed.equalsIgnoreCase("endsubgraph")) {
                isClosingEnd = true;
            } else if (trimmed.matches("(?i)^end\\s*(;|%%.*|//.*|--.*|#.*)?$")) {
                isClosingEnd = true;
            } else if (openSubgraphs > 0 && trimmed.matches("(?i)^end(?:\\s*[\\[\\(:/\\-%#;].*|\\s+[a-zA-Z0-9_\\[\\(\\)].*|;.*)?$")
                    && !trimmed.contains("-->") && !trimmed.contains("==>") && !trimmed.contains("-.->")
                    && !trimmed.matches("(?i)^end\\s*\\(\\[.*")) {
                isClosingEnd = true;
            }

            if (isClosingEnd) {
                trimmed = "end";
                if (openSubgraphs > 0) {
                    openSubgraphs--;
                }
            }

            // Normalize HTML tags inside labels (<br> to <br/> and remove unsupported styling tags)
            if (trimmed.contains("<") && trimmed.contains(">")) {
                trimmed = trimmed.replaceAll("(?i)<br\\s*/?>", "<br/>");
                trimmed = trimmed.replaceAll("(?i)</?(?:font|b|i|u|span|div|p|strong|em)(?:\\s+[^>]*)?>", "");
            }

            // A. Auto-repair unclosed or unbracketed subgraph headers: e.g. subgraph Stage 1: Load Data or subgraph Stage2 ["Stage 2: ... "
            if (trimmed.matches("(?i)^subgraph\\s+.*") && !trimmed.matches("(?i)^subgraphNode\\b.*")) {
                if (trimmed.matches("(?i)^subgraph\\s+([a-zA-Z0-9_-]+)\\s*:\\s*(.+)$")) {
                    java.util.regex.Matcher sm = java.util.regex.Pattern.compile("(?i)^subgraph\\s+([a-zA-Z0-9_-]+)\\s*:\\s*(.+)$").matcher(trimmed);
                    if (sm.find()) {
                        String sId = sm.group(1).replaceAll("[^a-zA-Z0-9_]", "_");
                        String sTitle = sm.group(1) + ": " + sm.group(2).trim();
                        trimmed = "subgraph " + sId + " [\"" + sTitle.replace("\"", "'") + "\"]";
                    }
                } else if (trimmed.matches("(?i)^subgraph\\s+([a-zA-Z0-9_-]+)\\s+([^\\[\"\\r\\n].*)$") && !trimmed.contains("[")) {
                    java.util.regex.Matcher sm = java.util.regex.Pattern.compile("(?i)^subgraph\\s+([a-zA-Z0-9_-]+)\\s+([^\\[\"\\r\\n].*)$").matcher(trimmed);
                    if (sm.find()) {
                        String sId = (sm.group(1) + "_" + sm.group(2)).replaceAll("[^a-zA-Z0-9_]", "_");
                        String sTitle = sm.group(1) + " " + sm.group(2).trim();
                        trimmed = "subgraph " + sId + " [\"" + sTitle.replace("\"", "'") + "\"]";
                    }
                } else if (trimmed.contains("[\"") && !trimmed.endsWith("]")) {
                    if (!trimmed.endsWith("\"")) {
                        trimmed = trimmed + "\"]";
                    } else {
                        trimmed = trimmed + "]";
                    }
                }
            }

            // Track subgraph IDs and nesting depth
            if (trimmed.matches("(?i)^subgraph(?:\\s+.*|$)") && !trimmed.matches("(?i)^subgraphNode\\b.*")
                    && !trimmed.contains("-->") && !trimmed.contains("==>") && !trimmed.contains("-.->")) {
                openSubgraphs++;
                String afterSub = trimmed.replaceFirst("(?i)^subgraph\\s+", "").trim();
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
                } else {
                    trimmed = trimmed + "])";
                }
            }

            // C. Fix undeclared subgraph typo prefix: e.g. subgraphStage5 --> finishNode
            if (trimmed.startsWith("subgraphStage") && trimmed.contains("-->")) {
                trimmed = trimmed.replace("subgraphStage", "Stage");
            }

            // D. Sanitize conditional edges directly attached to a subgraph container
            for (String sId : declaredSubgraphIds) {
                if (trimmed.startsWith(sId + " -->|") || trimmed.startsWith(sId + "-->|")) {
                    int pipeEnd = trimmed.indexOf("|", trimmed.indexOf("|") + 1);
                    if (pipeEnd > 0) {
                        String afterPipe = trimmed.substring(pipeEnd + 1).trim();
                        trimmed = sId + " --> " + afterPipe;
                    }
                }
            }

            // E. Clean malformed / double edges (e.g. -->|label|--> -> -->|label|, -->|--> -> -->, -->||--> -> -->, -->--> -> -->)
            trimmed = trimmed.replaceAll("(-->|-.->|==>)\\s*\\|([^|\\r\\n]+)\\|\\s*(-->|-.->|==>)", "$1|$2| ")
                             .replaceAll("-->\\|-->", "-->")
                             .replaceAll("-->\\|\\|-->", "-->")
                             .replaceAll("-->\\|\\s*\\|-->", "-->")
                             .replaceAll("-->-->+", "-->")
                             .replaceAll("-\\.- >-\\.- >+", "-.->")
                             .replaceAll("==>==>+", "==>");

            // F. Auto-repair premature closing quotes before extensions across all shapes
            // Square: build["Content_Types"].xml"] -> build["Content_Types.xml"]
            if (trimmed.matches(".*\\b\\w+\\s*\\[\\s*\"[^\"]*?\"\\][^\\r\\n\\[\\]]*?\"?\\s*\\].*")) {
                trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\[\\s*\"[^\"]*?)\"\\]([^\\r\\n\\[\\]]*?)\"?\\s*\\]", "$1$2\"]");
            }
            // Pill: node(["File"].ext"]) -> node(["File.ext"])
            if (trimmed.matches(".*\\b\\w+\\s*\\(\\s*\\[\\s*\"[^\"]*?\"\\][^\\r\\n\\[\\]]*?\"?\\s*\\]\\).*")) {
                trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\(\\s*\\[\\s*\"[^\"]*?)\"\\]([^\\r\\n\\[\\]]*?)\"?\\s*\\]\\)", "$1$2\"])");
            }
            // Cylinder: db[("DB"].dat")] -> db[("DB.dat")]
            if (trimmed.matches(".*\\b\\w+\\s*\\[\\s*\\(\\s*\"[^\"]*?\"\\][^\\r\\n\\[\\]]*?\"?\\s*\\)\\]\\s*.*")) {
                trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\[\\s*\\(\\s*\"[^\"]*?)\"\\]([^\\r\\n\\[\\]]*?)\"?\\s*\\)\\]", "$1$2\")]");
            }

            // G. Auto-repair nested inner brackets inside node definitions (convert internal [...] to (...))
            // e.g. query["SELECT [COL1], [COL2] FROM [TABLE]"] -> query["SELECT (COL1), (COL2) FROM (TABLE)"]
            if (trimmed.contains("[") && trimmed.contains("]")) {
                String prev;
                do {
                    prev = trimmed;
                    trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\[[^\\r\\n\\]]*?)\\[([^\\[\\]\\r\\n]+)\\]", "$1($2)");
                } while (!trimmed.equals(prev));
            }

            // H. Auto-repair missing quotes inside shape brackets
            if (trimmed.matches(".*\\w+\\[[^\"]+\\].*") && !trimmed.contains("-->") && !trimmed.startsWith("subgraph")
                    && !trimmed.startsWith("classDef") && !trimmed.startsWith("class ")) {
                trimmed = trimmed.replaceAll("(\\w+)\\[([^\"]+)\\]", "$1[\"$2\"]");
            }

            // I. Auto-repair mismatched syntax brackets / trailing parenthesis on square bracket nodes
            if (trimmed.matches(".*\\b\\w+\\s*\\[[^\\]]+\\]\\).*")) {
                trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\[[^\\]]+\\])\\)", "$1");
            }
            if (trimmed.matches(".*\\b\\w+\\s*\\[\"[^\"]+\"\\).*") && !trimmed.contains("-->")) {
                trimmed = trimmed.replaceAll("(\\b\\w+\\s*\\[\"[^\"]+)\"\\)", "$1\"]");
            }

            sb.append("    ").append(trimmed).append("\n");
        }

        // Auto-close any unclosed subgraphs to guarantee balanced blocks
        while (openSubgraphs > 0) {
            sb.append("    end\n");
            openSubgraphs--;
        }

        // Append hoisted class statements at root scope
        for (String hoisted : hoistedClassStatements) {
            sb.append("    ").append(hoisted).append("\n");
        }

        String result = sb.toString().trim();
        result = DocxDiagramHelper.liftStartNodeOutsideSubgraph(result);
        return result;
    }

    /**
     * Executes adaptive batch requests (1x, 2x, or 4x) against Google AI Studio with cost tracking.
     */
    private static List<RoutineFinding> callGeminiAiStudioBatchWithCost(
            HttpClient client, ObjectMapper mapper, String apiUrl, String apiKey,
            List<RoutineBatchItem> batchItems) {
        if (batchItems == null || batchItems.isEmpty()) {
            return Collections.emptyList();
        }
        if (batchItems.size() == 1) {
            RoutineBatchItem single = batchItems.get(0);
            System.out.printf(" Calling AI API -> %-40s ... ", single.lookupKey);
            RoutineFinding f = callGeminiAiStudioWithCost(client, mapper, apiUrl, apiKey,
                    single.schema, single.pkg, single.routineName, single.routineType, single.fullSql);
            return Collections.singletonList(f);
        }

        long startMs = System.currentTimeMillis();
        String pkgLabel = (batchItems.get(0).pkg != null && !batchItems.get(0).pkg.equals("-")) ? batchItems.get(0).pkg : "STANDALONE";
        List<String> routineNames = batchItems.stream().map(b -> b.routineName).toList();
        String summaryRoutines = (routineNames.size() <= 4) ? String.join(", ", routineNames) : (routineNames.get(0) + " .. " + routineNames.get(routineNames.size() - 1));
        String batchHeader = String.format("%s.%s (%d routines: %s)", batchItems.get(0).schema, pkgLabel, batchItems.size(), summaryRoutines);
        System.out.printf(" Calling AI API [Batch x%d] -> %-45s ... ", batchItems.size(), batchHeader);

        Map<String, Object> startData = new LinkedHashMap<>();
        startData.put("target", batchHeader);
        startData.put("schema", batchItems.get(0).schema);
        startData.put("package", pkgLabel);
        startData.put("model", activeModelName);
        startData.put("batched", batchItems.size());
        List<String> rNames = new ArrayList<>();
        for (RoutineBatchItem r : batchItems) rNames.add(r.routineName);
        startData.put("routines", rNames);
        emitJsonLog("INFO", "API_CALL_START", startData);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a staff database engineer and technical documentation lead for the Florida Lottery.\n");
        sb.append(String.format("Analyze the following %d legacy Oracle database routines under schema '%s' (Package: %s).\n\n",
                batchItems.size(), batchItems.get(0).schema, pkgLabel));

        for (int i = 0; i < batchItems.size(); i++) {
            RoutineBatchItem item = batchItems.get(i);
            sb.append("================================================================================\n");
            sb.append(String.format("ROUTINE #%d of %d: %s (Type: %s, Schema: %s, Package: %s)\n",
                    i + 1, batchItems.size(), item.routineName, item.routineType, item.schema, item.pkg));
            sb.append("================================================================================\n");
            sb.append("SQL Source:\n```sql\n");
            sb.append(item.fullSql.isEmpty() ? "-- (No direct SQL body available)\n" : item.fullSql.trim());
            sb.append("\n```\n\n");
        }

        sb.append(String.format("""
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
            - Florida Lottery Domain Context: Accurately reference lottery gaming concepts (e.g., retailer weekly settlement, instant scratch-off/terminal game validations, draw sales, jackpot liability, commission ledger, staging tables).
            - Do NOT assume or mention any modern ETL framework unless explicitly indicated in the SQL comments or code.
            - TERMINOLOGY & VOCABULARY STANDARD:
              * Avoid the jargon words "governance", "ingest"/"ingress", and "persistence".
              * Use clear, natural alternatives:
                - Instead of "Ingress / Ingestion", use "Source Systems & Callers", "Inputs", or "Entry Points".
                - Instead of "Persistence", use "Database Storage & Target Tables", "Target Tables", or "Database Records".
                - Instead of "Governance", use "Technical Specifications & Standards", "Compliance Rules", or "Operational Standards".
            
            Provide a strict JSON response containing a JSON ARRAY of %d objects adhering to the response schema (one object for each routine in order):
            [
              {
                "routineName": "EXACT_ROUTINE_NAME",
                "what": "2 to 5 clear, plain-English sentences explaining WHAT this routine does step-by-step (naming exact target tables, validations, and mutations; NEVER using generic phrases like 'core tables').",
                "why": "2 to 5 clear sentences explaining WHY this routine exists from a business and batch operations standpoint.",
                "how": "2 to 5 plain-English sentences explaining technical mechanics for developers (cursor loops vs set-based/bulk collect, commit boundaries, dynamic SQL).",
                "callerChannels": ["Web Portals & Apps", "Batch IWS Schedulers", "Oracle Forms / Retail UI"],
                "flowSteps": ["1. Step one", "2. Step two", "3. Step three"],
                "parameters": {
                  "in": ["p_arg (VARCHAR2) - description", ...],
                  "out": ["p_res (NUMBER) - description", ...]
                },
                "dependencies": "Reads: [STAGE.TBL1(COL1)] | Updates: [None] | Writes: [None] | Calls: [None]",
                "dataFlowDiagram": "Callers: [BATCH: Script] -> Reads: [STAGE.TBL1] -> Writes: [None]",
                "businessRules": "Concise summary of key business rules and formulas.",
                "errorHandling": "Clear explanation of exception handling and rollback behavior.",
                "testingStrategy": "Step-by-step instructions for a developer or QA analyst to test this routine.",
                "complexityScore": 1-10,
                "recommendations": "Plain-English code review and performance suggestions.",
                "mermaidDiagram": "A valid, accurate Mermaid flowchart diagram capturing the complete, granular step-by-step chronological execution trace of this routine in valid 'flowchart TD' syntax adhering strictly to the Florida Lottery Gold Standard Flowchart Guidelines:\n1. OPERATIONAL NARRATIVE & ACTIVE ACTION VERBS: Every node must narrate what the code is functionally doing in plain, active English with technical precision (e.g., [\"Open SMTP connection to relay host on port 25\"], [\"Tokenize and register TO recipients via RCPT\"], [\"Query session timezone and format GMT offset\"], [\"Bulk insert calculated commission records into target ledger\"]). Never repeat raw variable syntax (e.g., do NOT write [v_conn := UTL_SMTP.OPEN_CONNECTION]).\n2. MODULAR STAGE SUBGRAPHS (ANTI-CROWDING RULE): Partition multi-step pipelines into balanced subgraphs with a space before the bracket: e.g., subgraph Stage1 [\"Stage 1: Connection & Recipient Handshake\"], subgraph Stage2 [\"Stage 2: Validation & Transformation Loop\"], subgraph Stage3 [\"Stage 3: Reconciliation & Target Ledger Update\"], subgraph Stage4 [\"Stage 4: Post-Processing & Connection Finalization\"]. STRICTLY limit each subgraph to 3 to 6 operational nodes per stage to prevent overcrowding and visual clutter.\n3. CLEAN INTER-STAGE HANDOFFS: Connect subgraphs or stages cleanly in sequential chronological order (Stage1 --> Stage2 --> Stage3). NEVER draw cross-stage diagonal spaghetti arrows that crisscross through multiple subgraphs. Keep table, view, and file entity nodes localized inside the stage that operates on them. NEVER attach conditional branch labels (|Yes| / |No|) to a subgraph container boundary (e.g., do NOT write Stage1 -->|No| subLoad; connect the decision diamond node directly: checkErr -->|No| subLoad).\n4. ELEVATE CURSOR LOOPS TO FUNCTIONAL SUBGRAPHS: Abstract row-by-row cursor loops into a clean subgraph that clearly illustrates the main transformation path, row rejection branches (-->|No / Reject| logRej[(\"<b>TABLE / ERROR LOG</b><br/>ERROR_LOG\")]), and the batch commit.\n5. STRICT BRACKET MATCHING & CLEAN SYNTAX: Never mix opening square brackets with closing parentheses (e.g., openCursor[\"Open cursor...\"]) is INVALID). Processes MUST be node[\"Action\"], decisions cond{\"Question?\"}, cylindrical tables [(\"...\")], parallelograms [/\"...\"/], and terminals term([\"Start/End: ...\"]).\n6. SELF-CONTAINED ENTITY LABELS: In short routines, never use generic labels like start([\"Start\"]), cursor[Open C1], or endNode([\"End\"]). Always name the specific source, staging, and target tables/entities (e.g., readDb2[(\"<b>TABLE</b><br/>ARTDNBR_DB2\")] --> cleanIns[(\"<b>TABLE</b><br/>ARTDNBR\")]).\n7. DISTINCT SUCCESS VS. ABORT TERMINALS: Explicitly separate successful completion terminals from fatal rollback/abortion terminals: e.g., successNode([\"End: Synchronization Complete\"]) vs. failNode([\"End: Rolled Back on Error\"]). Never route fatal errors to a 'Completed Successfully' pill.\n8. CONVERSATIONAL DECISION QUESTIONS & MANDATORY DUAL BRANCHES (YES & NO): Every decision diamond must ask a full, plain-English question in double quotes: e.g., checkCC{\"Are CC recipients specified?\"}, checkLimit{\"Does accumulated size exceed max limit?\"}, isSuccess{\"Did the batch load complete successfully?\"}. CRITICAL: Every decision diamond MUST have ALL outbound branches explicitly defined directly from the diamond (both |Yes| and |No|). NEVER leave a decision diamond dangling with only one branch.\n9. ACCURATE LOOPS & DDL PLACEMENT: Trace loops with a decision diamond, routing |Yes| through the loop body and looping back up, and |No| forward. Place DDL statements (like constraint enablement) and commits exactly when they occur in the code.\n10. EXPLICIT NODE ARCHETYPE LABELS: Whenever referencing procedures, tables, views, triggers, or files, format the archetype in bold above the identifier (e.g., [\"<b>PROCEDURE</b><br/>SCHEMA.PROC_NAME\"], [(\"<b>TABLE</b><br/>SCHEMA.TABLE_NAME\")], [(\"<b>VIEW</b><br/>SCHEMA.VIEW_NAME\")], [/\"<b>DATA FILE</b><br/>filename.dat\"/]) so non-technical stakeholders instantly recognize the component type.\n\nGOLD STANDARD REFERENCE PATTERN (Follow this structural layout):\nflowchart TD\n    startNode([\\\"Start: Routine Objective\\\"]) --> subConn\n\n    subgraph Stage1 [\\\"Stage 1: Connection & Recipient Handshake\\\"]\n        subConn[\\\"Open SMTP connection to relay host on port 25\\\"]\n        subConn --> helo[\\\"Perform HELO handshake & register sender address\\\"]\n        helo --> parseTo[\\\"Tokenize and register TO recipients via RCPT\\\"]\n        parseTo --> checkCC{\\\"Are CC recipients specified?\\\"}\n        checkCC -->|Yes| parseCC[\\\"Tokenize and register CC recipients via RCPT\\\"]\n        checkCC -->|No| checkBCC{\\\"Are BCC recipients specified?\\\"}\n        parseCC --> checkBCC\n        checkBCC -->|Yes| parseBCC[\\\"Tokenize and register BCC recipients via RCPT\\\"]\n        checkBCC -->|No| tzCalc[\\\"Query session timezone and format GMT offset\\\"]\n        parseBCC --> tzCalc\n    end\n\n    Stage1 --> subHeader\n\n    subgraph Stage2 [\\\"Stage 2: Header Assembly & HTML Body Delivery\\\"]\n        subHeader[\\\"Open SMTP data stream for message payload\\\"]\n        subHeader --> writeMime[\\\"Write MIME headers, boundary definitions, and HTML body\\\"]\n        writeMime --> checkAttach{\\\"Is a file attachment path provided?\\\"}\n    end\n\n    Stage2 -->|Yes| subAttach\n    Stage2 -->|No| subFinalize\n\n    subgraph Stage3 [\\\"Stage 3: File Attachment Streaming & Boundary Handling\\\"]\n        subAttach[\\\"Parse path into directory and file name components\\\"]\n        subAttach --> writeAttachHdr[\\\"Write attachment MIME headers and boundary\\\"]\n        writeAttachHdr --> openFile[\\\"Open operating system file handle in read mode\\\"]\n        openFile --> readLine[\\\"Read line from file via UTL_FILE\\\"]\n        readLine --> checkLimit{\\\"Does accumulated size exceed max limit?\\\"}\n        checkLimit -->|Yes| writeTrunc[\\\"Append truncation notice and raise size exception\\\"]\n        checkLimit -->|No| streamLine[\\\"Stream line to SMTP data channel\\\"]\n        streamLine --> readLine\n        writeTrunc --> fileCatch[\\\"Handle file exception or EOF NO_DATA_FOUND\\\"]\n        fileCatch --> closeFp[\\\"Close operating system file handle\\\"]\n    end\n\n    closeFp --> subFinalize\n\n    subgraph Stage4 [\\\"Stage 4: Connection Termination\\\"]\n        subFinalize[\\\"Append closing MIME multipart boundary delimiter\\\"]\n        subFinalize --> closeData[\\\"Close SMTP data channel\\\"]\n        closeData --> quitConn[\\\"Execute SMTP QUIT and terminate socket connection\\\"]\n    end\n\n    quitConn --> finishNode([\\\"End: Process Completed Successfully\\\"])",
                "verificationSqlScript": "A complete, runnable Oracle PL/SQL verification script block (anonymous block) that tests whether this routine completed successfully. CRITICAL MANDATORY REQUIREMENTS:\n1. TOP SQL HEADER COMMENTS: Must begin with an explicit comment block (-- or /* ... */) detailing: (a) WHAT the script is supposed to do (exact validation objectives, row mutation assertions, status checks), (b) WHAT DATABASES and SCHEMAS the script accesses (e.g. Target Database: Oracle OCI Enterprise Database / DEVOLTP, Schemas Accessed: ARS, COMMON, etc.), and (c) all specific tables/views queried.\n2. STRICT SCHEMA PREFIXING: ALWAYS prefix the schema name to every table, view, or sequence referenced in SQL statements (e.g. ARS.ARS_STG_SALES, COMMON.AUDIT_LOG). NEVER reference bare table names without their schema prefix in FROM, JOIN, INTO, or UPDATE clauses.\n3. STATUS ASSERTIONS: Query modified target tables or audit logs, verify row counts, validate status flags (e.g. 'PROCESSED'), output explicit '[PASS] ...' or '[FAIL] ...' status messages via DBMS_OUTPUT.PUT_LINE, and verify data integrity."
              }
            ]
            """, batchItems.size()));

        String prompt = sb.toString();

        ResolvedCallRoute route = resolveCallRoute();
        String currentKey = (route.apiKey != null && !route.apiKey.isEmpty()) ? route.apiKey : apiKey;
        boolean isFreeCall = route.isFreeCall;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (isFreeCall) {
                    enforceAdaptiveBurstGuard();
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

                String requestPayload = mapper.writeValueAsString(root);
                String activeEndpoint = (currentActiveApiUrl != null && !currentActiveApiUrl.isEmpty()) ? currentActiveApiUrl : apiUrl;
                String targetUrl = currentKey.trim().isEmpty() ? activeEndpoint : (activeEndpoint + "?key=" + currentKey.trim());

                HttpResponseWrapper response = executeHttpCall(client, targetUrl, currentKey, requestPayload, 60);
                long elapsed = System.currentTimeMillis() - startMs;

                if (response.statusCode == 200) {
                    JsonNode respJson = mapper.readTree(response.body);
                    JsonNode candidatesNode = respJson.path("candidates");

                    if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
                        System.err.printf("No candidates (Safety/Blocked): %s%n", response.body);
                        return fallbackToIndividualCalls(client, mapper, apiUrl, currentKey, batchItems);
                    }

                    String jsonText = candidatesNode.get(0).path("content").path("parts").get(0).path("text").asText("[]");
                    JsonNode parsedArray = mapper.readTree(jsonText);

                    byte[] reqBytes = requestPayload.getBytes(StandardCharsets.UTF_8);
                    byte[] respBytes = (response.body != null) ? response.body.getBytes(StandardCharsets.UTF_8) : new byte[0];
                    double kbIn = reqBytes.length / 1024.0;
                    double kbOut = respBytes.length / 1024.0;
                    totalBytesIn += reqBytes.length;
                    totalBytesOut += respBytes.length;

                    JsonNode usage = respJson.path("usageMetadata");
                    long promptTokens = usage.path("promptTokenCount").asLong(1500L * batchItems.size());
                    long candidateTokens = usage.path("candidatesTokenCount").asLong(400L * batchItems.size());

                    double callCostPennies = (promptTokens * resolveInputTokenCostPennies(activeModelName, promptTokens)) + (candidateTokens * resolveOutputTokenCostPennies(activeModelName, promptTokens));
                    totalPromptTokens += promptTokens;
                    totalCandidateTokens += candidateTokens;
                    totalCostInPennies += callCostPennies;

                    recordModelRequest(activeModelName, 200, promptTokens, candidateTokens, mapper);
                    dailyRequestsUsed = getModelRequestsUsedToday(activeModelName);

                    if (isFreeCall) {
                        totalFreeCalls++;
                    } else {
                        totalPaidCalls++;
                    }

                    if (activeTierMode == TierMode.HYBRID) {
                        if (isFreeCall) {
                            int activeQuotaLimit = getActiveDailyQuotaLimit();
                            int remaining = Math.max(0, activeQuotaLimit - dailyRequestsUsed);
                            double pctLeft = (remaining / (double) activeQuotaLimit) * 100.0;
                            int currentRpm = getCurrentRpm();
                            int activeRpm = getActiveRpmLimit();
                            System.out.printf("DONE in %dms [HYBRID: FREE | %s] (%.2f KB in / %.2f KB out) | Quota: %d/%d RPD (%d Left - %.1f%%) | Free Rate: %d/%d RPM | Batched: %d procs%n",
                                    elapsed, activeModelName, kbIn, kbOut, dailyRequestsUsed, activeQuotaLimit, remaining, pctLeft, currentRpm, activeRpm, batchItems.size());
                        } else {
                            double currentSpentUsd = totalCostInPennies / 100.0;
                            if (activeSpendingCapUsd > 0.0) {
                                double remainingBudgetUsd = Math.max(0.0, activeSpendingCapUsd - currentSpentUsd);
                                System.out.printf("DONE in %dms [HYBRID: PAID (Cooldown Speedup) | %s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Total Spent: $%.4f | Left: $%.4f | Batched: %d procs)%n",
                                        elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, currentSpentUsd, remainingBudgetUsd, batchItems.size());
                            } else {
                                System.out.printf("DONE in %dms [HYBRID: PAID (Cooldown Speedup) | %s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Total Spent: $%.4f | Batched: %d procs)%n",
                                        elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, currentSpentUsd, batchItems.size());
                            }
                        }
                    } else if (activeTierMode == TierMode.FREE) {
                        int activeQuotaLimit = getActiveDailyQuotaLimit();
                        int remaining = Math.max(0, activeQuotaLimit - dailyRequestsUsed);
                        double pctLeft = (remaining / (double) activeQuotaLimit) * 100.0;
                        int currentRpm = getCurrentRpm();
                        int activeRpm = getActiveRpmLimit();
                        System.out.printf("DONE in %dms [%s] (%.2f KB in / %.2f KB out) | Quota: %d/%d RPD (%d Left - %.1f%%) | Rate: %d/%d RPM | Batched: %d procs%n",
                                elapsed, activeModelName, kbIn, kbOut, dailyRequestsUsed, activeQuotaLimit, remaining, pctLeft, currentRpm, activeRpm, batchItems.size());
                    } else {
                        double currentSpentUsd = totalCostInPennies / 100.0;
                        if (activeSpendingCapUsd > 0.0) {
                            double remainingBudgetUsd = Math.max(0.0, activeSpendingCapUsd - currentSpentUsd);
                            System.out.printf("DONE in %dms [%s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Running Total: %.3f¢ | Prepaid Left: $%.4f | Batched: %d procs)%n",
                                    elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, totalCostInPennies, remainingBudgetUsd, batchItems.size());
                        } else {
                            System.out.printf("DONE in %dms [%s] (%.2f KB in / %.2f KB out | %d tok in / %d tok out | Call: %.4f¢ | Running Total: %.3f¢ | Total Spent: $%.4f | Batched: %d procs)%n",
                                    elapsed, activeModelName, kbIn, kbOut, promptTokens, candidateTokens, callCostPennies, totalCostInPennies, currentSpentUsd, batchItems.size());
                        }
                    }

                    Map<String, Object> logData = new LinkedHashMap<>();
                    logData.put("target", batchHeader);
                    logData.put("model", activeModelName);
                    logData.put("batched", batchItems.size());
                    logData.put("tier", isFreeCall ? "FREE" : "PAID");
                    logData.put("elapsedMs", elapsed);
                    logData.put("promptTokens", promptTokens);
                    logData.put("candidateTokens", candidateTokens);
                    logData.put("callCostPennies", Math.round(callCostPennies * 10000.0) / 10000.0);
                    logData.put("totalCostPennies", Math.round(totalCostInPennies * 1000.0) / 1000.0);
                    emitJsonLog("INFO", "API_CALL_DONE", logData);

                    // Extract array from parsed output
                    ArrayNode arrNode = null;
                    if (parsedArray.isArray()) {
                        arrNode = (ArrayNode) parsedArray;
                    } else if (parsedArray.path("routines").isArray()) {
                        arrNode = (ArrayNode) parsedArray.path("routines");
                    } else if (parsedArray.path("items").isArray()) {
                        arrNode = (ArrayNode) parsedArray.path("items");
                    } else if (parsedArray.path("results").isArray()) {
                        arrNode = (ArrayNode) parsedArray.path("results");
                    }

                    Map<String, JsonNode> byName = new HashMap<>();
                    if (arrNode != null) {
                        for (int k = 0; k < arrNode.size(); k++) {
                            JsonNode itemNode = arrNode.get(k);
                            String rName = itemNode.path("routineName").asText(
                                    itemNode.path("name").asText(itemNode.path("objectName").asText(""))).toUpperCase().trim();
                            if (!rName.isEmpty()) {
                                byName.put(rName, itemNode);
                            }
                        }
                    }

                    List<RoutineFinding> findings = new ArrayList<>();
                    for (int i = 0; i < batchItems.size(); i++) {
                        RoutineBatchItem item = batchItems.get(i);
                        JsonNode matchedNode = byName.get(item.routineName.toUpperCase());
                        if (matchedNode == null && arrNode != null && i < arrNode.size()) {
                            matchedNode = arrNode.get(i);
                        }

                        if (matchedNode != null && !matchedNode.path("what").asText("").isEmpty()) {
                            if (displayJsonBlocks) {
                                printPrettyJsonBlock(mapper, item.lookupKey, matchedNode);
                            }
                            RoutineFinding finding = parseRoutineFindingFromJson(matchedNode, item.lookupKey, item.schema, item.pkg, item.routineName);
                            findings.add(finding);
                        } else {
                            // Single item fallback if missing in batch response
                            System.out.printf("   [Adaptive Recovery] Missing '%s' in batch response -> Retrying individually... ", item.routineName);
                            Map<String, Object> recData = new LinkedHashMap<>();
                            recData.put("missingRoutine", item.routineName);
                            recData.put("action", "RETRY_INDIVIDUALLY");
                            emitJsonLog("WARN", "ADAPTIVE_RECOVERY", recData);
                            RoutineFinding f = callGeminiAiStudioWithCost(client, mapper, apiUrl, currentKey, item.schema, item.pkg, item.routineName, item.routineType, item.fullSql);
                            findings.add(f);
                        }
                    }
                    return findings;
                } else if (response.statusCode == 429) {
                    boolean isDailyExhausted = response.body != null && (
                            response.body.toLowerCase().contains("per day") ||
                                    response.body.contains("PerDay") ||
                                    response.body.contains("requests_per_day") ||
                                    response.body.toLowerCase().contains("daily limit") ||
                                    response.body.toLowerCase().contains("quota limit per day")
                    );
                    if (isDailyExhausted) {
                        recordModel429Exhaustion(activeModelName, mapper);
                        if (isFreeCall) {
                            freeTierExhausted = true;
                        }
                    }
                    if (isFreeCall && activeTierMode == TierMode.HYBRID && !activePaidApiKey.isEmpty() && !activePaidApiKey.equals(currentKey)) {
                        System.out.printf("   [Hybrid Failover] Free Tier Rate Limit 429 -> Seamlessly routing batch '%s' to Paid Tier key...%n", batchHeader);
                        currentKey = activePaidApiKey;
                        isFreeCall = false;
                        attempt = 0;
                        continue;
                    }
                    Map<String, Object> rateData = new LinkedHashMap<>();
                    rateData.put("target", batchHeader);
                    rateData.put("httpStatus", 429);
                    rateData.put("model", activeModelName);
                    rateData.put("attempt", attempt);
                    rateData.put("maxRetries", MAX_RETRIES);
                    rateData.put("dailyExhausted", isDailyExhausted);
                    emitJsonLog("WARN", "RATE_LIMIT_429", rateData);

                    if (isDailyExhausted || attempt == MAX_RETRIES) {
                        boolean failedOver = tryFailoverToNextModel(client, mapper, currentKey);
                        if (failedOver) {
                            attempt = 0;
                            continue;
                        }
                    }
                    long backoffMs = isFreeCall ? (6000L * attempt) : (RETRY_DELAY_MS * attempt);
                    System.out.printf("RATE LIMIT 429 (Pacing %.1fs, Retry %d/%d)... ", (backoffMs / 1000.0), attempt, MAX_RETRIES);
                    Thread.sleep(backoffMs);
                } else if (response.statusCode == 503 || response.statusCode == 500 || response.statusCode == 502 || response.statusCode == 504) {
                    if (isFreeCall && !activePaidApiKey.isEmpty() && !activePaidApiKey.equals(currentKey) && !isSpendingCapReached(activeSpendingCapUsd)) {
                        System.out.printf("   [Transient %d Server Busy] Free Tier Busy -> Seamlessly switching batch '%s' to Paid Tier key...%n", response.statusCode, batchHeader);
                        currentKey = activePaidApiKey;
                        isFreeCall = false;
                        attempt = 0;
                        continue;
                    }
                    long backoffMs = isFreeCall ? (5000L * attempt) : (RETRY_DELAY_MS * attempt);
                    System.out.printf("SERVER BUSY %d (Pacing %.1fs, Retry %d/%d)... ", response.statusCode, (backoffMs / 1000.0), attempt, MAX_RETRIES);
                    Map<String, Object> busyData = new LinkedHashMap<>();
                    busyData.put("target", batchHeader);
                    busyData.put("httpStatus", response.statusCode);
                    busyData.put("model", activeModelName);
                    busyData.put("attempt", attempt);
                    busyData.put("maxRetries", MAX_RETRIES);
                    busyData.put("backoffSeconds", backoffMs / 1000.0);
                    emitJsonLog("WARN", "SERVER_BUSY", busyData);
                    Thread.sleep(backoffMs);
                } else {
                    System.err.printf("%n   [HTTP %d Batch Response]: %s%n", response.statusCode, response.body);
                    Map<String, Object> errData = new LinkedHashMap<>();
                    errData.put("target", batchHeader);
                    errData.put("httpStatus", response.statusCode);
                    errData.put("model", activeModelName);
                    errData.put("responseBody", response.body);
                    emitJsonLog("ERROR", "HTTP_ERROR", errData);
                    return fallbackToIndividualCalls(client, mapper, apiUrl, currentKey, batchItems);
                }
            } catch (Exception ex) {
                if (isFreeCall && !activePaidApiKey.isEmpty() && !activePaidApiKey.equals(currentKey) && !isSpendingCapReached(activeSpendingCapUsd)) {
                    System.out.printf("   [Timeout/Network Failover] Free Endpoint Timeout (%s) -> Seamlessly switching batch '%s' to Paid Tier key...%n", ex.getClass().getSimpleName(), batchHeader);
                    currentKey = activePaidApiKey;
                    isFreeCall = false;
                    attempt = 0;
                    continue;
                }
                if (attempt == MAX_RETRIES) {
                    System.err.printf("BATCH ATTEMPT FAILED [%s]. Falling back to individual requests...%n", ex.getMessage());
                    Map<String, Object> errData = new LinkedHashMap<>();
                    errData.put("target", batchHeader);
                    errData.put("error", ex.getClass().getSimpleName());
                    errData.put("message", ex.getMessage());
                    emitJsonLog("ERROR", "BATCH_FAILED", errData);
                    return fallbackToIndividualCalls(client, mapper, apiUrl, currentKey, batchItems);
                }
            }
        }
        return fallbackToIndividualCalls(client, mapper, apiUrl, currentKey, batchItems);
    }

    private static List<RoutineFinding> fallbackToIndividualCalls(
            HttpClient client, ObjectMapper mapper, String apiUrl, String apiKey, List<RoutineBatchItem> batchItems) {
        System.out.printf("%n   [Adaptive Fallback] Processing %d routines individually...%n", batchItems.size());
        Map<String, Object> fbData = new LinkedHashMap<>();
        fbData.put("batchSize", batchItems.size());
        fbData.put("action", "PROCESSING_INDIVIDUALLY");
        emitJsonLog("INFO", "ADAPTIVE_FALLBACK", fbData);
        List<RoutineFinding> results = new ArrayList<>();
        for (RoutineBatchItem item : batchItems) {
            System.out.printf(" Calling AI API -> %-40s ... ", item.lookupKey);
            RoutineFinding f = callGeminiAiStudioWithCost(client, mapper, apiUrl, apiKey, item.schema, item.pkg, item.routineName, item.routineType, item.fullSql);
            results.add(f);
        }
        return results;
    }

    /**
     * Partitions a list of pending routine items into adaptive batches (1x, 2x, or 4x).
     * Routines with large SQL source are automatically isolated to 1x to safeguard output tokens.
     */
    private static List<List<RoutineBatchItem>> createAdaptiveBatches(List<RoutineBatchItem> items, int maxMultiplier) {
        List<List<RoutineBatchItem>> batches = new ArrayList<>();
        if (items == null || items.isEmpty()) return batches;

        if (maxMultiplier <= 1) {
            for (RoutineBatchItem item : items) {
                batches.add(Collections.singletonList(item));
            }
            return batches;
        }

        List<RoutineBatchItem> currentBatch = new ArrayList<>();
        int currentBatchChars = 0;

        for (RoutineBatchItem item : items) {
            // If item has very large SQL, isolate it into its own 1x batch
            if (item.isLargeSql()) {
                if (!currentBatch.isEmpty()) {
                    batches.add(new ArrayList<>(currentBatch));
                    currentBatch.clear();
                    currentBatchChars = 0;
                }
                batches.add(Collections.singletonList(item));
                continue;
            }

            // Also safeguard cumulative SQL size in a single batch (max 25,000 chars total prompt SQL)
            if (currentBatch.size() >= maxMultiplier || (currentBatchChars + item.fullSql.length()) > 25000) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
                currentBatchChars = 0;
            }

            currentBatch.add(item);
            currentBatchChars += item.fullSql.length();
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    public static class TurboCoordinatorResult {
        public final int totalBatches;
        public final int totalRoutines;
        public final int completedRoutines;
        public final int erroredRoutines;
        public final int freeCalls;
        public final int paidCalls;
        public final double totalCostPennies;
        public final Map<String, String> errorRegistry;

        public TurboCoordinatorResult(int totalBatches, int totalRoutines, int completedRoutines, int erroredRoutines,
                                      int freeCalls, int paidCalls, double totalCostPennies, Map<String, String> errorRegistry) {
            this.totalBatches = totalBatches;
            this.totalRoutines = totalRoutines;
            this.completedRoutines = completedRoutines;
            this.erroredRoutines = erroredRoutines;
            this.freeCalls = freeCalls;
            this.paidCalls = paidCalls;
            this.totalCostPennies = totalCostPennies;
            this.errorRegistry = errorRegistry;
        }
    }

    public static TurboCoordinatorResult runTurboResearchCoordinator(
            HttpClient httpClient, ObjectMapper mapper, String activeApiUrl,
            List<RoutineBatchItem> allPendingItems,
            Map<String, RoutineFinding> researchedCatalog,
            File catalogJsonFile, double spendingCapUsd) {

        if (allPendingItems == null || allPendingItems.isEmpty()) {
            TurboCoordinatorResult emptyRes = new TurboCoordinatorResult(0, 0, 0, 0, 0, 0, 0.0, Collections.emptyMap());
            return emptyRes;
        }

        int multiplier = activeSpeedMode.getBatchMultiplier();
        List<List<RoutineBatchItem>> allBatches = createAdaptiveBatches(allPendingItems, multiplier);
        ConcurrentLinkedQueue<List<RoutineBatchItem>> queue = new ConcurrentLinkedQueue<>(allBatches);

        int totalBatches = allBatches.size();
        int totalRoutines = allPendingItems.size();

        AtomicInteger completedRoutines = new AtomicInteger(0);
        AtomicInteger erroredRoutines = new AtomicInteger(0);
        AtomicInteger freeCallCount = new AtomicInteger(0);
        AtomicInteger paidCallCount = new AtomicInteger(0);
        AtomicBoolean paidSpendingCapReached = new AtomicBoolean(false);
        AtomicBoolean stopSignal = new AtomicBoolean(false);

        ConcurrentHashMap<String, Integer> retryMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> errorRegistry = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Long> inFlightLeases = new ConcurrentHashMap<>();
        Object diskSaveLock = new Object();

        System.out.printf("%n=========================================================================%n");
        System.out.println("LAUNCHING TURBO DUAL-LANE ASYNC COORDINATOR");
        System.out.printf("Pending Workload: %d total routines across %d adaptive batches (Speed: %s)%n",
                totalRoutines, totalBatches, activeSpeedMode.getDescription());
        System.out.println("Lane Allocation:  [LANE 1: Free Worker (15 RPM)] + [LANE 2: Paid Workers (2x Parallel Async)]");
        System.out.printf("=========================================================================%n%n");

        long turboStartMs = System.currentTimeMillis();

        // 1. Worker Lane 1: Free Tier Worker Thread (15 RPM / 4.1s spacing)
        Runnable freeWorker = () -> {
            while (!queue.isEmpty() && !stopSignal.get()) {
                List<RoutineBatchItem> batch = queue.poll();
                if (batch == null) break;

                String batchHeader = batch.size() == 1 ? batch.get(0).lookupKey : String.format("%s (and %d others)", batch.get(0).lookupKey, batch.size() - 1);
                inFlightLeases.put(batchHeader, System.currentTimeMillis());

                try {
                    enforceAdaptiveBurstGuard();
                    long start = System.currentTimeMillis();
                    List<RoutineFinding> findings = callGeminiAiStudioBatchWithCost(httpClient, mapper, activeApiUrl, activeFreeApiKey, batch);
                    long elapsed = System.currentTimeMillis() - start;
                    freeCallCount.incrementAndGet();

                    boolean batchHadErrors = false;
                    for (RoutineFinding f : findings) {
                        if (isPendingOrFailed(f)) {
                            int retries = retryMap.merge(f.lookupKey, 1, Integer::sum);
                            if (retries < 3 && !paidSpendingCapReached.get() && !activePaidApiKey.isEmpty()) {
                                // Re-queue individual item so Paid lane can pick it up
                                for (RoutineBatchItem item : batch) {
                                    if (item.lookupKey.equalsIgnoreCase(f.lookupKey)) {
                                        queue.offer(Collections.singletonList(item));
                                        break;
                                    }
                                }
                                batchHadErrors = true;
                            } else {
                                errorRegistry.put(f.lookupKey, f.description);
                                researchedCatalog.put(f.lookupKey, f);
                                erroredRoutines.incrementAndGet();
                            }
                        } else {
                            researchedCatalog.put(f.lookupKey, f);
                            completedRoutines.incrementAndGet();
                        }
                    }

                    inFlightLeases.remove(batchHeader);

                    if (!batchHadErrors) {
                        int done = completedRoutines.get();
                        double pct = ((double) done / totalRoutines) * 100.0;
                        System.out.printf("[TURBO: LANE-FREE | %s] (%dms) -> %-40s | Progress: %d/%d (%.1f%%) | Spend: $%.4f%n",
                                activeModelName, elapsed, batchHeader, done, totalRoutines, pct, (totalCostInPennies / 100.0));
                    }

                    if (completedRoutines.get() % 5 == 0) {
                        synchronized (diskSaveLock) {
                            saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);
                        }
                    }
                } catch (Exception ex) {
                    inFlightLeases.remove(batchHeader);
                    int retries = retryMap.merge(batchHeader, 1, Integer::sum);
                    if (retries < 3) {
                        queue.offer(batch);
                    } else {
                        for (RoutineBatchItem it : batch) {
                            RoutineFinding fail = new RoutineFinding(it.lookupKey, it.schema, it.pkg, it.routineName, "Pending Technical Review (TURBO Error)", ex.getMessage(), Collections.emptyList(), Collections.emptyList(), "Reads: [None] | Writes: [None]", "Callers: [UNKNOWN] -> Reads: [None]", "N/A", "N/A", "N/A", 1, "Review network connectivity");
                            researchedCatalog.put(it.lookupKey, fail);
                            errorRegistry.put(it.lookupKey, ex.getMessage());
                            erroredRoutines.incrementAndGet();
                        }
                    }
                }
            }
        };

        // 2. Worker Lane 2: Paid Tier Workers (2 parallel threads)
        int paidWorkerCount = (activePaidApiKey != null && !activePaidApiKey.isEmpty()) ? 2 : 0;
        List<Thread> paidThreads = new ArrayList<>();

        for (int p = 1; p <= paidWorkerCount; p++) {
            final int workerId = p;
            Runnable paidWorker = () -> {
                while (!queue.isEmpty() && !stopSignal.get() && !paidSpendingCapReached.get()) {
                    if (isSpendingCapReached(spendingCapUsd)) {
                        paidSpendingCapReached.set(true);
                        System.out.printf("   [TURBO: LANE-PAID-%d] Spending Cap $%.2f Reached -> Paid Worker Shutting Down Safely (Lane 1 will finish remaining)%n", workerId, spendingCapUsd);
                        break;
                    }

                    List<RoutineBatchItem> batch = queue.poll();
                    if (batch == null) break;

                    String batchHeader = batch.size() == 1 ? batch.get(0).lookupKey : String.format("%s (and %d others)", batch.get(0).lookupKey, batch.size() - 1);
                    inFlightLeases.put(batchHeader, System.currentTimeMillis());

                    try {
                        long start = System.currentTimeMillis();
                        List<RoutineFinding> findings = callGeminiAiStudioBatchWithCost(httpClient, mapper, activeApiUrl, activePaidApiKey, batch);
                        long elapsed = System.currentTimeMillis() - start;
                        paidCallCount.incrementAndGet();

                        for (RoutineFinding f : findings) {
                            if (isPendingOrFailed(f)) {
                                int retries = retryMap.merge(f.lookupKey, 1, Integer::sum);
                                if (retries < 3) {
                                    for (RoutineBatchItem item : batch) {
                                        if (item.lookupKey.equalsIgnoreCase(f.lookupKey)) {
                                            queue.offer(Collections.singletonList(item));
                                            break;
                                        }
                                    }
                                } else {
                                    errorRegistry.put(f.lookupKey, f.description);
                                    researchedCatalog.put(f.lookupKey, f);
                                    erroredRoutines.incrementAndGet();
                                }
                            } else {
                                researchedCatalog.put(f.lookupKey, f);
                                completedRoutines.incrementAndGet();
                            }
                        }

                        inFlightLeases.remove(batchHeader);

                        int done = completedRoutines.get();
                        double pct = ((double) done / totalRoutines) * 100.0;
                        System.out.printf("[TURBO: LANE-PAID-%d | %s] (%dms) -> %-40s | Progress: %d/%d (%.1f%%) | Spend: $%.4f / Cap: $%.2f%n",
                                workerId, activeModelName, elapsed, batchHeader, done, totalRoutines, pct, (totalCostInPennies / 100.0), spendingCapUsd);

                        if (completedRoutines.get() % 5 == 0) {
                            synchronized (diskSaveLock) {
                                saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);
                            }
                        }
                    } catch (Exception ex) {
                        inFlightLeases.remove(batchHeader);
                        int retries = retryMap.merge(batchHeader, 1, Integer::sum);
                        if (retries < 3) {
                            queue.offer(batch);
                        } else {
                            for (RoutineBatchItem it : batch) {
                                RoutineFinding fail = new RoutineFinding(it.lookupKey, it.schema, it.pkg, it.routineName, "Pending Technical Review (TURBO Error)", ex.getMessage(), Collections.emptyList(), Collections.emptyList(), "Reads: [None] | Writes: [None]", "Callers: [UNKNOWN] -> Reads: [None]", "N/A", "N/A", "N/A", 1, "Review network connectivity");
                                researchedCatalog.put(it.lookupKey, fail);
                                errorRegistry.put(it.lookupKey, ex.getMessage());
                                erroredRoutines.incrementAndGet();
                            }
                        }
                    }
                }
            };

            Thread t = new Thread(paidWorker, "Turbo-Paid-Worker-" + workerId);
            t.start();
            paidThreads.add(t);
        }

        Thread freeThread = new Thread(freeWorker, "Turbo-Free-Worker");
        freeThread.start();

        try {
            freeThread.join();
            for (Thread pt : paidThreads) {
                pt.join();
            }
        } catch (InterruptedException ie) {
            stopSignal.set(true);
            Thread.currentThread().interrupt();
        }

        synchronized (diskSaveLock) {
            try {
                saveCatalogJson(catalogJsonFile, mapper, researchedCatalog);
            } catch (Exception ex) {
                System.err.printf("Warning: Could not save final catalog JSON: %s%n", ex.getMessage());
            }
        }

        long totalDurationMs = System.currentTimeMillis() - turboStartMs;
        double totalSeconds = totalDurationMs / 1000.0;
        double throughputRpm = totalSeconds > 0 ? ((completedRoutines.get() / totalSeconds) * 60.0) : 0.0;

        System.out.printf("%n=========================================================================%n");
        System.out.printf("TURBO EXECUTION COMPLETED IN %.2f SECONDS (%.1f routines/min)%n", totalSeconds, throughputRpm);
        System.out.printf("Completed:       %d / %d routines (Errors: %d)%n", completedRoutines.get(), totalRoutines, erroredRoutines.get());
        System.out.printf("Calls Breakdown: %,d Free Calls ($0.00) | %,d Paid Calls%n", freeCallCount.get(), paidCallCount.get());
        System.out.printf("Total Spend:     $%.4f USD (Pennies: %.4f¢)%n", (totalCostInPennies / 100.0), totalCostInPennies);
        if (!errorRegistry.isEmpty()) {
            System.out.printf("Warning: %d routines experienced issues and were marked for technical review:%n", errorRegistry.size());
            errorRegistry.forEach((k, v) -> System.out.printf("   - %-35s -> %s%n", k, v));
        }
        System.out.printf("=========================================================================%n%n");

        TurboCoordinatorResult result = new TurboCoordinatorResult(totalBatches, totalRoutines, completedRoutines.get(), erroredRoutines.get(), freeCallCount.get(), paidCallCount.get(), totalCostInPennies, errorRegistry);
        return result;
    }

    private static Map<String, RoutineFinding> loadExistingCatalog(File catalogFile, ObjectMapper mapper) {
        Map<String, RoutineFinding> map = new LinkedHashMap<>();
        if (!catalogFile.exists()) return map;

        try {
            JsonNode root = mapper.readTree(catalogFile);
            JsonNode routines = root.path("ResearchedRoutines");
            if (routines.isArray()) {
                for (JsonNode r : routines) {
                    String key = r.path("LookupKey").asText("");
                    if (!key.isEmpty()) {
                        JsonNode descNode = r.path("Description");
                        String what = descNode.path("What").asText(descNode.path("what").asText(""));
                        String why = descNode.path("Why").asText(descNode.path("why").asText(""));
                        String how = descNode.path("How").asText(descNode.path("how").asText(""));

                        List<String> inParams = new ArrayList<>();
                        List<String> outParams = new ArrayList<>();
                        JsonNode paramsNode = r.path("Parameters");
                        if (paramsNode.isObject()) {
                            JsonNode inArr = paramsNode.path("In");
                            if (!inArr.isArray()) inArr = paramsNode.path("in");
                            if (inArr.isArray()) inArr.forEach(p -> { if (!p.asText("").trim().isEmpty()) inParams.add(p.asText().trim()); });

                            JsonNode outArr = paramsNode.path("Out");
                            if (!outArr.isArray()) outArr = paramsNode.path("out");
                            if (outArr.isArray()) outArr.forEach(p -> { if (!p.asText("").trim().isEmpty()) outParams.add(p.asText().trim()); });
                        } else if (paramsNode.isTextual()) {
                            String rawP = paramsNode.asText("").trim();
                            if (!rawP.isEmpty() && !rawP.equalsIgnoreCase("None") && !rawP.equalsIgnoreCase("N/A")) {
                                inParams.add(rawP);
                            }
                        }

                        String schema = r.path("Schema").asText("");
                        String pkg = r.path("Package").asText("-");
                        String objName = r.path("ObjectName").asText("");
                        String objType = r.path("ObjectType").asText(r.path("Type").asText(
                                objName.toUpperCase().startsWith("FN_") || objName.toUpperCase().startsWith("FUN_") || objName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE"));
                        String rawDocId = r.path("DocId").asText("");
                        String docId = (!rawDocId.isEmpty() && !rawDocId.startsWith("DOC-")) ? rawDocId : buildDocId(schema, pkg, objName, objType);
                        String slug = r.path("Slug").asText(buildSlug(schema, pkg, objName, objType));
                        String rawDocPath = r.path("DocPath").asText("");
                        String docPath = (!rawDocPath.isEmpty() && !rawDocPath.startsWith("docs/") && !rawDocPath.contains("DOC-")) ? rawDocPath : buildDocPath(schema, pkg, objName, objType);
                        String deps = r.path("Dependencies").asText("");
                        String dfd = r.path("DataFlowDiagram").asText(r.path("RelationshipFlow").asText(""));
                        if (dfd.isEmpty()) {
                            dfd = String.format("Callers: [BATCH: IWS Batch Job] -> %s", deps.replace(" | ", " -> "));
                        }
                        String recs = r.path("Recommendations").asText(r.path("Suggestions").asText("None / Code is optimal"));
                        String mermaid = r.path("MermaidDiagram").asText(r.path("mermaidDiagram").asText("")).trim();

                        map.put(key, new RoutineFinding(
                                key,
                                docId,
                                slug,
                                docPath,
                                schema,
                                pkg,
                                objName,
                                what,
                                why,
                                how,
                                inParams,
                                outParams,
                                deps,
                                dfd,
                                r.path("BusinessRules").asText(""),
                                r.path("ErrorHandling").asText(""),
                                r.path("TestingStrategy").asText(""),
                                r.path("ComplexityScore").asInt(1),
                                recs,
                                mermaid,
                                r.path("VerificationSqlScript").asText(r.path("verificationSqlScript").asText(""))
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not parse existing catalog JSON: " + e.getMessage());
        }
        return map;
    }

    private static void saveCatalogJson(File targetFile, ObjectMapper mapper, Map<String, RoutineFinding> catalog) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("GeneratedAt", new Date().toString());
        root.put("TotalRoutines", catalog.size());

        ArrayNode arr = mapper.createArrayNode();
        for (RoutineFinding f : catalog.values()) {
            ObjectNode n = mapper.createObjectNode();
            n.put("LookupKey", f.lookupKey);
            n.put("DocId", f.docId);
            n.put("Slug", f.slug);
            n.put("DocPath", f.docPath);
            n.put("Schema", f.schema);
            n.put("Package", f.packageName);
            n.put("ObjectName", f.objectName);

            // Store What, Why and How inside a clean, structured Description JSON object
            ObjectNode descObj = mapper.createObjectNode();
            descObj.put("What", f.what);
            descObj.put("Why", f.why);
            descObj.put("How", f.how);
            n.set("Description", descObj);

            // Store In and Out inside a clean, structured Parameters JSON object
            ObjectNode paramsObj = mapper.createObjectNode();
            ArrayNode inArr = mapper.createArrayNode();
            if (f.inParameters != null) f.inParameters.forEach(inArr::add);
            ArrayNode outArr = mapper.createArrayNode();
            if (f.outParameters != null) f.outParameters.forEach(outArr::add);
            paramsObj.set("In", inArr);
            paramsObj.set("Out", outArr);
            n.set("Parameters", paramsObj);

            n.put("Dependencies", f.dependencies);
            n.put("DataFlowDiagram", f.dataFlowDiagram);
            n.put("BusinessRules", f.businessRules);
            n.put("ErrorHandling", f.errorHandling);
            n.put("TestingStrategy", f.testingStrategy);
            n.put("ComplexityScore", f.complexityScore);
            n.put("Recommendations", f.recommendations);
            n.put("MermaidDiagram", f.mermaidDiagram);
            n.put("VerificationSqlScript", f.verificationSqlScript);
            arr.add(n);
        }
        root.set("ResearchedRoutines", arr);

        mapper.writeValue(targetFile, root);
    }

    private static void createHeaders(Sheet sheet, CellStyle style) {
        String[] headers = {
                "DB", "Schema", "Package", "Object Name", "Object Type",
                "Description (WHAT & WHY)", "Parameters", "Dependencies",
                "Data Flow & Relationships (Typed Objects)",
                "Business Rules", "Error Handling", "Testing Strategy & Verification",
                "Architecture Modernization & Code Optimization Recommendations",
                "Doc ID", "Slug (Anchor)", "Doc Path (Hyperlink Target)", "SQL Code Excerpt"
        };
        Row row = sheet.createRow(0);
        row.setHeightInPoints(ROW_HEIGHT_POINTS);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private static void writeExcelRow(Sheet sheet, int rowIdx, String db, String schema, String pkg,
                                      String objName, String type, String sql,
                                      Map<String, RoutineFinding> researchedCatalog, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(ROW_HEIGHT_POINTS);

        row.createCell(0).setCellValue(db);
        row.createCell(1).setCellValue(schema);
        row.createCell(2).setCellValue(pkg);
        row.createCell(3).setCellValue(objName);
        row.createCell(4).setCellValue(type);

        String pkgKey = (pkg == null || pkg.isEmpty() || pkg.equals("-")) ? "-" : pkg.toUpperCase();
        String lookupKey = String.format("%s.%s.%s", schema.toUpperCase(), pkgKey, objName.toUpperCase());
        RoutineFinding finding = (researchedCatalog != null) ? researchedCatalog.get(lookupKey) : null;

        Cell descCell = row.createCell(5);
        descCell.setCellValue(finding != null && finding.description != null ? finding.description : "");
        descCell.setCellStyle(style);

        Cell paramCell = row.createCell(6);
        paramCell.setCellValue(finding != null && finding.parameters != null ? finding.parameters : "");
        paramCell.setCellStyle(style);

        Cell depsCell = row.createCell(7);
        depsCell.setCellValue(finding != null && finding.dependencies != null ? finding.dependencies : "");
        depsCell.setCellStyle(style);

        Cell dfdCell = row.createCell(8);
        dfdCell.setCellValue(finding != null && finding.dataFlowDiagram != null ? finding.dataFlowDiagram : "");
        dfdCell.setCellStyle(style);

        Cell rulesCell = row.createCell(9);
        rulesCell.setCellValue(finding != null && finding.businessRules != null ? finding.businessRules : "");
        rulesCell.setCellStyle(style);

        Cell errCell = row.createCell(10);
        errCell.setCellValue(finding != null && finding.errorHandling != null ? finding.errorHandling : "");
        errCell.setCellStyle(style);

        Cell testCell = row.createCell(11);
        testCell.setCellValue(finding != null && finding.testingStrategy != null ? finding.testingStrategy : "");
        testCell.setCellStyle(style);

        Cell recCell = row.createCell(12);
        recCell.setCellValue(finding != null && finding.recommendations != null ? finding.recommendations : "");
        recCell.setCellStyle(style);

        Cell docIdCell = row.createCell(13);
        docIdCell.setCellValue(finding != null && finding.docId != null ? finding.docId : "");
        docIdCell.setCellStyle(style);

        Cell slugCell = row.createCell(14);
        slugCell.setCellValue(finding != null && finding.slug != null ? finding.slug : "");
        slugCell.setCellStyle(style);

        Cell docPathCell = row.createCell(15);
        docPathCell.setCellValue(finding != null && finding.docPath != null ? finding.docPath : "");
        docPathCell.setCellStyle(style);

        Cell sqlCell = row.createCell(16);
        String safeSql = (sql != null && sql.length() > MAX_EXCEL_CELL_CHARS)
                ? sql.substring(0, MAX_EXCEL_CELL_CHARS) + "\n... [Truncated for Excel]"
                : (sql != null ? sql : "");
        sqlCell.setCellValue(safeSql);
        sqlCell.setCellStyle(style);
    }

    private static void writeTableExcelRow(Sheet sheet, int rowIdx, String db, String schema, String pkg,
                                           String objName, String type, String desc, String columnsAndTypes,
                                           String constraints, String sql, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(ROW_HEIGHT_POINTS);

        row.createCell(0).setCellValue(db);
        row.createCell(1).setCellValue(schema);
        row.createCell(2).setCellValue(pkg);
        row.createCell(3).setCellValue(objName);
        row.createCell(4).setCellValue(type);

        Cell descCell = row.createCell(5);
        descCell.setCellValue(desc != null ? desc : "");
        descCell.setCellStyle(style);

        Cell paramCell = row.createCell(6);
        paramCell.setCellValue(columnsAndTypes != null ? columnsAndTypes : "");
        paramCell.setCellStyle(style);

        Cell depsCell = row.createCell(7);
        depsCell.setCellValue(constraints != null ? constraints : "");
        depsCell.setCellStyle(style);

        Cell dfdCell = row.createCell(8);
        dfdCell.setCellValue("Callers: [CRUD: Multi-Tier] -> Reads: [TABLE: " + objName + "] -> Writes: [TABLE: " + objName + "]");
        dfdCell.setCellStyle(style);

        Cell rulesCell = row.createCell(9);
        rulesCell.setCellValue("N/A (Table Object)");
        rulesCell.setCellStyle(style);

        Cell errCell = row.createCell(10);
        errCell.setCellValue("Enforced via Database Constraints");
        errCell.setCellStyle(style);

        Cell testCell = row.createCell(11);
        testCell.setCellValue("Inspect via SELECT COUNT(1) and schema verify");
        testCell.setCellStyle(style);

        Cell recCell = row.createCell(12);
        recCell.setCellValue("N/A (Database Table Schema)");
        recCell.setCellStyle(style);

        Cell docIdCell = row.createCell(13);
        docIdCell.setCellValue("");
        docIdCell.setCellStyle(style);

        Cell slugCell = row.createCell(14);
        slugCell.setCellValue("");
        slugCell.setCellStyle(style);

        Cell docPathCell = row.createCell(15);
        docPathCell.setCellValue("");
        docPathCell.setCellStyle(style);

        Cell sqlCell = row.createCell(16);
        String safeSql = (sql != null && sql.length() > MAX_EXCEL_CELL_CHARS)
                ? sql.substring(0, MAX_EXCEL_CELL_CHARS) + "\n... [Truncated for Excel]"
                : (sql != null ? sql : "");
        sqlCell.setCellValue(safeSql);
        sqlCell.setCellStyle(style);
    }

    private static List<JsonNode> extractSubRoutines(JsonNode pkg) {
        List<JsonNode> result = new ArrayList<>();
        String[] keys = {"Procedures", "procedures", "Children", "children", "Methods", "methods", "Functions", "functions", "Routines", "routines"};
        for (String k : keys) {
            JsonNode n = pkg.path(k);
            if (n.isArray() && !n.isEmpty()) n.forEach(result::add);
        }
        return result;
    }

    private static String extractTableDescription(JsonNode tblNode, String schemaName, String tblName) {
        String[] keys = {"Description", "description", "Remarks", "remarks", "Comment", "comment", "Comments"};
        for (String k : keys) {
            if (tblNode.hasNonNull(k)) return tblNode.get(k).asText().replaceAll("[\\r\\n]+", " ").trim();
        }
        return String.format("Database table definition for %s.%s.", schemaName, tblName);
    }

    private static String extractTableColumns(JsonNode tblNode) {
        JsonNode cols = tblNode.path("Columns");
        if (!cols.isArray() || cols.isEmpty()) cols = tblNode.path("columns");
        if (!cols.isArray() || cols.isEmpty()) cols = tblNode.path("Fields");
        if (!cols.isArray() || cols.isEmpty()) return "Columns: [None]";

        List<String> colDefs = new ArrayList<>();
        for (JsonNode col : cols) {
            String colName = extractText(col, "ColumnName", "columnName", "Name", "name");
            String dataType = extractText(col, "DataType", "dataType", "Type", "type");
            String length = extractText(col, "Length", "length", "DataLength");
            String precision = extractText(col, "Precision", "precision", "DataPrecision");
            String scale = extractText(col, "Scale", "scale", "DataScale");

            StringBuilder typeStr = new StringBuilder(dataType);
            if (!precision.isEmpty() && !precision.equals("null") && !precision.equals("0")) {
                typeStr.append("(").append(precision);
                if (!scale.isEmpty() && !scale.equals("null") && !scale.equals("0")) typeStr.append(",").append(scale);
                typeStr.append(")");
            } else if (!length.isEmpty() && !length.equals("null") && !length.equals("0")) {
                typeStr.append("(").append(length).append(")");
            }

            if (!colName.isEmpty()) colDefs.add(colName + " " + typeStr.toString().trim());
        }
        return colDefs.isEmpty() ? "Columns: [None]" : "[" + String.join(", ", colDefs) + "]";
    }

    private static String extractTableConstraints(JsonNode tblNode) {
        List<String> pks = new ArrayList<>();
        List<String> fks = new ArrayList<>();
        List<String> uks = new ArrayList<>();

        JsonNode pkNode = tblNode.path("PrimaryKey");
        if (pkNode.isMissingNode() || pkNode.isNull()) pkNode = tblNode.path("primaryKey");
        if (!pkNode.isMissingNode() && !pkNode.isNull()) {
            String pkName = pkNode.path("Name").asText(pkNode.asText(""));
            if (!pkName.isEmpty()) pks.add(pkName);
        }

        JsonNode constraints = tblNode.path("Constraints");
        if (!constraints.isArray()) constraints = tblNode.path("constraints");
        if (constraints.isArray()) {
            for (JsonNode c : constraints) {
                String cName = extractText(c, "ConstraintName", "constraintName", "Name", "name");
                String cType = extractText(c, "ConstraintType", "constraintType", "Type", "type").toUpperCase();
                String refTable = extractText(c, "RTableName", "rTableName", "ReferencedTable", "referencedTable");

                if (cType.contains("P") || cType.contains("PRIMARY")) pks.add(cName);
                else if (cType.contains("R") || cType.contains("FOREIGN")) {
                    if (!refTable.isEmpty()) fks.add(cName + " -> " + refTable);
                    else fks.add(cName);
                } else if (cType.contains("U") || cType.contains("UNIQUE")) uks.add(cName);
            }
        }

        JsonNode foreignKeys = tblNode.path("ForeignKeys");
        if (!foreignKeys.isArray()) foreignKeys = tblNode.path("foreignKeys");
        if (foreignKeys.isArray()) {
            for (JsonNode fk : foreignKeys) {
                String fkName = extractText(fk, "Name", "name", "ForeignKeyName");
                String refTable = extractText(fk, "ReferencedTable", "referencedTable", "RTableName");
                if (!refTable.isEmpty()) fks.add(fkName + " -> " + refTable);
                else if (!fkName.isEmpty()) fks.add(fkName);
            }
        }

        JsonNode triggers = tblNode.path("Triggers");
        if (!triggers.isArray()) triggers = tblNode.path("triggers");
        List<String> trgs = new ArrayList<>();
        if (triggers.isArray()) {
            for (JsonNode t : triggers) {
                String tName = extractText(t, "TriggerName", "triggerName", "Name", "name");
                String tEvent = extractText(t, "TriggeringEvent", "triggeringEvent");
                if (!tName.isEmpty()) {
                    trgs.add(tEvent.isEmpty() ? tName : tName + " (" + tEvent + ")");
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("PK: [").append(pks.isEmpty() ? "None" : String.join(", ", pks)).append("] | ");
        sb.append("FK: [").append(fks.isEmpty() ? "None" : String.join(", ", fks)).append("] | ");
        sb.append("UK: [").append(uks.isEmpty() ? "None" : String.join(", ", uks)).append("]");
        if (!trgs.isEmpty()) {
            sb.append(" | Triggers: [").append(String.join(", ", trgs)).append("]");
        }

        return sb.toString();
    }

    private static void createNamedRange(Workbook workbook, String nameText, String formulaReference) {
        Name namedRange = workbook.getName(nameText);
        if (namedRange == null) {
            namedRange = workbook.createName();
            namedRange.setNameName(nameText);
        }
        namedRange.setRefersToFormula(formulaReference);
    }

    private static void populateResearchReportSheetWithNamedRanges(Sheet sheet, List<String> schemaOrder,
                                                                   CellStyle reportHeaderStyle, CellStyle tableHeaderStyle,
                                                                   CellStyle boldStyle, CellStyle percentStyle,
                                                                   CellStyle boldPercentStyle) {
        Collections.sort(schemaOrder);

        String[] routineHeaders = {"Schema Name", "Progress Ratio", "% Researched"};
        Row routineHeaderRow = sheet.createRow(0);
        routineHeaderRow.setHeightInPoints(ROW_HEIGHT_POINTS);
        for (int i = 0; i < routineHeaders.length; i++) {
            Cell cell = routineHeaderRow.createCell(i);
            cell.setCellValue(routineHeaders[i]);
            cell.setCellStyle(reportHeaderStyle);
        }

        int rowIdx = 1;
        for (String schema : schemaOrder) {
            Row r = sheet.createRow(rowIdx++);
            r.setHeightInPoints(ROW_HEIGHT_POINTS);

            String typeNamedRange = schema + "_TYPE";
            String descNamedRange = schema + "_DESC";

            String researchedFormula = String.format(
                    "COUNTIFS(%s, \"<>TABLE\", %s, \"<>PACKAGE SPECIFICATION/BODY\", %s, \"<>\", %s, \"?*\")",
                    typeNamedRange, typeNamedRange, typeNamedRange, descNamedRange);

            String totalFormula = String.format(
                    "COUNTIFS(%s, \"<>TABLE\", %s, \"<>PACKAGE SPECIFICATION/BODY\", %s, \"<>\")",
                    typeNamedRange, typeNamedRange, typeNamedRange);

            r.createCell(0).setCellValue(schema);

            Cell ratioCell = r.createCell(1);
            ratioCell.setCellFormula(String.format("%s & \" / \" & %s", researchedFormula, totalFormula));

            Cell pctCell = r.createCell(2);
            pctCell.setCellFormula(String.format("IF(%s>0, (%s)/(%s), 0)", totalFormula, researchedFormula, totalFormula));
            pctCell.setCellStyle(percentStyle);
        }

        Row routineTotalRow = sheet.createRow(rowIdx++);
        routineTotalRow.setHeightInPoints(ROW_HEIGHT_POINTS);

        StringBuilder allResearchedBuilder = new StringBuilder();
        StringBuilder allTotalBuilder = new StringBuilder();
        for (int i = 0; i < schemaOrder.size(); i++) {
            String schema = schemaOrder.get(i);
            String typeNamedRange = schema + "_TYPE";
            String descNamedRange = schema + "_DESC";
            if (i > 0) {
                allResearchedBuilder.append("+");
                allTotalBuilder.append("+");
            }
            allResearchedBuilder.append(String.format("COUNTIFS(%s, \"<>TABLE\", %s, \"<>PACKAGE SPECIFICATION/BODY\", %s, \"<>\", %s, \"?*\")",
                    typeNamedRange, typeNamedRange, typeNamedRange, descNamedRange));
            allTotalBuilder.append(String.format("COUNTIFS(%s, \"<>TABLE\", %s, \"<>PACKAGE SPECIFICATION/BODY\", %s, \"<>\")",
                    typeNamedRange, typeNamedRange, typeNamedRange));
        }

        String allResearchedFormula = allResearchedBuilder.length() > 0 ? allResearchedBuilder.toString() : "0";
        String allTotalFormula = allTotalBuilder.length() > 0 ? allTotalBuilder.toString() : "0";

        Cell labelCell = routineTotalRow.createCell(0);
        labelCell.setCellValue("TOTAL");
        labelCell.setCellStyle(boldStyle);

        Cell totalRatio = routineTotalRow.createCell(1);
        totalRatio.setCellFormula(String.format("(%s) & \" / \" & (%s)", allResearchedFormula, allTotalFormula));
        totalRatio.setCellStyle(boldStyle);

        Cell totalPct = routineTotalRow.createCell(2);
        totalPct.setCellFormula(String.format("IF((%s)>0, (%s)/(%s), 0)", allTotalFormula, allResearchedFormula, allTotalFormula));
        totalPct.setCellStyle(boldPercentStyle);

        Row blankRow = sheet.createRow(rowIdx++);
        blankRow.setHeightInPoints(ROW_HEIGHT_POINTS);

        String[] tableHeaders = {"Schema Name", "Total Tables"};
        Row tableHeaderRow = sheet.createRow(rowIdx++);
        tableHeaderRow.setHeightInPoints(ROW_HEIGHT_POINTS);
        for (int i = 0; i < tableHeaders.length; i++) {
            Cell cell = tableHeaderRow.createCell(i);
            cell.setCellValue(tableHeaders[i]);
            cell.setCellStyle(tableHeaderStyle);
        }

        int tableFirstDataRow = rowIdx + 1;
        for (String schema : schemaOrder) {
            Row r = sheet.createRow(rowIdx++);
            r.setHeightInPoints(ROW_HEIGHT_POINTS);
            String typeNamedRange = schema + "_TYPE";
            r.createCell(0).setCellValue(schema);
            Cell tableCountCell = r.createCell(1);
            tableCountCell.setCellFormula(String.format("COUNTIF(%s, \"TABLE\")", typeNamedRange));
        }

        Row tableTotalRow = sheet.createRow(rowIdx);
        tableTotalRow.setHeightInPoints(ROW_HEIGHT_POINTS);
        Cell tableTotalLabel = tableTotalRow.createCell(0);
        tableTotalLabel.setCellValue("TOTAL TABLES");
        tableTotalLabel.setCellStyle(boldStyle);
        Cell sumTables = tableTotalRow.createCell(1);
        sumTables.setCellFormula(String.format("SUM(B%d:B%d)", tableFirstDataRow, rowIdx));
        sumTables.setCellStyle(boldStyle);
    }

    private static CellStyle createHeaderStyle(Workbook wb, IndexedColors color) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private static String getObjectName(JsonNode node, String... preferredKeys) {
        for (String key : preferredKeys) {
            if (node.hasNonNull(key)) return node.get(key).asText();
        }
        String[] fallbackKeys = {"Name", "name", "ObjectName", "objectName", "RoutineName", "routineName", "ProcedureName", "procedureName", "FunctionName", "functionName"};
        for (String k : fallbackKeys) {
            if (node.hasNonNull(k)) return node.get(k).asText();
        }
        return node.asText();
    }

    private static String extractText(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.hasNonNull(k)) return node.get(k).asText().trim();
        }
        return "";
    }

    private static String extractSqlExcerpt(JsonNode node) {
        String raw = extractFullSql(node);
        if (!raw.isEmpty() && raw.length() > MAX_SQL_EXCERPT_CHARS) {
            return raw.substring(0, MAX_SQL_EXCERPT_CHARS) + "\n... [Truncated for preview]";
        }
        return raw;
    }

    private static String extractFullSql(JsonNode node) {
        String[] sqlKeys = {"Body", "body", "Definition", "definition", "Sql", "sql", "Text", "text", "Source", "source", "Script", "script", "Ddl", "ddl", "Statement", "statement"};
        for (String k : sqlKeys) {
            if (node.hasNonNull(k)) {
                String raw = node.get(k).asText().trim();
                if (!raw.isEmpty()) return raw;
            }
        }
        return "";
    }

    private static Map<String, String> parseCommandLineArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) return map;
        for (String arg : args) {
            if (arg.startsWith("-") && arg.contains("=")) {
                String clean = arg.replaceFirst("^-+", "");
                int eqIdx = clean.indexOf('=');
                map.put(clean.substring(0, eqIdx).trim(), clean.substring(eqIdx + 1).trim());
            } else if (arg.startsWith("-")) {
                String clean = arg.replaceFirst("^-+", "").trim();
                map.put(clean, "true");
            }
        }
        return map;
    }

    private static Set<String> resolveTargetSchemas(Map<String, String> argMap) {
        if (argMap.containsKey("schemas")) {
            Set<String> set = new HashSet<>();
            for (String s : argMap.get("schemas").split(",")) {
                if (!s.trim().isEmpty()) set.add(s.trim().toUpperCase());
            }
            return set;
        }
        return DEFAULT_TARGET_SCHEMAS;
    }

    public static String buildDocId(String schema, String pkg, String objectName, String objectType) {
        String cleanSchema = (schema == null || schema.trim().isEmpty()) ? "GLOBAL" : schema.trim().toUpperCase();
        String cleanPkg = (pkg == null || pkg.trim().isEmpty() || pkg.equals("-")) ? "" : pkg.trim().toUpperCase();
        String cleanObj = (objectName == null || objectName.trim().isEmpty()) ? "UNKNOWN" : objectName.trim().toUpperCase();

        if ("TABLE".equalsIgnoreCase(objectType)) {
            return String.format("TBL-%s-%s", cleanSchema, cleanObj);
        } else if ("VIEW".equalsIgnoreCase(objectType)) {
            return String.format("VW-%s-%s", cleanSchema, cleanObj);
        } else if ("FUNCTION".equalsIgnoreCase(objectType) || cleanObj.startsWith("FN_") || cleanObj.startsWith("FUN_") || cleanObj.contains("FUNC")) {
            if (!cleanPkg.isEmpty()) {
                return String.format("PKG-%s-%s-%s", cleanSchema, cleanPkg, cleanObj);
            }
            return String.format("FNC-%s-%s", cleanSchema, cleanObj);
        } else if (!cleanPkg.isEmpty()) {
            return String.format("PKG-%s-%s-%s", cleanSchema, cleanPkg, cleanObj);
        } else {
            return String.format("PRC-%s-%s", cleanSchema, cleanObj);
        }
    }

    public static String buildSlug(String schema, String pkg, String objectName, String objectType) {
        String cleanSchema = (schema == null || schema.trim().isEmpty()) ? "global" : schema.trim().toLowerCase();
        String cleanPkg = (pkg == null || pkg.trim().isEmpty() || pkg.equals("-")) ? "" : pkg.trim().toLowerCase();
        String cleanObj = (objectName == null || objectName.trim().isEmpty()) ? "unknown" : objectName.trim().toLowerCase();

        String prefix;
        if ("TABLE".equalsIgnoreCase(objectType)) prefix = "tbl-";
        else if ("VIEW".equalsIgnoreCase(objectType)) prefix = "vw-";
        else if ("FUNCTION".equalsIgnoreCase(objectType) || cleanObj.startsWith("fn_") || cleanObj.startsWith("fun_") || cleanObj.contains("func")) prefix = "fnc-";
        else if (!cleanPkg.isEmpty()) prefix = "pkg-";
        else prefix = "prc-";

        if (!cleanPkg.isEmpty()) {
            return (prefix + cleanSchema + "-" + cleanPkg + "-" + cleanObj).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        } else {
            return (prefix + cleanSchema + "-" + cleanObj).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        }
    }

    public static String buildDocPath(String schema, String pkg, String objectName, String objectType) {
        String cleanSchema = (schema == null || schema.trim().isEmpty()) ? "GLOBAL" : schema.trim().toUpperCase();
        String cleanPkg = (pkg == null || pkg.trim().isEmpty() || pkg.equals("-")) ? "" : pkg.trim().toUpperCase();
        String cleanObj = (objectName == null || objectName.trim().isEmpty()) ? "UNKNOWN" : objectName.trim().toUpperCase();

        if ("TABLE".equalsIgnoreCase(objectType)) {
            return String.format("%s/Tables/TBL-%s-%s.docx", cleanSchema, cleanSchema, cleanObj);
        } else if ("VIEW".equalsIgnoreCase(objectType)) {
            return String.format("%s/Views/VW-%s-%s.docx", cleanSchema, cleanSchema, cleanObj);
        } else if ("FUNCTION".equalsIgnoreCase(objectType) || cleanObj.startsWith("FN_") || cleanObj.startsWith("FUN_") || cleanObj.contains("FUNC")) {
            if (!cleanPkg.isEmpty()) {
                return String.format("%s/Packages/PKG-%s-%s.docx", cleanSchema, cleanSchema, cleanPkg);
            }
            return String.format("%s/Functions/FNC-%s-%s.docx", cleanSchema, cleanSchema, cleanObj);
        } else if (!cleanPkg.isEmpty()) {
            return String.format("%s/Packages/PKG-%s-%s.docx", cleanSchema, cleanSchema, cleanPkg);
        } else {
            return String.format("%s/Procedures/PRC-%s-%s.docx", cleanSchema, cleanSchema, cleanObj);
        }
    }

    public static class RoutineFinding {
        public final String lookupKey;
        public final String docId;
        public final String slug;
        public final String docPath;
        public final String schema;
        public final String packageName;
        public final String objectName;
        public final String description;
        public final String what;
        public final String why;
        public final String how;
        public final List<String> inParameters;
        public final List<String> outParameters;
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

        public RoutineFinding(String lookupKey, String schema, String packageName, String objectName,
                              String what, String why, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations) {
            this(lookupKey, schema, packageName, objectName, what, why, "", inParameters, outParameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, "", "");
        }

        public RoutineFinding(String lookupKey, String schema, String packageName, String objectName,
                              String what, String why, String how, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations) {
            this(lookupKey,
                    buildDocId(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    buildSlug(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    buildDocPath(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    schema, packageName, objectName, what, why, how, inParameters, outParameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, "", "");
        }

        public RoutineFinding(String lookupKey, String schema, String packageName, String objectName,
                              String what, String why, String how, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations, String mermaidDiagram) {
            this(lookupKey,
                    buildDocId(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    buildSlug(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    buildDocPath(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    schema, packageName, objectName, what, why, how, inParameters, outParameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, mermaidDiagram, "");
        }

        public RoutineFinding(String lookupKey, String schema, String packageName, String objectName,
                              String what, String why, String how, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations, String mermaidDiagram,
                              String verificationSqlScript) {
            this(lookupKey,
                    buildDocId(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    buildSlug(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    buildDocPath(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE")),
                    schema, packageName, objectName, what, why, how, inParameters, outParameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, mermaidDiagram, verificationSqlScript);
        }

        public RoutineFinding(String lookupKey, String docId, String slug, String docPath,
                              String schema, String packageName, String objectName,
                              String what, String why, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations) {
            this(lookupKey, docId, slug, docPath, schema, packageName, objectName, what, why, "",
                    inParameters, outParameters, dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, "", "");
        }

        public RoutineFinding(String lookupKey, String docId, String slug, String docPath,
                              String schema, String packageName, String objectName,
                              String what, String why, String how, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations) {
            this(lookupKey, docId, slug, docPath, schema, packageName, objectName, what, why, how, inParameters, outParameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, "", "");
        }

        public RoutineFinding(String lookupKey, String docId, String slug, String docPath,
                              String schema, String packageName, String objectName,
                              String what, String why, String how, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations, String mermaidDiagram) {
            this(lookupKey, docId, slug, docPath, schema, packageName, objectName, what, why, how, inParameters, outParameters,
                    dependencies, dataFlowDiagram, businessRules, errorHandling, testingStrategy, complexityScore, recommendations, mermaidDiagram, "");
        }

        public RoutineFinding(String lookupKey, String docId, String slug, String docPath,
                              String schema, String packageName, String objectName,
                              String what, String why, String how, List<String> inParameters, List<String> outParameters,
                              String dependencies, String dataFlowDiagram, String businessRules, String errorHandling,
                              String testingStrategy, int complexityScore, String recommendations, String mermaidDiagram,
                              String verificationSqlScript) {
            this.lookupKey = lookupKey;
            this.schema = schema;
            this.packageName = packageName;
            this.objectName = objectName;
            this.docId = (docId != null && !docId.isEmpty()) ? docId : buildDocId(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE"));
            this.slug = (slug != null && !slug.isEmpty()) ? slug : buildSlug(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE"));
            this.docPath = (docPath != null && !docPath.isEmpty()) ? docPath : buildDocPath(schema, packageName, objectName, (objectName.toUpperCase().startsWith("FN_") || objectName.toUpperCase().startsWith("FUN_") || objectName.toUpperCase().contains("FUNC") ? "FUNCTION" : "PROCEDURE"));

            this.what = (what != null) ? what : "";
            this.why = (why != null) ? why : "";
            this.how = (how != null) ? how : "";
            if (!this.what.isEmpty() && !this.why.isEmpty() && !this.how.isEmpty()) {
                this.description = String.format("WHAT: %s | WHY: %s | HOW: %s", this.what, this.why, this.how);
            } else if (!this.what.isEmpty() && !this.why.isEmpty()) {
                this.description = String.format("WHAT: %s | WHY: %s", this.what, this.why);
            } else if (!this.what.isEmpty()) {
                this.description = this.what;
            } else {
                this.description = this.why;
            }
            this.inParameters = (inParameters != null) ? inParameters : Collections.emptyList();
            this.outParameters = (outParameters != null) ? outParameters : Collections.emptyList();

            // Format single cell parameters string for Excel
            if (this.inParameters.isEmpty() && this.outParameters.isEmpty()) {
                this.parameters = "None";
            } else {
                StringBuilder sb = new StringBuilder();
                if (!this.inParameters.isEmpty()) {
                    sb.append("IN: [").append(String.join(", ", this.inParameters)).append("]");
                }
                if (!this.outParameters.isEmpty()) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append("OUT: [").append(String.join(", ", this.outParameters)).append("]");
                }
                this.parameters = sb.toString();
            }

            this.dependencies = dependencies;
            this.dataFlowDiagram = (dataFlowDiagram != null && !dataFlowDiagram.trim().isEmpty())
                    ? dataFlowDiagram.trim()
                    : ((dependencies != null && !dependencies.trim().isEmpty())
                    ? "Callers: [BATCH: IWS Batch Job] -> " + dependencies.replace(" | ", " -> ")
                    : "Callers: [BATCH: IWS Batch Job] -> Reads: [None] -> Writes: [None]");
            this.businessRules = businessRules;
            this.errorHandling = errorHandling;
            this.testingStrategy = testingStrategy;
            this.complexityScore = complexityScore;
            this.recommendations = (recommendations != null && !recommendations.trim().isEmpty()) ? recommendations.trim() : "None / Code is optimal";
            this.mermaidDiagram = (mermaidDiagram != null) ? mermaidDiagram : "";
            this.verificationSqlScript = (verificationSqlScript != null) ? verificationSqlScript : "";
        }
    }

    /**
     * Encapsulates a candidate routine for adaptive batch scheduling.
     */
    public static class RoutineBatchItem {
        public final String schema;
        public final String pkg;
        public final String routineName;
        public final String routineType;
        public final String fullSql;
        public final String sqlExcerpt;
        public final String lookupKey;
        public final String dbName;
        public final boolean isPackage;

        public RoutineBatchItem(String schema, String pkg, String routineName, String routineType,
                                String fullSql, String sqlExcerpt, String dbName, boolean isPackage) {
            this.schema = schema;
            this.pkg = (pkg == null || pkg.isEmpty()) ? "-" : pkg;
            this.routineName = routineName;
            this.routineType = routineType;
            this.fullSql = fullSql != null ? fullSql : "";
            this.sqlExcerpt = sqlExcerpt != null ? sqlExcerpt : "";
            this.lookupKey = String.format("%s.%s.%s", schema.toUpperCase(), this.pkg.toUpperCase(), routineName.toUpperCase());
            this.dbName = dbName;
            this.isPackage = isPackage;
        }

        public boolean isLargeSql() {
            return fullSql.length() > MAX_ADAPTIVE_SQL_CHARS;
        }
    }

    private static void printPrettyJsonBlock(ObjectMapper mapper, String lookupKey, JsonNode jsonNode) {
        try {
            String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            System.out.println("┌── [AI RESEARCH FINDINGS: " + lookupKey + "] " + "─".repeat(Math.max(10, 60 - lookupKey.length())) + "┐");
            for (String line : pretty.split("\\R")) {
                System.out.println("│ " + line);
            }
            System.out.println("└──" + "─".repeat(70) + "┘");
        } catch (Exception ex) {
            System.out.println("   [JSON Preview]: " + jsonNode.toString());
        }
    }
}
