/*
 * Copyright 2024 The Android Open Source Project
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

package android.photopicker.cts;

import static android.photopicker.cts.PhotoPickerBaseTest.isHardwareSupported;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.assertUiObjectExistsWithId;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAndClickUiObjectWithId;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findObject;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getMediaItem;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getMediaItemSelector;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getUiObjectMatchingText;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getUiObjectMatchingTextSelector;

import static com.android.providers.media.flags.Flags.enableEmbeddedPhotopicker;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.view.SurfaceView;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.UserHelper;
import com.android.providers.media.flags.Flags;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EMBEDDED_PHOTOPICKER)
public class EmbeddedPhotoPickerTest {
    private static final String RESOURCE_ID_REGEX_PREFIX = ".*:id/";
    private static final String LAUNCH_EMBEDDED_BUTTON_ID =
            RESOURCE_ID_REGEX_PREFIX + "open_embedded_picker_session_button";
    private static final String EMBEDDED_SURFACE_ID =
            RESOURCE_ID_REGEX_PREFIX + "embedded_picker_surface";
    private static final String PHOTOS_TAB_LABEL = "Photos";
    private static final String ACTION_EMBEDDED_PHOTOPICKER_SERVICE =
            "com.android.photopicker.core.embedded.EmbeddedService.BIND";
    private static final String DONE_BUTTON_LABEL = "Done";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final UiDevice sDevice = UiDevice.getInstance(sInstrumentation);

    private final Context mContext = sInstrumentation.getContext();
    private final List<Uri> mUriList = new ArrayList<>();
    private EmbeddedTestActivity mActivity;

    private Intent mIntent = new Intent(ACTION_EMBEDDED_PHOTOPICKER_SERVICE);
    private CountDownLatch mSessionCountDownLatch;

    private boolean mIsVisibleBackgroundUser = false;
    private int mDisplayId = Display.DEFAULT_DISPLAY;

    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(isHardwareSupported());
        Assume.assumeTrue(enableEmbeddedPhotopicker());

        // Wake up the device and dismiss the keyguard before the test starts
        sDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        sDevice.executeShellCommand("wm dismiss-keyguard");
        UserHelper userHelper = new UserHelper(mContext);
        mIsVisibleBackgroundUser = userHelper.isVisibleBackgroundUser();
        mDisplayId = userHelper.getMainDisplayId();
    }

    @After
    public void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }

        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();
    }

    @Test
    public void testOnSessionOpened_sessionNotNull() throws Exception {
        addMediaAndLaunchActivity(1);
        assertThat(mActivity.getSession()).isNull();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();
    }

    @Test
    public void testOnItemSelected_selectedUrisNotEmptyAndHasAccess() throws Exception {
        addMediaAndLaunchActivity(1);
        final List<Uri> selectedUris = mActivity.getSelectedUris();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        assertThat(selectedUris.isEmpty()).isTrue();

        // 2. Get media item, perform click and wait for 'onItemsSelected' client invocation
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForItemsSelectedClientInvocation(countDownLatch);

        if (mIsVisibleBackgroundUser) {
            clickAndWait(sDevice, findObject(sDevice, getMediaItemSelector(mDisplayId)));
        } else {
            clickAndWait(sDevice, getMediaItem(sDevice));
        }

        assertThat(countDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(selectedUris.size()).isEqualTo(1);
        Uri selectedUri = selectedUris.get(0);
        assertThat(hasUriPermission(selectedUri)).isTrue();
    }

    @Test
    public void testOnItemDeselected_selectedUrisEmptyAndAccessRevoked() throws Exception {
        addMediaAndLaunchActivity(1);
        final List<Uri> selectedUris = mActivity.getSelectedUris();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        assertThat(selectedUris.isEmpty()).isTrue();

        // 2. Get media item, perform click and wait for 'onItemsSelected' client invocation
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForItemsSelectedClientInvocation(countDownLatch);

        if (mIsVisibleBackgroundUser) {
            clickAndWait(sDevice, findObject(sDevice, getMediaItemSelector(mDisplayId)));
        } else {
            clickAndWait(sDevice, getMediaItem(sDevice));
        }

        assertThat(countDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(selectedUris.size()).isEqualTo(1);
        Uri selectedUri = selectedUris.get(0);

        // 3. Set a new count down latch for 'onItemsDeselected' client invocation
        countDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForItemsDeselectedClientInvocation(countDownLatch);

        // 4. Deselect the previously selected media item
        if (mIsVisibleBackgroundUser) {
            clickAndWait(sDevice, findObject(sDevice, getMediaItemSelector(mDisplayId)));
        } else {
            clickAndWait(sDevice, getMediaItem(sDevice));
        }

        // 5. Assert that the item is deselected.
        assertThat(countDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(selectedUris.isEmpty()).isTrue();
        assertThat(hasUriPermission(selectedUri)).isFalse();
    }

    @Test
    public void testOnSessionError_sessionIsNull() throws Exception {
        addMediaAndLaunchActivity(1);

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        // 2. Set a new count down latch for 'onSessionError' client invocation
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForSessionErrorClientInvocation(countDownLatch);

        // 3. Kill the PhotoPicker process
        sDevice.executeShellCommand("am force-stop " + getExplicitPackageName());

        // 4. Assert that 'onSessionError' is invoked and the session is null
        assertThat(countDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(mActivity.getSession()).isNull();
    }

    @Test
    public void testClose_surfacePackageReleased() throws Exception {
        addMediaAndLaunchActivity(1);

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();
        assertThat(getMediaItem(sDevice).exists()).isTrue();

        // 2. Close the session
        mActivity.getSession().close();

        // 3. Assert the embedded ui (surface package) is released
        assertThat(getMediaItem(sDevice).exists()).isFalse();
    }

    @Test
    public void testIsPhotoPickerExpanded_expandedStateTrue_photosTabVisible() throws Exception {
        launchTestActivity();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        if (mIsVisibleBackgroundUser) {
            // 2. Assert that "Photos" tab is hidden when expanded state is set to false
            BySelector photosTabSelector = getUiObjectMatchingTextSelector(PHOTOS_TAB_LABEL,
                    mDisplayId);
            assertThat(sDevice.hasObject(photosTabSelector)).isFalse();

            // 3. Set the expanded state to true and assert that the "Photos" tab is visible
            mActivity.getSession().notifyPhotoPickerExpanded(true);
            assertPhotosTabExists();
            UiObject2 photosTab = findObject(sDevice, photosTabSelector);
            assertThat(photosTab.getText()).isEqualTo(PHOTOS_TAB_LABEL);
        } else {
            // 2. Assert that "Photos" tab is hidden when expanded state is set to false
            UiObject photosTab = getUiObjectMatchingText(PHOTOS_TAB_LABEL, sDevice);
            assertThat(photosTab.exists()).isFalse();

            // 3. Set the expanded state to true and assert that the "Photos" tab is visible
            mActivity.getSession().notifyPhotoPickerExpanded(true);
            assertPhotosTabExists();
            assertThat(photosTab.getText()).isEqualTo(PHOTOS_TAB_LABEL);
        }
    }

    @Test
    public void testNotifyResized_surfacePackageResized() throws Exception {
        launchTestActivity();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        // 2. Set the expanded state to true so that navigation bar is visible and the view that
        // constitutes the surface package can be extracted
        mActivity.getSession().notifyPhotoPickerExpanded(true);
        assertPhotosTabExists();
        UiObject2 surfacePackage =
                sDevice.findObject(getUiObjectMatchingTextSelector(PHOTOS_TAB_LABEL, mDisplayId))
                        .getParent()
                        .getParent()
                        .getParent();
        assertThat(surfacePackage).isNotNull();
        int oldWidth = surfacePackage.getVisibleBounds().width();
        int oldHeight = surfacePackage.getVisibleBounds().height();

        // 3. Notify resize
        final SurfaceView surfaceView = mActivity.getSurfaceView();
        int expectedWidth = (int) (surfaceView.getWidth() * 0.9);
        int expectedHeight = (int) (surfaceView.getHeight() * 0.9);
        mActivity.getSession().notifyResized(expectedWidth, expectedHeight);

        // 4. Get the new surface package and its dimensions
        assertPhotosTabExists();
        UiObject2 newSurfacePackage =
                sDevice.findObject(getUiObjectMatchingTextSelector(PHOTOS_TAB_LABEL, mDisplayId))
                        .getParent()
                        .getParent()
                        .getParent();
        assertThat(newSurfacePackage).isNotNull();
        int newWidth = newSurfacePackage.getVisibleBounds().width();
        int newHeight = newSurfacePackage.getVisibleBounds().height();
        assertThat(newWidth).isNotEqualTo(oldWidth);
        assertThat(newWidth).isEqualTo(expectedWidth);
        assertThat(newHeight).isNotEqualTo(oldHeight);
        assertThat(newHeight).isEqualTo(expectedHeight);
    }

    @Test
    public void testClose_onSelectionComplete_onClickDoneButton() throws Exception {
        addMediaAndLaunchActivity(1);

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        // 2. For the 'Done' button to show up, notify picker expanded and click a media item
        mActivity.getSession().notifyPhotoPickerExpanded(true);
        assertPhotosTabExists();
        if (mIsVisibleBackgroundUser) {
            clickAndWait(sDevice, findObject(sDevice, getMediaItemSelector(mDisplayId)));
        } else {
            clickAndWait(sDevice, getMediaItem(sDevice));
        }


        // 3. Set a new count down latch for 'onSelectionComplete' client invocation
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForSelectionCompleteClientInvocation(countDownLatch);

        // 4. Click the 'Done' button to trigger 'onSelectionComplete'
        if (mIsVisibleBackgroundUser) {
            final BySelector doneButtonSelector = getUiObjectMatchingTextSelector(DONE_BUTTON_LABEL,
                    mDisplayId);
            final UiObject2 doneButton = findObject(sDevice, doneButtonSelector);
            clickAndWait(sDevice, doneButton);
        } else {
            final UiObject doneButton = getUiObjectMatchingText(DONE_BUTTON_LABEL, sDevice);
            clickAndWait(sDevice, doneButton);
        }


        // 5. Assert that 'onSelectionComplete' is invoked and the surface package is released
        assertThat(countDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(getMediaItem(sDevice).exists()).isFalse();
    }

    @Test
    public void testRevokeUriAccess_onRequestRevokeUriPermission() throws Exception {
        addMediaAndLaunchActivity(1);
        final List<Uri> selectedUris = mActivity.getSelectedUris();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();
        assertThat(selectedUris.size()).isEqualTo(0);

        // 2. Get media item, perform click and wait for 'onItemsSelected' invocation
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForItemsSelectedClientInvocation(countDownLatch);

        if (mIsVisibleBackgroundUser) {
            clickAndWait(sDevice, findObject(sDevice, getMediaItemSelector(mDisplayId)));
        } else {
            clickAndWait(sDevice, getMediaItem(sDevice));
        }

        assertThat(countDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(selectedUris.size()).isEqualTo(1);
        Uri selectedUri = selectedUris.get(0);

        // 3. Set a new count down latch for 'onItemsDeselected' client invocation
        countDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForItemsDeselectedClientInvocation(countDownLatch);

        // 4. Notify items deselected for the previously selected media item
        mActivity.getSession().requestRevokeUriPermission(selectedUris);

        // 5. Assert that the item is deselected.
        assertThat(countDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(selectedUris.isEmpty()).isTrue();
        assertThat(hasUriPermission(selectedUri)).isFalse();
    }

    private void addMediaAndLaunchActivity(int itemCount) throws Exception {
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));
        launchTestActivity();
    }

    private void launchEmbeddedSession() throws Exception {
        assertUiObjectExistsWithId(LAUNCH_EMBEDDED_BUTTON_ID, sDevice);
        assertUiObjectExistsWithId(EMBEDDED_SURFACE_ID, sDevice);
        mSessionCountDownLatch = new CountDownLatch(1);
        mActivity.setCountDownLatchForSessionOpenedClientInvocation(mSessionCountDownLatch);
        if (mIsVisibleBackgroundUser) {
            findAndClickUiObjectWithId(LAUNCH_EMBEDDED_BUTTON_ID, sDevice, mDisplayId);
        } else {
            findAndClickUiObjectWithId(LAUNCH_EMBEDDED_BUTTON_ID, sDevice);
        }
        assertThat(mSessionCountDownLatch.await(1L, TimeUnit.SECONDS)).isTrue();
    }

    private void launchTestActivity() {
        final Intent intent = new Intent(mContext, EmbeddedTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        mActivity = (EmbeddedTestActivity) sInstrumentation.startActivitySync(intent);
        // Wait for the UI Thread to become idle.
        sInstrumentation.waitForIdleSync();
        sDevice.waitForIdle();
    }

    /**
     * Get an explicit package name that limit the component {@link #mIntent} intent will
     * resolve to.
     */
    private String getExplicitPackageName() {
        // Use {@link PackageManager.MATCH_SYSTEM_ONLY} flag to match services only
        // by system apps.
        List<ResolveInfo> services = mActivity.getApplicationContext().getPackageManager()
                .queryIntentServices(mIntent, PackageManager.MATCH_SYSTEM_ONLY);
        // There should only be one matching service.
        if (services == null || services.isEmpty()) {
            Assert.fail("Failed to find embedded photopicker service!");
        } else if (services.size() != 1) {
            Assert.fail(String.format(
                    "Found more than 1 (%d) service by intent %s!",
                    services.size(), ACTION_EMBEDDED_PHOTOPICKER_SERVICE));
        }

        // Check that the service info contains package name.
        ServiceInfo embeddedService = services.get(0).serviceInfo;
        if (embeddedService != null && embeddedService.packageName != null) {
            return embeddedService.packageName;
        } else {
            Assert.fail("Failed to get valid service info or package info!");
        }
        return null;
    }

    private boolean hasUriPermission(Uri uri) {
        int res = mActivity.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return res == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Asserts that the "Photos" tab exists in the UI and is visible within a timeout of 1 second.
     * This method relies on finding a UI element that matches the text specified by
     * {@link #PHOTOS_TAB_LABEL}.
     *
     * @throws AssertionError If the "Photos" tab is not found within the specified timeout.
     */
    private void assertPhotosTabExists() {
        if (mIsVisibleBackgroundUser) {
            BySelector photosTab = getUiObjectMatchingTextSelector(PHOTOS_TAB_LABEL, mDisplayId);
            assertThat(sDevice.wait(Until.hasObject(photosTab), 1000)).isTrue();
        } else {
            UiObject photosTab = getUiObjectMatchingText(PHOTOS_TAB_LABEL, sDevice);
            assertThat(photosTab.waitForExists(1000)).isTrue();
        }
    }
}
