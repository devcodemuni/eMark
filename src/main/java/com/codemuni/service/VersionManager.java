package com.codemuni.service;

import com.codemuni.App;
import com.codemuni.utils.AppConstants;
import com.codemuni.utils.UIConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URL;

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
        if (currentVersion == null || currentVersion.trim().isEmpty()) {
            log.error("Current version is null or empty");
            return false;
        }

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
                return false;
            }

            if (location == null || location.trim().isEmpty()) {
                log.warn("No redirect location found for latest release check.");
                return false;
            }

            // Extract version from URL (e.g., .../releases/tag/V1.0.2)
            if (location.endsWith("/")) {
                location = location.substring(0, location.length() - 1);
            }

            String latestVersion = location.substring(location.lastIndexOf("/") + 1);

            // Normalize both versions by removing v/V prefix
            String normalizedLatest = normalizeVersion(latestVersion);
            String normalizedCurrent = normalizeVersion(currentVersion);

            log.info("Latest GitHub version: " + latestVersion + " (normalized: " + normalizedLatest + ")");
            log.info("Current app version: " + currentVersion + " (normalized: " + normalizedCurrent + ")");

            int comparison = compareVersions(normalizedLatest, normalizedCurrent);
            log.info("Version comparison result: " + comparison + " (>0 means update available)");

            return comparison > 0;

        } catch (Exception e) {
            log.error("Failed to check for latest version: " + e.getMessage(), e);
            return false;
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
     * Makes a label clickable to open GitHub release page.
     */
    public static void makeLabelClickable(final JLabel label) {
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setOpaque(true);
        label.setBackground(UIConstants.Colors.STATUS_WARNING);
        label.setForeground(UIConstants.Colors.TEXT_PRIMARY);
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        label.setText("<html><b>Update available!</b></html>");
        label.setToolTipText("Visit the official eMark website to download the latest version");

        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(AppConstants.APP_WEBSITE));
                } catch (Exception ex) {
                    log.error("Failed to open website: " + ex.getMessage());
                }
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                label.setText("<html><b><u>Update available!</u></b></html>");
                label.setBackground(UIConstants.Colors.BUTTON_SECONDARY_HOVER);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                label.setText("<html><b>Update available!</b></html>");
                label.setBackground(UIConstants.Colors.STATUS_WARNING);
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                label.setBackground(UIConstants.Colors.BUTTON_SECONDARY);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                label.setBackground(UIConstants.Colors.BUTTON_SECONDARY_HOVER);
            }
        });

        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.Colors.BORDER_PRIMARY, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
    }


    /**
     * Callback interface for async version check.
     */
    public interface VersionCheckCallback {
        void onResult(boolean updateAvailable);
    }
}
