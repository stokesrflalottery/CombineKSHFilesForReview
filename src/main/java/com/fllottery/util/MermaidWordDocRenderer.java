package com.fllottery.util;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Enterprise Utility to render Mermaid diagram specifications into crisp PNG graphics
 * and embed them directly into Apache POI Microsoft Word (.docx) technical reports.
 * <p>
 * Supported Rendering Backends:
 *  1. <b>Playwright Offline Engine (Primary)</b>: Local headless Chromium running completely offline
 *     against local {@code mermaid.min.js}. Produces 2x Retina high-DPI transparent PNGs in ~20ms
 *     with ZERO external WAN calls, zero HTTP 503 errors, and zero Cloudflare rate limits.
 *  2. <b>HTTP API Engine (Secondary / Fallback)</b>: Hits {@code mermaid.ink} or internal OCI Docker container.
 *  3. <b>Java2D Vector Engine (Final Failsafe)</b>: Generates offline graphic cards via {@link DocxDiagramHelper}.
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery Architecture
 * @since 2026-10-08
 */
public class MermaidWordDocRenderer {

    public enum RenderingEngine {
        AUTO,
        PLAYWRIGHT,
        HTTP
    }

    // Default to mermaid.ink service, or swap to internal OCI Docker container if preferred
    public static final String DEFAULT_MERMAID_INK_URL = "https://mermaid.ink/img/";
    public static volatile String customMermaidServerUrl = null;

    // Active engine selection (Defaults to AUTO: prefers offline Playwright, falls back to HTTP)
    private static volatile RenderingEngine activeEngine = initDefaultEngine();

    // Standard single-page Word document bounding limits (in points, 72 pt = 1 inch)
    public static final int DEFAULT_MAX_WIDTH_PT = 525;   // 7.29 inches (fills standard 0.5" - 0.75" document margins)
    public static final int DEFAULT_MAX_HEIGHT_PT = 520;  // 7.22 inches (accommodates 9-column entity cards and multi-entity layouts)

    private static final HttpClient HTTP_CLIENT = EnterpriseProxyResolver.createEnterpriseHttpClient(Duration.ofSeconds(25));

    // Audit Record Class for JSON Failure Tracking
    public static class DiagramAuditRecord {
        public String timestamp;
        public String caption;
        public String targetObject;
        public String status; // "SUCCESS" or "FALLBACK_JAVA2D"
        public String engine; // "PLAYWRIGHT_OFFLINE", "REMOTE_HTTP", "JAVA2D_FALLBACK"
        public String reason;
        public String mermaidDsl;

        public DiagramAuditRecord(String caption, String targetObject, String status, String engine, String reason, String mermaidDsl) {
            this.timestamp = Instant.now().toString();
            this.caption = caption != null ? caption : "N/A";
            this.targetObject = targetObject != null ? targetObject : "N/A";
            this.status = status;
            this.engine = engine != null ? engine : "UNKNOWN";
            this.reason = reason != null ? reason : "";
            this.mermaidDsl = mermaidDsl != null ? mermaidDsl : "";
        }
    }

    // Audit Counters for Diagram Uniformity Tracking
    public static int totalDiagramsAttempted = 0;
    public static int playwrightSuccessCount = 0;
    public static int httpSuccessCount = 0;
    public static int java2dFallbackCount = 0;
    private static final List<DiagramAuditRecord> auditRecords = Collections.synchronizedList(new ArrayList<>());

    private static RenderingEngine initDefaultEngine() {
        String prop = System.getProperty("mermaid.engine");
        if (prop == null || prop.trim().isEmpty()) {
            prop = System.getenv("MERMAID_ENGINE");
        }
        if (prop != null) {
            String clean = prop.trim().toUpperCase();
            if ("PLAYWRIGHT".equals(clean)) {
                RenderingEngine result = RenderingEngine.PLAYWRIGHT;
                return result;
            }
            if ("HTTP".equals(clean)) {
                RenderingEngine result = RenderingEngine.HTTP;
                return result;
            }
        }
        RenderingEngine result = RenderingEngine.AUTO;
        return result;
    }

    public static void setEngine(RenderingEngine engine) {
        activeEngine = (engine != null) ? engine : RenderingEngine.AUTO;
    }

    public static RenderingEngine getActiveEngine() {
        RenderingEngine result = activeEngine;
        return result;
    }

