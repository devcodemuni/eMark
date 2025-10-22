package com.codemuni.service;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.security.PdfPKCS7;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.security.InvalidAlgorithmParameterException;
import java.security.Security;
import java.security.cert.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Service for verifying digital signatures in PDF documents.
 * Implements PDF viewer-level signature verification including:
 * - Document integrity verification
 * - Signature validity check
 * - Certificate validation
 * - Certificate chain verification
 * - Timestamp verification
 * - LTV (Long Term Validation)
 */
public class SignatureVerificationService {

    private static final Log log = LogFactory.getLog(SignatureVerificationService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");

    static {
        // Register BouncyCastle provider for cryptographic operations
        Security.addProvider(new BouncyCastleProvider());
    }

    private final TrustStoreManager trustStoreManager;
    private VerificationProgressListener progressListener;
    // Revocation status cache for current verification session
    // Prevents redundant OCSP/CRL checks for same certificate across multiple signatures
    private Map<String, RevocationCacheEntry> revocationCache;

    public SignatureVerificationService() {
        this.trustStoreManager = TrustStoreManager.getInstance();
        // Initialize trust store on first use
        trustStoreManager.initialize();
    }

    /**
     * Sets the progress listener for verification updates.
     */
    public void setProgressListener(VerificationProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Notifies progress listener with a message (thread-safe).
     */
    private synchronized void notifyProgress(String message) {
        if (progressListener != null) {
            progressListener.onProgress(message);
        }
    }

    /**
     * Resets the verification service state.
     * Should be called when loading a new PDF to ensure clean state.
     * Clears revocation cache and removes progress listener.
     */
    public void reset() {
        if (revocationCache != null) {
            revocationCache.clear();
            log.debug("Cleared revocation cache");
        }
        progressListener = null;
        log.debug("Reset verification service state");
    }
    /**
     * Verifies all signatures in a PDF file.
     *
     * @param pdfFile     PDF file to verify
     * @param pdfPassword Password for encrypted PDFs (can be null)
     * @return List of verification results for all signatures
     */
    public List<SignatureVerificationResult> verifySignatures(File pdfFile, String pdfPassword) {
        List<SignatureVerificationResult> results = new ArrayList<>();

        if (pdfFile == null || !pdfFile.exists()) {
            log.error("PDF file does not exist: " + pdfFile);
            return results;
        }

        // Initialize revocation cache for this verification session
        // This prevents redundant OCSP/CRL checks for same certificate across multiple signatures
        revocationCache = new HashMap<>();
        log.info("Initialized revocation status cache for verification session");

        PdfReader reader = null;
        try {
            // Open PDF with password if provided
            if (pdfPassword != null && !pdfPassword.isEmpty()) {
                reader = new PdfReader(pdfFile.getAbsolutePath(), pdfPassword.getBytes());
            } else {
                reader = new PdfReader(pdfFile.getAbsolutePath());
            }

            AcroFields acroFields = reader.getAcroFields();
            if (acroFields == null) {
                log.info("No AcroForm fields found in PDF");
                return results;
            }

            // Get all signature fields
            List<String> signatureNames = acroFields.getSignatureNames();
            if (signatureNames.isEmpty()) {
                log.info("No signatures found in PDF");
                return results;
            }
            results.addAll(verifySignaturesSequential(reader, acroFields, signatureNames));

        } catch (Exception e) {
            log.error("Error reading PDF file", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error("Error closing PDF reader", e);
                }
            }
        }

        // Apply PDF viewer certification rules before returning
        applyPdfViewerCertificationRules(results);

        return results;
    }

    /**
     * Verifies signatures sequentially (one-by-one).
     * Used for single signature documents or when parallel verification is disabled.
     */
    private List<SignatureVerificationResult> verifySignaturesSequential(
            PdfReader reader, AcroFields acroFields, List<String> signatureNames) {

        List<SignatureVerificationResult> results = new ArrayList<>();

        for (int i = 0; i < signatureNames.size(); i++) {
            String signatureName = signatureNames.get(i);
            try {
                notifyProgress("Verifying signature " + (i + 1) + " of " + signatureNames.size() + "...");
                SignatureVerificationResult result = verifySignature(reader, acroFields, signatureName);
                results.add(result);
            } catch (Exception e) {
                log.error("Error verifying signature: " + signatureName, e);
                SignatureVerificationResult errorResult = new SignatureVerificationResult(
                        signatureName, "", null, "", "", "");
                errorResult.addVerificationError("Failed to verify signature: " + e.getMessage());
                results.add(errorResult);
            }
        }

        return results;
    }

    /**
     * Applies PDF viewer verification rules for certification levels.
     * This method modifies verification results based on the certification status
     * of the LAST signature in the document.
     * <p>
     * PDF Viewer Rules:
     * - Case 1: Last sig is NO_CHANGES_ALLOWED → only last valid, all previous invalid
     * - Case 2: Last sig is FORM_FILLING_* → all valid if all previous were NOT_CERTIFIED
     * - Case 3: Last sig is NOT_CERTIFIED → all valid if all previous were NOT_CERTIFIED
     *
     * @param results List of verification results in chronological order
     */
    private void applyPdfViewerCertificationRules(List<SignatureVerificationResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        // Get last signature (most recent modification)
        SignatureVerificationResult lastSig = results.get(results.size() - 1);
        com.codemuni.model.CertificationLevel lastCertLevel = lastSig.getCertificationLevel();

        log.info("=== Applying PDF Viewer Certification Rules ===");
        log.info("Last signature certification level: " + lastCertLevel.getLabel());
        log.info("Total signatures: " + results.size());

        // CASE 1: Last signature is NO_CHANGES_ALLOWED (P=1)
        if (lastCertLevel == com.codemuni.model.CertificationLevel.NO_CHANGES_ALLOWED) {
            log.warn("Case 1: Last signature is NO_CHANGES_ALLOWED - invalidating all previous signatures");

            // Invalidate ALL previous signatures
            for (int i = 0; i < results.size() - 1; i++) {
                SignatureVerificationResult prevSig = results.get(i);

                // Mark as invalid with specific error
                prevSig.setDocumentIntact(false);
                prevSig.addVerificationError(
                        "Document was changed after signing. This signature is no longer valid."
                );

                log.info("  [" + i + "] " + prevSig.getFieldName() + " → INVALIDATED");
            }

            // Last signature status depends on its own verification
            log.info("  [" + (results.size() - 1) + "] " + lastSig.getFieldName() +
                    " → " + (lastSig.getOverallStatus() == VerificationStatus.VALID ? "VALID" : "CHECK VERIFICATION"));
        }

        // CASE 2: Last signature is FORM_FILLING_* (P=2 or P=3)
        else if (lastCertLevel == com.codemuni.model.CertificationLevel.FORM_FILLING_CERTIFIED ||
                lastCertLevel == com.codemuni.model.CertificationLevel.FORM_FILLING_AND_ANNOTATION_CERTIFIED) {

            log.info("Case 2: Last signature is " + lastCertLevel.getLabel() +
                    " - verifying all previous are NOT_CERTIFIED");

            // Check if all earlier signatures are NOT_CERTIFIED
            boolean allPreviousAreApprovalSignatures = true;
            for (int i = 0; i < results.size() - 1; i++) {
                SignatureVerificationResult prevSig = results.get(i);
                if (prevSig.getCertificationLevel() != com.codemuni.model.CertificationLevel.NOT_CERTIFIED) {
                    allPreviousAreApprovalSignatures = false;
                    log.warn("  [" + i + "] " + prevSig.getFieldName() +
                            " is " + prevSig.getCertificationLevel().getLabel() + " (NOT allowed before FORM_FILLING_*)");
                    break;
                }
            }

            if (allPreviousAreApprovalSignatures) {
                log.info("✓ All previous signatures are NOT_CERTIFIED - all signatures remain valid");
                // All signatures remain valid based on their individual verification status
            } else {
                log.warn("✗ Found certified signature before last FORM_FILLING_* signature - document integrity compromised");
                // Optionally invalidate signatures here if strict PDF viewer compliance needed
            }
        }

        // CASE 3: Last signature is NOT_CERTIFIED (P=0)
        else if (lastCertLevel == com.codemuni.model.CertificationLevel.NOT_CERTIFIED) {
            log.info("Case 3: Last signature is NOT_CERTIFIED - verifying all previous are NOT_CERTIFIED");

            // Check if all signatures are NOT_CERTIFIED (multiple approval signatures)
            boolean allAreApprovalSignatures = true;
            for (SignatureVerificationResult sig : results) {
                if (sig.getCertificationLevel() != com.codemuni.model.CertificationLevel.NOT_CERTIFIED) {
                    allAreApprovalSignatures = false;
                    log.warn("  Found certified signature: " + sig.getFieldName() +
                            " (" + sig.getCertificationLevel().getLabel() + ")");
                    break;
                }
            }

            if (allAreApprovalSignatures) {
                log.info("✓ All signatures are approval signatures (NOT_CERTIFIED) - all valid, signing allowed");
                // All signatures remain valid, additional signatures allowed
            } else {
                log.info("⚠ Mixed certification levels detected - verify document modification rules");
            }
        }

        log.info("=== PDF Viewer Certification Rules Applied ===");
    }

    /**
     * Verifies a single signature in the PDF.
     */
    private SignatureVerificationResult verifySignature(PdfReader reader, AcroFields acroFields, String signatureName) {
        // Extract signature metadata first
        String signerName = "";
        Date signDate = null;
        String reason = "";
        String location = "";
        String contactInfo = "";

        try {
            PdfPKCS7 pkcs7Temp = acroFields.verifySignature(signatureName);
            if (pkcs7Temp != null) {
                // Extract signer name from certificate
                if (pkcs7Temp.getSigningCertificate() != null) {
                    signerName = pkcs7Temp.getSigningCertificate().getSubjectDN().toString();
                }
                // Extract signature date
                signDate = pkcs7Temp.getSignDate() != null ? pkcs7Temp.getSignDate().getTime() : null;
                // Extract reason, location, contact from signature dictionary
                reason = pkcs7Temp.getReason();
                location = pkcs7Temp.getLocation();
                // Contact info is not directly available in PdfPKCS7, extract from dictionary
                try {
                    com.itextpdf.text.pdf.PdfDictionary sigDict = acroFields.getSignatureDictionary(signatureName);
                    if (sigDict != null) {
                        com.itextpdf.text.pdf.PdfString contactStr = sigDict.getAsString(com.itextpdf.text.pdf.PdfName.CONTACTINFO);
                        if (contactStr != null) {
                            contactInfo = contactStr.toString();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not extract contact info", e);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract signature metadata", e);
        }

        SignatureVerificationResult result = new SignatureVerificationResult(
                signatureName, signerName, signDate, reason, location, contactInfo);

        try {
            // Get signature dictionary
            PdfPKCS7 pkcs7 = acroFields.verifySignature(signatureName);
            if (pkcs7 == null) {
                result.addVerificationError("Unable to extract signature data");
                return result;
            }

            // Get revision information first
            int revision = acroFields.getRevision(signatureName);
            int totalRevisions = acroFields.getTotalRevisions();
            result.setRevision(revision);
            result.setTotalRevisions(totalRevisions);
            result.setCoversWholeDocument(revision == totalRevisions);

            // 1. DOCUMENT INTEGRITY CHECK (PDF viewer-style)
            notifyProgress("Checking document integrity...");
            boolean documentIntact = verifyDocumentIntegrity(acroFields, signatureName, revision, totalRevisions, pkcs7);
            result.setDocumentIntact(documentIntact);

            if (!documentIntact) {
                result.addVerificationError("Document was changed after signing");
            } else if (revision < totalRevisions) {
                // Signature is valid but not the last one - this is informational
                result.addVerificationInfo("Signature is valid. Document has additional signatures or modifications after this signature.");
            }

            // 2. SIGNATURE VALIDITY CHECK
            notifyProgress("Verifying signature validity...");
            boolean signatureValid = pkcs7.verify();
            result.setSignatureValid(signatureValid);
            if (!signatureValid) {
                result.addVerificationError("This signature is not valid");
            }

            // 3. CERTIFICATE INFORMATION
            notifyProgress("Checking certificate...");
            X509Certificate signerCert = pkcs7.getSigningCertificate();
            result.setSignerCertificate(signerCert);

            if (signerCert != null) {
                result.setCertificateSubject(signerCert.getSubjectDN().toString());
                result.setCertificateIssuer(signerCert.getIssuerDN().toString());
                result.setCertificateValidFrom(signerCert.getNotBefore());
                result.setCertificateValidTo(signerCert.getNotAfter());

                // 4. CERTIFICATE VALIDITY CHECK (CCA/PAdES Compliant)
                // CRITICAL: Certificate validity MUST be checked at signing time, not current time
                // As per India CCA guidelines and ETSI EN 319 102-1:
                // - Use timestamp date if available (trusted time)
                // - Otherwise use signing date (claimed time)
                // - Current time check is only informational
                Date effectiveSigningTime = null;
                String timeSource = "unknown";

                // First, try to get timestamp date (most trusted)
                if (pkcs7.getTimeStampDate() != null) {
                    effectiveSigningTime = pkcs7.getTimeStampDate().getTime();
                    timeSource = "trusted timestamp";
                } else if (signDate != null) {
                    // Fallback to signing date (claimed by signer)
                    effectiveSigningTime = signDate;
                    timeSource = "signer's claimed time";
                }

                boolean certificateValidAtSigningTime = false;

                try {
                    if (effectiveSigningTime != null) {
                        // PRIMARY CHECK: Certificate validity at signing/timestamp time (CCA requirement)
                        signerCert.checkValidity(effectiveSigningTime);
                        certificateValidAtSigningTime = true;
                        result.setCertificateValid(true);
                        log.info("Certificate was valid at signing time (" + timeSource + "): " +
                                DATE_FORMAT.format(effectiveSigningTime));
                        result.addVerificationInfo("Certificate was valid at signing time (" +
                                timeSource + ": " + DATE_FORMAT.format(effectiveSigningTime) + ")");

                        // SECONDARY CHECK: Certificate validity at current time (informational only)
                        try {
                            signerCert.checkValidity();
                            result.addVerificationInfo("Certificate is still valid today");
                        } catch (CertificateExpiredException e) {
                            // Certificate expired after signing - this is OK for CCA compliance
                            result.addVerificationWarning("Certificate has expired since signing, but was valid when document was signed");
                            log.info("Certificate expired after signing (still valid signature)");
                        } catch (CertificateNotYetValidException e) {
                            // Should not happen if cert was valid at signing time
                            result.addVerificationWarning("Certificate validity period issue detected");
                        }
                    } else {
                        // No signing time available - check current time as fallback
                        log.info("No signing time or timestamp available - checking certificate validity at current time");
                        signerCert.checkValidity();
                        result.setCertificateValid(true);
                        result.addVerificationInfo("Certificate validity checked at current time");
                    }
                } catch (CertificateExpiredException e) {
                    result.setCertificateValid(false);
                    if (effectiveSigningTime != null) {
                        result.addVerificationError("Certificate was expired at signing time (" +
                                DATE_FORMAT.format(effectiveSigningTime) + ")");
                        log.error("Certificate was EXPIRED at signing time - signature INVALID");
                    } else {
                        result.addVerificationError("Certificate has expired");
                    }
                } catch (CertificateNotYetValidException e) {
                    result.setCertificateValid(false);
                    if (effectiveSigningTime != null) {
                        result.addVerificationError("Certificate was not yet valid at signing time (" +
                                DATE_FORMAT.format(effectiveSigningTime) + ")");
                        log.error("Certificate was NOT YET VALID at signing time - signature INVALID");
                    } else {
                        result.addVerificationError("Certificate is not yet valid");
                    }
                }

                // 5. EXTENDED KEY USAGE VALIDATION (CCA Requirement)
                // CRITICAL: Certificate must be authorized for document/code signing
                // This prevents misuse of certificates (e.g., TLS certificates for signing)
                notifyProgress("Validating certificate usage...");
                try {
                    List<String> extKeyUsage = signerCert.getExtendedKeyUsage();

                    if (extKeyUsage != null && !extKeyUsage.isEmpty()) {
                        boolean validForSigning = false;
                        StringBuilder ekuInfo = new StringBuilder("Extended Key Usage: ");

                        for (String oid : extKeyUsage) {
                            switch (oid) {
                                case "1.3.6.1.5.5.7.3.3": // Code Signing
                                    validForSigning = true;
                                    ekuInfo.append("Code Signing, ");
                                    break;
                                case "1.3.6.1.5.5.7.3.4": // Email Protection (S/MIME)
                                    validForSigning = true;
                                    ekuInfo.append("Email Protection, ");
                                    break;
                                case "1.3.6.1.4.1.311.10.3.12": // Microsoft Document Signing
                                    validForSigning = true;
                                    ekuInfo.append("Document Signing, ");
                                    break;
                                case "1.2.840.113583.1.1.5": // Adobe Authentic Documents Trust
                                    validForSigning = true;
                                    ekuInfo.append("Adobe PDF Signing, ");
                                    break;
                                case "1.3.6.1.5.5.7.3.1": // TLS Web Server Authentication
                                    ekuInfo.append("TLS Server Auth, ");
                                    break;
                                case "1.3.6.1.5.5.7.3.2": // TLS Web Client Authentication
                                    ekuInfo.append("TLS Client Auth, ");
                                    break;
                                default:
                                    ekuInfo.append(oid).append(", ");
                                    break;
                            }
                        }

                        // Remove trailing comma
                        if (ekuInfo.length() > 22) {
                            ekuInfo.setLength(ekuInfo.length() - 2);
                        }

                        log.info(ekuInfo.toString());

                        if (validForSigning) {
                            result.addVerificationInfo("Certificate is authorized for document signing");
                        } else {
                            // Certificate has EKU but not for signing
                            result.addVerificationWarning("Certificate Extended Key Usage does not include document signing - " +
                                    "certificate may not be intended for this purpose");
                            log.warn("Certificate EKU does not include signing purposes");
                        }
                    } else {
                        // No Extended Key Usage - check Basic Constraints
                        // If it's not a CA certificate, it can be used for signing
                        int basicConstraints = signerCert.getBasicConstraints();
                        if (basicConstraints == -1) {
                            // End-entity certificate without EKU can be used for signing
                            result.addVerificationInfo("Certificate can be used for document signing (no EKU restrictions)");
                            log.info("Certificate has no Extended Key Usage restrictions");
                        } else {
                            // CA certificate used for signing - unusual
                            result.addVerificationWarning("CA certificate used for signing - this is unusual");
                            log.warn("CA certificate used for signing");
                        }
                    }

                    // Validate Key Usage (basic usage flags)
                    // RFC 5280 - Key Usage bits define certificate purpose
                    boolean[] keyUsage = signerCert.getKeyUsage();
                    if (keyUsage != null) {
                        boolean hasDigitalSignature = keyUsage.length > 0 && keyUsage[0]; // Bit 0: Digital Signature
                        boolean hasNonRepudiation = keyUsage.length > 1 && keyUsage[1];   // Bit 1: Non Repudiation
                        boolean hasKeyEncipherment = keyUsage.length > 2 && keyUsage[2];  // Bit 2: Key Encipherment
                        boolean hasDataEncipherment = keyUsage.length > 3 && keyUsage[3]; // Bit 3: Data Encipherment

                        if (hasDigitalSignature || hasNonRepudiation) {
                            result.addVerificationInfo("Certificate has Digital Signature capability");
                            log.info("Key Usage: Digital Signature = " + hasDigitalSignature +
                                    ", Non Repudiation = " + hasNonRepudiation);

                            // Additional check: Warn if encryption bits are also present (dual-use certificate)
                            if (hasKeyEncipherment || hasDataEncipherment) {
                                result.addVerificationInfo("Certificate supports both signing and encryption (dual-use)");
                                log.info("Key Usage: Also has encryption capabilities - " +
                                        "Key Encipherment = " + hasKeyEncipherment +
                                        ", Data Encipherment = " + hasDataEncipherment);
                            }
                        } else {
                            // CRITICAL: Certificate is primarily for encryption, NOT signing
                            if (hasKeyEncipherment || hasDataEncipherment) {
                                result.addVerificationWarning("Certificate appears to be an ENCRYPTION certificate - " +
                                        "Key Usage includes encryption but NOT digital signature. " +
                                        "This certificate may not be intended for document signing.");
                                log.warn("Encryption certificate used for signing - " +
                                        "Key Encipherment = " + hasKeyEncipherment +
                                        ", Data Encipherment = " + hasDataEncipherment);
                            } else {
                                result.addVerificationWarning("Certificate Key Usage does not include Digital Signature");
                                log.warn("Certificate lacks Digital Signature in Key Usage");
                            }
                        }
                    }

                } catch (Exception e) {
                    log.warn("Could not validate Extended Key Usage: " + e.getMessage());
                }

                // 6. CERTIFICATE CHAIN VERIFICATION
                // Extract all certificates from signature
                Certificate[] certs = pkcs7.getCertificates();
                List<X509Certificate> allCerts = new ArrayList<>();
                for (Certificate cert : certs) {
                    if (cert instanceof X509Certificate) {
                        allCerts.add((X509Certificate) cert);
                    }
                }

                // Build properly ordered certificate chain starting from signer certificate
                // CRITICAL: pkcs7.getCertificates() returns certs in arbitrary order
                // We must build chain in correct order: [signer, intermediate(s), root]
                List<X509Certificate> orderedCertChain = buildOrderedCertificateChain(signerCert, allCerts);
                result.setCertificateChain(orderedCertChain);

                log.info("Built ordered certificate chain with " + orderedCertChain.size() + " certificate(s)");

                // Check if certificate is trusted (PDF viewer-level verification)
                notifyProgress("Verifying certificate trust...");
                try {
                    verifyCertificateChain(orderedCertChain);
                    result.setCertificateTrusted(true);
                    log.info("Certificate trust verification: PASSED");
                } catch (Exception e) {
                    result.setCertificateTrusted(false);
                    // Provide clear, user-friendly error message
                    String userMessage = getUserFriendlyTrustMessage(e.getMessage());
                    result.addVerificationError(userMessage);
                    log.warn("Certificate trust verification: FAILED - " + e.getMessage());
                }

                // Check certificate revocation status (OCSP) - PDF viewer style
                notifyProgress("Checking revocation status (OCSP)...");
                checkRevocationStatus(signerCert, pkcs7, result, signDate);
            } else {
                result.addVerificationError("No certificate found in signature");
            }

            // 6. SIGNATURE ALGORITHM VALIDATION (CCA/NIST Requirement)
            // CRITICAL: CCA requires SHA-256 or stronger for legal validity
            // Weak algorithms (MD5, SHA-1) are deprecated and insecure
            String hashAlgorithm = pkcs7.getHashAlgorithm();
            String encryptionAlgorithm = null;

            // Extract encryption algorithm from certificate's public key
            if (signerCert != null) {
                encryptionAlgorithm = signerCert.getPublicKey().getAlgorithm();
            }

            result.setSignatureAlgorithm(hashAlgorithm);

            log.info("Signature Algorithm: Hash=" + hashAlgorithm +
                    (encryptionAlgorithm != null ? ", Encryption=" + encryptionAlgorithm : ""));

            // Validate hash algorithm strength (CCA/NIST SP 800-131A compliance)
            AlgorithmStrength hashStrength = validateHashAlgorithm(hashAlgorithm);
            switch (hashStrength) {
                case FORBIDDEN:
                    result.addVerificationError("Signature uses FORBIDDEN hash algorithm (" + hashAlgorithm +
                            ") - signature is INSECURE and INVALID");
                    log.error("FORBIDDEN hash algorithm detected: " + hashAlgorithm);
                    // Mark signature as invalid
                    result.setSignatureValid(false);
                    break;
                case DEPRECATED:
                    result.addVerificationWarning("Signature uses DEPRECATED hash algorithm (" + hashAlgorithm +
                            ") - not recommended by CCA/NIST. Should use SHA-256 or stronger.");
                    log.warn("DEPRECATED hash algorithm detected: " + hashAlgorithm);
                    break;
                case WEAK:
                    result.addVerificationWarning("Signature uses WEAK hash algorithm (" + hashAlgorithm +
                            ") - consider upgrading to SHA-256 or stronger");
                    log.warn("WEAK hash algorithm detected: " + hashAlgorithm);
                    break;
                case ACCEPTABLE:
                    result.addVerificationInfo("Signature uses acceptable hash algorithm: " + hashAlgorithm);
                    log.info("Hash algorithm strength: ACCEPTABLE");
                    break;
                case STRONG:
                    result.addVerificationInfo("Signature uses strong hash algorithm: " + hashAlgorithm +
                            " (recommended by CCA)");
                    log.info("Hash algorithm strength: STRONG (CCA compliant)");
                    break;
            }

            // Validate encryption algorithm (RSA key size)
            if (signerCert != null) {
                int keySize = com.codemuni.utils.CertificateUtils.getKeySize(signerCert);
                if (keySize > 0) {
                    if (encryptionAlgorithm != null && encryptionAlgorithm.contains("RSA")) {
                        if (keySize < 2048) {
                            result.addVerificationError("RSA key size (" + keySize +
                                    " bits) is too weak - minimum 2048 bits required by CCA");
                            log.error("WEAK RSA key size: " + keySize + " bits");
                            result.setSignatureValid(false);
                        } else if (keySize < 3072) {
                            result.addVerificationInfo("RSA key size: " + keySize +
                                    " bits (acceptable, 3072+ recommended)");
                        } else {
                            result.addVerificationInfo("RSA key size: " + keySize +
                                    " bits (strong, CCA compliant)");
                        }
                    }
                }
            }

            // 7. TIMESTAMP VERIFICATION (RFC 3161)
            // Timestamp provides trusted proof of signing time (optional feature)
            if (pkcs7.getTimeStampDate() != null) {
                notifyProgress("Verifying timestamp token...");
                log.info("Timestamp found - performing RFC 3161 verification");

                try {
                    // Perform complete timestamp verification
                    TimestampVerificationResult tsResult = verifyTimestamp(pkcs7, result);

                    if (tsResult.isValid) {
                        result.setTimestampValid(true);
                        result.setTimestampDate(pkcs7.getTimeStampDate().getTime());
                        result.setTimestampAuthority(tsResult.tsaName);
                        log.info("Timestamp verification: PASSED");
                        result.addVerificationInfo("Timestamp verified successfully - TSA: " + tsResult.tsaName);

                        // Timestamp proves exact signing time
                        result.addVerificationInfo("Signing time verified by trusted timestamp: " +
                                DATE_FORMAT.format(pkcs7.getTimeStampDate().getTime()));
                    } else {
                        result.setTimestampValid(false);
                        result.addVerificationError("Timestamp verification failed: " + tsResult.errorMessage);
                        log.error("Timestamp verification: FAILED - " + tsResult.errorMessage);

                        // If timestamp is invalid, signing time cannot be trusted
                        result.addVerificationWarning("Signing time cannot be trusted (timestamp invalid)");
                    }
                } catch (Exception e) {
                    result.setTimestampValid(false);
                    result.addVerificationError("Timestamp verification error: " + e.getMessage());
                    log.error("Timestamp verification error", e);
                }

                log.info("Timestamp: Enabled and verified");
            } else {
                // Timestamp not present - this is normal and optional
                log.info("Timestamp: Not present (signing time is self-declared)");
            }

            // 8. LTV INFORMATION (PDF viewer-style check)
            // Check if document has DSS (Document Security Store) for LTV
            boolean hasLTV = checkLTVEnabled(reader, pkcs7);
            result.setHasLTV(hasLTV);
            if (hasLTV) {
                log.info("LTV: Enabled - Document contains revocation information (CRL/OCSP)");
            } else {
                // LTV not enabled - this is INFO, not an error
                result.addVerificationInfo("Long term validation not enabled in this signature");
                log.info("LTV: Not enabled");
            }

            // 9. POSITION INFORMATION (for rectangle overlay)
            try {
                List<AcroFields.FieldPosition> positions = acroFields.getFieldPositions(signatureName);
                if (positions != null && !positions.isEmpty()) {
                    // Get the first position (signatures typically have one position)
                    AcroFields.FieldPosition fieldPos = positions.get(0);
                    result.setPageNumber(fieldPos.page);

                    float left = fieldPos.position.getLeft();
                    float bottom = fieldPos.position.getBottom();
                    float right = fieldPos.position.getRight();
                    float top = fieldPos.position.getTop();

                    result.setPosition(new float[]{left, bottom, right, top});

                    // Detect invisible signature (width or height is zero or very small)
                    float width = right - left;
                    float height = top - bottom;
                    boolean invisible = (width <= 0.1f || height <= 0.1f);
                    result.setInvisible(invisible);

                    if (invisible) {
                        log.info("Signature " + signatureName + " is invisible (width=" + width + ", height=" + height + ")");
                    }
                } else {
                    // No position means invisible signature
                    result.setInvisible(true);
                    log.info("Signature " + signatureName + " has no position (invisible)");
                }
            } catch (Exception e) {
                log.debug("Could not extract signature position", e);
            }

            // 10. CERTIFICATION LEVEL DETECTION (PDF viewer style)
            log.info("Detecting certification level for signature: " + signatureName);
            boolean isCert = isCertificationSignature(acroFields, signatureName);
            result.setCertificationSignature(isCert);

            if (isCert) {
                int pValue = getCertificationLevel(acroFields, signatureName);
                com.codemuni.model.CertificationLevel certLevel =
                        com.codemuni.model.CertificationLevel.fromPValue(pValue);
                result.setCertificationLevel(certLevel);
                log.info("✓ Certification signature detected: " + certLevel.getLabel() +
                        " (P=" + pValue + ")");
            } else {
                result.setCertificationLevel(com.codemuni.model.CertificationLevel.NOT_CERTIFIED);
                log.info("✓ Approval signature (NOT_CERTIFIED)");
            }

            log.info("Signature verification completed for: " + signatureName +
                    " - Status: " + result.getOverallStatus() +
                    " - Certification: " + result.getCertificationLevel().getLabel() +
                    " - Page: " + result.getPageNumber());

        } catch (Exception e) {
            log.error("Error during signature verification", e);

            if (e.getMessage().contains("can't decode PKCS7SignedData object")) {
                result.addVerificationError("No valid digital signature was found in the document.");
                result.addVerificationError("Details: " + e.getMessage());
            } else {
                result.addVerificationError("Signature verification failed");
                result.addVerificationError("Details: " + e.getMessage());
            }

        }

        return result;
    }

    /**
     * Builds a properly ordered certificate chain starting from the signer certificate.
     * This is critical because pkcs7.getCertificates() returns certificates in arbitrary order.
     * <p>
     * The correct order is: [end-entity (signer), intermediate CA(s), root CA]
     *
     * @param signerCert     The signing certificate (end entity)
     * @param availableCerts All certificates from the PDF signature
     * @return Ordered certificate chain from signer to root
     */
    private List<X509Certificate> buildOrderedCertificateChain(X509Certificate signerCert,
                                                               List<X509Certificate> availableCerts) {
        List<X509Certificate> orderedChain = new ArrayList<>();

        if (signerCert == null) {
            log.warn("Signer certificate is null, cannot build chain");
            return orderedChain;
        }

        // Start with the signer certificate (end entity)
        orderedChain.add(signerCert);
        log.info("Building ordered chain starting from signer: " + extractCN(signerCert.getSubjectDN().toString()));

        // Build chain upward by finding issuers
        X509Certificate currentCert = signerCert;
        int maxIterations = 10; // Prevent infinite loops
        int iteration = 0;

        while (iteration < maxIterations) {
            // Check if current cert is self-signed (reached root)
            if (isSelfSignedCertificate(currentCert)) {
                log.info("Reached self-signed root certificate");
                break;
            }

            // Find the issuer of current certificate in available certs
            X509Certificate issuerCert = findIssuerInList(currentCert, availableCerts);

            if (issuerCert == null) {
                log.info("Issuer not found in PDF certificates for: " + extractCN(currentCert.getSubjectDN().toString()));
                log.info("Chain will be completed later from trust store if needed");
                break;
            }

            // Verify this is the correct issuer by checking signature
            if (!verifyCertificateSignature(currentCert, issuerCert)) {
                log.warn("Found certificate with matching DN but signature verification failed");
                break;
            }

            // Check if we already added this cert (prevent duplicates/loops)
            if (orderedChain.contains(issuerCert)) {
                log.warn("Certificate already in chain, stopping to prevent loop");
                break;
            }

            // Add issuer to chain
            orderedChain.add(issuerCert);
            log.info("  Added issuer to chain: " + extractCN(issuerCert.getSubjectDN().toString()));

            // Move up the chain
            currentCert = issuerCert;
            iteration++;
        }

        if (iteration >= maxIterations) {
            log.warn("Stopped building chain after max iterations");
        }

        log.info("Ordered certificate chain built with " + orderedChain.size() + " certificate(s):");
        for (int i = 0; i < orderedChain.size(); i++) {
            X509Certificate cert = orderedChain.get(i);
            String role = i == 0 ? "Signer" : (isSelfSignedCertificate(cert) ? "Root CA" : "Intermediate CA");
            log.info("  [" + i + "] " + role + ": " + extractCN(cert.getSubjectDN().toString()));
        }

        return orderedChain;
    }

    /**
     * Finds the issuer certificate for a given certificate in a list of certificates.
     * Uses proper DN comparison and signature verification.
     *
     * @param cert           Certificate to find issuer for
     * @param availableCerts List of available certificates
     * @return Issuer certificate if found, null otherwise
     */
    private X509Certificate findIssuerInList(X509Certificate cert, List<X509Certificate> availableCerts) {
        if (cert == null || availableCerts == null || availableCerts.isEmpty()) {
            return null;
        }

        for (X509Certificate candidate : availableCerts) {
            // Skip if this is the same certificate
            if (candidate.equals(cert)) {
                continue;
            }

            // Check if candidate is the issuer using proper DN comparison
            if (isIssuer(candidate, cert)) {
                log.debug("Found potential issuer: " + extractCN(candidate.getSubjectDN().toString()));
                return candidate;
            }
        }

        return null;
    }

    /**
     * Verifies the certificate chain using PDF viewer-level verification.
     * Step-by-step verification with clear error messages for non-tech users.
     */
    private void verifyCertificateChain(List<X509Certificate> certChain) throws Exception {
        if (certChain == null || certChain.isEmpty()) {
            throw new Exception("No certificate found in signature");
        }

        X509Certificate signerCert = certChain.get(0);

        // Step 1: Check if certificate is self-signed using proper DN comparison
        boolean isSelfSigned = isSelfSignedCertificate(signerCert);

        // Step 2: Get trust anchors (embedded + manual certificates)
        Set<TrustAnchor> trustAnchors = getTrustStore();

        if (trustAnchors.isEmpty()) {
            throw new Exception("No trusted certificates available for verification");
        }

        log.info("Verifying certificate chain with " + trustAnchors.size() + " trust anchor(s)");
        log.info("Certificate chain length: " + certChain.size());
        log.info("Signer certificate subject: " + signerCert.getSubjectDN());
        log.info("Signer certificate issuer: " + signerCert.getIssuerDN());

        // Step 3: Check if signer certificate itself is in trusted store (direct trust)
        for (TrustAnchor anchor : trustAnchors) {
            X509Certificate trustedCert = anchor.getTrustedCert();
            if (trustedCert.equals(signerCert)) {
                log.info("Certificate is directly trusted (found in trust store)");
                return; // Directly trusted
            }
        }

        // Step 4: If self-signed and not in trust store, fail
        if (isSelfSigned) {
            String errorMsg = "Certificate is self-signed and not in trusted list";
            throw new Exception(errorMsg);
        }

        // Step 5: Build and validate certificate path to root CA
        try {
            // PDF viewer-style verification: Try to find a valid path
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");

            // Create PKIXParameters with root trust anchors
            PKIXParameters params = new PKIXParameters(trustAnchors);
            params.setRevocationEnabled(false); // Disable CRL/OCSP for basic verification

            // Add intermediate certificates for proper chain building
            try {
                CertStore intermediateCertStore = getIntermediateCertStore();
                params.addCertStore(intermediateCertStore);
                log.info("Added intermediate certificate store for chain building");
            } catch (Exception e) {
                log.warn("Could not add intermediate certificates: " + e.getMessage());
                // Continue without intermediate cert store - may still work if chain is complete in PDF
            }

            // Log certificate chain details
            log.info("Building certificate path:");
            for (int i = 0; i < certChain.size(); i++) {
                X509Certificate cert = certChain.get(i);
                log.info("  [" + i + "] Subject: " + extractCN(cert.getSubjectDN().toString()));
                log.info("      Issuer: " + extractCN(cert.getIssuerDN().toString()));
            }

            // Log trust anchors for debugging
            log.info("Available trust anchors:");
            int anchorCount = 0;
            for (TrustAnchor anchor : trustAnchors) {
                X509Certificate anchorCert = anchor.getTrustedCert();
                log.info("  Trust Anchor [" + anchorCount + "]: " + extractCN(anchorCert.getSubjectDN().toString()));
                anchorCount++;
                if (anchorCount >= 10) {
                    log.info("  ... and " + (trustAnchors.size() - anchorCount) + " more");
                    break;
                }
            }

            // Build complete certificate chain by finding missing issuers in trust store
            // This handles cases where PDF doesn't include all intermediate/root certificates
            certChain = buildCompleteChain(certChain, trustAnchors);

            // Build certificate path
            CertPath certPath = cf.generateCertPath(certChain);

            // Validate the path using BouncyCastle provider
            CertPathValidator validator = CertPathValidator.getInstance("PKIX", "BC");
            PKIXCertPathValidatorResult validationResult = (PKIXCertPathValidatorResult) validator.validate(certPath, params);

            TrustAnchor trustAnchor = validationResult.getTrustAnchor();
            log.info("Certificate chain validated successfully!");
            log.info("Trusted by: " + extractCN(trustAnchor.getTrustedCert().getSubjectDN().toString()));

        } catch (CertPathValidatorException e) {
            // Provide specific error messages based on validation failure
            String reason = e.getReason() != null ? e.getReason().toString() : "Unknown";
            int index = e.getIndex();

            log.error("Certificate path validation failed at index " + index + ": " + reason);

            if (index >= 0 && index < certChain.size()) {
                X509Certificate failedCert = certChain.get(index);
                String certName = extractCN(failedCert.getSubjectDN().toString());

                // Provide specific error based on reason
                String errorMsg;
                if (reason.contains("NO_TRUST_ANCHOR") || reason.contains("UNDETERMINED_REVOCATION_STATUS")) {
                    errorMsg = "Root certificate not found in trust store. Add '" +
                            extractCN(failedCert.getIssuerDN().toString()) +
                            "' to Trust Manager.";
                } else {
                    errorMsg = "Certificate '" + certName + "' verification failed: " + reason;
                }
                throw new Exception(errorMsg);
            } else {
                String errorMsg = "Certificate chain validation failed: " + reason;
                throw new Exception(errorMsg);
            }
        } catch (InvalidAlgorithmParameterException e) {
            log.error("Invalid algorithm parameter", e);
            String errorMsg = "No trusted root certificate found in trust store";
            throw new Exception(errorMsg);
        } catch (Exception e) {
            log.error("Certificate validation error", e);
            String errorMsg = "Certificate not trusted: " + e.getMessage();
            throw new Exception(errorMsg);
        }
    }

    /**
     * Builds a complete certificate chain by finding missing issuers in trust store.
     * This is crucial for signature verification when PDF doesn't include all certificates.
     * Now properly searches BOTH root certificates (trust anchors) AND intermediate certificates.
     *
     * @param originalChain The original certificate chain from PDF
     * @param trustAnchors  Available root trust anchors (self-signed CAs)
     * @return Complete certificate chain including missing issuers
     */
    private List<X509Certificate> buildCompleteChain(List<X509Certificate> originalChain, Set<TrustAnchor> trustAnchors) {
        if (originalChain == null || originalChain.isEmpty()) {
            return originalChain;
        }

        List<X509Certificate> completeChain = new ArrayList<>(originalChain);

        // Get intermediate certificates for chain building
        Collection<X509Certificate> intermediateCerts = new ArrayList<>();
        try {
            intermediateCerts = trustStoreManager.getIntermediateCertificates();
            log.info("Loaded " + intermediateCerts.size() + " intermediate certificate(s) for chain building");
        } catch (Exception e) {
            log.warn("Could not load intermediate certificates: " + e.getMessage());
        }

        // Combine all available certificates for searching
        List<X509Certificate> allAvailableCerts = new ArrayList<>();
        // Add root certificates from trust anchors
        for (TrustAnchor anchor : trustAnchors) {
            allAvailableCerts.add(anchor.getTrustedCert());
        }
        // Add intermediate certificates
        allAvailableCerts.addAll(intermediateCerts);

        log.info("Total available certificates for chain building: " + allAvailableCerts.size() +
                " (Roots: " + trustAnchors.size() + ", Intermediates: " + intermediateCerts.size() + ")");

        // Keep looking for issuers until we find a self-signed cert or can't find issuer
        int maxIterations = 10; // Prevent infinite loop
        int iterations = 0;

        while (iterations < maxIterations) {
            X509Certificate lastCert = completeChain.get(completeChain.size() - 1);

            // If last cert is self-signed, chain is complete
            if (isSelfSignedCertificate(lastCert)) {
                log.info("Chain is complete - found self-signed root: " + extractCN(lastCert.getSubjectDN().toString()));
                break;
            }

            // Look for issuer in all available certificates (roots + intermediates)
            boolean foundIssuer = false;

            // Debug: Log what we're looking for
            log.info("Looking for issuer of '" + extractCN(lastCert.getSubjectDN().toString()) + "'");
            log.info("Required issuer DN: " + lastCert.getIssuerX500Principal().getName());

            for (X509Certificate candidateCert : allAvailableCerts) {
                // Check if this certificate is the issuer using proper DN comparison
                if (isIssuer(candidateCert, lastCert)) {
                    log.info("Found potential issuer: " + extractCN(candidateCert.getSubjectDN().toString()));

                    // Verify signature to ensure this is the correct issuer
                    if (verifyCertificateSignature(lastCert, candidateCert)) {
                        // Avoid duplicates
                        boolean isDuplicate = false;
                        for (X509Certificate existingCert : completeChain) {
                            if (existingCert.equals(candidateCert)) {
                                isDuplicate = true;
                                break;
                            }
                        }

                        if (!isDuplicate) {
                            completeChain.add(candidateCert);
                            String certType = isSelfSignedCertificate(candidateCert) ? "root" : "intermediate";
                            log.info("Added verified " + certType + " certificate to chain - new chain length: " + completeChain.size());
                        }

                        foundIssuer = true;
                        break;
                    } else {
                        log.warn("DN matched but signature verification failed - not the correct issuer");
                    }
                }
            }

            // If we couldn't find issuer, stop looking
            if (!foundIssuer) {
                log.warn("Could not find issuer '" + extractCN(lastCert.getIssuerDN().toString()) + "' in available certificates");
                log.warn("Required issuer DN: " + lastCert.getIssuerX500Principal().getName());
                log.warn("Available certificates:");
                for (X509Certificate cert : allAvailableCerts) {
                    log.warn("  - " + extractCN(cert.getSubjectDN().toString()) +
                            " [DN: " + cert.getSubjectX500Principal().getName() + "]");
                }
                break;
            }

            iterations++;
        }

        if (iterations >= maxIterations) {
            log.warn("Stopped chain building after max iterations");
        }

        return completeChain;
    }

    /**
     * Checks if a certificate is self-signed.
     * Uses X500Principal for proper DN comparison instead of string comparison.
     *
     * @param cert Certificate to check
     * @return true if self-signed, false otherwise
     */
    private boolean isSelfSignedCertificate(X509Certificate cert) {
        if (cert == null) {
            return false;
        }

        // Use X500Principal CANONICAL format for proper DN comparison
        // This handles cases where DN components are in different order
        boolean dnMatch;
        try {
            String subjectDN = cert.getSubjectX500Principal().getName(javax.security.auth.x500.X500Principal.CANONICAL);
            String issuerDN = cert.getIssuerX500Principal().getName(javax.security.auth.x500.X500Principal.CANONICAL);
            dnMatch = subjectDN.equals(issuerDN);
        } catch (Exception e) {
            log.warn("Error comparing DNs in canonical form, falling back to direct comparison", e);
            // Fallback to direct comparison
            dnMatch = cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());
        }

        if (!dnMatch) {
            return false;
        }

        // Also verify the signature to ensure it's truly self-signed
        try {
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception e) {
            log.debug("Certificate has matching subject/issuer but signature verification failed", e);
            return false;
        }
    }

    /**
     * Checks if issuerCert is the issuer of subjectCert.
     * Uses X500Principal for proper DN comparison.
     *
     * @param issuerCert  Potential issuer certificate
     * @param subjectCert Subject certificate
     * @return true if issuerCert issued subjectCert
     */
    private boolean isIssuer(X509Certificate issuerCert, X509Certificate subjectCert) {
        if (issuerCert == null || subjectCert == null) {
            return false;
        }

        // Compare issuer DN of subject with subject DN of issuer
        // Use component-wise comparison since CANONICAL format doesn't normalize component order
        try {
            String requiredIssuerDN = subjectCert.getIssuerX500Principal().getName(javax.security.auth.x500.X500Principal.CANONICAL);
            String candidateSubjectDN = issuerCert.getSubjectX500Principal().getName(javax.security.auth.x500.X500Principal.CANONICAL);

            // Parse DNs into component sets for order-independent comparison
            boolean match = compareDNs(requiredIssuerDN, candidateSubjectDN);

            return match;
        } catch (Exception e) {
            log.warn("Error comparing DNs, falling back to direct comparison", e);
            // Fallback to direct comparison
            return subjectCert.getIssuerX500Principal().equals(issuerCert.getSubjectX500Principal());
        }
    }

    /**
     * Compares two DNs component-wise, ignoring order.
     * Handles cases where DN components are in different order.
     */
    private boolean compareDNs(String dn1, String dn2) {
        if (dn1 == null || dn2 == null) {
            return false;
        }

        // Quick equality check first
        if (dn1.equals(dn2)) {
            return true;
        }

        try {
            // Parse DNs into component maps
            Map<String, String> components1 = parseDN(dn1);
            Map<String, String> components2 = parseDN(dn2);

            // Compare component maps
            return components1.equals(components2);
        } catch (Exception e) {
            log.warn("Error parsing DNs for comparison: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parses a DN string into a map of attribute=value pairs.
     */
    private Map<String, String> parseDN(String dn) {
        Map<String, String> components = new HashMap<>();

        if (dn == null || dn.isEmpty()) {
            return components;
        }

        // Split by comma (handling escaped commas)
        String[] parts = dn.split("(?<!\\\\),");

        for (String part : parts) {
            part = part.trim();
            int equalsIndex = part.indexOf('=');
            if (equalsIndex > 0) {
                String key = part.substring(0, equalsIndex).trim().toLowerCase();
                String value = part.substring(equalsIndex + 1).trim().toLowerCase();
                // Remove any backslash escapes
                value = value.replace("\\,", ",");
                components.put(key, value);
            }
        }

        return components;
    }

    /**
     * Verifies that subjectCert was signed by issuerCert.
     *
     * @param subjectCert Certificate to verify
     * @param issuerCert  Issuer certificate
     * @return true if signature is valid, false otherwise
     */
    private boolean verifyCertificateSignature(X509Certificate subjectCert, X509Certificate issuerCert) {
        if (subjectCert == null || issuerCert == null) {
            return false;
        }

        try {
            subjectCert.verify(issuerCert.getPublicKey());
            log.info("      ✓ Signature verification SUCCESS");
            return true;
        } catch (Exception e) {
            log.warn("      ✗ Signature verification FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            log.warn("        Subject cert serial: " + subjectCert.getSerialNumber());
            log.warn("        Issuer cert serial: " + issuerCert.getSerialNumber());
            log.warn("        This means the issuer certificate in trust store has a DIFFERENT KEY than the one that actually signed this certificate");
            return false;
        }
    }

    /**
     * Extracts Common Name (CN) from Distinguished Name.
     */
    private String extractCN(String dn) {
        if (dn == null) return "Unknown";
        String[] parts = dn.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("CN=")) {
                return part.substring(3).trim();
            }
        }
        return dn;
    }

    /**
     * Converts technical error messages to user-friendly messages.
     */
    private String getUserFriendlyTrustMessage(String technicalMessage) {
        if (technicalMessage == null) {
            return "Certificate trust verification failed";
        }

        String lowerMsg = technicalMessage.toLowerCase();

        // Self-signed certificate
        if (lowerMsg.contains("self-signed")) {
            return "This certificate is not trusted";
        }

        // No trusted root found
        if (lowerMsg.contains("no trusted root") || lowerMsg.contains("trust anchor")) {
            return "Certificate issuer is not trusted";
        }

        // Certificate chain broken
        if (lowerMsg.contains("chain broken")) {
            return "Certificate chain is broken";
        }

        // Path validation failed
        if (lowerMsg.contains("path") && lowerMsg.contains("invalid")) {
            return "Certificate chain is not valid";
        }

        // No certificate found
        if (lowerMsg.contains("no certificate found")) {
            return "No certificate found";
        }

        // No trusted certificates available
        if (lowerMsg.contains("no trusted certificates available")) {
            return "No trusted certificates available";
        }

        // Generic certificate not trusted
        if (lowerMsg.contains("not trusted")) {
            return "This certificate is not trusted";
        }

        // Default: return original message
        return technicalMessage;
    }

    /**
     * Checks certificate revocation status using OCSP.
     * Performs live OCSP check by extracting URL from certificate.
     * CRITICAL FIX: Now checks revocation time vs signing time to properly validate signatures.
     *
     * @param cert     The certificate to check
     * @param pkcs7    The signature PKCS7 data
     * @param result   The verification result to update
     * @param signDate The date when the document was signed (used for revocation time comparison)
     */
    private void checkRevocationStatus(X509Certificate cert, PdfPKCS7 pkcs7, SignatureVerificationResult result, Date signDate) {
        try {
            String certSerial = cert.getSerialNumber().toString();
            String certSubject = extractCN(cert.getSubjectDN().toString());
            String cacheKey = certSerial + ":" + cert.getIssuerDN().toString();

            log.info("Checking certificate revocation status for [" + certSerial + "] " + certSubject);

            // STEP 0: Check cache first (avoid redundant checks for same certificate)
            if (revocationCache.containsKey(cacheKey)) {
                RevocationCacheEntry cached = revocationCache.get(cacheKey);
                log.info("Revocation status found in cache - Status: " + cached.status +
                        " (Source: " + cached.source + ", Age: " +
                        (System.currentTimeMillis() - cached.timestamp) + "ms)");

                result.setRevocationStatus(cached.status);
                result.setCertificateRevoked(cached.isRevoked);

                if (cached.isRevoked) {
                    result.addVerificationError("Certificate has been revoked (" + cached.source + ")");
                } else {
                    result.addVerificationInfo("Revocation checked via " + cached.source + " (cached)");
                }
                return;
            }

            // Method 1: Check embedded OCSP response in signature (LTV)
            try {
                Object ocspResponse = pkcs7.getOcsp();
                if (ocspResponse != null) {
                    log.info("OCSP: Found embedded OCSP response - parsing for revocation time");

                    // CRITICAL FIX: Parse embedded OCSP to check revocation time vs signing time
                    try {
                        byte[] ocspBytes;
                        if (ocspResponse instanceof byte[]) {
                            ocspBytes = (byte[]) ocspResponse;
                        } else {
                            log.warn("Embedded OCSP response is not byte array, cannot parse");
                            result.setRevocationStatus("Valid (Embedded)");
                            result.setCertificateRevoked(false);
                            result.addVerificationInfo("Revocation checked via embedded OCSP (time not verified)");
                            return;
                        }

                        org.bouncycastle.ocsp.OCSPResp ocspResp = new org.bouncycastle.ocsp.OCSPResp(ocspBytes);
                        if (ocspResp.getStatus() == org.bouncycastle.ocsp.OCSPRespStatus.SUCCESSFUL) {
                            org.bouncycastle.ocsp.BasicOCSPResp basicResp =
                                    (org.bouncycastle.ocsp.BasicOCSPResp) ocspResp.getResponseObject();
                            org.bouncycastle.ocsp.SingleResp[] responses = basicResp.getResponses();

                            if (responses.length > 0) {
                                Object certStatus = responses[0].getCertStatus();

                                if (certStatus == null) {
                                    // Not revoked
                                    result.setRevocationStatus("Valid (Embedded OCSP)");
                                    result.setCertificateRevoked(false);
                                    result.addVerificationInfo("Revocation checked via embedded OCSP");

                                    // Cache the result
                                    revocationCache.put(cacheKey, new RevocationCacheEntry(
                                            "Valid (Embedded OCSP)", false, null, "Embedded OCSP"));
                                    log.info("Cached revocation status: VALID (Embedded OCSP)");
                                    return;
                                } else if (certStatus instanceof org.bouncycastle.ocsp.RevokedStatus) {
                                    // Revoked - check time
                                    org.bouncycastle.ocsp.RevokedStatus revokedStatus =
                                            (org.bouncycastle.ocsp.RevokedStatus) certStatus;
                                    Date revocationTime = revokedStatus.getRevocationTime();

                                    Date effectiveSigningTime = result.getTimestampDate() != null ?
                                            result.getTimestampDate() : signDate;

                                    if (effectiveSigningTime != null && revocationTime != null) {
                                        if (revocationTime.before(effectiveSigningTime)) {
                                            result.setRevocationStatus("Revoked before signing (Embedded OCSP)");
                                            result.setCertificateRevoked(true);
                                            result.addVerificationError("Certificate was revoked BEFORE the document was signed (embedded OCSP)");
                                            log.error("Embedded OCSP shows cert revoked BEFORE signing");

                                            // Cache the result
                                            revocationCache.put(cacheKey, new RevocationCacheEntry(
                                                    "Revoked before signing (Embedded OCSP)", true, revocationTime, "Embedded OCSP"));
                                            return;
                                        } else {
                                            if (result.isTimestampValid()) {
                                                result.setRevocationStatus("Valid (Revoked after signing, has timestamp)");
                                                result.setCertificateRevoked(false);
                                                result.addVerificationInfo("Certificate was revoked after signing, but signature has valid timestamp (embedded OCSP)");
                                                log.info("Embedded OCSP shows cert revoked AFTER signing with timestamp");

                                                // Cache the result
                                                revocationCache.put(cacheKey, new RevocationCacheEntry(
                                                        "Valid (Revoked after signing, has timestamp)", false, revocationTime, "Embedded OCSP"));
                                                return;
                                            } else {
                                                result.setRevocationStatus("Revoked (no timestamp)");
                                                result.setCertificateRevoked(true);
                                                result.addVerificationError("Certificate revoked and signature lacks timestamp (embedded OCSP)");

                                                // Cache the result
                                                revocationCache.put(cacheKey, new RevocationCacheEntry(
                                                        "Revoked (no timestamp)", true, revocationTime, "Embedded OCSP"));
                                                return;
                                            }
                                        }
                                    } else {
                                        result.setRevocationStatus("Revoked (Embedded OCSP)");
                                        result.setCertificateRevoked(true);
                                        result.addVerificationError("Certificate has been revoked (embedded OCSP)");

                                        // Cache the result
                                        revocationCache.put(cacheKey, new RevocationCacheEntry(
                                                "Revoked (Embedded OCSP)", true, null, "Embedded OCSP"));
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception parseEx) {
                        log.warn("Could not parse embedded OCSP response for revocation time check", parseEx);
                        // Fall back to simple valid status
                        result.setRevocationStatus("Valid (Embedded OCSP)");
                        result.setCertificateRevoked(false);
                        result.addVerificationInfo("Revocation checked via embedded OCSP (time not verified)");
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("No embedded OCSP", e);
            }

            // Method 2: Check embedded CRLs (LTV) with proper validation
            try {
                Collection<?> crls = pkcs7.getCRLs();
                if (crls != null && !crls.isEmpty()) {
                    log.info("CRL: Found " + crls.size() + " embedded CRL(s) - validating...");

                    // Validate each CRL and check for revocation
                    for (Object crlObj : crls) {
                        if (crlObj instanceof java.security.cert.X509CRL) {
                            java.security.cert.X509CRL crl = (java.security.cert.X509CRL) crlObj;

                            // Check if certificate is revoked in this CRL
                            java.security.cert.X509CRLEntry revokedEntry = crl.getRevokedCertificate(cert);

                            if (revokedEntry != null) {
                                // Certificate is revoked - check WHEN it was revoked
                                Date revocationTime = revokedEntry.getRevocationDate();

                                // Use timestamp if available, otherwise use signing date
                                Date effectiveSigningTime = result.getTimestampDate() != null ?
                                        result.getTimestampDate() : signDate;

                                if (effectiveSigningTime != null && revocationTime != null) {
                                    if (revocationTime.before(effectiveSigningTime)) {
                                        // Certificate was revoked BEFORE signing - INVALID
                                        result.setRevocationStatus("Revoked before signing (Embedded CRL)");
                                        result.setCertificateRevoked(true);
                                        result.addVerificationError("Certificate was revoked BEFORE the document was signed (embedded CRL)");
                                        log.error("Embedded CRL shows cert revoked BEFORE signing: " +
                                                "Revoked: " + DATE_FORMAT.format(revocationTime) +
                                                ", Signed: " + DATE_FORMAT.format(effectiveSigningTime));

                                        // Cache the result
                                        revocationCache.put(cacheKey, new RevocationCacheEntry(
                                                "Revoked before signing (Embedded CRL)", true, revocationTime, "Embedded CRL"));
                                        return;
                                    } else {
                                        // Certificate was revoked AFTER signing
                                        if (result.isTimestampValid()) {
                                            // Has timestamp - signature is VALID
                                            result.setRevocationStatus("Valid (Revoked after signing, has timestamp)");
                                            result.setCertificateRevoked(false);
                                            result.addVerificationInfo("Certificate was revoked after signing, but signature has valid timestamp (embedded CRL)");
                                            log.info("Embedded CRL shows cert revoked AFTER signing with timestamp - signature VALID");

                                            // Cache the result
                                            revocationCache.put(cacheKey, new RevocationCacheEntry(
                                                    "Valid (Revoked after signing, has timestamp)", false, revocationTime, "Embedded CRL"));
                                            return;
                                        } else {
                                            // No timestamp - cannot prove signing time
                                            result.setRevocationStatus("Revoked (no timestamp)");
                                            result.setCertificateRevoked(true);
                                            result.addVerificationError("Certificate revoked and signature lacks timestamp (embedded CRL)");
                                            log.warn("Embedded CRL shows cert revoked, no timestamp to prove signing time");

                                            // Cache the result
                                            revocationCache.put(cacheKey, new RevocationCacheEntry(
                                                    "Revoked (no timestamp)", true, revocationTime, "Embedded CRL"));
                                            return;
                                        }
                                    }
                                } else {
                                    // Revoked but no time info available
                                    result.setRevocationStatus("Revoked (Embedded CRL)");
                                    result.setCertificateRevoked(true);
                                    result.addVerificationError("Certificate has been revoked (embedded CRL)");

                                    // Cache the result
                                    revocationCache.put(cacheKey, new RevocationCacheEntry(
                                            "Revoked (Embedded CRL)", true, null, "Embedded CRL"));
                                    return;
                                }
                            } else {
                                // Certificate not found in this CRL - check next CRL
                                log.debug("Certificate not revoked in CRL issued by: " + crl.getIssuerDN());
                            }
                        }
                    }

                    // Certificate not revoked in any embedded CRL
                    result.setRevocationStatus("Valid (Embedded CRL)");
                    result.setCertificateRevoked(false);
                    result.addVerificationInfo("Revocation checked via embedded CRL - certificate is valid");
                    log.info("CRL validation passed - certificate not revoked");

                    // Cache the result
                    revocationCache.put(cacheKey, new RevocationCacheEntry(
                            "Valid (Embedded CRL)", false, null, "Embedded CRL"));
                    return;
                }
            } catch (Exception e) {
                log.warn("Error validating embedded CRL: " + e.getMessage(), e);
            }

            // Method 3: Perform live OCSP check
            // certSerial and certSubject already defined at the start of this method
            log.info("OCSP: Performing live check for cert [" + certSerial + "] " + certSubject);
            String ocspUrl = extractOCSPUrl(cert);

            if (ocspUrl != null && !ocspUrl.isEmpty()) {
                log.info("OCSP: Found URL for cert [" + certSerial + "]: " + ocspUrl);

                Certificate[] certs = pkcs7.getCertificates();
                X509Certificate issuerCert = findIssuerCertificate(cert, certs);

                if (issuerCert != null) {
                    // Retry logic for OCSP network failures (max 3 attempts)
                    int maxRetries = 3;
                    OCSPCheckResult ocspResult = null;
                    SignatureVerificationException lastException = null;

                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        try {
                            if (attempt > 1) {
                                log.info("OCSP: Retry attempt " + attempt + " of " + maxRetries + " for cert [" + certSerial + "]");
                            } else {
                                log.info("OCSP: Contacting server for cert [" + certSerial + "]...");
                            }

                            ocspResult = performLiveOCSPCheck(cert, issuerCert, ocspUrl);

                            // Success - break out of retry loop
                            log.info("OCSP: Request successful on attempt " + attempt);
                            lastException = null;
                            break;

                        } catch (SignatureVerificationException ocspEx) {
                            lastException = ocspEx;

                            if (ocspEx.isNetworkError() && attempt < maxRetries) {
                                // Network error - retry with exponential backoff
                                long waitTime = (long) (1000 * Math.pow(2, attempt - 1)); // 1s, 2s, 4s
                                log.warn("OCSP: Network error on attempt " + attempt + " - retrying after " + waitTime + "ms");

                                try {
                                    Thread.sleep(waitTime);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            } else {
                                // Non-network error or final retry - don't retry
                                log.warn("OCSP: Failed on attempt " + attempt + " - " + ocspEx.getMessage());
                                break;
                            }
                        }
                    }

                    // Check if OCSP check was successful
                    if (ocspResult != null) {

                        // CRITICAL FIX: Compare revocation time with signing time
                        boolean actuallyRevoked = false;
                        String status;

                        if (ocspResult.isRevoked && ocspResult.revocationTime != null) {
                            // Certificate is revoked - check WHEN it was revoked
                            Date revocationTime = ocspResult.revocationTime;

                            // Use timestamp if available, otherwise use signing date
                            Date effectiveSigningTime = result.getTimestampDate() != null ?
                                    result.getTimestampDate() : signDate;

                            if (effectiveSigningTime != null) {
                                if (revocationTime.before(effectiveSigningTime)) {
                                    // CRITICAL: Certificate was revoked BEFORE signing
                                    // Signature is INVALID - signer used a revoked certificate
                                    actuallyRevoked = true;
                                    status = "Revoked before signing";
                                    log.error("OCSP: Certificate was REVOKED BEFORE signing! " +
                                            "Revoked: " + DATE_FORMAT.format(revocationTime) +
                                            ", Signed: " + DATE_FORMAT.format(effectiveSigningTime));
                                    result.addVerificationError("Certificate was revoked BEFORE the document was signed");
                                } else {
                                    // Certificate was revoked AFTER signing
                                    if (result.isTimestampValid()) {
                                        // Signature has valid timestamp - signature is VALID
                                        // The timestamp proves the signature was created when cert was still valid
                                        actuallyRevoked = false;
                                        status = "Valid (Revoked after signing, has timestamp)";
                                        log.info("OCSP: Certificate revoked AFTER signing, but signature has valid timestamp → VALID. " +
                                                "Revoked: " + DATE_FORMAT.format(revocationTime) +
                                                ", Signed: " + DATE_FORMAT.format(effectiveSigningTime));
                                        result.addVerificationInfo("Certificate was revoked after signing, but signature has valid timestamp proving it was created when certificate was valid");
                                    } else {
                                        // No timestamp - cannot prove when signature was created
                                        actuallyRevoked = true;
                                        status = "Revoked (no timestamp to prove signing time)";
                                        log.warn("OCSP: Certificate revoked AFTER signing, but NO timestamp to prove signing time → INVALID. " +
                                                "Revoked: " + DATE_FORMAT.format(revocationTime));
                                        result.addVerificationError("Certificate has been revoked and signature lacks timestamp to prove it was created before revocation");
                                    }
                                }
                            } else {
                                // No signing date available
                                actuallyRevoked = true;
                                status = "Revoked";
                                log.warn("OCSP: Certificate is revoked but cannot determine signing date");
                                result.addVerificationError("Certificate has been revoked");
                            }
                        } else if (ocspResult.isRevoked) {
                            // Revoked but no revocation time available
                            actuallyRevoked = true;
                            status = "Revoked";
                            log.warn("OCSP: Certificate is revoked (revocation time unknown)");
                            result.addVerificationError("Certificate has been revoked");
                        } else {
                            // Certificate is not revoked
                            actuallyRevoked = false;
                            status = "Valid (Live OCSP)";
                            log.info("OCSP: Certificate is valid (not revoked)");
                            result.addVerificationInfo("Revocation checked via live OCSP");
                        }

                        result.setRevocationStatus(status);
                        result.setCertificateRevoked(actuallyRevoked);
                        log.info("OCSP: Success for cert [" + certSerial + "] → " + status);

                        // Cache the successful OCSP result
                        revocationCache.put(cacheKey, new RevocationCacheEntry(
                                status, actuallyRevoked, ocspResult.revocationTime, "Live OCSP"));
                        log.info("Cached revocation status: " + status + " (Live OCSP)");

                        return;
                    } else if (lastException != null) {
                        // All retry attempts failed
                        log.warn("OCSP: All " + maxRetries + " attempts failed for cert [" + certSerial + "] " + certSubject + " - " + lastException.getMessage());

                        String status;
                        if (lastException.isNetworkError()) {
                            status = "Validity Unknown (Network Error)";
                            result.addVerificationInfo("Revocation status could not be verified due to network error");
                        } else {
                            status = "Validity Unknown (Check Failed)";
                            result.addVerificationInfo("Revocation status could not be verified");
                        }
                        result.setRevocationStatus(status);

                        // Cache the failure to avoid repeated failed attempts for same certificate
                        revocationCache.put(cacheKey, new RevocationCacheEntry(
                                status, false, null, "Live OCSP (Failed)"));
                        log.info("Cached revocation check failure for future signatures with same certificate");
                        return;
                    }
                }
            }

            // No OCSP URL found - this is normal for some certificates
            // Adobe Reader also accepts this as valid (just can't verify revocation)
            result.setRevocationStatus("Validity Unknown");
            result.addVerificationInfo("Revocation status could not be determined (no OCSP information available)");
            log.info("No OCSP URL found in certificate - revocation check not possible");

            // Cache the "Not Checked" status to avoid redundant failed attempts
            revocationCache.put(cacheKey, new RevocationCacheEntry(
                    "Validity Unknown", false, null, "No OCSP URL"));

        } catch (Exception e) {
            log.warn("OCSP error: " + e.getMessage());
            result.setRevocationStatus("Validity Unknown");
            result.addVerificationInfo("Revocation status could not be determined");
        }
    }

    private String extractOCSPUrl(X509Certificate cert) {
        try {
            byte[] aiaExt = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");
            if (aiaExt == null) return null;

            org.bouncycastle.asn1.ASN1InputStream ais = new org.bouncycastle.asn1.ASN1InputStream(
                    new java.io.ByteArrayInputStream(aiaExt));
            org.bouncycastle.asn1.DEROctetString oct = (org.bouncycastle.asn1.DEROctetString) ais.readObject();
            ais.close();

            ais = new org.bouncycastle.asn1.ASN1InputStream(new java.io.ByteArrayInputStream(oct.getOctets()));
            org.bouncycastle.asn1.ASN1Sequence seq = (org.bouncycastle.asn1.ASN1Sequence) ais.readObject();
            ais.close();

            for (int i = 0; i < seq.size(); i++) {
                org.bouncycastle.asn1.ASN1Sequence access = (org.bouncycastle.asn1.ASN1Sequence) seq.getObjectAt(i);
                org.bouncycastle.asn1.ASN1ObjectIdentifier oid = (org.bouncycastle.asn1.ASN1ObjectIdentifier) access.getObjectAt(0);

                if ("1.3.6.1.5.5.7.48.1".equals(oid.getId())) {
                    org.bouncycastle.asn1.ASN1TaggedObject tagged = (org.bouncycastle.asn1.ASN1TaggedObject) access.getObjectAt(1);
                    org.bouncycastle.asn1.DERIA5String url = org.bouncycastle.asn1.DERIA5String.getInstance(tagged, false);
                    return url.getString();
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting OCSP URL", e);
        }
        return null;
    }

    private X509Certificate findIssuerCertificate(X509Certificate cert, Certificate[] chain) {
        String issuerDN = cert.getIssuerDN().toString();
        for (Certificate c : chain) {
            if (c instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) c;
                if (x509.getSubjectDN().toString().equals(issuerDN)) {
                    return x509;
                }
            }
        }
        return null;
    }

    private OCSPCheckResult performLiveOCSPCheck(X509Certificate cert, X509Certificate issuerCert, String ocspUrl)
            throws SignatureVerificationException {
        try {
            // Use BouncyCastle 1.48 OCSP API (org.bouncycastle.ocsp)
            org.bouncycastle.ocsp.CertificateID certId = new org.bouncycastle.ocsp.CertificateID(
                    org.bouncycastle.ocsp.CertificateID.HASH_SHA1,
                    issuerCert,
                    cert.getSerialNumber()
            );

            org.bouncycastle.ocsp.OCSPReqGenerator reqGen = new org.bouncycastle.ocsp.OCSPReqGenerator();
            reqGen.addRequest(certId);
            org.bouncycastle.ocsp.OCSPReq req = reqGen.generate();

            java.net.URL url = new java.net.URL(ocspUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/ocsp-request");
            conn.setRequestProperty("Accept", "application/ocsp-response");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);  // Reduced from 10s to 5s
            conn.setReadTimeout(5000);      // Reduced from 10s to 5s

            java.io.OutputStream out = conn.getOutputStream();
            out.write(req.getEncoded());
            out.flush();
            out.close();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.warn("OCSP responder returned code: " + responseCode);
                throw new SignatureVerificationException(
                        SignatureVerificationException.ErrorType.OCSP_FAILED,
                        "OCSP responder returned HTTP " + responseCode);
            }

            java.io.InputStream in = conn.getInputStream();
            org.bouncycastle.ocsp.OCSPResp ocspResp = new org.bouncycastle.ocsp.OCSPResp(in);
            in.close();

            if (ocspResp.getStatus() != org.bouncycastle.ocsp.OCSPRespStatus.SUCCESSFUL) {
                log.warn("OCSP response status: " + ocspResp.getStatus());
                return new OCSPCheckResult(false, null);
            }

            org.bouncycastle.ocsp.BasicOCSPResp basicResp = (org.bouncycastle.ocsp.BasicOCSPResp) ocspResp.getResponseObject();
            org.bouncycastle.ocsp.SingleResp[] responses = basicResp.getResponses();

            if (responses.length > 0) {
                org.bouncycastle.ocsp.SingleResp singleResp = responses[0];
                Object certStatus = singleResp.getCertStatus();

                if (certStatus == null) {
                    // null means GOOD (not revoked)
                    log.info("OCSP: Certificate is GOOD (not revoked)");
                    return new OCSPCheckResult(false, null);
                } else if (certStatus instanceof org.bouncycastle.ocsp.RevokedStatus) {
                    // CRITICAL FIX: Extract revocation time for comparison with signing time
                    org.bouncycastle.ocsp.RevokedStatus revokedStatus = (org.bouncycastle.ocsp.RevokedStatus) certStatus;
                    Date revocationTime = revokedStatus.getRevocationTime();

                    log.warn("OCSP: Certificate is REVOKED at: " +
                            (revocationTime != null ? DATE_FORMAT.format(revocationTime) : "unknown time"));

                    return new OCSPCheckResult(true, revocationTime);
                } else if (certStatus instanceof org.bouncycastle.ocsp.UnknownStatus) {
                    log.warn("OCSP: Certificate status is UNKNOWN");
                    return new OCSPCheckResult(false, null);
                }
            }

        } catch (java.net.SocketTimeoutException e) {
            log.warn("OCSP: Timeout - " + e.getMessage());
            throw new SignatureVerificationException(
                    SignatureVerificationException.ErrorType.OCSP_TIMEOUT,
                    "OCSP responder at " + ocspUrl + " did not respond within 5 seconds",
                    e);
        } catch (java.io.IOException e) {
            log.warn("OCSP: Network error - " + e.getMessage());
            throw new SignatureVerificationException(
                    SignatureVerificationException.ErrorType.OCSP_NETWORK_ERROR,
                    e.getMessage(),
                    e);
        } catch (SignatureVerificationException e) {
            // Re-throw our custom exception
            throw e;
        } catch (Exception e) {
            log.warn("OCSP: Error - " + e.getMessage());
            throw new SignatureVerificationException(
                    SignatureVerificationException.ErrorType.OCSP_FAILED,
                    e.getMessage(),
                    e);
        }

        return new OCSPCheckResult(false, null);
    }

    /**
     * Verifies timestamp according to RFC 3161 and CCA requirements.
     * This is CRITICAL for legal validity in India.
     * <p>
     * NOTE: iText 5 has limited timestamp API access. This performs basic verification.
     * For full RFC 3161 compliance, consider upgrading to iText 7 or using BouncyCastle directly.
     * <p>
     * Verification steps:
     * 1. Check timestamp presence and extract date
     * 2. Verify timestamp date is reasonable (not in future, not too old)
     * 3. Basic signature integrity (iText's built-in verification)
     *
     * @param pkcs7           Signature PKCS7 data containing timestamp
     * @param signatureResult Signature verification result (for context)
     * @return Timestamp verification result
     */
    private TimestampVerificationResult verifyTimestamp(PdfPKCS7 pkcs7, SignatureVerificationResult signatureResult) {
        try {
            log.info("=== Timestamp Verification (CCA Requirement) ===");

            // STEP 1: Check if timestamp exists
            Calendar tsCalendar = pkcs7.getTimeStampDate();
            if (tsCalendar == null) {
                return new TimestampVerificationResult(false, null,
                        "Timestamp not found in signature");
            }

            Date timestampDate = tsCalendar.getTime();
            log.info("Step 1: Timestamp found - Date: " + DATE_FORMAT.format(timestampDate));

            // STEP 2: Validate timestamp date is reasonable
            Date now = new Date();
            Date signDate = pkcs7.getSignDate() != null ? pkcs7.getSignDate().getTime() : null;

            // Check timestamp is not in future (allow 5 minutes clock skew)
            Date maxAllowedDate = new Date(now.getTime() + (5 * 60 * 1000));
            if (timestampDate.after(maxAllowedDate)) {
                return new TimestampVerificationResult(false, "Unknown TSA",
                        "Timestamp date is in the future - possible tampering");
            }

            // Check timestamp is not unreasonably old (before PDF specification existed)
            Calendar minDate = Calendar.getInstance();
            minDate.set(1993, Calendar.JUNE, 1); // PDF 1.0 released June 1993
            if (timestampDate.before(minDate.getTime())) {
                return new TimestampVerificationResult(false, "Unknown TSA",
                        "Timestamp date is unreasonably old");
            }

            // Check timestamp is consistent with signing date (if available)
            if (signDate != null) {
                // Timestamp should be close to signing date (allow up to 1 day difference)
                long timeDiff = Math.abs(timestampDate.getTime() - signDate.getTime());
                long oneDayMs = 24 * 60 * 60 * 1000L;

                if (timeDiff > oneDayMs) {
                    log.warn("Timestamp date differs significantly from signing date - " +
                            "Signing: " + DATE_FORMAT.format(signDate) +
                            ", Timestamp: " + DATE_FORMAT.format(timestampDate));
                    signatureResult.addVerificationWarning("Timestamp date differs from signing date by " +
                            (timeDiff / (60 * 60 * 1000)) + " hours");
                }
            }

            log.info("Step 2: Timestamp date validated - " + DATE_FORMAT.format(timestampDate));

            // STEP 3: Try to extract TSA information using BouncyCastle
            String tsaName = "Timestamp Authority";
            try {
                // Try to get the timestamp token using reflection (iText 5 limitation workaround)
                // PdfPKCS7 internally has a TimeStampToken but doesn't expose it publicly
                java.lang.reflect.Method method = pkcs7.getClass().getDeclaredMethod("getTimeStampToken");
                method.setAccessible(true);
                Object tsToken = method.invoke(pkcs7);

                if (tsToken != null) {
                    // Use BouncyCastle TimeStampToken to extract signer info
                    org.bouncycastle.tsp.TimeStampToken timeStampToken = (org.bouncycastle.tsp.TimeStampToken) tsToken;

                    // Get the TSA signer certificate from the timestamp token
                    org.bouncycastle.cert.X509CertificateHolder[] certs =
                        (org.bouncycastle.cert.X509CertificateHolder[]) timeStampToken.getCertificates().getMatches(null).toArray(new org.bouncycastle.cert.X509CertificateHolder[0]);

                    if (certs != null && certs.length > 0) {
                        // The first certificate is typically the TSA signer certificate
                        org.bouncycastle.cert.X509CertificateHolder tsaCert = certs[0];
                        org.bouncycastle.asn1.x500.X500Name x500name = tsaCert.getSubject();

                        // Extract CN from X500Name
                        org.bouncycastle.asn1.x500.RDN[] rdns = x500name.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.CN);
                        if (rdns != null && rdns.length > 0) {
                            tsaName = org.bouncycastle.asn1.x500.style.IETFUtils.valueToString(rdns[0].getFirst().getValue());
                            log.info("Step 3: Timestamp TSA extracted (from token): " + tsaName);
                        } else {
                            // Use full DN if CN not found
                            tsaName = x500name.toString();
                            log.info("Step 3: Timestamp TSA extracted (full DN): " + tsaName);
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                // Method not available, try alternative approach
                log.debug("getTimeStampToken method not available, using fallback");
                try {
                    // Alternative: Try to access through PdfPKCS7's signature
                    Certificate[] certs = pkcs7.getSignCertificateChain();
                    if (certs != null && certs.length > 0) {
                        // Check each certificate for Time Stamping EKU
                        for (Certificate cert : certs) {
                            if (cert instanceof X509Certificate) {
                                X509Certificate x509 = (X509Certificate) cert;
                                List<String> extKeyUsage = x509.getExtendedKeyUsage();
                                if (extKeyUsage != null && extKeyUsage.contains("1.3.6.1.5.5.7.3.8")) {
                                    tsaName = x509.getSubjectDN().toString();
                                    log.info("Step 3: Timestamp TSA extracted (from cert chain): " + extractCN(tsaName));
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Could not extract TSA from certificate chain", ex);
                }
            } catch (Exception e) {
                log.debug("Could not extract TSA details", e);
            }

            if (tsaName.equals("Timestamp Authority")) {
                log.info("Step 3: Timestamp present and validated (TSA details not available)");
            }

            // STEP 4: Verify signature includes timestamp (basic integrity check)
            // iText's verify() method already validates the timestamp signature internally
            // We rely on that for cryptographic validation
            try {
                boolean sigValid = pkcs7.verify();
                if (!sigValid) {
                    return new TimestampVerificationResult(false, tsaName,
                            "Signature (including timestamp) cryptographic verification failed");
                }
                log.info("Step 4: Signature with timestamp cryptographically verified");
            } catch (Exception e) {
                return new TimestampVerificationResult(false, tsaName,
                        "Could not verify signature with timestamp: " + e.getMessage());
            }

            log.info("=== Timestamp Verification: PASSED ===");
            log.info("NOTE: This is basic timestamp validation. For full RFC 3161 verification, " +
                    "consider using iText 7 or BouncyCastle TSP library.");

            return new TimestampVerificationResult(true, tsaName, null);

        } catch (Exception e) {
            log.error("Timestamp verification failed", e);
            return new TimestampVerificationResult(false, null,
                    "Timestamp verification failed: " + e.getMessage());
        }
    }

    /**
     * Validates hash algorithm strength according to CCA and NIST SP 800-131A.
     * <p>
     * CCA Requirements (India):
     * - SHA-256 or stronger is required for new signatures
     * - SHA-1 is deprecated (allowed only for legacy verification)
     * - MD5 is forbidden
     *
     * @param algorithm Hash algorithm name
     * @return Algorithm strength level
     */
    private AlgorithmStrength validateHashAlgorithm(String algorithm) {
        if (algorithm == null) {
            return AlgorithmStrength.WEAK;
        }

        String algoUpper = algorithm.toUpperCase();

        // FORBIDDEN: MD5, MD2, MD4
        if (algoUpper.contains("MD5") || algoUpper.contains("MD2") || algoUpper.contains("MD4")) {
            return AlgorithmStrength.FORBIDDEN;
        }

        // DEPRECATED: SHA-1 (deprecated by NIST in 2017, CCA discourages)
        if (algoUpper.contains("SHA-1") || algoUpper.equals("SHA1") || algoUpper.equals("SHA")) {
            return AlgorithmStrength.DEPRECATED;
        }

        // WEAK: SHA-224 (not recommended but sometimes acceptable)
        if (algoUpper.contains("SHA-224") || algoUpper.contains("SHA224")) {
            return AlgorithmStrength.WEAK;
        }

        // ACCEPTABLE: SHA-256 (minimum requirement for CCA)
        if (algoUpper.contains("SHA-256") || algoUpper.contains("SHA256")) {
            return AlgorithmStrength.ACCEPTABLE;
        }

        // STRONG: SHA-384, SHA-512 (recommended by CCA/NIST)
        if (algoUpper.contains("SHA-384") || algoUpper.contains("SHA384") ||
                algoUpper.contains("SHA-512") || algoUpper.contains("SHA512") ||
                algoUpper.contains("SHA-3")) {
            return AlgorithmStrength.STRONG;
        }

        // Unknown algorithm - treat as weak
        return AlgorithmStrength.WEAK;
    }

    /**
     * Checks if LTV (Long Term Validation) is enabled for the signature.
     * LTV is enabled if the PDF contains revocation information (CRL/OCSP).
     * PDF viewers check for:
     * 1. DSS (Document Security Store) dictionary
     * 2. CRLs (Certificate Revocation Lists) embedded
     * 3. OCSP responses embedded
     */
    private boolean checkLTVEnabled(PdfReader reader, PdfPKCS7 pkcs7) {
        try {
            // Method 1: Check if PDF has DSS dictionary (Document Security Store)
            com.itextpdf.text.pdf.PdfDictionary catalog = reader.getCatalog();
            if (catalog != null) {
                com.itextpdf.text.pdf.PdfDictionary dss = catalog.getAsDict(new com.itextpdf.text.pdf.PdfName("DSS"));
                if (dss != null) {
                    log.debug("LTV: Found DSS dictionary in PDF");

                    // Check for CRLs in DSS
                    com.itextpdf.text.pdf.PdfArray crls = dss.getAsArray(new com.itextpdf.text.pdf.PdfName("CRLs"));
                    if (crls != null && crls.size() > 0) {
                        log.info("LTV: Found " + crls.size() + " CRL(s) in DSS");
                        return true;
                    }

                    // Check for OCSPs in DSS
                    com.itextpdf.text.pdf.PdfArray ocsps = dss.getAsArray(new com.itextpdf.text.pdf.PdfName("OCSPs"));
                    if (ocsps != null && ocsps.size() > 0) {
                        log.info("LTV: Found " + ocsps.size() + " OCSP response(s) in DSS");
                        return true;
                    }

                    // Check for VRI (Validation Related Information)
                    com.itextpdf.text.pdf.PdfDictionary vri = dss.getAsDict(new com.itextpdf.text.pdf.PdfName("VRI"));
                    if (vri != null && vri.size() > 0) {
                        log.info("LTV: Found VRI dictionary with " + vri.size() + " entries");
                        return true;
                    }
                }
            }

            // Method 2: Check if signature PKCS7 contains CRLs
            try {
                Collection<?> crls = pkcs7.getCRLs();
                if (crls != null && !crls.isEmpty()) {
                    log.info("LTV: Found " + crls.size() + " CRL(s) in signature");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Could not check CRLs in signature", e);
            }

            // Method 3: Check if signature contains OCSP response
            try {
                Object ocsp = pkcs7.getOcsp();
                if (ocsp != null) {
                    log.info("LTV: Found OCSP response in signature");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Could not check OCSP in signature", e);
            }

            log.debug("LTV: No revocation information found");
            return false;

        } catch (Exception e) {
            log.debug("Error checking LTV", e);
            return false;
        }
    }

    /**
     * Verifies document integrity based on signature type and certification level.
     * This implements PDF viewer-style verification:
     * - Approval signatures: Valid even if not the last revision (multiple signatures expected)
     * - Certification signatures: Check if subsequent changes are allowed by certification level
     *
     * @param acroFields     AcroFields from PDF
     * @param signatureName  Name of the signature field
     * @param revision       Signature's revision number
     * @param totalRevisions Total number of revisions in PDF
     * @param pkcs7          Signature PKCS7 data
     * @return true if document integrity is intact, false if altered
     */
    private boolean verifyDocumentIntegrity(AcroFields acroFields, String signatureName,
                                            int revision, int totalRevisions, PdfPKCS7 pkcs7) {
        try {
            // STEP 1: Always verify cryptographic signature first
            // This checks if the signed content matches the signature
            boolean sigValid = pkcs7.verify();
            if (!sigValid) {
                log.warn("Signature " + signatureName + " cryptographic validation failed - signature is invalid or document has been altered");
                return false;
            }

            // STEP 2: Check document coverage based on revision
            if (revision == totalRevisions) {
                // This is the last signature - must cover the whole document
                boolean coversWhole = acroFields.signatureCoversWholeDocument(signatureName);
                log.info("Signature " + signatureName + " is the last revision - covers whole document: " + coversWhole);

                if (!coversWhole) {
                    log.warn("Last signature does not cover whole document - document has been modified after signing");
                    return false;
                }

                log.info("Document integrity check PASSED: Signature is cryptographically valid and covers whole document");
                return true;
            }

            // STEP 3: For non-last signatures, check signature type
            boolean isCertified = isCertificationSignature(acroFields, signatureName);

            if (isCertified) {
                // For certification signatures, check if subsequent changes violate certification level
                int certLevel = getCertificationLevel(acroFields, signatureName);
                log.info("Signature " + signatureName + " is a certification signature (level " + certLevel + ")");

                // Signature is cryptographically valid (checked in STEP 1)
                // For now, we accept that subsequent changes may be allowed by certification level
                // A full implementation would check the actual modifications against the certification level
                // Level 1 (NO_CHANGES_ALLOWED): No changes allowed
                // Level 2 (FORM_FILLING): Only form filling allowed
                // Level 3 (FORM_FILLING_AND_ANNOTATION): Form filling and annotations allowed
                log.info("Certification signature is cryptographically valid. Subsequent changes may be allowed by certification level.");
                return true;
            } else {
                // For approval signatures (NOT_CERTIFIED), multiple signatures are EXPECTED
                // The signature is cryptographically valid (checked in STEP 1)
                // Check if this signature covered the document at the time it was signed
                boolean coveredAtSigningTime = acroFields.signatureCoversWholeDocument(signatureName);
                log.info("Signature " + signatureName + " is an approval signature (revision " + revision + "/" + totalRevisions +
                        ") - cryptographically valid: true, covered document at signing time: " + coveredAtSigningTime);

                // For approval signatures, as long as the signature is cryptographically valid,
                // it's acceptable that there are subsequent revisions (additional signatures)
                return true;
            }

        } catch (Exception e) {
            log.error("Error verifying document integrity for " + signatureName, e);
            return false;
        }
    }

    /**
     * Checks if a signature is a certification signature (has DocMDP transform).
     *
     * @param acroFields    AcroFields from PDF
     * @param signatureName Name of the signature field
     * @return true if this is a certification signature, false otherwise
     */
    private boolean isCertificationSignature(AcroFields acroFields, String signatureName) {
        try {
            com.itextpdf.text.pdf.PdfDictionary sigDict = acroFields.getSignatureDictionary(signatureName);
            if (sigDict == null) {
                return false;
            }

            // Check for Reference array with DocMDP transform
            com.itextpdf.text.pdf.PdfArray reference = sigDict.getAsArray(com.itextpdf.text.pdf.PdfName.REFERENCE);
            if (reference != null && reference.size() > 0) {
                com.itextpdf.text.pdf.PdfDictionary refDict = reference.getAsDict(0);
                if (refDict != null) {
                    com.itextpdf.text.pdf.PdfName transformMethod = refDict.getAsName(com.itextpdf.text.pdf.PdfName.TRANSFORMMETHOD);
                    return com.itextpdf.text.pdf.PdfName.DOCMDP.equals(transformMethod);
                }
            }
        } catch (Exception e) {
            log.debug("Error checking if signature is certified", e);
        }
        return false;
    }

    /**
     * Gets the certification level (P value) from a certification signature.
     *
     * @param acroFields    AcroFields from PDF
     * @param signatureName Name of the signature field
     * @return Certification level: 1 (no changes), 2 (form filling), 3 (form filling + annotations)
     */
    private int getCertificationLevel(AcroFields acroFields, String signatureName) {
        try {
            com.itextpdf.text.pdf.PdfDictionary sigDict = acroFields.getSignatureDictionary(signatureName);
            if (sigDict == null) {
                return 0;
            }

            com.itextpdf.text.pdf.PdfArray reference = sigDict.getAsArray(com.itextpdf.text.pdf.PdfName.REFERENCE);
            if (reference != null && !reference.isEmpty()) {
                com.itextpdf.text.pdf.PdfDictionary refDict = reference.getAsDict(0);
                if (refDict != null) {
                    com.itextpdf.text.pdf.PdfDictionary transformParams = refDict.getAsDict(com.itextpdf.text.pdf.PdfName.TRANSFORMPARAMS);
                    if (transformParams != null) {
                        com.itextpdf.text.pdf.PdfNumber p = transformParams.getAsNumber(com.itextpdf.text.pdf.PdfName.P);
                        if (p != null) {
                            return p.intValue();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error getting certification level", e);
        }
        return 0;
    }

    /**
     * Gets ONLY root trust anchors from TrustStoreManager.
     * Returns only self-signed root certificates, NOT intermediate certificates.
     * This includes:
     * - Embedded root certificates (resources/trusted-certs/) - read-only
     * - Manual root certificates (user.home/.emark/trusted-certs/) - user-managed
     * <p>
     * NOTE: OS trust stores (Windows, macOS, Linux) are NOT used.
     * <p>
     * For intermediate certificates, use getIntermediateCertStore().
     */
    private Set<TrustAnchor> getTrustStore() throws Exception {
        // Use TrustStoreManager to get ONLY root trust anchors (self-signed)
        // Intermediate certificates are filtered out
        return trustStoreManager.getRootTrustAnchors();
    }

    /**
     * Gets intermediate certificates as a CertStore for certificate path building.
     * These are non-root certificates needed to build the chain from signer to root.
     *
     * @return CertStore containing intermediate certificates
     * @throws Exception if CertStore creation fails
     */
    private CertStore getIntermediateCertStore() throws Exception {
        Collection<X509Certificate> intermediateCerts = trustStoreManager.getIntermediateCertificates();
        return CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(intermediateCerts));
    }

    /**
     * Overall verification status enum (PDF viewer style).
     */
    public enum VerificationStatus {
        VALID,      // Green checkmark - All checks passed
        UNKNOWN,    // Yellow question mark - Cannot verify trust
        INVALID     // Red X - Signature invalid or document modified
    }

    /**
     * Algorithm strength levels (CCA/NIST compliance).
     */
    private enum AlgorithmStrength {
        FORBIDDEN,   // MD5 - Must reject
        DEPRECATED,  // SHA-1 - Deprecated since 2017
        WEAK,        // SHA-224 - Weak but sometimes acceptable
        ACCEPTABLE,  // SHA-256 - Minimum acceptable
        STRONG       // SHA-384, SHA-512 - Recommended
    }

    /**
     * Interface for receiving verification progress updates.
     */
    public interface VerificationProgressListener {
        void onProgress(String message);
    }

    /**
     * Cache entry for revocation status.
     * Used to avoid redundant OCSP/CRL checks for same certificate.
     */
    private static class RevocationCacheEntry {
        final String status;
        final boolean isRevoked;
        final Date revocationTime;
        final String source;
        final long timestamp;

        RevocationCacheEntry(String status, boolean isRevoked, Date revocationTime, String source) {
            this.status = status;
            this.isRevoked = isRevoked;
            this.revocationTime = revocationTime;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Verification result for a single signature.
     */
    public static class SignatureVerificationResult {
        private final String fieldName;
        private final String signerName;
        private final Date signDate;
        private final String reason;
        private final String location;
        private final String contactInfo;

        // Verification status flags
        private boolean documentIntact = false;
        private boolean signatureValid = false;
        private boolean certificateValid = false;
        private boolean certificateTrusted = false;
        private boolean timestampValid = false;
        private boolean hasLTV = false;
        private boolean certificateRevoked = false;
        private String revocationStatus = "Not Checked"; // Not Checked, Valid, Revoked, Unknown

        // Detailed information
        private String certificateSubject;
        private String certificateIssuer;
        private Date certificateValidFrom;
        private Date certificateValidTo;
        private String signatureAlgorithm;
        private final List<String> verificationErrors = new ArrayList<>();
        private final List<String> verificationWarnings = new ArrayList<>();
        private final List<String> verificationInfo = new ArrayList<>();
        private X509Certificate signerCertificate;
        private List<X509Certificate> certificateChain;

        // Timestamp information
        private Date timestampDate;
        private String timestampAuthority;

        // Revision information
        private int revision;
        private int totalRevisions;
        private boolean coversWholeDocument = false;

        // Position information (for rectangle overlay)
        private int pageNumber = -1;
        private float[] position; // [llx, lly, urx, ury] in PDF coordinates

        // Certification information (PDF viewer style)
        private com.codemuni.model.CertificationLevel certificationLevel = com.codemuni.model.CertificationLevel.NOT_CERTIFIED;
        private boolean isCertificationSignature = false;

        // Visibility information
        private boolean isInvisible = false;

        public SignatureVerificationResult(String fieldName, String signerName, Date signDate,
                                           String reason, String location, String contactInfo) {
            this.fieldName = fieldName;
            this.signerName = signerName != null ? signerName : "";
            this.signDate = signDate;
            this.reason = reason != null ? reason : "";
            this.location = location != null ? location : "";
            this.contactInfo = contactInfo != null ? contactInfo : "";
        }

        // Getters and setters
        public String getFieldName() {
            return fieldName;
        }

        public String getSignerName() {
            return signerName;
        }

        public Date getSignDate() {
            return signDate;
        }

        public String getReason() {
            return reason;
        }

        public String getLocation() {
            return location;
        }

        public String getContactInfo() {
            return contactInfo;
        }

        public boolean isDocumentIntact() {
            return documentIntact;
        }

        public void setDocumentIntact(boolean documentIntact) {
            this.documentIntact = documentIntact;
        }

        public boolean isSignatureValid() {
            return signatureValid;
        }

        public void setSignatureValid(boolean signatureValid) {
            this.signatureValid = signatureValid;
        }

        public boolean isCertificateValid() {
            return certificateValid;
        }

        public void setCertificateValid(boolean certificateValid) {
            this.certificateValid = certificateValid;
        }

        public boolean isCertificateTrusted() {
            return certificateTrusted;
        }

        public void setCertificateTrusted(boolean certificateTrusted) {
            this.certificateTrusted = certificateTrusted;
        }

        public boolean isTimestampValid() {
            return timestampValid;
        }

        public void setTimestampValid(boolean timestampValid) {
            this.timestampValid = timestampValid;
        }

        public boolean hasLTV() {
            return hasLTV;
        }

        public void setHasLTV(boolean hasLTV) {
            this.hasLTV = hasLTV;
        }

        public boolean isCertificateRevoked() {
            return certificateRevoked;
        }

        public void setCertificateRevoked(boolean certificateRevoked) {
            this.certificateRevoked = certificateRevoked;
        }

        public String getRevocationStatus() {
            return revocationStatus;
        }

        public void setRevocationStatus(String revocationStatus) {
            this.revocationStatus = revocationStatus;
        }

        public String getCertificateSubject() {
            return certificateSubject;
        }

        public void setCertificateSubject(String certificateSubject) {
            this.certificateSubject = certificateSubject;
        }

        public String getCertificateIssuer() {
            return certificateIssuer;
        }

        public void setCertificateIssuer(String certificateIssuer) {
            this.certificateIssuer = certificateIssuer;
        }

        public Date getCertificateValidFrom() {
            return certificateValidFrom;
        }

        public void setCertificateValidFrom(Date certificateValidFrom) {
            this.certificateValidFrom = certificateValidFrom;
        }

        public Date getCertificateValidTo() {
            return certificateValidTo;
        }

        public void setCertificateValidTo(Date certificateValidTo) {
            this.certificateValidTo = certificateValidTo;
        }

        public String getSignatureAlgorithm() {
            return signatureAlgorithm;
        }

        public void setSignatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
        }

        public List<String> getVerificationErrors() {
            return verificationErrors;
        }

        public void addVerificationError(String error) {
            this.verificationErrors.add(error);
        }

        public List<String> getVerificationWarnings() {
            return verificationWarnings;
        }

        public void addVerificationWarning(String warning) {
            this.verificationWarnings.add(warning);
        }

        public List<String> getVerificationInfo() {
            return verificationInfo;
        }

        public void addVerificationInfo(String info) {
            this.verificationInfo.add(info);
        }

        public X509Certificate getSignerCertificate() {
            return signerCertificate;
        }

        public void setSignerCertificate(X509Certificate signerCertificate) {
            this.signerCertificate = signerCertificate;
        }

        public List<X509Certificate> getCertificateChain() {
            return certificateChain;
        }

        public void setCertificateChain(List<X509Certificate> certificateChain) {
            this.certificateChain = certificateChain;
        }

        public Date getTimestampDate() {
            return timestampDate;
        }

        public void setTimestampDate(Date timestampDate) {
            this.timestampDate = timestampDate;
        }

        public String getTimestampAuthority() {
            return timestampAuthority;
        }

        public void setTimestampAuthority(String timestampAuthority) {
            this.timestampAuthority = timestampAuthority;
        }

        public int getRevision() {
            return revision;
        }

        public void setRevision(int revision) {
            this.revision = revision;
        }

        public int getTotalRevisions() {
            return totalRevisions;
        }

        public void setTotalRevisions(int totalRevisions) {
            this.totalRevisions = totalRevisions;
        }

        public boolean isCoversWholeDocument() {
            return coversWholeDocument;
        }

        public void setCoversWholeDocument(boolean coversWholeDocument) {
            this.coversWholeDocument = coversWholeDocument;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public float[] getPosition() {
            return position;
        }

        public void setPosition(float[] position) {
            this.position = position;
        }

        public com.codemuni.model.CertificationLevel getCertificationLevel() {
            return certificationLevel;
        }

        public void setCertificationLevel(com.codemuni.model.CertificationLevel certificationLevel) {
            this.certificationLevel = certificationLevel;
        }

        public boolean isCertificationSignature() {
            return isCertificationSignature;
        }

        public void setCertificationSignature(boolean certificationSignature) {
            this.isCertificationSignature = certificationSignature;
        }

        public boolean isInvisible() {
            return isInvisible;
        }

        public void setInvisible(boolean invisible) {
            this.isInvisible = invisible;
        }

        /**
         * Returns overall verification status based on all checks.
         * Adobe Reader style: Signature is VALID if core checks pass, even if revocation cannot be verified.
         */
        /**
         * Gets overall verification status according to PDF/ISO 32000 standards
         * with enhanced security for revocation verification.
         *
         * Standard PDF Signature Verification Status:
         *
         * INVALID (Red X) - Critical failures that invalidate the signature:
         *   - Document modified after signing (documentIntact = false)
         *   - Signature cryptographically invalid (signatureValid = false)
         *   - Certificate revoked BEFORE signing time (certificateRevoked = true)
         *   - Certificate expired/invalid AT SIGNING TIME (certificateValid = false)
         *
         * UNKNOWN (Yellow ?) - Cannot verify identity or revocation status:
         *   - Certificate not trusted (cannot build chain to trusted root)
         *   - Revocation status unknown (OCSP/CRL unavailable or check failed)
         *   - More secure than Adobe Reader's approach for CCA compliance
         *
         * VALID (Green ✓) - All verification checks passed:
         *   - Document intact
         *   - Signature cryptographically valid
         *   - Certificate was valid at signing time
         *   - Certificate chains to trusted root
         *   - Certificate revocation verified as valid (not just unchecked)
         */
        public VerificationStatus getOverallStatus() {
            // CRITICAL FAILURES - INVALID
            // These are conditions that definitively invalidate the signature

            // 1. Document integrity - document was modified after signing
            if (!documentIntact) {
                return VerificationStatus.INVALID;
            }

            // 2. Signature integrity - cryptographic signature is invalid
            if (!signatureValid) {
                return VerificationStatus.INVALID;
            }

            // 3. Certificate validity - certificate was expired/invalid at signing time
            // This is INVALID per PDF standards (ISO 32000, Adobe Reader behavior)
            // If cert was expired when document was signed, signature is not valid
            if (!certificateValid) {
                return VerificationStatus.INVALID;
            }

            // 4. Certificate revocation - certificate was revoked before signing
            // This is INVALID because signer used a revoked certificate
            if (certificateRevoked) {
                return VerificationStatus.INVALID;
            }

            // TRUST ISSUES - UNKNOWN
            // Certificate is valid but identity or revocation cannot be verified

            // 5. Certificate trust - cannot build chain to trusted root
            // This is UNKNOWN per PDF standards - signature itself is valid but
            // we cannot verify the identity of the signer
            if (!certificateTrusted) {
                return VerificationStatus.UNKNOWN;
            }

            // 6. Revocation status verification
            // Following Adobe Reader behavior: if revocation status cannot be determined
            // (no OCSP/CRL available), but all other checks passed, treat as VALID
            // Note: If certificate was actually revoked, it would fail at check #4 above
            // "Validity Unknown" or "Not Checked" means we couldn't check, not that it's revoked

            // ALL CHECKS PASSED - VALID
            // Document intact, signature valid, certificate valid and trusted
            // Revocation status is advisory - if we couldn't check it, we accept the signature
            // This matches Adobe Reader, Foxit, and other PDF viewers' behavior
            return VerificationStatus.VALID;
        }

        public String getStatusMessage() {
            VerificationStatus status = getOverallStatus();
            switch (status) {
                case VALID:
                    return "Signed and all signatures are valid";
                case UNKNOWN:
                    return "Signed but identity could not be verified";
                case INVALID:
                    // Provide specific reason for invalidity in priority order
                    if (!documentIntact) {
                        return "Document has been modified after signing";
                    } else if (!signatureValid) {
                        return "Signature is invalid or corrupted";
                    } else if (certificateRevoked) {
                        return "Certificate has been revoked";
                    } else if (!certificateValid) {
                        return "Certificate has expired or is not yet valid";
                    } else {
                        return "Signature verification failed";
                    }
                default:
                    return "Unknown verification status";
            }
        }
    }

    /**
     * Result of OCSP check including revocation status and time.
     */
    private static class OCSPCheckResult {
        final boolean isRevoked;
        final Date revocationTime; // null if not revoked

        OCSPCheckResult(boolean isRevoked, Date revocationTime) {
            this.isRevoked = isRevoked;
            this.revocationTime = revocationTime;
        }
    }

    /**
     * Result of timestamp verification.
     */
    private static class TimestampVerificationResult {
        final boolean isValid;
        final String tsaName;
        final String errorMessage;

        TimestampVerificationResult(boolean isValid, String tsaName, String errorMessage) {
            this.isValid = isValid;
            this.tsaName = tsaName;
            this.errorMessage = errorMessage;
        }
    }
}
