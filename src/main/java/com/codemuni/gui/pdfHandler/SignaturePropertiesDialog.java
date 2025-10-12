package com.codemuni.gui.pdfHandler;

import com.codemuni.core.keyStoresProvider.X509SubjectUtils;
import com.codemuni.service.SignatureVerificationService.SignatureVerificationResult;
import com.codemuni.service.SignatureVerificationService.VerificationStatus;
import com.codemuni.utils.UIConstants;
import com.codemuni.utils.IconLoader;
import com.codemuni.utils.CertificateUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;

/**
 * Detailed signature properties dialog showing comprehensive verification information.
 * Includes certificate details, timestamp info, warnings/errors, and export functionality.
 */
public class SignaturePropertiesDialog extends JDialog {

    private static final Color VALID_COLOR = UIConstants.Colors.STATUS_VALID;
    private static final Color UNKNOWN_COLOR = UIConstants.Colors.STATUS_WARNING;
    private static final Color INVALID_COLOR = UIConstants.Colors.STATUS_ERROR;
    private static final Color BG_COLOR = UIConstants.Colors.BG_PRIMARY;
    private static final Color SECTION_BG = UIConstants.Colors.BG_SECONDARY;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");

    private final SignatureVerificationResult result;
    private final Color signatureColor;

    public SignaturePropertiesDialog(Frame parent, SignatureVerificationResult result, Color signatureColor) {
        super(parent, "Signature Properties - " + result.getFieldName(), true);
        this.result = result;
        this.signatureColor = signatureColor;

        setupDialog();
        createUI();
    }

    private void setupDialog() {
        setSize(750, 850);
        setLocationRelativeTo(getParent());
        setResizable(true);
        setMinimumSize(new Dimension(650, 750));
        getContentPane().setBackground(BG_COLOR);
    }

