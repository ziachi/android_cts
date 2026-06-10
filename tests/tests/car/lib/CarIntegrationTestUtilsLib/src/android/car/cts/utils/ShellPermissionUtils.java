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

package android.car.cts.utils;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.annotation.IntDef;
import android.util.ArraySet;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.AssumptionViolatedException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Set;

/**
 * Class contains static methods to adopt all or a subset of the shell's permissions while invoking
 * a {@link ThrowingRunnable}. Also, if an {@link AssumptionViolatedException} is thrown while
 * executing {@link ThrowingRunnable}, it will pass it through to the test infrastructure.
 */
public final class ShellPermissionUtils {

    private ShellPermissionUtils() {
    }

    /**
     * No check for granted permissions.
     */
    public static final int CHECK_MODE_NONE = 1;
    /**
     * Skip the test if the required permissions are not granted after trying to adopt the shell
     * permissions.
     */
    public static final int CHECK_MODE_ASSUME = 2;
    /**
     * Fail the test if the required permissions are not granted after trying to adopt the shell
     * permissions.
     */
    public static final int CHECK_MODE_ASSERT = 3;

    /** @hide */
    @IntDef(prefix = {"CHECK_MODE_"}, value = {
            CHECK_MODE_NONE,
            CHECK_MODE_ASSUME,
            CHECK_MODE_ASSERT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PermissionCheckMode {}

    /**
     * Run {@code throwingRunnable} with all the shell's permissions adopted.
     *
     * <p>This function allows nesting. It is guaranteed to restore the previous permission after
     * return.
     */
    public static void runWithShellPermissionIdentity(ThrowingRunnable throwingRunnable) {
        Set<String> adoptablePermissions = TestApis.permissions().adoptablePermissions();
        runWithShellPermissionIdentity(throwingRunnable, CHECK_MODE_NONE,
                adoptablePermissions.toArray(new String[0]));
    }

    /**
     * Run {@code throwingRunnable} with a subset, {@code permissions}, of the shell's permissions
     * adopted.
     *
     * <p>This will skip the test case if the required permissions are not granted after trying
     * to adopt the shell permissions.
     */
    public static void runWithShellPermissionIdentity(ThrowingRunnable throwingRunnable,
            String... permissions) {
        runWithShellPermissionIdentity(throwingRunnable, CHECK_MODE_ASSUME, permissions);
    }

    /**
     * Run {@code throwingRunnable} with a subset, {@code permissions}, of the shell's permissions
     * adopted.
     *
     * <p>The {@code checkMode} specified whether to skip/fail/do nothing if failed to adopt the
     * required permissions.
     *
     * <p>This function allows nesting. It is guaranteed to restore the previous permission after
     * return.
     */
    public static void runWithShellPermissionIdentity(ThrowingRunnable throwingRunnable,
            @PermissionCheckMode int checkMode, String... permissions) {
        Set<String> permissionsSet = new ArraySet<>();
        for (int i = 0; i < permissions.length; i++) {
            permissionsSet.add(permissions[i]);
        }
        Set<String> adoptablePermissions = TestApis.permissions().adoptablePermissions();
        String msg = "Unable to adopt shell permission: " + Arrays.toString(permissions);
        if (checkMode == CHECK_MODE_ASSERT) {
            assertWithMessage(msg).that(adoptablePermissions).containsAtLeastElementsIn(
                    permissionsSet);
        } else if (checkMode == CHECK_MODE_ASSUME) {
            assumeTrue(msg, adoptablePermissions.containsAll(permissionsSet));
        }

        try (PermissionContext p = TestApis.permissions().withPermission(permissions)) {
            throwingRunnable.run();
        } catch (AssumptionViolatedException e) {
            // Make sure we allow AssumptionViolatedExceptions through.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        }
    }
}
