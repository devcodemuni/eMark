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
    private static final Log log = LogFactory.getLog(TopBarPanel.class);

    private final JButton openBtn;
    private final JButton signBtn;
    private final JButton settingsBtn;
    private final JLabel pageInfoLabel;
    private final JLabel versionStatusLabel;

    private boolean signMode = false;

    public TopBarPanel(Runnable onOpen, Runnable onSettings, Runnable onToggleSign) {
        super(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(FlatUIUtils.getUIColor("Panel.background", Color.WHITE));

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
        VersionManager.checkUpdateAsync(new VersionManager.VersionCheckCallback() {
            @Override
            public void onResult(final boolean updateAvailable) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (updateAvailable) {
                            VersionManager.makeLabelClickable(versionStatusLabel);
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

    public void setInteractiveEnabled(boolean enabled) {
        openBtn.setEnabled(enabled);
        settingsBtn.setEnabled(enabled);
        signBtn.setEnabled(enabled);
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
        signBtn.setText(signMode ? CANCEL_SIGN_TEXT : BEGIN_SIGN_TEXT);
    }
}