    private void createUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 15));
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Header with colored bar
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Scrollable content
        JPanel contentPanel = createContentPanel();
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_COLOR);

        // To improve scroll density
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Ensure scroll starts at top
        scrollPane.getVerticalScrollBar().setValue(0);
        SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0)));

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_COLOR);

        // Left section: Colored bar + Shield icon
        JPanel leftSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftSection.setBackground(BG_COLOR);

        // Colored bar
        JPanel colorBar = new JPanel();
        colorBar.setBackground(signatureColor);
        colorBar.setPreferredSize(new Dimension(6, 70));
        leftSection.add(colorBar);

        // Shield icon (if signature is valid/trusted)
        boolean isOverallValid = result.getOverallStatus() == VerificationStatus.VALID;

        // Select appropriate icon
        String iconPath = isOverallValid ? "shield.png" : "shield-invalid.png";
        ImageIcon shieldIcon = IconLoader.loadIcon(iconPath, 48, 48);

        if (shieldIcon != null) {
            JLabel iconLabel = new JLabel(shieldIcon);
            iconLabel.setBorder(new EmptyBorder(10, 12, 10, 12));
            leftSection.add(iconLabel);
        } else {
            // Fallback: if icon missing, maintain layout spacing
            leftSection.add(Box.createRigidArea(new Dimension(12, 70)));
        }


        panel.add(leftSection, BorderLayout.WEST);

        // Status section
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBackground(BG_COLOR);

        String signerName = X509SubjectUtils.extractCommonNameFromDN(result.getCertificateSubject());
        if (signerName.isEmpty()) {
            signerName = result.getFieldName();
        }

        JLabel nameLabel = new JLabel(signerName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel statusLabel = new JLabel(result.getStatusMessage());
        statusLabel.setFont(UIConstants.Fonts.LARGE_PLAIN);
        statusLabel.setForeground(getStatusColor(result.getOverallStatus()));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fieldLabel = new JLabel("Field: " + result.getFieldName());
        fieldLabel.setFont(UIConstants.Fonts.SMALL_PLAIN);
        fieldLabel.setForeground(UIConstants.Colors.TEXT_DISABLED);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusPanel.add(nameLabel);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        statusPanel.add(fieldLabel);

        panel.add(statusPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_COLOR);

        // Info Section (if any)
        if (!result.getVerificationInfo().isEmpty()) {
            panel.add(createSection("Information", createInfoContent()));
            panel.add(Box.createRigidArea(new Dimension(0, 12)));
        }

        // Warnings Section (if any)
        if (!result.getVerificationWarnings().isEmpty()) {
            panel.add(createSection("Warnings", createWarningsContent()));
            panel.add(Box.createRigidArea(new Dimension(0, 12)));
        }

        // Errors Section (if any)
        if (!result.getVerificationErrors().isEmpty()) {
            panel.add(createSection("Errors", createErrorsContent()));
            panel.add(Box.createRigidArea(new Dimension(0, 12)));
        }

        // Verification Status Section
        panel.add(createSection("Verification Status", createVerificationStatusContent()));
        panel.add(Box.createRigidArea(new Dimension(0, 12)));

        // Signature Information Section
        panel.add(createSection("Signature Information", createSignatureInfoContent()));
        panel.add(Box.createRigidArea(new Dimension(0, 12)));

        // Certificate Information Section
        panel.add(createSection("Certificate Information", createCertificateInfoContent()));
        panel.add(Box.createRigidArea(new Dimension(0, 12)));

        // Document Information Section
        panel.add(createSection("Document Information", createDocumentInfoContent()));
        panel.add(Box.createRigidArea(new Dimension(0, 12)));

        return panel;
    }

    private JPanel createSection(String title, JPanel content) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(SECTION_BG);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIConstants.Colors.BORDER_SECONDARY, 1),
                new EmptyBorder(14, 16, 14, 16)
        ));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIConstants.Fonts.LARGE_BOLD);
        titleLabel.setForeground(UIConstants.Colors.TEXT_PRIMARY);
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        section.add(titleLabel, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);

        return section;
    }

    private JPanel createVerificationStatusContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECTION_BG);

        addStatusRow(panel, "Signature", result.isSignatureValid());
        addStatusRow(panel, "Document Integrity", result.isDocumentIntact());
        addStatusRow(panel, "Certificate Valid", result.isCertificateValid());
        addStatusRow(panel, "Certificate Trusted", result.isCertificateTrusted());
        addStatusRow(panel, "Revocation Status", !result.isCertificateRevoked(), result.getRevocationStatus());

        // Timestamp status - show "Not Enabled" if no timestamp present
        String timestampStatus = getTimestampStatusText();
        addStatusRow(panel, "Timestamp", result.isTimestampValid(), timestampStatus);

        // LTV status - show "Not Enabled" if LTV not present
        String ltvStatus = getLTVStatusText();
        addStatusRow(panel, "Long Term Validation", result.hasLTV(), ltvStatus);

        return panel;
    }

    private String getTimestampStatusText() {
        if (result.isTimestampValid()) {
            return "Valid";
        } else if (result.getTimestampDate() != null) {
            // Timestamp present but invalid
            return "Invalid";
        } else {
            // No timestamp in signature
            return "Not Enabled";
        }
    }

    private String getLTVStatusText() {
        if (result.hasLTV()) {
            return "Enabled";
        } else {
            // LTV not enabled in signature
            return "Not Enabled";
        }
    }

    private void addStatusRow(JPanel panel, String label, boolean status) {
        addStatusRow(panel, label, status, null);
    }

    private void addStatusRow(JPanel panel, String label, boolean status, String customText) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(SECTION_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel labelComp = new JLabel(label + ":");
        labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        labelComp.setForeground(UIConstants.Colors.TEXT_SECONDARY);

        // Create status panel with icon
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        statusPanel.setOpaque(false);

        String statusText;
        Color statusColor;

        if (customText != null && !customText.isEmpty()) {
            // Use custom text with appropriate color
            statusText = customText;
            if (customText.contains("Valid") || customText.contains("CRL") || customText.equals("Enabled")) {
                statusColor = VALID_COLOR;
            } else if (customText.contains("Not Checked") || customText.equals("Not Enabled")) {
                statusColor = UIConstants.Colors.TEXT_MUTED; // Gray
            } else if (customText.contains("Revoked")) {
                statusColor = INVALID_COLOR;
            } else {
                statusColor = UNKNOWN_COLOR;
            }
        } else {
            statusText = status ? "Valid" : "Invalid";
            statusColor = status ? VALID_COLOR : INVALID_COLOR;
        }

        // Add icon for valid status
        if (status && customText == null) {
            ImageIcon icon = IconLoader.loadIcon("green_tick.png", 14, 14);
            if (icon != null) {
                JLabel iconLabel = new JLabel(icon);
                statusPanel.add(iconLabel);
            }
        } else if (status && customText != null && (customText.contains("Valid") || customText.contains("CRL"))) {
            ImageIcon icon = IconLoader.loadIcon("green_tick.png", 14, 14);
            if (icon != null) {
                JLabel iconLabel = new JLabel(icon);
                statusPanel.add(iconLabel);
            }
        }

        JLabel valueComp = new JLabel(statusText);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, 13));
        valueComp.setForeground(statusColor);
        statusPanel.add(valueComp);

        row.add(labelComp, BorderLayout.WEST);
        row.add(statusPanel, BorderLayout.EAST);

        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
    }

    private JPanel createSignatureInfoContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECTION_BG);

        if (result.getSignDate() != null) {
            addDetailRow(panel, "Signed On", DATE_FORMAT.format(result.getSignDate()));
        }

        if (result.getReason() != null && !result.getReason().isEmpty()) {
            addDetailRow(panel, "Reason", result.getReason());
        }

        if (result.getLocation() != null && !result.getLocation().isEmpty()) {
            addDetailRow(panel, "Location", result.getLocation());
        }

        if (result.getContactInfo() != null && !result.getContactInfo().isEmpty()) {
            addDetailRow(panel, "Contact", result.getContactInfo());
        }

        if (result.getSignatureAlgorithm() != null) {
            addDetailRow(panel, "Algorithm", result.getSignatureAlgorithm());
        }

        // Timestamp information - show even if not valid to explain why
        if (result.getTimestampDate() != null || result.isTimestampValid()) {
            panel.add(Box.createRigidArea(new Dimension(0, 8)));
            addSectionSubheader(panel, "Timestamp Information");

            if (result.getTimestampDate() != null) {
                addDetailRow(panel, "Timestamp Date", DATE_FORMAT.format(result.getTimestampDate()));
            }

            if (result.getTimestampAuthority() != null) {
                addDetailRow(panel, "Timestamp Authority", result.getTimestampAuthority());
            }

            // Timestamp status explanation
            if (result.isTimestampValid()) {
                addDetailRow(panel, "Status", "✓ Timestamp verified successfully");
            } else if (result.getTimestampDate() != null) {
                addDetailRow(panel, "Status", "⚠ Timestamp present but verification failed");
            }
        } else {
            // No timestamp at all
            panel.add(Box.createRigidArea(new Dimension(0, 8)));
            addSectionSubheader(panel, "Timestamp Information");
            addDetailRow(panel, "Status", "Not included in this signature");
        }

        return panel;
    }

    private JPanel createCertificateInfoContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECTION_BG);

        if (result.getCertificateSubject() != null) {
            addDetailRow(panel, "Subject", result.getCertificateSubject());
        }

        if (result.getCertificateIssuer() != null) {
            addDetailRow(panel, "Issuer", result.getCertificateIssuer());
        }

        // Certificate serial number in hex format
        if (result.getSignerCertificate() != null) {
            String serialHex = result.getSignerCertificate().getSerialNumber().toString(16).toUpperCase();
            addDetailRow(panel, "Serial Number", serialHex);
        }

        if (result.getCertificateValidFrom() != null) {
            addDetailRow(panel, "Valid From", DATE_FORMAT.format(result.getCertificateValidFrom()));
        }

        if (result.getCertificateValidTo() != null) {
            addDetailRow(panel, "Valid To", DATE_FORMAT.format(result.getCertificateValidTo()));
        }

        // Certificate version
        if (result.getSignerCertificate() != null) {
            addDetailRow(panel, "Version", "V" + result.getSignerCertificate().getVersion());
        }

        // Signature algorithm with OID
        if (result.getSignerCertificate() != null) {
            String sigAlg = result.getSignerCertificate().getSigAlgName();
            String sigAlgOID = result.getSignerCertificate().getSigAlgOID();
            addDetailRow(panel, "Signature Algorithm", sigAlg + " (" + sigAlgOID + ")");
        }

        // Public key algorithm and size
        if (result.getSignerCertificate() != null) {
            java.security.PublicKey pubKey = result.getSignerCertificate().getPublicKey();
            String keyAlgorithm = pubKey.getAlgorithm();
            int keySize = CertificateUtils.getKeySize(pubKey);
            addDetailRow(panel, "Public Key", keyAlgorithm + " (" + keySize + " bits)");
        }

        // Key usage
        if (result.getSignerCertificate() != null) {
            java.util.List<String> keyUsages = CertificateUtils.getKeyUsageStrings(result.getSignerCertificate());
            if (!keyUsages.isEmpty()) {
                addDetailRow(panel, "Key Usage", String.join(", ", keyUsages));
            }
        }

        // Extended key usage
        if (result.getSignerCertificate() != null) {
            java.util.List<String> extKeyUsages = CertificateUtils.getExtendedKeyUsageStrings(result.getSignerCertificate());
            if (!extKeyUsages.isEmpty()) {
                addDetailRow(panel, "Extended Key Usage", String.join(", ", extKeyUsages));
            }
        }

        // Certificate chain info
        if (result.getCertificateChain() != null && !result.getCertificateChain().isEmpty()) {
            panel.add(Box.createRigidArea(new Dimension(0, 8)));
            addSectionSubheader(panel, "Certificate Chain");
            addDetailRow(panel, "Chain Length", String.valueOf(result.getCertificateChain().size()) + " certificate(s)");

            // Show each certificate in chain with proper hierarchy
            int chainSize = result.getCertificateChain().size();

            for (int i = 0; i < chainSize; i++) {
                java.security.cert.X509Certificate cert = result.getCertificateChain().get(i);
                String subjectCN = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());
                String issuerCN = X509SubjectUtils.extractCommonNameFromDN(cert.getIssuerDN().toString());

                // Determine role using CertificateUtils
                CertificateUtils.CertificateRole certRole = CertificateUtils.determineCertificateRole(cert);
                String role = certRole.getDisplayName();

                // Format: [index] Role: CN (issued by: Issuer CN)
                String displayText;
                if (certRole == CertificateUtils.CertificateRole.END_ENTITY) {
                    // For end entity, show who issued it
                    displayText = subjectCN + " (Issued by: " + issuerCN + ")";
                } else {
                    // For CAs, just show the name
                    displayText = subjectCN;
                }

                String label = "[" + i + "] " + role + ":";
                addDetailRow(panel, "  " + label, displayText);
            }
        }

        return panel;
    }


    private JPanel createDocumentInfoContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECTION_BG);

        if (result.getTotalRevisions() > 0) {
            addDetailRow(panel, "Signature Revision", result.getRevision() + " of " + result.getTotalRevisions());
            addStatusRow(panel, "Covers Whole Document", result.isCoversWholeDocument());
        }

        if (result.getPageNumber() > 0) {
            addDetailRow(panel, "Signature Location", "Page " + result.getPageNumber());
        }

        return panel;
    }

    private JPanel createInfoContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECTION_BG);

        for (String info : result.getVerificationInfo()) {
            addMessageRow(panel, info, UIConstants.Colors.STATUS_INFO); // Blue color for info
        }

        return panel;
    }

    private JPanel createWarningsContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECTION_BG);

        for (String warning : result.getVerificationWarnings()) {
            addMessageRow(panel, warning, UNKNOWN_COLOR);
        }

        return panel;
    }

    private JPanel createErrorsContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECTION_BG);

        for (String error : result.getVerificationErrors()) {
            addMessageRow(panel, error, INVALID_COLOR);
        }

        return panel;
    }

    private void addSectionSubheader(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIConstants.Fonts.SMALL_PLAIN);
        label.setForeground(UIConstants.Colors.TEXT_TERTIARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private void addDetailRow(JPanel panel, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        JPanel row = new JPanel(new BorderLayout(12, 2));
        row.setBackground(SECTION_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel labelComp = new JLabel(label + ":");
        labelComp.setFont(UIConstants.Fonts.NORMAL_BOLD);
        labelComp.setForeground(UIConstants.Colors.TEXT_SECONDARY);
        labelComp.setVerticalAlignment(SwingConstants.TOP);

        JTextArea valueComp = new JTextArea(value);
        valueComp.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        valueComp.setForeground(UIConstants.Colors.TEXT_MUTED);
        valueComp.setBackground(SECTION_BG);
        valueComp.setLineWrap(true);
        valueComp.setWrapStyleWord(true);
        valueComp.setEditable(false);
        valueComp.setBorder(null);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.CENTER);

        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void addMessageRow(JPanel panel, String message, Color color) {
        JTextArea textArea = new JTextArea("• " + message);
        textArea.setFont(UIConstants.Fonts.SMALL_PLAIN);
        textArea.setForeground(color);
        textArea.setBackground(SECTION_BG);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setBorder(new EmptyBorder(2, 0, 2, 0));
        textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        textArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        panel.add(textArea);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setBackground(BG_COLOR);

        // Export certificate button
        if (result.getSignerCertificate() != null) {
            JButton exportCertBtn = new JButton("Export Certificate");
            exportCertBtn.setFont(UIConstants.Fonts.NORMAL_PLAIN);
            exportCertBtn.setPreferredSize(UIConstants.buttonSize(150));
            exportCertBtn.setFocusPainted(false);
            exportCertBtn.addActionListener(e -> exportCertificate());
            panel.add(exportCertBtn);
        }

        // View full certificate button
        if (result.getSignerCertificate() != null) {
            JButton viewCertBtn = new JButton("View Certificate");
            viewCertBtn.setFont(UIConstants.Fonts.NORMAL_PLAIN);
            viewCertBtn.setPreferredSize(UIConstants.buttonSize(140));
            viewCertBtn.setFocusPainted(false);
            viewCertBtn.addActionListener(e -> viewFullCertificate());
            panel.add(viewCertBtn);
        }

        // Close button
        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(UIConstants.Fonts.NORMAL_BOLD);
        closeBtn.setPreferredSize(UIConstants.buttonSize(100));
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dispose());
        panel.add(closeBtn);

        return panel;
    }

    /**
     * Exports the signer certificate to PEM format.
     */
    private void exportCertificate() {
        try {
            java.security.cert.X509Certificate cert = result.getSignerCertificate();
            if (cert == null) {
                JOptionPane.showMessageDialog(this,
                    "No certificate available to export",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Certificate");

            // Default filename from CN
            String cn = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());
            String defaultName = cn.replaceAll("[^a-zA-Z0-9]", "_") + "_certificate.pem";
            chooser.setSelectedFile(new File(defaultName));

            // Add file filters
            chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PEM Certificate (*.pem)", "pem"));
            chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("DER Certificate (*.cer, *.der)", "cer", "der"));
            chooser.setFileFilter(chooser.getChoosableFileFilters()[0]); // Default to PEM

            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File outputFile = chooser.getSelectedFile();
                boolean isPEM = chooser.getFileFilter().getDescription().contains("PEM");

                // Add extension if not present
                if (isPEM && !outputFile.getName().toLowerCase().endsWith(".pem")) {
                    outputFile = new File(outputFile.getAbsolutePath() + ".pem");
                } else if (!isPEM && !outputFile.getName().toLowerCase().matches(".*\\.(cer|der)$")) {
                    outputFile = new File(outputFile.getAbsolutePath() + ".cer");
                }

                if (isPEM) {
                    // Export as PEM
                    String pemCert = "-----BEGIN CERTIFICATE-----\n" +
                                   java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                                       .encodeToString(cert.getEncoded()) +
                                   "\n-----END CERTIFICATE-----\n";
                    java.nio.file.Files.write(outputFile.toPath(), pemCert.getBytes());
                } else {
                    // Export as DER
                    java.nio.file.Files.write(outputFile.toPath(), cert.getEncoded());
                }

                JOptionPane.showMessageDialog(this,
                    "Certificate exported successfully to:\n" + outputFile.getAbsolutePath(),
                    "Export Success",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to export certificate:\n" + ex.getMessage(),
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Shows full certificate details in a text dialog.
     */
    private void viewFullCertificate() {
        try {
            java.security.cert.X509Certificate cert = result.getSignerCertificate();
            if (cert == null) {
                JOptionPane.showMessageDialog(this,
                    "No certificate available",
                    "View Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Create dialog
            JDialog certDialog = new JDialog(this, "Certificate Details", true);
            certDialog.setSize(800, 600);
            certDialog.setLocationRelativeTo(this);

            // Create text area with full certificate details
            JTextArea textArea = new JTextArea();
            textArea.setFont(UIConstants.Fonts.MONOSPACE);
            textArea.setEditable(false);
            textArea.setBackground(new Color(30, 30, 30));
            textArea.setForeground(UIConstants.Colors.TEXT_PRIMARY);
            textArea.setCaretColor(Color.WHITE);

            // Build certificate details text
            StringBuilder details = new StringBuilder();
            details.append("X.509 Certificate Details\n");
            details.append(repeatChar('=', 80)).append("\n\n");

            details.append("Version: ").append(cert.getVersion()).append("\n");
            details.append("Serial Number: ").append(cert.getSerialNumber().toString(16).toUpperCase()).append("\n\n");

            details.append("Subject:\n  ").append(cert.getSubjectDN().toString()).append("\n\n");
            details.append("Issuer:\n  ").append(cert.getIssuerDN().toString()).append("\n\n");

            details.append("Valid From: ").append(DATE_FORMAT.format(cert.getNotBefore())).append("\n");
            details.append("Valid To: ").append(DATE_FORMAT.format(cert.getNotAfter())).append("\n\n");

            details.append("Signature Algorithm: ").append(cert.getSigAlgName()).append("\n");
            details.append("Signature Algorithm OID: ").append(cert.getSigAlgOID()).append("\n\n");

            details.append("Public Key Algorithm: ").append(cert.getPublicKey().getAlgorithm()).append("\n");
            details.append("Public Key Size: ").append(CertificateUtils.getKeySize(cert.getPublicKey())).append(" bits\n\n");

            // Key usage
            java.util.List<String> keyUsages = CertificateUtils.getKeyUsageStrings(cert);
            if (!keyUsages.isEmpty()) {
                details.append("Key Usage: ").append(String.join(", ", keyUsages)).append("\n");
            }

            // Extended key usage
            java.util.List<String> extKeyUsages = CertificateUtils.getExtendedKeyUsageStrings(cert);
            if (!extKeyUsages.isEmpty()) {
                details.append("Extended Key Usage: ").append(String.join(", ", extKeyUsages)).append("\n");
            }

            details.append("\n").append(repeatChar('-', 80)).append("\n");
            details.append("PEM Encoded:\n").append(repeatChar('-', 80)).append("\n\n");
            details.append("-----BEGIN CERTIFICATE-----\n");
            details.append(java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(cert.getEncoded()));
            details.append("\n-----END CERTIFICATE-----\n");

            textArea.setText(details.toString());
            textArea.setCaretPosition(0);

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setBorder(UIConstants.Padding.SMALL);

            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBackground(BG_COLOR);
            JButton copyBtn = new JButton("Copy to Clipboard");
            copyBtn.addActionListener(e -> {
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(textArea.getText());
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                JOptionPane.showMessageDialog(certDialog, "Certificate details copied to clipboard", "Copied", JOptionPane.INFORMATION_MESSAGE);
            });
            JButton closeBtn = new JButton("Close");
            closeBtn.addActionListener(e -> certDialog.dispose());
            buttonPanel.add(copyBtn);
            buttonPanel.add(closeBtn);

            certDialog.setLayout(new BorderLayout());
            certDialog.add(scrollPane, BorderLayout.CENTER);
            certDialog.add(buttonPanel, BorderLayout.SOUTH);

            certDialog.setVisible(true);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to view certificate:\n" + ex.getMessage(),
                "View Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private Color getStatusColor(VerificationStatus status) {
        switch (status) {
            case VALID:
                return VALID_COLOR;
            case UNKNOWN:
                return UNKNOWN_COLOR;
            case INVALID:
                return INVALID_COLOR;
            default:
                return UIConstants.Colors.TEXT_DISABLED;
        }
    }

    /**
     * Java 8 compatible repeat character method.
     * Replacement for String.repeat() which is available only from Java 11+
     */
    private String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

}
