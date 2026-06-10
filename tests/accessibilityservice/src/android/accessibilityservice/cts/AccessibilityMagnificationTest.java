/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_WINDOW;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.CtsTestUtils.isAutomotive;
import static android.content.pm.PackageManager.FEATURE_WINDOW_MAGNIFICATION;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY;
import static android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.accessibilityservice.AccessibilityService.MagnificationController.OnMagnificationChangedListener;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.MagnificationConfig;
import android.accessibilityservice.cts.activities.AccessibilityTextTraversalActivity;
import android.accessibilityservice.cts.activities.AccessibilityWindowQueryActivity;
import android.accessibilityservice.cts.utils.SettingsSession;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;
import com.android.compatibility.common.util.UserSettings;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Class for testing {@link MagnificationController} and the magnification overlay window.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityMagnificationTest {

    /** Maximum timeout when waiting for a magnification callback. */
    public static final int LISTENER_TIMEOUT_MILLIS = 500 * HW_TIMEOUT_MULTIPLIER;
    /** Maximum animation timeout when waiting for a magnification callback. */
    public static final int LISTENER_ANIMATION_TIMEOUT_MILLIS = 1000 * HW_TIMEOUT_MULTIPLIER;
    public static final int BOUNDS_TOLERANCE = 1;
    public static final String ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED =
            "accessibility_display_magnification_enabled";

    /** Maximum timeout while waiting for a config to be updated */
    public static final int TIMEOUT_CONFIG_SECONDS = 15;

    private static final int TEARDOWN_SLEEP_TIMEOUT_MS = 200 * HW_TIMEOUT_MULTIPLIER;

    // From WindowManager.java.
    public static final int TYPE_NAVIGATION_BAR_PANEL = 2024;

    private static UiAutomation sUiAutomation;

    private static final String TAG = "AccessibilityMagnificationTest";

    private StubMagnificationAccessibilityService mService;
    private Instrumentation mInstrumentation;

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private ActivityScenario<? extends Activity> mActivityScenario;

    private InstrumentedAccessibilityServiceTestRule<InstrumentedAccessibilityService>
            mInstrumentedAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    InstrumentedAccessibilityService.class, false);

    private InstrumentedAccessibilityServiceTestRule<StubMagnificationAccessibilityService>
            mMagnificationAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    StubMagnificationAccessibilityService.class, false);

    private final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(sUiAutomation);

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mMagnificationAccessibilityServiceRule)
                    .around(mInstrumentedAccessibilityServiceRule)
                    .around(mDumpOnFailureRule)
                    .around(mCheckFlagsRule);

    @BeforeClass
    public static void oneTimeSetUp() {
        sUiAutomation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        final AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        assumeFalse("Magnification is not supported on Automotive.",
                isAutomotive(mInstrumentation.getTargetContext()));
        ShellCommandBuilder.create(sUiAutomation)
                .deleteSecureSetting(ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED)
                .run();
        // Starting the service will force the accessibility subsystem to examine its settings, so
        // it will update magnification in the process to disable it.
        mService = mMagnificationAccessibilityServiceRule.enableService();
    }

    @After
    public void tearDown() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }

        // Ensure the magnification is deactivated after each test case. For some test cases that
        // would disable mService during the test, they still need to reset magnification themselves
        // after the test.
        if (mService != null) {
            mService.runOnServiceSync(() -> {
                mService.getMagnificationController().resetCurrentMagnification(/* animate= */
                        false);
                mService.disableSelfAndRemove();
            });
        }

        // Since window magnification may need times to remove the magnification window, we would
        // like to wait and ensure the overlays for magnification are removed here.
        if (isMagnificationOverlayExisting() || doesAccessibilityMagnificationOverlayExist()) {
            // Do nothing, we just want to wait for the event and check the
            // overlays for magnification are removed
            try {
                sUiAutomation.executeAndWaitForEvent(() -> {},
                        event -> !(isMagnificationOverlayExisting()
                                || doesAccessibilityMagnificationOverlayExist()),
                        5000);
            } catch (TimeoutException timeoutException) {
                // Double check the overlay is not exists in case there is no event sent
                assertTrue(!(isMagnificationOverlayExisting()
                               || doesAccessibilityMagnificationOverlayExist()));
            }
        }

        // A slight sleep to wait for the service asynchronous teardown process, such as
        // magnification system ui connection releasing.
        // It's to prevent the releasing from affecting next test's system ui connection creating.
        SystemClock.sleep(TEARDOWN_SLEEP_TIMEOUT_MS);
    }

    @Test
    public void testSetScale() {
        final MagnificationController controller = mService.getMagnificationController();
        final float scale = 2.0f;
        final AtomicBoolean result = new AtomicBoolean();

        mService.runOnServiceSync(() -> result.set(controller.setScale(scale, false)));

        assertTrue(getSetterErrorMessage("Failed to set scale"), result.get());
        assertEquals("Failed to apply scale", scale, controller.getScale(), 0f);

        mService.runOnServiceSync(() -> result.set(controller.reset(false)));

        assertTrue("Failed to reset", result.get());
        assertEquals("Failed to apply reset", 1.0f, controller.getScale(), 0f);
    }

    @Test
    public void testSetScaleAndCenter() {
        final MagnificationController controller = mService.getMagnificationController();
        final Region region = controller.getMagnificationRegion();
        final Rect bounds = region.getBounds();
        final float scale = 2.0f;
        final float x = bounds.left + (bounds.width() / 4.0f);
        final float y = bounds.top + (bounds.height() / 4.0f);
        final AtomicBoolean setScale = new AtomicBoolean();
        final AtomicBoolean setCenter = new AtomicBoolean();
        final AtomicBoolean result = new AtomicBoolean();

        mService.runOnServiceSync(() -> {
            setScale.set(controller.setScale(scale, false));
            setCenter.set(controller.setCenter(x, y, false));
        });

        assertTrue(getSetterErrorMessage("Failed to set scale"), setScale.get());
        assertEquals("Failed to apply scale", scale, controller.getScale(), 0f);

        assertTrue(getSetterErrorMessage("Failed to set center"), setCenter.get());
        assertEquals("Failed to apply center X", x, controller.getCenterX(), 5.0f);
        assertEquals("Failed to apply center Y", y, controller.getCenterY(), 5.0f);

        mService.runOnServiceSync(() -> result.set(controller.reset(false)));

        assertTrue("Failed to reset", result.get());
        assertEquals("Failed to apply reset", 1.0f, controller.getScale(), 0f);
    }

    @Test
    public void testSetMagnificationConfig_expectedConfig() throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Rect rect = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = rect.centerX();
        final float y = rect.centerY();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final int targetMode = isWindowModeSupported(mInstrumentation.getContext())
                ? MAGNIFICATION_MODE_WINDOW : MAGNIFICATION_MODE_FULLSCREEN;
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(targetMode)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();

        mService.runOnServiceSync(() -> {
            setConfig.set(controller.setMagnificationConfig(config, false));
        });
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, config);
        assertConfigEquals(config, controller.getMagnificationConfig());

        final float newScale = scale + 1;
        final Region region = controller.getMagnificationRegion();
        final Rect bounds = region.getBounds();
        final float newX = bounds.left + (bounds.width() / 4.0f);
        final float newY = bounds.top + (bounds.height() / 4.0f);
        final MagnificationConfig newConfig = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN).setScale(newScale).setCenterX(
                        newX).setCenterY(
                        newY).build();
        mService.runOnServiceSync(() -> {
            controller.setMagnificationConfig(newConfig, false);
        });
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, newConfig);
        assertConfigEquals(newConfig, controller.getMagnificationConfig());
    }

    @Test
    public void testSetConfigWithDefaultModeAndCenter_expectedConfig() throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 3.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final int targetMode = isWindowModeSupported(mInstrumentation.getContext())
                ? MAGNIFICATION_MODE_WINDOW : MAGNIFICATION_MODE_FULLSCREEN;
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(targetMode)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y)
                .build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(config, false)));
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, config);
        assertConfigEquals(config, controller.getMagnificationConfig());

        final float newScale = scale + 1;
        final MagnificationConfig newConfig = new MagnificationConfig.Builder()
                .setScale(newScale).build();
        final MagnificationConfig expectedConfig = obtainConfigBuilder(config).setScale(
                newScale).build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(newConfig, false)));
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, expectedConfig);
        assertConfigEquals(expectedConfig, controller.getMagnificationConfig());

        mService.runOnServiceSync(
                () -> controller.resetCurrentMagnification(/* animate= */ false));
    }

    @Test
    public void testSetConfigWithActivatedFalse_expectedConfig() throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 3.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final int targetMode = isWindowModeSupported(mInstrumentation.getContext())
                ? MAGNIFICATION_MODE_WINDOW : MAGNIFICATION_MODE_FULLSCREEN;
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(targetMode)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y)
                .build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(config,
                        /* animate= */ false)));
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, config);
        assertConfigEquals(config, controller.getMagnificationConfig());

        final MagnificationConfig newConfig = new MagnificationConfig.Builder()
                .setActivated(false).build();
        final MagnificationConfig expectedConfig = obtainConfigBuilder(config).setActivated(
                false).build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(newConfig,
                        /* animate= */ false)));
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, expectedConfig);
        assertConfigEquals(expectedConfig, controller.getMagnificationConfig());
    }

    @Test
    public void testSetConfigWithActivatedFalse_magnificationDisabled_expectedReturnedValue()
            throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setActivated(false)
                .build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(config,
                        /* animate= */ false)));
        assertFalse(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, config);
    }

    @Test
    public void testSetFullScreenConfigWithDefaultValues_windowModeEnabled_expectedConfig()
            throws Exception {
        final boolean windowModeSupported = isWindowModeSupported(mInstrumentation.getContext());
        Assume.assumeTrue("window mode is not available", windowModeSupported);

        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 3.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(config, false)));
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, config);
        assertConfigEquals(config, controller.getMagnificationConfig());

        final MagnificationConfig newConfig = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(newConfig, false)));
        assertTrue("Set config should have failed but didn't", setConfig.get());

        final MagnificationConfig expectedConfig = obtainConfigBuilder(config).setMode(
                MAGNIFICATION_MODE_FULLSCREEN).build();

        waitUntilMagnificationConfig(controller, expectedConfig);
        assertConfigEquals(expectedConfig, controller.getMagnificationConfig());
    }

    @Test
    public void testSetFullScreenConfigWithScaleOne_expectedConfig() {
        final MagnificationController controller = mService.getMagnificationController();
        final float scale = 1.0f;
        final AtomicBoolean setConfig = new AtomicBoolean();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(config,
                        /* animate= */ false)));
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());
        assertTrue(controller.getMagnificationConfig().isActivated());
        assertEquals(1.0f, controller.getMagnificationConfig().getScale(), 0);
    }

    @Test
    public void testResetCurrentMagnification_fullScreenEnabled_expectedConfig()
            throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 3.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y)
                .build();

        mService.runOnServiceSync(
                () -> setConfig.set(controller.setMagnificationConfig(config,
                        /* animate= */ false)));
        assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

        waitUntilMagnificationConfig(controller, config);
        assertConfigEquals(config, controller.getMagnificationConfig());

        mService.runOnServiceSync(() -> {
            controller.resetCurrentMagnification(/* animate= */ false);
        });

        assertFalse(controller.getMagnificationConfig().isActivated());
        assertEquals(1.0f, controller.getMagnificationConfig().getScale(), 0);
    }

    @Test
    public void testSetMagnificationConfig_legacyApiExpectedResult() {
        final MagnificationController controller = mService.getMagnificationController();
        final Region region = controller.getMagnificationRegion();
        final Rect bounds = region.getBounds();
        final float scale = 2.0f;
        final float x = bounds.left + (bounds.width() / 4.0f);
        final float y = bounds.top + (bounds.height() / 4.0f);
        final AtomicBoolean setConfig = new AtomicBoolean();
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();
        try {
            mService.runOnServiceSync(() -> {
                setConfig.set(controller.setMagnificationConfig(config, false));
            });
            assertTrue(getSetterErrorMessage("Failed to set config"), setConfig.get());

            assertEquals("Failed to apply scale", scale, controller.getScale(), 0f);
            assertEquals("Failed to apply center X", x, controller.getCenterX(), 5.0f);
            assertEquals("Failed to apply center Y", y, controller.getCenterY(), 5.0f);
        } finally {
            mService.runOnServiceSync(() -> controller.resetCurrentMagnification(false));
        }
    }

    @Test
    public void testSetWindowModeConfig_connectionReset_expectedResult() throws Exception {
        Assume.assumeTrue(isWindowModeSupported(mInstrumentation.getContext()));

        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();

        mService.runOnServiceSync(
                () -> controller.setMagnificationConfig(config, /* animate= */ false));

        waitUntilMagnificationConfig(controller, config);

        // Test service is disabled and enabled to make the connection reset.
        mService.runOnServiceSync(() -> mService.disableSelfAndRemove());
        mService = null;
        InstrumentedAccessibilityService service =
                mMagnificationAccessibilityServiceRule.enableService();
        MagnificationController controller2 = service.getMagnificationController();
        try {
            final float newScale = scale + 1;
            final float newX = x + bounds.width() / 4.0f;
            final float newY = y + bounds.height() / 4.0f;
            final MagnificationConfig newConfig = new MagnificationConfig.Builder()
                    .setMode(MAGNIFICATION_MODE_WINDOW)
                    .setScale(newScale)
                    .setCenterX(newX)
                    .setCenterY(newY).build();

            service.runOnServiceSync(
                    () -> controller2.setMagnificationConfig(newConfig, /* animate= */ false));

            waitUntilMagnificationConfig(controller2, newConfig);
        } finally {
            service.runOnServiceSync(
                    () -> controller2.resetCurrentMagnification(false));
        }
    }

    @Test
    public void testSetWindowModeConfig_hasAccessibilityOverlay() throws TimeoutException {
        Assume.assumeTrue(isWindowModeSupported(mInstrumentation.getContext()));

        final MagnificationController controller = mService.getMagnificationController();
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(2.0f)
                .build();

        try {
            sUiAutomation.executeAndWaitForEvent(
                    () -> controller.setMagnificationConfig(config, false),
                    event -> doesAccessibilityMagnificationOverlayExist(), 5000);
        } finally {
            mService.runOnServiceSync(() -> controller.resetCurrentMagnification(false));
        }
    }

    @Test
    public void testServiceConnectionDisconnected_hasNoAccessibilityOverlay()
            throws TimeoutException {
        Assume.assumeTrue(isWindowModeSupported(mInstrumentation.getContext()));

        final MagnificationController controller = mService.getMagnificationController();
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(2.0f)
                .build();

        try {
            sUiAutomation.executeAndWaitForEvent(
                    () -> controller.setMagnificationConfig(config, false),
                    event -> doesAccessibilityMagnificationOverlayExist(), 5000);

            sUiAutomation.executeAndWaitForEvent(
                    () -> mService.runOnServiceSync(() -> mService.disableSelfAndRemove()),
                    event -> !doesAccessibilityMagnificationOverlayExist(), 5000);
        } finally {
            mService.runOnServiceSync(() -> controller.resetCurrentMagnification(false));
        }
    }

    @Test
    public void testGetMagnificationConfig_setConfigByLegacyApi_expectedResult() {
        final MagnificationController controller = mService.getMagnificationController();
        final Region region = controller.getMagnificationRegion();
        final Rect bounds = region.getBounds();
        final float scale = 2.0f;
        final float x = bounds.left + (bounds.width() / 4.0f);
        final float y = bounds.top + (bounds.height() / 4.0f);
        mService.runOnServiceSync(() -> {
            controller.setScale(scale, false);
            controller.setCenter(x, y, false);
        });

        final MagnificationConfig config = controller.getMagnificationConfig();

        assertTrue(config.isActivated());
        assertEquals("Failed to apply scale", scale, config.getScale(), 0f);
        assertEquals("Failed to apply center X", x, config.getCenterX(), 5.0f);
        assertEquals("Failed to apply center Y", y, config.getCenterY(), 5.0f);
    }

    @Test
    public void testGetMagnificationConfig_setConfigByLegacyApiAndReset_expectedResult() {
        final MagnificationController controller = mService.getMagnificationController();
        final Region region = controller.getMagnificationRegion();
        final Rect bounds = region.getBounds();
        final float scale = 2.0f;
        final float x = bounds.left + (bounds.width() / 4.0f);
        final float y = bounds.top + (bounds.height() / 4.0f);
        mService.runOnServiceSync(() -> {
            controller.setScale(scale, /* animate= */ false);
            controller.setCenter(x, y, /* animate= */ false);
            controller.reset(/* animate= */ false);
        });

        final MagnificationConfig config = controller.getMagnificationConfig();

        assertFalse(config.isActivated());
    }

    @Test
    public void testListener() {
        final MagnificationController controller = mService.getMagnificationController();
        final OnMagnificationChangedListener spyListener = mock(
                OnMagnificationChangedListener.class);
        final OnMagnificationChangedListener listener =
                (controller1, region, scale, centerX, centerY) ->
                        spyListener.onMagnificationChanged(controller1, region, scale, centerX,
                                centerY);
        controller.addListener(listener);

        try {
            final float scale = 2.0f;
            final AtomicBoolean result = new AtomicBoolean();

            mService.runOnServiceSync(() -> result.set(controller.setScale(scale, false)));

            assertTrue(getSetterErrorMessage("Failed to set scale"), result.get());
            verify(spyListener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), eq(scale), anyFloat(), anyFloat());

            mService.runOnServiceSync(() -> result.set(controller.reset(false)));

            assertTrue("Failed to reset", result.get());
            verify(spyListener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), eq(1.0f), anyFloat(), anyFloat());
        } finally {
            controller.removeListener(listener);
        }
    }

    @Test
    public void testListener_changeConfigByLegacyApi_notifyConfigChanged() {
        final MagnificationController controller = mService.getMagnificationController();
        final OnMagnificationChangedListener listener = mock(OnMagnificationChangedListener.class);
        controller.addListener(listener);

        try {
            final float scale = 2.0f;
            final AtomicBoolean result = new AtomicBoolean();

            mService.runOnServiceSync(() -> result.set(controller.setScale(scale, false)));

            assertTrue(getSetterErrorMessage("Failed to set scale"), result.get());
            final ArgumentCaptor<MagnificationConfig> configCaptor = ArgumentCaptor.forClass(
                    MagnificationConfig.class);
            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), configCaptor.capture());
            assertTrue(configCaptor.getValue().isActivated());
            assertEquals(scale, configCaptor.getValue().getScale(), 0);

            reset(listener);
            mService.runOnServiceSync(() -> result.set(controller.reset(false)));

            assertTrue("Failed to reset", result.get());
            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), configCaptor.capture());
            assertFalse(configCaptor.getValue().isActivated());
            assertEquals(1.0f, configCaptor.getValue().getScale(), 0);
        } finally {
            controller.removeListener(listener);
        }
    }

    @Test
    public void testListener_magnificationConfigChangedWithoutAnimation_notifyConfigChanged()
            throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final OnMagnificationChangedListener listener = mock(OnMagnificationChangedListener.class);
        controller.addListener(listener);
        final int targetMode = isWindowModeSupported(mInstrumentation.getContext())
                ? MAGNIFICATION_MODE_WINDOW : MAGNIFICATION_MODE_FULLSCREEN;
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(targetMode)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y)
                .build();

        try {
            mService.runOnServiceSync(() -> controller.setMagnificationConfig(config, false));
            waitUntilMagnificationConfig(controller, config);

            final ArgumentCaptor<MagnificationConfig> configCaptor = ArgumentCaptor.forClass(
                    MagnificationConfig.class);
            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), configCaptor.capture());
            assertConfigEquals(config, configCaptor.getValue());

            final float newScale = scale + 1;
            final float newX = x + bounds.width() / 4.0f;
            final float newY = y + bounds.height() / 4.0f;
            final MagnificationConfig fullscreenConfig = new MagnificationConfig.Builder()
                    .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                    .setScale(newScale)
                    .setCenterX(newX)
                    .setCenterY(newY).build();

            reset(listener);
            mService.runOnServiceSync(() -> {
                controller.setMagnificationConfig(fullscreenConfig, false);
            });
            waitUntilMagnificationConfig(controller, fullscreenConfig);

            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), configCaptor.capture());
            assertConfigEquals(fullscreenConfig, configCaptor.getValue());
        } finally {
            mService.runOnServiceSync(() -> {
                controller.resetCurrentMagnification(false);
                controller.removeListener(listener);
            });
        }
    }

    @Test
    public void testListener_magnificationConfigChangedWithAnimation_notifyConfigChanged() {
        final MagnificationController controller = mService.getMagnificationController();
        final OnMagnificationChangedListener listener = mock(OnMagnificationChangedListener.class);
        controller.addListener(listener);
        final int targetMode = isWindowModeSupported(mInstrumentation.getContext())
                ? MAGNIFICATION_MODE_WINDOW : MAGNIFICATION_MODE_FULLSCREEN;
        final float scale = 2.0f;
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(targetMode)
                .setScale(scale).build();

        try {
            mService.runOnServiceSync(
                    () -> controller.setMagnificationConfig(config, /* animate= */ true));

            verify(listener, timeout(LISTENER_ANIMATION_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), any(MagnificationConfig.class));

            final float newScale = scale + 1;
            final MagnificationConfig fullscreenConfig = new MagnificationConfig.Builder()
                    .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                    .setScale(newScale).build();

            reset(listener);
            mService.runOnServiceSync(() -> {
                controller.setMagnificationConfig(fullscreenConfig, /* animate= */ true);
            });

            verify(listener, timeout(LISTENER_ANIMATION_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), any(MagnificationConfig.class));
        } finally {
            mService.runOnServiceSync(() -> {
                controller.resetCurrentMagnification(false);
                controller.removeListener(listener);
            });
        }
    }

    @Test
    public void testListener_transitionFromFullScreenToWindow_notifyConfigChanged()
            throws Exception {
        Assume.assumeTrue(isWindowModeSupported(mInstrumentation.getContext()));

        final MagnificationController controller = mService.getMagnificationController();
        final OnMagnificationChangedListener listener = mock(OnMagnificationChangedListener.class);
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();
        final MagnificationConfig windowConfig = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y)
                .build();
        final float newScale = scale + 1;
        final float newX = x + bounds.width() / 4.0f;
        final float newY = y + bounds.height() / 4.0f;
        final MagnificationConfig fullscreenConfig = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(newScale)
                .setCenterX(newX)
                .setCenterY(newY).build();

        try {
            final ArgumentCaptor<MagnificationConfig> configCaptor = ArgumentCaptor.forClass(
                    MagnificationConfig.class);

            mService.runOnServiceSync(() -> {
                controller.setMagnificationConfig(fullscreenConfig, false);
            });
            waitUntilMagnificationConfig(controller, fullscreenConfig);

            controller.addListener(listener);
            mService.runOnServiceSync(() -> controller.setMagnificationConfig(windowConfig, false));
            waitUntilMagnificationConfig(controller, windowConfig);

            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), configCaptor.capture());
            assertConfigEquals(windowConfig, configCaptor.getValue());
        } finally {
            mService.runOnServiceSync(() -> {
                controller.resetCurrentMagnification(false);
                controller.removeListener(listener);
            });
        }
    }

    @Test
    public void testListener_resetCurrentMagnification_notifyConfigChanged() throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final OnMagnificationChangedListener listener = mock(OnMagnificationChangedListener.class);
        final int targetMode = isWindowModeSupported(mInstrumentation.getContext())
                ? MAGNIFICATION_MODE_WINDOW : MAGNIFICATION_MODE_FULLSCREEN;
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();
        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(targetMode)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y)
                .build();

        try {
            mService.runOnServiceSync(
                    () -> controller.setMagnificationConfig(config, /* animate= */ false));
            waitUntilMagnificationConfig(controller, config);

            controller.addListener(listener);
            controller.resetCurrentMagnification(false);

            final ArgumentCaptor<MagnificationConfig> configCaptor = ArgumentCaptor.forClass(
                    MagnificationConfig.class);
            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), configCaptor.capture());
            assertFalse(configCaptor.getValue().isActivated());
            assertEquals(1.0f, configCaptor.getValue().getScale(), 0);
        } finally {
            controller.removeListener(listener);
        }
    }

    @Test
    public void testMagnificationServiceShutsDownWhileMagnifying_fullscreen_shouldReturnTo1x() {
        final MagnificationController controller = mService.getMagnificationController();
        mService.runOnServiceSync(() -> controller.setScale(2.0f, false));

        mService.runOnServiceSync(() -> mService.disableSelf());
        mService = null;
        InstrumentedAccessibilityService service =
                mInstrumentedAccessibilityServiceRule.enableService();
        final MagnificationController controller2 = service.getMagnificationController();
        assertEquals("Magnification must reset when a service dies",
                1.0f, controller2.getScale(), 0f);
        assertFalse(controller2.getMagnificationConfig().isActivated());
    }

    @Test
    public void testMagnificationServiceShutsDownWhileMagnifying_windowMode_shouldReturnTo1x()
            throws Exception {
        Assume.assumeTrue(isWindowModeSupported(mInstrumentation.getContext()));

        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();

        mService.runOnServiceSync(() -> {
            controller.setMagnificationConfig(config, false);
        });
        waitUntilMagnificationConfig(controller, config);

        mService.runOnServiceSync(() -> mService.disableSelf());
        mService = null;
        InstrumentedAccessibilityService service =
                mInstrumentedAccessibilityServiceRule.enableService();
        final MagnificationController controller2 = service.getMagnificationController();
        assertEquals("Magnification must reset when a service dies",
                1.0f, controller2.getMagnificationConfig().getScale(), 0f);
        assertFalse(controller2.getMagnificationConfig().isActivated());
    }

    @Test
    public void testGetMagnificationRegion_whenCanControlMagnification_shouldNotBeEmpty() {
        final MagnificationController controller = mService.getMagnificationController();
        Region magnificationRegion = controller.getMagnificationRegion();
        assertFalse("Magnification region should not be empty when "
                 + "magnification is being actively controlled", magnificationRegion.isEmpty());
    }

    @Test
    public void testGetMagnificationRegion_whenCantControlMagnification_shouldBeEmpty() {
        mService.runOnServiceSync(() -> mService.disableSelf());
        mService = null;
        InstrumentedAccessibilityService service =
                mInstrumentedAccessibilityServiceRule.enableService();
        final MagnificationController controller = service.getMagnificationController();
        Region magnificationRegion = controller.getMagnificationRegion();
        assertTrue("Magnification region should be empty when magnification "
                + "is not being actively controlled", magnificationRegion.isEmpty());
    }

    @Test
    public void testGetMagnificationRegion_whenMagnificationGesturesEnabled_shouldNotBeEmpty()
            throws Exception {
        try (var session =
                new SettingsSession(
                        mInstrumentation,
                        UserSettings.Namespace.SECURE,
                        ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED,
                        "1")) {
            mService.runOnServiceSync(() -> mService.disableSelf());
            mService = null;
            InstrumentedAccessibilityService service =
                    mInstrumentedAccessibilityServiceRule.enableService();

            final MagnificationController controller = service.getMagnificationController();
            Region magnificationRegion = controller.getMagnificationRegion();
            assertFalse("Magnification region should not be empty when magnification "
                    + "gestures are active", magnificationRegion.isEmpty());
        }
    }

    @Test
    public void testGetCurrentMagnificationRegion_fullscreen_exactRegionCenter() throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Region region = controller.getMagnificationRegion();
        final Rect bounds = region.getBounds();
        final float scale = 2.0f;
        final float x = bounds.left + (bounds.width() / 4.0f);
        final float y = bounds.top + (bounds.height() / 4.0f);

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();
        try {
            mService.runOnServiceSync(() -> {
                controller.setMagnificationConfig(config, false);
            });
            waitUntilMagnificationConfig(controller, config);

            final Region magnificationRegion = controller.getCurrentMagnificationRegion();
            assertFalse(magnificationRegion.isEmpty());
        } finally {
            mService.runOnServiceSync(() -> {
                controller.resetCurrentMagnification(false);
            });
        }
    }

    @Test
    public void testMagnificationRegion_hasNonMagnifiableWindow_excludeWindowExactTouchableRegion()
            throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Region expectedMagnificationRegion = controller.getMagnificationRegion();
        final Rect magnifyBounds = controller.getMagnificationRegion().getBounds();

        mService.runOnServiceSync(() -> controller.setScale(4.0f, /* animate =*/ false));

        Button createdButton = null;
        try {
            // We should ensure all the button area is in magnifyBounds for the following tests
            Button button = addNonMagnifiableWindow(R.string.button1, "button1", params -> {
                params.gravity = Gravity.LEFT | Gravity.TOP;
                params.x = magnifyBounds.left;
                params.y = magnifyBounds.top;
                params.width = magnifyBounds.width();
                params.height = magnifyBounds.height() / 8;
            });
            createdButton = button;

            // Set two small rects at the top-left and right-bottom corner as the touchable region
            Rect buttonBounds = new Rect();
            button.getBoundsOnScreen(buttonBounds, false);
            int touchableRegionWidth = buttonBounds.width() / 4;
            int touchableRegionHeight = buttonBounds.height() / 4;
            Rect touchableRect1 = new Rect(0, 0, touchableRegionWidth, touchableRegionHeight);
            Rect touchableRect2 = new Rect(
                    buttonBounds.width() - touchableRegionWidth,
                    buttonBounds.height() - touchableRegionHeight,
                    buttonBounds.width(),
                    buttonBounds.height());
            Region touchableRegion = new Region();
            touchableRegion.op(touchableRect1, Region.Op.UNION);
            touchableRegion.op(touchableRect2, Region.Op.UNION);
            mInstrumentation.runOnMainSync(() ->
                    button.getRootSurfaceControl().setTouchableRegion(touchableRegion));

            // Offset the touchable rects to screen coordinates
            touchableRect1.offset(buttonBounds.left, buttonBounds.top);
            touchableRect2.offset(buttonBounds.left, buttonBounds.top);
            TestUtils.waitUntil(
                    "The updated magnification region is not expected. expected: "
                            + expectedMagnificationRegion + " , actual: "
                            + controller.getMagnificationRegion() + ", touchableRect1: "
                            + touchableRect1 + ", touchableRect2: " + touchableRect2,
                    TIMEOUT_CONFIG_SECONDS,
                    () -> {
                        final Region magnificationRegion = controller.getMagnificationRegion();

                        Region tmpRegion = new Region();

                        // magnificationRegion should not intersect with real touchable rects
                        tmpRegion.set(magnificationRegion);
                        tmpRegion.op(touchableRect1, Region.Op.INTERSECT);
                        if (!tmpRegion.isEmpty()) {
                            return false;
                        }
                        tmpRegion.set(magnificationRegion);
                        tmpRegion.op(touchableRect2, Region.Op.INTERSECT);
                        if (!tmpRegion.isEmpty()) {
                            return false;
                        }

                        // The region united by magnificationRegion and touchable rects
                        // should equal to the original region
                        tmpRegion.set(magnificationRegion);
                        tmpRegion.union(touchableRect1);
                        tmpRegion.union(touchableRect2);
                        // In case the screen is round (e.g. Wear OS), the magnification region will
                        // also be round. We need to intersect the united region with the
                        // original round shape to fit the screen.
                        tmpRegion.op(expectedMagnificationRegion, Region.Op.INTERSECT);
                        return expectedMagnificationRegion.equals(tmpRegion);
                    });
        } finally {
            if (createdButton != null) {
                mInstrumentation.getContext().getSystemService(WindowManager.class).removeView(
                        createdButton);
            }
            // After the non-magnifiable window is removed, the magnificationRegion should restore
            // to the original region.
            waitUntilMagnificationRegion(controller, expectedMagnificationRegion);

            mService.runOnServiceSync(() -> controller.reset(/* animate =*/ false));
        }
    }

    @Test
    public void testGetCurrentMagnificationRegion_windowMode_exactRegionCenter() throws Exception {
        Assume.assumeTrue(isWindowModeSupported(mInstrumentation.getContext()));

        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();
        try {
            mService.runOnServiceSync(() -> {
                controller.setMagnificationConfig(config, false);
            });
            waitUntilMagnificationConfig(controller, config);

            final Region magnificationRegion = controller.getCurrentMagnificationRegion();
            final Rect magnificationBounds = magnificationRegion.getBounds();
            assertEquals(magnificationBounds.exactCenterX(), x, BOUNDS_TOLERANCE);
            assertEquals(magnificationBounds.exactCenterY(), y, BOUNDS_TOLERANCE);
        } finally {
            mService.runOnServiceSync(() -> {
                controller.resetCurrentMagnification(false);
            });
        }
    }

    @Test
    public void testResetCurrentMagnificationRegion_WindowMode_regionIsEmpty() throws Exception {
        Assume.assumeTrue(isWindowModeSupported(mInstrumentation.getContext()));

        final MagnificationController controller = mService.getMagnificationController();
        final Rect bounds = controller.getMagnificationRegion().getBounds();
        final float scale = 2.0f;
        final float x = bounds.centerX();
        final float y = bounds.centerY();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();

        mService.runOnServiceSync(() -> {
            controller.setMagnificationConfig(config, false);
        });
        waitUntilMagnificationConfig(controller, config);

        assertEquals(scale, controller.getMagnificationConfig().getScale(), 0);

        mService.runOnServiceSync(() -> {
            controller.resetCurrentMagnification(false);
        });

        assertFalse(controller.getMagnificationConfig().isActivated());
        assertEquals(1.0f, controller.getMagnificationConfig().getScale(), 0);
        assertTrue(controller.getCurrentMagnificationRegion().isEmpty());
    }

    @Test
    public void testAnimatingMagnification() throws InterruptedException {
        final MagnificationController controller = mService.getMagnificationController();
        final int timeBetweenAnimationChanges = 100;

        final float scale1 = 5.0f;
        final float x1 = 500;
        final float y1 = 1000;

        final float scale2 = 4.0f;
        final float x2 = 500;
        final float y2 = 1500;

        final float scale3 = 2.1f;
        final float x3 = 700;
        final float y3 = 700;

        for (int i = 0; i < 5; i++) {
            mService.runOnServiceSync(() -> {
                controller.setScale(scale1, true);
                controller.setCenter(x1, y1, true);
            });

            Thread.sleep(timeBetweenAnimationChanges);

            mService.runOnServiceSync(() -> {
                controller.setScale(scale2, true);
                controller.setCenter(x2, y2, true);
            });

            Thread.sleep(timeBetweenAnimationChanges);

            mService.runOnServiceSync(() -> {
                controller.setScale(scale3, true);
                controller.setCenter(x3, y3, true);
            });

            Thread.sleep(timeBetweenAnimationChanges);
        }

        mService.runOnServiceSync(() -> {
            controller.resetCurrentMagnification(/* animate= */ false);
        });
    }

    @Test
    @FlakyTest
    public void testA11yNodeInfoVisibility_whenOutOfMagnifiedArea_shouldVisible()
            throws Exception {
        mActivityScenario = ActivityScenario.launch(AccessibilityWindowQueryActivity.class);
        AtomicReference<Activity> activity = new AtomicReference<>();
        mActivityScenario.moveToState(Lifecycle.State.RESUMED).onActivity(activity::set);

        final MagnificationController controller = mService.getMagnificationController();
        final Rect magnifyBounds = controller.getMagnificationRegion().getBounds();
        final float scale = 8.0f;
        final Button button = activity.get().findViewById(R.id.button1);
        adjustViewBoundsIfNeeded(button, scale, magnifyBounds);

        final AccessibilityNodeInfo buttonNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button1").get(0);
        assertNotNull("Can't find button on the screen", buttonNode);
        assertTrue("Button should be visible", buttonNode.isVisibleToUser());

        // Get right-bottom center position
        final float centerX = magnifyBounds.left + (((float) magnifyBounds.width() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));
        final float centerY = magnifyBounds.top + (((float) magnifyBounds.height() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));
        final Rect boundsBeforeMagnify = new Rect();
        buttonNode.getBoundsInScreen(boundsBeforeMagnify);
        final Rect boundsAfterMagnify = new Rect();
        try {
            waitOnMagnificationChanged(controller, scale, centerX, centerY);

            final DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.get().getDisplay().getMetrics(displayMetrics);
            final Rect displayRect = new Rect(0, 0,
                    displayMetrics.widthPixels, displayMetrics.heightPixels);

            // The boundsInScreen of button is adjusted to outside of screen by framework,
            // for example, Rect(-xxx, -xxx, -xxx, -xxx). Intersection of button and screen
            // should be empty.
            TestUtils.waitUntil("Button shouldn't be on the screen, screen is " + displayRect
                    + ", button bounds before magnified is " + boundsBeforeMagnify
                    + ", button bounds after layout is " + boundsAfterMagnify,
                    TIMEOUT_CONFIG_SECONDS,
                    () -> {
                        buttonNode.refresh();
                        buttonNode.getBoundsInScreen(boundsAfterMagnify);
                        return !Rect.intersects(displayRect, boundsAfterMagnify);
                    });
            assertTrue("Button should be visible", buttonNode.isVisibleToUser());
        } finally {
            mService.runOnServiceSync(() -> controller.reset(false));
        }
    }

    @Test
    public void testShowMagnifiableWindow_outOfTheMagnifiedRegion_moveMagnification()
            throws Exception {
        mActivityScenario = ActivityScenario.launch(AccessibilityWindowQueryActivity.class);
        AtomicReference<Activity> activity = new AtomicReference<>();
        mActivityScenario.moveToState(Lifecycle.State.RESUMED).onActivity(activity::set);

        final MagnificationController controller = mService.getMagnificationController();
        final Rect magnifyBounds = controller.getMagnificationRegion().getBounds();
        final float scale = 4.0f;
        // Get right-bottom center position
        final float centerX = magnifyBounds.left + (((float) magnifyBounds.width() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));
        final float centerY = magnifyBounds.top + (((float) magnifyBounds.height() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));

        Button createdButton = null;

        try {
            waitOnMagnificationChanged(controller, scale, centerX, centerY);

            // Add window at left-top position
            Button button =
                    addMagnifiableWindowInActivity(
                            R.string.button1,
                            params -> {
                                params.width = magnifyBounds.width() / 4;
                                params.height = magnifyBounds.height() / 4;
                                params.gravity = Gravity.TOP | Gravity.LEFT;
                            },
                            activity.get());
            createdButton = button;

            TestUtils.waitUntil("bounds are not intersected:", TIMEOUT_CONFIG_SECONDS,
                    () -> {
                        Rect magnifiedArea = getMagnifiedArea(controller);
                        Rect buttonBounds = new Rect();
                        button.getBoundsOnScreen(buttonBounds, false);
                        // magnification should be moved to make the button visible on screen
                        return magnifiedArea.intersect(buttonBounds);
                    });

        } finally {
            if (createdButton != null) {
                activity.get().getWindowManager().removeView(createdButton);
            }
            mService.runOnServiceSync(() -> controller.reset(false));
        }
    }

    @Test
    public void testShowNonMagnifiableWindow_outOfTheMagnifiedRegion_shouldNotMoveMagnification()
            throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Rect magnifyBounds = controller.getMagnificationRegion().getBounds();
        final float scale = 4.0f;
        // Get right-bottom center position
        final float centerX = magnifyBounds.left + (((float) magnifyBounds.width() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));
        final float centerY = magnifyBounds.top + (((float) magnifyBounds.height() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));

        Button createdButton = null;

        try {
            waitOnMagnificationChanged(controller, scale, centerX, centerY);

            // Add window at left-top position
            Button button = addNonMagnifiableWindow(R.string.button1, "button1", params -> {
                params.width = magnifyBounds.width() / 4;
                params.height = magnifyBounds.height() / 4;
                params.gravity = Gravity.TOP | Gravity.LEFT;
            });
            createdButton = button;
            // wait 1 second to check if the magnification would move.
            SystemClock.sleep(1000);
            Rect magnifiedArea = getMagnifiedArea(controller);
            Rect buttonBounds = new Rect();
            button.getBoundsOnScreen(buttonBounds, false);
            // magnification should not be moved since the button is already visible on screen
            assertFalse(magnifiedArea.intersect(buttonBounds));
            assertEquals(centerX, controller.getCenterX(), 0f);
            assertEquals(centerY, controller.getCenterY(), 0f);
        } finally {
            if (createdButton != null) {
                mInstrumentation.getContext().getSystemService(WindowManager.class).removeView(
                        createdButton);
            }
            mService.runOnServiceSync(() -> controller.reset(false));
        }
    }

    @Test
    public void testAccessibilityNodeInfo_getBoundsInWindow_noChangeWhenMagnified()
            throws Exception {
        mActivityScenario = ActivityScenario.launch(AccessibilityWindowQueryActivity.class);
        AtomicReference<Activity> activity = new AtomicReference<>();
        mActivityScenario.moveToState(Lifecycle.State.RESUMED).onActivity(activity::set);
        final MagnificationController controller = mService.getMagnificationController();
        final Rect magnifyBounds = controller.getMagnificationRegion().getBounds();
        final float scale = 8.0f;
        final Button button = activity.get().findViewById(R.id.button1);
        adjustViewBoundsIfNeeded(button, scale, magnifyBounds);

        final AccessibilityNodeInfo buttonNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button1").get(0);
        assertWithMessage("Can't find button on the screen").that(buttonNode).isNotNull();

        // Get right-bottom center position
        final float centerX = magnifyBounds.left + (((float) magnifyBounds.width() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));
        final float centerY = magnifyBounds.top + (((float) magnifyBounds.height() / (2.0f * scale))
                * ((2.0f * scale) - 1.0f));
        final Rect boundsBeforeMagnify = new Rect();
        buttonNode.getBoundsInWindow(boundsBeforeMagnify);

        try {
            waitOnMagnificationChanged(controller, scale, centerX, centerY);

            // The boundsInWindow of button is not adjusted by magnification.
            final Rect boundsAfterMagnify = new Rect();
            buttonNode.getBoundsInWindow(boundsAfterMagnify);
            assertThat(boundsBeforeMagnify).isEqualTo(boundsAfterMagnify);
        } finally {
            mService.runOnServiceSync(() -> controller.reset(false));
        }
    }

    @Test
    public void testTextLocations_changeWhenMagnified() throws Exception {
        AtomicReference<AccessibilityNodeInfo> text = new AtomicReference<>();
        mActivityScenario = ActivityScenario.launch(AccessibilityTextTraversalActivity.class);
        mActivityScenario
                .moveToState(Lifecycle.State.RESUMED)
                .onActivity(
                        activity -> {
                            final TextView textView = activity.findViewById(R.id.text);
                            assertThat(textView).isNotNull();

                            textView.setVisibility(View.VISIBLE);
                            textView.setText(activity.getString(R.string.a_b));

                            text.set(
                                    sUiAutomation
                                            .getRootInActiveWindow()
                                            .findAccessibilityNodeInfosByText(
                                                    activity.getString(R.string.a_b))
                                            .get(0));
                        });
        assertWithMessage("Can't find text on the screen").that(text).isNotNull();

        Bundle extras = waitForExtraTextData(text.get(), EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
        final Parcelable[] initialParcelables = extras.getParcelableArray(
                EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, RectF.class);
        assertNotNull(initialParcelables);
        final RectF[] initialLocations = Arrays.copyOf(initialParcelables,
                initialParcelables.length, RectF[].class);
        assertThat(text.get().getText().length()).isEqualTo(initialLocations.length);

        final MagnificationController controller = mService.getMagnificationController();
        // Pick some scale and center. The actual values are not important, but we do this
        // based on the magnifiable region size so that the values are valid.
        final Rect magnifyBounds = controller.getMagnificationRegion().getBounds();
        final float scale = 8.0f;
        final float centerX =
                magnifyBounds.left
                        + (((float) magnifyBounds.width() / (2.0f * scale))
                                * ((2.0f * scale) - 1.0f));
        final float centerY =
                magnifyBounds.top
                        + (((float) magnifyBounds.height() / (2.0f * scale))
                                * ((2.0f * scale) - 1.0f));

        try {
            waitOnMagnificationChanged(controller, scale, centerX, centerY);
            TestUtils.waitUntil(
                    "Failed to update character coordinates after window magnification",
                    TIMEOUT_CONFIG_SECONDS,
                    () -> {
                        Bundle finalExtras =
                                waitForExtraTextData(
                                        text.get(), EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
                        final Parcelable[] finalParcelables =
                                finalExtras.getParcelableArray(
                                        EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, RectF.class);
                        assertNotNull(finalParcelables);
                        final RectF[] finalLocations =
                                Arrays.copyOf(
                                        finalParcelables, finalParcelables.length, RectF[].class);
                        assertThat(text.get().getText().length()).isEqualTo(finalLocations.length);

                        float tolerance = 0.01f;
                        for (int i = 0; i < initialLocations.length; i++) {
                            // If any loctions match, we haven't updated the character
                            // locations due to a viewport change. Fail the test.
                            if (Math.abs(initialLocations[i].top - finalLocations[i].top)
                                            < tolerance
                                    || Math.abs(
                                                    initialLocations[i].bottom
                                                            - finalLocations[i].bottom)
                                            < tolerance
                                    || Math.abs(initialLocations[i].left - finalLocations[i].left)
                                            < tolerance
                                    || Math.abs(initialLocations[i].right - finalLocations[i].right)
                                            < tolerance) {
                                return false;
                            }
                        }
                        // Success.
                        return true;
                    });
        } finally {
            mService.runOnServiceSync(() -> controller.reset(false));
        }
    }

    @Test
    @RequiresFlagsEnabled(android.view.accessibility.Flags.FLAG_A11Y_CHARACTER_IN_WINDOW_API)
    public void testTextLocationsInWindow_noChangeWhenMagnified() throws Exception {
        AtomicReference<AccessibilityNodeInfo> text = new AtomicReference<>();
        mActivityScenario = ActivityScenario.launch(AccessibilityTextTraversalActivity.class);
        mActivityScenario
                .moveToState(Lifecycle.State.RESUMED)
                .onActivity(
                        activity -> {
                            final TextView textView = activity.findViewById(R.id.text);
                            assertThat(textView).isNotNull();

                            textView.setVisibility(View.VISIBLE);
                            textView.setText(activity.getString(R.string.a_b));

                            text.set(
                                    sUiAutomation
                                            .getRootInActiveWindow()
                                            .findAccessibilityNodeInfosByText(
                                                    activity.getString(R.string.a_b))
                                            .get(0));

                            assertWithMessage("Can't find text on the screen")
                                    .that(text)
                                    .isNotNull();
                        });

        Bundle extras =
                waitForExtraTextData(text.get(), EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY);
        final Parcelable[] initialParcelables = extras.getParcelableArray(
                EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY, RectF.class);
        assertNotNull(initialParcelables);
        final RectF[] initialLocations = Arrays.copyOf(initialParcelables,
                initialParcelables.length, RectF[].class);
        assertThat(text.get().getText().length()).isEqualTo(initialLocations.length);

        final MagnificationController controller = mService.getMagnificationController();
        // Pick some scale and center. The actual values are not important, but we do this
        // based on the magnifiable region size so that the values are valid.
        final Rect magnifyBounds = controller.getMagnificationRegion().getBounds();
        final float scale = 8.0f;
        final float centerX =
                magnifyBounds.left
                        + (((float) magnifyBounds.width() / (2.0f * scale))
                                * ((2.0f * scale) - 1.0f));
        final float centerY =
                magnifyBounds.top
                        + (((float) magnifyBounds.height() / (2.0f * scale))
                                * ((2.0f * scale) - 1.0f));

        try {
            waitOnMagnificationChanged(controller, scale, centerX, centerY);

            // Wait long enough for magnification to impact character bounds.
            // It shouldn't impact bounds in window.
            SystemClock.sleep(1000);

            extras =
                    waitForExtraTextData(
                            text.get(), EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY);
            final Parcelable[] finalParcelables = extras.getParcelableArray(
                    EXTRA_DATA_TEXT_CHARACTER_LOCATION_IN_WINDOW_KEY, RectF.class);
            assertNotNull(finalParcelables);
            final RectF[] finalLocations =
                    Arrays.copyOf(finalParcelables, finalParcelables.length, RectF[].class);
            assertThat(text.get().getText().length()).isEqualTo(finalLocations.length);

            float tolerance = 0.01f;
            for (int i = 0; i < initialLocations.length; i++) {
                assertThat(initialLocations[i].top).isWithin(tolerance).of(finalLocations[i].top);
                assertThat(initialLocations[i].bottom).isWithin(tolerance).of(
                        finalLocations[i].bottom);
                assertThat(initialLocations[i].left).isWithin(tolerance).of(finalLocations[i].left);
                assertThat(initialLocations[i].right).isWithin(tolerance).of(
                        finalLocations[i].right);
            }
        } finally {
            mService.runOnServiceSync(() -> controller.reset(false));
        }
    }

    private boolean isMagnificationOverlayExisting() {
        return sUiAutomation.getWindows().stream().anyMatch(
                accessibilityWindowInfo -> accessibilityWindowInfo.getType()
                        == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY);
    }

    private boolean doesAccessibilityMagnificationOverlayExist() {
        return sUiAutomation.getWindows().stream().anyMatch(
                // TODO: b/335440685 - Move to TYPE_ACCESSIBILITY_OVERLAY after the issues with
                // that type preventing swipe to navigate are resolved.
                accessibilityWindowInfo -> accessibilityWindowInfo.getType()
                        == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY);
    }

    private Rect getMagnifiedArea(MagnificationController magnificationController) {
        final Rect magnifyBounds = magnificationController.getMagnificationRegion().getBounds();
        final float scale = magnificationController.getScale();
        final float centerX = magnificationController.getCenterX();
        final float centerY = magnificationController.getCenterX();

        float halfWidth = magnifyBounds.width() / (2.0f * scale);
        float halfHeight = magnifyBounds.height() / (2.0f * scale);
        return new Rect(
                (int) (centerX - halfWidth),
                (int) (centerY - halfHeight),
                (int) (centerX + halfWidth),
                (int) (centerY + halfHeight));
    }

    private Button addNonMagnifiableWindow(int btnTextRes, String title,
            Consumer<WindowManager.LayoutParams> configure) throws Exception {
        final WindowManager wm =
                mInstrumentation.getContext().getSystemService(WindowManager.class);
        AtomicReference<Button> result = new AtomicReference<>();

        sUiAutomation.executeAndWaitForEvent(() -> {
            mInstrumentation.runOnMainSync(() -> {
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.type = TYPE_NAVIGATION_BAR_PANEL;
                params.accessibilityTitle = title;
                params.format = PixelFormat.TRANSPARENT;
                configure.accept(params);

                final Button button = new Button(mInstrumentation.getContext());
                button.setText(btnTextRes);
                result.set(button);
                SystemUtil.runWithShellPermissionIdentity(sUiAutomation,
                        () -> wm.addView(button, params),
                        "android.permission.INTERNAL_SYSTEM_WINDOW",
                        "android.permission.STATUS_BAR_SERVICE");
            });
        }, filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ADDED), DEFAULT_TIMEOUT_MS);
        return result.get();
    }

    private Button addMagnifiableWindowInActivity(int btnTextRes,
            Consumer<WindowManager.LayoutParams> configure,
            Activity activity) throws TimeoutException {
        AtomicReference<Button> result = new AtomicReference<>();
        sUiAutomation.executeAndWaitForEvent(() -> {
            mInstrumentation.runOnMainSync(() -> {
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                params.token = activity.getWindow().getDecorView().getWindowToken();
                configure.accept(params);

                final Button button = new Button(activity);
                button.setText(btnTextRes);
                result.set(button);
                activity.getWindowManager().addView(button, params);
            });
        }, filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ADDED), DEFAULT_TIMEOUT_MS);
        return result.get();
    }

    private void waitOnMagnificationChanged(MagnificationController controller, float newScale,
            float newCenterX, float newCenterY) {
        final OnMagnificationChangedListener spyListener = mock(
                OnMagnificationChangedListener.class);
        final OnMagnificationChangedListener listener =
                (controller1, region, scale, centerX, centerY) ->
                        spyListener.onMagnificationChanged(controller1, region, scale, centerX,
                                centerY);
        controller.addListener(listener);
        try {
            final AtomicBoolean setScale = new AtomicBoolean();
            final AtomicBoolean setCenter = new AtomicBoolean();
            mService.runOnServiceSync(() -> {
                setScale.set(controller.setScale(newScale, false));
            });

            assertTrue(getSetterErrorMessage("Failed to set scale"), setScale.get());
            verify(spyListener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), eq(newScale), anyFloat(), anyFloat());

            reset(spyListener);
            mService.runOnServiceSync(() -> {
                setCenter.set(controller.setCenter(newCenterX, newCenterY, false));
            });

            assertTrue(getSetterErrorMessage("Failed to set center"), setCenter.get());
            verify(spyListener, timeout(LISTENER_TIMEOUT_MILLIS)).onMagnificationChanged(
                    eq(controller), any(Region.class), anyFloat(), eq(newCenterX), eq(newCenterY));
        } finally {
            controller.removeListener(listener);
        }
    }

    /**
     * Adjust top-left view bounds if it's still in the magnified viewport after sets magnification
     * scale and move centers to bottom-right.
     */
    private void adjustViewBoundsIfNeeded(View topLeftview, float scale, Rect magnifyBounds) {
        final Point magnifyViewportTopLeft = new Point();
        magnifyViewportTopLeft.x = (int)((scale - 1.0f) * ((float) magnifyBounds.width() / scale));
        magnifyViewportTopLeft.y = (int)((scale - 1.0f) * ((float) magnifyBounds.height() / scale));
        magnifyViewportTopLeft.offset(magnifyBounds.left, magnifyBounds.top);

        final int[] viewLocation = new int[2];
        topLeftview.getLocationOnScreen(viewLocation);
        final Rect viewBounds = new Rect(viewLocation[0], viewLocation[1],
                viewLocation[0] + topLeftview.getWidth(),
                viewLocation[1] + topLeftview.getHeight());
        if (viewBounds.right < magnifyViewportTopLeft.x
                && viewBounds.bottom < magnifyViewportTopLeft.y) {
            // no need
            return;
        }

        final ViewGroup.LayoutParams layoutParams = topLeftview.getLayoutParams();
        if (viewBounds.right >= magnifyViewportTopLeft.x) {
            layoutParams.width = topLeftview.getWidth() - 1
                    - (viewBounds.right - magnifyViewportTopLeft.x);
            assertTrue("Needs to fix layout", layoutParams.width > 0);
        }
        if (viewBounds.bottom >= magnifyViewportTopLeft.y) {
            layoutParams.height = topLeftview.getHeight() - 1
                    - (viewBounds.bottom - magnifyViewportTopLeft.y);
            assertTrue("Needs to fix layout", layoutParams.height > 0);
        }
        mInstrumentation.runOnMainSync(() -> topLeftview.setLayoutParams(layoutParams));
        // Waiting for UI refresh
        mInstrumentation.waitForIdleSync();
    }

    private void waitUntilMagnificationConfig(MagnificationController controller,
            MagnificationConfig config) throws Exception {
        TestUtils.waitUntil(
                "Failed to apply the config. expected: " + config + " , actual: "
                        + controller.getMagnificationConfig(), TIMEOUT_CONFIG_SECONDS,
                () -> {
                    final MagnificationConfig actualConfig = controller.getMagnificationConfig();
                    Log.d(TAG, "Polling config: " + actualConfig.toString());
                    // If expected config activated is false, we just need to verify the activated
                    // value is the same. Otherwise, we need to check all the actual values are
                    // equal to the expected values.
                    if (config.isActivated()) {
                        return actualConfig.getMode() == config.getMode()
                                && actualConfig.isActivated() == config.isActivated()
                                && Float.compare(actualConfig.getScale(), config.getScale()) == 0
                                && (Math.abs(actualConfig.getCenterX() - config.getCenterX())
                                        <= BOUNDS_TOLERANCE)
                                && (Math.abs(actualConfig.getCenterY() - config.getCenterY())
                                        <= BOUNDS_TOLERANCE);
                    } else {
                        return actualConfig.isActivated() == config.isActivated();
                    }
                });
    }

    private void waitUntilMagnificationRegion(MagnificationController controller,
            Region expectedRegion) throws Exception {
        TestUtils.waitUntil(
                "Failed to apply the region. expected: " + expectedRegion + " , actual: "
                        + controller.getMagnificationRegion(), TIMEOUT_CONFIG_SECONDS,
                () -> {
                    final Region actualRegion = controller.getMagnificationRegion();
                    Log.d(TAG, "Polling region: " + actualRegion);
                    return actualRegion.equals(expectedRegion);
                });
    }

    private void assertConfigEquals(MagnificationConfig expected, MagnificationConfig result) {
        // If expected config activated is false, we just need to verify the activated
        // value is the same. Otherwise, we need to check all the actual values are
        // equal to the expected values.
        if (expected.isActivated()) {
            assertEquals("Failed to apply mode", expected.getMode(),
                    result.getMode(), 0f);
            assertEquals("Failed to apply activated", expected.isActivated(),
                    result.isActivated());
            assertEquals("Failed to apply scale", expected.getScale(),
                    result.getScale(), 0f);
            assertEquals("Failed to apply center X", expected.getCenterX(),
                    result.getCenterX(), 5.0f);
            assertEquals("Failed to apply center Y", expected.getCenterY(),
                    result.getCenterY(), 5.0f);
        } else {
            assertEquals("Failed to apply activated", expected.isActivated(),
                    result.isActivated());
        }
    }

    private static boolean isWindowModeSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        final boolean isWatch = pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
        return pm.hasSystemFeature(FEATURE_WINDOW_MAGNIFICATION) && !isWatch;
    }

    private static MagnificationConfig.Builder obtainConfigBuilder(MagnificationConfig config) {
        MagnificationConfig.Builder builder = new MagnificationConfig.Builder();
        builder.setMode(config.getMode())
                .setScale(config.getScale())
                .setCenterX(config.getCenterX())
                .setCenterY(config.getCenterY());
        return builder;
    }

    private String getSetterErrorMessage(String rawMessage) {
        String postfix = ". The failure may be because the service connection does not exist"
                + ", the given setter info does not make the magnification spec changed"
                + ", or the SystemUI connection does not exist."
                + " Please ensure your SystemUI implements the IMagnificationConnection AIDL.";
        return rawMessage + postfix;
    }

    private Bundle waitForExtraTextData(AccessibilityNodeInfo info, String key) {
        final Bundle getTextArgs = new Bundle();
        getTextArgs.putInt(EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0);
        getTextArgs.putInt(EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, info.getText().length());
        // Node refresh must succeed and the resulting extras must contain the requested key.
        try {
            TestUtils.waitUntil("Timed out waiting for extra data", () -> {
                info.refreshWithExtraData(key, getTextArgs);
                return info.getExtras().containsKey(key);
            });
        } catch (Exception e) {
            fail(e.getMessage());
        }

        return info.getExtras();
    }
}
