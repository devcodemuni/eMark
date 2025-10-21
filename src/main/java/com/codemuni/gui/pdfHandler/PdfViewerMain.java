package com.codemuni.gui.pdfHandler;

import com.codemuni.App;
import com.codemuni.controller.SignerController;
import com.codemuni.gui.DialogUtils;
import com.codemuni.gui.settings.SettingsDialog;
import com.codemuni.service.SignatureVerificationService;
import com.codemuni.utils.CursorStateManager;
import com.codemuni.utils.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

import static com.codemuni.utils.AppConstants.APP_NAME;

/**
 * Responsibilities:
 * - Window frame & layout
 * - Orchestrates top bar, scroll pane, renderer, and sign controller
 * - File open & preferences (last directory)
 * - Title updates & placeholder toggle
 */
public class PdfViewerMain extends JFrame {
    private static final Log log = LogFactory.getLog(PdfViewerMain.class);

    // Layout / sizing
    private static final int INITIAL_WIDTH = 950;
    private static final int MIN_WIDTH = 800;
    private static final int MIN_HEIGHT = 400;

    // Preferences
    private static final Preferences prefs = Preferences.userNodeForPackage(PdfViewerMain.class);
    private static final String LAST_DIR_KEY = "lastPdfDir";

    // Singleton (if you still want it)
    public static PdfViewerMain INSTANCE = null;

    // Collaborators
    private final TopBarPanel topBar;
    private final PdfScrollPane pdfScrollPane;
    private final PlaceholderPanel placeholderPanel;
    private final PdfRendererService pdfRendererService;
    private final SignModeController signModeController;
    private final SignerController signerController = new SignerController();
    private final CollapsableSignaturePanel signaturePanel;
    private final SignatureVerificationService verificationService;
    private final SignatureColorManager colorManager;
    private final SignatureVerificationBanner verificationBanner;
    private JLayeredPane layeredPane;

    // State
    private File selectedPdfFile = null;
    private String pdfPassword = null;

