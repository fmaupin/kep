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
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.enumeration.ChunkStrategyTypeEnum;

@Component
@SuppressWarnings("null")
public class ParagraphChunkStrategy implements ChunkStrategy {

    private static final String PARAGRAPH_SEPARATOR = "\\n\\s*\\n";

    @Value("${chunk.min-paragraph-size:100}")
    private int minParagraphSize;

    @Value("${chunk.max-paragraphs-per-chunk:5}")
    private int maxParagraphsPerChunk;

    @Override
    public List<String> split(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        return createChunksFromParagraphs(splitIntoParagraphs(content));
    }

    private List<String> splitIntoParagraphs(String content) {
        return Arrays.stream(content.split(PARAGRAPH_SEPARATOR))
                .map(String::trim)
                .filter(this::isValidParagraphSize)
                .toList();
    }

    private boolean isValidParagraphSize(String paragraph) {
        return paragraph.length() >= minParagraphSize;
    }

    private List<String> createChunksFromParagraphs(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int paragraphCount = 0;

        for (String paragraph : paragraphs) {
            if (shouldStartNewChunk(paragraphCount)) {
                addChunkIfNotEmpty(chunks, currentChunk);
                currentChunk = new StringBuilder();
                paragraphCount = 0;
            }

            appendParagraph(currentChunk, paragraph);
            paragraphCount++;
        }

        addChunkIfNotEmpty(chunks, currentChunk);
        return chunks;
    }

    private boolean shouldStartNewChunk(int paragraphCount) {
        return paragraphCount >= maxParagraphsPerChunk;
    }

    private void appendParagraph(StringBuilder chunk, String paragraph) {
        chunk.append(paragraph).append("\n\n");
    }

    private void addChunkIfNotEmpty(List<String> chunks, StringBuilder currentChunk) {
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }
    }

    @Override
    public ChunkStrategyTypeEnum getStrategyType() {
        return ChunkStrategyTypeEnum.PARAGRAPH;
    }
}
