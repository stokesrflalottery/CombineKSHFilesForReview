package com.fllottery.ksh.miner;

import com.fllottery.ksh.model.KornShellScriptDocModel;
import com.fllottery.ksh.model.ScriptReferenceDoc;
import com.fllottery.ksh.model.ScriptReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Static AST &amp; Regex Mining Engine for Florida Lottery
 * Korn Shell (.ksh), Bourne Shell (.sh), and SQL*Plus (.sql) batch assets.
 *
 * <p>Exhaustively traces all operational dependencies:
 * <ul>
 *   <li>Oracle Packages &amp; Stored Procedure Drivers (runOraclePKG, runOraclePROC, exec, PL/SQL blocks)</li>
 *   <li>SQL*Plus files and scripts (@script.sql, @@nested.sql, inline heredocs)</li>
 *   <li>Sourced environment files (.env, .profile, .db2ConnLogin, .tlConn, MoveAndCopyConnection)</li>
 *   <li>Child .ksh and .sh sub-orchestrators (runInt, runMultiFileInt, runLdr, spoolOut, fPurge)</li>
 *   <li>Filesystem I/O, Redirections (&gt;, &gt;&gt;), Logs, Reports, and SPOOL outputs</li>
 *   <li>Email Notifications (mailx, mail, /usr/sbin/sendmail)</li>
 *   <li>SFTP, FTP, and SCP remote transmissions (IGT, FLAIR/DFS, DOR, HQFILEVM, ImageAPI)</li>
 *   <li>Physical relational database tables and views read or updated</li>
 *   <li>Script comment metadata (Author, Creation Date, Purpose, IRRF ticket numbers)</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.2.0
 */
public class KshReferenceMiner {

    private static final Logger log = LoggerFactory.getLogger(KshReferenceMiner.class);

