package com.codemuni.utils;


import com.itextpdf.xmp.impl.Base64;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * Utility class for common string formatting operations.
 * Provides methods for formatting Base64 data, PEM certificates, and other string operations.
 */
public final class StringFormatUtils {

    // Prevent instantiation
    private StringFormatUtils() {
    }

    /**
     * Formats Base64-encoded byte array by inserting line breaks every 64 characters.
     * This is the standard PEM format for certificate encoding.
     *
     * @param data Base64-encoded byte array
     * @return Formatted Base64 string with line breaks
     */
    public static String formatBase64(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        String base64 = new String(data).replaceAll("(.{64})", "$1\n");
        return base64.trim();
    }

    /**
     * Generates a PEM-formatted certificate string from an X509Certificate.
     * The output format is:
     * -----BEGIN CERTIFICATE-----
     * [Base64-encoded certificate data]
     * -----END CERTIFICATE-----
     *
     * @param certificate The X509Certificate to convert to PEM format
     * @return PEM-formatted certificate string
     * @throws CertificateEncodingException if the certificate cannot be encoded
     */
    public static String toPemFormat(X509Certificate certificate) throws CertificateEncodingException {
        if (certificate == null) {
            throw new IllegalArgumentException("Certificate cannot be null");
        }

        return "-----BEGIN CERTIFICATE-----\n" +
                formatBase64(Base64.encode(certificate.getEncoded())) +
                "\n-----END CERTIFICATE-----";
    }

    /**
     * Truncates a string to a maximum length, appending "..." if truncated.
     *
     * @param text      The string to truncate
     * @param maxLength The maximum length (including the "..." if applicable)
     * @return Truncated string
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Converts a byte array to a hexadecimal string.
     *
     * @param bytes The byte array to convert
     * @return Hexadecimal string representation
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Formats a byte count into a human-readable string (e.g., "1.5 KB", "2.3 MB").
     *
     * @param bytes The number of bytes
     * @return Formatted size string
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }
}
