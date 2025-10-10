package com.codemuni.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Utility class to generate icons programmatically for UI components.
 */
public class IconGenerator {

    /**
     * Creates a signature panel toggle icon (signatures list icon).
     */
    public static ImageIcon createSignaturePanelIcon(int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2f));

        // Draw signature lines (list icon)
        int lineY1 = size / 4;
        int lineY2 = size / 2;
        int lineY3 = (size * 3) / 4;
        int padding = size / 6;

        g2d.drawLine(padding, lineY1, size - padding, lineY1);
        g2d.drawLine(padding, lineY2, size - padding, lineY2);
        g2d.drawLine(padding, lineY3, size - padding, lineY3);

        g2d.dispose();
        return new ImageIcon(img);
    }

    /**
     * Creates a verify all signatures icon (shield with checkmark).
     */
    public static ImageIcon createVerifyAllIcon(int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Draw shield shape
        int shieldWidth = (int) (size * 0.7);
        int shieldHeight = (int) (size * 0.8);
        int x = (size - shieldWidth) / 2;
        int y = (size - shieldHeight) / 2;

        int[] xPoints = {
            x + shieldWidth / 2,           // Top center
            x + shieldWidth,               // Right
            x + shieldWidth,               // Bottom right
            x + shieldWidth / 2,           // Bottom center (point)
            x,                             // Bottom left
            x                              // Left
        };
        int[] yPoints = {
            y,                             // Top center
            y + shieldHeight / 4,          // Right
            y + shieldHeight * 2 / 3,      // Bottom right
            y + shieldHeight,              // Bottom center (point)
            y + shieldHeight * 2 / 3,      // Bottom left
            y + shieldHeight / 4           // Left
        };

        g2d.drawPolygon(xPoints, yPoints, 6);

        // Draw checkmark inside
        g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int checkX1 = x + shieldWidth / 4;
        int checkY1 = y + shieldHeight / 2;
        int checkX2 = x + shieldWidth / 2 - 1;
        int checkY2 = y + shieldHeight * 2 / 3;
        int checkX3 = x + shieldWidth * 3 / 4;
        int checkY3 = y + shieldHeight / 3;

        g2d.drawLine(checkX1, checkY1, checkX2, checkY2);
        g2d.drawLine(checkX2, checkY2, checkX3, checkY3);

        g2d.dispose();
        return new ImageIcon(img);
    }

    /**
     * Creates a properties/details icon (info icon with 'i').
     */
    public static ImageIcon createPropertiesIcon(int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);

        // Draw circle
        g2d.setStroke(new BasicStroke(2f));
        int padding = 2;
        g2d.drawOval(padding, padding, size - padding * 2 - 1, size - padding * 2 - 1);

        // Draw 'i' letter
        g2d.setFont(new Font("Segoe UI", Font.BOLD, size - 6));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "i";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(text, x, y);

        g2d.dispose();
        return new ImageIcon(img);
    }

    /**
     * Creates a certificate icon (document with seal).
     */
    public static ImageIcon createCertificateSmallIcon(int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(1.5f));

        // Draw document
        int padding = 2;
        int docWidth = (int) (size * 0.6);
        int docHeight = (int) (size * 0.8);
        int docX = padding;
        int docY = padding;
        g2d.drawRect(docX, docY, docWidth, docHeight);

        // Draw lines on document
        g2d.setStroke(new BasicStroke(1f));
        g2d.drawLine(docX + 2, docY + 4, docX + docWidth - 2, docY + 4);
        g2d.drawLine(docX + 2, docY + 7, docX + docWidth - 4, docY + 7);

        // Draw seal (circle)
        int sealSize = (int) (size * 0.5);
        int sealX = docX + docWidth - sealSize / 2;
        int sealY = docY + docHeight - sealSize / 2;
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawOval(sealX, sealY, sealSize, sealSize);

        g2d.dispose();
        return new ImageIcon(img);
    }
}
