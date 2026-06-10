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

import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.cloneProfile;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.EnsureHasCloneProfile;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for PackageInstaller CUJs to uninstall an app with the clone profile.
 */
@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "Cannot query other apps if instant")
@AppModeNonSdkSandbox(reason = "SDK sandboxes cannot query other apps")
@EnsureHasCloneProfile
public class UninstallationWithCloneProfileTest extends UninstallationTestBase {

    private Context mCloneUserContext;
    private Context mPrimaryUserContext;
    private UserReference mCloneUser;
    private UserReference mPrimaryUser;

    private UninstallResultReceiver mPrimaryUninstallResultReceiver;
    private UninstallResultReceiver mCloneUninstallResultReceiver;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    @Override
    public void setup() throws Exception {
        setupTestEnvironment();

        assumeTrue(UserManager.supportsMultipleUsers());

        // Prepare the users and the user contexts
        mPrimaryUser = sDeviceState.initialUser();
        mCloneUser = cloneProfile(sDeviceState);

        installExistingPackageOnUser(getContext().getPackageName(), mCloneUser.id());
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        mPrimaryUserContext = getContext().createContextAsUser(mPrimaryUser.userHandle(),
                /* flags= */ 0);
        mCloneUserContext = getContext().createContextAsUser(mCloneUser.userHandle(),
                /* flags= */ 0);

        // Register the uninstall result receivers
        mPrimaryUninstallResultReceiver = new UninstallResultReceiver();
        mPrimaryUninstallResultReceiver.registerReceiver(mPrimaryUserContext);
        mCloneUninstallResultReceiver = new UninstallResultReceiver();
        mCloneUninstallResultReceiver.registerReceiver(mCloneUserContext);

        // Install the test package and assert it is installed on both users
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mCloneUserContext);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        if (mPrimaryUninstallResultReceiver != null) {
            mPrimaryUserContext.unregisterReceiver(mPrimaryUninstallResultReceiver);
            mPrimaryUninstallResultReceiver = null;
        }

        if (mCloneUninstallResultReceiver != null) {
            mCloneUserContext.unregisterReceiver(mCloneUninstallResultReceiver);
            mCloneUninstallResultReceiver = null;
        }

        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        mCloneUserContext = null;
        mPrimaryUserContext = null;
        mCloneUser = null;
        mPrimaryUser = null;

        uninstallTestPackage();
        // to avoid any UI is still on the screen
        pressBack();
    }

    @Test
    public void actionDeleteIntent_uninstallOnCloneUser_okButton_success() throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mCloneUserContext);

        startUninstallationViaIntentActionDeleteForUser(mCloneUserContext);

        waitForUiIdle();

        clickUninstallCloneOkButton();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
    }

    @Test
    public void actionDeleteIntent_uninstallOnPrimaryUser_okButton_success() throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mCloneUserContext);

        startUninstallationViaIntentActionDeleteForUser(mPrimaryUserContext);

        waitForUiIdle();

        clickUninstallOkButton();

        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallOnCloneUser_okButton_success()
            throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mCloneUserContext);

        startUninstallationViaIntentActionUninstallPackageForUser(mCloneUserContext);

        waitForUiIdle();

        clickUninstallCloneOkButton();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallOnPrimaryUser_okButton_success() throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mCloneUserContext);

        startUninstallationViaIntentActionUninstallPackageForUser(mPrimaryUserContext);

        waitForUiIdle();

        clickUninstallOkButton();

        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
    }

    @Test
    public void installerApi_noDeletePackages_uninstallOnCloneUser_okButton_success()
            throws Exception {
        startUninstallationViaPackageInstallerApiForUser(mCloneUserContext,
                mCloneUninstallResultReceiver);

        waitForUiIdle();

        clickUninstallCloneOkButton();

        assertUninstallSuccess(mCloneUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
    }

    @Test
    public void installerApi_noDeletePackages_uninstallOnPrimaryUser_okButton_success()
            throws Exception {
        startUninstallationViaPackageInstallerApiForUser(mPrimaryUserContext,
                mPrimaryUninstallResultReceiver);

        waitForUiIdle();

        clickUninstallOkButton();

        assertUninstallSuccess(mPrimaryUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
    }

    @Test
    public void installerApi_deletePackages_differentInstaller_cloneUser_okButton_success()
            throws Exception {
        startUninstallationViaPackageInstallerApiWithDeletePackagesForUser(mCloneUserContext,
                mCloneUninstallResultReceiver, /* isSameInstaller= */ false);

        waitForUiIdle();

        clickUninstallCloneOkButton();

        assertUninstallSuccess(mCloneUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
    }

    @Test
    public void installerApi_deletePackages_differentInstaller_primaryUser_okButton_success()
            throws Exception {
        startUninstallationViaPackageInstallerApiWithDeletePackagesForUser(mPrimaryUserContext,
                mPrimaryUninstallResultReceiver, /* isSameInstaller= */ false);

        waitForUiIdle();

        clickUninstallOkButton();

        assertUninstallSuccess(mPrimaryUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
    }

    @Test
    public void installerApi_deletePackages_sameInstaller_cloneUser_noConfirmedDialog_success()
            throws Exception {
        // if the installer is not the test case, even if the test is granted the DELETE_PACKAGES
        // permission, it also needs the user confirmation to approve the uninstallation.
        // Set the test case to be the installer of the test app
        installTestPackageWithInstallerPackageName();

        startUninstallationViaPackageInstallerApiWithDeletePackagesForUser(mCloneUserContext,
                mCloneUninstallResultReceiver, /* isSameInstaller= */ true);

        assertUninstallSuccess(mCloneUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
    }

    @Test
    public void installerApi_deletePackages_sameInstaller_primaryUser_noConfirmedDialog_success()
            throws Exception {
        // if the installer is not the test case, even if the test is granted the DELETE_PACKAGES
        // permission, it also needs the user confirmation to approve the uninstallation.
        // Set the test case to be the installer of the test app
        installTestPackageWithInstallerPackageName();

        startUninstallationViaPackageInstallerApiWithDeletePackagesForUser(mPrimaryUserContext,
                mPrimaryUninstallResultReceiver, /* isSameInstaller= */ true);

        assertUninstallSuccess(mPrimaryUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mCloneUserContext);
    }
}
