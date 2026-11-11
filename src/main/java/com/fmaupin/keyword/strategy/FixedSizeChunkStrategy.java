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

@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {

    @Value("${chunk.size:1000}")
    private int defaultChunkSize;

    private int currentChunkSize;

    public void setChunkSize(int size) {
        this.currentChunkSize = size;
    }

    private int getChunkSize() {
        return currentChunkSize > 0 ? currentChunkSize : defaultChunkSize;
    }

    @Override
    public List<String> split(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return splitContent(content);
    }

    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        int startIndex = 0;

        while (startIndex < content.length()) {
            int endIndex = calculateChunkEndIndex(content, startIndex);
            chunks.add(content.substring(startIndex, endIndex).trim());
            startIndex = endIndex;
        }

        return chunks;
    }

    private int calculateChunkEndIndex(String content, int startIndex) {
        int proposedEnd = Math.min(startIndex + getChunkSize(), content.length());

        if (proposedEnd < content.length()) {
            int lastSpace = content.lastIndexOf(' ', proposedEnd);
            if (lastSpace > startIndex) {
                return lastSpace;
            }
        }

        return proposedEnd;
    }

    @Override
    public ChunkStrategyTypeEnum getStrategyType() {
        return ChunkStrategyTypeEnum.FIXED_SIZE;
    }
}
