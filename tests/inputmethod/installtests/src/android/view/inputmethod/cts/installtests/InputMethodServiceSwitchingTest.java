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

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.installtests.common.Ime1Constants;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
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
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(BedsteadJUnit4.class)
public final class InputMethodServiceSwitchingTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    @After
    public void tearDown() {
        TestApis.packages().find(Ime1Constants.PACKAGE).uninstallFromAllUsers();
        runShellCommandOrThrow(ShellCommandUtils.resetImesForAllUsers());
    }

    @Test
    public void testUninstallCurrentIme() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final int currentUserId = currentUser.id();

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        assertImeInInputMethodList(Ime1Constants.IME_ID, currentUserId);

        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);

        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime1Constants.IME_ID, currentUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime1Constants.IME_ID, currentUserId));

        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);

        TestApis.packages().find(Ime1Constants.PACKAGE).uninstall(currentUser);

        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
    }

    @Test
    public void testDisableCurrentIme() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final int currentUserId = currentUser.id();

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        assertImeInInputMethodList(Ime1Constants.IME_ID, currentUserId);

        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);

        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime1Constants.IME_ID, currentUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime1Constants.IME_ID, currentUserId));

        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);

        disableComponent(Ime1Constants.COMPONENT_NAME, currentUserId);

        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
    }

    @Nullable
    private static String getCurrentInputMethodId(int userId) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        final InputMethodInfo imi = SystemUtil.runWithShellPermissionIdentity(() ->
                        imm.getCurrentInputMethodInfoAsUser(UserHandle.of(userId)),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.QUERY_ALL_PACKAGES);
        return imi != null ? imi.getId() : null;
    }

    private void disableComponent(@NonNull ComponentName componentName, int userId) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SystemUtil.runWithShellPermissionIdentity(() ->
                        context.createContextAsUser(UserHandle.of(userId), 0)
                                .getPackageManager()
                                .setComponentEnabledSetting(componentName,
                                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0),
                Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    @NonNull
    private static List<InputMethodInfo> getInputMethodList(int userId) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodManager imm =
                Objects.requireNonNull(context.getSystemService(InputMethodManager.class));
        return SystemUtil.runWithShellPermissionIdentity(
                () -> imm.getInputMethodListAsUser(userId),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.QUERY_ALL_PACKAGES);
    }

    private static void assertImeInInputMethodList(String imeId, int userId) throws Exception {
        PollingCheck.check(String.format("Ime %s must be in the IME list.", imeId), TIMEOUT,
                () -> getInputMethodList(userId)
                        .stream()
                        .map(InputMethodInfo::getId)
                        .anyMatch(imeId::equals));
    }

    private static void assertImeInCurrentInputMethodInfo(String imeId, int userId)
            throws Exception {
        PollingCheck.check(String.format("Ime %s must be the current IME.", imeId), TIMEOUT,
                () -> TextUtils.equals(getCurrentInputMethodId(userId), imeId));
    }

    private void assertImeNotCurrentInputMethodInfo(String imeId, int userId)
            throws Exception {
        PollingCheck.check(String.format("Ime %s must not be the current IME.", imeId), TIMEOUT,
                () -> !TextUtils.equals(getCurrentInputMethodId(userId), imeId));
    }
}
