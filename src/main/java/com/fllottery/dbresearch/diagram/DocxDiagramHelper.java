package com.fllottery.dbresearch.diagram;

import com.fllottery.common.office.FlBrandTheme;
import com.fllottery.dbresearch.doc.GenerateTechnicalWordDocs;
import com.fllottery.dbresearch.research.AiStudioProcResearcherService;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Flowchart & Visual Execution Diagram Engine for Florida Lottery DOCX Reports.
 * <p>
 * Standard ISO/ANSI 5807 Compliant Vector Flowchart & Multi-Branching Decision Tree Engine.
 * Features:
 *  1. Inscribed Diamond Geometries: Text is mathematically bounded to the inscribed diamond center
 *     (width * 0.52) with centered alignment so text NEVER bleeds outside the angled borders.
 *  2. No Duplicate Headings: Seamless integration with document generator section titles.
 *  3. Single-Page Budgeting: Supports up to 9 steps (START, 7 phases, END) per page to utilize full vertical space.
 *  4. 4X - 6X Ultra-Retina Super-Sampling (600+ DPI) for razor-sharp high-resolution Word previews and PDF exports.
 *  5. Multi-Branch Decision Trees: Downward [VALID] spine with Right-side [INVALID] rejection cards.
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery DataOps Architecture
 * @since 2026-09-29
 */
public class DocxDiagramHelper {

    // Florida Lottery Enterprise Color Palette (Hex & Java AWT)
    public static final String HEX_NAVY = "002B49";     // #002B49 Deep Navy
    public static final String HEX_BLUE = "005A9C";     // #005A9C Florida Lottery Blue
    public static final String HEX_GOLD = "DAA520";     // #DAA520 Accent Gold
    public static final String HEX_GREEN = "228B22";    // #228B22 Forest Green (Success / Audit)
    public static final String HEX_RED = "B91C1C";      // #B91C1C Error / Rejection Red
    public static final String HEX_PLUM = "581845";     // #581845 Deep Plum
    public static final String HEX_SLATE = "475569";    // #475569 Border & Connector Slate
    public static final String HEX_LIGHT_BG = "F8FAFC"; // #F8FAFC Shape Background

    public static final Color COLOR_NAVY = new Color(0, 43, 73);
    public static final Color COLOR_BLUE = new Color(0, 90, 156);
    public static final Color COLOR_GOLD = new Color(218, 165, 32);
    public static final Color COLOR_GREEN = new Color(34, 139, 34);
    public static final Color COLOR_RED = new Color(185, 28, 28);
    public static final Color COLOR_PLUM = new Color(88, 24, 69);
    public static final Color COLOR_SLATE = new Color(71, 85, 105);

    // Maximum steps allocated per page before smart slicing occurs (7-8 levels per page ensures large, highly legible node text)
    private static final int MAX_STEPS_PER_PAGE = 7;

    /**
     * Top-level StepType enum with full backward compatibility.
     */
    public enum StepType {
        TERMINATOR(HEX_GREEN),
        START(HEX_GREEN),
        END(HEX_GREEN),
        PROCESS(HEX_BLUE),
        DECISION(HEX_GOLD),
        INPUT_OUTPUT(HEX_BLUE),
        IO(HEX_BLUE),
        IO_INPUT(HEX_BLUE),
        IO_OUTPUT(HEX_BLUE),
        DATABASE(HEX_PLUM),
        DB_OPERATION(HEX_PLUM),
        TABLE(HEX_PLUM),
        PREDEFINED_PROCESS(HEX_NAVY),
        CALL_EXTERNAL(HEX_NAVY);

        private final String defaultHexColor;

        StepType(String defaultHexColor) {
            this.defaultHexColor = defaultHexColor;
        }

        public String getDefaultHexColor() {
            String result = this.defaultHexColor;
            return result;
        }
    }

    /**
     * Represents a single step or decision node in the execution pipeline.
     */
    public static class FlowStep {

        public enum StepType {
            TERMINATOR(DocxDiagramHelper.StepType.TERMINATOR),
            START(DocxDiagramHelper.StepType.START),
            END(DocxDiagramHelper.StepType.END),
            PROCESS(DocxDiagramHelper.StepType.PROCESS),
            DECISION(DocxDiagramHelper.StepType.DECISION),
            INPUT_OUTPUT(DocxDiagramHelper.StepType.INPUT_OUTPUT),
            IO(DocxDiagramHelper.StepType.IO),
            IO_INPUT(DocxDiagramHelper.StepType.IO_INPUT),
            IO_OUTPUT(DocxDiagramHelper.StepType.IO_OUTPUT),
            DATABASE(DocxDiagramHelper.StepType.DATABASE),
            DB_OPERATION(DocxDiagramHelper.StepType.DB_OPERATION),
            TABLE(DocxDiagramHelper.StepType.TABLE),
            PREDEFINED_PROCESS(DocxDiagramHelper.StepType.PREDEFINED_PROCESS),
            CALL_EXTERNAL(DocxDiagramHelper.StepType.CALL_EXTERNAL);

            private final DocxDiagramHelper.StepType parentType;

            StepType(DocxDiagramHelper.StepType parentType) {
                this.parentType = parentType;
            }

            public DocxDiagramHelper.StepType getParentType() {
                DocxDiagramHelper.StepType result = this.parentType;
                return result;
            }

            public String getDefaultHexColor() {
                String result = this.parentType.getDefaultHexColor();
                return result;
            }
        }

        public final int stepNumber;
        public final String title;
        public final String description;
        public final DocxDiagramHelper.StepType type;
        public final String conditionYes;
        public final String conditionNo;
        public final String rejectAction;
        public final String hexThemeColor;

        // -------------------------------------------------------------------------------------
        // 4-ARGUMENT CONSTRUCTORS
        // -------------------------------------------------------------------------------------
        public FlowStep(int stepNumber, String title, String description, FlowStep.StepType stepType) {
            this(stepNumber, title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS, null, null, null,
                    (stepType != null) ? stepType.getDefaultHexColor() : HEX_BLUE);
        }

        public FlowStep(int stepNumber, String title, String description, DocxDiagramHelper.StepType stepType) {
            this(stepNumber, title, description, stepType, null, null, null,
                    (stepType != null) ? stepType.getDefaultHexColor() : HEX_BLUE);
        }

        public FlowStep(String stepNumber, String title, String description, FlowStep.StepType stepType) {
            this(parseStepNum(stepNumber), title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS, null, null, null,
                    (stepType != null) ? stepType.getDefaultHexColor() : HEX_BLUE);
        }

        public FlowStep(String stepNumber, String title, String description, DocxDiagramHelper.StepType stepType) {
            this(parseStepNum(stepNumber), title, description, stepType, null, null, null,
                    (stepType != null) ? stepType.getDefaultHexColor() : HEX_BLUE);
        }

        // -------------------------------------------------------------------------------------
        // 5-ARGUMENT CONSTRUCTORS
        // -------------------------------------------------------------------------------------
        public FlowStep(int stepNumber, String title, String description, FlowStep.StepType stepType, String hexThemeColor) {
            this(stepNumber, title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS, null, null, null, hexThemeColor);
        }

        public FlowStep(int stepNumber, String title, String description, DocxDiagramHelper.StepType stepType, String hexThemeColor) {
            this(stepNumber, title, description, stepType, null, null, null, hexThemeColor);
        }

        public FlowStep(int stepNumber, String title, String description, FlowStep.StepType stepType, Color themeColor) {
            this(stepNumber, title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS, null, null, null, toHex(themeColor));
        }

        public FlowStep(int stepNumber, String title, String description, DocxDiagramHelper.StepType stepType, Color themeColor) {
            this(stepNumber, title, description, stepType, null, null, null, toHex(themeColor));
        }

        public FlowStep(String stepNumber, String title, String description, FlowStep.StepType stepType, String hexThemeColor) {
            this(parseStepNum(stepNumber), title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS, null, null, null, hexThemeColor);
        }

        public FlowStep(String stepNumber, String title, String description, DocxDiagramHelper.StepType stepType, String hexThemeColor) {
            this(parseStepNum(stepNumber), title, description, stepType, null, null, null, hexThemeColor);
        }

        public FlowStep(String stepNumber, String title, String description, FlowStep.StepType stepType, Color themeColor) {
            this(parseStepNum(stepNumber), title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS, null, null, null, toHex(themeColor));
        }

        public FlowStep(String stepNumber, String title, String description, DocxDiagramHelper.StepType stepType, Color themeColor) {
            this(parseStepNum(stepNumber), title, description, stepType, null, null, null, toHex(themeColor));
        }

        // -------------------------------------------------------------------------------------
        // 7-ARGUMENT CONSTRUCTORS
        // -------------------------------------------------------------------------------------
        public FlowStep(int stepNumber, String title, String description, FlowStep.StepType stepType, String conditionYes, String conditionNo, String rejectAction) {
            this(stepNumber, title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS,
                    conditionYes, conditionNo, rejectAction, (stepType != null) ? stepType.getDefaultHexColor() : HEX_GOLD);
        }

        public FlowStep(int stepNumber, String title, String description, DocxDiagramHelper.StepType stepType, String conditionYes, String conditionNo, String rejectAction) {
            this(stepNumber, title, description, stepType, conditionYes, conditionNo, rejectAction, (stepType != null) ? stepType.getDefaultHexColor() : HEX_GOLD);
        }

        public FlowStep(String stepNumber, String title, String description, FlowStep.StepType stepType, String conditionYes, String conditionNo, String rejectAction) {
            this(parseStepNum(stepNumber), title, description, (stepType != null) ? stepType.getParentType() : DocxDiagramHelper.StepType.PROCESS,
                    conditionYes, conditionNo, rejectAction, (stepType != null) ? stepType.getDefaultHexColor() : HEX_GOLD);
        }

        // Master Constructor
        public FlowStep(int stepNumber, String title, String description, DocxDiagramHelper.StepType stepType,
                        String conditionYes, String conditionNo, String rejectAction, String hexThemeColor) {
            this.stepNumber = stepNumber;
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.type = stepType != null ? stepType : DocxDiagramHelper.StepType.PROCESS;
            this.conditionYes = conditionYes;
            this.conditionNo = conditionNo;
            this.rejectAction = rejectAction;
            this.hexThemeColor = sanitizeHex(hexThemeColor != null ? hexThemeColor : this.type.getDefaultHexColor());
        }

        public FlowStep withBranchNo(String branchNoAction) {
            FlowStep result = new FlowStep(this.stepNumber, this.title, this.description, this.type,
                    this.conditionYes != null ? this.conditionYes : "VALID",
                    this.conditionNo != null ? this.conditionNo : "INVALID",
                    branchNoAction, this.hexThemeColor);
            return result;
        }

        public int getStepNumber() { return this.stepNumber; }
        public String getTitle() { return this.title; }
        public String getDetail() { return this.description; }
        public String getDescription() { return this.description; }
        public DocxDiagramHelper.StepType getStepType() { return this.type; }
        public String getHexThemeColor() { return this.hexThemeColor; }
        public String getBranchNoAction() { return this.rejectAction; }
        public boolean hasBranchNo() { return this.rejectAction != null && !this.rejectAction.trim().isEmpty(); }

        private static int parseStepNum(String stepNum) {
            if (stepNum == null) return 1;
            try {
                int parsed = Integer.parseInt(stepNum.replaceAll("[^0-9]", ""));
                return parsed;
            } catch (Exception e) {
                return 1;
            }
        }

        private static String sanitizeHex(String hex) {
            if (hex == null || hex.trim().isEmpty()) {
                String defaultHex = HEX_BLUE;
                return defaultHex;
            }
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return clean;
        }

        private static String toHex(Color color) {
            if (color == null) {
                String defaultHex = HEX_BLUE;
                return defaultHex;
            }
            String result = String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
            return result;
        }
    }

    // =========================================================================================
    // PRIMARY PUBLIC API METHODS (MERMAID ENGINE WITH AUTOMATIC JAVA2D FALLBACK)
    // =========================================================================================

    /**
     * Master engine toggle:
     *  - true  : uses Mermaid engine via MermaidWordDocRenderer (modern vector diagrams)
     *  - false : uses legacy Java2D pixel-buffered rendering
     */
    public static boolean USE_MERMAID = true;

    public static void embedProgramFlowDiagram(XWPFDocument doc, String sectionTitle, List<FlowStep> steps) {
        renderProgramFlowchart(doc, sectionTitle, steps);
    }

    /**
     * Renders an AI-generated or custom Mermaid diagram directly into the Word document.
     * Enforces Florida Lottery theme settings and dimensions (515pt x 480pt) to fit cleanly on a single page.
     *
     * @param doc Target XWPF document
     * @param sectionTitle Name of procedure / function for the figure caption
     * @param mermaidSource Raw Mermaid flowchart DSL (with or without markdown fences)
     * @return true if rendering succeeded, false otherwise
     */
    public static boolean renderMermaidDiagram(XWPFDocument doc, String sectionTitle, String mermaidSource) {
        if (mermaidSource == null || mermaidSource.trim().isEmpty()) {
            boolean result = false;
            return result;
        }

        try {
            String themedSource = cleanAndThemeMermaidSource(mermaidSource);
            String caption = "Figure: Program Decision & Execution Flowchart — " + sectionTitle;

            // Adaptive height allocation: multi-stage subgraphs receive expanded vertical bounds (up to 580pt)
            int subgraphCount = 0;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\bsubgraph\\b").matcher(themedSource);
            while (m.find()) {
                subgraphCount++;
            }

            int maxDisplayHeight = (subgraphCount >= 3) ? 580 : (subgraphCount >= 2 ? 520 : 460);
            boolean success = MermaidWordDocRenderer.insertMermaidDiagram(doc, themedSource, 520, maxDisplayHeight, caption);
            boolean result = success;
            return result;
        } catch (Throwable t) {
            System.err.println("WARN: Custom Mermaid rendering failed for " + sectionTitle + " - " + t.getMessage());
            boolean result = false;
            return result;
        }
    }

    /**
     * Cleans up raw Mermaid syntax from AI models (stripping markdown fences) and injects
     * the enterprise theme configuration and class definitions if not already present.
     */
    public static String cleanAndThemeMermaidSource(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            String empty = "";
            return empty;
        }

        String clean = AiStudioProcResearcherService.cleanAndRepairMermaidSyntax(raw);
        if (clean.startsWith("```")) {
            clean = clean.replaceAll("^```[a-zA-Z]*\\R?", "").replaceAll("\\R?```$", "").trim();
        }

        // If the prompt didn't specify flowchart direction, ensure it starts with flowchart TD
        if (!clean.startsWith("%%{init:") && !clean.startsWith("flowchart") && !clean.startsWith("graph")) {
            clean = "flowchart TD\n" + clean;
        }

        // Eliminate all spaces/tabs/NBSP around Mermaid inline class assignments (e.g., ") ::: startEnd" -> "):::startEnd")
        clean = clean.replaceAll("[ \\t\\u00A0]*:::[ \\t\\u00A0]*", ":::");

        // Fix run-on statements where a closing shape is immediately followed by a new node definition without newline
        clean = clean.replaceAll("([)\\]\\}])[ \\t\\u00A0]*([a-zA-Z0-9_]+[ \\t\\u00A0]*[\\(\\[\\{])", "$1\n    $2");

        // Fix run-on inline 'end' statements:
        // 1. Separate 'end' from preceding closing node bracket/parenthesis: node(["Finish"]) end -> node(["Finish"])\n    end
        clean = clean.replaceAll("([)\\]\\}])[ \\t\\u00A0]+(?i)end\\b", "$1\n    end");

        // 2. Separate 'end' from following statement on the same line: end success -.-> -> end\n    success -.->
        clean = clean.replaceAll("(?i)\\bend[ \\t\\u00A0]+([a-zA-Z0-9_#$]+|%%|//|#)", "end\n    $1");

        // Fix malformed or unclosed pill shapes ([ ... ] missing the trailing ')'
        clean = clean.replaceAll("\\(\\[\\s*([^\\r\\n\\[\\]]*?)\\](?!\\))", "([$1])");

        // Replace reserved keywords used as node IDs (e.g., node named "end" -> "endNode") to prevent HTTP 400
        clean = sanitizeReservedNodeKeywords(clean);

        // Clean and quote all unquoted node labels to prevent HTTP 400 Mermaid syntax errors on special chars
        clean = sanitizeAndQuoteMermaidLabels(clean);

        // Lift any start terminal node declared inside Stage 1 out to top level so it never overlaps the stage title
        clean = liftStartNodeOutsideSubgraph(clean);

        // Strip any existing/legacy %%{init:...}%% blocks to guarantee standard enterprise theme & geometry
        if (clean.contains("%%{init:")) {
            clean = clean.replaceAll("%%\\{init:[\\s\\S]*?\\}%%\\R?", "").trim();
        }

        // Inject Florida Lottery high-contrast theme & subgraph spacing configuration
        StringBuilder themed = new StringBuilder();
        themed.append("%%{init: {\n");
        themed.append("  'theme': 'base',\n");
        themed.append("  'securityLevel': 'loose',\n");
        themed.append("  'themeVariables': {\n");
        themed.append("    'background': '#FFFFFF',\n");
        themed.append("    'primaryColor': '#002B49',\n");
        themed.append("    'primaryTextColor': '#FFFFFF',\n");
        themed.append("    'primaryBorderColor': '#005A9C',\n");
        themed.append("    'lineColor': '#002B49',\n");
        themed.append("    'textColor': '#002B49',\n");
        themed.append("    'fontSize': '15px',\n");
        themed.append("    'fontFamily': 'Segoe UI, Arial, sans-serif',\n");
        themed.append("    'edgeLabelBackground': '#FFFFFF',\n");
        themed.append("    'clusterBkg': '#F0F6FA',\n");
        themed.append("    'clusterBorder': '#005A9C',\n");
        themed.append("    'clusterTextColor': '#002B49',\n");
        themed.append("    'titleColor': '#002B49'\n");
        themed.append("  },\n");
        themed.append("  'themeCSS': '");
        themed.append(".cluster text, .cluster .nodeLabel p, .cluster .nodeLabel span, .cluster span, g.cluster text { fill: #002B49 !important; color: #002B49 !important; font-weight: 900 !important; font-size: 22px !important; font-family: Segoe UI, Arial, sans-serif !important; letter-spacing: 0.5px !important; text-shadow: 0 1px 1px #FFFFFF, 0 0 4px #FFFFFF !important; } ");
        themed.append(".cluster rect { stroke: #005A9C !important; stroke-width: 2.5px !important; rx: 8px !important; ry: 8px !important; fill: #F0F6FA !important; } ");
        themed.append(".edgeLabel text, .edgeLabel span, .edgeLabel p { fill: #002B49 !important; color: #002B49 !important; font-weight: bold !important; font-size: 13px !important; } ");
        themed.append(".edgeLabel rect { fill: #FFFFFF !important; opacity: 0.95 !important; stroke: #CBD5E1 !important; stroke-width: 1px !important; rx: 4px !important; ry: 4px !important; }");
        themed.append("',\n");
        themed.append("  'flowchart': {\n");
        themed.append("    'subGraphTitleMargin': {\n");
        themed.append("      'top': 16,\n");
        themed.append("      'bottom': 42\n");
        themed.append("    },\n");
        themed.append("    'nodeSpacing': 38,\n");
        themed.append("    'rankSpacing': 48,\n");
        themed.append("    'padding': 28,\n");
        themed.append("    'useMaxWidth': true\n");
        themed.append("  }\n");
        themed.append("}}%%\n");
        themed.append(clean);
        clean = themed.toString();

        // Ensure any unclosed subgraphs are safely closed before appending class definitions and bindings
        clean = closeAnyUnclosedSubgraphs(clean);

