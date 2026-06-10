/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.service.dreams.cts;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.app.Components.PIP_ACTIVITY;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ON_PAUSE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.dream.cts.app.IControlledDream;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DreamCoordinator;
import android.server.wm.LockScreenSession;
import android.service.dreams.DreamService;
import android.service.dreams.Flags;
import android.view.ActionMode;
import android.view.Display;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Build/Install/Run:
 *     atest CtsDreamsTestCases:DreamServiceTest
 */
@RunWith(AndroidJUnit4.class)
public class DreamServiceTest extends ActivityManagerTestBase {
    private static final int TIMEOUT_SECONDS = 2;
    private static final String DREAM_APP_PACKAGE_NAME = "android.app.dream.cts.app";
    private static final String DREAM_SERVICE_COMPONENT =
            DREAM_APP_PACKAGE_NAME + "/.SeparateProcessDreamService";
    private static final String CONTROLLED_DREAM_SERVICE_COMPONENT =
            DREAM_APP_PACKAGE_NAME + "/.ControlledTestDreamService";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    /**
     * A simple {@link BroadcastReceiver} implementation that counts down a
     * {@link CountDownLatch} when a matching message is received
     */
    static final class DreamBroadcastReceiver extends BroadcastReceiver {
        final CountDownLatch mLatch;

        DreamBroadcastReceiver(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mLatch.countDown();
        }
    }

    @Before
    public void setup() {
        mDreamCoordinator.setup();
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
    }

    @After
    public void tearDown() {
        mDreamCoordinator.restoreDefaults();
        stopTestPackage(DREAM_APP_PACKAGE_NAME);
    }

    @Test
    public void testOnWindowStartingActionMode() {
        DreamService dreamService = new DreamService();

        ActionMode actionMode = dreamService.onWindowStartingActionMode(null);

        assertEquals(actionMode, null);
    }

    @Test
    public void testOnWindowStartingActionModeTyped() {
        DreamService dreamService = new DreamService();

        ActionMode actionMode = dreamService.onWindowStartingActionMode(
                null, ActionMode.TYPE_FLOATING);

        assertEquals(actionMode, null);
    }

