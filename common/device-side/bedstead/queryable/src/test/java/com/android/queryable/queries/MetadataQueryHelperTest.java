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
import static com.android.queryable.queries.MetadataQuery.metadata;

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
public final class MetadataQueryHelperTest {

    private static final String KEY = "Key";
    private static final String STRING_VALUE = "value";
    private static final Set<MetadataInfo> EMPTY_METADATA_SET = Collections.emptySet();
    private static final Set<MetadataInfo> METADATA_SET = Collections.singleton(
            MetadataInfo.builder().key(KEY)
                    .value(MetadataValue.builder().value(STRING_VALUE).build())
                    .build());

    private final Queryable mQuery = null;

    @Test
    public void matches_noRestrictions_returnsTrue() {
        MetadataQueryHelper<Queryable> metadataQueryHelper = new MetadataQueryHelper<>(mQuery);

        assertThat(metadataQueryHelper.matches(EMPTY_METADATA_SET)).isTrue();
    }

    @Test
    public void matches_restrictionOnOneKey_restrictionIsMet_returnsTrue() {
        MetadataQueryHelper<Queryable> metadataQueryHelper = new MetadataQueryHelper<>(mQuery);

        metadataQueryHelper.key(KEY).exists();

        assertThat(metadataQueryHelper.matches(METADATA_SET)).isTrue();
    }

    @Test
    public void matches_restrictionOnOneKey_restrictionIsNotMet_returnsFalse() {
        MetadataQueryHelper<Queryable> metadataQueryHelper = new MetadataQueryHelper<>(mQuery);

        metadataQueryHelper.key(KEY).doesNotExist();

        assertThat(metadataQueryHelper.matches(METADATA_SET)).isFalse();
    }

    @Test
    public void matches_restrictionOnNonExistingKey_returnsFalse() {
        MetadataQueryHelper<Queryable> metadataQueryHelper = new MetadataQueryHelper<>(mQuery);

        metadataQueryHelper.key(KEY).stringValue().isEqualTo(STRING_VALUE);

        assertThat(metadataQueryHelper.matches(EMPTY_METADATA_SET)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        MetadataQueryHelper<Queryable> metadataQueryHelper = new MetadataQueryHelper<>(mQuery);

        metadataQueryHelper.key(KEY).stringValue().isEqualTo(STRING_VALUE);

        assertParcelsCorrectly(MetadataQueryHelper.class, metadataQueryHelper);
    }

    @Test
    public void metadataQueryHelperBase_queries() {
        assertThat(
                metadata().where().key(KEY).exists().where().key(KEY).stringValue().isEqualTo(
                        STRING_VALUE).matches(METADATA_SET)).isTrue();
    }

}
