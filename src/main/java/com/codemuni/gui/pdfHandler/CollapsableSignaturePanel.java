package com.codemuni.gui.pdfHandler;

import com.codemuni.service.SignatureVerificationService.SignatureVerificationResult;
import com.codemuni.service.SignatureVerificationService.VerificationStatus;
import com.codemuni.utils.IconGenerator;
import com.codemuni.utils.IconLoader;
import com.codemuni.utils.SignatureColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.List;

import static com.codemuni.utils.SignatureColors.PANEL_BG;

/**
 * Collapsable signature panel overlay (Adobe Reader DC style).
 * - Appears as overlay on right side of PDF viewer
 * - Can be collapsed/expanded with toggle button
 * - Shows signature verification status and details
 */
public class CollapsableSignaturePanel extends JPanel {

    private static final int PANEL_WIDTH = 350;
    private static final int ANIMATION_STEPS = 10;
    private static final int ANIMATION_DELAY = 20;
    private static final int ICON_SIZE = 16;

    // Icon files
    private static final String ICON_VALID = "check_circle.png";
    private static final String ICON_INVALID = "cross_circle.png";
    private static final String ICON_UNKNOWN = "question_circle.png";

    // Date format for signature items (shared across all items)
    private static final SimpleDateFormat SIGNATURE_DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    private final JPanel contentPanel;
    private final JPanel headerPanel;
    private final JPanel signaturesListPanel;
    private final JLabel emptyLabel;

    private final boolean visible = true;
    private boolean closed = true; // Initially hidden as per requirement
    private Timer animationTimer;
    private int currentWidth = 0; // Start with 0 width since initially hidden
    private int targetWidth = 0; // Start with 0 width since initially hidden
    private Runnable onCloseCallback;
    private SignatureColorManager colorManager;
    private Runnable onVerifyAllCallback;
    private JLabel verificationStatusLabel; // For showing verification progress
    private SignatureSelectionListener signatureSelectionListener; // For highlighting signature rectangles

