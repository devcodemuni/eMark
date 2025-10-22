package com.codemuni.core.keyStoresProvider;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

public interface X509CertificateValidator {

    boolean isExpired(X509Certificate certificate);

    boolean isNotYetValid(X509Certificate certificate);

    boolean isDigitalSignatureAllowed(X509Certificate certificate);

    boolean isEncryptionAllowed(X509Certificate certificate);

    boolean isSignatureAlgorithmSecure(X509Certificate certificate);

    boolean isSelfSigned(X509Certificate certificate);

    boolean isEndEntity(X509Certificate certificate);

    boolean isRevoked(X509Certificate certificate); // validate revocation status of the certificate using OCSP

    boolean isChainValid(List<X509Certificate> chain, Set<X509Certificate> trustedRoots, int maxChainLength);

    /**
     * Validates a certificate chain with separate root and intermediate certificates.
     * This is the proper way to validate chains according to PKI standards.
     *
     * @param chain              The certificate chain to validate
     * @param trustedRoots       Root certificates (self-signed trust anchors)
     * @param intermediateCerts  Intermediate certificates for chain building
     * @param maxChainLength     Maximum allowed chain length
     * @return true if chain is valid, false otherwise
     */
    boolean isChainValid(List<X509Certificate> chain, Set<X509Certificate> trustedRoots,
                         Set<X509Certificate> intermediateCerts, int maxChainLength);
}
