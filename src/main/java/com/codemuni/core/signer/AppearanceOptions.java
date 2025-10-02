package com.codemuni.core.signer;

import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfSignatureAppearance;

import java.util.Arrays;
import java.util.Objects;

/**
 * Encapsulates options for customizing the appearance of a digital signature in a PDF.
 */
public class AppearanceOptions {

//    private PdfSignatureAppearance.RenderingMode renderingMode = PdfSignatureAppearance.RenderingMode.NAME_AND_DESCRIPTION;

    private boolean graphicRendering = false;
    private String graphicImagePath;

    private boolean includeEntireSubject = false;
    private boolean includeCompany = false;

    private String reason = "";
    private String location = "";
    private String customText = "";

    private SignatureDateFormats.FormatterType dateFormat = SignatureDateFormats.FormatterType.COMPACT;

    private int certificationLevel = PdfSignatureAppearance.NOT_CERTIFIED;
    private boolean ltvEnabled = false;
    private boolean timestampEnabled = false;
    private boolean greenTickEnabled = false;

    private SignatureDateFormats.FormatterType formatterType;

    public void setFormatterType(SignatureDateFormats.FormatterType formatterType) {
        this.formatterType = formatterType;
    }

    private int pageNumber = 1;
    private int[] coordinates = {0, 0, 0, 0};

    private Image watermarkImage;

    // Constructors
    public AppearanceOptions() {
    }

    public boolean isGraphicRendering() {
        return graphicRendering;
    }

    public void setGraphicRendering(boolean graphicRendering) {
        this.graphicRendering = graphicRendering;
    }

    public String getGraphicImagePath() {
        return graphicImagePath;
    }

    public void setGraphicImagePath(String graphicImagePath) {
        this.graphicImagePath = graphicImagePath;
    }

    public boolean isIncludeEntireSubject() {
        return includeEntireSubject;
    }

    public void setIncludeEntireSubject(boolean includeEntireSubject) {
        this.includeEntireSubject = includeEntireSubject;
    }

    public boolean isIncludeCompany() {
        return includeCompany;
    }

    public void setIncludeCompany(boolean includeCompany) {
        this.includeCompany = includeCompany;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason != null ? reason : "";
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location != null ? location : "";
    }

    public String getCustomText() {
        return customText;
    }

    public void setCustomText(String customText) {
        this.customText = customText != null ? customText : "";
    }

    public SignatureDateFormats.FormatterType getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(SignatureDateFormats.FormatterType dateFormat) {
        this.dateFormat = dateFormat;
    }

    public int getCertificationLevel() {
        return certificationLevel;
    }

    public void setCertificationLevel(int certificationLevel) {
        this.certificationLevel = certificationLevel;
    }

    public boolean isLtvEnabled() {
        return ltvEnabled;
    }

    public void setLtvEnabled(boolean ltvEnabled) {
        this.ltvEnabled = ltvEnabled;
    }

    public boolean isTimestampEnabled() {
        return timestampEnabled;
    }

    public void setTimestampEnabled(boolean timestampEnabled) {
        this.timestampEnabled = timestampEnabled;
    }

    public boolean isGreenTickEnabled() {
        return greenTickEnabled;
    }

    public void setGreenTickEnabled(boolean greenTickEnabled) {
        this.greenTickEnabled = greenTickEnabled;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = Math.max(1, pageNumber); // Ensure page number is always >= 1
    }

    public int[] getCoordinates() {
        return Arrays.copyOf(coordinates, coordinates.length);
    }

    public void setCoordinates(int[] coordinates) {
        if (coordinates != null && coordinates.length == 4) {
            this.coordinates = Arrays.copyOf(coordinates, coordinates.length);
        }
    }

    public void setCoordinates(float pdfX, float pdfY, float pdfWidth, float pdfHeight) {
        this.coordinates = new int[]{
                (int) pdfX,
                (int) pdfY,
                (int) pdfWidth,
                (int) pdfHeight
        };
    }

    public Image getWatermarkImage() {
        return watermarkImage;
    }

    public void setWatermarkImage(Image watermarkImage) {
        this.watermarkImage = watermarkImage;
    }

    @Override
    public String toString() {
        return "AppearanceOptions{" +
                ", graphicRendering=" + graphicRendering +
                ", graphicImagePath='" + graphicImagePath + '\'' +
                ", includeEntireSubject=" + includeEntireSubject +
                ", includeCompany=" + includeCompany +
                ", reason='" + reason + '\'' +
                ", location='" + location + '\'' +
                ", customText='" + customText + '\'' +
                ", dateFormat=" + dateFormat +
                ", certificationLevel=" + certificationLevel +
                ", ltvEnabled=" + ltvEnabled +
                ", timestampEnabled=" + timestampEnabled +
                ", greenTickEnabled=" + greenTickEnabled +
                ", pageNumber=" + pageNumber +
                ", coordinates=" + Arrays.toString(coordinates) +
                '}';
    }
}
