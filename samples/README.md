# Sample documents

Small files for trying the knowledge upload API by hand. They are also checked by
`SampleDocumentsTest`, so they stay valid for the pipeline.

| File | Type | Language | Expected `language` |
|---|---|---|---|
| `documents/leave-policy-en.txt` | TXT | English | `en` |
| `documents/leave-policy-hi.txt` | TXT | Hindi (Devanagari) | `hi` |
| `documents/leave-policy-or.txt` | TXT | Odia | `or` |
| `documents/expense-policy-en.md` | Markdown | English | `en` |
| `documents/it-security-en.html` | HTML | English | `en` |

The three leave policies carry the same facts (24 days annual leave, carry-over of 10 days, 26 weeks maternity leave),
so you can compare how each language is handled.

## Try them

Start Ollama and PostgreSQL, then the app with the `knowledge` profile (see the main README). Then, from the project root:

```bash
curl -F "file=@samples/documents/leave-policy-hi.txt" -F "access_group=hr" http://127.0.0.1:8080/api/documents
curl "http://127.0.0.1:8080/api/documents/<id-from-the-first-response>?access_group=hr"
```

Expected: the first call returns `202` with `"status":"PENDING"`; the second shows `"status":"INDEXED"`, `"language":"hi"`
and a `chunkCount` of at least 1 within a few seconds.

Other checks:

| Do this | Expect |
|---|---|
| Upload the same file again | `200` with `"duplicate": true` |
| Edit a line, upload under the same name | `"version": 2` |
| Query a document id with `access_group=finance` | `404` |
| Rename a PDF or other binary file to `x.txt` and upload | `415` (content does not match the extension) |
| Upload a file with an unsupported extension, such as `.exe` | `415` |
| Stop Ollama, then upload | `202`, then status `FAILED` with `EMBEDDING_UNAVAILABLE` |

To see the stored chunks:

```bash
podman compose exec postgres psql -U assistant -d assistant -c "select d.name, d.language, d.status, count(c.*) from documents d left join document_chunks c on c.document_id = d.id group by d.id"
```
