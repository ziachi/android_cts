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

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@LargeTest
@RequireMultiUserSupport
@RunWith(BedsteadJUnit4.class)
public final class UserUnlockTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    private static final ComponentName DIRECT_BOOT_AWARE_IME = ComponentName.createRelative(
            "com.android.cts.directbootawareime", ".DirectBootAwareIme");
    private static final String DIRECT_BOOT_AWARE_IME_ID =
            DIRECT_BOOT_AWARE_IME.flattenToShortString();

    private static final ComponentName DIRECT_BOOT_UNAWARE_IME = ComponentName.createRelative(
            "com.android.cts.directbootawareime", ".DirectBootUnawareIme");
    private static final String DIRECT_BOOT_UNAWARE_IME_ID =
            DIRECT_BOOT_UNAWARE_IME.flattenToShortString();

    private static final String TEST_APK_PACKAGE_NAME = DIRECT_BOOT_AWARE_IME.getPackageName();
    private static final File TEST_APK_PACKAGE_FILE =
            new File("/data/local/tmp/cts/inputmethod/CtsDirectBootAwareIme.apk");

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    @After
    public void tearDown() {
        TestApis.packages().find(TEST_APK_PACKAGE_NAME).uninstallFromAllUsers();
    }

    private static List<InputMethodInfo> getInputMethodListAsUser(
            @NonNull InputMethodManager imm, int userId) {
        return SystemUtil.runWithShellPermissionIdentity(
                () -> imm.getInputMethodListAsUser(userId),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private static Predicate<InputMethodInfo> imeIdMatcher(@NonNull String imeId) {
        return imi -> TextUtils.equals(imi.getId(), imeId);
    }

    private static void assertImeInApiResult(@NonNull InputMethodManager imm,
            @NonNull String imeId, int userId, long timeout) throws Exception {
        pollingCheck("Ime " + imeId + " must exist.", timeout, () ->
                getInputMethodListAsUser(imm, userId).stream().anyMatch(imeIdMatcher(imeId)));
    }

    private static void assertImeNotInApiResult(@NonNull InputMethodManager imm,
            @NonNull String imeId, int userId, long timeout) throws Exception {
        pollingCheck("Ime " + imeId + " must not exist.", timeout, () ->
                getInputMethodListAsUser(imm, userId).stream().noneMatch(imeIdMatcher(imeId)));
    }

    private static void pollingCheck(@NonNull String message, long timeout,
            @NonNull Callable<Boolean> condition) throws Exception {
        if (timeout <= 0) {
            assertTrue(message, condition.call());
        } else {
            PollingCheck.check(message, timeout, condition);
        }
    }

    /**
     * A regression test for b/356037588.
     */
    @Test
    @EnsureHasAdditionalUser
    public void testDirectBootUnawareImesInvisibleAfterStoppingUser() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final InputMethodManager imm = Objects.requireNonNull(
                context.getSystemService(InputMethodManager.class));

        final UserReference additionalUser = additionalUser(sDeviceState);
        final int additionalUserId = additionalUser.id();
        assertTrue(additionalUser.isUnlocked());

        // Install the test IME
        TestApis.packages().install(additionalUser, TEST_APK_PACKAGE_FILE);

        // When the user is unlocked, both direct-boot aware/unaware IMEs should be visible.
        assertImeInApiResult(imm, DIRECT_BOOT_AWARE_IME_ID, additionalUserId, TIMEOUT);
        assertImeInApiResult(imm, DIRECT_BOOT_UNAWARE_IME_ID, additionalUserId, 0 /* timeout */);

        try {
            // Stopping the user makes the user storage be locked again.
            additionalUser.stop();

            PollingCheck.check("Waiting for the user=" + additionalUserId + " to be locked",
                    TIMEOUT, () -> !additionalUser.isUnlocked());

            // When the user is unlocked, only direct-boot aware IME should be visible.
            assertImeNotInApiResult(imm, DIRECT_BOOT_UNAWARE_IME_ID, additionalUserId, TIMEOUT);
            assertImeInApiResult(imm, DIRECT_BOOT_AWARE_IME_ID, additionalUserId, 0 /* timeout */);
        } finally {
            // The test would get stuck if the additional user is not running upon existing.
            if (!additionalUser.isRunning()) {
                additionalUser.start();
            }
        }
    }
}
