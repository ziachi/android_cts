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
import android.os.Bundle
import android.os.RemoteException
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.CloudMediaProviderContract
import android.provider.cts.cloudproviders.DefaultCloudMediaProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.providers.media.flags.Flags
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudMediaProviderTest {

    // Strings are internal only and not part of the public API
    val METHOD_GET_CAPABILITIES = "android:getCapabilities"
    val EXTRA_PROVIDER_CAPABILITIES = "android.provider.extra.PROVIDER_CAPABILITIES"

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(
      Flags.FLAG_ENABLE_CLOUD_MEDIA_PROVIDER_CAPABILITIES,
      Flags.FLAG_CLOUD_MEDIA_PROVIDER_SEARCH,
    )
    @Test
    fun testCloudMediaProviderDefaultCapabilities() {
        val context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        try {
            val client: ContentProviderClient =
                checkNotNull(
                    context.contentResolver.acquireContentProviderClient(
                        DefaultCloudMediaProvider.AUTHORITY
                    )
                ) {
                    "Unable to obtain a cloud provider client."
                }

            val response: Bundle =
                checkNotNull(client.call(METHOD_GET_CAPABILITIES, null, null)) {
                    "Returned bundle was null"
                }

            // This test runs back to R, and the new API isn't available until T.
            @Suppress("DEPRECATION")
            val capabilities: CloudMediaProviderContract.Capabilities? =
                response.getParcelable(EXTRA_PROVIDER_CAPABILITIES)

            assertWithMessage("Capabilities was not present in the returned bundle")
                .that(capabilities)
                .isNotNull()

            assertWithMessage("isSearchEnabled had unexpected default value.")
                .that(capabilities?.isSearchEnabled())
                .isFalse()

            assertWithMessage("isMediaCategoriesEnabled had unexpected default value.")
                .that(capabilities?.isMediaCategoriesEnabled())
                .isFalse()
        } catch (e: RemoteException) {
            throw AssertionError("The DefaultCloudMediaProvider threw an error.", e)
        }
    }

    @RequiresFlagsEnabled(
      Flags.FLAG_ENABLE_CLOUD_MEDIA_PROVIDER_CAPABILITIES,
      Flags.FLAG_CLOUD_MEDIA_PROVIDER_SEARCH,
    )
    @Test
    fun testCloudMediaProviderCapabilitiesBuilderDefaults() {
        val capabilities = CloudMediaProviderContract.Capabilities.Builder().build()

        assertWithMessage("isSearchEnabled had unexpected default value.")
            .that(capabilities.isSearchEnabled())
            .isFalse()

        assertWithMessage("isMediaCategoriesEnabled had unexpected default value.")
            .that(capabilities.isMediaCategoriesEnabled())
            .isFalse()
    }

    @RequiresFlagsEnabled(
      Flags.FLAG_ENABLE_CLOUD_MEDIA_PROVIDER_CAPABILITIES,
      Flags.FLAG_CLOUD_MEDIA_PROVIDER_SEARCH,
    )
    @Test
    fun testCloudMediaProviderCapabilitiesBuilderInverseDefaults() {
        val capabilities =
            CloudMediaProviderContract.Capabilities.Builder()
                .setSearchEnabled(true)
                .setMediaCategoriesEnabled(true)
                .build()

        assertWithMessage("isSearchEnabled had unexpected default value.")
            .that(capabilities.isSearchEnabled())
            .isTrue()

        assertWithMessage("isMediaCategoriesEnabled had unexpected default value.")
            .that(capabilities.isMediaCategoriesEnabled())
            .isTrue()
    }
}
