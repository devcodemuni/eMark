package com.codemuni.gui;

import com.codemuni.config.ConfigManager;
import com.codemuni.core.model.KeystoreAndCertificateInfo;
import com.codemuni.gui.pdfHandler.PdfViewerMain;
import com.codemuni.utils.AppConstants;
import com.codemuni.utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static com.codemuni.utils.AppConstants.APP_NAME;

public class CertificateListDialog extends JDialog {
    private static final Color COLOR_BACKGROUND = new Color(34, 34, 34); // base UI
    private static final Color COLOR_BORDER_SELECTED = new Color(77, 145, 218);
    private static final Color COLOR_BORDER_NORMAL = new Color(64, 64, 64);

    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(150, 150, 150);

    private final List<KeystoreAndCertificateInfo> certificateList;
    private final List<JPanel> cardList = new ArrayList<>();

    private static final Preferences prefs = Preferences.userNodeForPackage(CertificateListDialog.class);
    private static final String LAST_DIR_KEY = "lastPdfDir";

    private JButton browseButton;
    private File selectedPfxFile = null;
    private JLabel selectedFileLabel;
    private JScrollPane certificateScrollPane;

    private JPanel selectedCard;

    public CertificateListDialog(Frame parent, List<KeystoreAndCertificateInfo> certificates) {
        super(parent, APP_NAME + " - Choose Certificate", true);
        this.certificateList = certificates != null ? certificates : new ArrayList<>();

        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    // for the PFX file browser dialog
    private static JFileChooser getJFileChooser() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".pfx") || name.endsWith(".p12");
            }

            @Override
            public String getDescription() {
                return "PFX Files (*.pfx, *.p12)";
            }
        });
        return fileChooser;
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(600, 540));
        setResizable(false);
        getContentPane().setBackground(COLOR_BACKGROUND);

        // Prevent default dispose so we can handle it manually
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancelSelection();
            }
        });

        add(createHeaderLabel(), BorderLayout.NORTH);
        add(createCertificateScrollPane(), BorderLayout.CENTER);
        add(createFooterPanel(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> cancelSelection(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JLabel createHeaderLabel() {
        JLabel label = new JLabel("Select a Certificate for Signing");
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        label.setForeground(COLOR_TEXT_PRIMARY);
        label.setBorder(new EmptyBorder(20, 25, 10, 25));
        return label;
    }

    private JScrollPane createCertificateScrollPane() {
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(COLOR_BACKGROUND);
        listPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        if (certificateList.isEmpty()) {
            JLabel empty = new JLabel("No certificates available.");
            empty.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            empty.setForeground(COLOR_TEXT_SECONDARY);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(empty);
        } else {
            for (KeystoreAndCertificateInfo info : certificateList) {
                JPanel card = createCertificateCard(info);
                cardList.add(card);
                listPanel.add(card);
                listPanel.add(Box.createVerticalStrut(8));
            }
        }

        certificateScrollPane = new JScrollPane(listPanel);
        certificateScrollPane.setBorder(BorderFactory.createEmptyBorder());
        certificateScrollPane.setBackground(COLOR_BACKGROUND);
        certificateScrollPane.getViewport().setBackground(COLOR_BACKGROUND);
        certificateScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        certificateScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return certificateScrollPane;
    }

    private JPanel createFooterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 15, 10, 15));
        panel.setBackground(COLOR_BACKGROUND);

        // Left Panel: Browse Button + Label
        browseButton = new JButton("Browse PFX File");
        browseButton.addActionListener(e -> handleBrowseOrCancel());

        JLabel browseLabel = new JLabel();
        browseLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        browseLabel.setForeground(COLOR_TEXT_PRIMARY);
        browseLabel.setPreferredSize(new Dimension(150, 20));

        // Right Panel: Filename Label + Buttons (Choose, Cancel)
        selectedFileLabel = new JLabel("");
        selectedFileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        selectedFileLabel.setForeground(COLOR_TEXT_SECONDARY);
        selectedFileLabel.setPreferredSize(new Dimension(150, 20));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(browseButton);
        leftPanel.add(selectedFileLabel);
        leftPanel.setVisible(ConfigManager.isPFXStoreActive());

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> cancelSelection());

        JButton choose = new JButton("Choose");
        choose.addActionListener(e -> {
            if (selectedCard != null || selectedPfxFile != null) {
                dispose();
            } else {
                DialogUtils.showWarning(this, "No Selection", "Please choose a certificate or PFX file.");
            }
        });

        getRootPane().setDefaultButton(choose);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(choose);
        rightPanel.add(cancel);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private void handleBrowseOrCancel() {
        if (selectedPfxFile == null) {
            JFileChooser fileChooser = getJFileChooser();

            String lastDir = prefs.get(LAST_DIR_KEY, null);
            if (lastDir != null) {
                fileChooser.setCurrentDirectory(new File(lastDir));
            }

            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {

                File parentDir = fileChooser.getSelectedFile().getParentFile();
                if (parentDir != null) {
                    prefs.put(LAST_DIR_KEY, parentDir.getAbsolutePath());
                }

                // Check if the selected file is a PFX file
                String fileName = fileChooser.getSelectedFile().getName();
                if (!fileName.toLowerCase().endsWith(".pfx") && !fileName.toLowerCase().endsWith(".p12")) {
                    DialogUtils.showWarning(this, "Invalid File", "Please select a PFX file.");
                    return;
                }

                selectedPfxFile = fileChooser.getSelectedFile();
                browseButton.setText("Cancel");
                setCertificateCardsEnabled(false);

                if (fileName.length() > 40) {
                    fileName = fileName.substring(0, 35) + "..." + fileName.substring(fileName.length() - 3);
                }
                selectedFileLabel.setText(fileName);
                selectedFileLabel.setToolTipText(selectedPfxFile.getAbsolutePath());

                dispose();
            }
        } else {
            selectedPfxFile = null;
            browseButton.setText("Browse PFX File");
            setCertificateCardsEnabled(true);
            selectedFileLabel.setText("");
            selectedFileLabel.setToolTipText(null);
        }
    }

    private void setCertificateCardsEnabled(boolean enabled) {
        for (JPanel card : cardList) {
            card.setEnabled(enabled);
            for (Component comp : card.getComponents()) {
                comp.setEnabled(enabled);
            }
            card.setCursor(enabled ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
        certificateScrollPane.setEnabled(enabled);
        certificateScrollPane.getViewport().getView().setEnabled(enabled);
    }


    private JPanel createCertificateCard(KeystoreAndCertificateInfo info) {
        X509Certificate cert = info.getCertificate();

        JPanel card = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (this == selectedCard) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(77, 145, 218, 50)); // Semi-transparent blue
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                }
            }
        };

        card.setOpaque(false);
        card.setBackground(new Color(0, 0, 0, 0)); // Fully transparent background
        card.setPreferredSize(new Dimension(520, 75));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER_NORMAL, 2, true),
                new EmptyBorder(10, 15, 10, 15)));

        putCardProperties(card, info, cert);
        JLabel iconLabel = new JLabel(Utils.loadScaledIcon(getIconPath(info.getKeystoreName()), 32));
        iconLabel.setToolTipText("Click to select. Double-click to view details.");

        card.add(iconLabel, BorderLayout.WEST);
        card.add(createCardDetailsPanel(cert), BorderLayout.CENTER);
        card.add(createRightDetailsPanel(cert), BorderLayout.EAST);

        // Mouse events
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!card.isEnabled()) return;  // Block event if disabled
                if (e.getClickCount() == 2) {
                    new CertificateDetailsDialog(PdfViewerMain.INSTANCE, cert);
                } else {
                    selectCard(card);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!card.isEnabled()) return;
                if (card != selectedCard) {
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(COLOR_BORDER_SELECTED, 2, true),
                            new EmptyBorder(10, 15, 10, 15)));
                    card.setBackground(new Color(52, 120, 193, 30)); // Semi-transparent hover
                    card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!card.isEnabled()) return;
                if (card != selectedCard) {
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(COLOR_BORDER_NORMAL, 2, true),
                            new EmptyBorder(10, 15, 10, 15)));
                    card.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
                    card.setCursor(Cursor.getDefaultCursor());
                }
            }
        });
        return card;
    }

    private void putCardProperties(JPanel card, KeystoreAndCertificateInfo info, X509Certificate cert) {
        card.putClientProperty("keystoreName", info.getKeystoreName());
        card.putClientProperty("x509Certificate", cert);
        if (info.getTokenSerial() != null) card.putClientProperty("tokenSerialNumber", info.getTokenSerial());
        if (info.getPkcs11LibPath() != null) card.putClientProperty("pkcs11Path", info.getPkcs11LibPath());
    }

    private String getIconPath(String keystoreName) {
        switch (keystoreName) {
            case AppConstants.PKCS11_KEY_STORE:
                return "/icons/pkcs11.png";
            case AppConstants.SOFTHSM:
                return "/icons/pfx.png";
            case AppConstants.WIN_KEY_STORE:
                return "/icons/certificate.png";
            default:
                return null;
        }
    }

    private JPanel createCardDetailsPanel(X509Certificate cert) {
        String subject = extractFromDN(cert.getSubjectDN().getName(), "CN");
        String issuer = extractFromDN(cert.getIssuerDN().getName(), "O");

        JLabel name = new JLabel(subject != null ? subject : "Unknown Subject");
        name.setFont(new Font("Segoe UI", Font.BOLD, 14));
        name.setForeground(COLOR_TEXT_PRIMARY);

        JLabel issuerLabel = new JLabel(issuer != null ? issuer : "Unknown Issuer");
        issuerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        issuerLabel.setForeground(COLOR_TEXT_SECONDARY);
        issuerLabel.setToolTipText(cert.getIssuerDN().getName());

        JPanel labels = new JPanel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        labels.setOpaque(false);
        labels.add(name);
        labels.add(Box.createVerticalStrut(10));
        labels.add(issuerLabel);

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(labels, BorderLayout.CENTER);
        return container;
    }

    private JPanel createRightDetailsPanel(X509Certificate cert) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");

        JLabel serial = new JLabel("Serial: " + cert.getSerialNumber().toString(16));
        serial.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        serial.setForeground(COLOR_TEXT_PRIMARY);

        JLabel expires = new JLabel("Expires: " + sdf.format(cert.getNotAfter()));
        expires.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        expires.setForeground(COLOR_TEXT_SECONDARY);

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0, 10, 0, 0));
        wrapper.add(wrapRight(serial));
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(wrapRight(expires));

        return wrapper;
    }

    private JPanel wrapRight(JComponent comp) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        panel.setOpaque(false);
        panel.add(comp);
        return panel;
    }

    private void selectCard(JPanel card) {
        selectedCard = card;
        for (JPanel p : cardList) {
            boolean selected = p == card;
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? COLOR_BORDER_SELECTED : COLOR_BORDER_NORMAL, 2, true),
                    new EmptyBorder(10, 15, 10, 15)));
            p.setBackground(selected ? new Color(77, 145, 218, 50) : new Color(0, 0, 0, 0));
            p.repaint();
        }
    }

    private void cancelSelection() {
        selectedCard = null;
        selectedFileLabel.setText("");
        selectedPfxFile = null;
        dispose();
    }

    public KeystoreAndCertificateInfo getSelectedKeystoreInfo() {
        if (selectedCard == null && selectedPfxFile == null) return null;


        if (selectedPfxFile != null)
            return new KeystoreAndCertificateInfo(AppConstants.SOFTHSM, selectedPfxFile.getAbsolutePath());

        String keystoreName = (String) selectedCard.getClientProperty("keystoreName");
        X509Certificate x509Certificate = (X509Certificate) selectedCard.getClientProperty("x509Certificate");
        String tokenSerial = (String) selectedCard.getClientProperty("tokenSerialNumber");
        String pkcs11Path = (String) selectedCard.getClientProperty("pkcs11Path");

        return new KeystoreAndCertificateInfo(x509Certificate, keystoreName, tokenSerial, pkcs11Path);
    }

    private String extractFromDN(String dn, String key) {
        for (String part : dn.split(",")) {
            if (part.trim().startsWith(key + "=")) return part.trim().substring(key.length() + 1);
        }
        return null;
    }
}
