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

import org.springframework.stereotype.Service;

import com.fmaupin.keyword.configuration.ChunkAnalysisProperties;
import com.fmaupin.keyword.model.DocumentAnalysisResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentAnalysisService {

    private final ChunkAnalysisProperties properties;

    public DocumentAnalysisResult analyzeDocument(String content) {
        if (content == null || content.isEmpty()) {
            return DocumentAnalysisResult.empty();
        }

        DocumentAnalysisResult result = new DocumentAnalysisResult();

        // Basic table detection
        if (properties.getDocument().isFormatDetection()) {
            detectTables(content, result);
        }

        analyzeFormatting(content, result);

        log.info("Document analysis results: {}", result);

        return result;
    }

    private void detectTables(String content, DocumentAnalysisResult result) {
        String[] lines = content.split("\\n");
        long tableLines = 0;

        for (String line : lines) {
            if (isTableRow(line)) {
                tableLines++;
            }
        }

        double tableRatio = (double) tableLines / lines.length;
        result.setTableContent(tableRatio >= properties.getDocument().getTableThreshold());
        result.setTableRatio(tableRatio);
    }

    private boolean isTableRow(String line) {
        // Basic table row detection (at least 3 separators)
        String trimmed = line.trim();
        if (trimmed.isEmpty())
            return false;

        int tabCount = trimmed.split("\\t").length - 1;
        int pipeCount = trimmed.split("\\|").length - 1;
        int commaCount = trimmed.split(",").length - 1;

        return tabCount >= 2 || pipeCount >= 2 || commaCount >= 2;
    }

    private void analyzeFormatting(String content, DocumentAnalysisResult result) {
        String[] lines = content.split("\\n");
        int totalLines = lines.length;
        int formattedLines = 0;
        int potentialImageLines = 0;

        for (String line : lines) {
            if (containsFormattingMarkers(line)) {
                formattedLines++;
            }
            if (containsImageMarkers(line)) {
                potentialImageLines++;
            }
        }

        double formattingRatio = (double) formattedLines / totalLines;
        double imageRatio = (double) potentialImageLines / totalLines;

        result.setHasSpecialFormatting(formattingRatio > properties.getDocument().getFontSizeVariations());
        result.setImageContent(imageRatio > properties.getDocument().getImageTextRatio());
        result.setFormattingRatio(formattingRatio);
        result.setImageRatio(imageRatio);
    }

    private boolean containsFormattingMarkers(String line) {
        return line.chars()
                .anyMatch(ch -> "<>_*#[]`".indexOf(ch) >= 0);
    }

    private boolean containsImageMarkers(String line) {
        return line.contains(".png") || line.contains(".jpg") || line.contains(".jpeg") ||
                line.contains(".gif") || line.contains(".bmp") || line.contains(".svg") ||
                line.contains("<img") ||
                line.contains("![");
    }
}
