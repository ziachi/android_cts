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

package android.virtualdevice.cts.applaunch;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_BLOCKED_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.VirtualDeviceManager.ActivityListener;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.StreamedAppConstants;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests for blocking of activities that should not be shown on the virtual device.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityBlockingTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getTargetContext();
    private final ActivityManager mActivityManager =
            mContext.getSystemService(ActivityManager.class);

    private final Intent mEmptyActivityIntent = new Intent(mContext, EmptyActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

    // Monitor an activity in a different APK to test cross-task navigations.
    private final Intent mMonitoredIntent = new Intent(Intent.ACTION_MAIN)
            .setComponent(StreamedAppConstants.CUSTOM_HOME_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;

    @Mock
    private ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void nonTrustedDisplay_startNonEmbeddableActivity_shouldThrowSecurityException() {
        createVirtualDeviceAndNonTrustedDisplay();
        mRule.assumeActivityLaunchSupported(mVirtualDisplay.getDisplay().getDisplayId());
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, mVirtualDisplay.getDisplay().getDisplayId(), mMonitoredIntent))
                .isFalse();
        assertThrows(SecurityException.class,
                () -> mRule.sendIntentToDisplay(mMonitoredIntent, mVirtualDisplay));
    }

    @Test
    public void cannotDisplayOnRemoteActivity_shouldBeBlockedFromLaunching() {
        createVirtualDeviceAndTrustedDisplay();
        Intent blockedIntent = new Intent(mContext, CannotDisplayOnRemoteActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertActivityLaunchBlocked(blockedIntent);
    }

    @Test
    public void trustedDisplay_startNonEmbeddableActivity_shouldSucceed() {
        createVirtualDeviceAndTrustedDisplay();
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void setAllowedActivities_shouldBlockNonAllowedActivities() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void setAllowedActivities_shouldAllowActivitiesInAllowlist() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void setBlockedActivities_shouldBlockActivityFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void setBlockedActivities_shouldAllowOtherActivitiesToLaunch() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void setBlockedActivities_removeExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void setAllowedActivities_removeExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void defaultActivityPolicy_addExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void defaultActivityPolicy_addPackageExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void customActivityPolicy_addExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void customActivityPolicy_addPackageExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void defaultActivityPolicy_removeExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void defaultActivityPolicy_removePackageExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void customActivityPolicy_removeExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void customActivityPolicy_removePackageExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void customActivityPolicy_changeToDefault_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void defaultActivityPolicy_changeToCustom_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void defaultActivityPolicy_changePolicy_clearsExemptions() {
        // Initially, allow launches by default except for the monitored component.
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Changing the policy to block by default still blocks it as it is not exempt anymore.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Making it exempt allows for launching it.
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);

        // Changing the policy to allow by default allows it as the exemptions were cleared.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchAllowed(mMonitoredIntent);

        // Adding an exemption blocks it from launching.
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Changing the policy to its current value does not affect the exemptions.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void defaultActivityPolicy_changePolicy_clearsPackageExemptions() {
        // Initially, allow launches by default except for the monitored component.
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Changing the policy to block by default still blocks it as it is not exempt anymore.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Making it exempt allows for launching it.
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        // Changing the policy to allow by default allows it as the exemptions were cleared.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchAllowed(mMonitoredIntent);

        // Adding an exemption blocks it from launching.
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Changing the policy to its current value does not affect the exemptions.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void testPerDisplayActivityPolicy() {
        // Allow launches by default except for the monitored component.
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());

        // Create a new display that will have custom policy.
        VirtualDisplay customPolicyDisplay = mRule.createManagedVirtualDisplay(
                mVirtualDevice, VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        final int customPolicyDisplayId = customPolicyDisplay.getDisplay().getDisplayId();

        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchBlocked(mMonitoredIntent, customPolicyDisplay);

        // Removing an exemption applies only to that display.
        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setComponentName(mMonitoredIntent.getComponent())
                .setDisplayId(customPolicyDisplayId)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchAllowed(mMonitoredIntent, customPolicyDisplay);

        // Set the display policy policy to block by default.
        mVirtualDevice.setDevicePolicy(
                POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM, customPolicyDisplayId);
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchBlocked(mMonitoredIntent, customPolicyDisplay);

        // Adding an exemption allows for launching it.
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setComponentName(mMonitoredIntent.getComponent())
                .setDisplayId(customPolicyDisplayId)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchAllowed(mMonitoredIntent, customPolicyDisplay);

        // Changing the device level exemption applies to all displays.
        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchBlocked(mMonitoredIntent, customPolicyDisplay);
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchAllowed(mMonitoredIntent, customPolicyDisplay);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void testPerDisplayActivityPolicy_packageExemption() {
        // Allow launches by default except for the monitored component.
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());

        // Create a new display that will have custom policy.
        VirtualDisplay customPolicyDisplay = mRule.createManagedVirtualDisplay(
                mVirtualDevice, VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        final int customPolicyDisplayId = customPolicyDisplay.getDisplay().getDisplayId();

        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchBlocked(mMonitoredIntent, customPolicyDisplay);

        // Removing an exemption applies only to that display.
        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .setDisplayId(customPolicyDisplayId)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchAllowed(mMonitoredIntent, customPolicyDisplay);

        // Set the display policy policy to block by default.
        mVirtualDevice.setDevicePolicy(
                POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM, customPolicyDisplayId);
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchBlocked(mMonitoredIntent, customPolicyDisplay);

        // Adding an exemption allows for launching it.
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .setDisplayId(customPolicyDisplayId)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchAllowed(mMonitoredIntent, customPolicyDisplay);

        // Changing the device level exemption applies to all displays.
        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchBlocked(mMonitoredIntent, customPolicyDisplay);
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent, mVirtualDisplay);
        assertActivityLaunchAllowed(mMonitoredIntent, customPolicyDisplay);
    }

    /** Test all combinations of default policy, package exemption and component exemption. */
    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void testActivityPolicy_componentAndPackageExemptions() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setComponentName(mMonitoredIntent.getComponent())
                .build());
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        // Allowed by default but exempt by both component and package policy.
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setComponentName(mMonitoredIntent.getComponent())
                .build());
        // Allowed by default but still exempt by the package policy.
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        // Allowed by default with no exemptions.
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setComponentName(mMonitoredIntent.getComponent())
                .build());
        // Allowed by default but exempt by component level policy.
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setComponentName(mMonitoredIntent.getComponent())
                .build());
        mVirtualDevice.addActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        // Blocked by default but exempt by both component and package policy.
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setComponentName(mMonitoredIntent.getComponent())
                .build());
        // Blocked by default but still exempt by the package policy.
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(new ActivityPolicyExemption.Builder()
                .setPackageName(mMonitoredIntent.getComponent().getPackageName())
                .build());
        // Blocked by default with no exemptions.
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        // Blocked by default but exempt by component level policy.
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void autoMirrorDisplay_shouldNotLaunchActivity() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        assertNoActivityLaunched(mMonitoredIntent);
    }

    @Test
    public void publicDisplay_shouldNotLaunchActivity() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        assertNoActivityLaunched(mMonitoredIntent);
    }

    @Test
    public void publicAutoMirrorDisplay_shouldNotLaunchActivity() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        assertNoActivityLaunched(mMonitoredIntent);
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertBlockedAppStreamingActivityLaunched();
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldAllowNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(Set.of(mMonitoredIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertActivityLaunched(mMonitoredIntent.getComponent());
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldAllowNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertActivityLaunched(mMonitoredIntent.getComponent());
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(Set.of(mMonitoredIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertBlockedAppStreamingActivityLaunched();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void blockedActivity_customBlockedActivityLaunchPolicy() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_BLOCKED_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());

        mRule.sendIntentToDisplay(mMonitoredIntent, mVirtualDisplay);
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onActivityLaunchBlocked(
                eq(mVirtualDisplay.getDisplay().getDisplayId()),
                eq(mMonitoredIntent.getComponent()), any(), any());
        assertNoActivityLaunched(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void blockedActivity_intentSenderPassedToListener() {
        createVirtualDeviceAndTrustedDisplay();
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        Intent blockedIntent = new Intent(mContext, CannotDisplayOnRemoteActivity.class);
        emptyActivity.startActivity(blockedIntent);

        var intentSender = ArgumentCaptor.forClass(IntentSender.class);
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onActivityLaunchBlocked(
                eq(mVirtualDisplay.getDisplay().getDisplayId()),
                eq(blockedIntent.getComponent()), any(), intentSender.capture());
        assertThat(intentSender.getValue()).isNotNull();
        assertThat(intentSender.getValue().getCreatorPackage())
                .isEqualTo(mContext.getPackageName());
        assertThat(intentSender.getValue().getCreatorUserHandle()).isEqualTo(mContext.getUser());

        // Ensure that the intent can be sent to another display. For this it needs to go into a
        // new task.
        Intent fillInIntent = new Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle activityOptions = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .setLaunchDisplayId(DEFAULT_DISPLAY)
                .toBundle();
        try {
            emptyActivity.startIntentSender(intentSender.getValue(), fillInIntent,
                    /* flagsMask= */ 0, /* flagsValues= */ 0, /* extraFlags= */ 0, activityOptions);
        } catch (IntentSender.SendIntentException e) {
            fail("No exception expected: " + e);
        }
        mRule.waitAndAssertActivityResumed(blockedIntent.getComponent(), DEFAULT_DISPLAY);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ACTIVITY_CONTROL_API)
    @Test
    public void blockedActivity_resultExpected_intentSenderNotPassedToListener() {
        createVirtualDeviceAndTrustedDisplay();
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        Intent blockedIntent = new Intent(mContext, CannotDisplayOnRemoteActivity.class);
        emptyActivity.startActivityForResult(blockedIntent, /* requestCode= */ 0);

        var intentSender = ArgumentCaptor.forClass(IntentSender.class);
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onActivityLaunchBlocked(
                eq(mVirtualDisplay.getDisplay().getDisplayId()),
                eq(blockedIntent.getComponent()), any(), intentSender.capture());
        assertThat(intentSender.getValue()).isNull();
    }

    private void createVirtualDeviceAndNonTrustedDisplay() {
        createVirtualDeviceAndDisplay(
                new VirtualDeviceParams.Builder().build(), /* virtualDisplayFlags= */0);
    }

    private void createVirtualDeviceAndTrustedDisplay() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build());
    }

    private void createVirtualDeviceAndTrustedDisplay(VirtualDeviceParams virtualDeviceParams) {
        createVirtualDeviceAndDisplay(
                virtualDeviceParams, DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    private void createVirtualDeviceAndTrustedDisplay(
            VirtualDeviceParams virtualDeviceParams, int virtualDisplayFlags) {
        createVirtualDeviceAndDisplay(
                virtualDeviceParams,
                virtualDisplayFlags | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    private void createVirtualDeviceAndDisplay(
            VirtualDeviceParams virtualDeviceParams, int virtualDisplayFlags) {
        mVirtualDevice = mRule.createManagedVirtualDevice(virtualDeviceParams);
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        mVirtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(virtualDisplayFlags));
    }

    /**
     * Assert that starting an activity with the given intent actually starts
     * BlockedAppStreamingActivity.
     */
    private void assertActivityLaunchBlocked(Intent intent) {
        assertActivityLaunchBlocked(intent, mVirtualDisplay);
    }

    private void assertActivityLaunchBlocked(Intent intent, VirtualDisplay display) {
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, display.getDisplay().getDisplayId(), intent)).isFalse();
        mRule.sendIntentToDisplay(intent, display);
        if (Flags.activityControlApi()) {
            verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onActivityLaunchBlocked(
                    eq(display.getDisplay().getDisplayId()),
                    eq(intent.getComponent()), any(), any());
        }
        assertBlockedAppStreamingActivityLaunched(display);
    }

    /**
     * Assert that no activity is launched with the given intent.
     */
    private void assertNoActivityLaunched(Intent intent) {
        mRule.sendIntentToDisplay(intent, mVirtualDisplay);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, mVirtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        verify(mActivityListener, never()).onTopActivityChanged(
                eq(mVirtualDisplay.getDisplay().getDisplayId()), any(), anyInt());
        reset(mActivityListener);
    }

    /**
     * Assert that launching an activity is successful with the given intent.
     */
    private void assertActivityLaunchAllowed(Intent intent) {
        assertActivityLaunchAllowed(intent, mVirtualDisplay);
    }

    private void assertActivityLaunchAllowed(Intent intent, VirtualDisplay display) {
        mRule.sendIntentToDisplay(intent, display);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, display.getDisplay().getDisplayId(), intent)).isTrue();
        assertActivityLaunched(intent.getComponent(), display);
    }

    private void assertBlockedAppStreamingActivityLaunched() {
        assertBlockedAppStreamingActivityLaunched(mVirtualDisplay);
    }

    private void assertBlockedAppStreamingActivityLaunched(VirtualDisplay display) {
        assertActivityLaunched(VirtualDeviceRule.BLOCKED_ACTIVITY_COMPONENT, display);
    }

    private void assertActivityLaunched(ComponentName componentName) {
        assertActivityLaunched(componentName, mVirtualDisplay);
    }

    private void assertActivityLaunched(ComponentName componentName, VirtualDisplay display) {
        verify(mActivityListener, timeout(TIMEOUT_MILLIS).atLeastOnce()).onTopActivityChanged(
                eq(display.getDisplay().getDisplayId()), eq(componentName), anyInt());
        reset(mActivityListener);
    }

    /** An empty activity that cannot be displayed on remote devices. */
    public static class CannotDisplayOnRemoteActivity extends EmptyActivity {}
}

