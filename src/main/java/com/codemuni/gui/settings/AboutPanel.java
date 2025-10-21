package com.codemuni.gui.settings;

import com.codemuni.gui.DialogUtils;
import com.codemuni.utils.Utils;
import com.codemuni.service.VersionManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

import static com.codemuni.utils.AppConstants.*;

public class AboutPanel extends JPanel {

    private static final Log log = LogFactory.getLog(AboutPanel.class);
    private static final int LOGO_SIZE = 72;
    private static final Color LINK_COLOR = new Color(66, 133, 244);  // Blue
    private static final Color LINK_HOVER = LINK_COLOR.darker();

    private final JButton versionBtn;

    public AboutPanel() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Main card
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setMaximumSize(new Dimension(360, 480));

        // --- Logo ---
        JLabel logoLabel = new JLabel(Utils.loadScaledIcon(LOGO_PATH, LOGO_SIZE));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoLabel.setBorder(new EmptyBorder(0, 0, 12, 0));

        // --- App Name ---
        JLabel titleLabel = new JLabel(APP_NAME);
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 22));
        titleLabel.setForeground(UIManager.getColor("Label.foreground"));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // --- Version ---
        JLabel versionLabel = new JLabel("Version " + APP_VERSION);
        versionLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        versionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setBorder(new EmptyBorder(3, 0, 15, 0));

        // --- Description ---
        JEditorPane descriptionPane = getDescriptionPane();

        // --- Links (Website, License, GitHub) ---
        JPanel linksPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        linksPanel.setOpaque(false);
        linksPanel.add(createStyledLink("🌐 Website", APP_WEBSITE));
        linksPanel.add(createStyledLink("📄 License", APP_LICENSE_URL));
        linksPanel.add(createStyledLink("💻 GitHub", APP_GITHUB)); // fixed GitHub link
        linksPanel.setBorder(new EmptyBorder(8, 0, 15, 0));

        // --- Version Check Button ---
        versionBtn = new JButton("Check Update");
        versionBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        versionBtn.addActionListener(e ->
                VersionManager.checkUpdateWithInfoAsync(new VersionManager.UpdateInfoCallback() {
                    @Override
                    public void onResult(final VersionManager.UpdateInfo info) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                if (info.updateAvailable && info.latestVersion != null) {
                                    VersionManager.showUpdateDialogAsync(AboutPanel.this, info.latestVersion);
                                } else {
                                    VersionManager.showUpToDateDialog(AboutPanel.this);
                                }
                            }
                        });
                    }
                })
        );




        // --- Footer ---
        JLabel copyrightLabel = new JLabel(
                "© " + Year.now().getValue() + " " + APP_AUTHOR + ". All rights reserved."
        );
        copyrightLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        copyrightLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        copyrightLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Assemble
        card.add(Box.createVerticalGlue());
        card.add(logoLabel);
        card.add(titleLabel);
        card.add(versionLabel);
        card.add(descriptionPane);
        card.add(linksPanel);
        card.add(versionBtn);  // <- add button here
        card.add(copyrightLabel);
        card.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(card, new GridBagConstraints());

        add(wrapper, BorderLayout.CENTER);
    }

    private JEditorPane getDescriptionPane() {
        JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditorKit(new HTMLEditorKit() {
            @Override
            public StyleSheet getStyleSheet() {
                StyleSheet styleSheet = super.getStyleSheet();
                styleSheet.addRule("body { text-align: center; color: " +
                        getColorHex(UIManager.getColor("Label.foreground")) +
                        "; font-family: Dialog; font-size: 13px; line-height: 1.5; }");
                return styleSheet;
            }
        });
        pane.setText("<html><body>" + APP_DESCRIPTION + "</body></html>");
        pane.setOpaque(false);
        pane.setEditable(false);
        pane.setFocusable(false);
        pane.setAlignmentX(Component.CENTER_ALIGNMENT);
        pane.setBorder(new EmptyBorder(0, 0, 12, 0));
        pane.setMaximumSize(new Dimension(320, 60));
        return pane;
    }

    private JLabel createStyledLink(String text, String url) {
        JLabel link = new JLabel(text);
        link.setFont(new Font("Dialog", Font.PLAIN, 13));
        link.setForeground(LINK_COLOR);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Keep a base font
        Font baseFont = link.getFont();

        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                Map<TextAttribute, Object> attrs = new HashMap<>(baseFont.getAttributes());
                attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                link.setFont(baseFont.deriveFont(attrs));
                link.setForeground(LINK_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                link.setFont(baseFont); // back to normal
                link.setForeground(LINK_COLOR);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                openUrl(url);
            }
        });

        return link;
    }


    private void openUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(uri);
            } else {
                JOptionPane.showMessageDialog(this,
                        "<html>Unable to open browser.<br>Please visit:<br><b>" + urlString + "</b></html>",
                        "Open Link",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (URISyntaxException e) {
            log.error("Invalid URL: " + urlString, e);
            DialogUtils.showError(null, "Error", "Invalid link address.");
        } catch (IOException e) {
            log.error("Failed to open URL: " + urlString, e);
            DialogUtils.showError(null, "Error", "Could not open web browser.");
        } catch (Exception e) {
            log.error("Unexpected error opening URL: " + urlString, e);
            DialogUtils.showError(null, "Error", "An unexpected error occurred.");
        }
    }

    private String getColorHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
