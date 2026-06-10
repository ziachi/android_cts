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

/** Tests for PackageInstaller CUJs via startActivity with ACTION_UNINSTALL_PACKAGE. */
@RunWith(AndroidJUnit4.class)
@PlatinumTest(focusArea = "pm")
@AppModeFull
public class UninstallationViaIntentActionUninstallPackageTest extends UninstallationTestBase {

    @Test
    public void launch_okButton_success() throws Exception {
        startUninstallationViaIntentActionUninstallPackage();

        waitForUiIdle();

        clickUninstallOkButton();

        assertTestPackageNotInstalled();
    }

    @Test
    public void launch_backKey_failed() throws Exception {
        startUninstallationViaIntentActionUninstallPackage();

        waitForUiIdle();

        assertUninstallDialog();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void launch_touchOutside_failed() throws Exception {
        startUninstallationViaIntentActionUninstallPackage();

        waitForUiIdle();

        assertUninstallDialog();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void launch_cancelButton_failed() throws Exception {
        startUninstallationViaIntentActionUninstallPackage();

        waitForUiIdle();

        assertUninstallDialog();

        clickCancelButton();

        assertTestPackageInstalled();
    }
}
