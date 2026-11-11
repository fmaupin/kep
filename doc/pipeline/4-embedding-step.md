The `EmbeddingStep` is responsible for converting the candidate keywords produced by the previous pipeline stage into dense numerical vector representations, called embeddings. These vectors are later used by the `KeywordSelectionStep` to measure semantic similarity and perform diversity-aware keyword selection.

**Main responsibilities**

* Generate embeddings for candidate keywords
* Reuse previously computed embeddings through a persistent disk cache
* Batch requests to the local `Ollama` embedding service
* Restore embeddings to the exact order of the original candidate list
* Store the resulting vectors in the ProcessingContext
* Measure the execution time through the pipeline benchmark

**Embedding model**

The step uses the local Ollama REST API:

```
http://localhost:11434/api/embed
```

with the `bge-m3` embedding model.

The request contains a list of candidate terms:

```
{
  "model": "bge-m3",
  "input": ["santé publique", "changement global", "..."]
}
```

Using `Ollama` locally avoids sending document content or candidate keywords to an external API and makes the embedding stage suitable for a fully local NLP pipeline.

**What is an embedding ?**

An **embedding** is a numerical representation of a piece of text in a multidimensional vector space.

For example, a keyword might be represented conceptually as:

```
"santé publique"
        ↓
[0.12, -0.43, 0.87, ..., 0.21]
```

The vector contains many numerical dimensions. The individual dimensions generally do not have a directly interpretable meaning; the important property is the geometric relationship between vectors.

Texts with related meanings tend to have vectors that are close to each other in this vector space.

For example, an embedding model may place:

```
santé publique
médecine préventive
système de santé
```

closer together than:

```
santé publique
compagnies aériennes
```

This allows the pipeline to reason about semantic similarity, rather than relying only on exact word matching.

**Why embeddings are used in this pipeline**

Candidate extraction is primarily lexical and syntactic: it looks at word frequency, chunk coverage, n-grams, stopwords and POS patterns.

Embeddings provide a complementary semantic signal.

This creates a two-stage approach:

```
Candidate Extraction
        │
        │ lexical + POS analysis
        ▼
   Candidate keywords
        │
        │ semantic representation
        ▼
     Embeddings
        │
        │ semantic similarity
        ▼
 Keyword Selection / MMR
```

This distinction is important because two keywords can be lexically different while being semantically related.

**Disk caching**

One of the most important technical characteristics of this step is its use of a persistent DiskCache.

Before calling `Ollama`, every candidate is checked against the cache:

```
String key = DiskCache.hash(chunk);


if (cache.exists(key)) {
    embeddingsMap.put(chunk, cache.load(key));
} else {
    toEmbed.add(chunk);
}
```

The cache key is generated from the candidate text using `DiskCache.hash()`.

Consequently, if the same candidate has already been embedded during an earlier document-processing run, the application can reuse the existing vector without invoking Ollama again.

For example:

```
Document 1
  "santé publique" → Ollama → embedding → disk cache

Document 2
  "santé publique" → disk cache → no Ollama request
```

This is particularly useful because embedding generation is an external inference operation and therefore significantly more expensive than a local hash lookup.

The cache is also beneficial for a **file-by-file processing architecture**, where there is no global corpus model but the same terminology may occur across multiple documents.

**Batch processing**

Candidates that are not found in the cache are processed in batches of 8:

```
private static final int BATCH_SIZE = 8;
```

Instead of making one HTTP request per candidate:

`50 candidates
→ 50 HTTP requests`

the step groups them into batches:

`50 candidates
→ 7 HTTP requests
   8 + 8 + 8 + 8 + 8 + 8 + 2`

This substantially reduces HTTP overhead and is particularly important when Ollama is running locally.

The batching also allows the embedding model to process several texts within a single inference request.

**Preserving candidate order**

The cache and batching logic deliberately use a `Map<String, float[]>` internally:

```
Map<String, float[]> embeddingsMap
```

but the final vectors are reconstructed using the original candidate list:

```
List<float[]> ordered = chunks.stream()
        .map(embeddingsMap::get)
        .toList();
```

This is an important implementation detail.

The resulting list therefore has exactly the same ordering as:

```
ctx.getCandidates()
```

For example:

```
Candidates:
[ A, B, C, D ]

Embeddings:
[ vector(A), vector(B), vector(C), vector(D) ]
```

regardless of whether some vectors came from the cache and others were generated through different batches.

This positional correspondence is required by subsequent pipeline stages.

**HTTP communication**

The implementation uses Java's built-in `java.net.http.HttpClient`, rather than introducing another HTTP client dependency.

