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

package android.server.wm.input;

import static android.server.wm.overlay.Components.OverlayActivity.EXTRA_TOKEN;

import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.shared.BlockingResultReceiver;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * Executes the same tests as WindowUntrustedTouchTest to ensure compatibility with target SDK 35
 * and below. Opt-in is not required for cross-uid pass-through touches.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerSdk35TestCases:WindowUntrustedTouchTestSdk35
 */
@Presubmit
public class WindowUntrustedTouchTestSdk35 extends WindowUntrustedTouchTestBase {
    private static final String APP_SELF = "android.server.wm.cts.testsdk35";

    @Override
    @NonNull
    String getAppSelf() {
        return APP_SELF;
    }

    /**
     * Tests that pass-through touches are allowed even without opt-in if the compat change is not
     * enabled (for target SDK 35 and below).
     */
    @Test
    public void testWhenOneActivityWindowWithZeroOpacityNoOptIn_compat_allowsTouch()
            throws Exception {
        // No touch pass-through opt-in by default.
        addActivityOverlay(APP_A, /* opacity */ 0f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opt-in is not required for apps with target SDK 35 and below to receive touches.
        assertTouchReceived();
    }

    /**
     * Tests that pass-through touches are allowed even without opt-in if the compat change is not
     * enabled (for target SDK 35 and below).
     */
    @Test
    public void testWhenActivityChildWindowWithDifferentTokenFromSameAppNoOptIn_compat_allowsTouch()
            throws Exception {
        // Creates a new activity with 0 opacity
        BlockingResultReceiver receiver = new BlockingResultReceiver();
        addActivityOverlay(APP_A, /* opacity */ 0f, receiver);
        // Now get its token and put a child window owned by us
        IBinder token = receiver.getData(TIMEOUT_MS).getBinder(EXTRA_TOKEN);
        addActivityChildWindow(getAppSelf(), WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opt-in is not required for apps with target SDK 35 and below to receive touches.
        assertTouchReceived();
    }
}
