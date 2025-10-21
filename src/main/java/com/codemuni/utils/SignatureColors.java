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

    /**
     * Creates a color with specified alpha (transparency) value.
     *
     * @param color Base color
     * @param alpha Alpha value (0-255, where 0 is fully transparent and 255 is fully opaque)
     * @return Color with specified alpha
     */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /**
     * Creates a lighter version of the given color.
     *
     * @param color Base color
     * @param factor Lightness factor (0.0 to 1.0, where higher values create lighter colors)
     * @return Lighter version of the color
     */
    public static Color lighter(Color color, float factor) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        // Blend with white based on factor
        int white = 255;
        r = (int) (r + (white - r) * factor);
        g = (int) (g + (white - g) * factor);
        b = (int) (b + (white - b) * factor);

        return new Color(r, g, b);
    }
}
