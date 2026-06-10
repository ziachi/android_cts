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
import android.telecom.ApiNameEnum;
import android.telecom.ApiResultEnum;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.telecom.TelecomApiStats;
import com.android.os.telecom.TelecomExtensionAtom;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@NonApiTest(
        exemptionReasons = {},
        justification = "METRIC")
@RunWith(DeviceJUnit4ClassRunner.class)
public class TelecomApiStatsTests extends BaseTelecomMetricsTest {

    private static final String[][] TELECOM_MANAGER_TEST_CASES = {
        {"android.telecom.cts.TelecomManagerTest", "testGetCurrentTtyMode"},
        {"android.telecom.cts.TelecomManagerTest", "testIsInEmergencyCall_noOngoingEmergencyCall"},
        {"android.telecom.cts.OutgoingCallTest", "testExtraPhoneAccountHandleAvailable"},
    };

    private static final ApiNameEnum[] TELECOM_MANAGER_TEST_CASES_API = {
        ApiNameEnum.API_GET_CURRENT_TTY_MODE,
        ApiNameEnum.API_IS_IN_EMERGENCY_CALL,
        ApiNameEnum.API_REGISTER_PHONE_ACCOUNT,
        ApiNameEnum.API_PLACE_CALL,
        ApiNameEnum.API_UNREGISTER_PHONE_ACCOUNT,
    };

    @Test
    public void testApiStats() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                TelecomExtensionAtom.TELECOM_API_STATS_FIELD_NUMBER);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        TelecomExtensionAtom.registerAllExtensions(registry);
        HashSet<ApiNameEnum> apiSet = new HashSet<>();
        Collections.addAll(apiSet, TELECOM_MANAGER_TEST_CASES_API);
        for (String[] tc : TELECOM_MANAGER_TEST_CASES) {
            runDeviceTests(
                    new DeviceTestRunOptions(PKG_CTS_TELECOM_TEST)
                            .setDevice(getDevice())
                            .setDisableHiddenApiCheck(true)
                            .setTestClassName(tc[0])
                            .setTestMethodName(tc[1]));
        }
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice(), registry, true);
        assertThat(data).isNotEmpty();

        for (AtomsProto.Atom atom : data) {
            TelecomApiStats stats = atom.getExtension(TelecomExtensionAtom.telecomApiStats);
            assertTrue(stats.hasApiName());
            assertTrue(stats.hasUid());
            assertTrue(stats.hasApiResult());
            assertTrue(stats.hasCount());
            if (apiSet.contains(stats.getApiName())
                    && stats.getUid() == mTestAppUid
                    && stats.getApiResult() == ApiResultEnum.RESULT_SUCCESS) {
                assertThat(stats.getCount()).isGreaterThan(0);
            }
        }
    }
}
