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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.exceptions.KeywordException;
import com.fmaupin.keyword.helper.DiskCache;
import com.fmaupin.keyword.model.ProcessingContext;
import com.fmaupin.keyword.pipeline.PipelineStep;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * This class is responsible for generating embeddings for the candidate
 * keywords extracted from the text content of a document.
 */
@Component
@Order(4)
@Slf4j
public class EmbeddingStep implements PipelineStep<ProcessingContext, ProcessingContext> {

    private static final String OLLAMA_URL = "http://localhost:11434/api/embed";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private final DiskCache cache;

    private static final int BATCH_SIZE = 8;

    public EmbeddingStep() {
        try {
            this.cache = new DiskCache();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize DiskCache", e);
        }
    }

    @Override
    public ProcessingContext process(ProcessingContext ctx) throws KeywordException {
        ctx.getBenchmark().start();

        List<String> chunks = ctx.getCandidates();

        // cache lookup
        Map<String, float[]> embeddingsMap = new HashMap<>();
        List<String> toEmbed = new ArrayList<>();

        for (String chunk : chunks) {
            try {
                String key = DiskCache.hash(chunk);

                if (cache.exists(key)) {
                    embeddingsMap.put(chunk, cache.load(key));
                } else {
                    toEmbed.add(chunk);
                }
            } catch (Exception e) {
                throw new KeywordException("Cache error", e);
            }
        }

        log.info("EmbeddingStep: {} cached / {} to compute", embeddingsMap.size(), toEmbed.size());

        // batch embedding
        List<List<String>> batches = partition(toEmbed, BATCH_SIZE);

        for (List<String> batch : batches) {
            List<float[]> vectors = embedBatch(batch);

            for (int i = 0; i < batch.size(); i++) {
                String text = batch.get(i);
                float[] vector = vectors.get(i);

                try {
                    String key = DiskCache.hash(text);
                    cache.save(key, vector);
                } catch (Exception e) {
                    log.warn("Cache save failed", e);
                }

                embeddingsMap.put(text, vector);
            }
        }

        // restore original order
        List<float[]> ordered = chunks.stream()
                .map(embeddingsMap::get)
                .toList();

        ctx.setCandidateEmbeddings(ordered);

        ctx.getBenchmark().end("Embeddings");
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = null;

        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", "bge-m3",
                    "input", texts));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Map<?, ?> json = mapper.readValue(response.body(), Map.class);
            List<?> embeddings = (List<?>) json.get("embeddings");

            if (embeddings == null) {
                throw new IllegalStateException(
                        "Unexpected Ollama response, no 'embeddings' field : " + response.body());
            }

            result = new ArrayList<>();

            for (Object o : embeddings) {
                List<Double> raw = (List<Double>) o;
                float[] vector = new float[raw.size()];
                for (int i = 0; i < raw.size(); i++) {
                    vector[i] = raw.get(i).floatValue();
                }
                result.add(vector);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Batch embedding interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Batch embedding failed", e);
        }

        return result;
    }

    private List<List<String>> partition(List<String> list, int size) {
        List<List<String>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }
}
