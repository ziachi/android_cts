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
package android.content.cts;

import static android.content.UriRelativeFilterGroup.ACTION_ALLOW;
import static android.content.UriRelativeFilterGroup.ACTION_BLOCK;
import static android.os.PatternMatcher.PATTERN_LITERAL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.UriRelativeFilter;
import android.content.UriRelativeFilterGroup;
import android.content.pm.Flags;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UriRelativeFilterGroupTest {
    private static final String DOMAIN = "https://testhost";
    private static final String PATH = "/testpath";
    private static final String QUERY = "query=test";
    private static final String FRAGMENT = "testfragment";

    private static final int PATH_MASK = 1;
    private static final int QUERY_MASK = 1 << 1;
    private static final int FRAGMENT_MASK = 1 << 2;

    private List<UriRelativeFilterGroup> mGroups = new ArrayList<>();

    @Before
    public void clearGroups() {
        mGroups.clear();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    public void testGroupMatch() {
        for (int i = 1; i <= 7; i++) {
            UriRelativeFilterGroup group = maskToGroup(ACTION_ALLOW, i);
            for (int j = 0; j <= 7; j++) {
                Uri uri = maskToUri(j);
                if ((i & j) == i) {
                    assertTrue(group + " should match " + uri, group.matchData(uri));
                } else {
                    assertFalse(group + " should not match " + uri, group.matchData(uri));
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    public void testMatchGroupsToUri_matchAnyGroup() {
        mGroups.add(maskToGroup(ACTION_ALLOW, PATH_MASK));
        mGroups.add(maskToGroup(ACTION_ALLOW, QUERY_MASK));
        mGroups.add(maskToGroup(ACTION_ALLOW, FRAGMENT_MASK));
        assertTrue(UriRelativeFilterGroup.matchGroupsToUri(mGroups, maskToUri(PATH_MASK)));
        assertTrue(UriRelativeFilterGroup.matchGroupsToUri(mGroups, maskToUri(QUERY_MASK)));
        assertTrue(UriRelativeFilterGroup.matchGroupsToUri(mGroups, maskToUri(FRAGMENT_MASK)));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    public void testMachGroupsToUri_blockMatch() {
        mGroups.add(maskToGroup(ACTION_BLOCK, PATH_MASK));
        mGroups.add(maskToGroup(ACTION_ALLOW, PATH_MASK | QUERY_MASK));
        mGroups.add(maskToGroup(ACTION_ALLOW, FRAGMENT_MASK));
        assertFalse(UriRelativeFilterGroup.matchGroupsToUri(mGroups, maskToUri(PATH_MASK)));
        assertFalse(UriRelativeFilterGroup.matchGroupsToUri(mGroups,
                maskToUri(PATH_MASK | QUERY_MASK)));
        assertTrue(UriRelativeFilterGroup.matchGroupsToUri(mGroups, maskToUri(FRAGMENT_MASK)));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    public void testMatchGroupsToUri_ignoreNonMatchingBlocks() {
        mGroups.add(maskToGroup(ACTION_BLOCK, PATH_MASK));
        mGroups.add(maskToGroup(ACTION_ALLOW, FRAGMENT_MASK));
        assertFalse(UriRelativeFilterGroup.matchGroupsToUri(mGroups, maskToUri(PATH_MASK)));
        assertTrue(UriRelativeFilterGroup.matchGroupsToUri(mGroups, maskToUri(FRAGMENT_MASK)));
    }

    private Uri maskToUri(int mask) {
        String uri = DOMAIN;
        if ((mask & PATH_MASK) > 0) {
            uri += PATH;
        }
        if ((mask & QUERY_MASK) > 0) {
            uri += "?" + QUERY;
        }
        if ((mask & FRAGMENT_MASK) > 0) {
            uri += "#" + FRAGMENT;
        }
        return Uri.parse(uri);
    }

    private UriRelativeFilterGroup maskToGroup(int action, int mask) {
        UriRelativeFilterGroup group = new UriRelativeFilterGroup(action);
        if ((mask & PATH_MASK) > 0) {
            group.addUriRelativeFilter(
                    new UriRelativeFilter(UriRelativeFilter.PATH, PATTERN_LITERAL, PATH));
        }
        if ((mask & QUERY_MASK) > 0) {
            group.addUriRelativeFilter(
                    new UriRelativeFilter(UriRelativeFilter.QUERY, PATTERN_LITERAL, QUERY));
        }
        if ((mask & FRAGMENT_MASK) > 0) {
            group.addUriRelativeFilter(
                    new UriRelativeFilter(UriRelativeFilter.FRAGMENT, PATTERN_LITERAL, FRAGMENT));
        }
        return group;
    }
}
