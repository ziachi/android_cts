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

import com.android.queryable.Queryable;
import com.android.queryable.info.ResourceInfo;
import com.android.queryable.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Implementation of {@link XmlResourceQuery} */
public final class XmlResourceQueryHelper<E extends Queryable> implements XmlResourceQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final Map<String, XmlPathQueryHelper<E>> mXmlPathQueryHelpers;

    public XmlResourceQueryHelper(E query) {
        mQuery = query;
        mXmlPathQueryHelpers = new HashMap<>();
    }

    private XmlResourceQueryHelper(Parcel in) {
        mQuery = null;
        mXmlPathQueryHelpers = in.readHashMap(XmlResourceQueryHelper.class.getClassLoader());
    }

    @Override
    public XmlPathQueryHelper<E> path(String path) {
        if (StringUtils.isNullOrEmpty(path)) {
            throw new IllegalStateException("Query contains an empty path");
        }

        if (!mXmlPathQueryHelpers.containsKey(path)) {
            mXmlPathQueryHelpers.put(path, new XmlPathQueryHelper<>(mQuery, path));
        }
        return mXmlPathQueryHelpers.get(path);
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        for (Map.Entry<String, XmlPathQueryHelper<E>> query : mXmlPathQueryHelpers.entrySet()) {
            queryStrings.add(query.getValue().describeQuery(fieldName + "." + query.getKey()));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public boolean isEmptyQuery() {
        for (Map.Entry<String, XmlPathQueryHelper<E>> keyQueries : mXmlPathQueryHelpers.entrySet()) {
            if (!Queryable.isEmptyQuery(keyQueries.getValue())) {
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
    public void writeToParcel(Parcel out, int flags) {
        out.writeMap(mXmlPathQueryHelpers);
    }

    public static final Parcelable.Creator<XmlResourceQueryHelper> CREATOR =
            new Parcelable.Creator<>() {
                public XmlResourceQueryHelper createFromParcel(Parcel in) {
                    return new XmlResourceQueryHelper(in);
                }

                public XmlResourceQueryHelper[] newArray(int size) {
                    return new XmlResourceQueryHelper[size];
                }
            };

    /** Match the XML resource with the query. */
    @Override
    public boolean matches(ResourceInfo resource) {
        for (Map.Entry<String, XmlPathQueryHelper<E>> pathQueries :
                mXmlPathQueryHelpers.entrySet()) {
            if (!pathQueries.getValue().matches(resource)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XmlResourceQueryHelper<?>)) return false;
        XmlResourceQueryHelper<?> that = (XmlResourceQueryHelper<?>) o;
        return Objects.equals(mXmlPathQueryHelpers, that.mXmlPathQueryHelpers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mXmlPathQueryHelpers);
    }
}
