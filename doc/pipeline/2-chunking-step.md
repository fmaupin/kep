The `ChunkingStep` is responsible for analyzing the extracted document text and selecting the most appropriate chunking strategy before splitting the content into processing units.

It is executed as pipeline step 2, immediately after PDF parsing.

**Main responsibilities**

* Analyze the overall structure and characteristics of the extracted text.
* Select a chunking strategy dynamically according to the document's structure.
* Delegate the actual splitting operation to a dedicated ChunkStrategy.
* Store the resulting chunks in the shared ProcessingContext.
Measure execution time through the pipeline benchmark.

**Dynamic chunking strategy selection**

The step uses `TextAnalysisService` to inspect the document when the content is sufficiently large. The analysis provides structural and textual characteristics such as:

* heading ratio;
* list-item ratio;
* whether the document has a structured format;
* word density;
* whitespace ratio;
* whether the text is considered dense.

The strategy is selected according to the following decision tree:

| Condition                                          | Selected strategy | Rationale                                                               |
| -------------------------------------------------- | ----------------- | ----------------------------------------------------------------------- |
| Content is `null` or shorter than `minContentSize` | `FIXED_SIZE`      | Small documents do not justify detailed structural analysis             |
| Heading ratio > 5%                                 | `SECTION`         | The document appears to contain explicit sections/headings              |
| Structured format detected                         | `PARAGRAPH`       | Lists and other structural elements indicate paragraph-oriented content |
| Dense text detected                                | `ADAPTIVE`        | Dense content benefits from adaptive chunk boundaries                   |
| None of the above                                  | `FIXED_SIZE`      | Safe default when no specific structure is identified                   |

The thresholds are intentionally configurable through Spring properties:

```
chunk.analysis.min-content-size=1000
chunk.analysis.paragraph-detection-ratio=0.1
```

The default minimum content size is 1000 characters.

[Note] paragraphDetectionRatio is currently injected but is not used in the strategy-selection logic. The paragraph decision currently relies directly on TextAnalysisResult.isHasStructuredFormat().

**Strategy abstraction**

The step does not contain the actual chunking algorithms. Instead, it receives all available `ChunkStrategy` implementations through Spring dependency injection:

```
public ChunkingStep(
    List<ChunkStrategy> strategies,
    TextAnalysisService textAnalysisService)
```

The strategies are indexed by their ChunkStrategyTypeEnum:

```
ChunkStrategyTypeEnum → ChunkStrategy
```

This provides a clean Strategy Pattern implementation.

Adding a new chunking algorithm therefore does not require modifying the decision logic itself. A new ChunkStrategy implementation can be registered with Spring and exposed through its corresponding strategy type.

**Chunk creation**

Once a strategy has been selected, createChunks() resolves the corresponding implementation from the strategy map and delegates the splitting operation:

```
Document text
     │
     ▼
Text analysis
     │
     ▼
Strategy selection
     │
     ├── FIXED_SIZE
     ├── SECTION
     ├── PARAGRAPH
     └── ADAPTIVE
     │
     ▼
ChunkStrategy.split()
     │
     ▼
List<String> chunks
```

The resulting chunks are stored in:

```
ctx.setChunks(...)
```

The rest of the pipeline therefore remains independent of the chunking algorithm that was selected.

**Fallback and error handling**

For small documents, the step deliberately avoids structural analysis and falls back to `FIXED_SIZE`.

If the selected strategy is not present in the injected strategy map, the step throws an `IllegalArgumentException` containing the list of available strategies. This prevents silent failures or accidental processing with an incorrect chunking implementation.

**Language independence**

The step itself is language-agnostic.

It does not contain language-specific rules, stopwords, linguistic patterns, or POS information. Its decisions are based exclusively on structural and statistical properties supplied by `TextAnalysisService`.

Language-specific processing, if required by a particular chunking strategy or by later NLP stages, is therefore isolated from this pipeline step.

**Pipeline context integration**

`ChunkingStep` communicates exclusively through ProcessingContext:

* Input: extracted text `from ctx.getText()`
* Output: generated chunks through `ctx.setChunks(...)`
* Benchmark: execution timing is started and stopped within the step

This keeps the step compatible with the project's generic `PipelineStep<ProcessingContext, ProcessingContext>` architecture.

**Technical characteristics**

* Spring @Component
* Pipeline execution order: 2
* Uses the Strategy Pattern for chunking
* Runtime discovery of available ChunkStrategy implementations through Spring DI
* Configurable content-analysis thresholds
* Structural analysis delegated to TextAnalysisService
* No dependency on a specific document language
* Graceful fallback to fixed-size chunking
* Explicit failure when a configured strategy is unavailable
* Logs both the selected strategy and the resulting number of chunks
* Integrated with the application's benchmarking mechanism
