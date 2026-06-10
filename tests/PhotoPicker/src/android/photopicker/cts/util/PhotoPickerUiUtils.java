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

package android.photopicker.cts.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Photo Picker Utility methods for finding UI elements.
 */
public class PhotoPickerUiUtils {
    public static final long SHORT_TIMEOUT = 5 * DateUtils.SECOND_IN_MILLIS;

    private static final long TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;

    public static final String REGEX_PACKAGE_NAME =
            "com(.google)?.android.providers.media(.module)?";
    // Matches all possible content description, while avoiding to match with content description of
    // other ui objects like the "Photos" tab
    private static final String REGEX_MEDIA_ITEM_CONTENT_DESCRIPTION =
            "^(Media|Photo|Video|GIF|Motion)[^s].*";

    /**
     * Gets the list of items from the photo grid list.
     *
     * @param itemCount if the itemCount is -1, return all matching items. Otherwise, return the
     *                  item list that its size is not greater than the itemCount.
     */
    public static List<UiObject> findItemList(int itemCount) throws Exception {
        final List<UiObject> itemList = new ArrayList<>();
        final UiSelector gridList = new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/picker_tab_recyclerview");

        // Wait for the first item to appear
        assertWithMessage("Timed out while waiting for first item to appear")
                .that(new UiObject(gridList.childSelector(new UiSelector())).waitForExists(TIMEOUT))
                .isTrue();

        final UiSelector itemSelector = new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/icon_thumbnail");
        final UiScrollable grid = new UiScrollable(gridList);
        final int childCount = grid.getChildCount();
        final int count = itemCount == -1 ? childCount : itemCount;

        for (int i = 0; i < childCount; i++) {
            final UiObject item = grid.getChildByInstance(itemSelector, i);
            if (item.exists()) {
                itemList.add(item);
            }
            if (itemList.size() == count) {
                break;
            }
        }
        return itemList;
    }

    /**
     * Gets the list of items from the photo grid list.
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @param itemCount if the itemCount is -1, return all matching items. Otherwise, return the
     *                  item list that its size is not greater than the itemCount.
     * @param displayId The id of the target display.
     * @return a list of {@link UiObject2} representing the found items
     * @throws Exception if an error occurs while waiting for the first item to appear or
     * retrieving the items
     */
    public static List<UiObject2> findItemList(UiDevice uiDevice, int itemCount, int displayId)
            throws Exception {
        final List<UiObject2> itemList = new ArrayList<>();
        final BySelector gridList = By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/picker_tab_recyclerview")).displayId(displayId);

        // Wait for the first item to appear
        assertWithMessage("Timed out while waiting for first item to appear")
                .that(uiDevice.wait(Until.hasObject(gridList.hasChild(By.depth(1))), TIMEOUT))
                .isTrue();

        final BySelector itemSelector = By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/icon_thumbnail")).displayId(displayId);

        List<UiObject2> items = uiDevice.findObjects(itemSelector);
        final int childCount = items.size();
        final int count = itemCount == -1 ? childCount : itemCount;

        for (int i = 0; i < childCount; i++) {
            final UiObject2 item = items.get(i);
            itemList.add(item);
            if (itemList.size() == count) {
                break;
            }
        }
        return itemList;
    }

    /** Find a media item to perform click events */
    public static UiObject getMediaItem(UiDevice device) throws Exception {
        UiSelector mediaItemSelector =
                new UiSelector().descriptionMatches(REGEX_MEDIA_ITEM_CONTENT_DESCRIPTION);
        return device.findObject(mediaItemSelector);
    }

    /**
     * Retrieves the BySelector representing media item.
     *
     * @param displayId The id of the target display.
     * @return a BySelector that can be used to find media items with the specified
     * content description
     */
    public static BySelector getMediaItemSelector(int displayId) {
        return By.desc(Pattern.compile(REGEX_MEDIA_ITEM_CONTENT_DESCRIPTION)).displayId(displayId);
    }

