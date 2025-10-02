package com.codemuni.gui.pdfHandler;

import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.gui.DialogUtils;
import com.codemuni.gui.PasswordDialog;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

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

    public PdfRendererService(PdfViewerMain owner) {
        this.owner = owner;
        pdfPanel = new JPanel();
        pdfPanel.setLayout(new BoxLayout(pdfPanel, BoxLayout.Y_AXIS));
        pdfPanel.setFocusable(true);
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
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI);

                JPanel pageWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                pageWrapper.setOpaque(false);

                JLabel pageLabel = new JLabel(new ImageIcon(image));
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

    public void close() {
        try {
            if (document != null) document.close();
        } catch (Exception e) {
            log.error("Failed to close the current PDF document", e);
            DialogUtils.showError(owner, "Unable to Close PDF",
                    "An unexpected error occurred while closing the PDF. Please try again.");
            System.exit(1);
        } finally {
            document = null;
            currentFile = null;
            pdfPanel.removeAll();
            pdfPanel.revalidate();
            pdfPanel.repaint();
        }
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
