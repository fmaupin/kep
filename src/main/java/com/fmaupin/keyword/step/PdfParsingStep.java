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

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.exceptions.KeywordException;
import com.fmaupin.keyword.model.ProcessingContext;
import com.fmaupin.keyword.pipeline.PipelineStep;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.ByteArrayInputStream;

/**
 * This class is responsible for parsing PDF documents and extracting their text
 * content.
 * 
 * It implements the `PipelineStep` interface, allowing it to be used as a step
 * in a processing pipeline.
 */
@Component
@Order(1)
@Slf4j
public class PdfParsingStep implements PipelineStep<ProcessingContext, ProcessingContext> {

    @Override
    public ProcessingContext process(ProcessingContext ctx) throws KeywordException {
        ctx.getBenchmark().start();

        byte[] data;
        try {
            data = ctx.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new KeywordException("Failed to read PDF input stream", e);
        }

        if (data.length == 0) {
            throw new KeywordException("Empty PDF input stream");
        }

        String content;
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(data));
                PdfDocument doc = new PdfDocument(reader)) {

            content = extractText(doc);
        } catch (IOException e) {
            throw new KeywordException("Failed to parse PDF", e);
        }

        content = normalizeExtractedText(content);

        ctx.setText(content);

        ctx.setLanguage(detectLanguage(content));
        log.info("Detected language: " + ctx.getLanguage());

        ctx.getBenchmark().end("PDF Parsing");

        return ctx;
    }

    private String extractText(PdfDocument doc) {
        StringBuilder text = new StringBuilder();
        int numberOfPages = doc.getNumberOfPages();

        for (int pageNum = 1; pageNum <= numberOfPages; pageNum++) {
            PdfPage page = doc.getPage(pageNum);
            String pageText = PdfTextExtractor.getTextFromPage(page);

            text.append(pageText).append("\n");
        }

        return text.toString();
    }

    private String normalizeExtractedText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String normalized = java.text.Normalizer.normalize(
                text,
                java.text.Normalizer.Form.NFC);

        // BOM ?
        normalized = normalized.replace("\uFEFF", "");

        // Unicode wildcard
        // → usually indicates an already corrupted extraction
        if (normalized.indexOf('\uFFFD') >= 0) {
            log.warn(
                    "PDF extraction contains replacement characters (U+FFFD)");
        }

        // Detection of frequent encoding artifacts
        if (containsEncodingArtifacts(normalized)) {
            log.warn(
                    "Possible PDF text encoding corruption detected");
        }

        // Cleaning up unnecessary Unicode spaces
        normalized = normalized
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n");

        return normalized.trim();
    }

    private boolean containsEncodingArtifacts(String text) {
        return text.contains("´┐¢")
                || text.contains("├")
                || text.contains("┐")
                || text.contains("Ã")
                || text.contains("Â")
                || text.contains("�");
    }

    private String detectLanguage(String text) {
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        LanguageResult result = detector.detect(text);

        if (result.isReasonablyCertain()) {
            return result.getLanguage();
        }

        return "en";
    }
}
