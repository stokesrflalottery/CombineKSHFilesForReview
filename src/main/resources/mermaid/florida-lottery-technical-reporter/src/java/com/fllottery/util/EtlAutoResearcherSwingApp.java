package com.fllottery.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Enterprise Java Swing GUI Dashboard for Florida Lottery ETL Auto-Researcher & Document Generator.
 * <p>
 * Features:
 *  1. Interactive Swing Form for Input/Output Paths, API Keys, Tier Mode, Speed Mode & Target Schemas.
 *  2. Multi-Threaded SwingWorker Execution (Non-blocking background thread execution for batch jobs).
 *  3. Live Streaming Terminal Console with dark-mode syntax styling and auto-scrolling.
 *  4. Real-time Model Quota & Failover Tracker Table monitoring daily RPD and HTTP 429 status.
 *  5. One-Click Workflows: Auto-Research, Unified Word Spec Generation, and Ecosystem World-View Synthesis.
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery DataOps Architecture
 * @since 2026-10-06
 */
public class EtlAutoResearcherSwingApp extends JFrame {

    // Enterprise Brand Colors (Royal Plum & Gold Prestige Palette)
    private static final Color COLOR_PLUM = new Color(92, 29, 72);       // #5C1D48 Royal Plum
    private static final Color COLOR_PLUM_DARK = new Color(59, 16, 46);  // #3B102E Dark Plum
    private static final Color COLOR_GOLD = new Color(218, 165, 32);     // #DAA520 Accent Gold
    private static final Color COLOR_LIGHT_BG = new Color(247, 245, 249); // #F7F5F9 Alabaster-Plum
    private static final Color COLOR_PANEL_BG = new Color(255, 255, 255);
    private static final Color COLOR_CONSOLE_BG = new Color(15, 23, 42);   // Slate Dark
    private static final Color COLOR_CONSOLE_FG = new Color(241, 245, 249); // Soft White

    // Console Syntax Highlighting Attributes (Paid = Yellow, Free = Green)
    private static final SimpleAttributeSet STYLE_DEFAULT = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_FREE = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_PAID = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_CACHE = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_ERROR = new SimpleAttributeSet();

    static {
        StyleConstants.setFontFamily(STYLE_DEFAULT, "Consolas");
        StyleConstants.setFontSize(STYLE_DEFAULT, 12);
        StyleConstants.setForeground(STYLE_DEFAULT, new Color(241, 245, 249)); // Soft White

        StyleConstants.setFontFamily(STYLE_FREE, "Consolas");
        StyleConstants.setFontSize(STYLE_FREE, 12);
        StyleConstants.setBold(STYLE_FREE, true);
        StyleConstants.setForeground(STYLE_FREE, new Color(34, 197, 94)); // Emerald Green (#22C55E)

        StyleConstants.setFontFamily(STYLE_PAID, "Consolas");
        StyleConstants.setFontSize(STYLE_PAID, 12);
        StyleConstants.setBold(STYLE_PAID, true);
        StyleConstants.setForeground(STYLE_PAID, new Color(250, 204, 21)); // Vibrant Gold/Yellow (#FACC15)

        StyleConstants.setFontFamily(STYLE_CACHE, "Consolas");
        StyleConstants.setFontSize(STYLE_CACHE, 12);
        StyleConstants.setForeground(STYLE_CACHE, new Color(148, 163, 184)); // Muted Slate (#94A3B8)

        StyleConstants.setFontFamily(STYLE_ERROR, "Consolas");
        StyleConstants.setFontSize(STYLE_ERROR, 12);
        StyleConstants.setBold(STYLE_ERROR, true);
        StyleConstants.setForeground(STYLE_ERROR, new Color(239, 68, 68)); // Coral Red (#EF4444)
    }

    // Input Form Fields
    private JTextField txtInputPath;
    private JTextField txtSchemasDir;
    private JTextField txtFreeKey;
    private JTextField txtPaidKey;
    private JTextField txtSpendingCap;
    private JComboBox<String> comboTierMode;
    private JComboBox<String> comboSpeedMode;
    private JComboBox<String> comboWordDocMode;
    private JCheckBox chkGenerateWordDocs;
    private JCheckBox chkSynthesizeWorldView;

    // Schema Checkboxes
    private final List<JCheckBox> schemaCheckBoxes = new ArrayList<>();
    private static final String[] DEFAULT_SCHEMAS = {
            "ARS", "COMMON", "CSTS", "DWDOL", "DOLEMP", "GL",
            "INET", "INET2", "LIDS", "LST", "PR", "RETAILER", "STAGE", "TMS", "TRANSLATOR", "VP"
    };

    // Action Control Buttons
    private JButton btnStartResearch;
    private JButton btnGenerateWordDocs;
    private JButton btnSynthesizeWorldView;
    private JButton btnOpenOutputDir;
    private JButton btnDeleteCache;
    private JButton btnDeleteWordDocs;
    private JButton btnStop;
    private JButton btnRefreshQuota;
    private JButton btnClearLog;

    // Timers & State
    private Timer liveQuotaRefreshTimer;

    // Telemetry & Output Controls
    private JProgressBar progressBar;
    private JTextPane txtConsoleLog;
    private JTable tblQuotaTracker;
    private DefaultTableModel quotaTableModel;
    private JLabel lblStatusMessage;
    private JLabel lblTotalCostMessage;

