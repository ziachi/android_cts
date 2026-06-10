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

package android.photopicker.cts;

import static android.photopicker.cts.util.PhotoPickerComponentUtils.GET_CONTENT_ACTIVITY_COMPONENT;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUriAndPath;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.clearPackageData;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.getDocumentsUiPackageName;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAndClickBrowse;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findObject;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertReadOnlyAccess;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.photopicker.cts.util.PhotoPickerComponentUtils;
import android.photopicker.cts.util.UiAssertionUtils;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Photo Picker tests for PhotoPicker launched via {@link Intent#ACTION_GET_CONTENT} intent
 * exclusively.
 */
public class ActionGetContentOnlyTest extends PhotoPickerBaseTest {

    public static final String TAG = "ActionGetContentOnlyTest";

    private static String sDocumentsUiPackageName;
    private static int sGetContentTakeOverActivityAliasState;

    private List<Uri> mUriList = new ArrayList<>();

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

    @Before
    public void setUp() throws Exception {
        super.setUp();

        sDocumentsUiPackageName = getDocumentsUiPackageName();
        sGetContentTakeOverActivityAliasState = PhotoPickerComponentUtils
                .enableAndGetOldState(GET_CONTENT_ACTIVITY_COMPONENT);
        clearPackageData(sDocumentsUiPackageName);
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        // TODO(b/374851711): Re-enable these tests once b/374851711 is fixed.
        assumeFalse("DocumentsUi does not support visible background users",
                isVisibleBackgroundUser());
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        // Should open documentsUi
        assertThatShowsDocumentsUiButtons();

        // We don't test the result of the picker here because the intention of the test is only to
        // test that DocumentsUi is opened.
    }

    @Test
    public void testExtraMimeTypeFilter() throws Exception {
        // TODO(b/374851711): Re-enable these tests once b/374851711 is fixed.
        assumeFalse("DocumentsUi does not support visible background users",
                isVisibleBackgroundUser());
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*"});
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        // Should open documentsUi
        assertThatShowsDocumentsUiButtons();

        // We don't test the result of the picker here because the intention of the test is only to
        // test that DocumentsUi is opened.
    }

    @Test
    public void testBrowse_singleSelect() throws Exception {
        // TODO(b/374851711): Re-enable these tests once b/374851711 is fixed.
        assumeFalse("DocumentsUi does not support visible background users",
                isVisibleBackgroundUser());
        final int itemCount = 1;
        List<Pair<Uri, String>> createdImagesData = createImagesAndGetUriAndPath(itemCount,
                mContext.getUserId(), /* isFavorite */ false);

        final List<String> fileNameList = new ArrayList<>();
        for (Pair<Uri, String> createdImageData: createdImagesData) {
            mUriList.add(createdImageData.first);
            fileNameList.add(createdImageData.second);
        }

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        if (isVisibleBackgroundUser()) {
            findAndClickBrowse(sDevice, getMainDisplayId());
        } else {
            findAndClickBrowse(sDevice);
        }

        findAndClickFilesInDocumentsUi(fileNameList);

        final Uri uri = mActivity.getResult().data.getData();

        assertReadOnlyAccess(uri, mContext.getContentResolver());
    }

    @Test
    public void testBrowse_multiSelect() throws Exception {
        // TODO(b/374851711): Re-enable these tests once b/374851711 is fixed.
        assumeFalse("DocumentsUi does not support visible background users",
                isVisibleBackgroundUser());
        final int itemCount = 3;
        List<Pair<Uri, String>> createdImagesData = createImagesAndGetUriAndPath(itemCount,
                mContext.getUserId(), /* isFavorite */ false);

        final List<String> fileNameList = new ArrayList<>();
        for (Pair<Uri, String> createdImageData: createdImagesData) {
            mUriList.add(createdImageData.first);
            fileNameList.add(createdImageData.second);
        }

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        if (isVisibleBackgroundUser()) {
            findAndClickBrowse(sDevice, getMainDisplayId());
        } else {
            findAndClickBrowse(sDevice);
        }

        findAndClickFilesInDocumentsUi(fileNameList);

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            assertReadOnlyAccess(clipData.getItemAt(i).getUri(), mContext.getContentResolver());
        }
    }

    @Test
    public void testChooserIntent_mediaFilter() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        mActivity.startActivityForResult(Intent.createChooser(intent, TAG), REQUEST_CODE);

        // Should open Picker
        UiAssertionUtils.assertThatShowsPickerUi(intent.getType());
    }

    @Test
    public void testChooserIntent_nonMediaFilter() throws Exception {
        // TODO(b/374851711): Re-enable these tests once b/374851711 is fixed.
        assumeFalse("DocumentsUi does not support visible background users",
                isVisibleBackgroundUser());
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Should open DocumentsUi
        assertThatShowsDocumentsUiButtons();
    }

    @Test
    public void testPickerAccentColorWithGetContent() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_ACCENT_COLOR, 0xFFFF5A5F);

        mActivity.startActivityForResult(Intent.createChooser(intent, TAG), REQUEST_CODE);
        sDevice.waitForIdle();

        // Should open the picker
        UiAssertionUtils.assertThatShowsPickerUi(intent.getType());
    }

    private void assertThatShowsDocumentsUiButtons() {
        // Assert that "Recent files" header for DocumentsUi shows
        // Add a short timeout wait for DocumentsUi to show
        if (isVisibleBackgroundUser()) {
            final BySelector selector = By.res(Pattern.compile(
                    sDocumentsUiPackageName + ":id/header_title")).displayId(getMainDisplayId());
            assertThat(sDevice.wait(Until.hasObject(selector), SHORT_TIMEOUT)).isTrue();
        } else {
            assertThat(new UiObject(new UiSelector().resourceId(sDocumentsUiPackageName
                    + ":id/header_title")).waitForExists(SHORT_TIMEOUT)).isTrue();
        }
    }

    private UiObject findSaveButton() {
        return new UiObject(new UiSelector().resourceId(
                        sDocumentsUiPackageName + ":id/container_save")
                .childSelector(new UiSelector().resourceId("android:id/button1")));
    }

    private UiObject2 findSaveButton(int displayId) {
        final BySelector containerSelector = By.res(Pattern.compile(
                sDocumentsUiPackageName + ":id/container_save")).displayId(displayId);
        final BySelector buttonSelector = containerSelector.hasChild(By.res("android:id/button1"));
        return sDevice.findObject(buttonSelector);
    }

    private void findAndClickFilesInDocumentsUi(List<String> fileNameList) throws Exception {
        if (isVisibleBackgroundUser()) {
            final int displayId = getMainDisplayId();
            final BySelector docList = getDirectoryListSelector(displayId);
            for (String fileName : fileNameList) {
                findAndClickFileInDocumentsUi(docList, fileName, displayId);
            }
        } else {
            final UiSelector docList = getDirectoryListSelector();
            for (String fileName : fileNameList) {
                findAndClickFileInDocumentsUi(docList, fileName);
            }
        }
        findAndClickSelect();
    }

    private void findAndClickSelect() throws Exception {
        if (isVisibleBackgroundUser()) {
            final int displayId = getMainDisplayId();
            final BySelector buttonSelector = By.res(Pattern.compile(
                    sDocumentsUiPackageName + ":id/action_menu_select")).displayId(displayId);
            final UiObject2 selectButton = findObject(sDevice, buttonSelector);
            clickAndWait(sDevice, selectButton);
        } else {
            final UiObject selectButton = new UiObject(new UiSelector().resourceId(
                    sDocumentsUiPackageName + ":id/action_menu_select"));
            clickAndWait(sDevice, selectButton);
        }

    }

    private UiSelector getDirectoryListSelector() throws Exception {
        final UiSelector docList = new UiSelector().resourceId(sDocumentsUiPackageName
                + ":id/dir_list");

        // Wait for the first list item to appear
        assertWithMessage("First list item").that(
                new UiObject(docList.childSelector(new UiSelector()))
                        .waitForExists(SHORT_TIMEOUT)).isTrue();

        try {
            // Enforce to set the list mode
            // Because UiScrollable can't reach the real bottom (when WEB_LINKABLE_FILE item)
            // in grid mode when screen landscape mode
            clickAndWait(sDevice, new UiObject(new UiSelector().resourceId(sDocumentsUiPackageName
                    + ":id/sub_menu_list")));
        } catch (UiObjectNotFoundException ignored) {
            // Do nothing, already be in list mode.
        }
        return docList;
    }

    private BySelector getDirectoryListSelector(int displayId) throws Exception {
        final BySelector docList = By.res(Pattern.compile(sDocumentsUiPackageName
                + ":id/dir_list")).displayId(displayId);

        // Wait for the first list item to appear
        assertWithMessage("First list item")
                .that(sDevice.wait(Until.hasObject(docList.hasChild(By.depth(1))), SHORT_TIMEOUT))
                .isTrue();

        // Enforce to set the list mode
        // Because UiScrollable can't reach the real bottom (when WEB_LINKABLE_FILE item)
        // in grid mode when screen landscape mode
        final BySelector subMenuSelector = By.res(Pattern.compile(sDocumentsUiPackageName
                + ":id/sub_menu_list")).displayId(displayId);
        final UiObject2 subMenu = findObject(sDevice, subMenuSelector);
        clickAndWait(sDevice, subMenu);
        return docList;
    }

    private void findAndClickFileInDocumentsUi(UiSelector docList, String fileName)
            throws Exception {

        // Repeat swipe gesture to find our item
        // (UiScrollable#scrollIntoView does not seem to work well with SwipeRefreshLayout)
        UiObject targetObject = new UiObject(docList.childSelector(new UiSelector()
                .textContains(fileName)));
        UiObject saveButton = findSaveButton();
        int stepLimit = 10;
        while (stepLimit-- > 0) {
            if (targetObject.exists()) {
                boolean targetObjectFullyVisible = !saveButton.exists()
                        || targetObject.getVisibleBounds().bottom
                        <= saveButton.getVisibleBounds().top;
                if (targetObjectFullyVisible) {
                    break;
                }
            }

            sDevice.swipe(/* startX= */ sDevice.getDisplayWidth() / 2,
                    /* startY= */ sDevice.getDisplayHeight() / 2,
                    /* endX= */ sDevice.getDisplayWidth() / 2,
                    /* endY= */ 0,
                    /* steps= */ 40);
        }

        targetObject.longClick();
    }

    private void findAndClickFileInDocumentsUi(BySelector docList, String fileName, int displayId)
            throws Exception {
        // Repeat swipe gesture to find our item
        final BySelector targetSelect = By.textContains(fileName).displayId(displayId);
        UiObject2 targetObject = findObject(sDevice, targetSelect);
        UiObject2 saveButton = findSaveButton(displayId);
        int stepLimit = 10;
        while (stepLimit-- > 0) {
            boolean targetObjectFullyVisible = (saveButton == null)
                    || targetObject.getVisibleBounds().bottom
                    <= saveButton.getVisibleBounds().top;
            if (targetObjectFullyVisible) {
                break;
            }
            sDevice.swipe(/* startX= */ sDevice.getDisplayWidth() / 2,
                    /* startY= */ sDevice.getDisplayHeight() / 2,
                    /* endX= */ sDevice.getDisplayWidth() / 2,
                    /* endY= */ 0,
                    /* steps= */ 40);
            targetObject = findObject(sDevice, targetSelect);
            saveButton = findSaveButton(displayId);
        }
        targetObject.longClick();
    }
}
