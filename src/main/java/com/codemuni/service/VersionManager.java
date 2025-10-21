package com.codemuni.service;

import com.codemuni.utils.AppConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Handles version checking for eMark.
 * Supports async callbacks, clickable labels, and manual check buttons.
 */
public class VersionManager {

    public static final String GITHUB_RELEASES_LATEST = "https://github.com/devcodemuni/eMark/releases/latest";
    private static final Log log = LogFactory.getLog(VersionManager.class);
    private static final int TIMEOUT_MS = 5000; // 5 seconds

    /**
     * Checks if a newer version is available on GitHub.
     *
     * @param currentVersion Current app version (e.g., "V1.0.1" or "1.0.1")
     * @return true if a newer version exists
     */
    public static boolean isUpdateAvailable(String currentVersion) {
        String latestVersion = getLatestVersion();
        if (latestVersion == null) {
            return false;
        }

        // Normalize both versions by removing v/V prefix
        String normalizedLatest = normalizeVersion(latestVersion);
        String normalizedCurrent = normalizeVersion(currentVersion);

        log.info("Latest GitHub version: " + latestVersion + " (normalized: " + normalizedLatest + ")");
        log.info("Current app version: " + currentVersion + " (normalized: " + normalizedCurrent + ")");

        int comparison = compareVersions(normalizedLatest, normalizedCurrent);
        log.info("Version comparison result: " + comparison + " (>0 means update available)");

        return comparison > 0;
    }

