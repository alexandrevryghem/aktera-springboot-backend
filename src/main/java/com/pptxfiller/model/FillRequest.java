package com.pptxfiller.model;

import java.util.Map;

/**
 * Request body for the /fill-base64 endpoint.
 *
 * <pre>
 * {
 *   "template": "<base64-encoded .pptx>",
 *   "variables": {
 *     "{{CLIENT_NAME}}": "Acme Corp",
 *     "{{DATE}}": "2026-05-10"
 *   }
 * }
 * </pre>
 */
public class FillRequest {

    /**
     * Base64-encoded PPTX template bytes.
     */
    private String template;

    /**
     * Key-value map where each key is the placeholder string that appears
     * verbatim inside the template (e.g. "{{CLIENT_NAME}}") and the value
     * is the text to substitute in.
     */
    private Map<String, String> variables;

    // ── getters / setters ──────────────────────────────────────────────────

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }
}
