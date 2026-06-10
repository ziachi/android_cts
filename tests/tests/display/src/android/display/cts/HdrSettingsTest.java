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

package android.display.cts;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.view.Display;
import android.view.Display.HdrCapabilities;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class HdrSettingsTest {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private DisplayManager mDisplayManager;

    private HdrConversionMode mOriginalHdrConversionModeSettings;

    private int[] mOriginalUserDisabledTypes;

    private boolean mOriginalAreUserDisabledHdrTypesAllowed;

    private HdrConversionTestActivity mHdrConversionTestActivity;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.HDMI_CEC,
            Manifest.permission.MODIFY_HDR_CONVERSION_MODE,
            Manifest.permission.WRITE_SECURE_SETTINGS);

    @Rule
    public ActivityScenarioRule<HdrConversionTestActivity> mActivityRule =
            new ActivityScenarioRule<>(HdrConversionTestActivity.class);

    @Rule
    public DeviceConfigStateChangerRule mHdrOutputControlDeviceConfig =
            new DeviceConfigStateChangerRule(sContext,
                    DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                    DisplayManager.HDR_OUTPUT_CONTROL_FLAG,
                    Boolean.toString(true));

    @Before
    public void setUp() throws Exception {
        assumeTrue("Need an Android TV device to run this test.", FeatureUtil.isTV());
        assertTrue("Physical display is expected.", DisplayUtil.isDisplayConnected(sContext)
                || MediaUtils.onCuttlefish());

        mDisplayManager = sContext.getSystemService(DisplayManager.class);
        assumeTrue(mDisplayManager.getHdrConversionMode().getConversionMode()
                != HdrConversionMode.HDR_CONVERSION_UNSUPPORTED);
        cacheOriginalHdrConversionModeSetting();
    }

    @After
    public void tearDown() throws Exception {
        restoreOriginalHdrConversionModeSettings();
    }

    @Test
    public void testSetHdrConversionModeThrowsExceptionWithInvalidArgument() {
        assertThrows(
                "Preferred HDR output type should not be set if the conversion mode is "
                        + "PASSTHROUGH or SYSTEM",
                IllegalArgumentException.class,
                () -> mDisplayManager.setHdrConversionMode(new HdrConversionMode(
                        HdrConversionMode.HDR_CONVERSION_SYSTEM,
                        HdrCapabilities.HDR_TYPE_DOLBY_VISION)));

        assertThrows(
                "Preferred HDR output type should not be set if the conversion mode is "
                        + "PASSTHROUGH or SYSTEM",
                IllegalArgumentException.class,
                () -> new HdrConversionMode(
                        HdrConversionMode.HDR_CONVERSION_PASSTHROUGH,
                        HdrCapabilities.HDR_TYPE_DOLBY_VISION));
    }

    @Test
    public void testSetHdrConversionMode() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final int invalidHdrConversionMode = 0;

        HdrConversionMode hdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_SYSTEM);
        mDisplayManager.setHdrConversionMode(hdrConversionMode);
        assertEquals(hdrConversionMode, mDisplayManager.getHdrConversionModeSetting());
        int currentHdrConversionMode = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.HDR_CONVERSION_MODE, invalidHdrConversionMode);
        assertEquals(hdrConversionMode.getConversionMode(), currentHdrConversionMode);

        hdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_PASSTHROUGH);
        mDisplayManager.setHdrConversionMode(hdrConversionMode);
        assertEquals(hdrConversionMode, mDisplayManager.getHdrConversionModeSetting());
        currentHdrConversionMode = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.HDR_CONVERSION_MODE, invalidHdrConversionMode);
        assertEquals(hdrConversionMode.getConversionMode(), currentHdrConversionMode);

        hdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_FORCE,
                HdrCapabilities.HDR_TYPE_DOLBY_VISION);
        mDisplayManager.setHdrConversionMode(hdrConversionMode);
        assertEquals(hdrConversionMode, mDisplayManager.getHdrConversionModeSetting());
        currentHdrConversionMode = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.HDR_CONVERSION_MODE, invalidHdrConversionMode);
        assertEquals(hdrConversionMode.getConversionMode(), currentHdrConversionMode);
        int preferredForceHdrConversionType = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.HDR_FORCE_CONVERSION_TYPE, -1);
        assertEquals(hdrConversionMode.getPreferredHdrOutputType(),
                preferredForceHdrConversionType);
    }

    @Test
    public void testGetSupportedHdrOutputTypes() {
        int[] hdrTypes = mDisplayManager.getSupportedHdrOutputTypes();
        List<Integer> permissibleHdrTypes = new ArrayList<>(Arrays.asList(
                HdrCapabilities.HDR_TYPE_DOLBY_VISION,
                HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG,
                HdrCapabilities.HDR_TYPE_HDR10_PLUS));
        for (int hdrType : hdrTypes) {
            assertTrue("HDR types returned from getSupportedHdrOutputTypes should be one of"
                            + Arrays.toString(permissibleHdrTypes.toArray()),
                    permissibleHdrTypes.contains(hdrType));
        }
    }

    @Test
    public void testGetSupportedHdrOutputTypesWithAppOverride() throws Throwable {
        mActivityRule.getScenario().onActivity(activity -> mHdrConversionTestActivity = activity);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        HdrConversionMode hdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_SYSTEM);

        mDisplayManager.setHdrConversionMode(hdrConversionMode);
        assertEquals(hdrConversionMode.getConversionMode(),
                mDisplayManager.getHdrConversionMode().getConversionMode());

        mHdrConversionTestActivity.runOnUiThread(() ->
                mHdrConversionTestActivity.disableHdrConversion(true));
        waitUntil(mDisplayManager -> mDisplayManager.getHdrConversionMode().getConversionMode()
                == HdrConversionMode.HDR_CONVERSION_PASSTHROUGH, Duration.ofSeconds(2));
        assertEquals(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH,
                mDisplayManager.getHdrConversionMode().getConversionMode());

        mHdrConversionTestActivity.runOnUiThread(() ->
                mHdrConversionTestActivity.disableHdrConversion(false));
        waitUntil(mDisplayManager -> mDisplayManager.getHdrConversionMode().getConversionMode()
                == hdrConversionMode.getConversionMode(), Duration.ofSeconds(2));
        assertEquals(hdrConversionMode.getConversionMode(),
                mDisplayManager.getHdrConversionMode().getConversionMode());

        mHdrConversionTestActivity.runOnUiThread(() ->
                mHdrConversionTestActivity.disableHdrConversion(true));
        waitUntil(mDisplayManager -> mDisplayManager.getHdrConversionMode().getConversionMode()
                == HdrConversionMode.HDR_CONVERSION_PASSTHROUGH, Duration.ofSeconds(2));
        assertEquals(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH,
                mDisplayManager.getHdrConversionMode().getConversionMode());

        mActivityRule.getScenario().close();
        waitUntil(mDisplayManager -> mDisplayManager.getHdrConversionMode().getConversionMode()
                == hdrConversionMode.getConversionMode(), Duration.ofSeconds(2));
        assertEquals(hdrConversionMode.getConversionMode(),
                mDisplayManager.getHdrConversionMode().getConversionMode());
    }

    @Test
    public void forceSdr_whenSystemConversionAndDisabledTypesAllowed_displayReturnsNoHdr() {
        mDisplayManager.setHdrConversionMode(new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_SYSTEM));
        mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);
        mDisplayManager.setUserDisabledHdrTypes(new int[0]);
        Display display = mDisplayManager.getDisplay(DEFAULT_DISPLAY);
        assumeNotNull(display.getHdrCapabilities());

        mDisplayManager.setHdrConversionMode(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE,
                        HdrCapabilities.HDR_TYPE_INVALID));

        assertNull(display.getHdrCapabilities());
    }

    @Test
    public void forceSdr_whenSystemConversionAndHdrTypesDisabled_displayReturnsNoHdr() {
        mDisplayManager.setHdrConversionMode(new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_SYSTEM));
        mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
        mDisplayManager.setUserDisabledHdrTypes(new int[0]);
        Display display = mDisplayManager.getDisplay(DEFAULT_DISPLAY);
        assumeNotNull(display.getHdrCapabilities());
        int[] hdrTypes = display.getHdrCapabilities().getSupportedHdrTypes();
        mDisplayManager.setUserDisabledHdrTypes(hdrTypes); // all HDR types disabled
        assertTrue(display.getHdrCapabilities().getSupportedHdrTypes().length == 0);

        mDisplayManager.setHdrConversionMode(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE,
                        HdrCapabilities.HDR_TYPE_INVALID));

        assertNull(display.getHdrCapabilities());
    }

    @Test
    public void setAreUserDisabledHdrTypesAllowed_updatesSettings() {
        mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
        assertFalse(mDisplayManager.areUserDisabledHdrTypesAllowed());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        int invalidAreUserDisabledHdrFormatsAllowed = -1;
        int currentAreUserDisabledHdrFormatsAllowed =
                Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.ARE_USER_DISABLED_HDR_FORMATS_ALLOWED,
                        invalidAreUserDisabledHdrFormatsAllowed);
        assertEquals(0, currentAreUserDisabledHdrFormatsAllowed);

        mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);

        assertTrue(mDisplayManager.areUserDisabledHdrTypesAllowed());
        int newAreUserDisabledHdrFormatsAllowed =
                Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ARE_USER_DISABLED_HDR_FORMATS_ALLOWED,
                        invalidAreUserDisabledHdrFormatsAllowed);
        assertEquals(1, newAreUserDisabledHdrFormatsAllowed);
    }

    @Test
    public void setUserDisabledHdrTypes_updatesSettings() {
        mDisplayManager.setUserDisabledHdrTypes(new int[0]);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String userDisabledFormatsString = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.USER_DISABLED_HDR_FORMATS);
        assertEquals(0, userDisabledFormatsString.length());

        mDisplayManager.setUserDisabledHdrTypes(new int[]{HdrCapabilities.HDR_TYPE_HDR10});

        userDisabledFormatsString = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.USER_DISABLED_HDR_FORMATS);
        assertEquals(String.valueOf(HdrCapabilities.HDR_TYPE_HDR10), userDisabledFormatsString);
    }
    private void cacheOriginalHdrConversionModeSetting() {
        mOriginalHdrConversionModeSettings = mDisplayManager.getHdrConversionModeSetting();
        mOriginalUserDisabledTypes = mDisplayManager.getUserDisabledHdrTypes();
        mOriginalAreUserDisabledHdrTypesAllowed = mDisplayManager.areUserDisabledHdrTypesAllowed();
    }

    private void restoreOriginalHdrConversionModeSettings() {
        // mDisplayManager can be null if the test assumptions if setUp have failed
        if (mDisplayManager == null || mOriginalHdrConversionModeSettings == null) {
            return;
        }
        // Use cache here
        mDisplayManager.setHdrConversionMode(
                new HdrConversionMode(mOriginalHdrConversionModeSettings.getConversionMode()));
        mDisplayManager.setUserDisabledHdrTypes(mOriginalUserDisabledTypes);
        mDisplayManager.setAreUserDisabledHdrTypesAllowed(mOriginalAreUserDisabledHdrTypesAllowed);
    }

    private void waitUntil(Predicate<DisplayManager> pred, Duration maxWait)
            throws Exception {
        final Lock lock = new ReentrantLock();
        final Condition displayChanged = lock.newCondition();
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayChanged(int displayId) {
                lock.lock();
                try {
                    displayChanged.signal();
                } finally {
                    lock.unlock();
                }
            }
            @Override
            public void onDisplayAdded(int displayId) {}
            @Override
            public void onDisplayRemoved(int displayId) {}
        };
        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(listener, handler);
        long remainingNanos = maxWait.toNanos();
        lock.lock();
        try {
            while (!pred.test(mDisplayManager)) {
                if (remainingNanos <= 0L) {
                    throw new TimeoutException();
                }
                remainingNanos = displayChanged.awaitNanos(remainingNanos);
            }
        } finally {
            lock.unlock();
        }
    }
}
