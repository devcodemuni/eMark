package com.codemuni.core.keyStoresProvider;

import com.codemuni.core.exception.CertificateNotFoundException;
import com.codemuni.core.exception.IncorrectPINException;
import com.codemuni.core.exception.KeyStoreInitializationException;
import com.codemuni.core.exception.MaxPinAttemptsExceededException;
import com.codemuni.core.exception.NotADigitalSignatureException;
import com.codemuni.core.exception.PKCS11OperationException;
import com.codemuni.core.exception.PrivateKeyAccessException;
import com.codemuni.core.exception.TokenOrHsmNotFoundException;
import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.core.model.KeystoreAndCertificateInfo;
import com.codemuni.gui.SmartCardCallbackHandler;

import com.codemuni.utils.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import sun.security.pkcs11.SunPKCS11;
import sun.security.pkcs11.wrapper.*;

import javax.security.auth.callback.PasswordCallback;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PKCS#11 KeyStore provider implementation with persistent session support.
 * PIN is only requested once per app lifecycle unless explicitly logged out/reset.
 */
public final class PKCS11KeyStoreProvider implements KeyStoreProvider {

    private static final Log LOG = LogFactory.getLog(PKCS11KeyStoreProvider.class);

    private static final String PKCS11_TYPE = "PKCS11";
    private static final Provider BC_PROVIDER = new BouncyCastleProvider();
    private final Map<String, String> serialToAlias = new ConcurrentHashMap<>();
    private List<String> pkcs11LibPathsToBeLoadPublicKey;
    private volatile SunPKCS11 sunPKCS11Provider;
    private volatile KeyStore keyStore;


    private String certificateSerialNumber; // hex string
    private String tokenSerialNumber;       // token info serial string
    private String pkcs11LibPath;           // PKCS#11 library path
    private String keystoreName = null;

    public PKCS11KeyStoreProvider(List<String> pkcs11LibPaths, String keystoreName) {
        this.pkcs11LibPathsToBeLoadPublicKey = Objects.requireNonNull(pkcs11LibPaths);
        this.keystoreName = Objects.requireNonNull(keystoreName,
                "Keystore initialization failed: keystore name is not set.");
    }

    public PKCS11KeyStoreProvider(List<String> pkcs11LibPaths) {
        this.pkcs11LibPathsToBeLoadPublicKey = Objects.requireNonNull(pkcs11LibPaths);
    }

    public PKCS11KeyStoreProvider(String keystoreName) {
        this.keystoreName = Objects.requireNonNull(keystoreName,
                "Keystore initialization failed: keystore name is not set.");
    }

    public PKCS11KeyStoreProvider() {
    }

    public void setPkcs11LibPathsToBeLoadPublicKey(List<String> pkcs11LibPathsToBeLoadPublicKey) {
        this.pkcs11LibPathsToBeLoadPublicKey = pkcs11LibPathsToBeLoadPublicKey;
    }

    private static long findSlotByTokenSerial(String libPath, String desiredSerial)
            throws IncorrectPINException, TokenOrHsmNotFoundException, KeyStoreInitializationException {
        try {
            PKCS11 pkcs11 = PKCS11.getInstance(libPath, "C_GetFunctionList", null, false);
            for (long slot : pkcs11.C_GetSlotList(true)) {
                CK_TOKEN_INFO info = pkcs11.C_GetTokenInfo(slot);
                String serial = new String(info.serialNumber).trim();
                if (serial.equalsIgnoreCase(desiredSerial.trim())) {
                    return slot;
                }
            }
            throw new TokenOrHsmNotFoundException("Token with serial " + desiredSerial + " not found in library: " + libPath);
        } catch (PKCS11Exception e) {
            LOG.error("PKCS#11 error: " + e.getMessage(), e);
            throw translatePKCS11Error(e);
        } catch (IOException e) {
            throw new KeyStoreInitializationException("Unable to load PKCS#11 library from path: " + libPath, e);
        }
    }

