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

/** Tests for PackageInstaller CUJs via PackageInstaller.Session APIs. */
@RunWith(AndroidJUnit4.class)
@PlatinumTest(focusArea = "pm")
@AppModeFull
public class InstallationViaSessionTest extends InstallationTestBase {

    @Test
    public void newInstall_grantedInstallPackages_noConfirmedDialog_success() throws Exception {
        startInstallationViaPackageInstallerSessionWithPermission();

        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_installButton_success() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickInstallButton();

        assertInstallSuccess();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_backKey_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchButNoGrantPermission_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_installButton_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickInstallButton();

        assertInstallSuccess();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppInstallDialog();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppInstallDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppInstallDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void update_grantedInstallPackages_noConfirmedDialog_success() throws Exception {
        installTestPackage();
        startInstallationUpdateViaPackageInstallerSessionWithPermission();

        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton();

        assertInstallSuccess();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickUpdateButton();

        assertInstallSuccess();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }
}
