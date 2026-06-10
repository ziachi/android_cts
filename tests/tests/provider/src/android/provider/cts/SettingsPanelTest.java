/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.provider.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.StaleObjectException;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests related SettingsPanels:
 *
 * atest SettingsPanelTest
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SettingsPanelTest {

    private static final int TIMEOUT = 8000;

    private static final String RESOURCE_DONE = "done";
    private static final String RESOURCE_SEE_MORE = "see_more";
    private static final String RESOURCE_TITLE = "panel_title";

    private String mSettingsPackage;
    private String mLauncherPackage;

    private Context mContext;
    private boolean mHasTouchScreen;
    private boolean mHasBluetooth;

    private UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        final PackageManager packageManager = mContext.getPackageManager();

        mHasTouchScreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
        mHasBluetooth = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_HOME);
        mLauncherPackage = packageManager.resolveActivity(launcherIntent,
                PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;

        Intent settingsIntent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        mSettingsPackage = packageManager.resolveActivity(settingsIntent,
                PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;

        assumeFalse("Skipping test: Auto does not support provider android.settings.panel", isCar());
        assumeFalse(
            "Skipping test: Watch does not support provider android.settings.panel", isWatch());
    }

    @After
    public void cleanUp() {
        mDevice.pressHome();
        mDevice.wait(Until.hasObject(By.pkg(mLauncherPackage).depth(0)), TIMEOUT);
    }

    // Check correct package is opened

    @Test
    public void volumePanel_correctPackage() {
        assumeTrue(mHasTouchScreen);
        launchVolumePanel();

        String currentPackage = mDevice.getCurrentPackageName();

        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_VOLUME));
    }

    @Test
    public void nfcPanel_correctPackage() {
        launchNfcPanel();

        String currentPackage = mDevice.getCurrentPackageName();

        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_NFC));
    }

    @Test
    public void wifiPanel_correctPackage() {
        launchWifiPanel();

        String currentPackage = mDevice.getCurrentPackageName();

        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_WIFI));
    }

    @Test
    public void volumePanel_doneClosesPanel() {
        assumeTrue(mHasTouchScreen);
        assumeTrue(packageNameForAction(Settings.Panel.ACTION_VOLUME).equals(mSettingsPackage));
        // Launch panel
        launchVolumePanel();
        String currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_VOLUME));

        // Click the done button
        pressDone();

        // Assert that we have left the panel
        currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isNotEqualTo(packageNameForAction(Settings.Panel.ACTION_VOLUME));
    }

    @Test
    public void nfcPanel_doneClosesPanel() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        assumeTrue(packageNameForAction(Settings.Panel.ACTION_NFC).equals(mSettingsPackage));

        // Launch panel
        launchNfcPanel();
        String currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_NFC));

        // Click the done button
        pressDone();

        // Assert that we have left the panel
        currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isNotEqualTo(packageNameForAction(Settings.Panel.ACTION_NFC));
    }

    @Test
    public void wifiPanel_doneClosesPanel() {
        assumeTrue(packageNameForAction(Settings.Panel.ACTION_WIFI).equals(mSettingsPackage));

        // Launch panel
        launchWifiPanel();
        String currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_WIFI));

        // Click the done button
        pressDone();

        // Assert that we have left the panel
        currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isNotEqualTo(packageNameForAction(Settings.Panel.ACTION_WIFI));
    }

    @Test
    public void volumePanel_seeMoreButton_launchesIntoSettings() {
        assumeTrue(mHasTouchScreen);
        assumeTrue(packageNameForAction(Settings.Panel.ACTION_VOLUME).equals(mSettingsPackage));
        // Launch panel
        launchVolumePanel();
        String currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_VOLUME));

        // Click the see more button
        pressSeeMore();

        // Assert that we're in Settings, on a different page.
        currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(mSettingsPackage);
        UiObject2 titleView = mDevice.findObject(By.res(mSettingsPackage, RESOURCE_TITLE));
        assertThat(titleView).isNull();
    }

    @Test
    public void nfcPanel_seeMoreButton_launchesIntoSettings() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        assumeTrue(packageNameForAction(Settings.Panel.ACTION_NFC).equals(mSettingsPackage));

        // Launch panel
        launchNfcPanel();
        String currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_NFC));

        // Click the see more button
        assumeTrue(mHasTouchScreen);
        pressSeeMore();

        // Assert that in Settings, on a different page.
        currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(mSettingsPackage);
        UiObject2 titleView = mDevice.findObject(By.res(mSettingsPackage, RESOURCE_TITLE));
        assertThat(titleView).isNull();
    }

    @Test
    public void wifiPanel_seeMoreButton_launchesIntoSettings() {
        assumeTrue(packageNameForAction(Settings.Panel.ACTION_WIFI).equals(mSettingsPackage));

        // Launch panel
        launchWifiPanel();
        String currentPackage = mDevice.getCurrentPackageName();
        assertThat(currentPackage).isEqualTo(packageNameForAction(Settings.Panel.ACTION_WIFI));

        // Click the see more button
        assumeTrue(mHasTouchScreen);
        pressSeeMore();

        try {
            UiObject2 titleView = mDevice.findObject(By.res(mSettingsPackage, RESOURCE_TITLE));
            assertThat(titleView).isNull();
        } catch (StaleObjectException ex) {
            // If we get a StaleObjectException, it means that the underlying View has already
            // been destroyed. The test panel might be no longer visible, which is same as expected
            // result. Filter out exceptions to avoid unnecessary flaky errors.
        }
    }

    private void launchVolumePanel() {
        launchPanel(Settings.Panel.ACTION_VOLUME);
    }

    private void launchNfcPanel() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        launchPanel(Settings.Panel.ACTION_NFC);
    }

    private void launchWifiPanel() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI));
        launchPanel(Settings.Panel.ACTION_WIFI);
    }

    private void launchPanel(String action) {
        // Start from the home screen
        mDevice.pressHome();
        mDevice.wait(Until.hasObject(By.pkg(mLauncherPackage).depth(0)), TIMEOUT);

        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.startActivity(intent),
                Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

        // Wait for the app to appear
        mDevice.wait(Until.hasObject(By.pkg(mSettingsPackage).depth(0)), TIMEOUT);
    }

    private void pressDone() {
        UiObject2 doneObject = mDevice.findObject(By.res(mSettingsPackage, RESOURCE_DONE));
        if (!mHasTouchScreen || doneObject == null) {
            mDevice.pressBack();
            return;
        }

        doneObject.click();
        mDevice.wait(Until.hasObject(By.pkg(mLauncherPackage).depth(0)), TIMEOUT);
    }

    private void pressSeeMore() {
        mDevice.findObject(By.res(mSettingsPackage, RESOURCE_SEE_MORE)).click();
        mDevice.wait(Until.hasObject(By.pkg(mSettingsPackage).depth(0)), TIMEOUT);
    }

    private String packageNameForAction(String action) {
        ResolveInfo resolvedInfoForAction = mContext.getPackageManager().resolveActivity(
                new Intent(action),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (resolvedInfoForAction == null) {
            Assert.fail("No activity to handle action " + action);
        }
        return resolvedInfoForAction.activityInfo.packageName;
    }

    private boolean isCar() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private boolean isWatch() {
      return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
