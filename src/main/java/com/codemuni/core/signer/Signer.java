package com.codemuni.core.signer;

import com.codemuni.core.exception.CertificateChainException;
import com.codemuni.core.exception.SigningProcessException;
import com.codemuni.core.exception.TSAConfigurationException;
import com.codemuni.core.exception.UserCancelledPasswordEntryException;
import com.codemuni.core.keyStoresProvider.KeyStoreProvider;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.security.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.security.KeyStoreException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Signer {

    private static final Log log = LogFactory.getLog(Signer.class);
    private static final int BASE_SIGNATURE_SIZE = 10000;
    private static final int CERTIFICATE_SIZE_ESTIMATE = 20_000;  // Increased for larger certs and chains
    private static final int TIMESTAMP_SIZE_ESTIMATE = 20_000;    // Increased for TSA responses
    private static final int LTV_SIZE_ESTIMATE = 25_00_000;      // 2,500,000 bytes (2.5MB) for LTV with CRLs + OCSP
    private static final int CMS_OVERHEAD = 15_000;              // Increased for larger CMS structures
    private static final int SAFETY_MARGIN = 50_000;             // Increased safety margin for 2048-bit keys

    public static String buildDetailedMessage(String context, Exception e) {
        String baseMsg = context != null ? context : "An error occurred";
        String exceptionType = e.getClass().getSimpleName();
        String exceptionMsg = e.getMessage() != null ? e.getMessage() : "No message";

        // Extract root cause if nested
        Throwable rootCause = getRootCause(e);
        if (rootCause != null && rootCause != e) {
            String rootType = rootCause.getClass().getSimpleName();
            String rootMsg = rootCause.getMessage() != null ? rootCause.getMessage() : "No root cause message";
            return String.format("%s [%s: %s] | Root Cause: [%s: %s]",
                    baseMsg, exceptionType, exceptionMsg, rootType, rootMsg);
        }

        return String.format("%s [%s: %s]", baseMsg, exceptionType, exceptionMsg);
    }

    public static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable.getCause();
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public String sign(PdfReader reader, KeyStoreProvider keyStoreProvider, String signatureCreator, String signatureFieldName, AppearanceOptions options, CustomTSAClientBouncyCastle tsaClient) throws UserCancelledPasswordEntryException {

        if (reader == null) {
            throw new IllegalArgumentException("PdfReader cannot be null.");
        }

        PdfStamper stamper = null;

        try (ByteArrayOutputStream signedPdfOutputStream = new ByteArrayOutputStream()) {

            if (options.isTimestampEnabled()) {
                if (tsaClient == null) throw new TSAConfigurationException("TSA client is not configured.");
                if (tsaClient.getUrl() == null) throw new TSAConfigurationException("TSA URL is not configured.");
                if (tsaClient.getUrl().isEmpty()) throw new TSAConfigurationException("TSA URL is empty.");
            }

            // Validate certificate chain
            Certificate[] certChain = keyStoreProvider.getCertificateChain();

            stamper = PdfStamper.createSignature(reader, signedPdfOutputStream, '\0', null, true);
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();

            SignatureAppearanceBuilder appearanceHandler = new SignatureAppearanceBuilder(keyStoreProvider, options);
            appearanceHandler.configureAppearance(signatureFieldName, appearance, signatureCreator);

            // Watermark
            if (options.getWatermarkImage() != null)
                applyWatermarkToSignatureAppearance(appearance, options);

            ExternalDigest digest = new BouncyCastleDigest();
            ExternalSignature signature = new PrivateKeySignature(
                    keyStoreProvider.getPrivateKey(), DigestAlgorithms.SHA256, keyStoreProvider.getProvider());

            List<CrlClient> crlList = options.isLtvEnabled() ? prepareLtvComponents(certChain) : new ArrayList<>();
            OcspClient ocspClient = options.isLtvEnabled() ? new OcspClientBouncyCastle(null) : null;

            int estimatedSize = estimateSignatureSize(certChain.length, tsaClient != null && options.isTimestampEnabled(), options.isLtvEnabled());

            MakeSignature.signDetached(
                    appearance, digest, signature, certChain,
                    crlList, ocspClient, tsaClient, estimatedSize, MakeSignature.CryptoStandard.CADES
            );

            return Base64.getEncoder().encodeToString(signedPdfOutputStream.toByteArray());

        } catch (SignatureException e) {
            throw new UserCancelledPasswordEntryException("Signature cancelled by user.", e);
        } catch (KeyStoreException e) {
            throw new CertificateChainException("Unable to fetch certificate chain.", e);
        } catch (Exception e) {
            String detailedMessage = buildDetailedMessage("Signing PDF failed", e);
            throw new SigningProcessException(detailedMessage, e);
        } finally {
            try {
                if (stamper != null) stamper.close();
                reader.close();
            } catch (Exception e) {
                log.error("Failed to close resources" + e.getMessage(), e);
            }
        }
    }

    private void applyWatermarkToSignatureAppearance(PdfSignatureAppearance appearance, AppearanceOptions options) {
        int[] coords = options.getCoordinates();
        float rectWidth = coords[2] - coords[0]; // urx - llx
        float rectHeight = coords[3] - coords[1]; // ury - lly

        options.getWatermarkImage().scaleToFit(rectWidth, rectHeight);

        float imageWidth = options.getWatermarkImage().getScaledWidth();
        float imageHeight = options.getWatermarkImage().getScaledHeight();

        float xOffset = (rectWidth - imageWidth) / 2;
        float yOffset = (rectHeight - imageHeight) / 2;
        options.getWatermarkImage().setAbsolutePosition(xOffset, yOffset);

        PdfTemplate background = appearance.getLayer(0); // Layer 0 = background
        PdfGState gState = new PdfGState();
        gState.setFillOpacity(0.20f); // 15% transparent

        background.saveState();
        background.setGState(gState);
        try {
            background.addImage(options.getWatermarkImage());
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to add watermark to signature appearance.", e);
        }
        background.restoreState();
    }

    private int estimateSignatureSize(int certCount, boolean withTimestamp, boolean withLTV) {
        return BASE_SIGNATURE_SIZE + (certCount * CERTIFICATE_SIZE_ESTIMATE) +
                (withTimestamp ? TIMESTAMP_SIZE_ESTIMATE : 0) +
                (withLTV ? LTV_SIZE_ESTIMATE : 0) + CMS_OVERHEAD + SAFETY_MARGIN;
    }

    private List<CrlClient> prepareLtvComponents(Certificate[] certChain) {
        List<CrlClient> crlList = new ArrayList<>();
        crlList.add(new CrlClientOnline(certChain));
        return crlList;
    }

}