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

import lombok.Getter;

@Getter
public class Token {
    private final String text;

    /**
     * True when a hard punctuation boundary exists
     * immediately before this token.
     */
    private final boolean hardBoundary;

    public Token(
            String text,
            boolean hardBoundary) {

        this.text = text;
        this.hardBoundary = hardBoundary;
    }
}
