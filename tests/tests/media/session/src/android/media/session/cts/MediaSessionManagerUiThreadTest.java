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
package android.media.session.cts;

import static org.junit.Assert.assertThrows;

import android.app.Instrumentation;
import android.content.Context;
import android.media.session.MediaSessionManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaSessionManager} that need to be run on the UIThread and can't be run with
 * Bedstead. Normally {@link MediaSessionManagerTest} should be used instead.
 */
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaSessionManagerUiThreadTest {

    private MediaSessionManager mSessionManager;

    @Before
    public void setUp() {
        mSessionManager =
                (MediaSessionManager)
                        getInstrumentation()
                                .getTargetContext()
                                .getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @After
    public void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    @UiThreadTest
    @FrameworkSpecificTest
    public void testAddOnActiveSessionsListener_invalidMethodArguments_throwsException() {
        assertThrows(
                "Expected NPE for call to addOnActiveSessionsChangedListener",
                NullPointerException.class,
                () ->
                        mSessionManager.addOnActiveSessionsChangedListener(
                                /* sessionListener= */ null, /* notificationListener= */ null));

        MediaSessionManager.OnActiveSessionsChangedListener listener = controllers -> {};

        assertThrows(
                "Expected security exception for call to addOnActiveSessionsChangedListener",
                SecurityException.class,
                () ->
                        mSessionManager.addOnActiveSessionsChangedListener(
                                listener, /* notificationListener= */ null));
    }

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}
