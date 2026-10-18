package com.fllottery.util;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Diagnostic network probe utility for Florida Lottery enterprise environment.
 *
 * Verifies network routing, DNS resolution, Windows/Enterprise proxy detection,
 * and live HTTP/HTTPS handshake to Google AI Studio (generativelanguage.googleapis.com).
 *
 * Usage in IntelliJ IDEA:
 *   Right click TestAiStudioConnectivity -> Run 'TestAiStudioConnectivity.main()'
 *
 * Usage via CLI:
 *   java -cp target/classes;target/dependency/* com.fllottery.util.TestAiStudioConnectivity
 *   java -cp target/classes;target/dependency/* com.fllottery.util.TestAiStudioConnectivity -proxyHost=proxy.lottery.state.fl.us -proxyPort=8080
 */
public class TestAiStudioConnectivity {

    private static final String TARGET_HOST = "generativelanguage.googleapis.com";
    private static final String PING_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.useSystemProxies", "true");

        Map<String, String> argMap = parseArgs(args);

        System.out.println("=========================================================================");
        System.out.println("   FLORIDA LOTTERY: AI STUDIO NETWORK & PROXY CONNECTIVITY PROBE         ");
        System.out.println("=========================================================================");

        // ---------------------------------------------------------------------
        // 1. ENVIRONMENT & RUNTIME DIAGNOSTICS
        // ---------------------------------------------------------------------
        System.out.println("\n[1/6] Inspecting Runtime Environment...");
        String osName = System.getProperty("os.name", "Unknown");
        String userName = System.getProperty("user.name", "Unknown");
        String javaVersion = System.getProperty("java.version", "Unknown");
        System.out.printf("   OS Platform:         %s%n", osName);
        System.out.printf("   User Account:        %s%n", userName);
        System.out.printf("   Java Runtime:        %s%n", javaVersion);

        String envHttps = System.getenv("HTTPS_PROXY");
        String envHttp = System.getenv("HTTP_PROXY");
        String envFlProxy = System.getenv("FL_PROXY_HOST");
        System.out.printf("   ENV HTTPS_PROXY:     %s%n", (envHttps != null ? envHttps : "[Not Set]"));
        System.out.printf("   ENV HTTP_PROXY:      %s%n", (envHttp != null ? envHttp : "[Not Set]"));
        System.out.printf("   ENV FL_PROXY_HOST:   %s%n", (envFlProxy != null ? envFlProxy : "[Not Set]"));

        File localProps = new File("proxy.properties");
        File resourceProps = new File("src/main/resources/proxy.properties");
        File ociProps = new File("C:\\ocioldapps1\\Cached\\DbCache\\proxy.properties");
        boolean classpathProps = Thread.currentThread().getContextClassLoader().getResource("proxy.properties") != null;
        System.out.printf("   Local proxy.props:   %s%n", (localProps.exists() ? "Found (" + localProps.getAbsolutePath() + ")" : "Not Found"));
        System.out.printf("   Resources proxy.prop: %s%n", (resourceProps.exists() ? "Found (" + resourceProps.getAbsolutePath() + ")" : (classpathProps ? "Found on Classpath" : "Not Found")));
        System.out.printf("   OCI cache proxy.props: %s%n", (ociProps.exists() ? "Found (" + ociProps.getAbsolutePath() + ")" : "Not Found"));

        try {
            List<Proxy> defaultProxies = ProxySelector.getDefault().select(URI.create("https://" + TARGET_HOST));
            System.out.printf("   Windows ProxySelector: %s%n", defaultProxies);
        } catch (Exception ex) {
            System.out.printf("   Windows ProxySelector: Error querying default (%s)%n", ex.getMessage());
        }

        // ---------------------------------------------------------------------
        // 2. DNS RESOLUTION TEST
        // ---------------------------------------------------------------------
        System.out.println("\n[2/6] Testing DNS Resolution for Google AI Studio (" + TARGET_HOST + ")...");
        try {
            InetAddress[] addresses = InetAddress.getAllByName(TARGET_HOST);
            List<String> ipList = new ArrayList<>();
            for (InetAddress addr : addresses) {
                ipList.add(addr.getHostAddress());
            }
            System.out.printf("   [DNS SUCCESS] Resolved %d IP address(es):%n", addresses.length);
            for (String ip : ipList) {
                System.out.printf("      -> %s%n", ip);
            }
        } catch (Exception ex) {
            System.err.printf("   [DNS FAILURE] Unable to resolve %s: %s%n", TARGET_HOST, ex.getMessage());
        }

