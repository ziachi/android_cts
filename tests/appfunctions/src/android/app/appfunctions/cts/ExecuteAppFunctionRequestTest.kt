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

import android.app.appfunctions.ExecuteAppFunctionRequest
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
class ExecuteAppFunctionRequestTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#Builder",
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#setParameter",
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#setExtras",
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#build",
                "android.app.appfunctions.ExecuteAppFunctionRequest#writeToParcel",
                "android.app.appfunctions.ExecuteAppFunctionRequest#CREATOR",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getTargetPackageName",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getFunctionIdentifier",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getParameters",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getExtras",
            ]
    )
    @Test
    fun build() {
        val extras = Bundle()
        extras.putString("extra", "value")
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("testLong", 23)
                .build()
        val request: ExecuteAppFunctionRequest =
            ExecuteAppFunctionRequest.Builder("targetPkg", "targetFunctionId")
                .setExtras(extras)
                .setParameters(parameters)
                .build()

        val restoredRequest: ExecuteAppFunctionRequest = parcelAndUnparcel(request)

        assertThat(restoredRequest.targetPackageName).isEqualTo("targetPkg")
        assertThat(restoredRequest.functionIdentifier).isEqualTo("targetFunctionId")
        assertThat(restoredRequest.parameters).isEqualTo(parameters)
        assertThat(restoredRequest.extras.size()).isEqualTo(1)
        assertThat(restoredRequest.extras.getString("extra")).isEqualTo("value")
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#Builder",
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#setParameter",
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#setExtras",
                "android.app.appfunctions.ExecuteAppFunctionRequest.Builder#build",
                "android.app.appfunctions.ExecuteAppFunctionRequest#writeToParcel",
                "android.app.appfunctions.ExecuteAppFunctionRequest#CREATOR",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getTargetPackageName",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getFunctionIdentifier",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getParameters",
                "android.app.appfunctions.ExecuteAppFunctionRequest#getExtras",
            ]
    )
    @Test
    fun emptyBuild() {
        val request: ExecuteAppFunctionRequest =
            ExecuteAppFunctionRequest.Builder("targetPkg", "targetFunctionId").build()

        val restoredRequest: ExecuteAppFunctionRequest = parcelAndUnparcel(request)

        assertThat(restoredRequest.targetPackageName).isEqualTo("targetPkg")
        assertThat(restoredRequest.functionIdentifier).isEqualTo("targetFunctionId")
        assertThat(restoredRequest.parameters).isNotNull()
        assertThat(restoredRequest.extras).isNotNull()
    }

    private fun parcelAndUnparcel(original: ExecuteAppFunctionRequest): ExecuteAppFunctionRequest {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return ExecuteAppFunctionRequest.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
