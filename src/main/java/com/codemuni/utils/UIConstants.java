package com.codemuni.utils;

import java.awt.*;

/**
 * Central constants for UI theming - colors, fonts, dimensions, padding.
 */
public final class UIConstants {

    private UIConstants() {
        throw new AssertionError("Cannot instantiate UIConstants");
    }

    // ==================== COLORS ====================

    public static final class Colors {
        // Backgrounds
        public static final Color BG_PRIMARY = new Color(45, 45, 45);      // Main background
        public static final Color BG_SECONDARY = new Color(40, 40, 40);    // Secondary background
        public static final Color BG_TERTIARY = new Color(35, 35, 35);     // Panel background
        public static final Color BG_SECTION = new Color(50, 50, 50);      // Section background

        // Status colors
        public static final Color STATUS_VALID = new Color(40, 167, 69);   // Green - valid/success
        public static final Color STATUS_WARNING = new Color(255, 193, 7);  // Yellow - warning
        public static final Color STATUS_ERROR = new Color(220, 53, 69);    // Red - error/invalid
        public static final Color STATUS_INFO = new Color(100, 150, 255);   // Blue - info

        // Text colors
        public static final Color TEXT_PRIMARY = new Color(220, 220, 220);  // Main text
        public static final Color TEXT_SECONDARY = new Color(200, 200, 200); // Secondary text
        public static final Color TEXT_TERTIARY = new Color(180, 180, 180); // Tertiary text
        public static final Color TEXT_MUTED = new Color(150, 150, 150);    // Muted text
        public static final Color TEXT_DISABLED = Color.GRAY;                // Disabled text

        // Border colors
        public static final Color BORDER_PRIMARY = new Color(70, 70, 70);
        public static final Color BORDER_SECONDARY = new Color(80, 80, 80);
        public static final Color BORDER_LIGHT = new Color(90, 90, 90);

        // Table colors
        public static final Color TABLE_GRID = new Color(60, 60, 60);
        public static final Color TABLE_ROW_EVEN = new Color(40, 40, 40);
        public static final Color TABLE_ROW_ODD = new Color(45, 45, 45);
        public static final Color TABLE_SELECTION = new Color(60, 120, 180);
        public static final Color TABLE_HEADER = new Color(50, 50, 50);

        // Button colors
        public static final Color BUTTON_PRIMARY = new Color(40, 167, 69);     // Green
        public static final Color BUTTON_PRIMARY_HOVER = new Color(50, 187, 79);
        public static final Color BUTTON_DANGER = new Color(180, 60, 60);      // Red
        public static final Color BUTTON_DANGER_HOVER = new Color(200, 70, 70);
        public static final Color BUTTON_SECONDARY = new Color(60, 60, 60);    // Gray
        public static final Color BUTTON_SECONDARY_HOVER = new Color(70, 70, 70);

        // Certificate type colors
        public static final Color TYPE_EMBEDDED = new Color(110, 179, 255);    // Blue
        public static final Color TYPE_MANUAL = new Color(100, 221, 100);      // Green

        private Colors() {}
    }

    // ==================== FONTS ====================

    public static final class Fonts {
        public static final String FAMILY = "Segoe UI";

        // Font sizes
        public static final int SIZE_TITLE = 16;
        public static final int SIZE_LARGE = 14;
        public static final int SIZE_NORMAL = 12;
        public static final int SIZE_SMALL = 11;
        public static final int SIZE_TINY = 10;

        // Common fonts
        public static final Font TITLE_BOLD = new Font(FAMILY, Font.BOLD, SIZE_TITLE);
        public static final Font LARGE_BOLD = new Font(FAMILY, Font.BOLD, SIZE_LARGE);
        public static final Font LARGE_PLAIN = new Font(FAMILY, Font.PLAIN, SIZE_LARGE);
        public static final Font NORMAL_BOLD = new Font(FAMILY, Font.BOLD, SIZE_NORMAL);
        public static final Font NORMAL_PLAIN = new Font(FAMILY, Font.PLAIN, SIZE_NORMAL);
        public static final Font SMALL_PLAIN = new Font(FAMILY, Font.PLAIN, SIZE_SMALL);
        public static final Font TINY_PLAIN = new Font(FAMILY, Font.PLAIN, SIZE_TINY);

        // Use monospace for displaying code or certificate data
        public static final Font MONOSPACE = new Font("Consolas", Font.PLAIN, SIZE_NORMAL);

        private Fonts() {}
    }

    // ==================== DIMENSIONS ====================

    public static final class Dimensions {
        // Spacing
        public static final int SPACING_TINY = 4;
        public static final int SPACING_SMALL = 8;
        public static final int SPACING_NORMAL = 12;
        public static final int SPACING_LARGE = 16;
        public static final int SPACING_XLARGE = 20;

        // Button dimensions
        public static final int BUTTON_HEIGHT = 34;
        public static final int BUTTON_WIDTH_SMALL = 100;
        public static final int BUTTON_WIDTH_MEDIUM = 140;
        public static final int BUTTON_WIDTH_LARGE = 180;

        // Icon sizes
        public static final int ICON_TINY = 16;
        public static final int ICON_SMALL = 24;
        public static final int ICON_MEDIUM = 32;
        public static final int ICON_LARGE = 48;
        public static final int ICON_XLARGE = 64;

        // Table row height
        public static final int TABLE_ROW_HEIGHT = 28;
        public static final int TABLE_HEADER_HEIGHT = 32;

        // Border widths
        public static final int BORDER_WIDTH_THIN = 1;
        public static final int BORDER_WIDTH_NORMAL = 2;
        public static final int BORDER_WIDTH_THICK = 3;

        private Dimensions() {}
    }

    // ==================== PADDING ====================

    public static final class Padding {
        public static final javax.swing.border.EmptyBorder TINY =
            new javax.swing.border.EmptyBorder(4, 4, 4, 4);
        public static final javax.swing.border.EmptyBorder SMALL =
            new javax.swing.border.EmptyBorder(8, 8, 8, 8);
        public static final javax.swing.border.EmptyBorder NORMAL =
            new javax.swing.border.EmptyBorder(12, 12, 12, 12);
        public static final javax.swing.border.EmptyBorder LARGE =
            new javax.swing.border.EmptyBorder(16, 16, 16, 16);

        private Padding() {}
    }

    // ==================== HELPER METHODS ====================

    public static Dimension buttonSize(int width) {
        return new Dimension(width, Dimensions.BUTTON_HEIGHT);
    }
}
