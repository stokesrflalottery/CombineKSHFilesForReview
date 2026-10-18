package com.fllottery.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Enterprise Proxy Resolver for Florida Lottery ETL & AI Research Framework.
 *
 * Automatically detects and configures network routing through the Florida Lottery
 * Enterprise Proxy across multiple resolution tiers:
 *   1. Explicit CLI arguments (-proxyHost, --proxyHost, -proxyPort, -proxy)
 *   2. JVM System Properties (https.proxyHost, https.proxyPort, http.proxyHost, http.proxyPort)
 *   3. OS Environment Variables (HTTPS_PROXY, HTTP_PROXY, FL_PROXY_HOST, etc.)
 *   4. Enterprise Properties Files (proxy.properties, db_credentials.properties in workspace and C:\ocioldapps1\Cached\DbCache\)
 *   5. Windows WinINet / Group Policy Registry Auto-Detection (HKCU Internet Settings)
 *   6. Enterprise Default Fallback for Florida Lottery / OCI developer workstations
 */
public class EnterpriseProxyResolver {

    public static final String DEFAULT_ENTERPRISE_PROXY_HOST = "proxy.lottery.state.fl.us";
    public static final int DEFAULT_ENTERPRISE_PROXY_PORT = 8080;

    public static class EnterpriseProxyConfig {
        public final boolean enabled;
        public final String host;
        public final int port;
        public final String username;
        public final String password;
        public final String resolutionSource;
        public final Proxy javaProxy;
        public final ProxySelector proxySelector;

        public EnterpriseProxyConfig(boolean enabled, String host, int port, String username, String password, String resolutionSource) {
            this.enabled = enabled;
            this.host = host != null ? host.trim() : "";
            this.port = port > 0 ? port : DEFAULT_ENTERPRISE_PROXY_PORT;
            this.username = username != null ? username.trim() : "";
            this.password = password != null ? password.trim() : "";
            this.resolutionSource = resolutionSource != null ? resolutionSource : "Direct";

            if (this.enabled && !this.host.isEmpty()) {
                this.javaProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(this.host, this.port));
                this.proxySelector = ProxySelector.of(new InetSocketAddress(this.host, this.port));
            } else {
                this.javaProxy = Proxy.NO_PROXY;
                this.proxySelector = ProxySelector.getDefault();
            }
        }

        public Proxy getJavaProxy() {
            Proxy result = this.javaProxy;
            return result;
        }

        public ProxySelector getProxySelector() {
            ProxySelector result = this.proxySelector;
            return result;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public String getHost() {
            String result = this.host;
            return result;
        }

        public int getPort() {
            return this.port;
        }

        public String getResolutionSource() {
            String result = this.resolutionSource;
            return result;
        }

        @Override
        public String toString() {
            if (this.enabled) {
                String result = String.format("Enterprise Proxy [%s:%d] via %s", this.host, this.port, this.resolutionSource);
                return result;
            }
            String result = "Direct Connection (No Enterprise Proxy Configured)";
            return result;
        }
    }

    private static volatile EnterpriseProxyConfig cachedConfig = null;

