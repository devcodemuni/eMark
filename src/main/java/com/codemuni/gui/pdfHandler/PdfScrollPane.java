package com.codemuni.gui.pdfHandler;


import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Wraps the PDF panel (pages inside) and updates page label based on scroll.
 */
public class PdfScrollPane extends JScrollPane {

    private final JPanel pdfPanel;      // The vertical BoxLayout host of pages
    private final JPanel wrapper;       // Centers pdfPanel horizontally
    private final PdfRendererService rendererService;
    private final Consumer<String> pageInfoUpdater;

    public PdfScrollPane(PdfRendererService rendererService, Consumer<String> pageInfoUpdater) {
        this.rendererService = rendererService;
        this.pageInfoUpdater = pageInfoUpdater;

        pdfPanel = rendererService.getPdfPanel();
        wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 20));
        wrapper.add(pdfPanel);

        setViewportView(wrapper);
        setBorder(BorderFactory.createEmptyBorder());

        // Performance: Improved scroll speed for smoother navigation
        getVerticalScrollBar().setUnitIncrement(20);
        getVerticalScrollBar().setBlockIncrement(100);

        // Enable smooth scrolling performance
        getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        getVerticalScrollBar().addAdjustmentListener(e -> updateCurrentPageBasedOnScroll());
    }

    public JPanel getPdfPanel() {
        return pdfPanel;
    }

    private void updateCurrentPageBasedOnScroll() {
        int totalPages = rendererService.getPageCountSafe();
        if (totalPages <= 0 || pdfPanel.getComponentCount() == 0) {
            pageInfoUpdater.accept("");
            return;
        }

        Rectangle viewportRect = getViewport().getViewRect();
        for (int i = totalPages - 1; i >= 0; i--) {
            Component comp = pdfPanel.getComponent(i);
            Rectangle bounds = comp.getBounds();
            if (bounds.y + bounds.height - viewportRect.y <= viewportRect.height + 200) {
                pageInfoUpdater.accept("Page: " + (i + 1) + "/" + totalPages);
                break;
            }
        }
    }

    /**
     * Scrolls the viewport to show the specified page number (1-based).
     * @param pageNumber The page number to scroll to (1-based)
     */
    public void scrollToPage(int pageNumber) {
        int pageIndex = pageNumber - 1; // Convert to 0-based index

        // Wait for the component to be laid out
        SwingUtilities.invokeLater(() -> {
            if (pageIndex >= 0 && pageIndex < pdfPanel.getComponentCount()) {
                Component page = pdfPanel.getComponent(pageIndex);
                Rectangle pageBounds = page.getBounds();

                // Get the viewport and its current view rectangle
                JViewport viewport = getViewport();
                Rectangle viewRect = viewport.getViewRect();

                // Calculate the target position to center the page
                int targetY = pageBounds.y - (viewRect.height - pageBounds.height) / 3; // 1/3 from top
                targetY = Math.max(0, targetY); // Don't scroll above the top

                // Scroll to the target position
                viewport.setViewPosition(new Point(0, targetY));

                // Update the page info
                updateCurrentPageBasedOnScroll();
            }
        });
    }
}