    // Header metadata patterns
    private static final Pattern PATTERN_AUTHOR = Pattern.compile("^#+\\s*(?:Author|By|Developer)\\s*[:=]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_DATE = Pattern.compile("^#+\\s*(?:Date|Created)\\s*[:=]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_PURPOSE = Pattern.compile("^#+\\s*(?:Purpose|Description)\\s*[:=]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_HEADER_CONTINUATION = Pattern.compile("^#+\\s{2,}(.*)$");

    // Oracle stored procedure / package invocations in Shell
    private static final Pattern PATTERN_RUN_ORACLE_PKG = Pattern.compile(
            "(?:runOraclePKG|runOraclePROC|runOracleBatch|runOracleSQL)\\.ksh\\s+([A-Za-z0-9_$.]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Oracle stored procedure invocations inside SQL or heredocs (exec PKG.PROC, EXECUTE PKG.PROC)
    private static final Pattern PATTERN_SQL_EXEC_PROC = Pattern.compile(
            "^\\s*(?:exec|execute)\\s+([A-Za-z0-9_$.]+(?:\\([^)]*\\))?)",
            Pattern.CASE_INSENSITIVE
    );

    // Direct SQL*Plus file execution: sqlplus ... @/path/to/script.sql or @@nested.sql
    private static final Pattern PATTERN_SQLPLUS_FILE = Pattern.compile(
            "(?:@|@@)\\s*([/a-zA-Z0-9_$.-]+\\.sql)",
            Pattern.CASE_INSENSITIVE
    );

    // Sourced environment files: . /path/oltp.env, source /path/oltp.env, . /path/MoveAndCopyConnection
    private static final Pattern PATTERN_SOURCED_FILE = Pattern.compile(
            "(?:^|\\s)(?:\\.|source)\\s+([/a-zA-Z0-9_$.-]+\\.(?:env|ksh|sh|profile)|/[a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9_.-]+)*)",
            Pattern.CASE_INSENSITIVE
    );

    // Child Korn Shell scripts invoked directly or via path
    private static final Pattern PATTERN_CHILD_KSH = Pattern.compile(
            "(?:^|\\s|/)([a-zA-Z0-9_\\-]+\\.(?:ksh|sh))(?:\\s|$)",
            Pattern.CASE_INSENSITIVE
    );

    // SQL*Plus SPOOL statement: SPOOL filename or SPOOL &&1
    private static final Pattern PATTERN_SQL_SPOOL = Pattern.compile(
            "^\\s*spool\\s+([^;\\n]+)",
            Pattern.CASE_INSENSITIVE
    );

    // File redirections: > file or >> file
    private static final Pattern PATTERN_REDIRECTION = Pattern.compile(
            "(?:>>|>)\\s*([/a-zA-Z0-9_$%.()+\\-]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Explicit variable assignments: outfile=... or logfile=... or hisfile=...
    private static final Pattern PATTERN_FILE_ASSIGNMENT = Pattern.compile(
            "(?:out_?file|log_?file|in_?file|data_?file|rpt_?file|extract_?file|his_?file|spool_?file|reportfile)\\s*=\\s*[\"']?([^\"'\\s;]+)[\"']?",
            Pattern.CASE_INSENSITIVE
    );

    // Mailx / mail / sendmail alerts
    private static final Pattern PATTERN_MAILX = Pattern.compile(
            "(?:mailx|mail|/usr/sbin/sendmail)\\s+(?:-[a-zA-Z]\\s+[\"']?([^\"'\\n]+)[\"']?\\s+)*([$a-zA-Z0-9_@., -]+)",
            Pattern.CASE_INSENSITIVE
    );

    // SFTP / FTP / SCP commands
    private static final Pattern PATTERN_REMOTE_XFER = Pattern.compile(
            "(?:sftp|ftp|scp|ssh)\\s+([^\\n;]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Relational Database Table mentions in SQL statements (FROM, JOIN, INTO, UPDATE)
    private static final Pattern PATTERN_SQL_TABLE = Pattern.compile(
            "(?:\\bfrom|\\bjoin|\\binto|\\bupdate|\\btable)\\s+([A-Za-z0-9_$.]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Table mentions in comments
    private static final Pattern PATTERN_TABLE_COMMENT = Pattern.compile(
            "(?:in the|from|into|update|table)\\s+([A-Z0-9_]{3,30})\\s+(?:table|\\(|set|where|,|$)",
            Pattern.CASE_INSENSITIVE
    );

    // Parameter placeholders
    private static final Pattern PATTERN_PARAMETER = Pattern.compile(
            "\\$([0-9]|\\{[0-9]\\}|[A-Z0-9_]+_DATE|[A-Z0-9_]+_ID|[A-Z0-9_]+_FILE)"
    );

    /**
     * Executes the complete static analysis pass over a KornShellScriptDocModel.
     * Enriches the model in-place with mined references, metadata, and complexity metrics.
     *
     * @param scriptModel the target script doc model to analyze
     * @return the analyzed and enriched KornShellScriptDocModel
     */
    public KornShellScriptDocModel analyze(KornShellScriptDocModel scriptModel) {
        if (scriptModel == null) {
            KornShellScriptDocModel nullResult = null;
            return nullResult;
        }

        String code = scriptModel.getCode();
        if (code == null || code.isBlank()) {
            scriptModel.setComplexityScore(0);
            scriptModel.setLineCount(0);
            KornShellScriptDocModel emptyResult = scriptModel;
            return emptyResult;
        }

        try {
            List<ScriptReferenceDoc> references = new ArrayList<>();
            Set<String> detectedTargets = new HashSet<>();
            Set<String> parameters = new TreeSet<>();

            String[] lines = code.split("\\r?\\n");
            scriptModel.setLineCount(lines.length);

            // Step 1: Mine Header Metadata (Author, Date, Purpose)
            mineHeaderMetadata(lines, scriptModel);

            // Step 2: Line-by-line static analysis
            int complexity = 0;
            boolean inSqlPlusBlock = false;
            boolean isSqlFile = scriptModel.getFilename() != null && scriptModel.getFilename().toLowerCase().endsWith(".sql");

            for (int i = 0; i < lines.length; i++) {
                int lineNumber = i + 1;
                String line = lines[i].trim();

                // Track complexity indicators
                if (line.startsWith("if ") || line.startsWith("elif ") || line.startsWith("case ") ||
                        line.startsWith("while ") || line.startsWith("for ") || line.contains("&&") || line.contains("||")) {
                    complexity += 2;
                }

                // Check SQL*Plus heredoc start/stop
                if (line.toLowerCase().contains("sqlplus") && line.contains("<<")) {
                    inSqlPlusBlock = true;
                    complexity += 3;
                }
                if (inSqlPlusBlock && (line.equalsIgnoreCase("EOF") || line.equalsIgnoreCase("EOD") || line.equalsIgnoreCase("END") || line.startsWith("EXIT") || line.startsWith("exit;"))) {
                    inSqlPlusBlock = false;
                }

                // Skip comment lines for execution mining, but extract table mentions from comments
                if (line.startsWith("#") || line.startsWith("--") || line.startsWith("/*") || line.startsWith("*")) {
                    mineTableMentions(line, lineNumber, references, detectedTargets);
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                // Detect Parameters ($1, $DATE, &&1)
                mineParameters(line, parameters);

                // Detect Oracle Procedure / Package calls
                mineOracleProcedures(line, lineNumber, references, detectedTargets);

                // Detect SQL*Plus external scripts (@script.sql or @@conn.sql)
                mineSqlPlusScripts(line, lineNumber, references, detectedTargets);

                // Detect SQL*Plus SPOOL outputs
                mineSqlSpoolOutput(line, lineNumber, references, detectedTargets);

                // Detect Sourced Environments and Profiles
                mineSourcedEnvironments(line, lineNumber, references, detectedTargets);

                // Detect Child .ksh / .sh scripts
                mineChildScripts(line, lineNumber, references, detectedTargets, scriptModel.getBaseFilename());

                // Detect File Assignments & Redirections (logs, reports, extracts)
                mineFileOperations(line, lineNumber, references, detectedTargets);

                // Detect Mailx / sendmail notifications
                mineMailxNotifications(line, lineNumber, references, detectedTargets);

                // Detect SFTP / FTP / SCP transfers
                mineRemoteTransfers(line, lineNumber, references, detectedTargets);

                // Detect SQL Queries & Table DML
                mineSqlTableReferences(line, lineNumber, references, detectedTargets);
            }

            // Step 3: Calculate composite complexity score
            complexity += references.size() * 3;
            complexity += (lines.length / 10);
            scriptModel.setComplexityScore(complexity);

            // Sort references in canonical order
            Collections.sort(references);
            scriptModel.setScriptReferences(references);
            scriptModel.setParameters(new ArrayList<>(parameters));
            scriptModel.setLastAnalyzedTimestamp(Instant.now().toString());

        } catch (Exception ex) {
            log.error("Failed to analyze script: " + scriptModel.getFilename(), ex);
        }

        KornShellScriptDocModel result = scriptModel;
        return result;
    }

    /**
     * Mines Author, Creation Date, and Purpose from comment headers (supporting both shell # and SQL -- comments).
     */
    private void mineHeaderMetadata(String[] lines, KornShellScriptDocModel model) {
        StringBuilder purposeBuilder = new StringBuilder();
        boolean inPurposeBlock = false;

        for (int i = 0; i < Math.min(lines.length, 60); i++) {
            String rawLine = lines[i].trim();

            if (!rawLine.startsWith("#") && !rawLine.startsWith("--")) {
                if (inPurposeBlock) {
                    break;
                }
                continue;
            }

            // Author match
            Matcher authorMatcher = PATTERN_AUTHOR.matcher(rawLine);
            if (authorMatcher.find()) {
                String author = authorMatcher.group(1).trim();
                if (model.getAuthor() == null || model.getAuthor().isBlank()) {
                    model.setAuthor(author);
                }
                inPurposeBlock = false;
                continue;
            }

            // Date match
            Matcher dateMatcher = PATTERN_DATE.matcher(rawLine);
            if (dateMatcher.find()) {
                String date = dateMatcher.group(1).trim();
                if (model.getCreationDate() == null || model.getCreationDate().isBlank()) {
                    model.setCreationDate(date);
                }
                inPurposeBlock = false;
                continue;
            }

            // Purpose match
            Matcher purposeMatcher = PATTERN_PURPOSE.matcher(rawLine);
            if (purposeMatcher.find()) {
                String text = purposeMatcher.group(1).trim();
                if (!text.isBlank() && !text.startsWith("---") && !text.startsWith("===")) {
                    purposeBuilder.append(text);
                }
                inPurposeBlock = true;
                continue;
            }

            // Purpose multi-line continuation
            if (inPurposeBlock) {
                String sanitized = rawLine.replaceFirst("^[#\\-\\s]+", "").trim();
                if (!sanitized.startsWith("---") && !sanitized.startsWith("===") && !sanitized.isBlank() && !sanitized.toLowerCase().startsWith("mod") && !sanitized.toLowerCase().startsWith("author")) {
                    purposeBuilder.append(" ").append(sanitized);
                } else {
                    inPurposeBlock = false;
                }
            }
        }

        if (purposeBuilder.length() > 0 && (model.getPurpose() == null || model.getPurpose().isBlank())) {
            String sanitizedPurpose = purposeBuilder.toString().replaceAll("\\s+", " ").trim();
            model.setPurpose(sanitizedPurpose);
        }
    }

    /**
     * Mines runOraclePKG.ksh, runOraclePROC.ksh, exec <PROC>, and PL/SQL block calls.
     */
    private void mineOracleProcedures(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        // Pattern 1: Shell wrapper call (runOraclePKG.ksh <TARGET>)
        Matcher pkgMatcher = PATTERN_RUN_ORACLE_PKG.matcher(line);
        while (pkgMatcher.find()) {
            String target = pkgMatcher.group(1).trim();
            addOracleRef(target, lineNum, line, refs, detected, "Invokes Oracle database stored procedure or package driver via runOraclePKG wrapper");
        }

        // Pattern 2: SQL exec statement (exec <PKG>.<PROC>)
        Matcher execMatcher = PATTERN_SQL_EXEC_PROC.matcher(line);
        while (execMatcher.find()) {
            String fullTarget = execMatcher.group(1).trim();
            // Strip parameter parentheticals for the target ID
            String target = fullTarget.split("\\(")[0].trim();
            if (target.contains(".") && !target.toLowerCase().startsWith("dbms_") && !target.toLowerCase().startsWith("sys.")) {
                addOracleRef(target, lineNum, line, refs, detected, "Direct PL/SQL execution of database procedure/package");
            }
        }
    }

    private void addOracleRef(String target, int lineNum, String line, List<ScriptReferenceDoc> refs, Set<String> detected, String note) {
        String cleanTarget = target.replaceAll("[\"';()]", "").trim();
        String uniqueKey = "PROC:" + cleanTarget.toUpperCase();
        if (!detected.contains(uniqueKey)) {
            detected.add(uniqueKey);
            ScriptReferenceDoc doc = new ScriptReferenceDoc(
                    ScriptReferenceType.ORACLE_PROCEDURE,
                    cleanTarget,
                    lineNum,
                    line,
                    note
            );
            doc.populateOracleComponents();
            refs.add(doc);
        }
    }

    /**
     * Mines external .sql files executed via @/path/to/script.sql or @@conn.sql.
     */
    private void mineSqlPlusScripts(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        Matcher matcher = PATTERN_SQLPLUS_FILE.matcher(line);
        while (matcher.find()) {
            String target = matcher.group(1).trim();
            String uniqueKey = "SQLPLUS:" + target.toLowerCase();
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.SQLPLUS_SCRIPT,
                        target,
                        lineNum,
                        line,
                        "Direct SQL*Plus batch script execution (@" + target + ")"
                );
                refs.add(doc);
            }
        }
    }

    /**
     * Mines SQL*Plus SPOOL statements.
     */
    private void mineSqlSpoolOutput(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        Matcher matcher = PATTERN_SQL_SPOOL.matcher(line);
        while (matcher.find()) {
            String target = matcher.group(1).trim().replaceAll("[\"';]", "");
            if (target.equalsIgnoreCase("off") || target.equalsIgnoreCase("on")) {
                continue;
            }

            String uniqueKey = "SPOOL:" + target.toLowerCase();
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.SQL_SPOOL_OUTPUT,
                        target,
                        lineNum,
                        line,
                        "SQL*Plus SPOOL command directs formatted output to target file"
                );
                refs.add(doc);
            }
        }
    }

    /**
     * Mines sourced environment and configuration files (.env, .db2ConnLogin, .tlConn, MoveAndCopyConnection).
     */
    private void mineSourcedEnvironments(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        Matcher matcher = PATTERN_SOURCED_FILE.matcher(line);
        while (matcher.find()) {
            String target = matcher.group(1).trim();
            if (target.equalsIgnoreCase("/") || target.equalsIgnoreCase(".")) {
                continue;
            }

            // Filter out common false positives in echo or paths
            if (!target.contains("/") && !target.endsWith(".env") && !target.endsWith(".sh") && !target.endsWith(".ksh")) {
                continue;
            }

            String uniqueKey = "ENV:" + target.toLowerCase();
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.SOURCED_ENV,
                        target,
                        lineNum,
                        line,
                        "Sourced shell environment configuring system variables, credentials, or TNS aliases"
                );
                doc.setDirection("INCOMING");
                refs.add(doc);
            }
        }
    }

