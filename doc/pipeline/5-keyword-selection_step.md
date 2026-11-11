The `KeywordSelectionStep` is responsible for selecting the final set of keywords from the candidates produced by `CandidateExtractionStep`.

Unlike the previous extraction stage, which primarily evaluates lexical frequency, POS patterns and chunk coverage, this step combines three complementary signals:

* Semantic relevance — how close a candidate is to the overall document in embedding space.
* Lexical relevance — how strong the candidate was according to the previous extraction/ranking stage.
* Semantic diversity — how different a candidate is from keywords that have already been selected.

The final selection therefore uses a hybrid relevance score followed by Maximum Marginal Relevance (**MMR**).

**Overall processing flow**

```
Candidate keywords
        │
        ├── Candidate embeddings
        │
        └── Lexical candidate scores
                │
                ▼
       Document embedding
                │
                ▼
      Semantic relevance
                │
                ├── Lexical relevance
                │
                ▼
        Hybrid relevance
                │
                ▼
      Minimum relevance filter
                │
                ▼
              MMR
                │
                ▼
       Final keywords (max 20)
```

The implementation is therefore a semantic reranking and diversification stage, rather than another candidate-extraction mechanism.

**Document representation**

The first operation is to construct a single vector representing the document:

```
float[] documentEmbedding = mean(embeddings);
```

The implementation computes the arithmetic mean vector of all candidate embeddings.

For vectors:

```
v1​,v2​,...,vn​
```

the document representation is:

$$
D = \frac{1}{n}\sum_{i=1}^{n} v_i
$$

For each dimension `j`:

$$
D_j = \frac{1}{n}\sum_{i=1}^{n} v_{ij}
$$

This creates a document-level semantic centroid based on the candidate terms.

*Why use the mean ?*

The candidate embeddings already represent expressions extracted from the document. Their average provides a compact approximation of the semantic center of gravity of those candidates.

A candidate whose embedding is close to this centroid is considered semantically representative of the document.

This approach is deliberately lightweight: it does not require another LLM request or a separate document embedding API call.

**Embedding dimension consistency**

The `mean()` method verifies that all embeddings have the same dimensionality:

```
if (vector.length != dimension) {
    throw new IllegalArgumentException(
        "Embedding dimension mismatch");
}
```

This is mathematically necessary because vectors can only be added or averaged when they belong to the same vector space.

For example:

```
[0.2, 0.4, 0.1]
+
[0.3, 0.1, 0.8]
```

is valid.

But:

```
[0.2, 0.4, 0.1]
+
[0.3, 0.1]
```

is not.

This validation protects the subsequent similarity calculations from invalid input.

**Lexical relevance normalization**

The candidate extraction stage produces lexical scores that can have arbitrary absolute values.

For example:

```
santé publique          5.34
changement global       4.23
prévisions météo        3.67
...
```

These scores are normalized relative to the highest candidate score.

The implementation uses:

$L_{\text{normalized}}(c) = \frac{L(c)}{\max_{x \in C} L(x)}$

where:

* L(c) is the original lexical score,
* C is the candidate set.

Consequently, the best lexical candidate receives:

`1.0`

while all other candidates receive a value between approximately 0 and 1.


*Why normalization is necessary ?*

Semantic similarity and lexical scores are different quantities.

For example:

```
semantic similarity = 0.72
lexical score       = 4.23
```

Adding these values directly would be meaningless because they have different scales.

Normalization puts the lexical signal on a comparable scale before combining it with semantic relevance.

The implementation deliberately does not use min-max normalization. It uses normalization relative to the maximum because the lexical scores are positive and the objective is to preserve their relative strength with respect to the best candidate.

**Semantic relevance**

For every candidate, the implementation computes:

```
double semanticRelevance = cosine(
    documentEmbedding,
    embedding);
```

This is the cosine similarity between the candidate embedding and the document embedding.

For two vectors **A** and **B**:

$$
\operatorname{cosine}(A,B) = \frac{A \cdot B}{\|A\| \, \|B\|}
$$

with:

$$
A \cdot B = \sum_{i=1}^{n} a_i b_i
$$

and:

$$
\|A\| = \sqrt{\sum_{i=1}^{n} a_i^2}
$$

The cosine similarity measures the angle between two vectors rather than their absolute magnitude.

Conceptually:

```
high cosine similarity
        ↓
similar semantic direction
        ↓
candidate is semantically representative
        ↓
higher relevance
```

The implementation also explicitly handles zero vectors:

```
if (normA == 0.0 || normB == 0.0) {
    return 0.0;
}
```

