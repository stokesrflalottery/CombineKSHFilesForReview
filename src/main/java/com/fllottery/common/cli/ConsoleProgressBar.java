package com.fllottery.common.cli;

/**
 * Enterprise ANSI & Unicode Interactive Console Progress Bar for IntelliJ IDEA & CLI Terminals.
 * <p>
 * Provides dual-level progress tracking:
 *  1. Schema Progress Bar: Tracks the current schema's completion percentage (x/y) and active document name.
 *  2. Overall Progress Bar: Tracks global batch progress (X/Total), global percentage, throughput, and ETA.
 *  3. Schema Completion Checkpoint: Permanently logs completed schema metrics upon finish.
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) &amp; Florida Lottery DataOps Architecture
 * @version 1.0.0
 */
public class ConsoleProgressBar {

    private final String taskName;
    private final int totalOverall;
    private final int totalSchemas;
    private final int barWidth;
    private final long overallStartTime;

    private int currentOverall = 0;
    private long lastRenderTime = 0;

    // Current Schema Tracking
    private String currentSchemaName = "";
    private int currentSchemaIndex = 0;
    private int currentSchemaTotal = 0;
    private int currentSchemaProgress = 0;
    private long currentSchemaStartTime = 0;

    // ANSI Escape Codes for IntelliJ & CLI Terminals
    private static final String ANSI_RESET   = "\u001B[0m";
    private static final String ANSI_NAVY    = "\u001B[34m";
    private static final String ANSI_GREEN   = "\u001B[32m";
    private static final String ANSI_GOLD    = "\u001B[33m";
    private static final String ANSI_CYAN    = "\u001B[36m";
    private static final String ANSI_MUTED   = "\u001B[90m";
    private static final String ANSI_BOLD    = "\u001B[1m";
    private static final String ANSI_MAGENTA = "\u001B[35m";

    public ConsoleProgressBar(String taskName, int totalOverall) {
        this(taskName, totalOverall, 1, 14);
    }

    public ConsoleProgressBar(String taskName, int totalOverall, int totalSchemas) {
        this(taskName, totalOverall, totalSchemas, 12);
    }

    public ConsoleProgressBar(String taskName, int totalOverall, int totalSchemas, int barWidth) {
        this.taskName = taskName != null ? taskName : "Progress";
        this.totalOverall = Math.max(1, totalOverall);
        this.totalSchemas = Math.max(1, totalSchemas);
        this.barWidth = Math.max(8, Math.min(20, barWidth));
        this.overallStartTime = System.currentTimeMillis();
    }

    /**
     * Initializes a new schema section.
     *
     * @param schemaName Name of the schema (e.g. "ARS", "BIF", "CLAIMS")
     * @param schemaIndex 1-based index of the schema (e.g. 1 of 16)
     * @param schemaTotal Number of documents planned for this schema
     */
    public synchronized void startSchema(String schemaName, int schemaIndex, int schemaTotal) {
        startSchema(schemaName, schemaIndex, 0, schemaTotal);
    }

    /**
     * Initializes a new schema section with an initial progress count.
     *
     * @param schemaName Name of the schema (e.g. "ARS", "BIF", "CLAIMS")
     * @param schemaIndex 1-based index of the schema (e.g. 1 of 16)
     * @param startProgress Starting document count (e.g. 0, or resumed count)
     * @param schemaTotal Number of documents planned for this schema
     */
    public synchronized void startSchema(String schemaName, int schemaIndex, int startProgress, int schemaTotal) {
        this.currentSchemaName = schemaName;
        this.currentSchemaIndex = schemaIndex;
        this.currentSchemaTotal = Math.max(1, schemaTotal);
        this.currentSchemaProgress = Math.max(0, startProgress);
        this.currentSchemaStartTime = System.currentTimeMillis();

        // Print clean schema start banner
        System.out.printf("%s[%d/%d] Schema: %s%s %s(%d/%d planned document%s)%s%n",
                ANSI_BOLD + ANSI_NAVY, schemaIndex, totalSchemas, schemaName, ANSI_RESET,
                ANSI_MUTED, this.currentSchemaProgress, schemaTotal, (schemaTotal == 1 ? "" : "s"), ANSI_RESET);
        System.out.flush();
    }

