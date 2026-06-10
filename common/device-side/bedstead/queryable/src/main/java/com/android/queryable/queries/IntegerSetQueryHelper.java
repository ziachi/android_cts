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

package com.android.queryable.queries;

import com.android.queryable.Queryable;
import com.android.queryable.annotations.IntegerSetQuery;

import com.google.auto.value.AutoAnnotation;

import java.io.Serializable;

@SuppressWarnings("CheckReturnValue")
public final class IntegerSetQueryHelper<E extends Queryable> extends SetQueryHelper<E, Integer>
        implements Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;

    public IntegerSetQueryHelper(E query) {
        super(query);
        mQuery = query;
    }

    public E matchesAnnotation(IntegerSetQuery queryAnnotation) {
        if (queryAnnotation.contains().length > 0) {
            Integer[] intArray = new Integer[queryAnnotation.contains().length];
            for (int i = 0; i < intArray.length; i++) {
                intArray[i] = queryAnnotation.contains()[i];
            }
            contains(intArray);
        }

        return mQuery;
    }

    public IntegerSetQuery toAnnotation() {
        return integerSetQuery(mContainsByType.isEmpty() ? new int[] {} :
                mContainsByType.stream().mapToInt(Number::intValue).toArray());
    }

    @AutoAnnotation
    private static IntegerSetQuery integerSetQuery(int[] contains) {
        return new AutoAnnotation_IntegerSetQueryHelper_integerSetQuery(contains);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}