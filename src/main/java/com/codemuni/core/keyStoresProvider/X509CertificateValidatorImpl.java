package com.codemuni.core.keyStoresProvider;

import com.codemuni.core.keyStoresProvider.X509CertificateValidator;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.GeneralName;

import java.net.URL;
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
        try {
            if (chain == null || chain.isEmpty() || chain.size() > maxChainLength) return false;

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            CertPath certPath = factory.generateCertPath(chain);

            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate root : trustedRoots) {
                anchors.add(new TrustAnchor(root, null));
            }

            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false); // OCSP/CRL separately
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certPath, params);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper: fetch issuer certificate (should be implemented with a real store)
     */
    private X509Certificate getIssuerCertificate(X509Certificate certificate) {
        // TODO: Replace with actual issuer lookup from store or chain
        return null;
    }

    /**
     * Helper: extract OCSP URL from certificate
     */
    private URL getOCSPUrl(X509Certificate cert) {
        try {
            byte[] aiaExt = cert.getExtensionValue("1.3.6.1.5.5.7.1.1"); // Authority Info Access
            if (aiaExt == null) return null;

            ASN1InputStream aIn = new ASN1InputStream(aiaExt);
            ASN1Sequence seq = (ASN1Sequence) aIn.readObject();
            aIn.close();

            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(seq);
            for (AccessDescription ad : aia.getAccessDescriptions()) {
                if (ad.getAccessMethod().equals(AccessDescription.id_ad_ocsp)) {
                    GeneralName gn = ad.getAccessLocation();
                    if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        DERIA5String str = DERIA5String.getInstance(gn.getName());
                        return new URL(str.getString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
