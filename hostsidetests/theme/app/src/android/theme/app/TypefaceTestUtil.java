/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.theme.app;

import android.graphics.Paint;
import android.graphics.fonts.Font;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;

import java.io.File;

public class TypefaceTestUtil {
    public static File getFirstFont(String text, Paint p) {
        PositionedGlyphs glyphs = TextRunShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, p);

        requireNotEqual(0, glyphs.glyphCount());
        Font font = glyphs.getFont(0);
        requireNonNull(font.getFile());
        return font.getFile();
    }

    private static void requireNonNull(Object obj) {
        if (obj == null) {
            throw new RuntimeException("Must not null");
        }
    }

    private static void requireNotEqual(int l, int r) {
        if (l == r) {
            throw new RuntimeException("Must not equal");
        }
    }
}
