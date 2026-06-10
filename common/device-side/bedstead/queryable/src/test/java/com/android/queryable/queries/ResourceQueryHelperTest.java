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
import com.android.queryable.info.ResourceInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResourceQueryHelperTest {

    private final Queryable mQuery = null;
    private static final String XML_CONTENT =
            "<test-parent-tag xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                    + "    <test-tag>test-value</test-tag>\n"
                    + "</test-parent-tag>";
    private final ResourceInfo mResource = ResourceInfo.builder()
            .content(XML_CONTENT)
            .build();

    @Test
    public void matches_noRestrictions_returnsTrue() {
        ResourceQueryHelper<Queryable> resourceQueryHelper =
                new ResourceQueryHelper<>(mQuery);

        assertThat(resourceQueryHelper.matches(mResource)).isTrue();
    }

    @Test
    public void matches_asXml_meetsRestriction_returnsTrue() {
        ResourceQueryHelper<Queryable> resourceQueryHelper =
                new ResourceQueryHelper<>(mQuery);

        resourceQueryHelper.asXml().path("/test-tag").exists();

        assertThat(resourceQueryHelper.matches(mResource)).isTrue();
    }

    @Test
    public void matches_asXml_doesNotMeetRestriction_returnsFalse() {
        ResourceQueryHelper<Queryable> resourceQueryHelper =
                new ResourceQueryHelper<>(mQuery);

        resourceQueryHelper.asXml().path("/non-existing-tag").exists();

        assertThat(resourceQueryHelper.matches(mResource)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        ResourceQueryHelper<Queryable> resourceQueryHelper =
                new ResourceQueryHelper<>(mQuery);

        resourceQueryHelper.asXml();

        assertParcelsCorrectly(ResourceQueryHelper.class, resourceQueryHelper);
    }

}
