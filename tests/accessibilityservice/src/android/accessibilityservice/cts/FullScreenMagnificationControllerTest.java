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

package android.accessibilityservice.cts;

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.homeScreenOrBust;
import static android.accessibilityservice.cts.utils.CtsTestUtils.DEFAULT_GLOBAL_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.CtsTestUtils.DEFAULT_IDLE_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.CtsTestUtils.isAutomotive;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.MagnificationConfig;
import android.accessibilityservice.cts.activities.AccessibilityWindowQueryActivity;
import android.accessibilityservice.cts.utils.SettingsSession;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.res.Resources;
import android.graphics.Rect;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.TestUtils;
import com.android.compatibility.common.util.UserSettings;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for testing {@See FullScreenMagnificationController}.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class FullScreenMagnificationControllerTest {

    /** Maximum timeout while waiting for a config to be updated */
    private static final int TIMEOUT_CONFIG_SECONDS = 15;

    private static final int BOUNDS_TOLERANCE = 1;

    private static final String DEVICE_CONFIG_NAMESPACE_WM = "window_manager";
    private static final String DEVICE_CONFIG_KEY_ALWAYS_ON_MAGNIFIER =
            "AlwaysOnMagnifier__enable_always_on_magnifier";
    private static final String SETTING_KEY_MAGNIFICATION_ALWAYS_ON =
            "accessibility_magnification_always_on_enabled";

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private StubMagnificationAccessibilityService mService;

    private ActivityScenario<AccessibilityWindowQueryActivity> mActivityScenario = null;

    private final InstrumentedAccessibilityServiceTestRule<StubMagnificationAccessibilityService>
            mMagnificationAccessibilityServiceRule =
                    new InstrumentedAccessibilityServiceTestRule<>(
                            StubMagnificationAccessibilityService.class, false);

    // StateChangerRules starts UiAutomation without FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES.
    // They have to be outer rule than other accessibility related rules.
    private final DeviceConfigStateChangerRule mDeviceConfigStateChangerRule =
            new DeviceConfigStateChangerRule(
                    sInstrumentation.getContext(),
                    DEVICE_CONFIG_NAMESPACE_WM,
                    DEVICE_CONFIG_KEY_ALWAYS_ON_MAGNIFIER,
                    "true");

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mDeviceConfigStateChangerRule)
                    .around(mMagnificationAccessibilityServiceRule)
                    .around(new AccessibilityDumpOnFailureRule());

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        assumeFalse("Magnification is not supported on Automotive.",
                isAutomotive(sInstrumentation.getTargetContext()));
        mService = mMagnificationAccessibilityServiceRule.enableService();

        // `setServiceInfo` resets magnification unless there's any magnification listener.
        // In `homeScreenOrBust`, `uiAutomation.setServiceInfo` is done to ensure uiAutomation can
        // listen the window events.
        // Although we don't need to listen magnification here, adding an empty listener makes sure
        // that magnification won't be reset by calling `setServiceInfo`.
        // See also b/401998908.
        final MagnificationController controller = mService.getMagnificationController();
        controller.addListener(
                (controllerInner, region, scale1, centerX, centerY) -> {
                    // Do nothing.
                });
    }

    @After
    public void cleanUp() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    @Test
    public void testActivityTransitions_alwaysOnEnabled_keepMagnifiedDisabled_zoomOut()
            throws Exception {
        assumeFalse(isKeepMagnifiedOnContextChangeEnabled());

        try (var session = getAlwaysOnSettingsSession(true)) {
            mActivityScenario = launchActivityAndWait();

            zoomIn(/* scale= */ 2.0f);
            // transition to home screen
            homeScreenOrBust(sInstrumentation.getContext(), sUiAutomation);

            assertThat(currentScale()).isEqualTo(1f);
            assertThat(isActivated()).isTrue();
        }
    }

    @Test
    public void testActivityTransitions_alwaysOnEnabled_keepMagnifiedEnabled_keepZoom()
            throws Exception {
        assumeTrue(isKeepMagnifiedOnContextChangeEnabled());

        try (var session = getAlwaysOnSettingsSession(true)) {
            mActivityScenario = launchActivityAndWait();

            zoomIn(/* scale= */ 2.0f);
            // transition to home screen
            homeScreenOrBust(sInstrumentation.getContext(), sUiAutomation);

            assertThat(currentScale()).isEqualTo(2f);
            assertThat(isActivated()).isTrue();
        }
    }

    @Test
    public void testActivityTransitions_alwaysOnDisabled_disableMagnification() throws Exception {
        try (var session = getAlwaysOnSettingsSession(false)) {
            mActivityScenario = launchActivityAndWait();

            zoomIn(/* scale= */ 2.0f);
            // transition to home screen
            homeScreenOrBust(sInstrumentation.getContext(), sUiAutomation);

            assertThat(currentScale()).isEqualTo(1f);
            assertThat(isActivated()).isFalse();
        }
    }

    // launch an activity and waits for it to be on screen
    private ActivityScenario<AccessibilityWindowQueryActivity> launchActivityAndWait()
            throws Exception {
        final var activityScenario =
                ActivityScenario.launch(AccessibilityWindowQueryActivity.class)
                        .moveToState(Lifecycle.State.RESUMED);
        sUiAutomation.waitForIdle(DEFAULT_IDLE_TIMEOUT_MS, DEFAULT_GLOBAL_TIMEOUT_MS);
        return activityScenario;
    }

    private void zoomIn(float scale) throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Rect rect = controller.getMagnificationRegion().getBounds();
        final float x = rect.centerX();
        final float y = rect.centerY();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();

        mService.runOnServiceSync(
                () -> {
                    setConfig.set(controller.setMagnificationConfig(config, false));
                });
        waitUntilMagnificationConfigEquals(controller, config);

        assertTrue("Failed to set config", setConfig.get());
    }

    private float currentScale() {
        final MagnificationController controller = mService.getMagnificationController();
        final MagnificationConfig config = controller.getMagnificationConfig();

        assertThat(config).isNotNull();

        return config.getScale();
    }

    private boolean isActivated() {
        final MagnificationController controller = mService.getMagnificationController();
        final MagnificationConfig config = controller.getMagnificationConfig();

        assertThat(config).isNotNull();

        return config.isActivated();
    }

    private void waitUntilMagnificationConfigEquals(
            AccessibilityService.MagnificationController controller,
            MagnificationConfig config) throws Exception {
        TestUtils.waitUntil(
                "Failed to apply the config. expected: " + config + " , actual: "
                        + controller.getMagnificationConfig(), TIMEOUT_CONFIG_SECONDS,
                () -> {
                    final MagnificationConfig actualConfig = controller.getMagnificationConfig();
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

    private boolean isKeepMagnifiedOnContextChangeEnabled() {
        try {
            return sInstrumentation.getTargetContext().getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_magnification_keep_zoom_level_when_context_changed", "bool",
                            "android"));
        } catch (Resources.NotFoundException ignore) {
            return false;
        }
    }

    private static SettingsSession getAlwaysOnSettingsSession(boolean enabled) throws IOException {
        return new SettingsSession(
                sInstrumentation,
                UserSettings.Namespace.SECURE,
                SETTING_KEY_MAGNIFICATION_ALWAYS_ON,
                enabled ? "1" : "0");
    }
}
