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

import com.fmaupin.keyword.model.ProcessingContext;
import com.fmaupin.keyword.pipeline.PipelineStep;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * This class is responsible for selecting the most relevant keywords from a
 * list of candidate keywords based on their semantic and lexical relevance to
 * the document content.
 */
@Component
@Order(5)
@Slf4j
public class KeywordSelectionStep implements PipelineStep<ProcessingContext, ProcessingContext> {

        /**
         * Weight of semantic relevance.
         */
        private static final double SEMANTIC_WEIGHT = 0.65;

        /**
         * Weight of lexical relevance.
         */
        private static final double LEXICAL_WEIGHT = 0.35;

        /**
         * MMR relevance/diversity balance.
         */
        private static final double MMR_LAMBDA = 0.70;

        /**
         * minimum hybrid relevance before the MMR.
         */
        private static final double MIN_HYBRID_RELEVANCE = 0.45;

        /**
         * Number of final keywords.
         */
        private static final int MAX_KEYWORDS = 20;

        @SuppressWarnings("null")
        public ProcessingContext process(ProcessingContext ctx) {
                ctx.getBenchmark().start();

                List<String> candidates = ctx.getCandidates();
                List<float[]> embeddings = ctx.getCandidateEmbeddings();

                if (candidates == null
                                || embeddings == null
                                || candidates.isEmpty()
                                || embeddings.isEmpty()) {

                        log.warn("No candidates or embeddings available for keyword selection");

                        ctx.setKeywords(List.of());
                        ctx.getBenchmark().end("Keyword selection");

                        return ctx;
                }

                if (embeddings == null
                                || embeddings.size() != candidates.size()) {
                        throw new IllegalStateException(
                                        "Candidate/embedding size mismatch: candidates="
                                                        + candidates.size()
                                                        + ", embeddings="
                                                        + (embeddings == null
                                                                        ? 0
                                                                        : embeddings.size()));
                }

                /*
                 * ---------------------------------------------------------
                 * 1. Document embedding
                 * ---------------------------------------------------------
                 */
                float[] documentEmbedding = mean(embeddings);

                /*
                 * ---------------------------------------------------------
                 * 2. Normalize lexical scores
                 * ---------------------------------------------------------
                 */
                Map<String, Double> lexicalScores = normalizeLexicalScores(
                                ctx.getCandidateScores(),
                                candidates);

                /*
                 * ---------------------------------------------------------
                 * 3. Build candidate pool
                 * ---------------------------------------------------------
                 */
                List<Scored> pool = new ArrayList<>();

                for (int i = 0; i < candidates.size(); i++) {
                        String candidate = candidates.get(i);

                        float[] embedding = embeddings.get(i);

                        double semanticRelevance = cosine(
                                        documentEmbedding,
                                        embedding);

                        double lexicalRelevance = lexicalScores.getOrDefault(
                                        normalize(candidate),
                                        0.0);

                        double hybridRelevance = SEMANTIC_WEIGHT * semanticRelevance
                                        + LEXICAL_WEIGHT * lexicalRelevance;

                        pool.add(
                                        new Scored(
                                                        candidate,
                                                        semanticRelevance,
                                                        lexicalRelevance,
                                                        hybridRelevance,
                                                        embedding));
                }

                /*
                 * ---------------------------------------------------------
                 * 4. Initial ranking
                 * ---------------------------------------------------------
                 *
                 * The hybrid relevance is used here rather than
                 * semantic similarity alone.
                 */
                pool.sort(
                                Comparator
                                                .comparingDouble(
                                                                Scored::getHybridRelevance)
                                                .reversed());

                // ----------------------------------------------------
                // 5. Filtering before MMR
                // ----------------------------------------------------

                List<Scored> filteredPool = pool.stream()
                                .filter(s -> s.getHybridRelevance() >= MIN_HYBRID_RELEVANCE)
                                .toList();

                /*
                 * ---------------------------------------------------------
                 * 6. MMR selection
                 * ---------------------------------------------------------
                 */
                List<String> selected = mmr(
                                filteredPool,
                                MAX_KEYWORDS);

                ctx.setKeywords(selected);

                log.info(
                                "Selected {} keywords",
                                selected.size());

                log.info(
                                "Selected keywords: {}",
                                selected);

                ctx.getBenchmark().end("Keyword selection");

                return ctx;
        }

        @Getter
        static class Scored {
                private final String term;

                private final double semanticRelevance;

                private final double lexicalRelevance;

                private final double hybridRelevance;

                private final float[] embedding;

                Scored(
                                String term,
                                double semanticRelevance,
                                double lexicalRelevance,
                                double hybridRelevance,
                                float[] embedding) {

                        this.term = term;
                        this.semanticRelevance = semanticRelevance;
                        this.lexicalRelevance = lexicalRelevance;
                        this.hybridRelevance = hybridRelevance;
                        this.embedding = embedding;
                }
        }

