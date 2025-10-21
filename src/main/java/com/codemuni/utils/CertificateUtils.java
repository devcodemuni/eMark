package com.codemuni.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

// Helper methods for working with X.509 certificates
public final class CertificateUtils {

    private static final Log log = LogFactory.getLog(CertificateUtils.class);

    // Can't create instances of this class
    private CertificateUtils() {
        throw new AssertionError("Cannot instantiate CertificateUtils");
    }

    // Where the certificate sits in the chain
    public enum CertificateRole {
        END_ENTITY("End Entity"),
        INTERMEDIATE_CA("Intermediate CA"),
        ROOT_CA("Root CA");

        private final String displayName;

        CertificateRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Figure out if this cert is an end entity, intermediate CA, or root CA
    public static CertificateRole determineCertificateRole(X509Certificate cert) {
        if (cert == null) {
            return CertificateRole.END_ENTITY;
        }

        boolean isSelfSigned = isSelfSigned(cert);
        boolean isCA = isCA(cert);

        if (isCA) {
            return isSelfSigned ? CertificateRole.ROOT_CA : CertificateRole.INTERMEDIATE_CA;
        } else {
            return CertificateRole.END_ENTITY;
        }
    }

    // Check if cert is self-signed (subject == issuer)
    // Uses X500Principal for proper DN comparison instead of string comparison
    private static boolean isSelfSigned(X509Certificate cert) {
        if (cert == null) return false;

        // Use X500Principal for normalized DN comparison
        boolean dnMatch = cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());

        if (!dnMatch) {
            return false;
        }

        // Verify signature to confirm it's truly self-signed
        try {
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception e) {
            log.debug("Certificate has matching subject/issuer but signature verification failed", e);
            return false;
        }
    }

    // Check if cert is a CA using Basic Constraints (-1 = not CA, >= 0 = CA)
    private static boolean isCA(X509Certificate cert) {
        if (cert == null) return false;
        int basicConstraints = cert.getBasicConstraints();
        return basicConstraints >= 0;
    }

    // Get key size from a certificate
    public static int getKeySize(X509Certificate cert) {
        if (cert == null) return 0;
        return getKeySize(cert.getPublicKey());
    }

    // Get key size in bits - works for RSA, DSA, and EC keys
    public static int getKeySize(java.security.PublicKey publicKey) {
        if (publicKey == null) return 0;

        try {
            if (publicKey instanceof java.security.interfaces.RSAPublicKey) {
                java.security.interfaces.RSAPublicKey rsaKey =
                    (java.security.interfaces.RSAPublicKey) publicKey;
                return rsaKey.getModulus().bitLength();
            } else if (publicKey instanceof java.security.interfaces.DSAPublicKey) {
                java.security.interfaces.DSAPublicKey dsaKey =
                    (java.security.interfaces.DSAPublicKey) publicKey;
                return dsaKey.getParams().getP().bitLength();
            } else if (publicKey instanceof java.security.interfaces.ECPublicKey) {
                java.security.interfaces.ECPublicKey ecKey =
                    (java.security.interfaces.ECPublicKey) publicKey;
                return ecKey.getParams().getOrder().bitLength();
            }
        } catch (Exception e) {
            log.warn("Failed to get key size", e);
        }
        return 0;
    }

    // Convert key usage flags to readable strings like "Digital Signature", "Key Cert Sign", etc.
    public static List<String> getKeyUsageStrings(X509Certificate cert) {
        List<String> usages = new ArrayList<>();
        if (cert == null) return usages;

        try {
            boolean[] keyUsage = cert.getKeyUsage();
            if (keyUsage == null) return usages;

            String[] usageNames = {
                "Digital Signature", "Non Repudiation", "Key Encipherment",
                "Data Encipherment", "Key Agreement", "Key Cert Sign",
                "CRL Sign", "Encipher Only", "Decipher Only"
            };

            for (int i = 0; i < keyUsage.length && i < usageNames.length; i++) {
                if (keyUsage[i]) {
                    usages.add(usageNames[i]);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get key usage", e);
        }

        return usages;
    }

    // Convert extended key usage OIDs to readable strings like "Code Signing", "Time Stamping"
    public static List<String> getExtendedKeyUsageStrings(X509Certificate cert) {
        List<String> usages = new ArrayList<>();
        if (cert == null) return usages;

        try {
            List<String> extKeyUsage = cert.getExtendedKeyUsage();
            if (extKeyUsage == null || extKeyUsage.isEmpty()) {
                return usages;
            }

            // Map OIDs to friendly names
            java.util.Map<String, String> oidMap = new java.util.HashMap<>();
            oidMap.put("1.3.6.1.5.5.7.3.1", "TLS Web Server Authentication");
            oidMap.put("1.3.6.1.5.5.7.3.2", "TLS Web Client Authentication");
            oidMap.put("1.3.6.1.5.5.7.3.3", "Code Signing");
            oidMap.put("1.3.6.1.5.5.7.3.4", "Email Protection");
            oidMap.put("1.3.6.1.5.5.7.3.8", "Time Stamping");
            oidMap.put("1.3.6.1.5.5.7.3.9", "OCSP Signing");

            for (String oid : extKeyUsage) {
                usages.add(oidMap.getOrDefault(oid, oid));
            }
        } catch (Exception e) {
            log.warn("Failed to get extended key usage", e);
        }

        return usages;
    }
}
