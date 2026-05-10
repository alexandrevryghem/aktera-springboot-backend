package com.pptxfiller.controller;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxfiller.model.FillRequest;
import com.pptxfiller.service.PptxFillerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Two lightweight endpoints for filling variables in a PPTX template.
 *
 * <h2>Endpoint 1 – multipart/form-data  (POST /api/pptx/fill)</h2>
 * Upload the template as a file part and pass the variable map as a JSON
 * string in the {@code variables} part.  The filled PPTX is returned as a
 * binary download.
 *
 * <pre>
 * curl -X POST http://localhost:8080/api/pptx/fill \
 *      -F "template=@my-template.pptx" \
 *      -F 'variables={"{{CLIENT}}":"Acme","{{DATE}}":"2026-05-10"}' \
 *      --output filled.pptx
 * </pre>
 *
 * <h2>Endpoint 2 – application/json  (POST /api/pptx/fill-base64)</h2>
 * Send everything as JSON.  The template must be base64-encoded.  Useful when
 * the caller cannot send multipart (e.g. from another service).
 *
 * <pre>
 * POST /api/pptx/fill-base64
 * {
 *   "template": "<base64 bytes>",
 *   "variables": {
 *     "{{CLIENT}}": "Acme Corp",
 *     "{{DATE}}":   "2026-05-10"
 *   }
 * }
 * </pre>
 * Returns a JSON object: {@code {"filename":"filled.pptx","data":"<base64>"}}
 *
 * <h2>Endpoint 3 – GET /api/pptx/placeholders</h2>
 * Upload a template (multipart) and receive a JSON array of every unique
 * placeholder found that matches the pattern {@code {{...}}}.
 * Handy for UI "variable discovery" features.
 */
@RestController
@RequestMapping("/api/pptx")
public class PptxController {

    private final PptxFillerService fillerService;
    private final ObjectMapper objectMapper;

    public PptxController(PptxFillerService fillerService, ObjectMapper objectMapper) {
        this.fillerService = fillerService;
        this.objectMapper = objectMapper;
    }

    // ── Endpoint 1: multipart upload ───────────────────────────────────────

    /**
     * Accepts a .pptx template as a multipart file plus a JSON variables map,
     * returns the filled presentation as a binary download.
     */
    @PostMapping(
        value = "/fill",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )
    public ResponseEntity<byte[]> fillMultipart(
        @RequestPart("template") MultipartFile templateFile,
        @RequestPart("variables") String variablesJson
    ) throws IOException {

        Map<String, String> variables = objectMapper.readValue(
            variablesJson, new TypeReference<>() {
            });

        byte[] filled = fillerService.fill(templateFile.getBytes(), variables);

        String filename = deriveFilename(templateFile.getOriginalFilename());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
            .contentLength(filled.length)
            .body(filled);
    }

    // ── Endpoint 2: JSON / base64 ─────────────────────────────────────────

    /**
     * Accepts a base64-encoded template and a variables map as JSON.
     * Returns the filled PPTX base64-encoded in a JSON envelope.
     */
    @PostMapping(
        value = "/fill-base64",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, String>> fillBase64(
        @RequestBody FillRequest request
    ) throws IOException {

        byte[] templateBytes = Base64.getDecoder().decode(request.getTemplate());
        byte[] filled = fillerService.fill(templateBytes, request.getVariables());
        String base64Result = Base64.getEncoder().encodeToString(filled);

        return ResponseEntity.ok(Map.of(
            "filename", "filled.pptx",
            "data", base64Result
        ));
    }

    // ── Endpoint 3: placeholder discovery ────────────────────────────────

    /**
     * Scans the uploaded template and returns every unique placeholder that
     * matches the {@code {{...}}} convention.
     *
     * Example response:
     * <pre>["{{CLIENT_NAME}}","{{DATE}}","{{TOTAL_AMOUNT}}"]</pre>
     */
    @PostMapping(
        value = "/placeholders",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<java.util.List<String>> listPlaceholders(
        @RequestPart("template") MultipartFile templateFile
    ) throws IOException {

        java.util.List<String> found =
            fillerService.extractPlaceholders(templateFile.getBytes());
        return ResponseEntity.ok(found);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private String deriveFilename(String original) {
        if (original == null || original.isBlank()) {
            return "filled.pptx";
        }
        String base = original.replaceAll("(?i)\\.pptx$", "");
        return base + "-filled.pptx";
    }
}
