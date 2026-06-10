/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.EXTRA_RESTRICTION;
import static android.app.admin.DevicePolicyManager.WIFI_SECURITY_PERSONAL;
import static android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID;
import static android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_WIFI_CONFIG;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CHANGE_WIFI_STATE;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_TETHERING;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_WIFI;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_WIFI_DIRECT;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_WIFI_TETHERING;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiSsid;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.AddNetwork;
import com.android.bedstead.harrier.policies.DisallowAddWifiConfig;
import com.android.bedstead.harrier.policies.DisallowChangeWifiState;
import com.android.bedstead.harrier.policies.DisallowConfigTethering;
import com.android.bedstead.harrier.policies.DisallowConfigWifi;
import com.android.bedstead.harrier.policies.DisallowWifiDirect;
import com.android.bedstead.harrier.policies.DisallowWifiTethering;
import com.android.bedstead.harrier.policies.Wifi;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FakeKeys;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalTetheringSettingsStep;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalWifiDirectSettingsStep;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalWifiSettingsStep;
import com.android.interactive.steps.settings.CanYouAddWifiConfigStep;
import com.android.interactive.steps.settings.CanYouChangeWifiSettingsStep;
import com.android.interactive.steps.settings.CanYouEnableWifiTetheringStep;
import com.android.interactive.steps.settings.CanYouSwitchWifiOffStep;
import com.android.interactive.steps.settings.CanYouUseWifiDirectStep;
import com.android.interactive.steps.settings.IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep;
import com.android.interactive.steps.settings.SwitchWifiOffStep;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class WifiTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final int WIFI_SECURITY_LEVEL = WIFI_SECURITY_PERSONAL;
    private static final WifiSsidPolicy WIFI_SSID_POLICY =
            new WifiSsidPolicy(WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST,
                    Set.of(WifiSsid.fromBytes(null)));

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    private static final String TEST_ALIAS = "test_alias";
    private static final String TEST_SSID = "\"test_ssid\"";
    private static final PrivateKey PRIVATE_KEY =
            TestApis.certificates().generateRSAPrivateKey(FakeKeys.FAKE_RSA_1.privateKey);
    private static final Certificate CERTIFICATE =
            TestApis.certificates().generateCertificate(FakeKeys.FAKE_RSA_1.caCertificate);
    private static final Certificate[] CERTIFICATES = new Certificate[]{CERTIFICATE};

    private static final WifiEnterpriseConfig sWifiEnterpriseConfig = enterpriseWifiConfiguration();
    private static final WifiConfiguration sWifiConfiguration = wifiConfiguration();
    private static final List<WifiNetworkSuggestion> sWifiNetworkSuggestions =
            Collections.singletonList(new WifiNetworkSuggestion.Builder()
                    .setSsid(TEST_SSID)
                    .setWpa2EnterpriseConfig(sWifiEnterpriseConfig)
                    .build());

    @CannotSetPolicyTest(policy = DisallowConfigWifi.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void setUserRestriction_disallowConfigWifi_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_CONFIG_WIFI));
    }

    @PolicyAppliesTest(policy = DisallowConfigWifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void setUserRestriction_disallowConfigWifi_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_WIFI);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_WIFI))
                    .isTrue();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_WIFI);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigWifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void setUserRestriction_disallowConfigWifi_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_WIFI);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_WIFI))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_WIFI);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void disallowConfigWifiIsNotSet_canConfigWifi() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouChangeWifiSettingsStep.class)).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_WIFI)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void disallowConfigWifiIsSet_canNotConfigWifi() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouChangeWifiSettingsStep.class)).isFalse();
        assertThat(
                Step.execute(
                        IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(
            policy = DisallowChangeWifiState.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void setUserRestriction_disallowChangeWifiState_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_CHANGE_WIFI_STATE));
    }

    @PolicyAppliesTest(policy = DisallowChangeWifiState.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void setUserRestriction_disallowChangeWifiState_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CHANGE_WIFI_STATE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CHANGE_WIFI_STATE))
                    .isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CHANGE_WIFI_STATE);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowChangeWifiState.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void setUserRestriction_disallowChangeWifiState_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CHANGE_WIFI_STATE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CHANGE_WIFI_STATE))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CHANGE_WIFI_STATE);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CHANGE_WIFI_STATE)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @EnsureWifiEnabled
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void disallowChangeWifiStateIsNotSet_canChangeWifiState() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        Step.execute(SwitchWifiOffStep.class);
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_CHANGE_WIFI_STATE)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @EnsureWifiEnabled
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void disallowChangeWifiStateIsSet_canNotChangeWifiState() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouSwitchWifiOffStep.class)).isFalse();
        assertThat(
                Step.execute(
                        IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(policy = DisallowWifiTethering.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    public void setUserRestriction_disallowWifiTethering_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_WIFI_TETHERING));
    }

    @PolicyAppliesTest(policy = DisallowWifiTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    public void setUserRestriction_disallowWifiTethering_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_TETHERING))
                    .isTrue();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_TETHERING);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowWifiTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    public void setUserRestriction_disallowWifiTethering_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_TETHERING))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_TETHERING);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_WIFI_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    @NotFullyAutomated(reason = "CanYouEnableWifiTetheringStep") // TODO: Automate
    public void disallowWifiTetheringIsNotSet_canEnableWifiTethering() throws Exception {
        Step.execute(NavigateToPersonalTetheringSettingsStep.class);

        assertThat(
                Step.execute(CanYouEnableWifiTetheringStep.class))
                .isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_WIFI_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    @NotFullyAutomated(reason = "CanYouEnableWifiTetheringStep") // TODO: Automate
    public void disallowWifiTetheringIsSet_canNotEnableWifiTethering() throws Exception {
        Step.execute(NavigateToPersonalTetheringSettingsStep.class);

        assertThat(
                Step.execute(CanYouEnableWifiTetheringStep.class))
                .isFalse();
        assertThat(
                Step.execute(
                        IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(
            policy = DisallowConfigTethering.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void setUserRestriction_disallowConfigTethering_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_CONFIG_TETHERING));
    }

    @PolicyAppliesTest(policy = DisallowConfigTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void setUserRestriction_disallowConfigTethering_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_TETHERING))
                    .isTrue();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_TETHERING);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void setUserRestriction_disallowConfigTethering_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_TETHERING))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_TETHERING);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void disallowConfigTetheringIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_WIFI_TETHERING)
    @EnsureHasUserRestriction(DISALLOW_CONFIG_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void disallowConfigTetheringIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowWifiDirect.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    public void setUserRestriction_disallowWifiDirect_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_WIFI_DIRECT));
    }

    @PolicyAppliesTest(policy = DisallowWifiDirect.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    public void setUserRestriction_disallowWifiDirect_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_DIRECT);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_DIRECT))
                    .isTrue();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_DIRECT);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowWifiDirect.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    public void setUserRestriction_disallowWifiDirect_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_DIRECT);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_DIRECT))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_WIFI_DIRECT);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_WIFI_DIRECT)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    @NotFullyAutomated(reason = "NavigateToPersonalWifiDirectSettingsStep") // TODO: Automate
    public void disallowWifiDirectIsNotSet_canUseWifiDirect() throws Exception {
        Step.execute(NavigateToPersonalWifiDirectSettingsStep.class);

        assertThat(Step.execute(CanYouUseWifiDirectStep.class)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_WIFI_DIRECT)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    @NotFullyAutomated(reason = "NavigateToPersonalWifiDirectSettingsStep") // TODO: Automate
    public void disallowWifiDirectIsSet_canNotUseWifiDirect() throws Exception {
        Step.execute(NavigateToPersonalWifiDirectSettingsStep.class);

        assertThat(Step.execute(CanYouUseWifiDirectStep.class)).isFalse();
        assertThat(
                Step.execute(
                        IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(policy = DisallowAddWifiConfig.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    public void setUserRestriction_disallowAddWifiConfig_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_ADD_WIFI_CONFIG));
    }

    @PolicyAppliesTest(policy = DisallowAddWifiConfig.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    public void setUserRestriction_disallowAddWifiConfig_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_ADD_WIFI_CONFIG);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_WIFI_CONFIG))
                    .isTrue();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_ADD_WIFI_CONFIG);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowAddWifiConfig.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    public void setUserRestriction_disallowAddWifiConfig_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_ADD_WIFI_CONFIG);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_WIFI_CONFIG))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_ADD_WIFI_CONFIG);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_ADD_WIFI_CONFIG)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    @NotFullyAutomated(reason = "CanYouAddWifiConfigStep") // TODO: Automate
    public void disallowAddWifiConfigIsNotSet_canAddWifiConfig() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouAddWifiConfigStep.class)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_ADD_WIFI_CONFIG)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    @NotFullyAutomated(reason = "CanYouAddWifiConfigStep") // TODO: Automate
    public void disallowAddWifiConfigIsSet_canNotAddWifiConfig() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouAddWifiConfigStep.class)).isFalse();
    }

    @CannotSetPolicyTest(policy = Wifi.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getWifiMacAddress")
    public void getWifiMacAddress_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .getWifiMacAddress(dpc(sDeviceState).componentName()));
    }

    @CanSetPolicyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getWifiMacAddress")
    @EnsureWifiEnabled
    public void getWifiMacAddress_doesNotThrow() {
        assertThat(dpc(sDeviceState).devicePolicyManager()
                .getWifiMacAddress(dpc(sDeviceState).componentName())).isNotNull();
    }

    @CannotSetPolicyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMinimumRequiredWifiSecurityLevel")
    public void setMinimumRequiredWifiSecurityLevel_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setMinimumRequiredWifiSecurityLevel(WIFI_SECURITY_LEVEL));
    }

    @PolicyAppliesTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setMinimumRequiredWifiSecurityLevel",
            "android.app.admin.DevicePolicyManager#getMinimumRequiredWifiSecurityLevel"})
    public void setMinimumRequiredWifiSecurityLevel_minimumRequiredWifiSecurityLevelIsSet() {
        int originalWifiSecurityLevel = dpc(sDeviceState).devicePolicyManager()
                .getMinimumRequiredWifiSecurityLevel();
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setMinimumRequiredWifiSecurityLevel(WIFI_SECURITY_LEVEL);

            assertThat(sLocalDevicePolicyManager.getMinimumRequiredWifiSecurityLevel())
                    .isEqualTo(WIFI_SECURITY_LEVEL);
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setMinimumRequiredWifiSecurityLevel(originalWifiSecurityLevel);
        }
    }

    @PolicyDoesNotApplyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setMinimumRequiredWifiSecurityLevel",
            "android.app.admin.DevicePolicyManager#getMinimumRequiredWifiSecurityLevel"})
    public void setMinimumRequiredWifiSecurityLevel_doesNotApply_minimumRequiredWifiSecurityLevelIsNotSet() {
        int originalWifiSecurityLevel = dpc(sDeviceState).devicePolicyManager()
                .getMinimumRequiredWifiSecurityLevel();
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setMinimumRequiredWifiSecurityLevel(WIFI_SECURITY_LEVEL);

            assertThat(sLocalDevicePolicyManager.getMinimumRequiredWifiSecurityLevel())
                    .isNotEqualTo(WIFI_SECURITY_LEVEL);
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setMinimumRequiredWifiSecurityLevel(originalWifiSecurityLevel);
        }
    }

    @CannotSetPolicyTest(policy = Wifi.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setWifiSsidPolicy")
    public void setWifiSsidPolicy_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setWifiSsidPolicy(WIFI_SSID_POLICY));
    }

    @CanSetPolicyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setWifiSsidPolicy",
            "android.app.admin.DevicePolicyManager#getWifiSsidPolicy"})
    public void setWifiSsidPolicy_wifiSsidPolicyIsSet() {
        WifiSsidPolicy originalWifiSsidPolicy = dpc(sDeviceState).devicePolicyManager()
                .getWifiSsidPolicy();
        try {
            dpc(sDeviceState).devicePolicyManager().setWifiSsidPolicy(WIFI_SSID_POLICY);

            assertThat(dpc(sDeviceState).devicePolicyManager().getWifiSsidPolicy())
                    .isEqualTo(WIFI_SSID_POLICY);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setWifiSsidPolicy(originalWifiSsidPolicy);
        }
    }

    @CannotSetPolicyTest(policy = Wifi.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setConfiguredNetworksLockdownState")
    public void setConfiguredNetworksLockdownState_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> dpc(sDeviceState)
                .devicePolicyManager().setConfiguredNetworksLockdownState(
                        dpc(sDeviceState).componentName(), /* lockdown= */ true));
    }

    // We can't have a simple policyappliestest + policydoesnotapplytest because we can't fetch the
    // policy for a given user - once we can adopt MANAGE_DEVICE_POLICY_WIFI we can add the correct
    // tests
    @CanSetPolicyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setConfiguredNetworksLockdownState",
            "android.app.admin.DevicePolicyManager#hasLockdownAdminConfiguredNetworks"
    })
    public void setConfiguredNetworksLockdownState_true_isSet() {
        boolean originalLockdown = dpc(sDeviceState).devicePolicyManager()
                .hasLockdownAdminConfiguredNetworks(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setConfiguredNetworksLockdownState(
                            dpc(sDeviceState).componentName(), /* lockdown= */ true);

            assertThat(dpc(sDeviceState).devicePolicyManager().hasLockdownAdminConfiguredNetworks(
                    dpc(sDeviceState).componentName())).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setConfiguredNetworksLockdownState(
                            dpc(sDeviceState).componentName(), originalLockdown);
        }
    }

    @CanSetPolicyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setConfiguredNetworksLockdownState",
            "android.app.admin.DevicePolicyManager#hasLockdownAdminConfiguredNetworks"
    })
    public void setConfiguredNetworksLockdownState_false_isSet() {
        boolean originalLockdown = dpc(sDeviceState).devicePolicyManager()
                .hasLockdownAdminConfiguredNetworks(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setConfiguredNetworksLockdownState(
                            dpc(sDeviceState).componentName(), /* lockdown= */ false);

            assertThat(dpc(sDeviceState).devicePolicyManager().hasLockdownAdminConfiguredNetworks(
                    dpc(sDeviceState).componentName())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setConfiguredNetworksLockdownState(
                            dpc(sDeviceState).componentName(), originalLockdown);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.os.UserManager#DISALLOW_CONFIG_WIFI"})
    @Postsubmit(reason = "new test")
    @EnsureHasUserRestriction(DISALLOW_CONFIG_WIFI)
    @Test
    public void createAdminSupportIntent_disallowConfigWifi_createsIntent() {
        Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                DISALLOW_CONFIG_WIFI);

        assertThat(intent).isNotNull();
        assertThat(intent.getStringExtra(EXTRA_RESTRICTION)).isEqualTo(DISALLOW_CONFIG_WIFI);
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.os.UserManager#DISALLOW_CONFIG_WIFI"})
    @Postsubmit(reason = "new test")
    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @Test
    public void createAdminSupportIntent_allowConfigWifi_doesNotCreate() {
        Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                DISALLOW_CONFIG_WIFI);

        assertThat(intent).isNull();
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addNetwork")
    @CanSetPolicyTest(policy = AddNetwork.class)
    @Postsubmit(reason = "new test")
    public void addNetwork_keychainKeyGranted_success() {
        int networkId = -1;
        try {
            dpc(sDeviceState).devicePolicyManager().installKeyPair(
                    dpc(sDeviceState).componentName(), PRIVATE_KEY, CERTIFICATES,
                    TEST_ALIAS, /* requestAccess= */ false);
            dpc(sDeviceState).devicePolicyManager().grantKeyPairToWifiAuth(TEST_ALIAS);

            networkId = dpc(sDeviceState).wifiManager().addNetwork(sWifiConfiguration);

            assertThat(networkId).isNotEqualTo(-1);
        } finally {
            dpc(sDeviceState).wifiManager().removeNetwork(networkId);
            dpc(sDeviceState).devicePolicyManager().removeKeyPair(
                    dpc(sDeviceState).componentName(), TEST_ALIAS);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addNetwork")
    @CanSetPolicyTest(policy = AddNetwork.class)
    @Postsubmit(reason = "new test")
    public void addNetwork_keychainKeyNotGranted_failure() {
        try {
            dpc(sDeviceState).devicePolicyManager().installKeyPair(
                    dpc(sDeviceState).componentName(), PRIVATE_KEY, CERTIFICATES,
                    TEST_ALIAS, /* requestAccess= */ false);

            assertThat(dpc(sDeviceState).wifiManager().addNetwork(sWifiConfiguration))
                    .isEqualTo(-1);
        } finally {
            dpc(sDeviceState).devicePolicyManager().removeKeyPair(
                    dpc(sDeviceState).componentName(), TEST_ALIAS);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addNetwork")
    @CanSetPolicyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    public void addNetworkSuggestions_keychainKeyGranted_success() {
        try {
            dpc(sDeviceState).devicePolicyManager().installKeyPair(
                    dpc(sDeviceState).componentName(), PRIVATE_KEY, CERTIFICATES,
                    TEST_ALIAS, /* requestAccess= */ false);
            dpc(sDeviceState).devicePolicyManager().grantKeyPairToWifiAuth(TEST_ALIAS);

            assertThat(dpc(sDeviceState).wifiManager().addNetworkSuggestions(
                    sWifiNetworkSuggestions))
                    .isEqualTo(STATUS_NETWORK_SUGGESTIONS_SUCCESS);
        } finally {
            dpc(sDeviceState).wifiManager().removeNetworkSuggestions(sWifiNetworkSuggestions);
            dpc(sDeviceState).devicePolicyManager().removeKeyPair(
                    dpc(sDeviceState).componentName(), TEST_ALIAS);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addNetworkSuggestions")
    @CanSetPolicyTest(policy = Wifi.class)
    @Postsubmit(reason = "new test")
    public void addNetworkSuggestions_keychainKeyNotGranted_invalid() {
        try {
            dpc(sDeviceState).devicePolicyManager().installKeyPair(
                    dpc(sDeviceState).componentName(), PRIVATE_KEY, CERTIFICATES,
                    TEST_ALIAS, /* requestAccess= */ false);

            assertThat(dpc(sDeviceState).wifiManager().addNetworkSuggestions(
                    sWifiNetworkSuggestions))
                    .isEqualTo(STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID);
        } finally {
            dpc(sDeviceState).wifiManager().removeNetworkSuggestions(sWifiNetworkSuggestions);
            dpc(sDeviceState).devicePolicyManager().removeKeyPair(
                    dpc(sDeviceState).componentName(), TEST_ALIAS);
        }
    }

    private static WifiEnterpriseConfig enterpriseWifiConfiguration() {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setDomainSuffixMatch("some-domain.com");
        enterpriseConfig.setIdentity("user");
        enterpriseConfig.setCaCertificate((X509Certificate) CERTIFICATE);
        enterpriseConfig.setClientKeyPairAlias(TEST_ALIAS);
        return enterpriseConfig;
    }

    private static WifiConfiguration wifiConfiguration() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        config.enterpriseConfig = enterpriseWifiConfiguration();
        return config;
    }
}
