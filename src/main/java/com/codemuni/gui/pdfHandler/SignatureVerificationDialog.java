package com.codemuni.gui.pdfHandler;

import com.codemuni.core.keyStoresProvider.X509SubjectUtils;
import com.codemuni.service.SignatureVerificationService.SignatureVerificationResult;
import com.codemuni.service.SignatureVerificationService.VerificationStatus;
import com.codemuni.utils.IconGenerator;
import com.codemuni.utils.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Quick signature verification status dialog shown when user clicks on signature rectangle.
 * Shows essential verification info and provides option to view detailed properties.
 */
public class SignatureVerificationDialog extends JDialog {

    private final SignatureVerificationResult result;
    private final Color signatureColor;

    public SignatureVerificationDialog(Frame parent, SignatureVerificationResult result, Color signatureColor) {
        super(parent, "Signature Verification", true);
        this.result = result;
        this.signatureColor = signatureColor;

        setupDialog();
        createUI();
    }

    private void setupDialog() {
        setSize(420, 240);
        setLocationRelativeTo(getParent());
        setResizable(false);
        getContentPane().setBackground(UIConstants.Colors.BG_PRIMARY);
    }

    private void createUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(UIConstants.Colors.BG_PRIMARY);
        mainPanel.setBorder(UIConstants.Padding.LARGE);

        // Header panel with colored bar and status icon
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Content panel with verification status
        JPanel contentPanel = createContentPanel();
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBackground(UIConstants.Colors.BG_PRIMARY);

        // Colored bar on left (signature color)
        JPanel colorBar = new JPanel();
        colorBar.setBackground(signatureColor);
        colorBar.setPreferredSize(new Dimension(6, 60));
        panel.add(colorBar, BorderLayout.WEST);

        // Status icon
        JLabel statusIcon = createStatusIcon();
        panel.add(statusIcon, BorderLayout.CENTER);

        // Status text
        JPanel statusTextPanel = new JPanel();
        statusTextPanel.setLayout(new BoxLayout(statusTextPanel, BoxLayout.Y_AXIS));
        statusTextPanel.setBackground(UIConstants.Colors.BG_PRIMARY);

        String signerName = X509SubjectUtils.extractCommonNameFromDN(result.getCertificateSubject());
        if (signerName == null || signerName.isEmpty()) {
            signerName = result.getFieldName();
        }

        JLabel nameLabel = new JLabel(signerName);
        nameLabel.setFont(UIConstants.Fonts.TITLE_BOLD);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel statusLabel = new JLabel(result.getStatusMessage());
        statusLabel.setFont(UIConstants.Fonts.LARGE_PLAIN);
        statusLabel.setForeground(getStatusColor(result.getOverallStatus()));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusTextPanel.add(nameLabel);
        statusTextPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        statusTextPanel.add(statusLabel);

        panel.add(statusTextPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.Colors.BG_TERTIARY);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.Colors.BORDER_PRIMARY, 1),
                new EmptyBorder(10, 15, 10, 15)
        ));

        // Simple, easy-to-understand labels
        addInfoRow(panel, "Signature:", result.isSignatureValid() ? "Valid" : "Invalid",
                result.isSignatureValid() ? UIConstants.Colors.STATUS_VALID : UIConstants.Colors.STATUS_ERROR);

        addInfoRow(panel, "Document:", result.isDocumentIntact() ? "Not Modified" : "Modified",
                result.isDocumentIntact() ? UIConstants.Colors.STATUS_VALID : UIConstants.Colors.STATUS_ERROR);

        addInfoRow(panel, "Certificate:", result.isCertificateTrusted() ? "Trusted" : "Not Trusted",
                result.isCertificateTrusted() ? UIConstants.Colors.STATUS_VALID : UIConstants.Colors.STATUS_WARNING);

        // Show timestamp or LTV status only if enabled
        if (result.isTimestampValid() || result.hasLTV()) {
            String additionalInfo = "";
            if (result.isTimestampValid() && result.hasLTV()) {
                additionalInfo = "Timestamp, LTV";
            } else if (result.isTimestampValid()) {
                additionalInfo = "Timestamp";
            } else if (result.hasLTV()) {
                additionalInfo = "LTV";
            }
            addInfoRow(panel, "Additional Security:", additionalInfo, UIConstants.Colors.STATUS_VALID);
        }

        return panel;
    }

    private void addInfoRow(JPanel panel, String label, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(UIConstants.Colors.BG_TERTIARY);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(UIConstants.Fonts.LARGE_PLAIN);
        labelComp.setForeground(UIConstants.Colors.TEXT_TERTIARY);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(UIConstants.Fonts.LARGE_BOLD);
        valueComp.setForeground(valueColor);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.EAST);

        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setBackground(UIConstants.Colors.BG_PRIMARY);

        // Cancel button
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        cancelBtn.setPreferredSize(UIConstants.buttonSize(100));
        cancelBtn.setFocusPainted(false);
        cancelBtn.addActionListener(e -> dispose());

        // Show signature properties button
        JButton propertiesBtn = new JButton("Show Signature Properties");
        propertiesBtn.setFont(UIConstants.Fonts.NORMAL_BOLD);
        propertiesBtn.setPreferredSize(UIConstants.buttonSize(200));
        propertiesBtn.setFocusPainted(false);
        propertiesBtn.addActionListener(e -> {
            dispose();
            showDetailedProperties();
        });

        panel.add(cancelBtn);
        panel.add(propertiesBtn);

        return panel;
    }

    private void showDetailedProperties() {
        SignaturePropertiesDialog dialog = new SignaturePropertiesDialog(
                (Frame) getParent(), result, signatureColor);
        dialog.setVisible(true);
    }

    private JLabel createStatusIcon() {
        VerificationStatus status = result.getOverallStatus();
        String iconText;
        Color iconColor;

        switch (status) {
            case VALID:
                iconText = "✓";
                iconColor = UIConstants.Colors.STATUS_VALID;
                break;
            case UNKNOWN:
                iconText = "?";
                iconColor = UIConstants.Colors.STATUS_WARNING;
                break;
            case INVALID:
                iconText = "✗";
                iconColor = UIConstants.Colors.STATUS_ERROR;
                break;
            default:
                iconText = "?";
                iconColor = UIConstants.Colors.TEXT_DISABLED;
        }

        JLabel icon = new JLabel(iconText);
        icon.setFont(new Font("Segoe UI", Font.BOLD, 32));
        icon.setForeground(iconColor);
        icon.setPreferredSize(new Dimension(40, 40));
        icon.setHorizontalAlignment(SwingConstants.CENTER);

        return icon;
    }

    private Color getStatusColor(VerificationStatus status) {
        switch (status) {
            case VALID:
                return UIConstants.Colors.STATUS_VALID;
            case UNKNOWN:
                return UIConstants.Colors.STATUS_WARNING;
            case INVALID:
                return UIConstants.Colors.STATUS_ERROR;
            default:
                return UIConstants.Colors.TEXT_DISABLED;
        }
    }

}
