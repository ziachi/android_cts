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
public final class XmlPathQueryHelperTest {

    private final Queryable mQuery = null;
    private static final String XML_CONTENT =
            "<test-parent-tag>\n"
                    + "    <test-tag>test-value</test-tag>\n"
                    + "    <headless-system-user device-owner-mode=\"main_user\"\n />"
                    + "</test-parent-tag>";
    private static final String INVALID_XML_CONTENT = "";

    private final ResourceInfo mResource = ResourceInfo.builder()
            .content(XML_CONTENT)
            .build();
    private final ResourceInfo mInvalidResource = ResourceInfo.builder()
            .content(INVALID_XML_CONTENT)
            .build();

    @Test
    public void matches_noRestrictions_returnsTrue() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/test-tag");

        assertThat(xmlPathQueryHelper.matches(mResource)).isTrue();
    }
    @Test
    public void matches_existsRestriction_meetsRestriction_returnsTrue() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/test-tag");

        xmlPathQueryHelper.exists();

        assertThat(xmlPathQueryHelper.matches(mResource)).isTrue();
    }

    @Test
    public void matches_existsRestriction_doesNotMeetRestriction_returnsFalse() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/non-existing-tag");

        xmlPathQueryHelper.exists();

        assertThat(xmlPathQueryHelper.matches(mResource)).isFalse();
    }

    @Test
    public void matches_doesNotExistRestriction_meetsRestriction_returnsTrue() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/non-existing-tag");

        xmlPathQueryHelper.doesNotExist();

        assertThat(xmlPathQueryHelper.matches(mResource)).isTrue();
    }

    @Test
    public void matches_doesNotExistRestriction_doesNotMeetRestriction_returnsFalse() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/test-tag");

        xmlPathQueryHelper.doesNotExist();

        assertThat(xmlPathQueryHelper.matches(mResource)).isFalse();
    }

    @Test
    public void matches_asTextRestriction_meetsRestriction_returnsTrue() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/test-tag");

        xmlPathQueryHelper.asText().isEqualTo("test-value");

        assertThat(xmlPathQueryHelper.matches(mResource)).isTrue();
    }

    @Test
    public void matches_asTextRestriction_doesNotMeetRestriction_returnsFalse() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/test-tag");

        xmlPathQueryHelper.asText().isEqualTo("non-existing-value");

        assertThat(xmlPathQueryHelper.matches(mResource)).isFalse();
    }

    @Test
    public void matches_attributeRestriction_meetsRestriction_returnsTrue() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "test-parent-tag/headless-system-user");

        xmlPathQueryHelper.attribute("device-owner-mode").value().isEqualTo("main_user");

        assertThat(xmlPathQueryHelper.matches(mResource)).isTrue();
    }

    @Test
    public void matches_multiplePaths_meetsRestriction_returnsTrue() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/test-tag");

        xmlPathQueryHelper.asText().isEqualTo("non-existing-value");

        assertThat(xmlPathQueryHelper.matches(mResource)).isFalse();
    }

    @Test
    public void matches_invalidXml_returnsFalse() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "/test-tag");

        xmlPathQueryHelper.exists();

        assertThat(xmlPathQueryHelper.matches(mInvalidResource)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        XmlPathQueryHelper<Queryable> xmlPathQueryHelper =
                new XmlPathQueryHelper<>(mQuery, "");

        xmlPathQueryHelper.exists();
        xmlPathQueryHelper.asText();

        assertParcelsCorrectly(XmlPathQueryHelper.class, xmlPathQueryHelper);
    }
}
