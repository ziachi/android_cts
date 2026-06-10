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

/** Tests for PackageInstaller CUJs via startActivity with ACTION_INSTALL_PACKAGE. */
@RunWith(AndroidJUnit4.class)
@PlatinumTest(focusArea = "pm")
@AppModeFull
public class InstallationViaIntentTest extends InstallationTestBase {

    @Test
    public void newInstall_noLauncherEntry_launchGrantPermission_installButton_success()
            throws Exception {
        startNoLauncherActivityInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickInstallButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_installButton_success() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickInstallButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_backKey_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        pressBack();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        touchOutside();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        clickCancelButton();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchButNoGrantPermission_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertTestPackageNotInstalled();
    }


    @Test
    public void newInstall_noLauncherEntry_noLaunchGrantPermission_installButton_success()
            throws Exception {
        grantRequestInstallPackagesPermission();

        startNoLauncherActivityInstallationViaIntent();

        waitForUiIdle();

        clickInstallButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_installButton_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        clickInstallButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        assertTestAppInstallDialog();

        pressBack();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        assertTestAppInstallDialog();

        touchOutside();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        assertTestAppInstallDialog();

        clickCancelButton();

        assertTestPackageNotInstalled();
    }



    @Test
    public void update_noLauncherEntry_launchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        startNoLauncherActivityInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_disableEntry_launchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        disableTestPackageLauncherActivity();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntent();

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

        startInstallationUpdateViaIntent();

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

        startInstallationUpdateViaIntent();

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

        startInstallationUpdateViaIntent();

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

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLauncherEntry_noLaunchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startNoLauncherActivityInstallationUpdateViaIntent();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_disableEntry_noLaunchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        disableTestPackageLauncherActivity();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_noLaunchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertTestPackageInstalled();
    }


    @Test
    public void updateWithPackageUri_noLauncherEntry_launchGrantPermission_updateButton_success()
            throws Exception {
        installNoLauncherActivityTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_disableEntry_launchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        disableTestPackageLauncherActivity();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLauncherEntry_noLaunchGrantPermission_updateButton_success()
            throws Exception {
        installNoLauncherActivityTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_disableEntry_noLaunchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        disableTestPackageLauncherActivity();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        assertInstallSuccessDialogForNoLauncherActivity();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        assertInstallSuccessDialogAndLaunchTestApp();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_touchOutside_failed()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_cancelButton_failed()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertTestPackageInstalled();
    }
}
