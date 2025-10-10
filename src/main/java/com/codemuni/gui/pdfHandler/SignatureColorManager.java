package com.codemuni.gui.pdfHandler;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages unique colors for each signature to visually link
 * signature rectangles on PDF pages with signature cards in the panel.
 */
public class SignatureColorManager {

    // Distinct colors for signature identification (vibrant but readable)
    private static final Color[] SIGNATURE_COLORS = {
            new Color(255, 107, 107),  // Soft Red
            new Color(78, 205, 196),   // Turquoise
            new Color(255, 195, 18),   // Yellow/Gold
            new Color(138, 43, 226),   // Purple
            new Color(46, 213, 115),   // Green
            new Color(255, 159, 64),   // Orange
            new Color(75, 123, 236),   // Blue
            new Color(234, 84, 85),    // Pink Red
            new Color(72, 219, 251),   // Cyan
            new Color(253, 203, 110)   // Light Orange
    };

    private final Map<String, Color> signatureColorMap = new HashMap<>();
    private int colorIndex = 0;

    /**
     * Gets or assigns a color for the given signature field name.
     *
     * @param fieldName The signature field name
     * @return The assigned color
     */
    public Color getColorForSignature(String fieldName) {
        if (fieldName == null) {
            fieldName = "unknown";
        }

        // Return existing color if already assigned
        if (signatureColorMap.containsKey(fieldName)) {
            return signatureColorMap.get(fieldName);
        }

        // Assign new color
        Color color = SIGNATURE_COLORS[colorIndex % SIGNATURE_COLORS.length];
        signatureColorMap.put(fieldName, color);
        colorIndex++;

        return color;
    }

    /**
     * Gets a semi-transparent version of the signature color for overlay rectangles.
     *
     * @param fieldName The signature field name
     * @return Semi-transparent color
     */
    public Color getTransparentColorForSignature(String fieldName) {
        Color baseColor = getColorForSignature(fieldName);
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 80);
    }

    /**
     * Resets all color assignments.
     */
    public void reset() {
        signatureColorMap.clear();
        colorIndex = 0;
    }

    /**
     * Returns the number of signatures that have been assigned colors.
     */
    public int getSignatureCount() {
        return signatureColorMap.size();
    }
}
