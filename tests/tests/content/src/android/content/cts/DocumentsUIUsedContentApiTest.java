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

package android.content.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class DocumentsUIUsedContentApiTest {

    private Context mContext;

    /**
     * Returns the Context object that's being tested.
     */
    private Context getContextUnderTest() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Before
    public final void setUp() throws Exception {
        mContext = getContextUnderTest();
    }

    @Test
    public void testCreatePackageContextAsUser() throws Exception {
        for (UserHandle user : new UserHandle[]{
                Process.myUserHandle(),
                UserHandle.ALL, UserHandle.CURRENT, UserHandle.SYSTEM
        }) {
            assertThat(mContext.createPackageContextAsUser("com.android.shell", 0, user)
                    .getUser()).isEqualTo(user);
        }
    }

    @Test
    public void testStartActivityAsUser() {
        try (ActivitySession activitySession = new ActivitySession()) {
            Intent intent = new Intent(mContext, AvailableIntentsActivity.class);

            activitySession.assertActivityLaunched(intent.getComponent().getClassName(),
                    () -> SystemUtil.runWithShellPermissionIdentity(() ->
                            mContext.startActivityAsUser(intent, UserHandle.CURRENT)));
        }
    }

    /**
     * Helper class to launch / close test activity.
     */
    private class ActivitySession implements AutoCloseable {
        private Activity mTestActivity;
        private static final int ACTIVITY_LAUNCH_TIMEOUT = 5000;

        void assertActivityLaunched(String activityClassName, Runnable activityStarter) {
            final Instrumentation.ActivityMonitor monitor = getInstrumentation()
                    .addMonitor(activityClassName, null /* result */,
                            false /* block */);
            activityStarter.run();
            // Wait for activity launch with timeout.
            mTestActivity = getInstrumentation().waitForMonitorWithTimeout(monitor,
                    ACTIVITY_LAUNCH_TIMEOUT);
            assertThat(mTestActivity).isNotNull();
        }

        @Override
        public void close() {
            if (mTestActivity != null) {
                mTestActivity.finishAndRemoveTask();
            }
        }
    }
}
