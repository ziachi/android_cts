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

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;

import static com.google.common.truth.Truth.assertThat;

import com.android.queryable.Queryable;
import com.android.queryable.info.MetadataInfo;
import com.android.queryable.info.MetadataValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Set;

@RunWith(JUnit4.class)
public final class MetadataKeyQueryHelperTest {
    private static final String KEY = "Key";
    private static final String KEY2 = "Key2";
    private static final String STRING_VALUE = "String";
    private static final String DIFFERENT_STRING_VALUE = "String2";
    private static final int INTEGER_VALUE = 1;
    private static final int DIFFERENT_INTEGER_VALUE = 2;
    private static final long LONG_VALUE = 1;
    private static final long DIFFERENT_LONG_VALUE = 2;
    private static final Set<MetadataInfo> EMPTY_METADATA_SET = Collections.emptySet();
    private static final Set<MetadataInfo> METADATA_SET_STRING_VALUE = Collections.singleton(
            MetadataInfo.builder().key(KEY).value(
                    MetadataValue.builder().value(STRING_VALUE).build()).build());
    private static final Set<MetadataInfo> METADATA_SET_INTEGER_VALUE = Collections.singleton(
            MetadataInfo.builder().key(KEY).value(
                    MetadataValue.builder().value(String.valueOf(INTEGER_VALUE)).build()).build());
    private static final Set<MetadataInfo> METADATA_SET_LONG_VALUE = Collections.singleton(
            MetadataInfo.builder().key(KEY).value(
                    MetadataValue.builder().value(String.valueOf(LONG_VALUE)).build()).build());
    private static final Set<MetadataInfo> METADATA_SET_BOOLEAN_VALUE = Collections.singleton(
            MetadataInfo.builder().key(KEY).value(
                    MetadataValue.builder().value(String.valueOf(true)).build()).build());

    private final Queryable mQuery = null;

    @Test
    public void matches_metadataDoesNotExist_returnsFalse() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        assertThat(metadataKeyQueryHelper.matches(EMPTY_METADATA_SET, KEY)).isFalse();
    }

    @Test
    public void matches_noRestrictions_returnsTrue() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_STRING_VALUE, KEY)).isTrue();
    }

    @Test
    public void matches_stringValueRestriction_meetsRestriction_returnsTrue() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.stringValue().isEqualTo(STRING_VALUE);

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_STRING_VALUE, KEY)).isTrue();
    }

    @Test
    public void matches_stringValueRestriction_doesNotMeetRestriction_returnsFalse() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.stringValue().isEqualTo(DIFFERENT_STRING_VALUE);

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_STRING_VALUE, KEY)).isFalse();
    }

    @Test
    public void matches_existsRestriction_meetsRestriction_returnsTrue() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.exists();

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_STRING_VALUE, KEY)).isTrue();
    }

    @Test
    public void matches_existsRestriction_doesNotMeetRestriction_returnsFalse() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.exists();

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_STRING_VALUE, KEY2)).isFalse();
    }

    @Test
    public void matches_doesNotExistRestriction_meetsRestriction_returnsTrue() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.doesNotExist();

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_STRING_VALUE, KEY2)).isTrue();
    }

    @Test
    public void matches_doesNotExistRestriction_doesNotMeetRestriction_returnsFalse() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.doesNotExist();

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_STRING_VALUE, KEY)).isFalse();
    }

    @Test
    public void matches_integerValueRestriction_meetsRestriction_returnsTrue() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.integerValue().isEqualTo(INTEGER_VALUE);

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_INTEGER_VALUE, KEY)).isTrue();
    }

    @Test
    public void matches_integerValueRestriction_doesNotMeetRestriction_returnsFalse() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.integerValue().isEqualTo(DIFFERENT_INTEGER_VALUE);

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_INTEGER_VALUE, KEY)).isFalse();
    }

    @Test
    public void matches_longValueRestriction_meetsRestriction_returnsTrue() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.longValue().isEqualTo(LONG_VALUE);

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_LONG_VALUE, KEY)).isTrue();
    }

    @Test
    public void matches_longValueRestriction_doesNotMeetRestriction_returnsFalse() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.longValue().isEqualTo(DIFFERENT_LONG_VALUE);

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_LONG_VALUE, KEY)).isFalse();
    }

    @Test
    public void matches_booleanValueRestriction_meetsRestriction_returnsTrue() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.booleanValue().isTrue();

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_BOOLEAN_VALUE, KEY)).isTrue();
    }

    @Test
    public void matches_booleanValueRestriction_doesNotMeetRestriction_returnsFalse() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.booleanValue().isFalse();

        assertThat(metadataKeyQueryHelper.matches(METADATA_SET_BOOLEAN_VALUE, KEY)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        MetadataKeyQueryHelper<Queryable> metadataKeyQueryHelper =
                new MetadataKeyQueryHelper<>(mQuery);

        metadataKeyQueryHelper.exists();
        metadataKeyQueryHelper.stringValue().isEqualTo("");

        assertParcelsCorrectly(MetadataKeyQueryHelper.class, metadataKeyQueryHelper);
    }
}