    @Test
    public void testDreamInSeparateProcess() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);

        mDreamCoordinator.startDream();
        waitAndAssertResumedAndFocusedActivityOnDisplay(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mDreamCoordinator.stopDream();
    }

    @Test
    public void testMetadataParsing() throws PackageManager.NameNotFoundException {
        final String dreamComponent = DREAM_APP_PACKAGE_NAME + "/.TestDreamService";
        final String testSettingsActivity =
                DREAM_APP_PACKAGE_NAME + "/.TestDreamSettingsActivity";
        final DreamService.DreamMetadata metadata = getDreamMetadata(dreamComponent);

        assertThat(metadata.settingsActivity).isEqualTo(
                ComponentName.unflattenFromString(testSettingsActivity));
        assertThat(metadata.showComplications).isFalse();
        assertThat(metadata.dreamCategory).isEqualTo(DreamService.DREAM_CATEGORY_HOME_PANEL);
    }

    @Test
    public void testMetadataParsing_invalidSettingsActivity()
            throws PackageManager.NameNotFoundException {
        final String dreamComponent =
                DREAM_APP_PACKAGE_NAME + "/.TestDreamServiceWithInvalidSettings";
        final DreamService.DreamMetadata metadata = getDreamMetadata(dreamComponent);

        assertThat(metadata.settingsActivity).isNull();
        assertThat(metadata.dreamCategory).isEqualTo(DreamService.DREAM_CATEGORY_DEFAULT);
    }

    @Test
    public void testMetadataParsing_nonexistentSettingsActivity()
            throws PackageManager.NameNotFoundException {
        final String testDreamClassName =
                DREAM_APP_PACKAGE_NAME + "/.TestDreamServiceWithNonexistentSettings";
        final DreamService.DreamMetadata metadata = getDreamMetadata(testDreamClassName);

        assertThat(metadata.settingsActivity).isNull();
        assertThat(metadata.dreamCategory).isEqualTo(DreamService.DREAM_CATEGORY_DEFAULT);
    }

    @Test
    public void testMetadataParsing_noPackage_nonexistentSettingsActivity()
            throws PackageManager.NameNotFoundException {
        final String testDreamClassName =
                DREAM_APP_PACKAGE_NAME + "/.TestDreamServiceNoPackageNonexistentSettings";
        final DreamService.DreamMetadata metadata = getDreamMetadata(testDreamClassName);

        assertThat(metadata.settingsActivity).isNull();
        assertThat(metadata.dreamCategory).isEqualTo(DreamService.DREAM_CATEGORY_DEFAULT);
    }

    private DreamService.DreamMetadata getDreamMetadata(String dreamComponent)
            throws PackageManager.NameNotFoundException {
        final ServiceInfo si = mContext.getPackageManager().getServiceInfo(
                ComponentName.unflattenFromString(dreamComponent),
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));
        return DreamService.getDreamMetadata(mContext, si);
    }

    @Test
    public void testDreamServiceOnDestroyCallback() throws InterruptedException {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);

        mDreamCoordinator.startDream();
        waitAndAssertResumedAndFocusedActivityOnDisplay(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");

        removeRootTasksWithDreamTypeActivity();

        // Listen for the dream to end
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mContext.registerReceiver(
                new DreamBroadcastReceiver(countDownLatch),
                new IntentFilter(Intent.ACTION_DREAMING_STOPPED));
        assertThat(countDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertFalse("DreamService is still dreaming", mDreamCoordinator.isDreaming());
        mDreamCoordinator.stopDream();
    }

    @Test
    public void testDreamDoesNotForcePictureInPicture() {
        assumeTrue(supportsPip());

        // Launch a PIP activity
        launchActivity(PIP_ACTIVITY, extraString(EXTRA_ENTER_PIP_ON_PAUSE, "true"));

        // Asserts that the pinned stack does not exist.
        mWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);
        mDreamCoordinator.startDream();
        waitAndAssertResumedAndFocusedActivityOnDisplay(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mDreamCoordinator.stopDream();

        // Asserts that the pinned stack does not exist.
        mWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
    }

    private ControlledDreamSession startControlledDream()
            throws RemoteException, InterruptedException {
        final ComponentName dreamComponent =
                ComponentName.unflattenFromString(CONTROLLED_DREAM_SERVICE_COMPONENT);
        final ControlledDreamSession dreamSession = new ControlledDreamSession(mContext,
                dreamComponent, mDreamCoordinator);

        dreamSession.start();
        waitAndAssertResumedActivity(mDreamCoordinator.getDreamActivityName(dreamComponent),
                "Dream activity should be resumed");
        dreamSession.awaitLifecycle(ControlledDreamSession.DREAM_LIFECYCLE_ON_FOCUS_GAINED);
        return dreamSession;
    }

    /**
     * Validates that pressing a confirm key when dreaming over an insecure keyguard wakes and
     * dismisses the keyguard.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DREAM_HANDLES_CONFIRM_KEYS)
    public void testKeyHandling_InsecureKeyguardDismissesOnConfirmKey()
            throws RemoteException, InterruptedException {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        final ControlledDreamSession dreamSession = startControlledDream();

        injectKey(KeyEvent.KEYCODE_SPACE, false, true);

        dreamSession.awaitLifecycle(ControlledDreamSession.DREAM_LIFECYCLE_ON_WAKEUP);

        // Assert keyguard is gone
        mWmState.waitAndAssertKeyguardGone();
        dreamSession.stop();
    }

    /**
     * Checks that dismissing a dream from confirm key over a secure keyguard prompts the bouncer.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DREAM_HANDLES_CONFIRM_KEYS)
    public void testKeyHandling_SecureKeyguardConfirmKeyPromptsBouncer()
            throws InterruptedException, RemoteException {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LEANBACK));
        // Set secure lock credentials
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();

        // Go to the keyguard
        lockScreenSession.gotoKeyguard();
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertKeyguardShowingAndNotOccluded();

        final ControlledDreamSession dreamSession = startControlledDream();

        // Confirm and enter credentials
        injectKey(KeyEvent.KEYCODE_SPACE, true, true);
        dreamSession.awaitLifecycle(ControlledDreamSession.DREAM_LIFECYCLE_ON_FOCUS_LOST);

        // Ensure keyguard is still visible
        mWmState.assertKeyguardShowingAndOccluded();
        lockScreenSession.enterAndConfirmLockCredential();

        mWmState.waitForHomeActivityVisible();

        dreamSession.stop();
    }

    /**
     * Checks that interactive dreams are given priority on consuming keys and no action is taken
     * in the case a confirm key is consumed.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DREAM_HANDLES_CONFIRM_KEYS)
    public void testKeyHandling_InteractiveDreamConsumesConfirmNoWakeup()
            throws InterruptedException, RemoteException {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        // Start the dream session
        final ControlledDreamSession dreamSession = startControlledDream();
        final IControlledDream dream = dreamSession.getControlledDream();

        // Make the dream interactive
        dream.setInteractive(true);

        // Add space bar as a consumed key
        dream.setConsumedKeys(new int[]{ KeyEvent.KEYCODE_SPACE});

        // Dispatch confirm key
        injectKey(KeyEvent.KEYCODE_SPACE, true, true);

        // Assert that the device is still dreaming
        assertThat(mDreamCoordinator.isDreaming()).isTrue();

        dreamSession.stop();
    }

    /**
     * Checks that pressing a confirm key over an interactive dream that doesn't consume the key
     * causes the bouncer to show.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DREAM_HANDLES_CONFIRM_KEYS)
    public void testKeyHandling_InteractiveDreamDoesNotConsumeConfirmPromptsBouncer()
            throws InterruptedException, RemoteException {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LEANBACK));
        // Set secure lock credentials
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();

        lockScreenSession.gotoKeyguard();
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertKeyguardShowingAndNotOccluded();

        // Start the dream session
        final ControlledDreamSession dreamSession = startControlledDream();
        final IControlledDream dream = dreamSession.getControlledDream();

        // Make the dream interactive, but do not consume key presses
        dream.setInteractive(true);

        // Confirm and enter credentials
        injectKey(KeyEvent.KEYCODE_SPACE, true, true);
        dreamSession.awaitLifecycle(ControlledDreamSession.DREAM_LIFECYCLE_ON_FOCUS_LOST);

        // Ensure keyguard is still visible
        mWmState.assertKeyguardShowingAndOccluded();
        lockScreenSession.enterAndConfirmLockCredential();

        mWmState.waitForHomeActivityVisible();

        dreamSession.stop();
    }

    private void removeRootTasksWithDreamTypeActivity() {
        runWithShellPermission(() -> {
            mAtm.removeRootTasksWithActivityTypes(new int[]{ACTIVITY_TYPE_DREAM});
        });
        waitForIdle();
    }
}
