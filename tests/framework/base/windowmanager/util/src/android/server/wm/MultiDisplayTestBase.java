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

package android.server.wm;

import static android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;
import static android.content.pm.PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_INPUT_METHODS;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.UiDeviceUtils.pressSleepButton;
import static android.view.Display.INVALID_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.cts.mockime.ImeEventStreamTestUtils.clearAllEvents;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.withDescription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.server.wm.CommandSession.ActivitySession;
import android.server.wm.CommandSession.ActivitySessionClient;
import android.server.wm.WindowManagerState.DisplayContent;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeEventStreamTestUtils;

import org.junit.Before;
import org.junit.ClassRule;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base class for ActivityManager display tests.
 *
 * @see android.server.wm.display.DisplayTests
 * @see android.server.wm.display.AppConfigurationTests
 * @see android.server.wm.multidisplay.MultiDisplayKeyguardTests
 * @see android.server.wm.multidisplay.MultiDisplayLockedKeyguardTests
 */
public class MultiDisplayTestBase extends ActivityManagerTestBase {

    public static final int CUSTOM_DENSITY_DPI = 222;
    protected Context mTargetContext;

    @ClassRule
    public static DisableImmersiveModeConfirmationRule mDisableImmersiveModeConfirmationRule =
            new DisableImmersiveModeConfirmationRule();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTargetContext = getInstrumentation().getTargetContext();
    }

    protected boolean isAutomotive() {
        return mContext.getPackageManager().hasSystemFeature(FEATURE_AUTOMOTIVE);
    }

    protected boolean supportsInstallableIme() {
        return mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS);
    }

    public static class LetterboxAspectRatioSession extends IgnoreOrientationRequestSession {
        private static final String WM_SET_LETTERBOX_STYLE_ASPECT_RATIO =
                "wm set-letterbox-style --aspectRatio ";
        private static final String WM_RESET_LETTERBOX_STYLE_ASPECT_RATIO =
                "wm reset-letterbox-style aspectRatio";

        LetterboxAspectRatioSession(float aspectRatio) {
            super(true);
            executeShellCommand(WM_SET_LETTERBOX_STYLE_ASPECT_RATIO + aspectRatio);
        }

        @Override
        public void close() {
            super.close();
            executeShellCommand(WM_RESET_LETTERBOX_STYLE_ASPECT_RATIO);
        }
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected LetterboxAspectRatioSession createManagedLetterboxAspectRatioSession(
            float aspectRatio) {
        return mObjectTracker.manage(new LetterboxAspectRatioSession(aspectRatio));
    }

    // TODO(b/112837428): Merge into VirtualDisplaySession when all usages are migrated.
    public class VirtualDisplayLauncher extends VirtualDisplaySession {
        private final ActivitySessionClient mActivitySessionClient = createActivitySessionClient();

        public ActivitySession launchActivityOnDisplay(ComponentName activityName,
                DisplayContent display) {
            return launchActivityOnDisplay(activityName, display, null /* extrasConsumer */,
                    true /* withShellPermission */, true /* waitForLaunch */);
        }

        public ActivitySession launchActivityOnDisplay(ComponentName activityName,
                DisplayContent display, Consumer<Bundle> extrasConsumer,
                boolean withShellPermission, boolean waitForLaunch) {
            return launchActivity(builder -> builder
                    // VirtualDisplayActivity is in different package. If the display is not public,
                    // it requires shell permission to launch activity ({@see com.android.server.wm.
                    // ActivityStackSupervisor#isCallerAllowedToLaunchOnDisplay}).
                    .setWithShellPermission(withShellPermission)
                    .setWaitForLaunched(waitForLaunch)
                    .setIntentExtra(extrasConsumer)
                    .setTargetActivity(activityName)
                    .setDisplayId(display.mId));
        }

        public ActivitySession launchActivity(Consumer<LaunchActivityBuilder> setupBuilder) {
            final LaunchActivityBuilder builder = getLaunchActivityBuilder()
                    .setUseInstrumentation();
            setupBuilder.accept(builder);
            return mActivitySessionClient.startActivity(builder);
        }

        @Override
        public void close() {
            super.close();
            mActivitySessionClient.close();
        }
    }

    /** A clearer alias of {@link Pair#create(Object, Object)}. */
    protected <K, V> Pair<K, V> pair(K k, V v) {
        return new Pair<>(k, v);
    }

    protected void assertBothDisplaysHaveResumedActivities(
            Pair<Integer, ComponentName> firstPair, Pair<Integer, ComponentName> secondPair) {
        assertNotEquals("Displays must be different.  First display id: "
                        + firstPair.first, firstPair.first, secondPair.first);
        mWmState.assertResumedActivities("Both displays must have resumed activities",
                mapping -> {
                    mapping.put(firstPair.first, firstPair.second);
                    mapping.put(secondPair.first, secondPair.second);
                });
    }

    /** Checks if the device supports multi-display. */
    protected boolean supportsMultiDisplay() {
        return hasDeviceFeature(FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
    }

    /** Checks if the device supports live wallpaper for multi-display. */
    protected boolean supportsLiveWallpaper() {
        return hasDeviceFeature(PackageManager.FEATURE_LIVE_WALLPAPER);
    }

    /** Checks if the device supports wallpaper. */
    protected boolean supportsWallpaper() {
        return WallpaperManager.getInstance(mContext).isWallpaperSupported();
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected ExternalDisplaySession createManagedExternalDisplaySession() {
        return mObjectTracker.manage(new ExternalDisplaySession());
    }

    @SafeVarargs
    protected final void waitOrderedImeEventsThenAssertImeShown(ImeEventStream stream,
            int displayId,
            Predicate<ImeEvent>... conditions) throws Exception {
        for (var condition : conditions) {
            expectEvent(stream, condition, TimeUnit.SECONDS.toMillis(5) /* eventTimeout */);
        }
        // Assert the IME is shown on the expected display.
        mWmState.waitAndAssertImeWindowShownOnDisplay(displayId);
    }

    protected void waitAndAssertImeNoScreenSizeChanged(ImeEventStream stream) {
        notExpectEvent(stream, withDescription("onConfigurationChanged(SCREEN_SIZE | ..)",
                event -> "onConfigurationChanged".equals(event.getEventName())
                        && (event.getArguments().getInt("ConfigUpdates") & CONFIG_SCREEN_SIZE)
                        != 0), TimeUnit.SECONDS.toMillis(1) /* eventTimeout */);
    }

    /**
     * Clears all {@link InputMethodService#onConfigurationChanged(Configuration)} events from the
     * given {@code stream} and returns a forked {@link ImeEventStream}.
     *
     * @see ImeEventStreamTestUtils#clearAllEvents(ImeEventStream, String)
     */
    protected ImeEventStream clearOnConfigurationChangedFromStream(ImeEventStream stream) {
        return clearAllEvents(stream, "onConfigurationChanged");
    }

    /**
     * This class is used when you need to test virtual display created by a privileged app.
     *
     * If you need to test virtual display created by a non-privileged app or when you need to test
     * on simulated display, please use {@link VirtualDisplaySession} instead.
     */
    public class ExternalDisplaySession implements AutoCloseable {

        private boolean mCanShowWithInsecureKeyguard = false;
        private boolean mPublicDisplay = false;
        private boolean mShowSystemDecorations = false;

        private int mDisplayId = INVALID_DISPLAY;

        @Nullable
        private VirtualDisplayHelper mExternalDisplayHelper;

        public ExternalDisplaySession setCanShowWithInsecureKeyguard(boolean canShowWithInsecureKeyguard) {
            mCanShowWithInsecureKeyguard = canShowWithInsecureKeyguard;
            return this;
        }

        public ExternalDisplaySession setPublicDisplay(boolean publicDisplay) {
            mPublicDisplay = publicDisplay;
            return this;
        }

        /**
         * @deprecated untrusted virtual display won't have system decorations even it has the flag.
         * Only use this method to verify that. To test secondary display with system decorations,
         * please use simulated display.
         */
        @Deprecated
        public ExternalDisplaySession setShowSystemDecorations(boolean showSystemDecorations) {
            mShowSystemDecorations = showSystemDecorations;
            return this;
        }

        /**
         * Creates a private virtual display with insecure keyguard flags set.
         */
        public DisplayContent createVirtualDisplay() {
            final List<DisplayContent> originalDS = getDisplaysStates();
            final int originalDisplayCount = originalDS.size();

            mExternalDisplayHelper = new VirtualDisplayHelper();
            mExternalDisplayHelper
                    .setPublicDisplay(mPublicDisplay)
                    .setCanShowWithInsecureKeyguard(mCanShowWithInsecureKeyguard)
                    .setShowSystemDecorations(mShowSystemDecorations)
                    .createAndWaitForDisplay();

            // Wait for the virtual display to be created and get configurations.
            final List<DisplayContent> ds = getDisplayStateAfterChange(originalDisplayCount + 1);
            assertEquals("New virtual display must be created", originalDisplayCount + 1,
                    ds.size());

            // Find the newly added display.
            final DisplayContent newDisplay = findNewDisplayStates(originalDS, ds).get(0);
            mDisplayId = newDisplay.mId;
            return newDisplay;
        }

        public void turnDisplayOff() {
            if (mExternalDisplayHelper == null) {
                throw new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOff();
        }

        public void turnDisplayOn() {
            if (mExternalDisplayHelper == null) {
                throw new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOn();
        }

        @Override
        public void close() {
            if (mExternalDisplayHelper != null) {
                mExternalDisplayHelper.releaseDisplay();
                mExternalDisplayHelper = null;

                waitForDisplayGone(d -> d.mId == mDisplayId);
                mDisplayId = INVALID_DISPLAY;
            }
        }
    }

    public class PrimaryDisplayStateSession implements AutoCloseable {

        public void turnScreenOff() {
            setPrimaryDisplayState(false);
        }

        @Override
        public void close() {
            setPrimaryDisplayState(true);
        }

        /** Turns the primary display on/off by pressing the power key */
        private void setPrimaryDisplayState(boolean wantOn) {
            if (wantOn) {
                UiDeviceUtils.wakeUpAndUnlock(mContext);
            } else {
                pressSleepButton();
            }
            VirtualDisplayHelper.waitForDefaultDisplayState(wantOn);
        }
    }
}