        // If classDef is missing, append standard palette
        if (!clean.contains("classDef startEnd")) {
            StringBuilder withStyles = new StringBuilder(clean);
            withStyles.append("\n");
            withStyles.append("    classDef startEnd fill:#166534,stroke:#14532D,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
            withStyles.append("    classDef process fill:#002B49,stroke:#005A9C,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
            withStyles.append("    classDef decision fill:#D97706,stroke:#B45309,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
            withStyles.append("    classDef db fill:#581845,stroke:#3B102E,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
            withStyles.append("    classDef reject fill:#DC2626,stroke:#991B1B,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:14px;\n");
            withStyles.append("    classDef error fill:#DC2626,stroke:#991B1B,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
            withStyles.append("    classDef io fill:#0284C7,stroke:#0369A1,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
            clean = withStyles.toString();
        }

        // Auto-detect and bind all unstyled nodes to the Florida Lottery enterprise palette
        clean = autoBindMermaidNodeClasses(clean);

        String result = clean;
        return result;
    }

    /**
     * Inspects Mermaid flowchart syntax and lifts any start terminal node (e.g., startNode(["Start: ..."]))
     * that was defined inside the first subgraph out to the top level (above the subgraph declaration)
     * so that it renders cleanly above the stage container box without overlapping the stage title.
     */
    public static String liftStartNodeOutsideSubgraph(String mermaid) {
        if (mermaid == null || !mermaid.contains("subgraph")) {
            return mermaid;
        }

        String[] lines = mermaid.split("\\R");
        List<String> resultLines = new ArrayList<>();
        List<String> startNodeLines = new ArrayList<>();
        boolean insideSubgraph = false;
        boolean inFirstSubgraph = false;
        int subgraphCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("subgraph")) {
                subgraphCount++;
                insideSubgraph = true;
                if (subgraphCount == 1) {
                    inFirstSubgraph = true;
                }
                resultLines.add(line);
                continue;
            }

            if (insideSubgraph && (trimmed.equalsIgnoreCase("end") || (trimmed.matches("(?i)^end(\\b.*|;|$)")
                    && !trimmed.contains("-->") && !trimmed.contains("==>") && !trimmed.contains("-.->")
                    && !trimmed.contains("[") && !trimmed.contains("(") && !trimmed.contains("{")))) {
                insideSubgraph = false;
                inFirstSubgraph = false;
                resultLines.add("    end");
                continue;
            }

            // If inside the first subgraph, check if this line is a start node definition
            boolean isStartLine = inFirstSubgraph && (trimmed.contains("([\"Start:") || trimmed.contains("([\"START:")
                    || trimmed.contains("([Start:") || trimmed.contains("([START:")
                    || trimmed.matches(".*\\(\\[[\"']?(?i)start[:\\s].*")
                    || trimmed.matches("^(start|startNode|startPill)\\b.*"));
            if (isStartLine) {
                if (!trimmed.contains("-->") && !trimmed.contains("==>") && !trimmed.contains("-.->")) {
                    startNodeLines.add(line);
                    continue; // Lift this line out
                } else {
                    // Chained definition like: startNode(["Start: ..."]):::startEnd --> nextStep
                    int arrowIdx = trimmed.indexOf("-->");
                    if (arrowIdx == -1) arrowIdx = trimmed.indexOf("==>");
                    if (arrowIdx == -1) arrowIdx = trimmed.indexOf("-.->");
                    if (arrowIdx != -1) {
                        String defPart = trimmed.substring(0, arrowIdx).trim();
                        String arrowPart = trimmed.substring(arrowIdx).trim();
                        startNodeLines.add(defPart);
                        String nodeId = defPart.replaceAll("[\\[\\(\\{].*", "").trim();
                        resultLines.add("        " + nodeId + " " + arrowPart);
                        continue;
                    }
                }
            }

            resultLines.add(line);
        }

        if (startNodeLines.isEmpty()) {
            return mermaid;
        }

        // Reconstruct with startNodeLines placed right before the first subgraph declaration
        StringBuilder sb = new StringBuilder();
        boolean inserted = false;

        for (String l : resultLines) {
            String tr = l.trim();
            if (!inserted && tr.startsWith("subgraph")) {
                for (String sn : startNodeLines) {
                    sb.append("    ").append(sn.trim()).append("\n");
                }
                sb.append("\n");
                inserted = true;
            }
            sb.append(l).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Inspects Mermaid diagram source, normalizes any non-standard 'end' statements
     * (e.g. 'end Stage 2', 'end [Stage 1]', 'end;'), and guarantees that every open subgraph
     * is cleanly closed with 'end' before any classDef or class statements are appended.
     */
    public static String closeAnyUnclosedSubgraphs(String mermaid) {
        if (mermaid == null || !mermaid.contains("subgraph")) {
            return mermaid;
        }
        String[] lines = mermaid.split("\\R");
        int openSubgraphs = 0;
        List<String> normalizedLines = new ArrayList<>();
        for (String l : lines) {
            String tr = l.trim();
            if (tr.matches("(?i)^subgraph(?:\\s+.*|$)") && !tr.matches("(?i)^subgraphNode\\b.*")
                    && !tr.contains("-->") && !tr.contains("==>") && !tr.contains("-.->")) {
                openSubgraphs++;
                normalizedLines.add(l);
            } else if (tr.equalsIgnoreCase("end") || tr.equalsIgnoreCase("endsubgraph")
                    || (tr.matches("(?i)^end\\s*(;|%%.*|//.*|--.*|#.*)?$"))
                    || (openSubgraphs > 0 && tr.matches("(?i)^end(?:\\s*[\\[\\(:/\\-%#;].*|\\s+[a-zA-Z0-9_\\[\\(\\)].*|;.*)?$")
                    && !tr.contains("-->") && !tr.contains("==>") && !tr.contains("-.->")
                    && !tr.matches("(?i)^end\\s*\\(\\[.*"))) {
                if (openSubgraphs > 0) openSubgraphs--;
                normalizedLines.add("    end");
            } else {
                normalizedLines.add(l);
            }
        }
        StringBuilder sb = new StringBuilder(String.join("\n", normalizedLines));
        while (openSubgraphs > 0) {
            sb.append("\n    end");
            openSubgraphs--;
        }
        String result = sb.toString();
        return result;
    }

    /**
     * Sanitizes raw Mermaid diagram source, ensuring that all node definitions with inner
     * parentheses, assignment operators, commas, or special characters are safely wrapped
     * in double quotes so that the Mermaid parser does not reject them with HTTP 400.
     */
    public static String sanitizeAndQuoteMermaidLabels(String mermaid) {
        if (mermaid == null || mermaid.trim().isEmpty()) {
            return mermaid;
        }

        String[] lines = mermaid.split("\\R");
        List<String> cleanedLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("%%") || trimmed.startsWith("classDef")
                    || trimmed.matches("(?i)^class\\s+[a-zA-Z0-9_,\\s]+\\s+[a-zA-Z0-9_]+;?")
                    || trimmed.matches("(?i)^style\\s+[a-zA-Z0-9_]+\\s+[a-zA-Z0-9_#:,]+;?")
                    || (trimmed.matches("(?i)^subgraph\\b.*") && !trimmed.matches("(?i)^subgraphNode\\b.*"))
                    || trimmed.equalsIgnoreCase("end")
                    || trimmed.matches("(?i)^(flowchart|graph)\\s+[a-zA-Z]+\\b.*")) {
                cleanedLines.add(line);
                continue;
            }

            String processed = line.replaceAll("[ \\t\\u00A0]*:::[ \\t\\u00A0]*", ":::");

            // Normalize HTML tags inside labels (<br> to <br/> and strip non-compliant styling tags)
            if (processed.contains("<") && processed.contains(">")) {
                processed = processed.replaceAll("(?i)<br\\s*/?>", "<br/>");
                processed = processed.replaceAll("(?i)</?(?:font|b|i|u|span|div|p|strong|em)(?:\\s+[^>]*)?>", "");
            }

            // Clean malformed double/pipe edges
            processed = processed.replaceAll("(-->|-.->|==>)\\s*\\|([^|\\r\\n]+)\\|\\s*(-->|-.->|==>)", "$1|$2| ")
                    .replaceAll("-->\\|-->", "-->")
                    .replaceAll("-->\\|\\|-->", "-->")
                    .replaceAll("-->\\|\\s*\\|-->", "-->")
                    .replaceAll("-->-->+", "-->")
                    .replaceAll("-\\.- >-\\.- >+", "-.->")
                    .replaceAll("==>==>+", "==>");

            // 1. Cylinders: id[( ... )]
            Pattern cylPat = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\[\\(\\s*([^\\n\\r]*?)\\s*\\)\\]");
            Matcher cylMat = cylPat.matcher(processed);
            StringBuffer sbCyl = new StringBuffer();
            while (cylMat.find()) {
                String id = cylMat.group(1);
                String text = cylMat.group(2).trim();
                text = translateToNaturalEnglish(text.replaceAll("^\"|\"$", "").trim());
                cylMat.appendReplacement(sbCyl, Matcher.quoteReplacement(id + "[(\"" + text.replace("\"", "'") + "\")]"));
            }
            cylMat.appendTail(sbCyl);
            processed = sbCyl.toString();

            // 2. Pills: id([ ... ])
            Pattern pillPat = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\(\\[\\s*([^\\n\\r]*?)\\s*\\]\\)");
            Matcher pillMat = pillPat.matcher(processed);
            StringBuffer sbPill = new StringBuffer();
            while (pillMat.find()) {
                String id = pillMat.group(1);
                String text = pillMat.group(2).trim();
                text = translateToNaturalEnglish(text.replaceAll("^\"|\"$", "").trim());
                pillMat.appendReplacement(sbPill, Matcher.quoteReplacement(id + "([\"" + text.replace("\"", "'") + "\"])"));
            }
            pillMat.appendTail(sbPill);
            processed = sbPill.toString();

            // 3. Parallelograms: id[/ ... /] or id[\ ... \]
            Pattern ioPat = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\[([/\\\\])\\s*([^\\n\\r]*?)\\s*([/\\\\])\\]");
            Matcher ioMat = ioPat.matcher(processed);
            StringBuffer sbIo = new StringBuffer();
            while (ioMat.find()) {
                String id = ioMat.group(1);
                String s1 = ioMat.group(2);
                String text = ioMat.group(3).trim();
                String s2 = ioMat.group(4);
                text = translateToNaturalEnglish(text.replaceAll("^\"|\"$", "").trim());
                ioMat.appendReplacement(sbIo, Matcher.quoteReplacement(id + "[" + s1 + "\"" + text.replace("\"", "'") + "\"" + s2 + "]"));
            }
            ioMat.appendTail(sbIo);
            processed = sbIo.toString();

            // 4. Diamonds: id{ ... } or id{{ ... }}
            Pattern decPat = Pattern.compile("([a-zA-Z0-9_]+)\\s*(\\{+)\\s*([^{}\\n\\r]*?)\\s*(\\}+)");
            Matcher decMat = decPat.matcher(processed);
            StringBuffer sbDec = new StringBuffer();
            while (decMat.find()) {
                String id = decMat.group(1);
                String b1 = decMat.group(2);
                String text = decMat.group(3).trim();
                String b2 = decMat.group(4);
                text = translateToNaturalEnglish(text.replaceAll("^\"|\"$", "").trim());
                decMat.appendReplacement(sbDec, Matcher.quoteReplacement(id + b1 + "\"" + text.replace("\"", "'") + "\"" + b2));
            }
            decMat.appendTail(sbDec);
            processed = sbDec.toString();

            // Pre-pass 1: auto-repair labels that contain premature closing quotes before file extensions
            // e.g., build["Content_Types"].xml"] -> build["Content_Types.xml"]
            // Handles square brackets, pills, and cylinders
            if (processed.matches(".*\\b\\w+\\s*\\[\\s*\"[^\"]*?\"\\][^\\r\\n\\[\\]]*?\"?\\s*\\].*")) {
                processed = processed.replaceAll("(\\b\\w+\\s*\\[\\s*\"[^\"]*?)\"\\]([^\\r\\n\\[\\]]*?)\"?\\s*\\]", "$1$2\"]");
            }
            if (processed.matches(".*\\b\\w+\\s*\\(\\s*\\[\\s*\"[^\"]*?\"\\][^\\r\\n\\[\\]]*?\"?\\s*\\]\\).*")) {
                processed = processed.replaceAll("(\\b\\w+\\s*\\(\\s*\\[\\s*\"[^\"]*?)\"\\]([^\\r\\n\\[\\]]*?)\"?\\s*\\]\\)", "$1$2\"])");
            }
            if (processed.matches(".*\\b\\w+\\s*\\[\\s*\\(\\s*\"[^\"]*?\"\\][^\\r\\n\\[\\]]*?\"?\\s*\\)\\]\\s*.*")) {
                processed = processed.replaceAll("(\\b\\w+\\s*\\[\\s*\\(\\s*\"[^\"]*?)\"\\]([^\\r\\n\\[\\]]*?)\"?\\s*\\)\\]", "$1$2\")]");
            }

            // Pre-pass 2: auto-repair labels that contain unescaped internal square brackets
            // e.g., meta["Build [Content_Types].xml"] or query["SELECT [COL1], [COL2] FROM [TABLE]"]
            if (processed.contains("[") && processed.contains("]")) {
                String prev;
                do {
                    prev = processed;
                    processed = processed.replaceAll("(\\b\\w+\\s*\\[[^\\r\\n\\]]*?)\\[([^\\[\\]\\r\\n]+)\\]", "$1($2)");
                } while (!processed.equals(prev));
            }

            // 5. Standard Process / Action Boxes: id[ ... ]
            Pattern boxPat = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\[(?![(\\[/\\\\])\\s*([^\\n\\r\\[\\]]*?)\\s*\\](?![)\\]])");
            Matcher boxMat = boxPat.matcher(processed);
            StringBuffer sbBox = new StringBuffer();
            while (boxMat.find()) {
                String id = boxMat.group(1);
                String text = boxMat.group(2).trim();
                text = translateToNaturalEnglish(text.replaceAll("^\"|\"$", "").trim());
                boxMat.appendReplacement(sbBox, Matcher.quoteReplacement(id + "[\"" + text.replace("\"", "'") + "\"]"));
            }
            boxMat.appendTail(sbBox);
            processed = sbBox.toString();

            cleanedLines.add(processed);
        }

        String result = String.join("\n", cleanedLines);
        return result;
    }

    /**
     * Sanitizes reserved Mermaid keywords used as node identifiers.
     * In particular, 'end', 'subgraph', 'style', 'class', 'click', 'flowchart', 'graph'
     * are reserved syntax tokens. If an AI or user defines a node with a reserved keyword ID
     * (e.g., 'end(["END"])' or 'subgraph["Execute"]' or 'start --> end'), Mermaid throws syntax errors.
     * This method safely renames colliding node identifiers to '<keyword>Node' while preserving structural statements.
     */
    public static String sanitizeReservedNodeKeywords(String mermaid) {
        if (mermaid == null || mermaid.trim().isEmpty()) {
            return mermaid;
        }

        String[] lines = mermaid.split("\\R");
        List<String> cleanedLines = new ArrayList<>();
        String[] keywords = new String[]{"end", "subgraph", "style", "class", "click", "flowchart", "graph"};

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                cleanedLines.add(line);
                continue;
            }

            // Skip comments and global config
            if (trimmed.startsWith("%%") || trimmed.startsWith("classDef")) {
                cleanedLines.add(line);
                continue;
            }

            // Preserve structural subgraph opening: subgraph ID or subgraph ID [Title]
            if (trimmed.matches("(?i)^subgraph\\s+[a-zA-Z0-9_-]+(\\s*\\[.*|\\s*:\\s*.*)?$")
                    && !trimmed.contains("-->") && !trimmed.contains("==>") && !trimmed.contains("-.->")) {
                cleanedLines.add(line);
                continue;
            }

            // Preserve structural class statement: class A,B className;
            if (trimmed.matches("(?i)^class\\s+[a-zA-Z0-9_,\\s]+\\s+[a-zA-Z0-9_]+;?")) {
                cleanedLines.add(line);
                continue;
            }

            // Preserve structural style statement: style id fill:...
            if (trimmed.matches("(?i)^style\\s+[a-zA-Z0-9_]+\\s+[a-zA-Z0-9_#:,]+;?")) {
                cleanedLines.add(line);
                continue;
            }

            // Preserve structural click statement: click id ...
            if (trimmed.matches("(?i)^click\\s+[a-zA-Z0-9_]+\\s+.*")) {
                cleanedLines.add(line);
                continue;
            }

            // Preserve graph header
            if (trimmed.matches("(?i)^(flowchart|graph)\\s+(TD|TB|LR|RL|BT)\\b.*")) {
                cleanedLines.add(line);
                continue;
            }

            // Preserve clean standalone end
            if (trimmed.equals("end") || trimmed.matches("(?i)^end\\s*(;|%%.*|//.*)?$")) {
                cleanedLines.add("    end");
                continue;
            }

            String processed = line;
            for (String kw : keywords) {
                String repl = kw + "Node";
                // 1. Arrow targets: (-->|...| kw or --> kw followed by shape or boundary)
                processed = processed.replaceAll("(-->|-.->|==>)\\s*(\\|[^|\\n\\r]*\\|)?\\s*\\b" + kw + "\\b(\\s*[\\(\\[\\{])?", "$1 $2 " + repl + "$3");
                // 2. Arrow sources: (kw --> or kw -.->)
                processed = processed.replaceAll("(^|[\\s;])\\b" + kw + "\\b\\s*(-->|-.->|==>)", "$1" + repl + " $2");
                // 3. Node definitions starting a statement: (kw[...] or kw(...) or kw{...})
                processed = processed.replaceAll("(^|[\\s;])\\b" + kw + "\\b(\\s*[\\(\\[\\{])", "$1" + repl + "$2");
                // 4. Class statement lists: (class ... kw ...)
                if (!"class".equalsIgnoreCase(kw)) {
                    processed = processed.replaceAll("(\\bclass\\s+[^;\\n\\r]*?)\\b" + kw + "\\b", "$1" + repl);
                }
            }

            cleanedLines.add(processed);
        }

        String result = String.join("\n", cleanedLines);
        return result;
    }

    /**
     * Inspects raw or AI-generated Mermaid flowchart DSL, dynamically analyzes node shapes,
     * and automatically binds unstyled nodes to the Florida Lottery enterprise palette:
     *  - Terminal pills ([...]) -> startEnd (Forest Green)
     *  - Database cylinders [(...)] -> db (Deep Plum)
     *  - I/O parallelograms [/ ... /] -> io (Florida Lottery Blue)
     *  - Decision diamonds {...} / {{...}} -> decision (Golden Amber)
     *  - Error / Abort / Rollback / Exception / Reject nodes -> error / reject (Crimson Red)
     *  - Process boxes [...] / (...) -> process (Deep Navy)
     */
    public static String autoBindMermaidNodeClasses(String mermaid) {
        if (mermaid == null || mermaid.trim().isEmpty()) {
            return mermaid;
        }

        Set<String> reservedKeywords = new HashSet<>(Arrays.asList(
                "flowchart", "graph", "subgraph", "end", "classdef", "class", "style",
                "click", "linkstyle", "td", "lr", "tb", "rl", "bt"
        ));

        // 1. Detect any nodes that already have explicit inline or statement class bindings
        Set<String> boundNodes = new HashSet<>();
        Pattern inlineClassPattern = Pattern.compile("([a-zA-Z0-9_]+)(?:\\[[^\\]]*\\]|\\([^\\)]*\\)|\\{[^\\}]*\\})?\\s*:::+\\s*([a-zA-Z0-9_]+)");
        Matcher inlineMatcher = inlineClassPattern.matcher(mermaid);
        while (inlineMatcher.find()) {
            boundNodes.add(inlineMatcher.group(1));
        }

        Pattern classStmtPattern = Pattern.compile("class\\s+([a-zA-Z0-9_,\\s]+)\\s+([a-zA-Z0-9_]+)");
        Matcher classStmtMatcher = classStmtPattern.matcher(mermaid);
        while (classStmtMatcher.find()) {
            String[] ids = classStmtMatcher.group(1).split(",");
            for (String id : ids) {
                boundNodes.add(id.trim());
            }
        }

        // 2. Prepare groups for unstyled nodes
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        groups.put("startEnd", new LinkedHashSet<>());
        groups.put("io", new LinkedHashSet<>());
        groups.put("decision", new LinkedHashSet<>());
        groups.put("db", new LinkedHashSet<>());
        groups.put("error", new LinkedHashSet<>());
        groups.put("process", new LinkedHashSet<>());

        String[] lines = mermaid.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("%%") || trimmed.startsWith("classDef")
                    || trimmed.startsWith("class ") || trimmed.startsWith("style ")
                    || trimmed.startsWith("subgraph") || trimmed.equals("end")) {
                continue;
            }

            // A. Terminal Pills: id([ ... ])
            Pattern pillPattern = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\(\\[([^\\n]*?)\\]\\)");
            Matcher pillMatcher = pillPattern.matcher(trimmed);
            while (pillMatcher.find()) {
                String id = pillMatcher.group(1);
                String label = pillMatcher.group(2).toLowerCase();
                if (!reservedKeywords.contains(id.toLowerCase()) && !boundNodes.contains(id)) {
                    if (id.toLowerCase().contains("err") || label.contains("error") || label.contains("abort") || label.contains("fail") || label.contains("rollback")) {
                        groups.get("error").add(id);
                    } else {
                        groups.get("startEnd").add(id);
                    }
                    boundNodes.add(id);
                }
            }

            // B. Database Cylinders: id[( ... )]
            Pattern cylPattern = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\[\\(([^\\n]*?)\\)\\]");
            Matcher cylMatcher = cylPattern.matcher(trimmed);
            while (cylMatcher.find()) {
                String id = cylMatcher.group(1);
                if (!reservedKeywords.contains(id.toLowerCase()) && !boundNodes.contains(id)) {
                    groups.get("db").add(id);
                    boundNodes.add(id);
                }
            }

            // C. I/O Parallelograms: id[/ ... /] or id[\ ... \]
            Pattern ioPattern = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\[[\\\\/]([^\\n]*?)[\\\\/]\\]");
            Matcher ioMatcher = ioPattern.matcher(trimmed);
            while (ioMatcher.find()) {
                String id = ioMatcher.group(1);
                if (!reservedKeywords.contains(id.toLowerCase()) && !boundNodes.contains(id)) {
                    groups.get("io").add(id);
                    boundNodes.add(id);
                }
            }

            // D. Decision Diamonds: id{ ... } or id{{ ... }}
            Pattern decPattern = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\{+([^\\n]*?)\\}+");
            Matcher decMatcher = decPattern.matcher(trimmed);
            while (decMatcher.find()) {
                String id = decMatcher.group(1);
                if (!reservedKeywords.contains(id.toLowerCase()) && !boundNodes.contains(id)) {
                    groups.get("decision").add(id);
                    boundNodes.add(id);
                }
            }

            // E. Standard Process / Action Boxes: id[ ... ]
            Pattern procPattern = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\[(?![(/\\\\])([^\\n]*?)\\](?![)\\]])");
            Matcher procMatcher = procPattern.matcher(trimmed);
            while (procMatcher.find()) {
                String id = procMatcher.group(1);
                String label = procMatcher.group(2).toLowerCase();
                if (!reservedKeywords.contains(id.toLowerCase()) && !boundNodes.contains(id)) {
                    if (id.toLowerCase().contains("err") || label.contains("error") || label.contains("abort") || label.contains("rollback")
                            || label.contains("reject") || label.contains("fail") || label.contains("exception") || label.contains("sqlerrm")) {
                        groups.get("error").add(id);
                    } else {
                        groups.get("process").add(id);
                    }
                    boundNodes.add(id);
                }
            }
        }

        // Ensure any unclosed subgraphs are safely closed before binding class styles at root diagram scope
        int openSubgraphs = 0;
        for (String l : lines) {
            String tr = l.trim();
            if (tr.matches("(?i)^subgraph(?:\\s+.*|$)") && !tr.matches("(?i)^subgraphNode\\b.*")
                    && !tr.contains("-->") && !tr.contains("==>") && !tr.contains("-.->")) {
                openSubgraphs++;
            } else if (tr.equalsIgnoreCase("end") || tr.equalsIgnoreCase("endsubgraph")
                    || (tr.matches("(?i)^end\\s*(;|%%.*|//.*|--.*|#.*)?$"))
                    || (openSubgraphs > 0 && tr.matches("(?i)^end(?:\\s*[\\[\\(:/\\-%#;].*|\\s+[a-zA-Z0-9_\\[\\(\\)].*|;.*)?$")
                    && !tr.contains("-->") && !tr.contains("==>") && !tr.contains("-.->")
                    && !tr.matches("(?i)^end\\s*\\(\\[.*"))) {
                if (openSubgraphs > 0) openSubgraphs--;
            }
        }

        StringBuilder sb = new StringBuilder(mermaid.trim());
        while (openSubgraphs > 0) {
            sb.append("\n    end");
            openSubgraphs--;
        }
        sb.append("\n\n");
        boolean addedAny = false;
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sb.append("    class ").append(String.join(",", entry.getValue())).append(" ").append(entry.getKey()).append(";\n");
                addedAny = true;
            }
        }

        String result = addedAny ? sb.toString() : mermaid;
        return result;
    }

    /**
     * Primary rendering entry point. Automatically delegates to the Mermaid engine,
     * with transparent automatic fallback to legacy Java2D if Mermaid fails or is disabled.
     */
    public static void renderProgramFlowchart(XWPFDocument doc, String sectionTitle, List<FlowStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        int totalSteps = steps.size();
        if (totalSteps <= MAX_STEPS_PER_PAGE) {
            if (USE_MERMAID) {
                try {
                    String mermaidCode = buildMermaidFlowchart(sectionTitle, steps);
                    String caption = "Figure: Program Decision & Execution Flowchart — " + sectionTitle;
                    boolean success = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidCode, 520, 460, caption);
                    if (success) {
                        return;
                    }
                    System.err.println("INFO: Mermaid rendering bypassed; engaging Java2D fallback for: " + sectionTitle);
                } catch (Throwable t) {
                    System.err.println("WARN: Mermaid flowchart generation failed; falling back to Java2D: " + t.getMessage());
                }
            }
            // Guaranteed fallback to Java2D single slice
            renderDiagramSlice(doc, sectionTitle, steps, 1, 1);
        } else {
            // Smart multi-part slicing when flowchart exceeds 7-9 levels (avoids microscopic node text)
            int totalParts = (int) Math.ceil((double) totalSteps / (MAX_STEPS_PER_PAGE - 1));
            int stepsPerPart = (int) Math.ceil((double) totalSteps / totalParts);

            int startIdx = 0;
            for (int part = 1; part <= totalParts; part++) {
                int endIdx = Math.min(startIdx + stepsPerPart, totalSteps);
                List<FlowStep> slice = new ArrayList<>();

                if (part > 1) {
                    slice.add(new FlowStep(0, "CONTINUED FROM PART " + (part - 1), "Continuation from preceding execution segment", DocxDiagramHelper.StepType.START, HEX_GREEN));
                }

                slice.addAll(steps.subList(startIdx, endIdx));

                if (part < totalParts) {
                    slice.add(new FlowStep(999, "CONTINUES TO PART " + (part + 1), "Off-page connector to next execution segment", DocxDiagramHelper.StepType.PREDEFINED_PROCESS, HEX_NAVY));
                }

                boolean sliceRendered = false;
                if (USE_MERMAID) {
                    try {
                        if (part > 1) {
                            XWPFParagraph pageBreakPara = doc.createParagraph();
                            pageBreakPara.setPageBreak(true);
                            pageBreakPara.setSpacingBefore(0);
                            pageBreakPara.setSpacingAfter(40);
                            safeSetKeepNext(pageBreakPara, true);
                        }
                        String mermaidCode = buildMermaidFlowchart(sectionTitle + " (Part " + part + " of " + totalParts + ")", slice);
                        String caption = String.format("Figure: Program Decision & Execution Flowchart — Part %d of %d: %s", part, totalParts, sectionTitle);
                        sliceRendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidCode, 520, 440, caption);
                    } catch (Throwable t) {
                        sliceRendered = false;
                    }
                }

                if (!sliceRendered) {
                    renderDiagramSlice(doc, sectionTitle, slice, part, totalParts);
                }

                startIdx = endIdx;
            }
        }
    }

    /**
     * Translates a list of FlowStep objects into a standard, Florida Lottery-styled Mermaid flowchart definition.
     */
    public static String buildMermaidFlowchart(String routineName, List<FlowStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: {\n");
        sb.append("  'theme': 'base',\n");
        sb.append("  'securityLevel': 'loose',\n");
        sb.append("  'themeVariables': {\n");
        sb.append("    'background': '#FFFFFF',\n");
        sb.append("    'primaryColor': '#002B49',\n");
        sb.append("    'primaryTextColor': '#FFFFFF',\n");
        sb.append("    'primaryBorderColor': '#005A9C',\n");
        sb.append("    'lineColor': '#002B49',\n");
        sb.append("    'textColor': '#002B49',\n");
        sb.append("    'fontSize': '15px',\n");
        sb.append("    'fontFamily': 'Segoe UI, Arial, sans-serif',\n");
        sb.append("    'edgeLabelBackground': '#FFFFFF',\n");
        sb.append("    'clusterBkg': '#F0F6FA',\n");
        sb.append("    'clusterBorder': '#005A9C',\n");
        sb.append("    'clusterTextColor': '#002B49',\n");
        sb.append("    'titleColor': '#002B49'\n");
        sb.append("  },\n");
        sb.append("  'themeCSS': '");
        sb.append(".cluster text, .cluster .nodeLabel p, .cluster .nodeLabel span, .cluster span, g.cluster text { fill: #002B49 !important; color: #002B49 !important; font-weight: 900 !important; font-size: 22px !important; font-family: Segoe UI, Arial, sans-serif !important; letter-spacing: 0.5px !important; text-shadow: 0 1px 1px #FFFFFF, 0 0 4px #FFFFFF !important; } ");
        sb.append(".cluster rect { stroke: #005A9C !important; stroke-width: 2.5px !important; rx: 8px !important; ry: 8px !important; fill: #F0F6FA !important; } ");
        sb.append(".edgeLabel text, .edgeLabel span, .edgeLabel p { fill: #002B49 !important; color: #002B49 !important; font-weight: bold !important; font-size: 13px !important; } ");
        sb.append(".edgeLabel rect { fill: #FFFFFF !important; opacity: 0.95 !important; stroke: #CBD5E1 !important; stroke-width: 1px !important; rx: 4px !important; ry: 4px !important; }");
        sb.append("',\n");
        sb.append("  'flowchart': {\n");
        sb.append("    'subGraphTitleMargin': {\n");
        sb.append("      'top': 16,\n");
        sb.append("      'bottom': 42\n");
        sb.append("    },\n");
        sb.append("    'nodeSpacing': 38,\n");
        sb.append("    'rankSpacing': 48,\n");
        sb.append("    'padding': 28,\n");
        sb.append("    'useMaxWidth': true\n");
        sb.append("  }\n");
        sb.append("}}%%\n");
        sb.append("flowchart TD\n");
        sb.append("    classDef startEnd fill:#166534,stroke:#14532D,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
        sb.append("    classDef process fill:#002B49,stroke:#005A9C,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
        sb.append("    classDef decision fill:#D97706,stroke:#B45309,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
        sb.append("    classDef db fill:#581845,stroke:#3B102E,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
        sb.append("    classDef reject fill:#DC2626,stroke:#991B1B,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:14px;\n");
        sb.append("    classDef error fill:#DC2626,stroke:#991B1B,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n");
        sb.append("    classDef io fill:#0284C7,stroke:#0369A1,stroke-width:2.5px,color:#fff,font-weight:bold,font-size:15px;\n\n");

        for (int i = 0; i < steps.size(); i++) {
            FlowStep s = steps.get(i);
            String nodeId = "S" + i;
            String labelText = formatNodeLabel(s);

            String cssClass = "process";
            String nodeSyntax;

            if (s.type == StepType.START || s.type == StepType.TERMINATOR || s.type == StepType.END) {
                nodeSyntax = nodeId + "([\"" + labelText + "\"])";
                cssClass = "startEnd";
            } else if (s.type == StepType.DECISION) {
                nodeSyntax = nodeId + "{\"" + labelText + "\"}";
                cssClass = "decision";
            } else if (s.type == StepType.DATABASE || s.type == StepType.DB_OPERATION || s.type == StepType.TABLE) {
                nodeSyntax = nodeId + "[(\"" + labelText + "\")]";
                cssClass = "db";
            } else if (s.type == StepType.IO || s.type == StepType.IO_INPUT || s.type == StepType.IO_OUTPUT || s.type == StepType.INPUT_OUTPUT) {
                nodeSyntax = nodeId + "[/\"" + labelText + "\"/]";
                cssClass = "io";
            } else {
                nodeSyntax = nodeId + "[\"" + labelText + "\"]";
                cssClass = "process";
            }

            sb.append("    ").append(nodeSyntax).append(":::").append(cssClass).append("\n");

            // If this is a decision step with a rejection branch
            if (s.hasBranchNo()) {
                String rejectId = "R" + i;
                String rejectTitle = sanitizeForMermaid(s.rejectAction);
                String wrappedReject = wrapTextHtml(rejectTitle, 24);
                sb.append("    ").append(rejectId).append("[/\"<b>").append(wrappedReject).append("</b>\"/]:::reject\n");
            }
        }

        sb.append("\n");

        // Connect nodes sequentially with high-contrast text labels (no awkward colored box background)
        for (int i = 0; i < steps.size() - 1; i++) {
            FlowStep s = steps.get(i);
            String currId = "S" + i;
            String nextId = "S" + (i + 1);

            if (s.type == StepType.DECISION) {
                String yesCond = (s.conditionYes != null && !s.conditionYes.isEmpty()) ? sanitizeForMermaid(s.conditionYes) : "VALID";
                String yesLabel = "<b>[" + yesCond + "]</b>";
                sb.append("    ").append(currId).append(" -->|\"").append(yesLabel).append("\"| ").append(nextId).append("\n");

                if (s.hasBranchNo()) {
                    String rejectId = "R" + i;
                    String noCond = (s.conditionNo != null && !s.conditionNo.isEmpty()) ? sanitizeForMermaid(s.conditionNo) : "INVALID";
                    String noLabel = "<b>[" + noCond + "]</b>";
                    sb.append("    ").append(currId).append(" -->|\"").append(noLabel).append("\"| ").append(rejectId).append("\n");
                }
            } else {
                sb.append("    ").append(currId).append(" --> ").append(nextId).append("\n");
            }
        }

        String result = sb.toString();
        return result;
    }

    private static String sanitizeForMermaid(String text) {
        if (text == null) {
            String empty = "";
            return empty;
        }
        String clean = text.replace("\"", "'")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("[", "&#91;")
                .replace("]", "&#93;")
                .replace("{", "&#123;")
                .replace("}", "&#125;")
                .replace("(", "&#40;")
                .replace(")", "&#41;")
                .trim();
        return clean;
    }

    /**
     * Intelligently wraps long text strings across multiple balanced lines with <br/> tags
     * so that the full sentences render completely without being cut off.
     */
    public static String wrapTextHtml(String text, int maxLineLen) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String[] words = text.trim().split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
            } else if (currentLine.length() + 1 + word.length() <= maxLineLen) {
                currentLine.append(" ").append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return String.join("<br/>", lines);
    }

    /**
     * Translates raw PL/SQL variables, assignment operators, and code syntax into
     * fluid, natural conversational English for non-technical stakeholders (The English Rule).
     */
    public static String translateToNaturalEnglish(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String clean = text.trim();

        // Strip raw DBMS_OUTPUT or Outp calls -> "Print ... to console"
        if (clean.toLowerCase().contains("dbms_output.put_line") || clean.toLowerCase().contains("outp(")) {
            clean = clean.replaceAll("(?i)(dbms_output\\.put_line|outp)\\s*\\(['\"]?(.*?)['\"]?\\)", "Print to console: $2");
        }

        // Specific high-frequency PL/SQL flags (e.g. v_diff := TRUE)
        clean = clean.replaceAll("(?i)\\b(v_|p_)?diff\\s*:=\\s*TRUE\\b", "Mark that difference was found");
        clean = clean.replaceAll("(?i)\\b(v_|p_)?diff\\s*:=\\s*FALSE\\b", "Clear difference flag");

        // Clean assignment operators (e.g., v_x := y -> Set x to y)
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*:=\\s*(['\"]?.*?['\"]?)\\b", "Set $2 to $3");

        // Cursor, transaction & control flow translations
        clean = clean.replaceAll("(?i)\\bOPEN\\s+([a-zA-Z0-9_]+)\\b", "Open cursor for $1");
        clean = clean.replaceAll("(?i)\\bFETCH\\s+([a-zA-Z0-9_]+)\\s+INTO\\s+([^;]+)", "Fetch next record from $1");
        clean = clean.replaceAll("(?i)\\bCLOSE\\s+([a-zA-Z0-9_]+)\\b", "Close cursor $1");
        clean = clean.replaceAll("(?i)\\bFOR\\s+([a-zA-Z0-9_]+)\\s+IN\\s+([a-zA-Z0-9_]+)\\s+LOOP\\b", "Iterate records in $2");
        clean = clean.replaceAll("(?i)\\bEXIT\\s+WHEN\\s+([^;]+)", "Exit loop when $1");
        clean = clean.replaceAll("(?i)\\bCOMMIT\\b;?", "Commit transaction to database");
        clean = clean.replaceAll("(?i)\\bROLLBACK\\b;?", "Rollback transaction");
        clean = clean.replaceAll("(?i)\\bRAISE_APPLICATION_ERROR\\s*\\([^)]*?,\\s*['\"]?(.*?)['\"]?\\)", "Raise error: $1");

        // Clean raw conditional checks (e.g., v_i > 200 -> Count exceeds 200)
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*>\\s*([0-9]+)", "Does $2 exceed $3?");
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*>=\\s*([0-9]+)", "Is $2 at least $3?");
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*<=\\s*([0-9]+)", "Is $2 within $3?");
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*<\\s*([0-9]+)", "Is $2 less than $3?");
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*=\\s*(['\"]?.*?['\"]?)", "Is $2 equal to $3?");
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*(!=|<>)\\s*(['\"]?.*?['\"]?)", "Is $2 different from $3?");
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*IS\\s+NULL\\b", "Is $2 unassigned?");
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)\\s*IS\\s+NOT\\s+NULL\\b", "Is $2 populated?");

        // Clean variable prefix markers from bare identifiers
        clean = clean.replaceAll("(?i)\\b(v_|p_)([a-zA-Z0-9_]+)", "$2");

        // Specific PL/SQL session & operational driver commands
        clean = clean.replaceAll("(?i)\\bExecute:\\s*ALTER\\s+SESSION\\s+SET\\s+NLS_DATE_FORMAT\\b.*", "Configure database session date formatting rules");
        clean = clean.replaceAll("(?i)\\bQuery\\s+USER_CONSTRAINTS\\b", "Query foreign key constraints to disable");
        clean = clean.replaceAll("(?i)\\bLoop\\s*\\(\\s*1:\\s*Disable\\s+Fk\\s*\\)", "Are there more referential foreign key constraints to look up?");
        clean = clean.replaceAll("(?i)\\bLoop\\s*\\(\\s*2:\\s*Enable\\s+Fk\\s*\\)", "Are there more referential foreign key constraints to re-apply?");
        clean = clean.replaceAll("(?i)\\bLoop\\s*\\([0-9]+:\\s*(.*?)\\)", "Are there more $1 to process?");
        clean = clean.replaceAll("(?i)\\bExecute:\\s*ALTER\\s+TABLE\\s+DISABLE\\s+CONSTRAINT\\b.*", "Build and run dynamic SQL statement to disable current table constraint");
        clean = clean.replaceAll("(?i)\\bExecute:\\s*ALTER\\s+TABLE\\s+ENABLE\\s+CONSTRAINT\\b.*", "Build and run dynamic SQL statement to re-enable current table constraint");
        clean = clean.replaceAll("(?i)\\bTruncate\\s+Core\\s+([A-Za-z0-9_]+)\\s+Tables:?\\s*(.*)", "Sequentially truncate target operational tables: $2");
        clean = clean.replaceAll("(?i)\\bCall\\s+Procedure:\\s*([a-zA-Z0-9_]+)_ext_insert\\b", "Execute batch data extraction routine for $1");
        clean = clean.replaceAll("(?i)\\bCall\\s+Procedure:\\s*([a-zA-Z0-9_]+)\\b", "Execute batch sub-procedure: $1");
        clean = clean.replaceAll("(?i)\\bCheck\\s+Status\\s*/\\s*Get\\s+Count\\b", "Did table import complete successfully?");
        clean = clean.replaceAll("(?i)\\bCommit\\s+transaction\\s+in\\s+Archive\\s+Transaction\\b", "Commit data changes to the database instance");
        clean = clean.replaceAll("(?i)\\bRollback\\s+transaction\\s+&\\s+Raise\\s+Application\\s+Error\\b", "Roll back transaction changes and raise application error");

        // Clean raw SQL DML syntax into active operational English
        if (clean.toLowerCase().startsWith("select ")) {
            clean = "Query " + clean.substring(7).trim();
        } else if (clean.toLowerCase().startsWith("insert into ")) {
            clean = "Insert into " + clean.substring(12).trim();
        } else if (clean.toLowerCase().startsWith("update ")) {
            clean = "Update " + clean.substring(7).trim();
        } else if (clean.toLowerCase().startsWith("delete from ")) {
            clean = "Delete from " + clean.substring(12).trim();
        } else if (clean.toLowerCase().startsWith("truncate table ")) {
            clean = "Clear table " + clean.substring(15).trim() + " via truncate";
        } else if (clean.toLowerCase().startsWith("truncate ")) {
            clean = "Clear table " + clean.substring(9).trim() + " via truncate";
        }

        return clean;
    }

    private static String formatNodeLabel(FlowStep s) {
        String naturalTitle = translateToNaturalEnglish(s.title != null ? s.title : "Step " + s.stepNumber);
        String rawTitle = sanitizeForMermaid(naturalTitle);
        if (s.stepNumber > 0 && s.stepNumber < 900
                && !rawTitle.toLowerCase().startsWith("step")
                && !rawTitle.toLowerCase().startsWith("phase")
                && !rawTitle.toLowerCase().startsWith("start")
                && !rawTitle.toLowerCase().startsWith("end")) {
            rawTitle = "Phase " + s.stepNumber + ": " + rawTitle;
        }

        // Decision diamonds in Mermaid circumscribe text bounding boxes.
        // Include the specific constraint / precondition rule if present, formatted with balanced multi-line subtitle.
        if (s.type == StepType.DECISION) {
            String naturalDesc = translateToNaturalEnglish(s.description);
            String cleanDesc = sanitizeForMermaid(naturalDesc);
            if (cleanDesc != null && !cleanDesc.trim().isEmpty() && !cleanDesc.equalsIgnoreCase(s.title)) {
                String wrappedDesc = wrapTextHtml(cleanDesc, 34);
                String result = "<b>" + rawTitle + "</b><br/><sub style='color:#FEF3C7;font-size:11.5px;line-height:1.25;'>" + wrappedDesc + "</sub>";
                return result;
            }
            String result = "<b>" + rawTitle + "</b>";
            return result;
        }

        // For Process, Database, and IO cards: include full subtitle wrapped gracefully
        String naturalDesc = translateToNaturalEnglish(s.description);
        String cleanDesc = sanitizeForMermaid(naturalDesc);
        if (cleanDesc != null && !cleanDesc.trim().isEmpty()) {
            String wrappedDesc = wrapTextHtml(cleanDesc, 48);
            String result = "<div style='min-width:380px;max-width:500px;text-align:center;'><b>" + rawTitle + "</b><br/><sub style='color:#CBD5E1;font-size:11.5px;line-height:1.25;'>" + wrappedDesc + "</sub></div>";
            return result;
        }

        String result = "<div style='min-width:380px;max-width:500px;text-align:center;'><b>" + rawTitle + "</b></div>";
        return result;
    }

    /**
     * Legacy Java2D flowchart renderer with smart vertical space filling and intelligent pagination.
     */
    public static void renderProgramFlowchartJava2D(XWPFDocument doc, String sectionTitle, List<FlowStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        int totalSteps = steps.size();
        if (totalSteps <= MAX_STEPS_PER_PAGE) {
            // Render single complete flowchart
            renderDiagramSlice(doc, sectionTitle, steps, 1, 1);
        } else {
            // Multi-part slicing for unusually large routines (> 9 steps)
            int totalParts = (int) Math.ceil((double) totalSteps / (MAX_STEPS_PER_PAGE - 1));
            int stepsPerPart = (int) Math.ceil((double) totalSteps / totalParts);

            int startIdx = 0;
            for (int part = 1; part <= totalParts; part++) {
                int endIdx = Math.min(startIdx + stepsPerPart, totalSteps);
                List<FlowStep> slice = new ArrayList<>(steps.subList(startIdx, endIdx));

                if (part < totalParts) {
                    slice.add(new FlowStep(999, "CONTINUES TO PART " + (part + 1), "Off-page connector to next execution segment", DocxDiagramHelper.StepType.PREDEFINED_PROCESS, HEX_NAVY));
                }

                renderDiagramSlice(doc, sectionTitle, slice, part, totalParts);
                startIdx = endIdx;
            }
        }
    }

    private static void renderDiagramSlice(XWPFDocument doc, String routineName, List<FlowStep> steps, int currentPart, int totalParts) {
        // Page Break ONLY on subsequent parts (avoids duplicate headings on page 1)
        if (currentPart > 1) {
            XWPFParagraph pageBreakPara = doc.createParagraph();
            pageBreakPara.setPageBreak(true);
            pageBreakPara.setSpacingBefore(0);
            pageBreakPara.setSpacingAfter(60);
            safeSetKeepNext(pageBreakPara, true);
        }

        // Dynamic Height Scaling
        int logicalWidth = 920;
        int headerHeight = 56;
        int nodeHeight = (steps.size() <= 4) ? 104 : (steps.size() <= 6 ? 96 : 88);
        int verticalGap = (steps.size() <= 4) ? 30 : (steps.size() <= 6 ? 24 : 20);
        int totalLogicalHeight = headerHeight + 20 + (steps.size() * (nodeHeight + verticalGap)) + 10;

        // 6X Ultra-Retina Super-Sampling for 600+ DPI vector sharpness (Doubled Resolution)
        int scale = 6;
        BufferedImage image = new BufferedImage(logicalWidth * scale, totalLogicalHeight * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.scale(scale, scale);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // Canvas Background & Outer Border
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, logicalWidth, totalLogicalHeight);
        g2d.setColor(new Color(226, 232, 240));
        g2d.drawRoundRect(2, 2, logicalWidth - 4, totalLogicalHeight - 4, 8, 8);

        // Header Banner Card with Integrated Business Analyst Legend
        g2d.setColor(COLOR_NAVY);
        g2d.fillRoundRect(6, 6, logicalWidth - 12, 26, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));

        String bannerText = (totalParts > 1)
                ? String.format("PROGRAM DECISION & EXECUTION FLOW: %s (Part %d of %d)", routineName.toUpperCase(), currentPart, totalParts)
                : String.format("PROGRAM DECISION & EXECUTION FLOW: %s", routineName.toUpperCase());
        g2d.drawString(bannerText, 20, 24);

        // BA Legend Ribbon Strip
        int legendY = 35;
        int legendH = 20;
        g2d.setColor(new Color(241, 245, 249)); // #F1F5F9 Slate Tint
        g2d.fillRoundRect(6, legendY, logicalWidth - 12, legendH, 4, 4);
        g2d.setColor(new Color(203, 213, 225)); // Border #CBD5E1
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawRoundRect(6, legendY, logicalWidth - 12, legendH, 4, 4);

        // Legend Items
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 9));

        // 1. Start/End (Green)
        g2d.setColor(new Color(22, 163, 74));
        g2d.fillOval(18, legendY + 5, 10, 10);
        g2d.setColor(new Color(30, 41, 59));
        g2d.drawString("Start / Complete", 32, legendY + 14);

        // 2. Ingress/Egress (Blue)
        g2d.setColor(new Color(2, 132, 199));
        g2d.fillRect(135, legendY + 5, 10, 10);
        g2d.setColor(new Color(30, 41, 59));
        g2d.drawString("Inputs & Outputs", 149, legendY + 14);

        // 3. Validation (Gold Diamond)
        Polygon goldDia = new Polygon();
        goldDia.addPoint(265, legendY + 5);
        goldDia.addPoint(270, legendY + 10);
        goldDia.addPoint(265, legendY + 15);
        goldDia.addPoint(260, legendY + 10);
        g2d.setColor(new Color(217, 119, 6));
        g2d.fill(goldDia);
        g2d.setColor(new Color(30, 41, 59));
        g2d.drawString("Business Validation", 276, legendY + 14);

        // 4. Core Logic (Navy)
        g2d.setColor(COLOR_NAVY);
        g2d.fillRect(405, legendY + 5, 10, 10);
        g2d.setColor(new Color(30, 41, 59));
        g2d.drawString("Business Logic", 419, legendY + 14);

        // 5. Database (Plum)
        g2d.setColor(new Color(88, 24, 69));
        g2d.fillRoundRect(520, legendY + 5, 10, 10, 4, 4);
        g2d.setColor(new Color(30, 41, 59));
        g2d.drawString("Database DML", 534, legendY + 14);

        // 6. Rejection/Error (Red)
        g2d.setColor(new Color(220, 38, 38));
        g2d.fillRect(635, legendY + 5, 10, 10);
        g2d.setColor(new Color(30, 41, 59));
        g2d.drawString("Rejection / Error", 649, legendY + 14);

        boolean hasAnyRejection = false;
        for (FlowStep s : steps) {
            if (s.hasBranchNo()) {
                hasAnyRejection = true;
                break;
            }
        }
        int mainWidth = hasAnyRejection ? 580 : 800;
        int mainX = hasAnyRejection ? 40 : (logicalWidth - mainWidth) / 2;
        int currentY = headerHeight + 20;
        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            Color themeColor = parseHexColor(step.getHexThemeColor());

            // 1. Draw Inscribed-Polygon Node (With safe diamond bounds)
            drawRasterNodeShape(g2d, step, mainX, currentY, mainWidth, nodeHeight, themeColor);

            // 2. Draw Right-Side Rejection Branch Card if present
            if (step.hasBranchNo()) {
                String badgeText = step.conditionNo != null ? "[" + step.conditionNo + "]" : "[INVALID]";
                drawRasterBranchRejectionCard(g2d, badgeText, step.getBranchNoAction(), mainX + mainWidth, currentY, nodeHeight);
            }

            // 3. Draw Downward Vector Spine Connector
            if (i < steps.size() - 1) {
                int arrowStartY = currentY + nodeHeight;
                int arrowEndY = arrowStartY + verticalGap;
                int centerX = mainX + (mainWidth / 2);

                g2d.setColor(COLOR_SLATE);
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.drawLine(centerX, arrowStartY, centerX, arrowEndY);

                // Connector condition label (e.g., [VALID] or [PASS])
                if (step.conditionYes != null && !step.conditionYes.trim().isEmpty()) {
                    g2d.setFont(new Font("Segoe UI", Font.BOLD, 9));
                    String yesLabel = "[" + step.conditionYes.trim() + "]";
                    FontMetrics cfm = g2d.getFontMetrics();
                    int labelW = cfm.stringWidth(yesLabel) + 12;
                    int labelH = 16;
                    int labelX = centerX + 6;
                    int labelY = arrowStartY + (verticalGap / 2) - 8;
                    g2d.setColor(new Color(220, 252, 231)); // #DCFCE7 Mint Green Pill
                    g2d.fillRoundRect(labelX, labelY, labelW, labelH, 6, 6);
                    g2d.setColor(new Color(22, 101, 52)); // Border #166534
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRoundRect(labelX, labelY, labelW, labelH, 6, 6);
                    g2d.setColor(new Color(20, 83, 45)); // Dark Forest Green #14532D
                    g2d.drawString(yesLabel, labelX + 6, labelY + 12);
                }

                // Triangle Arrowhead
                Polygon arrowHead = new Polygon();
                arrowHead.addPoint(centerX, arrowEndY);
                arrowHead.addPoint(centerX - 5, arrowEndY - 8);
                arrowHead.addPoint(centerX + 5, arrowEndY - 8);
                g2d.setColor(COLOR_SLATE);
                g2d.fill(arrowHead);
            }

            currentY += (nodeHeight + verticalGap);
        }

        g2d.dispose();

        // Embed Rendered 300 DPI Image into Word with full page width utilization (520 pt / 7.2")
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            XWPFParagraph imgPara = doc.createParagraph();
            imgPara.setAlignment(ParagraphAlignment.CENTER);
            imgPara.setSpacingBefore(30);
            imgPara.setSpacingAfter(30);
            safeSetKeepNext(imgPara, true);

            int targetWidthPt = 540; // 7.50 inches wide, maximizing single page printable width
            int targetHeightPt = (int) ((double) targetWidthPt * totalLogicalHeight / logicalWidth);

            XWPFRun imgRun = imgPara.createRun();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
                imgRun.addPicture(bais, XWPFDocument.PICTURE_TYPE_PNG, "ExecutionFlow.png",
                        Units.toEMU(targetWidthPt), Units.toEMU(targetHeightPt));
            }

            // Figure Caption
            XWPFParagraph capPara = doc.createParagraph();
            capPara.setAlignment(ParagraphAlignment.CENTER);
            capPara.setSpacingBefore(10);
            capPara.setSpacingAfter(60);
            XWPFRun capRun = capPara.createRun();
            capRun.setItalic(true);
            capRun.setFontSize(8.5);
            capRun.setColor("64748B");
            capRun.setFontFamily("Segoe UI");
            String capText = (totalParts > 1)
                    ? String.format("Figure: Program Flowchart — Part %d of %d: %s", currentPart, totalParts, routineName)
                    : String.format("Figure: Program Decision & Execution Flowchart — %s", routineName);
            capRun.setText(capText);

        } catch (Exception e) {
            throw new RuntimeException("Failed to embed flowchart diagram into document", e);
        }
    }

    private static void drawRasterNodeShape(Graphics2D g2d, FlowStep step, int x, int y, int w, int h, Color strokeColor) {
        Color bgColor = getTintedBackgroundColor(step.getStepType());

        switch (step.getStepType()) {
            case TERMINATOR:
            case START:
            case END:
                g2d.setColor(bgColor);
                g2d.fillRoundRect(x, y, w, h, h, h);
                g2d.setColor(new Color(22, 101, 52)); // #166534 Forest Green Border
                g2d.setStroke(new BasicStroke(2.8f));
                g2d.drawRoundRect(x, y, w, h, h, h);

                // Inscribed Title
                g2d.setColor(new Color(20, 83, 45)); // #14532D
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String startTitle = (step.getStepNumber() > 0 && step.getStepNumber() < 900)
                        ? "STEP " + step.getStepNumber() + ": " + step.getTitle()
                        : step.getTitle();
                FontMetrics startFm = g2d.getFontMetrics();
                g2d.drawString(startTitle, x + (w - startFm.stringWidth(startTitle)) / 2, y + 25);

                // Inscribed Detail
                g2d.setColor(new Color(22, 101, 52)); // #166534
                g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                renderMultiLineTextCentered(g2d, step.getDetail(), x + (w / 2), y + 42, w - 40, 14, 3);
                break;

            case DATABASE:
            case DB_OPERATION:
            case TABLE:
                int capH = 14;
                g2d.setColor(bgColor);
                g2d.fillRect(x, y + (capH / 2), w, h - capH);
                g2d.fillOval(x, y, w, capH);
                g2d.fillOval(x, y + h - capH, w, capH);

                // Top shaded rim
                g2d.setColor(new Color(233, 213, 255)); // #E9D5FF Shaded cap
                g2d.fillOval(x, y, w, capH);

                g2d.setColor(new Color(88, 24, 69)); // #581845 Deep Plum
                g2d.setStroke(new BasicStroke(2.5f));
                g2d.drawLine(x, y + (capH / 2), x, y + h - (capH / 2));
                g2d.drawLine(x + w, y + (capH / 2), x + w, y + h - (capH / 2));
                g2d.drawOval(x, y, w, capH);
                g2d.drawArc(x, y + h - capH, w, capH, 180, 180);

                // Inscribed Title
                g2d.setColor(new Color(59, 16, 46)); // #3B102E Dark Plum
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String dbTitle = (step.getStepNumber() > 0 && step.getStepNumber() < 900)
                        ? "STEP " + step.getStepNumber() + ": " + step.getTitle()
                        : step.getTitle();
                FontMetrics dbFm = g2d.getFontMetrics();
                g2d.drawString(dbTitle, x + (w - dbFm.stringWidth(dbTitle)) / 2, y + 25);

                // Inscribed Detail
                g2d.setColor(new Color(88, 24, 69));
                g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                renderMultiLineTextCentered(g2d, step.getDetail(), x + (w / 2), y + 42, w - 40, 14, 3);
                break;

            case INPUT_OUTPUT:
            case IO:
            case IO_INPUT:
            case IO_OUTPUT:
                int slant = 22;
                Path2D.Double poly = new Path2D.Double();
                poly.moveTo(x + slant, y);
                poly.lineTo(x + w, y);
                poly.lineTo(x + w - slant, y + h);
                poly.lineTo(x, y + h);
                poly.closePath();
                g2d.setColor(bgColor);
                g2d.fill(poly);
                g2d.setColor(new Color(0, 90, 156)); // #005A9C Florida Lottery Blue
                g2d.setStroke(new BasicStroke(2.5f));
                g2d.draw(poly);

                // Inscribed Title
                g2d.setColor(new Color(0, 43, 73)); // #002B49 Dark Navy
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String ioTitle = (step.getStepNumber() > 0 && step.getStepNumber() < 900)
                        ? "STEP " + step.getStepNumber() + ": " + step.getTitle()
                        : step.getTitle();
                FontMetrics ioFm = g2d.getFontMetrics();
                g2d.drawString(ioTitle, x + (w - ioFm.stringWidth(ioTitle)) / 2, y + 25);

                // Inscribed Detail
                g2d.setColor(new Color(3, 105, 161)); // #0369A1
                g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                renderMultiLineTextCentered(g2d, step.getDetail(), x + (w / 2), y + 42, w - 50, 14, 3);
                break;

            case DECISION:
                Path2D.Double diamond = new Path2D.Double();
                diamond.moveTo(x + (w / 2.0), y);
                diamond.lineTo(x + w, y + (h / 2.0));
                diamond.lineTo(x + (w / 2.0), y + h);
                diamond.lineTo(x, y + (h / 2.0));
                diamond.closePath();
                g2d.setColor(bgColor);
                g2d.fill(diamond);
                g2d.setColor(new Color(217, 119, 6)); // #D97706 Warm Amber Gold
                g2d.setStroke(new BasicStroke(2.8f));
                g2d.draw(diamond);

                // Inscribed Title (Mathematically bounded to w * 0.68)
                g2d.setColor(new Color(120, 53, 15)); // #78350F
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 11));
                String decTitle = (step.getStepNumber() > 0 && step.getStepNumber() < 900)
                        ? "STEP " + step.getStepNumber() + ": " + step.getTitle()
                        : step.getTitle();
                FontMetrics decFm = g2d.getFontMetrics();
                g2d.drawString(decTitle, x + (w - decFm.stringWidth(decTitle)) / 2, y + 22);

                // Inscribed Subtitle Detail Text with Diamond-Safe Bounding
                g2d.setColor(new Color(146, 64, 14)); // #92400E
                g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                int diamondMaxW = (int) (w * 0.68);
                renderMultiLineTextCentered(g2d, step.getDetail(), x + (w / 2), y + 36, diamondMaxW, 13, 3);
                break;

            case PREDEFINED_PROCESS:
            case CALL_EXTERNAL:
            default:
                // Executive Process Card with Navy Header Stripe
                g2d.setColor(bgColor);
                g2d.fillRoundRect(x, y, w, h, 8, 8);

                // Top Navy Header Stripe
                int stripeH = 26;
                g2d.setColor(COLOR_NAVY);
                g2d.fillRoundRect(x, y, w, stripeH, 8, 8);
                g2d.fillRect(x, y + 16, w, stripeH - 16); // Square off bottom corners of stripe

                // Card Border
                g2d.setColor(COLOR_NAVY);
                g2d.setStroke(new BasicStroke(2.5f));
                g2d.drawRoundRect(x, y, w, h, 8, 8);

                if (step.getStepType() == StepType.PREDEFINED_PROCESS || step.getStepType() == StepType.CALL_EXTERNAL) {
                    g2d.drawLine(x + 16, y, x + 16, y + h);
                    g2d.drawLine(x + w - 16, y, x + w - 16, y + h);
                }

                // White Title Text inside Navy Stripe
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String procTitle = (step.getStepNumber() > 0 && step.getStepNumber() < 900)
                        ? "STEP " + step.getStepNumber() + ": " + step.getTitle()
                        : step.getTitle();
                FontMetrics procFm = g2d.getFontMetrics();
                g2d.drawString(procTitle, x + (w - procFm.stringWidth(procTitle)) / 2, y + 18);

                // Body Detail Text in crisp dark slate
                g2d.setColor(new Color(30, 41, 59)); // #1E293B
                g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                int standardMaxW = w - 40;
                renderMultiLineTextCentered(g2d, step.getDetail(), x + (w / 2), y + 44, standardMaxW, 14, 3);
                break;
        }
    }

    private static Color getTintedBackgroundColor(DocxDiagramHelper.StepType stepType) {
        if (stepType == null) {
            Color fallback = new Color(248, 250, 252);
            return fallback;
        }
        switch (stepType) {
            case START:
            case END:
            case TERMINATOR:
                Color greenTint = new Color(220, 252, 231); // #DCFCE7 Emerald Green tint
                return greenTint;
            case DECISION:
                Color goldTint = new Color(254, 243, 199); // #FEF3C7 Warm Amber tint
                return goldTint;
            case DATABASE:
            case DB_OPERATION:
            case TABLE:
                Color plumTint = new Color(243, 232, 255); // #F3E8FF Deep Plum/Lavender tint
                return plumTint;
            case INPUT_OUTPUT:
            case IO:
            case IO_INPUT:
            case IO_OUTPUT:
                Color blueTint = new Color(224, 242, 254); // #E0F2FE Sky Blue tint
                return blueTint;
            case PREDEFINED_PROCESS:
            case CALL_EXTERNAL:
            default:
                Color defaultTint = new Color(248, 250, 252); // #F8FAFC Clean Card Body
                return defaultTint;
        }
    }

    private static Color getThemeTitleColor(DocxDiagramHelper.StepType stepType) {
        if (stepType == null) {
            Color fallback = COLOR_NAVY;
            return fallback;
        }
        switch (stepType) {
            case START:
            case END:
            case TERMINATOR:
                Color greenTitle = new Color(20, 83, 45); // Dark Green #14532D
                return greenTitle;
            case DECISION:
                Color goldTitle = new Color(120, 53, 15); // Dark Amber #78350F
                return goldTitle;
            case DATABASE:
            case DB_OPERATION:
            case TABLE:
                Color plumTitle = new Color(59, 16, 46); // Dark Plum #3B102E
                return plumTitle;
            case INPUT_OUTPUT:
            case IO:
            case IO_INPUT:
            case IO_OUTPUT:
                Color blueTitle = new Color(0, 43, 73); // Florida Lottery Dark Navy #002B49
                return blueTitle;
            default:
                Color defaultTitle = COLOR_NAVY;
                return defaultTitle;
        }
    }

    private static Color getThemeSubtitleColor(DocxDiagramHelper.StepType stepType) {
        if (stepType == null) {
            Color fallback = new Color(71, 85, 105);
            return fallback;
        }
        switch (stepType) {
            case START:
            case END:
            case TERMINATOR:
                Color greenSub = new Color(22, 101, 52); // #166534
                return greenSub;
            case DECISION:
                Color goldSub = new Color(146, 64, 14); // #92400E
                return goldSub;
            case DATABASE:
            case DB_OPERATION:
            case TABLE:
                Color plumSub = new Color(88, 24, 69); // #581845
                return plumSub;
            case INPUT_OUTPUT:
            case IO:
            case IO_INPUT:
            case IO_OUTPUT:
                Color blueSub = new Color(3, 105, 161); // #0369A1
                return blueSub;
            default:
                Color defaultSub = new Color(30, 41, 59); // #1E293B
                return defaultSub;
        }
    }

    private static void drawRasterBranchRejectionCard(Graphics2D g2d, String badgeText, String actionText, int splitX, int y, int h) {
        int cardX = splitX + 35;
        int cardY = y + 8;
        int cardW = 210;
        int cardH = h - 16;

        // Dashed Red Rejection Branch Connector
        g2d.setColor(COLOR_RED);
        g2d.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4}, 0));
        g2d.drawLine(splitX, y + (h / 2), cardX, y + (h / 2));

        // Rejection Card Body with Notched Look
        g2d.setColor(new Color(254, 242, 242));
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 6, 6);
        g2d.setColor(COLOR_RED);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 6, 6);

        // Badge & Action Text
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g2d.drawString(badgeText, cardX + 10, cardY + 15);
        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        g2d.setColor(new Color(30, 41, 59));
        renderMultiLineTextLeft(g2d, actionText, cardX + 10, cardY + 28, cardW - 20, 12, 3);
    }

    private static void renderMultiLineTextCentered(Graphics2D g2d, String text, int centerX, int startY, int maxW, int lineH, int maxLines) {
        if (text == null || text.trim().isEmpty()) return;
        List<String> lines = wrapText(g2d, text.trim(), maxW, maxLines);
        FontMetrics fm = g2d.getFontMetrics();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineW = fm.stringWidth(line);
            g2d.drawString(line, centerX - (lineW / 2), startY + (i * lineH));
        }
    }

    private static void renderMultiLineTextLeft(Graphics2D g2d, String text, int x, int startY, int maxW, int lineH, int maxLines) {
        if (text == null || text.trim().isEmpty()) return;
        List<String> lines = wrapText(g2d, text.trim(), maxW, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            g2d.drawString(lines.get(i), x, startY + (i * lineH));
        }
    }

    private static List<String> wrapText(Graphics2D g2d, String text, int maxW, int maxLines) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        FontMetrics fm = g2d.getFontMetrics();

        for (String word : words) {
            String test = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (fm.stringWidth(test) <= maxW) {
                currentLine.append(currentLine.length() == 0 ? "" : " ").append(word);
            } else {
                if (currentLine.length() > 0) {
                    result.add(currentLine.toString());
                    if (result.size() >= maxLines) break;
                    currentLine = new StringBuilder(word);
                }
            }
        }
        if (currentLine.length() > 0 && result.size() < maxLines) {
            result.add(currentLine.toString());
        }
        return result;
    }

    /**
     * Safely sets keepNext on a paragraph, ensuring CTPPr is initialized to prevent NPE in Apache POI.
     */
    public static void safeSetKeepNext(XWPFParagraph paragraph, boolean keepNext) {
        if (paragraph == null) {
            return;
        }
        try {
            CTP ctp = paragraph.getCTP();
            if (ctp != null) {
                CTPPr ppr = ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
                if (keepNext) {
                    if (!ppr.isSetKeepNext()) {
                        ppr.addNewKeepNext();
                    }
                } else if (ppr.isSetKeepNext()) {
                    ppr.unsetKeepNext();
                }
            }
        } catch (Throwable t) {
            // Defensive
        }
    }

    private static Color parseHexColor(String hex) {
        String clean = (hex != null && hex.startsWith("#")) ? hex.substring(1) : hex;
        Color result = (clean != null && clean.length() == 6) ? Color.decode("0x" + clean) : COLOR_BLUE;
        return result;
    }

    // =========================================================================
    // 1. PROCEDURE-LEVEL PILL-TO-CYLINDER ENTITY LINEAGE DIAGRAM
    // =========================================================================
    public static class LineageInteractionItem {
        public final String targetName;
        public final String actionType; // "LOCAL WRITE", "REMOTE DB LINK SELECT", "LOCAL READ", "EXECUTE EXTERNAL FUNCTION", etc.
        public final boolean isRemote;
        public final boolean isSubRoutine;
        public final boolean isFunction;

        public LineageInteractionItem(String targetName, String actionType, boolean isRemote, boolean isSubRoutine, boolean isFunction) {
            this.targetName = targetName;
            this.actionType = actionType;
            this.isRemote = isRemote;
            this.isSubRoutine = isSubRoutine;
            this.isFunction = isFunction;
        }

        public LineageInteractionItem(String targetName, String actionType, boolean isRemote, boolean isSubRoutine) {
            this(targetName, actionType, isRemote, isSubRoutine, false);
        }
    }

    public static boolean isFunctionTarget(
            String targetName,
            String rawToken,
            GenerateTechnicalWordDocs.GlobalObjectRegistry registry,
            String currentSchema,
            String sqlSource) {
        if (rawToken != null) {
            String upper = rawToken.toUpperCase();
            if (upper.contains("FUNCTION") || upper.contains("FUNC:") || upper.contains("(FUNCTION)") || upper.startsWith("FN_") || upper.startsWith("SF_")) {
                return true;
            }
        }
        if (targetName != null) {
            String upper = targetName.toUpperCase();
            String bare = upper.contains(".") ? upper.substring(upper.lastIndexOf('.') + 1) : upper;
            if (bare.startsWith("FN_") || bare.startsWith("SF_") || bare.startsWith("FUNC_") || bare.startsWith("GET_") || bare.startsWith("IS_") || bare.startsWith("CALC_") || bare.startsWith("F_")) {
                return true;
            }
            if (registry != null) {
                GenerateTechnicalWordDocs.ObjectDocRef ref = registry.resolve(targetName, currentSchema);
                if (ref != null && "FUNCTION".equalsIgnoreCase(ref.objectType)) {
                    return true;
                }
            }
            if (sqlSource != null && !sqlSource.trim().isEmpty()) {
                // If invoked in assignment/expression context: := bare( or RETURN bare( or SELECT bare(
                try {
                    Pattern funcCallPat = Pattern.compile("(?im)(?:\\:\\=|RETURN\\s+|SELECT\\s+[^;]*?)\\b" + Pattern.quote(bare) + "\\s*\\(");
                    if (funcCallPat.matcher(sqlSource).find()) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    public static String determineCallActionType(
            String cleanTarget,
            String rawToken,
            boolean isRemote,
            boolean isFunc,
            String sqlSource) {
        String upper = ((cleanTarget != null ? cleanTarget : "") + " " + (rawToken != null ? rawToken : "")).toUpperCase();

        // 1. Email / SMTP / Mail services
        if (upper.contains("UTL_SMTP") || upper.contains("UTL_MAIL") || upper.contains("APEX_MAIL") || upper.contains("SEND_MAIL") || upper.contains("EMAIL_PKG") || upper.contains("SEND_EMAIL") || upper.contains("MAIL_PKG") || upper.contains("NOTIFICATION")) {
            return isRemote ? "REMOTE EMAIL DISPATCH" : "SEND EMAIL NOTIFICATION";
        }

        // 2. HTTP / REST / Web APIs
        if (upper.contains("UTL_HTTP") || upper.contains("APEX_WEB_SERVICE") || upper.contains("HTTPURITYPE") || upper.contains("REST_API") || upper.contains("WEB_SERVICE") || upper.contains("HTTP_CLIENT") || upper.contains("API_CLIENT") || upper.contains("REST")) {
            return isRemote ? "REMOTE WEB SERVICE API" : "INVOKE EXTERNAL API";
        }

        // 3. File I/O & PDF / Report Generation / Printing
        if (upper.contains("UTL_FILE") || upper.contains("DBMS_XSLPROCESSOR") || upper.contains("PDF_PKG") || upper.contains("GENERATE_PDF") || upper.contains("PRINT_PKG") || upper.contains("RUN_REPORT_OBJECT") || upper.contains("REPORT_PKG")) {
            if (upper.contains("PDF") || upper.contains("REPORT") || upper.contains("PRINT")) {
                return "GENERATE PDF / REPORT";
            }
            return "FILE EXPORT (UTL_FILE)";
        }

        // 4. Function vs Procedure default
        if (isFunc) {
            return isRemote ? "REMOTE DB LINK FUNCTION" : "EXECUTE EXTERNAL FUNCTION";
        } else {
            return isRemote ? "REMOTE DB LINK PROCEDURE" : "EXECUTE EXTERNAL PROCEDURE";
        }
    }

    /**
     * Generates a Florida Lottery-styled Mermaid flowchart DSL (graph LR) representing
     * the Procedure-to-Table Entity Lineage Diagram:
     * [Procedure Navy Pill] ──(LOCAL WRITE / REMOTE DB LINK SELECT)──▶ [(Database Cylinders)]
     */
    public static String generateMermaidProcedureLineage(
            GenerateTechnicalWordDocs.RoutineDocModel r,
            List<LineageInteractionItem> items) {

        StringBuilder sb = new StringBuilder();
        sb.append("graph LR\n");
        sb.append("    classDef procPill fill:#002B49,stroke:#DAA520,stroke-width:2.5px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("    classDef localTable fill:#002B49,stroke:#DAA520,stroke-width:2px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("    classDef remoteTable fill:#FEF3C7,stroke:#D97706,stroke-width:2px,color:#78350F,font-weight:bold;\n");
        sb.append("    classDef subRoutine fill:#EFF6FF,stroke:#2563EB,stroke-width:1.5px,color:#1E3A8A,font-weight:bold;\n\n");

        String procType = (r.routineType != null && !r.routineType.trim().isEmpty()) ? r.routineType.toUpperCase() : "PROCEDURE";
        String procLabel = "<b>" + procType + "</b><br/>" + r.schema.toUpperCase() + "." + r.objectName.toUpperCase();
        String safeProcId = "PROC_" + r.objectName.replaceAll("[^a-zA-Z0-9_]", "_");
        sb.append("    ").append(safeProcId).append("([\"").append(procLabel).append("\"]):::procPill\n\n");

        int count = items.size();
        for (int i = 0; i < count; i++) {
            LineageInteractionItem item = items.get(i);
            String safeTargetId = "TARGET_" + i + "_" + item.targetName.replaceAll("[^a-zA-Z0-9_]", "_");
            String styleClass = item.isRemote ? "remoteTable" : (item.isSubRoutine ? "subRoutine" : "localTable");
            String typeHeader = item.isSubRoutine
                    ? (item.isRemote ? "REMOTE ROUTINE" : (item.isFunction ? "FUNCTION" : "PROCEDURE"))
                    : (item.isRemote ? "REMOTE TABLE" : (item.targetName.contains(".Views(") ? "VIEW" : "TABLE"));
            String targetLabel = "<b>" + typeHeader + "</b><br/>" + item.targetName;
            String shape = item.isSubRoutine
                    ? "([\"" + targetLabel + "\"])"
                    : "[(\"" + targetLabel + "\")]";

            sb.append("    ").append(safeTargetId).append(shape).append(":::").append(styleClass).append("\n");
            sb.append("    ").append(safeProcId).append(" -->|\"").append(item.actionType).append("\"| ").append(safeTargetId).append("\n");
        }
        String result = sb.toString();
        return result;
    }

    /**
     * Renders and embeds a crisp Procedure-to-Table Entity Lineage Diagram:
     * Primary: Vector Mermaid Flowchart.
     * Fallback: High-DPI Retina Java2D Renderer.
     */
    public static void embedProcedureEntityLineageDiagram(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.RoutineDocModel r,
            GenerateTechnicalWordDocs.GlobalObjectRegistry registry) {
        try {
            String sourceText = (r.dataFlowDiagram != null && !r.dataFlowDiagram.isEmpty()) ? r.dataFlowDiagram : r.dependencies;
            String writes = GenerateTechnicalWordDocs.extractSection(sourceText, "Writes:");
            String updates = GenerateTechnicalWordDocs.extractSection(sourceText, "Updates:");
            String deletes = GenerateTechnicalWordDocs.extractSection(sourceText, "Deletes:");
            String reads = GenerateTechnicalWordDocs.extractSection(sourceText, "Reads:");
            String calls = GenerateTechnicalWordDocs.extractSection(sourceText, "Calls:");

            List<LineageInteractionItem> items = new ArrayList<>();
            Set<String> seenTargets = new HashSet<>();

            // 1. Reads & Selects (Constraint & Lookup Ingress)
            for (String t : GenerateTechnicalWordDocs.parseCleanList(reads)) {
                String cleanTarget = formatQualifiedObjectName(t, r.schema);
                if (!cleanTarget.isEmpty() && seenTargets.add(cleanTarget.toUpperCase())) {
                    boolean isRemote = t.contains("@");
                    String action = isRemote ? "REMOTE DB LINK SELECT" : "LOCAL READ";
                    items.add(new LineageInteractionItem(cleanTarget, action, isRemote, false));
                }
            }

            // 2. Direct SQL source DML table recovery (e.g. TRUNCATE TABLE ARTSECR, ARTTRAN, ARTDNBR, ARTTCDE...)
            if (r.sqlSource != null && !r.sqlSource.trim().isEmpty()) {
                Matcher truncMat = Pattern.compile("(?im)(?:TRUNCATE\\s+TABLE|DELETE\\s+FROM|INSERT\\s+INTO|UPDATE)\\s+([a-zA-Z0-9_$.]+(?:@[a-zA-Z0-9_]+)?)").matcher(r.sqlSource);
                while (truncMat.find()) {
                    String rawTbl = truncMat.group(1).trim();
                    String cleanTarget = formatQualifiedObjectName(rawTbl, r.schema);
                    if (!cleanTarget.isEmpty() && seenTargets.add(cleanTarget.toUpperCase())) {
                        boolean isRemote = rawTbl.contains("@");
                        items.add(new LineageInteractionItem(cleanTarget, isRemote ? "REMOTE DB LINK WRITE" : "LOCAL WRITE", isRemote, false));
                    }
                }
            }

            // 3. Writes
            for (String t : GenerateTechnicalWordDocs.parseCleanList(writes)) {
                String cleanTarget = formatQualifiedObjectName(t, r.schema);
                if (!cleanTarget.isEmpty() && seenTargets.add(cleanTarget.toUpperCase())) {
                    boolean isRemote = t.contains("@");
                    String action = isRemote ? "REMOTE DB LINK WRITE" : "LOCAL WRITE";
                    items.add(new LineageInteractionItem(cleanTarget, action, isRemote, false));
                }
            }

            // 4. Updates
            for (String t : GenerateTechnicalWordDocs.parseCleanList(updates)) {
                String cleanTarget = formatQualifiedObjectName(t, r.schema);
                if (!cleanTarget.isEmpty() && seenTargets.add(cleanTarget.toUpperCase())) {
                    boolean isRemote = t.contains("@");
                    String action = isRemote ? "REMOTE DB LINK UPDATE" : "LOCAL UPDATE";
                    items.add(new LineageInteractionItem(cleanTarget, action, isRemote, false));
                }
            }

            // 5. Deletes
            for (String t : GenerateTechnicalWordDocs.parseCleanList(deletes)) {
                String cleanTarget = formatQualifiedObjectName(t, r.schema);
                if (!cleanTarget.isEmpty() && seenTargets.add(cleanTarget.toUpperCase())) {
                    boolean isRemote = t.contains("@");
                    String action = isRemote ? "REMOTE DB LINK DELETE" : "LOCAL DELETE";
                    items.add(new LineageInteractionItem(cleanTarget, action, isRemote, false));
                }
            }

            // 6. Calls (External APIs, Emails, Reports, Functions & Procedures executed downstream)
            for (String t : GenerateTechnicalWordDocs.parseCleanList(calls)) {
                String cleanTarget = formatQualifiedObjectName(t, r.schema);
                if (!cleanTarget.isEmpty() && seenTargets.add(cleanTarget.toUpperCase())) {
                    boolean isRemote = t.contains("@");
                    boolean isFunc = isFunctionTarget(cleanTarget, t, registry, r.schema, r.sqlSource);
                    String action = determineCallActionType(cleanTarget, t, isRemote, isFunc, r.sqlSource);
                    items.add(new LineageInteractionItem(cleanTarget, action, isRemote, true, isFunc));
                }
            }

            // 7. Direct SQL source scanner for External APIs, Emails & File/Report Services
            if (r.sqlSource != null && !r.sqlSource.trim().isEmpty()) {
                Matcher apiMat = Pattern.compile("(?im)\\b(UTL_HTTP|UTL_SMTP|UTL_MAIL|APEX_MAIL|APEX_WEB_SERVICE|UTL_FILE|RUN_REPORT_OBJECT)\\b").matcher(r.sqlSource);
                while (apiMat.find()) {
                    String pkg = apiMat.group(1).toUpperCase();
                    String qualifiedPkg = "SYS." + pkg;
                    if (seenTargets.add(qualifiedPkg)) {
                        String action;
                        if (pkg.contains("SMTP") || pkg.contains("MAIL")) {
                            action = "SEND EMAIL NOTIFICATION";
                        } else if (pkg.contains("HTTP") || pkg.contains("WEB_SERVICE")) {
                            action = "INVOKE EXTERNAL API";
                        } else if (pkg.contains("FILE")) {
                            action = "FILE EXPORT (UTL_FILE)";
                        } else {
                            action = "GENERATE PDF / REPORT";
                        }
                        items.add(new LineageInteractionItem(qualifiedPkg, action, false, true, false));
                    }
                }
            }

            if (items.isEmpty()) {
                items.add(new LineageInteractionItem(r.schema.toUpperCase() + ".AUDIT_LOG", "LOCAL WRITE", false, false, false));
                items.add(new LineageInteractionItem(r.schema.toUpperCase() + ".SYSTEM_CONTROL", "LOCAL READ", false, false, false));
            }

            // Group targets by Schema and Entity Type (Tables vs Procedures vs Functions vs APIs)
            // Rule: Show up to the first 4 specific items (Schema.ObjectName), followed by "+N More..." if > 4 items
            Map<String, List<LineageInteractionItem>> grouped = new LinkedHashMap<>();
            for (LineageInteractionItem it : items) {
                String sch = extractSchema(it.targetName, r.schema);
                String type = it.isFunction ? "Functions" : (it.isSubRoutine ? "Procedures" : "Tables");
                String key = sch + ":" + type;
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
            }

            List<LineageInteractionItem> displayItems = new ArrayList<>();
            for (Map.Entry<String, List<LineageInteractionItem>> entry : grouped.entrySet()) {
                String[] parts = entry.getKey().split(":");
                String sch = parts[0];
                String type = parts[1];
                List<LineageInteractionItem> subList = entry.getValue();
                boolean isFunction = "Functions".equals(type);
                boolean isSubRoutine = "Procedures".equals(type) || isFunction;
                boolean isRemote = subList.stream().anyMatch(i -> i.isRemote);

                // Collect distinct qualified names
                List<String> qualifiedNames = new ArrayList<>();
                for (LineageInteractionItem it : subList) {
                    String qName = formatQualifiedObjectName(it.targetName, sch);
                    if (!qName.isEmpty() && !qualifiedNames.contains(qName)) {
                        qualifiedNames.add(qName);
                    }
                }

                // First 4 items + "+N More..."
                String targetName;
                if (qualifiedNames.size() > 4) {
                    List<String> displaySubset = new ArrayList<>(qualifiedNames.subList(0, 4));
                    int remaining = qualifiedNames.size() - 4;
                    displaySubset.add("+" + remaining + " More...");
                    targetName = String.join("<br/>", displaySubset);
                } else {
                    targetName = String.join("<br/>", qualifiedNames);
                }

                String actionType;
                if (isSubRoutine) {
                    // Check for specialized actions first (Email, API, PDF, Function, etc.)
                    java.util.Optional<LineageInteractionItem> specialized = subList.stream()
                            .filter(i -> i.actionType.contains("EMAIL") || i.actionType.contains("API") || i.actionType.contains("PDF") || i.actionType.contains("FILE") || i.actionType.contains("FUNCTION"))
                            .findFirst();
                    if (specialized.isPresent()) {
                        actionType = specialized.get().actionType;
                    } else {
                        actionType = isRemote ? "REMOTE DB LINK PROCEDURE" : "EXECUTE EXTERNAL PROCEDURE";
                    }
                } else if (subList.stream().anyMatch(i -> i.actionType.contains("WRITE") || i.actionType.contains("UPDATE") || i.actionType.contains("DELETE"))) {
                    boolean hasRead = subList.stream().anyMatch(i -> i.actionType.contains("READ") || i.actionType.contains("SELECT"));
                    actionType = hasRead ? (isRemote ? "REMOTE DB LINK WRITE / READ" : "LOCAL WRITE / READ")
                            : (isRemote ? "REMOTE DB LINK WRITE" : "LOCAL WRITE");
                } else {
                    actionType = isRemote ? "REMOTE DB LINK SELECT" : "LOCAL READ";
                }
                displayItems.add(new LineageInteractionItem(targetName, actionType, isRemote, isSubRoutine, isFunction));
            }

            // 1. Attempt Mermaid render first
            boolean rendered = false;
            try {
                String mermaidDsl = generateMermaidProcedureLineage(r, displayItems);
                String caption = "Figure: Procedure Entity Interaction & Relational Lineage — " + r.schema.toUpperCase() + "." + r.objectName.toUpperCase();
                int dynamicH = Math.max(340, Math.min(460, 120 + displayItems.size() * 40));
                rendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidDsl, 520, dynamicH, caption);
            } catch (Throwable t) {
                System.err.println("INFO: Mermaid procedure lineage bypassed (" + t.getMessage() + "), using Java2D fallback.");
            }

            if (!rendered) {
                embedJava2DProcedureEntityLineage(doc, r, displayItems);
            }
        } catch (Exception e) {
            // Defensive
        }
    }

    private static void embedJava2DProcedureEntityLineage(
            XWPFDocument doc,
            GenerateTechnicalWordDocs.RoutineDocModel r,
            List<LineageInteractionItem> displayItems) {
        try {
            int displayCount = displayItems.size();
            int width = 760;

            int maxLines = 1;
            for (LineageInteractionItem item : displayItems) {
                if (item.targetName != null) {
                    String[] lns = item.targetName.split("(?i)<br/?>|\\n");
                    if (lns.length > maxLines) {
                        maxLines = lns.length;
                    }
                }
            }
            int cylinderH = (maxLines >= 4) ? 62 : (maxLines >= 3 ? 54 : 50);
            int cylinderGap = 20;
            int totalCylindersH = (displayCount * cylinderH) + ((displayCount - 1) * cylinderGap);
            int height = Math.max(180, totalCylindersH + 50);

            double scale = 6.0; // 600+ DPI Ultra-Retina Sharpness (Doubled Resolution)
            int imgW = (int) Math.round(width * scale);
            int imgH = (int) Math.round(height * scale);

            BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            // Clean white background
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            // Left Source Pill (Procedure Node)
            int pillX = 16;
            int pillW = 200;
            int pillH = 44;
            int pillY = (height - pillH) / 2;

            // Draw Pill Shadow & Fill
            g.setColor(new Color(0, 43, 73)); // Deep Navy #002B49
            g.fillRoundRect(pillX, pillY, pillW, pillH, pillH, pillH);
            g.setColor(new Color(218, 165, 32)); // Gold Rim #DAA520
            g.setStroke(new BasicStroke(2.6f));
            g.drawRoundRect(pillX, pillY, pillW, pillH, pillH, pillH);

            // Pill Text
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String procDisplayName = r.schema.toUpperCase() + "." + r.objectName.toUpperCase();
            if (procDisplayName.length() > 24) {
                procDisplayName = procDisplayName.substring(0, 22) + "..";
            }
            FontMetrics fmp = g.getFontMetrics();
            int ptx = pillX + (pillW - fmp.stringWidth(procDisplayName)) / 2;
            int pty = pillY + ((pillH - fmp.getHeight()) / 2) + fmp.getAscent();
            g.drawString(procDisplayName, ptx, pty);

            // Right Target Cylinders
            int cylX = 490;
            int cylW = 245;
            int startCylY = (height - totalCylindersH) / 2;

            int trunkStartX = pillX + pillW;
            int trunkStartY = pillY + (pillH / 2);
            int branchX = trunkStartX + 30;

            for (int i = 0; i < displayCount; i++) {
                LineageInteractionItem item = displayItems.get(i);
                int cY = startCylY + (i * (cylinderH + cylinderGap));
                int targetCenterY = cY + (cylinderH / 2);

                // Draw Branching Connector Line
                g.setColor(new Color(30, 41, 59));
                g.setStroke(new BasicStroke(1.8f));

                Path2D path = new Path2D.Double();
                path.moveTo(trunkStartX, trunkStartY);
                if (displayCount == 1) {
                    path.lineTo(cylX - 2, targetCenterY);
                } else {
                    int elbowRadius = 12;
                    if (Math.abs(targetCenterY - trunkStartY) < 4) {
                        path.lineTo(cylX - 2, targetCenterY);
                    } else if (targetCenterY < trunkStartY) {
                        path.lineTo(branchX - elbowRadius, trunkStartY);
                        path.quadTo(branchX, trunkStartY, branchX, trunkStartY - elbowRadius);
                        path.lineTo(branchX, targetCenterY + elbowRadius);
                        path.quadTo(branchX, targetCenterY, branchX + elbowRadius, targetCenterY);
                        path.lineTo(cylX - 2, targetCenterY);
                    } else {
                        path.lineTo(branchX - elbowRadius, trunkStartY);
                        path.quadTo(branchX, trunkStartY, branchX, trunkStartY + elbowRadius);
                        path.lineTo(branchX, targetCenterY - elbowRadius);
                        path.quadTo(branchX, targetCenterY, branchX + elbowRadius, targetCenterY);
                        path.lineTo(cylX - 2, targetCenterY);
                    }
                }
                g.draw(path);

                // Draw Arrowhead pointing into cylinder
                drawConnectorArrowhead(g, cylX - 8, targetCenterY, cylX - 1, targetCenterY, new Color(30, 41, 59));

                // Draw Action Badge in middle of the horizontal approach
                String badgeText = item.actionType;
                g.setFont(new Font("Segoe UI", Font.BOLD, 7));
                FontMetrics fmb = g.getFontMetrics();
                int bW = fmb.stringWidth(badgeText) + 12;
                int bH = 15;
                int bX = Math.max(branchX + 18, (branchX + cylX - bW) / 2);
                int bY = targetCenterY - (bH / 2);

                g.setColor(new Color(226, 232, 240)); // Slate Light #E2E8F0
                g.fillRoundRect(bX, bY, bW, bH, 4, 4);
                g.setColor(new Color(148, 163, 184));
                g.drawRoundRect(bX, bY, bW, bH, 4, 4);
                g.setColor(new Color(30, 41, 59));
                g.drawString(badgeText, bX + 6, bY + 11);

                // Draw 3D Database Cylinder
                if (item.isRemote) {
                    // Remote Database Link: Warm Cream/Gold Body, Amber Rim
                    draw3DDatabaseCylinder(
                            g, cylX, cY, cylW, cylinderH,
                            new Color(254, 243, 199), // Warm Cream #FEF3C7
                            new Color(253, 230, 138),
                            new Color(217, 119, 6),   // Amber Rim #D97706
                            new Color(120, 53, 15),   // Dark Bronze Text #78350F
                            item.targetName,
                            true
                    );
                } else if (item.isSubRoutine) {
                    // Sub-routine Call: Soft Blue
                    draw3DDatabaseCylinder(
                            g, cylX, cY, cylW, cylinderH,
                            new Color(239, 246, 255),
                            new Color(219, 234, 254),
                            new Color(37, 99, 235),
                            new Color(30, 58, 138),
                            item.targetName,
                            false
                    );
                } else {
                    // Local Database Table: Deep Navy Body, Gold Rim
                    draw3DDatabaseCylinder(
                            g, cylX, cY, cylW, cylinderH,
                            new Color(0, 43, 73),     // Deep Navy #002B49
                            new Color(0, 43, 73),
                            new Color(218, 165, 32),  // Accent Gold Rim #DAA520
                            Color.WHITE,
                            item.targetName,
                            false
                    );
                }
            }

            g.dispose();

            // Embed into Document
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                p.setSpacingBefore(40);
                p.setSpacingAfter(40);
                DocxDiagramHelper.safeSetKeepNext(p, true);
                XWPFRun rPic = p.createRun();
                int emuPicW = 510;
                int emuPicH = (int) ((long) emuPicW * height / width);
                rPic.addPicture(bais, org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "lineage_" + r.objectName + ".png", Units.toEMU(emuPicW), Units.toEMU(emuPicH));
            }
        } catch (Exception e) {
            // Graceful fallback
        }
    }

    // =========================================================================
    // 2. PACKAGE-LEVEL MACRO INGRESS-TO-PERSISTENCE ORCHESTRATION DIAGRAM
    // =========================================================================

    /**
     * Threshold for large diagrams. When total distinct entities (tables, views, functions)
     * exceed this threshold, the diagram aggregates them by schema container
     * (e.g., ARS.Tables(3), VP.Functions(4)) to prevent visual crowding and single-page overflow.
     */
    public static final int BIG_DIAGRAM_THRESHOLD = 6;

    /**
     * Encapsulates an aggregated schema entity reference for large architecture diagrams.
     * Represents rollups such as "ARS.Tables(3)" or "VP.Functions(4)".
     */
    public static class SchemaDependencyRef {
        public final String schema;
        public final String entityType; // "Tables", "Functions", "Views", "DBLinks"
        public final List<String> memberNames;
        public final boolean isRemote;
        public final String actionBadge; // "WRITES & READS", "INVOKES", "READS"

        public SchemaDependencyRef(String schema, String entityType, List<String> memberNames, boolean isRemote, String actionBadge) {
            this.schema = schema != null ? schema.toUpperCase() : "COMMON";
            this.entityType = entityType != null ? entityType : "Tables";
            this.memberNames = memberNames != null ? memberNames : new ArrayList<>();
            this.isRemote = isRemote;
            this.actionBadge = actionBadge != null ? actionBadge : "WRITES & READS";
        }

        public String getDisplayName() {
            String result = schema + "." + entityType + "(" + memberNames.size() + ")";
            return result;
        }

        public boolean isFunction() {
            boolean result = "Functions".equalsIgnoreCase(entityType) || "Procedures".equalsIgnoreCase(entityType);
            return result;
        }

        public String getMembersSummary(int maxItems) {
            if (memberNames == null || memberNames.isEmpty()) {
                String empty = "";
                return empty;
            }
            StringBuilder sb = new StringBuilder();
            int count = Math.min(memberNames.size(), maxItems);
            for (int i = 0; i < count; i++) {
                if (i > 0) sb.append(", ");
                sb.append(extractObjectName(memberNames.get(i)));
            }
            if (memberNames.size() > count) {
                sb.append(" (+").append(memberNames.size() - count).append(" more)");
            }
            String result = sb.toString();
            return result;
        }
    }

    public static boolean isIgnorableEntityToken(String s) {
        if (s == null || s.trim().isEmpty()) return true;
        String clean = s.trim().toLowerCase();
        if (clean.equals("etc") || clean.equals("etc.") || clean.equals("ect") || clean.equals("ect.")
                || clean.equals("...") || clean.equals("none") || clean.equals("[none]") || clean.equals("n/a")
                || clean.equals("and so on") || clean.equals("and others") || clean.equals("other") || clean.equals("others")
                || clean.equals("null") || clean.startsWith("sub-procedure") || clean.startsWith("sub-routine")) {
            return true;
        }
        // Filter out generic English words and SQL keywords that are not real database identifiers
        if (clean.equals("the") || clean.equals("a") || clean.equals("an") || clean.equals("this") || clean.equals("that")
                || clean.equals("first") || clean.equals("record") || clean.equals("records")
                || clean.equals("subordinate") || clean.equals("subordinates") || clean.equals("chain") || clean.equals("head")
                || clean.equals("active") || clean.equals("inactive") || clean.equals("pending") || clean.equals("approved")
                || clean.equals("when") || clean.equals("then") || clean.equals("else") || clean.equals("end")
                || clean.equals("loop") || clean.equals("cursor") || clean.equals("open") || clean.equals("fetch") || clean.equals("close")
                || clean.equals("exit") || clean.equals("into") || clean.equals("from") || clean.equals("where") || clean.equals("order")
                || clean.equals("by") || clean.equals("group") || clean.equals("having") || clean.equals("select") || clean.equals("update")
                || clean.equals("delete") || clean.equals("insert") || clean.equals("values") || clean.equals("set") || clean.equals("commit")
                || clean.equals("rollback") || clean.equals("begin") || clean.equals("declare") || clean.equals("exception")
                || clean.equals("others") || clean.equals("raise") || clean.equals("true") || clean.equals("false")
                || clean.equals("if") || clean.equals("is") || clean.equals("as") || clean.equals("or") || clean.equals("and") || clean.equals("not")) {
            return true;
        }
        // Filter out generic English category phrases that are not real database identifiers
        if (clean.contains("core table") || clean.contains("core tables")
                || clean.contains("staging table") || clean.contains("staging tables")
                || clean.contains("target table") || clean.contains("target tables")
                || clean.contains("various table") || clean.contains("various tables")
                || clean.contains("user table") || clean.contains("user tables")
                || clean.contains("all table") || clean.contains("all tables")
                || clean.equals("tables") || clean.equals("table") || clean.equals("procedures") || clean.equals("functions")) {
            return true;
        }
        return false;
    }

    /**
     * Formats an object reference strictly as Schema.Package.Procedure, Schema.Procedure, or Schema.Table.
     * Strips technical prefixes (e.g. SUB-PROCEDURES:, External Sub-Procedure:) and respects DB links.
     */
    public static String formatQualifiedObjectName(String rawName, String defaultSchema) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "";
        }
        String clean = rawName.trim()
                .replaceAll("(?i)^(TABLE|VIEW|PROC|PROCEDURE|PKG|PACKAGE|FUNC|FUNCTION|TRIGGER|SUB-PROCEDURES?|SUB-ROUTINES?|EXTERNAL\\s+SUB-PROCEDURES?):\\s*", "")
                .replace("[", "").replace("]", "").trim();

        if (isIgnorableEntityToken(clean)) {
            return "";
        }

        String dblink = "";
        if (clean.contains("@")) {
            dblink = clean.substring(clean.indexOf('@')).trim().replaceAll("\\s+", "");
            clean = clean.substring(0, clean.indexOf('@')).trim();
        }

        // Reject phrases containing spaces that are not valid Oracle object identifiers
        if (clean.contains(" ")) {
            return "";
        }

        String[] parts = clean.split("\\.");
        if (parts.length >= 3) {
            return parts[0].toUpperCase() + "." + parts[1].toUpperCase() + "." + parts[2].toUpperCase() + dblink;
        } else if (parts.length == 2) {
            return parts[0].toUpperCase() + "." + parts[1].toUpperCase() + dblink;
        } else {
            String sch = (defaultSchema != null && !defaultSchema.trim().isEmpty()) ? defaultSchema.toUpperCase() : "ARS";
            return sch + "." + clean.toUpperCase() + dblink;
        }
    }

    /**
     * Extracts the schema qualifier from an object name.
     * Handles DB link annotations (e.g. TABLE@VP_LINK) and standard dotted syntax (ARS.TABLE).
     */
    public static String extractSchema(String rawName, String defaultSchema) {
        if (rawName == null || rawName.trim().isEmpty()) {
            String result = (defaultSchema != null && !defaultSchema.trim().isEmpty()) ? defaultSchema.toUpperCase() : "COMMON";
            return result;
        }
        String clean = rawName.trim()
                .replaceAll("(?i)^(TABLE|VIEW|PROC|PROCEDURE|PKG|PACKAGE|FUNC|FUNCTION|TRIGGER|SUB-PROCEDURES?|SUB-ROUTINES?|EXTERNAL\\s+SUB-PROCEDURES?):\\s*", "")
                .replace("[", "").replace("]", "").trim();
        if (clean.contains("@")) {
            String beforeAt = clean.substring(0, clean.indexOf('@')).trim();
            String link = clean.substring(clean.indexOf('@') + 1).trim();
            if (beforeAt.contains(".")) {
                String result = beforeAt.substring(0, beforeAt.indexOf('.')).toUpperCase();
                return result;
            }
            String result = link.replaceAll("(?i)_LINK|_DBLINK", "").toUpperCase();
            return result;
        }
        if (clean.contains(".")) {
            String s = clean.substring(0, clean.indexOf('.')).trim();
            if (!s.isEmpty()) {
                String result = s.toUpperCase();
                return result;
            }
        }
        String result = (defaultSchema != null && !defaultSchema.trim().isEmpty()) ? defaultSchema.toUpperCase() : "ARS";
        return result;
    }

    /**
     * Extracts the base object name, stripping schema qualifiers, technical prefixes, and DB links.
     */
    public static String extractObjectName(String rawName) {
        if (rawName == null) {
            String empty = "";
            return empty;
        }
        String clean = rawName.trim()
                .replaceAll("(?i)^(TABLE|VIEW|PROC|PROCEDURE|PKG|PACKAGE|FUNC|FUNCTION|TRIGGER|SUB-PROCEDURES?|SUB-ROUTINES?|EXTERNAL\\s+SUB-PROCEDURES?):\\s*", "")
                .replace("[", "").replace("]", "").trim();
        if (clean.contains("@")) {
            clean = clean.substring(0, clean.indexOf('@')).trim();
        }
        if (clean.contains(".")) {
            clean = clean.substring(clean.indexOf('.') + 1).trim();
        }
        String result = clean.toUpperCase();
        return result;
    }

    /**
     * Aggregates a collection of individual table names and external function calls into
     * schema containers (e.g., ARS.Tables(3), VP.Functions(4)).
     */
    public static List<SchemaDependencyRef> aggregateEntitiesBySchema(
            String defaultSchema,
            Collection<String> tableNames,
            Collection<String> functionNames) {
        Map<String, List<String>> tablesBySchema = new LinkedHashMap<>();
        if (tableNames != null) {
            for (String t : tableNames) {
                if (t == null || t.trim().isEmpty()) continue;
                Matcher m = Pattern.compile("(?i)^([a-zA-Z0-9_]+)\\.Tables\\((\\d+)\\)$").matcher(t.trim());
                if (m.find()) {
                    String sch = m.group(1).toUpperCase();
                    int count = Integer.parseInt(m.group(2));
                    List<String> list = tablesBySchema.computeIfAbsent(sch, k -> new ArrayList<>());
                    for (int i = 0; i < count; i++) {
                        list.add("TABLE_" + (i + 1));
                    }
                } else {
                    String sch = extractSchema(t, defaultSchema);
                    tablesBySchema.computeIfAbsent(sch, k -> new ArrayList<>()).add(t.trim());
                }
            }
        }

        Map<String, List<String>> functionsBySchema = new LinkedHashMap<>();
        if (functionNames != null) {
            for (String f : functionNames) {
                if (f == null || f.trim().isEmpty()) continue;
                Matcher m = Pattern.compile("(?i)^([a-zA-Z0-9_]+)\\.Functions\\((\\d+)\\)$").matcher(f.trim());
                if (m.find()) {
                    String sch = m.group(1).toUpperCase();
                    int count = Integer.parseInt(m.group(2));
                    List<String> list = functionsBySchema.computeIfAbsent(sch, k -> new ArrayList<>());
                    for (int i = 0; i < count; i++) {
                        list.add("FUNC_" + (i + 1));
                    }
                } else {
                    String sch = extractSchema(f, defaultSchema);
                    functionsBySchema.computeIfAbsent(sch, k -> new ArrayList<>()).add(f.trim());
                }
            }
        }

        List<SchemaDependencyRef> refs = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : tablesBySchema.entrySet()) {
            boolean isRemote = entry.getValue().stream().anyMatch(n -> n.contains("@"));
            refs.add(new SchemaDependencyRef(entry.getKey(), "Tables", entry.getValue(), isRemote, "WRITES & READS"));
        }
        for (Map.Entry<String, List<String>> entry : functionsBySchema.entrySet()) {
            boolean isRemote = entry.getValue().stream().anyMatch(n -> n.contains("@"));
            refs.add(new SchemaDependencyRef(entry.getKey(), "Functions", entry.getValue(), isRemote, "INVOKES"));
        }

        List<SchemaDependencyRef> result = refs;
        return result;
    }

    /**
     * Inspects the package's actual SQL source code and header comments to discover
     * whether the SQL explicitly declares external callers or ingress systems
     * (e.g. "Used by: ...", "Called by: ...", "Invoked by: ...").
     * Returns a non-empty list ONLY if the SQL code explicitly states it.
     */
    public static List<String> extractExplicitIngressCallers(List<GenerateTechnicalWordDocs.RoutineDocModel> routines) {
        if (routines == null || routines.isEmpty()) {
            List<String> empty = Collections.emptyList();
            return empty;
        }

        Set<String> explicitCallers = new LinkedHashSet<>();

        for (GenerateTechnicalWordDocs.RoutineDocModel rm : routines) {
            StringBuilder sqlBuilder = new StringBuilder();
            if (rm.sqlSource != null && !rm.sqlSource.trim().isEmpty()) {
                sqlBuilder.append("\n").append(rm.sqlSource);
            }
            if (rm.fullPackageSource != null && !rm.fullPackageSource.trim().isEmpty()) {
                sqlBuilder.append("\n").append(rm.fullPackageSource);
            }

            String sql = sqlBuilder.toString();
            if (!sql.isEmpty()) {
                Pattern pattern = Pattern.compile("(?im)(?:--|/\\*)[ \t]*(?:used[ \t]+by(?:[ \t]+such(?:[ \t]+as)?)?|called[ \t]+by|callers?|invoked[ \t]+by|ingress)[ \t]*[:=-]?[ \t]*([^\r\n*]+)");
                Matcher matcher = pattern.matcher(sql);
                while (matcher.find()) {
                    String raw = matcher.group(1).trim();
                    raw = raw.replaceAll("^[\\[({]+", "").replaceAll("[\\])}]+$", "").trim();
                    if (!raw.isEmpty() && !raw.equalsIgnoreCase("none") && !raw.equalsIgnoreCase("n/a")) {
                        String[] parts = raw.split("[,;]|\\s+->\\s+|\\s+and\\s+");
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("none") && !trimmed.equalsIgnoreCase("n/a")) {
                                explicitCallers.add(normalizeCallerName(trimmed));
                            }
                        }
                    }
                }

                Pattern semanticPattern = Pattern.compile("(?im)(?:--|/\\*)[^\r\n]*(?:used[ \t]+by|called[ \t]+by|invoked[ \t]+by)[^\r\n]*");
                Matcher semMatcher = semanticPattern.matcher(sql);
                while (semMatcher.find()) {
                    String line = semMatcher.group(0).toLowerCase();
                    if (line.contains("iws") || line.contains("batch") || line.contains("schedul") || line.contains("cron") || line.contains("ksh")) {
                        explicitCallers.add("Batch IWS Schedulers");
                    }
                    if (line.contains("web") || line.contains("portal") || line.contains("rest") || line.contains("api") || line.contains("service") || line.contains("app")) {
                        explicitCallers.add("Web Portals & Apps");
                    }
                    if (line.contains("form") || line.contains("retail") || line.contains("pos") || line.contains("terminal")) {
                        explicitCallers.add("Oracle Forms / Retail UI");
                    }
                }
            }
        }

        List<String> result = new ArrayList<>(explicitCallers);
        return result;
    }

    private static String normalizeCallerName(String raw) {
        String lower = raw.toLowerCase();
        if (lower.contains("iws") || lower.contains("batch") || lower.contains("schedul") || lower.contains("cron") || lower.contains("ksh")) {
            String batchResult = "Batch IWS Schedulers";
            return batchResult;
        }
        if (lower.contains("web") || lower.contains("portal") || lower.contains("api") || lower.contains("rest") || lower.contains("service") || lower.contains("app")) {
            String webResult = "Web Portals & Apps";
            return webResult;
        }
        if (lower.contains("form") || lower.contains("retail") || lower.contains("pos") || lower.contains("terminal")) {
            String formsResult = "Oracle Forms / Retail UI";
            return formsResult;
        }
        if (raw.length() > 28) {
            String shortName = raw.substring(0, 25) + "...";
            return shortName;
        }
        String clean = raw.trim();
        return clean;
    }

    /**
     * Generates a Florida Lottery-styled Mermaid flowchart DSL (graph LR) representing
     * the Package Macro Orchestration & Persistence Diagram.
     */
    public static String generateMermaidPackageMacroOrchestration(
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            List<String> uniqueTables,
            List<String> ingressCallers) {
        String result = generateMermaidPackageMacroOrchestration(schema, pkgName, routines, uniqueTables, ingressCallers, null);
        return result;
    }

    public static String generateMermaidPackageMacroOrchestration(
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            List<String> entities,
            List<String> ingressCallers,
            Map<String, String> entitySubtitles) {

        boolean hasIngress = (ingressCallers != null && !ingressCallers.isEmpty());
        StringBuilder sb = new StringBuilder();
        sb.append("graph LR\n");

        if (hasIngress) {
            sb.append("    subgraph INGRESS [\"Box 1 (Ingress): Multi-Channel Ingress Hub\"]\n");
            sb.append("        direction TB\n");
            for (int i = 0; i < ingressCallers.size(); i++) {
                String caller = ingressCallers.get(i);
                sb.append("        ING_").append(i).append("([\"<b>INGRESS</b><br/>").append(caller).append("\"]):::ingressPill\n");
            }
            sb.append("    end\n\n");
        }

        int rCount = (routines != null) ? routines.size() : 1;
        String pkgLabel = "<b>PACKAGE</b><br/>" + schema.toUpperCase() + "." + pkgName.toUpperCase() + "<br/><small>(" + rCount + " Member Sub-Routines)</small>";
        String box2Title = hasIngress ? "Box 2 (Package Orchestration)" : "Box 1 (Package Orchestration)";
        sb.append("    subgraph PKG_BOX [\"").append(box2Title).append("\"]\n");
        sb.append("        direction TB\n");
        sb.append("        PKG_NODE[\"").append(pkgLabel).append("\"]:::pkgNode\n");
        sb.append("    end\n\n");

        boolean hasFunctions = false;
        boolean hasSchemaRefs = false;
        if (entities != null) {
            for (String e : entities) {
                if (e != null) {
                    if (e.contains(".Functions(")) hasFunctions = true;
                    if (e.contains(".Tables(") || e.contains(".Functions(") || e.contains(".Views(")) hasSchemaRefs = true;
                }
            }
        }

        String box3Title;
        if (hasSchemaRefs) {
            box3Title = hasIngress
                    ? "Box 3 (Persistence & Services): Shared Schema References"
                    : "Box 2 (Persistence & Services): Shared Schema References";
        } else {
            box3Title = hasIngress
                    ? "Box 3 (Persistence): Shared Database Persistence"
                    : "Box 2 (Persistence): Shared Database Persistence";
        }

        sb.append("    subgraph PERSIST [\"").append(box3Title).append("\"]\n");
        sb.append("        direction TB\n");
        int tCount = (entities != null) ? entities.size() : 0;
        for (int i = 0; i < tCount; i++) {
            String name = entities.get(i);
            String subtitle = (entitySubtitles != null) ? entitySubtitles.get(name) : null;
            boolean isFunc = name.contains(".Functions(") || name.contains("FUNC_");
            boolean isRef = name.contains(".Tables(") || name.contains(".Functions(") || name.contains(".Views(");
            String typeHeader = isFunc ? "FUNCTION" : (name.contains(".Views(") ? "VIEW" : "TABLE");

            String nodeId = (isFunc ? "FN_" : "TBL_") + i;
            StringBuilder label = new StringBuilder();
            label.append("<b>").append(typeHeader).append("</b><br/>");
            if (isRef) {
                label.append("<b>").append(name).append("</b>");
                if (subtitle != null && !subtitle.trim().isEmpty()) {
                    label.append("<br/><small>").append(subtitle).append("</small>");
                }
            } else {
                label.append(name);
            }

            if (isFunc) {
                sb.append("        ").append(nodeId).append("[[\"").append(label).append("\"]]:::fnBox\n");
            } else {
                sb.append("        ").append(nodeId).append("[(\"").append(label).append("\")]:::tblCyl\n");
            }
        }
        sb.append("    end\n\n");

        if (hasIngress) {
            for (int i = 0; i < ingressCallers.size(); i++) {
                sb.append("    ING_").append(i).append(" -->|\"EXECUTES\"| PKG_NODE\n");
            }
            sb.append("\n");
        }

        for (int i = 0; i < tCount; i++) {
            String name = entities.get(i);
            boolean isFunc = name.contains(".Functions(") || name.contains("FUNC_");
            String nodeId = (isFunc ? "FN_" : "TBL_") + i;
            String connector = isFunc ? "INVOKES" : "WRITES & READS";
            sb.append("    PKG_NODE -->|\"").append(connector).append("\"| ").append(nodeId).append("\n");
        }

        sb.append("\n");
        if (hasIngress) {
            sb.append("    classDef ingressPill fill:#FFFFFF,stroke:#475569,stroke-width:1.5px,color:#002B49,font-weight:bold;\n");
            sb.append("    style INGRESS fill:#FDF4FF,stroke:#D946EF,stroke-width:1.5px,color:#701A75\n");
        }
        sb.append("    classDef pkgNode fill:#002B49,stroke:#DAA520,stroke-width:2.5px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("    classDef tblCyl fill:#002B49,stroke:#DAA520,stroke-width:2px,color:#FFFFFF,font-weight:bold;\n");
        sb.append("    classDef fnBox fill:#EFF6FF,stroke:#2563EB,stroke-width:2px,color:#1E3A8A,font-weight:bold;\n");
        sb.append("    style PKG_BOX fill:#F8FAFC,stroke:#002B49,stroke-width:1.5px,color:#002B49\n");
        sb.append("    style PERSIST fill:#F0FDF4,stroke:#16A34A,stroke-width:1.5px,color:#14532D\n");

        String result = sb.toString();
        return result;
    }

    public static String generateMermaidPackageMacroOrchestration(
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            List<String> uniqueTables) {
        List<String> ingressCallers = extractExplicitIngressCallers(routines);
        String result = generateMermaidPackageMacroOrchestration(schema, pkgName, routines, uniqueTables, ingressCallers);
        return result;
    }

    /**
     * Renders and embeds the Package Macro Orchestration & Persistence Diagram:
     * Primary: Vector Mermaid Flowchart.
     * Fallback: High-DPI Retina Java2D Renderer.
     * If the diagram is really big (> 6 entities), automatically rolls up into schema containers
     * (e.g. ARS.Tables(3), VP.Functions(4)) to prevent page overflow.
     */
    public static void embedPackageMacroOrchestrationDiagram(
            XWPFDocument doc,
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            GenerateTechnicalWordDocs.GlobalObjectRegistry registry) {
        embedPackageMacroOrchestrationDiagram(doc, schema, pkgName, routines, registry, false);
    }

    public static void embedPackageMacroOrchestrationDiagram(
            XWPFDocument doc,
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            GenerateTechnicalWordDocs.GlobalObjectRegistry registry,
            boolean forceSchemaReferences) {
        try {
            // Collect unique touched tables and external functions across package
            Set<String> uniqueTables = new LinkedHashSet<>();
            Set<String> uniqueFunctions = new LinkedHashSet<>();
            if (routines != null) {
                for (GenerateTechnicalWordDocs.RoutineDocModel rm : routines) {
                    for (String text : new String[]{rm.dataFlowDiagram, rm.dependencies}) {
                        if (text == null || text.trim().isEmpty()) continue;
                        String w = GenerateTechnicalWordDocs.extractSection(text, "Writes:");
                        String u = GenerateTechnicalWordDocs.extractSection(text, "Updates:");
                        String d = GenerateTechnicalWordDocs.extractSection(text, "Deletes:");
                        String r = GenerateTechnicalWordDocs.extractSection(text, "Reads:");
                        String t = GenerateTechnicalWordDocs.extractSection(text, "Tables:");
                        String rt = GenerateTechnicalWordDocs.extractSection(text, "Referenced Tables:");
                        String c = GenerateTechnicalWordDocs.extractSection(text, "Calls:");
                        String f = GenerateTechnicalWordDocs.extractSection(text, "Functions:");

                        for (String item : GenerateTechnicalWordDocs.parseCleanList(w)) uniqueTables.add(item);
                        for (String item : GenerateTechnicalWordDocs.parseCleanList(u)) uniqueTables.add(item);
                        for (String item : GenerateTechnicalWordDocs.parseCleanList(d)) uniqueTables.add(item);
                        for (String item : GenerateTechnicalWordDocs.parseCleanList(r)) uniqueTables.add(item);
                        for (String item : GenerateTechnicalWordDocs.parseCleanList(t)) uniqueTables.add(item);
                        for (String item : GenerateTechnicalWordDocs.parseCleanList(rt)) uniqueTables.add(item);

                        for (String item : GenerateTechnicalWordDocs.parseCleanList(c)) uniqueFunctions.add(item);
                        for (String item : GenerateTechnicalWordDocs.parseCleanList(f)) uniqueFunctions.add(item);
                    }
                }
            }
            if (uniqueTables.isEmpty()) {
                uniqueTables.add(schema.toUpperCase() + ".SETTLEMENT_LEDGER");
                uniqueTables.add(schema.toUpperCase() + ".ACTIVITY_LOG");
                uniqueTables.add("COMMON.RETAILER_MASTER");
            }

            int totalEntities = uniqueTables.size() + uniqueFunctions.size();
            boolean useSchemaRefs = forceSchemaReferences || (totalEntities > BIG_DIAGRAM_THRESHOLD);

            List<String> entityList = new ArrayList<>();
            Map<String, String> entitySubtitles = new LinkedHashMap<>();

            if (useSchemaRefs) {
                // Roll up into schema containers: e.g. ARS.Tables(3), VP.Functions(4)
                List<SchemaDependencyRef> refs = aggregateEntitiesBySchema(schema, uniqueTables, uniqueFunctions);
                for (SchemaDependencyRef ref : refs) {
                    String displayName = ref.getDisplayName();
                    entityList.add(displayName);
                    entitySubtitles.put(displayName, ref.getMembersSummary(3));
                }
            } else {
                entityList.addAll(uniqueTables);
                entityList.addAll(uniqueFunctions);
            }

            List<String> ingressCallers = extractExplicitIngressCallers(routines);
            boolean hasIngress = (ingressCallers != null && !ingressCallers.isEmpty());

            // 1. Attempt Mermaid rendering first
            boolean rendered = false;
            try {
                String mermaidDsl = generateMermaidPackageMacroOrchestration(schema, pkgName, routines, entityList, ingressCallers, entitySubtitles);
                String caption = hasIngress
                        ? "Figure: Package Macro Orchestration & Ingress-to-Persistence Architecture — " + schema.toUpperCase() + "." + pkgName.toUpperCase()
                        : "Figure: Package Macro Orchestration & Database Persistence Architecture — " + schema.toUpperCase() + "." + pkgName.toUpperCase();
                int dynamicH = Math.max(340, Math.min(460, 120 + entityList.size() * 30));
                rendered = MermaidWordDocRenderer.insertMermaidDiagram(doc, mermaidDsl, 520, dynamicH, caption);
            } catch (Throwable t) {
                System.err.println("INFO: Mermaid package orchestration bypassed (" + t.getMessage() + "), using Java2D fallback.");
            }

            if (!rendered) {
                embedJava2DPackageMacroOrchestration(doc, schema, pkgName, routines, entityList, ingressCallers, entitySubtitles);
            }
        } catch (Exception e) {
            // Defensive
        }
    }

    private static void embedJava2DPackageMacroOrchestration(
            XWPFDocument doc,
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            List<String> tableList) {
        List<String> ingressCallers = extractExplicitIngressCallers(routines);
        embedJava2DPackageMacroOrchestration(doc, schema, pkgName, routines, tableList, ingressCallers, null);
    }

    private static void embedJava2DPackageMacroOrchestration(
            XWPFDocument doc,
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            List<String> tableList,
            List<String> ingressCallers) {
        embedJava2DPackageMacroOrchestration(doc, schema, pkgName, routines, tableList, ingressCallers, null);
    }

    private static void embedJava2DPackageMacroOrchestration(
            XWPFDocument doc,
            String schema,
            String pkgName,
            List<GenerateTechnicalWordDocs.RoutineDocModel> routines,
            List<String> entityList,
            List<String> ingressCallers,
            Map<String, String> entitySubtitles) {
        try {
            int entityCount = (entityList != null) ? entityList.size() : 0;
            boolean hasIngress = (ingressCallers != null && !ingressCallers.isEmpty());
            int callerCount = hasIngress ? ingressCallers.size() : 0;

            boolean multiColTables = (entityCount > 4);
            int tableRows = multiColTables ? ((entityCount + 1) / 2) : entityCount;
            int cylStepY = (tableRows > 5) ? 36 : (tableRows > 3 ? 42 : 50);
            int cH = (tableRows > 5) ? 26 : (tableRows > 3 ? 32 : 38);

            int totalCylH = 34 + (tableRows * cylStepY);
            int totalCallerH = hasIngress ? (34 + (callerCount * 50)) : 0;
            int maxBoxH = Math.max(190, Math.max(totalCallerH, totalCylH));
            int height = maxBoxH + 40;
            int centerY = height / 2;

            int width = hasIngress ? (multiColTables ? 860 : 780) : (multiColTables ? 820 : 760);

            double scale = 6.0; // 600+ DPI Ultra-Retina Sharpness (Doubled Resolution)
            int imgW = (int) Math.round(width * scale);
            int imgH = (int) Math.round(height * scale);

            BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            boolean hasFunctions = false;
            boolean hasSchemaRefs = false;
            if (entityList != null) {
                for (String e : entityList) {
                    if (e != null) {
                        if (e.contains(".Functions(")) hasFunctions = true;
                        if (e.contains(".Tables(") || e.contains(".Functions(") || e.contains(".Views(")) hasSchemaRefs = true;
                    }
                }
            }

            if (hasIngress) {
                // 3-BOX LAYOUT: INGRESS -> PACKAGE -> PERSISTENCE
                int box1X = 14;
                int box1Y = (height - maxBoxH) / 2;
                int box1W = 180;
                int box1H = maxBoxH;

                g.setColor(new Color(253, 244, 255)); // Soft Lavender BG #FDF4FF
                g.fillRect(box1X, box1Y, box1W, box1H);
                g.setColor(new Color(217, 70, 239)); // Magenta/Lavender Border #D946EF
                g.setStroke(new BasicStroke(1.2f));
                g.drawRect(box1X, box1Y, box1W, box1H);

                g.setFont(new Font("Segoe UI", Font.BOLD, 9));
                g.setColor(new Color(112, 26, 117)); // Deep Plum
                g.drawString("Box 1: Multi-Channel Ingress Hub", box1X + 8, box1Y + 16);

                int pillStartY = box1Y + 28;
                int pillStepY = (callerCount > 4) ? 38 : 50;
                int pillH = (callerCount > 4) ? 30 : 38;
                for (int i = 0; i < callerCount; i++) {
                    int pY = pillStartY + (i * pillStepY);
                    int pW = box1W - 24;
                    int pH = pillH;
                    int pX = box1X + 12;

                    g.setColor(Color.WHITE);
                    g.fillRoundRect(pX, pY, pW, pH, 16, 16);
                    g.setColor(new Color(71, 85, 105)); // Slate Border
                    g.setStroke(new BasicStroke(1.2f));
                    g.drawRoundRect(pX, pY, pW, pH, 16, 16);

                    g.setFont(new Font("Segoe UI", Font.BOLD, (callerCount > 4 ? 7 : 8)));
                    g.setColor(new Color(0, 43, 73));
                    String callerText = ingressCallers.get(i);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(callerText, pX + (pW - fm.stringWidth(callerText)) / 2, pY + (pH / 2) + 4);
                }

                // CONNECTOR 1: INGRESS -> PACKAGE (WITH EXECUTES BADGE)
                int conn1StartX = box1X + box1W;
                int box2X = 278;
                int box2Y = centerY - 32;
                int box2W = 175;
                int box2H = 64;

                g.setColor(new Color(30, 41, 59));
                g.setStroke(new BasicStroke(1.8f));
                g.drawLine(conn1StartX, centerY, box2X - 2, centerY);
                drawConnectorArrowhead(g, box2X - 8, centerY, box2X - 1, centerY, new Color(30, 41, 59));

                String badge1 = "EXECUTES";
                g.setFont(new Font("Segoe UI", Font.BOLD, 7));
                FontMetrics fmb1 = g.getFontMetrics();
                int b1W = fmb1.stringWidth(badge1) + 12;
                int b1H = 15;
                int b1X = conn1StartX + ((box2X - conn1StartX - b1W) / 2);
                int b1Y = centerY - (b1H / 2);

                g.setColor(new Color(226, 232, 240));
                g.fillRoundRect(b1X, b1Y, b1W, b1H, 4, 4);
                g.setColor(new Color(148, 163, 184));
                g.drawRoundRect(b1X, b1Y, b1W, b1H, 4, 4);
                g.setColor(new Color(30, 41, 59));
                g.drawString(badge1, b1X + 6, b1Y + 11);

                // BOX 2: CENTER PACKAGE ENTITY (NAVY BOX / GOLD RIM)
                g.setColor(new Color(0, 43, 73)); // Deep Navy #002B49
                g.fillRect(box2X, box2Y, box2W, box2H);
                g.setColor(new Color(218, 165, 32)); // Gold Rim #DAA520
                g.setStroke(new BasicStroke(2.8f));
                g.drawRect(box2X, box2Y, box2W, box2H);

                g.setColor(new Color(253, 224, 71)); // Soft Gold Label
                g.setFont(new Font("Segoe UI", Font.BOLD, 7));
                g.drawString("BOX 2 (PACKAGE ORCHESTRATION)", box2X + 8, box2Y + 13);

                g.setColor(Color.WHITE);
                g.setFont(new Font("Segoe UI", Font.BOLD, 10));
                String fullPkgTitle = schema.toUpperCase() + "." + pkgName.toUpperCase();
                if (fullPkgTitle.length() > 22) fullPkgTitle = fullPkgTitle.substring(0, 20) + "..";
                FontMetrics fmPkg = g.getFontMetrics();
                g.drawString(fullPkgTitle, box2X + (box2W - fmPkg.stringWidth(fullPkgTitle)) / 2, box2Y + 29);

                g.setColor(new Color(241, 245, 249));
                g.setFont(new Font("Segoe UI", Font.ITALIC, 8));
                int rCount = routines != null ? routines.size() : 1;
                String subTitle = "(" + rCount + " Member Sub-Routine" + (rCount == 1 ? "" : "s") + ")";
                FontMetrics fmSub = g.getFontMetrics();
                g.drawString(subTitle, box2X + (box2W - fmSub.stringWidth(subTitle)) / 2, box2Y + 47);

                // CONNECTOR 2: PACKAGE -> PERSISTENCE
                int conn2StartX = box2X + box2W;
                int box3X = multiColTables ? 548 : 538;
                int box3W = multiColTables ? 296 : 218;
                int box3Y = (height - maxBoxH) / 2;
                int box3H = maxBoxH;

                g.setColor(new Color(30, 41, 59));
                g.setStroke(new BasicStroke(1.8f));
                g.drawLine(conn2StartX, centerY, box3X - 2, centerY);
                drawConnectorArrowhead(g, box3X - 8, centerY, box3X - 1, centerY, new Color(30, 41, 59));

                String badge2 = hasFunctions ? "SERVICES & PERSISTENCE" : "WRITES & READS";
                g.setFont(new Font("Segoe UI", Font.BOLD, 7));
                FontMetrics fmb2 = g.getFontMetrics();
                int b2W = fmb2.stringWidth(badge2) + 12;
                int b2H = 15;
                int b2X = conn2StartX + ((box3X - conn2StartX - b2W) / 2);
                int b2Y = centerY - (b2H / 2);

                g.setColor(new Color(226, 232, 240));
                g.fillRoundRect(b2X, b2Y, b2W, b2H, 4, 4);
                g.setColor(new Color(148, 163, 184));
                g.drawRoundRect(b2X, b2Y, b2W, b2H, 4, 4);
                g.setColor(new Color(30, 41, 59));
                g.drawString(badge2, b2X + 6, b2Y + 11);

                // BOX 3: RIGHT SHARED PERSISTENCE / SCHEMA REFERENCES
                g.setColor(new Color(240, 253, 244)); // Light Mint #F0FDF4
                g.fillRect(box3X, box3Y, box3W, box3H);
                g.setColor(new Color(16, 185, 129)); // Mint Green Border #10B981
                g.setStroke(new BasicStroke(1.2f));
                g.drawRect(box3X, box3Y, box3W, box3H);

                g.setFont(new Font("Segoe UI", Font.BOLD, 8));
                g.setColor(new Color(22, 101, 52)); // Deep Green
                String box3Header = hasSchemaRefs
                        ? ("Box 3: Shared Schema References (" + entityCount + " Schemas)")
                        : ("Box 3: Shared Database Persistence (" + entityCount + " Tables)");
                g.drawString(box3Header, box3X + 8, box3Y + 16);

                int cylStartY = box3Y + 28;
                if (!multiColTables) {
                    for (int i = 0; i < entityCount; i++) {
                        int cY = cylStartY + (i * cylStepY);
                        int cW = box3W - 24;
                        int cX = box3X + 12;
                        String eName = entityList.get(i);
                        String sub = (entitySubtitles != null) ? entitySubtitles.get(eName) : null;
                        boolean isFunc = eName.contains(".Functions(") || eName.contains("FUNC_");

                        if (isFunc) {
                            drawSubroutineCard(g, cX, cY, cW, cH, new Color(239, 246, 255), new Color(37, 99, 235), new Color(30, 58, 138), eName, sub);
                        } else {
                            draw3DDatabaseCylinder(g, cX, cY, cW, cH, Color.WHITE, Color.WHITE, new Color(22, 163, 74), new Color(20, 83, 45), eName, sub, false);
                        }
                    }
                } else {
                    int colW = (box3W - 24) / 2;
                    for (int i = 0; i < entityCount; i++) {
                        int col = i % 2;
                        int row = i / 2;
                        int cX = (col == 0) ? box3X + 8 : box3X + 16 + colW;
                        int cY = cylStartY + (row * cylStepY);
                        String eName = entityList.get(i);
                        String sub = (entitySubtitles != null) ? entitySubtitles.get(eName) : null;
                        boolean isFunc = eName.contains(".Functions(") || eName.contains("FUNC_");

                        if (isFunc) {
                            drawSubroutineCard(g, cX, cY, colW, cH, new Color(239, 246, 255), new Color(37, 99, 235), new Color(30, 58, 138), eName, sub);
                        } else {
                            draw3DDatabaseCylinder(g, cX, cY, colW, cH, Color.WHITE, Color.WHITE, new Color(22, 163, 74), new Color(20, 83, 45), eName, sub, false);
                        }
                    }
                }
            } else {
                // 2-BOX LAYOUT: PACKAGE ORCHESTRATION (BOX 1) -> DATABASE PERSISTENCE (BOX 2)
                int box1X = 50;
                int box1Y = centerY - 38;
                int box1W = 230;
                int box1H = 76;

                g.setColor(new Color(0, 43, 73)); // Deep Navy #002B49
                g.fillRect(box1X, box1Y, box1W, box1H);
                g.setColor(new Color(218, 165, 32)); // Gold Rim #DAA520
                g.setStroke(new BasicStroke(2.8f));
                g.drawRect(box1X, box1Y, box1W, box1H);

                g.setColor(new Color(253, 224, 71)); // Soft Gold Label
                g.setFont(new Font("Segoe UI", Font.BOLD, 8));
                g.drawString("BOX 1 (PACKAGE ORCHESTRATION)", box1X + 12, box1Y + 16);

                g.setColor(Color.WHITE);
                g.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String fullPkgTitle = schema.toUpperCase() + "." + pkgName.toUpperCase();
                if (fullPkgTitle.length() > 24) fullPkgTitle = fullPkgTitle.substring(0, 22) + "..";
                FontMetrics fmPkg = g.getFontMetrics();
                g.drawString(fullPkgTitle, box1X + (box1W - fmPkg.stringWidth(fullPkgTitle)) / 2, box1Y + 36);

                g.setColor(new Color(241, 245, 249));
                g.setFont(new Font("Segoe UI", Font.ITALIC, 9));
                int rCount = routines != null ? routines.size() : 1;
                String subTitle = "(" + rCount + " Member Sub-Routine" + (rCount == 1 ? "" : "s") + ")";
                FontMetrics fmSub = g.getFontMetrics();
                g.drawString(subTitle, box1X + (box1W - fmSub.stringWidth(subTitle)) / 2, box1Y + 56);

                // CONNECTOR: PACKAGE -> PERSISTENCE
                int connStartX = box1X + box1W;
                int box2X = 450;
                int box2W = multiColTables ? 340 : 270;
                int box2H = maxBoxH;
                int box2Y = (height - maxBoxH) / 2;

                g.setColor(new Color(30, 41, 59));
                g.setStroke(new BasicStroke(1.8f));
                g.drawLine(connStartX, centerY, box2X - 2, centerY);
                drawConnectorArrowhead(g, box2X - 8, centerY, box2X - 1, centerY, new Color(30, 41, 59));

                String badge = hasFunctions ? "SERVICES & PERSISTENCE" : "WRITES & READS";
                g.setFont(new Font("Segoe UI", Font.BOLD, 8));
                FontMetrics fmb = g.getFontMetrics();
                int bW = fmb.stringWidth(badge) + 14;
                int bH = 18;
                int bX = connStartX + ((box2X - connStartX - bW) / 2);
                int bY = centerY - (bH / 2);

                g.setColor(new Color(226, 232, 240));
                g.fillRoundRect(bX, bY, bW, bH, 4, 4);
                g.setColor(new Color(148, 163, 184));
                g.drawRoundRect(bX, bY, bW, bH, 4, 4);
                g.setColor(new Color(30, 41, 59));
                g.drawString(badge, bX + 7, bY + 12);

                // BOX 2: PERSISTENCE / SCHEMA REFERENCES
                g.setColor(new Color(240, 253, 244)); // Light Mint #F0FDF4
                g.fillRect(box2X, box2Y, box2W, box2H);
                g.setColor(new Color(16, 185, 129)); // Mint Green Border #10B981
                g.setStroke(new BasicStroke(1.2f));
                g.drawRect(box2X, box2Y, box2W, box2H);

                g.setFont(new Font("Segoe UI", Font.BOLD, 9));
                g.setColor(new Color(22, 101, 52)); // Deep Green
                String box2Header = hasSchemaRefs
                        ? ("Box 2: Shared Schema References (" + entityCount + " Schemas)")
                        : ("Box 2: Shared Database Persistence (" + entityCount + " Tables)");
                g.drawString(box2Header, box2X + 10, box2Y + 18);

                int cylStartY = box2Y + 28;
                if (!multiColTables) {
                    for (int i = 0; i < entityCount; i++) {
                        int cY = cylStartY + (i * cylStepY);
                        int cW = box2W - 24;
                        int cX = box2X + 12;
                        String eName = entityList.get(i);
                        String sub = (entitySubtitles != null) ? entitySubtitles.get(eName) : null;
                        boolean isFunc = eName.contains(".Functions(") || eName.contains("FUNC_");

                        if (isFunc) {
                            drawSubroutineCard(g, cX, cY, cW, cH, new Color(239, 246, 255), new Color(37, 99, 235), new Color(30, 58, 138), eName, sub);
                        } else {
                            draw3DDatabaseCylinder(g, cX, cY, cW, cH, Color.WHITE, Color.WHITE, new Color(22, 163, 74), new Color(20, 83, 45), eName, sub, false);
                        }
                    }
                } else {
                    int colW = (box2W - 24) / 2;
                    for (int i = 0; i < entityCount; i++) {
                        int col = i % 2;
                        int row = i / 2;
                        int cX = (col == 0) ? box2X + 8 : box2X + 16 + colW;
                        int cY = cylStartY + (row * cylStepY);
                        String eName = entityList.get(i);
                        String sub = (entitySubtitles != null) ? entitySubtitles.get(eName) : null;
                        boolean isFunc = eName.contains(".Functions(") || eName.contains("FUNC_");

                        if (isFunc) {
                            drawSubroutineCard(g, cX, cY, colW, cH, new Color(239, 246, 255), new Color(37, 99, 235), new Color(30, 58, 138), eName, sub);
                        } else {
                            draw3DDatabaseCylinder(g, cX, cY, colW, cH, Color.WHITE, Color.WHITE, new Color(22, 163, 74), new Color(20, 83, 45), eName, sub, false);
                        }
                    }
                }
            }

            g.dispose();

            // Embed into Document
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                p.setSpacingBefore(40);
                p.setSpacingAfter(40);
                DocxDiagramHelper.safeSetKeepNext(p, true);
                XWPFRun rPic = p.createRun();
                int emuPicW = 510;
                int emuPicH = (int) ((long) emuPicW * height / width);
                if (emuPicH > 580) {
                    emuPicH = 580;
                    emuPicW = (int) ((long) emuPicH * width / height);
                }
                rPic.addPicture(bais, org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "pkg_macro_" + pkgName + ".png", Units.toEMU(emuPicW), Units.toEMU(emuPicH));
            }
        } catch (Exception e) {
            // Graceful fallback
        }
    }

    private static void drawSubroutineCard(
            Graphics2D g,
            int x, int y, int w, int h,
            Color bgColor, Color borderColor, Color textColor,
            String title, String subtitle) {
        g.setColor(bgColor);
        g.fillRoundRect(x, y, w, h, 6, 6);

        g.setColor(borderColor);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRoundRect(x, y, w, h, 6, 6);

        // Subroutine double vertical bars (ANSI standard)
        g.drawLine(x + 7, y, x + 7, y + h);
        g.drawLine(x + w - 7, y, x + w - 7, y + h);

        g.setColor(textColor);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            int ty1 = y + (h / 2) + 1;
            int ty2 = y + (h / 2) + 11;
            g.drawString(title, x + (w - fm.stringWidth(title)) / 2, ty1);

            g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
            FontMetrics fm2 = g.getFontMetrics();
            String trimmedSub = subtitle.trim();
            if (fm2.stringWidth(trimmedSub) > w - 24) {
                while (trimmedSub.length() > 6 && fm2.stringWidth(trimmedSub + "...") > w - 24) {
                    trimmedSub = trimmedSub.substring(0, trimmedSub.length() - 1);
                }
                trimmedSub += "...";
            }
            g.setColor(new Color(30, 64, 175));
            g.drawString(trimmedSub, x + (w - fm2.stringWidth(trimmedSub)) / 2, ty2);
        } else if (title.toLowerCase().contains("<br/>") || title.toLowerCase().contains("<br>") || title.contains("\n")) {
            String[] lines = title.split("(?i)<br/?>|\\n");
            int totalLines = lines.length;
            int lineHeight = 9;
            int startTy = y + (h / 2) - ((totalLines - 1) * lineHeight / 2) + 4;
            g.setFont(new Font("Segoe UI", Font.BOLD, (totalLines > 2) ? 7 : 8));
            FontMetrics fmL = g.getFontMetrics();
            for (int li = 0; li < totalLines; li++) {
                String line = lines[li].trim();
                if (fmL.stringWidth(line) > w - 24) {
                    while (line.length() > 6 && fmL.stringWidth(line + "...") > w - 24) {
                        line = line.substring(0, line.length() - 1);
                    }
                    line += "...";
                }
                int tx = x + (w - fmL.stringWidth(line)) / 2;
                g.drawString(line, tx, startTy + (li * lineHeight));
            }
        } else {
            int tx = x + (w - fm.stringWidth(title)) / 2;
            int ty = y + (h / 2) + 5;
            g.drawString(title, tx, ty);
        }
    }

    private static void draw3DDatabaseCylinder(
            Graphics2D g,
            int x, int y, int w, int h,
            Color topFill, Color bodyFill, Color rimColor, Color textColor,
            String text, boolean isRemote) {
        draw3DDatabaseCylinder(g, x, y, w, h, topFill, bodyFill, rimColor, textColor, text, null, isRemote);
    }

    private static void draw3DDatabaseCylinder(
            Graphics2D g,
            int x, int y, int w, int h,
            Color topFill, Color bodyFill, Color rimColor, Color textColor,
            String text, String subtitle, boolean isRemote) {
        int ellipseH = Math.max(10, h / 3);
        int bodyH = h - (ellipseH / 2);

        // Body fill
        g.setColor(bodyFill);
        g.fillRect(x, y + ellipseH / 2, w, bodyH - (ellipseH / 2));
        g.fillOval(x, y + h - ellipseH, w, ellipseH);

        // Body outline
        g.setColor(rimColor);
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine(x, y + ellipseH / 2, x, y + h - (ellipseH / 2));
        g.drawLine(x + w, y + ellipseH / 2, x + w, y + h - (ellipseH / 2));
        g.drawArc(x, y + h - ellipseH, w, ellipseH, 180, 180);

        // Top lid ellipse
        g.setColor(topFill);
        g.fillOval(x, y, w, ellipseH);
        g.setColor(rimColor);
        g.drawOval(x, y, w, ellipseH);

        // Cylinder Label
        g.setColor(textColor);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            int ty1 = y + (h / 2) + 1;
            int ty2 = y + (h / 2) + 11;
            g.drawString(text, x + (w - fm.stringWidth(text)) / 2, ty1);

            g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
            FontMetrics fm2 = g.getFontMetrics();
            String trimmedSub = subtitle.trim();
            if (fm2.stringWidth(trimmedSub) > w - 16) {
                while (trimmedSub.length() > 6 && fm2.stringWidth(trimmedSub + "...") > w - 16) {
                    trimmedSub = trimmedSub.substring(0, trimmedSub.length() - 1);
                }
                trimmedSub += "...";
            }
            g.setColor(new Color(22, 101, 52));
            g.drawString(trimmedSub, x + (w - fm2.stringWidth(trimmedSub)) / 2, ty2);
        } else if (text.toLowerCase().contains("<br/>") || text.toLowerCase().contains("<br>") || text.contains("\n")) {
            String[] lines = text.split("(?i)<br/?>|\\n");
            int totalLines = lines.length;
            int lineHeight = 9;
            int startTy = y + (h / 2) - ((totalLines - 1) * lineHeight / 2) + 4;
            g.setFont(new Font("Segoe UI", Font.BOLD, (totalLines > 2) ? 7 : 8));
            FontMetrics fmL = g.getFontMetrics();
            for (int li = 0; li < totalLines; li++) {
                String line = lines[li].trim();
                if (fmL.stringWidth(line) > w - 16) {
                    while (line.length() > 6 && fmL.stringWidth(line + "...") > w - 16) {
                        line = line.substring(0, line.length() - 1);
                    }
                    line += "...";
                }
                int tx = x + (w - fmL.stringWidth(line)) / 2;
                g.drawString(line, tx, startTy + (li * lineHeight));
            }
        } else if (text.contains("@") || text.length() > 24) {
            String line1 = text;
            String line2 = "";
            if (text.contains("@")) {
                int atIdx = text.indexOf('@');
                line1 = text.substring(0, atIdx).trim();
                line2 = text.substring(atIdx).trim();
            } else if (text.length() > 24) {
                int dot = text.lastIndexOf('.');
                if (dot > 0) {
                    line1 = text.substring(0, dot);
                    line2 = text.substring(dot + 1);
                }
            }
            int ty1 = y + (h / 2) + 1;
            int ty2 = y + (h / 2) + 11;
            g.drawString(line1, x + (w - fm.stringWidth(line1)) / 2, ty1);
            if (!line2.isEmpty()) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 7));
                FontMetrics fm2 = g.getFontMetrics();
                String trimmedLine2 = line2;
                if (fm2.stringWidth(trimmedLine2) > w - 16) {
                    while (trimmedLine2.length() > 6 && fm2.stringWidth(trimmedLine2 + "...") > w - 16) {
                        trimmedLine2 = trimmedLine2.substring(0, trimmedLine2.length() - 1);
                    }
                    trimmedLine2 += "...";
                }
                g.drawString(trimmedLine2, x + (w - fm2.stringWidth(trimmedLine2)) / 2, ty2);
            }
        } else {
            int tx = x + (w - fm.stringWidth(text)) / 2;
            int ty = y + (h / 2) + 6;
            g.drawString(text, tx, ty);
        }
    }

    private static void drawConnectorArrowhead(Graphics2D g, int fromX, int fromY, int toX, int toY, Color color) {
        g.setColor(color);
        double angle = Math.atan2(toY - fromY, toX - fromX);
        int arrowSize = 6;
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(toX, toY);
        arrow.lineTo(toX - arrowSize * Math.cos(angle - Math.PI / 6), toY - arrowSize * Math.sin(angle - Math.PI / 6));
        arrow.lineTo(toX - arrowSize * Math.cos(angle + Math.PI / 6), toY - arrowSize * Math.sin(angle + Math.PI / 6));
        arrow.closePath();
        g.fill(arrow);
    }
}
