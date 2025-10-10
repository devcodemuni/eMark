package com.codemuni.gui.pdfHandler;

import com.codemuni.controller.SignerController;
import com.codemuni.core.exception.IncorrectPINException;
import com.codemuni.core.exception.MaxPinAttemptsExceededException;
import com.codemuni.core.exception.UserCancelledOperationException;
import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.gui.DialogUtils;
import com.codemuni.service.SignatureFieldDetectionService.SignatureFieldInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import javax.swing.plaf.basic.BasicLabelUI;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Responsibilities:
 * - Manage sign mode enable/disable
 * - Apply crosshair cursor to pdf panel & children
 * - Attach mouse listeners to each page label for rectangle drawing
 * - Convert coords & invoke SignerController
 */
public class SignModeController {
    private static final Log log = LogFactory.getLog(SignModeController.class);

    // Color constants for better performance (avoid repeated object creation)
    private static final Color SELECTION_FILL_COLOR = new Color(66, 133, 244, 35);
    private static final Color SELECTION_BORDER_COLOR = new Color(66, 133, 244, 255);
    private static final Color HANDLE_FILL_COLOR = Color.WHITE;
    private static final Color HANDLE_BORDER_COLOR = new Color(66, 133, 244, 255);
    private static final Color MARKER_COLOR = new Color(66, 133, 244, 180);
    private static final Color CENTER_POINT_COLOR = new Color(66, 133, 244, 150);
    private static final Color DRAG_FEEDBACK_COLOR = new Color(66, 133, 244, 100);
    private static final Color GRID_COLOR_MINOR = new Color(128, 128, 128, 30);
    private static final Color GRID_COLOR_MAJOR = new Color(128, 128, 128, 50);
    private static final Color INFO_BG_COLOR = new Color(40, 40, 40, 220);
    private static final Color INFO_BORDER_COLOR = new Color(66, 133, 244, 100);
    private static final Color INFO_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color INFO_TEXT_SECONDARY = new Color(180, 180, 180);

    private final PdfViewerMain owner;
    private final PdfRendererService rendererService;
    private final SignerController signerController;

    private final Runnable onSignStart; // UI disable callback
    private final Runnable onSignDone;  // UI enable callback

    // Drawing state
    private boolean signModeEnabled = false;
    private Rectangle drawnRect = null;
    private Point startPoint = null;
    private JLabel activePageLabel = null;
    private int selectedPage = 0;
    private int[] pageCoords = new int[4];

    private volatile boolean isSigningInProgress = false;

    // Professional drawing features
    private static final int GRID_SIZE = 20; // Grid spacing in pixels
    private static final int HANDLE_SIZE = 8; // Size of resize handles

    private boolean showGrid = false; // Grid disabled by default
    private boolean snapToGrid = true;
    private Point currentMousePos = null;
    private boolean lockAspectRatio = false;

    public SignModeController(
            PdfViewerMain owner,
            PdfRendererService rendererService,
            SignerController signerController,
            Runnable onSignStart,
            Runnable onSignDone
    ) {
        this.owner = owner;
        this.rendererService = rendererService;
        this.signerController = signerController;
        this.onSignStart = onSignStart;
        this.onSignDone = onSignDone;

        // Keyboard controls for professional rectangle manipulation
        rendererService.getPdfPanel().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!signModeEnabled) return;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        resetSignModeUI();
                        break;

                    case KeyEvent.VK_SHIFT:
                        lockAspectRatio = true;
                        if (activePageLabel != null) activePageLabel.repaint();
                        break;

                    case KeyEvent.VK_G:
                        showGrid = !showGrid;
                        if (activePageLabel != null) activePageLabel.repaint();
                        break;

                    case KeyEvent.VK_S:
                        if (e.isControlDown()) {
                            snapToGrid = !snapToGrid;
                            if (activePageLabel != null) activePageLabel.repaint();
                        }
                        break;

