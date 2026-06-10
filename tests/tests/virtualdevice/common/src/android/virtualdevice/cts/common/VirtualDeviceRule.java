/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.virtualdevice.cts.common;

import static android.content.pm.PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.companion.AssociationInfo;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.Condition;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A test rule that allows for testing VDM and virtual device features.
 *
 * <p>The interaction with VDM / CDM requires the test to execute a lot of statements with specific
 * permissions held by Shell. Running logic in the individual tests with the Shell permission
 * identity may interfere with the rule setup and teardown.</p>
 *
 * <p>Do not use this rule alongside {@link AdoptShellPermissionsRule} or any other method to adopt
 * the Shell permission identity, like
 * {@link com.android.compatibility.common.util.SystemUtil#runWithShellPermissionIdentity}. Use
 * the utilities provided here (e.g. {@link VirtualDeviceRule#runWithTemporaryPermission}, which
 * will re-acquire the necessary permissions after the execution of the statement.</p>
 *
 * <p>Similarly, {@link android.platform.test.flag.junit.CheckFlagsRule} may interfere with the
 * permissions depending on the rule order because it also adopts the Shell identity and drops it
 * during teardown. Do not use this rule alongside the {@link VirtualDeviceRule}, it already
 * incorporates the flag rule in the correct order and the flag values can be accessed in the tests.
 * </p>
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
public class VirtualDeviceRule implements TestRule {

    /** General permissions needed for creating virtual devices and displays. */
    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.CREATE_VIRTUAL_DEVICE,
                    Manifest.permission.ADD_TRUSTED_DISPLAY,
                    Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY,
                    Manifest.permission.ADD_MIRROR_DISPLAY,
                    Manifest.permission.ASSOCIATE_COMPANION_DEVICES,
                    Manifest.permission.MANAGE_ROLE_HOLDERS,
                    Manifest.permission.BYPASS_ROLE_QUALIFICATION,
                    "android.permission.MANAGE_COMPANION_DEVICES",
            };

    public static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    public static final String DEFAULT_VIRTUAL_DISPLAY_NAME = "testVirtualDisplay";
    public static final int DEFAULT_VIRTUAL_DISPLAY_WIDTH = 640;
    public static final int DEFAULT_VIRTUAL_DISPLAY_HEIGHT = 480;
    public static final int DEFAULT_VIRTUAL_DISPLAY_DPI = 240;

    public static final ComponentName BLOCKED_ACTIVITY_COMPONENT =
            new ComponentName("android", "com.android.internal.app.BlockedAppStreamingActivity");

    private final String[] mPermissions;
    private final FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();
    private final VirtualDeviceTrackerRule mTrackerRule = new VirtualDeviceTrackerRule();

    private final Context mContext = getInstrumentation().getTargetContext();
    private final VirtualDeviceManager mVirtualDeviceManager =
            mContext.getSystemService(VirtualDeviceManager.class);
    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    /** A default virtual device for tests that only use the rule to access VDM functionality. */
    private VirtualDevice mDefaultVirtualDevice = null;

    /** Creates a rule with the required permissions for creating virtual devices and displays. */
    public static VirtualDeviceRule createDefault() {
        return new VirtualDeviceRule();
    }

    /** Creates a rule with any additional permission needed for the specific test. */
    public static VirtualDeviceRule withAdditionalPermissions(String... additionalPermissions) {
        return new VirtualDeviceRule(additionalPermissions);
    }

    private VirtualDeviceRule(String... permissions) {
        mPermissions =
                Stream.concat(Arrays.stream(REQUIRED_PERMISSIONS), Arrays.stream(permissions))
                        .toArray(String[]::new);
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        assumeNotNull(mVirtualDeviceManager);
        return RuleChain
                .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
                .around(new AdoptShellPermissionsRule(
                        getInstrumentation().getUiAutomation(), mPermissions))
                .around(mFakeAssociationRule)
                .around(mTrackerRule)
                .apply(base, description);
    }

    /**
     * Returns a default virtual device.
     */
    public VirtualDevice getDefaultVirtualDevice() {
        if (mDefaultVirtualDevice == null) {
            mDefaultVirtualDevice = createManagedVirtualDevice();
        }
        return mDefaultVirtualDevice;
    }

    /**
     * Returns the VirtualDevice object for the given deviceId
     */
    public android.companion.virtual.VirtualDevice getVirtualDevice(int deviceId) {
        if (BuildCompat.isAtLeastV()) {
            return mVirtualDeviceManager.getVirtualDevice(deviceId);
        } else {
            return mVirtualDeviceManager.getVirtualDevices().stream()
                    .filter(device -> device.getDeviceId() == deviceId).findFirst().orElse(null);
        }
    }

    /**
     * Creates a virtual device with default params that will be automatically closed when the
     * test is torn down.
     */
    @NonNull
    public VirtualDevice createManagedVirtualDevice() {
        return createManagedVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
    }

    /**
     * Creates a virtual device with the given params that will be automatically closed when the
     * test is torn down.
     */
    @NonNull
    public VirtualDevice createManagedVirtualDevice(@NonNull VirtualDeviceParams params) {
        final VirtualDevice virtualDevice = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(), params);
        mTrackerRule.mVirtualDevices.add(virtualDevice);
        return virtualDevice;
    }

    /**
     * Creates a virtual display associated with the given device that will be automatically
     * released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedVirtualDisplay(@NonNull VirtualDevice virtualDevice) {
        return createManagedVirtualDisplay(virtualDevice,
                createDefaultVirtualDisplayConfigBuilder());
    }

    /**
     * Creates a virtual display associated with the given device and flags that will be
     * automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedVirtualDisplayWithFlags(
            @NonNull VirtualDevice virtualDevice, int flags) {
        return createManagedVirtualDisplay(virtualDevice,
                createDefaultVirtualDisplayConfigBuilder().setFlags(flags));
    }

    /**
     * Creates a virtual display associated with the given device and config that will be
     * automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedVirtualDisplay(@NonNull VirtualDevice virtualDevice,
            @NonNull VirtualDisplayConfig.Builder builder) {
        return createManagedVirtualDisplay(virtualDevice, builder, /* callback= */ null);
    }

    /**
     * Creates a virtual display associated with the given device and config that will be
     * automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedVirtualDisplay(@NonNull VirtualDevice virtualDevice,
            @NonNull VirtualDisplayConfig.Builder builder,
            @Nullable VirtualDisplay.Callback callback) {
        VirtualDisplayConfig config = builder.build();
        final Surface surface = prepareSurface(config.getWidth(), config.getHeight());
        final VirtualDisplay virtualDisplay = virtualDevice.createVirtualDisplay(
                builder.setSurface(surface).build(), Runnable::run, callback);
        if (virtualDisplay != null) {
            assertDisplayExists(virtualDisplay.getDisplay().getDisplayId());
            // There's no need to track managed virtual displays to have them released on tear-down
            // because they will be released automatically when the VirtualDevice is closed.
        }
        return virtualDisplay;
    }

    /**
     * Creates a virtual display not associated with the any virtual device that will be
     * automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedUnownedVirtualDisplay() {
        return createManagedUnownedVirtualDisplay(createTrustedVirtualDisplayConfigBuilder());
    }

    /**
     * Creates a virtual display not associated with the any virtual device with the given flags
     * that will be automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedUnownedVirtualDisplayWithFlags(int flags) {
        return createManagedUnownedVirtualDisplay(
                createDefaultVirtualDisplayConfigBuilder().setFlags(flags));
    }

    /**
     * Creates a virtual display not associated with the any virtual device with the given config
     * that will be automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedUnownedVirtualDisplay(
            @NonNull VirtualDisplayConfig.Builder builder) {
        VirtualDisplayConfig config = builder.build();
        final Surface surface = prepareSurface(config.getWidth(), config.getHeight());
        final VirtualDisplay virtualDisplay =
                mContext.getSystemService(DisplayManager.class).createVirtualDisplay(
                        builder.setSurface(surface).build());
        if (virtualDisplay != null) {
            assertDisplayExists(virtualDisplay.getDisplay().getDisplayId());
            mTrackerRule.mVirtualDisplays.add(virtualDisplay);
        }
        return virtualDisplay;
    }

    /**
     * Default config for virtual display creation, with a predefined name, dimensions and an empty
     * surface.
     */
    @NonNull
    public static VirtualDisplayConfig.Builder createDefaultVirtualDisplayConfigBuilder() {
        return createDefaultVirtualDisplayConfigBuilder(
                DEFAULT_VIRTUAL_DISPLAY_WIDTH, DEFAULT_VIRTUAL_DISPLAY_HEIGHT);
    }

    /**
     * Config for trusted virtual display creation, which applies the flags
     * {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_TRUSTED},
     * {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_PUBLIC} and
     * {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY}. A predefined name and
     * dimensions and an empty surface are used.
     */
    @NonNull
    public static VirtualDisplayConfig.Builder createTrustedVirtualDisplayConfigBuilder() {
        return createDefaultVirtualDisplayConfigBuilder().setFlags(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    }

    /**
     * Default config for virtual display creation with custom dimensions.
     */
    @NonNull
    public static VirtualDisplayConfig.Builder createDefaultVirtualDisplayConfigBuilder(
            int width, int height) {
        return new VirtualDisplayConfig.Builder(
                DEFAULT_VIRTUAL_DISPLAY_NAME, width, height, DEFAULT_VIRTUAL_DISPLAY_DPI);
    }

    /**
     * Blocks until the display with the given ID is available.
     */
    public void assertDisplayExists(int displayId) {
        waitAndAssertWindowManagerState("Waiting for display to be available",
                () -> mWmState.getDisplay(displayId) != null);
    }

    /**
     * Blocks until the display with the given ID is removed.
     */
    public void assertDisplayDoesNotExist(int displayId) {
        waitAndAssertWindowManagerState("Waiting for display to be removed",
                () -> mWmState.getDisplay(displayId) == null);
    }

    /** Returns the WM state helper. */
    public WindowManagerStateHelper getWmState() {
        return mWmState;
    }

    /** Creates a new CDM association. */
    public AssociationInfo createManagedAssociation(String deviceProfile) {
        return mFakeAssociationRule.createManagedAssociation(deviceProfile);
    }

    /** Drops the current CDM association. */
    public void dropCompanionDeviceAssociation() {
        mFakeAssociationRule.disassociate();
    }

    /** Acquires all permissions needed for the VDM test setup and teardown. */
    public void acquireNecessaryPermissions() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(mPermissions);
    }

    /**
     * Temporarily assumes the given permissions and executes the given supplier. Reverts any
     * permissions currently held after the execution.
     */
    public <T> T runWithTemporaryPermission(Supplier<T> supplier, String... permissions) {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(permissions);
        try {
            return supplier.get();
        } finally {
            // Revert the permissions needed for the test again.
            acquireNecessaryPermissions();
        }
    }

    /**
     * Temporarily assumes the given permissions and executes the given runnable. Reverts any
     * permissions currently held after the execution.
     */
    public void runWithTemporaryPermission(Runnable runnable, String... permissions) {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(permissions);
        try {
            runnable.run();
        } finally {
            // Revert the permissions needed for the test again.
            acquireNecessaryPermissions();
        }
    }

    /**
     * Temporarily drops any permissions and executes the given supplier. Reverts any permissions
     * currently held after the execution.
     */
    public <T> T runWithoutPermissions(Supplier<T> supplier) {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        try {
            return supplier.get();
        } finally {
            // Revert the permissions needed for the test again.
            acquireNecessaryPermissions();
        }
    }

    /**
     * Temporarily drops any permissions and executes the given runnable. Reverts any permissions
     * currently held after the execution.
     */
    public void runWithoutPermissions(Runnable runnable) {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        try {
            runnable.run();
        } finally {
            // Revert the permissions needed for the test again.
            acquireNecessaryPermissions();
        }
    }

    /**
     * Sends the given intent to the given virtual display.
     */
    public void sendIntentToDisplay(Intent intent, VirtualDisplay virtualDisplay) {
        sendIntentToDisplay(intent, virtualDisplay.getDisplay().getDisplayId());
    }

    /**
     * Sends the given intent to the given display.
     */
    public void sendIntentToDisplay(Intent intent, int displayId) {
        assumeActivityLaunchSupported(displayId);
        mContext.startActivity(intent, createActivityOptions(displayId));
    }

    /**
     * Starts the activity for the given class on the given virtual display and blocks until it is
     * successfully launched there. The activity will be finished after the test run.
     */
    public <T extends Activity> T startActivityOnDisplaySync(
            VirtualDisplay virtualDisplay, Class<T> clazz) {
        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        return startActivityOnDisplaySync(displayId, clazz);
    }

    /**
     * Starts the activity for the given class on the given display and blocks until it is
     * successfully launched there. The activity will be finished after the test run.
     */
    public <T extends Activity> T startActivityOnDisplaySync(int displayId, Class<T> clazz) {
        return startActivityOnDisplaySync(displayId, new Intent(mContext, clazz)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    /**
     * Starts the activity for the given intent on the given virtual display and blocks until it is
     * successfully launched there. The activity will be finished after the test run.
     */
    public <T extends Activity> T startActivityOnDisplaySync(
            VirtualDisplay virtualDisplay, Intent intent) {
        return startActivityOnDisplaySync(virtualDisplay.getDisplay().getDisplayId(), intent);
    }

    /**
     * Starts the activity for the given intent on the given display and blocks until it is
     * successfully launched there. The activity will be finished after the test run.
     */
    public <T extends Activity> T startActivityOnDisplaySync(int displayId, Intent intent) {
        assumeActivityLaunchSupported(displayId);
        T activity = (T) getInstrumentation()
                .startActivitySync(intent, createActivityOptions(displayId));
        mTrackerRule.mActivities.add(activity);
        return activity;
    }

    /**
     * Creates activity options for launching activities on the given virtual display.
     */
    public static Bundle createActivityOptions(VirtualDisplay virtualDisplay) {
        return createActivityOptions(virtualDisplay.getDisplay().getDisplayId());
    }

    /**
     * Creates activity options for launching activities on the given display.
     */
    public static Bundle createActivityOptions(int displayId) {
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle();
    }

    /**
     * Skips the test if the device doesn't support virtual displays that can host activities.
     */
    public void assumeActivityLaunchSupported(int displayId) {
        if (displayId != Display.DEFAULT_DISPLAY) {
            assumeTrue(FeatureUtil.hasSystemFeature(FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
            // TODO(b/261155110): Re-enable once freeform mode is supported on virtual displays.
            assumeFalse(FeatureUtil.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));
        }
    }

    /**
     * Blocks until the given activity is in resumed state.
     */
    public void waitAndAssertActivityResumed(ComponentName componentName) {
        waitAndAssertWindowManagerState("Waiting for activity to be resumed",
                () -> mWmState.hasActivityState(componentName, WindowManagerState.STATE_RESUMED));
    }

    /**
     * Blocks until the given activity is in resumed state on the given display.
     */
    public void waitAndAssertActivityResumed(ComponentName componentName, int displayId) {
        waitAndAssertActivityResumed(componentName);
        mWmState.assertResumedActivities("Activity must be on display " + displayId,
                mapping -> mapping.put(displayId, componentName));
    }

    /**
     * Blocks until the given activity is gone.
     */
    public void waitAndAssertActivityRemoved(ComponentName componentName) {
        waitAndAssertWindowManagerState("Waiting for activity to be removed",
                () -> !mWmState.containsActivity(componentName));
    }

    /**
     * Override the default retry limit of WindowManagerStateHelper.
     * Destroying activities on virtual displays and destroying the virtual displays themselves
     * takes longer than the default timeout of 5s.
     */
    private void waitAndAssertWindowManagerState(
            String message, BooleanSupplier waitCondition) {
        final Condition<String> condition =
                new Condition<>(message, () -> {
                    mWmState.computeState();
                    return waitCondition.getAsBoolean();
                });
        condition.setRetryLimit(10);
        assertThat(Condition.waitFor(condition)).isTrue();
    }

    private Surface prepareSurface(int width, int height) {
        ImageReader imageReader = new ImageReader.Builder(width, height).build();
        mTrackerRule.mImageReaders.add(imageReader);
        return imageReader.getSurface();
    }

    /**
     * Internal rule that tracks all created virtual devices and displays and ensures they are
     * properly closed and released after the test.
     */
    private static final class VirtualDeviceTrackerRule extends ExternalResource {

        final ArrayList<VirtualDevice> mVirtualDevices = new ArrayList<>();
        final ArrayList<VirtualDisplay> mVirtualDisplays = new ArrayList<>();
        final ArrayList<Activity> mActivities = new ArrayList<>();
        final ArrayList<ImageReader> mImageReaders = new ArrayList<>();

        @Override
        protected void after() {
            for (Activity activity : mActivities) {
                activity.finish();
            }
            mActivities.clear();

            for (VirtualDevice virtualDevice : mVirtualDevices) {
                virtualDevice.close();
            }
            mVirtualDevices.clear();
            for (VirtualDisplay virtualDisplay : mVirtualDisplays) {
                virtualDisplay.release();
            }
            mVirtualDisplays.clear();
            for (ImageReader imageReader : mImageReaders) {
                imageReader.close();
            }
            mImageReaders.clear();
            super.after();
        }
    }
}