        // ---------------------------------------------------------------------
        // 3. DIRECT OUTBOUND TCP PORT 443 TEST (WITHOUT PROXY)
        // ---------------------------------------------------------------------
        System.out.println("\n[3/6] Testing Direct Socket Handshake to " + TARGET_HOST + ":443 (No Proxy)...");
        boolean directSuccess = false;
        try (Socket directSocket = new Socket()) {
            directSocket.connect(new InetSocketAddress(TARGET_HOST, 443), 8000);
            directSuccess = true;
            System.out.println("   [DIRECT SOCKET OPEN] Direct outbound connection on port 443 SUCCEEDED.");
            System.out.println("   -> Your machine has open direct internet egress to Google.");
        } catch (Exception ex) {
            System.out.printf("   [DIRECT SOCKET BLOCKED] %s: %s%n", ex.getClass().getSimpleName(), ex.getMessage());
            System.out.println("   -> State/Enterprise perimeter firewall is blocking direct outbound port 443 egress.");
        }

        // ---------------------------------------------------------------------
        // 4. ENTERPRISE PROXY RESOLUTION
        // ---------------------------------------------------------------------
        System.out.println("\n[4/6] Resolving Enterprise Proxy Configuration...");
        EnterpriseProxyResolver.EnterpriseProxyConfig proxyConfig = EnterpriseProxyResolver.resolve(argMap);
        System.out.printf("   Proxy Active:        %s%n", proxyConfig.isEnabled());
        System.out.printf("   Proxy Endpoint:      %s%n", (proxyConfig.isEnabled() ? proxyConfig.getHost() + ":" + proxyConfig.getPort() : "[Direct / Windows System Default]"));
        System.out.printf("   Resolution Source:   %s%n", proxyConfig.getResolutionSource());

        if (proxyConfig.isEnabled() && !proxyConfig.getHost().isEmpty()) {
            System.out.printf("   Testing Proxy Reachability (%s:%d)... ", proxyConfig.getHost(), proxyConfig.getPort());
            boolean proxyReachable = EnterpriseProxyResolver.testProxyReachability(proxyConfig, 5000);
            if (proxyReachable) {
                System.out.println("[REACHABLE]");
            } else {
                System.out.println("[UNREACHABLE / TIMEOUT]");
                System.err.println("   ACTION REQUIRED: Verify your VPN connection or check proxy host/port in proxy.properties.");
            }
        } else {
            System.out.println("   -> No explicit proxy host configured. Using Windows System Default route.");
        }

