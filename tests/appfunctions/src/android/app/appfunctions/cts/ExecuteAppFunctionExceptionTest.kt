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

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER
import android.os.Bundle
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_ENABLE_APP_FUNCTION_MANAGER)
class AppFunctionExceptionTest {
    @ApiTest(
        apis =
        [
            "android.app.appfunctions.AppFunctionException#getErrorMessage",
            "android.app.appfunctions.AppFunctionException#getErrorCode",
            "android.app.appfunctions.AppFunctionException#getExtras",
        ]
    )
    @Test
    fun testConstructor_withErrorCodeAndMessage() {
        val exception =
            AppFunctionException(
                AppFunctionException.ERROR_DENIED,
                "Permission denied",
            )
        assertThat(exception.errorCode).isEqualTo(AppFunctionException.ERROR_DENIED)
        assertThat(exception.errorMessage).isEqualTo("Permission denied")
        assertThat(exception.extras).isEqualTo(Bundle.EMPTY)
    }

    @ApiTest(
        apis =
        [
            "android.app.appfunctions.AppFunctionException#getErrorMessage",
            "android.app.appfunctions.AppFunctionException#getErrorCode",
            "android.app.appfunctions.AppFunctionException#getExtras",
        ]
    )
    @Test
    fun testConstructor_withErrorCodeMessageAndExtras() {
        val extras = Bundle().apply { putString("key", "value") }
        val exception =
            AppFunctionException(
                AppFunctionException.ERROR_SYSTEM_ERROR,
                "System error",
                extras,
            )
        assertThat(exception.errorCode).isEqualTo(AppFunctionException.ERROR_SYSTEM_ERROR)
        assertThat(exception.errorMessage).isEqualTo("System error")
        assertThat(exception.extras).isEqualTo(extras)
    }

    @ApiTest(
        apis =
        [
            "android.app.appfunctions.AppFunctionException#getErrorCategory",
        ]
    )
    @Test
    fun testGetErrorCategory() {
        assertThat(
                AppFunctionException(AppFunctionException.ERROR_DENIED, null)
                    .errorCategory
            )
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_REQUEST_ERROR)
        assertThat(
                AppFunctionException(
                        AppFunctionException.ERROR_INVALID_ARGUMENT,
                        null,
                    )
                    .errorCategory
            )
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_REQUEST_ERROR)
        assertThat(
                AppFunctionException(AppFunctionException.ERROR_DISABLED, null)
                    .errorCategory
            )
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_REQUEST_ERROR)
        assertThat(
                AppFunctionException(
                        AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                        null,
                    )
                    .errorCategory
            )
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_REQUEST_ERROR)

        assertThat(
                AppFunctionException(AppFunctionException.ERROR_SYSTEM_ERROR, null)
                    .errorCategory
            )
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_SYSTEM)
        assertThat(
                AppFunctionException(AppFunctionException.ERROR_CANCELLED, null)
                    .errorCategory
            )
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_SYSTEM)

        assertThat(
                AppFunctionException(
                        AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                        null,
                    )
                    .errorCategory
            )
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_APP)

        assertThat(AppFunctionException(4000, null).errorCategory)
            .isEqualTo(AppFunctionException.ERROR_CATEGORY_UNKNOWN)
    }

    @ApiTest(
        apis =
        [
            "android.app.appfunctions.AppFunctionException#CREATOR",
            "android.app.appfunctions.AppFunctionException#writeToParcel",
        ]
    )
    @Test
    fun testParcelable() {
        val extras = Bundle().apply { putString("key", "value") }
        val exception =
            AppFunctionException(
                AppFunctionException.ERROR_CANCELLED,
                "Cancelled",
                extras,
            )

        val parcel = Parcel.obtain()
        exception.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreatedException = AppFunctionException.CREATOR.createFromParcel(parcel)
        assertThat(recreatedException.errorCode).isEqualTo(exception.errorCode)
        assertThat(recreatedException.errorMessage).isEqualTo(exception.errorMessage)
        assertThat(recreatedException.extras.getString(("key"))).isEqualTo("value")

        parcel.recycle()
    }

    @ApiTest(
        apis =
        [
            "android.app.appfunctions.AppFunctionException#CREATOR",
            "android.app.appfunctions.AppFunctionException#writeToParcel",
        ]
    )
    @Test
    fun testParcel_nullErrorMessage() {
        val exception =
            AppFunctionException(AppFunctionException.ERROR_CANCELLED, null)

        val parcel = Parcel.obtain()
        exception.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreatedException = AppFunctionException.CREATOR.createFromParcel(parcel)
        assertThat(recreatedException.errorCode).isEqualTo(exception.errorCode)
        assertThat(recreatedException.errorMessage).isNull()
        assertThat(recreatedException.extras.isEmpty).isTrue()

        parcel.recycle()
    }
}
