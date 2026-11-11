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

package com.fmaupin.keyword.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import org.springframework.stereotype.Service;

import com.fmaupin.keyword.enumeration.PosCategory;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PosTaggerService {

    private final Map<String, POSTaggerME> taggers;

    private static final Map<String, PosCategory> UNIVERSAL_TAGS = Map.ofEntries(
            Map.entry("NOUN", PosCategory.NOUN),
            Map.entry("PROPN", PosCategory.PROPN),
            Map.entry("ADJ", PosCategory.ADJ),
            Map.entry("VERB", PosCategory.VERB),
            Map.entry("COP", PosCategory.VERB),
            Map.entry("AUX", PosCategory.AUX),
            Map.entry("DET", PosCategory.DET),
            Map.entry("PRON", PosCategory.PRON),
            Map.entry("ADV", PosCategory.ADV),
            Map.entry("ADP", PosCategory.ADP),
            Map.entry("CONJ", PosCategory.CONJ),
            Map.entry("CCONJ", PosCategory.CONJ),
            Map.entry("SCONJ", PosCategory.CONJ),
            Map.entry("NUM", PosCategory.NUM),
            Map.entry("PUNCT", PosCategory.PUNCT),
            Map.entry("PART", PosCategory.OTHER),
            Map.entry("INTJ", PosCategory.OTHER),
            Map.entry("SYM", PosCategory.OTHER),
            Map.entry("X", PosCategory.OTHER));

    private static final Map<String, PosCategory> PENN_TAGS = Map.ofEntries(
            // Nouns
            Map.entry("NN", PosCategory.NOUN),
            Map.entry("NNS", PosCategory.NOUN),
            Map.entry("NNP", PosCategory.PROPN),
            Map.entry("NNPS", PosCategory.PROPN),

            // French TreeTagger
            Map.entry("NC", PosCategory.NOUN),
            Map.entry("NPP", PosCategory.PROPN),

            // Adjectives
            Map.entry("JJ", PosCategory.ADJ),
            Map.entry("JJR", PosCategory.ADJ),
            Map.entry("JJS", PosCategory.ADJ),

            // Verbs
            Map.entry("VB", PosCategory.VERB),
            Map.entry("VBD", PosCategory.VERB),
            Map.entry("VBG", PosCategory.VERB),
            Map.entry("VBN", PosCategory.VERB),
            Map.entry("VBP", PosCategory.VERB),
            Map.entry("VBZ", PosCategory.VERB),

            // Common French / TreeTagger verb tags
            Map.entry("V", PosCategory.VERB),
            Map.entry("VINF", PosCategory.VERB),
            Map.entry("VS", PosCategory.VERB),
            Map.entry("VPP", PosCategory.VERB),
            Map.entry("VPR", PosCategory.VERB),
            Map.entry("VIMP", PosCategory.VERB),
            Map.entry("VIND", PosCategory.VERB),
            Map.entry("VSUB", PosCategory.VERB),
            Map.entry("VCON", PosCategory.VERB),

            // Auxiliary / modal
            Map.entry("MD", PosCategory.AUX),

            // Determiners
            Map.entry("DT", PosCategory.DET),
            Map.entry("PDT", PosCategory.DET),
            Map.entry("WDT", PosCategory.DET),

            // Pronouns
            Map.entry("PRP", PosCategory.PRON),
            Map.entry("PRP$", PosCategory.PRON),
            Map.entry("WP", PosCategory.PRON),
            Map.entry("WP$", PosCategory.PRON),

            // Adverbs
            Map.entry("RB", PosCategory.ADV),
            Map.entry("RBR", PosCategory.ADV),
            Map.entry("RBS", PosCategory.ADV),
            Map.entry("WRB", PosCategory.ADV),

            // Prepositions
            Map.entry("IN", PosCategory.ADP),

            // Conjunctions
            Map.entry("CC", PosCategory.CONJ),

            // Numbers
            Map.entry("CD", PosCategory.NUM));

    public PosTaggerService() {
        try {
            this.taggers = Map.of(
                    "fr", createTagger("/fr/pos-model.bin"),
                    "en", createTagger("/en/pos-model.bin"));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to initialize POS taggers",
                    e);
        }
    }

    private POSTaggerME createTagger(String resource)
            throws IOException {
        InputStream input = PosTaggerService.class
                .getResourceAsStream(resource);

        if (input == null) {
            throw new IllegalStateException(
                    "POS model not found: " + resource);
        }

        try (input) {
            POSModel model = new POSModel(input);
            return new POSTaggerME(model);
        }
    }

    /**
     * Tags a token array using the POS model associated
     * with the requested language.
     */
    public String[] tag(
            String[] tokens,
            String language) {
        return resolveTagger(language).tag(tokens);
    }

    /**
     * Returns the normalized POS categories directly.
     *
     * Candidate extraction does not need to know which
     * tagset is used by the underlying language model.
     */
    public PosCategory[] tagCategories(
            String[] tokens,
            String language) {
        String[] tags = tag(tokens, language);

        PosCategory[] categories = new PosCategory[tags.length];

        for (int i = 0; i < tags.length; i++) {
            categories[i] = normalizeTag(tags[i]);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "POS [{}]: {}",
                    language,
                    buildPosDebug(tokens, tags, categories));
        }

        return categories;
    }

    private String buildPosDebug(
            String[] tokens,
            String[] tags,
            PosCategory[] categories) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                result.append(' ');
            }

            result.append(tokens[i])
                    .append('/')
                    .append(tags[i])
                    .append("->")
                    .append(categories[i]);
        }

        return result.toString();
    }

    private POSTaggerME resolveTagger(String language) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException(
                    "Language is required for POS tagging");
        }

        POSTaggerME tagger = taggers.get(language.toLowerCase(Locale.ROOT));

        if (tagger == null) {
            throw new IllegalArgumentException(
                    "Unsupported language for POS tagging: " + language);
        }

        return tagger;
    }

    /**
     * Converts language/model-specific POS tags into
     * a small language-independent POS taxonomy.
     *
     * Supported tag families:
     *
     * - Universal Dependencies
     * - Penn Treebank
     * - TreeTagger-style French tags
     * - common OpenNLP tags
     */
    private PosCategory normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return PosCategory.OTHER;
        }

        String p = tag.toUpperCase(Locale.ROOT);

        PosCategory universal = UNIVERSAL_TAGS.get(p);

        if (universal != null) {
            return universal;
        }

        PosCategory penn = PENN_TAGS.get(p);

        if (penn != null) {
            return penn;
        }

        return normalizeByPrefix(p);
    }

    private PosCategory normalizeByPrefix(String tag) {
        if (tag.startsWith("VB")) {
            return PosCategory.VERB;
        }

        if (tag.startsWith("NN")) {
            return PosCategory.NOUN;
        }

        if (tag.startsWith("JJ")) {
            return PosCategory.ADJ;
        }

        if (tag.startsWith("RB")) {
            return PosCategory.ADV;
        }

        return PosCategory.OTHER;
    }
}
