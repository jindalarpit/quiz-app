package com.quizplatform.analytics.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ExportService filename formatting.
 *
 * <p><b>Validates: Requirements 4.4, 5.4</b>
 */
class ExportFilenamePropertyTest {

    private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter PDF_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Pattern CSV_FILENAME_PATTERN =
            Pattern.compile("^quiz-results-[A-Za-z0-9]{6}-\\d{8}\\.csv$");
    private static final Pattern PDF_FILENAME_PATTERN =
            Pattern.compile("^quiz-results-[A-Za-z0-9]{6}-\\d{4}-\\d{2}-\\d{2}\\.pdf$");

    private final ExportService exportService = createExportServiceForFilenameTests();

    /**
     * Property 11a: CSV filename matches the pattern quiz-results-{pin}-{YYYYMMDD}.csv
     *
     * <p><b>Validates: Requirements 4.4</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 11: Export filename format")
    void csvFilenameMatchesExpectedPattern(
            @ForAll("sixCharPin") String pin,
            @ForAll("validDate") LocalDate endDate) {

        String filename = exportService.formatCsvFilename(pin, endDate);

        // Verify overall pattern
        assertThat(filename).matches(CSV_FILENAME_PATTERN.pattern());

        // Verify exact expected filename
        String expectedFilename = "quiz-results-" + pin + "-" + endDate.format(CSV_DATE_FORMATTER) + ".csv";
        assertThat(filename).isEqualTo(expectedFilename);
    }

    /**
     * Property 11b: PDF filename matches the pattern quiz-results-{pin}-{YYYY-MM-DD}.pdf
     *
     * <p><b>Validates: Requirements 5.4</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 11: Export filename format")
    void pdfFilenameMatchesExpectedPattern(
            @ForAll("sixCharPin") String pin,
            @ForAll("validDate") LocalDate endDate) {

        String filename = exportService.formatPdfFilename(pin, endDate);

        // Verify overall pattern
        assertThat(filename).matches(PDF_FILENAME_PATTERN.pattern());

        // Verify exact expected filename
        String expectedFilename = "quiz-results-" + pin + "-" + endDate.format(PDF_DATE_FORMATTER) + ".pdf";
        assertThat(filename).isEqualTo(expectedFilename);
    }

    /**
     * Property 11c: CSV filename starts with correct prefix and ends with .csv extension
     *
     * <p><b>Validates: Requirements 4.4</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 11: Export filename format")
    void csvFilenameHasCorrectPrefixAndExtension(
            @ForAll("sixCharPin") String pin,
            @ForAll("validDate") LocalDate endDate) {

        String filename = exportService.formatCsvFilename(pin, endDate);

        assertThat(filename).startsWith("quiz-results-");
        assertThat(filename).endsWith(".csv");
        assertThat(filename).contains(pin);
    }

    /**
     * Property 11d: PDF filename starts with correct prefix and ends with .pdf extension
     *
     * <p><b>Validates: Requirements 5.4</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 11: Export filename format")
    void pdfFilenameHasCorrectPrefixAndExtension(
            @ForAll("sixCharPin") String pin,
            @ForAll("validDate") LocalDate endDate) {

        String filename = exportService.formatPdfFilename(pin, endDate);

        assertThat(filename).startsWith("quiz-results-");
        assertThat(filename).endsWith(".pdf");
        assertThat(filename).contains(pin);
    }

    /**
     * Property 11e: CSV and PDF filenames for the same session differ only in date format and extension
     *
     * <p><b>Validates: Requirements 4.4, 5.4</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 11: Export filename format")
    void csvAndPdfFilenamesDifferOnlyInDateFormatAndExtension(
            @ForAll("sixCharPin") String pin,
            @ForAll("validDate") LocalDate endDate) {

        String csvFilename = exportService.formatCsvFilename(pin, endDate);
        String pdfFilename = exportService.formatPdfFilename(pin, endDate);

        // Both should share the same prefix with pin
        String commonPrefix = "quiz-results-" + pin + "-";
        assertThat(csvFilename).startsWith(commonPrefix);
        assertThat(pdfFilename).startsWith(commonPrefix);

        // CSV uses YYYYMMDD (8 chars), PDF uses YYYY-MM-DD (10 chars)
        String csvDatePart = csvFilename.substring(commonPrefix.length(), csvFilename.length() - 4); // remove .csv
        String pdfDatePart = pdfFilename.substring(commonPrefix.length(), pdfFilename.length() - 4); // remove .pdf

        assertThat(csvDatePart).hasSize(8); // YYYYMMDD
        assertThat(pdfDatePart).hasSize(10); // YYYY-MM-DD

        // Both date parts should represent the same date
        LocalDate csvDate = LocalDate.parse(csvDatePart, CSV_DATE_FORMATTER);
        LocalDate pdfDate = LocalDate.parse(pdfDatePart, PDF_DATE_FORMATTER);
        assertThat(csvDate).isEqualTo(pdfDate);
        assertThat(csvDate).isEqualTo(endDate);
    }

    // --- Generators ---

    /**
     * Generates a random 6-character alphanumeric PIN.
     */
    @Provide
    Arbitrary<String> sixCharPin() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofLength(6);
    }

    /**
     * Generates a random valid LocalDate between 2020-01-01 and 2030-12-31.
     */
    @Provide
    Arbitrary<LocalDate> validDate() {
        return Arbitraries.longs()
                .between(
                        LocalDate.of(2020, 1, 1).toEpochDay(),
                        LocalDate.of(2030, 12, 31).toEpochDay()
                )
                .map(LocalDate::ofEpochDay);
    }

    // --- Helper ---

    /**
     * Creates an ExportService instance with null dependencies since we only test
     * the filename formatting methods which don't require any injected dependencies.
     */
    private static ExportService createExportServiceForFilenameTests() {
        return new ExportService(null, null, null, null);
    }
}
