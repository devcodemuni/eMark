package com.codemuni.gui.pdfHandler;

import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.gui.DialogUtils;
import com.codemuni.gui.PasswordDialog;
import com.codemuni.service.SignatureFieldDetectionService;
import com.codemuni.service.SignatureFieldDetectionService.SignatureFieldInfo;
import com.itextpdf.text.pdf.PdfReader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsibilities:
 * - Load & close PDDocument
 * - Handle password attempts via PasswordDialog
 * - Render each page as an ImageIcon + JLabel
 * - Register rectangle drawing via SignModeController
 */
public class PdfRendererService {
    // Rendering constants (kept same as original)
    public static final int RENDER_DPI = 100;
    public static final int DEFAULT_RENDERER_PADDING = 10;
    private static final Log log = LogFactory.getLog(PdfRendererService.class);
    private final PdfViewerMain owner;
    private final JPanel pdfPanel;

    private PDDocument document;
    private File currentFile;
    private String pdfPassword;

    // Signature field support
    private final SignatureFieldDetectionService fieldDetectionService;
    private List<SignatureFieldInfo> unsignedSignatureFields;
    private List<SignatureFieldOverlay> fieldOverlays;
    private boolean showSignatureFieldsOverlay = false;

    // Signed signature overlay support
    private List<SignedSignatureOverlay> signedSignatureOverlays;

    public PdfRendererService(PdfViewerMain owner) {
        this.owner = owner;
        pdfPanel = new JPanel();
        pdfPanel.setLayout(new BoxLayout(pdfPanel, BoxLayout.Y_AXIS));
        pdfPanel.setFocusable(true);

        this.fieldDetectionService = new SignatureFieldDetectionService();
        this.unsignedSignatureFields = new ArrayList<>();
        this.fieldOverlays = new ArrayList<>();
        this.signedSignatureOverlays = new ArrayList<>();
    }

    public JPanel getPdfPanel() {
        return pdfPanel;
    }

