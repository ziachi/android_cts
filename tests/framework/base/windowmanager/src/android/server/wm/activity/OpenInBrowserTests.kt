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

package android.server.wm.activity

import android.content.ComponentName
import android.server.wm.WaitForValidActivityState
import android.server.wm.WindowManagerState
import android.server.wm.WindowManagerTestBase
import com.android.compatibility.common.util.ApiTest
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceActivity:OpenInBrowserTests
 */
@ApiTest(apis = ["android.app.Activity#requestOpenInBrowserEducation"])
class OpenInBrowserTests : WindowManagerTestBase() {

    /**
     * Tests that [ActivityRecord.mRequestOpenInBrowserEducation] is correctly set when
     * [Activity.requestOpenInBrowserEducation] is called, notifying the system that the app is
     * requesting the open in browser education to be shown.
     */
    @Test
    fun requestOpenInBrowserEducation_requestTimestampRecorded() {
        val activity = startTestActivity()

        activity.requestOpenInBrowserEducation()
        assertNotEquals(
                0L,
                getActivity(activity.componentName)?.requestOpenInBrowserEducationTimestamp
        )
    }

    private fun getActivity(componentName: ComponentName): WindowManagerState.Activity? {
        mWmState.computeState(WaitForValidActivityState(componentName))
        return mWmState.getActivity(componentName)
    }

    private fun startTestActivity(): TestActivity {
        val activity = startActivity(TestActivity::class.java)
        val activityName = activity!!.componentName
        waitAndAssertResumedActivity(activityName, "$activityName must be resumed")
        return activity
    }

     class TestActivity : FocusableActivity()
}
