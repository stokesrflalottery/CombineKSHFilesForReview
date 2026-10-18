package com.fllottery.common.util;

/**
 * Enterprise String and Identifier Sanitization Utilities for Florida Lottery.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class FlStringUtil {

    private FlStringUtil() {
        // Utility class
    }

    /**
     * Safely truncates a string with an ellipsis if it exceeds maxLength.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            String result = "";
            return result;
        }
        if (text.length() <= maxLength) {
            String result = text;
            return result;
        }
        int cutIndex = Math.max(0, maxLength - 3);
        String result = text.substring(0, cutIndex) + "...";
        return result;
    }

    /**
     * Extracts the base filename from a Unix or Windows absolute path.
     */
    public static String shortenPath(String fullPath) {
        if (fullPath == null || fullPath.isBlank()) {
            String result = "N/A";
            return result;
        }
        int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < fullPath.length() - 1) {
            String result = fullPath.substring(lastSlash + 1);
            return result;
        }
        String result = fullPath;
        return result;
    }

    /**
     * Trims and removes surrounding double or single quotation marks.
     */
    public static String stripQuotes(String text) {
        if (text == null) {
            String result = "";
            return result;
        }
        String clean = text.trim();
        if ((clean.startsWith("\"") && clean.endsWith("\"")) ||
            (clean.startsWith("'") && clean.endsWith("'"))) {
            if (clean.length() >= 2) {
                String result = clean.substring(1, clean.length() - 1);
                return result;
            }
        }
        String result = clean;
        return result;
    }

    /**
     * Capitalizes the first letter of a string.
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            String result = str;
            return result;
        }
        String result = str.substring(0, 1).toUpperCase() + str.substring(1);
        return result;
    }
}
