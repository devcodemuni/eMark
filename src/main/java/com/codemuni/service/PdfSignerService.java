package com.codemuni.service;

import com.codemuni.config.ConfigManager;
import com.codemuni.core.exception.SigningProcessException;
import com.codemuni.core.exception.TSAConfigurationException;
import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.core.keyStoresProvider.KeyStoreProvider;
import com.codemuni.core.keyStoresProvider.PKCS11KeyStoreProvider;
import com.codemuni.core.signer.AppearanceOptions;
import com.codemuni.core.signer.CustomTSAClientBouncyCastle;
import com.codemuni.core.signer.Signer;
import com.codemuni.gui.DialogUtils;
import com.codemuni.gui.pdfHandler.PdfViewerMain;
import com.codemuni.utils.AppConstants;
import com.itextpdf.text.pdf.PdfReader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;


public class PdfSignerService {


    private static final Log log = LogFactory.getLog(PdfSignerService.class);
    private File selectedFile;
    private String pdfPassword;
    private KeyStoreProvider provider;

    public PdfSignerService() {
    }

    private static Timer reRenderSignedPdfTimer(File saveFile) {
        Timer timer = new Timer(1000, evt -> {
            SwingUtilities.invokeLater(() -> {
                PdfViewerMain.INSTANCE.renderPdfFromPath(saveFile.getAbsolutePath());
                PdfViewerMain.INSTANCE.setWindowTitle(saveFile.getAbsolutePath());
                PdfViewerMain.INSTANCE.requestFocusInWindow();
                PdfViewerMain.INSTANCE.repaint();
            });
        });
        timer.setRepeats(false);
        return timer;
    }

    public void setProvider(KeyStoreProvider provider) {
        this.provider = provider;
    }

    public void setPdfPassword(String pdfPassword) {
        this.pdfPassword = pdfPassword;
    }

    public void setSelectedFile(File selectedFile) {
        this.selectedFile = selectedFile;
    }

    public void launchSigningFlow(AppearanceOptions appearanceOptions) {
        PdfReader reader = openPdfReader(selectedFile, pdfPassword);
        signPdfDocument(reader, provider, appearanceOptions);
    }

    private PdfReader openPdfReader(File file, String password) {
        try {
            if (password == null || password.isEmpty()) {
                return new PdfReader(file.getAbsolutePath());
            } else {
                return new PdfReader(file.getAbsolutePath(), password.getBytes());
            }
        } catch (IOException e) {
            log.error("Failed to open PDF file:::::::", e);
            return null;
        }
    }

    private void signPdfDocument(PdfReader reader, KeyStoreProvider provider, AppearanceOptions appearanceOptions) {
        try {

            CustomTSAClientBouncyCastle tsaClient = getTsaClient(appearanceOptions);
            String signedBase64 = new Signer().sign(reader, provider, "codemuni", AppConstants.APP_NAME, appearanceOptions, tsaClient);
            byte[] signedBytes = Base64.getDecoder().decode(signedBase64);

            File saveFile = showSaveFileDialog();
            if (saveFile == null) {
                System.out.println("User cancelled file saving.");
                return;
            }

            Files.write(saveFile.toPath(), signedBytes);

            // Render the signed PDF after 1 second delay
            if (saveFile.exists() && saveFile.length() > 0) {
                Timer timer = reRenderSignedPdfTimer(saveFile);
                timer.start();
            }

        } catch (Exception e) {
            handleSigningException(e, provider);
        } finally {
            reader.close();
            if (provider instanceof PKCS11KeyStoreProvider) {
                ((PKCS11KeyStoreProvider) provider).reset();
            }
        }
    }

    private void handleSigningException(Exception e, KeyStoreProvider provider) {
        if (provider instanceof PKCS11KeyStoreProvider) {
            ((PKCS11KeyStoreProvider) provider).reset();
        }

        if (e instanceof UserCancelledPasswordEntryException) {
            System.err.println(e.getMessage());
            return;
        }

        if (e instanceof TSAConfigurationException) {
            String htmlMessage = "<html><body>"
                    + "<div style='color:#ff5555; font-weight:bold;'>Timestamp Configuration Required</div>"
                    + "<div style='margin-top:6px; color:#dddddd;'>Timestamp server URL is missing or invalid.</div>"
                    + "<div style='margin-top:6px; color:#cccccc;'>"
                    + "Please check your timestamp server settings and try again."
                    + "</div></body></html>";

            DialogUtils.showError(
                    PdfViewerMain.INSTANCE,
                    "Timestamp Error",
                    htmlMessage
            );
            return;
        }

        DialogUtils.showExceptionDialog(
                PdfViewerMain.INSTANCE,
                e instanceof SigningProcessException ? "Error while signing PDF" : "Unexpected Error Occurred",
                e
        );

        log.error("Error while signing PDF", e);
    }

    private File showSaveFileDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Signed PDF");
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        File desktopDir = new File(System.getProperty("user.home"), "Desktop");
        if (desktopDir.exists()) {
            fileChooser.setCurrentDirectory(desktopDir);
        }

        File defaultFile = new File(desktopDir, selectedFile.getName());
        fileChooser.setSelectedFile(defaultFile);

        while (true) {
            int userSelection = fileChooser.showSaveDialog(null);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return null;
            }

            File chosenFile = fileChooser.getSelectedFile();
            if (!chosenFile.getName().toLowerCase().endsWith(".pdf")) {
                chosenFile = new File(chosenFile.getAbsolutePath() + ".pdf");
            }

            if (chosenFile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(
                        null,
                        "The file already exists. Do you want to replace it?",
                        "Confirm Overwrite",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (overwrite == JOptionPane.YES_OPTION) {
                    return chosenFile;
                }
            } else {
                return chosenFile;
            }
        }
    }

    private CustomTSAClientBouncyCastle getTsaClient(AppearanceOptions appearanceOptions) {
        if (!appearanceOptions.isTimestampEnabled()) return null;
        Map<String, String> tsaConfig = ConfigManager.getTimestampServer();
        return new CustomTSAClientBouncyCastle(
                tsaConfig.get("url"),
                tsaConfig.getOrDefault("username", null),
                tsaConfig.getOrDefault("password", null),
                8192,
                "SHA-256"
        );
    }
}
