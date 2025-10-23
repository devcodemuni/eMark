package com.codemuni.gui.settings;

import com.codemuni.utils.UIConstants;
import com.codemuni.utils.IconLoader;

import javax.swing.*;
import java.awt.*;

// Popup window for managing trusted certificates - can add/remove certificates used for signature verification
public class TrustManagerDialog extends JDialog {

    private static final int DIALOG_WIDTH = 900;
    private static final int DIALOG_HEIGHT = 700;

    private TrustCertificatesPanel trustPanel;

    // Create dialog with a parent window
    public TrustManagerDialog(Frame parent) {
        super(parent, "Trust Manager - Certificate Management", true);
        initializeDialog();
    }

    // Create standalone dialog without parent
    public TrustManagerDialog() {
        this((Frame) null);
    }

    private void initializeDialog() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(getParent());
        setLayout(new BorderLayout());

        // Main panel with certificate table
        trustPanel = new TrustCertificatesPanel();
        add(trustPanel, BorderLayout.CENTER);

        // Info text and close button at bottom
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        panel.setBackground(UIConstants.Colors.BG_PRIMARY);

        // Info text on the left
        JLabel infoLabel = new JLabel("Trust certificates are used for signature verification");
        infoLabel.setFont(UIConstants.Fonts.SMALL_PLAIN);
        infoLabel.setForeground(UIConstants.Colors.TEXT_DISABLED);

        // Add a small shield icon next to the text
        ImageIcon shieldIcon = loadScaledIcon("shield.png", UIConstants.Dimensions.ICON_TINY, 0.5f);
        if (shieldIcon != null) {
            infoLabel.setIcon(shieldIcon);
            infoLabel.setIconTextGap(UIConstants.Dimensions.SPACING_SMALL);
        }

        panel.add(infoLabel, BorderLayout.WEST);

        // Close button on the right side
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);

        JButton closeButton = new JButton("Close");
        closeButton.setFont(UIConstants.Fonts.NORMAL_BOLD);
        closeButton.setPreferredSize(UIConstants.buttonSize(UIConstants.Dimensions.BUTTON_WIDTH_SMALL));
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(closeButton);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    // Show dialog with a parent window
    public static void showTrustManager(Frame parent) {
        TrustManagerDialog dialog = new TrustManagerDialog(parent);
        dialog.setVisible(true);
    }

    // Show dialog standalone without parent
    public static void showTrustManager() {
        TrustManagerDialog dialog = new TrustManagerDialog();
        dialog.setVisible(true);
    }

    private ImageIcon loadScaledIcon(String iconName, int baseSize, float scaleFactor) {
        int scaledSize = Math.max(1, Math.round(baseSize * scaleFactor));
        return IconLoader.loadIcon(iconName, scaledSize);
    }
}
