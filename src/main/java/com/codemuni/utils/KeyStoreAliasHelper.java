package com.codemuni.utils;

import com.codemuni.core.exception.CertificateNotFoundException;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Utility class for common KeyStore alias operations.
 * Consolidates duplicate alias-finding logic from multiple keystore providers.
 */
public final class KeyStoreAliasHelper {

    // Prevent instantiation
    private KeyStoreAliasHelper() {
    }

    /**
     * Finds a keystore alias by matching the certificate serial number in hexadecimal format.
     * This method iterates through all aliases in the keystore and compares serial numbers.
     *
     * @param keyStore  The KeyStore to search
     * @param serialHex The certificate serial number in hexadecimal format
     * @return The matching alias
     * @throws CertificateNotFoundException if no certificate with the specified serial is found
     * @throws KeyStoreException            if there's an error accessing the keystore
     */
    public static String findAliasBySerialHex(KeyStore keyStore, String serialHex)
            throws CertificateNotFoundException, KeyStoreException {

        if (keyStore == null) {
            throw new IllegalArgumentException("KeyStore cannot be null");
        }
        if (serialHex == null || serialHex.isEmpty()) {
            throw new IllegalArgumentException("Serial number cannot be null or empty");
        }

        // Normalize serial hex (remove leading zero if present)
        String normalizedSerial = serialHex.startsWith("0") ? serialHex.substring(1) : serialHex;

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = keyStore.getCertificate(alias);

            if (cert instanceof X509Certificate) {
                String certSerialHex = ((X509Certificate) cert).getSerialNumber().toString(16);
                if (certSerialHex.equalsIgnoreCase(normalizedSerial) ||
                        certSerialHex.equalsIgnoreCase(serialHex)) {
                    return alias;
                }
            }
        }

        throw new CertificateNotFoundException(
                String.format("Certificate with serial number %s not found in keystore", serialHex)
        );
    }

    /**
     * Finds the first alias in the keystore that contains a private key entry.
     * This is useful for PFX/PKCS12 keystores that typically contain a single signing certificate.
     *
     * @param keyStore The KeyStore to search
     * @return The first alias with a private key entry
     * @throws KeyStoreException if no private key entry is found or on keystore access error
     */
    public static String findFirstPrivateKeyAlias(KeyStore keyStore) throws KeyStoreException {
        if (keyStore == null) {
            throw new IllegalArgumentException("KeyStore cannot be null");
        }

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }

        throw new KeyStoreException("No private key entry found in keystore");
    }

    /**
     * Checks if a keystore contains a certificate with the specified serial number.
     *
     * @param keyStore  The KeyStore to search
     * @param serialHex The certificate serial number in hexadecimal format
     * @return true if the certificate exists, false otherwise
     * @throws KeyStoreException if there's an error accessing the keystore
     */
    public static boolean containsCertificateWithSerial(KeyStore keyStore, String serialHex)
            throws KeyStoreException {
        try {
            findAliasBySerialHex(keyStore, serialHex);
            return true;
        } catch (CertificateNotFoundException e) {
            return false;
        }
    }
}
