package com.codemuni.model;

/**
 * Certification levels for PDF signatures (PDF viewer compatible).
 * Based on DocMDP (Document Modification Detection and Prevention) P values:
 * - P=1 (NO_CHANGES_ALLOWED): No changes allowed - document is locked
 * - P=2 (FORM_FILLING_AND_ANNOTATION_CERTIFIED): Form filling and annotations allowed
 * - P=3 (FORM_FILLING_CERTIFIED): Form filling allowed
 * - NOT_CERTIFIED: Not a certification signature (approval signature)
 */
public enum CertificationLevel {
    NOT_CERTIFIED("Approval Signature", "NOT_CERTIFIED", 0),
    NO_CHANGES_ALLOWED("Certified - No Changes Allowed", "NO_CHANGES_ALLOWED", 1),
    FORM_FILLING_CERTIFIED("Certified - Form Filling Allowed", "FORM_FILLING_CERTIFIED", 3),
    FORM_FILLING_AND_ANNOTATION_CERTIFIED("Certified - Form Filling & Annotations Allowed", "FORM_FILLING_AND_ANNOTATION_CERTIFIED", 2);

    private final String label;
    private final String id;
    private final int pValue; // DocMDP P value (1, 2, 3, or 0 for not certified)

    CertificationLevel(String label, String id, int pValue) {
        this.label = label;
        this.id = id;
        this.pValue = pValue;
    }

    public static CertificationLevel fromLabel(String label) {
        for (CertificationLevel level : values()) {
            if (level.label.equals(label)) return level;
        }
        return null;
    }

    /**
     * Gets certification level from DocMDP P value.
     * @param pValue DocMDP P value (1, 2, 3, or 0)
     * @return Corresponding certification level
     */
    public static CertificationLevel fromPValue(int pValue) {
        for (CertificationLevel level : values()) {
            if (level.pValue == pValue) return level;
        }
        return NOT_CERTIFIED;
    }

    public String getLabel() {
        return label;
    }

    public String getId() {
        return id;
    }

    public int getPValue() {
        return pValue;
    }

    /**
     * Checks if this certification level allows additional signatures.
     * @return true if additional signatures are allowed, false otherwise
     */
    public boolean allowsSignatures() {
        return this == NOT_CERTIFIED;
    }

    /**
     * Checks if this is a certification signature.
     * @return true if certified, false if approval signature
     */
    public boolean isCertified() {
        return this != NOT_CERTIFIED;
    }
}
