package com.codemuni.core.model;

import java.security.cert.X509Certificate;
import java.util.Objects;

public class KeystoreAndCertificateInfo {
    private final String keystoreName;
    private final String tokenSerial;
    private final String pkcs11LibPath;

    public String getPkcs11LibPath() {
        return pkcs11LibPath;
    }

    private final String pfxFilePath;

    public String getPfxFilePath() {
        return pfxFilePath;
    }

    private X509Certificate certificate;

    public String getTokenSerial() {
        return tokenSerial;
    }

    public String getKeystoreName() {
        return keystoreName;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(X509Certificate certificate) {
        this.certificate = certificate;
    }

    // Constructor for PKCS11 and Windows keystores
    public KeystoreAndCertificateInfo(X509Certificate certificate, String keystoreName, String tokenSerial, String pkcs11LibPath) {
        this.certificate = Objects.requireNonNull(certificate, "certificate must not be null");
        this.keystoreName = keystoreName;
        this.tokenSerial = tokenSerial;
        this.pkcs11LibPath = pkcs11LibPath;
        this.pfxFilePath = null;
    }

    // Constructor for PFX keystore (certificate loaded later)
    public KeystoreAndCertificateInfo(String keystoreName, String pfxFilePath) {
        this.keystoreName = keystoreName;
        this.pfxFilePath = Objects.requireNonNull(pfxFilePath, "pfxFile must not be null");
        this.tokenSerial = null;
        this.pkcs11LibPath = null;
        this.certificate = null; // Will be set later
    }

    public String getCertificateSerial() {
        return certificate != null ? certificate.getSerialNumber().toString(16) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeystoreAndCertificateInfo)) return false;
        KeystoreAndCertificateInfo that = (KeystoreAndCertificateInfo) o;

        // If both have certificates, compare them
        if (this.certificate != null && that.certificate != null) {
            return Objects.equals(this.certificate.getSerialNumber(), that.certificate.getSerialNumber()) &&
                    Objects.equals(this.certificate.getIssuerX500Principal(), that.certificate.getIssuerX500Principal());
        }

        // Otherwise, compare by keystore name + PFX file (for PFX)
        return Objects.equals(this.keystoreName, that.keystoreName) &&
                Objects.equals(this.pfxFilePath, that.pfxFilePath);
    }

    @Override
    public int hashCode() {
        if (certificate != null) {
            return Objects.hash(certificate.getSerialNumber(), certificate.getIssuerX500Principal());
        }
        return Objects.hash(keystoreName, pfxFilePath);
    }

    @Override
    public String toString() {
        if (certificate != null) {
            return "CertificateInfo{" +
                    "serial=" + getCertificateSerial() +
                    ", issuer=" + certificate.getIssuerX500Principal().getName() +
                    ", keystoreName='" + keystoreName + '\'' +
                    ", tokenSerial='" + tokenSerial + '\'' +
                    ", pkcs11Path='" + pkcs11LibPath + '\'' +
                    '}';
        } else {
            return "CertificateInfo{pfxFile='" + pfxFilePath + "', keystoreName='" + keystoreName + "'}";
        }
    }
}
