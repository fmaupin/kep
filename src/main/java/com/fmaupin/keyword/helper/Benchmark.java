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

package com.fmaupin.keyword.helper;

import java.util.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Benchmark {
    private final Map<String, Long> timings = new LinkedHashMap<>();
    private long startTime;

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void end(String step) {
        timings.put(step, System.currentTimeMillis() - startTime);
    }

    public void print() {
        timings.forEach((k, v) -> log.info(k + ": " + v + " ms"));
    }
}
