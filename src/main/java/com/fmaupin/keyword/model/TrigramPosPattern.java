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

import com.fmaupin.keyword.enumeration.PosCategory;

public record TrigramPosPattern(
        double quality,
        PosCategory pos1,
        PosCategory pos2,
        PosCategory pos3) {

    public boolean matches(
            PosCategory actual1,
            PosCategory actual2,
            PosCategory actual3) {
        return pos1 == actual1
                && pos2 == actual2
                && pos3 == actual3;
    }
}
