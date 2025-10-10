package com.codemuni.gui.pdfHandler;

import com.codemuni.utils.IconGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Floating button to show signature panel when it's closed.
 * Appears on the right side of the screen with an icon.
 */
public class FloatingSignatureButton extends JPanel {

    private static final Color BUTTON_BG = new Color(45, 45, 45, 230);
    private static final Color BUTTON_HOVER_BG = new Color(60, 60, 60, 230);
    private static final Color BUTTON_BORDER = new Color(70, 70, 70);
    private static final int BUTTON_WIDTH = 50;
    private static final int BUTTON_HEIGHT = 50;

    private final Runnable onClickAction;
    private boolean hovered = false;
    private ImageIcon normalIcon;
    private ImageIcon hoverIcon;
    private JLabel iconLabel;

    public FloatingSignatureButton(Runnable onClickAction) {
        this.onClickAction = onClickAction;

        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        setMaximumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Signature Panel");

        // Create icons
        normalIcon = IconGenerator.createSignaturePanelIcon(24, Color.LIGHT_GRAY);
        hoverIcon = IconGenerator.createSignaturePanelIcon(24, Color.WHITE);

        // Icon panel
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw background
                g2d.setColor(hovered ? BUTTON_HOVER_BG : BUTTON_BG);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                // Draw border
                g2d.setColor(BUTTON_BORDER);
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                g2d.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setLayout(new GridBagLayout());

        // Icon label with image
        iconLabel = new JLabel(normalIcon);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        iconPanel.add(iconLabel);
        add(iconPanel, BorderLayout.CENTER);

        // Mouse listeners
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (FloatingSignatureButton.this.onClickAction != null) {
                    FloatingSignatureButton.this.onClickAction.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                iconLabel.setIcon(hoverIcon);
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                iconLabel.setIcon(normalIcon);
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT);
    }
}
