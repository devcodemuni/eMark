================================================================================
Trusted Root Certificates for Signature Verification
================================================================================

This directory contains embedded root CA certificates that are trusted by the
application for digital signature verification.

IMPORTANT:
- These certificates are READ-ONLY and packaged with the application
- Cannot be modified by users
- Used as base trust anchors for signature verification

================================================================================
SUPPORTED FORMATS:
================================================================================

- .pem (PEM-encoded X.509 certificates)
- .der (DER-encoded X.509 certificates)
- .cer (Certificate files)
- .crt (Certificate files)

================================================================================
ADDING CUSTOM TRUST CERTIFICATES:
================================================================================

Users can add their own trusted certificates via the Settings UI.

User certificates are stored separately in:
    {user.home}/.emark/trusted-certs/

These can be added/removed through the application settings.

================================================================================
CERTIFICATE SOURCES:
================================================================================

The application uses certificates from TWO sources ONLY:

1. EMBEDDED (this folder)
   - Location: resources/trusted-certs/
   - Read-only
   - Packaged with application

2. MANUAL (user-added)
   - Location: {user.home}/.emark/trusted-certs/
   - User-managed via Settings
   - Can be added/removed

IMPORTANT: OS trust stores (Windows, macOS, Linux) are NOT used for
signature verification. Only embedded and manual certificates are trusted.

================================================================================
CERTIFICATE VERIFICATION:
================================================================================

During signature verification, the application will:

1. Check signature cryptographic validity
2. Verify certificate chain
3. Check if certificate is trusted against embedded + manual sources ONLY
4. Validate certificate expiration
5. Check timestamp (if present)
6. Verify LTV support (if present)

NOTE: The application does NOT use OS/system trust stores for verification.

================================================================================
SAMPLE CERTIFICATES:
================================================================================

You can add your organization's root CA certificates here.

Example filenames:
- company-root-ca.pem
- internal-ca.der
- trusted-issuer.cer

================================================================================
SECURITY NOTES:
================================================================================

⚠ Only add certificates from trusted sources!
⚠ Adding untrusted certificates may compromise signature verification!
⚠ Verify certificate fingerprints before adding!

================================================================================
MORE INFORMATION:
================================================================================

For more information about certificate management and signature verification,
please refer to the application documentation.

Version: 1.0
Last Updated: 2025-10-10
================================================================================