    public static void resetAuditCounters() {
        totalDiagramsAttempted = 0;
        playwrightSuccessCount = 0;
        httpSuccessCount = 0;
        java2dFallbackCount = 0;
        auditRecords.clear();
    }

    public static int getMermaidSuccessCount() {
        int result = playwrightSuccessCount + httpSuccessCount;
        return result;
    }

    public static void saveAuditReportJson(File targetFile) {
        if (targetFile == null) return;
        try {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("auditDate", Instant.now().toString());
            root.put("totalDiagramsProcessed", totalDiagramsAttempted);
            root.put("playwrightOfflineSuccessCount", playwrightSuccessCount);
            root.put("httpServiceSuccessCount", httpSuccessCount);
            root.put("vectorMermaidSuccessCount", playwrightSuccessCount + httpSuccessCount);
            root.put("java2dFallbackCount", java2dFallbackCount);
            int totalSuccess = playwrightSuccessCount + httpSuccessCount;
            double pct = totalDiagramsAttempted > 0 ? (totalSuccess * 100.0) / totalDiagramsAttempted : 100.0;
            root.put("uniformityPercentage", String.format("%.1f%%", pct));
            root.put("status", java2dFallbackCount == 0 ? "PERFECT_UNIFORMITY" : "MIXED_UNIFORMITY");

            List<DiagramAuditRecord> failedItems = new ArrayList<>();
            synchronized (auditRecords) {
                for (DiagramAuditRecord r : auditRecords) {
                    if ("FALLBACK_JAVA2D".equalsIgnoreCase(r.status)) {
                        failedItems.add(r);
                    }
                }
            }

            root.put("failedDiagramsCount", failedItems.size());
            root.put("failedItems", failedItems);
            root.put("allRecords", new ArrayList<>(auditRecords));

            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            mapper.writeValue(targetFile, root);
            System.out.println("\n=========================================================================");
            System.out.println("[DIAGRAM AUDIT JSON] Saved structured diagram rendering audit log to:");
            System.out.println("   " + targetFile.getAbsolutePath());
            System.out.println("=========================================================================\n");
        } catch (Exception ex) {
            System.err.println("Warning: Failed to save diagram audit JSON: " + ex.getMessage());
        }
    }

    public static void logDiagramAuditSummary() {
        System.out.println("\n=========================================================================");
        System.out.println("[DIAGRAM UNIFORMITY & LINEAGE AUDIT REPORT]");
        System.out.println(String.format(" Total Diagrams Processed:      %,d", totalDiagramsAttempted));
        if (totalDiagramsAttempted > 0) {
            double playwrightPct = (playwrightSuccessCount * 100.0) / totalDiagramsAttempted;
            double httpPct = (httpSuccessCount * 100.0) / totalDiagramsAttempted;
            double fallbackPct = (java2dFallbackCount * 100.0) / totalDiagramsAttempted;
            System.out.println(String.format(" Playwright Offline (Zero WAN): %,d (%.1f%%) [Local Headless Chromium Engine]", playwrightSuccessCount, playwrightPct));
            System.out.println(String.format(" Remote / HTTP Mermaid Service: %,d (%.1f%%) [Network Endpoint Engine]", httpSuccessCount, httpPct));
            System.out.println(String.format(" Java2D Fallback Diagrams:      %,d (%.1f%%) [Offline High-DPI Graphic Cards]", java2dFallbackCount, fallbackPct));
            if (java2dFallbackCount == 0) {
                System.out.println(" DIAGRAM STYLING STATUS:        PERFECT UNIFORMITY (100% Vector Mermaid)");
            } else {
                System.out.println(" DIAGRAM STYLING STATUS:        MIXED UNIFORMITY (" + java2dFallbackCount + " diagrams fell back to Java2D due to syntax/network)");
            }
        } else {
            System.out.println(" DIAGRAM STYLING STATUS:        NO DIAGRAMS GENERATED");
        }
        System.out.println("=========================================================================\n");
    }

    /**
     * Resolves the local mermaid.min.js file from standard project locations, system properties, or environment variables.
     */
    public static File findLocalMermaidJs() {
        // 1. Explicit System Property
        String sysProp = System.getProperty("mermaid.js.path");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            File f = new File(sysProp.trim());
            if (f.exists() && f.isFile()) {
                File result = f;
                return result;
            }
        }

