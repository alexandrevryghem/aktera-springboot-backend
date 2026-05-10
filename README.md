# PPTX Filler – Lightweight Java REST Service

A minimal Spring Boot service that fills placeholder variables inside a
PowerPoint (`.pptx`) template via HTTP.

---

## Quick start

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Endpoints

### 1 `POST /api/pptx/fill` — multipart upload

Upload the template as a file part; pass variables as a JSON string.
Returns the filled PPTX as a binary download.

```bash
curl -X POST http://localhost:8080/api/pptx/fill \
     -F "template=@my-template.pptx" \
     -F 'variables={"{{CLIENT_NAME}}":"Acme Corp","{{DATE}}":"2026-05-10"}' \
     --output filled.pptx
```

---

### 2 `POST /api/pptx/fill-base64` — JSON / base64

All data travels as JSON. Handy for service-to-service calls.

```bash
# Encode the template
B64=$(base64 -i my-template.pptx)

curl -X POST http://localhost:8080/api/pptx/fill-base64 \
     -H "Content-Type: application/json" \
     -d "{
           \"template\": \"$B64\",
           \"variables\": {
             \"{{CLIENT_NAME}}\": \"Acme Corp\",
             \"{{DATE}}\": \"2026-05-10\"
           }
         }" | jq -r .data | base64 -d > filled.pptx
```

Response envelope:

```json
{
    "filename": "filled.pptx",
    "data": "<base64-encoded PPTX>"
}
```

---

### 3 `POST /api/pptx/placeholders` — discover variables

Scan a template and get back a JSON array of every `{{...}}` placeholder found.

```bash
curl -X POST http://localhost:8080/api/pptx/placeholders \
     -F "template=@my-template.pptx"
# → ["{{CLIENT_NAME}}","{{DATE}}","{{TOTAL_AMOUNT}}"]
```

---

## How to create a template

Open PowerPoint and type your placeholders directly in any text box, table cell,
or speaker notes. Use any delimiter you like — the default pattern is `{{NAME}}`:

| Placeholder         | Example value    |
|---------------------|------------------|
| `{{CLIENT_NAME}}`   | Acme Corporation |
| `{{DATE}}`          | 2026-05-10       |
| `{{TOTAL_AMOUNT}}`  | €12,400          |
| `{{CONTACT_EMAIL}}` | hello@acme.com   |

> **Tip**: If PowerPoint auto-corrects or splits a word while you type a
> placeholder, finish typing first, then undo the autocorrect (Ctrl+Z once).
> The service handles split XML runs automatically using its merge-then-replace
> strategy.

---

## Running tests

```bash
mvn test
```

---

## Configuration (`application.properties`)

| Property                                 | Default | Description     |
|------------------------------------------|---------|-----------------|
| `server.port`                            | `8080`  | HTTP port       |
| `spring.servlet.multipart.max-file-size` | `50MB`  | Max upload size |

---

## Dependencies

| Library                        | Purpose         |
|--------------------------------|-----------------|
| Spring Boot 3.2 (Web)          | HTTP server     |
| Apache POI 5.2.5 (`poi-ooxml`) | PPTX read/write |

No database, no message broker, no extra infra required.
