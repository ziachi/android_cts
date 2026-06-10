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
package com.android.cts.backportedfixes;

import com.android.build.backportedfixes.BackportedFix;
import com.android.build.backportedfixes.BackportedFixes;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import java.net.URL;
import java.util.Set;

/**
 * List of all known issues approved by Android Partner Engineering as a backported fix reportable
 * by  {@link android.os.Build#getBackportedFixStatus(long)}.
 *
 * The known issues are listed in cts/backported_fixes/approved/backported_fixes.txtpb
 */
public final class ApprovedBackportedFixes {
    private final ImmutableSet<Long> mAllIssues;
    private final ImmutableBiMap<Long, Integer> mId2Alias;

    private ApprovedBackportedFixes() {
        BackportedFixes fixes = readBackportedFixes();
        mAllIssues = fixes.getFixesList().stream().map(BackportedFix::getKnownIssue).collect(
                ImmutableSet.toImmutableSet());
        mId2Alias =
                fixes.getFixesList().stream()
                        .filter(fix -> fix.getAlias() > 0)
                        .collect(
                                ImmutableBiMap.toImmutableBiMap(
                                        BackportedFix::getKnownIssue, BackportedFix::getAlias));
    }


    /**
     * Returns the alias of a known issue.
     *
     * @param issueId The id of the known issue.
     * @return the alias or 0 if the issue does not have an alias or is not found.
     */
    public int getAlias(Long issueId) {
        Integer alias = mId2Alias.get(issueId);
        return alias == null ? 0 : alias;
    }

    /**
     * Returns the id of a known issue.
     *
     * @param alias The alias of the known issue.
     * @return the id or 0 if the alias is not found.
     */
    public long getId(int alias) {
        Long id = mId2Alias.inverse().get(alias);
        return id == null ? 0 : id;
    }

    public Set<Integer> getAllAliases() {
        return mId2Alias.values();
    }

    /** Returns the ids of all known issues approved as a backported fix. */
    public ImmutableSet<Long> getAllIssues() {
        return mAllIssues;
    }

    private static BackportedFixes readBackportedFixes() {
        try {
            URL configResource = ApprovedBackportedFixes.class.getResource(
                    "backported_fixes.binpb");
            var byteStream = Resources.toByteArray(configResource);
            return BackportedFixes.parseFrom(byteStream);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load list of approved backported fixes.", e);
        }
    }

    private static volatile ApprovedBackportedFixes sInstance;

    /** Get the singleton instance of {@code ApprovedBackportedFixes}. */
    public static ApprovedBackportedFixes getInstance() {
        if (sInstance == null) {
            synchronized (ApprovedBackportedFixes.class) {
                if (sInstance == null) {
                    sInstance = new ApprovedBackportedFixes();
                }
            }
        }
        return sInstance;
    }
}
