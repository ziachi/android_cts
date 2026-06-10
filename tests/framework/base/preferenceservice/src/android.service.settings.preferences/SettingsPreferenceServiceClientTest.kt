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

package android.service.settings.preferences

import android.Manifest
import android.content.Context
import android.os.OutcomeReceiver
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.android.settingslib.flags.Flags.FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED
import com.google.common.truth.Truth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class SettingsPreferenceServiceClientTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var context: Context
    private lateinit var client: SettingsPreferenceServiceClient

    @Before
    fun setup() {
        val connectionLatch = CountDownLatch(1)
        context = InstrumentationRegistry.getInstrumentation().context
        TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
            client = SettingsPreferenceServiceClient(
                context,
                "android.service.settings.preferences.cts",
                false,
                context.mainExecutor,
                object : OutcomeReceiver<SettingsPreferenceServiceClient, Exception> {
                    override fun onResult(result: SettingsPreferenceServiceClient) {
                        connectionLatch.countDown()
                    }

                    override fun onError(error: Exception) {
                        throw AssertionError("Binding failed")
                    }
                }
            )
            if (!connectionLatch.await(1, TimeUnit.SECONDS)) {
                throw AssertionError("Binding timeout")
            }
        }
    }

    @After
    fun teardown() {
        client.close()
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @Test
    fun verifyConstructor_nonSystemApp_noBinding() {
        val connectionLatch = CountDownLatch(1)
        TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
            client = SettingsPreferenceServiceClient(
                context,
                "android.service.settings.preferences.cts",
                context.mainExecutor,
                object : OutcomeReceiver<SettingsPreferenceServiceClient, Exception> {
                    override fun onResult(result: SettingsPreferenceServiceClient) {
                        throw AssertionError("Invalid binding")
                    }

                    override fun onError(error: Exception) {
                        connectionLatch.countDown()
                    }
                }
            )
            if (!connectionLatch.await(1, TimeUnit.SECONDS)) {
                throw AssertionError("Binding timeout")
            }
        }
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @Test
    fun getAllPreferenceMetadata_retrievesResult() {
        val statusLatch = CountDownLatch(1)
        TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
            client.getAllPreferenceMetadata(
                MetadataRequest.Builder().build(),
                context.mainExecutor
            ) { result ->
                Truth.assertThat(result.resultCode).isEqualTo(MetadataResult.RESULT_OK)
                Truth.assertThat(result.metadataList).isNotEmpty()
                statusLatch.countDown()
            }
        }
        Truth.assertThat(statusLatch.await(1, TimeUnit.SECONDS)).isTrue()
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @Test
    fun getPreferenceValue_retrievesResult() {
        val statusLatch = CountDownLatch(1)
        TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
            client.getPreferenceValue(
                GetValueRequest.Builder("s", "k").build(),
                context.mainExecutor
            ) { result ->
                Truth.assertThat(result.resultCode).isEqualTo(MetadataResult.RESULT_OK)
                Truth.assertThat(result.metadata).isNotNull()
                Truth.assertThat(result.value).isNotNull()
                statusLatch.countDown()
            }
        }
        Truth.assertThat(statusLatch.await(1, TimeUnit.SECONDS)).isTrue()
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @Test
    fun getPreferenceValue_unsupportedKey_retrievesNoResult() {
        val statusLatch = CountDownLatch(1)
        TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
            client.getPreferenceValue(
                GetValueRequest.Builder("invalid", "invalid").build(),
                context.mainExecutor
            ) { result ->
                Truth.assertThat(result.resultCode).isEqualTo(MetadataResult.RESULT_UNSUPPORTED)
                Truth.assertThat(result.metadata).isNull()
                Truth.assertThat(result.value).isNull()
                statusLatch.countDown()
            }
        }
        Truth.assertThat(statusLatch.await(1, TimeUnit.SECONDS)).isTrue()
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST, FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED)
    @Test
    fun setPreferenceValue_retrievesResult() {
        val statusLatch = CountDownLatch(1)
        TestApis.permissions().withPermission(
            Manifest.permission.READ_SYSTEM_PREFERENCES,
            Manifest.permission.WRITE_SYSTEM_PREFERENCES
        ).withAppOp("android:write_system_preferences").use {
            client.setPreferenceValue(
                SetValueRequest.Builder(
                    "s",
                    "k",
                    SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_STRING)
                        .setStringValue("value")
                        .build()
                ).build(),
                context.mainExecutor
            ) { result ->
                Truth.assertThat(result.resultCode).isEqualTo(MetadataResult.RESULT_OK)
                statusLatch.countDown()
            }
        }
        Truth.assertThat(statusLatch.await(1, TimeUnit.SECONDS)).isTrue()
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST, FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED)
    @Test
    fun setPreferenceValue_withoutPermission_throwsException() {
        val statusLatch = CountDownLatch(1)
        client.setPreferenceValue(
            SetValueRequest.Builder(
                "s",
                "k",
                SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_STRING)
                    .setStringValue("value")
                    .build()
            ).build(),
            context.mainExecutor,
            object : OutcomeReceiver<SetValueResult, Exception> {
                override fun onResult(result: SetValueResult?) {
                    throw AssertionError("onResult should not be invoked")
                }

                override fun onError(error: Exception) {
                    Truth.assertThat(error).isInstanceOf(SecurityException::class.java)
                    statusLatch.countDown()
                }
            }
        )
        Truth.assertThat(statusLatch.await(1, TimeUnit.SECONDS)).isTrue()
    }
}
