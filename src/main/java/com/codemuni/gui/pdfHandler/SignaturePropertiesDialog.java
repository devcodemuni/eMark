package com.codemuni.gui.pdfHandler;

import com.codemuni.core.keyStoresProvider.X509SubjectUtils;
import com.codemuni.service.SignatureVerificationService;
import com.codemuni.service.SignatureVerificationService.SignatureVerificationResult;
import com.codemuni.service.SignatureVerificationService.VerificationStatus;
import com.codemuni.service.TrustStoreManager;
import com.codemuni.utils.UIConstants;
import com.codemuni.utils.IconLoader;
import com.codemuni.utils.CertificateUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.function.Consumer;

/**
 * COMPREHENSIVE Signature Properties Dialog (Adobe Reader DC Style)
 *
 * Features:
 * - Complete signature verification details
 * - Certificate chain tree visualization
 * - Re-verification capability
 * - Color-coded status indicators
 * - Export certificate functionality
 * - Non-scrollable horizontal layout (uses tabs)
 * - Professional UX matching Adobe Reader DC
 */
public class SignaturePropertiesDialog extends JDialog {

    // ====================================================================================
    // CONSTANTS
    // ====================================================================================

    // Color scheme
    private static final Color VALID_COLOR = UIConstants.Colors.STATUS_VALID;
    private static final Color UNKNOWN_COLOR = UIConstants.Colors.STATUS_WARNING;
    private static final Color INVALID_COLOR = UIConstants.Colors.STATUS_ERROR;
    private static final Color INFO_COLOR = UIConstants.Colors.STATUS_INFO;
    private static final Color BG_COLOR = UIConstants.Colors.BG_PRIMARY;
    private static final Color SECTION_BG = UIConstants.Colors.BG_SECONDARY;
    private static final Color PANEL_BG = new Color(45, 50, 60);
    private static final Color PANEL_BORDER = new Color(70, 75, 85);
    private static final Color CERT_CHAIN_BG = new Color(40, 44, 52);

    // Certificate chain colors
    private static final Color CERT_ROOT_COLOR = new Color(152, 195, 121);        // Green
    private static final Color CERT_INTERMEDIATE_COLOR = new Color(229, 192, 123); // Orange
    private static final Color CERT_END_ENTITY_COLOR = new Color(97, 175, 239);   // Blue

    // Layout
    private static final int LABEL_WIDTH = 180;
    private static final int LABEL_WIDTH_SMALL = 140;
    private static final int DIALOG_WIDTH = 750;
    private static final int DIALOG_HEIGHT = 850;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");

    // ====================================================================================
    // FIELDS
    // ====================================================================================

    private final SignatureVerificationResult result;
    private final Color signatureColor;
    private final TrustStoreManager trustStoreManager;

    // Optional: For re-verification
    private File pdfFile;
    private String pdfPassword;
    private Consumer<SignatureVerificationResult> onReVerify;

    // ====================================================================================
    // CONSTRUCTOR
    // ====================================================================================

    /**
     * Creates signature properties dialog.
     *
     * @param parent Parent frame
     * @param result Verification result
     * @param signatureColor Color indicator for this signature
     */
    public SignaturePropertiesDialog(Frame parent, SignatureVerificationResult result, Color signatureColor) {
        super(parent, "Signature Properties - " + result.getFieldName(), true);
        this.result = result;
        this.signatureColor = signatureColor;
        this.trustStoreManager = TrustStoreManager.getInstance();

        initializeDialog();
        buildUI();
    }

    /**
     * Sets PDF file information for re-verification feature.
     */
    public void setPdfInfo(File pdfFile, String pdfPassword, Consumer<SignatureVerificationResult> onReVerify) {
        this.pdfFile = pdfFile;
        this.pdfPassword = pdfPassword;
        this.onReVerify = onReVerify;
    }

    // ====================================================================================
    // INITIALIZATION
    // ====================================================================================

    private void initializeDialog() {
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(getParent());
        setResizable(true);
        setMinimumSize(new Dimension(700, 750));
        getContentPane().setBackground(BG_COLOR);
    }

    private void buildUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Top: Header with colored bar, icon, and status
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center: Tabbed content (prevents horizontal scrolling)
        JTabbedPane tabbedPane = createTabbedContent();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Bottom: Action buttons
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    // ====================================================================================
    // HEADER PANEL
    // ====================================================================================

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Left: Color bar + Icon
        JPanel leftSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftSection.setBackground(BG_COLOR);

        // Color indicator bar
        JPanel colorBar = new JPanel();
        colorBar.setBackground(signatureColor);
        colorBar.setPreferredSize(new Dimension(6, 70));
        leftSection.add(colorBar);

        // Status icon
        ImageIcon icon = getHeaderIcon();
        if (icon != null) {
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setBorder(new EmptyBorder(10, 12, 10, 12));
            leftSection.add(iconLabel);
        }

        panel.add(leftSection, BorderLayout.WEST);

        // Center: Signer name and status
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBackground(BG_COLOR);

        String signerName = X509SubjectUtils.extractCommonNameFromDN(result.getCertificateSubject());
        if (signerName == null || signerName.isEmpty()) {
            signerName = result.getFieldName();
        }

        JLabel nameLabel = new JLabel(signerName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel statusLabel = new JLabel(result.getStatusMessage());
        statusLabel.setFont(UIConstants.Fonts.LARGE_PLAIN);
        statusLabel.setForeground(getStatusColor(result.getOverallStatus()));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fieldLabel = new JLabel("Field: " + result.getFieldName());
        fieldLabel.setFont(UIConstants.Fonts.SMALL_PLAIN);
        fieldLabel.setForeground(UIConstants.Colors.TEXT_DISABLED);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusPanel.add(nameLabel);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        statusPanel.add(fieldLabel);

        // Add invisible badge if needed
        if (result.isInvisible()) {
            statusPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            JLabel invisibleBadge = createBadge("INVISIBLE", new Color(158, 158, 158));
            invisibleBadge.setToolTipText("This signature has no visual appearance");
            statusPanel.add(invisibleBadge);
        }

        // Add certification badge if certified
        if (result.isCertificationSignature()) {
            statusPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            JLabel certBadge = createBadge("CERTIFIED", new Color(52, 152, 219));
            certBadge.setToolTipText("This is a certification signature");
            statusPanel.add(certBadge);
        }

        panel.add(statusPanel, BorderLayout.CENTER);

        return panel;
    }

