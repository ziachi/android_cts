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

package android.photopicker.cts;

import static android.photopicker.cts.util.PhotoPickerComponentUtils.GET_CONTENT_ACTIVITY_COMPONENT;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImageWithUnknownMimeType;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createMj2VideosAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createMpegVideo;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createSvgImage;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideoWithUnknownMimeType;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideosAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.clearPackageData;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.getDocumentsUiPackageName;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findObject;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddOrSelectButton;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertContainsMimeType;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertExtension;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertMimeType;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertPersistedGrant;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.provider.MediaStore.ACTION_PICK_IMAGES;
import static android.view.KeyEvent.KEYCODE_BACK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ClipData;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.photopicker.cts.util.PhotoPickerComponentUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.MediaStore;
import android.system.Os;
import android.system.OsConstants;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.providers.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Photo Picker Device only tests for common flows.
 */
@RunWith(Parameterized.class)
public class PhotoPickerTest extends PhotoPickerBaseTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Parameter(0)
    public String mAction;

    @Parameters(name = "intent={0}")
    public static Iterable<? extends Object> data() {
        return getTestParameters();
    }

    private List<Uri> mUriList = new ArrayList<>();

    private static int sGetContentTakeOverActivityAliasState;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        sGetContentTakeOverActivityAliasState = PhotoPickerComponentUtils
                .enableAndGetOldState(GET_CONTENT_ACTIVITY_COMPONENT);
        clearPackageData(getDocumentsUiPackageName());
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();

        if (mActivity != null) {
            mActivity.finish();
        }

        if (!super.isModernPickerEnabled()) {
            PhotoPickerComponentUtils.setState(
                    GET_CONTENT_ACTIVITY_COMPONENT, sGetContentTakeOverActivityAliasState);
        }
    }

    @Test
    public void testSingleSelect() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            final UiObject2 item = findItemList(sDevice, itemCount, getMainDisplayId()).get(0);
            clickAndWait(sDevice, item);
        } else {
            final UiObject item = findItemList(itemCount).get(0);
            clickAndWait(sDevice, item);
        }

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(mAction, uri, mContext.getUserId());
        assertPersistedGrant(uri, mContext.getContentResolver());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenFile() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            final UiObject2 item = findItemList(sDevice, itemCount, getMainDisplayId()).get(0);
            clickAndWait(sDevice, item);
        } else {
            final UiObject item = findItemList(itemCount).get(0);
            clickAndWait(sDevice, item);
        }

        final Uri uri = mActivity.getResult().data.getData();
        final CancellationSignal cg = new CancellationSignal();

        try (ParcelFileDescriptor pfd1 = mContext.getContentResolver()
                .openFileDescriptor(uri, "r", cg)) {
            try (ParcelFileDescriptor pfd2 = MediaStore
                    .openFileDescriptor(mContext.getContentResolver(), uri, "r", cg)) {

                long end1 = Os.lseek(pfd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(pfd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenAssetFile() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            final UiObject2 item = findItemList(sDevice, itemCount, getMainDisplayId()).get(0);
            clickAndWait(sDevice, item);
        } else {
            final UiObject item = findItemList(itemCount).get(0);
            clickAndWait(sDevice, item);
        }

        final Uri uri = mActivity.getResult().data.getData();
        final CancellationSignal cg = new CancellationSignal();

        try (AssetFileDescriptor afd1 = mContext.getContentResolver()
                .openAssetFileDescriptor(uri, "r", cg)) {
            try (AssetFileDescriptor afd2 = MediaStore
                    .openAssetFileDescriptor(mContext.getContentResolver(), uri, "r", cg)) {
                long end1 = Os.lseek(afd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(afd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenTypedAssetFile() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            final UiObject2 item = findItemList(sDevice, itemCount, getMainDisplayId()).get(0);
            clickAndWait(sDevice, item);
        } else {
            final UiObject item = findItemList(itemCount).get(0);
            clickAndWait(sDevice, item);
        }

        final Uri uri = mActivity.getResult().data.getData();
        final CancellationSignal cg = new CancellationSignal();

        try (AssetFileDescriptor afd1 = mContext.getContentResolver()
                .openTypedAssetFileDescriptor(uri, "*/*", null, cg)) {
            try (AssetFileDescriptor afd2 = MediaStore.openTypedAssetFileDescriptor(
                    mContext.getContentResolver(), uri, "*/*", null, cg)) {
                long end1 = Os.lseek(afd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(afd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    public void testSingleSelectForFavoritesAlbum() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId(),
                /* isFavorite */ true));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            int displayId = getMainDisplayId();
            UiObject2 albumsTab = findObject(sDevice, By.text("Albums").displayId(displayId));
            clickAndWait(sDevice, albumsTab);
            final UiObject2 album = findItemList(sDevice, 1, displayId).get(0);
            clickAndWait(sDevice, album);

            final UiObject2 item = findItemList(sDevice, itemCount, displayId).get(0);
            clickAndWait(sDevice, item);
        } else {
            UiObject albumsTab = sDevice.findObject(new UiSelector().text(
                    "Albums"));
            clickAndWait(sDevice, albumsTab);
            final UiObject album = findItemList(1).get(0);
            clickAndWait(sDevice, album);

            final UiObject item = findItemList(itemCount).get(0);
            clickAndWait(sDevice, item);
        }

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(mAction, uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testLaunchPreviewMultipleForVideoAlbum() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createVideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            int displayId = getMainDisplayId();
            UiObject2 albumsTab = findObject(sDevice, By.text("Albums").displayId(displayId));
            clickAndWait(sDevice, albumsTab);
            final UiObject2 album = findItemList(sDevice, 1, displayId).get(0);
            clickAndWait(sDevice, album);

            final List<UiObject2> itemList = findItemList(sDevice, videoCount, displayId);
            final int itemCount = itemList.size();

            assertThat(itemCount).isEqualTo(videoCount);

            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            UiObject2 viewSelectedButton = findObject(sDevice,
                    getViewSelectedButtonSelector(getMainDisplayId()));
            clickAndWait(sDevice, viewSelectedButton);
        } else {
            UiObject albumsTab = sDevice.findObject(new UiSelector().text(
                    "Albums"));
            clickAndWait(sDevice, albumsTab);
            final UiObject album = findItemList(1).get(0);
            clickAndWait(sDevice, album);

            final List<UiObject> itemList = findItemList(videoCount);
            final int itemCount = itemList.size();

            assertThat(itemCount).isEqualTo(videoCount);

            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findViewSelectedButton());
        }

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);
    }

    @Test
    public void testSingleSelectWithPreview() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            final UiObject2 item = findItemList(sDevice, itemCount, getMainDisplayId()).get(0);
            item.longClick();
            sDevice.waitForIdle();

            final UiObject2 addButton = findPreviewAddOrSelectButton(sDevice, getMainDisplayId());
            assertThat(addButton).isNotNull();
            clickAndWait(sDevice, addButton);
        } else {
            final UiObject item = findItemList(itemCount).get(0);
            item.longClick();
            sDevice.waitForIdle();

            final UiObject addButton = findPreviewAddOrSelectButton();
            assertThat(addButton.waitForExists(1000)).isTrue();
            clickAndWait(sDevice, addButton);
        }

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(mAction, uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect() throws Exception {
        final int imageCount = 4;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));
        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final int itemCount;
        if (isVisibleBackgroundUser()) {
            final List<UiObject2> itemList = findItemList(sDevice, imageCount, getMainDisplayId());
            itemCount = itemList.size();
            assertThat(itemCount).isEqualTo(imageCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton(sDevice, getMainDisplayId()));
        } else {
            final List<UiObject> itemList = findItemList(imageCount);
            itemCount = itemList.size();
            assertThat(itemCount).isEqualTo(imageCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton());
        }

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_longPress() throws Exception {
        final int videoCount = 3;
        mUriList.addAll(createMj2VideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            final List<UiObject2> itemList = findItemList(sDevice, videoCount, getMainDisplayId());
            final int itemCount = itemList.size();
            assertThat(itemCount).isEqualTo(videoCount);

            // Select one item from Photo grid
            clickAndWait(sDevice, itemList.get(0));

            // Preview the item
            UiObject2 item = itemList.get(1);
            item.longClick();
            sDevice.waitForIdle();

            final UiObject2 addOrSelectButton =
                    findPreviewAddOrSelectButton(sDevice, getMainDisplayId());
            assertWithMessage("Timed out waiting for AddOrSelectButton to appear")
                    .that(addOrSelectButton).isNotNull();

            // Select the item from Preview
            clickAndWait(sDevice, addOrSelectButton);

            // Instrumentation will ensure that back key event is delivered to the proper display
            // for visible background users.
            sInstrumentation.sendKeyDownUpSync(KEYCODE_BACK);

            // Select one more item from Photo grid
            clickAndWait(sDevice, findItemList(sDevice, videoCount, getMainDisplayId()).get(2));

            clickAndWait(sDevice, findAddButton(sDevice, getMainDisplayId()));
        } else {
            final List<UiObject> itemList = findItemList(videoCount);
            final int itemCount = itemList.size();
            assertThat(itemCount).isEqualTo(videoCount);

            // Select one item from Photo grid
            clickAndWait(sDevice, itemList.get(0));

            // Preview the item
            UiObject item = itemList.get(1);
            item.longClick();
            sDevice.waitForIdle();

            final UiObject addOrSelectButton = findPreviewAddOrSelectButton();
            assertWithMessage("Timed out waiting for AddOrSelectButton to appear")
                    .that(addOrSelectButton.waitForExists(1000)).isTrue();

            // Select the item from Preview
            clickAndWait(sDevice, addOrSelectButton);

            sDevice.pressBack();

            // Select one more item from Photo grid
            clickAndWait(sDevice, itemList.get(2));

            clickAndWait(sDevice, findAddButton());
        }

        // Verify that all 3 items are returned
        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(3);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_preview() throws Exception {
        final int imageCount = 4;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final int itemCount;
        if (isVisibleBackgroundUser()) {
            final List<UiObject2> itemList = findItemList(sDevice, imageCount, getMainDisplayId());
            itemCount = itemList.size();
            assertThat(itemCount).isEqualTo(imageCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            UiObject2 viewSelectedButton = findObject(sDevice,
                    getViewSelectedButtonSelector(getMainDisplayId()));
            clickAndWait(sDevice, viewSelectedButton);
        } else {
            final List<UiObject> itemList = findItemList(imageCount);
            itemCount = itemList.size();
            assertThat(itemCount).isEqualTo(imageCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findViewSelectedButton());
        }

        // Swipe left three times
        swipeLeftAndWait();
        swipeLeftAndWait();
        swipeLeftAndWait();

        if (isVisibleBackgroundUser()) {
            // Deselect one item
            UiObject2 previewSelectedCheckButton = findObject(sDevice,
                    getPreviewSelectedCheckButtonSelector(getMainDisplayId()));
            clickAndWait(sDevice, previewSelectedCheckButton);

            // Return selected items
            clickAndWait(sDevice, findPreviewAddButton(sDevice, getMainDisplayId()));
        } else {
            // Deselect one item
            clickAndWait(sDevice, findPreviewSelectedCheckButton());

            // Return selected items
            clickAndWait(sDevice, findPreviewAddButton());
        }

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount - 1);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonInitial() throws Exception {
        if (isVisibleBackgroundUser()) {
            verifyMultiSelect_previewVideoMuteButtonInitialForVisibleBackgroundUser();
            return;
        }

        launchPreviewMultipleWithVideos(/* videoCount */ 1);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Initial state of the mute Button
        // Check that initial state of mute button is mute, i.e., volume off
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Click Mute Button
        // Click to unmute the audio
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        // Check that mute button state is unmute, i.e., it shows `volume up` icon
        assertMuteButtonState(muteButton, /* isMuted */ false);
        // Click on the muteButton and check that mute button status is now 'mute'
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Click on the muteButton and check that mute button status is now unmute
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);

        // Test 3: Next preview resumes mute state
        // Go back and launch preview again
        sDevice.pressBack();
        clickAndWait(sDevice, findViewSelectedButton());

        waitForBinderCallsToComplete();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    private void verifyMultiSelect_previewVideoMuteButtonInitialForVisibleBackgroundUser()
            throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 1);

        final BySelector playPauseButton = getPlayPauseButtonSelector(getMainDisplayId());
        final BySelector muteButton = getMuteButtonSelector(getMainDisplayId());

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Initial state of the mute Button
        // Check that initial state of mute button is mute, i.e., volume off
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Click Mute Button
        // Click to unmute the audio
        UiObject2 previewMuteButton = findObject(sDevice, muteButton);
        clickAndWait(sDevice, previewMuteButton);

        waitForBinderCallsToComplete();

        // Check that mute button state is unmute, i.e., it shows `volume up` icon
        assertMuteButtonState(muteButton, /* isMuted */ false);
        // Click on the muteButton and check that mute button status is now 'mute'
        clickAndWait(sDevice, previewMuteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Click on the muteButton and check that mute button status is now unmute
        clickAndWait(sDevice, previewMuteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);

        // Test 3: Next preview resumes mute state
        // Go back and launch preview again
        // Instrumentation will ensure that back key event is delivered to the proper display
        // for visible background users.
        sInstrumentation.sendKeyDownUpSync(KEYCODE_BACK);
        UiObject2 viewSelectedButton = findObject(sDevice,
                getViewSelectedButtonSelector(getMainDisplayId()));
        clickAndWait(sDevice, viewSelectedButton);

        waitForBinderCallsToComplete();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonOnSwipe() throws Exception {
        if (isVisibleBackgroundUser()) {
            verifyMultiSelect_previewVideoMuteButtonOnSwipeForVisibleBackgroundUser();
            return;
        }

        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();
        final UiObject playerView = findPlayerView();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Swipe resumes mute state, with state of the button is 'volume off' / 'mute'
        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Swipe to next page and check that muteButton is in mute state.
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // set-up and wait for player controls to be sticky
        setUpAndAssertStickyPlayerControls(playerView, playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Swipe resumes mute state, with state of mute button 'volume up' / 'unmute'
        // Click muteButton again to check the next video resumes the previous video's mute state
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);
        // check that next video resumed previous video's mute state
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // Wait for 1s before checking Play/Pause button's visibility
        playPauseButton.waitForExists(1000);
        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    private void verifyMultiSelect_previewVideoMuteButtonOnSwipeForVisibleBackgroundUser()
            throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        final BySelector playerView = getPlayerViewSelector(getMainDisplayId());
        final BySelector playPauseButton = getPlayPauseButtonSelector(getMainDisplayId());
        final BySelector muteButton = getMuteButtonSelector(getMainDisplayId());

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Swipe resumes mute state, with state of the button is 'volume off' / 'mute'
        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Swipe to next page and check that muteButton is in mute state.
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // set-up and wait for player controls to be sticky
        setUpAndAssertStickyPlayerControls(playerView, playPauseButton, muteButton);

        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Swipe resumes mute state, with state of mute button 'volume up' / 'unmute'
        // Click muteButton again to check the next video resumes the previous video's mute state
        final UiObject2 previewMuteButton = findObject(sDevice, muteButton);
        clickAndWait(sDevice, previewMuteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);
        // check that next video resumed previous video's mute state
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // Wait for 1s before checking Play/Pause button's visibility
        sDevice.wait(Until.hasObject(playPauseButton), 1000);

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testVideoPreviewAudioFocus() throws Exception {
        final int[] focusStateForTest = new int[1];
        final AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        AudioFocusRequest audioFocusRequest =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.USAGE_MEDIA)
                        .setUsage(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build())
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        focusStateForTest[0] = focusChange;
                    }
                })
                .build();

        // Request AudioFocus
        assertWithMessage("Expected requestAudioFocus result")
                .that(audioManager.requestAudioFocus(audioFocusRequest))
                .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        // Launch Preview
        launchPreviewMultipleWithVideos(/* videoCount */ 2);
        // Video preview launches in mute mode, hence, test's audio focus shouldn't be lost when
        // video preview starts
        assertThat(focusStateForTest[0]).isEqualTo(0);

        if (isVisibleBackgroundUser()) {
            final BySelector muteButton = getMuteButtonSelector(getMainDisplayId());
            final UiObject2 previewMuteButton = findObject(sDevice, muteButton);
            // unmute the audio of video preview
            clickAndWait(sDevice, previewMuteButton);

            // Remote video preview involves binder calls
            // Wait for Binder calls to complete and device to be idle
            MediaStore.waitForIdle(mContext.getContentResolver());
            sDevice.waitForIdle();

            assertMuteButtonState(muteButton, /* isMuted */ false);
        } else {
            final UiObject muteButton = findMuteButton();
            // unmute the audio of video preview
            clickAndWait(sDevice, muteButton);

            // Remote video preview involves binder calls
            // Wait for Binder calls to complete and device to be idle
            MediaStore.waitForIdle(mContext.getContentResolver());
            sDevice.waitForIdle();

            assertMuteButtonState(muteButton, /* isMuted */ false);
        }

        // Verify that test lost the audio focus because PhotoPicker has requested audio focus now.
        assertThat(focusStateForTest[0]).isEqualTo(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        // Reset the focusStateForTest to verify test loses audio focus when video preview is
        // launched with unmute state
        focusStateForTest[0] = 0;
        // Abandon the audio focus before requesting again. This is necessary to reduce test flakes
        audioManager.abandonAudioFocusRequest(audioFocusRequest);
        // Request AudioFocus from test again
        assertWithMessage("Expected requestAudioFocus result")
                .that(audioManager.requestAudioFocus(audioFocusRequest))
                        .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        // Wait for PhotoPicker to lose Audio Focus
        findPlayButton().waitForExists(SHORT_TIMEOUT);
        // Test requesting audio focus will make PhotoPicker lose audio focus, Verify video is
        // paused when PhotoPicker loses audio focus.
        assertWithMessage("PlayPause button's content description")
                .that(findPlayPauseButton().getContentDescription())
                .isEqualTo("Play");

        // Swipe to next video and verify preview gains audio focus
        if (isVisibleBackgroundUser()) {
            findPlayButton(getMainDisplayId(), 1000).swipe(Direction.LEFT, 1.0f);
        } else {
            findPlayButton().swipeLeft(5);
        }
        findPauseButton().waitForExists(SHORT_TIMEOUT);
        // Video preview is now in unmute mode. Hence, PhotoPicker will request audio focus. Verify
        // that test lost the audio focus.
        assertThat(focusStateForTest[0]).isEqualTo(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    }

    // Note: This test works independent of the device accessibility state and does not test the
    // controls' auto-hide feature which requires the accessibility state to be disabled.
    @Test
    public void testMultiSelect_previewVideoControlsVisibility() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 2);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        final UiObject playerView = findPlayerView();
        // Click on StyledPlayerView to make the video controls visible
        // Don't click in the center else it may pause the video and hide the controls.
        playerView.clickBottomRight();
        sDevice.waitForIdle();
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Wait for 1s and check that controls are still visible
        assertPlayerControlsDontAutoHide(playPauseButton, muteButton);

        // Swipe left to the next video
        swipeLeftAndWait();

        // Click on the StyledPlayerView and check that controls appear
        // Don't click in the center else it may pause the video and hide the controls.
        playerView.clickBottomRight();
        sDevice.waitForIdle();
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createMj2VideosAndGetUris(videoCount, mContext.getUserId()));
        final int imageCount = 1;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        final String mimeType = "video/mj2";

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        intent.setType(mimeType);
        launchPhotoPickerForIntent(intent);

        // find all items
        final int itemCount;
        if (isVisibleBackgroundUser()) {
            final List<UiObject2> itemList = findItemList(sDevice, -1, getMainDisplayId());
            itemCount = itemList.size();
            assertThat(itemCount).isAtLeast(videoCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton(sDevice, getMainDisplayId()));
        } else {
            final List<UiObject> itemList = findItemList(-1);
            itemCount = itemList.size();
            assertThat(itemCount).isAtLeast(videoCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton());
        }

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    @Test
    public void testExtraMimeTypeFilter() throws Exception {
        final int mj2VideoCount = 2;
        // Creates 2 videos with mime type: "video/mj2"
        mUriList.addAll(createMj2VideosAndGetUris(mj2VideoCount, mContext.getUserId()));

        final int mp4VideoCount = 3;
        // Creates 3 videos with mime type: "video/mp4"
        mUriList.addAll(createVideosAndGetUris(mp4VideoCount, mContext.getUserId()));

        final int imageCount = 4;
        // Creates 4 images with mime type: "image/dng"
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);

        if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
            intent.setType("*/*");
        }
        final String[] mimeTypes = new String[]{"video/mj2", "image/dng"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        launchPhotoPickerForIntent(intent);

        final int totalCount = mj2VideoCount + imageCount;
        final int itemCount;
        if (isVisibleBackgroundUser()) {
            final List<UiObject2> itemList = findItemList(sDevice, totalCount, getMainDisplayId());
            itemCount = itemList.size();
            assertThat(itemCount).isAtLeast(totalCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton(sDevice, getMainDisplayId()));
        } else {
            final List<UiObject> itemList = findItemList(totalCount);
            itemCount = itemList.size();
            assertThat(itemCount).isAtLeast(totalCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton());
        }

        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Expected number of items returned to be: " + itemCount)
                .that(clipData.getItemCount()).isEqualTo(itemCount);
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertContainsMimeType(uri, mimeTypes);
        }
    }

    @Test
    public void testMimeTypeFilterPriority() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createMj2VideosAndGetUris(videoCount, mContext.getUserId()));
        final int imageCount = 1;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        // setType has lower priority than EXTRA_MIME_TYPES filters.
        intent.setType("image/*");
        final String mimeType = "video/mj2";
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {mimeType});
        launchPhotoPickerForIntent(intent);

        final int itemCount;
        if (isVisibleBackgroundUser()) {
            // find all items
            final List<UiObject2> itemList = findItemList(sDevice, -1, getMainDisplayId());
            itemCount = itemList.size();
            assertThat(itemCount).isAtLeast(videoCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton(sDevice, getMainDisplayId()));
        } else {
            // find all items
            final List<UiObject> itemList = findItemList(-1);
            itemCount = itemList.size();
            assertThat(itemCount).isAtLeast(videoCount);
            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findAddButton());
        }

        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Expected number of items returned to be: " + itemCount)
                .that(clipData.getItemCount()).isEqualTo(itemCount);
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    @Test
    public void testPickerUriFileExtensions() throws Exception {
        // 1. Create test media items
        mUriList.add(createSvgImage(mContext.getUserId()));
        mUriList.add(createImageWithUnknownMimeType(mContext.getUserId()));
        mUriList.add(createMpegVideo(mContext.getUserId()));
        mUriList.add(createVideoWithUnknownMimeType(mContext.getUserId()));

        final int expectedItemCount = mUriList.size();

        final Map<String, String> mimeTypeToExpectedExtensionMap = Map.of(
                "image/svg+xml", "svg",
                "image/foo", "jpg",
                "video/mpeg", "mpeg",
                "video/foo", "mp4"
        );

        // 2. Launch Picker in multi-select mode for the test mime types
        final Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        // 3. Add all items
        final int itemCount;
        if (isVisibleBackgroundUser()) {
            final List<UiObject2> itemList =
                    findItemList(sDevice, expectedItemCount, getMainDisplayId());
            itemCount = itemList.size();
            assertWithMessage("Unexpected number of media items found in the picker ui")
                    .that(itemCount)
                    .isEqualTo(expectedItemCount);

            for (UiObject2 item : itemList) {
                clickAndWait(sDevice, item);
            }
            clickAndWait(sDevice, findAddButton(sDevice, getMainDisplayId()));
        } else {
            final List<UiObject> itemList = findItemList(expectedItemCount);
            itemCount = itemList.size();
            assertWithMessage("Unexpected number of media items found in the picker ui")
                    .that(itemCount)
                    .isEqualTo(expectedItemCount);

            for (UiObject item : itemList) {
                clickAndWait(sDevice, item);
            }
            clickAndWait(sDevice, findAddButton());
        }

        // 4. Get the activity result data to extract the picker uris
        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Unexpected number of items returned from the picker activity")
                .that(clipData.getItemCount())
                .isEqualTo(itemCount);

        // 5. Assert the picker uri file extension as expected for each item
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertExtension(uri, mimeTypeToExpectedExtensionMap);
        }
    }

    private void assertMuteButtonState(UiObject muteButton, boolean isMuted)
            throws UiObjectNotFoundException {
        // We use content description to assert the state of the mute button, there is no other way
        // to test this.
        final String expectedContentDescription = isMuted ? "Unmute video" : "Mute video";
        final String assertMessage =
                "Expected mute button content description to be " + expectedContentDescription;
        assertWithMessage(assertMessage).that(muteButton.getContentDescription())
                .isEqualTo(expectedContentDescription);
    }

    private void assertMuteButtonState(BySelector muteButtonSelector, boolean isMuted) {
        // We use content description to assert the state of the mute button, there is no other way
        // to test this.
        final String expectedContentDescription = isMuted ? "Unmute video" : "Mute video";
        final String assertMessage =
                "Expected mute button content description to be " + expectedContentDescription;
        UiObject2 muteButton = findObject(sDevice, muteButtonSelector);
        assertWithMessage(assertMessage).that(muteButton.getContentDescription())
                .isEqualTo(expectedContentDescription);
    }

    private void launchPreviewMultipleWithVideos(int videoCount) throws  Exception {
        mUriList.addAll(createVideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        if (isVisibleBackgroundUser()) {
            final List<UiObject2> itemList = findItemList(sDevice, videoCount, getMainDisplayId());
            final int itemCount = itemList.size();

            assertThat(itemCount).isEqualTo(videoCount);

            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            UiObject2 viewSelectedButton = findObject(sDevice,
                    getViewSelectedButtonSelector(getMainDisplayId()));
            clickAndWait(sDevice, viewSelectedButton);
        } else {
            final List<UiObject> itemList = findItemList(videoCount);
            final int itemCount = itemList.size();

            assertThat(itemCount).isEqualTo(videoCount);

            for (int i = 0; i < itemCount; i++) {
                clickAndWait(sDevice, itemList.get(i));
            }

            clickAndWait(sDevice, findViewSelectedButton());
        }

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);

        waitForBinderCallsToComplete();
    }

    private void waitForBinderCallsToComplete() {
        // Wait for Binder calls to complete and device to be idle
        MediaStore.waitForIdle(mContext.getContentResolver());
        sDevice.waitForIdle();
    }

    private void setUpAndAssertStickyPlayerControls(UiObject playerView, UiObject playPauseButton,
            UiObject muteButton) throws Exception {
        // Wait for 1s for player view to exist
        playerView.waitForExists(1000);
        // Wait for 1s or Play/Pause button to hide
        playPauseButton.waitUntilGone(1000);
        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(sDevice, playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);
    }

    private void setUpAndAssertStickyPlayerControls(BySelector playerViewSelector,
            BySelector playPauseButtonSelector, BySelector muteButtonSelector) throws Exception {
        // Wait for 1s for player view to exist
        sDevice.wait(Until.hasObject(playerViewSelector), 1000);
        // Wait for 1s or Play/Pause button to hide
        sDevice.wait(Until.gone(playPauseButtonSelector), 1000);
        // Click on StyledPlayerView to make the video controls visible
        UiObject2 playerView = findObject(sDevice, playerViewSelector);
        clickAndWait(sDevice, playerView);
        assertPlayerControlsVisible(playPauseButtonSelector, muteButtonSelector);
    }

    private void assertPlayerControlsVisible(UiObject playPauseButton, UiObject muteButton) {
        assertVisible(playPauseButton, "Expected play/pause button to be visible");
        assertVisible(muteButton, "Expected mute button to be visible");
    }

    private void assertPlayerControlsVisible(
            BySelector playPauseButtonSelector, BySelector muteButtonSelctor) {
        assertVisible(playPauseButtonSelector, "Expected play/pause button to be visible");
        assertVisible(muteButtonSelctor, "Expected mute button to be visible");
    }

    private void assertPlayerControlsDontAutoHide(UiObject playPauseButton, UiObject muteButton) {
        assertWithMessage("Expected play/pause button to not auto hide in 1s")
                .that(playPauseButton.waitUntilGone(1100)).isFalse();
        assertVisible(muteButton, "Expected mute button to be still visible after 1s");
    }

    private void assertVisible(UiObject button, String message) {
        assertWithMessage(message).that(button.exists()).isTrue();
    }

    private void assertVisible(BySelector selector, String message) {
        assertWithMessage(message)
                .that(sDevice.wait(Until.hasObject(selector), SHORT_TIMEOUT)).isTrue();
    }

    private static UiObject findViewSelectedButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_view_selected"));
    }

    private static BySelector getViewSelectedButtonSelector(int displayId) {
        return By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/button_view_selected")).displayId(displayId);
    }

    private static UiObject findPreviewSelectedCheckButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_selected_check_button"));
    }

    private static BySelector getPreviewSelectedCheckButtonSelector(int displayId) {
        return By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/preview_selected_check_button")).displayId(displayId);
    }

    private static UiObject findPlayerView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_player_view"));
    }

    private static UiObject findViewPager() {
        return new UiObject(
                new UiSelector().resourceIdMatches(REGEX_PACKAGE_NAME + ":id/preview_viewPager"));
    }

    private static BySelector getPlayerViewSelector(int displayId) {
        return By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/preview_player_view")).displayId(displayId);
    }

    private static UiObject findMuteButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_mute"));
    }

    private static BySelector getMuteButtonSelector(int displayId) {
        return By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/preview_mute")).displayId(displayId);
    }

    private static UiObject findPlayPauseButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/exo_play_pause"));
    }

    private static BySelector getPlayPauseButtonSelector(int displayId) {
        return By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/exo_play_pause")).displayId(displayId);
    }

    private static UiObject findPauseButton() {
        return new UiObject(new UiSelector().descriptionContains("Pause"));
    }

    private static UiObject2 findPauseButton(int displayId, long timeout) {
        return sDevice.wait(Until.findObject(
                By.displayId(displayId).descContains("Pause")), timeout);
    }

    private static UiObject findPlayButton() {
        return new UiObject(new UiSelector().descriptionContains("Play"));
    }

    private static UiObject2 findPlayButton(int displayId, long timeout) {
        return sDevice.wait(Until.findObject(
                By.displayId(displayId).descContains("Play")), timeout);
    }

    private static UiObject findPreviewVideoImageView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_video_image"));
    }

    private void swipeLeftAndWait() throws UiObjectNotFoundException {
        final int width = findViewPager().getVisibleBounds().width();
        final int height = findViewPager().getVisibleBounds().height();
        final int centerX = findViewPager().getVisibleBounds().centerX();
        final int centerY = findViewPager().getVisibleBounds().centerY();
        sDevice.swipe(centerX - (width / 3), centerY, centerX + (width / 3), centerY, 10);
        sDevice.waitForIdle();
    }

    private static List<String> getTestParameters() {
        return Arrays.asList(
                ACTION_PICK_IMAGES,
                Intent.ACTION_GET_CONTENT
        );
    }

    private void addMultipleSelectionFlag(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_PICK_IMAGES:
                intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,
                        MediaStore.getPickImagesMaxLimit());
                break;
            case Intent.ACTION_GET_CONTENT:
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                break;
            default:
                // do nothing
        }
    }

    private void launchPhotoPickerForIntent(Intent intent) throws Exception {
        // GET_CONTENT needs to have setType
        if (Intent.ACTION_GET_CONTENT.equals(intent.getAction()) && intent.getType() == null) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        }

        mActivity.startActivityForResult(intent, REQUEST_CODE);
    }
}
