/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.apimap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** A class for collecting jar files. */
public final class FileUtils {

    private static final List<String> JAR_DIRS = new ArrayList<>(
            List.of("withres", "combined", "javac"));

    /**
     * Returns a jar file based on the given file path. Returns null if the jar file doesn't exist.
     */
    public static Path getJarFile(String inputFile) {
        Path filePath = Paths.get(inputFile);
        if (inputFile.endsWith(".jar")) {
            if (Files.exists(filePath)) {
                return filePath;
            }
            return null;
        } else if (inputFile.endsWith(".apk")) {
            // Search for the corresponding jar file for the given apk file. This only works for apk
            // packages installed under out/soong/.intermediate. For example, if the apk file path
            // is out/soong/.intermediate/.../Module/android_common/Module.apk, then search for
            // the jar file under
            // out/soong/.intermediate/.../Module/android_common/withres(combines, javac)/.
            String[] arrOsStr = inputFile.split("/");
            if (arrOsStr.length < 3) {
                return null;
            }
            String moduleName = arrOsStr[arrOsStr.length - 3];
            // Search for the jar file under withres, combined and javac directories in order.
            for (String jarDir : JAR_DIRS) {
                Path jarFile = Paths.get(String.format(
                        "%s/%s/%s.jar", filePath.getParent(), jarDir, moduleName));
                if (Files.exists(jarFile)) {
                    return jarFile;
                }
            }
        }
        return null;
    }

    /**
     * Parses jar files from the given file. Listed files must be split by a whitespace:
     * (1) jar file: record the file if it exists, otherwise, ignore it
     * (2) apk file: try to search for the corresponding jar file under the same directory if the
     *               given apk file is installed under out/soong/.intermediate/
     * (3) other types: ignore them
     */
    public static List<Path> getJarFilesFromFile(String inputFile) throws IOException {
        Path filePath = Paths.get(inputFile);
        List<String> lines = Files.readAllLines(filePath);
        List<Path> jarFiles = new ArrayList<>();
        for (String line : lines) {
            for (String file : line.split("\\s+")) {
                Path jarFile = getJarFile(file);
                if (jarFile == null) {
                    continue;
                }
                jarFiles.add(jarFile);
            }
        }
        return jarFiles;
    }
}
