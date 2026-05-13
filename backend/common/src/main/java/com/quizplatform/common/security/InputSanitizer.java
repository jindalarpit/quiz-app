package com.quizplatform.common.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Input sanitization utility that strips XSS patterns and SQL metacharacters.
 * Used as a Spring component filter across services to prevent injection attacks.
 */
@Component
public class InputSanitizer {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // Script tags
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
            // Event handlers
            Pattern.compile("on\\w+\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE),
            // JavaScript protocol
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            // Data URI with script
            Pattern.compile("data\\s*:[^;]*;base64", Pattern.CASE_INSENSITIVE),
            // iframe/object/embed tags
            Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
            // SQL injection patterns
            Pattern.compile("('\\s*(OR|AND)\\s*')", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(--|#|/\\*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(UNION\\s+SELECT)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(DROP\\s+TABLE)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(INSERT\\s+INTO)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(DELETE\\s+FROM)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(UPDATE\\s+\\w+\\s+SET)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(xp_|sp_)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> DETECTION_PATTERNS = List.of(
            Pattern.compile("<script", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on(load|error|click|mouseover|focus|blur)\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("('\\s*(OR|AND)\\s*'\\s*=\\s*')", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(UNION\\s+(ALL\\s+)?SELECT)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(DROP\\s+TABLE)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(;\\s*(DROP|DELETE|INSERT|UPDATE))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(1\\s*=\\s*1)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\bOR\\b\\s+\\d+\\s*=\\s*\\d+)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Sanitizes input by stripping XSS patterns and SQL metacharacters.
     *
     * @param input the raw input string
     * @return sanitized string with dangerous patterns removed, or null if input is null
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input;

        // Remove dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }

        // Encode remaining HTML special characters
        sanitized = sanitized
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");

        return sanitized.trim();
    }

    /**
     * Checks if the input contains dangerous patterns (XSS, SQL injection).
     *
     * @param input the raw input string to check
     * @return true if input contains dangerous patterns, false otherwise
     */
    public boolean containsDangerousPatterns(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        for (Pattern pattern : DETECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }

        return false;
    }
}
