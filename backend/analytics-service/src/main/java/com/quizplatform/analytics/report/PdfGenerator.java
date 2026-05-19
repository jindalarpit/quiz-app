package com.quizplatform.analytics.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.quizplatform.analytics.dto.LeaderboardEntryDTO;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class PdfGenerator {

    private static final Font TITLE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font METADATA_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font TABLE_HEADER_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font TABLE_BODY_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font TABLE_BODY_BOLD_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Generates a PDF report containing session metadata and a leaderboard table.
     *
     * @param quizTitle the title of the quiz
     * @param sessionDate the date the session ended
     * @param participantCount the total number of participants
     * @param durationSeconds the session duration in seconds
     * @param entries the leaderboard entries sorted by rank ascending
     * @return the PDF document as a byte array
     */
    public byte[] generate(
            String quizTitle,
            LocalDate sessionDate,
            int participantCount,
            long durationSeconds,
            List<LeaderboardEntryDTO> entries) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);

        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addTitle(document);
            addMetadataSection(document, quizTitle, sessionDate, participantCount, durationSeconds);
            addLeaderboardTable(document, entries);

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }

        return outputStream.toByteArray();
    }

    private void addTitle(Document document) throws DocumentException {
        Paragraph title = new Paragraph("Quiz Results Report", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20f);
        document.add(title);
    }

    private void addMetadataSection(
            Document document,
            String quizTitle,
            LocalDate sessionDate,
            int participantCount,
            long durationSeconds)
            throws DocumentException {

        document.add(
                new Paragraph("Quiz Title: " + quizTitle, METADATA_FONT));
        document.add(
                new Paragraph(
                        "Session Date: " + sessionDate.format(DATE_FORMATTER), METADATA_FONT));
        document.add(
                new Paragraph(
                        "Participants: " + participantCount, METADATA_FONT));
        document.add(
                new Paragraph(
                        "Duration: " + formatDuration(durationSeconds), METADATA_FONT));

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(15f);
        document.add(spacer);
    }

    private void addLeaderboardTable(Document document, List<LeaderboardEntryDTO> entries)
            throws DocumentException {

        float[] columnWidths = {1f, 3f, 1.5f, 2f, 2f, 2f, 2.5f};
        PdfPTable table = new PdfPTable(columnWidths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);

        addTableHeader(table);
        addTableRows(table, entries);

        document.add(table);
    }

    private void addTableHeader(PdfPTable table) {
        String[] headers = {
            "Rank", "Nickname", "Score", "Correct", "Total", "Max Streak", "Avg Time (s)"
        };

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER_FONT));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5f);
            table.addCell(cell);
        }
    }

    private void addTableRows(PdfPTable table, List<LeaderboardEntryDTO> entries) {
        for (LeaderboardEntryDTO entry : entries) {
            boolean isTopThree = entry.getRank() <= 3;
            Font font = isTopThree ? TABLE_BODY_BOLD_FONT : TABLE_BODY_FONT;

            addCell(table, String.valueOf(entry.getRank()), font);
            addCell(table, entry.getNickname(), font);
            addCell(table, String.valueOf(entry.getScore()), font);
            addCell(table, String.valueOf(entry.getCorrectAnswers()), font);
            addCell(table, String.valueOf(entry.getTotalAnswers()), font);
            addCell(table, String.valueOf(entry.getMaxStreak()), font);
            addCell(
                    table,
                    String.format("%.1f", entry.getAvgResponseTimeSec()),
                    font);
        }
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private String formatDuration(long durationSeconds) {
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
