package com.fllottery.common.cli;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise Command-Line Argument and Switch Parser for Florida Lottery CLI utilities.
 *
 * <p>Supports:
 * <ul>
 *   <li>Key-value parameters with single or double dashes: {@code -input=file.json}, {@code --mode=master}</li>
 *   <li>Boolean presence flags: {@code -verbose}, {@code --all}, {@code -force}</li>
 *   <li>Type-safe getters with fallback defaults (String, boolean, int, long)</li>
 *   <li>Case-insensitive key lookup</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class CliArgsParser {

    private final Map<String, String> argsMap;

    private CliArgsParser(Map<String, String> parsed) {
        this.argsMap = parsed != null ? parsed : Collections.emptyMap();
    }

    /**
     * Parses standard string array arguments passed into {@code public static void main(String[] args)}.
     */
    public static CliArgsParser parse(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args != null) {
            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                String clean = arg.trim();
                if (clean.startsWith("-") || clean.startsWith("--")) {
                    int eqIndex = clean.indexOf('=');
                    if (eqIndex != -1) {
                        String key = clean.substring(clean.startsWith("--") ? 2 : 1, eqIndex).trim();
                        String val = clean.substring(eqIndex + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                        map.put(key.toLowerCase(), val);
                    } else {
                        String key = clean.substring(clean.startsWith("--") ? 2 : 1).trim();
                        map.put(key.toLowerCase(), "true");
                    }
                }
            }
        }

        CliArgsParser result = new CliArgsParser(Collections.unmodifiableMap(map));
        return result;
    }

    public boolean has(String key) {
        boolean result = key != null && this.argsMap.containsKey(key.toLowerCase());
        return result;
    }

    public String get(String key, String defaultValue) {
        if (key == null) {
            String result = defaultValue;
            return result;
        }
        String val = this.argsMap.get(key.toLowerCase());
        String result = (val != null && !val.isBlank()) ? val : defaultValue;
        return result;
    }

    public String getRequired(String key) {
        String val = get(key, null);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("ACTION REQUIRED: Missing mandatory command-line switch: -" + key);
        }
        String result = val;
        return result;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key, null);
        if (val == null) {
            boolean result = defaultValue;
            return result;
        }
        boolean result = "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val) || "y".equalsIgnoreCase(val);
        return result;
    }

    public int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val == null) {
            int result = defaultValue;
            return result;
        }
        try {
            int result = Integer.parseInt(val);
            return result;
        } catch (NumberFormatException ex) {
            int result = defaultValue;
            return result;
        }
    }

    public long getLong(String key, long defaultValue) {
        String val = get(key, null);
        if (val == null) {
            long result = defaultValue;
            return result;
        }
        try {
            long result = Long.parseLong(val);
            return result;
        } catch (NumberFormatException ex) {
            long result = defaultValue;
            return result;
        }
    }

    public Map<String, String> asMap() {
        Map<String, String> result = this.argsMap;
        return result;
    }
}
