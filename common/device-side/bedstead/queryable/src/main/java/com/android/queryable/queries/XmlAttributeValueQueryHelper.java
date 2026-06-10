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

package com.android.queryable.queries;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.queryable.Queryable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Implementation of {@link XmlAttributeValueQuery} */
public final class XmlAttributeValueQueryHelper<E extends Queryable>
        implements XmlAttributeValueQuery<E> {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final String mAttribute;
    private StringQueryHelper<E> mValueQuery = null;

    public XmlAttributeValueQueryHelper(E query, String attribute) {
        mQuery = query;
        mAttribute = attribute;
    }

    public XmlAttributeValueQueryHelper(Parcel in) {
        mQuery = null;
        mAttribute = in.readString();
        mValueQuery = in.readParcelable(XmlPathQueryHelper.class.getClassLoader());
    }

    public static final Parcelable.Creator<XmlAttributeValueQueryHelper> CREATOR =
            new Parcelable.Creator<>() {
                public XmlAttributeValueQueryHelper createFromParcel(Parcel in) {
                    return new XmlAttributeValueQueryHelper(in);
                }

                public XmlAttributeValueQueryHelper[] newArray(int size) {
                    return new XmlAttributeValueQueryHelper[size];
                }
            };

    @Override
    public StringQuery<E> value() {
        if (mValueQuery == null) {
            mValueQuery = new StringQueryHelper<>(mQuery);
        }
        return mValueQuery;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mValueQuery != null) {
            queryStrings.add(mValueQuery.describeQuery(fieldName + ".value"));
        }

        return null;
    }

    @Override
    public boolean isEmptyQuery() {
        return mValueQuery.isEmptyQuery();
    }

    public boolean matches(String value) {
        if (!mValueQuery.matches(value)) {
            return false;
        }

        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mAttribute);
        dest.writeParcelable(mValueQuery, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XmlAttributeValueQueryHelper<?>)) return false;
        XmlAttributeValueQueryHelper<?> that = (XmlAttributeValueQueryHelper<?>) o;
        return Objects.equals(mAttribute, that.mAttribute) && Objects.equals(
                mValueQuery, that.mValueQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAttribute, mValueQuery);
    }
}
