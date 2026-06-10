/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin.util;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;

import static com.android.bedstead.permissions.CommonPermissions.ACCESS_VOICE_INTERACTION_SERVICE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.car.builtin.util.AssistUtilsHelper;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@EnsureHasPermission(value = {ACCESS_VOICE_INTERACTION_SERVICE, INTERACT_ACROSS_USERS})
public final class AssistUtilsHelperTest {

    private static final String TAG = AssistUtilsHelper.class.getSimpleName();
    private static final long TIMEOUT_MS = 2_000;
    private static final long TIMEOUT_UI_MS = 1_000;

    private Handler mHandler;

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private final Context mContext = mInstrumentation.getContext();

    @Before
    public void setUp() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Test
    public void testOnShownCallback() throws Exception {
        TestVoiceInteractionSessionListener listener = new TestVoiceInteractionSessionListener();
        AssistUtilsHelper.registerVoiceInteractionSessionListenerHelper(mContext, listener);
        SessionShowCallbackHelperImpl callbackHelperImpl = new SessionShowCallbackHelperImpl();
        boolean isAssistantComponentAvailable = AssistUtilsHelper
                .showPushToTalkSessionForActiveService(mContext, callbackHelperImpl);
        assumeTrue(isAssistantComponentAvailable);

        callbackHelperImpl.waitForCallback();

        assertWithMessage("Voice session shown")
                .that(callbackHelperImpl.isSessionOnShown()).isTrue();
        waitForUI();
        hideSessionAndWait(listener);
    }

    @Test
    public void testOnFailedCallback() throws Exception {
        // TODO (b/200609382): setup a failure scenario to cover session failed case and
        // call onFailed API
    }

    @Test
    public void isSessionRunning_whenSessionIsShown_succeeds() throws Exception {
        TestVoiceInteractionSessionListener listener = new TestVoiceInteractionSessionListener();
        AssistUtilsHelper.registerVoiceInteractionSessionListenerHelper(mContext, listener);
        SessionShowCallbackHelperImpl callbackHelperImpl = new SessionShowCallbackHelperImpl();
        boolean isAssistantComponentAvailable = AssistUtilsHelper
                .showPushToTalkSessionForActiveService(mContext, callbackHelperImpl);
        assumeTrue(isAssistantComponentAvailable);

        callbackHelperImpl.waitForCallback();

        assertWithMessage("Voice interaction session running")
                .that(AssistUtilsHelper.isSessionRunning(mContext)).isTrue();
        waitForUI();
        hideSessionAndWait(listener);
    }

    @Test
    public void registerVoiceInteractionSessionListenerHelper_onShowSession() throws Exception {
        TestVoiceInteractionSessionListener listener = new TestVoiceInteractionSessionListener();
        AssistUtilsHelper.registerVoiceInteractionSessionListenerHelper(mContext, listener);

        SessionShowCallbackHelperImpl callbackHelperImpl = new SessionShowCallbackHelperImpl();
        boolean isAssistantComponentAvailable = AssistUtilsHelper
                .showPushToTalkSessionForActiveService(mContext, callbackHelperImpl);
        assumeTrue(isAssistantComponentAvailable);
        callbackHelperImpl.waitForCallback();

        listener.waitForSessionChange();

        assertWithMessage("Voice interaction session shown")
                .that(listener.mIsSessionShown).isTrue();
        waitForUI();
        hideSessionAndWait(listener);
    }

    @Test
    public void registerVoiceInteractionSessionListenerHelper_hideCurrentSession()
            throws Exception {
        TestVoiceInteractionSessionListener listener = new TestVoiceInteractionSessionListener();
        AssistUtilsHelper.registerVoiceInteractionSessionListenerHelper(mContext, listener);
        SessionShowCallbackHelperImpl callbackHelperImpl = new SessionShowCallbackHelperImpl();
        boolean isAssistantComponentAvailable = AssistUtilsHelper
                .showPushToTalkSessionForActiveService(mContext, callbackHelperImpl);
        assumeTrue(isAssistantComponentAvailable);
        callbackHelperImpl.waitForCallback();
        listener.waitForSessionChange();
        waitForUI();
        listener.reset();

        AssistUtilsHelper.hideCurrentSession(mContext);

        // If the session is already hidden skip wait
        if (listener.mIsSessionShown) {
            listener.waitForSessionChange();
        }
        assertWithMessage("Voice interaction session when hidden")
                .that(listener.mIsSessionShown).isFalse();
        waitForUI();
    }

    private void hideSessionAndWait(TestVoiceInteractionSessionListener listener) throws Exception {
        // Do nothing if no Assistant session is running.
        if (!AssistUtilsHelper.isSessionRunning(mContext) || !listener.mIsSessionShown) {
            return;
        }

        AssistUtilsHelper.hideCurrentSession(mContext);

        listener.waitForSessionChange();
        waitForUI();
    }

    // TODO(b/338414165): Find out window delay to reduce failures
    private void waitForUI() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        mHandler.postDelayed(latch::countDown, TIMEOUT_UI_MS);
        boolean results = latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Log.i(TAG, "Wait for UI result " + results);
    }

    private static final class TestVoiceInteractionSessionListener implements
            AssistUtilsHelper.VoiceInteractionSessionListenerHelper {

        private CountDownLatch mChangeWait = new CountDownLatch(1);
        private boolean mIsSessionShown;

        @Override
        public void onVoiceSessionShown() {
            mIsSessionShown = true;
            Log.d(TAG, "onVoiceSessionShown is called");
            mChangeWait.countDown();
        }

        @Override
        public void onVoiceSessionHidden() {
            mIsSessionShown = false;
            Log.d(TAG, "onVoiceSessionHidden is called");
            mChangeWait.countDown();
        }

        private void waitForSessionChange() throws Exception {
            if (!mChangeWait.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timed out waiting for session change");
            }
        }

        private void reset() {
            Log.d(TAG, "reset is called");
            mChangeWait = new CountDownLatch(1);
        }
    }

    private static final class SessionShowCallbackHelperImpl implements
            AssistUtilsHelper.VoiceInteractionSessionShowCallbackHelper {

        private final CountDownLatch mCallbackLatch = new CountDownLatch(1);
        private boolean mIsSessionOnShown = false;

        public void onShown() {
            mIsSessionOnShown = true;
            Log.d(TAG, "onShown is called");
            mCallbackLatch.countDown();
        }

        public void onFailed() {
            Log.d(TAG, "onFailed");
        }

        private boolean isSessionOnShown() {
            return mIsSessionOnShown;
        }

        private void waitForCallback() throws Exception {
            mCallbackLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
