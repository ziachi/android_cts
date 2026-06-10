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

package com.android.queryable.queries;

import static com.android.queryable.util.ParcelableUtils.readNullableBoolean;
import static com.android.queryable.util.ParcelableUtils.writeNullableBoolean;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.queryable.Queryable;
import com.android.queryable.info.MetadataInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Implementation of {@link MetadataKeyQuery}. */
public final class MetadataKeyQueryHelper<E extends Queryable> implements MetadataKeyQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1L;

    private final transient E mQuery;
    private Boolean mExpectsToExist = null;
    private StringQueryHelper<E> mStringQuery = null;
    private IntegerQueryHelper<E> mIntegerQuery = null;
    private LongQueryHelper<E> mLongQuery = null;
    private BooleanQueryHelper<E> mBooleanQuery = null;
    private ResourceQueryHelper<E> mResourceQuery = null;

    public MetadataKeyQueryHelper(E query) {
        mQuery = query;
    }

    private MetadataKeyQueryHelper(Parcel in) {
        mQuery = null;
        mExpectsToExist = readNullableBoolean(in);
        mStringQuery = in.readParcelable(MetadataKeyQueryHelper.class.getClassLoader());
        mIntegerQuery = in.readParcelable(MetadataKeyQueryHelper.class.getClassLoader());
        mLongQuery = in.readParcelable(MetadataKeyQueryHelper.class.getClassLoader());
        mBooleanQuery = in.readParcelable(MetadataKeyQueryHelper.class.getClassLoader());
        mResourceQuery = in.readParcelable(MetadataKeyQueryHelper.class.getClassLoader());
    }

    @Override
    public E exists() {
        if (mExpectsToExist != null) {
            throw new IllegalStateException(
                    "Cannot call exists() after calling exists() or doesNotExist()");
        }
        mExpectsToExist = true;
        return mQuery;
    }

    @Override
    public E doesNotExist() {
        if (mExpectsToExist != null) {
            throw new IllegalStateException(
                    "Cannot call doesNotExist() after calling exists() or doesNotExist()");
        }
        mExpectsToExist = false;
        return mQuery;
    }

    @Override
    public StringQuery<E> stringValue() {
        if (mStringQuery == null) {
            checkUntyped();
            mStringQuery = new StringQueryHelper<>(mQuery);
        }
        return mStringQuery;
    }

    @Override
    public IntegerQuery<E> integerValue() {
        if (mIntegerQuery == null) {
            checkUntyped();
            mIntegerQuery = new IntegerQueryHelper<>(mQuery);
        }
        return mIntegerQuery;
    }

    @Override
    public LongQuery<E> longValue() {
        if (mLongQuery == null) {
            checkUntyped();
            mLongQuery = new LongQueryHelper<>(mQuery);
        }
        return mLongQuery;
    }

    @Override
    public BooleanQuery<E> booleanValue() {
        if (mBooleanQuery == null) {
            checkUntyped();
            mBooleanQuery = new BooleanQueryHelper<>(mQuery);
        }
        return mBooleanQuery;
    }

    @Override
    public ResourceQuery<E> resourceValue() {
        if (mResourceQuery == null) {
            checkUntyped();
            mResourceQuery = new ResourceQueryHelper<>(mQuery);
        }
        return mResourceQuery;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        writeNullableBoolean(dest, mExpectsToExist);
        dest.writeParcelable(mStringQuery, flags);
        dest.writeParcelable(mIntegerQuery, flags);
        dest.writeParcelable(mLongQuery, flags);
        dest.writeParcelable(mBooleanQuery, flags);
        dest.writeParcelable(mResourceQuery, flags);
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mExpectsToExist != null) {
            queryStrings.add(fieldName + " exists");
        }
        if (mStringQuery != null) {
            queryStrings.add(mStringQuery.describeQuery(fieldName + ".stringValue"));
        }
        if (mIntegerQuery != null) {
            queryStrings.add(mIntegerQuery.describeQuery(fieldName + ".integerValue"));
        }
        if (mLongQuery != null) {
            queryStrings.add(mLongQuery.describeQuery(fieldName + ".longValue"));
        }
        if (mBooleanQuery != null) {
            queryStrings.add(mBooleanQuery.describeQuery(fieldName + ".booleanValue"));
        }
        if (mResourceQuery != null) {
            queryStrings.add(mResourceQuery.describeQuery(fieldName + ".resourceValue"));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public boolean isEmptyQuery() {
        return mExpectsToExist == null && mStringQuery.isEmptyQuery() &&
                mIntegerQuery.isEmptyQuery() && mLongQuery.isEmptyQuery() &&
                mBooleanQuery.isEmptyQuery() && mResourceQuery.isEmptyQuery();
    }

    /** Match the metadata for the provided {@code key} with the provided {@code metadataSet}. */
    public boolean matches(Set<MetadataInfo> metadataSet, String key) {
        if (metadataSet.isEmpty()) {
            return false;
        }

        Optional<MetadataInfo> optionalMetadataForKey = metadataSet.stream().filter(
                m -> m.key().equals(key)).findFirst();
        if (optionalMetadataForKey.isPresent()) {
            if (mExpectsToExist != null && !mExpectsToExist) {
                return false;
            }
            MetadataInfo metadata = optionalMetadataForKey.get();
            if (mStringQuery != null && !mStringQuery.matches(metadata.value().asString())) {
                return false;
            }
            if (mIntegerQuery != null && !mIntegerQuery.matches(metadata.value().asInt())) {
                return false;
            }
            if (mLongQuery != null && !mLongQuery.matches(metadata.value().asLong())) {
                return false;
            }
            if (mBooleanQuery != null
                    && !mBooleanQuery.matches(metadata.value().asBoolean())) {
                return false;
            }
            if (mResourceQuery != null && !mResourceQuery.matches(metadata.resource())) {
                return false;
            }
        } else {
            if (mExpectsToExist == null || mExpectsToExist) {
                return false;
            }
        }

        return true;
    }

    public static final Parcelable.Creator<MetadataKeyQueryHelper> CREATOR =
            new Parcelable.Creator<>() {
                public MetadataKeyQueryHelper createFromParcel(Parcel in) {
                    return new MetadataKeyQueryHelper<>(in);
                }

                public MetadataKeyQueryHelper[] newArray(int size) {
                    return new MetadataKeyQueryHelper[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetadataKeyQueryHelper<?>)) return false;
        MetadataKeyQueryHelper<?> that = (MetadataKeyQueryHelper<?>) o;
        return Objects.equals(mExpectsToExist, that.mExpectsToExist)
                && Objects.equals(mStringQuery, that.mStringQuery)
                && Objects.equals(mIntegerQuery, that.mIntegerQuery)
                && Objects.equals(mLongQuery, that.mLongQuery)
                && Objects.equals(mBooleanQuery, that.mBooleanQuery)
                && Objects.equals(mResourceQuery, that.mResourceQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mExpectsToExist, mStringQuery, mIntegerQuery, mLongQuery,
                mBooleanQuery, mResourceQuery);
    }

    private void checkUntyped() {
        if (mStringQuery != null || mIntegerQuery != null || mLongQuery != null
                || mBooleanQuery != null || mResourceQuery != null) {
            throw new IllegalStateException("Each key can only be typed once");
        }
    }
}
