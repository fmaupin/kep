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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "chunk.analysis")
@Data
public class ChunkAnalysisProperties {

    private int minContentSize = 1000;
    private double paragraphDetectionRatio = 0.1;
    private StructureProperties structure = new StructureProperties();
    private DensityProperties density = new DensityProperties();
    private DocumentProperties document = new DocumentProperties();

    @Data
    public static class StructureProperties {
        private String headingPattern = "^[A-Z0-9]+[.)\\s].*$";
        private double listItemRatio = 0.3;
        private int maxLineLength = 100;
    }

    @Data
    public static class DensityProperties {
        private int wordDensity = 5;
        private int sentenceLength = 20;
        private double whitespaceRatio = 0.15;
        private double varianceThreshold = 0.5;
    }

    @Data
    public static class DocumentProperties {
        private boolean formatDetection = true;
        private double tableThreshold = 0.3;
        private boolean metadataAnalysis = true;
        private double fontSizeVariations = 0.2;
        private double imageTextRatio = 0.1;
    }
}
