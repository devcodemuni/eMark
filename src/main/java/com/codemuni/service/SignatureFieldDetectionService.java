package com.codemuni.service;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for detecting and managing unsigned signature fields (/Sig AcroForm fields) in PDF documents.
 * Supports PDF viewers and PAdES-compliant applications.
 */
public class SignatureFieldDetectionService {

    private static final Log log = LogFactory.getLog(SignatureFieldDetectionService.class);

    /**
     * Represents an unsigned signature field in a PDF document.
     */
    public static class SignatureFieldInfo {
        private final String fieldName;
        private final int pageNumber; // 1-based page number
        private final float llx; // Lower-left X coordinate
        private final float lly; // Lower-left Y coordinate
        private final float urx; // Upper-right X coordinate
        private final float ury; // Upper-right Y coordinate
        private final boolean isSigned;

        public SignatureFieldInfo(String fieldName, int pageNumber, float llx, float lly, float urx, float ury, boolean isSigned) {
            this.fieldName = fieldName;
            this.pageNumber = pageNumber;
            this.llx = llx;
            this.lly = lly;
            this.urx = urx;
            this.ury = ury;
            this.isSigned = isSigned;
        }

        public String getFieldName() {
            return fieldName;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public float getLlx() {
            return llx;
        }

        public float getLly() {
            return lly;
        }

        public float getUrx() {
            return urx;
        }

        public float getUry() {
            return ury;
        }

        public boolean isSigned() {
            return isSigned;
        }

        public float getWidth() {
            return urx - llx;
        }

        public float getHeight() {
            return ury - lly;
        }

        /**
         * Returns the rectangle bounds in PDF coordinates.
         */
        public Rectangle2D.Float getBounds() {
            return new Rectangle2D.Float(llx, lly, getWidth(), getHeight());
        }

        @Override
        public String toString() {
            return "SignatureFieldInfo{" +
                    "fieldName='" + fieldName + '\'' +
                    ", pageNumber=" + pageNumber +
                    ", position=[" + llx + ", " + lly + ", " + urx + ", " + ury + "]" +
                    ", isSigned=" + isSigned +
                    '}';
        }
    }

    /**
     * Detects all unsigned signature fields in the PDF.
     *
     * @param reader PdfReader instance for the PDF document
     * @return List of unsigned signature field information
     */
    public List<SignatureFieldInfo> detectUnsignedSignatureFields(PdfReader reader) {
        List<SignatureFieldInfo> unsignedFields = new ArrayList<>();

        if (reader == null) {
            log.warn("PdfReader is null. Cannot detect signature fields.");
            return unsignedFields;
        }

        try {
            AcroFields acroFields = reader.getAcroFields();
            if (acroFields == null) {
                log.info("No AcroForm fields found in the PDF.");
                return unsignedFields;
            }

            List<String> signatureFieldNames = acroFields.getSignatureNames();
            List<String> blankSignatureFieldNames = acroFields.getBlankSignatureNames();

            log.info("Total signature fields: " + signatureFieldNames.size());
            log.info("Blank (unsigned) signature fields: " + blankSignatureFieldNames.size());

            // Process only blank (unsigned) signature fields
            for (String fieldName : blankSignatureFieldNames) {
                SignatureFieldInfo fieldInfo = extractFieldInfo(acroFields, fieldName, reader);
                if (fieldInfo != null) {
                    unsignedFields.add(fieldInfo);
                    log.info("Detected unsigned signature field: " + fieldInfo);
                }
            }

        } catch (Exception e) {
            log.error("Error detecting signature fields in PDF", e);
        }

        return unsignedFields;
    }

    /**
     * Detects all signature fields (both signed and unsigned) in the PDF.
     *
     * @param reader PdfReader instance for the PDF document
     * @return List of all signature field information
     */
    public List<SignatureFieldInfo> detectAllSignatureFields(PdfReader reader) {
        List<SignatureFieldInfo> allFields = new ArrayList<>();

        if (reader == null) {
            log.warn("PdfReader is null. Cannot detect signature fields.");
            return allFields;
        }

        try {
            AcroFields acroFields = reader.getAcroFields();
            if (acroFields == null) {
                log.info("No AcroForm fields found in the PDF.");
                return allFields;
            }

            List<String> signatureFieldNames = acroFields.getSignatureNames();
            List<String> blankSignatureFieldNames = acroFields.getBlankSignatureNames();

            // Process all signature fields
            for (String fieldName : signatureFieldNames) {
                boolean isSigned = !blankSignatureFieldNames.contains(fieldName);
                SignatureFieldInfo fieldInfo = extractFieldInfo(acroFields, fieldName, reader, isSigned);
                if (fieldInfo != null) {
                    allFields.add(fieldInfo);
                }
            }

            log.info("Total signature fields detected: " + allFields.size() +
                    " (Signed: " + (signatureFieldNames.size() - blankSignatureFieldNames.size()) +
                    ", Unsigned: " + blankSignatureFieldNames.size() + ")");

        } catch (Exception e) {
            log.error("Error detecting signature fields in PDF", e);
        }

        return allFields;
    }