    /**
     * Resolves the enterprise proxy using CLI args and all discovery tiers.
     */
    public static EnterpriseProxyConfig resolve(Map<String, String> cliArgs) {
        // Tier 1: Explicit Command Line Arguments
        if (cliArgs != null) {
            String cliHost = cliArgs.getOrDefault("proxyHost", cliArgs.getOrDefault("proxy-host", ""));
            String cliPort = cliArgs.getOrDefault("proxyPort", cliArgs.getOrDefault("proxy-port", ""));
            String cliCombined = cliArgs.getOrDefault("proxy", "");

            if (!cliCombined.isEmpty() && cliHost.isEmpty()) {
                String[] parts = parseHostAndPort(cliCombined);
                cliHost = parts[0];
                if (cliPort.isEmpty() && parts.length > 1) {
                    cliPort = parts[1];
                }
            }

            if (!cliHost.trim().isEmpty()) {
                int port = parsePortOrDefault(cliPort, DEFAULT_ENTERPRISE_PROXY_PORT);
                EnterpriseProxyConfig config = new EnterpriseProxyConfig(true, cliHost.trim(), port, "", "", "CLI Argument");
                applyConfiguration(config);
                EnterpriseProxyConfig result = config;
                return result;
            }
        }

        // Tier 2: JVM System Properties
        String sysHost = System.getProperty("https.proxyHost", System.getProperty("http.proxyHost", System.getProperty("proxyHost", "")));
        String sysPort = System.getProperty("https.proxyPort", System.getProperty("http.proxyPort", System.getProperty("proxyPort", "")));
        if (!sysHost.trim().isEmpty()) {
            int port = parsePortOrDefault(sysPort, DEFAULT_ENTERPRISE_PROXY_PORT);
            EnterpriseProxyConfig config = new EnterpriseProxyConfig(true, sysHost.trim(), port, "", "", "JVM System Property");
            applyConfiguration(config);
            EnterpriseProxyConfig result = config;
            return result;
        }

        // Tier 3: OS Environment Variables (HTTPS_PROXY, HTTP_PROXY, FL_PROXY_HOST)
        String envProxy = System.getenv("HTTPS_PROXY");
        if (envProxy == null || envProxy.trim().isEmpty()) envProxy = System.getenv("https_proxy");
        if (envProxy == null || envProxy.trim().isEmpty()) envProxy = System.getenv("HTTP_PROXY");
        if (envProxy == null || envProxy.trim().isEmpty()) envProxy = System.getenv("http_proxy");
        if (envProxy == null || envProxy.trim().isEmpty()) envProxy = System.getenv("FL_PROXY_HOST");
        if (envProxy == null || envProxy.trim().isEmpty()) envProxy = System.getenv("LOTTERY_PROXY");

        if (envProxy != null && !envProxy.trim().isEmpty()) {
            String[] parts = parseHostAndPort(envProxy);
            String envHost = parts[0];
            String envPortStr = parts.length > 1 ? parts[1] : System.getenv("FL_PROXY_PORT");
            int port = parsePortOrDefault(envPortStr, DEFAULT_ENTERPRISE_PROXY_PORT);
            EnterpriseProxyConfig config = new EnterpriseProxyConfig(true, envHost, port, "", "", "Environment Variable");
            applyConfiguration(config);
            EnterpriseProxyConfig result = config;
            return result;
        }

        // Tier 4: Enterprise Properties Files (proxy.properties, db_credentials.properties)
        Properties fileProps = loadEnterpriseProperties();
        if (fileProps != null) {
            String propHost = fileProps.getProperty("proxy.host",
                    fileProps.getProperty("https.proxyHost",
                            fileProps.getProperty("enterprise.proxy.host",
                                    fileProps.getProperty("fl.proxy.host", ""))));
            String propPortStr = fileProps.getProperty("proxy.port",
                    fileProps.getProperty("https.proxyPort",
                            fileProps.getProperty("enterprise.proxy.port",
                                    fileProps.getProperty("fl.proxy.port", ""))));
            String propUser = fileProps.getProperty("proxy.user", fileProps.getProperty("proxy.username", ""));
            String propPass = fileProps.getProperty("proxy.password", fileProps.getProperty("proxy.pass", ""));

            if (!propHost.trim().isEmpty()) {
                int port = parsePortOrDefault(propPortStr, DEFAULT_ENTERPRISE_PROXY_PORT);
                EnterpriseProxyConfig config = new EnterpriseProxyConfig(true, propHost.trim(), port, propUser, propPass, "Configuration File (proxy.properties)");
                applyConfiguration(config);
                EnterpriseProxyConfig result = config;
                return result;
            }
        }

        // Tier 5: Windows Registry Auto-Detection (HKCU Internet Settings)
        if (isWindowsOs()) {
            EnterpriseProxyConfig winConfig = queryWindowsRegistryProxy();
            if (winConfig != null && winConfig.enabled) {
                applyConfiguration(winConfig);
                EnterpriseProxyConfig result = winConfig;
                return result;
            }
        }

        // Fallback: Windows System Default / Direct Route (No explicit proxy configured)
        EnterpriseProxyConfig direct = new EnterpriseProxyConfig(false, "", 0, "", "", "Windows System Default / Direct Route");
        applyConfiguration(direct);
        EnterpriseProxyConfig result = direct;
        return result;
    }

