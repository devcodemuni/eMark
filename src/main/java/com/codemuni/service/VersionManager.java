package com.codemuni.service;

import com.codemuni.App;
import com.codemuni.utils.AppConstants;
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
     * @param currentVersion Current app version
     * @return true if a newer version exists
     */
    public static boolean isUpdateAvailable(String currentVersion) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_RELEASES_LATEST).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.connect();

            String location = conn.getHeaderField("Location");
            conn.disconnect();

            if (location == null) {
                log.warn("No redirect location found for latest release check.");
                return false;
            }

            if (location.endsWith("/")) location = location.substring(0, location.length() - 1);

            String latestVersion = location.substring(location.lastIndexOf("/") + 1);
            if (latestVersion.startsWith("v") || latestVersion.startsWith("V")) {
                latestVersion = latestVersion.substring(1);
            }

            log.info("Latest GitHub version: " + latestVersion);

            return compareVersions(latestVersion, currentVersion) > 0;

        } catch (Exception e) {
            log.error("Failed to check for latest version: " + e.getMessage());
            return false;
        }
    }

    /**
     * Compares two semantic version strings: "1.0.0", "1.2.3"
     *
     * @return -1 if v1<v2, 0 if v1=v2, 1 if v1>v2
     */
    private static int compareVersions(String v1, String v2) {
        String[] a1 = v1.split("\\.");
        String[] a2 = v2.split("\\.");
        int len = Math.max(a1.length, a2.length);

        for (int i = 0; i < len; i++) {
            int n1 = i < a1.length ? parseIntSafe(a1[i]) : 0;
            int n2 = i < a2.length ? parseIntSafe(a2[i]) : 0;
            if (n1 != n2) return n1 > n2 ? 1 : -1;
        }
        return 0;
    }

    private static int parseIntSafe(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
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
                return isUpdateAvailable(App.APP_VERSION);
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
     * Makes a label clickable to open GitHub release page if update is available.
     *
     * @param label JLabel to make clickable
     */
    public static void makeLabelClickable(final JLabel label) {
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setOpaque(true);
        label.setBackground(new Color(255, 230, 230));
        label.setForeground(new Color(200, 0, 0));
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8)); // padding
        label.setText("<html><b>🔔 Update available!</b></html>");
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
                label.setText("<html><b><u>🔔 Update available!</u></b></html>");
                label.setBackground(new Color(255, 210, 210)); // darker hover
                label.setForeground(new Color(160, 0, 0));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                label.setText("<html><b>🔔 Update available!</b></html>");
                label.setBackground(new Color(255, 230, 230));
                label.setForeground(new Color(200, 0, 0));
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                label.setBackground(new Color(255, 180, 180)); // click feedback
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                label.setBackground(new Color(255, 210, 210));
            }
        });

        // Rounded background effect
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 0, 0), 1, true),
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
