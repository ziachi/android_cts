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

package android.appsearch.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * These tests cover:
 * 1) general enterprise access to AppSearch data through EnterpriseGlobalSearchSession, and
 * 2) enterprise fields restrictions applied to the Person schema
 *
 * <p>These tests do not cover:
 * 1) the enterprise transformation applied to Person documents, since that only applies to
 * AppSearch's actual contacts corpus, and these tests run using the local AppSearch database
 * 2) the managed profile device policy check for AppSearch's actual contacts corpus as we cannot
 * set the policy in CTS tests
 *
 * <p>Unlock your device when testing locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class EnterpriseContactsMultiUserTest extends AppSearchHostTestBase {
    private static int sMainUserId;
    private static int sSecondaryUserId;
    private static int sEnterpriseUserId;
    private static boolean sIsTemporaryEnterpriseUser;
    private static ITestDevice sDevice;
    private static final List<SuiteApkInstaller> sInstallers = new ArrayList<>();

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        ITestDevice device = testInfo.getDevice();
        assumeTrue("Multi-user is not supported on this device", device.isMultiUserSupported());
        assumeTrue("No main user on this device so cannot create enterprise profile",
                device.getMainUserId() != null);
        // Enterprise profile can only be created from the main user (initial human user)
        sMainUserId = device.getMainUserId();
        sSecondaryUserId = createSecondaryUser(device);
        assumeTrue("Could not find or create an enterprise profile on this device",
                setUpEnterpriseProfile(testInfo.getDevice()));
        sDevice = device;
        installPackageAsUser(testInfo, sMainUserId);
        installPackageAsUser(testInfo, sSecondaryUserId);
        installPackageAsUser(testInfo, sEnterpriseUserId);
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        for (SuiteApkInstaller installer : sInstallers) {
            installer.tearDown(testInfo, null);
        }
        if (sSecondaryUserId > 0) {
            testInfo.getDevice().removeUser(sSecondaryUserId);
        }
        if (sIsTemporaryEnterpriseUser) {
            testInfo.getDevice().removeUser(sEnterpriseUserId);
        }
    }

    /** Creates a test user and returns the user id. */
    private static int createSecondaryUser(ITestDevice device) throws DeviceNotAvailableException {
        int profileId = device.createUser("Test User");
        assertThat(device.startUser(profileId)).isTrue();
        return profileId;
    }

    /**
     * Gets or creates an enterprise profile and sets the user id. Returns false if could neither
     * get or create an enterprise profile.
     */
    private static boolean setUpEnterpriseProfile(ITestDevice device)
            throws DeviceNotAvailableException {
        // Search for a managed profile
        for (UserInfo userInfo : device.getUserInfos().values()) {
            if (userInfo.isManagedProfile()) {
                sEnterpriseUserId = userInfo.userId();
                return true;
            }
        }
        // If no managed profile, set up a temporary one
        try {
            // Create a managed profile "work" under the main user
            String createUserOutput = device.executeShellCommand(
                    "pm create-user --profileOf " + sMainUserId + " --managed work");
            sEnterpriseUserId = Integer.parseInt(createUserOutput.split(" id ")[1].trim());
            assertThat(device.startUser(sEnterpriseUserId, /*waitFlag=*/ true)).isTrue();
            sIsTemporaryEnterpriseUser = true;
            return true;
        } catch (Exception e) {
            LogUtil.CLog.w("Could not set up enterprise profile for test: %s", e);
            return false;
        }
    }

    private static void installPackageAsUser(TestInformation testInfo, int userId)
            throws Exception {
        SuiteApkInstaller installer = new SuiteApkInstaller();
        installer.addTestFileName(TARGET_APK_A);
        installer.setUserId(userId);
        installer.setShouldGrantPermission(true);
        installer.setUp(testInfo);
        sInstallers.add(installer);
    }

    /**
     * As setup, we need the enterprise user to first create some contacts locally. It's ok for this
     * method to run at the beginning of each test without a previous teardown, since it will just
     * overwrite the same contacts.
     */
    private void setUpEnterpriseContacts() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA("setUpEnterpriseContacts",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    private void setUpEnterpriseContactsWithoutEnterprisePermissions() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "setUpEnterpriseContactsWithoutEnterprisePermissions",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    private void setUpEnterpriseContactsWithManagedPermission() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA("setUpEnterpriseContactsWithManagedPermission",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_hasEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testHasEnterpriseAccess",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_hasEnterpriseAccess_withManagedPermission_onUAbove() throws Exception {
        assumeTrue(sDevice.getApiLevel() >= 34);
        // We only run this test if we know that we have managed profile contacts access; this will
        // be the case if we set up a temporary work profile. (It's not guaranteed in the case that
        // there was an existing work profile instead)
        assumeTrue(sIsTemporaryEnterpriseUser);
        setUpEnterpriseContactsWithManagedPermission();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testHasEnterpriseAccess",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccess_withManagedPermission_onTBelow()
            throws Exception {
        assumeTrue(sDevice.getApiLevel() <= 33);
        // We only run this test if we know that we have managed profile contacts access; this will
        // be the case if we set up a temporary work profile. (It's not guaranteed in the case that
        // there was an existing work profile instead)
        assumeTrue(sIsTemporaryEnterpriseUser);
        setUpEnterpriseContactsWithManagedPermission();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccessIfEnterpriseProfileIsStopped()
            throws Exception {
        setUpEnterpriseContacts();
        try {
            assertThat(sDevice.stopUser(sEnterpriseUserId, /*waitFlag=*/ true, /*forceFlag=*/
                    true)).isTrue();
            runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                    sMainUserId,
                    Collections.emptyMap());
        } finally {
            sDevice.startUser(sEnterpriseUserId, /*waitFlag=*/ true);
        }
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccessToNonEnterpriseSchema() throws Exception {
        setUpEnterpriseContactsWithoutEnterprisePermissions();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testEnterpriseUser_doesNotHaveEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSecondaryUser_doesNotHaveEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sSecondaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testGetEnterpriseContact() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testGetEnterpriseContact",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testGetEnterpriseContact_withProjection() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testGetEnterpriseContact_withProjection",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts_withProjection() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts_withProjection",
                sMainUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts_withFilter() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts_withFilter",
                sMainUserId,
                Collections.emptyMap());
    }
}