    private ImageIcon getHeaderIcon() {
        VerificationStatus status = result.getOverallStatus();
        boolean isCertified = result.isCertificationSignature();

        String iconPath;
        if (isCertified) {
            // Certified signature icon (blue certified badge)
            iconPath = "certified.png";
        } else {
            // Choose icon based on verification status
            switch (status) {
                case VALID:
                    iconPath = "shield.png"; // Green shield (valid)
                    break;
                case INVALID:
                    iconPath = "shield-invalid.png"; // Red shield (invalid)
                    break;
                case UNKNOWN:
                default:
                    iconPath = "shield-warning.png"; // Yellow shield (warning/unknown)
                    break;
            }
        }

        return IconLoader.loadIcon(iconPath, 48, 48);
    }

    private JLabel createBadge(String text, Color bgColor) {
        JLabel badge = new JLabel(text);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(bgColor);
        badge.setBorder(new EmptyBorder(3, 7, 3, 7));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        return badge;
    }

    // ====================================================================================
    // TABBED CONTENT (Prevents horizontal scrolling)
    // ====================================================================================

    private JTabbedPane createTabbedContent() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UIConstants.Fonts.NORMAL_BOLD);
        tabbedPane.setBackground(BG_COLOR);
        tabbedPane.setBorder(new EmptyBorder(0, 15, 15, 15));

        // Tab 1: Verification Status & Details
        JScrollPane overviewTab = createOverviewTab();
        tabbedPane.addTab("Overview", null, overviewTab, "Verification status and signature details");

        // Tab 2: Certificate Information
        JScrollPane certificateTab = createCertificateTab();
        tabbedPane.addTab("Certificate", null, certificateTab, "Certificate details and chain");

        // Tab 3: Advanced Info (Timestamp, LTV, Document info)
        JScrollPane advancedTab = createAdvancedTab();
        tabbedPane.addTab("Advanced", null, advancedTab, "Timestamp, LTV, and document information");

        // Reset scroll position to top when tab is changed
        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < tabbedPane.getTabCount()) {
                Component selectedComponent = tabbedPane.getComponentAt(selectedIndex);
                if (selectedComponent instanceof JScrollPane) {
                    JScrollPane scrollPane = (JScrollPane) selectedComponent;
                    SwingUtilities.invokeLater(() -> {
                        scrollPane.getVerticalScrollBar().setValue(0);
                        scrollPane.getHorizontalScrollBar().setValue(0);
                    });
                }
            }
        });

        // Set initial scroll position to top for all tabs
        SwingUtilities.invokeLater(() -> {
            overviewTab.getVerticalScrollBar().setValue(0);
            certificateTab.getVerticalScrollBar().setValue(0);
            advancedTab.getVerticalScrollBar().setValue(0);
        });

        return tabbedPane;
    }

    // ====================================================================================
    // TAB 1: OVERVIEW
    // ====================================================================================

    private JScrollPane createOverviewTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Verification Status Section
        panel.add(createVerificationStatusPanel());
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Messages Section (Errors, Warnings, Info) - Second position
        if (!result.getVerificationErrors().isEmpty()) {
            panel.add(createMessagesPanel("Errors", result.getVerificationErrors(), INVALID_COLOR));
            panel.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        if (!result.getVerificationWarnings().isEmpty()) {
            panel.add(createMessagesPanel("Warnings", result.getVerificationWarnings(), UNKNOWN_COLOR));
            panel.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        // Signature Details Section
        panel.add(createSignatureDetailsPanel());
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        return scrollPane;
    }

    /**
     * Creates verification status panel according to PDF/ISO 32000 standards.
     *
     * Standard PDF Signature Verification Checks (in order):
     * 1. Signature Valid - Cryptographic signature verification
     * 2. Document Integrity - Document not modified after signing
     * 3. Certificate Valid - Certificate was valid at signing time
     * 4. Certificate Trusted - Certificate chains to trusted root
     * 5. Revocation Status - Certificate not revoked at signing time
     * 6. Timestamp - Trusted signing time (optional, recommended)
     * 7. Long Term Validation - Embedded validation data for future verification (optional)
     */
    private JPanel createVerificationStatusPanel() {
        JPanel section = createSection("Verification Status");

        // Core verification checks - REQUIRED for valid signature
        addStatusRow(section, "Signature Valid", result.isSignatureValid());
        addStatusRowWithTooltip(section, "Document Integrity", result.isDocumentIntact(),
                "Document has not been modified after signing");

        addStatusRowWithTooltip(section, "Certificate Valid", result.isCertificateValid(),
                "Certificate was valid at signing time");

        addStatusRowWithTooltip(section, "Certificate Trusted", result.isCertificateTrusted(),
                "Certificate chains to a trusted root authority");

        // Revocation status: show VALID/REVOKED/UNKNOWN
        addRevocationStatusRow(section);

        // Optional but recommended features
        String timestampStatus = getTimestampStatusText();
        boolean timestampValid = result.isTimestampValid();
        String timestampTooltip = timestampValid ?
                "Signature has a valid trusted timestamp" :
                "Timestamp proves exact signing time (recommended by CCA)";
        addStatusRowWithTooltip(section, "Timestamp", timestampValid,
                timestampStatus, timestampTooltip);

        // LTV - Optional feature for long-term verification
        String ltvStatus = result.hasLTV() ? "Enabled" : "Not Enabled";
        String ltvTooltip = result.hasLTV() ?
                "Document contains embedded validation data (CRL/OCSP)" :
                "Long-term validation data ensures signature can be verified in the future";
        addStatusRowWithTooltip(section, "Long Term Validation", result.hasLTV(),
                ltvStatus, ltvTooltip);

        return section;
    }

    private JPanel createMessagesPanel(String title, java.util.List<String> messages, Color messageColor) {
        JPanel section = createSection(title);

        for (String message : messages) {
            addMessageRow(section, message, messageColor);
        }

        return section;
    }

    private JPanel createSignatureDetailsPanel() {
        JPanel section = createSection("Signature Details");

        if (result.getSignDate() != null) {
            addDetailRow(section, "Signed On", DATE_FORMAT.format(result.getSignDate()));
        }

        if (result.getReason() != null && !result.getReason().isEmpty()) {
            addDetailRow(section, "Reason", result.getReason());
        }

        if (result.getLocation() != null && !result.getLocation().isEmpty()) {
            addDetailRow(section, "Location", result.getLocation());
        }

        if (result.getContactInfo() != null && !result.getContactInfo().isEmpty()) {
            addDetailRow(section, "Contact", result.getContactInfo());
        }

        if (result.getSignatureAlgorithm() != null) {
            addDetailRow(section, "Algorithm", result.getSignatureAlgorithm());
        }

        // Certification info
        String certType = result.isCertificationSignature() ? "Certified Signature" : "Regular Signature";
        addDetailRow(section, "Type", certType);

        if (result.isCertificationSignature()) {
            String allowMore = result.getCertificationLevel().allowsSignatures() ? "Yes" : "No";
            addDetailRow(section, "Can Add More Signatures", allowMore);
        }

        return section;
    }

    // ====================================================================================
    // TAB 2: CERTIFICATE
    // ====================================================================================

    private JScrollPane createCertificateTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Certificate Details
        panel.add(createCertificateDetailsPanel());
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Certificate Chain
        if (result.getCertificateChain() != null && !result.getCertificateChain().isEmpty()) {
            panel.add(createCertificateChainPanel());
            panel.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        return scrollPane;
    }

    private JPanel createCertificateDetailsPanel() {
        JPanel section = createSection("Certificate Details");

        if (result.getCertificateSubject() != null) {
            addDetailRow(section, "Subject", result.getCertificateSubject());
        }

        if (result.getCertificateIssuer() != null) {
            addDetailRow(section, "Issuer", result.getCertificateIssuer());
        }

        if (result.getSignerCertificate() != null) {
            String serialHex = result.getSignerCertificate().getSerialNumber().toString(16).toUpperCase();
            addDetailRow(section, "Serial Number", serialHex);
        }

        if (result.getCertificateValidFrom() != null) {
            addDetailRow(section, "Valid From", DATE_FORMAT.format(result.getCertificateValidFrom()));
        }

        if (result.getCertificateValidTo() != null) {
            addDetailRow(section, "Valid To", DATE_FORMAT.format(result.getCertificateValidTo()));
        }

        if (result.getSignerCertificate() != null) {
            addDetailRow(section, "Version", "V" + result.getSignerCertificate().getVersion());

            String sigAlg = result.getSignerCertificate().getSigAlgName();
            String sigAlgOID = result.getSignerCertificate().getSigAlgOID();
            addDetailRow(section, "Signature Algorithm", sigAlg + " (" + sigAlgOID + ")");

            java.security.PublicKey pubKey = result.getSignerCertificate().getPublicKey();
            String keyAlgorithm = pubKey.getAlgorithm();
            int keySize = CertificateUtils.getKeySize(pubKey);
            addDetailRow(section, "Public Key", keyAlgorithm + " (" + keySize + " bits)");

            java.util.List<String> keyUsages = CertificateUtils.getKeyUsageStrings(result.getSignerCertificate());
            if (!keyUsages.isEmpty()) {
                addDetailRow(section, "Key Usage", String.join(", ", keyUsages));
            }

            java.util.List<String> extKeyUsages = CertificateUtils.getExtendedKeyUsageStrings(result.getSignerCertificate());
            if (!extKeyUsages.isEmpty()) {
                addDetailRow(section, "Extended Key Usage", String.join(", ", extKeyUsages));
            }
        }

        return section;
    }

    private JPanel createCertificateChainPanel() {
        JPanel section = createSection("Certificate Chain Hierarchy");

        int chainSize = result.getCertificateChain().size();

        // Chain count info
        JLabel countLabel = new JLabel(chainSize + " certificate" + (chainSize > 1 ? "s" : "") + " in chain");
        countLabel.setFont(UIConstants.Fonts.SMALL_PLAIN);
        countLabel.setForeground(UIConstants.Colors.TEXT_TERTIARY);
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        countLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        section.add(countLabel);

        // Tree panel
        JPanel treePanel = new JPanel();
        treePanel.setLayout(new BoxLayout(treePanel, BoxLayout.Y_AXIS));
        treePanel.setBackground(CERT_CHAIN_BG);
        treePanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        treePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Display in reverse order (root first, user cert last)
        for (int i = chainSize - 1; i >= 0; i--) {
            java.security.cert.X509Certificate cert = result.getCertificateChain().get(i);
            String subjectCN = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());

            CertificateUtils.CertificateRole certRole = CertificateUtils.determineCertificateRole(cert);
            String role = certRole.getDisplayName();

            int depth = chainSize - 1 - i;
            String treePrefix = buildTreePrefix(depth, i == 0);

            JLabel certLabel = new JLabel(treePrefix + role + ": " + subjectCN);
            certLabel.setFont(new Font("Consolas", Font.PLAIN, 13));
            certLabel.setForeground(getCertificateColorByDepth(depth, chainSize));
            certLabel.setBorder(new EmptyBorder(2, 0, 2, 0));
            certLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            treePanel.add(certLabel);
        }

        section.add(treePanel);
        section.add(Box.createRigidArea(new Dimension(0, 8)));

        return section;
    }

    // ====================================================================================
    // TAB 3: ADVANCED
    // ====================================================================================

    private JScrollPane createAdvancedTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Revocation Status Details
        panel.add(createRevocationStatusPanel());
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Timestamp Information
        panel.add(createTimestampPanel());
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        // LTV Information
        panel.add(createLTVPanel());
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Document Information
        panel.add(createDocumentInfoPanel());
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        return scrollPane;
    }

    private JPanel createRevocationStatusPanel() {
        JPanel section = createSection("Revocation Status Details");

        // Determine status
        String statusText;
        Color statusColor;
        String explanation;

        if (result.isCertificateRevoked()) {
            statusText = "REVOKED";
            statusColor = INVALID_COLOR;
            explanation = "The certificate used to sign this document has been revoked by the issuing authority. This signature is not valid.";
        } else if (isRevocationActuallyValid(result)) {
            statusText = "VALID (Verified)";
            statusColor = VALID_COLOR;
            String revStatus = result.getRevocationStatus();
            if (revStatus != null && revStatus.contains("Embedded")) {
                explanation = "Certificate revocation status was verified using validation data embedded in the PDF document.";
            } else {
                explanation = "Certificate revocation status was verified. The certificate has not been revoked.";
            }
        } else {
            statusText = "UNKNOWN";
            statusColor = UNKNOWN_COLOR;
            String revStatus = result.getRevocationStatus();
            if (revStatus != null && revStatus.contains("Validity Unknown")) {
                explanation = "Revocation status could not be determined. The certificate does not contain OCSP or CRL information, " +
                             "or the revocation service is not available. The signature may still be valid, but revocation cannot be verified.";
            } else if (revStatus != null && revStatus.equals("Not Checked")) {
                explanation = "Revocation status was not checked during this verification session.";
            } else {
                explanation = "Revocation status could not be determined. " +
                             (revStatus != null ? "Reason: " + revStatus : "No revocation information available.");
            }
        }

        addColoredDetailRow(section, "Status", statusText, statusColor);

        // Add raw revocation status detail
        String rawStatus = result.getRevocationStatus();
        if (rawStatus != null && !rawStatus.isEmpty()) {
            addDetailRow(section, "Detail", rawStatus);
        }

        // Add explanation
        addWrappedTextRow(section, "Explanation", explanation, UIConstants.Colors.TEXT_MUTED);

        // Add revocation time if certificate is revoked
        if (result.isCertificateRevoked()) {
            addDetailRow(section, "Revoked", "Yes");
        }

        return section;
    }

    private JPanel createTimestampPanel() {
        JPanel section = createSection("Timestamp Information");

        if (result.getTimestampDate() != null || result.isTimestampValid()) {
            // Timestamp Date
            if (result.getTimestampDate() != null) {
                addDetailRow(section, "Timestamp Date", DATE_FORMAT.format(result.getTimestampDate()));
            }

            // Timestamp Authority (TSA)
            String tsaName = result.getTimestampAuthority();
            if (tsaName != null && !tsaName.trim().isEmpty()) {
                String commonName = extractCommonName(tsaName);
                if (commonName != null && !commonName.equals("Unknown")) {
                    addDetailRow(section, "TSA Signer Name", commonName);
                }
                addDetailRow(section, "TSA Authority (Full DN)", tsaName);
            } else {
                // If TSA name not available, show placeholder
                addDetailRow(section, "TSA Authority", "Not available");
            }

            // Status
            String status = result.isTimestampValid() ? "Verified" : "Could not verify";
            Color statusColor = result.isTimestampValid() ? VALID_COLOR : UNKNOWN_COLOR;
            addColoredDetailRow(section, "Status", status, statusColor);

            // Add explanation
            if (result.isTimestampValid()) {
                addWrappedTextRow(section, "Info",
                    "This signature includes a trusted timestamp from a Time Stamping Authority (TSA). " +
                    "The timestamp proves when the document was signed, making the signature valid even if the certificate expires later.",
                    UIConstants.Colors.TEXT_MUTED);
            } else {
                addWrappedTextRow(section, "Info",
                    "Timestamp is present but could not be verified.",
                    UIConstants.Colors.TEXT_MUTED);
            }
        } else {
            addDetailRow(section, "Status", "Not included in signature");
            addWrappedTextRow(section, "Info",
                "This signature does not include a timestamp. The signing time is self-declared and cannot be independently verified.",
                UIConstants.Colors.TEXT_MUTED);
        }

        return section;
    }

    private JPanel createLTVPanel() {
        JPanel section = createSection("Long Term Validation");

        String ltvStatus = result.hasLTV() ? "Enabled" : "Not Enabled";
        Color ltvColor = result.hasLTV() ? VALID_COLOR : UIConstants.Colors.TEXT_MUTED;
        addColoredDetailRow(section, "Status", ltvStatus, ltvColor);

        if (result.hasLTV()) {
            addDetailRow(section, "Description",
                "This signature includes LTV data (OCSP responses and/or CRLs) embedded in the PDF. " +
                "This allows the signature to be validated even after the certificate expires or is revoked.");
        } else {
            addDetailRow(section, "Description",
                "LTV data is not embedded in this signature. The signature may not be verifiable " +
                "after the certificate expires or is revoked.");
        }

        return section;
    }

    private JPanel createDocumentInfoPanel() {
        JPanel section = createSection("Document Information");

        // Signature field name
        addDetailRow(section, "Field Name", result.getFieldName());

        // Certification level
        if (result.getCertificationLevel() != null) {
            String certLevel = result.isCertificationSignature() ?
                result.getCertificationLevel().getLabel() :
                "Not Certified (Approval Signature)";
            Color certColor = result.isCertificationSignature() ? INFO_COLOR : UIConstants.Colors.TEXT_MUTED;
            addColoredDetailRow(section, "Certification", certLevel, certColor);
        }

        // Signature algorithm
        if (result.getSignatureAlgorithm() != null && !result.getSignatureAlgorithm().isEmpty()) {
            addDetailRow(section, "Algorithm", result.getSignatureAlgorithm());
        }

        // Document revisions
        if (result.getTotalRevisions() > 0) {
            addDetailRow(section, "Signature Revision", result.getRevision() + " of " + result.getTotalRevisions());

            String coversAll = result.isCoversWholeDocument() ? "Yes" : "No";
            Color coverColor = result.isCoversWholeDocument() ? VALID_COLOR : UNKNOWN_COLOR;
            addColoredDetailRow(section, "Covers Whole Document", coversAll, coverColor);

            if (!result.isCoversWholeDocument()) {
                addWrappedTextRow(section, "Note",
                    "This signature does not cover the entire document. Changes may have been made after signing.",
                    UNKNOWN_COLOR);
            }
        }

        // Page location
        if (result.getPageNumber() > 0) {
            addDetailRow(section, "Page Location", "Page " + result.getPageNumber());
        }

        // Visibility
        String visibility = result.isInvisible() ?
            "Invisible (no visual appearance)" :
            "Visible on document";
        addDetailRow(section, "Visibility", visibility);

        // Signature date (self-declared)
        if (result.getSignDate() != null) {
            addDetailRow(section, "Signing Date (Self-declared)", DATE_FORMAT.format(result.getSignDate()));
        }

        // Reason, Location, Contact Info (if available)
        if (result.getReason() != null && !result.getReason().isEmpty()) {
            addDetailRow(section, "Reason", result.getReason());
        }

        if (result.getLocation() != null && !result.getLocation().isEmpty()) {
            addDetailRow(section, "Location", result.getLocation());
        }

        if (result.getContactInfo() != null && !result.getContactInfo().isEmpty()) {
            addDetailRow(section, "Contact Info", result.getContactInfo());
        }

        return section;
    }

    // ====================================================================================
    // BUTTON PANEL
    // ====================================================================================

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(5, 15, 10, 15));

        // Re-verify button (if PDF info available)
        if (pdfFile != null && onReVerify != null) {
            JButton reVerifyBtn = new JButton("Re-Verify");
            reVerifyBtn.setFont(UIConstants.Fonts.NORMAL_PLAIN);
            reVerifyBtn.setPreferredSize(new Dimension(120, 32));
            reVerifyBtn.setFocusPainted(false);
            reVerifyBtn.setToolTipText("Re-verify this signature");
            reVerifyBtn.addActionListener(e -> reVerifySignature());
            panel.add(reVerifyBtn);
        }

        // Export certificate button
        if (result.getSignerCertificate() != null) {
            JButton exportBtn = new JButton("Export Certificate");
            exportBtn.setFont(UIConstants.Fonts.NORMAL_PLAIN);
            exportBtn.setPreferredSize(new Dimension(150, 32));
            exportBtn.setFocusPainted(false);
            exportBtn.addActionListener(e -> exportCertificate());
            panel.add(exportBtn);
        }

        // View certificate button
        if (result.getSignerCertificate() != null) {
            JButton viewBtn = new JButton("View Certificate");
            viewBtn.setFont(UIConstants.Fonts.NORMAL_PLAIN);
            viewBtn.setPreferredSize(new Dimension(140, 32));
            viewBtn.setFocusPainted(false);
            viewBtn.addActionListener(e -> viewFullCertificate());
            panel.add(viewBtn);
        }

        // Close button
        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(UIConstants.Fonts.NORMAL_BOLD);
        closeBtn.setPreferredSize(new Dimension(100, 32));
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dispose());
        panel.add(closeBtn);

        return panel;
    }

    // ====================================================================================
    // ACTIONS
    // ====================================================================================

    private void reVerifySignature() {
        try {
            // Show progress
            JDialog progressDialog = new JDialog(this, "Re-verifying...", true);
            JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
            progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
            progressPanel.add(new JLabel("Re-verifying signature..."), BorderLayout.CENTER);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressPanel.add(progressBar, BorderLayout.SOUTH);
            progressDialog.add(progressPanel);
            progressDialog.setSize(300, 120);
            progressDialog.setLocationRelativeTo(this);

            // Re-verify in background
            SwingWorker<SignatureVerificationResult, Void> worker = new SwingWorker<SignatureVerificationResult, Void>() {
                @Override
                protected SignatureVerificationResult doInBackground() throws Exception {
                    SignatureVerificationService service = new SignatureVerificationService();
                    java.util.List<SignatureVerificationResult> results =
                        service.verifySignatures(pdfFile, pdfPassword);

                    // Find our signature
                    for (SignatureVerificationResult r : results) {
                        if (r.getFieldName().equals(result.getFieldName())) {
                            return r;
                        }
                    }
                    return null;
                }

                @Override
                protected void done() {
                    progressDialog.dispose();
                    try {
                        SignatureVerificationResult newResult = get();
                        if (newResult != null && onReVerify != null) {
                            onReVerify.accept(newResult);
                            dispose(); // Close dialog

                            // Show new dialog with updated result
                            SignaturePropertiesDialog newDialog = new SignaturePropertiesDialog(
                                (Frame) getParent(), newResult, signatureColor);
                            newDialog.setPdfInfo(pdfFile, pdfPassword, onReVerify);
                            newDialog.setVisible(true);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(SignaturePropertiesDialog.this,
                            "Re-verification failed:\n" + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            };

            worker.execute();
            progressDialog.setVisible(true);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to re-verify signature:\n" + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportCertificate() {
        try {
            java.security.cert.X509Certificate cert = result.getSignerCertificate();
            if (cert == null) {
                JOptionPane.showMessageDialog(this,
                    "No certificate available to export",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Certificate");

            String cn = X509SubjectUtils.extractCommonNameFromDN(cert.getSubjectDN().toString());
            String defaultName = cn.replaceAll("[^a-zA-Z0-9]", "_") + "_certificate.pem";
            chooser.setSelectedFile(new File(defaultName));

            chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PEM Certificate (*.pem)", "pem"));
            chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("DER Certificate (*.cer, *.der)", "cer", "der"));
            chooser.setFileFilter(chooser.getChoosableFileFilters()[0]);

            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File outputFile = chooser.getSelectedFile();
                boolean isPEM = chooser.getFileFilter().getDescription().contains("PEM");

                if (isPEM && !outputFile.getName().toLowerCase().endsWith(".pem")) {
                    outputFile = new File(outputFile.getAbsolutePath() + ".pem");
                } else if (!isPEM && !outputFile.getName().toLowerCase().matches(".*\\.(cer|der)$")) {
                    outputFile = new File(outputFile.getAbsolutePath() + ".cer");
                }

                if (isPEM) {
                    String pemCert = "-----BEGIN CERTIFICATE-----\n" +
                                   java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                                       .encodeToString(cert.getEncoded()) +
                                   "\n-----END CERTIFICATE-----\n";
                    java.nio.file.Files.write(outputFile.toPath(), pemCert.getBytes());
                } else {
                    java.nio.file.Files.write(outputFile.toPath(), cert.getEncoded());
                }

                JOptionPane.showMessageDialog(this,
                    "Certificate exported successfully to:\n" + outputFile.getAbsolutePath(),
                    "Export Success",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to export certificate:\n" + ex.getMessage(),
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void viewFullCertificate() {
        try {
            java.security.cert.X509Certificate cert = result.getSignerCertificate();
            if (cert == null) {
                return;
            }

            JDialog certDialog = new JDialog(this, "Certificate Details", true);
            certDialog.setSize(800, 600);
            certDialog.setLocationRelativeTo(this);

            JTextArea textArea = new JTextArea();
            textArea.setFont(UIConstants.Fonts.MONOSPACE);
            textArea.setEditable(false);
            textArea.setBackground(new Color(30, 30, 30));
            textArea.setForeground(UIConstants.Colors.TEXT_PRIMARY);
            textArea.setCaretColor(Color.WHITE);

            StringBuilder details = new StringBuilder();
            details.append("X.509 Certificate Details\n");
            details.append(repeatChar('=', 80)).append("\n\n");
            details.append("Version: ").append(cert.getVersion()).append("\n");
            details.append("Serial Number: ").append(cert.getSerialNumber().toString(16).toUpperCase()).append("\n\n");
            details.append("Subject:\n  ").append(cert.getSubjectDN().toString()).append("\n\n");
            details.append("Issuer:\n  ").append(cert.getIssuerDN().toString()).append("\n\n");
            details.append("Valid From: ").append(DATE_FORMAT.format(cert.getNotBefore())).append("\n");
            details.append("Valid To: ").append(DATE_FORMAT.format(cert.getNotAfter())).append("\n\n");
            details.append("Signature Algorithm: ").append(cert.getSigAlgName()).append("\n");
            details.append("Signature Algorithm OID: ").append(cert.getSigAlgOID()).append("\n\n");
            details.append("Public Key Algorithm: ").append(cert.getPublicKey().getAlgorithm()).append("\n");
            details.append("Public Key Size: ").append(CertificateUtils.getKeySize(cert.getPublicKey())).append(" bits\n\n");

            java.util.List<String> keyUsages = CertificateUtils.getKeyUsageStrings(cert);
            if (!keyUsages.isEmpty()) {
                details.append("Key Usage: ").append(String.join(", ", keyUsages)).append("\n");
            }

            java.util.List<String> extKeyUsages = CertificateUtils.getExtendedKeyUsageStrings(cert);
            if (!extKeyUsages.isEmpty()) {
                details.append("Extended Key Usage: ").append(String.join(", ", extKeyUsages)).append("\n");
            }

            details.append("\n").append(repeatChar('-', 80)).append("\n");
            details.append("PEM Encoded:\n").append(repeatChar('-', 80)).append("\n\n");
            details.append("-----BEGIN CERTIFICATE-----\n");
            details.append(java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(cert.getEncoded()));
            details.append("\n-----END CERTIFICATE-----\n");

            textArea.setText(details.toString());
            textArea.setCaretPosition(0);

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setBorder(UIConstants.Padding.SMALL);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBackground(BG_COLOR);
            JButton copyBtn = new JButton("Copy to Clipboard");
            copyBtn.addActionListener(e -> {
                java.awt.datatransfer.StringSelection selection =
                    new java.awt.datatransfer.StringSelection(textArea.getText());
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                JOptionPane.showMessageDialog(certDialog, "Copied to clipboard", "Copied", JOptionPane.INFORMATION_MESSAGE);
            });
            JButton closeBtn = new JButton("Close");
            closeBtn.addActionListener(e -> certDialog.dispose());
            buttonPanel.add(copyBtn);
            buttonPanel.add(closeBtn);

            certDialog.setLayout(new BorderLayout());
            certDialog.add(scrollPane, BorderLayout.CENTER);
            certDialog.add(buttonPanel, BorderLayout.SOUTH);

            certDialog.setVisible(true);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to view certificate:\n" + ex.getMessage(),
                "View Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    // ====================================================================================
    // HELPER METHODS - UI COMPONENTS
    // ====================================================================================

    private JPanel createSection(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(SECTION_BG);
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.Colors.BORDER_SECONDARY, 1),
            new EmptyBorder(14, 16, 14, 16)
        ));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIConstants.Fonts.LARGE_BOLD);
        titleLabel.setForeground(UIConstants.Colors.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        section.add(titleLabel);

        return section;
    }

    private void addStatusRow(JPanel panel, String label, boolean status) {
        addStatusRow(panel, label, status, null);
    }

    private void addStatusRowWithTooltip(JPanel panel, String label, boolean status, String tooltip) {
        addStatusRowWithTooltip(panel, label, status, null, tooltip);
    }

    private void addStatusRowWithTooltip(JPanel panel, String label, boolean status, String customText, String tooltip) {
        // Use existing addStatusRow method
        addStatusRow(panel, label, status, customText);

        // Add tooltip to the last added row if tooltip is provided
        if (tooltip != null && !tooltip.isEmpty() && panel.getComponentCount() >= 2) {
            Component lastRow = panel.getComponent(panel.getComponentCount() - 2); // -2 because rigid area is added after
            if (lastRow instanceof JPanel) {
                ((JPanel) lastRow).setToolTipText(tooltip);
                // Also set tooltip on child components for better UX
                for (Component child : ((JPanel) lastRow).getComponents()) {
                    if (child instanceof JComponent) {
                        ((JComponent) child).setToolTipText(tooltip);
                    }
                }
            }
        }
    }

    private void addStatusRow(JPanel panel, String label, boolean status, String customText) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(SECTION_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 12);

        JLabel labelComp = new JLabel(label + ":");
        labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        labelComp.setForeground(UIConstants.Colors.TEXT_SECONDARY);
        labelComp.setPreferredSize(new Dimension(LABEL_WIDTH, labelComp.getPreferredSize().height));

        gbc.gridx = 0;
        gbc.weightx = 0;
        row.add(labelComp, gbc);

        // Status icon + text
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusPanel.setOpaque(false);

        String statusText;
        Color statusColor;

        if (customText != null && !customText.isEmpty()) {
            statusText = customText;
            // Map status text to colors consistently with VerificationStatus enum:
            // - VALID (green): Valid certificates, successful CRL checks, enabled features
            // - INVALID (red): Revoked certificates, failed checks
            // - UNKNOWN (yellow): Cannot verify, not checked, unknown status
            //
            // IMPORTANT: Check for "Validity Unknown" and "Not Checked" first before checking "Valid"
            if (customText.equals("Not Checked") || customText.contains("Validity Unknown") || customText.contains("Unknown")) {
                statusColor = UNKNOWN_COLOR;
            } else if (customText.contains("Revoked")) {
                statusColor = INVALID_COLOR;
            } else if (customText.contains("Valid") || customText.contains("CRL") || customText.equals("Enabled")) {
                statusColor = VALID_COLOR;
            } else {
                // All other statuses are UNKNOWN
                statusColor = UNKNOWN_COLOR;
            }
        } else {
            statusText = status ? "Valid" : "Invalid";
            statusColor = status ? VALID_COLOR : INVALID_COLOR;
        }

        // Show green tick only if status is true AND it's not "Validity Unknown" or "Not Checked"
        boolean showGreenTick = status &&
                                (customText == null ||
                                 (customText.contains("Valid") && !customText.contains("Unknown") && !customText.equals("Not Checked")) ||
                                 customText.contains("CRL"));
        if (showGreenTick) {
            ImageIcon icon = IconLoader.loadIcon("green_tick.png", 14, 14);
            if (icon != null) {
                statusPanel.add(new JLabel(icon));
            }
        }

        JLabel valueComp = new JLabel(statusText);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, 13));
        valueComp.setForeground(statusColor);
        statusPanel.add(valueComp);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row.add(statusPanel, gbc);

        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
    }

    /**
     * Adds revocation status row with VALID/REVOKED/UNKNOWN display.
     */
    private void addRevocationStatusRow(JPanel panel) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(SECTION_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 12);

        // Label
        JLabel labelComp = new JLabel("Revocation status:");
        labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        labelComp.setForeground(UIConstants.Colors.TEXT_SECONDARY);
        labelComp.setPreferredSize(new Dimension(LABEL_WIDTH, labelComp.getPreferredSize().height));
        gbc.gridx = 0;
        gbc.weightx = 0;
        row.add(labelComp, gbc);

        // Determine revocation status
        String statusText;
        Color statusColor;
        boolean showTick = false;
        String tooltip;

        if (result.isCertificateRevoked()) {
            statusText = "REVOKED";
            statusColor = INVALID_COLOR;
            tooltip = "Certificate has been revoked - signature is not valid";
        } else if (isRevocationActuallyValid(result)) {
            statusText = "VALID";
            statusColor = VALID_COLOR;
            showTick = true;
            tooltip = "Certificate revocation verified - not revoked";
        } else {
            statusText = "UNKNOWN";
            statusColor = UNKNOWN_COLOR;
            String revStatus = result.getRevocationStatus();
            if (revStatus != null && !revStatus.isEmpty()) {
                tooltip = "Revocation status: " + revStatus;
            } else {
                tooltip = "Revocation status could not be determined";
            }
        }

        // Status panel with icon + text
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusPanel.setOpaque(false);

        if (showTick) {
            ImageIcon icon = IconLoader.loadIcon("green_tick.png", 14, 14);
            if (icon != null) {
                statusPanel.add(new JLabel(icon));
            }
        }

        JLabel valueComp = new JLabel(statusText);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, 13));
        valueComp.setForeground(statusColor);
        statusPanel.add(valueComp);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row.add(statusPanel, gbc);

        // Add tooltip
        row.setToolTipText(tooltip);
        labelComp.setToolTipText(tooltip);
        statusPanel.setToolTipText(tooltip);
        valueComp.setToolTipText(tooltip);

        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
    }

    private void addDetailRow(JPanel panel, String label, String value) {
        addDetailRow(panel, label, value, UIConstants.Colors.TEXT_MUTED);
    }

    private void addColoredDetailRow(JPanel panel, String label, String value, Color valueColor) {
        addDetailRow(panel, label, value, valueColor);
    }

    private void addDetailRow(JPanel panel, String label, String value, Color valueColor) {
        if (value == null || value.isEmpty()) {
            return;
        }

        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(SECTION_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(2, 0, 2, 12);

        JLabel labelComp = new JLabel(label + ":");
        labelComp.setFont(UIConstants.Fonts.NORMAL_BOLD);
        labelComp.setForeground(UIConstants.Colors.TEXT_SECONDARY);
        labelComp.setPreferredSize(new Dimension(LABEL_WIDTH, labelComp.getPreferredSize().height));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        row.add(labelComp, gbc);

        // Create text area with proper wrapping
        JTextArea valueComp = new JTextArea(value);
        valueComp.setFont(UIConstants.Fonts.NORMAL_PLAIN);
        valueComp.setForeground(valueColor);
        valueComp.setBackground(SECTION_BG);
        valueComp.setLineWrap(true);
        valueComp.setWrapStyleWord(true);
        valueComp.setEditable(false);
        valueComp.setBorder(null);

        // Calculate appropriate rows for text area based on content length
        int estimatedRows = Math.max(1, value.length() / 60);
        valueComp.setRows(Math.min(estimatedRows, 10));

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row.add(valueComp, gbc);

        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void addMessageRow(JPanel panel, String message, Color color) {
        JTextArea textArea = new JTextArea("• " + message);
        textArea.setFont(UIConstants.Fonts.SMALL_PLAIN);
        textArea.setForeground(color);
        textArea.setBackground(SECTION_BG);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setBorder(new EmptyBorder(2, 0, 2, 0));
        textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        textArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        panel.add(textArea);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    /**
     * Adds a row with wrapped text (for long explanations).
     */
    private void addWrappedTextRow(JPanel panel, String label, String text, Color textColor) {
        if (text == null || text.isEmpty()) {
            return;
        }

        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(SECTION_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(2, 0, 2, 12);

        // Label
        JLabel labelComp = new JLabel(label + ":");
        labelComp.setFont(UIConstants.Fonts.NORMAL_BOLD);
        labelComp.setForeground(UIConstants.Colors.TEXT_SECONDARY);
        labelComp.setPreferredSize(new Dimension(LABEL_WIDTH, labelComp.getPreferredSize().height));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        row.add(labelComp, gbc);

        // Wrapped text area
        JTextArea textArea = new JTextArea(text);
        textArea.setFont(UIConstants.Fonts.SMALL_PLAIN);
        textArea.setForeground(textColor);
        textArea.setBackground(SECTION_BG);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setBorder(null);
        textArea.setRows(3); // Minimum rows

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row.add(textArea, gbc);

        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    /**
     * Extracts Common Name from Distinguished Name.
     */
    private String extractCommonName(String dn) {
        if (dn == null || dn.trim().isEmpty()) {
            return "Unknown";
        }

        // Try to extract CN= field
        String[] parts = dn.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.toUpperCase().startsWith("CN=")) {
                return part.substring(3).trim();
            }
        }

        // If no CN found, return the original DN
        return dn.trim();
    }

    // ====================================================================================
    // HELPER METHODS - CERTIFICATE CHAIN
    // ====================================================================================

    private String buildTreePrefix(int depth, boolean isLast) {
        if (depth == 0) {
            return "[ROOT] ";
        }

        StringBuilder prefix = new StringBuilder();
        for (int d = 1; d < depth; d++) {
            prefix.append("│   ");
        }

        if (isLast) {
            prefix.append("└── ");
        } else {
            prefix.append("├── ");
        }

        return prefix.toString();
    }

    private Color getCertificateColorByDepth(int depth, int totalDepth) {
        if (depth == 0) {
            return CERT_ROOT_COLOR;
        } else if (depth == totalDepth - 1) {
            return CERT_END_ENTITY_COLOR;
        } else {
            return CERT_INTERMEDIATE_COLOR;
        }
    }

    // ====================================================================================
    // HELPER METHODS - STATUS & TEXT
    // ====================================================================================

    /**
     * Determines if revocation status is actually valid (verified as not revoked).
     * Only returns true if revocation was ACTUALLY CHECKED and certificate is valid.
     * Returns false for "Not Checked", "Validity Unknown", etc.
     *
     * @param result Verification result
     * @return true only if revocation was verified and certificate is not revoked
     */
    private boolean isRevocationActuallyValid(SignatureVerificationResult result) {
        // Certificate is revoked - definitely not valid
        if (result.isCertificateRevoked()) {
            return false;
        }

        // Certificate is NOT revoked, but was revocation actually checked?
        String revocationStatus = result.getRevocationStatus();
        if (revocationStatus == null || revocationStatus.isEmpty()) {
            return false; // No status = not checked
        }

        // Only return true if status explicitly contains "Valid"
        // This includes:
        // - "Valid (Embedded OCSP)"
        // - "Valid (Embedded CRL)"
        // - "Valid (Live OCSP)"
        // - "Valid (Revoked after signing, has timestamp)"
        //
        // This excludes:
        // - "Not Checked"
        // - "Validity Unknown"
        // - "Validity Unknown (Network Error)"
        // - "Validity Unknown (Check Failed)"
        return revocationStatus.contains("Valid");
    }

    private String getTimestampStatusText() {
        if (result.isTimestampValid()) {
            return "Valid";
        } else if (result.getTimestampDate() != null) {
            return "Invalid";
        } else {
            return "Not Enabled";
        }
    }

    private Color getStatusColor(VerificationStatus status) {
        switch (status) {
            case VALID:
                return VALID_COLOR;
            case UNKNOWN:
                return UNKNOWN_COLOR;
            case INVALID:
                return INVALID_COLOR;
            default:
                return UIConstants.Colors.TEXT_DISABLED;
        }
    }

    /**
     * Repeats a character n times (Java 8 compatible).
     * Replaces String.repeat() which is only available in Java 11+.
     */
    private String repeatChar(char ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }
}
