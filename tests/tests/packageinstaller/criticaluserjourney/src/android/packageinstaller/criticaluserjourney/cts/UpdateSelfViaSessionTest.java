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

/**
 * Tests for PackageInstaller CUJs via PackageInstaller.Session APIs with updating installer itself
 */
@RunWith(AndroidJUnit4.class)
@PlatinumTest(focusArea = "pm")
@AppModeFull
public class UpdateSelfViaSessionTest extends InstallationTestBase {

    @Test
    public void launchGrantPermission_update_success() throws Exception {
        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButtonForInstallerUpdateSelf();

        assertInstallSuccess();
        assertInstallerVersion2Installed();
    }

    @Test
    public void launchGrantPermission_backKey_failed() throws Exception {
        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertInstallerUpdateSelfDialog();

        pressBack();

        assertInstallFailureAborted();
        assertInstallerInstalled();
    }

    @Test
    public void launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertInstallerUpdateSelfDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertInstallerInstalled();
    }

    @Test
    public void launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        assertInstallerUpdateSelfDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertInstallerInstalled();
    }

    @Test
    public void noLaunchGrantPermission_update_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        clickUpdateButtonForInstallerUpdateSelf();

        assertInstallSuccess();
        assertInstallerVersion2Installed();
    }

    @Test
    public void noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        assertInstallerUpdateSelfDialog();

        pressBack();

        assertInstallFailureAborted();
        assertInstallerInstalled();
    }

    @Test
    public void noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        assertInstallerUpdateSelfDialog();

        touchOutside();

        assertInstallFailureAborted();
        assertInstallerInstalled();
    }

    @Test
    public void noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallerUpdateItselfViaPackageInstallerSession();

        waitForUiIdle();

        assertInstallerUpdateSelfDialog();

        clickCancelButton();

        assertInstallFailureAborted();
        assertInstallerInstalled();
    }
}
