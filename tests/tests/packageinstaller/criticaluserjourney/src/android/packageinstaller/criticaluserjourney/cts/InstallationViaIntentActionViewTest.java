/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.packageinstaller.criticaluserjourney.cts;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.PlatinumTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for PackageInstaller CUJs via startActivity with ACTION_VIEW. */
@RunWith(AndroidJUnit4.class)
@PlatinumTest(focusArea = "pm")
@AppModeFull
public class InstallationViaIntentActionViewTest extends InstallationTestBase {

    @Test
    public void newInstall_launchGrantPermission_installButton_success() throws Exception {
        startInstallationViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickInstallButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_backKey_failed() throws Exception {
        startInstallationViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        pressBack();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallationViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        touchOutside();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallationViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        clickCancelButton();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchButNoGrantPermission_failed() throws Exception {
        startInstallationViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_installButton_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentActionView();

        waitForUiIdle();

        clickInstallButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentActionView();

        waitForUiIdle();

        assertTestAppInstallDialog();

        pressBack();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentActionView();

        waitForUiIdle();

        assertTestAppInstallDialog();

        touchOutside();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentActionView();

        waitForUiIdle();

        assertTestAppInstallDialog();

        clickCancelButton();

        assertTestPackageNotInstalled();
    }

    @Test
    public void update_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertTestPackageInstalled();
    }

    @Test
    public void update_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentActionView();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertTestPackageInstalled();
    }
}
