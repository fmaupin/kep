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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.enumeration.ChunkStrategyTypeEnum;
import com.fmaupin.keyword.exceptions.KeywordException;
import com.fmaupin.keyword.model.ContentAnalysis;
import com.fmaupin.keyword.model.ProcessingContext;
import com.fmaupin.keyword.model.TextAnalysisResult;
import com.fmaupin.keyword.pipeline.PipelineStep;
import com.fmaupin.keyword.service.TextAnalysisService;
import com.fmaupin.keyword.strategy.ChunkStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for analyzing the content of a text and determining
 * the most appropriate chunking strategy to apply.
 * 
 * It implements the `PipelineStep` interface, allowing it to be used as a step
 * in a processing pipeline.
 * 
 * It uses various chunking strategies based on the analysis of the text, such
 * as fixed-size, section-based, paragraph-based, or adaptive chunking.
 * 
 * It also logs relevant information about the analysis and the chosen strategy.
 */
@Component
@Order(2)
@Slf4j
@SuppressWarnings("null")
public class ChunkingStep implements PipelineStep<ProcessingContext, ProcessingContext> {

        @Value("${chunk.analysis.min-content-size:1000}")
        private int minContentSize;

        @Value("${chunk.analysis.paragraph-detection-ratio:0.1}")
        private double paragraphDetectionRatio;

        private final Map<ChunkStrategyTypeEnum, ChunkStrategy> strategyMap;

        private final TextAnalysisService textAnalysisService;

        public ChunkingStep(List<ChunkStrategy> strategies, TextAnalysisService textAnalysisService) {
                this.strategyMap = strategies.stream()
                                .collect(Collectors.toMap(ChunkStrategy::getStrategyType, Function.identity()));
                this.textAnalysisService = textAnalysisService;
        }

        @Override
        public ProcessingContext process(ProcessingContext ctx) throws KeywordException {
                ctx.getBenchmark().start();

                ChunkStrategyTypeEnum strategy = analyzeContent(ctx.getText()).getRecommendedStrategy();
                log.info("Selected chunking strategy: {}", strategy);

                ctx.setChunks(createChunks(ctx.getText(), strategy));

                ctx.getBenchmark().end("Chunking");

                return ctx;
        }

        private ContentAnalysis analyzeContent(String content) {
                // If the content is too small or null, we use the default policy
                if (content == null || content.length() < minContentSize) {
                        log.info("Content too small ({} chars), minimum required: {} chars",
                                        content != null ? content.length() : 0, minContentSize);

                        return ContentAnalysis.builder()
                                        .recommendedStrategy(ChunkStrategyTypeEnum.FIXED_SIZE)
                                        .reason("Content too small for detailed analysis")
                                        .build();
                }

                TextAnalysisResult analysis = textAnalysisService.analyzeText(content);

                // Use the SECTION policy if the text is structured with headings
                if (analysis.getHeadingRatio() > 0.05) {
                        log.info("Detected section-based structure with heading ratio: {}",
                                        String.format("%.2f", analysis.getHeadingRatio()));

                        return ContentAnalysis.builder()
                                        .textAnalysis(analysis)
                                        .recommendedStrategy(ChunkStrategyTypeEnum.SECTION)
                                        .reason("The text is structured with sections and headings")
                                        .build();
                }

                // Use the PARAGRAPH strategy in the case of structured text
                if (analysis.isHasStructuredFormat()) {
                        log.info("Detected structured format with list ratio: {}",
                                        String.format("%.2f", analysis.getListItemRatio()));

                        return ContentAnalysis.builder()
                                        .textAnalysis(analysis)
                                        .recommendedStrategy(ChunkStrategyTypeEnum.PARAGRAPH)
                                        .reason("The text presents a clear structure with lists")
                                        .build();
                }

                // Use the ADAPTIVE strategy for dense text
                if (analysis.isDenseText()) {
                        log.info("Detected dense text with word density: {} and whitespace ratio: {}",
                                        String.format("%.2f", analysis.getWordDensity()),
                                        String.format("%.2f", analysis.getWhitespaceRatio()));

                        return ContentAnalysis.builder()
                                        .textAnalysis(analysis)
                                        .recommendedStrategy(ChunkStrategyTypeEnum.ADAPTIVE)
                                        .reason("The text is dense, adaptive chunking recommended")
                                        .build();
                }

                log.info("No specific structure detected, using default fixed-size strategy");

                return ContentAnalysis.builder()
                                .textAnalysis(analysis)
                                .recommendedStrategy(ChunkStrategyTypeEnum.FIXED_SIZE)
                                .reason("No specific structure detected")
                                .build();
        }

        public List<String> createChunks(String content, ChunkStrategyTypeEnum strategyType) {
                ChunkStrategy strategy = strategyMap.get(strategyType);
                if (strategy == null) {
                        String availableStrategies = strategyMap.keySet().stream()
                                        .map(Enum::name)
                                        .collect(Collectors.joining(", "));
                        throw new IllegalArgumentException(
                                        "Unknown strategy: " + strategyType + ". Available strategies: "
                                                        + availableStrategies);
                }

                log.info("Applying chunking strategy: {}", strategyType);

                List<String> chunks = strategy.split(content);
                log.info("Content split into {} chunks", chunks.size());

                return chunks;
        }
}
