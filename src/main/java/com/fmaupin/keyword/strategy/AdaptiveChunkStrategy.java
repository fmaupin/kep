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

package com.fmaupin.keyword.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.enumeration.ChunkStrategyTypeEnum;
import com.fmaupin.keyword.model.TextAnalysisResult;
import com.fmaupin.keyword.service.TextAnalysisService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AdaptiveChunkStrategy implements ChunkStrategy {

    private final TextAnalysisService textAnalysisService;
    private final ParagraphChunkStrategy paragraphStrategy;
    private final FixedSizeChunkStrategy fixedSizeStrategy;

    public AdaptiveChunkStrategy(
            TextAnalysisService textAnalysisService,
            ParagraphChunkStrategy paragraphStrategy,
            FixedSizeChunkStrategy fixedSizeStrategy) {
        this.textAnalysisService = textAnalysisService;
        this.paragraphStrategy = paragraphStrategy;
        this.fixedSizeStrategy = fixedSizeStrategy;
    }

    @Value("${chunk.adaptive.min-size:500}")
    private int minSize;

    @Value("${chunk.adaptive.max-size:2000}")
    private int maxSize;

    @Override
    public List<String> split(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        TextAnalysisResult analysis = textAnalysisService.analyzeText(content);
        ChunkStrategy selectedStrategy;
        int targetSize;

        if (analysis.isHasStructuredFormat()) {
            log.info("Using paragraph strategy due to structured content");
            selectedStrategy = paragraphStrategy;
        } else {
            log.info("Using fixed size strategy with adaptive size");
            selectedStrategy = fixedSizeStrategy;

            if (analysis.isDenseText()) {
                targetSize = Math.min(maxSize, (int) (minSize * 1.5));
                fixedSizeStrategy.setChunkSize(targetSize);

                if (log.isDebugEnabled()) {
                    log.debug("Dense text detected, using larger chunk size: {}", targetSize);
                }
            } else {
                targetSize = Math.max(minSize, (int) (maxSize * 0.7));
                fixedSizeStrategy.setChunkSize(targetSize);

                if (log.isDebugEnabled()) {
                    log.debug("Light text detected, using smaller chunk size: {}", targetSize);
                }
            }
        }

        return selectedStrategy.split(content);
    }

    @Override
    public ChunkStrategyTypeEnum getStrategyType() {
        return ChunkStrategyTypeEnum.ADAPTIVE;
    }
}
