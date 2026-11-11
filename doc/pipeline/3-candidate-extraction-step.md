The `CandidateExtractionStep` is responsible for generating and ranking a pool of candidate keywords from the document chunks. It is the first linguistically driven stage of the pipeline and provides the candidate pool consumed by subsequent embedding and semantic-selection stages.

It is executed as pipeline step 3.

The step combines:

* language-specific stopword and generic-term filtering;
* POS tagging through a language-independent POS abstraction;
* unigram, bigram and trigram extraction;
* punctuation-aware tokenization;
* POS-pattern scoring;
* frequency and chunk-coverage statistics;
* lexical quality filtering;
* redundancy removal;
* a weighted candidate-ranking formula.

**Overall processing flow**

```
Document chunks
      │
      ▼
Sentence splitting
      │
      ▼
Punctuation-aware tokenization
      │
      ▼
Language-specific POS tagging
      │
      ▼
POS normalization
      │
      ├── Unigrams
      ├── Bigrams
      └── Trigrams
      │
      ▼
Lexical validation
      │
      ▼
Candidate statistics
      │
      ▼
Frequency / coverage / POS scoring
      │
      ▼
Redundancy removal
      │
      ▼
Top 50 candidates
```

**Candidate generation**

Each chunk is processed independently, while candidate statistics are accumulated globally across the document.

The chunk is first divided into sentences using sentence-ending punctuation:

```
.  !  ?
```

Each sentence is then tokenized and POS-tagged once. The resulting POS sequence is reused for unigram, bigram and trigram extraction.

This is important from a performance perspective: POS tagging is performed once per sentence rather than separately for each n-gram type.

**Language-aware resources**

The step loads four resource sets:

* French stopwords;
* English stopwords;
* French generic terms;
* English generic terms.

They are loaded from the application resources and converted to lowercase using `Locale.ROOT`.

The appropriate sets are selected according to `ProcessingContext.getLanguage()`.

Consequently, lexical filtering is language-aware while the extraction and ranking algorithms themselves remain largely language-independent.

Currently supported languages are:

```
fr
en
```

An unsupported language results in an `IllegalArgumentException`.

**Stopword and generic-term filtering**

Two different lexical filtering concepts are used.

Stopwords remove grammatical or function words that are unlikely to be useful keywords.

Generic terms remove domain-independent words that may be syntactically valid but have insufficient keyword specificity.

Both filters are applied to unigrams and to every token composing a multi-word candidate.

This distinction is useful because a word can be linguistically meaningful while still being too generic to represent the document's subject matter.

**POS normalization**

The extraction algorithm does not directly depend on OpenNLP's original tagset.

`PosTaggerService` converts model-specific POS tags into the application's internal PosCategory taxonomy, allowing the candidate extraction logic to work with concepts such as:

```
NOUN
PROPN
ADJ
VERB
AUX
DET
PRON
ADV
ADP
CONJ
NUM
...
```

This provides an abstraction layer between the underlying POS model and the keyword extraction algorithm.

**Unigram extraction**

Single-word candidates are retained when:

* their normalized length is at least 3 characters;
* they are not stopwords;
* they are not generic terms;
* they are not composed exclusively of digits;
* their POS category is considered relevant.

Nouns and proper names are the primary accepted categories.

Current POS quality values are:

```
PROPN → 1.20
NOUN  → 0.85
other → 0.00
```

The POS quality subsequently contributes directly to the candidate's final ranking score.

**Bigram extraction**

Bigrams are generated from adjacent lexical tokens.

A bigram must satisfy three independent conditions:

* Both tokens pass lexical validation.
* No hard punctuation boundary exists between them.
* Their POS combination matches one of the accepted POS patterns.

The current patterns are:

| POS pattern  | Quality |
| ------------ | ------: |
| NOUN + ADJ   |    1.50 |
| ADJ + NOUN   |    1.40 |
| PROPN + NOUN |    1.35 |
| NOUN + PROPN |    1.35 |

