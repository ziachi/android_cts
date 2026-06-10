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

package com.android.xts.root

import com.android.bedstead.adb.adb
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.ShellCommandUtils
import com.android.xts.root.annotations.RequireAdbRoot
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class RootAnnotationExecutorTest {

    @Test
    @RequireAdbRoot
    fun requireAdbRootAnnotation_hasAdbRoot() {
        assertThat(TestApis.adb().isRootAvailable()).isTrue()
    }

    @Test
    @RequireAdbRoot
    fun testUsesAdbRoot_usesRequireAdbRootAnnotation_returnsTrue() {
        assertThat(deviceState.testUsesAdbRoot()).isTrue()
    }

    @Test
    fun testUsesAdbRoot_doesNotUseRequireAdbRootAnnotation_returnsFalse() {
        assertThat(deviceState.testUsesAdbRoot()).isFalse()
    }

    @Test
    @RequireRootInstrumentation(reason = "testing root instrumentation")
    fun requireRootInstrumentation_isRecognisedAsRootBySystemServer() {
        // This will throw if not instrumented as root
        ShellCommandUtils.uiAutomation().clearOverridePermissionStates(/* uid = */ -1)
    }

    @Test
    @RequireRootInstrumentation(reason = "testing root instrumentation")
    fun testUsesRootInstrumentation_usesRequireRootInstrumentationAnnotation_returnsTrue() {
        assertThat(deviceState.testUsesRootInstrumentation()).isTrue()
    }

    @Test
    fun testUsesRootInstrumentation_doesNotUseRequireRootInstrumentationAnnotation_returnsFalse() {
        assertThat(deviceState.testUsesRootInstrumentation()).isFalse()
    }

    @Test
    @RequireRootInstrumentation(reason = "testing root instrumentation")
    @RequireAdbRoot(reason = "To be picked up by btest --root")
    fun isRunningAsRoot_true_returnsTrue() {
        assertThat(ShellCommandUtils.isRunningAsRoot()).isTrue()
    }

    companion object {
        @ClassRule @Rule @JvmField
        val deviceState = DeviceState()
    }
}
