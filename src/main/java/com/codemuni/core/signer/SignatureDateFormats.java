package com.codemuni.core.signer;

import java.time.format.DateTimeFormatter;

public class SignatureDateFormats {

    // Enum of supported formatter types
    public enum FormatterType {
        COMPACT,
        ISO,
        LONG_READABLE,
        SHORT,
        DATE_ONLY,
        TIME_ONLY,
        LEGAL_STYLE
    }

    // Formatter instances
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss z");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter LONG_READABLE = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy HH:mm:ss z");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm:ss z");
    private static final DateTimeFormatter LEGAL_STYLE = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' HH:mm z");

    /**
     * Get formatter by known enum type.
     */
    public static DateTimeFormatter getFormatter(FormatterType type) {
        if (type == null) {
            return COMPACT; // default fallback
        }

        switch (type) {
            case ISO:
                return ISO;
            case LONG_READABLE:
                return LONG_READABLE;
            case SHORT:
                return SHORT;
            case DATE_ONLY:
                return DATE_ONLY;
            case TIME_ONLY:
                return TIME_ONLY;
            case LEGAL_STYLE:
                return LEGAL_STYLE;
            default:
                return COMPACT;
        }
    }
}
