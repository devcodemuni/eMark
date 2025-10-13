package com.codemuni.gui.pdfHandler;

import com.codemuni.utils.UIConstants;

import javax.swing.*;
import java.awt.*;

/**
 * UI component factory helpers.
 */
public final class UiFactory {
    private UiFactory() {
    }

    public static JButton createButton(String text, Color bg) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(5, 10, 5, 10));
        if (bg != null) button.setBackground(bg);
        button.setForeground(UIConstants.Colors.TEXT_PRIMARY);
        return button;
    }

    public static JPanel wrapLeft(JComponent comp) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setOpaque(false);
        panel.add(comp);
        return panel;
    }

    public static JPanel wrapRight(JComponent comp) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setOpaque(false);
        panel.add(comp);
        return panel;
    }
}
