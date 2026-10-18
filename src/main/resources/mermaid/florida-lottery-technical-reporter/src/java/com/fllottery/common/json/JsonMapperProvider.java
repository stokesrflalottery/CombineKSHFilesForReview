package com.fllottery.common.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Enterprise Shared Jackson ObjectMapper Provider for Florida Lottery.
 *
 * <p>Enforces global serialization governance across all batch extractors,
 * AI Studio payloads, and cache files:
 * <ul>
 *   <li>Registered {@link JavaTimeModule} for robust Java 8/21 date-time handling</li>
 *   <li>ISO-8601 date formatting (dates serialized as strings, not numeric timestamps)</li>
 *   <li>Pretty-printing enabled for clean human-readable JSON files</li>
 *   <li>Defensive deserialization: {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled to prevent crashes on schema evolution</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class JsonMapperProvider {

    private static final ObjectMapper MAPPER = createConfiguredMapper();

    private JsonMapperProvider() {
        // Utility class
    }

    private static ObjectMapper createConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
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
