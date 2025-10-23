package com.codemuni.core.signer;

import com.codemuni.core.exception.NotADigitalSignatureException;
import com.codemuni.core.keyStoresProvider.KeyStoreProvider;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfGState;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfTemplate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Objects;

import static com.codemuni.core.keyStoresProvider.X509SubjectUtils.*;

/**
 * Handles the configuration and appearance of PDF digital signatures.
 * This class manages signature visualization, rendering modes, watermarks,
 * and certificate information display.
 */
public class SignatureAppearanceBuilder {

    private static final Log log = LogFactory.getLog(SignatureAppearanceBuilder.class);
    private static final int REQUIRED_COORDINATE_COUNT = 4;
    private static final float DEFAULT_WATERMARK_OPACITY = 0.01f;
    private static final int FIELD_NAME_RANDOM_RANGE = 900_000;

    private final KeyStoreProvider keyStoreProvider;
    private final com.codemuni.core.signer.AppearanceOptions options;
    private final SecureRandom secureRandom;

    public SignatureAppearanceBuilder(KeyStoreProvider keyStoreProvider, AppearanceOptions options) {
        this.keyStoreProvider = Objects.requireNonNull(keyStoreProvider, "KeyStoreProvider cannot be null");
        this.options = Objects.requireNonNull(options, "AppearanceOptions cannot be null");
        this.secureRandom = new SecureRandom();
    }

    /**
     * Configures the complete appearance of a PDF signature.
     *
     * @param signatureFieldName the base name for the signature field
     * @param appearance         the PDF signature appearance to configure
     * @param signatureCreator   the creator identifier for the signature
     * @throws CertificateExpiredException   if the certificate has expired
     * @throws KeyStoreException             if there's an error accessing the keystore
     * @throws NotADigitalSignatureException if the certificate is not valid for digital signatures
     */
    public void configureAppearance(
            String signatureFieldName,
            PdfSignatureAppearance appearance,
            String signatureCreator)
            throws CertificateExpiredException, KeyStoreException, NotADigitalSignatureException {

        Objects.requireNonNull(signatureFieldName, "Signature field name cannot be null");
        Objects.requireNonNull(appearance, "PdfSignatureAppearance cannot be null");

        appearance.setSignatureCreator(signatureCreator);

        setVisibleSignature(signatureFieldName, appearance);

        setRenderingMode(appearance);
        setCertificationAndInfo(appearance);
        setLayer2Text(appearance);

        if (options.getWatermarkImage() != null) {
            applyWatermark(appearance);
        }
    }

    /**
     * Configures the visible signature position and field name.
     * Supports both creating new signature fields and signing into existing unsigned fields.
     */
    private void setVisibleSignature(String signatureFieldName, PdfSignatureAppearance appearance) {
        // Check if we should use an existing signature field
        if (options.isUseExistingField() && options.getExistingFieldName() != null) {
            // Sign into existing unsigned signature field (/Sig AcroForm field)
            String existingFieldName = options.getExistingFieldName();
            log.info("Signing into existing signature field: " + existingFieldName);
            appearance.setVisibleSignature(existingFieldName);
            return;
        }

        // Create new signature field at specified coordinates
        int[] coord = options.getCoordinates();

        if (coord != null && coord.length == REQUIRED_COORDINATE_COUNT) {
            if (!areCoordinatesValid(coord)) {
                log.warn("Invalid signature coordinates provided. Signature may not display correctly.");
            }

            Rectangle rect = new Rectangle(coord[0], coord[1], coord[2], coord[3]);
            String fieldName = generateFieldName(signatureFieldName, options.getPageNumber());
            appearance.setVisibleSignature(rect, options.getPageNumber(), fieldName);
        } else {
            log.info("No valid coordinates provided. Signature will be invisible.");
        }
    }

    /**
     * Validates that coordinates form a proper rectangle.
     */
    private boolean areCoordinatesValid(int[] coord) {
        return coord[2] > coord[0] && coord[3] > coord[1];
    }

    /**
     * Sets the rendering mode for the signature appearance.
     */
    private void setRenderingMode(PdfSignatureAppearance appearance) throws CertificateExpiredException, NotADigitalSignatureException, KeyStoreException {
        if (options.isGraphicRendering()) {
            configureGraphicRendering(appearance);
        } else {
            configureTextRendering(appearance);
        }

        if (options.getCertificationLevel() != PdfSignatureAppearance.NOT_CERTIFIED) {
            appearance.setLayer4Text("Signed By: " + getCommonName(getCertificate())); // Set layer 4 text to the common name of the certificate if the signature is certified
        }
        appearance.setAcro6Layers(!options.isGreenTickEnabled());
    }

