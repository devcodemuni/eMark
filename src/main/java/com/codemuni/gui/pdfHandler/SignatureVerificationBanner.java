package com.codemuni.gui.pdfHandler;

import com.codemuni.service.SignatureVerificationService.SignatureVerificationResult;
import com.codemuni.utils.IconLoader;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Top banner component that displays overall signature verification status.
 * Shows appropriate icon and message based on verification results.
 */
public class SignatureVerificationBanner extends JPanel {

    private static final int BANNER_HEIGHT = 40;
    private static final int ICON_SIZE = 24;

    // Status colors
    private static final Color VALID_BG = new Color(212, 237, 218);
    private static final Color VALID_FG = new Color(21, 87, 36);
    private static final Color INVALID_BG = new Color(248, 215, 218);
    private static final Color INVALID_FG = new Color(114, 28, 36);
    private static final Color WARNING_BG = new Color(255, 243, 205);
    private static final Color WARNING_FG = new Color(133, 100, 4);
    private static final Color INFO_BG = new Color(217, 237, 247);
    private static final Color INFO_FG = new Color(12, 84, 96);

    private final JLabel iconLabel;
    private final JLabel messageLabel;
    private final JToggleButton signatureButton;
    private VerificationStatus currentStatus;
    private boolean buttonHovered = false;
    private Color currentBgColor = INFO_BG;
    private Runnable toggleAction;
    private boolean isCertified = false; // Track if document is certified

    public enum VerificationStatus {
        ALL_VALID,
        SOME_INVALID,
        ALL_INVALID,
        UNKNOWN,
        NONE
    }

