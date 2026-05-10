package com.pptxfiller.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Service;

/**
 * Fills placeholder variables inside a .pptx template using Apache POI.
 *
 * <h2>How placeholders work</h2>
 * Write anything you like inside your slides, e.g. {@code {{CLIENT_NAME}}} or
 * {@code %DATE%}.  Pass the same strings as keys in the {@code variables} map
 * and this service will replace every occurrence, including ones that PowerPoint
 * has split across multiple XML runs (a very common gotcha).
 *
 * <h2>What is replaced</h2>
 * <ul>
 *   <li>Regular text frames on slides</li>
 *   <li>Table cells</li>
 *   <li>Group shapes (recursively)</li>
 *   <li>Notes pages</li>
 *   <li>Slide layout / master placeholders are intentionally skipped to
 *       preserve the template structure.</li>
 * </ul>
 */
@Service
public class PptxFillerService {

    /**
     * Matches {{ANYTHING}} placeholders.
     */
    private static final Pattern PLACEHOLDER_PATTERN =
        Pattern.compile("\\{\\{[^{}]+}}");

    // ── Placeholder discovery ──────────────────────────────────────────────

    /**
     * Performs variable substitution on a PPTX template.
     *
     * @param templateBytes raw bytes of the source .pptx file
     * @param variables     map of {@code placeholder → replacement} strings
     * @return raw bytes of the filled .pptx file
     * @throws IOException on any I/O or parsing error
     */
    public byte[] fill(byte[] templateBytes, Map<String, String> variables) throws IOException {
        if (variables == null || variables.isEmpty()) {
            return templateBytes; // nothing to do
        }

        try (XMLSlideShow pptx = new XMLSlideShow(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (XSLFSlide slide : pptx.getSlides()) {
                processShapes(slide.getShapes(), variables);
                // Also fill speaker notes if present
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    processShapes(notes.getShapes(), variables);
                }
            }

            pptx.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Scans a PPTX template and returns a sorted, deduplicated list of every
     * placeholder string matching {@code {{...}}} found in slide text.
     */
    public List<String> extractPlaceholders(byte[] templateBytes) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (XMLSlideShow pptx = new XMLSlideShow(new ByteArrayInputStream(templateBytes))) {
            for (XSLFSlide slide : pptx.getSlides()) {
                collectFromShapes(slide.getShapes(), found);
            }
        }
        return found.stream().sorted().collect(java.util.stream.Collectors.toList());
    }

    private void collectFromShapes(List<XSLFShape> shapes, Set<String> out) {
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTextShape ts) {
                for (XSLFTextParagraph p : ts.getTextParagraphs()) {
                    String text = p.getTextRuns().stream()
                        .map(r -> r.getRawText() == null ? "" : r.getRawText())
                        .collect(java.util.stream.Collectors.joining());
                    Matcher m = PLACEHOLDER_PATTERN.matcher(text);
                    while (m.find()) {
                        out.add(m.group());
                    }
                }
            } else if (shape instanceof XSLFTable table) {
                for (int r = 0; r < table.getNumberOfRows(); r++) {
                    for (int c = 0; c < table.getNumberOfColumns(); c++) {
                        XSLFTableCell cell = table.getCell(r, c);
                        if (cell != null) {
                            collectFromShapes(List.of(cell), out);
                        }
                    }
                }
            } else if (shape instanceof XSLFGroupShape g) {
                collectFromShapes(g.getShapes(), out);
            }
        }
    }

    // ── Shape traversal ────────────────────────────────────────────────────

    private void processShapes(List<XSLFShape> shapes, Map<String, String> variables) {
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTextShape textShape) {
                processTextShape(textShape, variables);

            } else if (shape instanceof XSLFTable table) {
                processTable(table, variables);

            } else if (shape instanceof XSLFGroupShape group) {
                processShapes(group.getShapes(), variables); // recurse
            }
        }
    }

    // ── Text shapes ────────────────────────────────────────────────────────

    /**
     * Replaces variables in a text shape.
     *
     * <p>PowerPoint often splits a single logical word into multiple XML runs
     * when the user types, accepts autocorrect, or changes formatting mid-word.
     * To handle this we first merge adjacent runs inside each paragraph into a
     * single run, perform the substitution, and then update only the text –
     * preserving the formatting of the <em>first</em> run of that paragraph.
     *
     * <p>This "merge-then-replace" strategy handles all common split-run
     * scenarios without any regex lookaheads.
     */
    private void processTextShape(XSLFTextShape shape, Map<String, String> variables) {
        for (XSLFTextParagraph paragraph : shape.getTextParagraphs()) {
            mergeAndReplace(paragraph, variables);
        }
    }

    /**
     * Merges the text of all runs in a paragraph, applies substitutions, then
     * writes the result back into the first run (keeping its formatting) and
     * clears the remaining runs.
     */
    private void mergeAndReplace(XSLFTextParagraph paragraph, Map<String, String> variables) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            return;
        }

        // 1. Collect full paragraph text
        StringBuilder combined = new StringBuilder();
        for (XSLFTextRun run : runs) {
            String t = run.getRawText();
            combined.append(t == null ? "" : t);
        }
        String originalText = combined.toString();

        // 2. Apply all substitutions
        String replaced = applyVariables(originalText, variables);

        // 3. Only rewrite if something actually changed
        if (replaced.equals(originalText)) {
            return;
        }

        // 4. Put the result in the first run, blank out the others
        runs.get(0).setText(replaced);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("");
        }
    }

    // ── Tables ─────────────────────────────────────────────────────────────

    private void processTable(XSLFTable table, Map<String, String> variables) {
        for (int r = 0; r < table.getNumberOfRows(); r++) {
            for (int c = 0; c < table.getNumberOfColumns(); c++) {
                XSLFTableCell cell = table.getCell(r, c);
                if (cell != null) {
                    processTextShape(cell, variables);
                }
            }
        }
    }

    // ── String substitution ────────────────────────────────────────────────

    /**
     * Applies every variable substitution to the given text.
     * Order: longest key first to avoid partial replacements
     * (e.g. {@code {{NAME}}} before {@code {{NAME_FULL}}}).
     */
    private String applyVariables(String text, Map<String, String> variables) {
        // Sort by key length descending for safe substitution order
        return variables.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
            .reduce(text,
                    (t, entry) -> t.replace(entry.getKey(), entry.getValue()),
                    (a, b) -> b); // combiner not used in sequential stream
    }
}
