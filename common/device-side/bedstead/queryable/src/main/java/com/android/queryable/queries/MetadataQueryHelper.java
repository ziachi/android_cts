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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;
import com.android.queryable.info.MetadataInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Implementation of {@link MetadataQuery} */
public final class MetadataQueryHelper<E extends Queryable> implements MetadataQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final Map<String, MetadataKeyQueryHelper<E>> mKeyQueryHelpers;

    public static final class MetadataQueryBase extends
            QueryableBaseWithMatch<Set<MetadataInfo>, MetadataQueryHelper<MetadataQueryBase>> {
        MetadataQueryBase() {
            super();
            setQuery(new MetadataQueryHelper<>(this));
        }

        MetadataQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<MetadataQueryHelper.MetadataQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public MetadataQueryHelper.MetadataQueryBase createFromParcel(Parcel in) {
                        return new MetadataQueryHelper.MetadataQueryBase(in);
                    }

                    public MetadataQueryHelper.MetadataQueryBase[] newArray(int size) {
                        return new MetadataQueryHelper.MetadataQueryBase[size];
                    }
                };
    }

    public MetadataQueryHelper(E query) {
        mQuery = query;
        mKeyQueryHelpers = new HashMap<>();
    }

    private MetadataQueryHelper(Parcel in) {
        mQuery = null;
        mKeyQueryHelpers = in.readHashMap(MetadataQueryHelper.class.getClassLoader());
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        for (Map.Entry<String, MetadataKeyQueryHelper<E>> query : mKeyQueryHelpers.entrySet()) {
            queryStrings.add(query.getValue().describeQuery(fieldName + "." + query.getKey()));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public boolean isEmptyQuery() {
        for (Map.Entry<String, MetadataKeyQueryHelper<E>> keyQueries : mKeyQueryHelpers.entrySet()) {
            if (!Queryable.isEmptyQuery(keyQueries.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public MetadataKeyQuery<E> key(String key) {
        if (!mKeyQueryHelpers.containsKey(key)) {
            mKeyQueryHelpers.put(key, new MetadataKeyQueryHelper<>(mQuery));
        }
        return mKeyQueryHelpers.get(key);
    }

    @Override
    public boolean matches(Set<MetadataInfo> metadataSet) {
        for (Map.Entry<String, MetadataKeyQueryHelper<E>> keyQueries :
                mKeyQueryHelpers.entrySet()) {
            if (!keyQueries.getValue().matches(metadataSet, keyQueries.getKey())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeMap(mKeyQueryHelpers);
    }

    public static boolean matches(
            MetadataQueryHelper<?> metadataQueryHelper, Set<MetadataInfo> value) {
        return metadataQueryHelper.matches(value);
    }

    public static final Parcelable.Creator<MetadataQueryHelper> CREATOR =
            new Parcelable.Creator<>() {
                public MetadataQueryHelper createFromParcel(Parcel in) {
                    return new MetadataQueryHelper(in);
                }

                public MetadataQueryHelper[] newArray(int size) {
                    return new MetadataQueryHelper[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetadataQueryHelper<?>)) return false;
        MetadataQueryHelper<?> that = (MetadataQueryHelper<?>) o;
        return Objects.equals(mKeyQueryHelpers, that.mKeyQueryHelpers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKeyQueryHelpers);
    }
}
