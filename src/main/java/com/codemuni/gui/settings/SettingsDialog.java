package com.codemuni.gui.settings;

import javax.swing.*;
import java.awt.*;

import static com.codemuni.utils.AppConstants.APP_NAME;

public class SettingsDialog extends JDialog {
    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_HEIGHT = 600;

    public SettingsDialog(JFrame parent) {
        super(parent, APP_NAME+ " - Keystore and Security Settings", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        setResizable(false);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(tabbedPane, BorderLayout.CENTER);

        // Keystore Tab
        KeystoreSettingsPanel keystorePanel = new KeystoreSettingsPanel(parent);
        tabbedPane.addTab("Keystore", keystorePanel);

        // Security Tab
        tabbedPane.addTab("Security", new SecuritySettingsPanel());

        // About Tab
        tabbedPane.addTab("About", new AboutPanel());

        // Bottom panel with Trust Manager button
        JPanel bottomPanel = createBottomPanel(parent);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createBottomPanel(final JFrame parent) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Trust Manager button on left
        JButton trustManagerBtn = new JButton("Open Trust Manager");
        trustManagerBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        trustManagerBtn.setToolTipText("Open separate window to manage trust certificates");
        trustManagerBtn.setFocusPainted(false);
        trustManagerBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                TrustManagerDialog.showTrustManager(parent);
            }
        });

        // Close button on right
        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });

        panel.add(trustManagerBtn, BorderLayout.WEST);
        panel.add(closeBtn, BorderLayout.EAST);

        return panel;
    }
}
