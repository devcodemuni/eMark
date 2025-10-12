package com.codemuni.service;

/**
 * Exception thrown during signature verification operations.
 * Provides specific error types for different verification failures.
 */
public class SignatureVerificationException extends Exception {

    /**
     * Type of verification failure.
     */
    public enum ErrorType {
        // Network/Communication errors
        OCSP_TIMEOUT("OCSP server timeout"),
        OCSP_NETWORK_ERROR("Network error during OCSP check"),
        OCSP_FAILED("OCSP check failed"),
        
        // Certificate errors
        CERTIFICATE_EXPIRED("Certificate has expired"),
        CERTIFICATE_NOT_YET_VALID("Certificate is not yet valid"),
        CERTIFICATE_REVOKED("Certificate has been revoked"),
        CERTIFICATE_NOT_TRUSTED("Certificate is not trusted"),
        CERTIFICATE_CHAIN_INVALID("Certificate chain is invalid"),
        
        // Signature errors
        SIGNATURE_INVALID("Signature is invalid"),
        DOCUMENT_MODIFIED("Document has been modified after signing"),
        SIGNATURE_CORRUPT("Signature data is corrupt"),
        
        // General errors
        VERIFICATION_FAILED("Verification failed"),
        UNKNOWN_ERROR("Unknown error");

        private final String message;

        ErrorType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private final ErrorType errorType;
    private final String detailMessage;

    /**
     * Creates a new SignatureVerificationException with error type and message.
     *
     * @param errorType Type of verification error
     * @param message   Detailed error message
     */
    public SignatureVerificationException(ErrorType errorType, String message) {
        super(errorType.getMessage() + ": " + message);
        this.errorType = errorType;
        this.detailMessage = message;
    }

    /**
     * Creates a new SignatureVerificationException with error type, message, and cause.
     *
     * @param errorType Type of verification error
     * @param message   Detailed error message
     * @param cause     Underlying cause
     */
    public SignatureVerificationException(ErrorType errorType, String message, Throwable cause) {
        super(errorType.getMessage() + ": " + message, cause);
        this.errorType = errorType;
        this.detailMessage = message;
    }

    /**
     * Creates a new SignatureVerificationException with just error type.
     *
     * @param errorType Type of verification error
     */
    public SignatureVerificationException(ErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.detailMessage = errorType.getMessage();
    }

    /**
     * Gets the error type.
     *
     * @return Error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Gets the detailed error message.
     *
     * @return Detailed message
     */
    public String getDetailMessage() {
        return detailMessage;
    }

    /**
     * Gets a user-friendly error message.
     *
     * @return User-friendly message
     */
    public String getUserFriendlyMessage() {
        switch (errorType) {
            case OCSP_TIMEOUT:
                return "Could not verify certificate revocation status: Server timeout. The certificate authority's server is not responding.";
            case OCSP_NETWORK_ERROR:
                return "Could not verify certificate revocation status: Network error. Please check your internet connection.";
            case OCSP_FAILED:
                return "Could not verify certificate revocation status: " + detailMessage;
            case CERTIFICATE_EXPIRED:
                return "The certificate has expired and is no longer valid.";
            case CERTIFICATE_NOT_YET_VALID:
                return "The certificate is not yet valid. Check your system date and time.";
            case CERTIFICATE_REVOKED:
                return "The certificate has been revoked by the certificate authority. Do not trust this signature.";
            case CERTIFICATE_NOT_TRUSTED:
                return "The certificate is not trusted. Add the root certificate to Trust Manager to trust it.";
            case CERTIFICATE_CHAIN_INVALID:
                return "The certificate chain is invalid or incomplete.";
            case SIGNATURE_INVALID:
                return "The signature is invalid or has been tampered with.";
            case DOCUMENT_MODIFIED:
                return "The document has been modified after it was signed.";
            case SIGNATURE_CORRUPT:
                return "The signature data is corrupt or unreadable.";
            default:
                return errorType.getMessage() + (detailMessage != null ? ": " + detailMessage : "");
        }
    }

    /**
     * Checks if this is a network-related error.
     *
     * @return true if network error
     */
    public boolean isNetworkError() {
        return errorType == ErrorType.OCSP_TIMEOUT || 
               errorType == ErrorType.OCSP_NETWORK_ERROR;
    }

    /**
     * Checks if this is a critical error (signature invalid/document modified).
     *
     * @return true if critical error
     */
    public boolean isCriticalError() {
        return errorType == ErrorType.SIGNATURE_INVALID || 
               errorType == ErrorType.DOCUMENT_MODIFIED ||
               errorType == ErrorType.CERTIFICATE_REVOKED;
    }
}
