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

import com.fmaupin.keyword.enumeration.PosCategory;

import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PosTagging {
    public static boolean isNoun(PosCategory pos) {
        return pos == PosCategory.NOUN;
    }

    public static boolean isProperName(PosCategory pos) {
        return pos == PosCategory.PROPN;
    }

    public static boolean isAdjective(PosCategory pos) {
        return pos == PosCategory.ADJ;
    }

    public static boolean isVerb(PosCategory pos) {
        return pos == PosCategory.VERB
                || pos == PosCategory.AUX;
    }

    public static boolean isDeterminer(PosCategory pos) {
        return pos == PosCategory.DET;
    }

    public static boolean isPronoun(PosCategory pos) {
        return pos == PosCategory.PRON;
    }

    public static boolean isAdverb(PosCategory pos) {
        return pos == PosCategory.ADV;
    }

    public static boolean isFunctionWord(PosCategory pos) {
        return isDeterminer(pos)
                || isPronoun(pos)
                || isAdverb(pos)
                || pos == PosCategory.ADP
                || pos == PosCategory.CONJ
                || pos == PosCategory.AUX;
    }
}