        // =========================================================
        // Document embedding
        // =========================================================

        float[] mean(
                        List<float[]> vectors) {

                if (vectors == null
                                || vectors.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "Cannot calculate mean of empty vectors");
                }

                int dimension = vectors.get(0).length;

                float[] mean = new float[dimension];

                for (float[] vector : vectors) {
                        if (vector.length != dimension) {
                                throw new IllegalArgumentException(
                                                "Embedding dimension mismatch");
                        }

                        for (int i = 0; i < dimension; i++) {
                                mean[i] += vector[i];
                        }
                }

                for (int i = 0; i < dimension; i++) {
                        mean[i] /= vectors.size();
                }

                return mean;
        }

        // =========================================================
        // Lexical score normalization
        // =========================================================

        private Map<String, Double> normalizeLexicalScores(
                        Map<String, Double> scores,
                        List<String> candidates) {
                if (scores == null
                                || scores.isEmpty()) {
                        log.warn(
                                        "No lexical candidate scores available. "
                                                        + "Lexical relevance will be ignored.");

                        return Collections.emptyMap();
                }

                double max = candidates.stream()
                                .mapToDouble(candidate -> scores.getOrDefault(
                                                normalize(candidate),
                                                0.0))
                                .max()
                                .orElse(0.0);

                if (max <= 0.0) {
                        return Collections.emptyMap();
                }

                Map<String, Double> normalized = new HashMap<>();

                for (String candidate : candidates) {
                        String key = normalize(candidate);

                        double score = scores.getOrDefault(
                                        key,
                                        0.0);

                        /*
                         * Min-max is deliberately not used here.
                         *
                         * The lexical scores are positive and naturally
                         * comparable relative to the maximum candidate.
                         */
                        normalized.put(
                                        key,
                                        score / max);
                }

                return normalized;
        }

        // =========================================================
        // Cosine similarity
        // =========================================================

        double cosine(
                        float[] a,
                        float[] b) {
                if (a.length != b.length) {
                        throw new IllegalArgumentException(
                                        "Embedding dimension mismatch");
                }

                double dot = 0.0;

                double normA = 0.0;

                double normB = 0.0;

                for (int i = 0; i < a.length; i++) {
                        dot += a[i] * b[i];

                        normA += a[i] * a[i];

                        normB += b[i] * b[i];
                }

                if (normA == 0.0
                                || normB == 0.0) {
                        return 0.0;
                }

                return dot /
                                (Math.sqrt(normA)
                                                * Math.sqrt(normB));
        }

        // =========================================================
        // MMR
        // =========================================================

        List<String> mmr(
                        List<Scored> pool,
                        int k) {
                List<String> selected = new ArrayList<>();

                Set<Integer> used = new HashSet<>();

                while (selected.size() < k
                                && selected.size() < pool.size()) {
                        double bestScore = Double.NEGATIVE_INFINITY;

                        int bestIndex = -1;

                        double bestDiversity = 0.0;

                        for (int i = 0; i < pool.size(); i++) {
                                if (used.contains(i)) {
                                        continue;
                                }

                                Scored candidate = pool.get(i);

                                /*
                                 * Maximum similarity with an already selected
                                 * keyword.
                                 *
                                 * This is the standard MMR redundancy term.
                                 */
                                double diversity = 0.0;

                                for (int selectedIndex : used) {
                                        double similarity = cosine(
                                                        candidate.getEmbedding(),
                                                        pool.get(selectedIndex)
                                                                        .getEmbedding());

                                        diversity = Math.max(
                                                        diversity,
                                                        similarity);
                                }

                                double mmrScore = MMR_LAMBDA
                                                * candidate.getHybridRelevance()
                                                - (1.0 - MMR_LAMBDA)
                                                                * diversity;

                                if (mmrScore > bestScore) {
                                        bestScore = mmrScore;

                                        bestIndex = i;

                                        bestDiversity = diversity;
                                }
                        }

                        if (bestIndex < 0) {
                                break;
                        }

                        Scored selectedCandidate = pool.get(bestIndex);

                        used.add(bestIndex);

                        selected.add(
                                        selectedCandidate.getTerm());

                        log.info(
                                        "MMR selected: {} [semantic={}, lexical={}, hybrid={}, diversity={}, mmr={}]",
                                        selectedCandidate.getTerm(),
                                        selectedCandidate.getSemanticRelevance(),
                                        selectedCandidate.getLexicalRelevance(),
                                        selectedCandidate.getHybridRelevance(),
                                        bestDiversity,
                                        bestScore);
                }

                return selected;
        }

        // =========================================================
        // Normalization
        // =========================================================

        private String normalize(
                        String value) {
                return value
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("\\s+", " ");
        }
}
