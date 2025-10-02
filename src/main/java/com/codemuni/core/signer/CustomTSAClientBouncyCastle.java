package com.codemuni.core.signer;

import com.codemuni.core.exception.TSAConfigurationException;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;

public class CustomTSAClientBouncyCastle extends TSAClientBouncyCastle {

    private final String url;

    public CustomTSAClientBouncyCastle(String url) {
        super(validateUrl(url));
        this.url = url;
    }

    public CustomTSAClientBouncyCastle(String url, String username, String password) {
        super(validateUrl(url), username, password);
        this.url = url;
    }

    public CustomTSAClientBouncyCastle(String url, String username, String password, int tokSzEstimate, String digestAlgorithm) {
        super(validateUrl(url), username, password, tokSzEstimate, digestAlgorithm);
        this.url = url;
    }

    /**
     * Validates that the given URL is not null or empty.
     * Throws IllegalArgumentException if the URL is invalid.
     */
    private static String validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new TSAConfigurationException("TSA URL must not be null or empty.");
        }
        // Optional: Add basic format check
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new TSAConfigurationException("TSA URL must start with http:// or https://");
        }
        return url.trim();
    }

    public String getUrl() {
        return url;
    }
}