    private static PKCS11OperationException translatePKCS11Error(PKCS11Exception e) throws IncorrectPINException {
        int code = (int) e.getErrorCode();
        switch (code) {
            case (int) PKCS11Constants.CKR_SLOT_ID_INVALID:
                return new PKCS11OperationException("Invalid slot ID.", e);
            case (int) PKCS11Constants.CKR_TOKEN_NOT_PRESENT:
                return new PKCS11OperationException("No token present in the slot.", e);
            case (int) PKCS11Constants.CKR_TOKEN_NOT_RECOGNIZED:
                return new PKCS11OperationException("Unrecognized token in slot.", e);
            case (int) PKCS11Constants.CKR_PIN_INCORRECT:
                throw new IncorrectPINException("Incorrect PIN.", e);
            case (int) PKCS11Constants.CKR_DEVICE_REMOVED:
                return new PKCS11OperationException("Cryptographic device removed during operation.", e);
            default:
                return new PKCS11OperationException("PKCS#11 error: " + e.getMessage(), e);
        }
    }

    private static void clearPassword(PasswordCallback passwordCallback) {
        if (passwordCallback.getPassword() != null) {
            Arrays.fill(passwordCallback.getPassword(), '\0');
        }
        passwordCallback.clearPassword();
    }

    // Add these helpers anywhere in the class (e.g., near other helpers)
    private static Throwable rootCause(Throwable t) {
        Throwable cur = t, next;
        while (cur != null && (next = cur.getCause()) != null && next != cur) cur = next;
        return cur;
    }

    // -------------------- Internal Helpers --------------------

    private static PKCS11Exception findPkcs11Cause(Throwable t) {
        while (t != null) {
            if (t instanceof PKCS11Exception) return (PKCS11Exception) t;
            t = t.getCause();
        }
        return null;
    }

    private static boolean isIncorrectPin(Throwable t) {
        PKCS11Exception ex = findPkcs11Cause(t);
        return ex != null && ex.getErrorCode() == PKCS11Constants.CKR_PIN_INCORRECT;
    }

    private static boolean isPinLocked(Throwable t) {
        PKCS11Exception ex = findPkcs11Cause(t);
        return ex != null && ex.getErrorCode() == PKCS11Constants.CKR_PIN_LOCKED;
    }

    // -------------------- Public API --------------------

    public void setCertificateSerialNumber(String certificateSerialNumber) {
        this.certificateSerialNumber = certificateSerialNumber;
    }

    public void setTokenSerialNumber(String tokenSerialNumber) {
        this.tokenSerialNumber = tokenSerialNumber;
    }

    public void setPkcs11LibPath(String pkcs11LibPath) {
        this.pkcs11LibPath = pkcs11LibPath;
    }

    @Override
    public List<KeystoreAndCertificateInfo> loadCertificates() {
        if (pkcs11LibPathsToBeLoadPublicKey.isEmpty()) return Collections.emptyList();

        List<KeystoreAndCertificateInfo> result = new ArrayList<>();
        for (String libPath : pkcs11LibPathsToBeLoadPublicKey) {
            try {
                enumerateLibraryCertificates(libPath, result::add);
            } catch (Exception ex) {
                LOG.warn("Unable to read certificates from PKCS#11 library: " + libPath, ex);
            }
        }
        return result;
    }

