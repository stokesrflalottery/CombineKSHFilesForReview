package com.fllottery.common.diagram;

/**
 * Enterprise Mermaid Diagram Syntax, Lexer Escaping, and Brand Palette Helper for Florida Lottery.
 *
 * <p>Prevents Mermaid Jison lexer syntax errors by:
 * <ul>
 *   <li>Sanitizing node identifiers to valid alphanumeric tokens ({@code [^a-zA-Z0-9_]})</li>
 *   <li>Escaping quotes, angle brackets, brackets, pipes, and control characters in labels</li>
 *   <li>Providing official Florida Lottery {@code classDef} CSS declarations (Plum, Navy, Green, Gold)</li>
 *   <li>Generating standard flowchart edges and connector symbols</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class MermaidDslHelper {

    private MermaidDslHelper() {
        // Utility class
    }

    /**
     * Sanitizes raw object/script names into valid Mermaid node IDs.
     */
    public static String sanitizeNodeId(String raw) {
        if (raw == null || raw.isBlank()) {
            String result = "NODE_UNKNOWN";
            return result;
        }
        String result = raw.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        return result;
    }

    /**
     * Safely escapes text to prevent Mermaid parser crashes.
     */
    public static String escapeLabel(String raw) {
        if (raw == null) {
            String result = "";
            return result;
        }
        String clean = raw.trim()
                .replace("\"", "'")
                .replace("\r", "")
                .replace("\n", "<br/>")
                .replace("[", "&#91;")
                .replace("]", "&#93;")
                .replace("(", "&#40;")
                .replace(")", "&#41;")
                .replace("|", "&#124;");
        String result = clean;
        return result;
    }

    /**
     * Returns standard Florida Lottery brand classDef declarations for flowchart and graph diagrams.
     */
    public static String getBrandClassDefs() {
        StringBuilder sb = new StringBuilder();
        sb.append("  classDef iwsClass fill:#002B49,stroke:#001726,stroke-width:2px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("  classDef kshClass fill:#5C1D48,stroke:#3D1230,stroke-width:3px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("  classDef procClass fill:#7C3AED,stroke:#5B21B6,stroke-width:2px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("  classDef sqlClass fill:#0284C7,stroke:#0369A1,stroke-width:2px,color:#FFFFFF;\n");
        sb.append("  classDef tableClass fill:#16A34A,stroke:#15803D,stroke-width:2px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("  classDef fileClass fill:#475569,stroke:#334155,stroke-width:2px,color:#FFFFFF;\n");
        sb.append("  classDef mailClass fill:#DC2626,stroke:#B91C1C,stroke-width:2px,color:#FFFFFF;\n");
        sb.append("  classDef envClass fill:#0D9488,stroke:#0F766E,stroke-width:2px,color:#FFFFFF;\n\n");
        String result = sb.toString();
        return result;
    }

    /**
     * Builds a formatted node definition: {@code nodeId["label"]:::styleClass}.
     */
    public static String buildNode(String nodeId, String label, String styleClass) {
        String safeId = sanitizeNodeId(nodeId);
        String safeLabel = escapeLabel(label);
        String cls = (styleClass != null && !styleClass.isBlank()) ? (":::" + styleClass.trim()) : "";
        String result = String.format("    %s[\"%s\"]%s\n", safeId, safeLabel, cls);
        return result;
    }

    /**
     * Builds a cylinder database table node: {@code nodeId[("label")]:::tableClass}.
     */
    public static String buildTableNode(String nodeId, String label) {
        String safeId = sanitizeNodeId(nodeId);
        String safeLabel = escapeLabel(label);
        String result = String.format("    %s[(\"🗄️ %s\")]:::tableClass\n", safeId, safeLabel);
        return result;
    }

    /**
     * Builds a standard directed edge: {@code fromId -->|label| toId}.
     */
    public static String buildDirectedEdge(String fromId, String toId, String edgeLabel) {
        String sFrom = sanitizeNodeId(fromId);
        String sTo = sanitizeNodeId(toId);
        if (edgeLabel != null && !edgeLabel.isBlank()) {
            String result = String.format("    %s -->|\"%s\"| %s\n", sFrom, escapeLabel(edgeLabel), sTo);
            return result;
        }
        String result = String.format("    %s --> %s\n", sFrom, sTo);
        return result;
    }

    /**
     * Builds a thick scheduling edge: {@code fromId ==>|label| toId}.
     */
    public static String buildThickEdge(String fromId, String toId, String edgeLabel) {
        String sFrom = sanitizeNodeId(fromId);
        String sTo = sanitizeNodeId(toId);
        if (edgeLabel != null && !edgeLabel.isBlank()) {
            String result = String.format("    %s ==>|\"%s\"| %s\n", sFrom, escapeLabel(edgeLabel), sTo);
            return result;
        }
        String result = String.format("    %s ==> %s\n", sFrom, sTo);
        return result;
    }

    /**
     * Builds a dotted dependency edge: {@code fromId -.->|label| toId}.
     */
    public static String buildDottedEdge(String fromId, String toId, String edgeLabel) {
        String sFrom = sanitizeNodeId(fromId);
        String sTo = sanitizeNodeId(toId);
        if (edgeLabel != null && !edgeLabel.isBlank()) {
            String result = String.format("    %s -.->|\"%s\"| %s\n", sFrom, escapeLabel(edgeLabel), sTo);
            return result;
        }
        String result = String.format("    %s -.-> %s\n", sFrom, sTo);
        return result;
    }
}
