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

package android.server.wm.jetpack.area;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.PollingCheck.waitFor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.devicestate.DeviceState;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.feature.flags.Flags;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.DeviceStateUtils;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestActivityLauncher;
import android.server.wm.jetpack.utils.TestRearDisplayActivity;
import android.server.wm.jetpack.utils.WindowExtensionTestRule;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.window.extensions.area.WindowAreaComponent;
import androidx.window.extensions.core.util.function.Consumer;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Rear display presentation tests specific to {@link
 * DeviceState.PROPERTY_FEATURE_REAR_DISPLAY_OUTER_DEFAULT}. In other words, general RDM tests
 * should continue to exist in {@link ExtensionRearDisplayTest} while functionality specific to this
 * new property should be here.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerJetpackTestCases:ExtensionRearDisplayOuterDefaultTest
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_DEVICE_STATE_RDM_V2)
public class ExtensionRearDisplayOuterDefaultTest extends WindowManagerJetpackTestBase
        implements DeviceStateManager.DeviceStateCallback {

    private static final int TIMEOUT = 2000;

    private final Context mInstrumentationContext = getInstrumentation().getTargetContext();
    private final DeviceStateManager mDeviceStateManager =
            mInstrumentationContext.getSystemService(DeviceStateManager.class);
    private final DisplayManager mDisplayManager =
            mInstrumentationContext.getSystemService(DisplayManager.class);
    private final ActivityManager mActivityManager =
            mInstrumentationContext.getSystemService(ActivityManager.class);
    private WindowAreaComponent mWindowAreaComponent;
    private DeviceState mCurrentDeviceState;
    private TestRearDisplayActivity mActivity;

    @WindowAreaComponent.WindowAreaSessionState
    private int mWindowAreaSessionState = WindowAreaComponent.SESSION_STATE_INACTIVE;

    @WindowAreaComponent.WindowAreaStatus
    private int mWindowAreaStatus = WindowAreaComponent.STATUS_UNSUPPORTED;

    @Nullable private Display mInnerDisplay;

    private final Consumer<Integer> mSessionStateListener =
            (sessionState) -> mWindowAreaSessionState = sessionState;
    private final Consumer<Integer> mStatusListener = (status) -> mWindowAreaStatus = status;

    @Rule
    public final WindowExtensionTestRule mWindowManagerJetpackTestRule =
            new WindowExtensionTestRule(WindowAreaComponent.class);

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        final List<DeviceState> supportedStates = mDeviceStateManager.getSupportedDeviceStates();
        DeviceState rearDisplayOuterDefaultState = null;
        for (DeviceState state : supportedStates) {
            if (state.hasProperty(DeviceState.PROPERTY_FEATURE_REAR_DISPLAY_OUTER_DEFAULT)) {
                rearDisplayOuterDefaultState = state;
                break;
            }
        }
        assumeNotNull(rearDisplayOuterDefaultState);
        assertTrue(
                rearDisplayOuterDefaultState.hasProperty(
                        DeviceState.PROPERTY_FEATURE_REAR_DISPLAY));

        mWindowAreaComponent =
                (WindowAreaComponent) mWindowManagerJetpackTestRule.getExtensionComponent();
        mWindowAreaComponent.addRearDisplayStatusListener(mStatusListener);
        mDeviceStateManager.registerCallback(Runnable::run, this);

        mActivity = startActivityNewTask(TestRearDisplayActivity.class);

        assumeFalse(
                "Should not be in RDM",
                mCurrentDeviceState.hasProperties(
                        DeviceState.PROPERTY_FEATURE_REAR_DISPLAY,
                        DeviceState.PROPERTY_FEATURE_REAR_DISPLAY_OUTER_DEFAULT));
    }

    @After
    @Override
    public void tearDown() throws Throwable {
        super.tearDown();
        if (mWindowAreaComponent != null) {
            mWindowAreaComponent.removeRearDisplayStatusListener(mStatusListener);
            mDeviceStateManager.unregisterCallback(this);
            try {
                DeviceStateUtils.runWithControlDeviceStatePermission(
                        mDeviceStateManager::cancelStateRequest);
                DeviceStateUtils.runWithControlDeviceStatePermission(
                        mDeviceStateManager::cancelBaseStateOverride);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    @ApiTest(
            apis = {
                "androidx.window.extensions.area.WindowAreaComponent#startRearDisplaySession",
                "androidx.window.extensions.area.WindowAreaComponent#endRearDisplaySession"
            })
    @Test
    public void testStartAndStopRearDisplayMode() throws Throwable {
        startRearDisplayMode();
        stopRearDisplayMode();
    }

    @ApiTest(
            apis = {
                "androidx.window.extensions.area.WindowAreaComponent#startRearDisplaySession",
                "androidx.window.extensions.area.WindowAreaComponent#endRearDisplaySession"
            })
    @Test(expected = SecurityException.class)
    public void testStartActivityOnRearDisplay() throws Throwable {
        startRearDisplayMode();

        final TestActivityLauncher<TestActivity> launcher =
                launcherForNewActivity(TestActivity.class, mInnerDisplay.getDisplayId());
        final boolean allowed =
                mActivityManager.isActivityStartAllowedOnDisplay(
                        mInstrumentationContext,
                        mInnerDisplay.getDisplayId(),
                        launcher.getIntent());
        assertFalse("Should not be allowed to launch", allowed);

        // Should throw SecurityException
        launcher.launch(mInstrumentation);
    }

    private void stopRearDisplayMode() throws Throwable {
        DeviceStateUtils.runWithControlDeviceStatePermission(
                () -> mDeviceStateManager.cancelStateRequest());
        waitAndAssert(() -> mWindowAreaSessionState == WindowAreaComponent.SESSION_STATE_INACTIVE);
        waitAndAssert(() -> mWindowAreaStatus == WindowAreaComponent.STATUS_AVAILABLE);
        waitAndAssert(
                () ->
                        mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_REAR).length
                                == 0);
    }

    private void startRearDisplayMode() throws Throwable {
        DeviceStateUtils.runWithControlDeviceStatePermission(
                () ->
                        mWindowAreaComponent.startRearDisplaySession(
                                mActivity, mSessionStateListener));
        waitAndAssert(() -> isActivityVisible(mActivity));
        waitAndAssert(() -> mWindowAreaSessionState == WindowAreaComponent.SESSION_STATE_ACTIVE);
        assertTrue(
                mCurrentDeviceState.hasProperties(
                        DeviceState.PROPERTY_FEATURE_REAR_DISPLAY,
                        DeviceState.PROPERTY_FEATURE_REAR_DISPLAY_OUTER_DEFAULT));
        assertEquals(WindowAreaComponent.STATUS_ACTIVE, mWindowAreaStatus);

        waitAndAssert(
                () -> mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_REAR).length > 0);
        mInnerDisplay = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_REAR)[0];
    }

    @Override
    public void onDeviceStateChanged(@NonNull DeviceState state) {
        mCurrentDeviceState = state;
    }

    private void waitAndAssert(PollingCheck.PollingCheckCondition condition) {
        waitFor(TIMEOUT, condition);
    }
}
