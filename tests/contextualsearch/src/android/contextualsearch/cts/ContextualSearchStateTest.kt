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

package android.contextualsearch.cts

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.app.contextualsearch.ContextualSearchState
import android.content.ComponentName
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextualSearchStateTest {

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION)
    fun testContextualSearchStateDataClass() {
        val testState = ContextualSearchState(testAssistStructure, testAssistContent, testExtras)

        // Ensuring at least one of the values inside the structure, content and extras.
        // This test is to only verify the working of to/from parcel of ContextualSearchState.
        assertThat(testState.structure!!.activityComponent).isEqualTo(testComponentName)
        assertThat(testState.content!!.webUri).isEqualTo(testUri)
        assertThat(testState.content!!.sessionTransferUri).isEqualTo(testSessionTransferUri)
        testState.extras.keySet().forEach { key ->
            assertThat(testState.extras.getString(key)).isEqualTo(testExtras.getString(key))
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION)
    fun testContextualSearchStateToAndFromParcel() {
        val testState = ContextualSearchState(testAssistStructure, testAssistContent, testExtras)

        // Writing testState to a parcel
        val parcel = Parcel.obtain()
        parcel.setDataPosition(0)
        testState.writeToParcel(parcel, 0)
        // Creating copyState from the parcel
        parcel.setDataPosition(0)
        val copyState = ContextualSearchState.CREATOR.createFromParcel(parcel)

        // Ensuring at least one of the values inside the structure, content and extras.
        // This test is to only verify the working of to/from parcel of ContextualSearchState.
        assertThat(copyState.structure!!.activityComponent).isEqualTo(testComponentName)
        assertThat(copyState.content!!.webUri).isEqualTo(testUri)
        assertThat(copyState.content!!.sessionTransferUri).isEqualTo(testSessionTransferUri)
        testState.extras.keySet().forEach { key ->
            assertThat(copyState.extras.getString(key)).isEqualTo(testExtras.getString(key))
        }
    }

    companion object {
        private val testComponentName = ComponentName("pkg", "class")
        private val testAssistStructure: AssistStructure
            get() {
                // Since AssistStructure's constructor/setters are annotated with @hide,
                // a parcel is used to create an instance which can be tested.
                val parcel = Parcel.obtain()
                parcel.setDataPosition(0)
                parcel.writeInt(0) // mTaskId
                testComponentName.writeToParcel(parcel, 0)
                parcel.writeInt(1) // mIsHomeActivity
                parcel.writeStrongBinder(Binder())
                parcel.setDataPosition(0) // mReceiveChannel
                return AssistStructure.CREATOR.createFromParcel(parcel).also { parcel.recycle() }
            }

        private val testUri = Uri.parse("example.com")
        private val testSessionTransferUri = Uri.parse("example2.com")
        private val testAssistContent = AssistContent().apply {
            webUri = testUri
            sessionTransferUri = testSessionTransferUri
        }
        private val testExtras = Bundle().apply { putString("key", "value") }

        private val TAG = ContextualSearchStateTest::class.java.simpleName
    }
}
