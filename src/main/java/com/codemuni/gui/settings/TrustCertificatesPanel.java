package com.codemuni.gui.settings;

import com.codemuni.core.keyStoresProvider.X509SubjectUtils;
import com.codemuni.service.TrustStoreManager;
import com.codemuni.utils.UIConstants;
import com.codemuni.utils.IconLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Panel for managing trust certificates used in signature verification.
 *
 * Shows both embedded (read-only) and manually added certificates.
 * Users can add new certificates or remove their manually added ones.
 * Embedded certificates cannot be removed to ensure system security.
 */
public class TrustCertificatesPanel extends JPanel {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");

    private final TrustStoreManager trustStoreManager;
    private final DefaultTableModel tableModel;
    private final JTable certificatesTable;
    private final TableRowSorter<DefaultTableModel> tableSorter;
    private JLabel statusLabel;
    private JTextField searchField;
    private JLabel matchCountLabel;

    public TrustCertificatesPanel() {
        this.trustStoreManager = TrustStoreManager.getInstance();

        setLayout(new BorderLayout(10, 10));
        setBackground(UIConstants.Colors.BG_PRIMARY);
        setBorder(UIConstants.Padding.LARGE);

        // Create and add header
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Setup table to show certificates
        String[] columnNames = {"Type", "Alias", "Subject", "Issuer", "Valid Until"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Table is read-only
            }
        };
        certificatesTable = new JTable(tableModel);

        // Add table sorter for search functionality
        tableSorter = new TableRowSorter<DefaultTableModel>(tableModel);
        certificatesTable.setRowSorter(tableSorter);

        certificatesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        certificatesTable.setRowHeight(UIConstants.Dimensions.TABLE_ROW_HEIGHT);
        certificatesTable.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        certificatesTable.setShowGrid(true);
        certificatesTable.setGridColor(UIConstants.Colors.TABLE_GRID);
        certificatesTable.setIntercellSpacing(new Dimension(UIConstants.Dimensions.SPACING_SMALL, UIConstants.Dimensions.SPACING_TINY));

