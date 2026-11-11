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

package com.fmaupin.keyword.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fmaupin.keyword.model.ProcessingContext;
import com.fmaupin.keyword.pipeline.Pipeline;
import com.fmaupin.keyword.service.PathValidationService;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class StartupRunner {

    @Bean
    CommandLineRunner runPipeline(Pipeline pipeline, PathValidationService pathValidationService) {
        return args -> {
            long start = System.nanoTime();

            try {
                // arguments parsing
                String mode = resolveMode(args);
                String filePath = resolveFilePath(args);

                // context initialization
                ProcessingContext context = new ProcessingContext();
                context.setMode(mode);

                if (filePath != null) {
                    Path path = Path.of(filePath);

                    validateInputFile(path, pathValidationService);

                    context.setInputStream(Files.newInputStream(path));

                    log.info("==== PDF FILE: {}", path.toAbsolutePath() + " ====");
                }

                log.info("==== MODE: " + context.getMode() + " ====");

                // pipeline execution
                ProcessingContext result = pipeline.execute(context);

                long durationMs = (System.nanoTime() - start) / 1_000_000;

                // log results
                log.info("\n==== KEYWORDS ====");
                result.getKeywords().forEach(log::info);

                log.info("\n==== STEPS BENCHMARK ====");
                context.getBenchmark().print();

                log.info("\n==== GLOBAL BENCHMARK ====");
                log.info("Total time: {} ms", durationMs);
            } catch (Exception e) {
                log.error("Pipeline execution failed", e);
            }
        };
    }

    private String resolveMode(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--mode=")) {

                String mode = arg.substring("--mode=".length()).trim();

                if (!mode.isBlank()) {
                    return mode;
                }
            }
        }

        return "fast";
    }

    private String resolveFilePath(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--file=")) {
                String file = arg.substring("--file=".length()).trim();

                if (!file.isBlank()) {
                    return file;
                }

                throw new IllegalArgumentException(
                        "Parameter --file cannot be empty");
            }
        }

        return null;
    }

    private void validateInputFile(Path path, PathValidationService pathValidationService) throws IOException {
        if (!pathValidationService.isValidPath(path.toAbsolutePath().toString())) {
            throw new IllegalArgumentException(
                    "The path format is not correct: " + path);
        }

        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "Input file does not exist: " + path);
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "Input path is not a regular file: " + path);
        }

        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(
                    "Input file is not readable: " + path);
        }

        if (Files.size(path) == 0) {
            throw new IllegalArgumentException(
                    "Input PDF is empty: " + path);
        }
    }

}