Additionally, the implementation explicitly requires that at least one of the two POS categories is a noun or proper name. This prevents purely adjectival or otherwise weak combinations from becoming candidates.

**Trigram extraction**

Trigrams follow the same general principles as bigrams but use more restrictive POS-pattern matching.

Accepted patterns include:

| POS pattern         | Quality |
| ------------------- | ------: |
| NOUN + ADJ + ADJ    |    1.80 |
| NOUN + NOUN + ADJ   |    1.50 |
| NOUN + ADJ + NOUN   |    1.40 |
| ADJ + NOUN + ADJ    |    1.35 |
| ADJ + NOUN + NOUN   |    1.30 |
| PROPN + NOUN + NOUN |    1.40 |

This deliberately favors noun-phrase-like constructions rather than arbitrary three-word sequences.

**Invalid POS filtering**

Candidates containing certain POS categories are rejected before pattern matching.

A POS is considered invalid when it is:

* a verb;
* a function word;
* an auxiliary.

This prevents constructions containing grammatical or verbal structures from being considered keyword candidates.

**Punctuation-aware tokenization**

One of the technically important characteristics of this step is that punctuation is not simply discarded.

The tokenizer creates Token objects containing both the lexical token and information about whether a hard punctuation boundary occurred before it.

For example:

```
agriculture, transport
```

must not produce:

```
agriculture transport
```

as a candidate bigram.

The implementation therefore preserves punctuation boundaries while still removing punctuation from the lexical token itself.

Hard boundaries currently include:

```
, ; :
( )
[ ]
{ }
/ |
!
?
"
```

Characters such as -, +, # and ' are retained inside tokens. This allows terms such as hyphenated expressions or technical identifiers to remain intact.

**Candidate statistics**

Each candidate is represented by CandidateStats.

The extraction stage maintains at least two important document-level statistics:

* frequency — number of occurrences of the candidate;
* chunk frequency — number of distinct chunks containing the candidate.

A candidate occurring multiple times inside the same chunk increases its frequency but increases its chunk frequency only once.

For example:

```
frequency       = 5
chunkFrequency  = 2
```

means that the candidate occurred five times overall but appeared in two distinct chunks.

This distinction is subsequently used to favor candidates that are distributed across the document rather than concentrated in a single location.

The best POS interpretation encountered for a candidate is also retained.

**Candidate normalization**

Candidates are normalized using:

* trimming;
* lowercase conversion using Locale.ROOT;
* whitespace normalization.

The normalized representation is used as the candidate map key.

This means that different textual representations differing only in case or redundant whitespace are aggregated into the same candidate statistics.

**Candidate validation**

Unigrams and n-grams have slightly different validation rules.

For unigrams:

* minimum length: 3 characters;
* stopwords rejected;
* generic terms rejected;
* purely numeric tokens rejected.

For n-gram components:

* minimum token length: 2 characters;
* stopwords rejected;
* generic terms rejected;
* purely numeric tokens rejected.

There is also a final candidate-level validation stage that verifies:

* expected n-gram size;
* positive frequency;
* positive POS quality;
* minimum quality for single-occurrence multi-word candidates;
* absence of weak lexical constructions.

**Single-occurrence n-gram filtering**

A particularly useful quality rule applies to multi-word candidates occurring only once.

If a bigram or trigram:

* occurs once;
* occurs in only one chunk;
* has POS quality below 1.40;

it is rejected.

This prevents weak one-off expressions from entering the candidate pool merely because they happen to match a syntactic pattern.

**Generic-prefix filtering**

The step also performs a second generic-term check at the expression level.

For a multi-word candidate, the first token is checked against the generic-term dictionary.

For example, if a generic lexical construction appears at the beginning of a phrase, the entire candidate can be rejected.

This provides a lightweight mechanism for eliminating systematically weak noun phrases without requiring a complete linguistic grammar.

**Candidate scoring**

The final ranking score combines several independent signals:

