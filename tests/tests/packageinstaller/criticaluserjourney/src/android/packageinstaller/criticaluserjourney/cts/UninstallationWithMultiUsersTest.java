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

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for PackageInstaller CUJs to uninstall an app with Multi-Users.
 */
@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "Cannot query other apps if instant")
@AppModeNonSdkSandbox(reason = "SDK sandboxes cannot query other apps")
@EnsureHasWorkProfile
public class UninstallationWithMultiUsersTest extends UninstallationTestBase {

    private Context mPrimaryUserContext;
    private Context mWorkProfileUserContext;
    private UserReference mPrimaryUser;
    private UserReference mWorkProfileUser;

    private UninstallResultReceiver mPrimaryUninstallResultReceiver;
    private UninstallResultReceiver mWorkProfileUninstallResultReceiver;

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
        mWorkProfileUser = workProfile(sDeviceState);

        installExistingPackageOnUser(getContext().getPackageName(), mWorkProfileUser.id());
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        mPrimaryUserContext = getContext().createContextAsUser(mPrimaryUser.userHandle(),
                /* flags= */ 0);
        mWorkProfileUserContext = getContext().createContextAsUser(mWorkProfileUser.userHandle(),
                /* flags= */ 0);

        // Register the uninstall result receivers
        mPrimaryUninstallResultReceiver = new UninstallResultReceiver();
        mPrimaryUninstallResultReceiver.registerReceiver(mPrimaryUserContext);
        mWorkProfileUninstallResultReceiver = new UninstallResultReceiver();
        mWorkProfileUninstallResultReceiver.registerReceiver(mWorkProfileUserContext);

        // Install the test package and assert it is installed on both users
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        if (mPrimaryUninstallResultReceiver != null) {
            mPrimaryUserContext.unregisterReceiver(mPrimaryUninstallResultReceiver);
            mPrimaryUninstallResultReceiver = null;
        }

        if (mWorkProfileUninstallResultReceiver != null) {
            mWorkProfileUserContext.unregisterReceiver(mWorkProfileUninstallResultReceiver);
            mWorkProfileUninstallResultReceiver = null;
        }

        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        mPrimaryUserContext = null;
        mWorkProfileUserContext = null;
        mPrimaryUser = null;
        mWorkProfileUser = null;

        uninstallTestPackage();
        // to avoid any UI is still on the screen
        pressBack();
    }

    @Test
    public void actionDeleteIntent_uninstallOnWorkProfile_okButton_success() throws Exception {
        startUninstallationViaIntentActionDeleteForUser(mWorkProfileUserContext);

        waitForUiIdle();

        clickUninstallAppFromWorkProfileOkButton();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void actionDeleteIntent_uninstallOnPrimaryUser_okButton_success() throws Exception {
        startUninstallationViaIntentActionDeleteForUser(mPrimaryUserContext);

        waitForUiIdle();

        clickUninstallOkButton();

        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallWorkProfileAppOnPrimaryUser_okButton_success()
            throws Exception {
        startUninstallationViaIntentActionUninstallPackageForUser(
                mPrimaryUserContext, mWorkProfileUser.userHandle());

        waitForUiIdle();

        clickUninstallAppFromWorkProfileOkButton();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallPrimaryAppOnWorkProfile_fail() throws Exception {
        startUninstallationViaIntentActionUninstallPackageForUser(
                mWorkProfileUserContext, mPrimaryUser.userHandle());

        waitForUiIdle();

        findPackageInstallerObject(
                "The current user is not allowed to perform this uninstallation.");

        // close the error dialog
        pressBack();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallOnWorkProfile_okButton_success()
            throws Exception {
        startUninstallationViaIntentActionUninstallPackageForUser(mWorkProfileUserContext);

        waitForUiIdle();

        clickUninstallAppFromWorkProfileOkButton();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallOnPrimaryUser_okButton_success() throws Exception {
        startUninstallationViaIntentActionUninstallPackageForUser(mPrimaryUserContext);

        waitForUiIdle();

        clickUninstallOkButton();

        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void installerApi_noDeletePackages_uninstallOnWorkProfile_okButton_success()
            throws Exception {
        startUninstallationViaPackageInstallerApiForUser(mWorkProfileUserContext,
                mWorkProfileUninstallResultReceiver);

        waitForUiIdle();

        clickUninstallAppFromWorkProfileOkButton();

        assertUninstallSuccess(mWorkProfileUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
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
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void installerApi_deletePackages_differentInstaller_workProfile_okButton_success()
            throws Exception {
        startUninstallationViaPackageInstallerApiWithDeletePackagesForUser(mWorkProfileUserContext,
                mWorkProfileUninstallResultReceiver, /* isSameInstaller= */ false);

        waitForUiIdle();

        clickUninstallAppFromWorkProfileOkButton();

        assertUninstallSuccess(mWorkProfileUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
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
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void installerApi_deletePackages_sameInstaller_workProfile_noConfirmedDialog_success()
            throws Exception {
        // if the installer is not the test case, even if the test is granted the DELETE_PACKAGES
        // permission, it also needs the user confirmation to approve the uninstallation.
        // Set the test case to be the installer of the test app
        installTestPackageWithInstallerPackageName();

        startUninstallationViaPackageInstallerApiWithDeletePackagesForUser(mWorkProfileUserContext,
                mWorkProfileUninstallResultReceiver, /* isSameInstaller= */ true);

        assertUninstallSuccess(mWorkProfileUninstallResultReceiver);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
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
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }
}
