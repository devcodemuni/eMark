package com.codemuni.utils;

import java.awt.*;

/**
 * Centralized color constants for signature-related UI components.
 * Provides consistent theming across signature panels, overlays, and dialogs.
 *
 * This class now delegates to UIConstants for consistency across the application.
 */
public final class SignatureColors {

    // Prevent instantiation
    private SignatureColors() {
    }

    // Signature status colors - delegate to UIConstants for consistency
    public static final Color VALID_COLOR = UIConstants.Colors.STATUS_VALID;
    public static final Color UNKNOWN_COLOR = UIConstants.Colors.STATUS_WARNING;
    public static final Color INVALID_COLOR = UIConstants.Colors.STATUS_ERROR;

    // Panel background colors - delegate to UIConstants for consistency
    public static final Color PANEL_BG = new Color(35, 35, 35, 245);     // Semi-transparent dark (unique)
    public static final Color HEADER_BG = UIConstants.Colors.BG_PRIMARY;
    public static final Color ITEM_BG = UIConstants.Colors.BG_SECTION;
    public static final Color ITEM_HOVER_BG = UIConstants.Colors.BUTTON_SECONDARY;
    public static final Color BORDER_COLOR = UIConstants.Colors.BORDER_PRIMARY;

    /**
     * Get color based on verification status string.
     *
     * @param status Status string (e.g., "VALID", "INVALID", "UNKNOWN")
     * @return Corresponding color
     */
    public static Color getColorForStatus(String status) {
        if (status == null) {
            return UNKNOWN_COLOR;
        }
        switch (status.toUpperCase()) {
            case "VALID":
                return VALID_COLOR;
            case "INVALID":
                return INVALID_COLOR;
            default:
                return UNKNOWN_COLOR;
        }
    }
}
