/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.queryable.info;

import java.io.Serializable;

/**
 * Wrapper for information about a metadata value.
 */
public final class MetadataValue implements Serializable {

    private static final long serialVersionUID = 1;

    private final String mValue;

    /** Return a new builder for {@link MetadataValue}. */
    public static MetadataValue.Builder builder() {
        return new MetadataValue.Builder();
    }

    private MetadataValue(String value) {
        mValue = value;
    }

    /**
     * Get the value as a String.
     */
    public String asString() {
        return mValue;
    }

    /**
     * Get the value as an int.
     */
    public int asInt() {
        return Integer.parseInt(mValue);
    }

    /**
     * Get the value as a long.
     */
    public long asLong() {
        return Long.parseLong(mValue);
    }

    /**
     * Get the value as a boolean.
     */
    public boolean asBoolean() {
        return Boolean.parseBoolean(mValue);
    }

    @Override
    public String toString() {
        return "MetadataValue{"
                + "value=" + mValue
                + "}";
    }

    /** Builder for {@link MetadataValue}. */
    public static final class Builder {
        String mValue;

        /** Set the value with the value string provided. */
        public MetadataValue.Builder value(String value) {
            mValue = value;
            return this;
        }

        /** Build the {@link MetadataValue}*/
        public MetadataValue build() {
            return new MetadataValue(mValue);
        }
    }
}
