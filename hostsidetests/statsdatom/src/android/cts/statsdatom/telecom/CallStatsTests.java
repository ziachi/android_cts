/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.cts.statsdatom.telecom;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.telecom.AccountTypeEnum;
import android.telecom.CallDirectionEnum;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.telecom.CallStats;
import com.android.os.telecom.TelecomExtensionAtom;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@NonApiTest(
        exemptionReasons = {},
        justification = "METRIC")
@RunWith(DeviceJUnit4ClassRunner.class)
public class CallStatsTests extends BaseTelecomMetricsTest {

    private static final String APK_TELECOM_CUJ_TEST = "CtsTelecomCujTestCases.apk";
    private static final String APK_MANAGED_CONNECTION_APP = "ManagedConnectionServiceApp.apk";
    private static final String PKG_TELECOM_CUJ_TEST = "android.telecom.cts.cuj";
    private static final String PKG_MANAGED_CONNECTION_APP = "android.telecom.cts.apps.managedapp";
    private static final String CLASS_SINGLE_CALLING_TEST =
            "android.telecom.cts.cuj.app.integration.SingleCallingTest";
    private static final String CASE_TEST_OUTGOING_CALL =
            "testOutgoingCall_ManagedConnectionServiceApp";

    @Test
    public void testCallStats() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                TelecomExtensionAtom.CALL_STATS_FIELD_NUMBER);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        TelecomExtensionAtom.registerAllExtensions(registry);
        DeviceUtils.runDeviceTests(
                getDevice(),
                PKG_TELECOM_CUJ_TEST,
                CLASS_SINGLE_CALLING_TEST,
                CASE_TEST_OUTGOING_CALL);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice(), registry, true);
        assertThat(data).isNotEmpty();

        CallStats stats = data.get(0).getExtension(TelecomExtensionAtom.callStats);
        assertTrue(stats.hasCallDirection());
        assertTrue(stats.hasExternalCall());
        assertTrue(stats.hasEmergencyCall());
        assertTrue(stats.hasMultipleAudioAvailable());
        assertTrue(stats.hasAccountType());
        assertTrue(stats.hasUid());
        assertTrue(stats.hasAccountType());
        assertTrue(stats.hasDisconnectCause());
        assertTrue(stats.hasAccountType());
        assertTrue(stats.hasSimultaneousType());
        assertTrue(stats.hasAverageDurationMs());

        assertThat(stats.getCallDirection()).isEqualTo(CallDirectionEnum.DIR_OUTGOING);
        assertThat(stats.getEmergencyCall()).isEqualTo(false);
        assertThat(stats.getAccountType()).isEqualTo(AccountTypeEnum.ACCOUNT_MANAGED);
    }

    @Override
    protected String[][] getInstalledTestApps() {
        return new String[][] {
            {APK_TELECOM_CUJ_TEST, PKG_TELECOM_CUJ_TEST},
            {APK_MANAGED_CONNECTION_APP, PKG_MANAGED_CONNECTION_APP}
        };
    }
}
