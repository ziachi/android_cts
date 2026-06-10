/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.graphics.fonts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FontFileTestUtil {
    private static final int SFNT_VERSION_1 = 0x00010000;
    private static final int SFNT_VERSION_OTTO = 0x4F54544F;
    private static final int TTC_TAG = 0x74746366;
    private static final int NAME_TAG = 0x6E616D65;
    private static final int FVAR_TAG = 0x66766172;
    private static final int META_TAG = 0x6D657461;
    private static final int EMJI_TAG = 0x456D6A69;

    public static String getPostScriptName(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            final FileChannel fc = fis.getChannel();
            long size = fc.size();
            ByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, size)
                    .order(ByteOrder.BIG_ENDIAN);

            int magicNumber = buffer.getInt(0);

            int fontOffset = 0;
            if (magicNumber == TTC_TAG) {
                fontOffset = buffer.getInt(12);  // 0th offset
                magicNumber = buffer.getInt(fontOffset);
                if (magicNumber != SFNT_VERSION_1 && magicNumber != SFNT_VERSION_OTTO) {
                    throw new IOException("Unknown magic number at 0th font: #" + magicNumber);
                }
            } else if (magicNumber != SFNT_VERSION_1 && magicNumber != SFNT_VERSION_OTTO) {
                throw new IOException("Unknown magic number: #" + magicNumber);
            }

            int numTables = buffer.getShort(fontOffset + 4);  // offset to number of table
            int nameTableOffset = 0;
            for (int i = 0; i < numTables; ++i) {
                int tableEntryOffset = fontOffset + 12 + i * 16;
                int tableTag = buffer.getInt(tableEntryOffset);
                if (tableTag == NAME_TAG) {
                    nameTableOffset = buffer.getInt(tableEntryOffset + 8);
                    break;
                }
            }

            if (nameTableOffset == 0) {
                throw new IOException("name table not found.");
            }

            int nameTableCount = buffer.getShort(nameTableOffset + 2);
            int storageOffset = buffer.getShort(nameTableOffset + 4);

            for (int i = 0; i < nameTableCount; ++i) {
                int platformID = buffer.getShort(nameTableOffset + 6 + i * 12);
                int encodingID = buffer.getShort(nameTableOffset + 6 + i * 12 + 2);
                int languageID = buffer.getShort(nameTableOffset + 6 + i * 12 + 4);
                int nameID = buffer.getShort(nameTableOffset + 6 + i * 12 + 6);
                int length = buffer.getShort(nameTableOffset + 6 + i * 12 + 8);
                int stringOffset = buffer.getShort(nameTableOffset + 6 + i * 12 + 10);

                if (nameID == 6 && platformID == 3 && encodingID == 1 && languageID == 1033) {
                    byte[] name = new byte[length];
                    ByteBuffer slice = buffer.slice();
                    slice.position(nameTableOffset + storageOffset + stringOffset);
                    slice.get(name);
                    // encoded in UTF-16BE for platform ID = 3
                    return new String(name, StandardCharsets.UTF_16BE);
                }
            }
        }
        return null;
    }

    public static final class FVarEntry {
        public FVarEntry(float minValue, float defaultValue, float maxValue) {
            mMinValue = minValue;
            mDefaultValue = defaultValue;
            mMaxValue = maxValue;
        }

        public float getMinValue() {
            return mMinValue;
        }

        public void setMinValue(float minValue) {
            mMinValue = minValue;
        }

        public float getDefaultValue() {
            return mDefaultValue;
        }

        public void setDefaultValue(float defaultValue) {
            mDefaultValue = defaultValue;
        }

        public float getMaxValue() {
            return mMaxValue;
        }

        public void setMaxValue(float maxValue) {
            mMaxValue = maxValue;
        }

        private float mMinValue;
        private float mDefaultValue;
        private float mMaxValue;
    }

    private static float to1616Fixed(int bits) {
        return bits / (float) (0x10000);
    }

    /**
     * Read fvar table and return the axis definitions.
     */
    public static Map<String, FVarEntry> getFVarTable(ByteBuffer buf) throws IOException {
        ByteBuffer buffer = buf.order(ByteOrder.BIG_ENDIAN);

        int magicNumber = buffer.getInt(0);

        int fontOffset = 0;
        if (magicNumber == TTC_TAG) {
            fontOffset = buffer.getInt(12);  // 0th offset
            magicNumber = buffer.getInt(fontOffset);
            if (magicNumber != SFNT_VERSION_1 && magicNumber != SFNT_VERSION_OTTO) {
                throw new IOException("Unknown magic number at 0th font: #" + magicNumber);
            }
        } else if (magicNumber != SFNT_VERSION_1 && magicNumber != SFNT_VERSION_OTTO) {
            throw new IOException("Unknown magic number: #" + magicNumber);
        }

        int numTables = buffer.getShort(fontOffset + 4);  // offset to number of table
        int fvarTableOffset = 0;
        for (int i = 0; i < numTables; ++i) {
            int tableEntryOffset = fontOffset + 12 + i * 16;
            int tableTag = buffer.getInt(tableEntryOffset);
            if (tableTag == FVAR_TAG) {
                fvarTableOffset = buffer.getInt(tableEntryOffset + 8);
                break;
            }
        }

        if (fvarTableOffset == 0) {
            throw new IOException("name table not found.");
        }

        int axisOffset = buffer.getShort(fvarTableOffset + 4);
        int axisCount = buffer.getShort(fvarTableOffset + 8);
        int axisSize = buffer.getShort(fvarTableOffset + 10);

        Map<String, FVarEntry> out = new HashMap<>();
        for (int i = 0; i < axisCount; i++) {
            int recordOffset = axisOffset + i * axisSize;
            int tag = buffer.getInt(fvarTableOffset + recordOffset);
            float minValue = to1616Fixed(buffer.getInt(fvarTableOffset + recordOffset + 4));
            float defaultValue = to1616Fixed(buffer.getInt(fvarTableOffset + recordOffset + 8));
            float maxValue = to1616Fixed(buffer.getInt(fvarTableOffset + recordOffset + 12));

            String tagStr = String.format("%c%c%c%c",
                    (tag >> 24) & 0xFF, (tag >> 16) & 0xFF, (tag >> 8) & 0xFF, tag & 0xFF);
            out.put(tagStr, new FVarEntry(minValue, defaultValue, maxValue));
        }
        return out;
    }
}
