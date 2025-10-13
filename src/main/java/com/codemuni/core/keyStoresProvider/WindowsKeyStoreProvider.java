package com.codemuni.core.keyStoresProvider;


import com.codemuni.core.exception.CertificateNotFoundException;
import com.codemuni.core.exception.KeyStoreInitializationException;
import com.codemuni.core.exception.PrivateKeyAccessException;
import com.codemuni.core.model.KeystoreAndCertificateInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static com.codemuni.utils.AppConstants.WIN_KEY_STORE;

public class WindowsKeyStoreProvider extends X509CertificateValidatorImpl implements KeyStoreProvider {

    private final KeyStore keyStore;

    private final String provider = "SunMSCAPI";
    private final BouncyCastleProvider cryptoProvider = new BouncyCastleProvider();
    private String serialHex;

    public WindowsKeyStoreProvider() throws KeyStoreInitializationException {
        try {
            Security.addProvider(cryptoProvider);
            this.keyStore = KeyStore.getInstance("Windows-MY", provider);
            this.keyStore.load(null, null);
        } catch (KeyStoreException | NoSuchProviderException e) {
            throw new KeyStoreInitializationException("Failed to initialize KeyStore: " + e.getMessage(), e);
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new KeyStoreInitializationException("Failed to load KeyStore: " + e.getMessage(), e);
        }
    }

    public String getProvider() {
        return provider;
    }

    public void setSerialHex(String serialHex) {
        this.serialHex = serialHex;
    }

    @Override
    public List<KeystoreAndCertificateInfo> loadCertificates() {
        List<KeystoreAndCertificateInfo> result = new ArrayList<>();

        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();

                if (!keyStore.isKeyEntry(alias)) continue;

                Certificate cert = keyStore.getCertificate(alias);
                if (!(cert instanceof X509Certificate)) continue;

                X509Certificate x509Cert = (X509Certificate) cert;

                result.add(new KeystoreAndCertificateInfo(x509Cert, WIN_KEY_STORE, null, null));
            }

        } catch (KeyStoreException e) {
            System.err.println("Error accessing Windows keystore: " + e.getMessage());
        }

        return result;
    }


    private String findAliasByCertSerial(String serialHex) throws CertificateNotFoundException, KeyStoreInitializationException {
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    String serial = ((X509Certificate) cert).getSerialNumber().toString(16);
                    String providedSerialHex = serialHex.startsWith("0") ? serialHex.substring(1) : serialHex;

                    if (serial.equalsIgnoreCase(providedSerialHex)) {
                        return alias; // Found matching alias, return immediately
                    }
                }
            }
            throw new CertificateNotFoundException(String.format("Certificate with serial %s not found", serialHex));
        } catch (KeyStoreException e) {
            throw new KeyStoreInitializationException("Error searching for alias", e);
        }
    }


    public PrivateKey getPrivateKey() throws KeyStoreInitializationException, CertificateNotFoundException, PrivateKeyAccessException {
        try {
            String alias = findAliasByCertSerial(serialHex);
            return (PrivateKey) keyStore.getKey(alias, null);
        } catch (UnrecoverableKeyException e) {
            throw new PrivateKeyAccessException("Invalid PIN or access denied to private key", e);
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            throw new KeyStoreInitializationException("Failed to access private key", e);
        }
    }

    public X509Certificate getCertificate() throws KeyStoreInitializationException, CertificateNotFoundException {
        try {
            String alias = findAliasByCertSerial(serialHex);
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                return (X509Certificate) cert;
            } else {
                throw new CertificateNotFoundException("Certificate not found or not X509");
            }
        } catch (KeyStoreException e) {
            throw new KeyStoreInitializationException("Failed to fetch certificate: " + e.getMessage(), e);
        }
    }

    public X509Certificate[] getCertificateChain() throws KeyStoreException {
        String alias = findAliasByCertSerial(serialHex);
        return Arrays.stream(keyStore.getCertificateChain(alias))
                .map(cert -> (X509Certificate) cert)
                .toArray(X509Certificate[]::new);
    }
}