This avoids division by zero.

**Hybrid relevance**

The step combines semantic and lexical relevance using fixed weights:

```
private static final double SEMANTIC_WEIGHT = 0.65;
private static final double LEXICAL_WEIGHT = 0.35;
```

The resulting score is:

$$
H = 0.65(0.807) + 0.35(0.689)
$$

which gives approximately:

$$
H = 0.766
$$

This corresponds to the values visible in the application's logs.

**Initial candidate ranking**

Each candidate is represented internally by a Scored object containing:

```
term
semantic relevance
lexical relevance
hybrid relevance
embedding
```

The complete pool is then sorted by hybrid relevance in descending order.

This means the initial ranking answers:

*Which candidates are simultaneously semantically relevant to the document and lexically strong ?*

However, this ranking alone is not used to generate the final keyword list.

The problem is redundancy.

For example, a document about climate change might produce:

```
changement climatique
réchauffement climatique
évolution climatique
variations climatiques
```

All four can have very high semantic relevance, but selecting all four would provide relatively little additional information.

This is the reason for the MMR stage.

**Minimum relevance filtering**

Before applying MMR, candidates with insufficient hybrid relevance are removed:

```
private static final double MIN_HYBRID_RELEVANCE = 0.45;
```

Only candidates satisfying:

$$
H(c) \geq 0.45
$$

are passed to the MMR algorithm.

This creates an important separation between:

* relevance filtering — eliminate weak candidates;
* diversification — choose the best non-redundant candidates among the remaining candidates.

This prevents MMR from selecting a very diverse but fundamentally weak keyword merely because it is different from the other candidates.

**Maximum Marginal Relevance (MMR)**

The core of the final selection is Maximum Marginal Relevance, commonly abbreviated MMR.

MMR is a ranking technique designed to balance:

* relevance to the query/document
* diversity with respect to already selected items

In this implementation, the MMR score is:

$$
\operatorname{MMR}(c) = \lambda H(c) - (1-\lambda)D(c)
$$

with:

$$
\lambda = 0.70
$$

Therefore:

$$
\operatorname{MMR}(c) = 0.70H(c) - 0.30D(c)
$$

where:

* H(c) = hybrid relevance,
* D(c) = maximum semantic similarity between candidate c and any already selected keyword.

**MMR diversity term**

For each candidate, the implementation computes its similarity with every already selected keyword:

```
double similarity = cosine(
    candidate.getEmbedding(),
    pool.get(selectedIndex).getEmbedding());
```

It keeps the maximum similarity:

$$
D(c) = \max_{s \in S} \operatorname{cosine}(c,s)
$$

where `S` is the set of already selected keywords.

This is important: the algorithm does not calculate an average similarity to the selected keywords.

It uses the maximum similarity.

Therefore, if a candidate is very similar to even one already selected keyword, it receives a strong redundancy penalty.

**Example of MMR behavior**

Suppose two candidates have:

```
Candidate A
hybrid relevance = 0.80
similarity to selected keywords = 0.10

Candidate B
hybrid relevance = 0.85
similarity to selected keywords = 0.90
```

With:

$$
\lambda = 0.70
$$

Candidate A :

$$
\operatorname{MMR}(A) = 0.70(0.80)−0.30(0.10)=0.53
$$

Candidate B :

$$
\operatorname{MMR}(B) = 0.70(0.85)−0.30(0.90)=0.325
$$

Despite B having a higher relevance score, A wins because B is highly redundant.

This is precisely the behavior desired for keyword extraction.

**Relevance/diversity balance**

The constant:

```
private static final double MMR_LAMBDA = 0.70;
```

controls the balance.

Conceptually:

```
λ → 1.0
    relevance dominates
    less diversification

λ → 0.0
    diversity dominates
    relevance becomes less important
```

The current value:

$$
\lambda = 0.70
$$

means the implementation deliberately favors relevance over diversity, while still applying a significant redundancy penalty.

This is a reasonable configuration for keyword extraction: the goal is not merely to obtain unrelated terms, but to obtain high-quality, document-relevant terms with limited semantic duplication.

**Maximum number of keywords**

The final result is limited to:

```
private static final int MAX_KEYWORDS = 20;
```

MMR stops when either:

```
20 keywords have been selected
```

or:

```
the candidate pool has been exhausted
```

Therefore, the actual number can be lower than `20` if insufficient candidates survive the hybrid relevance threshold.

**Greedy nature of the MMR algorithm**

The implementation uses a greedy MMR algorithm.

At each iteration:

