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

package android.view.inputmethod.cts.installtests;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.ApplicationExitInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.InstantAppInfo;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerStateHelper;
import android.text.TextUtils;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;
import android.view.inputmethod.cts.util.MockTestActivityUtil;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.CommonPackages;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingSupplier;
import com.android.cts.input.DebugInputRule;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImePackageNames;
import com.android.cts.mockime.MockImeSession;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@LargeTest
@RequireMultiUserSupport
@RunWith(BedsteadJUnit4.class)
public final class MultiUserMockImeTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    private static final long MOCKIME_CRASH_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    @Rule public DebugInputRule mDebugInputRule = new DebugInputRule();

    /** Tag for the first EditText. */
    private static final String FIRST_EDIT_TEXT_TAG = "first-EditText";
    /** Tag for the second EditText. */
    private static final String SECOND_EDIT_TEXT_TAG = "second-EditText";

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @After
    public void tearDown() {
        runShellCommandOrThrow(ShellCommandUtils.resetImesForAllUsers());
    }

    /**
     * TODO(b/327704045): Unify the implementation with
     * {@link android.view.inputmethod.cts.util.EndToEndImeTestBase#getTestMarker(String)}
     */
    private String getTestMarker(@NonNull String tag) {
        return getClass().getName() + "/" + tag + "/" + SystemClock.elapsedRealtimeNanos();
    }

    @Test
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testProfileSwitching() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference workUser = workProfile(sDeviceState, currentUser);
        final int currentUserId = currentUser.id();
        final int workUserId = workUser.id();

        assertTrue(workUser.isRunning());

        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final var context = instrumentation.getContext();
        final var uiAutomation = instrumentation.getUiAutomation();
        final boolean isInstant = isInstantApp(context, uiAutomation);

        // Copy required packages from the current user to the profile user. Note that currently
        // bedstead does not support install-existing with "--instant" option so here we directly
        // use shell commands.

        // For MockIme, always install as full (non-instant) app.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, workUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), workUserId, isInstant));

        try (var session1 = MockImeSession.create(context, uiAutomation,
                new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                        new ImeSettings.Builder().setTargetUser(workUser.userHandle()))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, isInstant,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1))) {
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        workUserId, isInstant,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    expectEvent(stream1, event -> "onDestroy".equals(event.getEventName()),
                            TIMEOUT);
                }
            }
        }
    }

    @Test
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    @DebugInputRule.DebugInput(bug = 385227171)
    public void testRemoveCurrentProfileCanStartInputOnOtherUser() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference profileUser = workProfile(sDeviceState, currentUser);
        final int currentUserId = currentUser.id();
        final int profileUserId = profileUser.id();

        assertTrue(profileUser.isRunning());

        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final var context = instrumentation.getContext();
        final var uiAutomation = instrumentation.getUiAutomation();

        // DeviceState setup disabled keyguard to avoid test failures, but we need it enabled.
        // This also ensures we don't end the test with keyguard shown.
        TestApis.device().setKeyguardEnabled(true);

        // Copy required packages from the current user to the profile user. Note that currently
        // bedstead does not support install-existing with "--instant" option so here we directly
        // use shell commands.

        // For MockIme, always install as full (non-instant) app.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, profileUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), profileUserId,
                false /* instant */));

        try (var lockScreenSession = new LockScreenSession(instrumentation, mWmState);
                var session1 = MockImeSession.create(context, uiAutomation,
                        new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                        new ImeSettings.Builder()
                                .setTargetUser(profileUser.userHandle())
                                .setSuppressDeleteSettings(true))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, false /* instant */,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1))) {
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        profileUserId, false /* instant */,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    expectEvent(stream1, event -> "onDestroy".equals(event.getEventName()),
                            TIMEOUT);

                    lockScreenSession.sleepDevice();

                    // Remove profile with screen off to maintain currentImeUser ID in
                    // InputMethodManagerService.
                    profileUser.remove();

                    final var exitInfo = PollingCheck.waitFor(MOCKIME_CRASH_TIMEOUT,
                            session2::findLatestMockImeSessionExitInfo, Objects::nonNull);
                    assertEquals("Expected MockImeSession to crash due to removed user",
                            ApplicationExitInfo.REASON_USER_STOPPED, exitInfo.getReason());

                    lockScreenSession.wakeUpDevice();
                    // Wait for lock screen to be visible and focused before unlocking.
                    mWmState.waitForNonActivityWindowFocused();
                    lockScreenSession.unlockDevice();

                    mWmState.waitForFocusedActivity(MockTestActivityUtil.TEST_ACTIVITY);

                    // Must be able to startInput on the previous user even when the currentImeUser
                    // was removed.
                    expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);
                }
            }
        }
    }

    private static <T> T runWithShellPermissionIdentity(@NonNull UiAutomation uiAutomation,
            @NonNull ThrowingSupplier<T> supplier, String... permissions) {
        Object[] placeholder = new Object[1];
        SystemUtil.runWithShellPermissionIdentity(uiAutomation, () ->
                placeholder[0] = supplier.get(), permissions);
        return (T) placeholder[0];
    }

    private boolean isInstantApp(@NonNull Context context, @NonNull UiAutomation uiAutomation) {
        return runWithShellPermissionIdentity(uiAutomation, () -> {
            // as this test app itself is always running as a full app, we can check if the
            // CtsInputMethodStandaloneTestApp was installed as an instant app
            Optional<InstantAppInfo> instantAppInfo =
                    context.getPackageManager().getInstantApps().stream()
                            .filter(packageInfo -> TextUtils.equals(packageInfo.getPackageName(),
                                    MockTestActivityUtil.TEST_ACTIVITY.getPackageName()))
                            .findFirst();
            return instantAppInfo.isPresent()
                    && instantAppInfo.get().getApplicationInfo().isInstantApp();
        }, Manifest.permission.ACCESS_INSTANT_APPS);
    }
}