    /**
     * Mines child shell script invocations.
     */
    private void mineChildScripts(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected, String parentFilename) {
        Matcher matcher = PATTERN_CHILD_KSH.matcher(line);
        while (matcher.find()) {
            String childKsh = matcher.group(1).trim();

            // Ignore system wrappers or self
            if (childKsh.equalsIgnoreCase("runOraclePKG.ksh") ||
                    childKsh.equalsIgnoreCase("runOraclePROC.ksh") ||
                    childKsh.equalsIgnoreCase("runOracleBatch.ksh") ||
                    childKsh.equalsIgnoreCase("runOracleSQL.ksh") ||
                    childKsh.equalsIgnoreCase(parentFilename)) {
                continue;
            }

            String uniqueKey = "CHILD:" + childKsh.toLowerCase();
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.CHILD_KSH_SCRIPT,
                        childKsh,
                        lineNum,
                        line,
                        "Invokes secondary child Korn Shell script orchestrator"
                );
                refs.add(doc);
            }
        }
    }

    /**
     * Mines explicit file assignments, redirections, and extracts.
     */
    private void mineFileOperations(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        // Explicit file assignments (logfile=..., outfile=..., hisfile=...)
        Matcher assignMatcher = PATTERN_FILE_ASSIGNMENT.matcher(line);
        while (assignMatcher.find()) {
            String target = assignMatcher.group(1).trim();
            if (target.contains("/") || target.contains(".")) {
                addFileReference(target, lineNum, line, refs, detected, "Assigned filesystem artifact");
            }
        }

        // Redirections (> or >>)
        Matcher redirMatcher = PATTERN_REDIRECTION.matcher(line);
        while (redirMatcher.find()) {
            String target = redirMatcher.group(1).trim();
            if (target.contains("/") || target.endsWith(".log") || target.endsWith(".txt") || target.endsWith(".csv") || target.endsWith(".dat")) {
                addFileReference(target, lineNum, line, refs, detected, "Stdout/stderr output redirection");
            }
        }
    }

    private void addFileReference(String target, int lineNum, String line, List<ScriptReferenceDoc> refs, Set<String> detected, String note) {
        String cleanTarget = target.replaceAll("[\"';]", "").trim();
        if (cleanTarget.isBlank() || cleanTarget.equals("/dev/null") || cleanTarget.equals("&1") || cleanTarget.equals("&2")) {
            return;
        }

        ScriptReferenceType refType = ScriptReferenceType.classifyFilePath(cleanTarget);
        String uniqueKey = refType.name() + ":" + cleanTarget.toLowerCase();

        if (!detected.contains(uniqueKey)) {
            detected.add(uniqueKey);
            ScriptReferenceDoc doc = new ScriptReferenceDoc(
                    refType,
                    cleanTarget,
                    lineNum,
                    line,
                    note + " (" + refType.getDisplayName() + ")"
            );
            refs.add(doc);
        }
    }

    /**
     * Mines mailx, mail, and sendmail alerts.
     */
    private void mineMailxNotifications(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        Matcher matcher = PATTERN_MAILX.matcher(line);
        if (matcher.find()) {
            String target = matcher.group(0).trim();
            String uniqueKey = "MAILX:" + lineNum;
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.NOTIFICATION_MAILX,
                        target,
                        lineNum,
                        line,
                        "Dispatches automated operational alert via UNIX mail utility"
                );
                refs.add(doc);
            }
        }
    }

    /**
     * Mines SFTP, FTP, SCP, and remote SSH file movement.
     */
    private void mineRemoteTransfers(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        Matcher matcher = PATTERN_REMOTE_XFER.matcher(line);
        if (matcher.find()) {
            String target = matcher.group(0).trim();
            // Filter out plain ssh test checks (like ssh hostname "ls")
            if (target.startsWith("ssh ") && !target.contains(".ksh") && !target.contains("put") && !target.contains("get")) {
                return;
            }

            String uniqueKey = "XFER:" + lineNum;
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.SFTP_TRANSFER,
                        target,
                        lineNum,
                        line,
                        "Remote file transfer or cross-server automated movement"
                );
                refs.add(doc);
            }
        }
    }

    /**
     * Mines relational database tables directly referenced in SQL statements (FROM, JOIN, INTO, UPDATE).
     */
    private void mineSqlTableReferences(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        Matcher matcher = PATTERN_SQL_TABLE.matcher(line);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim().replaceAll("[\"';(),]", "");
            if (candidate.isBlank() || candidate.equalsIgnoreCase("dual") || candidate.equalsIgnoreCase("select") || candidate.startsWith("$")) {
                continue;
            }

            // Exclude common keywords that follow FROM / JOIN
            if (candidate.equalsIgnoreCase("where") || candidate.equalsIgnoreCase("order") || candidate.equalsIgnoreCase("group") || candidate.equalsIgnoreCase("set")) {
                continue;
            }

            String uniqueKey = "TABLE:" + candidate.toUpperCase();
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.DATABASE_TABLE,
                        candidate.toUpperCase(),
                        lineNum,
                        line,
                        "Relational table accessed via SQL DML / query"
                );
                doc.setConfidence("EXACT");
                refs.add(doc);
            }
        }
    }

    /**
     * Mines database table names from comments.
     */
    private void mineTableMentions(String line, int lineNum, List<ScriptReferenceDoc> refs, Set<String> detected) {
        Matcher matcher = PATTERN_TABLE_COMMENT.matcher(line);
        while (matcher.find()) {
            String tableName = matcher.group(1).trim().toUpperCase();
            if (tableName.equals("THE") || tableName.equals("FOR") || tableName.equals("AND") || tableName.equals("DATE") || tableName.equals("THIS") || tableName.equals("THAT")) {
                continue;
            }

            String uniqueKey = "TABLE:" + tableName;
            if (!detected.contains(uniqueKey)) {
                detected.add(uniqueKey);
                ScriptReferenceDoc doc = new ScriptReferenceDoc(
                        ScriptReferenceType.DATABASE_TABLE,
                        tableName,
                        lineNum,
                        line,
                        "Referenced relational table mined from script header/comments"
                );
                doc.setConfidence("HEURISTIC");
                refs.add(doc);
            }
        }
    }

    /**
     * Mines command-line positional parameters or business date variables ($1, &&1).
     */
    private void mineParameters(String line, Set<String> params) {
        Matcher matcher = PATTERN_PARAMETER.matcher(line);
        while (matcher.find()) {
            params.add(matcher.group(0));
        }

        // SQL*Plus substitution variables (&&1, &&2)
        if (line.contains("&&")) {
            Matcher sqlParam = Pattern.compile("&&([0-9A-Za-z_]+)").matcher(line);
            while (sqlParam.find()) {
                params.add("&&" + sqlParam.group(1));
            }
        }
    }
}
