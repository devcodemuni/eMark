package com.codemuni.gui.pdfHandler;

import com.codemuni.service.VersionManager;
import com.codemuni.utils.AppConstants;
import com.formdev.flatlaf.ui.FlatUIUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Top bar panel with:
 * - Open PDF button
 * - Settings button
 * - Begin/Cancel Sign button
 * - Page info label
 * - Version status label (auto-check on startup, hides if up-to-date)
 */
public class TopBarPanel extends JPanel {
    private static final String OPEN_PDF_TEXT = "Open PDF";
    private static final String BEGIN_SIGN_TEXT = "Begin Sign";
    private static final String CANCEL_SIGN_TEXT = "Cancel Signing (ESC)";
    private static final String CERTIFIED_TEXT = "\ud83d\udd0f Certified";
    private static final Log log = LogFactory.getLog(TopBarPanel.class);

    private final JButton openBtn;
    private final JButton signBtn;
    private final JButton settingsBtn;
    private final JLabel pageInfoLabel;
    private final JLabel versionStatusLabel;

    private boolean signMode = false;
    private boolean isCertified = false;

    public TopBarPanel(Runnable onOpen, Runnable onSettings, Runnable onToggleSign) {
        super(new BorderLayout());
        setBorder(new EmptyBorder(12, 15, 12, 15));
        setBackground(FlatUIUtils.getUIColor("Panel.background", Color.WHITE));

        // Add subtle bottom border for visual separation
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 20)),
                new EmptyBorder(12, 15, 12, 15)
        ));

        // -------------------- Buttons --------------------
        openBtn = UiFactory.createButton(OPEN_PDF_TEXT, new Color(0x007BFF));
        openBtn.addActionListener(e -> onOpen.run());

        signBtn = UiFactory.createButton(BEGIN_SIGN_TEXT, new Color(0x28A745));
        signBtn.setVisible(false);
        signBtn.addActionListener(e -> {
            signMode = !signMode;
            updateSignButtonText();
            onToggleSign.run();
        });

        settingsBtn = UiFactory.createButton("Settings", new Color(0x6C757D));
        settingsBtn.addActionListener(e -> onSettings.run());

        // -------------------- Version Status Label --------------------
        versionStatusLabel = new JLabel("Checking for updates...");
        versionStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        versionStatusLabel.setForeground(Color.LIGHT_GRAY);
        versionStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        pageInfoLabel = new JLabel("");
        pageInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        // -------------------- Layout --------------------
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(pageInfoLabel);
        centerPanel.add(signBtn);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(versionStatusLabel);
        rightPanel.add(settingsBtn);

        add(UiFactory.wrapLeft(openBtn), BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // -------------------- Auto Startup Version Check --------------------
        VersionManager.checkUpdateWithInfoAsync(new VersionManager.UpdateInfoCallback() {
            @Override
            public void onResult(final VersionManager.UpdateInfo info) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (info.updateAvailable && info.latestVersion != null) {
                            // Make label clickable with async dialog showing
                            VersionManager.makeLabelClickable(versionStatusLabel, TopBarPanel.this, info.latestVersion);
                        } else {
                            // Hide label if no update
                            versionStatusLabel.setVisible(false);
                        }
                    }
                });
            }
        });

    }

    // -------------------- Helper Methods --------------------
    public void setPageInfoText(String text) {
        pageInfoLabel.setText(text);
    }

    public void setSignButtonVisible(boolean visible) {
        signBtn.setVisible(visible);
    }

    /**
     * Sets the button to certified mode - disabled with "Document Certified" label
     */
    public void setSignButtonCertified(boolean certified) {
        this.isCertified = certified;
        if (certified) {
            signBtn.setText(CERTIFIED_TEXT);
            signBtn.setEnabled(false);
            signBtn.setVisible(true);
            // Default tooltip - can be overridden by setSignButtonTooltip()
            if (signBtn.getToolTipText() == null || signBtn.getToolTipText().isEmpty()) {
                signBtn.setToolTipText("This document is certified. You cannot add more signatures.");
            }
        } else {
            signBtn.setEnabled(true);
            signBtn.setToolTipText(null);
            updateSignButtonText();
        }
    }

    /**
     * Sets tooltip for the sign button (PDF viewer style)
     */
    public void setSignButtonTooltip(String tooltip) {
        if (tooltip != null && !tooltip.isEmpty()) {
            signBtn.setToolTipText(tooltip);
        } else {
            signBtn.setToolTipText(null);
        }
    }

    public void setInteractiveEnabled(boolean enabled) {
        openBtn.setEnabled(enabled);
        settingsBtn.setEnabled(enabled);
        if (!isCertified) {
            signBtn.setEnabled(enabled);
        }
        setSignMode(!enabled);
    }

    public void setLoading(boolean loading) {
        openBtn.setText(loading ? "Opening PDF..." : OPEN_PDF_TEXT);
        setInteractiveEnabled(!loading);
    }

    public void setSignMode(boolean enabled) {
        this.signMode = enabled;
        updateSignButtonText();
    }

    private void updateSignButtonText() {
        if (isCertified) {
            signBtn.setText(CERTIFIED_TEXT);
        } else {
            signBtn.setText(signMode ? CANCEL_SIGN_TEXT : BEGIN_SIGN_TEXT);
        }
    }

    public boolean isSignModeEnabled() {
        return signMode;
    }
}
