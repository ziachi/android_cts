/*
 * Copyright 2020 The Android Open Source Project
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

package android.app.appsearch.testutil;

import android.app.appsearch.stats.SchemaMigrationStats;

import com.android.server.appsearch.external.localstorage.AppSearchLogger;
import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.stats.InitializeStats;
import com.android.server.appsearch.external.localstorage.stats.OptimizeStats;
import com.android.server.appsearch.external.localstorage.stats.PutDocumentStats;
import com.android.server.appsearch.external.localstorage.stats.RemoveStats;
import com.android.server.appsearch.external.localstorage.stats.SearchSessionStats;
import com.android.server.appsearch.external.localstorage.stats.SearchStats;
import com.android.server.appsearch.external.localstorage.stats.SetSchemaStats;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-thread-safe simple logger implementation for testing.
 *
 * @hide
 */
public final class SimpleTestLogger implements AppSearchLogger {
    /** Holds {@link CallStats} after logging. */
    public @Nullable CallStats mCallStats;

    /** Holds {@link PutDocumentStats} after logging. */
    public @Nullable PutDocumentStats mPutDocumentStats;

    /** Holds {@link InitializeStats} after logging. */
    public @Nullable InitializeStats mInitializeStats;

    /** Holds {@link SearchStats} after logging. */
    public @Nullable SearchStats mSearchStats;

    /** Holds {@link RemoveStats} after logging. */
    public @Nullable RemoveStats mRemoveStats;

    /** Holds {@link OptimizeStats} after logging. */
    public @Nullable OptimizeStats mOptimizeStats;

    /** Holds {@link SetSchemaStats} after logging. */
    public @NonNull List<SetSchemaStats> mSetSchemaStats = new ArrayList<>();

    /** Holds {@link android.app.appsearch.stats.SchemaMigrationStats} after logging. */
    public @Nullable SchemaMigrationStats mSchemaMigrationStats;

    /** Holds {@link SearchSessionStats} after logging. */
    public @NonNull List<SearchSessionStats> mSearchSessionsStats = new ArrayList<>();

    @Override
    public void logStats(@NonNull CallStats stats) {
        mCallStats = stats;
    }

    @Override
    public void logStats(@NonNull PutDocumentStats stats) {
        mPutDocumentStats = stats;
    }

    @Override
    public void logStats(@NonNull InitializeStats stats) {
        mInitializeStats = stats;
    }

    @Override
    public void logStats(@NonNull SearchStats stats) {
        mSearchStats = stats;
    }

    @Override
    public void logStats(@NonNull RemoveStats stats) {
        mRemoveStats = stats;
    }

    @Override
    public void logStats(@NonNull OptimizeStats stats) {
        mOptimizeStats = stats;
    }

    @Override
    public void logStats(@NonNull SetSchemaStats stats) {
        mSetSchemaStats.add(stats);
    }

    @Override
    public void logStats(@NonNull SchemaMigrationStats stats) {
        mSchemaMigrationStats = stats;
    }

    @Override
    public void logStats(@NonNull List<SearchSessionStats> searchSessionsStats) {
        mSearchSessionsStats.addAll(searchSessionsStats);
    }
}
