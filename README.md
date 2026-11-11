# Keyword Extractor

Keyword Extractor is an open-source Java application designed to extract
relevant keywords from PDF documents written in French or English.

## License

Keyword Extractor is free software licensed under the:

**GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later)**

See the [LICENSE](LICENSE) file for the complete license text.

Third-party dependencies are distributed under their respective licenses.
See [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md) for details.

## Third-Party Software / Licensing

This project uses third-party software distributed under their respective
license terms.

The license of this project does not replace or modify the licenses of
third-party dependencies.

The main third-party dependencies include:

* Spring Boot — Apache License 2.0
* Project Lombok — MIT License
* iText — AGPL / Commercial
* Jackson — Apache License 2.0
* Apache Tika — Apache License 2.0
* Apache OpenNLP — Apache License 2.0

For more information about third-party software and their applicable
licenses, see:

* [`NOTICE.md`](NOTICE.md)
* [`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md)

### iText

This project uses the AGPL-licensed version of iText.

iText is also available under a commercial license. Users who require a
commercial license for iText should obtain the appropriate license directly
from iText.

The iText components remain subject to their own license terms.

# PDF Keyword Extractor

A Java/Spring Boot application for extracting relevant keywords from
**PDF documents written in French or English**.

The project combines deterministic NLP processing, content-aware
chunking, linguistic candidate extraction, local semantic embeddings,
hybrid relevance scoring, diversity-aware selection, and optional
LLM-based refinement.

The processing pipeline is designed to run locally, with **Ollama**
providing the embedding model and, in `quality` mode, the final LLM
refinement.

------------------------------------------------------------------------

## Project Goal

The main objective of the project is to automatically extract a concise
and relevant set of keywords from PDF documents in **French (FR)** and
**English (EN)**.

The pipeline progressively transforms the source document into a final
keyword list:

``` text
PDF document
    │
    ▼
PDF text extraction + language detection
    │
    ▼
Content-aware chunking
    │
    ▼
Candidate keyword extraction
    │
    ▼
Semantic embeddings
    │
    ▼
Hybrid semantic/lexical selection + MMR
    │
    ▼
Optional LLM refinement
    │
    ▼
Final keywords
```

The application is intended to remain **local**: Ollama runs the
required models locally rather than relying on a remote LLM or embedding
API.

------------------------------------------------------------------------

## Supported Languages

The current pipeline supports:

-   **French (`fr`)**
-   **English (`en`)**

Language detection is performed automatically during PDF parsing.

Language-specific resources are used during candidate extraction,
including:

``` text
/fr/stopwords.txt
/fr/generic-terms.txt

/en/stopwords.txt
/en/generic-terms.txt
```

------------------------------------------------------------------------

## Pipeline Documentation

The detailed technical documentation of each processing stage is
available under:

``` text
doc/pipeline/
```

Each document describes the responsibilities, algorithms, technical
choices, scoring mechanisms, and implementation details of the
corresponding pipeline step.

The pipeline currently consists of the following stages:

1.  **PDF Parsing**
    -   PDF text extraction
    -   Unicode normalization
    -   Encoding-artifact detection
    -   Language detection
2.  **Chunking**
    -   Content analysis
    -   Automatic chunking-strategy selection
    -   Section, paragraph, adaptive, and fixed-size strategies
3.  **Candidate Extraction**
    -   Unigram, bigram, and trigram extraction
    -   Stopword and generic-term filtering
    -   POS-based candidate validation
    -   Frequency and chunk-coverage scoring
    -   Lexical redundancy filtering
4.  **Embedding**
    -   Local semantic embeddings through Ollama
    -   `bge-m3`
    -   Batch processing
    -   Persistent disk cache
5.  **Keyword Selection**
    -   Document embedding
    -   Cosine similarity
    -   Semantic + lexical hybrid relevance
    -   Minimum relevance filtering
    -   Maximal Marginal Relevance (MMR)
6.  **LLM Refinement**
    -   Optional `quality` mode
    -   Local `qwen2.5:7b` model through Ollama
    -   Redundancy and specificity filtering
    -   Output validation and fallback

------------------------------------------------------------------------

# Ollama

Ollama is used locally for semantic embeddings and optional LLM
refinement.

The application currently uses:

  Purpose          Model
  ---------------- --------------
  Embeddings       `bge-m3`
  LLM refinement   `qwen2.5:7b`

Ollama must therefore be installed and running before executing the
application.

## Installing Ollama on Windows

Download and install Ollama from the official website:

https://ollama.com/download/windows

After installation, open PowerShell and verify that Ollama is available:

``` powershell
ollama --version
```

Pull the required models:

``` powershell
ollama pull bge-m3
ollama pull qwen2.5:7b
```

Verify the installed models:

``` powershell
ollama list
```

The Ollama server is normally available on:

``` text
http://127.0.0.1:11434
```

### Recommended Windows configuration

Before starting Ollama, configure the following environment variables:

``` powershell
$env:OLLAMA_HOST = "127.0.0.1:11434"
$env:OLLAMA_KEEP_ALIVE = "30m"
$env:OLLAMA_NUM_PARALLEL = "1"
$env:OLLAMA_MAX_LOADED_MODELS=1
```

These settings are used to:

-   bind Ollama explicitly to the local loopback interface;
-   keep loaded models available for 30 minutes;
-   limit Ollama to one parallel inference request;
-   limit the number of loaded models to one.

**Note**: this is a minimal setting - the parameters can be adjusted if necessary

Then start Ollama:

``` powershell
ollama serve
```

Keep this terminal running while the application is being used.

> The environment variables configured with `$env:` apply to the current
> PowerShell session. They should therefore be set before starting
> `ollama serve`.

------------------------------------------------------------------------

## Installing Ollama on Linux

Install Ollama using the official installation command:

``` bash
curl -fsSL https://ollama.com/install.sh | sh
```

Verify the installation:

``` bash
ollama --version
```

Pull the required models:

``` bash
ollama pull bge-m3
ollama pull qwen2.5:7b
```

Verify the installed models:

``` bash
ollama list
```

### Recommended Linux configuration

Configure the same runtime parameters:

``` bash
export OLLAMA_HOST="127.0.0.1:11434"
export OLLAMA_KEEP_ALIVE="30m"
export OLLAMA_NUM_PARALLEL="1"
export OLLAMA_MAX_LOADED_MODELS=1
```

Start Ollama:

``` bash
ollama serve
```

If Ollama is managed by `systemd`, the same variables can be configured
in the Ollama service environment before restarting the service.

------------------------------------------------------------------------

# Running the Application

The application is a Spring Boot application that can be packaged as a
standalone JAR and executed from the command line.

## Build

From the project root:

``` bash
mvn clean package
```

The resulting JAR is generated under:

``` text
target/
```

For example:

``` text
target/keyword-extractor-1.0.0.jar
```

## Command-line execution

The application can then be launched with a PDF file, passed with the
`--file` option:

``` bash
java -jar target/keyword-extractor-1.0.0.jar --file=sample.pdf
```

A Windows example:

``` powershell
java -jar target/keyword-extractor-1.0.0.jar --file="C:\documents\meteo.pdf"
```

Available options:

| Option | Description | Default |
|---|---|---|
| `--file=<path>` | PDF document to process | none |
| `--mode=<fast\|quality>` | `fast`: no LLM call; `quality`: final LLM refinement | `fast` |

The application processes the document through the complete pipeline and
outputs the extracted keywords.

------------------------------------------------------------------------

## Quality Mode

The LLM refinement stage is enabled when the application is run in
`quality` mode.

Example:

``` bash
java -jar target/keyword-extractor-1.0.0.jar --file="C:\documents\meteo.pdf" --mode=quality
```

In `quality` mode, the final keyword list is refined by the local
`qwen2.5:7b` model.

The LLM is deliberately used only at the final stage. Candidate
extraction, semantic ranking, and MMR selection are performed before the
LLM is called.

This keeps the LLM's role focused on qualitative refinement rather than
primary keyword generation.

------------------------------------------------------------------------

## Runtime Requirements

The project requires:

-   Java 21 or compatible configured project JDK
-   Maven 3.9.x
-   Ollama
-   `bge-m3`
-   `qwen2.5:7b` for `quality` mode
-   A PDF document in French or English

Ollama must be running locally before the application starts processing
documents.

------------------------------------------------------------------------

## Architecture Overview

The application is organized as an ordered processing pipeline.

Each stage receives a `ProcessingContext`, enriches it with new
information, and passes it to the next stage.

The main data flow is:

``` text
ProcessingContext
       │
       ▼
PdfParsingStep
       │ text + language
       ▼
ChunkingStep
       │ chunks
       ▼
CandidateExtractionStep
       │ candidates + lexical scores
       ▼
EmbeddingStep
       │ candidate embeddings
       ▼
KeywordSelectionStep
       │ selected keywords
       ▼
LlmRefinementStep
       │ final keywords
       ▼
ProcessingContext
```

Each stage is implemented as a Spring component and ordered using
Spring's `@Order` annotation.

------------------------------------------------------------------------

## Local-First Design

The project is designed around local processing.

The application communicates with Ollama through:

``` text
http://127.0.0.1:11434
```

No external embedding API or cloud-based LLM service is required.

This provides:

-   local document processing;
-   no requirement to upload PDF content to an external AI service;
-   reproducible local inference;
-   control over the models used by the pipeline.

------------------------------------------------------------------------

## Performance Considerations

The pipeline uses several mechanisms to limit unnecessary computation:

-   content-aware chunking;
-   candidate filtering before embedding;
-   batch embedding requests;
-   persistent embedding caching;
-   lexical filtering before semantic processing;
-   relevance filtering before MMR;
-   a maximum candidate pool;
-   a maximum final keyword count;
-   optional LLM refinement rather than mandatory LLM processing.

The embedding stage uses batches of 8 candidates per Ollama request.

The LLM refinement stage is only activated in `quality` mode.

