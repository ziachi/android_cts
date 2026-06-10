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

package android.virtualdevice.cts.core;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.Settings;
import android.server.wm.UiDeviceUtils;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.server.power.nano.PowerManagerServiceDumpProto;
import com.android.server.power.nano.WakeLockProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

/** Tests to verify that power manager APIs behave as expected for virtual devices. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDevicePowerTest {

    private static final String TAG = "VirtualDevicePowerTest";

    // A short timeout to trigger screen off to speed up the tests.
    private static final int FAST_SCREEN_OFF_TIMEOUT_MS = 500;
    // Custom display timeout.
    private static final int DISPLAY_TIMEOUT_MS = 2000;
    // Timeout we give for the callbacks to be triggered.
    private static final int CALLBACK_TIMEOUT_MS = 5000;

    // Custom default and dim brightness.
    private static final float DEFAULT_BRIGHTNESS = 0.4f;
    private static final float DIM_BRIGHTNESS = 0.1f;

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            android.Manifest.permission.WAKE_LOCK,
            android.Manifest.permission.WRITE_SETTINGS,
            android.Manifest.permission.WRITE_SECURE_SETTINGS);

    private final Context mContext = getInstrumentation().getContext();
    private final ContentResolver mContentResolver = mContext.getContentResolver();

    private PowerManager mDefaultDisplayPowerManager;
    private PowerManager mVirtualDisplayPowerManager;

    private String mInitialDisplayTimeout;
    private int mInitialStayOnWhilePluggedInSetting;
    private int mMinimumScreenOffTimeoutMs;

    private VirtualDevice mVirtualDevice;
    private Display mDisplay;

    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock
    private VirtualDisplayConfig.BrightnessListener mBrightnessListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mInitialDisplayTimeout =
                Settings.System.getString(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT);
        mInitialStayOnWhilePluggedInSetting =
                Settings.Global.getInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
        Settings.Global.putInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);

        mMinimumScreenOffTimeoutMs = mContext.getResources().getInteger(
                Resources.getSystem().getIdentifier("config_minimumScreenOffTimeout", "integer",
                        "android"));
        mVirtualDeviceRule.runWithoutPermissions(() -> {
            UiDeviceUtils.wakeUpAndUnlock(mContext);
            return true;
        });
    }

    @After
    public void tearDown() {
        setScreenOffTimeoutMs(mInitialDisplayTimeout);
        Settings.Global.putInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                mInitialStayOnWhilePluggedInSetting);
        mVirtualDeviceRule.runWithoutPermissions(() -> {
            UiDeviceUtils.wakeUpAndUnlock(mContext);
            return true;
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void proximityOffWakeLockLevelSupported_falseOnVirtualDevice() {
        createVirtualDeviceAndDisplay();

        // Only PROXIMITY_SCREEN_OFF_WAKE_LOCK's availability depends on the display.
        assertThat(mVirtualDisplayPowerManager.isWakeLockLevelSupported(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))
                .isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void isInteractive_screenOffTimeout_isPerPowerGroup() {
        assumeScreenOffSupported();

        createVirtualDeviceAndDisplay();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        setScreenOffTimeoutMs(FAST_SCREEN_OFF_TIMEOUT_MS);
        SystemClock.sleep(mMinimumScreenOffTimeoutMs);

        assumeNoWakeLocksHeld();
        assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void isInteractive_powerButton_isPerPowerGroup() {
        assumeScreenOffSupported();

        createVirtualDeviceAndDisplay();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        UiDeviceUtils.pressSleepButton();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void newWakeLock_isPerPowerGroup() {
        assumeScreenOffSupported();

        createVirtualDeviceAndDisplay();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        PowerManager.WakeLock wakeLock =
                mDefaultDisplayPowerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "cts");
        try {
            wakeLock.acquire();

            setScreenOffTimeoutMs(FAST_SCREEN_OFF_TIMEOUT_MS);
            SystemClock.sleep(mMinimumScreenOffTimeoutMs);

            assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
            assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

            wakeLock.release();
            SystemClock.sleep(mMinimumScreenOffTimeoutMs);

            assumeNoWakeLocksHeld();
            assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
            assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
        } finally {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void goToSleepAndWakeUp_turnsOffAndOnVirtualDisplay() {
        createVirtualDeviceAndDisplay();

        mVirtualDeviceRule.startActivityOnDisplaySync(mDisplay.getDisplayId(), Activity.class);
        int resumed = Flags.correctVirtualDisplayPowerState() ? 1 : 0;
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(resumed)).onResumed();
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        mVirtualDevice.goToSleep();
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(1)).onPaused();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_OFF);
        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();

        mVirtualDevice.wakeUp();
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(++resumed)).onResumed();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void untrustedDisplay_followsDefaultDisplayPowerState() {
        assumeScreenOffSupported();

        createVirtualDeviceAndDisplay(VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder());

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        UiDeviceUtils.pressSleepButton();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void untrustedDisplay_noWakeLock() {
        assumeScreenOffSupported();

        createVirtualDeviceAndDisplay(VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder());

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        setScreenOffTimeoutMs(FAST_SCREEN_OFF_TIMEOUT_MS);
        SystemClock.sleep(mMinimumScreenOffTimeoutMs);

        assumeNoWakeLocksHeld();
        assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void turnScreenOn_turnsOnVirtualDisplay() {
        createVirtualDeviceAndDisplay();

        mVirtualDeviceRule.startActivityOnDisplaySync(mDisplay.getDisplayId(), Activity.class);
        int resumed = Flags.correctVirtualDisplayPowerState() ? 1 : 0;
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(resumed)).onResumed();
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        mVirtualDevice.goToSleep();
        UiDeviceUtils.pressSleepButton();
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(1)).onPaused();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_OFF);
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();

        mVirtualDeviceRule.startActivityOnDisplaySync(
                mDisplay.getDisplayId(), TurnScreenOnShowWhenLockedActivity.class);
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(++resumed)).onResumed();
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    /**
     * Virtual device displays never show keyguard and are always considered "insecure" and
     * "unlocked", so android:showWhenLocked is ignored for such displays.
     */
    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void turnScreenOnWithoutShowWhenLocked_turnsOnVirtualDisplay() {
        createVirtualDeviceAndDisplay();

        mVirtualDeviceRule.startActivityOnDisplaySync(mDisplay.getDisplayId(), Activity.class);
        int resumed = Flags.correctVirtualDisplayPowerState() ? 1 : 0;
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(resumed)).onResumed();
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        mVirtualDevice.goToSleep();
        UiDeviceUtils.pressSleepButton();
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(1)).onPaused();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_OFF);
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();

        mVirtualDeviceRule.startActivityOnDisplaySync(
                mDisplay.getDisplayId(), TurnScreenOnActivity.class);
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(++resumed)).onResumed();
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void turnScreenOnWithoutShowWhenLocked_turnsOnAlwaysUnlockedVirtualDisplay() {
        createVirtualDeviceAndDisplay(new VirtualDeviceParams.Builder()
                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                .build());

        mVirtualDeviceRule.startActivityOnDisplaySync(mDisplay.getDisplayId(), Activity.class);
        int resumed = Flags.correctVirtualDisplayPowerState() ? 1 : 0;
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(resumed)).onResumed();
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        mVirtualDevice.goToSleep();
        UiDeviceUtils.pressSleepButton();
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(1)).onPaused();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_OFF);
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();

        mVirtualDeviceRule.startActivityOnDisplaySync(
                mDisplay.getDisplayId(), TurnScreenOnActivity.class);
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(++resumed)).onResumed();
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void customSleepTimeout_goesToSleep() {
        assumeScreenOffSupported();

        // Ensure the default display timeout is different.
        setScreenOffTimeoutMs(mMinimumScreenOffTimeoutMs * 3);
        createVirtualDeviceAndDisplay(new VirtualDeviceParams.Builder()
                .setScreenOffTimeout(Duration.ofMillis(DISPLAY_TIMEOUT_MS))
                .build());

        mVirtualDeviceRule.startActivityOnDisplaySync(mDisplay.getDisplayId(), Activity.class);
        verify(mVirtualDisplayCallback, timeout(CALLBACK_TIMEOUT_MS).times(1)).onPaused();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_OFF);
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void customBrightness_dimTimeoutTriggersCallback() {
        assumeScreenOffSupported();

        createVirtualDeviceAndDisplay(
                new VirtualDeviceParams.Builder()
                        // Dim after 2s, sleep after 4s.
                        .setDimDuration(Duration.ofMillis(DISPLAY_TIMEOUT_MS))
                        .setScreenOffTimeout(Duration.ofMillis(DISPLAY_TIMEOUT_MS * 2))
                        .build(),
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
                        .setBrightnessListener(mContext.getMainExecutor(), mBrightnessListener)
                        .setDefaultBrightness(DEFAULT_BRIGHTNESS)
                        .setDimBrightness(DIM_BRIGHTNESS));

        mVirtualDeviceRule.startActivityOnDisplaySync(mDisplay.getDisplayId(), Activity.class);
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);
        verify(mBrightnessListener, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onBrightnessChanged(DEFAULT_BRIGHTNESS);

        reset(mBrightnessListener);
        SystemClock.sleep(DISPLAY_TIMEOUT_MS);
        verify(mBrightnessListener, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onBrightnessChanged(DIM_BRIGHTNESS);
    }

    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void customDefaultBrightness_windowManagerOverrideRequestTriggersCallback() {
        createVirtualDeviceAndDisplay(VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
                .setBrightnessListener(mContext.getMainExecutor(), mBrightnessListener)
                .setDefaultBrightness(DEFAULT_BRIGHTNESS));

        Activity activity = mVirtualDeviceRule.startActivityOnDisplaySync(
                mDisplay.getDisplayId(), Activity.class);
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);
        verify(mBrightnessListener, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onBrightnessChanged(DEFAULT_BRIGHTNESS);

        reset(mBrightnessListener);
        setBrightnessOverride(activity, 0.1f);
        verify(mBrightnessListener, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onBrightnessChanged(0.1f);

        reset(mBrightnessListener);
        setBrightnessOverride(activity, 1f);
        verify(mBrightnessListener, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onBrightnessChanged(1f);

        reset(mBrightnessListener);
        setBrightnessOverride(activity, -1f);
        verify(mBrightnessListener, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onBrightnessChanged(DEFAULT_BRIGHTNESS);
    }

    private void assumeScreenOffSupported() {
        assumeFalse("Skipping test: Automotive main display is always on",
                FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse("Skipping test: TVs may start screen saver instead of turning screen off",
                FeatureUtil.hasSystemFeature(PackageManager.FEATURE_LEANBACK));
    }

    private void assumeNoWakeLocksHeld() {
        PowerManagerServiceDumpProto powerManagerDump;
        try {
            powerManagerDump =  ProtoUtils.getProto(
                    getInstrumentation().getUiAutomation(),
                    PowerManagerServiceDumpProto.class, "dumpsys power --proto");
        } catch (Exception e) {
            assumeNoException("Skipping test: Failed to get PowerManager dump", e);
            return;
        }
        int activeWakeLocks = 0;
        for (WakeLockProto wakeLock : powerManagerDump.wakeLocks) {
            if (wakeLock.isDisabled) {
                continue;
            }
            if (wakeLock.lockLevel != PowerManager.FULL_WAKE_LOCK
                    && wakeLock.lockLevel != PowerManager.PARTIAL_WAKE_LOCK
                    && wakeLock.lockLevel != PowerManager.SCREEN_DIM_WAKE_LOCK
                    && wakeLock.lockLevel != PowerManager.SCREEN_BRIGHT_WAKE_LOCK) {
                continue;
            }
            activeWakeLocks++;
            Log.w(TAG, "Wake lock held: " + wakeLock);
        }
        assumeFalse("Skipping test: Found active wake locks, which will break the interactivity "
                + "checks done by the test", activeWakeLocks > 0);
    }

    private void setScreenOffTimeoutMs(int timeoutMs) {
        setScreenOffTimeoutMs(String.valueOf(timeoutMs));
    }

    private void setScreenOffTimeoutMs(String timeoutMs) {
        Settings.System.putString(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs);
    }

    private void setBrightnessOverride(Activity activity, float brightness) {
        getInstrumentation().runOnMainSync(() -> {
            WindowManager.LayoutParams layout = activity.getWindow().getAttributes();
            layout.screenBrightness = brightness;
            activity.getWindow().setAttributes(layout);
        });
    }

    void createVirtualDeviceAndDisplay() {
        createVirtualDeviceAndDisplay(VirtualDeviceRule.DEFAULT_VIRTUAL_DEVICE_PARAMS,
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
    }

    void createVirtualDeviceAndDisplay(VirtualDeviceParams params) {
        createVirtualDeviceAndDisplay(params,
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
    }

    void createVirtualDeviceAndDisplay(VirtualDisplayConfig.Builder displayConfig) {
        createVirtualDeviceAndDisplay(VirtualDeviceRule.DEFAULT_VIRTUAL_DEVICE_PARAMS,
                displayConfig);
    }

    void createVirtualDeviceAndDisplay(VirtualDeviceParams params,
            VirtualDisplayConfig.Builder displayConfig) {
        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);
        VirtualDisplay virtualDisplay =
                mVirtualDeviceRule.createManagedVirtualDisplay(mVirtualDevice,
                        displayConfig, mVirtualDisplayCallback);
        mDisplay = virtualDisplay.getDisplay();

        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);

        Context defaultDisplayContext = mContext.createDisplayContext(
                displayManager.getDisplay(Display.DEFAULT_DISPLAY));
        mDefaultDisplayPowerManager = defaultDisplayContext.getSystemService(PowerManager.class);

        Context virtualDisplayContext = mContext.createDisplayContext(mDisplay);
        mVirtualDisplayPowerManager = virtualDisplayContext.getSystemService(PowerManager.class);
    }

    public static final class TurnScreenOnActivity extends Activity {}

    public static final class TurnScreenOnShowWhenLockedActivity extends Activity {}
}