    /**
     * Resolves without CLI args.
     */
    public static EnterpriseProxyConfig resolve() {
        EnterpriseProxyConfig result = resolve(Collections.emptyMap());
        return result;
    }

    /**
     * Gets or lazily resolves the active enterprise proxy configuration.
     */
    public static EnterpriseProxyConfig getActiveConfig() {
        if (cachedConfig == null) {
            cachedConfig = resolve();
        }
        EnterpriseProxyConfig result = cachedConfig;
        return result;
    }

    /**
     * Applies the proxy configuration to JVM system properties and global authenticators.
     */
    public static void applyConfiguration(EnterpriseProxyConfig config) {
        cachedConfig = config;
        if (config != null && config.enabled && !config.host.isEmpty()) {
            System.setProperty("https.proxyHost", config.host);
            System.setProperty("https.proxyPort", String.valueOf(config.port));
            System.setProperty("http.proxyHost", config.host);
            System.setProperty("http.proxyPort", String.valueOf(config.port));
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "true");
            System.setProperty("java.net.preferIPv4Stack", "true");

            if (!config.username.isEmpty()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == RequestorType.PROXY) {
                            return new PasswordAuthentication(config.username, config.password.toCharArray());
                        }
                        return super.getPasswordAuthentication();
                    }
                });
            }
        }
    }

    /**
     * Configures a standard Java 21 HttpClient.Builder with the resolved enterprise proxy.
     */
    public static HttpClient.Builder configureHttpClientBuilder(HttpClient.Builder builder) {
        EnterpriseProxyConfig config = getActiveConfig();
        if (config.enabled && !config.host.isEmpty()) {
            builder.proxy(config.getProxySelector());
        } else {
            builder.proxy(ProxySelector.getDefault());
        }
        builder.version(HttpClient.Version.HTTP_1_1);
        HttpClient.Builder result = builder;
        return result;
    }

    /**
     * Creates a new Java 21 HttpClient configured with the enterprise proxy and standard timeouts.
     */
    public static HttpClient createEnterpriseHttpClient(Duration connectTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(30));
        configureHttpClientBuilder(builder);
        HttpClient result = builder.build();
        return result;
    }

    /**
     * Proactively verifies network connectivity through the enterprise proxy.
     */
    public static boolean testProxyReachability(EnterpriseProxyConfig config, int timeoutMs) {
        if (config == null || !config.enabled) {
            boolean result = true;
            return result;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.host, config.port), timeoutMs);
            boolean result = true;
            return result;
        } catch (Exception ex) {
            boolean result = false;
            return result;
        }
    }

    // =========================================================================
    // INTERNAL DISCOVERY HELPERS
    // =========================================================================

    private static String[] parseHostAndPort(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            String[] result = new String[]{"", ""};
            return result;
        }
        String clean = raw.trim();
        if (clean.startsWith("http://")) clean = clean.substring(7);
        if (clean.startsWith("https://")) clean = clean.substring(8);
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);

        int atIndex = clean.indexOf('@');
        if (atIndex != -1) {
            clean = clean.substring(atIndex + 1);
        }

        int colon = clean.lastIndexOf(':');
        if (colon != -1 && colon < clean.length() - 1) {
            String host = clean.substring(0, colon).trim();
            String port = clean.substring(colon + 1).trim();
            String[] result = new String[]{host, port};
            return result;
        }
        String[] result = new String[]{clean, String.valueOf(DEFAULT_ENTERPRISE_PROXY_PORT)};
        return result;
    }

    private static int parsePortOrDefault(String portStr, int defaultPort) {
        if (portStr == null || portStr.trim().isEmpty()) {
            return defaultPort;
        }
        try {
            int p = Integer.parseInt(portStr.trim());
            return (p > 0 && p <= 65535) ? p : defaultPort;
        } catch (NumberFormatException ignored) {
            return defaultPort;
        }
    }

    private static boolean isWindowsOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean result = os.contains("win");
        return result;
    }

    private static boolean isFloridaLotteryEnvironment() {
        String user = System.getProperty("user.name", "").toLowerCase();
        String home = System.getProperty("user.home", "").toLowerCase();
        String host = System.getenv("COMPUTERNAME");
        if (host == null) host = System.getenv("HOSTNAME");
        if (host == null) host = "";
        host = host.toLowerCase();

        boolean userMatch = user.contains("stokes") || user.contains("lottery") || home.contains("stokes");
        boolean ociDirExists = new File("C:\\ocioldapps1").exists() || new File("C:\\ocioldapps1\\Cached\\DbCache").exists();
        boolean hostMatch = host.contains("fl") || host.contains("lottery") || host.contains("oci");

        boolean result = userMatch || ociDirExists || hostMatch;
        return result;
    }

    private static Properties loadEnterpriseProperties() {
        // 1. Check Classpath / Resource Folder (src/main/resources/proxy.properties)
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("proxy.properties")) {
            if (is != null) {
                Properties resProps = new Properties();
                resProps.load(is);
                if (resProps.containsKey("proxy.host") || resProps.containsKey("https.proxyHost")
                        || resProps.containsKey("enterprise.proxy.host") || resProps.containsKey("fl.proxy.host")) {
                    Properties result = resProps;
                    return result;
                }
            }
        } catch (Exception ignored) {}

        // 2. Check File Locations
        Properties props = new Properties();
        List<File> candidates = new ArrayList<>();
        candidates.add(new File("proxy.properties"));
        candidates.add(new File("src/main/resources/proxy.properties"));
        candidates.add(new File("target/classes/proxy.properties"));
        candidates.add(new File("db_credentials.properties"));
        candidates.add(new File("C:\\ocioldapps1\\Cached\\DbCache\\proxy.properties"));
        candidates.add(new File("C:\\ocioldapps1\\Cached\\DbCache\\db_credentials.properties"));

        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            candidates.add(new File(userHome, ".fl_lottery_proxy.properties"));
            candidates.add(new File(userHome, ".oracle_credentials.properties"));
        }

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile() && candidate.canRead()) {
                try (FileInputStream fis = new FileInputStream(candidate)) {
                    props.load(fis);
                    if (props.containsKey("proxy.host") || props.containsKey("https.proxyHost")
                            || props.containsKey("enterprise.proxy.host") || props.containsKey("fl.proxy.host")) {
                        Properties result = props;
                        return result;
                    }
                } catch (Exception ignored) {}
            }
        }
        Properties result = props.isEmpty() ? null : props;
        return result;
    }

    /**
     * Inspects Windows Registry for corporate group policy WinINet proxy settings.
     */
    private static EnterpriseProxyConfig queryWindowsRegistryProxy() {
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings").start();

            String proxyServer = null;
            boolean proxyEnabled = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.contains("ProxyEnable")) {
                        if (trimmed.endsWith("0x1") || trimmed.endsWith("1")) {
                            proxyEnabled = true;
                        }
                    } else if (trimmed.contains("ProxyServer")) {
                        String[] tokens = trimmed.split("\\s+");
                        if (tokens.length >= 3) {
                            proxyServer = tokens[tokens.length - 1];
                        }
                    }
                }
            }
            process.waitFor();

            if (proxyEnabled && proxyServer != null && !proxyServer.trim().isEmpty()) {
                String server = proxyServer.trim();
                // Check if format is protocol-specific: e.g. "http=proxy:8080;https=proxy:8080"
                if (server.contains("https=")) {
                    for (String part : server.split(";")) {
                        if (part.trim().startsWith("https=")) {
                            server = part.substring(6).trim();
                            break;
                        }
                    }
                } else if (server.contains("http=")) {
                    for (String part : server.split(";")) {
                        if (part.trim().startsWith("http=")) {
                            server = part.substring(5).trim();
                            break;
                        }
                    }
                }
                String[] parts = parseHostAndPort(server);
                int port = parsePortOrDefault(parts.length > 1 ? parts[1] : "", DEFAULT_ENTERPRISE_PROXY_PORT);
                EnterpriseProxyConfig config = new EnterpriseProxyConfig(true, parts[0], port, "", "", "Windows Registry (WinINet / Group Policy)");
                EnterpriseProxyConfig result = config;
                return result;
            }
        } catch (Exception ignored) {}

        EnterpriseProxyConfig result = null;
        return result;
    }
}
