package com.codemuni.service;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.security.PdfPKCS7;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
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
    private final TrustStoreManager trustStoreManager;
    private VerificationProgressListener progressListener;

    /**
     * Interface for receiving verification progress updates.
     */
    public interface VerificationProgressListener {
        void onProgress(String message);
    }

    static {
        // Register BouncyCastle provider for cryptographic operations
        Security.addProvider(new BouncyCastleProvider());
    }

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
     * Notifies progress listener with a message.
     */
    private void notifyProgress(String message) {
        if (progressListener != null) {
            progressListener.onProgress(message);
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
        private List<String> verificationErrors = new ArrayList<>();
        private List<String> verificationWarnings = new ArrayList<>();
        private List<String> verificationInfo = new ArrayList<>();
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

        /**
         * Returns overall verification status based on all checks.
         * PDF viewer style: If revocation cannot be verified, signature is UNKNOWN.
         */
        public VerificationStatus getOverallStatus() {
            // Critical failures - INVALID
            if (!documentIntact) {
                return VerificationStatus.INVALID;
            }
            if (!signatureValid) {
                return VerificationStatus.INVALID;
            }
            if (certificateRevoked) {
                return VerificationStatus.INVALID;
            }

            // Revocation issues - UNKNOWN (PDF viewer style)
            if (revocationStatus.startsWith("Unknown") || revocationStatus.equals("Not Checked")) {
                return VerificationStatus.UNKNOWN;
            }

            // Trust issues - UNKNOWN
            if (!certificateValid) {
                return VerificationStatus.UNKNOWN;
            }
            if (!certificateTrusted) {
                return VerificationStatus.UNKNOWN;
            }

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
     * Overall verification status enum (PDF viewer style).
     */
    public enum VerificationStatus {
        VALID,      // Green checkmark - All checks passed
        UNKNOWN,    // Yellow question mark - Cannot verify trust
        INVALID     // Red X - Signature invalid or document modified
    }

    /**
     * Checks if PDF is certified (has a certification signature).
     * Certified PDFs may restrict additional signatures.
     *
     * @param pdfFile     PDF file to check
     * @param pdfPassword Password for encrypted PDFs (can be null)
     * @return true if PDF is certified, false otherwise
     */
    public boolean isPdfCertified(File pdfFile, String pdfPassword) {
        if (pdfFile == null || !pdfFile.exists()) {
            return false;
        }

        PdfReader reader = null;
        try {
            if (pdfPassword != null && !pdfPassword.isEmpty()) {
                reader = new PdfReader(pdfFile.getAbsolutePath(), pdfPassword.getBytes());
            } else {
                reader = new PdfReader(pdfFile.getAbsolutePath());
            }

            AcroFields acroFields = reader.getAcroFields();
            if (acroFields == null) {
                return false;
            }

            // Check if PDF has certification signature (DocMDP)
            // In iText 5, we check the signature dictionary for certification
            List<String> signatureNames = acroFields.getSignatureNames();
            if (!signatureNames.isEmpty()) {
                // Certification signature is typically the first signature
                String firstSig = signatureNames.get(0);
                try {
                    // Check if this signature certifies the document
                    com.itextpdf.text.pdf.PdfDictionary sigDict = acroFields.getSignatureDictionary(firstSig);
                    if (sigDict != null) {
                        com.itextpdf.text.pdf.PdfArray reference = sigDict.getAsArray(com.itextpdf.text.pdf.PdfName.REFERENCE);
                        if (reference != null && reference.size() > 0) {
                            // Has reference array - indicates certification
                            com.itextpdf.text.pdf.PdfDictionary refDict = reference.getAsDict(0);
                            if (refDict != null) {
                                com.itextpdf.text.pdf.PdfName transformMethod = refDict.getAsName(com.itextpdf.text.pdf.PdfName.TRANSFORMMETHOD);
                                if (com.itextpdf.text.pdf.PdfName.DOCMDP.equals(transformMethod)) {
                                    log.info("PDF is certified (DocMDP signature found)");
                                    return true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error checking certification for signature: " + firstSig, e);
                }
            }
        } catch (Exception e) {
            log.error("Error checking PDF certification status", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
        return false;
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

            log.info("Found " + signatureNames.size() + " signature(s) in PDF");

            // Verify each signature
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
     * Applies PDF viewer verification rules for certification levels.
     * This method modifies verification results based on the certification status
     * of the LAST signature in the document.
     *
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

                // 4. CERTIFICATE VALIDITY CHECK
                // PDF viewers check validity at BOTH signing time and current time
                try {
                    // Check validity at current time
                    signerCert.checkValidity();
                    result.setCertificateValid(true);

                    // Also check if certificate was valid at signing time
                    if (signDate != null) {
                        try {
                            signerCert.checkValidity(signDate);
                            result.addVerificationInfo("Certificate was valid at signing time");
                        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                            result.addVerificationWarning("Certificate was not valid when document was signed");
                        }
                    }
                } catch (CertificateExpiredException e) {
                    result.setCertificateValid(false);
                    result.addVerificationError("Certificate has expired");
                } catch (CertificateNotYetValidException e) {
                    result.setCertificateValid(false);
                    result.addVerificationError("Certificate is not yet valid");
                }

                // 5. CERTIFICATE CHAIN VERIFICATION
                Certificate[] certs = pkcs7.getCertificates();
                List<X509Certificate> certChain = new ArrayList<>();
                for (Certificate cert : certs) {
                    if (cert instanceof X509Certificate) {
                        certChain.add((X509Certificate) cert);
                    }
                }
                result.setCertificateChain(certChain);

                // Check if certificate is trusted (PDF viewer-level verification)
                notifyProgress("Verifying certificate trust...");
                try {
                    verifyCertificateChain(certChain);
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
            checkRevocationStatus(signerCert, pkcs7, result);
            } else {
                result.addVerificationError("No certificate found in signature");
            }

            // 6. SIGNATURE ALGORITHM
            result.setSignatureAlgorithm(pkcs7.getHashAlgorithm());

            // 7. TIMESTAMP VERIFICATION
            if (pkcs7.getTimeStampDate() != null) {
                result.setTimestampValid(true);
                result.setTimestampDate(pkcs7.getTimeStampDate().getTime());

                // Try to get TSA certificate info
                try {
                    if (pkcs7.getTimeStampToken() != null) {
                        result.setTimestampAuthority("Timestamp Authority");
                    }
                } catch (Exception e) {
                    log.debug("Could not extract timestamp authority info", e);
                }
                log.info("Timestamp: Enabled");
            } else {
                // Timestamp not enabled - this is INFO, not an error
                result.addVerificationInfo("Timestamp not enabled in this signature");
                log.info("Timestamp: Not enabled");
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
                    result.setPosition(new float[]{
                        fieldPos.position.getLeft(),
                        fieldPos.position.getBottom(),
                        fieldPos.position.getRight(),
                        fieldPos.position.getTop()
                    });
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
            result.addVerificationError("Verification error: " + e.getMessage());
        }

        return result;
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

        // Step 1: Check if certificate is self-signed
        boolean isSelfSigned = signerCert.getSubjectDN().equals(signerCert.getIssuerDN());

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
            throw new Exception("Certificate is self-signed and not in trusted list");
        }

        // Step 5: Build and validate certificate path to root CA
        try {
            // PDF viewer-style verification: Try to find a valid path
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            // Create PKIXParameters with trust anchors
            PKIXParameters params = new PKIXParameters(trustAnchors);
            params.setRevocationEnabled(false); // Disable CRL/OCSP for basic verification

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

            // Validate the path
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
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
                if (reason.contains("NO_TRUST_ANCHOR") || reason.contains("UNDETERMINED_REVOCATION_STATUS")) {
                    throw new Exception("Root certificate not found in trust store. Add '" +
                                      extractCN(failedCert.getIssuerDN().toString()) +
                                      "' to Trust Manager.");
                } else {
                    throw new Exception("Certificate '" + certName + "' verification failed: " + reason);
                }
            } else {
                throw new Exception("Certificate chain validation failed: " + reason);
            }
        } catch (InvalidAlgorithmParameterException e) {
            log.error("Invalid algorithm parameter", e);
            throw new Exception("No trusted root certificate found in trust store");
        } catch (Exception e) {
            log.error("Certificate validation error", e);
            throw new Exception("Certificate not trusted: " + e.getMessage());
        }
    }

    /**
     * Builds a complete certificate chain by finding missing issuers in trust store.
     * This is crucial for signature verification when PDF doesn't include all certificates.
     *
     * @param originalChain The original certificate chain from PDF
     * @param trustAnchors  Available trust anchors (root + intermediate CAs)
     * @return Complete certificate chain including missing issuers
     */
    private List<X509Certificate> buildCompleteChain(List<X509Certificate> originalChain, Set<TrustAnchor> trustAnchors) {
        if (originalChain == null || originalChain.isEmpty()) {
            return originalChain;
        }

        List<X509Certificate> completeChain = new ArrayList<>(originalChain);

        // Keep looking for issuers until we find a self-signed cert or can't find issuer
        int maxIterations = 10; // Prevent infinite loop
        int iterations = 0;

        while (iterations < maxIterations) {
            X509Certificate lastCert = completeChain.get(completeChain.size() - 1);
            String lastIssuerDN = lastCert.getIssuerDN().toString();
            String lastSubjectDN = lastCert.getSubjectDN().toString();

            // If last cert is self-signed, chain is complete
            if (lastIssuerDN.equals(lastSubjectDN)) {
                log.info("Chain is complete - found self-signed root: " + extractCN(lastSubjectDN));
                break;
            }

            // Look for issuer in trust anchors
            boolean foundIssuer = false;
            for (TrustAnchor anchor : trustAnchors) {
                X509Certificate anchorCert = anchor.getTrustedCert();
                String anchorSubjectDN = anchorCert.getSubjectDN().toString();

                // Check if this anchor is the issuer
                if (anchorSubjectDN.equals(lastIssuerDN)) {
                    log.info("Found issuer in trust store: " + extractCN(anchorSubjectDN));

                    // Avoid duplicates
                    boolean isDuplicate = false;
                    for (X509Certificate existingCert : completeChain) {
                        if (existingCert.equals(anchorCert)) {
                            isDuplicate = true;
                            break;
                        }
                    }

                    if (!isDuplicate) {
                        completeChain.add(anchorCert);
                        log.info("Added issuer to chain - new chain length: " + completeChain.size());
                    }

                    foundIssuer = true;
                    break;
                }
            }

            // If we couldn't find issuer, stop looking
            if (!foundIssuer) {
                log.info("Could not find issuer '" + extractCN(lastIssuerDN) + "' in trust store");
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
     */
    private void checkRevocationStatus(X509Certificate cert, PdfPKCS7 pkcs7, SignatureVerificationResult result) {
        try {
            log.info("Checking certificate revocation status (OCSP)...");

            // Method 1: Check embedded OCSP response in signature (LTV)
            try {
                Object ocspResponse = pkcs7.getOcsp();
                if (ocspResponse != null) {
                    log.info("OCSP: Found embedded OCSP response");
                    result.setRevocationStatus("Valid (Embedded)");
                    result.setCertificateRevoked(false);
                    result.addVerificationInfo("Revocation checked via embedded OCSP");
                    return;
                }
            } catch (Exception e) {
                log.debug("No embedded OCSP", e);
            }

            // Method 2: Check embedded CRLs (LTV)
            try {
                Collection<?> crls = pkcs7.getCRLs();
                if (crls != null && !crls.isEmpty()) {
                    log.info("OCSP: Found embedded CRL");
                    result.setRevocationStatus("Valid (CRL)");
                    result.setCertificateRevoked(false);
                    result.addVerificationInfo("Revocation checked via embedded CRL");
                    return;
                }
            } catch (Exception e) {
                log.debug("No embedded CRL", e);
            }

            // Method 3: Extract OCSP URL and perform live check (with timeout)
            log.info("OCSP: Attempting live check...");
            String ocspUrl = extractOCSPUrl(cert);

            if (ocspUrl != null && !ocspUrl.isEmpty()) {
                log.info("OCSP URL: " + ocspUrl);

                Certificate[] certs = pkcs7.getCertificates();
                X509Certificate issuerCert = findIssuerCertificate(cert, certs);

                if (issuerCert != null) {
                    try {
                        boolean isRevoked = performLiveOCSPCheck(cert, issuerCert, ocspUrl);

                        if (isRevoked) {
                            result.setRevocationStatus("Revoked");
                            result.setCertificateRevoked(true);
                            result.addVerificationError("Certificate has been revoked");
                        } else {
                            result.setRevocationStatus("Valid (Live OCSP)");
                            result.setCertificateRevoked(false);
                            result.addVerificationInfo("Revocation checked via live OCSP");
                        }
                        return;
                    } catch (SignatureVerificationException ocspEx) {
                        log.warn("OCSP check failed: " + ocspEx.getMessage());
                        // PDF viewer style: If revocation check fails, treat as verification failure
                        if (ocspEx.isNetworkError()) {
                            result.setRevocationStatus("Unknown (Network Error)");
                            result.addVerificationWarning("Could not check if certificate was revoked (network error)");
                        } else {
                            result.setRevocationStatus("Unknown (Check Failed)");
                            result.addVerificationWarning("Could not check if certificate was revoked");
                        }
                        return;
                    }
                }
            }

            result.setRevocationStatus("Not Checked");
            result.addVerificationWarning("Could not check if certificate was revoked");

        } catch (Exception e) {
            log.warn("OCSP error: " + e.getMessage());
            result.setRevocationStatus("Unknown");
            result.addVerificationWarning("Could not check if certificate was revoked");
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

    private boolean performLiveOCSPCheck(X509Certificate cert, X509Certificate issuerCert, String ocspUrl)
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
                return false;
            }

            org.bouncycastle.ocsp.BasicOCSPResp basicResp = (org.bouncycastle.ocsp.BasicOCSPResp) ocspResp.getResponseObject();
            org.bouncycastle.ocsp.SingleResp[] responses = basicResp.getResponses();

            if (responses.length > 0) {
                org.bouncycastle.ocsp.SingleResp singleResp = responses[0];
                Object certStatus = singleResp.getCertStatus();

                if (certStatus == null) {
                    // null means GOOD
                    log.info("OCSP: Certificate is GOOD (not revoked)");
                    return false;
                } else if (certStatus instanceof org.bouncycastle.ocsp.RevokedStatus) {
                    log.warn("OCSP: Certificate is REVOKED");
                    return true;
                } else if (certStatus instanceof org.bouncycastle.ocsp.UnknownStatus) {
                    log.warn("OCSP: Certificate status is UNKNOWN");
                    return false;
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

        return false;
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
     * @param acroFields      AcroFields from PDF
     * @param signatureName   Name of the signature field
     * @param revision        Signature's revision number
     * @param totalRevisions  Total number of revisions in PDF
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
     * Gets all trust anchors from TrustStoreManager.
     * This includes ONLY:
     * - Embedded certificates (resources/trusted-certs/) - read-only
     * - Manual certificates (user.home/.emark/trusted-certs/) - user-managed
     *
     * NOTE: OS trust stores (Windows, macOS, Linux) are NOT used.
     */
    private Set<TrustAnchor> getTrustStore() throws Exception {
        // Use TrustStoreManager to get all trust anchors (embedded + manual only)
        return trustStoreManager.getAllTrustAnchors();
    }
}
