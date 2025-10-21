package com.codemuni.gui;

import com.codemuni.core.keyStoresProvider.X509SubjectUtils;
import com.codemuni.utils.StringFormatUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;

public class CertificateDetailsDialog extends JDialog {

    private static final Color COLOR_BACKGROUND = new Color(34, 34, 34);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(150, 150, 150);
    private static final Font MONOSPACED = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 14);

    private final X509Certificate certificate;

    public CertificateDetailsDialog(Frame owner, X509Certificate certificate) {
        super(owner, "Certificate Details", true);
        this.certificate = certificate;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(COLOR_BACKGROUND);
        setResizable(false);
        setPreferredSize(new Dimension(450, 500));

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(COLOR_BACKGROUND);
        tabbedPane.setForeground(COLOR_TEXT_PRIMARY);

        // Add tabs
        tabbedPane.addTab("X509 Details", createDetailsPanel());
        tabbedPane.addTab("PEM/Base64", createPemPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // Add close button
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(COLOR_BACKGROUND);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setVisible(true);
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(0, 10));
        panel.setBackground(COLOR_BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Add validation status panel at the top
        panel.add(createValidationStatusPanel(), BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(COLOR_BACKGROUND);

        // Subject section
        addSection(contentPanel, "Subject", formatDN(certificate.getSubjectX500Principal().getName()));

        // Issuer section
        addSection(contentPanel, "Issuer", formatDN(certificate.getIssuerX500Principal().getName()));

        // Validity section
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        String validity = String.format("From: %s%nTo: %s",
                sdf.format(certificate.getNotBefore()),
                sdf.format(certificate.getNotAfter()));
        addSection(contentPanel, "Validity", validity);

        // Certificate information
        addSection(contentPanel, "Serial Number", certificate.getSerialNumber().toString(16).toUpperCase());
        addSection(contentPanel, "Version", "v" + (certificate.getVersion() + 1));
        addSection(contentPanel, "Signature Algorithm", certificate.getSigAlgName());

        // Key usage
        try {
            if (certificate.getKeyUsage() != null) {
                addSection(contentPanel, "Key Usage", formatKeyUsage(certificate.getKeyUsage()));
            }
        } catch (Exception e) {
            // Key usage not available
        }

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(COLOR_BACKGROUND);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPemPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(COLOR_BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Add copy button at the top
        JButton copyButton = new JButton("Copy PEM to Clipboard");
        copyButton.addActionListener(e -> copyPemToClipboard());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        buttonPanel.add(copyButton);
        panel.add(buttonPanel, BorderLayout.NORTH);

        // Add PEM content in a scrollable area
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(COLOR_BACKGROUND);

        JTextArea pemArea = new JTextArea() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = Math.min(d.width, 430); // Account for padding
                return d;
            }
        };
        pemArea.setEditable(false);
        pemArea.setFont(MONOSPACED);
        pemArea.setBackground(COLOR_BACKGROUND);
        pemArea.setForeground(COLOR_TEXT_PRIMARY);
        pemArea.setCaretColor(COLOR_TEXT_PRIMARY);
        pemArea.setLineWrap(true);
        pemArea.setWrapStyleWord(true);
        pemArea.setMargin(new Insets(0, 0, 0, 0));

        try {
            pemArea.setText(StringFormatUtils.toPemFormat(certificate));
        } catch (CertificateEncodingException e) {
            pemArea.setText("Error encoding certificate: " + e.getMessage());
        }

        JScrollPane scrollPane = new JScrollPane(pemArea);
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private void addSection(Container parent, String title, String content) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(COLOR_BACKGROUND);
        section.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(COLOR_TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        JTextArea contentArea = new JTextArea(content) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = Math.min(d.width, 430); // Account for padding
                return d;
            }
        };
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(MONOSPACED);
        contentArea.setBackground(COLOR_BACKGROUND);
        contentArea.setForeground(COLOR_TEXT_SECONDARY);
        contentArea.setBorder(BorderFactory.createEmptyBorder(5, 15, 0, 0));
        contentArea.setMargin(new Insets(0, 0, 0, 0));

        section.add(titleLabel, BorderLayout.NORTH);
        section.add(contentArea, BorderLayout.CENTER);

        parent.add(section);
        parent.add(Box.createRigidArea(new Dimension(0, 5)));
    }

    private String formatDN(String dn) {
        return X509SubjectUtils.formatDN(dn);
    }

    private String formatKeyUsage(boolean[] keyUsage) {
        if (keyUsage == null) return "None";

        StringBuilder sb = new StringBuilder();
        String[] usageNames = {
                "Digital Signature", "Non-Repudiation", "Key Encipherment",
                "Data Encipherment", "Key Agreement", "Key Cert Sign",
                "CRL Sign", "Encipher Only", "Decipher Only"
        };

        for (int i = 0; i < Math.min(keyUsage.length, usageNames.length); i++) {
            if (keyUsage[i]) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(usageNames[i]);
            }
        }

        return sb.length() > 0 ? sb.toString() : "None";
    }

    private JPanel createValidationStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout(10, 5));
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_TEXT_SECONDARY, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));
        statusPanel.setOpaque(false);

        // Validation icon and text
        JLabel statusIcon = new JLabel();
        JLabel statusText = new JLabel();

        try {
            certificate.checkValidity();
            statusIcon.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
            statusText.setText("Certificate is valid");
            statusText.setForeground(new Color(100, 200, 100));
        } catch (CertificateExpiredException e) {
            statusIcon.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
            statusText.setText("Certificate expired on " + new SimpleDateFormat("yyyy-MM-dd").format(certificate.getNotAfter()));
            statusText.setForeground(new Color(255, 100, 100));
        } catch (CertificateNotYetValidException e) {
            statusIcon.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
            statusText.setText("Certificate not valid until " + new SimpleDateFormat("yyyy-MM-dd").format(certificate.getNotBefore()));
            statusText.setForeground(new Color(255, 200, 100));
        } catch (Exception e) {
            statusIcon.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
            statusText.setText("Certificate validation error: " + e.getMessage());
            statusText.setForeground(new Color(255, 100, 100));
        }

        JPanel statusContent = new JPanel(new BorderLayout(10, 0));
        statusContent.setOpaque(false);
        statusContent.add(statusIcon, BorderLayout.WEST);
        statusContent.add(statusText, BorderLayout.CENTER);

        // Add validity period
        JLabel validityPeriod = new JLabel(String.format("Valid from %s to %s",
                new SimpleDateFormat("yyyy-MM-dd").format(certificate.getNotBefore()),
                new SimpleDateFormat("yyyy-MM-dd").format(certificate.getNotAfter())));
        validityPeriod.setForeground(COLOR_TEXT_SECONDARY);
        validityPeriod.setHorizontalAlignment(SwingConstants.RIGHT);

        statusPanel.add(statusContent, BorderLayout.WEST);
        statusPanel.add(validityPeriod, BorderLayout.CENTER);

        return statusPanel;
    }

    private void copyPemToClipboard() {
        try {
            String pem = StringFormatUtils.toPemFormat(certificate);
            StringSelection selection = new StringSelection(pem);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            JOptionPane.showMessageDialog(this,
                    "PEM content copied to clipboard!",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to copy PEM: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