    /**
     * Explicit login method — prompts for PIN only if not already logged in.
     */
    private void enumerateLibraryCertificates(String libPath, java.util.function.Consumer<KeystoreAndCertificateInfo> consumer)
            throws Exception {

        if (!FileUtils.isFileExist(libPath)) {
            System.err.println("[WARN] PKCS#11 library not found at: " + libPath + " — skipping.");
            return;
        }


        PKCS11 pkcs11 = PKCS11.getInstance(libPath, "C_GetFunctionList", null, false);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        for (long slot : pkcs11.C_GetSlotList(true)) {
            long session = 0L;
            try {
                CK_TOKEN_INFO tokenInfo = pkcs11.C_GetTokenInfo(slot);
                String tokenSerial = new String(tokenInfo.serialNumber).trim();

                session = pkcs11.C_OpenSession(slot, PKCS11Constants.CKF_SERIAL_SESSION, null, null);
                CK_ATTRIBUTE[] template = {new CK_ATTRIBUTE(PKCS11Constants.CKA_CLASS, PKCS11Constants.CKO_CERTIFICATE)};

                pkcs11.C_FindObjectsInit(session, template);
                while (true) {
                    long[] objects = pkcs11.C_FindObjects(session, 10);
                    if (objects == null || objects.length == 0) break;
                    for (long obj : objects) {
                        CK_ATTRIBUTE[] attrs = {new CK_ATTRIBUTE(PKCS11Constants.CKA_VALUE)};
                        pkcs11.C_GetAttributeValue(session, obj, attrs);
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(attrs[0].getByteArray())) {
                            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(bais);
                            consumer.accept(new KeystoreAndCertificateInfo(cert, keystoreName, tokenSerial, libPath));
                        }
                    }
                }
                pkcs11.C_FindObjectsFinal(session);
            } finally {
                if (session != 0L) {
                    try {
                        pkcs11.C_CloseSession(session);
                    } catch (Exception ignore) {
                    }
                }
            }
        }
    }


    public synchronized void login(SmartCardCallbackHandler pinHandler)
            throws IncorrectPINException, KeyStoreException, UserCancelledPasswordEntryException {

        if (keyStore != null) {
            LOG.info("Already logged in — reusing existing session.");
            return;
        }

        Objects.requireNonNull(pkcs11LibPath, "PKCS#11 library path must be set.");
        Objects.requireNonNull(tokenSerialNumber, "Token serial number must be set.");

        long slot = findSlotByTokenSerial(pkcs11LibPath, tokenSerialNumber);

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BC_PROVIDER);
        }

        cleanupProvider();

        String config = String.format(Locale.ROOT,
                "name=PKCS11-%d\nlibrary=%s\nslot=%d", slot, pkcs11LibPath, slot);

        sunPKCS11Provider = new SunPKCS11(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
        Security.addProvider(sunPKCS11Provider);

        try {
            KeyStore.Builder builder = KeyStore.Builder.newInstance(
                    "PKCS11", null, new KeyStore.CallbackHandlerProtection(pinHandler));
            this.keyStore = builder.getKeyStore();
            LOG.info("Login successful — session will remain active until logout() or reset().");
        } catch (KeyStoreException e) {
            handleLoginException(e);
        }
    }

    private void handleLoginException(KeyStoreException e)
            throws IncorrectPINException, UserCancelledPasswordEntryException, KeyStoreException {

        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof javax.security.auth.login.FailedLoginException) {
                Throwable rootCause = cause.getCause();
                if (rootCause instanceof PKCS11Exception) {
                    String msg = rootCause.getMessage();
                    if (msg.contains("CKR_PIN_INCORRECT")) {
                        throw new IncorrectPINException("The provided PIN is incorrect.", e);
                    }
                    if (msg.contains("CKR_CANCEL")) {
                        throw new UserCancelledPasswordEntryException("PIN entry was cancelled by the user.", e);
                    }
                }
            }
            if (cause instanceof PKCS11Exception &&
                    cause.getMessage().contains("CKR_CANCEL")) {
                throw new UserCancelledPasswordEntryException("PIN entry was cancelled by the user.", e);
            }
            cause = cause.getCause();
        }
        throw e; // Unhandled — rethrow
    }

    private void cleanupProvider() {
        if (sunPKCS11Provider != null) {
            try {
                Security.removeProvider(sunPKCS11Provider.getName());
                LOG.debug("Removed previous PKCS#11 provider: " + sunPKCS11Provider.getName());
            } catch (Exception e) {
                LOG.warn("Failed to remove PKCS#11 provider: " + e.getMessage(), e);
            } finally {
                sunPKCS11Provider = null;
            }
        }
    }

    public synchronized void loadKeyStore(SmartCardCallbackHandler handler)
            throws KeyStoreException, UserCancelledPasswordEntryException {

        int attempts = 0;
        final int MAX_ATTEMPTS = 3;

        while (true) {
            try {
                login(handler);
                return; // success
            } catch (IncorrectPINException e) {
                attempts++;
                LOG.warn("Incorrect PIN entered (attempt " + attempts + "/" + MAX_ATTEMPTS + ").");

                // If user already cancelled in the handler, stop immediately
                if (handler.isCancelled()) {
                    throw new UserCancelledPasswordEntryException("PIN entry was cancelled by the user.", e);
                }

                if (attempts >= MAX_ATTEMPTS) {
                    throw new MaxPinAttemptsExceededException("Maximum PIN attempts reached", e);
                }

                // Update dialog status instead of creating new handler
                String message = String.format(
                        "<html><body><p style='color:red'>Authentication failed. (%d of %d left)</p></body></html>",
                        (MAX_ATTEMPTS - attempts),
                        MAX_ATTEMPTS
                );
                handler.setStatusMessage(message);

            } catch (UserCancelledPasswordEntryException e) {
                LOG.info("User cancelled PIN entry.");
                throw e; // don’t retry — user explicitly cancelled
            }
        }
    }


    /**
     * Explicit logout — closes session and clears sensitive data.
     */
    public synchronized void logout() {
        try {
            if (sunPKCS11Provider != null) {
                sunPKCS11Provider.logout();
                Security.removeProvider(sunPKCS11Provider.getName());
            }
        } catch (Exception ignored) {
        }
        keyStore = null;
        sunPKCS11Provider = null;
        serialToAlias.clear();
        LOG.info("Logged out from token — session closed.");
    }

    public synchronized void reset() {
        logout();
        certificateSerialNumber = null;
        tokenSerialNumber = null;
        pkcs11LibPath = null;
    }

    @Override
    public String getProvider() {
        return (sunPKCS11Provider != null) ? sunPKCS11Provider.getName() : null;
    }

    @Override
    public PrivateKey getPrivateKey()
            throws KeyStoreInitializationException, CertificateNotFoundException,
            PrivateKeyAccessException, NotADigitalSignatureException, KeyStoreException {

        if (keyStore == null) {
            throw new KeyStoreInitializationException("KeyStore not loaded. Call login() first.");
        }
        String alias = getAliasForCertificateSerial();
        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);
            if (privateKey == null) {
                throw new PrivateKeyAccessException("No private key for alias: " + alias);
            }
            return privateKey;
        } catch (UnrecoverableKeyException e) {
            throw new PrivateKeyAccessException("Unable to access private key: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new KeyStoreInitializationException("Unsupported algorithm: " + e.getMessage(), e);
        }
    }

    @Override
    public X509Certificate getCertificate()
            throws KeyStoreInitializationException, CertificateNotFoundException,
            NotADigitalSignatureException, KeyStoreException {

        if (keyStore == null) {
            throw new KeyStoreInitializationException("KeyStore not loaded. Call login() first.");
        }
        Certificate cert = keyStore.getCertificate(getAliasForCertificateSerial());
        if (!(cert instanceof X509Certificate)) {
            throw new NotADigitalSignatureException("Certificate is not X.509.");
        }
        return (X509Certificate) cert;
    }

    @Override
    public Certificate[] getCertificateChain() throws KeyStoreException {
        if (keyStore == null) {
            throw new KeyStoreException("KeyStore not loaded. Call login() first.");
        }
        Certificate[] chain = keyStore.getCertificateChain(getAliasForCertificateSerial());
        if (chain == null || chain.length == 0) {
            throw new KeyStoreException("No certificate chain found.");
        }
        return chain;
    }

    private String getAliasForCertificateSerial() {
        if (certificateSerialNumber == null) {
            throw new IllegalArgumentException("Certificate serial number must be set first.");
        }
        return serialToAlias.computeIfAbsent(certificateSerialNumber, serial -> {
            try {
                Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    Certificate cert = keyStore.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        String serialHex = ((X509Certificate) cert).getSerialNumber().toString(16);
                        if (serialHex.equalsIgnoreCase(serial)) {
                            return alias;
                        }
                    }
                }
                return null;
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
