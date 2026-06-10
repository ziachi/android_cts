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
package android.app.appfunctions.cts

import android.app.appsearch.GenericDocument
import android.app.appsearch.SearchResult
import android.app.appsearch.SearchResultsShim

object AppSearchUtils {
    fun collectAllSearchResults(searchResults: SearchResultsShim): List<GenericDocument> {
        val documents = mutableListOf<GenericDocument>()
        var results: List<SearchResult>
        do {
            results = searchResults.getNextPageAsync().get()
            for (result in results) {
                documents.add(result.genericDocument)
            }
        } while (results.isNotEmpty())
        return documents
    }
}
