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
}