        // 2. Environment Variable
        String envVal = System.getenv("MERMAID_JS_PATH");
        if (envVal != null && !envVal.trim().isEmpty()) {
            File f = new File(envVal.trim());
            if (f.exists() && f.isFile()) {
                File result = f;
                return result;
            }
        }

        // 3. Standard Local Candidate Paths
        String[] candidatePaths = {
            "src/main/resources/mermaid/mermaid.min.js",
            "src/main/resources/mermaid.min.js",
            "src/resources/mermaid.min.js",
            "resources/mermaid/mermaid.min.js",
            "target/mermaid.min.js",
            "target/classes/mermaid/mermaid.min.js",
            "target/classes/mermaid.min.js",
            "mermaid.min.js",
            "../mermaid.min.js",
            "C:\\ocioldapps1\\mermaid.min.js",
            "C:\\ocioldapps1\\Cached\\mermaid.min.js",
            "C:\\ocioldapps1\\Cached\\DbCache\\mermaid.min.js"
        };
        for (String p : candidatePaths) {
            File f = new File(p);
            if (f.exists() && f.isFile() && f.length() > 1000) {
                File result = f;
                return result;
            }
        }

        File result = null;
        return result;
    }

    /**
     * Checks if the offline Playwright rendering engine is initialized and operational.
     */
    public static boolean isPlaywrightAvailable() {
        try {
            PlaywrightWorker.ensureInitialized();
            boolean result = PlaywrightWorker.available;
            return result;
        } catch (Throwable t) {
            boolean result = false;
            return result;
        }
    }

    public static String getPlaywrightUnavailableReason() {
        String result = PlaywrightWorker.unavailableReason;
        return result;
    }

    /**
     * Renders a Mermaid diagram completely offline using Microsoft Playwright and headless Chromium.
     *
     * @param mermaidSource Raw or themed Mermaid DSL
     * @return 2x Retina PNG image bytes
     */
    public static byte[] renderViaPlaywright(String mermaidSource) throws Exception {
        if (mermaidSource == null || mermaidSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Mermaid diagram source cannot be empty.");
        }
        String cleanDsl = mermaidSource.trim();
        if (cleanDsl.startsWith("```")) {
            cleanDsl = cleanDsl.replaceAll("^```[a-zA-Z]*\\R?", "").replaceAll("\\R?```$", "").trim();
        }
        final String targetDsl = cleanDsl;

        Future<byte[]> future = PlaywrightWorker.EXECUTOR.submit(() -> {
            byte[] bytes = PlaywrightWorker.renderOnWorker(targetDsl);
            return bytes;
        });

        byte[] result = future.get(35, TimeUnit.SECONDS);
        return result;
    }

    /**
     * Dedicated single-threaded worker for Playwright.
     * Guarantees 100% thread safety and isolates browser process lifecycle.
     */
    private static class PlaywrightWorker {
        private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FL-Lottery-Playwright-Worker");
            t.setDaemon(true);
            return t;
        });

        private static volatile boolean initialized = false;
        private static volatile boolean available = false;
        private static volatile String unavailableReason = null;
        private static Playwright playwright = null;
        private static Browser browser = null;
        private static BrowserContext context = null;
        private static Page page = null;
        private static String cachedMermaidJs = null;

        static {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    EXECUTOR.submit(PlaywrightWorker::closeQuietly).get(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                } finally {
                    EXECUTOR.shutdown();
                }
            }, "FL-Lottery-Playwright-ShutdownHook"));
        }

        private static void closeQuietly() {
            try {
                if (page != null) { page.close(); page = null; }
                if (context != null) { context.close(); context = null; }
                if (browser != null) { browser.close(); browser = null; }
                if (playwright != null) { playwright.close(); playwright = null; }
            } catch (Throwable ignored) {}
        }

        private static synchronized void ensureInitialized() {
            if (initialized) {
                return;
            }
            initialized = true;

            try {
                Class.forName("com.microsoft.playwright.Playwright");
            } catch (Throwable t) {
                available = false;
                unavailableReason = "Playwright library (com.microsoft.playwright:playwright) is not on classpath: " + t.getMessage();
                return;
            }

            try {
                // 1. Locate and load local mermaid.min.js
                File jsFile = findLocalMermaidJs();
                if (jsFile != null && jsFile.exists()) {
                    cachedMermaidJs = Files.readString(jsFile.toPath(), StandardCharsets.UTF_8);
                } else {
                    // Try classpath resources
                    try (InputStream is = MermaidWordDocRenderer.class.getResourceAsStream("/mermaid/mermaid.min.js")) {
                        if (is != null) {
                            cachedMermaidJs = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        } else {
                            try (InputStream is2 = MermaidWordDocRenderer.class.getResourceAsStream("/mermaid.min.js")) {
                                if (is2 != null) {
                                    cachedMermaidJs = new String(is2.readAllBytes(), StandardCharsets.UTF_8);
                                }
                            }
                        }
                    }
                }

                if (cachedMermaidJs == null || cachedMermaidJs.trim().isEmpty()) {
                    available = false;
                    unavailableReason = "Local mermaid.min.js file not found. Place it in 'src/main/resources/mermaid/mermaid.min.js' or set -Dmermaid.js.path";
                    return;
                }

                // 2. Launch Headless Chromium via Playwright
                playwright = Playwright.create();
                BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(true);
                String channel = System.getProperty("mermaid.browser.channel");
                if (channel != null && !channel.trim().isEmpty()) {
                    launchOptions.setChannel(channel.trim());
                }

                browser = playwright.chromium().launch(launchOptions);
                context = browser.newContext(new Browser.NewContextOptions()
                        .setDeviceScaleFactor(4.0)
                        .setViewportSize(3840, 2160));
                page = context.newPage();

                // 3. Inject HTML Shell
                String htmlShell = buildHtmlShell(cachedMermaidJs);
                page.setContent(htmlShell);

                available = true;
                unavailableReason = null;
                System.out.println("[PLAYWRIGHT ENGINE] Initialized headless Chromium offline Mermaid renderer (4x Ultra-Retina DPI).");

            } catch (Throwable t) {
                available = false;
                unavailableReason = "Failed to launch Playwright Chromium: " + t.getMessage();
                System.err.println("[PLAYWRIGHT ENGINE WARNING] " + unavailableReason);
                closeQuietly();
            }
        }

        private static String buildHtmlShell(String mermaidJsCode) {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<script>\n");
            sb.append(mermaidJsCode);
            sb.append("\n</script>\n<script>\n");
            sb.append("window.mermaid.initialize({\n");
            sb.append("  startOnLoad: false,\n");
            sb.append("  theme: 'base',\n");
            sb.append("  securityLevel: 'loose',\n");
            sb.append("  fontFamily: 'Segoe UI, Arial, sans-serif'\n");
            sb.append("});\n");
            sb.append("</script>\n");
            sb.append("<style>\n");
            sb.append("  th, .node foreignObject table th, .node .label table th, table tr th {\n");
            sb.append("    color: #FFFFFF !important;\n");
            sb.append("  }\n");
            sb.append("  th font, th b, th span {\n");
            sb.append("    color: #FFFFFF !important;\n");
            sb.append("  }\n");
            sb.append("</style>\n");
            sb.append("</head>\n");
            sb.append("<body style=\"background: transparent; margin: 0; padding: 12px; display: inline-block;\">\n");
            sb.append("  <div id=\"diagram-container\"></div>\n");
            sb.append("</body>\n</html>");
            String result = sb.toString();
            return result;
        }

        private static byte[] renderOnWorker(String mermaidDsl) throws Exception {
            ensureInitialized();
            if (!available) {
                throw new IllegalStateException("Playwright engine unavailable: " + unavailableReason);
            }

            String script =
                "async (dsl) => {\n" +
                "  const container = document.getElementById('diagram-container');\n" +
                "  container.innerHTML = '';\n" +
                "  const strayOld = document.querySelectorAll('[id^=\"dmmd_\"]');\n" +
                "  strayOld.forEach(el => el.remove());\n" +
                "  try {\n" +
                "    const id = 'mmd_' + Math.floor(Math.random() * 10000000);\n" +
                "    const res = await window.mermaid.render(id, dsl);\n" +
                "    container.innerHTML = res.svg;\n" +
                "    const svgEl = container.querySelector('svg');\n" +
                "    if (!svgEl) return { success: false, error: 'Mermaid produced empty SVG container' };\n" +
                "    return { success: true, error: null };\n" +
                "  } catch (err) {\n" +
                "    container.innerHTML = '';\n" +
                "    return { success: false, error: err.message || String(err) };\n" +
                "  }\n" +
                "}";

            @SuppressWarnings("unchecked")
            Map<String, Object> evalResult = (Map<String, Object>) page.evaluate(script, mermaidDsl);
            if (evalResult == null) {
                throw new IOException("Playwright returned null result for Mermaid rendering");
            }

            Boolean success = (Boolean) evalResult.get("success");
            if (!Boolean.TRUE.equals(success)) {
                String errorMsg = (String) evalResult.get("error");
                throw new IOException(errorMsg != null ? errorMsg : "Mermaid render failed in browser");
            }

            Locator svgLocator = page.locator("#diagram-container svg");
            byte[] screenshotBytes = svgLocator.first().screenshot(
                    new Locator.ScreenshotOptions().setOmitBackground(true)
            );
            byte[] result = screenshotBytes;
            return result;
        }
    }

    /**
     * Converts a Mermaid diagram definition into PNG bytes.
     * Automatically attempts offline Playwright headless Chromium first, falling back to HTTP API if configured.
     *
     * @param mermaidSource Raw Mermaid diagram string
     * @return Binary PNG image data
     */
    public static byte[] convertMermaidToImageBytes(String mermaidSource) throws IOException, InterruptedException {
        if (mermaidSource == null || mermaidSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Mermaid diagram source definition cannot be empty.");
        }

        // 1. Try Playwright Offline Engine first if AUTO or PLAYWRIGHT
        if (activeEngine == RenderingEngine.AUTO || activeEngine == RenderingEngine.PLAYWRIGHT) {
            try {
                byte[] playwrightBytes = renderViaPlaywright(mermaidSource);
                if (playwrightBytes != null && playwrightBytes.length > 0) {
                    playwrightSuccessCount++;
                    byte[] result = playwrightBytes;
                    return result;
                }
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                // If it is a syntax parse error, do not hit external HTTP service as DSL is invalid
                if (msg.contains("Parse error") || msg.contains("Syntax error") || msg.contains("Lexical error")) {
                    throw new IOException(msg, ex);
                }

                if (activeEngine == RenderingEngine.PLAYWRIGHT) {
                    throw new IOException("Playwright offline render failed: " + msg, ex);
                }

                // In AUTO mode, log transient failure and fall back to HTTP API
                System.out.println("   [PLAYWRIGHT FALLBACK] Playwright offline render failed (" + msg + ") -> Attempting HTTP API...");
            }
        }

        // 2. HTTP API Engine (mermaid.ink or internal Docker container)
        byte[] httpBytes = convertMermaidToImageBytesViaHttp(mermaidSource);
        if (httpBytes != null && httpBytes.length > 0) {
            httpSuccessCount++;
            byte[] result = httpBytes;
            return result;
        }

        byte[] result = new byte[0];
        return result;
    }

    /**
     * Converts a Mermaid diagram definition into PNG bytes via HTTP API with pako deflate compression.
     */
    public static byte[] convertMermaidToImageBytesViaHttp(String mermaidSource) throws IOException, InterruptedException {
        if (mermaidSource == null || mermaidSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Mermaid diagram source definition cannot be empty.");
        }

        String sourceToRender = mermaidSource.trim();
        if (sourceToRender.startsWith("```")) {
            sourceToRender = sourceToRender.replaceAll("^```[a-zA-Z]*\\R?", "").replaceAll("\\R?```$", "").trim();
        }

        String encodedPayload;
        try {
            // Package as JSON with pako deflate compression for maximal capacity and URL safety
            String jsonPayload = "{\"code\":" + escapeJson(sourceToRender) + ",\"mermaid\":{\"theme\":\"base\"}}";
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);

            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            deflater.setInput(input);
            deflater.finish();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            deflater.end();

            String pakoBase64 = Base64.getUrlEncoder().encodeToString(baos.toByteArray());
            encodedPayload = "pako:" + pakoBase64;
        } catch (Exception ex) {
            // Direct Base64 fallback
            byte[] sourceBytes = mermaidSource.trim().getBytes(StandardCharsets.UTF_8);
            String base64 = Base64.getUrlEncoder().encodeToString(sourceBytes);
            encodedPayload = base64;
        }

        String baseUrl = (customMermaidServerUrl != null && !customMermaidServerUrl.trim().isEmpty())
                ? customMermaidServerUrl.trim()
                : DEFAULT_MERMAID_INK_URL;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        // Try high-resolution (1600px width for 600 DPI) first, with fallback to standard resolution
        String[] queryOptions = new String[]{"?type=png&width=1600", "?type=png"};
        HttpResponse<byte[]> lastResponse = null;

        for (String query : queryOptions) {
            String endpointUrl = baseUrl + encodedPayload + query;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) FloridaLottery-DocGen/2.0")
                    .header("Accept", "image/png,image/*")
                    .GET()
                    .build();

            int maxAttempts = 3;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    lastResponse = response;
                    if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
                        byte[] result = response.body();
                        return result;
                    }
                    if (response.statusCode() == 503 || response.statusCode() == 502 || response.statusCode() == 504 || response.statusCode() == 429) {
                        if (attempt < maxAttempts) {
                            long backoffMs = (long) (1500L * attempt + Math.random() * 500);
                            Thread.sleep(backoffMs);
                            continue;
                        }
                    }
                    break;
                } catch (IOException ioe) {
                    if (attempt == maxAttempts) {
                        break;
                    }
                    Thread.sleep(1200L * attempt);
                }
            }
        }

        if (lastResponse != null && lastResponse.statusCode() != 200) {
            String bodySnippet = new String(lastResponse.body(), StandardCharsets.UTF_8);
            if (bodySnippet.length() > 200) {
                bodySnippet = bodySnippet.substring(0, 200);
            }
            String errorMsg = "Mermaid service returned HTTP " + lastResponse.statusCode() + ": " + bodySnippet;
            throw new IOException(errorMsg);
        }

        byte[] result = (lastResponse != null) ? lastResponse.body() : new byte[0];
        return result;
    }

    /**
     * Converts a Mermaid diagram and embeds it directly into an Apache POI Word document.
     *
     * @param doc Target XWPFDocument
     * @param mermaidSource Mermaid flowchart, sequence, or ERD definition
     * @param maxDisplayWidthPt Maximum logical width in points
     * @param caption Optional figure caption text
     * @return true if rendering succeeded, false if an error occurred
     */
    public static boolean insertMermaidDiagram(XWPFDocument doc, String mermaidSource, int maxDisplayWidthPt, String caption) {
        boolean result = insertMermaidDiagram(doc, mermaidSource, maxDisplayWidthPt, DEFAULT_MAX_HEIGHT_PT, caption);
        return result;
    }

    /**
     * Converts a Mermaid diagram and embeds it directly into an Apache POI Word document with dual-axis bounding.
     * Ensures the image fits comfortably on a standard 8.5x11 page without running off the bottom or overlapping footers.
     *
     * @param doc Target XWPFDocument
     * @param mermaidSource Mermaid flowchart, sequence, or ERD definition
     * @param maxDisplayWidthPt Maximum logical width in points (default 520)
     * @param maxDisplayHeightPt Maximum logical height in points (default 460)
     * @param caption Optional figure caption text
     * @return true if rendering succeeded, false if an error occurred
     */
    public static boolean insertMermaidDiagram(XWPFDocument doc, String mermaidSource, int maxDisplayWidthPt, int maxDisplayHeightPt, String caption) {
        try {
            // 1. Fetch image bytes from Mermaid engine (Playwright or HTTP)
            int prePlaywright = playwrightSuccessCount;
            byte[] imageBytes = convertMermaidToImageBytes(mermaidSource);
            boolean usedPlaywright = (playwrightSuccessCount > prePlaywright);
            String engineUsed = usedPlaywright ? "PLAYWRIGHT_OFFLINE" : "REMOTE_HTTP";

            // 2. Dynamically detect picture type by inspecting magic header bytes
            int pictureType = XWPFDocument.PICTURE_TYPE_PNG;
            String filename = "MermaidDiagram.png";

            if (imageBytes.length > 2 && (imageBytes[0] & 0xFF) == 0xFF && (imageBytes[1] & 0xFF) == 0xD8) {
                // JPEG magic bytes (FF D8)
                pictureType = XWPFDocument.PICTURE_TYPE_JPEG;
                filename = "MermaidDiagram.jpg";
            }

            // 3. Inspect native dimensions to preserve aspect ratio
            int nativeW = 800;
            int nativeH = 400;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
                BufferedImage bImg = ImageIO.read(bais);
                if (bImg != null) {
                    nativeW = bImg.getWidth();
                    nativeH = bImg.getHeight();
                }
            }

            // Dual-Axis Proportional Fit: constrain both width and height to fit on single letter page.
            // Allow diagrams to fill up to maxW (default 525 pt) across the page so text and column badges remain comfortably legible.
            int maxW = (maxDisplayWidthPt > 0) ? maxDisplayWidthPt : DEFAULT_MAX_WIDTH_PT;
            int maxH = (maxDisplayHeightPt > 0) ? maxDisplayHeightPt : DEFAULT_MAX_HEIGHT_PT;

            double scaleW = (double) maxW / (double) nativeW;
            double scaleH = (double) maxH / (double) nativeH;
            double scale = Math.min(scaleW, scaleH);

            // Avoid extreme over-stretching for single-entity or narrow diagrams:
            // Playwright uses deviceScaleFactor = 4.0. Baseline 1:1 CSS pixel to point ratio is 1.0 / 4.0 = 0.25.
            // Clamping scale so it does not exceed 1.15 / 4.0 (= 0.2875) prevents a single isolated table
            // from stretching huge across the full margin-to-margin width (525 pt), keeping its physical
            // table entity card size (~250-290 pt) uniform and consistent with multi-table diagrams!
            double maxNaturalScale = 1.15 / 4.0; // ~0.2875 max scale
            if (scale > maxNaturalScale) {
                scale = maxNaturalScale;
            }

            int displayW = (int) Math.round(nativeW * scale);
            int displayH = (int) Math.round(nativeH * scale);

            // 4. Inject Picture into Word Document
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            p.setSpacingBefore(16);
            p.setSpacingAfter(8);

            // Bind picture paragraph to caption so they are never broken across page boundaries
            try {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctpImg = p.getCTP();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr pPrImg = ctpImg.getPPr() != null ? ctpImg.getPPr() : ctpImg.addNewPPr();
                pPrImg.addNewKeepNext();
            } catch (Exception ignored) {}

            XWPFRun r = p.createRun();
            try (ByteArrayInputStream picStream = new ByteArrayInputStream(imageBytes)) {
                r.addPicture(
                        picStream,
                        pictureType,
                        filename,
                        Units.toEMU(displayW),
                        Units.toEMU(displayH)
                );
            }

            // 5. Add clean italicized caption
            if (caption != null && !caption.trim().isEmpty()) {
                XWPFParagraph cp = doc.createParagraph();
                cp.setAlignment(ParagraphAlignment.CENTER);
                cp.setSpacingBefore(6);
                cp.setSpacingAfter(28);

                XWPFRun cRun = cp.createRun();
                cRun.setText("▲ " + caption.trim());
                cRun.setFontFamily("Segoe UI");
                cRun.setFontSize(8.5);
                cRun.setItalic(true);
                cRun.setColor("666666");
            }

            String targetObj = "N/A";
            if (caption != null && (caption.contains("—") || caption.contains("-"))) {
                int dashIdx = caption.contains("—") ? caption.indexOf("—") : caption.indexOf("-");
                targetObj = caption.substring(dashIdx + 1).trim();
            }

            totalDiagramsAttempted++;
            auditRecords.add(new DiagramAuditRecord(caption, targetObj, "SUCCESS", engineUsed, "Vector Mermaid Rendered Successfully", mermaidSource));
            boolean result = true;
            return result;

        } catch (Throwable t) {
            totalDiagramsAttempted++;
            java2dFallbackCount++;
            String targetObj = "N/A";
            if (caption != null && (caption.contains("—") || caption.contains("-"))) {
                int dashIdx = caption.contains("—") ? caption.indexOf("—") : caption.indexOf("-");
                targetObj = caption.substring(dashIdx + 1).trim();
            }
            String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            auditRecords.add(new DiagramAuditRecord(caption, targetObj, "FALLBACK_JAVA2D", "JAVA2D_FALLBACK", errorMsg, mermaidSource));
            System.out.println("   [DIAGRAM AUDIT WARNING] Mermaid render failed for '" + (caption != null ? caption : "Diagram") + "' -> Applied Java2D fallback. Reason: " + errorMsg);
            boolean result = false;
            return result;
        }
    }

    /**
     * Escapes a string to be safely embedded as a JSON string literal (surrounded by quotes).
     */
    public static String escapeJson(String raw) {
        if (raw == null) {
            String result = "\"\"";
            return result;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        String hex = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(hex.substring(hex.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        String result = sb.toString();
        return result;
    }
}
