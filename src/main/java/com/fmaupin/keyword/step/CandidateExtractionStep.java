/*
 * Keyword Extractor
 *
 * Copyright (C) 2026 Fabrice Maupin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.fmaupin.keyword.step;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.enumeration.PosCategory;
import com.fmaupin.keyword.exceptions.KeywordException;
import com.fmaupin.keyword.helper.PosTagging;
import com.fmaupin.keyword.model.BigramPosPattern;
import com.fmaupin.keyword.model.CandidateStats;
import com.fmaupin.keyword.model.TrigramPosPattern;
import com.fmaupin.keyword.model.ProcessingContext;
import com.fmaupin.keyword.model.Token;
import com.fmaupin.keyword.pipeline.PipelineStep;
import com.fmaupin.keyword.service.PosTaggerService;

import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for extracting candidate keywords from the text
 * content of a document.
 */
@Component
@Order(3)
@Slf4j
public class CandidateExtractionStep
        implements PipelineStep<ProcessingContext, ProcessingContext> {
    private static final int MAX_CANDIDATES = 50;

    private static final int MIN_UNIGRAM_LENGTH = 3;
    private static final int MIN_NGRAM_TOKEN_LENGTH = 2;

    /*
     * N-gram bonuses.
     */
    private static final double UNIGRAM_BONUS = 0.70;
    private static final double BIGRAM_BONUS = 1.20;
    private static final double TRIGRAM_BONUS = 1.10;

    /*
     * Importance of the candidate's presence in multiple chunks.
     */
    private static final double CHUNK_COVERAGE_WEIGHT = 0.5;

    /*
     * Minimum POS quality for a single-occurrence multi-word candidate.
     *
     * This prevents weak expressions.
     */
    private static final double MIN_SINGLE_OCCURRENCE_NGRAM_POS = 1.40;

    /**
     * Threshold for high-frequency unigrams - penalty for too frequent unigrams.
     */
    private static final int HIGH_FREQUENCY_UNIGRAM_THRESHOLD = 5;
    private static final double HIGH_FREQUENCY_UNIGRAM_PENALTY = 0.75;

    /*
     * Bonus for n-grams that appear in multiple chunks.
     */
    private static final double NGRAM_CHUNK_COVERAGE_BONUS = 1.20;

    /**
     * List of terms or words that are too generic
     * to be considered as keywords.
     */
    private static Set<String> stopWordsFr;
    private static Set<String> stopWordsEn;

    private static Set<String> genericTermsFr;
    private static Set<String> genericTermsEn;

    private final PosTaggerService posTaggerService;

    private static final List<BigramPosPattern> BIGRAM_PATTERNS = List.of(
            new BigramPosPattern(
                    1.50,
                    PosCategory.NOUN,
                    PosCategory.ADJ),

            new BigramPosPattern(
                    1.40,
                    PosCategory.ADJ,
                    PosCategory.NOUN),

            new BigramPosPattern(
                    1.35,
                    PosCategory.PROPN,
                    PosCategory.NOUN),

            new BigramPosPattern(
                    1.35,
                    PosCategory.NOUN,
                    PosCategory.PROPN));

    private static final List<TrigramPosPattern> TRIGRAM_PATTERNS = List.of(
            new TrigramPosPattern(
                    1.80,
                    PosCategory.NOUN,
                    PosCategory.ADJ,
                    PosCategory.ADJ),

            new TrigramPosPattern(
                    1.50,
                    PosCategory.NOUN,
                    PosCategory.NOUN,
                    PosCategory.ADJ),

            new TrigramPosPattern(
                    1.40,
                    PosCategory.NOUN,
                    PosCategory.ADJ,
                    PosCategory.NOUN),

            new TrigramPosPattern(
                    1.35,
                    PosCategory.ADJ,
                    PosCategory.NOUN,
                    PosCategory.ADJ),

            new TrigramPosPattern(
                    1.30,
                    PosCategory.ADJ,
                    PosCategory.NOUN,
                    PosCategory.NOUN),

            new TrigramPosPattern(
                    1.40,
                    PosCategory.PROPN,
                    PosCategory.NOUN,
                    PosCategory.NOUN));

    public CandidateExtractionStep(
            PosTaggerService posTaggerService) {
        this.posTaggerService = posTaggerService;
    }

    static {
        try {
            stopWordsFr = loadFile("/fr/stopwords.txt");
            stopWordsEn = loadFile("/en/stopwords.txt");

            genericTermsFr = loadFile("/fr/generic-terms.txt");
            genericTermsEn = loadFile("/en/generic-terms.txt");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Error loading candidate extraction step resources",
                    e);
        }
    }

    @Override
    @SuppressWarnings("null")
    public ProcessingContext process(
            ProcessingContext ctx)
            throws KeywordException {
        ctx.getBenchmark().start();

        Set<String> stopWords = resolveStopWords(ctx.getLanguage());

        Set<String> genericTerms = resolveGenericTerms(ctx.getLanguage());

        Map<String, CandidateStats> candidates = new HashMap<>();

        for (String chunk : ctx.getChunks()) {
            extractFromChunk(
                    chunk,
                    stopWords,
                    genericTerms,
                    ctx.getLanguage(),
                    candidates);
        }

        List<CandidateStats> selected = topCandidateStats(
                genericTerms,
                candidates,
                MAX_CANDIDATES);

        ctx.setCandidates(selected.stream()
                .map(CandidateStats::getTerm)
                .toList());

        ctx.setCandidateScores(
                selected.stream()
                        .collect(
                                Collectors.toMap(
                                        candidate -> candidateKey(candidate.getTerm()),
                                        this::score)));

        log.info(
                "Selected {} candidate keywords",
                selected.size());

        ctx.getBenchmark().end(
                "Candidate extraction");

        return ctx;
    }

    private String candidateKey(String term) {
        return normalize(term);
    }

    // =========================================================
    // Resources
    // =========================================================

    @SuppressWarnings("null")
    private static Set<String> loadFile(
            String resource)
            throws IOException {
        try (InputStream input = CandidateExtractionStep.class
                .getResourceAsStream(resource)) {
            if (input == null) {
                log.error(
                        "Resource not found: {}. Using empty set.",
                        resource);

                return Collections.emptySet();
            }

            return new BufferedReader(
                    new InputStreamReader(
                            input,
                            StandardCharsets.UTF_8))
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(
                            Collectors.toUnmodifiableSet());
        }
    }

    private Set<String> resolveStopWords(
            String language) {
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "fr" -> stopWordsFr;
            case "en" -> stopWordsEn;
            default -> throw new IllegalArgumentException(
                    "Unsupported language: " + language);
        };
    }

    private Set<String> resolveGenericTerms(
            String language) {
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "fr" -> genericTermsFr;
            case "en" -> genericTermsEn;
            default -> throw new IllegalArgumentException(
                    "Unsupported language: " + language);
        };
    }

    // =========================================================
    // Extraction
    // =========================================================

    private void extractFromChunk(
            String chunk,
            Set<String> stopWords,
            Set<String> genericTerms,
            String language,
            Map<String, CandidateStats> candidates) {
        Set<String> candidatesInChunk = new HashSet<>();

        String[] sentences = chunk.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            extractFromSentence(
                    sentence,
                    stopWords,
                    genericTerms,
                    language,
                    candidates,
                    candidatesInChunk);
        }
    }

    private void extractFromSentence(
            String sentence,
            Set<String> stopWords,
            Set<String> genericTerms,
            String language,
            Map<String, CandidateStats> candidates,
            Set<String> candidatesInChunk) {
        List<Token> tokens = tokenizeWithBoundaries(sentence);

        if (tokens.isEmpty()) {
            return;
        }

        String[] words = extractWords(tokens);
        Optional<PosCategory[]> posTagsResult = tagWords(words, language);

        if (posTagsResult.isEmpty()) {
            return;
        }

        PosCategory[] posTags = posTagsResult.get();

        extractUnigrams(
                words,
                posTags,
                stopWords,
                genericTerms,
                candidates,
                candidatesInChunk);

        extractBigrams(
                words,
                tokens,
                posTags,
                stopWords,
                genericTerms,
                candidates,
                candidatesInChunk);

        extractTrigrams(
                words,
                tokens,
                posTags,
                stopWords,
                genericTerms,
                candidates,
                candidatesInChunk);
    }

    @SuppressWarnings("null")
    private String[] extractWords(
            List<Token> tokens) {
        return tokens.stream()
                .map(Token::getText)
                .toArray(String[]::new);
    }

    private Optional<PosCategory[]> tagWords(
            String[] words,
            String language) {
        PosCategory[] posTags = posTaggerService.tagCategories(
                words,
                language);

        if (posTags.length != words.length) {
            log.warn(
                    "POS tagger returned {} tags for {} tokens. "
                            + "Skipping sentence.",
                    posTags.length,
                    words.length);

            return Optional.empty();
        }

        return Optional.of(posTags);
    }

    private void extractUnigrams(
            String[] words,
            PosCategory[] posTags,
            Set<String> stopWords,
            Set<String> genericTerms,
            Map<String, CandidateStats> candidates,
            Set<String> candidatesInChunk) {
        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            double posQuality = unigramPosQuality(
                    posTags[i]);

            if (!isValidUnigram(
                    word,
                    stopWords,
                    genericTerms) || posQuality <= 0.0) {
                continue;
            }

            addCandidate(
                    word,
                    1,
                    posQuality,
                    candidates,
                    candidatesInChunk);
        }
    }

    private void extractBigrams(
            String[] words,
            List<Token> tokens,
            PosCategory[] posTags,
            Set<String> stopWords,
            Set<String> genericTerms,
            Map<String, CandidateStats> candidates,
            Set<String> candidatesInChunk) {
        for (int i = 0; i < words.length - 1; i++) {
            double posQuality = bigramPosQuality(
                    posTags[i],
                    posTags[i + 1]);

            String w1 = words[i];
            String w2 = words[i + 1];

            if (!isValidNgram(
                    w1,
                    w2,
                    stopWords,
                    genericTerms) || hasHardBoundary(tokens, i, i + 1) || posQuality <= 0.0) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug(
                        "BIGRAM: '{}' '{}' -> POS [{} / {}], quality={}",
                        w1,
                        w2,
                        posTags[i],
                        posTags[i + 1],
                        posQuality);
            }

            addCandidate(
                    w1 + " " + w2,
                    2,
                    posQuality,
                    candidates,
                    candidatesInChunk);
        }
    }

    private void extractTrigrams(
            String[] words,
            List<Token> tokens,
            PosCategory[] posTags,
            Set<String> stopWords,
            Set<String> genericTerms,
            Map<String, CandidateStats> candidates,
            Set<String> candidatesInChunk) {
        for (int i = 0; i < words.length - 2; i++) {
            String w1 = words[i];
            String w2 = words[i + 1];
            String w3 = words[i + 2];

            double posQuality = trigramPosQuality(
                    posTags[i],
                    posTags[i + 1],
                    posTags[i + 2]);

            if (hasHardBoundary(tokens, i, i + 1)
                    || hasHardBoundary(tokens, i + 1, i + 2) || !isValidNgram(
                            w1,
                            w2,
                            w3,
                            stopWords,
                            genericTerms)
                    || posQuality <= 0.0) {
                continue;
            }

            addCandidate(
                    w1 + " " + w2 + " " + w3,
                    3,
                    posQuality,
                    candidates,
                    candidatesInChunk);
        }
    }

    // =========================================================
    // Candidate registration
    // =========================================================

    private void addCandidate(
            String term,
            int ngramSize,
            double posQuality,
            Map<String, CandidateStats> candidates,
            Set<String> candidatesInChunk) {
        String normalized = candidateKey(term);

        CandidateStats stats = candidates.computeIfAbsent(
                normalized,
                key -> new CandidateStats(
                        term,
                        ngramSize,
                        posQuality));

        stats.setFrequency(
                stats.getFrequency() + 1);

        /*
         * Keep the best POS interpretation.
         */
        stats.setPosQuality(
                Math.max(
                        stats.getPosQuality(),
                        posQuality));

        /*
         * Count the candidate only once per chunk.
         */
        if (candidatesInChunk.add(normalized)) {
            stats.setChunkFrequency(
                    stats.getChunkFrequency() + 1);
        }
    }

    // =========================================================
    // POS scoring
    // =========================================================

    /**
     * Quality of a unigram based on its POS.
     *
     * Nouns and proper names are interesting candidates.
     * Verbs, adverbs, determiners, etc. are rejected.
     */
    private double unigramPosQuality(
            PosCategory pos) {
        if (PosTagging.isProperName(pos)) {
            return 1.20;
        }

        if (PosTagging.isNoun(pos)) {
            return 0.85;
        }

        return 0.0;
    }

    /**
     * Quality of a bigram based on its POS pattern.
     */
    @SuppressWarnings("null")
    private double bigramPosQuality(
            PosCategory pos1,
            PosCategory pos2) {
        if (containsInvalidPos(pos1, pos2)) {
            return 0.0;
        }

        if (!PosTagging.isNoun(pos1)
                && !PosTagging.isProperName(pos1)
                && !PosTagging.isNoun(pos2)
                && !PosTagging.isProperName(pos2)) {
            return 0.0;
        }

        return BIGRAM_PATTERNS.stream()
                .filter(pattern -> pattern.matches(pos1, pos2))
                .mapToDouble(BigramPosPattern::quality)
                .findFirst()
                .orElse(0.0);
    }

    /**
     * Quality of a trigram based on its POS pattern.
     */
    @SuppressWarnings("null")
    private double trigramPosQuality(
            PosCategory pos1,
            PosCategory pos2,
            PosCategory pos3) {
        if (containsInvalidPos(pos1, pos2, pos3)) {
            return 0.0;
        }

        return TRIGRAM_PATTERNS.stream()
                .filter(pattern -> pattern.matches(pos1, pos2, pos3))
                .mapToDouble(TrigramPosPattern::quality)
                .findFirst()
                .orElse(0.0);
    }

    private boolean containsInvalidPos(
            PosCategory... positions) {
        return Arrays.stream(positions)
                .anyMatch(this::isInvalidCandidatePos);
    }

    private boolean isInvalidCandidatePos(
            PosCategory pos) {
        return PosTagging.isVerb(pos)
                || PosTagging.isFunctionWord(pos)
                || pos == PosCategory.AUX;
    }

    // =========================================================
    // Tokenization / boundaries
    // =========================================================

    /**
     * Punctuation is not simply discarded.
     *
     * We remember whether a hard punctuation boundary exists
     * between two lexical tokens.
     */
    private List<Token> tokenizeWithBoundaries(
            String sentence) {
        List<Token> result = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        boolean hardBoundary = false;

        for (int i = 0; i < sentence.length(); i++) {
            char c = sentence.charAt(i);

            if (Character.isLetterOrDigit(c)
                    || c == '-'
                    || c == '+'
                    || c == '#'
                    || c == '\'') {

                current.append(c);
                continue;
            }

            /*
             * We encountered a separator.
             */
            if (!current.isEmpty()) {
                result.add(
                        new Token(
                                current.toString(),
                                hardBoundary));

                current.setLength(0);

                hardBoundary = false;
            }

            /*
             * Strong punctuation boundaries.
             */
            if (isHardBoundary(c)) {
                hardBoundary = true;
            }
        }

        if (!current.isEmpty()) {
            result.add(
                    new Token(
                            current.toString(),
                            hardBoundary));
        }

        return result;
    }

    /**
     * Punctuation that prevents two tokens from forming
     * an n-gram.
     *
     * Example:
     *
     * agriculture, transport
     *
     * must NOT produce:
     *
     * agriculture transport
     */
    private boolean isHardBoundary(
            char c) {

        return switch (c) {
            case ',', ';', ':',
                    '(', ')',
                    '[', ']',
                    '{', '}',
                    '/', '|',
                    '!', '?',
                    '"' ->
                true;

            default ->
                false;
        };
    }

    /**
     * Returns true when a hard punctuation boundary exists
     * between token index left and token index right.
     */
    private boolean hasHardBoundary(
            List<Token> tokens,
            int left,
            int right) {

        if (left < 0
                || right >= tokens.size()
                || right != left + 1) {

            return true;
        }

        return tokens.get(right).isHardBoundary();
    }

    private String normalize(String value) {
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    // =========================================================
    // Validation
    // =========================================================

    private boolean isValidUnigram(
            String word,
            Set<String> stopWords,
            Set<String> genericTerms) {
        String normalized = normalize(word);

        if (normalized.length() < MIN_UNIGRAM_LENGTH) {
            return false;
        }

        if (stopWords.contains(normalized)) {
            return false;
        }

        if (genericTerms.contains(normalized)) {
            return false;
        }

        /*
         * Reject tokens composed only of digits.
         */
        return (!normalized.matches("\\d+"));
    }

    private boolean isValidNgram(
            String w1,
            String w2,
            Set<String> stopWords,
            Set<String> genericTerms) {
        return isValidNgramToken(
                w1,
                stopWords, genericTerms)
                && isValidNgramToken(
                        w2,
                        stopWords, genericTerms);
    }

    private boolean isValidNgram(
            String w1,
            String w2,
            String w3,
            Set<String> stopWords,
            Set<String> genericTerms) {
        return isValidNgramToken(
                w1,
                stopWords, genericTerms)
                && isValidNgramToken(
                        w2,
                        stopWords, genericTerms)
                && isValidNgramToken(
                        w3,
                        stopWords, genericTerms);
    }

    private boolean isValidNgramToken(
            String token,
            Set<String> stopWords,
            Set<String> genericTerms) {
        String normalized = normalize(token);

        if (normalized.length() < MIN_NGRAM_TOKEN_LENGTH) {
            return false;
        }

        if (stopWords.contains(normalized)) {
            return false;
        }

        if (genericTerms.contains(normalized)) {
            return false;
        }

        return !normalized.matches("\\d+");
    }

    // =========================================================
    // Ranking
    // =========================================================

    @SuppressWarnings("null")
    private List<CandidateStats> topCandidateStats(
            Set<String> genericTerms,
            Map<String, CandidateStats> candidates,
            int limit) {
        List<CandidateStats> ranked = candidates.values()
                .stream()
                .filter(candidate -> isUsefulCandidate(genericTerms, candidate))
                .sorted(
                        Comparator
                                .comparingDouble(
                                        this::score)
                                .reversed()
                                .thenComparing(
                                        CandidateStats::getTerm,
                                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<CandidateStats> selected = removeRedundantCandidates(
                ranked,
                limit);

        logCandidates(selected);

        return selected;
    }

    private boolean isUsefulCandidate(
            Set<String> genericTerms,
            CandidateStats candidate) {
        /*
         * Basic unigram length.
         */
        if (candidate.getNgramSize() == 1
                && candidate.getTerm().length() < MIN_UNIGRAM_LENGTH) {
            return false;
        }

        /*
         * Verify the expected number of tokens.
         */
        if (candidate.getNgramSize() >= 2) {
            String[] tokens = candidate.getTerm().split(" ");

            if (tokens.length < candidate.getNgramSize()) {
                return false;
            }
        }

        /*
         * No useful POS = no candidate.
         */
        if (candidate.getPosQuality() <= 0.0) {
            return false;
        }

        /*
         * Frequency must be positive.
         */
        if (candidate.getFrequency() < 1) {
            return false;
        }

        /*
         * A multi-word expression seen only once and
         * having a weak POS structure is rejected.
         *
         * This removes candidates such as:
         *
         * science quotidien
         * météorologie température
         * numériques satellites
         */
        if (candidate.getNgramSize() >= 2
                && candidate.getFrequency() == 1
                && candidate.getChunkFrequency() == 1
                && candidate.getPosQuality() < MIN_SINGLE_OCCURRENCE_NGRAM_POS) {

            return false;
        }

        /*
         * Reject obviously weak lexical patterns.
         */
        return !isWeakCandidate(genericTerms, candidate);
    }

    /**
     * Additional lexical quality rules.
     */
    private boolean isWeakCandidate(
            Set<String> genericTerms,
            CandidateStats candidate) {
        String normalized = normalize(candidate.getTerm());

        /*
         * Candidate starts with a very generic lexical
         * construction.
         */
        String[] tokens = normalized.split(" ");

        if (tokens.length >= 2) {
            String first = tokens[0];

            if (genericTerms.contains(first)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Final candidate score.
     *
     * score =
     *
     * frequency
     * × chunk coverage
     * × ngram bonus
     * × POS quality
     * × frequency penalty
     * × specificity bonus
     */
    private double score(
            CandidateStats candidate) {
        /*
         * Logarithmic frequency.
         *
         * freq 1 -> 0.69
         * freq 5 -> 1.79
         * freq 20 -> 3.04
         * freq 100 -> 4.61
         */
        double frequencyScore = Math.log1p(
                candidate.getFrequency());

        /*
         * Chunk coverage.
         */
        double chunkScore = 1.0
                + CHUNK_COVERAGE_WEIGHT
                        * Math.log1p(
                                candidate
                                        .getChunkFrequency());

        if (candidate.getNgramSize() >= 2) {
            chunkScore *= NGRAM_CHUNK_COVERAGE_BONUS;
        }

        /*
         * Multi-word expression bonus.
         */
        double ngramBonus = switch (candidate.getNgramSize()) {
            case 1 ->
                UNIGRAM_BONUS;

            case 2 ->
                BIGRAM_BONUS;

            case 3 ->
                TRIGRAM_BONUS;

            default ->
                1.0;
        };

        /**
         * Penalty for too frequent unigrams.
         */
        double frequencyPenalty = 1.0;

        if (candidate.getNgramSize() == 1
                && candidate.getFrequency() >= HIGH_FREQUENCY_UNIGRAM_THRESHOLD) {
            frequencyPenalty = HIGH_FREQUENCY_UNIGRAM_PENALTY;
        }

        /**
         * Specificity bonus for n-grams.
         */
        double specificityBonus = specificityBonus(candidate);

        /*
         * POS quality.
         */
        double posQuality = candidate.getPosQuality();

        return frequencyScore
                * chunkScore
                * ngramBonus
                * posQuality
                * frequencyPenalty
                * specificityBonus;
    }

    private double specificityBonus(
            CandidateStats candidate) {
        return switch (candidate.getNgramSize()) {
            case 1 -> 0.90;
            case 2 -> 1.15;
            case 3 -> 1.20;
            default -> 1.0;
        };
    }

    // =========================================================
    // Redundancy
    // =========================================================

    private List<CandidateStats> removeRedundantCandidates(
            List<CandidateStats> ranked,
            int limit) {
        List<CandidateStats> selected = new ArrayList<>();

        for (CandidateStats candidate : ranked) {
            boolean redundant = false;

            for (CandidateStats existing : selected) {
                if (isLexicallyRedundant(
                        candidate,
                        existing)) {
                    redundant = true;
                    break;
                }
            }

            if (!redundant) {
                selected.add(candidate);
            }

            if (selected.size() >= limit) {
                break;
            }
        }

        return selected;
    }

    private boolean isLexicallyRedundant(
            CandidateStats candidate,
            CandidateStats existing) {
        /*
         * Two different unigrams are not redundant.
         */
        if (candidate.getNgramSize() == 1
                && existing.getNgramSize() == 1) {
            return false;
        }

        List<String> candidateTokens = tokenizeTerm(
                candidate.getTerm());

        List<String> existingTokens = tokenizeTerm(
                existing.getTerm());

        /*
         * Exact match.
         */
        if (candidateTokens.equals(
                existingTokens)) {
            return true;
        }

        /*
         * Sub-expression.
         *
         * Example :
         * 
         * machine learning
         * machine learning algorithm
         */
        return containsTokenSequence(
                candidateTokens,
                existingTokens)
                || containsTokenSequence(
                        existingTokens,
                        candidateTokens);
    }

    private List<String> tokenizeTerm(
            String term) {
        return Arrays.stream(
                normalize(term)
                        .split(" "))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private boolean containsTokenSequence(
            List<String> source,
            List<String> target) {
        if (target.size() >= source.size()) {
            return false;
        }

        for (int i = 0; i <= source.size() - target.size(); i++) {
            boolean match = true;

            for (int j = 0; j < target.size(); j++) {
                if (!source.get(i + j)
                        .equals(target.get(j))) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // Logging
    // =========================================================

    private void logCandidates(
            List<CandidateStats> candidates) {
        log.info(
                candidates.stream()
                        .map(candidate -> String.format(
                                Locale.ROOT,
                                "%s [score=%.2f, freq=%d, chunks=%d, ngram=%d, pos=%.2f]",
                                candidate.getTerm(),
                                score(candidate),
                                candidate.getFrequency(),
                                candidate.getChunkFrequency(),
                                candidate.getNgramSize(),
                                candidate.getPosQuality()))
                        .collect(
                                Collectors.joining(
                                        ", ",
                                        "Top candidates: ",
                                        "")));
    }
}
