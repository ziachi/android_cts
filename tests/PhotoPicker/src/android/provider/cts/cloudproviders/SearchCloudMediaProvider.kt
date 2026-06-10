/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package android.provider.cts.cloudproviders

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Point
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.CloudMediaProvider

/**
 * Customisable [CloudMediaProvider] that captures input to search APIs. We can't use Mockito.spy()
 * to capture input to these methods because {@link CloudMediaProvider#query} is a final method.
 */
class SearchCloudMediaProvider : CloudMediaProvider() {

    companion object {
        const val AUTHORITY = "com.android.provider.cts.cloudproviders.search"

        var queryMediaCategoryInput: QueryMediaCategoryInput? = null
        var queryMediaSetsInput: QueryMediaSetsInput? = null
        var queryMediaInMediaSetsInput: QueryMediaInMediaSetsInput? = null
        var querySearchSuggestionsInput: QuerySearchSuggestionsInput? = null
        var querySearchMediaTextInput: QuerySearchMediaTextInput? = null
        var querySearchMediaSuggestionInput: QuerySearchMediaSuggestionInput? = null

        fun clear() {
            queryMediaCategoryInput = null
            queryMediaSetsInput = null
            queryMediaInMediaSetsInput = null
            querySearchSuggestionsInput = null
            querySearchMediaTextInput = null
            querySearchMediaSuggestionInput = null
        }
    }

    override fun onQueryMediaCategories(
        parentCategoryId: String?,
                                        extras: Bundle,
                                        cancellationSignal: CancellationSignal?
    ): Cursor {
        queryMediaCategoryInput = QueryMediaCategoryInput(parentCategoryId, extras,
            cancellationSignal)

        return super.onQueryMediaCategories(parentCategoryId, extras, cancellationSignal)
    }

    override fun onQueryMediaSets(
        mediaCategoryId: String,
                                  extras: Bundle,
                                  cancellationSignal: CancellationSignal?
    ): Cursor {
        queryMediaSetsInput = QueryMediaSetsInput(mediaCategoryId, extras, cancellationSignal)

        return super.onQueryMediaSets(mediaCategoryId, extras, cancellationSignal)
    }

    override fun onQueryMediaInMediaSet(
        mediaSetId: String,
                                        extras: Bundle,
                                        cancellationSignal: CancellationSignal?
    ): Cursor {
        queryMediaInMediaSetsInput = QueryMediaInMediaSetsInput(
            mediaSetId, extras, cancellationSignal)

        return super.onQueryMediaInMediaSet(mediaSetId, extras, cancellationSignal)
    }

    override fun onQuerySearchSuggestions(
        prefixText: String,
                                          extras: Bundle,
                                          cancellationSignal: CancellationSignal?
    ): Cursor {
        querySearchSuggestionsInput = QuerySearchSuggestionsInput(
            prefixText, extras, cancellationSignal)

        return super.onQuerySearchSuggestions(prefixText, extras, cancellationSignal)
    }

    override fun onSearchMedia(
        searchText: String,
                               extras: Bundle,
                               cancellationSignal: CancellationSignal?
    ): Cursor {
        querySearchMediaTextInput = QuerySearchMediaTextInput(
            searchText, extras, cancellationSignal)

        return super.onSearchMedia(searchText, extras, cancellationSignal)
    }

    override fun onSearchMedia(
        suggestedMediaSetId: String,
                               fallbackSearchText: String?,
                               extras: Bundle,
                               cancellationSignal: CancellationSignal?
    ): Cursor {
        querySearchMediaSuggestionInput = QuerySearchMediaSuggestionInput(
            suggestedMediaSetId, fallbackSearchText, extras, cancellationSignal)

        return super.onSearchMedia(
            suggestedMediaSetId,
            fallbackSearchText,
            extras,
            cancellationSignal
        )
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun onQueryMedia(extras: Bundle): Cursor {
        throw UnsupportedOperationException("onQueryMedia not supported")
    }

    override fun onQueryDeletedMedia(extras: Bundle): Cursor {
        throw UnsupportedOperationException("onQueryDeletedMedia not supported")
    }

    override fun onQueryAlbums(extras: Bundle): Cursor {
        throw UnsupportedOperationException("onQueryAlbums not supported")
    }

    override fun onOpenPreview(
        mediaId: String,
        size: Point,
        extras: Bundle?,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        throw UnsupportedOperationException("onOpenPreview not supported")
    }

    override fun onOpenMedia(
        mediaId: String,
        extras: Bundle?,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        throw UnsupportedOperationException("onOpenMedia not supported")
    }

    override fun onGetMediaCollectionInfo(extras: Bundle): Bundle {
        throw UnsupportedOperationException("onGetMediaCollectionInfo not supported")
    }
}

data class QueryMediaCategoryInput (
    val parentCategoryId: String?,
    val extras: Bundle,
    val cancellationSignal: CancellationSignal?
)

data class QueryMediaSetsInput (
    val mediaCategoryId: String,
    val extras: Bundle,
    val cancellationSignal: CancellationSignal?
)

data class QueryMediaInMediaSetsInput (
    val mediaSetId: String,
    val extras: Bundle,
    val cancellationSignal: CancellationSignal?
)

data class QuerySearchSuggestionsInput (
    val prefixText: String,
    val extras: Bundle,
    val cancellationSignal: CancellationSignal?
)

data class QuerySearchMediaTextInput (
    val searchText: String,
    val extras: Bundle,
    val cancellationSignal: CancellationSignal?
)

data class QuerySearchMediaSuggestionInput (
    val mediaSetId: String,
    val fallbackSearchText: String?,
    val extras: Bundle,
    val cancellationSignal: CancellationSignal?
)
