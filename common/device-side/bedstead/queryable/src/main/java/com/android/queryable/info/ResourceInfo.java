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

// TODO(b/273215258): add support for other types e.g. drawables
/**
 * Wrapper for information about a Resource.
 */
public final class ResourceInfo implements Serializable {

    private static final long serialVersionUID = 1;

    private final String mStringContent;

    /** Return a new builder for {@link ResourceInfo}. */
    public static ResourceInfo.Builder builder() {
        return new ResourceInfo.Builder();
    }

    private ResourceInfo(String content) {
        mStringContent = content;
    }

    /** Get the content of this resource in String format. */
    public String asString() {
        return mStringContent;
    }

    /** Builder for {@link ResourceInfo}. */
    public static final class Builder {
        String mContent;

        /** Set the content with the content string provided. */
        public ResourceInfo.Builder content(String content) {
            mContent = content;
            return this;
        }

        /** Build the {@link ResourceInfo}*/
        public ResourceInfo build() {
            return new ResourceInfo(mContent);
        }
    }
}
