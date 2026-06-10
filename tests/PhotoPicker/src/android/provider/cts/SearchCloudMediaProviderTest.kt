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

package android.provider.cts

import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.CloudMediaProviderContract
import android.provider.cts.cloudproviders.SearchCloudMediaProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.providers.media.flags.Flags
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times

@RequiresFlagsEnabled(Flags.FLAG_CLOUD_MEDIA_PROVIDER_SEARCH)
@RunWith(AndroidJUnit4::class)
class SearchCloudMediaProviderTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {
        private const val PARENT_CATEGORY_ID_KEY = "parent_category_id"
        private const val MEDIA_CATEGORY_ID_KEY = "media_category_id"
        private const val MEDIA_SET_ID_KEY = "media_set_id"
        private const val SEARCH_TEXT_KEY = "search_text"
        private const val PREFIX_TEXT_KEY = "prefix_text"
    }

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()

        SearchCloudMediaProvider.clear()
    }

    @Test
    fun testOnQueryMediaCategories() {
        val parentCategoryId: String = "category-id"
        val extras: Bundle = Bundle()
        extras.putString(PARENT_CATEGORY_ID_KEY, parentCategoryId)
        val cancellationSignal = CancellationSignal()

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        client.use {
            assertThrows(java.lang.UnsupportedOperationException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/media_category",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        cancellationSignal
                    )
                }
            }

            assertWithMessage("Query was not routed to onQueryMediaCategories")
                .that(SearchCloudMediaProvider.queryMediaCategoryInput)
                .isNotNull()
            assertWithMessage("Input parent category Id is not as expected")
                .that(SearchCloudMediaProvider.queryMediaCategoryInput?.parentCategoryId)
                .isEqualTo(parentCategoryId)
            assertWithMessage("Input extras is not as expected")
                .that(SearchCloudMediaProvider.queryMediaCategoryInput?.extras)
                .isEqualTo(extras)
            assertWithMessage("Input extras is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaCategoryInput?.extras
                    ?.getString(PARENT_CATEGORY_ID_KEY)
                )
                .isNull()
        }
    }

    @Test
    fun testOnQueryMediaSets() {
        val parentCategoryId: String = "category-id"
        val pageToken: String = "token"
        val pageSize: Int = 1000
        val mimeTypes: Array<String> = arrayOf("image/*", "video/*")
        val cancellationSignal = CancellationSignal()

        val extras: Bundle = Bundle()
        extras.putString(MEDIA_CATEGORY_ID_KEY, parentCategoryId)
        extras.putString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN, pageToken)
        extras.putInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE, pageSize)
        extras.putStringArray(Intent.EXTRA_MIME_TYPES, mimeTypes)

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        client.use {
            assertThrows(java.lang.UnsupportedOperationException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/media_set",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        cancellationSignal
                    )
                }
            }

            assertWithMessage("Query was not routed to onQueryMediaSets")
                .that(SearchCloudMediaProvider.queryMediaSetsInput)
                .isNotNull()
            assertWithMessage("Input parent category Id is not as expected")
                .that(SearchCloudMediaProvider.queryMediaSetsInput?.mediaCategoryId)
                .isEqualTo(parentCategoryId)
            assertWithMessage("Input extras are null")
                .that(SearchCloudMediaProvider.queryMediaSetsInput?.extras)
                .isNotNull()
            assertWithMessage("Category id is not as expected to be in extras")
                .that(
                    SearchCloudMediaProvider.queryMediaSetsInput?.extras
                    ?.getString(MEDIA_CATEGORY_ID_KEY)
                )
                .isNull()
            assertWithMessage("Page token is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaSetsInput?.extras
                    ?.getString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN)
                )
                .isEqualTo(pageToken)
            assertWithMessage("Page size is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaSetsInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE)
                )
                .isEqualTo(pageSize)
            assertWithMessage("Mime types is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaSetsInput?.extras
                    ?.getStringArray(Intent.EXTRA_MIME_TYPES)
                )
                .isEqualTo(mimeTypes)
        }
    }

    @Test
    fun testOnQueryMediaSetsInvalidInput() {
        val extras: Bundle = Bundle()

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        // Assert that an exception is thrown if the parent category id is not provided
        client.use {
            assertThrows(java.lang.NullPointerException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/media_set",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        null
                    )
                }
            }
        }
    }

    @Test
    fun testOnQueryMediaInMediaSets() {
        val mediaSetId: String = "media-set-id"
        val cancellationSignal = CancellationSignal()
        val pageToken: String = "token"
        val pageSize: Int = 1000
        val mimeTypes: Array<String> = arrayOf("image/*", "video/*")

        val extras: Bundle = Bundle()
        extras.putString(MEDIA_SET_ID_KEY, mediaSetId)
        extras.putString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN, pageToken)
        extras.putInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE, pageSize)
        extras.putInt(
            CloudMediaProviderContract.EXTRA_SORT_ORDER,
            CloudMediaProviderContract.SORT_ORDER_DESC_DATE_TAKEN
        )
        extras.putStringArray(Intent.EXTRA_MIME_TYPES, mimeTypes)

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        client.use {
            assertThrows(java.lang.UnsupportedOperationException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/query_media_in_media_set",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        cancellationSignal
                    )
                }
            }

            assertWithMessage("Query was not routed to onQueryMediaInMediaSets")
                .that(SearchCloudMediaProvider.queryMediaInMediaSetsInput)
                .isNotNull()
            assertWithMessage("Input media set Id is not as expected")
                .that(SearchCloudMediaProvider.queryMediaInMediaSetsInput?.mediaSetId)
                .isEqualTo(mediaSetId)
            assertWithMessage("Input extras are null")
                .that(SearchCloudMediaProvider.queryMediaInMediaSetsInput?.extras)
                .isNotNull()
            assertWithMessage("Input extras is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaInMediaSetsInput?.extras
                    ?.getString(MEDIA_SET_ID_KEY)
                )
                .isNull()
            assertWithMessage("Page token is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaInMediaSetsInput?.extras
                    ?.getString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN)
                )
                .isEqualTo(pageToken)
            assertWithMessage("Page size is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaInMediaSetsInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE)
                )
                .isEqualTo(pageSize)
            assertWithMessage("Sort order is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaInMediaSetsInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_SORT_ORDER)
                )
                .isEqualTo(CloudMediaProviderContract.SORT_ORDER_DESC_DATE_TAKEN)
            assertWithMessage("Mime types is not as expected")
                .that(
                    SearchCloudMediaProvider.queryMediaInMediaSetsInput?.extras
                    ?.getStringArray(Intent.EXTRA_MIME_TYPES)
                )
                .isEqualTo(mimeTypes)
        }
    }

    @Test
    fun testOnQueryMediaInMediaSetsInvalidInput() {
        val extras: Bundle = Bundle()

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        // Assert that an exception is thrown if media set id is not provided
        client.use {
            assertThrows(java.lang.NullPointerException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/query_media_in_media_set",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        null
                    )
                }
            }
        }
    }

    @Test
    fun testOnQuerySearchSuggestions() {
        val prefixText: String = ""
        val pageSize: Int = 1000
        val cancellationSignal = CancellationSignal()

        val extras: Bundle = Bundle()
        extras.putString(PREFIX_TEXT_KEY, prefixText)
        extras.putInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE, pageSize)

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        client.use {
            assertThrows(java.lang.UnsupportedOperationException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/search_suggestion",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        cancellationSignal
                    )
                }
            }

            assertWithMessage("Query was not routed to onQuerySearchSuggestions")
                .that(SearchCloudMediaProvider.querySearchSuggestionsInput)
                .isNotNull()
            assertWithMessage("Input prefix text is not as expected")
                .that(SearchCloudMediaProvider.querySearchSuggestionsInput?.prefixText)
                .isEqualTo(prefixText)
            assertWithMessage("Input extras are null")
                .that(SearchCloudMediaProvider.querySearchSuggestionsInput?.extras)
                .isNotNull()
            assertWithMessage("Input extras is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchSuggestionsInput?.extras
                    ?.getString(PREFIX_TEXT_KEY)
                )
                .isNull()
            assertWithMessage("Page size is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchSuggestionsInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE)
                )
                .isEqualTo(pageSize)
        }
    }

    @Test
    fun testOnQuerySearchSuggestionsInvalidInput() {
        val extras: Bundle = Bundle()

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        // Assert that an exception is thrown if prefix text is not provided
        client.use {
            assertThrows(java.lang.NullPointerException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/search_suggestion",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        null
                    )
                }
            }
        }
    }

    @Test
    fun testOnSearchMediaWithText() {
        val searchText: String = "volcano"
        val cancellationSignal = CancellationSignal()
        val pageToken: String = "token"
        val pageSize: Int = 1000
        val mimeTypes: Array<String> = arrayOf("image/*", "video/*")

        val extras: Bundle = Bundle()
        extras.putString(SEARCH_TEXT_KEY, searchText)
        extras.putString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN, pageToken)
        extras.putInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE, pageSize)
        extras.putInt(
            CloudMediaProviderContract.EXTRA_SORT_ORDER,
            CloudMediaProviderContract.SORT_ORDER_DESC_DATE_TAKEN
        )
        extras.putStringArray(Intent.EXTRA_MIME_TYPES, mimeTypes)

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        client.use {
            assertThrows(java.lang.UnsupportedOperationException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/search_media",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        cancellationSignal
                    )
                }
            }

            assertWithMessage("Query was not routed to onSearchMedia for search text query path")
                .that(SearchCloudMediaProvider.querySearchMediaTextInput)
                .isNotNull()
            assertWithMessage("Input search text is not as expected")
                .that(SearchCloudMediaProvider.querySearchMediaTextInput?.searchText)
                .isEqualTo(searchText)
            assertWithMessage("Input extras are null")
                .that(SearchCloudMediaProvider.querySearchMediaTextInput?.extras)
                .isNotNull()
            assertWithMessage("Input extras is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaTextInput?.extras
                    ?.getString(SEARCH_TEXT_KEY)
                )
                .isNull()
            assertWithMessage("Page token is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaTextInput?.extras
                    ?.getString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN)
                )
                .isEqualTo(pageToken)
            assertWithMessage("Page size is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaTextInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE)
                )
                .isEqualTo(pageSize)
            assertWithMessage("Sort order is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaTextInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_SORT_ORDER)
                )
                .isEqualTo(CloudMediaProviderContract.SORT_ORDER_DESC_DATE_TAKEN)
            assertWithMessage("Mime types is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaTextInput?.extras
                    ?.getStringArray(Intent.EXTRA_MIME_TYPES)
                )
                .isEqualTo(mimeTypes)
        }
    }

    @Test
    fun testOnSearchMediaWithSuggestion() {
        val searchText: String = "volcano"
        val mediaSetId: String = "volcano-media-set-id"
        val cancellationSignal = CancellationSignal()
        val pageToken: String = "token"
        val pageSize: Int = 1000
        val mimeTypes: Array<String> = arrayOf("image/*", "video/*")

        val extras: Bundle = Bundle()
        extras.putString(SEARCH_TEXT_KEY, searchText)
        extras.putString(MEDIA_SET_ID_KEY, mediaSetId)
        extras.putString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN, pageToken)
        extras.putInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE, pageSize)
        extras.putInt(
            CloudMediaProviderContract.EXTRA_SORT_ORDER,
            CloudMediaProviderContract.SORT_ORDER_DESC_DATE_TAKEN
        )
        extras.putStringArray(Intent.EXTRA_MIME_TYPES, mimeTypes)

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        client.use {
            assertThrows(java.lang.UnsupportedOperationException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/search_media",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        cancellationSignal
                    )
                }
            }

            assertWithMessage(
                "Query was not routed to onSearchMedia for " +
                    "search suggestion query path"
            )
                .that(SearchCloudMediaProvider.querySearchMediaSuggestionInput)
                .isNotNull()
            assertWithMessage("Input search text is not as expected")
                .that(SearchCloudMediaProvider.querySearchMediaSuggestionInput?.fallbackSearchText)
                .isEqualTo(searchText)
            assertWithMessage("Input search media set id is not as expected")
                .that(SearchCloudMediaProvider.querySearchMediaSuggestionInput?.mediaSetId)
                .isEqualTo(mediaSetId)
            assertWithMessage("Input extras are null")
                .that(SearchCloudMediaProvider.querySearchMediaSuggestionInput?.extras)
                .isNotNull()
            assertWithMessage("Input extras is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaSuggestionInput?.extras
                    ?.getString(SEARCH_TEXT_KEY)
                )
                .isNull()
            assertWithMessage("Page token is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaSuggestionInput?.extras
                    ?.getString(CloudMediaProviderContract.EXTRA_PAGE_TOKEN)
                )
                .isEqualTo(pageToken)
            assertWithMessage("Page size is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaSuggestionInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE)
                )
                .isEqualTo(pageSize)
            assertWithMessage("Sort order is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaSuggestionInput?.extras
                    ?.getInt(CloudMediaProviderContract.EXTRA_SORT_ORDER)
                )
                .isEqualTo(CloudMediaProviderContract.SORT_ORDER_DESC_DATE_TAKEN)
            assertWithMessage("Mime types is not as expected")
                .that(
                    SearchCloudMediaProvider.querySearchMediaSuggestionInput?.extras
                    ?.getStringArray(Intent.EXTRA_MIME_TYPES)
                )
                .isEqualTo(mimeTypes)
        }
    }

    @Test
    fun testOnSearchMediaInvalidInput() {
        val extras: Bundle = Bundle()

        val client: ContentProviderClient? =
            context.contentResolver.acquireContentProviderClient(
                SearchCloudMediaProvider.AUTHORITY
            )

        assertWithMessage("Unable to obtain a cloud provider client for search API tests.")
            .that(client)
            .isNotNull()

        // Assert that an exception is thrown if search text and search media id both are
        // not provided
        client.use {
            assertThrows(java.lang.IllegalArgumentException::class.java) {
                runBlocking {
                    client!!.query(
                        Uri.parse(String.format(
                            Locale.ROOT,
                            "content://%s/search_media",
                            SearchCloudMediaProvider.AUTHORITY
                        )),
                        null,
                        extras,
                        null
                    )
                }
            }
        }
    }
}