* Examine every unselected candidate.
* Calculate its redundancy relative to the selected set.
* Calculate its MMR score.
* Select the candidate with the highest MMR score.
* Repeat.

It does not attempt to evaluate every possible combination of `20` keywords.

This is intentional: exhaustive combinatorial optimization would become computationally expensive as the candidate pool grows.

For the project's candidate pool size, the greedy approach provides a practical compromise between computational cost and selection quality.

**Computational complexity**

Let:

* N = number of candidates,
* K = number of final keywords,
* d = embedding dimension.

Each MMR iteration compares candidates against previously selected embeddings.

The approximate complexity is:

$$
O(KNd)
$$

because each candidate may be compared against up to K selected vectors, with each cosine similarity requiring `O(d)` operations.

Since the implementation limits:

$$
K \leq 20
$$

and the preceding candidate extraction stage limits the pool to a relatively small number of candidates, this remains practical.

**Deterministic candidate/embedding correspondence**

The step assumes that:

```
candidates[i]
```

corresponds exactly to:

```
embeddings[i]
```

It explicitly validates this:

```
if (embeddings.size() != candidates.size()) {
    throw new IllegalStateException(...)
}
```

This is an important invariant established by the previous EmbeddingStep.

Without this guarantee, the semantic score could be calculated using the embedding of a completely different candidate, producing silently incorrect results.

**Handling missing data**

If candidates or embeddings are absent or empty, the step does not fail the entire pipeline.

Instead:

```
ctx.setKeywords(List.of());
```

and a warning is logged.

However, if both collections exist but have different sizes, the situation is considered a programming/data-integrity error and an IllegalStateException is thrown.

This distinction is useful:

```
No data
→ valid empty result

Inconsistent data
→ pipeline error
```

**Mathematical model of the complete selection**

The complete process can be summarized mathematically.

For candidate c:

*Semantic relevance*

$$
S(c) = \operatorname{cosine}(D, E_c)
$$

where `D` is the mean document embedding and `Ec` is the candidate embedding.

*Normalized lexical relevance*

$$
L(c) = \frac{L_{\text{raw}}(c)}{\max_x L_{\text{raw}}(x)}
$$

*Hybrid relevance*

$$
H(c) = 0.65S(c) + 0.35L(c)
$$

Only candidates satisfying:

$H(c) \geq 0.45$

enter the MMR pool.

For every iteration, the redundancy term is:

$$
D(c) = \max_{s \in S} \operatorname{cosine}(E_c, E_s)
$$

and the final selection score is:

$$
\operatorname{MMR}(c) = 0.70H(c) - 0.30D(c)
$$

The candidate with the highest MMR score is selected, and the process repeats until `20` keywords have been obtained or the pool is exhausted.

Role in the overall pipeline

At this point, the architecture can be summarized as:

```
PDF
 │
 ▼
PdfParsingStep
 │
 │ text + language
 ▼
ChunkingStep
 │
 │ structurally appropriate chunks
 ▼
CandidateExtractionStep
 │
 │ lexical candidates
 │ POS quality
 │ frequency
 │ chunk coverage
 ▼
EmbeddingStep
 │
 │ semantic vectors
 ▼
KeywordSelectionStep
 │
 ├── document semantic representation
 ├── semantic relevance
 ├── lexical relevance
 ├── hybrid relevance
 ├── relevance filtering
 └── MMR diversification
 │
 ▼
Final keyword list
 │
 ▼
LlmRefinementStep
```

The key architectural distinction is therefore:

`CandidateExtractionStep` asks "**Is this expression linguistically and statistically a good candidate ?**"

while:

`KeywordSelectionStep` asks "**Which of these candidates best represent the document, while avoiding semantic redundancy ?**"

**Summary**

`KeywordSelectionStep` performs the final algorithmic selection of keywords before optional LLM refinement. It combines the lexical scores produced by candidate extraction with semantic similarity derived from `bge-m3` embeddings.

A document-level embedding is approximated by averaging all candidate vectors, and cosine similarity is used to measure each candidate's semantic relevance to this representation. The semantic and normalized lexical signals are combined with weights of `65%` and `35%`, respectively.

Candidates below a hybrid relevance threshold of `0.45` are discarded. The remaining candidates are then selected using a greedy Maximum Marginal Relevance algorithm with `λ=0.70`, favoring highly relevant keywords while penalizing candidates that are semantically redundant with already selected keywords.

The process produces at most `20` final keywords.

This makes the step the semantic ranking and diversification layer of the pipeline: it transforms the broad set of linguistically plausible candidates into a compact, semantically representative and non-redundant keyword set.