        // Table header styling
        certificatesTable.getTableHeader().setFont(UIConstants.Fonts.NORMAL_BOLD);
        certificatesTable.getTableHeader().setBackground(UIConstants.Colors.TABLE_HEADER);
        certificatesTable.getTableHeader().setForeground(UIConstants.Colors.TEXT_PRIMARY);
        certificatesTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, UIConstants.Colors.BORDER_PRIMARY));
        certificatesTable.getTableHeader().setPreferredSize(new Dimension(0, UIConstants.Dimensions.TABLE_HEADER_HEIGHT));

        // Set column widths
        certificatesTable.getColumnModel().getColumn(0).setPreferredWidth(90);   // Type
        certificatesTable.getColumnModel().getColumn(0).setMinWidth(80);
        certificatesTable.getColumnModel().getColumn(0).setMaxWidth(120);

        certificatesTable.getColumnModel().getColumn(1).setPreferredWidth(200);  // Alias
        certificatesTable.getColumnModel().getColumn(1).setMinWidth(150);

        certificatesTable.getColumnModel().getColumn(2).setPreferredWidth(250);  // Subject
        certificatesTable.getColumnModel().getColumn(2).setMinWidth(200);

        certificatesTable.getColumnModel().getColumn(3).setPreferredWidth(250);  // Issuer
        certificatesTable.getColumnModel().getColumn(3).setMinWidth(200);

        certificatesTable.getColumnModel().getColumn(4).setPreferredWidth(120);  // Valid Until
        certificatesTable.getColumnModel().getColumn(4).setMinWidth(100);
        certificatesTable.getColumnModel().getColumn(4).setMaxWidth(140);

        // Custom styling for table cells
        certificatesTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                         boolean isSelected, boolean hasFocus,
                                                         int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    c.setBackground(UIConstants.Colors.TABLE_SELECTION);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(row % 2 == 0 ? UIConstants.Colors.TABLE_ROW_EVEN : UIConstants.Colors.TABLE_ROW_ODD);
                    c.setForeground(UIConstants.Colors.TEXT_PRIMARY);

                    // Color code the Type column
                    if (column == 0) {
                        String type = (String) value;
                        if ("Embedded".equals(type)) {
                            c.setForeground(UIConstants.Colors.TYPE_EMBEDDED);
                        } else if ("Manual".equals(type)) {
                            c.setForeground(UIConstants.Colors.TYPE_MANUAL);
                        }
                    }
                }

                setBorder(BorderFactory.createEmptyBorder(
                    UIConstants.Dimensions.SPACING_TINY,
                    UIConstants.Dimensions.SPACING_SMALL,
                    UIConstants.Dimensions.SPACING_TINY,
                    UIConstants.Dimensions.SPACING_SMALL));
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(certificatesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIConstants.Colors.BORDER_PRIMARY, 1));
        scrollPane.getViewport().setBackground(UIConstants.Colors.BG_SECONDARY);

        // Create panel to hold search bar and table
        JPanel tablePanel = new JPanel(new BorderLayout(0, 0));
        tablePanel.setBackground(UIConstants.Colors.BG_PRIMARY);
        tablePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(UIConstants.Colors.BORDER_PRIMARY, 1),
            "Trust Certificates (Embedded + Manual)",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            UIConstants.Fonts.LARGE_BOLD,
            UIConstants.Colors.TEXT_SECONDARY
        ));

        // Add search panel
        JPanel searchPanel = createSearchPanel();
        tablePanel.add(searchPanel, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        add(tablePanel, BorderLayout.CENTER);

        // Bottom panel with buttons and status
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);

        // Load certificates
        loadCertificates();
    }

    /**
     * Creates the search panel with search field and clear button.
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.Dimensions.SPACING_SMALL, 0));
        panel.setBackground(UIConstants.Colors.BG_SECONDARY);
        panel.setBorder(new EmptyBorder(
            UIConstants.Dimensions.SPACING_SMALL,
            UIConstants.Dimensions.SPACING_NORMAL,
            UIConstants.Dimensions.SPACING_SMALL,
            UIConstants.Dimensions.SPACING_NORMAL
        ));

        // Left side - Search label and field
        JPanel searchInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.Dimensions.SPACING_SMALL, 0));
        searchInputPanel.setBackground(UIConstants.Colors.BG_SECONDARY);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(UIConstants.Fonts.NORMAL_BOLD);
        searchLabel.setForeground(UIConstants.Colors.TEXT_SECONDARY);

        searchField = new JTextField(30);
        searchField.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        searchField.setPreferredSize(new Dimension(300, 28));
        searchField.setToolTipText("Search by type, alias, subject, or issuer");

        // Add document listener for real-time filtering
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                filterTable();
            }
            public void removeUpdate(DocumentEvent e) {
                filterTable();
            }
            public void insertUpdate(DocumentEvent e) {
                filterTable();
            }
        });

        // ESC key to clear search
        searchField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");
                }
            }
        });

        // Clear button
        JButton clearButton = new JButton("Clear");
        clearButton.setFont(UIConstants.Fonts.SMALL_PLAIN);
        clearButton.setPreferredSize(new Dimension(70, 26));
        clearButton.setFocusPainted(false);
        clearButton.setBackground(UIConstants.Colors.BUTTON_SECONDARY);
        clearButton.setForeground(Color.WHITE);
        clearButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.Colors.BORDER_SECONDARY, 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                clearButton.setBackground(UIConstants.Colors.BUTTON_SECONDARY_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                clearButton.setBackground(UIConstants.Colors.BUTTON_SECONDARY);
            }
        });
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchField.setText("");
            }
        });

        searchInputPanel.add(searchLabel);
        searchInputPanel.add(searchField);
        searchInputPanel.add(clearButton);

        panel.add(searchInputPanel, BorderLayout.WEST);

        // Right side - Match count label
        JPanel matchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        matchPanel.setBackground(UIConstants.Colors.BG_SECONDARY);

        matchCountLabel = new JLabel(" ");
        matchCountLabel.setFont(UIConstants.Fonts.SMALL_PLAIN);
        matchCountLabel.setForeground(UIConstants.Colors.TEXT_DISABLED);

        matchPanel.add(matchCountLabel);
        panel.add(matchPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Filters the table based on search text.
     * Searches across Type, Alias, Subject, and Issuer columns.
     */
    private void filterTable() {
        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            tableSorter.setRowFilter(null);
            matchCountLabel.setText(" ");
        } else {
            try {
                // Escape special regex characters for literal search
                String escapedSearch = searchText.replaceAll("([\\\\\\[\\]{}()*+?.$^|])", "\\\\$1");
                // Create case-insensitive filter that searches all text columns
                RowFilter<DefaultTableModel, Object> rowFilter = RowFilter.regexFilter("(?i)" + escapedSearch);
                tableSorter.setRowFilter(rowFilter);

                // Update match count
                int matchCount = certificatesTable.getRowCount();
                int totalCount = tableModel.getRowCount();
                matchCountLabel.setText("Showing " + matchCount + " of " + totalCount + " certificates");
                matchCountLabel.setForeground(UIConstants.Colors.STATUS_VALID);
            } catch (java.util.regex.PatternSyntaxException e) {
                // If regex is invalid, just show all rows
                tableSorter.setRowFilter(null);
                matchCountLabel.setText("Invalid search pattern");
                matchCountLabel.setForeground(UIConstants.Colors.STATUS_ERROR);
            }
        }
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.Dimensions.SPACING_NORMAL, 0));
        panel.setBackground(UIConstants.Colors.BG_PRIMARY);
        panel.setBorder(new EmptyBorder(0, 0, UIConstants.Dimensions.SPACING_LARGE, 0));

        // Shield icon on the left side
        JPanel iconPanel = new JPanel();
        iconPanel.setBackground(UIConstants.Colors.BG_PRIMARY);
        iconPanel.setPreferredSize(new Dimension(180, 160));
        iconPanel.setLayout(new GridBagLayout());

        // Try to load the shield icon
        ImageIcon shieldIcon = IconLoader.loadIcon("shield.png", 140);
        if (shieldIcon != null) {
            JLabel iconLabel = new JLabel(shieldIcon);
            iconPanel.add(iconLabel);
        } else {
            // Fallback to emoji
            JLabel fallbackLabel = IconLoader.createShieldFallback(80);
            iconPanel.add(fallbackLabel);
        }

        panel.add(iconPanel, BorderLayout.WEST);

        // Info text on the right side
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(UIConstants.Colors.BG_PRIMARY);

        // Title
        JLabel titleLabel = new JLabel("Trust Manager - Certificate Management");
        titleLabel.setFont(UIConstants.Fonts.TITLE_BOLD);
        titleLabel.setForeground(UIConstants.Colors.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Description - compact
        JLabel descLabel = new JLabel("<html>" +
            "<div style='margin-top: 8px; margin-bottom: 6px;'>" +
            "This panel shows ALL trust certificates used for signature verification" +
            "</div></html>");
        descLabel.setFont(UIConstants.Fonts.SMALL_PLAIN);
        descLabel.setForeground(UIConstants.Colors.TEXT_TERTIARY);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Certificate types - single line
        JLabel typesLabel = new JLabel("<html>" +
            "<b style='color: #6eb3ff;'>Embedded</b> (read-only) • " +
            "<b style='color: #64dd64;'>Manual</b> (removable)" +
            "</html>");
        typesLabel.setFont(UIConstants.Fonts.SMALL_PLAIN);
        typesLabel.setForeground(UIConstants.Colors.TEXT_SECONDARY);
        typesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Formats - compact
        JLabel formatsLabel = new JLabel("<html>" +
            "<span style='color: #aaaaaa;'><b>Formats:</b> PEM, DER, CER, CRT, PKCS#7</span>" +
            "</html>");
        formatsLabel.setFont(UIConstants.Fonts.TINY_PLAIN);
        formatsLabel.setForeground(UIConstants.Colors.TEXT_MUTED);
        formatsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(titleLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, UIConstants.Dimensions.SPACING_SMALL)));
        infoPanel.add(descLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, UIConstants.Dimensions.SPACING_TINY)));
        infoPanel.add(typesLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, UIConstants.Dimensions.SPACING_TINY)));
        infoPanel.add(formatsLabel);

        panel.add(infoPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(UIConstants.Colors.BG_PRIMARY);
        panel.setBorder(new EmptyBorder(UIConstants.Dimensions.SPACING_SMALL, 0, 0, 0));

        // Status label with icon
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        statusPanel.setBackground(UIConstants.Colors.BG_PRIMARY);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        statusLabel.setForeground(UIConstants.Colors.TEXT_DISABLED);
        statusPanel.add(statusLabel);

        panel.add(statusPanel, BorderLayout.WEST);

        // Buttons panel with better styling
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.Dimensions.SPACING_NORMAL, 0));
        buttonsPanel.setBackground(UIConstants.Colors.BG_PRIMARY);

        // Refresh button
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        refreshButton.setPreferredSize(UIConstants.buttonSize(UIConstants.Dimensions.BUTTON_WIDTH_SMALL));
        refreshButton.setFocusPainted(false);
        refreshButton.setBackground(UIConstants.Colors.BUTTON_SECONDARY);
        refreshButton.setForeground(Color.WHITE);
        refreshButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.Colors.BORDER_SECONDARY, 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        refreshButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                refreshButton.setBackground(UIConstants.Colors.BUTTON_SECONDARY_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                refreshButton.setBackground(UIConstants.Colors.BUTTON_SECONDARY);
            }
        });
        refreshButton.addActionListener(e -> loadCertificates());

        // Remove button
        JButton removeButton = new JButton("Remove Selected");
        removeButton.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        removeButton.setPreferredSize(UIConstants.buttonSize(UIConstants.Dimensions.BUTTON_WIDTH_MEDIUM));
        removeButton.setFocusPainted(false);
        removeButton.setBackground(UIConstants.Colors.BUTTON_DANGER);
        removeButton.setForeground(Color.WHITE);
        removeButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.Colors.BUTTON_DANGER_HOVER, 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                removeButton.setBackground(UIConstants.Colors.BUTTON_DANGER_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                removeButton.setBackground(UIConstants.Colors.BUTTON_DANGER);
            }
        });
        removeButton.addActionListener(e -> removeCertificate());

        // Add button
        JButton addButton = new JButton("Add Certificate");
        addButton.setFont(UIConstants.Fonts.NORMAL_BOLD);
        addButton.setPreferredSize(UIConstants.buttonSize(UIConstants.Dimensions.BUTTON_WIDTH_MEDIUM));
        addButton.setFocusPainted(false);
        addButton.setBackground(UIConstants.Colors.BUTTON_PRIMARY);
        addButton.setForeground(Color.WHITE);
        addButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.Colors.BUTTON_PRIMARY_HOVER, 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        addButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                addButton.setBackground(UIConstants.Colors.BUTTON_PRIMARY_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                addButton.setBackground(UIConstants.Colors.BUTTON_PRIMARY);
            }
        });
        addButton.addActionListener(e -> addCertificate());

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(removeButton);
        buttonsPanel.add(addButton);

        panel.add(buttonsPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Loads and displays all trust certificates (embedded + manual).
     */
    private void loadCertificates() {
        // Clear search when reloading
        if (searchField != null) {
            searchField.setText("");
        }

        tableModel.setRowCount(0); // Clear table

        // Load embedded certificates
        Set<X509Certificate> embeddedCerts = trustStoreManager.getEmbeddedCertificates();
        int embeddedCount = 0;
        for (X509Certificate cert : embeddedCerts) {
            String cn = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());
            String subject = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());
            String issuer = X509SubjectUtils.extractCommonNameFromDN(cert.getIssuerDN().toString());
            String validUntil = DATE_FORMAT.format(cert.getNotAfter());

            // Type, Alias, Subject, Issuer, Valid Until
            tableModel.addRow(new Object[]{"Embedded", cn, subject, issuer, validUntil});
            embeddedCount++;
        }

        // Load manual certificates
        Map<String, X509Certificate> manualCerts = trustStoreManager.getManualCertificates();
        int manualCount = 0;
        for (Map.Entry<String, X509Certificate> entry : manualCerts.entrySet()) {
            String alias = entry.getKey();
            X509Certificate cert = entry.getValue();

            String subject = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());
            String issuer = X509SubjectUtils.extractCommonNameFromDN(cert.getIssuerDN().toString());
            String validUntil = DATE_FORMAT.format(cert.getNotAfter());

            // Type, Alias, Subject, Issuer, Valid Until
            tableModel.addRow(new Object[]{"Manual", alias, subject, issuer, validUntil});
            manualCount++;
        }

        // Update status
        int total = embeddedCount + manualCount;
        if (total == 0) {
            statusLabel.setText("No trust certificates available.");
            statusLabel.setForeground(UIConstants.Colors.TEXT_DISABLED);
        } else {
            statusLabel.setText("Loaded " + total + " certificate(s) - Embedded: " +
                              embeddedCount + ", Manual: " + manualCount);
            statusLabel.setForeground(UIConstants.Colors.STATUS_VALID);
        }
    }

    /**
     * Adds a new trust certificate.
     * Automatically uses CN (Common Name) as alias - no user prompt needed.
     * Supports multiple formats: PEM, DER, CER, CRT, P7B, P7C, SPC
     */
    private void addCertificate() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Trust Certificate");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Certificate Files (*.pem, *.der, *.cer, *.crt, *.p7b, *.p7c, *.spc)",
            "pem", "der", "cer", "crt", "p7b", "p7c", "spc"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File certFile = fileChooser.getSelectedFile();

            try {
                // Load certificate to extract CN
                X509Certificate cert = loadCertificateFromFile(certFile);

                // Extract CN from certificate subject
                String cn = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());

                // Use CN as alias (with counter if duplicate)
                String alias = generateUniqueAlias(cn);

                // Add certificate - TrustStoreManager will handle multiple certs if present
                trustStoreManager.addTrustCertificate(certFile, alias);
                statusLabel.setText("Certificate(s) added successfully: " + cn);
                statusLabel.setForeground(UIConstants.Colors.STATUS_VALID);
                loadCertificates(); // Refresh list
            } catch (Exception e) {
                statusLabel.setText("Failed to add certificate: " + e.getMessage());
                statusLabel.setForeground(UIConstants.Colors.STATUS_ERROR);
                JOptionPane.showMessageDialog(this,
                    "Failed to add certificate:\n" + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Loads X509Certificate from file for preview/validation.
     * Supports all formats: PEM, DER, CER, CRT, P7B, P7C, SPC
     * Returns the first certificate if file contains multiple.
     */
    private X509Certificate loadCertificateFromFile(File file) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);

            // Try to parse certificate(s) - works for all formats
            Collection<? extends Certificate> certs = cf.generateCertificates(fis);

            if (certs == null || certs.isEmpty()) {
                // If generateCertificates fails, try single certificate parsing
                fis.close();
                fis = new FileInputStream(file);
                Certificate cert = cf.generateCertificate(fis);
                if (cert instanceof X509Certificate) {
                    return (X509Certificate) cert;
                } else {
                    throw new Exception("Not a valid X.509 certificate");
                }
            }

            // Return first certificate from collection
            for (Certificate cert : certs) {
                if (cert instanceof X509Certificate) {
                    return (X509Certificate) cert;
                }
            }

            throw new Exception("No valid X.509 certificate found in file");

        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    // Ignore close exception
                }
            }
        }
    }

    /**
     * Generates unique alias from CN.
     * If CN already exists, appends counter.
     */
    private String generateUniqueAlias(String cn) {
        String baseAlias = cn;
        String alias = baseAlias;
        int counter = 1;

        // Check if alias already exists
        Map<String, X509Certificate> existing = trustStoreManager.getManualCertificates();
        while (existing.containsKey(alias)) {
            alias = baseAlias + "_" + counter;
            counter++;
        }

        return alias;
    }

    /**
     * Removes the selected trust certificate.
     * Only manual certificates can be removed - embedded are read-only.
     */
    private void removeCertificate() {
        int selectedRow = certificatesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                "Please select a certificate to remove.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check certificate type (column 0)
        String type = (String) tableModel.getValueAt(selectedRow, 0);
        String alias = (String) tableModel.getValueAt(selectedRow, 1);

        // Only manual certificates can be removed
        if ("Embedded".equals(type)) {
            JOptionPane.showMessageDialog(this,
                "Embedded certificates are read-only and cannot be removed.\n\n" +
                "Only manually added certificates can be deleted.",
                "Cannot Remove Embedded Certificate",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to remove this certificate?\n\nAlias: " + alias,
            "Confirm Removal",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean removed = trustStoreManager.removeTrustCertificate(alias);
            if (removed) {
                statusLabel.setText("Certificate removed successfully!");
                statusLabel.setForeground(UIConstants.Colors.STATUS_VALID);
                loadCertificates(); // Refresh list
            } else {
                statusLabel.setText("Failed to remove certificate.");
                statusLabel.setForeground(UIConstants.Colors.STATUS_ERROR);
            }
        }
    }

}