    public PdfViewerMain() {
        super(APP_NAME);
        INSTANCE = this;

        setIconImage(App.getAppIcon());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int frameWidth = Math.min(INITIAL_WIDTH, screen.width);
        int frameHeight = screen.height - 50;
        setSize(frameWidth, frameHeight);
        setPreferredSize(new Dimension(frameWidth, frameHeight));
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setLocationRelativeTo(null);

        // Initialize cursor state manager
        CursorStateManager.getInstance().setTargetComponent(this);

        // Services
        pdfRendererService = new PdfRendererService(this);
        verificationService = new SignatureVerificationService();
        colorManager = new SignatureColorManager();
        signModeController = new SignModeController(
                PdfViewerMain.INSTANCE,
                pdfRendererService,
                signerController,
                this::onSignStart,
                this::onSignDone
        );

        // UI
        topBar = new TopBarPanel(
                this::openPdf,
                () -> new SettingsDialog(this).setVisible(true),
                signModeController::toggleSignMode
        );
        pdfScrollPane = new PdfScrollPane(
                pdfRendererService,
                topBar::setPageInfoText // callback to update page label
        );
        placeholderPanel = new PlaceholderPanel(this::openPdf);

        // Initialize verification banner first
        verificationBanner = new SignatureVerificationBanner();

        // Initialize signature panel
        signaturePanel = new CollapsableSignaturePanel();
        signaturePanel.setColorManager(colorManager); // Set color manager for color coding
        signaturePanel.setOnCloseCallback(() -> {
            // Update banner button state when panel is closed
            verificationBanner.setButtonSelected(false);
            layoutOverlayComponents();
        }); // Update layout when panel closes
        // Requirement 4: Highlight signature rectangle when selected from panel
        signaturePanel.setSignatureSelectionListener(pdfRendererService::highlightSignatureOnOverlay);
        signaturePanel.setOnVerifyAllCallback(this::verifyAllSignatures);

        // Connect signature button in banner to toggle signature panel
        verificationBanner.setSignatureButtonAction(() -> {
            if (signaturePanel.isClosed()) {
                signaturePanel.openPanel();
                verificationBanner.setButtonSelected(true);
            } else {
                signaturePanel.closePanel();
                verificationBanner.setButtonSelected(false);
            }
            layoutOverlayComponents();
        });

        // Create layered pane for overlay effect
        layeredPane = new JLayeredPane();
        layeredPane.setLayout(null); // Absolute positioning for overlay

        // Create container panel for banner and layered pane
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.add(verificationBanner, BorderLayout.NORTH);
        centerContainer.add(layeredPane, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(topBar, BorderLayout.NORTH);
        add(centerContainer, BorderLayout.CENTER);

        // Add component listener to handle resizing
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutOverlayComponents();
            }
        });

        showPlaceholder(true);
        enableDragAndDrop(placeholderPanel);
        enableDragAndDrop(pdfScrollPane);
    }

    /* --------------------------
       Public helpers / API
     --------------------------- */

    public void setWindowTitle(String titlePath) {
        String generated = Utils.truncateText(APP_NAME, titlePath, 70);
        setTitle(generated);
    }

    /**
     * Layouts the overlay components (PDF viewer, signature panel, and floating button).
     */
    private void layoutOverlayComponents() {
        if (layeredPane == null) return;

        int width = layeredPane.getWidth();
        int height = layeredPane.getHeight();

        if (width <= 0 || height <= 0) return;

        // PDF scroll pane takes full width/height (base layer)
        pdfScrollPane.setBounds(0, 0, width, height);

        // Signature panel overlay on right side (top layer)
        // Only position if panel is visible (not closed)
        if (signaturePanel.isVisible() && !signaturePanel.isClosed()) {
            int panelWidth = signaturePanel.getPreferredSize().width;
            signaturePanel.setBounds(width - panelWidth, 0, panelWidth, height);
        } else {
            // Hide panel completely when closed
            signaturePanel.setBounds(width, 0, 0, height);
        }

        // Ensure components are in layered pane
        if (pdfScrollPane.getParent() != layeredPane) {
            layeredPane.add(pdfScrollPane, Integer.valueOf(JLayeredPane.DEFAULT_LAYER));
        }
        if (signaturePanel.getParent() != layeredPane) {
            layeredPane.add(signaturePanel, Integer.valueOf(JLayeredPane.PALETTE_LAYER));
        }

        layeredPane.revalidate();
        layeredPane.repaint();
    }

    public void renderPdfFromPath(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            DialogUtils.showError(this, "Error", "File not found: " + filePath);
            return;
        }
        selectedPdfFile = file;
        loadAndRenderPdf(file);
    }

    /* --------------------------
       Internal wiring
     --------------------------- */

    private void showPlaceholder(boolean show) {
        if (show) {
            pdfScrollPane.setViewportView(placeholderPanel);
            topBar.setSignButtonVisible(false);
            topBar.setSignButtonCertified(false); // Reset certified state
            topBar.setPageInfoText("");
            signaturePanel.clearSignatures();
            signaturePanel.setVisible(false); // Hide signature panel when no PDF
            verificationBanner.hideBanner(); // Hide verification banner when no PDF
        } else {
            pdfScrollPane.setViewportView(pdfScrollPane.getPdfPanel());
            topBar.setSignButtonVisible(true);
            // Signature panel visibility is handled by verifyAndUpdateSignatures
            // Don't show panel here - wait for verification to complete
        }
        signModeController.resetSignModeUI();
        layoutOverlayComponents(); // Update layout
    }

    private void setLoadingState(boolean loading) {
        CursorStateManager cursorManager = CursorStateManager.getInstance();

        if (loading) {
            cursorManager.pushCursor(Cursor.WAIT_CURSOR, "pdf-loading");
        } else {
            cursorManager.popCursor("pdf-loading");
        }

        topBar.setLoading(loading);
    }

    private void openPdf() {
        JFileChooser chooser = new JFileChooser();
        String lastDir = prefs.get(LAST_DIR_KEY, null);
        if (lastDir != null) {
            chooser.setCurrentDirectory(new File(lastDir));
        }
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedPdfFile = chooser.getSelectedFile();

            File parentDir = selectedPdfFile.getParentFile();
            if (parentDir != null) {
                prefs.put(LAST_DIR_KEY, parentDir.getAbsolutePath());
            }
            loadAndRenderPdf(selectedPdfFile);
        }
    }

    private void loadAndRenderPdf(File file) {
        setLoadingState(true);
        SwingUtilities.invokeLater(() -> {
            boolean ok = pdfRendererService.render(file); // handles password internally

            // Don't clear loading state yet - keep WAIT cursor during verification
            // setLoadingState(false); // Removed - will be cleared after verification

            if (ok) {
                setWindowTitle(file.getAbsolutePath());

                // Initialize with signing disabled until verification completes
                topBar.setSignButtonCertified(true); // Temporarily disable until we verify
                topBar.setSignButtonVisible(true);

                topBar.setPageInfoText("Page: 1/" + pdfRendererService.getPageCountSafe());
                showPlaceholder(false);

                // Verify signatures and update signature panel
                // Keep cursor in WAIT state during verification
                verifyAndUpdateSignatures(file);
            } else {
                selectedPdfFile = null;
                topBar.setSignButtonVisible(false);
                topBar.setPageInfoText("");
                showPlaceholder(true);
                setLoadingState(false); // Clear loading state on error
            }
            signModeController.resetSignModeUI();
        });
    }

    /**
     * Verifies all signatures in the PDF and updates the signature panel.
     * Only shows panel if signatures are found.
     */
    private void verifyAndUpdateSignatures(File pdfFile) {
        // Reset color manager for new PDF
        colorManager.reset();

        // Show verification progress in banner and panel + disable buttons
        SwingUtilities.invokeLater(() -> {
            verificationBanner.showVerifying();
            signaturePanel.setVerifying(true); // Disable verify all button
            if (signaturePanel.isVisible()) {
                signaturePanel.setVerificationStatus("Verifying signatures...");
            }
        });

        // Run verification in background to avoid blocking UI
        new Thread(() -> {
            try {
                // Set progress listener for visual feedback - update both banner and panel
                verificationService.setProgressListener(message ->
                        SwingUtilities.invokeLater(() -> {
                            verificationBanner.updateProgress(message);
                            signaturePanel.setVerificationStatus(message);
                        })
                );

                List<SignatureVerificationService.SignatureVerificationResult> results =
                        verificationService.verifySignatures(pdfFile, pdfPassword);

                // Update UI on EDT
                SwingUtilities.invokeLater(() -> {
                    // Clear loading cursor state - verification complete
                    setLoadingState(false);

                    // Re-enable buttons
                    signaturePanel.setVerifying(false);

                    // Clear status message
                    signaturePanel.setVerificationStatus("");

                    if (results != null && !results.isEmpty()) {
                        // Apply PDF viewer certification logic for Begin Sign button
                        // Get LAST signature (most recent)
                        SignatureVerificationService.SignatureVerificationResult lastSig = results.get(results.size() - 1);
                        com.codemuni.model.CertificationLevel lastCertLevel = lastSig.getCertificationLevel();

                        // Begin Sign button logic (PDF viewer style)
                        boolean allowsSignatures = lastCertLevel.allowsSignatures(); // true only for NOT_CERTIFIED
                        topBar.setSignButtonCertified(!allowsSignatures);

                        if (!allowsSignatures) {
                            // Certified - show simple message
                            String tooltipMsg = "This document is certified. You cannot add more signatures.";
                            topBar.setSignButtonTooltip(tooltipMsg);
                            log.info("Signing DISABLED: " + tooltipMsg);
                        } else {
                            // Not certified - signing allowed
                            topBar.setSignButtonTooltip(null);
                            log.info("Signing ENABLED: Document allows additional signatures");
                        }

                        // PDF is signed - update signature panel and auto-open it
                        signaturePanel.updateSignatures(results);
                        signaturePanel.setVisible(true); // Make toggle button visible

                        // Auto-open panel to draw user attention to verification results
                        signaturePanel.openPanel();
                        verificationBanner.setButtonSelected(true); // Sync button state

                        // Update verification banner with results
                        verificationBanner.updateStatus(results);

                        // Draw colored rectangles on PDF pages
                        drawSignatureRectangles(results);

                        log.info("Signature panel updated with " + results.size() + " signature(s) and auto-opened");
                    } else {
                        // PDF is not signed - enable signing (unsigned PDF, signing allowed)
                        topBar.setSignButtonCertified(false);
                        topBar.setSignButtonTooltip(null);

                        // Hide signature panel and banner
                        signaturePanel.clearSignatures();
                        signaturePanel.setVisible(false);
                        verificationBanner.hideBanner();
                        log.info("No signatures found - signature panel hidden, signing enabled");
                    }
                    layoutOverlayComponents();
                });
            } catch (Exception e) {
                log.error("Error verifying signatures", e);
                SwingUtilities.invokeLater(() -> {
                    // Clear loading cursor state on error
                    setLoadingState(false);

                    // Re-enable buttons
                    signaturePanel.setVerifying(false);

                    signaturePanel.setVerificationStatus("Verification failed");
                    signaturePanel.clearSignatures();
                    signaturePanel.setVisible(false);
                    verificationBanner.hideBanner();
                    layoutOverlayComponents();
                });
            }
        }, "Signature-Verification-Thread").start();
    }

    /**
     * Requirement 2: Verifies all signatures manually when user clicks verify all button.
     */
    private void verifyAllSignatures() {
        if (selectedPdfFile == null) {
            return;
        }

        log.info("User triggered verify all signatures");

        // Show progress message + disable buttons
        signaturePanel.setVerificationStatus("Verifying all signatures...");
        signaturePanel.setVerifying(true); // Disable verify all button

        // Re-run verification in background
        new Thread(() -> {
            try {
                // Set progress listener for visual feedback
                verificationService.setProgressListener(message ->
                        SwingUtilities.invokeLater(() -> signaturePanel.setVerificationStatus(message))
                );

                List<SignatureVerificationService.SignatureVerificationResult> results =
                        verificationService.verifySignatures(selectedPdfFile, pdfPassword);

                // Update UI on EDT
                SwingUtilities.invokeLater(() -> {
                    signaturePanel.setVerificationStatus(""); // Clear status
                    signaturePanel.setVerifying(false); // Re-enable buttons

                    if (results != null && !results.isEmpty()) {
                        signaturePanel.updateSignatures(results);

                        // Update verification banner with new results
                        verificationBanner.updateStatus(results);

                        // Redraw signature rectangles
                        pdfRendererService.hideSignedSignatureOverlays();
                        drawSignatureRectangles(results);

                        log.info("Verified " + results.size() + " signature(s)");
                    }
                });
            } catch (Exception e) {
                log.error("Error during manual verification", e);
                SwingUtilities.invokeLater(() -> {
                    signaturePanel.setVerificationStatus("Verification failed");
                    signaturePanel.setVerifying(false); // Re-enable buttons
                });
            }
        }, "Manual-Signature-Verification-Thread").start();
    }

    /**
     * Draws colored rectangles on PDF pages to highlight signature locations.
     * Each signature gets a unique color from colorManager that matches the signature panel card.
     */
    private void drawSignatureRectangles(List<SignatureVerificationService.SignatureVerificationResult> results) {
        if (results == null || results.isEmpty()) {
            log.info("No signature rectangles to draw");
            return;
        }

        log.info("Drawing " + results.size() + " signature rectangle(s) on PDF pages");

        // Delegate to PDF renderer service to show overlays (with scrollPane for auto-scroll)
        pdfRendererService.showSignedSignatureOverlays(results, colorManager, pdfScrollPane);
    }

    private void enableDragAndDrop(JComponent component) {
        new DropTarget(component, DnDConstants.ACTION_COPY, new DropTargetListener() {

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    component.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                component.setBorder(null);
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                component.setBorder(null);
                try {
                    Transferable tr = dtde.getTransferable();
                    if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> files = (List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor);
                        for (File file : files) {
                            if (file.getName().toLowerCase().endsWith(".pdf")) {
                                selectedPdfFile = file;
                                loadAndRenderPdf(file);
                                break; // only handle the first PDF
                            }
                        }
                        dtde.dropComplete(true);
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                    log.error("Drag-and-drop failed", ex);
                }
            }
        }, true, null);
    }

    /* --------------------------
       Sign mode lifecycle hooks
     --------------------------- */

    private void onSignStart() {
        // Disable open/settings while in sign mode
        topBar.setInteractiveEnabled(false);
    }

    private void onSignDone() {
        // Re-enable controls after signing flow completes or cancels
        topBar.setInteractiveEnabled(true);
    }

    public String getPdfPassword() {
        return pdfPassword;
    }

    public void setPdfPassword(String pdfPassword) {
        this.pdfPassword = pdfPassword;
    }

    /**
     * Triggers sign mode for a specific signature field when user clicks on overlay.
     * This is called automatically when unsigned field overlays are clicked.
     */
    public void triggerSignModeForField(com.codemuni.service.SignatureFieldDetectionService.SignatureFieldInfo field) {
        log.info("Triggering sign mode for field: " + field.getFieldName());

        // Enable sign mode if not already enabled
        if (!topBar.isSignModeEnabled()) {
            signModeController.toggleSignMode();
        }

        // Delegate to sign mode controller to handle the field click
        signModeController.signExistingField(field);
    }
}
