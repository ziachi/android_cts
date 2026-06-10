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
package com.android.bedstead.testapp

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.testapps.testApps
import com.android.queryable.queries.ActivityQuery
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ProcessReferenceTest {
    @Test
    @Ignore("Killing by granting + ungranting a permission is currently not working")
    fun kill_killsProcess() {
        sTestApp.install().use { testApp ->
            testApp.activities().query().whereActivity().exported().isTrue().get().start()
            val pidBefore = testApp.process()!!.pid()

            testApp.process()!!.kill()
            Poll.forValue(
                "pid"
            ) { if (testApp.process() == null) -1 else testApp.process()!!.pid() }
                .toNotBeEqualTo(pidBefore)
                .errorOnFail()
                .await()
        }
    }

    @Test
    fun crash_crashesProcess() {
        sTestApp.install().use { testApp ->
            testApp.activities().query().whereActivity().exported().isTrue().get().start()
            val pidBefore = testApp.process()!!.pid()

            testApp.process()!!.crash()
            Poll.forValue(
                "pid"
            ) { if (testApp.process() == null) -1 else testApp.process()!!.pid() }
                .toNotBeEqualTo(pidBefore)
                .errorOnFail()
                .await()
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState: DeviceState = DeviceState()

        private val sTestApp: TestApp = sDeviceState.testApps().query()
            .whereActivities().contains(ActivityQuery.activity().where().exported().isTrue())
            .get()
    }
}
