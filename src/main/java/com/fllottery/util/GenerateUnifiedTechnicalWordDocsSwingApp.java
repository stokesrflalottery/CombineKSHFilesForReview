package com.fllottery.util;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Enterprise Java Swing GUI Application for Florida Lottery Unified Technical Word Spec Generator.
 * <p>
 * Provides an interactive UI for configuring paths, modes, diagram orientation, and launching batch
 * Word document generation with live console streaming and progress telemetry.
 * </p>
 *
 * @author Ryan Stokes (Programmer Analyst III) & Florida Lottery DataOps Architecture
 * @since 2026-10-06
 */
public class GenerateUnifiedTechnicalWordDocsSwingApp extends JFrame {

    // Enterprise Brand Colors (Royal Plum Prestige Palette)
    private static final Color COLOR_PLUM = new Color(92, 29, 72);       // #5C1D48 Royal Plum
    private static final Color COLOR_PLUM_DARK = new Color(59, 16, 46);  // #3B102E Dark Plum
    private static final Color COLOR_GOLD = new Color(218, 165, 32);     // #DAA520 Accent Gold
    private static final Color COLOR_LIGHT_BG = new Color(247, 245, 249); // #F7F5F9 Alabaster-Plum
    private static final Color COLOR_CONSOLE_BG = new Color(15, 23, 42);   // Slate Dark (#0F172A)
    private static final Color COLOR_CONSOLE_FG = new Color(241, 245, 249); // Soft White

    // Console Syntax Highlighting Attributes
    private static final SimpleAttributeSet STYLE_DEFAULT = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_FREE = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_PAID = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_CACHE = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_ERROR = new SimpleAttributeSet();

    static {
        StyleConstants.setFontFamily(STYLE_DEFAULT, "Consolas");
        StyleConstants.setFontSize(STYLE_DEFAULT, 12);
        StyleConstants.setForeground(STYLE_DEFAULT, new Color(241, 245, 249));

        StyleConstants.setFontFamily(STYLE_FREE, "Consolas");
        StyleConstants.setFontSize(STYLE_FREE, 12);
        StyleConstants.setBold(STYLE_FREE, true);
        StyleConstants.setForeground(STYLE_FREE, new Color(34, 197, 94)); // Green (#22C55E)

        StyleConstants.setFontFamily(STYLE_PAID, "Consolas");
        StyleConstants.setFontSize(STYLE_PAID, 12);
        StyleConstants.setBold(STYLE_PAID, true);
        StyleConstants.setForeground(STYLE_PAID, new Color(250, 204, 21)); // Yellow (#FACC15)

        StyleConstants.setFontFamily(STYLE_CACHE, "Consolas");
        StyleConstants.setFontSize(STYLE_CACHE, 12);
        StyleConstants.setForeground(STYLE_CACHE, new Color(148, 163, 184)); // Slate

        StyleConstants.setFontFamily(STYLE_ERROR, "Consolas");
        StyleConstants.setFontSize(STYLE_ERROR, 12);
        StyleConstants.setBold(STYLE_ERROR, true);
        StyleConstants.setForeground(STYLE_ERROR, new Color(239, 68, 68)); // Red
    }

    // Input Fields
    private JTextField txtInputPath;
    private JTextField txtCatalogPath;
    private JTextField txtOutputDir;
    private JTextField txtBaseUrl;
    private JTextField txtDatabase;
    private JComboBox<String> comboMode;
    private JComboBox<String> comboOrientation;
    private JCheckBox chkConsolidateTables;
    private JCheckBox chkSynthesizeWorldView;

    // Action Control Buttons
    private JButton btnGenerate;
    private JButton btnOpenOutputDir;
    private JButton btnStop;
    private JButton btnClearConsole;
    private JButton btnCopyLog;

    // Telemetry & Output Controls
    private JProgressBar progressBar;
    private JTextPane txtConsoleLog;
    private JLabel lblStatusMessage;

    // Background Worker State
    private SwingWorker<Void, String> activeWorker;