    /**
     * Increments progress for both the active schema and the overall project.
     *
     * @param currentItem Name of the active routine/table/document being generated
     */
    public synchronized void step(String currentItem) {
        currentOverall++;
        currentSchemaProgress++;
        render(currentItem, false);
    }

    /**
     * Completes the current schema and commits a clean milestone summary line.
     */
    public synchronized void completeSchema(int standaloneCount, int packageCount, int tableCount) {
        long schemaElapsed = System.currentTimeMillis() - currentSchemaStartTime;
        double schemaSec = schemaElapsed / 1000.0;
        int totalForSchema = standaloneCount + packageCount + tableCount;

        // Clear active progress line and write permanent completion line
        System.out.print("\u001B[2K\r");
        System.out.printf("  %s✔ Schema [%d/%d] %s complete:%s %d doc%s in %.1fs %s(Procedures/Funcs: %d, Packages: %d, Tables: %d)%s%n",
                ANSI_GREEN + ANSI_BOLD, currentSchemaIndex, totalSchemas, currentSchemaName, ANSI_RESET,
                totalForSchema, (totalForSchema == 1 ? "" : "s"), schemaSec,
                ANSI_MUTED, standaloneCount, packageCount, tableCount, ANSI_RESET);
        System.out.flush();
    }

    private void render(String currentItem, boolean force) {
        long now = System.currentTimeMillis();
        // Throttle rendering to at most once every 40ms to avoid console flicker during rapid loops
        if (!force && (now - lastRenderTime < 40) && currentOverall < totalOverall) {
            return;
        }
        lastRenderTime = now;

        // 1. Schema Progress Bar
        double schemaPct = Math.min(1.0, (double) currentSchemaProgress / currentSchemaTotal);
        String schemaBar = buildBar(schemaPct, 10);

        // 2. Overall Progress Bar
        double overallPct = Math.min(1.0, (double) currentOverall / totalOverall);
        String overallBar = buildBar(overallPct, 12);

        // Overall Metrics
        long elapsedMs = Math.max(1, now - overallStartTime);
        double itemsPerSec = (currentOverall * 1000.0) / elapsedMs;
        long remainingMs = (itemsPerSec > 0.001) ? (long) ((totalOverall - currentOverall) / (itemsPerSec / 1000.0)) : 0;
        long etaSeconds = Math.max(0, remainingMs / 1000);

        String truncatedItem = currentItem != null ? truncate(currentItem, 22) : "";

        // Formats cleanly on a single line with ANSI line erasure
        String formattedLine = String.format(
                "\u001B[2K\r  %s[%d/%d] Schema: %s%s %s(%d/%d planned document%s)%s [%s] %s%5.1f%%%s | %sOverall%s [%s] %s%5.1f%%%s (%d/%d) | ETA %02d:%02d | %s%-20s%s",
                ANSI_BOLD + ANSI_NAVY, currentSchemaIndex, totalSchemas, currentSchemaName, ANSI_RESET,
                ANSI_MUTED, currentSchemaProgress, currentSchemaTotal, (currentSchemaTotal == 1 ? "" : "s"), ANSI_RESET,
                schemaBar,
                ANSI_GOLD, (schemaPct * 100.0), ANSI_RESET,
                ANSI_NAVY + ANSI_BOLD, ANSI_RESET,
                overallBar,
                ANSI_GREEN + ANSI_BOLD, (overallPct * 100.0), ANSI_RESET,
                currentOverall, totalOverall,
                (etaSeconds / 60), (etaSeconds % 60),
                ANSI_CYAN, truncatedItem, ANSI_RESET
        );

        System.out.print(formattedLine);
        System.out.flush();
    }

    private String buildBar(double progress, int width) {
        int filled = (int) Math.round(progress * width);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < width; i++) {
            if (i < filled) {
                bar.append(ANSI_GREEN).append("█").append(ANSI_RESET);
            } else {
                bar.append(ANSI_MUTED).append("░").append(ANSI_RESET);
            }
        }
        String result = bar.toString();
        return result;
    }

    /**
     * Completes the overall progress tracking and cleanly releases the console line.
     */
    public synchronized void finish() {
        this.currentOverall = totalOverall;
        System.out.print("\u001B[2K\r");
        System.out.flush();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            String empty = "";
            return empty;
        }
        if (s.length() <= max) {
            String orig = s;
            return orig;
        }
        String result = s.substring(0, max - 3) + "...";
        return result;
    }
}
