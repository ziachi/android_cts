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

package android.appsecurity.cts;

import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host side test to verify APIs in the {@link android.security.keystore.KeyStoreManager} function
 * as intended. These APIs are tested with host side tests as they typically require coordination
 * between two or more apps.
 */
@Presubmit
@RunWith(DeviceJUnit4ClassRunner.class)
@RequiresFlagsEnabled(android.security.Flags.FLAG_KEYSTORE_GRANT_API)
public final class KeyStoreManagerTest extends BaseHostJUnit4Test {
    /*
     * The test package of the app used to test APIs related to the granting of keys owned by the
     * app to other apps on the device.
     */
    private static final String GRANT_TEST_PKG =
            "android.appsecurity.cts.keystoremanagertest.granter";
    /**
     * The class containing test methods used to verify APIs related to the granting of keys.
      */
    private static final String GRANT_TEST_CLASS = GRANT_TEST_PKG + ".KeyStoreManagerGrantTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Test
    @ApiTest(apis = {"android.security.keystore.KeyStoreManager#grantKeyAccess",
            "android.security.keystore.KeyStoreManager#revokeKeyAccess",
            "android.security.keystore.KeyStoreManager#getGrantedKeyFromId"})
    @CddTest(requirements = {"3.1/C-0-1"})
    public void grantAndRevokeKeyAccess_generatedSecretKey() throws Exception {
        Utils.runDeviceTests(getDevice(), GRANT_TEST_PKG, GRANT_TEST_CLASS,
                "grantAndRevokeKeyAccess_generatedSecretKey");
    }

    @Test
    @ApiTest(apis = {"android.security.keystore.KeyStoreManager#grantKeyAccess",
            "android.security.keystore.KeyStoreManager#revokeKeyAccess",
            "android.security.keystore.KeyStoreManager#getGrantedKeyFromId"})
    @CddTest(requirements = {"3.1/C-0-1"})
    public void grantAndRevokeKeyAccess_generatedPrivateKey() throws Exception {
        Utils.runDeviceTests(getDevice(), GRANT_TEST_PKG, GRANT_TEST_CLASS,
                "grantAndRevokeKeyAccess_generatedPrivateKey");
    }

    @Test
    @ApiTest(apis = {"android.security.keystore.KeyStoreManager#grantKeyAccess",
            "android.security.keystore.KeyStoreManager#revokeKeyAccess",
            "android.security.keystore.KeyStoreManager#getGrantedKeyFromId",
            "android.security.keystore.KeyStoreManager#getGrantedKeyPairFromId",
            "android.security.keystore.KeyStoreManager#getGrantedCertificateChainFromId"})
    @CddTest(requirements = {"3.1/C-0-1"})
    public void grantAndRevokeKeyAccess_importedKeyPair() throws Exception {
        Utils.runDeviceTests(getDevice(), GRANT_TEST_PKG, GRANT_TEST_CLASS,
                "grantAndRevokeKeyAccess_importedKeyPair");
    }

    @Test
    @ApiTest(apis = {"android.security.keystore.KeyStoreManager#grantKeyAccess",
            "android.security.keystore.KeyStoreManager#revokeKeyAccess"})
    @CddTest(requirements = {"3.1/C-0-1"})
    public void grantRevokeKeyAccess_aliasDoesNotExist_throwsException() throws Exception {
        Utils.runDeviceTests(getDevice(), GRANT_TEST_PKG, GRANT_TEST_CLASS,
                "grantRevokeKeyAccess_aliasDoesNotExist_throwsException");
    }

    @Test
    @ApiTest(apis = {"android.security.keystore.KeyStoreManager#getGrantedKeyFromId",
            "android.security.keystore.KeyStoreManager#getGrantedKeyPairFromId",
            "android.security.keystore.KeyStoreManager#getGrantedCertificateChainFromId"})
    @CddTest(requirements = {"3.1/C-0-1"})
    public void getGranted_grantIdDoesNotExist_throwsException() throws Exception {
        Utils.runDeviceTests(getDevice(), GRANT_TEST_PKG, GRANT_TEST_CLASS,
                "getGranted_grantIdDoesNotExist_throwsException");
    }
}
