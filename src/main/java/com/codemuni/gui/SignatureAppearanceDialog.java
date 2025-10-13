package com.codemuni.gui;

import com.codemuni.App;
import com.codemuni.core.keyStoresProvider.X509SubjectUtils;
import com.codemuni.core.signer.AppearanceOptions;
import com.codemuni.core.signer.SignatureDateFormats;
import com.codemuni.model.CertificationLevel;
import com.codemuni.model.RenderingMode;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.prefs.Preferences;

import static com.codemuni.core.keyStoresProvider.X509SubjectUtils.getCommonName;
import static com.codemuni.core.keyStoresProvider.X509SubjectUtils.getOrganization;
import static com.itextpdf.text.pdf.PdfSigLockDictionary.LockPermissions.FORM_FILLING;
import static com.itextpdf.text.pdf.PdfSigLockDictionary.LockPermissions.FORM_FILLING_AND_ANNOTATION;

public class SignatureAppearanceDialog extends JDialog {

    private static final Log log = LogFactory.getLog(SignatureAppearanceDialog.class);
    private final Frame parent;
    // Preferences node
    private final Preferences prefs = Preferences.userNodeForPackage(SignatureAppearanceDialog.class);
    private X509Certificate certificate;
    private JTextField reasonField;
    private JTextField locationField;
    private JTextField customTextField;
    private JCheckBox ltvCheckbox, timestampCheckbox, greenTickCheckbox, includeCompanyCheckbox, includeEntireSubjectDNCheckbox;
    private JComboBox<String> renderingModeCombo, certLevelCombo;
    private JComboBox<SignatureDateFormats.FormatterType> dateFormatOptions;
    private JButton chooseImageButton;
    private File selectedImageFile;
    private JPanel previewPanel;
    private AppearanceOptions appearanceOptions;
    private boolean isInitializing = false; // Flag to prevent recursive updates during initialization

