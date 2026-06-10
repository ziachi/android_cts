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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.queryable.Queryable;
import com.android.queryable.info.ResourceInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO(b/273215258) (add support for types other than XML)

/**
 * Implementation of {@link ResourceQuery}
 */
public class ResourceQueryHelper<E extends Queryable> implements ResourceQuery<E>, Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private XmlResourceQueryHelper<E> mXmlResourceQuery;

    public ResourceQueryHelper(E query) {
        mQuery = query;
    }

    private ResourceQueryHelper(Parcel in) {
        mQuery = null;
        mXmlResourceQuery = in.readParcelable(ResourceQueryHelper.class.getClassLoader());
    }

    @Override
    public XmlResourceQueryHelper<E> asXml() {
        if (mXmlResourceQuery == null) {
            mXmlResourceQuery = new XmlResourceQueryHelper<>(mQuery);
        }
        return mXmlResourceQuery;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mXmlResourceQuery != null) {
            queryStrings.add(mXmlResourceQuery.describeQuery(fieldName));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public boolean isEmptyQuery() {
        return mXmlResourceQuery.isEmptyQuery();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mXmlResourceQuery, flags);
    }

    public static final Parcelable.Creator<ResourceQueryHelper> CREATOR =
            new Parcelable.Creator<>() {
                public ResourceQueryHelper createFromParcel(Parcel in) {
                    return new ResourceQueryHelper(in);
                }

                public ResourceQueryHelper[] newArray(int size) {
                    return new ResourceQueryHelper[size];
                }
            };

    @Override
    public boolean matches(ResourceInfo resource) {
        if (mXmlResourceQuery != null && !mXmlResourceQuery.matches(resource)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceQueryHelper<?>)) return false;
        ResourceQueryHelper<?> that = (ResourceQueryHelper<?>) o;
        return Objects.equals(mXmlResourceQuery, that.mXmlResourceQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mXmlResourceQuery);
    }
}
