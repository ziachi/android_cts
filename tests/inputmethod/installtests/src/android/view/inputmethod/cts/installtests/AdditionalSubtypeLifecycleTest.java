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

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.inputmethodservice.cts.common.CommandProviderConstants;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.installtests.common.Ime1Constants;
import android.view.inputmethod.cts.installtests.common.Ime2Constants;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;

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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@LargeTest
@RequireMultiUserSupport
@RunWith(BedsteadJUnit4.class)
public final class AdditionalSubtypeLifecycleTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    private static final InputMethodSubtype TEST_SUBTYPE1 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(1)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE2 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(2)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE3 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(3)
                    .build();

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
    }

    @Test
    @EnsureHasAdditionalUser
    public void testPerUserAdditionalInputMethodSubtype() {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference additionalUser = additionalUser(sDeviceState);
        final int currentUserId = currentUser.id();
        final int additionalUserId = additionalUser.id();
        assertThat(currentUserId).isNotEqualTo(additionalUserId);

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(additionalUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, additionalUserId);

        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);

            assertThat(subtypeHashCodes).contains(TEST_SUBTYPE1.hashCode());
            assertThat(subtypeHashCodes).contains(TEST_SUBTYPE2.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE3.hashCode());
        }
        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime2Constants.IME_ID,
                    additionalUserId);

            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE1.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE2.hashCode());
            assertThat(subtypeHashCodes).contains(TEST_SUBTYPE3.hashCode());
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void testClearAdditionalInputMethodSubtypeUponApkUpdateForForegroundUser() {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference additionalUser = additionalUser(sDeviceState);
        final int currentUserId = currentUser.id();
        final int additionalUserId = additionalUser.id();
        assertThat(currentUserId).isNotEqualTo(additionalUserId);

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(additionalUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, additionalUserId);

        // Updating an already-installed APK clears additional subtypes (for the foreground user).
        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));

        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);

            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE1.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE2.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE3.hashCode());
        }
        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime2Constants.IME_ID,
                    additionalUserId);

            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE1.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE2.hashCode());
            assertThat(subtypeHashCodes).contains(TEST_SUBTYPE3.hashCode());
        }
    }

    /**
     * Regression test for Bug 27859687.
     */
    @Test
    @EnsureHasAdditionalUser
    public void testClearAdditionalInputMethodSubtypeUponApkUpdateForBackgroundUser() {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference additionalUser = additionalUser(sDeviceState);
        final int currentUserId = currentUser.id();
        final int additionalUserId = additionalUser.id();
        assertThat(currentUserId).isNotEqualTo(additionalUserId);

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(additionalUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, additionalUserId);

        // Updating an already-installed APK clears additional subtypes (for a background user).
        TestApis.packages().install(additionalUser, new File(Ime2Constants.APK_PATH));

        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);

            assertThat(subtypeHashCodes).contains(TEST_SUBTYPE1.hashCode());
            assertThat(subtypeHashCodes).contains(TEST_SUBTYPE2.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE3.hashCode());
        }
        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime2Constants.IME_ID,
                    additionalUserId);

            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE1.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE2.hashCode());
            assertThat(subtypeHashCodes).doesNotContain(TEST_SUBTYPE3.hashCode());
        }
    }

    /**
     * Regression test for Bug 267124364.
     */
    @Test
    @EnsureHasAdditionalUser
    public void testClearAdditionalInputMethodSubtypeUponClearDataForForegroundUser()
            throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference additionalUser = additionalUser(sDeviceState);
        final int currentUserId = currentUser.id();
        final int additionalUserId = additionalUser.id();
        assertThat(currentUserId).isNotEqualTo(additionalUserId);

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(additionalUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, additionalUserId);

        // Updating an already-installed APK clears additional subtypes (for the foreground user).
        runShellCommandOrThrow(
                ShellCommandUtils.clearPackageData(Ime1Constants.PACKAGE, currentUserId));

        PollingCheck.check(
                "Additional Subtypes should be cleared for user" + currentUserId,
                TIMEOUT, () -> {
                    final var subtypeHashCodes =
                            getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);
                    return !subtypeHashCodes.contains(TEST_SUBTYPE1.hashCode())
                            && !subtypeHashCodes.contains(TEST_SUBTYPE2.hashCode())
                            && !subtypeHashCodes.contains(TEST_SUBTYPE3.hashCode());
                });

        PollingCheck.check(
                "Additional Subtypes should not be cleared for user" + additionalUserId,
                TIMEOUT, () -> {
                    final var subtypeHashCodes =
                            getSubtypeHashCodes(Ime2Constants.IME_ID, additionalUserId);
                    return !subtypeHashCodes.contains(TEST_SUBTYPE1.hashCode())
                            && !subtypeHashCodes.contains(TEST_SUBTYPE2.hashCode())
                            && subtypeHashCodes.contains(TEST_SUBTYPE3.hashCode());
                });
    }

    /**
     * Regression test for Bug 328098968.
     */
    @Test
    @EnsureHasAdditionalUser
    public void testClearAdditionalInputMethodSubtypeUponClearDataForBackgroundUser()
            throws Exception  {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference additionalUser = additionalUser(sDeviceState);
        final int currentUserId = currentUser.id();
        final int additionalUserId = additionalUser.id();
        assertThat(currentUserId).isNotEqualTo(additionalUserId);

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(additionalUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, additionalUserId);

        // Updating an already-installed APK clears additional subtypes (for a background user).
        runShellCommandOrThrow(
                ShellCommandUtils.clearPackageData(Ime2Constants.PACKAGE, additionalUserId));

        PollingCheck.check(
                "Additional Subtypes should not be cleared for user" + currentUserId,
                TIMEOUT, () -> {
                    final var subtypeHashCodes =
                            getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);
                    return subtypeHashCodes.contains(TEST_SUBTYPE1.hashCode())
                            && subtypeHashCodes.contains(TEST_SUBTYPE2.hashCode())
                            && !subtypeHashCodes.contains(TEST_SUBTYPE3.hashCode());
                });

        PollingCheck.check(
                "Additional Subtypes should not be cleared for user" + additionalUserId,
                TIMEOUT, () -> {
                    final var subtypeHashCodes =
                            getSubtypeHashCodes(Ime2Constants.IME_ID, additionalUserId);
                    return !subtypeHashCodes.contains(TEST_SUBTYPE1.hashCode())
                            && !subtypeHashCodes.contains(TEST_SUBTYPE2.hashCode())
                            && !subtypeHashCodes.contains(TEST_SUBTYPE3.hashCode());
                });
    }

    private static void callSetAdditionalInputMethodSubtype(
            @NonNull String authority, @NonNull String imeId,
            @NonNull InputMethodSubtype[] additionalSubtypes, int userId) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final Bundle args = new Bundle();
        args.putString(CommandProviderConstants.SET_ADDITIONAL_SUBTYPES_IMEID_KEY, imeId);
        args.putParcelableArray(CommandProviderConstants.SET_ADDITIONAL_SUBTYPES_SUBTYPES_KEY,
                additionalSubtypes);
        SystemUtil.runWithShellPermissionIdentity(
                () -> getContentResolverForUser(context, userId).call(authority,
                        CommandProviderConstants.SET_ADDITIONAL_SUBTYPES_COMMAND, null, args));
    }

    @NonNull
    private static ContentResolver getContentResolverForUser(@NonNull Context context, int userId) {
        try {
            return context.createPackageContextAsUser("android", 0, UserHandle.of(userId))
                    .getContentResolver();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static List<Integer> getSubtypeHashCodes(@NonNull String imeId, int userId) {
        return getInputMethodList(userId)
                .stream()
                .filter(imi -> TextUtils.equals(imi.getId(), imeId))
                .flatMap(imi -> StreamSupport.stream(
                        asSubtypeIterable(imi).spliterator(), false))
                .mapToInt(InputMethodSubtype::hashCode)
                .boxed()
                .collect(Collectors.toList());
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

    @NonNull
    private static Iterable<InputMethodSubtype> asSubtypeIterable(@NonNull InputMethodInfo imi) {
        final int subtypeCount = imi.getSubtypeCount();
        return new Iterable<>() {
            @Override
            public Iterator<InputMethodSubtype> iterator() {
                return new Iterator<>() {
                    private int mIndex = 0;

                    @Override
                    public boolean hasNext() {
                        return mIndex < subtypeCount;
                    }

                    @Override
                    public InputMethodSubtype next() {
                        final var value = imi.getSubtypeAt(mIndex);
                        mIndex++;
                        return value;
                    }
                };
            }

            @Override
            public void forEach(Consumer<? super InputMethodSubtype> action) {
                for (int i = 0; i < subtypeCount; ++i) {
                    action.accept(imi.getSubtypeAt(i));
                }
            }
        };
    }
}
