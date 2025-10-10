package com.codemuni.gui.pdfHandler;

import com.codemuni.service.SignatureVerificationService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Overlay panel that draws colored rectangles on PDF pages to highlight signed signature locations.
 * Each signature gets a unique color from SignatureColorManager to link with the signature panel.
 *
 * Features:
 * - Normal state: Solid border (no highlight)
 * - Hover state: Dashed animated border
 * - Click: Shows verification dialog
 * - Highlight: Thicker border with fill (when selected from panel)
 */
public class SignedSignatureOverlay extends JPanel {

    private final int pageNumber;
    private final float scale;
    private final List<SignatureRect> signatureRects;
    private final int imageWidth;
    private final int imageHeight;
    private final PdfScrollPane scrollPane;

    // Interaction state
    private SignatureRect hoveredRect = null;
    private SignatureRect highlightedRect = null;

    // Animation
    private Timer dashAnimationTimer;
    private float dashPhase = 0.0f;

    /**
     * Creates a signature overlay for a specific page.
     *
     * @param pageNumber  Page number (1-based)
     * @param scale       Scale factor (DPI / 72)
     * @param results     All signature verification results
     * @param colorManager Color manager for signature colors
     * @param imageWidth  Actual rendered image width
     * @param imageHeight Actual rendered image height
     * @param scrollPane  Scroll pane for auto-scroll functionality
     */
    public SignedSignatureOverlay(
            int pageNumber,
            float scale,
            List<SignatureVerificationService.SignatureVerificationResult> results,
            SignatureColorManager colorManager,
            int imageWidth,
            int imageHeight,
            PdfScrollPane scrollPane) {

        this.pageNumber = pageNumber;
        this.scale = scale;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.scrollPane = scrollPane;
        this.signatureRects = new ArrayList<>();

        setOpaque(false);
        setLayout(null);

        // Extract signatures for this page
        for (SignatureVerificationService.SignatureVerificationResult result : results) {
            if (result.getPageNumber() == pageNumber && result.getPosition() != null) {
                Color borderColor = colorManager.getColorForSignature(result.getFieldName());
                Color transparentColor = colorManager.getTransparentColorForSignature(result.getFieldName());
                signatureRects.add(new SignatureRect(result, result.getPosition(), borderColor, transparentColor));
            }
        }

        setupMouseListeners();
        setupDashAnimation();
    }

    /**
     * Sets up mouse listeners for hover and click detection.
     */
    private void setupMouseListeners() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                SignatureRect previousHovered = hoveredRect;
                hoveredRect = getRectAtPoint(e.getPoint());

                if (previousHovered != hoveredRect) {
                    setCursor(hoveredRect != null ?
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) :
                        Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                SignatureRect clickedRect = getRectAtPoint(e.getPoint());
                if (clickedRect != null) {
                    showVerificationDialog(clickedRect);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredRect != null) {
                    hoveredRect = null;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
            }
        };

        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    /**
     * Sets up dashed border animation for hover state.
     */
    private void setupDashAnimation() {
        dashAnimationTimer = new Timer(50, e -> {
            dashPhase += 1.0f;
            if (dashPhase > 20.0f) {
                dashPhase = 0.0f;
            }
            if (hoveredRect != null) {
                repaint();
            }
        });
        dashAnimationTimer.start();
    }

    /**
     * Finds signature rectangle at given point.
     */
    private SignatureRect getRectAtPoint(Point point) {
        for (SignatureRect rect : signatureRects) {
            Rectangle bounds = calculateScreenBounds(rect);
            if (bounds.contains(point)) {
                return rect;
            }
        }
        return null;
    }

    /**
     * Shows signature properties dialog for clicked signature (directly, no intermediate dialog).
     */
    private void showVerificationDialog(SignatureRect rect) {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        if (parentFrame != null) {
            // Directly open detailed signature properties dialog
            SignaturePropertiesDialog dialog = new SignaturePropertiesDialog(
                parentFrame, rect.result, rect.borderColor);
            dialog.setVisible(true);
        }
    }

    /**
     * Highlights a signature by field name (called from panel selection).
     */
    public void highlightSignature(String fieldName) {
        for (SignatureRect rect : signatureRects) {
            if (rect.result.getFieldName().equals(fieldName)) {
                highlightedRect = rect;
                scrollToRect(rect);
                repaint();

                // Auto-remove highlight after 2 seconds
                Timer timer = new Timer(2000, e -> {
                    highlightedRect = null;
                    repaint();
                });
                timer.setRepeats(false);
                timer.start();
                break;
            }
        }
    }

