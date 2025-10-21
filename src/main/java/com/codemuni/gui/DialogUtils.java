package com.codemuni.gui;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.prefs.Preferences;

public class DialogUtils {

    public static final int INFO_MESSAGE = JOptionPane.INFORMATION_MESSAGE;
    public static final int ERROR_MESSAGE = JOptionPane.ERROR_MESSAGE;
    public static final int WARNING_MESSAGE = JOptionPane.WARNING_MESSAGE;
    public static final int QUESTION_MESSAGE = JOptionPane.QUESTION_MESSAGE;

    private static final Font MESSAGE_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    private static final int MIN_WIDTH = 350;

    /**
     * Shows an informational dialog.
     */
    public static void showInfo(Component parent, String title, String htmlMessage) {
        showMessage(parent, title, htmlMessage, INFO_MESSAGE);
    }

    /**
     * Shows a warning dialog.
     */
    public static void showWarning(Component parent, String title, String htmlMessage) {
        showMessage(parent, title, htmlMessage, WARNING_MESSAGE);
    }

    /**
     * Shows an error dialog.
     */
    public static void showError(Component parent, String title, String htmlMessage) {
        showMessage(parent, title, htmlMessage, ERROR_MESSAGE);
    }

    /**
     * Common method to show simple message dialogs with consistent font.
     */
    private static void showMessage(Component parent, String title, String htmlMessage, int messageType) {
        JLabel label = new JLabel("<html><div style='text-align:left; max-width:400px;'>" + htmlMessage + "</div></html>");
        label.setFont(MESSAGE_FONT);

        JOptionPane optionPane = new JOptionPane(label, messageType);
        JDialog dialog = optionPane.createDialog(parent, title);

        enforceMinWidth(dialog);
        centerDialog(dialog, parent);

        dialog.setVisible(true);
    }

    /**
     * Shows a confirmation dialog with Yes/No options.
     */
    public static boolean confirmYesNo(Component parent, String title, String htmlMessage) {
        JLabel label = formatHtmlMessage(htmlMessage);
        JOptionPane optionPane = new JOptionPane(label, QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
        JDialog dialog = optionPane.createDialog(parent, title);

        enforceMinWidth(dialog);
        centerDialog(dialog, parent);

        dialog.setVisible(true);

        Object selectedValue = optionPane.getValue();
        return selectedValue != null && (int) selectedValue == JOptionPane.YES_OPTION;
    }

    /**
     * Shows a confirmation dialog with Yes/No/Cancel options.
     */
    public static int confirmYesNoCancel(Component parent, String title, String htmlMessage) {
        int result = JOptionPane.showConfirmDialog(parent,
                formatHtmlMessage(htmlMessage),
                title,
                JOptionPane.YES_NO_CANCEL_OPTION,
                QUESTION_MESSAGE);

        return result;
    }

    /**
     * Shows a dialog with options.
     */
    public static int showOptionDialog(Component parent, String htmlMessage, String title, String[] options, int messageType) {
        JLabel label = new JLabel("<html><div style='text-align:left; max-width:400px;'>" + htmlMessage + "</div></html>");
        label.setFont(MESSAGE_FONT);

        JOptionPane optionPane = new JOptionPane(label, messageType, JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        JDialog dialog = optionPane.createDialog(parent, title);

        enforceMinWidth(dialog);
        centerDialog(dialog, parent);

        dialog.setVisible(true);

        Object selectedValue = optionPane.getValue();
        return selectedValue instanceof Integer ? (Integer) selectedValue : JOptionPane.CLOSED_OPTION;
    }

    private static JLabel formatHtmlMessage(String htmlMessage) {
        JLabel label = new JLabel("<html><div style='text-align:left; max-width:400px;'>" + htmlMessage + "</div></html>");
        label.setFont(MESSAGE_FONT);
        return label;
    }

    /**
     * Shows an exception dialog with collapsible details (stack trace).
     */
    public static void showExceptionDialog(Component parent, String title, Exception e) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JLabel summary = new JLabel("<html><div style='color:#ff5555; font-weight:bold;'>"
                + e.getClass().getSimpleName() + "</div>"
                + "<div style='margin-top:4px;'>"
                + (e.getMessage() != null ? e.getMessage() : "No message available")
                + "</div></html>");
        summary.setFont(MESSAGE_FONT);

        JTextArea detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(MONO_FONT);

        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        detailsArea.setText(sw.toString());

        JScrollPane scroll = new JScrollPane(detailsArea);
        scroll.setPreferredSize(new Dimension(600, 300));
        scroll.setVisible(false);

        JButton detailsBtn = new JButton("Show Details ▾");
        detailsBtn.addActionListener(ev -> {
            scroll.setVisible(!scroll.isVisible());
            detailsBtn.setText(scroll.isVisible() ? "Hide Details ▴" : "Show Details ▾");
            SwingUtilities.getWindowAncestor(panel).pack();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(detailsBtn);

        panel.add(summary, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JOptionPane optionPane = new JOptionPane(panel, ERROR_MESSAGE);
        JDialog dialog = optionPane.createDialog(parent, title);

        enforceMinWidth(dialog);
        centerDialog(dialog, parent);

        dialog.setVisible(true);
    }

    /**
     * Shows an HTML message with "Don't show again" checkbox.
     */
    public static void showHtmlMessageWithCheckbox(Component parent, String title,
                                                   String htmlMessage, String preferenceKey) {
        Preferences prefs = Preferences.userNodeForPackage(DialogUtils.class);
        if (prefs.getBoolean(preferenceKey, false)) {
            return;
        }

        JLabel messageLabel = new JLabel("<html><div style='text-align:left; max-width:400px;'>" + htmlMessage + "</div></html>");
        messageLabel.setFont(MESSAGE_FONT);

        JCheckBox dontShowAgain = new JCheckBox("Don't show this message again");
        dontShowAgain.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel checkboxPanel = new JPanel(new BorderLayout());
        checkboxPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); // top=10px margin
        checkboxPanel.add(dontShowAgain, BorderLayout.WEST);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(messageLabel, BorderLayout.CENTER);
        panel.add(checkboxPanel, BorderLayout.SOUTH);

        JOptionPane optionPane = new JOptionPane(panel, INFO_MESSAGE);
        JDialog dialog = optionPane.createDialog(parent, title);

        enforceMinWidth(dialog);
        centerDialog(dialog, parent);

        dialog.setVisible(true);

        if (dontShowAgain.isSelected()) {
            prefs.putBoolean(preferenceKey, true);
        }
    }

    /**
     * Ensures dialog has at least MIN_WIDTH.
     */
    private static void enforceMinWidth(JDialog dialog) {
        dialog.pack();
        Dimension size = dialog.getSize();
        if (size.width < MIN_WIDTH) {
            dialog.setSize(new Dimension(MIN_WIDTH, size.height));
        }
    }

    /**
     * Ensures dialog is centered relative to parent (or screen if parent is null).
     */
    private static void centerDialog(JDialog dialog, Component parent) {
        if (parent != null) {
            dialog.setLocationRelativeTo(parent);
        } else {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setLocation(
                    (screenSize.width - dialog.getWidth()) / 2,
                    (screenSize.height - dialog.getHeight()) / 2
            );
        }
    }
}
