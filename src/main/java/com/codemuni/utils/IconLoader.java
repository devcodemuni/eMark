package com.codemuni.utils;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// Load icons from resources with automatic resizing and error handling
public class IconLoader {

    private static final Log log = LogFactory.getLog(IconLoader.class);
    private static final String ICONS_PATH = "/icons/";

    // Load an icon by filename (e.g., "green_tick.png")
    public static ImageIcon loadIcon(String iconName) {
        try {
            URL iconUrl = IconLoader.class.getResource(ICONS_PATH + iconName);
            if (iconUrl != null) {
                return new ImageIcon(iconUrl);
            } else {
                log.warn("Icon not found: " + iconName);
            }
        } catch (Exception e) {
            log.error("Failed to load icon: " + iconName, e);
        }
        return null;
    }

    // Load an icon and resize it to specific dimensions
    public static ImageIcon loadIcon(String iconName, int width, int height) {
        ImageIcon icon = loadIcon(iconName);
        if (icon != null) {
            return scaleIcon(icon, width, height);
        }
        return null;
    }

    // Load an icon as a square (width = height)
    public static ImageIcon loadIcon(String iconName, int size) {
        return loadIcon(iconName, size, size);
    }

    // Resize an existing icon to new dimensions
    public static ImageIcon scaleIcon(ImageIcon icon, int width, int height) {
        if (icon == null) return null;
        Image img = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    // Create a fallback icon using text/emoji when image files aren't available
    public static JLabel createTextIcon(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    // Create a shield emoji as fallback
    public static JLabel createShieldFallback(int size) {
        return createTextIcon("🛡️", size, UIConstants.Colors.TYPE_EMBEDDED);
    }
}
