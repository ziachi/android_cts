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

/** Tests for PackageInstaller CUJs via startActivityForResult with ACTION_INSTALL_PACKAGE. */
@RunWith(AndroidJUnit4.class)
@PlatinumTest(focusArea = "pm")
@AppModeFull
public class InstallationViaIntentForResultTest extends InstallationTestBase {

    @Test
    public void newInstall_launchGrantPermission_installButton_success() throws Exception {
        startInstallationViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickInstallButton(/* checkInstallingDialog= */ true);

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultOK();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_backKey_failed() throws Exception {
        startInstallationViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        pressBack();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallationViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        touchOutside();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallationViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppInstallDialog();

        clickCancelButton();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchButNoGrantPermission_failed() throws Exception {
        startInstallationViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_installButton_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentForResult();

        waitForUiIdle();

        assertTestAppInstallDialog();

        clickInstallButton(/* checkInstallingDialog= */ true);

        assertInstallerResponseActivityResultOK();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentForResult();

        waitForUiIdle();

        assertTestAppInstallDialog();

        pressBack();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentForResult();

        waitForUiIdle();

        assertTestAppInstallDialog();

        touchOutside();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntentForResult();

        waitForUiIdle();

        assertTestAppInstallDialog();

        clickCancelButton();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageNotInstalled();
    }

    @Test
    public void update_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ true);

//        assertInstallerResponseActivityResultOK();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        pressBack();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        touchOutside();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        clickCancelButton();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ true);

        assertInstallerResponseActivityResultOK();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        pressBack();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        touchOutside();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntentForResult();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultOK();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        pressBack();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        touchOutside();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateDialog();

        clickCancelButton();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        // TODO (b/349258056): should get the correct activity result
//        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        clickUpdateButton(/* checkInstallingDialog= */ false, /* isUpdatedViaPackageUri= */ true);

        assertInstallerResponseActivityResultOK();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        pressBack();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_touchOutside_failed()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        touchOutside();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_cancelButton_failed()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUriForResult();

        waitForUiIdle();

        assertTestAppUpdateDialog();

        clickCancelButton();

        assertInstallerResponseActivityResultCanceled();
        assertTestPackageInstalled();
    }
}
