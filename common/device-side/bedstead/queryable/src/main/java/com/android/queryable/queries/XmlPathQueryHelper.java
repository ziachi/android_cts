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
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.queryable.Queryable;
import com.android.queryable.info.ResourceInfo;
import com.android.queryable.util.StringUtils;

import org.xml.sax.InputSource;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public final class XmlPathQueryHelper<E extends Queryable> implements XmlPathQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private static final String LOG_TAG = "XmlPathQueryHelper";
    private static final String SINGLE_FORWARD_SLASH = "/";
    private static final String DOUBLE_FORWARD_SLASH = "//";
    private static final XPath sXPath = XPathFactory.newInstance().newXPath();

    private final transient E mQuery;
    private final String mPath;

    private Boolean mExpectsToExist = null;
    private StringQueryHelper<E> mTextValueQuery = null;
    private final Map<String, XmlAttributeValueQueryHelper<E>> mAttributeValueQueryHelpers;

    public XmlPathQueryHelper(E query, String path) {
        mQuery = query;
        mPath = enforceXpathSyntax(path);
        mAttributeValueQueryHelpers = new HashMap<>();
    }

    private XmlPathQueryHelper(Parcel in) {
        mQuery = null;
        mExpectsToExist = readNullableBoolean(in);
        mPath = in.readString();
        mTextValueQuery = in.readParcelable(XmlPathQueryHelper.class.getClassLoader());
        mAttributeValueQueryHelpers = in.readHashMap(XmlPathQueryHelper.class.getClassLoader());
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
    public StringQuery<E> asText() {
        if (mTextValueQuery == null) {
            mTextValueQuery = new StringQueryHelper<>(mQuery);
        }
        return mTextValueQuery;
    }

    @Override
    public XmlAttributeValueQueryHelper<E> attribute(String attribute) {
        if (StringUtils.isNullOrEmpty(attribute)) {
            throw new IllegalStateException("Query contains an empty attribute");
        }

        if (!mAttributeValueQueryHelpers.containsKey(attribute)) {
            mAttributeValueQueryHelpers.put(attribute,
                    new XmlAttributeValueQueryHelper<>(mQuery, attribute));
        }

        return mAttributeValueQueryHelpers.get(attribute);
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();

        if (mExpectsToExist != null) {
            queryStrings.add(fieldName + " exists");
        }

        if (mTextValueQuery != null) {
            queryStrings.add(mTextValueQuery.describeQuery(fieldName + ".textValue"));
        }

        for (Map.Entry<String, XmlAttributeValueQueryHelper<E>> query :
                mAttributeValueQueryHelpers.entrySet()) {
            queryStrings.add(query.getValue().describeQuery(fieldName + "." + query.getKey()));
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public boolean isEmptyQuery() {
        return mExpectsToExist == null && mTextValueQuery.isEmptyQuery();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        writeNullableBoolean(dest, mExpectsToExist);
        dest.writeString(mPath);
        dest.writeParcelable(mTextValueQuery, flags);
    }

    public static final Parcelable.Creator<XmlPathQueryHelper> CREATOR =
            new Parcelable.Creator<>() {
                public XmlPathQueryHelper createFromParcel(Parcel in) {
                    return new XmlPathQueryHelper(in);
                }

                public XmlPathQueryHelper[] newArray(int size) {
                    return new XmlPathQueryHelper[size];
                }
            };

    /**
     * Match the XML resource with the query.
     */
    public boolean matches(ResourceInfo resource) {
        if (StringUtils.isNullOrEmpty(mPath)) {
            throw new IllegalStateException("Cannot call matches() for an XML query without "
                    + "specifying the path through path() first.");
        }

        try {
            String path = mPath;
            if (mExpectsToExist != null) {
                boolean pathExists = Boolean.parseBoolean(
                        sXPath.evaluate("boolean(" + path + ")",
                                new InputSource(new StringReader(resource.asString()))));
                if (pathExists != mExpectsToExist) {
                    return false;
                }
            }

            if (mTextValueQuery != null) {
                String textValue =
                        sXPath.evaluate(StringUtils.ensureEndsWith(path, '/') + "text()",
                                        new InputSource(new StringReader(resource.asString())))
                                .trim();

                if (!mTextValueQuery.matches(textValue)) {
                    return false;
                }
            }

            for (Map.Entry<String, XmlAttributeValueQueryHelper<E>> attributeValueQueries :
                    mAttributeValueQueryHelpers.entrySet()) {
                String attribute = attributeValueQueries.getKey();
                String attributeValue =
                        sXPath.evaluate(
                                StringUtils.ensureEndsWith(path, '/') + "@" + attribute,
                                        new InputSource(new StringReader(resource.asString())))
                                .trim();

                if (!attributeValueQueries.getValue().matches(attributeValue)) {
                    return false;
                }
            }
        } catch (XPathExpressionException e) {
            Log.e(LOG_TAG, "Unable to evaluate XML resource", e);
            return false;
        }

        return true;
    }

    private static String enforceXpathSyntax(String path) {
        if (path.endsWith(SINGLE_FORWARD_SLASH)) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.startsWith(DOUBLE_FORWARD_SLASH)) {
            return path;
        }
        if (path.startsWith(SINGLE_FORWARD_SLASH)) {
            return SINGLE_FORWARD_SLASH + path;
        }
        return DOUBLE_FORWARD_SLASH + path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XmlPathQueryHelper<?>)) return false;
        XmlPathQueryHelper<?> that = (XmlPathQueryHelper<?>) o;
        return Objects.equals(mExpectsToExist, that.mExpectsToExist)
                && Objects.equals(mTextValueQuery, that.mTextValueQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mExpectsToExist, mTextValueQuery);
    }
}