    public SignatureVerificationBanner() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(Integer.MAX_VALUE, BANNER_HEIGHT));
        setMinimumSize(new Dimension(0, BANNER_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, BANNER_HEIGHT));

        // Content panel with left and right padding
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 50, 0, 50));

        // Left side: Icon and message
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(false);
        
        // Icon on the left
        iconLabel = new JLabel();
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 10));

        // Message in the center
        messageLabel = new JLabel();
        messageLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        messageLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 15));

        leftPanel.add(iconLabel, BorderLayout.WEST);
        leftPanel.add(messageLabel, BorderLayout.CENTER);
        
        // Right side: Toggle button for signature panel (modern toggle style)
        signatureButton = new JToggleButton("Signature Panel") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                boolean isSelected = isSelected();
                int width = getWidth();
                int height = getHeight();
                int radius = 16;
                
                // Background with toggle effect
                if (isSelected) {
                    // Selected state: Solid darker background with subtle shadow
                    g2d.setColor(new Color(0, 0, 0, 15));
                    g2d.fillRoundRect(1, 2, width - 2, height - 2, radius, radius);
                    g2d.setColor(getBackground());
                    g2d.fillRoundRect(0, 0, width, height - 2, radius, radius);
                } else {
                    // Unselected state: Lighter background
                    g2d.setColor(getBackground());
                    g2d.fillRoundRect(0, 0, width, height, radius, radius);
                }
                
                // Border with toggle effect
                Color borderColor = getForeground();
                if (isSelected) {
                    // Thicker, more prominent border when selected
                    g2d.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 200));
                    g2d.setStroke(new BasicStroke(2.0f));
                    g2d.drawRoundRect(1, 1, width - 3, height - 3, radius, radius);
                } else {
                    // Subtle border when not selected
                    g2d.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 120));
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawRoundRect(1, 1, width - 2, height - 2, radius, radius);
                }
                
                // Toggle indicator (vertical bar on left side)
                if (isSelected) {
                    int barWidth = 3;
                    int barHeight = height - 12;
                    int barX = 8;
                    int barY = 6;
                    g2d.setColor(getForeground());
                    g2d.fillRoundRect(barX, barY, barWidth, barHeight, 2, 2);
                }
                
                // Draw text with appropriate offset
                g2d.setColor(getForeground());
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int textOffset = isSelected ? 6 : 0; // Shift text right when selected to make room for indicator
                int textX = ((width - fm.stringWidth(getText())) / 2) + textOffset;
                int textY = (height + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(getText(), textX, textY);
                
                g2d.dispose();
            }
            
            @Override
            protected void paintBorder(Graphics g) {
                // Border is painted in paintComponent for better control
            }
        };
        signatureButton.setFont(new Font("Segoe UI", Font.BOLD, 11));
        signatureButton.setFocusPainted(false);
        signatureButton.setBorderPainted(false);
        signatureButton.setContentAreaFilled(false); // We paint it ourselves
        signatureButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signatureButton.setOpaque(false);
        
        // Set smaller height - 28px for better toggle appearance
        signatureButton.setPreferredSize(new Dimension(140, 28));
        signatureButton.setMaximumSize(new Dimension(140, 28));
        signatureButton.setMinimumSize(new Dimension(140, 28));
        
        // Add hover effect
        signatureButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                buttonHovered = true;
                updateButtonStyle(currentBgColor);
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                buttonHovered = false;
                updateButtonStyle(currentBgColor);
            }
        });
        
        // Add action listener for toggle
        signatureButton.addActionListener(e -> {
            if (toggleAction != null) {
                toggleAction.run();
            }
        });
        
        contentPanel.add(leftPanel, BorderLayout.CENTER);
        contentPanel.add(signatureButton, BorderLayout.EAST);
        
        add(contentPanel, BorderLayout.CENTER);

        // Initially hidden
        setVisible(false);
        currentStatus = VerificationStatus.NONE;
    }

    /**
     * Updates the banner based on verification results.
     * Follows PDF viewer logic:
     * - ALL_VALID (green): All signatures valid AND trusted
     * - SOME_INVALID (yellow): Mixed results OR valid but not trusted
     * - ALL_INVALID (red): All signatures invalid (modified/revoked/expired)
     */
    public void updateStatus(List<SignatureVerificationResult> results) {
        if (results == null || results.isEmpty()) {
            setVisible(false);
            currentStatus = VerificationStatus.NONE;
            return;
        }

        int totalSignatures = results.size();
        int validAndTrusted = 0;  // Green: Valid + Trusted
        int validButUntrusted = 0; // Yellow: Valid but not trusted
        int invalid = 0;           // Red: Invalid (modified/revoked/expired)
        
        // Track specific failure reasons for better messaging
        int documentModified = 0;
        int signatureInvalid = 0;
        int certificateRevoked = 0;
        int certificateExpired = 0;

        // Check if document is certified (any certification signature present)
        isCertified = false;
        for (SignatureVerificationResult result : results) {
            if (result.isCertificationSignature()) {
                isCertified = true;
                break;
            }
        }

        // Categorize each signature
        for (SignatureVerificationResult result : results) {
            if (isSignatureValid(result)) {
                // Signature is cryptographically valid
                if (result.isCertificateTrusted()) {
                    validAndTrusted++;
                } else {
                    validButUntrusted++; // Valid but trust cannot be established
                }
            } else {
                invalid++; // Invalid (document modified, signature broken, cert revoked, etc.)

                // Track specific failure reason (priority order)
                if (!result.isDocumentIntact()) {
                    documentModified++;
                } else if (!result.isSignatureValid()) {
                    signatureInvalid++;
                } else if (result.isCertificateRevoked()) {
                    certificateRevoked++;
                } else if (!result.isCertificateValid()) {
                    certificateExpired++;
                }
            }
        }

        // Determine overall status (PDF viewer style)
        VerificationStatus newStatus;
        if (invalid > 0) {
            // If ANY signature is invalid, show red/invalid
            if (invalid == totalSignatures) {
                newStatus = VerificationStatus.ALL_INVALID;
            } else {
                newStatus = VerificationStatus.SOME_INVALID;
            }
        } else if (validButUntrusted > 0) {
            // All signatures valid but some/all not trusted - show yellow/warning
            newStatus = VerificationStatus.SOME_INVALID; // Using SOME_INVALID for yellow color
        } else {
            // All signatures valid AND trusted - show green
            newStatus = VerificationStatus.ALL_VALID;
        }

        currentStatus = newStatus;
        updateUI(newStatus, totalSignatures, validAndTrusted, validButUntrusted, invalid, 
                documentModified, signatureInvalid, certificateRevoked, certificateExpired);
        setVisible(true);
    }

    /**
     * Shows a loading/verification in progress message.
     */
    public void showVerifying() {
        currentStatus = VerificationStatus.UNKNOWN;
        ImageIcon icon = IconLoader.loadIcon("info.png", ICON_SIZE);
        iconLabel.setIcon(icon);
        messageLabel.setText("Checking document signatures. Please wait...");
        setBackground(INFO_BG);
        messageLabel.setForeground(INFO_FG);
        setVisible(true);
    }

    /**
     * Hides the banner.
     */
    public void hideBanner() {
        setVisible(false);
        currentStatus = VerificationStatus.NONE;
    }

    /**
     * Updates the UI based on verification status.
     * PDF viewer style messages with specific failure reasons:
     * - Green: "Signed and all signatures are valid"
     * - Yellow: "At least one signature has problems" or "Valid but identity of signer could not be verified"
     * - Red: "At least one signature is invalid" with specific reason
     */
    private void updateUI(VerificationStatus status, int total, int validTrusted, int validUntrusted, int invalid,
                         int documentModified, int signatureInvalid, int certificateRevoked, int certificateExpired) {
        String iconName;
        String message;
        Color bgColor;
        Color fgColor;

        switch (status) {
            case ALL_VALID:
                // Use certified icon if document is certified, otherwise use check_circle
                iconName = isCertifiedDocument() ? "certified.png" : "check_circle.png";
                if (total == 1) {
                    message = "Signed and all signatures are valid. Document has not been modified since signing.";
                } else {
                    message = "Signed and all " + total + " signatures are valid. Document has not been modified since signing.";
                }
                bgColor = VALID_BG;
                fgColor = VALID_FG;
                break;

            case ALL_INVALID:
                iconName = "cross_circle.png";
                // Provide specific reason based on failure type (priority order)
                if (documentModified > 0) {
                    if (total == 1) {
                        message = "Document has been modified after signing. Signature is invalid.";
                    } else {
                        message = "Document has been modified after signing. " + documentModified + " of " + total + " signature" + (documentModified > 1 ? "s are" : " is") + " invalid.";
                    }
                } else if (signatureInvalid > 0) {
                    if (total == 1) {
                        message = "Signature is invalid or corrupted.";
                    } else {
                        message = signatureInvalid + " of " + total + " signature" + (signatureInvalid > 1 ? "s are" : " is") + " invalid or corrupted.";
                    }
                } else if (certificateRevoked > 0) {
                    if (total == 1) {
                        message = "Certificate has been revoked. Signature is invalid.";
                    } else {
                        message = certificateRevoked + " certificate" + (certificateRevoked > 1 ? "s have" : " has") + " been revoked.";
                    }
                } else if (certificateExpired > 0) {
                    if (total == 1) {
                        message = "Certificate has expired. Signature is invalid.";
                    } else {
                        message = certificateExpired + " certificate" + (certificateExpired > 1 ? "s have" : " has") + " expired.";
                    }
                } else {
                    message = "At least one signature is invalid. See signature panel for details.";
                }
                bgColor = INVALID_BG;
                fgColor = INVALID_FG;
                break;

            case SOME_INVALID:
                iconName = "question_circle.png";
                // Determine message based on what failed
                if (invalid > 0) {
                    // Some signatures are actually invalid - provide specific reason
                    if (documentModified > 0) {
                        message = "Document has been modified. " + invalid + " of " + total + " signature" + (invalid > 1 ? "s are" : " is") + " invalid.";
                    } else if (signatureInvalid > 0) {
                        message = invalid + " of " + total + " signature" + (invalid > 1 ? "s are" : " is") + " invalid or corrupted.";
                    } else if (certificateRevoked > 0) {
                        message = invalid + " certificate" + (invalid > 1 ? "s have" : " has") + " been revoked.";
                    } else if (certificateExpired > 0) {
                        message = invalid + " certificate" + (invalid > 1 ? "s have" : " has") + " expired.";
                    } else {
                        message = "At least one signature has problems. " + invalid + " of " + total + " signature" + (invalid > 1 ? "s" : "") + " could not be verified.";
                    }
                } else if (validUntrusted > 0) {
                    // All valid but some/all not trusted
                    if (validUntrusted == total) {
                        message = "Signed and all signatures are valid, but the identity of one or more signers could not be verified.";
                    } else {
                        message = "Signed and all signatures are valid, but the identity of " + validUntrusted + 
                                 " signer" + (validUntrusted > 1 ? "s" : "") + " could not be verified.";
                    }
                } else {
                    message = "At least one signature has problems. See signature panel for details.";
                }
                bgColor = WARNING_BG;
                fgColor = WARNING_FG;
                break;

            case UNKNOWN:
            default:
                iconName = "question_circle.png";
                message = "Unable to verify signature status. Additional information may be needed.";
                bgColor = INFO_BG;
                fgColor = INFO_FG;
                break;
        }

        // Load and set icon
        ImageIcon icon = IconLoader.loadIcon(iconName, ICON_SIZE);
        iconLabel.setIcon(icon);

        // Set message and colors
        messageLabel.setText(message);
        setBackground(bgColor);
        messageLabel.setForeground(fgColor);
        currentBgColor = bgColor;
        updateButtonStyle(bgColor);
    }

    /**
     * Determines if a signature is valid based on verification result.
     * PDF viewer considers a signature valid if:
     * 1. Document integrity is intact (not modified after signing)
     * 2. Signature cryptographically valid
     * 3. Certificate is valid (not expired)
     * 4. Certificate is not revoked
     *
     * Note: Certificate trust is checked separately and shown as warning, not error
     */
    private boolean isSignatureValid(SignatureVerificationResult result) {
        // Critical checks - if any fail, signature is INVALID
        if (!result.isDocumentIntact()) return false;
        if (!result.isSignatureValid()) return false;
        if (!result.isCertificateValid()) return false;
        if (result.isCertificateRevoked()) return false;

        // If certificate is not trusted, it's still technically "valid" but with warning
        // PDF viewer shows this as yellow/unknown, not red/invalid
        return true;
    }

    /**
     * Returns whether the document is certified.
     */
    private boolean isCertifiedDocument() {
        return isCertified;
    }

    /**
     * Gets the current verification status.
     */
    public VerificationStatus getCurrentStatus() {
        return currentStatus;
    }

    /**
     * Sets the toggle action to perform when the signature button is clicked.
     */
    public void setSignatureButtonAction(Runnable action) {
        this.toggleAction = action;
    }
    
    /**
     * Updates the toggle button state based on panel visibility.
     */
    public void setButtonSelected(boolean selected) {
        signatureButton.setSelected(selected);
        updateButtonStyle(currentBgColor);
    }

    /**
     * Updates button styling based on banner background color and toggle state.
     */
    private void updateButtonStyle(Color bgColor) {
        boolean isSelected = signatureButton.isSelected();
        
        // Calculate button background color based on banner color and state
        int darkenAmount;
        if (isSelected) {
            darkenAmount = buttonHovered ? 60 : 50; // Darker when selected
        } else {
            darkenAmount = buttonHovered ? 35 : 20; // Lighter when not selected
        }
        
        Color buttonBg = new Color(
            Math.max(0, bgColor.getRed() - darkenAmount),
            Math.max(0, bgColor.getGreen() - darkenAmount),
            Math.max(0, bgColor.getBlue() - darkenAmount)
        );
        signatureButton.setBackground(buttonBg);
        
        // Set text/border color based on banner foreground
        Color fgColor = messageLabel.getForeground();
        signatureButton.setForeground(fgColor);
        
        // Repaint to apply new colors
        signatureButton.repaint();
    }
}