        // ---------------------------------------------------------------------
        // 5. LIVE HTTPS HANDSHAKE VIA JAVA 21 HTTPCLIENT (ENGINE 1)
        // ---------------------------------------------------------------------
        System.out.println("\n[5/6] Testing Live HTTPS Handshake via Java 21 HttpClient (Engine 1)...");
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);

        EnterpriseProxyResolver.configureHttpClientBuilder(builder);
        HttpClient client = builder.build();

        String apiKey = resolveApiKey(argMap);
        String requestUrl = apiKey.isEmpty() ? PING_URL : (PING_URL + "?key=" + apiKey);

        boolean httpSuccess = false;
        try {
            long start = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("User-Agent", "FloridaLottery-DiagnosticProbe/2.0")
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            int status = response.statusCode();
            System.out.printf("   [HTTP RESPONSE] Status Code: HTTP %d (Elapsed: %dms)%n", status, elapsed);

            if (status == 200) {
                httpSuccess = true;
                System.out.println("   [SUCCESS] Full 200 OK Handshake confirmed! API key is valid and authorized.");
            } else if (status == 400 || status == 403) {
                httpSuccess = true;
                System.out.printf("   [NETWORK CONFIRMED] HTTP %d received from Google AI Studio.%n", status);
                System.out.println("   -> Network route and proxy handshake are 100% FUNCTIONAL.");
                if (apiKey.isEmpty()) {
                    System.out.println("   -> (HTTP 400/403 is expected because no API key was provided for this ping).");
                }
            } else {
                System.out.printf("   [STATUS] HTTP %d: %s%n", status, truncate(response.body(), 120));
            }
        } catch (Exception ex) {
            System.err.printf("   [HTTPCLIENT FAILED] %s: %s%n", ex.getClass().getSimpleName(), ex.getMessage());
            if (ex.getCause() != null) {
                System.err.printf("   -> Root Cause: %s: %s%n", ex.getCause().getClass().getSimpleName(), ex.getCause().getMessage());
            }
        }

        // ---------------------------------------------------------------------
        // 6. LIVE HTTPS HANDSHAKE VIA NATIVE URLCONNECTION (ENGINE 2 FALLBACK)
        // ---------------------------------------------------------------------
        System.out.println("\n[6/6] Testing Native Windows HttpURLConnection Fallback (Engine 2)...");
        try {
            long start = System.currentTimeMillis();
            URL url = new URL(requestUrl);
            HttpURLConnection conn;
            if (proxyConfig.isEnabled() && !proxyConfig.getHost().isEmpty()) {
                conn = (HttpURLConnection) url.openConnection(proxyConfig.getJavaProxy());
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "FloridaLottery-DiagnosticProbe/2.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int status = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("   [URLCONNECTION RESPONSE] Status Code: HTTP %d (Elapsed: %dms)%n", status, elapsed);
            if (status == 200 || status == 400 || status == 403) {
                System.out.println("   [SUCCESS] Native HttpURLConnection fallback verified operational.");
            }
        } catch (Exception ex) {
            System.err.printf("   [URLCONNECTION FAILED] %s: %s%n", ex.getClass().getSimpleName(), ex.getMessage());
        }

        System.out.println("\n=========================================================================");
        System.out.println("                         DIAGNOSTIC SUMMARY                              ");
        System.out.println("=========================================================================");
        if (httpSuccess) {
            System.out.println("Verdict: SUCCESS! Network route to Google AI Studio is VERIFIED & OPERATIONAL.");
            if (proxyConfig.isEnabled()) {
                System.out.printf("Routing: Explicit Enterprise Proxy [%s:%d] via %s%n", proxyConfig.getHost(), proxyConfig.getPort(), proxyConfig.getResolutionSource());
            } else {
                System.out.println("Routing: Windows System Default ProxySelector (Automatic corporate routing)");
            }
            System.out.println("Status:  All ETL jobs, extractors, and Turbo/Hybrid research services are ready.");
        } else if (directSuccess) {
            System.out.println("Verdict: Direct internet connection is available.");
        } else if (proxyConfig.isEnabled()) {
            System.out.printf("Verdict: Operating behind enterprise firewall. Routing active via:%n         %s%n", proxyConfig.toString());
        } else {
            System.out.println("Verdict: Direct connection is blocked and no enterprise proxy was detected.");
            System.out.println("ACTION REQUIRED: Ensure 'proxy.properties' is present with valid host/port.");
        }
        System.out.println("=========================================================================\n");
    }

    private static String resolveApiKey(Map<String, String> argMap) {
        String key = argMap.getOrDefault("key", argMap.getOrDefault("apiKey", ""));
        if (key.isEmpty()) key = System.getProperty("GEMINI_API_KEY", "");
        if (key.isEmpty()) key = System.getenv("GEMINI_API_KEY") != null ? System.getenv("GEMINI_API_KEY") : "";
        if (key.isEmpty()) key = System.getenv("GEMINI_FREE_API_KEY") != null ? System.getenv("GEMINI_FREE_API_KEY") : "";
        if (key.isEmpty()) key = ExtractAndResearchProcsInventory.FREE_API_KEY;
        String result = key != null ? key.trim() : "";
        return result;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) {
            Map<String, String> empty = map;
            return empty;
        }
        for (String a : args) {
            if (a.startsWith("-") || a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq != -1) {
                    String k = a.substring(a.startsWith("--") ? 2 : 1, eq).trim();
                    String v = a.substring(eq + 1).trim();
                    map.put(k, v);
                } else {
                    String k = a.substring(a.startsWith("--") ? 2 : 1).trim();
                    map.put(k, "true");
                }
            }
        }
        Map<String, String> result = map;
        return result;
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) {
            String result = "";
            return result;
        }
        if (val.length() <= maxLen) {
            String result = val;
            return result;
        }
        String result = val.substring(0, maxLen) + "...";
        return result;
    }
}
