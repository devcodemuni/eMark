package com.codemuni.gui.pdfHandler;

import com.codemuni.service.SignatureFieldDetectionService.SignatureFieldInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual overlay component that highlights unsigned signature fields on PDF pages.
 * Provides professional visual feedback and click handling for signature field selection.
 */
public class SignatureFieldOverlay extends JPanel {

    // Application theme colors - matching sign button green (#28A745)
    // Extract RGB: 0x28A745 = R:40, G:167, B:69
    private static final Color APP_GREEN = new Color(40, 167, 69); // Application's sign button color
    private static final Color FIELD_BORDER_COLOR = new Color(40, 167, 69, 180); // Green with transparency
    private static final Color FIELD_FILL_COLOR = new Color(40, 167, 69, 15); // Very subtle green fill
    private static final Color FIELD_HOVER_BORDER_COLOR = new Color(40, 167, 69, 255); // Solid green on hover
    private static final Color FIELD_HOVER_FILL_COLOR = new Color(40, 167, 69, 40); // More visible green on hover
    private static final Color FIELD_GLOW_COLOR = new Color(40, 167, 69, 120); // Green glow for pulse
    private static final Color FIELD_TEXT_COLOR = new Color(230, 230, 230);
    private static final Color FIELD_TEXT_BG_COLOR = new Color(40, 40, 40, 240);

    private final List<SignatureFieldInfo> fieldsOnThisPage;
    private final int pageNumber; // 1-based
    private final float scale; // DPI scale factor
    private final FieldClickListener clickListener;
    private final int pageImageWidth;  // Actual rendered page image width
    private final int pageImageHeight; // Actual rendered page image height

    private SignatureFieldInfo hoveredField = null;
    private float pulseAlpha = 0.0f;
    private boolean pulseIncreasing = true;
    private Timer pulseTimer;

    // Dashed border animation
    private float dashPhase = 0.0f;
    private Timer dashAnimationTimer;

    /**
     * Listener interface for signature field click events.
     */
    public interface FieldClickListener {
        void onFieldClicked(SignatureFieldInfo field);
    }

