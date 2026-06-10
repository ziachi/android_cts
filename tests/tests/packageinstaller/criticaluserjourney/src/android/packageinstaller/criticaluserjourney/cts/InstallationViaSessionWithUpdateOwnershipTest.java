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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for PackageInstaller CUJs via PackageInstaller.Session APIs with update-ownership. */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class InstallationViaSessionWithUpdateOwnershipTest extends UpdateOwnershipTestBase {

    @Test
    public void launchGrantPermission_updateAnyway_success() throws Exception {
        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateAnywayButton();

        assertInstallSuccess();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void launchGrantPermission_backKey_failed() throws Exception {
        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateAnywayDialog();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateAnywayDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertTestAppUpdateAnywayDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void noLaunchGrantPermission_updateAnyway_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickUpdateAnywayButton();

        assertInstallSuccess();
        assertTestPackageVersion2Installed();
    }

    @Test
    public void noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppUpdateAnywayDialog();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppUpdateAnywayDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        assertTestAppUpdateAnywayDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }
}