    public static UiObject findPreviewAddButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_add_button"));
    }

    /**
     * Retrieves the UI object representing the preview add button.
     *
     * This method first verifies that the preview add button exists
     * on the target display.If present, it returns a {@link UiObject2} that can be used to
     * interact with the preview add button.
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @param displayId The id of the target display.
     * @return the {@link UiObject2} representing the found preview add button.
     */
    public static UiObject2 findPreviewAddButton(UiDevice uiDevice, int displayId) {
        final BySelector selector = By.displayId(displayId)
                .res(Pattern.compile(REGEX_PACKAGE_NAME + ":id/preview_add_button"));
        UiObject2 button = uiDevice.wait(Until.findObject(selector), SHORT_TIMEOUT);
        assertThat(button).isNotNull();
        return button;
    }

    public static UiObject findPreviewAddOrSelectButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_add_or_select_button"));
    }

    /**
     * Retrieves the UI object representing the preview add or select button.
     *
     * <p>This method first verifies that the preview add or select button exists
     * on the target display.If present, it returns a {@link UiObject2} that can be used to
     * interact with the preview add or select button.<p>
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @param displayId The id of the target display.
     * @return the {@link UiObject2} representing the found preview add or select button.
     */
    public static UiObject2 findPreviewAddOrSelectButton(UiDevice uiDevice, int displayId) {
        final BySelector selector = By.displayId(displayId)
                .res(Pattern.compile(REGEX_PACKAGE_NAME + ":id/preview_add_or_select_button"));
        UiObject2 button = uiDevice.wait(Until.findObject(selector), SHORT_TIMEOUT);
        assertThat(button).isNotNull();
        return button;
    }

    public static UiObject findAddButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_add"));
    }

    /**
     * Retrieves the UI object representing the 'add' button.
     *
     * <p>This method first verifies that the 'add' button exists on the target display.
     * If present, it returns a {@link UiObject2} that can be used to interact with
     * the 'add' button.<p>
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @param displayId The id of the target display.
     * @return the {@link UiObject2} representing the found add button.
     */
    public static UiObject2 findAddButton(UiDevice uiDevice, int displayId) {
        final BySelector selector = By.res(Pattern.compile(
                REGEX_PACKAGE_NAME + ":id/button_add")).displayId(displayId);
        UiObject2 button = uiDevice.wait(Until.findObject(selector), SHORT_TIMEOUT);
        assertThat(button).isNotNull();
        return button;
    }

    public static UiObject findProfileButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/profile_button"));
    }

    public static void findAndClickBrowse(UiDevice uiDevice) throws Exception {
        final UiObject overflowMenu = getOverflowMenuObject(uiDevice);
        clickAndWait(uiDevice, overflowMenu);

        final UiObject browseButton = new UiObject(new UiSelector().textContains("Browse"));
        clickAndWait(uiDevice, browseButton);
    }

    /**
     * Click the UI object representing the "Browse" button.
     *
     * <p>This method first finds and clicks that the overflow menu on the target display.
     * And it verifies that the "Browse" button exists on the target display. If present,
     * it clicks {@link UiObject2} that can be used to interact with the "Browse" button.</p>
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @param displayId The id of the target display.
     * @throws Exception if an error occurs during the click action or while waiting for the UI
     */
    public static void findAndClickBrowse(UiDevice uiDevice, int displayId) throws Exception {
        final UiObject2 overflowMenu = getOverflowMenuObject(uiDevice, displayId);
        clickAndWait(uiDevice, overflowMenu);

        UiObject2 browseButton = uiDevice.wait(Until.findObject(
                By.textContains("Browse").displayId(displayId)), SHORT_TIMEOUT);
        assertThat(browseButton).isNotNull();
        clickAndWait(uiDevice, browseButton);
    }

    public static UiObject findSettingsOverflowMenuItem(UiDevice uiDevice) throws Exception {
        final UiObject overflowMenu = getOverflowMenuObject(uiDevice);
        clickAndWait(uiDevice, overflowMenu);
        return new UiObject(new UiSelector().textContains("Cloud media app"));
    }

    /**
     * Retrieves the UI object representing the overflow menu.
     *
     * <p>This method first verifies that the overflow menu exists on the screen. If present,
     * it returns a {@link UiObject} that can be used to interact with the overflow menu.</p>
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @return The {@link UiObject} representing the overflow menu,
     * or {@code null} if it's not found.
     */
    public static UiObject getOverflowMenuObject(UiDevice uiDevice) {
        // Wait for overflow menu to appear.
        verifyOverflowMenuExists(uiDevice);
        return new UiObject(new UiSelector().description("More options"));
    }

    /**
     * Retrieves the UI object representing the overflow menu.
     *
     * <p>This method first verifies that the overflow menu exists on the screen. If present,
     * it returns a {@link UiObject} that can be used to interact with the overflow menu.</p>
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @param displayId The id of the target display.
     * @return The {@link UiObject2} representing the overflow menu.
     */
    public static UiObject2 getOverflowMenuObject(UiDevice uiDevice, int displayId) {
        // Wait for overflow menu to appear.
        verifyOverflowMenuExists(uiDevice, displayId);
        final BySelector selector = By.desc("More options").displayId(displayId);
        UiObject2 menu = findObject(uiDevice, selector);
        return menu;
    }

    public static boolean isPhotoPickerVisible() {
        return new UiObject(new UiSelector().resourceIdMatches(
                PhotoPickerUiUtils.REGEX_PACKAGE_NAME + ":id/bottom_sheet")).waitForExists(TIMEOUT);
    }

    /**
     * Verify if the app label of the {@code sTargetPackageName} is visible on the UI.
     */
    public static void verifySettingsCloudProviderOptionIsVisible(@NonNull String cmpLabel) {
        assertWithMessage("Timed out waiting for cloud provider option on settings activity")
                .that(new UiObject(new UiSelector().textContains(cmpLabel))
                        .waitForExists(TIMEOUT))
                .isTrue();
    }

    private static void verifyOverflowMenuExists(UiDevice uiDevice) {
        assertWithMessage("Timed out waiting for overflow menu to appear")
                .that(new UiObject(new UiSelector().description("More options"))
                        .waitForExists(TIMEOUT))
                .isTrue();
    }

    private static void verifyOverflowMenuExists(UiDevice uiDevice, int displayId) {
        BySelector options = By.desc("More options").displayId(displayId);
        assertWithMessage("Timed out waiting for overflow menu to appear")
                .that(uiDevice.wait(Until.hasObject(options), TIMEOUT)).isTrue();
    }

    public static void verifySettingsActivityIsVisible() {
        // id/settings_activity_root is the root layout in activity_photo_picker_settings.xml
        assertWithMessage("Timed out waiting for settings activity to appear")
                .that(new UiObject(new UiSelector()
                        .resourceIdMatches(REGEX_PACKAGE_NAME + ":id/settings_activity_root"))
                        .waitForExists(TIMEOUT))
                .isTrue();
    }

    public static void clickAndWait(UiDevice uiDevice, UiObject uiObject) throws Exception {
        uiObject.click();
        uiDevice.waitForIdle();
    }

    /**
     * Click the given UI object(UiObject2)
     *
     * @param uiDevice The {@link UiDevice} instance to use for interacting with the UI.
     * @param The {@link UiObject2} The UI object to click
     * @throws Exception if an error occurs during the click action or while waiting for the UI
     * to become idle
     */
    public static void clickAndWait(UiDevice uiDevice, UiObject2 uiObject2) throws Exception {
        uiObject2.click();
        uiDevice.waitForIdle();
    }

    /**
     * Verifies whether the selected tab is the one with the provided title
     */
    public static boolean isSelectedTabTitle(
            @NonNull String tabTitle, @NonNull String tabResourceId, UiDevice device)
            throws UiObjectNotFoundException {
        final UiObject tabLayout = findObject(tabResourceId, device);
        final UiObject tab = tabLayout.getChild(new UiSelector().textContains(tabTitle));
        return tab.isSelected();
    }

    /**
     * Returns the UI object corresponding to the specified resourceId
     */
    public static UiObject findObject(@NonNull String resourceId, UiDevice device) {
        return device.findObject(new UiSelector().resourceIdMatches(resourceId));
    }

    /**
     * Retrieves a UI object based on the specified resource ID and display ID.
     *
     * @param resourceId a non-null string representing the resource ID of the UI object.
     * @param device The {@link UiDevice} instance to use for interacting with the UI.
     * @param displayId an integer representing the display ID where the UI object is located.
     * @return The {@link UiObject2} representing the resource ID and display ID.
     */
    public static UiObject2 findObject(@NonNull String resourceId, UiDevice device, int displayId) {
        final BySelector selector = By.res(Pattern.compile(resourceId)).displayId(displayId);
        return findObject(device, selector);
    }

    /**
     * Retrieves a UI object based on the specified selector
     *
     * @param device The {@link UiDevice} instance to use for interacting with the UI.
     * @param selector the BySelector used to identify the UI object to find
     * @return the {@link UiObject2} representing the found UI object
     */
    public static UiObject2 findObject(@NonNull UiDevice device, @NonNull BySelector selector) {
        UiObject2 object = device.wait(Until.findObject(selector), SHORT_TIMEOUT);
        assertThat(object).isNotNull();
        return object;
    }

    /**
     * Asserts that a UI object with the specified resource ID exists within the given timeout.
     *
     * @param resourceId The resource ID of the UI object to find.
     * @param device     The {@link UiDevice} instance to use for searching the UI.
     */
    public static void assertUiObjectExistsWithId(@NonNull String resourceId, UiDevice device) {
        assertWithMessage("Couldn't find Ui object with resource Id " + resourceId)
                .that(findObject(resourceId, device).waitForExists(TIMEOUT)).isTrue();
    }

    /**
     * Finds a UI object with the specified resource ID and performs a click action on it.
     *
     * @param resourceId The resource ID of the UI object to find and click.
     * @param device     The {@link UiDevice} instance to use for searching the UI.
     * @throws Exception if an error occurs during the process of finding or clicking the object.
     */
    public static void findAndClickUiObjectWithId(@NonNull String resourceId, UiDevice device)
            throws Exception {
        clickAndWait(device, findObject(resourceId, device));
    }

    /**
     * Finds a UI object with the specified resource ID and performs a click action on it.
     *
     * @param resourceId The resource ID of the UI object to find and click.
     * @param device     The {@link UiDevice} instance to use for searching the UI.
     * @param displayId The id of the target display.
     * @throws Exception if an error occurs during the process of finding or clicking the object.
     */
    public static void findAndClickUiObjectWithId(@NonNull String resourceId, UiDevice device,
            int displayId) throws Exception {
        clickAndWait(device, findObject(resourceId, device, displayId));
    }

    /**
     * Finds a UI element using UI Automator (UiObject) that matches the provided text.
     *
     * @param text   The text to match. This can be a regular expression.
     * @param device The {@link UiDevice} instance to use for searching.
     */
    public static UiObject getUiObjectMatchingText(@NonNull String text, UiDevice device) {
        return device.findObject(new UiSelector().textMatches(text));
    }

    /**
     * Gets a UI element using UI Automator (BySelector) that matches the provided text.
     *
     * @param text   The text to match. This can be a regular expression.
     * @param device The {@link UiDevice} instance to use for searching.
     * @param displayId The id of the target display.
     * @return the BySelector for given text and displayId
     */
    public static BySelector getUiObjectMatchingTextSelector(@NonNull String text, int displayId) {
        return By.text(text).displayId(displayId);
    }

    /**
     * Finds a UI element using UI Automator (UiObject2) that matches the provided
     * content description.
     *
     * @param text   The content description to match.
     * @param device The {@link UiDevice} instance to use for searching.
     */
    public static UiObject2 getUiObjectMatchingDescription(@NonNull String text, UiDevice device) {
        return device.findObject(By.desc(text));
    }

    /**
     * Finds a UI element using UI Automator (UiObject2) that matches the provided
     * content description.
     *
     * @param text   The content description to match.
     * @param device The {@link UiDevice} instance to use for searching.
     * @param displayId The id of the target display.
     * @return the {@link UiObject2} for given text and displayId
     */
    public static UiObject2 getUiObjectMatchingDescription(@NonNull String text, UiDevice device,
            int displayId) {
        return device.findObject(By.desc(text).displayId(displayId));
    }
}
