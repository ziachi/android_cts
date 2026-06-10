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

package android.app.appsearch.testutil;

import android.annotation.NonNull;
import android.app.appsearch.SearchResult;
import android.app.appsearch.exceptions.AppSearchException;

import com.android.server.appsearch.appsindexer.SyncSearchResults;

import java.util.ArrayList;
import java.util.List;

/** Utility class for AppSearch Framework tests. */
public final class AppSearchFrameworkTestUtils {
    private AppSearchFrameworkTestUtils() {}

    /** Extracts all {@link SearchResult} from {@link SyncSearchResults}. */
    @NonNull
    public static List<SearchResult> retrieveAllSearchResults(
            @NonNull SyncSearchResults searchResults) throws AppSearchException {
        List<SearchResult> page = searchResults.getNextPage();
        List<SearchResult> results = new ArrayList<>();
        while (!page.isEmpty()) {
            results.addAll(page);
            page = searchResults.getNextPage();
        }
        return results;
    }
}