frequency
× chunk coverage
× n-gram bonus
× POS quality
× frequency penalty
× specificity bonus

Frequency uses logarithmic scaling:

```
Math.log1p(frequency)
```

This prevents very frequent words from completely dominating the ranking.

The chunk-coverage component is:

```
1 + CHUNK_COVERAGE_WEIGHT × log1p(chunkFrequency)
```

with:

```
CHUNK_COVERAGE_WEIGHT = 0.5
```

Multi-word candidates receive an additional chunk-coverage multiplier:

```
NGRAM_CHUNK_COVERAGE_BONUS = 1.20
```

**N-gram weighting**

The current n-gram bonuses are:

| Candidate type | Bonus |
| -------------- | ----: |
| Unigram        |  0.70 |
| Bigram         |  1.20 |
| Trigram        |  1.10 |

This deliberately favors multi-word expressions over isolated words, while keeping trigrams slightly below bigrams.

An additional specificity multiplier is applied:

| Candidate type | Specificity |
| -------------- | ----------: |
| Unigram        |        0.90 |
| Bigram         |        1.15 |
| Trigram        |        1.20 |

Consequently, multi-word expressions benefit from two separate mechanisms: their n-gram bonus and their specificity multiplier.

**High-frequency unigram penalty**

Very frequent unigrams are explicitly penalized.

When a unigram occurs at least five times:

```
HIGH_FREQUENCY_UNIGRAM_THRESHOLD = 5
HIGH_FREQUENCY_UNIGRAM_PENALTY   = 0.75
```

its score is multiplied by `0.75`.

The objective is to reduce the influence of words that are frequent but potentially too broad to be useful as document keywords.

**Redundancy removal**

After ranking, candidates are filtered for lexical redundancy before the final top-50 list is produced.

Two unigrams are never considered redundant simply because they are different words.

For multi-word candidates, redundancy is detected through token-sequence containment.

For example:

```
machine learning
machine learning algorithm
```

are considered redundant because one candidate contains the other as a contiguous token sequence.

The ranked candidate list is traversed from highest to lowest score, so the higher-ranked expression is retained while the redundant lower-ranked expression is discarded.

This is a deliberately lexical redundancy mechanism; it does not use embeddings or semantic similarity. Semantic diversification is handled later by the embedding/MMR stage.

**Candidate pool size**

The step produces a maximum of:

`50 candidates`

through:

```
MAX_CANDIDATES = 50
```

These candidates are stored in the ProcessingContext together with their calculated scores.

The 50-candidate pool is therefore an intermediate representation rather than the final keyword list.

**Separation of responsibilities**

The architecture deliberately separates candidate extraction from semantic selection.

`CandidateExtractionStep` answers:

**Which terms and expressions are linguistically and statistically plausible keywords?**

The subsequent embedding/selection stages answer:

**Which of those candidates are semantically representative and sufficiently diverse?**

This separation prevents the extraction step from becoming dependent on embedding models or LLM inference.

**Language independence**

The algorithmic layer is language-agnostic, particularly:

* candidate statistics;
* normalization;
* frequency calculation;
* chunk coverage;
* ranking;
* redundancy detection;
* punctuation handling;
* n-gram generation;
* POS-category-based filtering.

The language-dependent components are isolated to:

* stopword resources;
* generic-term resources;
* POS model selection.

The use of the internal PosCategory abstraction is particularly important because it allows the same extraction and ranking logic to operate independently of the underlying POS tagset.

**Pipeline integration**

The step reads:

```
ctx.getChunks()
ctx.getLanguage()
```

and writes:

```
ctx.setCandidates(...)
ctx.setCandidateScores(...)
```

It also integrates with the application's benchmark mechanism and logs the number of selected candidates.

Overall, this stage acts as a high-recall linguistic candidate generator followed by a statistical/lexical ranking and pruning layer, producing a compact and higher-quality candidate pool for the subsequent semantic selection stage.
