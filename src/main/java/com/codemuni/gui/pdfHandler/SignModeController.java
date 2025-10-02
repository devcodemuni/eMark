package com.codemuni.gui.pdfHandler;

import com.codemuni.controller.SignerController;
import com.codemuni.core.exception.IncorrectPINException;
import com.codemuni.core.exception.MaxPinAttemptsExceededException;
import com.codemuni.core.exception.UserCancelledOperationException;
import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.gui.DialogUtils;
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

        // ESC to cancel sign mode
        rendererService.getPdfPanel().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && signModeEnabled) {
                    resetSignModeUI();
                }
            }
        });
    }

    public void toggleSignMode() {
        signModeEnabled = !signModeEnabled;
        updateSignModeUI();

        if (signModeEnabled) {
            String message =
                    "<html><body style='font-family:Segoe UI, sans-serif; font-size:12px; " +
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
            onSignStart.run();
        } else {
            onSignDone.run();
        }
        rendererService.getPdfPanel().requestFocusInWindow();
    }

    private void attachDrawingListenersToAllPages() {
        JPanel pdfPanel = rendererService.getPdfPanel();
        int totalPages = rendererService.getPageCountSafe();
        float scale = PdfRendererService.RENDER_DPI / 72f;

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
    }

    private JLabel findPageLabel(JPanel pageWrapper) {
        for (Component c : pageWrapper.getComponents()) {
            if (c instanceof JLabel) return (JLabel) c;
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

        // Avoid duplicate listeners by clearing previous UI and creating a fresh BasicLabelUI
        pageLabel.setUI(new BasicLabelUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                super.paint(g, c);
                if (drawnRect != null && signModeEnabled && pageLabel == activePageLabel) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(60, 141, 188, 100));
                    g2.fill(drawnRect);
                    g2.setColor(new Color(60, 141, 188));
                    g2.setStroke(new BasicStroke(2));
                    g2.draw(drawnRect);
                }
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
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (!signModeEnabled || drawnRect == null || startPoint == null || activePageLabel != pageLabel)
                    return;

                int x = Math.min(startPoint.x, e.getX());
                int y = Math.min(startPoint.y, e.getY());
                int width = Math.abs(startPoint.x - e.getX());
                int height = Math.abs(startPoint.y - e.getY());
                drawnRect.setBounds(x, y, width, height);
                pageLabel.repaint();
            }
        });
    }
}
