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
    private final JLabel progressLabel; // New: Shows real-time progress
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
        NONE,
        PASSWORD_PROTECTED_SIGNED // For password-protected signed PDFs
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

        // Center panel: Message and progress stacked
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 15));

        // Message label
        messageLabel = new JLabel();
        messageLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Progress label (initially hidden)
        progressLabel = new JLabel();
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressLabel.setVisible(false);

        // Add vertical glue to center content vertically
        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(messageLabel);
        centerPanel.add(progressLabel);
        centerPanel.add(Box.createVerticalGlue());

        leftPanel.add(iconLabel, BorderLayout.WEST);
        leftPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Right side: Toggle button for signature panel (modern toggle style)
        signatureButton = new JToggleButton("Show Panel") {
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
     * Enhanced for India CCA compliance with detailed status tracking.
     *
     * Color coding:
     * - ALL_VALID (green): All signatures valid, trusted, CCA-compliant
     * - SOME_INVALID (yellow): Warnings, mixed results, or trust issues
     * - ALL_INVALID (red): Critical failures (modified/revoked/invalid)
     */
    public void updateStatus(List<SignatureVerificationResult> results) {
        if (results == null || results.isEmpty()) {
            setVisible(false);
            currentStatus = VerificationStatus.NONE;
            return;
        }

        int totalSignatures = results.size();
        int validAndTrusted = 0;      // Green: Valid + Trusted
        int validButUntrusted = 0;     // Yellow: Valid but not trusted
        int validWithWarnings = 0;     // Yellow: Valid but has warnings (weak algo, no timestamp, etc.)
        int invalid = 0;               // Red: Invalid (modified/revoked/expired)

        // Track specific failure reasons for better messaging (CRITICAL errors)
        int documentModified = 0;
        int signatureInvalid = 0;
        int certificateRevoked = 0;
        int certificateExpired = 0;
        int weakAlgorithm = 0;

        // Track warning conditions (non-critical issues)
        int deprecatedAlgorithm = 0;
        int invalidTimestamp = 0;
        int revocationNotChecked = 0;

        // Check if document is certified (any certification signature present)
        isCertified = false;
        for (SignatureVerificationResult result : results) {
            if (result.isCertificationSignature()) {
                isCertified = true;
                break;
            }
        }

        // Categorize each signature using service's getOverallStatus() for consistency
        for (SignatureVerificationResult result : results) {
            com.codemuni.service.SignatureVerificationService.VerificationStatus status = result.getOverallStatus();
            boolean hasWarnings = hasWarnings(result);

            if (status == com.codemuni.service.SignatureVerificationService.VerificationStatus.INVALID) {
                // CRITICAL: Signature is invalid
                invalid++;

                // Track specific failure reason (priority order for messaging)
                if (!result.isDocumentIntact()) {
                    documentModified++;
                } else if (!result.isSignatureValid()) {
                    // Check if it's due to weak algorithm (MD5, etc.)
                    if (hasWeakAlgorithm(result)) {
                        weakAlgorithm++;
                    } else {
                        signatureInvalid++;
                    }
                } else if (result.isCertificateRevoked()) {
                    certificateRevoked++;
                } else if (!result.isCertificateValid()) {
                    certificateExpired++;
                }
            } else if (status == com.codemuni.service.SignatureVerificationService.VerificationStatus.UNKNOWN) {
                // UNKNOWN: Valid cryptographically but cannot verify identity
                // Check if it has warnings (deprecated algo, invalid timestamp, etc.)
                if (hasWarnings) {
                    validWithWarnings++;

                    // Track warning types
                    if (hasDeprecatedAlgorithm(result)) {
                        deprecatedAlgorithm++;
                    }
                    if (hasInvalidTimestamp(result)) {
                        invalidTimestamp++;
                    }
                    if (hasRevocationCheckIssue(result)) {
                        revocationNotChecked++;
                    }
                } else {
                    // No warnings, just trust/validity issues
                    validButUntrusted++;
                }
            } else {
                // VALID: All checks passed including trust
                validAndTrusted++;
            }
        }

        // Determine overall status with enhanced CCA logic
        VerificationStatus newStatus;
        if (invalid > 0) {
            // CRITICAL ERRORS: At least one signature is invalid
            if (invalid == totalSignatures) {
                newStatus = VerificationStatus.ALL_INVALID; // RED: All invalid
            } else {
                newStatus = VerificationStatus.SOME_INVALID; // YELLOW: Mixed (some invalid)
            }
        } else if (validWithWarnings > 0 || validButUntrusted > 0) {
            // WARNINGS: All signatures cryptographically valid but have issues
            // - Weak/deprecated algorithms
            // - No timestamp
            // - Revocation not checked
            // - Trust issues
            newStatus = VerificationStatus.SOME_INVALID; // YELLOW: Valid with warnings
        } else {
            // ALL VALID: No errors, no warnings, all trusted
            newStatus = VerificationStatus.ALL_VALID; // GREEN: Perfect
        }

        currentStatus = newStatus;
        // Clear progress message when showing results
        clearProgress();
        updateUI(newStatus, totalSignatures, validAndTrusted, validButUntrusted, invalid,
                documentModified, signatureInvalid, certificateRevoked, certificateExpired,
                weakAlgorithm, deprecatedAlgorithm, invalidTimestamp, revocationNotChecked,
                validWithWarnings);
        setVisible(true);
    }

    /**
     * Shows a loading/verification in progress message.
     */
    public void showVerifying() {
        currentStatus = VerificationStatus.UNKNOWN;
        ImageIcon icon = IconLoader.loadIcon("info.png", ICON_SIZE);
        iconLabel.setIcon(icon);
        messageLabel.setText("Verifying document signatures...");
        setBackground(INFO_BG);
        messageLabel.setForeground(INFO_FG);
        progressLabel.setForeground(INFO_FG);
        progressLabel.setText("Starting verification...");
        progressLabel.setVisible(true);
        currentBgColor = INFO_BG;
        updateButtonStyle(INFO_BG);
        setVisible(true);
    }

    /**
     * Shows banner for password-protected signed PDFs.
     * This indicates that signature verification is not supported for encrypted PDFs.
     */
    public void showPasswordProtectedSigned() {
        currentStatus = VerificationStatus.PASSWORD_PROTECTED_SIGNED;
        ImageIcon icon = IconLoader.loadIcon("info.png", ICON_SIZE);
        iconLabel.setIcon(icon);
        messageLabel.setText("Signature verification isn’t available for password-protected PDFs in this version.");
        setBackground(WARNING_BG);
        messageLabel.setForeground(WARNING_FG);
        progressLabel.setVisible(false);
        currentBgColor = WARNING_BG;

        // Hide toggle button for this case since there's no verification
        signatureButton.setVisible(false);

        setVisible(true);
    }

    /**
     * Updates the progress message during verification.
     */
    public void updateProgress(String message) {
        if (currentStatus == VerificationStatus.UNKNOWN && progressLabel != null) {
            progressLabel.setText(message);
            progressLabel.setVisible(true);
        }
    }

    /**
     * Clears the progress message.
     */
    public void clearProgress() {
        if (progressLabel != null) {
            progressLabel.setText("");
            progressLabel.setVisible(false);
        }
    }

    /**
     * Hides the banner.
     */
    public void hideBanner() {
        setVisible(false);
        currentStatus = VerificationStatus.NONE;
    }

    /**
     * Resets the banner to initial state.
     * Should be called when loading a new PDF.
     */
    public void reset() {
        // Hide banner
        setVisible(false);
        currentStatus = VerificationStatus.NONE;
        isCertified = false;

        // Clear messages
        messageLabel.setText("");
        progressLabel.setText("");
        progressLabel.setVisible(false);

        // Reset button state and visibility
        signatureButton.setSelected(false);
        signatureButton.setText("Show Panel");
        signatureButton.setVisible(true); // Restore button visibility

        // Clear icon
        iconLabel.setIcon(null);
    }

    /**
     * Updates the UI based on verification status.
     * Enhanced messages with specific failure/warning reasons:
     * - GREEN: All signatures valid and trusted
     * - YELLOW: Valid but with warnings (deprecated algorithms, invalid timestamps, trust issues)
     * - RED: Critical failures (document modified, signature invalid, cert revoked)
     *
     * Note: Missing optional features (timestamp/LTV) are NOT shown as warnings.
     */
    private void updateUI(VerificationStatus status, int total, int validTrusted, int validUntrusted, int invalid,
                         int documentModified, int signatureInvalid, int certificateRevoked, int certificateExpired,
                         int weakAlgorithm, int deprecatedAlgorithm, int invalidTimestamp, int revocationNotChecked,
                         int validWithWarnings) {
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
                // Determine message based on what failed/warned
                if (invalid > 0) {
                    // CRITICAL: Some signatures are actually invalid
                    if (documentModified > 0) {
                        message = "Document has been modified. " + invalid + " of " + total + " signature" + (invalid > 1 ? "s are" : " is") + " invalid.";
                    } else if (weakAlgorithm > 0) {
                        message = "Weak or forbidden algorithm detected. " + weakAlgorithm + " signature" + (weakAlgorithm > 1 ? "s are" : " is") + " insecure (CCA non-compliant).";
                    } else if (signatureInvalid > 0) {
                        message = invalid + " of " + total + " signature" + (invalid > 1 ? "s are" : " is") + " invalid or corrupted.";
                    } else if (certificateRevoked > 0) {
                        message = invalid + " certificate" + (invalid > 1 ? "s have" : " has") + " been revoked.";
                    } else if (certificateExpired > 0) {
                        message = invalid + " certificate" + (invalid > 1 ? "s have" : " has") + " expired.";
                    } else {
                        message = "At least one signature has problems. " + invalid + " of " + total + " signature" + (invalid > 1 ? "s" : "") + " could not be verified.";
                    }
                } else if (validWithWarnings > 0) {
                    // WARNING: Valid but has non-critical issues
                    message = buildWarningMessage(deprecatedAlgorithm, invalidTimestamp, revocationNotChecked);
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

            case PASSWORD_PROTECTED_SIGNED:
                iconName = "info.png";
                message = "Signature verification isn’t available for password-protected PDFs in this version.";
                bgColor = WARNING_BG;
                fgColor = WARNING_BG;
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
     * Builds a warning message for signatures that are valid but have non-critical issues.
     * @param deprecatedAlgo Number of signatures using deprecated algorithms
     * @param invalidTs Number of signatures with invalid timestamps
     * @param revocationIssues Number of signatures with revocation check failures
     * @return User-friendly warning message
     */
    private String buildWarningMessage(int deprecatedAlgo, int invalidTs, int revocationIssues) {
        StringBuilder msg = new StringBuilder("Signed and all signatures are valid, but ");

        // Multiple issues
        if (deprecatedAlgo > 0 && invalidTs > 0) {
            msg.append(deprecatedAlgo).append(" signature")
                    .append(deprecatedAlgo > 1 ? "s use" : " uses")
                    .append(" deprecated algorithm (SHA-1) and ")
                    .append(invalidTs).append(" ")
                    .append(invalidTs > 1 ? "have" : "has")
                    .append(" invalid timestamp. CCA recommends SHA-256+ with valid timestamps.");
        }
        // Deprecated algorithm only
        else if (deprecatedAlgo > 0) {
            msg.append(deprecatedAlgo).append(" signature")
                    .append(deprecatedAlgo > 1 ? "s use" : " uses")
                    .append(" deprecated algorithm (SHA-1). CCA recommends SHA-256 or stronger.");
        }
        // Invalid timestamp only
        else if (invalidTs > 0) {
            msg.append(invalidTs).append(" signature")
                    .append(invalidTs > 1 ? "s have" : " has")
                    .append(" invalid or untrusted timestamp")
                    .append(invalidTs > 1 ? "s" : "")
                    .append(".");
        }
        // Revocation check issues only
        else if (revocationIssues > 0) {
            msg.append("revocation status could not be verified for ")
                    .append(revocationIssues).append(" signature")
                    .append(revocationIssues > 1 ? "s" : "")
                    .append(".");
        }
        // Fallback for other warnings
        else {
            msg.append("some signatures have warnings. See signature panel for details.");
        }

        return msg.toString();
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
        // Update button text based on panel state
        signatureButton.setText(selected ? "Hide Panel" : "Show Panel");
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

    /**
     * Checks if signature has non-critical warnings (CCA recommendations).
     * Warnings include:
     * - Deprecated hash algorithms (SHA-1)
     * - Invalid timestamp (if present)
     * - Revocation check failures
     *
     * Note: Missing timestamp/LTV is NOT considered a warning since they are optional features.
     */
    private boolean hasWarnings(SignatureVerificationResult result) {
        // Check for deprecated algorithm warnings
        if (hasDeprecatedAlgorithm(result)) return true;

        // Check for invalid timestamp (only if timestamp is present but invalid)
        if (hasInvalidTimestamp(result)) return true;

        // Check for revocation check issues
        if (hasRevocationCheckIssue(result)) return true;

        return false;
    }

    /**
     * Checks if signature uses weak/forbidden algorithm (MD5, etc.).
     * This is a CRITICAL error, not just a warning.
     */
    private boolean hasWeakAlgorithm(SignatureVerificationResult result) {
        if (result.getVerificationErrors() == null) return false;

        for (String error : result.getVerificationErrors()) {
            // Check for forbidden algorithm errors
            if (error.contains("FORBIDDEN") && error.contains("algorithm")) {
                return true;
            }
            if (error.contains("MD5") || error.contains("MD2") || error.contains("MD4")) {
                return true;
            }
            if (error.contains("too weak") && error.contains("bits")) {
                return true; // RSA < 2048 bits
            }
        }
        return false;
    }

    /**
     * Checks if signature uses deprecated algorithm (SHA-1).
     * This is a WARNING (CCA recommendation), not an error.
     */
    private boolean hasDeprecatedAlgorithm(SignatureVerificationResult result) {
        if (result.getVerificationWarnings() == null) return false;

        for (String warning : result.getVerificationWarnings()) {
            if (warning.contains("DEPRECATED") && warning.contains("algorithm")) {
                return true;
            }
            if (warning.contains("SHA-1")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if signature has a timestamp (valid or invalid).
     */
    private boolean hasTimestamp(SignatureVerificationResult result) {
        return result.getTimestampDate() != null;
    }

    /**
     * Checks if signature has a timestamp that is present but invalid.
     * This is a warning condition - timestamp exists but failed validation.
     * Note: Missing timestamp is NOT a warning (it's optional).
     */
    private boolean hasInvalidTimestamp(SignatureVerificationResult result) {
        // Only warn if timestamp is present but invalid
        return hasTimestamp(result) && !result.isTimestampValid();
    }

    /**
     * Checks if signature has revocation check issues.
     * Returns true when revocation status could not be verified (OCSP unavailable, network errors, etc.).
     * This is more secure than Adobe Reader's approach - we treat unverified status as a warning.
     */
    private boolean hasRevocationCheckIssue(SignatureVerificationResult result) {
        if (result.getRevocationStatus() == null) return false;

        String status = result.getRevocationStatus();
        // Treat all unverified revocation statuses as warnings for enhanced security
        return status.contains("Validity Unknown") ||
               status.contains("Not Checked") ||
               status.contains("Network Error") ||
               status.contains("Check Failed") ||
               status.contains("Unreachable");
    }
}