    public SignatureAppearanceDialog(Frame parent) {
        super(parent, "Signature Appearance Settings", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        this.parent = parent;
    }

    public void setCertificate(X509Certificate certificate) {
        this.certificate = certificate;
    }

    public void showAppearanceConfigPrompt() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JPanel formPanel = createFormPanel();
        mainPanel.add(formPanel, BorderLayout.CENTER);

        previewPanel = new JPanel(new BorderLayout());
        TitledBorder titledBorder = BorderFactory.createTitledBorder("Live Preview");
        titledBorder.setTitleColor(Color.LIGHT_GRAY);
        titledBorder.setTitleFont(new Font("SansSerif", Font.PLAIN, 12));
        previewPanel.setBorder(titledBorder);
        previewPanel.setBackground(new Color(106, 106, 106));
        previewPanel.setPreferredSize(new Dimension(400, 220)); // Set minimum size for preview

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        JButton okButton = new JButton("OK");
        getRootPane().setDefaultButton(okButton);
        cancelButton.addActionListener(e -> dispose());
        okButton.addActionListener(this::onSubmit);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(previewPanel, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        add(mainPanel);

        isInitializing = true;
        setupListeners();
        loadPreferences(); // <-- Load saved settings
        isInitializing = false;

        // Force initial preview update
        SwingUtilities.invokeLater(this::updatePreview);

        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private JPanel createFormPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Filters
        DocumentFilter reasonFilter = TextFieldValidators.createAlphanumericFilter(25);
        DocumentFilter locationFilter = TextFieldValidators.createAlphanumericFilter(25);
        DocumentFilter customTextFilter = TextFieldValidators.createAlphanumericFilter(60);

        // Rendering Mode
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Rendering Mode:"), gbc);
        gbc.gridy++;
        renderingModeCombo = new JComboBox<>(
                Arrays.stream(RenderingMode.values()).map(RenderingMode::getLabel).toArray(String[]::new)
        );
        formPanel.add(renderingModeCombo, gbc);

        // Certification Level
        gbc.gridx = 1;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Signature Permissions:"), gbc);
        gbc.gridy++;
        certLevelCombo = new JComboBox<>(
                Arrays.stream(CertificationLevel.values()).map(CertificationLevel::getLabel).toArray(String[]::new)
        );
        formPanel.add(certLevelCombo, gbc);

        // Reason
        gbc.gridx = 0;
        gbc.gridy++;
        formPanel.add(new JLabel("Reason (max 25 chars):"), gbc);
        gbc.gridy++;
        reasonField = new JTextField(15);
        ((AbstractDocument) reasonField.getDocument()).setDocumentFilter(reasonFilter);
        formPanel.add(reasonField, gbc);

        // Location
        gbc.gridx = 1;
        gbc.gridy -= 1;
        formPanel.add(new JLabel("Location (max 25 chars):"), gbc);
        gbc.gridy++;
        locationField = new JTextField(15);
        ((AbstractDocument) locationField.getDocument()).setDocumentFilter(locationFilter);
        formPanel.add(locationField, gbc);

        // Custom Text
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        formPanel.add(new JLabel("Custom Text (max 60 chars):"), gbc);
        gbc.gridy++;
        customTextField = new JTextField(30);
        ((AbstractDocument) customTextField.getDocument()).setDocumentFilter(customTextFilter);
        formPanel.add(customTextField, gbc);
        gbc.gridwidth = 1;

        // Options Section
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JPanel checkboxPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        checkboxPanel.setBorder(BorderFactory.createTitledBorder("Options"));

        ltvCheckbox = new JCheckBox("LTV");
        timestampCheckbox = new JCheckBox("Timestamp");
        greenTickCheckbox = new JCheckBox("Green Tick");
        includeCompanyCheckbox = new JCheckBox("Include Org Name");
        includeEntireSubjectDNCheckbox = new JCheckBox("Include Subject DN");

        boolean isPersonalCert = getOrganization(certificate).equalsIgnoreCase("Personal");
        includeCompanyCheckbox.setEnabled(!isPersonalCert);
        includeCompanyCheckbox.setToolTipText(isPersonalCert
                ? "Organization name not available for personal certificates."
                : "Include organization name from certificate.");
        includeEntireSubjectDNCheckbox.setToolTipText("Include full Subject Distinguished Name (DN).");

        checkboxPanel.add(ltvCheckbox);
        checkboxPanel.add(timestampCheckbox);
        checkboxPanel.add(includeCompanyCheckbox);
        checkboxPanel.add(includeEntireSubjectDNCheckbox);
        checkboxPanel.add(greenTickCheckbox);

        formPanel.add(checkboxPanel, gbc);
        gbc.gridwidth = 1;

        // Date Format
        gbc.gridx = 0;
        gbc.gridy++;
        formPanel.add(new JLabel("Date Format:"), gbc);

        gbc.gridx = 1;
        dateFormatOptions = new JComboBox<>(SignatureDateFormats.FormatterType.values());
        dateFormatOptions.setSelectedItem(SignatureDateFormats.FormatterType.COMPACT);
        dateFormatOptions.setToolTipText("Select date format");
        formPanel.add(dateFormatOptions, gbc);

        // Graphic Image Selection
        gbc.gridx = 0;
        gbc.gridy++;
        formPanel.add(new JLabel("Graphic Image (optional):"), gbc);

        gbc.gridx = 1;
        chooseImageButton = new JButton("Choose Graphic Image");
        chooseImageButton.setEnabled(false);
        SwingUtilities.invokeLater(() -> {
            Dimension comboPref = dateFormatOptions.getPreferredSize();
            chooseImageButton.setPreferredSize(new Dimension(comboPref.width, chooseImageButton.getPreferredSize().height));
            chooseImageButton.setMaximumSize(new Dimension(comboPref.width, Short.MAX_VALUE));
        });
        formPanel.add(chooseImageButton, gbc);

        return formPanel;
    }

    private void setupListeners() {
        chooseImageButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedImageFile = chooser.getSelectedFile();
                updatePreviewAndSave();
            }
        });

        renderingModeCombo.addItemListener(e -> {
            boolean isGraphic = "Name and Graphic".equals(renderingModeCombo.getSelectedItem());
            chooseImageButton.setEnabled(isGraphic);
            greenTickCheckbox.setEnabled(!isGraphic); // disable green tick for graphic rendering
            updatePreviewAndSave();
        });

        // Add listener for certification level combo
        certLevelCombo.addItemListener(e -> updatePreviewAndSave());

        // Checkbox listeners with auto-save
        ltvCheckbox.addActionListener(e -> updatePreviewAndSave());
        timestampCheckbox.addActionListener(e -> updatePreviewAndSave());
        greenTickCheckbox.addActionListener(e -> updatePreviewAndSave());
        includeCompanyCheckbox.addActionListener(e -> updatePreviewAndSave());

        includeEntireSubjectDNCheckbox.addActionListener(e -> {
            boolean selected = includeEntireSubjectDNCheckbox.isSelected();
            includeCompanyCheckbox.setEnabled(!selected && !getOrganization(certificate).equalsIgnoreCase("Personal"));
            updatePreviewAndSave();
        });

        // Text field listeners with auto-save
        DocumentListener autoSaveListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updatePreviewAndSave();
            }

            public void removeUpdate(DocumentEvent e) {
                updatePreviewAndSave();
            }

            public void changedUpdate(DocumentEvent e) {
                updatePreviewAndSave();
            }
        };
        reasonField.getDocument().addDocumentListener(autoSaveListener);
        locationField.getDocument().addDocumentListener(autoSaveListener);
        customTextField.getDocument().addDocumentListener(autoSaveListener);

        // Date format
        dateFormatOptions.addActionListener(e -> updatePreviewAndSave());
    }

    /**
     * Helper method to update preview and save preferences in one call
     */
    private void updatePreviewAndSave() {
        updatePreview();
        if (!isInitializing) {
            savePreferences();
        }
    }

    private void loadPreferences() {
        // Load Rendering Mode
        String savedRendering = prefs.get("renderingMode", RenderingMode.NAME_AND_DESCRIPTION.getLabel());
        renderingModeCombo.setSelectedItem(savedRendering);

        // Load Certification Level
        String savedCertLevel = prefs.get("certLevel", CertificationLevel.NO_CHANGES_ALLOWED.getLabel());
        certLevelCombo.setSelectedItem(savedCertLevel);

        // Load text fields
        reasonField.setText(prefs.get("reason", ""));
        locationField.setText(prefs.get("location", ""));
        customTextField.setText(prefs.get("customText", ""));

        // Load checkboxes
        ltvCheckbox.setSelected(prefs.getBoolean("ltv", false));
        timestampCheckbox.setSelected(prefs.getBoolean("timestamp", true)); // default true
        greenTickCheckbox.setSelected(prefs.getBoolean("greenTick", false));
        includeCompanyCheckbox.setSelected(prefs.getBoolean("includeCompany", true));
        includeEntireSubjectDNCheckbox.setSelected(prefs.getBoolean("includeEntireSubject", false));

        // Load date format
        try {
            String fmtName = prefs.get("dateFormat", SignatureDateFormats.FormatterType.COMPACT.name());
            SignatureDateFormats.FormatterType fmt = SignatureDateFormats.FormatterType.valueOf(fmtName);
            dateFormatOptions.setSelectedItem(fmt);
        } catch (Exception ex) {
            log.warn("Invalid date format in prefs, using default", ex);
            dateFormatOptions.setSelectedItem(SignatureDateFormats.FormatterType.COMPACT);
        }

        // Update UI state based on loaded values
        boolean isGraphic = "Name and Graphic".equals(renderingModeCombo.getSelectedItem());
        chooseImageButton.setEnabled(isGraphic);

        boolean isPersonalCert = getOrganization(certificate).equalsIgnoreCase("Personal");
        includeCompanyCheckbox.setEnabled(!isPersonalCert && !includeEntireSubjectDNCheckbox.isSelected());
    }

    private void savePreferences() {
        try {
            prefs.put("renderingMode", (String) renderingModeCombo.getSelectedItem());
            prefs.put("certLevel", (String) certLevelCombo.getSelectedItem());
            prefs.put("reason", reasonField.getText().trim());
            prefs.put("location", locationField.getText().trim());
            prefs.put("customText", customTextField.getText().trim());

            prefs.putBoolean("ltv", ltvCheckbox.isSelected());
            prefs.putBoolean("timestamp", timestampCheckbox.isSelected());
            prefs.putBoolean("greenTick", greenTickCheckbox.isSelected());
            prefs.putBoolean("includeCompany", includeCompanyCheckbox.isSelected());
            prefs.putBoolean("includeEntireSubject", includeEntireSubjectDNCheckbox.isSelected());

            SignatureDateFormats.FormatterType fmt = (SignatureDateFormats.FormatterType) dateFormatOptions.getSelectedItem();
            prefs.put("dateFormat", fmt.name());

            // Flush to disk (optional, but ensures immediate write)
            prefs.flush();
        } catch (Exception ex) {
            log.error("Failed to save preferences", ex);
        }
    }

    private void updatePreview() {
        if (previewPanel == null) {
            return; // Not yet initialized
        }

        previewPanel.removeAll();

        String previewText = buildPreviewText();
        SignatureDateFormats.FormatterType fmtType = (SignatureDateFormats.FormatterType) dateFormatOptions.getSelectedItem();
        String time = ZonedDateTime.now().format(SignatureDateFormats.getFormatter(fmtType));

        JPanel overlayContainer = createOverlayContainer(previewText, time);

        if (greenTickCheckbox.isSelected()) {
            applyGreenTickOverlay(overlayContainer);
        }

        JPanel centerWrapper = wrapInCenterPanel(overlayContainer);
        previewPanel.setLayout(new BorderLayout());
        previewPanel.add(centerWrapper, BorderLayout.CENTER);
        previewPanel.revalidate();
        previewPanel.repaint();
    }

    /**
     * ========================== Extracted helper methods ==========================
     **/

    private String buildPreviewText() {
        StringBuilder sb = new StringBuilder();

        if (includeEntireSubjectDNCheckbox.isSelected()) {
            sb.append(X509SubjectUtils.getFullSubjectDN(certificate)).append("\n");
        } else {
            sb.append("Signed by: ").append(getCommonName(certificate)).append("\n");
            String org = getOrganization(certificate);
            if (includeCompanyCheckbox.isSelected() && org != null && !org.trim().isEmpty()) {
                sb.append("ORG: ").append(org).append("\n");
            }
        }

        sb.append("Date: ").append(ZonedDateTime.now().format(SignatureDateFormats.getFormatter(
                (SignatureDateFormats.FormatterType) dateFormatOptions.getSelectedItem()))).append("\n");

        if (!reasonField.getText().trim().isEmpty())
            sb.append("Reason: ").append(reasonField.getText().trim()).append("\n");
        if (!locationField.getText().trim().isEmpty())
            sb.append("Location: ").append(locationField.getText().trim()).append("\n");
        if (!customTextField.getText().trim().isEmpty()) sb.append(customTextField.getText().trim()).append("\n");

        return sb.toString();
    }

    private JPanel createOverlayContainer(String previewText, String time) {
        JPanel overlayContainer = new JPanel();
        overlayContainer.setLayout(new OverlayLayout(overlayContainer));
        overlayContainer.setPreferredSize(new Dimension(400, 200));
        overlayContainer.setOpaque(false);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.X_AXIS));
        contentPanel.setOpaque(false);

        JLabel leftLabel = createLeftLabel();
        JLabel rightLabel = createRightLabel(previewText);

        contentPanel.add(leftLabel);
        contentPanel.add(rightLabel);
        contentPanel.add(Box.createHorizontalGlue());

        overlayContainer.add(contentPanel);
        return overlayContainer;
    }

    private JLabel createLeftLabel() {
        String renderingMode = (String) renderingModeCombo.getSelectedItem();
        String certLevelLabel = (String) certLevelCombo.getSelectedItem();

        // Check if we should hide the left label
        boolean isNameAndDescription = "Name and Description".equals(renderingMode);
        boolean isGreenTickEnabled = greenTickCheckbox.isSelected();
        // Check if NOT_CERTIFIED (which allows editing/additional signatures)
        boolean isNotEditable = !CertificationLevel.NOT_CERTIFIED.getLabel().equals(certLevelLabel);

        if (isNameAndDescription && isGreenTickEnabled && isNotEditable) {
            // Return an empty, invisible label
            JLabel emptyLabel = new JLabel();
            emptyLabel.setPreferredSize(new Dimension(0, 0));
            emptyLabel.setMaximumSize(new Dimension(0, 0));
            emptyLabel.setMinimumSize(new Dimension(0, 0));
            emptyLabel.setOpaque(false);
            return emptyLabel;
        }

        JLabel leftLabel = new JLabel();
        leftLabel.setVerticalAlignment(SwingConstants.TOP);
        leftLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        if ("Name and Description".equals(renderingMode)) {
            leftLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
            leftLabel.setText("<html><div style='font-weight:bold; color: black;'>" +
                    getCommonName(certificate).replace(" ", "<br>") + "</div></html>");
        } else if ("Name and Graphic".equals(renderingMode) && selectedImageFile != null && selectedImageFile.exists()) {
            ImageIcon icon = new ImageIcon(selectedImageFile.getAbsolutePath());
            Image scaled = icon.getImage().getScaledInstance(80, 50, Image.SCALE_SMOOTH);
            leftLabel.setIcon(new ImageIcon(scaled));
        }

        return leftLabel;
    }

    private JLabel createRightLabel(String previewText) {
        int fontSize = computeFontSizeForPreview(previewText, previewPanel.getWidth());

        StringBuilder rightHtml = new StringBuilder("<html><div style='font-family:sans-serif;color:black;'>");
        for (String line : previewText.split("\n")) {
            rightHtml.append("<div style='font-size:").append(fontSize).append("px;'>")
                    .append(line).append("</div>");
        }
        rightHtml.append("</div></html>");

        JLabel rightLabel = new JLabel(rightHtml.toString());
        rightLabel.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
        rightLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        rightLabel.setVerticalAlignment(SwingConstants.TOP);

        return rightLabel;
    }

    private void applyGreenTickOverlay(JPanel overlayContainer) {
        try {
            ImageIcon tickIcon = new ImageIcon(Objects.requireNonNull(App.class.getResource("/icons/green_tick.png")));
            Image scaledTick = tickIcon.getImage().getScaledInstance(140, 120, Image.SCALE_SMOOTH);
            JLabel tickLabel = new JLabel(new ImageIcon(scaledTick));
            tickLabel.setAlignmentX(0.5f);
            tickLabel.setAlignmentY(0.5f);
            overlayContainer.add(tickLabel);
        } catch (Exception ex) {
            log.error("Failed to load green tick icon", ex);
        }
    }

    private JPanel wrapInCenterPanel(JPanel overlayContainer) {
        JPanel centerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        centerWrapper.setBackground(new Color(240, 240, 240));
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        centerWrapper.add(overlayContainer);
        return centerWrapper;
    }

    private int computeFontSizeForPreview(String text, int panelWidth) {
        int maxFontSize = 16;
        int minFontSize = 10;
        int length = text.length();
        int size = maxFontSize - (length / 20);
        return Math.max(minFontSize, size);
    }


    private void onSubmit(ActionEvent e) {
        if ("Name and Graphic".equals(renderingModeCombo.getSelectedItem()) && selectedImageFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a graphic image for the signature.", "Missing Image", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Final save (redundant but safe)
        savePreferences();

        String renderingLabel = (String) renderingModeCombo.getSelectedItem();
        String certLabel = (String) certLevelCombo.getSelectedItem();
        RenderingMode selectedRendering = RenderingMode.fromLabel(renderingLabel);
        CertificationLevel selectedCertLevel = CertificationLevel.fromLabel(certLabel);

        appearanceOptions = new AppearanceOptions();
        boolean isGraphicRendering = selectedRendering == RenderingMode.NAME_AND_GRAPHIC;
        appearanceOptions.setGraphicRendering(isGraphicRendering);

        int certLevel = PdfSignatureAppearance.NOT_CERTIFIED;
        switch (Objects.requireNonNull(selectedCertLevel)) {
            case NO_CHANGES_ALLOWED:
                certLevel = PdfSignatureAppearance.CERTIFIED_NO_CHANGES_ALLOWED;
                break;
            case FORM_FILLING_CERTIFIED:
                certLevel = PdfSignatureAppearance.CERTIFIED_FORM_FILLING;
                break;
            case FORM_FILLING_AND_ANNOTATION_CERTIFIED:
                certLevel = PdfSignatureAppearance.CERTIFIED_FORM_FILLING_AND_ANNOTATIONS;
                break;
        }

        appearanceOptions.setIncludeCompany(includeCompanyCheckbox.isSelected());
        appearanceOptions.setIncludeEntireSubject(includeEntireSubjectDNCheckbox.isSelected());
        appearanceOptions.setCertificationLevel(certLevel);
        appearanceOptions.setReason(reasonField.getText().trim());
        appearanceOptions.setLocation(locationField.getText().trim());
        appearanceOptions.setCustomText(customTextField.getText().trim());
        appearanceOptions.setLtvEnabled(ltvCheckbox.isSelected());
        appearanceOptions.setTimestampEnabled(timestampCheckbox.isSelected());
        appearanceOptions.setGreenTickEnabled(!isGraphicRendering &&  greenTickCheckbox.isSelected()); // Only enable green tick for text rendering not with graphic rendering
        appearanceOptions.setDateFormat((SignatureDateFormats.FormatterType) dateFormatOptions.getSelectedItem());
        appearanceOptions.setGraphicImagePath(
                selectedRendering == RenderingMode.NAME_AND_GRAPHIC && selectedImageFile != null
                        ? selectedImageFile.getAbsolutePath()
                        : null
        );

        dispose();
    }

    public AppearanceOptions getAppearanceOptions() {
        return appearanceOptions;
    }
}