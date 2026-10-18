package com.fllottery.common.util;

import java.util.logging.Logger;

/**
 * Enterprise Zero-Dependency Logging Facade for Florida Lottery ETL & Research Framework.
 *
 * <p>Wraps the standard Java Runtime Environment logging facility ({@link java.util.logging.Logger})
 * to provide SLF4J-style parameterized formatting ({@code "{}"} placeholders) with ZERO external
 * JAR dependency requirements. This ensures seamless compilation across standalone Maven modules,
 * IDE projects, Spring Boot applications, and CLI environments without classpath conflicts.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class FlLogger {

    private final Logger jul;

    private FlLogger(String name) {
        this.jul = Logger.getLogger(name);
    }

    /**
     * Obtains a thread-safe logger instance for the specified class.
     */
    public static FlLogger getLogger(Class<?> clazz) {
        String name = clazz != null ? clazz.getName() : "FL_LOTTERY_ETL";
        FlLogger result = new FlLogger(name);
        return result;
    }

    public void info(String message) {
        jul.info(message != null ? message : "");
    }

    public void info(String format, Object... args) {
        jul.info(formatMessage(format, args));
    }

    public void warn(String message) {
        jul.warning(message != null ? message : "");
    }

    public void warn(String format, Object... args) {
        jul.warning(formatMessage(format, args));
    }

    public void error(String message) {
        jul.severe(message != null ? message : "");
    }

    public void error(String format, Object... args) {
        jul.severe(formatMessage(format, args));
    }

    public void debug(String message) {
        jul.fine(message != null ? message : "");
    }

    public void debug(String format, Object... args) {
        jul.fine(formatMessage(format, args));
    }

    /**
     * Formats SLF4J style "{}" placeholders with runtime arguments.
     */
    private static String formatMessage(String format, Object... args) {
        if (format == null) {
            String result = "";
            return result;
        }
        if (args == null || args.length == 0) {
            String result = format;
            return result;
        }

        StringBuilder sb = new StringBuilder(format.length() + 64);
        int argIdx = 0;
        int i = 0;
        int len = format.length();

        while (i < len) {
            if (i < len - 1 && format.charAt(i) == '{' && format.charAt(i + 1) == '}') {
                if (argIdx < args.length) {
                    Object arg = args[argIdx++];
                    sb.append(arg != null ? arg.toString() : "null");
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else {
                sb.append(format.charAt(i));
                i++;
            }
        }

        String result = sb.toString();
        return result;
    }
}
