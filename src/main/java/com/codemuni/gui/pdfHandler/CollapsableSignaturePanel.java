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
 * Collapsable signature panel overlay (PDF viewer style).
 * - Appears as overlay on right side of PDF viewer
 * - Can be collapsed/expanded with toggle button
 * - Shows signature verification status and details
 */
public class CollapsableSignaturePanel extends JPanel {

    private static final int PANEL_WIDTH = 380;
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
    private final JLabel signatureCountLabel;

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

        // Initialize signature count label
        signatureCountLabel = new JLabel("");

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
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Modern gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(45, 50, 60),
                    0, getHeight(), new Color(35, 40, 48)
                );
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());

                g2d.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 40)),
                new EmptyBorder(14, 15, 14, 15)
        ));

        // Left panel: Title + Count + Status Label
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        // Title with modern styling
        JLabel titleLabel = new JLabel("Digital Signatures");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setForeground(new Color(255, 255, 255));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Signature count label
        signatureCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        signatureCountLabel.setForeground(new Color(170, 180, 195));
        signatureCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Verification status label (for showing progress)
        verificationStatusLabel = new JLabel("");
        verificationStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        verificationStatusLabel.setForeground(new Color(150, 160, 175));
        verificationStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        verificationStatusLabel.setVisible(false); // Initially hidden

        leftPanel.add(titleLabel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        leftPanel.add(signatureCountLabel);
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
            signatureCountLabel.setText("");
        } else {
            // Update signature count with validation statistics
            int validCount = 0;
            int invalidCount = 0;
            int unknownCount = 0;

            for (SignatureVerificationResult result : verificationResults) {
                VerificationStatus status = result.getOverallStatus();
                if (status == VerificationStatus.VALID) validCount++;
                else if (status == VerificationStatus.INVALID) invalidCount++;
                else unknownCount++;
            }

            String countText = verificationResults.size() + " signature" + (verificationResults.size() > 1 ? "s" : "");
            if (validCount > 0 || invalidCount > 0 || unknownCount > 0) {
                countText += " • ";
                if (validCount > 0) countText += validCount + " valid ";
                if (invalidCount > 0) countText += invalidCount + " invalid ";
                if (unknownCount > 0) countText += unknownCount + " unknown";
            }
            signatureCountLabel.setText(countText.trim());

            // Add signature items with color coding and modern spacing
            for (SignatureVerificationResult result : verificationResults) {
                Color signatureColor = colorManager != null ?
                        colorManager.getColorForSignature(result.getFieldName()) :
                        Color.GRAY;
                SignatureItem item = new SignatureItem(result, signatureColor);
                signaturesListPanel.add(item);
                signaturesListPanel.add(Box.createRigidArea(new Dimension(0, 10)));
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
     * Individual signature item with modern card design.
     */
    private class SignatureItem extends JPanel {

        private final SignatureVerificationResult result;
        private final Color signatureColor;
        private boolean isHovered = false;

        public SignatureItem(SignatureVerificationResult result, Color signatureColor) {
            this.result = result;
            this.signatureColor = signatureColor;
            setLayout(new BorderLayout());
            setBackground(new Color(40, 45, 52)); // Modern dark card background
            setOpaque(false); // For custom painting

            // Modern border with accent color and subtle shadow
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, signatureColor), // Left accent bar
                    BorderFactory.createCompoundBorder(
                            new EmptyBorder(1, 1, 1, 1), // Space for shadow
                            new EmptyBorder(12, 12, 12, 12) // Inner padding
                    )
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

            // Main content panel
            JPanel mainPanel = createMainPanel();
            add(mainPanel, BorderLayout.CENTER);

            // Click to highlight signature rectangle + smooth hover effect
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
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw shadow for depth effect
            if (isHovered) {
                g2d.setColor(new Color(0, 0, 0, 40));
                g2d.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);
            } else {
                g2d.setColor(new Color(0, 0, 0, 20));
                g2d.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
            }

            // Draw card background with subtle gradient
            GradientPaint gradient = new GradientPaint(
                0, 0, isHovered ? new Color(48, 53, 60) : new Color(40, 45, 52),
                0, getHeight(), isHovered ? new Color(42, 47, 54) : new Color(35, 40, 47)
            );
            g2d.setPaint(gradient);
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            // Draw subtle border
            g2d.setColor(new Color(60, 65, 72));
            g2d.setStroke(new BasicStroke(1f));
            g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            g2d.dispose();
            super.paintComponent(g);
        }


        private JPanel createMainPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);

            // === Header with status icon, name, and Details button ===
            JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
            headerPanel.setOpaque(false);
            headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            // Left: Status icon
            JLabel statusIcon = createStatusIcon();
            JPanel iconWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            iconWrapper.setOpaque(false);
            iconWrapper.add(statusIcon);
            headerPanel.add(iconWrapper, BorderLayout.WEST);

            // Center: Name + certification badge with proper vertical alignment
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.X_AXIS));
            centerPanel.setOpaque(false);
            centerPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

            JLabel nameLabel = new JLabel(result.getFieldName());
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
            nameLabel.setForeground(new Color(245, 250, 255));
            nameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
            centerPanel.add(nameLabel);

            // Add certification badge if signature is certified
            if (result.isCertificationSignature()) {
                centerPanel.add(Box.createRigidArea(new Dimension(8, 0)));

                JLabel certBadge = new JLabel("CERTIFIED");
                certBadge.setFont(new Font("Segoe UI", Font.BOLD, 9));
                certBadge.setForeground(new Color(255, 255, 255));
                certBadge.setOpaque(true);
                certBadge.setBackground(new Color(66, 133, 244));
                certBadge.setBorder(new EmptyBorder(3, 7, 3, 7));
                certBadge.setAlignmentY(Component.CENTER_ALIGNMENT);
                certBadge.setToolTipText("This document is certified");
                centerPanel.add(certBadge);
            }

            centerPanel.add(Box.createHorizontalGlue());
            headerPanel.add(centerPanel, BorderLayout.CENTER);

            // Right: Modern "Details" button with proper alignment
            JButton propertiesBtn = new JButton("Details") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Button background
                    if (getModel().isRollover()) {
                        g2d.setColor(new Color(66, 133, 244, 40));
                    } else {
                        g2d.setColor(new Color(66, 133, 244, 20));
                    }
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);

                    g2d.dispose();
                    super.paintComponent(g);
                }
            };
            propertiesBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            propertiesBtn.setForeground(new Color(100, 160, 255));
            propertiesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            propertiesBtn.setFocusPainted(false);
            propertiesBtn.setBorderPainted(false);
            propertiesBtn.setContentAreaFilled(false);
            propertiesBtn.setOpaque(false);
            propertiesBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
            propertiesBtn.setAlignmentY(Component.CENTER_ALIGNMENT);

            propertiesBtn.addActionListener(e -> showDetailedProperties());

            JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttonWrapper.setOpaque(false);
            buttonWrapper.add(propertiesBtn);
            headerPanel.add(buttonWrapper, BorderLayout.EAST);



            panel.add(headerPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 10)));

            // === Summary Message and Verification Details Section ===
            JPanel summaryRow = getSummaryPanel();
            summaryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            panel.add(summaryRow);

            panel.add(Box.createRigidArea(new Dimension(0, 8)));

            // Verification details with proper alignment
            JPanel verificationDetailsPanel = createVerificationDetailsPanel(result);
            verificationDetailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            verificationDetailsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            panel.add(verificationDetailsPanel);

            return panel;
        }

        private JPanel getSummaryPanel() {
            JPanel summaryRow = new JPanel(new BorderLayout(10, 0));
            summaryRow.setOpaque(false);
            summaryRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Left: Section title
            JLabel detailsTitle = new JLabel("Verification Details");
            detailsTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
            detailsTitle.setForeground(new Color(180, 190, 200));

            // Right: Status badge
            JLabel summaryLabel = getSummaryLabel();

            summaryRow.add(detailsTitle, BorderLayout.WEST);
            summaryRow.add(summaryLabel, BorderLayout.EAST);

            return summaryRow;
        }

        private JLabel getSummaryLabel() {
            JLabel summaryLabel = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Draw subtle background
                    VerificationStatus status = result.getOverallStatus();
                    Color bgColor;
                    if (status == VerificationStatus.VALID) {
                        bgColor = new Color(76, 175, 80, 15);
                    } else if (status == VerificationStatus.INVALID) {
                        bgColor = new Color(244, 67, 54, 15);
                    } else {
                        bgColor = new Color(255, 193, 7, 15);
                    }
                    g2d.setColor(bgColor);
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);

                    g2d.dispose();
                    super.paintComponent(g);
                }
            };
            summaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            summaryLabel.setOpaque(false);
            summaryLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

            // Simple, clear messages for non-technical users
            if (result.getOverallStatus() == VerificationStatus.VALID) {
                summaryLabel.setText("Valid");
                summaryLabel.setForeground(new Color(129, 199, 132));
            } else if (result.getOverallStatus() == VerificationStatus.INVALID) {
                // Provide specific reason for invalidity in priority order
                if (!result.isDocumentIntact()) {
                    summaryLabel.setText("Document Modified");
                } else if (!result.isSignatureValid()) {
                    summaryLabel.setText("Invalid");
                } else if (result.isCertificateRevoked()) {
                    summaryLabel.setText("Revoked");
                } else if (!result.isCertificateValid()) {
                    summaryLabel.setText("Expired");
                } else {
                    summaryLabel.setText("Not Valid");
                }
                summaryLabel.setForeground(new Color(255, 138, 128));
            } else {
                // UNKNOWN status - usually means valid but not trusted
                if (result.isSignatureValid() && result.isDocumentIntact() && !result.isCertificateTrusted()) {
                    summaryLabel.setText("Identity Unverified");
                } else {
                    summaryLabel.setText("Cannot Verify");
                }
                summaryLabel.setForeground(new Color(255, 213, 79));
            }
            return summaryLabel;
        }

        private JPanel createVerificationDetailsPanel(SignatureVerificationResult result) {
            JPanel detailsPanel = new JPanel();
            detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
            detailsPanel.setOpaque(false);
            detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailsPanel.setBorder(new EmptyBorder(2, 0, 0, 0));

            // Core verification checks (PDF viewer order)
            addCompactStatusRow(detailsPanel, "Signature Verified", result.isSignatureValid(), false);
            addCompactStatusRow(detailsPanel, "Document Tampered", result.isDocumentIntact(), false);
            addCompactStatusRow(detailsPanel, "Certificate Valid", result.isCertificateValid(), false);
            addCompactStatusRow(detailsPanel, "Certificate Trusted", result.isCertificateTrusted(), false);

            // Spacer between core and optional checks
            detailsPanel.add(Box.createRigidArea(new Dimension(0, 4)));

            // Optional features (show as enabled/not enabled)
            addCompactStatusRow(detailsPanel, "Timestamp", result.isTimestampValid(), true);
            addCompactStatusRow(detailsPanel, "Long-Term Validation", result.hasLTV(), true);

            return detailsPanel;
        }

        private void addCompactStatusRow(JPanel panel, String label, boolean status, boolean isIncludedType) {
            JPanel row = new JPanel(new GridBagLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(0, 0, 0, 10);
            gbc.gridy = 0;

            // Icon: Modern status indicator
            JLabel iconLabel = new JLabel(status
                    ? IconLoader.loadIcon(ICON_VALID, 16, 16)
                    : IconLoader.loadIcon(ICON_INVALID, 16, 16));
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            gbc.gridx = 0;
            gbc.weightx = 0;
            row.add(iconLabel, gbc);

            // Label: Text with improved readability
            JLabel labelText = new JLabel(label);
            labelText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            labelText.setForeground(new Color(210, 215, 220));
            labelText.setVerticalAlignment(SwingConstants.CENTER);
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            row.add(labelText, gbc);

            // Value: Modern badge styling with proper colors
            String value;
            Color bgColor;
            Color fgColor;

            if (isIncludedType) {
                value = status ? "Enabled" : "Not Enabled";
                bgColor = status ? new Color(76, 175, 80, 30) : new Color(128, 128, 128, 20);
                fgColor = status ? new Color(129, 199, 132) : new Color(180, 180, 180);
            } else {
                value = status ? "Yes" : "No";
                bgColor = status ? new Color(76, 175, 80, 30) : new Color(244, 67, 54, 30);
                fgColor = status ? new Color(129, 199, 132) : new Color(255, 138, 128);
            }

            JLabel valueLabel = new JLabel(value) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(bgColor);
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                    g2d.dispose();
                    super.paintComponent(g);
                }
            };
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
            valueLabel.setForeground(fgColor);
            valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
            valueLabel.setVerticalAlignment(SwingConstants.CENTER);
            valueLabel.setOpaque(false);
            valueLabel.setBorder(new EmptyBorder(3, 8, 3, 8));
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(0, 10, 0, 0);
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
