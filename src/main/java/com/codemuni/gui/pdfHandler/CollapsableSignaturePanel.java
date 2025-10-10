package com.codemuni.gui.pdfHandler;

import com.codemuni.core.keyStoresProvider.X509SubjectUtils;
import com.codemuni.service.SignatureVerificationService;
import com.codemuni.service.SignatureVerificationService.SignatureVerificationResult;
import com.codemuni.service.SignatureVerificationService.VerificationStatus;
import com.codemuni.utils.IconGenerator;
import com.codemuni.utils.IconLoader;
import com.codemuni.utils.SignatureColors;
import com.formdev.flatlaf.ui.FlatUIUtils;

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
    private static final String ICON_VALID = "green_tick.png";
    private static final String ICON_INFO = "info.png";
    private static final String ICON_CERTIFICATE = "certificate.png";

    // Date format for signature items (shared across all items)
    private static final SimpleDateFormat SIGNATURE_DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    private final JPanel contentPanel;
    private final JPanel headerPanel;
    private final JPanel signaturesListPanel;
    private final JLabel emptyLabel;

    private boolean visible = true;
    private boolean closed = true; // Initially hidden as per requirement
    private Timer animationTimer;
    private int currentWidth = 0; // Start with 0 width since initially hidden
    private int targetWidth = 0; // Start with 0 width since initially hidden
    private Runnable onCloseCallback;
    private SignatureColorManager colorManager;
    private Runnable onVerifyAllCallback;
    private JLabel verificationStatusLabel; // For showing verification progress
    private SignatureSelectionListener signatureSelectionListener; // For highlighting signature rectangles

    /**
     * Listener interface for signature selection events.
     */
    public interface SignatureSelectionListener {
        void onSignatureSelected(String fieldName);
    }

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

        JLabel titleLabel = new JLabel("Signatures");
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
            private ImageIcon normalIcon = IconGenerator.createVerifyAllIcon(16, Color.LIGHT_GRAY);
            private ImageIcon hoverIcon = IconGenerator.createVerifyAllIcon(16, SignatureColors.VALID_COLOR);

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
    private void closePanel() {
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
                            new EmptyBorder(8, 10, 8, 10)
                    )
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

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

            // === TOP: Signer name and status icon ===
            JPanel topPanel = new JPanel(new BorderLayout(8, 0));
            topPanel.setOpaque(false);
            topPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Status icon
            JLabel statusIcon = createStatusIcon();
            topPanel.add(statusIcon, BorderLayout.WEST);

            // Signer name
            String signerName = X509SubjectUtils.extractCommonNameFromDN(result.getCertificateSubject());
            if (signerName == null || signerName.isEmpty()) {
                signerName = result.getFieldName();
            }

            JLabel nameLabel = new JLabel(truncateText(signerName, 25));
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            nameLabel.setForeground(Color.WHITE);
            topPanel.add(nameLabel, BorderLayout.CENTER);

            panel.add(topPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 6)));

            // === ESSENTIAL VERIFICATION INFO ===
            JPanel essentialInfo = new JPanel();
            essentialInfo.setLayout(new BoxLayout(essentialInfo, BoxLayout.Y_AXIS));
            essentialInfo.setOpaque(false);
            essentialInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Simple, easy-to-understand labels
            addCompactStatusRow(essentialInfo, "Signature:", result.isSignatureValid());

            // Tamper Status (Document Intact)
            addCompactStatusRow(essentialInfo, "Document:", result.isDocumentIntact());

            // Timestamp Enabled
            addCompactStatusRow(essentialInfo, "Timestamp:", result.isTimestampValid());

            // LTV Enabled
            addCompactStatusRow(essentialInfo, "Long Term:", result.hasLTV());

            panel.add(essentialInfo);
            panel.add(Box.createRigidArea(new Dimension(0, 8)));

            // === SIGNATURE PROPERTIES BUTTON ===
            JButton propertiesBtn = new JButton("Signature Properties");
            propertiesBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            propertiesBtn.setFocusPainted(false);
            propertiesBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            propertiesBtn.setMaximumSize(new Dimension(160, 26));
            propertiesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            propertiesBtn.addActionListener(e -> showDetailedProperties());
            panel.add(propertiesBtn);

            return panel;
        }

        /**
         * Adds a compact status row with icon (for essential info display).
         */
        private void addCompactStatusRow(JPanel panel, String label, boolean status) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 1));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(300, 18));

            JLabel labelComp = new JLabel(label);
            labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            labelComp.setForeground(Color.LIGHT_GRAY);

            // Use small icon for status
            ImageIcon statusIcon = status ?
                IconLoader.loadIcon(ICON_VALID, 12, 12) :
                createCrossIcon(12);

            JLabel iconLabel = new JLabel(statusIcon);

            JLabel valueText = new JLabel(status ? "Yes" : "No");
            valueText.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            valueText.setForeground(status ? SignatureColors.VALID_COLOR : SignatureColors.INVALID_COLOR);

            row.add(labelComp);
            row.add(iconLabel);
            row.add(valueText);

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


        /**
         * Creates a red cross icon for invalid status.
         */
        private ImageIcon createCrossIcon(int size) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(SignatureColors.INVALID_COLOR);
            g2d.setStroke(new BasicStroke(2f));

            int padding = 2;
            g2d.drawLine(padding, padding, size - padding, size - padding);
            g2d.drawLine(size - padding, padding, padding, size - padding);

            g2d.dispose();
            return new ImageIcon(img);
        }


        private JLabel createStatusIcon() {
            VerificationStatus status = result.getOverallStatus();
            ImageIcon icon;

            switch (status) {
                case VALID:
                    icon = IconLoader.loadIcon(ICON_VALID, 24, 24);
                    break;
                case UNKNOWN:
                    icon = createQuestionIcon(24);
                    break;
                case INVALID:
                    icon = createCrossIcon(24);
                    break;
                default:
                    icon = createQuestionIcon(24);
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
