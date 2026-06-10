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

/** Tests for PackageInstaller CUJs via PackageInstaller#uninstall api */
@RunWith(AndroidJUnit4.class)
@PlatinumTest(focusArea = "pm")
@AppModeFull
public class UninstallationViaPackageInstallerApiTest extends UninstallationTestBase {

    @Test
    public void launch_hasDeletePackages_differentInstaller_okButton_success() throws Exception {
        startUninstallationViaPackageInstallerApiWithDeletePackages(/* isSameInstaller= */ false);

        waitForUiIdle();

        clickUninstallOkButton();

        assertUninstallSuccess();
        assertTestPackageNotInstalled();
    }

    @Test
    public void launch_hasDeletePackages_sameInstaller_noConfirmedDialog_success()
            throws Exception {
        // if the installer is not the test case, even if the test is granted the DELETE_PACKAGES
        // permission, it also needs the user confirmation to approve the uninstallation.
        // Set the test case to be the installer of the test app
        installTestPackageWithInstallerPackageName();

        startUninstallationViaPackageInstallerApiWithDeletePackages(/* isSameInstaller= */ true);

        assertUninstallSuccess();
        assertTestPackageNotInstalled();
    }

    @Test
    public void launch_noDeletePackages_okButton_success() throws Exception {
        startUninstallationViaPackageInstallerApi();

        waitForUiIdle();

        clickUninstallOkButton();

        assertUninstallSuccess();
        assertTestPackageNotInstalled();
    }

    @Test
    public void launch_backKey_failed() throws Exception {
        startUninstallationViaPackageInstallerApi();

        waitForUiIdle();

        assertUninstallDialog();

        pressBack();

        // TODO (b/352604292): should receive STATUS_FAILURE_ABORTED result in this case
//        assertUninstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void launch_touchOutside_failed() throws Exception {
        startUninstallationViaPackageInstallerApi();

        waitForUiIdle();

        assertUninstallDialog();

        touchOutside();

        // TODO (b/352604292): should receive STATUS_FAILURE_ABORTED result in this case
//        assertUninstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void launch_cancelButton_failed() throws Exception {
        startUninstallationViaPackageInstallerApi();

        waitForUiIdle();

        assertUninstallDialog();

        clickCancelButton();

        assertUninstallFailureAborted();
        assertTestPackageInstalled();
    }
}