    public int getPageCountSafe() {
        try {
            return (document == null) ? 0 : document.getNumberOfPages();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean render(File file) {
        pdfPanel.removeAll();
        try {
            close(); // close if already open
            document = tryLoadDocument(file);
            if (document == null) return false;

            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
            }

            currentFile = file;
            // Store password for later use (signature field detection, signing)
            if (owner.getPdfPassword() != null) {
                this.pdfPassword = owner.getPdfPassword();
            }
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                // Performance: Render with optimized image type for better memory usage
                BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI);

                // Performance: Optimize image if it's large
                BufferedImage optimizedImage = optimizeImageForDisplay(image);

                JPanel pageWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                pageWrapper.setOpaque(false);

                // Performance: Use custom JLabel with double buffering
                JLabel pageLabel = new JLabel(new ImageIcon(optimizedImage)) {
                    @Override
                    public boolean isDoubleBuffered() {
                        return true;
                    }
                };

                pageLabel.setBorder(BorderFactory.createEmptyBorder(
                        DEFAULT_RENDERER_PADDING,
                        DEFAULT_RENDERER_PADDING,
                        DEFAULT_RENDERER_PADDING,
                        DEFAULT_RENDERER_PADDING
                ));

                // Drawing is attached by SignModeController when sign mode is enabled.
                // But we expose a helper so the controller can attach listeners anytime.
                pageWrapper.add(pageLabel);
                pdfPanel.add(pageWrapper);
            }

            pdfPanel.revalidate();
            pdfPanel.repaint();

            // Detect signature fields after rendering
            detectUnsignedSignatureFields();

            // Automatically show overlay if unsigned signature fields exist (without sign mode)
            if (hasUnsignedSignatureFields()) {
                log.info("Unsigned signature fields detected - showing automatic overlay");
                showSignatureFieldOverlaysAutomatic();
            }

            return true;

        } catch (UserCancelledPasswordEntryException ex) {
            log.info("User cancelled password entry.");
        } catch (Exception ex) {
            log.error("Error rendering PDF", ex);
            DialogUtils.showExceptionDialog(owner, "Unable to Display PDF Preview, Please try again.", ex);
        }
        return false;
    }

    public PDDocument getDocument() {
        return document;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public String getPdfPassword() {
        return pdfPassword;
    }

    public void setPdfPassword(String pdfPassword) {
        this.pdfPassword = pdfPassword;
    }

    /**
     * Detects unsigned signature fields in the current PDF and returns them.
     *
     * @return List of unsigned signature fields
     */
    public List<SignatureFieldInfo> detectUnsignedSignatureFields() {
        unsignedSignatureFields.clear();

        if (currentFile == null) {
            log.warn("No PDF file loaded. Cannot detect signature fields.");
            return unsignedSignatureFields;
        }

        try {
            PdfReader reader = pdfPassword != null && !pdfPassword.isEmpty()
                    ? new PdfReader(currentFile.getAbsolutePath(), pdfPassword.getBytes())
                    : new PdfReader(currentFile.getAbsolutePath());

            unsignedSignatureFields = fieldDetectionService.detectUnsignedSignatureFields(reader);
            reader.close();

            log.info("Detected " + unsignedSignatureFields.size() + " unsigned signature fields");
            return unsignedSignatureFields;

        } catch (Exception e) {
            log.error("Error detecting signature fields", e);
            return unsignedSignatureFields;
        }
    }

    /**
     * Shows visual overlays automatically when PDF loads (no click listener needed initially).
     * Overlays are interactive and will trigger sign mode when clicked.
     */
    public void showSignatureFieldOverlaysAutomatic() {
        showSignatureFieldOverlays(field -> {
            // Auto-trigger sign mode when field is clicked
            log.info("User clicked on unsigned signature field: " + field.getFieldName());
            // Delegate to PdfViewerMain to handle sign mode
            if (owner != null) {
                owner.triggerSignModeForField(field);
            }
        });
    }

    /**
     * Shows visual overlays highlighting unsigned signature fields on the PDF.
     *
     * @param clickListener Listener for field click events
     */
    public void showSignatureFieldOverlays(SignatureFieldOverlay.FieldClickListener clickListener) {
        if (unsignedSignatureFields.isEmpty()) {
            detectUnsignedSignatureFields();
        }

        if (unsignedSignatureFields.isEmpty()) {
            log.info("No unsigned signature fields to display");
            return;
        }

        hideSignatureFieldOverlays(); // Clear existing overlays

        float scale = RENDER_DPI / 72f;
        int totalPages = getPageCountSafe();

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            Component wrapper = pdfPanel.getComponent(pageIndex);
            if (wrapper instanceof JPanel) {
                JPanel pageWrapper = (JPanel) wrapper;
                JLabel pageLabel = findPageLabel(pageWrapper);

                if (pageLabel != null) {
                    int pageNumber = pageIndex + 1; // 1-based

                    // Get actual rendered image dimensions
                    Icon icon = pageLabel.getIcon();
                    if (icon == null) {
                        log.warn("Page " + pageNumber + " has no icon, skipping overlay");
                        continue;
                    }

                    int imageWidth = icon.getIconWidth();
                    int imageHeight = icon.getIconHeight();

                    // Create overlay with actual page dimensions
                    SignatureFieldOverlay overlay = new SignatureFieldOverlay(
                            pageNumber,
                            scale,
                            unsignedSignatureFields,
                            clickListener,
                            imageWidth,   // Pass actual image width
                            imageHeight   // Pass actual image height
                    );

                    if (overlay.hasFields()) {
                        // Calculate total size including padding
                        Dimension totalSize = new Dimension(
                                imageWidth + 2 * DEFAULT_RENDERER_PADDING,
                                imageHeight + 2 * DEFAULT_RENDERER_PADDING
                        );

                        // Create layered pane for proper z-ordering
                        JLayeredPane layeredPane = new JLayeredPane();
                        layeredPane.setPreferredSize(totalSize);
                        layeredPane.setSize(totalSize);

                        // Set bounds for both components (same size, same position)
                        pageLabel.setBounds(0, 0, totalSize.width, totalSize.height);
                        overlay.setBounds(0, 0, totalSize.width, totalSize.height);

                        // Add to layered pane: page label at bottom, overlay on top
                        layeredPane.add(pageLabel, Integer.valueOf(JLayeredPane.DEFAULT_LAYER));
                        layeredPane.add(overlay, Integer.valueOf(JLayeredPane.PALETTE_LAYER));

                        // Make sure overlay is visible
                        overlay.setVisible(true);
                        overlay.revalidate();

                        // Replace the page label with the layered pane in the wrapper
                        pageWrapper.removeAll();
                        pageWrapper.add(layeredPane);

                        fieldOverlays.add(overlay);

                        log.info("Added signature field overlay to page " + pageNumber +
                                " (" + overlay.getFieldCount() + " fields)");
                    }
                }
            }
        }

        showSignatureFieldsOverlay = true;
        pdfPanel.revalidate();
        pdfPanel.repaint();

        log.info("Signature field overlays displayed");
    }

    /**
     * Hides all signature field overlays and restores original layout.
     */
    public void hideSignatureFieldOverlays() {
        if (!showSignatureFieldsOverlay) {
            return; // Already hidden
        }

        // Cleanup all overlay timers to prevent memory leaks
        for (SignatureFieldOverlay overlay : fieldOverlays) {
            overlay.cleanup();
        }

        // Restore original page wrapper layout
        int totalPages = getPageCountSafe();
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            Component wrapper = pdfPanel.getComponent(pageIndex);
            if (wrapper instanceof JPanel) {
                JPanel pageWrapper = (JPanel) wrapper;

                // Find the layered pane (if it exists)
                for (Component comp : pageWrapper.getComponents()) {
                    if (comp instanceof JLayeredPane) {
                        JLayeredPane layeredPane = (JLayeredPane) comp;

                        // Extract the original page label from the layered pane
                        Component[] components = layeredPane.getComponents();
                        JLabel pageLabel = null;
                        for (Component c : components) {
                            if (c instanceof JLabel) {
                                pageLabel = (JLabel) c;
                                break;
                            }
                        }

                        if (pageLabel != null) {
                            // Restore original layout: FlowLayout with page label
                            pageWrapper.removeAll();
                            pageWrapper.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
                            pageWrapper.add(pageLabel);
                        }
                        break;
                    }
                }
            }
        }

        fieldOverlays.clear();
        showSignatureFieldsOverlay = false;

        pdfPanel.revalidate();
        pdfPanel.repaint();

        log.info("Signature field overlays hidden");
    }

    /**
     * Finds the JLabel containing the PDF page image within a wrapper panel.
     */
    private JLabel findPageLabel(JPanel pageWrapper) {
        for (Component c : pageWrapper.getComponents()) {
            if (c instanceof JLabel) {
                return (JLabel) c;
            }
        }
        return null;
    }

    /**
     * Returns true if the PDF has unsigned signature fields.
     */
    public boolean hasUnsignedSignatureFields() {
        return !unsignedSignatureFields.isEmpty();
    }

    /**
     * Returns the list of detected unsigned signature fields.
     */
    public List<SignatureFieldInfo> getUnsignedSignatureFields() {
        return new ArrayList<>(unsignedSignatureFields);
    }

    /**
     * Shows colored rectangle overlays on PDF pages to highlight signed signature locations.
     * Each signature gets a unique color from the color manager.
     *
     * @param results All signature verification results
     * @param colorManager Color manager for signature colors
     * @param scrollPane Scroll pane for auto-scroll functionality
     */
    public void showSignedSignatureOverlays(
            List<com.codemuni.service.SignatureVerificationService.SignatureVerificationResult> results,
            SignatureColorManager colorManager,
            PdfScrollPane scrollPane) {

        if (results == null || results.isEmpty()) {
            log.info("No signed signatures to display");
            return;
        }

        // Clear any existing overlays
        hideSignedSignatureOverlays();

        float scale = RENDER_DPI / 72f;
        int totalPages = getPageCountSafe();

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            Component wrapper = pdfPanel.getComponent(pageIndex);
            if (wrapper instanceof JPanel) {
                JPanel pageWrapper = (JPanel) wrapper;

                // Find the page label or layered pane
                Component pageComponent = findPageComponent(pageWrapper);
                if (pageComponent == null) {
                    continue;
                }

                int pageNumber = pageIndex + 1; // 1-based

                // Get image dimensions
                int imageWidth, imageHeight;
                if (pageComponent instanceof JLabel) {
                    Icon icon = ((JLabel) pageComponent).getIcon();
                    if (icon == null) continue;
                    imageWidth = icon.getIconWidth();
                    imageHeight = icon.getIconHeight();
                } else if (pageComponent instanceof JLayeredPane) {
                    imageWidth = pageComponent.getWidth();
                    imageHeight = pageComponent.getHeight();
                } else {
                    continue;
                }

                // Create overlay for this page (with scrollPane parameter)
                SignedSignatureOverlay overlay = new SignedSignatureOverlay(
                        pageNumber,
                        scale,
                        results,
                        colorManager,
                        imageWidth,
                        imageHeight,
                        scrollPane  // NEW PARAMETER
                );

                if (overlay.hasSignatures()) {
                    // Calculate total size including padding
                    Dimension totalSize = new Dimension(
                            imageWidth + 2 * DEFAULT_RENDERER_PADDING,
                            imageHeight + 2 * DEFAULT_RENDERER_PADDING
                    );

                    // If there's already a layered pane (from unsigned field overlay), add to it
                    if (pageComponent instanceof JLayeredPane) {
                        JLayeredPane layeredPane = (JLayeredPane) pageComponent;
                        overlay.setBounds(0, 0, totalSize.width, totalSize.height);
                        layeredPane.add(overlay, Integer.valueOf(JLayeredPane.MODAL_LAYER));
                    } else {
                        // Create new layered pane
                        JLayeredPane layeredPane = new JLayeredPane();
                        layeredPane.setPreferredSize(totalSize);
                        layeredPane.setSize(totalSize);

                        // Set bounds for both components
                        pageComponent.setBounds(0, 0, totalSize.width, totalSize.height);
                        overlay.setBounds(0, 0, totalSize.width, totalSize.height);

                        // Add to layered pane
                        layeredPane.add(pageComponent, Integer.valueOf(JLayeredPane.DEFAULT_LAYER));
                        layeredPane.add(overlay, Integer.valueOf(JLayeredPane.MODAL_LAYER));

                        // Replace in wrapper
                        pageWrapper.removeAll();
                        pageWrapper.add(layeredPane);
                    }

                    signedSignatureOverlays.add(overlay);

                    log.info("Added signed signature overlay to page " + pageNumber +
                            " (" + overlay.getSignatureCount() + " signatures)");
                }
            }
        }

        pdfPanel.revalidate();
        pdfPanel.repaint();

        log.info("Signed signature overlays displayed");
    }

    /**
     * Gets signed signature overlay for highlighting (called from panel).
     */
    public void highlightSignatureOnOverlay(String fieldName) {
        for (SignedSignatureOverlay overlay : signedSignatureOverlays) {
            overlay.highlightSignature(fieldName);
        }
    }

    /**
     * Hides all signed signature overlays.
     */
    public void hideSignedSignatureOverlays() {
        if (signedSignatureOverlays.isEmpty()) {
            return;
        }

        // Remove overlays from their parents
        for (SignedSignatureOverlay overlay : signedSignatureOverlays) {
            Container parent = overlay.getParent();
            if (parent != null) {
                parent.remove(overlay);
                parent.revalidate();
                parent.repaint();
            }
        }

        signedSignatureOverlays.clear();

        pdfPanel.revalidate();
        pdfPanel.repaint();

        log.info("Signed signature overlays hidden");
    }

    /**
     * Finds the main page component (JLabel or JLayeredPane) within a wrapper.
     */
    private Component findPageComponent(JPanel pageWrapper) {
        for (Component c : pageWrapper.getComponents()) {
            if (c instanceof JLabel || c instanceof JLayeredPane) {
                return c;
            }
        }
        return null;
    }

    public void close() {
        try {
            if (document != null) document.close();
        } catch (Exception e) {
            log.error("Failed to close the current PDF document", e);
            DialogUtils.showError(owner, "Unable to Close PDF",
                    "An unexpected error occurred while closing the PDF. Please try again.");
            System.exit(1);
        } finally {
            // Cleanup all overlay timers to prevent memory leaks
            for (SignatureFieldOverlay overlay : fieldOverlays) {
                overlay.cleanup();
            }

            document = null;
            currentFile = null;
            pdfPassword = null;
            unsignedSignatureFields.clear();
            fieldOverlays.clear();
            signedSignatureOverlays.clear();
            showSignatureFieldsOverlay = false;
            pdfPanel.removeAll();
            pdfPanel.revalidate();
            pdfPanel.repaint();
        }
    }

    /* --------------------------
       Image Optimization
     --------------------------- */

    /**
     * Optimizes image for display by ensuring optimal color model and rendering hints.
     * This improves both memory usage and rendering performance.
     */
    private BufferedImage optimizeImageForDisplay(BufferedImage source) {
        if (source == null) return null;

        // If image is already in optimal format, return as-is
        int type = source.getType();
        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        // Convert to optimal format for display
        BufferedImage optimized = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = optimized.createGraphics();
        try {
            // Use quality rendering hints
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(source, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        return optimized;
    }

    /* --------------------------
       Password-aware loading
     --------------------------- */

    private PDDocument tryLoadDocument(File file) throws Exception {
        int attempts = 0;
        final int maxAttempts = 3;

        try {
            // Try without password first
            PDDocument doc = PDDocument.load(file);
            owner.setPdfPassword(null);
            return doc;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            PasswordDialog dialog = new PasswordDialog(
                    owner,
                    null,
                    "PDF Document Password required",
                    "Password",
                    "Open Document",
                    "Cancel"
            );

            while (attempts < maxAttempts) {
                dialog.setVisible(true);

                if (!dialog.isConfirmed() || dialog.wasClosedByUser()) {
                    throw new UserCancelledPasswordEntryException("User cancelled password entry.");
                }

                try {
                    String pwd = dialog.getValue();
                    owner.setPdfPassword(pwd);
                    PDDocument doc = PDDocument.load(file, pwd);
                    return doc;
                } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
                    attempts++;
                    if (attempts < maxAttempts) {
                        int remaining = maxAttempts - attempts;
                        dialog.showInvalidMessage(
                                String.format("Invalid password — try again (<b>%d</b> left.)", remaining)
                        );
                    }
                }
            }

            DialogUtils.showError(owner, "Access Denied", "Maximum password attempts reached. PDF loading cancelled.");
            throw new UserCancelledPasswordEntryException("Max password attempts exceeded.");
        }
    }
}
