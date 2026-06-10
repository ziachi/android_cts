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

import com.android.bedstead.nene.annotations.Nullable;

import java.io.Serializable;

/**
 * Wrapper for information about a Metadata.
 */
public final class MetadataInfo implements Serializable {

    private static final long serialVersionUID = 1;

    private final String mKey;
    private final MetadataValue mValue;
    private ResourceInfo mResource;

    /** Return a new builder for {@link MetadataInfo}. */
    public static MetadataInfo.Builder builder() {
        return new MetadataInfo.Builder();
    }

    private MetadataInfo(String key, MetadataValue value, ResourceInfo resource) {
        mKey = key;
        mValue = value;
        mResource = resource;
    }

    /** Get key of the metadata tag. */
    public String key() {
        return mKey;
    }

    /** Get value of the metadata tag. */
    @Nullable
    public MetadataValue value() {
        return mValue;
    }

    /** Get resource of the metadata tag. */
    @Nullable
    public ResourceInfo resource() {
        return mResource;
    }

    /** Set resource of {@link MetadataInfo}. */
    public void setResource(ResourceInfo resource) {
        mResource = resource;
    }

    @Override
    public String toString() {
        return "Metadata{"
                + "key=" + mKey
                + ", value=" + (mValue == null ? "null" : mValue.asString())
                + ", resource=" + (mResource == null ? "null" : mResource.asString())
                + "}";
    }

    /** Builder for {@link MetadataInfo}. */
    public static final class Builder {
        String mKey;
        MetadataValue mValue;
        ResourceInfo mResource;

        /** Set the key with the key provided. */
        public MetadataInfo.Builder key(String key) {
            mKey = key;
            return this;
        }

        /** Set the value with the value provided. */
        public MetadataInfo.Builder value(MetadataValue value) {
            mValue = value;
            return this;
        }

        /** Set the resource with the resource provided. */
        public MetadataInfo.Builder resource(ResourceInfo resource) {
            mResource = resource;
            return this;
        }

        /** Build the {@link MetadataInfo}*/
        public MetadataInfo build() {
            return new MetadataInfo(mKey, mValue, mResource);
        }
    }
}
