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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fmaupin.keyword.exceptions.KeywordException;
import com.fmaupin.keyword.model.ProcessingContext;
import com.fmaupin.keyword.pipeline.PipelineStep;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class is responsible for refining the list of candidate keywords
 * extracted from a document using a large language model (LLM).
 */
@Component
@Order(6)
@Slf4j
public class LlmRefinementStep implements PipelineStep<ProcessingContext, ProcessingContext> {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";

    private final HttpClient client = HttpClient.newBuilder()
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("null")
    @Override
    public ProcessingContext process(ProcessingContext ctx) throws KeywordException {
        if (!"quality".equalsIgnoreCase(ctx.getMode())) {
            return ctx;
        }

        ctx.getBenchmark().start();

        List<String> top = ctx.getKeywords()
                .stream()
                .limit(10)
                .toList();

        String prompt = """
                Here is a list of candidate keywords automatically extracted from a document.

                Select the 10 most representative keywords according to these rules:
                - Remove redundant or synonymous keywords; keep the most representative one.
                - Remove keywords that are too generic or not informative enough.
                - Prefer specific and meaningful keywords over broad terms.
                - Do not invent new keywords.
                - Do not rephrase, translate, or correct spelling.
                - Each selected keyword MUST be copied EXACTLY as it appears in the input list.

                IMPORTANT:
                - Return exactly 10 keywords if at least 10 valid candidates are provided.
                - Preserve the original spelling, wording, and capitalization of every selected keyword.
                - Return fewer than 10 only if fewer than 10 valid and meaningful keywords are available.

                Keywords:
                %s

                Return ONLY a valid JSON array of strings.
                No explanation, no text, no markdown.

                Example:
                ["keyword1", "keyword2", "keyword3"]
                """.formatted(top);

        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", "qwen2.5:7b",
                    "prompt", prompt,
                    "stream", false));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new KeywordException("Ollama has returned the status " + response.statusCode()
                        + " : " + response.body());
            }

            Map<?, ?> json = mapper.readValue(response.body(), Map.class);
            String raw = (String) json.get("response");

            if (raw == null) {
                throw new KeywordException("No 'response' field in Ollama response : " + response.body());
            }

            String cleanedJson = extractJsonArray(raw);

            List<String> refined = mapper.readValue(cleanedJson, new TypeReference<List<String>>() {
            });

            Set<String> originalWords = top.stream()
                    .flatMap(k -> Arrays.stream(k.split("\\s+")))
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            List<String> validated = refined.stream()
                    .filter(k -> isComposedOfOriginalWords(k, originalWords))
                    .toList();

            ctx.setKeywords(validated.isEmpty() ? top : validated);
        } catch (IOException e) {
            throw new KeywordException("LLM refinement failed", e);
        } catch (InterruptedException e) {
            log.warn("LLM refinement interrupted", e);
            Thread.currentThread().interrupt();
        }

        ctx.getBenchmark().end("Llm refinement");

        return ctx;
    }

    private String extractJsonArray(String input) throws KeywordException {
        int start = input.indexOf('[');
        int end = input.lastIndexOf(']');

        if (start != -1 && end != -1 && end > start) {
            return input.substring(start, end + 1);
        }

        throw new KeywordException("No JSON array found in LLM response: " + input);
    }

    private boolean isComposedOfOriginalWords(String keyword, Set<String> originalWords) {
        String[] parts = keyword.toLowerCase().split("\\s+");
        for (String part : parts) {
            if (!originalWords.contains(part)) {
                return false;
            }
        }
        return true;
    }
}
