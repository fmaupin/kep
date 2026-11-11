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

package com.fmaupin.keyword.pipeline;

import java.util.List;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.fmaupin.keyword.exceptions.KeywordException;
import com.fmaupin.keyword.model.ProcessingContext;

@Component
public class Pipeline {

    private final List<PipelineStep<ProcessingContext, ProcessingContext>> steps;

    public Pipeline(@NonNull List<PipelineStep<ProcessingContext, ProcessingContext>> steps) {
        AnnotationAwareOrderComparator.sort(steps);
        this.steps = steps;
    }

    public ProcessingContext execute(ProcessingContext context) throws KeywordException {
        for (var step : steps) {
            context = step.process(context);
        }
        return context;
    }
}
