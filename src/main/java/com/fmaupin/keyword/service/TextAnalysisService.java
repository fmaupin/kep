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

import java.util.Arrays;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fmaupin.keyword.configuration.ChunkAnalysisProperties;
import com.fmaupin.keyword.model.DocumentAnalysisResult;
import com.fmaupin.keyword.model.TextAnalysisResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TextAnalysisService {

        private final ChunkAnalysisProperties properties;
        private final DocumentAnalysisService documentAnalysisService;

        public TextAnalysisResult analyzeText(String content) {
                if (content == null || content.isEmpty()) {
                        return TextAnalysisResult.empty();
                }

                TextAnalysisResult result = new TextAnalysisResult();

                analyzeStructure(content, result);

                analyzeDensity(content, result);

                DocumentAnalysisResult docResult = documentAnalysisService.analyzeDocument(content);
                result.setHasSpecialFormatting(docResult.isHasSpecialFormatting());
                result.setHasTableContent(docResult.isTableContent());
                result.setHasImageContent(docResult.isImageContent());

                log.info("Text analysis results: {}", result);
                return result;
        }

        private void analyzeStructure(String content, TextAnalysisResult result) {
                String[] lines = content.split("\\n");
                Pattern headingPattern = Pattern.compile(properties.getStructure().getHeadingPattern());

                long headingLines = Arrays.stream(lines)
                                .filter(line -> !line.trim().isEmpty())
                                .filter(line -> headingPattern.matcher(line.trim()).matches())
                                .count();

                long listItemLines = Arrays.stream(lines)
                                .filter(line -> !line.trim().isEmpty())
                                .filter(line -> line.trim().matches("^[\\-\\*\\d]+[.)\\s].*$"))
                                .count();

                double listItemRatio = (double) listItemLines / lines.length;
                double headingRatio = (double) headingLines / lines.length;

                result.setHeadingRatio(headingRatio);
                result.setListItemRatio(listItemRatio);
                result.setHasStructuredFormat(
                                headingRatio > 0.05 || listItemRatio > properties.getStructure().getListItemRatio());
        }

        @SuppressWarnings("null")
        private void analyzeDensity(String content, TextAnalysisResult result) {
                String[] lines = content.split("\\n");
                String[] words = content.split("\\s+");

                double avgWordsPerLine = (double) words.length / lines.length;
                result.setWordDensity(avgWordsPerLine);

                long whitespaceCount = content.chars().filter(Character::isWhitespace).count();
                double whitespaceRatio = (double) whitespaceCount / content.length();
                result.setWhitespaceRatio(whitespaceRatio);

                double averageLineLength = Arrays.stream(lines)
                                .mapToInt(String::length)
                                .average()
                                .orElse(0.0);

                double variance = Arrays.stream(lines)
                                .mapToDouble(line -> Math.pow(line.length() - averageLineLength, 2))
                                .average()
                                .orElse(0.0);

                result.setLineLengthVariance(variance);
                result.setAverageLineLength(averageLineLength);

                boolean isDense = avgWordsPerLine > properties.getDensity().getWordDensity() &&
                                whitespaceRatio < properties.getDensity().getWhitespaceRatio() &&
                                variance < properties.getDensity().getVarianceThreshold();

                result.setDenseText(isDense);
        }
}
