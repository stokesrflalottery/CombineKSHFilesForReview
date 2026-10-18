package com.fllottery.common.io;

import java.io.File;

/**
 * Enterprise Filesystem and Cache Path Resolver for Florida Lottery DataOps.
 *
 * <p>Centralizes standard server directory paths across Windows workstations and OCI Linux servers:
 * <ul>
 *   <li>Primary Windows OCI Cache Root: {@code C:\ocioldapps1\Cached\}</li>
 *   <li>Primary OCI Linux Cache Root: {@code /fl/cached/}</li>
 *   <li>Local Workspace Fallback: {@code ./Cached/}</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class FlCachePaths {

    public static final String DEFAULT_WIN_ROOT = "C:\\ocioldapps1\\Cached\\";
    public static final String DEFAULT_LINUX_ROOT = "/fl/cached/";

    public static final String DEFAULT_DBCACHE_PATH = "C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json";
    public static final String DEFAULT_SCRIPTCACHE_PATH = "C:\\ocioldapps1\\Cached\\ScriptCache\\ScriptCache.json";
    public static final String DEFAULT_WORD_DOCS_OUTPUT_DIR = "C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\Word_Docs";
    public static final String DEFAULT_KSH_WORD_DOCS_DIR = "C:\\ocioldapps1\\Cached\\ScriptCache\\Word_Docs";

    private FlCachePaths() {
        // Utility class
    }

    /**
     * Resolves a target file against the prioritized environment roots (Custom -> Windows -> Linux -> Working Directory).
     */
    public static File resolveCacheFile(String customPath, String relativeDefault) {
        if (customPath != null && !customPath.isBlank()) {
            File custom = new File(customPath);
            if (custom.exists()) {
                File result = custom;
                return result;
            }
        }

        // Check Windows standard path
        File winFile = new File(DEFAULT_WIN_ROOT + relativeDefault);
        if (winFile.exists()) {
            File result = winFile;
            return result;
        }

        // Check OCI Linux standard path
        File linuxFile = new File(DEFAULT_LINUX_ROOT + relativeDefault);
        if (linuxFile.exists()) {
            File result = linuxFile;
            return result;
        }

        // Fallback to local working directory
        File localFile = new File("./Cached/" + relativeDefault);
        if (localFile.exists()) {
            File result = localFile;
            return result;
        }

        // Default to Windows path representation if none exists yet
        File result = winFile;
        return result;
    }

    /**
     * Resolves and ensures a target output directory exists.
     */
    public static File resolveOutputDir(String customPath, String defaultPath) {
        String path = (customPath != null && !customPath.isBlank()) ? customPath : defaultPath;
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File result = dir;
        return result;
    }
}
