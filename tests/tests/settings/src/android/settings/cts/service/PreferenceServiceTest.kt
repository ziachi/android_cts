/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.settings.cts.service

import android.Manifest
import android.content.Context
import android.os.OutcomeReceiver
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.service.settings.preferences.GetValueRequest
import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.MetadataRequest
import android.service.settings.preferences.MetadataResult
import android.service.settings.preferences.SetValueRequest
import android.service.settings.preferences.SetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceServiceClient
import android.service.settings.preferences.SettingsPreferenceValue
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireHandheldDevice
import com.android.bedstead.nene.TestApis
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.android.settingslib.flags.Flags.FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED
import com.google.common.truth.Truth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.use
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST, FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED)
class PreferenceServiceTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @RequireHandheldDevice
    fun getAllPreferenceMetadata_retrievesPopulatedValidResult() {
        val possibleSensitivities = setOf(
            SettingsPreferenceMetadata.NO_SENSITIVITY,
            SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION,
            SettingsPreferenceMetadata.DEEPLINK_ONLY,
            SettingsPreferenceMetadata.NO_DIRECT_ACCESS
        )
        Truth.assertThat(metadata).isNotEmpty()
        Truth.assertThat(metadata.all { it.writeSensitivity in possibleSensitivities }).isTrue()
        Truth.assertThat(metadata.none { it.screenKey.isEmpty() }).isTrue()
        Truth.assertThat(
            metadata.any {
                !it.title.isNullOrEmpty() && it.isEnabled
            }
        ).isTrue()
    }

    @Test
    @RequireHandheldDevice
    fun getPreferenceValue_writablePreference_valueRetrievable() {
        val possibleTypes = setOf(
            SettingsPreferenceValue.TYPE_BOOLEAN,
            SettingsPreferenceValue.TYPE_DOUBLE,
            SettingsPreferenceValue.TYPE_INT,
            SettingsPreferenceValue.TYPE_LONG,
            SettingsPreferenceValue.TYPE_STRING,
        )
        // We assume a value that is writable is also readable, as it doesn't seem safe
        // to write blindly.
        val pref = metadata.firstOrNull { it.isWritable }
        if (pref == null) return
        val statusLatch = CountDownLatch(1)
        val permissions = pref.readPermissions + Manifest.permission.READ_SYSTEM_PREFERENCES
        TestApis.permissions().withPermission(*permissions.toTypedArray()).use {
            client.getPreferenceValue(
                GetValueRequest.Builder(pref.screenKey, pref.key).build(),
                context.mainExecutor
            ) { result ->
                Truth.assertThat(result.resultCode).isEqualTo(GetValueResult.RESULT_OK)
                Truth.assertThat(result.metadata).isNotNull()
                Truth.assertThat(result.value).isNotNull()
                Truth.assertThat(result.value!!.type in possibleTypes).isTrue()
                if (result.value!!.type == SettingsPreferenceValue.TYPE_STRING) {
                    Truth.assertThat(result.value!!.stringValue).isNotNull()
                }
                statusLatch.countDown()
            }
            Truth.assertThat(statusLatch.await(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    @RequireHandheldDevice
    fun setPreferenceValue_highSensitivityPreferences_failToWrite() {
        val prefs = metadata.filter {
            it.isWritable && it.writeSensitivity in setOf(
                SettingsPreferenceMetadata.DEEPLINK_ONLY,
                SettingsPreferenceMetadata.NO_DIRECT_ACCESS
            )
        }
        for (pref in prefs) {
            val statusLatch = CountDownLatch(1)
            val permissions = pref.writePermissions + Manifest.permission.READ_SYSTEM_PREFERENCES +
                    Manifest.permission.WRITE_SYSTEM_PREFERENCES
            TestApis.permissions().withPermission(*permissions.toTypedArray()).use {
                client.setPreferenceValue(
                    SetValueRequest.Builder(
                        pref.screenKey,
                        pref.key,
                        // only verifying boolean preferences, as we don't know the type information
                        // without first calling GET
                        SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_BOOLEAN)
                            .setBooleanValue(true)
                            .build()
                    ).build(),
                    context.mainExecutor,
                    object : OutcomeReceiver<SetValueResult, Exception> {
                        override fun onResult(result: SetValueResult) {
                            if (result.resultCode == SetValueResult.RESULT_OK) {
                                throw AssertionError(
                                    "Able to write to sensitive preference: ${pref.key}"
                                )
                            }
                            statusLatch.countDown()
                        }
                        override fun onError(error: Exception) {
                            throw AssertionError(
                                "Error callback reached for setting preference ${pref.key}",
                                error
                            )
                        }
                    }
                )
                Truth.assertThat(statusLatch.await(5, TimeUnit.SECONDS)).isTrue()
            }
        }
    }

    companion object {
        @get:ClassRule @get:Rule
        val deviceState = DeviceState()

        // share state for tests as these should not change
        private lateinit var context: Context
        private lateinit var client: SettingsPreferenceServiceClient
        private lateinit var metadata: List<SettingsPreferenceMetadata>

        @com.android.bedstead.harrier.annotations.BeforeClass
        @RequireHandheldDevice
        @JvmStatic
        fun setup() {
            val bindingLatch = CountDownLatch(1)
            val metadataLatch = CountDownLatch(1)
            context = InstrumentationRegistry.getInstrumentation().context
            TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
                client = SettingsPreferenceServiceClient(
                    context,
                    "com.android.settings",
                    false,
                    context.mainExecutor,
                    object : OutcomeReceiver<SettingsPreferenceServiceClient, Exception> {
                        override fun onResult(result: SettingsPreferenceServiceClient) {
                            bindingLatch.countDown()
                        }

                        override fun onError(error: Exception) {
                            throw AssertionError("Binding failed")
                        }
                    }
                )
                if (!bindingLatch.await(5, TimeUnit.SECONDS)) {
                    throw AssertionError("Binding timeout")
                }

                client.getAllPreferenceMetadata(
                    MetadataRequest.Builder().build(),
                    context.mainExecutor,
                    object : OutcomeReceiver<MetadataResult, Exception> {
                        override fun onResult(result: MetadataResult) {
                            if (result.resultCode != MetadataResult.RESULT_OK ||
                                result.metadataList.isEmpty()) {
                                throw AssertionError("No metadata")
                            }
                            Truth.assertThat(result.resultCode).isEqualTo(MetadataResult.RESULT_OK)
                            metadata = result.metadataList
                            metadataLatch.countDown()
                        }
                        override fun onError(error: Exception) {
                            throw AssertionError("Metadata request error")
                        }
                    }
                )
                // populating metadata can be fairly slow, give large buffer
                if (!metadataLatch.await(10, TimeUnit.SECONDS)) {
                    throw AssertionError("Metadata retrieval timeout")
                }
            }
        }

        @com.android.bedstead.harrier.annotations.AfterClass
        @RequireHandheldDevice
        @JvmStatic
        fun teardown() {
            client.close()
        }
    }
}
