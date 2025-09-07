package com.codemuni.core.keyStoresProvider;

import com.codemuni.exceptions.*;
import com.codemuni.model.KeystoreAndCertificateInfo;

import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.List;

public interface KeyStoreProvider {
    List<KeystoreAndCertificateInfo> loadCertificates();

    public String getProvider() throws Exception;
    public PrivateKey getPrivateKey() throws KeyStoreInitializationException, CertificateNotFoundException, PrivateKeyAccessException, CertificateExpiredException, NotADigitalSignatureException, KeyStoreException, UserCancelledPasswordEntryException;
    public X509Certificate getCertificate() throws KeyStoreInitializationException, CertificateNotFoundException, CertificateExpiredException, NotADigitalSignatureException, KeyStoreException, UserCancelledPasswordEntryException;
    public Certificate[] getCertificateChain() throws KeyStoreException, CertificateExpiredException, NotADigitalSignatureException;


}
