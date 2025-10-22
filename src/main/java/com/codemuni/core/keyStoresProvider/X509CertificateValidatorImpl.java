package com.codemuni.core.keyStoresProvider;

import java.security.PublicKey;
import java.security.cert.*;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class X509CertificateValidatorImpl implements X509CertificateValidator {

    @Override
    public boolean isExpired(X509Certificate certificate) {
        return new Date().after(certificate.getNotAfter());
    }

    @Override
    public boolean isNotYetValid(X509Certificate certificate) {
        return new Date().before(certificate.getNotBefore());
    }

    @Override
    public boolean isDigitalSignatureAllowed(X509Certificate certificate) {
        boolean[] usage = certificate.getKeyUsage();
        return usage != null && usage.length > 0 && usage[0]; // digitalSignature
    }

    @Override
    public boolean isEncryptionAllowed(X509Certificate certificate) {
        boolean[] usage = certificate.getKeyUsage();
        return usage != null && usage.length > 2 && usage[2]; // keyEncipherment
    }

    @Override
    public boolean isSignatureAlgorithmSecure(X509Certificate certificate) {
        String sigAlg = certificate.getSigAlgName().toUpperCase();
        return !(sigAlg.contains("SHA1") || sigAlg.contains("MD5"));
    }

    @Override
    public boolean isSelfSigned(X509Certificate certificate) {
        try {
            PublicKey key = certificate.getPublicKey();
            certificate.verify(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isEndEntity(X509Certificate certificate) {
        return certificate.getBasicConstraints() == -1;
    }

    @Override
    public boolean isRevoked(X509Certificate certificate) {
        return true;
    }

    @Override
    public boolean isChainValid(List<X509Certificate> chain, Set<X509Certificate> trustedRoots, int maxChainLength) {
        // Delegate to the new method with empty intermediate certificates set
        return isChainValid(chain, trustedRoots, new HashSet<>(), maxChainLength);
    }

    @Override
    public boolean isChainValid(List<X509Certificate> chain, Set<X509Certificate> trustedRoots,
                                Set<X509Certificate> intermediateCerts, int maxChainLength) {
        try {
            if (chain == null || chain.isEmpty() || chain.size() > maxChainLength) return false;

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            CertPath certPath = factory.generateCertPath(chain);

            // Create trust anchors from root certificates only
            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate root : trustedRoots) {
                anchors.add(new TrustAnchor(root, null));
            }

            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false); // OCSP/CRL separately

            // Add intermediate certificates for proper chain building
            if (intermediateCerts != null && !intermediateCerts.isEmpty()) {
                try {
                    CertStore intermediateCertStore = CertStore.getInstance("Collection",
                            new CollectionCertStoreParameters(intermediateCerts));
                    params.addCertStore(intermediateCertStore);
                } catch (Exception e) {
                    // Continue without intermediate cert store
                }
            }

            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certPath, params);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
