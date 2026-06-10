/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.permissions;

import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Versions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default implementation of {@link PermissionContext}
 */
public final class PermissionContextImpl implements PermissionContextModifier {

    private final Permissions mPermissions;
    private final Set<String> mGrantedPermissions = new HashSet<>();
    private final Set<String> mDeniedPermissions = new HashSet<>();
    private final Set<String> mGrantedAppOps = new HashSet<>();
    private final Set<String> mDeniedAppOps = new HashSet<>();
    private final boolean mNonBlockingContext;

    private static final Lock sPermissionsLock = new ReentrantLock();
    private static final String TAG = "PermissionContextImpl";
    private static final List<PermissionContextImpl> sNotClosedContexts = new ArrayList<>();

    /**
     * creates PermissionContextImpl instance
     * it won't be executed until other threads will stop using permissions contexts
     */
    static PermissionContextImpl create(Permissions permissions) {
        Log.v(TAG, "locking permissionsLock...");
        try {
            boolean success = sPermissionsLock.tryLock(90, TimeUnit.SECONDS);
            if (!success) {
                var exception = new NeneException("unable to obtain the lock in "
                        + "PermissionContextImpl, make sure to not use permission contexts from "
                        + "more than one thread at a time");
                Log.e(TAG, Log.getStackTraceString(exception));
                throw exception;
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "unable to obtain the lock in PermissionContextImpl", e);
        }
        Log.v(TAG, "permissionsLock locked");
        return new PermissionContextImpl(permissions, false);
    }

    /**
     * creates a special version of PermissionContextImpl that allows other threads to create new
     * PermissionContextImpl objects, it's designed to be used only for handling annotations
     */
    static PermissionContextImpl createNonBlocking(Permissions permissions) {
        Log.v(TAG, "creating non blocking context");
        return new PermissionContextImpl(permissions, true);
    }

    static void closeAllContexts() {
        for (int i = 0; i < sNotClosedContexts.size(); i++) {
            PermissionContextImpl notClosedContext = sNotClosedContexts.get(i);
            Log.w(TAG, "closing not closed context: "
                    + (i + 1) + " of " + sNotClosedContexts.size()
                    + ", it should have been closed by now");
            notClosedContext.close();
        }
    }

    private PermissionContextImpl(Permissions permissions, boolean nonBlockingContext) {
        mPermissions = permissions;
        mNonBlockingContext = nonBlockingContext;
        sNotClosedContexts.add(this);
    }

    Set<String> grantedPermissions() {
        return mGrantedPermissions;
    }

    Set<String> deniedPermissions() {
        return mDeniedPermissions;
    }

    Set<String> grantedAppOps() {
        return mGrantedAppOps;
    }

    Set<String> deniedAppOps() {
        return mDeniedAppOps;
    }

    /**
     * See {@link Permissions#withPermission(String...)}
     */
    @Override
    public PermissionContextImpl withPermission(String... permissions) {
        for (String permission : permissions) {
            if (mDeniedPermissions.contains(permission)) {
                mPermissions.clearPermissions();
                close();
                throw new NeneException(
                        permission + " cannot be required to be both granted and denied");
            }
        }

        mGrantedPermissions.addAll(Arrays.asList(permissions));

        applyPermissions();

        return this;
    }

    /**
     * See {@link Permissions#withPermissionOnVersion(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersion(int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(sdkVersion, sdkVersion, permissions);
    }

    /**
     * See {@link Permissions#withPermissionOnVersionAtLeast(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersionAtLeast(
            int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(sdkVersion, Versions.ANY, permissions);
    }

    /**
     * See {@link Permissions#withPermissionOnVersionAtMost(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersionAtMost(
            int sdkVersion, String... permissions) {
        return withPermissionOnVersionBetween(Versions.ANY, sdkVersion, permissions);
    }

    /**
     * See {@link Permissions#withPermissionOnVersionBetween(int, String...)}
     */
    @Override
    public PermissionContextImpl withPermissionOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... permissions) {
        if (Versions.meetsSdkVersionRequirements(minSdkVersion, maxSdkVersion)) {
            return withPermission(permissions);
        }

        return this;
    }

    /**
     * See {@link Permissions#withoutPermission(String...)}
     */
    @Override
    public PermissionContextImpl withoutPermission(String... permissions) {
        for (String permission : permissions) {
            if (mGrantedPermissions.contains(permission)) {
                mPermissions.clearPermissions();
                close();
                throw new NeneException(
                        permission + " cannot be required to be both granted and denied");
            }
        }

        if (TestApis.packages().instrumented().isInstantApp()) {
            close();
            throw new NeneException(
                    "Tests which use withoutPermission must not run as instant apps. If you are"
                            + "using DeviceState, use the @RequireNotInstantApp annotation.");
        }

        mDeniedPermissions.addAll(Arrays.asList(permissions));

        applyPermissions();

        return this;
    }

    /**
     * See {@link Permissions#withAppOp(String...)}
     */
    @Override
    public PermissionContextImpl withAppOp(String... appOps) {
        for (String appOp : appOps) {
            if (mDeniedAppOps.contains(appOp)) {
                mPermissions.clearPermissions();
                close();
                throw new NeneException(
                        appOp + " cannot be required to be both granted and denied");
            }
        }

        mGrantedAppOps.addAll(Arrays.asList(appOps));

        applyPermissions();

        return this;
    }

    /**
     * See {@link Permissions#withAppOpOnVersion(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersion(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(sdkVersion, sdkVersion, appOps);
    }

    /**
     * See {@link Permissions#withAppOpOnVersionAtMost(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersionAtMost(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(Versions.ANY, sdkVersion, appOps);
    }

    /**
     * See {@link Permissions#withAppOpOnVersionAtLeast(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersionAtLeast(int sdkVersion, String... appOps) {
        return withAppOpOnVersionBetween(sdkVersion, Versions.ANY, appOps);
    }

    /**
     * See {@link Permissions#withAppOpOnVersionBetween(int, String...)}
     */
    @Override
    public PermissionContextImpl withAppOpOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... appOps) {
        if (Versions.meetsSdkVersionRequirements(minSdkVersion, maxSdkVersion)) {
            return withAppOp(appOps);
        }

        return this;
    }

    /**
     * See {@link Permissions#withoutAppOp(String...)}.
     */
    @Override
    public PermissionContextImpl withoutAppOp(String... appOps) {
        for (String appOp : appOps) {
            if (mGrantedAppOps.contains(appOp)) {
                mPermissions.clearPermissions();
                close();
                throw new NeneException(
                        appOp + " cannot be required to be both granted and denied");
            }
        }

        mDeniedAppOps.addAll(Arrays.asList(appOps));

        applyPermissions();

        return this;
    }

    private void applyPermissions() {
        try {
            mPermissions.applyPermissions();
        } catch (NeneException e) {
            Log.d(TAG, "caught NeneException while executing applyPermissions(), "
                    + "closing the context");
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        Permissions.sInstance.undoPermission(this);
        if (!mNonBlockingContext) {
            sPermissionsLock.unlock();
            Log.v(TAG, "permissionsLock unlocked");
        }
        sNotClosedContexts.remove(this);
    }
}
