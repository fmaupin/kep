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

package com.fmaupin.keyword.model;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fmaupin.keyword.helper.Benchmark;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcessingContext {
    private InputStream inputStream;
    private String text;

    private List<String> chunks;

    private List<float[]> candidateEmbeddings;
    private List<String> candidates;

    private List<String> keywords;

    private String mode;
    private String language;

    private Map<String, Double> candidateScores = new HashMap<>();

    private Benchmark benchmark = new Benchmark();
}
