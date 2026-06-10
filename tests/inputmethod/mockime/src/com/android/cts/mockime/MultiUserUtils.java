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

package com.android.cts.mockime;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.UiAutomation;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArraySet;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingSupplier;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for testing multi-user scenarios.
 *
 * <p>TODO(b/323251870): Consider creating a new utility class to host logic like this.</p>
 */
final class MultiUserUtils {
    /**
     * Not intended to be instantiated.
     */
    private MultiUserUtils() {
    }

    @Nullable
    private static <T> T runWithShellPermissionIdentity(@NonNull UiAutomation uiAutomation,
            @NonNull ThrowingSupplier<T> supplier, String... permissions) {
        Object[] placeholder = new Object[1];
        SystemUtil.runWithShellPermissionIdentity(uiAutomation, () -> {
            placeholder[0] = supplier.get();
        }, permissions);
        return (T) placeholder[0];
    }

    @NonNull
    private static String runShellCommandOrThrow(@NonNull UiAutomation uiAutomation,
            @NonNull String cmd) {
        try {
            return runShellCommand(uiAutomation, cmd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private static <T> T runWithShellPermissionIdentity(@NonNull UiAutomation uiAutomation,
            @NonNull ThrowingSupplier<T> supplier) {
        return runWithShellPermissionIdentity(uiAutomation, supplier,
                (String[]) null /* permissions */);
    }

    @NonNull
    static Bundle callContentProvider(@NonNull Context context, @NonNull UiAutomation uiAutomation,
            @NonNull String authority, @NonNull String method, @Nullable String arg,
            @Nullable Bundle extras, @NonNull UserHandle user) {
        return Objects.requireNonNull(runWithShellPermissionIdentity(uiAutomation, () -> {
            final Context userAwareContext;
            try {
                userAwareContext = context.createPackageContextAsUser("android", 0, user);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            return userAwareContext.getContentResolver().call(authority, method, arg, extras);
        }));
    }

    @Nullable
    static InputMethodInfo getCurrentInputMethodInfoAsUser(@NonNull Context context,
            @NonNull UiAutomation uiAutomation, @NonNull UserHandle user) {
        Objects.requireNonNull(user);
        final InputMethodManager imm = Objects.requireNonNull(
                context.getSystemService(InputMethodManager.class));
        return runWithShellPermissionIdentity(uiAutomation, () ->
                imm.getCurrentInputMethodInfoAsUser(user),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    @Nullable
    static String getSecureSettings(@NonNull UiAutomation uiAutomation, @NonNull String setting,
            @NonNull UserHandle user) {
        Objects.requireNonNull(user);
        final var command = "settings get --user " + user.getIdentifier() + " secure " + setting;
        return runShellCommandOrThrow(uiAutomation, command).stripTrailing();
    }

    @NonNull
    static List<ApplicationExitInfo> getHistoricalProcessExitReasons(@NonNull Context context,
            @NonNull UiAutomation uiAutomation, @Nullable String packageName,
            @IntRange(from = 0) int pid, @IntRange(from = 0) int maxNum, @NonNull UserHandle user) {
        return Objects.requireNonNull(runWithShellPermissionIdentity(uiAutomation, () -> {
            final Context userAwareContext;
            try {
                userAwareContext = context.createPackageContextAsUser("android", 0, user);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            return Objects.requireNonNull(userAwareContext.getSystemService(ActivityManager.class))
                    .getHistoricalProcessExitReasons(packageName, pid, maxNum);
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL, Manifest.permission.DUMP));
    }

    @NonNull
    static List<InputMethodInfo> getInputMethodListAsUser(@NonNull Context context,
            @NonNull UiAutomation uiAutomation, @NonNull UserHandle user) {
        final InputMethodManager imm = Objects.requireNonNull(
                context.getSystemService(InputMethodManager.class));
        return Objects.requireNonNull(runWithShellPermissionIdentity(uiAutomation,
                () -> imm.getInputMethodListAsUser(user.getIdentifier()),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.QUERY_ALL_PACKAGES));
    }

    @NonNull
    static List<InputMethodInfo> getEnabledInputMethodListAsUser(@NonNull Context context,
            @NonNull UiAutomation uiAutomation, @NonNull UserHandle user) {
        final InputMethodManager imm = Objects.requireNonNull(
                context.getSystemService(InputMethodManager.class));
        final List<InputMethodInfo> result = runWithShellPermissionIdentity(uiAutomation, () -> {
            try {
                return imm.getEnabledInputMethodListAsUser(user);
            } catch (NoSuchMethodError unused) {
                return null;
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL, Manifest.permission.QUERY_ALL_PACKAGES);
        if (result != null) {
            return result;
        }

        // Use the shell command as a fallback.
        final String command = "ime list -s --user " + user.getIdentifier();
        final var enabledImes = new ArraySet<>(
                runShellCommandOrThrow(uiAutomation, command).split("\n"));
        final List<InputMethodInfo> imes = getInputMethodListAsUser(context, uiAutomation, user);
        imes.removeIf(imi -> !enabledImes.contains(imi.getId()));
        return imes;
    }

    @NonNull
    static AutoCloseable acquireUnstableContentProviderClientSession(@NonNull Context context,
            @NonNull UiAutomation uiAutomation, @NonNull String name, @NonNull UserHandle user) {
        return Objects.requireNonNull(runWithShellPermissionIdentity(uiAutomation, () -> {
            final Context userAwareContext;
            try {
                userAwareContext = context.createPackageContextAsUser("android", 0, user);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            final ContentProviderClient client = Objects.requireNonNull(
                    userAwareContext.getContentResolver()
                            .acquireUnstableContentProviderClient(name));
            return () -> SystemUtil.runWithShellPermissionIdentity(uiAutomation, client::close);
        }));
    }
}