    /**
     * Configures graphic-based rendering mode.
     */
    private void configureGraphicRendering(PdfSignatureAppearance appearance) {

        appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.GRAPHIC_AND_DESCRIPTION);
        try {
            Image img = Image.getInstance(options.getGraphicImagePath());
            appearance.setSignatureGraphic(img);
        } catch (Exception e) {
            log.error("Failed to load signature graphic image from: " + options.getGraphicImagePath(), e);
            throw new RuntimeException("Failed to load signature graphic image.", e);
        }
    }

    /**
     * Configures text-based rendering mode.
     */
    private void configureTextRendering(PdfSignatureAppearance appearance) {
        PdfSignatureAppearance.RenderingMode mode = options.isGreenTickEnabled()
                ? PdfSignatureAppearance.RenderingMode.DESCRIPTION
                : PdfSignatureAppearance.RenderingMode.NAME_AND_DESCRIPTION;
        appearance.setRenderingMode(mode);
    }

    /**
     * Sets certification level and signature metadata.
     */
    private void setCertificationAndInfo(PdfSignatureAppearance appearance) {
        appearance.setCertificationLevel(options.getCertificationLevel());

        if (isNotEmpty(options.getReason())) {
            appearance.setReason(options.getReason().trim());
        }

        if (isNotEmpty(options.getLocation())) {
            appearance.setLocation(options.getLocation().trim());
        }
    }

    private X509Certificate getCertificate() throws KeyStoreException, CertificateExpiredException, NotADigitalSignatureException {
        java.security.cert.Certificate[] chain = keyStoreProvider.getCertificateChain();
        if (chain == null || chain.length == 0) {
            throw new KeyStoreException("No certificate chain available");
        }

        if (!(chain[0] instanceof X509Certificate)) {
            throw new KeyStoreException("Certificate is not an X509Certificate");
        }

        return (X509Certificate) chain[0];
    }

    /**
     * Builds and sets the text content for layer 2 of the signature.
     */
    private void setLayer2Text(PdfSignatureAppearance appearance)
            throws KeyStoreException, CertificateExpiredException, NotADigitalSignatureException {
        X509Certificate cert = getCertificate();
        String layerText = buildLayerText(cert);
        appearance.setLayer2Text(layerText);
    }

    /**
     * Builds the formatted text to display in the signature.
     */
    private String buildLayerText(X509Certificate cert) {
        StringBuilder sb = new StringBuilder();

        appendCertificateInfo(sb, cert);
        appendMetadata(sb);
        appendDateTime(sb);

        return sb.toString();
    }

    /**
     * Appends certificate subject information.
     */
    private void appendCertificateInfo(StringBuilder sb, X509Certificate cert) {
        if (options.isIncludeEntireSubject()) {
            sb.append(getFullSubjectDN(cert)).append("\n\n");
        } else {
            boolean isIncludeCommonName = (options.getCertificationLevel() == PdfSignatureAppearance.NOT_CERTIFIED && options.isGreenTickEnabled());
            if (isIncludeCommonName) sb.append("Signed by: ").append(getCommonName(cert)).append("\n");
            if (options.isIncludeCompany()) {
                sb.append("ORG: ").append(getOrganization(cert)).append("\n");
            }
        }
    }

    /**
     * Appends reason, location, and custom text metadata.
     */
    private void appendMetadata(StringBuilder sb) {
        if (isNotEmpty(options.getReason())) {
            sb.append("Reason: ").append(options.getReason().trim()).append("\n");
        }

        if (isNotEmpty(options.getLocation())) {
            sb.append("Location: ").append(options.getLocation().trim()).append("\n");
        }

        if (isNotEmpty(options.getCustomText())) {
            sb.append(options.getCustomText().trim()).append("\n");
        }
    }

    /**
     * Appends the current date and time.
     */
    private void appendDateTime(StringBuilder sb) {
        String dateTime = ZonedDateTime.now().format(SignatureDateFormats.getFormatter(options.getDateFormat()));
        sb.append("Date: ").append(dateTime);
    }

    /**
     * Applies a watermark image to the signature appearance.
     */
    private void applyWatermark(PdfSignatureAppearance appearance) {
        int[] coords = options.getCoordinates();

        if (coords == null || coords.length != REQUIRED_COORDINATE_COUNT) {
            log.warn("Cannot apply watermark: invalid coordinates");
            return;
        }

        float rectWidth = coords[2] - coords[0];
        float rectHeight = coords[3] - coords[1];

        Image watermark = options.getWatermarkImage();
        watermark.scaleToFit(rectWidth, rectHeight);

        positionWatermark(watermark, rectWidth, rectHeight);
        addWatermarkToLayer(appearance, watermark);
    }

    /**
     * Centers the watermark within the signature rectangle.
     */
    private void positionWatermark(Image watermark, float rectWidth, float rectHeight) {
        float imageWidth = watermark.getScaledWidth();
        float imageHeight = watermark.getScaledHeight();

        float xOffset = (rectWidth - imageWidth) / 2;
        float yOffset = (rectHeight - imageHeight) / 2;

        watermark.setAbsolutePosition(xOffset, yOffset);
    }

    /**
     * Adds the watermark to the signature's background layer with transparency.
     */
    private void addWatermarkToLayer(PdfSignatureAppearance appearance, Image watermark) {
        PdfTemplate background = appearance.getLayer(0); // Layer 0 = background
        PdfGState gState = new PdfGState();
        gState.setFillOpacity(DEFAULT_WATERMARK_OPACITY);

        background.saveState();
        background.setGState(gState);

        try {
            background.addImage(watermark);
        } catch (DocumentException e) {
            log.error("Failed to add watermark to signature appearance", e);
            throw new RuntimeException("Failed to add watermark to signature appearance.", e);
        } finally {
            background.restoreState();
        }
    }

    /**
     * Checks if a string is not null and not empty after trimming.
     */
    private boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * Generates a unique field name for the signature.
     * Uses SecureRandom for better randomness and uniqueness.
     */
    private String generateFieldName(String prefix, int pageNumber) {
        int randomSuffix = secureRandom.nextInt(FIELD_NAME_RANDOM_RANGE);
        return String.format("%s__P_%d_%06d", prefix, pageNumber, randomSuffix);
    }
}