    // Worker State
    private SwingWorker<Void, String> activeWorker;

    public EtlAutoResearcherSwingApp() {
        super("Florida Lottery ETL Auto-Researcher & Document Generator (Swing Enterprise)");
        initUi();
    }

    private void initUi() {
        // Set Native/System Look and Feel for crisp OS rendering
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 820));
        setPreferredSize(new Dimension(1280, 880));
        setLocationRelativeTo(null);

        // Root Container Panel
        JPanel rootPanel = new JPanel(new BorderLayout(8, 8));
        rootPanel.setBackground(COLOR_LIGHT_BG);
        rootPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Header Banner Panel
        rootPanel.add(buildHeaderPanel(), BorderLayout.NORTH);

        // 2. Central Split Pane (Top: Config Form + Quota Table | Bottom: Full-Width Stretched Console)
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildConfigFormPanel(), buildQuotaTrackerPanel());
        topSplitPane.setDividerLocation(540);
        topSplitPane.setResizeWeight(0.50);
        topSplitPane.setBorder(null);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, buildConsolePanel());
        mainSplitPane.setDividerLocation(390);
        mainSplitPane.setResizeWeight(0.48);
        mainSplitPane.setBorder(null);

        rootPanel.add(mainSplitPane, BorderLayout.CENTER);

        // 3. Footer Status Bar
        rootPanel.add(buildFooterPanel(), BorderLayout.SOUTH);

        setContentPane(rootPanel);
        pack();

        // Redirect System.out and System.err to the Live Swing Console
        redirectSystemStreams();

        // Load Initial Quota State
        refreshQuotaTrackerTable();
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COLOR_PLUM_DARK);
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblTitle = new JLabel("FLORIDA LOTTERY ETL AUTO-RESEARCHER & SPECIFICATION GENERATOR");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(COLOR_GOLD);

        JLabel lblSubtitle = new JLabel("Enterprise PL/SQL Routine Extraction, AI Catalog Research & Unified Word (.docx) Documentation");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(226, 232, 240));

        JPanel textStack = new JPanel(new GridLayout(2, 1, 0, 2));
        textStack.setOpaque(false);
        textStack.add(lblTitle);
        textStack.add(lblSubtitle);

        header.add(textStack, BorderLayout.WEST);

        // Header Right Info Badge
        JLabel lblBadge = new JLabel("<html><div style='text-align: right; color: #FFFFFF;'><b>Version:</b> 3.4.0-STOKES<br/><b>Java:</b> 21 | <b>Spring:</b> 3.4.0</div></html>");
        lblBadge.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        header.add(lblBadge, BorderLayout.EAST);

        return header;
    }

    private JPanel buildConfigFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(COLOR_PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Form Fields Container
        JPanel formContainer = new JPanel();
        formContainer.setLayout(new BoxLayout(formContainer, BoxLayout.Y_AXIS));
        formContainer.setOpaque(false);

        // A. File Paths Section
        JPanel pathPanel = new JPanel(new GridBagLayout());
        pathPanel.setOpaque(false);
        pathPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1), " 1. Input & Output Directory Configuration ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // DbCache Input File
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        pathPanel.add(new JLabel("DbCache Source:"), gbc);

        txtInputPath = new JTextField("C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json");
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        pathPanel.add(txtInputPath, gbc);

        JButton btnBrowseInput = new JButton("Browse...");
        styleButton(btnBrowseInput, new Color(241, 245, 249), new Color(15, 23, 42), new Color(148, 163, 184));
        btnBrowseInput.addActionListener(e -> browseFile(txtInputPath, false));
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.0;
        pathPanel.add(btnBrowseInput, gbc);

        // Schemas Output Directory
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        pathPanel.add(new JLabel("Schemas Output:"), gbc);

        txtSchemasDir = new JTextField("C:\\ocioldapps1\\Cached\\DbCache\\Schemas");
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        pathPanel.add(txtSchemasDir, gbc);

        JButton btnBrowseSchemas = new JButton("Browse...");
        styleButton(btnBrowseSchemas, new Color(241, 245, 249), new Color(15, 23, 42), new Color(148, 163, 184));
        btnBrowseSchemas.addActionListener(e -> browseFile(txtSchemasDir, true));
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0.0;
        pathPanel.add(btnBrowseSchemas, gbc);

        formContainer.add(pathPanel);
        formContainer.add(Box.createVerticalStrut(8));

        // B. API & Tier Settings Section
        JPanel tierPanel = new JPanel(new GridBagLayout());
        tierPanel.setOpaque(false);
        tierPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1), " 2. Execution Tier & API Controls ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        // Tier Mode
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        tierPanel.add(new JLabel("Tier Mode:"), gbc);

        comboTierMode = new JComboBox<>(new String[]{
                "HYBRID (Smart Cooldown Routing - Free Priority + Paid Cooldown)",
                "FREE (100% Free Tier - 15 RPM Pacing, $0.00 Cost)",
                "PAID (Prepaid Enterprise - Uncapped Speed)",
                "TURBO (Dual-Lane Async Coordinator)"
        });
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
        tierPanel.add(comboTierMode, gbc);
        gbc.gridwidth = 1;

        // Speed Mode
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        tierPanel.add(new JLabel("Speed Mode:"), gbc);

        comboSpeedMode = new JComboBox<>(new String[]{
                "FASTEST (4x Batching - Maximum Speed & Quota Conservation)",
                "FAST (2x Batching - Balanced Throughput)",
                "NORMAL (1x Batching - 1 Routine Per Request)"
        });
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        tierPanel.add(comboSpeedMode, gbc);
        gbc.gridwidth = 1;

        // Free Key
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        tierPanel.add(new JLabel("Free API Key:"), gbc);

        txtFreeKey = new JTextField("AQ.Ab8RN6JhZRpiwfE-IX2rZK-FLdmTuoPzYjq0jEJRGDNsPE0eUw");
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        tierPanel.add(txtFreeKey, gbc);
        gbc.gridwidth = 1;

        // Paid Key
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        tierPanel.add(new JLabel("Paid API Key:"), gbc);

        txtPaidKey = new JTextField("AQ.Ab8RN6IJFjhJ9M6ud3riQ5zonSyHEjZ_NwYExlZljPmxWmJi3g");
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1.0;
        tierPanel.add(txtPaidKey, gbc);
        gbc.gridwidth = 1;

        // Spending Cap
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0;
        tierPanel.add(new JLabel("Spending Cap ($):"), gbc);

        txtSpendingCap = new JTextField("0.25");
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weightx = 1.0;
        tierPanel.add(txtSpendingCap, gbc);
        gbc.gridwidth = 1;

        formContainer.add(tierPanel);
        formContainer.add(Box.createVerticalStrut(8));

        // C. Target Schemas Selection Section
        JPanel schemaPanel = new JPanel(new GridLayout(4, 4, 4, 4));
        schemaPanel.setOpaque(false);
        schemaPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1), " 3. Target Schemas to Research ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        for (String schema : DEFAULT_SCHEMAS) {
            JCheckBox cb = new JCheckBox(schema, true);
            cb.setOpaque(false);
            schemaCheckBoxes.add(cb);
            schemaPanel.add(cb);
        }

        formContainer.add(schemaPanel);
        formContainer.add(Box.createVerticalStrut(8));

        // D. Word Document Mode Section
        JPanel wordDocPanel = new JPanel(new GridBagLayout());
        wordDocPanel.setOpaque(false);
        wordDocPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1), " 4. Word Document Generation Settings ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        chkGenerateWordDocs = new JCheckBox("Auto-Generate Word (.docx) Specs After Extraction", true);
        chkGenerateWordDocs.setOpaque(false);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
        wordDocPanel.add(chkGenerateWordDocs, gbc);

        chkSynthesizeWorldView = new JCheckBox("Run AI Ecosystem World-View Synthesis (Optional • Uses AI Credits)", false);
        chkSynthesizeWorldView.setOpaque(false);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        wordDocPanel.add(chkSynthesizeWorldView, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        wordDocPanel.add(new JLabel("Doc Mode:"), gbc);

        comboWordDocMode = new JComboBox<>(new String[]{
                "both (Generate Standalone Routine Specs + Master Schema Booklets)",
                "standalone (Generate Individual Procedure/Package Word Specs)",
                "master (Generate Consolidated Schema Master Compendium Booklets)"
        });
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        wordDocPanel.add(comboWordDocMode, gbc);
        gbc.gridwidth = 1;

        formContainer.add(wordDocPanel);

        // Wrap formContainer inside a ScrollPane for smooth scrolling on small screens
        JScrollPane formScrollPane = new JScrollPane(formContainer);
        formScrollPane.setBorder(null);
        formScrollPane.getVerticalScrollBar().setUnitIncrement(12);

        panel.add(formScrollPane, BorderLayout.CENTER);

        // E. Action Control Buttons Bar
        JPanel actionBar = new JPanel(new GridLayout(4, 2, 6, 6));
        actionBar.setOpaque(false);
        actionBar.setBorder(new EmptyBorder(8, 0, 0, 0));

        btnStartResearch = new JButton("Run AI Auto-Research");
        styleButton(btnStartResearch, COLOR_PLUM, Color.WHITE, COLOR_GOLD);
        btnStartResearch.addActionListener(e -> executeAutoResearchWorker());

        btnGenerateWordDocs = new JButton("Generate Word Specs (.docx)");
        styleButton(btnGenerateWordDocs, COLOR_PLUM, Color.WHITE, COLOR_GOLD);
        btnGenerateWordDocs.addActionListener(e -> executeWordDocWorker());

        btnSynthesizeWorldView = new JButton("Synthesize World-View");
        styleButton(btnSynthesizeWorldView, new Color(0, 43, 73), Color.WHITE, new Color(0, 90, 156));
        btnSynthesizeWorldView.addActionListener(e -> executeWorldViewWorker());

        btnOpenOutputDir = new JButton("Open Output Folder");
        styleButton(btnOpenOutputDir, new Color(30, 41, 59), Color.WHITE, new Color(71, 85, 105));
        btnOpenOutputDir.addActionListener(e -> openFolder(txtSchemasDir.getText().trim()));

        btnDeleteCache = new JButton("Purge AI Research Cache");
        styleButton(btnDeleteCache, new Color(130, 25, 25), Color.WHITE, COLOR_GOLD);
        btnDeleteCache.addActionListener(e -> purgeAiResearchCache());

        btnDeleteWordDocs = new JButton("Purge Word Specs Folder");
        styleButton(btnDeleteWordDocs, new Color(130, 25, 25), Color.WHITE, COLOR_GOLD);
        btnDeleteWordDocs.addActionListener(e -> purgeWordDocsFolder());

        btnStop = new JButton("Stop After Current");
        styleButton(btnStop, new Color(180, 40, 40), Color.WHITE, new Color(120, 20, 20));
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> requestCleanStop());

        actionBar.add(btnStartResearch);
        actionBar.add(btnGenerateWordDocs);
        actionBar.add(btnSynthesizeWorldView);
        actionBar.add(btnOpenOutputDir);
        actionBar.add(btnDeleteCache);
        actionBar.add(btnDeleteWordDocs);
        actionBar.add(btnStop);

        panel.add(actionBar, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildQuotaTrackerPanel() {
        JPanel quotaPanel = new JPanel(new BorderLayout(6, 6));
        quotaPanel.setBackground(COLOR_PANEL_BG);
        quotaPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JPanel inner = new JPanel(new BorderLayout(4, 4));
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1), " Active Model Candidate Quota Tracker ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        String[] columnNames = {"Candidate Model", "Limit", "Used", "Remaining", "Status"};
        quotaTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tblQuotaTracker = new JTable(quotaTableModel);
        tblQuotaTracker.setFont(new Font("Consolas", Font.PLAIN, 11));
        tblQuotaTracker.setRowHeight(20);
        tblQuotaTracker.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 1; i < tblQuotaTracker.getColumnCount(); i++) {
            tblQuotaTracker.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane quotaScrollPane = new JScrollPane(tblQuotaTracker);
        inner.add(quotaScrollPane, BorderLayout.CENTER);

        btnRefreshQuota = new JButton("Refresh Quota Cache");
        styleButton(btnRefreshQuota, new Color(30, 41, 59), Color.WHITE, new Color(71, 85, 105));
        btnRefreshQuota.addActionListener(e -> refreshQuotaTrackerTable());
        inner.add(btnRefreshQuota, BorderLayout.SOUTH);

        quotaPanel.add(inner, BorderLayout.CENTER);
        return quotaPanel;
    }

    private JPanel buildConsolePanel() {
        JPanel consolePanel = new JPanel(new BorderLayout(6, 6));
        consolePanel.setBackground(COLOR_PANEL_BG);
        consolePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel inner = new JPanel(new BorderLayout(4, 4));
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1), " Real-Time Telemetry & Console Log (Full-Width View • Green = Free | Yellow = Paid) ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        txtConsoleLog = new JTextPane();
        txtConsoleLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtConsoleLog.setBackground(COLOR_CONSOLE_BG);
        txtConsoleLog.setEditable(false);
        txtConsoleLog.setCaretColor(Color.GREEN);

        JScrollPane consoleScrollPane = new JScrollPane(txtConsoleLog);
        consoleScrollPane.getVerticalScrollBar().setUnitIncrement(12);
        inner.add(consoleScrollPane, BorderLayout.CENTER);

        JPanel consoleBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        consoleBar.setOpaque(false);

        btnClearLog = new JButton("Clear Console Log");
        styleButton(btnClearLog, new Color(30, 41, 59), Color.WHITE, new Color(71, 85, 105));
        btnClearLog.addActionListener(e -> txtConsoleLog.setText(""));
        consoleBar.add(btnClearLog);

        inner.add(consoleBar, BorderLayout.SOUTH);
        consolePanel.add(inner, BorderLayout.CENTER);
        return consolePanel;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setBackground(COLOR_PLUM_DARK);
        footer.setBorder(new EmptyBorder(8, 12, 8, 12));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progressBar.setPreferredSize(new Dimension(300, 22));

        lblStatusMessage = new JLabel("System Ready.");
        lblStatusMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusMessage.setForeground(Color.WHITE);

        lblTotalCostMessage = new JLabel("Spent: $0.0000 USD");
        lblTotalCostMessage.setFont(new Font("Consolas", Font.BOLD, 12));
        lblTotalCostMessage.setForeground(COLOR_GOLD);

        JPanel statusStack = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        statusStack.setOpaque(false);
        statusStack.add(lblStatusMessage);
        statusStack.add(lblTotalCostMessage);

        footer.add(statusStack, BorderLayout.WEST);
        footer.add(progressBar, BorderLayout.EAST);

        return footer;
    }

    // =========================================================================
    // EXECUTION WORKERS (SwingWorker Background Threads)
    // =========================================================================

    private void executeAutoResearchWorker() {
        if (activeWorker != null && !activeWorker.isDone()) {
            JOptionPane.showMessageDialog(this, "A background execution worker is already running!", "Worker Active", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setControlsEnabled(false);
        startLiveQuotaTimer();
        progressBar.setIndeterminate(true);
        progressBar.setString("Running AI Stored Procedure Auto-Research...");
        lblStatusMessage.setText("Executing ExtractAndResearchProcsInventory...");

        activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<String> argsList = new ArrayList<>();
                argsList.add("-input=" + txtInputPath.getText().trim());
                argsList.add("-schemasDir=" + txtSchemasDir.getText().trim());
                argsList.add("-freeKey=" + txtFreeKey.getText().trim());
                argsList.add("-paidKey=" + txtPaidKey.getText().trim());
                argsList.add("-spendingCap=" + txtSpendingCap.getText().trim());

                // Selected Schemas
                List<String> selectedSchemas = new ArrayList<>();
                for (JCheckBox cb : schemaCheckBoxes) {
                    if (cb.isSelected()) selectedSchemas.add(cb.getText());
                }
                if (!selectedSchemas.isEmpty()) {
                    argsList.add("-schemas=" + String.join(",", selectedSchemas));
                }

                // Tier Mode
                String selectedTier = (String) comboTierMode.getSelectedItem();
                if (selectedTier != null) {
                    if (selectedTier.startsWith("FREE")) argsList.add("-tier=FREE");
                    else if (selectedTier.startsWith("PAID")) argsList.add("-tier=PAID");
                    else if (selectedTier.startsWith("TURBO")) argsList.add("-tier=TURBO");
                    else argsList.add("-tier=HYBRID");
                }

                // Speed Mode
                String selectedSpeed = (String) comboSpeedMode.getSelectedItem();
                if (selectedSpeed != null) {
                    if (selectedSpeed.startsWith("NORMAL")) argsList.add("-speed=NORMAL");
                    else if (selectedSpeed.startsWith("FAST (")) argsList.add("-speed=FAST");
                    else argsList.add("-speed=FASTEST");
                }

                // Word Doc Generation
                if (chkGenerateWordDocs.isSelected()) {
                    argsList.add("-generateWordDocs=true");
                    String docMode = (String) comboWordDocMode.getSelectedItem();
                    if (docMode != null) {
                        argsList.add("-wordDocMode=" + docMode.split(" ")[0]);
                    }
                }
                argsList.add("-synthesizeWorldView=" + chkSynthesizeWorldView.isSelected());

                System.out.println("\n=========================================================================");
                System.out.println("[GUI WORKER] Launching ExtractAndResearchProcsInventory with arguments:");
                System.out.println(String.join(" ", argsList));
                System.out.println("=========================================================================\n");

                // Execute core Java Auto-Researcher
                ExtractAndResearchProcsInventory.main(argsList.toArray(new String[0]));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    lblStatusMessage.setText("Auto-Research Pipeline Completed Successfully!");
                    JOptionPane.showMessageDialog(EtlAutoResearcherSwingApp.this, "Auto-Research and Catalog Extraction Completed Successfully!", "Pipeline Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    lblStatusMessage.setText("Execution Error: " + ex.getCause().getMessage());
                    JOptionPane.showMessageDialog(EtlAutoResearcherSwingApp.this, "Pipeline Execution Error: " + ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    stopLiveQuotaTimer();
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Completed 100%");
                    setControlsEnabled(true);
                }
            }
        };

        activeWorker.execute();
    }

    private void executeWordDocWorker() {
        if (activeWorker != null && !activeWorker.isDone()) {
            JOptionPane.showMessageDialog(this, "A background execution worker is already running!", "Worker Active", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setControlsEnabled(false);
        startLiveQuotaTimer();
        progressBar.setIndeterminate(true);
        progressBar.setString("Generating Unified Technical Word Specs (.docx)...");
        lblStatusMessage.setText("Executing GenerateUnifiedTechnicalWordDocs...");

        activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                String schemasDir = txtSchemasDir.getText().trim();
                String inputPath = txtInputPath.getText().trim();
                String catalogPath = schemasDir + "\\DbCache_Ai_Researched_Catalog.json";
                String outputDir = schemasDir + "\\Word_Docs";

                String mode = "both";
                String selectedDocMode = (String) comboWordDocMode.getSelectedItem();
                if (selectedDocMode != null) mode = selectedDocMode.split(" ")[0];

                if (chkSynthesizeWorldView.isSelected()) {
                    System.out.println("\n=========================================================================");
                    System.out.println("[Triggering Ecosystem World-View Synthesizer before Word Doc generation...]");
                    System.out.println("=========================================================================\n");
                    List<String> wvArgs = new ArrayList<>();
                    wvArgs.add("-catalog=" + catalogPath);
                    wvArgs.add("-dbCache=" + inputPath);
                    wvArgs.add("-output=" + schemasDir + "\\Enterprise_Ecosystem_WorldView.json");
                    wvArgs.add("-schemasDir=" + schemasDir);
                    SynthesizeEcosystemWorldView.main(wvArgs.toArray(new String[0]));
                }

                List<String> argsList = new ArrayList<>();
                argsList.add("-input=" + inputPath);
                argsList.add("-catalog=" + catalogPath);
                argsList.add("-outputDir=" + outputDir);
                argsList.add("-mode=" + mode);

                System.out.println("\n=========================================================================");
                System.out.println("[GUI WORKER] Launching GenerateUnifiedTechnicalWordDocs with arguments:");
                System.out.println(String.join(" ", argsList));
                System.out.println("=========================================================================\n");

                GenerateUnifiedTechnicalWordDocs.main(argsList.toArray(new String[0]));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lblStatusMessage.setText("Word Document Generation Completed!");
                    JOptionPane.showMessageDialog(EtlAutoResearcherSwingApp.this, "Unified Word Technical Specifications (.docx) Generated Successfully!", "Doc Generation Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    lblStatusMessage.setText("Doc Generation Error: " + ex.getCause().getMessage());
                    JOptionPane.showMessageDialog(EtlAutoResearcherSwingApp.this, "Word Doc Generation Error: " + ex.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    stopLiveQuotaTimer();
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Completed 100%");
                    setControlsEnabled(true);
                }
            }
        };

        activeWorker.execute();
    }

    private void executeWorldViewWorker() {
        if (activeWorker != null && !activeWorker.isDone()) {
            JOptionPane.showMessageDialog(this, "A background execution worker is already running!", "Worker Active", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setControlsEnabled(false);
        startLiveQuotaTimer();
        progressBar.setIndeterminate(true);
        progressBar.setString("Synthesizing Ecosystem World-View JSON...");
        lblStatusMessage.setText("Executing SynthesizeEcosystemWorldView...");

        activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                String schemasDir = txtSchemasDir.getText().trim();
                String inputPath = txtInputPath.getText().trim();
                String catalogPath = schemasDir + "\\DbCache_Ai_Researched_Catalog.json";
                String outputPath = schemasDir + "\\Enterprise_Ecosystem_WorldView.json";

                List<String> argsList = new ArrayList<>();
                argsList.add("-catalog=" + catalogPath);
                argsList.add("-dbCache=" + inputPath);
                argsList.add("-output=" + outputPath);
                argsList.add("-schemasDir=" + schemasDir);

                System.out.println("\n=========================================================================");
                System.out.println("[GUI WORKER] Launching SynthesizeEcosystemWorldView with arguments:");
                System.out.println(String.join(" ", argsList));
                System.out.println("=========================================================================\n");

                SynthesizeEcosystemWorldView.main(argsList.toArray(new String[0]));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lblStatusMessage.setText("Ecosystem World-View Synthesized!");
                    JOptionPane.showMessageDialog(EtlAutoResearcherSwingApp.this, "Ecosystem World-View JSON Synthesized Successfully!", "World-View Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    lblStatusMessage.setText("World-View Error: " + ex.getCause().getMessage());
                    JOptionPane.showMessageDialog(EtlAutoResearcherSwingApp.this, "World-View Error: " + ex.getMessage(), "World-View Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    stopLiveQuotaTimer();
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Completed 100%");
                    setControlsEnabled(true);
                }
            }
        };

        activeWorker.execute();
    }

    // =========================================================================
    // HELPER UTILITIES
    // =========================================================================

    private void refreshQuotaTrackerTable() {
        quotaTableModel.setRowCount(0);
        String schemasDir = txtSchemasDir.getText().trim();
        File quotaFile = new File(schemasDir + "\\Ai_Model_Quota_Tracker.json");

        ExtractAndResearchProcsInventory.QuotaTrackerData data = ExtractAndResearchProcsInventory.getQuotaTrackerData();
        if (data == null || data.models == null || data.models.isEmpty()) {
            if (quotaFile.exists()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    ExtractAndResearchProcsInventory.initQuotaTracker(quotaFile, mapper);
                    data = ExtractAndResearchProcsInventory.getQuotaTrackerData();
                } catch (Exception ex) {
                    quotaTableModel.addRow(new Object[]{"Quota Load Error", "-", "-", "-", ex.getMessage()});
                    return;
                }
            } else {
                quotaTableModel.addRow(new Object[]{"No Quota File", "-", "-", "-", "UNINITIALIZED"});
                return;
            }
        }

        if (data != null && data.models != null) {
            for (ExtractAndResearchProcsInventory.ModelQuotaEntry entry : data.models.values()) {
                String status = entry.status != null ? entry.status : "READY";
                quotaTableModel.addRow(new Object[]{
                        entry.modelName,
                        String.format("%,d RPD", entry.quotaLimit),
                        String.format("%,d req", entry.requestsToday),
                        String.format("%,d req", entry.remainingQuota),
                        status
                });
            }
        }

        double spentUsd = ExtractAndResearchProcsInventory.getTotalCostInUsd();
        lblTotalCostMessage.setText(String.format("Spent: $%.4f USD", spentUsd));
    }

    private void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                appendConsoleLog(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendConsoleLog(new String(b, off, len));
            }
        };

        PrintStream ps = new PrintStream(out, true);
        System.setOut(ps);
        System.setErr(ps);
    }

    private final StringBuilder lineBuffer = new StringBuilder();

    private synchronized void appendConsoleLog(String text) {
        lineBuffer.append(text);
        int lineEnd;
        while ((lineEnd = lineBuffer.indexOf("\n")) != -1) {
            String line = lineBuffer.substring(0, lineEnd + 1);
            lineBuffer.delete(0, lineEnd + 1);
            printStyledText(line);
        }
        if (lineBuffer.length() > 300) {
            String remaining = lineBuffer.toString();
            lineBuffer.setLength(0);
            printStyledText(remaining);
        }
    }

    private void printStyledText(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = txtConsoleLog.getStyledDocument();
                AttributeSet style = STYLE_DEFAULT;
                String upper = text.toUpperCase();

                if (upper.contains("[HYBRID: PAID") || upper.contains("PAID TIER") || upper.contains("PAID KEY") || upper.contains("TOTAL SPENT:") || upper.contains("SPENT:")) {
                    style = STYLE_PAID;
                } else if (upper.contains("[HYBRID: FREE") || upper.contains("FREE TIER") || upper.contains("FREE RATE:") || upper.contains("FREE KEY")) {
                    style = STYLE_FREE;
                } else if (upper.contains("[CACHE HIT]")) {
                    style = STYLE_CACHE;
                } else if (upper.contains("ERROR") || upper.contains("CRITICAL") || upper.contains("ACTION REQUIRED") || upper.contains("FAILED") || upper.contains("EXCEPTION")) {
                    style = STYLE_ERROR;
                }

                doc.insertString(doc.getLength(), text, style);
                txtConsoleLog.setCaretPosition(doc.getLength());

                // 1. REAL-TIME SPENT DOLLAR UPDATES
                double liveSpentUsd = ExtractAndResearchProcsInventory.getTotalCostInUsd();
                if (liveSpentUsd > 0.0) {
                    lblTotalCostMessage.setText(String.format("Spent: $%.4f USD", liveSpentUsd));
                } else if (text.contains("Total Spent: $")) {
                    int idx = text.indexOf("Total Spent: $");
                    int endIdx = text.indexOf(" ", idx + 14);
                    if (endIdx == -1) endIdx = text.indexOf("|", idx + 14);
                    if (endIdx == -1) endIdx = text.length();
                    String dollarStr = text.substring(idx + 14, endIdx).replace("|", "").trim();
                    try {
                        double spentVal = Double.parseDouble(dollarStr);
                        lblTotalCostMessage.setText(String.format("Spent: $%.4f USD", spentVal));
                    } catch (Exception ignored) {}
                } else if (text.contains("TOTAL API COST:")) {
                    int idx = text.indexOf("$");
                    if (idx != -1) {
                        int endIdx = text.indexOf(" ", idx + 1);
                        if (endIdx == -1) endIdx = text.length();
                        String dollarStr = text.substring(idx + 1, endIdx).trim();
                        try {
                            double spentVal = Double.parseDouble(dollarStr);
                            lblTotalCostMessage.setText(String.format("Spent: $%.4f USD", spentVal));
                        } catch (Exception ignored) {}
                    }
                }

                // 2. REAL-TIME OVERALL PROGRESS PERCENTAGE UPDATES
                if (text.contains("Progress: ")) {
                    int pIdx = text.indexOf("Progress: ");
                    int endP = text.indexOf(" ", pIdx + 10);
                    if (endP == -1) endP = text.indexOf("|", pIdx + 10);
                    if (endP != -1) {
                        String ratio = text.substring(pIdx + 10, endP).trim();
                        String[] parts = ratio.split("/");
                        if (parts.length == 2) {
                            try {
                                int curr = Integer.parseInt(parts[0].trim());
                                int tot = Integer.parseInt(parts[1].trim());
                                int pct = (int) Math.round((curr * 100.0) / tot);
                                progressBar.setIndeterminate(false);
                                progressBar.setValue(pct);
                                progressBar.setString(String.format("Progress: %d%% (%d/%d Routines)", pct, curr, tot));
                            } catch (Exception ignored) {}
                        }
                    }
                } else if (text.contains("Quota: ")) {
                    int qIdx = text.indexOf("Quota: ");
                    int endQ = text.indexOf(" RPD", qIdx);
                    if (endQ != -1) {
                        String ratio = text.substring(qIdx + 7, endQ).trim();
                        String[] parts = ratio.split("/");
                        if (parts.length == 2) {
                            try {
                                int used = Integer.parseInt(parts[0].trim());
                                int limit = Integer.parseInt(parts[1].trim());
                                int pct = (int) Math.round((used * 100.0) / limit);
                                progressBar.setIndeterminate(false);
                                progressBar.setValue(pct);
                                progressBar.setString(String.format("Progress: %d%% (Daily Quota Used: %d/%d RPD)", pct, used, limit));
                            } catch (Exception ignored) {}
                        }
                    }
                } else if (text.contains("Chunk ")) {
                    int cIdx = text.indexOf("Chunk ");
                    int endC = text.indexOf(" ", cIdx + 6);
                    if (endC == -1) endC = text.indexOf(")", cIdx + 6);
                    if (endC != -1) {
                        String chunkStr = text.substring(cIdx + 6, endC).trim();
                        String[] parts = chunkStr.split("/");
                        if (parts.length == 2) {
                            try {
                                int curr = Integer.parseInt(parts[0].trim());
                                int tot = Integer.parseInt(parts[1].trim());
                                int pct = (int) Math.round((curr * 100.0) / tot);
                                progressBar.setIndeterminate(false);
                                progressBar.setValue(pct);
                                progressBar.setString(String.format("Progress: %d%% (Chunk %d/%d)", pct, curr, tot));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void setControlsEnabled(boolean enabled) {
        btnStartResearch.setEnabled(enabled);
        btnGenerateWordDocs.setEnabled(enabled);
        btnSynthesizeWorldView.setEnabled(enabled);
        btnRefreshQuota.setEnabled(enabled);
        btnOpenOutputDir.setEnabled(enabled);
        if (btnDeleteCache != null) btnDeleteCache.setEnabled(enabled);
        if (btnDeleteWordDocs != null) btnDeleteWordDocs.setEnabled(enabled);

        btnStop.setEnabled(!enabled);
        if (enabled) {
            btnStop.setText("Stop After Current");
        }
    }

    private void requestCleanStop() {
        ExtractAndResearchProcsInventory.stopRequested = true;
        GenerateUnifiedTechnicalWordDocs.stopRequested = true;
        btnStop.setText("Stopping After Current...");
        btnStop.setEnabled(false);
        lblStatusMessage.setText("Stop requested — waiting for current routine/doc to complete...");
        System.out.println("\n[GUI ACTION] USER REQUESTED CLEAN STOP — Process will halt cleanly after current routine/doc finishes.\n");
    }

    private void startLiveQuotaTimer() {
        if (liveQuotaRefreshTimer == null) {
            liveQuotaRefreshTimer = new Timer(2500, e -> refreshQuotaTrackerTable());
        }
        liveQuotaRefreshTimer.start();
    }

    private void stopLiveQuotaTimer() {
        if (liveQuotaRefreshTimer != null) {
            liveQuotaRefreshTimer.stop();
        }
        refreshQuotaTrackerTable();
    }

    private void purgeAiResearchCache() {
        String schemasDir = txtSchemasDir.getText().trim();
        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to PURGE the AI Research Catalog Cache?\n\n" +
                "Target Directory: " + schemasDir + "\n\n" +
                "This will permanently delete:\n" +
                " • DbCache_Ai_Researched_Catalog.json\n" +
                " • DbCache_Ai_Researched_Catalog.backup.json\n" +
                " • Enterprise_Ecosystem_WorldView.json\n" +
                " • DbCache_Ecosystem_WorldView.json\n" +
                " • DbCache_Ecosystem_WorldView.partial.json\n" +
                " • Ai_Model_Quota_Tracker.json\n\n" +
                "Next run will re-extract and re-research all routines from scratch.",
                "Confirm Purge AI Research Cache",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            try {
                int deletedCount = 0;
                String[] targetFiles = {
                        "DbCache_Ai_Researched_Catalog.json",
                        "DbCache_Ai_Researched_Catalog.backup.json",
                        "Enterprise_Ecosystem_WorldView.json",
                        "DbCache_Ecosystem_WorldView.json",
                        "DbCache_Ecosystem_WorldView.partial.json",
                        "Ai_Model_Quota_Tracker.json"
                };

                for (String fName : targetFiles) {
                    File f = new File(schemasDir + "\\" + fName);
                    if (f.exists() && f.delete()) {
                        deletedCount++;
                    }
                }

                lblStatusMessage.setText("Purged " + deletedCount + " AI research cache files.");
                refreshQuotaTrackerTable();
                JOptionPane.showMessageDialog(this, "AI Research Catalog Cache Purged Successfully!\n(" + deletedCount + " cache files removed)", "Cache Purged", JOptionPane.INFORMATION_MESSAGE);
                System.out.println("\n[GUI ACTION] PURGED " + deletedCount + " AI Research Cache files from: " + schemasDir + "\n");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error purging cache: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void purgeWordDocsFolder() {
        String schemasDir = txtSchemasDir.getText().trim();
        File wordDocsDir = new File(schemasDir + "\\Word_Docs");

        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to PURGE all generated Word (.docx) specifications?\n\n" +
                "Target Directory: " + wordDocsDir.getAbsolutePath() + "\n\n" +
                "This will permanently delete all .docx specification documents.\n" +
                "This action cannot be undone.",
                "Confirm Purge Word Docs Folder",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            try {
                int deletedCount = deleteFolderContents(wordDocsDir);
                lblStatusMessage.setText("Purged " + deletedCount + " generated Word (.docx) specifications.");
                JOptionPane.showMessageDialog(this, "Word Specs Folder Purged Successfully!\n(" + deletedCount + " .docx files removed)", "Word Docs Purged", JOptionPane.INFORMATION_MESSAGE);
                System.out.println("\n[GUI ACTION] PURGED " + deletedCount + " .docx files from: " + wordDocsDir.getAbsolutePath() + "\n");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error purging Word docs: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private int deleteFolderContents(File folder) {
        int count = 0;
        if (folder == null || !folder.exists()) return 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    count += deleteFolderContents(f);
                    f.delete();
                } else if (f.getName().toLowerCase().endsWith(".docx") || f.getName().toLowerCase().endsWith(".tmp")) {
                    if (f.delete()) count++;
                }
            }
        }
        return count;
    }

    private void browseFile(JTextField targetField, boolean isDirectory) {
        JFileChooser chooser = new JFileChooser(targetField.getText().trim());
        chooser.setFileSelectionMode(isDirectory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void openFolder(String path) {
        try {
            File folder = new File(path);
            if (folder.exists()) {
                Desktop.getDesktop().open(folder);
            } else {
                JOptionPane.showMessageDialog(this, "Directory does not exist yet: " + path, "Directory Missing", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open folder: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void styleButton(JButton btn, Color bg, Color fg, Color border) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = c.getWidth();
                int h = c.getHeight();

                if (!btn.isEnabled()) {
                    g2.setColor(new Color(226, 232, 240));
                    g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
                    g2.setColor(new Color(148, 163, 184));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
                    g2.setColor(new Color(148, 163, 184));
                } else {
                    if (btn.getModel().isPressed()) {
                        g2.setColor(bg.darker());
                    } else if (btn.getModel().isRollover()) {
                        g2.setColor(bg.brighter());
                    } else {
                        g2.setColor(bg);
                    }
                    g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

                    g2.setColor(border != null ? border : bg.darker());
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

                    g2.setColor(fg);
                }

                FontMetrics fm = g2.getFontMetrics(btn.getFont());
                Rectangle stringBounds = fm.getStringBounds(btn.getText(), g2).getBounds();
                int textX = (w - stringBounds.width) / 2;
                int textY = (h - stringBounds.height) / 2 + fm.getAscent() - 2;

                g2.setFont(btn.getFont());
                g2.drawString(btn.getText(), textX, textY);
                g2.dispose();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EtlAutoResearcherSwingApp app = new EtlAutoResearcherSwingApp();
            app.setVisible(true);
        });
    }
}