    public CollapsableSignaturePanel() {
        setLayout(new BorderLayout());
        setBackground(PANEL_BG);
        setPreferredSize(new Dimension(0, 0)); // Initially hidden with 0 width
        setOpaque(false);
        setVisible(false); // Initially invisible

        // Header panel with toggle button
        headerPanel = createHeaderPanel();

        // Content panel (scrollable signatures list)
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(PANEL_BG);
        contentPanel.setOpaque(false);

        // Signatures list panel
        signaturesListPanel = new JPanel();
        signaturesListPanel.setLayout(new BoxLayout(signaturesListPanel, BoxLayout.Y_AXIS));
        signaturesListPanel.setBackground(new Color(0, 0, 0, 0));
        signaturesListPanel.setOpaque(false);

        // Empty state label
        emptyLabel = new JLabel("<html><center>No signatures<br>found in this<br>document</center></html>");
        emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));

        // Scroll pane
        JScrollPane scrollPane = new JScrollPane(signaturesListPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        contentPanel.add(scrollPane, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        // Initially show empty state
        showEmptyState();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SignatureColors.HEADER_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SignatureColors.BORDER_COLOR),
                new EmptyBorder(10, 12, 10, 12)
        ));

        // Left panel: Title + Status Label
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Signature Panel");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Verification status label (for showing progress)
        verificationStatusLabel = new JLabel("");
        verificationStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        verificationStatusLabel.setForeground(Color.GRAY);
        verificationStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        verificationStatusLabel.setVisible(false); // Initially hidden

        leftPanel.add(titleLabel);
        leftPanel.add(verificationStatusLabel);

        // Button panel (verify all + close button)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);

        // Verify all button (with icon)
        JButton verifyAllBtn = new JButton(IconGenerator.createVerifyAllIcon(16, Color.LIGHT_GRAY));
        verifyAllBtn.setFocusPainted(false);
        verifyAllBtn.setBorderPainted(false);
        verifyAllBtn.setContentAreaFilled(false);
        verifyAllBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        verifyAllBtn.setPreferredSize(new Dimension(30, 30));
        verifyAllBtn.setToolTipText("Verify All Signatures");

        verifyAllBtn.addMouseListener(new MouseAdapter() {
            private final ImageIcon normalIcon = IconGenerator.createVerifyAllIcon(16, Color.LIGHT_GRAY);
            private final ImageIcon hoverIcon = IconGenerator.createVerifyAllIcon(16, SignatureColors.VALID_COLOR);

            @Override
            public void mouseEntered(MouseEvent e) {
                verifyAllBtn.setIcon(hoverIcon);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                verifyAllBtn.setIcon(normalIcon);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onVerifyAllCallback != null) {
                    onVerifyAllCallback.run();
                }
            }
        });

        // Close button (X)
        JButton closeBtn = new JButton("\u00D7"); // × symbol
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setForeground(Color.LIGHT_GRAY);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setPreferredSize(new Dimension(30, 30));
        closeBtn.setToolTipText("Close signature panel");

        closeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeBtn.setForeground(SignatureColors.INVALID_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeBtn.setForeground(Color.LIGHT_GRAY);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                closePanel();
            }
        });

        buttonPanel.add(verifyAllBtn);
        buttonPanel.add(closeBtn);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Closes the panel with smooth animation.
     */
    public void closePanel() {
        if (closed) return;

        closed = true;

        // Hide panel immediately - no animation needed
        headerPanel.setVisible(false);
        contentPanel.setVisible(false);
        setVisible(false);

        // Stop any running animation
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }

        // Reset width
        currentWidth = 0;
        targetWidth = 0;
        setPreferredSize(new Dimension(0, getHeight()));

        // Update parent
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }

        // Notify callback that panel is closed
        if (onCloseCallback != null) {
            SwingUtilities.invokeLater(onCloseCallback);
        }
    }

    /**
     * Sets callback to be called when panel is closed.
     */
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    /**
     * Sets callback to be called when verify all button is clicked.
     */
    public void setOnVerifyAllCallback(Runnable callback) {
        this.onVerifyAllCallback = callback;
    }

    /**
     * Shows verification status in header (e.g., "Verifying signatures...").
     */
    public void setVerificationStatus(String status) {
        if (verificationStatusLabel != null) {
            if (status == null || status.isEmpty()) {
                verificationStatusLabel.setVisible(false);
            } else {
                verificationStatusLabel.setText(status);
                verificationStatusLabel.setVisible(true);
            }
        }
    }

    /**
     * Sets listener for signature selection events.
     */
    public void setSignatureSelectionListener(SignatureSelectionListener listener) {
        this.signatureSelectionListener = listener;
    }

    /**
     * Opens/shows the panel with smooth animation.
     */
    public void openPanel() {
        if (!closed) {
            // Already open, do nothing
            return;
        }

        // Mark as open
        closed = false;

        // Make panel visible
        setVisible(true);

        // Show content immediately
        headerPanel.setVisible(true);
        contentPanel.setVisible(true);

        // Stop any running animation
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }

        // Start from 0 and animate to full width
        currentWidth = 0;
        targetWidth = PANEL_WIDTH;

        // Animate panel width from 0 to PANEL_WIDTH
        animationTimer = new Timer(ANIMATION_DELAY, e -> {
            int diff = targetWidth - currentWidth;
            int step = Math.max(diff / ANIMATION_STEPS, 1);

            if (currentWidth >= targetWidth) {
                currentWidth = targetWidth;
                ((Timer) e.getSource()).stop();

                // Notify parent to update layout after animation completes
                Container parent = getParent();
                if (parent != null) {
                    parent.revalidate();
                    parent.repaint();
                }

                // Trigger callback to update floating button visibility
                if (onCloseCallback != null) {
                    SwingUtilities.invokeLater(onCloseCallback);
                }
            } else {
                currentWidth += step;
            }

            setPreferredSize(new Dimension(currentWidth, getHeight()));
            revalidate();

            // Update parent during animation
            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        });
        animationTimer.start();
    }

    /**
     * Sets the color manager for signature color coding.
     */
    public void setColorManager(SignatureColorManager colorManager) {
        this.colorManager = colorManager;
    }

    /**
     * Updates the panel with signature verification results.
     */
    public void updateSignatures(List<SignatureVerificationResult> verificationResults) {
        signaturesListPanel.removeAll();

        if (verificationResults == null || verificationResults.isEmpty()) {
            showEmptyState();
        } else {
            // Add signature items with color coding
            for (SignatureVerificationResult result : verificationResults) {
                Color signatureColor = colorManager != null ?
                        colorManager.getColorForSignature(result.getFieldName()) :
                        Color.GRAY;
                SignatureItem item = new SignatureItem(result, signatureColor);
                signaturesListPanel.add(item);
                signaturesListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }

        signaturesListPanel.revalidate();
        signaturesListPanel.repaint();
    }

    /**
     * Clears all signatures and shows empty state.
     */
    public void clearSignatures() {
        signaturesListPanel.removeAll();
        showEmptyState();
        signaturesListPanel.revalidate();
        signaturesListPanel.repaint();
    }

    private void showEmptyState() {
        signaturesListPanel.removeAll();
        signaturesListPanel.add(emptyLabel);
    }

    /**
     * Returns true if panel is currently closed.
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Paint semi-transparent background
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw rounded rectangle background
        g2d.setColor(PANEL_BG);
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 0, 0);

        // Draw left border
        g2d.setColor(SignatureColors.BORDER_COLOR);
        g2d.drawLine(0, 0, 0, getHeight());

        g2d.dispose();
        super.paintComponent(g);
    }

    /**
     * Listener interface for signature selection events.
     */
    public interface SignatureSelectionListener {
        void onSignatureSelected(String fieldName);
    }

    /**
     * Individual signature item (compact for overlay).
     */
    private class SignatureItem extends JPanel {

        private final SignatureVerificationResult result;
        private final Color signatureColor;

        public SignatureItem(SignatureVerificationResult result, Color signatureColor) {
            this.result = result;
            this.signatureColor = signatureColor;
            setLayout(new BorderLayout());
            setBackground(SignatureColors.ITEM_BG);
            setOpaque(true);

            // Use signature color for left border (thicker to be visible)
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, signatureColor), // Left color bar
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(SignatureColors.BORDER_COLOR, 1),
                            new EmptyBorder(8, 8, 8, 8)
                    )
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

            // Main content panel
            JPanel mainPanel = createMainPanel();
            add(mainPanel, BorderLayout.CENTER);

            // Click to highlight signature rectangle + hover effect
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // Requirement 4: Clicking signature in panel highlights rectangle on PDF
                    if (signatureSelectionListener != null) {
                        signatureSelectionListener.onSignatureSelected(result.getFieldName());
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(SignatureColors.ITEM_HOVER_BG);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(SignatureColors.ITEM_BG);
                }
            });
        }


        private JPanel createMainPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);

            // === Header with name, status, and View Details button ===
            JPanel headerPanel = new JPanel(new BorderLayout(0, 0));
            headerPanel.setOpaque(false);
            headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel statusIcon = createStatusIcon();
            headerPanel.add(statusIcon, BorderLayout.WEST);

            JLabel nameLabel = new JLabel(result.getFieldName());
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            nameLabel.setForeground(Color.WHITE);
            headerPanel.add(nameLabel, BorderLayout.CENTER);

            // View Details button in header (using default UI)
            JLabel propertiesBtn = new JLabel("More Details");
            propertiesBtn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            propertiesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            propertiesBtn.setForeground(new Color(0, 102, 204)); // link color

            propertiesBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showDetailedProperties();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    propertiesBtn.setText("<html><u>More Details</u></html>"); // underline on hover
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    propertiesBtn.setText("More Details"); // remove underline
                }
            });

            headerPanel.add(propertiesBtn, BorderLayout.EAST);



            panel.add(headerPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 8)));

            // === Summary Message and Verification Details in one row ===
            JPanel summaryRow = getSummaryPanel();

            panel.add(summaryRow);
            panel.add(Box.createRigidArea(new Dimension(0, 6)));

            JPanel verificationDetailsPanel = createVerificationDetailsPanel(result);
            verificationDetailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(verificationDetailsPanel);

            return panel;
        }

        private JPanel getSummaryPanel() {
            JPanel summaryRow = new JPanel(new BorderLayout(2, 0));
            summaryRow.setOpaque(false);
            summaryRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel summaryLabel = getSummaryLabel();

            JLabel detailsTitle = new JLabel("Verification Details:");
            detailsTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
            detailsTitle.setForeground(Color.GRAY);

            summaryRow.add(summaryLabel, BorderLayout.EAST);
            summaryRow.add(detailsTitle, BorderLayout.WEST);
            return summaryRow;
        }

        private JLabel getSummaryLabel() {
            JLabel summaryLabel = new JLabel();
            summaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            // Adobe Reader DC style messages with priority order
            if (result.getOverallStatus() == VerificationStatus.VALID) {
                summaryLabel.setText("Signature is valid and trusted.");
                summaryLabel.setForeground(SignatureColors.VALID_COLOR);
            } else if (result.getOverallStatus() == VerificationStatus.INVALID) {
                // Provide specific reason for invalidity in priority order
                // Priority: Document Integrity > Signature > Revocation > Certificate Validity
                if (!result.isDocumentIntact()) {
                    summaryLabel.setText("Document has been modified after signing.");
                } else if (!result.isSignatureValid()) {
                    summaryLabel.setText("Signature is invalid or corrupted.");
                } else if (result.isCertificateRevoked()) {
                    summaryLabel.setText("Certificate has been revoked.");
                } else if (!result.isCertificateValid()) {
                    summaryLabel.setText("Certificate has expired or is not yet valid.");
                } else {
                    summaryLabel.setText("Signature verification failed.");
                }
                summaryLabel.setForeground(SignatureColors.INVALID_COLOR);
            } else {
                // UNKNOWN status - usually means valid but not trusted
                if (result.isSignatureValid() && result.isDocumentIntact() && !result.isCertificateTrusted()) {
                    summaryLabel.setText("Signature is valid but identity could not be verified.");
                } else {
                    summaryLabel.setText("Verification status is unknown.");
                }
                summaryLabel.setForeground(SignatureColors.UNKNOWN_COLOR);
            }
            return summaryLabel;
        }

        private JPanel createVerificationDetailsPanel(SignatureVerificationResult result) {
            JPanel detailsPanel = new JPanel();
            detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
            detailsPanel.setOpaque(false);
            // Added 10px left padding as requested
            detailsPanel.setBorder(new EmptyBorder(4, 10, 4, 0));

            // Core verification checks (Adobe Reader DC order)
            addCompactStatusRow(detailsPanel, "Signature Verified:", result.isSignatureValid(), false);
            addCompactStatusRow(detailsPanel, "Document Unchanged:", result.isDocumentIntact(), false);
            addCompactStatusRow(detailsPanel, "Certificate Valid:", result.isCertificateValid(), false);
            addCompactStatusRow(detailsPanel, "Certificate Trusted:", result.isCertificateTrusted(), false);
            
            // Optional features (show as enabled/not enabled)
            addCompactStatusRow(detailsPanel, "Timestamp:", result.isTimestampValid(), true);
            addCompactStatusRow(detailsPanel, "Long-Term Validation:", result.hasLTV(), true);

            return detailsPanel;
        }

        private void addCompactStatusRow(JPanel panel, String label, boolean status, boolean isIncludedType) {
            JPanel row = new JPanel(new GridBagLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(0, 0, 0, 6);

            // Icon (✔ or ✖)
            JLabel iconLabel = new JLabel(status
                    ? IconLoader.loadIcon(ICON_VALID, 14, 14)
                    : IconLoader.loadIcon(ICON_INVALID, 14, 14));
            gbc.gridx = 0;
            gbc.weightx = 0;
            row.add(iconLabel, gbc);

            // Label text (left)
            JLabel labelText = new JLabel(label);
            labelText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            labelText.setForeground(new Color(200, 200, 200));
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            row.add(labelText, gbc);

            // Value text (right aligned)
            String value;
            if (isIncludedType) {
                value = status ? "Enabled" : "Not Enabled";
            } else {
                value = status ? "Yes" : "No";
            }

            JLabel valueLabel = new JLabel(value);
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            valueLabel.setForeground(status ? new Color(90, 220, 120) : new Color(255, 100, 100));
            valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(0, 6, 0, 0);
            row.add(valueLabel, gbc);

            panel.add(row);
        }


        /**
         * Shows detailed signature properties dialog.
         */
        private void showDetailedProperties() {
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
            if (parentFrame != null) {
                SignaturePropertiesDialog dialog = new SignaturePropertiesDialog(
                        parentFrame, result, signatureColor);
                dialog.setVisible(true);
            }
        }

        private JLabel createStatusIcon() {
            VerificationStatus status = result.getOverallStatus();
            ImageIcon icon;

            switch (status) {
                case VALID:
                    icon = IconLoader.loadIcon(ICON_VALID, 24, 24);
                    break;
                case INVALID:
                    icon = IconLoader.loadIcon(ICON_INVALID, 24, 24);
                    break;
                default:
                    icon = IconLoader.loadIcon(ICON_UNKNOWN, 24, 24);
            }

            JLabel iconLabel = new JLabel(icon);
            iconLabel.setPreferredSize(new Dimension(24, 24));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

            return iconLabel;
        }

        /**
         * Creates a question mark icon for unknown status.
         */
        private ImageIcon createQuestionIcon(int size) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(SignatureColors.UNKNOWN_COLOR);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, size - 4));

            // Draw question mark
            FontMetrics fm = g2d.getFontMetrics();
            String text = "?";
            int x = (size - fm.stringWidth(text)) / 2;
            int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
            g2d.drawString(text, x, y);

            g2d.dispose();
            return new ImageIcon(img);
        }

        private Color getStatusColor(VerificationStatus status) {
            switch (status) {
                case VALID:
                    return SignatureColors.VALID_COLOR;
                case UNKNOWN:
                    return SignatureColors.UNKNOWN_COLOR;
                case INVALID:
                    return SignatureColors.INVALID_COLOR;
                default:
                    return Color.GRAY;
            }
        }

        private String truncateText(String text, int maxLength) {
            if (text == null || text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength - 3) + "...";
        }
    }
}