    public GenerateUnifiedTechnicalWordDocsSwingApp() {
        super("Florida Lottery - Unified Technical Specification (.docx) Generator");
        initUi();
    }

    private void initUi() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 780));
        setPreferredSize(new Dimension(1180, 840));
        setLocationRelativeTo(null);

        JPanel rootPanel = new JPanel(new BorderLayout(8, 8));
        rootPanel.setBackground(COLOR_LIGHT_BG);
        rootPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Header Banner Panel
        rootPanel.add(buildHeaderPanel(), BorderLayout.NORTH);

        // 2. Central Split Pane (Controls Top/Left, Console Log Bottom/Right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.42);
        splitPane.setDividerLocation(340);

        JPanel controlsPanel = new JPanel(new BorderLayout(8, 8));
        controlsPanel.setBackground(COLOR_LIGHT_BG);

        JPanel formGrid = new JPanel(new GridLayout(1, 2, 8, 8));
        formGrid.setBackground(COLOR_LIGHT_BG);
        formGrid.add(buildPathsPanel());
        formGrid.add(buildSettingsPanel());

        controlsPanel.add(formGrid, BorderLayout.CENTER);
        controlsPanel.add(buildActionBar(), BorderLayout.SOUTH);

        splitPane.setTopComponent(controlsPanel);
        splitPane.setBottomComponent(buildConsolePanel());

        rootPanel.add(splitPane, BorderLayout.CENTER);

        // 3. Status Bar Footer
        rootPanel.add(buildFooterPanel(), BorderLayout.SOUTH);

        setContentPane(rootPanel);
        pack();

        // Redirect System.out and System.err to live GUI terminal log
        redirectSystemStreams();
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COLOR_PLUM_DARK);
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblTitle = new JLabel("UNIFIED TECHNICAL SPECIFICATION WORD (.DOCX) GENERATOR");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(COLOR_GOLD);

        JLabel lblSubtitle = new JLabel("Florida Lottery DataOps Architecture • Web Team Formal Specification & Java2D Pipeline");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(220, 210, 225));

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        titleBox.add(lblTitle);
        titleBox.add(lblSubtitle);

        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildPathsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1),
                " Data Source & Output File Paths ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtInputPath = new JTextField("C:\\ocioldapps1\\Cached\\DbCache\\DbCache.json");
        txtCatalogPath = new JTextField("C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\DbCache_Ai_Researched_Catalog.json");
        txtOutputDir = new JTextField("C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\Word_Docs_Unified");

        addFileRow(panel, gbc, 0, "DbCache JSON File:", txtInputPath, true, false);
        addFileRow(panel, gbc, 1, "AI Catalog JSON File:", txtCatalogPath, true, false);
        addFileRow(panel, gbc, 2, "Output Root Directory:", txtOutputDir, true, true);

        return panel;
    }

    private void addFileRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField txtField, boolean browseButton, boolean isDirectory) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txtField.setFont(new Font("Consolas", Font.PLAIN, 11));
        panel.add(txtField, gbc);

        if (browseButton) {
            gbc.gridx = 2; gbc.weightx = 0.0;
            JButton btnBrowse = new JButton("Browse...");
            styleButton(btnBrowse, new Color(241, 245, 249), new Color(15, 23, 42), new Color(148, 163, 184));
            btnBrowse.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                if (isDirectory) {
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if (!txtField.getText().isEmpty()) chooser.setSelectedFile(new File(txtField.getText()));
                } else {
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    if (!txtField.getText().isEmpty()) chooser.setSelectedFile(new File(txtField.getText()));
                }
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    txtField.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            });
            panel.add(btnBrowse, gbc);
        }
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PLUM, 1),
                " Document Formatting & Execution Settings ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), COLOR_PLUM
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        comboMode = new JComboBox<>(new String[]{
                "both - Individual & Consolidated Specs",
                "individual - Routines/Tables Specs Only",
                "master - Consolidated Master Specs Only"
        });

        comboOrientation = new JComboBox<>(new String[]{
                "vertical - High-Density Single Page Flow (Default)",
                "horizontal - Wide Architectural Layout"
        });

        txtBaseUrl = new JTextField("https://flalottery.sharepoint.com/sites/DataOps/TechDocs/");
        txtDatabase = new JTextField("DEVOLTP");
        chkConsolidateTables = new JCheckBox("Consolidate Tables (Alphabetical Partitioning)", true);
        chkConsolidateTables.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkConsolidateTables.setBackground(Color.WHITE);

        chkSynthesizeWorldView = new JCheckBox("Synthesize Ecosystem World-View JSON First (Optional • Uses AI Credits)", false);
        chkSynthesizeWorldView.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkSynthesizeWorldView.setBackground(Color.WHITE);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        panel.add(new JLabel("Doc Generation Mode:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        panel.add(comboMode, gbc); gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("Diagram Flow Layout:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        panel.add(comboOrientation, gbc); gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        panel.add(new JLabel("SharePoint Base URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        panel.add(txtBaseUrl, gbc); gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        panel.add(new JLabel("Default Database Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        panel.add(txtDatabase, gbc); gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        panel.add(chkConsolidateTables, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3;
        panel.add(chkSynthesizeWorldView, gbc);

        return panel;
    }

    private JPanel buildActionBar() {
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        actionBar.setBackground(COLOR_LIGHT_BG);

        btnGenerate = new JButton("Generate Word Specs (.docx)");
        styleButton(btnGenerate, COLOR_PLUM, Color.WHITE, COLOR_GOLD);
        btnGenerate.addActionListener(e -> executeGeneratorWorker());

        btnOpenOutputDir = new JButton("Open Output Folder");
        styleButton(btnOpenOutputDir, new Color(0, 43, 73), Color.WHITE, new Color(0, 90, 156));
        btnOpenOutputDir.addActionListener(e -> openOutputDirectory());

        btnClearConsole = new JButton("Clear Console");
        styleButton(btnClearConsole, new Color(30, 41, 59), Color.WHITE, new Color(71, 85, 105));
        btnClearConsole.addActionListener(e -> txtConsoleLog.setText(""));

        btnCopyLog = new JButton("Copy Log");
        styleButton(btnCopyLog, new Color(30, 41, 59), Color.WHITE, new Color(71, 85, 105));
        btnCopyLog.addActionListener(e -> {
            StringSelection selection = new StringSelection(txtConsoleLog.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            JOptionPane.showMessageDialog(this, "Console log copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
        });

        btnStop = new JButton("Stop After Current");
        styleButton(btnStop, new Color(180, 40, 40), Color.WHITE, new Color(120, 20, 20));
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> requestCleanStop());

        actionBar.add(btnClearConsole);
        actionBar.add(btnCopyLog);
        actionBar.add(btnOpenOutputDir);
        actionBar.add(btnStop);
        actionBar.add(btnGenerate);

        return actionBar;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_CONSOLE_BG);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
                " Live Execution & Generation Terminal Log (Full-Width View • Green = Free | Yellow = Paid) ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Consolas", Font.BOLD, 12), COLOR_GOLD
        ));

        txtConsoleLog = new JTextPane();
        txtConsoleLog.setEditable(false);
        txtConsoleLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtConsoleLog.setBackground(COLOR_CONSOLE_BG);
        txtConsoleLog.setCaretColor(Color.GREEN);

        JScrollPane scrollPane = new JScrollPane(txtConsoleLog);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(8, 4));
        footer.setBackground(COLOR_PLUM_DARK);
        footer.setBorder(new EmptyBorder(6, 12, 6, 12));

        lblStatusMessage = new JLabel("Ready to generate unified technical Word specifications.");
        lblStatusMessage.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblStatusMessage.setForeground(Color.WHITE);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(280, 18));
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");

        footer.add(lblStatusMessage, BorderLayout.CENTER);
        footer.add(progressBar, BorderLayout.EAST);

        return footer;
    }

    private void executeGeneratorWorker() {
        if (activeWorker != null && !activeWorker.isDone()) {
            JOptionPane.showMessageDialog(this, "A document generation job is already running!", "Job Running", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String inputPath = txtInputPath.getText().trim();
        String catalogPath = txtCatalogPath.getText().trim();
        String outputDir = txtOutputDir.getText().trim();

        File catalogFile = new File(catalogPath);
        if (!catalogFile.exists()) {
            JOptionPane.showMessageDialog(this, "AI Catalog JSON not found at:\n" + catalogPath, "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setControlsEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Generating Word Specs...");
        lblStatusMessage.setText("Executing GenerateUnifiedTechnicalWordDocs...");

        activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                String selectedMode = (String) comboMode.getSelectedItem();
                String mode = selectedMode != null ? selectedMode.split(" ")[0] : "both";

                String selectedOrient = (String) comboOrientation.getSelectedItem();
                String orientation = selectedOrient != null ? selectedOrient.split(" ")[0] : "vertical";

                String baseUrl = txtBaseUrl.getText().trim();
                String dbName = txtDatabase.getText().trim();
                boolean consolidateTables = chkConsolidateTables.isSelected();

                if (chkSynthesizeWorldView.isSelected()) {
                    File catalogF = new File(catalogPath);
                    File parentDir = catalogF.getParentFile();
                    String schemasDir = parentDir != null ? parentDir.getAbsolutePath() : "C:\\ocioldapps1\\Cached\\DbCache\\Schemas";

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
                argsList.add("-diagramOrientation=" + orientation);
                argsList.add("-baseUrl=" + baseUrl);
                argsList.add("-db=" + dbName);
                argsList.add("-consolidateTables=" + consolidateTables);

                System.out.println("\n=========================================================================");
                System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] LAUNCHING UNIFIED TECHNICAL WORD SPEC GENERATOR");
                System.out.println("Arguments: " + String.join(" ", argsList));
                System.out.println("=========================================================================\n");

                GenerateUnifiedTechnicalWordDocs.main(argsList.toArray(new String[0]));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lblStatusMessage.setText("Word Document Generation Completed Successfully!");
                    JOptionPane.showMessageDialog(GenerateUnifiedTechnicalWordDocsSwingApp.this,
                            "Unified Technical Word Specs (.docx) Generated Successfully!\nOutput Folder:\n" + outputDir,
                            "Generation Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    lblStatusMessage.setText("Generation Error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    JOptionPane.showMessageDialog(GenerateUnifiedTechnicalWordDocsSwingApp.this,
                            "Error generating Word specifications:\n" + ex.getMessage(),
                            "Generation Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Completed 100%");
                    setControlsEnabled(true);
                }
            }
        };

        activeWorker.execute();
    }

    private void openOutputDirectory() {
        try {
            File dir = new File(txtOutputDir.getText().trim());
            if (!dir.exists()) dir.mkdirs();
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open output folder: " + ex.getMessage(), "Error Opening Folder", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setControlsEnabled(boolean enabled) {
        btnGenerate.setEnabled(enabled);
        btnOpenOutputDir.setEnabled(enabled);
        comboMode.setEnabled(enabled);
        comboOrientation.setEnabled(enabled);
        chkConsolidateTables.setEnabled(enabled);

        btnStop.setEnabled(!enabled);
        if (enabled) {
            btnStop.setText("Stop After Current");
        }
    }

    private void requestCleanStop() {
        GenerateUnifiedTechnicalWordDocs.stopRequested = true;
        btnStop.setText("Stopping After Current...");
        btnStop.setEnabled(false);
        lblStatusMessage.setText("Stop requested — waiting for current document to complete...");
        System.out.println("\n[GUI ACTION] USER REQUESTED CLEAN STOP — Document generation will halt cleanly after current document finishes.\n");
    }

    private void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                appendConsole(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendConsole(new String(b, off, len));
            }
        };

        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }

    private final StringBuilder lineBuffer = new StringBuilder();

    private synchronized void appendConsole(String text) {
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
            } catch (Exception ignored) {}
        });
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
            GenerateUnifiedTechnicalWordDocsSwingApp frame = new GenerateUnifiedTechnicalWordDocsSwingApp();
            frame.setVisible(true);
        });
    }
}
