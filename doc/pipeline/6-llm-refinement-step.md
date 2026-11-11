The `LlmRefinementStep` performs a final semantic and qualitative refinement of the keywords selected by the previous pipeline stage.

It uses a local Large Language Model (LLM) through Ollama to remove redundant, generic, or insufficiently informative keywords while preserving the exact wording of the original candidates.

This step is only executed in quality mode. In other modes, the processing context is returned unchanged.

**Responsibilities**

The step performs the following operations:

* Check whether LLM refinement is enabled.
* Limit the input to the top 10 keywords produced by the previous selection step.
* Build a constrained prompt for the LLM.
* Send the request to a local Ollama instance.
* Parse the LLM response as a JSON array.
* Validate the returned keywords against the original candidate vocabulary.
* Replace the selected keywords with the validated LLM output.
* Fall back to the original keywords if the LLM produces no valid result.

**Execution mode**

The refinement step is explicitly restricted to quality mode:

```
if (!"quality".equalsIgnoreCase(ctx.getMode())) {
    return ctx;
}
```

This is important because LLM inference is significantly more expensive than the deterministic processing performed by the preceding stages.

Consequently:

* quality mode → LLM refinement is performed.
* other modes → no LLM call is made and the existing keyword list is preserved.

**Input limitation**

Only the first 10 keywords produced by KeywordSelectionStep are passed to the LLM:

```
List<String> top = ctx.getKeywords()
        .stream()
        .limit(10)
        .toList();
```

The LLM is therefore not responsible for selecting keywords from the complete candidate pool. Semantic ranking and diversity selection have already been performed by the previous step.

The LLM acts as a final qualitative filter rather than as the primary keyword extraction mechanism.

**LLM model and communication**

The step communicates with a locally running Ollama server using the /api/generate endpoint:

```
http://localhost:11434/api/generate
```

The configured model is:

```
qwen2.5:7b
```

The request is performed using Java's built-in HttpClient.

The HTTP request has a timeout of 120 seconds, reflecting the potentially high latency of local LLM inference.

Streaming is explicitly disabled:

```
"stream": false
```

This allows the complete generated response to be processed as a single JSON response.

**Prompt constraints**

The prompt strongly constrains the LLM's behavior.

The model is instructed to:

* remove redundant or synonymous keywords;
* remove generic or insufficiently informative terms;
* prefer specific and meaningful keywords;
* never invent new keywords;
* never rephrase keywords;
* never translate keywords;
* never correct spelling;
* preserve the original capitalization and wording;
* return exactly 10 keywords when at least 10 valid candidates are available;
* return fewer only when fewer than 10 meaningful candidates exist;
* return only a JSON array.

The most important constraint is that the LLM must select keywords from the supplied list rather than generate new terminology.

This makes the LLM a controlled reranking/filtering component, rather than an open-ended text generation component.

**Structured output**

The expected response format is:

```
["keyword1", "keyword2", "keyword3"]
```

The response is first extracted using extractJsonArray().

This method searches for the first [ and the last ]:

```
int start = input.indexOf('[');
int end = input.lastIndexOf(']');
```

This provides some tolerance to an LLM response that accidentally contains additional text around the JSON array.

If no valid array boundaries can be found, the step raises a KeywordException.

The extracted content is then deserialized using Jackson into:

```
List<String>
```

**Output validation**

Because an LLM cannot be assumed to follow the prompt perfectly, the generated keywords are validated before being accepted.

The validation builds a vocabulary containing all individual words appearing in the original top-10 candidates:

```
Set<String> originalWords = top.stream()
        .flatMap(k -> Arrays.stream(k.split("\\s+")))
        .map(String::toLowerCase)
        .collect(Collectors.toSet());
```

Each returned keyword is then checked using:

```
isComposedOfOriginalWords(...)
```

A generated keyword is accepted only if every word composing it already exists in the original candidate vocabulary.

For example, if the original candidates contain:

```
machine learning
machine learning algorithm
deep learning
```

the LLM cannot introduce:

```
artificial intelligence
```

because artificial and intelligence do not exist in the original vocabulary.

This provides an additional safeguard against hallucinated terminology.

**Fallback behavior**

If the LLM returns keywords but none pass validation, the step falls back to the original top-10 list:

```
ctx.setKeywords(validated.isEmpty() ? top : validated);
```

Therefore, an unsuccessful refinement does not normally result in an empty keyword list.

The overall behavior is:

```
Selected keywords
       │
       ▼
    Top 10
       │
       ▼
   LLM/Qwen 2.5
       │
       ▼
 JSON array
       │
       ▼
Vocabulary validation
       │
       ├── valid results ──► refined keywords
       │
       └── no valid result ─► original top 10
```

**Error handling**

Several failure conditions are explicitly handled:

* non-HTTP-200 Ollama responses;
* missing response field;
* missing JSON array in the generated response;
* JSON parsing errors;
* HTTP communication errors;
* interrupted requests.

HTTP and parsing failures are wrapped in KeywordException:

```
throw new KeywordException("LLM refinement failed", e);
```

If the thread is interrupted, the interruption flag is restored:

```
Thread.currentThread().interrupt();
```

This is important for correct Java concurrency behavior.

**Benchmarking**

Like the other pipeline stages, the step integrates with the application's benchmarking mechanism:

```
ctx.getBenchmark().start();
...
ctx.getBenchmark().end("Llm refinement");
```

This makes the potentially expensive LLM inference time measurable independently from the other processing stages.

**Technical characteristics**

| Property                 | Implementation                 |
| ------------------------ | ------------------------------ |
| Pipeline order           | `6`                            |
| Execution mode           | `quality` only                 |
| LLM runtime              | Ollama                         |
| LLM model                | `qwen2.5:7b`                   |
| API                      | `/api/generate`                |
| Streaming                | Disabled                       |
| Input size               | Maximum 10 keywords            |
| Expected output          | JSON array of strings          |
| Output validation        | Original-word vocabulary check |
| Hallucination protection | Yes                            |
| Fallback                 | Original top-10 keywords       |
| HTTP timeout             | 120 seconds                    |
| Benchmarking             | Yes                            |
| External/cloud LLM       | No                             |

**Role in the overall pipeline**

`LlmRefinementStep` is the final quality-control stage of the keyword extraction pipeline.

The preceding stages are responsible for progressively narrowing the search space:

```
PDF
 │
 ▼
Text extraction & language detection
 │
 ▼
Content-aware chunking
 │
 ▼
Candidate extraction
 │
 ▼
Embedding generation
 │
 ▼
Semantic + lexical selection + MMR
 │
 ▼
Top keywords
 │
 ▼
LLM qualitative refinement
 │
 ▼
Final keywords
```

The architectural separation is intentional: deterministic NLP and embedding-based processing perform the main extraction and ranking, while the LLM is used only at the end to apply higher-level semantic judgment such as redundancy, specificity, and informativeness.

This limits the LLM's influence and reduces the risk of hallucination, while still allowing it to perform a qualitative distinction that is difficult to express entirely through deterministic scoring rules.

