package com.fllottery.common.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Enterprise Shared Jackson ObjectMapper Provider for Florida Lottery.
 *
 * <p>Enforces global serialization governance across all batch extractors,
 * AI Studio payloads, and cache files:
 * <ul>
 *   <li>Dynamically registers {@code JavaTimeModule} if present on the classpath</li>
 *   <li>ISO-8601 date formatting (dates serialized as strings, not numeric timestamps)</li>
 *   <li>Pretty-printing enabled for clean human-readable JSON files</li>
 *   <li>Defensive deserialization: {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled to prevent crashes on schema evolution</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.1.0
 */
public final class JsonMapperProvider {

    private static final ObjectMapper MAPPER = createConfiguredMapper();

    private JsonMapperProvider() {
        // Utility class
    }

    private static ObjectMapper createConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register JavaTimeModule reflectively if present without hard compile-time dependency
        try {
            Class<?> moduleClass = Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
            com.fasterxml.jackson.databind.Module module = (com.fasterxml.jackson.databind.Module) moduleClass.getDeclaredConstructor().newInstance();
            mapper.registerModule(module);
        } catch (Throwable ignored) {
            // Module not on classpath, continue gracefully with Jackson baseline serialization
        }

        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectMapper result = mapper;
        return result;
    }

    /**
     * Returns the globally configured, thread-safe ObjectMapper instance.
     */
    public static ObjectMapper get() {
        ObjectMapper result = MAPPER;
        return result;
    }
}
