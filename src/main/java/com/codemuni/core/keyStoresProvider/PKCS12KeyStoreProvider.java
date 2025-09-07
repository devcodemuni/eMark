package com.codemuni.core.keyStoresProvider;

import com.codemuni.exceptions.CertificateNotFoundException;
import com.codemuni.exceptions.KeyStoreInitializationException;
import com.codemuni.exceptions.PrivateKeyAccessException;
import com.codemuni.exceptions.UserCancelledPasswordEntryException;
import com.codemuni.gui.PasswordDialog;
import com.codemuni.gui.pdfHandler.PdfViewerMain;
import com.codemuni.model.KeystoreAndCertificateInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class PKCS12KeyStoreProvider implements KeyStoreProvider {

    // Retry config
    private static final int MAX_PIN_ATTEMPTS = 3;
    private static final Log log = LogFactory.getLog(PKCS12KeyStoreProvider.class);
    private final String pfxFilePath;
    private final Provider provider = new BouncyCastleProvider();
    // Session-level cached data
    private KeyStore keyStore;
    private PrivateKey privateKey;
    private X509Certificate certificate;
    private Certificate[] certificateChain;
    private char[] cachedPassword; // store password during session

    public PKCS12KeyStoreProvider(String pfxFilePath) {
        this.pfxFilePath = pfxFilePath;
    }

    @Override
    public List<KeystoreAndCertificateInfo> loadCertificates() {
        List<KeystoreAndCertificateInfo> certList = new ArrayList<>();
        try {
            loadKeyStore();
            String alias = getSigningAlias();

            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                certificate = (X509Certificate) cert;
                certificateChain = keyStore.getCertificateChain(alias);
                KeystoreAndCertificateInfo certInfo = new KeystoreAndCertificateInfo("PKCS12", pfxFilePath);
                certInfo.setCertificate(certificate);
                certList.add(certInfo);
            }
        } catch (Exception e) {
            throw new KeyStoreInitializationException(e.getMessage(), e);
        }
        return certList;
    }

    private void loadKeyStore() throws KeyStoreInitializationException, UserCancelledPasswordEntryException {
        if (keyStore != null) return; // Already loaded

        try {
            Security.addProvider(provider);
            keyStore = KeyStore.getInstance("PKCS12", provider);

            if (cachedPassword == null) {
                cachedPassword = promptPasswordWithRetry();
            }

            try (FileInputStream fis = new FileInputStream(pfxFilePath)) {
                keyStore.load(fis, cachedPassword);
            }
        } catch (IOException | GeneralSecurityException e) {
            // If load fails after retry, clear cache to force re-prompt later
            cachedPassword = null;
            keyStore = null;

            if (e instanceof UserCancelledPasswordEntryException) {
                throw (UserCancelledPasswordEntryException) e;
            }

            throw new KeyStoreInitializationException("Failed to load PKCS12 keystore", e);
        }
    }

    /**
     * Prompt for password up to MAX_PIN_ATTEMPTS times
     */
    private char[] promptPasswordWithRetry() throws UserCancelledPasswordEntryException {
        for (int attempt = 1; attempt <= MAX_PIN_ATTEMPTS; attempt++) {
            String message = (attempt == 1)
                    ? "Enter PFX Password"
                    : String.format("Incorrect password. Attempt %d of %d", attempt, MAX_PIN_ATTEMPTS);

            char[] pwd = showPasswordPrompt(message, attempt != 1);

            // Try loading a temporary KeyStore to validate password
            if (validatePassword(pfxFilePath, pwd)) {
                return pwd;
            }

            if (attempt == MAX_PIN_ATTEMPTS) {
                throw new UserCancelledPasswordEntryException("Maximum password attempts exceeded. Aborting operation.");
            }
        }
        throw new UserCancelledPasswordEntryException("Password entry cancelled.");
    }

    /**
     * Shows the reusable PasswordDialog
     */
    private char[] showPasswordPrompt(String message, boolean showError) throws UserCancelledPasswordEntryException {
        PasswordDialog dialog = new PasswordDialog(
                PdfViewerMain.INSTANCE,
                "Authentication Required",
                message,
                "Enter password",
                "Open",
                "Cancel"
        );

        // Simple validator: non-empty
        dialog.setValidator(value -> !value.trim().isEmpty());

        if (showError) {
            dialog.showInvalidMessage("Invalid password — please try again.");
        }

        dialog.setVisible(true); // Blocks until closed

        if (!dialog.isConfirmed()) {
            throw new UserCancelledPasswordEntryException("User cancelled password input.");
        }

        return dialog.getValue().toCharArray();
    }

    private boolean validatePassword(String pfxPath, char[] password) {
        try (FileInputStream fis = new FileInputStream(pfxPath)) {
            KeyStore tempKs = KeyStore.getInstance("PKCS12", provider);
            tempKs.load(fis, password);
            return true; // password works
        } catch (Exception e) {
            return false; // wrong password
        }
    }

    private String getSigningAlias() throws KeyStoreException {
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias; // Found alias with private key
            }
        }
        throw new KeyStoreException("No private key entry found in keystore.");
    }


    @Override
    public String getProvider() {
        return provider.getName();
    }

    @Override
    public PrivateKey getPrivateKey() throws KeyStoreInitializationException, CertificateNotFoundException,
            PrivateKeyAccessException, KeyStoreException, UserCancelledPasswordEntryException {
        if (privateKey != null) return privateKey;

        loadKeyStore();
        String alias = getSigningAlias();
        try {
            Key key = keyStore.getKey(alias, cachedPassword);
            if (!(key instanceof PrivateKey)) {
                throw new PrivateKeyAccessException("No private key entry found in keystore.");
            }
            privateKey = (PrivateKey) key;
            return privateKey;
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException e) {
            throw new PrivateKeyAccessException("Unable to access private key.", e);
        }
    }

    @Override
    public X509Certificate getCertificate() throws KeyStoreInitializationException, CertificateNotFoundException,
            KeyStoreException, UserCancelledPasswordEntryException {
        if (certificate != null) return certificate;

        loadKeyStore();
        String alias = getSigningAlias();
        Certificate cert = keyStore.getCertificate(alias);
        if (!(cert instanceof X509Certificate)) {
            throw new CertificateNotFoundException("Certificate not found in keystore.");
        }

        certificate = (X509Certificate) cert;
        return certificate;
    }

    @Override
    public Certificate[] getCertificateChain() throws KeyStoreException {
        if (certificateChain != null) return certificateChain;

        String alias = getSigningAlias();
        Certificate[] chain = keyStore.getCertificateChain(alias);

        if (chain == null || chain.length == 0) {
            // Fallback: try to get just the single certificate
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                throw new KeyStoreException("No certificate found for alias: " + alias);
            }

            System.err.println("[WARN] Certificate chain is missing. Using only signer certificate.");
            chain = new Certificate[]{cert};
        }

        certificateChain = chain;
        return chain;
    }

    /**
     * Clears the current session (forces password prompt on next operation)
     */
    public void clearSession() {
        keyStore = null;
        privateKey = null;
        certificate = null;
        certificateChain = null;
        cachedPassword = null;
    }
}