    /**
     * Extracts detailed information about a specific signature field.
     */
    private SignatureFieldInfo extractFieldInfo(AcroFields acroFields, String fieldName, PdfReader reader) {
        return extractFieldInfo(acroFields, fieldName, reader, false);
    }

    /**
     * Extracts detailed information about a specific signature field.
     */
    private SignatureFieldInfo extractFieldInfo(AcroFields acroFields, String fieldName, PdfReader reader, boolean isSigned) {
        try {
            // Get field positions (can have multiple positions if field appears on multiple pages)
            List<AcroFields.FieldPosition> positions = acroFields.getFieldPositions(fieldName);

            if (positions == null || positions.isEmpty()) {
                log.warn("No position information found for field: " + fieldName);
                return null;
            }

            // Use the first position (typically signature fields appear on one page)
            AcroFields.FieldPosition position = positions.get(0);

            int pageNumber = position.page; // 1-based page number
            com.itextpdf.text.Rectangle rect = position.position;

            if (rect == null) {
                log.warn("No rectangle found for field: " + fieldName);
                return null;
            }

            // Validate signature field type
            if (!isValidSignatureField(acroFields, fieldName)) {
                log.warn("Field is not a valid signature field: " + fieldName);
                return null;
            }

            return new SignatureFieldInfo(
                    fieldName,
                    pageNumber,
                    rect.getLeft(),
                    rect.getBottom(),
                    rect.getRight(),
                    rect.getTop(),
                    isSigned
            );

        } catch (Exception e) {
            log.error("Error extracting field info for: " + fieldName, e);
            return null;
        }
    }

    /**
     * Validates that a field is a proper signature field (/Sig type).
     */
    private boolean isValidSignatureField(AcroFields acroFields, String fieldName) {
        try {
            // Get the field's PDF dictionary
            AcroFields.Item item = acroFields.getFieldItem(fieldName);
            if (item == null) {
                return false;
            }

            // Check if field type is /Sig
            PdfDictionary widget = item.getWidget(0);
            if (widget != null) {
                PdfDictionary fieldDict = widget.getAsDict(PdfName.PARENT);
                if (fieldDict == null) {
                    fieldDict = widget;
                }

                PdfName fieldType = fieldDict.getAsName(PdfName.FT);
                if (fieldType != null && fieldType.equals(PdfName.SIG)) {
                    return true;
                }
            }

            // Alternative check: signature names list
            List<String> signatureNames = acroFields.getSignatureNames();
            return signatureNames.contains(fieldName);

        } catch (Exception e) {
            log.warn("Error validating signature field: " + fieldName, e);
            return false;
        }
    }

    /**
     * Checks if a PDF has any unsigned signature fields.
     *
     * @param reader PdfReader instance for the PDF document
     * @return true if the PDF contains at least one unsigned signature field
     */
    public boolean hasUnsignedSignatureFields(PdfReader reader) {
        if (reader == null) {
            return false;
        }

        try {
            AcroFields acroFields = reader.getAcroFields();
            if (acroFields == null) {
                return false;
            }

            List<String> blankSignatureFieldNames = acroFields.getBlankSignatureNames();
            return blankSignatureFieldNames != null && !blankSignatureFieldNames.isEmpty();

        } catch (Exception e) {
            log.error("Error checking for unsigned signature fields", e);
            return false;
        }
    }

    /**
     * Gets the field name at a specific location on a page (for click detection).
     *
     * @param unsignedFields List of unsigned signature fields
     * @param pageNumber     1-based page number
     * @param pdfX           X coordinate in PDF space
     * @param pdfY           Y coordinate in PDF space
     * @return Field name if found at the location, null otherwise
     */
    public SignatureFieldInfo getFieldAtLocation(List<SignatureFieldInfo> unsignedFields, int pageNumber, float pdfX, float pdfY) {
        for (SignatureFieldInfo field : unsignedFields) {
            if (field.getPageNumber() == pageNumber) {
                if (pdfX >= field.getLlx() && pdfX <= field.getUrx() &&
                        pdfY >= field.getLly() && pdfY <= field.getUry()) {
                    return field;
                }
            }
        }
        return null;
    }
}
