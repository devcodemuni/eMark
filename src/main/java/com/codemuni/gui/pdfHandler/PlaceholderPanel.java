package com.codemuni.gui.pdfHandler;

import javax.swing.*;
import java.awt.*;


/**
 * Professional centered placeholder shown before a PDF is loaded.
 * Supports opening PDF via button or drag-and-drop.
 */
public class PlaceholderPanel extends JPanel {
    public PlaceholderPanel(Runnable onOpen) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true); // respects FlatLaf theme
        setBackground(null); // use default background from FlatLaf

        // Icon label - professional visual indicator
        JLabel iconLabel = new JLabel("\ud83d\udcc4");
        iconLabel.setFont(iconLabel.getFont().deriveFont(Font.PLAIN, 72f));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        iconLabel.setForeground(new Color(180, 180, 180));

        // Title - larger, bold
        JLabel titleLabel = new JLabel("No PDF Loaded");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(new Color(60, 60, 60));

        // Subtitle - lighter weight, slightly smaller
        JLabel subtitleLabel = new JLabel("Drag and drop a PDF here or click below to open a file");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 14f));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setForeground(new Color(120, 120, 120));

        // Open PDF button - use FlatLaf default button styling
        JButton openBtn = UiFactory.createButton("Open PDF", null); // null to use FlatLaf default button color
        openBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        openBtn.addActionListener(e -> onOpen.run());

        // Layout spacing with professional proportions
        add(Box.createVerticalGlue());
        add(iconLabel);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(titleLabel);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(subtitleLabel);
        add(Box.createRigidArea(new Dimension(0, 30)));
        add(openBtn);
        add(Box.createVerticalGlue());
    }
}

