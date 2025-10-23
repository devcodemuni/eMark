package com.codemuni.controller;

import com.codemuni.App;
import com.codemuni.config.ConfigManager;
import com.codemuni.core.exception.CertificateNotFoundException;
import com.codemuni.core.exception.IncorrectPINException;
import com.codemuni.core.exception.UserCancelledOperationException;
import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.core.keyStoresProvider.KeyStoreProvider;
import com.codemuni.core.keyStoresProvider.PKCS11KeyStoreProvider;
import com.codemuni.core.keyStoresProvider.PKCS12KeyStoreProvider;
import com.codemuni.core.keyStoresProvider.WindowsKeyStoreProvider;
import com.codemuni.core.keyStoresProvider.X509CertificateValidatorImpl;
import com.codemuni.core.model.KeystoreAndCertificateInfo;
import com.codemuni.core.signer.AppearanceOptions;
import com.codemuni.gui.CertificateListDialog;
import com.codemuni.gui.SignatureAppearanceDialog;
import com.codemuni.gui.SmartCardCallbackHandler;
import com.codemuni.gui.pdfHandler.PdfViewerMain;
import com.codemuni.service.PdfSignerService;
import com.codemuni.utils.AppConstants;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Image;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SignerController {
    private static final Log log = LogFactory.getLog(SignerController.class);
    private final PKCS11KeyStoreProvider pkcs11KeyStoreProvider;
    private final PdfSignerService signerService;
    private File selectedFile;
    private String pdfPassword;
    private int pageNumber;
    private int[] coordinates;
    private String existingFieldName; // For signing existing signature fields
    private List<KeystoreAndCertificateInfo> keystoreAndCertificateInfos;
    private KeystoreAndCertificateInfo keystoreAndCertificateInfo;
    private PKCS12KeyStoreProvider pkcs12KeyStoreProvider;


    public SignerController() {
        pkcs11KeyStoreProvider = new PKCS11KeyStoreProvider(AppConstants.PKCS11_KEY_STORE);
        signerService = new PdfSignerService();
    }

    public void setOnSaveCancelled(Runnable onSaveCancelled) {
        signerService.setOnSaveCancelled(onSaveCancelled);
    }


    public void setSelectedFile(File selectedFile) {
        this.selectedFile = selectedFile;
    }

    public void setPdfPassword(String pdfPassword) {
        this.pdfPassword = pdfPassword;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setCoordinates(int[] coordinates) {
        this.coordinates = coordinates;
    }

    public void setCoordinates(float llx, float lly, float urx, float ury) {
        this.coordinates = new int[]{
                Math.round(llx),
                Math.round(lly),
                Math.round(urx),
                Math.round(ury)
        };
    }

    public void setExistingFieldName(String existingFieldName) {
        this.existingFieldName = existingFieldName;
    }

    public String getExistingFieldName() {
        return existingFieldName;
    }

    /**
     * Starts the signing service by prompting the user to select a certificate and signing the PDF.
     * Execution stops gracefully if the user cancels at any stage.
     */
    public void startSigningService() throws KeyStoreException, IOException, CertificateNotFoundException, IncorrectPINException {
        loadValidCertificates();

        if (keystoreAndCertificateInfos.isEmpty()) {
            log.error("No valid certificates were found in the keystore. Prompting user to select a PFX certificate.");
        }

        CertificateListDialog certDialog = new CertificateListDialog(PdfViewerMain.INSTANCE, keystoreAndCertificateInfos);
        certDialog.setVisible(true);

        keystoreAndCertificateInfo = certDialog.getSelectedKeystoreInfo();

        if (keystoreAndCertificateInfo == null) {
            throw new UserCancelledOperationException("User cancelled certificate selection");
        }

        X509Certificate x509Certificate = loadSelectedCertificate();
        if (x509Certificate == null) {
            log.info("No certificate loaded. Signing cancelled.");
            return;
        }


        SignatureAppearanceDialog appearanceDialog = new SignatureAppearanceDialog(PdfViewerMain.INSTANCE);
        appearanceDialog.setCertificate(x509Certificate);
        appearanceDialog.showAppearanceConfigPrompt();

        AppearanceOptions appearanceOptions = appearanceDialog.getAppearanceOptions();
        if (appearanceOptions == null) return;

        // watermark image
        try {
            Image watermarkImage = Image.getInstance(Objects.requireNonNull(App.class.getResource("/icons/logo.png")));
            appearanceOptions.setWatermarkImage( appearanceOptions.isGraphicRendering() ? null : watermarkImage); // if graphic rendering is enabled, no watermark image
        } catch (BadElementException | IOException ignore) {
        }

        // Check if we're signing an existing field or creating a new one
        boolean hasExistingField = existingFieldName != null && !existingFieldName.trim().isEmpty();
        if (hasExistingField) {
            appearanceOptions.setExistingFieldName(existingFieldName);
            if (pageNumber > 0) {
                appearanceOptions.setPageNumber(pageNumber);
            }
            if (coordinates != null && coordinates.length == 4) {
                appearanceOptions.setCoordinates(coordinates);
            } else {
                log.warn("Existing field coordinates are missing; appearance customization may be limited.");
            }
            log.info("Signing into existing field: " + existingFieldName);
        } else {
            appearanceOptions.setPageNumber(pageNumber);
            appearanceOptions.setCoordinates(coordinates);
            log.info("Creating new signature field at page " + pageNumber);
        }


        signerService.setSelectedFile(selectedFile);
        signerService.setPdfPassword(pdfPassword);


        KeyStoreProvider provider = createProvider();
        signerService.setProvider(provider);
        signerService.launchSigningFlow(appearanceOptions);
    }

    /**
     * Loads all valid certificates from configured keystore providers.
     */
    private void loadValidCertificates() {
        List<KeyStoreProvider> keyStoreProviders = loadStoresProviders();
        X509CertificateValidatorImpl validator = new X509CertificateValidatorImpl();

        keystoreAndCertificateInfos = keyStoreProviders.stream()
                .flatMap(provider -> provider.loadCertificates().stream())
                .distinct()
                .filter(certInfo -> {
                    X509Certificate cert = certInfo.getCertificate();
                    return !validator.isExpired(cert)
                            && !validator.isNotYetValid(cert)
                            && validator.isDigitalSignatureAllowed(cert)
                            && validator.isEndEntity(cert);
                })
                .collect(Collectors.toList());
    }

    /**
     * Loads keystore providers based on active configuration.
     */
    private List<KeyStoreProvider> loadStoresProviders() {
        List<KeyStoreProvider> providers = new ArrayList<>();
        Map<String, Boolean> activeStores = ConfigManager.getActiveStore();

        if (Boolean.TRUE.equals(activeStores.get(AppConstants.WIN_KEY_STORE))) {
            providers.add(new WindowsKeyStoreProvider());
        }

        if (Boolean.TRUE.equals(activeStores.get(AppConstants.PKCS11_KEY_STORE))) {
            pkcs11KeyStoreProvider.setPkcs11LibPathsToBeLoadPublicKey(ConfigManager.getPKCS11Paths());
            providers.add(pkcs11KeyStoreProvider);
        }

        return providers;
    }

    /**
     * Loads the selected certificate and initializes PKCS12 provider if necessary.
     */
    private X509Certificate loadSelectedCertificate() throws KeyStoreException, UserCancelledPasswordEntryException {
        String keystoreName = keystoreAndCertificateInfo.getKeystoreName();

        if (AppConstants.SOFTHSM.equals(keystoreName)) {
            String pfxFilePath = keystoreAndCertificateInfo.getPfxFilePath();
            pkcs12KeyStoreProvider = new PKCS12KeyStoreProvider(pfxFilePath);
            return pkcs12KeyStoreProvider.getCertificate();
        } else {
            return keystoreAndCertificateInfo.getCertificate();
        }
    }

    /**
     * Creates appropriate KeyStoreProvider based on selected certificate info.
     */
    private KeyStoreProvider createProvider() throws KeyStoreException, IOException {

        String keystoreName = keystoreAndCertificateInfo.getKeystoreName();
        KeyStoreProvider provider;

        switch (keystoreName) {

            case AppConstants.WIN_KEY_STORE: {
                WindowsKeyStoreProvider windowsKeyStoreProvider = new WindowsKeyStoreProvider();
                windowsKeyStoreProvider.setSerialHex(keystoreAndCertificateInfo.getCertificateSerial());
                provider = windowsKeyStoreProvider;
                break;
            }

            case AppConstants.PKCS11_KEY_STORE: {
                pkcs11KeyStoreProvider.setTokenSerialNumber(keystoreAndCertificateInfo.getTokenSerial());
                pkcs11KeyStoreProvider.setPkcs11LibPath(keystoreAndCertificateInfo.getPkcs11LibPath());
                pkcs11KeyStoreProvider.setCertificateSerialNumber(keystoreAndCertificateInfo.getCertificateSerial());
                pkcs11KeyStoreProvider.loadKeyStore(new SmartCardCallbackHandler());
                provider = pkcs11KeyStoreProvider;
                break;
            }

            case AppConstants.SOFTHSM: {
                provider = pkcs12KeyStoreProvider;
                break;
            }

            default:
                log.error("Unsupported keystore type: " + keystoreName);
                return null;
        }

        return provider;
    }

}
