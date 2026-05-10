package com.pptxfiller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import com.pptxfiller.service.PptxFillerService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;

class PptxFillerServiceTest {

    private final PptxFillerService service = new PptxFillerService();

    /**
     * Creates a minimal in-memory PPTX with one slide containing the given text.
     */
    private byte[] createTemplate(String slideText) throws Exception {
        try (XMLSlideShow pptx = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlide slide = pptx.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setText(slideText);
            pptx.write(out);
            return out.toByteArray();
        }
    }

    private String readFirstSlideText(byte[] pptxBytes) throws Exception {
        try (XMLSlideShow pptx = new XMLSlideShow(new ByteArrayInputStream(pptxBytes))) {
            XSLFSlide slide = pptx.getSlides().getFirst();
            StringBuilder sb = new StringBuilder();
            for (XSLFShape s : slide.getShapes()) {
                if (s instanceof XSLFTextShape ts) {
                    sb.append(ts.getText());
                }
            }
            return sb.toString();
        }
    }

    @Test
    void singleVariable_isReplaced() throws Exception {
        byte[] template = createTemplate("Hello, {{NAME}}!");
        byte[] filled = service.fill(template, Map.of("{{NAME}}", "World"));
        assertTrue(readFirstSlideText(filled).contains("Hello, World!"));
    }

    @Test
    void multipleVariables_allReplaced() throws Exception {
        byte[] template = createTemplate("Client: {{CLIENT}} — Date: {{DATE}}");
        byte[] filled = service.fill(template, Map.of(
            "{{CLIENT}}", "Acme Corp",
            "{{DATE}}", "2026-05-10"
        ));
        String text = readFirstSlideText(filled);
        assertTrue(text.contains("Acme Corp"));
        assertTrue(text.contains("2026-05-10"));
    }

    @Test
    void noVariables_returnsSameContent() throws Exception {
        byte[] template = createTemplate("No placeholders here.");
        byte[] filled = service.fill(template, Map.of());
        assertArrayEquals(template, filled);
    }

    @Test
    void extractPlaceholders_findsAll() throws Exception {
        byte[] template = createTemplate("{{FIRST}} and {{SECOND}} and {{FIRST}} again");
        List<String> placeholders = service.extractPlaceholders(template);
        assertEquals(2, placeholders.size());
        assertTrue(placeholders.contains("{{FIRST}}"));
        assertTrue(placeholders.contains("{{SECOND}}"));
    }

    @Test
    void unknownPlaceholder_leftAsIs() throws Exception {
        byte[] template = createTemplate("Hello {{UNKNOWN}}");
        byte[] filled = service.fill(template, Map.of("{{NAME}}", "World"));
        assertTrue(readFirstSlideText(filled).contains("{{UNKNOWN}}"));
    }
}
