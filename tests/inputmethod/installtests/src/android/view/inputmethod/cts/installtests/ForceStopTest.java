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

import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.additionalUser;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.installtests.common.Ime1Constants;
import android.view.inputmethod.cts.installtests.common.Ime2Constants;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(BedsteadJUnit4.class)
public class ForceStopTest {
    private static final String TAG = "ForceStopTest";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    private static final long SHORT_TIMEOUT = TimeUnit.MILLISECONDS.toMillis(500);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    private boolean mNeedsTearDown = false;

    @Before
    public void setUp() {
        mNeedsTearDown = true;
    }

    @After
    public void tearDown() {
        if (!mNeedsTearDown) {
            return;
        }

        TestApis.packages().find(Ime1Constants.PACKAGE).uninstallFromAllUsers();
        TestApis.packages().find(Ime2Constants.PACKAGE).uninstallFromAllUsers();

        runShellCommandOrThrow(ShellCommandUtils.resetImesForAllUsers());

        runShellCommandOrThrow(ShellCommandUtils.wakeUp());
        runShellCommandOrThrow(ShellCommandUtils.dismissKeyguard());
        runShellCommandOrThrow(ShellCommandUtils.closeSystemDialog());
    }

    /**
     * A regression test for Bug 333798837 (for the current / primary user).
     */
    @Test
    public void testImeRemainsEnabledAfterForceStopForCurrentUser() {
        final UserReference currentUser = sDeviceState.initialUser();
        testImeRemainsEnabledAfterForceStopMain(currentUser, false /* selectIme */);
    }

    /**
     * A regression test for Bug 333798837 (for the current / primary user).
     */
    @Test
    public void testImeRemainsSelectedAndEnabledAfterForceStopForCurrentUser() {
        final UserReference currentUser = sDeviceState.initialUser();
        testImeRemainsEnabledAfterForceStopMain(currentUser, true /* selectIme */);
    }

    /**
     * A regression test for Bug 333798837 (for background users).
     */
    @RequireMultiUserSupport
    @EnsureHasAdditionalUser
    @Test
    public void testImeRemainsEnabledAfterForceStopForBackgroundUser() {
        final UserReference additionalUser = additionalUser(sDeviceState);
        testImeRemainsEnabledAfterForceStopMain(additionalUser, false /* selectIme */);
    }

    @RequireMultiUserSupport
    @EnsureHasAdditionalUser
    @Test
    public void testImeRemainsSelectedAndEnabledAfterForceStopForBackgroundUser() {
        final UserReference additionalUser = additionalUser(sDeviceState);
        testImeRemainsEnabledAfterForceStopMain(additionalUser, true /* selectIme */);
    }

    private void testImeRemainsEnabledAfterForceStopMain(UserReference user, boolean selectIme) {
        final int userId = user.id();
        TestApis.packages().install(user, new File(Ime1Constants.APK_PATH));
        assertImeExistsInApiResult(Ime1Constants.IME_ID, userId);
        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime1Constants.IME_ID, userId));
        assertImeEnabledInApiResult(Ime1Constants.IME_ID, userId);
        if (selectIme) {
            runShellCommandOrThrow(
                    ShellCommandUtils.setCurrentImeSync(Ime1Constants.IME_ID, userId));
            assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, userId);
        }

        forceStopPackage(Ime1Constants.PACKAGE, userId);

        // This sleep is intended, as what we expect is to ensure that the current IME remains to
        // be enabled even after a short period of time.
        // c.f. intentional sleep within ImeEventStreamTestUtils.notExpectEvent.
        SystemClock.sleep(SHORT_TIMEOUT);

        // Force-stopping will no longer remove the IME from the enabled IME list. It must remain
        // enabled.
        assertImeEnabledInApiResult(Ime1Constants.IME_ID, userId);

        if (selectIme) {
            // Force-stopping a background user's IME package will unselect the IME.
            assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, userId);
        }
    }

    private static void forceStopPackage(String packageName, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> InstrumentationRegistry
                        .getInstrumentation()
                        .getTargetContext()
                        .createPackageContextAsUser("android", 0, UserHandle.of(userId))
                        .getSystemService(ActivityManager.class)
                        .forceStopPackage(packageName),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.FORCE_STOP_PACKAGES);
    }

    private static void assertImeExistsInApiResult(String imeId, int userId) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final InputMethodManager imm =
                Objects.requireNonNull(context.getSystemService(InputMethodManager.class));
        SystemUtil.runWithShellPermissionIdentity(
                () -> PollingCheck.check("Ime " + imeId + " must exist for user " + userId, TIMEOUT,
                        () -> imm.getInputMethodListAsUser(userId).stream().anyMatch(
                                imi -> TextUtils.equals(imi.getId(), imeId))),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private static void assertImeInCurrentInputMethodInfo(String imeId, int userId) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final InputMethodManager imm =
                Objects.requireNonNull(context.getSystemService(InputMethodManager.class));
        SystemUtil.runWithShellPermissionIdentity(() -> PollingCheck.check(
                String.format("Ime %s must be the current IME. Found %s", imeId,
                        imm.getCurrentInputMethodInfoAsUser(UserHandle.of(userId)).getId()),
                TIMEOUT, () -> TextUtils.equals(
                        imm.getCurrentInputMethodInfoAsUser(UserHandle.of(userId)).getId(), imeId)),
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private static void assertImeNotCurrentInputMethodInfo(String imeId, int userId) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final InputMethodManager imm =
                Objects.requireNonNull(context.getSystemService(InputMethodManager.class));
        SystemUtil.runWithShellPermissionIdentity(
                () -> PollingCheck.check("Ime " + imeId + " must not be the current IME.", TIMEOUT,
                        () -> {
                            final InputMethodInfo info = imm.getCurrentInputMethodInfoAsUser(
                                    UserHandle.of(userId));
                            if (info == null) {
                                return true;
                            }
                            return !TextUtils.equals(info.getId(), imeId);
                        }), Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private static void assertImeEnabledInApiResult(String imeId, int userId) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final InputMethodManager imm =
                Objects.requireNonNull(context.getSystemService(InputMethodManager.class));
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                PollingCheck.check("Ime " + imeId + " must be enabled.", TIMEOUT,
                        () -> imm.getEnabledInputMethodListAsUser(
                                UserHandle.of(userId)).stream().anyMatch(
                                    imi -> TextUtils.equals(imi.getId(), imeId)));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }
}
