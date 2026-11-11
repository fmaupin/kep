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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.enumeration.ChunkStrategyTypeEnum;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SectionChunkStrategy implements ChunkStrategy {

    /**
     * Pattern to detect section markers with:
     * - Markdown titles (# to ###)
     * - Simple and hierarchical numbering (1., 1.1., etc.)
     * - Capital letters with period or parenthesis (A., B.), (A), etc.)
     * - Bulleted lists (* or -)
     * - Roman numbered sections (I., II., etc.)
     * The pattern is limited to 3 depth levels to avoid problems with
     * performance
     */
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "(?m)^\\s*" + // Start of line + optional spaces
                    "(" +
                    "#{1,3}\\s+|" + // Markdown titles
                    "\\d+(?:\\.\\d+){0,2}[.)]?\\s+|" + // simple or hierarchical numbers
                    "[A-Z][.)]\\s+|" + // capital letters
                    "[*-]\\s+|" + // bullet points
                    "(?:I{1,3}|IV|VI{0,3})\\.\\s+" + // Roman numerals
                    ")");

    private static final String PARAGRAPH_SEPARATOR = "\\n\\s*\\n";
    private static final String LINE_SEPARATOR = "\n";
    private static final String DOUBLE_LINE_SEPARATOR = "\n\n";

    @Value("${chunk.analysis.section.max-size:3000}")
    private int maxSectionSize;

    @Value("${chunk.analysis.section.min-size:200}")
    private int minSectionSize;

    @Override
    public List<String> split(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> chunks = new ArrayList<>();
        Matcher matcher = SECTION_PATTERN.matcher(content);

        int lastIndex = 0;
        StringBuilder currentSection = new StringBuilder();

        while (matcher.find()) {
            int start = matcher.start();
            String header = matcher.group().trim();

            // previous section
            if (start > lastIndex) {
                String sectionContent = content.substring(lastIndex, start).trim();
                if (!sectionContent.isEmpty()) {
                    processSection(sectionContent, currentSection, chunks, null);
                }
            }

            // add header to the start of the new chunk
            processSection("", currentSection, chunks, header);

            lastIndex = start;
        }

        // add the remaining content
        if (lastIndex < content.length()) {
            String remaining = content.substring(lastIndex).trim();
            if (!remaining.isEmpty()) {
                processSection(remaining, currentSection, chunks, null);
            }
        }

        log.info("Split content into {} section-based chunks", chunks.size());
        return chunks;
    }

    private void processSection(String content, StringBuilder currentSection, List<String> chunks, String header) {
        // add header if available
        if (header != null) {
            // add header to the start of the new chunk
            currentSection.append(header).append(LINE_SEPARATOR);
        }

        if (content.length() <= maxSectionSize) {
            processSmallSection(content, currentSection, chunks);
        } else {
            processLargeSection(content, currentSection, chunks);
        }
    }

    private void processSmallSection(String content, StringBuilder currentSection, List<String> chunks) {
        if (!currentSection.isEmpty()) {
            currentSection.append(LINE_SEPARATOR);
        }
        currentSection.append(content);

        if (currentSection.length() >= minSectionSize) {
            chunks.add(currentSection.toString().trim());
            currentSection.setLength(0);
        }
    }

    private void processLargeSection(String content, StringBuilder currentSection, List<String> chunks) {
        if (!currentSection.isEmpty()) {
            chunks.add(currentSection.toString().trim());
            currentSection.setLength(0);
        }

        chunks.addAll(splitLargeSection(content));
    }

    private List<String> splitLargeSection(String section) {
        List<String> subChunks = new ArrayList<>();
        int optimalSize = maxSectionSize / 2; // Taille cible pour les sous-sections

        String[] paragraphs = section.split(PARAGRAPH_SEPARATOR);
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (shouldStartNewChunk(currentChunk, paragraph, optimalSize)) {
                subChunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }

            appendParagraph(currentChunk, paragraph);
        }

        // add last sub chunk
        if (!currentChunk.isEmpty()) {
            subChunks.add(currentChunk.toString().trim());
        }

        return subChunks;
    }

    private boolean shouldStartNewChunk(StringBuilder currentChunk, String paragraph, int optimalSize) {
        return !currentChunk.isEmpty() &&
                currentChunk.length() + paragraph.length() > optimalSize &&
                currentChunk.length() >= minSectionSize;
    }

    private void appendParagraph(StringBuilder chunk, String paragraph) {
        if (!chunk.isEmpty()) {
            chunk.append(DOUBLE_LINE_SEPARATOR);
        }
        chunk.append(paragraph);
    }

    @Override
    public ChunkStrategyTypeEnum getStrategyType() {
        return ChunkStrategyTypeEnum.SECTION;
    }
}
