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

    /**
     * Forces an update of the page display.
     * Useful when PDF is first loaded to ensure page number is shown.
     */
    public void forceUpdatePageDisplay() {
        // Ensure components are validated before updating
        wrapper.revalidate();
        pdfPanel.revalidate();

        // Use invokeLater to ensure layout is complete
        SwingUtilities.invokeLater(() -> {
            // Try multiple times with increasing delays to ensure components are ready
            tryUpdatePageDisplay(0);
        });
    }

    /**
     * Tries to update page display with retry logic to handle component initialization delays.
     */
    private void tryUpdatePageDisplay(int attempt) {
        int totalPages = rendererService.getPageCountSafe();

        // Check if components are ready
        if (totalPages > 0 && pdfPanel.getComponentCount() > 0) {
            Component firstPage = pdfPanel.getComponent(0);
            Rectangle bounds = firstPage.getBounds();
            Rectangle viewportRect = getViewport().getViewRect();

            // Check if bounds are valid (not zero)
            if (bounds.width > 0 && bounds.height > 0 && viewportRect.width > 0 && viewportRect.height > 0) {
                // Components are ready, update the display
                // Directly set page info instead of relying on scroll detection
                pageInfoUpdater.accept("Page: 1/" + totalPages);
                return;
            }
        }

        // Components not ready yet, retry with delay (max 15 attempts)
        if (attempt < 15) {
            int delay = 100; // Fixed 100ms delay
            Timer timer = new Timer(delay, e -> tryUpdatePageDisplay(attempt + 1));
            timer.setRepeats(false);
            timer.start();
        } else {
            // Final fallback - just set page 1
            if (totalPages > 0) {
                pageInfoUpdater.accept("Page: 1/" + totalPages);
            }
        }
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