    /**
     * Fetches the latest version from GitHub releases.
     *
     * @return Latest version string (e.g., "V1.0.2") or null if unable to fetch
     */
    public static String getLatestVersion() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_RELEASES_LATEST).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.connect();

            int responseCode = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            conn.disconnect();

            if (responseCode != 302 && responseCode != 301) {
                log.warn("Unexpected response code: " + responseCode + " (expected redirect)");
                return null;
            }

            if (location == null || location.trim().isEmpty()) {
                log.warn("No redirect location found for latest release check.");
                return null;
            }

            // Extract version from URL (e.g., .../releases/tag/V1.0.2)
            if (location.endsWith("/")) {
                location = location.substring(0, location.length() - 1);
            }

            return location.substring(location.lastIndexOf("/") + 1);

        } catch (Exception e) {
            log.error("Failed to fetch latest version: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetches release notes for the latest version from GitHub API.
     *
     * @param version Version tag (e.g., "V1.1.2")
     * @return Release notes/changelog or null if unable to fetch
     */
    public static String getReleaseNotes(String version) {
        if (version == null || version.trim().isEmpty()) {
            return null;
        }

        try {
            String apiUrl = "https://api.github.com/repos/devcodemuni/eMark/releases/tags/" + version;
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.warn("Failed to fetch release notes. Response code: " + responseCode);
                conn.disconnect();
                return null;
            }

            // Read the response
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            // Parse JSON to extract the body (release notes)
            String jsonResponse = response.toString();
            String body = extractJsonField(jsonResponse, "body");

            if (body != null && !body.trim().isEmpty()) {
                // Clean up the body - remove excessive newlines and format for display
                body = body.replace("\\r\\n", "\n").replace("\\n", "\n");
                body = body.replaceAll("\n{3,}", "\n\n"); // Max 2 consecutive newlines
                return body.trim();
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to fetch release notes: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Simple JSON field extractor (avoids dependency on JSON library).
     *
     * @param json  JSON string
     * @param field Field name to extract
     * @return Field value or null if not found
     */
    private static String extractJsonField(String json, String field) {
        try {
            String searchKey = "\"" + field + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) {
                return null;
            }

            startIndex += searchKey.length();
            // Skip whitespace
            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            // Check if value is a string (starts with quote)
            if (startIndex < json.length() && json.charAt(startIndex) == '"') {
                startIndex++; // Skip opening quote
                StringBuilder value = new StringBuilder();
                boolean escaped = false;

                for (int i = startIndex; i < json.length(); i++) {
                    char c = json.charAt(i);

                    if (escaped) {
                        value.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                        value.append(c);
                    } else if (c == '"') {
                        // End of string
                        return value.toString();
                    } else {
                        value.append(c);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to extract JSON field: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Normalizes a version string by removing v/V prefix and trimming whitespace.
     *
     * @param version Version string (e.g., "V1.0.1", "v1.0.1", "1.0.1")
     * @return Normalized version string (e.g., "1.0.1")
     */
    private static String normalizeVersion(String version) {
        if (version == null) return "";

        version = version.trim();

        // Remove v/V prefix
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }

        return version.trim();
    }

    /**
     * Compares two semantic version strings: "1.0.0", "1.2.3"
     * Supports any number of version parts (e.g., "1.0", "1.0.0", "1.0.0.1")
     *
     * @param v1 First version (must be normalized - no v/V prefix)
     * @param v2 Second version (must be normalized - no v/V prefix)
     * @return -1 if v1<v2, 0 if v1=v2, 1 if v1>v2
     */
    private static int compareVersions(String v1, String v2) {
        if (v1 == null || v1.trim().isEmpty()) return -1;
        if (v2 == null || v2.trim().isEmpty()) return 1;

        String[] a1 = v1.split("\\.");
        String[] a2 = v2.split("\\.");
        int len = Math.max(a1.length, a2.length);

        for (int i = 0; i < len; i++) {
            int n1 = i < a1.length ? parseIntSafe(a1[i]) : 0;
            int n2 = i < a2.length ? parseIntSafe(a2[i]) : 0;

            if (n1 != n2) {
                return n1 > n2 ? 1 : -1;
            }
        }
        return 0;
    }

    /**
     * Safely parses an integer from a string, returning 0 if parsing fails.
     * Handles edge cases like "1-beta", "1rc1" by extracting leading digits.
     *
     * @param str String to parse
     * @return Parsed integer or 0 if parsing fails
     */
    private static int parseIntSafe(String str) {
        if (str == null || str.trim().isEmpty()) {
            return 0;
        }

        try {
            // Try direct parsing first (handles normal cases like "1", "10", "123")
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            // Extract leading digits for cases like "1-beta", "2rc1"
            StringBuilder digits = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break; // Stop at first non-digit
                }
            }

            if (digits.length() > 0) {
                try {
                    return Integer.parseInt(digits.toString());
                } catch (NumberFormatException ex) {
                    return 0;
                }
            }
            return 0;
        }
    }

    /**
     * Async version check.
     *
     * @param callback called on EDT with true if update available
     */
    public static void checkUpdateAsync(final VersionCheckCallback callback) {
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return isUpdateAvailable(AppConstants.APP_VERSION);
            }

            @Override
            protected void done() {
                boolean updateAvailable = false;
                try {
                    updateAvailable = get();
                } catch (Exception e) {
                    log.error("Error during async version check", e);
                }
                if (callback != null) {
                    callback.onResult(updateAvailable);
                }
            }
        };
        worker.execute();
    }

    /**
     * Async version check with release info.
     * Fetches both update availability and latest version in background.
     *
     * @param callback called on EDT with update availability and version
     */
    public static void checkUpdateWithInfoAsync(final UpdateInfoCallback callback) {
        SwingWorker<UpdateInfo, Void> worker = new SwingWorker<UpdateInfo, Void>() {
            @Override
            protected UpdateInfo doInBackground() {
                boolean updateAvailable = isUpdateAvailable(AppConstants.APP_VERSION);
                String latestVersion = updateAvailable ? getLatestVersion() : null;
                return new UpdateInfo(updateAvailable, latestVersion);
            }

            @Override
            protected void done() {
                UpdateInfo info = null;
                try {
                    info = get();
                } catch (Exception e) {
                    log.error("Error during async version check with info", e);
                    info = new UpdateInfo(false, null);
                }
                if (callback != null) {
                    callback.onResult(info);
                }
            }
        };
        worker.execute();
    }

    /**
     * Shows update dialog asynchronously with loading state.
     * Fetches release notes in background and displays dialog when ready.
     *
     * @param parent        Parent component for the dialog
     * @param latestVersion Latest version to fetch notes for
     */
    public static void showUpdateDialogAsync(final Component parent, final String latestVersion) {
        // Create loading dialog with proper parent window handling
        Window parentWindow = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        final JDialog loadingDialog;
        if (parentWindow instanceof Frame) {
            loadingDialog = new JDialog((Frame) parentWindow, "Checking Update Details", true);
        } else if (parentWindow instanceof Dialog) {
            loadingDialog = new JDialog((Dialog) parentWindow, "Checking Update Details", true);
        } else {
            loadingDialog = new JDialog((Frame) null, "Checking Update Details", true);
        }

        JPanel loadingPanel = new JPanel(new BorderLayout(10, 10));
        loadingPanel.setBackground(new Color(45, 45, 45));
        loadingPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JLabel loadingLabel = new JLabel("<html><div style='text-align:center;'>"
                + "<p style='font-size:12pt; color:#DDDDDD;'>Loading update information...</p>"
                + "</div></html>");
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(250, 20));

        loadingPanel.add(loadingLabel, BorderLayout.CENTER);
        loadingPanel.add(progressBar, BorderLayout.SOUTH);

        loadingDialog.add(loadingPanel);
        loadingDialog.pack();

        // Center on screen or relative to parent window
        loadingDialog.setLocationRelativeTo(parentWindow);

        loadingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // Fetch release notes in background
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // Give it a moment to show the loading dialog
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }

            @Override
            protected void done() {
                loadingDialog.dispose();
                showUpdateDialog(parent, latestVersion);
            }
        };

        worker.execute();

        // Show loading dialog (blocks until worker completes)
        SwingUtilities.invokeLater(() -> loadingDialog.setVisible(true));
    }

    /**
     * Shows an update dialog with download option and release notes.
     *
     * @param parent        Parent component for the dialog
     * @param latestVersion Latest version available (e.g., "V1.1.2")
     */
    public static void showUpdateDialog(Component parent, String latestVersion) {
        String currentVersion = AppConstants.APP_VERSION;
        String displayLatest = latestVersion != null ? latestVersion : "Unknown";

        // Fetch release notes
        String releaseNotes = latestVersion != null ? getReleaseNotes(latestVersion) : null;

        // Create custom dialog for better control
        Window parentWindow = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        JDialog dialog;
        if (parentWindow instanceof Frame) {
            dialog = new JDialog((Frame) parentWindow, "New Update Available", true);
        } else if (parentWindow instanceof Dialog) {
            dialog = new JDialog((Dialog) parentWindow, "New Update Available", true);
        } else {
            dialog = new JDialog((Frame) null, "New Update Available", true);
        }
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(true);

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(45, 45, 45));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 15, 20));

        JLabel titleLabel = new JLabel("<html><div style='text-align:center;'>"
                + "<p style='font-size:16pt; font-weight:bold; color:#4CAF50; margin:0;'>"
                + "\u2728 New Version Available!</p>"
                + "<p style='font-size:10pt; color:#AAAAAA; margin-top:8px;'>"
                + "A newer version of eMark is ready for download</p>"
                + "</div></html>");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        // Version info panel
        JPanel versionPanel = new JPanel(new GridLayout(2, 2, 10, 8));
        versionPanel.setBackground(new Color(35, 35, 35));
        versionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70), 1),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));

        JLabel currentLabel = new JLabel("Current Version:");
        currentLabel.setForeground(new Color(160, 160, 160));
        currentLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JLabel currentValueLabel = new JLabel(currentVersion);
        currentValueLabel.setForeground(new Color(220, 220, 220));
        currentValueLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JLabel latestLabel = new JLabel("Latest Version:");
        latestLabel.setForeground(new Color(160, 160, 160));
        latestLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JLabel latestValueLabel = new JLabel(displayLatest);
        latestValueLabel.setForeground(new Color(76, 175, 80));
        latestValueLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        versionPanel.add(currentLabel);
        versionPanel.add(currentValueLabel);
        versionPanel.add(latestLabel);
        versionPanel.add(latestValueLabel);

        // Changelog panel
        JPanel changelogPanel = new JPanel(new BorderLayout(0, 8));
        changelogPanel.setBackground(new Color(45, 45, 45));
        changelogPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));

        JLabel changelogTitle = new JLabel("What's New:");
        changelogTitle.setForeground(new Color(220, 220, 220));
        changelogTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        changelogPanel.add(changelogTitle, BorderLayout.NORTH);

        if (releaseNotes != null && !releaseNotes.trim().isEmpty()) {
            JTextArea changelogArea = new JTextArea(releaseNotes);
            changelogArea.setEditable(false);
            changelogArea.setLineWrap(true);
            changelogArea.setWrapStyleWord(true);
            changelogArea.setBackground(new Color(35, 35, 35));
            changelogArea.setForeground(new Color(200, 200, 200));
            changelogArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
            changelogArea.setMargin(new java.awt.Insets(10, 10, 10, 10));
            changelogArea.setCaretPosition(0);

            JScrollPane scrollPane = new JScrollPane(changelogArea);
            scrollPane.setPreferredSize(new Dimension(500, 180));
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);

            changelogPanel.add(scrollPane, BorderLayout.CENTER);
        } else {
            JLabel noNotesLabel = new JLabel("<html><i>Release notes not available. Visit GitHub for details.</i></html>");
            noNotesLabel.setForeground(new Color(150, 150, 150));
            noNotesLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
            changelogPanel.add(noNotesLabel, BorderLayout.CENTER);
        }

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setBackground(new Color(45, 45, 45));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 20, 15, 20));

        JButton laterButton = new JButton("Remind Me Later");
        laterButton.setPreferredSize(new Dimension(140, 32));
        laterButton.setFocusPainted(false);

        JButton downloadButton = new JButton("Download Now");
        downloadButton.setPreferredSize(new Dimension(140, 32));
        downloadButton.setBackground(new Color(76, 175, 80));
        downloadButton.setForeground(Color.WHITE);
        downloadButton.setFocusPainted(false);
        downloadButton.setFont(new Font("SansSerif", Font.BOLD, 12));

        laterButton.addActionListener(e -> dialog.dispose());

        downloadButton.addActionListener(e -> {
            dialog.dispose();
            try {
                Desktop.getDesktop().browse(new java.net.URI(GITHUB_RELEASES_LATEST));
            } catch (Exception ex) {
                log.error("Failed to open browser: " + ex.getMessage(), ex);
                JOptionPane.showMessageDialog(
                        parent,
                        "<html><div style='padding:10px;'>"
                                + "<p>Unable to open browser automatically.</p>"
                                + "<p style='margin-top:10px;'>Please visit:</p>"
                                + "<p style='margin-top:5px; color:#4CAF50;'><b>" + GITHUB_RELEASES_LATEST + "</b></p>"
                                + "</div></html>",
                        "Error Opening Browser",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        buttonPanel.add(laterButton);
        buttonPanel.add(downloadButton);

        // Main content panel
        JPanel contentPanel = new JPanel(new BorderLayout(0, 0));
        contentPanel.setBackground(new Color(45, 45, 45));
        contentPanel.add(headerPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.setBackground(new Color(45, 45, 45));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        centerPanel.add(versionPanel, BorderLayout.NORTH);

        contentPanel.add(centerPanel, BorderLayout.CENTER);
        contentPanel.add(changelogPanel, BorderLayout.SOUTH);

        // Assemble dialog
        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.pack();

        // Set minimum size before centering
        dialog.setMinimumSize(new Dimension(550, 400));

        // Center on screen if no parent window, otherwise center relative to parent window
        dialog.setLocationRelativeTo(parentWindow);

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }

    /**
     * Shows a dialog indicating the app is up to date.
     *
     * @param parent Parent component for the dialog
     */
    public static void showUpToDateDialog(Component parent) {
        // Create custom dialog for better control and centering
        Window parentWindow = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        JDialog dialog;
        if (parentWindow instanceof Frame) {
            dialog = new JDialog((Frame) parentWindow, "Up to Date", true);
        } else if (parentWindow instanceof Dialog) {
            dialog = new JDialog((Dialog) parentWindow, "Up to Date", true);
        } else {
            dialog = new JDialog((Frame) null, "Up to Date", true);
        }

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(new Color(45, 45, 45));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        JLabel messageLabel = new JLabel("<html><div style='width:300px; text-align:center;'>"
                + "<p style='font-size:16pt; font-weight:bold; color:#4CAF50; margin-bottom:12px;'>"
                + "\u2714 You're Up to Date!</p>"
                + "<p style='font-size:11pt; color:#DDDDDD; margin-bottom:12px;'>"
                + "You are running the latest version of <b>eMark</b>.</p>"
                + "<div style='background-color:#2b2b2b; border-radius:8px; padding:12px; margin:10px 0;'>"
                + "<p style='color:#AAAAAA; font-size:10pt; margin:0;'>Current Version: <b style='color:#4CAF50;'>"
                + AppConstants.APP_VERSION + "</b></p>"
                + "</div>"
                + "</div></html>");
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton okButton = new JButton("OK");
        okButton.setPreferredSize(new Dimension(100, 32));
        okButton.setBackground(new Color(76, 175, 80));
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);
        okButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        okButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonPanel.setBackground(new Color(45, 45, 45));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        buttonPanel.add(okButton);

        contentPanel.add(messageLabel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(contentPanel);
        dialog.pack();

        // Center on screen or relative to parent window
        dialog.setLocationRelativeTo(parentWindow);

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    /**
     * Makes a label clickable to show update dialog with enhanced visual effects.
     *
     * @param label         Label to make clickable
     * @param parent        Parent component for dialogs
     * @param latestVersion Latest version string
     */
    public static void makeLabelClickable(final JLabel label, final Component parent, final String latestVersion) {
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setOpaque(true);

        // Enhanced colors for better visibility
        final Color updateBgColor = new Color(76, 175, 80);  // Green - more vibrant
        final Color updateHoverColor = new Color(100, 200, 105); // Lighter green on hover
        final Color updatePressColor = new Color(56, 142, 60);  // Darker green on press

        label.setBackground(updateBgColor);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setText("<html><b>\u2728 Update Available</b></html>");
        label.setToolTipText("Click to view update details and download");

        // Add rounded border with padding for modern look
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 142, 60), 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showUpdateDialogAsync(parent, latestVersion);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                label.setText("<html><b>\u2728 <u>Update Available</u></b></html>");
                label.setBackground(updateHoverColor);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(100, 200, 105), 2, true),
                        BorderFactory.createEmptyBorder(5, 11, 5, 11)
                ));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                label.setText("<html><b>\u2728 Update Available</b></html>");
                label.setBackground(updateBgColor);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(56, 142, 60), 1, true),
                        BorderFactory.createEmptyBorder(6, 12, 6, 12)
                ));
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                label.setBackground(updatePressColor);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (label.contains(e.getPoint())) {
                    label.setBackground(updateHoverColor);
                } else {
                    label.setBackground(updateBgColor);
                }
            }
        });

        // Add subtle pulse animation to draw attention
        startPulseAnimation(label, updateBgColor);
    }

    /**
     * Adds a subtle pulse animation to the update label.
     *
     * @param label     Label to animate
     * @param baseColor Base background color
     */
    private static void startPulseAnimation(final JLabel label, final Color baseColor) {
        final Timer pulseTimer = new Timer(40, null);
        final int[] pulseStep = {0};
        final boolean[] increasing = {true};

        pulseTimer.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (increasing[0]) {
                    pulseStep[0]++;
                    if (pulseStep[0] >= 20) {
                        increasing[0] = false;
                    }
                } else {
                    pulseStep[0]--;
                    if (pulseStep[0] <= 0) {
                        increasing[0] = true;
                    }
                }

                // Create pulsing effect by varying the brightness
                float brightnessAdjust = 1.0f + (pulseStep[0] * 0.01f);
                int r = Math.min(255, (int) (baseColor.getRed() * brightnessAdjust));
                int g = Math.min(255, (int) (baseColor.getGreen() * brightnessAdjust));
                int b = Math.min(255, (int) (baseColor.getBlue() * brightnessAdjust));

                // Only update if not being hovered (to avoid interfering with hover state)
                if (label.getBackground().equals(baseColor) ||
                        label.getBackground().getRGB() == new Color(r, g, b).getRGB()) {
                    label.setBackground(new Color(r, g, b));
                }
            }
        });

        pulseTimer.start();

        // Stop animation after 30 seconds to save resources
        Timer stopTimer = new Timer(30000, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pulseTimer.stop();
                label.setBackground(baseColor);
            }
        });
        stopTimer.setRepeats(false);
        stopTimer.start();
    }


    /**
     * Callback interface for async version check.
     */
    public interface VersionCheckCallback {
        void onResult(boolean updateAvailable);
    }

    /**
     * Callback interface for async version check with release info.
     */
    public interface UpdateInfoCallback {
        void onResult(UpdateInfo info);
    }

    /**
     * Container for update information.
     */
    public static class UpdateInfo {
        public final boolean updateAvailable;
        public final String latestVersion;

        public UpdateInfo(boolean updateAvailable, String latestVersion) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
        }
    }
}
