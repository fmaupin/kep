`PdfParsingStep` is the first step of the keyword extraction pipeline (`@Order(1)`).

Its responsibility is to transform the raw PDF input stream into a normalized textual representation and determine the document language.

The resulting text and language are stored in the shared `ProcessingContext` for all subsequent pipeline steps.

**Main responsibilities**
* Read the complete PDF from the input InputStream.
* Validate that the input is not empty.
* Parse the PDF using iText 7.
* Extract text from every PDF page.
* Normalize the extracted Unicode text.
* Detect common PDF text-encoding corruption patterns.
* Detect the document language using Apache Tika / Optimaize.
* Store the extracted text and detected language in the ProcessingContext.
* Measure the execution time through the pipeline benchmark mechanism.
* Convert low-level I/O and PDF parsing failures into the application's KeywordException.

**PDF input handling**

The step reads the entire input stream into a byte array before parsing:

```
InputStream → byte[] → ByteArrayInputStream → PdfReader → PdfDocument
```

This approach decouples the PDF parser from the original input stream and allows the resulting byte array to be passed safely to iText.

An empty input is explicitly rejected rather than allowing the PDF parser to fail later with a less meaningful error.

**PDF text extraction**

Text extraction is performed page by page using iText's PdfTextExtractor:

```
PDF
 ├── Page 1 → text
 ├── Page 2 → text
 ├── ...
 └── Page N → text
```

The extracted text from each page is concatenated into a single document-level string. A newline is inserted between pages to preserve a basic page boundary in the resulting text.

The step therefore does not attempt to preserve the original PDF layout, typography, tables, images, or visual structure. Its purpose is specifically to produce a plain-text representation suitable for downstream NLP processing.

**Unicode normalization**

PDF text extraction can produce inconsistent Unicode representations, especially for accented characters and other composed/decomposed characters. The extracted text is therefore normalized using Unicode NFC (Normalization Form C).

This is particularly important for multilingual keyword extraction because visually identical strings can otherwise have different underlying Unicode representations.

The step also removes:

* Unicode BOM characters (U+FEFF)
* non-breaking spaces (U+00A0)
* excessive consecutive spaces/tabs
* excessive blank lines

The final text is trimmed before being stored in the processing context.

**Encoding-corruption detection**

The step does not attempt to repair corrupted PDF text automatically. Instead, it performs diagnostic detection.

It checks for:

* Unicode replacement characters `(U+FFFD)`
* common mojibake/encoding artifacts such as `Ã, Â, ├, ┐, etc`.

When such patterns are detected, a warning is logged.

This is deliberately a detection mechanism rather than an automatic repair mechanism: attempting to guess the original encoding at this stage could potentially damage valid text. The warning provides visibility into problematic PDF extraction while allowing the pipeline to continue.

**Automatic language detection**

Once the text has been extracted and normalized, the document language is detected using:

* Apache Tika Language Detection
* Optimaize language models

The detector operates on the complete extracted document text, rather than individual sentences or chunks.

The detected language is stored in ProcessingContext and is subsequently used by language-dependent pipeline components, notably the POS tagging and candidate extraction stages.

If the detector does not consider its result sufficiently reliable, the implementation falls back to:

```
en
```

This provides a deterministic language value for downstream processing instead of propagating an undefined language.

**Error handling**

The step converts low-level failures into domain-specific KeywordException instances.

Two main failure cases are handled explicitly:

* Failure while reading the input stream
* Failure while parsing the PDF

This keeps PDF/I/O implementation details isolated from the rest of the pipeline.

**Resource management**

Both the PdfReader and PdfDocument are managed using Java try-with-resources, ensuring that PDF-related resources are properly released even when extraction fails.

**Pipeline integration**

The step implements:

```
PipelineStep<ProcessingContext, ProcessingContext>
```

and therefore follows the same context-in/context-out model as the other pipeline stages.

Its output can be summarized as:

```
Raw PDF
   ↓
PDF byte stream
   ↓
iText PDF parsing
   ↓
Page-by-page text extraction
   ↓
Unicode / whitespace normalization
   ↓
Encoding anomaly detection
   ↓
Language detection
   ↓
ProcessingContext
   ├── text
   └── language
```

**Technical characteristics**

| Aspect                   | Implementation                             |
| ------------------------ | ------------------------------------------ |
| Pipeline order           | `1`                                        |
| PDF library              | iText 7                                    |
| Text extraction          | `PdfTextExtractor`                         |
| Unicode normalization    | NFC                                        |
| Language detection       | Apache Tika + Optimaize                    |
| Language detection scope | Complete document                          |
| Encoding handling        | Detection and warning, no automatic repair |
| Empty input              | Explicitly rejected                        |
| Error abstraction        | `KeywordException`                         |
| Resource management      | Try-with-resources                         |
| Benchmarking             | `ProcessingContext.getBenchmark()`         |
| Output                   | Normalized text + detected language        |

**Design considerations**

A key characteristic of this step is that it deliberately keeps document ingestion concerns separate from NLP processing. It does not perform tokenization, sentence segmentation, POS tagging, chunking, or keyword extraction. Its only responsibility is to produce a clean and language-aware textual input for the subsequent stages.

The step is therefore effectively the document normalization boundary of the pipeline: everything after this stage operates on normalized text rather than directly on PDF data.
