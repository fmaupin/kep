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

package com.fmaupin.keyword.service;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class PathValidationService {
    private static final int MAX_COMPONENT_LENGTH = 255;
    private static final int MAX_COMPONENTS = 100;

    // Windows core components
    private static final String WINDOWS_SEPARATOR = "\\\\";
    private static final String DRIVE_LETTER = "[a-zA-Z]:";
    private static final String UNC_PART = "[^\\\\/:*?\"<>|]+";
    private static final String FOLDER_OR_FILE = "(?!\\.?\\.?$)[^\\\\/:*?\"<>|]+";

    // Windows regex : accepts \\server\share\folder\file
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "^(?:"
                    + DRIVE_LETTER
                    + "|"
                    + WINDOWS_SEPARATOR + WINDOWS_SEPARATOR + UNC_PART + WINDOWS_SEPARATOR + UNC_PART
                    + ")"
                    + "(?:" + WINDOWS_SEPARATOR + FOLDER_OR_FILE + "){0," + MAX_COMPONENTS + "}"
                    + WINDOWS_SEPARATOR + FOLDER_OR_FILE + "$");

    // Unix core components
    private static final String UNIX_BASE_CHARS = "[A-Za-z0-9]";
    private static final String UNIX_EXTRA_CHARS = "[-_.+@]";
    private static final String UNIX_PATH_COMPONENT = "(?![-.])" + UNIX_BASE_CHARS + "(?:" + UNIX_BASE_CHARS + "|"
            + UNIX_EXTRA_CHARS + "){0," + (MAX_COMPONENT_LENGTH - 1) + "}";

    // Unix regex : accepts /folder/subfolder/file
    private static final Pattern UNIX_PATH = Pattern.compile(
            "^/(?:" + UNIX_PATH_COMPONENT + "/)*" + UNIX_PATH_COMPONENT + "$");

    public boolean isValidPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        try {
            Paths.get(path).normalize();

            String windowsPath = path.replace("/", "\\");

            if (WINDOWS_PATH.matcher(windowsPath).matches() && checkWindowsComponentsLength(windowsPath)
                    && hasFileName(windowsPath)) {
                return true;
            }

            String unixPath = path.replace("\\", "/");

            return UNIX_PATH.matcher(unixPath).matches() && checkUnixComponentsLength(unixPath)
                    && hasFileName(unixPath);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private boolean hasFileName(String path) {
        String normalized = path.replace("\\", "/");
        String[] parts = normalized.split("/");
        String last = parts[parts.length - 1];

        return last != null && !last.isEmpty();
    }

    private boolean checkWindowsComponentsLength(String path) {
        String[] parts = getPathComponents(path);

        if (isUncPath(path)) {
            return hasValidUncStructure(parts) && allComponentsValid(parts);
        }

        return allComponentsValid(parts);
    }

    private boolean isUncPath(String path) {
        return path.startsWith(WINDOWS_SEPARATOR);
    }

    private String[] getPathComponents(String path) {
        return path.substring(2).split(Pattern.quote("\\"));
    }

    private boolean hasValidUncStructure(String[] parts) {
        // UNC must have at least \\server\share\file
        return parts.length >= 4;
    }

    private boolean allComponentsValid(String[] parts) {
        for (String comp : parts) {
            if (!comp.isEmpty() && comp.length() > MAX_COMPONENT_LENGTH) {
                return false;
            }
        }

        return true;
    }

    private boolean checkUnixComponentsLength(String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        String[] comps = relative.split("/");

        return allComponentsValid(comps);
    }
}
