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

import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER
import android.app.appsearch.GenericDocument
import android.os.Bundle
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.appsearch.flags.Flags.FLAG_ENABLE_GENERIC_DOCUMENT_OVER_IPC
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_ENABLE_GENERIC_DOCUMENT_OVER_IPC, FLAG_ENABLE_APP_FUNCTION_MANAGER)
class ExecuteAppFunctionResponseTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.ExecuteAppFunctionResponse#CREATOR",
                "android.app.appfunctions.ExecuteAppFunctionResponse#writeToParcel",
                "android.app.appfunctions.ExecuteAppFunctionResponse#getResultDocument",
                "android.app.appfunctions.ExecuteAppFunctionResponse#getResponseDataSize",
            ]
    )
    @Test
    fun build_nonEmptySuccessResponse_noExtras() {
        val resultGd: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyBoolean(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, true)
                .build()
        val response = ExecuteAppFunctionResponse(resultGd)
        val expectedDataSize = response.responseDataSize

        val restoredResponse = parcelAndUnparcel(response)

        assertThat(
                restoredResponse.resultDocument.getProperty(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo(booleanArrayOf(true))
        assertThat(restoredResponse.responseDataSize).isEqualTo(expectedDataSize)
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.ExecuteAppFunctionResponse#CREATOR",
                "android.app.appfunctions.ExecuteAppFunctionResponse#writeToParcel",
                "android.app.appfunctions.ExecuteAppFunctionResponse#getExtras",
                "android.app.appfunctions.ExecuteAppFunctionResponse#getResultDocument",
                "android.app.appfunctions.ExecuteAppFunctionResponse#getResponseDataSize",
            ]
    )
    @Test
    fun build_nonEmptySuccessResponse_withExtras() {
        val resultGd: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyBoolean(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, true)
                .build()
        val extras = Bundle()
        extras.putString("key", "value")
        val response = ExecuteAppFunctionResponse(resultGd, extras)
        val expectedDataSize = response.responseDataSize

        val restoredResponse = parcelAndUnparcel(response)

        assertThat(
            restoredResponse.resultDocument.getProperty(
                ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
            )
        )
            .isEqualTo(booleanArrayOf(true))
        assertThat(restoredResponse.extras.getString("key")).isEqualTo("value")
        assertThat(restoredResponse.responseDataSize).isEqualTo(expectedDataSize)
    }

    private fun parcelAndUnparcel(
        original: ExecuteAppFunctionResponse
    ): ExecuteAppFunctionResponse {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return ExecuteAppFunctionResponse.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