The client has a short connection timeout:

```
.connectTimeout(Duration.ofSeconds(2))
```

while individual embedding requests have a longer timeout:

```
.timeout(Duration.ofSeconds(60))
```

This distinction is appropriate for local inference:

* 2 seconds to establish the connection
* up to 60 seconds for Ollama to perform the embedding inference

The request is sent as a JSON `POST`.

**Response conversion**

`Ollama` returns embeddings as numerical arrays. The implementation converts the returned Double values to Java primitive `float[]` arrays:

```
float[] vector = new float[raw.size()];

for (int i = 0; i < raw.size(); i++) {
    vector[i] = raw.get(i).floatValue();
}
```

Using `float[]` reduces memory consumption compared with double[], which is appropriate because embedding vectors are normally used for similarity calculations rather than exact numerical computation.

**Mathematical concept: vector similarity**

The embeddings themselves become useful when comparing vectors.

A common similarity measure for embeddings is cosine similarity.

For two vectors:

```
A = (a1,a2,...,an)
```
and
```
B = (b1​,b2,...,bn​)
```

cosine similarity is defined as:

$$
\operatorname{cosine}(A,B) = \frac{A \cdot B}{\|A\| \, \|B\|}
$$

where:

$$
A \cdot B = \sum_{i=1}^{n} a_i b_i
$$

The dot product measures how strongly the two vectors point in the same direction.

$$
\|A\| = \sqrt{\sum_{i=1}^{n} a_i^2}
$$

The norm represents the mathematical length of the vector.

**Interpretation**

For normalized vectors, cosine similarity is generally interpreted as:

```
≈ 1   → very similar direction / strong semantic similarity
≈ 0   → little relationship
≈ -1  → opposite direction
```

For the keyword extraction pipeline, this allows the next stage to answer questions such as:

"How semantically related is this candidate keyword to the overall document ?"

**Role in the overall pipeline**

The complete flow at this point is:

```
PDF
 │
 ▼
PdfParsingStep
 │
 │ extracted text + detected language
 ▼
ChunkingStep
 │
 │ semantically/structurally appropriate chunks
 ▼
CandidateExtractionStep
 │
 │ lexical candidates
 │ + frequency
 │ + chunk coverage
 │ + POS quality
 ▼
EmbeddingStep
 │
 │ dense semantic vectors
 ▼
KeywordSelectionStep
 │
 │ semantic similarity
 │ + lexical score
 │ + diversity
 │
 ▼
Final keywords
```

The `EmbeddingStep` therefore acts as the bridge between the linguistic candidate extraction stage and the semantic keyword selection stage.

**Error handling and resilience**

The step distinguishes between cache and embedding operations:

* A cache lookup failure results in a KeywordException.
* A cache save failure is logged as a warning and does not abort the embedding process.
* An interrupted HTTP operation restores the thread's interrupted status with:

```
Thread.currentThread().interrupt();
```

* Unexpected Ollama responses cause the embedding operation to fail explicitly if the expected embeddings field is missing.

This means the cache is treated as an optimization, while the actual embedding computation remains the authoritative source of the vectors.

**Performance characteristics**

The implementation contains several deliberate optimizations:

| Optimization               | Purpose                                           |
| -------------------------- | ------------------------------------------------- |
| Persistent disk cache      | Avoid recomputing identical embeddings            |
| Batch size of 8            | Reduce HTTP/inference overhead                    |
| Local Ollama               | Avoid network latency and external API dependency |
| `float[]` vectors          | Reduce memory footprint                           |
| Original-order restoration | Maintain deterministic pipeline semantics         |
| Benchmark instrumentation  | Measure embedding-stage execution time            |

The log:

```
EmbeddingStep: 50 cached / 0 to compute
```

is particularly useful for diagnosing performance. In this example, all 50 candidates were already available in the cache, so no `Ollama` inference was required.

**Summary**

`EmbeddingStep` converts the lexical candidate keywords into dense semantic vectors using the local bge-m3 model through `Ollama`. 

It is optimized for repeated document processing through persistent disk caching and minimizes inference overhead by batching uncached candidates in groups of eight. 

The step guarantees that the resulting embedding list preserves the exact order of the original candidates, allowing subsequent keyword-selection algorithms to associate each vector with its corresponding candidate deterministically.

From an architectural perspective, this step introduces the semantic representation layer of the pipeline: candidate extraction determines which expressions are linguistically plausible, while embeddings provide the numerical representation required to determine how semantically relevant and diverse those expressions are.