                    // Arrow keys for precise movement (when rectangle exists)
                    case KeyEvent.VK_UP:
                        if (drawnRect != null) {
                            drawnRect.y -= e.isShiftDown() ? 10 : 1;
                            if (activePageLabel != null) activePageLabel.repaint();
                        }
                        break;
                    case KeyEvent.VK_DOWN:
                        if (drawnRect != null) {
                            drawnRect.y += e.isShiftDown() ? 10 : 1;
                            if (activePageLabel != null) activePageLabel.repaint();
                        }
                        break;
                    case KeyEvent.VK_LEFT:
                        if (drawnRect != null) {
                            drawnRect.x -= e.isShiftDown() ? 10 : 1;
                            if (activePageLabel != null) activePageLabel.repaint();
                        }
                        break;
                    case KeyEvent.VK_RIGHT:
                        if (drawnRect != null) {
                            drawnRect.x += e.isShiftDown() ? 10 : 1;
                            if (activePageLabel != null) activePageLabel.repaint();
                        }
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    lockAspectRatio = false;
                    if (activePageLabel != null) activePageLabel.repaint();
                }
            }
        });
    }

    public void toggleSignMode() {
        signModeEnabled = !signModeEnabled;
        updateSignModeUI();

        if (signModeEnabled) {
            // Hide all signature verification overlays when entering sign mode
            rendererService.hideSignedSignatureOverlays();
            rendererService.hideSignatureFieldOverlays();

            log.info("Sign mode enabled - all overlays hidden");

            // Check if unsigned signature fields exist
            boolean hasUnsignedFields = rendererService.hasUnsignedSignatureFields();

            // Only show instruction dialog for manual drawing mode
            // If unsigned fields exist, user sees tooltip on hover - no dialog needed
            if (!hasUnsignedFields) {
                // Show instruction only for manual rectangle drawing
                String message = "<html><body style='font-family:Segoe UI, sans-serif; font-size:12px; " +
                        "line-height:1.5;'>" +
                        "Click and drag to position your digital signature on the document.<br />Adjust the size as needed, then release to confirm." +
                        "</body></html>";

                DialogUtils.showHtmlMessageWithCheckbox(
                        owner,
                        "Guide for Signing PDF",
                        message,
                        "showSignModeMessage"
                );
            }
            // If unsigned fields exist: No dialog - user gets tooltip on hover for guidance
        }
    }

    public void resetSignModeUI() {
        signModeEnabled = false;
        isSigningInProgress = false;
        drawnRect = null;
        activePageLabel = null;
        startPoint = null;
        selectedPage = 0;

        // Reset cursor for all components
        if (rendererService != null && rendererService.getPdfPanel() != null) {
            applyCursorRecursively(rendererService.getPdfPanel(), Cursor.getDefaultCursor());
        }

        // Clear any drawn rectangles
        if (activePageLabel != null) {
            activePageLabel.repaint();
        }

        // Notify UI to update
        if (onSignDone != null) {
            onSignDone.run();
        }
    }

    private void updateSignModeUI() {
        applyCursorRecursively(rendererService.getPdfPanel(),
                signModeEnabled ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());

        // Attach drawing listeners lazily each time sign mode is toggled on,
        // so any newly rendered pages get listeners.
        if (signModeEnabled) {
            attachDrawingListenersToAllPages();

            // Show signature field overlays if there are unsigned fields
            if (rendererService.hasUnsignedSignatureFields()) {
                rendererService.showSignatureFieldOverlays(this::signExistingField);
                log.info("Signature field overlays displayed");
            }

            onSignStart.run();
        } else {
            // Hide signature field overlays when exiting sign mode
            rendererService.hideSignatureFieldOverlays();
            onSignDone.run();
        }
        rendererService.getPdfPanel().requestFocusInWindow();
    }

    private void attachDrawingListenersToAllPages() {
        JPanel pdfPanel = rendererService.getPdfPanel();
        int totalPages = rendererService.getPageCountSafe();
        float scale = PdfRendererService.RENDER_DPI / 72f;

        // Adobe Reader style: Enable BOTH modes
        // - User can click on unsigned fields (green overlays)
        // - User can also draw new rectangles anywhere on PDF
        for (int i = 0; i < totalPages; i++) {
            // Each child is a page wrapper (FlowLayout) with one JLabel inside
            Component wrapper = pdfPanel.getComponent(i);
            if (wrapper instanceof JPanel) {
                JLabel pageLabel = findPageLabel((JPanel) wrapper);
                if (pageLabel != null) {
                    enableRectangleDrawing(pageLabel, i, scale);
                }
            }
        }

        boolean hasUnsignedFields = rendererService.hasUnsignedSignatureFields();
        if (hasUnsignedFields) {
            log.info("Manual rectangle drawing enabled (unsigned fields also available for clicking)");
        } else {
            log.info("Manual rectangle drawing enabled (no unsigned fields detected)");
        }
    }

    private JLabel findPageLabel(JPanel pageWrapper) {
        for (Component c : pageWrapper.getComponents()) {
            // Direct JLabel case (no overlays)
            if (c instanceof JLabel) {
                return (JLabel) c;
            }
            // JLayeredPane case (when signature field overlays are shown)
            if (c instanceof JLayeredPane) {
                JLayeredPane layeredPane = (JLayeredPane) c;
                for (Component child : layeredPane.getComponents()) {
                    if (child instanceof JLabel) {
                        return (JLabel) child;
                    }
                }
            }
        }
        return null;
    }

    private void applyCursorRecursively(Component component, Cursor cursor) {
        component.setCursor(cursor);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyCursorRecursively(child, cursor);
            }
        }
    }

    /* --------------------------
       Drawing + Signing
     --------------------------- */

    private void enableRectangleDrawing(JLabel pageLabel, int pageIndex, float scale) {

        // Professional minimalist UI with optimized rendering
        pageLabel.setUI(new BasicLabelUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                super.paint(g, c);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    // Enable anti-aliasing for smooth, professional edges
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    // Draw grid when in sign mode (subtle, professional)
                    if (signModeEnabled && showGrid && pageLabel == activePageLabel) {
                        drawGrid(g2, c);
                    }

                    // Draw the signature rectangle (professional feature-rich design)
                    if (drawnRect != null && signModeEnabled && pageLabel == activePageLabel) {
                        // Semi-transparent fill - matches dark theme
                        g2.setColor(SELECTION_FILL_COLOR);
                        g2.fill(drawnRect);

                        // Single clean border
                        g2.setColor(SELECTION_BORDER_COLOR);
                        g2.setStroke(new BasicStroke(2f));
                        g2.draw(drawnRect);

                        // Professional resize handles (8 handles: 4 corners + 4 edges)
                        drawProfessionalResizeHandles(g2, drawnRect);

                        // Minimalist corner markers (simple L-shapes)
                        drawMinimalistCornerMarkers(g2, drawnRect);

                        // Display dimensions and additional info
                        drawEnhancedDimensionsInfo(g2, drawnRect, scale);

                        // Draw center point for alignment
                        drawCenterPoint(g2, drawnRect);
                    }
                } finally {
                    g2.dispose();
                }
            }

            private void drawProfessionalResizeHandles(Graphics2D g2, Rectangle rect) {
                int halfSize = HANDLE_SIZE / 2;

                // Define all 8 handle positions
                int[][] handles = {
                    {rect.x, rect.y}, // TL - Top Left
                    {rect.x + rect.width, rect.y}, // TR - Top Right
                    {rect.x, rect.y + rect.height}, // BL - Bottom Left
                    {rect.x + rect.width, rect.y + rect.height}, // BR - Bottom Right
                    {rect.x + rect.width / 2, rect.y}, // T - Top
                    {rect.x + rect.width / 2, rect.y + rect.height}, // B - Bottom
                    {rect.x, rect.y + rect.height / 2}, // L - Left
                    {rect.x + rect.width, rect.y + rect.height / 2} // R - Right
                };

                for (int[] handle : handles) {
                    int x = handle[0] - halfSize;
                    int y = handle[1] - halfSize;

                    // Fill - white for visibility
                    g2.setColor(HANDLE_FILL_COLOR);
                    g2.fillRect(x, y, HANDLE_SIZE, HANDLE_SIZE);

                    // Border - blue to match theme
                    g2.setColor(HANDLE_BORDER_COLOR);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x, y, HANDLE_SIZE, HANDLE_SIZE);
                }
            }

            private void drawMinimalistCornerMarkers(Graphics2D g2, Rectangle rect) {
                int lineLength = 15; // Slightly longer for better visibility

                g2.setColor(MARKER_COLOR);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));

                // Top-left corner
                g2.drawLine(rect.x, rect.y, rect.x + lineLength, rect.y);
                g2.drawLine(rect.x, rect.y, rect.x, rect.y + lineLength);

                // Top-right corner
                g2.drawLine(rect.x + rect.width, rect.y, rect.x + rect.width - lineLength, rect.y);
                g2.drawLine(rect.x + rect.width, rect.y, rect.x + rect.width, rect.y + lineLength);

                // Bottom-left corner
                g2.drawLine(rect.x, rect.y + rect.height, rect.x + lineLength, rect.y + rect.height);
                g2.drawLine(rect.x, rect.y + rect.height, rect.x, rect.y + rect.height - lineLength);

                // Bottom-right corner
                g2.drawLine(rect.x + rect.width, rect.y + rect.height, rect.x + rect.width - lineLength, rect.y + rect.height);
                g2.drawLine(rect.x + rect.width, rect.y + rect.height, rect.x + rect.width, rect.y + rect.height - lineLength);
            }

            private void drawCenterPoint(Graphics2D g2, Rectangle rect) {
                int centerX = rect.x + rect.width / 2;
                int centerY = rect.y + rect.height / 2;
                int size = 4;

                // Small center crosshair
                g2.setColor(CENTER_POINT_COLOR);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(centerX - size, centerY, centerX + size, centerY);
                g2.drawLine(centerX, centerY - size, centerX, centerY + size);

                // Center dot
                g2.fillOval(centerX - 2, centerY - 2, 4, 4);
            }

            private void drawGrid(Graphics2D g2, JComponent c) {
                int width = c.getWidth();
                int height = c.getHeight();

                // Subtle grid lines - professional and minimal
                g2.setColor(GRID_COLOR_MINOR);
                g2.setStroke(new BasicStroke(0.5f));

                // Draw vertical lines
                for (int x = GRID_SIZE; x < width; x += GRID_SIZE) {
                    g2.drawLine(x, 0, x, height);
                }

                // Draw horizontal lines
                for (int y = GRID_SIZE; y < height; y += GRID_SIZE) {
                    g2.drawLine(0, y, width, y);
                }

                // Major grid lines every 5 divisions - slightly more visible
                g2.setColor(GRID_COLOR_MAJOR);
                g2.setStroke(new BasicStroke(0.8f));
                for (int x = GRID_SIZE * 5; x < width; x += GRID_SIZE * 5) {
                    g2.drawLine(x, 0, x, height);
                }
                for (int y = GRID_SIZE * 5; y < height; y += GRID_SIZE * 5) {
                    g2.drawLine(0, y, width, y);
                }
            }

            private void drawEnhancedDimensionsInfo(Graphics2D g2, Rectangle rect, float scale) {
                int pdfWidth = Math.round(rect.width / scale);
                int pdfHeight = Math.round(rect.height / scale);

                // Create multi-line info
                String line1 = pdfWidth + " × " + pdfHeight + " pt";
                String line2 = "X:" + Math.round(rect.x / scale) + " Y:" + Math.round(rect.y / scale);
                String statusText = lockAspectRatio ? " 🔒 " : "";

                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                FontMetrics fm = g2.getFontMetrics();
                int width1 = fm.stringWidth(line1);
                int width2 = fm.stringWidth(line2 + statusText);
                int maxWidth = Math.max(width1, width2);
                int lineHeight = fm.getHeight();

                int textX = rect.x + (rect.width - maxWidth) / 2;
                int textY = rect.y - 10;

                // Ensure text stays within bounds
                if (textY - lineHeight * 2 < 0) {
                    textY = rect.y + rect.height + lineHeight * 2 + 8;
                }

                // Professional dark badge
                int padding = 7;
                int badgeHeight = lineHeight * 2 + padding;
                g2.setColor(INFO_BG_COLOR);
                g2.fillRoundRect(textX - padding, textY - lineHeight * 2 + 2, maxWidth + padding * 2, badgeHeight, 8, 8);

                // Subtle border
                g2.setColor(INFO_BORDER_COLOR);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(textX - padding, textY - lineHeight * 2 + 2, maxWidth + padding * 2, badgeHeight, 8, 8);

                // Text
                g2.setColor(INFO_TEXT_PRIMARY);
                g2.drawString(line1, textX + (maxWidth - width1) / 2, textY - lineHeight);
                g2.setColor(INFO_TEXT_SECONDARY);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.drawString(line2 + statusText, textX + (maxWidth - width2) / 2, textY);
            }
        });

        pageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            private Point localStartPoint = null;
            private Rectangle localDrawnRect = null;

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!signModeEnabled || isSigningInProgress) {
                    return;
                }

                isSigningInProgress = true;
                localStartPoint = e.getPoint();
                localDrawnRect = new Rectangle();
                startPoint = localStartPoint;
                drawnRect = localDrawnRect;
                activePageLabel = pageLabel;
                selectedPage = pageIndex;
                onSignStart.run();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (!signModeEnabled || localDrawnRect == null ||
                        localStartPoint == null ||
                        activePageLabel != pageLabel) {
                    resetSignModeUI();
                    return;
                }

                applyCursorRecursively(rendererService.getPdfPanel(), Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                SwingUtilities.invokeLater(() -> {
                    try {
                        int imageHeight = pageLabel.getIcon().getIconHeight();
                        int[] coords = SelectionUtils.convertToItextRectangle(
                                e.getX(), e.getY(),
                                localStartPoint.x, localStartPoint.y,
                                imageHeight,
                                scale,
                                PdfRendererService.DEFAULT_RENDERER_PADDING
                        );

                        if (coords[2] - coords[0] <= 30 || coords[3] - coords[1] <= 10) {
                            DialogUtils.showInfo(owner, "", "Draw a larger rectangle to sign.");
                            drawnRect = null;
                            pageLabel.repaint();
                            return;
                        }

                        pageCoords = coords;

                        File selectedFile = rendererService.getCurrentFile();
                        if (selectedFile == null) {
                            DialogUtils.showError(owner, "No file", "No PDF is currently loaded.");
                            return;
                        }

                        // Wire into existing SignerController API
                        signerController.setSelectedFile(selectedFile);
                        signerController.setPdfPassword(owner.getPdfPassword());
                        signerController.setPageNumber(selectedPage + 1);
                        signerController.setCoordinates(pageCoords);

                        // Clear existing field name to ensure we create a new signature field
                        signerController.setExistingFieldName(null);

                        signerController.startSigningService();

                        resetSignModeUI();
                        onSignDone.run();
                    } catch (UserCancelledPasswordEntryException | UserCancelledOperationException ex) {
                        log.info("User cancelled signing With reason: " + ex.getMessage());
                    } catch (IncorrectPINException ex) {
                        log.warn("Incorrect PIN entered");
                        DialogUtils.showError(PdfViewerMain.INSTANCE, "Incorrect PIN", ex.getMessage());
                    } catch (MaxPinAttemptsExceededException ex) {
                        log.warn("Maximum PIN attempts exceeded");
                        DialogUtils.showError(PdfViewerMain.INSTANCE, "Maximum PIN attempts exceeded, Signing aborted", ex.getMessage());
                    } catch (Exception ex) {
                        log.error("Error signing PDF", ex);
                        DialogUtils.showExceptionDialog(PdfViewerMain.INSTANCE, "Signing failed unknown error occurred", ex);
                    } finally {
                        applyCursorRecursively(rendererService.getPdfPanel(), Cursor.getDefaultCursor());
                        resetSignModeUI();
                    }
                });
            }
        });

        pageLabel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                if (!signModeEnabled || activePageLabel != pageLabel) return;

                // Update mouse position for crosshair
                Point oldPos = currentMousePos;
                currentMousePos = snapToGrid ? snapToGrid(e.getPoint()) : e.getPoint();

                // Repaint only if position changed
                if (oldPos == null || !oldPos.equals(currentMousePos)) {
                    pageLabel.repaint();
                }
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (!signModeEnabled || drawnRect == null || startPoint == null || activePageLabel != pageLabel)
                    return;

                // Performance: Store old bounds for optimized repaint
                Rectangle oldBounds = new Rectangle(drawnRect);

                // Get current position with optional snap-to-grid
                Point currentPos = snapToGrid ? snapToGrid(e.getPoint()) : e.getPoint();

                int x = Math.min(startPoint.x, currentPos.x);
                int y = Math.min(startPoint.y, currentPos.y);
                int width = Math.abs(startPoint.x - currentPos.x);
                int height = Math.abs(startPoint.y - currentPos.y);
                drawnRect.setBounds(x, y, width, height);

                // Performance: Only repaint the affected regions (old + new bounds + handles margin)
                int margin = 50; // Extra margin for handles, shadow, and info box
                Rectangle repaintRegion = oldBounds.union(drawnRect);
                repaintRegion.grow(margin, margin);
                pageLabel.repaint(repaintRegion);
            }
        });
    }

    /**
     * Snaps a point to the nearest grid intersection
     */
    private Point snapToGrid(Point p) {
        int snappedX = Math.round((float) p.x / GRID_SIZE) * GRID_SIZE;
        int snappedY = Math.round((float) p.y / GRID_SIZE) * GRID_SIZE;
        return new Point(snappedX, snappedY);
    }

    /**
     * Handles signing an existing signature field when clicked.
     * This method is called when a user clicks on a highlighted signature field overlay.
     * Opens the signature appearance dialog directly (Adobe Reader style).
     *
     * @param fieldInfo Information about the signature field to sign
     */
    public void signExistingField(SignatureFieldInfo fieldInfo) {
        if (fieldInfo == null) {
            log.warn("Cannot sign field: fieldInfo is null");
            return;
        }

        if (isSigningInProgress) {
            log.warn("Signing already in progress");
            return;
        }

        log.info("User clicked on unsigned signature field: " + fieldInfo.getFieldName() +
                 " on page " + fieldInfo.getPageNumber());

        // Disable sign mode UI temporarily
        isSigningInProgress = true;

        // Hide signature field overlays
        rendererService.hideSignatureFieldOverlays();

        SwingUtilities.invokeLater(() -> {
            try {
                File selectedFile = rendererService.getCurrentFile();
                if (selectedFile == null) {
                    DialogUtils.showError(owner, "No file", "No PDF is currently loaded.");
                    isSigningInProgress = false;
                    return;
                }

                // Set file and field information in SignerController
                signerController.setSelectedFile(selectedFile);
                signerController.setPdfPassword(owner.getPdfPassword());
                signerController.setExistingFieldName(fieldInfo.getFieldName()); // Use existing field name

                // Clear coordinates and page number to ensure we use the existing field only
                signerController.setCoordinates(null);
                signerController.setPageNumber(0);

                // Start signing service - this will open certificate selection and appearance dialog
                signerController.startSigningService();

                // Reset sign mode after signing completes
                resetSignModeUI();
                onSignDone.run();

            } catch (UserCancelledPasswordEntryException | UserCancelledOperationException ex) {
                log.info("User cancelled signing: " + ex.getMessage());
                isSigningInProgress = false;
                // Re-show overlays if user cancelled
                rendererService.showSignatureFieldOverlays(this::signExistingField);
            } catch (IncorrectPINException ex) {
                log.warn("Incorrect PIN entered");
                DialogUtils.showError(owner, "Incorrect PIN", ex.getMessage());
                isSigningInProgress = false;
                rendererService.showSignatureFieldOverlays(this::signExistingField);
            } catch (MaxPinAttemptsExceededException ex) {
                log.warn("Maximum PIN attempts exceeded");
                DialogUtils.showError(owner, "Maximum PIN attempts exceeded, Signing aborted", ex.getMessage());
                isSigningInProgress = false;
                rendererService.showSignatureFieldOverlays(this::signExistingField);
            } catch (Exception ex) {
                log.error("Error signing existing field", ex);
                DialogUtils.showExceptionDialog(owner, "Signing failed - unknown error occurred", ex);
                isSigningInProgress = false;
                rendererService.showSignatureFieldOverlays(this::signExistingField);
            }
        });
    }
}