    /**
     * Scrolls viewport to show the signature rectangle.
     */
    private void scrollToRect(SignatureRect rect) {
        if (scrollPane == null) {
            return;
        }

        Rectangle bounds = calculateScreenBounds(rect);
        JViewport viewport = scrollPane.getViewport();
        Rectangle viewRect = viewport.getViewRect();

        // Check if rectangle is not fully visible
        if (!viewRect.contains(bounds)) {
            // Calculate center position
            int centerX = bounds.x + bounds.width / 2 - viewRect.width / 2;
            int centerY = bounds.y + bounds.height / 2 - viewRect.height / 2;

            Point newPosition = new Point(
                Math.max(0, centerX),
                Math.max(0, centerY)
            );

            viewport.setViewPosition(newPosition);
        }
    }

    /**
     * Calculates screen bounds for a signature rectangle.
     */
    private Rectangle calculateScreenBounds(SignatureRect rect) {
        float[] pos = rect.position;
        // pos = [llx, lly, urx, ury] in PDF coordinates

        // Scale to screen coordinates
        int x = Math.round(pos[0] * scale);
        int y = Math.round((imageHeight / scale - pos[3]) * scale); // Flip Y axis
        int width = Math.round((pos[2] - pos[0]) * scale);
        int height = Math.round((pos[3] - pos[1]) * scale);

        // Add padding for border
        int padding = PdfRendererService.DEFAULT_RENDERER_PADDING;
        x += padding;
        y += padding;

        return new Rectangle(x, y, width, height);
    }

    /**
     * Returns true if this overlay has any signature rectangles to draw.
     */
    public boolean hasSignatures() {
        return !signatureRects.isEmpty();
    }

    /**
     * Returns the number of signatures on this page.
     */
    public int getSignatureCount() {
        return signatureRects.size();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw each signature rectangle
        for (SignatureRect rect : signatureRects) {
            drawSignatureRectangle(g2d, rect);
        }

        g2d.dispose();
    }

    /**
     * Draws a single signature rectangle with state-based styling.
     * States:
     * 1. Normal: Just show rectangle (no highlight) - Requirement 3
     * 2. Hover: Dashed border animation - Requirement 3
     * 3. Highlighted (selected from panel): Solid border with color - Requirement 4
     */
    private void drawSignatureRectangle(Graphics2D g2d, SignatureRect rect) {
        Rectangle bounds = calculateScreenBounds(rect);

        boolean isHovered = (rect == hoveredRect);
        boolean isHighlighted = (rect == highlightedRect);

        // Draw fill (only when highlighted)
        if (isHighlighted) {
            g2d.setColor(rect.transparentColor);
            g2d.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        // Draw border based on state
        if (isHovered) {
            // Requirement 3: Dashed border on hover (animated)
            float[] dashPattern = {8f, 4f};
            g2d.setStroke(new BasicStroke(
                3f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10.0f,
                dashPattern,
                dashPhase
            ));
            g2d.setColor(rect.borderColor);
            g2d.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        } else if (isHighlighted) {
            // Requirement 4: Thicker solid border when highlighted (selected from panel)
            g2d.setStroke(new BasicStroke(4f));
            g2d.setColor(rect.borderColor);
            g2d.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        } else {
            // Requirement 3: Normal state - rectangles are NOT highlighted initially
            // Just show subtle border to indicate signature field
            g2d.setStroke(new BasicStroke(2f));
            g2d.setColor(new Color(rect.borderColor.getRed(), rect.borderColor.getGreen(),
                                   rect.borderColor.getBlue(), 120)); // More subtle with transparency
            g2d.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    /**
     * Cleanup method to stop timers and release resources.
     */
    public void cleanup() {
        if (dashAnimationTimer != null) {
            dashAnimationTimer.stop();
            dashAnimationTimer = null;
        }
    }

    /**
     * Internal class to hold signature rectangle data.
     */
    private static class SignatureRect {
        final SignatureVerificationService.SignatureVerificationResult result;
        final float[] position; // [llx, lly, urx, ury]
        final Color borderColor;
        final Color transparentColor;

        SignatureRect(SignatureVerificationService.SignatureVerificationResult result,
                     float[] position, Color borderColor, Color transparentColor) {
            this.result = result;
            this.position = position;
            this.borderColor = borderColor;
            this.transparentColor = transparentColor;
        }
    }
}
