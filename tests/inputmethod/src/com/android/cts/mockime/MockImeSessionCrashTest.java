/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.mockime;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ApplicationExitInfo;
import android.app.Instrumentation;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public final class MockImeSessionCrashTest {

    private static final long MOCKIME_CRASH_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private Instrumentation mInstrumentation;

    private Context mContext;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
    }

    @Test
    public void testRetrieveExitReasonsWhenMockImeSessionCrashes() throws Exception {
        try (var mockImeSession = MockImeSession.create(mContext)) {
            assertThat(mockImeSession.retrieveExitReasonIfMockImeCrashed()).isNull();

            runShellCommandOrThrow("am force-stop " + mockImeSession.getMockImePackageName());

            PollingCheck.waitFor(MOCKIME_CRASH_TIMEOUT, () ->
                    mockImeSession.findLatestMockImeSessionExitInfo() != null);
            final var exitInfo = mockImeSession.findLatestMockImeSessionExitInfo();
            assertWithMessage(
                    "Expected MockImeSession to crash due to killed application").that(
                    exitInfo.getReason()).isEqualTo(ApplicationExitInfo.REASON_USER_REQUESTED);
            assertThat(mockImeSession.retrieveExitReasonIfMockImeCrashed()).isNotNull();
        }
    }
}