    public SignatureFieldOverlay(int pageNumber, float scale, List<SignatureFieldInfo> allFields,
                                FieldClickListener clickListener, int pageImageWidth, int pageImageHeight) {
        this.pageNumber = pageNumber;
        this.scale = scale;
        this.clickListener = clickListener;
        this.pageImageWidth = pageImageWidth;
        this.pageImageHeight = pageImageHeight;

        // Filter fields for this specific page
        this.fieldsOnThisPage = new ArrayList<>();
        for (SignatureFieldInfo field : allFields) {
            if (field.getPageNumber() == pageNumber && !field.isSigned()) {
                this.fieldsOnThisPage.add(field);
            }
        }

        // Transparent overlay with custom painting
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0)); // Fully transparent background
        setLayout(null); // Absolute positioning for field rectangles

        // Enable mouse events
        setEnabled(true);
        setFocusable(true);

        setupMouseHandlers();
        setupPulseAnimation();
        setupDashAnimation();
    }

    /**
     * Sets up mouse handlers for hover and click detection.
     */
    private void setupMouseHandlers() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                SignatureFieldInfo previousHovered = hoveredField;
                hoveredField = getFieldAtPoint(e.getPoint());

                // Update tooltip based on hovered field
                if (hoveredField != null) {
                    setToolTipText(getFieldTooltip(hoveredField));
                } else {
                    setToolTipText(null);
                }

                // Repaint if hover state changed
                if (previousHovered != hoveredField) {
                    setCursor(hoveredField != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

                    // Stop pulse animation when hovering, start dash animation
                    if (hoveredField != null) {
                        if (pulseTimer != null) pulseTimer.stop();
                        if (dashAnimationTimer != null) dashAnimationTimer.start();
                    } else {
                        if (pulseTimer != null) pulseTimer.start();
                        if (dashAnimationTimer != null) dashAnimationTimer.stop();
                        dashPhase = 0.0f;
                    }

                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                SignatureFieldInfo clickedField = getFieldAtPoint(e.getPoint());
                if (clickedField != null && clickListener != null) {
                    // Stop animation before triggering click
                    if (pulseTimer != null) {
                        pulseTimer.stop();
                    }
                    clickListener.onFieldClicked(clickedField);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredField != null) {
                    hoveredField = null;
                    setCursor(Cursor.getDefaultCursor());
                    setToolTipText(null);

                    // Restart pulse animation, stop dash animation
                    if (pulseTimer != null) pulseTimer.start();
                    if (dashAnimationTimer != null) dashAnimationTimer.stop();
                    dashPhase = 0.0f;

                    repaint();
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    /**
     * Sets up an attractive pulse animation to draw attention to signature fields.
     * Enhanced with smooth easing for professional look.
     */
    private void setupPulseAnimation() {
        pulseTimer = new Timer(40, e -> {
            if (pulseIncreasing) {
                pulseAlpha += 0.04f;
                if (pulseAlpha >= 0.6f) {
                    pulseAlpha = 0.6f;
                    pulseIncreasing = false;
                }
            } else {
                pulseAlpha -= 0.04f;
                if (pulseAlpha <= 0.0f) {
                    pulseAlpha = 0.0f;
                    pulseIncreasing = true;
                }
            }
            repaint();
        });
        pulseTimer.start();
    }

    /**
     * Sets up dashed border animation for hover state.
     * Creates a "marching ants" effect when hovering over fields.
     */
    private void setupDashAnimation() {
        dashAnimationTimer = new Timer(50, e -> {
            dashPhase += 1.0f;
            if (dashPhase > 20.0f) {
                dashPhase = 0.0f;
            }
            repaint();
        });
    }

    /**
     * Generates tooltip text for a specific signature field.
     */
    private String getFieldTooltip(SignatureFieldInfo field) {
        return "<html><body style='width: 220px; padding: 6px; font-family: Segoe UI, sans-serif;'>" +
                "<div style='text-align: center; margin-bottom: 4px;'>" +
                "<span style='font-size: 12px; font-weight: bold; color: #28A745;'>✍️ Click to Sign</span>" +
                "</div>" +
                "<div style='font-size: 10px; line-height: 1.4; color: #cccccc;'>" +
                "Click here to add your digital signature to <b>" + field.getFieldName() + "</b>" +
                "</div>" +
                "</body></html>";
    }

    /**
     * Finds the signature field at a given screen point.
     */
    private SignatureFieldInfo getFieldAtPoint(Point screenPoint) {
        for (SignatureFieldInfo field : fieldsOnThisPage) {
            Rectangle screenRect = pdfRectToScreenRect(field);
            if (screenRect.contains(screenPoint)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Converts PDF coordinates to screen coordinates for rendering.
     * This is the REVERSE of SelectionUtils.convertToItextRectangle().
     *
     * SelectionUtils transforms: Screen → iText PDF coords
     * This method transforms: iText PDF coords → Screen
     *
     * Reverse transformation logic:
     * - iText llx, lly, urx, ury are in PDF coordinate space (72 points/inch)
     * - Screen coordinates are in pixels (RENDER_DPI = 100)
     * - Scale factor: 100/72 ≈ 1.3888
     * - Y-axis flip: PDF origin is bottom-left, Screen origin is top-left
     */
    private Rectangle pdfRectToScreenRect(SignatureFieldInfo field) {
        final int PADDING = 10; // PdfRendererService.DEFAULT_RENDERER_PADDING

        // Step 1: Convert PDF coordinates to screen space (apply scale)
        // From SelectionUtils: llx = x / scale → REVERSE: x = llx * scale
        float screenX = field.getLlx() * scale;

        // Step 2: Calculate dimensions in screen space
        float screenWidth = (field.getUrx() - field.getLlx()) * scale;
        float screenHeight = (field.getUry() - field.getLly()) * scale;

        // Step 3: Y-axis flip (PDF bottom-left → Screen top-left)
        // From SelectionUtils: lly = (imageHeight - y - height) / scale
        // REVERSE: y = imageHeight - (lly * scale) - height
        // But we use ury for cleaner calculation:
        // From SelectionUtils: ury = (imageHeight - y) / scale
        // REVERSE: y = imageHeight - (ury * scale)
        float screenY = pageImageHeight - (field.getUry() * scale);

        // Step 4: Add padding offset (screen coordinates include padding)
        int finalX = Math.round(screenX) + PADDING;
        int finalY = Math.round(screenY) + PADDING;
        int finalWidth = Math.round(screenWidth);
        int finalHeight = Math.round(screenHeight);

        return new Rectangle(finalX, finalY, finalWidth, finalHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Enable anti-aliasing for professional, smooth rendering
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Draw all signature fields on this page with attractive styling
            for (SignatureFieldInfo field : fieldsOnThisPage) {
                drawSignatureField(g2, field);
            }

        } finally {
            g2.dispose();
        }
    }

    /**
     * Draws a single signature field with professional, attractive styling.
     * Enhanced with dashed border animation, subtle glow, and theme colors.
     */
    private void drawSignatureField(Graphics2D g2, SignatureFieldInfo field) {
        Rectangle rect = pdfRectToScreenRect(field);
        boolean isHovered = (field == hoveredField);

        // Choose colors based on hover state
        Color borderColor = isHovered ? FIELD_HOVER_BORDER_COLOR : FIELD_BORDER_COLOR;
        Color fillColor = isHovered ? FIELD_HOVER_FILL_COLOR : FIELD_FILL_COLOR;

        // Draw outer glow/pulse effect (only when not hovering) - Subtle green glow
        if (!isHovered && pulseAlpha > 0) {
            int pulseAlphaInt = (int) (pulseAlpha * 255);
            Color pulseColor = new Color(40, 167, 69, Math.min(pulseAlphaInt, 120)); // Green pulse

            // Soft glow layer
            g2.setColor(new Color(40, 167, 69, pulseAlphaInt / 4));
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRoundRect(rect.x - 3, rect.y - 3, rect.width + 6, rect.height + 6, 10, 10);

            g2.setColor(pulseColor);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRoundRect(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, 8, 8);
        }

        // Draw very subtle fill (more transparent)
        g2.setColor(fillColor);
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6);

        // Draw dashed border with animation on hover, solid on normal
        if (isHovered) {
            // Animated dashed border (marching ants effect)
            float[] dashPattern = {8f, 4f};
            g2.setStroke(new BasicStroke(
                    2.5f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    10.0f,
                    dashPattern,
                    dashPhase
            ));
            g2.setColor(FIELD_HOVER_BORDER_COLOR);
            g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6);
        } else {
            // Normal dashed border (static)
            float[] dashPattern = {10f, 5f};
            g2.setStroke(new BasicStroke(
                    2f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    10.0f,
                    dashPattern,
                    0f
            ));
            g2.setColor(borderColor);
            g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6);
        }

        // Draw corner markers (professional L-shapes) - More prominent on hover
        drawCornerMarkers(g2, rect, borderColor, isHovered);

        // Draw signature icon and text with enhanced visibility
        drawFieldIcon(g2, rect, isHovered);
        drawFieldLabel(g2, rect, field, isHovered);
    }

    /**
     * Draws corner L-shaped markers for visual clarity (Adobe Reader style).
     * Enhanced with better visibility and smooth animations.
     */
    private void drawCornerMarkers(Graphics2D g2, Rectangle rect, Color color, boolean isHovered) {
        int lineLength = isHovered ? 20 : 16;
        float lineWidth = isHovered ? 3f : 2f;

        g2.setColor(color);
        g2.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Top-left
        g2.drawLine(rect.x, rect.y, rect.x + lineLength, rect.y);
        g2.drawLine(rect.x, rect.y, rect.x, rect.y + lineLength);

        // Top-right
        g2.drawLine(rect.x + rect.width, rect.y, rect.x + rect.width - lineLength, rect.y);
        g2.drawLine(rect.x + rect.width, rect.y, rect.x + rect.width, rect.y + lineLength);

        // Bottom-left
        g2.drawLine(rect.x, rect.y + rect.height, rect.x + lineLength, rect.y + rect.height);
        g2.drawLine(rect.x, rect.y + rect.height, rect.x, rect.y + rect.height - lineLength);

        // Bottom-right
        g2.drawLine(rect.x + rect.width, rect.y + rect.height, rect.x + rect.width - lineLength, rect.y + rect.height);
        g2.drawLine(rect.x + rect.width, rect.y + rect.height, rect.x + rect.width, rect.y + rect.height - lineLength);
    }

    /**
     * Draws a signature icon (pen icon) inside the field with theme colors.
     */
    private void drawFieldIcon(Graphics2D g2, Rectangle rect, boolean isHovered) {
        int centerX = rect.x + rect.width / 2;
        int centerY = rect.y + rect.height / 2;
        int iconSize = Math.min(rect.width, rect.height) / 4;
        iconSize = Math.max(16, Math.min(iconSize, 48)); // Clamp between 16-48

        // Use green theme color for icon
        g2.setColor(isHovered ? APP_GREEN : new Color(40, 167, 69, 200));
        g2.setStroke(new BasicStroke(isHovered ? 3f : 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Draw stylized pen icon
        int penX1 = centerX - iconSize / 2;
        int penY1 = centerY + iconSize / 2;
        int penX2 = centerX + iconSize / 2;
        int penY2 = centerY - iconSize / 2;

        g2.drawLine(penX1, penY1, penX2, penY2); // Pen body

        // Pen tip
        int tipSize = iconSize / 4;
        g2.fillOval(penX1 - tipSize / 2, penY1 - tipSize / 2, tipSize, tipSize);
    }

    /**
     * Draws an attractive label inside the field with instructions (Adobe Reader style).
     * Enhanced with better typography and visibility.
     */
    private void drawFieldLabel(Graphics2D g2, Rectangle rect, SignatureFieldInfo field, boolean isHovered) {
        // Adobe style: Show attractive call-to-action text
        String labelText = isHovered ? "✍ Click to Sign" : "Sign Here";

        // Use larger, bolder font for better visibility
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(labelText);
        int textHeight = fm.getHeight();

        // Center text inside the rectangle
        int textX = rect.x + (rect.width - textWidth) / 2;
        int textY = rect.y + (rect.height + textHeight / 2) / 2;

        // Draw background badge for text (professional look)
        if (rect.width > 80 && rect.height > 30) {
            int badgePadding = 8;
            int badgeWidth = textWidth + badgePadding * 2;
            int badgeHeight = textHeight + 4;
            int badgeX = textX - badgePadding;
            int badgeY = textY - textHeight + 4;

            // Semi-transparent dark background
            g2.setColor(new Color(40, 40, 40, isHovered ? 240 : 200));
            g2.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 6, 6);

            // Subtle border with green theme
            g2.setColor(new Color(40, 167, 69, isHovered ? 200 : 150));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 6, 6);
        }

        // Draw text shadow for depth
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawString(labelText, textX + 1, textY + 1);

        // Draw text with attractive color - green on hover
        g2.setColor(isHovered ? APP_GREEN : new Color(200, 200, 200));
        g2.drawString(labelText, textX, textY);
    }

    /**
     * Returns the number of unsigned signature fields on this page.
     */
    public int getFieldCount() {
        return fieldsOnThisPage.size();
    }

    /**
     * Checks if this overlay has any fields to display.
     */
    public boolean hasFields() {
        return !fieldsOnThisPage.isEmpty();
    }

    /**
     * Cleanup method to properly stop timers and release resources.
     * Should be called when the overlay is no longer needed to prevent memory leaks.
     */
    public void cleanup() {
        if (pulseTimer != null) {
            pulseTimer.stop();
            pulseTimer = null;
        }
        if (dashAnimationTimer != null) {
            dashAnimationTimer.stop();
            dashAnimationTimer = null;
        }
    }
}
