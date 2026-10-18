package com.fllottery.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fllottery.common.json.JsonMapperProvider;
import com.fllottery.common.util.EnterpriseProxyResolver;
import com.fllottery.common.util.FlLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Enterprise Google AI Studio Gateway managing REST transport, enterprise proxies,
 * shared rate-limiting, and structured JSON deserialization across all Florida Lottery domains.
 *
 * <p>Key Architecture Capabilities:
 * <ul>
 *   <li>Single source of truth for Google AI Studio API endpoints &amp; default model ({@code gemini-3.8-flash})</li>
 *   <li>Coordinated rate-limit pacing (15 RPM safe spacing clock) shared across procedures and scripts</li>
 *   <li>Full Enterprise Proxy routing via {@link EnterpriseProxyResolver}</li>
 *   <li>Type-safe generic JSON deserialization via {@link #generateStructuredJson(String, Class)}</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public class GoogleAiStudioGateway {

    private static final FlLogger log = FlLogger.getLogger(GoogleAiStudioGateway.class);
    private static final GoogleAiStudioGateway INSTANCE = new GoogleAiStudioGateway();

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String apiKey;

    // Shared pacing clock across all research domains (15 RPM safe pacing = 4100ms)
    private static final Object PACING_LOCK = new Object();
    private static long lastCallTimestampMs = 0;
    private static final long MIN_SPACING_MS = 4100;

    private GoogleAiStudioGateway() {
        this.mapper = JsonMapperProvider.get();
        this.apiKey = resolveApiKey();

        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL);

        try {
            EnterpriseProxyResolver.configureHttpClientBuilder(builder);
        } catch (Throwable t) {
            log.warn("EnterpriseProxyResolver not configured: {}. Using default client routing.", t.getMessage());
        }

        this.httpClient = builder.build();
    }

    public static GoogleAiStudioGateway getInstance() {
        GoogleAiStudioGateway result = INSTANCE;
        return result;
    }

    public boolean isApiKeyConfigured() {
        boolean result = !this.apiKey.isEmpty();
        return result;
    }

    /**
     * Generic structured JSON invocation. Automatically enforces responseMimeType="application/json",
     * coordinated rate-limiting, and Jackson object mapping.
     */
    public <T> T generateStructuredJson(String prompt, Class<T> targetClass) throws Exception {
        if (this.apiKey.isEmpty()) {
            throw new IllegalStateException("Google AI Studio API Key is not configured in environment or system properties.");
        }

        enforceSharedPacing();

        String endpoint = API_BASE_URL + DEFAULT_MODEL + ":generateContent?key=" + this.apiKey;

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> contentMap = new LinkedHashMap<>();
        Map<String, Object> partMap = new LinkedHashMap<>();
        partMap.put("text", prompt);
        contentMap.put("parts", Collections.singletonList(partMap));
        payload.put("contents", Collections.singletonList(contentMap));

        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("temperature", 0.2);
        payload.put("generationConfig", genConfig);

        String jsonBody = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("User-Agent", "FloridaLottery-DataOpsAI/2.0")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("AI Studio returned HTTP status: " + response.statusCode() + " body: " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No candidates returned from AI Studio");
        }

        String rawJson = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");
        T result = mapper.readValue(rawJson, targetClass);
        return result;
    }

    private void enforceSharedPacing() {
        synchronized (PACING_LOCK) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastCallTimestampMs;
            if (elapsed < MIN_SPACING_MS) {
                long sleepTime = MIN_SPACING_MS - elapsed;
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ignored) {}
            }
            lastCallTimestampMs = System.currentTimeMillis();
        }
    }

    private static String resolveApiKey() {
        String key = System.getProperty("GEMINI_API_KEY", "");
        if (key.isEmpty()) {
            key = System.getenv("GEMINI_API_KEY") != null ? System.getenv("GEMINI_API_KEY") : "";
        }
        if (key.isEmpty()) {
            key = System.getenv("GEMINI_FREE_API_KEY") != null ? System.getenv("GEMINI_FREE_API_KEY") : "";
        }
        String result = key != null ? key.trim() : "";
        return result;
    }
}
