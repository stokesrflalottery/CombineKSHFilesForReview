package com.fllottery.kshresearch.model;

/**
 * Enterprise Classification Taxonomy for references and dependencies mined from
 * Florida Lottery Korn Shell (.ksh), Bourne Shell (.sh), and SQL*Plus (.sql) batch assets.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public enum ScriptReferenceType {

    ORACLE_STORED_PROCEDURE("Oracle Stored Procedure / Driver", "PL/SQL routine invoked via runOraclePKG.ksh wrapper or SQL exec"),
    ORACLE_PROCEDURE("Oracle Stored Procedure / Driver", "PL/SQL routine invoked via runOraclePKG.ksh wrapper or SQL exec"),
    SQLPLUS_SCRIPT("SQL*Plus Script File", "Direct execution of external .sql file via sqlplus @script.sql"),
    SOURCED_ENVIRONMENT("Sourced Environment File", "Included environment variables or login script via '. /path/file.env'"),
    SOURCED_ENV("Sourced Environment File", "Included environment variables or login script via '. /path/file.env'"),
    CHILD_KSH_SCRIPT("Child Batch Sub-Orchestrator", "Secondary Korn Shell script invoked to handle specific operational subroutines"),
    FILESYSTEM_LOG("Filesystem Log File", "Execution log spooled or redirected via '>' or '>>' for IWS auditability"),
    LOG_FILE("Filesystem Log File", "Execution log spooled or redirected via '>' or '>>' for IWS auditability"),
    REPORT_HTML("Generated HTML Report", "Executive or operational balance report emitted for web/portal distribution"),
    REPORT_CSV("Generated CSV Data Extract", "Comma-delimited transactional extract generated for external clearing"),
    FILE_SPOOL("SQL*Plus Spooled Artifact", "Data extract or diagnostic trace created via SQL*Plus SPOOL directive"),
    SQL_SPOOL_OUTPUT("SQL*Plus Spooled Artifact", "Data extract or diagnostic trace created via SQL*Plus SPOOL directive"),
    MAILX_NOTIFICATION("Mailx / SMTP Email Alert", "Operational notification or critical failure page dispatched via mailx/sendmail"),
    NOTIFICATION_MAILX("Mailx / SMTP Email Alert", "Operational notification or critical failure page dispatched via mailx/sendmail"),
    REMOTE_TRANSFER_SFTP("Remote SFTP / FTP Transmission", "Secure file transmission to external banking, gaming vendor, or state agency"),
    SFTP_TRANSFER("Remote SFTP / FTP Transmission", "Secure file transmission to external banking, gaming vendor, or state agency"),
    DATABASE_TABLE("Relational Database Table", "Underlying physical database table queried, updated, inserted, or locked"),
    SYSTEM_COMMAND("System Command / Utility", "Host operating system utility invoked (banner, hostname, date, awk, sed, grep)");

    private final String displayName;
    private final String description;

    ScriptReferenceType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        String result = this.displayName;
        return result;
    }

    public String getDescription() {
        String result = this.description;
        return result;
    }

    /**
     * Inspects a filename or path and determines its primary operational reference type.
     */
    public static ScriptReferenceType classifyFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            ScriptReferenceType result = FILESYSTEM_LOG;
            return result;
        }
        String clean = filePath.toLowerCase().trim();
        if (clean.endsWith(".html") || clean.endsWith(".htm")) {
            ScriptReferenceType result = REPORT_HTML;
            return result;
        }
        if (clean.endsWith(".csv")) {
            ScriptReferenceType result = REPORT_CSV;
            return result;
        }
        if (clean.endsWith(".sql")) {
            ScriptReferenceType result = SQLPLUS_SCRIPT;
            return result;
        }
        if (clean.endsWith(".env") || clean.contains("connlogin") || clean.contains("connection")) {
            ScriptReferenceType result = SOURCED_ENV;
            return result;
        }
        if (clean.endsWith(".ksh") || clean.endsWith(".sh")) {
            ScriptReferenceType result = CHILD_KSH_SCRIPT;
            return result;
        }
        ScriptReferenceType result = FILESYSTEM_LOG;
        return result;
    }
}
