package com.fllottery.common.cli;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enterprise CLI Header, Footer, and Metric Diagnostics Helper for Florida Lottery utilities.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class ConsoleBannerHelper {

    private static final String LINE = "=========================================================================";

    private ConsoleBannerHelper() {
        // Utility class
    }

    /**
     * Prints an official Florida Lottery CLI header banner.
     */
    public static void printHeader(String title, String... details) {
        System.out.println(LINE);
        System.out.println(" " + title.toUpperCase());
        System.out.println(" Runtime: Java 21 / Spring Boot 3.4.0 / Florida Lottery DataOps");
        System.out.println(LINE);
        if (details != null) {
            for (String detail : details) {
                if (detail != null && !detail.isBlank()) {
                    System.out.println(" " + detail);
                }
            }
            if (details.length > 0) {
                System.out.println(LINE);
            }
        }
    }

    /**
     * Prints an official Florida Lottery CLI horizontal divider line.
     */
    public static void printDivider() {
        System.out.println(LINE);
    }

    /**
     * Prints execution completion diagnostics including elapsed time and throughput.
     */
    public static void printFooter(long startTimeMs, int itemsProcessed, String itemType) {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        System.out.println(LINE);
        System.out.printf(" EXECUTION COMPLETED IN %d ms (%d %s)%n", elapsed, itemsProcessed, itemType);
        System.out.println(" Completed at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println(LINE + "\n");
    }

    /**
     * Formats bytes into a human-readable string (KB, MB, GB).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            String result = bytes + " B";
            return result;
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        String result = String.format("%.1f %cB", bytes / Math.pow(1024, exp), unit);
        return result;
    }
}
